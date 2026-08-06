# TD-005-版本绑定审计 Outbox 迁移回滚设计 · 独立评审报告

> 评审对象：`.doc/技术设计/电力运维云平台/TD-005-版本绑定审计Outbox迁移与回滚设计.md` v0.1.0
> 评审日期：2026-08-06
> 评审基线（强制双基线）：
> - [EasyAIoT 项目开发宪法 1.4.0](./EasyAIoT项目开发宪法.md)
> - [平台功能计划 1.4.0](../架构设计/平台功能计划.md)
> 上游追踪：TD-005 1.0.15、运行模型设计 0.1.8、ADR-009、ADR-011、ADR-012 1.0.2、TD-004 §7.12
> 评审范围：MUST/SHOULD/MAY 条款逐项核对 + 事实核验 + 合同与合同测试闭合性
> 与既有评审关系：本文独立于 TD-005 主评审报告 §13/§14，不替代其结论；本节只覆盖"版本/绑定/审计/Outbox 迁移回滚"新增资产，不重复运行模型/删除链评审（R1～R10）的范畴

---

## 1. 总评

**技术设计质量：高；但在 4 项 MUST 观察未关闭前不得进入 `Approved / Frozen`，不得执行任何 DDL。**

核心亮点：
- **事实原子性清晰**：把"业务事实 + 幂等 + 领域审计 + Outbox"钉在同一本地事务，HTTP 成功 ≠ collector 已应用（§1），符合宪法 §5.4 Kafka 一致性要求；
- **扩展—迁移—收缩**：M0～M5 阶段、additive V001、空表才能 U001、有数据禁止 destructive down（§7/§8），严格符合宪法 §6.3；
- **Outbox 状态机完整**：PENDING → PUBLISHING → PUBLISHED / DEAD_LETTER，claim 短事务 `FOR UPDATE SKIP LOCKED`，租约/迟到 ACK/重复 eventId 均有合同（§6 / §10 OUT-001～004）；
- **双档位统一**：`standard/full` 同表同事件同测试，mini 前置 capability 拒绝（§12  PROF-001/002），符合宪法 §2.3 / §2.4 standard/full 单一实现；
- **OPEN 项诚实列出**：§13 九项门禁全部明确未关闭，不伪装完成。

必须补强：
- `product` 表 `(tenant_id, product_identification)` UNIQUE 约束前置（阻塞 binding FK 启用）；
- `power_idempotency_record` 落库与版本写 API 启用顺序需要显式串行化门禁；
- V001/U001 SQL 资产、payload JSON Schema、transport 方案与消费者 Inbox 去重必须从"OPEN"进入冻结；
- 宪法条款的显式映射需要补全（特别是 §5.4 消费者先行 + §12.1 合同测试）。

---

## 2. 与强制双基线的逐条合规性

### 2.1 宪法 1.4.0 条款映射

