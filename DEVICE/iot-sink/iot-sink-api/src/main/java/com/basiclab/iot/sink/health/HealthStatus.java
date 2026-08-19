package com.basiclab.iot.sink.health;

/** Closed health vocabulary shared by the collector facets. */
public enum HealthStatus {
    HEALTHY(0),
    DEGRADED(1),
    FAILED(2);

    private final int severity;

    HealthStatus(int severity) {
        this.severity = severity;
    }

    public int severity() {
        return severity;
    }

    public static HealthStatus worst(HealthStatus left, HealthStatus right) {
        if (left == null || right == null) {
            throw new IllegalArgumentException("health status is required");
        }
        return left.severity >= right.severity ? left : right;
    }
}
