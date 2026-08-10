package com.basiclab.iot.device.service.event;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.ibatis.datasource.pooled.PooledDataSource;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/** TD-001 1.0.13 / V007：第四协调端口的真实 PostgreSQL 原子合同。 */
class JdbcCollectorConfigReleasePortPostgresIntegrationTest {

    private static final long TENANT = 920_007_001L;
    private static final long SITE = 920_007_002L;
    private static final long NODE = 920_007_003L;
    private static final long TEMPLATE = 920_007_004L;
    private static final long TEMPLATE_VERSION = 920_007_005L;
    private static final long BINDING_OLD = 920_007_006L;
    private static final long BINDING_NEW = 920_007_007L;
    private static final long AUDIT_OLD = 920_007_008L;
    private static final long AUDIT_NEW = 920_007_009L;
    private static final long OUTBOX_OLD = 920_007_010L;
    private static final long OUTBOX_NEW = 920_007_011L;
    private static final long RELEASE_OLD = 920_007_012L;
    private static final long RELEASE_NEW = 920_007_013L;
    private static final long PROJECTION = 920_007_014L;
    private static final long ACTOR = 920_007_015L;
    private static final String EVENT_OLD = "00000000-0000-0000-0000-000092000710";
    private static final String EVENT_NEW = "00000000-0000-0000-0000-000092000711";
    private static final String AUDIT_EVENT_OLD = "00000000-0000-0000-0000-000092000708";
    private static final String AUDIT_EVENT_NEW = "00000000-0000-0000-0000-000092000709";

    @Test
    void beanIsFailClosedByDefault() {
        ConditionalOnProperty condition = JdbcCollectorConfigReleasePort.class
                .getAnnotation(ConditionalOnProperty.class);
        assertNotNull(condition);
        assertEquals("easyaiot.power-model", condition.prefix());
        assertEquals("collector-release-port-enabled", condition.name()[0]);
        assertEquals("true", condition.havingValue());
        assertFalse(condition.matchIfMissing());
    }

    @Test
    void validatedCandidateAndProjectionAdvanceAtomically() {
        assumeTrue(Boolean.parseBoolean(System.getenv("TD007_PG_ENABLED")),
                "Set TD007_PG_ENABLED=true to run the V007 PostgreSQL contract");
        String password = System.getenv("TD005_PG_PASSWORD");
        assumeTrue(password != null && !password.isEmpty(),
                "Set TD005_PG_PASSWORD without committing credentials");
        String url = environmentOrDefault("TD007_PG_URL",
                "jdbc:postgresql://localhost:5432/iot-device20");
        String username = environmentOrDefault("TD005_PG_USERNAME", "postgres");

        PooledDataSource dataSource = new PooledDataSource(
                "org.postgresql.Driver", url, username, password);
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        assertNotNull(jdbc.queryForObject(
                "SELECT to_regclass('public.iot_collector_config_release')", String.class));
        assertEquals(1, jdbc.queryForObject("SELECT count(*) FROM information_schema.columns"
                + " WHERE table_schema='public' AND table_name='iot_collector_config_release'"
                + " AND column_name='source_event_id'", Integer.class));

        ObjectMapper mapper = new ObjectMapper();
        DataSourceTransactionManager manager = new DataSourceTransactionManager(dataSource);
        TransactionTemplate outer = new TransactionTemplate(manager);
        outer.executeWithoutResult(status -> {
            long productId = insertFixtures(jdbc, mapper);
            JdbcCollectorConfigReleasePort port =
                    new JdbcCollectorConfigReleasePort(dataSource, mapper, manager);

            assertFalse(port.desiredMatches(TENANT, "wl-v007", "tpl-v007", "1.2.0", 5));
            port.createRegenerationDraft("wl-v007", TENANT, productId, "tpl-v007", "1.2.0",
                    5, "BINDING_APPLIED", EVENT_NEW, ACTOR);
            assertTrue(port.desiredMatches(TENANT, "wl-v007", "tpl-v007", "1.2.0", 5));

            assertEquals("PUBLISHED:" + ACTOR,
                    jdbc.queryForObject("SELECT status||':'||published_by"
                                    + " FROM public.iot_collector_config_release WHERE id=?",
                            String.class, RELEASE_NEW));
            assertEquals(RELEASE_NEW + ":2:5:2",
                    jdbc.queryForObject("SELECT release_id||':'||config_version||':'"
                                    + "||binding_revision||':'||projection_revision"
                                    + " FROM public.collector_workload_binding_projection WHERE id=?",
                            String.class, PROJECTION));

            IllegalArgumentException missing = assertThrows(IllegalArgumentException.class,
                    () -> port.createRegenerationDraft("wl-v007", TENANT, productId,
                            "tpl-v007", "1.2.0", 5, "BINDING_APPLIED",
                            UUID.randomUUID().toString(), ACTOR));
            assertTrue(missing.getMessage().startsWith(
                    CollectorConfigSnapshotContract.CODE_FACT_MISSING));
            status.setRollbackOnly();
        });

        assertEquals(0, jdbc.queryForObject(
                "SELECT count(*) FROM public.iot_collector_config_release WHERE tenant_id=?",
                Integer.class, TENANT));
        assertEquals(0, jdbc.queryForObject(
                "SELECT count(*) FROM public.power_product_model_binding WHERE tenant_id=?",
                Integer.class, TENANT));
        dataSource.forceCloseAll();
    }

