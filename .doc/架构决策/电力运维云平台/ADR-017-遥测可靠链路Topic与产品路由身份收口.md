# ADR-017：遥测可靠链路 Topic 与产品路由身份收口

> 状态：Accepted
> 版本：1.1.1
> 日期：2026-08-24
> 决策所有者：EasyAIoT 架构组
> 实现状态：架构决策已接受；`LC02-09-R1` 已完成并经 Sol 接受为 Verified-Local，生产 broker 激活与后续包仍须独立冻结和授权

## 1. 决策背景

本 ADR 同时依据以下强制基线：

- [EasyAIoT 项目开发宪法 1.6.0](../../开发规范/EasyAIoT项目开发宪法.md)；
- [平台功能计划 1.5.0](../../架构设计/平台功能计划.md)；
- [ADR-003 遥测 ACK 机制](./ADR-003-遥测ACK机制.md)（Accepted）；
- [TD-003 遥测 Inbox、ACK 与时序投影](../../技术设计/电力运维云平台/TD-003-遥测Inbox-ACK与时序投影.md)（In Review）。

M1-LC-01 已把中心 Inbox 的 `INSERTED`、`DUPLICATE`、`COLLISION` 逐消息结果合同实现并完成真实 PostgreSQL 验证。继续接通 ACK 前，代码事实与已接受架构之间出现以下冲突：

| 事项 | 已接受/在审设计 | 当前代码事实 |
|---|---|---|
| 上行 Topic | `/iot/{productIdentification}/{deviceIdentification}/property/upstream/report` | `/telemetry/{siteCode}/{propertyCode}` |
| ACK Topic | 复用 `/iot/{productIdentification}/{deviceIdentification}/property/downstream/report/ack`，禁止新增平行 Topic | `/telemetry/ack/{siteCode}/{deviceIdentification}/{propertyCode}` |
| 产品路由身份 | Topic 必须包含 `productIdentification` | `DeviceDO` 已有该字段，但可靠 Envelope、SQLite Outbox、Claim 结果和中心 Inbox 均未保存 |
| ACK V1 | `schemaVersion/messageId/requestId/status/code/reasonCode/persistedAt` | 当前发布/消费仍使用 `resultCode/errorCode/observedAt` |
| FINAL ACK | 拒绝审计先提交，再允许 FINAL ACK | V008 未建立可靠拒绝审计表；碰撞不能安全发送 FINAL ACK |

因此，直接实现原先口头编号的“LC-02 ACK 接线”会固化一套被 ADR-003 明确禁止的平行 Topic，并使重启后的 ACK 对账缺少产品路由身份。

## 2. 决策

### 2.1 唯一合法的遥测 Topic

可靠遥测链路必须复用现有设备属性 Topic：

```text
上行：/iot/{productIdentification}/{deviceIdentification}/property/upstream/report
ACK ：/iot/{productIdentification}/{deviceIdentification}/property/downstream/report/ack
```

- 不再为 M1 可靠链路新增或保留 `/telemetry/**` 平行 Topic；
- Topic 构造必须复用 `IotDeviceTopicEnum` 或由其派生的单一共享合同，不得在 collector 与 center 分别拼接字符串；
- Topic 中的产品、设备身份必须与已应用配置及 Envelope 的 `deviceIdentification` 一致；不一致消息在进入业务 Inbox 前拒绝。

### 2.2 `productIdentification` 是持久化路由元数据

`productIdentification` 不加入 TD-003 已冻结的遥测 Envelope V1 业务载荷，也不参与当前 Envelope 规范化哈希。它作为可靠传输的路由元数据独立保存：

