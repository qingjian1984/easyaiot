package com.basiclab.iot.sink.telemetry.outbox;

/**
 * TD-003 §9 应用 ACK 结果码。
 * ACCEPTED_DURABLE/DUPLICATE → outbox ACKED；REJECTED_RETRYABLE → PENDING+退避；REJECTED_FINAL → gap+DEAD_LETTER。
 */
public enum AckResultCode {
    ACCEPTED_DURABLE,
    DUPLICATE,
    REJECTED_RETRYABLE,
    REJECTED_FINAL
}
