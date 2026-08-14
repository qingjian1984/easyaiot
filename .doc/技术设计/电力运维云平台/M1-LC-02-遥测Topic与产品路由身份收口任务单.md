# M1-LC-02：遥测 Topic 与产品路由身份收口任务单

> 状态：Review-Ready / Awaiting ADR Acceptance
> 版本：0.3.0
> 日期：2026-08-13
> 架构负责人：GPT-5.6 Sol
> 实现执行者：ADR-017 Accepted 且本任务转为 Frozen 后，交由 GPT-5.6 Luna（max reasoning）
> 当前实现授权：无

## 1. 任务结论

Sol 已完成接口、历史回填、精确订阅、V009、安全责任和测试边界的技术收敛。本任务仍**没有实现授权**：除 [ADR-017](../../架构决策/电力运维云平台/ADR-017-遥测可靠链路Topic与产品路由身份收口.md) 尚未 Accepted 外，[M1-LC-02A collector 版本配置应用链](./M1-LC-02A-Collector版本配置应用链任务单.md) 必须先实现并验证，才能为本任务提供持久、版本化的产品路由事实。

在门禁关闭后，本任务只负责：

```text
API 路径产品事实 → ConfigSnapshot 1.1.productIdentification
  → collector 已应用配置与批次路由元数据
  → SQLite Outbox 持久化
  → /iot/{product}/{device}/property/upstream/report
  → center Topic 三方校验
  → PostgreSQL Inbox 产品路由身份持久化
```

本任务不实现 ACK 发布/消费、ACK 重试、拒绝审计、时序投影或 Store 变更。

## 2. 强制基线与前置

- [EasyAIoT 项目开发宪法 1.6.0](../../开发规范/EasyAIoT项目开发宪法.md)；
- [平台功能计划 1.5.0](../../架构设计/平台功能计划.md)；
- [ADR-003 遥测 ACK 机制](../../架构决策/电力运维云平台/ADR-003-遥测ACK机制.md)（Accepted）；
- [ADR-017 遥测可靠链路 Topic 与产品路由身份收口](../../架构决策/电力运维云平台/ADR-017-遥测可靠链路Topic与产品路由身份收口.md)（Proposed，当前阻塞）；
- [TD-002 SQLite Outbox 与恢复迁移](./TD-002-SQLite-Outbox与恢复迁移.md)（In Review）；
- [TD-003 遥测 Inbox、ACK 与时序投影](./TD-003-遥测Inbox-ACK与时序投影.md)（In Review）；
- [M1-LC-01 Inbox 接收结果合同任务单](./M1-LC-01-Inbox接收结果合同任务单.md)（Implemented / Verified-Local）。

## 3. 明确不做

- 不装配或改造 `CenterMqttAckPublisher`；
- 不改 `CollectorMqttAckSubscriber` 的 ACK V1 解析；
- 不增加 `ack_sent_at`、`ack_attempts` 或 ACK 扫描器；
- 不发送 `INSERTED`、`DUPLICATE`、`COLLISION` 的任何 ACK；
- 不建立 `telemetry_ingress_rejection`；
- 不修改 `TelemetryEnvelope` V1 字段、规范化字节或 SHA-256 算法；
- 不修改 `TelemetryStore`、PG/TDengine 投影及其 DDL；
- 不用 `/telemetry/**` 双写、影子订阅或兼容回退；
- 不执行生产/共享环境数据库迁移。

## 4. 候选接口合同

本节只用于 ADR 评审；ADR-017 Accepted 后才可把“候选”改为“冻结”。

### 4.1 collector 批次路由合同

本任务只读取 LC-02A 已原子应用的 ConfigSnapshot 1.1。不得在本任务中读取当前 `DeviceDO`、补建 NODE 配置 API、实现发布状态机或自行解析中心数据库。

在 `iot-sink-api` 新增：

```java
public record TelemetryOutboxBatch(
        String productIdentification,
        List<TelemetryEnvelope> envelopes
) {}
```

约束：

- `productIdentification` 必须非空且满足现有产品标识校验；
- `envelopes` 防御性复制，不允许 `null` 元素；
- 同一批次所有 Envelope 均来自同一 `DeviceDO`，且 `deviceIdentification` 一致；
- `productIdentification` 是路由元数据，不进入 Envelope canonical bytes/hash。

将 `TelemetryOutboxPort` 的写入口收口为：

