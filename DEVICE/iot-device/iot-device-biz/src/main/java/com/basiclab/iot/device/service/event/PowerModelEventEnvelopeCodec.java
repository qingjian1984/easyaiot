package com.basiclab.iot.device.service.event;

import com.basiclab.iot.device.event.PowerModelEventEnvelope;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * ADR-014 §事件契约：Envelope 编解码（消费侧解析入口）。
 * 解析 JSON → 必填字段提取 → 交由 {@link PowerModelEventEnvelope#of} 校验不变量；
 * 畸形 JSON / 缺字段 / 不变量违规一律抛出带稳定码的 IllegalArgumentException，
 * 由消费编排器按 poison 消息处置（进 DLQ，不跳过、不阻塞分区）。
 * Java 8 兼容。
 */
public final class PowerModelEventEnvelopeCodec {

    /** JSON 畸形（无法解析为对象）。 */
    public static final String CODE_MALFORMED = "MODEL_EVENT_JSON_MALFORMED";

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private PowerModelEventEnvelopeCodec() {
    }

    /**
     * 解析一条原始消息为 Envelope。
     *
     * @param raw 原始消息正文（消费侧 payload_hash 应以该原文计算，与生产侧 Outbox 一致）
     * @return 校验通过的 Envelope（data 为载荷对象转换的 Map）
     * @throws IllegalArgumentException 畸形（MODEL_EVENT_JSON_MALFORMED）或不变量违规（MODEL_EVENT_*）
     */
    public static PowerModelEventEnvelope parse(String raw) {
        if (raw == null || raw.trim().isEmpty()) {
            throw new IllegalArgumentException(CODE_MALFORMED + ": 消息正文为空");
        }
        JsonNode root;
        try {
            root = MAPPER.readTree(raw);
        } catch (Exception e) {
            throw new IllegalArgumentException(CODE_MALFORMED + ": 无法解析为 JSON", e);
        }
        if (root == null || !root.isObject()) {
            throw new IllegalArgumentException(CODE_MALFORMED + ": 顶层必须为 JSON 对象");
        }
        return PowerModelEventEnvelope.of(
                text(root, "eventId"),
                text(root, "eventType"),
                integer(root, "schemaVersion"),
                text(root, "tenantId"),
                text(root, "aggregateType"),
                text(root, "aggregateId"),
                text(root, "occurredAt"),
                text(root, "requestId"),
                text(root, "traceId"),
                data(root));
    }

    /** 提取 data 载荷原始 JSON 文本（供逐事件 Schema 校验/业务处理器使用）。 */
    public static String dataJson(PowerModelEventEnvelope envelope) {
        Objects.requireNonNull(envelope, "envelope");
        try {
            return MAPPER.writeValueAsString(envelope.data());
        } catch (Exception e) {
            throw new IllegalStateException("data 载荷无法序列化", e);
        }
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> data(JsonNode root) {
        JsonNode node = root.get("data");
        if (node == null || !node.isObject()) {
            throw new IllegalArgumentException(
                    "MODEL_EVENT_ENVELOPE_INVALID: data 必须为 JSON 对象");
        }
        return MAPPER.convertValue(node, LinkedHashMap.class);
    }

    private static String text(JsonNode root, String field) {
        JsonNode node = root.get(field);
        if (node == null || node.isNull()) {
            return null;
        }
        return node.isValueNode() ? node.asText() : node.toString();
    }

    private static int integer(JsonNode root, String field) {
        JsonNode node = root.get(field);
        if (node == null || !node.isNumber()) {
            throw new IllegalArgumentException(
                    "MODEL_EVENT_ENVELOPE_INVALID: " + field + " 必须为整数");
        }
        return node.intValue();
    }
}
