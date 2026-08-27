# M1 SDD 进度与续作入口

> 检查点日期：2026-08-27
> Git 分支：`cfdqiot`（LC02 验收证据基线提交 `849f524eb`；本文件不宣称本地后续提交已经推送）
> 当前阶段：OPEN03 门禁 1/2/3/6 已 CLOSED-LOCAL；LC02A-0～4 与 M1-LC-02 全链已 Implemented / Verified-Local。R6 证据已由 Sol 接受并以提交 `849f524eb` 独立保存。M1-LC-03 的 `LC03-01` 共享 ACK V1 合同已由 Luna Max 实现并经 Sol 独立复验为 `COMPLETE / SOL-ACCEPTED`（23/23）；`LC03-02` 与 `LC03-03` 已冻结但未授权，下一步须独立授权 Luna Max 执行 LC03-02。正式 V009/V012 落库、生产 MQTT broker/ACL/TLS、`LC02-09-RUNTIME-01` 和发布/现场资格仍 OPEN / NOT APPROVED；TQ 查询/展示面已部署上线，MQTT→Inbox 真实容器链路已打通（见 2026-08-24 记录）
> 说明：本文件用于下次会话恢复上下文；状态以各正式文档为准

## 当前快照与下一步（2026-08-13）

### 会话暂停检查点（2026-08-14，下一次从此处续作）

