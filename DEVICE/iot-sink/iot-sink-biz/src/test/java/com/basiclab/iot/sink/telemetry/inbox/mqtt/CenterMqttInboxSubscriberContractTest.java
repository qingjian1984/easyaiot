package com.basiclab.iot.sink.telemetry.inbox.mqtt;

import com.basiclab.iot.sink.telemetry.inbox.InboxEnvelope;
import com.basiclab.iot.sink.telemetry.inbox.InboxReceiveResult;
import com.basiclab.iot.sink.telemetry.inbox.TelemetryInboxPort;
import com.basiclab.iot.sink.telemetry.inbox.route.CenterTelemetryIngressHandler;
import com.basiclab.iot.sink.telemetry.inbox.route.CenterTelemetryIngressResult;
import com.basiclab.iot.sink.telemetry.inbox.route.TelemetryDeviceAuthorityPort;
import com.basiclab.iot.sink.telemetry.inbox.route.TelemetryUpstreamTopicParser;
import com.basiclab.iot.sink.telemetry.outbox.TelemetryRoute;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CenterMqttInboxSubscriberContractTest {

    @Test
    void transportOnlyCopiesAndDelegatesToThePureIngressHandler() {
        TelemetryRoute route = new TelemetryRoute("product-1", "device-1");
        RecordingInbox inbox = new RecordingInbox();
        CenterTelemetryIngressHandler handler = new CenterTelemetryIngressHandler(
                new TelemetryUpstreamTopicParser(),
                ignored -> new TelemetryDeviceAuthorityPort.Resolution.Resolved("920006001"),
                inbox);
        CenterMqttInboxSubscriber subscriber = new CenterMqttInboxSubscriber(
                handler, "127.0.0.1", 1883, "contract-test",
                TelemetryUpstreamTopicParser.sharedSubscriptionFilter(), "", "");

        try {
            byte[] payload = payload();
            CenterTelemetryIngressResult result = subscriber.dispatch(route.upstreamTopic(), payload);
            assertInstanceOf(CenterTelemetryIngressResult.Accepted.class, result);
            assertEquals(1, inbox.calls);
            assertEquals("m-1", inbox.received.get(0).messageId());
            assertEquals("device-1", inbox.received.get(0).deviceIdentification());
            assertTrue(java.util.Arrays.equals(payload, inbox.received.get(0).canonicalBytes()));
        } finally {
            subscriber.close();
        }
    }

    @Test
    void malformedTransportMessageIsReturnedByHandlerAndNeverReachesInbox() {
        RecordingInbox inbox = new RecordingInbox();
        CenterTelemetryIngressHandler handler = new CenterTelemetryIngressHandler(
                new TelemetryUpstreamTopicParser(),
                ignored -> new TelemetryDeviceAuthorityPort.Resolution.Resolved("920006001"),
                inbox);
        CenterMqttInboxSubscriber subscriber = new CenterMqttInboxSubscriber(
                handler, "127.0.0.1", 1883, "contract-test-invalid",
                TelemetryUpstreamTopicParser.sharedSubscriptionFilter(), null, null);

        try {
            CenterTelemetryIngressResult.Rejected rejected =
                    assertInstanceOf(CenterTelemetryIngressResult.Rejected.class,
                            subscriber.dispatch("/telemetry/#", "{}".getBytes(StandardCharsets.UTF_8)));
            assertEquals("TELEMETRY_TOPIC_MALFORMED",
                    rejected.rejection().code().name());
            assertEquals(0, inbox.calls);
        } finally {
            subscriber.close();
        }
    }

    private static byte[] payload() {
        return ("{\"messageId\":\"m-1\",\"requestId\":\"r-1\","
                + "\"tenantId\":\"920006001\",\"siteCode\":\"site-1\","
                + "\"deviceIdentification\":\"device-1\","
                + "\"propertyCode\":\"voltage-a\",\"value\":\"220\"}")
                .getBytes(StandardCharsets.UTF_8);
    }

    private static final class RecordingInbox implements TelemetryInboxPort {
        private int calls;
        private List<InboxEnvelope> received = List.of();

        @Override
        public InboxReceiveResult receiveEnvelopes(List<InboxEnvelope> envelopes) {
            calls++;
            received = List.copyOf(envelopes);
            InboxEnvelope envelope = envelopes.get(0);
            return new InboxReceiveResult.Batch(List.of(new InboxReceiveResult.Item(
                    0, envelope.messageId(), envelope.requestId(),
                    InboxReceiveResult.Status.ACCEPTED_DURABLE, 1L)));
        }
    }
}
