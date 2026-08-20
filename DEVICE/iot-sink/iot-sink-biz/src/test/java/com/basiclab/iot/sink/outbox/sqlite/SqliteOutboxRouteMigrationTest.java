package com.basiclab.iot.sink.outbox.sqlite;

import com.basiclab.iot.sink.telemetry.envelope.DataPriority;
import com.basiclab.iot.sink.telemetry.envelope.EnvelopeCanonicalCodec;
import com.basiclab.iot.sink.telemetry.envelope.TelemetryEnvelope;
import com.basiclab.iot.sink.telemetry.envelope.TelemetryQuality;
import com.basiclab.iot.sink.telemetry.outbox.AppendBatchResult;
import com.basiclab.iot.sink.telemetry.outbox.ClaimBatchResult;
import com.basiclab.iot.sink.telemetry.outbox.TelemetryOutboxBatch;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.time.Duration;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * LC02-01A contract: SQLite V2→V3 additive routing identity and atomic writes.
 */
class SqliteOutboxRouteMigrationTest {

    private static final Duration ENQUEUE_TIMEOUT = Duration.ofSeconds(5);
    private static final String PRODUCT = "  power-meter/北  ";

    @Test
    void freshV3MigrationIsIdempotentAndExposesProductColumn(@TempDir Path directory) throws Exception {
        Path db = directory.resolve("outbox.db");

        SqliteOutboxMigration.migrate(db);
        SqliteOutboxMigration.migrate(db);

        try (Connection connection = open(db);
             Statement statement = connection.createStatement()) {
            try (ResultSet version = statement.executeQuery("PRAGMA user_version")) {
                assertTrue(version.next());
                assertEquals(3, version.getInt(1));
            }
            boolean hasProductIdentity = false;
            try (ResultSet columns = statement.executeQuery("PRAGMA table_info(telemetry_outbox)")) {
                while (columns.next()) {
                    if ("product_identification".equals(columns.getString("name"))) {
                        hasProductIdentity = true;
                        assertEquals(0, columns.getInt("notnull"),
                                "historical rows must remain representable as NULL");
                    }
                }
            }
            assertTrue(hasProductIdentity, "fresh V3 schema must include product_identification");
        }
    }

    @Test
    void v2UpgradePreservesHistoricalNullAndDoesNotClaimIt(@TempDir Path directory) throws Exception {
        Path db = directory.resolve("outbox.db");
        TelemetryEnvelope historical = envelope("legacy-1", "220.5");
        TelemetryEnvelope inFlight = envelope("legacy-2", "221.5");
        EnvelopeCanonicalCodec codec = new EnvelopeCanonicalCodec();
        EnvelopeCanonicalCodec.CanonicalEnvelope canonical = codec.canonicalize(historical);
        EnvelopeCanonicalCodec.CanonicalEnvelope inFlightCanonical = codec.canonicalize(inFlight);
        createV2Database(db, historical, canonical, inFlight, inFlightCanonical);
        List<Map<String, Object>> beforeMigration = readV2Rows(db);

        SqliteOutboxMigration.migrate(db);
        SqliteOutboxMigration.migrate(db);

        List<Map<String, Object>> afterMigration = readV2Rows(db);
        assertEquals(beforeMigration.size(), afterMigration.size());
        for (int rowIndex = 0; rowIndex < beforeMigration.size(); rowIndex++) {
            Map<String, Object> beforeRow = beforeMigration.get(rowIndex);
            Map<String, Object> afterRow = afterMigration.get(rowIndex);
            assertEquals(beforeRow.keySet(), afterRow.keySet());
            for (String column : beforeRow.keySet()) {
                assertEquals(beforeRow.get(column), afterRow.get(column),
                        "V2 value changed during V3 migration for row " + rowIndex + ", " + column);
            }
        }

        try (Connection connection = open(db);
             Statement statement = connection.createStatement()) {
            try (ResultSet version = statement.executeQuery("PRAGMA user_version")) {
                assertTrue(version.next());
                assertEquals(3, version.getInt(1));
            }
            try (ResultSet row = statement.executeQuery(
                    "SELECT message_id, product_identification, envelope, content_sha256, status "
                            + "FROM telemetry_outbox ORDER BY id")) {
                assertTrue(row.next());
                assertEquals("legacy-1", row.getString("message_id"));
                assertFalse(row.getString("product_identification") != null,
                        "V2 historical route identity must remain NULL until controlled backfill");
                assertArrayEquals(canonical.canonicalBytes(), row.getBytes("envelope"));
                assertEquals(canonical.contentSha256(), row.getString("content_sha256"));
                assertEquals("PENDING", row.getString("status"));
                assertTrue(row.next());
                assertEquals("legacy-2", row.getString("message_id"));
                assertFalse(row.getString("product_identification") != null,
                        "V2 historical route identity must remain NULL until controlled backfill");
                assertArrayEquals(inFlightCanonical.canonicalBytes(), row.getBytes("envelope"));
                assertEquals(inFlightCanonical.contentSha256(), row.getString("content_sha256"));
                assertEquals("IN_FLIGHT", row.getString("status"));
                assertFalse(row.next());
            }
        }

        SqliteTelemetryOutbox outbox = new SqliteTelemetryOutbox(db, codec, 8);
        try {
            ClaimBatchResult claim = outbox.claimBatch(10, Duration.ofMinutes(5));
            assertInstanceOf(ClaimBatchResult.Empty.class, claim,
                    "historical NULL product identities must not be claimed");
        } finally {
            outbox.shutdown();
        }
    }

