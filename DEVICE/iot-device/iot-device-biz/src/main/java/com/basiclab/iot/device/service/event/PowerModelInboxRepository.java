package com.basiclab.iot.device.service.event;

import java.time.Instant;

/**
 * ADR-014 §决策 MUST：消费者 Inbox 持久化端口（仓储模式）。
 * event_id 全局唯一由 UNIQUE(event_id) 承担；首插争抢失败方读取既有记录后
 * 交由 {@link InboxArbiter} 裁决。
 */
public interface PowerModelInboxRepository {

    /** 按 eventId 读取既有记录；无记录返回 null。 */
    InboxArbiter.RecordView findByEventId(String eventId);

    /**
     * 首插 RECEIVED 记录（争抢由数据库唯一约束裁决）。
     *
     * @return true=本副本插入成功；false=已被他副本抢先（调用方应重读后裁决）
     */
    boolean insertReceived(String eventId, long tenantId, String eventType, String payloadHash);

    /** 处理完成回写：status=PROCESSED、processed_at。 */
    void markProcessed(String eventId, Instant processedAt);

    /**
     * 隔离回写（upsert）：既有记录置 QUARANTINED（同 ID 异 hash），
     * 无记录则直接插入 QUARANTINED 行（未知主版本）；均写脱敏错误摘要。
     */
    void upsertQuarantined(String eventId, long tenantId, String eventType,
                           String payloadHash, String errorCode, String errorDigest);
}
