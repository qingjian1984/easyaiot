# P02-M2-02C1P-G2-03：设备事件协议证据与 Owner 确认单

> 版本：0.2.0
> 日期：2026-08-26
> 状态：LOCAL VERIFIER PREPARED / ALL CURRENT PROTOCOLS DISABLED / WAITING PROTOCOL OWNER / C1 CLOSED
> 设计责任：GPT-5.6 Sol
> 双基线：[平台功能计划 1.5.0](../../架构设计/平台功能计划.md)、[EasyAIoT 项目开发宪法 1.6.0](../../开发规范/EasyAIoT项目开发宪法.md)
> 上游：[C1P-G2 输入单 0.4.0](./P02-M2-02C1P-G2-责任人输入收集与建议处置单.md)、[ADR-019 0.2.0 Proposed](../../架构决策/电力运维云平台/ADR-019-告警来源身份周期与补偿接入.md)
> 机器合同：[协议证据 v1 Schema](./assets/c1p-g2/device-event-protocol-evidence-v1.schema.json)、[fixture v1 Schema](./assets/c1p-g2/device-event-protocol-fixture-v1.schema.json)、[DRAFT 模板](./assets/c1p-g2/device-event-protocol-evidence-template.json)
> 本地验收：在 `WEB/` 目录执行 `pnpm verify:device-event-protocol-evidence -- --file .doc/技术设计/电力运维云平台/assets/c1p-g2/OWNER_EVIDENCE.json`

## 1. 结论与授权边界

当前仓库没有一个 direct DEVICE_EVENT 协议同时证明设备原始 `requestId`、设备原始 `occurredAt`、重试稳定正文哈希、同 ID 异正文隔离，以及源事件事务提交后才 ACK。现有协议全部保持 `DISABLED`，SUB_EVENT 保持 `UNSUPPORTED`，属性遥测采集路径保持 `OUT_OF_SCOPE`；G2-03 和 C1 不关闭。

本确认单冻结证据格式和 fail-closed 条件，不是设备协议 owner 的签署，不授权修改 codec、transport、脚本、消息总线、DDL、数据库、broker 或 C1 adapter。只有 owner 从 DRAFT 模板复制出独立证据文件、填入正反例哈希并签署，才可逐协议评审；不得覆盖空白模板。

## 2. 仓库事实矩阵

| 路径 | 身份与时间事实 | ACK/失败事实 | 当前决策与原因码 |
|---|---|---|---|
| 通用 Topic JSON codec | 原始字节直接反序列化为后端 `IotDeviceMessage`，无法证明 `requestId/reportTime/tenantId` 的设备来源；产品 JS 还可在 codec 前改写正文 | 进入异步消息总线后，订阅器捕获并记录异常 | `DISABLED / GENERIC_ENVELOPE_PROVENANCE_UNPROVEN` |
| MQTT + Alink | `AlinkMessage.id` 传入 requestId，但无 retry/collision fixture；协议无原始时间字段 | EMQX 上行处理捕获异常，未形成“源事件事务提交后 PUBACK”合同 | `DISABLED / OCCURRED_AT_MISSING / COMMIT_ACK_UNPROVEN` |
| TCP JSON | 输入 `id` 传入 requestId；输入 `timestamp` 在 decode 中未保留，后续使用 backend reportTime | 认证后使用 `/tcp/{product}/{device}/unknown`，业务处理异常被捕获，响应不绑定源事件事务 | `DISABLED / OCCURRED_AT_DROPPED / DIRECT_EVENT_ROUTE_UNVERSIONED` |
| TCP Binary | 帧 messageId 可传入 requestId，但缺失时的下行编码可生成后端 ID；帧没有原始时间合同 | codec 检测默认 JSON，认证后同样走 `unknown` Topic，ACK 未绑定事务 | `DISABLED / OCCURRED_AT_MISSING / COMMIT_ACK_UNPROVEN` |
| HTTP + 版本化 codec | HTTP 仅构造 Topic 并委托 codec；transport 本身不是原始身份/时间 owner | `sendDeviceMessage` 返回后立即回 `messageId`，此时下游源事件事务尚未证明提交 | `DISABLED / TRANSPORT_NOT_IDENTITY_OWNER / PRECOMMIT_ACK` |
| 产品 JS `rawDataToProtocol` | 脚本可生成 requestId、设置 reportTime 或改写整个正文；当前未保留脚本版本、输入/输出哈希和来源证明 | 脚本返回空时回退原始 payload，不能形成单一版本合同 | `DISABLED / SCRIPT_TRANSFORM_PROVENANCE_UNPROVEN` |
| SUB_EVENT Topic | Topic 只有网关 product/device，未冻结子设备 subject、子 requestId 和子 occurredAt | 不适用 | `UNSUPPORTED / SUB_DEVICE_SUBJECT_UNFROZEN` |
| Modbus、OPC UA、polling | 当前是属性遥测采集，不是 direct DEVICE_EVENT | 不适用 | `OUT_OF_SCOPE / TELEMETRY_ONLY` |

