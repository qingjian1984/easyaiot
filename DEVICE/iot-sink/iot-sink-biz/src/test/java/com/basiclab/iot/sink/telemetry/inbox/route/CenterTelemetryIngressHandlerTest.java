package com.basiclab.iot.sink.telemetry.inbox.route;

import com.basiclab.iot.sink.telemetry.inbox.InboxEnvelope;
import com.basiclab.iot.sink.telemetry.inbox.InboxReceiveResult;
import com.basiclab.iot.sink.telemetry.inbox.TelemetryInboxPort;
import com.basiclab.iot.sink.telemetry.outbox.TelemetryRoute;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class CenterTelemetryIngressHandlerTest {

    private static final TelemetryRoute ROUTE = new TelemetryRoute("product-1", "device-1");
    private RecordingInbox inbox;
    private CenterTelemetryIngressHandler handler;
    private RecordingAuthority authority;

    @BeforeEach
    void setUp() {
        inbox = new RecordingInbox();
        authority = new RecordingAuthority(
                new TelemetryDeviceAuthorityPort.Resolution.Resolved("920006001"));
        handler = newHandler();
    }

    @Test
    void acceptsCanonicalThreeWayMatchAndCallsInboxExactlyOnce() {
        CenterTelemetryIngressResult.Accepted accepted =
                assertInstanceOf(CenterTelemetryIngressResult.Accepted.class,
                        handler.handle(ROUTE.upstreamTopic(), payload("920006001", "device-1")));

        assertNotNull(accepted.inboxResult());
        assertEquals(1, inbox.calls);
        assertEquals(1, inbox.received.size());
        assertEquals("920006001", inbox.received.get(0).tenantId());
        assertEquals("product-1", inbox.received.get(0).productIdentification());
        assertEquals("device-1", inbox.received.get(0).deviceIdentification());
        assertEquals(1, authority.calls);
        assertEquals(ROUTE, authority.lastRoute);
    }

    @Test
    void onlyAuthorizedTopicProductReachesInboxAndPayloadProductIsIgnored() {
        byte[] payload = payload("920006001", "device-1", "forged-product");

        CenterTelemetryIngressResult.Accepted accepted =
                assertInstanceOf(CenterTelemetryIngressResult.Accepted.class,
                        handler.handle(ROUTE.upstreamTopic(), payload));

        assertNotNull(accepted.inboxResult());
        InboxEnvelope received = inbox.received.get(0);
        assertEquals(ROUTE.productIdentification(), received.productIdentification());
        assertArrayEquals(payload, received.canonicalBytes());
        assertEquals(sha256(payload), received.contentSha256());
    }

    @Test
    void classifiesTopicAndEnvelopeFailuresBeforeAuthorityLookup() {
        assertRejected(TelemetryIngressRejectionCode.TELEMETRY_TOPIC_MALFORMED,
                handler.handle("/iot/product-1/device-1/property/upstream/report/", validPayload()));
        assertRejected(TelemetryIngressRejectionCode.TELEMETRY_TOPIC_PRODUCT_INVALID,
                handler.handle("/iot//device-1/property/upstream/report", validPayload()));
        assertRejected(TelemetryIngressRejectionCode.TELEMETRY_TOPIC_DEVICE_INVALID,
                handler.handle("/iot/product-1/+/property/upstream/report", validPayload()));
        assertRejected(TelemetryIngressRejectionCode.TELEMETRY_ENVELOPE_INVALID,
                handler.handle(ROUTE.upstreamTopic(),
                        "{\"messageId\":\"m-1\",\"tenantId\":\"920006001\"}".getBytes(StandardCharsets.UTF_8)));
        assertRejected(TelemetryIngressRejectionCode.TELEMETRY_TOPIC_DEVICE_ENVELOPE_MISMATCH,
                handler.handle(ROUTE.upstreamTopic(), payload("920006001", "other-device")));
        assertRejected(TelemetryIngressRejectionCode.TELEMETRY_ENVELOPE_TENANT_INVALID,
                handler.handle(ROUTE.upstreamTopic(), payload("01", "device-1")));

        assertEquals(0, inbox.calls);
        assertEquals(0, authority.calls);
    }

    @Test
    void classifiesEachAuthorityOutcomeWithoutCallingInbox() {
        authority = new RecordingAuthority(new TelemetryDeviceAuthorityPort.Resolution.NotFound());
        handler = newHandler();
        assertRejected(TelemetryIngressRejectionCode.TELEMETRY_DEVICE_REGISTRATION_NOT_FOUND,
                handler.handle(ROUTE.upstreamTopic(), validPayload()));
        assertEquals(1, authority.calls);

        authority = new RecordingAuthority(new TelemetryDeviceAuthorityPort.Resolution.Ambiguous());
        handler = newHandler();
        assertRejected(TelemetryIngressRejectionCode.TELEMETRY_DEVICE_REGISTRATION_AMBIGUOUS,
                handler.handle(ROUTE.upstreamTopic(), validPayload()));
        assertEquals(1, authority.calls);

        authority = new RecordingAuthority(new TelemetryDeviceAuthorityPort.Resolution.Unavailable());
        handler = newHandler();
        assertRejected(TelemetryIngressRejectionCode.TELEMETRY_DEVICE_AUTHORITY_UNAVAILABLE,
                handler.handle(ROUTE.upstreamTopic(), validPayload()));
        assertEquals(1, authority.calls);

        authority = new RecordingAuthority(
                new TelemetryDeviceAuthorityPort.Resolution.Resolved("920006002"));
        handler = newHandler();
        assertRejected(TelemetryIngressRejectionCode.TELEMETRY_DEVICE_REGISTRATION_TENANT_MISMATCH,
                handler.handle(ROUTE.upstreamTopic(), validPayload()));
        assertEquals(1, authority.calls);

        assertEquals(0, inbox.calls);
    }

    @Test
    void legalWrongProductIsResolvedByAuthorityAsNotFound() {
        TelemetryRoute wrongProduct = new TelemetryRoute("product-2", "device-1");
        authority = new RecordingAuthority(new TelemetryDeviceAuthorityPort.Resolution.NotFound());
        handler = newHandler();

        assertRejected(TelemetryIngressRejectionCode.TELEMETRY_DEVICE_REGISTRATION_NOT_FOUND,
                handler.handle(wrongProduct.upstreamTopic(), validPayload()));
        assertEquals(1, authority.calls);
        assertEquals(wrongProduct, authority.lastRoute);
        assertEquals(0, inbox.calls);
    }

    @Test
    void invalidAuthorityTenantIsUnavailableAndDependencyExceptionsDoNotBecomeNotFound() {
        authority = new RecordingAuthority(
                new TelemetryDeviceAuthorityPort.Resolution.Resolved("01"));
        handler = newHandler();
        assertRejected(TelemetryIngressRejectionCode.TELEMETRY_DEVICE_AUTHORITY_UNAVAILABLE,
                handler.handle(ROUTE.upstreamTopic(), validPayload()));
        assertEquals(1, authority.calls);

        authority = new RecordingAuthority(null);
        authority.failure = new IllegalStateException("dependency unavailable");
        handler = newHandler();
        assertRejected(TelemetryIngressRejectionCode.TELEMETRY_DEVICE_AUTHORITY_UNAVAILABLE,
                handler.handle(ROUTE.upstreamTopic(), validPayload()));
        assertEquals(1, authority.calls);

        assertEquals(0, inbox.calls);
    }

    @Test
    void inboxFailuresPropagateAndAreNotReclassifiedOrAcked() {
        inbox.failure = new IllegalStateException("inbox unavailable");
        assertThrows(IllegalStateException.class,
                () -> handler.handle(ROUTE.upstreamTopic(), validPayload()));
        assertEquals(1, inbox.calls);
        assertEquals(1, authority.calls);
    }

    private CenterTelemetryIngressHandler newHandler() {
        return new CenterTelemetryIngressHandler(
                new TelemetryUpstreamTopicParser(), authority, inbox);
    }

    private static byte[] validPayload() {
        return payload("920006001", "device-1");
    }

    private static byte[] payload(String tenantId, String deviceIdentification) {
        return payload(tenantId, deviceIdentification, null);
    }

    private static byte[] payload(String tenantId, String deviceIdentification,
                                  String payloadProductIdentification) {
        String json = "{\"messageId\":\"m-1\",\"requestId\":\"r-1\","
                + "\"tenantId\":\"" + tenantId + "\",\"siteCode\":\"site-1\","
                + "\"deviceIdentification\":\"" + deviceIdentification + "\","
                + "\"propertyCode\":\"voltage-a\",\"value\":\"220\""
                + (payloadProductIdentification == null ? "" :
                ",\"productIdentification\":\"" + payloadProductIdentification + "\"")
                + ","
                + "\"collectedAt\":\"2026-08-21T00:00:00Z\","
                + "\"sequence\":1,\"source\":\"collector\",\"configVersion\":1}";
        return json.getBytes(StandardCharsets.UTF_8);
    }

    private static String sha256(byte[] input) {
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256").digest(input);
            StringBuilder hex = new StringBuilder(hash.length * 2);
            for (byte value : hash) {
                hex.append(Character.forDigit((value >>> 4) & 0xF, 16));
                hex.append(Character.forDigit(value & 0xF, 16));
            }
            return hex.toString();
        } catch (Exception exception) {
            throw new AssertionError(exception);
        }
    }

    private static void assertRejected(TelemetryIngressRejectionCode expected,
                                       CenterTelemetryIngressResult result) {
        CenterTelemetryIngressResult.Rejected rejected =
                assertInstanceOf(CenterTelemetryIngressResult.Rejected.class, result);
        assertEquals(expected, rejected.rejection().code());
        assertEquals(expected.disposition(), rejected.rejection().disposition());
    }

    private static final class RecordingInbox implements TelemetryInboxPort {
        private int calls;
        private List<InboxEnvelope> received = List.of();
        private RuntimeException failure;

        @Override
        public InboxReceiveResult receiveEnvelopes(List<InboxEnvelope> envelopes) {
            calls++;
            received = List.copyOf(envelopes);
            if (failure != null) {
                throw failure;
            }
            InboxEnvelope envelope = envelopes.get(0);
            return new InboxReceiveResult.Batch(List.of(new InboxReceiveResult.Item(
                    0, envelope.messageId(), envelope.requestId(),
                    InboxReceiveResult.Status.ACCEPTED_DURABLE, 1L)));
        }
    }

    private static final class RecordingAuthority implements TelemetryDeviceAuthorityPort {
        private TelemetryDeviceAuthorityPort.Resolution resolution;
        private RuntimeException failure;
        private int calls;
        private TelemetryRoute lastRoute;

        private RecordingAuthority(TelemetryDeviceAuthorityPort.Resolution resolution) {
            this.resolution = resolution;
        }

        @Override
        public Resolution resolve(TelemetryRoute route) {
            calls++;
            lastRoute = route;
            if (failure != null) {
                throw failure;
            }
            return resolution;
        }
    }
}
