package com.basiclab.iot.sink.outbox.sqlite;

import com.basiclab.iot.sink.telemetry.envelope.EnvelopeCanonicalCodec;
import com.basiclab.iot.sink.telemetry.outbox.AckCommand;
import com.basiclab.iot.sink.telemetry.outbox.AppendBatchResult;
import com.basiclab.iot.sink.telemetry.outbox.ClaimBatchResult;
import com.basiclab.iot.sink.telemetry.outbox.OutboxBackpressureException;
import com.basiclab.iot.sink.telemetry.outbox.OutboxUnavailableException;
import com.basiclab.iot.sink.telemetry.outbox.TelemetryOutboxBatch;
import com.basiclab.iot.sink.telemetry.outbox.TelemetryOutboxPort;
import com.basiclab.iot.sink.telemetry.outbox.TelemetryRoute;

import java.nio.file.Path;
import java.io.IOException;
import java.time.Duration;
import java.sql.SQLException;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * TD-002 §9 {@link TelemetryOutboxPort} SQLite 实现。
 *
 * <p>appendBatch → 入队 {@link OutboxCommandQueue}（背压超时）→ 单 writer 线程原子提交。
 * 空批次不提交；同 messageId 同 hash → DUPLICATE；不同 hash → COLLISION 整批回滚。
 */
public final class SqliteTelemetryOutbox implements TelemetryOutboxPort {

    private static final long DEFAULT_FUTURE_TIMEOUT_SECONDS = 30;

    private final OutboxCommandQueue queue;
    private final SqliteOutboxWriter writer;
    private final OutboxFileLock fileLock;
    private volatile boolean shutdown;

    public SqliteTelemetryOutbox(Path dbPath, EnvelopeCanonicalCodec codec, int queueCapacity) {
        if (dbPath == null || dbPath.toAbsolutePath().normalize().getParent() == null) {
            throw new OutboxUnavailableException("ROUTE_BACKFILL_APPLY_FAILED: database path invalid");
        }
        Path database = dbPath.toAbsolutePath().normalize();
        Path lockPath = database.getParent().resolve("collector-outbox.lock");
        OutboxFileLock acquired = null;
        try {
            // The same lock guards migration and the writer connection.  A
            // caller must never observe a half-migrated outbox as running.
            acquired = new OutboxFileLock(lockPath);
            SqliteOutboxMigration.migrate(database);
            OutboxCommandQueue commandQueue = new OutboxCommandQueue(queueCapacity);
            SqliteOutboxWriter commandWriter = new SqliteOutboxWriter(database, codec, commandQueue);
            commandWriter.start();
            this.queue = commandQueue;
            this.writer = commandWriter;
            this.fileLock = acquired;
        } catch (IOException | SQLException e) {
            closeAfterFailedStart(acquired, e);
            throw new OutboxUnavailableException("ROUTE_BACKFILL_APPLY_FAILED: outbox startup failed", e);
        } catch (RuntimeException | Error e) {
            closeAfterFailedStart(acquired, e);
            throw e;
        }
    }

    @Override
    public AppendBatchResult appendBatch(TelemetryOutboxBatch batch, Duration enqueueTimeout) {
        CompletableFuture<AppendBatchResult> future = new CompletableFuture<>();
        queue.offer(new OutboxCommand.AppendBatch(batch, future), enqueueTimeout);
        try {
            return future.get(DEFAULT_FUTURE_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new OutboxBackpressureException("interrupted waiting for append");
        } catch (TimeoutException e) {
            throw new OutboxUnavailableException("append future timeout after " + DEFAULT_FUTURE_TIMEOUT_SECONDS + "s");
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof RuntimeException re) {
                throw re;
            }
            throw new OutboxUnavailableException("append failed", cause);
        }
    }

    @Override
    public ClaimBatchResult claimBatch(int maxCount, Duration lease) {
        CompletableFuture<ClaimBatchResult> future = new CompletableFuture<>();
        queue.offer(new OutboxCommand.Claim(maxCount, lease.toMillis(), future), Duration.ofSeconds(5));
        try {
            return future.get(DEFAULT_FUTURE_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new OutboxBackpressureException("interrupted waiting for claim");
        } catch (TimeoutException e) {
            throw new OutboxUnavailableException("claim future timeout");
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof RuntimeException re) {
                throw re;
            }
            throw new OutboxUnavailableException("claim failed", cause);
        }
    }

    @Override
    public List<TelemetryRoute> listUnfinishedRoutes() {
        CompletableFuture<List<TelemetryRoute>> future = new CompletableFuture<>();
        queue.offer(new OutboxCommand.ListUnfinishedRoutes(future), Duration.ofSeconds(5));
        try {
            return future.get(DEFAULT_FUTURE_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new OutboxBackpressureException("interrupted waiting for unfinished routes");
        } catch (TimeoutException e) {
            throw new OutboxUnavailableException("unfinished routes future timeout");
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof RuntimeException re) {
                throw re;
            }
            throw new OutboxUnavailableException("list unfinished routes failed", cause);
        }
    }

    @Override
    public void applyAck(AckCommand ack) {
        queue.offer(new OutboxCommand.ApplyAck(ack), Duration.ofSeconds(5));
    }

    /** 暴露内部队列供 CleanupTask/CheckpointTask 使用。 */
    OutboxCommandQueue getQueue() {
        return queue;
    }

    /** 租约回收（LeaseReclaimer 周期调用，fire-and-forget）。 */
    public void reclaimExpiredLeases() {
        queue.offer(new OutboxCommand.ReclaimExpiredLeases(
                System.currentTimeMillis(), 1000L, 1_800_000L), Duration.ofSeconds(5));
    }

    /** 优雅关闭 writer 线程（等待 Connection 关闭，释放 Windows 文件锁）。 */
    public void shutdown() {
        if (shutdown) {
            return;
        }
        synchronized (this) {
            if (shutdown) {
                return;
            }
            writer.shutdown();
            boolean interrupted = false;
            while (writer.isAlive()) {
                try {
                    // Never release the process lock while the writer may
                    // still hold a SQLite connection or file handle.
                    writer.join();
                } catch (InterruptedException e) {
                    interrupted = true;
                    writer.shutdown();
                }
            }
            try {
                fileLock.close();
            } catch (IOException e) {
                throw new OutboxUnavailableException(
                        "ROUTE_BACKFILL_APPLY_FAILED: outbox lock close failed", e);
            } finally {
                shutdown = true;
                if (interrupted) {
                    Thread.currentThread().interrupt();
                }
            }
        }
    }

    private static void closeAfterFailedStart(OutboxFileLock lock, Throwable failure) {
        if (lock == null) {
            return;
        }
        try {
            lock.close();
        } catch (IOException closeFailure) {
            failure.addSuppressed(closeFailure);
        }
    }
}
