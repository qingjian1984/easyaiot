# SPEC-008：事故追忆与视频证据

> Spec ID：POWER-SPEC-008  
> 上游需求：POWER-PRD-002 1.2.2、POWER-PRD-003 1.2.0  
> 依赖：POWER-SPEC-004、POWER-SPEC-005、POWER-SPEC-006  
> 版本：0.2.0  
> 状态：In Review  
> 日期：2026-08-24  
> 架构决策：[ADR-010 统一告警模型迁移](../../架构决策/电力运维云平台/ADR-010-统一告警模型迁移.md)、[ADR-011 Capability Manifest](../../架构决策/电力运维云平台/ADR-011-Capability-Manifest规范.md)、[ADR-013 受控数据库迁移](../../架构决策/电力运维云平台/ADR-013-受控数据库迁移执行器.md)、[ADR-014 Outbox/Inbox](../../架构决策/电力运维云平台/ADR-014-Outbox事件Transport与消费者Inbox.md)、[ADR-016 RUNTIME 与媒体归档边界](../../架构决策/电力运维云平台/ADR-016-EDGE退役与RUNTIME边缘执行边界.md)  
> 目标里程碑：M2  
> 产品基线：[平台功能计划 1.5.0](../../架构设计/平台功能计划.md)、[项目开发宪法 1.6.0](../../开发规范/EasyAIoT项目开发宪法.md)

## 1. 目标与责任边界

围绕统一 `alarmId` 建立可追溯事故时间线、证据索引、遥测窗口、图片/录像引用和异步事故报告。

PRD-03 当前仍为 `In Review`。因此本 Spec 可先冻结 PRD-02 所需的时间线、证据索引、遥测窗口、媒体接入合同和失败隔离；预置位交互、SCADA 展示及完整视频联动体验必须等待 PRD-03 基线化，不能由本 Spec 越权批准。

- `iot-device` 告警域拥有事故时间线、证据索引、生成任务和权限事实。
- VIDEO/RUNTIME 产生媒体，`iot-sink` 负责媒体接入、NFS 暂存和 MinIO 归档；媒体系统不保存告警处置状态。
- `TelemetryStore` 提供遥测事实；事故域不复制原始时序，只保存冻结查询上下文、水位、摘要和必要导出对象。
- 媒体失败不得阻止或回滚告警；必须形成明确证据缺口。

## 2. capability 与档位

| 能力 | standard | full |
|---|---|---|
| 基础证据 | 告警时间线、有限遥测窗口、已授权抓拍/录像引用 | 与 standard 共用模型和 API |
| `power.alarm.advanced` | 不提供完整事故冻结、跨站追忆和大规模异步报告 | 完整证据冻结、预置位/录像联动、异步事故报告和跨站治理 |
| mini | 无电力证据菜单、联动任务、消费者或报告 | 不适用 |

manifest 必须限制单告警摄像机数、并发联动、录像时长、遥测点数/跨度、在线返回体、对象大小、异步任务数和保留配额。

## 3. 权限与安全

- 告警详情权限不自动授予媒体查看、下载或报告导出权限。
- 每次钻取重新校验租户、站点、告警、设备、摄像机和对象权限。
- 图片、录像和报告只通过短时、限对象、限操作的签名 URL 访问；URL 不进入持久日志和分享链接。
- 证据查看、下载、导出、延长保留、作废和修订分别审计。
- 原始媒体、人员信息和操作记录按最小必要原则展示；报告脱敏策略随模板版本保存。

## 4. 时间线与证据模型

### 4.1 时间线事件

时间线按 UTC 排序并保留来源时区/偏移，至少包含：

- 告警首次/重复发生、升级、确认、处理、恢复、关闭、忽略和误报动作。
- 通知请求、渠道结果和人员确认。
- 关联遥控申请、审批、执行、回执和复核。
- 维护模式和抑制决策。
- 图片、录像、遥测窗口、操作日志、缺陷/工单关联和证据缺口。

