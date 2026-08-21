# M1-LC-02：遥测 Topic 与产品路由身份收口任务单

> 状态：Approved / Frozen（`LC02-01A` / `LC02-01A-R1` / `LC02-04A` / `LC02-04B` / `LC02-04C-1` / `LC02-04C-2` / `LC02-05` / `LC02-06` COMPLETE / SOL-ACCEPTED）
> 版本：1.1.2
> 日期：2026-08-22
> 架构负责人：GPT-5.6 Sol
> 实现执行者：GPT-5.6 Luna（max reasoning）
> 当前交付状态：`LC02-06` 已由 GPT-5.6 Luna（max reasoning）交付并经 Sol 独立复核为 COMPLETE / SOL-ACCEPTED；当前实现授权归零，`LC02-07`～`LC02-10` 继续锁定

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
| LC02-04 | 受控清单回填历史路由身份 | COMPLETE / SOL-ACCEPTED：04A、04B、04C-1、04C-2 均已完成（2026-08-21）；运行期资格独立保持 OPEN-RUNTIME |
| LC02-05 | Claim 使用 canonical 上行 Topic并暴露路由集合 | Topic 单测覆盖非法标识、解绑在途消息及旧 Topic 不可达 |
| LC02-06 | center Topic 与权威注册事实校验 | COMPLETE / SOL-ACCEPTED（2026-08-22）：40/40，真实 PostgreSQL Skipped=0，Linux 隔离 clean compile 34/34 |
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
> 决策所有者授权：已授权实现（2026-08-21）
> 实现授权：GPT-5.6 Luna（max reasoning），仅限本节。

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

### 16.7 Sol 最终复核结论（2026-08-21）

结论：**接受，`LC02-04B` 转为 COMPLETE / SOL-ACCEPTED。** 本次复核同时依据《EasyAIoT 项目开发宪法》v1.6.0 与《平台功能计划》v1.5.0。Luna Max 在 §16.3 白名单内完成五个不可变 manifest/result 合同、中心只读事实 repository、单事务 resolver 及三组直接测试；Sol 复核后收紧发布 payload/root/产品的逐值身份一致性、整数 `configVersion`、64 位小写摘要和真实 PostgreSQL fixture。实现未进入 Controller、配置开关、部署、SQLite 写回、Topic、Claim、Inbox、ACK、Store 或 04C。

Sol 在不受 IDE Java Language Server 输出污染的仓库隔离副本中执行最终冻结命令：设备合同测试 `21/21`、iot-sink 回归 `13/13`、真实 PostgreSQL 合同 `5/5`，均为 Failures=0、Errors=0、Skipped=0；精确 compile 的 33 个 reactor 模块全部 `BUILD SUCCESS`。PostgreSQL 矩阵覆盖 1.0/1.1、PUBLISHED/APPLIED/ROLLED_BACK、DRAFT/VALIDATED/FAILED 排除、跨租户产品、projection product/site/node 漂移、摘要、根身份和 device 缺失；非法 canonical 长度由当前表约束拒绝，resolver 的长度首错由 fake repository 合同覆盖。每个 fixture 均在事务内回滚，运行前后 `product`、`power_product_model_binding`、`power_model_release_outbox`、`iot_collector_config_release`、`collector_workload_binding_projection` 按随机标识均为 0 残留。

生产 backfill 包的写 SQL / DDL / `FOR UPDATE` 扫描与 `DeviceDO` / `DeviceMapper` / `/telemetry/` / `DEFAULT_PRODUCT` 扫描均为 0 命中，`git diff --check` 通过。凭据仅在单次 Maven 进程中由本地容器环境注入，未写入源码、文档或 Git diff。当前实现授权归零；下一步只能由 Sol 细化并冻结 `LC02-04C` 的清单来源认证、完整性验证、断点恢复和 SQLite 原子写回合同，经决策所有者再次授权后交 Luna Max 实现。`LC02-05`～`LC02-10` 继续锁定，Linux PTY、资源压测、Windows 发布资格和现场证据继续保持 OPEN。

## 17. LC02-04C：受认证清单的离线原子写回

> 冻结版本：v1（2026-08-21，GPT-5.6 Sol）
> 决策所有者授权：`LC02-04C-1` / `LC02-04C-2` 均已授权并完成
> 实现授权：归零；`LC02-04C-1` / `LC02-04C-2` 均已 COMPLETE / SOL-ACCEPTED

### 17.1 架构结论、顺序与非目标

本节同时依据《EasyAIoT 项目开发宪法》v1.6.0、《平台功能计划》v1.5.0、ADR-017 §2.3.2 和 TD-002 §4/§8/§14。ADR-017 已决定中心生成“签名/校验和受控清单”；本节只把该决策具体化，不新建第二产品事实源。

`LC02-04C` 固定拆为两个不可并行实现的有界包：

| 子包 | 单一职责 | 关闭条件 |
|---|---|---|
| `LC02-04C-1` | 用 Java 17 JCA `Ed25519` 对 §16 manifest 建立域分离的来源认证、时效和绑定合同 | 纯 Java 合同、签名与验签负向矩阵通过，Sol 接受 |
| `LC02-04C-2` | collector 停机后获取 TD-002 同一 OS 排他锁，对已验签的一页 manifest 执行单事务写回和双索引断点 | 真实 SQLite 原子性、锁争用、崩溃重放和逐列不变证据通过，Sol 接受 |

采用非对称签名而不是复用 ADR-018 HMAC：中心只持有私钥，collector 只得到按 `keyId` 选取的公钥；不得把 iot-node 服务 HMAC、node Agent HMAC、用户 Token 或 Agent token 复用为回填凭据。签名后的 artifact 可以经任意受控文件/通道转运，collector 仍须独立验签；普通 SHA-256 仅证明字节完整性，不能代替来源认证。

04C 只适用于 `standard/full` 的一次性运维流程；`mini` 不获得入口、密钥、后台任务或资源占用。明确不做：不新增 Controller/Feign/Kafka/MQTT/NODE API/调度器/后台线程；不选择 Vault/KMS 产品、不提交真实密钥或公钥、不修改部署配置；不读取中心数据库或 `DeviceDO`；不重新解析产品；不修改 Envelope canonical bytes/hash、状态、attempt、retry、lease、ACK 或 gap；不启用 Topic/Claim/ACK，不执行 V009/V010，不在共享/生产环境回填。公钥分发、签名 artifact 转运、权限/轮换和现场演练仍是独立运行期门禁。

### 17.2 `LC02-04C-1` 冻结认证合同

在既有 `iot-sink-api/.../telemetry/outbox/backfill/` 中只新增以下不可变合同；字段名与顺序固定：

```java
public record RouteBackfillAuthorization(
        String schemaVersion,
        String canonicalizationVersion,
        String signatureAlgorithm,
        String signatureContext,
        String keyId,
        String operationId,
        long issuedAtEpochSeconds,
        long expiresAtEpochSeconds,
        String manifestContentSha256,
        String sourceInventorySha256,
        String workloadId
) {}

public record RouteBackfillAuthorizationArtifact(
        RouteBackfillAuthorization authorization,
        byte[] canonicalBytes,
        String contentSha256,
        String signatureBase64
) {}

public record RouteBackfillApplyRequest(
        RouteBackfillManifestArtifact manifestArtifact,
        RouteBackfillAuthorizationArtifact authorizationArtifact
) {}

public sealed interface RouteBackfillVerificationResult {
    record Verified(RouteBackfillApplyRequest request)
            implements RouteBackfillVerificationResult {}
    record Rejected(String operationId, String manifestContentSha256, String code)
            implements RouteBackfillVerificationResult {}
}
```

冻结值与校验规则：

1. `schemaVersion=1.0`、`canonicalizationVersion=jcs-rfc8785-v1`、`signatureAlgorithm=Ed25519`、`signatureContext=easyaiot-route-backfill-authorization-v1`。字符串不 trim、不大小写折叠、不做 Unicode 归一化。
2. `keyId` 为 1～64 个 ASCII `[A-Za-z0-9._-]`；`operationId` 为 `UUID.toString()` 形式的 36 位小写 canonical UUID；两个 epoch 均为正数，`expires > issued` 且授权窗口不超过 86400 秒。
3. 两个 SHA 均为 64 位小写十六进制。`manifestContentSha256/sourceInventorySha256/workloadId` 必须分别与 request 中 manifest artifact 的 `contentSha256`、manifest 的 `sourceInventorySha256/workloadId` 逐值相等；manifest 对象、canonical bytes 与 hash 必须按 §16.2 再验证，不能只信 DTO 字段。
4. authorization canonical bytes 使用既有 `EnvelopeJcsCanonicalizer`；构造与访问防御性复制，`contentSha256` 必须等于 canonical bytes 的 SHA-256。签名输入精确为 UTF-8 `EASYAIOT-ROUTE-BACKFILL-AUTHORIZATION-V1\n` 后直接拼接 authorization canonical bytes，不加入平台默认编码、换行转换或 Base64 包装。
5. `signatureBase64` 使用 RFC 4648 标准 Base64（含规范 padding、无空白），解码后必须恰为 64 bytes；签名和验签只允许 `Signature.getInstance("Ed25519")`，不得新增密码学 Maven 依赖、不得回退 RSA/ECDSA/HMAC/明文摘要。
6. verifier 使用注入的 `RouteBackfillVerificationKeyProvider` 按 `keyId` 获取公钥；未知 key 必须失败关闭，不允许默认 key。验签时 `issuedAt` 最多允许比注入时钟未来 300 秒，`expiresAt <= now` 即过期。测试必须注入固定 `Clock`，生产代码不得从 manifest 取“当前时间”。
7. 稳定拒绝码仅允许 `ROUTE_BACKFILL_MANIFEST_INTEGRITY_FAILED`、`ROUTE_BACKFILL_AUTHORIZATION_INTEGRITY_FAILED`、`ROUTE_BACKFILL_AUTHORIZATION_BINDING_MISMATCH`、`ROUTE_BACKFILL_TARGET_WORKLOAD_MISMATCH`、`ROUTE_BACKFILL_AUTHORIZATION_KEY_UNKNOWN`、`ROUTE_BACKFILL_AUTHORIZATION_NOT_YET_VALID`、`ROUTE_BACKFILL_AUTHORIZATION_EXPIRED`、`ROUTE_BACKFILL_AUTHORIZATION_SIGNATURE_INVALID`。拒绝不得写 SQLite，不得返回异常正文、签名、公钥或 canonical payload。

中心 signer 固定为纯服务对象：

