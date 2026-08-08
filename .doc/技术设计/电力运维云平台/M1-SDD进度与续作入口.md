# M1 SDD 进度与续作入口

> 检查点日期：2026-08-06
> Git 分支：`cfdqiot`  
> 当前阶段：Technical Design 进行中  
> 说明：本文件用于下次会话恢复上下文；状态以各正式文档为准

## 0. 项目强制双基线

本项目所有需求、PRD、SPEC、ADR、TD、开发、测试、评审、发布和运维变更，必须同时依据：

- [平台功能计划 1.4.0](../../架构设计/平台功能计划.md)：产品范围、版本、部署档位、模块归属、里程碑和优先级基线；
- [EasyAIoT 项目开发宪法 1.5.0](../../开发规范/EasyAIoT项目开发宪法.md)：安全、架构、数据、兼容、开发流程、质量门禁和 DoD 基线。

不得只读取或遵循其中一份。下游文档、代码或既有实现与双基线冲突时，必须先停止开发并完成基线修订或 ADR 决策；未获得实际代码、测试、构建、数据库和运行证据的事项继续标记为 `OPEN`。

## 1. 当前基线

| 文档 | 版本 | 状态 |
|---|---:|---|
| 平台功能计划 | 1.4.0 | 当前产品基线 |
| EasyAIoT 项目开发宪法 | 1.5.0 | 当前开发治理基线 |
| PRD-01 站点设备与数据采集 | 1.2.0 | Approved / Baselined（M1） |
| SPEC-001～004 集合 | 1.4.0 | Approved / Frozen |
| ADR-001～012（ADR-005 Superseded） | 当前索引基线 | Accepted / Superseded |
| [TD-001 collector 与 NODE 部署契约](./TD-001-collector与NODE部署契约.md) | 1.0.4 | In Review |
| [TD-002 SQLite Outbox 与恢复迁移](./TD-002-SQLite-Outbox与恢复迁移.md) | 1.0.2 | In Review |
| [TD-003 遥测 Inbox、ACK 与时序投影](./TD-003-遥测Inbox-ACK与时序投影.md) | 1.0.1 | In Review |
| [TD-004 电力对象、别名、二维码与历史编码兼容](./TD-004-电力对象别名二维码与历史编码兼容.md) | 1.0.1 | In Review |
| [TD-005 物模型模板 Schema、版本差异与发布 API](./TD-005-物模型模板Schema版本差异与发布API.md) | 1.0.16 | In Review |
| [TD-005 运行模型兼容与删除链技术设计](./TD-005-运行模型兼容与删除链技术设计.md) | 0.1.9 | In Review |
| [TD-005 版本、绑定、审计与 Outbox 迁移回滚设计](./TD-005-版本绑定审计Outbox迁移与回滚设计.md) | 0.1.7 | In Review / Migration Candidate |
| [TD-005 孤儿属性处置方案](./TD-005-孤儿属性处置方案.md) | 0.2.0 | Executed / Verified |

`In Review` 表示设计已形成并进入评审，可能仍有评审意见或实现/压测证据待关闭；不得描述为已经开发完成或 Approved / Frozen。TD-001～005 均已完成现有评审报告的文档处置；各 TD 仍需分别关闭证据门禁。

## 2. 已完成工作

