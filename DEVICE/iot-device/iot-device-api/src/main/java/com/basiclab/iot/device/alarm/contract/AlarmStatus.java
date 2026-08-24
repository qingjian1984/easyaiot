package com.basiclab.iot.device.alarm.contract;

/**
 * 告警主状态。
 *
 * <p>升级、维护、通知和证据均是独立事实，不属于主状态。{@link #CLOSED}
 * 与 {@link #FALSE_ALARM} 是不可逆终态。</p>
 */
public enum AlarmStatus {

    ACTIVE,
    ACKNOWLEDGED,
    PROCESSING,
    RECOVERED,
    CLOSED,
    IGNORED,
    FALSE_ALARM
}