- **决策状态**：ADR-017 1.0.0、ADR-018 1.0.0 已由决策所有者接受；LC02A-0～4 已 `Implemented / Verified-Local`；M1-LC-02 已 `Approved / Frozen`。
- **执行模型门禁**：当前环境已提供 `gpt-5.6-luna`；Sol 负责架构/范围收敛，本轮已将 OPEN-03 的有界接口、安全和联合审查/本地测试交给 Luna Max（max reasoning）执行。LC02A-0 早前由 Sol 按授权完成，未冒充为 Luna 执行。
- **仓库凭据治理**：`.gitignore` 已忽略 `NODE/agent.env` 及其本地变体，仅允许 `NODE/agent.env.example`；已执行 `git rm --cached NODE/agent.env`，当前索引为该文件的暂存删除，本地文件仍保留且不得提交或回显。
- **外部轮换现状**：控制面恢复后，正式 `reset-agent-token?id=1` 对平台节点返回业务拒绝；按授权采用受控 DB fallback。数据库、本地 `NODE/agent.env`、bootstrap 返回值一致，一次性 Agent register/heartbeat 均 `code=0`；旧值已失效且未进入索引。
- **安全约束**：不在文档、日志或交付说明中回显任何凭据值；仅保留布尔值、节点 ID 与测试结果作为证据。
- **本次完成门槛**：`.git/index.lock` 已清理，SQL 转储清理已暂存；cached/worktree secret scan 通过，`NODE/agent.env` 不在索引且保持 ignored。部署后/现场/7 天稳定性验证仍保持 `OPEN`。
- **人工执行约束（2026-08-16）**：当前仅进行本地开发与合同测试；Linux PTY 端到端、资源压测、Windows 发布资格及其他部署/现场验证暂不执行，保持 `OPEN`，待决策所有者明确要求并提供相应环境后再执行。
- **执行模型说明（2026-08-16 更新）**：当前环境已提供 Luna Max；本轮按 Sol 的边界拆解，将 TD-001 OPEN-03 的 1～3 审查与本地合同测试交由 Luna Max（max reasoning）执行。第 4 项运行证据按人工要求保持不执行。
- **OPEN03-04 已由 Sol 验收（2026-08-17）**：NODE collector 专用 HMAC deploy、通用入口 `UNSUPPORTED_GENERIC_DEPLOY` 精确早拒绝、WorkloadSpec/安装 capability allowlist、固定 Compose/关闭默认 Windows 计划已落地。Sol 首次否决 `brokerRef` 丢弃和 project 随 spec 漂移，Luna Max 修正后 Sol 独立执行冻结测试及 NODE 全量 26/26、Skipped=0，Schema 双副本 hash、`compileall`、`git diff --check` 与临时目录清理通过；未启动 Docker/PTY/压测/Windows/现场。
- **OPEN03-05 已由 Sol 验收、OPEN03-06 v2 已冻结（2026-08-17）**：NODE 已实现 ConfigSnapshot 1.1 严格接收、desired/active/observed/history 状态机、共同 workload 目录、原 canonical bytes、原子替换/恢复与 `02770/0660` 合同。Sol 独立冻结集 56/56 PASS、Skipped=0，Schema 长度 3853、hash `52FCC23AE0DF65BE19C902E604611A4078ABF9E89B13EF91E5DC05D088C7A28A`，`compileall`/`git diff --check`/临时清理通过。Sol 随后识别并修正“Compose 配置目录 `:ro` 但 Java 要写 active/observed”的跨进程冲突，冻结为精确单 workload `rw`、Agent/collector 正式文件分权、共同 record lock 和固定提交顺序。当前唯一授权 OPEN03-06 交 Luna Max；Linux 锁/owner/GID、PTY、压测、Windows 与现场仍不执行。
- **OPEN03-06 已由 Sol 验收、OPEN03-07 已解锁（2026-08-17）**：collector 本地 Provider、版本原子应用、唯一 RTU 引擎/center bridge 与 SQLite outbox 写入已落地。Sol 首轮否决全包扫描下的伪 profile 隔离，次轮否决测试强制非 Web而生产入口未固化的差异；最终以生产同款 collector CLI 启动真实 Spring 白名单上下文，确认无中心 DB/service/controller/message bus、Redis、Nacos/Feign，首次无配置写 `WAITING_CONFIG`。Sol 独立冻结集 Java 27/27、NODE 37/37、Skipped=0，三份 Schema hash、`compileall`、`git diff --check` 与临时清理通过。当前唯一授权 OPEN03-07；所有运行期资格继续 OPEN。
- **OPEN03-08A 及 S1 已由 Sol 验收，OPEN03-08 v2 已恢复（2026-08-17）**：OPEN03-08 首批实现暴露 TD-003 §13 批量 Store 与当前逐条代码事实冲突，Sol 否决错误断言后冻结 OPEN03-08A，Luna Max 已完成批量主接口、四状态逐条结果、projector 单次批量调用和两个 adapter 顺序隔离，旧 `writeSample` 仅保留 deprecated bridge。Sol 独立完整冻结矩阵 `34/34 PASS`、Skipped=0，真实 PostgreSQL 残留 0；之后发现并收敛 Apache HTTP DEBUG 凭据头泄露，S1 日志合同 `2/2 PASS`、TDengine 相关 `7/7 PASS`、敏感输出扫描 0 命中。OPEN03-08 v2 已恢复授权 GPT-5.6 Luna（max reasoning）；运行期资格继续 OPEN。
- **OPEN03-08 v2 验收命令全量执行通过，待 Sol 复核（2026-08-19）**：fixture、编排器与全部 5 个指定测试类经查已由前会话实现完毕（含 `CollectorCrossTdContractTest`/`CollectorHealthAggregatorTest`/`CollectorOpen03CombinedStageTest`/`CollectorOpen03CombinedApplyStageTest`/`test_open03_08_path_contract.py`），本轮零代码改动，只执行 13.6 节全部验收命令：组合编排器 E2E 成功链+失败链 PASS（无 skip）；iot-sink 9 类 `36/36`、iot-node 5 类 `32/32`、iot-device 2 类（真实 PG 实连）`8/8`，全部 Failures:0/Errors:0/Skipped:0；NODE pytest `58 passed`；三模块 `compile` exit 0、`compileall` OK、`git diff --check` OK。**本轮唯一修复为环境性阻塞**：iot-device 合同测试首跑 2 个用例报 `SERVICE_AUTH_UNKNOWN_CALLER`，隔离复现证明路由模板匹配正确，根因是本地 Maven 仓库 `iot-common-security` 旧版 jar 不含 8/17 的 `{releaseId}` 模板路由匹配；`mvn install -pl iot-common/iot-common-security -DskipTests` 刷新后 6/6 全绿，零源码改动（Sol 复核重跑时需 `-am` 全量构建或先 install 该模块，避免踩同样旧 jar）。后置检查：仓库无 key/replay DB/SQLite 生成物、PG `iot_collector_config_release` 0 行残留、生产开关默认关闭（`EASYAIOT_INTERNAL_SERVICE_AUTH_ENABLED:false`、`EASYAIOT_COLLECTOR_CONFIG_DISPATCH_ENABLED:false`）、临时文件已清。未执行运行项如实保持 OPEN（Linux PTY/串口/压测/7 天/Windows/现场）。按任务单约束未 commit。
- **Sol 最终复核并解锁 M1-LC-02（2026-08-20）**：当前 HEAD 重跑 iot-sink `36/36`、iot-node `32/32`、NODE `58/58`，合计 `126/126` 且 0 failure/error/skipped；OPEN03 pytest 历史残留与本轮临时目录均已清理。Docker daemon 当日未运行，iot-device 当前复跑为 6 个纯合同通过、2 个真实 PG 用例环境不可用；`48c446bf..HEAD` 证明相关 device/common-security/iot-node/NODE 代码自 2026-08-19 真实 PG `2/2` 证据后未变化，因此沿用该次真实证据，不把本次环境错误冒充通过。TD-001 门禁 1/2/3/6 转 `CLOSED-LOCAL`，4/5 保持 `OPEN-RUNTIME`；LC02A-0～4 转 `Implemented / Verified-Local`。ADR-017 已 Accepted，M1-LC-02 转 `Approved / Frozen`；Sol 将原 LC02-01 与 SQLite V3 的 LC02-03 合并冻结为不可拆的 expand→switch 包 `LC02-01A`，当前唯一授权 GPT-5.6 Luna（max reasoning）执行。V010 已批准先于 V009，后续 runner 依赖顺序固定 `V008 → V010 → V009`。
- **LC02-01A 首轮实现完成但 Sol 拒收（2026-08-20）**：Luna Max 已在 §12.2 白名单内完成 `TelemetryOutboxBatch`、Port 签名切换、center/collector 产品事实来源和 SQLite `user_version 2→3` additive migration；Sol 独立冻结集 7 类 `28/28`、Failures/Errors/Skipped=0，28 模块 compile `BUILD SUCCESS`，旧 List 写签名 Java 0 命中、`git diff --check` 无空白错误，Docker Client/Server 29.7.2 可用。复核发现跨产品同 ID 碰撞虽整批回滚，却返回空 `Success` 且 `collisionMessageIds()` 为空；正确表达 `COLLISION` 必须修改未列入白名单的 `AppendBatchResult.java`，触发 §12.5 停止条件。另缺 V2 非默认 state/attempt/lease 逐字段保持和 Unicode 不归一化的显式测试。当前授权暂停，首轮改动保留、未验收、未提交；下一步由 Sol 形成 `LC02-01A-R1` 最小重冻结并取得决策所有者授权，禁止提前进入 LC02-04～10。
- **LC02-01A-R1 已最小重冻结并获授权（2026-08-20）**：决策所有者授权下一步后，Sol 将修订严格限制为 `AppendBatchResult.Collision`、writer 碰撞返回及三类直接证据；Collision 只报告输入顺序首个碰撞 ID，返回前整批 rollback，Success/Envelope/hash/Schema/Topic/Claim/ACK 均不变。新增证据固定非默认 V2 state/attempt/lease 逐字段保持和 NFD/NFC 不归一化。当前唯一授权 GPT-5.6 Luna（max reasoning）执行 §13；LC02-04～10 继续锁定。
- **LC02-01A / R1 已由 Sol 最终验收（2026-08-20）**：Luna Max 完成类型化 `AppendBatchResult.Collision`、两类碰撞整批回滚和 V2 状态/租约及 NFD/NFC 补强证据。Sol 独立 clean reactor 冻结集 8 类 `31/31`，Failures/Errors/Skipped=0；28 模块 compile `BUILD SUCCESS`；writer 空 `Success` 碰撞返回与旧 List 写签名均 0 命中，`git diff --check` 通过。状态转 `COMPLETE / SOL-ACCEPTED`，当前实现授权归零；下一步由 Sol 细化并冻结 `LC02-04`，LC02-04～10 未冻结前不得交 Luna 实现。
- **LC02-04 已拆分，04A 已冻结并获授权（2026-08-20）**：Sol 依据 ADR-017 把历史回填拆为 04A 本地空身份库存只读导出、04B 中心权威唯一解析、04C 本地完整性验证与原子写回，隔离 SQLite 与中心数据库信任边界。当前仅授权 GPT-5.6 Luna（max reasoning）实现 04A：最多 500 个四元键一页、canonical JCS + SHA-256、read-only/query-only 一致性读取；禁止写库、迁移、产品推断、输出 envelope 或进入后续包。
- **LC02-04A 已由 Sol 最终验收（2026-08-20）**：Luna Max 在冻结白名单内完成四个不可变库存合同、SQLite read-only/query-only 导出器与直接测试，并以 Java UTF-16 collation 固定跨页顺序。Sol 独立 clean reactor 冻结集 4 类 `22/22`，Failures/Errors/Skipped=0；28 模块精确 compile `BUILD SUCCESS`；501 键的 500+1 分页、四状态、全列快照、JCS/SHA、NFD/NFC、稳定错误码全部有直接证据；生产导出器写 SQL 关键字 0 命中，`git diff --check` 通过。状态转 `COMPLETE / SOL-ACCEPTED`，当前实现授权归零；04B/04C 继续锁定。
- **LC02-04B 已冻结并获实现授权（2026-08-21）**：中心解析固定消费一个 04A inventory artifact，在单一 PostgreSQL `REPEATABLE_READ + readOnly` 事务中逐键核对 V003/V007 不可变发布单、V004 workload 投影与同租户 product 主数据；发布 payload SHA/长度/canonical、根身份和 device 唯一出现全部 fail-closed。输出为绑定源 inventory SHA 的 canonical manifest；任一键零/多匹配、投影漂移、产品缺失或摘要不符时整页 Rejected，禁止部分清单、DeviceDO/default/site 推断和任何数据库写。共享 DTO、中心 repository/resolver、fixture、稳定码及三组验收命令已冻结在 M1-LC-02 §16；当前唯一授权 GPT-5.6 Luna（max reasoning）执行 04B，04C 继续锁定。
- **LC02-04B 已由 Sol 最终验收（2026-08-21；双基线：开发宪法 v1.6.0 / 平台功能计划 v1.5.0）**：Luna Max 已完成 manifest/result 不可变合同、中心只读事实 repository、`REPEATABLE_READ + readOnly` resolver 与 rollback-only PostgreSQL fixture；Sol 收紧精确身份、整数版本、摘要格式及长度证据后，在隔离副本独立执行设备合同 `21/21`、iot-sink 回归 `13/13`、真实 PostgreSQL `5/5`，全部 Failures/Errors/Skipped=0，33 模块精确 compile `BUILD SUCCESS`。真实库覆盖两版 payload、合法/非法发布状态、跨租户产品、projection 三类漂移、摘要、表级长度约束、根身份和 device 缺失，五张 fixture 表运行前后均为 0 残留；两项生产边界扫描 0 命中，`git diff --check` 通过。状态转 `COMPLETE / SOL-ACCEPTED`，当前实现授权归零；04C 与 LC02-05～10 继续锁定。
- **LC02-04C 已由 Sol 拆分并冻结（2026-08-21；任务单 0.9.0）**：04C 固定按 `04C-1 来源认证 → Sol 复核 → 04C-2 离线写回` 顺序执行。04C-1 使用 Java 17 JCA Ed25519、域分离 canonical authorization、`keyId/operationId/24h` 时效和 manifest/inventory/workload 三重绑定，中心只持私钥、collector 只持公钥，禁止复用 ADR-018 两类 HMAC。04C-2 仅在 collector 停机并取得 TD-002 同一 `collector-outbox.lock` 后，对一页最多 500 键执行单事务产品列写回与 operation/manifest 双断点；运行 outbox 必须先持锁再 migration/writer，失败、行数漂移或身份冲突不得部分提交。当前只完成 Sol 冻结，**实现授权仍为零**；下一步须决策所有者明确授权 04C-1 后才交 Luna Max。
- **LC02-04C-1 已获实现授权（2026-08-21）**：决策所有者明确授权 GPT-5.6 Luna（max reasoning）严格执行任务单 §17.2～§17.3 的共享 authorization 合同、中心 signer、collector verifier 与直接测试。04C-2、SQLite 写回、锁改造、部署/密钥 Provider 和 LC02-05～10 均未授权。
- **LC02-04C-1 已由 Sol 最终验收（2026-08-21；任务单 0.9.1）**：Luna Max 完成 Ed25519/JCS/SHA 域分离授权合同、中心 signer、collector verifier 与负向矩阵。Sol 首轮审查拒收未受信任 operationId 可在 Rejected 中回显的问题，最小 R1 改为只返回 canonical UUID/null，并补齐 algorithm/keyId/UUID/24h/300s 边界。Sol 独立 clean reactor 验证 device `10/10`（33 模块）与 sink `14/14`（28 模块），全部 0 failure/error/skipped；三目标 34 模块 compile 成功，两类边界扫描 0 命中，`git diff --check` 通过。状态转 `COMPLETE / SOL-ACCEPTED`，当前实现授权归零；04C-2 仍待单独授权。
- **LC02-04C-2 已获独立实现授权（2026-08-21；任务单 0.9.2）**：决策所有者独立授权下一步。Sol 依据双基线复核 §17.4～§17.5 无冲突，当前唯一授权 GPT-5.6 Luna（max reasoning）实现停机独占锁、SQLite V3 单事务产品身份写回、operation/manifest 双断点、DEGRADED 修复推进和冻结测试。LC02-05～10、Schema/user_version、在线 writer 命令、后台任务、配置/部署与全部运行期资格验证继续锁定/OPEN。
- **LC02-04C-2 已由 Sol 最终验收（2026-08-21；任务单 0.9.4）**：Luna Max 经 R1～R4 完成共享 apply result、离线 SQLite 单事务写回、运行 outbox 同锁生命周期、JCS 双断点/DEGRADED 恢复与冻结矩阵；Sol 已关闭结果码过宽、重叠锁 channel 泄漏、checkpoint canonical/时间矛盾、collision 优先级和测试覆盖问题。Windows 主机无权创建 fixture 符号链接后，改在隔离 WSL2 Linux Docker 临时容器执行完整冻结命令：8 类 `59/59`，Failures/Errors/Skipped=0，两个 db/lock symlink 生产拒绝断言均真实通过；28 模块精确 compile `BUILD SUCCESS`，两条生产边界扫描 0 命中，`git diff --check` 通过，临时容器已自动清理且未触碰既有服务。状态转 `COMPLETE / SOL-ACCEPTED`，当前实现授权归零，LC02-05～10 继续锁定。
- **LC02-05 已由 Sol 细化并冻结（2026-08-21；任务单 1.0.0）**：依据双基线与 ADR-017，将本包收敛为共享 `TelemetryRoute`、`ClaimedEnvelope` canonical 上行 Topic、SQLite 单 writer unfinished 路由查询及“applied ConfigSnapshot 路由 ∪ PENDING/IN_FLIGHT 路由”的无副作用 Provider。非法 Topic level 身份整批 fail-closed 且 writer 必须存活；禁止旧 `/telemetry/**` 回退、坏行跳过、Schema/Envelope/hash/ACK/MQTT/中心链路或自动配置扩展。文件白名单、停工条件、九类冻结测试和验收命令见任务单 §18。当前状态 `FROZEN / NOT-YET-AUTHORIZED`，须决策所有者独立授权后才交 GPT-5.6 Luna（max reasoning）实现；LC02-06～10 继续锁定。
- **LC02-05 已获独立实现授权（2026-08-21；任务单 1.0.1）**：决策所有者已在 §18 冻结后独立授权 GPT-5.6 Luna（max reasoning）严格执行 §18.1～§18.6。当前唯一实现范围为共享路由合同、canonical Claim、单 writer unfinished 查询、applied/outbox 路由并集及冻结测试；实现者不得 commit，遇到白名单外文件或停工条件必须交回 Sol。LC02-06～10 与全部运行期资格继续锁定。
- **LC02-05 已由 Sol 最终验收（2026-08-21；任务单 1.0.2）**：Luna Max 完成共享路由合同、canonical Claim、单 writer unfinished 查询与 applied/outbox 路由并集。Sol 首轮暂缓接受并要求 R1 关闭成功查询遗留读事务，同时补齐“解绑在途保留→终态移除”和多总线去重证据；修正后 Sol 独立 clean reactor 九类 `37/37`，Failures/Errors/Skipped=0，28 模块测试与独立 compile 均 `BUILD SUCCESS`，旧 `/telemetry/**`/新包 `/iot/**` 字面量扫描 0、Schema/migration 无变更、`git diff --check` 通过。状态转 `COMPLETE / SOL-ACCEPTED`，当前实现授权归零；LC02-06～10 继续锁定。
- **LC02-06 已由 Sol 细化并冻结（2026-08-21；任务单 1.1.0）**：中心入口冻结为 `06A iot-device 权威解析 provider/auth → 06B iot-sink parser/guard/subscriber`。现有 `limit 1` 查询不能证明唯一且禁止被新链路复用；新增 ADR-018 HMAC 保护的 `RESOLVED/NOT_FOUND/AMBIGUOUS` 专用内部合同，Topic 解析只由 `IotDeviceTopicEnum` 模板派生，中心按“Topic → Envelope device/tenant → 权威注册 tenant”固定顺序裁决，10 个稳定拒绝码明确 `FINAL/RETRYABLE` 且全部在 Inbox 前失败。文件白名单、停工条件、真实 PG fixture 与双模块验收命令见任务单 §19。当前状态 `FROZEN / NOT-YET-AUTHORIZED`，实现授权仍为零；subscriber 默认保持关闭，ACL/压力/索引画像/生产激活和 LC02-07～10 继续锁定/OPEN。
- **LC02-06 已获独立实现授权（2026-08-22；任务单 1.1.1）**：决策所有者已独立授权 GPT-5.6 Luna（max reasoning）严格按 `06A → 06B` 执行 §19.1～§19.9。当前唯一实现范围为 iot-device 受 ADR-018 保护的权威唯一解析合同/provider、iot-sink canonical Topic parser/Feign adapter/三方 ingress guard/subscriber 接线及冻结测试；不得 commit，受保护文件摘要必须保持，遇到 §19.7 任一条件立即停工。LC02-07～10、DDL/V009、ACK、拒绝审计、ACL、生产激活与全部运行期资格继续锁定/OPEN；完成后必须交 Sol 独立复核，不得自行标记接受。
- **LC02-06 Luna 实现交付与 R1（2026-08-22；待 Sol 复核）**：按 `06A → 06B` 顺序完成白名单内 authority API/provider/auth 与 iot-sink canonical Topic parser、签名 Feign adapter、三方 guard、拒绝分类和 fail-closed subscriber/config；R1 仅修改既有三个测试文件及本任务/SDD 证据，未修改生产代码、未新增测试类名、未 commit。06A 三类直接测试合计 **14/14**，其中真实 PostgreSQL fixture `1/1`，Failures/Errors/Skipped 均为 0；R1 直接覆盖 ADR-018 缺 Header、独立有效 key 的错 service、错 key、过期、重放、错签名、query 篡改，auth-before-route/service、401/400/500/503 稳定映射和 DTO 敏感类型排除。06B 六类直接测试合计 **26/26**，Failures/Errors/Skipped 均为 0；R1 记录 authority 调用次数并覆盖所有前置拒绝及错误 product→authority `NotFound`→精确 `NOT_FOUND`。非 clean 目标编译覆盖 **34/34** reactor 且 `BUILD SUCCESS`；§19.9 clean compile 本轮在 Windows 删除 `iot-sink-biz/target/classes` 时遭文件锁阻断，未出现源码编译错误。禁止边界扫描、Mapper `LIMIT 2` 检查、`git diff --check` 均通过；受保护 25 文件 manifest SHA-256 仍为 `3D557A19BD394FDCC062AAF645478CF6C87003F4A987E54786A8F30C05C8CD47`。真实 MQTT/ACL、DDL/V009、生产激活、压测、Linux PTY、Windows 发布资格、现场验证和 `LC02-06-RUNTIME-01` 继续 OPEN，等待 Sol 独立复核，不得标记 COMPLETE / SOL-ACCEPTED。
- **LC02-06 已由 Sol 最终验收（2026-08-22；任务单 1.1.2）**：Sol 独立审查生产 diff、白名单、认证前置顺序、权威唯一查询和十类拒绝语义，未发现生产缺陷或双基线冲突；R1 仅补强直接测试证据。Sol 独立复跑 iot-device **14/14**（真实 PostgreSQL `1/1`、Skipped=0、33 模块）与 iot-sink **26/26**（28 模块），合计 **40/40** 且 0 failure/error/skipped。Windows clean 在最后模块受外部文件锁阻断后，一次性 Linux Docker 隔离副本执行同目标 `clean compile` **34/34 BUILD SUCCESS**，容器自动清理；禁止扫描 0 命中、唯一查询保持 `LIMIT 2`、保护区 25 文件摘要仍为 `3D557A19BD394FDCC062AAF645478CF6C87003F4A987E54786A8F30C05C8CD47`，`git diff --check` 通过。状态转 `COMPLETE / SOL-ACCEPTED`，当前实现授权归零；真实 MQTT/ACL、生产激活、DDL/V009、压测、Linux PTY/锁互操作、Windows 发布资格、现场验证和 `LC02-06-RUNTIME-01` 继续 OPEN。
- **LC02-07 已由 Sol 细化并冻结（2026-08-24；任务单 1.2.0）**：只读核验确认 `iot-device20` 的 V008/V010 history 与 hash 正常且尚无产品身份列，同时发现首装权威基线 `iot-device10.sql` 缺少整个 `iot_sink` 迁移结构。§20 将本包冻结为 nullable `VARCHAR(128)` 单列 expand、V008+V010 精确依赖、同 ID/hash 幂等、无 history 既有列拒绝、U009 全 NULL+RESTRICT 临时回滚、中文 COMMENT gate、两个以上临时 PostgreSQL 数据库的迁移/全量恢复对照，以及仅限 V008/V010/V009 的全量基线与三条 history seed 同步。当前状态 **FROZEN / NOT-YET-AUTHORIZED**：没有创建资产、修改 runner/dump 或执行 DDL；须决策所有者再次独立授权后才可交 GPT-5.6 Luna（max reasoning），LC02-08～10 与正式落库继续锁定。
- **LC02-07 已获授权、完成并由 Sol 接受（2026-08-24；任务单 1.2.2）**：Luna Max 完成 V009/U009、runner、COMMENT gate、README/env、全量基线和 OPEN 窗口单核心实现，但两个 Luna Max 回合均在自动合同脚本阶段持续无响应；Sol 明示限制后接手，发现并修正全量基线缺两个主键及约束 catalog 不同形问题，补齐自动合同。PostgreSQL 18.4 最终 `79/79 PASS`、无 skip：依赖/漂移/既有列拒绝、成功迁移、重复 skip、旧行/约束/索引保持、COMMENT 正反例、U009 三矩阵、全量恢复、`iot_sink` 与 history 双结构指纹全部通过；14 份临时备份及所有临时库清理为 0。V009 SHA 为 `48416787b7fc886cc3274be53f3a38c60f9a9dd93ca205e3f0311d54a8eafbde`。目标 `iot-device20` 最终只读证明 V008/V010 未变、V009 history/列均为 0，正式窗口仍 OPEN / NOT APPROVED。状态转 `COMPLETE / SOL-ACCEPTED`，当前实现授权归零。
- **LC02-08 已由 Sol 细化并冻结（2026-08-24；任务单 1.3.0）**：依据双基线、ADR-017、已接受的 LC02-06/07 与 M1-LC-01，冻结 Topic 权威产品身份在全部前置校验后注入 `InboxEnvelope`、新写非空产品列、历史 NULL 六字段完全匹配时条件 UPDATE 一次补全、产品参与 duplicate/collision，以及默认 `READ_COMMITTED` 下五组同键并发胜负合同。只允许 4 个生产文件、7 个既有/新增直接测试、1 个隔离 PostgreSQL 编排脚本和两份进度文档；V009 迁移保护集聚合 SHA 固定为 `2ab39079fb84d8ba42b056cbbdc25685fc0461f82eda762565f620eada662adb`。验收只在 V008→V010→V009 唯一临时库执行，正式 V009 落库、ACK/审计/投影/Store、真实 MQTT/ACL 和运行期资格全部锁定。当前状态 `FROZEN / NOT-YET-AUTHORIZED`，实现授权为零；须决策所有者再次独立授权后才交 GPT-5.6 Luna（max reasoning）。
- **LC02-08 已获授权并由 Sol 接受（2026-08-24；任务单 1.3.2）**：Luna Max 完成四个生产文件及直接合同核心实现，在补齐测试后执行回合无响应；Sol 明示运行限制后接手 R1/R2 和独立验收，未替换为其他模型。最终产品身份仅在 Topic/device/tenant/authority 全部通过后注入，payload bytes/hash 不变；JDBC 新写非空产品，历史 NULL 仅在六字段完全相同时条件 UPDATE 一次补全，产品参与 duplicate/collision，缺列失败关闭。PostgreSQL 18.4 双夹具覆盖 §21.4 全部 15 项，八类 **49/49**、Failures/Errors/Skipped 均为 0；纯 JVM 六类 **25/25**，完整 28 模块 test-compile SUCCESS；临时库残留 0，13 文件迁移保护摘要仍为 `2ab39079fb84d8ba42b056cbbdc25685fc0461f82eda762565f620eada662adb`，V009 hash 未变。状态转 `COMPLETE / SOL-ACCEPTED (Verified-Local)`，当前实现授权归零；正式 V009 落库和运行期资格继续 OPEN，下一步由 Sol 冻结 LC02-09。
- **LC02-09 已由 Sol 细化并冻结（2026-08-24；任务单 1.4.0）**：只读核验确认当前 EMQX 5.8.7 仍为无 authenticator、`no_match=allow`、默认 `{allow, all}`，且中心所谓 shared filter 实为普通 `/iot/+/+/property/upstream/report`；NODE 已挂 broker secret 文件但 collector Java 尚未消费，多设备 workload 的账号/动态 ACL 签发仍无生产合同。§22 将本包严格收敛为：Java 侧固定 `$share/easyaiot-center-inbox-v1//iot/+/+/property/upstream/report` 并对中心 MQTT 身份 fail-closed；隔离真实 EMQX 5.8.7 用两个 collector、一个 center 非 superuser 账号证明 exact QoS1/non-retained publish、固定共享组 subscribe、跨设备/普通/其他组/ACK/legacy/系统 Topic 默认拒绝，以及零残留清理。只允许 3 个生产/配置文件、3 个测试文件、3 个隔离 EMQX 资产和两份进度文档；禁止触碰现有 broker、主 Compose、NODE/collector/ACK/POM/数据库。若 5.8.7 不能区分固定共享组与普通/其他组订阅，必须停工回 Sol。当前状态 **FROZEN / NOT-YET-AUTHORIZED**，实现授权为零；下一步须决策所有者再次独立授权 GPT-5.6 Luna（max reasoning），LC02-10、正式 V009 落库和生产凭据/ACL 运行期包继续锁定/OPEN。
- **LC02-09 已获独立实现授权（2026-08-24；任务单 1.4.1）**：决策所有者明确授权 GPT-5.6 Luna（max reasoning）严格执行 §22.1～§22.7。唯一范围为中心固定共享 filter/fail-closed、两个直接测试增量、新真实 EMQX 集成测试及三个隔离资产；必须保护 `TelemetryInboxAutoConfigurationTest.java` 的 LC02-08 既有脏改动，不得 commit。现有 broker、主/cluster Compose、NODE/collector/ACK/POM/数据库、V009 正式落库、LC02-10 与生产凭据/ACL 运行期包均未授权；交付只能标记“待 Sol 复核”。
- **LC02-09 已触发真实 Broker 停工条件（2026-08-24；任务单 1.4.2）**：两个 Luna Max 回合受运行时限制，仅有首批 parser/properties 增量延迟落盘；Sol 明示限制后在原白名单接管，补齐 Java 配置/测试与隔离 EMQX 资产。直接 JVM 合同 **11/11**、28 模块 reactor SUCCESS；隔离 `emqx/emqx:5.8.7` 真实 MQTT 矩阵发现 **12** 项，最终 **10 通过、2 失败、0 error、0 skipped**，失败均为固定共享组允许场景。EMQX AUTHZ 脱敏日志把客户端 `$share/easyaiot-center-inbox-v1//iot/+/+/property/upstream/report` 剥离为 real filter `/iot/+/+/property/upstream/report` 后才交 file authorizer，故精确共享 ACL 被拒；若允许 real filter，则普通订阅和其他共享组也无法由该 authorizer 区分。每轮 container/network/临时凭据残留均为 0，密码未写仓库/命令行/报告。该事实精确触发 §22.7 第一项，状态转 **STOPPED / BLOCKED-EMQX-SHARED-GROUP-AUTHZ / NOT ACCEPTED**，实现授权归零、LC02-10 不解锁；下一步必须由 Sol 冻结 `LC02-09-R1`，在“责任重划分”与“原始 SUBSCRIBE hook/plugin/exhook”之间形成决策并再次独立授权。
- **LC02-09-R1 责任重划分已接受并冻结（2026-08-24；ADR-017 v1.1.0；任务单 1.5.0）**：决策所有者选择“Broker 只约束 center 身份和 real Topic、共享组由 Java fail-closed 保证”。Broker 仍必须以非 superuser 独占主体只允许 QoS1 订阅 exact real filter，禁止 center 发布、其他 real filter 和 QoS0/2；Java 唯一允许固定 `$share/easyaiot-center-inbox-v1//iot/+/+/property/upstream/report`，普通/其他组/`$queue`/broad/缺身份或 key 在联网前失败。R1 只允许修改 `acl.conf`、真实 EMQX 测试和隔离脚本；修订矩阵显式把固定组、普通 real filter、其他共享组的相同 SUBACK 作为 EMQX 能力画像，同时保留 Java 负向合同、其他 real filter 默认拒绝和组内恰好一次。当前状态 **FROZEN / NOT-YET-AUTHORIZED**，实现授权为零；须再次独立授权 Luna Max 后才可修改和执行，LC02-10 与生产运行期包继续锁定。
- **LC02-09-R1 已获独立实现授权（2026-08-24；任务单 1.5.1）**：决策所有者明确授权 GPT-5.6 Luna（max reasoning）严格执行 §23。唯一可写实现范围为 `acl.conf`、真实 EMQX 集成测试和隔离脚本；§22 已通过的生产 Java、直接测试、`emqx.conf` 及全部运行期/生产资产保持只读，不得 commit。Luna 交付后只能标记“待 Sol 复核”，LC02-10、正式 broker/V009 激活和 `LC02-09-RUNTIME-01` 继续锁定。
- **LC02-09-R1 已由 Sol 最终验收（2026-08-24；ADR-017 v1.1.1；任务单 1.5.2）**：首个 Luna Max 回合无消息/无落盘，第二个回合完成三个白名单文件后在测试阶段无响应；Sol 明示运行限制并接管独立审查/验收，未替换或冒充 Luna。center ACL 最终只允许 QoS1 exact real filter，Java 仍逐字节固定共享组；EMQX 能力画像证明固定组、普通 real filter、其他共享组均真实 SUBACK，同时其他 real filter/QoS/发布默认拒绝，固定组两 client 消息不重不漏。Java **11/11**、28 模块 SUCCESS；真实 `emqx/emqx:5.8.7` **12/12**、Failures/Errors/Skipped=0；脚本语法、scoped diff、ACL/凭据扫描通过，container/network/临时目录/凭据残留 0。状态转 **COMPLETE / SOL-ACCEPTED（Verified-Local）**，实现授权归零；生产 broker 激活和运行期资格继续 OPEN。
- **LC02-10 已由 Sol 细化并冻结（2026-08-24；任务单 1.6.0 §24）**：本包是零实现关闭包，只允许执行既有回归并向任务单/SDD 回填证据。冻结六层门禁：device 定向纯合同、sink 定向纯合同、完整 `iot-sink-biz` 模块、V009 PostgreSQL **79/79**、Inbox PostgreSQL **49/49**、Java fail-closed **11/11** + 真实 EMQX **12/12**，以及受影响 reactor `test-compile`。保护集固定为迁移 13 文件、当前 Inbox/Outbox 25 文件、Store 9 文件、ACK/投影 5 文件和 EMQX 4 文件；任一漂移或测试缺陷必须停工回 Sol，禁止在本包内修代码。当前状态 **FROZEN / NOT-YET-AUTHORIZED**，Luna 执行授权仍为零；正式 V009、生产 broker/TLS、运行期资格继续 OPEN。
- **LC02-10 Luna Max 执行授权已获但运行时阻塞（2026-08-24；任务单 1.6.1 §24.9）**：决策所有者已独立授权执行 §24。首个 Luna Max 回合约 7 分钟、精简重试约 6 分钟，均未发送最小开工确认、无新测试报告/文件落盘且不响应状态请求；Sol 分别中断并确认未遗留新的 Maven 进程或交付文件。没有任何 §24.5 命令或 Tests/Failures/Errors/Skipped 可作为证据，Sol 未静默替换模型执行。当前为 **FROZEN / AUTHORIZED / EXECUTION-BLOCKED-LUNA-RUNTIME**，不是测试失败；后续须等待 Luna Max 恢复后重试，或由 owner 另行授权 Sol 接管。
- **LC02-10 Sol 接管后触发冻结停工（2026-08-24；任务单 1.6.2 §24.10）**：owner 已明确授权 Sol 接管 §24.5。联合 `test-compile` **34/34 reactor SUCCESS**，device 七类 **39/39**、0 failure/error/skipped；sink 二十九类实际执行 155 项，Failures=0、Errors=3、Skipped=0：一项缺失父目录 outbox 在构造期因 lock `NoSuchFileException` 提前抛 `OutboxUnavailableException`，两项 Windows symlink fixture 因主机无创建符号链接权限未进入生产拒绝断言。按 §24.7 立即停止，未继续完整 sink、PG 79/79、Inbox 49/49、Java 11/11 与 EMQX 12/12；本进程也缺两组 PG 密码环境变量。五组保护摘要与 R1 三单文件摘要均不变，禁止扫描 0 命中，无 `lc02*` 容器/网络或 Maven 残留，未修改实现。状态为 **STOPPED / NOT ACCEPTED**；下一步由 Sol 冻结 `LC02-10-R1`，先处置构造期合同漂移并确定 Linux Docker symlink 复验入口，取得独立授权和显式 PG 凭据后才能重跑。
- **LC02-10-R1 已由 Sol 冻结（2026-08-24；任务单 1.7.0 §25）**：Git 历史证明失败测试早于已接受的 04C-2 “构造时先锁→migration→writer”生命周期；R1 决定保留生产 fail-closed 顺序，只允许机械修订 `CollectorCrossTdContractTest` 的单一旧方法，直接断言构造期稳定错误、`NoSuchFileException` cause 和目录/DB/lock 零残留。全部纯 Java 验收统一进入本地精确 `maven:3.9.16-amazoncorretto-17-alpine` Linux 容器并禁网，sink 二十九类必须一次 **155/155**，两个 symlink fixture 必须真实进入生产拒绝断言，随后完整模块再跑；不得拼接 Windows/Linux 结果。完整重验开始前两组 PG 密码必须已由调用进程显式注入，任一缺失则整个 R1 NOT RUN。生产锁/迁移三文件、symlink 测试及 §24 全部保护摘要冻结；当前 **FROZEN / NOT-YET-AUTHORIZED**，没有测试或实现授权。
- **LC02-10-R1 Luna Max 授权已获但运行时阻塞（2026-08-24；任务单 1.7.1 §25.7）**：owner 已明确授权 §25.2。Sol 前检确认双基线和五个 R1 文件摘要均未漂移，并确认两组 PG 密码未注入，故只派发单测试修订与最小 Linux 直接测试。首个 Luna Max 回合约 3 分钟无开工确认、文件改动或测试报告且不响应状态请求；无历史精简重试仍无任何可观察进展，Sol 分别中断。测试文件仍为冻结 SHA，生产保护文件未变，未启动 PG/EMQX/完整验收。当前 **FROZEN / AUTHORIZED / EXECUTION-BLOCKED-LUNA-RUNTIME**；Sol 未静默替换或接管。
- **LC02-10-R1 §25.2 已由 Sol 接管实现并直接验证（2026-08-24；任务单 1.7.2 §25.8）**：owner 明确授权 Sol 后，只机械修订 `CollectorCrossTdContractTest` 的一个旧方法及 `NoSuchFileException` import，保持生产“构造先锁→migration→writer”不变；新断言覆盖构造期稳定错误、lock cause 路径与目录/DB/lock 零残留。精确 Linux Maven 17 镜像、禁网、`--rm` 下该类 **5/5**、0 failure/error/skipped，28 reactor BUILD SUCCESS，容器残留 0；生产保护文件摘要未变，测试新 SHA=`4CE83126...484C8`，scoped diff check 通过。两组 PG 密码仍未注入，故 §25.3～§25.4 完整 Java/PG/EMQX 验收全部 NOT RUN；状态 **IMPLEMENTED / DIRECT-TEST-PASSED / FULL-ACCEPTANCE-NOT-RUN**。
- **LC02-10-R1 完整重验在依赖 reactor 停工（2026-08-25；任务单 1.7.3 §25.9）**：owner 本回合显式允许从 `DEVICE/.env` 读取两组 PG 密码，Sol 仅检查非空且未回显；由于 PG 阶段前停工，密码未传给 PG/Maven/EMQX。三张镜像、五组聚合及九个 R1 关键摘要前检一致；Linux Docker 联合 `test-compile` **34/34 reactor SUCCESS**，device 七类 **39/39**、sink 二十九类 **155/155**，均 0 failure/error/skipped，两个 symlink fixture 真实执行。随后完整模块冻结命令在依赖 reactor `iot-common-web` 的 `DesensitizeTest.test` 出现 **1 Failure**（期望 `芋***`，实际 `B*********`），`iot-sink-biz` 尚未执行即 `BUILD FAILURE`。按门禁未继续 bash/PG 79/79/PG 49/49/Java 11/11/EMQX 12/12；停工后五组摘要一致、固定容器与两类数据库前缀残留 0。当前 **STOPPED / FULL-RETEST-FAILED / NOT ACCEPTED**；下一步由 Sol 只读分析测试确定性、`-am` 门禁范围和凭据来源例外，再决定是否冻结 `LC02-10-R2`，不得直接修测试或继续 §25.4。
- **LC02-10-R2 已由 Sol 冻结（2026-08-25；任务单 1.8.0 §26）**：双基线核对为宪法 v1.6.0 / 平台计划 v1.5.0。`BasicLab源码` 长度 10，现行 `ChineseNameDesensitize` 固定保留首字符，正确确定值为 `B*********`；R2 只允许把旧期望 `芋***` 机械改为该 literal，测试当前 SHA=`403B2D6...58EBEF`，三个生产脱敏文件与 §24/§25 全保护区冻结。`-am` 对依赖 reactor 的 `*Test` 明确继续构成完整门禁，禁止缩减命令或排除新失败。owner 的 `.env` 来源授权被收敛为仅本地例外：文件必须 Git ignored/untracked、两键唯一非空且不回显；所有 Maven 容器以仓库外空文件覆盖 `/workspace/DEVICE/.env`，只在 PG 两段前导入进程，`finally` 清除，且已在对话出现的值禁止生产复用、须外部轮换。完整验收必须从直接 1/1 后重新执行四个 Linux Java 阶段、bash、PG 79/79、PG 49/49、Java 11/11、EMQX 12/12、摘要/扫描/零残留。当前 **FROZEN / NOT-YET-AUTHORIZED**，未改测试、未运行验收；下一步须独立授权 Luna Max。
- **LC02-10-R2 单 literal 已落盘，Sol 接管在保护前检停工（2026-08-25 13:30；任务单 1.8.1 §26.6）**：Luna Max 只把 `DesensitizeTest` 的旧期望机械改为 `B*********`，新 SHA=`852A24BB...E2C7`，精确 diff 仅一处；两个 Luna 测试回合未启动 Docker或返回退出码，owner 随后于 13:30 授权 Sol 接管 §26.3～§26.5。Sol 前检确认 Docker Linux 29.7.2、三张冻结镜像、`.env` ignored/untracked 与凭据 `2/2`、四组生产聚合及全部关键单文件摘要均通过，但 LC02-07 迁移 13 文件当前聚合为 `fd59868a...76a3`，不等于冻结 `2ab39079...2adb`。HEAD `78939834` 的 V011 独立提交在 R2 白名单外修改 runner、COMMENT gate、env、README 四个受保护文件，精确触发 §26.5。直接 1/1、四段 Java、bash、PG 79/79、PG 49/49、Java 11/11、EMQX 12/12 全部 **NOT RUN**；无容器、数据库、遮罩文件或凭据变量残留，无 commit。收尾诊断误输出 `.env` 整体文件 SHA-256，虽未输出密码值/长度/部分字符/JDBC URL，也未注入子进程，但仍违反零 hash 规则并构成第二个独立停工条件；文档不复述摘要，外部轮换继续强制。最终状态还出现 WEB 与告警域范围外并行改动，Sol 未触碰或归属。状态 **STOPPED / PROTECTED-MANIFEST-DRIFT / NOT ACCEPTED**；下一步须先由 Sol 冻结 `LC02-10-R3` 的 V011/LC02-07 保护边界，未经独立授权不得继续验收或回退 V011。
- **LC02-10-R3 分层保护边界已由 Sol 冻结（2026-08-25；任务单 1.9.0 §27）**：逐行审查 HEAD `78939834` 确认 V011/U011 仅增加显式 review-only 支路，默认 `APPLY_STEPS` 仍精确止于 `V008 → V010 → V009`，V009 专属资产、首装 dump、precheck、init 与 79 断言合同在该提交中零 diff。R3 将旧 `2ab39079...2adb` 保留为历史证据但退出当前阻断：LC02/V009 专属 6 文件固定聚合 `3FD4294B...B45AA`，V011 专属 3 文件固定聚合 `30EC12B4...54D0`，precheck/init 2 文件固定聚合 `8D9AE0CF...D865`；runner、COMMENT、env、README、`iot-device10.sql` 五个共享文件改为开工/收工 manifest 相等，并由默认 dry-run/V009 runner 语义、P02 V011 静态 **58/58**（真实 DB/DDL=0）和真实 V009 **79/79** 证明不变量。完整重验仍须从直接 1/1、四段 Linux Java、PG 79/79、PG 49/49、Java 11/11、EMQX 12/12 全部从头执行；V011 真实数据库合同继续 NOT AUTHORIZED。R3 同时禁止对 `.env` 或秘密值计算/输出任何 hash。当前 **FROZEN / NOT-YET-AUTHORIZED**，本轮未运行测试、未读取 `.env`、未修改实现或脚本、未 commit。
- **LC02-10-R3 Sol 接管在完整 `-am` 门禁停工（2026-08-25；任务单 1.9.2 §27.7）**：两次 Luna Max 均无可观察工具进展，owner 随后书面授权 Sol 接管。双基线、Docker Linux 29.7.2、三张镜像、A/B/C、既有保护摘要和 `.env` 安全前检通过，凭据只报告 `2/2_PRESENT_REDACTED`；五个 `bash -n`、默认 V009 顺序、P02 V011 静态 **58/58**（真实 DB/DDL=0）、脱敏直接 **1/1**、34-reactor 编译、device **39/39**、sink **155/155** 全绿，Linux symlink fixture 无 skip。完整 `iot-sink-biz -am` 中历史 `DesensitizeTest` 已再次 1/1 通过，但 `CollectorOpen03CombinedApplyStageTest` 出现 **1 Error**：`COLLECTOR_CONFIG_PERMISSION_INVALID`；按 §27.6 立即停止，PG 79/79、PG 49/49、Java 11/11、EMQX 12/12 全部 NOT RUN。第一次收尾快照中 A/B/C 与既有保护摘要一致且 `sharedStart == sharedEnd == C0D28DB9...50DD0`，容器/网络/数据库前缀/遮罩/精确环境变量残留 0；初次回填后又观察到并行改动覆盖保护集 B 的 V011 与 P02 脚本，B 聚合变为 `1AB2C4FA...7520E`，构成第二个独立 STOP 条件。本轮没有修改或归属这些并行文件，无实现文件改动或 commit。状态 **STOPPED / FULL-RETEST-FAILED / NOT ACCEPTED**；下一步须 Sol 同时只读分析 standalone fixture 权限/生产 fail-closed 合同与保护集 B 的并行改动归属，再决定是否冻结新的有界修订包。
- **LC02-10-R4 已由 Sol 冻结（2026-08-25；任务单 1.10.0 §28）**：只读对照生产 Provider 与三类测试后确认，R3 Linux Error 是 `CollectorOpen03CombinedApplyStageTest` 的 standalone fixture 未像相邻测试一样设置目录 `02770`、`desired.json` `0660`，生产精确权限 fail-closed 合同正确且禁止修改。R4 唯一实现白名单为该测试文件的 standalone 方法，只增加 POSIX capability guard 并引用生产 mode 常量；staged E2E 分支、生产、POM、脚本、SQL 与配置均只读。修订后先在冻结 Linux Maven 17 容器执行直接 **1/1** 与相邻三类 **16/16**，再从零重跑 R3 全套 Java、PG 79/79、PG 49/49、Java 11/11、EMQX 12/12。并行 V011/U011/P02 属于 M2/P02 候选演进，不再绑定 LC02 历史固定摘要；LC02 仅要求其不进入默认迁移链、P02 静态报告正数 PASS 且真实 DB/DDL=0，并要求单回合 `p02Start == p02End`。当前 **FROZEN / NOT-YET-AUTHORIZED**，未改实现、未运行 R4 验收、未读取 `.env`、未 commit。
- **LC02-10-R4 Luna Max 执行在 V009 PostgreSQL 停工（2026-08-25；任务单 1.10.1 §28.6）**：唯一测试 diff 严格按 §28.2 落盘，新 SHA=`A5CA1634...7D46`；Linux 直接 **1/1**、联合 **16/16**、脱敏 **1/1**、34-reactor、device **39/39**、sink **155/155** 和完整 `-am` Tests=284 均 0 failure/error。完整模块唯一 skip 的 XML testcase 是允许环境禁用的 `EmqxTelemetryAclIntegrationTest`，Center 源码本身 7/7，Sol 复核后按 §24.4 接受该阶段 skip。第二轮五个 `bash -n` 通过，凭据只报告 `2/2_PRESENT_REDACTED`，随后 V009 PG 脚本退出 1；输出未保存且失败即停，当前失败阶段 `failure_detail_not_captured`。Inbox 49、Java 11、真实 EMQX 12 均 NOT RUN；V011/U011 未授权/未运行。shared/p02 前后相等、全部保护摘要、scoped diff 和零残留通过，未泄露秘密、未 commit。状态 **IMPLEMENTED / JAVA-PASSED / PG-FAILED / NOT ACCEPTED**；下一步须 Sol 冻结有界 PG 诊断重验包，不能直接重跑。
- **LC02-10-R5 已由 Sol 冻结（2026-08-26；任务单 1.11.0 §29）**：当前 HEAD `528e93736` 包含 R4 提交 `32fbc62c7`，工作树仅有范围外未跟踪 `DEVICE/.claude/`；双基线与 V009 脚本固定摘要未漂移。只读分析确认 V009 脚本无 `set -x`，凭据仅经进程环境传入，既有 `[PASS]/[FAIL]` 标签足以做阶段诊断。R5 是零实现续跑包：先复核全部 R4 保护与默认迁移隔离，再把 V009 单次输出写入仓库外临时日志；内存检查秘密/连接串禁出后，只报告退出码、PASS 数、最后 PASS 标签、首个 FAIL 标签和稳定脱敏错误类别，随后无条件删除日志与变量。V009 **79/79** 后才执行 Inbox **49/49**、Java **11/11**、真实 EMQX **12/12**；失败禁止重试或改脚本。R4 Java 证据仅在保护完全一致时复用。当前 **FROZEN / NOT-YET-AUTHORIZED**，未读取 `.env`、未运行 Docker/PG/Java/EMQX、未 commit。
- **LC02-10-R5 Luna Max 执行在 Inbox 计数/退出证据停工（2026-08-26；任务单 1.11.1 §29.6）**：双基线、镜像、全部固定/共享/P02保护和迁移隔离前检通过；V009 单次安全执行 **79/79**，秘密扫描、日志删除和数据库/变量清理均通过。Inbox 八类 Surefire 实际为 **50/50**、0 failure/error/skipped，但冻结要求 49，且 wrapper 顶层退出码未捕获，故 STOP，Java 11 与 EMQX 12 未运行。Sol 定位多出的 1 项为 `148ed68af` 已加入的 broker identity fail-closed 测试；八类源码和脚本与 R4 零 diff，脚本未硬编码 49，故计数是历史基线漂移。但 Surefire 结果不能替代编排顶层退出证据，Inbox 仍未接受。shared/p02 前后相等、全部摘要/扫描/零残留通过，范围外 P02 证据与 `.claude/` 未触碰，未 commit。状态 **STOPPED / INBOX-COUNT-AND-EXIT-EVIDENCE-DRIFT / NOT ACCEPTED**。
- **LC02-10-R6 已由 Sol 冻结（2026-08-26；任务单 1.12.0 §30）**：保留 §21.8 历史 49/49，不改写过去；当前/后继 Inbox 唯一口径修正为八类 **50/50**。新增脚本+八类测试九文件固定聚合 `CD989772...E9336A`，与既有全部固定/共享/P02保护叠加。保护一致时复用 R5 V009 **79/79**，禁止重跑；Inbox 只允许运行一次，wrapper 必须在同一进程/try作用域、日志删除前保存 `$LASTEXITCODE`，同时要求退出0、9个稳定PASS、0 FAIL、八类4/7/13/11/1/5/5/4及零残留。通过后才运行Java **11/11**、真实EMQX **12/12**和最终保护。当前 **FROZEN / NOT-YET-AUTHORIZED**，未读取 `.env`、未运行验收、未改实现/脚本、未 commit。
- **LC02-10-R6 Luna Max 执行并由 Sol 最终接受（2026-08-26；任务单 1.12.1 §30.6）**：首次 Luna 回合因用量限制在执行前失败，owner 再次授权同模型 max reasoning 后完成。全部保护和迁移隔离通过，`R5_V009_79_REUSE=ELIGIBLE`且未重跑V009；Inbox单次执行顶层退出0、秘密扫描通过、9 PASS/0 FAIL，八类 **50/50**；汇总器一次通配符误判通过精确报告名只读纠正，未重跑。Java **11/11**、真实EMQX **12/12**全部0 failure/error/skipped；shared/p02前后相等、九文件及全部摘要/扫描/零残留通过。Sol独立复核本轮Surefire与时间戳后，LC02-10及R1～R6、M1-LC-02全部 **COMPLETE / SOL-ACCEPTED（Implemented / Verified-Local）**。未改实现/脚本、未泄密、未commit；范围外P02/WEB/`.claude/`未触碰。
- **TQ 遥测查询 API + WEB 展示面编码侧完成，四决策点已锁定（2026-08-19，决策所有者批准）**：补齐「TelemetryStore 只有写入、无任何查询能力」的未排期缺口（PRD §4.4/4.5）。TQ-01 `TelemetryQueryPort`（api 模块独立 query 包，不动已冻结只写端口）+ PG 适配器（date_bin 聚合/DISTINCT ON latest/租户 fail-closed/配额前置：series ≤10、跨度 ≤31 天、pageSize ≤1000、累计 ≤100,000，稳定码 `TELEMETRY_QUERY_QUOTA_EXCEEDED`）；TQ-02 双 Store 补 quality/received_at（构造时列探测降级，V010 落库前后行为一致；`parseQuality` 入共享 `TelemetryValueCodec`，canonical payload 受控枚举缺省 GOOD）；TQ-03 Controller（raw/aggregate/latest/export，CSV 流式导出同配额）+ TDengine 适配器（INTERVAL 窗口；引擎差异处理：TAG 不能作 INTERVAL SELECT 列，聚合/latest 按 series 循环回填）+ gateway `/admin-api/telemetry/**` 路由 + `EASYAIOT_TELEMETRY_QUERY_ENABLED` 开关默认关；TQ-04 WEB 遥测历史页（多测点/粒度/聚合/echarts 对比/明细/CSV 下载）+ QualityTag（8 类质量码→PRD 文案色）+ MetricPicker + 前端静态路由。验收：查询套件 25/25 + store 回归 45/45 全绿（真实 PG + 真实 TDengine）、`vite build` 通过。**遗留边界如实记录**：Detail.vue/TingModelCardList 数据源切换待部署窗口（现在切会空白）；V010 runner 已接线（dry-run `08d809a4...`，V001~V008 hash 未漂移；临时库双向演练 apply/回滚通过，`iot-device20` 未触碰），正式落库待批准单。**四决策点经决策所有者确认（2026-08-19）**：① V010 先于 V009 落库（不同表对象无依赖）；② quality 维持 canonicalBytes 解析方案（TD-003 §6 payload 本含字段，Envelope V1 冻结不出 V2）；③ TDengine ALTER 复用适配器内置幂等 ALTER+列探测（真实实例合同已验证）；④ `/admin-api/telemetry` M1 走登录态+租户头，细粒度权限点列 M2。提交：`61ebb00b`/`95c55500`/`2eb7d50e`/`77a8ee5f`/`0223a3ee`。部署操作见 `TQ-部署窗口清单-20260819.md`。
- **MQTT→Inbox 全链路真实容器打通 + ADR-018 装配缺陷族修复（2026-08-24，TQ 部署延续）**：以「MQTT 上报→`telemetry_sample` 入库」为目标在真实容器全链联调，暴露并修复 **4 个同族 Spring 装配/绑定缺陷**（OPEN03/LC02 各包单测级验收覆盖不到，仅 verifier 全开的真实容器暴露；对 Sol 后续包的装配设计有直接参考价值）：① **Spring relaxed binding 剥离 Map 键冒号**——yaml `"iot-sink:authority-v1"` 绑定后实际键为 `iot-sinkauthority-v1`，`EnvironmentInternalServiceKeyProvider.findKey` 永远 miss→`SERVICE_AUTH_KEY_UNKNOWN`（隔离 Binder 复现实证）；修复为 miss 后回退去冒号键。② **Feign 子上下文条件评估**——`TelemetryDeviceAuthorityFeignConfiguration` 的 `@ConditionalOnProperty` 在子上下文读不到父上下文属性→signer interceptor 被跳过→请求裸发 `SERVICE_AUTH_MISSING`；去掉条件注解（configuration 仅被单一 @FeignClient 引用）。③ **子上下文 Bean 注入时序**——去条件后主上下文注入共享 `InternalServiceKeyProvider` 又 NoSuchBean；signer 改 Environment 直读 `key-references` 构造 provider。④ **`@ConditionalOnBean/@OnMissingBean` 用在组件扫描类上评估早于 autoconfiguration**——Recorder（`@ConditionalOnMissingBean`）曾致 iot-device 崩溃循环、两个内部 Controller（`@ConditionalOnBean(verifier)`）被跳过 404；修复为显式 `@Bean` 装配（新增 `CollectorConfigReleaseWiringConfiguration`）+ `@ConditionalOnProperty` 同开关。另：adapter 增加异常类型/脱敏 body 日志（原先静默吞为 Unavailable）；compose 补 `EASYAIOT_TELEMETRY_MQTT_HOST`（默认 emqx-server，容器内 localhost 指向自身曾令订阅从未成功）与 ADR-018 密钥两端透传。**端到端证据**：`center MQTT connected; exact shared subscription enabled`；authority Feign HMAC 通过 `RESOLVED tenantId=1`；手工 curl 双 query 顺序均 200；`e2e-mqtt-*` 入库 value=221.5 quality=GOOD（401 时代消息修复后自动补投，RETRYABLE 语义同步验证）；凭证数据保留库中。提交 `f3f1fe7e`。**运行期边界如实记录**：本验证仅覆盖 standard 档单容器、模拟 publisher（非真实 collector/EMQX ACL）、单条消息；`LC02-06-RUNTIME-01`（真实 MQTT/ACL/压测）与全部运行期资格继续 OPEN。