- PRD-01 已完成评审处置并建立 M1 产品基线。
- SPEC-001～004 与 ADR-001～011 已形成冻结/接受基线。
- TD-001 已完成 collector Profile、NODE 类型化部署、配置快照、串口、健康与 `TelemetryOutboxPort` 设计；评审问题已处置。
- TD-002 已完成 SQLite WAL、单 writer、有界队列、ACK 状态机、容量保护、恢复、迁移与 Gap 设计；评审问题已处置。
- TD-003 已完成 Envelope V1、中心 Inbox、应用 ACK、两层幂等、standard/full Store、投影事件 Outbox、Gap Report、完整率、水位和混合版本设计；评审问题已处置。
- TD-004 已完成首次评审处置，补齐授权撤销、幂等、审计 Schema、永久 assetCode、索引、collector 契约和 API 细节；仍待存量画像、自动合同/安全测试、迁移与压测证据。
- TD-005 已完成 1.0.4 评审处置与自动证据，明确现有 `/thingModel` 发布仅为无持久化占位成功；采用新增版本化控制层并保留现有产品运行表的兼容方案，形成 Schema、SemVer/JCS/hash、三方差异、产品绑定、升级回滚、导入和发布 API。
- TD-005 已产出 Review Candidate 资产：10 类标准模板、71 个属性、16 个事件、7 个高风险服务、JSON Schema、合法样例、Excel 统一导入模板和 SHA-256 manifest；尚未完成行业专家与标准 validator 冻结。
- TD-005 评审的 5 项 HIGH 已完成设计处置：幂等复用 TD-004、capability 对齐 `power.device.model`、旧电力发布入口拒绝假成功、导入攻击面收敛、CT/PT 变比十进制归一化；3 项部分采纳和 1 项错误文件名意见已在评审报告末尾说明。
- TD-005 Draft 2020-12 自动验证已执行 2 个正例、11 个反例；修正了原合法样例的下划线 eventCode。Python RFC8785 与 Node/ECMAScript 已对 2 组 canonical/hash golden 一致通过，证据位于资产 `verification/` 目录。
- TD-005 二次复核 R-01～R-05 已处置：最终输出精度取目标属性、冒号 action 路由单一风格、旧电力发布保持 409 并说明依据、补齐版本记录、固定 UTF-8 无 BOM。
- TD-005 已在 `postgres-server / iot-device20` 完成本地目标库只读画像：确认缺少 `product_properties.service_id`、业务唯一约束、外键和触发器，并发现 4 条孤儿属性；事实采集完成但修复门禁仍为 OPEN。
- TD-005 画像评审 R1～R7 已完成文档处置：画像 v1.1 增加七表列签名、双作用域重复、标识异常、结果 JSON Schema 与生产重跑契约。
- TD-005 的 4 条孤儿属性处置已执行完成：执行前 precheck、动态12列、完整快照、最终删除回滚演练及修复后 rollback 恢复演练均 PASS；初始化 COPY 旧种子4→0，数据库显式提交4→0，修复后画像 `product_properties=17`、六类 orphan/七类重复组/六类标识异常全部为0，现行演示产品/设备/属性保持3/3/9。该存量子门禁 PASS。
- ADR-012 已完成独立评审并转 Accepted，冻结根属性使用 `product_properties`、服务参数使用 command request/response、参数以 `commands_id` 为权威关联；代码与 DDL 实现门禁仍由 TD-005 阻断。
- ADR-012 专项复核已完成：ADR 更新至 1.0.1；L-01 部分采纳、L-02～L-04 采纳、L-05 不修改。运行模型更新至 0.1.1，纠正“18 列”为画像批准的 20 列，并固定 8 张核心运行表 + 4 张受保护依赖表的 12 表画像范围。
- ADR-012 宪法专项复核已完成：纠正原报告“DoD 11/11”为宪法实际 12 项、当前 3 PASS / 7 OPEN / 2 N/A；ADR 更新至 1.0.2，明确收缩 owner/到期日、golden 前置及备份/保留期/审批/恢复演练。
- 已新增并复核 TD-005 运行模型兼容与删除链技术设计 0.1.3，覆盖 DO/VO/Mapper 分层、legacy adapter、unique/XOR/tenant FK/RESTRICT、完整删除依赖图、Feign 超时/降级、性能预算和 TEN-001～008、DEL-001～010 合同。
- 目标实例补充只读核对：`product_event_response=0`、`product_script=0`、`product_template=0`、`device=4`，4 个产品均各关联1个设备；现有产品必须受删除保护，删除成功测试需使用独立 fixture。
- 目标画像已升级到 v1.2.0 与结果 Schema 1.1.0：覆盖 8 张核心运行表 + 4 张受保护依赖表，12 表孤儿/当前关系异常为0；`product_script` 缺少主键、`ota_packages.tenant_id` 可空及业务 unique/FK/check/trigger 为0继续阻断上线。
- 首份非空旧格式 round-trip fixture/golden 已冻结并自动验证 PASS：覆盖根属性、服务、命令、输入/输出、事件及事件输出，8 表运行投影和 canonical SHA-256 已进入 manifest；生产 adapter 合同经第一批实现推进为 PARTIAL。
- TD-005 第一批生产实现已完成：`ProductPropertyDO`/MapStruct 分层、20 列根属性 Mapper、无 `serviceId` 的新根属性 DTO、command 参数链 legacy 只读 adapter、旧聚合调用迁移；Java 17 反应堆编译 PASS，6 项定向合同测试 PASS。该批次不含 PostgreSQL tenant CRUD 和 frozen golden 的生产 Java 全量消费，因此 adapter 总门禁仍为 PARTIAL。
- TD-005 主代码 Java legacy 双向纯转换已直接消费冻结的同一 fixture/golden：旧 JSON→八表运行投影→旧 JSON 结构等价 PASS，歧义服务属性和根属性 `serviceId` 均 fail-closed；新增 3 项测试后定向测试累计 9 项 PASS。尚未接入数据库和旧端点，生产 adapter 总门禁仍为 PARTIAL。
- TD-005 已在真实 `postgres-server / iot-device20` 完成根属性 TEN-001～004/006 子合同：上下文注入、跨租户查改删、混租户批量原子拒绝和缺失上下文 fail-closed 均 PASS；修复了租户拦截器未自动限制 DELETE 计数子查询导致的部分成功风险。完整定向测试累计 13 项 PASS，事务回滚后 fixture 残留为 0；其余 TEN 和八表持久化仍 OPEN。
- TD-005 已新增内部八表聚合持久化边界：锁定当前租户既有 product，在同一事务替换其余七张模型表并导出旧格式；TEN-005 的跨租户参数和 service-command 影子关系在删除前拒绝，数据库中途失败保存点回滚后原模型完整恢复。完整定向测试累计 17 项 PASS，八表 fixture 残留总数为 0；公开接口尚未接线。
- ADR-011 capability 基础已落地：版本化 Draft 2020-12 schema 与 electric-standard/full `0.1.0` 候选 manifest、`iot-common-env` 统一 `CapabilityService`、`iot-system GET /system/capabilities`、Docker/安装器同源传递均已实现；full enabled 集合为 standard 的严格超集，共有 quota 不降低。quota 仍待容量压测，不得作为销售承诺；standard 部署旧裁剪已修正为包含 `iot-device`，mini 未配置电力 manifest 时 fail-closed。
- TD-005 内部持久化已接稳定业务错误与 `power.device.model` 前置守卫：TEN-007 standard/full 同业务语义和 SQL 路径、TEN-008 mini 在解析/数据库前统一拒绝。共享能力合同 4 项、只读 API 1 项、设备侧目标测试 19 项全部 PASS，真实 PostgreSQL 两测试 tenant 的八表残留总数为 0。审计/Outbox 因版本/绑定/outbox migration 尚未批准而保持 OPEN，本批次未提前造表。
- TD-005 已形成版本/绑定/领域审计/Outbox migration 与 rollback 0.1.0 候选：冻结同事务边界、UUID v4 事件、Outbox 租约重试、additive migration、应用回滚优先和非空表禁止 destructive down。仓库未发现 Flyway/Liquibase，迁移执行器、SQL 资产、事件消费者、压测值和全部故障合同仍为 OPEN；本轮没有执行 DDL。
- TD-005 migration 候选宪法专项评审已完成评估并更新至 0.1.1：补齐目标角色、事件当前/上一主版本与 V1/V2 双发、未知字段/未知主版本处理、API/资源候选预算、配置安全默认、网络超时、迁移锁风险，以及 `power_idempotency_record` 和 product 同租户 unique 的串行前置。C-09“每条必须显式写 MUST/SHOULD/MAY”不属于宪法要求，已用全局强度约定澄清；其他合理意见已进入设计，但实现证据继续 OPEN，未执行 DDL。
- TD-005 migration 已更新至 0.1.2：形成 ADR-013（Proposed）受控 runner 选型候选（不引入 Flyway/Liquibase，使用 history + SHA-256 + advisory lock + 事务/非事务步骤分离），并生成 V001/U001 DDL 骨架评审附件（`assets/td005-migration/`）。runner 实现、历史表/校验和、约束名/trigger/权限和目标库核对仍 OPEN；本轮未执行 DDL。
- TD-005 migration 骨架烟测（2026-08-06）：V001/U001 已在本地临时评审库 PostgreSQL 18.4 完成可执行性验证：V001 建表/索引/触发器 PASS、审计追加写 UPDATE 拒绝 PASS、U001 非空拒绝且零变更 PASS、U001 空表卸载 PASS；临时库已清理。该结果仅证明骨架可执行，MIG/TX/OUT 等正式合同仍未执行。
- TD-005 首批 4 个领域事件 V1 Schema 与合法 fixture 候选已生成（`assets/td005-migration/events/`），并通过 Ajv Draft 2020-12 正例校验 PASS；transport、消费者 Inbox、双版本合同仍未冻结。
- 项目开发宪法已升级至 1.5.0：新增“数据库 DDL 必须提供中文表/字段注释”MUST，并纳入模块最低门禁与评审 diff 核对；当前治理基线已同步，历史冻结记录继续保留 1.4.0 版本标注。
- ADR-013 已按宪法专项评审升级至 1.1.0：补齐目标用户与角色、状态机、CLI/错误码、配置清单、history 索引与 `TIMESTAMPTZ`、中文注释 MUST、资源预算、超时/重试/日志、目标平台、`migration_executor` 最小权限、文档同步与 MIG-007～009；仍为 Proposed，执行器实现、合同证据、压测和演练 OPEN。
- ADR-013 已升级至 1.2.0 并完成候选 runner Spike：`.scripts/postgresql/td005-migration/td005_migration.sh` 支持 dry-run/apply/uninstall/check-comments；临时 PostgreSQL 18.4 上 MIG-001/002/004/007/009 PASS，MIG-003/005/006/008、性能压测与备份/恢复/回滚演练 OPEN；未触碰生产库。
- ADR-013 已升级至 1.3.0：新增运行时画像 precheck（产品重复/孤儿阻断，MIG-003 PARTIAL）、幂等表门禁（MIG-005 PARTIAL）、INVALID index 检测与恢复（MIG-006 PASS）、连接超时快速失败与幂等恢复（MIG-008 PASS）；完整 12 表画像、TD-004 幂等表 DDL、压测与演练仍 OPEN。
- ADR-014（Proposed）已形成：Kafka 作为 Outbox transport（`power-model-release-v1` + 消费者组），消费者 Inbox 候选 DDL（`power_model_event_inbox`）已生成；待评审关闭后冻结 topic/route、重试、保留窗口和双版本合同。
- ADR-014 宪法专项评审已完成并处置至 1.1.0：基线复跑 24/24 PASS（capability 4 + 只读 API 1 + 设备侧 19，真实 PostgreSQL）；评审报告 0 VIOLATION / 8 MISSING / 7 PARTIAL，已补目标用户与角色、档位行为（mini fail-closed、standard/full 同契约）、事件 Envelope 冻结引用、Schema 入 `iot-device-api` 路径、CI 双主版本合同门禁、配置清单、可观测性与对账、Kafka PLAINTEXT 安全态势声明、双 UNIQUE 收缩、文档同步计划；压测、CI 接线与消费者实现继续 OPEN，仍为 Proposed。
- ADR-013 已升级至 1.3.1：12 表画像 v1.2.0 在 `postgres-server / iot-device20` 完成新鲜度重跑（只读事务，原始输出与结果 JSON 已存档，结果 Schema 1.1.0 校验 PASS），与 2026-08-05 冻结基线逐项一致——行数不变、重复/孤儿/标识异常/关系不匹配全部为 0、无 blocking 条件触发；生产存量重跑（`productionRerunRequired=true`）、TD-004 幂等表、压测、演练与 DBA 核对继续 OPEN。
- TD-004 `power_idempotency_record` 候选 DDL 已形成（`assets/td005-migration/power_idempotency_record_candidate.sql`）：完整覆盖 §7.12 列契约与 §7.10 索引基线，含唯一作用域争抢、状态-响应一致性、hash 长度、payload 16KiB 上限、expires_at 排序与 24 小时默认值约束，中文表/列注释齐全。临时评审库烟测 PASS：DDL 应用、默认 24h 窗口、状态迁移、8 项反例（hash 长度/状态-响应/枚举/过期排序/payload 超界/principal_type/同作用域争抢唯一冲突）全部按预期拒绝、跨租户同 key 不冲突、注释完整性 0 缺失；临时库已清理，未触碰共享库。落库仍受 ADR-013 批准与 MIG-005 合同门禁约束，继续 OPEN。
- 运维文档同步已启动：新增 `.scripts/postgresql/README.md` 与 `td005-migration/env.example`；`.doc/架构设计` 与最终部署文档同步仍待 DBA/文档评审完成。
- ADR-013/ADR-014 与 migration 资产的 DBA/架构专项评审已完成（2026-08-07，[评审报告](../../开发规范/ADR-013与ADR-014评审报告-DBA架构专项.md)）：架构方向可批准，但发现 4 HIGH / 10 MEDIUM / 5 LOW——runner 缺锁等待/语句超时/重试实现、apply 前备份被可选化、hash 校验与 DDL 执行交错、V001 三步拆分声明与单事务执行矛盾；另有版本 trigger 身份列未保护、DRAFT 可跳 RETIRED、注释门禁缺幂等/Inbox 两表等。
- DBA/架构专项评审已全部处置（2026-08-07，同报告 §8，采纳 18/部分采纳 1）：ADR-013 升 1.4.0、ADR-014 升 1.2.0、migration 设计升 0.1.8。runner 重构为两阶段先校验后执行 + 超时/重试/强制备份/FAILED 落史；V001 拆分 V001/V002 并新增 M16 约束附加步骤；版本 trigger、SemVer/有界 CHECK、九表注释门禁、roles_candidate.sql 落地。临时库重跑：全链路 SUCCEEDED、幂等跳过、篡改阻断、锁忙有界失败、trigger 反例、空表卸载全部 PASS；事件 fixture strict+ajv-formats 4/4 PASS。两 ADR 维持 Proposed，未在任何共享/生产库执行 DDL。
- 演练证据第二批已完成（2026-08-07，ADR-013 升 1.4.1、migration 升 0.1.9、评审报告 §8.3）：索引签名漂移反例与恢复、MIG-005 幂等表门禁双向 + 8 反例烟测复跑、备份/恢复演练（损毁→异库恢复逐项一致）、回滚演练（六表清零、history/约束/幂等表保留）全部 PASS。转 Accepted 剩余 OPEN 收敛为四项：生产画像重跑、standard 最低规格压测、幂等表 runner 落库步骤建模、DBA/代码 owner 复核签字。
- 幂等表 runner 落库步骤建模已完成（2026-08-07，ADR-013 升 1.4.2、migration 升 0.2.0）：新增 runner 步骤 `M05`（`.scripts/postgresql/td005-migration/steps/M05__power_idempotency_record.sql`，链首串行前置、单事务，DDL 与评审候选逐字一致），步骤链扩展为 M05 → M15 → M16 → V001 → V002，CLI/env/roles 同步，MIG-005 升 PASS。临时评审库演练全项 PASS：五步骤链 SUCCEEDED（history 五行 hash 与 manifest 一致）、重跑全 SKIPPED、M05 篡改 HASH_MISMATCH 阻断零变更、幂等语义 9 反例烟测 PASS（SMOKE_RESULT=2）、MIG-009 九表门禁 PASS、U001 保留幂等表与 history；临时库已销毁，`iot-device20` 未触碰（product=4 与基线一致）。转 Accepted 剩余 OPEN 收敛为三项：生产画像重跑、standard 最低规格压测、DBA/代码 owner 复核签字。
- DBA/代码 owner 复核签字材料包已形成（2026-08-07，[ADR-013与ADR-014复核签字包](../../开发规范/ADR-013与ADR-014复核签字包.md) 1.0.0）：冻结基线提交 `a09a1b02` 与 17 项资产 SHA-256 清单（含五步骤链、roles、幂等表候选/烟测、Inbox 候选、事件 V1 Schema），提供资产漂移核对、dry-run 两阶段校验、临时库全链路复跑三类走查命令与预期输出，列明 DBA 视角 7 项重点复核项与签字栏。签字包仅就设计/资产/已有演练证据征求签署，明确不构成上线批准；签字关闭后剩余 OPEN 为两项：standard 最低规格压测、完整 12 表画像生产重跑。
- 剩余两项 OPEN 的执行方案已形成（2026-08-07）：[TD-005-standard最低规格压测方案](./TD-005-standard最低规格压测方案.md) 0.1.0（6 类压测对象、环境规格/数据规模/阈值全为候选值待实测冻结、故障注入与证据包格式；发现仓库未定义 standard 硬件基线，列为执行前运维确认项）与 [TD-005-生产画像重跑Runbook](./TD-005-生产画像重跑Runbook.md) 1.0.0（只读红线、画像报告 §2 六条冻结契约、v1.2.0 脚本 + Schema 1.1.0 解析管线复用、PASS/WARN/BLOCK 判定与证据包格式）。两方案仅备制规程，均未执行任何压测或生产访问。
- 基线回归复跑 PASS（2026-08-07，当日第二次）：capability manifest 4 项（iot-common-env）、只读 API 1 项（iot-system-biz CapabilityControllerTest）、设备侧 19 项（ProductPropertiesMapperContractTest 4 / ProductPropertiesTenantPostgresIntegrationTest 4 / LegacyServicePropertyAdapterTest 2 / LegacyThingModelPersistencePostgresIntegrationTest 6 / LegacyThingModelRuntimeAdapterGoldenTest 3）全部 0 失败 0 错误 0 跳过，真实 PostgreSQL（postgres-server 容器）集成测试通过；`mvn` 退出码 0。基线在签字包/压测方案/Runbook 归档后保持绿色。
- item 5（生产 Java/TypeScript 消费 JCS/hash golden + Schema 外语义合同）已完成首轮落地（2026-08-07）：新增 `iot-device-biz` 包 `service.model`——`JcsCanonicalizer`/`JcsNumberFormatter`（RFC 8785，ECMAScript 数值逐位等价，对象键 UTF-16 排序，`contentHash="sha256:"+hex`）、`ModelSemVer`（解析/排序/prerelease 生产绑定拒绝/最低增量门）、`TemplateMemberValidator`（properties/events/services 成员 code 类内唯一）、`TransformationRatioValidator`（§5.3：BigDecimal 变比、二次值>0、6 位 HALF_UP 一致性、归一化中间值不截断、目标 precision 缺失失败）。合同测试 18/18 PASS：`JcsGoldenContractTest` 消费 `jcs-golden.json` 两个 case 的 canonical 字节/字节数/哈希与 Python/Node 全量一致；TypeScript 侧新增生产模块 `WEB/src/utils/jcs.ts` 与合同脚本 `build/script/verifyJcsGolden.ts`（`pnpm verify:jcs-golden`，2/2 case PASS）。全量回归 42/42 PASS（基线 24 + 新增 18），且新代码在默认 Java 8 编译级别下 `test-compile` 通过（双编译级别兼容）。新引入候选错误码 `MODEL_TEMPLATE_SEMVER_INVALID`/`MODEL_TEMPLATE_SEMVER_PRERELEASE_FORBIDDEN`/`MODEL_MEMBER_CODE_DUPLICATE`/`MODEL_JCS_*` 待 TD-005 下次修订登记入稳定错误码清单；item 5 余项（diff 核心、参数级唯一性、HIGH_RISK 策略校验、TS 侧 SemVer/CT-PT）继续 OPEN。
- item 5 余项已完成（2026-08-07，同包 `service.model` 扩展）：`TemplateDiffEngine`（§7.1 结构化 diff 计算最低 SemVer 增量：成员删除/类型或单位语义变化/required 提升/范围收紧/枚举收缩/高风险语义变化→MAJOR，可选成员新增/范围放宽/枚举与映射扩充→MINOR，纯展示元数据→PATCH，未列明结构变化保守归 MAJOR）、`TemplateThreeWayMerge`+`MergeOutcome`（§9 三方合并六规则全覆盖：AUTO_STANDARD/AUTO_VENDOR/AUTO_COMMON/AUTO_DROP/CONFLICT/DELETE_MODIFY_CONFLICT/ADD_ADD_CONFLICT，成员指纹=JCS canonical，纯函数确定性输出，冲突决策持久化属后续工作包）、`TemplateHighRiskValidator`（§5.4 HIGH_RISK 四项策略任一缺失即 `MODEL_HIGH_RISK_POLICY_INCOMPLETE`）、`TemplateMemberValidator` 扩展参数级唯一性（服务/事件 inputs/outputs 内 parameterCode）。新增合同测试 26 项全 PASS；全量回归 68/68 PASS（基线 24 + model 包 44）；默认 Java 8 级别 `test-compile` 通过。TS 侧 SemVer/CT-PT 经设计复核不实现：§22 冻结"只实现一套核心"，校验归服务端，前端仅消费错误码——此解释已记录。item 5 全部关闭；§23 门禁 6 的 SemVer/三方合并单项已有单元合同，发布不可变/幂等并发属工作包 5 继续 OPEN。
- item 6 恶意导入 fixture 与预检合同已完成（2026-08-07，fixture 部分）：新增 `ImportSafetyPrecheck`（§11.3 进入 EasyExcel/Schema 校验前的安全门）：OOXML 侧 ZIP magic/原始 20MiB/展开 100MiB/entry 1000/压缩比 100 有界，拒绝绝对路径与 `..` 穿越、`vbaProject.bin`、OLE 嵌入、externalLinks、connections/queryTables、PivotTable/PivotCache、ActiveX、rels 外部 TargetMode；公式单元格（`<f>` 元素或 calcChain）单独返回 `MODEL_IMPORT_FORMULA_NOT_ALLOWED`，其余返回 `MODEL_IMPORT_UNSAFE_WORKBOOK`；JSON 侧严格 UTF-8 无 BOM、10MiB 上限、嵌套深度 64、不可信 `$schema` 返回 `MODEL_IMPORT_UNTRUSTED_SCHEMA_REFERENCE`（可信前缀 `https://easyaiot.local/schemas/`）。恶意 fixture 由 `ImportSafetyPrecheckTest` 内联构造（ZIP 字节级，自描述可评审）17 例全 PASS；限额值为候选（已标注待评审冻结），新候选错误码 `MODEL_IMPORT_JSON_MALFORMED` 待 TD-005 下次修订登记。全量回归 80/80 PASS；Java 8 默认编译通过。item 6 余项 OPEN：Excel 逐 Sheet 导入链路（工作包 4，依赖 EasyExcel 集成）、MinIO key 隔离与错误下载合同、10 类行业模板专家评审（人工）。
- 工作包 5 领域层已完成（2026-08-07，不依赖未批准新表的纯逻辑部分）：`TemplateLifecycle`（§7 状态机：仅允许 DRAFT→PUBLISHED、PUBLISHED→DEPRECATED、DEPRECATED→RETIRED、PUBLISHED/DEPRECATED→DRAFT 五种转换；RETIRE 需结构化 migrationNotice + 已发布替代 + 影响确认否则 `MODEL_RETIRE_PRECONDITION_FAILED`；DEPRECATED 禁新绑定 `MODEL_DEPRECATED_NEW_BINDING_DENIED`；PUBLISHED 起内容不可变 `MODEL_TEMPLATE_PUBLISHED_IMMUTABLE`；草稿 90 天无活动 ABANDONED 且不可编辑）与新包 `service.idempotency` 的 `IdempotencyArbiter`（TD-004 §7.12：无记录→首插争抢交数据库唯一约束；同 key 异 hash→409 `IDEMPOTENCY_KEY_REUSED` 绝不覆盖；同 hash 终态（SUCCEEDED/FAILED_FINAL）→重放已存响应；IN_PROGRESS 未过恢复阈值→`IDEMPOTENCY_IN_PROGRESS`，过阈值→可重试；`keyHash`=服务端 HMAC-SHA-256 32 字节、`requestHash`=method/path/规范 payload SHA-256）。合同测试 14/14 PASS；全量回归 94/94 PASS；Java 8 默认编译通过。持久化接线（发布事务、Outbox、幂等表 Mapper）待 ADR-013 Accepted 后接入；恢复阈值（候选 5min）待评审冻结。
- DBA/代码 owner 复核签字已双签关闭、压测经 owner 决定豁免（2026-08-07，ADR-013 升 1.4.3、评审报告 §8.5）：签字包 §3 走查当日实际执行全项 PASS（资产哈希零漂移@`9debfc54`、dry-run、临时库 `td005_signoff_review` 全链/重跑/篡改阻断/烟测/注释门/卸载保留，`iot-device20` 未触碰），青见兼两角双签「通过」（签字包 §5/§5.1）；standard 最低规格压测 owner 决定不执行、默认通过门禁，候选预算保持未冻结标记（压测方案转存档规程）。ADR-013/014 转 Accepted 仅余一项：完整 12 表画像生产重跑（按 Runbook，需生产只读窗口与审批）。
- 完整 12 表画像生产重跑已按 Runbook 执行并 PASS（2026-08-07，ADR-013 升 1.4.4、评审报告 §8.6）：只读事务（PGOPTIONS 强制 `default_transaction_read_only=on`、statement_timeout 300s）对 `iot-device20`（PG 18.4）执行画像脚本 v1.2.0，原始输出 ROLLBACK 收尾；jsonschema 1.1.0 校验 PASS；与 2026-08-05 冻结基线逐项 diff **零差异**；六项阻断条件均未触发、无告警。证据包五件归档 `assets/model-templates/verification/profile-rerun-prod-20260807/`。**证据诚实性边界**：执行环境为本地目标集成实例（非独立生产实例），`productionRerunRequired` 是否由本次重跑满足待 owner 指定；指定前 ADR-013/014 维持 Proposed。
- **ADR-013/ADR-014 已转 Accepted（2026-08-08，ADR-013 1.5.0 / ADR-014 1.3.0、评审报告 §8.7）**：owner（青见/qingjian1984）审阅证据包后指定画像重跑满足 `productionRerunRequired=true`，三项人工闭环（① 双签 2026-08-07、② 压测豁免 2026-08-07、③ 画像生产重跑 PASS + owner 指定）全数关闭；2026-08-07 画像成为后续迁移 apply 的新前置基线。转 Accepted 后 OPEN：事件消费者/transport/Inbox 实现、CI 双主版本门禁与事件合同门禁接线、候选预算生产前人工评估。
- TD-005 冻结第一批实现证据已落地（2026-08-08，ADR-014 升 1.3.1）：① Schema 归位——4 个事件 V1 Schema 复制入 `iot-device-api` 资源 `schema/power/model/v1/`（与评审资产 sha256 逐项一致），新增共享合同类型 `PowerModelEventEnvelope`（iot-device-api `event` 包：Envelope 不变量、`_V` 后缀与 schemaVersion 一致性、topicKey=`tenantId:aggregateType:aggregateId`、payload_hash、topic/DLQ/消费者组/2MiB 上限常量）；② 新包 `service.event` 领域逻辑——`InboxArbiter`（PROCEED/DUPLICATE/RETRYABLE/QUARANTINE_HASH_CONFLICT/REJECT_UNKNOWN_MAJOR_VERSION/AWAITING_DISPOSITION，未知主版本先于既有记录判定）与 `OutboxRelayPolicy`（OUT-001～004：仅 PENDING 到期或 PUBLISHING 租约过期可 CLAIM、final 错误即时 DEAD_LETTER、retryable 超限 DEAD_LETTER、1s→16s 指数退避封顶防溢出）；合同测试 31/31 PASS，设备域全量回归 125/125 PASS（另：reactor 全链运行时 iot-common-web 的 `DesensitizeTest` 因本机 Windows zh-CN 默认字符集失败，为上游预存环境问题、与本批次无关，单独复跑同样失败）。③ CI 事件合同门禁接线——`WEB/build/script/verifyEventContracts.ts` + `pnpm verify:event-contracts`（Ajv 2020-12 strict + ajv-formats 显式 devDeps）：4 Schema 的 $id 可信前缀/`_V` 后缀=schemaVersion=目录主版本校验、文档评审资产与 API 资源字节一致性（防第二份拷贝漂移）、4 fixture 校验、OUT-008 未知主版本反例、additionalProperties strict 反例、双主版本目录扫描（当前 v1 单主版本，V2 出现后双版本同检）——样例运行 24 项全 PASS。剩余 OPEN：Outbox/Inbox Mapper 与发布器持久化接线（DDL 须经 ADR-013 runner 获批窗口执行）、collector 配置发布协调器、双发对账演练、容量压测维持豁免口径。
- TD-005 冻结第二批（持久化接线）已落地（2026-08-08，ADR-014 升 1.3.2，`service.event` 包扩展）：`PowerModelOutboxService`（`Propagation.MANDATORY` 结构化强制同事务提交 + capability fail-closed 拒绝 mini 档入列）、`PowerModelOutboxRelay`（claim→send→回写编排，OUT-001～004 全路径：租约参数透传、成功 PUBLISHED、retryable 退避 1s→16s、final 即时 DEAD_LETTER、超限 DEAD_LETTER、多条目独立处置）、`PowerModelInboxWriter`（七路径：PROCESS/LOST_CONTENTION 争抢落败不处理不提交/DUPLICATE/RETRYABLE/异 hash 隔离 critical/未知主版本隔离 critical/维持隔离不重复升级）、仓储端口 `PowerModelOutboxRepository`/`PowerModelInboxRepository` + JDBC 实现（原子认领 FOR UPDATE SKIP LOCKED + UPDATE RETURNING、首插 ON CONFLICT DO NOTHING、隔离 upsert ON CONFLICT DO UPDATE、错误摘要截断脱敏）、`PowerModelEventKafkaConfiguration`（`power.model.events.enabled=true` 才装配，acks=all 候选/幂等 producer/有界超时）+ `KafkaPowerModelEventTransport`（RetriableException 链探测分流、摘要仅异常类名）。合同测试 23/23 PASS（fake 仓储/transport），设备域全量回归 **148/148 PASS**。剩余 OPEN：JDBC 真实库合同测试（待 V001 经 ADR-013 runner 获批窗口落库，沿用 TD005_PG_ENABLED 跳过模式）、collector 配置发布协调器、双发对账演练、容量压测维持豁免口径。
- TD-005 冻结第三批（消费循环）已落地（2026-08-08，ADR-014 升 1.3.3）：`PowerModelEventEnvelopeCodec`（消费侧解析，畸形/缺字段/不变量违规全 fail-closed 稳定码，payload_hash 以原始正文计算）、`PowerModelEventHandlerRegistry`（处理器注册表 + `PowerModelEventProcessingException` retryable/final 分流；未注册处理器按 final 进 DLQ 绝不静默丢弃）、`PowerModelEventConsumerCoordinator`（P-07 全契约单条裁决：markProcessed 后才 COMMIT、DUPLICATE/QUARANTINED/DLQ 处置后 COMMIT、LOST_CONTENTION 不提交等重投、retryable 1s→16s 退避超限 DLQ、DLQ 投递失败抛错不提交）、`PowerModelEventKafkaListener` 薄适配（MANUAL_IMMEDIATE、nack 退避、enabled 门禁 + 消费容器工厂批量上限候选 100）、`PowerModelEventWiringConfiguration`（编排 Bean 装配）。合同测试 18/18 PASS，设备域全量回归 **166/166 PASS**。两项如实 OPEN：① 发布器调度驱动——iot-device 无 @EnableScheduling，@Scheduled 静默不触发违背失败关闭，调度器选型（Spring Scheduling / iot-common-job Quartz）列为部署评审 OPEN；② 处理器注册表暂为空——TD-001 collector 业务处理器实现时接入，空表下事件按缺失处理器进 DLQ（有持久证据，非静默丢弃）。
- TD-005 冻结第四批（发布器调度驱动）已落地（2026-08-08，ADR-014 升 1.3.4）：选型经 owner 部署评审裁定为 **Spring Scheduling**——iot-device 运行时经 iot-common-mq 传递激活调度设施（无 auto-config exclude），Quartz 引入新依赖与 QRTZ_* 表属过度设计，多实例安全由 SKIP LOCKED + 租约认领承担。新增 `PowerModelOutboxRelayScheduler`（fixedDelay 轮询 relayOnce、注入 Clock、单轮异常只记类型摘要绝不外抛），`PowerModelEventWiringConfiguration` 显式 @EnableScheduling（类级 enabled 门禁不变，mini 不调度；防传递依赖变化静默停转）。新增候选配置 relay.poll-interval-ms=1000 / initial-delay-ms=5000（压测后冻结）。合同测试 3/3 PASS，设备域全量回归 **169/169 PASS**。
- TD-001/002/003 的 Envelope、configVersion、siteCode、dataPriority、requestId、Topic、5 分钟 ACK deadline 和健康语义已经对齐。
- TD-001～004 四份评审报告均保留原始意见并附最终逐项处置，发生冲突时以报告末尾的“复核与最终处置”为准。

