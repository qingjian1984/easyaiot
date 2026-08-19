package com.basiclab.iot.sink.health;

import java.time.Instant;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * Immutable closed health value used by exactly one of process/config/serial/center.
 * It intentionally contains no path, exception text, credential or extension map.
 */
public record CollectorHealthFacet(
        HealthStatus status,
        String reasonCode,
        Instant since
) {
    private static final Pattern REASON_CODE = Pattern.compile("[A-Z][A-Z0-9_]{0,63}");

    public CollectorHealthFacet {
        status = Objects.requireNonNull(status, "status");
        if (reasonCode == null || !REASON_CODE.matcher(reasonCode).matches()) {
            throw new IllegalArgumentException("reasonCode must be a stable code");
        }
        since = Objects.requireNonNull(since, "since");
    }

    public static CollectorHealthFacet healthy(String reasonCode, Instant since) {
        return new CollectorHealthFacet(HealthStatus.HEALTHY, reasonCode, since);
    }

    public static CollectorHealthFacet degraded(String reasonCode, Instant since) {
        return new CollectorHealthFacet(HealthStatus.DEGRADED, reasonCode, since);
    }

    public static CollectorHealthFacet failed(String reasonCode, Instant since) {
        return new CollectorHealthFacet(HealthStatus.FAILED, reasonCode, since);
    }
}
