package com.basiclab.iot.sink.outbox;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * TD-002 §21-④ SQLite Spike 续：损坏检测（不自动重建）+ dispatch 索引（claim 查询规划）。
 *
 * <p>验证：
 * <ul>
 *   <li>主库中段损坏 → SQLite 检测并报错（不静默重建第二事实源，符合 TD-002 §13 "损坏不自动重建"）</li>
 *   <li>dispatch 索引（state + created_at）被 claim 查询规划器使用（非全表扫描）</li>
 *   <li>message_id PRIMARY KEY 查询走主键索引</li>
 *   <li>健康库 integrity_check = ok</li>
 * </ul>
 *
 * <p>真崩溃（kill -9，不 shutdown checkpoint）的 WAL replay、ENOSPC、候选参数压测在更深子项。
 */
class SqliteCorruptionIndexSpikeTest {

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
            // TD-002 dispatch 索引：claim 查询 WHERE state=? ORDER BY created_at
            s.execute("CREATE INDEX IF NOT EXISTS idx_outbox_dispatch ON outbox(state, created_at)");
        }
        c.commit();
    }

    @Test
    void corruptedDatabaseDetectedNotAutoRebuilt(@TempDir Path dir) throws Exception {
        Path db = dir.resolve("outbox.db");
        try (Connection c = open(db)) {
            createOutbox(c);
            try (PreparedStatement p = c.prepareStatement(
                    "INSERT INTO outbox VALUES (?, ?, ?, ?, ?)")) {
                p.setString(1, "msg-1");
                p.setString(2, "hash-1");
                p.setBytes(3, new byte[]{1, 2, 3, 4, 5, 6, 7, 8});
                p.setString(4, "PENDING");
                p.setLong(5, 1L);
                p.executeUpdate();
            }
            c.commit();
            // 强制 checkpoint 让数据落主库（这样损坏主库才有效）
            try (Statement s = c.createStatement()) {
                s.execute("PRAGMA wal_checkpoint(TRUNCATE)");
            }
        }
        // 损坏主库中段（TD-002 §13 要求检测、不自动重建）
        byte[] data = Files.readAllBytes(db);
        int offset = data.length / 2;
        for (int i = offset; i < offset + 256 && i < data.length; i++) {
            data[i] = (byte) 0xFF;
        }
        Files.write(db, data);

        // 重开：损坏的库必须被检测（报错），不静默重建
        SQLException ex = assertThrows(SQLException.class, () -> {
            try (Connection c = open(db)) {
                try (Statement s = c.createStatement();
                     ResultSet rs = s.executeQuery("SELECT count(*) FROM outbox")) {
                    rs.next();
                }
            }
        }, "损坏主库必须被 SQLite 检测并报错，不得静默重建");
        System.out.println("[Spike] corruption detected: " + ex.getMessage());
    }

    @Test
    void dispatchIndexUsedByClaimQuery(@TempDir Path dir) throws Exception {
        Path db = dir.resolve("outbox.db");
        try (Connection c = open(db)) {
            createOutbox(c);
            try (PreparedStatement p = c.prepareStatement(
                    "INSERT INTO outbox VALUES (?, ?, ?, ?, ?)")) {
                for (int i = 0; i < 2000; i++) {
                    p.setString(1, "msg-" + i);
                    p.setString(2, "hash-" + i);
                    p.setBytes(3, new byte[]{(byte) i});
                    p.setString(4, i < 1000 ? "PENDING" : "ACKED");
                    p.setLong(5, i);
                    p.addBatch();
                    if (i % 200 == 199) {
                        p.executeBatch();
                        c.commit();
                    }
                }
                p.executeBatch();
                c.commit();
            }
            // EXPLAIN：claim 查询（state=PENDING ORDER BY created_at LIMIT）
            String plan;
            try (Statement s = c.createStatement();
                 ResultSet rs = s.executeQuery(
                         "EXPLAIN QUERY PLAN SELECT message_id FROM outbox "
                                 + "WHERE state='PENDING' ORDER BY created_at LIMIT 100")) {
                StringBuilder sb = new StringBuilder();
                while (rs.next()) {
                    sb.append(rs.getString("detail")).append(" | ");
                }
                plan = sb.toString();
            }
            System.out.println("[Spike] claim dispatch plan: " + plan);
            String lower = plan.toLowerCase();
            assertTrue(lower.contains("idx_outbox_dispatch") || lower.contains("search outbox"),
                    "claim 查询应使用 dispatch 索引（非全表扫描），plan: " + plan);
        }
    }

    @Test
    void primaryKeyUsedForMessageIdLookup(@TempDir Path dir) throws Exception {
        Path db = dir.resolve("outbox.db");
        try (Connection c = open(db)) {
            createOutbox(c);
            try (PreparedStatement p = c.prepareStatement(
                    "INSERT INTO outbox VALUES (?, ?, ?, ?, ?)")) {
                for (int i = 0; i < 1000; i++) {
                    p.setString(1, "msg-" + i);
                    p.setString(2, "h" + i);
                    p.setBytes(3, new byte[]{(byte) i});
                    p.setString(4, "PENDING");
                    p.setLong(5, i);
                    p.addBatch();
                    if (i % 200 == 199) {
                        p.executeBatch();
                        c.commit();
                    }
                }
                p.executeBatch();
                c.commit();
            }
            try (Statement s = c.createStatement();
                 ResultSet rs = s.executeQuery(
                         "EXPLAIN QUERY PLAN SELECT * FROM outbox WHERE message_id = 'msg-500'")) {
                rs.next();
                String plan = rs.getString("detail").toLowerCase();
                System.out.println("[Spike] pk lookup plan: " + plan);
                assertTrue(plan.contains("primary key") || plan.contains("search"),
                        "message_id 查询应走 PRIMARY KEY，plan: " + plan);
            }
        }
    }

    @Test
    void integrityCheckOkOnHealthyDb(@TempDir Path dir) throws Exception {
        Path db = dir.resolve("outbox.db");
        try (Connection c = open(db)) {
            createOutbox(c);
            try (Statement s = c.createStatement();
                 ResultSet rs = s.executeQuery("PRAGMA integrity_check")) {
                rs.next();
                assertEquals("ok", rs.getString(1), "健康库 integrity_check 应为 ok");
            }
        }
    }
}
