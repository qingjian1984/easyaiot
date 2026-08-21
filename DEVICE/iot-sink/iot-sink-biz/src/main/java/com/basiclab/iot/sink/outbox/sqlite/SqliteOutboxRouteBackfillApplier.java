package com.basiclab.iot.sink.outbox.sqlite;

import com.basiclab.iot.sink.telemetry.envelope.EnvelopeJcsCanonicalizer;
import com.basiclab.iot.sink.telemetry.outbox.backfill.RouteBackfillApplyRequest;
import com.basiclab.iot.sink.telemetry.outbox.backfill.RouteBackfillApplyResult;
import com.basiclab.iot.sink.telemetry.outbox.backfill.RouteBackfillAuthorization;
import com.basiclab.iot.sink.telemetry.outbox.backfill.RouteBackfillKey;
import com.basiclab.iot.sink.telemetry.outbox.backfill.RouteBackfillManifest;
import com.basiclab.iot.sink.telemetry.outbox.backfill.RouteBackfillManifestEntry;
import com.basiclab.iot.sink.telemetry.outbox.backfill.RouteBackfillVerificationResult;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Clock;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Collector-owned, offline, authenticated route backfill writer.
 *
 * <p>The verifier is deliberately invoked before any path inspection or
 * SQLite connection is opened.  The process lock is then acquired before the
 * database is inspected and one page is applied in one explicit
 * {@code BEGIN IMMEDIATE} transaction.</p>
 */
public final class SqliteOutboxRouteBackfillApplier {

    public static final String ROUTE_BACKFILL_APPLY_FAILED = "ROUTE_BACKFILL_APPLY_FAILED";
    public static final String OUTBOX_ALREADY_OWNED = "OUTBOX_ALREADY_OWNED";
    public static final String ROUTE_BACKFILL_OPERATION_COLLISION =
            "ROUTE_BACKFILL_OPERATION_COLLISION";
    public static final String ROUTE_BACKFILL_CHECKPOINT_CONFLICT =
            "ROUTE_BACKFILL_CHECKPOINT_CONFLICT";
    public static final String ROUTE_BACKFILL_LOCAL_IDENTITY_CONFLICT =
            "ROUTE_BACKFILL_LOCAL_IDENTITY_CONFLICT";
    public static final String ROUTE_BACKFILL_ROW_COUNT_MISMATCH =
            "ROUTE_BACKFILL_ROW_COUNT_MISMATCH";

    private static final int MAX_PAGE_ENTRIES = 500;
    private static final String CHECKPOINT_PREFIX = "route_backfill.v1.";
    private static final String CHECKPOINT_SCHEMA_VERSION = "1.0";
    private static final Pattern SHA256_HEX = Pattern.compile("[0-9a-f]{64}");
    private static final Pattern KEY_ID = Pattern.compile("[A-Za-z0-9._-]{1,64}");
    private static final List<String> CHECKPOINT_FIELDS = List.of(
            "schemaVersion", "operationId", "manifestContentSha256", "sourceInventorySha256",
            "workloadId", "keyId", "status", "appliedRowCount", "nextInventoryCursor",
            "updatedAtEpochSeconds");
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final EnvelopeJcsCanonicalizer JCS = new EnvelopeJcsCanonicalizer();

    private final RouteBackfillAuthorizationVerifier verifier;
    private final Clock clock;

    public SqliteOutboxRouteBackfillApplier(RouteBackfillAuthorizationVerifier verifier,
                                            Clock clock) {
        if (verifier == null) {
            throw new IllegalArgumentException("verifier required");
        }
        if (clock == null) {
            throw new IllegalArgumentException("clock required");
        }
        this.verifier = verifier;
        this.clock = clock;
    }

    public SqliteOutboxRouteBackfillApplier(RouteBackfillAuthorizationVerifier verifier) {
        this(verifier, Clock.systemUTC());
    }

