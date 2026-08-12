package com.basiclab.iot.sink.outbox.sqlite;

import com.basiclab.iot.sink.telemetry.envelope.EnvelopeCanonicalCodec;
import com.basiclab.iot.sink.telemetry.envelope.TelemetryEnvelope;
import com.basiclab.iot.sink.telemetry.outbox.AppendBatchResult;
import com.basiclab.iot.sink.telemetry.outbox.OutboxUnavailableException;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

/**
 * TD-002 §8 单 writer 线程（长生命周期 Connection + 有界命令队列消费）。
 *
 * <p>所有写操作（appendBatch/Ack/Claim/Cleanup）串行化在此线程；P1-T1 只实现 appendBatch。
 * 复用 P0-3 Spike 验证的 §8 PRAGMA（WAL/FULL/busy_timeout/wal_autocheckpoint/trusted_schema）。
 *
 * <p>appendBatch 语义（§9）：
 * <ul>
 *   <li>同 messageId 同 hash → DUPLICATE（跳过，返回）</li>
 *   <li>同 messageId 不同 hash → MESSAGE_ID_COLLISION（整批回滚，无半批）</li>
 *   <li>新 messageId → STORED（INSERT）</li>
 *   <li>空批次不提交</li>
 * </ul>
 */
final class SqliteOutboxWriter extends Thread {

    private static final String INSERT_SQL = "INSERT INTO telemetry_outbox"
            + "(message_id, request_id, tenant_id, site_code, device_identification,"
            + " property_code, sequence_no, collected_at_ms, data_priority, priority_rank,"
            + " envelope, content_sha256, envelope_size, status, created_at_ms, updated_at_ms, config_version)"
            + " VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)";

    private static final String SELECT_HASH_SQL = "SELECT content_sha256 FROM telemetry_outbox WHERE message_id = ?";

    private final Path dbPath;
    private final EnvelopeCanonicalCodec codec;
    private final OutboxCommandQueue queue;
    private volatile boolean running = true;
    private Connection connection;

    SqliteOutboxWriter(Path dbPath, EnvelopeCanonicalCodec codec, OutboxCommandQueue queue) {
        super("sqlite-outbox-writer");
        setDaemon(true);
        this.dbPath = dbPath;
        this.codec = codec;
        this.queue = queue;
    }

    @Override
    public void run() {
        try {
            connection = DriverManager.getConnection("jdbc:sqlite:" + dbPath.toAbsolutePath());
            try (Statement s = connection.createStatement()) {
                SqliteOutboxMigration.applyPragmas(s);
            }
            connection.setAutoCommit(false);
            while (running) {
                try {
                    OutboxCommand cmd = queue.take();
                    if (cmd instanceof OutboxCommand.AppendBatch ab) {
                        ab.future().complete(executeAppendBatch(ab.envelopes()));
                    }
                } catch (InterruptedException e) {
                    if (!running) {
                        break;
                    }
                }
            }
        } catch (SQLException e) {
            throw new OutboxUnavailableException("writer connection failed", e);
        } finally {
            if (connection != null) {
                try {
                    connection.close();
                } catch (SQLException ignore) {
                }
            }
        }
    }

    void shutdown() {
        running = false;
        interrupt();
    }

    private AppendBatchResult executeAppendBatch(List<TelemetryEnvelope> envelopes) {
        if (envelopes.isEmpty()) {
            return new AppendBatchResult.Success(List.of(), List.of());
        }
        List<String> stored = new ArrayList<>();
        List<String> duplicates = new ArrayList<>();
        try {
            for (TelemetryEnvelope env : envelopes) {
                EnvelopeCanonicalCodec.CanonicalEnvelope canonical = codec.canonicalize(env);
                byte[] bytes = canonical.canonicalBytes();
                long now = System.currentTimeMillis();
                String existingHash = queryExistingHash(env.messageId());
                if (existingHash != null) {
                    if (existingHash.equals(canonical.contentSha256())) {
                        duplicates.add(env.messageId());
                        continue;
                    }
                    // MESSAGE_ID_COLLISION — 整批回滚（无半批，TD-002 §9）
                    connection.rollback();
                    return new AppendBatchResult.Success(List.of(), List.of());
                }
                insertEnvelope(env, bytes, canonical.contentSha256(), now);
                stored.add(env.messageId());
            }
            connection.commit();
            return new AppendBatchResult.Success(stored, duplicates);
        } catch (SQLException e) {
            try {
                connection.rollback();
            } catch (SQLException ignore) {
            }
            throw new OutboxUnavailableException("appendBatch failed", e);
        }
    }

    private String queryExistingHash(String messageId) throws SQLException {
        try (PreparedStatement q = connection.prepareStatement(SELECT_HASH_SQL)) {
            q.setString(1, messageId);
            try (ResultSet rs = q.executeQuery()) {
                return rs.next() ? rs.getString(1) : null;
            }
        }
    }

    private void insertEnvelope(TelemetryEnvelope env, byte[] bytes, String sha256, long now) throws SQLException {
        try (PreparedStatement p = connection.prepareStatement(INSERT_SQL)) {
            p.setString(1, env.messageId());
            p.setString(2, env.requestId());
            p.setString(3, env.tenantId());
            p.setString(4, env.siteCode());
            p.setString(5, env.deviceIdentification());
            p.setString(6, env.propertyCode());
            p.setLong(7, env.sequence());
            p.setLong(8, now);
            p.setString(9, env.dataPriority().name());
            p.setInt(10, env.dataPriority().rank());
            p.setBytes(11, bytes);
            p.setString(12, sha256);
            p.setInt(13, bytes.length);
            p.setString(14, "PENDING");
            p.setLong(15, now);
            p.setLong(16, now);
            p.setLong(17, env.configVersion());
            p.executeUpdate();
        }
    }
}