## 3. 已冻结的关键方向

- mini 不增加电力运维能力；standard/full 共用核心实现，只允许 Store adapter、容量和配额差异。
- M1 RTU Poller 位于站点 `iot-sink collector`，由 NODE 管理。
- 边缘可靠队列使用 SQLite WAL、`synchronous=FULL`、单写入器和应用 ACK。
- MQTT QoS 1 PUBACK 不代表业务持久化；只有 `ACCEPTED_DURABLE/DUPLICATE` 可清理边缘数据。
- 中心先提交 PostgreSQL Inbox 再 ACK；standard 投影 PostgreSQL 月分区，full 投影 TDengine。
- full 在 M1 不提供 PostgreSQL 应急 Store，避免形成第二事实源。
- 电力遥测 siteCode 必须非空；未绑定站点的历史设备不得启用 collector，不使用 `unassigned` 占位。
- 未知 ACK 按 messageId 独立计数，不能使用 collector 全局次数推动正常消息进死信。
- 最终拒绝必须先有持久审计，不允许 `audit_pending` FINAL。
- EDGE_DELIVERY 与 CENTER_PROJECTION gap 分阶段存储和统计，不得重复计数。
- 物模型模板使用 SemVer、JCS canonical JSON 和 SHA-256；PUBLISHED 内容不可原地修改，产品绑定精确版本与快照。
- standard/full 共用模板 Schema、表、API、差异算法和导入资产；full 只允许提高配额；mini capability 禁用。
- 标准模板与 RTU 点位绑定分别版本化；同一发布包原子静态校验，寄存器地址不得进入标准模板。