| 宪法条款 | 强度 | 本设计落点 | 合规 |
|---|---|---|---|
| §2.2 架构边界（DEVICE 为控制面） | MUST | §12：版本/绑定/审计/Outbox 全部位于 `iot-device` | ✅ |
| §2.3 电力仅 standard/full，mini 不支持 | MUST | §1/§12 PROF-002 mini 前置拒绝 | ✅ |
| §2.4 standard/full 单一实现 | MUST | §12 PROF-001 同 Schema/SQL/事件/错误 | ✅ |
| §2.4 禁止按档位复制 Controller/Service/表 | MUST | §2 范围：同一 `iot-device` 模块 | ✅ |
| §2.5 兼容优先 | MUST | §7 additive / §8.1 应用回滚优先 / §8.2 空表才卸载 | ✅ |
| §2.5 破坏性变更必须提供迁移+兼容期+回滚 | MUST | §7.2 M0～M5 / §8.1/8.2 | ✅ |
| §4.2 设计必备项（业务流程/模块/API/数据/异步/权限/档位/可观测/测试/回滚） | MUST | §4～§11 全部覆盖 | ✅ |
| §5.1 公共 API `/api/v1/{domain}/...` | MUST | 不在本设计范围（TD-005 主 §10 已冻结） | — |
| §5.4 Kafka/MQTT 事件必须稳定名称+版本+事件 ID+occurredAt+来源+关联 ID | MUST | §4.6 envelope 包含 `eventId/eventType/schemaVersion/tenantId/aggregateType/aggregateId/occurredAt/requestId/traceId` | ✅ |
| §5.4 消费者必须幂等 | MUST | §4.6 `eventId` 唯一约束 + §5.3 重复发布幂等 | ✅ |
| **§5.4 事件 Schema 必须进入生产者 API 模块并版本化** | MUST | §4.6 仅放入 `iot-device-api/src/main/resources/events/{event-name}/v1.json`，未冻结生产者/消费者 Schema 位置与版本演进规则 | ⚠️ O-01 |
| **§5.4 破坏性升级：消费者先行、生产者双发、逐消费者切换** | MUST | 设计未明确 Outbox 消费者是谁（NODE？collector？WEB？），也没有消费者升级顺序与双发窗口 | ⚠️ O-02 |
| §5.4 CI 必须对当前和上一主版本运行生产者/消费者合同 | MUST | §10 列出 TX/OUT 合同，但未明确 V1 → V2 跨主版本 Schema 合同测试 | ⚠️ O-03 |
| §6.1 选型 PostgreSQL 为关系业务 | MUST | §4 全部位于 PostgreSQL | ✅ |
| §6.2 多租户必须包含并执行隔离 | MUST | §4 每张新表均有 `tenant_id NOT NULL` + 复合 tenant 唯一/约束 | ✅ |
| §6.2 时间统一明确时区/精度 | MUST | §4.6 `TIMESTAMPTZ` + UTC ISO 8601 | ✅ |
| §6.3 迁移必须可重复执行 | MUST | §7.1 V001/U001 + SHA-256 manifest + §7.3 幂等校验 | ✅（待 SQL 资产落地） |
| §6.3 扩展—迁移—收缩 | MUST | §7.2 M0～M5 | ✅ |
| §8 跨网络超时/重试退避上限 | MUST | §6 指数退避 + full jitter + 12 次上限 | ✅ |
| §8 服务必须提供健康/就绪/关键依赖 | MUST | §11 健康接口区分 `businessWrite/audit/outboxPublisher/transport` | ✅ |
| §9 新功能必须识别资源成本 | MUST | 仅提"standard 最低规格压测"，未给出 Outbox 发布器/claim 轮询/租约恢复/对账任务的预期 CPU/IO | ⚠️ O-04 |
| §10.2 永久产品能力使用 capability manifest | MUST | §12 ADR-011 复用 `power.device.model`，但未引用 ADR-011 manifest Schema/读取时机/降级 | ⚠️ O-05 |
| §11 禁止吞掉异常 | MUST | §5.3 审计/Outbox 失败整体回滚 | ✅ |
| §12.1 领域规则单元测试 + DB/消息集成测试 | MUST | §10 MIG/TX/OUT/AUD/TEN/PROF/LEG 合同 | ✅ |
| §15 完成定义 | MUST | §13 九项 DoD 门禁一一对应 | ✅ |

### 2.2 平台功能计划 1.4.0 映射

| 功能计划条款 | 本设计落点 | 合规 |
|---|---|---|
| §1.1 电力仅 standard/full，mini 不在建设/交付/验收范围 | §12 PROF-001/002 | ✅ |
| §1.2 standard/full 功能矩阵（站点与设备/物模型模板） | 物模型模板版本/绑定/审计位于"站点与设备"共享能力 | ✅ |
| §1.4 standard/full 单一实现 | §2 同一 `iot-device`，PROF-001 同实现 | ✅ |
| §1.4 测试必须证明 `Capabilities(standard) ⊂ Capabilities(full)` | §10 PROF-001/002 合同 | ✅ |
| §2.2 电力设备物模型 P0 | §4～§5 模板/版本/绑定/审计 | ✅ |
| §5 部署适配：mini 不启用电力采集/告警/控制/能源/运维/报表 | §12 PROF-002 | ✅ |
| §7.1 性能：standard 限流/分区/保留期 | §6 PUBLISHED 保留 ≥ 30 天；审计/DEAD_LETTER M1 不自动删除 | ✅ |

