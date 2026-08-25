# G1-OUTBOX：来源事件 Outbox 与 Relay 合同

> 版本：1.0.0
> 日期：2026-08-25
> 状态：DESIGN-CLOSED / Assets and Runtime Open / No DDL
> 双基线：[平台功能计划 1.5.0](../../../../架构设计/平台功能计划.md)、[EasyAIoT 项目开发宪法 1.6.0](../../../../开发规范/EasyAIoT项目开发宪法.md)
> 上游：[ADR-019 0.2.0 Proposed](../../../../架构决策/电力运维云平台/ADR-019-告警来源身份周期与补偿接入.md)、[ADR-014](../../../../架构决策/电力运维云平台/ADR-014-Outbox事件Transport与消费者Inbox.md)

## 1. 结论与责任边界

THRESHOLD 与 DEVICE_EVENT 共用一张 iot-device 本地 `alarm_source_event_outbox` 候选表和同一个 relay 合同，但各自拥有独立 ingress Inbox：

- THRESHOLD：`alarm_threshold_evaluation_inbox`，幂等域为 telemetry message + ruleVersion；
- DEVICE_EVENT：`alarm_device_event_source_inbox`，幂等域为 tenant + product + device + 原始 requestId；
- 统一告警入口继续由既有 `alarm_source_inbox` 与 `AlarmSourceTransactionService.REQUIRES_NEW` 负责；
- 对外告警领域事件继续由 V011 `alarm_outbox` 负责，不能复用来源 Outbox，也不能反向由来源 relay 发送通知。

三种 Outbox 分工不同：TD-003 projection outbox 证明 telemetry 已 Store；本文件 source-event outbox 证明来源 evaluator 已形成 `AlarmSourceEvent.v1`；V011 alarm_outbox 证明统一告警事实已提交。表、状态、指标和重试不能混用。

## 2. 端到端事务流

### 2.1 THRESHOLD

```text
iot-sink TelemetryStore STORED/DUPLICATE
  + telemetry Inbox COMPLETED
  + TELEMETRY_PROJECTED_V1 outbox             （iot-sink 本地事务）
      ↓ 至少一次 transport
iot-device threshold consumer
  + evaluation Inbox
  + ruleVersion 解析
  + cycleState CAS
  + AlarmSourceEvent source outbox             （iot-device 单事务）
      ↓ 本地 relay，至少一次
AlarmSourceTransactionService.REQUIRES_NEW
  + alarm_source_inbox
  + alarm_record/mapping/action/alarm_outbox    （统一告警事务）
```

Telemetry consumer 只有在 evaluation/cycle/source-outbox 事务提交后才 ACK/提交 offset。非 GOOD、未命中或有序 no-op 也必须把 evaluation Inbox 标为 PROCESSED 并记录稳定 resultCode，防止无限重投。

### 2.2 DEVICE_EVENT

```text
canonical direct event
  → source Inbox 首插/重复/碰撞裁决
  → 保存 legacy device_event（首次合法来源）
  → route + allowlist + cycle 解析
  → 未映射：PROCESSED/no outbox
  → 已映射：AlarmSourceEvent source outbox
                                      （iot-device 单事务）
  → 提交后 transport ACK/offset
  → 与 THRESHOLD 共用本地 relay/统一告警事务
```

同 identity/hash 重放不得再次插入 legacy `device_event`。当前 subscriber 捕获异常后只记录日志，且消息总线没有在此链冻结手工 ACK 语义；实现前必须改为“事务异常向 transport 传播，提交后确认”，但本任务不修改代码。

## 3. Ingress Inbox 候选合同

### 3.1 共同三态

`RECEIVED/PROCESSED/QUARANTINED` 语义与 alarm source Inbox 一致：

- RECEIVED 只存在于活动业务事务或可恢复首插状态；
- PROCESSED 表示来源裁决、周期状态和必要 source outbox 已原子提交；
- QUARANTINED 是同 identity 异 hash、未知主版本或不可自动覆盖的终态；
- retry count、lease、退避属于 transport，不塞入业务 Inbox；事务异常回滚，transport 不确认；
- 原隔离行不可重置，人工重驱使用新 identity 并关联 originalInboxId。

### 3.2 `alarm_threshold_evaluation_inbox`

字段和唯一性见 [G1-THRESHOLD §5](./G1-THRESHOLD-规则版本与周期状态合同.md)。必须额外保存 `projectionEventId/projectionEventHash/routeEvidenceHash/resultCode`，以证明 Store 投影、历史 route 和逐规则裁决来自同一输入。

