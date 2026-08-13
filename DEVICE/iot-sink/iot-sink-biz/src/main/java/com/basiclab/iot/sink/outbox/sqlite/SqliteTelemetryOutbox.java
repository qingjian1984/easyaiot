package com.basiclab.iot.sink.outbox.sqlite;

import com.basiclab.iot.sink.telemetry.envelope.EnvelopeCanonicalCodec;
import com.basiclab.iot.sink.telemetry.envelope.TelemetryEnvelope;
import com.basiclab.iot.sink.telemetry.outbox.AckCommand;
import com.basiclab.iot.sink.telemetry.outbox.AppendBatchResult;
import com.basiclab.iot.sink.telemetry.outbox.ClaimBatchResult;
import com.basiclab.iot.sink.telemetry.outbox.OutboxBackpressureException;
import com.basiclab.iot.sink.telemetry.outbox.OutboxUnavailableException;
import com.basiclab.iot.sink.telemetry.outbox.TelemetryOutboxPort;

import java.nio.file.Path;
import java.time.Duration;
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

    public SqliteTelemetryOutbox(Path dbPath, EnvelopeCanonicalCodec codec, int queueCapacity) {
        this.queue = new OutboxCommandQueue(queueCapacity);
        this.writer = new SqliteOutboxWriter(dbPath, codec, queue);
        this.writer.start();
    }

    @Override
    public AppendBatchResult appendBatch(List<TelemetryEnvelope> envelopes, Duration enqueueTimeout) {
        CompletableFuture<AppendBatchResult> future = new CompletableFuture<>();
        queue.offer(new OutboxCommand.AppendBatch(envelopes, future), enqueueTimeout);
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
    public void applyAck(AckCommand ack) {
        queue.offer(new OutboxCommand.ApplyAck(ack), Duration.ofSeconds(5));
    }

    /** 优雅关闭 writer 线程（等待 Connection 关闭，释放 Windows 文件锁）。 */
    public void shutdown() {
        writer.shutdown();
        try {
            writer.join(5000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
