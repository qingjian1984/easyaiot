package com.basiclab.iot.sink.outbox.sqlite;

import com.basiclab.iot.sink.telemetry.outbox.backfill.RouteBackfillKey;
import com.basiclab.iot.sink.telemetry.outbox.backfill.RouteInventoryArtifact;
import com.basiclab.iot.sink.telemetry.outbox.backfill.RouteInventoryEntry;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;

class SqliteOutboxRouteInventoryExporterTest {

    private final SqliteOutboxRouteInventoryExporter exporter = new SqliteOutboxRouteInventoryExporter();

    @Test
    void emptyInventoryReturnsEmptyArtifact(@TempDir Path directory) throws Exception {
        Path db = migratedDb(directory);

        RouteInventoryArtifact artifact = exporter.exportPage(db, "workload-empty", null, 1);

        assertEquals("workload-empty", artifact.page().workloadId());
        assertTrue(artifact.page().entries().isEmpty());
        assertTrue(artifact.page().nextCursor() == null);
        assertFalse(new String(artifact.canonicalBytes(), StandardCharsets.UTF_8).contains("envelope"));
    }

    @Test
    void allStatusesAreGroupedAndNonNullProductsAreExcluded(@TempDir Path directory) throws Exception {
        Path db = migratedDb(directory);
        insertRow(db, "pending", "tenant", "site", 1, "device-a", "PENDING", null);
        insertRow(db, "in-flight", "tenant", "site", 1, "device-a", "IN_FLIGHT", null);
        insertRow(db, "acked", "tenant", "site", 1, "device-a", "ACKED", null);
        insertRow(db, "dead", "tenant", "site", 1, "device-a", "DEAD_LETTER", null);
        insertRow(db, "excluded", "tenant", "site", 1, "device-a", "PENDING", "product-a");
        insertRow(db, "other-key", "tenant", "site", 1, "device-b", "PENDING", null);

        RouteInventoryPageView result = page(exporter.exportPage(db, "workload-1", null, 500));

        assertEquals(List.of("device-a", "device-b"), result.devices());
        assertEquals(List.of(4L, 1L), result.counts());
    }

    @Test
    void paginationAndAfterExclusiveCursorHaveNoGapsOrDuplicates(@TempDir Path directory) throws Exception {
        Path db = migratedDb(directory);
        for (String device : List.of("device-a", "device-b", "device-c")) {
            insertRow(db, "id-" + device, "tenant", "site", 1, device, "PENDING", null);
        }

        RouteInventoryArtifact first = exporter.exportPage(db, "workload-1", null, 1);
        RouteInventoryArtifact second = exporter.exportPage(db, "workload-1", first.page().nextCursor(), 1);
        RouteInventoryArtifact third = exporter.exportPage(db, "workload-1", second.page().nextCursor(), 1);
        RouteBackfillKey lastKey = third.page().entries().get(0).key();
        RouteInventoryArtifact end = exporter.exportPage(db, "workload-1", lastKey, 1);

        assertEquals(List.of("device-a"), devices(first));
        assertEquals(List.of("device-b"), devices(second));
        assertEquals(List.of("device-c"), devices(third));
        assertTrue(end.page().entries().isEmpty());
        assertNotNull(first.page().nextCursor());
        assertNotNull(second.page().nextCursor());
        assertTrue(third.page().nextCursor() == null);

        RouteInventoryArtifact all = exporter.exportPage(db, "workload-1", null, 500);
        assertEquals(List.of("device-a", "device-b", "device-c"), devices(all));
    }

