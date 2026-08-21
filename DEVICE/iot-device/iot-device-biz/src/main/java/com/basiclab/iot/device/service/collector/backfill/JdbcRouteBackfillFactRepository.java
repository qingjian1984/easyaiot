package com.basiclab.iot.device.service.collector.backfill;

import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;
import java.util.List;
import java.util.Objects;

/** PostgreSQL read adapter for the immutable release and current projection facts. */
public final class JdbcRouteBackfillFactRepository implements RouteBackfillFactRepository {

    private static final String RELEASE_SQL =
            "SELECT id, tenant_id, site_id, site_code, workload_id, node_id, config_version,"
                    + " schema_version, canonicalization_version, payload_canonical,"
                    + " payload::text AS payload_projection, payload_sha256,"
                    + " canonical_length_bytes, status, product_id"
                    + " FROM public.iot_collector_config_release"
                    + " WHERE tenant_id=? AND workload_id=? AND site_code=? AND config_version=?"
                    + " AND status IN ('PUBLISHED','APPLIED','APPLY_TIMEOUT','ROLLED_BACK')";
    private static final String PROJECTION_SQL =
            "SELECT tenant_id, workload_id, site_id, site_code, node_id, product_id,"
                    + " config_version, release_id, lifecycle_status"
                    + " FROM public.collector_workload_binding_projection"
                    + " WHERE tenant_id=? AND workload_id=?";
    private static final String PRODUCT_SQL =
            "SELECT tenant_id, id, product_identification"
                    + " FROM public.product WHERE tenant_id=? AND id=?";

    private final JdbcTemplate jdbc;

    public JdbcRouteBackfillFactRepository(DataSource dataSource) {
        this(new JdbcTemplate(Objects.requireNonNull(dataSource, "dataSource")));
    }

    public JdbcRouteBackfillFactRepository(JdbcTemplate jdbc) {
        this.jdbc = Objects.requireNonNull(jdbc, "jdbc");
    }

    @Override
    public List<ReleaseFact> findReleaseFacts(long tenantId, String workloadId,
                                              String siteCode, long configVersion) {
        return jdbc.query(RELEASE_SQL, (rs, rowNum) -> new ReleaseFact(
                rs.getLong("id"), rs.getLong("tenant_id"), rs.getLong("site_id"),
                rs.getString("site_code"), rs.getString("workload_id"), rs.getLong("node_id"),
                rs.getLong("config_version"), rs.getString("schema_version"),
                rs.getString("canonicalization_version"), rs.getString("payload_canonical"),
                rs.getString("payload_projection"), rs.getString("payload_sha256"),
                rs.getLong("canonical_length_bytes"), rs.getString("status"),
                rs.getLong("product_id")), tenantId, workloadId, siteCode, configVersion);
    }

    @Override
    public List<ProjectionFact> findProjectionFacts(long tenantId, String workloadId) {
        return jdbc.query(PROJECTION_SQL, (rs, rowNum) -> new ProjectionFact(
                rs.getLong("tenant_id"), rs.getString("workload_id"), rs.getLong("site_id"),
                rs.getString("site_code"), rs.getLong("node_id"), rs.getLong("product_id"),
                rs.getLong("config_version"), rs.getLong("release_id"),
                rs.getString("lifecycle_status")), tenantId, workloadId);
    }

    @Override
    public List<ProductFact> findProductFacts(long tenantId, long productId) {
        return jdbc.query(PRODUCT_SQL, (rs, rowNum) -> new ProductFact(
                rs.getLong("tenant_id"), rs.getLong("id"),
                rs.getString("product_identification")), tenantId, productId);
    }
}
