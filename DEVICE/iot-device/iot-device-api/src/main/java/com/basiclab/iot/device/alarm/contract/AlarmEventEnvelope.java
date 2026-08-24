package com.basiclab.iot.device.alarm.contract;

import java.lang.reflect.Array;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * 告警领域事件 Envelope 的共享编译合同。
 *
 * <p>本类型只负责 Envelope 级不变量，不负责具体事件 payload Schema 的大小
 * 和字段校验。它没有 Spring、数据库、系统时钟或随机数依赖；时间和 payload
 * 均由调用方显式提供。</p>
 */
public final class AlarmEventEnvelope {

    /** 当前冻结的 Envelope 版本。 */
    public static final String EVENT_VERSION = "1.0";
    /** 告警领域事件的固定生产者身份。 */
    public static final String SOURCE = "iot-device";

    /** Envelope 不变量错误的稳定前缀。 */
    public static final String ERROR_ENVELOPE_INVALID = "ALARM_EVENT_ENVELOPE_INVALID";
    public static final String ERROR_VERSION_INVALID = "ALARM_EVENT_VERSION_INVALID";
    public static final String ERROR_EVENT_TYPE_UNKNOWN = "ALARM_EVENT_TYPE_UNKNOWN";
    public static final String ERROR_TENANT_INVALID = "ALARM_EVENT_TENANT_INVALID";
    public static final String ERROR_TIME_INVALID = "ALARM_EVENT_TIME_INVALID";
    public static final String ERROR_SOURCE_INVALID = "ALARM_EVENT_SOURCE_INVALID";
    public static final String ERROR_CORRELATION_INVALID = "ALARM_EVENT_CORRELATION_INVALID";
    public static final String ERROR_PAYLOAD_INVALID = "ALARM_EVENT_PAYLOAD_INVALID";

    private static final Pattern UUID_PATTERN = Pattern.compile(
            "^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$");
    private static final Pattern TENANT_ID_PATTERN = Pattern.compile("^[1-9][0-9]*$");

    private final String eventId;
    private final String eventVersion;
    private final String eventType;
    private final String tenantId;
    private final OffsetDateTime occurredAt;
    private final OffsetDateTime recordedAt;
    private final String source;
    private final String correlationId;
    private final String traceId;
    private final Map<String, Object> payload;

    private AlarmEventEnvelope(String eventId, String eventVersion, String eventType,
                               String tenantId, OffsetDateTime occurredAt,
                               OffsetDateTime recordedAt, String source,
                               String correlationId, String traceId,
                               Map<String, ?> payload) {
        this.eventId = eventId;
        this.eventVersion = eventVersion;
        this.eventType = eventType;
        this.tenantId = tenantId;
        this.occurredAt = occurredAt;
        this.recordedAt = recordedAt;
        this.source = source;
        this.correlationId = correlationId;
        this.traceId = traceId;
        this.payload = immutablePayload(payload);
    }

    /**
     * 按冻结字段顺序构造 Envelope。
     *
     * @param eventId 全局唯一、规范小写 UUID 文本
     * @param eventVersion 当前为 {@code 1.0}
     * @param eventType 冻结的完整事件名称
     * @param tenantId 正十进制租户 ID 文本（不接受 JSON number 语义）
     * @param occurredAt 来源发生时间，必须显式带 offset
     * @param recordedAt 平台记录时间，必须显式带 offset
     * @param source 固定为 {@code iot-device}
     * @param correlationId 非空业务追踪标识
     * @param traceId 可选链路追踪标识
     * @param payload 事件 payload；构造后会深度防御复制
     */
    public static AlarmEventEnvelope of(String eventId, String eventVersion,
                                        String eventType, String tenantId,
                                        OffsetDateTime occurredAt,
                                        OffsetDateTime recordedAt, String source,
                                        String correlationId, String traceId,
                                        Map<String, ?> payload) {
        validate(eventId, eventVersion, eventType, tenantId, occurredAt, recordedAt,
                source, correlationId, traceId, payload);
        return new AlarmEventEnvelope(eventId, eventVersion, eventType, tenantId,
                occurredAt, recordedAt, source, correlationId, traceId, payload);
    }

