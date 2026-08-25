package com.basiclab.iot.device.alarm.infrastructure.event;

import java.time.Duration;

/**
 * 可替换的退避抖动来源。
 *
 * <p>返回值表示在指数退避上的非负附加时长；Relay 会再次把它限制在
 * retry cap 内。实现只接收 eventId 和次数，不接收 payload。</p>
 */
@FunctionalInterface
public interface AlarmOutboxJitterSource {

    Duration jitter(String eventId, int attempt, Duration exponentialDelay);

    static AlarmOutboxJitterSource none() {
        return (eventId, attempt, exponentialDelay) -> Duration.ZERO;
    }
}
