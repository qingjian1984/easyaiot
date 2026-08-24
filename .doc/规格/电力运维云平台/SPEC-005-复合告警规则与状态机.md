# SPEC-005：复合告警规则与状态机

> Spec ID：POWER-SPEC-005  
> 上游需求：POWER-PRD-002 1.2.2
> 依赖：POWER-SPEC-001、POWER-SPEC-002、POWER-SPEC-004  
> 版本：0.1.1
> 状态：In Review  
> 日期：2026-08-24  
> 架构决策：[ADR-010 统一告警模型迁移](../../架构决策/电力运维云平台/ADR-010-统一告警模型迁移.md)、[ADR-011 Capability Manifest](../../架构决策/电力运维云平台/ADR-011-Capability-Manifest规范.md)、[ADR-013 受控数据库迁移](../../架构决策/电力运维云平台/ADR-013-受控数据库迁移执行器.md)、[ADR-014 Outbox/Inbox](../../架构决策/电力运维云平台/ADR-014-Outbox事件Transport与消费者Inbox.md)、[ADR-016 RUNTIME 边缘执行边界](../../架构决策/电力运维云平台/ADR-016-EDGE退役与RUNTIME边缘执行边界.md)  
> 目标里程碑：M2  
> 产品基线：[平台功能计划 1.5.0](../../架构设计/平台功能计划.md)、[项目开发宪法 1.6.0](../../开发规范/EasyAIoT项目开发宪法.md)

## 1. 目标与范围

建立 `iot-device` 内唯一告警主记录、版本化规则、告警周期、处置状态机、正交升级事件和维护模式语义，使阈值、设备事件、VIDEO、AI/RUNTIME 来源都映射到同一 `alarmId`。

本 Spec 包含：

- 单测点阈值、持续时间、迟滞、恢复阈值。
- full 档受控复合规则、抑制和维护模式。
- 告警创建、确认、处理、恢复、关闭、忽略和误报判定。
- 来源事件 Inbox、幂等周期、旧告警迁移和兼容查询。

不包含通知渠道编排（SPEC-006）、遥控（SPEC-007）和媒体证据归档（SPEC-008）。

## 2. 现有事实与禁止边界

当前仓库存在 `device_threshold_alarm` 的 `OPEN/CLEARED` 记录，以及 `iot-sink`、`iot-message`、VIDEO 的独立 `alert` 记录。它们是迁移来源或证据，不得继续扩展为第二处置事实源。

- 新主记录固定归属 `iot-device`，表逻辑名为 `alarm_record`。
- 来源系统只发布版本化事件并保存不可变来源明细，不保存确认、忽略、误报、处理或关闭状态。
- WEB、APP、VISUALIZE 只经网关访问统一告警 API，不直连来源库。
- ADR-014 的 power-model Inbox 不覆盖算法告警；告警域必须拥有独立 Schema、Inbox 和死信处置。
- DDL 必须通过受控迁移并提供中文表/字段注释；本 Spec 不授权直接执行生产 DDL。

## 3. 角色与权限族

| 动作 | 产品权限族 | 强制规则 |
|---|---|---|
| 查看告警 | 告警查看 | 按租户、站点、设备数据范围过滤 |
| 确认/处理 | 告警处置 | 服务端校验当前状态和乐观锁版本 |
| 关闭 | 告警关闭 | `RECOVERED` 或经批准的处置条件满足后执行 |
| 临时忽略 | 告警忽略 | 紧急等级永远拒绝；必须给原因和截止时间 |
| 误报判定 | 误报判定 | 与误报复核人员分离 |
| 误报复核 | 误报复核 | 与判定人分离，`FALSE_ALARM` 不可逆 |
| 规则维护/发布 | 规则管理 | 草稿、校验、发布、回滚分权并审计 |
| 维护模式 | 维护模式管理 | 仅 full；必须限定站点/设备/规则和时段 |

具体权限编码在 TD 中与 `iot-system` 菜单/权限 Seed 一并冻结；任何前端隐藏均不能替代服务端权限。

## 4. capability 与档位

| 能力 | standard | full |
|---|---|---|
| `power.alarm.core` | 必须：阈值、持续、迟滞、恢复、四级告警、处置状态机 | 必须，与 standard 共用实现 |
| `power.alarm.advanced` | 关闭：复合规则、抑制、维护模式、跨站策略 API/任务/菜单均不可用 | 启用：在核心模型上增加高级策略 |
| mini | 两项均关闭；不得创建电力规则、Inbox、消费者、任务或初始化数据 | 不适用 |

