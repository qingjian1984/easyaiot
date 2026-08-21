package com.basiclab.iot.sink.telemetry.outbox.backfill;

import java.util.Set;

/** One stable, deliberately non-descriptive rejection for an inventory key. */
public record RouteBackfillIssue(RouteBackfillKey key, String code) {

    public static final Set<String> CODES = Set.of(
            "ROUTE_BACKFILL_TENANT_ID_INVALID",
            "ROUTE_BACKFILL_RELEASE_NOT_UNIQUE",
            "ROUTE_BACKFILL_PAYLOAD_INTEGRITY_FAILED",
            "ROUTE_BACKFILL_PAYLOAD_IDENTITY_MISMATCH",
            "ROUTE_BACKFILL_DEVICE_NOT_UNIQUE",
            "ROUTE_BACKFILL_PROJECTION_NOT_UNIQUE",
            "ROUTE_BACKFILL_PROJECTION_IDENTITY_MISMATCH",
            "ROUTE_BACKFILL_PRODUCT_NOT_UNIQUE",
            "ROUTE_BACKFILL_PRODUCT_IDENTIFICATION_INVALID",
            "ROUTE_BACKFILL_PRODUCT_IDENTITY_MISMATCH");

    public RouteBackfillIssue {
        if (key == null) {
            throw new IllegalArgumentException("key required");
        }
        if (!CODES.contains(code)) {
            throw new IllegalArgumentException("unsupported route backfill issue code: " + code);
        }
    }
}
