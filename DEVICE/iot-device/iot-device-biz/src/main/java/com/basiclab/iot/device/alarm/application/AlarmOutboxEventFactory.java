package com.basiclab.iot.device.alarm.application;

import com.basiclab.iot.device.alarm.contract.AlarmEventEnvelope;
import com.basiclab.iot.device.alarm.contract.AlarmEventType;
import com.basiclab.iot.device.alarm.contract.AlarmStatus;
import com.basiclab.iot.device.alarm.application.AlarmSourcePersistencePort.AlarmSnapshot;
import com.basiclab.iot.device.alarm.application.AlarmSourcePersistencePort.OutboxEntry;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/** 构造并最终序列化告警领域 Envelope；Outbox hash 始终对最终 UTF-8 正文计算。 */
public final class AlarmOutboxEventFactory {

    private final ObjectMapper mapper;
    private final AlarmIdGenerator ids;
    private final int maxRetries;

    public AlarmOutboxEventFactory(ObjectMapper mapper, AlarmIdGenerator ids, int maxRetries) {
        this.mapper = Objects.requireNonNull(mapper, "mapper").copy()
                .configure(MapperFeature.SORT_PROPERTIES_ALPHABETICALLY, true)
                .configure(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS, true);
        this.ids = Objects.requireNonNull(ids, "ids");
        if (maxRetries < 0) throw new IllegalArgumentException("maxRetries must be >= 0");
        this.maxRetries = maxRetries;
    }

    public OutboxEntry created(AlarmSnapshot alarm, AlarmSourceCommand command) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("alarmId", Long.toString(alarm.id()));
        payload.put("status", AlarmStatus.ACTIVE.name());
        payload.put("severity", alarm.severity().name());
        payload.put("sourceType", alarm.sourceType());
        payload.put("sourceId", alarm.sourceId());
        payload.put("cycleKey", alarm.cycleKey());
        payload.put("siteId", Long.toString(alarm.siteId()));
        payload.put("deviceId", alarm.deviceIdentification());
        payload.put("propertyCode", alarm.propertyCode());
        payload.put("ruleId", alarm.ruleId() == null ? null : Long.toString(alarm.ruleId()));
        payload.put("ruleVersion", alarm.ruleVersion());
        payload.put("firstOccurredAt", offset(alarm.firstOccurredAt()).toString());
        payload.put("occurrenceCount", 1L);
        return event(AlarmEventType.CREATED, alarm, command, payload);
    }

    public OutboxEntry occurrence(AlarmSnapshot before, AlarmSourceCommand command) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("alarmId", Long.toString(before.id()));
        payload.put("status", before.status().name());
        payload.put("occurrenceCount", before.occurrenceCount() + 1);
        payload.put("lastOccurredAt", offset(command.occurredAt()).toString());
        payload.put("sourceMessageId", command.messageId());
        return event(AlarmEventType.OCCURRENCE_RECORDED, before, command, payload);
    }

    public OutboxEntry recovered(AlarmSnapshot before, AlarmSourceCommand command) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("alarmId", Long.toString(before.id()));
        payload.put("fromStatus", before.status().name());
        payload.put("status", AlarmStatus.RECOVERED.name());
        payload.put("recoveredAt", offset(command.occurredAt()).toString());
        payload.put("version", before.rowVersion() + 1);
        return event(AlarmEventType.RECOVERED, before, command, payload);
    }

    private OutboxEntry event(AlarmEventType type, AlarmSnapshot alarm,
                              AlarmSourceCommand command, Map<String, Object> payload) {
        String eventId = ids.nextEventId();
        AlarmEventEnvelope envelope = AlarmEventEnvelope.of(eventId, type,
                Long.toString(command.tenantId()), offset(command.occurredAt()),
                offset(command.recordedAt()), AlarmEventEnvelope.SOURCE,
                command.correlationId(), command.traceId(), payload);
        try {
            String json = mapper.writeValueAsString(envelope);
            return new OutboxEntry(ids.nextLongId(), eventId, command.tenantId(), alarm.id(),
                    type.value(), sha256(json), json, "{}", maxRetries, command.recordedAt());
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("ALARM_OUTBOX_SERIALIZATION_FAILED", ex);
        }
    }

    private static OffsetDateTime offset(java.time.Instant instant) {
        return instant.atOffset(ZoneOffset.UTC);
    }

    private static String sha256(String json) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(json.getBytes(StandardCharsets.UTF_8));
            StringBuilder value = new StringBuilder("sha256:");
            for (byte item : digest) value.append(String.format("%02x", item & 0xff));
            return value.toString();
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 unavailable", ex);
        }
    }
}
