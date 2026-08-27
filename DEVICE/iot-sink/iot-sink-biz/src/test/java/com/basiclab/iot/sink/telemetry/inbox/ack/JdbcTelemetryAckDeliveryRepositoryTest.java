package com.basiclab.iot.sink.telemetry.inbox.ack;

import org.apache.ibatis.datasource.pooled.PooledDataSource;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * LC03-03 §5.2/§5.3 dispatch repository 直接合同（显式隔离 PostgreSQL）。
 *
 * <p>覆盖：V012 列就绪后的领取顺序与上限、attempts 递增、markSent
 * 条件更新幂等、行事实不完整 fail-closed、产品/设备路由必齐。
 * 未提供隔离 PG 环境变量时整类按既有口径 NOT_RUN_LOCAL_ENV 跳过。
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class JdbcTelemetryAckDeliveryRepositoryTest {

    private static final String PG_URL = firstDefined("LC03_PG_URL", "LC02_08_PG_URL", "TD008_PG_URL");
    private static final String PG_USER = firstDefined("LC03_PG_USERNAME", "LC02_08_PG_USERNAME", "TD008_PG_USERNAME");
    private static final String PG_PASSWORD = firstDefined("LC03_PG_PASSWORD", "LC02_08_PG_PASSWORD", "TD008_PG_PASSWORD");
    private static final long TENANT = 999_777_666L;

    private PooledDataSource dataSource;
    private JdbcTemplate jdbc;
    private JdbcTelemetryAckDeliveryRepository repository;
    private String runPrefix;

    @BeforeAll
    void setup() {
        Assumptions.assumeTrue(PG_URL != null && !PG_URL.isBlank()
                        && PG_USER != null && !PG_USER.isBlank()
                        && PG_PASSWORD != null,
                "NOT_RUN_LOCAL_ENV: isolated PostgreSQL variables are not set");
        dataSource = new PooledDataSource("org.postgresql.Driver", PG_URL, PG_USER, PG_PASSWORD);
        try (java.sql.Connection ignored = dataSource.getConnection()) {
            // isolated PostgreSQL is optional for this suite
        } catch (Exception e) {
            dataSource.forceCloseAll();
            dataSource = null;
            Assumptions.assumeTrue(false,
                    "NOT_RUN_LOCAL_ENV: PostgreSQL is unavailable at " + PG_URL);
        }
        jdbc = new JdbcTemplate(dataSource);
        repository = new JdbcTelemetryAckDeliveryRepository(dataSource);
        runPrefix = "lc03-ack-" + Long.toUnsignedString(System.nanoTime(), 36);
        ensureV012Columns();
        jdbc.update("DELETE FROM iot_sink.telemetry_inbox WHERE tenant_id = ?"
                + " AND message_id LIKE ?", TENANT, runPrefix + "%");
    }

    /** 隔离库演练 V012 候选：列缺失时补列（仅测试库；生产 DDL 另授权）。 */
    private void ensureV012Columns() {
        Boolean hasSent = jdbc.queryForObject(
                "SELECT EXISTS (SELECT 1 FROM information_schema.columns"
                        + " WHERE table_schema='iot_sink' AND table_name='telemetry_inbox'"
                        + " AND column_name='ack_sent_at_ms')", Boolean.class);
        if (Boolean.FALSE.equals(hasSent)) {
            jdbc.execute("ALTER TABLE iot_sink.telemetry_inbox"
                    + " ADD COLUMN ack_sent_at_ms BIGINT,"
                    + " ADD COLUMN ack_attempts INTEGER NOT NULL DEFAULT 0");
        }
        Boolean hasIndex = jdbc.queryForObject(
                "SELECT to_regclass('iot_sink.idx_inbox_ack_pending') IS NOT NULL", Boolean.class);
        if (Boolean.FALSE.equals(hasIndex)) {
            jdbc.execute("CREATE INDEX idx_inbox_ack_pending"
                    + " ON iot_sink.telemetry_inbox(received_at_ms, id)"
                    + " WHERE ack_sent_at_ms IS NULL");
        }
    }

    @AfterAll
    void cleanup() {
        if (dataSource != null) {
            jdbc.update("DELETE FROM iot_sink.telemetry_inbox WHERE tenant_id = ?"
                    + " AND message_id LIKE ?", TENANT, runPrefix + "%");
            dataSource.forceCloseAll();
        }
    }

    private void insertRow(String suffix, long receivedAtMs, String product, String requestId) {
        jdbc.update("INSERT INTO iot_sink.telemetry_inbox"
                + "(message_id, message_id_wire, request_id, tenant_id, product_identification,"
                + " site_code, device_identification, property_code, payload, content_sha256,"
                + " collected_at_ms, sequence_no, source, config_version, projection_state,"
                + " received_at_ms, updated_at_ms)"
                + " VALUES (?,?,?,?,?,?,?,?,digest_empty_bytea(1),?,0,0,'modbus-rtu',0,'RECEIVED',?,?)"
                        .replace("digest_empty_bytea(1)", "'{}'::bytea"),
                runPrefix + "-" + suffix, runPrefix + "-" + suffix, requestId, TENANT, product,
                "site-test", "meter-01", "voltage-a",
                shaOf(suffix), receivedAtMs, receivedAtMs);
    }

    private static String shaOf(String suffix) {
        return String.format("%064x", Math.abs((long) suffix.hashCode()));
    }

    @Test
    void claimPendingReturnsRowsInReceivedOrderAndIncrementsAttempts() {
        insertRow("order-1", 1000L, "power-meter", "a9afddc7-02ee-4df3-905b-ec3e4107f25d");
        insertRow("order-2", 2000L, "power-meter", "a9afddc7-02ee-4df3-905b-ec3e4107f25d");

        List<TelemetryAckDeliveryRow> rows = repository.claimPending(10);

        List<String> claimed = rows.stream()
                .filter(row -> row.messageIdWire().startsWith(runPrefix))
                .map(TelemetryAckDeliveryRow::messageIdWire)
                .toList();
        assertEquals(List.of(runPrefix + "-order-1", runPrefix + "-order-2"), claimed);
        Integer attempts = jdbc.queryForObject(
                "SELECT ack_attempts FROM iot_sink.telemetry_inbox WHERE message_id = ?",
                Integer.class, runPrefix + "-order-1");
        assertEquals(1, attempts);
    }

    @Test
    void claimPendingRespectsLimit() {
        insertRow("limit-1", 3000L, "power-meter", "a9afddc7-02ee-4df3-905b-ec3e4107f25d");
        insertRow("limit-2", 3100L, "power-meter", "a9afddc7-02ee-4df3-905b-ec3e4107f25d");

        List<TelemetryAckDeliveryRow> rows = repository.claimPending(1);

        List<TelemetryAckDeliveryRow> ours = rows.stream()
                .filter(row -> row.messageIdWire().startsWith(runPrefix + "-limit"))
                .toList();
        assertTrue(ours.size() <= 1, "claim limit must be respected");
    }

    @Test
    void markedSentRowsLeavePendingSet() {
        insertRow("sent-1", 4000L, "power-meter", "a9afddc7-02ee-4df3-905b-ec3e4107f25d");
        List<TelemetryAckDeliveryRow> claimed = repository.claimPending(1000);
        TelemetryAckDeliveryRow target = claimed.stream()
                .filter(row -> row.messageIdWire().equals(runPrefix + "-sent-1"))
                .findFirst().orElseThrow();

        assertTrue(repository.markSent(target.tenantId(), target.messageIdWire(), 5000L));

        List<TelemetryAckDeliveryRow> again = repository.claimPending(1000);
        assertTrue(again.stream().noneMatch(row ->
                row.messageIdWire().equals(runPrefix + "-sent-1")));
    }

    @Test
    void markSentIsConditionalAndIdempotent() {
        insertRow("cond-1", 4500L, "power-meter", "a9afddc7-02ee-4df3-905b-ec3e4107f25d");
        assertTrue(repository.markSent(TENANT, runPrefix + "-cond-1", 4600L));
        assertFalse(repository.markSent(TENANT, runPrefix + "-cond-1", 4700L),
                "second mark on already-sent row must not update");
        Long sentAt = jdbc.queryForObject(
                "SELECT ack_sent_at_ms FROM iot_sink.telemetry_inbox WHERE message_id = ?",
                Long.class, runPrefix + "-cond-1");
        assertEquals(4600L, sentAt);
    }

    @Test
    void loadForImmediateAckFailsClosedOnMissingOrIncompleteRow() {
        assertNull(repository.loadForImmediateAck(TENANT, runPrefix + "-missing-404"));

        insertRow("noproduct", 4700L, null, "a9afddc7-02ee-4df3-905b-ec3e4107f25d");
        assertNull(repository.loadForImmediateAck(TENANT, runPrefix + "-noproduct"),
                "row without product identity must not enter the send chain");

        insertRow("noreq", 4800L, "power-meter", null);
        assertNull(repository.loadForImmediateAck(TENANT, runPrefix + "-noreq"),
                "row without requestId must not enter the send chain");
    }

    @Test
    void loadForImmediateAckReturnsRowAndIncrementsAttempts() {
        insertRow("imm-1", 5000L, "power-meter", "a9afddc7-02ee-4df3-905b-ec3e4107f25d");
        TelemetryAckDeliveryRow row = repository.loadForImmediateAck(
                TENANT, runPrefix + "-imm-1");

        assertNotNull(row);
        assertEquals(runPrefix + "-imm-1", row.messageIdWire());
        assertEquals("a9afddc7-02ee-4df3-905b-ec3e4107f25d", row.requestId());
        assertEquals("power-meter", row.route().productIdentification());
        assertEquals(5000L, row.receivedAtMs());
        assertEquals(1, row.ackAttempts());
    }

    private static String firstDefined(String... names) {
        for (String name : names) {
            String value = System.getenv(name);
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return null;
    }
}
