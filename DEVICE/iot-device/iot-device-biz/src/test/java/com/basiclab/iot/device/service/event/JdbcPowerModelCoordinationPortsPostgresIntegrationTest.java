package com.basiclab.iot.device.service.event;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;

import java.sql.Connection;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * TD-001 §6.2 / ADR-015：三个已实现协调端口的真实 PostgreSQL 合同。
 * 所有 fixture 位于同一连接事务并在 tearDown 回滚，包含追加写审计表在内均不残留。
 * V003～V007 必须先由 ADR-013 runner 落库，本测试不创建数据库对象。
 */
class JdbcPowerModelCoordinationPortsPostgresIntegrationTest {

    private static final long TENANT = 910_005_203L;
    private static final long PRODUCT = 910_005_204L;
    private static final long RELEASE_ID = 910_005_205L;
    private static final long TEMPLATE = 910_005_208L;
    private static final long TEMPLATE_VERSION = 910_005_209L;
    private static final long BINDING = 910_005_210L;
    private static final long AUDIT = 910_005_215L;
    private static final long OUTBOX = 910_005_216L;
    private static final String HASH_64 =
            "0000000000000000000000000000000000000000000000000000000000000000";
    private static final String SHA256 = "sha256:" + HASH_64;

    private SingleConnectionDataSource dataSource;
    private Connection connection;
    private JdbcTemplate jdbc;

    @BeforeEach
    void setUp() throws Exception {
        assumeTrue(Boolean.parseBoolean(System.getenv("TD005_PG_ENABLED")),
                "Set TD005_PG_ENABLED=true to run the PostgreSQL coordination-port contracts");
        String password = System.getenv("TD005_PG_PASSWORD");
        assumeTrue(password != null && !password.isEmpty(),
                "Set TD005_PG_PASSWORD without committing credentials");
        String url = environmentOrDefault("TD005_PG_URL",
                "jdbc:postgresql://localhost:5432/td005_contract_review");
        String username = environmentOrDefault("TD005_PG_USERNAME", "postgres");

        dataSource = new SingleConnectionDataSource(url, username, password, true);
        connection = dataSource.getConnection();
        connection.setAutoCommit(false);
        jdbc = new JdbcTemplate(dataSource);

        assertNotNull(jdbc.queryForObject(
                "SELECT to_regclass('public.power_model_coordination_audit')", String.class));
        assertNotNull(jdbc.queryForObject(
                "SELECT to_regclass('public.power_model_template_reference_mark')", String.class));
        assertNotNull(jdbc.queryForObject(
                "SELECT to_regclass('public.collector_workload_binding_projection')", String.class));
    }

    @AfterEach
    void tearDown() throws Exception {
        if (connection != null) {
            connection.rollback();
        }
        if (dataSource != null) {
            dataSource.destroy();
        }
    }

    @Test
    void coordinationAuditIsIdempotentAndDetailIsBounded() {
        JdbcPowerModelCoordinationAuditPort port =
                new JdbcPowerModelCoordinationAuditPort(dataSource);
        String eventId = UUID.randomUUID().toString();
        String detail = "x".repeat(600);

        port.record(eventId, TENANT, "POWER_MODEL_TEMPLATE_PUBLISHED_V1", "IMPACT_EMPTY", detail);
        port.record(eventId, TENANT, "POWER_MODEL_TEMPLATE_PUBLISHED_V1", "IMPACT_EMPTY", detail);

        assertEquals(1, jdbc.queryForObject(
                "SELECT count(*) FROM public.power_model_coordination_audit"
                        + " WHERE tenant_id=? AND event_id=CAST(? AS uuid) AND action='IMPACT_EMPTY'",
                Integer.class, TENANT, eventId));
        assertEquals(512, jdbc.queryForObject(
                "SELECT length(detail) FROM public.power_model_coordination_audit"
                        + " WHERE tenant_id=? AND event_id=CAST(? AS uuid)",
                Integer.class, TENANT, eventId));
    }

