package com.basiclab.iot.sink.telemetry.inbox.ack;

import com.basiclab.iot.sink.telemetry.outbox.TelemetryRoute;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import javax.sql.DataSource;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;

/**
 * LC03-03 §5.2/§5.3/§5.4 ACK 派发 PostgreSQL 实现（V012 候选列）。
 *
 * <p>所有 SELECT 只读取已持久 Inbox 行；产品/设备路由不完整或
 * requestId 缺失的行一律不进入发送链（fail-closed，不补猜）。
 * {@code FOR UPDATE SKIP LOCKED} 只用于领取短事务，禁止跨 MQTT
 * publish 持锁；publish 确认由 {@link #markSent} 的条件更新完成。
 */
public final class JdbcTelemetryAckDeliveryRepository implements TelemetryAckDispatchPort {

    /** 只选择路由完整且 requestId 非空的已接收成功行。 */
    private static final String SENDABLE_PREDICATE =
            " request_id IS NOT NULL AND request_id <> ''"
            + " AND product_identification IS NOT NULL AND product_identification <> ''"
            + " AND device_identification IS NOT NULL AND device_identification <> ''";

    private static final String CLAIM_SQL =
            "SELECT tenant_id, message_id_wire, request_id, product_identification,"
            + " device_identification, received_at_ms, ack_sent_at_ms, ack_attempts"
            + " FROM iot_sink.telemetry_inbox"
            + " WHERE ack_sent_at_ms IS NULL AND" + SENDABLE_PREDICATE
            + " ORDER BY received_at_ms ASC, id ASC"
            + " LIMIT ?"
            + " FOR UPDATE SKIP LOCKED";

    private static final String LOAD_SQL =
            "SELECT tenant_id, message_id_wire, request_id, product_identification,"
            + " device_identification, received_at_ms, ack_sent_at_ms, ack_attempts"
            + " FROM iot_sink.telemetry_inbox"
            + " WHERE tenant_id = ? AND message_id = ?";

    private static final String ATTEMPT_INCREMENT_SQL =
            "UPDATE iot_sink.telemetry_inbox"
            + " SET ack_attempts = ack_attempts + 1, updated_at_ms = ?"
            + " WHERE tenant_id = ? AND message_id = ?";

    private static final String MARK_SENT_SQL =
            "UPDATE iot_sink.telemetry_inbox"
            + " SET ack_sent_at_ms = ?, updated_at_ms = ?"
            + " WHERE tenant_id = ? AND message_id = ? AND ack_sent_at_ms IS NULL";

    private static final RowMapper<TelemetryAckDeliveryRow> ROW_MAPPER =
            (rs, rowNum) -> mapRow(rs);

    private final JdbcTemplate jdbc;

    public JdbcTelemetryAckDeliveryRepository(DataSource dataSource) {
        this.jdbc = new JdbcTemplate(dataSource);
    }

    /**
     * 领取一批待发行并逐行递增尝试计数。
     *
     * <p>领取 SELECT（含 {@code FOR UPDATE SKIP LOCKED}）在单条语句的
     * 行锁范围内完成，锁不跨语句、不跨 publish；attempts 递增是领取后
     * 的独立 UPDATE。多实例并发下同一行可能被重复领取——§5.4 明确允许
     * 重复 ACK，由 collector 幂等吸收。
     */
    @Override
    public List<TelemetryAckDeliveryRow> claimPending(int limit) {
        if (limit <= 0) {
            throw new IllegalArgumentException("limit must be positive");
        }
        List<TelemetryAckDeliveryRow> rows = jdbc.query(
                connection -> {
                    PreparedStatement statement = connection.prepareStatement(
                            CLAIM_SQL, ResultSet.TYPE_FORWARD_ONLY, ResultSet.CONCUR_READ_ONLY);
                    statement.setInt(1, limit);
                    return statement;
                },
                ROW_MAPPER);
        long now = System.currentTimeMillis();
        for (TelemetryAckDeliveryRow row : rows) {
            jdbc.update(ATTEMPT_INCREMENT_SQL, now, row.tenantId(), row.messageIdWire());
        }
        return withAttemptsIncremented(rows);
    }

    @Override
    public TelemetryAckDeliveryRow loadForImmediateAck(long tenantId, String messageId) {
        List<TelemetryAckDeliveryRow> rows = jdbc.query(LOAD_SQL, ROW_MAPPER, tenantId, messageId);
        if (rows.isEmpty()) {
            return null;
        }
        TelemetryAckDeliveryRow row = rows.get(0);
        if (!row.isSendable() || row.route() == null) {
            return null;
        }
        long now = System.currentTimeMillis();
        jdbc.update(ATTEMPT_INCREMENT_SQL, now, row.tenantId(), row.messageIdWire());
        return new TelemetryAckDeliveryRow(row.tenantId(), row.messageIdWire(), row.requestId(),
                row.route(), row.receivedAtMs(), row.ackSentAtMs(), row.ackAttempts() + 1);
    }

    @Override
    public boolean markSent(long tenantId, String messageId, long sentAtMs) {
        return jdbc.update(MARK_SENT_SQL, sentAtMs, sentAtMs, tenantId, messageId) > 0;
    }

    private static List<TelemetryAckDeliveryRow> withAttemptsIncremented(
            List<TelemetryAckDeliveryRow> rows) {
        return rows.stream()
                .map(row -> new TelemetryAckDeliveryRow(row.tenantId(), row.messageIdWire(),
                        row.requestId(), row.route(), row.receivedAtMs(), row.ackSentAtMs(),
                        row.ackAttempts() + 1))
                .toList();
    }

    /** 路由列任一为空时返回 null（RowMapper 过滤，发送链随后 fail-closed）。 */
    private static TelemetryAckDeliveryRow mapRow(ResultSet rs) throws SQLException {
        String messageIdWire = rs.getString("message_id_wire");
        if (messageIdWire == null || messageIdWire.isBlank()) {
            return null;
        }
        String product = rs.getString("product_identification");
        String device = rs.getString("device_identification");
        if (product == null || product.isBlank() || device == null || device.isBlank()) {
            return null;
        }
        String requestId = rs.getString("request_id");
        if (requestId == null || requestId.isBlank()) {
            return null;
        }
        Long sentAt = rs.getObject("ack_sent_at_ms") == null ? null : rs.getLong("ack_sent_at_ms");
        return new TelemetryAckDeliveryRow(
                rs.getLong("tenant_id"),
                messageIdWire,
                requestId,
                new TelemetryRoute(product, device),
                rs.getLong("received_at_ms"),
                sentAt,
                rs.getInt("ack_attempts"));
    }
}
