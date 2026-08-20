package com.basiclab.iot.sink.outbox.sqlite;

import com.basiclab.iot.sink.outbox.backoff.FullJitterBackoff;
import com.basiclab.iot.sink.telemetry.envelope.EnvelopeCanonicalCodec;
import com.basiclab.iot.sink.telemetry.envelope.TelemetryEnvelope;
import com.basiclab.iot.sink.telemetry.outbox.AckCommand;
import com.basiclab.iot.sink.telemetry.outbox.AckResultCode;
import com.basiclab.iot.sink.telemetry.outbox.AppendBatchResult;
import com.basiclab.iot.sink.telemetry.outbox.ClaimBatchResult;
import com.basiclab.iot.sink.telemetry.outbox.ClaimedEnvelope;
import com.basiclab.iot.sink.telemetry.outbox.OutboxUnavailableException;
import com.basiclab.iot.sink.telemetry.outbox.TelemetryOutboxBatch;

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
 * TD-002 §8 单 writer 线程。所有写 SQL 串行化在此线程。
 * P1-T1: appendBatch；P1-T4: claim/applyAck/reclaimExpiredLeases。
 */
final class SqliteOutboxWriter extends Thread {

    private static final String INSERT_SQL = "INSERT INTO telemetry_outbox"
            + "(message_id, request_id, tenant_id, site_code, product_identification, device_identification,"
            + " property_code, sequence_no, collected_at_ms, data_priority, priority_rank,"
            + " envelope, content_sha256, envelope_size, status, delivery_class,"
            + " created_at_ms, updated_at_ms, config_version)"
            + " VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)";

    private static final String SELECT_EXISTING_SQL =
            "SELECT content_sha256, product_identification FROM telemetry_outbox WHERE message_id = ?";

    private static final String CLAIM_SELECT_SQL = "SELECT id, message_id, envelope, content_sha256,"
            + " tenant_id, site_code, device_identification, property_code"
            + " FROM telemetry_outbox"
            + " WHERE status = 'PENDING'"
            + " AND product_identification IS NOT NULL"
            + " AND (next_retry_at_ms IS NULL OR next_retry_at_ms <= ?)"
            + " ORDER BY priority_rank ASC, delivery_class ASC, created_at_ms ASC, id ASC"
            + " LIMIT ?";

    private static final String CLAIM_UPDATE_SQL = "UPDATE telemetry_outbox"
            + " SET status = 'IN_FLIGHT', attempts = attempts + 1,"
            + " in_flight_at_ms = ?, ack_deadline_at_ms = ?, updated_at_ms = ?"
            + " WHERE id = ? AND status = 'PENDING'";

    private static final String RECLAIM_SQL = "UPDATE telemetry_outbox"
            + " SET status = 'PENDING', next_retry_at_ms = ?,"
            + " in_flight_at_ms = NULL, ack_deadline_at_ms = NULL, updated_at_ms = ?"
            + " WHERE status = 'IN_FLIGHT' AND ack_deadline_at_ms <= ?";

    private static final String ACK_SELECT_SQL = "SELECT id, status, unknown_ack_count"
            + " FROM telemetry_outbox WHERE message_id = ?";

    private static final int UNKNOWN_ACK_THRESHOLD = 12;
    private static final long BACKOFF_BASE_MS = 1000L;
    private static final long BACKOFF_CAP_MS = 1_800_000L;

