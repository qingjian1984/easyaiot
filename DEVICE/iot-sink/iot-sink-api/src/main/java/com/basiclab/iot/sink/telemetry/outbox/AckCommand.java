package com.basiclab.iot.sink.telemetry.outbox;

import com.basiclab.iot.sink.telemetry.ack.TelemetryAckStatus;
import com.basiclab.iot.sink.telemetry.ack.TelemetryAckV1;

/**
 * TD-003 §9 ACK 命令（ACK consumer → outbox writer）。
 *
 * <p>The nine fields are the in-memory hand-off for ACK V1.  The wire has
 * exactly seven fields, including {@code persistedAtMs}; {@code route} and
 * {@code observedAtMs} are local delivery facts and never enter that payload.
 * The deprecated four-argument constructor exists solely for the already
 * compiled legacy SQLite state-machine callers and is not a wire parser.
 */
public record AckCommand(
        String schemaVersion,
        String messageId,
        String requestId,
        TelemetryRoute route,
        TelemetryAckStatus status,
        int code,
        String reasonCode,
        long persistedAtMs,
        long observedAtMs
) {
    public AckCommand {
        if (messageId == null || messageId.isBlank()) {
            throw new IllegalArgumentException("messageId required");
        }
        if (status == null) {
            throw new IllegalArgumentException("status required");
        }
        if (route != null) {
            if (!TelemetryAckV1.SCHEMA_VERSION.equals(schemaVersion)) {
                throw new IllegalArgumentException("ACK_SCHEMA_UNSUPPORTED");
            }
            if (!TelemetryAckV1.isWireMessageId(messageId)) {
                throw new IllegalArgumentException("ACK_MESSAGE_ID_INVALID");
            }
            if (!TelemetryAckV1.isCanonicalRequestId(requestId)) {
                throw new IllegalArgumentException("ACK_REQUEST_ID_INVALID");
            }
            if (status.isSuccess()) {
                if (status == TelemetryAckStatus.ACCEPTED_DURABLE
                        && (code != 0 || !"OK".equals(reasonCode))) {
                    throw new IllegalArgumentException("ACK_SUCCESS_TRIPLET_INVALID");
                }
                if (status == TelemetryAckStatus.DUPLICATE
                        && (code != 1001 || !"DUPLICATE".equals(reasonCode))) {
                    throw new IllegalArgumentException("ACK_DUPLICATE_TRIPLET_INVALID");
                }
            }
        }
    }

    public AckCommand(TelemetryAckV1 ack, TelemetryRoute route, long observedAtMs) {
        this(ack.schemaVersion(), ack.messageId(), ack.requestId(), requireRoute(route), ack.status(),
                ack.code(), ack.reasonCode(), ack.persistedAtMs(), observedAtMs);
    }

    /**
     * Compatibility adapter for the pre-LC03 in-memory state machine.
     *
     * <p>It intentionally does not make the old four-field JSON acceptable;
     * the LC03 codec rejects that wire shape.  The route is null only for
     * these legacy callers and must be supplied by the V1 subscriber.
     */
    @Deprecated
    public AckCommand(String messageId, AckResultCode resultCode,
                      String errorCode, long observedAt) {
        this("1.0", messageId, messageId, null,
                toStatus(resultCode), legacyCode(resultCode),
                errorCode == null ? legacyReason(resultCode) : errorCode,
                observedAt, observedAt);
    }

    /** Legacy state-machine view retained until LC03-02 switches its writer. */
    @Deprecated
    public AckResultCode resultCode() {
        return AckResultCode.valueOf(status.name());
    }

    /** Legacy state-machine view; V1 callers use {@link #reasonCode()}. */
    @Deprecated
    public String errorCode() {
        return reasonCode;
    }

    /** Legacy state-machine view; V1 callers use {@link #observedAtMs()}. */
    @Deprecated
    public long observedAt() {
        return observedAtMs;
    }

    private static TelemetryAckStatus toStatus(AckResultCode code) {
        if (code == null) {
            throw new IllegalArgumentException("resultCode required");
        }
        return TelemetryAckStatus.valueOf(code.name());
    }

    private static TelemetryRoute requireRoute(TelemetryRoute route) {
        if (route == null) {
            throw new IllegalArgumentException("ACK_ROUTE_MISSING");
        }
        return route;
    }

    private static int legacyCode(AckResultCode code) {
        return switch (code) {
            case ACCEPTED_DURABLE -> 0;
            case DUPLICATE -> 1001;
            case REJECTED_RETRYABLE, REJECTED_FINAL -> 0;
        };
    }

    private static String legacyReason(AckResultCode code) {
        return switch (code) {
            case ACCEPTED_DURABLE -> "OK";
            case DUPLICATE -> "DUPLICATE";
            case REJECTED_RETRYABLE -> "REJECTED_RETRYABLE";
            case REJECTED_FINAL -> "REJECTED_FINAL";
        };
    }
}
