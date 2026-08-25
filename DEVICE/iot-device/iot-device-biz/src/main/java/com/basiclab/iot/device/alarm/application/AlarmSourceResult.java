package com.basiclab.iot.device.alarm.application;

/**
 * 来源处理持久化裁决。只有通过 Spring 事务代理返回到调用方后，调用方才可据此 ACK。
 */
public final class AlarmSourceResult {
    public enum Outcome { PROCESSED, DUPLICATE, QUARANTINED }

    private final Outcome outcome;
    private final Long alarmId;
    private final String errorCode;

    private AlarmSourceResult(Outcome outcome, Long alarmId, String errorCode) {
        this.outcome = outcome;
        this.alarmId = alarmId;
        this.errorCode = errorCode;
    }

    public static AlarmSourceResult processed(long alarmId) {
        return new AlarmSourceResult(Outcome.PROCESSED, alarmId, null);
    }

    public static AlarmSourceResult duplicate(Long alarmId) {
        return new AlarmSourceResult(Outcome.DUPLICATE, alarmId, null);
    }

    public static AlarmSourceResult quarantined(String errorCode) {
        return new AlarmSourceResult(Outcome.QUARANTINED, null, errorCode);
    }

    public Outcome outcome() { return outcome; }
    public Long alarmId() { return alarmId; }
    public String errorCode() { return errorCode; }
}
