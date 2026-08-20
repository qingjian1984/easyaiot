# M1-LC-02：遥测 Topic 与产品路由身份收口任务单

> 状态：Approved / Frozen（`LC02-01A` / `LC02-01A-R1` / `LC02-04A` COMPLETE / SOL-ACCEPTED；`LC02-04B` FROZEN / AWAITING IMPLEMENTATION AUTHORIZATION）
> 版本：0.7.0
> 日期：2026-08-21
> 架构负责人：GPT-5.6 Sol
> 实现执行者：GPT-5.6 Luna（max reasoning）
> 当前实现授权：无；本轮只授权 Sol 冻结 `LC02-04B`，其实现及 `LC02-04C`～`LC02-10` 均须再次授权

## 1. 任务结论

Sol 已完成接口、历史回填、精确订阅、V009、安全责任和测试边界的技术收敛。[ADR-017](../../架构决策/电力运维云平台/ADR-017-遥测可靠链路Topic与产品路由身份收口.md) 已 Accepted，[M1-LC-02A collector 版本配置应用链](./M1-LC-02A-Collector版本配置应用链任务单.md) 已于 2026-08-20 经 Sol 复核为 `Implemented / Verified-Local`，因此本任务前置门禁关闭并转为 `Approved / Frozen`。

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
- [ADR-017 遥测可靠链路 Topic 与产品路由身份收口](../../架构决策/电力运维云平台/ADR-017-遥测可靠链路Topic与产品路由身份收口.md)（Accepted 1.0.0）；
- [TD-002 SQLite Outbox 与恢复迁移](./TD-002-SQLite-Outbox与恢复迁移.md)（In Review）；
- [TD-003 遥测 Inbox、ACK 与时序投影](./TD-003-遥测Inbox-ACK与时序投影.md)（In Review）；
- [M1-LC-01 Inbox 接收结果合同任务单](./M1-LC-01-Inbox接收结果合同任务单.md)（Implemented / Verified-Local）；
- [M1-LC-02A Collector 版本配置应用链任务单](./M1-LC-02A-Collector版本配置应用链任务单.md)（Implemented / Verified-Local，2026-08-20）。

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

## 4. 冻结接口合同

本节自 0.4.0 起为冻结合同；任何扩大或语义变化必须停止并交回 Sol。

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

### 5.2 PostgreSQL V009 冻结候选

expand 候选：

```sql
ALTER TABLE iot_sink.telemetry_inbox
    ADD COLUMN product_identification VARCHAR(128);

COMMENT ON COLUMN iot_sink.telemetry_inbox.product_identification IS
    '经 MQTT Topic、认证主体与载荷设备身份校验后持久化的产品路由标识；禁止由站点或属性推断';
```

V009 的正式 SQL、回滚 SQL、预检、画像、备份与审批单必须通过 ADR-013 受控迁移流程另行冻结。本任务在该窗口未批准前只能进行临时库/测试容器验证，不得改共享环境。决策所有者已批准 V010 先于 V009；runner 的依赖顺序必须固定为 `V008 → V010 → V009`，不得按数字排序，也不得重写已成功的 V010 history。

### 5.3 精确 ACK Topic 集合的前置数据

LC-02 只提供可查询的路由集合，不启动 ACK 订阅。集合定义为：

```text
ConfigSnapshot 1.1 已应用的产品/设备路由
UNION
SQLite Outbox 中 PENDING/IN_FLIGHT 的产品/设备路由
```

LC-03 必须按“先订阅新增并确认 SUBACK，再替换集合，最后取消无在途消息的旧订阅”刷新。该合同保证设备解绑后在途消息仍能接收 ACK。

## 6. 文件白名单候选

Luna 只能修改下列范围；新增文件必须属于当前获授权包或 V009 受控迁移资产：

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

## 7. 冻结任务顺序

| ID | 工作项 | 验收 |
|---|---|---|
| LC02-00 | 接受 ADR-017、完成 LC-02A 并冻结本任务 | COMPLETE / SOL-ACCEPTED（2026-08-20） |
| LC02-01A | `TelemetryOutboxBatch` 与 SQLite V3 expand→switch 原子包 | COMPLETE / SOL-ACCEPTED（含 R1，2026-08-20）；V2→V3、全新 V3、重启恢复、旧空身份不 claim、类型化 Collision |
| LC02-04 | 受控清单回填历史路由身份 | SPLIT / IN PROGRESS：04A COMPLETE / SOL-ACCEPTED；04B 中心唯一解析已冻结、待实现授权；04C 本地原子写回待 Sol 冻结 |
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

## 9. 验收命令

以下命令需在任务 Frozen 后执行；测试类名可在实现时按本节语义创建，但不得减少场景：

```powershell
mvn -f DEVICE/pom.xml test -pl iot-sink/iot-sink-biz -am `
  -Dtest=TelemetryOutboxBatchContractTest,SqliteOutboxRouteMigrationTest,TelemetryTopicContractTest,CenterMqttInboxTopicValidationTest `
  -DfailIfNoTests=false -Dmaven.test.skip=false

mvn -f DEVICE/pom.xml test -pl iot-sink/iot-sink-biz -am `
  -Dtest=JdbcTelemetryInboxContractTest,JdbcTelemetryInboxFailureContractTest `
  -DfailIfNoTests=false -Dmaven.test.skip=false

mvn -f DEVICE/pom.xml test -pl iot-sink/iot-sink-biz -am `
  -DfailIfNoTests=false -Dmaven.test.skip=false

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
| LC02-DECISION-03 | PostgreSQL 扩展编号 V009，按 ADR-013 单独批准窗口；runner 依赖顺序 V010→V009 | RESOLVED-DESIGN |
| LC02-DECISION-04 | 产品身份纳入 Inbox 碰撞判定；历史 NULL 只允许合法首次补全 | RESOLVED-DESIGN |
| LC02-DECISION-05 | broker ACL 与 center 注册事实校验分层提供安全证据 | RESOLVED-DESIGN |
| LC02-OPEN-06 | M1-LC-02A 完成并提供本地版本快照、原子应用、重启恢复和 APPLIED 证据 | CLOSED（Verified-Local，2026-08-20） |
| LC02-OPEN-07 | 决策所有者接受 ADR-017，并批准本任务转为 Frozen | CLOSED（ADR-017 Accepted；Sol Frozen，2026-08-20） |