    /**
     * Applies one already-materialized manifest page to the existing V3
     * outbox.  A rejected authorization is returned without opening the
     * database; local identity problems become a durable DEGRADED checkpoint.
     */
    public RouteBackfillApplyResult apply(Path dbPath, String expectedWorkloadId,
                                          RouteBackfillApplyRequest request) {
        RouteBackfillVerificationResult verification =
                verifier.verify(request, expectedWorkloadId, clock);
        if (verification instanceof RouteBackfillVerificationResult.Rejected rejected) {
            return new RouteBackfillApplyResult.Rejected(
                    rejected.operationId(), rejected.manifestContentSha256(), rejected.code());
        }
        if (!(verification instanceof RouteBackfillVerificationResult.Verified verified)) {
            throw applyFailed("verifier returned an unsupported result", null);
        }

        RouteBackfillApplyRequest verifiedRequest = verified.request();
        RouteBackfillManifest manifest = verifiedRequest.manifestArtifact().manifest();
        RouteBackfillAuthorization authorization =
                verifiedRequest.authorizationArtifact().authorization();
        if (manifest.entries().size() > MAX_PAGE_ENTRIES) {
            throw applyFailed("manifest page exceeds 500 entries", null);
        }

        Path database = validateDatabasePath(dbPath);
        Path lockPath = database.getParent().resolve("collector-outbox.lock");
        if (Files.isSymbolicLink(lockPath)) {
            throw applyFailed("collector-outbox.lock must not be a symbolic link", null);
        }

        try (OutboxFileLock ignored = new OutboxFileLock(lockPath);
            Connection connection = open(database)) {
            verifyV3Schema(connection);

            String operationKey = CHECKPOINT_PREFIX + "operation." + authorization.operationId();
            String manifestKey = CHECKPOINT_PREFIX + "manifest."
                    + verifiedRequest.manifestArtifact().contentSha256();
            try {
                beginImmediate(connection);
                CheckpointPair checkpoints = readCheckpoints(connection, operationKey, manifestKey);
                CheckpointDecision decision = decideCheckpoint(
                        checkpoints, authorization, manifest, verifiedRequest);
                if (decision.result() != null) {
                    commit(connection);
                    return decision.result();
                }
                long updatedRows = applyPage(connection, manifest);
                String checkpoint = checkpointJson(authorization, manifest, "APPLIED",
                        updatedRows, manifest.inventoryNextCursor());
                writeCheckpoint(connection, operationKey, checkpoint);
                writeCheckpoint(connection, manifestKey, checkpoint);
                commit(connection);
                return new RouteBackfillApplyResult.Applied(
                        authorization.operationId(),
                        verifiedRequest.manifestArtifact().contentSha256(),
                        updatedRows, manifest.inventoryNextCursor());
        } catch (LocalMismatch mismatch) {
                rollbackQuietly(connection);
                return writeDegradedCheckpoint(connection, operationKey, manifestKey,
                        authorization, manifest,
                        verifiedRequest.manifestArtifact().contentSha256(), mismatch.code());
            } catch (SQLException | RuntimeException failure) {
                rollbackQuietly(connection);
                if (failure instanceof RouteBackfillApplyException applyException) {
                    throw applyException;
                }
                throw applyFailed("transactional route backfill failed", failure);
            }
        } catch (RouteBackfillApplyException e) {
            throw e;
        } catch (IllegalStateException e) {
            if (e.getMessage() != null && e.getMessage().startsWith(OUTBOX_ALREADY_OWNED)) {
                throw e;
            }
            throw applyFailed("route backfill lock or database failed", e);
        } catch (IOException | SQLException | RuntimeException e) {
            throw applyFailed("route backfill lock or database failed", e);
        }
    }

