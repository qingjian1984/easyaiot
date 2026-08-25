package com.basiclab.iot.device.alarm.infrastructure.persistence;

import com.basiclab.iot.device.alarm.application.AlarmSourceCommand;
import com.basiclab.iot.device.alarm.application.AlarmSourcePersistencePort;
import com.basiclab.iot.device.alarm.contract.AlarmSeverity;
import com.basiclab.iot.device.alarm.contract.AlarmStatus;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import javax.sql.DataSource;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Objects;

/**
 * P02-M2-02B 的 V011-only JDBC 适配器。无 DDL、无启动副作用、无 transport 装配。
 * 本类故意不标 Component，须在后续 capability 任务中显式装配。
 */
public final class JdbcAlarmSourcePersistence implements AlarmSourcePersistencePort {

    private static final String FIND_INBOX =
            "SELECT envelope_hash,status FROM public.alarm_source_inbox"
                    + " WHERE message_id=CAST(:messageId AS uuid)";
    private static final String INSERT_INBOX =
            "INSERT INTO public.alarm_source_inbox"
                    + " (id,message_id,tenant_id,event_type,source_action,event_version,source,"
                    + " envelope_hash,payload_json,status,received_at)"
                    + " VALUES (:id,CAST(:messageId AS uuid),:tenantId,'device.alarm.source-event.v1',"
                    + " :sourceAction,'1.0','iot-device',:envelopeHash,CAST(:payloadJson AS jsonb),"
                    + " 'RECEIVED',:receivedAt) ON CONFLICT (message_id) DO NOTHING";
    private static final String MARK_INBOX_PROCESSED =
            "UPDATE public.alarm_source_inbox SET status='PROCESSED',processed_at=:at,"
                    + " quarantined_at=NULL,last_error_code=NULL,last_error_summary=NULL"
                    + " WHERE message_id=CAST(:messageId AS uuid) AND status='RECEIVED'";
    private static final String QUARANTINE_INBOX =
            "INSERT INTO public.alarm_source_inbox"
                    + " (id,message_id,tenant_id,event_type,source_action,event_version,source,"
                    + " envelope_hash,payload_json,status,last_error_code,last_error_summary,"
                    + " received_at,quarantined_at)"
                    + " VALUES (:id,CAST(:messageId AS uuid),:tenantId,'device.alarm.source-event.v1',"
                    + " :sourceAction,'1.0','iot-device',:envelopeHash,CAST(:payloadJson AS jsonb),"
                    + " 'QUARANTINED',:errorCode,:errorSummary,:at,:at)"
                    + " ON CONFLICT (message_id) DO UPDATE SET status='QUARANTINED',"
                    + " last_error_code=EXCLUDED.last_error_code,"
                    + " last_error_summary=EXCLUDED.last_error_summary,"
                    + " quarantined_at=EXCLUDED.quarantined_at";

    private static final String FIND_MAPPING =
            "SELECT alarm_id,source_payload_hash FROM public.alarm_source_mapping"
                    + " WHERE tenant_id=:tenantId AND source_type=:sourceType"
                    + " AND source_id=:sourceId AND cycle_key=:cycleKey";
    private static final String INSERT_MAPPING =
            "INSERT INTO public.alarm_source_mapping"
                    + " (id,tenant_id,alarm_id,source_type,source_id,cycle_key,source_payload_hash,"
                    + " mapping_method,created_at)"
                    + " VALUES (:id,:tenantId,:alarmId,:sourceType,:sourceId,:cycleKey,"
                    + " :sourcePayloadHash,'NATIVE',:createdAt)"
                    + " ON CONFLICT (tenant_id,source_type,source_id,cycle_key) DO NOTHING";

