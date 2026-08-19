# M1-LC-02A：Collector 版本配置应用链任务单

> 状态：LC02A-0 Approved / Frozen；OPEN03 本地收敛任务单 Approved / Frozen；LC02A-1～4 整包仍 Blocked
> 版本：0.4.9
> 日期：2026-08-13
> 架构负责人：GPT-5.6 Sol
> 实现执行者：本任务 Frozen 后，按 02A-0→02A-4 顺序交由 GPT-5.6 Luna（max reasoning）
> 当前实现授权：OPEN03-08A（含 S1）已由 Sol 验收；OPEN03-08 v2 已恢复授权 Luna Max；OPEN03-01/02/03/04/05/06/07 已由 Sol 验收

## 1. 为什么必须先做本任务

代码核对确认：

- `iot-device` 已能生成并持久化不可变 collector 发布单；
- TD-001 已设计 `/workload/collector/config`、本地版本快照和 `PollingConfigProvider`；
- 当前 NODE Agent 只有通用 `/workload/deploy`、`/workload/stop`，没有 collector 配置应用 API；
- `iot-node` 没有 PUBLISHED collector 发布单派发器；
- collector Profile 仍由 `DeviceMapper.selectPollingDevices` 读取当前 `DeviceDO.extension`，没有本地版本快照 Provider；
- 现有 ConfigSnapshot 1.0 不含 `productIdentification`。

因此 M1-LC-02 不能安全地从“已应用快照”取得产品路由身份。本任务先补齐版本配置应用闭环，但不修改遥测 outbox、MQTT Topic、Inbox 或 ACK。

## 2. 强制基线

- [EasyAIoT 项目开发宪法 1.6.0](../../开发规范/EasyAIoT项目开发宪法.md)；
- [平台功能计划 1.5.0](../../架构设计/平台功能计划.md)；
- [ADR-001 RTU Poller 运行位置](../../架构决策/电力运维云平台/ADR-001-RTU-Poller运行位置.md)（Accepted）；
- [ADR-007 collector 打包与 NODE 管理契约](../../架构决策/电力运维云平台/ADR-007-collector打包与NODE管理契约.md)（Accepted）；
- [ADR-017 遥测可靠链路 Topic 与产品路由身份收口](../../架构决策/电力运维云平台/ADR-017-遥测可靠链路Topic与产品路由身份收口.md)（Accepted）；
- [ADR-018 控制面内部服务与 NODE 请求认证](../../架构决策/电力运维云平台/ADR-018-控制面内部服务与NODE请求认证.md)（Accepted）；
- [TD-001 collector 与 NODE 部署契约](./TD-001-collector与NODE部署契约.md)（In Review）。

ADR-017、ADR-018 已于 2026-08-14 Accepted。LC02A-0 的边界、测试和凭据治理授权已冻结；TD-001 其余门禁继续阻塞 LC02A-1～4。

## 3. 目标数据流

```text
iot-device PUBLISHED ConfigSnapshot 1.1
  → iot-node 内部鉴权拉取 canonical bytes/hash
  → iot-node HMAC 调用 NODE /workload/collector/config
  → NODE 原子写 desired 快照并返回接单 ACK
  → collector 本地 PollingConfigProvider 校验并原子切换 active
  → collector 原子写 observed 状态
  → NODE GET workload 状态
  → iot-node 回报 iot-device
  → PUBLISHED → APPLIED | FAILED | APPLY_TIMEOUT
```

完整快照不进入 Kafka 事件，不写普通环境变量，不进入命令行和日志。控制面通知或轮询只传 release 标识；canonical bytes 通过内部鉴权接口按需拉取。

## 4. 冻结候选合同

### 4.1 ConfigSnapshot 1.1

1.0 文件保持不可变；新增 `schema/collector/v1.1/collector-config-snapshot-v1.1.json`。相对 1.0 只增加：

```json
{
  "schemaVersion": "1.1",
  "productIdentification": "product-stable-code"
}
```

约束：

