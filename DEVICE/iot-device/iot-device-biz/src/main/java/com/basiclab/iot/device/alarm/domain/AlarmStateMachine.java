package com.basiclab.iot.device.alarm.domain;

import com.basiclab.iot.device.alarm.contract.AlarmSeverity;
import com.basiclab.iot.device.alarm.contract.AlarmStatus;

/**
 * 无副作用的告警状态转换器。
 *
 * <p>状态机只读取显式传入的快照，不依赖 Spring、数据库、租户上下文、
 * 当前时间或随机数。幂等键、乐观锁和 Inbox 重复裁决由调用方在进入状态机
 * 前完成。</p>
 */
public final class AlarmStateMachine {

    /** 便于不需要依赖注入的调用方复用的无状态实例。 */
    public static final AlarmStateMachine INSTANCE = new AlarmStateMachine();

    public AlarmStateMachine() {
    }

    /** 计算一次状态转换。 */
    public static AlarmTransitionResult transition(AlarmTransitionContext context,
                                                   AlarmAction action) {
        AlarmTransitionContext safeContext = context == null
                ? AlarmTransitionContext.initial() : context;
        AlarmStatus current = safeContext.currentStatus();
        if (action == null) {
            return invalid(current);
        }
        if (current == AlarmStatus.CLOSED || current == AlarmStatus.FALSE_ALARM) {
            return invalid(current);
        }

        switch (action) {
            case SOURCE_RAISED:
                return sourceRaised(safeContext);
            case SOURCE_RECOVERED:
                return sourceRecovered(safeContext);
            case ACK:
                return acknowledge(safeContext);
            case START_PROCESSING:
                return startProcessing(safeContext);
            case CLOSE:
                return close(safeContext);
            case IGNORE:
                return ignore(safeContext);
            case IGNORE_EXPIRED:
            case UNIGNORE:
                return unignore(safeContext);
            case PROPOSE_FALSE_ALARM:
                return proposeFalseAlarm(safeContext);
            case APPROVE_FALSE_ALARM:
                return approveFalseAlarm(safeContext);
            case REJECT_FALSE_ALARM:
                return rejectFalseAlarm(safeContext);
            case ESCALATE:
                return escalate(safeContext);
            default:
                return invalid(current);
        }
    }

    /** 用显式当前状态覆盖 Context 中的状态，保留其他事实。 */
    public static AlarmTransitionResult transition(AlarmStatus currentStatus,
                                                   AlarmAction action,
                                                   AlarmTransitionContext context) {
        AlarmTransitionContext safeContext = context == null
                ? AlarmTransitionContext.builder().currentStatus(currentStatus).build()
                : context.withCurrentStatus(currentStatus);
        return transition(safeContext, action);
    }

    public static AlarmTransitionResult transition(AlarmStatus currentStatus,
                                                   AlarmAction action) {
        return transition(AlarmTransitionContext.forStatus(currentStatus), action);
    }

    public static AlarmTransitionResult transition(AlarmAction action,
                                                   AlarmTransitionContext context) {
        return transition(context, action);
    }

    public static AlarmTransitionResult evaluate(AlarmTransitionContext context,
                                                  AlarmAction action) {
        return transition(context, action);
    }

    /** 实例风格别名，便于由现有服务持有无状态机器。 */
    public AlarmTransitionResult apply(AlarmTransitionContext context,
                                       AlarmAction action) {
        return transition(context, action);
    }

    private static AlarmTransitionResult sourceRaised(AlarmTransitionContext context) {
        AlarmStatus current = context.currentStatus();
        if (current == null) {
            return allowed(context, null, AlarmStatus.ACTIVE, true, null,
                    true, context.escalationLevel(), context.escalationLevel());
        }
        switch (current) {
            case ACTIVE:
            case ACKNOWLEDGED:
            case PROCESSING:
            case IGNORED:
                // 同一活动周期重复发生只增加 occurrence，不撤销确认/处理/忽略。
                return allowed(context, current, current, false,
                        current == AlarmStatus.IGNORED ? context.ignoredFromStatus() : null,
                        true, context.escalationLevel(), context.escalationLevel());
            case RECOVERED:
                // 旧周期不可重开；新周期应由应用层创建新的 alarmId。
                return rejected(current, AlarmErrorCode.ALARM_SOURCE_CYCLE_CONFLICT);
            default:
                return invalid(current);
        }
    }