    private static final String ALARM_COLUMNS =
            "id,tenant_id,site_id,source_type,source_id,cycle_key,cycle_identity,"
                    + "cycle_identity_hash,source_object_id,device_identification,property_code,"
                    + "rule_id,rule_version_id,rule_version,severity,status,row_version,"
                    + "occurrence_count,escalation_level,first_occurred_at,last_occurred_at,"
                    + "recovered_at,source_timezone,source_offset,created_by,created_at";
    private static final String FIND_ALARM = "SELECT " + ALARM_COLUMNS
            + " FROM public.alarm_record WHERE tenant_id=:tenantId AND site_id=:siteId AND id=:id";
    private static final String FIND_ALARM_BY_CYCLE = "SELECT " + ALARM_COLUMNS
            + " FROM public.alarm_record WHERE tenant_id=:tenantId"
            + " AND cycle_identity_hash=:cycleIdentityHash";
    private static final String INSERT_ALARM =
            "INSERT INTO public.alarm_record"
                    + " (id,tenant_id,site_id,source_type,source_id,cycle_key,cycle_identity,"
                    + " cycle_identity_hash,source_object_id,device_identification,property_code,"
                    + " rule_id,rule_version_id,rule_version,severity,status,row_version,"
                    + " occurrence_count,escalation_level,first_occurred_at,last_occurred_at,"
                    + " source_timezone,source_offset,created_by,updated_by,created_at,updated_at)"
                    + " VALUES (:id,:tenantId,:siteId,:sourceType,:sourceId,:cycleKey,:cycleIdentity,"
                    + " :cycleIdentityHash,:sourceObjectId,:deviceIdentification,:propertyCode,"
                    + " :ruleId,:ruleVersionId,:ruleVersion,:severity,'ACTIVE',0,1,0,"
                    + " :firstOccurredAt,:lastOccurredAt,:sourceTimezone,:sourceOffset,"
                    + " :actorId,:actorId,:createdAt,:createdAt)"
                    + " ON CONFLICT (tenant_id,cycle_identity_hash) DO NOTHING";
    private static final String RECORD_RAISED =
            "UPDATE public.alarm_record SET occurrence_count=occurrence_count+1,"
                    + " last_occurred_at=GREATEST(last_occurred_at,:occurredAt),"
                    + " row_version=row_version+1,updated_by=:actorId,updated_at=:updatedAt"
                    + " WHERE tenant_id=:tenantId AND site_id=:siteId AND id=:id"
                    + " AND row_version=:expectedVersion"
                    + " AND status IN ('ACTIVE','ACKNOWLEDGED','PROCESSING','IGNORED')";
    private static final String RECORD_RECOVERED =
            "UPDATE public.alarm_record SET status='RECOVERED',recovered_at=:recoveredAt,"
                    + " last_occurred_at=GREATEST(last_occurred_at,:recoveredAt),"
                    + " ignored_from_status=NULL,ignored_until=NULL,row_version=row_version+1,"
                    + " updated_by=:actorId,updated_at=:updatedAt"
                    + " WHERE tenant_id=:tenantId AND site_id=:siteId AND id=:id"
                    + " AND row_version=:expectedVersion"
                    + " AND status IN ('ACTIVE','ACKNOWLEDGED','PROCESSING','IGNORED')";
    private static final String INSERT_ACTION =
            "INSERT INTO public.alarm_action_log"
                    + " (id,tenant_id,alarm_id,sequence_no,action_type,from_status,to_status,"
                    + " actor_type,actor_id,request_id,trace_id,details,occurred_at,recorded_at)"
                    + " VALUES (:id,:tenantId,:alarmId,:sequenceNo,:actionType,:fromStatus,:toStatus,"
                    + " 'SERVICE',:actorId,:requestId,:traceId,CAST('{}' AS jsonb),:occurredAt,:recordedAt)";
    private static final String INSERT_OUTBOX =
            "INSERT INTO public.alarm_outbox"
                    + " (id,event_id,tenant_id,alarm_id,event_type,event_version,partition_key,"
                    + " payload_hash,payload_json,headers_json,status,retry_count,max_retries,"
                    + " next_attempt_at,created_at,updated_at)"
                    + " VALUES (:id,CAST(:eventId AS uuid),:tenantId,:alarmId,:eventType,'1.0',"
                    + " CAST(:alarmId AS varchar),:payloadHash,CAST(:payloadJson AS jsonb),"
                    + " CAST(:headersJson AS jsonb),'PENDING',0,:maxRetries,:createdAt,:createdAt,:createdAt)";

    private final NamedParameterJdbcTemplate jdbc;

    public JdbcAlarmSourcePersistence(DataSource dataSource) {
        this(new NamedParameterJdbcTemplate(Objects.requireNonNull(dataSource, "dataSource")));
    }

    JdbcAlarmSourcePersistence(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = Objects.requireNonNull(jdbc, "jdbc");
    }

    @Override
    public InboxEntry findInbox(String messageId) {
        List<InboxEntry> rows = jdbc.query(FIND_INBOX,
                new MapSqlParameterSource("messageId", messageId),
                (rs, rowNum) -> new InboxEntry(rs.getString("envelope_hash"),
                        InboxStatus.valueOf(rs.getString("status"))));
        return one(rows);
    }

    @Override
    public boolean insertInboxReceived(long id, AlarmSourceCommand command) {
        return jdbc.update(INSERT_INBOX, inboxParams(id, command)
                .addValue("receivedAt", ts(command.recordedAt()))) == 1;
    }

