# PRD-02 SDD 方案设计评审报告（SPEC-005～008 / TD-006～009 / ADR-010 1.1.0）

> 历史输入说明：本报告保留评审时原貌；最终采纳范围、口径校正与落地证据以 [PRD-02 SDD 方案设计评审处置记录](./PRD-02-SDD方案设计评审处置记录.md) 为准。

> 报告版本：1.0.0
> 评审日期：2026-08-24
> 评审对象：
> [SPEC-005 0.1.0](../规格/电力运维云平台/SPEC-005-复合告警规则与状态机.md)、[SPEC-006 0.1.0](../规格/电力运维云平台/SPEC-006-值班通知与告警升级.md)、[SPEC-007 0.1.0](../规格/电力运维云平台/SPEC-007-安全遥控与操作票.md)、[SPEC-008 0.1.0](../规格/电力运维云平台/SPEC-008-事故追忆与视频证据.md)、[TD-006 0.1.0](../技术设计/电力运维云平台/TD-006-统一告警与规则引擎.md)（含三份附件与 V011/U011 候选 DDL、事件 Schema）、[TD-007 0.1.0](../技术设计/电力运维云平台/TD-007-通知与升级编排.md)、[TD-008 0.1.0](../技术设计/电力运维云平台/TD-008-安全遥控与操作票.md)、[TD-009 0.1.0](../技术设计/电力运维云平台/TD-009-事故证据与媒体归档.md)、[ADR-010 1.1.0](../架构决策/电力运维云平台/ADR-010-统一告警模型迁移.md)
> 评审基线：[《EasyAIoT 项目开发宪法》1.6.0](./EasyAIoT项目开发宪法.md)、[《平台功能计划》1.5.0](../架构设计/平台功能计划.md)、[PRD-02 1.2.1](../产品需求/电力运维云平台/PRD-02-监控告警与安全控制.md)
> 评审视角：双基线合规 · SPEC→TD 追踪一致性 · 状态机/DDL 合同正确性 · 安全默认值 · 迁移与回滚 · 与已冻结局部合同（P02-M2-01）的一致性
> 评审方法：逐文档逐节核对 + 跨文档字段/枚举/错误码比对 + V011 候选 DDL 逐约束审读 + 已实现 `AlarmStateMachine` 代码与冻结矩阵逐行比对

---

## 一、评审结论（摘要）

| 维度 | 结论 | 说明 |
|---|---|---|
| 双基线合规 | ✅ 通过 | 宪法第二条之二/之三/之五、§5/§6/§8 关键 MUST 均有对应设计；mini 无残留、standard/full 单一实现、fail-closed、中文 DDL 注释均已落实 |
| PRD→SPEC 追踪 | ✅ 通过 | PRD-02 §4～§8 的每条业务规则均可落到 SPEC-005～008 的 MUST/场景；SPEC 各自的 12/10/12/10 条追踪 ID 与验收场景映射完整 |
| SPEC→TD 追踪 | ⚠️ 有条件通过 | 主链路完整，但存在 2 处 Spec 声明未下沉到 TD（H-01、M-04）、1 处 TD 状态机图与 Spec 文本不一致（M-01）、1 处 TD 间相互引用不一致（M-02） |
| 状态机/DDL 合同 | ⚠️ 有条件通过 | 冻结矩阵与 V011 DDL 工程质量高，但发现 1 处已实现代码与 PRD 基线冲突（H-02）、1 处 DDL 唯一约束缺口（M-03） |
| 安全默认值 | ✅ 通过 | 紧急不可忽略、未知风险按 HIGH、非幂等不重发、现场硬禁用、fail-closed 原则贯穿四份 Spec 与 TD |
| 迁移与回滚 | ✅ 通过 | 五步迁移、对账归零、回滚不删事实的模式四域一致；与 ADR-010/013/014 对齐 |
| OPEN 门禁诚实度 | ✅ 通过 | 25 项 OPEN（TD 19 + Spec 侧承接）全部有责任角色归属，无以假设参数冒充关闭的情况 |

