package com.basiclab.iot.sink.telemetry.inbox.route;

/** Classification used by later reliable rejection audit/ACK stages. */
public enum TelemetryIngressDisposition {
    FINAL,
    RETRYABLE
}
