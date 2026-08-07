# ADR-013 / ADR-014 与 TD-005 migration 资产 DBA/架构专项评审报告

> 评审对象：[ADR-013 受控数据库迁移执行器 1.3.1（Proposed）](../架构决策/电力运维云平台/ADR-013-受控数据库迁移执行器.md)、[ADR-014 Outbox 事件 Transport 与消费者 Inbox 1.1.0（Proposed）](../架构决策/电力运维云平台/ADR-014-Outbox事件Transport与消费者Inbox.md)、[TD-005 版本/绑定/审计/Outbox 迁移与回滚设计 0.1.7](../技术设计/电力运维云平台/TD-005-版本绑定审计Outbox迁移与回滚设计.md)
> 评审资产：`assets/td005-migration/`（V001/U001、`power_idempotency_record_candidate.sql`、`consumer_inbox_candidate.sql`、4 份事件 V1 Schema/fixture）、`.scripts/postgresql/td005-migration/`（runner Spike、M15 步骤、precheck、check_ddl_comments）
> 基线文档：[平台功能计划 1.4.0](../架构设计/平台功能计划.md)、[EasyAIoT 项目开发宪法 1.5.0](./EasyAIoT项目开发宪法.md)
> 评审日期：2026-08-07
> 评审视角：DBA（DDL 正确性/约束/索引/权限/锁与执行顺序）· 架构（单一事实/阶段边界/契约一致性）· 宪法 MUST/SHOULD 逐项核对 · 候选资产与 Spike 证据真实性
> 评审性质：静态评审 + 资产交叉核对；未在任何数据库执行 DDL，未复跑 Spike
> 评审结论：**需要修改——有条件继续 Proposed**（4 HIGH / 10 MEDIUM / 5 LOW）；HIGH 未关闭前 ADR-013/014 不得转 Accepted，不得在任何共享/生产库执行 DDL

---

## 1. 评审范围

| 维度 | 说明 |
|---|---|
| DDL 正确性 | V001 七对象（六表 + history）列型、约束、索引、trigger、FK 拓扑在 PostgreSQL 18.4 的语义有效性 |
| 阶段与执行器一致性 | V001 头注三步拆分声明、设计 §7.2 M0～M5 阶段、runner 实际执行顺序三方对齐 |
| ADR 契约与实现对账 | ADR-013 CLI/配置/超时/备份 MUST 与 `td005_migration.sh` Spike 逐项对账 |
| 宪法合规 | §1.1 强度语义、§5.4 事件、§6.2 数据模型（含中文注释 MUST）、§6.3 迁移、§8 超时重试、§10.2 配置、§12.2 最低门禁、§14 文档同步 |
| 事件契约 | ADR-014 Envelope 与 4 份 V1 Schema/fixture 一致性、CI 门禁缺口 |
| 证据真实性 | ADR 声明的 MIG-001～009 PASS/PARTIAL 与 Spike 脚本逻辑是否足以支撑该结论 |

## 2. 总体评价

**设计质量：高。** ADR-013 的"不引入 Flyway/Liquibase、保持 `*10.sql` 唯一安装基线、schema-sync 只做漂移校验"的选型论证基于真实仓库核对，方案 D 的 history + SHA-256 + advisory lock + 事务/非事务步骤分离是仓库现状下的合理最小解。V001 的 DDL 工程质量明显高于仓库历史水平：全链路复合 `(tenant_id, …)` 外键、审计追加写 trigger、`NOT VALID → VALIDATE CONSTRAINT` 分段验证、单一 ACTIVE 部分唯一索引、绑定快照不可变 trigger、U001 失败关闭的空表断言，均与 ADR-009/ADR-012 的不可变与单一事实决策一致。幂等表候选的 8 项 CHECK（hash 长度、状态-响应一致、payload 有界、过期排序）是本轮资产中最完整的防御性设计。

**主要风险集中在执行器实现层，而非 DDL 层**：runner Spike 目前只实现了 ADR-013 决策 MUST 的一个子集——锁等待、语句超时、重试退避、强制备份、全量前置校验四项关键安全语义缺失或与 ADR 冲突；V001 文件自述的三步拆分与 runner 的单事务整文件执行相互矛盾。这些问题不改变选型方向，但按宪法 §15 证据诚实性，ADR 声明的 Spike 证据不能覆盖其 MUST 清单，必须在转 Accepted 前补齐或修订契约。

