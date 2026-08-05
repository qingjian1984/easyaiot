package com.basiclab.iot.device.service.product;

import com.basiclab.iot.common.core.context.TenantContextHolder;
import com.basiclab.iot.common.capability.CapabilityService;
import com.basiclab.iot.common.exception.ServiceException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.sql.DataSource;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static com.basiclab.iot.common.exception.util.ServiceExceptionUtil.exception;
import static com.basiclab.iot.device.enums.ErrorCodeConstants.CAPABILITY_NOT_SUPPORTED;
import static com.basiclab.iot.device.enums.ErrorCodeConstants.MODEL_FIELD_REQUIRED;
import static com.basiclab.iot.device.enums.ErrorCodeConstants.MODEL_LONG_REQUIRED;
import static com.basiclab.iot.device.enums.ErrorCodeConstants.MODEL_PRODUCT_NOT_FOUND;
import static com.basiclab.iot.device.enums.ErrorCodeConstants.MODEL_PRODUCT_SCOPE_AMBIGUOUS;
import static com.basiclab.iot.device.enums.ErrorCodeConstants.MODEL_RUNTIME_CONTRACT_INVALID;
import static com.basiclab.iot.device.enums.ErrorCodeConstants.MODEL_RUNTIME_ID_DUPLICATE;
import static com.basiclab.iot.device.enums.ErrorCodeConstants.MODEL_RUNTIME_SCOPE_INVALID;
import static com.basiclab.iot.device.enums.ErrorCodeConstants.MODEL_SERVICE_PARAM_RELATION_INVALID;
import static com.basiclab.iot.device.enums.ErrorCodeConstants.MODEL_TENANT_MISMATCH;
import static com.basiclab.iot.device.enums.ErrorCodeConstants.MODEL_VERSION_UNSUPPORTED;

/**
 * TD-005 legacy thing-model persistence boundary.
 *
 * <p>This service is intentionally not exposed by a controller yet. It validates the complete
 * runtime ownership graph before touching the database, binds every statement to the required
 * tenant context, and replaces the seven child tables in one transaction. The existing product
 * must already exist and is locked as the aggregate root.</p>
 */
@Service
public class LegacyThingModelPersistenceService {

    public static final String CAPABILITY_CODE = "power.device.model";

    private static final String PRODUCT_LOCK_SQL = """
            SELECT id
            FROM product
            WHERE tenant_id = :tenantId AND product_identification = :productIdentification
            FOR UPDATE
            """;

    private final NamedParameterJdbcTemplate jdbc;
    private final LegacyThingModelRuntimeAdapter adapter;
    private final ObjectMapper objectMapper;
    private final CapabilityService capabilityService;

    public LegacyThingModelPersistenceService(DataSource dataSource,
                                              LegacyThingModelRuntimeAdapter adapter,
                                              ObjectMapper objectMapper,
                                              CapabilityService capabilityService) {
        this.jdbc = new NamedParameterJdbcTemplate(dataSource);
        this.adapter = adapter;
        this.objectMapper = objectMapper;
        this.capabilityService = capabilityService;
    }

    @Transactional(rollbackFor = Exception.class)
    public ObjectNode replaceLegacyDocument(JsonNode document) {
        requireCapability();
        ObjectNode runtime;
        try {
            runtime = adapter.importToRuntime(document);
        } catch (IllegalArgumentException error) {
            throw translateAdapterError(error);
        }
        replaceRuntimeForCurrentTenant(runtime);
        String productIdentification = requiredText(product(runtime), "productIdentification");
        return exportLegacyForCurrentTenant(productIdentification);
    }