### LC-02 前置冲突门禁（2026-08-13，OPEN）

- **决策与前置关闭（2026-08-20 更新）**：决策所有者已接受 ADR-017 1.0.0 与 ADR-018 1.0.0，并授权凭据移除与外部轮换；M1-LC-02A-0～4 已按顺序完成并由 Sol 验收为 Verified-Local。

- **事实冲突**：ADR-003 与 TD-003 要求复用 `/iot/{productIdentification}/{deviceIdentification}/property/**`；现有可靠链路实现却使用 `/telemetry/**`，而且 Envelope、SQLite Outbox 与 PostgreSQL Inbox 均未保存 `productIdentification`。
- **处置**： [ADR-017](../../架构决策/电力运维云平台/ADR-017-遥测可靠链路Topic与产品路由身份收口.md) 已 Accepted，[M1-LC-02 任务单](./M1-LC-02-遥测Topic与产品路由身份收口任务单.md) 已于 2026-08-20 转 `Approved / Frozen`。Topic 必须回归既有 `/iot/**`；产品身份作为可靠路由元数据按 expand→backfill→enforce 持久化，禁止默认产品或旧 Topic 回退。
- **重新排序**：LC-02 仅收口产品路由身份和双端 canonical Topic；LC-03 才实现成功 ACK V1 与重启对账；LC-04 实现拒绝审计及 FINAL/RETRYABLE ACK。
- **Sol 收敛结果**：ConfigSnapshot 升级为 1.1 并由服务端固化产品身份；历史 V2 outbox 只接受中心受控清单回填；ACK 精确订阅集合取“已应用路由 ∪ 未终态 outbox 路由”；V009 使用可空 expand；安全证据由 broker ACL 与 center 注册事实校验分层提供。
- **前置事实已关闭**：TD-001 设计的 NODE `/workload/collector/config`、iot-node 发布单派发器和 collector 本地 `PollingConfigProvider` 已通过 [M1-LC-02A](./M1-LC-02A-Collector版本配置应用链任务单.md) 与 OPEN03-01～08A/S1 顺序落地并完成本地验收；运行期资格仍独立保持 OPEN。
- **安全事实**：现有微服务只透传用户/租户 Header，`iot-node` 还存在全路径放行；NODE 只有可重放的单值 Agent Token，且仓库基线中存在本地凭据文件。已新增 [ADR-018](../../架构决策/电力运维云平台/ADR-018-控制面内部服务与NODE请求认证.md)，把内部服务 HMAC 与节点 HMAC 分域，禁止 token-only 降级；凭据值不得在文档或输出中复述。
- **LC02 证据已提交，M1-LC-03 已由 Sol 冻结（2026-08-26）**：两份 LC02 进度文档已独立提交为 `849f524eb`。Sol 依据双基线、ADR-003/017、TD-002/003 和当前代码事实新增 [M1-LC-03 成功 ACK V1 与重启对账任务单](./M1-LC-03-成功ACK-V1与重启对账任务单.md)，将工作拆为 `LC03-01 共享 ACK 合同 → LC03-02 collector 精确订阅/成功应用 → LC03-03 center V012/即时发送/10秒扫描 → LC03-04 组合重启 E2E → LC03-05 回归收口`。ACK 仅对 Inbox `ACCEPTED_DURABLE/DUPLICATE` 发送；collision、拒绝审计及 FINAL/RETRYABLE ACK 继续锁在 LC04。2026-08-27，LC03-01 已由 GPT-5.6 Luna Max 在白名单内实现，Sol 独立复跑 3 类直接合同测试 23/23 全绿并接受；下一步须 owner 独立授权 Luna Max 执行 LC03-02。正式 V009/V012 落库与生产 broker/ACL/TLS 激活必须分别获得部署授权；`LC02-09-RUNTIME-01`、Linux PTY/锁互操作、资源压测、Windows 发布资格和现场证据继续锁定/OPEN。
- **历史更正**：下方既有记录中的 `/telemetry/ack/...` 只代表当时测试代码事实，不再是目标架构或后续实现授权。

**M1 采集主线编码侧 + 本地验证全部闭环；B 类部署前硬化完成（2026-08-13）**：设备/RS485 → Poller → SQLite Outbox → MQTT QoS1 → 中心 Inbox → TelemetryStore（standard PG / full TDengine）全链路代码实现 + 验证完成；4 项硬化（EMQX 凭证 / MQTT 配置块 / SHA-256 子表名 / 共享 codec + 补零契约修复）见下「编码侧硬化」段。

### 本会话（2026-08-13）成果（commit `1e31edba`～`543810f2`，均 push）
- PG Inbox + Store 合同测试 5/5（真实 `iot-device20`，修复 `updated_at_ms` NOT NULL + `message_id` UUID→VARCHAR(64)）
- TDengine Store 合同测试 3/3（真实 `iot_telemetry`，修复 REST URL 缺 db path；表名占位 `INSERT INTO ?` 实测工作）
- 构建治理根因修复：reactor compiler 锁 `--release 17`（治 `maven.compiler.source` 被覆盖致 source=1.8 静默漂移）
- center 侧端到端 MQTT 实测：paho → EMQX 1883 → CenterMqttInboxSubscriber → JdbcTelemetryInbox → ProjectionOrchestrator → JdbcTelemetryStore，`telemetry_inbox` COMPLETED + `telemetry_sample` value_numeric=225.5 + 端到端两层幂等
- CenterMqttInboxSubscriber host 硬编码 bug 修复（构造器 host/port 参数被忽略）

### 下一步（部署后运行时，已归此类）
- **collector 侧 outbox→publish 往返**：硬约束——collector 靠 modbus-rtu 轮询真实设备产数据，无设备则 outbox 空。需接真实设备或手动注入 outbox 记录验证 dispatch→publish→center 收。collector 侧 57 单元/合同测试已覆盖 dispatch/publish/状态机。
- **ACK 回环**：center → collector `/telemetry/ack/...`（CenterMqttAckPublisher），需 collector CollectorMqttAckSubscriber 在线。
- **7天稳定性**：M1 §10 验收，collector/center 容器部署后长期观察。

### 编码侧硬化（B 类，2026-08-13 完成；commit `17050dc3` / `7dc19930` / `22279a77`）
- ✅ B-1 center subscriber EMQX 凭证 + 配置块：新建 `TelemetryMqttProperties` @ConfigurationProperties（对称 CollectorMqttProperties），AutoConfiguration 改用 Properties 替代 @Value，subscriber 构造器加 username/password 条件注入；application.yaml 加 telemetry.mqtt 连接段（凭证环境变量占位），local/standard/prod 加 inbox+mqtt enabled=true（原靠命令行系统属性启用）
- ✅ B-2 collector MQTT 配置块：application-collector.yaml 加 `easyaiot.collector.mqtt`（Java 零改动，CollectorMqttProperties + VertxCollectorMqttPublisher 已支持凭证）
- ✅ B-3 TDengine 子表名 SHA-256：buildSubTableName 改 SHA-256 取前 8 字节 → 无符号 long（消除 `Math.abs(Integer.MIN_VALUE)` 负号边界缺陷，碰撞空间 2^32→2^64；碰撞本不影响正确性，message_id tag 兜底；TDengine 合同 3/3 仍绿）
- ✅ B-4 共享 TelemetryValueCodec + 修补零契约：两 store parseValue 去重 + 单例 ObjectMapper；value 缺失返 null（对齐 TD-003 §6 禁止补 0）；PG 侧 jdbc.update 传 null → value_numeric 写 NULL（修契约），codec 单测 6/6

> **TDengine 引擎限制（已知妥协）**：TDengine DOUBLE 列强制非空，spike 验证 `CREATE STABLE ... DOUBLE NULL` → syntax error，无法写 NULL。B-4 中 TDengine 侧 value 缺失时回退 0.0 + warn 日志（PG 侧已正确写 NULL）。代码注释 + 此处标注。

### 部署链路修复（V008 接入 runner，2026-08-13；commit `a21a93fb`）
- **问题**：V008（`iot_sink.telemetry_inbox` + `telemetry_sample`）此前只落了 SQL 文件、未接入 `td005_migration` runner，且错位放在 `steps/`（V 系列约定在 ASSET_DIR）。`install_linux.sh` 部署后 PG 表不建 → iot-sink 首次写 inbox 因表不存在失败（部署阻塞）。
- **修复**：V008 SQL 移到 ASSET_DIR（对齐 V001-V007）；runner 接入 8 处（`V008_SQL` 变量 + `APPLY_STEPS` + `step_sql_path` case + 事务型执行分支 + 帮助文本 4 处）；补 V008 落库窗口申请单（ADR-013 流程）。
- **验证**：`dry-run` 列出 V008（sha256=`693c0473...`）；本地 `apply --step V008` SUCCEEDED + history 落库 + `iot_sink` 两表建。
- **部署须知**：`install_linux.sh` 设计上**不自动跑受控 migration**（ADR-013）；部署流程需手动执行一次 `td005_migration.sh apply --step V008 --approval <生产审批单> --yes`（带 `BACKUP_DIR` 仓库外备份 + precheck）。申请单见 `.doc/技术设计/电力运维云平台/assets/td005-migration/V008落库窗口申请单-20260813.md`。