    private static AlarmTransitionResult sourceRecovered(AlarmTransitionContext context) {
        AlarmStatus current = context.currentStatus();
        if (current == AlarmStatus.ACTIVE || current == AlarmStatus.ACKNOWLEDGED
                || current == AlarmStatus.PROCESSING || current == AlarmStatus.IGNORED) {
            return allowed(context, current, AlarmStatus.RECOVERED, true, null,
                    false, context.escalationLevel(), context.escalationLevel());
        }
        if (current == AlarmStatus.RECOVERED) {
            // 不同来源重复恢复事件安全收敛为领域 no-op。
            return allowed(context, current, current, false, null,
                    false, context.escalationLevel(), context.escalationLevel());
        }
        return invalid(current);
    }

    private static AlarmTransitionResult acknowledge(AlarmTransitionContext context) {
        AlarmStatus current = context.currentStatus();
        if (current == AlarmStatus.ACTIVE) {
            return allowed(context, current, AlarmStatus.ACKNOWLEDGED, true, null,
                    false, context.escalationLevel(), context.escalationLevel());
        }
        if (current == AlarmStatus.IGNORED) {
            return allowed(context, current, AlarmStatus.ACKNOWLEDGED, true, null, false,
                    context.escalationLevel(), context.escalationLevel());
        }
        // ACKNOWLEDGED + ACK 由幂等层重放；纯状态机明确拒绝。
        return invalid(current);
    }

    private static AlarmTransitionResult startProcessing(AlarmTransitionContext context) {
        AlarmStatus current = context.currentStatus();
        if (current == AlarmStatus.ACKNOWLEDGED) {
            return allowed(context, current, AlarmStatus.PROCESSING, true, null,
                    false, context.escalationLevel(), context.escalationLevel());
        }
        return invalid(current);
    }

    private static AlarmTransitionResult close(AlarmTransitionContext context) {
        AlarmStatus current = context.currentStatus();
        if (current == AlarmStatus.RECOVERED) {
            return allowed(context, current, AlarmStatus.CLOSED, true, null,
                    false, context.escalationLevel(), context.escalationLevel());
        }
        return invalid(current);
    }

    private static AlarmTransitionResult ignore(AlarmTransitionContext context) {
        AlarmStatus current = context.currentStatus();
        if (context.severity() == AlarmSeverity.EMERGENCY) {
            return rejected(current, AlarmErrorCode.ALARM_EMERGENCY_IGNORE_FORBIDDEN);
        }
        if (current != AlarmStatus.ACTIVE && current != AlarmStatus.ACKNOWLEDGED) {
            return invalid(current);
        }
        if (context.ignoredFromStatus() == null) {
            return rejected(current, AlarmErrorCode.ALARM_IGNORED_FROM_STATUS_REQUIRED);
        }
        if (context.ignoredFromStatus() != current) {
            return rejected(current, AlarmErrorCode.ALARM_IGNORED_FROM_STATUS_REQUIRED);
        }
        if (isBlank(context.reason()) || context.ignoredUntil() == null) {
            return rejected(current, AlarmErrorCode.ALARM_IGNORE_ARGUMENT_REQUIRED);
        }
        return allowed(context, current, AlarmStatus.IGNORED, true, current,
                false, context.escalationLevel(), context.escalationLevel());
    }

    private static AlarmTransitionResult unignore(AlarmTransitionContext context) {
        AlarmStatus current = context.currentStatus();
        if (current != AlarmStatus.IGNORED) {
            return invalid(current);
        }
        AlarmStatus restored = context.ignoredFromStatus();
        if (restored != AlarmStatus.ACTIVE && restored != AlarmStatus.ACKNOWLEDGED) {
            return rejected(current, AlarmErrorCode.ALARM_IGNORED_FROM_STATUS_REQUIRED);
        }
        return allowed(context, current, restored, true, null,
                false, context.escalationLevel(), context.escalationLevel());
    }

    private static AlarmTransitionResult proposeFalseAlarm(AlarmTransitionContext context) {
        AlarmStatus current = context.currentStatus();
        if (!canReviewFalseAlarm(current)) {
            return invalid(current);
        }
        if (context.pendingFalseAlarmReview()) {
            return rejected(current, AlarmErrorCode.ALARM_FALSE_ALARM_REVIEW_PENDING);
        }
        return allowed(context, current, current, false,
                current == AlarmStatus.IGNORED ? context.ignoredFromStatus() : null,
                false, context.escalationLevel(), context.escalationLevel(), true);
    }