每项包含 `timelineEventId`、`alarmId`、类型、发生时间、来源、来源 ID、摘要、可选证据引用和完整性状态。时间线是追加式投影，可重建但不可静默删除历史事件。

### 4.2 证据索引

`EvidenceIndex` 至少包含：

- `evidenceId`、`alarmId`、`tenantId`、`siteId`。
- `evidenceType`、`sourceType/sourceId`、发生/归档时间。
- 对象引用或冻结查询上下文；对象大小、媒体类型。
- `sha256`、归档状态、缺口码、保留策略 ID/版本。
- 创建来源、可见级别和审计时间。

唯一键至少覆盖 `alarmId + sourceType + sourceId + evidenceId`；重复归档只返回同一证据，摘要冲突进入隔离。

## 5. 遥测追忆窗口

- 默认窗口为告警首次发生前 5 分钟至恢复后 10 分钟；规则版本或事故模板可在 manifest 硬上限内调整。
- 告警未恢复时采用当前时间作为临时结束，生成结果标记 `OPEN_WINDOW`；恢复后可生成修订版。
- 在线查询默认最多 10 个测点、1 小时和 100,000 条；超过任一上限不得执行无界同步查询。若异步能力和任务配额可用，API 返回 accepted/202、`EVIDENCE_TELEMETRY_ASYNC_REQUIRED` 及异步任务 ID；该结果表示已受理，不是失败。
- 若异步能力未启用或任务配额不可用，服务端必须 fail-closed，按原因返回 `EVIDENCE_LIMIT_EXCEEDED` 或 `EVIDENCE_CAPABILITY_UNAVAILABLE`，不得以通用 TelemetryStore 查询配额错误替代，也不得偷偷降级为同步查询。
- 冻结清单保存 `alarmId`、测点、`[start,end)`、站点时区、TelemetryStore 后端抽象版本、数据水位、质量策略版本、查询摘要和结果哈希。
- standard/full 使用同一查询契约；standard 走 PostgreSQL，full 走 TDengine，业务层不得分叉。
- 缺失、迟到、质量异常和已过保留期的数据必须在结果中显式标记，不以 0 填充。

## 6. 视频联动与媒体归档

### 6.1 联动动作

联动策略按站点、告警规则/类型、摄像机和预置位版本化，可定义抓拍、预置位调用、事故前后录像冻结。每个动作独立记录 `REQUESTED/RUNNING/SUCCEEDED/FAILED/EXPIRED`，一个失败不得回滚其他动作或告警。

- 摄像机权限、在线状态、并发配额和预置位占用在执行前校验。
- 同一 `alarmId + cameraId + actionType + policyVersion` 幂等。
- 视频不可用、权限拒绝、NFS/MinIO 故障和录像过期使用稳定缺口码。
- 观看链路与算法链路保持独立，证据冻结不得改变实时观看配置。

### 6.2 归档链

`RUNTIME/VIDEO → iot-sink → 受管 NFS 暂存 → MinIO 归档`。禁止回退到非受管本地目录。

- 暂存和归档均计算 SHA-256；传输完成后校验大小和摘要。
- MinIO 不可用时保留受管暂存和重试元数据，受容量上限与优先级保护；满载时不得无限增长，必须产生缺口和告警。
- 归档成功后按保留策略清理暂存；清理失败可重试但不得删除唯一副本。
- 当前链路参数、NFS 容量、重试、死信和恢复演练由媒体 TD/容量测试冻结。

## 7. 保留、不可变性与修订

