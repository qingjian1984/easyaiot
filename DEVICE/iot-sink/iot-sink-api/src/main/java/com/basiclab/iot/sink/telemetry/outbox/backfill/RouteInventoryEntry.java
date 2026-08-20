package com.basiclab.iot.sink.telemetry.outbox.backfill;

/** One grouped historical route key and its number of outbox rows. */
public record RouteInventoryEntry(RouteBackfillKey key, long rowCount) {

    public RouteInventoryEntry {
        if (key == null) {
            throw new IllegalArgumentException("key required");
        }
        if (rowCount <= 0) {
            throw new IllegalArgumentException("rowCount must be > 0: " + rowCount);
        }
    }
}
