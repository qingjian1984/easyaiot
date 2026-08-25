# G1-THRESHOLD：规则版本与周期状态合同

> 版本：1.0.0
> 日期：2026-08-25
> 状态：DESIGN-CLOSED / Activation Input Open / No DDL
> 双基线：[平台功能计划 1.5.0](../../../../架构设计/平台功能计划.md)、[EasyAIoT 项目开发宪法 1.6.0](../../../../开发规范/EasyAIoT项目开发宪法.md)
> 上游：[ADR-019 0.2.0 Proposed](../../../../架构决策/电力运维云平台/ADR-019-告警来源身份周期与补偿接入.md)、[C1P-G1 任务单](../../P02-M2-02C1P-G1-来源合同缺口收敛任务单.md)

## 1. 结论

THRESHOLD 不建立第二套规则版本事实。稳定规则身份和不可变版本直接复用 V011 候选中的 `alarm_rule` 与 `alarm_rule_version`；旧 `device_property_threshold` 只作为迁移输入和观察期旧链配置，不再被新 evaluator 当作历史规则权威。

活动周期需要 V011 之后的独立候选资产 `alarm_threshold_cycle_state`，遥测逐规则幂等需要 `alarm_threshold_evaluation_inbox`。两者只在本文件冻结字段与约束要求，不指定迁移编号、不编写 SQL、不修改 V011。

现有历史告警无法无歧义恢复 rule revision 和 cycleKey，因此采用“历史只读、新 revision 起算”：旧 `device_threshold_alarm` 全部保留为 legacy 证据，不 backfill 为统一告警周期；新链只处理明确 activationAt 之后、命中已发布 rule version 的第一条 GOOD telemetry。

## 2. 仓库事实与禁止项

- `device_property_threshold` 直接原地更新 min/max/rules/alarmLevel，只有 updateTime，没有 revision 或内容 hash；
- 其唯一约束是 `(device_identification, property_code)`，不含 tenant；Mapper 查询也不显式带 tenant；
- 旧评估按当前行解释输入，静默窗口替代周期去重，恢复使用 `selectLatestOpen`；
- 旧告警写入 `tenant_id=0`、接收时 `now`，写库后直接 Kafka，不能成为新链的身份或原子证据；
- V011 已定义不可变 `alarm_rule_version`、发布生命周期、内容 hash 和统一告警 rule 外键，不允许新增平行版本表。

禁止以 legacy id + updateTime 拼接伪 revision，禁止把当前规则应用于 activationAt 之前的重放，禁止导入 latest OPEN 作为活动周期，禁止把 legacy `WARNING/CRITICAL` 自行映射成统一四级 severity。

## 3. 规则身份与版本合同

### 3.1 `alarm_rule`

每个 legacy `(tenantId, deviceIdentification, propertyCode)` 迁移为一个 `rule_kind=THRESHOLD` 的稳定 `alarm_rule`：

| 字段/事实 | 合同 |
|---|---|
| `id` | 平台统一 ID；sourceId 使用该值，不使用显示名 |
| `tenant_id` | 必须等于 legacy 行 tenant 且大于 0；由设备权威事实交叉校验 |
| `site_id` | activationAt 时设备唯一当前归属；规则配置是当前控制面事实，不用于推断历史事件 |
| `rule_code` | `legacy-threshold-{tenantId}-{legacyRowId}`；只作稳定迁移编码，不参与事件显示 |
| `rule_name` | 可读名称，可修改，不进入 identity |
| `rule_kind` | 固定 `THRESHOLD` |
| `capability_code` | standard/full 固定 `power.alarm.core`；mini 不创建 |
| origin evidence | 迁移清单保存 legacy table/id、快照 hash、activationAt；不向 V011 夹带新列 |

同一 legacy 行只能对应一个 ruleId；重复运行按 deterministic ruleCode + snapshot hash 返回 DUPLICATE，同 code 异 legacy identity 进入 `THRESHOLD_RULE_IDENTITY_COLLISION`。

### 3.2 `alarm_rule_version`

首个迁移版本字符串固定为 `1`。版本内容至少冻结：

