package com.basiclab.iot.sink.telemetry.query;

import java.math.BigDecimal;

/**
 * 单序列最新值行。receivedAtMs 用于前端展示“更新时间”。
 */
public record TelemetryLatestSample(
        String deviceIdentification,
        String propertyCode,
        BigDecimal value,
        long collectedAtMs,
        long receivedAtMs,
        String quality
) {
}