Topic 技术决策与前置门禁已收敛，`LC02-01A` / `LC02-01A-R1` 已由 Sol 验收。Sol 已把跨三个信任边界的 `LC02-04` 拆为 04A/04B/04C；当前仅授权 Luna Max 执行 §15 `LC02-04A`，其余包不得提前实现。

## 12. LC02-01A：TelemetryOutboxBatch + SQLite V3 expand→switch

> 冻结版本：v1（2026-08-20，GPT-5.6 Sol）
> 执行授权：GPT-5.6 Luna，`max reasoning`；本节之外无隐含授权。

### 12.1 单一目标与顺序理由

把产品路由身份从已应用 ConfigSnapshot/中心 DeviceDO 带入 outbox 写入边界，并在同一原子包把 SQLite 从 V2 扩展到 V3。原任务的 LC02-01 与 LC02-03 不得分开交付：只切 API 会丢弃产品身份，只升库又会继续产生 NULL 新记录；本包采用 `expand → switch → verify`，但不实现历史回填、canonical Topic、MQTT 入站校验、PostgreSQL V009 或 ACK。

本包完成后生产 MQTT/collector 派发开关仍保持默认关闭；M1-LC-02 未整体验收前不得部署或激活部分链路。

### 12.2 唯一文件边界

允许修改或新增：

- `iot-sink-api` 的 `telemetry/outbox/TelemetryOutboxBatch.java`、`TelemetryOutboxPort.java` 及直接合同测试；
- `iot-sink-biz` 的 `protocol/polling/CollectorTelemetryWriter.java`；
- `iot-sink-biz` 的 `outbox/sqlite/SqliteOutboxMigration.java`、`SqliteTelemetryOutbox.java`、`SqliteOutboxWriter.java`、内部命令对象，以及上述类的直接测试；
- 为编译迁移现有 `TelemetryOutboxPort` fake/stub 所必需的直接测试文件；
- 本任务单和 M1 SDD 续作记录。

禁止修改 `ClaimedEnvelope`、Topic 构造/订阅、MQTT publisher/subscriber、Inbox/Store/查询、NODE/iot-node、ConfigSnapshot、DDL runner、V009/V010、生产开关或 Maven 依赖。若新身份必须进入 claim 才能完成，立即停止，由 Sol 解锁 LC02-05，不得在本包偷跑。

### 12.3 冻结合同

1. `TelemetryOutboxBatch` 为不可变 record：`productIdentification` 必须非 null/非空/非纯空白，Java `String.length()` 为 1～128；原值不得 trim、大小写折叠或 Unicode 归一化。本包尚不构造 Topic，因此不新增字符集规则；`envelopes` 非空、防御性复制且无 null，批内 `deviceIdentification` 必须唯一且非空。
2. `TelemetryOutboxPort` 主写入口切换为 `appendBatch(TelemetryOutboxBatch, Duration)`；旧 `List` 签名不得保留可写兼容入口。`TelemetryStorePort.appendBatch(List<TelemetrySample>)` 不受影响。
3. center overload 从 `DeviceDO.productIdentification` 取事实，collector overload 只从已应用 `CollectorConfigSnapshot.productIdentification` 取事实；不得从 site/property/device 字符串推断或填默认值。
4. SQLite V3 只 additive 新增 `product_identification TEXT` 并设置 `PRAGMA user_version=3`；V2→V3 与全新建库必须幂等，既有 payload/canonical/hash/state/attempt/lease 字节与语义不变。
5. V3 新写入必须持久化非空产品身份；同 messageId 同 bytes 但产品身份不同必须作为 collision 整批回滚，不得返回 duplicate。V2 历史 NULL 保留原状且 claim SQL 必须排除，留给 LC02-04 受控回填。
6. 入队超时、不可用、顺序、cardinality、返回前 COMMIT 和重启恢复保持 TD-002 既有合同；不得把产品身份加入 Envelope canonical bytes/hash。

### 12.4 测试与验收

新增固定测试类 `TelemetryOutboxBatchContractTest`、`SqliteOutboxRouteMigrationTest`，并重跑直接回归：

```powershell
mvn -f DEVICE/pom.xml test -pl iot-sink/iot-sink-biz -am `
  "-Dtest=TelemetryOutboxBatchContractTest,SqliteOutboxRouteMigrationTest,CollectorTelemetryWriterTest,SqliteOutboxAppendBatchTest,SqliteOutboxDurabilityTest,OutboxClaimTest,CollectorCrossTdContractTest" `
  "-Dsurefire.failIfNoSpecifiedTests=false" "-Dmaven.test.skip=false"

mvn -f DEVICE/pom.xml -pl iot-sink/iot-sink-api,iot-sink/iot-sink-biz -am compile -DskipTests

rg -n "appendBatch\(List<TelemetryEnvelope>" DEVICE/iot-sink
git diff --check
```

