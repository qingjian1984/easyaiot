package com.basiclab.iot.sink.outbox.sqlite;

import com.basiclab.iot.sink.telemetry.envelope.EnvelopeJcsCanonicalizer;
import com.basiclab.iot.sink.telemetry.envelope.EnvelopeCanonicalCodec;
import com.basiclab.iot.sink.telemetry.outbox.backfill.RouteBackfillApplyRequest;
import com.basiclab.iot.sink.telemetry.outbox.backfill.RouteBackfillApplyResult;
import com.basiclab.iot.sink.telemetry.outbox.backfill.RouteBackfillAuthorization;
import com.basiclab.iot.sink.telemetry.outbox.backfill.RouteBackfillAuthorizationArtifact;
import com.basiclab.iot.sink.telemetry.outbox.backfill.RouteBackfillKey;
import com.basiclab.iot.sink.telemetry.outbox.backfill.RouteBackfillManifest;
import com.basiclab.iot.sink.telemetry.outbox.backfill.RouteBackfillManifestArtifact;
import com.basiclab.iot.sink.telemetry.outbox.backfill.RouteBackfillManifestEntry;
import com.basiclab.iot.sink.telemetry.outbox.backfill.RouteBackfillVerificationResult;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.Signature;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.Statement;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.text.Normalizer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SqliteOutboxRouteBackfillApplierTest {

    private static final String INVENTORY_HASH = "a".repeat(64);
    private static final String KEY_ID = "collector-test-key";
    private static final long NOW = 1_700_000_000L;
    private static final Clock CLOCK = Clock.fixed(Instant.ofEpochSecond(NOW), ZoneOffset.UTC);
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final EnvelopeJcsCanonicalizer JCS = new EnvelopeJcsCanonicalizer();

    @Test
    void appliesOnePageAndWritesOnlyProductAndCheckpoints(@TempDir Path directory) throws Exception {
        Path db = migratedDb(directory);
        RouteBackfillKey key = key("device-a");
        insertRow(db, key, "row-a-1", null);
        insertRow(db, key, "row-a-2", null);
        List<List<String>> before = snapshotRows(db);

        KeyPair pair = keyPair();
        RouteBackfillManifestArtifact manifest = manifest(
                List.of(entry(key, 2, "product-a")), key);
        RouteBackfillApplyResult.Applied result = assertInstanceOf(
                RouteBackfillApplyResult.Applied.class,
                applier(pair).apply(db, "workload-a",
                        request(manifest, "123e4567-e89b-12d3-a456-426614174000", pair)));

        assertEquals(2, result.updatedRowCount());
        assertEquals(key, result.nextInventoryCursor());
        assertEquals(List.of("product-a", "product-a"), products(db));
        assertEquals(before.size(), snapshotRows(db).size());
        assertOnlyProductChanged(before, snapshotRows(db));
        assertEquals(2, checkpointCount(db));
        assertTrue(checkpointValue(db, "route_backfill.v1.operation.123e4567-e89b-12d3-a456-426614174000")
                .contains("\"status\":\"APPLIED\""));
    }

    @Test
    void appliedCheckpointIsIdempotentAndOperationCollisionIsRejected(@TempDir Path directory)
            throws Exception {
        Path db = migratedDb(directory);
        RouteBackfillKey key = key("device-a");
        insertRow(db, key, "row-a", null);
        KeyPair pair = keyPair();
        RouteBackfillManifestArtifact first = manifest(
                List.of(entry(key, 1, "product-a")), key);
        RouteBackfillApplyRequest firstRequest = request(
                first, "123e4567-e89b-12d3-a456-426614174000", pair);
        SqliteOutboxRouteBackfillApplier applier = applier(pair);

        RouteBackfillApplyResult.Applied applied = assertInstanceOf(
                RouteBackfillApplyResult.Applied.class,
                applier.apply(db, "workload-a", firstRequest));
        List<List<String>> afterApply = snapshotRows(db);
        RouteBackfillApplyResult.AlreadyApplied replay = assertInstanceOf(
                RouteBackfillApplyResult.AlreadyApplied.class,
                applier.apply(db, "workload-a", firstRequest));
        assertEquals(applied.updatedRowCount(), replay.updatedRowCount());
        assertEquals(afterApply, snapshotRows(db));

        RouteBackfillManifestArtifact differentManifest = manifest(
                List.of(entry(key, 0 + 1, "product-b")), key);
        RouteBackfillApplyResult.Rejected collision = assertInstanceOf(
                RouteBackfillApplyResult.Rejected.class,
                applier.apply(db, "workload-a", request(
                        differentManifest, "123e4567-e89b-12d3-a456-426614174000", pair)));
        assertEquals(SqliteOutboxRouteBackfillApplier.ROUTE_BACKFILL_OPERATION_COLLISION,
                collision.code());
        assertEquals(afterApply, snapshotRows(db));
    }

    @Test
    void sameOperationCollisionWinsWhenBothCheckpointKeysExist(@TempDir Path directory)
            throws Exception {
        Path db = migratedDb(directory);
        RouteBackfillKey key = key("device-a");
        insertRow(db, key, "row-a", null);
        KeyPair pair = keyPair();
        String operationId = "123e4567-e89b-12d3-a456-426614174000";
        RouteBackfillManifestArtifact first = manifest(List.of(entry(key, 1, "product-a")), key);
        RouteBackfillApplyRequest firstRequest = request(first, operationId, pair);
        SqliteOutboxRouteBackfillApplier applier = applier(pair);
        assertInstanceOf(RouteBackfillApplyResult.Applied.class,
                applier.apply(db, "workload-a", firstRequest));

        RouteBackfillManifestArtifact second = manifest(List.of(entry(key, 1, "product-b")), key);
        String operationCheckpoint = checkpointValue(db,
                "route_backfill.v1.operation." + operationId);
        String secondCheckpoint = operationCheckpoint.replace(
                first.contentSha256(), second.contentSha256());
        putCheckpoint(db, "route_backfill.v1.manifest." + second.contentSha256(), secondCheckpoint);

        RouteBackfillApplyResult.Rejected result = assertInstanceOf(
                RouteBackfillApplyResult.Rejected.class,
                applier.apply(db, "workload-a", request(second, operationId, pair)));
        assertEquals(SqliteOutboxRouteBackfillApplier.ROUTE_BACKFILL_OPERATION_COLLISION,
                result.code());
        assertEquals(secondCheckpoint, checkpointValue(db,
                "route_backfill.v1.manifest." + second.contentSha256()));
        assertEquals(List.of("product-a"), products(db));
    }

    @Test
    void sameManifestWithDifferentOperationIsCheckpointConflict(@TempDir Path directory)
            throws Exception {
        Path db = migratedDb(directory);
        RouteBackfillKey key = key("device-a");
        insertRow(db, key, "row-a", null);
        KeyPair pair = keyPair();
        RouteBackfillManifestArtifact manifest = manifest(
                List.of(entry(key, 1, "product-a")), key);
        SqliteOutboxRouteBackfillApplier applier = applier(pair);
        assertInstanceOf(RouteBackfillApplyResult.Applied.class,
                applier.apply(db, "workload-a", request(
                        manifest, "123e4567-e89b-12d3-a456-426614174000", pair)));
        List<List<String>> before = snapshotRows(db);

        RouteBackfillApplyResult.Degraded result = assertInstanceOf(
                RouteBackfillApplyResult.Degraded.class,
                applier.apply(db, "workload-a", request(
                        manifest, "123e4567-e89b-12d3-a456-426614174001", pair)));
        assertEquals(SqliteOutboxRouteBackfillApplier.ROUTE_BACKFILL_CHECKPOINT_CONFLICT,
                result.code());
        assertEquals(before, snapshotRows(db));
    }

    @Test
    void canonicalExtraNonCanonicalAndTimestampDifferentPairsConflictWithoutOverwrite(
            @TempDir Path directory) throws Exception {
        String operationId = "123e4567-e89b-12d3-a456-426614174000";
        for (String variant : List.of("canonical", "extra", "noncanonical", "timestamp")) {
            Path caseDirectory = Files.createDirectory(directory.resolve(variant));
            Path db = migratedDb(caseDirectory);
            RouteBackfillKey key = key("device-a");
            insertRow(db, key, "row-a", null);
            KeyPair pair = keyPair();
            RouteBackfillManifestArtifact manifest = manifest(
                    List.of(entry(key, 1, "product-a")), key);
            SqliteOutboxRouteBackfillApplier applier = applier(pair);
            RouteBackfillApplyRequest applyRequest = request(manifest, operationId, pair);
            assertInstanceOf(RouteBackfillApplyResult.Applied.class,
                    applier.apply(db, "workload-a", applyRequest));

            String keyName = "route_backfill.v1.manifest." + manifest.contentSha256();
            String original = checkpointValue(db, keyName);
            String variantValue = checkpointVariant(original, variant);
            putCheckpoint(db, keyName, variantValue);

            RouteBackfillApplyResult.Degraded result = assertInstanceOf(
                    RouteBackfillApplyResult.Degraded.class,
                    applier.apply(db, "workload-a", applyRequest));
            assertEquals(SqliteOutboxRouteBackfillApplier.ROUTE_BACKFILL_CHECKPOINT_CONFLICT,
                    result.code());
            assertEquals(variantValue, checkpointValue(db, keyName));
            assertEquals(List.of("product-a"), products(db));
        }
    }

    @Test
    void localIdentityDegradedCanBeRepairedAndAdvanced(@TempDir Path directory) throws Exception {
        Path db = migratedDb(directory);
        RouteBackfillKey key = key("device-a");
        insertRow(db, key, "row-a", "other-product");
        KeyPair pair = keyPair();
        RouteBackfillManifestArtifact manifest = manifest(
                List.of(entry(key, 1, "product-a")), key);
        SqliteOutboxRouteBackfillApplier applier = applier(pair);
        RouteBackfillApplyRequest request = request(
                manifest, "123e4567-e89b-12d3-a456-426614174000", pair);

        RouteBackfillApplyResult.Degraded degraded = assertInstanceOf(
                RouteBackfillApplyResult.Degraded.class,
                applier.apply(db, "workload-a", request));
        assertEquals(SqliteOutboxRouteBackfillApplier.ROUTE_BACKFILL_LOCAL_IDENTITY_CONFLICT,
                degraded.code());
        assertEquals(List.of("other-product"), products(db));

        setProduct(db, key, null);
        RouteBackfillApplyResult.Applied repaired = assertInstanceOf(
                RouteBackfillApplyResult.Applied.class,
                applier.apply(db, "workload-a", request));
        assertEquals(1, repaired.updatedRowCount());
        assertEquals(List.of("product-a"), products(db));
    }

    @Test
    void rowCountMismatchWritesDegradedWithoutChangingProduct(@TempDir Path directory)
            throws Exception {
        Path db = migratedDb(directory);
        RouteBackfillKey key = key("device-a");
        insertRow(db, key, "row-a", null);
        KeyPair pair = keyPair();
        RouteBackfillManifestArtifact manifest = manifest(
                List.of(entry(key, 2, "product-a")), key);

        RouteBackfillApplyResult.Degraded result = assertInstanceOf(
                RouteBackfillApplyResult.Degraded.class,
                applier(pair).apply(db, "workload-a",
                        request(manifest, "123e4567-e89b-12d3-a456-426614174000", pair)));
        assertEquals(SqliteOutboxRouteBackfillApplier.ROUTE_BACKFILL_ROW_COUNT_MISMATCH,
                result.code());
        assertEquals(Arrays.asList((String) null), products(db));
        assertEquals(2, checkpointCount(db));
        assertTrue(checkpointValue(db, "route_backfill.v1.manifest." + manifest.contentSha256())
                .contains("\"status\":\"DEGRADED\""));
    }

    @Test
    void contradictoryDoubleCheckpointIsDegradedAndNotOverwritten(@TempDir Path directory)
            throws Exception {
        Path db = migratedDb(directory);
        RouteBackfillKey key = key("device-a");
        insertRow(db, key, "row-a", null);
        KeyPair pair = keyPair();
        RouteBackfillManifestArtifact manifest = manifest(
                List.of(entry(key, 1, "product-a")), key);
        String operationId = "123e4567-e89b-12d3-a456-426614174000";
        RouteBackfillApplyRequest request = request(manifest, operationId, pair);
        SqliteOutboxRouteBackfillApplier applier = applier(pair);
        assertInstanceOf(RouteBackfillApplyResult.Applied.class,
                applier.apply(db, "workload-a", request));

        String manifestKey = "route_backfill.v1.manifest." + manifest.contentSha256();
        String original = checkpointValue(db, manifestKey);
        String contradictory = original.replace(operationId,
                "123e4567-e89b-12d3-a456-426614174001");
        try (Connection connection = open(db);
             PreparedStatement update = connection.prepareStatement(
                     "UPDATE outbox_meta SET meta_value=? WHERE meta_key=?")) {
            update.setString(1, contradictory);
            update.setString(2, manifestKey);
            update.executeUpdate();
        }

        RouteBackfillApplyResult.Degraded result = assertInstanceOf(
                RouteBackfillApplyResult.Degraded.class,
                applier.apply(db, "workload-a", request));
        assertEquals(SqliteOutboxRouteBackfillApplier.ROUTE_BACKFILL_CHECKPOINT_CONFLICT,
                result.code());
        assertEquals(contradictory, checkpointValue(db, manifestKey),
                "conflicting evidence must not be overwritten");
    }

    @Test
    void rejectedVerificationDoesNotInspectOrCreateDatabase(@TempDir Path directory)
            throws Exception {
        Path missing = directory.resolve("missing.db");
        KeyPair pair = keyPair();
        RouteBackfillApplyResult.Rejected result = assertInstanceOf(
                RouteBackfillApplyResult.Rejected.class,
                applier(pair).apply(missing, "workload-a", null));
        assertEquals("ROUTE_BACKFILL_MANIFEST_INTEGRITY_FAILED", result.code());
        assertFalse(Files.exists(missing));
    }

    @Test
    void transactionFailureRollsBackAllProductsAndAppliedCheckpoints(@TempDir Path directory)
            throws Exception {
        Path db = migratedDb(directory);
        RouteBackfillKey firstKey = key("device-a");
        RouteBackfillKey secondKey = key("device-b");
        insertRow(db, firstKey, "row-a", null);
        insertRow(db, secondKey, "row-b", null);
        try (Connection connection = open(db); Statement statement = connection.createStatement()) {
            statement.execute("CREATE TRIGGER fail_product_b BEFORE UPDATE OF product_identification "
                    + "ON telemetry_outbox WHEN NEW.product_identification='product-b' BEGIN "
                    + "SELECT RAISE(ABORT, 'fixture failure'); END");
        }

        KeyPair pair = keyPair();
        RouteBackfillManifestArtifact manifest = manifest(List.of(
                entry(firstKey, 1, "product-a"), entry(secondKey, 1, "product-b")), secondKey);
        SqliteOutboxRouteBackfillApplier applier = applier(pair);
        assertThrows(SqliteOutboxRouteBackfillApplier.RouteBackfillApplyException.class,
                () -> applier.apply(db, "workload-a",
                        request(manifest, "123e4567-e89b-12d3-a456-426614174000", pair)));
        assertEquals(Arrays.asList((String) null, null), products(db));
        assertEquals(0, checkpointCount(db));
    }

    @Test
    void lockContentionFailsWithoutWaiting(@TempDir Path directory) throws Exception {
        Path db = migratedDb(directory);
        RouteBackfillKey key = key("device-a");
        insertRow(db, key, "row-a", null);
        KeyPair pair = keyPair();
        RouteBackfillManifestArtifact manifest = manifest(
                List.of(entry(key, 1, "product-a")), key);
        try (OutboxFileLock held = new OutboxFileLock(directory.resolve("collector-outbox.lock"))) {
            IllegalStateException failure = assertThrows(IllegalStateException.class,
                    () -> applier(pair).apply(db, "workload-a",
                            request(manifest, "123e4567-e89b-12d3-a456-426614174000", pair)));
            assertTrue(failure.getMessage().startsWith("OUTBOX_ALREADY_OWNED"));
        }
    }

    @Test
    void runningOutboxAndApplierContendForOneLockAndShutdownReleasesIt(@TempDir Path directory)
            throws Exception {
        Path db = migratedDb(directory);
        RouteBackfillKey key = key("device-a");
        insertRow(db, key, "row-a", null);
        KeyPair pair = keyPair();
        RouteBackfillManifestArtifact manifest = manifest(
                List.of(entry(key, 1, "product-a")), key);
        RouteBackfillApplyRequest applyRequest = request(
                manifest, "123e4567-e89b-12d3-a456-426614174000", pair);
        SqliteTelemetryOutbox running = new SqliteTelemetryOutbox(
                db, new EnvelopeCanonicalCodec(), 8);
        try {
            IllegalStateException failure = assertThrows(IllegalStateException.class,
                    () -> applier(pair).apply(db, "workload-a", applyRequest));
            assertTrue(failure.getMessage().startsWith("OUTBOX_ALREADY_OWNED"));
        } finally {
            running.shutdown();
        }

        RouteBackfillApplyResult.Applied applied = assertInstanceOf(
                RouteBackfillApplyResult.Applied.class,
                applier(pair).apply(db, "workload-a", applyRequest));
        assertEquals(1, applied.updatedRowCount());
        try (OutboxFileLock reopened = new OutboxFileLock(
                directory.resolve("collector-outbox.lock"))) {
            assertTrue(reopened != null);
        }
    }

    @Test
    void lockIsRequiredBeforeMigrationAndFailedStartupLeavesMissingDatabaseAbsent(
            @TempDir Path directory) throws Exception {
        Path db = directory.resolve("not-created.db");
        Path lock = directory.resolve("collector-outbox.lock");
        try (OutboxFileLock held = new OutboxFileLock(lock)) {
            IllegalStateException failure = assertThrows(IllegalStateException.class,
                    () -> new SqliteTelemetryOutbox(db, new EnvelopeCanonicalCodec(), 8));
            assertTrue(failure.getMessage().startsWith("OUTBOX_ALREADY_OWNED"));
            assertFalse(Files.exists(db));
        }
        SqliteTelemetryOutbox started = new SqliteTelemetryOutbox(
                db, new EnvelopeCanonicalCodec(), 8);
        started.shutdown();
        assertTrue(Files.exists(db));
    }

    @Test
    void emptyPageIsAppliedWithoutCursor(@TempDir Path directory) throws Exception {
        Path db = migratedDb(directory);
        KeyPair pair = keyPair();
        RouteBackfillManifestArtifact manifest = manifest(List.of(), null);
        RouteBackfillApplyResult.Applied result = assertInstanceOf(
                RouteBackfillApplyResult.Applied.class,
                applier(pair).apply(db, "workload-a", request(
                        manifest, "123e4567-e89b-12d3-a456-426614174000", pair)));
        assertEquals(0, result.updatedRowCount());
        assertEquals(null, result.nextInventoryCursor());
        assertCheckpointPair(db, manifest, "123e4567-e89b-12d3-a456-426614174000",
                "APPLIED", 0, null);
    }

    @Test
    void fiveHundredKeysAreAppliedInOnePage(@TempDir Path directory) throws Exception {
        Path db = migratedDb(directory);
        List<RouteBackfillManifestEntry> entries = new ArrayList<>();
        for (int i = 0; i < 500; i++) {
            RouteBackfillKey key = key(String.format("device-%03d", i));
            insertRow(db, key, "row-" + i, null);
            entries.add(entry(key, 1, "product-" + i));
        }
        KeyPair pair = keyPair();
        RouteBackfillManifestArtifact manifest = manifest(entries, entries.get(499).key());
        RouteBackfillApplyResult.Applied result = assertInstanceOf(
                RouteBackfillApplyResult.Applied.class,
                applier(pair).apply(db, "workload-a", request(
                        manifest, "123e4567-e89b-12d3-a456-426614174000", pair)));
        assertEquals(500, result.updatedRowCount());
        assertEquals(500, products(db).size());
        assertEquals("product-499", products(db).get(499));
    }

    @Test
    void existingSameProductRowsAreAllowedAlongsideNullRows(@TempDir Path directory)
            throws Exception {
        Path db = migratedDb(directory);
        RouteBackfillKey key = key("device-a");
        insertRow(db, key, "same", "product-a");
        insertRow(db, key, "null-1", null);
        insertRow(db, key, "null-2", null);
        KeyPair pair = keyPair();
        RouteBackfillManifestArtifact manifest = manifest(
                List.of(entry(key, 2, "product-a")), key);
        RouteBackfillApplyResult.Applied result = assertInstanceOf(
                RouteBackfillApplyResult.Applied.class,
                applier(pair).apply(db, "workload-a", request(
                        manifest, "123e4567-e89b-12d3-a456-426614174000", pair)));
        assertEquals(2, result.updatedRowCount());
        assertEquals(List.of("product-a", "product-a", "product-a"), products(db));
    }

    @Test
    void moreRowsThanManifestCountIsRowCountDegraded(@TempDir Path directory) throws Exception {
        Path db = migratedDb(directory);
        RouteBackfillKey key = key("device-a");
        insertRow(db, key, "row-a", null);
        insertRow(db, key, "row-b", null);
        KeyPair pair = keyPair();
        RouteBackfillManifestArtifact manifest = manifest(
                List.of(entry(key, 1, "product-a")), key);
        RouteBackfillApplyResult.Degraded result = assertInstanceOf(
                RouteBackfillApplyResult.Degraded.class,
                applier(pair).apply(db, "workload-a", request(
                        manifest, "123e4567-e89b-12d3-a456-426614174000", pair)));
        assertEquals(SqliteOutboxRouteBackfillApplier.ROUTE_BACKFILL_ROW_COUNT_MISMATCH,
                result.code());
        assertEquals(Arrays.asList((String) null, null), products(db));
        assertCheckpointPair(db, manifest, "123e4567-e89b-12d3-a456-426614174000",
                "DEGRADED", 0, null);
    }

    @Test
    void nfdAndNfcDeviceKeysAreMatchedByteExactly(@TempDir Path directory) throws Exception {
        String composed = "caf\u00e9";
        String decomposed = Normalizer.normalize(composed, Normalizer.Form.NFD);
        String composedAgain = Normalizer.normalize(composed, Normalizer.Form.NFC);
        assertFalse(decomposed.equals(composedAgain));

        Path exactDb = migratedDb(Files.createDirectory(directory.resolve("exact")));
        RouteBackfillKey exactKey = key(decomposed);
        insertRow(exactDb, exactKey, "exact", null);
        KeyPair exactPair = keyPair();
        RouteBackfillManifestArtifact exactManifest = manifest(
                List.of(entry(exactKey, 1, "product-exact")), exactKey);
        assertInstanceOf(RouteBackfillApplyResult.Applied.class,
                applier(exactPair).apply(exactDb, "workload-a", request(
                        exactManifest, "123e4567-e89b-12d3-a456-426614174000", exactPair)));

        Path mismatchDb = migratedDb(Files.createDirectory(directory.resolve("mismatch")));
        RouteBackfillKey storedNfd = key(decomposed);
        RouteBackfillKey requestedNfc = key(composedAgain);
        insertRow(mismatchDb, storedNfd, "mismatch", null);
        KeyPair mismatchPair = keyPair();
        RouteBackfillManifestArtifact mismatchManifest = manifest(
                List.of(entry(requestedNfc, 1, "product-mismatch")), requestedNfc);
        RouteBackfillApplyResult.Degraded result = assertInstanceOf(
                RouteBackfillApplyResult.Degraded.class,
                applier(mismatchPair).apply(mismatchDb, "workload-a", request(
                        mismatchManifest, "123e4567-e89b-12d3-a456-426614174000", mismatchPair)));
        assertEquals(SqliteOutboxRouteBackfillApplier.ROUTE_BACKFILL_ROW_COUNT_MISMATCH,
                result.code());
        assertEquals(Arrays.asList((String) null), products(mismatchDb));
    }

    @Test
    void allFourOutboxStatusesAreEligibleForProductBackfill(@TempDir Path directory)
            throws Exception {
        String[] statuses = {"PENDING", "IN_FLIGHT", "ACKED", "DEAD_LETTER"};
        for (String status : statuses) {
            Path caseDirectory = Files.createDirectory(directory.resolve(status));
            Path db = migratedDb(caseDirectory);
            RouteBackfillKey key = key("device-a");
            insertRow(db, key, "row-" + status, null);
            setStatus(db, key, status);
            KeyPair pair = keyPair();
            RouteBackfillManifestArtifact manifest = manifest(
                    List.of(entry(key, 1, "product-" + status)), key);
            RouteBackfillApplyResult.Applied result = assertInstanceOf(
                    RouteBackfillApplyResult.Applied.class,
                    applier(pair).apply(db, "workload-a", request(
                            manifest, "123e4567-e89b-12d3-a456-426614174000", pair)));
            assertEquals(1, result.updatedRowCount(), status);
            assertEquals(List.of("product-" + status), products(db), status);
        }
    }

    @Test
    void identityAndRowCountDegradedCheckpointsAreDurableAndRepairable(@TempDir Path directory)
            throws Exception {
        Path identityDb = migratedDb(Files.createDirectory(directory.resolve("identity")));
        RouteBackfillKey identityKey = key("identity-device");
        insertRow(identityDb, identityKey, "identity-row", "wrong-product");
        KeyPair identityPair = keyPair();
        RouteBackfillManifestArtifact identityManifest = manifest(
                List.of(entry(identityKey, 1, "product-a")), identityKey);
        RouteBackfillApplyRequest identityRequest = request(
                identityManifest, "123e4567-e89b-12d3-a456-426614174000", identityPair);
        RouteBackfillApplyResult.Degraded identity = assertInstanceOf(
                RouteBackfillApplyResult.Degraded.class,
                applier(identityPair).apply(identityDb, "workload-a", identityRequest));
        assertEquals(SqliteOutboxRouteBackfillApplier.ROUTE_BACKFILL_LOCAL_IDENTITY_CONFLICT,
                identity.code());
        assertCheckpointPair(identityDb, identityManifest,
                "123e4567-e89b-12d3-a456-426614174000", "DEGRADED", 0, null);
        assertEquals(List.of("wrong-product"), products(identityDb));
        setProduct(identityDb, identityKey, null);
        RouteBackfillApplyResult.Applied identityRepair = assertInstanceOf(
                RouteBackfillApplyResult.Applied.class,
                applier(identityPair).apply(identityDb, "workload-a", identityRequest));
        assertEquals(1, identityRepair.updatedRowCount());
        assertCheckpointPair(identityDb, identityManifest,
                "123e4567-e89b-12d3-a456-426614174000", "APPLIED", 1, identityKey);

        Path countDb = migratedDb(Files.createDirectory(directory.resolve("count")));
        RouteBackfillKey countKey = key("count-device");
        insertRow(countDb, countKey, "count-row", null);
        KeyPair countPair = keyPair();
        RouteBackfillManifestArtifact countManifest = manifest(
                List.of(entry(countKey, 2, "product-b")), countKey);
        RouteBackfillApplyRequest countRequest = request(
                countManifest, "123e4567-e89b-12d3-a456-426614174001", countPair);
        RouteBackfillApplyResult.Degraded count = assertInstanceOf(
                RouteBackfillApplyResult.Degraded.class,
                applier(countPair).apply(countDb, "workload-a", countRequest));
        assertEquals(SqliteOutboxRouteBackfillApplier.ROUTE_BACKFILL_ROW_COUNT_MISMATCH,
                count.code());
        assertCheckpointPair(countDb, countManifest,
                "123e4567-e89b-12d3-a456-426614174001", "DEGRADED", 0, null);
        insertRow(countDb, countKey, "count-row-2", null);
        RouteBackfillApplyResult.Applied countRepair = assertInstanceOf(
                RouteBackfillApplyResult.Applied.class,
                applier(countPair).apply(countDb, "workload-a", countRequest));
        assertEquals(2, countRepair.updatedRowCount());
        assertCheckpointPair(countDb, countManifest,
                "123e4567-e89b-12d3-a456-426614174001", "APPLIED", 2, countKey);
    }

    @Test
    void missingDatabaseIsRejectedAsInfrastructureFailureWithoutCreation(@TempDir Path directory)
            throws Exception {
        KeyPair pair = keyPair();
        RouteBackfillApplyRequest applyRequest = validRequest(pair);
        Path db = directory.resolve("missing.db");
        assertApplyFailed(db, applyRequest, pair);
        assertFalse(Files.exists(db));
        assertFalse(Files.exists(directory.resolve("collector-outbox.lock")));
    }

    @Test
    void databaseDirectoryIsRejectedAsInfrastructureFailure(@TempDir Path directory)
            throws Exception {
        KeyPair pair = keyPair();
        RouteBackfillApplyRequest applyRequest = validRequest(pair);
        Path dbDirectory = Files.createDirectory(directory.resolve("db-directory"));
        assertApplyFailed(dbDirectory, applyRequest, pair);
        assertFalse(Files.exists(directory.resolve("collector-outbox.lock")));
    }

    @Test
    void nonV3DatabaseIsRejectedWithoutMigration(@TempDir Path directory) throws Exception {
        Path db = migratedDb(directory);
        setUserVersion(db, 2);
        KeyPair pair = keyPair();
        assertApplyFailed(db, validRequest(pair), pair);
        assertEquals(2, userVersion(db));
    }

    @Test
    void missingOutboxMetaIsRejectedWithoutMigration(@TempDir Path directory) throws Exception {
        Path db = migratedDb(directory);
        try (Connection connection = open(db); Statement statement = connection.createStatement()) {
            statement.execute("DROP TABLE outbox_meta");
        }
        KeyPair pair = keyPair();
        assertApplyFailed(db, validRequest(pair), pair);
        assertFalse(tableExists(db, "outbox_meta"));
    }

    @Test
    void missingTelemetryOutboxIsRejectedWithoutMigration(@TempDir Path directory)
            throws Exception {
        Path db = migratedDb(directory);
        try (Connection connection = open(db); Statement statement = connection.createStatement()) {
            statement.execute("DROP TABLE telemetry_outbox");
        }
        KeyPair pair = keyPair();
        assertApplyFailed(db, validRequest(pair), pair);
        assertFalse(tableExists(db, "telemetry_outbox"));
    }

    @Test
    void missingProductColumnIsRejectedWithoutMigration(@TempDir Path directory) throws Exception {
        Path db = directory.resolve("missing-product.db");
        try (Connection connection = open(db); Statement statement = connection.createStatement()) {
            statement.execute("CREATE TABLE telemetry_outbox (id INTEGER PRIMARY KEY)");
            statement.execute("CREATE TABLE outbox_meta (meta_key TEXT PRIMARY KEY, "
                    + "meta_value TEXT, updated_at_ms INTEGER)");
            statement.execute("PRAGMA user_version=3");
        }
        KeyPair pair = keyPair();
        assertApplyFailed(db, validRequest(pair), pair);
        assertEquals(3, userVersion(db));
        assertTrue(tableExists(db, "telemetry_outbox"));
    }

    @Test
    void quickCheckFailureIsRejectedWithoutMigration(@TempDir Path directory) throws Exception {
        Path db = migratedDb(directory);
        corruptForQuickCheck(db);
        KeyPair pair = keyPair();
        assertApplyFailed(db, validRequest(pair), pair);
        assertEquals(3, userVersion(db));
    }

    @Test
    void bindingAndTargetFailuresDoNotInspectDatabaseOrCreateLock(@TempDir Path directory)
            throws Exception {
        Path absent = directory.resolve("guard.db");
        KeyPair pair = keyPair();
        RouteBackfillManifestArtifact manifest = manifest(
                List.of(), null);
        RouteBackfillApplyRequest bindingMismatch = requestWithBinding(
                manifest, "123e4567-e89b-12d3-a456-426614174000", pair,
                "b".repeat(64), INVENTORY_HASH, "workload-a");
        RouteBackfillApplyResult.Rejected binding = assertInstanceOf(
                RouteBackfillApplyResult.Rejected.class,
                applier(pair).apply(absent, "workload-a", bindingMismatch));
        assertEquals("ROUTE_BACKFILL_AUTHORIZATION_BINDING_MISMATCH", binding.code());
        assertFalse(Files.exists(absent));
        assertFalse(Files.exists(directory.resolve("collector-outbox.lock")));

        RouteBackfillApplyResult.Rejected target = assertInstanceOf(
                RouteBackfillApplyResult.Rejected.class,
                applier(pair).apply(absent, "another-workload",
                        request(manifest, "123e4567-e89b-12d3-a456-426614174001", pair)));
        assertEquals("ROUTE_BACKFILL_TARGET_WORKLOAD_MISMATCH", target.code());
        assertFalse(Files.exists(absent));
        assertFalse(Files.exists(directory.resolve("collector-outbox.lock")));
    }

    @Test
    void invalidSignatureDoesNotInspectDatabaseOrCreateLock(@TempDir Path directory)
            throws Exception {
        KeyPair pair = keyPair();
        RouteBackfillManifestArtifact manifest = manifest(List.of(), null);
        RouteBackfillApplyRequest valid = request(
                manifest, "123e4567-e89b-12d3-a456-426614174000", pair);
        byte[] signature = Base64.getDecoder().decode(
                valid.authorizationArtifact().signatureBase64());
        signature[0] ^= 0x01;
        RouteBackfillAuthorizationArtifact tampered = new RouteBackfillAuthorizationArtifact(
                valid.authorizationArtifact().authorization(),
                valid.authorizationArtifact().canonicalBytes(),
                valid.authorizationArtifact().contentSha256(),
                Base64.getEncoder().encodeToString(signature));
        RouteBackfillApplyRequest invalid = new RouteBackfillApplyRequest(
                valid.manifestArtifact(), tampered);
        Path db = directory.resolve("signature-failure.db");
        RouteBackfillApplyResult.Rejected result = assertInstanceOf(
                RouteBackfillApplyResult.Rejected.class,
                applier(pair).apply(db, "workload-a", invalid));
        assertEquals("ROUTE_BACKFILL_AUTHORIZATION_SIGNATURE_INVALID", result.code());
        assertFalse(Files.exists(db));
        assertFalse(Files.exists(directory.resolve("collector-outbox.lock")));
    }

    @Test
    void databaseSymbolicLinkIsRejectedWithoutChangingRealDatabase(@TempDir Path directory)
            throws Exception {
        Path realDirectory = Files.createDirectory(directory.resolve("real-db"));
        Path realDb = migratedDb(realDirectory);
        RouteBackfillKey key = key("device-a");
        insertRow(realDb, key, "row-a", null);
        List<List<String>> rowsBefore = snapshotRows(realDb);
        Map<String, String> metaBefore = checkpointSnapshot(realDb);
        Path link = directory.resolve("db-link.db");
        Files.createSymbolicLink(link, realDb.toAbsolutePath());
        assertTrue(Files.isSymbolicLink(link));

        KeyPair pair = keyPair();
        RouteBackfillManifestArtifact manifest = manifest(
                List.of(entry(key, 1, "product-a")), key);
        SqliteOutboxRouteBackfillApplier.RouteBackfillApplyException failure = assertThrows(
                SqliteOutboxRouteBackfillApplier.RouteBackfillApplyException.class,
                () -> applier(pair).apply(link, "workload-a", request(
                        manifest, "123e4567-e89b-12d3-a456-426614174000", pair)));
        assertTrue(failure.getMessage().startsWith(
                SqliteOutboxRouteBackfillApplier.ROUTE_BACKFILL_APPLY_FAILED));
        assertEquals(rowsBefore, snapshotRows(realDb));
        assertEquals(metaBefore, checkpointSnapshot(realDb));
    }

    @Test
    void symbolicLinkLockIsRejectedWithoutChangingDatabase(@TempDir Path directory)
            throws Exception {
        Path db = migratedDb(directory);
        RouteBackfillKey key = key("device-a");
        insertRow(db, key, "row-a", null);
        List<List<String>> rowsBefore = snapshotRows(db);
        Map<String, String> metaBefore = checkpointSnapshot(db);
        Path lockTarget = directory.resolve("lock-target");
        Files.writeString(lockTarget, "ordinary lock target");
        Path lock = directory.resolve("collector-outbox.lock");
        Files.createSymbolicLink(lock, lockTarget.toAbsolutePath());
        assertTrue(Files.isSymbolicLink(lock));

        KeyPair pair = keyPair();
        RouteBackfillManifestArtifact manifest = manifest(
                List.of(entry(key, 1, "product-a")), key);
        SqliteOutboxRouteBackfillApplier.RouteBackfillApplyException failure = assertThrows(
                SqliteOutboxRouteBackfillApplier.RouteBackfillApplyException.class,
                () -> applier(pair).apply(db, "workload-a", request(
                        manifest, "123e4567-e89b-12d3-a456-426614174000", pair)));
        assertTrue(failure.getMessage().startsWith(
                SqliteOutboxRouteBackfillApplier.ROUTE_BACKFILL_APPLY_FAILED));
        assertEquals(rowsBefore, snapshotRows(db));
        assertEquals(metaBefore, checkpointSnapshot(db));
    }

    private static SqliteOutboxRouteBackfillApplier applier(KeyPair pair) {
        RouteBackfillAuthorizationVerifier verifier = new RouteBackfillAuthorizationVerifier(
                id -> Optional.of(pair.getPublic()));
        return new SqliteOutboxRouteBackfillApplier(verifier, CLOCK);
    }

    private static RouteBackfillApplyRequest request(RouteBackfillManifestArtifact manifest,
                                                      String operationId, KeyPair pair)
            throws Exception {
        RouteBackfillAuthorization authorization = new RouteBackfillAuthorization(
                RouteBackfillAuthorization.SCHEMA_VERSION,
                RouteBackfillAuthorization.CANONICALIZATION_VERSION,
                RouteBackfillAuthorization.SIGNATURE_ALGORITHM,
                RouteBackfillAuthorization.SIGNATURE_CONTEXT,
                KEY_ID, operationId, NOW - 10, NOW + 600,
                manifest.contentSha256(), manifest.manifest().sourceInventorySha256(),
                manifest.manifest().workloadId());
        byte[] canonical = JCS.canonicalize(MAPPER.valueToTree(authorization))
                .getBytes(StandardCharsets.UTF_8);
        byte[] domain = RouteBackfillAuthorizationVerifier.DOMAIN_SEPARATOR
                .getBytes(StandardCharsets.UTF_8);
        byte[] input = new byte[domain.length + canonical.length];
        System.arraycopy(domain, 0, input, 0, domain.length);
        System.arraycopy(canonical, 0, input, domain.length, canonical.length);
        Signature signer = Signature.getInstance("Ed25519");
        signer.initSign(pair.getPrivate());
        signer.update(input);
        return new RouteBackfillApplyRequest(manifest,
                new RouteBackfillAuthorizationArtifact(authorization, canonical,
                        sha256(canonical), Base64.getEncoder().encodeToString(signer.sign())));
    }

    private static RouteBackfillManifestArtifact manifest(List<RouteBackfillManifestEntry> entries,
                                                           RouteBackfillKey cursor) {
        return new RouteBackfillManifestArtifact(new RouteBackfillManifest(
                RouteBackfillManifest.SCHEMA_VERSION,
                RouteBackfillManifest.CANONICALIZATION_VERSION,
                INVENTORY_HASH, "workload-a", entries, cursor));
    }

    private static RouteBackfillManifestEntry entry(RouteBackfillKey key, long rowCount,
                                                    String product) {
        return new RouteBackfillManifestEntry(key, rowCount, product, "workload-a", 1,
                "b".repeat(64));
    }

    private static RouteBackfillKey key(String device) {
        return new RouteBackfillKey("tenant", "site", 1, device);
    }

    private static Path migratedDb(Path directory) throws Exception {
        Path db = directory.resolve("outbox.db");
        SqliteOutboxMigration.migrate(db);
        return db;
    }

    private static RouteBackfillApplyRequest validRequest(KeyPair pair) throws Exception {
        return request(manifest(List.of(), null),
                "123e4567-e89b-12d3-a456-426614174000", pair);
    }

    private static RouteBackfillApplyRequest requestWithBinding(
            RouteBackfillManifestArtifact manifest, String operationId, KeyPair pair,
            String manifestHash, String sourceInventoryHash, String workloadId) throws Exception {
        RouteBackfillAuthorization authorization = new RouteBackfillAuthorization(
                RouteBackfillAuthorization.SCHEMA_VERSION,
                RouteBackfillAuthorization.CANONICALIZATION_VERSION,
                RouteBackfillAuthorization.SIGNATURE_ALGORITHM,
                RouteBackfillAuthorization.SIGNATURE_CONTEXT,
                KEY_ID, operationId, NOW - 10, NOW + 600,
                manifestHash, sourceInventoryHash, workloadId);
        byte[] canonical = JCS.canonicalize(MAPPER.valueToTree(authorization))
                .getBytes(StandardCharsets.UTF_8);
        byte[] domain = RouteBackfillAuthorizationVerifier.DOMAIN_SEPARATOR
                .getBytes(StandardCharsets.UTF_8);
        byte[] input = new byte[domain.length + canonical.length];
        System.arraycopy(domain, 0, input, 0, domain.length);
        System.arraycopy(canonical, 0, input, domain.length, canonical.length);
        Signature signer = Signature.getInstance("Ed25519");
        signer.initSign(pair.getPrivate());
        signer.update(input);
        return new RouteBackfillApplyRequest(manifest,
                new RouteBackfillAuthorizationArtifact(authorization, canonical,
                        sha256(canonical), Base64.getEncoder().encodeToString(signer.sign())));
    }

    private static void assertApplyFailed(Path db, RouteBackfillApplyRequest request,
                                          KeyPair pair) {
        SqliteOutboxRouteBackfillApplier.RouteBackfillApplyException failure = assertThrows(
                SqliteOutboxRouteBackfillApplier.RouteBackfillApplyException.class,
                () -> applier(pair).apply(db, "workload-a", request));
        assertTrue(failure.getMessage().startsWith(
                SqliteOutboxRouteBackfillApplier.ROUTE_BACKFILL_APPLY_FAILED));
    }

    private static void setUserVersion(Path db, int version) throws Exception {
        try (Connection connection = open(db); Statement statement = connection.createStatement()) {
            statement.execute("PRAGMA user_version=" + version);
        }
    }

    private static int userVersion(Path db) throws Exception {
        try (Connection connection = open(db); Statement statement = connection.createStatement();
             ResultSet rows = statement.executeQuery("PRAGMA user_version")) {
            assertTrue(rows.next());
            return rows.getInt(1);
        }
    }

    private static boolean tableExists(Path db, String table) throws Exception {
        try (Connection connection = open(db);
             PreparedStatement query = connection.prepareStatement(
                     "SELECT 1 FROM sqlite_master WHERE type='table' AND name=?")) {
            query.setString(1, table);
            try (ResultSet rows = query.executeQuery()) {
                return rows.next();
            }
        }
    }

    private static void corruptForQuickCheck(Path db) throws Exception {
        try (Connection connection = open(db); Statement statement = connection.createStatement()) {
            statement.execute("PRAGMA writable_schema=ON");
            statement.execute("UPDATE sqlite_master SET rootpage=0 WHERE name='telemetry_outbox'");
            statement.execute("PRAGMA writable_schema=OFF");
        }
    }

    private static void putCheckpoint(Path db, String key, String value) throws Exception {
        try (Connection connection = open(db); PreparedStatement update = connection.prepareStatement(
                "INSERT INTO outbox_meta(meta_key, meta_value, updated_at_ms) VALUES(?,?,?) "
                        + "ON CONFLICT(meta_key) DO UPDATE SET meta_value=excluded.meta_value, "
                        + "updated_at_ms=excluded.updated_at_ms")) {
            update.setString(1, key);
            update.setString(2, value);
            update.setLong(3, NOW * 1000);
            update.executeUpdate();
        }
    }

    private static String checkpointVariant(String original, String variant) throws Exception {
        if ("noncanonical".equals(variant)) {
            return " " + original + " ";
        }
        ObjectNode root = (ObjectNode) MAPPER.readTree(original);
        if ("canonical".equals(variant)) {
            root.put("operationId", "123e4567-e89b-12d3-a456-426614174001");
        } else if ("extra".equals(variant)) {
            root.put("unexpected", "must-conflict");
        } else if ("timestamp".equals(variant)) {
            root.put("updatedAtEpochSeconds", NOW + 1);
        } else {
            throw new IllegalArgumentException("unknown checkpoint variant: " + variant);
        }
        return JCS.canonicalize(root);
    }

    private static void assertCheckpointPair(Path db, RouteBackfillManifestArtifact manifest,
                                             String operationId, String status,
                                             long appliedRows, RouteBackfillKey cursor)
            throws Exception {
        String operationValue = checkpointValue(db, "route_backfill.v1.operation." + operationId);
        String manifestValue = checkpointValue(db,
                "route_backfill.v1.manifest." + manifest.contentSha256());
        assertEquals(operationValue, manifestValue);
        JsonNode root = MAPPER.readTree(operationValue);
        assertEquals(operationValue, JCS.canonicalize(root));
        Set<String> fields = new HashSet<>();
        root.fieldNames().forEachRemaining(fields::add);
        assertEquals(Set.of("schemaVersion", "operationId", "manifestContentSha256",
                "sourceInventorySha256", "workloadId", "keyId", "status",
                "appliedRowCount", "nextInventoryCursor", "updatedAtEpochSeconds"), fields);
        assertEquals("1.0", root.get("schemaVersion").textValue());
        assertEquals(operationId, root.get("operationId").textValue());
        assertEquals(manifest.contentSha256(), root.get("manifestContentSha256").textValue());
        assertEquals(INVENTORY_HASH, root.get("sourceInventorySha256").textValue());
        assertEquals("workload-a", root.get("workloadId").textValue());
        assertEquals(KEY_ID, root.get("keyId").textValue());
        assertEquals(status, root.get("status").textValue());
        assertEquals(appliedRows, root.get("appliedRowCount").longValue());
        if (cursor == null) {
            assertTrue(root.get("nextInventoryCursor").isNull());
        } else {
            JsonNode cursorNode = root.get("nextInventoryCursor");
            assertEquals(cursor.tenantId(), cursorNode.get("tenantId").textValue());
            assertEquals(cursor.siteCode(), cursorNode.get("siteCode").textValue());
            assertEquals(cursor.configVersion(), cursorNode.get("configVersion").longValue());
            assertEquals(cursor.deviceIdentification(),
                    cursorNode.get("deviceIdentification").textValue());
        }
    }

    private static void insertRow(Path db, RouteBackfillKey key, String messageId,
                                  String product) throws Exception {
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
            insert.setString(3, key.tenantId());
            insert.setString(4, key.siteCode());
            insert.setString(5, key.deviceIdentification());
            insert.setString(6, "voltage-a");
            insert.setLong(7, 1);
            insert.setLong(8, NOW * 1000);
            insert.setString(9, "NORMAL_TELEMETRY");
            insert.setInt(10, 3);
            insert.setBytes(11, new byte[]{1, 2, 3});
            insert.setString(12, "c".repeat(64));
            insert.setInt(13, 3);
            insert.setString(14, "PENDING");
            insert.setString(15, "REALTIME");
            insert.setLong(16, NOW * 1000);
            insert.setLong(17, NOW * 1000 + 1);
            insert.setLong(18, key.configVersion());
            if (product == null) {
                insert.setNull(19, java.sql.Types.VARCHAR);
            } else {
                insert.setString(19, product);
            }
            insert.executeUpdate();
        }
    }

    private static void setProduct(Path db, RouteBackfillKey key, String product) throws Exception {
        try (Connection connection = open(db);
             PreparedStatement update = connection.prepareStatement(
                     "UPDATE telemetry_outbox SET product_identification=? WHERE tenant_id=? "
                             + "AND site_code=? AND config_version=? AND device_identification=?")) {
            update.setString(1, product);
            update.setString(2, key.tenantId());
            update.setString(3, key.siteCode());
            update.setLong(4, key.configVersion());
            update.setString(5, key.deviceIdentification());
            update.executeUpdate();
        }
    }

    private static void setStatus(Path db, RouteBackfillKey key, String status) throws Exception {
        try (Connection connection = open(db);
             PreparedStatement update = connection.prepareStatement(
                     "UPDATE telemetry_outbox SET status=? WHERE tenant_id=? AND site_code=? "
                             + "AND config_version=? AND device_identification=?")) {
            update.setString(1, status);
            update.setString(2, key.tenantId());
            update.setString(3, key.siteCode());
            update.setLong(4, key.configVersion());
            update.setString(5, key.deviceIdentification());
            assertEquals(1, update.executeUpdate());
        }
    }

    private static List<String> products(Path db) throws Exception {
        List<String> products = new ArrayList<>();
        try (Connection connection = open(db);
             Statement statement = connection.createStatement();
             ResultSet rows = statement.executeQuery(
                     "SELECT product_identification FROM telemetry_outbox ORDER BY id")) {
            while (rows.next()) {
                products.add(rows.getString(1));
            }
        }
        return products;
    }

    private static List<List<String>> snapshotRows(Path db) throws Exception {
        List<List<String>> result = new ArrayList<>();
        try (Connection connection = open(db);
             Statement statement = connection.createStatement();
             ResultSet rows = statement.executeQuery("SELECT * FROM telemetry_outbox ORDER BY id")) {
            ResultSetMetaData metadata = rows.getMetaData();
            while (rows.next()) {
                List<String> row = new ArrayList<>();
                for (int i = 1; i <= metadata.getColumnCount(); i++) {
                    Object value = rows.getObject(i);
                    row.add(value instanceof byte[] bytes
                            ? "B:" + Base64.getEncoder().encodeToString(bytes)
                            : String.valueOf(value));
                }
                result.add(row);
            }
        }
        return result;
    }

    private static void assertOnlyProductChanged(List<List<String>> before,
                                                 List<List<String>> after) {
        assertEquals(before.size(), after.size());
        for (int row = 0; row < before.size(); row++) {
            assertEquals(before.get(row).size(), after.get(row).size());
            for (int column = 0; column < before.get(row).size(); column++) {
                if (column != 5) {
                    assertEquals(before.get(row).get(column), after.get(row).get(column),
                            "column " + column + " changed unexpectedly");
                }
            }
        }
    }

    private static int checkpointCount(Path db) throws Exception {
        try (Connection connection = open(db);
             PreparedStatement query = connection.prepareStatement(
                     "SELECT count(*) FROM outbox_meta WHERE meta_key LIKE 'route_backfill.v1.%'")) {
            try (ResultSet rows = query.executeQuery()) {
                rows.next();
                return rows.getInt(1);
            }
        }
    }

    private static String checkpointValue(Path db, String key) throws Exception {
        try (Connection connection = open(db);
             PreparedStatement query = connection.prepareStatement(
                     "SELECT meta_value FROM outbox_meta WHERE meta_key=?")) {
            query.setString(1, key);
            try (ResultSet rows = query.executeQuery()) {
                assertTrue(rows.next());
                return rows.getString(1);
            }
        }
    }

    private static Map<String, String> checkpointSnapshot(Path db) throws Exception {
        Map<String, String> result = new LinkedHashMap<>();
        try (Connection connection = open(db);
             PreparedStatement query = connection.prepareStatement(
                     "SELECT meta_key, meta_value FROM outbox_meta "
                             + "WHERE meta_key LIKE 'route_backfill.v1.%' ORDER BY meta_key");
             ResultSet rows = query.executeQuery()) {
            while (rows.next()) {
                result.put(rows.getString(1), rows.getString(2));
            }
        }
        return result;
    }

    private static Connection open(Path db) throws Exception {
        return DriverManager.getConnection("jdbc:sqlite:" + db.toAbsolutePath());
    }

    private static KeyPair keyPair() throws Exception {
        return KeyPairGenerator.getInstance("Ed25519").generateKeyPair();
    }

    private static String sha256(byte[] bytes) throws Exception {
        byte[] digest = java.security.MessageDigest.getInstance("SHA-256").digest(bytes);
        StringBuilder result = new StringBuilder(64);
        for (byte value : digest) {
            result.append(Character.forDigit((value >>> 4) & 0xf, 16));
            result.append(Character.forDigit(value & 0xf, 16));
        }
        return result.toString();
    }
}