| V011 字段 | THRESHOLD 内容要求 |
|---|---|
| `severity` | 由产品规则 owner 对每个迁移清单条目批准；无批准映射不发布 |
| `condition_tree` | deviceIdentification、propertyCode、规范数值比较器、min/max 或受控 rules 条件、持续时间；禁止脚本/SpEL/SQL |
| `recovery_policy` | 恢复比较器、迟滞、持续时间、qualityPolicy=`GOOD_ONLY`、retiredCyclePolicy=`RECOVERY_ONLY` |
| `schedule_policy` | activationAt、站点 IANA timezone、适用时段；activationAt 必须带 offset |
| `canonicalization_version` | 沿用 `jcs-rfc8785-v1` |
| `content_hash` | 对 severity + 三个 policy 的规范内容计算 `sha256:`；发布后不可覆盖 |
| `lifecycle` | `DRAFT → VALIDATED → PUBLISHED → RETIRED`；只有 PUBLISHED 可创建新周期 |

新 revision 发布后，旧 revision 立即停止创建新周期，但继续以冻结旧 predicate 处理其 ACTIVE 周期的 GOOD 数据，直到真实恢复。规则发布不能伪造 RECOVERED，也不能把旧 cycleKey 搬到新 revision。新旧 revision 的 ACTIVE 周期允许短暂并存，查询、对账与 UI 必须显示 revision，禁止按设备+属性静默合并。

## 4. `alarm_threshold_cycle_state` 候选合同

### 4.1 字段

| 字段 | 语义/约束 |
|---|---|
| `id` | 平台统一 ID |
| `tenant_id/site_id` | 正数；与 rule、设备权威一致 |
| `device_identification/property_code` | 规范业务编码；显示名不得进入 |
| `rule_id/rule_version_id/rule_version` | 同租户 V011 不可变版本三元组 |
| `source_id/source_identity_hash` | ADR-019 sourceId 与其 canonical identity SHA-256 |
| `state` | `NORMAL/ACTIVE/RECOVERED` |
| `cycle_key/cycle_identity_hash` | ACTIVE/RECOVERED 必填；NORMAL 可空 |
| `first_source_message_id` | 创建当前周期的 canonical telemetry UUID；ACTIVE/RECOVERED 必填 |
| `last_source_message_id` | 最近成功改变/确认该状态的 telemetry UUID |
| `last_collected_at_ms/last_sequence` | 事件水位；按 TD-003 `(epochMillis,sequence,messageId)` 排序 |
| `breached_at/recovered_at` | ACTIVE 必须有 breachedAt 且 recoveredAt 空；RECOVERED 两者都存在且有序 |
| `occurrence_count` | 当前/最近周期 GOOD breach 次数，非负且不回退 |
| `state_version` | CAS 版本，从 0 单调递增 |
| `last_value_hash` | 规范 telemetry value/quality 证据摘要，不保存到日志 |
| 审计列 | createdAt/updatedAt；均为服务端 UTC 记录时间，不参与 identity |

唯一性要求：`(tenant_id, rule_version_id, source_id)` 唯一；同一 `source_id` 同时至多一个 ACTIVE 状态；source/cycle UUID 与 canonical hash 冲突必须隔离。后续 SQL 必须为所有列提供中文 COMMENT，并证明索引不重复 V011 既有索引。

### 4.2 CAS 转换

| 当前状态 | 有序 GOOD 输入 | CAS 后状态 | 输出 |
|---|---|---|---|
| 无行/NORMAL/RECOVERED | breach | ACTIVE，新 cycleKey，version+1 | RAISED |
| ACTIVE | breach | ACTIVE，同 cycleKey，occurrence+1，version+1 | RAISED occurrence |
| ACTIVE | recovery | RECOVERED，同 cycleKey，version+1 | RECOVERED |
| NORMAL/RECOVERED | recovery | 不变 | no-op |
| 任意 | 非 GOOD | 不变 | `THRESHOLD_QUALITY_NOT_EVALUABLE` |
| 任意 | 重复逐规则 evaluation identity | 不变 | DUPLICATE |
| 任意 | 早于水位 | 不变 | `THRESHOLD_EVENT_OUT_OF_ORDER` |

水位严格使用 TD-003 的 `(collectedAt epochMillis, sequence, canonical messageId)`。messageId 不单独用于排序；相同三元组异 content hash 为 collision。并发 CAS 失败者重新读取：若目标状态已由同一 evaluation identity 达成则 DUPLICATE，否则重新按新状态裁决，禁止创建第二 ACTIVE 周期。

## 5. `alarm_threshold_evaluation_inbox` 候选合同