## 4. 尚未关闭的门禁

### TD-001

- standard/full 资源候选值、超时值和规模边界缺少原始压测证据；
- Linux PTY/真实串口、端口热插拔和 7 天稳定性尚未验证；
- Windows COM 与服务运行资格尚未完成。

### TD-002

- SQLite JDBC/JDK/目标文件系统的 WAL+FULL 掉电、ENOSPC、损坏恢复证据；
- 队列、事务、索引和未知 ACK 12 次候选值需要合同测试/压测；
- 4 小时及 24 小时断网补传、容量 80%/95% 和迁移演练尚未执行。

### TD-003

- projection event outbox、配置快照 replica、拒绝审计故障、Gap Report、32/36 位 UUID 合同测试；
- PostgreSQL 分区、容量、水位和 standard 准入压测；
- TDengine 确定性幂等 DDL/驱动 Spike，尤其“写成功后崩溃再投影”；
- 完整率、迟到、封账/重开和投影死信的业务/运维验收。

### TD-004

- `deviceIdentification` 存量重复、tenant 异常、软删除复用和未绑定站点画像；
- alias 并发锁/循环、二维码 HMAC/keyVersion、统一错误、审计故障和授权撤销证据；
- OpenFeign/objectRevision/configVersion 合同及迁移、回滚、压测演练。

