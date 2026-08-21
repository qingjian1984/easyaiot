package com.basiclab.iot.sink.protocol.polling;

import com.basiclab.iot.sink.dal.dataobject.DeviceDO;
import com.basiclab.iot.sink.polling.CollectorConfigSnapshot;
import com.basiclab.iot.sink.polling.CollectorDevice;
import com.basiclab.iot.sink.polling.CollectorPoint;
import com.basiclab.iot.sink.polling.CollectorSerialBus;
import com.basiclab.iot.sink.telemetry.envelope.TelemetryEnvelope;
import com.basiclab.iot.sink.telemetry.outbox.AppendBatchResult;
import com.basiclab.iot.sink.telemetry.outbox.TelemetryOutboxBatch;
import com.basiclab.iot.sink.telemetry.outbox.TelemetryOutboxPort;
import com.basiclab.iot.sink.telemetry.outbox.TelemetryRoute;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CollectorTelemetryWriterTest {

    @Test
    void writesOnePollAsSingleOutboxBatch() {
        CapturingOutbox outbox = new CapturingOutbox();
        CollectorTelemetryWriter writer = new CollectorTelemetryWriter(outbox, Duration.ofSeconds(3));
        IndustrialDeviceConfig config = new IndustrialDeviceConfig();
        config.setSiteCode("site-a");
        config.setConfigVersion(2L);
        IndustrialDeviceConfig.Point point = new IndustrialDeviceConfig.Point();
        point.setPropertyCode("active-power");
        point.setDataPriority("METERING_TOTAL");
        config.setPoints(List.of(point));

        writer.store(DeviceDO.builder().tenantId(9L).deviceIdentification("meter-9")
                        .productIdentification("power-meter")
                        .build(),
                config, Map.of("active-power", 12.5D), "MODBUS_RTU");

        assertEquals(Duration.ofSeconds(3), outbox.timeout);
        assertEquals("power-meter", outbox.batch.productIdentification());
        assertEquals(1, outbox.envelopes.size());
        assertEquals("12.5", outbox.envelopes.get(0).value());
    }

    @Test
    void writesAppliedCollectorSnapshotProductIdentity() {
        CollectorPoint point = new CollectorPoint("active-power", "HOLDING_REGISTER", 0, 1,
                "UINT16", "BIG_ENDIAN", "BIG_ENDIAN", "1", "0", "METERING_TOTAL", false, "default");
        CollectorDevice device = new CollectorDevice("device-collector", "meter-collector", 1,
                1_000L, 5_000L, 1, List.of(point));
        CollectorConfigSnapshot snapshot = new CollectorConfigSnapshot("1.1", "collector-product",
                "workload-a", "tenant-a", "site-a", "site-code", 7L,
                "2026-08-20T00:00:00Z",
                List.of(new CollectorSerialBus("bus-1", "COM1", 9_600, 8, "1", "NONE", 0,
                        true, List.of(device))), null);
        CapturingOutbox outbox = new CapturingOutbox();
        CollectorTelemetryWriter writer = new CollectorTelemetryWriter(outbox, Duration.ofSeconds(3));

        writer.store(snapshot, device, Map.of("active-power", 12.5D), "MODBUS_RTU");

        assertEquals("collector-product", outbox.batch.productIdentification());
        assertEquals("meter-collector", outbox.envelopes.get(0).deviceIdentification());
    }

    private static final class CapturingOutbox implements TelemetryOutboxPort {
        private TelemetryOutboxBatch batch;
        private List<TelemetryEnvelope> envelopes;
        private Duration timeout;

        @Override
        public AppendBatchResult appendBatch(TelemetryOutboxBatch batch, Duration enqueueTimeout) {
            this.batch = batch;
            this.envelopes = batch.envelopes();
            this.timeout = enqueueTimeout;
            return new AppendBatchResult.Success(
                    envelopes.stream().map(TelemetryEnvelope::messageId).toList(), List.of());
        }

        @Override
        public com.basiclab.iot.sink.telemetry.outbox.ClaimBatchResult claimBatch(int maxCount, Duration lease) {
            return new com.basiclab.iot.sink.telemetry.outbox.ClaimBatchResult.Empty();
        }

        @Override
        public List<TelemetryRoute> listUnfinishedRoutes() {
            return List.of();
        }

        @Override
        public void applyAck(com.basiclab.iot.sink.telemetry.outbox.AckCommand ack) {
        }
    }
}
