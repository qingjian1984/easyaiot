package com.basiclab.iot.sink.telemetry.outbox.backfill;

import java.util.Comparator;
import java.util.List;

/**
 * A bounded, canonicalizable page of historical route inventory facts.
 */
public record RouteInventoryPage(
        String schemaVersion,
        String canonicalizationVersion,
        String workloadId,
        List<RouteInventoryEntry> entries,
        RouteBackfillKey nextCursor
) {

    public static final String SCHEMA_VERSION = "1.0";
    public static final String CANONICALIZATION_VERSION = "jcs-rfc8785-v1";
    public static final int MAX_ENTRIES = 500;

    private static final Comparator<RouteBackfillKey> KEY_ORDER = Comparator
            .comparing(RouteBackfillKey::tenantId)
            .thenComparing(RouteBackfillKey::siteCode)
            .thenComparingLong(RouteBackfillKey::configVersion)
            .thenComparing(RouteBackfillKey::deviceIdentification);

    public RouteInventoryPage {
        if (!SCHEMA_VERSION.equals(schemaVersion)) {
            throw new IllegalArgumentException("schemaVersion must be \"" + SCHEMA_VERSION + "\": " + schemaVersion);
        }
        if (!CANONICALIZATION_VERSION.equals(canonicalizationVersion)) {
            throw new IllegalArgumentException("canonicalizationVersion must be \""
                    + CANONICALIZATION_VERSION + "\": " + canonicalizationVersion);
        }
        requireNonBlank("workloadId", workloadId);
        if (entries == null) {
            throw new IllegalArgumentException("entries required");
        }
        if (entries.size() > MAX_ENTRIES) {
            throw new IllegalArgumentException("entries must contain at most " + MAX_ENTRIES + " items");
        }
        RouteBackfillKey previous = null;
        for (RouteInventoryEntry entry : entries) {
            if (entry == null) {
                throw new IllegalArgumentException("entries must not contain null");
            }
            RouteBackfillKey current = entry.key();
            if (previous != null && KEY_ORDER.compare(previous, current) >= 0) {
                throw new IllegalArgumentException("entries must be strictly ordered and unique");
            }
            previous = current;
        }
        entries = List.copyOf(entries);
        if (nextCursor != null) {
            if (entries.isEmpty() || !nextCursor.equals(entries.get(entries.size() - 1).key())) {
                throw new IllegalArgumentException("nextCursor must equal the last entry key");
            }
        }
    }

    private static void requireNonBlank(String name, String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " required (non-blank)");
        }
    }
}
