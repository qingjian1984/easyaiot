package com.basiclab.iot.sink.polling;

import java.util.List;

/** Immutable device definition from collector-config-snapshot-v1.1. */
public record CollectorDevice(
        String deviceId,
        String deviceIdentification,
        int unitId,
        long pollIntervalMs,
        long requestTimeoutMs,
        int maxRetries,
        List<CollectorPoint> points
) {
    public CollectorDevice {
        if (deviceId == null || deviceId.isBlank() || deviceIdentification == null
                || deviceIdentification.isBlank() || unitId < 1 || unitId > 247
                || pollIntervalMs < 1 || requestTimeoutMs < 1 || maxRetries < 0 || points == null || points.isEmpty()) {
            throw new IllegalArgumentException("invalid collector device");
        }
        points = List.copyOf(points);
    }
}
