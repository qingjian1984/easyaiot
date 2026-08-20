package com.basiclab.iot.sink.protocol.polling;

import com.basiclab.iot.sink.dal.dataobject.DeviceDO;
import com.basiclab.iot.sink.polling.CollectorConfigSnapshot;
import com.basiclab.iot.sink.polling.CollectorDevice;
import com.basiclab.iot.sink.telemetry.envelope.TelemetryEnvelope;
import com.basiclab.iot.sink.telemetry.outbox.AppendBatchResult;
import com.basiclab.iot.sink.telemetry.outbox.TelemetryOutboxBatch;
import com.basiclab.iot.sink.telemetry.outbox.TelemetryOutboxPort;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** collector Poller 到可靠 SQLite Outbox 的唯一写入口。 */
public final class CollectorTelemetryWriter {

    private final TelemetryOutboxPort outbox;
    private final Duration enqueueTimeout;

    public CollectorTelemetryWriter(TelemetryOutboxPort outbox, Duration enqueueTimeout) {
        this.outbox = Objects.requireNonNull(outbox, "outbox");
        this.enqueueTimeout = Objects.requireNonNull(enqueueTimeout, "enqueueTimeout");
        if (enqueueTimeout.isZero() || enqueueTimeout.isNegative()) {
            throw new IllegalArgumentException("enqueueTimeout must be positive");
        }
    }

    public AppendBatchResult store(DeviceDO device, IndustrialDeviceConfig config,
                                   Map<String, Object> values, String protocolType) {
        List<TelemetryEnvelope> envelopes = PollingResultMapper.toEnvelopes(
                device, config, values, protocolType);
        if (envelopes.isEmpty()) {
            return new AppendBatchResult.Success(List.of(), List.of());
        }
        return outbox.appendBatch(new TelemetryOutboxBatch(device.getProductIdentification(), envelopes),
                enqueueTimeout);
    }

    /** Collector Profile overload: only the applied local snapshot supplies identity and priority. */
    public AppendBatchResult store(CollectorConfigSnapshot snapshot, CollectorDevice device,
                                   Map<String, Object> values, String protocolType) {
        List<TelemetryEnvelope> envelopes = PollingResultMapper.toEnvelopes(snapshot, device, values, protocolType);
        if (envelopes.isEmpty()) {
            return new AppendBatchResult.Success(List.of(), List.of());
        }
        return outbox.appendBatch(new TelemetryOutboxBatch(snapshot.productIdentification(), envelopes),
                enqueueTimeout);
    }
}