至少覆盖 null/空/纯空白/超过 128 的产品身份、原值不被 trim/归一化、空/混设备批、防御性复制、center/collector 两事实来源、全新 V3、V2→V3、重复 migrate、历史 NULL 不 claim、新写入非空、同 ID 跨产品 collision 原子回滚、重启保持身份、canonical/hash 不变。全部测试必须 0 failure/error/skipped；`rg` 只允许历史文档，不得命中生产或测试 Java 签名。

### 12.5 停止与交付

需要修改非白名单文件、引入默认产品、改变 Envelope/hash、读取中心数据库补产品、提前生成 `/iot/**` Topic、修改 ACK/Inbox/Store/查询、执行共享库 DDL或无法保证 V2 原字节时立即停止。Luna 不得 commit；交付需列出变更文件、精确测试数、迁移前后 `user_version`、未执行项、风险、回滚和 `git diff --check`。

### 12.6 Sol 首轮复核结论（2026-08-20）

结论：**拒收，暂停实现授权，不能解锁后续包。** Luna Max 已完成白名单内首轮实现；Sol 独立执行冻结测试为 7 类 `28/28`、Failures=0、Errors=0、Skipped=0，28 模块 reactor compile 为 `BUILD SUCCESS`，旧 `appendBatch(List<TelemetryEnvelope>, Duration)` Java 签名 0 命中，`git diff --check` 无空白错误。SQLite 实际迁移路径为 `user_version 2 → 3`，全新 V3 与重复 migrate 测试通过；Docker Client/Server 29.7.2 已可用，但本包不需要扩展到 PostgreSQL/V009。

拒收点：

1. 同 messageId、同 canonical bytes、不同产品身份虽然已整批回滚且未返回 duplicate，但实现返回 `AppendBatchResult.Success([], [])`，`collisionMessageIds()` 仍为空，无法按既有 Port/`AppendBatchResult` 合同表达 `COLLISION`。修复必须修改当前 §12.2 白名单外的 `AppendBatchResult.java`，已触发 §12.5 停止条件；不得把空 Success 误记为 collision。
2. `SqliteOutboxRouteMigrationTest` 已证明历史 envelope/hash/status 保持，但尚未以非默认值逐项证明 `attempts`、`unknown_ack_count`、`next_retry_at_ms`、`in_flight_at_ms`、`ack_deadline_at_ms`、错误字段等 V2 状态/租约在 V3 additive migration 后原值不变。
3. `TelemetryOutboxBatchContractTest` 已证明空白与原字符串保持，但缺少组合 Unicode（NFD/NFC）不归一化的显式断言。

下一步只能由 Sol 先形成 `LC02-01A-R1` 最小重冻结：明确是否将 `AppendBatchResult.java` 及其直接合同测试加入白名单、冻结 `Collision` 结果形态，并补齐上述两类证据；获得决策所有者授权后，方可再次交 Luna Max 修正。当前首轮改动保留但未验收、未提交。

## 13. LC02-01A-R1：碰撞结果与迁移证据最小修订

> 冻结版本：v1（2026-08-20，GPT-5.6 Sol）
> 决策所有者授权：已授权（2026-08-20）
> 实现授权：GPT-5.6 Luna，`max reasoning`；只修复 §12.6 三个拒收点。

### 13.1 唯一目标与文件边界

本修订只把已发生的 outbox collision 表达为类型化失败结果，并补齐 SQLite additive migration 与 Unicode 原值保持证据。允许修改或新增：

- `iot-sink-api/.../telemetry/outbox/AppendBatchResult.java`；
- `iot-sink-biz/.../outbox/sqlite/SqliteOutboxWriter.java`；
- `AppendBatchResultContractTest.java`、`SqliteOutboxRouteMigrationTest.java`、`TelemetryOutboxBatchContractTest.java`；
- 为断言既有 collision 行为所必需的 `SqliteOutboxAppendBatchTest.java`、`CollectorCrossTdContractTest.java`；
- 本任务单和 M1 SDD 续作记录。

除上述文件外无新增授权。尤其禁止修改 `TelemetryEnvelope`、`TelemetryOutboxBatch`、`TelemetryOutboxPort`、`ClaimedEnvelope`、claim/ACK 状态机、Topic/MQTT、Inbox/Store/query、SQLite Schema、NODE/iot-node、ConfigSnapshot、V009/V010、开关或依赖；首轮 LC02-01A 其他文件只允许读取和回归，不得借 R1 重构。

### 13.2 冻结结果合同

1. `AppendBatchResult` sealed interface 增加 `Collision` 实现；`Success` 语义保持不变。
2. `Collision` 接收非 null、非空、无 null/空白元素的 `collisionMessageIds`，构造时防御性复制；`storedMessageIds()` 与 `duplicateMessageIds()` 固定返回不可变空列表，`collisionMessageIds()` 返回不可变副本。
3. writer 遇到同 messageId 不同 canonical hash，或相同 canonical hash 但 `productIdentification` 不同，必须先 rollback 整个逻辑批次，再返回 `Collision`；不得返回 `Success`、不得把碰撞 ID 放入 duplicate。
4. R1 固定只报告按输入 envelope 顺序检测到的首个碰撞 messageId；该规则仅影响诊断结果，不改变整批原子回滚。不得为收集多个碰撞而扩大扫描、事务或 API 语义。
5. `Collision` 返回前数据库中不得残留本批此前已插入的新行；原有行的 envelope/hash/product/state 不得改变。

### 13.3 补强证据

