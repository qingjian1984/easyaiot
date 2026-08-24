package com.basiclab.iot.device.alarm.domain;

import com.basiclab.iot.device.alarm.contract.AlarmStatus;

import java.util.Objects;

/**
 * 状态机的确定性结果。
 *
 * <p>拒绝也通过结果表达，业务层不需要依赖异常文本。成功结果的
 * {@code errorCode} 为 {@code null}；允许但不改变主状态的 occurrence、
 * no-op recover、误报提议/拒绝和升级均以 {@code stateChanged=false}
 * 表达。</p>
 */
public final class AlarmTransitionResult {

    private final AlarmStatus previousStatus;
    private final AlarmStatus targetStatus;
    private final boolean stateChanged;
    private final AlarmStatus ignoredFromStatus;
    private final String errorCode;
    private final boolean occurrenceRecorded;
    private final boolean falseAlarmReviewPending;
    private final int previousEscalationLevel;
    private final int targetEscalationLevel;

    private AlarmTransitionResult(AlarmStatus previousStatus, AlarmStatus targetStatus,
                                  boolean stateChanged, AlarmStatus ignoredFromStatus,
                                  String errorCode, boolean occurrenceRecorded,
                                  boolean falseAlarmReviewPending,
                                  int previousEscalationLevel,
                                  int targetEscalationLevel) {
        this.previousStatus = previousStatus;
        this.targetStatus = targetStatus;
        this.stateChanged = stateChanged;
        this.ignoredFromStatus = ignoredFromStatus;
        this.errorCode = errorCode;
        this.occurrenceRecorded = occurrenceRecorded;
        this.falseAlarmReviewPending = falseAlarmReviewPending;
        this.previousEscalationLevel = previousEscalationLevel;
        this.targetEscalationLevel = targetEscalationLevel;
    }

    public static AlarmTransitionResult allowed(AlarmStatus previousStatus,
                                                 AlarmStatus targetStatus,
                                                 boolean stateChanged,
                                                 AlarmStatus ignoredFromStatus) {
        return new AlarmTransitionResult(previousStatus, targetStatus, stateChanged,
                ignoredFromStatus, null, false, false, 0, 0);
    }

    public static AlarmTransitionResult allowed(AlarmStatus previousStatus,
                                                 AlarmStatus targetStatus,
                                                 boolean stateChanged,
                                                 AlarmStatus ignoredFromStatus,
                                                 boolean occurrenceRecorded,
                                                 boolean falseAlarmReviewPending,
                                                 int previousEscalationLevel,
                                                 int targetEscalationLevel) {
        return new AlarmTransitionResult(previousStatus, targetStatus, stateChanged,
                ignoredFromStatus, null, occurrenceRecorded, falseAlarmReviewPending,
                previousEscalationLevel, targetEscalationLevel);
    }

    public static AlarmTransitionResult rejected(AlarmStatus currentStatus,
                                                  String errorCode) {
        return rejected(currentStatus, currentStatus, errorCode);
    }

    public static AlarmTransitionResult rejected(AlarmStatus currentStatus,
                                                  AlarmStatus targetStatus,
                                                  String errorCode) {
        String stableCode = errorCode == null || errorCode.trim().isEmpty()
                ? AlarmErrorCode.ALARM_INVALID_TRANSITION.code() : errorCode;
        return new AlarmTransitionResult(currentStatus, targetStatus, false, null,
                stableCode, false, false, 0, 0);
    }

    public AlarmStatus previousStatus() {
        return previousStatus;
    }

    public AlarmStatus targetStatus() {
        return targetStatus;
    }

    /** 当前状态别名，方便领域层将结果写回。 */
    public AlarmStatus status() {
        return targetStatus;
    }

    public boolean stateChanged() {
        return stateChanged;
    }

    public boolean statusChanged() {
        return stateChanged;
    }

    public boolean isStateChanged() {
        return stateChanged;
    }

    public AlarmStatus ignoredFromStatus() {
        return ignoredFromStatus;
    }

    /** 稳定错误码；允许结果为 {@code null}。 */
    public String errorCode() {
        return errorCode;
    }

    public String stableErrorCode() {
        return errorCode;
    }

    public boolean allowed() {
        return errorCode == null;
    }

    public boolean isAllowed() {
        return allowed();
    }

    public boolean success() {
        return allowed();
    }

    public boolean rejected() {
        return !allowed();
    }

    public boolean isRejected() {
        return rejected();
    }

    public boolean noOp() {
        return allowed() && !stateChanged;
    }

    public boolean isNoOp() {
        return noOp();
    }

    public boolean occurrenceRecorded() {
        return occurrenceRecorded;
    }

    public boolean falseAlarmReviewPending() {
        return falseAlarmReviewPending;
    }

    public int previousEscalationLevel() {
        return previousEscalationLevel;
    }

    public int targetEscalationLevel() {
        return targetEscalationLevel;
    }

    public boolean escalationChanged() {
        return targetEscalationLevel != previousEscalationLevel;
    }

    // JavaBean aliases.
    public AlarmStatus getPreviousStatus() { return previousStatus; }
    public AlarmStatus getTargetStatus() { return targetStatus; }
    public boolean isStatusChanged() { return stateChanged; }
    public AlarmStatus getIgnoredFromStatus() { return ignoredFromStatus; }
    public String getErrorCode() { return errorCode; }
    public boolean isOccurrenceRecorded() { return occurrenceRecorded; }
    public boolean isFalseAlarmReviewPending() { return falseAlarmReviewPending; }
    public int getPreviousEscalationLevel() { return previousEscalationLevel; }
    public int getTargetEscalationLevel() { return targetEscalationLevel; }

    @Override
    public String toString() {
        return "AlarmTransitionResult{" +
                "previousStatus=" + previousStatus +
                ", targetStatus=" + targetStatus +
                ", stateChanged=" + stateChanged +
                ", ignoredFromStatus=" + ignoredFromStatus +
                ", errorCode='" + errorCode + '\'' +
                ", occurrenceRecorded=" + occurrenceRecorded +
                ", falseAlarmReviewPending=" + falseAlarmReviewPending +
                ", previousEscalationLevel=" + previousEscalationLevel +
                ", targetEscalationLevel=" + targetEscalationLevel +
                '}';
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof AlarmTransitionResult that)) {
            return false;
        }
        return stateChanged == that.stateChanged
                && occurrenceRecorded == that.occurrenceRecorded
                && falseAlarmReviewPending == that.falseAlarmReviewPending
                && previousEscalationLevel == that.previousEscalationLevel
                && targetEscalationLevel == that.targetEscalationLevel
                && previousStatus == that.previousStatus
                && targetStatus == that.targetStatus
                && ignoredFromStatus == that.ignoredFromStatus
                && Objects.equals(errorCode, that.errorCode);
    }

    @Override
    public int hashCode() {
        return Objects.hash(previousStatus, targetStatus, stateChanged, ignoredFromStatus,
                errorCode, occurrenceRecorded, falseAlarmReviewPending,
                previousEscalationLevel, targetEscalationLevel);
    }
}