    @Test
    void v3WritePersistsExactRouteIdentityAndCanonicalHashAcrossRestart(@TempDir Path directory) throws Exception {
        Path db = directory.resolve("outbox.db");
        SqliteOutboxMigration.migrate(db);
        TelemetryEnvelope envelope = envelope("route-1", "220.5");
        EnvelopeCanonicalCodec codec = new EnvelopeCanonicalCodec();
        EnvelopeCanonicalCodec.CanonicalEnvelope canonical = codec.canonicalize(envelope);

        SqliteTelemetryOutbox first = new SqliteTelemetryOutbox(db, codec, 8);
        try {
            AppendBatchResult stored = first.appendBatch(batch(PRODUCT, envelope), ENQUEUE_TIMEOUT);
            assertEquals(List.of("route-1"), stored.storedMessageIds());
        } finally {
            first.shutdown();
        }

        assertRowMatches(db, PRODUCT, canonical);

        SqliteTelemetryOutbox reopened = new SqliteTelemetryOutbox(db, codec, 8);
        try {
            AppendBatchResult duplicate = reopened.appendBatch(batch(PRODUCT, envelope), ENQUEUE_TIMEOUT);
            assertEquals(List.of("route-1"), duplicate.duplicateMessageIds());
            assertTrue(duplicate.storedMessageIds().isEmpty());
        } finally {
            reopened.shutdown();
        }
        assertRowMatches(db, PRODUCT, canonical);
    }

    @Test
    void sameMessageIdSameBytesDifferentProductRollsBackWholeBatch(@TempDir Path directory) throws Exception {
        Path db = directory.resolve("outbox.db");
        SqliteOutboxMigration.migrate(db);
        EnvelopeCanonicalCodec codec = new EnvelopeCanonicalCodec();
        TelemetryEnvelope existing = envelope("collision-1", "220.5");
        TelemetryEnvelope newMessage = envelope("collision-2", "221.0");

        SqliteTelemetryOutbox outbox = new SqliteTelemetryOutbox(db, codec, 8);
        try {
            outbox.appendBatch(batch("product-a", existing), ENQUEUE_TIMEOUT);
            AppendBatchResult collision = outbox.appendBatch(
                    batch("product-b", newMessage, existing), ENQUEUE_TIMEOUT);

            assertInstanceOf(AppendBatchResult.Collision.class, collision);
            assertEquals(List.of("collision-1"), collision.collisionMessageIds());
            assertTrue(collision.storedMessageIds().isEmpty());
            assertTrue(collision.duplicateMessageIds().isEmpty());
        } finally {
            outbox.shutdown();
        }

        try (Connection connection = open(db);
             Statement statement = connection.createStatement();
             ResultSet rows = statement.executeQuery(
                     "SELECT message_id, product_identification FROM telemetry_outbox ORDER BY id")) {
            assertTrue(rows.next());
            assertEquals("collision-1", rows.getString("message_id"));
            assertEquals("product-a", rows.getString("product_identification"));
            assertFalse(rows.next(), "collision must roll back the whole batch");
        }
    }