### 3.3 `alarm_device_event_source_inbox`

| 字段 | 语义/约束 |
|---|---|
| `id` | 平台统一 ID |
| `source_ingress_id` | 对 tenant/product/device/sourceRequestId 的确定性 UUIDv5 |
| `tenant_id/product_identification/device_identification` | 权威解析后的 direct subject |
| `source_request_id_hash` | 原 requestId 的 `sha256:`；正文和日志不暴露原值 |
| `source_payload_hash` | 原事件 canonical bytes hash |
| `event_identifier/occurred_at/source_offset` | Topic/原始事件事实 |
| `site_id/assignment_id/route_evidence_hash` | 历史 assignment resolver 输出 |
| `mapping_id/mapping_version/mapping_hash` | 未映射时可空；映射成功时必填 |
| `legacy_event_id` | 首次合法来源保存的 legacy 行 ID；重复不新增 |
| `status/result_code/source_event_message_id` | 三态、稳定裁决和可选输出 event ID |
| `received_at/processed_at` | 服务端审计时间，不参与来源 identity |

唯一 `source_ingress_id`；同 ID 同 sourcePayloadHash 返回 DUPLICATE，同 ID 异 hash QUARANTINED。未 allowlist 不是技术异常：保存 legacy 后以 `DEVICE_EVENT_NOT_ALLOWLISTED` 正常 PROCESSED，零 source outbox。

## 4. `alarm_source_event_outbox` 候选合同

### 4.1 字段

| 字段 | 语义/约束 |
|---|---|
| `id` | 平台统一 ID |
| `message_id` | ADR-019 UUIDv5；全局唯一，等于 AlarmSourceEvent Envelope messageId |
| `tenant_id/source_type` | 正 tenant；`THRESHOLD/DEVICE_EVENT` |
| `source_message_id_hash` | 原 telemetry UUID 或设备 requestId 的受控 hash |
| `source_id/cycle_key/source_action` | ADR-019 确定性 identity；action 仅 RAISED/RECOVERED |
| `source_identity_hash` | canonical sourceMessage/sourceId/cycle/action/contractVersion 的 `sha256:`；租户内唯一 |
| `event_type/schema_version` | 固定 `device.alarm.source-event.v1` / `1` |
| `payload_canonical/payload_hash` | AlarmSourceEvent canonical JSON 与 `sha256:`；大小上限沿用事件 Schema/DDL 评审值 |
| `status` | `PENDING/PROCESSING/DELIVERED/DEAD_LETTER` |
| `retry_count/max_retries/next_attempt_at` | 有界重试事实；首插 retry=0 |
| `lease_owner/lease_until` | PROCESSING 必填；其他状态清空 |
| `last_error_code/last_error_summary` | 稳定码和脱敏短摘要，不含 payload/凭据/URL/手机号 |
| `created_at/delivered_at/dead_lettered_at/updated_at` | UTC 审计时间 |
| `origin_inbox_type/origin_inbox_id` | 指回 threshold 或 device event ingress Inbox |

唯一性：全局 `message_id` unique；`(tenant_id,source_identity_hash)` unique。冲突时必须比较 payloadHash/canonical identity；同 ID 同 hash 为 DUPLICATE，同 ID 异 hash 不覆盖首次行并产生 `ALARM_SOURCE_OUTBOX_ID_COLLISION` critical。

后继 SQL 必须放在 V011 之后的独立迁移，全部表/列中文 COMMENT，提供 forward-fix/down 评审；不得修改 V011，不得复用 `device_threshold_alarm.kafka_sent` 或 `alarm_outbox`。

### 4.2 状态与 claim

| 当前 | 条件 | 原子结果 |
|---|---|---|
| PENDING | `nextAttemptAt<=now` | 单语句 claim 为 PROCESSING，设置 leaseOwner/leaseUntil |
| PROCESSING | lease 未过期 | 其他副本跳过 |
| PROCESSING | lease 已过期 | 允许恢复性 claim，更换 owner/lease |
| DELIVERED/DEAD_LETTER | 任意 | 终态跳过 |

claim 必须 `ORDER BY createdAt,id`、有界 batch，并用 `FOR UPDATE SKIP LOCKED` 或等价单语句原子实现。所有状态回写必须带 `messageId + leaseOwner + status=PROCESSING` CAS；丢失 lease 不得覆盖新 owner。

## 5. Relay 与重试

source relay 是 iot-device 本地应用 relay，不引入新的 Kafka Topic：它解析并验证已持久化 canonical payload，调用 `AlarmSourceTransactionService.process()`；该方法继续 `REQUIRES_NEW`。明确结果：

