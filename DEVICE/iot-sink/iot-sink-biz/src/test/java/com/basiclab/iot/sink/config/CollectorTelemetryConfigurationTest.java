package com.basiclab.iot.sink.config;

import com.basiclab.iot.sink.protocol.polling.CollectorTelemetryWriter;
import com.basiclab.iot.sink.telemetry.outbox.AppendBatchResult;
import com.basiclab.iot.sink.telemetry.outbox.TelemetryOutboxPort;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.Profile;

import java.lang.reflect.Method;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class CollectorTelemetryConfigurationTest {

    @Test
    void writerFactoryIsCollectorOnlyAndRequiresOutboxPort() throws Exception {
        Method factory = IotGatewayConfiguration.class.getMethod(
                "collectorTelemetryWriter", TelemetryOutboxPort.class);
        Profile profile = factory.getAnnotation(Profile.class);
        assertNotNull(profile);
        assertArrayEquals(new String[]{"collector"}, profile.value());
        assertEquals(TelemetryOutboxPort.class, factory.getParameterTypes()[0]);

        TelemetryOutboxPort outbox = new TelemetryOutboxPort() {
            @Override
            public AppendBatchResult appendBatch(
                    java.util.List<com.basiclab.iot.sink.telemetry.envelope.TelemetryEnvelope> envelopes,
                    java.time.Duration timeout) {
                return new AppendBatchResult.Success(
                        envelopes.stream().map(e -> e.messageId()).toList(), List.of());
            }
            @Override
            public com.basiclab.iot.sink.telemetry.outbox.ClaimBatchResult claimBatch(int maxCount, java.time.Duration lease) {
                return new com.basiclab.iot.sink.telemetry.outbox.ClaimBatchResult.Empty();
            }
            @Override
            public void applyAck(com.basiclab.iot.sink.telemetry.outbox.AckCommand ack) {
            }
        };
        CollectorTelemetryWriter writer = new IotGatewayConfiguration().collectorTelemetryWriter(outbox);
        assertNotNull(writer);
    }
}
