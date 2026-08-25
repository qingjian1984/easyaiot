package com.basiclab.iot.device.alarm.infrastructure.persistence;

import com.basiclab.iot.device.alarm.infrastructure.event.AlarmOutboxClaimedEntry;
import com.basiclab.iot.device.alarm.infrastructure.event.AlarmOutboxRepository;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import javax.sql.DataSource;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Objects;

/** V011 alarm_outbox 的原子 claim/lease JDBC 实现；无调度器、无 transport Bean。 */
public final class JdbcAlarmOutboxRepository implements AlarmOutboxRepository {

    private static final String CLAIM_DUE =
            "UPDATE public.alarm_outbox o SET status='PUBLISHING',lease_owner=:leaseOwner,"
                    + " lease_until=:leaseUntil,updated_at=:now WHERE o.id IN ("
                    + " SELECT c.id FROM public.alarm_outbox c WHERE"
                    + " (c.status='PENDING' AND c.next_attempt_at<=:now) OR"
                    + " (c.status='PUBLISHING' AND c.lease_until<=:now)"
                    + " ORDER BY c.created_at,c.id LIMIT :batchSize FOR UPDATE SKIP LOCKED)"
                    + " RETURNING o.id,o.event_id,o.tenant_id,o.alarm_id,o.event_type,"
                    + " o.event_version,o.partition_key,o.payload_hash,o.payload_json::text AS payload_json,"
                    + " o.headers_json::text AS headers_json,o.retry_count,o.max_retries";
    private static final String MARK_PUBLISHED =
            "UPDATE public.alarm_outbox SET status='PUBLISHED',published_at=:at,"
                    + " lease_owner=NULL,lease_until=NULL,last_error_code=NULL,last_error_summary=NULL,"
                    + " updated_at=:at WHERE event_id=CAST(:eventId AS uuid)"
                    + " AND status='PUBLISHING' AND lease_owner=:leaseOwner";
    private static final String MARK_RETRY =
            "UPDATE public.alarm_outbox SET status='PENDING',retry_count=:retryCount,"
                    + " next_attempt_at=:nextAttemptAt,lease_owner=NULL,lease_until=NULL,"
                    + " last_error_code=:errorCode,last_error_summary=:errorSummary,"
                    + " updated_at=:updatedAt WHERE event_id=CAST(:eventId AS uuid)"
                    + " AND status='PUBLISHING' AND lease_owner=:leaseOwner";
    private static final String MARK_DEAD =
            "UPDATE public.alarm_outbox SET status='DEAD_LETTER',dead_lettered_at=:at,"
                    + " lease_owner=NULL,lease_until=NULL,last_error_code=:errorCode,"
                    + " last_error_summary=:errorSummary,updated_at=:at"
                    + " WHERE event_id=CAST(:eventId AS uuid)"
                    + " AND status='PUBLISHING' AND lease_owner=:leaseOwner";

    private final NamedParameterJdbcTemplate jdbc;

    public JdbcAlarmOutboxRepository(DataSource dataSource) {
        this.jdbc = new NamedParameterJdbcTemplate(Objects.requireNonNull(dataSource, "dataSource"));
    }

    @Override
    public List<AlarmOutboxClaimedEntry> claimDue(Instant now, String leaseOwner,
                                                  Duration leaseDuration, int batchSize) {
        if (batchSize < 1) throw new IllegalArgumentException("batchSize must be >= 1");
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("now", ts(now)).addValue("leaseOwner", leaseOwner)
                .addValue("leaseUntil", ts(now.plus(leaseDuration))).addValue("batchSize", batchSize);
        return jdbc.query(CLAIM_DUE, params, (rs, rowNum) -> new AlarmOutboxClaimedEntry(
                rs.getLong("id"), rs.getString("event_id"), rs.getString("tenant_id"),
                rs.getString("alarm_id"), rs.getString("event_type"),
                rs.getString("event_version"), rs.getString("partition_key"),
                rs.getString("payload_hash"), rs.getString("payload_json"),
                rs.getString("headers_json"), rs.getInt("retry_count"),
                rs.getInt("max_retries")));
    }

    @Override
    public void markPublished(String eventId, String leaseOwner, Instant publishedAt) {
        requireOne(MARK_PUBLISHED, state(eventId, leaseOwner)
                .addValue("at", ts(publishedAt)), "ALARM_OUTBOX_PUBLISH_LEASE_LOST");
    }

    @Override
    public void markRetry(String eventId, String leaseOwner, int retryCount,
                          Instant failedAt, Instant nextAttemptAt,
                          String errorCode, String errorSummary) {
        requireOne(MARK_RETRY, state(eventId, leaseOwner)
                .addValue("retryCount", retryCount).addValue("nextAttemptAt", ts(nextAttemptAt))
                .addValue("errorCode", truncate(errorCode, 64))
                .addValue("errorSummary", truncate(errorSummary, 1000))
                .addValue("updatedAt", ts(failedAt)), "ALARM_OUTBOX_RETRY_LEASE_LOST");
    }

    @Override
    public void markDeadLetter(String eventId, String leaseOwner, Instant deadLetteredAt,
                               String errorCode, String errorSummary) {
        requireOne(MARK_DEAD, state(eventId, leaseOwner)
                .addValue("at", ts(deadLetteredAt)).addValue("errorCode", truncate(errorCode, 64))
                .addValue("errorSummary", truncate(errorSummary, 1000)),
                "ALARM_OUTBOX_DEAD_LETTER_LEASE_LOST");
    }

    private void requireOne(String sql, MapSqlParameterSource params, String code) {
        if (jdbc.update(sql, params) != 1) throw new IllegalStateException(code);
    }

    private static MapSqlParameterSource state(String eventId, String leaseOwner) {
        return new MapSqlParameterSource().addValue("eventId", eventId)
                .addValue("leaseOwner", leaseOwner);
    }

    private static Timestamp ts(Instant value) { return Timestamp.from(value); }
    private static String truncate(String value, int max) {
        if (value == null || value.length() <= max) return value;
        return value.substring(0, max);
    }
}