- 根级 `productIdentification` 必填，长度 1～128，字符集复用产品标识现行合同；
- `PowerModelBindingApplyService` 从已校验 API 路径参数服务端注入，客户端提交的 `collectorSnapshot` 源字段集合不含该字段；
- 生成、持久化、哈希、下发复用同一份 JCS canonical UTF-8 bytes；
- 新发布只生成 1.1；历史 1.0 发布单保持只读，禁止原地升级或重算哈希；
- collector 可识别 1.0 以返回稳定 `ROUTE_IDENTITY_MISSING`，但 1.0 不能成为 LC-02 canonical Topic 的路由来源。

### 4.2 iot-device 内部发布单接口

由 `iot-device-api` 定义共享 DTO 和 Feign 合同；只允许通过 ADR-018 HMAC 服务身份校验的 `iot-node` 调用。用户 Token、`login-user`、租户 Header、Nacos 服务名和内网地址均不构成服务身份：

```text
GET  /internal-api/device/collector-config/releases/pending?limit={1..100}
GET  /internal-api/device/collector-config/releases/{releaseId}
POST /internal-api/device/collector-config/releases/{releaseId}/observed
```

`pending` 只返回 PUBLISHED 且目标 node/workload 投影仍匹配的轻量元数据；允许多个 `iot-node` 实例重复读取，幂等由 release/version/hash 与 Agent 状态机保证。

详情响应至少包含：

```text
releaseId, tenantId, nodeId, workloadId, configVersion,
schemaVersion, canonicalizationVersion,
payloadCanonical, payloadSha256, canonicalLengthBytes, publishedAt
```

`observed` 请求：

```text
releaseId, tenantId, nodeId, workloadId,
configVersion, payloadSha256,
status = AGENT_ACCEPTED | APPLIED | FAILED,
observedAt, errorCode, errorDetailSanitized
```

CAS 规则：

- 只有 release、tenant、node、workload、version、hash 全部匹配才更新；
- `APPLIED`：`PUBLISHED → APPLIED`，写 `applied_version/applied_at`；
- `FAILED`：`PUBLISHED → FAILED`，保留上一 active 配置；
- `AGENT_ACCEPTED` 不改变业务终态，只记录可观测接单事实；若现有表无法保存该事实，以结构化指标/审计实现，不把它伪装为 APPLIED；
- 迟到、低版本或 hash 不同只记脱敏审计，不覆盖当前发布单；
- `APPLY_TIMEOUT` 由 iot-device 按 `publishedAt/agentAcceptedAt` 契约判定，不由 Agent 自报。

### 4.3 iot-node 派发器

新增专用 `CollectorConfigDispatchService`，不得复用接受任意 `command/env/files` 的通用工作负载部署入口。

循环行为：

1. 从 iot-device 拉取最多 100 个 PUBLISHED 元数据；
2. 按 releaseId 拉取 canonical bytes/hash；
3. 验证长度和 SHA-256；
4. 查权威 `ComputeNodeDO`，拒绝离线或 nodeId 不匹配；
5. 使用节点专属 secret 对 Agent 请求签名；
6. PUT 配置；同 version+hash 重放视为成功；
7. 查询 Agent observed 状态并回报 iot-device。

不得把快照写入 iot-node 数据库、普通日志或错误详情。内存中的 canonical bytes 用完即释放引用。

### 4.4 NODE Agent 配置 API 与本地状态

新增：

```text
PUT /workload/collector/config
GET /workload/collector/{workloadId}
```

PUT body：

```json
{
  "workloadId": "collector-01",
  "configVersion": 12,
  "schemaVersion": "1.1",
  "canonicalizationVersion": "jcs-rfc8785-v1",
  "payloadCanonical": "{...}",
  "payloadSha256": "64-lower-hex",
  "canonicalLengthBytes": 1234
}
```

签名头和防重放语义必须复用 ADR-018 的节点认证合同：

```text
X-EasyAIoT-Timestamp
X-EasyAIoT-Nonce
X-EasyAIoT-Body-SHA256
X-EasyAIoT-Signature = HMAC-SHA256(agentSigningKey, canonicalRequest)
```

