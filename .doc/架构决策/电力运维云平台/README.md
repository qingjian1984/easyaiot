# EasyAIoT 电力运维云平台 ADR 索引

> 索引版本：1.3.0
> 日期：2026-08-06
> 产品基线：[平台功能计划 1.4.0](../../架构设计/平台功能计划.md)、[项目开发宪法 1.5.0](../../开发规范/EasyAIoT项目开发宪法.md)

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
| [ADR-013](./ADR-013-受控数据库迁移执行器.md) | Proposed | 1.3.1：TD-005 migration 采用受控迁移步骤执行器（history + SHA-256 + advisory lock）；不引入 Flyway/Liquibase；宪法专项设计处置与候选 runner Spike 完成，MIG-001/002/004/006/007/008/009 PASS、003/005 PARTIAL；12 表画像新鲜度重跑与 2026-08-05 基线一致；生产存量重跑、演练与核对 OPEN，批准前不得执行 DDL |
| [ADR-014](./ADR-014-Outbox事件Transport与消费者Inbox.md) | Proposed | 1.1.0：Kafka 作为 Outbox transport（`power-model-release-v1` + 消费者组 + `power_model_event_inbox`）；消费者按 eventId 幂等；双版本窗口与回滚遵循宪法 §5.4；宪法专项设计处置完成（目标角色/档位行为/Envelope 冻结/CI 门禁/配置清单/安全态势/双 UNIQUE 收缩），压测、CI 接线与消费者实现 OPEN，批准前不接生产传输、Inbox 不落库 |

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