    private static long insertFixtures(JdbcTemplate jdbc, ObjectMapper mapper) {
        String productIdentification = "td007-" + UUID.randomUUID().toString().replace("-", "");
        Long productId = jdbc.queryForObject("INSERT INTO public.product"
                        + " (app_id,product_name,product_identification,product_type,manufacturer_id,"
                        + " manufacturer_name,model,data_format,device_type,protocol_type,status,tenant_id)"
                        + " VALUES ('td007','TD007 fixture',?,'COMMON','td007','TD007','v007','JSON',"
                        + " 'COMMON','MQTT','0',?) RETURNING id",
                Long.class, productIdentification, TENANT);
        if (productId == null) throw new IllegalStateException("product id missing");

        jdbc.update("INSERT INTO public.power_model_template"
                        + " (id,tenant_id,template_code,template_name,device_type,template_kind,owner_scope)"
                        + " VALUES (?,?, 'tpl-v007','V007模板','METER','STANDARD','TENANT')",
                TEMPLATE, TENANT);
        jdbc.update("INSERT INTO public.power_model_template_version"
                        + " (id,tenant_id,template_id,version,major,minor,patch,lifecycle,schema_version,"
                        + " canonicalization_version,hash_algorithm,content_canonical,content_json,"
                        + " content_hash,source_type,published_by,published_at)"
                        + " VALUES (?,?,?,'1.2.0',1,2,0,'PUBLISHED','1.0.0','jcs-rfc8785-v1',"
                        + " 'SHA-256','{}','{}'::jsonb,?,'UI',?,CURRENT_TIMESTAMP)",
                TEMPLATE_VERSION, TENANT, TEMPLATE, "sha256:" + "1".repeat(64),
                Long.toString(ACTOR));
        insertBinding(jdbc, BINDING_OLD, productId, productIdentification, 4,
                "SUPERSEDED", null, true);
        insertBinding(jdbc, BINDING_NEW, productId, productIdentification, 5,
                "ACTIVE", BINDING_OLD, false);
        insertAuditAndOutbox(jdbc, AUDIT_OLD, AUDIT_EVENT_OLD, OUTBOX_OLD, EVENT_OLD,
                productId, productIdentification, BINDING_OLD, 4);
        insertAuditAndOutbox(jdbc, AUDIT_NEW, AUDIT_EVENT_NEW, OUTBOX_NEW, EVENT_NEW,
                productId, productIdentification, BINDING_NEW, 5);

        CollectorConfigSnapshotContract contract = new CollectorConfigSnapshotContract();
        insertRelease(jdbc, mapper, contract, RELEASE_OLD, productId, 4, EVENT_OLD, 1,
                "PUBLISHED", true);
        insertRelease(jdbc, mapper, contract, RELEASE_NEW, productId, 5, EVENT_NEW, 2,
                "VALIDATED", false);
        jdbc.update("INSERT INTO public.collector_workload_binding_projection"
                        + " (id,tenant_id,workload_id,site_id,site_code,node_id,product_id,template_code,"
                        + " template_version,binding_revision,config_version,release_id,"
                        + " projection_revision,lifecycle_status)"
                        + " VALUES (?,?, 'wl-v007',?,'site-v007',?,?,'tpl-v007','1.2.0',4,1,?,1,'ACTIVE')",
                PROJECTION, TENANT, SITE, NODE, productId, RELEASE_OLD);
        return productId;
    }

    private static void insertBinding(JdbcTemplate jdbc, long id, long productId,
                                      String productIdentification, long revision, String status,
                                      Long previous, boolean closed) {
        jdbc.update("INSERT INTO public.power_product_model_binding"
                        + " (id,tenant_id,product_id,product_identification,binding_revision,status,"
                        + " template_version_id,template_code,template_version,content_hash,"
                        + " binding_snapshot_canonical,binding_snapshot_json,binding_snapshot_hash,"
                        + " previous_binding_id,effective_from,effective_to,created_by)"
                        + " VALUES (?,?,?,?,?,?,?,'tpl-v007','1.2.0',?,'{}','{}'::jsonb,?,?,"
                        + " CURRENT_TIMESTAMP,CASE WHEN ? THEN CURRENT_TIMESTAMP ELSE NULL END,?)",
                id, TENANT, productId, productIdentification, revision, status, TEMPLATE_VERSION,
                "sha256:" + "1".repeat(64), "sha256:" + (revision == 4 ? "2" : "3").repeat(64),
                previous, closed, Long.toString(ACTOR));
    }