    /** 使用枚举事件类型的构造重载。 */
    public static AlarmEventEnvelope of(String eventId, String eventVersion,
                                        AlarmEventType eventType, String tenantId,
                                        OffsetDateTime occurredAt,
                                        OffsetDateTime recordedAt, String source,
                                        String correlationId, String traceId,
                                        Map<String, ?> payload) {
        if (eventType == null) {
            throw invalid(ERROR_EVENT_TYPE_UNKNOWN, "eventType 不得为空");
        }
        return of(eventId, eventVersion, eventType.value(), tenantId, occurredAt,
                recordedAt, source, correlationId, traceId, payload);
    }

    /** 使用固定版本的简化构造重载。 */
    public static AlarmEventEnvelope of(String eventId, AlarmEventType eventType,
                                        String tenantId, OffsetDateTime occurredAt,
                                        OffsetDateTime recordedAt, String source,
                                        String correlationId, String traceId,
                                        Map<String, ?> payload) {
        return of(eventId, EVENT_VERSION, eventType, tenantId, occurredAt, recordedAt,
                source, correlationId, traceId, payload);
    }

    /** 使用字符串时间的构造重载；时间必须是 ISO-8601 offset 文本。 */
    public static AlarmEventEnvelope of(String eventId, String eventVersion,
                                        String eventType, String tenantId,
                                        String occurredAt, String recordedAt,
                                        String source, String correlationId,
                                        String traceId, Map<String, ?> payload) {
        return of(eventId, eventVersion, eventType, tenantId,
                parseOffsetDateTime(occurredAt, "occurredAt"),
                parseOffsetDateTime(recordedAt, "recordedAt"), source, correlationId,
                traceId, payload);
    }

    /** 使用枚举事件类型和字符串时间的构造重载。 */
    public static AlarmEventEnvelope of(String eventId, String eventVersion,
                                        AlarmEventType eventType, String tenantId,
                                        String occurredAt, String recordedAt,
                                        String source, String correlationId,
                                        String traceId, Map<String, ?> payload) {
        return of(eventId, eventVersion, eventType, tenantId,
                parseOffsetDateTime(occurredAt, "occurredAt"),
                parseOffsetDateTime(recordedAt, "recordedAt"), source, correlationId,
                traceId, payload);
    }

    private static void validate(String eventId, String eventVersion, String eventType,
                                 String tenantId, OffsetDateTime occurredAt,
                                 OffsetDateTime recordedAt, String source,
                                 String correlationId, String traceId,
                                 Map<String, ?> payload) {
        if (isBlank(eventId) || !UUID_PATTERN.matcher(eventId).matches()) {
            throw invalid(ERROR_ENVELOPE_INVALID, "eventId 必须为规范小写 UUID");
        }
        // UUID.fromString provides a second canonicality guard without changing the
        // public text representation used by the event contract.
        try {
            UUID.fromString(eventId);
        } catch (IllegalArgumentException ex) {
            throw invalid(ERROR_ENVELOPE_INVALID, "eventId 必须为规范小写 UUID");
        }
        if (!EVENT_VERSION.equals(eventVersion)) {
            throw invalid(ERROR_VERSION_INVALID, "eventVersion 必须为 1.0");
        }
        if (isBlank(eventType) || !AlarmEventType.isKnown(eventType)) {
            throw invalid(ERROR_EVENT_TYPE_UNKNOWN, "eventType 不是冻结的告警事件类型");
        }
        if (isBlank(tenantId) || !TENANT_ID_PATTERN.matcher(tenantId).matches()) {
            throw invalid(ERROR_TENANT_INVALID, "tenantId 必须为正十进制字符串");
        }
        if (occurredAt == null || recordedAt == null) {
            throw invalid(ERROR_TIME_INVALID, "occurredAt/recordedAt 必须带 offset");
        }
        if (!SOURCE.equals(source)) {
            throw invalid(ERROR_SOURCE_INVALID, "source 必须为 iot-device");
        }
        if (isBlank(correlationId) || correlationId.length() > 128) {
            throw invalid(ERROR_CORRELATION_INVALID,
                    "correlationId 必须为 1-128 个非空字符");
        }
        if (traceId != null && traceId.length() > 128) {
            throw invalid(ERROR_CORRELATION_INVALID, "traceId 长度不得超过 128");
        }
        if (payload == null) {
            throw invalid(ERROR_PAYLOAD_INVALID, "payload 不得为空");
        }
        for (String key : payload.keySet()) {
            if (key == null) {
                throw invalid(ERROR_PAYLOAD_INVALID, "payload key 不得为空");
            }
        }
    }

