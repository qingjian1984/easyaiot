# EasyAIoT 电力运维云平台 ADR 索引

> 索引版本：1.8.0
> 日期：2026-08-13
> 产品基线：[平台功能计划 1.5.0](../../架构设计/平台功能计划.md)、[项目开发宪法 1.6.0](../../开发规范/EasyAIoT项目开发宪法.md)

## 当前决策集

| ADR | 状态 | 决策摘要 |
|---|---|---|
| [ADR-001](./ADR-001-RTU-Poller运行位置.md) | Accepted | M1 使用站点 `iot-sink collector`，由 NODE 编排；不复制到 EDGE |
| [ADR-002](./ADR-002-边缘持久队列.md) | Accepted | standard/full collector 共用 SQLite WAL outbox |
| [ADR-003](./ADR-003-遥测ACK机制.md) | Accepted | MQTT QoS 1 + 中心可靠 inbox + 应用持久化 ACK |
| [ADR-004](./ADR-004-历史设备编码兼容策略.md) | Accepted | 历史 `deviceIdentification` 原值保留，新业务编码严格校验 |
| [ADR-005](./ADR-005-mini采集通信方案.md) | Superseded | mini 不支持电力运维，本决策不实施 |
| [ADR-006](./ADR-006-mini-standard时序存储方案.md) | Accepted | standard PostgreSQL、full TDengine，共用 `TelemetryStore` |
| [ADR-007](./ADR-007-collector打包与NODE管理契约.md) | Accepted | 同一 iot-sink 构建产物、collector profile、NODE 受控生命周期 |
| [ADR-008](./ADR-008-二维码安全解析方案.md) | Accepted | 独立随机短码表，可撤销、鉴权、审计 |
| [ADR-009](./ADR-009-物模型模板版本策略.md) | Accepted | 模板采用 SemVer，发布版本不可原地修改 |
| [ADR-010](./ADR-010-统一告警模型迁移.md) | Accepted | `iot-device` 统一告警事实、状态机与迁移 |
| [ADR-011](./ADR-011-Capability-Manifest规范.md) | Accepted | standard/full 同源能力清单和 strict-superset CI 门禁 |
| [ADR-012](./ADR-012-产品根属性与服务参数单一事实.md) | Accepted | 1.0.2：根属性使用 `product_properties`；服务参数使用 command request/response；影子列收缩须独立 ADR、明确 owner/到期日及备份审批 |
| [ADR-013](./ADR-013-受控数据库迁移执行器.md) | Accepted | 1.5.2：受控 runner 已冻结并执行 V001 窗口；后续 V003/V004/Inbox 仍须逐窗口批准 |
| [ADR-014](./ADR-014-Outbox事件Transport与消费者Inbox.md) | Accepted | 1.3.7：Kafka Outbox/Inbox、消费循环、CI 合同、调度、指标和 JDBC 合同证据已落地；V005 已受控落入目标集成实例，任务 7 与双发对账仍 OPEN |
| [ADR-015](./ADR-015-collector-workload-binding设备侧投影.md) | Accepted | 1.1.1：iot-device 库独立可变 workload binding 投影；V004 已受控落入目标集成实例并通过 PG 合同，任务 7 写入状态机仍 OPEN |
| [ADR-016](./ADR-016-EDGE退役与RUNTIME边缘执行边界.md) | Accepted | EDGE 退役；电力采集固定由 NODE + iot-sink collector 承担，边缘推理由 RUNTIME 承担，告警媒体统一经 iot-sink 归档 |
| [ADR-017](./ADR-017-遥测可靠链路Topic与产品路由身份收口.md) | Accepted | 遥测可靠链路回归既有 `/iot/{product}/{device}/property/**` Topic；产品身份作为持久化路由元数据，按 expand→backfill→enforce 迁移；LC-02 仍等待 LC-02A 前置验证 |
| [ADR-018](./ADR-018-控制面内部服务与NODE请求认证.md) | Accepted | collector 配置链采用两段隔离认证：微服务内部 HMAC 服务身份与 iot-node→NODE 节点级 HMAC；已授权 LC-02A-0，禁止 token-only 降级 |

## 开发解释顺序

1. 先遵循产品功能计划与项目开发宪法。
2. 再遵循 Accepted ADR 和冻结 Feature Spec；`Proposed` ADR 仅作为待决策门禁，不得作为生产实现授权。
3. ADR 冲突时，以状态更新较新且明确 supersede 关系的 ADR 为准；无法判定时停止 Technical Design 并发起 ADR 评审。
4. [ADR 评审报告](../../开发规范/ADR评审报告.md)第一至第九章保留 ADR-001～011 原始证据，第十章为其最终处置；第十一章为 ADR-012 追加评审与最终处置。评审报告不可覆盖本索引中的当前 ADR 状态。

## 当前不可回退的边界

- mini 无电力菜单、API 成功能力、后台任务、collector、能源、运维、SCADA 或报告。
- full 是 standard 的严格能力超集，共享功能禁止复制开发。
- standard/full 的认证、授权、租户隔离、遥控安全、审计、幂等、数据质量和回滚要求一致。
- 遥测后端差异封装于 `TelemetryStore`；档位差异统一由 capability manifest 表达。
- EDGE/TASK 不再是现行实施模块；采集、RUNTIME 推理和媒体归档边界遵循 ADR-016。
