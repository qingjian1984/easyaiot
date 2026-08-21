package com.basiclab.iot.sink.telemetry.inbox.route;

import java.util.Objects;

/** Low-sensitivity rejection result; no payload or candidate data is carried. */
public record TelemetryIngressRejection(
        TelemetryIngressRejectionCode code,
        TelemetryIngressDisposition disposition
) {
    public TelemetryIngressRejection {
        Objects.requireNonNull(code, "code");
        Objects.requireNonNull(disposition, "disposition");
        if (code.disposition() != disposition) {
            throw new IllegalArgumentException("disposition does not match rejection code");
        }
    }

    public static TelemetryIngressRejection of(TelemetryIngressRejectionCode code) {
        return new TelemetryIngressRejection(code, code.disposition());
    }
}