1. `iot-device` 在发布 collector 配置时根据 API 路径中的产品事实，由服务端把 `productIdentification` 固化进不可变 ConfigSnapshot 1.1；collector 只接受该已应用快照中的值，禁止运行时读取当前设备表补猜。当前仓库尚未实现 TD-001 冻结的 NODE Agent 配置 API和 collector 本地版本快照 Provider，因此必须先完成 M1-LC-02A；
2. SQLite Outbox 与 Claim 结果保存并返回该值，使断线重试和重启恢复不依赖易变内存配置；
3. center 从经过鉴权且通过格式校验的上行 Topic 提取该值，并随 Inbox 记录持久化；
4. ACK 即时发布和重启对账只能使用已持久化的产品、设备路由身份，禁止根据 `siteCode`、`propertyCode` 或展示名称猜测。

### 2.3 迁移采用 expand → backfill → enforce

- SQLite 与 PostgreSQL 首先增加可空 `product_identification`，保证既有数据可读；
- 迁移完成后的新写入必须提供非空产品身份；
- 既有空值记录必须从受信任的已应用设备配置进行受控回填；无法唯一回填的记录保持不可发送/不可 ACK，并进入 `DEGRADED` 可观测状态；
- 禁止使用空串、默认产品、`siteCode` 推断或退回 `/telemetry/**` 作为兼容手段；
- 清零空值并取得画像证据后，才允许在后续迁移中收紧非空约束。

#### 2.3.1 ConfigSnapshot 1.1

现有 ConfigSnapshot 1.0 不含产品标识，不能作为路由事实。新增向后可读的 1.1 合同：

- 根级新增必填 `productIdentification`；同一 workload 只绑定一个产品，设备项不重复该字段；
- `PowerModelBindingApplyService` 从已校验的 API 路径产品标识服务端注入，调用方提交的 `collectorSnapshot` 源对象仍不得自报该字段；
- 新发布单只生成 1.1；1.0 历史发布单保持不可变、可查询，不原地重写；
- collector 启用 canonical Topic 前必须已应用 1.1；仍运行 1.0 时保持发送门禁关闭并报告 `ROUTE_IDENTITY_MISSING`。

#### 2.3.2 历史 SQLite V2 回填清单

历史 V2 outbox 不从当前 `DeviceDO` 回填。中心按不可变发布事实生成签名/校验和受控清单，清单键为：

```text
tenantId + siteCode + configVersion + deviceIdentification
```

值至少包含 `productIdentification`、`workloadId`、`releaseId`、发布载荷 `payloadSha256`。生成依据是：

- `iot_collector_config_release` 的历史 payload 确认该版本包含目标设备；
- `collector_workload_binding_projection` 的不可漂移 workload→productId 身份；
- 产品单一事实把 `productId` 解析为 `productIdentification`。

只有唯一匹配且清单完整性验证通过才可回填。零匹配、多匹配、摘要不符或历史投影缺失均保持不可发送并告警。

### 2.4 精确 ACK 订阅集合

collector 后续在 M1-LC-03 订阅的精确 ACK Topic 集合为：

```text
已应用 ConfigSnapshot 1.1 中的产品/设备路由
UNION
SQLite Outbox 中 PENDING/IN_FLIGHT 的产品/设备路由
```

- 设备从新快照移除后，只要仍存在未终态消息，其 ACK Topic 就必须保留；
- 刷新顺序固定为“订阅新增并确认 SUBACK → 原子替换本地集合 → 取消已无未终态消息的旧订阅”，避免切换窗口漏 ACK；
- 禁止生产 collector 使用 `/iot/+/+/...` 或 `#`；center 服务端共享订阅不等于设备主体 ACL。

### 2.5 实现顺序重新冻结

| 顺序 | 任务 | 边界 |
|---|---|---|
| M1-LC-02A | collector 版本配置应用链 | ConfigSnapshot 1.1、iot-node 派发、NODE 原子落盘、collector 本地 Provider 与 APPLIED 回报；不改遥测 Topic |
| M1-LC-02 | Topic 与产品路由身份收口 | 只消费 LC-02A 已应用的产品路由事实；SQLite 持久化、canonical 上行 Topic、center Topic 校验、Inbox 产品身份；不发布 ACK |
| M1-LC-03 | ACK V1 发布、消费与重启对账 | 仅对 `INSERTED/DUPLICATE` 发布成功 ACK；增加 ACK 发送状态和重试扫描 |
| M1-LC-04 | 拒绝审计与 FINAL/RETRYABLE ACK | 先可靠记录拒绝事实，再发送碰撞等 FINAL ACK；补齐安全审计与告警 |

