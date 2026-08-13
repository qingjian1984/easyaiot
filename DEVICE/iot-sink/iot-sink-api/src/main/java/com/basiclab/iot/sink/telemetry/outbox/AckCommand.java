package com.basiclab.iot.sink.telemetry.outbox;

/**
 * TD-003 §9 ACK 命令（ACK consumer → outbox writer）。
 */
public record AckCommand(
        String messageId,
        AckResultCode resultCode,
        String errorCode,
        long observedAt
) {
    public AckCommand {
        if (messageId == null || messageId.isBlank()) {
            throw new IllegalArgumentException("messageId required");
        }
        if (resultCode == null) {
            throw new IllegalArgumentException("resultCode required");
        }
    }
}
