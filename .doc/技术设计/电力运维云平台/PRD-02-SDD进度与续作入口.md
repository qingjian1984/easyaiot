# PRD-02 SDD 进度与续作入口

> 版本：0.17.0
> 日期：2026-08-26
> 状态：Design In Review / Implementation Gated
> 双基线：[平台功能计划 1.5.0](../../架构设计/平台功能计划.md)、[EasyAIoT 项目开发宪法 1.6.0](../../开发规范/EasyAIoT项目开发宪法.md)
> 需求包：[PRD-02 监控告警与安全控制](../../需求包/电力运维云平台/PRD-02-监控告警与安全控制/README.md)
> 最近归档：`d37054a41 docs(prd-02): prepare G2-03 protocol evidence gate`

## 1. 当前结论

PRD-02 1.2.2 已完成基线修订；P02-M2-01 无副作用合同保持本地验收结论。2026-08-25 已完成 [P02-M2-02 专项冻结评审](./P02-M2-02-专项冻结评审记录.md)、02A review-only runner 静态验收、02B 本地无 transport 验收、02C 来源迁移冻结、[02C0 本地静态验收](./P02-M2-02C0-本地验收记录.md)、[P02-M2-02R1 整改专项冻结](./P02-M2-02R1-Inbox与动作序号整改专项冻结评审记录.md)、[R1A 本地无数据库验收](./P02-M2-02R1A-本地验收记录.md)、[C1 前置门禁核对](./P02-M2-02C1P-C1来源接入前置门禁核对记录.md)、[C1P 架构决策交付评审](./P02-M2-02C1P-架构决策交付与门禁评审记录.md)和 [C1P-G1 本地合同验收](./P02-M2-02C1P-G1-本地合同验收记录.md)。[ADR-019 0.2.0](../../架构决策/电力运维云平台/ADR-019-告警来源身份周期与补偿接入.md) 保持 Proposed；rule/version/cycle、历史 route 和 source Outbox 三个仓库内设计包已关闭，DEVICE_EVENT 产品 allowlist、legacy severity/activation、projection event 必需字段及设备协议原始 identity/time 仍等待责任人输入。真实数据库调用、真实 DDL 和生产 transport 仍为 0，C1 adapter、物理 transport、双写、backfill、对账、切读、capability 和生产容量继续关闭。

C1P-G1 的 10 个设计、Schema 与验收文件已归档于提交 `b7d0554ee`。其后的 `32fbc62c7` 是 M1-LC-02 独立测试进度，不改变本文件记录的 PRD-02 门禁、授权边界或下一步；当前没有新增 C1 实现授权。

2026-08-26 初始仓库证据复核形成 C1P-G2 输入单 0.1.0；当前已演进为 [0.4.0](./P02-M2-02C1P-G2-责任人输入收集与建议处置单.md)。初始检查点 `528e93736` 不包含四类责任人签署；其后只有 G2-01 获得部分可接受签署。输入包只把仓库事实、推荐选择、必交证据和签署顺序固化，不把推荐值、合同或验收工具准备视为批准，不解锁 C1A。

G2-01 产品签署已在提交 `d7d77d5c6` 归档。Sol 复核后接受 severity、范围、disabled、隔离和 activation 语义，但 activationAt 仍为 `PENDING-PREVIEW`，且签署对象把实际输入单 `0.1.0` 写成 `1.0.0`，因此状态为 `PARTIAL-ACCEPTED / SIGNED-PENDING-ACTIVATION`，而非完全关闭。提交 `1cb25b730` 已归档 [G2 输入处置记录](./P02-M2-02C1P-G2-输入处置记录.md)和 [G2-02 投影事件 Owner 确认单 0.1.0](./P02-M2-02C1P-G2-02-投影事件Owner确认单.md)，提交 `d37054a41` 已归档 G2-03 evidence Schema、DRAFT 模板和 owner 确认单。本轮继续补齐 fixture v1 Schema 与本地语义验收器；当前所有 direct DEVICE_EVENT 协议仍为 DISABLED，C1A 继续关闭。

