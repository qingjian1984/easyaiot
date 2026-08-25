package com.basiclab.iot.device.alarm.application;

import com.basiclab.iot.device.alarm.contract.AlarmSeverity;
import com.basiclab.iot.device.alarm.contract.AlarmStatus;

import java.time.Instant;

/**
 * 告警来源 Inbox、映射、主记录、动作历史和 Outbox 的同数据源持久化端口。
 * 实现只能访问 V011 对象，事务边界由应用服务持有。
 */
public interface AlarmSourcePersistencePort {

    enum InboxStatus { RECEIVED, PROCESSED, QUARANTINED }

    InboxEntry findInbox(String messageId);

    boolean insertInboxReceived(long id, AlarmSourceCommand command);

    void markInboxProcessed(String messageId, Instant processedAt);

    void quarantineInbox(long id, AlarmSourceCommand command, String errorCode,
                         String safeSummary, Instant quarantinedAt);

    SourceMapping findMapping(long tenantId, String sourceType, String sourceId,
                              String cycleKey);

    /** 唯一键争抢失败返回 false；调用方必须重读并比较完整身份。 */
    boolean insertMappingIfAbsent(long id, long tenantId, long alarmId,
                                  String sourceType, String sourceId, String cycleKey,
                                  String sourcePayloadHash, Instant createdAt);

    AlarmSnapshot findAlarm(long tenantId, long siteId, long alarmId);

    AlarmSnapshot findAlarmByCycleHash(long tenantId, String cycleIdentityHash);

    /** 周期 hash 唯一键争抢失败返回 false；不得靠捕获约束异常继续事务。 */
    boolean insertAlarmIfAbsent(AlarmSnapshot alarm);

    boolean recordRaisedCas(long tenantId, long siteId, long alarmId,
                            long expectedVersion, Instant occurredAt,
                            Instant recordedAt, String actorId);

    boolean recordRecoveredCas(long tenantId, long siteId, long alarmId,
                               long expectedVersion, Instant recoveredAt,
                               Instant recordedAt, String actorId);

    /**
     * 在当前告警事务内原子分配下一个动作序号。实现必须按租户、站点和告警主键定位，
     * 不得从 row_version 或 action_log 的 MAX(sequence_no) 推导。
     * 目标不存在或数据范围不匹配时必须 fail-closed。
     */
    long allocateNextActionSequence(long tenantId, long siteId, long alarmId);

    void appendAction(ActionEntry action);

    void enqueue(OutboxEntry event);

    final class InboxEntry {
        private final String envelopeHash;
        private final InboxStatus status;

        public InboxEntry(String envelopeHash, InboxStatus status) {
            this.envelopeHash = envelopeHash;
            this.status = status;
        }

        public String envelopeHash() { return envelopeHash; }
        public InboxStatus status() { return status; }
    }

    final class SourceMapping {
        private final long alarmId;
        private final String sourcePayloadHash;

        public SourceMapping(long alarmId, String sourcePayloadHash) {
            this.alarmId = alarmId;
            this.sourcePayloadHash = sourcePayloadHash;
        }

        public long alarmId() { return alarmId; }
        public String sourcePayloadHash() { return sourcePayloadHash; }
    }

    final class AlarmSnapshot {
        private final long id;
        private final long tenantId;
        private final long siteId;
        private final String sourceType;
        private final String sourceId;
        private final String cycleKey;
        private final String cycleIdentity;
        private final String cycleIdentityHash;
        private final String sourceObjectId;
        private final String deviceIdentification;
        private final String propertyCode;
        private final Long ruleId;
        private final Long ruleVersionId;
        private final String ruleVersion;
        private final AlarmSeverity severity;
        private final AlarmStatus status;
        private final long rowVersion;
        private final long occurrenceCount;
        private final int escalationLevel;
        private final Instant firstOccurredAt;
        private final Instant lastOccurredAt;
        private final Instant recoveredAt;
        private final String sourceTimezone;
        private final String sourceOffset;
        private final String actorId;
        private final Instant createdAt;

        public AlarmSnapshot(long id, long tenantId, long siteId, String sourceType,
                             String sourceId, String cycleKey, String cycleIdentity,
                             String cycleIdentityHash, String sourceObjectId,
                             String deviceIdentification, String propertyCode, Long ruleId,
                             Long ruleVersionId, String ruleVersion, AlarmSeverity severity,
                             AlarmStatus status, long rowVersion, long occurrenceCount,
                             int escalationLevel, Instant firstOccurredAt, Instant lastOccurredAt,
                             Instant recoveredAt, String sourceTimezone, String sourceOffset,
                             String actorId, Instant createdAt) {
            this.id = id;
            this.tenantId = tenantId;
            this.siteId = siteId;
            this.sourceType = sourceType;
            this.sourceId = sourceId;
            this.cycleKey = cycleKey;
            this.cycleIdentity = cycleIdentity;
            this.cycleIdentityHash = cycleIdentityHash;
            this.sourceObjectId = sourceObjectId;
            this.deviceIdentification = deviceIdentification;
            this.propertyCode = propertyCode;
            this.ruleId = ruleId;
            this.ruleVersionId = ruleVersionId;
            this.ruleVersion = ruleVersion;
            this.severity = severity;
            this.status = status;
            this.rowVersion = rowVersion;
            this.occurrenceCount = occurrenceCount;
            this.escalationLevel = escalationLevel;
            this.firstOccurredAt = firstOccurredAt;
            this.lastOccurredAt = lastOccurredAt;
            this.recoveredAt = recoveredAt;
            this.sourceTimezone = sourceTimezone;
            this.sourceOffset = sourceOffset;
            this.actorId = actorId;
            this.createdAt = createdAt;
        }

