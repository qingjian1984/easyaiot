package com.basiclab.iot.device.service.power;

import com.basiclab.iot.common.capability.CapabilityService;
import com.basiclab.iot.device.config.PowerModelIdempotencySecretProvider;
import java.nio.charset.StandardCharsets;
import com.basiclab.iot.common.core.service.TenantFrameworkService;
import com.basiclab.iot.device.controller.power.dto.PowerModelBindingApplyRequest;
import com.basiclab.iot.device.controller.power.dto.PowerModelBindingApplyResponse;
import com.basiclab.iot.device.event.PowerModelEventEnvelope;
import com.basiclab.iot.device.service.event.JdbcPowerModelOutboxRepository;
import com.basiclab.iot.device.service.event.JdbcCollectorConfigReleasePort;
import com.basiclab.iot.device.service.event.JdbcCollectorWorkloadImpactPort;
import com.basiclab.iot.device.service.event.JdbcPowerModelCoordinationAuditPort;
import com.basiclab.iot.device.service.event.JdbcPowerModelInboxRepository;
import com.basiclab.iot.device.service.event.JdbcPowerModelTemplateReferencePort;
import com.basiclab.iot.device.service.event.PowerModelCollectorEventHandlers;
import com.basiclab.iot.device.service.event.PowerModelEventConsumerCoordinator;
import com.basiclab.iot.device.service.event.PowerModelEventEnvelopeCodec;
import com.basiclab.iot.device.service.event.PowerModelEventHandlerRegistry;
import com.basiclab.iot.device.service.event.PowerModelEventMetrics;
import com.basiclab.iot.device.service.event.PowerModelEventTransport;
import com.basiclab.iot.device.service.event.PowerModelInboxWriter;
import com.basiclab.iot.device.service.event.PowerModelOutboxService;
import com.basiclab.iot.device.service.idempotency.JdbcPowerIdempotencyStore;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.ibatis.datasource.pooled.PooledDataSource;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.transaction.TransactionDefinition;

import java.util.UUID;
import java.time.Duration;
import java.time.Instant;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** TD-005 §11.4/§17：绑定、审计、Outbox 与 VALIDATED 候选的真实 PostgreSQL 原子合同。 */
class PowerModelBindingApplyPostgresIntegrationTest {

    private static final long TENANT = 920_008_001L;
    private static final long TEMPLATE = 920_008_002L;
    private static final long TEMPLATE_VERSION = 920_008_003L;
    private static final long NODE = 920_008_004L;
    private static final long ACTOR = 920_008_005L;

