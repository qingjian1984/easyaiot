# M1-TD001-OPEN03：门禁 1～3、6 本地收敛实现任务单

> 状态：Approved / Frozen（任务边界）  
> 版本：1.4.2  
> 日期：2026-08-16  
> 架构与冻结：GPT-5.6 Sol  
> 实现与测试：GPT-5.6 Luna（max reasoning）  
> 当前授权：`OPEN03-08A` 及安全收尾 S1 已由 Sol 验收；`OPEN03-08` v2 恢复授权 GPT-5.6 Luna（max reasoning）实现与测试

## 1. 目标与结论

本任务单只收敛 TD-001 §19 的本地可执行门禁 1～3、6：

1. collector Profile 切断中心数据库/中心消息总线配置来源，同时复用既有 RTU 协议实现；
2. 补齐 ConfigSnapshot 1.1、类型化 WorkloadSpec、release/observed CAS 和版本状态机的机器合同与实现；
3. 对 collector 禁止通用 `/workload/deploy` 的任意命令、路径、环境变量和文件能力，改为专用 fail-closed 适配器；
4. 以自动合同证明 TD-001/002/003 的 `TelemetryOutboxPort`、卷路径和健康摘要无冲突。

本任务单冻结的是实现边界和验收口径，不改变 TD-001 `In Review` 状态，也不预先关闭 OPEN-03。每包必须由 Luna 独立实现与测试，再由 Sol 复核并决定是否授权下一包。

## 2. 强制基线与范围

- [EasyAIoT 项目开发宪法 1.6.0](../../开发规范/EasyAIoT项目开发宪法.md)，SHA-256 `76BF30903A5A62C957A75B57E24080AA7132DE6992D56C262EEAFBE7BE553C4D`；
- [平台功能计划 1.5.0](../../架构设计/平台功能计划.md)，SHA-256 `F0E5734C8FADA6D0E4DAB98F7D12F58940077F9C60AA8DA42AC8EF45C6F69869`；
- [TD-001 v1.0.19](./TD-001-collector与NODE部署契约.md)；
- [TD-002](./TD-002-SQLite-Outbox与恢复迁移.md)；
- [TD-003](./TD-003-遥测Inbox-ACK与时序投影.md)；
- [M1-LC-02A](./M1-LC-02A-Collector版本配置应用链任务单.md)；
- Accepted ADR-007、ADR-017、ADR-018。

部署范围只包含 `standard/full` 的同一套实现；`mini` 必须 fail-closed。当前只允许 Windows 本地开发、确定性单元/合同/JDBC 测试和本地假服务 E2E。

## 3. 明确排除

- 不执行 Linux PTY、真实串口、跨主机网络、NTP 偏差、资源/稳定性压测、7 天运行或 Windows 发布资格；
- 不冻结生产 CPU/内存、串口数、点位数、周期和 P95/P99 数值；
- 不修改 Envelope、MQTT Topic、Inbox、ACK、TelemetryStore 或 SQLite Outbox 状态语义；
- 不启用当前默认关闭的生产开关，不执行新 DDL，不处理真实凭据；
- 不允许 Luna 跨越当前授权包预做后续实现。

## 4. 冻结的共同架构规则

1. ConfigSnapshot 与 WorkloadSpec 的 JSON Schema 是机器事实源；所有 object 默认 `additionalProperties=false`。
2. canonical bytes、SHA-256 和字节长度只生成一次并全链路复用；历史 ConfigSnapshot 1.0 bytes/hash 不得变化。
3. 新 collector 发布只生成 ConfigSnapshot 1.1；`productIdentification` 由已校验 URL 路径参数在服务端注入，客户端 `collectorSnapshot` 不得提交该字段。
4. `workloadType` 固定为 `iot-sink-collector`。collector 的镜像、路径、卷、串口和启动方式只来自安装侧 allowlist 与固定模板，调用方不能传入命令片段。
5. Agent 先完成 HMAC/body hash/nonce/Schema/长度/hash 校验，再接触本地状态；失败不得覆盖 desired/active。
6. collector Profile 只使用本地版本快照 `PollingConfigProvider`，不得以异常回退中心数据库；普通 center Profile 可保留现有数据库适配器。
7. 配置目录与 outbox 目录相互独立：配置根由 Agent 管理，outbox 固定 `/var/lib/easyaiot/outbox`，不得嵌套、复用或互相清理。
8. 健康摘要只保留 `process/config/serial/center` 四 facet；TD-002 outbox 状态映射到 `center`，不得新增第五个 outbox facet。

## 5. 顺序任务包

| 顺序 | 包 ID | 对应门禁 | 单一交付边界 | 当前状态 |
|---:|---|---:|---|---|
| 1 | OPEN03-01 | 2 | ConfigSnapshot 1.1 + 服务端产品身份注入 | COMPLETE / SOL-ACCEPTED（2026-08-17） |
| 2 | OPEN03-02 | 2 | 类型化 WorkloadSpec 1.0 机器合同 | COMPLETE / SOL-ACCEPTED（2026-08-17） |
| 3 | OPEN03-03 | 2 | iot-device release 详情/observed CAS API | COMPLETE / SOL-ACCEPTED（2026-08-17） |
| 4 | OPEN03-04 | 3 | NODE collector 专用部署安全边界 | COMPLETE / SOL-ACCEPTED（2026-08-17） |
| 5 | OPEN03-05 | 2、3 | NODE 配置版本状态机与原子文件 | COMPLETE / SOL-ACCEPTED（2026-08-17） |
| 6 | OPEN03-06 | 1 | collector 本地 Provider 与 Profile 去中心依赖 | COMPLETE / SOL-ACCEPTED（2026-08-17） |
| 7 | OPEN03-07 | 2 | iot-node 派发、对账与超时分类 | COMPLETE / SOL-ACCEPTED（2026-08-17） |
| 8A | OPEN03-08A | 6 | TD-003 批量 TelemetryStore 兼容迁移 | SOL-ACCEPTED（含 S1，2026-08-17） |
| 8 | OPEN03-08 | 6 | TD-001/002/003 组合合同与联合冻结记录 | FROZEN / LUNA-MAX-AUTHORIZED（2026-08-17） |

禁止并行实现共享合同。任一包需要扩大文件边界、改变公共契约或修改排除项时必须停止，由 Sol 修订并重新冻结任务单。

## 6. OPEN03-01：ConfigSnapshot 1.1

### 6.1 文件边界

- `DEVICE/iot-device/iot-device-api/src/main/resources/schema/collector/v1.1/collector-config-snapshot-v1.1.json`；
- `CollectorConfigSnapshotContract.java`；
- `PowerModelBindingApplyService.java`；
- 上述合同与服务直接对应的 `iot-device-biz` 测试/fixture。

不得修改 Controller、release 查询/observed API、数据库迁移、NODE、iot-node、iot-sink 或运行开关。

### 6.2 必须实现

- 保持 v1 schema、v1 canonical golden bytes/hash 和历史读取语义不变；
- v1.1 根字段只在 v1 基础上增加必填 `productIdentification`，非空、1～128 字符，复用现有产品稳定标识校验；
- 新发布路径只生成 `schemaVersion=1.1`；服务端将已校验 `productIdentification` 注入 canonical payload；
- 客户端源 `collectorSnapshot` 出现 `productIdentification`、未知字段或伪造字段必须 fail-closed；
- DB 中 `schema_version`、payload、canonical、hash、长度必须来自同一 1.1 artifact，不得从 jsonb 重序列化；
- 继续使用稳定错误码 `COLLECTOR_CONFIG_SNAPSHOT_INVALID` / `COLLECTOR_CONFIG_SOURCE_FACT_MISSING`，不输出 payload 全文。

### 6.3 验收

- v1 既有 5 项合同测试全部通过且新增 golden fixture 证明 bytes/hash 不变；
- v1.1 合法样例重复 canonical 结果一致；产品身份已注入且参与 hash/长度；
- 空白、超长、客户端伪造/额外字段、错误版本均拒绝；
- `PowerModelBindingApplyPostgresIntegrationTest` 至少覆盖新候选落库的 1.1 schema、产品身份和 hash/长度一致性；原子回滚/fixture 清理既有断言不得削弱。

```powershell
mvn -f DEVICE/pom.xml test -pl iot-device/iot-device-biz -am `
  -Dtest=CollectorConfigSnapshotContractTest,PowerModelBindingApplyPostgresIntegrationTest `
  -DfailIfNoTests=false -Dmaven.test.skip=false

mvn -f DEVICE/pom.xml -pl iot-device/iot-device-biz -am compile -DskipTests
```

## 7. OPEN03-02：WorkloadSpec 1.0 机器合同

### 7.1 文件边界

- `iot-node-api` 新增 `schema/collector/workload/v1/collector-workload-spec-v1.json`、DTO、枚举与 fixture；
- `iot-node-biz` 只新增 provider-side contract validator/test，不调用 NODE、不落库。

### 7.2 必须实现与验收

- 完整固化 TD-001 §5 字段、类型、上限和 `additionalProperties=false`；内部 bigint ID 使用十进制字符串；资源十进制不得用二进制浮点；
- 禁止 `command/entrypoint/privileged/hostNetwork/capabilities/arbitrary env/files`；镜像只接受 allowlist repository + `sha256:<64-lower-hex>` digest；
- `config.targetPath` 固定为容器内 `/var/lib/easyaiot/config/active.json`，不得由请求选择宿主机配置根；宿主机状态根只允许后续 Agent 从本地安装配置解析；
- volume hostPath 必须精确绑定 `{collectorRoot}/{safeWorkloadId}/outbox`；串口 hostPath 必须在安装侧串口白名单，均拒绝根目录、Docker socket、`..` 和符号链接逃逸；
- Schema 的 CPU 64 核/内存 64 GiB 只作为反滥用传输硬上限，不是 standard/full 生产配额；validator 构造时必须显式注入安装侧 capability 配额，禁止使用无配置默认配额继续执行；
- 当前包只验证并生成不可变类型化 DTO/artifact，不生成 Compose、不启动 workload；
- 正常、未知字段、恶意路径、跨 workload 卷、漂移 tag、Schema 传输上限、安装侧配额、mini capability 拒绝均有自动合同测试。

### 7.3 验收命令

最终类名允许按模块惯例确定，但必须显式命中测试且不得以 `failIfNoTests=false` 掩盖零测试：

```powershell
mvn -f DEVICE/pom.xml test -pl iot-node/iot-node-biz -am `
  -Dtest=CollectorWorkloadSpecContractTest -Dsurefire.failIfNoSpecifiedTests=false `
  -Dmaven.test.skip=false

mvn -f DEVICE/pom.xml -pl iot-node/iot-node-api,iot-node/iot-node-biz -am compile -DskipTests
```

验收还必须执行：解析新增 JSON Schema；核对其字段集合与 DTO/validator 一致；`git diff --check`；证明本包没有调用 NODE、写数据库、生成 Compose 或修改通用部署逻辑。

## 8. OPEN03-03：release 详情与 observed CAS

### 8.1 文件边界