代码位置仅用于证明当前仓库事实，不代表冻结接口：`IotTopicDeviceMessageCodec`、`IotDeviceMessageServiceImpl.decodeDeviceMessageByTopic`、`JsUtilFunction`、`IotEmqxUpstreamHandler`、`IotTcpUpstreamHandler`、`IotHttpUpstreamHandler`、`IotDeviceTopicSubscriber` 和 `IotUpstreamMessageSubscriber`。

## 3. v1 机器合同

### 3.1 文档与批准

- `DRAFT` 必须保持 `approval=null`，允许 `protocols=[]`，只能作为收集模板；
- `APPROVED/RETIRED` 必须至少列出一个协议，并填写 `ownerRole/approvedBy/approvedAt/decisionRef/contentSha256`；
- `contentSha256` 使用 RFC 8785 JCS 规范化，计算时把 `approval.contentSha256` 字段从文档中排除；
- 文档获批不等于 G2-03 关闭：至少一个条目必须为 `ENABLED`，其余协议继续按各自 decision 关闭；
- `protocolId` 在仓库内稳定唯一；协议字段或语义变化必须增加 `protocolVersion` 和 evidence revision，不得原地覆盖已批准证据。

### 3.2 ENABLED 条目的强制条件

Schema 对 `ENABLED` 条目强制以下条件：

1. direct event Topic 非空且 `subjectMode=DIRECT`；
2. requestId 来自设备原文字段，重试稳定，唯一范围为 tenant+product+device，有明确窗口、UTF-8 长度和控制字符拒绝规则；
3. occurredAt 来自设备原文字段，格式为 RFC 3339 显式 offset 毫秒或 epoch millis，保留原始 offset/毫秒精度，并冻结时钟源和未来/历史容差；
4. 原始正文哈希被保留，规范化策略被版本化，产品脚本改写必须关闭；
5. transport/application ACK 只能在 source Inbox/cycle/outbox 同事务提交后产生，持久化失败必须反馈到 transport；
6. original、retry、collision、missingRequestId、invalidOccurredAt 五类 fixture 均有仓库路径和 SHA-256；
7. `reasonCodes=[]`。任一条件不满足只能填 `DISABLED/UNSUPPORTED/OUT_OF_SCOPE` 并给出原因码。

Schema 负责结构与关键布尔门禁；本地评审器必须校验：`minUtf8Bytes <= maxUtf8Bytes`、protocolId 唯一、fixture/raw capture 路径存在且哈希匹配、original/retry 的 requestId/time/canonical hash 相同、collision 的 requestId 相同而 canonical hash 不同、失败样例确实 fail-closed，以及批准哈希正确。

### 3.3 fixture v1 文件合同

五类 fixture 均为 JSON 文件，并通过 fixture v1 Schema 校验。每份文件必须包含：

- `fixtureType/protocolId/protocolVersion/captureId`；同一协议的五个 captureId 不得重复；
- `rawArtifact.path/sha256`，指向仓库内脱敏原始报文字节或文本，禁止绝对路径、反斜线和 `..`；
- `wireIdentity.requestId/occurredAt/canonicalPayloadSha256`，记录从原文得到的身份、时间和规范正文哈希；
- `processing`，明确接受、重复、碰撞隔离、源事务提交、既有事务核验、失败传播、后端补 ID 和接收时间 fallback；
- `acknowledgement.mode/afterDurableDecision`，证明 ACK/NACK 在持久化决定之后产生。

