# P02-M2-02C1P-G2-04：设备事件产品 Allowlist 与 Owner 确认单

> 版本：0.1.0
> 日期：2026-08-26
> 状态：CONTRACT PREPARED / WAITING G2-03 / DENY_ALL / WAITING PRODUCT OWNER / C1 CLOSED
> 设计责任：GPT-5.6 Sol
> 双基线：[平台功能计划 1.5.0](../../架构设计/平台功能计划.md)、[EasyAIoT 项目开发宪法 1.6.0](../../开发规范/EasyAIoT项目开发宪法.md)
> 上游：[G2-03 协议证据与 Owner 确认单 0.2.0](./P02-M2-02C1P-G2-03-设备事件协议证据与Owner确认单.md)、[G1-EVENT 输入说明 1.0.0](./assets/c1p-g1/G1-EVENT-产品Allowlist输入说明.md)、[ADR-019 0.2.0 Proposed](../../架构决策/电力运维云平台/ADR-019-告警来源身份周期与补偿接入.md)
> 生产合同：[allowlist v1 Schema](./assets/c1p-g1/device-event-alarm-allowlist-v1.schema.json)、[空白 DRAFT 模板](./assets/c1p-g1/device-event-alarm-allowlist-template.json)
> 评审合同：[review package v1 Schema](./assets/c1p-g2/device-event-alarm-allowlist-review-v1.schema.json)、[review DRAFT 模板](./assets/c1p-g2/device-event-alarm-allowlist-review-template.json)、[mapping fixture v1 Schema](./assets/c1p-g2/device-event-alarm-mapping-fixture-v1.schema.json)

## 1. 当前结论与授权边界

当前 G2-03 没有任何 `ENABLED` direct 协议，生产 allowlist 模板仍为 `DRAFT / mappings=[] / approval=null`。因此本轮只能冻结产品回答、fixture 和交叉证据合同，不得填写虚构 product、model、eventIdentifier、severity、correlation 或恢复条件，不得把 review package 标为 `COMPLETE`，运行结论保持 `DENY_ALL`。

本确认单不是产品负责人签署，不授权 DEVICE_EVENT adapter、DDL、transport、数据库、双写、backfill、对账、切读或 capability。产品 allowlist 即使最终完成，也只关闭 G2-04 输入；G2-01～03、ADR-019 和 C1A 仍按各自门禁独立关闭。

## 2. 两层文档职责

| 文档 | 作用 | 是否进入生产事实 |
|---|---|---|
| allowlist v1 | 只保存版本化 mapping、scope、raised/recovered matcher、severity、correlation/time pointer、升级策略及产品批准 | 是；但只有 APPROVED、hash 正确且全部交叉校验通过后才可成为候选生产事实 |
| review package v1 | 固化 allowlist 文件 hash、G2-03 协议证据 hash、逐 mapping 产品回答和 fixture 文件 hash | 否；只作为评审与 CI 输入 |
| mapping fixture v1 | 保存单条/成对脱敏事件 payload 和预期匹配、周期关联、时间及失败码 | 否；只作为可复现验收证据 |

不得把评审 fixture、产品解释文本或协议原始报文复制进生产 allowlist；也不得只提交生产 allowlist 而省略 review package。

## 3. G2-03 硬依赖

每个 `mappingReview.protocolId/protocolVersion` 必须精确命中 review package 所引用 G2-03 evidence 中的一个 `ENABLED` 条目，并满足：

1. evidence 文档为 `APPROVED`，approval hash 正确；
2. direct Topic、requestId、occurredAt、正文 hash、commit 后 ACK 和五类协议 fixture 已通过 G2-03 verifier；
3. mapping 的 product/model scope 与该协议可承载的 direct subject 一致；
4. SUB_EVENT、脚本改写或未批准 codec 不能通过产品 allowlist 获得协议资格。

没有任何 ENABLED 协议时，review package 只能保持空白 DRAFT，产品负责人不能以 `APPROVE_NONE_ENABLED` 生成 APPROVED allowlist。

## 4. matcher 与映射语义

### 4.1 确定性匹配

- `eventIdentifier` 大小写敏感、逐字节精确匹配；不得 trim、转小写或使用模糊/正则匹配；
- `payloadPredicates` 使用 AND；`EXISTS` 只判断 RFC 6901 pointer 是否存在，值为 JSON null 仍算存在；
- `EQUALS` 按 JSON 标量类型和值比较，不做字符串/数字/布尔隐式转换；
- JSON Pointer 按 RFC 6901 解码 `~0/~1`，数组索引必须是规范十进制形式；越界、非法 escape 或穿越非容器节点均为未命中；
- 一条事件在同一 scope/effective 选择结果中最多命中一个 mapping；不同 mappingId 的 scope/matcher 重叠一律拒绝，不设隐式优先级。

### 4.2 scope 与版本

- `PRODUCT` 与同 product 的 `PRODUCT_MODEL` 若可同时命中同一事件，视为歧义；不能用“model 更具体”作为未写入合同的优先级；
- 同一 mappingId 的 mappingVersion 必须递增，`effectiveFrom` 严格递增且不得相同；
- 新 RAISED 使用 occurredAt 时刻已生效的最高 mappingVersion；
- RECOVERED 先按 correlation 查活动周期并固定使用该周期创建时的 mappingVersion，不重新选择最新版本；
- 旧版本只有在 ACTIVE 周期为 0、对账完成且有 owner 退役决策后才能 RETIRED/删除。

