# ADR-014：Outbox 事件 Transport 与消费者 Inbox（TD-005）

> 状态：**Accepted**（2026-08-10：V005 Inbox 版本资产已冻结并随 V003/V004 经 ADR-013 runner 落入本地目标集成实例；MIG-009 与目标实例真实 PostgreSQL 合同测试 PASS。剩余 OPEN：TD-001 `CollectorConfigReleasePort`/任务 7、双发对账演练、Kafka DLQ topic 深度 broker 侧导出）
> 版本：1.3.8
> 日期：2026-08-10
> 决策范围：TD-005 版本/绑定/审计 Outbox 的异步投递与消费幂等
> 影响章节：《EasyAIoT 项目开发宪法》§2.1、§2.3、§5.4、§6.2、§6.3、§8、§10.2、§12、§14；TD-005 migration §4.6
> 产品基线：平台功能计划 1.5.0 / 项目开发宪法 1.6.0
> 宪法专项评审：[ADR-014评审报告-宪法专项.md](../../开发规范/ADR-014评审报告-宪法专项.md)

| 版本 | 日期 | 变更 |
|---|---|---|
| 1.0.0 | 2026-08-06 | 首次形成 Kafka transport 与消费者 Inbox 候选 |
| 1.1.0 | 2026-08-06 | 处置宪法专项评审 M-01～M-08/P-01～P-07：补目标用户与角色、档位行为、事件 Envelope 冻结引用、Schema 入 API 模块路径、CI 合同门禁、配置清单、可观测性与对账、重试/DLQ/保留值、Inbox 落库绑定 ADR-013、Kafka 安全态势声明、双 UNIQUE 收缩、文档同步计划 |
| 1.2.0 | 2026-08-07 | 处置 [DBA/架构专项评审](../../开发规范/ADR-013与ADR-014评审报告-DBA架构专项.md)：本 ADR 无 HIGH/MEDIUM 设计变更；M-10 事件 fixture 已用 Ajv Draft 2020-12 strict + ajv-formats 复跑 4/4 PASS（仓库外临时工具目录，CI 接线继续 OPEN）；`power_model_event_inbox` 已纳入 check-comments 注释门禁清单 |
| 1.3.0 | 2026-08-08 | **转 Accepted**：随 ADR-013 一并处置的三项人工闭环全数关闭（双签 / 压测豁免 / 画像生产重跑 PASS + owner 指定，记录见 ADR-013 1.5.0 与证据包 verdict.md）。消费者/transport/Inbox 实现、CI 合同门禁接线为转 Accepted 后的 TD-005 冻结工作，继续 OPEN 跟踪 |
| 1.3.1 | 2026-08-08 | 实现证据第一批落地：① Schema 归位——4 个 V1 Schema 复制入 `iot-device-api` 资源 `schema/power/model/v1/`（与评审资产 sha256 逐项一致，单源运行时拷贝），新增共享合同类型 `PowerModelEventEnvelope`（Envelope 不变量校验、topicKey、payload_hash、topic/消费者组常量）；② 消费/投递领域逻辑——`InboxArbiter`（PROCEED/DUPLICATE/RETRYABLE/QUARANTINE_HASH_CONFLICT/REJECT_UNKNOWN_MAJOR_VERSION/AWAITING_DISPOSITION）与 `OutboxRelayPolicy`（claim 租约恢复、retryable/final 分流、1s→16s 指数退避、超限 DEAD_LETTER），合同测试 31/31 PASS；③ CI 门禁接线——`pnpm verify:event-contracts`（Ajv 2020-12 strict + ajv-formats，4 Schema + 4 fixture + OUT-008 未知主版本反例 + strict 反例 + 文档资产/API 资源字节一致性 + 双主版本目录扫描）样例运行 24 项全 PASS，ajv/ajv-formats 已入 WEB 显式 devDependencies。剩余 OPEN：持久化接线（Outbox/Inbox Mapper、发布器、collector 协调器）、双发对账演练、容量压测（维持豁免口径，候选值不冻结） |
| 1.3.2 | 2026-08-08 | 实现证据第二批（持久化接线）：① 入列服务 `PowerModelOutboxService`——`Propagation.MANDATORY` 结构化强制同事务提交（无活动事务即拒绝），capability `power.device.model` 未启用时 fail-closed（mini 档不产生待投递残留）；② 发布器 `PowerModelOutboxRelay`——claim（租约/批量参数透传）→ send → 回写编排，OUT-001～004 全路径；③ Inbox 写入 `PowerModelInboxWriter`——七路径（PROCESS/LOST_CONTENTION/DUPLICATE/RETRYABLE/异 hash 隔离 critical/未知主版本隔离 critical/维持隔离），offset 在 Inbox 写成功后提交的消费契约；④ 持久化端口与实现——`PowerModelOutboxRepository`/`PowerModelInboxRepository` 端口 + JDBC 实现（原子认领 `FOR UPDATE SKIP LOCKED` + UPDATE RETURNING、首插 `ON CONFLICT DO NOTHING`、隔离 upsert `ON CONFLICT DO UPDATE`）；⑤ Kafka 装配 `PowerModelEventKafkaConfiguration`（`power.model.events.enabled=true` 才装配，acks=all 候选、幂等 producer、有界重试/超时）+ `KafkaPowerModelEventTransport` 薄适配（retryable/final 分流、摘要脱敏不含 payload）。合同测试 23/23 PASS（fake 仓储/transport），设备域全量回归 148/148 PASS。JDBC 实现的真实库合同测试待 V001 经 ADR-013 runner 获批窗口落库后接入（沿用 TD005_PG_ENABLED 跳过模式）；collector 配置发布协调器与双发对账演练继续 OPEN |
| 1.3.3 | 2026-08-08 | 实现证据第三批（消费循环）：① `PowerModelEventEnvelopeCodec`——消费侧解析（畸形/缺字段/不变量违规全 fail-closed 稳定码），payload_hash 以原始消息正文计算与生产侧一致；② `PowerModelEventHandlerRegistry`——TD-001 collector 处理器注册表 + `PowerModelEventProcessingException`（retryable/final 分流），已知主版本未注册处理器按 final 进 DLQ 绝不静默丢弃；③ `PowerModelEventConsumerCoordinator`——单条裁决（P-07 全契约：markProcessed 后才 COMMIT offset、DUPLICATE/QUARANTINED/DLQ 处置后 COMMIT、LOST_CONTENTION 不提交等重投、retryable 1s→16s 退避超限 DLQ、DLQ 投递失败抛错不提交）；④ `PowerModelEventKafkaListener` 薄适配——手动 MANUAL_IMMEDIATE、nack 退避重投、`enabled=true` 门禁 + 消费容器工厂（批量上限候选 100）；⑤ `PowerModelEventWiringConfiguration`——编排 Bean 装配；发布器调度驱动（@Scheduled/Quartz）因 iot-device 无 @EnableScheduling 列为部署评审 OPEN（拒绝静默不触发），处理器注册表暂为空（TD-001 协调器实现时接入，空表下事件按缺失处理器进 DLQ）。合同测试 18/18 PASS，设备域全量回归 166/166 PASS |
| 1.3.4 | 2026-08-08 | 实现证据第四批（发布器调度驱动）：选型经 owner 部署评审裁定为 **Spring Scheduling**——iot-device 运行时经 iot-common-mq 的 `YudaoRedisMQConsumerAutoConfiguration` 传递激活调度设施（无任何 auto-config exclude），Quartz/iot-common-job 会引入新依赖与 QRTZ_* 表、对单功能轮询属过度设计，且多实例并发安全已由认领 SQL 的 `FOR UPDATE SKIP LOCKED` + 租约承担，调度层无需集群协调。落地：`PowerModelOutboxRelayScheduler`（fixedDelay 轮询 `relayOnce`，注入 Clock 保证确定性，单轮异常只记录异常类型摘要、绝不外抛中断轮询、绝不含 payload）+ `PowerModelEventWiringConfiguration` 显式 `@EnableScheduling`（类级 `enabled=true` 门禁不变，mini 不装配不调度；显式声明防传递依赖变化导致轮询静默停转）。新增候选配置 `power.model.events.relay.poll-interval-ms`（1000）/`initial-delay-ms`（5000），压测后冻结。合同测试 3/3 PASS，设备域全量回归 169/169 PASS |
| 1.3.5 | 2026-08-08 | 实现证据第五批（可观测性指标，§可观测性与对账 MUST 随消费者/发布器落地的缺口关闭）：① 指标端口 `PowerModelEventMetrics`（领域逻辑只依赖端口，测试手写 fake）+ Micrometer 适配 `MicrometerPowerModelEventMetrics`（指标名/tag 冻结，同 result 复用 Counter）；② 埋点——发布器记录 `power_model_event_publish_total{result∈published/retry_scheduled/dead_letter}` 与 `power_model_event_delivery_duration`（逐次 send 耗时），Inbox 写入器两条 critical 隔离路径计 `power_model_inbox_quarantined_total`；③ gauge 接线——`power_model_outbox_backlog`（仓储新增 `countByStatus`，PENDING+PUBLISHING 实时求值）、`power_model_dlq_depth`（DEAD_LETTER 行数；如实声明边界：Kafka DLQ topic 自身积压需 broker 侧导出器，列入开放项）；④ 全部 Bean 仍在 `enabled=true` 门禁内装配，mini 无指标残留。合同测试 5/5 新增 PASS（事件包 80/80），设备域全量回归 174/174 PASS |
| 1.3.6 | 2026-08-08 | 实现证据第六批（JDBC 真实库合同测试）：新增 `JdbcPowerModelOutboxRepositoryPostgresIntegrationTest`（6 项：首插/UNIQUE(event_id) 数据库裁决/hash CHECK 拒绝/原子认领四态（到期、未到期、租约未过期、租约过期恢复）/批量上限与 ORDER BY created_at,id 顺序/双连接 SKIP LOCKED 并发互斥且回滚恢复/三种回写状态迁移与非 PUBLISHING 防漂移守卫/错误摘要 128 截断/countByStatus gauge 数据源）与 `JdbcPowerModelInboxRepositoryPostgresIntegrationTest`（5 项：首插 ON CONFLICT true→false/findByEventId 视图/hash CHECK/markProcessed/隔离 upsert 插入后更新且保留首次隔离 payload_hash）。执行环境如实声明：本地临时评审库 `td005_contract_review`（postgres-server PostgreSQL，非 iot-device20 目标实例），DDL 由测试用 V001/consumer_inbox 资产自检（V001 整文件单语句执行，含 PL/pgSQL 触发器），执行 11/11 PASS；残留核对 outbox=0/inbox=0/audit=36（审计表追加写设计使然），临时库已 DROP 并验证不存在。沿用 TD005_PG_ENABLED 跳过模式（常规回归 21 项跳过全为 PG 集成测试设计内跳过）。V001 向目标实例落库仍 MUST 经 ADR-013 runner 获批窗口，与此批测试证据分开跟踪。设备域全量回归 185/185 PASS |
| 1.3.7 | 2026-08-10 | 冻结 V005 Inbox 与空表卸载入口 U004，runner 链扩至 V005；镜像评审库首次/幂等复跑、MIG-009、V004 反例和 8 项 PG 合同均 PASS 后，经用户窗口授权对 `iot-device20` 执行 V003/V004/V005 SUCCEEDED；目标测试 8/8 PASS 且五表残留 0 |
| 1.3.8 | 2026-08-12 | 声明作用域边界（对齐 [ADR-016](./ADR-016-EDGE退役与RUNTIME边缘执行边界.md)）：本 ADR 仅覆盖 Kafka `power-model-release` 事件 outbox/inbox；RUNTIME/VIDEO 算法告警经 MQTT 总线（`mqtt/iot-*`）接入，建立独立告警 Inbox/Schema，复用本 ADR 的 msgId/内容摘要/先 inbox 后副作用/重试 DLQ/未知主版本隔离/可观测性原则，但不纳入本 ADR 的 Kafka inbox 契约，也不复用 `power_model_event_inbox` 表 |

