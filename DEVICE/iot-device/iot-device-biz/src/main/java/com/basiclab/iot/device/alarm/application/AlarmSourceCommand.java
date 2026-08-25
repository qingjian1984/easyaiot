package com.basiclab.iot.device.alarm.application;

import com.basiclab.iot.device.alarm.contract.AlarmSeverity;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * 已完成 Envelope/Schema、租户和设备权威事实核验后的来源告警命令。
 * 本对象不得直接由未校验的 transport JSON 构造。
 */
public final class AlarmSourceCommand {

    public enum Action { RAISED, RECOVERED }

    private static final Pattern HASH = Pattern.compile("^sha256:[0-9a-f]{64}$");

    private final String messageId;
    private final long tenantId;
    private final long siteId;
    private final String sourceType;
    private final Action action;
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
    private final Instant occurredAt;
    private final Instant recordedAt;
    private final String sourcePayloadHash;
    private final String envelopeHash;
    private final String payloadJson;
    private final String sourceTimezone;
    private final String sourceOffset;
    private final String correlationId;
    private final String traceId;
    private final String actorId;

    public AlarmSourceCommand(String messageId, long tenantId, long siteId, String sourceType,
                              Action action, String sourceId, String cycleKey,
                              String cycleIdentity, String cycleIdentityHash,
                              String sourceObjectId, String deviceIdentification,
                              String propertyCode, Long ruleId, Long ruleVersionId,
                              String ruleVersion, AlarmSeverity severity, Instant occurredAt,
                              Instant recordedAt, String sourcePayloadHash, String envelopeHash,
                              String payloadJson, String sourceTimezone, String sourceOffset,
                              String correlationId, String traceId,
                              String actorId) {
        this.messageId = canonicalUuid(messageId);
        this.tenantId = positive(tenantId, "tenantId");
        this.siteId = positive(siteId, "siteId");
        this.sourceType = requireOneOf(sourceType, "sourceType",
                "THRESHOLD", "DEVICE_EVENT", "VIDEO", "AI", "RUNTIME");
        this.action = Objects.requireNonNull(action, "action");
        this.sourceId = text(sourceId, 256, "sourceId");
        this.cycleKey = text(cycleKey, 256, "cycleKey");
        this.cycleIdentity = text(cycleIdentity, 4096, "cycleIdentity");
        this.cycleIdentityHash = hash(cycleIdentityHash, "cycleIdentityHash");
        this.sourceObjectId = text(sourceObjectId, 256, "sourceObjectId");
        this.deviceIdentification = text(deviceIdentification, 255, "deviceIdentification");
        this.propertyCode = optionalText(propertyCode, 128, "propertyCode");
        this.ruleId = ruleId;
        this.ruleVersionId = ruleVersionId;
        this.ruleVersion = optionalText(ruleVersion, 32, "ruleVersion");
        requireRuleReference(sourceType, propertyCode, ruleId, ruleVersionId, ruleVersion);
        this.severity = Objects.requireNonNull(severity, "severity");
        this.occurredAt = Objects.requireNonNull(occurredAt, "occurredAt");
        this.recordedAt = Objects.requireNonNull(recordedAt, "recordedAt");
        this.sourcePayloadHash = hash(sourcePayloadHash, "sourcePayloadHash");
        this.envelopeHash = hash(envelopeHash, "envelopeHash");
        this.payloadJson = text(payloadJson, 1_048_576, "payloadJson");
        this.sourceTimezone = optionalText(sourceTimezone, 64, "sourceTimezone");
        this.sourceOffset = optionalText(sourceOffset, 16, "sourceOffset");
        if (this.sourceOffset != null
                && !this.sourceOffset.matches("^(Z|[+-](0[0-9]|1[0-4]):[0-5][0-9])$")) {
            throw new IllegalArgumentException("ALARM_SOURCE_INVALID: sourceOffset");
        }
        this.correlationId = text(correlationId, 128, "correlationId");
        this.traceId = optionalText(traceId, 128, "traceId");
        this.actorId = text(actorId, 64, "actorId");
    }

    private static void requireRuleReference(String sourceType, String propertyCode,
                                             Long ruleId, Long ruleVersionId,
                                             String ruleVersion) {
        boolean allNull = ruleId == null && ruleVersionId == null && ruleVersion == null;
        boolean allPresent = ruleId != null && ruleId > 0 && ruleVersionId != null
                && ruleVersionId > 0 && ruleVersion != null && !ruleVersion.trim().isEmpty();
        if (!(allNull || allPresent)) {
            throw new IllegalArgumentException("ALARM_RULE_REFERENCE_INVALID");
        }
        if ("THRESHOLD".equals(sourceType)
                && (propertyCode == null || propertyCode.trim().isEmpty() || !allPresent)) {
            throw new IllegalArgumentException("ALARM_THRESHOLD_RULE_REFERENCE_REQUIRED");
        }
    }

    private static long positive(long value, String field) {
        if (value <= 0) throw new IllegalArgumentException("ALARM_SOURCE_INVALID: " + field);
        return value;
    }

    private static String canonicalUuid(String value) {
        String safe = text(value, 36, "messageId");
        if (!UUID.fromString(safe).toString().equals(safe)) {
            throw new IllegalArgumentException("ALARM_SOURCE_INVALID: messageId");
        }
        return safe;
    }

    private static String hash(String value, String field) {
        if (value == null || !HASH.matcher(value).matches()) {
            throw new IllegalArgumentException("ALARM_SOURCE_INVALID: " + field);
        }
        return value;
    }

    private static String requireOneOf(String value, String field, String... allowed) {
        String safe = text(value, 32, field);
        for (String item : allowed) if (item.equals(safe)) return safe;
        throw new IllegalArgumentException("ALARM_SOURCE_INVALID: " + field);
    }

    private static String text(String value, int max, String field) {
        if (value == null || value.trim().isEmpty() || value.length() > max) {
            throw new IllegalArgumentException("ALARM_SOURCE_INVALID: " + field);
        }
        return value;
    }

    private static String optionalText(String value, int max, String field) {
        if (value == null) return null;
        return text(value, max, field);
    }

    public String messageId() { return messageId; }
    public long tenantId() { return tenantId; }
    public long siteId() { return siteId; }
    public String sourceType() { return sourceType; }
    public Action action() { return action; }
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
    public Instant occurredAt() { return occurredAt; }
    public Instant recordedAt() { return recordedAt; }
    public String sourcePayloadHash() { return sourcePayloadHash; }
    public String envelopeHash() { return envelopeHash; }
    public String payloadJson() { return payloadJson; }
    public String sourceTimezone() { return sourceTimezone; }
    public String sourceOffset() { return sourceOffset; }
    public String correlationId() { return correlationId; }
    public String traceId() { return traceId; }
    public String actorId() { return actorId; }
}
