package com.basiclab.iot.sink.outbox.sqlite;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * TD-002 §7 DDL + §8 PRAGMA migration（V3：产品路由身份 + claim/ACK 状态机 + gap 表）。
 *
 * <p>V1/V2→V3 补列兼容：新库 CREATE TABLE 直接含全部列；已存在库通过 ALTER TABLE ADD COLUMN 补列。
 */
public final class SqliteOutboxMigration {

    private SqliteOutboxMigration() {
    }

    public static final int USER_VERSION = 3;

    public static void migrate(Path dbPath) throws SQLException {
        try (Connection c = DriverManager.getConnection("jdbc:sqlite:" + dbPath.toAbsolutePath());
             Statement s = c.createStatement()) {
            applyPragmas(s);
            createTelemetryOutbox(s);
            migrateV1toV2(s);
            migrateV2toV3(s);
            createTelemetryGap(s);
            createOutboxMeta(s);
            rebuildIndexes(s);
            s.execute("PRAGMA user_version = " + USER_VERSION);
        }
    }

    public static void applyPragmas(Statement s) throws SQLException {
        s.execute("PRAGMA journal_mode=WAL");
        s.execute("PRAGMA synchronous=FULL");
        s.execute("PRAGMA busy_timeout=5000");
        s.execute("PRAGMA wal_autocheckpoint=0");
        s.execute("PRAGMA trusted_schema=OFF");
    }

    /** telemetry_outbox V3（含产品路由身份与 claim/ACK 全列）。 */
    private static void createTelemetryOutbox(Statement s) throws SQLException {
        s.execute("CREATE TABLE IF NOT EXISTS telemetry_outbox ("
                + "id INTEGER PRIMARY KEY AUTOINCREMENT,"
                + "message_id TEXT NOT NULL UNIQUE,"
                + "request_id TEXT NOT NULL,"
                + "tenant_id TEXT NOT NULL,"
                + "site_code TEXT NOT NULL,"
                + "product_identification TEXT,"
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
    }

    /** V1→V2 补列（已存在列 ALTER 报错安全忽略）。 */
    private static void migrateV1toV2(Statement s) {
        String[] v2Columns = {
            "delivery_class TEXT NOT NULL DEFAULT 'REALTIME'",
            "attempts INTEGER NOT NULL DEFAULT 0",
            "unknown_ack_count INTEGER NOT NULL DEFAULT 0",
            "next_retry_at_ms INTEGER",
            "in_flight_at_ms INTEGER",
            "ack_deadline_at_ms INTEGER",
            "acked_at_ms INTEGER",
            "last_error_code TEXT",
            "last_error_detail TEXT"
        };
        for (String col : v2Columns) {
            try {
                s.execute("ALTER TABLE telemetry_outbox ADD COLUMN " + col);
            } catch (SQLException ignored) {
                // 列已存在（V2 新库或已迁移）
            }
        }
    }

    /** V2→V3 additive product identity column; historical rows intentionally remain NULL. */
    private static void migrateV2toV3(Statement s) {
        try {
            s.execute("ALTER TABLE telemetry_outbox ADD COLUMN product_identification TEXT");
        } catch (SQLException ignored) {
            // Column already exists (fresh V3 database or a repeated migration).
        }
    }

    /** §7 telemetry_gap（DEAD_LETTER 同事务 gap 写入）。 */
    private static void createTelemetryGap(Statement s) throws SQLException {
        s.execute("CREATE TABLE IF NOT EXISTS telemetry_gap ("
                + "id INTEGER PRIMARY KEY AUTOINCREMENT,"
                + "message_id TEXT NOT NULL,"
                + "tenant_id TEXT NOT NULL,"
                + "site_code TEXT NOT NULL,"
                + "device_identification TEXT NOT NULL,"
                + "property_code TEXT NOT NULL,"
                + "stage TEXT NOT NULL CHECK (stage IN ('EDGE_DELIVERY','CENTER_PROJECTION')),"
                + "reason_code TEXT NOT NULL,"
                + "gap_first_seen_ms INTEGER NOT NULL,"
                + "gap_last_seen_ms INTEGER NOT NULL,"
                + "created_at_ms INTEGER NOT NULL"
                + ") STRICT");
    }

    /** §7 outbox_meta。 */
    private static void createOutboxMeta(Statement s) throws SQLException {
        s.execute("CREATE TABLE IF NOT EXISTS outbox_meta ("
                + "meta_key TEXT PRIMARY KEY,"
                + "meta_value TEXT,"
                + "updated_at_ms INTEGER"
                + ") STRICT");
    }

    /** V2 索引重建（drop old V1 dispatch + create 6 列 dispatch + inflight + cleanup + sequence）。 */
    private static void rebuildIndexes(Statement s) throws SQLException {
        try {
            s.execute("DROP INDEX IF EXISTS idx_outbox_dispatch");
        } catch (SQLException ignored) {
        }
        s.execute("CREATE INDEX IF NOT EXISTS idx_outbox_dispatch "
                + "ON telemetry_outbox(status, delivery_class, priority_rank, next_retry_at_ms, created_at_ms, id)");
        s.execute("CREATE INDEX IF NOT EXISTS idx_outbox_inflight "
                + "ON telemetry_outbox(status, ack_deadline_at_ms)");
        s.execute("CREATE INDEX IF NOT EXISTS idx_outbox_cleanup "
                + "ON telemetry_outbox(status, acked_at_ms, id)");
        s.execute("CREATE INDEX IF NOT EXISTS idx_outbox_sequence "
                + "ON telemetry_outbox(tenant_id, device_identification, property_code, sequence_no)");
    }
}