    private static void insertAuditAndOutbox(JdbcTemplate jdbc, long auditId, String auditEvent,
                                             long outboxId, String eventId, long productId,
                                             String productIdentification, long bindingId,
                                             long revision) {
        jdbc.update("INSERT INTO public.power_model_audit"
                        + " (id,audit_event_id,tenant_id,operation,aggregate_type,aggregate_id,"
                        + " template_code,template_version,product_id,product_identification,"
                        + " binding_revision,principal_type,principal_id,request_id)"
                        + " VALUES (?,CAST(? AS uuid),?,'BINDING_APPLIED','power_product_model_binding',"
                        + " ?,'tpl-v007','1.2.0',?,?,?,'USER',?,'req-v007')",
                auditId, auditEvent, TENANT, Long.toString(bindingId), productId,
                productIdentification, revision, Long.toString(ACTOR));
        jdbc.update("INSERT INTO public.power_model_release_outbox"
                        + " (id,event_id,tenant_id,audit_event_id,aggregate_type,aggregate_id,event_type,"
                        + " payload,payload_hash) VALUES (?,CAST(? AS uuid),?,CAST(? AS uuid),"
                        + " 'power_product_model_binding',?,'POWER_PRODUCT_MODEL_BINDING_APPLIED_V1',"
                        + " '{}'::jsonb,?)",
                outboxId, eventId, TENANT, auditEvent, Long.toString(bindingId),
                "sha256:" + "4".repeat(64));
    }

    private static void insertRelease(JdbcTemplate jdbc, ObjectMapper mapper,
                                      CollectorConfigSnapshotContract contract, long releaseId,
                                      long productId, long revision, String eventId,
                                      long configVersion, String status, boolean published) {
        try {
            JsonNode root = mapper.readTree(snapshot(configVersion));
            CollectorConfigSnapshotContract.Artifact artifact =
                    contract.validateAndCanonicalize(root);
            jdbc.update("INSERT INTO public.iot_collector_config_release"
                            + " (id,tenant_id,site_id,site_code,workload_id,node_id,config_version,"
                            + " schema_version,canonicalization_version,payload_canonical,payload,"
                            + " payload_sha256,canonical_length_bytes,status,published_by,published_at,"
                            + " product_id,template_code,template_version,binding_revision,source_event_id,"
                            + " source_reason_code) VALUES (?,?,?,'site-v007','wl-v007',?,?,?, ?,?,"
                            + " CAST(? AS jsonb),?,?,?,CASE WHEN ? THEN ? ELSE NULL END,"
                            + " CASE WHEN ? THEN CURRENT_TIMESTAMP ELSE NULL END,?,'tpl-v007','1.2.0',?,"
                            + " CAST(? AS uuid),'BINDING_APPLIED')",
                    releaseId, TENANT, SITE, NODE, configVersion,
                    CollectorConfigSnapshotContract.SCHEMA_VERSION,
                    CollectorConfigSnapshotContract.CANONICALIZATION_VERSION,
                    artifact.canonical(), artifact.canonical(), artifact.sha256(),
                    artifact.lengthBytes(), status, published, ACTOR, published,
                    productId, revision, eventId);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private static String snapshot(long version) {
        return "{\"schemaVersion\":\"1.0\",\"workloadId\":\"wl-v007\","
                + "\"tenantId\":\"" + TENANT + "\",\"siteId\":\"" + SITE + "\","
                + "\"siteCode\":\"site-v007\",\"configVersion\":" + version + ","
                + "\"generatedAt\":\"2026-08-10T14:00:00+08:00\",\"serialBuses\":[{"
                + "\"busId\":\"bus-a\",\"serialPort\":\"/dev/easyaiot/rs485-0\","
                + "\"baudRate\":9600,\"dataBits\":8,\"stopBits\":\"1\",\"parity\":\"NONE\","
                + "\"transmitDelayMs\":0,\"rs485Mode\":true,\"devices\":[{"
                + "\"deviceId\":\"920007100\",\"deviceIdentification\":\"METER-V007\","
                + "\"unitId\":1,\"pollIntervalMs\":5000,\"requestTimeoutMs\":1000,"
                + "\"maxRetries\":2,\"points\":[{\"propertyCode\":\"active-power\","
                + "\"function\":\"HOLDING_REGISTER\",\"address\":0,\"quantity\":2,"
                + "\"dataType\":\"FLOAT32\",\"byteOrder\":\"BIG_ENDIAN\","
                + "\"wordOrder\":\"BIG_ENDIAN\",\"scale\":\"1\",\"offset\":\"0\","
                + "\"dataPriority\":\"METERING_TOTAL\",\"writable\":false,"
                + "\"pollGroup\":\"normal\"}]}]}]}";
    }

    private static String environmentOrDefault(String name, String fallback) {
        String value = System.getenv(name);
        return value == null || value.isEmpty() ? fallback : value;
    }
}
