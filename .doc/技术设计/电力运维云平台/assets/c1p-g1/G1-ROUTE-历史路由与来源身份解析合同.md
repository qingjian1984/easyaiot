# G1-ROUTE：历史路由与来源身份解析合同

> 版本：1.0.0
> 日期：2026-08-25
> 状态：DESIGN-CLOSED / Upstream Event Contract Open / No Code
> 双基线：[平台功能计划 1.5.0](../../../../架构设计/平台功能计划.md)、[EasyAIoT 项目开发宪法 1.6.0](../../../../开发规范/EasyAIoT项目开发宪法.md)
> 上游：[ADR-019 0.2.0 Proposed](../../../../架构决策/电力运维云平台/ADR-019-告警来源身份周期与补偿接入.md)、[ADR-017](../../../../架构决策/电力运维云平台/ADR-017-遥测可靠链路Topic与产品路由身份收口.md)

## 1. 结论

THRESHOLD 历史 route 必须使用受信任 ingress 已持久化的 `workloadId`，以 `(tenantId, workloadId, configVersion)` 精确取得“曾成功 APPLIED”的不可变 release，再交叉校验 site/product/device 和事件发生时间。仅以 `tenantId + siteCode + configVersion` 搜索不再允许。

DEVICE_EVENT 必须使用设备原始 request identity、payload/协议中的可信 occurredAt 和 canonical subject identity。直连事件可按历史 `power_device_assignment [valid_from,valid_to)` 解析；当前 SUB_EVENT Topic 只有网关产品/设备，未提供子设备 canonical identity，因此 C1 对子设备事件保持 fail-closed。

## 2. THRESHOLD resolver

### 2.1 输入合同

`TELEMETRY_PROJECTED_V1` 在进入阈值 evaluator 前必须包含并保留：

| 字段 | 权威来源 |
|---|---|
| `messageId/contentSha256` | M1 telemetry Inbox 的内部 canonical UUID 与原 payload hash |
| `tenantId` | 已通过 Topic/注册/Envelope 一致性校验的 Inbox |
| `workloadId` | MQTT principal/ingress route 持久化事实，不从 Envelope 猜测 |
| `productIdentification` | canonical Topic 与 Inbox 持久化路由元数据 |
| `siteCode/deviceIdentification/propertyCode` | canonical Envelope，且 ingress 已交叉验证 |
| `collectedAt/originalOffset/sequence/quality/configVersion` | canonical Envelope 原事实 |
| `storeResult` | 只允许 `STORED/DUPLICATE` |

当前 `TelemetryProjectionOrchestrator` 完成行只更新 Inbox 状态，没有实现 TD-003 `telemetry_projection_event_outbox`，其 SELECT 也未携带 workloadId/product/quality/originalOffset。该缺口必须由 M1 独立任务扩充；C1 不得从当前设备表或运行内存补字段。

### 2.2 解析算法

1. 校验 tenant>0、workload 非空、configVersion>0、canonical message/hash/time；
2. 以 `(tenant_id, workload_id, config_version)` 查询唯一 release；数据库已有该三元组 unique；
3. 要求 `applied_at` 非空、`applied_version=config_version`，且 status 为 `APPLIED` 或历史 `ROLLED_BACK`；PUBLISHED/FAILED/APPLY_TIMEOUT/NEVER_APPLIED 均不合格；
4. 要求 `collectedAt >= appliedAt`，并早于同 workload 下一次成功应用 release 的 appliedAt；等于下一 appliedAt 属新版本左闭边界，旧版本拒绝；
5. 重算/校验 payload canonical bytes、payloadSha256、schema/canonicalization；
6. payload 根级 tenantId/workloadId/siteId/siteCode/configVersion/productIdentification 与投影事件完全一致；
7. payload 设备集合中目标 device/property 恰好一次，且 product+device 权威注册返回同 tenant；
8. 输出 route evidence；任一步零/多匹配或冲突均 fail-closed。

输出固定为：

```text
tenantId, siteId, siteCode, workloadId,
productId, productIdentification, deviceIdentification,
releaseId, configVersion, payloadSha256,
appliedAt, nextAppliedAt?, routeEvidenceHash
```

`routeEvidenceHash` 对上述不可变字段按 JCS 计算 `sha256:`，供 source identity 复核；不取代原 release hash。

### 2.3 THRESHOLD 稳定错误

| 场景 | 错误码 | 分类 |
|---|---|---|
| 缺 workload/product/版本 | `ALARM_ROUTE_CONTEXT_MISSING` | final，等待新合同重驱 |
| release 零匹配 | `ALARM_ROUTE_RELEASE_NOT_FOUND` | retryable 仅限权威副本同步中；权威确认不存在后 final |
| release 多匹配/唯一约束异常 | `ALARM_ROUTE_RELEASE_AMBIGUOUS` | final+critical |
| 从未 APPLIED | `ALARM_ROUTE_RELEASE_NEVER_APPLIED` | final |
| collectedAt 不在应用窗口 | `ALARM_ROUTE_EVENT_OUTSIDE_APPLIED_WINDOW` | final |
| payload/hash/schema 冲突 | `ALARM_ROUTE_RELEASE_INTEGRITY_CONFLICT` | final+critical |
| site/product/device/property 冲突 | `ALARM_ROUTE_AUTHORITY_CONFLICT` | final+security audit |

## 3. DEVICE_EVENT resolver

### 3.1 原始身份

直连事件的来源身份字段固定为：