    /**
     * Internal runtime projection entry used by the legacy adapter and integration contracts.
     * It must not be exposed as an HTTP request model.
     */
    @Transactional(rollbackFor = Exception.class)
    public void replaceRuntimeForCurrentTenant(JsonNode runtimeDocument) {
        requireCapability();
        long tenantId = TenantContextHolder.getRequiredTenantId();
        ObjectNode runtime = requireObject(runtimeDocument, "MODEL_RUNTIME_DOCUMENT_INVALID");
        if (!LegacyThingModelRuntimeAdapter.RUNTIME_CONTRACT_VERSION.equals(
                requiredText(runtime, "contractVersion"))) {
            throw contractError("MODEL_VERSION_UNSUPPORTED", "contractVersion");
        }
        ObjectNode tables = requireObject(runtime.get("tables"), "MODEL_RUNTIME_TABLES_INVALID");
        ObjectNode product = product(runtime);
        String productIdentification = requiredText(product, "productIdentification");

        validateOwnershipGraph(tables, tenantId, productIdentification);
        MapSqlParameterSource scope = scope(tenantId, productIdentification);
        lockProduct(scope);
        deleteExistingModel(scope);
        insertRuntimeRows(tables, tenantId, productIdentification);
    }

    @Transactional(readOnly = true)
    public ObjectNode exportLegacyForCurrentTenant(String productIdentification) {
        requireCapability();
        long tenantId = TenantContextHolder.getRequiredTenantId();
        if (productIdentification == null || productIdentification.isBlank()) {
            throw contractError("MODEL_FIELD_REQUIRED", "productIdentification");
        }
        MapSqlParameterSource scope = scope(tenantId, productIdentification.trim());
        long productId = queryProductId(scope);

        ObjectNode runtime = objectMapper.createObjectNode();
        runtime.put("contractVersion", LegacyThingModelRuntimeAdapter.RUNTIME_CONTRACT_VERSION);
        ObjectNode tables = runtime.putObject("tables");
        ArrayNode products = tables.putArray("product");
        ObjectNode product = products.addObject();
        product.put("id", productId);
        product.put("productIdentification", productIdentification.trim());
        product.put("tenantId", tenantId);

        tables.set("product_properties", queryRootProperties(scope));
        tables.set("product_services", queryServices(scope));
        tables.set("product_commands", queryCommands(scope));
        tables.set("product_commands_requests", queryCommandRequests(scope));
        tables.set("product_commands_response", queryCommandResponses(scope));
        tables.set("product_event", queryEvents(scope));
        tables.set("product_event_response", queryEventResponses(scope));
        try {
            return adapter.exportFromRuntime(runtime);
        } catch (IllegalArgumentException error) {
            throw translateAdapterError(error);
        }
    }

    private void validateOwnershipGraph(ObjectNode tables, long tenantId, String productIdentification) {
        ArrayNode properties = requiredArray(tables, "product_properties");
        ArrayNode services = requiredArray(tables, "product_services");
        ArrayNode commands = requiredArray(tables, "product_commands");
        ArrayNode requests = requiredArray(tables, "product_commands_requests");
        ArrayNode responses = requiredArray(tables, "product_commands_response");
        ArrayNode events = requiredArray(tables, "product_event");
        ArrayNode eventResponses = requiredArray(tables, "product_event_response");

        validateScopedRows(properties, tenantId, productIdentification, "product_properties");
        validateScopedRows(services, tenantId, productIdentification, "product_services");
        validateScopedRows(events, tenantId, productIdentification, "product_event");

        Map<Long, ObjectNode> servicesById = indexRows(services, tenantId, "product_services");
        Map<Long, ObjectNode> commandsById = indexRows(commands, tenantId, "product_commands");
        Map<Long, ObjectNode> eventsById = indexRows(events, tenantId, "product_event");
        indexRows(properties, tenantId, "product_properties");
        indexRows(requests, tenantId, "product_commands_requests");
        indexRows(responses, tenantId, "product_commands_response");
        indexRows(eventResponses, tenantId, "product_event_response");

        Map<Long, Long> commandService = new HashMap<>();
        for (JsonNode value : commands) {
            ObjectNode command = requireObject(value, "MODEL_RUNTIME_ROW_INVALID");
            long commandId = requiredLong(command, "id");
            long serviceId = requiredLong(command, "serviceId");
            if (!servicesById.containsKey(serviceId)) {
                throw contractError("MODEL_RUNTIME_REFERENCE_INVALID", "command.serviceId=" + serviceId);
            }
            commandService.put(commandId, serviceId);
        }
        validateServiceParameters(requests, commandService, "request");
        validateServiceParameters(responses, commandService, "response");

        for (JsonNode value : eventResponses) {
            ObjectNode row = requireObject(value, "MODEL_RUNTIME_ROW_INVALID");
            long eventId = requiredLong(row, "eventId");
            if (!eventsById.containsKey(eventId)) {
                throw contractError("MODEL_RUNTIME_REFERENCE_INVALID", "eventResponse.eventId=" + eventId);
            }
            if (hasValue(row, "serviceId")) {
                throw contractError("MODEL_SERVICE_PARAM_RELATION_INVALID", "eventResponse.serviceId");
            }
        }
        if (commandsById.size() != commandService.size()) {
            throw contractError("MODEL_RUNTIME_REFERENCE_INVALID", "commands");
        }
    }

