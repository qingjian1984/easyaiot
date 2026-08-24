# SPEC-007：安全遥控与操作票

> Spec ID：POWER-SPEC-007  
> 上游需求：POWER-PRD-002 1.2.2  
> 依赖：POWER-SPEC-001、POWER-SPEC-002；告警触发控制时关联 POWER-SPEC-005  
> 版本：0.2.0  
> 状态：In Review  
> 日期：2026-08-24  
> 架构决策：[ADR-011 Capability Manifest](../../架构决策/电力运维云平台/ADR-011-Capability-Manifest规范.md)、[ADR-012 产品根属性与服务参数单一事实](../../架构决策/电力运维云平台/ADR-012-产品根属性与服务参数单一事实.md)、[ADR-013 受控数据库迁移](../../架构决策/电力运维云平台/ADR-013-受控数据库迁移执行器.md)、[ADR-014 Outbox/Inbox](../../架构决策/电力运维云平台/ADR-014-Outbox事件Transport与消费者Inbox.md)、[ADR-018 内部服务与 NODE 请求认证](../../架构决策/电力运维云平台/ADR-018-控制面内部服务与NODE请求认证.md)  
> 目标里程碑：M3  
> 产品基线：[平台功能计划 1.5.0](../../架构设计/平台功能计划.md)、[项目开发宪法 1.6.0](../../开发规范/EasyAIoT项目开发宪法.md)

## 1. 目标与安全边界

在 `iot-device` 建立遥控申请、操作票、独立复核、联锁、执行、回执、结果复核和审计的唯一状态机。WEB、APP、SCADA、能源和运维域只能调用 `/api/v1/device/control/**`，不得直接发布设备命令或写设备影子。

本 Spec 适用于分合闸、刀闸、电容柜投切、风机等受控服务/属性。平台不替代继电保护和现场硬联锁；任何依赖缺失或状态不确定均 fail-closed。

## 2. 现有事实与迁移约束

当前仓库仍暴露 `/{deviceId}/invokeService`、`/{deviceId}/setProperties` 和命令下发入口，尚无安全控制申请状态机。实现阶段必须：

- 给物模型服务/属性增加版本化风险分类和安全控制标记。
- 对标记为受控的目标，旧入口必须转调统一流程或稳定拒绝，禁止旁路。
- 普通设备读取、低风险非受控配置不应被误归类为高风险遥控，但风险分类默认取更高等级。
- 迁移期所有旁路调用量必须可观测并有调用方清单，下线旧路径前至少保留一个发布周期。

## 3. capability 与档位

| 能力 | standard | full |
|---|---|---|
| `power.control.safe` | 可选；依赖完整才可启用，启用即具备全部高风险安全门禁 | 与 standard 共用安全核心 |
| `power.control.advanced` | 关闭集中审批、跨站票据和统管 API/菜单/任务 | 集中审批、跨站操作票治理、策略统管 |
| mini | 无电力遥控菜单、API 成功响应、后台任务、命令消费者或初始化策略 | 不适用 |

现场硬禁用、权限、审计、联锁和幂等不能作为灰度变量。standard 缺任一强制依赖时必须整体关闭 `power.control.safe`。

## 4. 风险分类

### 4.1 等级

- `HIGH`：高低压分合闸、刀闸、电容柜投切及安全评审确定的高风险操作。
- `LOW_CONTROLLED`：安全评审批准的低风险辅助控制，例如满足现场条件的通风设备控制。
- `NOT_CONTROLLED`：不进入本流程的普通读取或低风险配置；仍按原接口授权和审计。

未知、未分类或规则冲突一律按 `HIGH` 拒绝直接执行。

### 4.2 白名单与版本

- 风险分类绑定产品模板版本、服务/属性编码、动作和值域，发布后不可原地修改。
- `LOW_CONTROLLED` 必须来自安全负责人批准的版本化白名单；站点普通管理员无权临时降级。
- 高风险流程不得豁免操作票、独立复核、联锁、二次确认、回执和审计。
- 低风险简化仍保留授权、服务端校验、幂等、超时、回执和审计；是否豁免独立复核由批准策略明确。
- 任一风险分类、目标动作、联锁规则或操作票变化使未执行批准失效。