一条 telemetry 可命中多个规则，因此幂等键固定为：

```text
tenantId + telemetryMessageId + ruleVersionId
```

字段至少包括 `tenant_id/telemetry_message_id/rule_id/rule_version_id/input_hash/status/result_code/source_event_message_id/received_at/processed_at`。状态只允许 `RECEIVED/PROCESSED/QUARANTINED`：

- 首插成功才可锁 cycle state；
- 同键同 hash 且 PROCESSED 返回既有结果，零新增状态/outbox；
- 同键异 hash 进入不可回退 QUARANTINED，保留首次 hash；
- cycle CAS、evaluation inbox 和 source-event outbox 必须同一事务提交；任一步失败全部回滚；
- 不在 Inbox 保存自由错误堆栈或完整凭据/设备 payload。

## 6. Legacy 迁移决策

### 6.1 预览清单

迁移只生成可重复预览，逐行至少记录：legacy id、tenant、device/property、当前 assignment/site、min/max/rulesJson 的规范 hash、legacy alarmLevel、候选 severity、candidate ruleCode/version、activationAt、decision 和 reasonCode。

稳定决策：

| 条件 | decision/reasonCode |
|---|---|
| tenant<=0 或设备权威冲突 | `QUARANTINE/THRESHOLD_TENANT_INVALID` |
| 无唯一当前 assignment | `QUARANTINE/THRESHOLD_SITE_NOT_UNIQUE` |
| rules JSON 非法或条件矛盾 | `QUARANTINE/THRESHOLD_RULE_INVALID` |
| min/max 与 rules 同时存在但语义不可唯一合并 | `QUARANTINE/THRESHOLD_RULE_AMBIGUOUS` |
| severity 未经产品确认 | `WAITING_INPUT/THRESHOLD_SEVERITY_UNAPPROVED` |
| enabled=0 | `RETAIN_LEGACY/THRESHOLD_DISABLED` |
| 全部通过 | `READY_NEW_REVISION` |

当前 SQL 的 unique key 不含 tenant，迁移画像必须额外检查跨租户设备编码冲突；不得因数据库当前“碰巧唯一”而省略 tenant 过滤。

### 6.2 Activation

1. 决策所有者批准预览清单、severity 映射和统一 activationAt；
2. 在受控迁移中创建 rule/version，但本任务不执行；
3. activationAt 前 telemetry 永不由新 revision 解释；
4. activationAt 后第一条有序 GOOD telemetry 从 NORMAL 起算，若 breach 创建新周期；
5. legacy OPEN/CLEARED 不导入、不自动恢复、不生成 source mapping；仅作为观察期对账参考；
6. 观察期旧通知 owner 唯一，新统一通知关闭；满足差异门禁后另行切换。

回滚只停新 evaluator/relay 并保留已创建不可变版本、cycle/outbox/告警事实；不得删除历史或让 legacy 当前行解释已处理新事件。

## 7. 测试矩阵（后继实现门禁）

- 规则：发布不可变、同 hash 重跑、异 hash collision、版本退役/回滚、旧 ACTIVE recovery-only；
- 迁移：tenant/site 冲突、非法 JSON、min/max 冲突、未批准 severity、disabled、重复预览；
- 状态：首次 breach、重复 breach、真实 recovery、恢复后再 breach、并发双 raised、raised/recovered 竞争；
- 顺序：同毫秒 sequence、同 sequence messageId、迟到、重放、同 ID 异 hash；
- quality：GOOD 与其余五类，确保非 GOOD 零状态变化；
- 事务：Inbox/cycle/outbox 任一步失败全回滚，提交后崩溃可重放；
- 档位：standard/full 同合同，mini 无表、Bean、task、配置、迁移或 capability 残留。

## 8. 未关闭输入

- 产品规则 owner 尚未批准 legacy `INFO/WARNING/CRITICAL → INFO/NORMAL/IMPORTANT/EMERGENCY` 的逐项映射；
- 既有规则是否全部保留、disabled 是否迁移为 DRAFT、activationAt 尚未签署；
- V011 仍是未执行候选，cycle/evaluation 资产必须使用独立后继迁移并经 ADR-013 评审。

这些输入阻断 THRESHOLD adapter 和 ADR-019 Accepted，但不推翻本文件已冻结的版本、周期、幂等和历史不 backfill 决策。
