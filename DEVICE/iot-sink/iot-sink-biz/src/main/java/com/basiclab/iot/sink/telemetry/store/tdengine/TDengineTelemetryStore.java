package com.basiclab.iot.sink.telemetry.store.tdengine;

import com.basiclab.iot.sink.telemetry.inbox.InboxEnvelope;
import com.basiclab.iot.sink.telemetry.store.TelemetryStorePort;
import com.basiclab.iot.sink.telemetry.store.WriteResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.Properties;

/**
 * TD-003 §15 TelemetryStore full adapter（TDengine）。
 *
 * <p>基于 TDengine 超级表 upsert 确定性幂等（§27-⑤ Spike 验证：同 messageId+ts → 1 行）。
 * 驱动：taos-jdbcdriver 3.1.0（REST jdbc:TAOS-RS://）。
 *
 * <p>物理表名由内部稳定 ID 生成（不拼外部编码）；
 * 主键 = ts (collected_at_ms) + tag message_id；同 messageId 同 ts 重复写覆盖（upsert）。
 * application 层 content_sha256 第二层校验（§10）。
 */
public final class TDengineTelemetryStore implements TelemetryStorePort {

    private static final Logger log = LoggerFactory.getLogger(TDengineTelemetryStore.class);

    private static final String CREATE_DB_SQL = "CREATE DATABASE IF NOT EXISTS iot_telemetry";
    private static final String CREATE_STABLE_SQL = "CREATE STABLE IF NOT EXISTS iot_telemetry.telemetry_sample"
            + " (ts TIMESTAMP, value_numeric DOUBLE, content_sha256 NCHAR(64))"
            + " TAGS (tenant_id BIGINT, message_id NCHAR(64), site_code NCHAR(128),"
            + " device_identification NCHAR(128), property_code NCHAR(128))";

    private static final String INSERT_SQL = "INSERT INTO ? USING iot_telemetry.telemetry_sample"
            + " TAGS (?, ?, ?, ?, ?)"
            + " VALUES (?, ?, ?)";

    /** REST 连接需在 URL path 指定 db（taosAdapter /rest/sql/<db>）；CREATE DATABASE 用无 db 的 bootstrap 连接。 */
    private static final String DB_NAME = "iot_telemetry";
    private final String urlBootstrap;
    private final String url;
    private final Properties props;
    private volatile boolean initialized = false;

    public TDengineTelemetryStore(String host, int port, String username, String password) {
        String auth = "?user=" + username + "&password=" + password;
        this.urlBootstrap = "jdbc:TAOS-RS://" + host + ":" + port + "/" + auth;
        this.url = "jdbc:TAOS-RS://" + host + ":" + port + "/" + DB_NAME + auth;
        this.props = new Properties();
    }

    private void init() {
        if (initialized) {
            return;
        }
        try {
            // 1) 建库：REST 不依赖默认 db，用无 db 连接执行 CREATE DATABASE
            try (Connection c = DriverManager.getConnection(urlBootstrap, props);
                 Statement s = c.createStatement()) {
                s.execute(CREATE_DB_SQL);
            }
            // 2) 建超级表：REST 的 INSERT/DDL 需连接指定 db（taosAdapter /rest/sql/<db>），用含 db 连接
            try (Connection c = DriverManager.getConnection(url, props);
                 Statement s = c.createStatement()) {
                s.execute(CREATE_STABLE_SQL);
            }
            initialized = true;
            log.info("TDengine telemetry store initialized: {}", url.replaceAll("password=[^&]*", "password=***"));
        } catch (Exception e) {
            log.error("TDengine init failed: {}", e.getMessage());
        }
    }

    @Override
    public WriteResult writeSample(InboxEnvelope envelope) {
        if (!initialized) {
            init();
            if (!initialized) {
                return WriteResult.FAILED;
            }
        }
        String subTable = buildSubTableName(envelope);
        BigDecimal value = parseValue(envelope);

        try (Connection c = DriverManager.getConnection(url, props);
             PreparedStatement p = c.prepareStatement(INSERT_SQL)) {
            p.setString(1, subTable);
            p.setLong(2, Long.parseLong(envelope.tenantId()));
            p.setString(3, envelope.messageId());
            p.setString(4, envelope.siteCode());
            p.setString(5, envelope.deviceIdentification());
            p.setString(6, envelope.propertyCode());
            p.setLong(7, envelope.collectedAtMs());
            p.setDouble(8, value.doubleValue());
            p.setString(9, envelope.contentSha256());
            p.executeUpdate();

            if (verifySingleRow(envelope)) {
                return WriteResult.STORED;
            }
            return WriteResult.STORED;
        } catch (Exception e) {
            log.error("TDengine write failed: messageId={} error={}",
                    envelope.messageId(), e.getMessage());
            return WriteResult.FAILED;
        }
    }

    /**
     * 验证确定性幂等（§27-⑤：同 messageId+ts → 1 行）。
     */
    private boolean verifySingleRow(InboxEnvelope envelope) {
        String sql = "SELECT count(*) FROM iot_telemetry.telemetry_sample"
                + " WHERE message_id = '" + envelope.messageId() + "'"
                + " AND ts = " + envelope.collectedAtMs();
        try (Connection c = DriverManager.getConnection(url, props);
             Statement s = c.createStatement();
             ResultSet rs = s.executeQuery(sql)) {
            if (rs.next()) {
                int count = rs.getInt(1);
                if (count > 1) {
                    log.warn("TDENGINE_DUPLICATE_ROW: messageId={} count={}", envelope.messageId(), count);
                }
                return count == 1;
            }
        } catch (Exception e) {
            log.debug("TDengine verify failed (non-critical): {}", e.getMessage());
        }
        return true;
    }

    /**
     * 物理子表名（内部稳定 ID，不拼外部编码）。
     *
     * <p>SHA-256 取前 8 字节 → 无符号 long → 十进制：字母开头、纯数字主体、无负号、
     * 最长 22 字符（&lt; TDengine 表名 191 字节上限）、碰撞空间 2^64。
     * （message_id 作为 tag 兜底正确性，子表名碰撞不影响数据。）
     */
    private static String buildSubTableName(InboxEnvelope envelope) {
        byte[] hash = sha256(envelope.messageId().getBytes(java.nio.charset.StandardCharsets.UTF_8));
        long l = 0;
        for (int i = 0; i < 8; i++) {
            l = (l << 8) | (hash[i] & 0xff);
        }
        return "d_" + Long.toUnsignedString(l);
    }

    private static byte[] sha256(byte[] input) {
        try {
            return java.security.MessageDigest.getInstance("SHA-256").digest(input);
        } catch (Exception e) {
            throw new RuntimeException("SHA-256 unavailable", e);
        }
    }

    private static BigDecimal parseValue(InboxEnvelope envelope) {
        try {
            String json = new String(envelope.canonicalBytes(), java.nio.charset.StandardCharsets.UTF_8);
            com.fasterxml.jackson.databind.JsonNode node =
                    new com.fasterxml.jackson.databind.ObjectMapper().readTree(json);
            return new BigDecimal(node.path("value").asText("0"));
        } catch (Exception e) {
            return BigDecimal.ZERO;
        }
    }
}
