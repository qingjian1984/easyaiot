package com.basiclab.iot.device.alarm.infrastructure.event;

import java.util.Objects;

/**
 * Relay 从 Outbox 原子 claim 得到的不可变快照。
 *
 * <p>payload 只在内存中传给 transport；Relay 不把它拼进日志、错误码或错误
 * 摘要。数据库数值 ID 在边界上以十进制字符串保留，避免应用层 long 转换
 * 造成跨语言或超范围截断。</p>
 */
public final class AlarmOutboxClaimedEntry {

    private final long id;
    private final String eventId;
    private final String tenantId;
    private final String alarmId;
    private final String eventType;
    private final String eventVersion;
    private final String partitionKey;
    private final String payloadHash;
    private final String payloadJson;
    private final String headersJson;
    private final int retryCount;
    private final int maxRetries;

    public AlarmOutboxClaimedEntry(long id, String eventId, String tenantId,
                                   String alarmId, String eventType, String eventVersion,
                                   String partitionKey, String payloadHash,
                                   String payloadJson, String headersJson,
                                   int retryCount, int maxRetries) {
        this.id = id;
        this.eventId = required(eventId, "eventId");
        this.tenantId = positiveDecimal(tenantId, "tenantId");
        this.alarmId = positiveDecimal(alarmId, "alarmId");
        this.eventType = required(eventType, "eventType");
        this.eventVersion = required(eventVersion, "eventVersion");
        this.partitionKey = required(partitionKey, "partitionKey");
        this.payloadHash = required(payloadHash, "payloadHash");
        this.payloadJson = required(payloadJson, "payloadJson");
        this.headersJson = required(headersJson, "headersJson");
        if (retryCount < 0) {
            throw new IllegalArgumentException("ALARM_OUTBOX_ENTRY_INVALID: retryCount < 0");
        }
        if (maxRetries < 0 || retryCount > maxRetries) {
            throw new IllegalArgumentException(
                    "ALARM_OUTBOX_ENTRY_INVALID: retry budget is invalid");
        }
        this.retryCount = retryCount;
        this.maxRetries = maxRetries;
    }

    /** 便于 fake 合同使用的无数据库主键构造器。 */
    public AlarmOutboxClaimedEntry(String eventId, String tenantId, String alarmId,
                                   String eventType, String eventVersion,
                                   String partitionKey, String payloadHash,
                                   String payloadJson, String headersJson,
                                   int retryCount, int maxRetries) {
        this(0L, eventId, tenantId, alarmId, eventType, eventVersion, partitionKey,
                payloadHash, payloadJson, headersJson, retryCount, maxRetries);
    }

    public long id() {
        return id;
    }

    public String eventId() {
        return eventId;
    }

    public String tenantId() {
        return tenantId;
    }

    public String alarmId() {
        return alarmId;
    }

    public String eventType() {
        return eventType;
    }

    public String eventVersion() {
        return eventVersion;
    }

    public String partitionKey() {
        return partitionKey;
    }

    public String topicKey() {
        return partitionKey;
    }

    public String payloadHash() {
        return payloadHash;
    }

    public String payloadJson() {
        return payloadJson;
    }

    public String payload() {
        return payloadJson;
    }

    public String headersJson() {
        return headersJson;
    }

    public int retryCount() {
        return retryCount;
    }

    public int maxRetries() {
        return maxRetries;
    }

    /** fake 仓储更新 retry_count 时使用，不改变其余消息字节。 */
    public AlarmOutboxClaimedEntry withRetryCount(int newRetryCount) {
        return new AlarmOutboxClaimedEntry(id, eventId, tenantId, alarmId, eventType,
                eventVersion, partitionKey, payloadHash, payloadJson, headersJson,
                newRetryCount, maxRetries);
    }

    private static String required(String value, String field) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException("ALARM_OUTBOX_ENTRY_INVALID: " + field + " blank");
        }
        return value;
    }

    private static String positiveDecimal(String value, String field) {
        String required = required(value, field);
        if (!required.matches("^[1-9][0-9]*$")) {
            throw new IllegalArgumentException(
                    "ALARM_OUTBOX_ENTRY_INVALID: " + field + " must be positive decimal text");
        }
        return required;
    }
}
