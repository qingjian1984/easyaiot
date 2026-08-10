# 首次 VALIDATED 候选创建事务验证记录

> 日期：2026-08-10
> 双基线：平台功能计划 1.4.0 / EasyAIoT 项目开发宪法 1.5.0
> 目标：本地集成实例 `postgres-server / iot-device20`
> 边界：仅执行事务性 fixture 合同；全部回滚，不执行 DDL，不启用第四端口

## 实现结论

- 新增 `POST /api/v1/products/{productIdentification}/model-binding:apply`，使用字面量冒号 action suffix；
- Controller 启用 `power:model-template:publish` 权限，tenant 与 actor 只从安全上下文取得；
- Service fail-closed 校验 capability、租户有效性、已发布模板和 tenant-safe 产品事实；
- 产品行锁分配 `bindingRevision`，workload advisory lock 分配 `configVersion`；
- 同事务按 binding → domain audit → Outbox → `VALIDATED` candidate 写入，事务内不调用 NODE；
- binding/collector 快照使用 JCS 单次生成 canonical 与 SHA-256，候选关联同一 Outbox `sourceEventId`；
- `Idempotency-Key` 仅保存 HMAC-SHA-256，同 key 同请求重放原结果，异请求返回 `IDEMPOTENCY_KEY_REUSED`；
- `EASYAIOT_POWER_MODEL_IDEMPOTENCY_HMAC_SECRET` 缺失或少于 32 UTF-8 字节时拒绝写入。

## 验证结果

1. Java 17 reactor compile：PASS；
2. 真实 PostgreSQL 正常路径：binding/audit/outbox/release/idempotency 同事务各 1 行，状态 `VALIDATED`，版本均从 1 分配：PASS；
3. collector canonical raw SHA-256 与 UTF-8 byte length 复算：PASS；
4. Outbox V1 Envelope 事件类型、bindingRevision、appliedBy：PASS；
5. 同 `Idempotency-Key` 同请求重放且不新增事实：PASS；
6. 同 key 异请求稳定拒绝且不新增事实：PASS；
7. 最终 candidate INSERT 由事务内临时 trigger 强制失败，binding/audit/outbox/idempotency 与 fixture 全部回滚：PASS；
8. 定向测试：`PowerModelBindingApplyPostgresIntegrationTest` **2/2 PASS**；
9. 测试后专用租户 `920008001` 在 idempotency/product/template/template_version/binding/audit/outbox/release 中均为 0 行。

## 保留门禁

- `easyaiot.power-model.collector-release-port-enabled` 仍未设置为 true，第四端口不装配；
- 下一步必须补候选→Outbox 消费→发布单/投影 CAS 的真实 PostgreSQL 端到端合同；
- secret 只允许由部署环境或配置中心注入，本文与仓库不保存真实值。