```java
RouteBackfillAuthorizationArtifact authorize(
        RouteBackfillManifestArtifact manifestArtifact,
        String keyId,
        String operationId,
        Instant issuedAt,
        Instant expiresAt,
        PrivateKey privateKey
);
```

signer 必须先完整复核 manifest artifact，再生成并签名 authorization；私钥只由调用方注入，类不得从环境、文件、数据库或配置中心自行取 key，不得缓存、序列化或记录私钥。collector verifier 固定消费 `RouteBackfillApplyRequest + expectedWorkloadId + Clock`；签名正确也不能绕过 workload 绑定。

`LC02-04C-1` 文件白名单：

- 上述四个共享合同及直接测试；
- `iot-device-biz/.../service/collector/backfill/RouteBackfillAuthorizationSigner.java` 及直接测试；
- `iot-sink-biz/.../outbox/sqlite/RouteBackfillVerificationKeyProvider.java`、`RouteBackfillAuthorizationVerifier.java` 及直接测试；
- 本任务单和 M1 SDD 续作记录。

禁止修改 Maven 依赖、§15/§16 既有合同语义、数据库、SQLite migration/writer/port/lock、Controller、配置、部署、iot-node/NODE、Topic/Claim/Inbox/ACK/Store。需要任一白名单外生产文件时立即停止并交回 Sol。

### 17.3 `LC02-04C-1` 必测矩阵与验收命令

测试使用运行期生成的临时 Ed25519 key pair，不落盘真实 key。至少覆盖：同一输入签名/验签成功；manifest/authorization canonical bytes 与 SHA；防御性复制；错 manifest hash、错 inventory hash、错 workload、错 context/algorithm/keyId；未知 key、错误公钥、签名字节篡改、非 canonical Base64、非 64-byte 签名；未生效、到期、最大 86400 秒边界；NFD/NFC 不归一化；拒绝结果不含 payload/签名/密钥；签名生产路径不出现 HMAC/Agent token/用户 Token。

```powershell
mvn -f DEVICE/pom.xml test -pl iot-device/iot-device-biz -am `
  "-Dtest=RouteBackfillAuthorizationContractTest,RouteBackfillAuthorizationSignerTest,RouteBackfillManifestContractTest" `
  "-Dsurefire.failIfNoSpecifiedTests=false" "-Dmaven.test.skip=false"

mvn -f DEVICE/pom.xml test -pl iot-sink/iot-sink-biz -am `
  "-Dtest=RouteBackfillAuthorizationContractTest,RouteBackfillAuthorizationVerifierTest,RouteInventoryContractTest" `
  "-Dsurefire.failIfNoSpecifiedTests=false" "-Dmaven.test.skip=false"

mvn -f DEVICE/pom.xml -pl iot-sink/iot-sink-api,iot-sink/iot-sink-biz,iot-device/iot-device-biz -am compile -DskipTests

rg -n 'Hmac|HMAC|Agent.?Token|X-Agent-Token|login-user|tenant-id' `
  DEVICE/iot-device/iot-device-biz/src/main/java/com/basiclab/iot/device/service/collector/backfill/RouteBackfillAuthorizationSigner.java `
  DEVICE/iot-sink/iot-sink-biz/src/main/java/com/basiclab/iot/sink/outbox/sqlite/RouteBackfillAuthorizationVerifier.java
git diff --check
```

`rg` 必须 0 命中，全部指定测试必须 0 failure/error/skipped。实现者不得 commit。04C-1 未经 Sol 验收前不得进入 04C-2。

### 17.4 `LC02-04C-2` 冻结离线写回合同

04C-2 只新增 collector-owned 离线服务：

```java
RouteBackfillApplyResult apply(
        Path dbPath,
        String expectedWorkloadId,
        RouteBackfillApplyRequest request
);
```

`RouteBackfillApplyResult` 是共享 sealed interface，固定为 `Applied(operationId, manifestContentSha256, updatedRowCount, nextInventoryCursor)`、`AlreadyApplied(...)`、`Rejected(operationId, manifestContentSha256, code)`、`Degraded(operationId, manifestContentSha256, code)` 四种结果；所有 List/record/游标防御性复制。`Rejected` 精确承接 §17.2 验证失败且不得打开数据库；`Degraded` 只表示来源已认证但本地身份/行数不满足写回前置。基础设施故障抛稳定前缀 `ROUTE_BACKFILL_APPLY_FAILED`，不得伪装为 `Applied`。

执行顺序冻结如下：

1. 先调用 §17.2 verifier；只有 `Verified` 才解析 `dbPath`。数据库必须已存在、是普通文件且位于真实父目录；固定锁文件为同目录 `collector-outbox.lock`，不得由请求指定、不得跟随符号链接跳出父目录。
2. 离线 applier 必须通过现有 `OutboxFileLock` 获取该锁；锁已持有时稳定失败 `OUTBOX_ALREADY_OWNED`，绝不等待、绕过或改用 SQLite busy timeout 猜所有权。为使锁合同真实成立，`SqliteTelemetryOutbox` 生产启动必须先持有同一锁，再执行 migration/启动 writer，并在 writer 确认退出后最后释放；`SqliteOutboxAutoConfiguration` 不得在锁外先迁移。
3. 获取锁后只接受 `PRAGMA user_version=3`、存在 `outbox_meta` 与 `telemetry_outbox.product_identification`、`PRAGMA quick_check=ok` 的数据库；离线 applier 不调用 migration、不创建空库、不改 `user_version`。
4. 一个 §16 manifest 页最多 500 个 key，全部在一个 `BEGIN IMMEDIATE` 事务中预检并写回，不跨网络、不 sleep。每个 entry 精确按 `(tenant_id, site_code, config_version, device_identification)` 查询：已有非空产品只能为空集合或全部等于 manifest 产品，否则 `ROUTE_BACKFILL_LOCAL_IDENTITY_CONFLICT`；空产品行数必须精确等于 `rowCount`，否则 `ROUTE_BACKFILL_ROW_COUNT_MISMATCH`。
5. 预检全部通过后，每个 entry 只允许执行 `SET product_identification=?`，WHERE 必须包含完整四元键和 `product_identification IS NULL`；每次 affected rows 必须等于 `rowCount`。严禁改变 `envelope/content_sha256/envelope_size/status/delivery_class/attempts/unknown_ack_count/retry/lease/ack/error/created/updated/config_version` 或任何其他列。
6. 同一事务写入 APPLIED 双索引断点：`route_backfill.v1.operation.<operationId>` 与 `route_backfill.v1.manifest.<manifestContentSha256>`。value 使用 JCS canonical JSON，字段固定为 `schemaVersion/operationId/manifestContentSha256/sourceInventorySha256/workloadId/keyId/status/appliedRowCount/nextInventoryCursor/updatedAtEpochSeconds`，不保存签名、authorization bytes、产品清单或 envelope。两行与全部产品更新必须同 commit/rollback。
7. 重跑同 manifest 或同 operation 且 APPLIED 绑定一致，返回 `AlreadyApplied` 且数据库逐字节业务内容不再变化；同 operation 绑定不同 manifest 返回 `Rejected(..., ROUTE_BACKFILL_OPERATION_COLLISION)` 且不写库。已有两个断点互相矛盾时返回 `Degraded(..., ROUTE_BACKFILL_CHECKPOINT_CONFLICT)`，不得覆盖既有证据。
8. 已验签但本地 identity/rowCount 不符时先 rollback 全部产品更新，再用独立短事务把同一 operation/manifest 双索引写为 `DEGRADED`，`appliedRowCount=0`、`nextInventoryCursor=null`、code 固定为上述 local identity 或 row count 码；重试同一绑定允许将 DEGRADED 原子推进为 APPLIED。若 DEGRADED 断点自身也无法提交，抛 `ROUTE_BACKFILL_APPLY_FAILED`。
9. 事务提交前 JVM/SQL 失败必须留下“产品列与 APPLIED 断点均未提交”；提交后重跑必须由断点返回 `AlreadyApplied`。每页 APPLIED 的 `nextInventoryCursor` 是多页恢复的唯一前进证据；DEGRADED/Rejected 不允许前进。

`LC02-04C-2` 稳定本地码只允许 `ROUTE_BACKFILL_OPERATION_COLLISION`、`ROUTE_BACKFILL_CHECKPOINT_CONFLICT`、`ROUTE_BACKFILL_LOCAL_IDENTITY_CONFLICT`、`ROUTE_BACKFILL_ROW_COUNT_MISMATCH`。Schema、quick-check、路径、锁、SQL/I/O 故障使用 `ROUTE_BACKFILL_APPLY_FAILED` 或 `OUTBOX_ALREADY_OWNED` 异常前缀，不写虚假 DEGRADED 断点。

04C-2 文件白名单：

- 共享 `RouteBackfillApplyResult` 及直接测试；
- `iot-sink-biz/.../outbox/sqlite/SqliteOutboxRouteBackfillApplier.java` 及直接测试；
- 既有 `OutboxFileLock.java`、`SqliteTelemetryOutbox.java`、`SqliteOutboxAutoConfiguration.java` 及锁/启动直接测试；
- 为临时 SQLite V3 fixture、故障触发器和逐列快照所必需的测试辅助代码；
- 本任务单和 M1 SDD 续作记录。

禁止修改 `TelemetryOutboxPort`、`OutboxCommand`、`SqliteOutboxWriter`、Schema/user_version、Envelope、Topic/Claim/dispatcher/ACK/gap、ConfigSnapshot、iot-device/iot-node/NODE、配置开关、部署文件或 Maven 依赖。需要在线 writer 命令、后台任务、数据库新表/列或白名单外生产文件时立即停止并交回 Sol。

### 17.5 `LC02-04C-2` 必测矩阵与验收命令

测试至少覆盖：运行 outbox 与离线 applier 争同一锁；同 JVM 重叠锁异常归一化；writer 关闭后锁可重获；锁先于 migration；不存在/目录/符号链接/非 V3/缺表列/quick-check 失败；空页；1/500 键；四状态行均写回；已有相同产品允许、不同产品拒绝；少行/多行；NFD/NFC 精确匹配；签名/绑定失败零 DB 访问；每类 DEGRADED 双断点；同 operation 碰撞；同 manifest/operation 幂等；DEGRADED 修复后推进；测试触发器在第 N 次 update 失败时全部产品与 APPLIED 断点 rollback；成功前后全列逐值对比，唯一允许变化为目标行 `product_identification` 和两条 `outbox_meta`。

