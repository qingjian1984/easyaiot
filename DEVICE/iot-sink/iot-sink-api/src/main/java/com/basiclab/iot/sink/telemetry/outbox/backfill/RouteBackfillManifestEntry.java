package com.basiclab.iot.sink.telemetry.outbox.backfill;

import java.util.regex.Pattern;

/** One authoritative center-side resolution for an inventory key. */
public record RouteBackfillManifestEntry(
        RouteBackfillKey key,
        long rowCount,
        String productIdentification,
        String workloadId,
        long releaseId,
        String payloadSha256
) {

    private static final Pattern SHA256_HEX = Pattern.compile("[0-9a-f]{64}");

    public RouteBackfillManifestEntry {
        if (key == null) {
            throw new IllegalArgumentException("key required");
        }
        if (rowCount <= 0) {
            throw new IllegalArgumentException("rowCount must be > 0: " + rowCount);
        }
        requireProductIdentification(productIdentification);
        requireNonBlank("workloadId", workloadId);
        if (releaseId <= 0) {
            throw new IllegalArgumentException("releaseId must be > 0: " + releaseId);
        }
        if (payloadSha256 == null || !SHA256_HEX.matcher(payloadSha256).matches()) {
            throw new IllegalArgumentException("payloadSha256 must be lowercase SHA-256 hex");
        }
    }

    private static void requireProductIdentification(String value) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException("productIdentification required (non-blank)");
        }
        int codePoints = value.codePointCount(0, value.length());
        if (codePoints > 128) {
            throw new IllegalArgumentException("productIdentification must contain at most 128 code points");
        }
    }

    private static void requireNonBlank(String name, String value) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException(name + " required (non-blank)");
        }
    }
}
