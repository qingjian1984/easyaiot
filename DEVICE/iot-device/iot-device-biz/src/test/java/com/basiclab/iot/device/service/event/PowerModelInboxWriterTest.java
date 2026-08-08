package com.basiclab.iot.device.service.event;

import com.basiclab.iot.device.event.PowerModelEventEnvelope;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * ADR-014 §决策 MUST + P-07：消费者 Inbox 写入编排合同（fake 仓储，不触碰 DB）。
 * PROCEED/争抢落败/DUPLICATE/RETRYABLE/异 hash 隔离/未知主版本隔离/维持隔离七条路径。
 */
class PowerModelInboxWriterTest {

    private static final Instant NOW = Instant.parse("2026-08-08T00:00:00Z");
    private static final String EVENT_ID = "00000000-0000-0000-0000-000000000001";
    private static final String PAYLOAD = "{\"eventId\":\"" + EVENT_ID + "\"}";

    @Test
    void proceedInsertsReceivedAndReturnsProcess() {
        FakeRepository repository = new FakeRepository();
        PowerModelInboxWriter.IngestResult result = writer(repository).ingest(envelope(1), PAYLOAD, NOW);

        assertEquals(PowerModelInboxWriter.Action.PROCESS, result.action());
        assertFalse(result.isCritical());
        assertEquals(1, repository.inserts.size());
        assertEquals(EVENT_ID, repository.inserts.get(0));
    }

    @Test
    void lostContentionDoesNotProcess() {
        FakeRepository repository = new FakeRepository();
        repository.insertSucceeds = false;
        PowerModelInboxWriter.IngestResult result = writer(repository).ingest(envelope(1), PAYLOAD, NOW);

        assertEquals(PowerModelInboxWriter.Action.LOST_CONTENTION, result.action(),
                "首插争抢落败：本轮不执行、不提交 offset，待重读裁决");
    }

    @Test
    void duplicateSkipsProcessing() {
        FakeRepository repository = new FakeRepository();
        repository.existing = view(PAYLOAD, InboxArbiter.Status.PROCESSED);
        PowerModelInboxWriter.IngestResult result = writer(repository).ingest(envelope(1), PAYLOAD, NOW);

        assertEquals(PowerModelInboxWriter.Action.DUPLICATE, result.action());
        assertTrue(repository.inserts.isEmpty());
        assertTrue(repository.quarantines.isEmpty());
    }

    @Test
    void retryableAfterCrashReprocesses() {
        FakeRepository repository = new FakeRepository();
        repository.existing = view(PAYLOAD, InboxArbiter.Status.RECEIVED);
        PowerModelInboxWriter.IngestResult result = writer(repository).ingest(envelope(1), PAYLOAD, NOW);

        assertEquals(PowerModelInboxWriter.Action.PROCESS, result.action());
        assertTrue(repository.inserts.isEmpty(), "RECEIVED 行已存在，不重复首插");
    }

    @Test
    void hashConflictQuarantinesWithCritical() {
        FakeRepository repository = new FakeRepository();
        repository.existing = view("{\"other\":true}", InboxArbiter.Status.PROCESSED);
        PowerModelInboxWriter.IngestResult result = writer(repository).ingest(envelope(1), PAYLOAD, NOW);

        assertEquals(PowerModelInboxWriter.Action.QUARANTINED, result.action());
        assertTrue(result.isCritical(), "同 ID 异 hash 必须 critical 告警");
        assertEquals(Arrays.asList(EVENT_ID + ":MODEL_EVENT_HASH_CONFLICT"), repository.quarantines);
    }

    @Test
    void unknownMajorVersionQuarantinesWithCritical() {
        FakeRepository repository = new FakeRepository();
        PowerModelInboxWriter.IngestResult result = writer(repository).ingest(envelope(2), PAYLOAD, NOW);

        assertEquals(PowerModelInboxWriter.Action.QUARANTINED, result.action());
        assertTrue(result.isCritical());
        assertEquals(Arrays.asList(EVENT_ID + ":MODEL_EVENT_UNKNOWN_MAJOR_VERSION"), repository.quarantines);
    }

