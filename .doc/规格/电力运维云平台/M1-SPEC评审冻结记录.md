# M1 第一批 Feature Spec 评审冻结记录

> 评审日期：2026-07-30  
> 评审范围：POWER-SPEC-001 ～ POWER-SPEC-004  
> 记录版本：1.4.0  
> 结论：Approved / Frozen；初始 M1 规格基线 1.0.0，当前 M1 规格基线 1.4.0  
> 冻结含义：需求边界和验收口径已确定；不代表代码已经实现或通过验收
> 上游基线：[平台功能计划 1.4.0](../../架构设计/平台功能计划.md)、[项目开发宪法 1.4.0](../../开发规范/EasyAIoT项目开发宪法.md)

## 1. 冻结清单

| Spec | 结论 | 关键冻结项 |
|---|---|---|
| SPEC-001 电力对象与测点编码 1.3.0 | Approved / Frozen | tenantId 字符串兼容；siteCode 门禁；稳定 deep link；别名防环 |
| SPEC-002 电力设备物模型模板 1.3.0 | Approved / Frozen | SemVer；三方合并；精确回滚；十进制精度；生命周期与错误契约 |
| SPEC-003 RS485/Modbus RTU 1.4.0 | Approved / Frozen | iot-sink collector；人工发布/应用超时；合并规则；总线准入；倍率分层 |
| SPEC-004 遥测质量与补传 1.4.0 | Approved / Frozen | tenant/site 契约；单写 outbox；应用 ACK；乱序/封账；精度与查询配额 |

当前 M1 规格基线 1.4.0 是本冻结记录的集合版本，不要求四份 Spec 使用相同 SemVer。其包含 PRD 1.2.0 对齐修订及本轮跨契约补强；不改变 Poller 归属、SQLite 队列实现或 ACK 删除语义。`tenantId` 采用“新生产者发字符串、消费者兼容安全旧整数”的迁移方式，不要求一次性切换全部旧消息。

### 1.1 M1 前六项待决策关闭结果

| Spec | 冻结决策 | 结论 |
|---|---|---|
| SPEC-001 | 二维码解析方案 | 独立短码表，可撤销、可审计；载荷仅含版本和随机短码 |
| SPEC-002 | 模板版本号策略 | 语义版本 `MAJOR.MINOR.PATCH` |
| SPEC-003 | RTU Poller 首期运行位置 | M1 使用 `iot-sink` collector；EDGE 延至 M2 重新评估 |
| SPEC-004 | 边缘持久队列 | SQLite WAL outbox |
| SPEC-004 | 中心持久化确认 | MQTT QoS 1 + 应用层持久化 ACK |
| SPEC-004 | 数据完整率 | `有效采样数 / 应采集数 × 100%`，应采集按版本化采样计划分段计算 |

上述六项已全部关闭，不再作为 M1 待决策项。

## 2. 评审依据与代码证据

- `iot-sink` 已存在 `IotModbusRtuPollingProtocol`、`AbstractIndustrialPollingProtocol`、`IndustrialDeviceConfig` 和串口互斥，支持继续产品化而非重写。
- 现有属性上报使用 `PROPERTY_UPSTREAM_REPORT`，RTU Poller 当前显式设置 `needReply=false`。
- 平台已定义 `PROPERTY_DOWNSTREAM_REPORT_ACK`，可复用现有 Topic 契约方向。
- `PropertyUpstreamReportListener` 调用 `DeviceDataStorageService` 写 PostgreSQL 状态、TDengine 和 Redis；当前 TDengine 异常被捕获后仅记录日志，不能形成可靠 ACK。
- `deviceIdentification` 已广泛用于 Topic、认证、缓存和时序；主要模型为字符串，但发现少数历史 VO 类型不一致，必须兼容审计后修复。

## 3. 已接受 ADR

