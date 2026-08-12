package com.basiclab.iot.sink.protocol.polling;

import com.basiclab.iot.sink.dal.dataobject.DeviceDO;
import com.basiclab.iot.sink.telemetry.envelope.TelemetryEnvelope;
import com.basiclab.iot.sink.telemetry.outbox.AppendBatchResult;
import com.basiclab.iot.sink.telemetry.outbox.TelemetryOutboxPort;
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

        writer.store(DeviceDO.builder().tenantId(9L).deviceIdentification("meter-9").build(),
                config, Map.of("active-power", 12.5D), "MODBUS_RTU");

        assertEquals(Duration.ofSeconds(3), outbox.timeout);
        assertEquals(1, outbox.envelopes.size());
        assertEquals("12.5", outbox.envelopes.get(0).value());
    }

    private static final class CapturingOutbox implements TelemetryOutboxPort {
        private List<TelemetryEnvelope> envelopes;
        private Duration timeout;

        @Override
        public AppendBatchResult appendBatch(List<TelemetryEnvelope> envelopes, Duration enqueueTimeout) {
            this.envelopes = List.copyOf(envelopes);
            this.timeout = enqueueTimeout;
            return new AppendBatchResult.Success(
                    envelopes.stream().map(TelemetryEnvelope::messageId).toList(), List.of());
        }
    }
}
