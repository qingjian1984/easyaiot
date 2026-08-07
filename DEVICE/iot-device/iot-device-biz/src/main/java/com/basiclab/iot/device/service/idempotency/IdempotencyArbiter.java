package com.basiclab.iot.device.service.idempotency;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.Objects;

/**
 * TD-004 §7.12：幂等争抢裁决（领域层，不触碰数据库）。
 * 跨副本首插争抢由 §7.10 唯一约束在数据库层承担（UNIQUE 作用域）；
 * 本类裁决冲突方读到既有记录后的行为：hash 不同 409 绝不覆盖；
 * 相同则重放已完成结果或返回处理中；IN_PROGRESS 超过恢复阈值才可重试。
 * 恢复阈值为候选值，由调用方注入。Java 8 兼容。
 */
public final class IdempotencyArbiter {

    public enum State {
        IN_PROGRESS, SUCCEEDED, FAILED_FINAL
    }

    public enum Outcome {
        /** 无既有记录：执行首个 insert（争抢由数据库唯一约束裁决）。 */
        PROCEED,
        /** 已完成（含终态失败）：重放已存响应。 */
        REPLAY,
        /** IN_PROGRESS 超过恢复阈值且无活动事务：允许重试。 */
        RETRYABLE
    }

    /** 既有记录的只读视图。 */
    public static final class RecordView {
        private final byte[] requestHash;
        private final State state;
        private final Integer httpStatus;
        private final String resultRef;
        private final Instant updatedAt;

        public RecordView(byte[] requestHash, State state, Integer httpStatus,
                          String resultRef, Instant updatedAt) {
            this.requestHash = Objects.requireNonNull(requestHash, "requestHash").clone();
            this.state = Objects.requireNonNull(state, "state");
            this.httpStatus = httpStatus;
            this.resultRef = resultRef;
            this.updatedAt = Objects.requireNonNull(updatedAt, "updatedAt");
        }

        public byte[] requestHash() {
            return requestHash.clone();
        }

        public State state() {
            return state;
        }

        public Integer httpStatus() {
            return httpStatus;
        }

        public String resultRef() {
            return resultRef;
        }

        public Instant updatedAt() {
            return updatedAt;
        }
    }

    /** 裁决结论。 */
    public static final class Decision {
        private final Outcome outcome;
        private final Integer httpStatus;
        private final String resultRef;

        private Decision(Outcome outcome, Integer httpStatus, String resultRef) {
            this.outcome = outcome;
            this.httpStatus = httpStatus;
            this.resultRef = resultRef;
        }

        static Decision of(Outcome outcome) {
            return new Decision(outcome, null, null);
        }

        static Decision replay(RecordView record) {
            return new Decision(Outcome.REPLAY, record.httpStatus(), record.resultRef());
        }

        public Outcome outcome() {
            return outcome;
        }

        public Integer httpStatus() {
            return httpStatus;
        }

        public String resultRef() {
            return resultRef;
        }
    }

    private final Duration recoveryThreshold;

    public IdempotencyArbiter(Duration recoveryThreshold) {
        this.recoveryThreshold = Objects.requireNonNull(recoveryThreshold, "recoveryThreshold");
    }

    public Decision decide(RecordView existing, byte[] requestHash, Instant now) {
        if (existing == null) {
            return Decision.of(Outcome.PROCEED);
        }
        if (!Arrays.equals(existing.requestHash(), requestHash)) {
            throw new IllegalArgumentException(
                    "IDEMPOTENCY_KEY_REUSED: 相同 key 被用于不同请求，绝不覆盖");
        }
        switch (existing.state()) {
            case SUCCEEDED:
            case FAILED_FINAL:
                // 终态（成功或最终失败）：重放已存响应，客户端拿到一致结论
                return Decision.replay(existing);
            default:
                boolean pastThreshold = !existing.updatedAt().plus(recoveryThreshold).isAfter(now);
                if (!pastThreshold) {
                    throw new IllegalArgumentException(
                            "IDEMPOTENCY_IN_PROGRESS: 相同请求仍在处理，可按 Retry-After 重试");
                }
                return Decision.of(Outcome.RETRYABLE);
        }
    }

    /** key_hash：对客户端 key 的服务端 HMAC-SHA-256（32 字节），不存原文。 */
    public static byte[] keyHash(byte[] serverSecret, String clientKey) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(serverSecret, "HmacSHA256"));
            return mac.doFinal(clientKey.getBytes(StandardCharsets.UTF_8));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("当前 JDK 缺少 HmacSHA256", e);
        }
    }

    /** request_hash：method/path/规范 payload 的 SHA-256（32 字节）。 */
    public static byte[] requestHash(String method, String path, String canonicalPayload) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            String material = method + "\n" + path + "\n" + canonicalPayload;
            return digest.digest(material.getBytes(StandardCharsets.UTF_8));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("当前 JDK 缺少 SHA-256", e);
        }
    }
}
