package com.basiclab.iot.sink.outbox.sqlite;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * TD-002 §7 DDL + §8 PRAGMA migration。
 *
 * <p>复用 P0-3 Spike 验证的 PRAGMA 套件（WAL/FULL/busy_timeout/wal_autocheckpoint/trusted_schema）。
 * STRICT 表 + dispatch 索引（claim 查询规划，Spike 已证 SEARCH USING INDEX）。
 */
public final class SqliteOutboxMigration {

    private SqliteOutboxMigration() {
    }

    /** Schema 版本（PRAGMA user_version）。 */
    public static final int USER_VERSION = 1;

    /** 应用 §8 PRAGMA（每次连接）+ 建表（首次）+ user_version。 */
    public static void migrate(Path dbPath) throws SQLException {
        try (Connection c = DriverManager.getConnection("jdbc:sqlite:" + dbPath.toAbsolutePath());
             Statement s = c.createStatement()) {
            applyPragmas(s);
            createTelemetryOutbox(s);
            createOutboxMeta(s);
            createIndexes(s);
            s.execute("PRAGMA user_version = " + USER_VERSION);
        }
    }

    /** §8 PRAGMA（WAL + FULL + busy_timeout + 不自动 checkpoint + 禁可信 schema）。 */
    public static void applyPragmas(Statement s) throws SQLException {
        s.execute("PRAGMA journal_mode=WAL");
        s.execute("PRAGMA synchronous=FULL");
        s.execute("PRAGMA busy_timeout=5000");
        s.execute("PRAGMA wal_autocheckpoint=0");
        s.execute("PRAGMA trusted_schema=OFF");
    }

    /** §7 telemetry_outbox（STRICT，核心列；M1 不含 gap 表，留给 T-5）。 */
    private static void createTelemetryOutbox(Statement s) throws SQLException {
        s.execute("CREATE TABLE IF NOT EXISTS telemetry_outbox ("
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
                + "created_at_ms INTEGER NOT NULL,"
                + "updated_at_ms INTEGER NOT NULL,"
                + "config_version INTEGER NOT NULL CHECK (config_version >= 0)"
                + ") STRICT");
    }

    /** §7 outbox_meta（键值元数据）。 */
    private static void createOutboxMeta(Statement s) throws SQLException {
        s.execute("CREATE TABLE IF NOT EXISTS outbox_meta ("
                + "meta_key TEXT PRIMARY KEY,"
                + "meta_value TEXT,"
                + "updated_at_ms INTEGER"
                + ") STRICT");
    }

    /** §7 索引（dispatch claim + cleanup + sequence）。 */
    private static void createIndexes(Statement s) throws SQLException {
        s.execute("CREATE INDEX IF NOT EXISTS idx_outbox_dispatch "
                + "ON telemetry_outbox(status, priority_rank, created_at_ms, id)");
        s.execute("CREATE INDEX IF NOT EXISTS idx_outbox_cleanup "
                + "ON telemetry_outbox(status, created_at_ms, id)");
        s.execute("CREATE INDEX IF NOT EXISTS idx_outbox_sequence "
                + "ON telemetry_outbox(tenant_id, device_identification, property_code, sequence_no)");
    }
}