**事件契约侧（ADR-014）设计闭环良好**：Envelope 十字段与 4 份 V1 Schema 抽查一致（`additionalProperties: false`、`schemaVersion const: 1`、eventType 常量与文件名 kebab-case 对应）；双 UNIQUE 收缩、Inbox 落库绑定 ADR-013、PLAINTEXT 安全态势声明均为诚实披露。剩余缺口（strict 模式校验、CI 接线、压测）均已在 ADR 开放项中声明，本评审无新增事实性异议。

## 3. 双基线一致性核对

| 基线条款 | 落点 | 结论 |
|---|---|---|
| 宪法 §6.2：中文注释 MUST | V001 六表 + history 全量中文注释；`check_ddl_comments.sql` 门禁存在 | ⚠️ 门禁清单缺 2 表，见 M-05 |
| 宪法 §6.2：租户隔离不依赖调用方 | 全部业务表 `tenant_id NOT NULL` + 复合 FK | ✅ |
| 宪法 §6.2：TIMESTAMPTZ/UTC | 全部时间列 TIMESTAMPTZ；envelope 声明 UTC ISO 8601 | ✅ |
| 宪法 §6.3：可重复执行迁移 | history + hash + 幂等跳过 | ⚠️ 见 H-03、M-06 |
| 宪法 §6.3：扩展—迁移—收缩 | M0～M5 additive，U001 仅空表卸载 | ✅ |
| 宪法 §6.3：大表锁/耗时/磁盘/回滚评估 | §7.3 + ADR-013 资源影响段 | ✅（数值仍候选） |
| 宪法 §6.3：删除前备份/保留期/审批 | U001 前置断言 + 审批单 | ⚠️ runner 备份可选，见 H-02 |
| 宪法 §5.4：事件稳定名/版本/ID/时间/来源 | Envelope 十字段冻结 | ✅ |
| 宪法 §5.4：消费者幂等 | Inbox `UNIQUE(event_id)` + 同 ID 异 hash 隔离 | ✅ |
| 宪法 §5.4：Schema 入生产者 API 模块 + CI 双版本合同 | ADR-014 已声明路径与门禁 | ⏳ OPEN（未接线，已诚实声明） |
| 宪法 §8：跨网络调用超时、有界重试 | ADR-013 配置清单冻结候选值 | ⚠️ runner 未实现，见 H-01 |
| 宪法 §10.2：配置清单五要素 | 两 ADR 配置表均含类型/默认/必填/示例 | ✅ |
| 宪法 §12.2：DDL 注释进评审 diff 门禁 | check-comments 模式 | ⚠️ 同 M-05 |
| 平台功能计划 §1.1：mini 不建电力数据 | ADR-014 档位行为 fail-closed | ✅ |
| 平台功能计划 §1.2：standard/full 单实现 | 同表同事件同契约，仅配额差异 | ✅ |

## 4. 资产真实性核对

| ADR/设计声明 | 本评审核对 | 结论 |
|---|---|---|
| V001 六表 + history 结构、约束、trigger | 逐对象审读，FK 引用的唯一键全部存在（`uq_...(tenant_id,id)` 系列、audit `(tenant_id,audit_event_id)`） | ✅ 拓扑自洽 |
| V001 不含 CONCURRENTLY 语句 | M1.5 在 V001 中仅为注释，实体在 `steps/M15__…sql` | ✅ runner 单事务包裹技术上安全 |
| MIG-009 门禁覆盖"7 张表/126 字段" | `check_ddl_comments.sql` 清单恰为 history + 六表 | ✅ 但不覆盖幂等表/Inbox（M-05） |
| MIG-002 "篡改 V001 触发 HASH_MISMATCH 且零变更" | runner 的 V001 hash 校验位于 M15 执行之后；该 PASS 只在 M15 已有 SUCCEEDED 历史或 M15 先失败的前提下成立 | ⚠️ 证据成立有条件，见 H-03 |
| MIG-008 "连接约 2.5s 快速失败" | 仅 `PGCONNECT_TIMEOUT` 生效；会话级 lock/statement 超时未设置 | ⚠️ 部分支撑，见 H-01 |
| 幂等表 8 项反例烟测 | DDL 含对应 8 项 CHECK，反例与 CHECK 一一对应 | ✅ 设计可支撑声明 |
| 事件 Schema 与 Envelope 一致 | 抽查 published-v1：十字段齐全、`additionalProperties:false`、`schemaVersion const:1`、eventType 常量匹配 | ✅ |
| fixture 校验"strict:false PASS" | events/README 自述缺 `ajv-formats` | ⚠️ 已声明 OPEN，见 M-10 |

