package com.basiclab.iot.sink.telemetry.query;

/**
 * PRD §4.5 原始查询配额（服务端强制，禁止无界查询）。
 *
 * <p>超过限制应提高聚合粒度、缩小范围或提交导出；配额校验先于任何 SQL 执行。</p>
 */
public final class TelemetryQueryQuota {

    /** 单查询最多测点序列数（设备 × 测点组合）。 */
    public static final int MAX_SERIES = 10;

    /** 原始查询最大时间跨度（毫秒）：31 天。 */
    public static final long MAX_RAW_RANGE_MS = 31L * 24 * 3600 * 1000;

    /** 单页最大行数。 */
    public static final int MAX_PAGE_SIZE = 1000;

    /** 单查询累计返回上限（跨页总行数）。 */
    public static final long MAX_TOTAL_ROWS = 100_000L;

    private TelemetryQueryQuota() {
    }
}
