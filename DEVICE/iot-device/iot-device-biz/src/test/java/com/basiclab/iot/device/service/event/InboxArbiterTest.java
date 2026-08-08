package com.basiclab.iot.device.service.event;

import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * ADR-014 §决策 MUST：消费者 Inbox 幂等裁决合同。
 * event_id 首插争抢由数据库 UNIQUE(event_id) 承担；本类裁决读到既有记录后的行为。
 */
class InboxArbiterTest {

    private static final Set<Integer> V1_ONLY = Collections.singleton(1);
    private static final Set<Integer> DUAL_WINDOW = dualWindow();

    private static Set<Integer> dualWindow() {
        Set<Integer> majors = new HashSet<Integer>();
        majors.add(1);
        majors.add(2);
        return Collections.unmodifiableSet(majors);
    }

    @Test
    void noExistingRecordProceeds() {
        InboxArbiter.Decision decision = InboxArbiter.decide(null, 1, hash('a'), V1_ONLY);
        assertEquals(InboxArbiter.Outcome.PROCEED, decision.outcome());
    }

    @Test
    void sameHashProcessedIsDuplicate() {
        InboxArbiter.RecordView existing = record(hash('a'), InboxArbiter.Status.PROCESSED);
        InboxArbiter.Decision decision = InboxArbiter.decide(existing, 1, hash('a'), V1_ONLY);
        assertEquals(InboxArbiter.Outcome.DUPLICATE, decision.outcome());
    }

    @Test
    void sameHashReceivedIsRetryableAfterCrash() {
        InboxArbiter.RecordView existing = record(hash('a'), InboxArbiter.Status.RECEIVED);
        InboxArbiter.Decision decision = InboxArbiter.decide(existing, 1, hash('a'), V1_ONLY);
        assertEquals(InboxArbiter.Outcome.RETRYABLE, decision.outcome());
    }

    @Test
    void sameHashQuarantinedAwaitsDisposition() {
        InboxArbiter.RecordView existing = record(hash('a'), InboxArbiter.Status.QUARANTINED);
        InboxArbiter.Decision decision = InboxArbiter.decide(existing, 1, hash('a'), V1_ONLY);
        assertEquals(InboxArbiter.Outcome.AWAITING_DISPOSITION, decision.outcome());
        assertTrue(decision.isQuarantine(), "已隔离记录维持隔离，等待安全/审计处置");
    }

    @Test
    void sameIdDifferentHashIsQuarantineConflict() {
        InboxArbiter.RecordView existing = record(hash('b'), InboxArbiter.Status.PROCESSED);
        InboxArbiter.Decision decision = InboxArbiter.decide(existing, 1, hash('a'), V1_ONLY);
        assertEquals(InboxArbiter.Outcome.QUARANTINE_HASH_CONFLICT, decision.outcome());
        assertTrue(decision.isQuarantine(), "同 ID 异 hash 必须隔离并 critical，绝不覆盖");
    }

    @Test
    void unknownMajorVersionIsRejected() {
        InboxArbiter.Decision decision = InboxArbiter.decide(null, 2, hash('a'), V1_ONLY);
        assertEquals(InboxArbiter.Outcome.REJECT_UNKNOWN_MAJOR_VERSION, decision.outcome());
        assertTrue(decision.isQuarantine(), "未知主版本写隔离/DLQ 并告警，不标记业务成功");
    }

    @Test
    void unknownMajorVersionRejectedBeforeExistingLookup() {
        InboxArbiter.RecordView existing = record(hash('a'), InboxArbiter.Status.PROCESSED);
        InboxArbiter.Decision decision = InboxArbiter.decide(existing, 9, hash('a'), V1_ONLY);
        assertEquals(InboxArbiter.Outcome.REJECT_UNKNOWN_MAJOR_VERSION, decision.outcome());
    }

    @Test
    void dualVersionWindowAcceptsNextMajor() {
        InboxArbiter.Decision decision = InboxArbiter.decide(null, 2, hash('a'), DUAL_WINDOW);
        assertEquals(InboxArbiter.Outcome.PROCEED, decision.outcome());
    }

    @Test
    void malformedPayloadHashRejected() {
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> InboxArbiter.decide(null, 1, "not-a-hash", V1_ONLY));
        assertTrue(error.getMessage().startsWith("MODEL_EVENT_HASH_INVALID"));
    }

    @Test
    void invalidSchemaVersionRejected() {
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> InboxArbiter.decide(null, 0, hash('a'), V1_ONLY));
        assertTrue(error.getMessage().startsWith("MODEL_EVENT_ENVELOPE_INVALID"));
    }

    private static InboxArbiter.RecordView record(String hash, InboxArbiter.Status status) {
        return new InboxArbiter.RecordView(hash, status);
    }

    /** 构造合法 payload_hash：sha256: + 64 位同一字符（满足格式即可，仲裁只比较相等性）。 */
    private static String hash(char seed) {
        StringBuilder sb = new StringBuilder("sha256:");
        for (int i = 0; i < 64; i++) {
            sb.append(seed);
        }
        return sb.toString();
    }
}