---

## 3. 事实核验（代码与资产核对）

| ID | 设计声明 | 核验方法 | 结果 |
|---|---|---|---|
| F-01 | 仓库当前未发现 Flyway/Liquibase | `grep -R flyway|liquibase` 仓库级搜索 | ✅ VERIFIED：仅 `EdgeNodeSchemaInitializer.java` 注释出现"兼容无 Flyway 环境"，无执行器 |
| F-02 | `power_idempotency_record` 复用 TD-004 §7.12 | SQL + Java 仓库搜索 | ✅ VERIFIED：仅设计文档与评审文档引用，代码无建表、无 DO/Mapper；TD-004 §7.12 设计已冻结但未落地 |
| F-03 | `system_operate_log` 位于 `iot-system` | Java 搜索 | ✅ VERIFIED：`OperateLogDO` 在 `iot-system-biz`，确实不能参与 `iot-device` 本地事务 |
| F-04 | `LegacyThingModelPersistenceService` 已证明同租户八表替换与失败回滚 | Java 搜索 | ✅ VERIFIED：`LegacyThingModelPersistenceService.java` 与 `LegacyThingModelRuntimeAdapter.java` 已落主代码；单元测试 9/13/17/19 项 PASS |
| F-05 | `power.device.model` capability 已有合同证据 | TD-005 §24 系列 | ✅ VERIFIED：TEN-007/008 4+1+19 tests PASS |
| F-06 | binding 对 `product` 同租户 FK 前置：`product` 必须有 `(tenant_id, product_identification)` UNIQUE | `.scripts/postgresql/iot-device10.sql` L3507-3579 | ⚠️ **PARTIAL**：`product.product_identification NOT NULL` 已具备，但仓库当前 **未发现** `(tenant_id, product_identification)` UNIQUE 约束；运行模型设计 0.1.8 §6.1 列出目标，但本设计 §7.2 M2 没有显式包含"先补该 UNIQUE"这一前置步骤 |
| F-07 | M1 不新建独立"物模型服务"，复用 `iot-device` 既有模块 | Java 模块搜索 | ✅ VERIFIED：所有新代码均落在 `iot-device-biz` |
| F-08 | V001/U001 SQL 资产尚未生成 | `.doc/技术设计/电力运维云平台/assets/` 目录 | ✅ VERIFIED：目录不存在；§7.1 明确"评审通过后再生成" |

---

## 4. 发现清单

### 4.1 HIGH（MUST 阻塞）

**H-01 · binding FK 前置约束未在 M2 步骤中固化**
- 位置：§4.4 / §7.2 M2 / §13 OPEN #3
- 现象：`product` 表当前缺少 `(tenant_id, product_identification)` UNIQUE 约束（F-06）。§7.2 M2 写"创建 binding 表；依赖 product 同租户唯一键的 FK 可先 `NOT VALID`"，但没有指出 **先补 product UNIQUE 再建 FK**，运行模型设计 0.1.8 §6.1 的"product 目标约束"也没有被拉入 M0～M5 阶段列表。
- 风险：若按 M0～M5 直接执行，M2 的 FK `NOT VALID` 也建不出来（引用端没有唯一键），整个 binding 写路径会被阻塞，且 OPEN #3 没有给出可执行的解锁步骤。
- 建议：在 §7.2 M1→M2 之间显式插入 **M1.5 补齐前置约束步骤**：`(a)` 重跑 12 表画像确认 `product` 表 `product_identification` 分布与重复度；`(b)` 在 staging 添加 `UNIQUE (tenant_id, product_identification)` 并 `VALIDATE`；`(c)` 把该 UNIQUE 进入画像批准基线；`(d)` M2 再建 FK `NOT VALID` → `VALIDATE CONSTRAINT`。同步把运行模型设计 0.1.8 §6.1 的 product 约束目标作为本设计前置依赖写入 §3。

