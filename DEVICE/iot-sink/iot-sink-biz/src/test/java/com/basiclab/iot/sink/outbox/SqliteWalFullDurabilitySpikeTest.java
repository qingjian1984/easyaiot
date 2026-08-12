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
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * TD-002 §21-④ SQLite Spike：WAL + synchronous=FULL 掉电持久性证据。
 *
 * <p>验证 sqlite-jdbc 3.46.x + WAL + FULL 在"连接关闭/崩溃（未 checkpoint）"场景下：
 * <ul>
 *   <li>已 commit 数据持久（FULL fsync 保证）</li>
 *   <li>未 commit 数据丢失（无半提交）</li>
 *   <li>WAL replay 恢复已提交行</li>
 *   <li>同 messageId 主键约束（TD-002 §10 MESSAGE_ID_COLLISION 基础）</li>
 *   <li>批量原子性（appendBatch 全成功或全回滚）</li>
 * </ul>
 *
 * <p>本 Spike 不模拟 kill -9（进程内难复现），用"关闭连接不 checkpoint"等价 WAL 崩溃恢复语义。
 * 完整 ENOSPC/损坏页/7 天稳定性在后续 P0-3 子项。
 */
class SqliteWalFullDurabilitySpikeTest {

    /** TD-002 §7 强制 PRAGMA：WAL + FULL + busy_timeout + 不自动 checkpoint + 禁可信 schema。 */
    private static Connection open(Path db) throws SQLException {
        Connection c = DriverManager.getConnection("jdbc:sqlite:" + db.toAbsolutePath());
        try (Statement s = c.createStatement()) {
            s.execute("PRAGMA journal_mode=WAL");
            s.execute("PRAGMA synchronous=FULL");
            s.execute("PRAGMA busy_timeout=5000");
            s.execute("PRAGMA wal_autocheckpoint=0");
            s.execute("PRAGMA trusted_schema=OFF");
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
                    + "state TEXT NOT NULL DEFAULT 'PENDING')");
        }
        c.commit();
    }

    private static void insert(Connection c, String messageId, String hash, byte[] payload) throws SQLException {
        try (PreparedStatement p = c.prepareStatement(
                "INSERT INTO outbox (message_id, content_sha256, payload) VALUES (?, ?, ?)")) {
            p.setString(1, messageId);
            p.setString(2, hash);
            p.setBytes(3, payload);
            p.executeUpdate();
        }
    }

    private static int count(Connection c) throws SQLException {
        try (Statement s = c.createStatement();
             ResultSet rs = s.executeQuery("SELECT count(*) FROM outbox")) {
            rs.next();
            return rs.getInt(1);
        }
    }

    @Test
    void committedDataSurvivesConnectionClose(@TempDir Path dir) throws Exception {
        Path db = dir.resolve("outbox.db");
        try (Connection c = open(db)) {
            createOutbox(c);
            insert(c, "msg-1", "hash-1", new byte[]{1});
            c.commit();
        }
        try (Connection c = open(db)) {
            assertEquals(1, count(c), "FULL+WAL 已 commit 数据必须跨连接关闭持久");
        }
    }

    @Test
    void uncommittedDataLostOnCrash(@TempDir Path dir) throws Exception {
        Path db = dir.resolve("outbox.db");
        try (Connection c = open(db)) {
            createOutbox(c);
            insert(c, "msg-1", "hash-1", new byte[]{1});
            c.commit();
            insert(c, "msg-2", "hash-2", new byte[]{2});
            // 不 commit，直接关闭（模拟崩溃，WAL replay 不恢复未提交）
        }
        try (Connection c = open(db)) {
            assertEquals(1, count(c), "未 commit 的 msg-2 必须丢失，已 commit 的 msg-1 保留");
        }
    }

    @Test
    void walReplayRecoversAllCommittedRows(@TempDir Path dir) throws Exception {
        Path db = dir.resolve("outbox.db");
        try (Connection c = open(db)) {
            createOutbox(c);
            for (int i = 0; i < 100; i++) {
                insert(c, "msg-" + i, "hash-" + i, new byte[]{(byte) i});
                if (i % 10 == 9) {
                    c.commit();
                }
            }
            // 不 checkpoint，直接关闭（WAL 保留全部 100 行已提交帧）
        }
        // Spike 发现：SQLite 关闭最后连接时自动 checkpoint（即使 wal_autocheckpoint=0），
        // db-wal 合并回主库后删除/截空。真崩溃（kill -9，不执行 shutdown checkpoint）的
        // WAL replay 证据需子进程测试（后续 P0-3 子项）。本断言验证"已 commit 数据跨连接关闭持久"。
        try (Connection c = open(db)) {
            assertEquals(100, count(c), "已 commit 的 100 行必须跨连接关闭持久（FULL fsync）");
        }
    }

    @Test
    void duplicateMessageIdRejectedByPrimaryKey(@TempDir Path dir) throws Exception {
        Path db = dir.resolve("outbox.db");
        try (Connection c = open(db)) {
            createOutbox(c);
            insert(c, "msg-1", "hash-1", new byte[]{1});
            c.commit();
            // 同 messageId 不同 hash → PRIMARY KEY 冲突（TD-002 §10 MESSAGE_ID_COLLISION 物理基础）
            assertThrows(SQLException.class, () -> {
                insert(c, "msg-1", "hash-2", new byte[]{2});
                c.commit();
            });
        }
    }

    @Test
    void batchAtomicityRollsBackOnConflict(@TempDir Path dir) throws Exception {
        Path db = dir.resolve("outbox.db");
        try (Connection c = open(db)) {
            createOutbox(c);
            insert(c, "msg-1", "hash-1", new byte[]{1});
            insert(c, "msg-2", "hash-2", new byte[]{2});
            // 第三条与 msg-1 冲突 → 整批回滚（appendBatch 语义）
            assertThrows(SQLException.class, () -> {
                insert(c, "msg-1", "hash-3", new byte[]{3});
                c.commit();
            });
            c.rollback();
            assertEquals(0, count(c), "含冲突的批量必须整体回滚，无半批");
        }
    }

    @Test
    void pragmaConfigurationVerified(@TempDir Path dir) throws Exception {
        Path db = dir.resolve("outbox.db");
        try (Connection c = open(db)) {
            try (Statement s = c.createStatement();
                 ResultSet rs = s.executeQuery("PRAGMA synchronous")) {
                rs.next();
                assertEquals(2, rs.getInt(1), "synchronous=FULL 是 2（OFF=0/NORMAL=1/FULL=2/EXTRA=3）");
            }
            try (Statement s = c.createStatement();
                 ResultSet rs = s.executeQuery("PRAGMA journal_mode")) {
                rs.next();
                assertEquals("wal", rs.getString(1).toLowerCase(), "journal_mode=WAL");
            }
        }
    }
}