    @Test
    void limit500Paginates501DistinctFourPartKeysWithoutGapOrDuplicate(@TempDir Path directory)
            throws Exception {
        Path db = migratedDb(directory);
        List<RouteBackfillKey> expected = new ArrayList<>();
        for (int i = 0; i < 501; i++) {
            expected.add(new RouteBackfillKey(
                    "tenant-" + (i % 5),
                    "site-" + ((i / 5) % 7),
                    i / 35,
                    String.format("device-%03d", i)));
        }
        expected.sort(Comparator.comparing(RouteBackfillKey::tenantId)
                .thenComparing(RouteBackfillKey::siteCode)
                .thenComparingLong(RouteBackfillKey::configVersion)
                .thenComparing(RouteBackfillKey::deviceIdentification));
        insertRows(db, expected);

        RouteInventoryArtifact first = exporter.exportPage(db, "workload-1", null, 500);
        RouteInventoryArtifact second = exporter.exportPage(
                db, "workload-1", first.page().nextCursor(), 500);
        List<RouteBackfillKey> actual = new ArrayList<>(keys(first));
        actual.addAll(keys(second));

        assertEquals(500, first.page().entries().size());
        assertNotNull(first.page().nextCursor());
        assertEquals(expected.get(499), first.page().nextCursor());
        assertEquals(1, second.page().entries().size());
        assertEquals(expected.get(500), second.page().entries().get(0).key());
        assertTrue(second.page().nextCursor() == null);
        assertEquals(expected, actual);
        assertEquals(expected.size(), new HashSet<>(actual).size());
    }

    @Test
    void nfdAndNfcDeviceValuesRemainDistinctAndUnnormalized(@TempDir Path directory) throws Exception {
        Path db = migratedDb(directory);
        String nfd = "cafe\u0301-meter";
        String nfc = "caf\u00e9-meter";
        insertRow(db, "id-nfd", "tenant", "site", 1, nfd, "PENDING", null);
        insertRow(db, "id-nfc", "tenant", "site", 1, nfc, "PENDING", null);

        RouteInventoryArtifact artifact = exporter.exportPage(db, "workload-1", null, 500);
        assertEquals(List.of(nfd, nfc), devices(artifact));
        String canonical = new String(artifact.canonicalBytes(), StandardCharsets.UTF_8);
        assertTrue(canonical.contains(nfd));
        assertTrue(canonical.contains(nfc));
    }

    @Test
    void supplementaryAndBmpValuesFollowJavaUtf16OrderAcrossPages(@TempDir Path directory) throws Exception {
        Path db = migratedDb(directory);
        String bmp = "device-\uE000";
        String supplementary = "device-\uD800\uDC00";
        insertRow(db, "id-bmp", "tenant", "site", 1, bmp, "PENDING", null);
        insertRow(db, "id-supplementary", "tenant", "site", 1, supplementary, "PENDING", null);

        RouteInventoryArtifact first = exporter.exportPage(db, "workload-1", null, 1);
        RouteInventoryArtifact second = exporter.exportPage(
                db, "workload-1", first.page().nextCursor(), 1);

        assertEquals(List.of(supplementary), devices(first));
        assertEquals(List.of(bmp), devices(second));
        assertEquals(List.of(supplementary, bmp), devices(exporter.exportPage(db, "workload-1", null, 500)));
    }

    @Test
    void invalidInputsAndSchemaFailuresUseStableCodes(@TempDir Path directory) throws Exception {
        Path db = migratedDb(directory);
        assertCode(SqliteOutboxRouteInventoryExporter.INPUT_INVALID,
                () -> exporter.exportPage(db, " ", null, 1));
        assertCode(SqliteOutboxRouteInventoryExporter.INPUT_INVALID,
                () -> exporter.exportPage(db, "workload", null, 0));
        assertCode(SqliteOutboxRouteInventoryExporter.INPUT_INVALID,
                () -> exporter.exportPage(null, "workload", null, 1));

        Path missing = directory.resolve("not-created.db");
        assertCode(SqliteOutboxRouteInventoryExporter.DB_NOT_FOUND,
                () -> exporter.exportPage(missing, "workload", null, 1));
        assertFalse(Files.exists(missing));

        Path wrongVersion = directory.resolve("wrong-version.db");
        createBareDatabase(wrongVersion, 2, false);
        assertCode(SqliteOutboxRouteInventoryExporter.SCHEMA_UNSUPPORTED,
                () -> exporter.exportPage(wrongVersion, "workload", null, 1));

        Path missingColumn = directory.resolve("missing-column.db");
        createBareDatabase(missingColumn, 3, true);
        assertCode(SqliteOutboxRouteInventoryExporter.SCHEMA_UNSUPPORTED,
                () -> exporter.exportPage(missingColumn, "workload", null, 1));
    }