manifest 必须给出每租户/站点规则数、单规则测点数、活动告警数、查询跨度、保留期和消费并发配额。full 只能提高配额或启用高级策略，不能改变公共状态机和 API 语义。

## 5. 统一告警模型

### 5.1 主记录

`AlarmRecord` 至少包含：

- `alarmId`、`tenantId`、`siteId`。
- `sourceType`、`sourceId`、`cycleKey`、`sourceObjectId`。
- `deviceId`、可选 `propertyCode`、`ruleId`、`ruleVersion`。
- `severity`：`INFO/NORMAL/IMPORTANT/EMERGENCY`。
- `status`、`version`、首次/最近发生、恢复、关闭时间。
- `occurrenceCount`、`escalationLevel`、`lastEscalatedAt`。
- `maintenanceContextId`、`ignoredFromStatus`、`ignoredUntil`、`ignoreGeneration`。
- 创建/更新人与 UTC 时间；来源时区和原始时间偏移。

所有跨服务 ID 在 JSON 中使用字符串，时间使用带偏移 ISO 8601，持久化使用 UTC。

### 5.2 来源映射与 Inbox

- `(tenantId, sourceType, sourceId, cycleKey)` 唯一映射到一个 `alarmId`。
- Inbox 以 `messageId` 唯一；相同 ID 相同摘要为重复，相同 ID 不同摘要进入隔离并告警。
- 活动周期幂等键采用 ADR-010 的 `(tenantId, sourceType, sourceObjectId/deviceId, propertyCode, ruleId, ruleVersion, cycleKey)`。
- 新周期必须使用新 `cycleKey`；规则允许重开时，窗口和行为随规则版本保存。
- Inbox 成功持久化后才能推进告警；失败不得提交消息位点或伪造成功。

## 6. 规则模型

### 6.1 规则版本

规则采用 `DRAFT → VALIDATED → PUBLISHED → RETIRED`。发布版本的条件树、恢复策略、时段策略和内容哈希不可原地修改；生命周期元数据允许在事务内从 `PUBLISHED` 转为 `RETIRED`，或将历史 `RETIRED` 版本重新激活为 `PUBLISHED`。回滚必须在同一事务中先退役当前已发布版本，再重新激活历史版本，不新建相同内容行、不修改版本号/内容哈希；同一规则同时只能有一个 `PUBLISHED` 版本。

### 6.2 条件树

首期只允许声明式条件树：

- 逻辑节点：`AND`、`OR`。
- 比较：`GT/GTE/LT/LTE/EQ/NE/BETWEEN/IN/NOT_IN`。
- 状态：`CHANGED_TO/CHANGED_FROM`。
- 时间序列：`RATE_GT/RATE_LT`、持续时间。
- 禁止脚本、SQL、SpEL、模板执行、正则回溯表达式和用户函数。

协议硬上限：单规则最多 16 个叶子条件、嵌套深度 3、8 个不同测点、最长实时窗口 24 小时。manifest 可以下调，不得上调协议硬上限；需要更复杂模型时必须新增 ADR 和主版本。

### 6.3 数据质量与时间

- 实时数值规则默认只接受 `GOOD`；明确允许时可接受 `BACKFILLED`，但补传历史越限默认只形成历史事件，不触发实时通知。
- `TIMEOUT/COMM_ERROR/DECODE_ERROR/UNKNOWN` 不得当作数值 0。
- 通信异常使用独立规则类型，不与业务越限混淆。
- 多测点复合规则按站点时间事实和事件时间水位评估；超过 SPEC-004 乱序窗口的数据不推进实时周期。
- 迟滞、持续时间、恢复阈值和规则时段必须随版本固化。

## 7. 主状态机与正交升级

```text
ACTIVE → ACKNOWLEDGED → PROCESSING → RECOVERED → CLOSED
   ├→ IGNORED(until, reason) → ACTIVE/ACKNOWLEDGED
   └→ FALSE_ALARM(reason, reviewer) [terminal]

ESCALATION（正交）：NONE → LEVEL_1 → LEVEL_2 → ...
```