## 2. 文档链

| 层级 | 文档 | 当前状态 |
|---|---|---|
| PRD | [PRD-02 1.2.2](../../产品需求/电力运维云平台/PRD-02-监控告警与安全控制.md) | Approved / Baselined |
| Spec | [SPEC-005 0.1.2](../../规格/电力运维云平台/SPEC-005-复合告警规则与状态机.md)、[006 0.1.2](../../规格/电力运维云平台/SPEC-006-值班通知与告警升级.md)、[007 0.2.0](../../规格/电力运维云平台/SPEC-007-安全遥控与操作票.md)、[008 0.2.0](../../规格/电力运维云平台/SPEC-008-事故追忆与视频证据.md) | In Review；评审建议已处置但未冻结 |
| Spec 评审 | [M2-M3 专项评审记录](../../规格/电力运维云平台/M2-M3-SPEC评审记录.md) | Conditional Pass for TD Drafting |
| ADR | [ADR-010 1.1.0](../../架构决策/电力运维云平台/ADR-010-统一告警模型迁移.md) | 下游设计方向已复核；DDL 未批准 |
| SDD 评审 | [评审报告 1.0.0](../../开发规范/PRD-02-SDD方案设计评审报告.md)、[处置记录 1.0.0](../../开发规范/PRD-02-SDD方案设计评审处置记录.md) | 12 项已处置；实现门禁未自动关闭 |
| C1P 评审 | [C1P 前置核对与决策任务单评审报告 1.0.0](../../开发规范/PRD-02-C1P前置核对与决策任务单评审报告.md)、[C1P 评审处置记录 1.0.0](../../开发规范/PRD-02-C1P评审处置记录.md) | PASS；D-01/D-02 随 ADR-019 起草关闭，D-03 已关闭；C1P 可执行，无门禁变化 |
| C1P 决策 | [ADR-019 0.2.0 Proposed](../../架构决策/电力运维云平台/ADR-019-告警来源身份周期与补偿接入.md)、[交付与门禁评审 1.0.0](./P02-M2-02C1P-架构决策交付与门禁评审记录.md) | Proposed；D-01/D-02 文档闭环，四项实质门禁仍 OPEN，ADR README 未更新 |
| C1P-G1 | [任务单 0.2.0](./P02-M2-02C1P-G1-来源合同缺口收敛任务单.md)、[本地合同验收 1.0.0](./P02-M2-02C1P-G1-本地合同验收记录.md) | 三个设计包关闭；allowlist Schema 通过但生产 mappings=0；等待四类责任人输入 |
| C1P-G2 | [输入单 0.4.0](./P02-M2-02C1P-G2-责任人输入收集与建议处置单.md)、[处置记录 0.3.0](./P02-M2-02C1P-G2-输入处置记录.md)、[G2-02 Owner 确认单](./P02-M2-02C1P-G2-02-投影事件Owner确认单.md)、[G2-03 Owner 确认单](./P02-M2-02C1P-G2-03-设备事件协议证据与Owner确认单.md) | G2-01 部分接受、待 activation/勘误；G2-02 待 M1 owner；G2-03 本地验收器已准备但全部协议关闭；C1 关闭 |
| TD | [TD-006 0.1.4](./TD-006-统一告警与规则引擎.md)、[007 0.1.2](./TD-007-通知与升级编排.md)、[008 0.2.0](./TD-008-安全遥控与操作票.md)、[009 0.2.0](./TD-009-事故证据与媒体归档.md) | In Review；TD-006 仅局部条件冻结；TD-008 Safety Hold |

## 3. 已冻结的产品/架构方向

- `iot-device` 是统一告警和安全遥控唯一责任模块。
- 告警主状态与升级级别正交；紧急告警永远不能忽略。
- 维护模式不停止规则和告警事实，只抑制受策略控制的通知，结束不补发过期通知。
- 通知平台提交与渠道真实送达分开；媒体/通知失败不回滚告警。
- 高风险遥控始终要求操作票、独立审批、联锁、二次确认、回执和审计；未知动作 fail-closed。
- standard/full 共用合同与安全语义；mini 无电力 API、任务、消费者和初始化残留。