## 5. 问题清单

### HIGH（必须关闭才能转 Accepted）

**H-01 · runner 未实现 ADR-013 的锁等待、语句超时与重试 MUST**

位置：`td005_migration.sh` `emit_apply_driver` / ADR-013 §配置清单。
问题：(1) 驱动器直接 `SELECT pg_advisory_lock(:lock_key)`，会话未 `SET lock_timeout`，锁等待无上限——ADR-013 明确要求"锁获取失败按重试与超时处理，不得无限等待"且 `lock-wait=15s` 为必填候选；(2) `statement-timeout=15s`、`read-timeout=5s` 未在会话设置，长 DDL 无兜底；(3) `retry-max=3` + 指数退避在脚本中完全不存在。MIG-008 的 PASS 仅覆盖建连超时（`PGCONNECT_TIMEOUT`），不能覆盖锁等待与语句执行两类更常见故障。
风险：生产执行时遇锁冲突无限挂起，违背宪法 §8"外部依赖故障不得造成无限等待"；运维只能手工杀会话，现场不可复现。
**建议**：驱动器首行注入 `SET lock_timeout='15s'; SET statement_timeout 按步骤类型区分（CONCURRENTLY 步骤独立放宽）`；`pg_advisory_lock` 失败映射 `MIGRATION_LOCK_BUSY`；按 ADR 配置实现有界退避重试（仅锁忙/暂时错误）；补对应临时库证据。

**H-02 · `--apply` 前备份 MUST 被实现为可选**

位置：`td005_migration.sh` `backup_before_apply`：`[ -n "${BACKUP_DIR:-}" ] || return 0`。
问题：ADR-013 决策 MUST 为"`--apply` 前调用现有 `pg_dump` 备份流程"；宪法 §6.3 要求删除前必须有备份。当前实现中 `BACKUP_DIR` 未设置时静默跳过备份继续 apply。
风险：操作者漏配环境变量即无备份执行 DDL；出问题时无恢复点，且历史 `evidence` 中无备份记录可查。
**建议**：apply/uninstall 模式下 `BACKUP_DIR` 缺失即 `fail_validation`（或提供 `--no-backup` 显式豁免并要求审批单中声明）；备份文件路径与大小写入 `evidence`。

**H-03 · hash/precheck 校验与 DDL 执行交错，违反"校验失败零变更"**

位置：`emit_apply_driver` 顺序：M15 hash 校验 → INVALID index 检查 → **执行 M15** → V001 hash 校验 → 执行 V001。
问题：MIG-002 与设计 §7.4 要求"同名异构/篡改必须失败且零 DDL 变化"。当前实现里若 V001 被篡改（HASH_MISMATCH），M15 的唯一索引可能已经创建——先执行后校验。ADR-013 声明的 MIG-002 PASS 只在"M15 已有 SUCCEEDED 历史"或"M15 先行失败"的前置下成立，不能推广到任意篡改场景。
风险：被篡改或版本错乱的资产在一次运行中造成部分迁移状态；与 history 表"SUCCEEDED"事实叠加后难以归因。
**建议**：改为两阶段——阶段一对全部步骤资产做 hash/precheck/INVALID/签名核对（含 M15 索引定义比对，见 M-06），全部通过后才进入阶段二逐步执行；或在 ADR 中显式修订 MIG-002 的通过条件定义。

**H-04 · V001 三步拆分声明与 runner 单事务整文件执行相互矛盾**