- 每个 evidence 必须绑定经部署责任人批准的 `retentionPolicyId/version`；未选择策略时 full 事故冻结 capability 不得启用。
- 保留策略声明普通/紧急证据期限、法律保留、自动清理、延长审批和容量上限；本 Spec 不臆定跨地区统一期限。
- 已发布事故报告和冻结清单不可覆盖。源数据或证据补齐后创建新修订版，通过 `revisionOf` 和连续修订号关联。
- “不可修改”表示应用层不可覆盖且有摘要/审计；若部署未提供对象锁/WORM，不得宣称满足法定不可篡改存证。
- 证据作废只追加原因和替代引用，不物理删除仍在保留期或法律保留内的对象。

## 8. 异步事故报告

状态机：

```text
PENDING → RUNNING → DRAFT_READY → PUBLISHED
   ├→ FAILED_RETRYABLE → PENDING
   ├→ FAILED_FINAL
   └→ CANCELLED
```

- 生成请求使用 `alarmId + templateVersion + sourceWatermark + requestPurpose` 幂等。
- 报告包含时间线、告警状态、通知/确认、遥控/处置、遥测完整率、媒体索引、证据缺口和生成清单摘要。
- 外部依赖失败不修改告警；任务有限重试并记录失败原因。
- 文件进入 MinIO，下载使用短时 URL；PDF/Excel 质量门禁由 PRD-06/SPEC-018 复用，本 Spec 只冻结事故内容事实。

## 9. API、事件与错误

公共 API 经 `/api/v1/device/alarms/{alarmId}/timeline`、`/evidence`、`/telemetry-window`、`/incident-reports` 提供；在线查询分页或有界返回，异步生成返回任务 ID。

事件至少包含：

- `device.alarm.evidence-requested.v1`
- `device.alarm.evidence-archived.v1`
- `device.alarm.evidence-failed.v1`

稳定错误/缺口码包括：`EVIDENCE_NOT_FOUND`、`EVIDENCE_PERMISSION_DENIED`、`EVIDENCE_OBJECT_EXPIRED`、`EVIDENCE_CAMERA_UNAVAILABLE`、`EVIDENCE_MEDIA_ARCHIVE_FAILED`、`EVIDENCE_TELEMETRY_INCOMPLETE`、`EVIDENCE_TELEMETRY_ASYNC_REQUIRED`、`EVIDENCE_LIMIT_EXCEEDED`、`EVIDENCE_CAPABILITY_UNAVAILABLE`、`EVIDENCE_HASH_CONFLICT`。其中 `EVIDENCE_TELEMETRY_ASYNC_REQUIRED` 仅用于超过在线上限且异步任务已被受理的响应，不表示任务失败。

## 10. 非功能与可观测性

- 告警创建不得等待媒体或报告；证据索引请求在告警提交后异步执行。
- 普通时间线查询 P95 目标不高于 500ms；媒体 URL 签发 P95 不高于 1 秒；大查询/报告异步。
- 指标至少包括联动请求、成功/失败、归档延迟、NFS 水位、MinIO 重试、缺口、哈希冲突、任务积压和报告耗时。
- 暂存、队列、批次、对象大小和并发全部有硬上限；媒体数据不得进入 Kafka 大消息。
- 日志携带 `alarmId/evidenceId/taskId/traceId`，不记录签名 URL、凭据或完整敏感正文。

## 11. 兼容与回滚

- 现有 VIDEO/iot-sink `alertId` 作为来源 ID，通过 ADR-010 映射到全局 `alarmId`；禁止把旧 ID 直接当新 ID。
- 现有图片/录像路径在兼容期通过适配层生成证据索引，迁移任务可重跑并按对象摘要对账。
- 新旧告警详情可双读一个发布周期；切换前按租户、来源、媒体类型、时间和对象数量对账。
- 回滚只切查询入口和停止新联动消费者，不删除已归档证据、索引、冻结清单或报告。

## 12. 验收场景

### A. 正常事故时间线

```gherkin
Given 告警已发生、通知、确认、遥控并恢复
When 授权用户查看时间线
Then 事件按 UTC 有序展示并保留站点时区
And 每项可追踪来源且主状态来自统一 alarmId
```

