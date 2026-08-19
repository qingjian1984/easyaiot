package com.basiclab.iot.sink.telemetry.store.tdengine;

import com.basiclab.iot.sink.telemetry.store.TelemetryStorePort;
import com.basiclab.iot.sink.telemetry.store.TelemetrySample;
import com.basiclab.iot.sink.telemetry.store.TelemetryValueCodec;
import com.basiclab.iot.sink.telemetry.store.WriteBatchResult;
import com.basiclab.iot.sink.telemetry.store.WriteItemResult;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.Properties;
import java.util.List;

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
    private static final ObjectMapper JSON = new ObjectMapper();

    private static final String CREATE_DB_SQL = "CREATE DATABASE IF NOT EXISTS iot_telemetry";
    private static final String CREATE_STABLE_SQL = "CREATE STABLE IF NOT EXISTS iot_telemetry.telemetry_sample"
            + " (ts TIMESTAMP, value_numeric DOUBLE, content_sha256 NCHAR(64))"
            + " TAGS (tenant_id BIGINT, message_id NCHAR(64), site_code NCHAR(128),"
            + " device_identification NCHAR(128), property_code NCHAR(128))";


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
            log.info("TDENGINE_INIT: code=STORE_AVAILABLE");
        } catch (Exception e) {
            log.warn("TDENGINE_INIT: code=STORE_UNAVAILABLE");
        }
    }

    @Override
    public WriteBatchResult appendBatch(java.util.List<TelemetrySample> samples) {
        if (samples == null) {
            throw new IllegalArgumentException("samples are required");
        }
        if (samples.isEmpty()) {
            return WriteBatchResult.empty();
        }
        if (samples.size() > 500) {
            return new WriteBatchResult(samples.stream()
                    .map(sample -> identifiable(sample)
                            ? WriteItemResult.finalFailed(sample.messageId(), "STORE_BATCH_TOO_LARGE")
                            : WriteItemResult.finalFailed(sample == null ? null : sample.messageId(),
                            "STORE_SAMPLE_INVALID"))
                    .toList());
        }
        java.util.List<WriteItemResult> results = new java.util.ArrayList<>(samples.size());
        for (TelemetrySample sample : samples) {
            results.add(writeOne(sample));
        }
        return new WriteBatchResult(results);
    }

    private WriteItemResult writeOne(TelemetrySample sample) {
        String messageId = sample == null ? null : sample.messageId();
        if (sample == null || !sample.isValid()) {
            return WriteItemResult.finalFailed(messageId, "STORE_SAMPLE_INVALID");
        }
        final long tenantId;
        try {
            tenantId = Long.parseLong(sample.tenantId());
        } catch (NumberFormatException error) {
            return WriteItemResult.finalFailed(messageId, "STORE_SAMPLE_INVALID");
        }
        if (valueIsDeterministicallyInvalid(sample.canonicalBytes())) {
            return WriteItemResult.finalFailed(messageId, "STORE_VALUE_INVALID");
        }
        if (!initialized) {
            init();
            if (!initialized) {
                return WriteItemResult.retryable(messageId, "STORE_UNAVAILABLE");
            }
        }
        List<String> existingHashes;
        try {
            existingHashes = existingHashes(sample, tenantId);
        } catch (Exception error) {
            log.warn("TDENGINE_WRITE_FAILED: messageId={} code=STORE_UNAVAILABLE", messageId);
            return WriteItemResult.retryable(messageId, "STORE_UNAVAILABLE");
        }
        WriteItemResult existingResult = classifyExisting(sample, existingHashes);
        if (existingResult != null) {
            return existingResult;
        }
        String subTable = buildSubTableName(sample);
        BigDecimal value = TelemetryValueCodec.parseDecimalValue(sample.canonicalBytes());
        String insertSql = "INSERT INTO " + subTable + " USING iot_telemetry.telemetry_sample"
                + " TAGS (?, ?, ?, ?, ?) VALUES (?, ?, ?)";

        try (Connection c = DriverManager.getConnection(url, props);
             PreparedStatement p = c.prepareStatement(insertSql)) {
            p.setLong(1, tenantId);
            p.setString(2, sample.messageId());
            p.setString(3, sample.siteCode());
            p.setString(4, sample.deviceIdentification());
            p.setString(5, sample.propertyCode());
            p.setLong(6, sample.collectedAtMs());
            // TDengine DOUBLE 列强制非空（引擎不支持 NULL；spike 验证 CREATE STABLE ... DOUBLE NULL → syntax error）。
            // value 缺失（无效质量，TD-003 §6 可省略 value）时回退 0.0 + 告警：PG 侧正确写 NULL，此处的 0 是引擎妥协。
            if (value == null) {
                p.setDouble(7, 0.0);
            } else {
                p.setDouble(7, value.doubleValue());
            }
            p.setString(8, sample.contentSha256());
            p.executeUpdate();
            return WriteItemResult.stored(messageId);
        } catch (Exception e) {
            log.warn("TDengine write failed: messageId={} code=STORE_UNAVAILABLE", messageId);
            return WriteItemResult.retryable(messageId, "STORE_UNAVAILABLE");
        }
    }

    private List<String> existingHashes(TelemetrySample sample, long tenantId) throws Exception {
        String sql = "SELECT content_sha256 FROM iot_telemetry.telemetry_sample"
                + " WHERE tenant_id = ? AND message_id = ?";
        try (Connection c = DriverManager.getConnection(url, props);
             PreparedStatement p = c.prepareStatement(sql)) {
            p.setLong(1, tenantId);
            p.setString(2, sample.messageId());
            try (ResultSet rs = p.executeQuery()) {
                List<String> hashes = new java.util.ArrayList<>();
                while (rs.next()) {
                    String hash = rs.getString(1);
                    if (!hashes.contains(hash)) {
                        hashes.add(hash);
                    }
                }
                return hashes;
            }
        }
    }

    private static boolean identifiable(TelemetrySample sample) {
        return sample != null && sample.messageId() != null && !sample.messageId().isBlank();
    }

    private static WriteItemResult classifyExisting(TelemetrySample sample, List<String> hashes) {
        if (hashes.size() > 1) {
            return WriteItemResult.finalFailed(sample.messageId(), "STORE_STATE_CORRUPT");
        }
        if (hashes.size() == 1) {
            return sample.contentSha256().equals(hashes.get(0))
                    ? WriteItemResult.duplicate(sample.messageId())
                    : WriteItemResult.finalFailed(sample.messageId(), "MESSAGE_ID_COLLISION");
        }
        return null;
    }

    /** Missing/null value is a valid quality omission; present but non-decimal value is final invalid input. */
    private static boolean valueIsDeterministicallyInvalid(byte[] canonicalBytes) {
        try {
            JsonNode root = JSON.readTree(canonicalBytes);
            if (root == null || !root.isObject()) {
                return true;
            }
            JsonNode value = root.get("value");
            if (value == null || value.isNull()) {
                return false;
            }
            if (!value.isTextual() && !value.isNumber()) {
                return true;
            }
            new BigDecimal(value.asText());
            return false;
        } catch (Exception error) {
            return true;
        }
    }

    /**
     * 物理子表名（内部稳定 ID，不拼外部编码）。
     *
     * <p>SHA-256 取前 8 字节 → 无符号 long → 十进制：字母开头、纯数字主体、无负号、
     * 最长 22 字符（&lt; TDengine 表名 191 字节上限）、碰撞空间 2^64。
     * （message_id 作为 tag 兜底正确性，子表名碰撞不影响数据。）
     */
    private static String buildSubTableName(TelemetrySample sample) {
        byte[] hash = sha256(sample.messageId().getBytes(java.nio.charset.StandardCharsets.UTF_8));
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
}
