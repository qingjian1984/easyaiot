package com.basiclab.iot.sink.outbox.sqlite;

import com.basiclab.iot.sink.telemetry.outbox.backfill.RouteBackfillKey;
import com.basiclab.iot.sink.telemetry.outbox.backfill.RouteInventoryArtifact;
import com.basiclab.iot.sink.telemetry.outbox.backfill.RouteInventoryEntry;
import com.basiclab.iot.sink.telemetry.outbox.backfill.RouteInventoryPage;
import org.sqlite.Collation;
import org.sqlite.SQLiteConfig;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

/**
 * Read-only inventory view for historical outbox route identities.
 * This class deliberately has no write-capable database operation.
 */
public final class SqliteOutboxRouteInventoryExporter {

    public static final String INPUT_INVALID = "ROUTE_INVENTORY_INPUT_INVALID";
    public static final String DB_NOT_FOUND = "ROUTE_INVENTORY_DB_NOT_FOUND";
    public static final String SCHEMA_UNSUPPORTED = "ROUTE_INVENTORY_SCHEMA_UNSUPPORTED";
    public static final String READ_FAILED = "ROUTE_INVENTORY_READ_FAILED";

    private static final int MAX_LIMIT = RouteInventoryPage.MAX_ENTRIES;
    private static final String UTF16_COLLATION = "ROUTE_INVENTORY_UTF16";

    /**
     * Exports at most {@code limit} grouped historical keys after the cursor.
     */
    public RouteInventoryArtifact exportPage(Path dbPath, String workloadId,
                                             RouteBackfillKey afterExclusive, int limit) {
        validateInput(dbPath, workloadId, limit);
        if (!Files.exists(dbPath) || !Files.isRegularFile(dbPath) || !Files.isReadable(dbPath)) {
            throw failure(DB_NOT_FOUND, "database file is not an ordinary readable file: " + dbPath);
        }

        try (Connection connection = openReadOnly(dbPath)) {
            connection.setAutoCommit(false);
            verifySchema(connection);
            List<RouteInventoryEntry> entries = readPage(connection, afterExclusive, limit + 1);
            boolean hasMore = entries.size() > limit;
            if (hasMore) {
                entries = new ArrayList<>(entries.subList(0, limit));
            }
            RouteBackfillKey nextCursor = hasMore
                    ? entries.get(entries.size() - 1).key()
                    : null;
            RouteInventoryPage page = new RouteInventoryPage(
                    RouteInventoryPage.SCHEMA_VERSION,
                    RouteInventoryPage.CANONICALIZATION_VERSION,
                    workloadId,
                    entries,
                    nextCursor);
            connection.commit();
            return new RouteInventoryArtifact(page);
        } catch (RouteInventoryException e) {
            throw e;
        } catch (SQLException | RuntimeException e) {
            throw failure(READ_FAILED, "read-only inventory query failed", e);
        }
    }

    private static Connection openReadOnly(Path dbPath) throws SQLException {
        SQLiteConfig config = new SQLiteConfig();
        config.setReadOnly(true);
        config.setExplicitReadOnly(true);
        Connection connection = config.createConnection("jdbc:sqlite:" + dbPath.toAbsolutePath());
        try {
            Collation.create(connection, UTF16_COLLATION, new Collation() {
                @Override
                protected int xCompare(String left, String right) {
                    return left.compareTo(right);
                }
            });
            return connection;
        } catch (SQLException | RuntimeException e) {
            try {
                connection.close();
            } catch (SQLException closeFailure) {
                e.addSuppressed(closeFailure);
            }
            throw e;
        }
    }

    private static void validateInput(Path dbPath, String workloadId, int limit) {
        if (dbPath == null || workloadId == null || workloadId.isBlank()
                || limit < 1 || limit > MAX_LIMIT) {
            throw failure(INPUT_INVALID, "dbPath, workloadId, and limit are invalid");
        }
    }