    @Test
    void quarantinedRecordAwaitsDispositionSilently() {
        FakeRepository repository = new FakeRepository();
        repository.existing = view(PAYLOAD, InboxArbiter.Status.QUARANTINED);
        PowerModelInboxWriter.IngestResult result = writer(repository).ingest(envelope(1), PAYLOAD, NOW);

        assertEquals(PowerModelInboxWriter.Action.QUARANTINED, result.action());
        assertFalse(result.isCritical(), "已隔离记录维持隔离，不重复升级告警");
        assertTrue(repository.quarantines.isEmpty());
    }

    @Test
    void dualWindowAcceptsNextMajor() {
        FakeRepository repository = new FakeRepository();
        PowerModelInboxWriter dualWriter =
                new PowerModelInboxWriter(repository, new java.util.HashSet<Integer>(Arrays.asList(1, 2)),
                new RecordingEventMetrics());
        PowerModelInboxWriter.IngestResult result = dualWriter.ingest(envelope(2), PAYLOAD, NOW);

        assertEquals(PowerModelInboxWriter.Action.PROCESS, result.action());
    }

    @Test
    void markProcessedDelegates() {
        FakeRepository repository = new FakeRepository();
        writer(repository).markProcessed(EVENT_ID, NOW);
        assertEquals(Arrays.asList(EVENT_ID + "@" + NOW), repository.processed);
    }

    @Test
    void quarantinePathsIncrementMetric() {
        RecordingEventMetrics metrics = new RecordingEventMetrics();

        // 路径一：同 ID 异 hash 隔离（existing 记录 hash 与入站不同）。
        FakeRepository conflictRepository = new FakeRepository();
        conflictRepository.existing = view("{\"other\":true}", InboxArbiter.Status.PROCESSED);
        new PowerModelInboxWriter(conflictRepository, Collections.singleton(1), metrics)
                .ingest(envelope(1), PAYLOAD, NOW);
        // 路径二：未知主版本隔离（无 existing 记录，按 schemaVersion 裁决）。
        new PowerModelInboxWriter(new FakeRepository(), Collections.singleton(1), metrics)
                .ingest(envelope(2), PAYLOAD, NOW);

        assertEquals(2, metrics.quarantined, "两条 critical 隔离路径都必须计数");
        assertEquals(0, metrics.published("published"));
        assertTrue(metrics.deliveryDurations.isEmpty());
    }

    private static PowerModelInboxWriter writer(FakeRepository repository) {
        return new PowerModelInboxWriter(repository, Collections.singleton(1), new RecordingEventMetrics());
    }

    private static PowerModelEventEnvelope envelope(int schemaVersion) {
        Map<String, Object> data = new HashMap<String, Object>();
        data.put("templateCode", "power.high_voltage_cabinet");
        return PowerModelEventEnvelope.of(EVENT_ID,
                "POWER_MODEL_TEMPLATE_PUBLISHED_V" + schemaVersion, schemaVersion,
                "1", "power_model_template", "1001",
                "2026-08-08T00:00:00Z", "req-1", "", data);
    }

    private static InboxArbiter.RecordView view(String payload, InboxArbiter.Status status) {
        return new InboxArbiter.RecordView(PowerModelEventEnvelope.payloadHash(payload), status);
    }

    private static final class FakeRepository implements PowerModelInboxRepository {
        InboxArbiter.RecordView existing;
        boolean insertSucceeds = true;
        final List<String> inserts = new ArrayList<String>();
        final List<String> processed = new ArrayList<String>();
        final List<String> quarantines = new ArrayList<String>();

        @Override
        public InboxArbiter.RecordView findByEventId(String eventId) {
            return existing;
        }

        @Override
        public boolean insertReceived(String eventId, long tenantId, String eventType, String payloadHash) {
            if (insertSucceeds) {
                inserts.add(eventId);
            }
            return insertSucceeds;
        }

        @Override
        public void markProcessed(String eventId, Instant processedAt) {
            processed.add(eventId + "@" + processedAt);
        }

        @Override
        public void upsertQuarantined(String eventId, long tenantId, String eventType,
                                      String payloadHash, String errorCode, String errorDigest) {
            quarantines.add(eventId + ":" + errorCode);
        }
    }
}
