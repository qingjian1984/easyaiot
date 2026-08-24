# PRD-03 可视化 SCADA 与视频联动需求包

> 状态快照：2026-08-24（仅供导航，不是事实源）
> 当前双基线：[平台功能计划 1.5.0](../../../架构设计/平台功能计划.md)、[EasyAIoT 项目开发宪法 1.6.0](../../../开发规范/EasyAIoT项目开发宪法.md)

本目录仅是导航/追踪层；现有 `.doc/产品需求`、`.doc/规格`、`.doc/架构决策`、`.doc/技术设计`、`.doc/开发规范` 中的原文是唯一事实源。禁止复制正文；状态和版本以原文档头部为准。

## 产品需求与评审

- [PRD-03 可视化 SCADA 与视频联动](../../../产品需求/电力运维云平台/PRD-03-可视化SCADA与视频联动.md) — In Review
- [PRD 总评审报告](../../../开发规范/PRD评审报告.md)

## Feature Spec

- SPEC-009 电力一次图、站点聚合与点位绑定 — `Planned / 尚未创建`
- SPEC-008 事故追忆与视频证据 — `Related / Planned / 尚未创建`（由 PRD-02 需求包主责）

## 架构决策

- [ADR-010 统一告警模型与存量迁移](../../../架构决策/电力运维云平台/ADR-010-统一告警模型迁移.md)
- [ADR-011 Capability Manifest 规范](../../../架构决策/电力运维云平台/ADR-011-Capability-Manifest规范.md)
- [ADR-016 EDGE 退役与 RUNTIME 边缘执行边界](../../../架构决策/电力运维云平台/ADR-016-EDGE退役与RUNTIME边缘执行边界.md)

## Technical Design、任务与验收

- Technical Design：`Planned / 尚未形成`
- 实现任务：`Planned / 尚未形成`
- 测试与验收证据：`Planned / 尚未形成`

## 当前门禁

- PRD-03 仍为 In Review，尚未进入 Approved → Specified 流程。
- SPEC-009 和关联 SPEC-008 形成前，不得冻结一次图拓扑、聚合 API、视频联动或档位差异。
- standard/full 共享数据与权限契约，full 增量能力必须由 capability manifest 表达；具体验收门禁以未来 Spec/TD 为准。

## 维护规则

- 只维护产品、决策和下游设计入口，不复制 SCADA、视频或告警正文。
- SPEC-008 的主责关系保持在 PRD-02 需求包；跨包引用不得制造第二事实源。
- 原文状态、版本和验收结论变化后更新快照，并保留未关闭门禁。
