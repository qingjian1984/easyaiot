package com.basiclab.iot.sink.telemetry.ack;

/**
 * Application-level telemetry ACK statuses.
 *
 * <p>LC03 emits only {@link #ACCEPTED_DURABLE} and {@link #DUPLICATE}.  The
 * two rejection statuses remain named here for the later LC04 package so the
 * shared in-memory command does not need another incompatible enum.  The
 * LC03 wire codec deliberately refuses to encode or decode either rejection
 * status.
 */
public enum TelemetryAckStatus {
    ACCEPTED_DURABLE,
    DUPLICATE,
    REJECTED_RETRYABLE,
    REJECTED_FINAL;

    public boolean isSuccess() {
        return this == ACCEPTED_DURABLE || this == DUPLICATE;
    }
}
