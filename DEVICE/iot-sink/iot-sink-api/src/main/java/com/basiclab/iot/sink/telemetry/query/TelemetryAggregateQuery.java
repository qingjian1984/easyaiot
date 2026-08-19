package com.basiclab.iot.sink.telemetry.query;

import java.util.List;
import java.util.Objects;

/**
 * 粒度聚合查询（MINUTE/HOUR/DAY 桶 × MIN/MAX/AVG/SUM/COUNT）。
 *
 * <p>聚合查询不受 31 天原始跨度限制约束（聚合输出行数远小于原始行数），
 * 但仍受 series ≤10 与时间范围合法性约束。</p>
 */
public record TelemetryAggregateQuery(
        String tenantId,
        List<TelemetrySeries> series,
        long fromMs,
        long toMs,
        Granularity granularity,
        AggregationType aggregation
) {

    public TelemetryAggregateQuery {
        if (tenantId == null || tenantId.isBlank()) {
            throw new IllegalArgumentException("tenantId must not be blank");
        }
        Objects.requireNonNull(series, "series");
        series = List.copyOf(series);
        if (series.isEmpty() || series.size() > TelemetryQueryQuota.MAX_SERIES) {
            throw new QueryQuotaExceededException(
                    "series must be 1.." + TelemetryQueryQuota.MAX_SERIES);
        }
        if (fromMs < 0 || toMs < fromMs) {
            throw new IllegalArgumentException("invalid time range");
        }
        Objects.requireNonNull(granularity, "granularity");
        Objects.requireNonNull(aggregation, "aggregation");
        if (granularity == Granularity.RAW) {
            throw new IllegalArgumentException("RAW is not an aggregate granularity; use raw query");
        }
    }
}