    @Test
    void createsFourFactsWithServerAllocatedIdentityAndCanonicalHashes() throws Exception {
        assumeEnabled();
        TestContext ctx = context();
        TransactionTemplate tx = new TransactionTemplate(ctx.manager);
        tx.executeWithoutResult(status -> {
            String productIdentification = insertFixtures(ctx.jdbc);
            PowerModelBindingApplyResponse response = ctx.service.apply(TENANT,
                    productIdentification, request(ctx.mapper, "wl-td008-success"), ACTOR,
                    "idem-td008-success", "req-td008-success", "trace-td008");

            assertEquals("1", response.getBindingRevision());
            assertEquals("1", response.getConfigVersion());
            assertEquals("PUBLISHED", response.getStatus());
            assertEquals(1, count(ctx.jdbc, "power_product_model_binding"));
            assertEquals(1, count(ctx.jdbc, "power_model_audit"));
            assertEquals(1, count(ctx.jdbc, "power_model_release_outbox"));
            assertEquals(1, count(ctx.jdbc, "iot_collector_config_release"));
            assertEquals(1, count(ctx.jdbc, "collector_workload_binding_projection"));
            assertEquals(1, count(ctx.jdbc, "power_idempotency_record"));

            PowerModelBindingApplyResponse replay = ctx.service.apply(TENANT,
                    productIdentification, request(ctx.mapper, "wl-td008-success"), ACTOR,
                    "idem-td008-success", "req-td008-retry", "trace-other");
            assertEquals(response.getSourceEventId(), replay.getSourceEventId());
            assertEquals(1, count(ctx.jdbc, "power_product_model_binding"));
            assertEquals(1, count(ctx.jdbc, "power_model_release_outbox"));
            IllegalArgumentException reused = assertThrows(IllegalArgumentException.class,
                    () -> ctx.service.apply(TENANT, productIdentification,
                            request(ctx.mapper, "wl-td008-different"), ACTOR,
                            "idem-td008-success", "req-td008-conflict", ""));
            assertTrue(reused.getMessage().startsWith("IDEMPOTENCY_KEY_REUSED"));
            assertEquals(1, count(ctx.jdbc, "power_product_model_binding"));

            String release = ctx.jdbc.queryForObject(
                    "SELECT status||':'||config_version||':'||binding_revision"
                            + " FROM public.iot_collector_config_release"
                            + " WHERE tenant_id=? AND source_event_id=CAST(? AS uuid)",
                    String.class, TENANT, response.getSourceEventId());
            assertEquals("PUBLISHED:1:1", release);
            String projection = ctx.jdbc.queryForObject(
                    "SELECT lifecycle_status||':'||projection_revision||':'||config_version"
                            + " FROM public.collector_workload_binding_projection"
                            + " WHERE tenant_id=? AND workload_id=?",
                    String.class, TENANT, "wl-td008-success");
            assertEquals("ACTIVE:1:1", projection);
            String[] canonicalFact = ctx.jdbc.queryForObject(
                    "SELECT payload_canonical,payload_sha256,canonical_length_bytes::text"
                            + " FROM public.iot_collector_config_release WHERE tenant_id=?",
                    (rs, rowNum) -> new String[]{rs.getString(1), rs.getString(2), rs.getString(3)},
                    TENANT);
            assertEquals(PowerModelEventEnvelope.payloadHash(canonicalFact[0])
                    .substring("sha256:".length()), canonicalFact[1]);
            assertEquals(canonicalFact[0].getBytes(java.nio.charset.StandardCharsets.UTF_8).length,
                    Long.parseLong(canonicalFact[2]));

            String payload = ctx.jdbc.queryForObject(
                    "SELECT payload::text FROM public.power_model_release_outbox"
                            + " WHERE tenant_id=? AND event_id=CAST(? AS uuid)",
                    String.class, TENANT, response.getSourceEventId());
            PowerModelEventEnvelope event = PowerModelEventEnvelopeCodec.parse(payload);
            assertEquals(PowerModelEventEnvelope.EVENT_BINDING_APPLIED_V1, event.eventType());
            assertEquals(response.getBindingRevision(), event.data().get("bindingRevision"));
            assertEquals(Long.toString(ACTOR), event.data().get("appliedBy"));
            status.setRollbackOnly();
        });
        assertNoFixtureRows(ctx.jdbc);
        ctx.dataSource.forceCloseAll();
    }

    @Test
    void finalCandidateFailureRollsBackBindingAuditAndOutbox() throws Exception {
        assumeEnabled();
        TestContext ctx = context();
        TransactionTemplate tx = new TransactionTemplate(ctx.manager);

        RuntimeException failure = assertThrows(RuntimeException.class, () ->
                tx.executeWithoutResult(status -> {
                    String productIdentification = insertFixtures(ctx.jdbc);
                    ctx.jdbc.execute("CREATE FUNCTION pg_temp.td008_reject_release()"
                            + " RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN"
                            + " RAISE EXCEPTION 'TD008_FORCED_FINAL_INSERT_FAILURE'; END $$");
                    ctx.jdbc.execute("CREATE TRIGGER td008_reject_release"
                            + " BEFORE INSERT ON public.iot_collector_config_release"
                            + " FOR EACH ROW EXECUTE FUNCTION pg_temp.td008_reject_release()");
                    ctx.service.apply(TENANT, productIdentification,
                            request(ctx.mapper, "wl-td008-rollback"), ACTOR,
                            "idem-td008-rollback", "req-td008-rollback", "");
                }));
        assertTrue(rootMessage(failure).contains("TD008_FORCED_FINAL_INSERT_FAILURE"));
        assertNoFixtureRows(ctx.jdbc);
        ctx.dataSource.forceCloseAll();
    }

