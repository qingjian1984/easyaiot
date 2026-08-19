package com.basiclab.iot.sink.telemetry.inbox.jdbc;

import com.basiclab.iot.sink.telemetry.store.TelemetryStorePort;
import com.basiclab.iot.sink.telemetry.store.TelemetrySample;
import com.basiclab.iot.sink.telemetry.store.WriteBatchResult;
import com.basiclab.iot.sink.telemetry.store.WriteItemResult;
import com.basiclab.iot.sink.telemetry.store.WriteStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import javax.sql.DataSource;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * TD-003 §4/§10 投影编排器：周期扫描 Inbox RECEIVED → TelemetryStore → COMPLETED/DEAD_LETTER。
 *
 * <p>状态机：
 * <ul>
 *   <li>RECEIVED → PROJECTING（claim 租约，attempts+1）</li>
 *   <li>PROJECTING + STORED → COMPLETED</li>
 *   <li>PROJECTING + DUPLICATE → COMPLETED（幂等）</li>
 *   <li>PROJECTING + FAILED → RETRY_WAIT（退避；attempts ≥ 上限 → DEAD_LETTER）</li>
 * </ul>
 *
 * <p>投影与 Inbox 接收不在同一事务（TD-003 §4 "投影不得与 Inbox 接收共事务"）。
 */
public final class TelemetryProjectionOrchestrator {

    private static final Logger log = LoggerFactory.getLogger(TelemetryProjectionOrchestrator.class);

    private static final String CLAIM_SQL = "UPDATE iot_sink.telemetry_inbox"
            + " SET projection_state = 'PROJECTING', projection_lease_until = ?,"
            + " projection_attempts = projection_attempts + 1, updated_at_ms = ?"
            + " WHERE id IN ("
            + "   SELECT id FROM iot_sink.telemetry_inbox"
            + "   WHERE projection_state = 'RECEIVED'"
            + "   ORDER BY received_at_ms ASC LIMIT ?"
            + " FOR UPDATE SKIP LOCKED)"
            + " RETURNING id";

    private static final String RECLAIM_SQL = "UPDATE iot_sink.telemetry_inbox"
            + " SET projection_state = 'RECEIVED', updated_at_ms = ?"
            + " WHERE projection_state = 'PROJECTING' AND projection_lease_until < ?";

    private static final String SELECT_PROJECTING_SQL = "SELECT"
            + " id, message_id, request_id, tenant_id, site_code, device_identification,"
            + " property_code, payload, content_sha256, collected_at_ms, sequence_no,"
            + " source, config_version, projection_attempts"
            + " FROM iot_sink.telemetry_inbox WHERE projection_state = 'PROJECTING'"
            + " ORDER BY received_at_ms ASC LIMIT ?";

    private static final String COMPLETE_SQL = "UPDATE iot_sink.telemetry_inbox"
            + " SET projection_state = 'COMPLETED', projected_at_ms = ?, updated_at_ms = ?"
            + " WHERE id = ?";

    private static final String DEAD_LETTER_SQL = "UPDATE iot_sink.telemetry_inbox"
            + " SET projection_state = 'PROJECTION_DEAD_LETTER',"
            + " last_projection_error = ?, updated_at_ms = ?"
            + " WHERE id = ?";

    private static final String RETRY_SQL = "UPDATE iot_sink.telemetry_inbox"
            + " SET projection_state = 'RECEIVED', last_projection_error = ?,"
            + " next_projection_at_ms = ?, updated_at_ms = ?"
            + " WHERE id = ?";

    private static final int MAX_ATTEMPTS = 5;
    private static final long LEASE_MS = 30_000L;
    private static final long RETRY_BASE_MS = 2000L;
    private static final Set<String> STABLE_ERROR_CODES = Set.of(
            "STORE_SAMPLE_INVALID", "STORE_VALUE_INVALID", "STORE_UNAVAILABLE",
            "STORE_STATE_CORRUPT", "MESSAGE_ID_COLLISION", "STORE_BATCH_TOO_LARGE",
            "STORE_CONTRACT_INVALID");

    private final JdbcTemplate jdbc;
    private final TelemetryStorePort store;
    private final ScheduledExecutorService scheduler;
    private final AtomicBoolean running = new AtomicBoolean(false);

