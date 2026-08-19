package com.basiclab.iot.sink.telemetry.query;

import java.util.List;
import java.util.Objects;

/**
 * 原始样本分页查询。tenantId 由服务端从登录态注入，客户端不得传入。
 *
 * <p>配额（PRD §4.5）：series 1..10、时间跨度 ≤31 天、pageSize ≤1000、
 * 累计 ≤100,000 行；由端口实现前置校验，超限抛 {@link QueryQuotaExceededException}。</p>
 */
public record TelemetryRawQuery(
        String tenantId,
        List<TelemetrySeries> series,
        long fromMs,
        long toMs,
        int pageNo,
        int pageSize
) {

    public TelemetryRawQuery {
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
        if (toMs - fromMs > TelemetryQueryQuota.MAX_RAW_RANGE_MS) {
            throw new QueryQuotaExceededException("raw range exceeds 31 days");
        }
        if (pageNo < 1 || pageSize < 1 || pageSize > TelemetryQueryQuota.MAX_PAGE_SIZE) {
            throw new IllegalArgumentException("pageNo >= 1 and pageSize in 1..1000 required");
        }
    }

    public long offset() {
        return (long) (pageNo - 1) * pageSize;
    }
}
