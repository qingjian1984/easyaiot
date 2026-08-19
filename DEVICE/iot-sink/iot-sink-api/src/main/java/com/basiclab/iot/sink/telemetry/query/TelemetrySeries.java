package com.basiclab.iot.sink.telemetry.query;

import java.util.Objects;

/**
 * 查询目标序列：设备 × 测点。一个查询最多 10 个序列（PRD §4.5 原始查询配额）。
 */
public record TelemetrySeries(String deviceIdentification, String propertyCode) {

    public TelemetrySeries {
        if (deviceIdentification == null || deviceIdentification.isBlank()) {
            throw new IllegalArgumentException("deviceIdentification must not be blank");
        }
        if (propertyCode == null || propertyCode.isBlank()) {
            throw new IllegalArgumentException("propertyCode must not be blank");
        }
        if (deviceIdentification.length() > 128 || propertyCode.length() > 128) {
            throw new IllegalArgumentException("identifier exceeds 128 chars");
        }
    }

    static TelemetrySeries requireValid(TelemetrySeries series) {
        return Objects.requireNonNull(series, "series");
    }
}
