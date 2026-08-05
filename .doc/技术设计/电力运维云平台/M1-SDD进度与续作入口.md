# M1 SDD 进度与续作入口

> 检查点日期：2026-08-05  
> Git 分支：`cfdqiot`  
> 当前阶段：Technical Design 进行中  
> 说明：本文件用于下次会话恢复上下文；状态以各正式文档为准

## 1. 当前基线

| 文档 | 版本 | 状态 |
|---|---:|---|
| 平台功能计划 | 1.4.0 | 当前产品基线 |
| EasyAIoT 项目开发宪法 | 1.4.0 | 当前开发治理基线 |
| PRD-01 站点设备与数据采集 | 1.2.0 | Approved / Baselined（M1） |
| SPEC-001～004 集合 | 1.4.0 | Approved / Frozen |
| ADR-001～011 | 当前索引基线 | Accepted |
| [TD-001 collector 与 NODE 部署契约](./TD-001-collector与NODE部署契约.md) | 1.0.4 | In Review |
| [TD-002 SQLite Outbox 与恢复迁移](./TD-002-SQLite-Outbox与恢复迁移.md) | 1.0.2 | In Review |
| [TD-003 遥测 Inbox、ACK 与时序投影](./TD-003-遥测Inbox-ACK与时序投影.md) | 1.0.1 | In Review |
| [TD-004 电力对象、别名、二维码与历史编码兼容](./TD-004-电力对象别名二维码与历史编码兼容.md) | 1.0.1 | In Review |
| [TD-005 物模型模板 Schema、版本差异与发布 API](./TD-005-物模型模板Schema版本差异与发布API.md) | 1.0.6 | In Review |
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
- TD-005 画像评审 R1～R7 已完成文档处置：画像 v1.1 增加七表列签名、双作用域重复、标识异常、结果 JSON Schema 与生产重跑契约；ADR-012 已建立但仍为 Proposed。
- TD-005 的 4 条孤儿属性处置已执行完成：执行前 precheck、动态12列、完整快照、最终删除回滚演练及修复后 rollback 恢复演练均 PASS；初始化 COPY 旧种子4→0，数据库显式提交4→0，修复后画像 `product_properties=17`、六类 orphan/七类重复组/六类标识异常全部为0，现行演示产品/设备/属性保持3/3/9。该存量子门禁 PASS。
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

- 本地目标集成实例画像、R1～R7 文档处置及4条孤儿属性清理已完成；仍需 ADR-012 转 Accepted、修正 Mapper/实体/VO，并通过唯一约束、租户 CRUD 和删除链合同；生产存量环境需按画像 Schema 重跑；
- 孤儿存量子门禁已 PASS，但单条/批量产品删除代码仍不完整，不得把数据清理等同于删除链修复；
- Draft 2020-12 资产级 fixture 已 PASS；仍需生产 Java/TypeScript 消费相同 JCS/hash golden，并补 Schema 外语义校验；
- 10 类模板、71 个属性、单位、三相、累计量、CT/PT 变比及高风险服务的行业专家复核；
- 发布不可变、SemVer、三方差异、全量错误、租户隔离、产品绑定事务和精确回滚测试；
- Excel 宏/公式拒绝、逐行错误、RTU 分区版本合同与现有非电力功能回归；
- manifest 仍为 `gitCommit=UNCOMMITTED`，评审冻结时必须写入实际提交并复算哈希。

## 5. 下次建议起点

继续 SDD 文档链，下一步优先执行 **TD-005 证据准备与冻结门禁关闭**：

1. 读取 [TD-005 1.0.6](./TD-005-物模型模板Schema版本差异与发布API.md)、[TD-005 评审报告 §18](../../开发规范/TD-005评审报告.md)、[修复后画像报告](./TD-005-目标数据库与现有实现画像报告.md)和[自动验证报告](../../规格/电力运维云平台/assets/model-templates/verification/README.md)；
2. 评审 ADR-012，冻结根属性/服务参数单一事实；
3. 修正 Mapper/实体/VO/statement 漂移，并建立租户 CRUD、唯一约束和单条/批量删除中途失败合同；
4. 在生产 Java/TypeScript 模块中消费现有 JCS/hash golden，并补成员唯一、SemVer、CT/PT 等 Schema 外语义合同；
5. 建立恶意 Excel/JSON 导入 fixture，验证宏、OLE、外链、Pivot、ZIP Slip/Bomb 和外部 `$ref` fail-closed；
6. 组织电力领域评审 10 类模板和点位字典，重点核对三相后缀、单位、累计量、CT/PT 变比和高风险服务；
7. 评审发布不可变、SemVer、三方差异、租户只读共享、产品绑定事务、升级回滚和 legacy 非电力兼容；
8. 门禁通过后更新资产 manifest 的真实 Git commit/hash，再决定是否转 Approved / Frozen 和拆分代码任务。

TD-005 评审可以与 TD-001～004 的证据准备并行，但任何生产代码不得绕过各 TD 的冻结门禁。

## 6. 下次恢复提示

可直接使用：

> 读取 `.doc/技术设计/电力运维云平台/M1-SDD进度与续作入口.md`，遵循《平台功能计划》和《EasyAIoT 项目开发宪法》，继续 TD-005。TD-005 1.0.6 已完成4条孤儿属性修复：初始化基线和目标库均为0，修复后六类 orphan 全部为0，该子门禁 PASS；整体仍为 OPEN_REMEDIATION_REQUIRED。下一步直接评审 ADR-012，再修正 Mapper/实体/VO、唯一约束、租户 CRUD 和删除链合同。
