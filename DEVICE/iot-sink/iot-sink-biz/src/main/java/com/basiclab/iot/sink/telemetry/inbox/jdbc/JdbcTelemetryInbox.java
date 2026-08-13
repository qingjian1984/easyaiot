package com.basiclab.iot.sink.telemetry.inbox.jdbc;

import com.basiclab.iot.sink.telemetry.inbox.InboxEnvelope;
import com.basiclab.iot.sink.telemetry.inbox.InboxReceiveResult;
import com.basiclab.iot.sink.telemetry.inbox.TelemetryInboxPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;
import java.util.ArrayList;
import java.util.List;

/**
 * TD-003 §10 中心 Inbox PostgreSQL 实现。
 *
 * <p>两层幂等：
 * <ul>
 *   <li>第一层：UNIQUE(tenant_id, message_id) + ON CONFLICT DO NOTHING</li>
 *   <li>第二层：content_sha256 校验（同 messageId 不同 hash → COLLISION）</li>
 * </ul>
 * canonical bytes 原字节落 payload BYTEA，不重新序列化。
 */
public final class JdbcTelemetryInbox implements TelemetryInboxPort {

    private static final Logger log = LoggerFactory.getLogger(JdbcTelemetryInbox.class);

    private static final String INSERT_SQL = "INSERT INTO iot_sink.telemetry_inbox"
            + "(message_id, message_id_wire, request_id, tenant_id, site_code,"
            + " device_identification, property_code, payload, content_sha256,"
            + " collected_at_ms, sequence_no, source, config_version,"
            + " projection_state, received_at_ms, updated_at_ms)"
            + " VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?, 'RECEIVED', ?, ?)"
            + " ON CONFLICT (tenant_id, message_id) DO NOTHING";

    private static final String SELECT_HASH_SQL = "SELECT content_sha256"
            + " FROM iot_sink.telemetry_inbox WHERE tenant_id = ? AND message_id = ?";

    private final JdbcTemplate jdbc;

    public JdbcTelemetryInbox(DataSource dataSource) {
        this.jdbc = new JdbcTemplate(dataSource);
    }

    @Override
    public InboxReceiveResult receiveEnvelopes(List<InboxEnvelope> envelopes) {
        if (envelopes == null || envelopes.isEmpty()) {
            return new InboxReceiveResult.Received(List.of());
        }
        List<String> received = new ArrayList<>();
        List<String> duplicates = new ArrayList<>();
        List<String> collisions = new ArrayList<>();
        long now = System.currentTimeMillis();

        for (InboxEnvelope env : envelopes) {
            int rows = jdbc.update(INSERT_SQL,
                    env.messageId(),
                    env.messageId(),
                    env.requestId(),
                    Long.parseLong(env.tenantId()),
                    env.siteCode(),
                    env.deviceIdentification(),
                    env.propertyCode(),
                    env.canonicalBytes(),
                    env.contentSha256(),
                    env.collectedAtMs(),
                    env.sequence(),
                    env.source(),
                    env.configVersion(),
                    now,
                    now);

            if (rows > 0) {
                received.add(env.messageId());
            } else {
                String existingHash = queryExistingHash(env.tenantId(), env.messageId());
                if (existingHash != null && existingHash.equals(env.contentSha256())) {
                    duplicates.add(env.messageId());
                } else {
                    collisions.add(env.messageId());
                    log.warn("INBOX_COLLISION: messageId={} existing={} incoming={}",
                            env.messageId(), existingHash, env.contentSha256());
                }
            }
        }
        return new InboxReceiveResult.Received(received);
    }

    private String queryExistingHash(String tenantId, String messageId) {
        try {
            return jdbc.queryForObject(SELECT_HASH_SQL, String.class,
                    Long.parseLong(tenantId), messageId);
        } catch (Exception e) {
            return null;
        }
    }
}
