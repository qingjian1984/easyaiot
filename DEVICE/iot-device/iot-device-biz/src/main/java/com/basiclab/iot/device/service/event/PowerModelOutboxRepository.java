package com.basiclab.iot.device.service.event;

import java.util.List;

/**
 * ADR-014：Outbox 持久化端口（仓储模式）。
 * 实现负责原子语义：claim 必须为单语句原子认领（UPDATE ... FOR UPDATE SKIP LOCKED
 * 或等价），并发副本不得认领到同一条目；首插争抢由 UNIQUE(event_id) 承担。
 */
public interface PowerModelOutboxRepository {

    /**
     * 业务事务内插入 PENDING 条目（与业务事实、领域审计同事务提交）。
     * 调用方必须在活动事务中调用（服务层以 MANDATORY 传播强制）。
     */
    void insertPending(OutboxEntry entry);

    /**
     * 原子认领到期条目并置为 PUBLISHING（租约 {@code leaseOwner/leaseUntil}）。
     * 认领规则见 {@link OutboxRelayPolicy#claimable}：PENDING 且 nextAttemptAt<=now，
     * 或 PUBLISHING 且租约缺失/已过期（崩溃恢复）。
     *
     * @return 被本副本认领的条目（不超过 batchSize）
     */
    List<ClaimedOutboxEntry> claimDue(java.time.Instant now, String leaseOwner,
                                      java.time.Duration leaseDuration, int batchSize);

    /** 发送成功回写：status=PUBLISHED、published_at、清空租约。 */
    void markPublished(String eventId, java.time.Instant publishedAt);

    /** 可重试失败回写：retry_count+1、next_attempt_at（退避）、脱敏错误摘要、清空租约。 */
    void markRetry(String eventId, int retryCount, java.time.Instant nextAttemptAt,
                   String errorCode, String errorDigest);

    /** final 失败或重试超限回写：status=DEAD_LETTER、脱敏错误摘要、清空租约。 */
    void markDeadLetter(String eventId, String errorCode, String errorDigest);
}
