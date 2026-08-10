package com.basiclab.iot.device.service.event;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import javax.sql.DataSource;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * TD-001 §6.2 + ADR-015：{@link CollectorWorkloadImpactPort} 的 JDBC 实现
 * （collector_workload_binding_projection 可变投影表，由 iot-device 发布单
 * 状态机同事务 upsert，ADR-015 1.1.0 Accepted，不依赖 iot-node 事件同步）。
 * 查询 lifecycle_status='ACTIVE' 的 workload_id 列表；空集合法（绝不返回 null）。
 * 投影表 DDL 见 V004，由 ADR-013 runner 受控落库；
 * 落库前本 bean 虽装配但不会被协调器调用——PowerModelEventWiringConfiguration
 * 的 @ConditionalOnBean 要求四端口齐备才填充处理器注册表，当前 Release 端口
 * 未实现，注册表仍空，事件按缺失处理器进 DLQ（有持久证据，非静默丢弃）。
 * 本类不创建任何数据库对象。
 */
@Repository
public class JdbcCollectorWorkloadImpactPort implements CollectorWorkloadImpactPort {

    private static final String RESOLVE_ACTIVE_WORKLOADS_SQL =
            "SELECT workload_id FROM public.collector_workload_binding_projection"
                    + " WHERE tenant_id = :tenantId"
                    + " AND product_id = :productId"
                    + " AND lifecycle_status = 'ACTIVE'"
                    + " ORDER BY workload_id";

    private final NamedParameterJdbcTemplate jdbc;

    public JdbcCollectorWorkloadImpactPort(DataSource dataSource) {
        this.jdbc = new NamedParameterJdbcTemplate(Objects.requireNonNull(dataSource, "dataSource"));
    }

    @Override
    public List<String> resolveActiveWorkloads(long tenantId, long productId) {
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("tenantId", tenantId)
                .addValue("productId", productId);
        List<String> workloads = jdbc.queryForList(RESOLVE_ACTIVE_WORKLOADS_SQL, params, String.class);
        return workloads == null ? Collections.<String>emptyList() : workloads;
    }
}