    @Test
    void firstPublishThenOutboxConsumptionIsIdempotentEndToEnd() throws Exception {
        assumeEnabled();
        TestContext ctx = context();
        TransactionTemplate tx = new TransactionTemplate(ctx.manager);
        tx.executeWithoutResult(status -> {
            String productIdentification = insertFixtures(ctx.jdbc);
            PowerModelBindingApplyResponse response = ctx.service.apply(TENANT,
                    productIdentification, request(ctx.mapper, "wl-td008-e2e"), ACTOR,
                    "idem-td008-e2e", "req-td008-e2e", "trace-td008-e2e");
            String payload = ctx.jdbc.queryForObject(
                    "SELECT payload::text FROM public.power_model_release_outbox"
                            + " WHERE tenant_id=? AND event_id=CAST(? AS uuid)",
                    String.class, TENANT, response.getSourceEventId());

            PowerModelEventMetrics metrics = new PowerModelEventMetrics() {
                @Override public void eventPublished(String result) { }
                @Override public void recordDeliveryDuration(Duration duration) { }
                @Override public void inboxQuarantined() { }
            };
            PowerModelInboxWriter writer = new PowerModelInboxWriter(
                    new JdbcPowerModelInboxRepository(ctx.dataSource),
                    Collections.singleton(1), metrics);
            PowerModelEventHandlerRegistry registry = new PowerModelEventHandlerRegistry(
                    PowerModelCollectorEventHandlers.create(
                            new JdbcCollectorWorkloadImpactPort(ctx.dataSource),
                            new JdbcCollectorConfigReleasePort(ctx.dataSource, ctx.mapper, ctx.manager),
                            new JdbcPowerModelTemplateReferencePort(ctx.dataSource),
                            new JdbcPowerModelCoordinationAuditPort(ctx.dataSource)));
            PowerModelEventTransport transport = (topic, key, body) ->
                    PowerModelEventTransport.TransportResult.success();
            PowerModelEventConsumerCoordinator coordinator =
                    new PowerModelEventConsumerCoordinator(writer, registry, transport,
                            "power-model.dlq", 5, Duration.ofSeconds(1), Duration.ofSeconds(6));

            PowerModelEventConsumerCoordinator.ConsumeDecision first =
                    coordinator.consume(payload, 0, Instant.now());
            assertEquals(PowerModelEventConsumerCoordinator.Action.COMMIT_OFFSET, first.action());
            assertEquals("processed", first.detail());
            assertEquals("PROCESSED", ctx.jdbc.queryForObject(
                    "SELECT status FROM public.power_model_event_inbox"
                            + " WHERE event_id=CAST(? AS uuid)",
                    String.class, response.getSourceEventId()));
            assertEquals("PUBLISHED:1", ctx.jdbc.queryForObject(
                    "SELECT status||':'||row_version FROM public.iot_collector_config_release"
                            + " WHERE tenant_id=? AND id=?::bigint",
                    String.class, TENANT, response.getCollectorConfigReleaseId()));
            assertEquals("ACTIVE:1:1", ctx.jdbc.queryForObject(
                    "SELECT lifecycle_status||':'||projection_revision||':'||config_version"
                            + " FROM public.collector_workload_binding_projection"
                            + " WHERE tenant_id=? AND workload_id='wl-td008-e2e'",
                    String.class, TENANT));
            assertEquals(1, count(ctx.jdbc, "power_model_coordination_audit"));

            PowerModelEventConsumerCoordinator.ConsumeDecision duplicate =
                    coordinator.consume(payload, 0, Instant.now());
            assertEquals(PowerModelEventConsumerCoordinator.Action.COMMIT_OFFSET,
                    duplicate.action());
            assertEquals("duplicate", duplicate.detail());
            assertEquals(1, count(ctx.jdbc, "power_model_coordination_audit"));

            PowerModelBindingApplyResponse second = ctx.service.apply(TENANT,
                    productIdentification, request(ctx.mapper, "wl-td008-e2e"), ACTOR,
                    "idem-td008-e2e-second", "req-td008-e2e-second", "trace-td008-e2e");
            assertEquals("2", second.getBindingRevision());
            assertEquals("2", second.getConfigVersion());
            String secondPayload = ctx.jdbc.queryForObject(
                    "SELECT payload::text FROM public.power_model_release_outbox"
                            + " WHERE tenant_id=? AND event_id=CAST(? AS uuid)",
                    String.class, TENANT, second.getSourceEventId());
            PowerModelEventConsumerCoordinator.ConsumeDecision differentEvent =
                    coordinator.consume(secondPayload, 0, Instant.now());
            assertEquals("processed", differentEvent.detail());
            assertEquals("ACTIVE:2:2", ctx.jdbc.queryForObject(
                    "SELECT lifecycle_status||':'||projection_revision||':'||config_version"
                            + " FROM public.collector_workload_binding_projection"
                            + " WHERE tenant_id=? AND workload_id='wl-td008-e2e'",
                    String.class, TENANT));
            assertEquals(2, count(ctx.jdbc, "power_model_coordination_audit"));

            ctx.jdbc.execute("CREATE FUNCTION pg_temp.td008_reject_projection_update()"
                    + " RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN"
                    + " RAISE EXCEPTION 'TD008_FORCED_PROJECTION_CAS_FAILURE'; END $$");
            ctx.jdbc.execute("CREATE TRIGGER td008_reject_projection_update"
                    + " BEFORE UPDATE ON public.collector_workload_binding_projection"
                    + " FOR EACH ROW EXECUTE FUNCTION pg_temp.td008_reject_projection_update()");
            TransactionTemplate nested = new TransactionTemplate(ctx.manager);
            nested.setPropagationBehavior(TransactionDefinition.PROPAGATION_NESTED);
            RuntimeException casFailure = assertThrows(RuntimeException.class, () ->
                    nested.executeWithoutResult(ignored -> ctx.service.apply(TENANT,
                            productIdentification, request(ctx.mapper, "wl-td008-e2e"), ACTOR,
                            "idem-td008-e2e-cas", "req-td008-e2e-cas", "trace-td008-e2e")));
            assertTrue(rootMessage(casFailure).contains("TD008_FORCED_PROJECTION_CAS_FAILURE"));
            assertEquals("ACTIVE:2:2", ctx.jdbc.queryForObject(
                    "SELECT lifecycle_status||':'||projection_revision||':'||config_version"
                            + " FROM public.collector_workload_binding_projection"
                            + " WHERE tenant_id=? AND workload_id='wl-td008-e2e'",
                    String.class, TENANT));
            assertEquals(2, count(ctx.jdbc, "power_product_model_binding"));
            assertEquals(2, count(ctx.jdbc, "power_model_release_outbox"));
            assertEquals(2, count(ctx.jdbc, "iot_collector_config_release"));
            status.setRollbackOnly();
        });
        assertNoFixtureRows(ctx.jdbc);
        ctx.dataSource.forceCloseAll();
    }