- `iot-device-api` 的 collector 内部 DTO/Feign 合同；
- `iot-device-biz` 的内部 Controller/Service/Mapper 和测试；
- 如现表无法保存 `AGENT_ACCEPTED`，只允许结构化审计/指标，不新增 DDL。

### 8.2 必须实现与验收

- 精确实现 M1-LC-02A §4.2 的 pending/detail/observed 三接口并复用 ADR-018 内部服务 HMAC allowlist；用户 token/租户 Header 不构成服务身份；
- pending 上限 `1..100`，只返回仍匹配目标 node/workload 的 PUBLISHED 轻量元数据；detail 返回同一 canonical bytes/hash/长度；
- observed 只有 release/tenant/node/workload/version/hash 全匹配才 CAS；重复同终态幂等，迟到/乱序/异 hash 不覆盖；
- APPLIED/FAILED/APPLY_TIMEOUT 的计时与终态语义不得混淆，FAILED 保留上一 active；
- Java 单元、服务身份/租户拒绝和真实 PostgreSQL CAS 集成测试全部通过，测试数据回滚清理。

### 8.3 验收命令

测试类名冻结为 `CollectorConfigReleaseInternalApiContractTest` 与 `CollectorConfigReleaseObservedPostgresIntegrationTest`；不得通过通配符或允许零测试掩盖缺失：

```powershell
mvn -f DEVICE/pom.xml test -pl iot-device/iot-device-biz -am `
  -Dtest=CollectorConfigReleaseInternalApiContractTest,CollectorConfigReleaseObservedPostgresIntegrationTest `
  -Dmaven.test.skip=false

mvn -f DEVICE/pom.xml -pl iot-device/iot-device-api,iot-device/iot-device-biz -am compile -DskipTests
```

PostgreSQL 测试必须实际执行且 `Skipped=0`；可使用现有本地测试数据库和受控 fixture，测试结束后相关租户数据必须为 0。还须运行 ADR-018 route allowlist/服务身份定向测试和 `git diff --check`。

## 9. OPEN03-04：NODE collector 专用部署安全边界

### 9.1 文件边界

- `NODE/agent_server.py`；
- 新增 `NODE/collector_workload.py`（或同职责拆分文件）及 `NODE/schemas/collector-workload-spec-v1.json`；Schema 必须与 OPEN03-02 的 Java 资源逐字节一致；
- `NODE/agent.env.example` 与 `NODE/install.sh` 只允许补安装侧策略变量、模块/Schema 同步，不得写入真实凭据或启用 Windows capability；
- `NODE/requirements.txt` 只允许登记 validator 实际使用的 Schema 依赖；`install.sh` 的离线 import 验证必须同步覆盖该依赖；
- `NODE/tests` 对应单元与 Flask TestClient 测试；
- 不修改 `workload_manager.py`，不修改通用工作负载对非 collector 的既有兼容语义。

### 9.2 必须实现与验收

- 新增专用 `/workload/collector/deploy`，只接受 OPEN03-02 的 WorkloadSpec；
- 专用端点继续由 ADR-018 Agent HMAC/body hash/持久 nonce 保护，token-only 必须在解析 JSON、读取策略、写文件和调用子进程前返回 401；
- 通用 `/workload/deploy` 一旦解析出 `workloadType=iot-sink-collector`，即使 `workloadId` 或其他字段缺失，也必须先于通用字段必填校验、端口探测、变更请求字典、调用 `WorkloadManager`、写文件或启动子进程返回 HTTP 400 + 稳定错误码 `UNSUPPORTED_GENERIC_DEPLOY`；
- Agent 从仓库内 Schema 校验闭合字段，并从安装侧策略读取部署档位、`repository@digest` 精确 allowlist、collector 根、串口 allowlist 和显式 CPU/内存 capability 上限。任一缺失返回 `COLLECTOR_DEPLOY_CONFIGURATION_INVALID`；`mini` 返回 `COLLECTOR_PROFILE_UNSUPPORTED`；不得提供生产资源默认值；
- `image.repository + image.digest` 必须作为一个不可分割 allowlist 条目匹配；配置 hostPath 只由公共 `collector_config_directory(stateRoot, workloadId)` 派生，固定为 `stateRoot/{identity}/config`，且 OPEN03-04 Compose 必须精确挂载这个目录到 `/var/lib/easyaiot/config`，OPEN03-05 也只能在同一目录读写 `desired.json/active.json/observed.json/history`，禁止两包各自拼路径；outbox 继续精确为 collector root + workloadId + `/outbox`，串口必须在本地 allowlist且容器路径固定；Docker socket、根目录、`..`、符号链接逃逸和跨 workload 路径拒绝；
- validator 只能生成不可变计划：Linux 为固定 Compose JSON/argv，禁止 `shell=True`；Windows 为固定 `java ... -jar app.jar --spring.profiles.active=collector` 计划且 capability 默认关闭。远端请求不得提供或影响 command/workDir/logDir/gpuIds/env/files、Compose project、容器名、restart policy、JVM 参数或宿主机配置路径；
- Compose project/container 名必须由 safe workloadId + **仅覆盖 workload identity 的 hash** 派生为小写、定长且无碰撞歧义；同一 workload 的配置版本、配置 hash、镜像 digest 或其他 spec 内容变化不得改变 project/container 名，大小写不同或可读前缀截断后相同的两个 workloadId 必须得到不同名称；restart policy 固定 `on-failure:5`；串口必须进入 Compose `devices` 的 `host:container:rwm`，不得伪装为普通 volume；
- `/dev/serial/by-id/*` 可作为安装 allowlist 中的稳定 symlink，但只允许解析到 `/dev` 内；其他宿主机路径的 symlink 及任何逃逸仍拒绝；不得因本地开发机缺少该 Linux 路径而创建或放宽它；
- Windows capability 开启时，`java`、jar 和签名 runtime-policy 必须来自安装侧绝对路径/策略 ID且全部非空；当前示例和默认值保持关闭，本包测试只检查固定 argv/关闭语义，不启动进程；
- `brokerRef` 是部署必需的节点侧不透明引用，不得在校验后静默丢弃。不可变计划必须保留该引用供专用 executor 调用注入的本地 secret resolver；`as_dict`、HTTP 响应、argv、普通环境变量、Compose JSON、日志和异常不得输出引用内容或 broker secret。默认真实 executor 在 resolver 未配置、引用无法解析或安全 secret lease 不可用时必须在调用 subprocess 前 fail-closed，绝不能启动一个没有 broker 凭据的伪成功 collector；lease 必须是实际存在、非 symlink 的本地 regular file，Linux 下不得有 group/world 权限；只能作为固定 Compose secret source，禁止同时作为 `--env-file` 或把宿主路径传给 collector；启动失败必须释放，成功后必须由 resolver/lease 以 project 为键 retain/commit 到 workload 生命周期，不能在 `compose up` 返回后立即删除而破坏 restart；本地测试只用无真实内容的 fake resolver/lease；
- 合法 Linux 流程可把固定 Compose 计划交给专用 executor；测试必须注入 fake executor，禁止实际启动 Docker/Windows 服务。计划的可序列化视图和响应不得包含 broker ref/secret、签名、nonce、token、canonical 配置或任意异常堆栈；
- 对外错误只允许稳定码：`UNSUPPORTED_GENERIC_DEPLOY`、`COLLECTOR_DEPLOY_CONFIGURATION_INVALID`、`COLLECTOR_PROFILE_UNSUPPORTED`、`COLLECTOR_WORKLOAD_SCHEMA_INVALID`、`COLLECTOR_IMAGE_FORBIDDEN`、`COLLECTOR_PATH_FORBIDDEN`、`COLLECTOR_RESOURCE_LIMIT_EXCEEDED`、`COLLECTOR_DEPLOY_FAILED`；日志不得记录请求原文、secret ref 内容或异常详情；
- 自动测试以 spy/fake 证明所有恶意请求均没有调用 `find_available_port`、`WorkloadManager`、executor、subprocess，没有写文件，也没有物化 env/files；非 collector 通用 deploy 保持既有行为。

### 9.3 冻结测试与命令

测试文件固定为：

- `NODE/tests/test_collector_workload_contract.py`：Schema 字节一致、合法 plan、同 workload 跨 spec project 稳定/不同 workload 防碰撞、brokerRef 内部保留但不可序列化、resolver 缺失时 subprocess 前拒绝、mini/配置缺失、镜像+digest、资源、路径/卷/串口、command/env/files 和 Windows capability 负例；
- `NODE/tests/test_collector_workload_routes.py`：Flask TestClient 的 HMAC 成功、token-only/重放/body hash 拒绝、通用入口提前拒绝、合法专用入口只调用 fake executor、恶意输入零副作用；
- 既有 `test_agent_security.py`、`test_agent_server_auth.py` 必须回归，后者不得再以 404 作为“签名后成功”的预期。

```powershell
python -m pytest `
  NODE/tests/test_collector_workload_contract.py `
  NODE/tests/test_collector_workload_routes.py `
  NODE/tests/test_agent_security.py `
  NODE/tests/test_agent_server_auth.py `
  -q --basetemp .codex-tmp/open03-04-pytest

