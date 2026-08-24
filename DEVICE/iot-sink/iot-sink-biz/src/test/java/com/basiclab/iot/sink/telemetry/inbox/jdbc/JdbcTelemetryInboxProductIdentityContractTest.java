package com.basiclab.iot.sink.telemetry.inbox.jdbc;

import com.basiclab.iot.sink.telemetry.inbox.InboxEnvelope;
import com.basiclab.iot.sink.telemetry.inbox.InboxReceiveResult;
import org.apache.ibatis.datasource.pooled.PooledDataSource;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.dao.DataAccessException;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.sql.Connection;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * LC02-08 direct PostgreSQL contract.  The fixture is created by the isolated
 * lc02_08_inbox_product_contract.sh orchestrator; no schema is created here.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class JdbcTelemetryInboxProductIdentityContractTest {

    private static final String PG_URL = System.getenv("LC02_08_PG_URL");
    private static final String PG_USER = System.getenv("LC02_08_PG_USERNAME");
    private static final String PG_PASSWORD = System.getenv("LC02_08_PG_PASSWORD");
    private static final long TENANT = 999_888_777L;
    private static final long OTHER_TENANT = 999_888_778L;

    private PooledDataSource dataSource;
    private JdbcTemplate jdbc;
    private JdbcTelemetryInbox inbox;
    private String runPrefix;

    @BeforeAll
    void setUp() {
        Assumptions.assumeTrue(PG_URL != null && !PG_URL.isBlank()
                        && PG_USER != null && !PG_USER.isBlank()
                        && PG_PASSWORD != null,
                "NOT_RUN_LOCAL_ENV: LC02_08_PG_URL/USERNAME/PASSWORD are not set");
        dataSource = new PooledDataSource("org.postgresql.Driver", PG_URL, PG_USER, PG_PASSWORD);
        try (Connection ignored = dataSource.getConnection()) {
            // The isolated orchestrator owns schema creation and credentials.
        } catch (Exception exception) {
            dataSource.forceCloseAll();
            dataSource = null;
            Assumptions.assumeTrue(false, "NOT_RUN_LOCAL_ENV: isolated PostgreSQL unavailable");
        }
        jdbc = new JdbcTemplate(dataSource);
        inbox = new JdbcTelemetryInbox(dataSource);
        runPrefix = "lc02-08-" + Long.toUnsignedString(System.nanoTime(), 36);
    }

    @AfterAll
    void tearDown() {
        if (dataSource == null) {
            return;
        }
        deleteRunRows();
        dataSource.forceCloseAll();
    }

    @Test
    void newKeyStoresAuthorizedProductAndCanonicalBytes() {
        InboxEnvelope envelope = envelope("new-key", "product-a");

        InboxReceiveResult.Item item = item(inbox.receiveEnvelopes(List.of(envelope)));
        assertEquals(InboxReceiveResult.Status.ACCEPTED_DURABLE, item.status());
        Map<String, Object> row = snapshot(envelope);
        assertEquals("product-a", row.get("product_identification"));
        assertArrayEquals(envelope.canonicalBytes(), (byte[]) row.get("payload"));
        assertEquals(envelope.contentSha256(), row.get("content_sha256"));
        assertEquals(1, countRows(envelope));
    }

    @Test
    void existingNonNullSameProductAndSixFieldsIsDuplicate() {
        InboxEnvelope envelope = envelope("non-null-duplicate", "product-a");

        InboxReceiveResult.Item first = item(inbox.receiveEnvelopes(List.of(envelope)));
        InboxReceiveResult.Item second = item(inbox.receiveEnvelopes(List.of(envelope)));
        assertEquals(InboxReceiveResult.Status.ACCEPTED_DURABLE, first.status());
        assertEquals(InboxReceiveResult.Status.DUPLICATE, second.status());
        assertEquals(first.persistedAtMs(), second.persistedAtMs());
    }

    @Test
    void existingNonNullDifferentProductIsCollisionAndWholeRowStaysUnchanged() {
        InboxEnvelope first = envelope("non-null-product-collision", "product-a");
        InboxEnvelope second = copy(first, "product-b", first.requestId(), first.siteCode(),
                first.deviceIdentification(), first.propertyCode(), first.canonicalBytes(),
                first.contentSha256(), first.tenantId());

        item(inbox.receiveEnvelopes(List.of(first)));
        Map<String, Object> before = snapshot(first);
        InboxReceiveResult.Item collision = item(inbox.receiveEnvelopes(List.of(second)));
        Map<String, Object> after = snapshot(first);

        assertEquals(InboxReceiveResult.Status.MESSAGE_ID_COLLISION, collision.status());
        assertNull(collision.persistedAtMs());
        assertSnapshotUnchanged(before, after);
        assertEquals("product-a", after.get("product_identification"));
        assertEquals(1, countRows(first));
    }

    @Test
    void historicalNullSameSixFieldsIsAtomicallyBackfilledAsDuplicate() {
        InboxEnvelope envelope = historicalNull("historical-null-backfill", "product-a");
        Map<String, Object> before = snapshot(envelope);

        InboxReceiveResult.Item duplicate = item(inbox.receiveEnvelopes(List.of(envelope)));
        Map<String, Object> after = snapshot(envelope);

        assertEquals(InboxReceiveResult.Status.DUPLICATE, duplicate.status());
        assertEquals(before.get("received_at_ms"), duplicate.persistedAtMs());
        assertSnapshotUnchanged(before, after);
        assertEquals("product-a", after.get("product_identification"));
    }

    @Test
    void historicalNullAnySixFieldDifferenceIsCollisionAndRemainsNull() {
        assertHistoricalNullCollision("historical-null-hash", base -> copy(base, "product-a",
                base.requestId(), base.siteCode(), base.deviceIdentification(), base.propertyCode(),
                "different-payload".getBytes(StandardCharsets.UTF_8), sha256("different-payload".getBytes(StandardCharsets.UTF_8)),
                base.tenantId()));
        assertHistoricalNullCollision("historical-null-request", base -> copy(base, "product-a",
                "different-request", base.siteCode(), base.deviceIdentification(), base.propertyCode(),
                base.canonicalBytes(), base.contentSha256(), base.tenantId()));
        assertHistoricalNullCollision("historical-null-site", base -> copy(base, "product-a",
                base.requestId(), "different-site", base.deviceIdentification(), base.propertyCode(),
                base.canonicalBytes(), base.contentSha256(), base.tenantId()));
        assertHistoricalNullCollision("historical-null-device", base -> copy(base, "product-a",
                base.requestId(), base.siteCode(), "different-device", base.propertyCode(),
                base.canonicalBytes(), base.contentSha256(), base.tenantId()));
        assertHistoricalNullCollision("historical-null-property", base -> copy(base, "product-a",
                base.requestId(), base.siteCode(), base.deviceIdentification(), "different-property",
                base.canonicalBytes(), base.contentSha256(), base.tenantId()));
    }

    @Test
    void historicalNullSameProductConcurrentReplayProducesTwoDuplicates() throws Exception {
        InboxEnvelope envelope = historicalNull("historical-null-concurrent-same", "product-a");
        Map<String, Object> before = snapshot(envelope);

        List<InboxReceiveResult.Item> results = concurrently(envelope, envelope);
        Map<String, Object> after = snapshot(envelope);

        assertEquals(List.of(InboxReceiveResult.Status.DUPLICATE,
                InboxReceiveResult.Status.DUPLICATE), statuses(results));
        assertSnapshotUnchanged(before, after);
        assertEquals("product-a", after.get("product_identification"));
        assertEquals(1, countRows(envelope));
    }

    @Test
    void historicalNullDifferentProductsConcurrentReplayHasOneWinnerAndOneCollision() throws Exception {
        InboxEnvelope first = historicalNull("historical-null-concurrent-different", "product-a");
        InboxEnvelope second = copy(first, "product-b", first.requestId(), first.siteCode(),
                first.deviceIdentification(), first.propertyCode(), first.canonicalBytes(),
                first.contentSha256(), first.tenantId());
        Map<String, Object> before = snapshot(first);

        List<InboxReceiveResult.Item> results = concurrently(first, second);
        Map<String, Object> after = snapshot(first);

        assertEquals(1, results.stream().filter(result ->
                result.status() == InboxReceiveResult.Status.DUPLICATE).count());
        assertEquals(1, results.stream().filter(result ->
                result.status() == InboxReceiveResult.Status.MESSAGE_ID_COLLISION).count());
        assertSnapshotUnchanged(before, after);
        assertTrue("product-a".equals(after.get("product_identification"))
                || "product-b".equals(after.get("product_identification")));
        assertEquals(1, countRows(first));
    }

    @Test
    void newKeySameProductConcurrentInsertHasOneAcceptedAndOneDuplicate() throws Exception {
        InboxEnvelope first = envelope("new-concurrent-same", "product-a");
        InboxEnvelope second = copy(first, "product-a", first.requestId(), first.siteCode(),
                first.deviceIdentification(), first.propertyCode(), first.canonicalBytes(),
                first.contentSha256(), first.tenantId());

        List<InboxReceiveResult.Item> results = concurrently(first, second);
        assertEquals(1, results.stream().filter(result ->
                result.status() == InboxReceiveResult.Status.ACCEPTED_DURABLE).count());
        assertEquals(1, results.stream().filter(result ->
                result.status() == InboxReceiveResult.Status.DUPLICATE).count());
        assertEquals(results.get(0).persistedAtMs(), results.get(1).persistedAtMs());
        assertEquals("product-a", snapshot(first).get("product_identification"));
        assertEquals(1, countRows(first));
    }

    @Test
    void newKeyDifferentProductsConcurrentInsertHasOneAcceptedAndOneCollision() throws Exception {
        InboxEnvelope first = envelope("new-concurrent-different", "product-a");
        InboxEnvelope second = copy(first, "product-b", first.requestId(), first.siteCode(),
                first.deviceIdentification(), first.propertyCode(), first.canonicalBytes(),
                first.contentSha256(), first.tenantId());

        List<InboxReceiveResult.Item> results = concurrently(first, second);
        assertEquals(1, results.stream().filter(result ->
                result.status() == InboxReceiveResult.Status.ACCEPTED_DURABLE).count());
        assertEquals(1, results.stream().filter(result ->
                result.status() == InboxReceiveResult.Status.MESSAGE_ID_COLLISION).count());
        assertNull(results.stream().filter(result ->
                result.status() == InboxReceiveResult.Status.MESSAGE_ID_COLLISION)
                .findFirst().orElseThrow().persistedAtMs());
        Object winner = snapshot(first).get("product_identification");
        assertTrue("product-a".equals(winner) || "product-b".equals(winner));
        assertEquals(1, countRows(first));
    }

    @Test
    void sameMessageIdAcrossTenantsIsIndependent() {
        InboxEnvelope first = envelope("cross-tenant", "product-a");
        InboxEnvelope second = copy(first, "product-b", first.requestId(), first.siteCode(),
                first.deviceIdentification(), first.propertyCode(), first.canonicalBytes(),
                first.contentSha256(), String.valueOf(OTHER_TENANT));

        assertEquals(InboxReceiveResult.Status.ACCEPTED_DURABLE,
                item(inbox.receiveEnvelopes(List.of(first))).status());
        assertEquals(InboxReceiveResult.Status.ACCEPTED_DURABLE,
                item(inbox.receiveEnvelopes(List.of(second))).status());
        assertEquals(1, countRows(first));
        assertEquals(1, countRows(second));
    }

    @Test
    void missingV009ColumnPropagatesDatabaseFailureWithoutLegacyFallback() {
        String missingUrl = System.getenv("LC02_08_MISSING_V009_PG_URL");
        Assumptions.assumeTrue(missingUrl != null && !missingUrl.isBlank(),
                "NOT_RUN_LOCAL_ENV: missing-column fixture URL is not set");
        PooledDataSource missingDataSource = new PooledDataSource(
                "org.postgresql.Driver", missingUrl, PG_USER, PG_PASSWORD);
        try {
            JdbcTelemetryInbox missingInbox = new JdbcTelemetryInbox(missingDataSource);
            InboxEnvelope envelope = envelope("missing-v009-column", "product-a");
            assertThrows(DataAccessException.class,
                    () -> missingInbox.receiveEnvelopes(List.of(envelope)));
            assertEquals(0, new JdbcTemplate(missingDataSource).queryForObject(
                    "SELECT count(*) FROM iot_sink.telemetry_inbox"
                            + " WHERE tenant_id = ? AND message_id = ?",
                    Integer.class, TENANT, envelope.messageId()));
        } finally {
            missingDataSource.forceCloseAll();
        }
    }

    private void assertHistoricalNullCollision(String suffix,
                                               java.util.function.UnaryOperator<InboxEnvelope> variant) {
        InboxEnvelope base = historicalNull(suffix, "product-a");
        Map<String, Object> before = snapshot(base);
        InboxReceiveResult.Item result = item(inbox.receiveEnvelopes(List.of(variant.apply(base))));
        Map<String, Object> after = snapshot(base);

        assertEquals(InboxReceiveResult.Status.MESSAGE_ID_COLLISION, result.status());
        assertNull(result.persistedAtMs());
        assertSnapshotUnchanged(before, after);
        assertNull(after.get("product_identification"));
    }

    private InboxEnvelope historicalNull(String suffix, String product) {
        InboxEnvelope envelope = envelope(suffix, product);
        item(inbox.receiveEnvelopes(List.of(envelope)));
        jdbc.update("UPDATE iot_sink.telemetry_inbox SET product_identification = NULL"
                        + " WHERE tenant_id = ? AND message_id = ?",
                Long.parseLong(envelope.tenantId()), envelope.messageId());
        assertNull(snapshot(envelope).get("product_identification"));
        return envelope;
    }

    private InboxEnvelope envelope(String suffix, String product) {
        String messageId = id(suffix);
        String payloadText = "{\"messageId\":\"" + messageId + "\",\"value\":\"220\"}";
        byte[] payload = payloadText.getBytes(StandardCharsets.UTF_8);
        return new InboxEnvelope(messageId, "request-" + suffix, String.valueOf(TENANT), product,
                "site-1", "device-1", "property-1", payload, sha256(payload), 1_700_000_000_000L,
                1L, "collector", 7L);
    }

    private InboxEnvelope copy(InboxEnvelope source, String product, String requestId,
                               String siteCode, String deviceIdentification, String propertyCode,
                               byte[] payload, String contentSha256, String tenantId) {
        return new InboxEnvelope(source.messageId(), requestId, tenantId, product, siteCode,
                deviceIdentification, propertyCode, payload, contentSha256, source.collectedAtMs(),
                source.sequence(), source.source(), source.configVersion());
    }

    private List<InboxReceiveResult.Item> concurrently(InboxEnvelope first,
                                                       InboxEnvelope second) throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CyclicBarrier barrier = new CyclicBarrier(3);
        try {
            Future<InboxReceiveResult.Item> left = executor.submit(() -> {
                barrier.await(10, TimeUnit.SECONDS);
                return item(inbox.receiveEnvelopes(List.of(first)));
            });
            Future<InboxReceiveResult.Item> right = executor.submit(() -> {
                barrier.await(10, TimeUnit.SECONDS);
                return item(inbox.receiveEnvelopes(List.of(second)));
            });
            barrier.await(10, TimeUnit.SECONDS);
            return List.of(left.get(10, TimeUnit.SECONDS), right.get(10, TimeUnit.SECONDS));
        } finally {
            executor.shutdownNow();
            assertTrue(executor.awaitTermination(10, TimeUnit.SECONDS),
                    "concurrency executor did not terminate");
        }
    }

    private List<InboxReceiveResult.Status> statuses(List<InboxReceiveResult.Item> results) {
        return results.stream().map(InboxReceiveResult.Item::status).sorted().toList();
    }

    private InboxReceiveResult.Item item(InboxReceiveResult result) {
        return ((InboxReceiveResult.Batch) result).items().get(0);
    }

    private Map<String, Object> snapshot(InboxEnvelope envelope) {
        return jdbc.queryForMap("SELECT product_identification, content_sha256, request_id,"
                        + " message_id_wire, site_code, device_identification, property_code,"
                        + " payload, collected_at_ms, sequence_no, source, config_version,"
                        + " projection_state, projection_attempts, projection_lease_until,"
                        + " next_projection_at_ms, projected_at_ms, last_projection_error,"
                        + " received_at_ms, updated_at_ms, created_at"
                        + " FROM iot_sink.telemetry_inbox WHERE tenant_id = ? AND message_id = ?",
                Long.parseLong(envelope.tenantId()), envelope.messageId());
    }

    private int countRows(InboxEnvelope envelope) {
        return jdbc.queryForObject("SELECT count(*) FROM iot_sink.telemetry_inbox"
                        + " WHERE tenant_id = ? AND message_id = ?", Integer.class,
                Long.parseLong(envelope.tenantId()), envelope.messageId());
    }

    private void assertSnapshotUnchanged(Map<String, Object> before, Map<String, Object> after) {
        assertEquals(before.keySet(), after.keySet());
        for (String key : before.keySet()) {
            if ("product_identification".equals(key)) {
                continue;
            }
            Object left = before.get(key);
            Object right = after.get(key);
            if (left instanceof byte[] leftBytes && right instanceof byte[] rightBytes) {
                assertArrayEquals(leftBytes, rightBytes, key);
            } else if (left instanceof byte[] || right instanceof byte[]) {
                assertTrue(false, "snapshot type changed for " + key);
            } else {
                assertEquals(left, right, key);
            }
        }
    }

    private String id(String suffix) {
        return runPrefix + "-" + suffix;
    }

    private void deleteRunRows() {
        if (jdbc == null || runPrefix == null) {
            return;
        }
        String pattern = runPrefix + "-%";
        jdbc.update("DELETE FROM iot_sink.telemetry_inbox WHERE tenant_id IN (?, ?)"
                        + " AND message_id LIKE ?", TENANT, OTHER_TENANT, pattern);
        jdbc.update("DELETE FROM iot_sink.telemetry_sample WHERE tenant_id IN (?, ?)"
                        + " AND message_id LIKE ?", TENANT, OTHER_TENANT, pattern);
    }

    private static String sha256(byte[] input) {
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256").digest(input);
            StringBuilder hex = new StringBuilder(hash.length * 2);
            for (byte value : hash) {
                hex.append(Character.forDigit((value >>> 4) & 0xF, 16));
                hex.append(Character.forDigit(value & 0xF, 16));
            }
            return hex.toString();
        } catch (Exception exception) {
            throw new AssertionError(exception);
        }
    }
}
