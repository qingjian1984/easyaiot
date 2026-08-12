package com.basiclab.iot.sink.outbox;

import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * TD-003 §27-⑤ TDengine 确定性幂等 Spike（Java / taos-jdbcdriver 3.1.0 REST）。
 *
 * <p>验证同 (message_id tag, ts) 重复写 → 物理行=1（upsert 覆盖），不依赖 exactly-once。
 * 补充 CLI Spike 的 Java 驱动层证据（§27-⑤ 要求"驱动版本 + 重复写行为证据"）。
 *
 * <p>用 REST 协议（jdbc:TAOS-RS://）避免原生 taos 客户端库依赖。
 * 若 TDengine 不可达则 assumeTrue 跳过（不视为失败）。
 */
class TDengineIdempotencySpikeTest {

    private static final String URL = "jdbc:TAOS-RS://localhost:6041/?user=root&password=taosdata";

    @Test
    void idempotentUpsertSameMessageIdAndTsOneRow() throws Exception {
        Connection c;
        try {
            c = DriverManager.getConnection(URL);
        } catch (SQLException e) {
            assumeTrue(false, "TDengine 不可达，跳过: " + e.getMessage());
            return;
        }
        try (Statement s = c.createStatement()) {
            s.execute("DROP DATABASE IF EXISTS spike_java");
            s.execute("CREATE DATABASE spike_java");
            s.execute("CREATE STABLE spike_java.telemetry (ts TIMESTAMP, val DOUBLE, content_sha256 NCHAR(64)) "
                    + "TAGS (tenant_id BIGINT, message_id NCHAR(64))");
            // 首次写
            s.execute("INSERT INTO spike_java.d1 USING spike_java.telemetry TAGS(123, 'msg-1') "
                    + "VALUES (1700000000000, 1.0, 'hash-1')");
            // 重投同 (message_id, ts) 不同 val/hash → upsert 覆盖
            s.execute("INSERT INTO spike_java.d1 USING spike_java.telemetry TAGS(123, 'msg-1') "
                    + "VALUES (1700000000000, 2.0, 'hash-2')");
            try (ResultSet rs = s.executeQuery("SELECT count(*) FROM spike_java.telemetry")) {
                rs.next();
                assertEquals(1, rs.getInt(1), "同 message_id+ts 重投必须 1 行（确定性幂等）");
            }
            try (ResultSet rs = s.executeQuery("SELECT val FROM spike_java.d1")) {
                rs.next();
                assertEquals(2.0, rs.getDouble(1), 0.001, "重投覆盖 val（最新写入）");
            }
            System.out.println("[Spike] TDengine idempotent upsert (Java REST): 1 row, val=2.0");
        } finally {
            try (Statement s = c.createStatement()) {
                s.execute("DROP DATABASE IF EXISTS spike_java");
            } catch (SQLException ignore) {
            }
            c.close();
        }
    }
}
