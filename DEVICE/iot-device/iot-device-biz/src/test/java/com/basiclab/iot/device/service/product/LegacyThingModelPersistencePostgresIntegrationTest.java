package com.basiclab.iot.device.service.product;

import com.basiclab.iot.common.core.context.TenantContextHolder;
import com.basiclab.iot.common.capability.CapabilityService;
import com.basiclab.iot.common.capability.ManifestCapabilityService;
import com.basiclab.iot.common.exception.ServiceException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.apache.ibatis.datasource.pooled.PooledDataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.Connection;
import java.sql.Savepoint;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/** Real PostgreSQL contract for the TD-005 seven-child-table persistence boundary. */
class LegacyThingModelPersistencePostgresIntegrationTest {

    private static final String CASE_PATH = ".doc/规格/电力运维云平台/assets/model-templates/verification/"
            + "legacy-roundtrip/easyaiot-legacy-thing-model-v1_td005-1.0.10/legacy-input.json";
    private static final long TENANT_ONE = 910_005_101L;
    private static final long TENANT_TWO = 910_005_102L;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private Connection connection;
    private JdbcTemplate jdbc;
    private LegacyThingModelRuntimeAdapter adapter;
    private LegacyThingModelPersistenceService service;
    private String productIdentification;

    @BeforeEach
    void openTransactionAndCreateProduct() throws Exception {
        assumeTrue(Boolean.parseBoolean(System.getenv("TD005_PG_ENABLED")),
                "Set TD005_PG_ENABLED=true to run the PostgreSQL tenant contract");
        String password = System.getenv("TD005_PG_PASSWORD");
        assumeTrue(password != null && !password.isBlank(),
                "Set TD005_PG_PASSWORD without committing credentials");

        String url = environmentOrDefault("TD005_PG_URL", "jdbc:postgresql://localhost:5432/iot-device20");
        String username = environmentOrDefault("TD005_PG_USERNAME", "postgres");
        PooledDataSource pooled = new PooledDataSource("org.postgresql.Driver", url, username, password);
        connection = pooled.getConnection();
        connection.setAutoCommit(false);
        SingleConnectionDataSource dataSource = new SingleConnectionDataSource(connection, true);
        jdbc = new JdbcTemplate(dataSource);
        adapter = new LegacyThingModelRuntimeAdapter(objectMapper);
        service = new LegacyThingModelPersistenceService(dataSource, adapter, objectMapper,
                loadCapability("electric-standard.json"));
        productIdentification = "td005-runtime-" + UUID.randomUUID().toString().replace("-", "");

        jdbc.update("""
                INSERT INTO product
                (app_id, product_name, product_identification, product_type, manufacturer_id,
                 manufacturer_name, model, data_format, device_type, protocol_type, status, tenant_id)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """, "td005-test", "TD005 runtime fixture", productIdentification, "COMMON",
                "td005", "TD005", "test-model", "JSON", "COMMON", "MQTT", "0", TENANT_ONE);
    }

    @AfterEach
    void rollbackAndClearTenant() throws Exception {
        TenantContextHolder.clear();
        if (connection != null) {
            connection.rollback();
            connection.close();
        }
    }

    @Test
    void persistenceMethodsMustDeclareTransactionalBoundaries() throws Exception {
        Transactional replace = LegacyThingModelPersistenceService.class
                .getMethod("replaceRuntimeForCurrentTenant", JsonNode.class)
                .getAnnotation(Transactional.class);
        Transactional export = LegacyThingModelPersistenceService.class
                .getMethod("exportLegacyForCurrentTenant", String.class)
                .getAnnotation(Transactional.class);
        assertNotNull(replace);
        assertTrue(replace.rollbackFor().length > 0);
        assertNotNull(export);
        assertTrue(export.readOnly());
    }

