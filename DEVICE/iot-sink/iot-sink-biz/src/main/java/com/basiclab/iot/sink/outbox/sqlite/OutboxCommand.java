package com.basiclab.iot.sink.outbox.sqlite;

import com.basiclab.iot.sink.telemetry.envelope.TelemetryEnvelope;
import com.basiclab.iot.sink.telemetry.outbox.AppendBatchResult;

import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * TD-002 §9 outbox 命令（sealed）。P1-T1 只实现 {@link AppendBatch}；
 * 后续 T-4/T-5 扩展 Ack/Claim/Cleanup/Gap。
 */
sealed interface OutboxCommand permits OutboxCommand.AppendBatch {

    /** 一次轮询的原子批次（TD-002 §9 appendBatch）。 */
    record AppendBatch(
            List<TelemetryEnvelope> envelopes,
            CompletableFuture<AppendBatchResult> future
    ) implements OutboxCommand {
    }
}
