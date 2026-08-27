# P02-M2-02C1P-G2-02：TELEMETRY_PROJECTED_V1 Owner 确认单

> 版本：0.3.0
> 日期：2026-08-27
> 状态：LOCAL VERIFIER PREPARED / UNSIGNED / C1 CLOSED
> 双基线：[平台功能计划 1.5.0](../../架构设计/平台功能计划.md)、[EasyAIoT 项目开发宪法 1.6.0](../../开发规范/EasyAIoT项目开发宪法.md)
> 输入来源：[C1P-G2 输入单 §4](./P02-M2-02C1P-G2-责任人输入收集与建议处置单.md)、[G2 输入处置记录 §3](./P02-M2-02C1P-G2-输入处置记录.md)
> Owner 基线：[TD-003 1.0.3 / In Review](./TD-003-遥测Inbox-ACK与时序投影.md)
> 机器合同：[Owner evidence v1 Schema](./assets/c1p-g2/telemetry-projected-owner-evidence-v1.schema.json)、[空白 DRAFT 模板](./assets/c1p-g2/telemetry-projected-owner-evidence-template.json)、[本地 verifier](../../../WEB/scripts/verify-telemetry-projected-owner-evidence.mjs)

## 1. 使用规则

本文件是 M1/TD-003 owner 的待签输入，不是执行指令，也不是代码授权。owner 必须从空白 DRAFT 模板复制独立 evidence 文件，填写 §5，运行本地 verifier 并提交受控签署记录；Sol 复核 verifier 结果、Schema、仓库路径、哈希和条件一致性，不代替 owner 确认外部发布历史。

不得直接覆盖模板。空白、`TBD`、聊天中的同意、仅修改 TD 文档或把 DRAFT 改名为 APPROVED 均不构成签署。若任何环境已经发布 `TELEMETRY_PROJECTED_V1`，不得选择原地收紧 v1。

## 2. 仓库事实

1. TD-003 1.0.3 §12 已设计 `telemetry_projection_event_outbox`，但没有提交版本化 API Schema；
2. 当前 `TelemetryProjectionOrchestrator` 只写 Inbox COMPLETED/重试/死信，没有同事务 outbox；
3. projector SELECT 未带 workloadId、productIdentification、quality、originalOffset、dataPriority；
4. Inbox API 已保留 productIdentification 和 canonical Envelope bytes，但没有受信 workloadId 字段；
5. TD-003 Envelope V1 已冻结 `value/valueEncoding/quality/dataPriority/collectedAt/sequence/configVersion`，其中 M1 valueEncoding 固定 `decimal-string`；
6. 仓库缺少 projection event Schema、publisher/relay 与原子性、重投、字段完整性测试。
7. [2026-08-12 TD-002/TD-003 协同冻结签字记录](../../开发规范/TD-002-003运维评审签字记录-20260812.md)接受了 TD-003 1.0.1 的 projection outbox 设计方向，但没有确认环境发布历史、v1/v2 选择、本节新增字段、生产 Schema 路径或测试计划，不能替代本 G2-02 evidence。

仓库事实只能证明当前代码未实现/发布该事件，不能证明部署环境历史；publicationHistory 必须由 owner 明确确认。

## 3. 推荐版本决策

- 如果全部环境均 `NEVER_PUBLISHED`：推荐 `EXTEND_V1_BEFORE_FIRST_PUBLISH`，在首次实现前一次冻结完整 v1；
- 如果任一环境已发布 v1：必须 `CREATE_V2`，消费者先兼容、生产者按批准周期双发、对账后切换，不原地增加 v1 必填字段；
- TD-003 必须从 1.0.3 升版并保持 In Review，直到 Schema、实现和测试证据通过独立冻结；
- Schema 推荐存放于生产者 API：`DEVICE/iot-sink/iot-sink-api/src/main/resources/events/telemetry.projected/v1.json`，若选择 v2 则使用独立 `v2.json`。

## 4. 必须冻结的事件合同

### 4.1 事件元数据

| 字段 | 合同 |
|---|---|
| `eventId` | canonical UUID；同 projection 事实重投不变化 |
| `eventType` | 固定 `TELEMETRY_PROJECTED_V1`；v2 时使用独立主版本名/Schema |
| `eventVersion` | 固定主/次版本字符串 |
| `occurredAt` | projector 完成时间，UTC/RFC 3339；只作事件审计，不替代 collectedAt |
| `source` | 固定 `iot-sink.telemetry-projector` |
| `eventContentSha256` | 对移除本字段后的事件对象执行 RFC 8785 JCS + SHA-256，避免自引用；不复用 telemetry payload hash |

### 4.2 遥测与路由事实

| 字段 | 权威来源/约束 |
|---|---|
| `messageId/contentSha256` | Inbox canonical message identity 与原 Envelope hash |
| `tenantId` | 已通过 principal/Topic/release/Envelope 一致性校验 |
| `workloadId` | MQTT principal/ingress route 持久化事实；禁止从 Envelope、当前设备表或内存猜测 |
| `productIdentification` | canonical Topic 与 Inbox route 元数据 |
| `siteCode/deviceIdentification/propertyCode` | canonical Envelope 且已与 release/注册事实交叉校验 |
| `value/valueEncoding` | Envelope 原事实；valueEncoding 固定 `decimal-string`，非 GOOD 时 value 可空但不得补 0 |
| `quality/dataPriority` | Envelope 原枚举，不以 Store 默认值或当前配置重建 |
| `collectedAt/originalOffset/sequence/configVersion` | Envelope 原事实；毫秒精度并保留原 offset |
| `storeResult` | 只允许 `STORED/DUPLICATE` |

