# TD-005 事件 Schema/fixture 候选

> 状态：Review Candidate（未冻结）
> 日期：2026-08-06
> 上游：TD-005 migration 0.1.2 §4.6

本目录提供 4 个首批领域事件的 Draft 2020-12 JSON Schema 与示例 fixture，用于 DBA/架构评审事件 payload 契约；尚未进入 `iot-device-api` 代码资源，也未冻结消费者/transport/Inbox。

## 文件

- `power-model-template-published-v1.json`：`POWER_MODEL_TEMPLATE_PUBLISHED_V1`
- `power-model-template-lifecycle-changed-v1.json`：`POWER_MODEL_TEMPLATE_LIFECYCLE_CHANGED_V1`
- `power-product-model-binding-applied-v1.json`：`POWER_PRODUCT_MODEL_BINDING_APPLIED_V1`
- `power-product-model-binding-rolled-back-v1.json`：`POWER_PRODUCT_MODEL_BINDING_ROLLED_BACK_V1`
- `events.fixtures.example.json`：每个事件的合法示例

## 契约约束

- 公共 envelope 固定包含 `eventId/eventType/schemaVersion/tenantId/aggregateType/aggregateId/occurredAt/requestId/traceId/data`。
- bigint ID 一律输出十进制字符串；时间使用 UTC ISO 8601。
- 同一主版本只允许 additive 兼容变化；破坏性变化必须创建 `_V2` 独立事件和 Schema。
- 消费者必须忽略未知可选字段；同 `eventId` 同 hash 返回 DUPLICATE，同 ID 异 hash 进入隔离并 critical。

## 冻结门禁

正式冻结前必须关闭：transport 选型（ADR）、消费者 Inbox、当前/上一主版本双版本合同、未知字段/未知主版本处理、事件 Schema 进入生产者 API 模块，以及 V1/V2 双发对账测试。

## 验证

当前仓库未安装 `ajv-formats`，使用 Ajv Draft 2020-12 `strict:false` 对 4 个合法 fixture 校验均 PASS（`format: uuid/date-time` 在 strict 模式会因缺少 formats 插件被拒；正则/枚举约束本身已覆盖）。最终 CI 应引入 `ajv-formats` 或等价 validator 后以 strict 模式复跑。
