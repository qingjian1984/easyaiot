package com.basiclab.iot.sink.telemetry.ack;

import com.basiclab.iot.sink.telemetry.outbox.TelemetryRoute;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TelemetryAckTopicContractTest {

    private final TelemetryAckTopicParser parser = new TelemetryAckTopicParser();

    @Test
    void parsesAndBuildsOnlyTheCanonicalAckTopic() {
        String topic = "/iot/power-meter/meter-01/property/downstream/report/ack";
        TelemetryRoute route = parser.parse(topic);
        assertEquals("power-meter", route.productIdentification());
        assertEquals("meter-01", route.deviceIdentification());
        assertEquals(topic, parser.build(route));
        assertTrue(parser.isCanonical(topic));
    }

    @Test
    void rejectsLegacyWildcardsSharedSubscriptionsAndExtraLevels() {
        String[] invalid = {
                "/telemetry/ack/site/meter/property",
                "/iot/+/meter-01/property/downstream/report/ack",
                "/iot/power-meter/#/property/downstream/report/ack",
                "$share/center-v1//iot/power-meter/meter-01/property/downstream/report/ack",
                "/iot/power-meter/meter-01/property/downstream/report/ack/extra",
                "/iot/power-meter//property/downstream/report/ack",
                "/iot/power-meter/meter/01/property/downstream/report/ack"
        };
        for (String topic : invalid) {
            assertFalse(parser.isCanonical(topic), topic);
            assertThrows(IllegalArgumentException.class, () -> parser.parse(topic));
        }
    }

    @Test
    void rejectsUnsafeTopicLevelsWithoutNormalizingThem() {
        assertThrows(IllegalArgumentException.class,
                () -> parser.parse("/iot/power-meter/meter-01\n/property/downstream/report/ack"));
        assertThrows(IllegalArgumentException.class,
                () -> parser.parse("/iot/power-meter/meter-01/property/downstream/report/ACK"));
    }

    @Test
    void optionalParseDoesNotReflectInvalidTopic() {
        assertTrue(parser.tryParse(
                "/iot/power-meter/meter-01/property/downstream/report/ack").isPresent());
        assertTrue(parser.tryParse("/telemetry/ack/legacy").isEmpty());
        assertThrows(IllegalArgumentException.class, () -> parser.build(null));
    }
}