    private static Path validateDatabasePath(Path dbPath) {
        if (dbPath == null) {
            throw applyFailed("database path is required", null);
        }
        Path supplied = dbPath.toAbsolutePath().normalize();
        Path parent = supplied.getParent();
        try {
            if (parent == null || !Files.isDirectory(parent, LinkOption.NOFOLLOW_LINKS)
                    || !Files.isRegularFile(supplied, LinkOption.NOFOLLOW_LINKS)
                    || Files.isSymbolicLink(supplied)) {
                throw applyFailed("database must be an existing ordinary file", null);
            }
            Path realParent = parent.toRealPath();
            Path realDatabase = supplied.toRealPath();
            if (!realParent.equals(realDatabase.getParent())) {
                throw applyFailed("database must remain inside its real parent", null);
            }
            return realDatabase;
        } catch (IOException e) {
            throw applyFailed("database path cannot be resolved", e);
        }
    }

    private static Connection open(Path database) throws SQLException {
        return DriverManager.getConnection("jdbc:sqlite:" + database);
    }

    private static void verifyV3Schema(Connection connection) throws SQLException {
        try (Statement statement = connection.createStatement();
             ResultSet version = statement.executeQuery("PRAGMA user_version")) {
            if (!version.next() || version.getInt(1) != SqliteOutboxMigration.USER_VERSION) {
                throw applyFailed("SQLite user_version must be 3", null);
            }
        }

        try (Statement statement = connection.createStatement();
             ResultSet check = statement.executeQuery("PRAGMA quick_check")) {
            if (!check.next() || !"ok".equals(check.getString(1))) {
                throw applyFailed("SQLite quick_check is not ok", null);
            }
        }

        if (!tableExists(connection, "outbox_meta")
                || !tableExists(connection, "telemetry_outbox")
                || !columnExists(connection, "telemetry_outbox", "product_identification")) {
            throw applyFailed("required V3 outbox schema is absent", null);
        }
    }

    private static boolean tableExists(Connection connection, String table) throws SQLException {
        try (PreparedStatement query = connection.prepareStatement(
                "SELECT 1 FROM sqlite_master WHERE type='table' AND name=?")) {
            query.setString(1, table);
            try (ResultSet rows = query.executeQuery()) {
                return rows.next();
            }
        }
    }

    private static boolean columnExists(Connection connection, String table, String column)
            throws SQLException {
        try (PreparedStatement query = connection.prepareStatement("PRAGMA table_info(" + table + ")");
             ResultSet columns = query.executeQuery()) {
            while (columns.next()) {
                if (column.equals(columns.getString("name"))) {
                    return true;
                }
            }
            return false;
        }
    }

    private static CheckpointPair readCheckpoints(Connection connection,
                                                  String operationKey,
                                                  String manifestKey) throws SQLException {
        return new CheckpointPair(readCheckpoint(connection, operationKey),
                readCheckpoint(connection, manifestKey));
    }

    private static MetaCheckpoint readCheckpoint(Connection connection, String key)
            throws SQLException {
        try (PreparedStatement query = connection.prepareStatement(
                "SELECT meta_value FROM outbox_meta WHERE meta_key=?")) {
            query.setString(1, key);
            try (ResultSet rows = query.executeQuery()) {
                if (!rows.next()) {
                    return MetaCheckpoint.absent();
                }
                String value = rows.getString(1);
                Checkpoint checkpoint = parseCheckpoint(value);
                return new MetaCheckpoint(true, value, checkpoint);
            }
        }
    }

