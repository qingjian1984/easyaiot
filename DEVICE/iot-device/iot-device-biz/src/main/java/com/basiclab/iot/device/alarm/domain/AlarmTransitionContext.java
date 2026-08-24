package com.basiclab.iot.device.alarm.domain;

import com.basiclab.iot.device.alarm.contract.AlarmSeverity;
import com.basiclab.iot.device.alarm.contract.AlarmStatus;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

/**
 * 状态转换所需的外部事实快照。
 *
 * <p>Context 是纯数据，不读取租户上下文、数据库或系统时钟。幂等键、版本
 * CAS 和 Inbox 重复裁决由应用层负责；本对象只表达状态机判断所需的事实，
 * 例如忽略原状态、误报复核是否待处理以及来源周期键。</p>
 */
public final class AlarmTransitionContext {

    private final AlarmStatus currentStatus;
    private final AlarmSeverity severity;
    private final AlarmStatus ignoredFromStatus;
    private final OffsetDateTime ignoredUntil;
    private final String reason;
    private final boolean pendingFalseAlarmReview;
    private final String falseAlarmProposerId;
    private final String falseAlarmReviewerId;
    private final Boolean reviewerIndependent;
    private final String existingCycleKey;
    private final String incomingCycleKey;
    private final int escalationLevel;
    private final String operatorId;

    /** 创建“告警不存在、普通等级、无附加事实”的空快照。 */
    public AlarmTransitionContext() {
        this(builder());
    }

    /** 便于简单状态矩阵调用的构造重载。 */
    public AlarmTransitionContext(AlarmStatus currentStatus, AlarmSeverity severity) {
        this(builder().currentStatus(currentStatus).severity(severity));
    }

