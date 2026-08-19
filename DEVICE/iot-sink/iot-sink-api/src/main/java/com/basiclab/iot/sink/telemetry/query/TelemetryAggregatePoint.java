package com.basiclab.iot.sink.telemetry.query;

import java.math.BigDecimal;

/**
 * 聚合桶结果行。bucketStartMs 为桶起点（UTC 对齐）；quality 为桶内最差质量
 * （V010 落库前恒为 GOOD 兜底）；sampleCount 为桶内参与聚合的样本数。
 */
public record TelemetryAggregatePoint(
        String deviceIdentification,
        String propertyCode,
        long bucketStartMs,
        BigDecimal value,
        long sampleCount,
        String quality
) {
}