1. `AppendBatchResultContractTest` 固定验证 `Success`/`Collision` 三组列表语义、输入校验和防御性复制。
2. 既有不同 hash collision 与新增同 hash/不同产品 collision 均必须断言结果为 `AppendBatchResult.Collision`、碰撞列表精确为首个 ID、stored/duplicate 为空、整批无新行残留。
3. V2→V3 fixture 使用非默认行：至少覆盖 `id/message_id/envelope/content_sha256/envelope_size/status/delivery_class/attempts/unknown_ack_count/next_retry_at_ms/in_flight_at_ms/ack_deadline_at_ms/acked_at_ms/last_error_code/last_error_detail/created_at_ms/updated_at_ms/config_version`；迁移前保存值，迁移并重复 migrate 后逐字段相等，唯一允许变化是新增 `product_identification=NULL` 与 `user_version 2→3`。
4. Unicode 证据必须使用可区分的 NFD/NFC 产品标识，断言构造后逐 Java code unit 保留 NFD 原值且未被归一化为 NFC；不得修改生产校验规则。

### 13.4 验收命令

```powershell
mvn -f DEVICE/pom.xml test -pl iot-sink/iot-sink-biz -am `
  "-Dtest=AppendBatchResultContractTest,TelemetryOutboxBatchContractTest,SqliteOutboxRouteMigrationTest,CollectorTelemetryWriterTest,SqliteOutboxAppendBatchTest,SqliteOutboxDurabilityTest,OutboxClaimTest,CollectorCrossTdContractTest" `
  "-Dsurefire.failIfNoSpecifiedTests=false" "-Dmaven.test.skip=false"

mvn -f DEVICE/pom.xml -pl iot-sink/iot-sink-api,iot-sink/iot-sink-biz -am compile -DskipTests

rg -n "return new AppendBatchResult.Success\(List.of\(\), List.of\(\)\)" `
  DEVICE/iot-sink/iot-sink-biz/src/main/java/com/basiclab/iot/sink/outbox/sqlite/SqliteOutboxWriter.java
git diff --check
```

第一条 `rg` 必须 0 命中。全部指定测试必须 0 failure/error/skipped。Luna 不得 commit；若正确修复需要越过 §13.1、改变成功结果语义、抛异常替代类型化 Collision、修改 Envelope/hash/Schema/Topic/Claim/ACK，立即停止并交回 Sol。

### 13.5 Sol 最终复核结论（2026-08-20）

结论：**接受，`LC02-01A` 与 `LC02-01A-R1` 转为 COMPLETE / SOL-ACCEPTED。** Luna Max 的最终修订保持在冻结边界内：`AppendBatchResult.Collision` 具备输入校验和防御性复制；writer 对不同 hash 及同 hash/不同产品两类碰撞均先整批 rollback，再返回输入顺序首个碰撞 ID；批次中碰撞前已写入的新行无残留。

Sol 独立执行 clean reactor 冻结集，8 类测试 `31/31`，Failures=0、Errors=0、Skipped=0；`iot-sink-api`、`iot-sink-biz` 及依赖共 28 个 reactor 模块 compile 为 `BUILD SUCCESS`。V2→V3 fixture 已以合法的 PENDING 与 IN_FLIGHT 非默认状态逐字段证明 envelope/hash、attempt、retry、lease、错误字段和时间戳在首次及重复 migrate 后保持，唯一新增值为 `product_identification=NULL`，版本升为 3；NFD/NFC 证据证明产品标识不被生产代码归一化。writer 空 `Success` 碰撞返回 0 命中，旧 List 写签名 Java 0 命中，`git diff --check` 通过。

本包仅涉及本地 Java/SQLite 合同，不需要 PostgreSQL、MQTT、Linux PTY、资源压测、Windows 或现场资格；这些运行期证据不因本次验收而关闭。当前实现授权归零，`LC02-04`～`LC02-10` 保持锁定；下一步由 Sol 单独细化并冻结 `LC02-04` 的受控历史路由身份回填任务单后，才能交 Luna Max 实现与测试。

## 14. LC02-04 受控回填分包与信任边界

Sol 依据 ADR-017 §2.3.2、宪法 §4/§6/§9/§12 和当前代码事实，将原 `LC02-04` 拆为三个顺序包：

| 子包 | 单一职责 | 禁止跨越的边界 |
|---|---|---|
| LC02-04A | collector 对 SQLite V3 中 `product_identification IS NULL` 的历史行生成有界、确定性的只读库存页及 SHA-256 | 不访问中心数据库、不推断产品、不修改 SQLite、不读取或输出 envelope 正文 |
| LC02-04B | center 依据不可变发布单、workload 投影和产品单一事实，对库存键生成唯一解析清单 | 不接触 collector SQLite、不直接写回、不使用当前 DeviceDO 或默认产品兜底 |
| LC02-04C | collector 验证受控清单完整性并按有界事务原子写回，保存断点与 DEGRADED 结果 | 不重新解析中心事实、不改变 envelope/hash/state/lease、不启用 Topic/Claim/ACK |

该拆分不是扩大范围。04A 的只读库存是 04B 唯一允许处理的请求输入；04B 的受控清单是 04C 唯一允许接受的产品事实。零匹配、多匹配、摘要不符和断点恢复分别在 04B/04C 冻结，禁止由 04A 猜测或提前实现。04A 已完成并由 Sol 验收，当前实现授权归零。

## 15. LC02-04A：历史空身份库存只读导出

> 冻结版本：v1（2026-08-20，GPT-5.6 Sol）
> 决策所有者授权：已授权（2026-08-20）
> 实现授权：GPT-5.6 Luna，`max reasoning`；仅限本节。

### 15.1 单一目标与文件白名单

本包只把 SQLite V3 中仍为空的历史路由键分页导出为可被后续 center 解析的 canonical 库存事实。允许修改或新增：

- `iot-sink-api/src/main/java/com/basiclab/iot/sink/telemetry/outbox/backfill/` 下的 `RouteBackfillKey`、`RouteInventoryEntry`、`RouteInventoryPage`、`RouteInventoryArtifact`；
- `iot-sink-biz/src/main/java/com/basiclab/iot/sink/outbox/sqlite/SqliteOutboxRouteInventoryExporter.java`；
- 上述类型的直接测试 `RouteInventoryContractTest`、`SqliteOutboxRouteInventoryExporterTest`；
- 为复用合法 V3 fixture 所必需的测试辅助代码，但不得修改生产 Schema、writer、Port、claim 或既有测试语义；
- 本任务单和 M1 SDD 续作记录。

禁止修改 Maven 依赖、`TelemetryEnvelope`、`TelemetryOutboxPort`、`OutboxCommand`、`SqliteOutboxMigration`、`SqliteOutboxWriter`、`SqliteTelemetryOutbox`、ConfigSnapshot、NODE/iot-node/iot-device、Topic/MQTT/Inbox/Store/query、V009/V010、生产开关或部署配置。需要任一白名单外生产文件时立即停止并交回 Sol。

### 15.2 冻结 API 与值对象合同

API 包固定提供以下不可变 record，字段名与顺序不得自行改变：

```java
public record RouteBackfillKey(
        String tenantId,
        String siteCode,
        long configVersion,
        String deviceIdentification
) {}