## 4. 可由仓库内工作关闭的门禁

1. 精确盘点旧告警表、Topic、消费者、API、控制下行和媒体路径。
2. 形成候选 DDL、中文注释、事件 Schema、Inbox/Outbox 和 migration/rollback 脚本，先做静态评审，不执行。
3. 建立状态机、事件合同、幂等、租户隔离、mini 无残留和旧入口阻断测试矩阵。
4. 形成 standard/full 可重复压测方案和本地基准；生产规格仍需目标规模输入。
5. 将旧调用方、旧 ID、双写与对账规则固化到迁移 Runbook。

## 5. 需要外部责任人输入的门禁

| 输入 | 责任角色 | 缺失时行为 |
|---|---|---|
| 站点/测点/规则/告警/通知/媒体目标规模 | 产品负责人、运维负责人 | 使用协议硬上限做保护，不声明生产容量 |
| 短信/电话/企业微信供应商、回执和验签 | 集成负责人、安全负责人 | 相应 adapter 禁用，不承诺送达 |
| 受控设备、动作风险、联锁、票据和回执清单 | 电气安全负责人、现场负责人 | `power.control.safe=false`，禁止真实遥控 |
| 证据保留、隐私、legal hold、对象锁/WORM | 合规/安全/部署责任人 | 完整事故冻结禁用，不宣称法定不可篡改 |
| PRD-03 基线化的视频/SCADA 体验 | 产品负责人 | 仅后端证据合同，不开发完整交互 |

## 6. 实现切片与门禁

| 切片 | 范围 | 解锁条件 |
|---|---|---|
| [P02-M2-01](./P02-M2-01-告警领域合同与状态机任务单.md) | 告警领域合同、状态机与纯单元测试 | **Implemented / Verified-Local**；[33 个测试及编译证据](./P02-M2-01-本地验收记录.md)通过，未启用功能 |
| [P02-M2-02](./P02-M2-02-专项冻结评审记录.md) | 告警 DDL、Inbox/Outbox、来源适配和迁移对账 | Conditional Freeze / Local Tasks Only；生产迁移、来源适配和切换仍关闭 |
| [P02-M2-02A](./P02-M2-02A-告警迁移资产与临时库合同任务单.md) | V011/U011 review-only runner 与临时库合同 | **Implemented / Static-Verified**；[58 项验收](./P02-M2-02A-本地静态验收记录.md)通过，临时库合同 NOT RUN，生产执行禁止 |
| [P02-M2-02B](./P02-M2-02B-告警持久化与可靠事件任务单.md) | 正式 Schema、持久化端口、同事务编排与 Relay fake | **Implemented / Verified-Local / No Production Transport**；[71 项 Java、182 项 Ajv 与编译证据](./P02-M2-02B-本地验收记录.md)通过 |
| [P02-M2-02C](./P02-M2-02C-来源接入与迁移切换专项冻结评审记录.md) | 五类来源、旧读写/通知旁路、双写对账和切读阶段冻结 | **Conditional Freeze / Repository Inventory Complete**；C1-C4 仍关闭 |
| [P02-M2-02C0](./P02-M2-02C0-告警来源清单与迁移防漂移门禁任务单.md) | 机器可读来源清单与无依赖静态防漂移校验 | **Implemented / Verified-Local / Inventory Guard Only**；[461 项正例、16 类反例及 C1 门禁表](./P02-M2-02C0-本地验收记录.md)通过，C1 仍关闭 |
| [P02-M2-02C1P 核对](./P02-M2-02C1P-C1来源接入前置门禁核对记录.md) | M1 与 C1 五项前置门禁的已提交仓库事实复核 | **Verified-Repository / C1 Still Closed**；M1 合同基础关闭，其余四项 OPEN |
| [P02-M2-02C1P 任务](./P02-M2-02C1P-来源身份周期与补偿决策任务单.md) | 来源 identity/site、阈值 cycle、设备事件映射和旧写补偿决策 | **Executed / ADR Proposed / Prerequisites Open**；不授权 adapter 或生产代码 |
| [P02-M2-02C1P-G1](./P02-M2-02C1P-G1-来源合同缺口收敛任务单.md) | rule revision、产品 allowlist、历史 route/identity 和 source outbox 前置合同 | **Executed-Repository / G1-EVENT Waiting External**；实现、DDL、数据库执行关闭 |
| [P02-M2-02C1P-G2](./P02-M2-02C1P-G2-责任人输入收集与建议处置单.md) | 四类责任人输入的仓库事实、推荐选择、签署字段和关闭顺序 | **G2-01 Partial-Accepted / G2-02 Prepared / G2-03 Local-Verifier-Prepared / C1 Closed**；不得把验收工具或部分签署解释为实现授权 |
| [P02-M2-02R1](./P02-M2-02R1-Inbox与动作序号整改专项冻结评审记录.md) | Inbox 三态/重试责任与动作序号架构整改 | **Conditional Freeze / R1A Verified-Local**；N-01/N-03 文档与 N-02 本地代码已关闭 |
| [P02-M2-02R1A](./P02-M2-02R1A-独立动作序号与并发合同任务单.md) | `last_action_sequence` 候选 DDL、原子分配端口、来源事务和本地并发合同 | **Implemented / Verified-Local / No Database Execution**；[60+20+74 项证据](./P02-M2-02R1A-本地验收记录.md)通过 |
| P02-M2-03 | 值班、站内信/APP、升级调度 | SPEC-006/TD-007 冻结；外部渠道可继续关闭 |
| P02-M2-04 | 时间线、遥测窗口与基础证据索引 | SPEC-008/TD-009 基础证据范围冻结 |
| P02-M3-01 | 安全遥控框架与旧入口阻断 | SPEC-007/TD-008 冻结且安全评审通过 |
| P02-M3-02 | 真实设备控制与完整事故联动 | 现场签字、隔离测试台、PRD-03 和全部故障演练完成 |

