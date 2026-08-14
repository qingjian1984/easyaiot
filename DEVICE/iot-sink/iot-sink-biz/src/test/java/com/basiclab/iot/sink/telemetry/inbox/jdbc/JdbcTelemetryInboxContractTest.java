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
import org.junit.jupiter.api.Assumptions;
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
        try (java.sql.Connection ignored = dataSource.getConnection()) {
            // Local PostgreSQL is optional for this contract suite.
        } catch (Exception e) {
            dataSource.forceCloseAll();
            dataSource = null;
            Assumptions.assumeTrue(false,
                    "NOT_RUN_LOCAL_ENV: PostgreSQL is unavailable at " + PG_URL);
        }
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
        InboxReceiveResult.Item item = item(result, 0);
        assertEquals(InboxReceiveResult.Status.ACCEPTED_DURABLE, item.status());
        assertNotNull(item.persistedAtMs());
    }

    @Test
    void duplicateSameHashSkipped() {
        InboxEnvelope e = env("pg-test-msg-2", "221.0");
        InboxReceiveResult.Item first = item(inbox.receiveEnvelopes(List.of(e)), 0);
        InboxReceiveResult.Item second = item(inbox.receiveEnvelopes(List.of(e)), 0);
        assertEquals(InboxReceiveResult.Status.ACCEPTED_DURABLE, first.status());
        assertEquals(InboxReceiveResult.Status.DUPLICATE, second.status());
        assertEquals(first.persistedAtMs(), second.persistedAtMs());
    }

    @Test
    void sameMessageIdDifferentHashIsCollisionAndKeepsOneRow() {
        InboxEnvelope first = env("pg-inbox-collision-hash", "222.0");
        InboxEnvelope second = env("pg-inbox-collision-hash", "223.0");
        assertEquals(InboxReceiveResult.Status.ACCEPTED_DURABLE,
                item(inbox.receiveEnvelopes(List.of(first)), 0).status());
        InboxReceiveResult.Item collision = item(inbox.receiveEnvelopes(List.of(second)), 0);
        assertEquals(InboxReceiveResult.Status.MESSAGE_ID_COLLISION, collision.status());
        assertNull(collision.persistedAtMs());
        assertEquals(1, countInboxRows(TENANT, first.messageId()));
    }

    @Test
    void sameHashDifferentRequestIdIsCollision() {
        InboxEnvelope first = env("pg-inbox-collision-request", "224.0");
        InboxEnvelope second = new InboxEnvelope(first.messageId(), "different-request", first.tenantId(),
                first.siteCode(), first.deviceIdentification(), first.propertyCode(), first.canonicalBytes(),
                first.contentSha256(), first.collectedAtMs(), first.sequence(), first.source(), first.configVersion());
        inbox.receiveEnvelopes(List.of(first));
        assertEquals(InboxReceiveResult.Status.MESSAGE_ID_COLLISION,
                item(inbox.receiveEnvelopes(List.of(second)), 0).status());
    }

    @Test
    void sameHashDifferentSiteDeviceAndPropertyAreCollisions() {
        InboxEnvelope first = env("pg-inbox-collision-identity", "225.0");
        inbox.receiveEnvelopes(List.of(first));
        InboxEnvelope differentSite = new InboxEnvelope(first.messageId(), first.requestId(), first.tenantId(),
                "other-site", first.deviceIdentification(), first.propertyCode(), first.canonicalBytes(),
                first.contentSha256(), first.collectedAtMs(), first.sequence(), first.source(), first.configVersion());
        InboxEnvelope differentDevice = new InboxEnvelope(first.messageId(), first.requestId(), first.tenantId(),
                first.siteCode(), "other-device", first.propertyCode(), first.canonicalBytes(),
                first.contentSha256(), first.collectedAtMs(), first.sequence(), first.source(), first.configVersion());
        InboxEnvelope differentProperty = new InboxEnvelope(first.messageId(), first.requestId(), first.tenantId(),
                first.siteCode(), first.deviceIdentification(), "other-property", first.canonicalBytes(),
                first.contentSha256(), first.collectedAtMs(), first.sequence(), first.source(), first.configVersion());
        assertEquals(InboxReceiveResult.Status.MESSAGE_ID_COLLISION,
                item(inbox.receiveEnvelopes(List.of(differentSite)), 0).status());
        assertEquals(InboxReceiveResult.Status.MESSAGE_ID_COLLISION,
                item(inbox.receiveEnvelopes(List.of(differentDevice)), 0).status());
        assertEquals(InboxReceiveResult.Status.MESSAGE_ID_COLLISION,
                item(inbox.receiveEnvelopes(List.of(differentProperty)), 0).status());
        assertEquals(1, countInboxRows(TENANT, first.messageId()));
    }

    @Test
    void mixedBatchPreservesInputOrderAndStatus() {
        InboxEnvelope existing = env("pg-inbox-mixed-existing", "226.0");
        inbox.receiveEnvelopes(List.of(existing));
        InboxEnvelope duplicate = env(existing.messageId(), "226.0");
        InboxEnvelope collision = env(existing.messageId(), "227.0");
        InboxEnvelope fresh = env("pg-inbox-mixed-fresh", "228.0");
        InboxReceiveResult.Batch result = batch(inbox.receiveEnvelopes(List.of(duplicate, collision, fresh)));
        assertEquals(3, result.items().size());
        assertEquals(List.of(0, 1, 2), result.items().stream().map(InboxReceiveResult.Item::inputIndex).toList());
        assertEquals(InboxReceiveResult.Status.DUPLICATE, result.items().get(0).status());
        assertEquals(InboxReceiveResult.Status.MESSAGE_ID_COLLISION, result.items().get(1).status());
        assertEquals(InboxReceiveResult.Status.ACCEPTED_DURABLE, result.items().get(2).status());
    }

    @Test
    void emptyAndNullInputsFollowContract() {
        InboxReceiveResult.Batch empty = batch(inbox.receiveEnvelopes(List.of()));
        assertTrue(empty.items().isEmpty());
        assertThrows(NullPointerException.class, () -> inbox.receiveEnvelopes(null));
    }

    @Test
    void batchIsDefensivelyCopied() {
        InboxReceiveResult.Item item = new InboxReceiveResult.Item(0, "message", "request",
                InboxReceiveResult.Status.ACCEPTED_DURABLE, 1L);
        java.util.ArrayList<InboxReceiveResult.Item> source = new java.util.ArrayList<>(List.of(item));
        InboxReceiveResult.Batch batch = new InboxReceiveResult.Batch(source);
        source.clear();
        assertEquals(1, batch.items().size());
        assertThrows(UnsupportedOperationException.class, () -> batch.items().clear());
    }

    @Test
    void sameMessageIdCanBeUsedByDifferentTenants() {
        String messageId = "pg-inbox-cross-tenant";
        InboxEnvelope first = env(messageId, "229.0");
        InboxEnvelope otherTenant = new InboxEnvelope(messageId, "req-" + messageId, String.valueOf(CLEANUP_TEST_TENANT),
                first.siteCode(), first.deviceIdentification(), first.propertyCode(), first.canonicalBytes(),
                first.contentSha256(), first.collectedAtMs(), first.sequence(), first.source(), first.configVersion());
        assertEquals(InboxReceiveResult.Status.ACCEPTED_DURABLE,
                item(inbox.receiveEnvelopes(List.of(first)), 0).status());
        assertEquals(InboxReceiveResult.Status.ACCEPTED_DURABLE,
                item(inbox.receiveEnvelopes(List.of(otherTenant)), 0).status());
    }

    @Test
    void retryAfterPreviouslyCommittedBatchItemIsDuplicate() {
        InboxEnvelope first = env("pg-inbox-retry-committed", "230.0");
        InboxEnvelope fresh = env("pg-inbox-retry-fresh", "231.0");
        inbox.receiveEnvelopes(List.of(first));
        InboxReceiveResult.Batch retry = batch(inbox.receiveEnvelopes(List.of(first, fresh)));
        assertEquals(InboxReceiveResult.Status.DUPLICATE, retry.items().get(0).status());
        assertEquals(InboxReceiveResult.Status.ACCEPTED_DURABLE, retry.items().get(1).status());
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

    private static InboxReceiveResult.Batch batch(InboxReceiveResult result) {
        return assertInstanceOf(InboxReceiveResult.Batch.class, result);
    }

    private static InboxReceiveResult.Item item(InboxReceiveResult result, int index) {
        return batch(result).items().get(index);
    }

    private int countInboxRows(long tenantId, String messageId) {
        return new JdbcTemplate(dataSource).queryForObject(
                "SELECT count(*) FROM iot_sink.telemetry_inbox WHERE tenant_id = ? AND message_id = ?",
                Integer.class, tenantId, messageId);
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