    private static AlarmTransitionResult approveFalseAlarm(AlarmTransitionContext context) {
        AlarmStatus current = context.currentStatus();
        if (!canReviewFalseAlarm(current)) {
            return invalid(current);
        }
        if (!context.pendingFalseAlarmReview()) {
            return rejected(current, AlarmErrorCode.ALARM_FALSE_ALARM_REVIEW_REQUIRED);
        }
        if (!context.reviewerIndependent()) {
            return rejected(current, AlarmErrorCode.ALARM_REVIEWER_CONFLICT);
        }
        return allowed(context, current, AlarmStatus.FALSE_ALARM, true,
                current == AlarmStatus.IGNORED ? context.ignoredFromStatus() : null,
                false, context.escalationLevel(), context.escalationLevel(), false);
    }

    private static AlarmTransitionResult rejectFalseAlarm(AlarmTransitionContext context) {
        AlarmStatus current = context.currentStatus();
        if (!canReviewFalseAlarm(current)) {
            return invalid(current);
        }
        if (!context.pendingFalseAlarmReview()) {
            return rejected(current, AlarmErrorCode.ALARM_FALSE_ALARM_REVIEW_REQUIRED);
        }
        if (!context.reviewerIndependent()) {
            return rejected(current, AlarmErrorCode.ALARM_REVIEWER_CONFLICT);
        }
        return allowed(context, current, current, false,
                current == AlarmStatus.IGNORED ? context.ignoredFromStatus() : null,
                false, context.escalationLevel(), context.escalationLevel(), false);
    }

    private static AlarmTransitionResult escalate(AlarmTransitionContext context) {
        AlarmStatus current = context.currentStatus();
        if (current != AlarmStatus.ACTIVE && current != AlarmStatus.ACKNOWLEDGED
                && current != AlarmStatus.PROCESSING) {
            return invalid(current);
        }
        if (context.escalationLevel() < 0 || context.escalationLevel() == Integer.MAX_VALUE) {
            return rejected(current, AlarmErrorCode.ALARM_ESCALATION_LEVEL_INVALID);
        }
        return allowed(context, current, current, false, null, false,
                context.escalationLevel(), context.escalationLevel() + 1);
    }

    private static boolean canReviewFalseAlarm(AlarmStatus status) {
        return status == AlarmStatus.ACTIVE || status == AlarmStatus.ACKNOWLEDGED
                || status == AlarmStatus.PROCESSING || status == AlarmStatus.IGNORED
                || status == AlarmStatus.RECOVERED;
    }

    private static AlarmTransitionResult allowed(AlarmTransitionContext context,
                                                 AlarmStatus previous,
                                                 AlarmStatus target,
                                                 boolean stateChanged,
                                                 AlarmStatus ignoredFromStatus,
                                                 boolean occurrenceRecorded,
                                                 int previousEscalationLevel,
                                                 int targetEscalationLevel) {
        return allowed(context, previous, target, stateChanged, ignoredFromStatus,
                occurrenceRecorded, previousEscalationLevel, targetEscalationLevel,
                context.pendingFalseAlarmReview());
    }

    private static AlarmTransitionResult allowed(AlarmTransitionContext context,
                                                 AlarmStatus previous,
                                                 AlarmStatus target,
                                                 boolean stateChanged,
                                                 AlarmStatus ignoredFromStatus,
                                                 boolean occurrenceRecorded,
                                                 int previousEscalationLevel,
                                                 int targetEscalationLevel,
                                                 boolean pendingFalseAlarmReview) {
        return AlarmTransitionResult.allowed(previous, target, stateChanged,
                ignoredFromStatus, occurrenceRecorded, pendingFalseAlarmReview,
                previousEscalationLevel, targetEscalationLevel);
    }

    private static AlarmTransitionResult invalid(AlarmStatus current) {
        return rejected(current, AlarmErrorCode.ALARM_INVALID_TRANSITION);
    }

    private static AlarmTransitionResult rejected(AlarmStatus current,
                                                   AlarmErrorCode errorCode) {
        return AlarmTransitionResult.rejected(current, errorCode.code());
    }

    private static boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
