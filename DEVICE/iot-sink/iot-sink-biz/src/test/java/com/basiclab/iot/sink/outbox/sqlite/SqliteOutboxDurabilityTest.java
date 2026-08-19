package com.basiclab.iot.sink.outbox.sqlite;

import com.basiclab.iot.sink.telemetry.envelope.DataPriority;
import com.basiclab.iot.sink.telemetry.envelope.EnvelopeCanonicalCodec;
import com.basiclab.iot.sink.telemetry.envelope.TelemetryEnvelope;
import com.basiclab.iot.sink.telemetry.envelope.TelemetryQuality;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.time.Duration;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * TD-002 §8 持久性合同：commit 后数据持久 + user_version + integrity_check。
 * 复用 P0-3 Spike 验证的 WAL+FULL 持久语义。
 */
class SqliteOutboxDurabilityTest {

    @TempDir
    Path dir;
    private SqliteTelemetryOutbox outbox;

    @BeforeEach
    void setup() throws Exception {
        Path db = dir.resolve("outbox.db");
        SqliteOutboxMigration.migrate(db);
        outbox = new SqliteTelemetryOutbox(db, new EnvelopeCanonicalCodec(), 100);
    }

    @AfterEach
    void teardown() {
        if (outbox != null) {
            outbox.shutdown();
        }
    }

    private TelemetryEnvelope env(String msgId) {
        return new TelemetryEnvelope(
                TelemetryEnvelope.SCHEMA_VERSION,
                TelemetryEnvelope.CANONICALIZATION_VERSION,
                msgId, "req-" + msgId,
                "123", "site-1", "dev-1", "voltage-a", "220.5",
                TelemetryEnvelope.VALUE_ENCODING_DECIMAL_STRING,
                TelemetryQuality.GOOD, DataPriority.NORMAL_TELEMETRY,
                "2026-08-12T00:00:00Z", "2026-08-12T00:00:00Z",
                1, "modbus-rtu", 1
        );
    }

    @Test
    void committedDataPersistAfterReopen(@TempDir Path dir2) throws Exception {
        outbox.appendBatch(List.of(env("msg-1"), env("msg-2")), Duration.ofSeconds(5));
        outbox.shutdown();
        outbox = null;

        Path db = dir.resolve("outbox.db");
        try (Connection c = DriverManager.getConnection("jdbc:sqlite:" + db.toAbsolutePath());
             Statement s = c.createStatement()) {
            s.execute("PRAGMA journal_mode=WAL");
            try (ResultSet rs = s.executeQuery("SELECT count(*) FROM telemetry_outbox")) {
                rs.next();
                assertEquals(2, rs.getInt(1), "committed 2 rows persist after reopen");
            }
        }
    }

    @Test
    void userVersionMatchesMigration() throws Exception {
        outbox.shutdown();
        outbox = null;
        Path db = dir.resolve("outbox.db");
        try (Connection c = DriverManager.getConnection("jdbc:sqlite:" + db.toAbsolutePath());
             Statement s = c.createStatement()) {
            try (ResultSet rs = s.executeQuery("PRAGMA user_version")) {
                rs.next();
                assertEquals(SqliteOutboxMigration.USER_VERSION, rs.getInt(1),
                        "user_version = SqliteOutboxMigration.USER_VERSION");
            }
        }
    }

    @Test
    void integrityCheckOk() throws Exception {
        outbox.appendBatch(List.of(env("msg-1")), Duration.ofSeconds(5));
        outbox.shutdown();
        outbox = null;
        Path db = dir.resolve("outbox.db");
        try (Connection c = DriverManager.getConnection("jdbc:sqlite:" + db.toAbsolutePath());
             Statement s = c.createStatement()) {
            try (ResultSet rs = s.executeQuery("PRAGMA integrity_check")) {
                rs.next();
                assertEquals("ok", rs.getString(1), "integrity_check = ok");
            }
        }
    }
}
