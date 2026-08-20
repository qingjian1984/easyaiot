package com.basiclab.iot.sink.telemetry.outbox.backfill;

/**
 * Immutable identity used to address one historical route inventory group.
 * The component values are facts from the collector outbox and are never
 * trimmed, folded, or otherwise normalized.
 */
public record RouteBackfillKey(
        String tenantId,
        String siteCode,
        long configVersion,
        String deviceIdentification
) {

    public RouteBackfillKey {
        requireNonBlank("tenantId", tenantId);
        requireNonBlank("siteCode", siteCode);
        requireNonBlank("deviceIdentification", deviceIdentification);
        if (configVersion < 0) {
            throw new IllegalArgumentException("configVersion must be >= 0: " + configVersion);
        }
    }

    private static void requireNonBlank(String name, String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " required (non-blank)");
        }
    }
}