- 恢复不等于关闭；`RECOVERED` 仍需授权关闭。
- `IGNORED` 原子保存原状态、截止时间、原因、操作者、版本和严格单次 `ignoreGeneration`。每次进入 `IGNORED` 都单调递增该代次；到期任务携带代次快照，并以 `status=IGNORED`、`expectedVersion`、`ignoredUntil<=now` 和 `ignoreGeneration=expectedIgnoreGeneration` 四项条件 CAS 恢复，不能覆盖期间的恢复、关闭、主动解除忽略或再次忽略。离开 `IGNORED` 时可保留最后代次，仅清除当前忽略窗口字段。
- `EMERGENCY` 禁止 `IGNORED`，M2 无配置放开。
- `FALSE_ALARM` 需要独立复核并为终态；纠正只能追加记录。
- 升级不改变主状态，以 `AlarmEscalated` 事件和递增级别表达，可在 ACTIVE/ACKNOWLEDGED/PROCESSING 发生。
- 告警、缺陷和工单保持独立状态机，只通过稳定关联 ID 和追加事件协作。

## 8. 维护模式与抑制

- 维护模式仅 full 启用，必须限定租户、站点、对象/规则集合、开始/结束时间、原因和批准人。
- 维护期间规则继续评估，告警继续创建/更新并关联 `maintenanceContextId`；只抑制策略指定的通知。
- 同一故障仍只形成一个活动周期，维护开始/结束不得切割周期。
- 结束时只对仍成立且未闭合的告警按当前有效通知窗口重新评估；不得补发过期通知。
- 抑制命中、未命中和结束重评估都记录决策事件，供 SPEC-006 和报表使用。

## 9. API 与事件边界

公共 API 经 `/api/v1/device/alarms/**` 暴露，至少覆盖分页查询、详情、确认、开始处理、关闭、忽略、误报判定/复核和规则版本管理。写操作必须携带幂等键或资源版本；列表默认 50、硬上限 500。

事件至少包含：

- `device.alarm.created.v1`
- `device.alarm.occurrence-recorded.v1`
- `device.alarm.recovered.v1`
- `device.alarm.status-changed.v1`
- `device.alarm.escalated.v1`
- `device.alarm.suppression-decided.v1`

Envelope 必含 `eventId/eventVersion/tenantId/occurredAt/source/correlationId/alarmId`。Schema 放入生产者 API 模块并执行当前/上一主版本合同测试。

## 10. 稳定错误语义

| 错误码 | 语义 |
|---|---|
| `ALARM_NOT_FOUND` | 告警不存在或无权访问时按防枚举策略返回 |
| `ALARM_VERSION_CONFLICT` | 乐观锁冲突 |
| `ALARM_INVALID_TRANSITION` | 当前状态不允许动作 |
| `ALARM_EMERGENCY_IGNORE_FORBIDDEN` | 紧急告警禁止忽略 |
| `ALARM_REVIEWER_CONFLICT` | 判定人与复核人相同 |
| `ALARM_RULE_LIMIT_EXCEEDED` | 条件树或 capability 配额超限 |
| `ALARM_CAPABILITY_UNAVAILABLE` | 当前档位未启用能力 |
| `ALARM_SOURCE_HASH_CONFLICT` | 相同消息 ID 内容冲突并已隔离 |

## 11. 非功能预算

- 从合格遥测进入规则引擎到告警主记录提交，普通规则 P95 不高于 1 秒；端到端遥测接收到告警创建 P95 不高于 5 秒。
- 单次规则评估必须有 CPU/时间预算；超预算规则被隔离并产生可行动告警，不得拖垮消费分区。
- 所有消费者幂等、有限重试、死信和积压可观测；指标至少包含评估量、命中量、延迟、活动周期、重复、隔离、状态冲突和维护抑制数。
- 查询按租户与站点索引、分页；事故窗口大查询交给 SPEC-008 异步任务。
- 安全、权限、租户隔离、审计和状态一致性不得灰度关闭。

## 12. 迁移与回滚

1. 扩展 `alarm_record`、来源映射、事件、动作审计和独立 Inbox，不切旧读路径。
2. 将旧 `OPEN` 映射为 `ACTIVE`、`CLEARED` 映射为 `RECOVERED`；VIDEO/AI 来源一条不可变记录一周期，禁止猜测合并。
3. 双写并按租户、来源、等级、状态、时间和证据全量对账；缺失/重复映射为零。
4. 切统一 API，旧 API 至少保留一个发布周期并内部转调。
5. 停旧处置写入，来源表只读保留。

回滚只切读路径和停止新消费者，不删除新主记录、映射、Inbox 或审计。双写观察窗口、批次和生产数据画像由 TD 冻结。

## 13. 验收场景

### A. 持续、迟滞与恢复