    @Test
    void sevenTableReplaceAndExportMustRoundTripInsideCurrentTenant() throws Exception {
        TenantContextHolder.setTenantId(TENANT_ONE);
        ObjectNode document = fixtureDocument();
        ObjectNode output = service.replaceLegacyDocument(document);

        assertEquals(TENANT_ONE, output.path("tenantId").asLong());
        assertEquals(productIdentification, output.path("productIdentification").asText());
        assertEquals(1, output.path("properties").size());
        assertEquals(1, output.path("services").size());
        assertEquals(1, output.path("services").get(0).path("inputParams").size());
        assertEquals(1, output.path("services").get(0).path("outParams").size());
        assertEquals(1, output.path("events").size());
        assertEquals(1, output.path("events").get(0).path("outParams").size());

        assertEquals(1, count("product_properties"));
        assertEquals(1, count("product_services"));
        assertEquals(1, count("product_commands"));
        assertEquals(1, count("product_commands_requests"));
        assertEquals(1, count("product_commands_response"));
        assertEquals(1, count("product_event"));
        assertEquals(1, count("product_event_response"));

        TenantContextHolder.setTenantId(TENANT_TWO);
        ServiceException denied = assertThrows(ServiceException.class,
                () -> service.exportLegacyForCurrentTenant(productIdentification));
        assertEquals(1_003_023_004, denied.getCode());
        assertTrue(denied.getMessage().startsWith("MODEL_PRODUCT_NOT_FOUND"));
    }

    @Test
    void ten005CrossTenantOrMismatchedServiceParameterMustFailBeforeDelete() throws Exception {
        TenantContextHolder.setTenantId(TENANT_ONE);
        service.replaceLegacyDocument(fixtureDocument());
        assertEquals(1, count("product_properties"));

        ObjectNode crossTenant = adapter.importToRuntime(fixtureDocument());
        ObjectNode request = (ObjectNode) crossTenant.path("tables")
                .path("product_commands_requests").get(0);
        request.put("tenantId", TENANT_TWO);
        ServiceException tenantError = assertThrows(ServiceException.class,
                () -> service.replaceRuntimeForCurrentTenant(crossTenant));
        assertEquals(1_003_023_002, tenantError.getCode());
        assertTrue(tenantError.getMessage().startsWith("MODEL_TENANT_MISMATCH"));
        assertEquals(1, count("product_properties"));

        request.put("tenantId", TENANT_ONE);
        request.put("serviceId", Long.MAX_VALUE);
        ServiceException relationError = assertThrows(ServiceException.class,
                () -> service.replaceRuntimeForCurrentTenant(crossTenant));
        assertEquals(1_003_023_003, relationError.getCode());
        assertTrue(relationError.getMessage().startsWith("MODEL_SERVICE_PARAM_RELATION_INVALID"));
        assertEquals(1, count("product_properties"));
        assertEquals(1, count("product_commands_requests"));
    }

    @Test
    void databaseFailureMustRollbackDeletedAndPartiallyInsertedRowsToSavepoint() throws Exception {
        TenantContextHolder.setTenantId(TENANT_ONE);
        ObjectNode baseline = service.replaceLegacyDocument(fixtureDocument());
        long baselinePropertyId = baseline.path("properties").get(0).path("id").asLong();
        Savepoint beforeReplace = connection.setSavepoint("before_invalid_replace");

        ObjectNode invalidRuntime = adapter.importToRuntime(fixtureDocument());
        ArrayNode properties = (ArrayNode) invalidRuntime.path("tables").path("product_properties");
        ObjectNode invalidProperty = properties.addObject();
        invalidProperty.put("id", Long.parseLong(String.valueOf(System.nanoTime())));
        invalidProperty.putNull("propertyName");
        invalidProperty.put("propertyCode", "invalid_not_null");
        invalidProperty.put("datatype", "DOUBLE");
        invalidProperty.putNull("templateIdentification");
        invalidProperty.put("productIdentification", productIdentification);
        invalidProperty.put("tenantId", TENANT_ONE);

        assertThrows(DataAccessException.class,
                () -> service.replaceRuntimeForCurrentTenant(invalidRuntime));
        connection.rollback(beforeReplace);

        ObjectNode restored = service.exportLegacyForCurrentTenant(productIdentification);
        assertEquals(1, restored.path("properties").size());
        assertEquals(baselinePropertyId, restored.path("properties").get(0).path("id").asLong());
        assertEquals(1, count("product_services"));
        assertEquals(1, count("product_commands_requests"));
        assertEquals(1, count("product_event_response"));
    }

