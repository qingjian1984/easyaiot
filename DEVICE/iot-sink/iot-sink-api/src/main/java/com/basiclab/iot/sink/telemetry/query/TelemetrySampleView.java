package com.basiclab.iot.sink.telemetry.query;

import java.math.BigDecimal;

/**
 * 原始样本查询返回行。quality 在 V010 落库前由适配器以 {@code GOOD} 兜底。
 */
public record TelemetrySampleView(
        String deviceIdentification,
        String propertyCode,
        BigDecimal value,
        long collectedAtMs,
        long receivedAtMs,
        String quality,
        String messageId
) {
}