    private AlarmTransitionContext(Builder builder) {
        this.currentStatus = builder.currentStatus;
        this.severity = builder.severity == null ? AlarmSeverity.NORMAL : builder.severity;
        this.ignoredFromStatus = builder.ignoredFromStatus;
        this.ignoredUntil = builder.ignoredUntil;
        this.reason = builder.reason;
        this.pendingFalseAlarmReview = builder.pendingFalseAlarmReview;
        this.falseAlarmProposerId = builder.falseAlarmProposerId;
        this.falseAlarmReviewerId = builder.falseAlarmReviewerId;
        this.reviewerIndependent = builder.reviewerIndependent;
        this.existingCycleKey = builder.existingCycleKey;
        this.incomingCycleKey = builder.incomingCycleKey;
        this.escalationLevel = builder.escalationLevel;
        this.operatorId = builder.operatorId;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static AlarmTransitionContext initial() {
        return builder().build();
    }

    public static AlarmTransitionContext forStatus(AlarmStatus status) {
        return builder().currentStatus(status).build();
    }

    public static AlarmTransitionContext of(AlarmStatus status, AlarmSeverity severity) {
        return builder().currentStatus(status).severity(severity).build();
    }

    /** 返回仅替换当前状态的快照，其他显式事实保持不变。 */
    public AlarmTransitionContext withCurrentStatus(AlarmStatus status) {
        return toBuilder().currentStatus(status).build();
    }

    public Builder toBuilder() {
        return builder()
                .currentStatus(currentStatus)
                .severity(severity)
                .ignoredFromStatus(ignoredFromStatus)
                .ignoredUntil(ignoredUntil)
                .reason(reason)
                .pendingFalseAlarmReview(pendingFalseAlarmReview)
                .falseAlarmProposerId(falseAlarmProposerId)
                .falseAlarmReviewerId(falseAlarmReviewerId)
                .existingCycleKey(existingCycleKey)
                .incomingCycleKey(incomingCycleKey)
                .escalationLevel(escalationLevel)
                .operatorId(operatorId)
                .reviewerIndependent(reviewerIndependent == null
                        ? reviewerIndependent() : reviewerIndependent);
    }

    public AlarmStatus currentStatus() {
        return currentStatus;
    }

    public AlarmSeverity severity() {
        return severity;
    }

    public AlarmStatus ignoredFromStatus() {
        return ignoredFromStatus;
    }

    public OffsetDateTime ignoredUntil() {
        return ignoredUntil;
    }

    public String reason() {
        return reason;
    }

    public boolean pendingFalseAlarmReview() {
        return pendingFalseAlarmReview;
    }

    public boolean hasPendingFalseAlarmReview() {
        return pendingFalseAlarmReview;
    }

    public String falseAlarmProposerId() {
        return falseAlarmProposerId;
    }

    public String falseAlarmReviewerId() {
        return falseAlarmReviewerId;
    }

    /**
     * 复核人是否已由入口显式确认独立；未显式提供时由两个身份标识推导。
     */
    public boolean reviewerIndependent() {
        if (isBlank(falseAlarmProposerId) || isBlank(falseAlarmReviewerId)
                || falseAlarmProposerId.equals(falseAlarmReviewerId)) {
            return false;
        }
        if (reviewerIndependent != null) {
            return reviewerIndependent;
        }
        return true;
    }

    public String existingCycleKey() {
        return existingCycleKey;
    }

    public String incomingCycleKey() {
        return incomingCycleKey;
    }

    public int escalationLevel() {
        return escalationLevel;
    }

    public String operatorId() {
        return operatorId;
    }

    // JavaBean aliases for adapters and test fixtures.
    public AlarmStatus getCurrentStatus() { return currentStatus; }
    public AlarmSeverity getSeverity() { return severity; }
    public AlarmStatus getIgnoredFromStatus() { return ignoredFromStatus; }
    public OffsetDateTime getIgnoredUntil() { return ignoredUntil; }
    public String getReason() { return reason; }
    public boolean isPendingFalseAlarmReview() { return pendingFalseAlarmReview; }
    public String getFalseAlarmProposerId() { return falseAlarmProposerId; }
    public String getFalseAlarmReviewerId() { return falseAlarmReviewerId; }
    public boolean isReviewerIndependent() { return reviewerIndependent(); }
    public String getExistingCycleKey() { return existingCycleKey; }
    public String getIncomingCycleKey() { return incomingCycleKey; }
    public int getEscalationLevel() { return escalationLevel; }
    public String getOperatorId() { return operatorId; }

    private static boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    /** Builder 只收集事实，不执行领域转换。 */
    public static final class Builder {
        private AlarmStatus currentStatus;
        private AlarmSeverity severity = AlarmSeverity.NORMAL;
        private AlarmStatus ignoredFromStatus;
        private OffsetDateTime ignoredUntil;
        private String reason;
        private boolean pendingFalseAlarmReview;
        private String falseAlarmProposerId;
        private String falseAlarmReviewerId;
        private Boolean reviewerIndependent;
        private String existingCycleKey;
        private String incomingCycleKey;
        private int escalationLevel;
        private String operatorId;

        public Builder currentStatus(AlarmStatus currentStatus) {
            this.currentStatus = currentStatus;
            return this;
        }

        public Builder status(AlarmStatus status) {
            return currentStatus(status);
        }

        public Builder severity(AlarmSeverity severity) {
            this.severity = severity;
            return this;
        }

        public Builder ignoredFromStatus(AlarmStatus ignoredFromStatus) {
            this.ignoredFromStatus = ignoredFromStatus;
            return this;
        }

        public Builder ignoredUntil(OffsetDateTime ignoredUntil) {
            this.ignoredUntil = ignoredUntil;
            return this;
        }

        public Builder ignoredUntil(Instant ignoredUntil) {
            this.ignoredUntil = ignoredUntil == null ? null : ignoredUntil.atOffset(ZoneOffset.UTC);
            return this;
        }

        public Builder ignoredUntil(String ignoredUntil) {
            this.ignoredUntil = ignoredUntil == null ? null : OffsetDateTime.parse(ignoredUntil);
            return this;
        }

        public Builder ignoreReason(String reason) {
            return reason(reason);
        }

        public Builder reason(String reason) {
            this.reason = reason;
            return this;
        }

        public Builder pendingFalseAlarmReview(boolean pending) {
            this.pendingFalseAlarmReview = pending;
            return this;
        }

        public Builder hasPendingFalseAlarmReview(boolean pending) {
            return pendingFalseAlarmReview(pending);
        }

        public Builder falseAlarmProposerId(String proposerId) {
            this.falseAlarmProposerId = proposerId;
            return this;
        }

        public Builder proposerId(String proposerId) {
            return falseAlarmProposerId(proposerId);
        }

        public Builder falseAlarmReviewerId(String reviewerId) {
            this.falseAlarmReviewerId = reviewerId;
            return this;
        }

        public Builder reviewerId(String reviewerId) {
            return falseAlarmReviewerId(reviewerId);
        }

        public Builder reviewerIndependent(boolean independent) {
            this.reviewerIndependent = independent;
            return this;
        }

        public Builder existingCycleKey(String cycleKey) {
            this.existingCycleKey = cycleKey;
            return this;
        }

        public Builder currentCycleKey(String cycleKey) {
            return existingCycleKey(cycleKey);
        }

        public Builder incomingCycleKey(String cycleKey) {
            this.incomingCycleKey = cycleKey;
            return this;
        }

        public Builder sourceCycleKey(String cycleKey) {
            return incomingCycleKey(cycleKey);
        }

        /** 同时设置现有与输入周期，适合验证同周期冲突。 */
        public Builder cycleKey(String cycleKey) {
            this.existingCycleKey = cycleKey;
            this.incomingCycleKey = cycleKey;
            return this;
        }

        public Builder escalationLevel(int escalationLevel) {
            this.escalationLevel = escalationLevel;
            return this;
        }

        public Builder operatorId(String operatorId) {
            this.operatorId = operatorId;
            return this;
        }

        public AlarmTransitionContext build() {
            return new AlarmTransitionContext(this);
        }
    }
}
