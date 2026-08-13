package com.basiclab.iot.sink.telemetry.inbox.jdbc;

import com.basiclab.iot.sink.telemetry.inbox.InboxEnvelope;
import com.basiclab.iot.sink.telemetry.inbox.InboxReceiveResult;
import com.basiclab.iot.sink.telemetry.store.WriteResult;
import com.basiclab.iot.sink.telemetry.store.jdbc.JdbcTelemetryStore;
import org.apache.ibatis.datasource.pooled.PooledDataSource;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.jdbc.core.JdbcTemplate;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * TD-003 §10/§13 PG Inbox + TelemetryStore 合同测试（真实 iot-device20）。
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class JdbcTelemetryInboxContractTest {

    private static final String PG_URL = System.getenv().getOrDefault("TD008_PG_URL",
            "jdbc:postgresql://localhost:5432/iot-device20");
    private static final String PG_USER = System.getenv().getOrDefault("TD008_PG_USERNAME", "postgres");
    private static final String PG_PASSWORD = System.getenv().getOrDefault("TD008_PG_PASSWORD",
            System.getenv().getOrDefault("TD005_PG_PASSWORD", ""));
    private static final long TENANT = 999_888_777L;
    private static final long CLEANUP_TEST_TENANT = 999_888_778L;

    private PooledDataSource dataSource;
    private JdbcTelemetryInbox inbox;
    private JdbcTelemetryStore store;

    @BeforeAll
    void setup() {
        dataSource = new PooledDataSource("org.postgresql.Driver", PG_URL, PG_USER, PG_PASSWORD);
        inbox = new JdbcTelemetryInbox(dataSource);
        store = new JdbcTelemetryStore(dataSource);
        // 清理上次测试残留
        new JdbcTemplate(dataSource).update(
                "DELETE FROM iot_sink.telemetry_inbox WHERE tenant_id IN (?, ?)", TENANT, CLEANUP_TEST_TENANT);
        new JdbcTemplate(dataSource).update(
                "DELETE FROM iot_sink.telemetry_sample WHERE tenant_id IN (?, ?)", TENANT, CLEANUP_TEST_TENANT);
    }

    @AfterAll
    void cleanup() {
        if (dataSource != null) {
            new JdbcTemplate(dataSource).update(
                    "DELETE FROM iot_sink.telemetry_inbox WHERE tenant_id IN (?, ?)", TENANT, CLEANUP_TEST_TENANT);
            new JdbcTemplate(dataSource).update(
                    "DELETE FROM iot_sink.telemetry_sample WHERE tenant_id IN (?, ?)", TENANT, CLEANUP_TEST_TENANT);
            dataSource.forceCloseAll();
        }
    }

    private InboxEnvelope env(String msgId, String value) {
        String json = "{\"schemaVersion\":\"1.0\",\"messageId\":\"" + msgId + "\","
                + "\"tenantId\":\"" + TENANT + "\",\"siteCode\":\"site-test\","
                + "\"deviceIdentification\":\"dev-test\",\"propertyCode\":\"voltage-a\","
                + "\"value\":\"" + value + "\",\"collectedAt\":\"2026-08-13T00:00:00Z\","
                + "\"source\":\"modbus-rtu\"}";
        byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
        String sha256 = sha256(bytes);
        return new InboxEnvelope(msgId, "req-" + msgId, String.valueOf(TENANT), "site-test",
                "dev-test", "voltage-a", bytes, sha256,
                System.currentTimeMillis(), 1, "modbus-rtu", 1);
    }

    @Test
    void newEnvelopeReceived() {
        InboxReceiveResult result = inbox.receiveEnvelopes(List.of(env("pg-test-msg-1", "220.5")));
        assertInstanceOf(InboxReceiveResult.Received.class, result);
        assertEquals(1, ((InboxReceiveResult.Received) result).messageIds().size());
    }

    @Test
    void duplicateSameHashSkipped() {
        InboxEnvelope e = env("pg-test-msg-2", "221.0");
        inbox.receiveEnvelopes(List.of(e));
        InboxReceiveResult result2 = inbox.receiveEnvelopes(List.of(e));
        // 第二次是重复（同 messageId 同 hash）
        assertInstanceOf(InboxReceiveResult.Received.class, result2);
        assertEquals(0, ((InboxReceiveResult.Received) result2).messageIds().size());
    }

    @Test
    void storeWritesSample() {
        InboxEnvelope e = env("pg-store-test-1", "230.5");
        WriteResult result = store.writeSample(e);
        assertEquals(WriteResult.STORED, result);
    }

    @Test
    void storeDuplicateReturnsDuplicate() {
        InboxEnvelope e = env("pg-store-test-2", "231.0");
        store.writeSample(e);
        WriteResult result = store.writeSample(e);
        assertEquals(WriteResult.DUPLICATE, result);
    }

    @Test
    void storeDifferentHashForSameMessageIdStored() {
        // 同 messageId 不同 hash（不同 value）→ 不同 content_sha256 → 应 STORED（非 DUPLICATE）
        InboxEnvelope e1 = env("pg-store-test-3", "240.0");
        store.writeSample(e1);
        InboxEnvelope e2 = env("pg-store-test-3", "241.0"); // 同 messageId 不同 value
        WriteResult result = store.writeSample(e2);
        assertEquals(WriteResult.STORED, result, "same messageId different hash should STORE (not DUPLICATE)");
    }

    private static String sha256(byte[] input) {
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256").digest(input);
            StringBuilder hex = new StringBuilder(hash.length * 2);
            for (byte b : hash) {
                hex.append(Character.forDigit((b >> 4) & 0xF, 16));
                hex.append(Character.forDigit(b & 0xF, 16));
            }
            return hex.toString();
        } catch (Exception e) {
            return "error";
        }
    }
}