    private static Checkpoint parseCheckpoint(String value) {
        if (value == null) {
            return null;
        }
        try {
            JsonNode root = MAPPER.readTree(value);
            if (root == null || !root.isObject()
                    || root.size() != CHECKPOINT_FIELDS.size()
                    || !CHECKPOINT_FIELDS.stream().allMatch(root::has)
                    || !value.equals(JCS.canonicalize(root))
                    || !CHECKPOINT_SCHEMA_VERSION.equals(text(root, "schemaVersion"))
                    || !isCanonicalUuid(text(root, "operationId"))
                    || !isSha256(text(root, "manifestContentSha256"))
                    || !isSha256(text(root, "sourceInventorySha256"))
                    || text(root, "workloadId") == null
                    || text(root, "workloadId").isBlank()
                    || !isKeyId(text(root, "keyId"))) {
                return null;
            }
            String status = text(root, "status");
            if (!"APPLIED".equals(status) && !"DEGRADED".equals(status)) {
                return null;
            }
            JsonNode countNode = root.get("appliedRowCount");
            if (countNode == null || !countNode.isIntegralNumber()
                    || !countNode.canConvertToLong() || countNode.asLong() < 0) {
                return null;
            }
            JsonNode cursorNode = root.get("nextInventoryCursor");
            RouteBackfillKey cursor = parseCursor(cursorNode);
            JsonNode updatedNode = root.get("updatedAtEpochSeconds");
            if (updatedNode == null || !updatedNode.isIntegralNumber()
                    || !updatedNode.canConvertToLong() || updatedNode.asLong() <= 0) {
                return null;
            }
            long appliedRows = countNode.asLong();
            if ("DEGRADED".equals(status)) {
                if (appliedRows != 0 || (cursorNode != null && !cursorNode.isNull())) {
                    return null;
                }
            } else if ((appliedRows == 0) != (cursorNode == null || cursorNode.isNull())) {
                // An empty APPLIED page has no cursor; every non-empty page
                // has a cursor.  Do not accept an impossible checkpoint state.
                return null;
            }
            return new Checkpoint(text(root, "operationId"), text(root, "manifestContentSha256"),
                    text(root, "sourceInventorySha256"), text(root, "workloadId"),
                    text(root, "keyId"), status, appliedRows, cursor,
                    updatedNode.asLong());
        } catch (Exception e) {
            return null;
        }
    }

    private static RouteBackfillKey parseCursor(JsonNode cursorNode) {
        if (cursorNode == null || cursorNode.isNull()) {
            return null;
        }
        if (!cursorNode.isObject() || cursorNode.size() != 4
                || !cursorNode.has("tenantId") || !cursorNode.has("siteCode")
                || !cursorNode.has("configVersion")
                || !cursorNode.has("deviceIdentification")) {
            throw new IllegalArgumentException("invalid checkpoint cursor");
        }
        JsonNode configVersion = cursorNode.get("configVersion");
        if (!configVersion.isIntegralNumber() || !configVersion.canConvertToLong()
                || configVersion.asLong() < 0) {
            throw new IllegalArgumentException("invalid checkpoint cursor configVersion");
        }
        return new RouteBackfillKey(text(cursorNode, "tenantId"), text(cursorNode, "siteCode"),
                configVersion.asLong(), text(cursorNode, "deviceIdentification"));
    }

    private static String text(JsonNode root, String field) {
        JsonNode value = root.get(field);
        return value != null && value.isTextual() ? value.textValue() : null;
    }

