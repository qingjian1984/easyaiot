package com.basiclab.iot.sink.outbox;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * TD-002 §21-④ 候选参数压测 Spike：大规模写入吞吐 + 查询性能 + incremental vacuum。
 *
 * <p>候选参数（TD-002 §8.3，待压测冻结）：4096 命令/16MiB 队列、500 envelope/批、vacuum 10000/20%。
 * 完整压测（7 天连续 + 多场景 + 真实硬件）需真实环境长跑，本 Spike 提供 JUnit 内初步可行性证据。
 *
 * <p>ENOSPC（磁盘满错误处理）需 Docker 小卷/物理小分区，标 OPEN（特殊环境）。
 */
class SqliteLoadSpikeTest {

    private static Connection open(Path db) throws SQLException {
        Connection c = DriverManager.getConnection("jdbc:sqlite:" + db.toAbsolutePath());
        try (Statement s = c.createStatement()) {
            s.execute("PRAGMA journal_mode=WAL");
            s.execute("PRAGMA synchronous=FULL");
            s.execute("PRAGMA busy_timeout=5000");
            s.execute("PRAGMA wal_autocheckpoint=0");
        }
        c.setAutoCommit(false);
        return c;
    }

    private static void createOutbox(Connection c) throws SQLException {
        try (Statement s = c.createStatement()) {
            s.execute("CREATE TABLE IF NOT EXISTS outbox ("
                    + "message_id TEXT PRIMARY KEY,"
                    + "content_sha256 TEXT NOT NULL,"
                    + "payload BLOB NOT NULL,"
                    + "state TEXT NOT NULL DEFAULT 'PENDING',"
                    + "created_at INTEGER NOT NULL)");
            s.execute("CREATE INDEX IF NOT EXISTS idx_outbox_dispatch ON outbox(state, created_at)");
        }
        c.commit();
    }

    @Test
    void appendBatchThroughputAtCandidateBatchSize(@TempDir Path dir) throws Exception {
        Path db = dir.resolve("load.db");
        int total = 10_000;
        int batch = 500;
        long start = System.nanoTime();
        try (Connection c = open(db)) {
            createOutbox(c);
            try (PreparedStatement p = c.prepareStatement(
                    "INSERT INTO outbox VALUES (?, ?, ?, ?, ?)")) {
                for (int i = 0; i < total; i++) {
                    p.setString(1, "msg-" + i);
                    p.setString(2, "sha256-" + i);
                    p.setBytes(3, new byte[]{(byte) i, (byte) (i >> 8)});
                    p.setString(4, i % 5 == 0 ? "ACKED" : "PENDING");
                    p.setLong(5, i);
                    p.addBatch();
                    if (i % batch == batch - 1) {
                        p.executeBatch();
                        c.commit();
                    }
                }
            }
        }
        double seconds = (System.nanoTime() - start) / 1_000_000_000.0;
        double throughput = total / seconds;
        System.out.printf("[Spike] appendBatch %d rows (batch=%d): %.2fs, %.0f rows/s%n",
                total, batch, seconds, throughput);
        assertTrue(throughput > 1000,
                "候选参数(500/批) appendBatch 吞吐应 >1000 rows/s, 实际 " + throughput);
    }

    @Test
    void queryLatencyUnderLoad(@TempDir Path dir) throws Exception {
        Path db = dir.resolve("load.db");
        try (Connection c = open(db)) {
            createOutbox(c);
            try (PreparedStatement p = c.prepareStatement(
                    "INSERT INTO outbox VALUES (?, ?, ?, ?, ?)")) {
                for (int i = 0; i < 10_000; i++) {
                    p.setString(1, "msg-" + i);
                    p.setString(2, "h" + i);
                    p.setBytes(3, new byte[]{(byte) i});
                    p.setString(4, "PENDING");
                    p.setLong(5, i);
                    p.addBatch();
                    if (i % 500 == 499) {
                        p.executeBatch();
                        c.commit();
                    }
                }
            }
            long t1 = System.nanoTime();
            try (PreparedStatement q = c.prepareStatement("SELECT * FROM outbox WHERE message_id = ?")) {
                q.setString(1, "msg-9999");
                try (ResultSet rs = q.executeQuery()) {
                    assertTrue(rs.next());
                }
            }
            long pkMs = (System.nanoTime() - t1) / 1_000_000;
            long t2 = System.nanoTime();
            try (Statement s = c.createStatement();
                 ResultSet rs = s.executeQuery(
                         "SELECT message_id FROM outbox WHERE state='PENDING' ORDER BY created_at LIMIT 100")) {
                int n = 0;
                while (rs.next()) {
                    n++;
                }
                assertEquals(100, n);
            }
            long claimMs = (System.nanoTime() - t2) / 1_000_000;
            System.out.printf("[Spike] query (10k rows): PK lookup %dms, dispatch claim %dms%n", pkMs, claimMs);
            assertTrue(pkMs < 100, "PK lookup 应 <100ms, 实际 " + pkMs);
            assertTrue(claimMs < 200, "dispatch claim 应 <200ms, 实际 " + claimMs);
        }
    }

    @Test
    void incrementalVacuumReclaimsPages(@TempDir Path dir) throws Exception {
        Path db = dir.resolve("vacuum.db");
        try (Connection c = open(db)) {
            try (Statement s = c.createStatement()) {
                s.execute("PRAGMA auto_vacuum=INCREMENTAL");
            }
            createOutbox(c);
            try (PreparedStatement p = c.prepareStatement("INSERT INTO outbox VALUES (?, ?, ?, ?, ?)")) {
                for (int i = 0; i < 5000; i++) {
                    p.setString(1, "msg-" + i);
                    p.setString(2, "h" + i);
                    p.setBytes(3, new byte[]{(byte) i});
                    p.setString(4, "ACKED");
                    p.setLong(5, i);
                    p.addBatch();
                    if (i % 500 == 499) {
                        p.executeBatch();
                        c.commit();
                    }
                }
            }
            try (Statement s = c.createStatement()) {
                s.execute("DELETE FROM outbox WHERE state='ACKED'");
            }
            c.commit();
            long before = pageCount(c);
            try (Statement s = c.createStatement()) {
                s.execute("PRAGMA incremental_vacuum(100)");
            }
            c.commit();
            long after = pageCount(c);
            System.out.printf("[Spike] incremental_vacuum: pages %d -> %d%n", before, after);
            assertTrue(after <= before, "incremental_vacuum 不应增加页数");
        }
    }

    private static long pageCount(Connection c) throws SQLException {
        try (Statement s = c.createStatement();
             ResultSet rs = s.executeQuery("PRAGMA page_count")) {
            rs.next();
            return rs.getLong(1);
        }
    }
}