### TD-005

- 本地目标集成实例 12 表画像、R1～R7 文档处置、4条孤儿属性清理、ADR-012 接受、迁移前非空 golden、Mapper/DO/根属性 DTO/legacy 只读 adapter、Java 双向纯转换、根属性 TEN-001～004/006、内部八表持久化/TEN-005/回滚、稳定错误和 TEN-007/008 已完成；仍需公开模型接口、审计/Outbox、唯一约束和删除链合同；生产存量环境需按画像 Schema 重跑；
- 孤儿存量子门禁已 PASS，但单条/批量产品删除代码仍不完整，不得把数据清理等同于删除链修复；
- Draft 2020-12 资产级 fixture 已 PASS；仍需生产 Java/TypeScript 消费相同 JCS/hash golden，并补 Schema 外语义校验；
- 10 类模板、71 个属性、单位、三相、累计量、CT/PT 变比及高风险服务的行业专家复核；
- 发布不可变、SemVer、三方差异、全量错误、租户隔离、产品绑定事务和精确回滚测试；
- Excel 宏/公式拒绝、逐行错误、RTU 分区版本合同与现有非电力功能回归；
- manifest 仍为 `gitCommit=UNCOMMITTED`，评审冻结时必须写入实际提交并复算哈希。

## 5. 下次建议起点