    private static void assertRowMatches(Path db, String expectedProduct,
                                         EnvelopeCanonicalCodec.CanonicalEnvelope expectedCanonical)
            throws Exception {
        try (Connection connection = open(db);
             Statement statement = connection.createStatement();
             ResultSet row = statement.executeQuery(
                     "SELECT product_identification, envelope, content_sha256, status "
                             + "FROM telemetry_outbox WHERE message_id = 'route-1'")) {
            assertTrue(row.next());
            assertEquals(expectedProduct, row.getString("product_identification"));
            assertArrayEquals(expectedCanonical.canonicalBytes(), row.getBytes("envelope"));
            assertEquals(expectedCanonical.contentSha256(), row.getString("content_sha256"));
            assertEquals("PENDING", row.getString("status"));
        }
    }

    private static void createV2Database(Path db, TelemetryEnvelope pendingEnvelope,
                                          EnvelopeCanonicalCodec.CanonicalEnvelope pendingCanonical,
                                          TelemetryEnvelope inFlightEnvelope,
                                          EnvelopeCanonicalCodec.CanonicalEnvelope inFlightCanonical)
            throws Exception {
        try (Connection connection = open(db);
             Statement statement = connection.createStatement()) {
            statement.execute("CREATE TABLE telemetry_outbox ("
                    + "id INTEGER PRIMARY KEY AUTOINCREMENT,"
                    + "message_id TEXT NOT NULL UNIQUE,"
                    + "request_id TEXT NOT NULL,"
                    + "tenant_id TEXT NOT NULL,"
                    + "site_code TEXT NOT NULL,"
                    + "device_identification TEXT NOT NULL,"
                    + "property_code TEXT NOT NULL,"
                    + "sequence_no INTEGER NOT NULL CHECK (sequence_no >= 0),"
                    + "collected_at_ms INTEGER NOT NULL,"
                    + "data_priority TEXT NOT NULL,"
                    + "priority_rank INTEGER NOT NULL CHECK (priority_rank BETWEEN 1 AND 5),"
                    + "envelope BLOB NOT NULL,"
                    + "content_sha256 TEXT NOT NULL,"
                    + "envelope_size INTEGER NOT NULL CHECK (envelope_size > 0),"
                    + "status TEXT NOT NULL DEFAULT 'PENDING'"
                    + " CHECK (status IN ('PENDING','IN_FLIGHT','ACKED','DEAD_LETTER')),"
                    + "delivery_class TEXT NOT NULL DEFAULT 'REALTIME'"
                    + " CHECK (delivery_class IN ('REALTIME','BACKFILL')),"
                    + "attempts INTEGER NOT NULL DEFAULT 0,"
                    + "unknown_ack_count INTEGER NOT NULL DEFAULT 0,"
                    + "next_retry_at_ms INTEGER,"
                    + "in_flight_at_ms INTEGER,"
                    + "ack_deadline_at_ms INTEGER,"
                    + "acked_at_ms INTEGER,"
                    + "last_error_code TEXT,"
                    + "last_error_detail TEXT,"
                    + "created_at_ms INTEGER NOT NULL,"
                    + "updated_at_ms INTEGER NOT NULL,"
                    + "config_version INTEGER NOT NULL CHECK (config_version >= 0)"
                    + ") STRICT");
            insertV2Row(connection, pendingEnvelope, pendingCanonical, 42L, "PENDING", "BACKFILL",
                    7, 4, 1_700_000_000_002L, null, null, null,
                    "E_NON_DEFAULT", "legacy detail survives migration",
                    1_700_000_000_001L, 1_700_000_000_006L);
            insertV2Row(connection, inFlightEnvelope, inFlightCanonical, 43L, "IN_FLIGHT", "REALTIME",
                    8, 2, null, 1_700_000_000_012L, 1_700_000_000_013L, null,
                    "E_IN_FLIGHT", "lease row detail survives migration",
                    1_700_000_000_011L, 1_700_000_000_014L);
            statement.execute("PRAGMA user_version = 2");
        }
    }