    private final Path dbPath;
    private final EnvelopeCanonicalCodec codec;
    private final OutboxCommandQueue queue;
    private final FullJitterBackoff backoff = new FullJitterBackoff(BACKOFF_BASE_MS, BACKOFF_CAP_MS);
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
                        ab.future().complete(executeAppendBatch(ab.batch()));
                    } else if (cmd instanceof OutboxCommand.Claim c) {
                        c.future().complete(executeClaim(c.maxCount(), c.leaseMs()));
                    } else if (cmd instanceof OutboxCommand.ApplyAck aa) {
                        executeApplyAck(aa.ack());
                    } else if (cmd instanceof OutboxCommand.ReclaimExpiredLeases r) {
                        executeReclaimExpiredLeases(r.nowMs());
                    } else if (cmd instanceof OutboxCommand.CleanupAcked ca) {
                        executeCleanupAcked(ca.keepBeforeMs(), ca.batchSize());
                    } else if (cmd instanceof OutboxCommand.Checkpoint cp) {
                        executeCheckpoint();
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

    // ==================== appendBatch (P1-T1) ====================

    private AppendBatchResult executeAppendBatch(TelemetryOutboxBatch batch) {
        List<TelemetryEnvelope> envelopes = batch.envelopes();
        List<String> stored = new ArrayList<>();
        List<String> duplicates = new ArrayList<>();
        try {
            for (TelemetryEnvelope env : envelopes) {
                EnvelopeCanonicalCodec.CanonicalEnvelope canonical = codec.canonicalize(env);
                byte[] bytes = canonical.canonicalBytes();
                long now = System.currentTimeMillis();
                ExistingEnvelope existing = queryExisting(env.messageId());
                if (existing != null) {
                    if (existing.contentSha256().equals(canonical.contentSha256())
                            && batch.productIdentification().equals(existing.productIdentification())) {
                        duplicates.add(env.messageId());
                        continue;
                    }
                    connection.rollback();
                    return new AppendBatchResult.Collision(List.of(env.messageId()));
                }
                insertEnvelope(batch.productIdentification(), env, bytes, canonical.contentSha256(), now);
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

    private ExistingEnvelope queryExisting(String messageId) throws SQLException {
        try (PreparedStatement q = connection.prepareStatement(SELECT_EXISTING_SQL)) {
            q.setString(1, messageId);
            try (ResultSet rs = q.executeQuery()) {
                return rs.next() ? new ExistingEnvelope(rs.getString(1), rs.getString(2)) : null;
            }
        }
    }

    private void insertEnvelope(String productIdentification, TelemetryEnvelope env, byte[] bytes,
                                String sha256, long now) throws SQLException {
        try (PreparedStatement p = connection.prepareStatement(INSERT_SQL)) {
            p.setString(1, env.messageId());
            p.setString(2, env.requestId());
            p.setString(3, env.tenantId());
            p.setString(4, env.siteCode());
            p.setString(5, productIdentification);
            p.setString(6, env.deviceIdentification());
            p.setString(7, env.propertyCode());
            p.setLong(8, env.sequence());
            p.setLong(9, now);
            p.setString(10, env.dataPriority().name());
            p.setInt(11, env.dataPriority().rank());
            p.setBytes(12, bytes);
            p.setString(13, sha256);
            p.setInt(14, bytes.length);
            p.setString(15, "PENDING");
            p.setString(16, "REALTIME");
            p.setLong(17, now);
            p.setLong(18, now);
            p.setLong(19, env.configVersion());
            p.executeUpdate();
        }
    }

    // ==================== claim (P1-T4 §11) ====================

    private ClaimBatchResult executeClaim(int maxCount, long leaseMs) {
        long now = System.currentTimeMillis();
        long deadline = now + leaseMs;
        List<ClaimedEnvelope> claimed = new ArrayList<>();
        try {
            List<Long> ids = new ArrayList<>();
            try (PreparedStatement sel = connection.prepareStatement(CLAIM_SELECT_SQL)) {
                sel.setLong(1, now);
                sel.setInt(2, maxCount);
                try (ResultSet rs = sel.executeQuery()) {
                    while (rs.next()) {
                        long id = rs.getLong(1);
                        String topic = buildTopic(rs.getString(6), rs.getString(8));
                        claimed.add(new ClaimedEnvelope(
                                id, rs.getString(2), rs.getBytes(3), rs.getString(4),
                                rs.getString(5), rs.getString(6), rs.getString(7),
                                rs.getString(8), topic));
                        ids.add(id);
                    }
                }
            }
            if (ids.isEmpty()) {
                connection.commit();
                return new ClaimBatchResult.Empty();
            }
            try (PreparedStatement upd = connection.prepareStatement(CLAIM_UPDATE_SQL)) {
                for (Long id : ids) {
                    upd.setLong(1, now);
                    upd.setLong(2, deadline);
                    upd.setLong(3, now);
                    upd.setLong(4, id);
                    upd.addBatch();
                }
                upd.executeBatch();
            }
            connection.commit();
            return new ClaimBatchResult.Claimed(claimed);
        } catch (SQLException e) {
            try {
                connection.rollback();
            } catch (SQLException ignore) {
            }
            throw new OutboxUnavailableException("claim failed", e);
        }
    }

    private static String buildTopic(String siteCode, String propertyCode) {
        return "/telemetry/" + siteCode + "/" + propertyCode;
    }

    // ==================== applyAck (P1-T4 §10 状态机) ====================

    private void executeApplyAck(AckCommand ack) {
        try {
            long now = System.currentTimeMillis();
            long[] rowInfo = queryAckRow(ack.messageId());
            if (rowInfo == null) {
                connection.commit();
                return;
            }
            long rowId = rowInfo[0];
            String currentStatus = rowInfo[1] == 1 ? "IN_FLIGHT" : rowInfo[1] == 2 ? "ACKED" : rowInfo[1] == 3 ? "DEAD_LETTER" : "PENDING";
            int unknownCount = (int) rowInfo[2];

            if ("DEAD_LETTER".equals(currentStatus)) {
                connection.commit();
                return;
            }

            AckResultCode code = ack.resultCode();
            if (code == AckResultCode.ACCEPTED_DURABLE || code == AckResultCode.DUPLICATE) {
                updateStatus(rowId, "ACKED", now, null, null, null, 0);
            } else if (code == AckResultCode.REJECTED_RETRYABLE) {
                long delay = backoff.nextDelayMs(queryAttempts(rowId));
                updateStatus(rowId, "PENDING", now, now + delay, ack.errorCode(), null, 0);
            } else if (code == AckResultCode.REJECTED_FINAL) {
                insertGap(ack, now);
                updateStatus(rowId, "DEAD_LETTER", now, null, ack.errorCode(), null, 0);
            } else {
                int newCount = unknownCount + 1;
                if (newCount >= UNKNOWN_ACK_THRESHOLD) {
                    insertGap(ack, now);
                    updateStatus(rowId, "DEAD_LETTER", now, null, "UNKNOWN_ACK_LIMIT", null, newCount);
                } else {
                    long delay = backoff.nextDelayMs(queryAttempts(rowId));
                    updateStatus(rowId, "PENDING", now, now + delay, "UNKNOWN_ACK", null, newCount);
                }
            }
            connection.commit();
        } catch (SQLException e) {
            try {
                connection.rollback();
            } catch (SQLException ignore) {
            }
            throw new OutboxUnavailableException("applyAck failed", e);
        }
    }

    private long[] queryAckRow(String messageId) throws SQLException {
        try (PreparedStatement q = connection.prepareStatement(ACK_SELECT_SQL)) {
            q.setString(1, messageId);
            try (ResultSet rs = q.executeQuery()) {
                if (!rs.next()) {
                    return null;
                }
                long id = rs.getLong(1);
                String status = rs.getString(2);
                int unknown = rs.getInt(3);
                int statusCode = "PENDING".equals(status) ? 0 : "IN_FLIGHT".equals(status) ? 1
                        : "ACKED".equals(status) ? 2 : "DEAD_LETTER".equals(status) ? 3 : 0;
                return new long[]{id, statusCode, unknown};
            }
        }
    }

    private int queryAttempts(long rowId) throws SQLException {
        try (PreparedStatement q = connection.prepareStatement(
                "SELECT attempts FROM telemetry_outbox WHERE id = ?")) {
            q.setLong(1, rowId);
            try (ResultSet rs = q.executeQuery()) {
                return rs.next() ? rs.getInt(1) : 0;
            }
        }
    }

    private void updateStatus(long rowId, String status, long now,
                              Long nextRetryMs, String errorCode, String errorDetail,
                              Integer unknownAckCount) throws SQLException {
        String sql = "UPDATE telemetry_outbox SET status = ?, updated_at_ms = ?";
        if (nextRetryMs != null) {
            sql += ", next_retry_at_ms = " + nextRetryMs;
        }
        if ("ACKED".equals(status)) {
            sql += ", acked_at_ms = " + now;
        }
        if ("PENDING".equals(status)) {
            sql += ", in_flight_at_ms = NULL, ack_deadline_at_ms = NULL";
        }
        if (errorCode != null) {
            sql += ", last_error_code = '" + errorCode.replace("'", "''") + "'";
        }
        if (unknownAckCount != null) {
            sql += ", unknown_ack_count = " + unknownAckCount;
        }
        sql += " WHERE id = ?";
        try (PreparedStatement p = connection.prepareStatement(sql)) {
            p.setString(1, status);
            p.setLong(2, now);
            p.setLong(3, rowId);
            p.executeUpdate();
        }
    }

    private void insertGap(AckCommand ack, long now) throws SQLException {
        try (PreparedStatement p = connection.prepareStatement(
                "INSERT INTO telemetry_gap"
                + "(message_id, tenant_id, site_code, device_identification, property_code,"
                + " stage, reason_code, gap_first_seen_ms, gap_last_seen_ms, created_at_ms)"
                + " SELECT message_id, tenant_id, site_code, device_identification, property_code,"
                + " 'EDGE_DELIVERY', ?, ?, ?, ?"
                + " FROM telemetry_outbox WHERE message_id = ?")) {
            p.setString(1, ack.errorCode() != null ? ack.errorCode() : "UNKNOWN");
            p.setLong(2, now);
            p.setLong(3, now);
            p.setLong(4, now);
            p.setString(5, ack.messageId());
            p.executeUpdate();
        }
    }

    // ==================== reclaimExpiredLeases (P1-T4 §10) ====================

    private void executeReclaimExpiredLeases(long nowMs) {
        try {
            int reclaimed;
            try (PreparedStatement p = connection.prepareStatement(RECLAIM_SQL)) {
                long jitter = backoff.nextDelayMs(1);
                p.setLong(1, nowMs + jitter);
                p.setLong(2, nowMs);
                p.setLong(3, nowMs);
                reclaimed = p.executeUpdate();
            }
            connection.commit();
        } catch (SQLException e) {
            try {
                connection.rollback();
            } catch (SQLException ignore) {
            }
        }
    }

    // ==================== cleanupAcked (P1-T5 §13) ====================

    private void executeCleanupAcked(long keepBeforeMs, int batchSize) {
        try {
            try (PreparedStatement p = connection.prepareStatement(
                    "DELETE FROM telemetry_outbox WHERE id IN ("
                    + "SELECT id FROM telemetry_outbox "
                    + "WHERE status = 'ACKED' AND created_at_ms < ? "
                    + "ORDER BY id ASC LIMIT ?)")) {
                p.setLong(1, keepBeforeMs);
                p.setInt(2, batchSize);
                p.executeUpdate();
            }
            connection.commit();
        } catch (SQLException e) {
            try {
                connection.rollback();
            } catch (SQLException ignore) {
            }
        }
    }

    // ==================== checkpoint (P1-T5 §13) ====================

    private void executeCheckpoint() {
        try {
            try (Statement s = connection.createStatement()) {
                s.execute("PRAGMA wal_checkpoint(PASSIVE)");
            }
            connection.commit();
        } catch (SQLException e) {
            try {
                connection.rollback();
            } catch (SQLException ignore) {
            }
        }
    }

    private record ExistingEnvelope(String contentSha256, String productIdentification) {
    }
}
