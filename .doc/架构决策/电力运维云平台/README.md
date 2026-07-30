# EasyAIoT 电力运维云平台 ADR 索引

> 索引版本：1.0.0  
> 日期：2026-07-30  
> 产品基线：[平台功能计划 1.4.0](../../架构设计/平台功能计划.md)、[项目开发宪法 1.4.0](../../开发规范/EasyAIoT项目开发宪法.md)

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

## 开发解释顺序

1. 先遵循产品功能计划与项目开发宪法。
2. 再遵循 Accepted ADR 和冻结 Feature Spec。
3. ADR 冲突时，以状态更新较新且明确 supersede 关系的 ADR 为准；无法判定时停止 Technical Design 并发起 ADR 评审。
4. [M1 ADR 评审报告](./ADR评审报告-M1第一批.md)第一至第七章属于历史评审证据，不可覆盖本索引中的当前状态。

## 当前不可回退的边界

- mini 无电力菜单、API 成功能力、后台任务、collector、能源、运维、SCADA 或报告。
- full 是 standard 的严格能力超集，共享功能禁止复制开发。
- standard/full 的认证、授权、租户隔离、遥控安全、审计、幂等、数据质量和回滚要求一致。
- 遥测后端差异封装于 `TelemetryStore`；档位差异统一由 capability manifest 表达。
