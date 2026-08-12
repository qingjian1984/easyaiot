package com.basiclab.iot.sink.telemetry.envelope;

/**
 * TD-002 §6 / TD-003 §6 Telemetry Envelope V1。
 *
 * <p>M1 固定 {@code schemaVersion="1.0"}、{@code canonicalizationVersion="jcs-rfc8785-v1"}。
 * 每条测点样本一个 envelope；落库/发送/重试复用同一份 UTF-8 canonical JSON 字节，
 * 不从列值重新拼装（TD-002 §6 不变量）。
 *
 * <p>{@code messageId} 是幂等键（UUID v4 小写 36 字符，兼容 32 无连字符），不用于排序。
 * {@code sentAt} 首次本地持久提交后保持不变。
 *
 * <p>构造时强制校验 M1 不变量（schemaVersion/canonicalizationVersion 固定、非空字段、
 * valueEncoding 固定 decimal-string、sequence/configVersion 范围）。
 */
public record TelemetryEnvelope(
        String schemaVersion,
        String canonicalizationVersion,
        String messageId,
        String requestId,
        String tenantId,
        String siteCode,
        String deviceIdentification,
        String propertyCode,
        String value,
        String valueEncoding,
        TelemetryQuality quality,
        DataPriority dataPriority,
        String collectedAt,
        String sentAt,
        long sequence,
        String source,
        long configVersion
) {
    /** M1 固定 schema 版本。 */
    public static final String SCHEMA_VERSION = "1.0";

    /** M1 固定规范化版本（RFC 8785 JCS）。 */
    public static final String CANONICALIZATION_VERSION = "jcs-rfc8785-v1";

    /** Envelope canonical bytes 上限（TD-002 §6：64 KiB）。 */
    public static final int MAX_ENVELOPE_BYTES = 64 * 1024;

    /** JS 安全整数上限（TD-002 §6：9007199254740991）。 */
    public static final long MAX_SAFE_INTEGER = 9007199254740991L;

    /** M1 固定 valueEncoding。 */
    public static final String VALUE_ENCODING_DECIMAL_STRING = "decimal-string";

    public TelemetryEnvelope {
        requireFixed("schemaVersion", schemaVersion, SCHEMA_VERSION);
        requireFixed("canonicalizationVersion", canonicalizationVersion, CANONICALIZATION_VERSION);
        requireNonBlank("messageId", messageId);
        requireNonBlank("requestId", requestId);
        requireNonBlank("tenantId", tenantId);
        requireNonBlank("siteCode", siteCode);
        requireNonBlank("deviceIdentification", deviceIdentification);
        requireNonBlank("propertyCode", propertyCode);
        requireFixed("valueEncoding", valueEncoding, VALUE_ENCODING_DECIMAL_STRING);
        if (quality == null) {
            throw new IllegalArgumentException("quality required");
        }
        if (dataPriority == null) {
            throw new IllegalArgumentException("dataPriority required");
        }
        requireNonBlank("collectedAt", collectedAt);
        requireNonBlank("sentAt", sentAt);
        requireNonBlank("source", source);
        if (sequence < 0 || sequence > MAX_SAFE_INTEGER) {
            throw new IllegalArgumentException("sequence out of range [0, " + MAX_SAFE_INTEGER + "]: " + sequence);
        }
        if (configVersion < 0) {
            throw new IllegalArgumentException("configVersion must be >= 0: " + configVersion);
        }
        // value 可为 null（无效采集省略），但若非 null 必须 decimal-string（由 valueEncoding 约束）
    }

    private static void requireFixed(String name, String actual, String expected) {
        if (actual == null || !actual.equals(expected)) {
            throw new IllegalArgumentException(name + " must be \"" + expected + "\" but was: " + actual);
        }
    }

    private static void requireNonBlank(String name, String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " required (non-blank)");
        }
    }
}