    @Test
    void exportIsReadOnlyAndPreservesAllObservedColumnsAndVersion(@TempDir Path directory) throws Exception {
        Path db = migratedDb(directory);
        insertRow(db, "id-a", "tenant", "site", 1, "device-a", "PENDING", null);
        insertRow(db, "id-b", "tenant", "site", 1, "device-b", "ACKED", "product-a");
        Snapshot before = snapshot(db);

        exporter.exportPage(db, "workload-1", null, 1);
        exporter.exportPage(db, "workload-1", new RouteBackfillKey("tenant", "site", 1, "device-a"), 500);

        Snapshot after = snapshot(db);
        assertEquals(3, before.version());
        assertEquals(before.version(), after.version());
        assertEquals(before.rows(), after.rows());
        assertEquals(before, after);
    }

    @Test
    void exporterReadOnlyConnectionRejectsWriteOperations(@TempDir Path directory) throws Exception {
        Path db = migratedDb(directory);
        insertRow(db, "id-read-only", "tenant", "site", 1, "device-a", "PENDING", null);
        Snapshot before = snapshot(db);

        try (Connection connection = openExporterReadOnly(db);
             Statement statement = connection.createStatement()) {
            assertTrue(connection.isReadOnly());
            assertThrows(java.sql.SQLException.class,
                    () -> statement.executeUpdate("UPDATE telemetry_outbox SET status='ACKED'"));
        }

        assertEquals(before, snapshot(db));
    }

    private static Path migratedDb(Path directory) throws Exception {
        Path db = directory.resolve("outbox.db");
        SqliteOutboxMigration.migrate(db);
        return db;
    }

    private static void insertRow(Path db, String messageId, String tenant, String site,
                                  long configVersion, String device, String status, String product)
            throws Exception {
        try (Connection connection = open(db);
             PreparedStatement insert = connection.prepareStatement(
                     "INSERT INTO telemetry_outbox "
                             + "(message_id, request_id, tenant_id, site_code, device_identification, "
                             + "property_code, sequence_no, collected_at_ms, data_priority, priority_rank, "
                             + "envelope, content_sha256, envelope_size, status, delivery_class, "
                             + "created_at_ms, updated_at_ms, config_version, product_identification) "
                             + "VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)")) {
            insert.setString(1, messageId);
            insert.setString(2, "request-" + messageId);
            insert.setString(3, tenant);
            insert.setString(4, site);
            insert.setString(5, device);
            insert.setString(6, "voltage-a");
            insert.setLong(7, 1);
            insert.setLong(8, 1_700_000_000_000L);
            insert.setString(9, "NORMAL_TELEMETRY");
            insert.setInt(10, 3);
            insert.setBytes(11, new byte[]{1, 2, 3});
            insert.setString(12, "sha-" + messageId);
            insert.setInt(13, 3);
            insert.setString(14, status);
            insert.setString(15, "REALTIME");
            insert.setLong(16, 1_700_000_000_000L);
            insert.setLong(17, 1_700_000_000_001L);
            insert.setLong(18, configVersion);
            if (product == null) {
                insert.setNull(19, java.sql.Types.VARCHAR);
            } else {
                insert.setString(19, product);
            }
            insert.executeUpdate();
        }
    }

    private static void insertRows(Path db, List<RouteBackfillKey> keys) throws Exception {
        try (Connection connection = open(db);
             PreparedStatement insert = connection.prepareStatement(
                     "INSERT INTO telemetry_outbox "
                             + "(message_id, request_id, tenant_id, site_code, device_identification, "
                             + "property_code, sequence_no, collected_at_ms, data_priority, priority_rank, "
                             + "envelope, content_sha256, envelope_size, status, delivery_class, "
                             + "created_at_ms, updated_at_ms, config_version, product_identification) "
                             + "VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)")) {
            for (int i = 0; i < keys.size(); i++) {
                RouteBackfillKey key = keys.get(i);
                long timestamp = 1_700_000_000_000L + i;
                insert.setString(1, "batch-message-" + i);
                insert.setString(2, "batch-request-" + i);
                insert.setString(3, key.tenantId());
                insert.setString(4, key.siteCode());
                insert.setString(5, key.deviceIdentification());
                insert.setString(6, "voltage-a");
                insert.setLong(7, 1);
                insert.setLong(8, timestamp);
                insert.setString(9, "NORMAL_TELEMETRY");
                insert.setInt(10, 3);
                insert.setBytes(11, new byte[]{1, 2, (byte) i});
                insert.setString(12, "batch-sha-" + i);
                insert.setInt(13, 3);
                insert.setString(14, "PENDING");
                insert.setString(15, "REALTIME");
                insert.setLong(16, timestamp);
                insert.setLong(17, timestamp + 1);
                insert.setLong(18, key.configVersion());
                insert.setNull(19, java.sql.Types.VARCHAR);
                insert.addBatch();
            }
            insert.executeBatch();
        }
    }

