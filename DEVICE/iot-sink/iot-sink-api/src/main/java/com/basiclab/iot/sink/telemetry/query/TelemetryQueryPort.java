package com.basiclab.iot.sink.telemetry.query;

import java.util.List;

/**
 * TD-003 §16 遥测查询端口（PRD §4.5）。standard（PG）/ full（TDengine）共用；
 * 页面与业务模块不得感知后端差异。
 *
 * <p>与只写的 {@code TelemetryStorePort} 分离：写入合同已冻结（OPEN03-08A），
 * 本端口只承担读路径。所有实现必须强制 tenant_id 过滤，租户隔离失败即拒绝。</p>
 */
public interface TelemetryQueryPort {

    /** 原始样本分页查询（配额：series ≤10、跨度 ≤31 天、pageSize ≤1000）。 */
    TelemetryRawPage queryRaw(TelemetryRawQuery query);

    /** 粒度聚合查询（MINUTE/HOUR/DAY × MIN/MAX/AVG/SUM/COUNT）。 */
    List<TelemetryAggregatePoint> aggregate(TelemetryAggregateQuery query);

    /** 每序列最新值（实时页轮询）。 */
    List<TelemetryLatestSample> latest(TelemetryLatestQuery query);
}
