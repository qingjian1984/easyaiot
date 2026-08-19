package com.basiclab.iot.sink.telemetry.store;

import java.util.List;

/** Ordered immutable one-result-per-input batch result. */
public record WriteBatchResult(List<WriteItemResult> items) {
    public WriteBatchResult {
        if (items == null) {
            throw new IllegalArgumentException("items are required");
        }
        items = List.copyOf(items);
    }

    public static WriteBatchResult empty() {
        return new WriteBatchResult(List.of());
    }

    public static WriteBatchResult of(WriteItemResult item) {
        return new WriteBatchResult(List.of(item));
    }
}
