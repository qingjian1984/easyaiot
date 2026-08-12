package com.basiclab.iot.device.service.power;

import com.basiclab.iot.common.capability.CapabilityService;
import com.basiclab.iot.device.config.PowerModelIdempotencySecretProvider;
import java.nio.charset.StandardCharsets;
import com.basiclab.iot.common.core.service.TenantFrameworkService;
import com.basiclab.iot.device.controller.power.dto.PowerModelTemplateCreateRequest;
import com.basiclab.iot.device.controller.power.dto.PowerModelTemplateCreateResponse;
import com.basiclab.iot.device.service.idempotency.JdbcPowerIdempotencyStore;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.ibatis.datasource.pooled.PooledDataSource;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** TD-005 §11.1/§17：模板 identity 创建、重放、唯一冲突与回滚的真实库合同。 */
class PowerModelTemplateIdentityPostgresIntegrationTest {

    private static final long TENANT = 920_010_001L;
    private static final long ACTOR = 920_010_002L;

    @Test
    void createsTenantIdentityReplaysAndRejectsConflictsWithoutResidue() {
        assumeEnabled();
        TestContext ctx = context();
        new TransactionTemplate(ctx.manager).executeWithoutResult(status -> {
            PowerModelTemplateCreateRequest request = request("td010-meter", "TD010 电表");
            PowerModelTemplateCreateResponse created = ctx.service.create(
                    TENANT, request, ACTOR, "td010-key-create");
            assertEquals("td010-meter", created.getTemplateCode());
            assertEquals("TENANT", created.getOwnerScope());
            assertEquals("0", created.getRowVersion());
            assertEquals(1, count(ctx.jdbc, "power_model_template"));
            assertEquals(1, count(ctx.jdbc, "power_idempotency_record"));

            PowerModelTemplateCreateResponse replay = ctx.service.create(
                    TENANT, request, ACTOR, "td010-key-create");
            assertEquals(created.getTemplateId(), replay.getTemplateId());
            assertEquals(1, count(ctx.jdbc, "power_model_template"));

            IllegalArgumentException reused = assertThrows(IllegalArgumentException.class,
                    () -> ctx.service.create(TENANT,
                            request("td010-meter", "不同请求"), ACTOR, "td010-key-create"));
            assertTrue(reused.getMessage().startsWith("IDEMPOTENCY_KEY_REUSED"));

            IllegalArgumentException duplicate = assertThrows(IllegalArgumentException.class,
                    () -> ctx.service.create(TENANT, request, ACTOR, "td010-key-duplicate"));
            assertTrue(duplicate.getMessage().startsWith("MODEL_TEMPLATE_ALREADY_EXISTS"));
            // 第二次不同 key 的幂等 IN_PROGRESS 与业务插入处于同一回滚事务，不会形成孤儿记录。
            status.setRollbackOnly();
        });
        assertEquals(0, count(ctx.jdbc, "power_model_template"));
        assertEquals(0, count(ctx.jdbc, "power_idempotency_record"));
        ctx.dataSource.forceCloseAll();
    }

    private static PowerModelTemplateCreateRequest request(String code, String name) {
        PowerModelTemplateCreateRequest value = new PowerModelTemplateCreateRequest();
        value.setTemplateCode(code);
        value.setTemplateName(name);
        value.setDeviceType("METER");
        value.setTemplateKind("STANDARD");
        return value;
    }

    private static TestContext context() {
        PooledDataSource dataSource = new PooledDataSource("org.postgresql.Driver",
                environmentOrDefault("TD008_PG_URL", "jdbc:postgresql://localhost:5432/iot-device20"),
                environmentOrDefault("TD005_PG_USERNAME", "postgres"),
                System.getenv("TD005_PG_PASSWORD"));
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        CapabilityService capability = mock(CapabilityService.class);
        TenantFrameworkService tenant = mock(TenantFrameworkService.class);
        when(capability.isEnabled(PowerModelTemplateIdentityService.CAPABILITY_CODE)).thenReturn(true);
        PowerModelIdempotencySecretProvider secretProvider = mock(PowerModelIdempotencySecretProvider.class);
        when(secretProvider.getSecret()).thenReturn(
                "td010-review-secret-must-be-at-least-32-bytes".getBytes(StandardCharsets.UTF_8));
        PowerModelTemplateIdentityService service = new PowerModelTemplateIdentityService(
                dataSource, new ObjectMapper(), capability, tenant,
                new JdbcPowerIdempotencyStore(dataSource),
                secretProvider);
        return new TestContext(dataSource, jdbc, new DataSourceTransactionManager(dataSource), service);
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
        final DataSourceTransactionManager manager;
        final PowerModelTemplateIdentityService service;

        TestContext(PooledDataSource dataSource, JdbcTemplate jdbc,
                    DataSourceTransactionManager manager, PowerModelTemplateIdentityService service) {
            this.dataSource = dataSource;
            this.jdbc = jdbc;
            this.manager = manager;
            this.service = service;
        }
    }
}