    private static void insertV2Row(Connection connection, TelemetryEnvelope envelope,
                                     EnvelopeCanonicalCodec.CanonicalEnvelope canonical, long id,
                                     String status, String deliveryClass, int attempts,
                                     int unknownAckCount, Long nextRetryAtMs, Long inFlightAtMs,
                                     Long ackDeadlineAtMs, Long ackedAtMs, String lastErrorCode,
                                     String lastErrorDetail, long collectedAtMs, long createdAtMs)
            throws Exception {
        try (var insert = connection.prepareStatement(
                "INSERT INTO telemetry_outbox "
                        + "(id, message_id, request_id, tenant_id, site_code, device_identification, "
                        + "property_code, sequence_no, collected_at_ms, data_priority, priority_rank, "
                        + "envelope, content_sha256, envelope_size, status, delivery_class, attempts, "
                        + "unknown_ack_count, next_retry_at_ms, in_flight_at_ms, ack_deadline_at_ms, "
                        + "acked_at_ms, last_error_code, last_error_detail, created_at_ms, updated_at_ms, "
                        + "config_version) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)")) {
            insert.setLong(1, id);
            insert.setString(2, envelope.messageId());
            insert.setString(3, envelope.requestId());
            insert.setString(4, envelope.tenantId());
            insert.setString(5, envelope.siteCode());
            insert.setString(6, envelope.deviceIdentification());
            insert.setString(7, envelope.propertyCode());
            insert.setLong(8, envelope.sequence());
            insert.setLong(9, collectedAtMs);
            insert.setString(10, envelope.dataPriority().name());
            insert.setInt(11, envelope.dataPriority().rank());
            insert.setBytes(12, canonical.canonicalBytes());
            insert.setString(13, canonical.contentSha256());
            insert.setInt(14, canonical.canonicalBytes().length);
            insert.setString(15, status);
            insert.setString(16, deliveryClass);
            insert.setInt(17, attempts);
            insert.setInt(18, unknownAckCount);
            if (nextRetryAtMs == null) {
                insert.setNull(19, java.sql.Types.INTEGER);
            } else {
                insert.setLong(19, nextRetryAtMs);
            }
            if (inFlightAtMs == null) {
                insert.setNull(20, java.sql.Types.INTEGER);
            } else {
                insert.setLong(20, inFlightAtMs);
            }
            if (ackDeadlineAtMs == null) {
                insert.setNull(21, java.sql.Types.INTEGER);
            } else {
                insert.setLong(21, ackDeadlineAtMs);
            }
            if (ackedAtMs == null) {
                insert.setNull(22, java.sql.Types.INTEGER);
            } else {
                insert.setLong(22, ackedAtMs);
            }
            insert.setString(23, lastErrorCode);
            insert.setString(24, lastErrorDetail);
            insert.setLong(25, createdAtMs);
            insert.setLong(26, createdAtMs + 5L);
            insert.setLong(27, envelope.configVersion());
            insert.executeUpdate();
        }
    }

