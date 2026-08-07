package com.basiclab.iot.device.service.idempotency;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * TD-004 §7.12：幂等争抢与重放语义（领域层，不触碰数据库）。
 * 跨副本首插争抢由 §7.10 唯一约束在数据库层承担；本类裁决"抢到/读到既有记录后怎么办"。
 */
class IdempotencyArbiterTest {

    private static final Duration RECOVERY_THRESHOLD = Duration.ofMinutes(5);
    private final IdempotencyArbiter arbiter = new IdempotencyArbiter(RECOVERY_THRESHOLD);

    @Test
    void noExistingRecordMeansFirstInsertWins() {
        IdempotencyArbiter.Decision decision = arbiter.decide(null, hash("req"), Instant.now());
        assertEquals(IdempotencyArbiter.Outcome.PROCEED, decision.outcome());
    }

    @Test
    void sameKeyDifferentRequestHashIsConflict() {
        IdempotencyArbiter.RecordView existing = record(hash("other"),
                IdempotencyArbiter.State.SUCCEEDED, 200, Instant.now());
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> arbiter.decide(existing, hash("req"), Instant.now()));
        assertTrue(error.getMessage().startsWith("IDEMPOTENCY_KEY_REUSED"),
                "相同 key 不同 request hash 必须 409 且绝不覆盖");
    }

    @Test
    void sameHashSucceededReplaysStoredResponse() {
        IdempotencyArbiter.RecordView existing = record(hash("req"),
                IdempotencyArbiter.State.SUCCEEDED, 200, Instant.now());
        IdempotencyArbiter.Decision decision = arbiter.decide(existing, hash("req"), Instant.now());
        assertEquals(IdempotencyArbiter.Outcome.REPLAY, decision.outcome());
        assertEquals(200, decision.httpStatus());
    }

    @Test
    void sameHashFailedFinalReplaysTerminalResponse() {
        // FAILED_FINAL 是终态：重放已存失败响应，保证客户端拿到一致结论
        IdempotencyArbiter.RecordView existing = record(hash("req"),
                IdempotencyArbiter.State.FAILED_FINAL, 422, Instant.now());
        IdempotencyArbiter.Decision decision = arbiter.decide(existing, hash("req"), Instant.now());
        assertEquals(IdempotencyArbiter.Outcome.REPLAY, decision.outcome());
        assertEquals(422, decision.httpStatus());
    }

    @Test
    void inProgressWithinThresholdReturnsConflict() {
        IdempotencyArbiter.RecordView existing = record(hash("req"),
                IdempotencyArbiter.State.IN_PROGRESS, null, Instant.now());
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> arbiter.decide(existing, hash("req"), Instant.now()));
        assertTrue(error.getMessage().startsWith("IDEMPOTENCY_IN_PROGRESS"));
    }

    @Test
    void inProgressBeyondRecoveryThresholdBecomesRetryable() {
        Instant now = Instant.now();
        IdempotencyArbiter.RecordView stale = record(hash("req"),
                IdempotencyArbiter.State.IN_PROGRESS, null, now.minus(RECOVERY_THRESHOLD.plusSeconds(1)));
        IdempotencyArbiter.Decision decision = arbiter.decide(stale, hash("req"), now);
        assertEquals(IdempotencyArbiter.Outcome.RETRYABLE, decision.outcome());
    }

    @Test
    void keyAndRequestHashAreFixedWidthDigests() {
        byte[] keyHash = IdempotencyArbiter.keyHash("server-secret".getBytes(StandardCharsets.UTF_8), "client-key-1");
        byte[] requestHash = IdempotencyArbiter.requestHash("POST", "/api/v1/power/x", "{\"a\":1}");
        assertEquals(32, keyHash.length, "key_hash 为 HMAC-SHA-256 的 32 字节");
        assertEquals(32, requestHash.length, "request_hash 为 SHA-256 的 32 字节");
        assertTrue(Arrays.equals(keyHash,
                IdempotencyArbiter.keyHash("server-secret".getBytes(StandardCharsets.UTF_8), "client-key-1")),
                "相同输入摘要稳定");
        assertTrue(!Arrays.equals(requestHash,
                IdempotencyArbiter.requestHash("POST", "/api/v1/power/x", "{\"a\":2}")),
                "payload 变化必须改变 request_hash");
    }

    private static IdempotencyArbiter.RecordView record(byte[] requestHash, IdempotencyArbiter.State state,
                                                        Integer httpStatus, Instant updatedAt) {
        return new IdempotencyArbiter.RecordView(requestHash, state, httpStatus, "result-1", updatedAt);
    }

    private static byte[] hash(String seed) {
        return IdempotencyArbiter.requestHash("POST", "/op", seed);
    }
}