    private static void verifySchema(Connection connection) throws SQLException {
        try (Statement statement = connection.createStatement();
             ResultSet version = statement.executeQuery("PRAGMA user_version")) {
            if (!version.next() || version.getInt(1) != 3) {
                throw failure(SCHEMA_UNSUPPORTED, "SQLite user_version must be 3");
            }
        }

        boolean hasProductIdentity = false;
        try (Statement statement = connection.createStatement();
             ResultSet columns = statement.executeQuery("PRAGMA table_info(telemetry_outbox)")) {
            while (columns.next()) {
                if ("product_identification".equals(columns.getString("name"))) {
                    hasProductIdentity = true;
                    break;
                }
            }
        }
        if (!hasProductIdentity) {
            throw failure(SCHEMA_UNSUPPORTED, "telemetry_outbox route identity column is absent");
        }
    }

    private static List<RouteInventoryEntry> readPage(Connection connection,
                                                       RouteBackfillKey afterExclusive,
                                                       int readLimit) throws SQLException {
        String tenantId = collated("tenant_id");
        String siteCode = collated("site_code");
        String deviceIdentification = collated("device_identification");
        StringBuilder sql = new StringBuilder(
                "SELECT tenant_id, site_code, config_version, device_identification, COUNT(*) "
                        + "FROM telemetry_outbox WHERE product_identification IS NULL");
        if (afterExclusive != null) {
            sql.append(" AND (").append(tenantId).append(" > ? OR (").append(tenantId)
                    .append(" = ? AND (").append(siteCode).append(" > ? OR (")
                    .append(siteCode).append(" = ? AND (config_version > ? OR ")
                    .append("(config_version = ? AND ").append(deviceIdentification)
                    .append(" > ?))))) )");
        }
        sql.append(" GROUP BY ").append(tenantId).append(", ").append(siteCode)
                .append(", config_version, ").append(deviceIdentification)
                .append(" ORDER BY ").append(tenantId).append(" ASC, ")
                .append(siteCode).append(" ASC, config_version ASC, ")
                .append(deviceIdentification).append(" ASC")
                .append(" LIMIT ?");

        List<RouteInventoryEntry> entries = new ArrayList<>();
        try (PreparedStatement query = connection.prepareStatement(sql.toString())) {
            int parameter = 1;
            if (afterExclusive != null) {
                query.setString(parameter++, afterExclusive.tenantId());
                query.setString(parameter++, afterExclusive.tenantId());
                query.setString(parameter++, afterExclusive.siteCode());
                query.setString(parameter++, afterExclusive.siteCode());
                query.setLong(parameter++, afterExclusive.configVersion());
                query.setLong(parameter++, afterExclusive.configVersion());
                query.setString(parameter++, afterExclusive.deviceIdentification());
            }
            query.setInt(parameter, readLimit);
            try (ResultSet rows = query.executeQuery()) {
                while (rows.next()) {
                    RouteBackfillKey key = new RouteBackfillKey(
                            rows.getString(1), rows.getString(2), rows.getLong(3), rows.getString(4));
                    long rowCount = rows.getLong(5);
                    if (rows.wasNull()) {
                        throw failure(READ_FAILED, "group row count is null");
                    }
                    entries.add(new RouteInventoryEntry(key, rowCount));
                }
            }
        }
        return entries;
    }

    private static String collated(String column) {
        return column + " COLLATE " + UTF16_COLLATION;
    }

    private static RouteInventoryException failure(String code, String detail) {
        return new RouteInventoryException(code, detail);
    }

    private static RouteInventoryException failure(String code, String detail, Throwable cause) {
        return new RouteInventoryException(code, detail, cause);
    }

    /** Stable failure code for callers and operational diagnostics. */
    public static final class RouteInventoryException extends RuntimeException {
        private final String code;

        public RouteInventoryException(String code, String detail) {
            super(code + ": " + detail);
            this.code = code;
        }

        public RouteInventoryException(String code, String detail, Throwable cause) {
            super(code + ": " + detail, cause);
            this.code = code;
        }

        public String code() {
            return code;
        }

        public String errorCode() {
            return code;
        }
    }
}
