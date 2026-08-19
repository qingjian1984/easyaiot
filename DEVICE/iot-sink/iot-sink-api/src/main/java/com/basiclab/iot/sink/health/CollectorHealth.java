package com.basiclab.iot.sink.health;

import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Closed collector health summary. The derived {@link #facets()} view has four
 * and only four keys; center owns the outbox/MQTT/application-ACK sub-state.
 */
public record CollectorHealth(
        CollectorHealthFacet process,
        CollectorHealthFacet config,
        CollectorHealthFacet serial,
        CollectorHealthFacet center,
        CollectorHealthFacet overall,
        Instant observedAt
) {
    private static final Set<String> FACET_NAMES = Set.of("process", "config", "serial", "center");

    public CollectorHealth {
        process = Objects.requireNonNull(process, "process");
        config = Objects.requireNonNull(config, "config");
        serial = Objects.requireNonNull(serial, "serial");
        center = Objects.requireNonNull(center, "center");
        overall = Objects.requireNonNull(overall, "overall");
        observedAt = Objects.requireNonNull(observedAt, "observedAt");
        HealthStatus expected = HealthStatus.worst(
                HealthStatus.worst(process.status(), config.status()),
                HealthStatus.worst(serial.status(), center.status()));
        if (overall.status() != expected) {
            throw new IllegalArgumentException("overall status does not match facets");
        }
    }

    /** Returns a newly-created immutable view; no free-form state is stored. */
    public Map<String, CollectorHealthFacet> facets() {
        return Map.of("process", process, "config", config, "serial", serial, "center", center);
    }

    public Set<String> facetNames() {
        return FACET_NAMES;
    }

    public CollectorHealthFacet facet(String name) {
        return switch (name) {
            case "process" -> process;
            case "config" -> config;
            case "serial" -> serial;
            case "center" -> center;
            default -> throw new IllegalArgumentException("unknown health facet");
        };
    }
}