    @Test
    void templateReferenceUpsertKeepsOneLatestMark() {
        JdbcPowerModelTemplateReferencePort port =
                new JdbcPowerModelTemplateReferencePort(dataSource);
        String firstEvent = UUID.randomUUID().toString();
        String secondEvent = UUID.randomUUID().toString();

        port.markLifecycleReference(TENANT, "tpl-contract", "1.0.0",
                "PUBLISHED", "RETIRED", firstEvent);
        port.markLifecycleReference(TENANT, "tpl-contract", "1.0.0",
                "DEPRECATED", "RETIRED", secondEvent);

        assertEquals(1, jdbc.queryForObject(
                "SELECT count(*) FROM public.power_model_template_reference_mark"
                        + " WHERE tenant_id=? AND template_code='tpl-contract'",
                Integer.class, TENANT));
        assertEquals("DEPRECATED", jdbc.queryForObject(
                "SELECT from_lifecycle FROM public.power_model_template_reference_mark"
                        + " WHERE tenant_id=? AND template_code='tpl-contract'",
                String.class, TENANT));
        assertEquals(secondEvent, jdbc.queryForObject(
                "SELECT source_event_id::text FROM public.power_model_template_reference_mark"
                        + " WHERE tenant_id=? AND template_code='tpl-contract'",
                String.class, TENANT));
    }

    @Test
    void impactResolutionReturnsOnlyActiveTenantProductInStableOrder() {
        insertReleaseDerivationFixture();
        insertProjection(910_005_211L, "workload-b", PRODUCT, "ACTIVE", 1L);
        insertProjection(910_005_212L, "workload-a", PRODUCT, "ACTIVE", 1L);
        insertProjection(910_005_213L, "workload-stopped", PRODUCT, "STOPPED", 2L);
        insertProjection(910_005_214L, "workload-other-product", PRODUCT + 1, "ACTIVE", 1L);

        JdbcCollectorWorkloadImpactPort port = new JdbcCollectorWorkloadImpactPort(dataSource);
        List<String> result = port.resolveActiveWorkloads(TENANT, PRODUCT);

        assertEquals(List.of("workload-a", "workload-b"), result);
        List<String> empty = port.resolveActiveWorkloads(TENANT, PRODUCT + 2);
        assertNotNull(empty);
        assertTrue(empty.isEmpty());
    }