    private void validateScopedRows(ArrayNode rows, long tenantId, String productIdentification,
                                    String table) {
        for (JsonNode value : rows) {
            ObjectNode row = requireObject(value, "MODEL_RUNTIME_ROW_INVALID");
            validateTenant(row, tenantId, table);
            if (!productIdentification.equals(requiredText(row, "productIdentification"))
                    || hasValue(row, "templateIdentification")) {
                throw contractError("MODEL_RUNTIME_SCOPE_INVALID", table);
            }
        }
    }

    private Map<Long, ObjectNode> indexRows(ArrayNode rows, long tenantId, String table) {
        Map<Long, ObjectNode> result = new HashMap<>();
        Set<Long> ids = new HashSet<>();
        for (JsonNode value : rows) {
            ObjectNode row = requireObject(value, "MODEL_RUNTIME_ROW_INVALID");
            validateTenant(row, tenantId, table);
            long id = requiredLong(row, "id");
            if (!ids.add(id)) {
                throw contractError("MODEL_RUNTIME_ID_DUPLICATE", table + ".id=" + id);
            }
            result.put(id, row);
        }
        return result;
    }

    private void validateServiceParameters(ArrayNode rows, Map<Long, Long> commandService, String direction) {
        for (JsonNode value : rows) {
            ObjectNode row = requireObject(value, "MODEL_RUNTIME_ROW_INVALID");
            long commandId = requiredLong(row, "commandsId");
            long serviceId = requiredLong(row, "serviceId");
            Long expectedServiceId = commandService.get(commandId);
            if (expectedServiceId == null || expectedServiceId.longValue() != serviceId) {
                throw contractError("MODEL_SERVICE_PARAM_RELATION_INVALID",
                        direction + ".commandsId=" + commandId + ",serviceId=" + serviceId);
            }
        }
    }

    private void validateTenant(ObjectNode row, long tenantId, String table) {
        if (requiredLong(row, "tenantId") != tenantId) {
            throw contractError("MODEL_TENANT_MISMATCH", table);
        }
    }

    private ObjectNode product(ObjectNode runtime) {
        ObjectNode tables = requireObject(runtime.get("tables"), "MODEL_RUNTIME_TABLES_INVALID");
        ArrayNode products = requiredArray(tables, "product");
        if (products.size() != 1) {
            throw contractError("MODEL_RUNTIME_PRODUCT_CARDINALITY_INVALID", "product");
        }
        ObjectNode product = requireObject(products.get(0), "MODEL_RUNTIME_PRODUCT_INVALID");
        long tenantId = TenantContextHolder.getRequiredTenantId();
        validateTenant(product, tenantId, "product");
        return product;
    }

    private void lockProduct(MapSqlParameterSource scope) {
        List<Long> ids = jdbc.query(PRODUCT_LOCK_SQL, scope, (rs, rowNum) -> rs.getLong(1));
        if (ids.isEmpty()) {
            throw contractError("MODEL_PRODUCT_NOT_FOUND", String.valueOf(scope.getValue("productIdentification")));
        }
        if (ids.size() != 1) {
            throw contractError("MODEL_PRODUCT_SCOPE_AMBIGUOUS",
                    String.valueOf(scope.getValue("productIdentification")));
        }
    }

