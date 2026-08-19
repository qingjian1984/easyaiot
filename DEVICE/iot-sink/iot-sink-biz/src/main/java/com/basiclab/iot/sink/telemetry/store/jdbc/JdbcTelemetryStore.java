package com.basiclab.iot.sink.telemetry.store.jdbc;

import com.basiclab.iot.sink.telemetry.store.TelemetryStorePort;
import com.basiclab.iot.sink.telemetry.store.TelemetrySample;
import com.basiclab.iot.sink.telemetry.store.TelemetryValueCodec;
import com.basiclab.iot.sink.telemetry.store.WriteBatchResult;
import com.basiclab.iot.sink.telemetry.store.WriteItemResult;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;
import java.math.BigDecimal;
import java.util.List;

/**
 * TD-003 §13 TelemetryStore standard adapter（PostgreSQL 月分区）。
 *
 * <p>幂等键：telemetry_sample_identity(tenant_id, message_id, content_sha256)。
 * 值列 NUMERIC（不经 Double）；时间精度毫秒。
 */
public final class JdbcTelemetryStore implements TelemetryStorePort {

    private static final Logger log = LoggerFactory.getLogger(JdbcTelemetryStore.class);
    private static final ObjectMapper JSON = new ObjectMapper();

    private static final String INSERT_SQL = "INSERT INTO iot_sink.telemetry_sample"
            + "(tenant_id, message_id, content_sha256, site_code, device_identification,"
            + " property_code, value_numeric, collected_at_ms, sequence_no, source, config_version)"
            + " VALUES (?,?,?,?,?,?,?,?,?,?,?)";
    private static final String EXISTING_HASH_SQL = "SELECT DISTINCT content_sha256"
            + " FROM iot_sink.telemetry_sample WHERE tenant_id = ? AND message_id = ?"
            + " ORDER BY content_sha256";

    private final JdbcTemplate jdbc;

    public JdbcTelemetryStore(DataSource dataSource) {
        this.jdbc = new JdbcTemplate(dataSource);
    }

    @Override
    public WriteBatchResult appendBatch(List<TelemetrySample> samples) {
        if (samples == null) {
            throw new IllegalArgumentException("samples are required");
        }
        if (samples.isEmpty()) {
            return WriteBatchResult.empty();
        }
        if (samples.size() > 500) {
            return new WriteBatchResult(samples.stream()
                    .map(sample -> identifiable(sample)
                            ? WriteItemResult.finalFailed(sample.messageId(), "STORE_BATCH_TOO_LARGE")
                            : WriteItemResult.finalFailed(sample == null ? null : sample.messageId(),
                            "STORE_SAMPLE_INVALID"))
                    .toList());
        }
        java.util.List<WriteItemResult> results = new java.util.ArrayList<>(samples.size());
        for (TelemetrySample sample : samples) {
            results.add(writeOne(sample));
        }
        return new WriteBatchResult(results);
    }

    private WriteItemResult writeOne(TelemetrySample sample) {
        String messageId = sample == null ? null : sample.messageId();
        if (sample == null || !sample.isValid()) {
            return WriteItemResult.finalFailed(messageId, "STORE_SAMPLE_INVALID");
        }
        final long tenantId;
        try {
            tenantId = Long.parseLong(sample.tenantId());
        } catch (NumberFormatException error) {
            return WriteItemResult.finalFailed(messageId, "STORE_SAMPLE_INVALID");
        }
        if (valueIsDeterministicallyInvalid(sample.canonicalBytes())) {
            return WriteItemResult.finalFailed(messageId, "STORE_VALUE_INVALID");
        }
        List<String> existingHashes;
        try {
            existingHashes = jdbc.query(EXISTING_HASH_SQL,
                    (rs, rowNum) -> rs.getString(1), tenantId, messageId);
        } catch (Exception error) {
            log.warn("STORE_WRITE_FAILED: messageId={} code=STORE_UNAVAILABLE", messageId);
            return WriteItemResult.retryable(messageId, "STORE_UNAVAILABLE");
        }
        WriteItemResult existingResult = classifyExisting(sample, existingHashes);
        if (existingResult != null) {
            return existingResult;
        }
        try {
            BigDecimal value = TelemetryValueCodec.parseDecimalValue(sample.canonicalBytes());
            jdbc.update(INSERT_SQL, tenantId, messageId, sample.contentSha256(), sample.siteCode(),
                    sample.deviceIdentification(), sample.propertyCode(), value, sample.collectedAtMs(),
                    sample.sequence(), sample.source(), sample.configVersion());
            return WriteItemResult.stored(messageId);
        } catch (org.springframework.dao.DataIntegrityViolationException error) {
            // A concurrent writer may have won the identity race; re-read the
            // existing hash without exposing SQL/exception details.
            try {
                List<String> concurrentHashes = jdbc.query(EXISTING_HASH_SQL,
                        (rs, rowNum) -> rs.getString(1), tenantId, messageId);
                WriteItemResult concurrentResult = classifyExisting(sample, concurrentHashes);
                if (concurrentResult != null) {
                    return concurrentResult;
                }
            } catch (Exception ignored) {
                // Fall through to the stable retry result.
            }
            log.warn("STORE_WRITE_FAILED: messageId={} code=STORE_UNAVAILABLE", messageId);
            return WriteItemResult.retryable(messageId, "STORE_UNAVAILABLE");
        } catch (Exception error) {
            log.warn("STORE_WRITE_FAILED: messageId={} code=STORE_UNAVAILABLE", messageId);
            return WriteItemResult.retryable(messageId, "STORE_UNAVAILABLE");
        }
    }

    private static boolean identifiable(TelemetrySample sample) {
        return sample != null && sample.messageId() != null && !sample.messageId().isBlank();
    }

    private static WriteItemResult classifyExisting(TelemetrySample sample, List<String> hashes) {
        if (hashes.size() > 1) {
            return WriteItemResult.finalFailed(sample.messageId(), "STORE_STATE_CORRUPT");
        }
        if (hashes.size() == 1) {
            return sample.contentSha256().equals(hashes.get(0))
                    ? WriteItemResult.duplicate(sample.messageId())
                    : WriteItemResult.finalFailed(sample.messageId(), "MESSAGE_ID_COLLISION");
        }
        return null;
    }

    /** Missing/null value is a valid quality omission; present but non-decimal value is final invalid input. */
    private static boolean valueIsDeterministicallyInvalid(byte[] canonicalBytes) {
        try {
            JsonNode root = JSON.readTree(canonicalBytes);
            if (root == null || !root.isObject()) {
                return true;
            }
            JsonNode value = root.get("value");
            if (value == null || value.isNull()) {
                return false;
            }
            if (!value.isTextual() && !value.isNumber()) {
                return true;
            }
            new BigDecimal(value.asText());
            return false;
        } catch (Exception error) {
            return true;
        }
    }
}
