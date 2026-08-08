package com.basiclab.iot.device.event;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * ADR-014 §事件契约：电力模型事件 Envelope（生产者/消费者共享合同类型，禁止第二份拷贝）。
 * 固定字段与 schema/power/model/v1/ 下 4 个 V1 Schema 资产一致；
 * 本类承载 Envelope 级不变量校验（eventId UUID、主版本后缀与 schemaVersion 一致、
 * 时间 ISO-8601、必填非空），逐事件 data 载荷的严格校验由 CI 门禁
 * （Ajv Draft 2020-12 strict + ajv-formats）承担。Java 8 兼容。
 */
public final class PowerModelEventEnvelope {

    /** 事件类型：模板已发布 V1。 */
    public static final String EVENT_TEMPLATE_PUBLISHED_V1 = "POWER_MODEL_TEMPLATE_PUBLISHED_V1";
    /** 事件类型：模板生命周期变更 V1。 */
    public static final String EVENT_TEMPLATE_LIFECYCLE_CHANGED_V1 = "POWER_MODEL_TEMPLATE_LIFECYCLE_CHANGED_V1";
    /** 事件类型：产品模型绑定已应用 V1。 */
    public static final String EVENT_BINDING_APPLIED_V1 = "POWER_PRODUCT_MODEL_BINDING_APPLIED_V1";
    /** 事件类型：产品模型绑定已回滚 V1。 */
    public static final String EVENT_BINDING_ROLLED_BACK_V1 = "POWER_PRODUCT_MODEL_BINDING_ROLLED_BACK_V1";

    /** 当前支持的事件 Schema 主版本（破坏性升级创建 _V2，不原地改 v1）。 */
    public static final int SUPPORTED_MAJOR_VERSION = 1;
    /** Outbox 事件 topic（ADR-014 §决策 MUST）。 */
    public static final String TOPIC_V1 = "power-model-release-v1";
    /** 死信 topic。 */
    public static final String DLQ_TOPIC_V1 = "power-model-release-v1-dlq";
    /** 消费者组（TD-001 collector 配置发布协调器）。 */
    public static final String CONSUMER_GROUP = "iot-device-power-model-release";
    /** 单事件载荷上限（字节），与 Outbox DDL ck_power_model_release_outbox_payload_bound 对齐。 */
    public static final long MAX_PAYLOAD_BYTES = 2097152L;

    /** 载荷哈希前缀：{@code "sha256:" + 64 位小写 hex}。 */
    public static final String HASH_PREFIX = "sha256:";
    /** payload_hash 格式（与 Inbox/Outbox DDL CHECK 一致）。 */
    public static final Pattern HASH_PATTERN = Pattern.compile("^sha256:[0-9a-f]{64}$");

    private static final Pattern UUID_PATTERN =
            Pattern.compile("^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$");
    private static final Pattern VERSION_SUFFIX = Pattern.compile("^([A-Z0-9_]+)_V([0-9]+)$");

    private final String eventId;
    private final String eventType;
    private final int schemaVersion;
    private final String tenantId;
    private final String aggregateType;
    private final String aggregateId;
    private final Instant occurredAt;
    private final String requestId;
    private final String traceId;
    private final Map<String, Object> data;

    private PowerModelEventEnvelope(String eventId, String eventType, int schemaVersion,
                                    String tenantId, String aggregateType, String aggregateId,
                                    Instant occurredAt, String requestId, String traceId,
                                    Map<String, Object> data) {
        this.eventId = eventId;
        this.eventType = eventType;
        this.schemaVersion = schemaVersion;
        this.tenantId = tenantId;
        this.aggregateType = aggregateType;
        this.aggregateId = aggregateId;
        this.occurredAt = occurredAt;
        this.requestId = requestId;
        this.traceId = traceId;
        this.data = Collections.unmodifiableMap(new LinkedHashMap<String, Object>(data));
    }