### 编码侧剩余潜在改进（仍未做，非阻塞）
- CenterMqttAckPublisher 已存在但**未装配**（TelemetryInboxAutoConfiguration 未建 bean）——ACK 回环启用时需补
- JdbcTelemetryStore.INSERT_SQL 未写 TD-003 §14 的 value_present/quality/value_text 列（设计 DDL 有这些列，当前 INSERT 未覆盖）——独立的字段覆盖差距，非 B-4 范围

### 复用要点（下次会话）
- 跑 iot-sink 测试：`mvn -f DEVICE/pom.xml test -pl iot-sink/iot-sink-biz -Dtest=<TestClass> -DfailIfNoTests=false -Dmaven.test.skip=false`（compiler 已锁 release 17，无需 source flag）
- PG 合同测试凭证：`docker exec postgres-server printenv POSTGRES_PASSWORD` → `TD008_PG_PASSWORD` 注入
- TDengine 合同测试：默认 localhost:6041 root/taosdata（REST）
- push origin：经 Clash 代理 `git -c http.proxy=http://127.0.0.1:7890 push origin cfdqiot`（直连 github:443 超时）

## 0. 项目强制双基线

本项目所有需求、PRD、SPEC、ADR、TD、开发、测试、评审、发布和运维变更，必须同时依据：

- [平台功能计划 1.5.0](../../架构设计/平台功能计划.md)：产品范围、版本、部署档位、模块归属、里程碑和优先级基线；
- [EasyAIoT 项目开发宪法 1.6.0](../../开发规范/EasyAIoT项目开发宪法.md)：安全、架构、数据、兼容、开发流程、质量门禁和 DoD 基线。

不得只读取或遵循其中一份。下游文档、代码或既有实现与双基线冲突时，必须先停止开发并完成基线修订或 ADR 决策；未获得实际代码、测试、构建、数据库和运行证据的事项继续标记为 `OPEN`。

## 1. 当前基线

| 文档 | 版本 | 状态 |
|---|---:|---|
| 平台功能计划 | 1.5.0 | 当前产品基线 |
| EasyAIoT 项目开发宪法 | 1.6.0 | 当前开发治理基线 |
| PRD-01 站点设备与数据采集 | 1.2.0 | Approved / Baselined（M1） |
| SPEC-001～004 集合 | 1.4.0 | Approved / Frozen |
| ADR-001～012（ADR-005 Superseded） | 当前索引基线 | Accepted / Superseded |
| [TD-001 collector 与 NODE 部署契约](./TD-001-collector与NODE部署契约.md) | 1.0.19 | In Review |
| [TD-002 SQLite Outbox 与恢复迁移](./TD-002-SQLite-Outbox与恢复迁移.md) | 1.0.2 | In Review |
| [TD-003 遥测 Inbox、ACK 与时序投影](./TD-003-遥测Inbox-ACK与时序投影.md) | 1.0.1 | In Review |
| [TD-004 电力对象、别名、二维码与历史编码兼容](./TD-004-电力对象别名二维码与历史编码兼容.md) | 1.0.3 | In Review |
| [TD-005 物模型模板 Schema、版本差异与发布 API](./TD-005-物模型模板Schema版本差异与发布API.md) | 1.0.53 | In Review |
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
- TD-005 冻结第五批（可观测性指标）已落地（2026-08-08，ADR-014 升 1.3.5）：ADR-014 §可观测性「指标 MUST 随消费者/发布器落地」缺口关闭——指标端口 `PowerModelEventMetrics` + Micrometer 适配（指标名/tag 冻结）、发布器结果计数与逐次投递耗时、Inbox 两条 critical 隔离路径计数、`power_model_outbox_backlog`（仓储新增 countByStatus，PENDING+PUBLISHING）与 `power_model_dlq_depth`（DEAD_LETTER 行数）两个 gauge 接线，全部在 enabled 门禁内装配。如实声明边界：Kafka DLQ topic 自身积压需 broker 侧导出器，列 OPEN。合同测试新增 5/5 PASS（事件包 80/80），设备域全量回归 **174/174 PASS**。
- TD-005 冻结第六批（JDBC 真实库合同测试）已落地（2026-08-08，ADR-014 升 1.3.6）：新增 Outbox（6 项）/Inbox（5 项）PostgreSQL 集成测试，覆盖首插/唯一裁决/hash CHECK/四态原子认领/批量顺序/双连接 SKIP LOCKED 互斥/三回写与防漂移守卫/截断/countByStatus/隔离 upsert 语义。执行环境如实声明：本地临时评审库 `td005_contract_review`（非 iot-device20），DDL 由测试用 V001/consumer_inbox 资产自检，11/11 PASS，outbox/inbox 残留 0、audit 残留 36（追加写设计使然），临时库已 DROP 并验证。跳过模式与既有 PG 测试一致（常规回归 21 项跳过全为设计内 PG 跳过）。V001 向目标实例落库仍待 ADR-013 runner 获批窗口。设备域全量回归 **185/185 PASS**。
- **V001 落库窗口已批准并执行（2026-08-08，ADR-013 升 1.5.1）**：镜像演练（iot-device20 schema-only 镜像库全链 SUCCEEDED 后删库）→ 窗口申请单 → owner 批准并立即执行 → runner 对 iot-device20 执行 M05→M15→M16→V001→V002 **apply SUCCEEDED**（APPROVAL=V001-WINDOW-20260808，执行前自动 pg_dump 备份于仓库外）；执行后只读验证 history 五行 SUCCEEDED、invalid_indexes=0、MIG-009 注释门禁 PASS、业务计数不变（4/4/17）、Outbox 空表。证据归档 `assets/td005-migration/verification/window-rehearsal-20260808/` 与 `window-execution-20260808/`。如实记录：① runner 头注释 PG_CONTAINER 默认值与实现不符（须显式传参，修正列后续小版本）；② `power_model_event_inbox` 不在五步骤链内，落库需 runner 增链步骤 + 新窗口，保持 OPEN。
- TD-001 协调器实现已启动（2026-08-08，TD-001 升 1.0.5 仍 In Review）：新增 §6.2「电力物模型事件驱动的快照再生」设计增补——四事件处理语义表（发布→审计 noop、生命周期→引用标记不改快照、绑定应用/回滚→影响面解析+单调递增 configVersion 发布单再生）、幂等派生键、retryable/final 分流、事务边界，以及 §18 新增实现任务 T-18。未经 §6.2 评审不得接线实现；处理器注册表在此之前保持为空（事件进 DLQ 有持久证据）。
- **T-18 批次 1 已落地（2026-08-08，TD-001 升 1.0.6，owner 评审通过 §6.2 后实施）**：新增四个协调端口（`CollectorWorkloadImpactPort` 影响面解析空集合法绝不返回 null / `CollectorConfigReleasePort` 幂等判定+再生发布单 templateVersion 回滚时由端口按 bindingRevision 解析 / `PowerModelTemplateReferencePort` 只写引用标记绝不改写快照 / `PowerModelCoordinationAuditPort` detail 脱敏有界 ≤512）、`PowerModelCollectorEventHandlers` 四 V1 处理器（字段缺失/解析失败→final MODEL_* 稳定码、端口 IAE→final、其余 RuntimeException→retryable、空影响面写 IMPACT_EMPTY 审计、幂等 workload 跳过、审计 detail 有界化）、`PowerModelEventWiringConfiguration` 条件装配（`@ConditionalOnBean` 四端口齐备时填充注册表，声明先于 `@ConditionalOnMissingBean` 空表回退；端口未装配时事件按缺失处理器进 DLQ，绝不静默丢弃）。合同测试 12/12 PASS，设备域全量回归 **197/197 PASS**。批次 2 OPEN：TD-001 DDL（`iot_collector_config_release` 等）资产 → runner 增链落库 → JDBC 端口实现 → 点表片段再生。
- **T-18 批次 2 第一步（DDL 资产 + runner 增链）已落地（2026-08-08，ADR-013 升 1.5.2、TD-001 升 1.0.7）**：新增 `V003__iot_collector_coordination.sql`（§4.1 发布单表[快照不可变触发器/canonical 长度与 hash CHECK/发布审计约束] + §6.2 引用标记表[同键幂等 upsert] + 协调审计表[追加写触发器/detail ≤512/动作白名单]）与 `U002` 卸载候选；runner APPLY_STEPS 增链 V003（事务型，CLI/env/MIG-009 清单同步）。临时评审库烟测全项 PASS（9 反例 + 幂等断言 + U002 非空拒绝，MIG-009 PASS，库用后已 DROP）。如实 OPEN：V003 对目标实例落库须**新窗口申请与 owner 批准**（本批不构成执行授权）；U002 未接入 runner uninstall 驱动；影响面端口只读既有表无新 DDL，workload binding 设备侧可见性为 TD-001 设计 OPEN。批次 2 后续：窗口批准 → V003 落库 → 三端口 JDBC 实现 + 真实库合同测试 → 点表片段再生。
- **ADR-015 已完成架构/DBA 专项评审并转 Accepted（2026-08-10，1.1.0）**：方案 E 冻结为 iot-device 库独立可变投影表 `collector_workload_binding_projection`，不跨库/不依赖 iot-node 事件同步。评审关闭 2 HIGH/3 MEDIUM：新增 `projection_revision` 严格单调、迟到写与同版本漂移数据库拒绝、STOPPED 防旧事件重激活、同租户 release FK、正数/非空边界和空表卸载；迁移固定独立 V004，保留 V003 校验和与审计轨迹。
- **T-18 批次 2 JDBC 端口实现进展（3/4，进行中，未完整）**：四协调端口 JDBC 实现已就绪三个——`JdbcPowerModelCoordinationAuditPort`/`JdbcPowerModelTemplateReferencePort`（commit `82c88680`，接口签名对齐 V003 `event_type`/`source_event_id` 列，处理器 4 处 `auditPort.record` 调用同步 `envelope.eventType()`，本次同时修复该批次编译失败中间状态）与 `JdbcCollectorWorkloadImpactPort`（commit `82806638`，基于 ADR-015 查 `collector_workload_binding_projection` 投影表 `WHERE tenant_id+product_id+lifecycle_status='ACTIVE'`，`@Repository` 装配但 3/4 不齐备故 `PowerModelEventWiringConfiguration` `@ConditionalOnBean` 注册表仍走空回退、事件进 DLQ 有持久证据）。合同测试 12/12 PASS，设备域全量回归 197/197 PASS（21 项设计内 PG 跳过）。**未完整/OPEN**：① `CollectorConfigReleasePort` 阻塞——`createRegenerationDraft` 依赖 ConfigSnapshot 组装（类型尚不存在，grep 确认 iot-device 无 `ConfigSnapshot`/`CollectorConfigSnapshot`）+ 投影表 upsert 写入端（人工首次发布 + 协调器再生 + workload 停止三条路径，属 TD-001 任务 7 发布管线范畴），`desiredMatches` 按 ADR-015 查投影表；② 三已实现端口的 PG 真实库合同测试待 V003 + V004 目标实例落库后接入（沿用 `TD005_PG_ENABLED` 跳过模式）；③ ADR-015 1.1.0 与 V004 字段已冻结且临时库验证通过，但目标实例迁移窗口尚未批准；④ 四端口齐备前注册表仍空。
- **ADR-015 V004 评审资产与临时库证据已完成（2026-08-10）**：新增 V004、空表卸载候选 U003、语义烟测和 runner 增链；临时库执行 V003→V004 成功，单调推进、旧 revision、同 revision 漂移、身份改写、跨租户 FK、空白字段反例全部符合预期，MIG-009 中文注释缺失 0、fixture 残留 0、空表卸载 PASS，临时库已删除。runner dry-run 输出 V004 SHA-256 `fb6708219c38e88ccf1f181828004deeb30ba09ca29d967eddba92ed81714603`；同时修复工具缺失时校验和为空却退出 0 的失败关闭缺口。目标实例未执行，仍须新窗口批准。
- **V003/V004/V005 目标窗口已执行完成（2026-08-10）**：将 ADR-014 Inbox 候选冻结为 V005（SHA-256 `a29f57d603b8beca667038a3625223f58c6463f32a2a9e1877e42e9dc41cc52c`）并新增 U004，runner 链扩至 V005。目标前先在 `iot-device20` 镜像评审库完成首次执行、全步骤幂等 SKIPPED、MIG-009、V004 反例及 8 项 PG 合同；随后按用户授权 `USER-CONTINUE-20260810-V003-V005` 对本地目标集成实例执行，V003/V004/V005 全部 SUCCEEDED。仓库外备份 `iot-device20_20260810_111153.sql` 已保留；目标 MIG-009 PASS、协调端口 3 项 + Inbox 5 项真实 PG 合同 **8/8 PASS**，五表 fixture 残留 0；镜像库已删除。当前唯一协调端口实现门禁转为任务 7 / `CollectorConfigReleasePort`，四端口齐备前继续空注册表回退并进 DLQ。
- **TD-001 任务 7 / ConfigSnapshot 合同批次已落地（2026-08-10，TD-001 1.0.8）**：新增 `collector-config-snapshot-v1.json` 机器 Schema 与 `CollectorConfigSnapshotContract`（严格字段集、生产非空总线、十进制字符串、采集策略必填、JCS canonical 单次生成、raw SHA-256/字节长度），并把 `CollectorConfigReleasePort.desiredMatches` 补为 `tenantId+workloadId+...` 租户安全判定；定向合同 **17/17 PASS**。目标库只读画像同时确认 `device`/`device_location` 无站点关系，现有 RTU `protocolConfig` 缺 `requestTimeoutMs/maxRetries/dataPriority/pollGroup`，故禁止用产品投影或默认值猜测，第四端口继续不装配并保持 DLQ fail-closed。站点关系应实现并复用 TD-004 已冻结的 `power_device_assignment→power_site` 与内部对象快照契约，不另建第二套；下一门禁是落地该关系并补冻四项采集策略事实，再实现发布/投影事务及 PG 合同。
- **TD-004 核心归属 Schema Spike 已落地（2026-08-10，TD-004 1.0.2 / TD-001 1.0.9 / ADR-013 1.5.3）**：新增 V006 五表（站点、空间树、回路树、设备资产、当前/历史归属）与 U005 空表拒绝候选，runner apply 链、env、MIG-009 同步。临时评审库首次 SUCCEEDED、同 hash SKIPPED、跨租户/当前唯一/跨站 FK/编码不可变/READY 闭合烟测、MIG-009、U005 非空拒绝与空表卸载全部 PASS，库已删除；目标 `iot-device20` 明确无 V006 对象/history。仍 OPEN：专项评审与目标窗口、全量安装 dump、存量导入、Mapper/Service/内部对象快照 API、四项采集策略来源；第四协调端口继续不装配。
- **V006 专项评审整改完成、目标窗口待批（2026-08-10，TD-004 1.0.3 / TD-001 1.0.10 / ADR-013 1.5.4）**：专项评审发现并关闭 2 HIGH——对象 tenant/site/device 身份列原地修改、assignment 直接覆写历史；新增统一身份 guard 与“仅当前行关闭且 version+1”历史 guard。最终 V006 hash `6fac9b429aae2fff34483fedc800f5d54bab8154b16953a85b2cf96f85229064`，第二临时库首次 DONE/重放 SKIPPED/增强反例/合法关闭换版/MIG-009/U005 全 PASS并删除。已形成专项评审报告及 `V006落库窗口申请单-20260810.md`，状态待 owner 批准；目标仍无 V006。
- **V006 目标窗口已执行完成（2026-08-10，TD-004 1.0.4 / TD-001 1.0.11 / ADR-013 1.5.5）**：owner 明确批准 `USER-APPROVAL-20260810-V006` 后，runner 自动在仓库外生成 `iot-device20_20260810_124655.sql` 并仅执行 V006；目标 history=`SUCCEEDED` 且 hash 精确匹配，五表均为 0 行，三个保护函数/六个 trigger 存在，MIG-009 PASS、invalid index=0、业务计数保持 `4/4/17`，V001～V005 未漂移。capability、业务初始化和第四协调端口仍关闭。下一步为 tenant-safe `PowerObjectQueryApi`/Mapper/Service 与真实 PostgreSQL 合同；全量安装 dump、存量导入和四项采集策略来源继续 OPEN。
- **PowerObjectQueryApi 查询闭环已落地（2026-08-10，TD-004 1.0.5 / TD-001 1.0.12）**：`iot-device-api` 新增无 tenant 入参的批量 Feign 契约与字符串 ID DTO，`iot-device-biz` 新增 provider、tenant-safe JDBC Mapper/Service、READY/NOT_BOUND/INACTIVE 门禁和服务端 `sha256:` objectRevision；未知/跨租户/重复标识均失败关闭。Java 17 reactor 编译 PASS，服务与静态 provider/consumer 合同 6/6 PASS，目标 V006 真实 PG 合同 2/2 PASS；回滚后五表与 fixture 设备均为 0，原业务计数仍为 `4/4/17`。第四端口继续不装配；下一步补冻四项采集策略来源并实现发布/投影事务。
- **四项采集策略、V007 与第四端口实现已完成至“待目标窗口”（2026-08-10，TD-001 1.0.14 / ADR-013 1.5.6）**：四项策略只认 VALIDATED canonical 发布单；V007 补 `product/template/binding/sourceEvent` 身份、同租户 binding/Outbox 复合 FK、稳定原因码以及 `node/base/rollback` 等不可变保护，专项评审关闭 2 HIGH/4 MEDIUM 并已接 runner。最终 V007/U006 hash=`6590d6daa33e6e3382f17b1ef1ced0ed854c5322857062617d2b77c621e38685` / `a96bf988cdcdc938dba3238a99a5a6dedaf35cee5e1a90db5cab103364889bc5`，runner 对目标 dry-run PASS，但**未执行 V007**。`JdbcCollectorConfigReleasePort` 已实现精确候选锁定、canonical/hash/长度复核和发布单+投影同事务 CAS，默认配置关闭；事件合同 13/13、静态门禁 1/1、schema-only 镜像 PG 合同 1/1 PASS，临时库均已删除且 fixture/invalid index 为 0。下一步只剩 V007 独立窗口授权、目标执行与目标 PG 复验；授权前不得开启第四端口。
- **V007 目标窗口与第四端口目标合同已完成（2026-08-10，TD-001 1.0.15 / ADR-013 1.5.7）**：owner 授权 `USER-APPROVAL-20260810-V007` 后，runner 自动在仓库外生成 358012-byte 备份（SHA-256 `c1196e0f99d040029506eb2a590b51346e12831e8f3f69675a56f74e6ff2a53b`）并仅执行 V007；history/hash、六列六约束、不可变函数、MIG-009、invalid index=0 全 PASS。目标静态门禁+PG 合同 2/2、事件合同 13/13，合计 15/15 PASS；fixture 回滚后 release/projection/binding/outbox/template 均为 0，业务计数仍 `4/4/17`。第四端口代码虽齐备但仓库配置仍未启用；下一门禁是首次 VALIDATED 候选创建 API/事务及其真实 PG 原子合同，完成前不得开启。
- **首次 VALIDATED 候选创建事务已落地（2026-08-10，TD-001 1.0.16 / TD-005 1.0.18）**：新增字面量冒号 action API `POST /api/v1/products/{productIdentification}/model-binding:apply`，启用 `power:model-template:publish` 权限并在 Service 再校验 capability、租户有效性、tenant-safe 产品/模板事实；服务端以产品行锁 + workload advisory lock 分配单调版本，同事务写 binding、领域 audit、Outbox 与同事件 `VALIDATED` canonical 候选，绝不调用 NODE。`Idempotency-Key` 仅存服务端 HMAC，支持同请求重放并拒绝异请求复用；secret 未配置或不足 32 字节时 fail-closed。Java 17 reactor 编译 PASS，目标 PostgreSQL 成功/canonical/hash/重放/冲突及最终插入强制失败整体回滚合同 **2/2 PASS**，专用租户的 idempotency/product/template/binding/audit/outbox/release 全部零残留。第四端口仍默认关闭；下一门禁为候选→Outbox 消费→发布单/投影推进端到端合同与显式启用评审。
- **首发闭环冲突已识别并隔离（2026-08-10，TD-001 1.0.17 / TD-005 1.0.19）**：端到端接线证明首个 `VALIDATED + Outbox` 事件会在第四端口之前被 `ImpactPort` 的空 ACTIVE 投影判定为 `IMPACT_EMPTY`，与 ADR-015 首次发布必须同事务创建 revision=1 投影的 Accepted 决策冲突，属于其已登记的 CRITICAL 首发死循环。已为绑定 Controller 增加 `easyaiot.power-model.binding-apply-api-enabled=true` 独立门禁并默认关闭；数据库原子合同仍有效，但不再表述为可开放生产闭环。下一步必须先完成“人工首发同步发布”与“消费者精确候选首发”二选一的专项架构决策及 TD 对齐，再实现首发投影写路径和真实 PG 端到端合同；两项开关继续禁止启用。本轮未执行 DDL、未调用 NODE。
- **人工首发与事件消费闭环已完成（2026-08-10，TD-001 1.0.18 / TD-005 1.0.20）**：按 ADR-015 Accepted 路径，绑定 apply 在同一数据库事务将已校验候选推进为 PUBLISHED 并插入 revision=1 ACTIVE 投影；既有投影仅在身份一致且产品无未覆盖活动 workload 时单调推进，部分发布失败关闭。真实 `iot-device20` 合同覆盖首发、重复投递、同 workload 不同 event 的 revision/configVersion 1→2、强制投影 CAS 失败保存点回滚、Inbox PROCESSED 与协调审计，连同 Controller 默认关闭静态门禁 **4/4 PASS**；与第四端口 PG 合同及事件处理器合同组合回归 **19/19 PASS**。测试租户八类 fixture `0/0/0/0/0/0/0/0`，业务计数仍 `4/4/17`。Java 17 reactor 编译 PASS；未执行 DDL、未调用 NODE。下一步为两项默认关闭开关的显式启用评审与配置组合测试，评审前继续禁止开启。
- **写链启用专项评审完成但未激活（2026-08-10，TD-001 1.0.19 / TD-005 1.0.21）**：识别实际四项运行事实（绑定 API、第四端口、事件总开关、幂等 secret），新增启动期 `PowerModelActivationGuard`。默认全关；任一激活必须是 standard/full + capability enabled；事件链要求第四端口；API 要求完整链且 secret ≥32 UTF-8 字节。合法灰度为“第四端口→事件链→API”，回滚为“API→Outbox 排空→事件链→第四端口”。application、iot-device Compose 和 env.example 已显式接线安全默认值，配置组合+静态部署合同 8/8、完整组合回归 26/26、Compose 解析全部 PASS；测试租户八类事实零残留，业务计数 `4/4/17`。评审结论 `CONDITIONALLY_APPROVED / NOT_ACTIVATED`；下一步需 owner 独立批准实际灰度窗口并提供运行密钥、Kafka/消费者健康和零积压证据。本轮未修改运行环境、未执行 DDL、未调用 NODE。
- **实际灰度前只读预检与窗口申请已形成（2026-08-10）**：PostgreSQL/Kafka healthy，V001～V007 均 SUCCEEDED，Outbox/Inbox/发布单/投影为 `0/0/0/0`，invalid index=0，业务计数 `4/4/17`；但当前无运行中的 `iot-device`，主 Topic/DLQ 和消费组均不存在，无法提供消费者在线/lag 证据，运行时 secret 也未注入。Kafka 虽允许自动建 Topic，但 ADR-014 的 6 分区/30d 保留仍是未冻结候选，不得依赖 broker 默认值隐式创建。已新增灰度窗口申请单，状态 `WAITING_OWNER_APPROVAL / NOT_READY / NOT_ACTIVATED`；需先裁定 Topic 参数、部署 standard/full `iot-device`、注入密钥并取得独立授权，本轮保持全开关关闭且未修改运行环境。
- **灰度只读预检已工具化（2026-08-10）**：新增 `power_model_activation_preflight.ps1`，按 baseline/release-port/events/api 四阶段核验档位、capability manifest、三开关组合、密钥合格布尔值、容器健康、V001～V007/hash、四类数据库积压、Kafka Topic/消费组，稳定退出码为 0/1/2；脚本不创建 Topic、不启动容器、不修改配置/数据库且不输出密钥。预检同时发现当前 `DEVICE/.env` 缺 capability profile/manifest location，直接 Compose 将安全关闭电力能力；该运行配置未擅自修复，继续列为窗口阻断。env.example 注释已纠正为三阶段启用顺序，禁止一次性全开。
- **baseline 运行准备参数已完成只读定参（2026-08-10，待明确授权）**：建议沿用当前 `full`（31.5 GiB 内存、D 盘 199.7 GiB 可用），主/DLQ 各 6 分区、单 broker 本地复制因子 1、显式保留 30 天。端口和网络就绪，但 iot-system/iot-infra/iot-device 三镜像均缺失，且 Compose 必须按三服务健康依赖链启动。窗口申请已给出精确批准原文；授权范围仅含 capability 同步、三镜像构建/启动、两个 Topic 创建及 baseline 只读验收，三写开关继续 false，不注入 secret、不执行 canary/NODE/DDL。
- **baseline 运行准备已获批并验收完成（2026-08-10，`USER-APPROVAL-20260810-POWER-MODEL-BASELINE`）**：full capability 已同步；主/DLQ 各 6 分区、RF=1、30d 显式保留创建并精确回查；Java 17 的 35 模块 package SUCCESS，三项 Jar/hash 与镜像 ID 已归档；iot-system→iot-infra→iot-device 均 healthy，内部 actuator=UP，device 内 full manifest 可读且含 `power.device.model`。最终 baseline 预检退出码 0，全项 PASS，V001～V007/hash、invalid index、四类零积压和业务 `4/4/17` 均保持。API/release/events 仍全 false，secret 为空，未执行 canary/NODE/DDL；阶段 1 release-port 仍未授权。
- **写链灰度阶段 1 已获批并验收完成（2026-08-10，`USER-APPROVAL-20260810-POWER-MODEL-RELEASE-PORT`）**：切换前 baseline 全项 PASS；仅将 release port 设为 true，并以 no-deps 仅重建 iot-device。最终 API=false/release=true/events=false/secret 空，device healthy，启动日志无保护错误；阶段 1 预检退出码 0、全项 PASS，V001～V007/hash、Topic 参数、invalid index、四类零积压和业务 `4/4/17` 均保持。未产生业务写入、未调用 NODE/DDL；阶段 2 event pipeline 仍未授权。
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