**综合裁定：有条件通过（CONDITIONAL PASS）——维持 TD-006～009 / SPEC-005～008 `In Review`，维持 P02-M2-02 及之后切片锁定。**
1 项 HIGH（H-02）建议在下一轮 TD 修订中优先关闭；4 项 MEDIUM、6 项 LOW 随 TD/SPEC 修订处置。无 CRITICAL 阻断——即当前方向不需要推翻，但 **P02-M2-02（DDL 切片）解锁前必须先处置 H-02、M-01、M-03**，否则 DDL 与纯领域合同将带着已知冲突进入实现。

---

## 二、总体评价

### 2.1 突出优点

1. **四域责任边界自洽且与宪法第二条之二严格对齐**——`iot-device` 唯一控制面、来源模块只存不可变证据、前端只经网关、跨模块只经 `iot-*-api`。SPEC-005 §2「禁止边界」、TD-006 §3 模块结构、TD-006 现状画像 §4.2「禁止复用」三处口径完全一致，没有留下第二事实源的口子。
2. **正交升级模型（ADR-010 1.1.0）在五份文档中零漂移**——PRD §4.2、SPEC-005 §7、TD-006 附件一、已实现 `AlarmStateMachine.escalate()`、V011 的 `escalation_level` 独立列 + `status` CHECK 约束五处表达完全一致，升级均不改主状态。
3. **现状画像诚实且可直接执行**——TD-006 现状画像对旧链的六大缺陷（周期分裂、tenant=0、非原子 Kafka、`kafka_sent` 伪事实、`int` 截断、`/device/alarm` 假成功）逐一定位到类/方法级，并给出「可复用/禁止复用」二分清单和 §6 迁移映射候选表。这是本轮文档链中质量最高的现状分析。
4. **V011 候选 DDL 工程质量高**——全部 9 表中文 COMMENT、`TIMESTAMPTZ` 统一、租户 CHECK、复合唯一键含 `tenant_id`、`cycle_identity_hash` 唯一约束落地周期唯一性、Inbox/Outbox 带租约与死信状态、`octet_length` 有界检查、部分索引（`WHERE status IN (...)`)精准匹配领取路径。U011 的「非空即拒绝卸载」守卫正确实现了「有事实不可 DDL 回滚」的宪法要求。
5. **fail-closed 安全语义贯穿**——SPEC-007 未知目标按 HIGH、联锁输入陈旧/坏质量拒绝、非幂等超时不重发、现场硬禁用优先级最高；SPEC-006 平台提交与外部送达分离、无接收人不阻塞告警；SPEC-008 媒体失败成缺口不回滚告警。三域的失败语义均不会静默丢事实或伪造成功。
6. **冻结纪律真实执行**——P02-M2-01 局部冻结记录明确「只冻结构不动行为」，已实现代码严格限定在 11 个文件、33 个测试 0 跳过、静态边界检查通过；`ACKNOWLEDGED + ACK` 拒绝交由幂等层重放的设计说明纯状态机与应用层职责切分有真实思考。

### 2.2 主要短板

1. **PRD 基线 1.2.1 与已冻结实现存在一处生命周期语义冲突未裁决**（H-02，详见 §三）。
2. **SPEC 声明与 TD 设计之间存在少量单点漂移**——最典型的是 TD-008 状态机图多出 `PRECHECKED` 状态且省略 `EXPIRED` 分支，与 SPEC-007 §6 文本状态机不一致（M-01）。
3. **TD-006 尚未承接 SPEC-005 已承诺的两项内容**（忽略到期不覆盖期间的关闭、维护模式不切割周期的落库表达）（H-01、M-04）。
4. **跨 TD 依赖交接缺少一致性校验**——TD-006/007 对「维护抑制事件」的命名不一致（M-02）。

---

## 三、问题清单

### 3.1 HIGH

#### H-01｜SPEC-005 的「忽略到期不覆盖期间恢复/关闭」承诺未在 TD-006 §5.2 CAS 条件中完整表达

