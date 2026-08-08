package com.basiclab.iot.device.service.event;

import com.basiclab.iot.device.event.PowerModelEventEnvelope;

import java.util.Objects;
import java.util.Set;

/**
 * ADR-014 §决策 MUST + TD-005 migration §4.6.1：消费者 Inbox 幂等裁决（领域层，不触碰数据库）。
 * event_id 全局唯一由 {@code power_model_event_inbox} 的 UNIQUE(event_id) 在数据库层承担；
 * 本类裁决冲突方读到既有记录后的行为：
 * 同 eventId 同 hash 且已处理 → DUPLICATE（不重复执行）；
 * 同 hash 但停留 RECEIVED（发送后回写崩溃/消费中断）→ 允许重试；
 * 同 ID 异 hash → 隔离并 critical（绝不覆盖、绝不标记成功）；
 * 未知主版本 → 隔离/DLQ 并告警，不阻塞其他事件。Java 8 兼容。
 */
public final class InboxArbiter {

    /** Inbox 记录状态（与 DDL CHECK 一致）。 */
    public enum Status {
        RECEIVED, PROCESSED, QUARANTINED
    }

    public enum Outcome {
        /** 无既有记录：首插 RECEIVED 并执行处理（争抢由数据库唯一约束裁决）。 */
        PROCEED,
        /** 同 eventId 同 hash 且 PROCESSED：幂等重复，返回 DUPLICATE，不重复执行。 */
        DUPLICATE,
        /** 同 hash 但停留 RECEIVED：上次处理未完成（崩溃/中断），允许重试。 */
        RETRYABLE,
        /** 同 eventId 异 hash：进入隔离并触发 critical 告警，生产者不得自批自愈。 */
        QUARANTINE_HASH_CONFLICT,
        /** 未知主版本：写隔离/DLQ 并告警；不标记业务成功、不阻塞其他事件。 */
        REJECT_UNKNOWN_MAJOR_VERSION,
        /** 既有记录已隔离：维持隔离，等待安全/审计角色处置，不自动恢复、不重复告警升级。 */
        AWAITING_DISPOSITION
    }

    /** 既有 Inbox 记录的只读视图。 */
    public static final class RecordView {
        private final String payloadHash;
        private final Status status;

        public RecordView(String payloadHash, Status status) {
            this.payloadHash = requireHash(payloadHash);
            this.status = Objects.requireNonNull(status, "status");
        }

        public String payloadHash() {
            return payloadHash;
        }

        public Status status() {
            return status;
        }
    }

    /** 裁决结论。 */
    public static final class Decision {
        private final Outcome outcome;

        private Decision(Outcome outcome) {
            this.outcome = outcome;
        }

        public Outcome outcome() {
            return outcome;
        }

        public boolean isQuarantine() {
            return outcome == Outcome.QUARANTINE_HASH_CONFLICT
                    || outcome == Outcome.REJECT_UNKNOWN_MAJOR_VERSION
                    || outcome == Outcome.AWAITING_DISPOSITION;
        }
    }

    /**
     * 裁决一条 incoming 事件。
     *
     * @param existing         既有 Inbox 记录视图；无记录传 null
     * @param schemaVersion    incoming 事件主版本（取自 Envelope，已与 eventType 后缀一致）
     * @param payloadHash      incoming 载荷哈希（{@code sha256:<64hex>}）
     * @param supportedMajors  消费者支持的主版本集合（M1 为 {1}；双发窗口为 {1,2}）
     */
    public static Decision decide(RecordView existing, int schemaVersion,
                                  String payloadHash, Set<Integer> supportedMajors) {
        Objects.requireNonNull(supportedMajors, "supportedMajors");
        requireHash(payloadHash);
        if (schemaVersion < 1) {
            throw new IllegalArgumentException(
                    "MODEL_EVENT_ENVELOPE_INVALID: schemaVersion 必须 >= 1");
        }
        if (!supportedMajors.contains(schemaVersion)) {
            return new Decision(Outcome.REJECT_UNKNOWN_MAJOR_VERSION);
        }
        if (existing == null) {
            return new Decision(Outcome.PROCEED);
        }
        if (!existing.payloadHash().equals(payloadHash)) {
            return new Decision(Outcome.QUARANTINE_HASH_CONFLICT);
        }
        if (existing.status() == Status.PROCESSED) {
            return new Decision(Outcome.DUPLICATE);
        }
        if (existing.status() == Status.QUARANTINED) {
            // 同 hash 的隔离记录：维持隔离，等待安全/审计处置，不自动恢复。
            return new Decision(Outcome.AWAITING_DISPOSITION);
        }
        return new Decision(Outcome.RETRYABLE);
    }

    private static String requireHash(String payloadHash) {
        if (payloadHash == null
                || !PowerModelEventEnvelope.HASH_PATTERN.matcher(payloadHash).matches()) {
            throw new IllegalArgumentException(
                    "MODEL_EVENT_HASH_INVALID: payloadHash 必须匹配 sha256:<64 位小写 hex>");
        }
        return payloadHash;
    }
}
