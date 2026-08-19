package com.basiclab.iot.sink.telemetry.store;

import com.basiclab.iot.sink.telemetry.inbox.InboxEnvelope;

import java.util.List;

/**
 * TD-003 §13 TelemetryStore 端口：standard（PG 月分区）/ full（TDengine）共用。
 * 幂等键：telemetry_sample_identity(tenant_id, message_id, content_sha256)。
 */
public interface TelemetryStorePort {

    /** TD-003 §13 primary batch contract. */
    WriteBatchResult appendBatch(List<TelemetrySample> samples);

    /**
     * Legacy one-item bridge retained for one compatibility cycle.
     *
     * @param envelope Inbox 载荷（含 canonical bytes + 结构化字段）
     * @return STORED / DUPLICATE / FAILED
     */
    @Deprecated
    default WriteResult writeSample(InboxEnvelope envelope) {
        if (envelope == null) {
            return WriteResult.FAILED;
        }
        try {
            WriteBatchResult result = appendBatch(List.of(TelemetrySample.fromInboxEnvelope(envelope)));
            if (result == null || result.items().size() != 1) {
                return WriteResult.FAILED;
            }
            WriteItemResult item = result.items().get(0);
            if (!envelope.messageId().equals(item.messageId())) {
                return WriteResult.FAILED;
            }
            return switch (item.status()) {
                case STORED -> WriteResult.STORED;
                case DUPLICATE -> WriteResult.DUPLICATE;
                case RETRYABLE_FAILED, FINAL_FAILED -> WriteResult.FAILED;
            };
        } catch (RuntimeException error) {
            return WriteResult.FAILED;
        }
    }
}
