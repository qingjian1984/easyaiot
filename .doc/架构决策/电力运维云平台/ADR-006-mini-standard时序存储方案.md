# ADR-006：standard/full 时序存储抽象与迁移方案

> 状态：Accepted  
> 日期：2026-07-30  
> 决策范围：M1 遥测可靠落地与历史查询  
> 关联：ADR-003、POWER-SPEC-004  
> 产品基线：平台功能计划 1.4.0 / 项目开发宪法 1.4.0

## 背景

电力运维平台不支持 mini。当前部署 profile 仅在 full 启用 TDengine，standard 使用 PostgreSQL。可靠 inbox 在 ACK 后仍必须把数据投影到可查询的时序后端；同时 standard 升级 full 时不能改变业务 API 或丢失历史数据。

## 决策

通过统一 `TelemetryStore` 接口按中心部署档位选择后端：

| 中心档位 | M1 时序后端 | 默认原始数据保留 | 适用边界 |
|---|---|---:|---|
| standard | PostgreSQL 月分区时序表 | 30 天 | 单站点/园区，受控采样量 |
| full | TDengine | 按租户策略，默认 365 天 | 高频、大规模、长期历史 |

collector 所属档位不决定最终存储；数据写入哪个中心，就采用该中心档位后端。standard 不提供 TDengine 例外配置；超过准入上限必须升级 full。

### 稳定接口锚点

`TelemetryStore` 归属 `iot-sink` API/公共契约层，业务模块只依赖以下语义，不依赖 JDBC、TDengine 客户端或表名：

```java
interface TelemetryStore {
    WriteResult appendBatch(List<TelemetrySample> samples);
    Optional<TelemetrySample> latest(TelemetryQuery query);
    PageResult<TelemetrySample> queryRaw(TelemetryQuery query, PageRequest page);
    List<TelemetryAggregate> aggregate(AggregateQuery query);
    StoreWatermark watermark(TenantSiteScope scope);
}
```

- `appendBatch` 按 `(tenantId, messageId)` 幂等；部分失败必须返回逐条结果，不得只返回布尔值。
- 查询必须执行租户/站点权限、质量码过滤、时间范围和最大返回量。
- PostgreSQL 与 TDengine 适配器必须通过同一合同测试；业务层不得出现后端类型判断。

遥测保留与物理清理由独立的管理契约 `TelemetryLifecycleManager` 负责，不向普通业务查询/写入接口暴露任意删除能力。该契约至少提供清理计划预览、按审批计划执行、执行结果和审计；各后端适配器实现分区淘汰或等价生命周期操作。inbox 清理由 inbox repository 自身负责，不属于 `TelemetryStore`。业务服务不得直接操作 JDBC/TDengine 客户端绕过上述契约。

### standard 准入基线

以下是 M1 默认保护值，容量测试只能收紧或经架构评审后放宽：

- 单个 standard 中心每天最多接收 500 万条原始样本。
- 持续写入不超过 100 条/秒，60 秒峰值不超过 500 条/秒。
- 单批写入最多 500 条；原始明细单次查询最多 100,000 条、时间跨度最多 31 天。
- 默认保留 30 天，磁盘达到 70% 告警、85% 停止接收新增高频点表发布。
- 点表发布必须根据点数和采样周期计算预计日样本量；超过任一上限时拒绝发布并提示升级 full，禁止静默降采样。

日样本量统一按 `Σ(启用测点数 × 86400 / 采样周期秒)` 计算，并叠加配置冻结的重传/峰值安全系数。500 万条/日是 M1 standard 的保护性准入值而非通用站点容量承诺：例如 250 个 5 秒测点约 432 万条/日。评审报告中的“200 设备 × 50 点 × 5 秒”约 1.728 亿条/日，属于 full 准入场景。TD-001 必须用点数、周期和报文大小共同生成容量评估并通过联合压测。

### PostgreSQL 模式要求

- 关系表按 `collected_at` 月分区；至少保存 tenant、site、device、property、messageId、sequence、value/原始 JSON、quality、collectedAt、receivedAt 和 configVersion。
- `(tenant_id, message_id)` 唯一；常用查询索引为 `(tenant_id, device_identification, property_code, collected_at DESC)`。
- 投影写入与 inbox 完成状态不得假设跨库事务；PostgreSQL 后端可同库事务提交，TDengine 后端使用可重试状态机和对账。
- 历史 API 只依赖 `TelemetryStore`，WEB/APP 不得感知或直接访问具体后端。
- standard 必须配置单租户点数、最小采样周期、日写入量和磁盘水位；超过能力时拒绝发布配置或要求升级档位，不得静默降采样。

### Inbox 生命周期

- ACK 仅在 inbox 提交后发送。
- 投影 `COMPLETED` 后 inbox 载荷默认保留 7 天用于对账，再按批清理；保留期可按租户合规策略延长。未完成、重试或死信记录不得按期限删除；对账水位落后时自动暂停相应范围的清理，直至完成或经审计处置。
- 投影失败达到上限时执行 ADR-003 的死信、告警和补偿流程。

## 被否决方案

- **standard 可选 TDengine**：会使 standard 出现两种未经统一验收的形态，模糊 full 边界。
- **直接把 SQLite outbox 当历史库**：边缘库缺少中心权限、查询和生命周期语义。
- **Redis 作为历史事实库**：不满足持久性与长期查询要求。

## standard → full 迁移

1. 安装 TDengine 并创建目标 schema，不切换读流量。
2. 记录迁移快照水位后启用投影层双写：PostgreSQL 继续作为当前查询源，TDengine 接收水位后的新增数据。
3. 在快照水位以下按月分区和原 `messageId` 有界回灌历史；回灌使用独立限速、批次和并发配额，在线投影优先。记录每个分区的起止水位、条数、失败游标和校验摘要，利用确定性键处理与在线双写的边界重叠。
4. 对设备/测点/时间分层抽样，并对总数、最值、聚合和质量码执行自动对账。
5. 对账通过后切换 `TelemetryStore` 读适配器到 TDengine；PostgreSQL 分区至少保留 7 天只读观察期。
6. 观察期异常时切回 PostgreSQL；稳定后按审批结果归档或删除旧分区。

迁移期间 API、设备身份、messageId、Topic 和业务时间语义保持不变。

## 验收与回滚

- 同一历史 API 必须分别通过 PostgreSQL 和 TDengine 合同测试。
- 覆盖重复投影、跨月查询、迟到数据、分区创建失败、磁盘告警、后端切换和回灌对账。
- TDengine 不可用时 full 不自动降级为无限写 PostgreSQL；继续保留 inbox 并告警，或通过经审批的有界应急配置切换。