位置：V001 头注"本文件按评审可读方式把三步放在一起，实际 runner 必须拆分执行"；设计 §7.2 把 M1（建表）/M1.5（并发索引）/M2（binding + FK 验证）列为独立阶段；runner 实际把 M15 单独执行后，将整个 V001（含 M1 六表与 M2 binding 表、NOT VALID + VALIDATE）包进单个 `BEGIN…COMMIT`。
问题：两份受控资产对同一执行边界给出两种事实。单事务合并执行技术上安全（新表为空、VALIDATE 瞬时），但 §7.2 为 M1/M2 设计的独立失败域（M1 失败整事务回滚、M2 FK 未验证不启用写路径）在 runner 中不存在；评审无法同时批准两个互相矛盾的契约。
风险：DBA 按 V001 头注理解执行边界，runner 按另一套执行；故障时回滚语义与审批单描述不符。
**建议**：二选一——(A) 拆分 V001 为 V001（M1 六表）/V002（M2 binding）两个迁移 ID，runner 分步执行并分别写 history；(B) 修订 V001 头注与 §7.2，明确"M1+M2 在空表前提下合并为单事务阶段"的决策及理由。推荐 A，保留阶段证据粒度。

### MEDIUM（转 Accepted 前应关闭）

**M-01 · history 表索引缺口与 DDL 双源**

ADR-013 要求 history 索引至少含 `UNIQUE(migration_id)` 与 `(status, started_at DESC)`；V001 只有前者。且 history 建表 DDL 同时存在于 V001（STEP M1/0）与 runner `history_bootstrap()`，两处文本当前一致但无单源保证。
**建议**：补 `(status, started_at DESC)` 索引；明确 history DDL 唯一事实源（建议 runner 引导独占，V001 删除 STEP M1/0），消除漂移面。

**M-02 · 版本不可变 trigger 未保护身份与发布事实列**

`fn_power_model_template_version_immutable` 只保护 content/version/base/source 列；已发布版本的 `tenant_id`、`template_id`、`id`、`published_by/published_at` 仍可 UPDATE。已发布版本可被改挂到另一模板（复合 FK 仍满足），直接破坏 ADR-009 的发布不可变事实。
**建议**：对 `OLD.lifecycle <> 'DRAFT'` 的行追加 `tenant_id/template_id/id/published_by/published_at` 的 `IS DISTINCT FROM` 拒改；DRAFT 行也至少保护 `tenant_id/template_id`。

**M-03 · 生命周期 trigger 允许 DRAFT 直接跳转 DEPRECATED/RETIRED**

当前 trigger 仅约束 PUBLISHED→{DEPRECATED,RETIRED}、DEPRECATED→RETIRED、RETIRED 终态；`DRAFT → DEPRECATED/RETIRED` 未被拒绝，且会被 `ck_..._published` 强制要求 `published_by/published_at`，产生"从未发布却有发布人"的畸形行。设计语义（评审 M-04 处置）是草稿废弃走 `draft_state=ABANDONED`，不走生命周期。
**建议**：trigger 增加 `OLD.lifecycle='DRAFT' AND NEW.lifecycle NOT IN ('DRAFT','PUBLISHED')` 拒绝；补对应反例合同。

**M-04 · Outbox `payload` 与审计 `diff_summary` 的"有界"无 DDL 防线**

设计 §4.5/§4.6 声明有界，幂等表已有 `octet_length ≤ 16384` 先例；outbox/audit 无对应 CHECK。
**建议**：按 `max-payload-bytes`（2MiB）与 diff 摘要上限补 CHECK，应用层超限时业务事务内拒绝的语义不变。

**M-05 · 注释门禁清单未覆盖幂等表与 Inbox 表**

`check_ddl_comments.sql` 仅含 history + 六表；`power_idempotency_record` 候选头注已自述"落库评审时须追加到注释门禁清单"，`power_model_event_inbox` 同样未纳入。两表一旦落库将处于宪法 §6.2/§12.2 门禁盲区。
**建议**：本轮即将两表追加进清单（候选 DDL 注释已齐全，追加即可生效）；后续新表以"进清单"为落库前置。

**M-06 · M15 步骤无索引定义签名核对**

