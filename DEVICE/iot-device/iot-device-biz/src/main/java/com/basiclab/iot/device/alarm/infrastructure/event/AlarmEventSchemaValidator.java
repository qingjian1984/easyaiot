package com.basiclab.iot.device.alarm.infrastructure.event;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.networknt.schema.JsonSchema;
import com.networknt.schema.JsonSchemaFactory;
import com.networknt.schema.SpecVersion;
import com.networknt.schema.ValidationMessage;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 告警事件正式 Schema 的无副作用校验器。
 *
 * <p>只从 iot-device-api classpath 读取七个正式资源；评审 union Schema
 * 不在运行时路径中。Schema 的 {@code additionalProperties:true} 保留向后
 * 兼容的可选字段扩展，必填字段和当前主版本仍严格校验。</p>
 */
public final class AlarmEventSchemaValidator {

    public static final int CURRENT_MAJOR = 1;
    public static final String ERROR_SCHEMA_INVALID = "ALARM_EVENT_SCHEMA_INVALID";
    public static final String ERROR_UNKNOWN_MAJOR = "REJECT_UNKNOWN_MAJOR";
    public static final String ERROR_EVENT_TYPE_INVALID = "ALARM_EVENT_TYPE_INVALID";

    private static final Pattern MAJOR_PATTERN = Pattern.compile("\\.v([0-9]+)$");
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final Map<String, String> RESOURCE_BY_TYPE = resources();
    private static final Map<String, JsonSchema> SCHEMAS = loadSchemas();

    public AlarmEventSchemaValidator() {
    }

    /** 返回稳定码开头的排序错误列表；不抛出业务异常。 */
    public List<String> validate(JsonNode event) {
        List<String> errors = new ArrayList<>();
        if (event == null || !event.isObject()) {
            errors.add(ERROR_SCHEMA_INVALID + ": Envelope 必须为 JSON 对象");
            return errors;
        }
        JsonNode eventTypeNode = event.get("eventType");
        if (eventTypeNode == null || !eventTypeNode.isTextual()) {
            errors.add(ERROR_EVENT_TYPE_INVALID + ": eventType 必须为字符串");
            return errors;
        }
        String eventType = eventTypeNode.textValue();
        int major = majorVersion(eventType);
        if (major != CURRENT_MAJOR) {
            errors.add(ERROR_UNKNOWN_MAJOR + ": eventType=" + eventType);
            return errors;
        }
        JsonSchema schema = SCHEMAS.get(eventType);
        if (schema == null) {
            errors.add(ERROR_EVENT_TYPE_INVALID + ": 不支持的 eventType=" + eventType);
            return errors;
        }
        schema.validate(event).stream()
                .sorted(Comparator.comparing(ValidationMessage::getPath)
                        .thenComparing(ValidationMessage::getMessage))
                .forEach(error -> errors.add(ERROR_SCHEMA_INVALID + "@"
                        + error.getPath() + ": " + error.getMessage()));
        return errors;
    }

    /** 严格校验当前主版本；未知主版本保留稳定隔离码。 */
    public void requireValid(JsonNode event) {
        List<String> errors = validate(event);
        if (!errors.isEmpty()) {
            throw new IllegalArgumentException(errors.get(0));
        }
    }

    /** 从完整 eventType 读取主版本；无法识别时返回 -1。 */
    public static int majorVersion(String eventType) {
        if (eventType == null) {
            return -1;
        }
        Matcher matcher = MAJOR_PATTERN.matcher(eventType);
        if (!matcher.find()) {
            return -1;
        }
        try {
            return Integer.parseInt(matcher.group(1));
        } catch (NumberFormatException error) {
            return -1;
        }
    }

    /** 供 Web/测试合同对账使用的正式事件类型集合。 */
    public static List<String> eventTypes() {
        return List.copyOf(RESOURCE_BY_TYPE.keySet());
    }

    private static Map<String, String> resources() {
        Map<String, String> resources = new LinkedHashMap<>();
        resources.put("device.alarm.source-event.v1", "events/device.alarm.source-event/v1.json");
        resources.put("device.alarm.created.v1", "events/device.alarm.created/v1.json");
        resources.put("device.alarm.occurrence-recorded.v1", "events/device.alarm.occurrence-recorded/v1.json");
        resources.put("device.alarm.recovered.v1", "events/device.alarm.recovered/v1.json");
        resources.put("device.alarm.status-changed.v1", "events/device.alarm.status-changed/v1.json");
        resources.put("device.alarm.escalated.v1", "events/device.alarm.escalated/v1.json");
        resources.put("device.alarm.suppression-decided.v1", "events/device.alarm.suppression-decided/v1.json");
        return Map.copyOf(resources);
    }

    private static Map<String, JsonSchema> loadSchemas() {
        Map<String, JsonSchema> schemas = new LinkedHashMap<>();
        JsonSchemaFactory factory = JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V202012);
        for (Map.Entry<String, String> entry : RESOURCE_BY_TYPE.entrySet()) {
            try (InputStream stream = AlarmEventSchemaValidator.class.getClassLoader()
                    .getResourceAsStream(entry.getValue())) {
                if (stream == null) {
                    throw new IllegalStateException("缺少告警正式 Schema: " + entry.getValue());
                }
                JsonNode schemaNode = MAPPER.readTree(stream);
                schemas.put(entry.getKey(), factory.getSchema(schemaNode));
            } catch (IOException error) {
                throw new IllegalStateException("无法读取告警 Schema: " + entry.getValue(), error);
            }
        }
        return Map.copyOf(schemas);
    }
}