public record RouteInventoryEntry(RouteBackfillKey key, long rowCount) {}

public record RouteInventoryPage(
        String schemaVersion,
        String canonicalizationVersion,
        String workloadId,
        List<RouteInventoryEntry> entries,
        RouteBackfillKey nextCursor
) {}

public record RouteInventoryArtifact(
        RouteInventoryPage page,
        byte[] canonicalBytes,
        String contentSha256
) {}
```

1. `schemaVersion` 固定 `1.0`，`canonicalizationVersion` 固定 `jcs-rfc8785-v1`。
2. `tenantId/siteCode/deviceIdentification/workloadId` 必须非 null、非空、非纯空白；`configVersion >= 0`，`rowCount > 0`。原字符串不得 trim、大小写折叠或 Unicode 归一化。
3. `entries` 构造时防御性复制且不得含 null，数量 `0..500`，按 `tenantId`、`siteCode`、`configVersion`、`deviceIdentification` 的 Java `String.compareTo`/数值升序严格递增，不得重复。
4. `nextCursor` 只有底层仍有更多键时才非 null，并必须等于本页最后一个 entry 的 key；空页及末页必须为 null。
5. `canonicalBytes` 构造与访问均防御性复制；其 UTF-8 JSON 必须正好表示 `page`，使用已有 `EnvelopeJcsCanonicalizer`，不含 BOM/空白且不额外规范化 Unicode。`contentSha256` 是该字节数组 SHA-256 的 64 位小写十六进制，构造时必须校验一致。

### 15.3 冻结导出行为

`SqliteOutboxRouteInventoryExporter` 只提供：

```java
RouteInventoryArtifact exportPage(
        Path dbPath,
        String workloadId,
        RouteBackfillKey afterExclusive,
        int limit
);
```

1. `limit` 只能为 `1..500`；`afterExclusive` 为 null 表示第一页。分页顺序必须与 §15.2 一致，查询至多读取 `limit + 1` 个分组以判断是否有下一页。
2. 只统计 `telemetry_outbox.product_identification IS NULL` 的全部状态行，包括 PENDING、IN_FLIGHT、ACKED、DEAD_LETTER；按四元键分组并输出 `COUNT(*)`。非空产品行不得进入库存。
3. 数据库必须已存在、为普通可读文件、`PRAGMA user_version=3` 且含 `product_identification` 列；不得因为路径错误创建空库，不得调用 migration。
4. 连接必须启用 SQLite read-only/query-only 语义并在单一一致性读事务中完成当前页；生产类不得包含 `INSERT`、`UPDATE`、`DELETE`、`ALTER`、`CREATE`、`DROP`、`REPLACE` 或写 PRAGMA。
5. 结果不得包含 outbox 行 ID、messageId、requestId、propertyCode、envelope、hash、错误详情或 ACK/lease 字段；04A 只输出后续唯一解析所需的四元键、行数和 workloadId。
6. 稳定失败码固定为 `ROUTE_INVENTORY_INPUT_INVALID`、`ROUTE_INVENTORY_DB_NOT_FOUND`、`ROUTE_INVENTORY_SCHEMA_UNSUPPORTED`、`ROUTE_INVENTORY_READ_FAILED`；不得吞异常或回退到空库存。
7. 导出前后数据库所有列、行数、`user_version`、WAL/状态语义保持不变。04A 不生成产品、releaseId、payloadSha256，不形成“已回填”或 DEGRADED 结论。

### 15.4 必测场景与验收命令

测试至少覆盖：值对象 null/空白/边界/防御性复制/严格排序/重复键/nextCursor 关系；空库存；四种状态均计入；非空产品排除；同键聚合行数；多页在 `limit=1` 与 `limit=500` 下无遗漏无重复；after-exclusive 边界；NFD/NFC 原值不归一化；canonical bytes 与 hash 确定性；不存在路径不创建文件；非 V3/缺列拒绝；导出前后逐列快照及 `user_version` 完全一致。

```powershell
mvn -f DEVICE/pom.xml test -pl iot-sink/iot-sink-biz -am `
  "-Dtest=RouteInventoryContractTest,SqliteOutboxRouteInventoryExporterTest,SqliteOutboxRouteMigrationTest,OutboxClaimTest" `
  "-Dsurefire.failIfNoSpecifiedTests=false" "-Dmaven.test.skip=false"

