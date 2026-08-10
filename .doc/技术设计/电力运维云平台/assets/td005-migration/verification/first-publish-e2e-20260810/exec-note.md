# ADR-015 人工首发与 Outbox 消费端到端证据（2026-08-10）

- 双基线：平台功能计划 1.4.0 / EasyAIoT 项目开发宪法 1.5.0。
- 目标：本地目标集成实例 `postgres-server / iot-device20`。
- 变更：无 DDL、无 NODE 调用；测试 fixture 全部事务回滚。
- 写序：绑定、领域审计、Outbox、VALIDATED 候选、PUBLISHED 状态和 workload 投影同事务。
- 首发：`status=PUBLISHED`，投影 `ACTIVE / projection_revision=1 / config_version=1`。
- 不同事件：同 workload 单调推进到 `projection_revision=2 / config_version=2`。
- 重投：Inbox 首次 `PROCESSED`，重复事件直接 `duplicate`，无重复协调副作用。
- 原子失败：临时触发器强制拒绝投影 UPDATE，保存点回滚后绑定、审计、Outbox、发布单均保持事件前数量，投影仍为 revision=2。
- 门禁：`binding-apply-api-enabled` 与 `collector-release-port-enabled` 均默认关闭。
- Java 17 reactor compile：PASS。
- 测试：`PowerModelBindingApplyPostgresIntegrationTest` 3/3 + `PowerModelBindingControllerGateTest` 1/1，合计 4/4 PASS。
- 组合回归：上述 4 项 + `JdbcCollectorConfigReleasePortPostgresIntegrationTest` 2 项 +
  `PowerModelCollectorEventHandlersTest` 13 项，合计 19/19 PASS。
- 最终残留：测试租户 binding/audit/outbox/release/projection/inbox/coordination-audit/idempotency = `0/0/0/0/0/0/0/0`。
- 原业务计数：product/device/product_properties = `4/4/17`。