    private long queryProductId(MapSqlParameterSource scope) {
        List<Long> ids = jdbc.query("""
                SELECT id FROM product
                WHERE tenant_id = :tenantId AND product_identification = :productIdentification
                """, scope, (rs, rowNum) -> rs.getLong(1));
        if (ids.size() != 1) {
            throw contractError(ids.isEmpty() ? "MODEL_PRODUCT_NOT_FOUND" : "MODEL_PRODUCT_SCOPE_AMBIGUOUS",
                    String.valueOf(scope.getValue("productIdentification")));
        }
        return ids.get(0);
    }

    private void deleteExistingModel(MapSqlParameterSource scope) {
        jdbc.update("""
                DELETE FROM product_event_response response
                USING product_event event
                WHERE response.event_id = event.id
                  AND response.tenant_id = :tenantId AND event.tenant_id = :tenantId
                  AND event.product_identification = :productIdentification
                """, scope);
        jdbc.update("""
                DELETE FROM product_event
                WHERE tenant_id = :tenantId AND product_identification = :productIdentification
                """, scope);
        jdbc.update("""
                DELETE FROM product_commands_requests request
                USING product_commands command, product_services service
                WHERE request.commands_id = command.id AND command.service_id = service.id
                  AND request.tenant_id = :tenantId AND command.tenant_id = :tenantId
                  AND service.tenant_id = :tenantId
                  AND service.product_identification = :productIdentification
                """, scope);
        jdbc.update("""
                DELETE FROM product_commands_response response
                USING product_commands command, product_services service
                WHERE response.commands_id = command.id AND command.service_id = service.id
                  AND response.tenant_id = :tenantId AND command.tenant_id = :tenantId
                  AND service.tenant_id = :tenantId
                  AND service.product_identification = :productIdentification
                """, scope);
        jdbc.update("""
                DELETE FROM product_commands command
                USING product_services service
                WHERE command.service_id = service.id
                  AND command.tenant_id = :tenantId AND service.tenant_id = :tenantId
                  AND service.product_identification = :productIdentification
                """, scope);
        jdbc.update("""
                DELETE FROM product_services
                WHERE tenant_id = :tenantId AND product_identification = :productIdentification
                """, scope);
        jdbc.update("""
                DELETE FROM product_properties
                WHERE tenant_id = :tenantId AND product_identification = :productIdentification
                """, scope);
    }

