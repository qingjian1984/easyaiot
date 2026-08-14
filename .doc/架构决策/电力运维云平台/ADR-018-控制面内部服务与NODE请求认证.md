# ADR-018：控制面内部服务与 NODE 请求认证

> 状态：Accepted
> 版本：1.0.0
> 日期：2026-08-14
> 决策所有者：EasyAIoT 安全/架构组
> 实现授权：授权执行 M1-LC-02A-0 凭据治理与认证基础；02A-0 验证完成前不得开放 collector 配置内部 API 或 NODE 配置变更 API

## 1. 背景与代码事实

本 ADR 同时依据：

- [EasyAIoT 项目开发宪法 1.6.0](../../开发规范/EasyAIoT项目开发宪法.md)；
- [平台功能计划 1.5.0](../../架构设计/平台功能计划.md)；
- [TD-001 collector 与 NODE 部署契约](../../技术设计/电力运维云平台/TD-001-collector与NODE部署契约.md)；
- [M1-LC-02A Collector 版本配置应用链任务单](../../技术设计/电力运维云平台/M1-LC-02A-Collector版本配置应用链任务单.md)。

代码核对得到以下事实：

1. Java 微服务 Feign 目前只透传用户 Token、`login-user` 与租户 Header，没有可信服务身份签名；
2. `login-user` 可从请求 Header 反序列化，不能作为调用服务身份；
3. `iot-node` 当前安全配置对 `/**` 放行，不能承载发布单详情等高权限内部接口；
4. iot-node→NODE 当前使用单个 `X-Agent-Token`；token 同时存在平台节点记录与 Agent 环境文件，没有密钥版本、请求签名、nonce 防重放或双密钥轮换窗口；
5. 仓库存在被 Git 跟踪的本地 Agent 凭据文件。其值不得在文档、日志或交付说明中复述，必须单独轮换和移出版本控制基线。

M1-LC-02A 会传输可控制站点串口采集行为的不可变配置。仅凭网络位置、Nacos 服务名、用户 Header 或 bearer token 不满足宪法“安全默认开启”。

## 2. 决策：两段认证、密钥域隔离

### 2.1 Java 微服务内部调用

`iot-node` 调用 `iot-device` collector release 内部 API 使用独立服务身份 HMAC，不复用用户 Token、租户 Header或 Agent 凭据。

请求头：

```text
X-EasyAIoT-Service-Id
X-EasyAIoT-Key-Id
X-EasyAIoT-Timestamp
X-EasyAIoT-Nonce
X-EasyAIoT-Body-SHA256
X-EasyAIoT-Signature
```

签名输入固定为 UTF-8：

```text
HTTP_METHOD + "\n"
+ REQUEST_PATH_WITH_SORTED_QUERY + "\n"
+ SERVICE_ID + "\n"
+ KEY_ID + "\n"
+ TIMESTAMP + "\n"
+ NONCE + "\n"
+ BODY_SHA256
```

规则：

- 算法固定 HMAC-SHA256；body hash 为原始请求 bytes 的 SHA-256 小写 hex，空 body 也使用标准空字节摘要；
- 时间使用 Unix epoch seconds，偏差超过 300 秒拒绝；
- nonce 至少 128 bit 随机，验签成功后用 Redis `SET NX EX` 保留 600 秒；Redis 不可用时 fail-closed；
- key 通过环境变量或配置中心的 secret 引用注入，代码、数据库、普通配置、日志和镜像不得保存明文；
- verifier 只接受明确 allowlist 的 `serviceId + keyId + path + method`；签名正确也不自动取得其他接口权限；
- `iot-gateway` 不路由这些内部端点；provider 仍必须在自身过滤器中验签，不能只靠网关隐藏；
- Feign 使用该客户端专属 interceptor，禁止设置为所有 Feign 客户端的全局拦截器。

### 2.2 iot-node 到 NODE Agent

NODE collector 配置变更请求继续遵循 TD-001 的 HMAC-SHA256，但与微服务密钥完全隔离。

- 每个 node 使用独立版本化 `agentSigningKey`，标识为 `nodeId + keyId`；
- 现有 `agentToken` 仅用于 Agent bootstrap/注册兼容，不作为新配置 API 的最终签名密钥；
- iot-node 通过节点密钥 Provider 获取当前 key，业务 Service 不直接读取或打印明文；
- Agent 本地只接受当前 key 和仍在短暂轮换窗口内的 previous key；
- canonical request 沿用 M1-LC-02A §4.4，并额外把 `nodeId/keyId` 纳入签名输入；
- nonce 在 Agent 本地持久存储 600 秒，Agent 重启后仍能拒绝窗口内重放；
- `/health` 可匿名；旧通用工作负载 API 保留既有 token 兼容，但 `/workload/collector/config` 与其状态变更端点必须要求 HMAC，不允许 token-only 降级。