```powershell
mvn -f DEVICE/pom.xml test -pl iot-sink/iot-sink-biz -am `
  "-Dtest=RouteBackfillAuthorizationContractTest,RouteBackfillAuthorizationVerifierTest,RouteBackfillApplyResultContractTest,SqliteOutboxRouteBackfillApplierTest,OutboxFileLockTest,SqliteOutboxDurabilityTest,SqliteOutboxRouteMigrationTest,OutboxClaimTest" `
  "-Dsurefire.failIfNoSpecifiedTests=false" "-Dmaven.test.skip=false"

mvn -f DEVICE/pom.xml -pl iot-sink/iot-sink-api,iot-sink/iot-sink-biz -am compile -DskipTests

rg -n 'TelemetryOutboxPort|OutboxCommand|SqliteOutboxWriter' `
  DEVICE/iot-sink/iot-sink-biz/src/main/java/com/basiclab/iot/sink/outbox/sqlite/SqliteOutboxRouteBackfillApplier.java
rg -n 'SET (envelope|content_sha256|envelope_size|status|delivery_class|attempts|unknown_ack_count|next_retry_at_ms|in_flight_at_ms|ack_deadline_at_ms|acked_at_ms|last_error_code|last_error_detail|created_at_ms|updated_at_ms|config_version)' `
  DEVICE/iot-sink/iot-sink-biz/src/main/java/com/basiclab/iot/sink/outbox/sqlite/SqliteOutboxRouteBackfillApplier.java
git diff --check
```

两个 `rg` 均必须 0 命中；生产 applier 对 `telemetry_outbox` 只能更新目标产品列。全部指定测试必须 0 failure/error/skipped，且不需要 Docker、PostgreSQL、MQTT、Linux PTY、资源压测、Windows 发布资格或现场环境。实现者不得 commit。

### 17.6 授权与后续门禁

本节现已 `FROZEN`。决策所有者于 2026-08-21 独立授权的 `LC02-04C-2` 已完成实现、R1～R4 收敛和 Linux Docker 符号链接资格复验，并由 Sol 最终接受；当前实现授权归零。不得扩展到 `LC02-05`～`LC02-10` 或任何运行期资格验证。

04C-1/2 的本地通过只能形成 `Verified-Local`。公钥可信分发、私钥 Provider/轮换、签名 artifact 实际转运、Linux `fcntl` 锁互操作、文件 owner/mode、运行停机窗口、恢复演练和现场告警仍保持 `OPEN-RUNTIME`；这些证据未关闭前，不得宣布历史回填可在生产执行。`LC02-05`～`LC02-10` 继续锁定。

### 17.7 `LC02-04C-1` Sol 最终复核结论（2026-08-21）

结论：**接受，`LC02-04C-1` 转为 COMPLETE / SOL-ACCEPTED。** 本次复核同时依据《EasyAIoT 项目开发宪法》v1.6.0 与《平台功能计划》v1.5.0。Luna Max 严格在 §17.2 白名单内完成四个共享 authorization/request/result 合同、中心 Ed25519 signer、collector 公钥 Provider/verifier 及四组直接测试；未进入 SQLite migration/writer/port/lock、Controller、配置、部署、iot-node/NODE、Topic、Claim、Inbox、ACK、Store 或 04C-2。

Sol 首轮代码审查发现：结构非法 authorization 的未受信任 `operationId` 会被原样放入 `Rejected`，可借此回显敏感/超长文本；同时 verifier 缺少错误 algorithm/keyId/operationId 与精确时效边界的直接证据。Luna Max 按最小 R1 收口：非法 operationId 统一返回 null，`Rejected` 构造器只接受 null/canonical UUID 与 null/64 位小写摘要，并补齐恶意文本不回显、错误 algorithm/keyId/UUID、窗口 `>86400`、未来 `300/301` 秒和精确 86400 秒边界。未改变八个稳定码、签名输入或文件边界。

Sol 独立执行两组 clean reactor：device 侧 `RouteBackfillAuthorizationContractTest/RouteBackfillAuthorizationSignerTest/RouteBackfillManifestContractTest` 为 `10/10`，覆盖 33 个 reactor 模块；sink 侧 `RouteBackfillAuthorizationContractTest/RouteBackfillAuthorizationVerifierTest/RouteInventoryContractTest` 为 `14/14`，覆盖 28 个 reactor 模块；两组均 Failures=0、Errors=0、Skipped=0 且 `BUILD SUCCESS`。三目标组合 compile 覆盖 34 个 reactor 模块并 `BUILD SUCCESS`。HMAC/Agent Token/用户租户 Header 禁用项扫描与 SQLite/Port/writer/lock/Controller/Feign/Kafka/MQTT 边界扫描均 0 命中，`git diff --check` 通过。首次沙箱内 clean reactor 因 Maven 依赖元数据网络权限被拒，授权网络后以同一命令重跑成功，不构成代码缺陷。

本包只形成本地 Java 密码学合同证据，不需要 Docker、PostgreSQL、MQTT、Linux PTY、资源压测、Windows 或现场环境，也不关闭公私钥分发、轮换、artifact 转运和现场安全门禁。当前实现授权归零；下一步须决策所有者明确授权 `LC02-04C-2` 后，才能交 GPT-5.6 Luna（max reasoning）执行 §17.4～§17.5。`LC02-05`～`LC02-10` 继续锁定。

### 17.8 `LC02-04C-2` 独立实现授权（2026-08-21）

决策所有者已独立授权下一步。Sol 重读《EasyAIoT 项目开发宪法》v1.6.0 与《平台功能计划》v1.5.0，并复核 §17.4～§17.5：离线停机写回、同一 OS 锁、SQLite V3 单事务、双索引断点、稳定结果/错误码、文件白名单和本地验收矩阵均无双基线冲突。当前唯一授权 GPT-5.6 Luna（max reasoning）实现并测试 `LC02-04C-2`；实现者不得 commit。

授权不包含 `LC02-05`～`LC02-10`，不包含 Schema/user_version、在线 writer 命令、后台任务、配置/部署、iot-device/iot-node/NODE，也不执行 Docker、PostgreSQL、MQTT、Linux PTY/锁互操作、资源压测、Windows 发布资格或现场验证。出现白名单外生产文件、数据库新表/列、在线写回需求或冻结合同不可实现时，Luna 必须立即停止并交回 Sol。

### 17.9 `LC02-04C-2` Sol R4 复核结论（2026-08-21，历史检查点；已由 §17.10 关闭）

结论：**实现已完成，但暂不转 `COMPLETE / SOL-ACCEPTED`。** 本轮继续同时依据《EasyAIoT 项目开发宪法》v1.6.0 与《平台功能计划》v1.5.0。Luna Max 在 §17.4 白名单内完成共享 apply result、离线 SQLite applier、运行 outbox 同锁生命周期以及直接测试；Sol 首轮审查拒绝了过宽结果码、重叠锁 channel 泄漏、checkpoint 非 canonical/时间戳矛盾仍可能被接受、同 operation collision 判定优先级错误和冻结矩阵覆盖不足，随后经 R1～R4 收敛。

当前代码证据覆盖：验签失败零 DB 访问；固定同目录 `collector-outbox.lock`；同 JVM/运行 outbox 锁争用与关闭重获；锁先于 migration；既存普通 V3、Schema 与 quick-check 失败关闭；空页、1/500 key、四状态、同产品/冲突产品、少/多行、NFD/NFC；单事务只更新产品列；operation/manifest JCS 双断点；DEGRADED 双断点与修复推进；operation collision、checkpoint conflict、AlreadyApplied；第 N 次 update 故障全回滚和逐列不变。28 模块 compile 为 `BUILD SUCCESS`，两个生产边界 `rg` 均 0 命中，`git diff --check` 无空白错误。

Sol 独立执行 §17.5 完整八类冻结集共 59 项：Failures=0、Skipped=0，其中 57 项通过；数据库符号链接与锁文件符号链接两项在 JUnit fixture 调用 `Files.createSymbolicLink` 时均因当前 Windows 主机缺少创建符号链接权限而报 `FileSystemException`，Errors=2，尚未进入生产拒绝断言。相同两项在受控沙箱外重跑仍为相同主机权限错误，证明不是 Codex 沙箱限制。测试未 skip、未吞异常并已保留；不得把这两个环境错误表述为代码通过。

因此在该历史检查点实现授权归零，`LC02-04C-2` 保持 `IMPLEMENTED / SOL-ACCEPTANCE BLOCKED-LOCAL-ENV`，并要求在具备符号链接资格的环境重跑 §17.5 后才能接受。该阻塞已由 §17.10 的 Linux Docker 复验关闭；本段不代表当前状态，也不关闭 Linux 锁互操作、文件 owner/mode、真实密钥/签名 artifact、停机窗口、恢复演练、资源压测、Windows 发布资格或现场门禁。

### 17.10 `LC02-04C-2` Linux Docker 最终复验与 Sol 接受（2026-08-21）

结论：**接受，`LC02-04C-2` 转为 COMPLETE / SOL-ACCEPTED。** 本次最终复验继续同时依据《EasyAIoT 项目开发宪法》v1.6.0 与《平台功能计划》v1.5.0。Luna Max 使用 Docker Server 29.7.2、`maven:3.9.16-amazoncorretto-17-alpine`、Java 17.0.20、Maven 3.9.16，在 WSL2 Linux `6.18.33.2-microsoft-standard-WSL2` 临时容器中执行 §17.5；容器仅挂载仓库与 Maven repository，并为 `/tmp` 使用临时文件系统，未连接、重启或修改任何既有 EasyAIoT 服务容器。

完整八类冻结集为 `59/59`，Failures=0、Errors=0、Skipped=0 且 `BUILD SUCCESS`。此前 Windows 主机因权限不足未能创建 fixture 的 `databaseSymbolicLinkIsRejectedWithoutChangingRealDatabase` 与 `symbolicLinkLockIsRejectedWithoutChangingDatabase` 均已真实进入生产拒绝断言并通过。随后精确 28 模块 compile 为 `BUILD SUCCESS`；两个生产边界 `rg` 均 0 命中；`git diff --check` 通过。两个验证容器均使用 `--rm` 并已清理，未遗留精确名称容器。

本次证据关闭的是 `LC02-04C-2` 本地实现与符号链接资格门禁，只形成 `Verified-Local`；不等同于生产 Linux Java/Python `fcntl` 跨进程锁互操作、文件 owner/mode、真实密钥与签名 artifact 分发、停机窗口、恢复演练、资源压测、Windows 发布资格或现场验证，上述运行期证据继续保持 `OPEN-RUNTIME`。当前实现授权归零；下一步只能由 Sol 细化并冻结 `LC02-05`，经决策所有者再次授权后再交 GPT-5.6 Luna（max reasoning）实现与测试，`LC02-05`～`LC02-10` 在此之前继续锁定。

