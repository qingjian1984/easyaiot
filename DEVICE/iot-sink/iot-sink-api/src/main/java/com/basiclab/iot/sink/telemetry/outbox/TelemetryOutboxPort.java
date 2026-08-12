package com.basiclab.iot.sink.telemetry.outbox;

import com.basiclab.iot.sink.telemetry.envelope.TelemetryEnvelope;

import java.time.Duration;
import java.util.List;

/**
 * TD-001 §9.2 Poller 解耦端口 + TD-002 §9 outbox 接口契约。
 *
 * <p>Poller 一次轮询结果作为一个本地原子批次调用 {@link #appendBatch}；
 * 只有 SQLite 事务持久提交后才返回；只等待本地排队和提交，不等 MQTT/应用 ACK。
 */
public interface TelemetryOutboxPort {

    /**
     * 追加一批遥测 envelope 到本地 outbox。
     *
     * @param envelopes     一批 envelope（一次轮询结果）
     * @param enqueueTimeout 入队等待超时（队列满时背压）
     * @return 批次结果（STORED/DUPLICATE/COLLISION 汇总）
     * @throws OutboxBackpressureException 队列满且超时
     * @throws OutboxUnavailableException  存储不可用（损坏/只读/磁盘故障）
     */
    AppendBatchResult appendBatch(List<TelemetryEnvelope> envelopes, Duration enqueueTimeout);
}
