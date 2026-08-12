package com.basiclab.iot.device.service.power;

import com.basiclab.iot.common.capability.CapabilityService;
import com.basiclab.iot.device.config.PowerModelIdempotencySecretProvider;
import java.nio.charset.StandardCharsets;
import com.basiclab.iot.common.core.service.TenantFrameworkService;
import com.basiclab.iot.device.controller.power.dto.PowerModelTemplateCreateRequest;
import com.basiclab.iot.device.controller.power.dto.PowerModelTemplateDraftResponse;
import com.basiclab.iot.device.controller.power.dto.PowerModelTemplateDraftWriteRequest;
import com.basiclab.iot.device.controller.power.dto.PowerModelTemplatePublishRequest;
import com.basiclab.iot.device.controller.power.dto.PowerModelTemplatePublishResponse;
import com.basiclab.iot.device.service.event.JdbcPowerModelOutboxRepository;
import com.basiclab.iot.device.service.event.PowerModelOutboxService;
import com.basiclab.iot.device.service.event.PowerModelOutboxRepository;
import com.basiclab.iot.device.service.idempotency.JdbcPowerIdempotencyStore;
import com.basiclab.iot.device.service.model.PowerModelTemplateContentValidator;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.ibatis.datasource.pooled.PooledDataSource;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.io.IOException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** TD-005 §10/§11/§17：发布事实、索引、审计、Outbox、重放及失败回滚真实库合同。 */
class PowerModelTemplatePublishPostgresIntegrationTest {

    private static final long TENANT = 920_012_001L;
    private static final long FAIL_TENANT = 920_012_101L;
    private static final long CONCURRENT_TENANT = 920_012_201L;
    private static final long LOCK_TENANT = 920_012_301L;
    private static final long ACTOR = 920_012_002L;
    private static final String SECRET = "td012-review-secret-must-be-at-least-32-bytes";

    @Test
    void publishesAtomicallyBuildsIndexesAndReplaysBeforeIfMatch() throws Exception {
        assumeEnabled();
        TestContext ctx = context(false);
        new TransactionTemplate(ctx.manager).executeWithoutResult(status -> {
            try {
                PowerModelTemplateDraftResponse draft = prepareDraft(ctx, TENANT, "ok");
                PowerModelTemplatePublishRequest request = publishRequest();
                PowerModelTemplatePublishResponse published = ctx.publish.publish(
                        TENANT, "std-meter", Long.parseLong(draft.getDraftId()), request, ACTOR,
                        "td012-publish", "req-td012", "trace-td012", draft.getEtag());
                assertEquals("PUBLISHED", published.getLifecycle());
                assertEquals("1.0.0", published.getVersion());
                assertEquals(1, count(ctx.jdbc, "power_model_template_version", TENANT));
                assertEquals(memberCount(loadExample(ctx.mapper)),
                        count(ctx.jdbc, "power_model_member_index", TENANT));
                assertEquals(1, count(ctx.jdbc, "power_model_audit", TENANT));
                assertEquals(1, count(ctx.jdbc, "power_model_release_outbox", TENANT));
                assertEquals("PENDING", ctx.jdbc.queryForObject(
                        "SELECT status FROM public.power_model_release_outbox WHERE tenant_id=?",
                        String.class, TENANT));
                JsonNode event = ctx.mapper.readTree(ctx.jdbc.queryForObject(
                        "SELECT payload::text FROM public.power_model_release_outbox WHERE tenant_id=?",
                        String.class, TENANT));
                assertEquals("POWER_MODEL_TEMPLATE_PUBLISHED_V1",
                        event.path("eventType").asText());
                assertEquals(1, event.path("schemaVersion").asInt());
                assertEquals("PUBLISHED", event.path("data").path("lifecycle").asText());
                assertEquals(published.getSourceEventId(), event.path("eventId").asText());

                PowerModelTemplatePublishResponse replay = ctx.publish.publish(
                        TENANT, "std-meter", Long.parseLong(draft.getDraftId()), request, ACTOR,
                        "td012-publish", "req-td012", "trace-td012", "stale-ignored");
                assertEquals(published.getSourceEventId(), replay.getSourceEventId());
                assertEquals(1, count(ctx.jdbc, "power_model_release_outbox", TENANT));

                PowerModelTemplateDraftWriteRequest lowerRequest = new PowerModelTemplateDraftWriteRequest();
                JsonNode lowerContent = loadExample(ctx.mapper).deepCopy();
                ((com.fasterxml.jackson.databind.node.ObjectNode) lowerContent)
                        .put("version", "0.9.0");
                lowerRequest.setContent(lowerContent);
                PowerModelTemplateDraftResponse lower = ctx.drafts.create(TENANT, "std-meter",
                        lowerRequest, ACTOR, "td012-lower-draft");
                IllegalArgumentException versionError = assertThrows(IllegalArgumentException.class,
                        () -> ctx.publish.publish(TENANT, "std-meter",
                                Long.parseLong(lower.getDraftId()), publishRequest(), ACTOR,
                                "td012-lower-publish", "req-td012-lower", "", lower.getEtag()));
                assertTrue(versionError.getMessage().startsWith(
                        "MODEL_TEMPLATE_SEMVER_BUMP_TOO_LOW"));
                assertEquals(1, count(ctx.jdbc, "power_model_release_outbox", TENANT));
            } catch (Exception error) {
                throw new RuntimeException(error);
            }
            status.setRollbackOnly();
        });
        assertZero(ctx, TENANT);
        ctx.dataSource.forceCloseAll();
    }