    @Test
    void ten007StandardAndFullMustUseSamePersistenceContract() throws Exception {
        TenantContextHolder.setTenantId(TENANT_ONE);
        ObjectNode standardResult = service.replaceLegacyDocument(fixtureDocument());

        SingleConnectionDataSource dataSource = new SingleConnectionDataSource(connection, true);
        LegacyThingModelPersistenceService fullService = new LegacyThingModelPersistenceService(
                dataSource, adapter, objectMapper, loadCapability("electric-full.json"));
        // Reuse the first result so generated legacy IDs are part of the same request contract.
        ObjectNode fullResult = fullService.replaceLegacyDocument(standardResult);

        // Legacy import allocates fresh surrogate IDs on every replacement; compare stable business semantics.
        assertEquals(withoutGeneratedIds(standardResult), withoutGeneratedIds(fullResult));
        assertEquals(1, count("product_properties"));
        assertEquals(1, count("product_services"));
        assertEquals(1, count("product_commands_requests"));
    }

    @Test
    void ten008MiniMustRejectBeforeReadingOrWritingModelTables() {
        TenantContextHolder.setTenantId(TENANT_ONE);
        SingleConnectionDataSource dataSource = new SingleConnectionDataSource(connection, true);
        LegacyThingModelPersistenceService miniService = new LegacyThingModelPersistenceService(
                dataSource, adapter, objectMapper, ManifestCapabilityService.disabled("mini"));

        ServiceException denied = assertThrows(ServiceException.class,
                () -> miniService.replaceLegacyDocument(objectMapper.createObjectNode()));
        assertEquals(1_003_023_000, denied.getCode());
        assertTrue(denied.getMessage().startsWith("CAPABILITY_NOT_SUPPORTED"));
        assertEquals(0, count("product_properties"));
        assertEquals(0, count("product_services"));
        assertEquals(0, count("product_event"));
    }

    private ObjectNode fixtureDocument() throws IOException {
        ObjectNode document = (ObjectNode) objectMapper.readTree(
                Files.newBufferedReader(findWorkspaceRoot().resolve(CASE_PATH)));
        document.put("tenantId", TENANT_ONE);
        document.put("productIdentification", productIdentification);
        return document;
    }

    private int count(String table) {
        return jdbc.queryForObject("SELECT count(*) FROM " + table + " WHERE tenant_id = ?",
                Integer.class, TENANT_ONE);
    }

    private Path findWorkspaceRoot() {
        Path current = Paths.get(System.getProperty("user.dir")).toAbsolutePath();
        while (current != null) {
            if (Files.isRegularFile(current.resolve(CASE_PATH))) {
                return current;
            }
            current = current.getParent();
        }
        throw new IllegalStateException("找不到冻结 TD-005 legacy round-trip 资产目录");
    }

    private CapabilityService loadCapability(String fileName) throws IOException {
        Path path = findWorkspaceRoot().resolve(".scripts/docker/capabilities").resolve(fileName);
        try (java.io.InputStream input = Files.newInputStream(path)) {
            return ManifestCapabilityService.load(input, objectMapper);
        }
    }

    private JsonNode withoutGeneratedIds(JsonNode source) {
        JsonNode copy = source.deepCopy();
        removeGeneratedIds(copy);
        return copy;
    }

    private void removeGeneratedIds(JsonNode node) {
        if (node.isObject()) {
            ObjectNode object = (ObjectNode) node;
            object.remove(List.of("id", "serviceId", "commandId", "eventId"));
            object.elements().forEachRemaining(this::removeGeneratedIds);
        } else if (node.isArray()) {
            node.elements().forEachRemaining(this::removeGeneratedIds);
        }
    }

    private static String environmentOrDefault(String name, String defaultValue) {
        String value = System.getenv(name);
        return value == null || value.isBlank() ? defaultValue : value;
    }
}