    /**
     * 构造并校验 Envelope 不变量；任一违规抛出 IllegalArgumentException（MODEL_EVENT_* 稳定码前缀）。
     */
    public static PowerModelEventEnvelope of(String eventId, String eventType, int schemaVersion,
                                             String tenantId, String aggregateType, String aggregateId,
                                             String occurredAt, String requestId, String traceId,
                                             Map<String, Object> data) {
        requireNonBlank(eventId, "eventId");
        if (!UUID_PATTERN.matcher(eventId).matches()) {
            throw new IllegalArgumentException("MODEL_EVENT_ENVELOPE_INVALID: eventId 必须为 UUID v4 小写格式");
        }
        requireNonBlank(eventType, "eventType");
        int suffix = majorVersionOf(eventType);
        if (suffix < 0) {
            throw new IllegalArgumentException("MODEL_EVENT_ENVELOPE_INVALID: eventType 必须以 _V<主版本> 结尾");
        }
        if (schemaVersion < 1) {
            throw new IllegalArgumentException("MODEL_EVENT_ENVELOPE_INVALID: schemaVersion 必须 >= 1");
        }
        if (suffix != schemaVersion) {
            throw new IllegalArgumentException(
                    "MODEL_EVENT_VERSION_SUFFIX_MISMATCH: eventType 后缀 _V" + suffix
                            + " 与 schemaVersion=" + schemaVersion + " 不一致");
        }
        requireNonBlank(tenantId, "tenantId");
        requireNonBlank(aggregateType, "aggregateType");
        requireNonBlank(aggregateId, "aggregateId");
        requireNonBlank(requestId, "requestId");
        Objects.requireNonNull(data, "data");
        return new PowerModelEventEnvelope(eventId, eventType, schemaVersion, tenantId,
                aggregateType, aggregateId, parseInstant(occurredAt), requestId,
                traceId == null ? "" : traceId, data);
    }

    /** 解析 eventType 主版本后缀；无合法后缀返回 -1。 */
    public static int majorVersionOf(String eventType) {
        if (eventType == null) {
            return -1;
        }
        Matcher m = VERSION_SUFFIX.matcher(eventType);
        if (!m.matches()) {
            return -1;
        }
        try {
            return Integer.parseInt(m.group(2));
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    /** 计算载荷哈希：{@code "sha256:" + 小写 hex(SHA-256(payload))}。 */
    public static String payloadHash(byte[] canonicalPayload) {
        Objects.requireNonNull(canonicalPayload, "canonicalPayload");
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(canonicalPayload);
            StringBuilder sb = new StringBuilder(HASH_PREFIX);
            for (byte b : digest) {
                sb.append(Character.forDigit((b >> 4) & 0xF, 16));
                sb.append(Character.forDigit(b & 0xF, 16));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 不可用", e);
        }
    }

    /** UTF-8 载荷便捷重载。 */
    public static String payloadHash(String canonicalPayload) {
        Objects.requireNonNull(canonicalPayload, "canonicalPayload");
        return payloadHash(canonicalPayload.getBytes(StandardCharsets.UTF_8));
    }

    /** Kafka key：{@code tenantId:aggregateType:aggregateId}，保证同一聚合分区有序。 */
    public String topicKey() {
        return tenantId + ":" + aggregateType + ":" + aggregateId;
    }

    private static Instant parseInstant(String occurredAt) {
        requireNonBlank(occurredAt, "occurredAt");
        try {
            return Instant.parse(occurredAt);
        } catch (DateTimeParseException e) {
            throw new IllegalArgumentException(
                    "MODEL_EVENT_ENVELOPE_INVALID: occurredAt 必须为 UTC ISO 8601", e);
        }
    }

    private static void requireNonBlank(String value, String field) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException(
                    "MODEL_EVENT_ENVELOPE_INVALID: " + field + " 不得为空");
        }
    }

    public String eventId() {
        return eventId;
    }

    public String eventType() {
        return eventType;
    }

    public int schemaVersion() {
        return schemaVersion;
    }

    public String tenantId() {
        return tenantId;
    }

    public String aggregateType() {
        return aggregateType;
    }

    public String aggregateId() {
        return aggregateId;
    }

    public Instant occurredAt() {
        return occurredAt;
    }

    public String requestId() {
        return requestId;
    }

    public String traceId() {
        return traceId;
    }

    public Map<String, Object> data() {
        return data;
    }
}