`canonicalRequest` 精确为：

```text
HTTP_METHOD + "\n" + REQUEST_PATH + "\n" + TIMESTAMP + "\n" + NONCE + "\n" + BODY_SHA256
```

约束：

- 时间偏差超过 5 分钟拒绝；nonce 在窗口内持久防重放；
- 先校验 body hash、HMAC、长度、payload hash、Schema/JCS 合同，再接触磁盘状态；
- workloadId 只作为逻辑键，经固定安全编码映射目录，禁止路径穿越；
- 低版本返回 `CONFIG_VERSION_STALE`；同版本同 hash 幂等成功；同版本异 hash 返回 `CONFIG_VERSION_CONFLICT`；
- 任一失败不得覆盖现有 desired/active。

状态根目录由安装策略指定，不接受请求体路径：

```text
{collectorStateRoot}/{safeWorkloadId}/
  desired.json
  active.json
  observed.json
  history/{configVersion}-{sha256-prefix}.json
  replay-window.db
```

所有文件使用同目录临时文件 → flush/fsync → atomic replace；历史至少保留最近两个已应用版本。日志只记录 workload/version/hash 短摘要和稳定错误码。

Linux M1 路径与权限候选冻结为：

```text
宿主机：${COLLECTOR_STATE_ROOT}/{workloadIdentity}/config
安装示例：/var/lib/easyaiot/collector-state/{workloadIdentity}/config
容器内：/var/lib/easyaiot/config
```

- 安装器创建专用 `easyaiot-collector` 组；NODE Agent 服务账号与 collector 容器固定 GID 同属该组；
- workload 目录为 `2770`（setgid），配置/状态文件为 `0660`，进程 `umask 0007`；父目录不授予其他用户遍历权限；
- NODE 以 bind mount 精确映射单个 workload 配置目录，禁止映射 `/var/lib/easyaiot` 或 Agent 安装根；
- outbox 继续使用独立 `/var/lib/easyaiot/outbox` 卷和 TD-002 的 `0700/0600` 权限，不与共享配置目录合并；
- 启动时 Agent 与 collector 都校验 owner/group/mode、非符号链接和本地文件系统原子 rename 能力；不满足返回 `COLLECTOR_CONFIG_PERMISSION_INVALID`；
- Windows collector capability 保持关闭，不为本地开发臆定 `%ProgramData%` 路径；待服务账号、COM、ACL 和现场安装资格验证后另行冻结。

### 4.5 collector 本地 Provider 与状态回报

新增 TD-001 端口：

```java
interface PollingConfigProvider {
    Optional<CollectorConfigSnapshot> current();
    Optional<CollectorConfigSnapshot> candidate(long version);
}

interface PollingStatusReporter {
    void reportConfigApplied(ConfigApplyResult result);
    void reportHealth(CollectorHealth health);
}
```

collector Profile 只能装配本地文件 Provider；不得继续以 `DeviceMapper.selectPollingDevices` 作为运行配置源。普通 center Profile 的数据库适配器不在本任务删除。

应用顺序：

```text
desired 完整性/Schema/hash
→ capability 与规模
→ 串口路径/指纹和点表冲突
→ 构建候选调度图
→ 原子替换 active 内存引用
→ 原子写 active.json/observed.json(APPLIED)
```

任一步失败写 `observed.json(FAILED + stable errorCode)`，继续使用上一 active；首次启动无 active 时为 `WAITING_CONFIG/DEGRADED`，不得回退查中心数据库。

## 5. 明确不做

- 不修改 `TelemetryEnvelope`、SQLite Outbox、MQTT Topic、中心 Inbox、ACK、TelemetryStore；
- 不通过 Kafka 传完整 ConfigSnapshot；
- 不让 NODE Agent 接受任意命令、任意路径、任意 env 或明文 broker secret；
- 不在 collector Profile 保留“本地 Provider 失败就查数据库”的回退；
- 不修改历史 ConfigSnapshot 1.0 bytes/hash；
- 不在本地开发阶段宣称现场串口、跨主机网络、NTP 偏差或 7 天稳定性已验证。