## 背景

TD-005 migration 要求：发布/绑定业务事实、领域审计和 Outbox 同事务提交；发布器只在提交后异步投递；消费者必须按 `eventId` 幂等去重；transport 选择必须形成 ADR。

仓库现状核对：

- `iot-device-biz` 已接入 Kafka：`DeviceAlertKafkaConfiguration` 提供 `deviceAlertKafkaTemplate`，`spring.kafka` 在 application 配置中启用。
- `iot-sink` 已有 `IotMessageBus` 抽象，支持 `local/kafka` 类型，`DeviceEventSubscriber` 等消费者复用该抽象。
- 部署栈包含 Kafka；`iot-device` 生产配置已有 Kafka broker 地址和 transaction-id-prefix。
- M1 已确定消费者：TD-001 collector 配置发布协调器；WEB/APP 不直接消费。

## 目标用户与角色

| 角色 | 职责 | 最小权限边界 |
|---|---|---|
| 生产者 owner（iot-device 电力模型服务） | 维护事件 Envelope、Schema、发布器与 Outbox 回写 | 仅写自身 Outbox 表与既定 topic |
| 消费者 owner（TD-001 collector 配置发布协调器） | Inbox 幂等消费、配置下发、消费失败处置 | 仅消费既定 topic、读写 `power_model_event_inbox` |
| 运维 | 接收积压/DLQ/隔离/对账告警并处置；执行双发对账 | 只读监控与告警平台 |
| DBA | 评审并（经 ADR-013 runner）执行 `power_model_event_inbox` 落库 | `migration_executor` 角色 |
| 安全/审计 | 处置同 ID 异 hash 隔离 critical 事件 | 只读隔离区与审计日志 |