**H-02 · `power_idempotency_record` 落库与版本写 API 启用的串行门禁未闭环**
- 位置：§1 / §3.5 / §13
- 现象：§3.5 说"若该表尚未落库，版本写 API 不得启用"，但 §13 九项冻结门禁中 **没有**"幂等表 DDL 落地并验证"这一项；§7.2 M0～M5 也未包含幂等表的 migration。由于 TD-004 尚未落地该表，本设计又依赖其作为业务事务一员，存在"业务事务依赖一张不存在的表"的断层。
- 风险：评审通过后若直接按 M0～M5 执行，M1 创建的新表无法与"不存在"的幂等表原子提交；要么回退修改事务边界，要么临时绕过幂等——两者都违反设计本意。
- 建议：
  - 在 §13 冻结门禁增加第 10 项："`power_idempotency_record` DDL 已落地，跨副本唯一约束、24 小时保留与清理扫描任务通过合同测试（按 TD-004 §7.12）"；
  - 在 §7.2 增加 **M0.5 幂等表前置 migration**，由 TD-004 提供 SQL 资产，本设计在 M1 之前验证其存在；
  - 或在 TD-004 落地前，把本设计 §5 的"业务事务"拆分"业务事务 + 异步幂等登记"两阶段，并明确后者失败不影响业务事实——但此方案违反 ADR-012 的"同事务"原则，不推荐。

### 4.2 MEDIUM（SHOULD 补强）

**M-01 · V001/U001 SQL 候选缺少可评审的 DDL 文本**
- 位置：§4 / §7.1
- 现象：§4 给出表/列/约束的文字候选，但**未提供** `CREATE TABLE` 脚本、约束命名规则、trigger 函数体和索引 DDL。§7.1 说"评审通过后再生成"，这与 §13 冻结门禁 #2 "由 DBA/代码 owner 核对全部列类型、稳定约束名、索引、trigger"形成循环：DBA 无法在评审时核对尚未存在的 DDL。
- 建议：在评审前提供"候选 DDL 骨架"（可以是伪 SQL 或草案），作为评审附件；SQL 资产在评审通过后走 SHA-256 manifest 入库，但 **骨架必须先过 DBA 评审**。

**M-02 · Outbox transport/消费者/Inbox 去重未冻结**
- 位置：§2 / §4.6 / §13 OPEN #5
- 现象：§2 明确"M1 不在数据库事务内调用 NODE、collector、Kafka、MQTT 或其他远程服务"，这是正确的；但 §13 OPEN #5 要求"明确 Outbox transport、消费者 Inbox 与 `eventId` 去重事实"。目前设计只冻结了领域事件与 payload envelope，**没有明确 transport 选型（Kafka topic? MQTT topic? HTTP?）、消费者清单、Inbox 落库 Schema、去重窗口**。
- 风险：transport 不冻结 → payload JSON Schema 无法最终冻结（不同 transport 对消息大小/key 有不同约束）→ §13 #4 "冻结事件 payload JSON Schema"也无法关闭。
- 建议：在 §4.6 增加"transport 选型候选与决策门禁"小节，列出候选（Kafka 优先、MQTT 次之、HTTP 兜底）及决策依据；并明确消费者 Inbox 表候选 Schema（`event_id / received_at / payload_hash / status`）作为续作入口。

**M-03 · payload Schema 存放位置与版本演进规则未闭环**
- 位置：§4.6 / §13 OPEN #4
- 现象：§4.6 说"在 `iot-device-api/src/main/resources/events/{event-name}/v1.json` 保存 Draft 2020-12 Schema"，但**未说明**：
  - Schema 文件进入哪个 Maven 模块的 classpath（`iot-device-api` 还是独立的 `events` 模块）；
  - 破坏性升级时如何命名 `v2.json`、旧 Schema 保留多久、`eventType` 如何与 `$id` 联动；
  - CI 如何执行 Schema 兼容检查（宪法 §5.4 要求）。
- 建议：在 §4.6 追加 Schema 生命周期小节，引用宪法 §5.4 与 TD-005 主 §19.1 兼容窗口。