1. [ADR-001 RTU Poller 运行位置](../../架构决策/电力运维云平台/ADR-001-RTU-Poller运行位置.md)
2. [ADR-002 边缘持久队列](../../架构决策/电力运维云平台/ADR-002-边缘持久队列.md)
3. [ADR-003 遥测 ACK 机制](../../架构决策/电力运维云平台/ADR-003-遥测ACK机制.md)
4. [ADR-004 历史设备编码兼容策略](../../架构决策/电力运维云平台/ADR-004-历史设备编码兼容策略.md)
5. [ADR-005 mini 采集通信方案](../../架构决策/电力运维云平台/ADR-005-mini采集通信方案.md) — Superseded，不实施
6. [ADR-006 standard/full 时序存储抽象](../../架构决策/电力运维云平台/ADR-006-mini-standard时序存储方案.md)
7. [ADR-007 collector 打包与 NODE 管理契约](../../架构决策/电力运维云平台/ADR-007-collector打包与NODE管理契约.md)
8. [ADR-008 二维码安全解析方案](../../架构决策/电力运维云平台/ADR-008-二维码安全解析方案.md)
9. [ADR-009 物模型模板版本策略](../../架构决策/电力运维云平台/ADR-009-物模型模板版本策略.md)
10. [ADR-011 Capability Manifest 规范](../../架构决策/电力运维云平台/ADR-011-Capability-Manifest规范.md)

## 4. 冻结后的 SDD 门禁

下一阶段为 Technical Design，不得直接进入业务功能编码。必须先产出并评审：

- [TD-001](../../技术设计/电力运维云平台/TD-001-collector与NODE部署契约.md)：站点 collector profile、ADR-007 NODE 容器部署契约与配置发布状态机（已产出，In Review）。
- [TD-002](../../技术设计/电力运维云平台/TD-002-SQLite-Outbox与恢复迁移.md)：SQLite schema、容量估算、并发模型、加密/权限、恢复与迁移（已产出，In Review）。
- [TD-003](../../技术设计/电力运维云平台/TD-003-遥测Inbox-ACK与时序投影.md)：遥测 envelope、中心 inbox 表/索引、ACK payload、Topic/QoS、两层幂等、投影死信/补偿和分档时序后端（已产出，In Review）。
- TD-004：对象/别名/二维码短码数据模型、存量编码画像、解析权限、API 兼容矩阵和迁移脚本设计。
- TD-005：物模型模板 JSON Schema、Excel 模板、版本差异和发布 API。

每份 TD 必须包含接口、数据结构、错误码、时序、权限、指标、standard/full 资源预算、mini 关闭验证、测试和回滚方案；通过后才拆 Implementation Tasks。

TD 评审至少包含产品/需求负责人、相关模块负责人、架构负责人和 QA；涉及安全遥控、隐私、外部通知或部署运维时增加安全/运维负责人。通过条件为：

- 不存在未关闭的 CRITICAL/HIGH 问题；MEDIUM 必须有责任人、处理结论和截止版本。
- PRD→Spec→TD→测试追踪完整，接口/事件/数据迁移和回滚可执行。
- standard/full 预算、mini 关闭、权限/租户、幂等/超时和故障恢复均有验收证据或已批准的测试计划。
- 评审输出包含结论、问题清单、签字/责任人和冻结版本；未满足时保持 In Review，不得拆实现任务。

## 5. 基线变更控制

- 文案澄清且不改变验收结果：Spec PATCH 版本，记录变更说明。
- 新增向后兼容能力：Spec MINOR 版本，重新评审受影响模块。
- 改变标识、ACK 删除条件、Poller 归属或队列实现：Spec MAJOR 版本并新增/替代 ADR。
- 紧急实现若偏离冻结基线，必须记录例外、风险、截止日期和恢复计划；不得静默偏离。
- 档位变化不得复制共享能力；任何新增 standard/full 分叉实现、改变 strict-superset 关系或绕过 capability manifest 的提案均需先更新产品基线并新增/替代 ADR。

后续 SPEC-005～018 的范围和必含门禁见 [后续 Feature Spec 编写约束](./后续Feature-Spec编写约束.md)。该文件是规划门禁，不代表这些 Spec 已完成或冻结。