任何角色不得绕过 Outbox 直接发送业务事件，不得由生产者自批自愈隔离事件。

## 候选方案

| 方案 | 优点 | 风险/缺口 |
|---|---|---|
| A. Kafka 事件总线（推荐） | 仓库已有生产者/消费者与部署依赖；支持版本化 topic、重放、分区顺序和消费者组；Outbox 与 Kafka 幂等语义可配合 | 需冻结 topic/quota/acks/重试；consumer Inbox 仍需建表 |
| B. MQTT | 边缘已有 EMQX，但中心业务事件治理、重放和分区顺序能力弱于 Kafka | 需额外引入中心侧消费框架；与仓库现有 Kafka 事件模式不一致 |
| C. 内部 HTTP 轮询 | 无中间件依赖 | 轮询延迟/背压/顺序/重放都要自建；违背“事件总线优先”的宪法 §5.4 语义 |

## 决策（候选）

采用方案 A：Kafka 作为 M1 Outbox transport。本段使用宪法 §1.1 强度语义，未重复英文缩写不改变效力。

**作用域（1.3.8 声明，对齐 [ADR-016](./ADR-016-EDGE退役与RUNTIME边缘执行边界.md)）**：本 ADR 的 transport、消费者 Inbox、Schema 与配置清单**仅覆盖 TD-005 `power-model-release` 事件**（Kafka topic `power-model-release-v1`）。RUNTIME/VIDEO 算法告警经 MQTT 算法总线（`mqtt/iot-*`）接入 iot-sink，按 ADR-016 决策 5 建立独立的告警事件 Schema 与 Inbox 契约——复用本 ADR 的稳定 msgId、内容摘要、先 Inbox 后副作用、重试/DLQ、未知主版本隔离与可观测性原则，但**不纳入本 ADR 的 Kafka inbox**，也不复用 `power_model_event_inbox` 表。下方候选方案 B（MQTT）的否决仅针对 power-model 中心业务事件，不否定告警侧使用 MQTT 接入。

