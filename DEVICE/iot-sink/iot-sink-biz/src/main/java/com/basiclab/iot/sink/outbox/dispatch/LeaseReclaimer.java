package com.basiclab.iot.sink.outbox.dispatch;

import com.basiclab.iot.sink.outbox.sqlite.SqliteTelemetryOutbox;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * TD-002 §10 租约回收器：30s 周期扫描过期 IN_FLIGHT → PENDING + 退避。
 */
public final class LeaseReclaimer {

    private static final Logger log = LoggerFactory.getLogger(LeaseReclaimer.class);

    private final SqliteTelemetryOutbox outbox;
    private final long intervalMs;
    private final ScheduledExecutorService scheduler;
    private final AtomicBoolean running = new AtomicBoolean(false);

    public LeaseReclaimer(SqliteTelemetryOutbox outbox, long intervalMs) {
        this.outbox = outbox;
        this.intervalMs = intervalMs;
        this.scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "outbox-lease-reclaimer");
            t.setDaemon(true);
            return t;
        });
    }

    public void start() {
        if (running.compareAndSet(false, true)) {
            scheduler.scheduleWithFixedDelay(this::reclaimCycle, intervalMs, intervalMs, TimeUnit.MILLISECONDS);
            log.info("lease reclaimer started: interval={}ms", intervalMs);
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

    private void reclaimCycle() {
        try {
            outbox.reclaimExpiredLeases();
        } catch (Exception e) {
            log.warn("reclaim cycle error: {}", e.getMessage());
        }
    }
}