    private void insertRuntimeRows(ObjectNode tables, long tenantId, String productIdentification) {
        for (JsonNode value : requiredArray(tables, "product_properties")) {
            ObjectNode row = (ObjectNode) value;
            jdbc.update("""
                    INSERT INTO product_properties
                    (id, property_name, property_code, datatype, description, enumlist, "max", maxlength,
                     "method", "min", required, step, unit, template_identification,
                     product_identification, tenant_id)
                    VALUES (:id, :propertyName, :propertyCode, :datatype, :description, :enumlist, :max,
                            :maxlength, :method, :min, :required, :step, :unit, NULL,
                            :productIdentification, :tenantId)
                    """, rowParameters(row, tenantId, productIdentification,
                    "propertyName", "propertyCode", "datatype", "description", "enumlist", "max",
                    "maxlength", "method", "min", "required", "step", "unit"));
        }
        for (JsonNode value : requiredArray(tables, "product_services")) {
            ObjectNode row = (ObjectNode) value;
            jdbc.update("""
                    INSERT INTO product_services
                    (id, service_code, service_name, template_identification, product_identification,
                     status, description, tenant_id)
                    VALUES (:id, :serviceCode, :serviceName, NULL, :productIdentification,
                            :status, :description, :tenantId)
                    """, rowParameters(row, tenantId, productIdentification,
                    "serviceCode", "serviceName", "status", "description"));
        }
        for (JsonNode value : requiredArray(tables, "product_commands")) {
            ObjectNode row = (ObjectNode) value;
            jdbc.update("""
                    INSERT INTO product_commands
                    (id, service_id, name, description, command_code, tenant_id)
                    VALUES (:id, :serviceId, :name, :description, :commandCode, :tenantId)
                    """, rowParameters(row, tenantId, productIdentification,
                    "serviceId", "name", "description", "commandCode"));
        }
        insertCommandParameters(requiredArray(tables, "product_commands_requests"), tenantId,
                productIdentification, "product_commands_requests");
        insertCommandParameters(requiredArray(tables, "product_commands_response"), tenantId,
                productIdentification, "product_commands_response");
        for (JsonNode value : requiredArray(tables, "product_event")) {
            ObjectNode row = (ObjectNode) value;
            jdbc.update("""
                    INSERT INTO product_event
                    (id, event_name, event_code, event_type, template_identification,
                     product_identification, status, description, tenant_id)
                    VALUES (:id, :eventName, :eventCode, :eventType, NULL,
                            :productIdentification, :status, :description, :tenantId)
                    """, rowParameters(row, tenantId, productIdentification,
                    "eventName", "eventCode", "eventType", "status", "description"));
        }
        for (JsonNode value : requiredArray(tables, "product_event_response")) {
            ObjectNode row = (ObjectNode) value;
            jdbc.update("""
                    INSERT INTO product_event_response
                    (id, event_id, service_id, datatype, enumlist, "max", maxlength, "min",
                     parameter_description, parameter_name, required, step, unit, tenant_id)
                    VALUES (:id, :eventId, :serviceId, :datatype, :enumlist, :max, :maxlength, :min,
                            :parameterDescription, :parameterName, :required, :step, :unit, :tenantId)
                    """, rowParameters(row, tenantId, productIdentification,
                    "eventId", "serviceId", "datatype", "enumlist", "max", "maxlength", "min",
                    "parameterDescription", "parameterName", "required", "step", "unit"));
        }
    }

    private void insertCommandParameters(ArrayNode rows, long tenantId, String productIdentification,
                                         String table) {
        requireCommandParameterTable(table);
        String sql = "INSERT INTO " + table + " " +
                "(id, service_id, commands_id, datatype, enumlist, \"max\", maxlength, \"min\", " +
                "parameter_description, parameter_name, required, step, unit, parameter_code, tenant_id) " +
                "VALUES (:id, :serviceId, :commandsId, :datatype, :enumlist, :max, :maxlength, :min, " +
                ":parameterDescription, :parameterName, :required, :step, :unit, :parameterCode, :tenantId)";
        for (JsonNode value : rows) {
            ObjectNode row = (ObjectNode) value;
            jdbc.update(sql, rowParameters(row, tenantId, productIdentification,
                    "serviceId", "commandsId", "datatype", "enumlist", "max", "maxlength", "min",
                    "parameterDescription", "parameterName", "required", "step", "unit", "parameterCode"));
        }
    }

    private ArrayNode queryRootProperties(MapSqlParameterSource scope) {
        return queryRows("""
                SELECT id, property_name, property_code, datatype, description, enumlist, "max", maxlength,
                       "method", "min", required, step, unit, template_identification,
                       product_identification, tenant_id
                FROM product_properties
                WHERE tenant_id = :tenantId AND product_identification = :productIdentification
                  AND template_identification IS NULL
                ORDER BY id
                """, scope, (rs, row) -> {
            row.put("id", rs.getLong("id"));
            putText(row, rs, "propertyCode", "property_code");
            putText(row, rs, "propertyName", "property_name");
            putText(row, rs, "datatype", "datatype");
            putText(row, rs, "description", "description");
            putText(row, rs, "enumlist", "enumlist");
            putText(row, rs, "max", "max");
            putLong(row, rs, "maxlength", "maxlength");
            putText(row, rs, "method", "method");
            putText(row, rs, "min", "min");
            putInteger(row, rs, "required", "required");
            putInteger(row, rs, "step", "step");
            putText(row, rs, "unit", "unit");
            putTextOrNull(row, rs, "templateIdentification", "template_identification");
            putText(row, rs, "productIdentification", "product_identification");
            row.put("tenantId", rs.getLong("tenant_id"));
        });
    }

