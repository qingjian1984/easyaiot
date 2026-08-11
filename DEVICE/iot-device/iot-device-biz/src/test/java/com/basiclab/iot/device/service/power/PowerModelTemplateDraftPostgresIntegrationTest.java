package com.basiclab.iot.device.service.power;

import com.basiclab.iot.common.capability.CapabilityService;
import com.basiclab.iot.common.core.service.TenantFrameworkService;
import com.basiclab.iot.device.controller.power.dto.PowerModelTemplateCreateRequest;
import com.basiclab.iot.device.controller.power.dto.PowerModelTemplateDraftResponse;
import com.basiclab.iot.device.controller.power.dto.PowerModelTemplateDraftWriteRequest;
import com.basiclab.iot.device.controller.power.dto.PowerModelTemplateValidationResponse;
import com.basiclab.iot.device.service.idempotency.JdbcPowerIdempotencyStore;
import com.basiclab.iot.device.service.model.PowerModelTemplateContentValidator;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.apache.ibatis.datasource.pooled.PooledDataSource;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** TD-005 §7/§11.1/§17：草稿 canonical、If-Match、重放顺序和零残留真实库合同。 */
class PowerModelTemplateDraftPostgresIntegrationTest {

    private static final long TENANT = 920_011_001L;
    private static final long ACTOR = 920_011_002L;
    private static final String CODE = "td011-meter";

    @Test
    void createsReplacesAndValidatesDraftWithReplayBeforeIfMatch() throws Exception {
        assumeEnabled();
        TestContext ctx = context();
        new TransactionTemplate(ctx.manager).executeWithoutResult(status -> {
            ctx.identity.create(TENANT, identityRequest(), ACTOR, "td011-identity");

            PowerModelTemplateDraftWriteRequest initial = draftRequest(ctx.mapper, null);
            PowerModelTemplateDraftResponse created = ctx.drafts.create(
                    TENANT, CODE, initial, ACTOR, "td011-draft-create");
            assertEquals("0", created.getDraftRevision());
            assertEquals("\"0\"", created.getEtag());
            assertEquals("DRAFT", created.getLifecycle());
            assertEquals(1, count(ctx.jdbc, "power_model_template_version"));
            String stored = ctx.jdbc.queryForObject("SELECT content_hash||':'||draft_revision"
                            + " FROM public.power_model_template_version WHERE tenant_id=?",
                    String.class, TENANT);
            assertEquals(created.getContentHash() + ":0", stored);

            PowerModelTemplateValidationResponse invalid = ctx.validation.validate(
                    TENANT, CODE, Long.parseLong(created.getDraftId()));
            assertTrue(!invalid.isValid());
            assertTrue(invalid.getErrors().size() >= 3);
            assertEquals(null, invalid.getComparisonVersion());
            assertEquals(null, invalid.getMinimumBump());

            PowerModelTemplateDraftWriteRequest changed = draftRequest(ctx.mapper, "第二版草稿");
            PowerModelTemplateDraftResponse updated = ctx.drafts.replace(TENANT, CODE,
                    Long.parseLong(created.getDraftId()), changed, ACTOR,
                    "td011-draft-update", created.getEtag());
            assertEquals("1", updated.getDraftRevision());
            assertEquals("\"1\"", updated.getEtag());
            assertNotEquals(created.getContentHash(), updated.getContentHash());

            // 相同 key + 相同请求必须先重放，不再解析已经过期/非法的 If-Match。
            PowerModelTemplateDraftResponse replay = ctx.drafts.replace(TENANT, CODE,
                    Long.parseLong(created.getDraftId()), changed, ACTOR,
                    "td011-draft-update", "stale-but-ignored-on-replay");
            assertEquals(updated.getContentHash(), replay.getContentHash());
            assertEquals("1", replay.getDraftRevision());

            IllegalArgumentException stale = assertThrows(IllegalArgumentException.class,
                    () -> ctx.drafts.replace(TENANT, CODE, Long.parseLong(created.getDraftId()),
                            changed, ACTOR, "td011-draft-stale", "\"0\""));
            assertTrue(stale.getMessage().startsWith("MODEL_PRECONDITION_FAILED"));
            assertEquals(updated.getContentHash() + ":1",
                    ctx.jdbc.queryForObject("SELECT content_hash||':'||draft_revision"
                                    + " FROM public.power_model_template_version WHERE tenant_id=?",
                            String.class, TENANT));

            // 构造同模板已发布基线，证明 validate 使用最近已发布版本并把过低增量纳入完整错误数组。
            ctx.jdbc.update("UPDATE public.power_model_template_version SET lifecycle='PUBLISHED',"
                            + " draft_state=NULL,last_activity_at=NULL,expires_at=NULL,"
                            + " published_by=?,published_at=CURRENT_TIMESTAMP WHERE tenant_id=? AND id=?",
                    Long.toString(ACTOR), TENANT, Long.parseLong(created.getDraftId()));
            PowerModelTemplateDraftWriteRequest patchVersion = draftRequest(ctx.mapper, "新增可选属性");
            ObjectNode patchContent = (ObjectNode) patchVersion.getContent();
            patchContent.put("version", "1.0.1");
            patchContent.putArray("properties").addObject()
                    .put("propertyCode", "voltage-a").put("required", false);
            patchContent.putArray("events");
            patchContent.putArray("services");
            PowerModelTemplateDraftResponse next = ctx.drafts.create(
                    TENANT, CODE, patchVersion, ACTOR, "td011-second-draft");
            PowerModelTemplateValidationResponse bump = ctx.validation.validate(
                    TENANT, CODE, Long.parseLong(next.getDraftId()));
            assertEquals("1.0.0", bump.getComparisonVersion());
            assertEquals("MINOR", bump.getMinimumBump());
            assertTrue(bump.getErrors().stream().anyMatch(error ->
                    "MODEL_TEMPLATE_SEMVER_BUMP_TOO_LOW".equals(error.getCode())));
            status.setRollbackOnly();
        });
        assertEquals(0, count(ctx.jdbc, "power_model_template_version"));
        assertEquals(0, count(ctx.jdbc, "power_model_template"));
        assertEquals(0, count(ctx.jdbc, "power_idempotency_record"));
        ctx.dataSource.forceCloseAll();
    }