    private static List<Map<String, Object>> readV2Rows(Path db) throws Exception {
        List<Map<String, Object>> rows = new java.util.ArrayList<>();
        try (Connection connection = open(db);
             Statement statement = connection.createStatement();
             ResultSet row = statement.executeQuery(
                     "SELECT id, message_id, request_id, tenant_id, site_code, device_identification, "
                             + "property_code, sequence_no, collected_at_ms, data_priority, priority_rank, "
                             + "envelope, content_sha256, envelope_size, status, delivery_class, attempts, "
                             + "unknown_ack_count, next_retry_at_ms, in_flight_at_ms, ack_deadline_at_ms, "
                             + "acked_at_ms, last_error_code, last_error_detail, created_at_ms, updated_at_ms, "
                             + "config_version FROM telemetry_outbox ORDER BY id")) {
            while (row.next()) {
                Map<String, Object> values = new LinkedHashMap<>();
                values.put("id", longValue(row, "id"));
                values.put("message_id", row.getString("message_id"));
                values.put("request_id", row.getString("request_id"));
                values.put("tenant_id", row.getString("tenant_id"));
                values.put("site_code", row.getString("site_code"));
                values.put("device_identification", row.getString("device_identification"));
                values.put("property_code", row.getString("property_code"));
                values.put("sequence_no", longValue(row, "sequence_no"));
                values.put("collected_at_ms", longValue(row, "collected_at_ms"));
                values.put("data_priority", row.getString("data_priority"));
                values.put("priority_rank", longValue(row, "priority_rank"));
                values.put("envelope", Base64.getEncoder().encodeToString(row.getBytes("envelope")));
                values.put("content_sha256", row.getString("content_sha256"));
                values.put("envelope_size", longValue(row, "envelope_size"));
                values.put("status", row.getString("status"));
                values.put("delivery_class", row.getString("delivery_class"));
                values.put("attempts", longValue(row, "attempts"));
                values.put("unknown_ack_count", longValue(row, "unknown_ack_count"));
                values.put("next_retry_at_ms", longValue(row, "next_retry_at_ms"));
                values.put("in_flight_at_ms", longValue(row, "in_flight_at_ms"));
                values.put("ack_deadline_at_ms", longValue(row, "ack_deadline_at_ms"));
                values.put("acked_at_ms", longValue(row, "acked_at_ms"));
                values.put("last_error_code", row.getString("last_error_code"));
                values.put("last_error_detail", row.getString("last_error_detail"));
                values.put("created_at_ms", longValue(row, "created_at_ms"));
                values.put("updated_at_ms", longValue(row, "updated_at_ms"));
                values.put("config_version", longValue(row, "config_version"));
                rows.add(values);
            }
        }
        assertEquals(2, rows.size(), "fixture must contain two legacy rows");
        return rows;
    }

    private static Object longValue(ResultSet row, String column) throws Exception {
        long value = row.getLong(column);
        return row.wasNull() ? null : value;
    }

    private static Connection open(Path db) throws Exception {
        return DriverManager.getConnection("jdbc:sqlite:" + db.toAbsolutePath());
    }

    private static TelemetryOutboxBatch batch(String product, TelemetryEnvelope... envelopes) {
        return new TelemetryOutboxBatch(product, List.of(envelopes));
    }

    private static TelemetryEnvelope envelope(String messageId, String value) {
        return new TelemetryEnvelope(
                TelemetryEnvelope.SCHEMA_VERSION, TelemetryEnvelope.CANONICALIZATION_VERSION,
                messageId, "request-" + messageId, "tenant-1", "site-1", "device-1",
                "voltage-a", value, TelemetryEnvelope.VALUE_ENCODING_DECIMAL_STRING,
                TelemetryQuality.GOOD, DataPriority.NORMAL_TELEMETRY,
                "2026-08-20T00:00:00Z", "2026-08-20T00:00:00Z", 1,
                "modbus-rtu", 1);
    }
}
