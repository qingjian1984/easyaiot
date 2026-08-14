package com.basiclab.iot.sink.telemetry.inbox;

import java.util.List;

/**
 * TD-003 §10 中心 Inbox 接收结果（sealed）。
 */
public sealed interface InboxReceiveResult permits InboxReceiveResult.Batch, InboxReceiveResult.Received,
        InboxReceiveResult.Duplicate, InboxReceiveResult.Collision {

    /**
     * Per-message outcomes in input order. New callers must consume this result.
     */
    record Batch(List<Item> items) implements InboxReceiveResult {
        public Batch {
            if (items == null) {
                throw new NullPointerException("items");
            }
            items = List.copyOf(items);
            for (int i = 0; i < items.size(); i++) {
                Item item = items.get(i);
                if (item == null || item.inputIndex() != i) {
                    throw new IllegalArgumentException("items must have contiguous inputIndex values");
                }
            }
        }
    }

    /** One durable Inbox decision for one input envelope. */
    record Item(
            int inputIndex,
            String messageId,
            String requestId,
            Status status,
            Long persistedAtMs
    ) {
        public Item {
            if (inputIndex < 0) {
                throw new IllegalArgumentException("inputIndex must be >= 0");
            }
            requireNonBlank("messageId", messageId);
            requireNonBlank("requestId", requestId);
            if (status == null) {
                throw new NullPointerException("status");
            }
            if (status == Status.MESSAGE_ID_COLLISION) {
                if (persistedAtMs != null) {
                    throw new IllegalArgumentException("collision persistedAtMs must be null");
                }
            } else if (persistedAtMs == null || persistedAtMs < 0) {
                throw new IllegalArgumentException("durable result persistedAtMs must be non-negative");
            }
        }

        private static void requireNonBlank(String name, String value) {
            if (value == null || value.isBlank()) {
                throw new IllegalArgumentException(name + " must not be blank");
            }
        }
    }

    enum Status {
        ACCEPTED_DURABLE,
        DUPLICATE,
        MESSAGE_ID_COLLISION
    }

    @Deprecated
    record Received(List<String> messageIds) implements InboxReceiveResult {
    }

    @Deprecated
    record Duplicate(List<String> messageIds) implements InboxReceiveResult {
    }

    @Deprecated
    record Collision(List<String> messageIds) implements InboxReceiveResult {
    }
}
