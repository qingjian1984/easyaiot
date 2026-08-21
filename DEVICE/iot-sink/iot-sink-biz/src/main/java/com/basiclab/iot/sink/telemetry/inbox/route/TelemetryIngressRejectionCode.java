package com.basiclab.iot.sink.telemetry.inbox.route;

/** The complete, frozen LC02-06 rejection vocabulary. */
public enum TelemetryIngressRejectionCode {
    TELEMETRY_TOPIC_MALFORMED(TelemetryIngressDisposition.FINAL),
    TELEMETRY_TOPIC_PRODUCT_INVALID(TelemetryIngressDisposition.FINAL),
    TELEMETRY_TOPIC_DEVICE_INVALID(TelemetryIngressDisposition.FINAL),
    TELEMETRY_ENVELOPE_INVALID(TelemetryIngressDisposition.FINAL),
    TELEMETRY_ENVELOPE_TENANT_INVALID(TelemetryIngressDisposition.FINAL),
    TELEMETRY_TOPIC_DEVICE_ENVELOPE_MISMATCH(TelemetryIngressDisposition.FINAL),
    TELEMETRY_DEVICE_REGISTRATION_NOT_FOUND(TelemetryIngressDisposition.FINAL),
    TELEMETRY_DEVICE_REGISTRATION_AMBIGUOUS(TelemetryIngressDisposition.RETRYABLE),
    TELEMETRY_DEVICE_AUTHORITY_UNAVAILABLE(TelemetryIngressDisposition.RETRYABLE),
    TELEMETRY_DEVICE_REGISTRATION_TENANT_MISMATCH(TelemetryIngressDisposition.FINAL);

    private final TelemetryIngressDisposition disposition;

    TelemetryIngressRejectionCode(TelemetryIngressDisposition disposition) {
        this.disposition = disposition;
    }

    public TelemetryIngressDisposition disposition() {
        return disposition;
    }
}