### MUST

- Topic 命名：`power-model-release-v1`；破坏性升级创建 `power-model-release-v2`，不原地改 v1。
- Key：`{tenantId}:{aggregateType}:{aggregateId}`，保证同一聚合事件顺序。
- Producer：acks=all（候选，待压测确认）、有界重试、幂等 producer 开启；数据库事务提交前不发送。
- 消费者组：`iot-device-power-model-release`，由 TD-001 collector 配置发布协调器消费。
- 消费者 Inbox：`power_model_event_inbox` 候选 DDL 见 [consumer_inbox_candidate.sql](../技术设计/电力运维云平台/assets/td005-migration/consumer_inbox_candidate.sql)；`event_id` 全局唯一，同 eventId 同 hash 返回 DUPLICATE，同 ID 异 hash 进入隔离并 critical。
- 未知主版本：写隔离/DLQ 并告警，不标记业务成功、不阻塞其他事件。
- 双版本窗口：V1/V2 双发、逐消费者切换、对账后停 V1，遵循宪法 §5.4。
- 事件 Envelope 以 §事件契约 冻结字段为准；Schema MUST 落入生产者 API 模块（见 §Schema 模块归属），禁止第二份拷贝。
- `power_model_event_inbox` 落库 MUST 经 ADR-013 受控 runner（history + SHA-256 + advisory lock）执行，禁止手工或其他路径执行该 DDL。