mvn -f DEVICE/pom.xml -pl iot-sink/iot-sink-api,iot-sink/iot-sink-biz -am compile -DskipTests

rg -n 'INSERT|UPDATE|DELETE|ALTER|CREATE|DROP|REPLACE' `
  DEVICE/iot-sink/iot-sink-biz/src/main/java/com/basiclab/iot/sink/outbox/sqlite/SqliteOutboxRouteInventoryExporter.java
git diff --check
```

`rg` 必须 0 命中（注释和字符串也不例外），全部指定测试必须 0 failure/error/skipped。Luna 不得 commit；若实现需要写库、运行 migration、读取 envelope、加入产品推断、增加运行开关或进入 04B/04C，必须立即停止并交回 Sol。

### 15.5 Sol 最终复核结论（2026-08-20）

结论：**接受，`LC02-04A` 转为 COMPLETE / SOL-ACCEPTED。** Luna Max 仅在 §15.1 白名单内新增四个不可变库存合同、一个 SQLite 只读导出器及两组直接测试；导出器使用 read-only/query-only 单一读事务，并注册与 Java `String.compareTo` 一致的 UTF-16 SQLite collation，保证补充字符参与跨页排序时仍满足冻结合同。未实现中心解析、产品推断、SQLite 写回、migration、Topic、Claim、ACK、运行开关或 04B/04C 行为。

Sol 独立 clean reactor 冻结集验证 4 类测试 `22/22`，Failures=0、Errors=0、Skipped=0；`iot-sink-api`、`iot-sink-biz` 及依赖共 28 个 reactor 模块精确 compile 为 `BUILD SUCCESS`。证据覆盖 501 个不同键在 `limit=500` 下分为 500+1 且无遗漏无重复、`limit=1`、Java UTF-16 边界、四状态聚合、非空产品排除、NFD/NFC 不归一化、canonical JCS/SHA-256、错误码、全列/类型/值及 `user_version` 导出前后不变。生产导出器写 SQL 关键字扫描 0 命中，`git diff --check` 通过。首次沙箱内精确编译因 Maven 镜像网络权限被拒，授权网络后用同一命令复跑成功，不构成代码缺陷。

本包只需本地 Java/SQLite 证据，不需要 Docker、PostgreSQL、MQTT、Linux PTY、资源压测、Windows 或现场资格，且不关闭这些后续运行期门禁。当前实现授权归零；下一步只能由 Sol 细化并冻结 `LC02-04B` 的中心权威唯一解析合同，经决策所有者再次授权后再交 Luna Max 实现与测试，`LC02-04C` 与 `LC02-05`～`LC02-10` 继续锁定。

## 16. LC02-04B：中心权威唯一解析清单

> 冻结版本：v1（2026-08-21，GPT-5.6 Sol）
> 决策所有者授权：仅授权 Sol 完成本节冻结（2026-08-21）
> 实现授权：无；须在本节冻结后再次授权 GPT-5.6 Luna（max reasoning）。

### 16.1 单一目标、部署档位与非目标

本包只在 `iot-device` 中心控制面消费一个已经通过 §15 校验的 `RouteInventoryArtifact`，在同一个 PostgreSQL `REPEATABLE_READ + readOnly` 事务内，逐键核对不可变发布单、workload 投影和产品单一事实，生成与源库存页摘要绑定的 canonical 回填清单。它适用于 `standard/full` 的一次性受控运维流程；不增加 Controller、Feign、Kafka、MQTT、调度器或后台线程，因此 `mini` 不获得入口、任务或资源占用。

明确不做：不读取或修改 collector SQLite；不修改中心 PostgreSQL；不执行 DDL/runner；不读取 `DeviceDO`/`DeviceMapper`；不按当前设备、siteCode、默认产品或 `/telemetry/**` 推断；不传输清单、不配置 HMAC/凭据、不直接写回、不形成 APPLIED/DEGRADED 状态；不实现 Topic、Claim、Inbox、ACK、Store 或 04C。清单跨边界传输必须由后续受认证通道完成，普通 SHA-256 只证明字节完整性，不替代来源认证。

### 16.2 冻结共享合同

在既有 `iot-sink-api/.../telemetry/outbox/backfill/` 中只新增以下不可变合同；字段名和顺序固定：

```java
public record RouteBackfillManifestEntry(
        RouteBackfillKey key,
        long rowCount,
        String productIdentification,
        String workloadId,
        long releaseId,
        String payloadSha256
) {}

public record RouteBackfillManifest(
        String schemaVersion,
        String canonicalizationVersion,
        String sourceInventorySha256,
        String workloadId,
        List<RouteBackfillManifestEntry> entries,
        RouteBackfillKey inventoryNextCursor
) {}

public record RouteBackfillManifestArtifact(
        RouteBackfillManifest manifest,
        byte[] canonicalBytes,
        String contentSha256
) {}

public record RouteBackfillIssue(RouteBackfillKey key, String code) {}

public sealed interface RouteBackfillResolutionResult {
    record Resolved(RouteBackfillManifestArtifact artifact)
            implements RouteBackfillResolutionResult {}
    record Rejected(String sourceInventorySha256, String workloadId,
                    List<RouteBackfillIssue> issues)
            implements RouteBackfillResolutionResult {}
}
```

