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
 * TD-002 §13 WAL checkpoint 任务：30s 周期 PASSIVE checkpoint。
 */
public final class OutboxCheckpointTask {

    private static final Logger log = LoggerFactory.getLogger(OutboxCheckpointTask.class);

    private final OutboxCommandQueue queue;
    private final long intervalMs;
    private final ScheduledExecutorService scheduler;
    private final AtomicBoolean running = new AtomicBoolean(false);

    public OutboxCheckpointTask(OutboxCommandQueue queue, long intervalMs) {
        this.queue = queue;
        this.intervalMs = intervalMs;
        this.scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "outbox-checkpoint");
            t.setDaemon(true);
            return t;
        });
    }

    public void start() {
        if (running.compareAndSet(false, true)) {
            scheduler.scheduleWithFixedDelay(this::checkpointCycle, intervalMs, intervalMs, TimeUnit.MILLISECONDS);
            log.info("checkpoint task started: interval={}ms", intervalMs);
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

    private void checkpointCycle() {
        try {
            queue.offer(new OutboxCommand.Checkpoint(), Duration.ofSeconds(5));
        } catch (Exception e) {
            log.warn("checkpoint cycle error: {}", e.getMessage());
        }
    }
}
