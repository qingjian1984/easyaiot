package com.basiclab.iot.sink.telemetry.store.jdbc;

import com.basiclab.iot.sink.telemetry.inbox.InboxEnvelope;
import com.basiclab.iot.sink.telemetry.store.TelemetryStorePort;
import com.basiclab.iot.sink.telemetry.store.TelemetryValueCodec;
import com.basiclab.iot.sink.telemetry.store.WriteResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;

/**
 * TD-003 §13 TelemetryStore standard adapter（PostgreSQL 月分区）。
 *
 * <p>幂等键：telemetry_sample_identity(tenant_id, message_id, content_sha256)。
 * 值列 NUMERIC（不经 Double）；时间精度毫秒。
 */
public final class JdbcTelemetryStore implements TelemetryStorePort {

    private static final Logger log = LoggerFactory.getLogger(JdbcTelemetryStore.class);

    private static final String INSERT_SQL = "INSERT INTO iot_sink.telemetry_sample"
            + "(tenant_id, message_id, content_sha256, site_code, device_identification,"
            + " property_code, value_numeric, collected_at_ms, sequence_no, source, config_version)"
            + " VALUES (?,?,?,?,?,?,?,?,?,?,?)"
            + " ON CONFLICT (tenant_id, message_id, content_sha256) DO NOTHING";

    private final JdbcTemplate jdbc;

    public JdbcTelemetryStore(DataSource dataSource) {
        this.jdbc = new JdbcTemplate(dataSource);
    }

    @Override
    public WriteResult writeSample(InboxEnvelope envelope) {
        try {
            java.math.BigDecimal value = TelemetryValueCodec.parseDecimalValue(envelope.canonicalBytes());
            int rows = jdbc.update(INSERT_SQL,
                    Long.parseLong(envelope.tenantId()),
                    envelope.messageId(),
                    envelope.contentSha256(),
                    envelope.siteCode(),
                    envelope.deviceIdentification(),
                    envelope.propertyCode(),
                    value,
                    envelope.collectedAtMs(),
                    envelope.sequence(),
                    envelope.source(),
                    envelope.configVersion());
            return rows > 0 ? WriteResult.STORED : WriteResult.DUPLICATE;
        } catch (NumberFormatException e) {
            log.error("STORE_FAILED: invalid tenantId={} messageId={}",
                    envelope.tenantId(), envelope.messageId());
            return WriteResult.FAILED;
        } catch (Exception e) {
            log.error("STORE_FAILED: messageId={} error={}",
                    envelope.messageId(), e.getMessage());
            return WriteResult.FAILED;
        }
    }
}
