package com.basiclab.iot.sink.outbox;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * TD-002 §21-④ 真崩溃 WAL replay Spike：子进程 halt(137)（模拟 kill -9，不执行 shutdown
 * checkpoint）后，WAL 文件保留，重开时 SQLite replay 恢复全部已提交数据。
 *
 * <p>这是 §21-④ 最严格的掉电证据（区别于核心 Spike 的"关闭连接"语义——关闭会触发 shutdown checkpoint）。
 */
class CrashReplayTest {

    @Test
    void walReplayRecoversCommittedAfterHardKill(@TempDir Path dir) throws Exception {
        Path db = dir.resolve("crash.db");
        String cp = System.getProperty("java.class.path");
        String javaBin = Path.of(System.getProperty("java.home"), "bin", "java").toString();
        ProcessBuilder pb = new ProcessBuilder(javaBin, "-cp", cp,
                "com.basiclab.iot.sink.outbox.CrashWriter",
                db.toAbsolutePath().toString(), "50");
        pb.redirectErrorStream(true);
        Process proc = pb.start();
        int code = proc.waitFor();
        String out = new String(proc.getInputStream().readAllBytes()).trim();
        System.out.println("[Spike] CrashWriter exit=" + code + " out='" + out + "'");
        assertEquals(137, code, "CrashWriter 应以 137 退出（halt 模拟 SIGKILL）");

        assertTrue(Files.exists(db.resolveSibling("crash.db-wal")),
                "真崩溃后 WAL 文件必须存在（halt 不执行 shutdown checkpoint）");

        // 重开：SQLite 自动 replay WAL，恢复全部已提交
        try (Connection c = DriverManager.getConnection("jdbc:sqlite:" + db.toAbsolutePath());
             Statement s = c.createStatement();
             ResultSet rs = s.executeQuery("SELECT count(*) FROM outbox")) {
            rs.next();
            assertEquals(50, rs.getInt(1),
                    "WAL replay 必须恢复全部 50 行已提交数据（真崩溃 kill -9 场景）");
        }
    }
}
