package com.basiclab.iot.device.service.event;

import com.basiclab.iot.device.event.PowerModelEventEnvelope;

import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * TD-005 migration §4.6 / ADR-014：待投递 Outbox 条目（值对象）。
 * 对应 {@code power_model_release_outbox} 的 PENDING 行；与业务事实、领域审计同事务提交。
 * 不变量：eventId/auditEventId UUID、eventType 主版本后缀=schemaVersion、
 * payload_hash 格式、序列化正文 ≤ 2MiB（与 DDL ck_power_model_release_outbox_payload_bound 对齐）。
 * Java 8 兼容。
 */
public final class OutboxEntry {

    private static final Pattern UUID_PATTERN =
            Pattern.compile("^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$");

    private final long id;
    private final String eventId;
    private final long tenantId;
    private final String auditEventId;
    private final String aggregateType;
    private final String aggregateId;
    private final String eventType;
    private final int schemaVersion;
    private final String payload;
    private final String payloadHash;
    private final int maxRetries;

    private OutboxEntry(long id, String eventId, long tenantId, String auditEventId,
                        String aggregateType, String aggregateId, String eventType,
                        int schemaVersion, String payload, String payloadHash, int maxRetries) {
        this.id = id;
        this.eventId = eventId;
        this.tenantId = tenantId;
        this.auditEventId = auditEventId;
        this.aggregateType = aggregateType;
        this.aggregateId = aggregateId;
        this.eventType = eventType;
        this.schemaVersion = schemaVersion;
        this.payload = payload;
        this.payloadHash = payloadHash;
        this.maxRetries = maxRetries;
    }

    /** 构造并校验不变量；违规抛出 IllegalArgumentException（MODEL_EVENT_* 稳定码前缀）。 */
    public static OutboxEntry of(long id, String eventId, long tenantId, String auditEventId,
                                 String aggregateType, String aggregateId, String eventType,
                                 int schemaVersion, String payload, int maxRetries) {
        requireUuid(eventId, "eventId");
        requireUuid(auditEventId, "auditEventId");
        requireNonBlank(aggregateType, "aggregateType");
        requireNonBlank(aggregateId, "aggregateId");
        requireNonBlank(eventType, "eventType");
        if (maxRetries < 1) {
            throw new IllegalArgumentException(
                    "MODEL_EVENT_RETRY_POLICY_INVALID: maxRetries 必须 >= 1");
        }
        int suffix = PowerModelEventEnvelope.majorVersionOf(eventType);
        if (suffix < 0 || suffix != schemaVersion) {
            throw new IllegalArgumentException(
                    "MODEL_EVENT_VERSION_SUFFIX_MISMATCH: eventType 后缀与 schemaVersion 不一致");
        }
        requireNonBlank(payload, "payload");
        long payloadBytes = payload.getBytes(StandardCharsets.UTF_8).length;
        if (payloadBytes > PowerModelEventEnvelope.MAX_PAYLOAD_BYTES) {
            throw new IllegalArgumentException(
                    "MODEL_EVENT_PAYLOAD_TOO_LARGE: 序列化正文 " + payloadBytes
                            + " 字节超过上限 " + PowerModelEventEnvelope.MAX_PAYLOAD_BYTES);
        }
        return new OutboxEntry(id, eventId, tenantId, auditEventId, aggregateType, aggregateId,
                eventType, schemaVersion, payload,
                PowerModelEventEnvelope.payloadHash(payload), maxRetries);
    }

    private static void requireUuid(String value, String field) {
        if (value == null || !UUID_PATTERN.matcher(value).matches()) {
            throw new IllegalArgumentException(
                    "MODEL_EVENT_ENVELOPE_INVALID: " + field + " 必须为 UUID 小写格式");
        }
    }

    private static void requireNonBlank(String value, String field) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException(
                    "MODEL_EVENT_ENVELOPE_INVALID: " + field + " 不得为空");
        }
    }

    /** Kafka key：{@code tenantId:aggregateType:aggregateId}。 */
    public String topicKey() {
        return tenantId + ":" + aggregateType + ":" + aggregateId;
    }

    public long id() {
        return id;
    }

    public String eventId() {
        return eventId;
    }

    public long tenantId() {
        return tenantId;
    }

    public String auditEventId() {
        return auditEventId;
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

    public String payloadHash() {
        return payloadHash;
    }

    public int maxRetries() {
        return maxRetries;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof OutboxEntry)) {
            return false;
        }
        OutboxEntry other = (OutboxEntry) o;
        return id == other.id && tenantId == other.tenantId && schemaVersion == other.schemaVersion
                && maxRetries == other.maxRetries
                && eventId.equals(other.eventId) && auditEventId.equals(other.auditEventId)
                && aggregateType.equals(other.aggregateType) && aggregateId.equals(other.aggregateId)
                && eventType.equals(other.eventType) && payload.equals(other.payload)
                && payloadHash.equals(other.payloadHash);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, eventId, tenantId, auditEventId, aggregateType, aggregateId,
                eventType, schemaVersion, payload, payloadHash, maxRetries);
    }
}