M1-LC-01 的结果合同保持不变。LC-02A 不得夹带 outbox/Topic 变更，LC-02 不得夹带 ACK，LC-03 不得绕过拒绝审计发送碰撞 FINAL ACK。

### 2.6 center 共享订阅的责任边界

决策所有者于 2026-08-24 接受 EMQX 5.8.7 的真实能力边界：共享订阅进入 authorization 阶段前，`$share/{group}/` 被剥离，file authorizer 只能看到 real topic filter。因此 center 的安全与消费拓扑责任固定拆分如下：

| 责任 | 强制执行者 | 合同 |
|---|---|---|
| center 身份认证 | EMQX | 独占非 superuser 服务主体；匿名、未知主体、错误凭据拒绝 |
| center 数据范围 | EMQX | 只允许 QoS 1 订阅精确 real filter `/iot/+/+/property/upstream/report`；禁止发布和其他 real topic/filter |
| 固定共享组 | `iot-sink` Java | enabled 时只允许 `$share/easyaiot-center-inbox-v1//iot/+/+/property/upstream/report`，在创建网络客户端前逐字节 fail-closed |
| Topic 内身份与租户 | `iot-sink` Java + `iot-device` 权威注册 | Topic product/device、Envelope device/tenant 与权威注册事实全部一致后才允许进入 Inbox |

固定共享组是受控 center 客户端的消费拓扑合同，不作为 EMQX 5.8.7 file authorizer 的数据授权维度。不得声称 broker 能区分固定共享组、普通订阅和其他共享组；真实 broker 合同必须显式证明三者在相同 real filter 下具有相同授权输入，而 Java 合同必须独立证明普通 filter、其他组、`$queue`、broad/legacy filter 均在联网前失败。

该责任重划分不把共享凭据持有者视为可信租户客户端。生产运行期必须使用 center 独占凭据并完成凭据文件消费与轮换、TLS/网络隔离、连接和异常订阅可观测性；凭据泄露者可绕过 Java 使用普通或其他共享组取得 center 原本可读数据的额外副本，属于必须处置的运行期安全事件。若后续威胁模型要求“凭据泄露后仍由 broker 强制固定组”，必须另立 ADR 引入能读取原始 SUBSCRIBE filter 的 hook/plugin/exhook，不得把该能力伪装成 file ACL。

## 3. 安全与多租户约束

- MQTT ACL 必须限制设备主体只能发布自身精确上行 Topic、订阅自身精确 ACK Topic；不得用全局 `#` 作为生产授权范围；
- 当前 Paho 消息回调不携带可信原始发布主体，因此安全证据分层：broker 集成测试证明“主体→精确 Topic”ACL；center 应用证明“Topic 产品/设备→租户设备注册事实→Envelope tenant/device”一致；不得伪造应用层无法取得的主体字段；
- center 的共享订阅过滤器只用于服务端消费扩展，由 Java 在联网前固定并 fail-closed；它不替代 broker 对 center 身份、real Topic、QoS 和动作方向的 ACL，也不替代设备注册事实查询；
- EMQX 5.8.7 file authorizer 不承担共享组名授权；验收不得把普通/其他共享组的 broker 拒绝伪造成安全证据，也不得因此放宽 center 对其他 real Topic、发布动作或 QoS 0/2 的默认拒绝；
- Topic 产品/设备、权威注册事实和载荷租户/设备任一不一致不得写业务 Inbox；
- 日志、指标与审计不得记录 MQTT 密码、令牌或完整敏感载荷。

## 4. 被否决方案

