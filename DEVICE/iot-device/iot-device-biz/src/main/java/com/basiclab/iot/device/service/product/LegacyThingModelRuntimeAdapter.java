package com.basiclab.iot.device.service.product;

import com.basiclab.iot.common.utils.SnowflakeIdUtil;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.DecimalNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * TD-005 旧物模型 JSON 与八张运行模型表之间的纯聚合适配器。
 *
 * <p>本类只处理合同转换，不访问数据库。持久化编排必须在 tenant-safe 事务服务中完成，
 * 且服务输入/输出只能进入 command request/response，不能写入 product_properties。</p>
 */
@Service
public class LegacyThingModelRuntimeAdapter {

    public static final String LEGACY_SCHEMA_VERSION = "easyaiot-legacy-thing-model-v1";
    public static final String RUNTIME_CONTRACT_VERSION = "td005-runtime-projection-v1";

    private final ObjectMapper objectMapper;

    public LegacyThingModelRuntimeAdapter(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public ObjectNode importToRuntime(JsonNode document) {
        return importToRuntime(document, type -> Long.parseLong(SnowflakeIdUtil.nextId()));
    }

    public ObjectNode importToRuntime(JsonNode document, RuntimeIdAllocator idAllocator) {
        ObjectNode source = requireObject(document, "$", "MODEL_LEGACY_DOCUMENT_INVALID");
        requireTextEquals(source, "schemaVersion", LEGACY_SCHEMA_VERSION);
        long tenantId = requireLong(source, "tenantId");
        String productIdentification = requireText(source, "productIdentification");

        ObjectNode runtime = objectMapper.createObjectNode();
        runtime.put("contractVersion", RUNTIME_CONTRACT_VERSION);
        ObjectNode tables = runtime.putObject("tables");
        ArrayNode products = tables.putArray("product");
        ArrayNode properties = tables.putArray("product_properties");
        ArrayNode services = tables.putArray("product_services");
        ArrayNode commands = tables.putArray("product_commands");
        ArrayNode requests = tables.putArray("product_commands_requests");
        ArrayNode responses = tables.putArray("product_commands_response");
        ArrayNode events = tables.putArray("product_event");
        ArrayNode eventResponses = tables.putArray("product_event_response");

        ObjectNode product = products.addObject();
        product.put("id", idAllocator.next(RuntimeEntity.PRODUCT));
        product.put("productIdentification", productIdentification);
        product.put("tenantId", tenantId);

        for (JsonNode value : arrayOrEmpty(source, "properties")) {
            ObjectNode item = requireObject(value, "$.properties[]", "MODEL_LEGACY_PROPERTY_INVALID");
            if (hasValue(item, "serviceId")) {
                throw contractError("MODEL_ROOT_PROPERTY_SERVICE_ID_FORBIDDEN", item.get("serviceId").asText());
            }
            ObjectNode row = properties.addObject();
            row.put("id", idAllocator.next(RuntimeEntity.ROOT_PROPERTY));
            row.put("propertyCode", requireText(item, "propertyCode"));
            row.put("propertyName", requireText(item, "propertyName"));
            row.put("datatype", requireText(item, "datatype").toUpperCase());
            copyPresent(row, item, "method", "step", "unit", "required", "description");
            copyAsDbText(row, item, "min", "max");
            row.putNull("templateIdentification");
            row.put("productIdentification", productIdentification);
            row.put("tenantId", tenantId);
        }

        for (JsonNode value : arrayOrEmpty(source, "services")) {
            ObjectNode item = requireObject(value, "$.services[]", "MODEL_LEGACY_SERVICE_INVALID");
            if (item.has("properties") && !item.get("properties").isNull()) {
                if (!item.get("properties").isArray()) {
                    throw contractError("MODEL_LEGACY_ARRAY_REQUIRED", "services[].properties");
                }
                if (item.get("properties").size() > 0) {
                    throw contractError("MODEL_LEGACY_SERVICE_PROPERTIES_AMBIGUOUS", requireText(item, "serviceCode"));
                }
            }
            long serviceId = idAllocator.next(RuntimeEntity.SERVICE);
            long commandId = idAllocator.next(RuntimeEntity.COMMAND);
            String serviceCode = requireText(item, "serviceCode");
            String serviceName = requireText(item, "serviceName");

            ObjectNode service = services.addObject();
            service.put("id", serviceId);
            service.put("serviceCode", serviceCode);
            service.put("serviceName", serviceName);
            service.put("status", textOrDefault(item, "status", "0"));
            copyPresent(service, item, "description");
            service.putNull("templateIdentification");
            service.put("productIdentification", productIdentification);
            service.put("tenantId", tenantId);

            ObjectNode command = commands.addObject();
            command.put("id", commandId);
            command.put("serviceId", serviceId);
            command.put("commandCode", textOrDefault(item, "commandCode", serviceCode));
            command.put("name", serviceName);
            copyPresent(command, item, "description");
            command.put("tenantId", tenantId);

            appendServiceParameters(requests, arrayOrEmpty(item, "inputParams"), serviceId, commandId,
                    tenantId, RuntimeEntity.SERVICE_INPUT, idAllocator);
            appendServiceParameters(responses, arrayOrEmpty(item, "outParams"), serviceId, commandId,
                    tenantId, RuntimeEntity.SERVICE_OUTPUT, idAllocator);
        }

        for (JsonNode value : arrayOrEmpty(source, "events")) {
            ObjectNode item = requireObject(value, "$.events[]", "MODEL_LEGACY_EVENT_INVALID");
            long eventId = idAllocator.next(RuntimeEntity.EVENT);
            ObjectNode event = events.addObject();
            event.put("id", eventId);
            event.put("eventCode", requireText(item, "eventCode"));
            event.put("eventName", requireText(item, "eventName"));
            event.put("eventType", requireText(item, "eventType"));
            event.put("status", textOrDefault(item, "status", "0"));
            copyPresent(event, item, "description");
            event.putNull("templateIdentification");
            event.put("productIdentification", productIdentification);
            event.put("tenantId", tenantId);

            for (JsonNode parameterValue : arrayOrEmpty(item, "outParams")) {
                ObjectNode parameter = requireObject(parameterValue, "$.events[].outParams[]",
                        "MODEL_LEGACY_EVENT_PARAM_INVALID");
                ObjectNode row = eventResponses.addObject();
                row.put("id", idAllocator.next(RuntimeEntity.EVENT_OUTPUT));
                row.put("eventId", eventId);
                row.putNull("serviceId");
                row.put("parameterName", requireText(parameter, "parameterName"));
                row.put("datatype", requireText(parameter, "datatype").toUpperCase());
                copyAsDbText(row, parameter, "min", "max", "step", "required");
                copyPresent(row, parameter, "unit", "enumlist");
                copyDescription(row, parameter);
                row.put("tenantId", tenantId);
            }
        }
        return runtime;
    }

    public ObjectNode exportFromRuntime(JsonNode runtimeDocument) {
        ObjectNode runtime = requireObject(runtimeDocument, "$", "MODEL_RUNTIME_DOCUMENT_INVALID");
        requireTextEquals(runtime, "contractVersion", RUNTIME_CONTRACT_VERSION);
        ObjectNode tables = requireObject(runtime.get("tables"), "$.tables", "MODEL_RUNTIME_TABLES_INVALID");
        ArrayNode products = requireArray(tables, "product");
        if (products.size() != 1) {
            throw contractError("MODEL_RUNTIME_PRODUCT_CARDINALITY_INVALID", "product");
        }
        ObjectNode product = requireObject(products.get(0), "$.tables.product[0]", "MODEL_RUNTIME_PRODUCT_INVALID");

        ObjectNode output = objectMapper.createObjectNode();
        output.put("schemaVersion", LEGACY_SCHEMA_VERSION);
        output.set("tenantId", requiredNode(product, "tenantId").deepCopy());
        output.put("productIdentification", requireText(product, "productIdentification"));
        ArrayNode properties = output.putArray("properties");
        ArrayNode services = output.putArray("services");
        ArrayNode events = output.putArray("events");

        for (JsonNode value : requireArray(tables, "product_properties")) {
            ObjectNode row = requireObject(value, "$.tables.product_properties[]", "MODEL_RUNTIME_PROPERTY_INVALID");
            ObjectNode item = properties.addObject();
            copyRequired(item, row, "id", "propertyCode", "propertyName", "datatype");
            copyAsLegacyNumber(item, row, "min", "max");
            copyPresent(item, row, "method", "step", "unit", "required", "description");
        }

        Map<Long, ObjectNode> commandsByService = indexUnique(requireArray(tables, "product_commands"),
                "serviceId", "MODEL_RUNTIME_COMMAND_DUPLICATE");
        Map<Long, ArrayNode> requestsByCommand = groupBy(requireArray(tables, "product_commands_requests"), "commandsId");
        Map<Long, ArrayNode> responsesByCommand = groupBy(requireArray(tables, "product_commands_response"), "commandsId");
        for (JsonNode value : requireArray(tables, "product_services")) {
            ObjectNode row = requireObject(value, "$.tables.product_services[]", "MODEL_RUNTIME_SERVICE_INVALID");
            long serviceId = requireLong(row, "id");
            ObjectNode command = commandsByService.get(serviceId);
            if (command == null) {
                throw contractError("MODEL_RUNTIME_DEFAULT_COMMAND_MISSING", String.valueOf(serviceId));
            }
            ObjectNode item = services.addObject();
            copyRequired(item, row, "id", "serviceCode", "serviceName", "status");
            copyPresent(item, row, "description");
            item.set("commandId", requiredNode(command, "id").deepCopy());
            item.set("commandCode", requiredNode(command, "commandCode").deepCopy());
            long commandId = requireLong(command, "id");
            item.set("inputParams", exportParameters(requestsByCommand.get(commandId), true));
            item.set("outParams", exportParameters(responsesByCommand.get(commandId), true));
        }

        Map<Long, ArrayNode> eventOutputs = groupBy(requireArray(tables, "product_event_response"), "eventId");
        for (JsonNode value : requireArray(tables, "product_event")) {
            ObjectNode row = requireObject(value, "$.tables.product_event[]", "MODEL_RUNTIME_EVENT_INVALID");
            ObjectNode item = events.addObject();
            copyRequired(item, row, "id", "eventCode", "eventName", "eventType", "status");
            copyPresent(item, row, "description");
            item.set("outParams", exportParameters(eventOutputs.get(requireLong(row, "id")), false));
        }
        return output;
    }

    private void appendServiceParameters(ArrayNode target, ArrayNode values, long serviceId, long commandId,
                                         long tenantId, RuntimeEntity type, RuntimeIdAllocator idAllocator) {
        for (JsonNode value : values) {
            ObjectNode parameter = requireObject(value, "$.services[].params[]", "MODEL_LEGACY_SERVICE_PARAM_INVALID");
            ObjectNode row = target.addObject();
            row.put("id", idAllocator.next(type));
            row.put("serviceId", serviceId);
            row.put("commandsId", commandId);
            row.put("parameterCode", requireText(parameter, "parameterCode"));
            row.put("parameterName", requireText(parameter, "parameterName"));
            row.put("datatype", requireText(parameter, "datatype").toUpperCase());
            copyAsDbText(row, parameter, "min", "max", "step", "required");
            copyPresent(row, parameter, "unit", "enumlist");
            copyDescription(row, parameter);
            row.put("tenantId", tenantId);
        }
    }

    private ArrayNode exportParameters(ArrayNode rows, boolean serviceParameter) {
        ArrayNode output = objectMapper.createArrayNode();
        if (rows == null) {
            return output;
        }
        for (JsonNode value : rows) {
            ObjectNode row = requireObject(value, "$.tables.parameters[]", "MODEL_RUNTIME_PARAM_INVALID");
            ObjectNode item = output.addObject();
            item.set("id", requiredNode(row, "id").deepCopy());
            if (serviceParameter) {
                item.set("serviceId", requiredNode(row, "serviceId").deepCopy());
                item.set("parameterCode", requiredNode(row, "parameterCode").deepCopy());
                item.set("parameterName", requiredNode(row, "parameterName").deepCopy());
                item.set("propertyCode", requiredNode(row, "parameterCode").deepCopy());
                item.set("propertyName", requiredNode(row, "parameterName").deepCopy());
            } else {
                item.set("eventId", requiredNode(row, "eventId").deepCopy());
                item.set("parameterName", requiredNode(row, "parameterName").deepCopy());
            }
            item.set("datatype", requiredNode(row, "datatype").deepCopy());
            copyAsLegacyNumber(item, row, "min", "max", "step", "required");
            copyPresent(item, row, "unit", "enumlist");
            if (hasValue(row, "parameterDescription")) {
                item.set("description", row.get("parameterDescription").deepCopy());
                item.set("parameterDescription", row.get("parameterDescription").deepCopy());
            }
            if ("BOOL".equals(row.path("datatype").asText()) && hasValue(row, "enumlist")) {
                addBoolLabels(item, row.get("enumlist").asText());
            }
        }
        return output;
    }

    private void addBoolLabels(ObjectNode item, String enumlist) {
        try {
            JsonNode labels = objectMapper.readTree(enumlist);
            item.put("boolClose", labels.path("0").asText("关"));
            item.put("boolOpen", labels.path("1").asText("开"));
        } catch (Exception e) {
            throw contractError("MODEL_RUNTIME_BOOLLABEL_INVALID", enumlist);
        }
    }

    private Map<Long, ObjectNode> indexUnique(ArrayNode rows, String key, String errorCode) {
        Map<Long, ObjectNode> result = new LinkedHashMap<>();
        for (JsonNode value : rows) {
            ObjectNode row = requireObject(value, "$.tables[]", "MODEL_RUNTIME_ROW_INVALID");
            long id = requireLong(row, key);
            if (result.put(id, row) != null) {
                throw contractError(errorCode, String.valueOf(id));
            }
        }
        return result;
    }

    private Map<Long, ArrayNode> groupBy(ArrayNode rows, String key) {
        Map<Long, ArrayNode> result = new HashMap<>();
        for (JsonNode value : rows) {
            ObjectNode row = requireObject(value, "$.tables[]", "MODEL_RUNTIME_ROW_INVALID");
            result.computeIfAbsent(requireLong(row, key), ignored -> objectMapper.createArrayNode()).add(row);
        }
        return result;
    }

    private void copyDescription(ObjectNode target, ObjectNode source) {
        if (hasValue(source, "description")) {
            target.set("parameterDescription", source.get("description").deepCopy());
        }
    }

    private void copyRequired(ObjectNode target, ObjectNode source, String... keys) {
        for (String key : keys) {
            target.set(key, requiredNode(source, key).deepCopy());
        }
    }

    private void copyPresent(ObjectNode target, ObjectNode source, String... keys) {
        for (String key : keys) {
            if (hasValue(source, key)) {
                target.set(key, source.get(key).deepCopy());
            }
        }
    }

    private void copyAsDbText(ObjectNode target, ObjectNode source, String... keys) {
        for (String key : keys) {
            if (hasValue(source, key)) {
                target.put(key, source.get(key).asText());
            }
        }
    }

    private void copyAsLegacyNumber(ObjectNode target, ObjectNode source, String... keys) {
        for (String key : keys) {
            if (!hasValue(source, key)) {
                continue;
            }
            BigDecimal value;
            try {
                value = new BigDecimal(source.get(key).asText()).stripTrailingZeros();
            } catch (NumberFormatException e) {
                throw contractError("MODEL_RUNTIME_NUMBER_INVALID", key);
            }
            if (value.scale() <= 0) {
                try {
                    target.put(key, value.intValueExact());
                } catch (ArithmeticException ignored) {
                    try {
                        target.put(key, value.longValueExact());
                    } catch (ArithmeticException overflow) {
                        target.set(key, DecimalNode.valueOf(value));
                    }
                }
            } else {
                target.set(key, DecimalNode.valueOf(value));
            }
        }
    }

    private ArrayNode arrayOrEmpty(ObjectNode source, String field) {
        JsonNode value = source.get(field);
        if (value == null || value.isNull()) {
            return objectMapper.createArrayNode();
        }
        if (!value.isArray()) {
            throw contractError("MODEL_LEGACY_ARRAY_REQUIRED", field);
        }
        return (ArrayNode) value;
    }

    private ArrayNode requireArray(ObjectNode source, String field) {
        JsonNode value = source.get(field);
        if (value == null || !value.isArray()) {
            throw contractError("MODEL_RUNTIME_TABLE_REQUIRED", field);
        }
        return (ArrayNode) value;
    }

    private ObjectNode requireObject(JsonNode value, String path, String errorCode) {
        if (value == null || !value.isObject()) {
            throw contractError(errorCode, path);
        }
        return (ObjectNode) value;
    }

    private JsonNode requiredNode(ObjectNode source, String field) {
        JsonNode value = source.get(field);
        if (value == null || value.isNull()) {
            throw contractError("MODEL_FIELD_REQUIRED", field);
        }
        return value;
    }

    private String requireText(ObjectNode source, String field) {
        JsonNode value = requiredNode(source, field);
        String text = value.asText().trim();
        if (text.isEmpty()) {
            throw contractError("MODEL_FIELD_REQUIRED", field);
        }
        return text;
    }

    private long requireLong(ObjectNode source, String field) {
        JsonNode value = requiredNode(source, field);
        if (!value.canConvertToLong() && !value.isTextual()) {
            throw contractError("MODEL_LONG_REQUIRED", field);
        }
        try {
            return Long.parseLong(value.asText());
        } catch (NumberFormatException e) {
            throw contractError("MODEL_LONG_REQUIRED", field);
        }
    }

    private void requireTextEquals(ObjectNode source, String field, String expected) {
        if (!expected.equals(requireText(source, field))) {
            throw contractError("MODEL_VERSION_UNSUPPORTED", field);
        }
    }

    private String textOrDefault(ObjectNode source, String field, String defaultValue) {
        return hasValue(source, field) ? source.get(field).asText() : defaultValue;
    }

    private boolean hasValue(ObjectNode source, String field) {
        return source.has(field) && !source.get(field).isNull();
    }

    private IllegalArgumentException contractError(String code, String detail) {
        return new IllegalArgumentException(code + ": " + detail);
    }

    public enum RuntimeEntity {
        PRODUCT,
        ROOT_PROPERTY,
        SERVICE,
        COMMAND,
        SERVICE_INPUT,
        SERVICE_OUTPUT,
        EVENT,
        EVENT_OUTPUT
    }

    @FunctionalInterface
    public interface RuntimeIdAllocator {
        long next(RuntimeEntity type);
    }
}
