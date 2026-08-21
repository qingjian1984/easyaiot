package com.basiclab.iot.sink.protocol.polling;

import com.basiclab.iot.sink.polling.CollectorConfigStatus;
import com.basiclab.iot.sink.telemetry.envelope.TelemetryEnvelope;
import com.basiclab.iot.sink.telemetry.outbox.AckCommand;
import com.basiclab.iot.sink.telemetry.outbox.AppendBatchResult;
import com.basiclab.iot.sink.telemetry.outbox.ClaimBatchResult;
import com.basiclab.iot.sink.telemetry.outbox.TelemetryOutboxBatch;
import com.basiclab.iot.sink.telemetry.outbox.TelemetryOutboxPort;
import com.basiclab.iot.sink.telemetry.outbox.TelemetryRoute;
import com.basiclab.iot.sink.protocol.modbus.IotModbusRtuPollingProtocol;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.time.Duration;
import java.nio.file.attribute.PosixFileAttributeView;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CollectorPollingRuntimeTest {
    @Test
    void appliedSnapshotPollsOnlyTheFakeOutbox(@TempDir Path directory) throws Exception {
        writeDesired(directory, 1, "voltage");
        CapturingOutbox outbox = new CapturingOutbox();
        CollectorTelemetryWriter writer = new CollectorTelemetryWriter(outbox, Duration.ofSeconds(1));
        IotModbusRtuPollingProtocol fakeEngine = new IotModbusRtuPollingProtocol() {
            @Override
            public Map<String, Object> poll(IndustrialDeviceConfig config) {
                return Map.of("voltage", 220);
            }
        };
        LocalFilePollingConfigProvider provider = new LocalFilePollingConfigProvider(directory,
                "collector-site-1001-a");
        CollectorPollingRuntime runtime = new CollectorPollingRuntime(provider, observation -> { }, fakeEngine, writer);
        assertEquals(CollectorConfigStatus.APPLIED, runtime.start().status());
        assertEquals(1, runtime.pollOnce());
        assertEquals(1, outbox.batches);
        assertEquals("220", outbox.envelopes.get(0).value());
        runtime.close();
    }

    @Test
    void reconcileLoopAppliesNewDesiredVersionAndReschedulesOnlyAfterCommit(@TempDir Path directory)
            throws Exception {
        writeDesired(directory, 1, "voltage");
        CapturingOutbox outbox = new CapturingOutbox();
        CollectorTelemetryWriter writer = new CollectorTelemetryWriter(outbox, Duration.ofSeconds(1));
        IotModbusRtuPollingProtocol fakeEngine = new IotModbusRtuPollingProtocol() {
            @Override
            public Map<String, Object> poll(IndustrialDeviceConfig config) {
                return Map.of("voltage", 220);
            }
        };
        LocalFilePollingConfigProvider provider = new LocalFilePollingConfigProvider(directory,
                "collector-site-1001-a");
        CollectorPollingRuntime runtime = new CollectorPollingRuntime(provider, observation -> { }, fakeEngine,
                writer, Duration.ofSeconds(30));
        assertEquals(CollectorConfigStatus.APPLIED, runtime.start().status());
        assertEquals(1, runtime.appliedSnapshot().configVersion());

        writeDesired(directory, 2, "current");
        assertEquals(CollectorConfigStatus.APPLIED, runtime.reconcileNow().status());
        assertEquals(2, runtime.appliedSnapshot().configVersion());
        runtime.close();
    }

    private static void writeDesired(Path directory, long version, String propertyCode) throws Exception {
        Files.createDirectories(directory);
        if (Files.getFileAttributeView(directory, PosixFileAttributeView.class, LinkOption.NOFOLLOW_LINKS) != null) {
            Files.setAttribute(directory, "unix:mode", LocalFilePollingConfigProvider.LINUX_CONFIG_DIRECTORY_MODE,
                    LinkOption.NOFOLLOW_LINKS);
        }
        Path desired = directory.resolve("desired.json");
        Files.write(desired, CollectorConfigTestFixtures.canonical("collector-site-1001-a", version, propertyCode));
        if (Files.getFileAttributeView(desired, PosixFileAttributeView.class, LinkOption.NOFOLLOW_LINKS) != null) {
            Files.setAttribute(desired, "unix:mode", LocalFilePollingConfigProvider.LINUX_CONFIG_FILE_MODE,
                    LinkOption.NOFOLLOW_LINKS);
        }
    }

    private static final class CapturingOutbox implements TelemetryOutboxPort {
        private int batches;
        private List<TelemetryEnvelope> envelopes = List.of();

        @Override
        public AppendBatchResult appendBatch(TelemetryOutboxBatch batch, Duration enqueueTimeout) {
            batches++;
            this.envelopes = List.copyOf(batch.envelopes());
            return new AppendBatchResult.Success(batch.envelopes().stream()
                    .map(TelemetryEnvelope::messageId).toList(), List.of());
        }

        @Override
        public ClaimBatchResult claimBatch(int maxCount, Duration lease) {
            return new ClaimBatchResult.Empty();
        }

        @Override
        public List<TelemetryRoute> listUnfinishedRoutes() {
            return List.of();
        }

        @Override
        public void applyAck(AckCommand ack) {
        }
    }
}
