package com.basiclab.iot.sink.telemetry.store;

import com.basiclab.iot.sink.telemetry.inbox.InboxEnvelope;

/**
 * TD-003 §13 TelemetryStore 端口：standard（PG 月分区）/ full（TDengine）共用。
 * 幂等键：telemetry_sample_identity(tenant_id, message_id, content_sha256)。
 */
public interface TelemetryStorePort {

    /**
     * 写入一条遥测样本。
     *
     * @param envelope Inbox 载荷（含 canonical bytes + 结构化字段）
     * @return STORED / DUPLICATE / FAILED
     */
    WriteResult writeSample(InboxEnvelope envelope);
}