0. **首次 VALIDATED 候选创建 API/事务及目标 PG 原子合同已完成，第四端口仍默认关闭**：下一步补候选→Outbox 消费→`JdbcCollectorConfigReleasePort`→发布单/投影推进的真实 PostgreSQL 端到端合同，覆盖重复事件、异事件、投影 CAS 失败整体回滚，并完成 `EASYAIOT_POWER_MODEL_IDEMPOTENCY_HMAC_SECRET` 与 `collector-release-port-enabled` 的显式配置评审；通过前不得开启第四端口；
1. 读取 [TD-005 1.0.16](./TD-005-物模型模板Schema版本差异与发布API.md)、[TD-005 运行模型兼容与删除链设计 0.1.9](./TD-005-运行模型兼容与删除链技术设计.md)、[migration 子设计 0.1.7](./TD-005-版本绑定审计Outbox迁移与回滚设计.md)和[TD-005 评审报告 §23](../../开发规范/TD-005评审报告.md)；
2. Mapper/DO/VO、legacy adapters、Java golden、根属性 TEN-001～004/006、内部八表持久化/TEN-005/回滚、稳定错误和 TEN-007/008 已完成；先复跑 capability 4 项、只读 API 1 项与设备侧 19 项目标测试，禁止回退到 `product_properties.service_id` 或散落 profile 判断；
3. 对 [版本/绑定/审计/Outbox migration 与 rollback 0.1.2 候选](./TD-005-版本绑定审计Outbox迁移与回滚设计.md)继续 DBA/架构评审，优先评审 [ADR-013（Proposed）](../../架构决策/电力运维云平台/ADR-013-受控数据库迁移执行器.md)与 [V001/U001 DDL 骨架](./assets/td005-migration/V001__power_model_version_binding_audit_outbox.sql)，关闭 runner 决策后再决定 TD-004 幂等表落库顺序、product unique/binding FK、事件 transport/消费者 Inbox 和压测/保留值；批准前不得启用 DDL；
4. 上述决策通过后，先形成可供 DBA 核对的 V001/U001 DDL 骨架和事件 V1 Schema/fixture 候选，再生成 manifest/hash 并执行 MIG/TX/OUT/AUD/CFG/PERF 合同；全部通过后才实现公开接口原子边界；
5. 在生产 Java/TypeScript 模块中消费现有 JCS/hash golden，并补成员唯一、SemVer、CT/PT 等 Schema 外语义合同；
6. 建立恶意 Excel/JSON 导入 fixture，并组织 10 类行业模板评审；
7. 所有门禁通过后更新资产 manifest 的真实 Git commit/hash，再决定 TD-005 是否转 Approved / Frozen。

TD-005 评审可以与 TD-001～004 的证据准备并行，但任何生产代码不得绕过各 TD 的冻结门禁。

## LC02A-0 本地执行续作记录（2026-08-15）

- 本轮按双基线执行：项目开发宪法 1.6.0（SHA-256 `76BF30903A5A62C957A75B57E24080AA7132DE6992D56C262EEAFBE7BE553C4D`）与平台功能计划 1.5.0（SHA-256 `F0E5734C8FADA6D0E4DAB98F7D12F58940077F9C60AA8DA42AC8EF45C6F69869`）。
- LC02A-0 已完成本地代码批次：Java common-security 内部服务 HMAC/allowlist/Redis nonce、iot-node 节点 key Provider/signer、NODE HMAC/replay/credential Provider 与安装权限基线。
- 证据：Java common-security 3/3、iot-node 2/2、NODE pytest 3 passed；Flask 集成用例 1 skipped（环境未安装 Flask）。Java 两个定向 Maven reactor 均 `BUILD SUCCESS`；`py_compile` 通过。
- 复跑证据（2026-08-15）：`NODE/tests` 在工作区可写临时目录为 `3 passed, 1 skipped`；`InternalServiceAuthContractTest` 3/3、`NodeAgentSigningKeyProviderTest` 2/2，两个 Maven reactor 均 `BUILD SUCCESS`。当前 Windows 的 WSL 实例创建返回 `E_ACCESSDENIED`，因此 Linux PTY 端到端证据仍保持 `OPEN`。
- 凭据状态：`NODE/agent.env` 已从 index 移除并被 ignore；PostgreSQL 初始化转储中的旧节点令牌已清除；旧令牌当前工作树匹配数 0。外部轮换与旧值失效已完成，cached/worktree secret scan 均通过；部署后/现场验证仍 `OPEN`。
- 轮换续作（2026-08-15）：控制面恢复后，正式 `reset-agent-token?id=1` 对平台节点返回业务拒绝；按既有授权执行受控 DB fallback。数据库、本地 `agent.env`、bootstrap 返回值一致；一次性 Agent register/heartbeat 均 `code=0`。未启动常驻 Agent；`.git/index.lock` 已清理，清理后的 SQL 已进入待提交索引，索引扫描通过。
- 暂存状态：仓库锁已清理，SQL 转储清理已进入待提交索引；`NODE/agent.env` 仍只保留在本地受保护文件且不在索引中。
- 继续门禁：LC02A-1～4 仍 Blocked；现场串口、跨主机网络、NTP、部署后稳定性和 7 天稳定性保持 OPEN，不得从本地测试推断已验证。

## TD-001 OPEN-03 本地接口/安全联合审查（2026-08-16）

本次审查依据项目开发宪法 1.6.0、平台功能计划 1.5.0、TD-001 v1.0.19、TD-001 评审报告和 M1-LC-02A 任务单，仅覆盖当前 Windows 工作区可执行的接口合同、安全约束和文档交叉核对。未启动 LC02A-1～4，未执行 Linux PTY、资源压测、Windows 发布资格、现场或部署后验证。

### 1. 接口冻结审查

- 当前 v1 `CollectorConfigSnapshotContract` 的 canonical UTF-8/hash/长度、生产非空总线、显式采集策略、十进制字段和缺失事实拒绝已有 5/5 定向测试；`CollectorTelemetryWriter`/配置/Profile 依赖 Outbox 的定向测试 5/5。
- 当前仓库尚无 ConfigSnapshot 1.1 schema、服务端注入 `productIdentification`、类型化 WorkloadSpec 机器合同、release 详情/observed CAS API、NODE `/workload/collector/config` 路由或 LC02A-1～4 的完整状态机/组合 E2E。版本单调、同版同 hash 幂等、同版异 hash/低版本拒绝、乱序回报、失败保留旧 active、回滚生成新版本等仍是待逐包实现证据，不得以 v1 单元合同替代。
- 当前 `iot-sink` collector 配置仍可从 `IotGatewayConfiguration` 的 RTU 配置注入 `DeviceMapper`、`IotDeviceMessageService` 和 `IotMessageBus`，且 `application-collector.yaml` 启用 `modbus-rtu`；因此“collector 无中心数据库依赖”的 §19 门禁尚未关闭。

### 2. 安全约束冻结审查

- `InternalServiceAuthContractTest` 3/3、`NodeAgentSigningKeyProviderTest` 2/2；`python -m pytest NODE/tests -q --basetemp .codex-tmp\pytest` 为 `3 passed, 1 skipped`。跳过项是因当前环境未安装 Flask 的集成测试，未冒充通过。
- HMAC canonical request、时间戳/body hash、节点 key Provider、持久 nonce/防重放和 fail-closed secret Provider 的本地可复核部分保持通过；没有回显任何凭据值。
- 当前 NODE 通用 `/workload/deploy`/`WorkloadManager` 仍接收 `command`、`workDir`、`logDir`、`gpuIds`、`env`/`files`，collector 专用类型拒绝、镜像/路径 allowlist 和固定模板尚未实现。签名保护的 collector 路由当前未实现；认证测试签名请求得到 404 只能证明未回退到 token-only，不能证明配置业务链通过。

### 3. 联合记录与剩余 OPEN

| 范围 | 结论 |
|---|---|
| 本地接口/安全合同 | 已执行、可复核；现有 deterministic tests 全部通过或明确 skip，缺失实现保持 OPEN |
| TD-002/003 交叉形状 | 文档已交叉引用 `TelemetryOutboxPort`、`/var/lib/easyaiot/outbox` 和四 facet 健康摘要；本次未做完整跨 TD 运行 E2E/冻结签字，记为待正式联合冻结 |
| TD-001 §19 门禁 1/2/3 | 1 OPEN（中心依赖/collector Provider）、2 PARTIAL/OPEN（v1 有证据，v1.1/WorkloadSpec/CAS/状态机缺失）、3 OPEN（通用 deploy 仍可接收任意命令/路径） |
| TD-001 §19 门禁 4/5 | 按用户授权不执行，Linux PTY、资源/7 天稳定性、Windows 资格继续 OPEN |
| OPEN-03 | 仍 OPEN；TD-001 继续 `In Review`，不改为 `Approved / Frozen`，LC02A-1～4 继续 `Blocked` |

本次仅更新 TD-001、M1-LC-02A 和本进度记录；未修改业务代码、配置、凭据或测试。工作区临时 pytest 目录已清理。

### Sol 最终复核（2026-08-16）

Sol 已独立核对 Luna Max 的证据并接受其事实结论，正式状态为 `OPEN / NOT_CONVERGED`。门禁 1、3 仍 `OPEN`，门禁 2、6 为 `PARTIAL / OPEN`，门禁 4、5 按人工约束保持 `OPEN-RUNTIME`；TD-001 不升级为 `Approved / Frozen`，LC02A-1～4 不启动。下一次本地续作入口是先拆分并冻结门禁 1～3、6 的实现与合同测试任务单，运行期门禁等待人工明确要求。

### OPEN-03 本地收敛任务冻结（2026-08-16）

Sol 已完成 [M1-TD001-OPEN03 本地收敛实现任务单](./M1-TD001-OPEN03-本地收敛实现任务单.md) 的 OPEN03-01～08 拆分与冻结，顺序为 ConfigSnapshot 1.1 → WorkloadSpec → DEVICE CAS → NODE 专用安全边界 → NODE 配置状态机 → collector 本地 Provider → iot-node 派发 → 跨 TD 组合合同。当前只授权 GPT-5.6 Luna（max reasoning）执行 OPEN03-01；运行期门禁 4、5 继续不执行。

### OPEN03-01 验收与 OPEN03-02 解锁（2026-08-17）

Luna Max 已完成 ConfigSnapshot 1.1 与服务端 `productIdentification` 注入，Sol 独立复核并补跑本地真实 PostgreSQL 合同：Snapshot 10/10、源字段安全 2/2、JDBC 3/3（Skipped=0）、33 模块 compile 和 `git diff --check` 全部通过。OPEN03-01 状态为 `COMPLETE / SOL-ACCEPTED`；门禁 2 仍为 `PARTIAL / OPEN`，当前只解锁 OPEN03-02 WorkloadSpec 机器合同。Linux PTY、资源/稳定性压测和 Windows 资格继续不执行。

### OPEN03-02 验收与 OPEN03-03 解锁（2026-08-17）

Luna Max 已完成 WorkloadSpec 1.0 Schema/DTO/validator/fixture。Sol 首次复核发现并收紧固定配置容器路径与显式 capability 配额，修正后独立测试 12/12、22 模块 reactor、Schema/字段一致性和 `git diff --check` 全部通过。OPEN03-02 状态为 `COMPLETE / SOL-ACCEPTED`；门禁 2 仍为 `PARTIAL / OPEN`，当前只解锁 OPEN03-03 release 详情/observed CAS。运行期门禁 4、5 继续不执行。

### OPEN03-03 验收与 OPEN03-04 解锁（2026-08-17）

Luna Max 已完成 iot-device pending/detail/observed 内部合同和 PostgreSQL CAS。Sol 首次复核发现 ADR-018 动态 `releaseId` 路由无法被静态 allowlist 匹配，并收紧详情读取、未知字段、错 node/workload/version/hash 与相反终态晚到负例；修正后独立测试 Collector 8/8（真实 PostgreSQL 2/2、Skipped=0）、ADR-018 4/4，33 模块 SUCCESS、八类 fixture=0、`git diff --check` PASS。OPEN03-03 状态为 `COMPLETE / SOL-ACCEPTED`；门禁 2 仍为 `PARTIAL / OPEN`，当前只解锁 OPEN03-04 NODE collector 专用部署安全边界。运行期门禁 4、5 继续不执行。

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

> 读取 `.doc/技术设计/电力运维云平台/M1-SDD进度与续作入口.md`，遵循《平台功能计划》1.4.0 和《EasyAIoT 项目开发宪法》1.5.0，继续 TD-005。TD-005 1.0.16 / 运行模型 0.1.9 已完成 12 表画像、非空 legacy golden、Mapper/DO/DTO/adapters、八表 tenant-safe 持久化、TEN-001～003/005～008、TEN-004 数据层整批原子拒绝、内部边界稳定业务错误，以及 ADR-011 manifest/统一 CapabilityService/只读 API；共享能力 4 项、只读 API 1 项与设备侧 19 项测试 PASS，真实 PostgreSQL 八表 fixture 残留为 0。migration 与 rollback 设计已更新为 0.2.0，ADR-013 1.5.2/ADR-014 1.3.6（**Accepted**，2026-08-08）：受控 runner（history + SHA-256 + advisory lock + 两阶段先校验后执行）、五步骤链 M05 → M15 → M16 → V001 → V002（M05 幂等表正式落库）、roles 候选与事件 V1 Schema/fixture（strict+ajv-formats 4/4 PASS）已形成；DBA/架构专项 19 项发现全部处置，演练证据（签名漂移、MIG-005 双向、备份/恢复、回滚、M05 建模）全部 PASS，未在任何共享/生产库执行 DDL。ADR-012 1.0.2 已 Accepted，整体仍为 OPEN_REMEDIATION_REQUIRED。**ADR-013 1.5.2 / ADR-014 1.3.6 已 Accepted**（三项人工闭环全数关闭：双签、压测豁免、画像生产重跑 PASS + owner 指定，评审报告 §8.7）。TD-005 冻结实现证据已落地：Schema 归位 + Envelope + Inbox/Outbox 领域逻辑（31 测试）、CI 事件合同门禁（24 项 PASS）、持久化接线（入列服务/发布器/Inbox 写入/JDBC/Kafka 装配，23 测试）、消费循环（codec/处理器注册表/消费编排器/@KafkaListener 薄适配/装配配置，18 测试）、发布器调度驱动（Spring Scheduling 选型 owner 裁定，fixedDelay 轮询 + 显式 @EnableScheduling，3 测试）、可观测性指标（端口 + Micrometer 适配 + 结果/耗时/隔离计数 + backlog/dlq_depth gauge，5 测试）、JDBC 真实库合同测试（Outbox 6 + Inbox 5，本地临时评审库执行 11/11 PASS、用后删库，TD005_PG_ENABLED 跳过模式）；设备域全量回归 197/197 PASS。V001 落库窗口已批准并执行（五步骤链对 iot-device20 apply SUCCEEDED，ADR-013 1.5.1）；TD-001 协调器 T-18 批次 1 已落地（1.0.6：四协调端口 + 四 V1 处理器 + 条件装配，12 合同测试），批次 2 第一步已落地（1.0.7 + ADR-013 1.5.2：V003 三表 DDL 资产 + U002 卸载候选 + runner 增链，临时评审库烟测 PASS，未对目标实例执行）。ADR-015（Proposed，commit `db2e9bd6`）已形成：处置 V003 OPEN 项「活动 collector workload binding 设备侧可见性」，决策 iot-device 库新建 `collector_workload_binding_projection` 投影表 + 发布单状态机 upsert（不依赖 iot-node 事件同步），经 architect 对抗性审查否决扩展 release 表方案（首次发布死循环 CRITICAL）。T-18 批次 2 JDBC 端口 3/4 已就绪（`JdbcPowerModelCoordinationAuditPort`/`JdbcPowerModelTemplateReferencePort` commit `82c88680` + `JdbcCollectorWorkloadImpactPort` commit `82806638`，合同测试 12/12 + 回归 197/197，注册表 3/4 仍空事件进 DLQ；`CollectorConfigReleasePort` 阻塞于 ConfigSnapshot 组装 + 任务 7 发布管线 upsert 写入端）。下一步：ADR-015 评审 → Accepted → 投影表 DDL 增链落库（V003 扩展或 V004 新窗口）→ 三端口 PG 真实库合同测试 → ReleasePort（待 ConfigSnapshot + 任务 7）；V003 新窗口申请与批准 → 真实库合同测试、`power_model_event_inbox` runner 增链落库（新窗口）、双发对账演练、manifest 真实 Git commit/hash 写入后 TD-005 Approved/Frozen 决策；全部门禁通过后更新资产 manifest 真实 Git commit/hash，再决定 TD-005 是否转 Approved / Frozen。

> **2026-08-10 恢复提示更正（取代上段中 ADR-015/下一步的旧状态）**：ADR-015 已完成技术评审并以 1.1.0 转 Accepted；独立 V004、U003、语义烟测及 runner 增链已完成，V003 原校验和保持不变，临时 PostgreSQL 的 V003→V004、反例、中文注释与空表卸载全部 PASS，临时库已删除，目标实例未执行。下一步先申请并批准 V003 + V004 + `power_model_event_inbox` 的目标实例窗口；执行后补 Audit/TemplateReference/Impact 三端口 PostgreSQL 合同测试；随后进入 TD-001 任务 7，冻结 `CollectorConfigSnapshot`、发布状态机与三条投影写路径并实现 `CollectorConfigReleasePort`。四端口齐备前继续空注册表回退和 DLQ 持久证据，不启用生产协调链。

## 8. 2026-08-10 运行激活最新状态（取代旧恢复提示中的运行态判断）

- `postgres-server / iot-device20` 已按独立窗口完成 V001～V007，当前 7/7 SUCCEEDED；
- baseline 与阶段 1 已完成；阶段 2 event pipeline 已完成并验收：template API=false、binding API=false、
  release=true、events=true、secret 空，`iot-device` healthy；
- 四个 V1 handler 增加了启动完整性失败关闭门禁；相关回归 24/24 PASS；
- Compose Kafka 标准地址缺失在首次开启时被消费组门禁发现，已安全回滚、修复并重试；最终消费组
  6 分区在线、lag=0、DLQ 六分区 offset=0；
- 数据库 Outbox/Inbox/release/projection 仍为 `0/0/0/0`，业务基线仍为 `4/4/17`；
- 提交前 Java/真实 PostgreSQL 综合回归 46/46 PASS；其中协调端口 fixture 已按 V007 补齐
  product/template/version/binding/audit/outbox/release 完整外键链，所有测试事务回滚后零残留；
- 阶段 3 仍未授权。下一步必须先明确 HMAC secret 的运行时注入设施及单一 canary 租户/workload，
  再独立批准 API=true 与一次 canary；不得把当前阶段 2 就绪误判为 M1 完成。

## 9. 2026-08-10 阶段 3 准备补充（当前工作树，尚未提交）

- 只读核对确认目标库没有模板身份、草稿或 PUBLISHED 版本，且现有 4 个产品均已有设备；因此不具备
  合法模板发布输入和零影响 canary 产品，阶段 3 继续保持未授权、API=false，未写入业务数据；
- 模板发布入口的首个缺口是 Draft 2020-12 生产校验器。已在父 BOM 锁定
  `com.networknt:json-schema-validator:1.0.74`（首个支持 Draft 2020-12 的 1.x 版本），实际依赖树为
  validator 1.0.74 + 平台 Jackson 2.13.5；未升级平台 Jackson；