    private ArrayNode queryServices(MapSqlParameterSource scope) {
        return queryRows("""
                SELECT id, service_code, service_name, status, description,
                       template_identification, product_identification, tenant_id
                FROM product_services
                WHERE tenant_id = :tenantId AND product_identification = :productIdentification
                  AND template_identification IS NULL
                ORDER BY id
                """, scope, (rs, row) -> {
            row.put("id", rs.getLong("id"));
            putText(row, rs, "serviceCode", "service_code");
            putText(row, rs, "serviceName", "service_name");
            putText(row, rs, "status", "status");
            putText(row, rs, "description", "description");
            putTextOrNull(row, rs, "templateIdentification", "template_identification");
            putText(row, rs, "productIdentification", "product_identification");
            row.put("tenantId", rs.getLong("tenant_id"));
        });
    }

    private ArrayNode queryCommands(MapSqlParameterSource scope) {
        return queryRows("""
                SELECT command.id, command.service_id, command.name, command.description,
                       command.command_code, command.tenant_id
                FROM product_commands command
                JOIN product_services service
                  ON service.id = command.service_id AND service.tenant_id = command.tenant_id
                WHERE command.tenant_id = :tenantId AND service.tenant_id = :tenantId
                  AND service.product_identification = :productIdentification
                ORDER BY command.id
                """, scope, (rs, row) -> {
            row.put("id", rs.getLong("id"));
            row.put("serviceId", rs.getLong("service_id"));
            putText(row, rs, "name", "name");
            putText(row, rs, "description", "description");
            putText(row, rs, "commandCode", "command_code");
            row.put("tenantId", rs.getLong("tenant_id"));
        });
    }

    private ArrayNode queryCommandRequests(MapSqlParameterSource scope) {
        return queryCommandParameters(scope, "product_commands_requests");
    }

    private ArrayNode queryCommandResponses(MapSqlParameterSource scope) {
        return queryCommandParameters(scope, "product_commands_response");
    }

    private ArrayNode queryCommandParameters(MapSqlParameterSource scope, String table) {
        requireCommandParameterTable(table);
        String sql = "SELECT parameter.id, parameter.service_id, parameter.commands_id, parameter.datatype, " +
                "parameter.enumlist, parameter.\"max\", parameter.maxlength, parameter.\"min\", " +
                "parameter.parameter_description, parameter.parameter_name, parameter.required, " +
                "parameter.step, parameter.unit, parameter.parameter_code, parameter.tenant_id " +
                "FROM " + table + " parameter " +
                "JOIN product_commands command ON command.id = parameter.commands_id " +
                "AND command.tenant_id = parameter.tenant_id " +
                "JOIN product_services service ON service.id = command.service_id " +
                "AND service.tenant_id = command.tenant_id " +
                "WHERE parameter.tenant_id = :tenantId AND command.tenant_id = :tenantId " +
                "AND service.tenant_id = :tenantId " +
                "AND service.product_identification = :productIdentification ORDER BY parameter.id";
        return queryRows(sql, scope, (rs, row) -> {
            row.put("id", rs.getLong("id"));
            row.put("serviceId", rs.getLong("service_id"));
            row.put("commandsId", rs.getLong("commands_id"));
            putText(row, rs, "datatype", "datatype");
            putText(row, rs, "enumlist", "enumlist");
            putText(row, rs, "max", "max");
            putText(row, rs, "maxlength", "maxlength");
            putText(row, rs, "min", "min");
            putText(row, rs, "parameterDescription", "parameter_description");
            putText(row, rs, "parameterName", "parameter_name");
            putText(row, rs, "required", "required");
            putText(row, rs, "step", "step");
            putText(row, rs, "unit", "unit");
            putText(row, rs, "parameterCode", "parameter_code");
            row.put("tenantId", rs.getLong("tenant_id"));
        });
    }

