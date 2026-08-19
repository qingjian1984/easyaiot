package com.basiclab.iot.device.service.collector;

import com.basiclab.iot.device.domain.collector.dto.CollectorConfigReleaseObservedRequestDTO;
import com.basiclab.iot.device.domain.collector.dto.CollectorConfigReleaseObservedStatus;
import com.basiclab.iot.device.service.event.CollectorConfigSnapshotContract;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.ibatis.datasource.pooled.PooledDataSource;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import javax.sql.DataSource;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** OPEN03-03 冻结：真实 PostgreSQL pending/detail 与 observed CAS。 */
class CollectorConfigReleaseObservedPostgresIntegrationTest {

    private static final long TENANT = 920_008_001L;
    private static final long SITE = 920_008_002L;
    private static final long NODE = 920_008_003L;
    private static final long TEMPLATE = 920_008_004L;
    private static final long TEMPLATE_VERSION = 920_008_005L;
    private static final long BINDING = 920_008_006L;
    private static final long AUDIT = 920_008_007L;
    private static final long OUTBOX = 920_008_008L;
    private static final long RELEASE = 920_008_009L;
    private static final long PROJECTION = 920_008_010L;
    private static final long ACTOR = 920_008_011L;
    private static final String EVENT = "00000000-0000-0000-0000-000092000801";
    private static final String AUDIT_EVENT = "00000000-0000-0000-0000-000092000802";

    @Test
    void appliedIsCasIdempotentAndPendingRequiresMatchingProjection() {
        DataSource dataSource = dataSource();
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        DataSourceTransactionManager manager = new DataSourceTransactionManager(dataSource);
        TransactionTemplate outer = new TransactionTemplate(manager);
        outer.executeWithoutResult(transaction -> {
            Fixture fixture = insertFixture(jdbc);
            RecordingFactRecorder recorder = new RecordingFactRecorder();
            JdbcCollectorConfigReleaseInternalRepository repository =
                    new JdbcCollectorConfigReleaseInternalRepository(dataSource, manager);
            CollectorConfigReleaseInternalService service =
                    new CollectorConfigReleaseInternalService(repository, recorder);

            assertEquals(1, service.listPending(100).size());
            assertEquals(fixture.canonical(), service.detail(Long.toString(RELEASE)).getPayloadCanonical());
            assertEquals(fixture.hash(), service.detail(Long.toString(RELEASE)).getPayloadSha256());
            assertEquals(fixture.canonicalLength(),
                    service.detail(Long.toString(RELEASE)).getCanonicalLengthBytes());

            CollectorConfigReleaseObservedRequestDTO accepted = request(
                    "AGENT_ACCEPTED", fixture.hash(), null);
            assertFalse(service.observe(Long.toString(RELEASE), accepted).isTerminal());
            assertEquals("PUBLISHED", status(jdbc));
            assertEquals(1, recorder.count);

            CollectorConfigReleaseObservedRequestDTO applied = request(
                    "APPLIED", fixture.hash(), null);
            assertTrue(service.observe(Long.toString(RELEASE), applied).isTerminal());
            assertEquals("APPLIED", status(jdbc));
            assertEquals(Long.toString(1L), jdbc.queryForObject(
                    "SELECT applied_version::text FROM public.iot_collector_config_release WHERE id=?",
                    String.class, RELEASE));
            assertNotNull(jdbc.queryForObject(
                    "SELECT applied_at FROM public.iot_collector_config_release WHERE id=?",
                    Object.class, RELEASE));
            assertTrue(service.observe(Long.toString(RELEASE), applied).isIdempotent());
            assertEquals(0, service.listPending(100).size());
            assertThrows(CollectorConfigReleaseInternalException.class,
                    () -> service.detail(Long.toString(RELEASE)));

            CollectorConfigReleaseObservedRequestDTO lateFailed = request(
                    "FAILED", fixture.hash(), "LATE_FAILURE");
            assertFalse(service.observe(Long.toString(RELEASE), lateFailed).isAccepted());
            assertEquals("APPLIED", status(jdbc));
            assertEquals("1", jdbc.queryForObject(
                    "SELECT applied_version::text FROM public.iot_collector_config_release WHERE id=?",
                    String.class, RELEASE));
            transaction.setRollbackOnly();
        });
        assertClean(jdbc);
        close(dataSource);
    }

