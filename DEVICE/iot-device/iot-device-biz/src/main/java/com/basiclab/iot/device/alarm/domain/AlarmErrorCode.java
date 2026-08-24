package com.basiclab.iot.device.alarm.domain;

/**
 * 状态机返回的稳定错误码。
 *
 * <p>状态机不抛出业务转换异常；调用方可根据这些代码决定幂等重放、冲突
 * 重读或向用户展示的错误语义。</p>
 */
public enum AlarmErrorCode {

    ALARM_INVALID_TRANSITION,
    ALARM_EMERGENCY_IGNORE_FORBIDDEN,
    ALARM_SOURCE_CYCLE_CONFLICT,
    ALARM_REVIEWER_CONFLICT,
    ALARM_FALSE_ALARM_REVIEW_PENDING,
    ALARM_FALSE_ALARM_REVIEW_REQUIRED,
    ALARM_IGNORED_FROM_STATUS_REQUIRED,
    ALARM_IGNORE_ARGUMENT_REQUIRED,
    ALARM_ESCALATION_LEVEL_INVALID;

    public String code() {
        return name();
    }

    public String getCode() {
        return name();
    }
}