- 新增 `PowerModelTemplateContentValidator`：使用冻结 Schema，拒绝任何非 `#` 本地 `$ref`，稳定排序并
  限制 Schema 错误数，同时复用成员编码唯一性和 HIGH_RISK 服务策略校验；
- 真实冻结 Schema + `example-standard-meter-1.0.0.json` 的兼容性测试 3/3 PASS，覆盖合法样例、
  Schema 与语义错误并报、外部 `$ref` fail-closed；GitHub Security 当前无该项目已发布 advisory，
  但仓库尚无自动依赖漏洞扫描，依赖安全门禁仍记为 OPEN；
- 冻结 Schema 已作为 `iot-device-biz` classpath 只读资源归位，副本与评审资产逐字一致，SHA-256 均为
  `2431b8e7f25414aff89468d1b1daced2d10ce064d80b0816791912c7272bbae5`；新增条件配置仅在
  `easyaiot.power-model.template-api-enabled=true` 时装配，并对资源大小、摘要、BOM、严格 UTF-8、
  外部 `$ref` 执行启动失败关闭；内容与装配目标测试累计 9/9 PASS；
- identity/draft 写链的幂等前置已完成：原内聚于 `PowerModelBindingApplyService` 的 JDBC 争抢/重放
  SQL 已抽取为
  `JdbcPowerIdempotencyStore`，统一支持首次争抢、成功/最终失败重放、异请求冲突、16KiB 响应上限与
  CAS 终态推进；绑定写链已迁移使用该端口；领域/Schema 回归 16/16 PASS，绑定 + 通用端口真实
  PostgreSQL 合同 5/5 PASS、0 skipped，测试事务后 tenant fixture 零残留；
- 模板 identity 创建服务已完成：只创建当前租户 `ownerScope=TENANT` 身份，服务端取得 tenant/actor，
  校验 10 类 deviceType、STANDARD/VENDOR、稳定 code 与名称边界，复用 HMAC 幂等端口，以
  `(tenantId,templateCode)` 的 `ON CONFLICT DO NOTHING` 返回稳定重复错误；identity + 通用幂等 + 原绑定
  真实 PostgreSQL 合同 6/6 PASS、0 skipped，事务后模板和
  幂等 fixture 均为 0；
- 模板 draft 创建/完整替换服务已完成：写入前只生成一次 JCS canonical/hash，校验模板身份、SemVer、
  STANDARD/VENDOR 精确基线、版本/hash 冲突，使用 tenant 模板行锁串行化；草稿从 revision 0 开始，
  `If-Match` 强 ETag 通过 CAS 单调递增，同幂等 key/request 的重放先于 ETag 解析；
- 草稿 `validate` 领域服务已完成：读取 ACTIVE 草稿后复核 canonical/hash 完整性，返回全部当前可判定的
  Draft 2020-12 + 成员唯一性 + HIGH_RISK 语义错误；已有已发布版本时选取低于目标版本的最近同模板版本，
  计算 `minimumBump` 并把过低版本作为 `MODEL_TEMPLATE_SEMVER_BUMP_TOO_LOW` 纳入完整错误数组。首个版本
  不虚构比较基线；同时修正 diff 核心，模板身份、版本号和派生基线等非运行语义元数据不再误触发 MAJOR；
- Java 17 全反应堆编译 PASS；模板发布事务领域服务已完成：幂等争抢/重放先于 `If-Match`，以
  `tenantId:templateCode` advisory transaction lock 和 15 秒超时串行发布，锁超时映射稳定
  `MODEL_TEMPLATE_PUBLISH_LOCK_TIMEOUT`；锁内重校验 canonical/hash、完整 Schema/语义、正式 SemVer，
  且目标版本必须高于所有既有 PUBLISHED 版本；
- 发布 CAS 将草稿推进为不可变 PUBLISHED，并从 canonical 同事务重建 PROPERTY/EVENT/SERVICE 成员索引、
  写追加审计和 `POWER_MODEL_TEMPLATE_PUBLISHED_V1` PENDING Outbox；事件 UUID v4、payload hash、审计 FK、
  diff summary 与发布响应均复用现有冻结合同。Outbox 注入失败时版本、索引、审计、Outbox 和幂等记录整体
  回滚；成功重放不再检查陈旧 ETag；
- 独立双事务合同已补齐：第一事务在 Outbox 前持有模板 advisory transaction lock 时，第二事务不能越过
  锁进入发布临界区；第一事务注入失败回滚后第二事务才可进入，二者均不留下版本推进、索引、审计、Outbox
  或发布幂等部分事实。真实独占同一 advisory key 时，发布在约 15 秒有界失败并返回稳定
  `MODEL_TEMPLATE_PUBLISH_LOCK_TIMEOUT`，幂等 claim 同事务回滚，草稿保持 DRAFT；
- 默认关闭的 HTTP 边界已完成：`PowerModelTemplateController` 只在
  `easyaiot.power-model.template-api-enabled=true` 时装配，暴露 identity/create draft/replace draft/validate/
  publish 五个冻结路由；tenant/actor 只取服务端上下文，编辑与发布分别使用
  `power:model-template:edit`/`power:model-template:publish`，validate/publish 严格使用字面量冒号 action，未提供
  slash alias；草稿创建/替换响应返回强 ETag；
- 模板 Controller 专属异常处理已按 TD-005 固定 `code/message/errors/traceId/timestamp/retryable` envelope，
  覆盖业务错误、缺 Header、参数绑定、坏 JSON 和兜底 500；412/409/发布锁超时及 retryable 语义集中映射，
  外部 traceId 仅接受 128 字符以内安全字符。为运行 MockMvc 合同，移除了 `iot-device-biz` 对
  `spring-test 5.0.5.RELEASE` 的过期硬编码，回归父 BOM 的 Spring 5.3.31，消除与 Spring MVC 的
  `NoSuchMethodError`；
- Controller 启动开关/权限/路由/错误包络合同 7/7 PASS（含 409/412/retryable 状态矩阵、缺 Header 与坏
  JSON 同包络，以及真实 Spring Method Security 在缺少 edit 权限时拒绝进入服务）；Java 17 全反应堆编译
  PASS。与既有 Schema/装配/SemVer/diff/identity/draft/publish/幂等/绑定合计 42/42 PASS：非数据库
  31/31，显式开启
  `TD008_PG_ENABLED=true` 后真实 PostgreSQL 11/11、0 skipped；发布专项 4/4 PASS，专用测试租户均精确
  清理。API 运行配置仍为 false，未执行 canary，当前新增代码尚未提交。下一步评审运行时 HMAC secret
  注入与单一
  canary 租户/产品；获得独立批准前不得开启 API 或写入 canary 数据。
- 运行配置面缺口已关闭并先同步 TD-005 1.0.22 候选：模板编排 API 使用独立
  `EASYAIOT_POWER_MODEL_TEMPLATE_API_ENABLED=false`，已贯通 `application.yaml`、iot-device Compose、
  `env.example`、`PowerModelActivationGuard` 与只读激活预检。启动门禁在 template/binding 任一 API 开启时
  强制 standard/full + `power.device.model` capability + 至少 32 UTF-8 字节 HMAC secret；template API 还
  要求 release/events 已开启，binding API 额外要求 template API 已开启。灰度顺序冻结为
  release→events→template API→binding API，回滚反向执行；
- 配置门禁/Schema/Controller 目标合同 21/21 PASS（Activation 8 + Schema 6 + Controller 7），预检脚本
  PowerShell 语法与 Docker Compose `config --quiet` 均 PASS，`git diff --check` PASS。只读容器核对确认当前
  template API 未设置（默认 false）、binding API=false、release=true、events=true；没有重启容器、注入
  secret、修改数据库或执行 canary。计入前述领域/PG 合同后当前目标集合为 50/50 PASS、0 skipped。
  下一步必须确定 secret 的运行时保管/注入设施和一个无存量设备影响的 canary 租户/产品；目标库现有四个
  产品均有设备，不得直接选作 canary。两项事实明确并取得独立批准后，才可进入 template API 阶段。
- 权限运行准备已同步 TD-005 1.0.23 候选：只读核对确认七个冻结权限位于系统库
  `ruoyi-vue-pro20.public.system_menu`，当前命中 0 行，父菜单为 `产品管理(id=2931)`；形成
  `.scripts/postgresql/td005-permissions/` apply/rollback/README 候选，固定候选 ID 3900～3906，遇到错库、
  父菜单漂移、部分权限已存在或 ID 占用时 fail-closed，仅创建 type=3 权限按钮，不创建页面、不授权角色。
  回滚只删除本候选精确创建且未被角色引用的行。静态安全合同与激活顺序合同 9/9 PASS，当前目标集合更新为
  **51/51 PASS、0 skipped**；本轮没有执行权限 seed、角色授权、目标库写入、secret 注入、容器重启或 canary。
  权限 seed 与 canary 角色授权必须分别取得独立批准，并保留执行前备份与执行后只读核验。
- 2026-08-11 已补齐权限 seed 的冻结只读 preflight、落库后 verify 与
  [窗口申请单](./TD-005权限Seed窗口申请单-20260811.md)：四项 SQL 资产已记录 SHA-256，静态安全合同
  1/1 PASS；目标 `postgres-server / ruoyi-vue-pro20` 新鲜只读复核 PASS（PostgreSQL 18.4、父菜单
  `2931` 精确匹配、七权限=0、候选 ID=0、角色关联=0，事务显式 ROLLBACK）。窗口仍为 OPEN，本批未备份、
  未执行 seed。下一动作必须是 owner 对申请单范围的明确批准；普通“继续”不扩大为数据库写入授权。
- **TD-005 权限 seed 窗口已完成（2026-08-11，TD-005 1.0.24）**：owner 以
  `USER-APPROVAL-20260811-TD005-PERMISSION-SEED` 明确批准自动备份后仅执行七项权限 seed。冻结资产四项
  hash 复核 PASS；只读 preflight PASS；仓库外 custom-format 备份 1,111,985 字节、SHA-256
  `3a4470580ba883acb826cf5e19afdbb4dd029bd27a40d3f1f95c9cd5b51f499e`，容器内 hash 一致且
  `pg_restore -l` 成功。apply 单事务 `INSERT 0 7`；冻结 verify 与独立只读核验确认七行精确匹配、活动
  角色关联=0。template API 仍默认 false、binding API=false、HMAC secret=0 字节，iot-device healthy；未授权
  角色、未启用 API、未注入 secret、未重启容器、未写 canary。容器临时文件已清理，仓库外备份保留。
  后续 canary 角色授权、secret 注入、隔离 canary 产品和 template API 启用仍为四个独立 OPEN 门禁。
- **Canary 角色候选已形成（2026-08-11，TD-005 1.0.25，未授权）**：双库只读画像选出 tenant 122
  “测试租户”——产品/设备/模板/绑定/事件/幂等/collector 14 类事实全为 0；role 111 是该租户唯一活动用户
  所属租户管理员，当前 TD-005 角色关联为 0。候选只增量授予 3900～3902 `read/edit/publish`，明确禁止
  3903～3906；同时如实记录该角色已有 180 项菜单，不是全局最小权限角色，其风险由空测试租户和租户隔离
  补偿。两个冻结 preflight 已在目标库实跑 PASS 并 ROLLBACK，静态安全合同 1/1 PASS，当前目标集合更新为
  **52/52 PASS、0 skipped**。授权窗口申请单已形成但仍为 OPEN；未备份新恢复点、未写 role_menu、未启用
  API、未注入 secret、未创建产品、未执行 canary。
- **HMAC secret 安全注入候选已形成（2026-08-11，TD-005 1.0.26，未执行）**：推荐使用仓库外绝对路径
  文件，经 `DEVICE/docker-compose.power-model-secret.yml` 以 Compose secret 挂载到 Spring Config Tree；
  安全覆盖层把兼容环境变量渲染为空，配置端优先读取挂载文件，避免 secret 出现在容器环境元数据。
  预检只读取环境/文件字节数并报告来源，不输出值或摘要，同时补齐 template-api 阶段的消费组要求。
  Compose 合并解析、PowerShell 语法、`git diff --check` 和静态契约 **1/1 PASS**，当前目标集合更新为
  **53/53 PASS、0 skipped**。窗口申请单已形成但仍为 OPEN；未生成或注入真实 secret、未重建容器、
  未启用 template/binding API，也未执行 Canary 角色授权或写入业务数据。下一动作必须由 owner 独立批准
  “仅注入 secret 并重建 iot-device、两个 API 保持 false”的窗口；普通“继续”不构成该授权。
- **Secret 文件宿主机预检已补齐（2026-08-11，TD-005 1.0.27，未执行窗口）**：新增只读、失败关闭
  `power_model_secret_file_preflight.ps1`，拒绝相对/仓库内/reparse 路径、BOM/换行/非法 UTF-8、短值、
  NUL/空白及宽泛读取 ACL；脚本不输出路径、内容、摘要或字节样本，也不生成、修改或删除文件。静态契约
  **2/2 PASS、0 skipped**，当前目标集合更新为 **54/54 PASS、0 skipped**；运行态和四个独立 OPEN
  门禁不变。行为验证同时确认相对路径以退出码 2 拒绝，仓库外 48 字节严格 UTF-8、收紧 ACL 的临时
  假数据通过全部门禁；临时文件已删除，未接触真实 secret。
- **Secret 注入窗口前运行基线已冻结（2026-08-11，TD-005 1.0.28，只读）**：按 `full`、Topic
  6 分区/复制因子 1/保留 2,592,000,000 ms 的精确参数执行阶段 2 全量预检 **16/16 PASS**。三容器
  healthy，template/binding API=false、release/events=true，迁移 7/7、写链积压 `0/0/0/0`、invalid
  index=0、业务 `4/4/17`，消费组 6 分区在线且 lag=0。该参数集已写入 HMAC 窗口申请单，批准执行时须
  重建前后原样复验。本轮未改变任何运行事实，自动化目标测试仍为 **54/54 PASS、0 skipped**。
- **Secret 注入受控执行器已形成（2026-08-11，TD-005 1.0.29，未执行）**：默认仅做文件/Compose/
  阶段 2 准备检查并返回 `READY_ONLY`；只有 `-Execute` 与固定 owner 批准令牌同时存在才允许仅重建
  `iot-device`。执行后要求 healthy、Config Tree ≥32 字节、明文环境长度 0 和原 16 项基线全 PASS；失败
  自动以基础 Compose 仅回退该服务。禁止 `compose down`，不删除仓库外文件。静态合同新增 1 项，待实际
  测试通过后更新目标集合；本轮没有运行变更路径。
- **Secret 注入执行器验证完成（2026-08-11，TD-005 1.0.29）**：契约测试 **3/3 PASS、0 skipped**，
  当前目标集合更新为 **55/55 PASS、0 skipped**。仓库外临时假数据的完整 `READY_ONLY` 演练通过文件门禁
  与阶段 2 预检 16/16，执行前后 `iot-device` 容器 ID 一致；临时文件已清理。演练发现并修复诊断文本混入
  布尔返回管道的误判风险。未提供 `-Execute` 或批准令牌，运行态、API、角色和数据库均未变化。
- **隔离模板 Canary 资产已形成（2026-08-11，TD-005 1.0.30，未执行）**：为 tenant 122 冻结
  `canary-meter-122` 的 identity/draft/publish 三请求体；模板只有 1 个只读电压测点，events/services
  为空，不含 tenant/actor/draftId/ETag/幂等键/requestId/secret。独立窗口申请单明确 Secret、角色授权、
  template API 开启及单次写入各自先行批准，binding API 始终 false，禁止产品/设备/绑定/collector 写入。
  三请求及生产 Schema SHA-256 已冻结到 manifest，`gitCommit=UNCOMMITTED` 明确阻断执行。请求语义与
  manifest hash 契约 **2/2 PASS、0 skipped**，当前目标集合更新为 **57/57 PASS、0 skipped**；未调用 API、
  未写 tenant 122，也未改变角色、Secret 或容器运行态。
- **模板 API 与运行准备资产已提交（2026-08-11，TD-005 1.0.31）**：资产基准提交为
  `af41b51517bee12e36a50c75b6009e96d76f4dea`；Canary manifest 已回填该提交，三个请求与生产 Schema
  相对提交无漂移。用户配置 `.claude/settings.json`、`CLAUDE.md`、`DEVICE/.claude/` 未纳入提交。该动作
  只关闭资产可追溯门禁，四项运行批准仍为 OPEN。
- **提交后 Canary 只读复验与 UTF-8 调用修复（2026-08-11，TD-005 1.0.32）**：首次复验因 Windows
  PowerShell native pipeline 未固定 UTF-8，中文租户名被误编码并触发假 `TENANT_MISMATCH`；独立事实查询
  确认 tenant/role/用户/权限关联无漂移。显式 UTF-8 后，角色与 tenant 空数据 preflight、阶段 2 运行基线
  全部 PASS。新增只读封装入口，只接受两份含 `BEGIN TRANSACTION READ ONLY` 且无 `COMMIT` 的 SQL，绝不
  引用 apply/rollback；封装实跑 PASS、两事务均 ROLLBACK，扩展合同 **2/2 PASS、0 skipped**，当前目标
  集合更新为 **58/58 PASS、0 skipped**。无数据库或容器写入。
- **仓库外 HMAC Secret 已生成（2026-08-11，TD-005 1.0.33，尚未注入）**：owner 仅批准建议的仓库外
  路径生成，系统 CSPRNG 48 随机字节编码为 64 字节 Base64；严格 UTF-8、无 BOM/换行/NUL、非 reparse、
  仓库外和 ACL 门禁全部 PASS，宽泛读取主体为 0。未记录 Secret 内容或摘要，未重建 `iot-device`，
  template/binding API=false，角色与数据库不变。下一步仍需独立批准 Secret 注入与单服务重建窗口。
- **HMAC Secret 注入窗口已完成（2026-08-11，TD-005 1.0.34）**：owner 以精确令牌批准仅重建
  `iot-device`。首次执行因 Kafka 消费组尚在重新入组而失败关闭并自动回退；回退后完整基线恢复。执行器
  增加 6×5 秒有界等待并修复回退结果输出，`READY_ONLY` 与契约 **4/4 PASS** 后重试成功。最终容器
  healthy、Config Tree=64 字节、明文环境=0、template/binding API=false、阶段 2 16/16 PASS、消费组
  6 分区在线 lag=0、数据库积压 `0/0/0/0`、业务 `4/4/17`。role 111 关联=0、tenant 122 残留=0；当前
  目标集合更新为 **59/59 PASS、0 skipped**。未授权角色、未启用 API、未写 Canary 数据。
- **Canary 角色最小增量授权已完成（2026-08-11，TD-005 1.0.35）**：owner 以
  `USER-APPROVAL-20260811-TD005-CANARY-ROLE-GRANT` 批准新备份成功后，仅向 tenant 122 / role 111
  授予菜单 3900～3902。五项冻结 hash 与双库 preflight PASS；仓库外 custom-format 备份 1,112,181 字节、
  SHA-256 `8bfc32f04b2075f00a0dc49e3e68f7cd2428c266bb42b875116e09b009b8062b`，hash 一致且
  `pg_restore -l` 1024 条目 PASS。apply 单事务 `INSERT 0 3`；verify 精确返回 read/edit/publish，3903～3906
  为 0，tenant 122 残留仍为 0。template/binding API=false、Secret 未修改、`iot-device` 未重启、阶段 2
  **16/16 PASS**。下一步仍需独立批准 template API 启用；本授权不允许执行任何 Canary 请求。
- **Template API 独立启用窗口已完成（2026-08-11，TD-005 1.0.36）**：新增独立 Compose 覆盖层和
  默认只读、失败自动回退的执行器；Java 17 reactor 构建及合同 **6/6 PASS**，`READY_ONLY` 验证容器
  ID/启动时间不变，自动化目标集合更新为 **61/61 PASS、0 skipped**。owner 以
  `USER-APPROVAL-20260811-TD005-TEMPLATE-API-ACTIVATION` 精确批准仅重建 `iot-device`；最终 healthy、
  template API=true、binding API=false、release/events=true、Config Tree Secret=64 字节、明文=0，
  template-api 阶段 **16/16 PASS**、消费组 6 分区在线 lag=0。role 111 仍仅 3900～3902，tenant 122
  残留仍为 0；未调用任何 API、未写 Canary 数据。下一步是独立的单次隔离模板 Canary 写入批准。
- **Canary 前检发现并修复网关路由代码缺口（2026-08-11，TD-005 1.0.37，未部署）**：冻结三请求与
  `af41b515` 基准提交、manifest 和生产 Schema hash 全部一致；template-api 阶段 16/16、角色三项、禁止
  权限 0、tenant 122 残留 0。只读认证画像确认唯一活动用户为 `113/aoteman`，未过期 token 数量为 0；
  同时确认现行网关没有 `/api/v1/power/**` 路由。新增 `device-power-model-api` 原样转发到
  `device-server`，明确无 StripPrefix/RewritePath；资产/路由合同 **3/3 PASS**，`iot-gateway` Java 17
  package BUILD SUCCESS，自动化目标集合更新为 **62/62 PASS、0 skipped**。本轮未重建网关、未读取或
  生成 token、未调用 API、未写 Canary。下一步必须先独立批准仅部署该网关路由；随后由 user 113 正常登录
  取得短时令牌，最后才可申请单次 Canary 写入。
- **公共网关路由已完成首次部署（2026-08-11，TD-005 1.0.38）**：首次窗口预检确认运行态不存在
  `iot-gateway` 容器或 `iot-gateway:latest` 镜像，原回退标签前提不成立并安全停止；owner 随后以
  `USER-APPROVAL-20260811-TD005-POWER-API-GATEWAY-FIRST-DEPLOY` 批准无网关基线下首次部署。仅构建并以
  `--no-deps` 创建 `iot-gateway`，最终 healthy；容器内 JAR 与冻结构建 JAR SHA-256 一致。`iot-device`、
  `iot-system`、`iot-infra`、PostgreSQL、Kafka 容器 ID/启动时间均未变化，template-api 阶段 **17 项
  PASS**，role 111 仍仅 3900～3902，tenant 122 残留 0。未获取 token、未调用业务 API、未写 Canary。
  下一步由 `user 113 / aoteman` 正常登录取得未过期短时令牌；登录/token 不属于本窗口，完成后再申请
  identity→draft→validate→publish 单次 Canary 写入批准。
- **Canary 登录面前检与独立窗口已形成（2026-08-11，TD-005 1.0.39，未部署）**：只读画像确认
  tenant 122、user 113 均启用，用户仅有 role 111；允许权限仍为 3、禁止权限 0、未过期 token 0。默认
  OAuth2 client 的访问令牌有效期为 1800 秒，后续必须 `rememberMe=false`。网关和系统服务 healthy，但
  当前无 `web-service` 容器、无 `web-service:latest` 镜像、无 8888 登录面。WEB Dockerfile 默认并可显式
  构建 `VITE_GLOB_DEPLOY_PROFILE=full`，无需改源码。已形成仅构建并首次创建 WEB 的独立窗口申请；本轮
  未部署 WEB、未登录、未读取密码/token、未调用业务 API、未写 Canary。下一步先批准 WEB 首次部署，
  再由用户本人在浏览器输入现有凭据完成独立认证窗口。
