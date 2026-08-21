package com.basiclab.iot.sink.telemetry.outbox;

import java.util.List;

/** On-demand exact route-set boundary used by a collector ACK subscriber. */
public interface TelemetryRouteSetProvider {

    /**
     * Return the current applied-config and unfinished-outbox route union.
     * The returned list is sorted by {@link TelemetryRoute#compareTo} and is
     * immutable.
     */
    List<TelemetryRoute> currentRoutes();
}
