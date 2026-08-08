package com.basiclab.iot.device.service.event;

import com.basiclab.iot.common.utils.SnowflakeIdUtil;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import javax.sql.DataSource;
import java.util.Objects;

/**
 * TD-001 §6.2：{@link PowerModelCoordinationAuditPort} 的 JDBC 实现
 * （power_model_coordination_audit，V003 经 ADR-013 runner 落库）。
 * 追加写语义由数据库触发器强制（UPDATE/DELETE 拒绝）；同
 * (tenant_id, event_id, action) 重复记录 ON CONFLICT DO NOTHING——
 * 重投重试时保留首次处置事实，不覆盖、不膨胀。
 * detail 防御性截断 ≤512 字符（端口合同；处理器已先行有界化）。
 * ID 由应用统一雪花策略赋值，数据库不兜底生成。本类不创建任何数据库对象。
 */
@Repository
public class JdbcPowerModelCoordinationAuditPort implements PowerModelCoordinationAuditPort {

    /** detail 上限（字符），与 DDL VARCHAR(512) 对齐。 */
    static final int MAX_DETAIL_LENGTH = 512;

    private static final String INSERT_AUDIT_SQL =
            "INSERT INTO public.power_model_coordination_audit"
                    + " (id, event_id, tenant_id, event_type, action, detail, occurred_at)"
                    + " VALUES (:id, CAST(:eventId AS uuid), :tenantId, :eventType, :action,"
                    + "         :detail, CURRENT_TIMESTAMP)"
                    + " ON CONFLICT (tenant_id, event_id, action) DO NOTHING";

    private final NamedParameterJdbcTemplate jdbc;

    public JdbcPowerModelCoordinationAuditPort(DataSource dataSource) {
        this.jdbc = new NamedParameterJdbcTemplate(Objects.requireNonNull(dataSource, "dataSource"));
    }

    @Override
    public void record(String eventId, long tenantId, String eventType, String action,
                       String detail) {
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("id", Long.parseLong(SnowflakeIdUtil.nextId()))
                .addValue("eventId", Objects.requireNonNull(eventId, "eventId"))
                .addValue("tenantId", tenantId)
                .addValue("eventType", Objects.requireNonNull(eventType, "eventType"))
                .addValue("action", Objects.requireNonNull(action, "action"))
                .addValue("detail", truncate(Objects.requireNonNull(detail, "detail")));
        jdbc.update(INSERT_AUDIT_SQL, params);
    }

    private static String truncate(String detail) {
        return detail.length() <= MAX_DETAIL_LENGTH ? detail
                : detail.substring(0, MAX_DETAIL_LENGTH);
    }
}
