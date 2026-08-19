package com.basiclab.iot.sink.telemetry.store;

/**
 * TD-003 §13 TelemetryStore 写入结果。
 */
@Deprecated
public enum WriteResult {
    STORED,
    DUPLICATE,
    FAILED
}
