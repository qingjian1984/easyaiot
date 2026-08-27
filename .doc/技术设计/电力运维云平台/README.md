# EasyAIoT 电力运维云平台 Technical Design 索引

> 索引版本：1.14.1
> 日期：2026-08-27
> 上游基线：[PRD-01 1.2.0](../../产品需求/电力运维云平台/PRD-01-站点设备与数据采集.md)、[PRD-02 1.2.2](../../产品需求/电力运维云平台/PRD-02-监控告警与安全控制.md)、[M1 Spec 集合基线 1.4.0](../../规格/电力运维云平台/M1-SPEC评审冻结记录.md)、[PRD-02 M2-M3 Spec 专项评审](../../规格/电力运维云平台/M2-M3-SPEC评审记录.md)、[PRD-02 SDD 评审处置](../../开发规范/PRD-02-SDD方案设计评审处置记录.md)、[ADR 决策集](../../架构决策/电力运维云平台/README.md)
> 续作入口：[M1 SDD 进度与续作入口](./M1-SDD进度与续作入口.md)、[PRD-02 SDD 进度与续作入口](./PRD-02-SDD进度与续作入口.md)

## M1 Technical Design

| ID | 主题 | 状态 | 实现门禁 |
|---|---|---|---|
| [TD-001](./TD-001-collector与NODE部署契约.md) | collector Profile、NODE 部署契约与配置发布状态机（1.0.4） | In Review | 评审通过并冻结生产资源准入值 |
| [TD-002](./TD-002-SQLite-Outbox与恢复迁移.md) | SQLite outbox、容量、并发、恢复与迁移 | In Review | 评审并冻结队列状态机、容量保护和恢复流程 |
| [TD-003](./TD-003-遥测Inbox-ACK与时序投影.md) | 遥测 envelope、inbox、ACK、幂等与时序投影 | In Review | 评审并冻结 ACK、Inbox、Store 适配和数据质量契约 |
| [TD-004](./TD-004-电力对象别名二维码与历史编码兼容.md) | 对象、别名、二维码及历史编码兼容（1.0.1） | In Review | 评审意见已处置；待授权撤销、存量画像、alias/二维码安全、跨 TD 合同和迁移证据 |
| [TD-005](./TD-005-物模型模板Schema版本差异与发布API.md) | 物模型模板 Schema、版本差异、导入资产、产品绑定及发布 API（1.0.16；ADR-012 1.0.2 Accepted、12 表画像、legacy golden、内部八表持久化及 capability 合同已有证据） | In Review | 版本/绑定/审计/Outbox migration、公开接口、生产 TypeScript golden、恶意导入、行业模板、删除/性能和资产 commit/hash 门禁全部关闭 |
| [TD-005-RUNTIME-001](./TD-005-运行模型兼容与删除链技术设计.md) | 根属性/服务参数单一事实、DO/VO/Mapper、租户约束和产品删除链（0.1.9；内部八表持久化、TEN-001～008、稳定错误及版本层 migration 前置已对齐） | In Review | 版本层原子合同、公开接口、DDL/rollback、Feign 合同、DEL-001～010、性能和三档端到端回归全部通过 |
| [TD-005-MIG-001](./TD-005-版本绑定审计Outbox迁移与回滚设计.md) | 模板版本、产品绑定、领域审计与发布 Outbox 的原子事务、迁移及回滚（0.1.6；宪法专项评审已处置；ADR-013 1.3.0/ADR-014 1.0.0 Proposed、候选 runner Spike、V001/U001 DDL 骨架与事件/Inbox 候选已形成） | In Review / Migration Candidate | 关闭 ADR-013/014、完整画像、幂等表落库、product unique/binding FK、DDL/rollback、事件 Schema/transport/消费者 Inbox、故障注入、压测与备份恢复全部通过；批准前不得执行 DDL |
| [TD-005-DATA-001](./TD-005-孤儿属性处置方案.md) | 4 条过期演示种子孤儿属性的证据、决策、预检、修复与回滚（0.2.0） | Executed / Verified | 初始化基线与目标库均为0，修复后画像已验证 |

## PRD-02 M2-M3 Technical Design

