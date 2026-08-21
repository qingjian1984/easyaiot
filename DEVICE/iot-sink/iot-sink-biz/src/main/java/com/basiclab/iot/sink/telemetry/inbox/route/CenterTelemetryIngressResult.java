package com.basiclab.iot.sink.telemetry.inbox.route;

import com.basiclab.iot.sink.telemetry.inbox.InboxReceiveResult;

import java.util.Objects;

/** Result of the pre-Inbox center route/tenant guard. */
public sealed interface CenterTelemetryIngressResult
        permits CenterTelemetryIngressResult.Accepted, CenterTelemetryIngressResult.Rejected {

    record Accepted(InboxReceiveResult inboxResult) implements CenterTelemetryIngressResult {
        public Accepted {
            Objects.requireNonNull(inboxResult, "inboxResult");
        }
    }

    record Rejected(TelemetryIngressRejection rejection) implements CenterTelemetryIngressResult {
        public Rejected {
            Objects.requireNonNull(rejection, "rejection");
        }
    }
}