    private static TestContext context() {
        String password = System.getenv("TD005_PG_PASSWORD");
        String url = environmentOrDefault("TD008_PG_URL",
                "jdbc:postgresql://localhost:5432/iot-device20");
        String username = environmentOrDefault("TD005_PG_USERNAME", "postgres");
        PooledDataSource dataSource = new PooledDataSource(
                "org.postgresql.Driver", url, username, password);
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        DataSourceTransactionManager manager = new DataSourceTransactionManager(dataSource);
        ObjectMapper mapper = new ObjectMapper();
        CapabilityService capability = mock(CapabilityService.class);
        TenantFrameworkService tenantFramework = mock(TenantFrameworkService.class);
        when(capability.isEnabled(PowerModelBindingApplyService.CAPABILITY_CODE)).thenReturn(true);
        PowerModelOutboxService outbox = new PowerModelOutboxService(
                new JdbcPowerModelOutboxRepository(dataSource), capability);
        PowerModelIdempotencySecretProvider secretProvider = mock(PowerModelIdempotencySecretProvider.class);
        when(secretProvider.getSecret()).thenReturn(
                "td008-review-secret-must-be-at-least-32-bytes".getBytes(StandardCharsets.UTF_8));
        PowerModelBindingApplyService service = new PowerModelBindingApplyService(
                dataSource, mapper, capability, tenantFramework, outbox,
                new JdbcPowerIdempotencyStore(dataSource),
                secretProvider);
        return new TestContext(dataSource, jdbc, manager, mapper, service);
    }

    private static String insertFixtures(JdbcTemplate jdbc) {
        String identification = "td008-" + UUID.randomUUID().toString().replace("-", "");
        jdbc.queryForObject("INSERT INTO public.product"
                        + " (app_id,product_name,product_identification,product_type,manufacturer_id,"
                        + " manufacturer_name,model,data_format,device_type,protocol_type,status,tenant_id)"
                        + " VALUES ('td008','TD008 fixture',?,'COMMON','td008','TD008','td008','JSON',"
                        + " 'COMMON','MQTT','0',?) RETURNING id",
                Long.class, identification, TENANT);
        jdbc.update("INSERT INTO public.power_model_template"
                        + " (id,tenant_id,template_code,template_name,device_type,template_kind,owner_scope)"
                        + " VALUES (?,?,'tpl-td008','TD008模板','METER','STANDARD','TENANT')",
                TEMPLATE, TENANT);
        jdbc.update("INSERT INTO public.power_model_template_version"
                        + " (id,tenant_id,template_id,version,major,minor,patch,lifecycle,schema_version,"
                        + " canonicalization_version,hash_algorithm,content_canonical,content_json,"
                        + " content_hash,source_type,published_by,published_at)"
                        + " VALUES (?,?,?,'1.0.0',1,0,0,'PUBLISHED','1.0.0','jcs-rfc8785-v1',"
                        + " 'SHA-256','{}','{}'::jsonb,?,'UI',?,CURRENT_TIMESTAMP)",
                TEMPLATE_VERSION, TENANT, TEMPLATE, "sha256:" + "8".repeat(64),
                Long.toString(ACTOR));
        return identification;
    }

