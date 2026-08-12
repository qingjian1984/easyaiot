package com.basiclab.iot.sink.telemetry.outbox;

import java.util.List;

/**
 * TD-002 §9 {@code appendBatch} 结果（sealed）。
 *
 * <p>同 messageId 同 hash → DUPLICATE；同 messageId 不同 hash → COLLISION（整批回滚）；
 * 新写入 → STORED。空批次不提交。
 */
public sealed interface AppendBatchResult permits AppendBatchResult.Success {

    List<String> storedMessageIds();

    List<String> duplicateMessageIds();

    List<String> collisionMessageIds();

    /** 成功批次（含 STORED + DUPLICATE 汇总；COLLISION 时整批回滚，不产生此结果）。 */
    record Success(
            List<String> stored,
            List<String> duplicates
    ) implements AppendBatchResult {
        @Override
        public List<String> storedMessageIds() {
            return stored;
        }

        @Override
        public List<String> duplicateMessageIds() {
            return duplicates;
        }

        @Override
        public List<String> collisionMessageIds() {
            return List.of();
        }
    }
}
