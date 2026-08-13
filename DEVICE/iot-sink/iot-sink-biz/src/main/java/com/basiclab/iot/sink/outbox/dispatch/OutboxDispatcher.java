package com.basiclab.iot.sink.outbox.dispatch;

import com.basiclab.iot.sink.telemetry.outbox.ClaimBatchResult;
import com.basiclab.iot.sink.telemetry.outbox.ClaimedEnvelope;
import com.basiclab.iot.sink.telemetry.outbox.TelemetryOutboxPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * TD-002 §11 发送调度器：周期 claim → MQTT publish。
 *
 * <p>100ms 周期 claim（≤100 条），对每条 ClaimedEnvelope 调用 {@link CollectorMqttPublisher#publish}。
 * publish 失败不阻塞下一轮；ACK 异步通过 AckSubscriber → applyAck。
 */
public final class OutboxDispatcher {

    private static final Logger log = LoggerFactory.getLogger(OutboxDispatcher.class);

    private final TelemetryOutboxPort outbox;
    private final CollectorMqttPublisher publisher;
    private final long pollIntervalMs;
    private final int claimBatchSize;
    private final long leaseMs;
    private final ScheduledExecutorService scheduler;
    private final AtomicBoolean running = new AtomicBoolean(false);

    public OutboxDispatcher(TelemetryOutboxPort outbox, CollectorMqttPublisher publisher,
                            long pollIntervalMs, int claimBatchSize, long leaseMs) {
        this.outbox = outbox;
        this.publisher = publisher;
        this.pollIntervalMs = pollIntervalMs;
        this.claimBatchSize = claimBatchSize;
        this.leaseMs = leaseMs;
        this.scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "outbox-dispatcher");
            t.setDaemon(true);
            return t;
        });
    }

    public void start() {
        if (running.compareAndSet(false, true)) {
            scheduler.scheduleWithFixedDelay(this::dispatchCycle, 1000, pollIntervalMs, TimeUnit.MILLISECONDS);
            log.info("outbox dispatcher started: pollInterval={}ms claimBatch={} leaseMs={}",
                    pollIntervalMs, claimBatchSize, leaseMs);
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

    private void dispatchCycle() {
        try {
            ClaimBatchResult result = outbox.claimBatch(claimBatchSize, Duration.ofMillis(leaseMs));
            if (result instanceof ClaimBatchResult.Empty) {
                return;
            }
            for (ClaimedEnvelope envelope : result.envelopes()) {
                try {
                    boolean published = publisher.publish(envelope);
                    if (!published) {
                        log.warn("publish failed for messageId={}, will be reclaimed on lease expiry",
                                envelope.messageId());
                    }
                } catch (Exception e) {
                    log.warn("publish error for messageId={}: {}",
                            envelope.messageId(), e.getMessage());
                }
            }
        } catch (Exception e) {
            log.warn("dispatch cycle error: {}", e.getMessage());
        }
    }
}
