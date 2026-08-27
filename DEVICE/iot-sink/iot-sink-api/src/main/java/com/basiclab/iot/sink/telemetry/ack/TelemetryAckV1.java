package com.basiclab.iot.sink.telemetry.ack;

import java.time.Instant;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * Immutable seven-field success ACK V1 contract.
 *
 * <p>{@code persistedAtMs} is the persisted Inbox millisecond value.  The
 * wire codec is the only component that formats it as an RFC 3339 UTC value;
 * keeping the numeric value here prevents a host timezone from becoming part
 * of the contract.
 */
public record TelemetryAckV1(
        String schemaVersion,
        String messageId,
        String requestId,
        TelemetryAckStatus status,
        int code,
        String reasonCode,
        long persistedAtMs
) {

    public static final String SCHEMA_VERSION = "1.0";

    private static final Pattern WIRE_MESSAGE_ID = Pattern.compile(
            "(?:[0-9a-f]{32}|[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12})");
    private static final Pattern CANONICAL_REQUEST_ID = Pattern.compile(
            "[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}");

    public TelemetryAckV1 {
        if (!SCHEMA_VERSION.equals(schemaVersion)) {
            throw new IllegalArgumentException("ACK_SCHEMA_UNSUPPORTED");
        }
        requireText("messageId", messageId);
        if (!WIRE_MESSAGE_ID.matcher(messageId).matches()) {
            throw new IllegalArgumentException("ACK_MESSAGE_ID_INVALID");
        }
        requireText("requestId", requestId);
        if (!CANONICAL_REQUEST_ID.matcher(requestId).matches()) {
            throw new IllegalArgumentException("ACK_REQUEST_ID_INVALID");
        }
        Objects.requireNonNull(status, "status");
        if (!status.isSuccess()) {
            throw new IllegalArgumentException("ACK_STATUS_UNSUPPORTED");
        }
        requireText("reasonCode", reasonCode);
        if (status == TelemetryAckStatus.ACCEPTED_DURABLE) {
            if (code != 0 || !"OK".equals(reasonCode)) {
                throw new IllegalArgumentException("ACK_SUCCESS_TRIPLET_INVALID");
            }
        } else if (code != 1001 || !"DUPLICATE".equals(reasonCode)) {
            throw new IllegalArgumentException("ACK_DUPLICATE_TRIPLET_INVALID");
        }
        if (persistedAtMs < 0) {
            throw new IllegalArgumentException("ACK_PERSISTED_AT_INVALID");
        }
        // Validate the representable range without imposing a local timezone.
        Instant.ofEpochMilli(persistedAtMs);
    }

    public static boolean isWireMessageId(String value) {
        return value != null && WIRE_MESSAGE_ID.matcher(value).matches();
    }

    public static boolean isCanonicalRequestId(String value) {
        return value != null && CANONICAL_REQUEST_ID.matcher(value).matches();
    }

    public Instant persistedAt() {
        return Instant.ofEpochMilli(persistedAtMs);
    }

    private static void requireText(String name, String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("ACK_" + name.toUpperCase() + "_MISSING");
        }
    }
}