    private static CheckpointDecision decideCheckpoint(CheckpointPair pair,
                                                       RouteBackfillAuthorization authorization,
                                                       RouteBackfillManifest manifest,
                                                       RouteBackfillApplyRequest request) {
        MetaCheckpoint operationMeta = pair.operation();
        MetaCheckpoint manifestMeta = pair.manifest();
        Checkpoint operation = operationMeta.checkpoint();
        Checkpoint manifestCheckpoint = manifestMeta.checkpoint();

        // The operation index is authoritative for detecting reuse of an
        // operation id.  Evaluate this before comparing the two raw values:
        // a pre-existing manifest index must not turn a same-operation,
        // different-manifest request into a generic checkpoint conflict.
        if (operation != null && authorization.operationId().equals(operation.operationId())
                && !request.manifestArtifact().contentSha256()
                .equals(operation.manifestContentSha256())) {
            return CheckpointDecision.result(new RouteBackfillApplyResult.Rejected(
                    authorization.operationId(), request.manifestArtifact().contentSha256(),
                    SqliteOutboxRouteBackfillApplier.ROUTE_BACKFILL_OPERATION_COLLISION));
        }
        if (operationMeta.present() && manifestMeta.present()
                && !Objects.equals(operationMeta.rawValue(), manifestMeta.rawValue())) {
            return CheckpointDecision.result(new RouteBackfillApplyResult.Degraded(
                    authorization.operationId(), request.manifestArtifact().contentSha256(),
                    ROUTE_BACKFILL_CHECKPOINT_CONFLICT));
        }
        if (operationMeta.present() != manifestMeta.present()
                || (operationMeta.present() && (operation == null || manifestCheckpoint == null))) {
            return CheckpointDecision.result(new RouteBackfillApplyResult.Degraded(
                    authorization.operationId(), request.manifestArtifact().contentSha256(),
                    ROUTE_BACKFILL_CHECKPOINT_CONFLICT));
        }
        if (operation == null) {
            return CheckpointDecision.continueApply();
        }

        if (!sameCheckpoint(operation, manifestCheckpoint)
                || !sameAuthorizationBinding(operation, authorization, manifest,
                request.manifestArtifact().contentSha256())
                || ("APPLIED".equals(operation.status())
                && !sameCursor(operation.nextInventoryCursor(), manifest.inventoryNextCursor()))) {
            return CheckpointDecision.result(new RouteBackfillApplyResult.Degraded(
                    authorization.operationId(), request.manifestArtifact().contentSha256(),
                    ROUTE_BACKFILL_CHECKPOINT_CONFLICT));
        }
        if ("APPLIED".equals(operation.status())) {
            return CheckpointDecision.result(new RouteBackfillApplyResult.AlreadyApplied(
                    authorization.operationId(), request.manifestArtifact().contentSha256(),
                    operation.appliedRowCount(), operation.nextInventoryCursor()));
        }
        return CheckpointDecision.continueApply();
    }

    private static boolean sameCheckpoint(Checkpoint left, Checkpoint right) {
        return left != null && right != null
                && left.operationId().equals(right.operationId())
                && left.manifestContentSha256().equals(right.manifestContentSha256())
                && left.sourceInventorySha256().equals(right.sourceInventorySha256())
                && left.workloadId().equals(right.workloadId())
                && left.keyId().equals(right.keyId())
                && left.status().equals(right.status())
                && left.appliedRowCount() == right.appliedRowCount()
                && left.updatedAtEpochSeconds() == right.updatedAtEpochSeconds()
                && sameCursor(left.nextInventoryCursor(), right.nextInventoryCursor());
    }

    private static boolean sameAuthorizationBinding(Checkpoint checkpoint,
                                                    RouteBackfillAuthorization authorization,
                                                    RouteBackfillManifest manifest,
                                                    String manifestContentSha256) {
        return checkpoint.operationId().equals(authorization.operationId())
                && checkpoint.manifestContentSha256().equals(manifestContentSha256)
                && checkpoint.sourceInventorySha256().equals(manifest.sourceInventorySha256())
                && checkpoint.workloadId().equals(manifest.workloadId())
                && checkpoint.keyId().equals(authorization.keyId());
    }

    private static boolean sameCursor(RouteBackfillKey left, RouteBackfillKey right) {
        return left == null ? right == null : left.equals(right);
    }

    private static long applyPage(Connection connection, RouteBackfillManifest manifest)
            throws SQLException, LocalMismatch {
        long updated = 0L;
        for (RouteBackfillManifestEntry entry : manifest.entries()) {
            RowIdentity identity = inspectRows(connection, entry);
            if (identity.conflictingProduct() != null) {
                throw new LocalMismatch(ROUTE_BACKFILL_LOCAL_IDENTITY_CONFLICT);
            }
            if (identity.nullProductRows() != entry.rowCount()) {
                throw new LocalMismatch(ROUTE_BACKFILL_ROW_COUNT_MISMATCH);
            }
            int affected = updateProduct(connection, entry);
            if ((long) affected != entry.rowCount()) {
                throw new LocalMismatch(ROUTE_BACKFILL_ROW_COUNT_MISMATCH);
            }
            updated = Math.addExact(updated, affected);
        }
        return updated;
    }