- **Canary WEB 登录面已首次部署（2026-08-11，TD-005 1.0.40）**：owner 以
  `USER-APPROVAL-20260811-TD005-CANARY-WEB-FIRST-DEPLOY` 精确批准后，仅以 full 构建
  `web-service:latest` 并用 `--no-deps` 首次创建容器；Vite/postBuild 成功，容器 healthy，8888→80 映射
  生效。gateway/system/device/PostgreSQL/Kafka 均未重建，template-api 阶段 **17 项 PASS**，token 仍为
  0、允许权限 3、禁止权限 0、tenant 122 残留 0。未打开页面、未登录、未调用 API、未写 Canary。已形成
  独立浏览器认证窗口，要求用户本人输入现有凭据、`rememberMe=false`、token 仅留浏览器且不导出；下一步
  先取得该认证窗口批准，仍不等同于 Canary 写入批准。
- **浏览器认证窗口获批但未执行（2026-08-11，TD-005 1.0.41）**：owner 以
  `USER-APPROVAL-20260811-TD005-CANARY-BROWSER-AUTH` 批准后，应用内浏览器控制在导航前被主机用户配置
  访问权限阻断，未打开 8888、未发租户/验证码/login/permission-info 请求。按安全规则未切换未批准的
  浏览器控制面。事后 token 仍为 0、权限 3/0，WEB/gateway/system/device 容器均 healthy 且 ID/启动时间
  未变化。下一步只能二选一：修复应用内浏览器连接后沿用原批准重试，或由 owner 另批 Chrome CDP 可见窗口；
  两种路径均继续要求用户本人输入凭据、阻断非认证 API且不导出 token。
- **Chrome CDP 认证续作检查点（2026-08-11，未关闭）**：登录对象已改为 tenant 123 `codex测试` /
  user 132 `aotemane` / role 112，role 112 仅获 3900～3902。已处置首次错误租户登录产生的 tenant 1 /
  user 115 六条令牌；已修复并仅部署 WEB 的重复登录表单、手工租户优先、回车验证码和 Axios 空值保护，
  当前 `web-service` healthy。新流程已正确使用 `get-id-by-name`，login 与 permission-info 均 HTTP 200，
  但因认证成功后固定进入 Dashboard，严格 allowlist 阻断 `/system/dict-data/list-all-simple` 及后续
  `/video/alert/**`，UI 仍显示 `Network Error`。tenant 123 / user 132 当前存在 access IDs
  6102/6104/6106 与 refresh IDs 6101/6103/6105，认证窗口不得继续重试。下一步先独立批准精确撤销这六行
  并清除浏览器会话，再实现 permission-info 后停止、完全不进入 Router/Dashboard 的认证-only 测试入口；
  不得用扩大 dict/video/业务 API allowlist 规避问题。详细证据见
  [`browser-cdp-auth-progress-20260811.md`](./assets/td005-canary/browser-cdp-auth-progress-20260811.md)。当前仍未调用
  电力 API、未写模板 Canary，identity→draft→validate→publish 独立写入门禁继续 OPEN。
- **tenant 123 / user 132 令牌处置窗口已完成（2026-08-11）**：owner 以
  `USER-APPROVAL-20260811-TD005-TOKEN-DISPOSAL-6101-6106` 精确批准后，先完成仓库外 custom-format
  全库备份（1,117,845 字节，SHA-256
  `c6b62581ff91d9517e460f423a32dcd769e0cddd061e0e8746f127635dc11d64`，TOC 1,039 行 PASS），再单事务
  软撤销 access IDs 6102/6104/6106 与 refresh IDs 6101/6103/6105。前后断言 PASS、两次均 `UPDATE 3`；
  六行 `deleted=1`、`expires_time` 未变，user 132 active access/refresh=0，全库活动计数从 2689/2689
  精确降为 2686/2686。role 112 仍为允许 3/禁止 0，PostgreSQL 容器 healthy 且未重启。下一步不是重新
  登录，而是先关闭认证-only harness 的“不得读取 token 值”和“失败一次即锁止”静态门禁并完成构建验证；
  harness 部署、重新认证和 Canary 写入仍分别需要独立批准。
- **认证-only harness 代码门禁已关闭（2026-08-11，未部署）**：独立入口仅在
  `VITE_TD005_AUTH_HARNESS=true` 构建时注册；已移除 token 存在性读取、页面会话清理和状态重置入口，
  首次租户查询前即永久锁定一次尝试，验证码成功/失败也由同一锁保证最多进入一次认证链。实际代码仅各有
  一次 `loginApi` / `getUserInfo` 调用，不导入 Router、Dashboard、dict 或 video 依赖；认证成功停在
  permission-info。`harness=true` 的 Vite 生产构建 PASS（192.2 秒），产物包含独立入口；仅出现仓库既有
  Vue 宏与 Rollup 循环分块警告。ESLint 因既有 `micromark` 非导出子路径错误无法启动；全量
  `vue-tsc` 仍被仓库既有跨模块类型错误阻断，过滤输出未发现 `td005-auth-harness` 局部错误，故这两项门禁
  不标记 PASS。当前运行中的 `web-service` 未重建，未打开浏览器、未登录、未调用 API、
  未清会话、未修改数据库或容器。下一步必须先形成并获得仅部署该 harness WEB 构建的独立批准；部署后仍需
  新的单次认证批准，Canary 写入继续是第三个独立门禁。
- **认证-only harness WEB 已完成受控部署（2026-08-11）**：owner 以
  `USER-APPROVAL-20260811-TD005-AUTH-HARNESS-WEB-DEPLOY` 精确批准后，仅按
  `VITE_GLOB_DEPLOY_PROFILE=full`、`VITE_TD005_AUTH_HARNESS=true` 构建并以 `--no-deps` 重建
  `web-service`。新镜像
  `sha256:6789fb7c54fb480c848679f1786ff86c2165de555c40aed583b253857377c89e`、新容器
  `7f1b877fe479` 均 healthy/restartCount=0，运行静态文件包含 harness 路由与页面标识；原镜像
  `sha256:cca2c5c4df72206769a1157e63c7f7ea1319f1c063fe6fd60ed09cc57998a047` 已保留专用回退标签。
  其他容器 ID 全部未变；窗口前既有的 ZLMediaKit unhealthy 未越权处置。未打开浏览器、未登录、未调用 API、
  未改数据库/配置且未写 Canary。下一步是新的认证-only 单次浏览器批准；该批准仍不得扩展为 Canary 写入。
- **认证-only harness 单次认证已验收通过（2026-08-11）**：owner 以
  `USER-APPROVAL-20260811-TD005-AUTH-HARNESS-SINGLE-AUTH` 精确批准，用户本人在独立可见 Chrome 输入 tenant
  123 `codex测试` / user 132 `aotemane` 的现有凭据与验证码，`rememberMe=false`。tenant、captcha、login、
  permission-info 四步均 `ok`，页面停在 harness 且永久锁定，未进入 Dashboard；Nginx 日志按 harness referrer
  核对只出现五类批准认证端点，login 精确一次，无 dict/video/power/其他业务 API。只读数据库元数据确认
  新增 access ID 6114（20:42:26 到期）与 refresh ID 6113 各一条，active=1/1；未查询 Token 字段，未刷新、
  未改容器/数据库/配置且未写 Canary。identity→draft→validate→publish 写入继续等待独立精确批准。
- **隔离模板 Canary 资产已重定向并冻结 tenant 123（2026-08-11，未执行）**：TD-005 升 1.0.43，
  identity/draft/publish 从 `canary-meter-122` 统一改为 `canary-meter-123`，描述、发布原因、README、窗口申请
  和生产合同测试同步 role 112 / user 132；manifest 升 1.1.0，三个请求 SHA-256 更新为
  `db379af5…67c3` / `bb2090f6…2824` / `beeb9544…2413`，生产 Schema hash 仍为 `2431b8e7…bae5`。
  聚焦资产基准提交 `1ec8e801d33436b7d176709c45c115faefe3b41c` 已形成并回填 manifest；Java 17 完整
  请求/Schema/manifest/网关合同 **3/3 PASS**，逐字节 hash 与 40 位提交格式门禁关闭。tenant 123 十四类
  空事实的新鲜度复核仍未执行；下一步只允许准备并运行 read-only tenant 123 前检，关闭前不得申请 Canary 写入。
- **tenant 123 Canary 只读运行前检已通过（2026-08-11，TD-005 1.0.44，未写入）**：新增双库只读 SQL、
  UTF-8 包装器及防漂移合同，Java 17 完整合同 **4/4 PASS**。实际在 `postgres-server` 执行后，两事务均
  `ROLLBACK`；tenant 123 / user 132 / role 112 身份精确匹配，允许菜单 3900～3902=3、禁止 3903～3906=0，
  `iot-device20` 十四类 Canary 业务事实残留=0。20:33:11 只读核对 access 6114 / refresh 6113 的 ID、状态和
  到期时间均 active，未查询 Token 字段。运行前身份/权限/空事实新鲜度门禁已关闭；下一步是 owner 独立批准
  identity→draft→validate→publish 单次 Canary 写入，现有认证和只读前检均不构成该授权。
- **认证重试失败、令牌已收敛，网络门禁修复已构建但未部署（2026-08-11，TD-005 1.0.45）**：重认证
  前已无读取地清空浏览器两类 Web Storage；20:45:50 tenant 401 后旧 Axios 流程调用白名单外 logout 401，
  因此即使随后 tenant/captcha/login/permission-info 全 200 且 login 一次，窗口仍按失败关闭。独立批准后，
  仓库外备份 1,118,259 字节、SHA-256 `7fc73563…bc16`、TOC 1,039 行 PASS；单事务仅软撤销 access
  6114/6116 与 refresh 6113/6115，各 `UPDATE 2`，user 132 未删除令牌恢复 0/0，权限 3/0、十四类事实 0。
  新门禁作为 harness 页面最先执行模块，先全拒绝五类网络面，再开放五个认证 API；logout/refresh/跨域/
  部分安装失败合同 PASS，full+harness 生产构建 PASS。当前 WEB 尚未部署修复；下一步须先独立批准仅重建
  web-service，之后另批单次认证，Canary 写入继续 OPEN。详见
  [`auth-harness-reauth-failure-containment-20260811.md`](./assets/td005-canary/auth-harness-reauth-failure-containment-20260811.md)。
- **页面内置认证网络门禁 WEB 已受控部署（2026-08-11，TD-005 1.0.46）**：owner 以
  `USER-APPROVAL-20260811-TD005-AUTH-HARNESS-NETWORK-GATE-WEB-DEPLOY` 精确批准，仅以 full+harness=true
  构建并 `--no-deps` 重建 web-service。旧镜像 `6789fb7c…c89e` 保留专用回退标签；新镜像
  `fd7c5887…c1e3a`、新容器 `f96616cd0756…` healthy/restartCount=0。运行资产 `index-c6336efc.js` 命中
  fail-closed 与 blocked 常量，其他 20 个容器 ID 20/20 未变化。未打开浏览器、未登录、未调用 API、未改
  数据库或配置、未写 Canary。下一步是新的单次认证批准；认证成功也仍不等同于 Canary 写入批准。
- **REAUTH-V2 认证与运行前新鲜度门禁已通过（2026-08-11，TD-005 1.0.47）**：owner 以
  `USER-APPROVAL-20260811-TD005-AUTH-HARNESS-REAUTH-V2` 精确批准；无读取地清除两类 Web Storage 后，
  页面内置门禁在输入前为 true。用户本人完成 tenant 123 / user 132 登录，四步均 ok、login 精确一次，页面
  保持 harness 且未出现白名单外 API。只读元数据确认 access 6118 / refresh 6117 active=1/1，未查询 Token
  字段；21:34 双库 READ ONLY 前检再次 PASS 并 ROLLBACK，权限 3/0、十四类业务事实残留 0。当前唯一下一
  门禁是 owner 独立批准冻结资产的 identity→draft→validate→publish 单次 Canary 写入；认证批准不得外推。
- **模板 Canary 首次单次写入在 identity 404 后安全停止（2026-08-11，TD-005 1.0.48）**：owner 以
  `USER-APPROVAL-20260811-TD005-CANARY-TEMPLATE-SINGLE-WRITE` 精确批准；冻结 hash、access 元数据、双库
  READ ONLY 前检和 iot-device20 仓库外 custom-format 备份（447,615 字节、SHA-256 `724464e6…ac99ac6`、
  TOC 1,346）均 PASS。首个 identity 返回 HTTP 404 后立即停止，draft/validate/publish 均未调用；失败后权限
  3/0、十四类业务事实仍为 0。根因是运行 iot-device JAR 缺少模板 Controller/Service 类，虽 template=true
  仍无真实路由。下一步必须另批仅重建 iot-device 的当前源码镜像修复窗口，验收运行类与路由后重新认证，
  再申请新的单次 Canary；本次批准不得复用。
- **identity 404 浏览器与令牌收敛已完成（2026-08-11，TD-005 1.0.49）**：owner 以
  `USER-APPROVAL-20260811-TD005-CANARY-404-TOKEN-CONTAINMENT` 精确批准；无读取地清除 Chrome 两类
  Web Storage，未清 Cookie/缓存。系统库仓库外 custom-format 备份 1,118,403 字节、SHA-256
  `8804eea3…bc1619`、TOC 1,039 行 PASS；单事务仅软撤销 access 6118 / refresh 6117，两行 deleted=1，
  user 132 active=0/0，未查询 Token 字段。最终权限 3/0、十四类业务事实残留 0，容器均 healthy 且未重建。
  下一步是独立 iot-device 当前源码镜像修复部署；之后必须重新认证并另批新的单次 Canary。
- **iot-device 模板 API 镜像修复前检已完成（2026-08-11，TD-005 1.0.50，未部署）**：旧暂存 JAR
  `49469f2c…d5c04` 缺少四个模板类；本机 Maven 用户 settings 默认 Java 8 覆盖仓库 Java 17，首次构建因此被
  文本块编译门禁拒绝。未改 settings/POM，改由命令显式锁定 Java 17 后反应堆 33/33 BUILD SUCCESS；新候选
  与暂存 JAR hash 均为 `953cc5d9…0346d6`，四个模板类存在，模板 Controller + Canary 资产合同 11/11 PASS。
  尚未构建镜像、重建容器或调用 API。下一步须 owner 独立批准仅构建镜像并重建 iot-device，保留 Secret、
  template=true、binding=false，验收运行类和一次未认证非 404 路由探针；失败仅回退 iot-device。
- **iot-device 模板 API 镜像修复部署失败并安全回退（2026-08-11，TD-005 1.0.51）**：owner 以
  `USER-APPROVAL-20260811-TD005-IOT-DEVICE-TEMPLATE-API-IMAGE-REPAIR-DEPLOY` 精确批准；新镜像及四类检查
  通过，仅重建 iot-device，但新容器因 `PowerModelActivationGuard` 解析到的 Secret 少于 32 字节而未 healthy。
  挂载文件实际存在、可读且为 64 字节，定位为 Config Tree 静态合同未覆盖真实 Spring 属性绑定。路由探针和
  API 均未执行；脚本仅回退 iot-device 至旧镜像 `4fa86930…705b`，当前 healthy。其他相关容器未变化，最终
  权限 3/0、十四类残留 0。下一步先修复 Secret 真实运行时解析并补隔离启动合同，再申请新部署窗口。
- **Config Tree Secret 运行时解析缺口已在源码关闭（2026-08-11，TD-005 1.0.52，未部署）**：Compose
  Secret 文件名已直接映射最终属性 `easyaiot.power-model.idempotency-hmac-secret`，移除失效的中间属性
  占位解析；明文环境变量仍只作为空值兼容回退。新增真实 Spring Config Data 临时目录启动合同，覆盖挂载存在
  和挂载缺失两条路径；静态挂载合同、启动门禁与动态绑定聚焦测试 16/16 PASS。Java 17 反应堆 33/33
  BUILD SUCCESS；新暂存 JAR 为 279,652,971 字节，SHA-256 `54bedaec…3b009`，模板四类齐全且内置配置不含
  旧中间键。本轮未读取/修改仓库外 Secret，未构建镜像、重建容器、调用 API 或修改数据库。当前 iot-device
  仍为回退旧镜像且 healthy；下一步申请新的仅 iot-device 部署审批，旧批准不得复用。证据见
  [`iot-device-configtree-runtime-repair-preflight-20260811.md`](./assets/td005-canary/iot-device-configtree-runtime-repair-preflight-20260811.md)。
- **Config Tree 最终键部署仍失败并安全回退（2026-08-11，TD-005 1.0.53）**：owner 以
  `USER-APPROVAL-20260811-TD005-IOT-DEVICE-CONFIGTREE-RUNTIME-REPAIR-DEPLOY` 精确批准；部署前候选 hash、
  Secret 形状、运行开关与双库只读门禁 PASS。仅重建 iot-device 后，新容器仍因 ActivationGuard 取得空
  Secret 而未 healthy，故未执行路由探针或任何 API。自动以旧镜像专用旧键覆盖层恢复 `4fa86930…705b`；
  当前 iot-device healthy/restartCount=0，其他容器未变化，回退后权限 3/0、十四类残留 0。真实仓库多文档
  application YAML 动态合同 3/3 PASS，实际容器属性源差异仍 OPEN。下一步实现 fail-closed 直接文件 provider，
  完成测试和新候选后再另批部署；同一候选与本次批准均不得复用。证据见
  [`iot-device-configtree-runtime-repair-deploy-attempt-20260811.md`](./assets/td005-canary/iot-device-configtree-runtime-repair-deploy-attempt-20260811.md)。
- **直接文件 provider 代码与候选 JAR 已就绪（2026-08-12，TD-005 1.0.54 候选，未部署）**：评审 1.0.53 §4
  `DIRECT_FILE_PROVIDER_REQUIRED` 后，新增 `PowerModelIdempotencySecretProvider`（`@Component`，fail-closed 读
  `EASYAIOT_POWER_MODEL_HMAC_SECRET_FILE` 指向的文件，启动期严格校验绝对路径/普通文件/UTF-8/无 BOM/无换行/无
  NUL/≥32 字节，异常消息只含原因类别不泄密）；`PowerModelActivationGuard` 与 4 写服务（Binding/Publish/Identity/
  Draft）统一构造注入 provider，删除全部 `@Value` secret 与 `spring.config.import: optional:configtree:/run/secrets/`；
  Compose secret target 改为不含 `.` 的 `easyaiot_power_model_hmac`，activation preflight 同步检查新路径
  `direct-file-provider`；删除废弃的 legacy 回滚覆盖层与 `PowerModelConfigTreeRuntimeContractTest`。Java 17 main
  + test 编译 PASS，provider/ActivationGuard/SecretMount 合同 **30/30 PASS**（provider 16 + Guard 8 + SecretMount
  6），4 写服务集成测试改 mock provider 后 test-compile PASS；静态合同 grep 无 `@Value` secret 残留、无
  `configtree`、provider 源码无日志输出。候选 JAR 暂存 `DEVICE/target/jars/iot-device-biz.jar`，279,673,823 字节，
  SHA-256 `9dd19363303769c27c7a98dff344fa37e50a5c5a1f639a6d8118797e603ff421`，含 provider +
  `PowerModelTemplateController` + 4 写服务全部类。本轮未部署、未调用 API、未改运行态、未触碰仓库外 Secret。
  下一步须 owner 独立批准仅重建 iot-device 的部署窗口（见
  [TD-005直接文件Provider部署窗口申请单-20260812](./TD-005直接文件Provider部署窗口申请单-20260812.md)），1.0.51/
  1.0.52/1.0.53 旧批准均不得复用。
- **直接文件 provider iot-device 部署成功（2026-08-12，TD-005 1.0.55）**：owner 以
  `USER-APPROVAL-20260812-TD005-IOT-DEVICE-DIRECT-FILE-PROVIDER-DEPLOY` 精确批准后，以候选 JAR SHA-256
  `9dd1936…` 构建新镜像 `3860dce5da25`（镜像内 `/app/app.jar` hash 精确匹配候选），`--no-deps --force-recreate`
  重建 iot-device；Secret 挂载从 legacy `-file-content`（含 `.` target）切换到直接文件 provider 的
  `easyaiot_power_model_hmac`（不含 `.`，杜绝 Config Tree 文件名映射）。新容器 `52d798735144` **healthy**
  （~40s）、restartCount=0；启动日志**无** `POWER_MODEL_IDEMPOTENCY_SECRET_INVALID`（1.0.51/1.0.52/1.0.53 三次
  Config Tree 失败的根因正式解除）、无 `POWER_MODEL_ACTIVATION_INCOMPLETE`，`Started DeviceServerApplication
  in 22.87s`、Tomcat 48083。路由探针 `POST /api/v1/power/model-templates` → **HTTP 400**（1.0.48 是 404，证明
  `PowerModelTemplateController` 路由已注册，identity 404 根因正式解除）。数据库业务 `4/4/17`、迁移 V001～V007
  = 7 SUCCEEDED、积压 `0/0/0/0` 无漂移；其他 6 容器（iot-system/iot-infra/postgres-server/kafka-server/
  iot-gateway/web-service）ID 与启动时间全部不变。仓库外 iot-device20 备份 SHA-256 `92dfc45b…`；旧镜像
  `4fa869302238` 保留回退标签 `rollback-td005-direct-file-provider-predeploy-20260812`。下一步：owner 独立批准
  harness REAUTH-V3 认证 → identity→draft→validate→publish 单次 Canary 模板写入 → 收尾提交。详见
  [`iot-device-direct-file-provider-deploy-execution-20260812.md`](./assets/td005-canary/iot-device-direct-file-provider-deploy-execution-20260812.md)。
- **REAUTH-V3 harness 认证成功（2026-08-12，TD-005 1.0.56）**：owner 批准
  `USER-APPROVAL-20260812-TD005-REAUTH-V3` 后，用户本人在独立 Chrome 完成 `/td005-auth-harness` 认证
  （tenant 123 `codex测试` / user 132 `aotemane` / rememberMe=false），四步全绿（tenant/captcha/login/
  permission-info），页面停在 harness 未进 Dashboard，网络门禁未触发业务 API。`ruoyi-vue-pro20` user 132
  新增 active access 1 条（ID 6120，expires 2026-08-12 00:55:45，未过期）、active refresh 1 条（ID 6119，
  expires 2026-08-13 00:25:45，未过期），未查询 token 值；`iot-device20` tenant 123 业务仍 `0/0/0/0`，
  role 112 权限仍 `3/0`，6 容器 ID/启动时间不变。当前唯一未关闭门禁是 owner 独立批准
  identity→draft→validate→publish 单次 Canary 模板写入（冻结资产 `canary-meter-123`）；本认证不构成 Canary
  写入授权。详见
  [`auth-harness-reauth-v3-window-20260812.md`](./assets/td005-canary/auth-harness-reauth-v3-window-20260812.md)。
