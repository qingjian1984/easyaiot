package com.basiclab.iot.sink.telemetry.query;

import java.util.List;
import java.util.Objects;

/**
 * 最新值查询（实时页轮询）。每个 series 返回一行最新样本。
 */
public record TelemetryLatestQuery(
        String tenantId,
        List<TelemetrySeries> series
) {

    public TelemetryLatestQuery {
        if (tenantId == null || tenantId.isBlank()) {
            throw new IllegalArgumentException("tenantId must not be blank");
        }
        Objects.requireNonNull(series, "series");
        series = List.copyOf(series);
        if (series.isEmpty() || series.size() > TelemetryQueryQuota.MAX_SERIES) {
            throw new QueryQuotaExceededException(
                    "series must be 1.." + TelemetryQueryQuota.MAX_SERIES);
        }
    }
}
