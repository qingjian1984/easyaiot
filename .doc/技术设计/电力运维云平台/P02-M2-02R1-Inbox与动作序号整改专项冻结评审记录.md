# P02-M2-02R1：Inbox 与动作序号整改专项冻结评审记录

> 版本：1.1.0
> 日期：2026-08-25
> 状态：Conditional Freeze / R1A Verified-Local / External Gates Open
> 评审模型：GPT-5.6 Sol
> 双基线：[平台功能计划 1.5.0](../../架构设计/平台功能计划.md)、[EasyAIoT 项目开发宪法 1.6.0](../../开发规范/EasyAIoT项目开发宪法.md)
> 上游：[新功能方案评审处置记录](../../开发规范/PRD-02-P02M2-02新功能方案评审处置记录.md)、[P02-M2-02 专项冻结评审](./P02-M2-02-专项冻结评审记录.md)、[TD-006 0.1.3](./TD-006-统一告警与规则引擎.md)

## 1. 评审结论

N-01、N-03 的文档缺口已关闭：告警 Inbox 有意保持 `RECEIVED/PROCESSED/QUARANTINED` 三态，数据库事务失败全部回滚且不得 ACK；有界重试、退避、毒消息 DLQ 和 ACK 属于未来物理 transport adapter；`QUARANTINED` 是同一 `messageId` 不可覆盖的终态，人工重驱必须使用新 `messageId` 并审计关联原记录。

N-02 的架构方案与本地代码缺口已经关闭。直接 `MAX(sequence_no)+1` 被拒绝，因为当前 `JdbcAlarmSourcePersistence.FIND_ALARM` 没有 `FOR UPDATE`，该算法存在并发竞争；现有 `rowVersion + 2` 同样被拒绝，因为状态版本与动作数量不是同一事实。[P02-M2-02R1A 本地验收](./P02-M2-02R1A-本地验收记录.md)已证明候选 DDL、端口、事务、fake 并发和回滚合同一致，但没有验证真实 PostgreSQL 行锁或执行迁移。

本评审不解锁 C1，也不授权临时 PostgreSQL、来源 adapter、物理 transport、backfill、双写、对账、切读、API 或 capability。

## 2. Inbox 裁决

| 议题 | 冻结结论 |
|---|---|
| 数据库状态 | 只允许 `RECEIVED/PROCESSED/QUARANTINED`；不增加 `RETRY_WAIT`、lease、attempt 或 next-retry 字段 |
| 可重试失败 | 事务整体回滚，不 ACK；由 transport 按 C2 后续冻结的有界预算重投 |
| final/未知主版本 | 持久化 `QUARANTINED`；物理 DLQ 成功及 ACK 语义由 C2 adapter 合同冻结 |
| hash 冲突 | 保留首次 `envelopeHash`，原 Inbox 进入 `QUARANTINED`，冲突正文不得覆盖原事实 |
| 终态 | 同一 `messageId` 不得从 `QUARANTINED` 回退或覆盖为其他状态 |
| 人工重驱 | 必须授权、审计、使用新 `messageId` 并关联原 Inbox；禁止重置原行 |
| ACK 边界 | 业务事实与 Inbox 裁决提交前不得 ACK/提交 offset |

三态 Inbox 是业务幂等与终态隔离账本，不是消息调度器。把 transport 的等待/租约状态复制进 Inbox 会形成双重重试状态机，增加 ACK、DLQ 与数据库事实不一致风险，因此本轮不采用。

## 3. 动作序号方案裁决

| 方案 | 裁决 | 理由 |
|---|---|---|
| `rowVersion + 2` | 拒绝 | 状态 CAS 次数不等于动作数量；无状态动作、失败 CAS 和未来调度动作会破坏语义 |
| `SELECT MAX(sequence_no)+1` | 拒绝 | 当前读取无 `FOR UPDATE`，并发事务可读到同一最大值；即使补锁也会把分配规则分散到 action 表查询 |
| 全局 sequence/Snowflake | 拒绝 | 只能保证全局唯一/递增，不能保证每告警从 1 开始的连续已提交序号 |
| `alarm_record.last_action_sequence` 原子递增 | 采用 | 单行 `UPDATE ... RETURNING` 对同告警串行、不同告警隔离，可与动作追加同事务回滚 |

冻结顺序为：适用的状态 CAS 成功后，调用独立分配器，再追加动作；首次创建则先插入主记录再分配。分配器条件固定包含 `tenant_id/site_id/id`，不包含 `row_version`。分配和动作追加必须同事务，失败回滚不产生已提交空洞；幂等请求唯一约束继续独立存在。

## 4. 风险与反例

- 若先分配后做状态 CAS，CAS 失败会产生不必要竞争，且错误实现可能留下空洞；因此固定 CAS 在前、分配在后。
- 若捕获 action 唯一冲突后仍提交事务，计数器会被错误消耗；重复请求必须回滚或在分配前完成幂等裁决。
- 若不同动作生成器各自计算序号，未来人工/调度路径仍会重复；所有动作生成器必须复用同一持久化端口。
- fake 并发测试不能替代 PostgreSQL 行锁证据；真实数据库并发继续受 P02-M2-02A 临时库授权门禁控制。

## 5. 门禁状态

| 门禁 | 状态 | 证据/下一步 |
|---|---|---|
| N-01 Inbox 三态与重试责任 | `CLOSED-DOCUMENTATION` | TD-006 0.1.3 §5.1、本记录 §2 |
| N-03 TD 同步与修订记录 | `CLOSED-DOCUMENTATION` | TD-006 0.1.3 §13 |
| N-02 架构方案 | `FROZEN` | TD-006 0.1.4 §5.3、本记录 §3 |
| N-02 DDL/Java/测试实现 | `CLOSED-LOCAL` | [R1A 本地验收](./P02-M2-02R1A-本地验收记录.md)：60 项静态、20 项定向、74 项回归 |
| N-02 真实 PostgreSQL 并发 | `OPEN / SEPARATE APPROVAL` | 隔离临时库获准后执行，不得连接目标/共享库 |
| C1 来源 adapter | `CLOSED` | C0 之外的 identity/route/authority/cycle/原子性门禁仍 OPEN |
| C2 transport 与重试参数 | `CLOSED` | 物理 Topic、consumer group、预算、DLQ、ACK 专项评审 |
| C3/C4 迁移切换 | `CLOSED` | 生产画像、backfill、对账、观察窗口和责任人仍 OPEN |

## 6. R1/R1A 完成定义

- TD-006 升至 0.1.3，明确三态、终态、重驱、事务回滚、ACK 和 transport 责任；
- 独立动作序号的字段、SQL、事务顺序、并发与回滚反例完整冻结；
- R1A 在冻结边界内由 Luna max 完成，Sol 独立复核 60 项静态、20 项定向、74 项回归及 Reactor 构建；
- 所有文档相对链接、版本引用和门禁状态一致；
- R1 文档轮次没有运行行为变化；R1A 只修改候选 DDL 与本地 Java/测试资产，没有数据库连接、DDL 执行、外部系统或运行装配变化。

R1/R1A 完成关闭 N-01/N-03 文档决策与 N-02 本地代码缺口，但不改变 TD-006 整体 `In Review` 状态，也不解锁真实数据库与后续迁移切片。
