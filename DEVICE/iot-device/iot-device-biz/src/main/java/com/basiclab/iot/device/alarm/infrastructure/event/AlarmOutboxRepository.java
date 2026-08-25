package com.basiclab.iot.device.alarm.infrastructure.event;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

/**
 * 告警 Outbox 持久化端口。
 *
 * <p>数据库实现必须把到期 {@code PENDING} 和租约过期的
 * {@code PUBLISHING} 行在一个原子 claim 中置为 {@code PUBLISHING}，并返回
 * 本次 claim 的只读快照。Relay 不自行判断状态，也不直接访问数据库。</p>
 *
 * <p>所有状态回写都带 lease owner。这样旧副本在租约过期、被另一副本重新
 * claim 后，不能把新副本的结果误写成 {@code PUBLISHED} 或覆盖其退避。</p>
 */
public interface AlarmOutboxRepository {

    /**
     * 原子认领到期消息，并设置 owner/leaseUntil。
     *
     * @param now 当前时间
     * @param leaseOwner 本次 relay 实例的稳定 owner
     * @param leaseDuration 租约时长
     * @param batchSize 本轮最多认领数量
     * @return 已由本 owner 认领的条目；不得返回 null
     */
    List<AlarmOutboxClaimedEntry> claimDue(Instant now, String leaseOwner,
                                           Duration leaseDuration, int batchSize);

    /** broker 已确认后置为 PUBLISHED，并清理租约。 */
    void markPublished(String eventId, String leaseOwner, Instant publishedAt);

    /** 可重试失败置回 PENDING，写入下次时间并清理租约。 */
    void markRetry(String eventId, String leaseOwner, int retryCount,
                   Instant failedAt, Instant nextAttemptAt,
                   String errorCode, String errorSummary);

    /** final 失败或重试超限置为 DEAD_LETTER，并清理租约。 */
    void markDeadLetter(String eventId, String leaseOwner, Instant deadLetteredAt,
                       String errorCode, String errorSummary);
}