    private static RowIdentity inspectRows(Connection connection, RouteBackfillManifestEntry entry)
            throws SQLException {
        String sql = "SELECT product_identification FROM telemetry_outbox"
                + " WHERE tenant_id=? AND site_code=? AND config_version=?"
                + " AND device_identification=?";
        long nullRows = 0L;
        String conflicting = null;
        try (PreparedStatement query = connection.prepareStatement(sql)) {
            bindKey(query, entry.key());
            try (ResultSet rows = query.executeQuery()) {
                while (rows.next()) {
                    String product = rows.getString(1);
                    if (product == null) {
                        nullRows++;
                    } else if (!entry.productIdentification().equals(product)) {
                        conflicting = product;
                    }
                }
            }
        }
        return new RowIdentity(nullRows, conflicting);
    }

    private static int updateProduct(Connection connection, RouteBackfillManifestEntry entry)
            throws SQLException {
        String sql = "UPDATE telemetry_outbox SET product_identification=?"
                + " WHERE tenant_id=? AND site_code=? AND config_version=?"
                + " AND device_identification=? AND product_identification IS NULL";
        try (PreparedStatement update = connection.prepareStatement(sql)) {
            update.setString(1, entry.productIdentification());
            bindKey(update, entry.key(), 2);
            return update.executeUpdate();
        }
    }

    private static void bindKey(PreparedStatement statement, RouteBackfillKey key)
            throws SQLException {
        bindKey(statement, key, 1);
    }

    private static void bindKey(PreparedStatement statement, RouteBackfillKey key, int offset)
            throws SQLException {
        statement.setString(offset, key.tenantId());
        statement.setString(offset + 1, key.siteCode());
        statement.setLong(offset + 2, key.configVersion());
        statement.setString(offset + 3, key.deviceIdentification());
    }

    private RouteBackfillApplyResult writeDegradedCheckpoint(Connection connection,
                                                              String operationKey,
                                                              String manifestKey,
                                                              RouteBackfillAuthorization authorization,
                                                              RouteBackfillManifest manifest,
                                                              String manifestContentSha256,
                                                              String code) {
        try {
            beginImmediate(connection);
            String checkpoint = checkpointJson(authorization, manifest, "DEGRADED", 0L, null);
            writeCheckpoint(connection, operationKey, checkpoint);
            writeCheckpoint(connection, manifestKey, checkpoint);
            commit(connection);
            return new RouteBackfillApplyResult.Degraded(
                    authorization.operationId(),
                    manifestContentSha256, code);
        } catch (SQLException | RuntimeException failure) {
            rollbackQuietly(connection);
            if (failure instanceof RouteBackfillApplyException applyException) {
                throw applyException;
            }
            throw applyFailed("DEGRADED checkpoint transaction failed", failure);
        }
    }

    private static byte[] manifestCanonicalBytes(RouteBackfillManifest manifest) {
        try {
            return JCS.canonicalize(MAPPER.valueToTree(manifest))
                    .getBytes(java.nio.charset.StandardCharsets.UTF_8);
        } catch (RuntimeException e) {
            throw applyFailed("manifest canonicalization failed", e);
        }
    }

    private String checkpointJson(RouteBackfillAuthorization authorization,
                                  RouteBackfillManifest manifest,
                                  String status,
                                  long appliedRows,
                                  RouteBackfillKey cursor) {
        Map<String, Object> fields = new LinkedHashMap<>();
        fields.put("schemaVersion", CHECKPOINT_SCHEMA_VERSION);
        fields.put("operationId", authorization.operationId());
        fields.put("manifestContentSha256", sha256Hex(manifestCanonicalBytes(manifest)));
        fields.put("sourceInventorySha256", manifest.sourceInventorySha256());
        fields.put("workloadId", manifest.workloadId());
        fields.put("keyId", authorization.keyId());
        fields.put("status", status);
        fields.put("appliedRowCount", appliedRows);
        fields.put("nextInventoryCursor", cursor);
        fields.put("updatedAtEpochSeconds", clock.instant().getEpochSecond());
        try {
            return JCS.canonicalize(MAPPER.valueToTree(fields));
        } catch (RuntimeException e) {
            throw applyFailed("checkpoint canonicalization failed", e);
        }
    }