## 18. `LC02-05` canonical 上行 Topic、Claim 与路由集合冻结单（2026-08-21）

### 18.1 冻结依据、单一目标与阶段边界

本节同时依据《EasyAIoT 项目开发宪法》v1.6.0、《平台功能计划》v1.5.0、ADR-017 1.0.0 及本任务 §4.2/§5.3 冻结。现有代码事实为：SQLite V3 已持久化 `product_identification`，但 `ClaimedEnvelope` 尚未返回该身份，`SqliteOutboxWriter` 仍用 `siteCode/propertyCode` 构造 `/telemetry/**`；`PollingConfigProvider.current()` 已能读取本地已应用 ConfigSnapshot 1.1。

`LC02-05` 的单一目标固定为：

```text
SQLite V3 产品/设备身份
  → ClaimedEnvelope 路由身份
  → IotDeviceTopicEnum 唯一 canonical 上行 Topic

PollingConfigProvider.current() 已应用路由
  UNION
SQLite PENDING/IN_FLIGHT 路由
  → 可查询、去重、确定排序的精确路由集合
```

本包不连接或操作 MQTT，不启动/修改 ACK 订阅，不实现 SUBACK/集合替换/退订，不修改中心 Inbox、ACK、Store、ConfigSnapshot、SQLite Schema/user_version、历史回填、部署配置或运行开关。`PROPERTY_DOWNSTREAM_REPORT_ACK` 只允许作为共享路由值对象的纯派生字符串供 LC-03 后续使用，不代表本包已实现 ACK。

### 18.2 冻结共享接口合同

在 `iot-sink-api` 新增不可变路由值对象：

```java
public record TelemetryRoute(
        String productIdentification,
        String deviceIdentification
) implements Comparable<TelemetryRoute> {
    public String upstreamTopic();
    public String ackTopic();
}
```

合同固定如下：

1. 两个身份均按传入 Java 字符序列原值保存，禁止 trim、大小写折叠和 Unicode 归一化；不得用 site、property、展示名或默认值推断。
2. `productIdentification` 必须非 null、非空、非纯空白，Unicode code point 数为 1～128；`deviceIdentification` 必须非 null、非空、非纯空白，Unicode code point 数为 1～256。
3. 两个身份都必须能成为一个精确 MQTT Topic level：禁止 `/`、`+`、`#`、U+0000、C0/C1 控制字符及未配对 UTF-16 surrogate。合法 Unicode、前后空格和 NFD/NFC 差异保持原值。
4. `compareTo` 固定先按 `productIdentification`、再按 `deviceIdentification` 使用 Java `String.compareTo`；`equals/hashCode` 使用 record 原值语义。
5. `upstreamTopic()` 只能调用 `IotDeviceTopicEnum.PROPERTY_UPSTREAM_REPORT.buildTopic(productIdentification, deviceIdentification)`；`ackTopic()` 只能调用 `IotDeviceTopicEnum.PROPERTY_DOWNSTREAM_REPORT_ACK.buildTopic(...)`。不得在其他新代码复制 `/iot/**` 字面量。

`ClaimedEnvelope` 的 canonical constructor 冻结为：

```java
public record ClaimedEnvelope(
        long id,
        String messageId,
        byte[] canonicalBytes,
        String contentSha256,
        String tenantId,
        String siteCode,
        String productIdentification,
        String deviceIdentification,
        String propertyCode
) {
    public String topic();
}
```

其中 `canonicalBytes` 继续双向防御性复制；构造时必须以 `TelemetryRoute` 校验产品/设备身份；`topic()` 只返回该路由的 `upstreamTopic()`。不再接受调用者传入可与身份漂移的 `topic` record component，Envelope V1 canonical bytes/hash 不变。

`TelemetryOutboxPort` 新增：

```java
List<TelemetryRoute> listUnfinishedRoutes();
```

返回值只包含 SQLite 中状态为 `PENDING` 或 `IN_FLIGHT` 的非空产品/设备二元路由，按 `TelemetryRoute.compareTo` 去重、升序且不可修改；`ACKED`、`DEAD_LETTER` 和产品身份为空的历史行不得进入结果。

同时新增：

```java
public interface TelemetryRouteSetProvider {
    List<TelemetryRoute> currentRoutes();
}
```

`CollectorTelemetryRouteSetProvider` 只依赖 `PollingConfigProvider` 与 `TelemetryOutboxPort`。每次查询把 `PollingConfigProvider.current()` 返回的已应用 1.1 快照内全部设备路由，与 `listUnfinishedRoutes()` 做集合并集；无 applied 快照时仍返回 unfinished 路由；两侧重复项只保留一项，结果同样按 `TelemetryRoute.compareTo` 排序并不可修改。它是无后台线程、无缓存、无 MQTT 副作用的按需查询边界；本包不新增 Spring Bean 或自动配置接线。

### 18.3 SQLite 单 writer 与失败语义

1. Claim 的 SELECT 必须读取 `product_identification`，并继续只选择 `PENDING`、产品身份非 NULL、达到 retry 时间且满足既有优先级/批量上限的行；不得改变既有排序、租约、attempts 或状态机。
2. 每个候选在任何 `PENDING → IN_FLIGHT` 更新前构造 `TelemetryRoute`。任一候选身份非法时，整次 Claim 不更新任何行并以 `OutboxUnavailableException` 的稳定前缀 `ROUTE_IDENTITY_INVALID` 失败；不得跳过非法首行继续发送、不得生成旧 Topic、不得把 writer 线程杀死。对应 command future 必须 exceptional completion，后续合法命令仍可执行。
3. Claim 成功时 `ClaimedEnvelope.productIdentification()` 与 SQLite 原值逐 Java code unit 相等，`topic()` 精确等于 enum 生成的 `/iot/{product}/{device}/property/upstream/report`；`siteCode/propertyCode` 仅保留为既有业务元数据，不参与 Topic。
4. unfinished 路由查询必须作为 control command 在既有 writer connection/线程内执行，禁止另开绕过 `collector-outbox.lock` 的 SQLite 连接；查询只读且不改变事务状态、attempts、lease、retry 或时间戳。非法 unfinished 路由使整次查询以同一 `ROUTE_IDENTITY_INVALID` 前缀失败，不得返回不完整集合；future exceptional completion 后 writer 必须继续存活。
5. 路由查询是调用时点的只读快照，不承诺跨本地配置文件与 SQLite 的分布式原子快照。LC-03 必须在配置 APPLIED 及 unfinished 路由集合变化时刷新，并遵守 ADR-017 的“订阅新增并确认 SUBACK → 原子替换 → 取消旧订阅”顺序；该刷新机制仍锁定，不得在本包偷跑。

### 18.4 精确文件白名单

允许修改或新增的生产文件仅为：

- `DEVICE/iot-sink/iot-sink-api/src/main/java/com/basiclab/iot/sink/telemetry/outbox/TelemetryRoute.java`（新增）；
- `DEVICE/iot-sink/iot-sink-api/src/main/java/com/basiclab/iot/sink/telemetry/outbox/TelemetryRouteSetProvider.java`（新增）；
- `DEVICE/iot-sink/iot-sink-api/src/main/java/com/basiclab/iot/sink/telemetry/outbox/ClaimedEnvelope.java`；
- `DEVICE/iot-sink/iot-sink-api/src/main/java/com/basiclab/iot/sink/telemetry/outbox/TelemetryOutboxPort.java`；
- `DEVICE/iot-sink/iot-sink-biz/src/main/java/com/basiclab/iot/sink/protocol/polling/CollectorTelemetryRouteSetProvider.java`（新增）；
- `DEVICE/iot-sink/iot-sink-biz/src/main/java/com/basiclab/iot/sink/outbox/sqlite/OutboxCommand.java`；
- `DEVICE/iot-sink/iot-sink-biz/src/main/java/com/basiclab/iot/sink/outbox/sqlite/OutboxCommandQueue.java`；
- `DEVICE/iot-sink/iot-sink-biz/src/main/java/com/basiclab/iot/sink/outbox/sqlite/SqliteOutboxWriter.java`；
- `DEVICE/iot-sink/iot-sink-biz/src/main/java/com/basiclab/iot/sink/outbox/sqlite/SqliteTelemetryOutbox.java`。

允许修改或新增的测试文件仅为上述合同/实现对应的 `TelemetryRouteContractTest`、`OutboxClaimTest`、`OutboxUnfinishedRoutesTest`、`CollectorTelemetryRouteSetProviderTest`，以及为构造签名机械适配现有测试所必需的直接测试文件。任务单与 SDD 续作入口可更新验证状态。

`IotDeviceTopicEnum.java`、`VertxCollectorMqttPublisher.java`、`OutboxDispatcher.java`、`TelemetryOutboxBatch.java`、`CollectorConfigSnapshot.java`、`PollingConfigProvider.java` 和 `CollectorPollingRuntime.java` 本包只读复用，不在白名单。明确禁止修改 MQTT publisher/subscriber/连接参数、`CollectorMqttAckSubscriber`、center Inbox/ACK、TelemetryStore/query、Envelope/canonical hash、SQLite Schema/migration/user_version、writer append/collision、04A～04C 回填、iot-device/iot-node/NODE、配置/部署/开关、Maven 依赖或数据库资产。

### 18.5 停工条件

出现以下任一情形，Luna 必须停止并交回 Sol，不得自行扩大范围：

1. 需要修改 `IotDeviceTopicEnum` 才能形成 canonical Topic，或发现 enum 模板与 ADR-017 不一致；
2. 需要改变 ConfigSnapshot 1.1、`TelemetryOutboxBatch`、Envelope/hash、SQLite Schema/user_version 或历史回填事实；
3. 无法在既有单 writer command/future 边界内完成 Claim 或 unfinished 查询，必须新建并发 SQLite connection/后台线程；
4. 需要接线 ACK subscriber、MQTT、Spring 自动配置、运行开关或 center 服务才能证明本包；
5. 非法路由只能通过默认值、trim/归一化、跳过坏行、旧 `/telemetry/**` 回退或部分集合返回处理；
6. 需要任一 §18.4 白名单外生产文件，或与工作树中既有 04C/用户改动发生不可隔离冲突。

### 18.6 冻结测试矩阵与验收命令

直接合同至少覆盖：

