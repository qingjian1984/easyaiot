# M1 SDD 进度与续作入口

> 检查点日期：2026-08-05  
> Git 分支：`cfdqiot`  
> 当前阶段：Technical Design 进行中  
> 说明：本文件用于下次会话恢复上下文；状态以各正式文档为准

## 0. 项目强制双基线

本项目所有需求、PRD、SPEC、ADR、TD、开发、测试、评审、发布和运维变更，必须同时依据：

- [平台功能计划 1.4.0](../../架构设计/平台功能计划.md)：产品范围、版本、部署档位、模块归属、里程碑和优先级基线；
- [EasyAIoT 项目开发宪法 1.4.0](../../开发规范/EasyAIoT项目开发宪法.md)：安全、架构、数据、兼容、开发流程、质量门禁和 DoD 基线。

不得只读取或遵循其中一份。下游文档、代码或既有实现与双基线冲突时，必须先停止开发并完成基线修订或 ADR 决策；未获得实际代码、测试、构建、数据库和运行证据的事项继续标记为 `OPEN`。

## 1. 当前基线

| 文档 | 版本 | 状态 |
|---|---:|---|
| 平台功能计划 | 1.4.0 | 当前产品基线 |
| EasyAIoT 项目开发宪法 | 1.4.0 | 当前开发治理基线 |
| PRD-01 站点设备与数据采集 | 1.2.0 | Approved / Baselined（M1） |
| SPEC-001～004 集合 | 1.4.0 | Approved / Frozen |
| ADR-001～012（ADR-005 Superseded） | 当前索引基线 | Accepted / Superseded |
| [TD-001 collector 与 NODE 部署契约](./TD-001-collector与NODE部署契约.md) | 1.0.4 | In Review |
| [TD-002 SQLite Outbox 与恢复迁移](./TD-002-SQLite-Outbox与恢复迁移.md) | 1.0.2 | In Review |
| [TD-003 遥测 Inbox、ACK 与时序投影](./TD-003-遥测Inbox-ACK与时序投影.md) | 1.0.1 | In Review |
| [TD-004 电力对象、别名、二维码与历史编码兼容](./TD-004-电力对象别名二维码与历史编码兼容.md) | 1.0.1 | In Review |
| [TD-005 物模型模板 Schema、版本差异与发布 API](./TD-005-物模型模板Schema版本差异与发布API.md) | 1.0.15 | In Review |
| [TD-005 运行模型兼容与删除链技术设计](./TD-005-运行模型兼容与删除链技术设计.md) | 0.1.8 | In Review |
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

1. 读取 [TD-005 1.0.15](./TD-005-物模型模板Schema版本差异与发布API.md)、[TD-005 运行模型兼容与删除链设计 0.1.8](./TD-005-运行模型兼容与删除链技术设计.md)和[TD-005 评审报告 §22](../../开发规范/TD-005评审报告.md)；
2. Mapper/DO/VO、legacy adapters、Java golden、根属性 TEN-001～004/006、内部八表持久化/TEN-005/回滚、稳定错误和 TEN-007/008 已完成；先复跑 capability 4 项、只读 API 1 项与设备侧 19 项目标测试，禁止回退到 `product_properties.service_id` 或散落 profile 判断；
3. 下一步先形成并评审版本/绑定/审计/`power_model_release_outbox` 的 migration/rollback 候选，明确事件 UUID、状态重试、审计最小字段和事务边界；批准前不得启用 DDL；
4. migration 候选评审通过后，实现审计/Outbox 原子合同，再设计公开 `/thingModel` 电力/非电力兼容接线、TEN-004 业务层整批不存在错误、旧缓存/Feign adapter、DEL-001～010 和 standard/full/mini 端到端回归；通过后再启用 unique/XOR/tenant FK/RESTRICT；
5. 在生产 Java/TypeScript 模块中消费现有 JCS/hash golden，并补成员唯一、SemVer、CT/PT 等 Schema 外语义合同；
6. 建立恶意 Excel/JSON 导入 fixture，并组织 10 类行业模板评审；
7. 所有门禁通过后更新资产 manifest 的真实 Git commit/hash，再决定 TD-005 是否转 Approved / Frozen。

TD-005 评审可以与 TD-001～004 的证据准备并行，但任何生产代码不得绕过各 TD 的冻结门禁。

## 6. 下次恢复提示

可直接使用：

> 读取 `.doc/技术设计/电力运维云平台/M1-SDD进度与续作入口.md`，遵循《平台功能计划》和《EasyAIoT 项目开发宪法》，继续 TD-005。TD-005 1.0.15 / 运行模型 0.1.8 已完成 12 表画像、非空 legacy golden、Mapper/DO/DTO/adapters、八表 tenant-safe 持久化、TEN-001～003/005～008、TEN-004 数据层整批原子拒绝、内部边界稳定业务错误，以及 ADR-011 manifest/统一 CapabilityService/只读 API；共享能力 4 项、只读 API 1 项与设备侧 19 项测试 PASS，真实 PostgreSQL 八表 fixture 残留为 0。ADR-012 1.0.2 已 Accepted，整体仍为 OPEN_REMEDIATION_REQUIRED。下一步先设计并评审版本/绑定/审计/Outbox migration 与 rollback 候选，批准后实现原子合同，再补 TEN-004 业务层错误并接公开模型接口；不得提前启用 DDL。
