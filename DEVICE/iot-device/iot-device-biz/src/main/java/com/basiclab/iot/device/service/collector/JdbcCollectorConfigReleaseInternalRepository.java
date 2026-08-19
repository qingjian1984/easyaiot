package com.basiclab.iot.device.service.collector;

import com.basiclab.iot.device.domain.collector.dto.CollectorConfigReleaseObservedStatus;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import javax.sql.DataSource;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** iot_collector_config_release 的 pending/detail/observed JDBC 实现。 */
@Repository
public class JdbcCollectorConfigReleaseInternalRepository
        implements CollectorConfigReleaseInternalRepository {

    private static final String PENDING_SQL =
            "SELECT r.id, r.tenant_id, r.node_id, r.workload_id, r.config_version,"
                    + " r.schema_version, r.canonicalization_version, r.payload_sha256,"
                    + " r.canonical_length_bytes, r.published_at"
                    + " FROM public.iot_collector_config_release r"
                    + " JOIN public.collector_workload_binding_projection p"
                    + " ON p.tenant_id=r.tenant_id AND p.workload_id=r.workload_id"
                    + " AND p.node_id=r.node_id AND p.site_id=r.site_id"
                    + " AND p.site_code=r.site_code AND p.release_id=r.id"
                    + " AND p.config_version=r.config_version"
                    + " WHERE r.status='PUBLISHED' AND p.lifecycle_status='ACTIVE'"
                    + " ORDER BY r.published_at ASC, r.id ASC LIMIT :limit";

    private static final String DETAIL_SQL =
            "SELECT r.id, r.tenant_id, r.node_id, r.workload_id, r.config_version, r.schema_version,"
                    + " canonicalization_version, payload_canonical, payload_sha256,"
                    + " r.canonical_length_bytes, r.published_at"
                    + " FROM public.iot_collector_config_release r"
                    + " JOIN public.collector_workload_binding_projection p"
                    + " ON p.tenant_id=r.tenant_id AND p.workload_id=r.workload_id"
                    + " AND p.node_id=r.node_id AND p.site_id=r.site_id"
                    + " AND p.site_code=r.site_code AND p.release_id=r.id"
                    + " AND p.config_version=r.config_version"
                    + " WHERE r.id=:releaseId AND r.status='PUBLISHED'"
                    + " AND p.lifecycle_status='ACTIVE'";

    private static final String LOCK_SQL =
            "SELECT tenant_id, node_id, workload_id, config_version, payload_sha256,"
                    + " status, row_version FROM public.iot_collector_config_release"
                    + " WHERE id=:releaseId FOR UPDATE";

    private static final String APPLY_SQL =
            "UPDATE public.iot_collector_config_release SET status='APPLIED',"
                    + " applied_version=:configVersion, applied_at=CURRENT_TIMESTAMP,"
                    + " error_code=NULL, error_detail=NULL, updated_by='iot-node',"
                    + " updated_at=CURRENT_TIMESTAMP, row_version=row_version+1"
                    + " WHERE id=:releaseId AND row_version=:rowVersion AND status='PUBLISHED'";

    private static final String FAIL_SQL =
            "UPDATE public.iot_collector_config_release SET status='FAILED',"
                    + " error_code=:errorCode, error_detail=:errorDetail,"
                    + " updated_by='iot-node', updated_at=CURRENT_TIMESTAMP,"
                    + " row_version=row_version+1"
                    + " WHERE id=:releaseId AND row_version=:rowVersion AND status='PUBLISHED'";

    private final NamedParameterJdbcTemplate jdbc;
    private final TransactionTemplate transaction;

    public JdbcCollectorConfigReleaseInternalRepository(
            DataSource dataSource, PlatformTransactionManager transactionManager) {
        this.jdbc = new NamedParameterJdbcTemplate(Objects.requireNonNull(dataSource, "dataSource"));
        this.transaction = new TransactionTemplate(Objects.requireNonNull(
                transactionManager, "transactionManager"));
    }

    @Override
    public List<ReleaseRecord> findPending(int limit) {
        return jdbc.query(PENDING_SQL, new MapSqlParameterSource().addValue("limit", limit),
                (rs, rowNum) -> releaseRecord(rs, false));
    }

    @Override
    public Optional<ReleaseRecord> findById(long releaseId) {
        List<ReleaseRecord> rows = jdbc.query(DETAIL_SQL,
                new MapSqlParameterSource().addValue("releaseId", releaseId),
                (rs, rowNum) -> releaseRecord(rs, true));
        return rows.stream().findFirst();
    }

    @Override
    public ObservedCasResult observe(ObservedRecord observed) {
        ObservedCasResult result = transaction.execute(status -> observeInTransaction(observed));
        if (result == null) {
            throw new IllegalStateException("collector observed transaction returned no result");
        }
        return result;
    }

    private ObservedCasResult observeInTransaction(ObservedRecord observed) {
        List<ObservedRow> rows = jdbc.query(LOCK_SQL,
                new MapSqlParameterSource().addValue("releaseId", observed.releaseId()),
                (rs, rowNum) -> new ObservedRow(rs.getLong("tenant_id"),
                        rs.getLong("node_id"), rs.getString("workload_id"),
                        rs.getLong("config_version"), rs.getString("payload_sha256").trim(),
                        rs.getString("status"), rs.getLong("row_version")));
        if (rows.size() != 1) {
            return new ObservedCasResult(Outcome.MISMATCH);
        }
        ObservedRow current = rows.get(0);
        if (!sameIdentity(current, observed)) {
            return new ObservedCasResult(Outcome.MISMATCH);
        }

        if (observed.status() == CollectorConfigReleaseObservedStatus.AGENT_ACCEPTED) {
            return "PUBLISHED".equals(current.status)
                    ? new ObservedCasResult(Outcome.AGENT_ACCEPTED)
                    : new ObservedCasResult(Outcome.STALE);
        }
        if (observed.status().name().equals(current.status)
                && ("APPLIED".equals(current.status) || "FAILED".equals(current.status))) {
            return new ObservedCasResult(Outcome.IDEMPOTENT);
        }
        if (!"PUBLISHED".equals(current.status)) {
            return new ObservedCasResult(Outcome.STALE);
        }

        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("releaseId", observed.releaseId())
                .addValue("rowVersion", current.rowVersion)
                .addValue("configVersion", observed.configVersion())
                .addValue("errorCode", observed.errorCode())
                .addValue("errorDetail", observed.errorDetailSanitized());
        String sql = observed.status() == CollectorConfigReleaseObservedStatus.APPLIED
                ? APPLY_SQL : FAIL_SQL;
        int updated = jdbc.update(sql, params);
        if (updated != 1) {
            return new ObservedCasResult(Outcome.STALE);
        }
        return new ObservedCasResult(observed.status() == CollectorConfigReleaseObservedStatus.APPLIED
                ? Outcome.APPLIED : Outcome.FAILED);
    }

    private static boolean sameIdentity(ObservedRow current, ObservedRecord observed) {
        return current.tenantId == observed.tenantId()
                && current.nodeId == observed.nodeId()
                && current.configVersion == observed.configVersion()
                && current.workloadId.equals(observed.workloadId())
                && current.payloadSha256.equals(observed.payloadSha256());
    }

    private static ReleaseRecord releaseRecord(java.sql.ResultSet rs, boolean includeCanonical)
            throws java.sql.SQLException {
        return new ReleaseRecord(rs.getLong("id"), rs.getLong("tenant_id"),
                rs.getLong("node_id"), rs.getString("workload_id"),
                rs.getLong("config_version"), rs.getString("schema_version"),
                rs.getString("canonicalization_version"),
                includeCanonical ? rs.getString("payload_canonical") : null,
                rs.getString("payload_sha256").trim(), rs.getLong("canonical_length_bytes"),
                rs.getString("published_at"));
    }

    private static final class ObservedRow {
        private final long tenantId;
        private final long nodeId;
        private final String workloadId;
        private final long configVersion;
        private final String payloadSha256;
        private final String status;
        private final long rowVersion;

        private ObservedRow(long tenantId, long nodeId, String workloadId,
                            long configVersion, String payloadSha256,
                            String status, long rowVersion) {
            this.tenantId = tenantId;
            this.nodeId = nodeId;
            this.workloadId = workloadId;
            this.configVersion = configVersion;
            this.payloadSha256 = payloadSha256;
            this.status = status;
            this.rowVersion = rowVersion;
        }
    }
}