- 合法 ASCII/Unicode/NFD/NFC 原值、确定排序、不可修改集合；产品 128/129 与设备 256/257 code point 边界；null/空/纯空白、`/`、`+`、`#`、NUL、C0/C1、未配对 surrogate 全部拒绝；上行/ACK 字符串精确来自 enum。
- Claim 返回原产品身份与 canonical 上行 Topic；canonical bytes/hash 不变；`siteCode/propertyCode` 改变不改变 Topic；批量上限、优先级、租约、attempts 和空 Claim 既有合同保持。
- 非法 PENDING 路由使整批 Claim 零状态变化并返回 `ROUTE_IDENTITY_INVALID`；随后修复 fixture 或提交独立合法命令，证明 writer 未死亡。历史 NULL 产品行仍不可 Claim。
- unfinished 查询只含 PENDING/IN_FLIGHT，排除 ACKED/DEAD_LETTER/NULL，精确去重排序；查询前后所有业务列逐值不变；非法任一路由拒绝整集合且 writer 存活。
- applied 快照路由与 unfinished 路由并集；设备解绑后旧路由只要仍 PENDING/IN_FLIGHT 就保留，转 ACKED/DEAD_LETTER 后下一次查询移除；无 applied、重复路由、多总线设备及不可修改返回值。
- `VertxCollectorMqttPublisher`/dispatcher 不需要网络即可由 Claim/值对象合同证明只会取得 canonical `topic()`；不得用 mock 接线 ACK 或把 MQTT 集成冒充本包通过。

冻结测试命令：

```powershell
mvn -f DEVICE/pom.xml clean test -pl iot-sink/iot-sink-biz -am `
  -Dtest=TelemetryRouteContractTest,OutboxClaimTest,OutboxUnfinishedRoutesTest,CollectorTelemetryRouteSetProviderTest,OutboxStateMachineTest,SqliteOutboxDurabilityTest,TelemetryOutboxBatchContractTest,CollectorPollingRuntimeTest,CollectorTelemetryWriterTest `
  -DfailIfNoTests=false -Dmaven.test.skip=false

mvn -f DEVICE/pom.xml clean compile -pl iot-sink/iot-sink-biz -am -DskipTests

rg -n '"/telemetry/|/telemetry/' `
  DEVICE/iot-sink/iot-sink-api/src/main/java/com/basiclab/iot/sink/telemetry/outbox/ClaimedEnvelope.java `
  DEVICE/iot-sink/iot-sink-api/src/main/java/com/basiclab/iot/sink/telemetry/outbox/TelemetryRoute.java `
  DEVICE/iot-sink/iot-sink-biz/src/main/java/com/basiclab/iot/sink/outbox/sqlite/SqliteOutboxWriter.java `
  DEVICE/iot-sink/iot-sink-biz/src/main/java/com/basiclab/iot/sink/protocol/polling/CollectorTelemetryRouteSetProvider.java

rg -n '"/iot/' `
  DEVICE/iot-sink/iot-sink-api/src/main/java/com/basiclab/iot/sink/telemetry/outbox `
  DEVICE/iot-sink/iot-sink-biz/src/main/java/com/basiclab/iot/sink/outbox/sqlite `
  DEVICE/iot-sink/iot-sink-biz/src/main/java/com/basiclab/iot/sink/protocol/polling/CollectorTelemetryRouteSetProvider.java

git diff --exit-code -- `
  DEVICE/iot-sink/iot-sink-biz/src/main/java/com/basiclab/iot/sink/outbox/sqlite/SqliteOutboxSchema.java `
  DEVICE/iot-sink/iot-sink-biz/src/main/java/com/basiclab/iot/sink/outbox/sqlite/SqliteOutboxMigration.java