- **位置**：[SPEC-005 §7](../规格/电力运维云平台/SPEC-005-复合告警规则与状态机.md)「到期任务用乐观锁恢复，不能覆盖期间的恢复或关闭」；[ADR-010](../架构决策/电力运维云平台/ADR-010-统一告警模型迁移.md)「到期仅在记录仍处于同一次忽略且版本匹配时恢复」；[TD-006 §5.2](../技术设计/电力运维云平台/TD-006-统一告警与规则引擎.md)。
- **事实**：TD-006 §5.2 写「忽略到期任务只对 `IGNORED + expectedVersion + ignoredUntil<=now` 做 CAS」——含状态、版本、时间三条件，但**未包含 ignoredFromStatus 一致性**；若到期任务读取快照后、CAS 前，记录经历了 `IGNORED → ACKNOWLEDGED → IGNORED`（第二次忽略、版本恰好回到读取值在正常递增下不可能，但 **IGNORED → ACK → 再次 IGNORE 且第一次快照被复用/重试**的场景下 `ignoredUntil` 可能属于旧忽略窗口），CAS 仍可能把状态恢复为旧的 `ignoredFromStatus`。ADR-010 的表述「仍处于**同一次忽略**」实际要求比 TD-006 §5.2 现有三条件更强。
- **影响**：并发窗口窄但违反 SPEC-005 MUST 语义；属于「SPEC 承诺了、TD 实现语义弱化」的追踪缺口。
- **建议**：TD-006 §5.2 CAS 条件补第四条件「`ignored_from_status` 与任务快照一致」，或在任务领取时携带忽略动作的 `action_log.sequence_no` 一并 CAS。修订随 TD-006 下一版处理，不阻塞其他章节评审。

#### H-02｜PRD-02 §4.2 状态机图缺少 `PROCESSING → IGNORED` 之外，已实现纯状态机允许 `IGNORED + ACK`，而 PRD 基线图中 `IGNORED` 只能回到原状态或 RECOVERED——「忽略后确认」存在基线裁决缺口

- **位置**：[PRD-02 §4.2 状态图](../产品需求/电力运维云平台/PRD-02-监控告警与安全控制.md)；[TD-006 附件一 §2](../技术设计/电力运维云平台/TD-006-状态转换与并发矩阵.md)「IGNORED + ACK → ACKNOWLEDGED（允许主动确认；清除当前忽略窗口）」；已实现 [AlarmStateMachine.acknowledge()](../../DEVICE/iot-device/iot-device-biz/src/main/java/com/basiclab/iot/device/alarm/domain/AlarmStateMachine.java)。
- **事实**：PRD-02 1.2.1 基线状态图中 `IGNORED` 的出边只有三条：`→ ACTIVE`（原状态恢复）、`→ ACKNOWLEDGED`（原状态恢复）、`→ RECOVERED`（数据恢复）。但 TD-006 冻结矩阵和已验收的 P02-M2-01 实现均允许 `IGNORED + ACK → ACKNOWLEDGED` 作为**人员主动确认并终止忽略**的路径，且该转换已在冻结记录 1.0.0 中冻结、已由 Sol 验收。两者不一致：要么 PRD 图遗漏了这条合法边（产品语义上「值班人员忽略后又被叫回来直接确认」是合理路径），要么实现越过了基线。
- **影响**：PRD 是上层基线，SPEC/TD/实现与它冲突时按宪法 §1.2 必须先修基线或裁决。当前 P02-M2-01 已 `SOL-ACCEPTED`，若不裁决，后续 P02-M2-03（API 切片）做权限与场景映射时会产生「PRD 场景列表里没有这条转换」的验收缺口。
- **建议**：在 PRD-02 下一个小版本（1.2.2）补画 `IGNORED → ACKNOWLEDGED: 人员确认并解除忽略` 边，或在 PRD 基线记录中显式裁决「ACK 隐含解除忽略、按主动确认处理」。**该问题不否定已实现代码——实现与冻结矩阵自洽，缺口在 PRD 图**。

### 3.2 MEDIUM

#### M-01｜TD-008 §4 状态机图与 SPEC-007 §6 状态机不一致（多出 `PRECHECKED`、缺少两个 `EXPIRED` 分支的区分）