    @Test
    void failedPreservesPriorAppliedFactsAndMismatchedObservedCannotOverwrite() {
        DataSource dataSource = dataSource();
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        DataSourceTransactionManager manager = new DataSourceTransactionManager(dataSource);
        TransactionTemplate outer = new TransactionTemplate(manager);
        outer.executeWithoutResult(transaction -> {
            Fixture fixture = insertFixture(jdbc);
            RecordingFactRecorder recorder = new RecordingFactRecorder();
            JdbcCollectorConfigReleaseInternalRepository repository =
                    new JdbcCollectorConfigReleaseInternalRepository(dataSource, manager);
            CollectorConfigReleaseInternalService service =
                    new CollectorConfigReleaseInternalService(repository, recorder);

            CollectorConfigReleaseObservedRequestDTO forgedTenant = request(
                    "APPLIED", fixture.hash(), null);
            forgedTenant.setTenantId(Long.toString(TENANT + 1));
            assertFalse(service.observe(Long.toString(RELEASE), forgedTenant).isAccepted());
            assertEquals("PUBLISHED", status(jdbc));
            assertEquals(1, recorder.count);

            CollectorConfigReleaseObservedRequestDTO forgedNode = request(
                    "APPLIED", fixture.hash(), null);
            forgedNode.setNodeId(Long.toString(NODE + 1));
            assertFalse(service.observe(Long.toString(RELEASE), forgedNode).isAccepted());
            assertEquals("PUBLISHED", status(jdbc));

            CollectorConfigReleaseObservedRequestDTO forgedWorkload = request(
                    "APPLIED", fixture.hash(), null);
            forgedWorkload.setWorkloadId("collector-008-other");
            assertFalse(service.observe(Long.toString(RELEASE), forgedWorkload).isAccepted());
            assertEquals("PUBLISHED", status(jdbc));

            CollectorConfigReleaseObservedRequestDTO forgedVersion = request(
                    "APPLIED", fixture.hash(), null);
            forgedVersion.setConfigVersion("2");
            assertFalse(service.observe(Long.toString(RELEASE), forgedVersion).isAccepted());
            assertEquals("PUBLISHED", status(jdbc));

            CollectorConfigReleaseObservedRequestDTO forgedHash = request(
                    "APPLIED", "b".repeat(64), null);
            assertFalse(service.observe(Long.toString(RELEASE), forgedHash).isAccepted());
            assertEquals("PUBLISHED", status(jdbc));
            assertEquals(5, recorder.count);

            CollectorConfigReleaseObservedRequestDTO failed = request(
                    "FAILED", fixture.hash(), "APPLY_FAILED");
            failed.setErrorDetailSanitized("serial timeout; old active retained");
            assertTrue(service.observe(Long.toString(RELEASE), failed).isTerminal());
            assertEquals("FAILED", status(jdbc));
            assertEquals(0, jdbc.queryForObject(
                    "SELECT count(*) FROM public.iot_collector_config_release"
                            + " WHERE id=? AND applied_version IS NOT NULL", Integer.class, RELEASE));
            assertEquals("APPLY_FAILED", jdbc.queryForObject(
                    "SELECT error_code FROM public.iot_collector_config_release WHERE id=?",
                    String.class, RELEASE));
            assertEquals("serial timeout_ old active retained", jdbc.queryForObject(
                    "SELECT error_detail FROM public.iot_collector_config_release WHERE id=?",
                    String.class, RELEASE));
            assertTrue(service.observe(Long.toString(RELEASE), failed).isIdempotent());

            CollectorConfigReleaseObservedRequestDTO lateApplied = request(
                    "APPLIED", fixture.hash(), null);
            assertFalse(service.observe(Long.toString(RELEASE), lateApplied).isAccepted());
            assertEquals("FAILED", status(jdbc));
            assertNull(jdbc.queryForObject(
                    "SELECT applied_version FROM public.iot_collector_config_release WHERE id=?",
                    Object.class, RELEASE));
            assertEquals("APPLY_FAILED", jdbc.queryForObject(
                    "SELECT error_code FROM public.iot_collector_config_release WHERE id=?",
                    String.class, RELEASE));
            transaction.setRollbackOnly();
        });
        assertClean(jdbc);
        close(dataSource);
    }

