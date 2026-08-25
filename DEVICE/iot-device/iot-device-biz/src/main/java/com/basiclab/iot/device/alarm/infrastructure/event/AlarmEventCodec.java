package com.basiclab.iot.device.alarm.infrastructure.event;

import com.basiclab.iot.device.alarm.contract.AlarmEventEnvelope;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

/** 告警 Envelope 的严格 JSON 解码入口；不装配 transport 或 Spring Bean。 */
public final class AlarmEventCodec {

    public static final String CODE_MALFORMED = "ALARM_EVENT_JSON_MALFORMED";
    private static final ObjectMapper MAPPER = new ObjectMapper()
            .enable(DeserializationFeature.FAIL_ON_READING_DUP_TREE_KEY);
    private static final AlarmEventSchemaValidator VALIDATOR = new AlarmEventSchemaValidator();

    private AlarmEventCodec() {
    }

    /** 解析并校验当前主版本的一条正式事件。 */
    public static AlarmEventEnvelope parse(String rawJson) {
        JsonNode root = parseTree(rawJson);
        VALIDATOR.requireValid(root);
        return AlarmEventEnvelope.of(
                requiredText(root, "eventId"),
                requiredText(root, "eventVersion"),
                requiredText(root, "eventType"),
                requiredText(root, "tenantId"),
                requiredOffset(root, "occurredAt"),
                requiredOffset(root, "recordedAt"),
                requiredText(root, "source"),
                requiredText(root, "correlationId"),
                optionalText(root, "traceId"),
                payload(root));
    }

    /** 仅解析严格 JSON 树；Schema/领域不变量由 {@link #parse(String)} 负责。 */
    public static JsonNode parseTree(String rawJson) {
        if (rawJson == null || rawJson.trim().isEmpty()) {
            throw new IllegalArgumentException(CODE_MALFORMED + ": 消息正文为空");
        }
        try {
            JsonNode root = MAPPER.readTree(rawJson);
            if (root == null || !root.isObject()) {
                throw new IllegalArgumentException(CODE_MALFORMED + ": 顶层必须为 JSON 对象");
            }
            return root;
        } catch (IllegalArgumentException error) {
            throw error;
        } catch (Exception error) {
            throw new IllegalArgumentException(CODE_MALFORMED + ": 无法解析 JSON", error);
        }
    }

    /** 从尚未校验的文本读取主版本，供未知主版本隔离路径使用。 */
    public static int majorVersion(String rawJson) {
        JsonNode root = parseTree(rawJson);
        JsonNode eventType = root.get("eventType");
        return eventType != null && eventType.isTextual()
                ? AlarmEventSchemaValidator.majorVersion(eventType.textValue()) : -1;
    }

    public static String envelopeHash(String rawJson) {
        return AlarmEventHash.envelopeHash(parseTree(rawJson));
    }

    private static Map<String, Object> payload(JsonNode root) {
        JsonNode node = root.get("payload");
        if (node == null || !node.isObject()) {
            throw new IllegalArgumentException(
                    "ALARM_EVENT_SCHEMA_INVALID: payload 必须为 JSON 对象");
        }
        return MAPPER.convertValue(node, new TypeReference<LinkedHashMap<String, Object>>() { });
    }

    private static String requiredText(JsonNode root, String field) {
        JsonNode node = root.get(field);
        if (node == null || !node.isTextual() || node.textValue().isBlank()) {
            throw new IllegalArgumentException(
                    "ALARM_EVENT_SCHEMA_INVALID: " + field + " 必须为非空字符串");
        }
        return node.textValue();
    }

    private static String optionalText(JsonNode root, String field) {
        JsonNode node = root.get(field);
        if (node == null || node.isNull()) {
            return null;
        }
        if (!node.isTextual()) {
            throw new IllegalArgumentException(
                    "ALARM_EVENT_SCHEMA_INVALID: " + field + " 必须为字符串或 null");
        }
        return node.textValue();
    }

    private static OffsetDateTime requiredOffset(JsonNode root, String field) {
        String value = requiredText(root, field);
        try {
            return OffsetDateTime.parse(value);
        } catch (RuntimeException error) {
            throw new IllegalArgumentException(
                    "ALARM_EVENT_SCHEMA_INVALID: " + field + " 必须为带 offset 的时间", error);
        }
    }
}
