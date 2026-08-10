package com.basiclab.iot.device.service.power;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import javax.sql.DataSource;
import java.util.Collection;
import java.util.List;
import java.util.Objects;

/**
 * V006 五表的 tenant-safe 只读 Mapper。每个 JOIN 都携带 tenant 条件，
 * 不依赖调用方输入 tenant，也不使用 TenantIgnore。
 */
@Repository
public class JdbcPowerObjectSnapshotMapper implements PowerObjectSnapshotMapper {

    private static final String SELECT_SQL =
            "SELECT d.tenant_id, d.id AS device_id, d.device_identification, d.device_status,"
                    + " asset.id AS asset_id, asset.status AS asset_status,"
                    + " asset.version AS asset_version, assignment.id AS assignment_id,"
                    + " assignment.version AS assignment_version, site.id AS site_id,"
                    + " site.site_code, site.status AS site_status, site.version AS site_version,"
                    + " space.id AS space_id, space.space_code, space.status AS space_status,"
                    + " space.version AS space_version, circuit.id AS circuit_id,"
                    + " circuit.circuit_code, circuit.status AS circuit_status,"
                    + " circuit.version AS circuit_version"
                    + " FROM public.device d"
                    + " LEFT JOIN public.power_device_asset asset"
                    + " ON asset.tenant_id=d.tenant_id AND asset.device_id=d.id"
                    + " LEFT JOIN public.power_device_assignment assignment"
                    + " ON assignment.tenant_id=d.tenant_id AND assignment.device_id=d.id"
                    + " AND assignment.valid_to IS NULL"
                    + " LEFT JOIN public.power_site site"
                    + " ON site.tenant_id=assignment.tenant_id AND site.id=assignment.site_id"
                    + " LEFT JOIN public.power_space_node space"
                    + " ON space.tenant_id=assignment.tenant_id"
                    + " AND space.site_id=assignment.site_id"
                    + " AND space.id=assignment.primary_space_id"
                    + " LEFT JOIN public.power_circuit circuit"
                    + " ON circuit.tenant_id=assignment.tenant_id"
                    + " AND circuit.site_id=assignment.site_id"
                    + " AND circuit.id=assignment.primary_circuit_id"
                    + " WHERE d.tenant_id=:tenantId AND d.deleted=0"
                    + " AND d.device_identification IN (:deviceIdentifications)"
                    + " ORDER BY d.device_identification,d.id";

    private final NamedParameterJdbcTemplate jdbc;

    public JdbcPowerObjectSnapshotMapper(DataSource dataSource) {
        this.jdbc = new NamedParameterJdbcTemplate(Objects.requireNonNull(dataSource, "dataSource"));
    }

    @Override
    public List<PowerObjectSnapshotRow> selectByDeviceIdentifications(
            long tenantId, Collection<String> deviceIdentifications) {
        MapSqlParameterSource parameters = new MapSqlParameterSource()
                .addValue("tenantId", tenantId)
                .addValue("deviceIdentifications", deviceIdentifications);
        return jdbc.query(SELECT_SQL, parameters, (rs, rowNum) -> new PowerObjectSnapshotRow(
                rs.getLong("tenant_id"),
                rs.getLong("device_id"),
                rs.getString("device_identification"),
                rs.getString("device_status"),
                nullableLong(rs, "asset_id"),
                rs.getString("asset_status"),
                nullableLong(rs, "asset_version"),
                nullableLong(rs, "assignment_id"),
                nullableLong(rs, "assignment_version"),
                nullableLong(rs, "site_id"),
                rs.getString("site_code"),
                rs.getString("site_status"),
                nullableLong(rs, "site_version"),
                nullableLong(rs, "space_id"),
                rs.getString("space_code"),
                rs.getString("space_status"),
                nullableLong(rs, "space_version"),
                nullableLong(rs, "circuit_id"),
                rs.getString("circuit_code"),
                rs.getString("circuit_status"),
                nullableLong(rs, "circuit_version")));
    }

    private static Long nullableLong(java.sql.ResultSet rs, String column)
            throws java.sql.SQLException {
        long value = rs.getLong(column);
        return rs.wasNull() ? null : value;
    }
}
