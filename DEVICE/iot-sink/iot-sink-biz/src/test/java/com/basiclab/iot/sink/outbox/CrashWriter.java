package com.basiclab.iot.sink.outbox;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.Statement;

/**
 * TD-002 §21-④ 真崩溃 Spike 子进程：写入 + commit 后 halt(137) 模拟 kill -9。
 *
 * <p>关键：commit 后<b>不关闭连接</b>，直接 {@code Runtime.halt(137)}。
 * halt 不执行 JVM shutdown hook、不触发 SQLite 连接 close（无 shutdown checkpoint），
 * 因此 db-wal 文件保留全部已提交帧，供主测试验证 WAL replay。
 *
 * <p>用法：{@code java -cp <test-classpath> CrashWriter <db-path> [rows]}
 */
public class CrashWriter {
    public static void main(String[] args) throws Exception {
        String dbPath = args[0];
        int rows = args.length > 1 ? Integer.parseInt(args[1]) : 50;
        Connection c = DriverManager.getConnection("jdbc:sqlite:" + dbPath);
        try (Statement s = c.createStatement()) {
            s.execute("PRAGMA journal_mode=WAL");
            s.execute("PRAGMA synchronous=FULL");
            s.execute("PRAGMA wal_autocheckpoint=0");
            s.execute("CREATE TABLE IF NOT EXISTS outbox (message_id TEXT PRIMARY KEY, payload BLOB)");
        }
        c.setAutoCommit(false);
        try (PreparedStatement p = c.prepareStatement("INSERT INTO outbox VALUES (?, ?)")) {
            for (int i = 0; i < rows; i++) {
                p.setString(1, "msg-" + i);
                p.setBytes(2, new byte[]{(byte) i});
                p.executeUpdate();
            }
        }
        c.commit();
        System.out.println("CrashWriter committed " + rows + " rows; halting without close");
        Runtime.getRuntime().halt(137);
    }
}