本合同的固定语义为：ORIGINAL 必须源事务提交后 ACK；RETRY 必须核验既有事务后 ACK；COLLISION 必须隔离并向 transport 返回失败；MISSING_REQUEST_ID 禁止后端补 ID；INVALID_OCCURRED_AT 禁止接收时间 fallback。所有失败样例均不得提交 source transaction。

## 4. Owner 提交包

设备协议 owner 每次可只提交一个拟启用协议，但必须同时提供：

| 交付物 | 必填内容 |
|---|---|
| 协议规范 | protocolId/version、wire 字段、字符/长度、唯一窗口、时间格式/offset/精度/容差、direct Topic |
| codec/transport 合同 | 禁止后端补 requestId/occurredAt；禁止脚本改写；失败传播与 commit 后 ACK 时序 |
| 正例 | 原始上报和完全相同的重试，提交两份 fixture JSON 及脱敏 raw capture，证明 requestId、occurredAt、canonical hash 稳定 |
| 碰撞负例 | 同 requestId 异正文，证明进入 collision/quarantine 且不写 source cycle/outbox |
| 缺失负例 | requestId 缺失的 fixture/raw capture，证明拒绝且不生成 backend ID |
| 时间负例 | 缺 offset、超精度、未来/历史越界或格式非法的 fixture/raw capture，证明拒绝且不使用接收时间 |
| 批准 | ownerRole、approvedBy、approvedAt、decisionRef 和排除 contentSha256 字段后计算的 JCS SHA-256 |

## 5. Owner 确认区（待填写）

| 字段 | 待填内容 |
|---|---|
| evidenceDocument | 从 DRAFT 模板复制后的独立仓库路径 |
| enabledProtocolIds | 至少一个，或明确 `NONE` 并接受 DEVICE_EVENT 继续 DENY_ALL |
| disabled/unsupported/outOfScope | 每个已盘点协议的稳定 decision 与 reasonCodes |
| approvedBy / approvedAt | 设备协议 owner 身份和 RFC 3339 显式 offset 时间 |
| decisionRef | 可审计提交/评审记录，不接受聊天中的泛化“继续执行” |
| contentSha256 | `sha256:<64 lowercase hex>` |

签署 decision 建议仅允许：`APPROVE_EVIDENCE`、`APPROVE_NONE_ENABLED` 或 `REQUEST_CHANGES`。`APPROVE_NONE_ENABLED` 可以关闭本轮盘点责任，但不关闭“至少一个协议可用”的 G2-03 功能门禁，G2-04 继续 `DENY_ALL`。

## 6. 本地验收命令

```powershell
cd WEB
pnpm verify:device-event-protocol-evidence
pnpm verify:device-event-protocol-evidence -- --self-test
pnpm verify:device-event-protocol-evidence -- --file .doc/技术设计/电力运维云平台/assets/c1p-g2/OWNER_EVIDENCE.json
```

默认命令只验证仓库 DRAFT 模板和两份 Schema，不把空模板视为批准。`--file` 只接受仓库相对 JSON 路径；APPROVED 文件没有 ENABLED 协议时可通过文档验收，但输出 `qualification=NO_ENABLED_PROTOCOL`，G2-03 功能门禁仍保持 OPEN。

## 7. 关闭条件与下一步

1. owner 提交通过 v1 Schema 和语义校验的独立证据文档；
2. Sol 复核代码/协议/fixture 一致性、哈希和 ACK 时序；
3. 至少一个 direct 协议为 `ENABLED` 后，G2-03 才能进入 `CLOSED-CONTRACT`；
4. G2-03 关闭只允许产品负责人开始 G2-04 allowlist 签署，不自动开启 C1A；
5. 四项 G2 输入均关闭、ADR-019 Accepted 且 ADR 索引同提交更新后，才可另行冻结实现白名单。

当前下一动作是设备协议 owner 选择候选协议并填写证据；执行者不得替 owner 选择、补造 fixture 或签署。