    public TelemetryProjectionOrchestrator(DataSource dataSource, TelemetryStorePort store) {
        this.jdbc = new JdbcTemplate(dataSource);
        this.store = store;
        this.scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "telemetry-projection");
            t.setDaemon(true);
            return t;
        });
    }

    public void start() {
        if (running.compareAndSet(false, true)) {
            scheduler.scheduleWithFixedDelay(this::projectionCycle, 2000, 500, TimeUnit.MILLISECONDS);
            log.info("projection orchestrator started: interval=500ms leaseMs={} maxAttempts={}",
                    LEASE_MS, MAX_ATTEMPTS);
        }
    }

    public void stop() {
        if (running.compareAndSet(true, false)) {
            scheduler.shutdown();
            try {
                scheduler.awaitTermination(5, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }

    private void projectionCycle() {
        try {
            reclaimExpiredLeases();
            claimBatch();
            projectBatch();
        } catch (Exception e) {
            log.warn("projection cycle error: code=STORE_UNAVAILABLE");
        }
    }

    private void reclaimExpiredLeases() {
        long now = System.currentTimeMillis();
        jdbc.update(RECLAIM_SQL, now, now);
    }

    private void claimBatch() {
        long now = System.currentTimeMillis();
        long deadline = now + LEASE_MS;
        jdbc.queryForRowSet(CLAIM_SQL, deadline, now, 50);
    }

    private void projectBatch() {
        List<ProjectionRow> rows = jdbc.query(SELECT_PROJECTING_SQL, ROW_MAPPER, 50);
        if (rows.isEmpty()) {
            return;
        }
        long now = System.currentTimeMillis();
        List<TelemetrySample> samples = rows.stream()
                .map(row -> new TelemetrySample(
                        row.messageId, row.requestId, String.valueOf(row.tenantId),
                        row.siteCode, row.deviceIdentification, row.propertyCode,
                        row.payload, row.contentSha256,
                        row.collectedAtMs, row.sequenceNo,
                        row.source, row.configVersion))
                .toList();
        WriteBatchResult result;
        try {
            result = store.appendBatch(samples);
        } catch (Exception error) {
            for (ProjectionRow row : rows) {
                handleFailure(row, now, "STORE_UNAVAILABLE");
            }
            return;
        }
        if (result == null || result.items() == null || result.items().size() != rows.size()) {
            for (ProjectionRow row : rows) {
                handleFailure(row, now, "STORE_CONTRACT_INVALID");
            }
            return;
        }
        for (int i = 0; i < rows.size(); i++) {
            ProjectionRow row = rows.get(i);
            WriteItemResult item = result.items().get(i);
            if (item == null || !java.util.Objects.equals(row.messageId, item.messageId())
                    || item.status() == null) {
                for (ProjectionRow unclosed : rows) {
                    handleFailure(unclosed, now, "STORE_CONTRACT_INVALID");
                }
                return;
            }
        }
        for (int i = 0; i < rows.size(); i++) {
            ProjectionRow row = rows.get(i);
            WriteItemResult item = result.items().get(i);
            if (item.status() == WriteStatus.STORED || item.status() == WriteStatus.DUPLICATE) {
                completeSafely(row, now);
            } else if (item.status() == WriteStatus.RETRYABLE_FAILED) {
                handleFailure(row, now, stableCodeOrContractInvalid(item.errorCode()));
            } else if (item.status() == WriteStatus.FINAL_FAILED) {
                handleFinalFailure(row, now, stableCodeOrContractInvalid(item.errorCode()));
            } else {
                for (ProjectionRow unclosed : rows) {
                    handleFailure(unclosed, now, "STORE_CONTRACT_INVALID");
                }
                return;
            }
        }
    }

    private void handleFailure(ProjectionRow row, long now, String error) {
        if (row.projectionAttempts >= MAX_ATTEMPTS) {
            handleFinalFailure(row, now, error);
        } else {
            long retryAt = now + RETRY_BASE_MS * (1L << Math.min(row.projectionAttempts, 10));
            try {
                jdbc.update(RETRY_SQL, stableCodeOrContractInvalid(error), retryAt, now, row.id);
                log.debug("projection retry: messageId={} attempts={} retryAt={}",
                        row.messageId, row.projectionAttempts, retryAt);
            } catch (Exception updateError) {
                log.warn("projection retry update failed: messageId={} code=STORE_UNAVAILABLE",
                        row.messageId);
            }
        }
    }

    private void completeSafely(ProjectionRow row, long now) {
        try {
            jdbc.update(COMPLETE_SQL, now, now, row.id);
        } catch (Exception updateError) {
            log.warn("projection complete update failed: messageId={} code=STORE_UNAVAILABLE",
                    row.messageId);
            handleFailure(row, now, "STORE_UNAVAILABLE");
        }
    }

    private void handleFinalFailure(ProjectionRow row, long now, String error) {
        String stableCode = stableCodeOrContractInvalid(error);
        try {
            jdbc.update(DEAD_LETTER_SQL, stableCode, now, row.id);
            log.warn("projection DEAD_LETTER: messageId={} code={} attempts={}",
                    row.messageId, stableCode, row.projectionAttempts);
        } catch (Exception updateError) {
            log.warn("projection dead-letter update failed: messageId={} code=STORE_UNAVAILABLE",
                    row.messageId);
        }
    }

    private static String stableCodeOrContractInvalid(String errorCode) {
        return errorCode != null && STABLE_ERROR_CODES.contains(errorCode)
                ? errorCode : "STORE_CONTRACT_INVALID";
    }

    private static final RowMapper<ProjectionRow> ROW_MAPPER = (rs, rowNum) -> new ProjectionRow(
            rs.getLong("id"),
            rs.getString("message_id"),
            rs.getString("request_id"),
            rs.getLong("tenant_id"),
            rs.getString("site_code"),
            rs.getString("device_identification"),
            rs.getString("property_code"),
            rs.getBytes("payload"),
            rs.getString("content_sha256"),
            rs.getLong("collected_at_ms"),
            rs.getLong("sequence_no"),
            rs.getString("source"),
            rs.getLong("config_version"),
            rs.getInt("projection_attempts"));

    private record ProjectionRow(
            long id, String messageId, String requestId, long tenantId,
            String siteCode, String deviceIdentification, String propertyCode,
            byte[] payload, String contentSha256,
            long collectedAtMs, long sequenceNo,
            String source, long configVersion, int projectionAttempts) {
    }
}
