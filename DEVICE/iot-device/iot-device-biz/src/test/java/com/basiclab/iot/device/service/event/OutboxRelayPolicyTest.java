package com.basiclab.iot.device.service.event;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * ADR-014 §验证 OUT-001～004：Outbox 投递策略合同。
 * 并发 claim / 发送后回写崩溃租约恢复 / retryable 与 final 错误分流 / 有界指数退避。
 */
class OutboxRelayPolicyTest {

    private static final Instant NOW = Instant.parse("2026-08-08T00:00:00Z");
    private static final Duration BASE = Duration.ofSeconds(1);
    private static final Duration CAP = Duration.ofSeconds(16);

    @Test
    void pendingDueIsClaimable() {
        assertEquals(OutboxRelayPolicy.ClaimDecision.CLAIM,
                OutboxRelayPolicy.claimable(OutboxRelayPolicy.Status.PENDING,
                        NOW.minusSeconds(1), null, NOW));
        assertEquals(OutboxRelayPolicy.ClaimDecision.CLAIM,
                OutboxRelayPolicy.claimable(OutboxRelayPolicy.Status.PENDING,
                        NOW, null, NOW));
    }

    @Test
    void pendingNotDueIsSkipped() {
        assertEquals(OutboxRelayPolicy.ClaimDecision.SKIP_NOT_DUE,
                OutboxRelayPolicy.claimable(OutboxRelayPolicy.Status.PENDING,
                        NOW.plusSeconds(5), null, NOW));
    }

    @Test
    void publishingWithLiveLeaseIsSkipped() {
        assertEquals(OutboxRelayPolicy.ClaimDecision.SKIP_LEASED,
                OutboxRelayPolicy.claimable(OutboxRelayPolicy.Status.PUBLISHING,
                        NOW, NOW.plusSeconds(30), NOW));
    }

    @Test
    void publishingWithExpiredLeaseIsRecoverable() {
        assertEquals(OutboxRelayPolicy.ClaimDecision.CLAIM,
                OutboxRelayPolicy.claimable(OutboxRelayPolicy.Status.PUBLISHING,
                        NOW, NOW.minusSeconds(1), NOW));
    }

    @Test
    void publishingWithMissingLeaseIsRecoverable() {
        assertEquals(OutboxRelayPolicy.ClaimDecision.CLAIM,
                OutboxRelayPolicy.claimable(OutboxRelayPolicy.Status.PUBLISHING,
                        NOW, null, NOW));
    }

    @Test
    void terminalStatesAreNeverClaimed() {
        assertEquals(OutboxRelayPolicy.ClaimDecision.SKIP_TERMINAL,
                OutboxRelayPolicy.claimable(OutboxRelayPolicy.Status.PUBLISHED,
                        NOW, null, NOW));
        assertEquals(OutboxRelayPolicy.ClaimDecision.SKIP_TERMINAL,
                OutboxRelayPolicy.claimable(OutboxRelayPolicy.Status.DEAD_LETTER,
                        NOW, null, NOW));
    }

    @Test
    void finalErrorGoesDeadLetterImmediately() {
        assertEquals(OutboxRelayPolicy.FailureDecision.DEAD_LETTER,
                OutboxRelayPolicy.afterFailure(false, 1, 5));
    }

    @Test
    void retryableErrorRetriesBelowLimit() {
        assertEquals(OutboxRelayPolicy.FailureDecision.RETRY,
                OutboxRelayPolicy.afterFailure(true, 1, 5));
        assertEquals(OutboxRelayPolicy.FailureDecision.RETRY,
                OutboxRelayPolicy.afterFailure(true, 4, 5));
    }

    @Test
    void retryableErrorExhaustionGoesDeadLetter() {
        assertEquals(OutboxRelayPolicy.FailureDecision.DEAD_LETTER,
                OutboxRelayPolicy.afterFailure(true, 5, 5));
        assertEquals(OutboxRelayPolicy.FailureDecision.DEAD_LETTER,
                OutboxRelayPolicy.afterFailure(true, 1, 1));
    }

    @Test
    void backoffDoublesFromBaseToCap() {
        // ADR-014 候选退避：1s→2s→4s→8s→16s，之后封顶 16s。
        assertEquals(NOW.plusSeconds(1), OutboxRelayPolicy.nextAttemptAt(1, BASE, CAP, NOW));
        assertEquals(NOW.plusSeconds(2), OutboxRelayPolicy.nextAttemptAt(2, BASE, CAP, NOW));
        assertEquals(NOW.plusSeconds(4), OutboxRelayPolicy.nextAttemptAt(3, BASE, CAP, NOW));
        assertEquals(NOW.plusSeconds(8), OutboxRelayPolicy.nextAttemptAt(4, BASE, CAP, NOW));
        assertEquals(NOW.plusSeconds(16), OutboxRelayPolicy.nextAttemptAt(5, BASE, CAP, NOW));
        assertEquals(NOW.plusSeconds(16), OutboxRelayPolicy.nextAttemptAt(6, BASE, CAP, NOW));
        assertEquals(NOW.plusSeconds(16), OutboxRelayPolicy.nextAttemptAt(100, BASE, CAP, NOW));
    }

    @Test
    void invalidPolicyParametersRejected() {
        IllegalArgumentException e1 = assertThrows(IllegalArgumentException.class,
                () -> OutboxRelayPolicy.afterFailure(true, 0, 5));
        assertTrue(e1.getMessage().startsWith("MODEL_EVENT_RETRY_POLICY_INVALID"));
        IllegalArgumentException e2 = assertThrows(IllegalArgumentException.class,
                () -> OutboxRelayPolicy.nextAttemptAt(1, Duration.ZERO, CAP, NOW));
        assertTrue(e2.getMessage().startsWith("MODEL_EVENT_RETRY_POLICY_INVALID"));
        IllegalArgumentException e3 = assertThrows(IllegalArgumentException.class,
                () -> OutboxRelayPolicy.nextAttemptAt(1, CAP, BASE, NOW));
        assertTrue(e3.getMessage().startsWith("MODEL_EVENT_RETRY_POLICY_INVALID"));
    }
}