    private static void writeCheckpoint(Connection connection, String key, String value)
            throws SQLException {
        String sql = "INSERT INTO outbox_meta(meta_key, meta_value, updated_at_ms) VALUES(?,?,?)"
                + " ON CONFLICT(meta_key) DO UPDATE SET meta_value=excluded.meta_value,"
                + " updated_at_ms=excluded.updated_at_ms";
        try (PreparedStatement update = connection.prepareStatement(sql)) {
            update.setString(1, key);
            update.setString(2, value);
            update.setLong(3, System.currentTimeMillis());
            update.executeUpdate();
        }
    }

    private static void beginImmediate(Connection connection) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.execute("BEGIN IMMEDIATE");
        }
    }

    private static void commit(Connection connection) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.execute("COMMIT");
        }
    }

    private static void rollbackQuietly(Connection connection) {
        try (Statement statement = connection.createStatement()) {
            statement.execute("ROLLBACK");
        } catch (SQLException ignored) {
            // Preserve the original transactional failure.
        }
    }

    private static boolean isCanonicalUuid(String value) {
        if (value == null || value.length() != 36) {
            return false;
        }
        try {
            return UUID.fromString(value).toString().equals(value);
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    private static boolean isSha256(String value) {
        return value != null && SHA256_HEX.matcher(value).matches();
    }

    private static boolean isKeyId(String value) {
        return value != null && KEY_ID.matcher(value).matches();
    }

    private static String sha256Hex(byte[] value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(value);
            StringBuilder result = new StringBuilder(64);
            for (byte b : digest) {
                result.append(Character.forDigit((b >>> 4) & 0x0f, 16));
                result.append(Character.forDigit(b & 0x0f, 16));
            }
            return result.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("JDK missing SHA-256", e);
        }
    }

    private static RouteBackfillApplyException applyFailed(String detail, Throwable cause) {
        return cause == null
                ? new RouteBackfillApplyException(ROUTE_BACKFILL_APPLY_FAILED + ": " + detail)
                : new RouteBackfillApplyException(ROUTE_BACKFILL_APPLY_FAILED + ": " + detail, cause);
    }

    private record RowIdentity(long nullProductRows, String conflictingProduct) {
    }

    private static final class LocalMismatch extends Exception {
        private final String code;

        private LocalMismatch(String code) {
            this.code = code;
        }

        private String code() {
            return code;
        }
    }

    private record Checkpoint(
            String operationId,
            String manifestContentSha256,
            String sourceInventorySha256,
            String workloadId,
            String keyId,
            String status,
            long appliedRowCount,
            RouteBackfillKey nextInventoryCursor,
            long updatedAtEpochSeconds) {
    }

    private record MetaCheckpoint(boolean present, String rawValue, Checkpoint checkpoint) {
        private static MetaCheckpoint absent() {
            return new MetaCheckpoint(false, null, null);
        }
    }

    private record CheckpointPair(MetaCheckpoint operation, MetaCheckpoint manifest) {
    }

    private record CheckpointDecision(RouteBackfillApplyResult result) {
        private static CheckpointDecision result(RouteBackfillApplyResult result) {
            return new CheckpointDecision(result);
        }

        private static CheckpointDecision continueApply() {
            return new CheckpointDecision(null);
        }
    }

    /** Stable exception prefix used for all local infrastructure failures. */
    public static final class RouteBackfillApplyException extends RuntimeException {
        public RouteBackfillApplyException(String message) {
            super(message);
        }

        public RouteBackfillApplyException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