    @Test
    void outboxFailureRollsBackVersionIndexAuditAndIdempotency() throws Exception {
        assumeEnabled();
        TestContext ctx = context(true);
        assertThrows(RuntimeException.class, () -> new TransactionTemplate(ctx.manager).execute(status -> {
            try {
                PowerModelTemplateDraftResponse draft = prepareDraft(ctx, FAIL_TENANT, "fail");
                ctx.publish.publish(FAIL_TENANT, "std-meter", Long.parseLong(draft.getDraftId()),
                        publishRequest(), ACTOR, "td012-publish-fail", "req-td012-fail", "",
                        draft.getEtag());
                return null;
            } catch (Exception error) {
                throw new RuntimeException(error);
            }
        }));
        assertZero(ctx, FAIL_TENANT);
        ctx.dataSource.forceCloseAll();
    }

    @Test
    void concurrentTransactionsAreSerializedAndLeaveNoPartialPublish() throws Exception {
        assumeEnabled();
        CountDownLatch firstAtOutbox = new CountDownLatch(1);
        CountDownLatch releaseFirst = new CountDownLatch(1);
        AtomicInteger calls = new AtomicInteger();
        AtomicInteger active = new AtomicInteger();
        AtomicInteger maxActive = new AtomicInteger();
        PowerModelOutboxRepository repository = mock(PowerModelOutboxRepository.class);
        doAnswer(invocation -> {
            int call = calls.incrementAndGet();
            int current = active.incrementAndGet();
            maxActive.accumulateAndGet(current, Math::max);
            try {
                if (call == 1) {
                    firstAtOutbox.countDown();
                    assertTrue(releaseFirst.await(10, TimeUnit.SECONDS));
                }
                throw new IllegalStateException("injected concurrent rollback " + call);
            } finally {
                active.decrementAndGet();
            }
        }).when(repository).insertPending(any());
        TestContext ctx = context(repository);
        PowerModelTemplateDraftResponse draft = null;
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            draft = committedDraft(ctx, CONCURRENT_TENANT, "concurrent");
            final PowerModelTemplateDraftResponse visibleDraft = draft;
            Future<Throwable> first = executor.submit(() -> publishFailure(ctx,
                    CONCURRENT_TENANT, visibleDraft, "td012-concurrent-a"));
            assertTrue(firstAtOutbox.await(10, TimeUnit.SECONDS));
            Future<Throwable> second = executor.submit(() -> publishFailure(ctx,
                    CONCURRENT_TENANT, visibleDraft, "td012-concurrent-b"));
            Thread.sleep(500);
            assertEquals(1, calls.get(), "第二事务不得越过同模板 advisory lock");
            releaseFirst.countDown();
            assertTrue(first.get(10, TimeUnit.SECONDS) instanceof RuntimeException);
            assertTrue(second.get(10, TimeUnit.SECONDS) instanceof RuntimeException);
            assertEquals(2, calls.get());
            assertEquals(1, maxActive.get());
            assertEquals("DRAFT", ctx.jdbc.queryForObject(
                    "SELECT lifecycle FROM public.power_model_template_version WHERE tenant_id=?",
                    String.class, CONCURRENT_TENANT));
            assertEquals(0, count(ctx.jdbc, "power_model_member_index", CONCURRENT_TENANT));
            assertEquals(0, count(ctx.jdbc, "power_model_audit", CONCURRENT_TENANT));
            assertEquals(0, count(ctx.jdbc, "power_model_release_outbox", CONCURRENT_TENANT));
        } finally {
            releaseFirst.countDown();
            executor.shutdownNow();
            cleanupDraftFixture(ctx, CONCURRENT_TENANT);
            ctx.dataSource.forceCloseAll();
        }
    }

    @Test
    void advisoryLockTimeoutReturnsStableErrorAndRollsBackClaim() throws Exception {
        assumeEnabled();
        TestContext ctx = context(false);
        PowerModelTemplateDraftResponse draft = null;
        Connection holder = null;
        try {
            draft = committedDraft(ctx, LOCK_TENANT, "lock-timeout");
            holder = ctx.dataSource.getConnection();
            holder.setAutoCommit(false);
            try (PreparedStatement statement = holder.prepareStatement(
                    "SELECT pg_advisory_xact_lock(hashtextextended(?,0))")) {
                statement.setString(1, LOCK_TENANT + ":std-meter");
                statement.execute();
            }
            final PowerModelTemplateDraftResponse visibleDraft = draft;
            long started = System.nanoTime();
            IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                    () -> new TransactionTemplate(ctx.manager).execute(status -> {
                        ctx.publish.publish(LOCK_TENANT, "std-meter",
                                Long.parseLong(visibleDraft.getDraftId()), publishRequest(), ACTOR,
                                "td012-lock-timeout", "req-td012-lock", "", visibleDraft.getEtag());
                        return null;
                    }));
            long elapsedMillis = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started);
            assertTrue(error.getMessage().startsWith("MODEL_TEMPLATE_PUBLISH_LOCK_TIMEOUT"));
            assertTrue(elapsedMillis >= 14_000L && elapsedMillis < 25_000L,
                    "锁等待必须接近有界 15 秒，actual=" + elapsedMillis);
            assertEquals("DRAFT", ctx.jdbc.queryForObject(
                    "SELECT lifecycle FROM public.power_model_template_version WHERE tenant_id=?",
                    String.class, LOCK_TENANT));
            assertEquals(2, count(ctx.jdbc, "power_idempotency_record", LOCK_TENANT));
        } finally {
            if (holder != null) {
                holder.rollback();
                holder.setAutoCommit(true);
                holder.close();
            }
            cleanupDraftFixture(ctx, LOCK_TENANT);
            ctx.dataSource.forceCloseAll();
        }
    }

    private static Throwable publishFailure(TestContext ctx, long tenantId,
                                            PowerModelTemplateDraftResponse draft, String key) {
        try {
            new TransactionTemplate(ctx.manager).execute(status -> {
                ctx.publish.publish(tenantId, "std-meter", Long.parseLong(draft.getDraftId()),
                        publishRequest(), ACTOR, key, "req-" + key, "", draft.getEtag());
                return null;
            });
            return null;
        } catch (Throwable error) {
            return error;
        }
    }

    private static PowerModelTemplateDraftResponse committedDraft(TestContext ctx, long tenantId,
                                                                   String suffix) {
        return new TransactionTemplate(ctx.manager).execute(status -> {
            try { return prepareDraft(ctx, tenantId, suffix); }
            catch (Exception error) { throw new RuntimeException(error); }
        });
    }

    private static void cleanupDraftFixture(TestContext ctx, long tenantId) {
        assertEquals(0, count(ctx.jdbc, "power_model_member_index", tenantId));
        assertEquals(0, count(ctx.jdbc, "power_model_release_outbox", tenantId));
        assertEquals(0, count(ctx.jdbc, "power_model_audit", tenantId));
        new TransactionTemplate(ctx.manager).executeWithoutResult(status -> {
            ctx.jdbc.update("DELETE FROM public.power_model_template_version WHERE tenant_id=?",
                    tenantId);
            ctx.jdbc.update("DELETE FROM public.power_model_template WHERE tenant_id=?", tenantId);
            ctx.jdbc.update("DELETE FROM public.power_idempotency_record WHERE tenant_id=?", tenantId);
        });
        assertZero(ctx, tenantId);
    }

    private static PowerModelTemplateDraftResponse prepareDraft(TestContext ctx, long tenantId,
                                                                String suffix) throws Exception {
        PowerModelTemplateCreateRequest identity = new PowerModelTemplateCreateRequest();
        identity.setTemplateCode("std-meter");
        identity.setTemplateName("多功能电表");
        identity.setDeviceType("METER");
        identity.setTemplateKind("STANDARD");
        ctx.identity.create(tenantId, identity, ACTOR, "td012-identity-" + suffix);
        PowerModelTemplateDraftWriteRequest request = new PowerModelTemplateDraftWriteRequest();
        request.setContent(loadExample(ctx.mapper));
        return ctx.drafts.create(tenantId, "std-meter", request, ACTOR, "td012-draft-" + suffix);
    }

    private static PowerModelTemplatePublishRequest publishRequest() {
        PowerModelTemplatePublishRequest request = new PowerModelTemplatePublishRequest();
        request.setReasonCode("INITIAL_RELEASE");
        request.setReasonSummary("M1 初始模板发布合同");
        return request;
    }

    private static JsonNode loadExample(ObjectMapper mapper) throws IOException {
        Path root = Paths.get(System.getProperty("user.dir")).toAbsolutePath();
        while (root != null && !Files.isDirectory(root.resolve(".doc"))) root = root.getParent();
        if (root == null) throw new IOException("repository root not found");
        return mapper.readTree(root.resolve(".doc/规格/电力运维云平台/assets/model-templates/"
                + "example-standard-meter-1.0.0.json").toFile());
    }

    private static int memberCount(JsonNode content) {
        return content.path("properties").size() + content.path("events").size()
                + content.path("services").size();
    }

    private static TestContext context(boolean failingOutbox) throws Exception {
        if (!failingOutbox) return context((PowerModelOutboxRepository) null);
        PowerModelOutboxRepository repository = mock(PowerModelOutboxRepository.class);
        doThrow(new IllegalStateException("injected outbox failure"))
                .when(repository).insertPending(any());
        return context(repository);
    }

    private static TestContext context(PowerModelOutboxRepository suppliedRepository)
            throws Exception {
        PooledDataSource dataSource = new PooledDataSource("org.postgresql.Driver",
                environmentOrDefault("TD008_PG_URL", "jdbc:postgresql://localhost:5432/iot-device20"),
                environmentOrDefault("TD005_PG_USERNAME", "postgres"),
                System.getenv("TD005_PG_PASSWORD"));
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        ObjectMapper mapper = new ObjectMapper();
        CapabilityService capability = mock(CapabilityService.class);
        TenantFrameworkService tenant = mock(TenantFrameworkService.class);
        when(capability.isEnabled("power.device.model")).thenReturn(true);
        JdbcPowerIdempotencyStore idempotency = new JdbcPowerIdempotencyStore(dataSource);
        PowerModelTemplateContentValidator validator = new PowerModelTemplateContentValidator(
                mapper.readTree(PowerModelTemplatePublishPostgresIntegrationTest.class
                        .getClassLoader().getResourceAsStream(
                                "schemas/power-model/easyaiot-power-model-template.schema.json")));
        PowerModelOutboxRepository repository = suppliedRepository == null
                ? new JdbcPowerModelOutboxRepository(dataSource) : suppliedRepository;
        PowerModelOutboxService outbox = new PowerModelOutboxService(repository, capability);
        PowerModelIdempotencySecretProvider secretProvider = mock(PowerModelIdempotencySecretProvider.class);
        when(secretProvider.getSecret()).thenReturn(SECRET.getBytes(StandardCharsets.UTF_8));
        return new TestContext(dataSource, jdbc, new DataSourceTransactionManager(dataSource), mapper,
                new PowerModelTemplateIdentityService(dataSource, mapper, capability, tenant,
                        idempotency, secretProvider),
                new PowerModelTemplateDraftService(dataSource, mapper, capability, tenant,
                        idempotency, secretProvider, 1024 * 1024),
                new PowerModelTemplatePublishService(dataSource, mapper, capability, tenant,
                        validator, outbox, idempotency, secretProvider));
    }

    private static int count(JdbcTemplate jdbc, String table, long tenantId) {
        return jdbc.queryForObject("SELECT count(*) FROM public." + table + " WHERE tenant_id=?",
                Integer.class, tenantId);
    }

    private static void assertZero(TestContext ctx, long tenantId) {
        assertEquals(0, count(ctx.jdbc, "power_model_member_index", tenantId));
        assertEquals(0, count(ctx.jdbc, "power_model_release_outbox", tenantId));
        assertEquals(0, count(ctx.jdbc, "power_model_audit", tenantId));
        assertEquals(0, count(ctx.jdbc, "power_model_template_version", tenantId));
        assertEquals(0, count(ctx.jdbc, "power_model_template", tenantId));
        assertEquals(0, count(ctx.jdbc, "power_idempotency_record", tenantId));
    }

    private static void assumeEnabled() {
        assumeTrue(Boolean.parseBoolean(System.getenv("TD008_PG_ENABLED")),
                "Set TD008_PG_ENABLED=true to run PostgreSQL contracts");
        assumeTrue(System.getenv("TD005_PG_PASSWORD") != null,
                "Set TD005_PG_PASSWORD without committing credentials");
    }

    private static String environmentOrDefault(String name, String fallback) {
        String value = System.getenv(name);
        return value == null || value.isEmpty() ? fallback : value;
    }

    private static final class TestContext {
        final PooledDataSource dataSource; final JdbcTemplate jdbc;
        final DataSourceTransactionManager manager; final ObjectMapper mapper;
        final PowerModelTemplateIdentityService identity;
        final PowerModelTemplateDraftService drafts;
        final PowerModelTemplatePublishService publish;
        TestContext(PooledDataSource dataSource, JdbcTemplate jdbc,
                    DataSourceTransactionManager manager, ObjectMapper mapper,
                    PowerModelTemplateIdentityService identity,
                    PowerModelTemplateDraftService drafts,
                    PowerModelTemplatePublishService publish) {
            this.dataSource = dataSource; this.jdbc = jdbc; this.manager = manager;
            this.mapper = mapper; this.identity = identity; this.drafts = drafts;
            this.publish = publish;
        }
    }
}
