# EasyAIoT 电力运维云平台 Technical Design 索引

> 索引版本：1.2.0  
> 日期：2026-07-31  
> 上游基线：[PRD-01 1.2.0](../../产品需求/电力运维云平台/PRD-01-站点设备与数据采集.md)、[M1 Spec 集合基线 1.4.0](../../规格/电力运维云平台/M1-SPEC评审冻结记录.md)、[ADR 决策集](../../架构决策/电力运维云平台/README.md)
> 续作入口：[M1 SDD 进度与续作入口](./M1-SDD进度与续作入口.md)

## M1 Technical Design

| ID | 主题 | 状态 | 实现门禁 |
|---|---|---|---|
| [TD-001](./TD-001-collector与NODE部署契约.md) | collector Profile、NODE 部署契约与配置发布状态机 | In Review | 评审通过并冻结生产资源准入值 |
| [TD-002](./TD-002-SQLite-Outbox与恢复迁移.md) | SQLite outbox、容量、并发、恢复与迁移 | In Review | 评审并冻结队列状态机、容量保护和恢复流程 |
| [TD-003](./TD-003-遥测Inbox-ACK与时序投影.md) | 遥测 envelope、inbox、ACK、幂等与时序投影 | In Review | 评审并冻结 ACK、Inbox、Store 适配和数据质量契约 |
| TD-004 | 对象、别名、二维码及历史编码兼容 | Planned | PRD-01/SPEC-001/ADR-004/008 |
| TD-005 | 物模型模板 Schema、版本差异及发布 API | Planned | SPEC-002/ADR-009 |

## 状态规则

- `Planned`：只有上游门禁，尚未形成可评审设计。
- `In Review`：设计已形成，但未通过实现前评审，不得按未冻结内容直接编码。
- `Approved / Frozen`：接口、数据、状态机、安全、测试和回滚均通过评审，可拆分代码任务。
- TD 中标记为压测冻结的数值，只有附带测试环境、负载模型和原始结果才可转为生产基线。