### 2.3 密钥存储与轮换

本 ADR 冻结接口，不在未完成部署事实核对前指定具体 Vault/KMS 产品：

- 控制面服务 key：配置中心 secret 引用或部署环境 secret，文件权限 `0600`，不得通过普通 Nacos 明文配置项下发；
- node key：平台侧必须由可审计的加密 secret Provider 保存；Agent 侧写入安装目录外的专用凭据文件，Linux `0600`，不进入容器 env、进程参数或日志；
- 轮换顺序：分发 next key → Agent/Provider 同时接受 current+next → iot-node 切换签名 keyId → 观察旧 key 无调用 → 撤销旧 key；
- current+next 重叠窗口候选 10 分钟，必须在部署演练后按 P99 传播时间冻结；
- 轮换失败回退到上一 current，但不得回退为 token-only。

若现有部署无法提供加密 secret Provider，M1-LC-02A 保持阻塞；不得把 key 继续明文新增到 `compute_node` 或提交到仓库来绕过门禁。

## 3. 授权与租户

- 服务身份只证明调用者是 `iot-node`，不替代业务租户校验；releaseId 必须与 DTO 中 tenant/node/workload 共同校验；
- pending 列表不得因为服务身份而跨租户无界返回，必须分页、有上限、并按任务租约/分片限制处理；
- observed 回报只允许推进签名请求中 nodeId 对应的发布单；
- 用户 Token、`login-user`、`tenant-id` Header 不参与服务签名信任决策，也不得覆盖请求 DTO 的权威事实。

## 4. 审计、错误与可观测性

稳定错误至少包括：

```text
SERVICE_AUTH_MISSING
SERVICE_AUTH_UNKNOWN_CALLER
SERVICE_AUTH_KEY_UNKNOWN
SERVICE_AUTH_EXPIRED
SERVICE_AUTH_REPLAYED
SERVICE_AUTH_BODY_HASH_MISMATCH
SERVICE_AUTH_SIGNATURE_INVALID
AGENT_SIGNING_KEY_UNAVAILABLE
AGENT_AUTH_REPLAYED
AGENT_AUTH_SIGNATURE_INVALID
```

- 日志只记录 serviceId/nodeId、keyId、requestId、稳定错误码和时间；不得记录签名、完整 nonce、Token、key 或配置 payload；
- 指标按调用方/端点/错误码计数，但低基数化 keyId，不把 releaseId 作为指标 label；
- 连续验签失败触发安全告警和限速，不自动封禁控制面自身健康检查。

## 5. 兼容与回滚

- 新 Header 和 verifier 先部署 provider，再部署 signer；provider 激活前接口保持关闭，不设置 token-only 兼容窗口；
- 服务 signer 回滚时内部 collector release API 自动停止派发，不影响已应用 collector；
- Agent HMAC 失败时保留现有 desired/active，不接受新配置；
- 旧 Agent 不认识 HMAC API时返回能力不支持，iot-node 标记 `AGENT_CONFIG_API_UNSUPPORTED`，不得改走通用 `/workload/deploy files` 注入配置。

## 6. 被否决方案

1. **信任 Nacos 服务名或内网 IP**：不构成可验证身份，无法防伪造与重放。
2. **信任 `login-user`/tenant Header**：当前 Header 是上下文透传，不是服务签名。
3. **继续只用 `X-Agent-Token`**：bearer token 被截获后可重放，且无方法/路径/body 绑定。
4. **把新 key 明文存进 `compute_node`**：扩大数据库泄露半径，违反凭据治理。
5. **把完整 ConfigSnapshot 放进 Kafka 规避内部 API**：扩大敏感配置复制面、积压面和审计复杂度。

## 7. 接受门禁

- [ ] 选定并验证控制面、node 两类加密 secret Provider；
- [ ] 明确 current/next key 元数据的数据模型、轮换 API、权限和审计；
- [ ] 冻结 Redis nonce key 格式、TTL、容量上限和故障策略；
- [ ] 冻结 Agent 本地凭据文件路径、Linux 权限与安装脚本；
- [ ] 从 Git 基线移除本地 Agent 凭据文件，轮换已暴露值，并加入 secret scanning 门禁（外部轮换、bootstrap、register/heartbeat 与 cached/worktree scan 已 PASS；部署后/现场验证仍 OPEN）；
- [ ] 完成跨服务伪造 Header、重放、错租户、错 node、key 轮换中断与 Redis 故障评审；
- [x] 决策所有者于 2026-08-14 将 ADR 状态改为 Accepted。

本 ADR 已接受并授权 M1-LC-02A-0；其余未关闭门禁继续阻塞 M1-LC-02A-1/2 和配置 API 激活。
