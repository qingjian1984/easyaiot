package com.basiclab.iot.device.service.event;

import java.util.List;

/**
 * TD-001 §6.2：受影响 collector workload 解析端口。
 * 实现负责固定解析顺序：product → 活动未软删 device → site → 活动 collector
 * workload binding。解析结果为空集是合法结果（无受影响运行端）。
 * JDBC 实现待 `iot_collector_config_release` 等 TD-001 DDL 经 ADR-013 runner
 * 增链落库后提供；本端口不得返回 null。
 */
public interface CollectorWorkloadImpactPort {

    /**
     * 解析受影响的活动 collector workloadId 列表。
     *
     * @return 活动 workloadId 列表（可空集，不得为 null）
     */
    List<String> resolveActiveWorkloads(long tenantId, long productId);
}