```java
AppendBatchResult appendBatch(TelemetryOutboxBatch batch, Duration enqueueTimeout);
```

仓库内现有 `appendBatch(List<TelemetryEnvelope>, Duration)` 调用点一次性迁移，不保留能写入无产品身份新记录的静默兼容路径。编译门禁用于证明调用点全部迁移。

### 4.2 Claim 合同

`ClaimedEnvelope` 增加非空 `productIdentification`。其 `topic` 必须由：

```java
IotDeviceTopicEnum.PROPERTY_UPSTREAM_REPORT
    .buildTopic(productIdentification, deviceIdentification)
```

生成。不得继续使用 `siteCode/propertyCode` 构造 Topic。

### 4.3 center 入站合同

`InboxEnvelope` 增加 `productIdentification` 路由元数据，但不改变 `canonicalBytes`。

`CenterMqttInboxSubscriber` 在反序列化后、调用 `TelemetryInboxPort.receiveBatch` 前必须校验：

1. Topic 精确匹配 `/iot/{product}/{device}/property/upstream/report`；
2. Topic `device` 等于 Envelope `deviceIdentification`；
3. Topic 产品/设备能唯一解析到与 Envelope `tenantId` 相同的权威设备注册事实；
4. 产品、设备均通过长度和字符集校验。

任一失败不得进入业务 Inbox；失败结果留给 M1-LC-04 的可靠拒绝审计处理。本任务只提供可测试的拒绝分类，不发送 ACK。发布主体到 Topic 的一致性由 EMQX ACL 集成测试证明，因为当前 Paho 回调不提供可信原始发布主体。

### 4.4 Inbox 幂等扩展

同一 `tenant_id + message_id`：

- 历史记录的 `product_identification IS NULL` 时，仅允许用通过 Topic/主体校验的首次重放做一次性路由身份补全；
- 既有非空产品身份与当前 Topic 产品身份不一致时必须返回 `COLLISION`；
- M1-LC-01 的 `INSERTED/DUPLICATE/COLLISION` 结果类型、批次顺序、单消息事务与失败隔离保持不变；
- 产品身份加入碰撞判定后，真实 JDBC 合同测试必须补齐“相同 messageId、相同原六字段、不同产品身份”的负向用例。

## 5. 数据迁移候选

### 5.1 SQLite V3

expand：

```sql
ALTER TABLE telemetry_outbox ADD COLUMN product_identification TEXT;
PRAGMA user_version = 3;
```

约束：

- 新写入必须非空；
- Claim 不得取出空产品身份记录；
- 历史空值只能由中心生成的受控回填清单填充；清单键为 `tenantId + siteCode + configVersion + deviceIdentification`，值含产品、workload、release 和发布 payload 摘要；
- 无法回填的记录保留原字节和状态，进入 DEGRADED 指标，禁止默认值和旧 Topic 回退。

### 5.2 PostgreSQL V009 候选

expand 候选：

```sql
ALTER TABLE iot_sink.telemetry_inbox
    ADD COLUMN product_identification VARCHAR(128);

COMMENT ON COLUMN iot_sink.telemetry_inbox.product_identification IS
    '经 MQTT Topic、认证主体与载荷设备身份校验后持久化的产品路由标识；禁止由站点或属性推断';
```

V009 的正式 SQL、回滚 SQL、预检、画像、备份与审批单必须通过 ADR-013 受控迁移流程另行冻结。本任务在该窗口未批准前只能进行临时库/测试容器验证，不得改共享环境。

### 5.3 精确 ACK Topic 集合的前置数据

LC-02 只提供可查询的路由集合，不启动 ACK 订阅。集合定义为：

```text
ConfigSnapshot 1.1 已应用的产品/设备路由
UNION
SQLite Outbox 中 PENDING/IN_FLIGHT 的产品/设备路由
```

LC-03 必须按“先订阅新增并确认 SUBACK，再替换集合，最后取消无在途消息的旧订阅”刷新。该合同保证设备解绑后在途消息仍能接收 ACK。

## 6. 文件白名单候选

ADR-017 Accepted 后，Luna 只能修改下列范围；新增文件必须属于对应包或 V009 受控迁移资产：

### 生产代码