| 告警入口结果 | source outbox 处置 |
|---|---|
| 首次 PROCESSED | DELIVERED |
| 同 messageId 同 hash DUPLICATE | DELIVERED |
| 同 messageId 异 hash/未知主版本 QUARANTINED | DEAD_LETTER + critical，不自动重驱 |
| 瞬时数据库/资源不可用 | PENDING + retry |
| adapter final contract error | DEAD_LETTER |

候选执行参数沿用现有告警 Outbox 的已实现本地策略：lease 60 秒、batch 100、指数退避 1 秒至 16 秒并带 bounded jitter；`maxRetries` 随行保存，候选 10，必须在实现任务容量评审后冻结。参数变化不能改变至少一次、幂等或终态语义。

进程在告警事务提交后、source outbox 标 DELIVERED 前崩溃会重投；告警 source Inbox 必须以 messageId/hash 吸收。禁止使用跨事务“先标 delivered 再调用”避免重复，因为那会丢事件。

## 6. ACK、offset 与提交边界

| 输入 | 确认条件 | 禁止行为 |
|---|---|---|
| TELEMETRY_PROJECTED_V1 | evaluation Inbox/cycle/source outbox 或 no-op 同事务提交 | Store 未完成即评估；事务失败仍提交 offset |
| direct DEVICE_EVENT | source Inbox + 首次 legacy fact + 可选 source outbox 同事务提交 | subscriber 吞异常；先 ACK 后写库 |
| source local relay | 告警入口返回首次/合法重复且 source outbox 标 DELIVERED | 仅调用成功未回写即删除 outbox |

消息总线若无法提供手工 ACK/offset，必须把 consumer 改为可证明的数据库 Inbox 拉取/可靠 transport，不能声称当前 fire-and-forget 回调满足本合同。

## 7. 通知 owner XOR

source relay 只产生统一告警事实，不发送短信、电话、APP、Kafka legacy alert。阶段合同：

| 阶段 | old notification owner | unified notification owner | 门禁 |
|---|---:|---:|---|
| OFF/观察前 | 1 | 0 | source relay 可关闭 |
| OBSERVE | 1 | 0 | 新告警事实/差异可生成，对外通知禁用 |
| CUTOVER | 0 | 1 | 同一 capability generation 预检 XOR 后切换 |
| ROLLBACK | 1 | 0 | 先停 unified owner，再恢复 old；统一事实保留 |

任何 `1/1` 或 `0/0`（需要通知的启用期）均为 `ALARM_NOTIFICATION_OWNER_INVALID`，切换失败整体不生效。

## 8. 对账与可观测性

至少提供：source outbox backlog/age、delivery result、dead letter depth、ingress quarantined、route/mapping no-op、relay duration、lease recovery 数量；标签只用 sourceType/result/errorCode，不用 messageId/deviceId。

对账游标按 `(createdAt,id)`，分别比较：

1. ingress PROCESSED 且应产生事件 → source outbox；
2. source outbox DELIVERED → alarm source Inbox PROCESSED；
3. alarm source Inbox PROCESSED → source mapping/alarm action；
4. OBSERVE 期 legacy 与 unified 按稳定 migration/source identity 比较，不按相近时间猜测。

差异清零是切换必要条件。DEAD_LETTER、QUARANTINED、identity/hash 冲突或通知 XOR 失败时禁止切换。

## 9. 后继实现测试矩阵

- Inbox：首插、合法重复、异 hash、未知版本、事务回滚、隔离不可重置；
- Outbox：并发 claim、租约未过期/过期、批次顺序、CAS 丢 lease、retry/final、耗尽；
- 崩溃：来源提交前后、告警入口提交前后、标 DELIVERED 前后；
- 流程：一 telemetry 多规则、非 GOOD no-op、未映射 event no-op、legacy 保存失败；
- 安全：跨租户、payload 日志、错误摘要脱敏、伪造 source identity；
- owner：OFF/OBSERVE/CUTOVER/ROLLBACK 全矩阵和 1/1、0/0 反例；
- 档位：standard/full 同合同，mini 无表/Bean/relay/scheduler/config/metric/migration 残留。

## 10. 开放项

- TD-003 projection outbox 与可信 workload/product 字段尚未实现；
- DEVICE_EVENT transport ACK/offset 责任尚未实现；
- 三张候选表、repository、relay 和测试均未授权；
- 候选 maxRetries、调度间隔、容量和保留期需实现/DBA/运维评审。

本文件关闭 source Outbox 的架构合同，不关闭资产和运行证据门禁。
