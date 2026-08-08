package com.basiclab.iot.device.service.event;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * ADR-014：发布器调度驱动合同（Spring Scheduling 选型，2026-08-08 owner 裁定）。
 * 覆盖：轮询以注入时钟调用 relayOnce、单轮异常不外抛（轮询存活）、
 * 构造参数 fail-closed。调度注解本身由 Spring 容器解释，合同只锁定适配行为。
 */
class PowerModelOutboxRelaySchedulerTest {

    private static final Instant NOW = Instant.parse("2026-08-08T00:00:00Z");

    @Test
    void pollDelegatesWithInjectedClock() {
        CountingRelay relay = new CountingRelay();
        PowerModelOutboxRelayScheduler scheduler = new PowerModelOutboxRelayScheduler(
                relay, Clock.fixed(NOW, ZoneOffset.UTC));

        scheduler.poll();

        assertEquals(1, relay.calls);
        assertEquals(NOW, relay.lastNow, "轮询必须使用注入时钟而非系统时钟");
    }

    @Test
    void pollSwallowsFailureAndSurvives() {
        CountingRelay relay = new CountingRelay();
        PowerModelOutboxRelayScheduler scheduler = new PowerModelOutboxRelayScheduler(
                relay, Clock.fixed(NOW, ZoneOffset.UTC));
        relay.toThrow = new IllegalStateException("MODEL_EVENT_SEND_RETRYABLE: transport down");

        scheduler.poll(); // 不抛出：fixedDelay 轮询必须存活
        scheduler.poll();

        assertEquals(2, relay.calls, "失败轮询之后调度器必须继续驱动后续轮询");
    }

    @Test
    void constructorRejectsNullArgs() {
        CountingRelay relay = new CountingRelay();
        assertThrows(NullPointerException.class,
                () -> new PowerModelOutboxRelayScheduler(null, Clock.systemUTC()));
        assertThrows(NullPointerException.class,
                () -> new PowerModelOutboxRelayScheduler(relay, null));
    }

    /** 记录调用并可注入失败的发布器替身。 */
    private static final class CountingRelay extends PowerModelOutboxRelay {
        private int calls;
        private Instant lastNow;
        private RuntimeException toThrow;

        CountingRelay() {
            super(new NoopRepository(), new NoopTransport(), "topic", "owner",
                    Duration.ofSeconds(60), 10, Duration.ofSeconds(1), Duration.ofSeconds(16));
        }

        @Override
        public int relayOnce(Instant now) {
            calls++;
            lastNow = now;
            if (toThrow != null) {
                throw toThrow;
            }
            return 0;
        }
    }

    private static final class NoopRepository implements PowerModelOutboxRepository {
        @Override
        public void insertPending(OutboxEntry entry) {
        }

        @Override
        public List<ClaimedOutboxEntry> claimDue(Instant now, String leaseOwner,
                                                 java.time.Duration leaseDuration, int batchSize) {
            return Collections.emptyList();
        }

        @Override
        public void markPublished(String eventId, Instant publishedAt) {
        }

        @Override
        public void markRetry(String eventId, int retryCount, Instant nextAttemptAt,
                              String errorCode, String errorDigest) {
        }

        @Override
        public void markDeadLetter(String eventId, String errorCode, String errorDigest) {
        }
    }

    private static final class NoopTransport implements PowerModelEventTransport {
        @Override
        public TransportResult send(String topic, String key, String payload) {
            return TransportResult.success();
        }
    }
}