- `DEVICE/iot-sink/iot-sink-api/src/main/java/com/basiclab/iot/sink/telemetry/outbox/`
- `DEVICE/iot-sink/iot-sink-api/src/main/java/com/basiclab/iot/sink/telemetry/inbox/InboxEnvelope.java`
- `DEVICE/iot-sink/iot-sink-api/src/main/java/com/basiclab/iot/sink/enums/IotDeviceTopicEnum.java`（原则上只读复用；除非评审证明现有构造器有缺陷）
- `DEVICE/iot-sink/iot-sink-biz/src/main/java/com/basiclab/iot/sink/protocol/polling/CollectorTelemetryWriter.java`
- `DEVICE/iot-sink/iot-sink-biz/src/main/java/com/basiclab/iot/sink/protocol/polling/IndustrialDeviceConfig.java`
- `DEVICE/iot-sink/iot-sink-biz/src/main/java/com/basiclab/iot/sink/outbox/sqlite/`
- `DEVICE/iot-sink/iot-sink-biz/src/main/java/com/basiclab/iot/sink/telemetry/inbox/mqtt/CenterMqttInboxSubscriber.java`
- `DEVICE/iot-sink/iot-sink-biz/src/main/java/com/basiclab/iot/sink/telemetry/inbox/mqtt/TelemetryMqttProperties.java`
- `DEVICE/iot-sink/iot-sink-biz/src/main/java/com/basiclab/iot/sink/telemetry/inbox/TelemetryInboxAutoConfiguration.java`
- `DEVICE/iot-sink/iot-sink-biz/src/main/java/com/basiclab/iot/sink/telemetry/inbox/jdbc/JdbcTelemetryInbox.java`
- `DEVICE/iot-sink/iot-sink-biz/src/main/java/com/basiclab/iot/sink/telemetry/inbox/jdbc/TelemetryProjectionOrchestrator.java`（仅机械透传新增路由字段，不改投影语义）
- `DEVICE/iot-sink/iot-sink-biz/src/main/resources/application.yaml`

### 测试与受控迁移资产

- 上述包对应的 `src/test/java` 合同测试；
- `.doc/技术设计/电力运维云平台/assets/td005-migration/` 中经批准的新 V009/U009 资产、runner 接线和落库窗口申请单；
- 本任务单、技术设计索引和续作入口的验证状态更新。

超出白名单立即停止并回到 Sol 重新拆分，不得由实现者自行扩大范围。

## 7. 任务拆分候选

| ID | 工作项 | 验收 |
|---|---|---|
| LC02-00 | 接受 ADR-017、完成 LC-02A 并冻结本任务 | ADR Accepted；LC-02A Verified-Local；本任务 Frozen |
| LC02-01 | 新增 `TelemetryOutboxBatch` 并迁移所有调用点 | 只消费本地已应用快照；reactor compile 无旧签名调用 |
| LC02-03 | SQLite V3 expand 与新写入产品身份 | V2→V3、全新 V3、重启恢复测试通过 |
| LC02-04 | 受控清单回填历史路由身份 | 唯一、零匹配、多匹配、摘要篡改、断点恢复测试通过 |
| LC02-05 | Claim 使用 canonical 上行 Topic并暴露路由集合 | Topic 单测覆盖非法标识、解绑在途消息及旧 Topic 不可达 |
| LC02-06 | center Topic 与权威注册事实校验 | 正向、错误产品、错误设备、畸形 Topic、跨租户负向测试通过 |
| LC02-07 | V009 候选 DDL 与真实 PG 合同 | 受控临时库迁移、回滚、画像和中文 COMMENT 证据齐全 |
| LC02-08 | Inbox 产品身份补全/碰撞合同 | 新增、重复、历史空值补全、跨产品碰撞、并发测试通过 |
| LC02-09 | EMQX 最小 ACL 集成合同 | 非本设备 Topic 发布/订阅拒绝；center 服务账号范围符合设计 |
| LC02-10 | 全模块回归与文档回填 | 第 9 节命令全绿；未实现 ACK/审计/Store 的 diff 证据 |

## 8. 必测场景候选

### 8.1 纯合同与 SQLite

- 非空产品身份随批次写入、Claim、重启保持不变；
- canonical bytes 与 LC-01 前一致，产品身份不改变 SHA-256；
- V2 数据可升级到 V3；空身份旧记录不会被误发；
- 多设备/多产品并发不串 Topic；
- `/telemetry/**` 在可靠链路不可生成、不可订阅；
- 回填仅接受唯一匹配的受信任配置，歧义和缺失均 DEGRADED。
- ConfigSnapshot 1.0 不启用发送；1.1 的产品身份由 LC-02A 服务端注入并纳入 canonical/hash。

