package com.basiclab.iot.sink.telemetry.inbox.route;

import com.basiclab.iot.sink.telemetry.outbox.TelemetryRoute;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

class TelemetryUpstreamTopicParserContractTest {

    private final TelemetryUpstreamTopicParser parser = new TelemetryUpstreamTopicParser();

    @Test
    void derivesOneExactSharedFilterAndRoundTripsCanonicalRoute() {
        TelemetryRoute route = new TelemetryRoute("product-1", "device-1");
        assertEquals("/iot/+/+/property/upstream/report", parser.sharedSubscriptionFilterValue());
        assertEquals(parser.sharedSubscriptionFilterValue(),
                TelemetryUpstreamTopicParser.sharedSubscriptionFilter());

        TelemetryUpstreamTopicParser.Result result = parser.parse(route.upstreamTopic());
        TelemetryUpstreamTopicParser.Parsed parsed =
                assertInstanceOf(TelemetryUpstreamTopicParser.Parsed.class, result);
        assertEquals(route, parsed.route());
        assertEquals(route.upstreamTopic(), route.upstreamTopic());
    }

    @Test
    void preservesExactLevelsAndRejectsOtherStructures() {
        List<String> malformed = Arrays.asList(
                null,
                "",
                "/iot/product-1/device-1/property/upstream/report/",
                "/iot/product-1/device-1/property/upstream/report/extra",
                "/iot/product-1/property/upstream/report",
                "/iot/product-1/device-1/property/downstream/report",
                "/iot/product-1/device-1/event/upstream/report",
                " /iot/product-1/device-1/property/upstream/report",
                "/iot/product-1/device-1/property/upstream/report%2F");

        for (String topic : malformed) {
            TelemetryUpstreamTopicParser.Rejected rejected =
                    assertInstanceOf(TelemetryUpstreamTopicParser.Rejected.class,
                            parser.parse(topic), "topic=" + topic);
            assertEquals(TelemetryIngressRejectionCode.TELEMETRY_TOPIC_MALFORMED,
                    rejected.rejection().code(), "topic=" + topic);
            assertEquals(TelemetryIngressDisposition.FINAL,
                    rejected.rejection().disposition());
        }
    }

    @Test
    void classifiesProductAndDeviceIdentityRulesWithoutNormalization() {
        List<String> invalidProducts = List.of(
                "/iot//device-1/property/upstream/report",
                "/iot/+/device-1/property/upstream/report",
                "/iot/#/device-1/property/upstream/report",
                "/iot/p\u0000/device-1/property/upstream/report");
        for (String topic : invalidProducts) {
            TelemetryUpstreamTopicParser.Rejected rejected =
                    assertInstanceOf(TelemetryUpstreamTopicParser.Rejected.class,
                            parser.parse(topic), "topic=" + topic);
            assertEquals(TelemetryIngressRejectionCode.TELEMETRY_TOPIC_PRODUCT_INVALID,
                    rejected.rejection().code(), "topic=" + topic);
        }

        List<String> invalidDevices = List.of(
                "/iot/product-1//property/upstream/report",
                "/iot/product-1/+/property/upstream/report",
                "/iot/product-1/#/property/upstream/report",
                "/iot/product-1/device-1\u0000/property/upstream/report");
        for (String topic : invalidDevices) {
            TelemetryUpstreamTopicParser.Rejected rejected =
                    assertInstanceOf(TelemetryUpstreamTopicParser.Rejected.class,
                            parser.parse(topic), "topic=" + topic);
            assertEquals(TelemetryIngressRejectionCode.TELEMETRY_TOPIC_DEVICE_INVALID,
                    rejected.rejection().code(), "topic=" + topic);
        }
    }

    @Test
    void acceptsUnicodeAsSuppliedAndDoesNotNormalizeOrDecode() {
        String nfd = "e\u0301";
        String nfc = "é";
        TelemetryRoute route = new TelemetryRoute("产品-" + nfd, "设备-" + nfc);
        TelemetryUpstreamTopicParser.Parsed parsed =
                assertInstanceOf(TelemetryUpstreamTopicParser.Parsed.class,
                        parser.parse(route.upstreamTopic()));
        assertEquals("产品-" + nfd, parsed.route().productIdentification());
        assertEquals("设备-" + nfc, parsed.route().deviceIdentification());
        assertNotEquals("产品-" + nfc, parsed.route().productIdentification());
        TelemetryUpstreamTopicParser.Parsed encoded =
                assertInstanceOf(TelemetryUpstreamTopicParser.Parsed.class,
                        parser.parse("/iot/产品-%C3%A9/设备-é/property/upstream/report"));
        assertEquals("产品-%C3%A9", encoded.route().productIdentification());
    }

    @Test
    void rejectsNonPropertyUpstreamAndBoundaryViolations() {
        String tooLongProduct = "p".repeat(TelemetryRoute.MAX_PRODUCT_IDENTIFICATION_CODE_POINTS + 1);
        String tooLongDevice = "d".repeat(TelemetryRoute.MAX_DEVICE_IDENTIFICATION_CODE_POINTS + 1);
        List<String> invalid = List.of(
                "/iot/" + tooLongProduct + "/device-1/property/upstream/report",
                "/iot/product-1/" + tooLongDevice + "/property/upstream/report",
                "/iot/\uD800/device-1/property/upstream/report",
                "/iot/product-1/\uDC00/property/upstream/report");
        for (String topic : invalid) {
            TelemetryUpstreamTopicParser.Rejected rejected =
                    assertInstanceOf(TelemetryUpstreamTopicParser.Rejected.class,
                            parser.parse(topic), "topic=" + topic);
            assertEquals(TelemetryIngressDisposition.FINAL,
                    rejected.rejection().disposition());
        }
    }
}