| ID | 主题 | 状态 | 实现门禁 |
|---|---|---|---|
| [TD-006](./TD-006-统一告警与规则引擎.md) | 统一告警主记录、规则引擎、专用 Inbox/Outbox 与存量迁移（0.1.2；P02-M2-02 已完成条件冻结评审） | In Review / Conditional Freeze | runner/临时库、正式 API Schema/JDBC、来源画像、transport、容量、双写与切换责任人全部关闭 |
| [TD-006-A](./TD-006-现有告警实现画像.md) | 旧阈值告警、Feign、Kafka 与租户/周期迁移事实 | Verified from Repository | 生产数据、Topic 和调用量画像仍需批准窗口 |
| [TD-006-B](./TD-006-状态转换与并发矩阵.md) | 主状态、误报复核、周期和并发 CAS 规则（0.1.2） | Freeze Candidate | 与 DDL/JDBC 并发合同一并验证 |
| [TD-006-C](./TD-006-事件与能力合同.md) | 事件命名、Envelope、payload、capability/quota key（0.1.2） | In Review / Freeze Candidate | 六个设计 Schema 已拆分；正式 API 资源、CI、容量和物理 transport 待关闭 |
| [TD-007](./TD-007-通知与升级编排.md) | 值班、通知意图、渠道适配、送达结果与正交升级（0.1.2；依赖 TD-006 0.1.2） | In Review / Draft | 渠道/验签、旧链防双发、配额压测和安全评审关闭；外部渠道此前禁用 |
| [TD-008](./TD-008-安全遥控与操作票.md) | 风险目录、操作票、独立审批、联锁、命令回执和旁路阻断（0.2.0；状态机已对齐） | In Review / Safety Hold | 现场签字、DDL、旧入口画像、非幂等 Runbook、安全评审和故障注入关闭；capability 默认关闭 |
| [TD-009](./TD-009-事故证据与媒体归档.md) | 时间线、证据索引、遥测冻结、NFS→MinIO 和事故报告（0.2.0；幂等/异步合同已对齐） | In Review / Draft | 媒体画像、容量/恢复、留存/WORM 批准、压测和 PRD-03 体验合同关闭 |

## PRD-02 本地收口任务包

| ID | 主题 | 状态 | 实现门禁 |
|---|---|---|---|
| [P02-M2-01](./P02-M2-01-告警领域合同与状态机任务单.md) | 告警枚举、事件 Envelope 和纯状态机 | [Implemented / Verified-Local](./P02-M2-01-本地验收记录.md) | 33 个定向测试与 Reactor 编译通过；数据库、Kafka、API、旧链迁移和 capability 仍关闭 |
| [P02-M2-02 评审](./P02-M2-02-专项冻结评审记录.md) | 告警 DDL、Schema、Inbox/Outbox 与迁移边界 | Conditional Freeze / Local Tasks Only | 整体未冻结；只解锁 02A/02B 本地任务 |
| [P02-M2-02A](./P02-M2-02A-告警迁移资产与临时库合同任务单.md) | V011/U011 review-only runner 与临时库合同 | [Implemented / Static-Verified](./P02-M2-02A-本地静态验收记录.md) | 58 项假命令合同通过；临时库合同 NOT RUN，任何真实 DDL 须另行授权 |
| [P02-M2-02B](./P02-M2-02B-告警持久化与可靠事件任务单.md) | 正式 Schema、持久化、同事务编排和 Relay fake | Approved / Frozen for Local No-Transport Implementation | 禁止生产 transport、旧来源、API 与 capability |

## M1 本地收口任务包

| ID | 主题 | 状态 | 实现门禁 |
|---|---|---|---|
| [M1-LC-01](./M1-LC-01-Inbox接收结果合同任务单.md) | Inbox 新增/重复/碰撞逐消息结果合同 | Implemented / Verified-Local | LC01-01～14 与模块编译验证通过；未提前实现 ACK、审计、DDL、投影或 Store 变更 |
| [M1-LC-02A](./M1-LC-02A-Collector版本配置应用链任务单.md) | 内部/NODE HMAC、ConfigSnapshot 1.1、iot-node 派发、NODE 原子落盘与 collector 本地应用 | Implemented / Verified-Local | LC02A-0～4 全部经 Sol 接受；运行期资格仍独立 OPEN |
| [M1-LC-02](./M1-LC-02-遥测Topic与产品路由身份收口任务单.md) | canonical 遥测 Topic 与产品路由身份持久化 | Implemented / Verified-Local | LC02-10 及 R1～R6 全部 COMPLETE / SOL-ACCEPTED；正式迁移与生产激活仍 OPEN |
| [M1-LC-03](./M1-LC-03-成功ACK-V1与重启对账任务单.md) | 成功 ACK V1、collector 精确订阅、center 即时发送与重启补发 | In Progress（LC03-01 SOL-ACCEPTED） | 共享 ACK V1 直接合同 23/23；下一步须独立授权 Luna Max 执行 LC03-02 |

## 状态规则

- `Planned`：只有上游门禁，尚未形成可评审设计。
- `In Review`：设计已形成，但未通过实现前评审，不得按未冻结内容直接编码。
- `Safety Hold`：安全边界已形成，但现场/安全责任人输入未关闭；相关 capability 必须 fail-closed，不得接入真实受控设备。
- `Approved / Frozen`：接口、数据、状态机、安全、测试和回滚均通过评审，可拆分代码任务。
- TD 中标记为压测冻结的数值，只有附带测试环境、负载模型和原始结果才可转为生产基线。
