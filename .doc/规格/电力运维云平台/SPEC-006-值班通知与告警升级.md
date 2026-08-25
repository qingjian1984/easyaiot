# SPEC-006：值班通知与告警升级

> Spec ID：POWER-SPEC-006
> 上游需求：POWER-PRD-002 1.2.2
> 依赖：POWER-SPEC-005 0.1.2
> 版本：0.1.2
> 状态：In Review
> 日期：2026-08-25
> 架构决策：[ADR-010 统一告警模型迁移](../../架构决策/电力运维云平台/ADR-010-统一告警模型迁移.md)、[ADR-011 Capability Manifest](../../架构决策/电力运维云平台/ADR-011-Capability-Manifest规范.md)、[ADR-014 Outbox/Inbox](../../架构决策/电力运维云平台/ADR-014-Outbox事件Transport与消费者Inbox.md)
> 目标里程碑：M2
> 产品基线：[平台功能计划 1.5.0](../../架构设计/平台功能计划.md)、[项目开发宪法 1.6.0](../../开发规范/EasyAIoT项目开发宪法.md)

## 1. 目标与边界

建立值班表、通知策略、渠道适配、送达状态和正交升级编排，使告警事实先可靠落库，通知失败不会阻止或回滚告警。

- `iot-device` 拥有告警、升级级别和通知请求意图。
- `iot-message` 拥有渠道适配、发送尝试和渠道结果，不成为告警状态事实源。
- 外部短信、电话、企业微信等渠道状态必须与“平台已提交”分开统计。
- 本 Spec 不定义告警规则（SPEC-005）、设备遥控（SPEC-007）或媒体证据（SPEC-008）。

## 2. capability 与档位

| 能力 | standard | full |
|---|---|---|
| `power.alarm.core` | 单站点值班表、APP/站内信、已配置渠道、基础角色升级 | 同一核心能力 |
| `power.alarm.advanced` | 关闭跨站策略和集中治理 | 跨站值班、集中策略、复合升级和策略模板 |
| mini | 无电力值班菜单、计划、消费者和通知任务 | 不适用 |

安全告警 capability 存在活动告警或待处理通知时不得直接关闭；必须先停止新策略、排空或受控取消队列并保留审计。

## 3. 角色与权限族

- 值班表查看、维护和发布分别授权。
- 告警确认属于 SPEC-005；通知策略维护不得授予普通处置人员默认权限。
- 替班必须由被授权人员发起和批准，记录原班、替班人、时段和原因。
- 跨站策略和集中模板只在 full 开放，并重新校验租户、组织和站点数据范围。
- 通知正文中的设备、人员和联系方式按最小必要原则展示，日志不得记录完整手机号、Token 或渠道密钥。

## 4. 值班模型

### 4.1 对象

- `DutyRoster`：站点、时区、有效期、版本和状态。
- `DutyShift`：左闭右开 `[start,end)` 的班次、班组、主值/备值。
- `DutySubstitution`：替班、批准、有效期和审计。
- `EscalationPolicy`：告警类型/等级、确认时限、升级级别、接收角色、渠道、有效窗口和停止条件。

班次按站点 IANA 时区录入，持久化为 UTC 并保留原偏移。重叠、空档和夏令时歧义必须在发布前显式校验；已发布版本不可原地修改。

### 4.2 接收人解析

- 以告警发生时有效的策略版本和每次升级时有效的值班快照解析接收人，并保存解析结果摘要。
- 人员离职、停用或失去站点权限时不得继续作为新通知接收人；历史记录保持。
- 无有效接收人时告警仍成立，通知请求进入 `NO_RECIPIENT` 并立即触发备用角色/平台运维告警。
- 同一人员多角色命中按策略去重，不得因此跳过其要求的多个独立渠道。

## 5. 通知请求与发送状态

### 5.1 可靠请求

告警事务提交后通过 Outbox 或等价原子机制创建版本化 `AlarmNotificationRequested`。通知请求至少包含：

- `notificationId`、`alarmId`、`tenantId`、`siteId`。
- `policyId/policyVersion`、`escalationLevel`、接收人快照。
- 渠道、模板版本、`validUntil`、幂等键和关联追踪 ID。
- 脱敏参数引用，不直接携带渠道凭据。

幂等键固定覆盖 `alarmId + policyVersion + escalationLevel + recipientId + channel + notificationPurpose`。

### 5.2 状态

提交状态与外部送达状态是两个正交字段，禁止用复合字符串表达二者：

