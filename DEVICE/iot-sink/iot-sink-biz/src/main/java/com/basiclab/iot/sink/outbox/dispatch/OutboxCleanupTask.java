package com.basiclab.iot.sink.outbox.dispatch;

import com.basiclab.iot.sink.outbox.sqlite.OutboxCommand;
import com.basiclab.iot.sink.outbox.sqlite.OutboxCommandQueue;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * TD-002 §13 ACKED 清理任务：10s 周期，单批 ≤1000 条 DELETE。
 */
public final class OutboxCleanupTask {

    private static final Logger log = LoggerFactory.getLogger(OutboxCleanupTask.class);

    private final OutboxCommandQueue queue;
    private final long intervalMs;
    private final long keepMs;
    private final int batchSize;
    private final ScheduledExecutorService scheduler;
    private final AtomicBoolean running = new AtomicBoolean(false);

    public OutboxCleanupTask(OutboxCommandQueue queue, long intervalMs, long keepMs, int batchSize) {
        this.queue = queue;
        this.intervalMs = intervalMs;
        this.keepMs = keepMs;
        this.batchSize = batchSize;
        this.scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "outbox-cleanup");
            t.setDaemon(true);
            return t;
        });
    }

    public void start() {
        if (running.compareAndSet(false, true)) {
            scheduler.scheduleWithFixedDelay(this::cleanupCycle, intervalMs, intervalMs, TimeUnit.MILLISECONDS);
            log.info("cleanup task started: interval={}ms keepMs={} batch={}", intervalMs, keepMs, batchSize);
        }
    }

    public void stop() {
        if (running.compareAndSet(true, false)) {
            scheduler.shutdown();
            try {
                scheduler.awaitTermination(5, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }

    private void cleanupCycle() {
        try {
            queue.offer(new OutboxCommand.CleanupAcked(
                    System.currentTimeMillis() - keepMs, batchSize), Duration.ofSeconds(5));
        } catch (Exception e) {
            log.warn("cleanup cycle error: {}", e.getMessage());
        }
    }
}