    private static void createBareDatabase(Path db, int version, boolean hasTable) throws Exception {
        try (Connection connection = open(db); Statement statement = connection.createStatement()) {
            if (hasTable) {
                statement.execute("CREATE TABLE telemetry_outbox (id INTEGER PRIMARY KEY, message_id TEXT)");
            }
            statement.execute("PRAGMA user_version = " + version);
        }
    }

    private static Snapshot snapshot(Path db) throws Exception {
        List<List<String>> rows = new ArrayList<>();
        int version;
        try (Connection connection = open(db); Statement statement = connection.createStatement()) {
            try (ResultSet result = statement.executeQuery("PRAGMA user_version")) {
                result.next();
                version = result.getInt(1);
            }
            try (ResultSet result = statement.executeQuery(
                    "SELECT * FROM telemetry_outbox ORDER BY id")) {
                ResultSetMetaData metadata = result.getMetaData();
                while (result.next()) {
                    rows.add(snapshotRow(result, metadata));
                }
            }
        }
        return new Snapshot(version, rows);
    }

    private static List<String> snapshotRow(ResultSet result, ResultSetMetaData metadata)
            throws Exception {
        List<String> columns = new ArrayList<>();
        for (int i = 1; i <= metadata.getColumnCount(); i++) {
            Object value = result.getObject(i);
            String encodedValue;
            if (value == null || result.wasNull()) {
                encodedValue = "<NULL>";
            } else if (value instanceof byte[] bytes) {
                encodedValue = "<BLOB>" + Base64.getEncoder().encodeToString(bytes);
            } else {
                encodedValue = "<" + value.getClass().getName() + ">" + value;
            }
            columns.add(metadata.getColumnName(i) + "|"
                    + metadata.getColumnType(i) + "|"
                    + metadata.getColumnTypeName(i) + "|" + encodedValue);
        }
        return columns;
    }

    private static Connection open(Path db) throws Exception {
        return DriverManager.getConnection("jdbc:sqlite:" + db.toAbsolutePath());
    }

    private static Connection openExporterReadOnly(Path db) throws Exception {
        Method method = SqliteOutboxRouteInventoryExporter.class
                .getDeclaredMethod("openReadOnly", Path.class);
        method.setAccessible(true);
        return (Connection) method.invoke(null, db);
    }

    private static List<String> devices(RouteInventoryArtifact artifact) {
        return artifact.page().entries().stream().map(entry -> entry.key().deviceIdentification()).toList();
    }

    private static List<RouteBackfillKey> keys(RouteInventoryArtifact artifact) {
        return artifact.page().entries().stream().map(RouteInventoryEntry::key).toList();
    }

    private static RouteInventoryPageView page(RouteInventoryArtifact artifact) {
        List<RouteInventoryEntry> entries = artifact.page().entries();
        return new RouteInventoryPageView(
                entries.stream().map(entry -> entry.key().deviceIdentification()).toList(),
                entries.stream().map(RouteInventoryEntry::rowCount).toList());
    }

    private static void assertCode(String expected, ThrowingCall call) {
        SqliteOutboxRouteInventoryExporter.RouteInventoryException exception =
                assertThrows(SqliteOutboxRouteInventoryExporter.RouteInventoryException.class, call::run);
        assertEquals(expected, exception.code());
    }

    private record RouteInventoryPageView(List<String> devices, List<Long> counts) {
    }

    private record Snapshot(int version, List<List<String>> rows) {
    }

    @FunctionalInterface
    private interface ThrowingCall {
        void run();
    }
}
