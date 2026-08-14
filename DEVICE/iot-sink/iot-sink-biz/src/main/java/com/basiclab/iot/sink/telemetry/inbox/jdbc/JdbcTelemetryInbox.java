package com.basiclab.iot.sink.telemetry.inbox.jdbc;

import com.basiclab.iot.sink.telemetry.inbox.InboxEnvelope;
import com.basiclab.iot.sink.telemetry.inbox.InboxReceiveResult;
import com.basiclab.iot.sink.telemetry.inbox.TelemetryInboxPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import javax.sql.DataSource;
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

    private static final String SELECT_EXISTING_SQL = "SELECT content_sha256, request_id, message_id_wire,"
            + " site_code, device_identification, property_code, received_at_ms"
            + " FROM iot_sink.telemetry_inbox WHERE tenant_id = ? AND message_id = ?";

    private final JdbcTemplate jdbc;
    private final TransactionTemplate transactionTemplate;

    public JdbcTelemetryInbox(DataSource dataSource) {
        this.jdbc = new JdbcTemplate(dataSource);
        this.transactionTemplate = new TransactionTemplate(new DataSourceTransactionManager(dataSource));
        this.transactionTemplate.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    @Override
    public InboxReceiveResult receiveEnvelopes(List<InboxEnvelope> envelopes) {
        if (envelopes == null) {
            throw new NullPointerException("envelopes");
        }
        List<InboxReceiveResult.Item> items = new java.util.ArrayList<>(envelopes.size());
        for (int i = 0; i < envelopes.size(); i++) {
            final int inputIndex = i;
            InboxEnvelope env = envelopes.get(i);
            if (env == null) {
                throw new NullPointerException("envelopes[" + i + "]");
            }
            items.add(transactionTemplate.execute(status -> receiveOne(env, inputIndex)));
        }
        return new InboxReceiveResult.Batch(items);
    }

    private InboxReceiveResult.Item receiveOne(InboxEnvelope env, int inputIndex) {
        long now = System.currentTimeMillis();
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
            return new InboxReceiveResult.Item(inputIndex, env.messageId(), env.requestId(),
                    InboxReceiveResult.Status.ACCEPTED_DURABLE, now);
        }

        ExistingInbox existing = queryExisting(env.tenantId(), env.messageId());
        if (existing == null) {
            throw new IllegalStateException("Inbox conflict row disappeared: messageId=" + env.messageId());
        }
        if (existing.matches(env)) {
            return new InboxReceiveResult.Item(inputIndex, env.messageId(), env.requestId(),
                    InboxReceiveResult.Status.DUPLICATE, existing.receivedAtMs());
        }
        log.warn("INBOX_COLLISION: messageId={} reason=existing_identity_or_hash_diff", env.messageId());
        return new InboxReceiveResult.Item(inputIndex, env.messageId(), env.requestId(),
                InboxReceiveResult.Status.MESSAGE_ID_COLLISION, null);
    }

    private ExistingInbox queryExisting(String tenantId, String messageId) {
        List<ExistingInbox> rows = jdbc.query(SELECT_EXISTING_SQL, EXISTING_ROW_MAPPER,
                Long.parseLong(tenantId), messageId);
        return rows.isEmpty() ? null : rows.get(0);
    }

    private static final RowMapper<ExistingInbox> EXISTING_ROW_MAPPER = (rs, rowNum) -> new ExistingInbox(
            rs.getString("content_sha256"),
            rs.getString("request_id"),
            rs.getString("message_id_wire"),
            rs.getString("site_code"),
            rs.getString("device_identification"),
            rs.getString("property_code"),
            rs.getLong("received_at_ms"));

    private record ExistingInbox(
            String contentSha256,
            String requestId,
            String messageIdWire,
            String siteCode,
            String deviceIdentification,
            String propertyCode,
            long receivedAtMs) {

        boolean matches(InboxEnvelope env) {
            return java.util.Objects.equals(contentSha256, env.contentSha256())
                    && java.util.Objects.equals(requestId, env.requestId())
                    && java.util.Objects.equals(messageIdWire, env.messageId())
                    && java.util.Objects.equals(siteCode, env.siteCode())
                    && java.util.Objects.equals(deviceIdentification, env.deviceIdentification())
                    && java.util.Objects.equals(propertyCode, env.propertyCode());
        }
    }
}