    @Override
    public void markInboxProcessed(String messageId, Instant processedAt) {
        int changed = jdbc.update(MARK_INBOX_PROCESSED, new MapSqlParameterSource()
                .addValue("messageId", messageId).addValue("at", ts(processedAt)));
        if (changed != 1) throw new IllegalStateException("ALARM_INBOX_PROCESSED_CAS_CONFLICT");
    }

    @Override
    public void quarantineInbox(long id, AlarmSourceCommand command, String errorCode,
                                String safeSummary, Instant quarantinedAt) {
        jdbc.update(QUARANTINE_INBOX, inboxParams(id, command)
                .addValue("errorCode", truncate(errorCode, 64))
                .addValue("errorSummary", truncate(safeSummary, 1000))
                .addValue("at", ts(quarantinedAt)));
    }

    @Override
    public SourceMapping findMapping(long tenantId, String sourceType, String sourceId,
                                     String cycleKey) {
        List<SourceMapping> rows = jdbc.query(FIND_MAPPING, new MapSqlParameterSource()
                        .addValue("tenantId", tenantId).addValue("sourceType", sourceType)
                        .addValue("sourceId", sourceId).addValue("cycleKey", cycleKey),
                (rs, rowNum) -> new SourceMapping(rs.getLong("alarm_id"),
                        rs.getString("source_payload_hash")));
        return one(rows);
    }

    @Override
    public boolean insertMappingIfAbsent(long id, long tenantId, long alarmId,
                                         String sourceType, String sourceId, String cycleKey,
                                         String sourcePayloadHash, Instant createdAt) {
        return jdbc.update(INSERT_MAPPING, new MapSqlParameterSource()
                .addValue("id", id).addValue("tenantId", tenantId).addValue("alarmId", alarmId)
                .addValue("sourceType", sourceType).addValue("sourceId", sourceId)
                .addValue("cycleKey", cycleKey).addValue("sourcePayloadHash", sourcePayloadHash)
                .addValue("createdAt", ts(createdAt))) == 1;
    }

    @Override
    public AlarmSnapshot findAlarm(long tenantId, long siteId, long alarmId) {
        List<AlarmSnapshot> rows = jdbc.query(FIND_ALARM, new MapSqlParameterSource()
                .addValue("tenantId", tenantId).addValue("siteId", siteId)
                .addValue("id", alarmId), ALARM_MAPPER);
        return one(rows);
    }

    @Override
    public AlarmSnapshot findAlarmByCycleHash(long tenantId, String cycleIdentityHash) {
        List<AlarmSnapshot> rows = jdbc.query(FIND_ALARM_BY_CYCLE, new MapSqlParameterSource()
                .addValue("tenantId", tenantId).addValue("cycleIdentityHash", cycleIdentityHash),
                ALARM_MAPPER);
        return one(rows);
    }

    @Override
    public boolean insertAlarmIfAbsent(AlarmSnapshot alarm) {
        return jdbc.update(INSERT_ALARM, alarmParams(alarm)) == 1;
    }

    @Override
    public boolean recordRaisedCas(long tenantId, long siteId, long alarmId,
                                   long expectedVersion, Instant occurredAt,
                                   Instant recordedAt, String actorId) {
        return jdbc.update(RECORD_RAISED, casParams(tenantId, siteId, alarmId,
                expectedVersion, actorId, recordedAt).addValue("occurredAt", ts(occurredAt))) == 1;
    }

    @Override
    public boolean recordRecoveredCas(long tenantId, long siteId, long alarmId,
                                      long expectedVersion, Instant recoveredAt,
                                      Instant recordedAt, String actorId) {
        return jdbc.update(RECORD_RECOVERED, casParams(tenantId, siteId, alarmId,
                expectedVersion, actorId, recordedAt).addValue("recoveredAt", ts(recoveredAt))) == 1;
    }

    @Override
    public void appendAction(ActionEntry action) {
        jdbc.update(INSERT_ACTION, new MapSqlParameterSource()
                .addValue("id", action.id()).addValue("tenantId", action.tenantId())
                .addValue("alarmId", action.alarmId()).addValue("sequenceNo", action.sequenceNo())
                .addValue("actionType", action.actionType())
                .addValue("fromStatus", name(action.fromStatus())).addValue("toStatus", name(action.toStatus()))
                .addValue("actorId", action.actorId()).addValue("requestId", action.requestId())
                .addValue("traceId", action.traceId()).addValue("occurredAt", ts(action.occurredAt()))
                .addValue("recordedAt", ts(action.recordedAt())));
    }