git diff --check
```

两个 `rg` 命令预期 0 命中；enum 常量引用由 `TelemetryRouteContractTest` 及代码审查确认。测试必须全部 Failures=0、Errors=0、Skipped=0，compile 必须覆盖 `iot-sink-api`、`iot-sink-biz` 及依赖并 `BUILD SUCCESS`。Luna 交付必须报告精确测试类/测试数、变更文件、未执行项、风险与回滚；不得 commit。

本包只需要本地 Java/SQLite 合同证据，不要求 Docker、PostgreSQL、MQTT Broker、Linux PTY、资源压测、Windows 发布资格或现场验证，也不关闭任何既有 `OPEN-RUNTIME`。上述运行项不得因本包授权而执行。

### 18.7 当前授权状态与后继门禁

本节现为 `FROZEN / NOT-YET-AUTHORIZED`。本次“授权下一步”只用于 Sol 完成 LC02-05 的需求拆解、接口合同、文件白名单、停工条件与验收矩阵冻结，尚未授权 Luna 修改生产代码。下一步须由决策所有者独立授权 GPT-5.6 Luna（max reasoning）严格执行 §18.1～§18.6；`LC02-06`～`LC02-10` 继续锁定。

### 18.8 `LC02-05` 独立实现授权（2026-08-21）

决策所有者已在 §18 冻结后独立授权下一步。Sol 已复核《EasyAIoT 项目开发宪法》v1.6.0、《平台功能计划》v1.5.0、ADR-017 1.0.0 与 §18.1～§18.6，未发现需要停工修订的双基线冲突。

当前唯一授权 GPT-5.6 Luna（max reasoning）在 §18.4 精确白名单内实现并测试 `LC02-05`。实现者不得 commit；遇到 §18.5 任一条件必须立即停止并交回 Sol。授权不包含 `LC02-06`～`LC02-10`，不包含 ACK/MQTT 接线、中心 Inbox、Schema/migration、ConfigSnapshot、历史回填、自动配置、部署/开关或任何 `OPEN-RUNTIME` 验证。

### 18.9 `LC02-05` Sol 最终复核结论（2026-08-21）

结论：**接受，`LC02-05` 转为 COMPLETE / SOL-ACCEPTED。** 本次复核同时依据《EasyAIoT 项目开发宪法》v1.6.0、《平台功能计划》v1.5.0 与 ADR-017 1.0.0。GPT-5.6 Luna（max reasoning）只在 §18.4 白名单内完成共享 `TelemetryRoute`/`TelemetryRouteSetProvider`、Claim 产品路由身份、canonical 上行 Topic、单 writer unfinished 路由查询及 applied/outbox 路由并集；既有 04C 锁生命周期改动保持不变，未进入 ACK/MQTT、中心 Inbox、Schema/migration、ConfigSnapshot、历史回填、自动配置、部署或后续包。

Sol 首轮独立审查暂缓接受并要求最小 R1：unfinished 查询在 `autoCommit=false` connection 上成功返回前未 commit，可能遗留读事务；Provider 测试缺“设备解绑但有 PENDING/IN_FLIGHT 时保留，转终态后移除”和跨多 serial bus 重复路由去重的直接证据。Luna 在原白名单内修正成功路径 commit/异常 rollback，并补齐两项测试，未扩大接口或文件范围。

Sol 随后独立执行 §18.6 九类 clean reactor 冻结集：`OutboxClaimTest` 6、`OutboxStateMachineTest` 5、`OutboxUnfinishedRoutesTest` 3、`SqliteOutboxDurabilityTest` 4、`CollectorPollingRuntimeTest` 2、`CollectorTelemetryRouteSetProviderTest` 5、`CollectorTelemetryWriterTest` 2、`TelemetryOutboxBatchContractTest` 5、`TelemetryRouteContractTest` 5，合计 `37/37`，Failures=0、Errors=0、Skipped=0，28 个 reactor 模块全部 `SUCCESS`。独立 `clean compile` 同样覆盖 28 个模块并 `BUILD SUCCESS`。

旧 `/telemetry/**` 与新包 `/iot/**` 字面量扫描均 0 命中；canonical Topic 仅由 enum 常量派生。`SqliteOutboxMigration.java` 无 diff，冻结命令列出的 `SqliteOutboxSchema.java` 在仓库不存在且未新增，Schema/user_version 保持不变；`git diff --check` 通过，仅有工作树既存 LF/CRLF 提示。Sol 首次命令因 PowerShell 未引用逗号参数未进入构建，首次沙箱构建又因 Maven 仓库网络权限失败；改用正确参数并授权网络后，上述两组最终命令均成功，不构成代码缺陷。

本包只形成 `Verified-Local` Java/SQLite 证据。Docker、PostgreSQL、MQTT Broker、Linux PTY、资源压测、Windows 发布资格、现场验证及全部既有 `OPEN-RUNTIME` 均未执行且不因本次接受而关闭。当前实现授权归零；下一步只能由 Sol 细化并冻结 `LC02-06` 的 center Topic 与权威注册事实校验包，经决策所有者再次授权后再交 GPT-5.6 Luna（max reasoning）实现与测试。`LC02-06`～`LC02-10` 在此之前继续锁定。

## 19. `LC02-06` 中心 Topic、权威设备注册与租户一致性冻结单（2026-08-21）

### 19.1 冻结依据、代码事实与单一目标

Sol 已逐项复核《EasyAIoT 项目开发宪法》v1.6.0、《平台功能计划》v1.5.0、ADR-017 1.0.0、ADR-018 1.0.0 和已接受的 §18 `TelemetryRoute` 合同，未发现需停止并修订双基线的冲突。本包适用于 `standard/full` 的同一实现；`mini` 不装配中心可靠遥测入口。

当前代码事实如下：

1. `CenterMqttInboxSubscriber` 仍订阅 `/telemetry/#`，只解析 Envelope，不解析或核对传入 Topic，随后直接调用 `TelemetryInboxPort.receiveEnvelopes`；
2. `iot-device` 现有 `RemoteDeviceService.selectByProductIdentificationAndDeviceIdentification` 和 iot-sink 本地同名 Mapper 查询均使用 `limit 1`，不能证明全局唯一，且前者不是为中心权威解析冻结的 ADR-018 专用内部合同；
3. 当前 `device` 基线未发现能保证 Topic `{productIdentification,deviceIdentification}` 全局唯一的约束。相同路由在同租户重复、跨租户重复都必须由查询显式识别为 `AMBIGUOUS`，不得任选首行；
4. 宪法禁止 iot-sink 为本功能跨服务直接查询 iot-device 私有数据库。现有 iot-sink `DeviceMapper/getDeviceIgnoreTenant` 只能作为旧代码事实，不得被 LC02-06 新链路调用；
5. 当前 MQTT 客户端消息回调不提供可信原始发布主体，因此本包只能证明 `Topic route → iot-device 权威注册事实 → Envelope tenant/device`；主体到 Topic 的 ACL 证据仍归 `LC02-09`。

本包的唯一目标是：在任何业务 Inbox 调用之前，对 canonical 上行 Topic、Envelope 设备、权威注册租户进行确定性三方校验，并返回可被后续 `M1-LC-04` 可靠拒绝审计复用的稳定分类。本包不得新增 ACK、拒绝审计表、Inbox 产品字段或数据库迁移。

实现顺序固定为：

```text
LC02-06A：iot-device 权威设备解析内部合同/provider/auth
    ↓ Sol/Luna 同包测试通过
LC02-06B：iot-sink Topic parser/client adapter/ingress guard/subscriber 接线
```

06B 不得以 mock provider 作为生产接线替代 06A；两段完成后才可将 `LC02-06` 交回 Sol 验收。

### 19.2 canonical Topic 解析合同

在 iot-sink 中新增无 I/O 的 `TelemetryUpstreamTopicParser`，解析结果只能是 `Parsed(TelemetryRoute)` 或带稳定码的 `Rejected`。解析器必须：

1. 从 `IotDeviceTopicEnum.PROPERTY_UPSTREAM_REPORT.getTopicTemplate()` 派生结构和中心订阅过滤器，不在 Java 中另写 `/iot/{...}` 拼接逻辑；
2. 使用 `split("/", -1)` 等保留空层级的等价方式，要求层级数、首部空层、固定层级及结尾全部与 enum 模板精确相等；不接受多/少层级、尾随 `/`、空产品/设备、大小写折叠、trim、URL decode、Unicode normalize 或其他 Topic 类型；
3. 复用 `TelemetryRoute` 的产品/设备 code-point 长度、surrogate、`/ + #` 和 C0/C1 控制字符规则。允许在 `TelemetryRoute` 中新增分别校验产品/设备的公共静态 guard，但不得改变 §18 已接受的构造、比较、upstream/ack Topic 语义；
4. 最后以 `parsedRoute.upstreamTopic().equals(inputTopic)` 做 exact round-trip 断言。不能 round-trip 的输入必须拒绝；
5. 只生成中心共享订阅过滤器 `/iot/+/+/property/upstream/report`；生产设备主体 wildcard 订阅不在本包授权范围。

解析分类固定为：

| 稳定码 | 条件 | disposition |
|---|---|---|
| `TELEMETRY_TOPIC_MALFORMED` | null、结构/固定层级/round-trip 不匹配 | `FINAL` |
| `TELEMETRY_TOPIC_PRODUCT_INVALID` | 产品 Topic level 违反 `TelemetryRoute` 规则 | `FINAL` |
| `TELEMETRY_TOPIC_DEVICE_INVALID` | 设备 Topic level 违反 `TelemetryRoute` 规则 | `FINAL` |

`TelemetryMqttProperties.topicFilter` 与 `application.yaml` 默认值改为上述精确共享过滤器。显式配置为 `/telemetry/**`、`#`、`/iot/#` 或其他宽/窄过滤器时必须在启动阶段 fail-closed，不得静默替换或影子订阅。

### 19.3 iot-device 权威注册内部合同（LC02-06A）

#### 19.3.1 API 与 DTO

在 `iot-device-api` 新增 `TelemetryDeviceAuthorityInternalApi` 及专用 DTO，不复用/返回 `Device` 实体。唯一端点为：

```text
GET /internal-api/device/telemetry-authority/resolve
    ?productIdentification={exactProduct}
    &deviceIdentification={exactDevice}
```

响应使用既有 `R<T>` 包装，业务 DTO 固定为：

```java
public record TelemetryDeviceAuthorityResolutionDTO(
        String productIdentification,
        String deviceIdentification,
        ResolutionStatus status,
        String tenantId
) {}

public enum ResolutionStatus { RESOLVED, NOT_FOUND, AMBIGUOUS }
```

`TelemetryDeviceAuthorityInternalApi` 只保存路径常量、Spring MVC method/query 注解和 DTO 签名，不声明无 signer 的通用 `@FeignClient`。iot-sink 必须在 §19.6 新 route 包内声明 consumer-local Feign client 继承该合同，并通过 client 专属 `configuration` 挂载 signer、timeout 和 `Retryer.NEVER_RETRY`，避免其他消费者无意取得此内部权限。

约束：

- route echo 必须与请求逐字符相等；禁止 trim、大小写折叠、默认值和站点/属性推断；
- 仅 `RESOLVED` 允许且必须返回 canonical 正十进制 `tenantId`；`NOT_FOUND/AMBIGUOUS` 的 `tenantId` 必须为 `null`，不得泄露候选租户；
- 正常的零行/一行/两行以上分别映射为 `NOT_FOUND/RESOLVED/AMBIGUOUS`，均返回 HTTP 200；provider、数据库、认证或合同异常不得伪装成 `NOT_FOUND`；
- provider 必须先验签再校验 route；非法 route 返回稳定 `TELEMETRY_DEVICE_AUTHORITY_REQUEST_INVALID`（HTTP 400），唯一候选的 `tenant_id` 为 null、非正数或不可 canonical 化时返回 `TELEMETRY_DEVICE_AUTHORITY_DATA_INVALID`（HTTP 500），依赖异常返回 `TELEMETRY_DEVICE_AUTHORITY_UNAVAILABLE`（HTTP 503）；center 均映射为本包 `Unavailable`；
- API 不经 `iot-gateway` 暴露，不接受用户 Token、`login-user` 或 `tenant-id` Header 作为信任输入。

#### 19.3.2 权威查询

provider 通过新增 Mapper 方法 `selectTelemetryDeviceAuthorityCandidates`，在 iot-device 自有数据库内执行 exact、单语句、`@Transactional(readOnly=true)` 且显式忽略当前请求租户拦截器的有界查询：

```sql
SELECT tenant_id, product_identification, device_identification
FROM device
WHERE product_identification = :product
  AND device_identification = :device
  AND deleted = 0
ORDER BY id ASC
LIMIT 2
```

返回 2 行已经足够判定歧义，禁止 `limit 1`、无界列表、当前租户过滤、缓存首行或跨租户任选。设备启停/在线状态不改变本包的注册身份判断；软删除行不属于权威事实。若后续证明必须校验产品表、设备状态或增加 UNIQUE/index/DDL，立即触发 §19.7 停工，不得由 Luna 自行扩展。

#### 19.3.3 ADR-018 服务认证

- provider 必须用现有 `InternalServiceAuthVerifier` 校验完整 method、path、排序 query、空 body hash、时间窗、nonce 与 HMAC-SHA256，并额外只允许 `serviceId=iot-sink`；
- iot-device 的 `easyaiot.security.internal.routes` 只新增上述 GET 静态路径；签名正确也不得访问 collector release 或其他内部端点；
- iot-sink 使用 client 专属 Feign interceptor 和 `serviceId=iot-sink` 签名；禁止全局 interceptor、Feign fallback、用户 Header 或 token-only 降级；
- keyId 可配置但无真实默认值；secret 只通过 ADR-018 `InternalServiceKeyProvider` 的仓库外引用注入。配置/密钥/Redis nonce 条件缺失时，MQTT subscriber 装配必须 fail-closed；
- Feign 固定无自动重试，候选 connect/read timeout 为 `500 ms / 1000 ms`。超时值可由部署配置收紧，扩大到 5 秒以上或增加重试必须交回 Sol；
- 日志不得记录签名、nonce、secret、完整 payload 或候选租户列表。

### 19.4 iot-sink 三方校验与拒绝分类（LC02-06B）

新增 `TelemetryDeviceAuthorityPort`，结果只允许 `Resolved(tenantId)`、`NotFound`、`Ambiguous`、`Unavailable`。Feign adapter 必须严格验证 HTTP/R/DTO/status/route echo/tenantId；网络异常、非成功 R、null/非法 DTO、echo 漂移统一映射 `Unavailable`，不得降级为 `NotFound`。

新增无 MQTT Broker 依赖、可直接测试的 `CenterTelemetryIngressHandler.handle(String topic, byte[] canonicalPayload)`。`CenterMqttInboxSubscriber` 只负责复制 Topic/payload 并调用该 handler；handler 的裁决顺序冻结为：

1. 解析 canonical Topic；失败按 §19.2 返回；
2. 按既有 Envelope V1 字段解析 `InboxEnvelope`；JSON 非对象、`tenantId/deviceIdentification` 非字符串、缺必填字段或既有解析合同失败为 `TELEMETRY_ENVELOPE_INVALID`；本包不借机重定义 Envelope canonical bytes/hash；
3. Topic device 与 Envelope `deviceIdentification` 逐字符比较，不一致为 `TELEMETRY_TOPIC_DEVICE_ENVELOPE_MISMATCH`；
4. Envelope `tenantId` 必须是 canonical 正十进制 long 字符串（无符号、前导零、空白或溢出），否则为 `TELEMETRY_ENVELOPE_TENANT_INVALID`；
5. 用 Topic 的 product/device 调用权威端口；`NotFound/Ambiguous/Unavailable` 分别分类；
6. `Resolved.tenantId` 与 Envelope tenant 逐字符相等才允许调用一次 `TelemetryInboxPort.receiveEnvelopes(List.of(envelope))`；不一致为 `TELEMETRY_DEVICE_REGISTRATION_TENANT_MISMATCH`；
7. Inbox 返回值仍完全使用 LC-01 合同。Inbox 抛错属于接收失败，不得伪装成本节安全拒绝，也不得在本包发送 ACK。

稳定分类全集如下，Luna 不得新增同义码或把不同条件合并为字符串消息：

| 稳定码 | disposition | 是否允许调用 Inbox |
|---|---|---|
| `TELEMETRY_TOPIC_MALFORMED` | `FINAL` | 否 |
| `TELEMETRY_TOPIC_PRODUCT_INVALID` | `FINAL` | 否 |
| `TELEMETRY_TOPIC_DEVICE_INVALID` | `FINAL` | 否 |
| `TELEMETRY_ENVELOPE_INVALID` | `FINAL` | 否 |
| `TELEMETRY_ENVELOPE_TENANT_INVALID` | `FINAL` | 否 |
| `TELEMETRY_TOPIC_DEVICE_ENVELOPE_MISMATCH` | `FINAL` | 否 |
| `TELEMETRY_DEVICE_REGISTRATION_NOT_FOUND` | `FINAL` | 否 |
| `TELEMETRY_DEVICE_REGISTRATION_AMBIGUOUS` | `RETRYABLE` | 否 |
| `TELEMETRY_DEVICE_AUTHORITY_UNAVAILABLE` | `RETRYABLE` | 否 |
| `TELEMETRY_DEVICE_REGISTRATION_TENANT_MISMATCH` | `FINAL` | 否 |

`FINAL/RETRYABLE` 在本包只是审计输入分类，不授权 ACK。拒绝日志只允许低敏摘要、messageId（可得时）、稳定码和 disposition；指标 label 只允许稳定码/disposition，不得使用 product/device/tenant/messageId 等高基数字段。

### 19.5 激活、性能与回滚边界

- 本包只关闭本地合同正确性。现有 `application-standard.yaml` 的 MQTT subscriber 改为读取 `EASYAIOT_TELEMETRY_MQTT_ENABLED` 且默认 `false`；`full` 复用同一开关和实现。`mini` 必须保持未装配；
- 在 `LC02-09` ACL 和 `LC02-10` 联合回归/激活评审前，不得把 center subscriber 默认置为 true。关闭 subscriber 不影响 collector 已持久化 outbox，且不得为回滚重新启用 `/telemetry/**`；
- 当前 device 基线未证明 authority 查询存在适用组合索引，且本地阶段不执行资源压测。`LC02-06-RUNTIME-01` 保持 OPEN：生产启用前必须画像索引/数据量并完成目标吞吐、P95/P99、依赖断连和积压预算；需要 DDL 时另走 ADR-013 迁移冻结；
- 本包不得加入无失效事件的正向 authority 缓存。若压测证明逐消息查询不满足预算，应由 Sol 另行冻结带撤销/租户变更一致性的缓存或注册投影，不得用 TTL 猜测安全事实；
- 权威 API 候选目标为同集群 P95 `≤200 ms`、单响应远小于 4 KiB；该目标未经压测，不得在本包交付时标记已验证。

### 19.6 精确文件白名单

Luna 仅可修改或新增以下文件/目录；既有脏改动必须原样保护：

#### iot-device API/provider

- `DEVICE/iot-device/iot-device-api/src/main/java/com/basiclab/iot/device/TelemetryDeviceAuthorityInternalApi.java`（新增）；
- `DEVICE/iot-device/iot-device-api/src/main/java/com/basiclab/iot/device/domain/device/authority/`（新增专用 DTO/enum）；
- `DEVICE/iot-device/iot-device-biz/src/main/java/com/basiclab/iot/device/controller/device/authority/`（新增）；
- `DEVICE/iot-device/iot-device-biz/src/main/java/com/basiclab/iot/device/service/device/authority/`（新增）；
- `DEVICE/iot-device/iot-device-biz/src/main/java/com/basiclab/iot/device/dal/pgsql/device/DeviceMapper.java`（仅新增有界 authority 查询）；
- `DEVICE/iot-device/iot-device-biz/src/main/resources/mapper/device/DeviceMapper.xml`（仅新增 §19.3.2 查询）；
- `DEVICE/iot-device/iot-device-biz/src/main/resources/application.yaml`（仅新增内部 GET allowlist）；
- 上述包对应的 `src/test/java` 直接合同测试。

#### iot-sink parser/guard/client/subscriber

- `DEVICE/iot-sink/iot-sink-api/src/main/java/com/basiclab/iot/sink/telemetry/outbox/TelemetryRoute.java`（仅公开拆分 identity guard，既有语义不变）；
- `DEVICE/iot-sink/iot-sink-biz/pom.xml`（仅在现有传递依赖不足时显式补 `iot-common-security`，不得升级版本）；
- `DEVICE/iot-sink/iot-sink-biz/src/main/java/com/basiclab/iot/sink/telemetry/inbox/route/`（新增）；
- `DEVICE/iot-sink/iot-sink-biz/src/main/java/com/basiclab/iot/sink/telemetry/inbox/mqtt/CenterMqttInboxSubscriber.java`；
- `DEVICE/iot-sink/iot-sink-biz/src/main/java/com/basiclab/iot/sink/telemetry/inbox/mqtt/TelemetryMqttProperties.java`；
- `DEVICE/iot-sink/iot-sink-biz/src/main/java/com/basiclab/iot/sink/telemetry/inbox/TelemetryInboxAutoConfiguration.java`；
- `DEVICE/iot-sink/iot-sink-biz/src/main/resources/application.yaml`（仅 MQTT filter 与 authority client 非敏感配置）；
- `DEVICE/iot-sink/iot-sink-biz/src/main/resources/application-standard.yaml`（仅 MQTT 默认关闭接线）；
- 上述包对应的 `src/test/java` 直接合同测试；
- 本任务单与 `M1-SDD进度与续作入口.md` 的状态/证据回填。

禁止修改 `InboxEnvelope`、`TelemetryInboxPort`、`JdbcTelemetryInbox`、Projection、Schema/DDL/migration、Outbox/collector、ACK publisher/subscriber、EMQX ACL、Compose、`.env` 或 `env.example`。白名单不包含 iot-sink 旧 `DeviceMapper/getDeviceIgnoreTenant`；不得借本包重构或删除旧通用上行链路。

### 19.7 停工条件

出现任一情况立即停止并交回 Sol，不得自行扩大：

1. 权威唯一解析需要 `device/product` DDL、UNIQUE/index、数据清洗或共享环境迁移；
2. 需要信任当前租户上下文、用户 Header、payload 自报产品、站点/属性推断、缓存首行或 `/telemetry/**` 才能通过；
3. 需要修改 common-security 的签名/canonical/nonce 实现，或无法为新 Feign client 独立挂载 `iot-sink` signer；
4. 需要改变 Envelope V1、canonical bytes/hash、Inbox 字段/幂等合同、ACK、拒绝审计或 Store/Projection；
5. 发现 enum 模板与 §18 `TelemetryRoute.upstreamTopic()` 不能 exact round-trip；
6. 需要启动真实 MQTT、修改 Broker/ACL、默认开启 subscriber、运行压测、生产/共享库迁移、Linux PTY、Windows 发布或现场验证；
7. 任何白名单外生产文件成为实现必需，或与用户既有脏改动发生无法隔离的重叠。

### 19.8 冻结测试矩阵

| 层 | 必须直接覆盖 |
|---|---|
| `TelemetryRoute`/Topic parser | canonical 正向；null；层级多/少；尾 `/`；错固定层；产品/设备空、过长、`/ + #`、控制字符、未配对 surrogate；NFC/NFD 不归一化；非属性上行 Topic；exact round-trip；共享 filter 唯一 |
| device authority provider | 0 行 `NOT_FOUND`；1 行 `RESOLVED` + canonical tenant；同租户 2 行、跨租户 2 行均 `AMBIGUOUS`；软删除不参与；查询最多 2 行且无 `limit 1`/当前租户过滤 |
| ADR-018 API | `iot-sink` 正确签名通过；缺 Header、错 service、错 key、过期、重放、错签名、query 被篡改拒绝；provider 不可用不伪装 NOT_FOUND；响应不返回 Device/候选租户列表 |
| Feign adapter | 三业务状态准确映射；非成功 R、null/非法 DTO、route echo 漂移、超时/异常均 `Unavailable`；无 fallback、无自动重试 |
| ingress handler | canonical 正向仅调用 Inbox 一次；错误产品→NOT_FOUND；Topic/Envelope 错设备；畸形 Topic；非法 tenant；跨租户；歧义；authority unavailable；每个拒绝断言精确 code/disposition 且 Inbox 0 次 |
| subscriber/config | 只委托 handler；精确 filter；旧 `/telemetry/#` 和宽 wildcard 启动拒绝；authority auth/config 缺失 fail-closed；standard/full 同合同；mini 不装配 |
| 边界回归 | LC-01 Inbox 结果合同与 LC02-05 `TelemetryRouteContractTest` 全绿；Inbox/Schema/ACK/Outbox 无 diff |

真实 PostgreSQL authority fixture 只允许在本地临时 schema/专用租户执行，测试前后必须清零；测试开关与连接参数固定从 `LC02_AUTHORITY_PG_ENABLED=true`、`LC02_AUTHORITY_PG_URL`、`LC02_AUTHORITY_PG_USER`、`LC02_AUTHORITY_PG_PASSWORD` 读取，凭据只允许通过单次 Maven 进程环境注入，不得写入代码、文档、命令历史或 diff。若 Docker/PG 当次不可用，只能把该测试记为未执行，不能用 mock 冒充真实唯一解析证据；Sol 最终接受要求三组命令 `Skipped=0`。

### 19.9 验收命令

实现完成后 Luna 必须执行并原样报告精确类数/测试数、Failures/Errors/Skipped、编译模块数和未执行项：

```powershell
mvn -f DEVICE/pom.xml clean test `
  -pl iot-device/iot-device-biz -am `
  -Dmaven.test.skip=false `
  -Dsurefire.failIfNoSpecifiedTests=false `
  '-Dtest=TelemetryDeviceAuthorityServiceContractTest,TelemetryDeviceAuthorityInternalApiContractTest,TelemetryDeviceAuthorityPostgresIntegrationTest'

mvn -f DEVICE/pom.xml clean test `
  -pl iot-sink/iot-sink-biz -am `
  -Dmaven.test.skip=false `
  -Dsurefire.failIfNoSpecifiedTests=false `
  '-Dtest=TelemetryRouteContractTest,TelemetryUpstreamTopicParserContractTest,TelemetryDeviceAuthorityClientAdapterTest,CenterTelemetryIngressHandlerTest,CenterMqttInboxSubscriberContractTest,TelemetryInboxAutoConfigurationTest'

mvn -f DEVICE/pom.xml clean compile `
  -pl 'iot-device/iot-device-biz,iot-sink/iot-sink-biz' -am `
  -DskipTests

rg -n 'limit 1|LIMIT 1|getDeviceIgnoreTenant|com\.basiclab\.iot\.sink\.dal\.mapper\.DeviceMapper' `
  DEVICE/iot-device/iot-device-biz/src/main/java/com/basiclab/iot/device/service/device/authority `
  DEVICE/iot-sink/iot-sink-biz/src/main/java/com/basiclab/iot/sink/telemetry/inbox/route

rg -n -A 14 '<select id="selectTelemetryDeviceAuthorityCandidates"' `
  DEVICE/iot-device/iot-device-biz/src/main/resources/mapper/device/DeviceMapper.xml

rg -n '"/telemetry/|/telemetry/#|"/iot/' `
  DEVICE/iot-sink/iot-sink-biz/src/main/java/com/basiclab/iot/sink/telemetry/inbox/route `
  DEVICE/iot-sink/iot-sink-biz/src/main/java/com/basiclab/iot/sink/telemetry/inbox/mqtt/CenterMqttInboxSubscriber.java `
  DEVICE/iot-sink/iot-sink-biz/src/main/java/com/basiclab/iot/sink/telemetry/inbox/mqtt/TelemetryMqttProperties.java

git diff --name-only -- `
  DEVICE/iot-sink/iot-sink-api/src/main/java/com/basiclab/iot/sink/telemetry/inbox/InboxEnvelope.java `
  DEVICE/iot-sink/iot-sink-api/src/main/java/com/basiclab/iot/sink/telemetry/inbox/TelemetryInboxPort.java `
  DEVICE/iot-sink/iot-sink-biz/src/main/java/com/basiclab/iot/sink/telemetry/inbox/jdbc `
  DEVICE/iot-sink/iot-sink-biz/src/main/java/com/basiclab/iot/sink/outbox

git diff --check
```

第一、第三个 `rg` 命令预期 0 命中；中间的 Mapper 查询命令必须只展示一个 `selectTelemetryDeviceAuthorityCandidates`，含 `deleted = 0`、`ORDER BY id ASC`、`LIMIT 2`，不得出现 `LIMIT 1`。Topic Java 字面量必须只由 enum 模板派生。

受保护的 `InboxEnvelope.java`、`TelemetryInboxPort.java`、`telemetry/inbox/jdbc/**` 与 `outbox/**` 共 25 个文件，以“仓库相对路径正序 + 一个空格 + 文件 SHA-256 大写 hex，再以 LF 连接并对 UTF-8 manifest 做 SHA-256”的冻结摘要为 `3D557A19BD394FDCC062AAF645478CF6C87003F4A987E54786A8F30C05C8CD47`。Luna 开始前和交付时必须复算且精确相等；`git diff --name-only` 仅用于展示既有历史 diff，不能替代该摘要。摘要不等立即停工，不得清理或覆盖用户改动。

本包不要求真实 MQTT Broker、EMQX ACL、资源压测、Linux PTY、Windows 发布资格或现场验证；这些项目及 `LC02-06-RUNTIME-01` 均保持 OPEN。真实 PostgreSQL 仅用于专用 fixture 证明 authority 唯一/跨租户语义，不授权 V009、共享 schema 或生产迁移。

### 19.10 `LC02-06` 独立实现授权与后继门禁（2026-08-22）

决策所有者已在 §19 冻结后独立授权下一步。Sol 已重新核对《EasyAIoT 项目开发宪法》v1.6.0、《平台功能计划》v1.5.0、ADR-017 1.0.0、ADR-018 1.0.0 与 §19.1～§19.9，未发现需要停止并修订双基线的漂移或冲突。

当前唯一授权 GPT-5.6 Luna（max reasoning）按 `LC02-06A → LC02-06B` 顺序，在 §19.6 精确白名单内实现并测试。实现者不得 commit；开始和交付时必须复算 §19.9 受保护文件摘要；遇到 §19.7 任一条件必须立即停止并交回 Sol。授权不包含 `LC02-07`～`LC02-10`、V009、ACK、拒绝审计、EMQX ACL、生产激活、DDL/迁移、真实 MQTT、资源压测、Linux PTY、Windows 发布资格或现场验证，上述门禁继续锁定/OPEN。

Luna 交付后状态只能是“待 Sol 复核”，不得自行把 `LC02-06` 标记为 COMPLETE / SOL-ACCEPTED。只有 Sol 独立审查 diff、确认白名单和停工条件、复跑 §19.9 且真实 PostgreSQL fixture `Skipped=0` 后，才能决定接受、要求最小 R1 或停止重冻结。

### 19.11 Luna `LC02-06A → LC02-06B` 交付记录（2026-08-22，待 Sol 复核）

本轮由 GPT-5.6 Luna（max reasoning）按 §19.6 白名单完成 06A 与 06B，随后执行 Sol 要求的 `LC02-06-R1` 最小测试补强；R1 仅修改既有三个测试文件及本任务/SDD 证据，未修改生产代码，未新增测试类名，未 commit，未发现 §19.7 停工条件。实现范围如下：

- 06A 在 `iot-device` 新增专用 authority API/DTO、ADR-018 `iot-sink` 服务签名校验、exact route validator、`@TenantIgnore` 有界查询（`deleted = 0`、`ORDER BY id ASC`、`LIMIT 2`）及 `NOT_FOUND/RESOLVED/AMBIGUOUS` provider；软删除、同租户/跨租户歧义、跨租户查询和稳定 HTTP 错误均有直接证据。
- 06B 在 `iot-sink` 完成 enum-derived canonical Topic parser、consumer-local signed Feign adapter、三方 ingress guard、固定十类拒绝分类、subscriber 委托和 fail-closed 配置；未修改 Inbox/Schema/Projection/Outbox/ACK/DDL/ACL，也未启用生产 subscriber。
- 06A 精确冻结集：`TelemetryDeviceAuthorityServiceContractTest` 8 项、`TelemetryDeviceAuthorityInternalApiContractTest` 5 项、`TelemetryDeviceAuthorityPostgresIntegrationTest` 1 项，合计 **14/14**，Failures=0、Errors=0、Skipped=0；其中 1 项真实 PostgreSQL fixture 通过，凭据仅从单次 Maven 进程环境注入且未回显。R1 直接覆盖缺 Header、独立有效 key 的错 service、错 key、过期、重放、错签名、query 篡改；验证 auth-before-route/service、非法 route 未调用 mapper；并直接断言 401/400/500/503 稳定 HTTP/R code 与 DTO 不含 Device/候选列表。
- 06B 精确冻结集：`TelemetryRouteContractTest` 5 项、`TelemetryUpstreamTopicParserContractTest` 5 项、`TelemetryDeviceAuthorityClientAdapterTest` 4 项、`CenterTelemetryIngressHandlerTest` 6 项、`CenterMqttInboxSubscriberContractTest` 2 项、`TelemetryInboxAutoConfigurationTest` 4 项，合计 **26/26**，Failures=0、Errors=0、Skipped=0；测试 reactor 为 28 个模块。R1 记录 authority 调用次数，证明 Topic/Envelope/device/tenant 前置拒绝不调用 authority，并覆盖合法但错误 product 经 authority `NotFound` 后精确返回 `NOT_FOUND`。
- §19.9 的 `clean compile` 命令本轮复跑一次，在 Windows 删除 `iot-sink-biz/target/classes` 时遭文件锁阻断，未出现源码编译错误；随后同目标的非 clean `compile -DskipTests` 完成 **34/34** reactor，`BUILD SUCCESS`。该环境性 clean 阻断交由 Sol 复核，不冒充 clean 通过。
- 静态门禁：禁止 `LIMIT 1`/旧 iot-sink DeviceMapper/`getDeviceIgnoreTenant` 扫描 0 命中；Topic `/telemetry/**`、`/telemetry/#`、硬编码 `/iot/` 扫描 0 命中；Mapper 查询仅有一处 authority select，含 `deleted = 0`、`ORDER BY id ASC`、`LIMIT 2`；`git diff --check` 通过。
- §19.9 受保护 25 文件交付复算：`COUNT=25`，`MANIFEST_SHA=3D557A19BD394FDCC062AAF645478CF6C87003F4A987E54786A8F30C05C8CD47`。`git diff --name-only` 展示的 outbox 既有改动属于前序 LC02-04C/05 脏工作区，本轮未清理、覆盖或回退。

未执行且仍保持 OPEN：真实 MQTT/EMQX ACL、生产激活、V009/DDL/共享迁移、资源压测、Linux PTY/锁互操作、Windows 发布资格、现场验证，以及 `LC02-06-RUNTIME-01`。本节状态仅为 **待 Sol 复核**；不得据此宣告 `LC02-06 COMPLETE / SOL-ACCEPTED`。

### 19.12 `LC02-06` Sol 最终复核与接受（2026-08-22）

Sol 依据《EasyAIoT 项目开发宪法》v1.6.0、《平台功能计划》v1.5.0、ADR-017 1.0.0 与 ADR-018 1.0.0 独立审查生产 diff、白名单、调用顺序、认证边界和拒绝分类。首轮复核仅发现直接测试证据不足，遂冻结并交 Luna Max 完成 R1；R1 只补强既有测试和交付记录，没有修改生产代码。最终未发现双基线冲突、越界实现或需要重新冻结的生产缺陷。

Sol 独立复跑结果如下：

- iot-device 精确冻结集 **14/14**，Failures=0、Errors=0、Skipped=0；33 个 reactor 模块全部成功，其中真实 PostgreSQL fixture **1/1**，证明全局唯一、跨租户歧义和软删除语义；凭据仅注入单次 Maven 进程，未写入仓库或输出；
- iot-sink 精确冻结集 **26/26**，Failures=0、Errors=0、Skipped=0；28 个 reactor 模块全部成功。两端合计 **40/40**；
- Windows 主机执行 §19.9 `clean compile` 时，前 33 个模块成功，最后清理 `iot-sink-biz/target/classes` 被外部进程文件锁阻断，未出现源码编译错误；随后在一次性 Linux Docker 容器内以只读源码复制、排除既有 `target` 的方式独立执行同目标 `clean compile`，**34/34** reactor 全部 `BUILD SUCCESS`，容器按 `--rm` 自动清理。该证据关闭本包源码 clean compile 门禁，但不替代 Windows 发布资格；
- 禁止 `LIMIT 1`、旧 iot-sink `DeviceMapper/getDeviceIgnoreTenant` 和旧/硬编码 Topic 扫描均为 0 命中；唯一 authority 查询保持 `deleted = 0`、`ORDER BY id ASC`、`LIMIT 2`；`git diff --check` 通过；
- 受保护 25 文件复算仍为 `3D557A19BD394FDCC062AAF645478CF6C87003F4A987E54786A8F30C05C8CD47`，与冻结摘要精确一致，前序脏工作区未被清理、覆盖或回退。

据此，`LC02-06` 状态转为 **COMPLETE / SOL-ACCEPTED**，当前实现授权归零。真实 MQTT/EMQX ACL、生产激活、V009/DDL/共享迁移、资源压测、Linux PTY/锁互操作、Windows 发布资格、现场验证及 `LC02-06-RUNTIME-01` 继续保持 OPEN。本次授权不延伸至后继包；下一步只能由 Sol 先细化并冻结 `LC02-07` 的 V009 候选 DDL、迁移/回滚、中文 COMMENT、真实 PostgreSQL fixture 和验收命令，经决策所有者再次独立授权后才可交 GPT-5.6 Luna（max reasoning）实现与测试，`LC02-08`～`LC02-10` 继续锁定。