## 5. 权限与职责分离

| 权限 | 用途 | 分离规则 |
|---|---|---|
| `device:control:switch` | 创建申请、在授权条件下执行 | 不包含审批 |
| `device:control:approve` | 批准/拒绝 | 审批人不得为申请人 |
| 控制策略管理族 | 风险分类、硬禁用、联锁策略 | 与普通执行权限分离 |
| 控制审计查看族 | 查询操作票和审计 | 按租户/站点数据范围 |

- 服务端校验用户、租户、站点、设备、操作、班组/组织范围和当前授权。
- 高风险控制不得由同一人员通过角色叠加同时完成申请与审批。
- break-glass 不在 M3 范围；未来如需必须独立安全 ADR，不得复用管理员万能权限。

## 6. 控制申请状态机

```text
REQUESTED
  ├→ PRECHECK_FAILED [terminal]
  ├→ CANCELLED [terminal]
  └→ PENDING_APPROVAL
       ├→ REJECTED [terminal]
       ├→ CANCELLED [terminal]
       ├→ EXPIRED [申请审批截止过期，terminal]
       └→ APPROVED
            ├→ INVALIDATED [terminal]
            ├→ EXPIRED [批准执行窗口过期，terminal]
            └→ EXECUTING
                 ├→ SUCCEEDED [terminal]
                 ├→ FAILED [terminal]
                 └→ UNKNOWN [terminal，需人工复核]
```

- `REQUESTED` 创建事务内同步完成预检；预检失败进入 `PRECHECK_FAILED`，预检通过只追加 `PRECHECK_PASSED` 动作记录和联锁快照，并进入 `PENDING_APPROVAL`，不建立独立的持久化预检状态。
- `PENDING_APPROVAL` 的申请审批截止过期返回 `CONTROL_REQUEST_EXPIRED`；`APPROVED` 的批准执行窗口默认有效 5 分钟、配置上限 15 分钟且仅执行一次，窗口过期返回 `CONTROL_APPROVAL_EXPIRED`。
- `UNKNOWN` 表示发送后无法确认设备最终结果；只能查询、写后读或人工复核，不得自动重发或伪造成功/失败。
- 状态转换使用乐观锁和追加式动作记录；同一幂等键重复请求返回原申请。
- 设备回执成功后仍按策略执行写后读/状态复核；回执与实际状态冲突进入 `UNKNOWN` 并告警。
- 已进入 `EXECUTING` 不接受取消；取消请求只记录意图，不伪造设备撤回。

## 7. 申请与操作票

申请至少保存：

- `controlRequestId`、幂等键、租户、站点、设备。
- 物模型版本、服务/属性编码、目标动作和值、风险分类版本。
- 申请人、原因、业务关联 ID、操作票编号/版本。
- 当前状态快照、联锁输入快照、联锁策略版本和摘要。
- 批准人、批准时间、执行窗口、执行人和二次确认摘要。
- `commandId`、发送时间、回执、写后读和最终结果。

操作票必须包含目标、动作、前置条件、风险、安全措施、影响范围和有效期。高风险动作未关联有效票据时不得进入审批。

## 8. 预检、联锁与执行前复检

预检和执行前复检都必须执行：

- capability、现场硬禁用、权限和数据范围。
- 设备在线/通信质量、目标当前状态和物模型可用性。
- 操作票有效性、风险分类、批准窗口和执行次数。
- 联锁规则、依赖测点质量和新鲜度。
- 并发互斥：同设备/同回路的冲突控制申请。

预检通过不代表执行时一定允许；执行前必须重新取权威状态。任何输入缺失、质量异常、超时或条件变化都拒绝执行并使批准失效。

联锁表达式只能使用受控白名单条件，不允许脚本；高风险联锁策略的变更必须双人审批并版本化。

## 9. 命令与回执