## 6. 五个顺序实现包

| 包 | 实现边界 | 主要模块 | 独立验收 |
|---|---|---|---|
| LC02A-0 | ADR-018 内部服务 HMAC verifier/signer、Redis nonce、防重放；NODE HMAC 密钥 Provider 与凭据基线修复 | `iot-common-security`、`iot-node`、`NODE`、安装配置 | 伪造 Header、错 key、过期、重放、Redis 故障、轮换、secret scan 全绿 |
| LC02A-1 | ConfigSnapshot 1.1 + 内部 release 详情/observed CAS API | `iot-device-api/biz` | 1.0 golden 不变；1.1 golden/恶意字段/租户越权/JDBC CAS |
| LC02A-2 | Agent HMAC、防重放、版本状态机、原子文件与恢复 | `NODE/` | Python 单测 + crash 注入 + 路径/HMAC/重放负例 |
| LC02A-3 | collector 本地 Provider、原子切换、observed 与 Profile 架构门禁 | `iot-sink-api/biz` | Java 合同 + 旧 active 保留 + 重启恢复 + collector 禁 DB 配置源 |
| LC02A-4 | iot-node 拉取/派发/对账，组合本地 E2E | `iot-node-api/biz` | WireMock/假 Agent + 真实本地 NODE/collector E2E；重复/乱序/超时 |

五包不得并行实现共享合同。每包开始前由 Sol 复核上一个包的接口证据；实现者若发现需越过本包模块边界，立即停止并回到 Sol。

## 7. 文件边界

### LC02A-0

- `DEVICE/iot-common/iot-common-security/` 下新增的专用内部服务签名合同、Feign interceptor、provider verifier 与测试
- `DEVICE/iot-node/iot-node-biz/` 下节点签名 key Provider、调用签名与安全配置测试
- `NODE/` 下 HMAC、防重放、凭据 Provider 与测试
- `NODE/agent.env.example`、`.gitignore`、NODE Linux 安装/同步脚本中的 secret 引用与权限
- 现有被跟踪的本地凭据文件必须从 Git 基线移除并轮换；不得把其值复制到任何新文件或测试 fixture

### LC02A-1

- `DEVICE/iot-device/iot-device-api/src/main/resources/schema/collector/`
- `DEVICE/iot-device/iot-device-api/src/main/java/com/basiclab/iot/device/` 下新增的 collector config 内部 DTO/Feign 合同
- `DEVICE/iot-device/iot-device-biz/src/main/java/com/basiclab/iot/device/service/event/CollectorConfigSnapshotContract.java`
- `DEVICE/iot-device/iot-device-biz/src/main/java/com/basiclab/iot/device/service/power/PowerModelBindingApplyService.java`
- 新增的内部 controller/service 及对应测试

### LC02A-2

- `NODE/agent_server.py`
- `NODE/` 下新增的 collector config state/HMAC 模块及测试
- NODE 配置示例中 collector state root 和节点 secret 引用；不得提交真实 secret

### LC02A-3

- `DEVICE/iot-sink/iot-sink-api/src/main/java/com/basiclab/iot/sink/` 下新配置端口/DTO
- `DEVICE/iot-sink/iot-sink-biz/src/main/java/com/basiclab/iot/sink/protocol/polling/`
- collector Profile 自动装配、配置和对应测试

### LC02A-4

- `DEVICE/iot-node/iot-node-api/src/main/java/com/basiclab/iot/node/` 下新内部客户端 DTO
- `DEVICE/iot-node/iot-node-biz/src/main/java/com/basiclab/iot/node/` 下专用派发/对账服务、配置和测试
- 不得扩大修改通用 `NodeCommandServiceImpl` 以传任意 files/env 代替专用协议

## 8. 关键测试矩阵