    private static OffsetDateTime parseOffsetDateTime(String value, String field) {
        if (isBlank(value)) {
            throw invalid(ERROR_TIME_INVALID, field + " 必须为 ISO-8601 offset 时间");
        }
        try {
            return OffsetDateTime.parse(value);
        } catch (DateTimeParseException ex) {
            throw invalid(ERROR_TIME_INVALID,
                    field + " 必须为带 offset 的 ISO-8601 时间");
        }
    }

    private static boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    private static IllegalArgumentException invalid(String code, String detail) {
        return new IllegalArgumentException(code + ": " + detail);
    }

    private static Map<String, Object> immutablePayload(Map<String, ?> source) {
        Map<String, Object> copy = new LinkedHashMap<>();
        for (Map.Entry<String, ?> entry : source.entrySet()) {
            copy.put(entry.getKey(), immutableValue(entry.getValue()));
        }
        return Collections.unmodifiableMap(copy);
    }

    @SuppressWarnings("unchecked")
    private static Object immutableValue(Object value) {
        if (value instanceof Map<?, ?> map) {
            Map<Object, Object> copy = new LinkedHashMap<>();
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                copy.put(entry.getKey(), immutableValue(entry.getValue()));
            }
            return Collections.unmodifiableMap(copy);
        }
        if (value instanceof List<?> list) {
            List<Object> copy = new ArrayList<>(list.size());
            for (Object item : list) {
                copy.add(immutableValue(item));
            }
            return Collections.unmodifiableList(copy);
        }
        if (value instanceof Set<?> set) {
            List<Object> copy = new ArrayList<>(set.size());
            for (Object item : set) {
                copy.add(immutableValue(item));
            }
            return Collections.unmodifiableList(copy);
        }
        if (value instanceof Collection<?> collection) {
            List<Object> copy = new ArrayList<>(collection.size());
            for (Object item : collection) {
                copy.add(immutableValue(item));
            }
            return Collections.unmodifiableList(copy);
        }
        if (value != null && value.getClass().isArray()) {
            int length = Array.getLength(value);
            List<Object> copy = new ArrayList<>(length);
            for (int i = 0; i < length; i++) {
                copy.add(immutableValue(Array.get(value, i)));
            }
            return Collections.unmodifiableList(copy);
        }
        return value;
    }

    public String eventId() {
        return eventId;
    }

    public String eventVersion() {
        return eventVersion;
    }

    public String eventType() {
        return eventType;
    }

    public AlarmEventType eventTypeEnum() {
        return AlarmEventType.fromValue(eventType);
    }

    public String tenantId() {
        return tenantId;
    }

    public OffsetDateTime occurredAt() {
        return occurredAt;
    }

    public OffsetDateTime recordedAt() {
        return recordedAt;
    }

    public String source() {
        return source;
    }

    public String correlationId() {
        return correlationId;
    }

    public String traceId() {
        return traceId;
    }

    public Map<String, Object> payload() {
        return payload;
    }

    // JavaBean aliases keep this API convenient for existing serializer adapters.
    public String getEventId() { return eventId; }
    public String getEventVersion() { return eventVersion; }
    public String getEventType() { return eventType; }
    public String getTenantId() { return tenantId; }
    public OffsetDateTime getOccurredAt() { return occurredAt; }
    public OffsetDateTime getRecordedAt() { return recordedAt; }
    public String getSource() { return source; }
    public String getCorrelationId() { return correlationId; }
    public String getTraceId() { return traceId; }
    public Map<String, Object> getPayload() { return payload; }
}
