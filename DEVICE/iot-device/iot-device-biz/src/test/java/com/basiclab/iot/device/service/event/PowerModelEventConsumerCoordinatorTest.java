package com.basiclab.iot.device.service.event;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * ADR-014 §决策 MUST + P-07：消费编排器合同（fake 端口，不触碰 Kafka/DB）。
 * 覆盖：处理成功→markProcessed+COMMIT、重复→COMMIT、隔离→COMMIT、
 * 争抢落败→NO_COMMIT、poison→DLQ+COMMIT、处理器缺失→DLQ+COMMIT、
 * retryable 退避→NO_COMMIT、retryable 超限→DLQ+COMMIT、final→DLQ+COMMIT、
 * DLQ 投递失败→抛错（适配层不提交 offset）。
 */
class PowerModelEventConsumerCoordinatorTest {

    private static final Instant NOW = Instant.parse("2026-08-08T00:00:00Z");
    private static final String EVENT_ID = "00000000-0000-0000-0000-000000000001";
    private static final String RAW = "{"
            + "\"eventId\":\"" + EVENT_ID + "\","
            + "\"eventType\":\"POWER_MODEL_TEMPLATE_PUBLISHED_V1\","
            + "\"schemaVersion\":1,"
            + "\"tenantId\":\"1\","
            + "\"aggregateType\":\"power_model_template\","
            + "\"aggregateId\":\"1001\","
            + "\"occurredAt\":\"2026-08-08T00:00:00Z\","
            + "\"requestId\":\"req-1\","
            + "\"traceId\":\"\","
            + "\"data\":{\"templateCode\":\"power.high_voltage_cabinet\"}"
            + "}";

    @Test
    void processedMarksAndCommits() {
        Fixture f = new Fixture();
        PowerModelEventConsumerCoordinator.ConsumeDecision decision =
                f.coordinator().consume(RAW, 0, NOW);

        assertEquals(PowerModelEventConsumerCoordinator.Action.COMMIT_OFFSET, decision.action());
        assertEquals("processed", decision.detail());
        assertEquals(1, f.handled.size());
        assertEquals(1, f.inbox.processed.size());
        assertTrue(f.dlq.sent.isEmpty());
    }

    @Test
    void duplicateCommitsWithoutHandling() {
        Fixture f = new Fixture();
        f.inbox.existing = view(RAW, InboxArbiter.Status.PROCESSED);
        PowerModelEventConsumerCoordinator.ConsumeDecision decision =
                f.coordinator().consume(RAW, 0, NOW);

        assertEquals(PowerModelEventConsumerCoordinator.Action.COMMIT_OFFSET, decision.action());
        assertEquals("duplicate", decision.detail());
        assertTrue(f.handled.isEmpty());
        assertTrue(f.inbox.processed.isEmpty());
    }

    @Test
    void quarantinedCommitsWithoutHandling() {
        Fixture f = new Fixture();
        f.inbox.existing = view("{\"different\":true}", InboxArbiter.Status.PROCESSED);
        PowerModelEventConsumerCoordinator.ConsumeDecision decision =
                f.coordinator().consume(RAW, 0, NOW);

        assertEquals(PowerModelEventConsumerCoordinator.Action.COMMIT_OFFSET, decision.action());
        assertEquals("quarantined-critical", decision.detail());
        assertEquals(1, f.inbox.quarantines.size());
        assertTrue(f.handled.isEmpty());
    }

    @Test
    void lostContentionDoesNotCommit() {
        Fixture f = new Fixture();
        f.inbox.insertSucceeds = false;
        PowerModelEventConsumerCoordinator.ConsumeDecision decision =
                f.coordinator().consume(RAW, 0, NOW);

        assertEquals(PowerModelEventConsumerCoordinator.Action.NO_COMMIT, decision.action());
        assertNull(decision.nextAttemptAt());
        assertTrue(f.handled.isEmpty());
    }

    @Test
    void poisonGoesDlqAndCommits() {
        Fixture f = new Fixture();
        PowerModelEventConsumerCoordinator.ConsumeDecision decision =
                f.coordinator().consume("{not-json", 0, NOW);

        assertEquals(PowerModelEventConsumerCoordinator.Action.COMMIT_OFFSET, decision.action());
        assertEquals("poison", decision.detail());
        assertEquals(1, f.dlq.sent.size());
        assertTrue(f.handled.isEmpty());
    }

    @Test
    void missingHandlerGoesDlqAndCommits() {
        Fixture f = new Fixture();
        f.handlers.clear();
        PowerModelEventConsumerCoordinator.ConsumeDecision decision =
                f.coordinator().consume(RAW, 0, NOW);

        assertEquals(PowerModelEventConsumerCoordinator.Action.COMMIT_OFFSET, decision.action());
        assertEquals("handler-missing", decision.detail());
        assertEquals(1, f.dlq.sent.size());
        assertTrue(f.inbox.processed.isEmpty(), "未处理成功不得回写 PROCESSED");
    }

    @Test
    void retryableFailureBacksOffWithoutCommit() {
        Fixture f = new Fixture();
        f.handlerFailure = new PowerModelEventHandlerRegistry.PowerModelEventProcessingException(
                true, "MODEL_EVENT_DOWNSTREAM_TIMEOUT", "timeout", null);
        PowerModelEventConsumerCoordinator.ConsumeDecision decision =
                f.coordinator().consume(RAW, 0, NOW);

        assertEquals(PowerModelEventConsumerCoordinator.Action.NO_COMMIT, decision.action());
        assertEquals(NOW.plusSeconds(1), decision.nextAttemptAt(), "第 1 次失败退避 base 1s");
        assertTrue(f.inbox.processed.isEmpty());
        assertTrue(f.dlq.sent.isEmpty());
    }