    @Override
    public void enqueue(OutboxEntry event) {
        jdbc.update(INSERT_OUTBOX, new MapSqlParameterSource()
                .addValue("id", event.id()).addValue("eventId", event.eventId())
                .addValue("tenantId", event.tenantId()).addValue("alarmId", event.alarmId())
                .addValue("eventType", event.eventType()).addValue("payloadHash", event.payloadHash())
                .addValue("payloadJson", event.payloadJson()).addValue("headersJson", event.headersJson())
                .addValue("maxRetries", event.maxRetries()).addValue("createdAt", ts(event.createdAt())));
    }

    private MapSqlParameterSource inboxParams(long id, AlarmSourceCommand command) {
        return new MapSqlParameterSource().addValue("id", id)
                .addValue("messageId", command.messageId()).addValue("tenantId", command.tenantId())
                .addValue("sourceAction", command.action().name())
                .addValue("envelopeHash", command.envelopeHash())
                .addValue("payloadJson", command.payloadJson());
    }

    private static MapSqlParameterSource alarmParams(AlarmSnapshot a) {
        return new MapSqlParameterSource().addValue("id", a.id()).addValue("tenantId", a.tenantId())
                .addValue("siteId", a.siteId()).addValue("sourceType", a.sourceType())
                .addValue("sourceId", a.sourceId()).addValue("cycleKey", a.cycleKey())
                .addValue("cycleIdentity", a.cycleIdentity()).addValue("cycleIdentityHash", a.cycleIdentityHash())
                .addValue("sourceObjectId", a.sourceObjectId())
                .addValue("deviceIdentification", a.deviceIdentification())
                .addValue("propertyCode", a.propertyCode()).addValue("ruleId", a.ruleId())
                .addValue("ruleVersionId", a.ruleVersionId()).addValue("ruleVersion", a.ruleVersion())
                .addValue("severity", a.severity().name()).addValue("firstOccurredAt", ts(a.firstOccurredAt()))
                .addValue("lastOccurredAt", ts(a.lastOccurredAt())).addValue("sourceTimezone", a.sourceTimezone())
                .addValue("sourceOffset", a.sourceOffset()).addValue("actorId", a.actorId())
                .addValue("createdAt", ts(a.createdAt()));
    }

    private static MapSqlParameterSource casParams(long tenantId, long siteId, long alarmId,
                                                    long expectedVersion, String actorId,
                                                    Instant updatedAt) {
        return new MapSqlParameterSource().addValue("tenantId", tenantId).addValue("siteId", siteId)
                .addValue("id", alarmId).addValue("expectedVersion", expectedVersion)
                .addValue("actorId", actorId).addValue("updatedAt", ts(updatedAt));
    }

    private static final RowMapper<AlarmSnapshot> ALARM_MAPPER = new RowMapper<AlarmSnapshot>() {
        @Override
        public AlarmSnapshot mapRow(ResultSet rs, int rowNum) throws SQLException {
            return new AlarmSnapshot(rs.getLong("id"), rs.getLong("tenant_id"), rs.getLong("site_id"),
                    rs.getString("source_type"), rs.getString("source_id"), rs.getString("cycle_key"),
                    rs.getString("cycle_identity"), rs.getString("cycle_identity_hash"),
                    rs.getString("source_object_id"), rs.getString("device_identification"),
                    rs.getString("property_code"), nullableLong(rs, "rule_id"),
                    nullableLong(rs, "rule_version_id"), rs.getString("rule_version"),
                    AlarmSeverity.valueOf(rs.getString("severity")), AlarmStatus.valueOf(rs.getString("status")),
                    rs.getLong("row_version"), rs.getLong("occurrence_count"),
                    rs.getInt("escalation_level"), instant(rs, "first_occurred_at"),
                    instant(rs, "last_occurred_at"), instant(rs, "recovered_at"),
                    rs.getString("source_timezone"), rs.getString("source_offset"),
                    rs.getString("created_by"), instant(rs, "created_at"));
        }
    };

    private static Long nullableLong(ResultSet rs, String column) throws SQLException {
        long value = rs.getLong(column);
        return rs.wasNull() ? null : value;
    }

    private static Instant instant(ResultSet rs, String column) throws SQLException {
        Timestamp value = rs.getTimestamp(column);
        return value == null ? null : value.toInstant();
    }

    private static Timestamp ts(Instant value) { return Timestamp.from(value); }
    private static String name(Enum<?> value) { return value == null ? null : value.name(); }
    private static String truncate(String value, int max) {
        if (value == null || value.length() <= max) return value;
        return value.substring(0, max);
    }
    private static <T> T one(List<T> rows) {
        if (rows.isEmpty()) return null;
        if (rows.size() != 1) throw new IllegalStateException("ALARM_PERSISTENCE_NOT_UNIQUE");
        return rows.get(0);
    }
}