### 8.2 MQTT 入站

- canonical Topic + 匹配设备通过；
- Topic 产品错误、设备错误、层级多/少、编码非法均拒绝；
- Topic 产品/设备无法映射到同租户权威设备事实时拒绝；
- EMQX 独立集成测试证明无权主体不能发布/订阅其他设备 Topic；
- 拒绝消息不调用 `receiveBatch`，也不发送 ACK。

### 8.3 真实 JDBC

- V009 expand 后新记录保存产品身份；
- 历史空值记录经合法首次重放补全；
- 同 messageId 且产品不同返回 `COLLISION`；
- 原 M1-LC-01 13 个 JDBC 场景保持通过；
- 数据库不可达、提交失败与并发唯一键竞争仍逐消息隔离。

## 9. 验收命令候选

以下命令需在任务 Frozen 后执行；测试类名可在实现时按本节语义创建，但不得减少场景：

```powershell
mvn -f DEVICE/pom.xml test -pl iot-sink/iot-sink-biz `
  -Dtest=TelemetryOutboxBatchContractTest,SqliteOutboxRouteMigrationTest,TelemetryTopicContractTest,CenterMqttInboxTopicValidationTest `
  -DfailIfNoTests=false -Dmaven.test.skip=false

mvn -f DEVICE/pom.xml test -pl iot-sink/iot-sink-biz `
  -Dtest=JdbcTelemetryInboxContractTest,JdbcTelemetryInboxFailureContractTest `
  -DfailIfNoTests=false -Dmaven.test.skip=false

mvn -f DEVICE/pom.xml test -pl iot-sink/iot-sink-biz `
  -DfailIfNoTests=false -Dmaven.test.skip=false

mvn -f DEVICE/pom.xml -pl iot-sink/iot-sink-biz -am compile -DskipTests

mvn -f DEVICE/pom.xml -pl iot-sink/iot-sink-biz -am compile -DskipTests
```

真实 JDBC 测试必须连接显式提供凭证的本地临时 PostgreSQL/测试容器；不得把密码写入任务单、源码、测试或 Git diff。

## 10. 完成定义

只有同时满足以下条件，M1-LC-02 才可标记 `Implemented / Verified-Local`：

- [ ] ADR-017 已 Accepted，本任务已评审为 Frozen；
- [ ] collector 的产品身份在 SQLite 持久化并生成唯一 canonical 上行 Topic；
- [ ] center 完成 Topic/主体/载荷三方一致校验；
- [ ] Inbox 持久化产品路由身份，跨产品同 messageId 不会被误判为可 ACK 重复；
- [ ] SQLite V2→V3 与 PostgreSQL V009 的 expand/backfill/rollback 证据齐全；
- [ ] 第 9 节纯合同、真实 JDBC、全模块测试和编译全部通过；
- [ ] diff 证明未提前实现 ACK、拒绝审计、投影或 Store 变更；
- [ ] 续作入口把 M1-LC-03 明确为下一任务。

## 11. 决策处置与剩余门禁

| ID | 门禁 | 当前状态 |
|---|---|---|
| LC02-DECISION-01 | 精确 ACK 集合=`已应用 1.1 路由 ∪ 未终态 outbox 路由`，先订后退 | RESOLVED-DESIGN |
| LC02-DECISION-02 | 历史 outbox 使用中心受控清单唯一回填；失败 DEGRADED | RESOLVED-DESIGN |
| LC02-DECISION-03 | PostgreSQL 扩展编号 V009，按 ADR-013 单独批准窗口 | RESOLVED-DESIGN |
| LC02-DECISION-04 | 产品身份纳入 Inbox 碰撞判定；历史 NULL 只允许合法首次补全 | RESOLVED-DESIGN |
| LC02-DECISION-05 | broker ACL 与 center 注册事实校验分层提供安全证据 | RESOLVED-DESIGN |
| LC02-OPEN-06 | M1-LC-02A 完成并提供本地版本快照、原子应用、重启恢复和 APPLIED 证据 | OPEN |
| LC02-OPEN-07 | 决策所有者接受 ADR-017，并批准本任务转为 Frozen | OPEN |

Topic 技术决策已收敛。LC02-OPEN-06/07 关闭前，不得把本任务交给 Luna Max 实现。