driver 以 `to_regclass('public.uq_product_tenant_identification') IS NULL` 判定跳过；同名但定义不同（列序/谓词漂移）的索引会被误判为已执行并记录 SUCCEEDED。
**建议**：比对 `pg_get_indexdef` 规范化摘要（列、谓词、唯一性），不一致按 HASH_MISMATCH 同级阻断。

**M-07 · ADR-013 CLI 契约与 runner 实现不符**

ADR 固定 CLI 含 `--step M1|M1.5|M2`、`--allow-destructive`、`--dry-run`；runner 仅有 `dry-run|apply|uninstall|check-comments` 子命令与 `--db/--approval/--yes/--skip-precheck`，不支持按步骤执行，也无破坏性豁免开关。
**建议**：补齐 `--step` 与 `--allow-destructive`（仅 uninstall 空表场景），或修订 ADR CLI 表使其与实现对齐；两者必须一致才能冻结。

**M-08 · 执行证据链缺口：FAILED 不落历史、起止时间同值**

runner 只在成功时插 SUCCEEDED 行；失败/崩溃后 history 无任何记录（状态机中的 FAILED 永不出现），且 `started_at/finished_at` 同为 `now()`，无真实耗时。ADR 日志契约要求记录 `duration_ms`。
**建议**：失败时以独立事务补记 FAILED（含 error_code 摘要）；开始/结束分别取时；或在 ADR 中说明"FAILED 不落库、以日志与重跑判定为准"的替代语义。

**M-09 · 权限模型只有符号性 REVOKE，缺角色 GRANT 资产**

V001 仅 `REVOKE UPDATE, DELETE ON power_model_audit FROM PUBLIC`（PostgreSQL 默认 PUBLIC 本就无表权限，此句无实际效力）；设计 §9 的业务写/Outbox 发布/只读运维/`migration_executor` 四角色分离无任何 GRANT 候选资产。
**建议**：形成 `roles_candidate.sql`（角色、最小授权、审计表 INSERT/SELECT-only、Outbox 状态列更新边界），作为 DBA 核对附件。

**M-10 · 事件 fixture 校验未达 ADR-014 要求的 strict 模式**

events/README 自述因缺 `ajv-formats` 使用 `strict:false`；ADR-014 CI 门禁要求 Ajv Draft 2020-12 strict。
**建议**：CI 接线前至少在仓库可复跑环境中引入 `ajv-formats` 复跑 strict 校验并留证据；本项与 ADR-014 开放项一致，不新开门禁但需跟踪。

### LOW（建议修复，可推迟到冻结前）

**L-01 · BIGSERIAL 与仓库统一 ID 策略的关系未说明**

骨架全部使用 BIGSERIAL（数据库序列兜底），仓库业务表惯例为应用侧赋值。两者可共存，但评审冻结时应显式声明取舍，避免双 ID 来源误用。

**L-02 · 枚举列 CHECK 不完整**

`draft_state`（设计冻结 ACTIVE/ABANDONED）、`member_type`（property/event/service）无 CHECK；audit `principal_type` 在幂等表有 CHECK、审计表没有。建议补齐同级防御。

**L-03 · `version` 与 major/minor/patch 一致性无 DB 约束**

可由 CHECK 表达 `version = major.minor.patch[-prerelease]` 的格式不变式；当前纯应用层保证。

**L-04 · SKIPPED 状态语义未闭环**

ADR 状态机与 history CHECK 含 SKIPPED，但 runner 跳过时不写任何行。建议文档说明 SKIPPED 仅为返回语义不落库，或落 SKIPPED 行。

**L-05 · 基线引用过期**

V001/U001 头注仍写"开发宪法 1.4.0"（中文注释 MUST 为 1.5.0 新增）；events/README 引用 migration 0.1.2（当前 0.1.7）；runner `LOCK_KEY` 默认 913005 与 ADR"TD005_MIG_LOCK 稳定哈希"的推导关系未注释。冻结前统一。

## 6. 对冻结门禁的影响

对照 migration 设计 §13 十项门禁：