### SHOULD / MAY

- SHOULD 在转 Accepted 前完成 50 events/s 容量压测并冻结 acks/retries/分区数。
- MAY 在平台统一事件治理评审通过后调整 topic 命名前缀；届时仍须保留主版本后缀语义。

## 事件契约

公共 Envelope 固定字段（与 4 个 V1 Schema 资产一致，[events/README.md](../技术设计/电力运维云平台/assets/td005-migration/events/README.md)）：

| 字段 | 类型 | 语义 |
|---|---|---|
| `eventId` | string(UUID v4) | 全局唯一事件 ID，与 Outbox `event_id` 一致 |
| `eventType` | string | 事件名，含主版本后缀（如 `POWER_MODEL_TEMPLATE_PUBLISHED_V1`） |
| `schemaVersion` | integer | 主版本号，当前恒为 1 |
| `tenantId` | string | 租户编号；bigint ID 一律十进制字符串 |
| `aggregateType` | string | 聚合类型（如 `power_model_template`） |
| `aggregateId` | string | 聚合 ID，十进制字符串 |
| `occurredAt` | string | 发生时间，UTC ISO 8601 |
| `requestId` | string | 触发请求关联 ID |
| `traceId` | string | 链路追踪 ID（可空串） |
| `data` | object | 事件载荷，`additionalProperties: false` |

首批 4 个事件 V1 Schema：`POWER_MODEL_TEMPLATE_PUBLISHED_V1`、`POWER_MODEL_TEMPLATE_LIFECYCLE_CHANGED_V1`、`POWER_PRODUCT_MODEL_BINDING_APPLIED_V1`、`POWER_PRODUCT_MODEL_BINDING_ROLLED_BACK_V1`。同一主版本只允许 additive 兼容变化；破坏性变化必须创建 `_V2` 独立事件、Schema 与 topic。

## Schema 模块归属与 CI 门禁

- 事件 Schema 与 Envelope 定义 MUST 落入生产者 API 模块：`iot-device-api` 资源目录 `schema/power/model/v1/`，配套 `PowerModelEventEnvelope` 记录类型；消费者从 classpath 加载同一 Schema 资源，禁止在消费者模块维护第二份拷贝。
- CI 门禁 MUST 包含：当前主版本与上一主版本的生产者/消费者合同测试（OUT-005～008）、Schema 兼容检查（Ajv Draft 2020-12 strict 模式，引入 `ajv-formats` 或等价 validator）；未通过不得合并。
- 门禁落地（CI 任务接线）属实现工作，转 Accepted 前至少完成任务定义与样例运行证据。

## 档位行为

