package com.basiclab.iot.sink.health;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

/** Pure, deterministic health aggregation; it has no Spring or runtime state. */
public final class CollectorHealthAggregator {

    private CollectorHealthAggregator() {
    }

    /**
     * Aggregates the four published facets. Outbox, MQTT and application ACK
     * are inputs to center and never become a fifth published facet.
     */
    public static CollectorHealth aggregate(
            CollectorHealthFacet process,
            CollectorHealthFacet config,
            CollectorHealthFacet serial,
            CollectorHealthFacet outbox,
            CollectorHealthFacet mqtt,
            CollectorHealthFacet applicationAck,
            Instant observedAt) {
        Objects.requireNonNull(observedAt, "observedAt");
        CollectorHealthFacet center = aggregateCenter(outbox, mqtt, applicationAck);
        CollectorHealthFacet overall = aggregateOverall(process, config, serial, center);
        return new CollectorHealth(process, config, serial, center, overall, observedAt);
    }

    public static CollectorHealth aggregate(
            CollectorHealthFacet process,
            CollectorHealthFacet config,
            CollectorHealthFacet serial,
            CollectorHealthFacet outbox,
            CollectorHealthFacet mqtt,
            CollectorHealthFacet applicationAck) {
        return aggregate(process, config, serial, outbox, mqtt, applicationAck, Instant.now());
    }

    public static CollectorHealthFacet aggregateCenter(
            CollectorHealthFacet outbox,
            CollectorHealthFacet mqtt,
            CollectorHealthFacet applicationAck) {
        CollectorHealthFacet selected = selectWorst(List.of(
                requireFacet(outbox, "outbox"),
                requireFacet(mqtt, "mqtt"),
                requireFacet(applicationAck, "applicationAck")));
        if (selected.status() == HealthStatus.HEALTHY) {
            return CollectorHealthFacet.healthy("CENTER_OK", selected.since());
        }
        return new CollectorHealthFacet(selected.status(), selected.reasonCode(), selected.since());
    }

    private static CollectorHealthFacet aggregateOverall(
            CollectorHealthFacet process,
            CollectorHealthFacet config,
            CollectorHealthFacet serial,
            CollectorHealthFacet center) {
        CollectorHealthFacet selected = selectWorst(List.of(
                requireFacet(process, "process"),
                requireFacet(config, "config"),
                requireFacet(serial, "serial"),
                requireFacet(center, "center")));
        if (selected.status() == HealthStatus.HEALTHY) {
            return CollectorHealthFacet.healthy("OK", selected.since());
        }
        return new CollectorHealthFacet(selected.status(), selected.reasonCode(), selected.since());
    }

    private static CollectorHealthFacet requireFacet(CollectorHealthFacet facet, String name) {
        if (facet == null) {
            throw new IllegalArgumentException(name + " is required");
        }
        return facet;
    }

    /** Stable tie-break is declaration order, so results do not depend on a Map. */
    private static CollectorHealthFacet selectWorst(List<CollectorHealthFacet> values) {
        CollectorHealthFacet selected = values.get(0);
        for (int i = 1; i < values.size(); i++) {
            CollectorHealthFacet candidate = values.get(i);
            if (candidate.status().severity() > selected.status().severity()) {
                selected = candidate;
            }
        }
        return selected;
    }
}