| 字段 | 合同 |
|---|---|
| `sourceRequestId` | 设备/协议原始 requestId，1～128 UTF-8 字节、非空、无控制字符；同设备在保留窗口内不得复用给不同正文 |
| `sourcePayloadHash` | 对协议 adapter 验证后的原始事件 canonical bytes 计算 `sha256:` |
| `occurredAt` | allowlist `occurredAtJsonPointer` 或协议签名元数据，RFC 3339、显式 offset、毫秒精度 |
| `product/device` | 直连 canonical Topic `/iot/{product}/{device}/event/upstream/report/{identifier}` |
| `eventIdentifier` | Topic 最后一段，必须与 allowlist matcher 完全一致 |
| `correlationKey` | allowlist JSON Pointer 指定的稳定值，raised/recovered 相同 |

后端 `IotDeviceMessage.id` 与 `reportTime` 明确由后端当前时间生成，分别不得作为 sourceRequestId/occurredAt。`message.requestId` 只有在具体协议 adapter 证明来自设备原文且重试不变后才合格；否则返回 `DEVICE_EVENT_ORIGINAL_ID_UNPROVEN`。

### 3.2 subject 与租户

1. 只接受经 `IotDeviceTopicEnum` 精确匹配的直连 canonical Topic；字符串 split 不能代替枚举解析；
2. product/device 通过全租户权威注册精确解析，必须恰好一行；
3. payload tenant 若存在只作一致性校验，不能扩大 Topic 权威；tenant=0/空值不可接受；
4. source request 幂等范围是 `tenant + product + device + sourceRequestId`；同范围同 hash 为 DUPLICATE，异 hash 为 `DEVICE_EVENT_SOURCE_ID_COLLISION`。

`SUB_EVENT_UPSTREAM_REPORT` Topic 表达的是网关 product/device，当前消息没有冻结的子产品、子设备、子 requestId 和子 occurredAt 字段。C1 返回 `DEVICE_EVENT_SUBJECT_UNRESOLVED`，只允许旧链按原行为保存；不得把网关身份当作子设备告警身份。后续若接入，必须先扩独立子设备事件 Schema 并重新评审。

### 3.3 历史 assignment

先由 product/device 权威得到 tenantId 与内部 deviceId，再执行事件时间查询：

```text
tenant_id = tenantId
device_id = deviceId
valid_from <= occurredAt
AND (valid_to IS NULL OR occurredAt < valid_to)
```

必须恰好一行，并以同 tenant join `power_site` 得到 siteCode。左闭右开规则固定：事件恰好等于旧 validTo 归新 assignment；恰好等于新 validFrom 也归新 assignment。禁止 current-only Mapper、缓存当前站点或接收时间查询。

V006 只用 partial unique 保证“当前一行”，没有数据库 exclusion constraint 阻止历史交叠；TD-004 依赖单事务服务规则避免交叠。因此 resolver 必须读取最多两行并把多匹配视为 `DEVICE_EVENT_ASSIGNMENT_AMBIGUOUS`，不能 `LIMIT 1`。

### 3.4 DEVICE_EVENT 稳定错误

| 场景 | 错误码 | 分类 |
|---|---|---|
| 原始 requestId 缺失/未证明 | `DEVICE_EVENT_ORIGINAL_ID_UNPROVEN` | final |
| 原始时间缺失、无 offset、超精度 | `DEVICE_EVENT_TIME_INVALID` | final |
| Topic/权威 tenant-product-device 冲突 | `DEVICE_EVENT_AUTHORITY_CONFLICT` | final+security audit |
| 子设备 subject 不完整 | `DEVICE_EVENT_SUBJECT_UNRESOLVED` | final |
| 历史 assignment 零匹配 | `DEVICE_EVENT_ASSIGNMENT_NOT_FOUND` | final；不查当前补齐 |
| 历史 assignment 多匹配 | `DEVICE_EVENT_ASSIGNMENT_AMBIGUOUS` | final+critical |
| requestId 同 ID 异 hash | `DEVICE_EVENT_SOURCE_ID_COLLISION` | terminal quarantine |

## 4. 时间、重放与边界

- 所有比较先按原 offset 解析为 UTC epochMillis，同时保留 originalOffset；
- 未来时钟容差、最大历史接收跨度由协议/产品合同配置，未冻结前只做格式与 assignment/release 窗口校验，不声明生产阈值；
- recordedAt/receivedAt 只作审计与延迟指标，不参与 route、sourceId、cycleKey 或 eventId；
- 权威副本暂未同步可 retry；权威确认不存在、歧义或完整性冲突均 final；
- 人工修复只能生成新的重驱 messageId，并关联原隔离记录；不得覆盖原 hash 或重置原 Inbox。

## 5. 负向测试矩阵

| 类别 | 必测反例 |
|---|---|
| THRESHOLD key | workload 缺失、错 workload、相同 site/config 的两个 workload、config=0 |
| 应用历史 | PUBLISHED 未应用、ROLLED_BACK 历史 backlog、事件早于 appliedAt、等于 nextAppliedAt |
| 快照 | hash 不同、payload root 不同、设备零/重复成员、product/tenant/site 冲突 |
| DEVICE direct | requestId 缺失/重复异 hash、reportTime fallback、Topic 多/少段、未知 product/device |
| assignment | 迁站前后边界、零匹配、人工历史交叠两匹配、跨租户同 device code |
| sub event | 只有网关 identity、伪造子设备字段、子 requestId 缺失，全部 fail-closed |
| replay | 同 identity/hash 重放、隔离后普通重试、当前 assignment 与历史 assignment 不同 |

## 6. 当前开放项

- TD-003 projection event outbox 尚未落地，必须增加受信任 workloadId/product/quality/originalOffset；
- DEVICE_EVENT 各协议尚未逐一证明 requestId 与 occurredAt 来自设备原文且重试稳定；
- 子设备事件 subject contract 缺失；
- resolver 端口、查询和测试尚无实现授权。

这些项阻断 C1 adapter；本文件只关闭解析算法与 fail-closed 语义的设计缺口。