**M-04 · lease_owner 长度在 Kubernetes 环境可能不足**
- 位置：§4.6
- 现象：`lease_owner VARCHAR(128)` 保存发布器实例标识。Kubernetes pod 名格式 `<deployment>-<replicaset>-<pod-hash>` 典型长度 40～63 字符，128 足够；但若加上 hostname + 进程 ID + 线程 ID 等复合标识，或未来迁移到更长 DNS 名（RFC 1123 上限 253），可能越界。
- 建议：把 `lease_owner` 上限提升到 `VARCHAR(255)` 或显式限制格式为"DNS-label 兼容字符串，≤ 63 字符"，并在发布器启动时校验。

**M-05 · 持锁阶段"最终复核"的具体动作未定义**
- 位置：§5.1
- 现象：§5.1 说"持锁后必须重新验证草稿 revision、目标 version、content hash 和 capability"，但**没有说明**复核是 SQL 查询、应用层比对还是触发器；复核失败返回什么错误码；复核与第一次校验之间的"时间窗语义"（防止其他副本在两次校验之间修改）。
- 建议：在 §5.1 增加"最终复核清单"表格：列、校验动作、失败错误码、与持锁前校验的差异；并明确"持锁后读取的权威源必须是数据库行（`FOR UPDATE` 锁定行）"，不得使用 cache。

**M-06 · binding snapshot 的"canonical 唯一可写事实"缺少数据库级兜底**
- 位置：§4.4 / §4.6
- 现象：§4 反复强调 `content_canonical` / `binding_snapshot_canonical` 是唯一可写事实，jsonb 只是查询投影；但 §4.4 没有给出 trigger 或 CHECK 来强制这一规则（对比 §4.2 版本表明确有"发布 trigger 禁止修改内容列"）。
- 风险：若应用层出现 bug，可能直接 UPDATE `binding_snapshot_json` 而不重新生成 canonical，造成两列不一致。
- 建议：对 `power_product_model_binding` 增加 trigger：UPDATE 时禁止修改 `binding_snapshot_canonical`；`binding_snapshot_json` 必须由 canonical 派生（与版本表 trigger 对称）。

**M-07 · 现有 product 表当前约束基线未在 §3 列出**
- 位置：§3 / §4.4
- 现象：设计大量引用"product 同租户唯一键"作为 binding FK 的引用端，但 §3 没有给出当前 `product` 表的约束清单（有无 UNIQUE、有无 FK 被依赖、`product_identification` 列长 100 还是 255）。运行模型设计 0.1.8 §6.1 给出"目标约束"，但本设计需要"当前约束"作为迁移基线。
- 建议：在 §3 增加"product 表当前约束快照"小节，至少列出：PK、现有 UNIQUE、NOT NULL、FK 被依赖清单，并注明"来源 `iot-device10.sql` dump / 生产画像"。

### 4.3 LOW（MAY/改进建议）

**L-01 · advisory lock 应用层重试与退避策略未定义**
- 位置：§5.1
- 现象：使用"template advisory lock"串行化发布，但应用层获取失败时的重试次数/退避/超时没有说明。
- 建议：补"advisory lock 等待超时 + 客户端重试上限"，并与 `lock_timeout=15s` 联动。

**L-02 · 错误码命名规范未统一**
- 位置：§5 / §10
- 现象：引用 `MODEL_TEMPLATE_PUBLISH_LOCK_TIMEOUT`、`CAPABILITY_NOT_SUPPORTED` 等，但没有统一的命名空间前缀规范。
- 建议：定义 `{DOMAIN}_{OPERATION}_{REASON}` 命名规范，避免后续新增事件/错误时漂移。

**L-03 · 审计与 Outbox M1 不自动删除缺少容量安全阀**
- 位置：§6
- 现象："M1 不自动删除"，但未规定容量阈值或告警线。
- 建议：增加"超过 X GB 或 Y 百万行必须触发告警并进入保留策略评审"，避免压测前就把数据库撑爆。

**L-04 · SYSTEM 模板生命周期与租户只读范围未展开**
- 位置：§9
- 现象："SYSTEM 模板只允许 `power:system-template:manage`，普通租户只读"，但未说明：
  - SYSTEM 模板被 DEPRECATED/RETIRED 时，对已绑定该模板的产品的通知与迁移路径；
  - "只读"是否包含"导出 canonical/snapshot"，是否返回完整正文（与 §9 "审计普通查询默认不返回 `diff_summary` 正文"对称）。