    private static CollectorConfigReleaseObservedRequestDTO request(
            String status, String hash, String errorCode) {
        CollectorConfigReleaseObservedRequestDTO request = new CollectorConfigReleaseObservedRequestDTO();
        request.setReleaseId(Long.toString(RELEASE));
        request.setTenantId(Long.toString(TENANT));
        request.setNodeId(Long.toString(NODE));
        request.setWorkloadId("collector-008");
        request.setConfigVersion("1");
        request.setPayloadSha256(hash);
        request.setStatus(CollectorConfigReleaseObservedStatus.valueOf(status));
        request.setObservedAt("2026-08-17T10:00:00+08:00");
        request.setErrorCode(errorCode);
        return request;
    }

    private static Fixture insertFixture(JdbcTemplate jdbc) {
        String productIdentification = "td008-" + UUID.randomUUID().toString().replace("-", "");
        Long productId = jdbc.queryForObject("INSERT INTO public.product"
                        + " (app_id,product_name,product_identification,product_type,manufacturer_id,"
                        + " manufacturer_name,model,data_format,device_type,protocol_type,status,tenant_id)"
                        + " VALUES ('td008','TD008 fixture',?,'COMMON','td008','TD008','v008','JSON',"
                        + " 'COMMON','MQTT','0',?) RETURNING id",
                Long.class, productIdentification, TENANT);
        if (productId == null) throw new IllegalStateException("fixture product id missing");
        jdbc.update("INSERT INTO public.power_model_template"
                        + " (id,tenant_id,template_code,template_name,device_type,template_kind,owner_scope)"
                        + " VALUES (?,?, 'tpl-v008','V008 fixture','METER','STANDARD','TENANT')",
                TEMPLATE, TENANT);
        jdbc.update("INSERT INTO public.power_model_template_version"
                        + " (id,tenant_id,template_id,version,major,minor,patch,lifecycle,schema_version,"
                        + " canonicalization_version,hash_algorithm,content_canonical,content_json,"
                        + " content_hash,source_type,published_by,published_at)"
                        + " VALUES (?,?,?,'1.0.0',1,0,0,'PUBLISHED','1.0.0','jcs-rfc8785-v1',"
                        + " 'SHA-256','{}','{}'::jsonb,?,'UI',?,CURRENT_TIMESTAMP)",
                TEMPLATE_VERSION, TENANT, TEMPLATE, "sha256:" + "1".repeat(64), Long.toString(ACTOR));
        jdbc.update("INSERT INTO public.power_product_model_binding"
                        + " (id,tenant_id,product_id,product_identification,binding_revision,status,"
                        + " template_version_id,template_code,template_version,content_hash,"
                        + " binding_snapshot_canonical,binding_snapshot_json,binding_snapshot_hash,"
                        + " previous_binding_id,effective_from,effective_to,created_by)"
                        + " VALUES (?,?,?,?,1,'ACTIVE',?,'tpl-v008','1.0.0',?,'{}','{}'::jsonb,?,NULL,"
                        + " CURRENT_TIMESTAMP,NULL,?)",
                BINDING, TENANT, productId, productIdentification, TEMPLATE_VERSION,
                "sha256:" + "1".repeat(64), "sha256:" + "2".repeat(64), Long.toString(ACTOR));
        jdbc.update("INSERT INTO public.power_model_audit"
                        + " (id,audit_event_id,tenant_id,operation,aggregate_type,aggregate_id,"
                        + " template_code,template_version,product_id,product_identification,"
                        + " binding_revision,principal_type,principal_id,request_id)"
                        + " VALUES (?,CAST(? AS uuid),?,'BINDING_APPLIED','power_product_model_binding',"
                        + " ?,'tpl-v008','1.0.0',?,?,1,'USER',?,'req-v008')",
                AUDIT, AUDIT_EVENT, TENANT, Long.toString(BINDING), productId,
                productIdentification, Long.toString(ACTOR));
        jdbc.update("INSERT INTO public.power_model_release_outbox"
                        + " (id,event_id,tenant_id,audit_event_id,aggregate_type,aggregate_id,event_type,"
                        + " payload,payload_hash) VALUES (?,CAST(? AS uuid),?,CAST(? AS uuid),"
                        + " 'power_product_model_binding',?,'POWER_PRODUCT_MODEL_BINDING_APPLIED_V1',"
                        + " '{}'::jsonb,?)",
                OUTBOX, EVENT, TENANT, AUDIT_EVENT, Long.toString(BINDING),
                "sha256:" + "4".repeat(64));

        try {
            ObjectMapper mapper = new ObjectMapper();
            CollectorConfigSnapshotContract contract = new CollectorConfigSnapshotContract();
            JsonNode root = mapper.readTree(snapshot());
            CollectorConfigSnapshotContract.Artifact artifact = contract.validateAndCanonicalize(root);
            jdbc.update("INSERT INTO public.iot_collector_config_release"
                            + " (id,tenant_id,site_id,site_code,workload_id,node_id,config_version,"
                            + " schema_version,canonicalization_version,payload_canonical,payload,"
                            + " payload_sha256,canonical_length_bytes,status,published_by,published_at,"
                            + " product_id,template_code,template_version,binding_revision,source_event_id,"
                            + " source_reason_code) VALUES (?,?,?,'site-v008','collector-008',?,?,?, ?,?,"
                            + " CAST(? AS jsonb),?,?,?,? ,CURRENT_TIMESTAMP,?,'tpl-v008','1.0.0',1,"
                            + " CAST(? AS uuid),'BINDING_APPLIED')",
                    RELEASE, TENANT, SITE, NODE, 1L,
                    CollectorConfigSnapshotContract.SCHEMA_VERSION,
                    CollectorConfigSnapshotContract.CANONICALIZATION_VERSION,
                    artifact.canonical(), artifact.canonical(), artifact.sha256(), artifact.lengthBytes(),
                    "PUBLISHED", ACTOR, productId, EVENT);
            jdbc.update("INSERT INTO public.collector_workload_binding_projection"
                            + " (id,tenant_id,workload_id,site_id,site_code,node_id,product_id,template_code,"
                            + " template_version,binding_revision,config_version,release_id,projection_revision,"
                            + " lifecycle_status) VALUES (?,?, 'collector-008',?,'site-v008',? ,?,'tpl-v008',"
                            + " '1.0.0',1,1,?,1,'ACTIVE')",
                    PROJECTION, TENANT, SITE, NODE, productId, RELEASE);
            return new Fixture(artifact.canonical(), artifact.sha256(), artifact.lengthBytes());
        } catch (Exception e) {
            throw new IllegalStateException("collector release fixture creation failed", e);
        }
    }

