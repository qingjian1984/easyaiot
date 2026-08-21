package com.basiclab.iot.sink.telemetry.inbox.route;

import com.basiclab.iot.sink.telemetry.inbox.InboxEnvelope;
import com.basiclab.iot.sink.telemetry.inbox.TelemetryInboxPort;
import com.basiclab.iot.sink.telemetry.outbox.TelemetryRoute;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.List;

/**
 * Deterministic pre-Inbox guard for center MQTT telemetry.  It has no MQTT or
 * network dependency and therefore is directly contract-testable.
 */
public final class CenterTelemetryIngressHandler {

    private final TelemetryUpstreamTopicParser topicParser;
    private final TelemetryDeviceAuthorityPort authority;
    private final TelemetryInboxPort inbox;
    private final TelemetryEnvelopeDecoder envelopeDecoder;

    public CenterTelemetryIngressHandler(TelemetryUpstreamTopicParser topicParser,
                                         TelemetryDeviceAuthorityPort authority,
                                         TelemetryInboxPort inbox) {
        this.topicParser = topicParser;
        this.authority = authority;
        this.inbox = inbox;
        this.envelopeDecoder = new TelemetryEnvelopeDecoder(new ObjectMapper());
    }

    public CenterTelemetryIngressResult handle(String topic, byte[] canonicalPayload) {
        TelemetryUpstreamTopicParser.Result parsedTopic = topicParser.parse(topic);
        if (parsedTopic instanceof TelemetryUpstreamTopicParser.Rejected rejected) {
            return new CenterTelemetryIngressResult.Rejected(rejected.rejection());
        }
        TelemetryRoute route = ((TelemetryUpstreamTopicParser.Parsed) parsedTopic).route();

        TelemetryEnvelopeDecoder.DecodeResult decoded = envelopeDecoder.decode(canonicalPayload);
        if (decoded instanceof TelemetryEnvelopeDecoder.Invalid) {
            return reject(TelemetryIngressRejectionCode.TELEMETRY_ENVELOPE_INVALID);
        }
        InboxEnvelope envelope = ((TelemetryEnvelopeDecoder.Decoded) decoded).envelope();
        if (!route.deviceIdentification().equals(envelope.deviceIdentification())) {
            return reject(TelemetryIngressRejectionCode.TELEMETRY_TOPIC_DEVICE_ENVELOPE_MISMATCH);
        }
        if (!isCanonicalPositiveTenant(envelope.tenantId())) {
            return reject(TelemetryIngressRejectionCode.TELEMETRY_ENVELOPE_TENANT_INVALID);
        }

        TelemetryDeviceAuthorityPort.Resolution resolution;
        try {
            resolution = authority.resolve(route);
        } catch (RuntimeException exception) {
            resolution = new TelemetryDeviceAuthorityPort.Resolution.Unavailable();
        }
        if (resolution instanceof TelemetryDeviceAuthorityPort.Resolution.NotFound) {
            return reject(TelemetryIngressRejectionCode.TELEMETRY_DEVICE_REGISTRATION_NOT_FOUND);
        }
        if (resolution instanceof TelemetryDeviceAuthorityPort.Resolution.Ambiguous) {
            return reject(TelemetryIngressRejectionCode.TELEMETRY_DEVICE_REGISTRATION_AMBIGUOUS);
        }
        if (!(resolution instanceof TelemetryDeviceAuthorityPort.Resolution.Resolved resolved)
                || !isCanonicalPositiveTenant(resolved.tenantId())) {
            return reject(TelemetryIngressRejectionCode.TELEMETRY_DEVICE_AUTHORITY_UNAVAILABLE);
        }
        if (!resolved.tenantId().equals(envelope.tenantId())) {
            return reject(TelemetryIngressRejectionCode.TELEMETRY_DEVICE_REGISTRATION_TENANT_MISMATCH);
        }

        // Inbox exceptions are intentionally allowed to propagate as receive
        // failures; they are not security rejections and cannot be ACKed here.
        return new CenterTelemetryIngressResult.Accepted(
                inbox.receiveEnvelopes(List.of(envelope)));
    }

    private static CenterTelemetryIngressResult.Rejected reject(
            TelemetryIngressRejectionCode code) {
        return new CenterTelemetryIngressResult.Rejected(TelemetryIngressRejection.of(code));
    }

    private static boolean isCanonicalPositiveTenant(String value) {
        if (value == null || !value.matches("[1-9][0-9]*")) {
            return false;
        }
        try {
            long parsed = Long.parseLong(value);
            return parsed > 0 && Long.toString(parsed).equals(value);
        } catch (NumberFormatException exception) {
            return false;
        }
    }
}