    @Test
    void retryableBackoffDoublesWithAttempts() {
        Fixture f = new Fixture();
        f.handlerFailure = new PowerModelEventHandlerRegistry.PowerModelEventProcessingException(
                true, "MODEL_EVENT_DOWNSTREAM_TIMEOUT", "timeout", null);
        PowerModelEventConsumerCoordinator.ConsumeDecision decision =
                f.coordinator().consume(RAW, 2, NOW);

        assertEquals(NOW.plusSeconds(4), decision.nextAttemptAt(), "attempts=3 → 退避 4s");
    }

    @Test
    void retryableExhaustionGoesDlqAndCommits() {
        Fixture f = new Fixture();
        f.handlerFailure = new PowerModelEventHandlerRegistry.PowerModelEventProcessingException(
                true, "MODEL_EVENT_DOWNSTREAM_TIMEOUT", "timeout", null);
        PowerModelEventConsumerCoordinator.ConsumeDecision decision =
                f.coordinator().consume(RAW, 4, NOW);

        // maxAttempts=5：attempts=5 达到上限 → DLQ + COMMIT。
        assertEquals(PowerModelEventConsumerCoordinator.Action.COMMIT_OFFSET, decision.action());
        assertEquals("dlq:MODEL_EVENT_DOWNSTREAM_TIMEOUT", decision.detail());
        assertEquals(1, f.dlq.sent.size());
    }

    @Test
    void finalFailureGoesDlqImmediately() {
        Fixture f = new Fixture();
        f.handlerFailure = new PowerModelEventHandlerRegistry.PowerModelEventProcessingException(
                false, "MODEL_EVENT_BINDING_REJECTED", "rejected", null);
        PowerModelEventConsumerCoordinator.ConsumeDecision decision =
                f.coordinator().consume(RAW, 0, NOW);

        assertEquals(PowerModelEventConsumerCoordinator.Action.COMMIT_OFFSET, decision.action());
        assertEquals("dlq:MODEL_EVENT_BINDING_REJECTED", decision.detail());
        assertEquals(1, f.dlq.sent.size());
    }

    @Test
    void dlqSendFailureThrowsAndNeverCommits() {
        Fixture f = new Fixture();
        f.dlq.failNext = true;
        IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> f.coordinator().consume("{not-json", 0, NOW));
        assertTrue(error.getMessage().startsWith("MODEL_EVENT_DLQ_SEND_FAILED"),
                "DLQ 投递失败绝不静默：适配层必须不提交 offset");
    }

    private static InboxArbiter.RecordView view(String payload, InboxArbiter.Status status) {
        return new InboxArbiter.RecordView(
                com.basiclab.iot.device.event.PowerModelEventEnvelope.payloadHash(payload), status);
    }

    /** 测试夹具：fake Inbox 仓储、handler 注册表、DLQ transport。 */
    private static final class Fixture {
        final FakeInboxRepository inbox = new FakeInboxRepository();
        final Map<String, PowerModelEventHandlerRegistry.PowerModelEventHandler> handlers =
                new HashMap<String, PowerModelEventHandlerRegistry.PowerModelEventHandler>();
        final List<String> handled = new ArrayList<String>();
        final FakeDlqTransport dlq = new FakeDlqTransport();
        PowerModelEventHandlerRegistry.PowerModelEventProcessingException handlerFailure;

        Fixture() {
            handlers.put("POWER_MODEL_TEMPLATE_PUBLISHED_V1",
                    new PowerModelEventHandlerRegistry.PowerModelEventHandler() {
                        @Override
                        public void handle(
                                com.basiclab.iot.device.event.PowerModelEventEnvelope envelope,
                                String dataJson) {
                            if (handlerFailure != null) {
                                throw handlerFailure;
                            }
                            handled.add(envelope.eventId());
                        }
                    });
        }

        PowerModelEventConsumerCoordinator coordinator() {
            return new PowerModelEventConsumerCoordinator(
                    new PowerModelInboxWriter(inbox, Collections.singleton(1), new RecordingEventMetrics()),
                    new PowerModelEventHandlerRegistry(handlers),
                    dlq, "power-model-release-v1-dlq", 5,
                    Duration.ofSeconds(1), Duration.ofSeconds(16));
        }
    }

    private static final class FakeInboxRepository implements PowerModelInboxRepository {
        InboxArbiter.RecordView existing;
        boolean insertSucceeds = true;
        final List<String> processed = new ArrayList<String>();
        final List<String> quarantines = new ArrayList<String>();

        @Override
        public InboxArbiter.RecordView findByEventId(String eventId) {
            return existing;
        }

        @Override
        public boolean insertReceived(String eventId, long tenantId, String eventType, String payloadHash) {
            return insertSucceeds;
        }

        @Override
        public void markProcessed(String eventId, Instant processedAt) {
            processed.add(eventId);
        }

        @Override
        public void upsertQuarantined(String eventId, long tenantId, String eventType,
                                      String payloadHash, String errorCode, String errorDigest) {
            quarantines.add(eventId + ":" + errorCode);
        }
    }

    private static final class FakeDlqTransport implements PowerModelEventTransport {
        final List<String> sent = new ArrayList<String>();
        boolean failNext;

        @Override
        public TransportResult send(String topic, String key, String payload) {
            if (failNext) {
                return TransportResult.failure(true, "MODEL_EVENT_SEND_RETRYABLE", "x");
            }
            sent.add(payload);
            return TransportResult.success();
        }
    }
}