    /** V007 发布单外键链：product/template/version/binding/audit/outbox/release。 */
    private void insertReleaseDerivationFixture() {
        String auditEventId = UUID.randomUUID().toString();
        String sourceEventId = UUID.randomUUID().toString();
        jdbc.update("INSERT INTO public.product"
                        + " (id,app_id,product_name,product_identification,product_type,manufacturer_id,"
                        + " manufacturer_name,model,data_format,device_type,protocol_type,status,tenant_id)"
                        + " VALUES (?,'td001','TD001 fixture','td001-coordination','COMMON','td001',"
                        + " 'TD001','td001','JSON','COMMON','MQTT','0',?)",
                PRODUCT, TENANT);
        jdbc.update("INSERT INTO public.power_model_template"
                        + " (id,tenant_id,template_code,template_name,device_type,template_kind,owner_scope)"
                        + " VALUES (?,?,'tpl-contract','协调端口模板','METER','STANDARD','TENANT')",
                TEMPLATE, TENANT);
        jdbc.update("INSERT INTO public.power_model_template_version"
                        + " (id,tenant_id,template_id,version,major,minor,patch,lifecycle,schema_version,"
                        + " canonicalization_version,hash_algorithm,content_canonical,content_json,"
                        + " content_hash,source_type,published_by,published_at)"
                        + " VALUES (?,?,?,'1.0.0',1,0,0,'PUBLISHED','1.0.0','jcs-rfc8785-v1',"
                        + " 'SHA-256','{}','{}'::jsonb,?,'UI','910005203',CURRENT_TIMESTAMP)",
                TEMPLATE_VERSION, TENANT, TEMPLATE, SHA256);
        jdbc.update("INSERT INTO public.power_product_model_binding"
                        + " (id,tenant_id,product_id,product_identification,binding_revision,status,"
                        + " template_version_id,template_code,template_version,content_hash,"
                        + " binding_snapshot_canonical,binding_snapshot_json,binding_snapshot_hash,"
                        + " effective_from,created_by)"
                        + " VALUES (?,?,?,'td001-coordination',1,'ACTIVE',?,'tpl-contract','1.0.0',?,"
                        + " '{}','{}'::jsonb,?,CURRENT_TIMESTAMP,'910005203')",
                BINDING, TENANT, PRODUCT, TEMPLATE_VERSION, SHA256, SHA256);
        jdbc.update("INSERT INTO public.power_model_audit"
                        + " (id,audit_event_id,tenant_id,operation,aggregate_type,aggregate_id,"
                        + " product_id,product_identification,binding_revision,principal_type,"
                        + " principal_id,request_id,after_hash,diff_summary)"
                        + " VALUES (?,CAST(? AS uuid),?,'BINDING_APPLIED','PRODUCT_MODEL_BINDING',?,"
                        + " ?,'td001-coordination',1,'USER','910005203','td001-coordination',?,'{}'::jsonb)",
                AUDIT, auditEventId, TENANT, Long.toString(BINDING), PRODUCT, SHA256);
        jdbc.update("INSERT INTO public.power_model_release_outbox"
                        + " (id,event_id,tenant_id,audit_event_id,aggregate_type,aggregate_id,event_type,"
                        + " schema_version,payload,payload_hash,status)"
                        + " VALUES (?,CAST(? AS uuid),?,CAST(? AS uuid),'PRODUCT_MODEL_BINDING',?,"
                        + " 'POWER_PRODUCT_MODEL_BINDING_APPLIED_V1',1,'{}'::jsonb,?,'PENDING')",
                OUTBOX, sourceEventId, TENANT, auditEventId, Long.toString(BINDING), SHA256);
        jdbc.update("INSERT INTO public.iot_collector_config_release"
                        + " (id, tenant_id, site_id, site_code, workload_id, node_id, config_version,"
                        + " schema_version, canonicalization_version, payload_canonical, payload,"
                        + " payload_sha256, canonical_length_bytes, status, product_id, template_code,"
                        + " template_version, binding_revision, source_event_id, source_reason_code)"
                        + " VALUES (?, ?, ?, 'site-contract', 'release-workload', ?, 1, '1.0',"
                        + " 'jcs-rfc8785-v1', '{}', CAST('{}' AS jsonb), ?, 2, 'DRAFT', ?,"
                        + " 'tpl-contract', '1.0.0', 1, CAST(? AS uuid), 'BINDING_APPLIED')",
                RELEASE_ID, TENANT, 910_005_206L, 910_005_207L, HASH_64, PRODUCT, sourceEventId);
    }

    private void insertProjection(long id, String workloadId, long productId,
                                  String lifecycle, long revision) {
        jdbc.update("INSERT INTO public.collector_workload_binding_projection"
                        + " (id, tenant_id, workload_id, site_id, site_code, node_id, product_id,"
                        + " template_code, template_version, binding_revision, config_version,"
                        + " release_id, projection_revision, lifecycle_status)"
                        + " VALUES (?, ?, ?, ?, 'site-contract', ?, ?, 'tpl-contract', '1.0.0',"
                        + " 1, 1, ?, ?, ?)",
                id, TENANT, workloadId, 910_005_206L, 910_005_207L, productId,
                RELEASE_ID, revision, lifecycle);
    }

    private static String environmentOrDefault(String name, String fallback) {
        String value = System.getenv(name);
        return value == null || value.isEmpty() ? fallback : value;
    }
}
