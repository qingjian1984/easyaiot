# M1-LC-02A：Collector 版本配置应用链任务单

> 状态：LC02A-0 Approved / Frozen；LC02A-1～4 Blocked
> 版本：0.3.0
> 日期：2026-08-13
> 架构负责人：GPT-5.6 Sol
> 实现执行者：本任务 Frozen 后，按 02A-0→02A-4 顺序交由 GPT-5.6 Luna（max reasoning）
> 当前实现授权：仅 LC02A-0；LC02A-1～4 需逐包复核后授权

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
宿主机：/var/lib/easyaiot/collector/{safeWorkloadId}/config
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
| LC02A-OPEN-03 | TD-001 配置 API、状态机、安全参数从 In Review 转为本任务 Frozen 基线 | OPEN |
| LC02A-DECISION-04 | Linux 路径 `/var/lib/easyaiot/collector/{workload}/config`、固定组/GID、2770/0660、精确 bind mount | RESOLVED-DESIGN（安装实现待 Frozen） |
| LC02A-OPEN-05 | Windows collector 保持 capability 关闭，直至 COM/服务账号/ACL 现场资格另行验证 | OPEN-RUNTIME（不阻塞 Linux 本地实现） |
| LC02A-OPEN-06 | 已暴露 Agent token 完成外部轮换、旧值失效、Agent 验证与 secret scan | CLOSED-FOR-LC02A-0（DB/local/bootstrap/register/heartbeat 与 cached/worktree scan 已通过；部署后/现场验证仍 OPEN） |

LC02A-0 已满足 Luna Max 无歧义实现条件；LC02A-1～4 仍等待 OPEN-03 与逐包复核。DECISION-04 已完成设计收敛，OPEN-05 是部署后运行期资格，不阻塞 Linux 本地实现。

> 2026-08-14 历史暂停记录（已由 2026-08-15 执行记录取代）：当时环境未提供 GPT-5.6 Luna，LC02A-0 尚未开始；控制面未启动，外部轮换暂缓。现已完成 LC02A-0 本地实现、定向测试、受控轮换与索引清理。恢复入口见 [M1 SDD 进度与续作入口](./M1-SDD进度与续作入口.md)。