继续 SDD 文档链，下一步优先执行 **TD-005 证据准备与冻结门禁关闭**：

0. **ADR-013/ADR-014 已转 Accepted（2026-08-08）**：三项人工闭环全数关闭（① 双签、② 压测豁免、③ 画像生产重跑 PASS + owner 指定满足，评审报告 §8.7）；**当前最优先：TD-005 冻结工作**——事件消费者/transport/Inbox 实现、CI 双主版本门禁与事件合同门禁接线（候选预算值保持未冻结，生产执行前人工评估）；
1. 读取 [TD-005 1.0.16](./TD-005-物模型模板Schema版本差异与发布API.md)、[TD-005 运行模型兼容与删除链设计 0.1.9](./TD-005-运行模型兼容与删除链技术设计.md)、[migration 子设计 0.1.7](./TD-005-版本绑定审计Outbox迁移与回滚设计.md)和[TD-005 评审报告 §23](../../开发规范/TD-005评审报告.md)；
2. Mapper/DO/VO、legacy adapters、Java golden、根属性 TEN-001～004/006、内部八表持久化/TEN-005/回滚、稳定错误和 TEN-007/008 已完成；先复跑 capability 4 项、只读 API 1 项与设备侧 19 项目标测试，禁止回退到 `product_properties.service_id` 或散落 profile 判断；
3. 对 [版本/绑定/审计/Outbox migration 与 rollback 0.1.2 候选](./TD-005-版本绑定审计Outbox迁移与回滚设计.md)继续 DBA/架构评审，优先评审 [ADR-013（Proposed）](../../架构决策/电力运维云平台/ADR-013-受控数据库迁移执行器.md)与 [V001/U001 DDL 骨架](./assets/td005-migration/V001__power_model_version_binding_audit_outbox.sql)，关闭 runner 决策后再决定 TD-004 幂等表落库顺序、product unique/binding FK、事件 transport/消费者 Inbox 和压测/保留值；批准前不得启用 DDL；
4. 上述决策通过后，先形成可供 DBA 核对的 V001/U001 DDL 骨架和事件 V1 Schema/fixture 候选，再生成 manifest/hash 并执行 MIG/TX/OUT/AUD/CFG/PERF 合同；全部通过后才实现公开接口原子边界；
5. 在生产 Java/TypeScript 模块中消费现有 JCS/hash golden，并补成员唯一、SemVer、CT/PT 等 Schema 外语义合同；
6. 建立恶意 Excel/JSON 导入 fixture，并组织 10 类行业模板评审；
7. 所有门禁通过后更新资产 manifest 的真实 Git commit/hash，再决定 TD-005 是否转 Approved / Frozen。