    private ArrayNode queryEvents(MapSqlParameterSource scope) {
        return queryRows("""
                SELECT id, event_name, event_code, event_type, status, description,
                       template_identification, product_identification, tenant_id
                FROM product_event
                WHERE tenant_id = :tenantId AND product_identification = :productIdentification
                  AND template_identification IS NULL
                ORDER BY id
                """, scope, (rs, row) -> {
            row.put("id", rs.getLong("id"));
            putText(row, rs, "eventName", "event_name");
            putText(row, rs, "eventCode", "event_code");
            putText(row, rs, "eventType", "event_type");
            putText(row, rs, "status", "status");
            putText(row, rs, "description", "description");
            putTextOrNull(row, rs, "templateIdentification", "template_identification");
            putText(row, rs, "productIdentification", "product_identification");
            row.put("tenantId", rs.getLong("tenant_id"));
        });
    }

    private ArrayNode queryEventResponses(MapSqlParameterSource scope) {
        return queryRows("""
                SELECT response.id, response.event_id, response.service_id, response.datatype,
                       response.enumlist, response."max", response.maxlength, response."min",
                       response.parameter_description, response.parameter_name, response.required,
                       response.step, response.unit, response.tenant_id
                FROM product_event_response response
                JOIN product_event event
                  ON event.id = response.event_id AND event.tenant_id = response.tenant_id
                WHERE response.tenant_id = :tenantId AND event.tenant_id = :tenantId
                  AND event.product_identification = :productIdentification
                ORDER BY response.id
                """, scope, (rs, row) -> {
            row.put("id", rs.getLong("id"));
            row.put("eventId", rs.getLong("event_id"));
            putLongOrNull(row, rs, "serviceId", "service_id");
            putText(row, rs, "datatype", "datatype");
            putText(row, rs, "enumlist", "enumlist");
            putText(row, rs, "max", "max");
            putText(row, rs, "maxlength", "maxlength");
            putText(row, rs, "min", "min");
            putText(row, rs, "parameterDescription", "parameter_description");
            putText(row, rs, "parameterName", "parameter_name");
            putText(row, rs, "required", "required");
            putText(row, rs, "step", "step");
            putText(row, rs, "unit", "unit");
            row.put("tenantId", rs.getLong("tenant_id"));
        });
    }

    private ArrayNode queryRows(String sql, MapSqlParameterSource scope, RuntimeRowMapper mapper) {
        ArrayNode rows = objectMapper.createArrayNode();
        jdbc.query(sql, scope, rs -> {
            ObjectNode row = rows.addObject();
            mapper.map(rs, row);
        });
        return rows;
    }

    private MapSqlParameterSource rowParameters(ObjectNode row, long tenantId, String productIdentification,
                                                String... fields) {
        MapSqlParameterSource parameters = new MapSqlParameterSource();
        parameters.addValue("id", requiredLong(row, "id"));
        parameters.addValue("tenantId", tenantId);
        parameters.addValue("productIdentification", productIdentification);
        for (String field : fields) {
            parameters.addValue(field, scalarValue(row.get(field)));
        }
        return parameters;
    }

    private Object scalarValue(JsonNode value) {
        if (value == null || value.isNull()) {
            return null;
        }
        if (value.isIntegralNumber()) {
            return value.longValue();
        }
        if (value.isFloatingPointNumber()) {
            return value.decimalValue();
        }
        if (value.isBoolean()) {
            return value.booleanValue();
        }
        return value.asText();
    }

    private MapSqlParameterSource scope(long tenantId, String productIdentification) {
        return new MapSqlParameterSource()
                .addValue("tenantId", tenantId)
                .addValue("productIdentification", productIdentification);
    }

    private void requireCommandParameterTable(String table) {
        if (!"product_commands_requests".equals(table)
                && !"product_commands_response".equals(table)) {
            throw contractError("MODEL_RUNTIME_TABLE_INVALID", table);
        }
    }

    private ArrayNode requiredArray(ObjectNode source, String field) {
        JsonNode value = source.get(field);
        if (value == null || !value.isArray()) {
            throw contractError("MODEL_RUNTIME_TABLE_REQUIRED", field);
        }
        return (ArrayNode) value;
    }

    private ObjectNode requireObject(JsonNode value, String code) {
        if (value == null || !value.isObject()) {
            throw contractError(code, "object");
        }
        return (ObjectNode) value;
    }

