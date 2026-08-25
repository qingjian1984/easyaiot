# G1-EVENT：产品 Allowlist 输入说明

> 版本：1.0.0
> 日期：2026-08-25
> 状态：CONTRACT-CLOSED / Production Mappings Waiting External
> 双基线：[平台功能计划 1.5.0](../../../../架构设计/平台功能计划.md)、[EasyAIoT 项目开发宪法 1.6.0](../../../../开发规范/EasyAIoT项目开发宪法.md)
> Schema：[device-event-alarm-allowlist-v1.schema.json](./device-event-alarm-allowlist-v1.schema.json)
> 填报起点：[device-event-alarm-allowlist-template.json](./device-event-alarm-allowlist-template.json)

## 1. 当前事实

模板固定为 `DRAFT`、`mappings=[]`、`approval=null`，表示当前没有任何 DEVICE_EVENT 获准转为统一告警。本文只说明产品负责人如何提供输入，不提供虚构 eventIdentifier、severity、correlation 或恢复关系。

未取得通过 Schema、语义校验和责任人批准的 `APPROVED` 文档前，运行策略固定 `DENY_ALL`。

## 2. 填报流程

1. 从空白模板复制受控工作副本，保持 schema/canonicalization version 不变；
2. 产品负责人联合设备协议 owner 逐条填写 mapping；
3. 运行 Draft 2020-12 Schema 校验和 §4 语义校验；
4. 评审 raised/recovered 是否能用同一 correlation 精确闭合，不能闭合则删除条目；
5. 将 `documentStatus` 改为 `APPROVED`，填写 approvedBy/approvedAt/decisionRef；
6. 按 §3 计算 contentSha256，写入 approval；
7. Sol 复核 hash、重叠、版本升级和 route/identity 前置后，才可在 ADR-019 评审中引用。

Schema 通过不是产品批准；`decisionRef` 和批准人缺失、审批发生在 hash 之前、正文在批准后修改，都视为未批准。

## 3. Approval hash

为避免自引用，`approval.contentSha256` 对以下 JCS 对象计算：

```text
schemaVersion
canonicalizationVersion
documentStatus
revision
mappings
approvalEvidence = { approvedBy, approvedAt, decisionRef }
```

固定算法：RFC 8785 JCS UTF-8 bytes → SHA-256 → `sha256:` + 64 位小写 hex。`contentSha256` 字段自身不进入 hash。修改 mappings、revision、状态或 approvalEvidence 后必须重新评审和计算；不得只更新 hash 延续旧批准。

## 4. Schema 之外的语义校验

| 校验 | 通过条件 | 失败码 |
|---|---|---|
| mapping identity | `(mappingId,mappingVersion)` 全文唯一 | `DEVICE_EVENT_MAPPING_ID_DUPLICATE` |
| scope overlap | 同 scope、同有效期内 matcher 最多命中一条 mapping | `DEVICE_EVENT_MAPPING_AMBIGUOUS` |
| lifecycle | 当前只能 PAIRED；raised/recovered 均存在 | `DEVICE_EVENT_LIFECYCLE_UNSUPPORTED` |
| correlation | 两个 matcher 对真实 fixture 提取相同非空稳定值 | `DEVICE_EVENT_CORRELATION_MISSING` |
| occurredAt | 两个 matcher 都能取得 RFC3339+offset 毫秒时间 | `DEVICE_EVENT_TIME_INVALID` |
| severity | 由产品固定，不能从 payload 自由覆盖 | `DEVICE_EVENT_MAPPING_INVALID` |
| one-shot | 当前只能 REJECT | `DEVICE_EVENT_ONE_SHOT_UNSUPPORTED` |
| upgrade | 已活动周期固定原 mappingVersion，旧版本清零前保留 | `DEVICE_EVENT_MAPPING_VERSION_IN_USE` |
| protocol identity | requestId 来自设备原文、重试稳定、同 ID 异正文可检测 | `DEVICE_EVENT_ORIGINAL_ID_UNPROVEN` |
| subject | 只批准 direct event；当前 sub event 不得填入 direct scope | `DEVICE_EVENT_SUBJECT_UNRESOLVED` |

每条 mapping 必须附至少四个不进入生产文档的评审 fixture：RAISED、RECOVERED、未命中、correlation 不一致；涉及 payload predicate 时再增加类型不匹配和字段缺失。

## 5. 版本升级

- 修改 matcher、severity、correlation/time pointer、scope 或 alarmTypeKey 都必须增加 mappingVersion；
- 新 raised 使用当前批准版本；recovered 先按 correlation 查活动周期并使用其原 mappingVersion；
- 旧版本在其 ACTIVE 周期数量为 0 且对账完成前不得 RETIRED/删除；
- mappingVersion 进入 sourceId/eventId，升级不改写历史；
- 破坏根 Schema 时创建 v2 文件和独立评审，不原地放宽 v1。

## 6. 产品负责人必须回答

每个候选事件都必须有明确答案：

1. 该事件为什么是需要处置闭环的告警，而不是普通业务事件？
2. 唯一 RAISED 条件是什么？
3. 唯一 RECOVERED 条件是什么？
4. 哪个字段在重试、恢复和跨进程后仍能关联同一故障周期？
5. 严重度为何固定为该级别，是否涉及紧急告警不可忽略规则？
6. 设备/协议如何保证 requestId 与 occurredAt 的真实性和稳定性？
7. mapping 升级时已有活动周期如何关闭？

任一问题无答案，该 mapping 不得进入 APPROVED 文档。

## 7. 当前状态

机器合同和输入流程已关闭；生产 mappings、审批 hash、decisionRef 仍为 0。G1-EVENT 保持 `WAITING-EXTERNAL`，并阻断 ADR-019 Accepted 与 DEVICE_EVENT adapter。
