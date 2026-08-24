package com.basiclab.iot.device.alarm.domain;

/** 告警状态机接受的领域动作。 */
public enum AlarmAction {

    SOURCE_RAISED,
    SOURCE_RECOVERED,
    ACK,
    START_PROCESSING,
    CLOSE,
    IGNORE,
    IGNORE_EXPIRED,
    UNIGNORE,
    PROPOSE_FALSE_ALARM,
    APPROVE_FALSE_ALARM,
    REJECT_FALSE_ALARM,
    ESCALATE
}
