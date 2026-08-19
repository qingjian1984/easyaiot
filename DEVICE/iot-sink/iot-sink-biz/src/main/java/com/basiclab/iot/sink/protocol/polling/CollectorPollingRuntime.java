package com.basiclab.iot.sink.protocol.polling;

import com.basiclab.iot.sink.polling.CollectorConfigObservation;
import com.basiclab.iot.sink.polling.CollectorConfigSnapshot;
import com.basiclab.iot.sink.polling.CollectorDevice;
import com.basiclab.iot.sink.polling.CollectorPoint;
import com.basiclab.iot.sink.polling.CollectorSerialBus;
import com.basiclab.iot.sink.polling.PollingConfigProvider;
import com.basiclab.iot.sink.polling.PollingStatusReporter;
import com.basiclab.iot.sink.protocol.modbus.IotModbusRtuPollingProtocol;

import javax.annotation.PostConstruct;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Collector-profile runtime. It consumes one local applied graph and sends
 * each poll batch only to CollectorTelemetryWriter/TelemetryOutboxPort.
 */
public final class CollectorPollingRuntime implements AutoCloseable {
    private final PollingConfigProvider provider;
    private final PollingStatusReporter statusReporter;
    private final IotModbusRtuPollingProtocol rtuEngine;
    private final CollectorTelemetryWriter telemetryWriter;
    private final java.time.Duration reconcileInterval;
    private final AtomicReference<CollectorPollingGraph> graph = new AtomicReference<>();
    private final AtomicReference<AppliedIdentity> appliedIdentity = new AtomicReference<>();
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1, runnable -> {
        Thread thread = new Thread(runnable, "easyaiot-collector-poller");
        thread.setDaemon(true);
        return thread;
    });
    private final List<ScheduledFuture<?>> pollingTasks = new CopyOnWriteArrayList<>();
    private final ScheduledExecutorService reconcileScheduler = Executors.newSingleThreadScheduledExecutor(runnable -> {
        Thread thread = new Thread(runnable, "easyaiot-collector-config-reconciler");
        thread.setDaemon(true);
        return thread;
    });
    private volatile ScheduledFuture<?> reconcileTask;
    private volatile boolean initialized;

    public CollectorPollingRuntime(PollingConfigProvider provider,
                                   PollingStatusReporter statusReporter,
                                   IotModbusRtuPollingProtocol rtuEngine,
                                   CollectorTelemetryWriter telemetryWriter) {
        this(provider, statusReporter, rtuEngine, telemetryWriter, java.time.Duration.ofSeconds(1));
    }

    public CollectorPollingRuntime(PollingConfigProvider provider,
                                   PollingStatusReporter statusReporter,
                                   IotModbusRtuPollingProtocol rtuEngine,
                                   CollectorTelemetryWriter telemetryWriter,
                                   java.time.Duration reconcileInterval) {
        this.provider = Objects.requireNonNull(provider, "provider");
        this.statusReporter = Objects.requireNonNull(statusReporter, "statusReporter");
        this.rtuEngine = Objects.requireNonNull(rtuEngine, "rtuEngine");
        this.telemetryWriter = Objects.requireNonNull(telemetryWriter, "telemetryWriter");
        this.reconcileInterval = Objects.requireNonNull(reconcileInterval, "reconcileInterval");
        if (reconcileInterval.isZero() || reconcileInterval.isNegative()) {
            throw new IllegalArgumentException("reconcileInterval must be positive");
        }
    }

    /** Spring lifecycle entrypoint: reconcile local state, then schedule each device interval. */
    @PostConstruct
    public void initialize() {
        if (initialized) {
            return;
        }
        initialized = true;
        start();
        long intervalMillis = Math.max(1L, reconcileInterval.toMillis());
        reconcileTask = reconcileScheduler.scheduleWithFixedDelay(() -> {
            try {
                start();
            } catch (RuntimeException ignored) {
                // A failed local reconciliation is represented by observed.json;
                // the loop must remain available for a later desired retry.
            }
        }, intervalMillis, intervalMillis, TimeUnit.MILLISECONDS);
    }

    /** Reconcile the local state once; initialized instances also refresh interval schedules. */
    public synchronized CollectorConfigObservation start() {
        AppliedIdentity before = appliedIdentity.get();
        AtomicReference<CollectorPollingGraph> preparedGraph = new AtomicReference<>();
        CollectorConfigObservation observation = provider.reconcile(new PollingConfigProvider.GraphApplier() {
            @Override
            public void prepare(CollectorConfigSnapshot snapshot) {
                preparedGraph.set(null);
                AppliedIdentity identity = AppliedIdentity.from(snapshot);
                if (identity.equals(appliedIdentity.get()) && graph.get() != null) {
                    return;
                }
                preparedGraph.set(CollectorPollingGraph.from(snapshot));
            }

            @Override
            public void replace(CollectorConfigSnapshot snapshot) {
                CollectorPollingGraph built = preparedGraph.getAndSet(null);
                if (built == null) {
                    if (AppliedIdentity.from(snapshot).equals(appliedIdentity.get()) && graph.get() != null) {
                        return;
                    }
                    throw new IllegalStateException("collector graph was not prepared");
                }
                AppliedIdentity identity = AppliedIdentity.from(snapshot);
                if (identity.equals(appliedIdentity.get()) && graph.get() != null) {
                    return;
                }
                graph.set(built);
                appliedIdentity.set(identity);
            }

            @Override
            public void restore(CollectorConfigSnapshot snapshot) {
                if (snapshot == null) {
                    graph.set(null);
                    appliedIdentity.set(null);
                } else {
                    graph.set(CollectorPollingGraph.from(snapshot));
                    appliedIdentity.set(AppliedIdentity.from(snapshot));
                }
            }
        });
        // The local provider persists observed as part of the same
        // state transition. Do not report it a second time: a second write could
        // turn a successfully committed APPLIED state into a later failure.
        if (observation.status() == com.basiclab.iot.sink.polling.CollectorConfigStatus.WAITING_CONFIG) {
            graph.set(null);
            appliedIdentity.set(null);
        }
        if (initialized && !Objects.equals(before, appliedIdentity.get())) {
            scheduleGraph();
        }
        return observation;
    }

    /** Explicitly trigger one configuration reconciliation (useful for a file-watch/event bridge). */
    public CollectorConfigObservation reconcileNow() {
        return start();
    }

    /** Poll all devices in the immutable applied graph as independent batches. */
    public int pollOnce() {
        CollectorPollingGraph current = graph.get();
        if (current == null) {
            return 0;
        }
        int batches = 0;
        for (BoundDevice bound : current.devices()) {
            if (pollDevice(bound)) {
                batches++;
            }
        }
        return batches;
    }

    public CollectorConfigSnapshot appliedSnapshot() {
        CollectorPollingGraph value = graph.get();
        return value == null ? null : value.snapshot();
    }

    @Override
    public void close() {
        initialized = false;
        cancelPollingTasks();
        ScheduledFuture<?> task = reconcileTask;
        if (task != null) {
            task.cancel(false);
            reconcileTask = null;
        }
        scheduler.shutdownNow();
        reconcileScheduler.shutdownNow();
        graph.set(null);
        appliedIdentity.set(null);
    }

    /** Explicit boundary for non-startup statuses; startup reconciliation already persists observed atomically. */
    public void reportStatus(CollectorConfigObservation observation) {
        statusReporter.report(Objects.requireNonNull(observation, "observation"));
    }

    private boolean pollDevice(BoundDevice bound) {
        CollectorPollingGraph current = graph.get();
        if (current == null || !current.devices().contains(bound)) {
            return false;
        }
        try {
            Map<String, Object> values = rtuEngine.poll(bound.config());
            if (values == null || values.isEmpty()) {
                return false;
            }
            telemetryWriter.store(current.snapshot(), bound.device(), values,
                    IotModbusRtuPollingProtocol.PROTOCOL_TYPE);
            return true;
        } catch (Exception ignored) {
            // Device I/O failure is not a config-state transition and
            // must not escape into a central message/DB path.
            return false;
        }
    }

    private void scheduleGraph() {
        cancelPollingTasks();
        CollectorPollingGraph current = graph.get();
        if (current == null) {
            return;
        }
        for (BoundDevice bound : current.devices()) {
            long intervalMs = Math.max(1L, bound.device().pollIntervalMs());
            pollingTasks.add(scheduler.scheduleWithFixedDelay(
                    () -> pollDevice(bound), 0L, intervalMs, TimeUnit.MILLISECONDS));
        }
    }

    private void cancelPollingTasks() {
        for (ScheduledFuture<?> task : pollingTasks) {
            task.cancel(false);
        }
        pollingTasks.clear();
    }

    private record AppliedIdentity(long version, String payloadSha256) {
        private static AppliedIdentity from(CollectorConfigSnapshot snapshot) {
            return new AppliedIdentity(snapshot.configVersion(), snapshot.payloadSha256());
        }
    }

    private record BoundDevice(CollectorDevice device, IndustrialDeviceConfig config) {
    }

    private record CollectorPollingGraph(CollectorConfigSnapshot snapshot, List<BoundDevice> devices) {
        private static CollectorPollingGraph from(CollectorConfigSnapshot snapshot) {
            List<BoundDevice> devices = new ArrayList<>();
            for (CollectorSerialBus bus : snapshot.serialBuses()) {
                for (CollectorDevice device : bus.devices()) {
                    devices.add(new BoundDevice(device, toIndustrialConfig(bus, device)));
                }
            }
            return new CollectorPollingGraph(snapshot, List.copyOf(devices));
        }

        private static IndustrialDeviceConfig toIndustrialConfig(CollectorSerialBus bus,
                                                                   CollectorDevice device) {
            IndustrialDeviceConfig config = new IndustrialDeviceConfig();
            config.setType("MODBUS_RTU");
            config.setEnabled(true);
            config.setSerialPort(bus.serialPort());
            config.setBaudRate(bus.baudRate());
            config.setDataBits(bus.dataBits());
            config.setStopBits(bus.stopBits());
            config.setParity(bus.parity());
            config.setTransmitDelayMs(bus.transmitDelayMs());
            config.setRs485Mode(bus.rs485Mode());
            config.setUnitId(device.unitId());
            config.setPollIntervalMs(device.pollIntervalMs());
            config.setConfigVersion(0L); // snapshot identity is carried by the writer overload
            List<IndustrialDeviceConfig.Point> points = new ArrayList<>();
            for (CollectorPoint source : device.points()) {
                IndustrialDeviceConfig.Point point = new IndustrialDeviceConfig.Point();
                point.setPropertyCode(source.propertyCode());
                point.setFunction(source.function());
                point.setAddress(source.address());
                point.setQuantity(source.quantity());
                point.setDataType(source.dataType());
                point.setByteOrder(source.byteOrder());
                point.setWordOrder(source.wordOrder());
                point.setScale(Double.valueOf(source.scale()));
                point.setOffset(Double.valueOf(source.offset()));
                point.setDataPriority(source.dataPriority());
                point.setWritable(source.writable());
                points.add(point);
            }
            config.setPoints(points);
            return config;
        }
    }
}