- **单次 Canary 写入窗口申请单已就绪（2026-08-12，TD-005 1.0.57 候选，未执行）**：V3 认证获得 access 6120 /
  refresh 6119 后，形成 [TD-005单次模板Canary写入窗口申请单-20260812](./TD-005单次模板Canary写入窗口申请单-20260812.md)，
  冻结资产 identity/draft/publish + 生产 Schema hash（manifest 1.1.0，gitCommit `1ec8e801d33436b7d176709c45c115faefe3b41c`）。
  **时效约束**：access 6120 expires 2026-08-12 00:55:45（~30 分钟有效），Canary 写入（identity→draft→validate→publish
  4 API）须在 token 有效期内完成；**过期则窗口失效，需 REAUTH-V4（用户本人 harness 重新认证）+ 新写入批准**。执行方式待
  owner 决定（浏览器 DevConsole sessionStorage token / 受控 CDP 脚本），token 不导出/不入库/不进聊天。本轮未调用电力 API、
  未写 Canary、未改容器/Secret/角色/数据库业务数据。**下次会话续作起点**：若 access 6120 未过期且 owner 批准
  `USER-APPROVAL-20260812-TD005-CANARY-TEMPLATE-SINGLE-WRITE-V2`，执行 4 API 写入并验收（power_model_template
  PUBLISHED + member_index + outbox POWER_MODEL_TEMPLATE_PUBLISHED_V1 PENDING）；若 token 已过期，先 REAUTH-V4
  （[TD-005Canary REAUTH-V3 认证窗口](./assets/td005-canary/auth-harness-reauth-v3-window-20260812.md) 流程复用）再申请写入。
  当前运行态：iot-device `52d798735144`（直接文件 provider 1.0.55，healthy）、template=true、binding=false、release/events=true、
  Secret 64 字节挂载 `/run/secrets/easyaiot_power_model_hmac`；其他容器不变；仓库外 iot-device20 备份 `92dfc45b...` 保留。
- **单次 Canary 模板写入端到端成功（2026-08-12，TD-005 1.0.57）**：1.0.57 候选后 access 6120 过期，先 REAUTH-V4
  产生 access 6122 / refresh 6121（user 132 / tenant 123）；途中 web-service 崩溃（nginx.conf L252 `rtc-host` 解析失败），
  owner 批准选项 A 修复（`rtc-host` → `srs-host`）后恢复 healthy。owner 批准
  `USER-APPROVAL-20260812-TD005-CANARY-TEMPLATE-SINGLE-WRITE-V2` 后，用户本人在 harness tab DevConsole 执行受控脚本
  （token 留 `localStorage.jwt_token` 不导出，iframe native fetch 绕过 harness 网络门禁），按序调用 identity→createDraft
  →validate→publish 4 API，body 与冻结资产（manifest 1.1.0，gitCommit `1ec8e801`）逐字节一致。identity **201**
  （templateId `8382352661430272`）、draft **201**（draftId `8382353080860672`，etag `"0"`，contentHash
  `sha256:f97ceb90…`）、validate **200**（valid=true, errors=[]）、publish **200**（lifecycle PUBLISHED）。数据库只读核验：
  tenant 123 `power_model_template` 1 行 PUBLISHED、`power_model_member_index` 1 行（PROPERTY voltage-a）、
  `power_model_release_outbox` 1 行 POWER_MODEL_TEMPLATE_PUBLISHED_V1 **status=PUBLISHED**（Outbox relay 已投递 Kafka）、
  `power_model_event_inbox` 1 行（消费者已消费，**ADR-014 端到端事件链验证**）。业务基线 `4/4/17`（模板不影响产品）、
  迁移 `7/7`、role 112 权限 `3/0` 无漂移。**M1 电力物模型模板发布链首次端到端成功**（1.0.48 identity 404 → 1.0.57 PUBLISHED），
  直接文件 provider（1.0.55）+ 模板 Controller（1.0.50 修复）+ Outbox/Inbox（ADR-014）全链路验证通过。详见
  [`canary-template-write-execution-20260812.md`](./assets/td005-canary/canary-template-write-execution-20260812.md)。
  下一步：token 处置（access 6120/6122 + refresh 6119/6121 撤销）+ 收尾提交（删 `.td005-auth-allowlist.js` + 提交全部代码/证据）。
- **Canary token 处置完成（2026-08-12，TD-005 1.0.58）**：owner 以
  `USER-APPROVAL-20260812-TD005-TOKEN-DISPOSAL-6119-6122` 批准后，仓库外备份 ruoyi-vue-pro20（1,118,689 字节，
  SHA-256 `026788bedcc5190928078336facb1ba6c365af45265fe02e1e4aa04e98d3ce97`），单事务软撤销 user 132 的
  access 6120/6122 + refresh 6119/6121（各 `UPDATE 2`）。4 行 `deleted=1`，user 132 active access/refresh 恢复 `0/0`；
  全库活动计数 2689/2689 → 2687/2687；业务基线 `4/4/17` + canary-meter-123 模板 1 行保留作 M1 证据、role 112 权限 `3/0` 不变。
  Canary 写链闭环清理完成。剩余收尾：删除临时 `.td005-auth-allowlist.js` + git 提交全部改动（provider + harness +
  nginx.conf + 测试 + 全部申请单/证据 + 进度入口）。
- **收尾提交完成（2026-08-12，TD-005 1.0.59，commit `857184e9`）**：删除临时 `.td005-auth-allowlist.js`，
  git 提交 Canary 闭环全部改动（27 files, +851/-196）：直接文件 provider + Guard/4 写服务注入 +
  application.yaml/compose/preflight + nginx.conf rtc-host 修复 + 全部测试（30/30 PASS）+ 3 申请单 + 3 执行证据
  + 进度入口 1.0.54→1.0.58。排除用户配置（`.claude/settings.json`、`CLAUDE.md`）。**TD-005 Canary 写链端到端闭环完成**
  （1.0.48 identity 404 → 1.0.57 PUBLISHED → 1.0.58 token 清理 → 1.0.59 提交）。
- **M1 采集主线 P0 启动：决策签字 + TDengine Spike + SQLite 核心持久性 Spike（2026-08-12）**：C 梳理确认
  TD-001/002/003 采集主线几乎零代码（SQLite Outbox/Envelope V1/中心 Inbox 全未实现）。owner 签字 6 项决策
  （[裁定记录](../../开发规范/TD-002-003采集主线决策裁定记录-20260812.md)）：SQLite JDBC **3.46.x LTS**、TDengine
  **保留 3.1.0**、H-03/M-04/M-03 确认、unknown_ack 12 次候选。TDengine CLI Spike 验证确定性幂等
  （同 message_id+ts → 物理行=1，upsert 覆盖，不依赖 exactly-once）。P0-3 SQLite 核心持久性 Spike **6/6 PASS**
  （`SqliteWalFullDurabilitySpikeTest`）：WAL+synchronous=FULL 已 commit 跨连接关闭持久、未 commit 丢失、
  批量原子、messageId 主键约束、PRAGMA 验证；发现"SQLite 关闭最后连接自动 checkpoint"。§21-④ 完整子项
  （真崩溃子进程/ENOSPC/损坏页/dispatch 索引/候选参数压测）待续。
- **P0-3 §21-④ Spike 续：损坏检测 + 索引 + 真崩溃 WAL replay（2026-08-12）**：扩展 SQLite 证据包。
  `SqliteCorruptionIndexSpikeTest` 4/4 PASS（损坏主库触发 `SQLITE_CORRUPT` 不静默重建 §13、claim 查询用
  dispatch 索引 `idx_outbox_dispatch`、message_id 走 PK 索引、integrity_check=ok）。`CrashReplayTest` 1/1 PASS：
  子进程 `CrashWriter` commit 50 行后 `halt(137)` 模拟 kill -9（不关闭连接、不执行 shutdown checkpoint），
  WAL 文件保留，重开 SQLite replay 恢复全部 50 行——§21-④ 最严格掉电证据。**P0-3 SQLite 证据累计 11 测试 PASS**
  （核心持久性 6 + 损坏/索引 4 + 真崩溃 1）。ENOSPC + 候选参数压测需特殊环境（小磁盘/性能基准），标 OPEN。
- **P0-3 §21-④ 候选参数压测 Spike（2026-08-12）**：`SqliteLoadSpikeTest` 3/3 PASS——appendBatch 10000 行 @ 500/批
  **132667 rows/s**（候选参数 500 envelope/批可行性，远超 1000 阈值）、PK lookup/claim **0ms**（10k 行）、
  incremental_vacuum 页数稳定不增。**P0-3 SQLite 证据累计 14 测试 PASS**（核心持久性 6 + 损坏/索引 4 + 真崩溃 1 + 压测 3）。
  ENOSPC（磁盘满错误处理）需 Docker 小卷/物理小分区，7 天连续稳定性需真实硬件长跑——两项标 OPEN（特殊环境）。
- **P0-2 TDengine Java 确定性幂等 Spike（2026-08-12）**：`TDengineIdempotencySpikeTest` 1/1 PASS
  （taos-jdbcdriver 3.1.0 REST，`jdbc:TAOS-RS://localhost:6041`）：同 message_id+ts 重投 → **物理行=1**（upsert 覆盖，
  val=2.0）。补 CLI 证据的 Java 驱动层（§27-⑤ "驱动版本 + 重复写行为证据"）。TDengine 写入即持久（无 WAL replay），
  客户端崩溃后数据已落库，重投 = upsert → 1 行（§27-⑤ 确定性幂等闭合）。**P0-2 TDengine 证据完整**（CLI + Java）；
  批量写部分失败逐条结果留 P1 adapter 实现时 Spike。**P0-2/P0-3 代码层证据完整**，P0-4 冻结待运维评审签字（§21-6/§27-6/7）。
- **P0-4 冻结申请就绪（2026-08-12）**：形成 [TD-002/003 协同冻结申请](../../开发规范/TD-002-003协同冻结申请-20260812.md)。
  代码层证据汇总（§21-④ SQLite 14 测试 + §27-⑤ TDengine CLI+Java）+ 运维签字清单（§21-6/§27-6/7）+
  协同冻结约束（TD-002/003 互相依赖，同时转 Approved）。ENOSPC + 7 天稳定性作为上线前门禁（不阻塞代码层冻结）。
  冻结后解锁 P1（collector Profile / SQLite Outbox / Envelope·Inbox·ACK / TelemetryStore）。待 owner 组织运维评审会议签字。
- **P0-4 协同冻结完成（2026-08-12，TD-002 1.0.2 + TD-003 1.0.1 转 Approved/Frozen）**：owner 以
  `USER-APPROVAL-20260812-TD002-003-JOINT-FREEZE` 签字 7 项运维评审清单（[签字记录](../../开发规范/TD-002-003运维评审签字记录-20260812.md)）。
  TD-002（SQLite Outbox）+ TD-003（遥测 Inbox/投影）协同转 **Approved/Frozen**。ENOSPC + 7 天稳定性作为上线前门禁
  （不阻塞代码层冻结）。**P0 完整闭环**（P0-1 决策 + P0-2 TDengine + P0-3 SQLite + P0-4 冻结）。冻结解锁 **P1 采集主链路编码**
  （collector Profile / SQLite Outbox / Envelope·Inbox·ACK / TelemetryStore）。
- **ADR-016 与 P1 采集主链第一批落地（2026-08-12）**：按主线代码事实新增 ADR-016，明确 EDGE 退役、TASK 迁移为
  RUNTIME；同步修订平台功能计划 1.5.0、项目开发宪法 1.6.0、ADR 索引和 ADR-001。采集实现新增
  `application-collector.yaml`、`CollectorTelemetryWriter`，三种工业 Poller 在 collector Profile 下将一次轮询结果作为
  原子批次写 `TelemetryOutboxPort`，不再走原 `IotDeviceMessageService` 直发路径；普通中心 Profile 保持兼容。
  Envelope 映射拒绝占位 siteCode、缺失/非法 configVersion、未知点位、缺失 dataPriority 和非十进制值，忽略 `_raw`
  诊断键，固化毫秒 RFC 3339、点位优先级和单调 sequence。Java 17 Reactor 编译 PASS；映射/Writer + SQLite
  append/durability/concurrency 与 collector 装配合同定向测试 17/17 PASS。仓库默认 Maven 曾异常以 source=8 编译，显式使用 POM 声明的
  `maven.compiler.source/target=17` 后通过；该默认参数漂移继续作为构建治理 OPEN。
- **告警/视频/运维续作盘点（2026-08-12）**：最新媒体底座已形成 `RUNTIME/VIDEO → iot-sink → NFS/MinIO`，但检测事实
  仍只写 VIDEO `alert`，录像按 device/time 窗口回填 `record_path`；ADR-010 要求的 `alarm_record`、
  `AlarmRaised/AlarmRecovered`、`sourceType + sourceId → alarmId` 映射和 `device.alarm.created/recovered.v1` 尚无代码/DDL。
  `iot-maintenance` 模块亦尚未创建。下一批顺序冻结为：统一告警 Schema/状态机/迁移 TD → 来源事件与媒体证据映射 →
  告警详情视频回看合同 → 以 alarmId 创建设备缺陷/简化工单首个运维闭环；不得直接把 VIDEO alertId 当统一 alarmId。
- **ADR-016 评审处置完成（2026-08-12，ADR-016 1.1.0）**：采纳评审 M-01/M-03/M-04 与 L-01～L-05，部分采纳
  M-02。补充 ADR-010 `alarm_record` 来源周期映射、独立告警 Inbox 双层幂等、Envelope V1 与 `configVersion` 边界、
  HTTP→VIDEO 心跳和 MQTT→iot-sink 告警双通道、NODE 受控 RUNTIME 工作负载目标以及 NFS/MinIO 分级降级。
  ADR-014 当前只覆盖 TD-005 物模型事件，因此只复用其稳定 ID/摘要/Inbox/重试/DLQ 原则，不宣称现有
  `IotAlgoBusMqttHandler` 已满足应用 ACK 或持久 Inbox。继续 OPEN：告警 Schema/DDL/消费者 Inbox、稳定 `msgId` 重试、
  RUNTIME 原生进程/容器生命周期 TD、媒体归档持久重试及最终一致性参数、历史 EDGE 现行实施表述审计。评审报告作为审计输入
  保留在 `开发规范/ADR-016评审报告.md`，不把 Accepted 状态误解为上述实现门禁已关闭。
- **M1 采集主线 P1-T4～T7 全链路代码实现（2026-08-13，TD-002 §20 T-4/T-5 + TD-003 §6/§9/§10/§11/§13/§15）**：
  P1-T4（claim/ACK/调度）：端口层（AckCommand/AckResultCode/ClaimedEnvelope/ClaimBatchResult + TelemetryOutboxPort
  扩展 claimBatch/applyAck）→ Schema V2（补 attempts/in_flight/ack_deadline/gap 等 9 列 + telemetry_gap 表 + 索引重建）→
  OutboxCommand 扩展（Claim/ApplyAck/ReclaimExpiredLeases/CleanupAcked/Checkpoint sealed）→ SqliteOutboxWriter 全状态机
  （executeClaim SELECT→UPDATE IN_FLIGHT / executeApplyAck ACCEPTED→ACKED+RETRYABLE→PENDING+退避+FINAL→gap+DEAD_LETTER /
  executeReclaimExpiredLeases 过期回收 / executeCleanupAcked DELETE / executeCheckpoint wal_checkpoint PASSIVE）→
  双队列优先级（control 优先 ACK/Claim 不饿死 data AppendBatch）→ OutboxDispatcher（100ms claim→publish）→
  VertxCollectorMqttPublisher（QoS1 canonical bytes）→ CollectorMqttAckSubscriber（订阅 ACK Topic→parse→applyAck）→
  LeaseReclaimer（30s）→ OutboxCleanupTask（10s ACKED 清理）→ OutboxCheckpointTask（30s WAL checkpoint）→
  FullJitterBackoff（base 1s cap 30min）→ 52/52 测试 PASS（Claim 5 + 状态机 5 + 退避 42）。
  P1-T5（清理/checkpoint）：ACKED 批量 DELETE + WAL PASSIVE + AutoConfig 装配。
  P1-T6（中心 Inbox + Store）：端口层（InboxEnvelope/TelemetryInboxPort/TelemetryStorePort/WriteResult）→
  JdbcTelemetryInbox（PG ON CONFLICT 幂等 + hash 碰撞检测）→ JdbcTelemetryStore（PG telemetry_sample NUMERIC 幂等）→
  CenterMqttInboxSubscriber（订阅上行 Topic→parse→receiveEnvelopes）→ CenterMqttAckPublisher（ACK V1→collector）→
  TelemetryProjectionOrchestrator（500ms claim RECEIVED FOR UPDATE SKIP LOCKED→writeSample→COMPLETED/DEAD_LETTER+retry）→
  V008 DDL（iot_sink.telemetry_inbox + telemetry_sample 含中文 COMMENT）→ AutoConfig 全装配。
  P1-T7（TelemetryStore full TDengine）：TDengineTelemetryStore（超级表 upsert messageId tag+ts PK 确定性幂等，
  taos-jdbcdriver 3.1.0 REST，content_sha256 第二层校验，lazy init）。
  **M1 采集主线从设备/RS485 → Poller → SQLite Outbox → MQTT QoS1 → 中心 Inbox → TelemetryStore（standard PG / full TDengine）
  全链路代码实现完成**（~50 新建文件 + ~15 修改 + 52+ tests PASS，全 mvn -D17 BUILD SUCCESS）。
  16 commit 待 push（网络间歇性 reset）；端到端验证需 PG+EMQX+TDengine 运行环境 + V008 runner 落库窗口。
- **P1-T6 PG 合同测试闭环（2026-08-13，TD-003 §10/§13）**：`JdbcTelemetryInboxContractTest` 在真实
  `iot-device20`（postgres-server 容器）上 5/5 PASS——3 Store（STORED/DUPLICATE/同 messageId 不同 hash 仍 STORED）+
  2 Inbox（新 envelope 入库 / 同 messageId 同 hash 第二次返回 0 行，验证两层幂等 UNIQUE + ON CONFLICT DO NOTHING）。
  修复点：① V008 DDL `updated_at_ms BIGINT NOT NULL` 无默认值，`JdbcTelemetryInbox.INSERT_SQL` 原未写该列 →
  补 `updated_at_ms` 列 + VALUES 占位 + jdbc.update 第 15 参 `now`；② 仓库默认 `maven.test.skip=true` 且
  reactor compiler 默认回落 source=1.8（`DEVICE/pom.xml` compiler 用 `${maven.compiler.source}`），显式
  `-Dmaven.compiler.source/target=17 -Dmaven.test.skip=false` 后通过；③ iot-sink-api 旧 jar 导致
  `InboxEnvelope ClassNotFoundException`，`install -am` 重建后解决。PG 凭证经 `TD008_PG_PASSWORD` 注入。
  构建治理 OPEN：source=1.8 默认漂移仍未根因修复（iot-parent 无 compiler `<build>`，依赖 `-D` 显式覆盖）。
- **构建治理根因修复：reactor compiler 锁定 `--release 17`（2026-08-13）**：`DEVICE/pom.xml` pluginManagement
  的 maven-compiler-plugin 原用 `<source>/<target>` 依赖 `${maven.compiler.source}`，但该 property 被
  maven-compiler-plugin 默认值机制覆盖（effective-pom 实测解析为 1.8；同理 `java.version` 被 JVM sysprop
  覆盖为 19.0.2），全 reactor 静默以 Java 8 编译——record/sealed/switch/pattern matching 报错，需显式
  `-Dmaven.compiler.source/target=17` 才能绕过，properties 声明的 `=17` 形同虚设。修复：改用字面量
  `<release>17</release>`（不依赖任何被覆盖的 property），pluginManagement config 继承对所有子模块生效
  （含独立声明 source/target 的 iot-gb28181-biz——release 优先于 source/target）。effective-pom 验证
  iot-sink-biz compiler 全为 `<release>17</release>`、无 `<source>1.8</source>`；无 `-D` source flag 下
  PG 合同测试 5/5 PASS。**此后跑 iot-sink 测试只需 `-Dmaven.test.skip=false`，不再需要编译 flag。**
  构建治理 OPEN 关闭。
- **P1-T7 TDengine Store 运行验证 + Store 层合同测试全绿（2026-08-13，TD-003 §13/§15）**：
  `TDengineTelemetryStoreContractTest` 在真实 tdengine-server（taosAdapter REST 6041，
  root/taosdata）3/3 PASS——writeSample STORED / 同 messageId 同 ts upsert 物理表保持 1 行
  （确定性幂等 §27-⑤）/ 不同 messageId 各 1 行。修复点：REST URL 缺 db path
  （`jdbc:TAOS-RS://host:port/?...`），taosAdapter `/rest/sql`（无 db）只允许 CREATE DATABASE/SHOW，
  INSERT/CREATE STABLE/SELECT 需 `/rest/sql/<db>`；原 `init()` 建库成功但建表/写入报
  `db is not specified`。改 `urlBootstrap`（无 db，建库）+ `url`（含 `/iot_telemetry`，建表/INSERT/SELECT）
  两阶段；表名占位 `INSERT INTO ?` 实测工作（非根因）。**至此 Store 层（standard PG + full TDengine）
  + Inbox 两层幂等合同测试 5+3 全绿**（真实 PG `iot-device20` + TDengine `iot_telemetry`）。
  待验证：端到端 MQTT（collector↔center 真实 broker 往返，需起 iot-sink-biz + nacos 配置）+ 7天稳定性（部署后运行时）。
- **端到端 MQTT 验证（center 侧）+ subscriber host 修复（2026-08-13，TD-003 §7/§10/§13）**：
  本地起 iot-sink-biz center（`--spring.profiles.active=local --easyaiot.telemetry.inbox.enabled=true
  --easyaiot.telemetry.mqtt.enabled=true`，Started in 37s），CenterMqttInboxSubscriber 匿名连真实 EMQX 1883
  订阅 `/telemetry/#`。用 paho-mqtt 模拟 collector 发 Envelope V1 上行 topic（`/telemetry/site-e2e/voltage-a`）→
  验证全链路打通：subscriber 收 → parseEnvelope → JdbcTelemetryInbox 两层幂等写入（RECEIVED）→
  TelemetryProjectionOrchestrator 500ms claim（FOR UPDATE SKIP LOCKED）→ JdbcTelemetryStore →
  PG `telemetry_inbox`(COMPLETED) + `telemetry_sample`(value_numeric=225.5)。重复发同 messageId，inbox/sample
  仍各 1 行（端到端两层幂等）。修复 CenterMqttInboxSubscriber 构造器 host/port 参数被忽略、start() 用硬编码
  localhost 的 bug（改字段注入，支持非 localhost 部署）。**center 侧真实 broker 往返 + Spring 装配 + Inbox + Store
  端到端验证通过**。坑：Git Bash MSYS2 把命令行 `--topic=/telemetry/#` 路径转换为 `C:/Program Files/Git/telemetry/#`，
  需用默认值或不经 shell 注入。EMQX 匿名允许（subscriber 无凭证连）。待补：collector 侧 outbox→publish 往返
  （需设备数据或手动注入 outbox）+ ACK 回环 + 7天稳定性（部署后运行时）。
