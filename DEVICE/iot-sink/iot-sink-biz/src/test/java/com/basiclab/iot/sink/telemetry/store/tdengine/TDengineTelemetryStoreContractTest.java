package com.basiclab.iot.sink.telemetry.store.tdengine;

import com.basiclab.iot.sink.telemetry.inbox.InboxEnvelope;
import com.basiclab.iot.sink.telemetry.store.TelemetrySample;
import com.basiclab.iot.sink.telemetry.store.WriteItemResult;
import com.basiclab.iot.sink.telemetry.store.WriteStatus;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * TD-003 §15 TelemetryStore full adapter（TDengine）合同测试（真实 tdengine-server）。
 *
 * <p>验证：① appendBatch 返回 STORED；② 同 messageId 同 ts 重复写为 TDengine 幂等
 * （物理表保持 1 行，而非 2 行）；③ 不同 messageId 各落 1 行。
 * 驱动 taos-jdbcdriver REST（jdbc:TAOS-RS://），凭证默认 root/taosdata。
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class TDengineTelemetryStoreContractTest {

    private static final String HOST = System.getenv().getOrDefault("TDENGINE_HOST", "localhost");
    private static final int PORT = Integer.parseInt(System.getenv().getOrDefault("TDENGINE_PORT", "6041"));
    private static final String USER = System.getenv().getOrDefault("TDENGINE_USER", "root");
    private static final String PASSWORD = System.getenv().getOrDefault("TDENGINE_PASSWORD", "taosdata");
    private static final String REST_URL = "jdbc:TAOS-RS://" + HOST + ":" + PORT + "/?user=" + USER + "&password=" + PASSWORD;

    private TDengineTelemetryStore store;
    private String runPrefix;

    @BeforeAll
    void setup() {
        store = new TDengineTelemetryStore(HOST, PORT, USER, PASSWORD);
        runPrefix = "td-contract-" + Long.toUnsignedString(System.nanoTime(), 36);
    }

    private String id(String suffix) {
        return runPrefix + "-" + suffix;
    }

    private InboxEnvelope env(String msgId, String value, long ts) {
        String json = "{\"schemaVersion\":\"1.0\",\"messageId\":\"" + msgId + "\","
                + "\"tenantId\":\"999888777\",\"siteCode\":\"site-td\","
                + "\"deviceIdentification\":\"dev-td\",\"propertyCode\":\"voltage-a\","
                + "\"value\":\"" + value + "\",\"collectedAt\":\"2026-08-13T00:00:00Z\","
                + "\"source\":\"modbus-rtu\"}";
        byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
        return new InboxEnvelope(msgId, "req-" + msgId, "999888777", "product-td", "site-td",
                "dev-td", "voltage-a", bytes, sha256(bytes), ts, 1, "modbus-rtu", 1);
    }

    @Test
    void appendBatchReturnsStored() {
        WriteItemResult r = write(env(id("msg-1"), "220.5", System.currentTimeMillis()));
        assertEquals(WriteStatus.STORED, r.status(), "首次写入应 STORED");
    }

    @Test
    void duplicateUpsertKeepsSingleRow() {
        long ts = System.currentTimeMillis();
        String messageId = id("msg-2");
        InboxEnvelope e = env(messageId, "221.0", ts);
        assertEquals(WriteStatus.STORED, write(e).status(), "首次写入应 STORED");
        // 同 messageId 同 ts 重复写 → 第二层 hash 校验返回 DUPLICATE，物理表保持 1 行
        assertEquals(WriteStatus.DUPLICATE, write(e).status(), "重复写应 DUPLICATE");
        long count = countRows(messageId, ts);
        assertEquals(1L, count, "同 messageId 同 ts 重复写后物理表应保持 1 行（upsert 幂等）");
    }

    @Test
    void differentMessageIdEachStored() {
        long ts = System.currentTimeMillis();
        String messageId1 = id("msg-3");
        String messageId2 = id("msg-4");
        WriteItemResult r1 = write(env(messageId1, "230.0", ts));
        WriteItemResult r2 = write(env(messageId2, "231.0", ts));
        assertEquals(WriteStatus.STORED, r1.status());
        assertEquals(WriteStatus.STORED, r2.status());
        assertTrue(countRows(messageId1, ts) == 1L, "msg-3 应 1 行");
        assertTrue(countRows(messageId2, ts) == 1L, "msg-4 应 1 行");
    }

    private WriteItemResult write(InboxEnvelope envelope) {
        return store.appendBatch(java.util.List.of(TelemetrySample.fromInboxEnvelope(envelope)))
                .items().get(0);
    }

    /** 直接查 TDengine 物理行数，验证 upsert 确定性幂等（不依赖被测类的 verifySingleRow）。 */
    private long countRows(String messageId, long ts) {
        String sql = "SELECT count(*) FROM iot_telemetry.telemetry_sample"
                + " WHERE message_id = '" + messageId + "' AND ts = " + ts;
        try (Connection c = DriverManager.getConnection(REST_URL, new java.util.Properties());
             Statement s = c.createStatement();
             ResultSet rs = s.executeQuery(sql)) {
            if (rs.next()) {
                return rs.getLong(1);
            }
        } catch (Exception e) {
            throw new AssertionError("countRows 查询失败: " + e.getMessage(), e);
        }
        return -1L;
    }

    private static String sha256(byte[] input) {
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256").digest(input);
            StringBuilder hex = new StringBuilder(hash.length * 2);
            for (byte b : hash) {
                hex.append(Character.forDigit((b >> 4) & 0xF, 16));
                hex.append(Character.forDigit(b & 0xF, 16));
            }
            return hex.toString();
        } catch (Exception e) {
            return "error";
        }
    }
}