### 4.3 identity、时间与严重度

- correlation pointer 必须从 raised/recovered fixture 提取相同、非空、1～512 UTF-8 字节字符串；不接受对象、数组、null、接收时间或后端随机值；
- occurredAt pointer 必须提取 RFC 3339、显式 offset、恰好毫秒精度的字符串，并满足所引用 G2-03 协议的未来/历史容差；
- severity 只能来自 mapping 固定值，payload 不得覆盖；
- `EMERGENCY` 必须在产品回答中确认不可忽略、值班升级和安全处置能力，缺一项不得批准；
- `oneShotPolicy=REJECT`、`lifecycle=PAIRED`、`upgradePolicy=PIN_ORIGINAL_VERSION_UNTIL_RECOVERED` 保持 v1 固定值。

## 5. 每条 mapping 的产品回答

review package 的 `answers` 必须逐项填写，不接受“同上”“按默认”或只引用界面名称：

| 字段 | 必须回答 |
|---|---|
| alarmRationale | 为什么这是需要处置闭环的告警，而非普通业务事件 |
| raisedRule | 唯一 RAISED 事件和 predicate 条件 |
| recoveredRule | 唯一 RECOVERED 事件和 predicate 条件 |
| correlationRationale | 字段为何能在重试、恢复、跨进程和升级期间稳定关联同一周期 |
| severityRationale | 固定等级依据、处置时限和升级影响 |
| emergencyNonIgnorableConfirmed | severity 为 EMERGENCY 时必须为 true；其他等级不得借此升级语义 |
| upgradeClosurePlan | mapping 升级后旧活动周期如何继续恢复、对账和退役 |

## 6. fixture 最小集合

每条 mapping 必须提交并哈希固定：

1. `RAISED`：唯一命中 raisedMatcher，提取非空 correlation 和合法 occurredAt；
2. `RECOVERED`：唯一命中 recoveredMatcher，与 RAISED correlation 完全相同；
3. `UNMATCHED`：不得命中任何 mapping，失败码 `DEVICE_EVENT_NOT_ALLOWLISTED`；
4. `CORRELATION_MISMATCH`：raised/recovered 各自匹配但 correlation 不同，必须拒绝闭合；
5. `PREDICATE_TYPE_MISMATCH`：只要 mapping 含 predicate 就必填，证明无隐式类型转换；
6. `FIELD_MISSING`：只要 mapping 使用 predicate/correlation/time pointer 就必填，证明缺字段 fail-closed。

fixture 的 `expected` 是待核验预期，不是自证结果；后续本地 verifier 必须重新执行 matcher、JSON Pointer、时间和跨 fixture 校验。

## 7. hash 与批准顺序

1. allowlist `approval.contentSha256` 继续严格使用 G1-EVENT §3 算法：排除 contentSha256 自身，对 schemaVersion、canonicalizationVersion、documentStatus、revision、mappings 和 approvalEvidence 做 RFC 8785 JCS + SHA-256；
2. review package `reviewApproval.contentSha256` 排除自身后，对完整 review package 做 RFC 8785 JCS + SHA-256；
3. review package 中 allowlist、protocol evidence 和 fixture artifact 的 SHA-256 必须与仓库文件字节一致；
4. 顺序固定为：协议证据批准 → fixture 冻结 → review package 校验 → allowlist 产品批准 → review 批准 → Sol 独立复核；正文或 artifact 变化后旧 hash/批准全部失效。

## 8. 产品 Owner 确认区（待填写）

| 字段 | 待填内容 |
|---|---|
| allowlistDocument | 独立 APPROVED allowlist 仓库路径、revision、contentSha256 |
| reviewPackage | COMPLETE review package 仓库路径、revision、contentSha256 |
| protocolEvidence | APPROVED G2-03 evidence 路径、hash、实际 ENABLED protocolId/version |
| approvedMappings | mappingId/version、product/model、raised/recovered、severity 摘要 |
| approvedBy / approvedAt | 产品负责人身份和 RFC 3339 显式 offset 时间 |
| decisionRef | 可审计提交/评审记录；聊天中的泛化“继续”不构成签署 |
| decision | `APPROVE_ALLOWLIST` / `REQUEST_CHANGES`；当前不得填写前者 |

## 9. 当前门禁与下一步

| 项目 | 状态 |
|---|---|
| G2-03 ENABLED direct 协议 | `0 / OPEN` |
| production allowlist | `DRAFT / mappings=0 / approval=null` |
| review package | `DRAFT / mappingReviews=0 / reviewApproval=null` |
| G2-04 | `CONTRACT-PREPARED / WAITING G2-03 + PRODUCT OWNER` |
| DEVICE_EVENT runtime | `DENY_ALL` |
| C1A/C1-C4 | `CLOSED` |

下一步先实现并本地验收 G2-04 review verifier；它只能验证空模板和未来 owner 文件，不能生成产品 mapping。G2-03 没有 ENABLED 协议前，任何 COMPLETE/APPROVED 正例都只能是临时测试数据，不得提交为生产输入。