TD-005 评审可以与 TD-001～004 的证据准备并行，但任何生产代码不得绕过各 TD 的冻结门禁。

## 6. 可直接复跑的检查点

- 功能检查点提交：`b7472b45 feat(sdd): enforce power model capabilities`；
- 当前分支：`cfdqiot`，尚未推送；
- 预期结果：capability manifest 4 tests、只读 API 1 test、设备侧 19 tests 全部 PASS，PostgreSQL 测试 tenant 八表残留为 0；
- `TD-005-版本绑定审计Outbox迁移与回滚设计.md` 已更新为 0.1.2 候选并完成宪法专项文档处置，ADR-013 Proposed runner 选型与 V001/U001 DDL 骨架已形成；下一步是 ADR-013/V001/U001 的 DBA 与架构评审、transport 决策，不执行 DDL。

从仓库根目录可直接执行以下 PowerShell 复跑当前基线；命令只读取本地 `.scripts/docker/.env.docker` 的 PostgreSQL 密码，不输出、不提交凭据：

```powershell
$passwordLine = Get-Content -LiteralPath '.scripts/docker/.env.docker' -Encoding UTF8 |
  Where-Object { $_ -match '^POSTGRES_PASSWORD=' } |
  Select-Object -First 1
$env:TD005_PG_PASSWORD = ($passwordLine -split '=', 2)[1]
$env:TD005_PG_ENABLED = 'true'

Push-Location DEVICE
mvn test -pl iot-system/iot-system-biz,iot-device/iot-device-biz -am `
  '-Dtest=CapabilityManifestContractTest,CapabilityControllerTest,ProductPropertiesMapperContractTest,ProductPropertiesTenantPostgresIntegrationTest,LegacyServicePropertyAdapterTest,LegacyThingModelRuntimeAdapterGoldenTest,LegacyThingModelPersistencePostgresIntegrationTest' `
  '-Dsurefire.failIfNoSpecifiedTests=false' `
  '-Dmaven.test.skip=false' '-DskipTests=false' `
  '-Dmaven.compiler.source=17' '-Dmaven.compiler.target=17'
Pop-Location
```

