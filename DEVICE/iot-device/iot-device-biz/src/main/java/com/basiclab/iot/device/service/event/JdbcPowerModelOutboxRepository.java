package com.basiclab.iot.device.service.event;

import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import javax.sql.DataSource;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Objects;

/**
 * ADR-014：{@link PowerModelOutboxRepository} 的 JDBC 实现（power_model_release_outbox）。
 * claim 为单语句原子认领：子查询 FOR UPDATE SKIP LOCKED 选到期行，
 * 外层 UPDATE 置 PUBLISHING + 租约并 RETURNING，并发副本不会认领到同一条目（OUT-001/003）。
 * 表由 ADR-013 受控 runner 落库（V001）；本类不创建任何对象。
 */
@Repository
public class JdbcPowerModelOutboxRepository implements PowerModelOutboxRepository {

    private static final String INSERT_PENDING_SQL =
            "INSERT INTO public.power_model_release_outbox ("
                    + " id, event_id, tenant_id, audit_event_id, aggregate_type, aggregate_id,"
                    + " event_type, schema_version, payload, payload_hash, status,"
                    + " retry_count, max_retries, next_attempt_at, created_at, updated_at)"
                    + " VALUES (:id, CAST(:eventId AS uuid), :tenantId, CAST(:auditEventId AS uuid),"
                    + " :aggregateType, :aggregateId, :eventType, :schemaVersion,"
                    + " CAST(:payload AS jsonb), :payloadHash, 'PENDING',"
                    + " 0, :maxRetries, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)";

    private static final String CLAIM_DUE_SQL =
            "UPDATE public.power_model_release_outbox o"
                    + " SET status = 'PUBLISHING', lease_owner = :leaseOwner,"
                    + "     lease_until = :leaseUntil, updated_at = CURRENT_TIMESTAMP"
                    + " WHERE o.id IN ("
                    + "   SELECT c.id FROM public.power_model_release_outbox c"
                    + "   WHERE (c.status = 'PENDING' AND c.next_attempt_at <= :now)"
                    + "      OR (c.status = 'PUBLISHING' AND (c.lease_until IS NULL OR c.lease_until <= :now))"
                    + "   ORDER BY c.created_at, c.id"
                    + "   LIMIT :batchSize"
                    + "   FOR UPDATE SKIP LOCKED"
                    + " )"
                    + " RETURNING o.event_id, o.tenant_id, o.aggregate_type, o.aggregate_id,"
                    + "          o.event_type, o.schema_version, o.payload::text AS payload_text,"
                    + "          o.retry_count, o.max_retries";

    private static final String MARK_PUBLISHED_SQL =
            "UPDATE public.power_model_release_outbox"
                    + " SET status = 'PUBLISHED', published_at = :publishedAt,"
                    + "     lease_owner = NULL, lease_until = NULL, updated_at = CURRENT_TIMESTAMP"
                    + " WHERE event_id = CAST(:eventId AS uuid) AND status = 'PUBLISHING'";

    private static final String MARK_RETRY_SQL =
            "UPDATE public.power_model_release_outbox"
                    + " SET status = 'PENDING', retry_count = :retryCount,"
                    + "     next_attempt_at = :nextAttemptAt,"
                    + "     last_error_code = :errorCode, last_error_digest = :errorDigest,"
                    + "     lease_owner = NULL, lease_until = NULL, updated_at = CURRENT_TIMESTAMP"
                    + " WHERE event_id = CAST(:eventId AS uuid) AND status = 'PUBLISHING'";

    private static final String MARK_DEAD_LETTER_SQL =
            "UPDATE public.power_model_release_outbox"
                    + " SET status = 'DEAD_LETTER',"
                    + "     last_error_code = :errorCode, last_error_digest = :errorDigest,"
                    + "     lease_owner = NULL, lease_until = NULL, updated_at = CURRENT_TIMESTAMP"
                    + " WHERE event_id = CAST(:eventId AS uuid) AND status = 'PUBLISHING'";