- 每个申请最多生成一个执行 `commandId`；重试 HTTP 请求只能查询/返回同一执行，不创建新命令。
- `iot-device` 审批通过后调用既有物模型服务/属性下发，`iot-sink` 负责协议发送和设备回执。
- 下行消息携带 `controlRequestId`、`commandId`、租户、设备、目标动作、过期时间和契约版本。
- 回执消费者按 `commandId` 幂等；相同 ID 不同结果进入冲突隔离。
- 无设备原生幂等能力时，平台不得通过网络重试重复下发；超时进入不确定状态。
- 最终结果、原始回执摘要和写后读证据不可覆盖。

## 10. API 与错误语义

公共 API：

- `POST /api/v1/device/control/requests`
- `GET /api/v1/device/control/requests/{id}`
- `POST /api/v1/device/control/requests/{id}/approve`
- `POST /api/v1/device/control/requests/{id}/reject`
- `POST /api/v1/device/control/requests/{id}/execute`
- `POST /api/v1/device/control/requests/{id}/cancel`
- `GET /api/v1/device/control/requests/page`

内部回执经受认证的 API 或版本化事件进入，不暴露为公共匿名端点。

| 错误码 | 语义 |
|---|---|
| `CONTROL_CAPABILITY_UNAVAILABLE` | 档位或依赖未启用 |
| `CONTROL_PERMISSION_DENIED` | 权限/数据范围拒绝 |
| `CONTROL_REVIEWER_CONFLICT` | 申请人与审批人相同 |
| `CONTROL_HARD_DISABLED` | 现场硬禁用 |
| `CONTROL_INTERLOCK_FAILED` | 联锁不满足 |
| `CONTROL_STATE_UNCERTAIN` | 状态/质量不确定，fail-closed |
| `CONTROL_REQUEST_EXPIRED` | 申请在审批截止前未完成审批 |
| `CONTROL_APPROVAL_EXPIRED` | 批准已过期 |
| `CONTROL_APPROVAL_INVALIDATED` | 目标/策略/票据/联锁发生变化 |
| `CONTROL_ALREADY_EXECUTED` | 已执行或正在执行 |
| `CONTROL_COMMAND_INDETERMINATE` | 兼容错误语义：超时或回执冲突使主状态为 `UNKNOWN`，禁止自动重发 |
| `CONTROL_VERSION_CONFLICT` | 并发状态冲突 |

`CONTROL_COMMAND_INDETERMINATE` 仅作为兼容错误标识，不是持久化状态；主状态机只使用 `UNKNOWN` 表示结果无法确认。

## 11. 审计与非功能预算

- 所有申请、预检、审批、拒绝、执行、回执、复核和失败以追加式审计保存，普通业务用户不可删除。
- 审计列表默认 50、硬上限 500；超量导出使用异步任务和临时授权 URL。
- 创建申请和审批 API P95 目标不高于 1 秒（不含设备网络）；执行请求必须在服务端决策超时内返回已接受/拒绝，不等待无界设备响应。
- 设备命令超时按协议/设备模板版本配置并有全局上限；执行线程、队列和并发有硬限制。
- 指标至少包括申请量、拒绝原因、审批时长、过期、联锁失败、执行成功、超时、不确定、回执冲突和旁路拦截。
- 日志不得记录密码、Token、完整敏感参数或可重放命令正文。

## 12. 兼容与回滚

1. 先标记受控服务/属性并上线只审计不拦截的旁路观测，但高风险生产启用前必须完成安全评审。
2. 发布 `/device/control/**` 和状态机，调用方迁移到新入口。
3. 对受控目标将旧 `invokeService/setProperties/issueCommands` 转调或稳定拒绝；对账旁路调用为零。
4. 至少一个发布周期后停止旧受控写路径。

回滚可关闭新申请入口并恢复只读查询，但不得恢复高风险旁路写入；已创建申请和审计保持可查。数据库回滚不得删除安全记录。

## 13. 验收场景

### A. 高风险正常流程

```gherkin
Given 高压开关合闸被标记 HIGH 且现场未硬禁用
And 操作票、设备状态和联锁均有效
When 申请人提交并由另一名授权人员批准
And 执行人在 5 分钟窗口内二次确认
Then 系统只生成一个 commandId
And 回执与写后读成功后状态为 SUCCEEDED
And 全过程可审计
```

