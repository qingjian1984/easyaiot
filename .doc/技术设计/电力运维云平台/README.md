# EasyAIoT 电力运维云平台 Technical Design 索引

> 索引版本：1.10.0
> 日期：2026-08-13
> 上游基线：[PRD-01 1.2.0](../../产品需求/电力运维云平台/PRD-01-站点设备与数据采集.md)、[M1 Spec 集合基线 1.4.0](../../规格/电力运维云平台/M1-SPEC评审冻结记录.md)、[ADR 决策集](../../架构决策/电力运维云平台/README.md)
> 续作入口：[M1 SDD 进度与续作入口](./M1-SDD进度与续作入口.md)

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

## M1 本地收口任务包

| ID | 主题 | 状态 | 实现门禁 |
|---|---|---|---|
| [M1-LC-01](./M1-LC-01-Inbox接收结果合同任务单.md) | Inbox 新增/重复/碰撞逐消息结果合同 | Implemented / Verified-Local | LC01-01～14 与模块编译验证通过；未提前实现 ACK、审计、DDL、投影或 Store 变更 |
| [M1-LC-02A](./M1-LC-02A-Collector版本配置应用链任务单.md) | 内部/NODE HMAC、ConfigSnapshot 1.1、iot-node 派发、NODE 原子落盘与 collector 本地应用 | LC02A-0 Approved / Frozen | ADR-017/018 已接受；先交付 02A-0，验证后由 Sol 复核并逐包解锁 02A-1～4 |
| [M1-LC-02](./M1-LC-02-遥测Topic与产品路由身份收口任务单.md) | canonical 遥测 Topic 与产品路由身份持久化 | Review-Ready / Blocked by LC-02A | Topic 五项技术决策已由 Sol 收敛；ADR-017 Accepted 且 LC-02A Verified-Local 后才能冻结交付 Luna Max |

## 状态规则

- `Planned`：只有上游门禁，尚未形成可评审设计。
- `In Review`：设计已形成，但未通过实现前评审，不得按未冻结内容直接编码。
- `Approved / Frozen`：接口、数据、状态机、安全、测试和回滚均通过评审，可拆分代码任务。
- TD 中标记为压测冻结的数值，只有附带测试环境、负载模型和原始结果才可转为生产基线。
