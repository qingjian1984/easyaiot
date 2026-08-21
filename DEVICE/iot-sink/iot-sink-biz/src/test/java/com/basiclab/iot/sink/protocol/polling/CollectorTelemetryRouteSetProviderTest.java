package com.basiclab.iot.sink.protocol.polling;

import com.basiclab.iot.sink.polling.CollectorConfigObservation;
import com.basiclab.iot.sink.polling.CollectorConfigSnapshot;
import com.basiclab.iot.sink.polling.CollectorDevice;
import com.basiclab.iot.sink.polling.CollectorPoint;
import com.basiclab.iot.sink.polling.CollectorSerialBus;
import com.basiclab.iot.sink.polling.PollingConfigProvider;
import com.basiclab.iot.sink.telemetry.outbox.AckCommand;
import com.basiclab.iot.sink.telemetry.outbox.AppendBatchResult;
import com.basiclab.iot.sink.telemetry.outbox.ClaimBatchResult;
import com.basiclab.iot.sink.telemetry.outbox.TelemetryOutboxBatch;
import com.basiclab.iot.sink.telemetry.outbox.TelemetryOutboxPort;
import com.basiclab.iot.sink.telemetry.outbox.TelemetryRoute;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class CollectorTelemetryRouteSetProviderTest {

    @Test
    void returnsAppliedAndUnfinishedUnionSortedDedupedAndImmutable() {
        FakePollingConfigProvider config = new FakePollingConfigProvider(snapshot());
        FakeOutbox outbox = new FakeOutbox(List.of(
                new TelemetryRoute("collector-product", "device-1"),
                new TelemetryRoute("other-product", "device-0")));
        CollectorTelemetryRouteSetProvider provider =
                new CollectorTelemetryRouteSetProvider(config, outbox);

        assertEquals(List.of(
                new TelemetryRoute("collector-product", "device-1"),
                new TelemetryRoute("collector-product", "device-2"),
                new TelemetryRoute("other-product", "device-0")), provider.currentRoutes());
        assertThrows(UnsupportedOperationException.class,
                () -> provider.currentRoutes().add(new TelemetryRoute("x", "y")));
    }

    @Test
    void withoutAppliedSnapshotStillReturnsUnfinishedRoutes() {
        FakePollingConfigProvider config = new FakePollingConfigProvider(null);
        FakeOutbox outbox = new FakeOutbox(List.of(new TelemetryRoute("product", "device")));

        assertEquals(List.of(new TelemetryRoute("product", "device")),
                new CollectorTelemetryRouteSetProvider(config, outbox).currentRoutes());
    }

    @Test
    void readsBothSourcesOnEveryQuery() {
        FakePollingConfigProvider config = new FakePollingConfigProvider(snapshot());
        FakeOutbox outbox = new FakeOutbox(new ArrayList<>());
        CollectorTelemetryRouteSetProvider provider =
                new CollectorTelemetryRouteSetProvider(config, outbox);

        assertEquals(2, provider.currentRoutes().size());
        outbox.addUnfinished(new TelemetryRoute("other", "device"));
        assertEquals(3, provider.currentRoutes().size());
    }

    @Test
    void unboundAppliedRouteRemainsUntilOutboxTerminalState() {
        TelemetryRoute deviceOne = new TelemetryRoute("collector-product", "device-1");
        TelemetryRoute deviceTwo = new TelemetryRoute("collector-product", "device-2");
        FakePollingConfigProvider config = new FakePollingConfigProvider(snapshot());
        FakeOutbox outbox = new FakeOutbox(List.of(deviceOne, deviceTwo));
        outbox.setStatus(deviceTwo, Status.IN_FLIGHT);
        CollectorTelemetryRouteSetProvider provider =
                new CollectorTelemetryRouteSetProvider(config, outbox);

        config.setSnapshot(snapshotAfterUnbind());
        TelemetryRoute replacement = new TelemetryRoute("collector-product", "new-device");
        assertEquals(List.of(deviceOne, deviceTwo, replacement), provider.currentRoutes());

        outbox.setStatus(deviceOne, Status.ACKED);
        assertEquals(List.of(deviceTwo, replacement), provider.currentRoutes());
        outbox.setStatus(deviceTwo, Status.DEAD_LETTER);
        assertEquals(List.of(replacement), provider.currentRoutes());
    }

    @Test
    void dedupesRepeatedDeviceRouteAcrossSerialBuses() {
        CollectorPoint point = point();
        CollectorDevice first = new CollectorDevice("id-1", "device-1", 1,
                1_000L, 1_000L, 0, List.of(point));
        CollectorDevice duplicate = new CollectorDevice("id-2", "device-1", 2,
                1_000L, 1_000L, 0, List.of(point));
        CollectorDevice third = new CollectorDevice("id-3", "device-2", 3,
                1_000L, 1_000L, 0, List.of(point));
        CollectorSerialBus firstBus = bus("bus-1", "COM1", first);
        CollectorSerialBus secondBus = bus("bus-2", "COM2", duplicate, third);
        FakePollingConfigProvider config = new FakePollingConfigProvider(
                snapshotWithBuses(List.of(firstBus, secondBus)));

        assertEquals(List.of(
                new TelemetryRoute("collector-product", "device-1"),
                new TelemetryRoute("collector-product", "device-2")),
                new CollectorTelemetryRouteSetProvider(config,
                        new FakeOutbox(List.of())).currentRoutes());
    }

    private static CollectorConfigSnapshot snapshot() {
        CollectorPoint point = point();
        CollectorDevice first = new CollectorDevice("id-1", "device-2", 1,
                1_000L, 1_000L, 0, List.of(point));
        CollectorDevice second = new CollectorDevice("id-2", "device-1", 2,
                1_000L, 1_000L, 0, List.of(point));
        return snapshotWithBuses(List.of(bus("bus", "COM1", first, second)));
    }

    private static CollectorConfigSnapshot snapshotAfterUnbind() {
        CollectorDevice replacement = new CollectorDevice("id-new", "new-device", 3,
                1_000L, 1_000L, 0, List.of(point()));
        return snapshotWithBuses(List.of(bus("bus", "COM1", replacement)));
    }

    private static CollectorConfigSnapshot snapshotWithBuses(List<CollectorSerialBus> buses) {
        return new CollectorConfigSnapshot("1.1", "collector-product", "workload",
                "tenant", "site", "site-code", 1L, "2026-08-21T00:00:00Z",
                buses, null);
    }

    private static CollectorPoint point() {
        return new CollectorPoint("voltage", "HOLDING_REGISTER", 0, 1,
                "UINT16", "BIG_ENDIAN", "BIG_ENDIAN", "1", "0", "NORMAL_TELEMETRY",
                false, "default");
    }

    private static CollectorSerialBus bus(String id, String port, CollectorDevice... devices) {
        return new CollectorSerialBus(id, port, 9_600, 8,
                "1", "NONE", 0, true, List.of(devices));
    }

    private static final class FakePollingConfigProvider implements PollingConfigProvider {
        private CollectorConfigSnapshot snapshot;

        private FakePollingConfigProvider(CollectorConfigSnapshot snapshot) {
            this.snapshot = snapshot;
        }

        private void setSnapshot(CollectorConfigSnapshot snapshot) {
            this.snapshot = snapshot;
        }

        @Override
        public Optional<CollectorConfigSnapshot> current() {
            return Optional.ofNullable(snapshot);
        }

        @Override
        public Optional<CollectorConfigSnapshot> candidate(long version) {
            return Optional.empty();
        }

        @Override
        public CollectorConfigObservation reconcile(GraphApplier graphApplier) {
            return CollectorConfigObservation.waiting("workload");
        }
    }

    private static final class FakeOutbox implements TelemetryOutboxPort {
        private final Map<TelemetryRoute, Status> routes = new LinkedHashMap<>();

        private FakeOutbox(List<TelemetryRoute> routes) {
            for (TelemetryRoute route : routes) {
                addUnfinished(route);
            }
        }

        private void addUnfinished(TelemetryRoute route) {
            routes.put(route, Status.PENDING);
        }

        private void setStatus(TelemetryRoute route, Status status) {
            routes.put(route, status);
        }

        @Override
        public AppendBatchResult appendBatch(TelemetryOutboxBatch batch, Duration enqueueTimeout) {
            return new AppendBatchResult.Success(List.of(), List.of());
        }

        @Override
        public ClaimBatchResult claimBatch(int maxCount, Duration lease) {
            return new ClaimBatchResult.Empty();
        }

        @Override
        public List<TelemetryRoute> listUnfinishedRoutes() {
            List<TelemetryRoute> unfinished = new ArrayList<>();
            for (Map.Entry<TelemetryRoute, Status> entry : routes.entrySet()) {
                if (entry.getValue() == Status.PENDING || entry.getValue() == Status.IN_FLIGHT) {
                    unfinished.add(entry.getKey());
                }
            }
            return unfinished;
        }

        @Override
        public void applyAck(AckCommand ack) {
        }
    }

    private enum Status {
        PENDING,
        IN_FLIGHT,
        ACKED,
        DEAD_LETTER
    }
}