| 类别 | 必测场景 |
|---|---|
| Snapshot | 1.0 golden bytes/hash 不变；1.1 服务端注入产品；请求体伪造/额外字段拒绝；JCS 重复稳定 |
| 内部 API | 服务身份、跨租户/错 node 拒绝；release/version/hash CAS；迟到回报不覆盖 |
| Agent 安全 | HMAC 错误、body hash 错误、过期时间、nonce 重放、路径穿越、超大 payload、日志脱敏 |
| Agent 状态 | 低版本、同版同 hash、同版异 hash；临时文件/rename/fsync 故障；重启恢复 desired/active/observed |
| collector | 首启无配置；合法切换；Schema/hash/串口/点表失败保留旧 active；observed 原子写；Profile 禁止 DB 回退 |
| iot-node | device/Agent 不可达；重复派发；乱序 observed；Agent 接单后应用超时；错误分类与退避 |
| 本地 E2E | PUBLISHED 1.1 → desired → APPLIED → iot-device APPLIED；失败候选保持上一 active |

现场串口指纹、跨主机断网、真实 NTP 偏差和 7 天稳定性保留为部署后 OPEN，不阻止本地实现证据，但不得标为已验证。

## 9. 验收命令骨架

```powershell
mvn -f DEVICE/pom.xml test -pl iot-device/iot-device-biz `
  -Dtest=CollectorConfigSnapshotContractTest,*CollectorConfigRelease*Test `
  -DfailIfNoTests=false -Dmaven.test.skip=false

python -m pytest NODE/tests -q

mvn -f DEVICE/pom.xml test -pl iot-sink/iot-sink-biz `
  -Dtest=*PollingConfigProvider*Test,*CollectorConfigApply*Test,*CollectorProfile*Test `
  -DfailIfNoTests=false -Dmaven.test.skip=false

mvn -f DEVICE/pom.xml test -pl iot-node/iot-node-biz `
  -Dtest=*CollectorConfigDispatch*Test,*CollectorConfigReconcile*Test `
  -DfailIfNoTests=false -Dmaven.test.skip=false

mvn -f DEVICE/pom.xml -pl iot-device/iot-device-biz,iot-node/iot-node-biz,iot-sink/iot-sink-biz -am compile -DskipTests
```

最终类名可在各包冻结时确定，但测试语义不可减少；若通配符未匹配测试，验收必须失败而不是静默通过。

## 10. 完成定义

### LC02A-0 本地执行记录（2026-08-15）

- 依据双基线：`EasyAIoT项目开发宪法.md` 1.6.0（SHA-256 `76BF30903A5A62C957A75B57E24080AA7132DE6992D56C262EEAFBE7BE553C4D`）与 `平台功能计划.md` 1.5.0（SHA-256 `F0E5734C8FADA6D0E4DAB98F7D12F58940077F9C60AA8DA42AC8EF45C6F69869`）。
- 已落地内部服务 HMAC-SHA256 canonicalizer/signer/verifier、显式 route allowlist、Redis `SET NX EX` 防重放与 fail-closed 错误码；已落地 `iot-node` 节点 key Provider/signer；已落地 NODE HMAC、持久化 nonce、防重放、current/previous key 轮换与安装权限基线。
- 定向证据：`InternalServiceAuthContractTest` 3/3、`NodeAgentSigningKeyProviderTest` 2/2、`NODE/tests` 3 passed；Flask 集成测试因当前环境未安装 Flask 标记为 skipped，未将其冒充为通过。
- 复跑证据（2026-08-15）：在工作区可写临时目录执行 `NODE/tests` 为 `3 passed, 1 skipped`；两个 Java 定向 Maven reactor 均 `BUILD SUCCESS`，分别为 3/3 与 2/2。Maven 使用受控网络补齐依赖，未修改业务代码。
- 凭据治理：`NODE/agent.env` 不在 Git index 且被 ignore；初始化 SQL 转储中的旧节点令牌已移除；当前旧令牌工作树匹配数为 0。外部轮换与旧值失效已完成，cached/worktree secret scan 均通过；部署后/现场验证仍保持 `OPEN`。
- 轮换续作：正式 `reset-agent-token?id=1` 对平台节点返回业务拒绝，因此按授权采用受控 DB fallback；数据库、本地受保护文件与 bootstrap 返回值一致，一次性 Agent register/heartbeat 均 `code=0`。`.git/index.lock` 已清理，清理后的 SQL 已进入待提交索引，索引 secret scan 通过。
- 本地完成状态：LC02A-0 代码与定向测试证据可供复核；LC02A-1～4 继续 Blocked，不能据此宣称现场、跨主机 NTP 或 7 天稳定性已验证。
- 人工执行约束（2026-08-16）：当前仅进行本地开发与合同测试；Linux PTY 端到端、资源压测、Windows 发布资格及其他部署/现场验证暂不执行，保持 `OPEN`，待决策所有者明确要求并提供相应环境后再执行。
- 暂存状态：`.git/index.lock` 已清理；SQL 转储清理已进入待提交索引，cached/worktree secret scan 已复核通过。