| 门禁 | 本评审影响 |
|---|---|
| ① ADR-013 runner 合同证据与演练 | H-01/H-02/H-03 未关闭前，MIG 证据链不成立 |
| ② DBA/代码 owner 核对列型/约束名/索引/trigger/权限 | 本报告即该核对的首轮；M-01～M-04、M-09 为必须处置项 |
| ③ 幂等表落库与合同 | DDL 候选质量可批准；须先完成 M-05（进注释门禁）并随 runner 修复后落库 |
| ④ product unique/binding FK | M-06 签名核对补齐后可排期 |
| ⑤ 事件 Schema 冻结 | 设计层面可批准；M-10 strict 复跑为冻结前置 |
| ⑥ ADR-014 关闭 | 无新增 HIGH；开放项（压测/CI/消费者实现）维持 OPEN 即可进入下一阶段 |
| ⑦ 配置/压测 | 不受影响，继续 OPEN |
| ⑧ MIG/TX/OUT 等自动合同 | 依赖 H-01～H-04 关闭后重跑 |
| ⑨ 备份/恢复/回滚演练 | 依赖 H-02 关闭 |
| ⑩ 结论追加与文档同步 | 本报告完成后履行 |

## 7. 结论

ADR-013 的选型方向（受控步骤执行器、单事实基线、事务/非事务分离）与 ADR-014 的 Kafka transport + Inbox 幂等设计**在架构层面可以批准方向**；V001/U001、幂等表、Inbox、事件 Schema 五类候选资产的 DDL 工程质量达到评审冻结的候选水准。

但执行器 Spike 与 ADR 契约之间存在 4 项 HIGH 级落差（超时/锁等待/重试缺失、备份可选化、校验与执行交错、三步拆分矛盾），这些直接关系到"失败时零变更、可恢复、可审计"这一 runner 存在的核心理由。按宪法 §15 证据诚实性与 §12.3 合并阻断条件，本评审结论为：

**需要修改——ADR-013/ADR-014 维持 Proposed，不得转 Accepted，不得在任何共享/生产库执行 DDL。**

建议处置顺序：

1. 关闭 H-01～H-04（runner 修复或契约修订，重跑受影响 MIG 合同）；
2. 关闭 M-01～M-10（多为 DDL/清单/文档级修改，可与 HIGH 同批）；
3. 补备份/恢复/回滚演练与 strict 模式事件校验证据；
4. 完成上述后由 DBA/代码 owner 复核签字，ADR-013/ADR-014 方可转 Accepted，进入压测与落库排期。

共发现 **HIGH 4 项、MEDIUM 10 项、LOW 5 项**。本报告未执行任何 DDL，未触碰共享或生产数据库；所有结论基于受控资产静态审读与交叉核对，可由仓库文件直接复核。

---

## 8. 处置与复核（2026-08-07）

### 8.1 处置结论

全部 19 项意见当日完成处置：**采纳 18 项、部分采纳 1 项（M-10）**。ADR-013 更新至 1.4.0、ADR-014 更新至 1.2.0、migration 设计更新至 0.1.8。处置后在本地临时评审库（PostgreSQL 18.4，含最小 product 表）重跑合同证据，临时库已清理；iot-device20 目标库未做任何变更（复核 product=4 与画像基线一致）。