- 建议：在 §9 增加 SYSTEM 模板生命周期与租户只读权限小节。

---

## 5. DoD（Definition of Done）核对（宪法 §15）

| DoD 项 | 本设计状态 |
|---|---|
| 目标用户、业务价值、范围与验收标准 | ✅ §1/§2 明确 |
| 模块边界、接口、数据、异步流程设计 | ✅ §4～§6 |
| 权限、租户、输入校验、敏感数据 | ✅ §9 |
| 正常/异常/边界/并发/依赖故障 | ✅ §5.3 / §10 |
| 适用档位明确；不适用档位已验证关闭 | ✅ §12 PROF-001/002 |
| standard/full 同一实现 | ✅ §12 PROF-001 |
| 数据库、配置、依赖、部署变更具备升级与回滚说明 | ✅ §7/§8 |
| 测试、编译、类型检查、构建 | ⚠️ §10 合同齐全，**待实现** |
| 日志、指标、健康检查、故障定位 | ✅ §11 |
| 公共契约与文档同步 | ⚠️ 待 V001/U001/payload Schema 落地 |
| Diff 无凭据/调试代码 | ✅ 纯设计文档 |
| 交付说明列出验证结果、限制与风险 | ✅ §3 / §13 |

---

## 6. 与既有评审协同

| 既有评审 | 本设计处理 |
|---|---|
| TD-005 主评审 H-01（幂等） | ✅ §3.5 / §5 完整复用 TD-004 §7.12 |
| TD-005 运行模型评审 R1～R10 | ✅ 本设计 §2 明确"运行模型八表由运行模型设计管理"，不重复评审 |
| TD-005 孤儿属性处置方案 O1～O7 | ✅ 本设计 §4.4 的 binding + §8.1 应用回滚覆盖"回滚创建更高 revision，不修改历史 binding" |
| ADR-012 单一事实 | ✅ §12 明确"不新增/回填 `product_properties.service_id`" |
| ADR-011 capability | ✅ §12 复用 `power.device.model` |
| ADR-009 不可变版本 | ✅ §4.2 trigger + §5.1 事务 |
| TD-004 §7.12 幂等 | ✅ 直接复用 |

---

## 7. 评审结论

| 类别 | 数量 |
|---|---|
| HIGH（MUST 阻塞） | 2 |
| MEDIUM（SHOULD 补强） | 7 |
| LOW（MAY 改进） | 4 |

**总体判定：`In Review / Migration Candidate` 维持，不得进入 `Approved / Frozen`。**

进入 `Approved / Frozen` 的最小闭环：
1. 关闭 H-01：在 §7.2 插入 M1.5 补齐 product 前置 UNIQUE，运行模型设计 0.1.8 §6.1 作为前置依赖写入 §3；
2. 关闭 H-02：在 §13 冻结门禁增加"`power_idempotency_record` 落库"项，或明确本设计事务边界的降级方案；
3. 关闭 M-01：提供 V001/U001 DDL 骨架作为评审附件；
4. 关闭 M-02/M-03：冻结 Outbox transport 选型、消费者清单与 payload Schema 版本化规则。

关闭上述 4 项后，可进入 `Approved / Frozen`，开始生成 SHA-256 manifest 的 SQL 资产与自动化合同。

---

## 8. 与双基线合规性的独立声明

- 本报告按用户固化偏好，以《EasyAIoT 项目开发宪法 1.4.0》和《平台功能计划 1.4.0》为强制双基线；
- 第 2 节已按宪法 §1.1 MUST/SHOULD/MAY 强度逐项核对，MUST 违反项列入 HIGH（H-01/H-02），SHOULD 观察项列入 MEDIUM（M-01～M-07），MAY 建议列入 LOW（L-01～L-04）；
- 本报告独立于 TD-005 主评审报告 §13/§14、运行模型评审 R1～R10、ADR-012 评审报告；
- 本节评审不替代后续 DBA/代码 owner 对真实 DDL 的核对，也不替代压测后的容量参数冻结。
