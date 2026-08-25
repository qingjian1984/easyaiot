package com.basiclab.iot.device.alarm.application;

/** 显式 ID 端口；生产装配在后续 transport/capability 任务中提供。 */
public interface AlarmIdGenerator {
    long nextLongId();
    String nextEventId();
}