1. manifest `schemaVersion` 固定 `1.0`，`canonicalizationVersion` 固定 `jcs-rfc8785-v1`；`sourceInventorySha256` 必须精确等于输入库存 artifact 的 64 位小写 SHA-256，`workloadId` 与 `inventoryNextCursor` 必须逐值复制输入页。
2. manifest entry 必须与源 inventory entry 一一对应、顺序相同，`key/rowCount` 原值不变；`productIdentification/workloadId/releaseId/payloadSha256` 分别来自本节权威核对结果，字符串不 trim、不大小写折叠、不做 Unicode 归一化。
3. `entries/issues` 均防御性复制、无 null；`Resolved` 只能覆盖源页全部 entry，禁止部分清单。任一键失败时结果只能是 `Rejected`，issues 按源 entry 顺序且每个失败键只保留按 §16.4 优先级确定的首个稳定码。`RouteBackfillIssue` 构造器必须拒绝 §16.4 未列出的任意 code。
4. manifest canonical bytes 使用既有 `EnvelopeJcsCanonicalizer`；构造与访问均防御性复制，artifact 构造时必须验证 bytes、对象与 SHA-256 完全一致。不得加入生成时间、数据库行版本或随机值，保证相同事实重复解析得到相同 bytes/hash。
5. 空 inventory 页允许生成空 `Resolved` manifest；其 `inventoryNextCursor` 必须为 null。`Rejected` 不携带 payload、产品内部 ID、节点 ID、模板、错误详情或数据库异常文本。

### 16.3 冻结中心服务与文件白名单

中心入口固定为：

```java
RouteBackfillResolutionResult resolve(RouteInventoryArtifact inventoryArtifact);
```

允许新增或修改：

- §16.2 五个共享合同及直接测试；既有 §15 四个合同只读复用，不得改语义；
- `iot-device-biz/src/main/java/com/basiclab/iot/device/service/collector/backfill/RouteBackfillFactRepository.java`；
- 同目录 `JdbcRouteBackfillFactRepository.java`、`RouteBackfillManifestResolver.java`；
- 直接测试 `RouteBackfillManifestContractTest`、`RouteBackfillManifestResolverTest`、`JdbcRouteBackfillFactRepositoryPostgresIntegrationTest`；
- 为上述 PostgreSQL 合同建立/清理独立 fixture 所必需的测试辅助代码；
- 本任务单和 M1 SDD 续作记录。

`iot-device-biz` 已依赖 `iot-sink-api`，不得修改 Maven 依赖。`CollectorConfigSnapshotContract`、`JcsCanonicalizer`、V003/V004/V007 SQL、product Mapper/Service 只读复用或作为事实参考，不在白名单。禁止修改 Controller、配置开关、部署文件、`DeviceDO`/`DeviceMapper`、发布状态机、表结构、迁移资产、iot-node/NODE、iot-sink biz、SQLite/Topic/Claim/Inbox/ACK/Store/query。需要越界时必须停止并交回 Sol。

### 16.4 唯一解析算法与稳定失败码

resolver 必须先验证 inventory artifact 的对象、canonical bytes、SHA-256、页排序和 workload 一致性；null/非法输入抛出前缀 `ROUTE_BACKFILL_INPUT_INVALID`，源 artifact 完整性不成立抛出 `ROUTE_BACKFILL_INVENTORY_INTEGRITY_FAILED`。随后在单个 PostgreSQL `REPEATABLE_READ + readOnly` 事务中按源 entry 顺序执行以下 fail-closed 核对：

1. `tenantId` 只接受可转换为正 `BIGINT` 的十进制字符串，否则记 `ROUTE_BACKFILL_TENANT_ID_INVALID`；查询发布单必须精确使用 `(tenant_id, workload_id, site_code, config_version)`，且状态只允许 `PUBLISHED/APPLIED/APPLY_TIMEOUT/ROLLED_BACK`。结果不是恰好一条时记 `ROUTE_BACKFILL_RELEASE_NOT_UNIQUE`。
2. 对唯一发布单重新验证 `schema_version` 仅为 `1.0/1.1`、`canonicalization_version=jcs-rfc8785-v1`、`octet_length(payload_canonical)`、stored `payload_sha256`、重新 canonicalize 后 bytes/hash 全部一致；否则记 `ROUTE_BACKFILL_PAYLOAD_INTEGRITY_FAILED`。
3. payload 根级 `tenantId/workloadId/siteCode/configVersion` 必须分别与源 key、输入 workload 及发布单精确一致；否则记 `ROUTE_BACKFILL_PAYLOAD_IDENTITY_MISMATCH`。在全部 `serialBuses[].devices[]` 中，目标 `deviceIdentification` 必须精确出现一次，零次或多次记 `ROUTE_BACKFILL_DEVICE_NOT_UNIQUE`。
4. `collector_workload_binding_projection` 按 `(tenant_id, workload_id)` 必须恰好一条，不因 `ACTIVE/STOPPED/RETIRED` 排除历史身份；否则记 `ROUTE_BACKFILL_PROJECTION_NOT_UNIQUE`。其 `site_code/node_id/product_id` 必须与发布单一致，且投影 `config_version >=` 历史发布单版本；否则记 `ROUTE_BACKFILL_PROJECTION_IDENTITY_MISMATCH`。不得要求投影当前 `release_id` 等于历史 releaseId。
5. `public.product` 必须按 `(tenant_id, id=release.product_id)` 恰好一条；否则记 `ROUTE_BACKFILL_PRODUCT_NOT_UNIQUE`。`product_identification` 必须非空并满足 ConfigSnapshot 1.1 的 1～128 code point 规则，否则记 `ROUTE_BACKFILL_PRODUCT_IDENTIFICATION_INVALID`。若发布 payload 为 1.1，其固化的 `productIdentification` 必须与该产品事实逐 code unit 相等，否则记 `ROUTE_BACKFILL_PRODUCT_IDENTITY_MISMATCH`；1.0 payload 不要求不存在的字段。

