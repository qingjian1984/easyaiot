package com.basiclab.iot.sink.telemetry.outbox;

import com.basiclab.iot.sink.enums.IotDeviceTopicEnum;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class TelemetryRouteContractTest {

    @Test
    void preservesRawUnicodeAndBuildsTopicsOnlyThroughEnum() {
        String nfd = "e\u0301";
        String nfc = "é";
        TelemetryRoute route = new TelemetryRoute(" 产品 " + nfd, "设备 " + nfc);

        assertEquals(" 产品 " + nfd, route.productIdentification());
        assertEquals("设备 " + nfc, route.deviceIdentification());
        assertNotEquals(new TelemetryRoute(" 产品 " + nfc, "设备 " + nfc), route);
        assertEquals(IotDeviceTopicEnum.PROPERTY_UPSTREAM_REPORT.buildTopic(
                route.productIdentification(), route.deviceIdentification()), route.upstreamTopic());
        assertEquals(IotDeviceTopicEnum.PROPERTY_DOWNSTREAM_REPORT_ACK.buildTopic(
                route.productIdentification(), route.deviceIdentification()), route.ackTopic());
    }

    @Test
    void sortsByProductThenDeviceUsingStringOrder() {
        List<TelemetryRoute> routes = List.of(
                new TelemetryRoute("p2", "d1"),
                new TelemetryRoute("p1", "d2"),
                new TelemetryRoute("p1", "d1"));

        assertEquals(List.of(
                new TelemetryRoute("p1", "d1"),
                new TelemetryRoute("p1", "d2"),
                new TelemetryRoute("p2", "d1")), routes.stream().sorted().toList());
    }

    @Test
    void acceptsCodePointBoundariesAndSupplementaryCharacters() {
        String emoji = "😀";
        new TelemetryRoute("p".repeat(128), emoji.repeat(256));
        new TelemetryRoute(emoji.repeat(128), "d".repeat(256));

        assertThrows(IllegalArgumentException.class,
                () -> new TelemetryRoute("p".repeat(129), "d"));
        assertThrows(IllegalArgumentException.class,
                () -> new TelemetryRoute("p", "d".repeat(257)));
        assertThrows(IllegalArgumentException.class,
                () -> new TelemetryRoute(emoji.repeat(129), "d"));
        assertThrows(IllegalArgumentException.class,
                () -> new TelemetryRoute("p", emoji.repeat(257)));
    }

    @Test
    void rejectsNullBlankAndTopicWildcards() {
        List<String> invalid = Arrays.asList(null, "", " ", "\t", "/", "+", "#", "p/1");
        for (String value : invalid) {
            assertThrows(IllegalArgumentException.class,
                    () -> new TelemetryRoute(value, "device"), "product=" + value);
            assertThrows(IllegalArgumentException.class,
                    () -> new TelemetryRoute("product", value), "device=" + value);
        }
    }

    @Test
    void rejectsControlsAndUnpairedSurrogates() {
        for (String invalid : List.of("\u0000", "\u001F", "\u007F", "\u0080", "\u009F",
                "\uD800", "\uDC00")) {
            assertThrows(IllegalArgumentException.class,
                    () -> new TelemetryRoute(invalid, "device"), "product=" + invalid);
            assertThrows(IllegalArgumentException.class,
                    () -> new TelemetryRoute("product", invalid), "device=" + invalid);
        }
    }
}
