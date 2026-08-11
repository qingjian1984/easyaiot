package com.basiclab.iot.device.service.idempotency;

import org.apache.ibatis.datasource.pooled.PooledDataSource;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/** TD-004 §7.12：共用 JDBC 幂等端口的真实 PostgreSQL 终态与有界响应合同。 */
class JdbcPowerIdempotencyStorePostgresIntegrationTest {

    private static final long TENANT = 920_009_001L;

    @Test
    void replaysFinalFailureAndRejectsDifferentRequestHash() {
        assumeEnabled();
        TestContext ctx = context();
        new TransactionTemplate(ctx.manager).executeWithoutResult(status -> {
            JdbcPowerIdempotencyStore.Scope scope = scope("final-failure");
            byte[] requestHash = requestHash("request-a");
            assertEquals(JdbcPowerIdempotencyStore.Claim.Outcome.PROCEED,
                    ctx.store.claim(scope, requestHash).outcome());
            ctx.store.completeFinalFailure(scope, 422,
                    "{\"code\":\"MODEL_TEMPLATE_SCHEMA_INVALID\"}", "draft-1");

            JdbcPowerIdempotencyStore.Claim replay = ctx.store.claim(scope, requestHash);
            assertEquals(JdbcPowerIdempotencyStore.Claim.Outcome.REPLAY, replay.outcome());
            assertEquals("FAILED_FINAL", replay.state());
            assertEquals(422, replay.httpStatus());
            assertEquals("draft-1", replay.resultRef());
            IllegalArgumentException conflict = assertThrows(IllegalArgumentException.class,
                    () -> ctx.store.claim(scope, requestHash("request-b")));
            assertTrue(conflict.getMessage().startsWith("IDEMPOTENCY_KEY_REUSED"));
            status.setRollbackOnly();
        });
        assertEquals(0, ctx.jdbc.queryForObject(
                "SELECT count(*) FROM public.power_idempotency_record WHERE tenant_id=?",
                Integer.class, TENANT));
        ctx.dataSource.forceCloseAll();
    }

    @Test
    void rejectsReplayPayloadLargerThanSixteenKib() {
        assumeEnabled();
        TestContext ctx = context();
        new TransactionTemplate(ctx.manager).executeWithoutResult(status -> {
            JdbcPowerIdempotencyStore.Scope scope = scope("oversized");
            ctx.store.claim(scope, requestHash("request-large"));
            String oversized = "\"" + repeat('x', 16 * 1024) + "\"";
            IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                    () -> ctx.store.completeSuccess(scope, 200, oversized, null));
            assertTrue(error.getMessage().startsWith("IDEMPOTENCY_RESPONSE_INVALID"));
            status.setRollbackOnly();
        });
        assertEquals(0, ctx.jdbc.queryForObject(
                "SELECT count(*) FROM public.power_idempotency_record WHERE tenant_id=?",
                Integer.class, TENANT));
        ctx.dataSource.forceCloseAll();
    }

    private static JdbcPowerIdempotencyStore.Scope scope(String suffix) {
        byte[] keyHash = IdempotencyArbiter.keyHash(
                "td009-review-secret-must-be-at-least-32-bytes".getBytes(StandardCharsets.UTF_8),
                "key-" + suffix);
        return new JdbcPowerIdempotencyStore.Scope(TENANT, "USER", "920009005",
                "TD009_" + suffix.toUpperCase().replace('-', '_'), keyHash);
    }

    private static byte[] requestHash(String value) {
        return IdempotencyArbiter.requestHash("POST", "/api/v1/power/test", value);
    }

    private static String repeat(char value, int count) {
        StringBuilder result = new StringBuilder(count);
        for (int i = 0; i < count; i++) result.append(value);
        return result.toString();
    }

    private static TestContext context() {
        PooledDataSource dataSource = new PooledDataSource("org.postgresql.Driver",
                environmentOrDefault("TD008_PG_URL", "jdbc:postgresql://localhost:5432/iot-device20"),
                environmentOrDefault("TD005_PG_USERNAME", "postgres"),
                System.getenv("TD005_PG_PASSWORD"));
        return new TestContext(dataSource, new JdbcTemplate(dataSource),
                new DataSourceTransactionManager(dataSource),
                new JdbcPowerIdempotencyStore(dataSource));
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
        final JdbcPowerIdempotencyStore store;

        TestContext(PooledDataSource dataSource, JdbcTemplate jdbc,
                    DataSourceTransactionManager manager, JdbcPowerIdempotencyStore store) {
            this.dataSource = dataSource;
            this.jdbc = jdbc;
            this.manager = manager;
            this.store = store;
        }
    }
}
