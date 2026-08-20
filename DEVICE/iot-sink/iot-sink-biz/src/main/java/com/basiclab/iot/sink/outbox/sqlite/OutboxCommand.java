package com.basiclab.iot.sink.outbox.sqlite;

import com.basiclab.iot.sink.telemetry.outbox.AckCommand;
import com.basiclab.iot.sink.telemetry.outbox.AppendBatchResult;
import com.basiclab.iot.sink.telemetry.outbox.ClaimBatchResult;
import com.basiclab.iot.sink.telemetry.outbox.TelemetryOutboxBatch;

import java.util.concurrent.CompletableFuture;

/**
 * TD-002 §9 outbox 命令（sealed）。P1-T4 扩展 Claim/ApplyAck/ReclaimExpiredLeases；P1-T5 扩展 CleanupAcked/Checkpoint。
 */
public sealed interface OutboxCommand permits OutboxCommand.AppendBatch, OutboxCommand.Claim,
        OutboxCommand.ApplyAck, OutboxCommand.ReclaimExpiredLeases,
        OutboxCommand.CleanupAcked, OutboxCommand.Checkpoint {

    record AppendBatch(
            TelemetryOutboxBatch batch,
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

    record CleanupAcked(
            long keepBeforeMs,
            int batchSize
    ) implements OutboxCommand {
    }

    record Checkpoint() implements OutboxCommand {
    }
}