    private static final int MAX_ERROR_DIGEST_LENGTH = 128;
    private static final int MAX_ERROR_CODE_LENGTH = 64;

    private static final RowMapper<ClaimedOutboxEntry> CLAIMED_MAPPER = new RowMapper<ClaimedOutboxEntry>() {
        @Override
        public ClaimedOutboxEntry mapRow(ResultSet rs, int rowNum) throws SQLException {
            return new ClaimedOutboxEntry(
                    rs.getString("event_id"), rs.getLong("tenant_id"),
                    rs.getString("aggregate_type"), rs.getString("aggregate_id"),
                    rs.getString("event_type"), rs.getInt("schema_version"),
                    rs.getString("payload_text"), rs.getInt("retry_count"), rs.getInt("max_retries"));
        }
    };

    private final NamedParameterJdbcTemplate jdbc;

    public JdbcPowerModelOutboxRepository(DataSource dataSource) {
        this.jdbc = new NamedParameterJdbcTemplate(Objects.requireNonNull(dataSource, "dataSource"));
    }

    @Override
    public void insertPending(OutboxEntry entry) {
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("id", entry.id())
                .addValue("eventId", entry.eventId())
                .addValue("tenantId", entry.tenantId())
                .addValue("auditEventId", entry.auditEventId())
                .addValue("aggregateType", entry.aggregateType())
                .addValue("aggregateId", entry.aggregateId())
                .addValue("eventType", entry.eventType())
                .addValue("schemaVersion", entry.schemaVersion())
                .addValue("payload", entry.payload())
                .addValue("payloadHash", entry.payloadHash())
                .addValue("maxRetries", entry.maxRetries());
        jdbc.update(INSERT_PENDING_SQL, params);
    }

    @Override
    public List<ClaimedOutboxEntry> claimDue(Instant now, String leaseOwner,
                                             Duration leaseDuration, int batchSize) {
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("now", java.sql.Timestamp.from(now))
                .addValue("leaseOwner", leaseOwner)
                .addValue("leaseUntil", java.sql.Timestamp.from(now.plus(leaseDuration)))
                .addValue("batchSize", batchSize);
        return jdbc.query(CLAIM_DUE_SQL, params, CLAIMED_MAPPER);
    }

    @Override
    public void markPublished(String eventId, Instant publishedAt) {
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("eventId", eventId)
                .addValue("publishedAt", java.sql.Timestamp.from(publishedAt));
        jdbc.update(MARK_PUBLISHED_SQL, params);
    }

    @Override
    public void markRetry(String eventId, int retryCount, Instant nextAttemptAt,
                          String errorCode, String errorDigest) {
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("eventId", eventId)
                .addValue("retryCount", retryCount)
                .addValue("nextAttemptAt", java.sql.Timestamp.from(nextAttemptAt))
                .addValue("errorCode", truncate(errorCode, MAX_ERROR_CODE_LENGTH))
                .addValue("errorDigest", truncate(errorDigest, MAX_ERROR_DIGEST_LENGTH));
        jdbc.update(MARK_RETRY_SQL, params);
    }

    @Override
    public void markDeadLetter(String eventId, String errorCode, String errorDigest) {
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("eventId", eventId)
                .addValue("errorCode", truncate(errorCode, MAX_ERROR_CODE_LENGTH))
                .addValue("errorDigest", truncate(errorDigest, MAX_ERROR_DIGEST_LENGTH));
        jdbc.update(MARK_DEAD_LETTER_SQL, params);
    }

    @Override
    public long countByStatus(String status) {
        Long count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM power_model_release_outbox WHERE status = :status",
                new MapSqlParameterSource("status", status), Long.class);
        return count == null ? 0L : count.longValue();
    }

    private static String truncate(String value, int maxLength) {
        if (value == null) {
            return null;
        }
        return value.length() <= maxLength ? value : value.substring(0, maxLength);
    }
}
