package com.basiclab.iot.device.service.event;

import java.util.Objects;

/**
 * ADR-014 §验证 OUT-001～004：发布器认领到的 Outbox 条目（只读视图）。
 */
public final class ClaimedOutboxEntry {

    private final String eventId;
    private final long tenantId;
    private final String aggregateType;
    private final String aggregateId;
    private final String eventType;
    private final int schemaVersion;
    private final String payload;
    private final int retryCount;
    private final int maxRetries;

    public ClaimedOutboxEntry(String eventId, long tenantId, String aggregateType,
                              String aggregateId, String eventType, int schemaVersion,
                              String payload, int retryCount, int maxRetries) {
        this.eventId = Objects.requireNonNull(eventId, "eventId");
        this.tenantId = tenantId;
        this.aggregateType = Objects.requireNonNull(aggregateType, "aggregateType");
        this.aggregateId = Objects.requireNonNull(aggregateId, "aggregateId");
        this.eventType = Objects.requireNonNull(eventType, "eventType");
        this.schemaVersion = schemaVersion;
        this.payload = Objects.requireNonNull(payload, "payload");
        this.retryCount = retryCount;
        this.maxRetries = maxRetries;
    }

    /** Kafka key：{@code tenantId:aggregateType:aggregateId}。 */
    public String topicKey() {
        return tenantId + ":" + aggregateType + ":" + aggregateId;
    }

    public String eventId() {
        return eventId;
    }

    public long tenantId() {
        return tenantId;
    }

    public String aggregateType() {
        return aggregateType;
    }

    public String aggregateId() {
        return aggregateId;
    }

    public String eventType() {
        return eventType;
    }

    public int schemaVersion() {
        return schemaVersion;
    }

    public String payload() {
        return payload;
    }

    /** 已重试次数（不含本次即将进行的尝试）。 */
    public int retryCount() {
        return retryCount;
    }

    public int maxRetries() {
        return maxRetries;
    }
}