- [ ] ADR-017/018 Accepted，TD-001 相关接口/状态机评审关闭，本任务 Frozen；
- [ ] LC02A-0～4 依次完成且每包独立测试通过；
- [ ] ConfigSnapshot 1.1 产品身份来自服务端产品事实，1.0 历史 bytes/hash 不变；
- [ ] Agent 原子状态、HMAC、防重放、版本幂等和 crash 恢复证据齐全；
- [ ] collector Profile 只读本地版本快照，失败保留上一 active；
- [ ] 本地组合 E2E 完成 PUBLISHED→APPLIED 与失败保留旧 active 两条链；
- [ ] diff 证明未修改遥测 outbox/Topic/Inbox/ACK/Store；
- [ ] 现场与稳定性限制在交付说明中保持 OPEN；
- [ ] 完成后 M1-LC-02 才可从前置阻塞转入 Frozen 评审。

## 11. 当前 OPEN

| ID | 门禁 | 状态 |
|---|---|---|
| LC02A-OPEN-01 | ADR-017 决策所有者接受 | CLOSED（2026-08-14） |
| LC02A-OPEN-02 | ADR-018 接受并冻结服务 HMAC、节点 HMAC、密钥 Provider 与轮换合同 | CLOSED-FOR-LC02A-0（2026-08-14；Provider 生产选型仍阻塞激活） |
| LC02A-OPEN-03 | TD-001 配置 API、状态机、安全参数从 In Review 转为本任务 Frozen 基线 | OPEN / NOT_CONVERGED（Sol 复核，2026-08-16） |
| LC02A-DECISION-04 | Linux 路径 `/var/lib/easyaiot/collector/{workload}/config`、固定组/GID、2770/0660、精确 bind mount | RESOLVED-DESIGN（安装实现待 Frozen） |
| LC02A-OPEN-05 | Windows collector 保持 capability 关闭，直至 COM/服务账号/ACL 现场资格另行验证 | OPEN-RUNTIME（不阻塞 Linux 本地实现） |
| LC02A-OPEN-06 | 已暴露 Agent token 完成外部轮换、旧值失效、Agent 验证与 secret scan | CLOSED-FOR-LC02A-0（DB/local/bootstrap/register/heartbeat 与 cached/worktree scan 已通过；部署后/现场验证仍 OPEN） |

### LC02A-OPEN-03 本地接口与安全冻结审查记录（2026-08-16）

本次仅按授权完成 OPEN-03 的接口冻结审查、安全约束冻结审查和联合记录；依据双基线 1.6.0/1.5.0 及 TD-001 v1.0.19。审查结果可在当前 Windows 工作区复核，但不改变 TD-001 `In Review` 状态、不把 OPEN-03 标记为 CLOSED、不启动 LC02A-1～4，也不执行 Linux PTY、资源压测、Windows 发布资格、现场或部署后验证。