| 编号 | 处置 | 落点与证据 |
|---|---|---|
| H-01 | 采纳 | runner 注入 `lock_timeout`/`statement_timeout`（M15 独立 30min），锁忙映射 `MIGRATION_LOCK_BUSY` 并按 3/1s/4s 有界退避重试；临时库实测：持锁竞争下 14s 内有界失败退出码 1 |
| H-02 | 采纳 | `BACKUP_DIR` 缺失即退出码 2 拒绝；备份文件写入 history evidence；实测未设备份/审批均拒绝 |
| H-03 | 采纳 | runner 重构为两阶段：全部 hash/INVALID/签名校验先于任何业务 DDL；篡改 V001 实测校验阶段阻断、退出码 2、无前置条件 |
| H-04 | 采纳（方案 A） | V001 拆分为 `V001`（五表）/`V002`（binding）两迁移 ID，新增 `M16` 约束附加步骤；全链路 M15→M16→V001→V002 实测 SUCCEEDED |
| M-01 | 采纳 | history DDL 归 runner 引导独占（V001 移除），补 `(status, started_at DESC)` 索引 |
| M-02 | 采纳 | 版本 trigger 保护 `id/tenant_id/template_id` 全生命周期 + 已发布 `published_by/published_at`；实测改挂模板与篡改发布人均拒绝 |
| M-03 | 采纳 | trigger 拒绝 DRAFT→DEPRECATED/RETIRED；实测拒绝，DRAFT→PUBLISHED、PUBLISHED→DEPRECATED 合法 |
| M-04 | 采纳 | Outbox `payload ≤ 2MiB`、审计 `diff_summary ≤ 64KiB` CHECK |
| M-05 | 采纳 | check-comments 清单扩至九表（含幂等表/Inbox，未建表自动跳过）；实测 MIG-009 PASS |
| M-06 | 采纳 | runner 校验 `pg_get_indexdef` 签名（列序/唯一性/谓词）；正向路径实测通过，漂移反例列入后续合同 |
| M-07 | 采纳 | ADR-013 CLI 契约改写与实现对齐（子命令 + `--step M15|M16|V001|V002`，移除 `--allow-destructive`） |
| M-08 | 采纳 | FAILED 以 `ON CONFLICT` 落史（脱敏摘要 + error_code），started/finished 取真实 `clock_timestamp()`；实测 U001 失败落 FAILED 行 |
| M-09 | 采纳 | 新增 `roles_candidate.sql`：migration_executor / power_model_write / power_model_outbox_pub（列级 UPDATE）/ power_model_readonly 四角色最小授权 |
| M-10 | 部分采纳 | Ajv strict + ajv-formats 复跑 4/4 PASS（仓库外临时工具目录）；CI 门禁接线属实现工作，继续 OPEN |
| L-01 | 采纳 | 业务表主键改 `BIGINT` 并在注释冻结"应用统一 ID 策略赋值，数据库不兜底" |
| L-02 | 采纳 | 补 `draft_state`/`member_type`/审计 `principal_type` CHECK |
| L-03 | 采纳 | 补 `version = major.minor.patch[-prerelease]` 一致性 + SemVer 格式 CHECK；实测不一致插入拒绝 |
| L-04 | 采纳 | ADR-013 状态机注明 SKIPPED 仅为返回语义不落库 |
| L-05 | 采纳 | V001/U001 头注升 1.5.0、events README 引用 0.1.8、LOCK_KEY=913005 推导说明入脚本/ADR/env.example |

### 8.2 复核结论

4 项 HIGH 的设计与实现缺口已关闭并有临时库实测证据；10 项 MEDIUM 全部落地；5 项 LOW 全部落地。**本轮处置不改变 ADR-013/ADR-014 的 Proposed 状态**：转 Accepted 仍需关闭完整 12 表画像生产重跑、`power_idempotency_record` 落库与 MIG-005 合同、索引签名漂移反例、性能压测、备份/恢复/回滚演练与 DBA/代码 owner 复核签字。不得在任何共享/生产库执行 DDL 的限制维持不变。

### 8.3 演练证据补录（2026-08-07 当日第二批）

§8.2 列出的"索引签名漂移反例、备份/恢复/回滚演练"已在同日补做并全部 PASS（临时评审库，用后清理；ADR-013 升 1.4.1、migration 设计升 0.1.9）：

- **M-06 漂移反例**：同名索引列序颠倒 → 校验阶段 `INDEX_SIGNATURE_MISMATCH`（退出码 2，零变更）；清理漂移索引后重跑恢复，四步 SUCCEEDED；
- **MIG-005 双向**：幂等表缺失 + `REQUIRE_IDEMPOTENCY=1` → precheck 阻断；候选 DDL 落临时库 → precheck PASS，8 反例烟测复跑 PASS；check-comments 对已落库幂等表实测生效；
- **备份/恢复**：runner 自动备份 → 人为损毁（DROP 审计表 + 删 product 行）→ 异库恢复，计数与对象逐项还原；
- **回滚**：uninstall 后六张新表全部删除，history 5 行、`uq_product_tenant_identification` 约束、幂等表按设计保留。

转 Accepted 剩余 OPEN 收敛为四项：完整 12 表画像生产重跑、standard 最低规格压测、幂等表经 runner 正式落库的步骤建模、DBA/代码 owner 复核签字。