### B. 同人审批与过期

```gherkin
Given 用户创建了高风险控制申请
When 同一用户尝试批准
Then 返回 CONTROL_REVIEWER_CONFLICT
When 申请在审批截止前未完成审批
Then 返回 CONTROL_REQUEST_EXPIRED 且不发送命令
When 申请已独立审批但超过批准执行窗口再执行
Then 返回 CONTROL_APPROVAL_EXPIRED 且不发送命令
```

### C. 联锁变化

```gherkin
Given 申请时联锁预检通过并已批准
When 执行前保护状态变化或测点质量为 TIMEOUT
Then 批准进入 INVALIDATED
And 不向设备发送命令
```

### D. 超时不重发

```gherkin
Given 非幂等分闸命令已发送但设备回执超时
When 客户端重复执行请求
Then 返回原申请的 UNKNOWN
And 不生成新 commandId 或重复下发
```

### E. 现场硬禁用

```gherkin
Given 现场硬禁用已开启
And 申请、审批、设备在线和联锁均满足
When 执行人提交执行
Then 返回 CONTROL_HARD_DISABLED
And 设备侧无发送记录
```

### F. 低风险白名单

```gherkin
Given 风机启动属于已批准 LOW_CONTROLLED 白名单且策略允许单人流程
When 授权用户执行简化流程
Then 系统仍校验状态、幂等、超时并保存回执与审计
Given 未分类服务请求简化流程
Then 系统按 HIGH 拒绝旁路
```

### G. 旧入口旁路

```gherkin
Given 物模型服务已标记为受控
When 用户直接调用旧 invokeService 或 setProperties
Then 请求转入统一控制申请或返回稳定旁路拒绝
And 不直接发布设备命令
```

### H. 档位与租户

```gherkin
Given standard 未部署完整控制依赖或部署为 mini
When 请求创建控制申请
Then 返回 CONTROL_CAPABILITY_UNAVAILABLE
And 不创建命令、消费者或残留任务
Given 用户无目标站点数据权限
Then 服务端拒绝且不泄露设备控制状态
```

## 14. 需求追踪

| ID | 要求 |
|---|---|
| CTL-001 | `iot-device` MUST 成为安全遥控唯一责任模块 |
| CTL-002 | 高风险控制 MUST 执行操作票、独立复核、联锁、二次确认、回执和审计 |
| CTL-003 | 风险分类 MUST 版本化，未知目标 MUST fail-closed |
| CTL-004 | 批准 MUST 默认 5 分钟、上限 15 分钟且单次执行 |
| CTL-005 | 执行前 MUST 重新校验权威状态与联锁 |
| CTL-006 | 同一申请 MUST 只生成一个 commandId |
| CTL-007 | 非幂等命令超时 MUST NOT 自动重发 |
| CTL-008 | 现场硬禁用 MUST NOT 被平台绕过 |
| CTL-009 | 旧受控写入口 MUST 转调或拒绝，不得旁路 |
| CTL-010 | standard/full MUST 共用安全核心，依赖不足或 mini MUST 关闭 |
| CTL-011 | 所有状态和安全动作 MUST 追加审计且不可由普通用户删除 |
| CTL-012 | 权限、租户、站点和设备范围 MUST 由服务端执行 |
| CTL-013 | 申请审批截止与批准执行窗口 MUST 使用不同过期语义和稳定错误码 |

## 15. OPEN 门禁

- OPEN-007-01：现场设备类型、动作、联锁输入和回执能力清单尚未由电气安全负责人签字；未签字目标保持禁用。
- OPEN-007-02：统一控制表、操作票、动作审计、Inbox/Outbox 和中文注释 DDL 尚待 TD 冻结。
- OPEN-007-03：旧 `invokeService/setProperties/issueCommands` 的生产调用方与受控目标画像尚未完成。
- OPEN-007-04：无原生幂等设备的状态复核、超时和人工处置 Runbook 尚待现场样本验证。
- OPEN-007-05：安全专项评审、权限 Seed、旁路阻断和故障注入完成前，本 Spec 不得标记 Approved / Frozen，`power.control.safe` 默认关闭。