### B. 媒体失败不阻塞告警

```gherkin
Given 告警联动摄像机抓拍但 MinIO 不可用
When 告警创建
Then 告警正常提交并可处置
And 证据动作记录失败/重试和明确缺口
And 不创建非受管本地回退目录
```

### C. 并发视频动作

```gherkin
Given 同一告警关联三台摄像机
When 两台成功且一台离线
Then 三个动作分别记录结果
And 成功证据保持可用，离线失败不回滚其他结果
```

### D. 遥测超限异步

```gherkin
Given 请求 20 个测点的 24 小时原始追忆数据
When 超过在线查询上限
Then 异步能力和任务配额可用时 API 返回 accepted/202、EVIDENCE_TELEMETRY_ASYNC_REQUIRED 和 taskId
And 异步能力或任务配额不可用时返回 EVIDENCE_LIMIT_EXCEEDED 或 EVIDENCE_CAPABILITY_UNAVAILABLE
And 不执行无界同步查询
```

### E. 权限与签名 URL

```gherkin
Given 用户有告警查看权限但无摄像机媒体权限
When 请求录像播放地址
Then 服务端拒绝且不签发 URL
Given 用户有权限
Then URL 仅限目标对象、操作和短时有效
```

### F. 修订与迟到证据

```gherkin
Given 事故报告已发布
When 迟到遥测或录像补齐
Then 原报告保持不变
And 系统生成带 revisionOf、差异和新摘要的修订版
```

### G. 保留策略

```gherkin
Given full 未配置批准的证据保留策略
When 尝试启用完整事故冻结
Then 返回 EVIDENCE_CAPABILITY_UNAVAILABLE
And 不启动无界归档任务
```

### H. mini 与 standard

```gherkin
Given 部署档位为 mini
Then 不存在电力证据菜单、联动消费者或报告任务
Given standard 请求 full 跨站事故冻结
Then 服务端返回 EVIDENCE_CAPABILITY_UNAVAILABLE
```

## 13. 需求追踪

| ID | 要求 |
|---|---|
| EVD-001 | 所有事故证据 MUST 通过统一 alarmId 索引 |
| EVD-002 | 告警域 MUST 拥有时间线/索引，媒体系统 MUST NOT 复制处置状态 |
| EVD-003 | 媒体失败 MUST NOT 阻止或回滚告警 |
| EVD-004 | 每个联动动作 MUST 独立、幂等并记录结果 |
| EVD-005 | 遥测追忆 MUST 使用 TelemetryStore 且受范围/条数上限 |
| EVD-006 | 证据 MUST 记录摘要、保留策略和缺口状态 |
| EVD-007 | 图片、录像和报告 MUST 使用短时限对象授权 |
| EVD-008 | 已发布清单/报告 MUST 不可覆盖，补齐生成修订版 |
| EVD-009 | NFS/MinIO 降级 MUST 有界、可观测且无非受管回退 |
| EVD-010 | standard/full MUST 共用契约，mini MUST 无任务残留 |
| EVD-011 | 遥测超过在线上限 MUST 返回异步受理结果或明确 fail-closed 码，不得无界同步查询 |

## 14. OPEN 门禁

- OPEN-008-01：NFS 暂存、MinIO 归档、重试/死信、容量和恢复演练参数尚待媒体 TD 与压测冻结。
- OPEN-008-02：摄像机预置位、录像切片、现有路径/对象画像和多来源 `alertId → alarmId` 对账尚未完成。
- OPEN-008-03：部署地区的证据保留、隐私、法律保留和对象锁/WORM 能力需责任人批准；未批准不得宣称法定不可篡改。
- OPEN-008-04：典型事故报告的数据量、生成资源和版式质量需样本验证。
- OPEN-008-05：权限签发、哈希冲突、MinIO/NFS 故障注入和跨租户测试通过前，本 Spec 不得标记 Approved / Frozen。
