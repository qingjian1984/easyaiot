package com.basiclab.iot.device.service.event;

import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import javax.sql.DataSource;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.List;
import java.util.Objects;

/**
 * ADR-014：{@link PowerModelInboxRepository} 的 JDBC 实现（power_model_event_inbox）。
 * 首插争抢用 INSERT ... ON CONFLICT (event_id) DO NOTHING，由数据库唯一约束裁决；
 * 隔离 upsert 用 ON CONFLICT DO UPDATE 覆盖两种入口（异 hash 更新既有行 / 未知主版本直接落隔离行）。
 * 表由 ADR-013 受控 runner 落库；本类不创建任何对象。
 */
@Repository
public class JdbcPowerModelInboxRepository implements PowerModelInboxRepository {

    private static final String FIND_BY_EVENT_ID_SQL =
            "SELECT payload_hash, status FROM public.power_model_event_inbox"
                    + " WHERE event_id = CAST(:eventId AS uuid)";

    private static final String INSERT_RECEIVED_SQL =
            "INSERT INTO public.power_model_event_inbox"
                    + " (tenant_id, event_id, event_type, payload_hash, status, received_at)"
                    + " VALUES (:tenantId, CAST(:eventId AS uuid), :eventType, :payloadHash,"
                    + "         'RECEIVED', CURRENT_TIMESTAMP)"
                    + " ON CONFLICT (event_id) DO NOTHING";

    private static final String MARK_PROCESSED_SQL =
            "UPDATE public.power_model_event_inbox"
                    + " SET status = 'PROCESSED', processed_at = :processedAt"
                    + " WHERE event_id = CAST(:eventId AS uuid) AND status = 'RECEIVED'";

    private static final String UPSERT_QUARANTINED_SQL =
            "INSERT INTO public.power_model_event_inbox"
                    + " (tenant_id, event_id, event_type, payload_hash, status,"
                    + "  last_error_code, last_error_digest, received_at)"
                    + " VALUES (:tenantId, CAST(:eventId AS uuid), :eventType, :payloadHash,"
                    + "         'QUARANTINED', :errorCode, :errorDigest, CURRENT_TIMESTAMP)"
                    + " ON CONFLICT (event_id) DO UPDATE"
                    + " SET status = 'QUARANTINED', last_error_code = EXCLUDED.last_error_code,"
                    + "     last_error_digest = EXCLUDED.last_error_digest";

    private static final int MAX_ERROR_DIGEST_LENGTH = 128;
    private static final int MAX_ERROR_CODE_LENGTH = 64;

    private static final RowMapper<InboxArbiter.RecordView> VIEW_MAPPER =
            new RowMapper<InboxArbiter.RecordView>() {
                @Override
                public InboxArbiter.RecordView mapRow(ResultSet rs, int rowNum) throws SQLException {
                    return new InboxArbiter.RecordView(
                            rs.getString("payload_hash"),
                            InboxArbiter.Status.valueOf(rs.getString("status")));
                }
            };

    private final NamedParameterJdbcTemplate jdbc;

    public JdbcPowerModelInboxRepository(DataSource dataSource) {
        this.jdbc = new NamedParameterJdbcTemplate(Objects.requireNonNull(dataSource, "dataSource"));
    }

    @Override
    public InboxArbiter.RecordView findByEventId(String eventId) {
        MapSqlParameterSource params = new MapSqlParameterSource().addValue("eventId", eventId);
        List<InboxArbiter.RecordView> rows = jdbc.query(FIND_BY_EVENT_ID_SQL, params, VIEW_MAPPER);
        return rows.isEmpty() ? null : rows.get(0);
    }

    @Override
    public boolean insertReceived(String eventId, long tenantId, String eventType, String payloadHash) {
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("eventId", eventId)
                .addValue("tenantId", tenantId)
                .addValue("eventType", eventType)
                .addValue("payloadHash", payloadHash);
        return jdbc.update(INSERT_RECEIVED_SQL, params) == 1;
    }

    @Override
    public void markProcessed(String eventId, Instant processedAt) {
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("eventId", eventId)
                .addValue("processedAt", java.sql.Timestamp.from(processedAt));
        jdbc.update(MARK_PROCESSED_SQL, params);
    }

    @Override
    public void upsertQuarantined(String eventId, long tenantId, String eventType,
                                  String payloadHash, String errorCode, String errorDigest) {
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("eventId", eventId)
                .addValue("tenantId", tenantId)
                .addValue("eventType", eventType)
                .addValue("payloadHash", payloadHash)
                .addValue("errorCode", truncate(errorCode, MAX_ERROR_CODE_LENGTH))
                .addValue("errorDigest", truncate(errorDigest, MAX_ERROR_DIGEST_LENGTH));
        jdbc.update(UPSERT_QUARANTINED_SQL, params);
    }

    private static String truncate(String value, int maxLength) {
        if (value == null) {
            return null;
        }
        return value.length() <= maxLength ? value : value.substring(0, maxLength);
    }
}
