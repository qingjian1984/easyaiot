package com.basiclab.iot.sink.telemetry.outbox;

import java.util.List;

/**
 * TD-002 §11 claim 结果（sealed）。
 */
public sealed interface ClaimBatchResult permits ClaimBatchResult.Claimed, ClaimBatchResult.Empty {

    List<ClaimedEnvelope> envelopes();

    record Claimed(List<ClaimedEnvelope> claimed) implements ClaimBatchResult {
        @Override
        public List<ClaimedEnvelope> envelopes() {
            return claimed;
        }
    }

    record Empty() implements ClaimBatchResult {
        @Override
        public List<ClaimedEnvelope> envelopes() {
            return List.of();
        }
    }
}
