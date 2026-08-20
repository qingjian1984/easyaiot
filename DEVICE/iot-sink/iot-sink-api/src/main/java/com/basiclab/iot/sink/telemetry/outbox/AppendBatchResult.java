package com.basiclab.iot.sink.telemetry.outbox;

import java.util.List;

/**
 * TD-002 §9 {@code appendBatch} 结果（sealed），包含整批碰撞诊断结果。
 *
 * <p>同 messageId 同 hash → DUPLICATE；同 messageId 不同 hash → COLLISION（整批回滚）；
 * 新写入 → STORED。空批次不提交。
 */
public sealed interface AppendBatchResult permits AppendBatchResult.Success, AppendBatchResult.Collision {

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

    /** 整批回滚后的碰撞结果；只报告按输入顺序首次检测到的 messageId。 */
    record Collision(List<String> collisionMessageIds) implements AppendBatchResult {
        public Collision {
            if (collisionMessageIds == null || collisionMessageIds.isEmpty()
                    || collisionMessageIds.stream().anyMatch(id -> id == null || id.isBlank())) {
                throw new IllegalArgumentException("collisionMessageIds must be non-empty and non-blank");
            }
            collisionMessageIds = List.copyOf(collisionMessageIds);
        }

        @Override
        public List<String> storedMessageIds() {
            return List.of();
        }

        @Override
        public List<String> duplicateMessageIds() {
            return List.of();
        }
    }
}