所有实现切片必须由 Sol 冻结边界和验收，边界清晰的编码/测试交给 GPT-5.6 Luna max，并由 Sol 独立复核。未解锁切片不得提前编码。

## 7. 下一步

1. 下一步由设备协议 owner 从 [G2-03 DRAFT 模板](./assets/c1p-g2/device-event-protocol-evidence-template.json)复制独立证据文件，按 [fixture v1 Schema](./assets/c1p-g2/device-event-protocol-fixture-v1.schema.json)提交至少一个 direct 协议的脱敏 raw capture、identity/time/fixture/commit 后 ACK 证据，运行 `pnpm verify:device-event-protocol-evidence -- --file .doc/.../OWNER_EVIDENCE.json` 后签署；当前所有既有协议保持 DISABLED，G2-04 保持 DENY_ALL。
2. M1/TD-003 owner 填写并签署 [G2-02 确认单](./P02-M2-02C1P-G2-02-投影事件Owner确认单.md)，明确发布历史、v1/v2、版本化 Schema/API 与测试计划；产品规则 owner 同时提交 G2-01 输入单版本勘误。activationAt 只能在迁移预览清单冻结后补签，不得预填；当前不得创建 C1A。
3. P02-M2-02A 临时 PostgreSQL 合同保持 `NOT RUN`；只有决策所有者另行明确授权新建、唯一前缀、用后销毁的隔离实例后才可执行，目标/共享库始终不在授权内。
4. 临时库授权前不得读取 `.env` 凭据、调用 Docker、连接既有 PostgreSQL 或执行 V011/U011；02B JDBC 真实并发/约束集成继续为 OPEN。
5. ADR-019 Proposed 不解锁 C1；四项 C1 门禁全部达到 `CLOSED-CONTRACT`、ADR 经决策所有者 Accepted 且同提交更新索引前，C1-C4 不得实现。
6. TD-008、外部通知和完整事故冻结继续关闭。