        public static AlarmSnapshot initial(long id, AlarmSourceCommand command,
                                            String timezone, String offset) {
            return new AlarmSnapshot(id, command.tenantId(), command.siteId(),
                    command.sourceType(), command.sourceId(), command.cycleKey(),
                    command.cycleIdentity(), command.cycleIdentityHash(),
                    command.sourceObjectId(), command.deviceIdentification(),
                    command.propertyCode(), command.ruleId(), command.ruleVersionId(),
                    command.ruleVersion(), command.severity(), AlarmStatus.ACTIVE, 0, 1, 0,
                    command.occurredAt(), command.occurredAt(), null, timezone, offset,
                    command.actorId(), command.recordedAt());
        }

        public long id() { return id; }
        public long tenantId() { return tenantId; }
        public long siteId() { return siteId; }
        public String sourceType() { return sourceType; }
        public String sourceId() { return sourceId; }
        public String cycleKey() { return cycleKey; }
        public String cycleIdentity() { return cycleIdentity; }
        public String cycleIdentityHash() { return cycleIdentityHash; }
        public String sourceObjectId() { return sourceObjectId; }
        public String deviceIdentification() { return deviceIdentification; }
        public String propertyCode() { return propertyCode; }
        public Long ruleId() { return ruleId; }
        public Long ruleVersionId() { return ruleVersionId; }
        public String ruleVersion() { return ruleVersion; }
        public AlarmSeverity severity() { return severity; }
        public AlarmStatus status() { return status; }
        public long rowVersion() { return rowVersion; }
        public long occurrenceCount() { return occurrenceCount; }
        public int escalationLevel() { return escalationLevel; }
        public Instant firstOccurredAt() { return firstOccurredAt; }
        public Instant lastOccurredAt() { return lastOccurredAt; }
        public Instant recoveredAt() { return recoveredAt; }
        public String sourceTimezone() { return sourceTimezone; }
        public String sourceOffset() { return sourceOffset; }
        public String actorId() { return actorId; }
        public Instant createdAt() { return createdAt; }
    }

    final class ActionEntry {
        private final long id;
        private final long tenantId;
        private final long alarmId;
        private final long sequenceNo;
        private final String actionType;
        private final AlarmStatus fromStatus;
        private final AlarmStatus toStatus;
        private final String actorId;
        private final String requestId;
        private final String traceId;
        private final Instant occurredAt;
        private final Instant recordedAt;

        public ActionEntry(long id, long tenantId, long alarmId, long sequenceNo,
                           String actionType, AlarmStatus fromStatus, AlarmStatus toStatus,
                           String actorId, String requestId, String traceId,
                           Instant occurredAt, Instant recordedAt) {
            this.id = id; this.tenantId = tenantId; this.alarmId = alarmId;
            this.sequenceNo = sequenceNo; this.actionType = actionType;
            this.fromStatus = fromStatus; this.toStatus = toStatus; this.actorId = actorId;
            this.requestId = requestId; this.traceId = traceId;
            this.occurredAt = occurredAt; this.recordedAt = recordedAt;
        }

        public long id() { return id; }
        public long tenantId() { return tenantId; }
        public long alarmId() { return alarmId; }
        public long sequenceNo() { return sequenceNo; }
        public String actionType() { return actionType; }
        public AlarmStatus fromStatus() { return fromStatus; }
        public AlarmStatus toStatus() { return toStatus; }
        public String actorId() { return actorId; }
        public String requestId() { return requestId; }
        public String traceId() { return traceId; }
        public Instant occurredAt() { return occurredAt; }
        public Instant recordedAt() { return recordedAt; }
    }

    final class OutboxEntry {
        private final long id;
        private final String eventId;
        private final long tenantId;
        private final long alarmId;
        private final String eventType;
        private final String payloadHash;
        private final String payloadJson;
        private final String headersJson;
        private final int maxRetries;
        private final Instant createdAt;

        public OutboxEntry(long id, String eventId, long tenantId, long alarmId,
                           String eventType, String payloadHash, String payloadJson,
                           String headersJson, int maxRetries, Instant createdAt) {
            this.id = id; this.eventId = eventId; this.tenantId = tenantId;
            this.alarmId = alarmId; this.eventType = eventType;
            this.payloadHash = payloadHash; this.payloadJson = payloadJson;
            this.headersJson = headersJson; this.maxRetries = maxRetries;
            this.createdAt = createdAt;
        }

        public long id() { return id; }
        public String eventId() { return eventId; }
        public long tenantId() { return tenantId; }
        public long alarmId() { return alarmId; }
        public String eventType() { return eventType; }
        public String payloadHash() { return payloadHash; }
        public String payloadJson() { return payloadJson; }
        public String headersJson() { return headersJson; }
        public int maxRetries() { return maxRetries; }
        public Instant createdAt() { return createdAt; }
    }
}