manifest 与部署脚本可独立快速复核：

```powershell
node -e "const fs=require('fs'),Ajv=require('./WEB/node_modules/ajv/dist/2020').default,d='.scripts/docker/capabilities/';const s=JSON.parse(fs.readFileSync(d+'capability.schema.json','utf8')),v=new Ajv({strict:true}).compile(s);for(const n of ['electric-standard.json','electric-full.json']){const x=JSON.parse(fs.readFileSync(d+n,'utf8'));if(!v(x))throw new Error(JSON.stringify(v.errors))}console.log('schema=PASS manifests=2 draft=2020-12')"
& 'C:\Program Files\Git\bin\bash.exe' -n .scripts/docker/deploy_profile.sh
```

## 7. 下次恢复提示

可直接使用：

> 读取 `.doc/技术设计/电力运维云平台/M1-SDD进度与续作入口.md`，遵循《平台功能计划》1.4.0 和《EasyAIoT 项目开发宪法》1.5.0，继续 TD-005。TD-005 1.0.16 / 运行模型 0.1.9 已完成 12 表画像、非空 legacy golden、Mapper/DO/DTO/adapters、八表 tenant-safe 持久化、TEN-001～003/005～008、TEN-004 数据层整批原子拒绝、内部边界稳定业务错误，以及 ADR-011 manifest/统一 CapabilityService/只读 API；共享能力 4 项、只读 API 1 项与设备侧 19 项测试 PASS，真实 PostgreSQL 八表 fixture 残留为 0。migration 与 rollback 设计已更新为 0.2.0，ADR-013 1.5.0/ADR-014 1.3.4（**Accepted**，2026-08-08）：受控 runner（history + SHA-256 + advisory lock + 两阶段先校验后执行）、五步骤链 M05 → M15 → M16 → V001 → V002（M05 幂等表正式落库）、roles 候选与事件 V1 Schema/fixture（strict+ajv-formats 4/4 PASS）已形成；DBA/架构专项 19 项发现全部处置，演练证据（签名漂移、MIG-005 双向、备份/恢复、回滚、M05 建模）全部 PASS，未在任何共享/生产库执行 DDL。ADR-012 1.0.2 已 Accepted，整体仍为 OPEN_REMEDIATION_REQUIRED。**ADR-013 1.5.0 / ADR-014 1.3.4 已 Accepted**（三项人工闭环全数关闭：双签、压测豁免、画像生产重跑 PASS + owner 指定，评审报告 §8.7）。TD-005 冻结四批实现证据已落地：Schema 归位 + Envelope + Inbox/Outbox 领域逻辑（31 测试）、CI 事件合同门禁（24 项 PASS）、持久化接线（入列服务/发布器/Inbox 写入/JDBC/Kafka 装配，23 测试）、消费循环（codec/处理器注册表/消费编排器/@KafkaListener 薄适配/装配配置，18 测试）、发布器调度驱动（Spring Scheduling 选型 owner 裁定，fixedDelay 轮询 + 显式 @EnableScheduling，3 测试）；设备域全量回归 169/169 PASS。下一步：TD-001 collector 业务处理器接入注册表、V001 获批窗口落库后的 JDBC 真实库合同测试（TD005_PG_ENABLED 模式）、双发对账演练；全部门禁通过后更新资产 manifest 真实 Git commit/hash，再决定 TD-005 是否转 Approved / Frozen。