- `mini`：不部署消费者、不创建/订阅本 topic；capability manifest 未启用 `power.device.model` 时生产者在守卫处 fail-closed 拒绝发布，不产生 Outbox 待投递残留。验证：mini 启动后无该消费者线程、无该 topic 订阅、无电力菜单/后台任务残留。
- `standard` / `full`：同一 topic、同一 Schema、同一 Envelope、同一消费者契约与同一 Inbox 表；full 仅可通过 capability manifest 提高配额（分区数、吞吐上限），禁止按档位复制 topic、消费者或表实现。
- 档位差异不得削弱幂等、审计、双版本对账和回滚要求。

## 配置清单

新配置经 Nacos / application 配置表达，敏感值不得提供真实默认值；`候选` 值待压测与 DBA 评审冻结。

| 配置 | 用途 | 类型 | 候选默认值 | 必填 | 示例 |
|---|---|---|---:|---|---|
| `spring.kafka.bootstrap-servers` | Broker 地址（既有配置复用） | string | 无（环境注入） | 是 | `kafka:9092` |
| `power.model.events.topic` | Outbox 事件 topic | string | `power-model-release-v1` | 是 | 同左 |
| `power.model.events.consumer-group` | 消费者组 | string | `iot-device-power-model-release` | 是 | 同左 |
| `power.model.events.max-payload-bytes` | 最大消息大小 | integer | `2097152`（2 MiB） | 是 | 同左 |
| `power.model.events.producer.acks` | 生产确认级别 | string | `all` | 是 | `all` |
| `power.model.events.producer.retries` | 生产重试上限 | integer | `5` | 是 | `5` |
| `power.model.events.retry.base-delay` | 消费重试退避基数 | duration | `1s` | 是 | 指数退避 1s→16s |
| `power.model.events.retry.max-attempts` | 消费重试上限 | integer | `5` | 是 | 超限进 DLQ |
| `power.model.events.dlq.topic` | 死信 topic | string | `power-model-release-v1-dlq` | 是 | 同左 |
| `power.model.events.topic.retention` | topic 保留期 | duration | `30d` | 是 | 必须覆盖双发窗口 |
| `power.model.events.topic.partitions` | 分区数 | integer | `6` | 是 | 压测后冻结 |
| `power.model.events.inbox.retention` | Inbox 保留窗口 | duration | `90d` | 是 | 不小于重试/死信重放/双版本窗口 |
| `power.model.events.inbox.cleanup-cron` | Inbox 清理任务 | cron | `0 0 3 * * ?` | 否 | 每日清理超窗 PROCESSED 记录 |
| `power.model.events.relay.poll-interval-ms` | 发布器轮询间隔 | integer | `1000` | 是 | 压测后冻结 |
| `power.model.events.relay.initial-delay-ms` | 发布器首次轮询延迟 | integer | `5000` | 否 | 避让启动期资源就绪 |

## 可观测性与对账

- 指标（MUST 随消费者/发布器实现落地）：`power_model_outbox_backlog`（PENDING 积压 gauge）、`power_model_event_publish_total{result}`、`power_model_event_delivery_duration`（P95 目标 ≤ 5 s）、`power_model_inbox_quarantined_total`、`power_model_dlq_depth`。
- 告警阈值（候选）：积压 > 1,000 持续 10 分钟；发布失败率 > 1% / 5 分钟；隔离或 DLQ 深度 > 0 即 critical。
- 对账：V1/V2 双发窗口内每日对账 Outbox PUBLISHED 与 Inbox PROCESSED 计数及 eventId 差集；对账责任人为运维角色，差异必须清零后方可停 V1。
- 日志结构化携带 `eventId`、`aggregateId`、`tenantId`、`traceId`；不得记录 payload 正文与敏感值，仅记录 `payload_hash`。

## Kafka 安全态势（核实声明）