    private static PowerModelBindingApplyRequest request(ObjectMapper mapper, String workloadId) {
        try {
            PowerModelBindingApplyRequest request = new PowerModelBindingApplyRequest();
            request.setTemplateCode("tpl-td008");
            request.setTemplateVersion("1.0.0");
            request.setNodeId(NODE);
            request.setBindingSnapshot(mapper.readTree("{\"templateCode\":\"tpl-td008\","
                    + "\"templateVersion\":\"1.0.0\"}"));
            request.setCollectorSnapshot(mapper.readTree("{\"schemaVersion\":\"1.0\","
                    + "\"workloadId\":\"" + workloadId + "\",\"tenantId\":\"" + TENANT + "\","
                    + "\"siteId\":\"920008006\",\"siteCode\":\"site-td008\","
                    + "\"serialBuses\":[{\"busId\":\"bus-a\","
                    + "\"serialPort\":\"/dev/easyaiot/rs485-0\",\"baudRate\":9600,"
                    + "\"dataBits\":8,\"stopBits\":\"1\",\"parity\":\"NONE\","
                    + "\"transmitDelayMs\":0,\"rs485Mode\":true,\"devices\":[{"
                    + "\"deviceId\":\"920008007\",\"deviceIdentification\":\"METER-TD008\","
                    + "\"unitId\":1,\"pollIntervalMs\":5000,\"requestTimeoutMs\":1000,"
                    + "\"maxRetries\":2,\"points\":[{\"propertyCode\":\"active-power\","
                    + "\"function\":\"HOLDING_REGISTER\",\"address\":0,\"quantity\":2,"
                    + "\"dataType\":\"FLOAT32\",\"byteOrder\":\"BIG_ENDIAN\","
                    + "\"wordOrder\":\"BIG_ENDIAN\",\"scale\":\"1\",\"offset\":\"0\","
                    + "\"dataPriority\":\"METERING_TOTAL\",\"writable\":false,"
                    + "\"pollGroup\":\"normal\"}]}]}]}"));
            return request;
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private static int count(JdbcTemplate jdbc, String table) {
        return jdbc.queryForObject("SELECT count(*) FROM public." + table + " WHERE tenant_id=?",
                Integer.class, TENANT);
    }

    private static void assertNoFixtureRows(JdbcTemplate jdbc) {
        assertEquals(0, count(jdbc, "power_product_model_binding"));
        assertEquals(0, count(jdbc, "power_model_audit"));
        assertEquals(0, count(jdbc, "power_model_release_outbox"));
        assertEquals(0, count(jdbc, "iot_collector_config_release"));
        assertEquals(0, count(jdbc, "collector_workload_binding_projection"));
        assertEquals(0, count(jdbc, "power_model_event_inbox"));
        assertEquals(0, count(jdbc, "power_model_coordination_audit"));
        assertEquals(0, count(jdbc, "power_idempotency_record"));
        assertEquals(0, count(jdbc, "power_model_template_version"));
        assertEquals(0, count(jdbc, "power_model_template"));
        assertEquals(0, jdbc.queryForObject(
                "SELECT count(*) FROM public.product WHERE tenant_id=?", Integer.class, TENANT));
    }

    private static void assumeEnabled() {
        assumeTrue(Boolean.parseBoolean(System.getenv("TD008_PG_ENABLED")),
                "Set TD008_PG_ENABLED=true to run the binding apply PostgreSQL contract");
        assumeTrue(System.getenv("TD005_PG_PASSWORD") != null,
                "Set TD005_PG_PASSWORD without committing credentials");
    }

    private static String rootMessage(Throwable error) {
        Throwable current = error;
        while (current.getCause() != null) current = current.getCause();
        return String.valueOf(current.getMessage());
    }

    private static String environmentOrDefault(String name, String fallback) {
        String value = System.getenv(name);
        return value == null || value.isEmpty() ? fallback : value;
    }

    private static final class TestContext {
        final PooledDataSource dataSource;
        final JdbcTemplate jdbc;
        final DataSourceTransactionManager manager;
        final ObjectMapper mapper;
        final PowerModelBindingApplyService service;

        TestContext(PooledDataSource dataSource, JdbcTemplate jdbc,
                    DataSourceTransactionManager manager, ObjectMapper mapper,
                    PowerModelBindingApplyService service) {
            this.dataSource = dataSource;
            this.jdbc = jdbc;
            this.manager = manager;
            this.mapper = mapper;
            this.service = service;
        }
    }
}