    private static PowerModelTemplateCreateRequest identityRequest() {
        PowerModelTemplateCreateRequest request = new PowerModelTemplateCreateRequest();
        request.setTemplateCode(CODE);
        request.setTemplateName("TD011 电表");
        request.setDeviceType("METER");
        request.setTemplateKind("STANDARD");
        return request;
    }

    private static PowerModelTemplateDraftWriteRequest draftRequest(ObjectMapper mapper,
                                                                    String description) {
        ObjectNode content = mapper.createObjectNode();
        content.put("schemaVersion", "1.0.0");
        content.put("templateCode", CODE);
        content.put("templateName", "TD011 电表");
        content.put("deviceType", "METER");
        content.put("templateKind", "STANDARD");
        content.put("version", "1.0.0");
        if (description != null) content.put("description", description);
        // 草稿允许尚未通过完整 Schema；validate/publish 动作负责 properties/events/services 全量错误。
        PowerModelTemplateDraftWriteRequest request = new PowerModelTemplateDraftWriteRequest();
        request.setContent(content);
        return request;
    }

    private static TestContext context() throws Exception {
        PooledDataSource dataSource = new PooledDataSource("org.postgresql.Driver",
                environmentOrDefault("TD008_PG_URL", "jdbc:postgresql://localhost:5432/iot-device20"),
                environmentOrDefault("TD005_PG_USERNAME", "postgres"),
                System.getenv("TD005_PG_PASSWORD"));
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        ObjectMapper mapper = new ObjectMapper();
        CapabilityService capability = mock(CapabilityService.class);
        TenantFrameworkService tenant = mock(TenantFrameworkService.class);
        when(capability.isEnabled(PowerModelTemplateIdentityService.CAPABILITY_CODE)).thenReturn(true);
        when(capability.isEnabled(PowerModelTemplateDraftService.CAPABILITY_CODE)).thenReturn(true);
        JdbcPowerIdempotencyStore store = new JdbcPowerIdempotencyStore(dataSource);
        String secret = "td011-review-secret-must-be-at-least-32-bytes";
        PowerModelTemplateContentValidator validator = new PowerModelTemplateContentValidator(
                mapper.readTree(PowerModelTemplateDraftPostgresIntegrationTest.class
                        .getClassLoader().getResourceAsStream(
                                "schemas/power-model/easyaiot-power-model-template.schema.json")));
        return new TestContext(dataSource, jdbc, mapper,
                new DataSourceTransactionManager(dataSource),
                new PowerModelTemplateIdentityService(dataSource, mapper, capability, tenant,
                        store, secret),
                new PowerModelTemplateDraftService(dataSource, mapper, capability, tenant,
                        store, secret, 1024 * 1024),
                new PowerModelTemplateValidationService(dataSource, mapper, capability, tenant,
                        validator));
    }

    private static int count(JdbcTemplate jdbc, String table) {
        return jdbc.queryForObject("SELECT count(*) FROM public." + table + " WHERE tenant_id=?",
                Integer.class, TENANT);
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
        final PooledDataSource dataSource;
        final JdbcTemplate jdbc;
        final ObjectMapper mapper;
        final DataSourceTransactionManager manager;
        final PowerModelTemplateIdentityService identity;
        final PowerModelTemplateDraftService drafts;
        final PowerModelTemplateValidationService validation;

        TestContext(PooledDataSource dataSource, JdbcTemplate jdbc, ObjectMapper mapper,
                    DataSourceTransactionManager manager,
                    PowerModelTemplateIdentityService identity,
                    PowerModelTemplateDraftService drafts,
                    PowerModelTemplateValidationService validation) {
            this.dataSource = dataSource;
            this.jdbc = jdbc;
            this.mapper = mapper;
            this.manager = manager;
            this.identity = identity;
            this.drafts = drafts;
            this.validation = validation;
        }
    }
}