- **事实**：SPEC-007 §6：`REQUESTED → PRECHECK_FAILED/CANCELLED/PENDING_APPROVAL`，即**预检失败是独立终态、通过则直接进待审批**；TD-008 §4 却是 `REQUESTED → PRECHECKED → PENDING_APPROVAL`，把预检通过画成显式状态，且 `EXPIRED` 只在图尾出现一次。SPEC-007 明确有两个 EXPIRED：**申请待审批期间过期**（PENDING_APPROVAL → EXPIRED）与**批准后执行窗口过期**（APPROVED → EXPIRED）；TD-008 图中 APPROVED 的出边只有 `INVALIDATED/EXPIRED/EXECUTING`（这条有），但 `PENDING_APPROVAL → EXPIRED` 分支在 TD 图中缺失。
- **影响**：`PRECHECKED` 若是持久化状态，则「申请提交后、审批前」多一个可查询状态，API 语义与 SPEC 验收场景 B（「独立审批后超过批准窗口再执行→CONTROL_APPROVAL_EXPIRED」）之外的「无人审批到期」场景无错误码对应；若是笔误，则 TD 与 Spec 冻结状态集合不一致会直接传导到 DDL CHECK 约束。
- **建议**：TD-008 修订时与 SPEC-007 §6 逐状态对齐；若确需 `PRECHECKED`（预检通过落库留痕），须回 SPEC-007 补状态与验收场景，并补 `CONTROL_REQUEST_EXPIRED` 或复用语义说明。

#### M-02｜TD-006 与 TD-007 对「维护抑制决策事件」的引用名称不一致