```text
submitStatus:
PENDING → SUBMITTING → SUBMITTED
   ├→ RETRY_WAIT → SUBMITTING
   ├→ FAILED_FINAL
   ├→ EXPIRED
   ├→ CANCELLED
   ├→ NO_RECIPIENT
   └→ SUPPRESSED

deliveryStatus:
NOT_APPLICABLE | UNKNOWN | DELIVERED | UNDELIVERED
```

- `SUBMITTED` 仅表示平台已将请求提交并被渠道受理，不等于最终送达；成功提交时 `deliveryStatus` 初始为 `UNKNOWN`（不提供回执的渠道保持 `UNKNOWN`）。
- 只有可信渠道回执才能将 `deliveryStatus` 更新为 `DELIVERED` 或 `UNDELIVERED`；禁止伪造 `DELIVERED`，提交状态与回执状态始终分开存储。
- `NO_RECIPIENT`、`SUPPRESSED` 等提交终态的 `deliveryStatus` 为 `NOT_APPLICABLE`；所有状态变化追加记录，禁止覆盖历史尝试。
- APP/站内信可通过已读/确认事件补充用户侧结果，但不得自动等同告警确认。

## 6. 重试、有效窗口与升级

- 只对明确可重试错误退避，默认序列为 `5s、30s、2m、5m`；策略可下调，单渠道单通知最多 8 次且不得超过 `validUntil`。
- 认证失败、模板非法、号码非法、权限拒绝等最终错误不重试，立即进入备用渠道或升级。
- 到达 `validUntil` 后标记 `EXPIRED`，不得补发陈旧通知；若告警仍活动，由升级策略决定创建新的升级通知请求，而不是复活旧请求。
- 告警确认、关闭或误报终态按策略停止未开始的低优先级通知；已提交请求只记录取消不可保证，不伪造撤回。
- 升级以 SPEC-005 的正交 `escalationLevel` 和 `AlarmEscalated` 事件表达，不修改主状态。
- 每次升级采用 compare-and-set，重复定时触发只生成一个级别事件和一组幂等通知。

## 7. 维护模式与抑制

- SPEC-005 提供维护上下文和抑制决策；通知域只执行决策，不自行停止告警评估。
- 通知域消费 `device.alarm.suppression-decided.v1`，只按 `SUPPRESSED`、`NOT_SUPPRESSED` 或 `REEVALUATED` 执行通知，不自行产生告警评估结论。
- `SUPPRESSED` 必须记录策略、原因、时段和原本接收人/渠道摘要。
- 维护结束时只重评估仍活动、仍在有效窗口且当前策略要求通知的告警；过期请求保持 `EXPIRED/SUPPRESSED`。
- 紧急告警不可被 `IGNORED`；维护模式是否允许抑制某个紧急渠道必须由预先批准的安全策略明确，且至少保留一个不可抑制的值守或平台运维出口。

## 8. 渠道适配

首期适配框架支持 APP、站内信、短信、电话和企业微信；实际可用渠道以部署配置和现场联调为准。

- 统一端口负责提交、查询回执、错误分类和限流，不把厂商 DTO 暴露给业务域。
- 渠道凭据来自环境变量/Nacos/密钥服务，不入库明文、不出现在消息和日志中。
- 每个网络调用设置连接/读取超时、并发上限和熔断；渠道故障不得阻塞消费线程。
- 模板发布版本不可原地修改；渲染输入使用白名单字段并防模板注入。
- 回调必须验签、防重放并按租户/渠道实例绑定。

## 9. API、事件与错误

公共 API 经 `/api/v1/device/alarm-notifications/**` 提供值班表/策略版本、通知查询和送达统计；写操作使用资源版本与幂等键。

事件至少包含：

- `device.alarm.notification-requested.v1`
- `device.alarm.notification-status-changed.v1`
- `device.alarm.escalated.v1`

稳定错误包括：`DUTY_ROSTER_CONFLICT`、`DUTY_ROSTER_GAP`、`NOTIFICATION_NO_RECIPIENT`、`NOTIFICATION_EXPIRED`、`CHANNEL_UNAVAILABLE`、`CHANNEL_FINAL_REJECTED`、`NOTIFICATION_CAPABILITY_UNAVAILABLE`。

## 10. 指标口径与非功能预算