- 已核实：现有 broker 为内网 PLAINTEXT，无 SASL/TLS/topic 级 ACL（`iot-device-biz` application-prod.yaml `bootstrap-servers: 10.0.0.87:9092`，无 security.protocol 配置）。本 ADR 沿用该态势，不新增暴露面：topic 仅中心服务可达，WEB/APP/边缘不直连。
- 事件 payload 不含凭据、Token 或个人敏感信息；完整性由 `payload_hash`（SHA-256）校验。
- topic 级 ACL 与传输加密列为后续平台级加固项（不阻断 M1，但必须在部署文档中声明当前边界）。

## 验证

- OUT-001～004：并发 claim、发送后回写崩溃、lease 竞争、retryable/final 错误（重试上限 5 次、指数退避 1s→16s、超限进 DLQ）。
- OUT-005～008：V1 additive 字段、当前/上一主版本 fixture、双发对账、未知主版本隔离；进入 CI 门禁（见 §Schema 模块归属与 CI 门禁）。
- OUT-009：连接/读取超时与资源释放。
- 容量候选：50 events/s，单事件投递 P95 ≤ 5 s；10,000 条 backlog 恢复 ≤ 15 分钟；全部待压测冻结。

## 回滚

- 优先停发布器并保留 Outbox；Kafka topic 保留期覆盖双发窗口。
- 消费者切回 V1 或旧 Inbox 逻辑，不删除已处理事件。
- 只有消费者全部退出且旧积压/死信清零后才能停 V1 topic。

## 文档同步

转 Accepted 前 MUST 完成并可评审：

- 事件契约（topic、4 个事件、Envelope 字段、消费者组）同步 `.doc/架构设计/`；
- `power_model_event_inbox` 表说明同步 `.scripts/postgresql/README.md` 与部署文档（含当前 PLAINTEXT 安全边界声明）；
- 新配置项同步 `env.example` / Nacos 说明。

## 评审处置

2026-08-06 宪法专项评审（M-01～M-08 / P-01～P-07）已逐项纳入本版本设计：M-01→§目标用户与角色；M-02→§档位行为；M-03→§事件契约；M-04/M-05→§Schema 模块归属与 CI 门禁；M-06→§配置清单；M-07→§可观测性与对账；M-08→§文档同步；P-02/P-04→配置清单与验证节冻结候选值；P-03→决策 MUST（绑定 ADR-013 runner）；P-05→§Kafka 安全态势；P-06→候选 DDL 已收缩双 UNIQUE 为单一 `UNIQUE(event_id)`（全局唯一已蕴含租户维度唯一，避免冗余唯一索引写放大；租户隔离由 `tenant_id NOT NULL` 与应用查询保证）；P-07→消费契约：单分区内顺序消费、批量上限候选 100 条、手动 offset 在 Inbox 写成功后提交、poison 消息进 DLQ 不跳过。P-01（压测）与消费者实现证据继续 OPEN。

## 开放项

容量压测与 acks/retries/分区数/轮询间隔冻结（维持 owner 豁免口径，候选值不冻结）、collector 配置发布协调器业务处理器接入（处理器注册表 1.3.3 起就位但为空，空表下事件按缺失处理器进 DLQ）、双发对账演练、~~JDBC 真实库合同测试~~（已于 1.3.6 在本地临时评审库执行 11/11 PASS；V001 向目标实例落库仍 MUST 经 ADR-013 runner 获批窗口，属迁移链执行门禁而非测试门禁）、Kafka DLQ topic 深度 broker 侧导出（`power_model_dlq_depth` 当前覆盖 Outbox DEAD_LETTER 行数）仍 OPEN。~~CI 合同门禁任务接线~~已于 1.3.1 落地（`pnpm verify:event-contracts`，含样例运行证据）；~~Outbox/Inbox 持久化接线~~已于 1.3.2 落地；~~发布器调度驱动选型与接线~~已于 1.3.4 落地（Spring Scheduling，owner 部署评审裁定）；`power_model_event_inbox` 落库仍 MUST 经 ADR-013 runner 在获批窗口执行，真实库合同测试沿用 TD005_PG_ENABLED 跳过模式。