    private static String snapshot() {
        return "{\"schemaVersion\":\"1.0\",\"workloadId\":\"collector-008\","
                + "\"tenantId\":\"" + TENANT + "\",\"siteId\":\"" + SITE + "\","
                + "\"siteCode\":\"site-v008\",\"configVersion\":1,"
                + "\"generatedAt\":\"2026-08-17T10:00:00+08:00\",\"serialBuses\":[{"
                + "\"busId\":\"bus-a\",\"serialPort\":\"/dev/easyaiot/rs485-0\","
                + "\"baudRate\":9600,\"dataBits\":8,\"stopBits\":\"1\",\"parity\":\"NONE\","
                + "\"transmitDelayMs\":0,\"rs485Mode\":true,\"devices\":[{"
                + "\"deviceId\":\"920008100\",\"deviceIdentification\":\"METER-V008\","
                + "\"unitId\":1,\"pollIntervalMs\":5000,\"requestTimeoutMs\":1000,"
                + "\"maxRetries\":2,\"points\":[{\"propertyCode\":\"active-power\","
                + "\"function\":\"HOLDING_REGISTER\",\"address\":0,\"quantity\":2,"
                + "\"dataType\":\"FLOAT32\",\"byteOrder\":\"BIG_ENDIAN\","
                + "\"wordOrder\":\"BIG_ENDIAN\",\"scale\":\"1\",\"offset\":\"0\","
                + "\"dataPriority\":\"METERING_TOTAL\",\"writable\":false,\"pollGroup\":\"normal\"}]}]}]}";
    }

