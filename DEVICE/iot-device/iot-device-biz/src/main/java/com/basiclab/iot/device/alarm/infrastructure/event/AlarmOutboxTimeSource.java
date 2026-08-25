package com.basiclab.iot.device.alarm.infrastructure.event;

import java.time.Instant;

/** Relay 使用的可替换时间来源；禁止在领域编排中直接调用系统时钟。 */
@FunctionalInterface
public interface AlarmOutboxTimeSource {

    Instant now();
}