    private String requiredText(ObjectNode source, String field) {
        JsonNode value = source.get(field);
        if (value == null || value.isNull() || value.asText().isBlank()) {
            throw contractError("MODEL_FIELD_REQUIRED", field);
        }
        return value.asText().trim();
    }

    private long requiredLong(ObjectNode source, String field) {
        JsonNode value = source.get(field);
        if (value == null || value.isNull()) {
            throw contractError("MODEL_FIELD_REQUIRED", field);
        }
        try {
            return Long.parseLong(value.asText());
        } catch (NumberFormatException error) {
            throw contractError("MODEL_LONG_REQUIRED", field);
        }
    }

    private boolean hasValue(ObjectNode source, String field) {
        return source.has(field) && !source.get(field).isNull();
    }

    private void requireCapability() {
        if (!capabilityService.isEnabled(CAPABILITY_CODE)) {
            throw exception(CAPABILITY_NOT_SUPPORTED, CAPABILITY_CODE);
        }
    }

    private ServiceException translateAdapterError(IllegalArgumentException error) {
        String message = error.getMessage() == null ? "unknown" : error.getMessage();
        String code = message.contains(":") ? message.substring(0, message.indexOf(':')) : message;
        String detail = message.contains(":") ? message.substring(message.indexOf(':') + 1).trim() : message;
        return contractError(code, detail);
    }

    private ServiceException contractError(String code, String detail) {
        switch (code) {
            case "MODEL_TENANT_MISMATCH":
                return exception(MODEL_TENANT_MISMATCH, detail);
            case "MODEL_SERVICE_PARAM_RELATION_INVALID":
                return exception(MODEL_SERVICE_PARAM_RELATION_INVALID, detail);
            case "MODEL_PRODUCT_NOT_FOUND":
                return exception(MODEL_PRODUCT_NOT_FOUND, detail);
            case "MODEL_PRODUCT_SCOPE_AMBIGUOUS":
                return exception(MODEL_PRODUCT_SCOPE_AMBIGUOUS, detail);
            case "MODEL_VERSION_UNSUPPORTED":
                return exception(MODEL_VERSION_UNSUPPORTED, detail);
            case "MODEL_FIELD_REQUIRED":
                return exception(MODEL_FIELD_REQUIRED, detail);
            case "MODEL_LONG_REQUIRED":
                return exception(MODEL_LONG_REQUIRED, detail);
            case "MODEL_RUNTIME_SCOPE_INVALID":
                return exception(MODEL_RUNTIME_SCOPE_INVALID, detail);
            case "MODEL_RUNTIME_ID_DUPLICATE":
                return exception(MODEL_RUNTIME_ID_DUPLICATE, detail);
            default:
                return exception(MODEL_RUNTIME_CONTRACT_INVALID, code + ": " + detail);
        }
    }

    private static void putText(ObjectNode row, ResultSet rs, String field, String column) throws SQLException {
        String value = rs.getString(column);
        if (value != null) {
            row.put(field, value);
        }
    }

    private static void putTextOrNull(ObjectNode row, ResultSet rs, String field, String column)
            throws SQLException {
        String value = rs.getString(column);
        if (value == null) {
            row.putNull(field);
        } else {
            row.put(field, value);
        }
    }

    private static void putLong(ObjectNode row, ResultSet rs, String field, String column) throws SQLException {
        long value = rs.getLong(column);
        if (!rs.wasNull()) {
            row.put(field, value);
        }
    }

    private static void putLongOrNull(ObjectNode row, ResultSet rs, String field, String column)
            throws SQLException {
        long value = rs.getLong(column);
        if (rs.wasNull()) {
            row.putNull(field);
        } else {
            row.put(field, value);
        }
    }

    private static void putInteger(ObjectNode row, ResultSet rs, String field, String column)
            throws SQLException {
        int value = rs.getInt(column);
        if (!rs.wasNull()) {
            row.put(field, value);
        }
    }

    @FunctionalInterface
    private interface RuntimeRowMapper {
        void map(ResultSet resultSet, ObjectNode row) throws SQLException;
    }
}
