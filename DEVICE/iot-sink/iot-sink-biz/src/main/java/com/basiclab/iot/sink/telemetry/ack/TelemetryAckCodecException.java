package com.basiclab.iot.sink.telemetry.ack;

/**
 * Stable, low-detail failure raised while decoding or encoding an ACK V1.
 *
 * <p>The code is safe to expose in diagnostics.  It deliberately contains no
 * payload, identifier, exception text, or other wire data.
 */
public final class TelemetryAckCodecException extends IllegalArgumentException {

    private final String errorCode;

    public TelemetryAckCodecException(String errorCode) {
        super(errorCode);
        this.errorCode = errorCode;
    }

    public TelemetryAckCodecException(String errorCode, Throwable cause) {
        // Do not retain parser causes: their messages can include fragments of
        // the rejected payload, which must never escape through diagnostics.
        super(errorCode);
        this.errorCode = errorCode;
    }

    public String errorCode() {
        return errorCode;
    }
}