- 告警创建到首个通知请求持久化 P95 不高于 1 秒。
- 通知请求持久化到平台首次提交渠道 P95 目标不高于 10 秒；外部渠道耗时另计并单独展示。
- “平台提交成功率”=`有效窗口内被渠道受理的唯一通知 / 有效窗口内进入提交阶段且配置有效的唯一通知 × 100%`，MVP 不低于 99%。
- 无接收人、配置非法、被批准维护策略抑制分别披露，不得通过删除失败记录或扩大排除项达标。
- “外部送达率”只统计提供可信回执的渠道；未知回执单列，禁止混入成功。
- 指标至少包含队列积压、请求年龄、各状态数量、重试、最终失败、无接收人、过期、抑制、渠道耗时和升级次数。
- 队列、批次和并发必须有 manifest/配置硬上限；不得无界重试或缓存。

## 11. 兼容与回滚

- 现有 `iot-alert-notification-send` 可在兼容期作为适配入口，但必须映射到统一 `alarmId` 和持久通知请求；不得继续以来源 `alertId` 作为处置事实。
- 迁移期双发新旧通知时必须使用共享幂等键防止人员收到两份；按渠道、租户、等级和接收人对账。
- 回滚停止新消费者并恢复旧入口，不删除新通知请求、状态或审计；旧接口至少保留一个发布周期。
- 渠道切换不影响告警事实；单渠道回滚可独立进行。

## 12. 验收场景

### A. 正常值班通知

```gherkin
Given site-a 当前班次主值和备值有效
And 重要告警策略要求 APP 与短信
When 告警创建
Then 通知请求在 1 秒目标内持久化
And 每个接收人/渠道只有一个幂等请求
And 告警事务不等待外部渠道返回
```

### B. 渠道故障与升级

```gherkin
Given 短信渠道返回可重试超时
When 请求仍在有效窗口内
Then 按有限退避重试
When 超过有效窗口且告警仍未确认
Then 原请求标记 EXPIRED 且不再补发
And 策略可创建下一升级级别的备用渠道请求
```

### C. 重复定时任务

```gherkin
Given 同一告警确认超时任务被并发执行两次
When 两个执行器尝试升级到 LEVEL_2
Then 只提交一个 AlarmEscalated 事件
And 每个接收人/渠道只有一条通知请求
```

### D. 无接收人

```gherkin
Given 发布后的值班表因人员停用没有有效接收人
When 紧急告警发生
Then 告警正常创建
And 通知请求标记 NO_RECIPIENT
And 触发不可抑制的平台运维出口与可观测告警
```

### E. 维护模式结束

```gherkin
Given 告警在维护模式中创建且通知被抑制
When 维护结束时告警仍活动但原通知已过期
Then 不补发原通知
And 仅在当前升级策略和新有效窗口要求时创建新请求
```

### F. 租户与渠道回调

```gherkin
Given 渠道回调签名无效或 notificationId 属于其他租户
When 回调到达
Then 服务端拒绝且不改变通知状态
And 日志不包含完整号码、密钥或 Token
```

### G. mini 与档位

```gherkin
Given 部署档位为 mini
Then 不创建电力值班菜单、调度、通知消费者或初始化模板
Given 档位为 standard
When 请求跨站集中策略
Then 返回 NOTIFICATION_CAPABILITY_UNAVAILABLE
```

## 13. 需求追踪

| ID | 要求 |
|---|---|
| NTF-001 | 告警事实提交 MUST 优先于通知且不等待渠道 |
| NTF-002 | 通知请求 MUST 持久化、幂等并有有效窗口 |
| NTF-003 | 可重试错误 MUST 有限退避，最终错误 MUST NOT 盲目重试 |
| NTF-004 | 过期通知 MUST NOT 补发 |
| NTF-005 | 升级 MUST 使用正交级别/事件且并发幂等 |
| NTF-006 | 平台提交与外部送达 MUST 分开统计 |
| NTF-007 | 无接收人 MUST 不阻止告警并触发备用出口 |
| NTF-008 | 维护模式 MUST 记录抑制决策且只重评估有效通知 |
| NTF-009 | 渠道凭据和个人联系方式 MUST 脱敏与最小化 |
| NTF-010 | standard/full MUST 共用实现，mini MUST 无任务残留 |

## 14. OPEN 门禁

- OPEN-006-01：短信、电话、企业微信的目标供应商、回执真实性和现场可用性尚未联调；未完成前只能声明适配框架，不得承诺渠道送达。
- OPEN-006-02：standard/full 的队列、并发和每站通知配额须由目标规模压测后写入 manifest。
- OPEN-006-03：现有 iot-sink/iot-message 通知链的幂等键、双发防重和数据迁移画像尚未完成。
- OPEN-006-04：通知模板、联系方式处理和回调验签需要安全专项评审；关闭前本 Spec 不得标记 Approved / Frozen。
