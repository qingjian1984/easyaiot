package com.basiclab.iot.sink.telemetry.inbox.route;

import com.basiclab.iot.sink.telemetry.outbox.TelemetryRoute;

/** Center-side port for the authoritative iot-device registration fact. */
public interface TelemetryDeviceAuthorityPort {

    Resolution resolve(TelemetryRoute route);

    sealed interface Resolution
            permits Resolution.Resolved, Resolution.NotFound,
            Resolution.Ambiguous, Resolution.Unavailable {

        record Resolved(String tenantId) implements Resolution {
        }

        record NotFound() implements Resolution {
        }

        record Ambiguous() implements Resolution {
        }

        record Unavailable() implements Resolution {
        }
    }
}
