package com.basiclab.iot.sink.telemetry.outbox.backfill;

import java.util.Comparator;
import java.util.List;
import java.util.regex.Pattern;

/** Complete, all-or-nothing center resolution for one inventory page. */
public record RouteBackfillManifest(
        String schemaVersion,
        String canonicalizationVersion,
        String sourceInventorySha256,
        String workloadId,
        List<RouteBackfillManifestEntry> entries,
        RouteBackfillKey inventoryNextCursor
) {

    public static final String SCHEMA_VERSION = "1.0";
    public static final String CANONICALIZATION_VERSION = "jcs-rfc8785-v1";
    private static final Pattern SHA256_HEX = Pattern.compile("[0-9a-f]{64}");
    private static final Comparator<RouteBackfillKey> KEY_ORDER = Comparator
            .comparing(RouteBackfillKey::tenantId)
            .thenComparing(RouteBackfillKey::siteCode)
            .thenComparingLong(RouteBackfillKey::configVersion)
            .thenComparing(RouteBackfillKey::deviceIdentification);

    public RouteBackfillManifest {
        if (!SCHEMA_VERSION.equals(schemaVersion)) {
            throw new IllegalArgumentException("schemaVersion must be " + SCHEMA_VERSION);
        }
        if (!CANONICALIZATION_VERSION.equals(canonicalizationVersion)) {
            throw new IllegalArgumentException("canonicalizationVersion must be "
                    + CANONICALIZATION_VERSION);
        }
        if (sourceInventorySha256 == null || !SHA256_HEX.matcher(sourceInventorySha256).matches()) {
            throw new IllegalArgumentException("sourceInventorySha256 must be lowercase SHA-256 hex");
        }
        requireNonBlank("workloadId", workloadId);
        if (entries == null) {
            throw new IllegalArgumentException("entries required");
        }
        RouteBackfillKey previous = null;
        for (RouteBackfillManifestEntry entry : entries) {
            if (entry == null) {
                throw new IllegalArgumentException("entries must not contain null");
            }
            if (!workloadId.equals(entry.workloadId())) {
                throw new IllegalArgumentException("manifest entry workloadId mismatch");
            }
            RouteBackfillKey current = entry.key();
            if (previous != null && KEY_ORDER.compare(previous, current) >= 0) {
                throw new IllegalArgumentException("entries must be strictly ordered and unique");
            }
            previous = current;
        }
        entries = List.copyOf(entries);
        if (entries.isEmpty() && inventoryNextCursor != null) {
            throw new IllegalArgumentException("empty manifest must not have inventoryNextCursor");
        }
        if (inventoryNextCursor != null && !inventoryNextCursor.equals(entries.get(entries.size() - 1).key())) {
            throw new IllegalArgumentException("inventoryNextCursor must equal the last entry key");
        }
    }

    private static void requireNonBlank(String name, String value) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException(name + " required (non-blank)");
        }
    }
}