- **接口审查已执行**：现有 v1 `CollectorConfigSnapshotContractTest` 5/5；canonical UTF-8/hash/字节长度、生产非空总线、显式采集策略、十进制字段和缺失事实拒绝可复核。仓库当前尚无 1.1 schema、服务端注入 `productIdentification`、类型化 WorkloadSpec 机器合同、release 详情/observed CAS API、NODE collector config 路由或 LC02A-1～4 的状态机实现；因此版本、CAS、乱序/重复、失败保留旧 active、回滚新版本等条款仍仅是冻结候选合同，必须逐包实现后再验收。
- **安全审查已执行**：内部服务 HMAC canonicalizer/verifier/allowlist/nonce 及节点 key Provider 的定向 Java 测试分别 3/3、2/2；NODE 安全测试在工作区可写临时目录为 `3 passed, 1 skipped`（Flask 集成用例缺依赖，未冒充通过）；collector outbox 装配/写入/路由定向测试 5/5。HMAC、时间戳、body hash、持久 nonce、防重放、secret Provider 的本地可复核部分保持通过，且不输出凭据值。
- **安全缺口保持 OPEN**：当前 NODE 通用 `/workload/deploy`/`WorkloadManager` 仍接收任意 `command`、`workDir`、`logDir`、`gpuIds`、`env`/`files`；collector 专用 `UNSUPPORTED_GENERIC_DEPLOY`、镜像/路径 allowlist、固定模板及配置状态 API 尚未实现。`test_collector_routes_do_not_fallback_to_agent_token` 因当前环境未安装 Flask 被跳过；签名路由本身尚未实现，不能把 404 解释为业务链通过。
- **联合记录**：TD-002/003 文档对 `TelemetryOutboxPort`、`/var/lib/easyaiot/outbox` 卷路径和 `process/config/serial/center` 健康摘要已有交叉引用；本地 writer/config/mapper 测试未发现形状冲突，但本次未作完整跨 TD 运行 E2E 或冻结签字。TD-001 §19 第 4、5 项按授权保持 OPEN；第 6 项仅记录为设计一致性待正式联合冻结，不能据此关闭 OPEN-03。

本次复核未修改业务代码、配置、凭据或测试；工作区临时 pytest 目录已清理。LC02A-0 仍为 `Approved / Frozen`，LC02A-1～4 继续 `Blocked`。

### Sol 最终复核决定（2026-08-16）

Sol 接受 Luna Max 的本地测试与静态审查证据，决定 **不收敛 LC02A-OPEN-03**。TD-001 §19 门禁状态为：1、3 `OPEN`；2、6 `PARTIAL / OPEN`；4、5 按人工约束保持 `OPEN-RUNTIME`。因此 TD-001 继续 `In Review`，LC02A-1～4 不获得实现授权。

本地继续推进前，须先形成并冻结以下有界任务：collector 本地 `PollingConfigProvider` 与中心依赖切断；ConfigSnapshot 1.1、类型化 WorkloadSpec、release/observed CAS 与状态机；NODE collector 专用 fail-closed/固定模板/allowlist；TD-002/003 正式联合冻结与可执行组合合同。Linux PTY、资源/稳定性压测和 Windows 资格不纳入当前本地执行范围。

上述边界已由 Sol 拆为并冻结 [M1-TD001-OPEN03 本地收敛实现任务单](./M1-TD001-OPEN03-本地收敛实现任务单.md) 的 OPEN03-01～08。OPEN03-01/02/03/04/05/06/07 已于 2026-08-17 经 Sol 验收；OPEN03-08A 已按 expand→switch→compat 完成批量 Store 前置迁移，并完成 S1 凭据日志安全收尾，同日经 Sol 验收。OPEN03-08 v2 现已恢复 Luna Max 实现授权；LC02A-1～4 整包仍不得并行启动。

OPEN03-03 已完成 pending/detail/observed 内部合同、PUBLISHED+ACTIVE 投影读取边界及真实 PostgreSQL CAS；ADR-018 动态单段 route、raw body hash、服务身份与未知字段均 fail-closed。Sol 独立证据为 Collector 8/8（PG 2/2、Skipped=0）、ADR-018 4/4、33 模块 SUCCESS、八类 fixture=0 和 `git diff --check` PASS。门禁 2 仍因 NODE/collector 状态机、派发与组合 E2E 保持 `PARTIAL / OPEN`。