稳定 issue code 仅允许 `ROUTE_BACKFILL_TENANT_ID_INVALID`、`ROUTE_BACKFILL_RELEASE_NOT_UNIQUE`、`ROUTE_BACKFILL_PAYLOAD_INTEGRITY_FAILED`、`ROUTE_BACKFILL_PAYLOAD_IDENTITY_MISMATCH`、`ROUTE_BACKFILL_DEVICE_NOT_UNIQUE`、`ROUTE_BACKFILL_PROJECTION_NOT_UNIQUE`、`ROUTE_BACKFILL_PROJECTION_IDENTITY_MISMATCH`、`ROUTE_BACKFILL_PRODUCT_NOT_UNIQUE`、`ROUTE_BACKFILL_PRODUCT_IDENTIFICATION_INVALID`、`ROUTE_BACKFILL_PRODUCT_IDENTITY_MISMATCH`。优先级固定为上述 1→5；同一键只报告最先失败的码。读取连接/SQL/解析的非业务故障抛出前缀 `ROUTE_BACKFILL_READ_FAILED`，manifest 值对象非法或其 bytes/hash 不一致分别抛出 `ROUTE_BACKFILL_MANIFEST_INVALID`、`ROUTE_BACKFILL_MANIFEST_INTEGRITY_FAILED`，均不得转换为空清单。只有所有键均通过才能生成 `Resolved`；解析过程、成功和拒绝结果均不得执行 `INSERT/UPDATE/DELETE/DDL/SELECT ... FOR UPDATE`，不得记录 payload 正文。

### 16.5 fixture 与测试矩阵

纯 Java fake repository 必须覆盖：空页；1.0 与 1.1 唯一成功；相同事实重复解析 bytes/hash 相同；所有失败码；一个键失败导致整页 Rejected 且无部分 artifact；多键 issue 顺序与首错优先级；NFD/NFC 不归一化；源 SHA、manifest SHA、防御性复制和敏感字段排除。

真实 PostgreSQL 合同只使用显式 `LC02_ROUTE_BACKFILL_PG_ENABLED`、`LC02_ROUTE_BACKFILL_PG_URL`、`LC02_ROUTE_BACKFILL_PG_USER`、`LC02_ROUTE_BACKFILL_PG_PASSWORD` 环境变量连接本地、可清理的测试库；启用后任一连接变量缺失必须失败，不得内置 URL、用户或密码默认值，凭据不得写入源码、文档或 Git diff。fixture 使用独立正数 tenant/product/release/projection ID 和 `lc02-04b-*` workload/site/device，覆盖 V003+V004+V007 当前表结构、1.0/1.1 payload、PUBLISHED/APPLIED/ROLLED_BACK 合法状态、DRAFT/VALIDATED/FAILED 排除、跨租户 product 拒绝、projection product/site/node 漂移、摘要/长度/根身份/device 缺失。fixture 必须置于事务回滚或 `try/finally` 精确清理，运行前后按 fixture 标识证明 `product`、`power_product_model_binding`、`power_model_release_outbox`、`iot_collector_config_release`、`collector_workload_binding_projection` 均为 0 残留；不得执行迁移或清理非 fixture 数据。

### 16.6 验收命令

```powershell
mvn -f DEVICE/pom.xml test -pl iot-device/iot-device-biz -am `
  "-Dtest=RouteBackfillManifestContractTest,RouteBackfillManifestResolverTest,CollectorConfigSnapshotContractTest" `
  "-Dsurefire.failIfNoSpecifiedTests=false" "-Dmaven.test.skip=false"

mvn -f DEVICE/pom.xml test -pl iot-sink/iot-sink-biz -am `
  "-Dtest=RouteInventoryContractTest,SqliteOutboxRouteInventoryExporterTest" `
  "-Dsurefire.failIfNoSpecifiedTests=false" "-Dmaven.test.skip=false"

$env:LC02_ROUTE_BACKFILL_PG_ENABLED='true'
mvn -f DEVICE/pom.xml test -pl iot-device/iot-device-biz -am `
  "-Dtest=JdbcRouteBackfillFactRepositoryPostgresIntegrationTest" `
  "-Dsurefire.failIfNoSpecifiedTests=false" "-Dmaven.test.skip=false"

mvn -f DEVICE/pom.xml -pl iot-sink/iot-sink-api,iot-device/iot-device-biz -am compile -DskipTests

rg -n 'INSERT|UPDATE|DELETE|ALTER|CREATE|DROP|REPLACE|FOR UPDATE' `
  DEVICE/iot-device/iot-device-biz/src/main/java/com/basiclab/iot/device/service/collector/backfill
rg -n 'DeviceDO|DeviceMapper|/telemetry/|DEFAULT_PRODUCT' `
  DEVICE/iot-device/iot-device-biz/src/main/java/com/basiclab/iot/device/service/collector/backfill
git diff --check
```

两个 `rg` 均必须 0 命中。三组指定测试必须分别报告 0 failure/error/skipped；PostgreSQL 测试未显式启用时允许 assumption skip，但不得把 skip 计作最终验收通过。实现者不得 commit。若当前数据库缺 V003/V004/V007、不能建立可清理 fixture、需要写库/加 Controller/加配置/复用当前设备事实、需要签名密钥或需要进入 04C，立即停止并交回 Sol。