    private static String status(JdbcTemplate jdbc) {
        return jdbc.queryForObject("SELECT status FROM public.iot_collector_config_release WHERE id=?",
                String.class, RELEASE);
    }

    private static void assertClean(JdbcTemplate jdbc) {
        assertEquals(0, jdbc.queryForObject(
                "SELECT count(*) FROM public.iot_collector_config_release WHERE tenant_id=?",
                Integer.class, TENANT));
        assertEquals(0, jdbc.queryForObject(
                "SELECT count(*) FROM public.collector_workload_binding_projection WHERE tenant_id=?",
                Integer.class, TENANT));
        assertEquals(0, jdbc.queryForObject(
                "SELECT count(*) FROM public.power_product_model_binding WHERE tenant_id=?",
                Integer.class, TENANT));
    }

    private static DataSource dataSource() {
        return new PooledDataSource("org.postgresql.Driver", environmentOrDefault("TD007_PG_URL",
                "jdbc:postgresql://localhost:5432/iot-device20"),
                environmentOrDefault("TD005_PG_USERNAME", "postgres"), databasePassword());
    }

    private static String databasePassword() {
        String configured = System.getenv("TD005_PG_PASSWORD");
        if (configured != null && !configured.isEmpty()) return configured;
        Process process = null;
        try {
            process = new ProcessBuilder("docker", "exec", "postgres-server",
                    "printenv", "POSTGRES_PASSWORD")
                    .redirectErrorStream(true).start();
            String password = new String(process.getInputStream().readAllBytes(),
                    StandardCharsets.UTF_8).trim();
            if (process.waitFor() != 0 || password.isEmpty()) {
                throw new IllegalStateException("PostgreSQL container password unavailable");
            }
            return password;
        } catch (IOException e) {
            throw new IllegalStateException("PostgreSQL container is unavailable", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("PostgreSQL password lookup interrupted", e);
        } finally {
            if (process != null && process.isAlive()) process.destroyForcibly();
        }
    }

    private static String environmentOrDefault(String name, String fallback) {
        String value = System.getenv(name);
        return value == null || value.isEmpty() ? fallback : value;
    }

    private static void close(DataSource dataSource) {
        if (dataSource instanceof PooledDataSource) ((PooledDataSource) dataSource).forceCloseAll();
    }

    private record Fixture(String canonical, String hash, long canonicalLength) {
    }

    private static final class RecordingFactRecorder
            implements CollectorConfigReleaseObservedFactRecorder {
        private int count;

        @Override
        public void record(CollectorConfigReleaseObservedFact fact, String outcome) {
            count++;
        }
    }
}