OPEN03-04 已完成 NODE collector 专用 HMAC deploy、通用入口精确早拒绝、Schema/镜像/路径/串口/资源 allowlist、固定 Linux Compose 与关闭默认的 Windows argv。Sol 首次否决 `brokerRef` 丢弃和 project 随 spec 漂移，修正后 ref 仅在内部不可变计划交给安全 resolver，任何缺失均在 subprocess 前拒绝；同 workload project 跨 spec 稳定。Sol 独立证据为冻结测试及 NODE 全量 26/26、Skipped=0，Schema 双副本 hash 一致、`compileall`/`git diff --check` PASS。门禁 3 的本地子缺口完成，但仍等待 OPEN03-08 联合冻结后标记 `CLOSED-LOCAL`；Docker/PTY/压测/Windows/现场均未执行。

OPEN03-05 已完成 NODE 配置接收与本地版本状态机。PUT 只写 desired，不伪造 active/APPLIED；配置目录统一为 `${COLLECTOR_STATE_ROOT}/{workloadIdentity}/config`，desired/active/history 保留原 canonical bytes，observed 为闭合摘要，POSIX 权限合同为 `02770/0660`。Sol 独立复验为 56/56 PASS、Skipped=0，Schema 长度 3853、SHA-256 `52FCC23AE0DF65BE19C902E604611A4078ABF9E89B13EF91E5DC05D088C7A28A`，`compileall`/`git diff --check`/临时目录清理通过。解锁 OPEN03-06 前，Sol 识别出 Compose `:ro` 与 Java 需要写 active/observed 的不可执行冲突，已把精确单 workload `rw`、正式文件分权、共同锁和原子提交顺序写入 v2 冻结合同；不得扩大宿主路径。Linux 锁/owner/GID、PTY、压测、Windows 与现场仍不执行。

OPEN03-06 已完成 collector 本地 Provider、原子配置应用、唯一 RTU 引擎、center bridge 与真实 collector 白名单启动图。Sol 先后否决“全包扫描下仅靠 `!collector` 注解隔离”和“测试强制非 Web、生产入口未固化”的差异，修正后以生产同款 `--spring.profiles.active=collector` 启动真实无 Web Spring 上下文，确认仅有本地 Provider/runtime/SQLite outbox，且无中心 DB/service/controller/message bus、Redis、Nacos/Feign；无配置写 `WAITING_CONFIG`。Sol 独立冻结集为 Java 27/27、NODE 37/37、Skipped=0，Schema 三副本 hash、`compileall`、`git diff --check` 与临时清理通过。当前只解锁 OPEN03-07；运行期资格仍保持 OPEN。

OPEN03-07 已完成 iot-node typed release 派发、节点权威查询、固定 HMAC PUT/GET、observed 对账、稳定错误分类和有界指数退避。Sol 否决反射适配、无 Spring 装配的普通 job 及读取完整响应后才做大小检查；修正后默认开关关闭，开启时形成 typed Feign/Mapper/signer/service/scheduled job Bean 图，响应在完整缓冲前限制为 `1 MiB + 1 byte`。正式 Maven 冻结矩阵 `45/45 PASS`、Skipped=0，独立 compile 与 `git diff --check` 通过。门禁 2 只剩 OPEN03-08 组合 E2E；其 v2 已冻结并授权 Luna Max，运行期资格仍保持 OPEN。

LC02A-0 已满足 Luna Max 无歧义实现条件；LC02A-1～4 仍等待 OPEN-03 与逐包复核。DECISION-04 已完成设计收敛，OPEN-05 是部署后运行期资格，不阻塞 Linux 本地实现。

> 2026-08-14 历史暂停记录（已由 2026-08-15 执行记录取代）：当时环境未提供 GPT-5.6 Luna，LC02A-0 尚未开始；控制面未启动，外部轮换暂缓。现已完成 LC02A-0 本地实现、定向测试、受控轮换与索引清理。恢复入口见 [M1 SDD 进度与续作入口](./M1-SDD进度与续作入口.md)。