事件不得包含凭据、完整 Inbox 原文、当前设备显示名或可变规则。THRESHOLD evaluator 必须直接消费这些版本化字段，不允许按 standard/full 分别查询 PostgreSQL/TDengine 补 value，也不能从接收时间重建 occurredAt/originalOffset。

### 4.3 事务与发布

1. Store 返回 STORED/DUPLICATE 后，Inbox COMPLETED 与 projection outbox PENDING 在同一 PostgreSQL 事务提交；
2. 事务任一步失败全部回滚，不能留下 COMPLETED 无 outbox；
3. relay 至少一次发布，成功后崩溃允许重投；消费者以 `(tenantId,messageId,eventType)` 幂等；
4. 同 messageId 异 contentSha256 必须隔离并告警，不能发布第二正文；
5. standard/full 共用同一事件和测试，mini 无 Bean、表、relay、consumer、配置或 capability 残留。

## 5. Owner 必填与签署

Owner 从 [空白 DRAFT 模板](./assets/c1p-g2/telemetry-projected-owner-evidence-template.json)复制一个独立 JSON 文件并按 [v1 Schema](./assets/c1p-g2/telemetry-projected-owner-evidence-v1.schema.json)填写。模板固定为 `UNCONFIRMED / UNDECIDED / approval=null`，只代表未提交输入。

| evidence 字段 | Owner 填写 |
|---|---|
| `publicationHistory` | `NEVER_PUBLISHED`，或逐环境列出首次/最近发布时间、已发布 v1 和当前消费者；无法确认时只能保留 `UNCONFIRMED/DRAFT` |
| `eventVersionDecision` | `NEVER_PUBLISHED` 只能选 `EXTEND_V1_BEFORE_FIRST_PUBLISH`；任一环境 `PUBLISHED` 只能选 `CREATE_V2`；v2 必交 migrationPlan |
| `artifacts.tdDocument` | 更新后的 TD-003 仓库路径、文档版本和 SHA-256 |
| `artifacts.productionSchema` | 生产者 API 下真实版本化事件 Schema 的路径、事件名、版本和 SHA-256；本 evidence Schema 不能替代它 |
| `artifacts.testPlan` | 可审计测试计划/任务单路径和 SHA-256 |
| `artifacts.consumerInventory` | 已发布 v1 时必交消费者清单；从未发布时可为 null |
| `requiredFields` | 完整列出 §4 的 23 个字段；APPROVED 不允许缺项、别名或额外字段 |
| `testCoverage` | 完整列出 §6 的 7 个测试域 |
| `approval` | ownerRole、approvedBy、RFC 3339 显式 offset 时间、decision、仓库内 decisionRef 和 evidence contentSha256 |

`approval.contentSha256` 按既有 G2 规则复制完整 evidence，仅移除 `approval.contentSha256` 字段后执行 RFC 8785 JCS 并计算 SHA-256，使用 `sha256:<64 lowercase hex>`。不得把整个 approval 置为 null。Schema 只校验格式和条件；本地 verifier 与 Sol 均会复算哈希并核对 artifact 实际内容。

若 `APPROVE_WITH_CHANGES`，必须在 decisionRef 中逐字段说明差异、兼容影响、消费者迁移、回滚与 C1 所需字段如何等价满足。不得删除 workload/product/value/quality/originalOffset 或以跨后端查询替代。

## 6. 必交测试计划

- Schema：缺/错 workload、product、valueEncoding、quality、offset、sequence、configVersion、storeResult；未知字段/版本；
- identity：同 ID 同 hash 重投、同 ID 异 hash 隔离、eventId 重投稳定；
- route：错 principal/workload、同 site/config 不同 workload、release 从未 APPLIED；
- value：高精度 decimal-string、非 GOOD value 空、禁止补 0、standard/full 结果一致；
- transaction：Store 成功后 COMPLETED/outbox 任一步失败全回滚，进程崩溃恢复；
- relay：成功后标记前崩溃、重复发布、租约回收、重试上限和死信；
- capability：standard/full 同合同，mini 无残留。

### 6.1 本地预检

在 `WEB/` 目录运行：

```bash
pnpm verify:telemetry-projected-owner-evidence -- --file .doc/技术设计/电力运维云平台/OWNER_EVIDENCE.json
```

默认不带 `--file` 时只验证仓库内空白 DRAFT 模板，应输出 `qualification=DRAFT_UNCONFIRMED`。APPROVED 包只有在 Schema、artifact 路径/realpath/哈希、批准哈希、TD 版本、生产事件 Schema、发布时间顺序、消费者清单和测试计划全部通过时，才输出 `APPROVED_V1_READY_FOR_SOL_REVIEW` 或 `APPROVED_V2_READY_FOR_SOL_REVIEW`；该结果仍只是提交 Sol 复核的资格，不是 G2-02 自动关闭。

## 7. 当前门禁

Owner evidence Schema、DRAFT 模板和本地 verifier 已准备，但当前模板仍为 `UNCONFIRMED / UNDECIDED / approval=null`，不构成责任人输入。owner 的 APPROVED evidence、更新 TD、生产事件 Schema、测试计划及条件性 consumerInventory 到位，通过 verifier 并经 Sol 复核前，G2-02 保持 `LOCAL-VERIFIER-PREPARED / WAITING M1 OWNER`；THRESHOLD adapter、ADR-019 Accepted 和 C1A 继续关闭。