```gherkin
Given 温度规则要求大于 80℃ 持续 60 秒触发且低于 75℃ 恢复
When GOOD 数据连续满足 60 秒
Then 只创建一个 ACTIVE 告警周期
When 温度降到 74℃
Then 同一 alarmId 进入 RECOVERED 而不是自动 CLOSED
```

### B. 重复与消息冲突

```gherkin
Given 同一来源 messageId 已持久化并创建告警
When 相同正文重复到达
Then 返回重复且不新增周期
When 相同 messageId 以不同正文到达
Then 消息被隔离并产生安全指标
And 不修改已有告警
```

### C. 忽略与紧急保护

```gherkin
Given 一般告警处于 ACKNOWLEDGED
When 授权用户忽略至 30 分钟后
Then 系统保存 ignoredFromStatus 和版本
When 到期且条件仍成立
Then 告警恢复 ACKNOWLEDGED 并进入通知重评估
And CAS 同时校验 status、expectedVersion、ignoredUntil 和 expectedIgnoreGeneration
When 人员主动确认并解除忽略
Then 告警进入 ACKNOWLEDGED 且不复用到期任务
Given 告警等级为 EMERGENCY
When 用户请求忽略
Then 服务端返回 ALARM_EMERGENCY_IGNORE_FORBIDDEN
```

### D. 维护模式

```gherkin
Given full 站点处于批准的维护模式
When 越限规则命中
Then 告警主记录仍创建并标记维护上下文
And 配置范围内通知被抑制
When 维护结束且告警仍成立但原通知已过有效窗口
Then 不补发过期通知且不创建新周期
And 同一评估周期只记录 occurrence-recorded 与 suppression-decided(REEVALUATED)
And 只有 SOURCE_RECOVERED 后再次发生才允许创建新 alarmId
```

### E. 权限与租户

```gherkin
Given 用户只有 site-a 查看权限
When 查询 site-b 告警或确认其他租户告警
Then 服务端拒绝且不泄露告警是否存在
And 记录脱敏审计
```

### F. 档位关闭

```gherkin
Given 部署档位为 standard
When 请求发布复合规则或维护模式
Then 返回 ALARM_CAPABILITY_UNAVAILABLE
And 不创建任务、消费者或残留配置
Given 部署档位为 mini
Then 不存在电力告警菜单、成功 API、规则任务或 Inbox 消费者
```

### G. 迁移对账

```gherkin
Given 旧阈值和 VIDEO 告警已完成迁移与双写
When 按租户、来源、等级、状态和时间窗口执行对账
Then 缺失映射与重复映射均为零
And 任何语义差异都有批准清单和回滚入口
```

## 14. 需求追踪

| ID | 要求 |
|---|---|
| ALM-001 | `iot-device` MUST 维护唯一告警主记录 |
| ALM-002 | 来源事件 MUST 经独立 Inbox 幂等进入统一周期 |
| ALM-003 | 规则 MUST 版本化且禁止任意脚本 |
| ALM-004 | 数据质量异常 MUST NOT 当作真实数值越限 |
| ALM-005 | 恢复 MUST NOT 自动关闭告警 |
| ALM-006 | 紧急告警 MUST NOT 进入 IGNORED |
| ALM-007 | 误报判定与复核 MUST 人员分离且历史不可改写 |
| ALM-008 | 升级 MUST 以正交级别/事件表达，不阻断主状态 |
| ALM-009 | 维护模式 MUST 保留规则和告警事实且不得补发过期通知 |
| ALM-010 | standard/full MUST 共用模型与 API，mini MUST 无残留能力 |
| ALM-011 | 迁移 MUST 可重跑、可对账、可回滚且不删除来源证据 |
| ALM-012 | 所有写动作 MUST 执行权限、租户、数据范围和乐观锁校验 |

## 15. OPEN 门禁

- OPEN-005-01：独立告警 Inbox/Schema、来源映射表和 `alarm_record` DDL 尚未形成，必须在告警 TD 冻结并通过中文注释、幂等和迁移评审。
- OPEN-005-02：规则硬上限已冻结为协议保护值；standard/full 业务配额仍须由目标规模画像和压测写入 capability manifest。
- OPEN-005-03：现有阈值、iot-sink/VIDEO alert 与统一模型的生产数据画像、双写窗口和差异清单尚未完成。
- OPEN-005-04：ADR-010 1.1.0 的正交升级修订需要纳入专项设计评审记录；关闭前本 Spec 不得标记 Approved / Frozen。
