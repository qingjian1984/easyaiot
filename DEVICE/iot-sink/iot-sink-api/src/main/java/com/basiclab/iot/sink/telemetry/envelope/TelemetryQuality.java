package com.basiclab.iot.sink.telemetry.envelope;

/**
 * SPEC-004 §3 遥测质量十级枚举。
 * GOOD 表示有效采集；其余表示不同程度的降级/异常。
 */
public enum TelemetryQuality {
    GOOD,
    STALE,
    TIMEOUT,
    COMM_ERROR,
    DECODE_ERROR,
    OUT_OF_RANGE,
    CLOCK_SKEW,
    BACKFILLED,
    MANUAL_CORRECTION,
    UNKNOWN
}