1. **继续使用 `/telemetry/**`**：违反 ADR-003“复用现有 Topic、不新增平行 Topic”的 Accepted 决策。
2. **把产品身份临时塞进 Envelope V1**：改变载荷与哈希合同，扩大兼容面，且路由身份本就应由受信任配置和 Topic 约束。
3. **发布时按设备现查产品**：重启、配置变更或历史重放时可能路由到不同产品，破坏 outbox 的确定性。
4. **为历史空值指定默认产品**：构成跨产品/跨租户误路由风险。
5. **碰撞后直接发送 FINAL ACK**：若审计写入失败会留下不可追责的永久拒绝，违反 TD-003 安全门禁。

## 5. 兼容、回滚与可观测性

- expand 阶段只增加字段和新合同；在完成回填前不删除旧列、不重写历史载荷；
- Topic 切换必须在同一发布窗口协调 collector、broker ACL 与 center，禁止长期双写双订阅；
- 回滚只能回滚应用版本，新增可空字段保留；不得为回滚重新启用 `/telemetry/**`；
- 至少暴露“缺少产品身份的 outbox 数量”“Topic 身份不一致拒绝数”“未完成回填数”三类指标或等价运维证据。

## 6. 接受门禁

本 ADR 转为 Accepted 前必须完成：

- [x] collector 精确 ACK 订阅集合已冻结为“ConfigSnapshot 1.1 已应用路由 ∪ outbox 未终态路由”，刷新顺序见 §2.4；
- [x] SQLite 历史 outbox 只接受中心受控回填清单；不可唯一回填时 DEGRADED 且禁止发送，见 §2.3.2；
- [x] PostgreSQL Inbox 扩展编号冻结为 V009；采用可空 expand、应用回填、后续 enforce，预检/备份/回滚/画像按 ADR-013；
- [x] 安全责任分层已冻结：broker ACL 证明主体边界，center 校验 Topic/注册事实/载荷，覆盖跨租户负向用例；
- [x] M1-LC-02 0.3.0 已补齐接口、迁移、文件白名单、测试矩阵和验收命令候选，并明确 LC-02A 前置依赖。
- [x] 代码事实确认 NODE Agent `/workload/collector/config`、iot-node 配置派发和 collector 本地 `PollingConfigProvider` 尚未实现；已从 LC-02 拆为独立 M1-LC-02A 前置任务。
- [x] ADR-018 Accepted，M1-LC-02A 已完成内部服务/NODE 认证、配置接口、状态机评审与本地故障恢复证据并达到 Verified-Local。
- [x] 决策所有者于 2026-08-14 接受本 ADR；M1-LC-02A 可按已冻结子任务顺序推进。
- [x] 决策所有者于 2026-08-24 接受 center 责任重划分：broker 约束身份、real Topic、QoS 和动作方向，Java 固定共享组并在联网前 fail-closed，见 §2.6。
- [x] M1-LC-02A 完成 Verified-Local 后，M1-LC-02 已从 Review-Ready 转为 Approved / Frozen。

架构决策已接受；M1-LC-02A 已完成 Verified-Local，M1-LC-02 已进入分包实现。当前只冻结 `LC02-09-R1` 责任重划分后的最小修订，正式 broker 激活、`LC02-09-RUNTIME-01` 与 `LC02-10` 仍须独立门禁。

## 7. 版本记录

| 版本 | 日期 | 说明 |
|---|---|---|
| 1.0.0 | 2026-08-14 | 接受 canonical Topic、产品路由元数据、expand→backfill→enforce 与安全分层 |
| 1.1.0 | 2026-08-24 | 基于 EMQX 5.8.7 真实合同，接受 broker 约束 center 身份/real Topic、Java 固定共享组的责任重划分 |
| 1.1.1 | 2026-08-24 | 回填 LC02-09-R1 真实 EMQX 12/12 与 Java 11/11 的 Verified-Local 接受状态，不扩大生产授权 |
