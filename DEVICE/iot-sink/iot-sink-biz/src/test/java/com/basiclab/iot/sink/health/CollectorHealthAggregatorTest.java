package com.basiclab.iot.sink.health;

import org.junit.jupiter.api.Test;

import java.lang.reflect.RecordComponent;
import java.time.Instant;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CollectorHealthAggregatorTest {

    private static final Instant NOW = Instant.parse("2026-08-17T00:00:00Z");

    private static CollectorHealthFacet facet(HealthStatus status, String reason) {
        return new CollectorHealthFacet(status, reason, NOW);
    }

    @Test
    void publishesExactlyFourFacetsAndHealthyCenter() {
        CollectorHealth result = CollectorHealthAggregator.aggregate(
                facet(HealthStatus.HEALTHY, "PROCESS_OK"),
                facet(HealthStatus.HEALTHY, "CONFIG_OK"),
                facet(HealthStatus.HEALTHY, "SERIAL_OK"),
                facet(HealthStatus.HEALTHY, "OUTBOX_OK"),
                facet(HealthStatus.HEALTHY, "MQTT_OK"),
                facet(HealthStatus.HEALTHY, "ACK_OK"),
                NOW);

        assertEquals(Set.of("process", "config", "serial", "center"), result.facetNames());
        assertEquals(result.facetNames(), result.facets().keySet());
        assertEquals(HealthStatus.HEALTHY, result.center().status());
        assertEquals("CENTER_OK", result.center().reasonCode());
        assertEquals(HealthStatus.HEALTHY, result.overall().status());
        assertEquals("OK", result.overall().reasonCode());
        assertFalse(result.facets().containsKey("outbox"));
    }

    @Test
    void centerAndOverallUseFailedThenDegradedThenHealthy() {
        CollectorHealth degraded = CollectorHealthAggregator.aggregate(
                facet(HealthStatus.HEALTHY, "PROCESS_OK"),
                facet(HealthStatus.HEALTHY, "CONFIG_OK"),
                facet(HealthStatus.HEALTHY, "SERIAL_OK"),
                facet(HealthStatus.DEGRADED, "OUTBOX_LAGGING"),
                facet(HealthStatus.HEALTHY, "MQTT_OK"),
                facet(HealthStatus.HEALTHY, "ACK_OK"),
                NOW);
        assertEquals(HealthStatus.DEGRADED, degraded.center().status());
        assertEquals("OUTBOX_LAGGING", degraded.center().reasonCode());
        assertEquals(HealthStatus.DEGRADED, degraded.overall().status());

        CollectorHealth failed = CollectorHealthAggregator.aggregate(
                facet(HealthStatus.HEALTHY, "PROCESS_OK"),
                facet(HealthStatus.HEALTHY, "CONFIG_OK"),
                facet(HealthStatus.HEALTHY, "SERIAL_OK"),
                facet(HealthStatus.FAILED, "OUTBOX_UNAVAILABLE"),
                facet(HealthStatus.DEGRADED, "MQTT_LAGGING"),
                facet(HealthStatus.HEALTHY, "ACK_OK"),
                NOW);
        assertEquals(HealthStatus.FAILED, failed.center().status());
        assertEquals("OUTBOX_UNAVAILABLE", failed.center().reasonCode());
        assertEquals(HealthStatus.FAILED, failed.overall().status());
    }

    @Test
    void valueObjectsAreClosedAndRejectUnstableFields() {
        assertThrows(IllegalArgumentException.class,
                () -> facet(HealthStatus.HEALTHY, "reason with spaces"));
        assertThrows(IllegalArgumentException.class,
                () -> new CollectorHealthFacet(HealthStatus.FAILED, "FAIL/PATH", NOW));
        assertThrows(IllegalArgumentException.class,
                () -> new CollectorHealthFacet(HealthStatus.FAILED, "FAILED_" + "x".repeat(64), NOW));
        assertThrows(NullPointerException.class,
                () -> new CollectorHealthFacet(HealthStatus.HEALTHY, "OK", null));

        assertEquals(6, CollectorHealth.class.getRecordComponents().length);
        for (RecordComponent component : CollectorHealth.class.getRecordComponents()) {
            assertFalse(Map.class.isAssignableFrom(component.getType()),
                    "health record must not carry a free-form map");
        }
        assertThrows(IllegalArgumentException.class,
                () -> CollectorHealthAggregator.aggregate(
                        facet(HealthStatus.HEALTHY, "PROCESS_OK"),
                        facet(HealthStatus.HEALTHY, "CONFIG_OK"),
                        facet(HealthStatus.HEALTHY, "SERIAL_OK"),
                        null,
                        facet(HealthStatus.HEALTHY, "MQTT_OK"),
                        facet(HealthStatus.HEALTHY, "ACK_OK"),
                        NOW));
    }
}