python -m compileall -q NODE
git diff --check
```

验收必须 `Skipped=0`；缺少 Flask/pytest 等本地开发依赖时应补齐本地测试环境，不得用 `importorskip` 或把 404 当作通过。测试结束删除 `.codex-tmp/open03-04-pytest`；本包不执行 Docker、Windows 服务、Linux PTY、资源/稳定性或现场验证。

## 10. OPEN03-05：NODE 配置状态机

### 10.1 文件边界

- `NODE/agent_server.py` 只新增专用 PUT/GET 路由及本地 state service 装配；
- 新增 `NODE/collector_config_state.py`（可按相同职责拆为 identity/atomic/JCS 小模块）、`NODE/schemas/collector-config-snapshot-v1.1.json` 及对应 `NODE/tests`；Schema 必须与 `iot-device-api` 的 1.1 资源逐字节一致；
- `NODE/collector_workload.py` 只允许提供并复用同一 safe workload identity 与 `collector_config_directory`；`NODE/requirements.txt`、`NODE/install.sh`、`NODE/agent.env.example` 只允许登记实际使用的 JCS/文件锁依赖、同步模块/Schema和配置本地 state root/大小上限；
- 不实现 collector Java Provider，不调用 iot-device，不新增 DDL，不改通用 deploy/stop/list 的非 collector 语义。

### 10.2 必须实现与验收

- 复用 ADR-018 HMAC、持久 nonce、防重放和 current/previous key 轮换；禁止 agent-token-only 降级；
- `PUT /workload/collector/config` 请求体固定为闭合对象 `workloadId/configVersion/schemaVersion/canonicalizationVersion/payloadCanonical/payloadSha256/canonicalLengthBytes`，不接受未知/重复字段；只允许 `schemaVersion=1.1`、`canonicalizationVersion=jcs-rfc8785-v1`、正整数版本和 64 位小写 hash；
- 校验顺序固定为认证/HMAC → raw body hash/nonce → 4 MiB 请求硬上限 → 闭合 envelope → `payloadCanonical` UTF-8 字节长度（固定最多 2 MiB）/SHA-256 → JSON 无重复键解析 → 1.1 Schema → JCS 重新 canonicalize 后逐字节相等 → envelope 与 payload 的 `workloadId/configVersion/schemaVersion` 交叉一致 → 版本判断 → 磁盘；任一前置失败不得创建 workload 目录或锁文件；
- 低版本返回 HTTP 409 + `CONFIG_VERSION_STALE`，同版同 hash 返回 200 + `IDEMPOTENT` 且不重写文件，同版异 hash 返回 HTTP 409 + `CONFIG_VERSION_CONFLICT`；更高版本返回 200 + `ACCEPTED`。比较基线取本地可验证 desired/active 的最高版本，禁止因 desired 损坏而把 active 倒退；
- `desired.json`、`active.json` 和 `history/*.json` 保存的必须是收到并验证的 `payloadCanonical` 原始 UTF-8 字节，不得解析后重新序列化；`observed.json` 保存闭合的状态摘要而非 canonical payload。PUT 只原子替换 desired，不得伪造 active/APPLIED；active/observed/history 的原子原语留给 OPEN03-06 调用；
- `collector_config_directory(stateRoot, workloadId)` 是部署和状态机唯一目录事实，固定为 `stateRoot/{identity}/config`；不得再出现 raw workloadId 与 hashed identity、是否包含 `/config` 的两套拼法。所有写入使用目标同目录的唯一临时文件 → flush → file fsync → atomic replace → directory fsync（当前平台不支持的资格差异须明确记录，不能静默冒充 Linux 通过）；版本判断与替换在同一 workload 锁内完成。replace/flush/fsync/crash 注入失败时旧 desired/active 不变；遗留临时文件不作为状态读取，重启只读取通过 Schema/hash/JCS 的正式文件；
- Linux 兼容模式固定为 identity/config/history 目录 `2770`（setgid）、desired/active/observed/history/lock 文件 `0660`，不得创建成阻断 collector 共享组读取的 `0600`；代码和安装器只固化模式与 fail-closed 检查，本地 Windows 测试不冒充 owner/group/GID 资格，实际共享组/固定 GID 仍保留为 Linux 安装验证项；
- workloadId 使用同一固定 identity 编码，state root 只来自本地安装配置；请求体不得选择路径。拒绝 `.`, `..`、分隔符、绝对路径、根/跨 workload、state root/workload/正式文件或历史目录的符号链接；不得跟随未知临时文件；
- `GET /workload/collector/{workloadId}` 同样只接受 ADR-018 HMAC，返回 desired/active/observed 的版本、hash、状态和稳定错误码摘要，不返回 `payloadCanonical`、secret、签名、nonce、token、本地绝对路径或异常详情；不存在返回 `COLLECTOR_WORKLOAD_NOT_FOUND`；损坏正式状态 fail-closed 为 `COLLECTOR_CONFIG_STATE_CORRUPT`，不得猜测或用临时文件覆盖；
- 本包稳定业务错误码固定为 `COLLECTOR_CONFIG_REQUEST_INVALID`、`COLLECTOR_CONFIG_TOO_LARGE`、`COLLECTOR_CONFIG_HASH_MISMATCH`、`COLLECTOR_CONFIG_SCHEMA_INVALID`、`COLLECTOR_CONFIG_CANONICAL_INVALID`、`CONFIG_VERSION_STALE`、`CONFIG_VERSION_CONFLICT`、`COLLECTOR_CONFIG_PATH_FORBIDDEN`、`COLLECTOR_CONFIG_PERMISSION_INVALID`、`COLLECTOR_CONFIG_STATE_CORRUPT`、`COLLECTOR_CONFIG_WRITE_FAILED`、`COLLECTOR_WORKLOAD_NOT_FOUND`；日志只允许 workload identity/version/hash 短摘要和稳定码；
- 覆盖 crash 注入、半写/损坏正式文件、遗留 temp、重启恢复、重复/乱序、同版冲突、HMAC/nonce/body hash、重复 JSON key、envelope/payload 身份漂移、超大 payload、路径/symlink 和并发 PUT 负例；本地文件系统证据不得冒充 Linux 权限资格。

### 10.3 冻结测试与命令

新增测试固定为：

- `NODE/tests/test_collector_config_contract.py`：1.1 Schema 字节一致、长度/hash/JCS、重复键、未知字段、envelope/payload 交叉一致与大小上限；
- `NODE/tests/test_collector_config_state.py`：公共 config directory 与 Compose 挂载逐路径一致、safe identity、2770/0660 模式合同、版本矩阵、原子写/锁、flush/fsync/replace/crash 注入、损坏正式文件、遗留 temp、重启与 symlink/path 负例；
- `NODE/tests/test_collector_config_routes.py`：HMAC/current+previous key、token-only、nonce/body hash、PUT/GET 响应脱敏、并发/重复/乱序及磁盘前零副作用；
- OPEN03-04 的四个冻结测试和 `NODE/tests/test_agent_security.py` 必须继续回归，Skipped=0。

```powershell
python -m pytest `
  NODE/tests/test_collector_config_contract.py `
  NODE/tests/test_collector_config_state.py `
  NODE/tests/test_collector_config_routes.py `
  NODE/tests/test_collector_workload_contract.py `
  NODE/tests/test_collector_workload_routes.py `
  NODE/tests/test_agent_security.py `
  NODE/tests/test_agent_server_auth.py `
  -q --basetemp .codex-tmp/open03-05-pytest

python -m compileall -q NODE
git diff --check
```

本包只允许临时目录、Flask TestClient 和 fake/injected failure；不得启动 Docker/collector Java、不得连接 iot-device、不得执行 Linux PTY、资源/稳定性、Windows 资格或现场验证。OPEN03-04 未经 Sol 接受前，本节虽已冻结但不得开始实现。

## 11. OPEN03-06：collector 本地 Provider 与 Profile 边界

### 11.1 文件边界

- `DEVICE/iot-sink/iot-sink-api/src/main/java/com/basiclab/iot/sink/polling/`：只新增 `PollingConfigProvider`、`PollingStatusReporter`、不可变 ConfigSnapshot/总线/设备/点位 DTO 和闭合状态/错误枚举；
- `DEVICE/iot-sink/iot-sink-api/src/main/resources/schema/collector/v1.1/collector-config-snapshot-v1.1.json`：与 iot-device、NODE 的冻结 Schema 保持逐字节一致；
- `DEVICE/iot-sink/iot-sink-biz/src/main/java/com/basiclab/iot/sink/protocol/polling/`：本地文件 Provider、状态提交器、collector 调度运行时、`CollectorTelemetryWriter` 与映射适配；
- `DEVICE/iot-sink/iot-sink-biz/src/main/java/com/basiclab/iot/sink/protocol/modbus/IotModbusRtuPollingProtocol.java`：改为无中心依赖的唯一 RTU I/O 引擎；允许新增一个只负责普通 center Profile 桥接的 `CenterModbusRtuPollingAdapter`，不得复制串口读写/编解码实现；
- `DEVICE/iot-sink/iot-sink-biz/src/main/java/com/basiclab/iot/sink/SinkServerApplication.java`、`config/IotGatewayConfiguration.java`、`application-collector.yaml`、`iot-sink-biz/pom.xml` 和上述类的直接测试/fixture；`SinkServerApplication` 只允许把 center 全包扫描与 collector 白名单入口分离，不得改变 center 默认启动语义；
- `NODE/collector_workload.py`、`NODE/collector_config_state.py` 及其 OPEN03-04/05 直接测试：只允许把精确单 workload 配置挂载改为 `rw`、注入固定 workload identity，并使 `.state.lock` 与 Java `FileChannel.lock` 使用同一 POSIX record-lock 语义；
- 本包不得修改 iot-device、iot-node、DDL、Envelope、Topic、Inbox、ACK、TelemetryStore、SQLite Outbox 状态语义或通用 `WorkloadManager`。

### 11.2 必须实现与验收

- Compose 仍只挂载 `collector_config_directory(stateRoot, workloadId)` 到 `/var/lib/easyaiot/config`，但由 `:ro` 收紧修正为该单 workload 目录 `:rw`，同时注入非秘密 `EASYAIOT_COLLECTOR_WORKLOAD_ID`；禁止扩大到 state root、其他 workload 或 Agent 安装根。outbox 仍为独立精确 `rw` 卷；
- 文件所有权闭合：Agent 只创建/替换 `desired.json`；collector 只创建/替换 `active.json`、`observed.json`、`history/{version}-{hash8}.json`，双方不得写对方拥有的正式文件。所有读改写进入同一 `.state.lock`；Linux 目标用与 Java `FileChannel.lock` 可互操作的 POSIX record lock。Windows 本地只验证可移植合同，不冒充 Linux 锁/owner/GID 资格；
- `collector` Profile 只装配本地文件 `PollingConfigProvider`、`PollingStatusReporter`、唯一 RTU 引擎、collector runtime、`CollectorTelemetryWriter` 与既有 collector SQLite `TelemetryOutboxPort`/dispatcher。应用入口必须把原 `com.basiclab.iot` 全包扫描置于 `!collector` center 配置内；collector 只能显式导入上述白名单配置，禁止依赖 `@Profile` 对 100+ 个中心组件逐个排除。首次无 active/desired 写 `WAITING_CONFIG`，不得查询中心数据库、Redis、Nacos 配置/发现、Feign 或中心消息总线；collector 所需 MQTT outbox publisher 可在生产启用，但本地 Bean 图测试固定关闭，禁止发起网络连接；
- Provider 必须按 4 MiB 上限、严格 UTF-8/重复键/非有限数、ConfigSnapshot 1.1 Schema、JCS 原字节、workloadId、正整数安全版本和 SHA-256 顺序验证 `active.json/desired.json`；Schema 三副本 SHA-256 必须一致。任何验证失败只写闭合 `FAILED + stable errorCode`，不得记录 canonical payload 或自由文本详情；
- 启动顺序固定：锁内验证 active；有合法 active 先构建内存调度图；再检查 desired。无 active 且无 desired 为 `WAITING_CONFIG`。合法 desired 与 active 同版本同 hash 为幂等；低版本或同版本异 hash fail-closed；更高版本候选必须先完整验证并构建新调度图，失败保持旧图和旧 active；
- 成功提交顺序固定为：锁内幂等写 history 原 canonical bytes → 同目录 temp/flush/file-fsync/atomic-replace active → directory-fsync（平台支持时）→ 原子替换内存调度图 → 原子写 `observed.json(APPLIED)`。active 一旦落盘后进程崩溃，重启必须以 active 重建；不得先切内存再持久化。历史同版本同 hash幂等，同版本异 hash视为状态损坏；
- `observed.json` 只能使用 OPEN03-05 的闭合字段集。`APPLIED` 必须含 version/hash 且无 errorCode；`FAILED` 尽可能带候选 version/hash并只带稳定码；`WAITING_CONFIG` 不伪造 version/hash。状态文件仍使用 canonical 紧凑 JSON；
- `IotModbusRtuPollingProtocol` 是唯一串口读写/编解码实现且不得 import/持有 `DeviceMapper`、`IotDeviceMessageService`、`IotMessageBus`。collector runtime 仅从已应用快照调度并将每次轮询结果作为原子批次写 `TelemetryOutboxPort`；不得直接发中心消息或回写中心设备表；
- 普通 center Profile 通过 `CenterModbusRtuPollingAdapter` 保留现有数据库扫描、消息总线/下行和状态回写行为，standard/full 共用同一 RTU 引擎，不复制第二套协议实现；
- 目录/正式文件继续执行 OPEN03-05 的 POSIX `02770/0660` 合同与符号链接拒绝；容器真实 UID/GID、Linux owner/GID 和跨语言锁互操作的部署资格只记录为运行期 OPEN，不得在 Windows 本地冒充通过。

### 11.3 冻结错误码

只允许以下 collector 应用错误码进入 `observed.json`：`COLLECTOR_CONFIG_NOT_AVAILABLE`、`COLLECTOR_CONFIG_TOO_LARGE`、`COLLECTOR_CONFIG_JSON_INVALID`、`COLLECTOR_CONFIG_SCHEMA_INVALID`、`COLLECTOR_CONFIG_CANONICAL_INVALID`、`COLLECTOR_CONFIG_WORKLOAD_MISMATCH`、`COLLECTOR_CONFIG_VERSION_STALE`、`COLLECTOR_CONFIG_VERSION_CONFLICT`、`COLLECTOR_CONFIG_STATE_CORRUPT`、`COLLECTOR_CONFIG_PERMISSION_INVALID`、`COLLECTOR_CONFIG_WRITE_FAILED`、`COLLECTOR_CONFIG_APPLY_FAILED`。异常类名、路径、串口名、payload 和堆栈不得进入 observed 或普通 INFO 日志。

### 11.4 冻结测试矩阵与命令

至少覆盖：三副本 Schema byte/hash；合法首次应用/升级/同版本幂等；首次无配置；非法 JSON/重复键/未知字段/非 canonical/错 workload/低版本/同版本异 hash；候选构图失败与 active/history/observed 原子写失败均保留旧 active 和旧内存图；active 已提交后模拟崩溃并重启恢复；符号链接、越界路径和权限漂移 fail-closed；collector Bean 图与 RTU 引擎的中心依赖禁入；center adapter 行为兼容；采集只写 fake `TelemetryOutboxPort`；NODE 精确 `rw` 配置挂载和 workload env，不扩大路径；不触碰真实串口。Bean 图测试不得只检查注解或源码字符串：必须以 `WebApplicationType.NONE`、临时 config/outbox、关闭 collector MQTT 的真实 `collector` Spring 上下文启动，断言本地 Provider/runtime/SQLite outbox 存在，且 `DeviceMapper`、中心 service/controller/message bus、Redis、Nacos/Feign 客户端 Bean 不存在；测试不得连接外部系统。

```powershell
mvn -f DEVICE/pom.xml test -pl iot-sink/iot-sink-biz -am `
  "-Dtest=CollectorConfigSchemaContractTest,LocalFilePollingConfigProviderTest,CollectorPollingRuntimeTest,CollectorProfileArchitectureTest,CollectorSpringContextTest,CenterModbusRtuPollingAdapterTest,CollectorTelemetryWriterTest,PollingResultMapperTest" `
  -DfailIfNoTests=false -Dmaven.test.skip=false

python -m pytest `
  NODE/tests/test_collector_config_state.py `
  NODE/tests/test_collector_workload_contract.py `
  NODE/tests/test_collector_workload_routes.py `
  -q --basetemp .codex-tmp/open03-06-pytest

python -m compileall -q NODE
git diff --check
```

本包只允许临时目录、fake `TelemetryOutboxPort`、fake RTU I/O、故障注入和 Spring/反射架构测试；不得启动 Docker、访问真实串口/中心数据库/Nacos/消息代理，不得执行 Linux PTY、资源/稳定性、Windows 资格或现场验证。

## 12. OPEN03-07：iot-node 派发与对账

### 12.1 文件边界

- `iot-node-api/src/main/java/com/basiclab/iot/node/domain/collector/config/`：只新增 NODE 专用配置 PUT/GET 的闭合请求、响应 DTO 与枚举；不得复制或修改 iot-device 的 release DTO；
- `iot-node-biz/src/main/java/com/basiclab/iot/node/service/collector/config/`：新增 `CollectorConfigDispatchService`、release client port/适配器、节点权威查询 port/适配器、`CollectorAgentClient`、确定性退避器、单实例防重入 job 及内部结果类型；
- `iot-node-biz/pom.xml`：只允许新增对既有 `iot-device-api` 的类型化编译依赖，以直接注入 `CollectorConfigReleaseInternalApi`；禁止反射/类名查找适配或复制 Feign DTO；
- `iot-node-biz/src/main/resources/application.yaml`：只允许新增 `easyaiot.collector.config-dispatch` 配置，`enabled` 默认 `false`；本包不得修改 dev/local/prod 文件或启用生产轮询；
- `iot-node-biz/src/test/java/com/basiclab/iot/node/service/collector/config/`：新增本节冻结测试；允许使用注入 fake、固定 `Clock` 和 JDK localhost 假 Agent，不得依赖外部网络或新增 WireMock 依赖；
- 只读复用 `CollectorConfigReleaseInternalApi`、`ComputeNodeMapper.selectById`、`NodeStatusEnum.ONLINE`、`NodeAgentRequestSigner`。禁止修改或引用 `NodeCommandServiceImpl`、通用 `/workload/deploy`、通用 files/env/command 能力，禁止新增 DDL、repository 写入或 iot-node canonical 缓存表。

### 12.2 必须实现与验收

#### 12.2.1 固定流程与身份校验

每次最多拉取 `1..100` 条 pending；同一 job 进程内单飞，单条失败不得阻断同批其他 release。对每条严格执行：

1. 用 pending 的 `releaseId` 拉 detail；pending/detail 的 `releaseId/tenantId/nodeId/workloadId/configVersion/schemaVersion/canonicalizationVersion/payloadSha256/canonicalLengthBytes/publishedAt` 必须逐字段一致；所有 bigint 字符串必须是正十进制且不超过 Java `long` 正数范围，`configVersion` 还必须不超过 JSON 安全整数上限；
2. 只允许 `schemaVersion=1.1`、`canonicalizationVersion=jcs-rfc8785-v1`、小写 64 hex；按原 `payloadCanonical` UTF-8 bytes 自校验固定 2 MiB 上限、声明长度和 SHA-256。不得解析后重排、重新 canonicalize 或改变发送 bytes；
3. `ComputeNodeMapper.selectById(nodeId)` 是节点地址唯一权威源；必须存在、主键相同、状态精确为 `online`，host 非空且不得含 scheme、路径、userinfo、query、fragment、控制字符，`agentPort` 必须在 `1..65535`。release、detail、配置 payload 和调用方都不得提供 Agent URL；
4. 用 `NodeAgentRequestSigner` 对精确路径签名：PUT `/workload/collector/config` 签署并发送与 HTTP body 完全相同的序列化 bytes；GET `/workload/collector/{url-encoded-workloadId}` 签署空 body。客户端必须禁用重定向、在完整缓冲前把响应读取硬限制为 1 MiB（最多探测第 1 MiB + 1 byte 后关闭 body）、使用可配置 connect/read timeout，且只接受 HTTP 2xx、闭合包装 `{code:0,msg,data}`；
5. PUT body 精确为 `workloadId/configVersion/schemaVersion/canonicalizationVersion/payloadCanonical/payloadSha256/canonicalLengthBytes`。Agent `ACCEPTED` 或 `IDEMPOTENT` 且 workload/version/hash 精确匹配后，先向 iot-device 幂等报告 `AGENT_ACCEPTED`，再 GET 对账；
6. GET 的 `workloadId` 必须匹配。`observed=APPLIED` 且 version/hash 精确匹配才报告 `APPLIED`；`observed=FAILED` 且候选 version/hash 精确匹配才报告 `FAILED`，只转发 allowlist 内稳定错误码；`WAITING_CONFIG/DEGRADED/null`、active/desired/observed 的旧版、新版或异 hash 均只审计并退避重试，绝不能覆盖 iot-device 终态；
7. iot-node 不产生、不报告 `APPLY_TIMEOUT`，也不因接单后等待超时报告 `FAILED`；应用超时只由 iot-device 按已冻结 CAS/时钟规则计算。`observedAt` 使用注入 `Clock` 的 UTC ISO-8601，`errorDetailSanitized` 固定为空，不转发 Agent msg、异常文本、路径或响应原文。

#### 12.2.2 响应、错误分类与退避

- PUT 成功 `data.status` 只允许 `ACCEPTED/IDEMPOTENT`；GET 只接受 OPEN03-05 的 `workloadId/desired/active/observed` 脱敏摘要。未知字段、未知状态、包装 `code!=0`、身份漂移、超限或畸形 JSON 一律 fail-closed；
- 可重试且不向 iot-device 伪造 FAILED：iot-device 暂时不可用/5xx/timeout、节点不存在或离线、签名密钥暂不可用、Agent 连接/timeout/5xx、GET 404 `COLLECTOR_WORKLOAD_NOT_FOUND`、WAITING/DEGRADED、observed 身份不匹配；
- 可归因于本 release 的确定性终止错误才报告 FAILED：pending/detail 或 canonical 长度/hash/版本合同不一致；Agent PUT 的 `CONFIG_VERSION_STALE`、`CONFIG_VERSION_CONFLICT` 及其他闭合 4xx 配置拒绝。Agent 401/403 必须归为安全配置故障并保持可重试，不得把凭据故障写成业务 FAILED；
- Agent `observed=FAILED` 的 errorCode 只允许 OPEN03-06 §11.3 的稳定码；缺失、未知或超长时统一为 `COLLECTOR_CONFIG_APPLY_FAILED`；HTTP/Feign/解析异常只映射内部枚举，不得把类名、message 或响应正文上报；
- 退避只保存 `releaseId/configVersion/payloadSha256/attempt/nextAttemptAt`，禁止保存 canonical。算法固定为 `min(baseDelay * 2^(attempt-1), maxDelay)`，默认 base `1s`、max `60s`，注入 Clock；有界内存最多 10000 项，终态清理，超限淘汰最早 `nextAttemptAt`。进程重启可丢失退避状态，幂等由两端合同保证；
- 普通 INFO 日志只允许 releaseId、nodeId、workloadId、configVersion、hash 前 8 位和内部稳定结果码；不得记录 canonical、完整 Agent 响应、host URL、签名头、nonce、secret 或异常堆栈。方法返回后不得在字段、队列、future、指标标签、退避项或日志中保留 canonical/完整 detail 引用。
- `enabled=false` 时不得创建 job 或发起轮询；`enabled=true` 时 Spring 必须能直接注入类型化 `CollectorConfigReleaseInternalApi`、Mapper、signer/ports/service/job，并按固定 delay 触发 `dispatchPending(batchLimit)`。禁止只提供未装配普通类、反射等待运行期对象或要求调用方手工 new 才能运行；装配测试仍不得连接外部系统。

### 12.3 冻结测试与命令

测试类固定为：

- `CollectorConfigDispatchServiceTest`：ACCEPTED→APPLIED、IDEMPOTENT、批次隔离；pending/detail/长度/hash/版本漂移；节点不存在/离线；Agent 不可达、5xx、401、GET 404；PUT 409；WAITING/DEGRADED；FAILED allowlist；迟到/乱序/异 hash observed；接单后等待不产生 FAILED/APPLY_TIMEOUT；固定 Clock、指数退避/上限/清理；证明 canonical 未进入退避/持久化/日志；
- `CollectorAgentClientTest`：JDK localhost 假 Agent 验证精确 PUT/GET path、HMAC 六个 header、签名 body 与实际 body 字节一致、包装 `code=0`、响应大小/超时/无重定向、闭合 DTO、脱敏；spy 证明从未调用 `/workload/deploy`；
- `CollectorConfigDispatchArchitectureTest`：生产类不引用 `NodeCommandServiceImpl`、通用 deploy、repository update/insert、任意 URL/files/env/command；配置开关默认关闭；只允许固定专用路径；
- 回归 `NodeAgentSigningKeyProviderTest` 与 `CollectorWorkloadSpecContractTest`。所有测试必须 `Skipped=0`，不得用允许零测试掩盖类名错误。

```powershell
mvn -f DEVICE/pom.xml test -pl iot-node/iot-node-biz -am `
  "-Dtest=CollectorConfigDispatchServiceTest,CollectorAgentClientTest,CollectorConfigDispatchArchitectureTest,NodeAgentSigningKeyProviderTest,CollectorWorkloadSpecContractTest" `
  -Dsurefire.failIfNoSpecifiedTests=false -Dmaven.test.skip=false

mvn -f DEVICE/pom.xml -pl iot-node/iot-node-api,iot-node/iot-node-biz -am compile -DskipTests
git diff --check
```

本包只执行 Java 单元/架构测试和 localhost 假 Agent；不得连接真实 iot-device/Agent、不得启动 Docker/collector、不得执行 Linux PTY、真实串口、资源/稳定性、Windows 资格或现场验证。生产 job 开关保持默认关闭；OPEN03-08 未经 Sol 授权不得开始。

## 13. OPEN03-08：跨 TD 组合合同与联合冻结

> 冻结版本：v2（2026-08-17，GPT-5.6 Sol）  
> 执行授权：GPT-5.6 Luna，`max reasoning`。本节之外没有隐含授权。

### 13.1 单一目标与非目标

本包只回答一个问题：OPEN03-01～07 已落地的发布、派发、Agent 文件状态、collector 应用、SQLite outbox 和健康摘要能否在同一组不可变 fixture 下组合，且不与 TD-001/002/003 冲突。不得新增第二套业务状态机，不得把假服务结果表述为生产运行 E2E。

明确非目标：不修改 Envelope/Topic/Inbox/ACK/`TelemetryStore`/SQLite 表或状态机；不新增 DDL；不启用生产派发、collector MQTT 或其他默认关闭开关；不启动 Docker、Nacos、Redis、MQTT、TDengine；不访问真实串口；不执行 Linux PTY/owner/GID/directory-fsync 资格、资源/稳定性压测、Windows 发布资格或现场验证。

### 13.2 允许文件边界

只允许修改或新增：

1. `DEVICE/iot-sink/iot-sink-api/src/main/java/com/basiclab/iot/sink/health/` 下的四 facet 健康值对象；
2. `DEVICE/iot-sink/iot-sink-biz/src/main/java/com/basiclab/iot/sink/health/CollectorHealthAggregator.java`，以及 `iot-sink-api/biz` 直接对应的合同测试；
3. `DEVICE/iot-node/iot-node-biz/src/test/java/com/basiclab/iot/node/service/collector/config/CollectorOpen03CombinedStageTest.java`；
4. `DEVICE/iot-sink/iot-sink-biz/src/test/java/com/basiclab/iot/sink/protocol/polling/CollectorOpen03CombinedApplyStageTest.java`；
5. `NODE/collector_workload.py` 中仅用于拒绝 config/outbox 根相等或互相嵌套的路径校验，及其直接 pytest；
6. `.scripts/tests/open03-08/` 下的只读 golden fixture、预期 trace 和本地编排器；
7. 本任务单、M1-LC-02A、TD-001 与 M1 SDD 进度记录。

禁止给任何生产 Maven 模块增加对其他模块 `*-biz` 的依赖，包括 `test` scope；组合只能通过固定 HTTP/文件合同和仓库级测试编排完成。禁止修改 OPEN03-01～07 的生产行为来迁就 E2E。若现有公开合同无法按本节编排，立即停止并交回 Sol，不得扩大边界。

### 13.3 不可变 fixture

`.scripts/tests/open03-08/fixtures/` 固定包含：

- `collector-config-v1-success.json`：ConfigSnapshot 1.1 canonical UTF-8 原字节，`workloadId=collector-open03-e2e-a`、`configVersion=1`；
- `collector-config-v2-apply-failure.json`：Schema/JCS 均合法、同 workload、`configVersion=2` 的候选；失败只由测试 GraphApplier 的显式 `prepare` 故障注入产生，不得把非法 JSON 冒充应用失败；
- `manifest.json`：闭合保存两份 fixture 的 workload/version/SHA-256/UTF-8 字节长度、releaseId 和预期终态；不得包含 HMAC key、token、路径或端口；
- `expected-trace.json`：事件顺序固定为成功链 `PUBLISHED → AGENT_ACCEPTED → DESIRED_WRITTEN → COLLECTOR_APPLIED → DEVICE_APPLIED`，失败链固定为 `PUBLISHED → AGENT_ACCEPTED → DESIRED_WRITTEN → COLLECTOR_FAILED → DEVICE_FAILED`。

测试每次先重算 SHA-256/长度并与 manifest 比较；任一字节漂移立即失败。HMAC key 每次运行随机生成到仓库外临时文件，只传文件路径，不进入命令行、日志、trace 或 fixture，结束后删除。

### 13.4 本地假服务与跨进程编排

仓库级 Python 编排器必须使用参数数组启动子进程，任何阶段非零立即终止，并在 `finally` 关闭服务、删除密钥与临时目录。拓扑固定如下：

1. 在 `127.0.0.1` 随机空闲端口启动实际 `NODE/agent_server.py` Flask app；状态实现必须是实际 `CollectorConfigStateService`，HMAC/nonce 必须走实际生产校验，只替换为运行期随机测试 key 和临时 replay DB；
2. iot-node 阶段测试必须实例化实际 `CollectorConfigDispatchService`、`CollectorAgentClient` 与 signer；只允许 `CollectorConfigReleaseClientPort` 使用文件持久的 fake iot-device release 服务，`CollectorNodeAuthorityPort` 使用指向该 loopback Agent 的固定 fake 节点；
3. iot-sink 阶段测试必须实例化实际 `LocalFilePollingConfigProvider`，从 Agent 写入的同一 config 目录读取 desired；串口/调度图只使用无网络 GraphApplier，不启动 Spring、MQTT 或真实串口；
4. config root 与 outbox root 必须是同一临时父目录下两个互不相等、互不为祖先的 sibling；Agent/collector 只共享精确 workload config 目录，outbox 目录在该 E2E 中只做路径和 SQLite durable 合同，不作 MQTT 发送；
5. iot-node 的 fake release 服务只模拟已由 OPEN03-03 真实 PostgreSQL 测试证明的 PUBLISHED/CAS 边界；它必须持久保存结构化 release 状态，拒绝错 release/tenant/node/workload/version/hash 和相反终态，不保存 canonical 到 trace/backoff。

成功链按四个串行阶段执行：创建 PUBLISHED v1 → iot-node 派发并确认 Agent 只写 desired、回报 AGENT_ACCEPTED → iot-sink 应用并原子写 history/active/observed APPLIED → iot-node 再次以幂等 PUT + GET 对账并把 fake release 推进 APPLIED。最终逐字节比较 desired/active/history 与 v1 fixture，并确认响应/trace 无 canonical、key、nonce、签名和绝对路径。

失败链在成功链的 active v1 上继续：创建 PUBLISHED v2 → Agent desired v2 → GraphApplier `prepare` 固定失败 → observed 只能是 `FAILED/COLLECTOR_CONFIG_APPLY_FAILED` → iot-node 回报 fake release FAILED。最终必须证明 active 仍逐字节等于 v1、内存图仍为 v1、v2 history 不存在、desired v2 保留用于诊断；不得回报 APPLIED/APPLY_TIMEOUT。

### 13.5 跨 TD 机器合同

新增的 `CollectorCrossTdContractTest` 必须同时证明：

- `TelemetryOutboxPort.appendBatch` 精确返回 `AppendBatchResult`，参数精确为 `List<TelemetryEnvelope>, Duration`；不得混淆 TD-003 `TelemetryStore.appendBatch(List<TelemetrySample>)`；
- 实际 `SqliteTelemetryOutbox` 返回前已完成外层 COMMIT：shutdown 后以同一数据库重开仍可 claim 原 messageId；同 bytes 重试为 `DUPLICATE`，同 ID 异 hash 为 collision/整批不部分提交；
- 队列入队超时只暴露 `OutboxBackpressureException`，数据库损坏/只读/I/O/提交不可用只暴露 `OutboxUnavailableException`；失败不得调用旧消息总线；
- Java collector 配置路径与 outbox `volumePath=/var/lib/easyaiot/outbox` 不相等、不嵌套。NODE validator 对 host `state_root`/`collector_root` 相等或任一包含另一方 fail-closed，正常 sibling 仍生成精确 `/var/lib/easyaiot/config:rw` 与 `/var/lib/easyaiot/outbox:rw` 两个挂载；
- 健康 summary 的 facet 精确且只包含 `process/config/serial/center`；总体和 center 均按 `FAILED > DEGRADED > HEALTHY` 聚合。outbox、MQTT、应用 ACK 先聚合到 `center`，禁止出现并列 `outbox` facet；只有 outbox/MQTT/ACK 全 HEALTHY 时 center 才为 HEALTHY，outbox FAILED 必须使 center 和总体 FAILED。

健康值对象只承载闭合状态、稳定 reasonCode 与 UTC `since`；不得包含路径、异常文本、凭据或自由扩展 Map。不得在本包接入生产 heartbeat；本包只冻结可供后续接线复用的 API 值对象与纯函数聚合器。

### 13.6 测试类与验收命令

新增测试类名固定为：

- `CollectorCrossTdContractTest`
- `CollectorHealthAggregatorTest`
- `CollectorOpen03CombinedStageTest`
- `CollectorOpen03CombinedApplyStageTest`
- `test_open03_08_path_contract.py`

组合编排器入口固定为 `.scripts/tests/open03-08/run_open03_08_combined_e2e.py`。它必须校验实际执行到成功链和失败链，不得以环境缺失 `skip`；缺 Java、Maven、Python、Flask 或 fixture 时直接失败。以下命令全部成功且测试 `Skipped=0` 才可交 Sol：

```powershell
python .scripts/tests/open03-08/run_open03_08_combined_e2e.py

mvn -f DEVICE/pom.xml test -pl iot-sink/iot-sink-biz -am `
  "-Dtest=CollectorCrossTdContractTest,CollectorHealthAggregatorTest,CollectorOpen03CombinedApplyStageTest,CollectorConfigSchemaContractTest,LocalFilePollingConfigProviderTest,CollectorPollingRuntimeTest,CollectorSpringContextTest,SqliteOutboxAppendBatchTest,SqliteOutboxDurabilityTest" `
  -Dsurefire.failIfNoSpecifiedTests=false -Dmaven.test.skip=false

mvn -f DEVICE/pom.xml test -pl iot-node/iot-node-biz -am `
  "-Dtest=CollectorOpen03CombinedStageTest,CollectorConfigDispatchServiceTest,CollectorAgentClientTest,CollectorConfigDispatchArchitectureTest,CollectorConfigDispatchConfigurationTest" `
  -Dsurefire.failIfNoSpecifiedTests=false -Dmaven.test.skip=false

python -m pytest -q `
  NODE/tests/test_open03_08_path_contract.py `
  NODE/tests/test_collector_config_contract.py `
  NODE/tests/test_collector_config_state.py `
  NODE/tests/test_collector_config_routes.py `
  NODE/tests/test_collector_workload_contract.py `
  NODE/tests/test_collector_workload_routes.py

mvn -f DEVICE/pom.xml test -pl iot-device/iot-device-biz `
  "-Dtest=CollectorConfigReleaseInternalApiContractTest,CollectorConfigReleaseObservedPostgresIntegrationTest" `
  -Dmaven.test.skip=false

mvn -f DEVICE/pom.xml -pl iot-device/iot-device-biz,iot-node/iot-node-biz,iot-sink/iot-sink-biz -am compile -DskipTests
python -m compileall -q NODE .scripts/tests/open03-08
git diff --check
```

PostgreSQL 合同必须连接现有本地测试实例、实际执行且 `Skipped=0`，结束后相关 fixture 为 0；组合编排器不得替代该证据。验收还必须检查所有临时服务已停止、临时目录已删除、仓库内无 key/replay DB/SQLite DB/日志/target 之外生成物、生产开关仍为默认关闭。

### 13.7 完成判定与回滚

Luna 交付必须列出变更文件、成功/失败 trace 摘要、每条命令的精确 tests/failures/errors/skipped、未执行运行项、风险和 `git diff --check`。不得 commit。

Sol 独立重跑并复核后，只有全部合同无冲突才将门禁 1、2、3、6 标为 `CLOSED-LOCAL`；门禁 4、5 继续 `OPEN-RUNTIME`，所以 TD-001 与 OPEN-03 总体仍不得标为完成。回滚为删除 OPEN03-08 新增健康值对象/聚合器、测试、fixture/编排器，并恢复本包对 `collector_workload.py` 的独立根校验；不得回滚或改写 OPEN03-01～07 已验收产物。

## 14. 每包交付格式与停止条件

Luna 每包必须交付：变更文件、合同差异、已执行命令与精确结果、未执行项、风险、回滚方式、`git diff --check`。不得提交或回显凭据，不得自行 commit。

出现以下任一情况立即停止并交回 Sol：

- 需要新增/执行 DDL、启用生产开关、修改 Envelope/Outbox/Topic/Inbox/ACK/Store；
- 现有代码事实与冻结合同冲突，且会导致破坏性 API 或历史 1.0 bytes/hash 变化；
- 测试只能通过削弱安全、跳过断言、使用真实凭据或扩大任意命令/路径能力；
- 需要 Linux PTY、资源压测、Windows 资格或现场环境才能继续。

## 15. 冻结签字

- Sol 决定：门禁 1～3、6 的本地实现任务边界于 2026-08-16 冻结；
- 当前执行授权：`OPEN03-08A` 及 S1 已由 Sol 验收，`OPEN03-08` v2 已恢复 Luna Max 实现与测试；
- Luna 模型要求：`gpt-5.6-luna`，`max reasoning`；
- 下一状态：Luna Max 完成 OPEN03-08 v2 组合 E2E 与联合合同后交 Sol 独立复核。

## 16. OPEN03-01 Sol 验收记录（2026-08-17）

结论：`ACCEPTED`。Luna 的变更严格落在 §6 文件边界；Sol 独立核对并补齐真实 PostgreSQL 证据后解锁 OPEN03-02。

- ConfigSnapshot v1 golden bytes/hash 保持不变；v1.1 只增加服务端产品身份，并由同一 artifact 固化 schema/canonical/hash/长度；
- 客户端伪造 `productIdentification`、未知字段、错误版本、空白/超长产品身份均 fail-closed；
- `CollectorConfigSnapshotContractTest` 10/10 PASS；
- `PowerModelBindingApplyServiceContractTest` 2/2 PASS；
- 本地 `postgres-server` 真实 JDBC：`PowerModelBindingApplyPostgresIntegrationTest` 3/3 PASS，包含 v1.1 schema、产品身份、hash/长度与失败整体回滚，Skipped=0；
- `iot-device-biz -am compile`：33 个 reactor 模块成功；JSON Schema 可解析；`git diff --check` 通过；
- 未修改 DDL、Controller、release API、NODE、iot-node、iot-sink 或运行开关，未执行运行期门禁 4、5。

门禁影响：TD-001 §19 门禁 2 仍为 `PARTIAL / OPEN`；本包只关闭其中 ConfigSnapshot 1.1 子缺口，WorkloadSpec、release/observed CAS、NODE/collector 状态机和组合 E2E 尚未完成。

## 17. OPEN03-02 Sol 验收记录（2026-08-17）

结论：首次复核发现配置路径可选和默认资源配额两项架构风险，任务合同收紧并由 Luna 修正后状态为 `ACCEPTED`。

- WorkloadSpec 1.0 Schema、DTO、validator、golden fixture 的闭合字段集合一致；内部 `nodeId` 为 bigint 十进制字符串；
- `config.targetPath` 固定为容器路径 `/var/lib/easyaiot/config/active.json`，宿主机配置根不由请求选择；
- outbox hostPath 精确绑定 `{collectorRoot}/{workloadId}/outbox`，串口路径来自安装 allowlist；任意 command/env/files、Docker socket、跨 workload 卷和符号链接逃逸 fail-closed；
- Schema 64 CPU/64 GiB 仅为传输反滥用上限；validator 必须显式注入安装侧 capability 配额，无默认生产配额；
- Sol 独立运行 `CollectorWorkloadSpecContractTest` 12/12 PASS、22 模块 reactor 全部 SUCCESS，Schema 可解析、`git diff --check` 通过；
- 未调用 NODE、未落库、未生成 Compose、未修改通用部署逻辑，未执行运行期门禁 4、5。

门禁影响：门禁 2 的 ConfigSnapshot 1.1 与 WorkloadSpec 机器合同子缺口已关闭；release/observed CAS、NODE/collector 状态机、派发对账和组合 E2E 仍未完成，故门禁 2 继续 `PARTIAL / OPEN`。

## 18. OPEN03-03 Sol 验收记录（2026-08-17）

结论：首次复核发现 ADR-018 allowlist 只支持静态精确路径，导致带 `releaseId` 的真实详情/回报请求必然被拒绝；Sol 收紧合同并由 Luna Max 修正后状态为 `ACCEPTED`。

- `iot-device-api/biz` 已实现 pending、detail、observed 三个内部接口；bigint ID 均以十进制字符串传输；detail 仅向仍为 `PUBLISHED` 且匹配 `ACTIVE` 目标投影的发布单返回原 canonical/hash/长度；
- ADR-018 通用 route allowlist 增加单路径段 `{name}` 模板，不能跨 `/`、匹配空段、吞 query 或额外路径段；真实 detail/observed 路径、POST raw body hash、错服务身份、token-only、错 body hash 和越界路径均有定向合同；
- observed JSON 字段闭集；release/tenant/node/workload/version/hash 全匹配后才在数据库行锁事务内 CAS。`AGENT_ACCEPTED` 仅记结构化事实，`APPLIED/FAILED` 才推进终态，重复同终态幂等，错身份事实、异 hash、低/错版本及相反终态晚到均不覆盖；Agent 不能自报 `APPLY_TIMEOUT`；
- iot-device 配置显式登记三条 allowlist route；`EASYAIOT_INTERNAL_SERVICE_AUTH_ENABLED` 默认 `false`，未提交 secret、未启用生产开关；
- Sol 独立运行 Collector 冻结测试 8/8 PASS（其中真实 PostgreSQL 2/2、Skipped=0）、ADR-018 合同 4/4 PASS；33 个 reactor 模块 compile/test SUCCESS；八类 PostgreSQL fixture 只读核对均为 0；`git diff --check` 通过；
- 未新增/执行 DDL，未实现 NODE/iot-node 派发，未执行 Linux PTY、资源/稳定性压测或 Windows 资格。

门禁影响：门禁 2 的 release 详情/observed CAS 子缺口关闭；NODE 配置状态机、collector Provider、iot-node 派发和组合 E2E 仍未完成，故门禁 2 继续 `PARTIAL / OPEN`。当前只授权 OPEN03-04 收敛门禁 3 的 NODE collector 专用部署安全边界。

## 19. OPEN03-04 Sol 验收记录（2026-08-17）

结论：首次复核发现 `brokerRef` 被静默丢弃、project 随 spec hash 漂移两项真实部署语义错误；Sol 将 §9 收紧至 v1.0.6，Luna Max 修正后状态为 `ACCEPTED`。

- 通用 `/workload/deploy` 对 `workloadType=iot-sink-collector` 精确早拒绝，即使缺 `workloadId` 也先返回 `UNSUPPORTED_GENERIC_DEPLOY`，且不会端口探测、修改请求、调用 `WorkloadManager`、写文件或启动子进程；非 collector 既有语义未改；
- 专用 `/workload/collector/deploy` 复用 ADR-018 HMAC、raw body hash 和持久 nonce，token-only、重放、错 hash/签名在解析业务 JSON 和 executor 前拒绝；
- WorkloadSpec Schema 与 Java 资源 SHA-256 同为 `013C597D80436C93901ED53D36B277C5385558DA42C28EF4ED3073CDB008D883`；安装侧 profile、精确 `repository@digest`、根路径、串口及显式资源上限任一缺失均 fail-closed，mini 关闭；
- Linux 固定 Compose 使用小写定长 workload identity 名、`on-failure:5`、`devices host:container:rwm`、只读根、cap drop/no-new-privileges；同 workload 跨配置/spec 名称稳定，不同 identity 防碰撞；Windows 固定 argv capability 默认关闭；
- `brokerRef` 仅在不可变计划内部保留；可序列化计划、响应、argv、普通 env 和日志不含 ref/secret。resolver/安全 lease 缺失在 subprocess 前拒绝；lease 只作为固定 Compose secret source，失败释放、成功按 project 保留；未提供真实 resolver 时不会伪启动无 broker 凭据实例；
- Sol 独立执行冻结测试与 NODE 全量均为 26/26 PASS、Skipped=0；`compileall`、Schema hash、`git diff --check` 和临时目录清理通过；`workload_manager.py` 未改；
- 未启动 Docker/Windows 服务，未执行 Linux PTY、资源/稳定性压测、现场验证或生产开关，也未写入真实凭据。

门禁影响：门禁 3 的本地专用 fail-closed/固定模板/allowlist 子缺口已完成，但按 §13 仍等待 OPEN03-08 联合冻结后统一标记 `CLOSED-LOCAL`。门禁 2 仍缺 NODE 配置状态机、collector Provider、iot-node 派发与组合 E2E；当前唯一授权切换为 OPEN03-05。

## 20. OPEN03-05 Sol 验收与 OPEN03-06 再冻结记录（2026-08-17）

结论：`OPEN03-05 ACCEPTED`；OPEN03-06 发现并消除了跨进程文件所有权冲突后，以 §11 v2 重新冻结并授权 Luna Max。

- NODE 已实现闭合配置 envelope、4 MiB 请求/2 MiB canonical 上限、严格 JSON/JCS/Schema/hash/长度/workload/version 校验；ConfigSnapshot 1.1 Schema 与 iot-device 资源逐字节一致，长度 `3853`，SHA-256 `52FCC23AE0DF65BE19C902E604611A4078ABF9E89B13EF91E5DC05D088C7A28A`；
- `collector_config_directory(stateRoot, workloadId)` 成为部署和状态机唯一目录事实，固定为 `stateRoot/{identity}/config`；原 raw workload 路径与 hashed identity 路径漂移已消除；
- PUT 只原子替换 `desired.json`，不伪造 active/APPLIED；desired/active/history 均保留已验证的原 canonical bytes，observed 为闭合摘要；同版本异 hash、正式文件/历史损坏、符号链接和权限漂移全部 fail-closed；
- POSIX 目录/文件合同为 `02770/0660`；同目录 temp、file fsync、atomic replace、directory fsync 和注入失败回滚已固化。Windows 本地不声明 Linux directory-fsync、owner/GID 或发布资格；
- Sol 独立执行 §10 冻结集为 `56 passed, 0 skipped`，`python -m compileall -q NODE`、Schema hash、`git diff --check` 和临时目录清理通过；未启动 Docker/collector Java，未执行 Linux PTY、真实串口、资源/稳定性、Windows 或现场验证；
- Sol 在解锁 OPEN03-06 前发现 OPEN03-04 Compose 把配置目录挂为 `:ro`，而原 §10 又把 active/observed/history 写原语留给 Java collector，跨语言进程无法调用 Python 内部原语，合同不可执行。§11 v2 已明确按正式文件分权、精确单 workload `rw` 挂载、共同 record lock、提交顺序、错误码、重启恢复和安全测试；不得以扩大宿主路径或新增自由回调凭据绕过。

门禁影响：门禁 2 的 NODE 配置接收/本地状态机子缺口关闭，但 collector Provider、iot-node 派发和组合 E2E 尚未完成，故门禁 2 继续 `PARTIAL / OPEN`。门禁 3 保持等待 OPEN03-08 联合标记；当前唯一授权为 OPEN03-06，OPEN03-07/08 仍禁止提前实现。

## 21. OPEN03-06 Sol 验收与 OPEN03-07 解锁记录（2026-08-17）

结论：`OPEN03-06 ACCEPTED`；当前唯一授权切换为 `OPEN03-07`，OPEN03-08 仍禁止提前实现。

- collector 配置卷只从同一 safe workload identity 挂载精确单 workload 目录为 `rw`，Agent/collector 按 desired 与 active/observed/history 分权，并以 Python `fcntl.lockf` / Java `FileChannel.lock` 的共同 POSIX record-lock 合同串行化；
- 三份 ConfigSnapshot 1.1 Schema 长度均为 `3853`，SHA-256 均为 `52FCC23AE0DF65BE19C902E604611A4078ABF9E89B13EF91E5DC05D088C7A28A`；本地 Provider 已覆盖严格验证、版本冲突、history/active/observed 原子顺序、失败回滚、重启恢复、符号链接和权限漂移 fail-closed；
- RTU 串口 I/O/编解码只保留一份实现；collector runtime 只依赖本地 `PollingConfigProvider` 并只写 `TelemetryOutboxPort`，普通 center 行为由 bridge adapter 保留；
- Sol 首轮审查否决了“只给协议配置加 `!collector`、但应用入口仍扫描全部 `com.basiclab.iot`”的伪隔离；修正后 center 保留原全包扫描，collector 只白名单导入本地 Provider/runtime 与 SQLite outbox。Sol 次轮又否决了测试强制 `WebApplicationType.NONE`、生产命令未固化非 Web 启动的差异；最终生产入口按 collector CLI 在 bootstrap 前关闭 Nacos，`application-collector.yaml` 固化 `web-application-type=none`，真实 Spring 上下文证明无 DeviceMapper、中心 service/controller/message bus、Redis、Nacos 或 Feign Bean，并在无配置时写 `WAITING_CONFIG`；
- Sol 最终独立执行 §11 冻结 Maven 集为 `27 passed, 0 failed, 0 errors, 0 skipped`，NODE 冻结集为 `37 passed`；`python -m compileall -q NODE`、Schema hash、`git diff --check` 与临时目录清理通过。未启动 Docker、未访问真实串口/中心数据库/Nacos/MQTT，未执行 Linux PTY、资源/稳定性、Windows 资格或现场验证。

门禁影响：门禁 1 的 collector 本地 Provider/Profile 去中心依赖子缺口和门禁 2 的 collector 配置应用子缺口已关闭；门禁 2 仍缺 iot-node 派发/对账及 OPEN03-08 组合 E2E，因此继续 `PARTIAL / OPEN`。Linux owner/GID、跨语言真实锁、directory-fsync、资源/稳定性、Windows 与现场继续 `OPEN-RUNTIME`，不得由本次 Windows 本地合同测试替代。

## 22. OPEN03-07 Sol 验收与 OPEN03-08 细化入口（2026-08-17）

结论：`OPEN03-07 ACCEPTED`；门禁 2 的 iot-node 派发/对账子缺口关闭。当前只允许 Sol 细化 OPEN03-08，Luna 不得依据 §13 粗粒度描述直接实现。

实现证据：iot-node 使用类型化 `CollectorConfigReleaseInternalApi` 拉取 pending/detail 与回报 observed，以 `ComputeNodeMapper` 为节点权威源，只对固定 collector PUT/GET 路径使用 ADR-018 signer；canonical 完成 UTF-8/长度/hash 自校验后只在调用栈和请求 DTO 中短暂存在。Agent client 禁重定向、在完整缓冲前以 `1 MiB + 1 byte` 硬限制并关闭响应流；ACCEPTED/IDEMPOTENT、APPLIED/FAILED、401/403/404/409/5xx、timeout、乱序/异 hash 与 allowlist 脱敏均有确定性测试。退避只保留 release/version/hash/attempt/next time，最大 10000 项；`APPLY_TIMEOUT` 仍只归 iot-device。Spring 条件装配在默认 `enabled=false` 时无 job/轮询，开启时形成 typed Feign→Mapper→signer→service→scheduled job 完整 Bean 图。

Sol 首轮否决了反射式 release 适配、未装配普通 job 和“完整读入后才检查 1 MiB”的实现；修正后正式 Maven 冻结矩阵为 `45/45 PASS`、Failures=0、Errors=0、Skipped=0，独立 reactor compile `BUILD SUCCESS`，`git diff --check` 通过。补测过程曾暴露一个未定义测试常量和四个返回 `data` 子节点的 fixture 构造错误，修正后重新完整执行并通过，未以手工 runner 替代 Maven。

门禁影响：门禁 2 只剩 OPEN03-08 组合 E2E/联合冻结，因此继续 `PARTIAL / OPEN`；门禁 1、2、3、6 均须等 OPEN03-08 统一判定 `CLOSED-LOCAL`。生产派发开关保持关闭，未连接真实 iot-device/Agent，未执行 Docker、串口、Linux PTY、资源/稳定性、Windows 资格或现场验证。

## 23. OPEN03-08 v2 Sol 冻结与 Luna Max 授权记录（2026-08-17）

Sol 已依据《EasyAIoT 项目开发宪法》1.6.0、《平台功能计划》1.5.0、TD-001 1.0.24、TD-002 和 TD-003 重核本地联合边界，并把 §13 从粗粒度描述收敛为可直接执行的 v2 任务单。

- 组合 E2E 采用仓库级串行编排，不给 Java 生产/测试模块增加跨 `*-biz` 依赖；
- Agent 使用 loopback 上的实际 Flask app、实际 HMAC/nonce 与实际文件状态服务，中心 release 和串口仅使用闭合 fake；
- 成功/失败两条链、不可变 fixture/trace、随机临时 key、清理条件与零跳过命令已固定；
- health 四 facet、outbox durable/异常、TD-003 同名端口隔离和 config/outbox 根独立合同已冻结；
- 本包不授权生产开关、DDL、Docker、真实串口、Linux PTY/owner/GID/directory-fsync 资格、压测、Windows 发布资格或现场验证。

决定：`OPEN03-08 v2 FROZEN / GPT-5.6 LUNA MAX AUTHORIZED`。Luna 完成后必须交回 Sol 独立审查；在 Sol 接受前不得把门禁 1、2、3、6 标记为 `CLOSED-LOCAL`。

## 24. OPEN03-08 Sol 首轮停工记录（2026-08-17）

结论：`BLOCKED-CONTRACT / LUNA-STOPPED`，不是实现失败，也不得以调整测试断言绕过。

- TD-003 §13 明确冻结 `TelemetryStore.appendBatch(List<TelemetrySample>) -> WriteBatchResult`，并要求每个输入都有逐条结果；
- 当前 `iot-sink-api` 的真实接口是 `TelemetryStorePort.writeSample(InboxEnvelope) -> WriteResult`，`JdbcTelemetryStore`、`TDengineTelemetryStore` 和 `TelemetryProjectionOrchestrator` 均按逐条接口接线；
- M1-LC-01 明确排除了修改 PostgreSQL/TDengine `TelemetryStore`，因此该漂移不是 LC-01 已批准的合同替代；
- Luna 首版 `CollectorCrossTdContractTest` 使用“`TelemetryStorePort` 不存在 appendBatch”为通过条件，恰好证明冲突存在，不能证明 TD-001/002/003 无冲突；
- 按 §14 停止条件，Sol 已中断 Luna；禁止在 OPEN03-08 内修改 TD-003、`TelemetryStorePort`、两个 Store adapter 或 projector，也禁止继续执行联合关闭命令。

恢复前必须由 Sol 另行冻结二选一方案：A）修订 TD-003，正式接受 projector 逐条调用、Store 单条结果合同，并补批次边界/部分失败语义；B）新建有界前置任务把 API 与两个 adapter 迁移到 TD-003 批量合同，再回到 OPEN03-08。任何方案都必须先做影响分析和回归命令，不能由 Luna 自选。

## 25. OPEN03-08A：TD-003 批量 TelemetryStore 兼容迁移

> 冻结版本：v1（2026-08-17，GPT-5.6 Sol）  
> 执行授权：GPT-5.6 Luna，`max reasoning`；本包通过 Sol 验收前不得恢复 OPEN03-08。

### 25.1 决策与边界

采用 §24 方案 B：实现 TD-003 §13 已定义的批量 Store 主合同，不为迁就现有代码削弱 TD。迁移使用 expand→switch→compat：先新增批量类型和方法，再把 projector 与两个 adapter 切到批量主路径，旧 `writeSample(InboxEnvelope)`/`WriteResult` 只作为一个兼容周期的 `@Deprecated` default bridge 保留。本包不得删除旧方法、不得改变其 STORED/DUPLICATE/FAILED 可观察语义。

允许修改：

1. `DEVICE/iot-sink/iot-sink-api/src/main/java/com/basiclab/iot/sink/telemetry/store/` 下 `TelemetryStorePort`、`WriteResult`，以及新增 `TelemetrySample`、`WriteStatus`、`WriteItemResult`、`WriteBatchResult`；
2. `JdbcTelemetryStore`、`TDengineTelemetryStore`、`TelemetryProjectionOrchestrator`；
3. 上述类的直接 `iot-sink-biz` 单元/合同测试，以及既有 Store 测试从旧调用迁移到批量主调用所需的最小修改；
4. TD-003、本任务单、TD-001、M1-LC-02A 与 M1 SDD 进度记录。

禁止修改 DDL/migration、Envelope/Inbox/ACK/Topic、outbox、查询接口、Spring profile、生产开关和 OPEN03-08 未验收代码；禁止新增依赖、执行 TDengine/生产 DDL、用异常文本作为业务错误码或删除旧 API。若必须改表、改变 adapter 幂等键或扩大到查询/生命周期，立即停止交 Sol。

### 25.2 批量 API 机器合同

`TelemetryStorePort` 主接口固定为：

```java
WriteBatchResult appendBatch(List<TelemetrySample> samples);
```

- `TelemetrySample` 闭合承载当前 `InboxEnvelope` 投影 Store 所需的同一组不可变字段，`canonicalBytes` 构造与访问均 defensive copy；提供从 `InboxEnvelope` 的显式 factory，不反向依赖 biz；
- `WriteStatus` 精确为 `STORED`、`DUPLICATE`、`RETRYABLE_FAILED`、`FINAL_FAILED`；
- `WriteItemResult` 精确包含 `messageId/status/errorCode`：成功状态 errorCode 必须为空，失败状态必须是 `[A-Z][A-Z0-9_]{0,63}` 稳定码；
- `WriteBatchResult` 对 items 做 defensive copy、保持输入顺序且不可变；空输入返回空 items；合法非空输入必须一对一返回同 messageId，禁止整批布尔成功；
- null batch 可 fail-fast `IllegalArgumentException`；null/非法 item 不调用存储，必须返回 `FINAL_FAILED/STORE_SAMPLE_INVALID`。批量硬上限 500；超限不访问存储，并为每个可识别输入返回 `FINAL_FAILED/STORE_BATCH_TOO_LARGE`；
- 兼容 bridge `writeSample(InboxEnvelope)` 只调用一次 `appendBatch`：STORED/DUPLICATE 原样映射，任何失败/缺失/错 identity 统一映射旧 `WriteResult.FAILED`，不得形成第二实现路径。

稳定失败码本包只允许：`STORE_SAMPLE_INVALID`、`STORE_VALUE_INVALID`、`STORE_UNAVAILABLE`、`STORE_STATE_CORRUPT`、`MESSAGE_ID_COLLISION`、`STORE_BATCH_TOO_LARGE`、`STORE_CONTRACT_INVALID`。不得把 SQLState、异常类名/message、主机、表名或凭据写进 result。

### 25.3 adapter 语义

- 两个 adapter 的 `appendBatch` 都必须逐 item 隔离、按输入顺序返回；单 item 失败不得抹掉其他 item 的结果，也不得抛出“整批失败”掩盖逐条事实；
- PostgreSQL 继续使用现有表和唯一约束，不执行 DDL。写前/冲突后按 `(tenantId,messageId)` 核对既有 `contentSha256`：同 hash 为 DUPLICATE，异 hash 为 `FINAL_FAILED/MESSAGE_ID_COLLISION`，多条异 hash 既有事实为 `FINAL_FAILED/STORE_STATE_CORRUPT`；值/tenant 等确定性输入错误为 FINAL，数据库连接/事务/I/O 为 RETRYABLE；
- TDengine 继续使用现有确定性子表与连接配置；同 messageId/时间/hash 重放为 DUPLICATE，明确异 hash 为 FINAL collision，初始化/连接/驱动失败为 RETRYABLE。不得在本包宣称 TDengine 运行资格；无真实 TDengine 时只执行无网络合同/结构测试；
- 日志只记录 messageId 短身份和稳定码，不记录 canonical/value、SQL、异常 message、URL、用户名或密码。

### 25.4 projector 切换

`TelemetryProjectionOrchestrator.projectBatch` 必须把本轮最多 50 个 `ProjectionRow` 映射为同序 `TelemetrySample`，对 Store 只调用一次 `appendBatch`：

- 结果 cardinality、顺序或 messageId 不匹配时，所有尚未闭合的行按 `RETRYABLE_FAILED/STORE_CONTRACT_INVALID` 处理；禁止按位置误更新其他消息；
- STORED/DUPLICATE 才执行原 COMPLETE；RETRYABLE_FAILED 走原有有界退避；FINAL_FAILED 直接进入 PROJECTION_DEAD_LETTER，不消耗无意义重试；
- Store 整体抛异常时，本批所有行按 `STORE_UNAVAILABLE` 可重试，数据库和日志不得保存异常 message；
- 每行状态更新仍独立，某行更新失败不得把其他行伪装完成；不得改 claim/lease/attempt SQL、调度周期或生产开关。

### 25.5 测试与验收命令

新增测试类名固定为：`TelemetryStoreBatchContractTest`、`JdbcTelemetryStoreBatchTest`、`TDengineTelemetryStoreBatchContractTest`、`TelemetryProjectionOrchestratorBatchTest`。至少覆盖：空/单/混合批、顺序/cardinality、defensive copy、兼容 bridge、同 hash duplicate、异 hash collision、确定性 final、依赖 retryable、部分失败、projector 一次调用、四状态映射、错序/缺项/多项/整体异常 fail-closed、日志/结果无异常详情。

冻结命令：

```powershell
mvn -f DEVICE/pom.xml test -pl iot-sink/iot-sink-biz -am `
  "-Dtest=TelemetryStoreBatchContractTest,JdbcTelemetryStoreBatchTest,TDengineTelemetryStoreBatchContractTest,TelemetryProjectionOrchestratorBatchTest,JdbcTelemetryInboxContractTest,TDengineTelemetryStoreContractTest" `
  -Dsurefire.failIfNoSpecifiedTests=false -Dmaven.test.skip=false

mvn -f DEVICE/pom.xml -pl iot-sink/iot-sink-api,iot-sink/iot-sink-biz -am compile -DskipTests

rg -n "writeSample\\(" DEVICE/iot-sink/iot-sink-biz/src/main DEVICE/iot-sink/iot-sink-api/src/main
git diff --check
```

真实 PostgreSQL `JdbcTelemetryInboxContractTest` 必须执行且 `Skipped=0`，测试租户清理为 0；真实 TDengine 在当前本地环境允许保持未执行，但必须明确报告，且新增 `TDengineTelemetryStoreBatchContractTest` 不得依赖网络或 skip。`rg` 结果只允许 deprecated bridge 自身和明确兼容测试，projector/adapter 生产主路径不得再调用 `writeSample`。

### 25.6 停止、交付与恢复条件

需要 DDL、删除旧 API、改变 Inbox/ACK/Store 查询语义、依赖真实 TDengine 才能完成，或无法在不泄露异常详情的情况下分类时立即停止。Luna 不得 commit，必须交付文件、精确 tests/failures/errors/skipped、真实 PG 清理、未执行 TDengine、风险、回滚和 `git diff --check`。

## 26. OPEN03-08A-S1：TDengine HTTP 凭据日志安全收尾

> 冻结版本：v1（2026-08-17，GPT-5.6 Sol）  
> 执行授权：GPT-5.6 Luna，`max reasoning`。

### 26.1 问题与决策

Sol 在带真实 PostgreSQL/TDengine 的完整冻结矩阵中观测到 Apache HttpClient DEBUG 日志输出 HTTP `Authorization` 头。即使生产默认 root=INFO，凭据也不得因临时 DEBUG 而可见。OPEN03-08A 在此收尾通过前不得验收。

### 26.2 唯一授权边界

- 只允许修改 `DEVICE/iot-sink/iot-sink-biz/src/main/resources/application.yaml`，将 `org.apache.http.headers`、`org.apache.http.wire` 以及为确保认证头不被输出所必需的最小 Apache HTTP 实现 logger 固定为 WARN/OFF；
- 只允许新增/compile-scope 下的直接日志配置合同测试，以及必要的 `src/test/resources/logback-test.xml`（或等价现有日志框架配置）使真实 TDengine 测试输出也不包含 header/wire 信息；
- 禁止改动 TDengine/JDBC/Store/projector 业务语义、连接参数、凭据来源、DDL、profile 启用语义、POM 依赖或 OPEN03-08 草稿。

### 26.3 验收

1. 定向合同证明两个敏感 logger 在公共配置中不高于 WARN，且未将任何真实凭据写入新文件；
2. 重跑 `TDengineTelemetryStoreBatchContractTest,TDengineTelemetryStoreContractTest`，当前实际 7 tests、0 failure、0 error、0 skipped；
3. 测试输出不得出现 `Authorization:`、`http.wire`、`http.headers`、连接 URL 中的 user/password query；
4. `git diff --check` 通过。Luna 不得 commit。

## 27. OPEN03-08A / S1 Sol 验收记录（2026-08-17）

结论：`ACCEPTED`。批量 Store 主合同、PostgreSQL/TDengine adapter 和 projector 已完成 expand→switch→compat 迁移；旧 `writeSample` 只保留 deprecated bridge。Sol 独立带真实 PostgreSQL 与本地 TDengine 重跑六类冻结测试，`34/34 PASS`、Failures=0、Errors=0、Skipped=0，28 个 reactor 模块全部 SUCCESS；PostgreSQL 两个测试租户的 Inbox+Sample 残留合计为 0。

S1 将 Apache HTTP header/wire logger 固定关闭、implementation logger 固定 WARN；日志合同 `2/2 PASS`，batch+真实 TDengine `7/7 PASS`，对 `Authorization:`、header/wire logger 和带 user/password query URL 的输出扫描均为 0。`rg writeSample` 生产侧只命中 deprecated bridge，`git diff --check` 退出 0（仅已有换行提示）。现恢复 OPEN03-08 v2 的 Luna Max 实现授权；运行期资格仍保持 OPEN。

OPEN03-08A 的回滚只恢复 Store 接口/adapter/projector、日志安全配置与直接测试，不触碰 OPEN03-01～07 或 OPEN03-08 fixture/health/path 草稿。
