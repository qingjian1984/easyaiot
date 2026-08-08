package com.basiclab.iot.device.service.event;

import com.basiclab.iot.common.utils.SnowflakeIdUtil;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import javax.sql.DataSource;
import java.util.Objects;

/**
 * TD-001 §6.2：{@link PowerModelTemplateReferencePort} 的 JDBC 实现
 * （power_model_template_reference_mark，V003 经 ADR-013 runner 落库）。
 * 同 (tenant_id, template_code, template_version, to_lifecycle) 键 upsert：
 * 重复事件刷新标记时间与来源事件 ID，行数不膨胀。
 * ID 由应用统一雪花策略赋值（{@link SnowflakeIdUtil}），数据库不兜底生成。
 * 本类不创建任何数据库对象。
 */
@Repository
public class JdbcPowerModelTemplateReferencePort implements PowerModelTemplateReferencePort {

    private static final String UPSERT_MARK_SQL =
            "INSERT INTO public.power_model_template_reference_mark"
                    + " (id, tenant_id, template_code, template_version,"
                    + "  from_lifecycle, to_lifecycle, source_event_id, marked_at)"
                    + " VALUES (:id, :tenantId, :templateCode, :templateVersion,"
                    + "         :fromLifecycle, :toLifecycle, CAST(:sourceEventId AS uuid),"
                    + "         CURRENT_TIMESTAMP)"
                    + " ON CONFLICT (tenant_id, template_code, template_version, to_lifecycle) DO UPDATE"
                    + " SET from_lifecycle = EXCLUDED.from_lifecycle,"
                    + "     source_event_id = EXCLUDED.source_event_id,"
                    + "     marked_at = CURRENT_TIMESTAMP";

    private final NamedParameterJdbcTemplate jdbc;

    public JdbcPowerModelTemplateReferencePort(DataSource dataSource) {
        this.jdbc = new NamedParameterJdbcTemplate(Objects.requireNonNull(dataSource, "dataSource"));
    }

    @Override
    public void markLifecycleReference(long tenantId, String templateCode, String templateVersion,
                                       String fromLifecycle, String toLifecycle,
                                       String sourceEventId) {
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("id", Long.parseLong(SnowflakeIdUtil.nextId()))
                .addValue("tenantId", tenantId)
                .addValue("templateCode", Objects.requireNonNull(templateCode, "templateCode"))
                .addValue("templateVersion", Objects.requireNonNull(templateVersion, "templateVersion"))
                .addValue("fromLifecycle", Objects.requireNonNull(fromLifecycle, "fromLifecycle"))
                .addValue("toLifecycle", Objects.requireNonNull(toLifecycle, "toLifecycle"))
                .addValue("sourceEventId", Objects.requireNonNull(sourceEventId, "sourceEventId"));
        jdbc.update(UPSERT_MARK_SQL, params);
    }
}