- **事实**：TD-007 §3.1「TD-006 在告警事务 Outbox 发布 raised/status/escalated/**suppression** 事件」——出现了 `raised`（与事件合同裁决的 `created` 命名不一致，见 L-01）和 `suppression`；而 TD-006 §7 与事件合同附件的五事件清单（created/occurrence-recorded/recovered/status-changed/escalated）中**没有 suppression 事件**。SPEC-005 §8 明确「抑制命中、未命中和结束重评估都记录决策事件」，SPEC-006 §7「SPEC-005 提供维护上下文和抑制决策；通知域只执行决策」。
- **影响**：维护抑制决策是通知域判定 `SUPPRESSED` 的唯一输入。事件清单缺它，P02-M2-03 实现时要么私造事件（违反合同冻结），要么通知域改轮询告警表（违反职责边界）。
- **建议**：TD-006 事件合同裁决补第 6 个事件（如 `device.alarm.suppression-decided.v1`）或在事件与能力合同附件中显式裁决「抑制决策经 status-changed 的 reasonCode 表达」；两者择一，修订后 TD-007 §3.1 同步对齐。

#### M-03｜V011 `alarm_rule_version` 缺少「同租户下同一 rule 的 PUBLISHED 版本至多一个」约束，`uq_alarm_rule_version_hash` 可能与版本演进冲突

- **事实**：V011 对 `alarm_rule_version` 有 `(tenant_id, rule_id, version)` 和 `(tenant_id, rule_id, content_hash)` 两个 UNIQUE。① SPEC-005 §6.1「回滚通过重新激活已发布版本」+ TD-006「发布版本不可原地修改」隐含「同一时刻生效版本唯一」，但无部分唯一索引（如 `WHERE lifecycle='PUBLISHED'`）支撑——依赖应用层保证，DDL 层可能同时存在两个 PUBLISHED 版本；② `content_hash` 唯一约束意味着**同一规则的相同内容不允许作为新版本再次发布**——「回滚=重新激活旧版本」若实现为新建同内容版本行会被此约束拒绝；若实现为原地改 lifecycle，又与「发布后不可覆盖」冲突。SPEC-005 的回滚语义（「重新激活已发布版本，不删除新版本」）到底落哪种形态，DDL 与 Spec 均未闭环。
- **影响**：规则回滚是 SPEC-005 §6.1 的显式功能，当前 DDL 两种实现路径都有约束障碍。
- **建议**：候选 DDL 评审（TD006-OPEN-02 关闭前）必须裁决：a) 增加 `PUBLISHED` 部分唯一索引 + 回滚定义为「lifecycle 迁移（PUBLISHED→RETIRED→PUBLISHED 不改内容）」；或 b) 放宽 `content_hash` 唯一为 `(tenant_id, rule_id, version, content_hash)` 并允许同内容新版本。属设计决策，不需要立即改文件，但**必须在 P02-M2-02 解锁前写入 TD-006**。

#### M-04｜SPEC-005 §8「维护开始/结束不得切割周期」缺少 TD-006 落库表达

- **事实**：SPEC-005 §8「同一故障仍只形成一个活动周期，维护开始/结束不得切割周期」；SPEC-006 验收 E「不创建新周期」。TD-006 §6 规则执行与 §4 数据模型中，维护上下文只是 `alarm_record.maintenance_context_id` 可空外键，没有任何机制防止「维护结束重评估」被实现为关闭旧周期+建新周期（这正是 PRD §4.2 明令禁止的「不得因此创建重复告警周期」）。
- **影响**：该语义完全依赖实现者自觉；对账口径（迁移双写按周期数对账）也无法检测违规切割。
- **建议**：TD-006 §6 补一条：维护结束重评估仅允许走 `SOURCE_RAISED` 同周期 occurrence 路径（状态机已保证 RECOVERED 拒绝同 cycleKey 重开，`ALARM_SOURCE_CYCLE_CONFLICT` 就是防切割屏障——TD 应显式声明这一依赖，把它写为验收断言）。

### 3.3 LOW

| 编号 | 位置 | 问题 | 建议 |
|---|---|---|---|
| L-01 | TD-007 §3.1 | 「发布 raised/status/escalated 事件」——`raised` 已被事件合同裁决废弃（统一为 `created`），TD-007 是残留旧命名 | TD-007 修订时统一为 `created` |
| L-02 | TD-007 §2 | 数据模型清单含 `alarm_notification_inbox`/`alarm_delivery_callback_inbox` 落在消息域，但未说明与 TD-006 `alarm_source_inbox` 的 Schema 分离边界如何体现在迁移 runner（是否同一个 V 系列、不同模块归属） | 迁移章节补一句归属说明即可 |
| L-03 | SPEC-006 §5.2 | 状态图 `PENDING → SUBMITTING → SUBMITTED → DELIVERED` 中 `DELIVERY_UNKNOWN` 以 `SUBMITTED/DELIVERY_UNKNOWN` 复合标注出现，未定义它是独立状态还是 SUBMITTED 的子标记 | 建议明确为 `deliveryStatus` 正交属性，避免实现成两个枚举混合 |
| L-04 | TD-009 §2 | `incident_timeline_event` 幂等键 `(alarm_id, source_type, source_id, event_type)` 未含 `sequence_no`；同一来源同一类型动作若天然多次发生（如两次升级），source_id 相同会互斥 | 与 TD-006 `alarm_action_log` 的 sequence 语义对齐，幂等键补序号或说明 source_id 已含序 |
| L-05 | SPEC-008 §5 / TD-009 §4 | 在线查询上限「10 测点/1 小时/100,000 条」与 TQ 已上线的遥测查询配额（series ≤10、跨度 ≤31 天、pageSize ≤1000、累计 ≤100,000）口径不同（TQ 31 天 vs 事故 1 小时）。事故窗口更严是合理的，但两套配额共用 `TELEMETRY_QUERY_QUOTA_EXCEEDED` 还是独立稳定码未说明 | TD-009 补错误码映射说明，避免复用 TQ 码导致运营排障歧义 |
| L-06 | TD-008 §2 | `control_risk_catalog_version` 表名在 TD-008 §2 出现，但 §3 数据模型表中没有该表定义（§3 共 8 表不含它） | 补入 §3 表清单或说明并入哪张表 |

---

## 四、双基线逐项核对（摘要）

### 4.1 宪法 1.6.0

| 宪法条款 | 落实情况 | 判定 |
|---|---|---|
| 第二条之二 架构边界（DEVICE 控制面、前端不直连） | SPEC-005 §2、SPEC-007 §1、TD-006 §3 均显式声明；WEB/APP/SCADA 只经 `/api/v1/device/**` | ✅ |
| 第二条之二 跨模块只经 api/common | TD-006 事件 Schema 放 `iot-device-api`；TD-007 渠道 SPI 在 iot-message 域内 | ✅ |
| 第二条之三 mini 无电力残留 | 四份 Spec 各自的 mini 行 + TD-006 §9「不创建 Bean/路由/消费者/任务/初始化数据」+ 冻结记录排除项 | ✅ |
| 第二条之三 standard/full 单一主线 | 四域均声明共用实现、full 只加 capability；quota key 已在事件合同附件冻结且协议上限不可上调 | ✅ |
| 第二条之三 遥控安全不降档 | SPEC-007 §3「standard 缺任一强制依赖整体关闭」；PRD §8「不得提供低安全版本」 | ✅ |
| 第二条之四 兼容性（旧 API 一个发布周期） | SPEC-005 §12、SPEC-006 §11、SPEC-007 §12、SPEC-008 §11 四域一致 | ✅ |
| 第二条之五 安全默认 | 权限族全部服务端校验、防枚举、脱敏审计、凭据不入库、回调验签 | ✅ |
| §6.2 中文 DDL 注释 | V011 全表全列 COMMENT 已验证（9 表逐条审读） | ✅ |
| §6.2 时间 UTC/ISO 8601 | Envelope offset 时间、TIMESTAMPTZ、站点时区保留原偏移三处一致 | ✅ |
| §6.3 迁移可重跑/回滚 | 五步迁移 + U011 非空拒绝 + 各 TD「回滚不删事实」 | ✅ |
| §8 重试退避有界 | SPEC-006 §6 退避序列/8 次上限、Inbox/Outbox attempt_count CHECK | ✅ |

### 4.2 平台功能计划 1.5.0

| 计划条目（§4 告警/遥控矩阵） | 落实 | 判定 |
|---|---|---|
| standard：阈值/持续/迟滞/恢复/确认/关闭/值班通知/基础升级 | SPEC-005 §6 + SPEC-006 core 行 | ✅ |
| full 增量：复合规则/抑制/维护模式/跨站升级/集中治理 | `power.alarm.advanced` + 协议硬上限（16 叶/深 3/8 点/24h） | ✅ |
| 事件命名 `device.alarm.created.v1` | 事件合同附件 §1 已裁决并弃用 `raised` | ✅（TD-007 残留见 L-01） |
| 安全遥控申请/审批/联锁/回执/审计归 `iot-device` | SPEC-007 §1、TD-008 §1 | ✅ |

---

## 五、追踪完整性抽查

| 上游要求 | SPEC 条目 | TD 条目 | 结论 |
|---|---|---|---|
| PRD §4.2 紧急不可忽略 | SPEC-005 §7 + ALM-006 + 场景 C | 状态机 `ALARM_EMERGENCY_IGNORE_FORBIDDEN` + V011 无对应 CHECK（依赖应用层，可接受：等级与忽略的联合约束跨列语义复杂，CHECK 表达会僵化） | ✅ |
| PRD §4.2 IGNORED 语义 | SPEC-005 §7 | TD-006 §5.2 CAS | ⚠️ H-01 |
| PRD §4.2 IGNORED 出边 | SPEC-005 §7 | TD-006 附件一 §2 | ❌ H-02（PRD 图缺口） |
| PRD §4.1 规则版本化 | SPEC-005 §6.1 ALM-003 | TD-006 §4 + V011 alarm_rule_version | ⚠️ M-03（回滚约束） |
| PRD §4.3 渠道故障不阻止告警 | SPEC-006 NTF-001 | TD-007 §3 事务边界 | ✅ |
| PRD §5.3 批准 5/15 分钟单次 | SPEC-007 CTL-004 | TD-008 §4 | ✅ |
| PRD §5.3 命令唯一 ID | SPEC-007 CTL-006 | TD-008 §3 `command_id` UNIQUE | ✅ |
| PRD §4.4 事故窗口受 TelemetryStore 约束 | SPEC-008 §5 EVD-005 | TD-009 §4 | ✅（L-05 配额口径） |
| PRD §8.1 事故证据档位差异 | SPEC-008 §2 | TD-009 §1 | ✅ |

---

## 六、OPEN 门禁复核

对四份 TD 的 19 项 OPEN 与四份 Spec 的 20 项 OPEN 逐项核验：

- 全部 OPEN 均有明确责任角色（产品/运维/安全/集成负责人或压测证据），无「待定 TBD」。
- 未发现以假设值冒充冻结的情况；V011 文件头「仅供评审，禁止执行」、`power.control.safe=false` 默认值、外部渠道「只保留禁用适配器，不返回伪成功」三处 fail-closed 声明与 OPEN 状态一致。
- 一处交叉确认：PRD-02 SDD 进度文档「P02-M2-01 Approved/Frozen」表述与核心合同局部冻结记录 1.0.0 的实际范围（**仅纯合同切片冻结**，SPEC-005/TD-006 整体仍 In Review）一致，无越权冻结。

**建议补充的 OPEN**（源自本报告新发现，应随对应文档下一版登记）：

| 登记到 | 新 OPEN | 来源 |
|---|---|---|
| TD-006 | TD006-OPEN-06：忽略到期 CAS 第四条件（同一次忽略一致性）设计裁决 | H-01 |
| TD-006 | TD006-OPEN-07：规则回滚的 DDL 约束形态（PUBLISHED 部分唯一 vs content_hash 放宽） | M-03 |
| TD-008 | TD008-OPEN-06：`PRECHECKED` 状态去留与双 EXPIRED 分支对齐 | M-01 |
| PRD-02 | 基线记录补：`IGNORED + ACK` 转换裁决 | H-02 |

---

## 七、对实现切片准入的影响

| 切片 | 当前门禁 | 本评审影响 |
|---|---|---|
| P02-M2-01（已完成） | SOL-ACCEPTED | 不受影响；H-02 是 PRD 图缺口而非实现缺陷 |
| P02-M2-02（DDL/Inbox/Outbox/迁移） | 锁定（待 DDL 评审） | **解锁前置**：处置 H-01、M-03（均在 TD-006 侧） |
| P02-M2-03（值班/站内信/升级） | 锁定 | 前置 M-02（suppression 事件裁决）+ L-01/L-03 |
| P02-M2-04（时间线/证据索引） | 锁定 | 前置 L-04/L-05 |
| P02-M3-01（遥控框架） | 锁定（Safety Hold） | 前置 M-01、L-06 |
| P02-M3-02（真实设备） | 锁定（外部签字） | 不变 |

---

## 八、评审处置要求

1. **必须处置（阻断 P02-M2-02）**：H-01、M-03 —— 修订 TD-006（§5.2、§4/§6 与候选 DDL 评审记录）。
2. **必须处置（阻断对应切片）**：M-01（TD-008 对齐 SPEC-007）、M-02（TD-006/007 事件裁决）、H-02（PRD-02 基线小版本修订）。
3. **随下版修订**：M-04、L-01～L-06。
4. 处置后本报告结论不变的部分（双基线合规、架构方向、迁移策略）无需重评；仅上述问题点需在下一轮 TD/SPEC 修订说明中引用本报告编号回链。

---

## 附：评审输入清单

- PRD-02-监控告警与安全控制.md（1.2.1，Approved/Baselined）
- SPEC-005/006/007/008（各 0.1.0，In Review）及 M2-M3-SPEC 评审记录（0.1.0）
- ADR-010-统一告警模型迁移.md（1.1.0，Accepted）
- TD-006/007/008/009（各 0.1.0）及 TD-006 三份附件
- V011/U011 候选 DDL（467+36 行，逐行审读）、alarm-source-event-v1 / alarm-domain-event-v1 Schema
- SPEC005-TD006 核心合同冻结记录（1.0.0）、P02-M2-01 任务单（1.0.0）与本地验收记录（1.0.0）
- 已实现代码：`AlarmStateMachine` / `AlarmErrorCode` / `AlarmEventEnvelope` 及两组测试（11 文件）
- 宪法 1.6.0 全文重点章节、平台功能计划 1.5.0 §2/§4/§8
