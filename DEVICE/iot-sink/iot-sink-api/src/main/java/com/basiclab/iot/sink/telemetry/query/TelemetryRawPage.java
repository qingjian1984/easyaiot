package com.basiclab.iot.sink.telemetry.query;

import java.util.List;

/**
 * 原始样本分页结果。totalRows 为满足条件的精确计数（同一查询累计上限 100,000）。
 */
public record TelemetryRawPage(
        long totalRows,
        int pageNo,
        int pageSize,
        List<TelemetrySampleView> rows
) {

    public TelemetryRawPage {
        rows = rows == null ? List.of() : List.copyOf(rows);
    }
}
