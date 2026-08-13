package com.basiclab.iot.sink.outbox.sqlite;

import com.basiclab.iot.sink.telemetry.envelope.TelemetryEnvelope;
import com.basiclab.iot.sink.telemetry.outbox.AckCommand;
import com.basiclab.iot.sink.telemetry.outbox.AppendBatchResult;
import com.basiclab.iot.sink.telemetry.outbox.ClaimBatchResult;

import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * TD-002 §9 outbox 命令（sealed）。P1-T4 扩展 Claim/ApplyAck/ReclaimExpiredLeases。
 */
sealed interface OutboxCommand permits OutboxCommand.AppendBatch, OutboxCommand.Claim,
        OutboxCommand.ApplyAck, OutboxCommand.ReclaimExpiredLeases {

    record AppendBatch(
            List<TelemetryEnvelope> envelopes,
            CompletableFuture<AppendBatchResult> future
    ) implements OutboxCommand {
    }

    record Claim(
            int maxCount,
            long leaseMs,
            CompletableFuture<ClaimBatchResult> future
    ) implements OutboxCommand {
    }

    record ApplyAck(
            AckCommand ack
    ) implements OutboxCommand {
    }

    record ReclaimExpiredLeases(
            long nowMs,
            long backoffBaseMs,
            long backoffCapMs
    ) implements OutboxCommand {
    }
}
