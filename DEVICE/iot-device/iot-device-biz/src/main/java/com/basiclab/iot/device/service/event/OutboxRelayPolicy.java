package com.basiclab.iot.device.service.event;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;

/**
 * ADR-014 §验证 OUT-001～004：Outbox 投递策略（领域层，不触碰数据库与 Kafka）。
 * 覆盖并发 claim（仅 PENDING 到期或 PUBLISHING 租约过期可认领）、
 * 发送后回写崩溃的租约恢复、retryable/final 错误分流、
 * 有界重试（指数退避 base→cap，超限进 DEAD_LETTER/DLQ）。
 * 退避基数/上限与最大尝试次数为注入参数（候选值由配置清单冻结）。Java 8 兼容。
 */
public final class OutboxRelayPolicy {

    /** Outbox 记录状态（与 DDL CHECK 一致）。 */
    public enum Status {
        PENDING, PUBLISHING, PUBLISHED, DEAD_LETTER
    }

    /** 认领裁决。 */
    public enum ClaimDecision {
        /** 可认领：PENDING 且到期，或 PUBLISHING 但租约已过期（崩溃恢复）。 */
        CLAIM,
        /** 跳过：PENDING 未到 nextAttemptAt。 */
        SKIP_NOT_DUE,
        /** 跳过：PUBLISHING 且租约未过期（他副本持有）。 */
        SKIP_LEASED,
        /** 跳过：终态（PUBLISHED/DEAD_LETTER）不再投递。 */
        SKIP_TERMINAL
    }

    /** 失败后处置。 */
    public enum FailureDecision {
        /** 可重试错误且未超限：按指数退避安排下次尝试。 */
        RETRY,
        /** final 错误或重试超限：进入 DEAD_LETTER 并投递 DLQ。 */
        DEAD_LETTER
    }

    /**
     * 并发 claim 裁决（OUT-001/003）。
     *
     * @param status         当前状态
     * @param nextAttemptAt  下次尝试时间（PENDING 有效；不得为 null）
     * @param leaseUntil     租约到期时间（PUBLISHING 有效，可为 null 视为已过期）
     * @param now            当前时间
     */
    public static ClaimDecision claimable(Status status, Instant nextAttemptAt,
                                          Instant leaseUntil, Instant now) {
        Objects.requireNonNull(status, "status");
        Objects.requireNonNull(nextAttemptAt, "nextAttemptAt");
        Objects.requireNonNull(now, "now");
        if (status == Status.PUBLISHED || status == Status.DEAD_LETTER) {
            return ClaimDecision.SKIP_TERMINAL;
        }
        if (status == Status.PUBLISHING) {
            if (leaseUntil != null && leaseUntil.isAfter(now)) {
                return ClaimDecision.SKIP_LEASED;
            }
            // 租约缺失或已过期：持有副本崩溃，允许恢复性认领。
            return ClaimDecision.CLAIM;
        }
        // PENDING
        return nextAttemptAt.isAfter(now) ? ClaimDecision.SKIP_NOT_DUE : ClaimDecision.CLAIM;
    }

    /**
     * 发送失败后处置（OUT-004）。
     *
     * @param retryableError true=可重试错误（连接/超时/可重试 broker 错误）；
     *                       false=final 错误（消息超限、序列化失败、未知 topic）
     * @param attempts       含本次在内的已尝试次数（>=1）
     * @param maxAttempts    最大尝试次数（>0）
     */
    public static FailureDecision afterFailure(boolean retryableError, int attempts, int maxAttempts) {
        if (maxAttempts < 1) {
            throw new IllegalArgumentException("MODEL_EVENT_RETRY_POLICY_INVALID: maxAttempts 必须 >= 1");
        }
        if (attempts < 1) {
            throw new IllegalArgumentException("MODEL_EVENT_RETRY_POLICY_INVALID: attempts 必须 >= 1");
        }
        if (!retryableError) {
            return FailureDecision.DEAD_LETTER;
        }
        return attempts >= maxAttempts ? FailureDecision.DEAD_LETTER : FailureDecision.RETRY;
    }

    /**
     * 指数退避：base * 2^(attempts-1)，封顶 cap（ADR-014 候选 1s→16s）。
     *
     * @param attempts 含本次在内的已尝试次数（>=1）；第 1 次失败后退避 base
     * @param base     退避基数（>0）
     * @param cap      退避上限（>= base）
     * @param now      当前时间
     * @return 下次尝试时间
     */
    public static Instant nextAttemptAt(int attempts, Duration base, Duration cap, Instant now) {
        Objects.requireNonNull(base, "base");
        Objects.requireNonNull(cap, "cap");
        Objects.requireNonNull(now, "now");
        if (attempts < 1) {
            throw new IllegalArgumentException("MODEL_EVENT_RETRY_POLICY_INVALID: attempts 必须 >= 1");
        }
        if (base.isNegative() || base.isZero()) {
            throw new IllegalArgumentException("MODEL_EVENT_RETRY_POLICY_INVALID: base 必须 > 0");
        }
        if (cap.compareTo(base) < 0) {
            throw new IllegalArgumentException("MODEL_EVENT_RETRY_POLICY_INVALID: cap 不得小于 base");
        }
        long baseMillis = base.toMillis();
        long capMillis = cap.toMillis();
        long delay = baseMillis;
        // attempts=1 → base；之后每次翻倍，封顶 cap；防溢出先比后乘。
        for (int i = 1; i < attempts && delay < capMillis; i++) {
            delay = delay > capMillis / 2 ? capMillis : delay * 2;
        }
        return now.plusMillis(Math.min(delay, capMillis));
    }
}
