package com.basiclab.iot.sink.telemetry.query;

/**
 * TD-003 §16 查询粒度。RAW 走原始分页查询；MINUTE/HOUR/DAY 走聚合窗口。
 */
public enum Granularity {

    RAW,

    /** 分钟桶，date_bin 60s / INTERVAL(1m)。 */
    MINUTE,

    /** 小时桶，date_bin 3600s / INTERVAL(1h)。 */
    HOUR,

    /** 日桶（UTC 对齐），date_bin 86400s / INTERVAL(1d)。 */
    DAY;

    /** PG date_bin 的秒宽度；RAW 不参与聚合。 */
    public long bucketSeconds() {
        return switch (this) {
            case MINUTE -> 60L;
            case HOUR -> 3600L;
            case DAY -> 86400L;
            case RAW -> throw new IllegalStateException("RAW has no bucket");
        };
    }
}
