package com.basiclab.iot.sink.protocol.polling;

import com.basiclab.iot.sink.polling.CollectorConfigSnapshot;
import com.basiclab.iot.sink.polling.CollectorDevice;
import com.basiclab.iot.sink.polling.CollectorSerialBus;
import com.basiclab.iot.sink.polling.PollingConfigProvider;
import com.basiclab.iot.sink.telemetry.outbox.TelemetryOutboxPort;
import com.basiclab.iot.sink.telemetry.outbox.TelemetryRoute;
import com.basiclab.iot.sink.telemetry.outbox.TelemetryRouteSetProvider;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.TreeSet;

/**
 * On-demand union of routes from the applied collector snapshot and the
 * unfinished SQLite outbox.  This class has no cache, thread, MQTT, or Spring
 * lifecycle side effect.
 */
public final class CollectorTelemetryRouteSetProvider implements TelemetryRouteSetProvider {

    private final PollingConfigProvider pollingConfigProvider;
    private final TelemetryOutboxPort telemetryOutboxPort;

    public CollectorTelemetryRouteSetProvider(PollingConfigProvider pollingConfigProvider,
                                               TelemetryOutboxPort telemetryOutboxPort) {
        this.pollingConfigProvider = Objects.requireNonNull(pollingConfigProvider,
                "pollingConfigProvider");
        this.telemetryOutboxPort = Objects.requireNonNull(telemetryOutboxPort,
                "telemetryOutboxPort");
    }

    @Override
    public List<TelemetryRoute> currentRoutes() {
        TreeSet<TelemetryRoute> routes = new TreeSet<>();
        pollingConfigProvider.current().ifPresent(snapshot -> addSnapshotRoutes(routes, snapshot));
        routes.addAll(telemetryOutboxPort.listUnfinishedRoutes());
        return List.copyOf(new ArrayList<>(routes));
    }

    private static void addSnapshotRoutes(TreeSet<TelemetryRoute> routes,
                                          CollectorConfigSnapshot snapshot) {
        for (CollectorSerialBus serialBus : snapshot.serialBuses()) {
            for (CollectorDevice device : serialBus.devices()) {
                routes.add(new TelemetryRoute(snapshot.productIdentification(),
                        device.deviceIdentification()));
            }
        }
    }
}
