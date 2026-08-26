# M1-LC-02：遥测 Topic 与产品路由身份收口任务单

> 状态：Implemented / Verified-Local（`LC02-01A` / `LC02-01A-R1` / `LC02-04A` / `LC02-04B` / `LC02-04C-1` / `LC02-04C-2` / `LC02-05` / `LC02-06` / `LC02-07` / `LC02-08` / `LC02-09-R1` / `LC02-10` / `LC02-10-R1` / `LC02-10-R2` / `LC02-10-R3` / `LC02-10-R4` / `LC02-10-R5` / `LC02-10-R6` COMPLETE / SOL-ACCEPTED）
> 版本：1.12.1
> 日期：2026-08-26
> 架构负责人：GPT-5.6 Sol
> 实现执行者：GPT-5.6 Luna（max reasoning，LC02-08 核心实现、LC02-09 首批生产增量、LC02-10-R2 单 literal 与 LC02-10-R4 standalone POSIX fixture 修订）/ GPT-5.6 Sol（R1/R2、LC02-09 原白名单接管、真实容器编排、独立验收及经 owner 授权接管 §24.5、§26.3～§26.5、§27.3～§27.6，并复核 R4 完整模块 skip 归类）
> 当前交付状态：经 owner 授权与 Sol 独立复核，R6 已全部通过：R5 V009 **79/79** 证据受保护复用且未重跑；Inbox 单次编排顶层退出码 0、9 PASS/0 FAIL、八类 **50/50**；Java fail-closed **11/11**；真实隔离 EMQX **12/12**；Failures/Errors/Skipped、秘密禁出、全部固定/共享/P02保护与资源残留均为 0。`LC02-10` 及 R1～R6 与 M1-LC-02 已转 COMPLETE / SOL-ACCEPTED（Implemented / Verified-Local），详见 §30.6。该结论只关闭本地实现与隔离合同；正式 V009 落库、生产 MQTT broker/ACL/TLS 激活及运行期/发布/现场资格仍 OPEN / NOT APPROVED

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
- [ADR-017 遥测可靠链路 Topic 与产品路由身份收口](../../架构决策/电力运维云平台/ADR-017-遥测可靠链路Topic与产品路由身份收口.md)（Accepted 1.1.1）；
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
| LC02-05 | Claim 使用 canonical 上行 Topic并暴露路由集合 | COMPLETE / SOL-ACCEPTED（2026-08-21）：Topic 合同覆盖非法标识、解绑在途消息及旧 Topic 不可达 |
| LC02-06 | center Topic 与权威注册事实校验 | COMPLETE / SOL-ACCEPTED（2026-08-22）：40/40，真实 PostgreSQL Skipped=0，Linux 隔离 clean compile 34/34 |
| LC02-07 | V009 候选 DDL 与真实 PG 合同 | COMPLETE / SOL-ACCEPTED（2026-08-24）：PG 18.4 隔离合同 79/79，目标库未落 V009 |
| LC02-08 | Inbox 产品身份补全/碰撞合同 | COMPLETE / SOL-ACCEPTED（§21）：新增、重复、历史空值原子补全、跨产品碰撞与并发矩阵已完成 Verified-Local 验收 |
| LC02-09-R1 | EMQX real Topic 最小 ACL + Java 固定共享组 | COMPLETE / SOL-ACCEPTED（§23，2026-08-24）：Java 11/11、真实 EMQX 12/12、零 skip/残留；broker 强制身份/real filter/QoS/动作方向，Java 联网前固定共享组 |
| LC02-10 | 全模块回归、保护区扫描与文档回填 | STOPPED / NOT ACCEPTED（§24.10）：Sol 接管后 device 39/39 通过，sink 定向集 155 项出现 3 Errors，按冻结门禁停止后续阶段 |
| LC02-10-R1 | 构造期合同对齐、Linux Docker symlink 资格与完整重验 | IMPLEMENTED / DIRECT-TEST-PASSED / FULL-ACCEPTANCE-NOT-RUN（§25.8）：Sol 接管机械对齐，Linux 直接测试 5/5；等待 PG 凭据与完整重验授权 |

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

本节原候选命令中的 `TelemetryTopicContractTest` 与 `CenterMqttInboxTopicValidationTest` 已被后续冻结包拆解为实际合同类，不能再作为最终入口。`LC02-10` 的唯一最终验收入口为 §24.5；其同时覆盖定向 Java/SQLite、完整 `iot-sink-biz` 模块、真实 PostgreSQL V009、真实 Inbox JDBC、真实 EMQX、编译和保护区扫描。

真实 JDBC/EMQX 只允许连接由现有隔离脚本创建或管理的本地临时环境，凭据必须由进程环境显式注入；不得把密码写入任务单、源码、测试、Git diff 或证据报告。第 10 节所称“第 9 节通过”自 1.6.0 起等价于“§24.5 全部强制阶段通过”。

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

## 20. `LC02-07` V009 产品路由身份受控迁移冻结单（2026-08-24）

### 20.1 冻结依据、已核事实与单一目标

Sol 已重新读取并核对《EasyAIoT 项目开发宪法》v1.6.0（SHA-256 `76BF30903A5A62C957A75B57E24080AA7132DE6992D56C262EEAFBE7BE553C4D`）、《平台功能计划》v1.5.0（SHA-256 `F0E5734C8FADA6D0E4DAB98F7D12F58940077F9C60AA8DA42AC8EF45C6F69869`）、ADR-013 1.5.7、ADR-017 1.0.0、§5.2 与已接受的 `LC02-06`。本次只读核验本地 PostgreSQL 18.4 的 `iot-device20`，未执行 DDL/DML，确认：

1. `V008` history 为 `SUCCEEDED`，脚本 SHA-256 为 `693c0473386048567886b382c8c984ab98a267b7e7ce8659307b0d7395048469`；
2. `V010` history 为 `SUCCEEDED`，脚本 SHA-256 为 `08d809a4453e1e0efd16f29522f0682e3b9a10b20df4f04be4a93f7d200d6662`；
3. `iot_sink.telemetry_inbox.product_identification` 尚不存在，`telemetry_sample.quality/received_at_ms` 已存在；
4. runner 当前顺序止于 `V008 → V010`，尚无 `V009`；
5. `.scripts/postgresql/iot-device10.sql` 是 `init-databases.sh` 首装权威全量基线，但尚无 `iot_sink`、`V008/V010` 结构或对应 migration history，已形成既有安装基线漂移。

本包唯一目标是：形成一个可审计、可重复验证、默认 dry-run 的 `V009` additive expand 迁移，并在隔离临时 PostgreSQL 中证明依赖、hash、幂等、回滚拒绝、中文 COMMENT 与新装机基线一致性。允许同时修复与本对象直接相关的既有全量基线漂移，即把 `V008 + V010 + V009` 的最终 `iot_sink` 结构及三条精确 migration history seed 同步到 `iot-device10.sql`；不得借机同步或改写其他迁移、表、数据或安装逻辑。

本包不修改 Java、MQTT、Inbox 写入、投影、查询、ACK、ACL、配置开关或部署容器；不在 `iot-device20`、共享库或生产库执行 `V009/U009`。`LC02-08` 才负责 Java Inbox 持久化接线与非空写入，当前继续锁定。

### 20.2 V009 正向 DDL 与失败合同

正式资产固定为 `V009__telemetry_inbox_product_identity.sql`。除前置断言外，业务 DDL 只能是：

```sql
ALTER TABLE iot_sink.telemetry_inbox
    ADD COLUMN product_identification VARCHAR(128);

COMMENT ON COLUMN iot_sink.telemetry_inbox.product_identification IS
    '经 MQTT Topic、认证主体与载荷设备身份校验后持久化的产品路由标识；禁止由站点或属性推断';
```

冻结约束如下：

1. 列必须是可空 `VARCHAR(128)`；禁止 `DEFAULT`、`NOT NULL`、CHECK/UNIQUE/FK、索引、trigger、数据回填及任何 `INSERT/UPDATE/DELETE`。既有行自然得到 `NULL`，原值、行数、约束和索引保持不变。
2. SQL 资产不得自带 `BEGIN/COMMIT`，由 ADR-013 runner 的事务步骤统一包裹；不得使用 `IF NOT EXISTS` 静默接管漂移。
3. V009 资产必须在 ALTER 前断言 `iot_sink.telemetry_inbox` 存在且目标列不存在。无 V009 history 但列已存在时以稳定标记 `V009_PREEXISTING_COLUMN_WITHOUT_HISTORY` 失败，禁止把“兼容列”自动登记为成功。
4. runner 执行 V009 前必须要求 history 中 `V008`、`V010` 均为 `SUCCEEDED` 且 hash 分别精确等于 §20.1；任一缺失、状态错误或 hash 不符，输出稳定标记 `DEPENDENCY_NOT_SATISFIED V009`，按非重试 validation 失败退出码 2，且不得执行任何 V009 DDL。该检查必须同时覆盖全链 apply 与 `--step V009`。
5. runner 顺序固定为 `M05 → M15 → M16 → V001 → … → V008 → V010 → V009`，不得按版本号重排，不得修改 V008/V010 资产、已有 history 或 hash。V009 首次成功写入自身实际 SHA-256；重复执行只能返回既有 `STEP_SKIPPED V009` 语义，不得重写 history。
6. 任何脚本 SHA 漂移继续由 ADR-013 全链 validation 在业务 DDL 前以 `HASH_MISMATCH V009` 拒绝。审批、备份、advisory lock、连接/statement timeout、默认 dry-run 和两阶段“先全校验后执行”语义全部继承 ADR-013，不得弱化。

### 20.3 U009 回滚候选与生产回滚边界

`U009__telemetry_inbox_product_identity.sql` 只作为独立评审/临时库演练资产，不接入 runner 的 `uninstall` 清单。它必须：

1. 自带单事务，先断言列存在；
2. 发现任意 `product_identification IS NOT NULL` 时以稳定标记 `U009_NON_NULL_DATA_PRESENT` 拒绝并完整回滚；
3. 仅执行 `DROP COLUMN product_identification RESTRICT`，显式禁止 `CASCADE`；存在 view/index/constraint 等依赖时由 PostgreSQL fail-closed；
4. 不读取、插入、更新或删除 `schema_migration_history`，不触碰其他列、表、schema 或业务数据。

生产/共享环境的正常应用回滚是关闭 `LC02-08` 新写链并保留 nullable 列，不执行 U009。U009 仅允许在本包隔离临时库演练，或未来另有“列全 NULL + 依赖为空 + 备份 + history 治理”专项审批时使用；本包不授权该专项。U009 执行后 history 与结构会有意不一致，因此临时库必须销毁，不得继续作为可迁移环境使用。

### 20.4 runner、COMMENT 门禁与全量安装基线

runner 允许的机械改动仅包括：新增 `V009_SQL` 环境覆盖/路径分派、`APPLY_STEPS` 尾部 V009、事务步骤分类、帮助/usage/dry-run 文本、§20.2 依赖门禁及 README/env 示例。`check_ddl_comments.sql` 只增加对 `iot_sink.telemetry_inbox.product_identification` 的 schema-qualified 检查；列缺失、注释缺失/空白或不含中文均必须返回一行，合法时返回零行。不得借本包补写 V008 其他历史列的 COMMENT。

全量安装基线同步固定为：

1. 只在 `.scripts/postgresql/iot-device10.sql` 增加与 runner `history_bootstrap` 同形的 `public.schema_migration_history` 表/sequence/index/中文 COMMENT、`iot_sink` 最终结构，以及 `V008/V010/V009` 三条 `SUCCEEDED` seed；三条 seed 的 script hash 必须分别等于当前 V008、当前 V010 和交付时 V009 实际 SHA-256，`executed_by` 固定为 `full-install-baseline`，evidence 明示 `LC02-07` 首装基线。
2. 不 seed `M05～V007`，不伪造其执行事实；首次在新装库运行全链时，这些步骤仍正常执行，而 V008/V010/V009 因精确 history 被跳过。
3. 除 `public.schema_migration_history` 与 `iot_sink` 相关结构/三条控制元数据外，`iot-device10.sql` 不得出现任何 diff；禁止加入 telemetry 业务样例数据。
4. 从全量基线恢复的临时库，其 `iot_sink` schema-only catalog 必须与另一临时库依次执行 V008→V010→V009 后一致；history seed 必须通过 runner 全链 hash validation，且分别执行 `--step V008/V010/V009` 都只能 skip。

若无法在不改安装器、不篡改历史迁移资产的前提下满足上述“结构一致 + 新装 runner 可继续”合同，立即停工交 Sol，不得选择只改 V009、只改 dump 或自动采用既有列。

### 20.5 精确文件白名单

Luna 获得后续独立授权后，只允许新增或修改：

- `.doc/技术设计/电力运维云平台/assets/td005-migration/V009__telemetry_inbox_product_identity.sql`（新增）；
- `.doc/技术设计/电力运维云平台/assets/td005-migration/U009__telemetry_inbox_product_identity.sql`（新增）；
- `.doc/技术设计/电力运维云平台/assets/td005-migration/V009落库窗口申请单-20260824.md`（新增，状态必须为 OPEN / NOT APPROVED）；
- `.scripts/postgresql/td005-migration/td005_migration.sh`；
- `.scripts/postgresql/td005-migration/check_ddl_comments.sql`；
- `.scripts/postgresql/td005-migration/env.example`；
- `.scripts/postgresql/td005-migration/README.md`；
- `.scripts/postgresql/td005-migration/tests/lc02_v009_contract.sh`（新增）；
- `.scripts/postgresql/iot-device10.sql`；
- 本任务单与 `M1-SDD进度与续作入口.md`，仅更新交付证据和状态。

`V008__iot_sink_telemetry_inbox.sql`、`V010__telemetry_quality.sql`、其他 V/U/M 资产、precheck、安装器、Docker Compose、Java/前端代码与其他 SQL dump 全部为只读保护区。不得新增依赖、凭据文件或仓库内备份。实现者不得 commit。

### 20.6 隔离真实 PostgreSQL fixture 与验收矩阵

`lc02_v009_contract.sh` 必须默认拒绝运行；仅当 `LC02_V009_PG_ENABLED=true` 且显式给出容器/连接、用户、密码与唯一测试前缀时启用。不得内置密码或复用目标业务库名。脚本必须以 `trap` 清理两个以上唯一临时数据库、临时 SQL 副本和仓库外临时备份；开始/结束均证明无该前缀数据库残留，绝不 drop `iot-device20` 或任何非本次创建数据库。

直接证据至少覆盖：

1. fresh 临时库直接 V009：缺 V008/V010，退出 2、`DEPENDENCY_NOT_SATISFIED V009`、无列、无 V009 history；仅 V008 后仍因缺 V010 同样拒绝；
2. V008→V010→V009：每步带测试审批 ID、非空仓库外 backup、`--yes --skip-precheck` 成功；顺序、实际 hash、history/evidence、中文 COMMENT 和列类型/长度/nullable 精确；
3. V009 前插入至少一条含非默认状态/租约/错误/时间字段的 Inbox 行，前后对旧列逐值、行数、约束和索引做快照；迁移后唯一变化是新列存在且旧行值为 NULL；
4. 重复 V009 只 skip；复制并篡改 V009 asset 后 hash mismatch 且零 DDL；预建同名兼容列但无 history 时稳定拒绝且不 adoption；
5. U009 在全 NULL、无依赖时只删目标列；存在非 NULL 行时 `U009_NON_NULL_DATA_PRESENT` 且列/数据保留；存在依赖对象时 RESTRICT 失败且完整回滚；三种演练均不改 history；
6. COMMENT gate 正例零行；临时移除注释或构造非中文注释时精确返回该 schema/table/column，恢复后归零；
7. 将 `iot-device10.sql` 中固定数据库名仅在临时副本替换为唯一测试库后完整恢复；验证三条 seed/hash、V008/V010/V009 runner skip，并与迁移库的 `iot_sink` schema-only 结构指纹一致；
8. dry-run 清单显示 `V008 → V010 → V009`，V009 hash 与 seed 一致；静态扫描证明 V009 无数据 DML/DEFAULT/NOT NULL/约束/索引，U009 无 CASCADE/history DML，保护资产无 diff；
9. 任一断言失败时退出非零并执行清理。日志不得回显密码、完整连接串或业务 payload。

冻结验收命令为：

```bash
bash -n .scripts/postgresql/td005-migration/td005_migration.sh
bash -n .scripts/postgresql/td005-migration/tests/lc02_v009_contract.sh

.scripts/postgresql/td005-migration/td005_migration.sh dry-run --db iot-device20

LC02_V009_PG_ENABLED=true \
PG_CONTAINER=postgres-server \
PG_USER=postgres \
PG_PASSWORD="$LC02_V009_PG_PASSWORD" \
LC02_V009_DB_PREFIX=lc02_v009_contract \
bash .scripts/postgresql/td005-migration/tests/lc02_v009_contract.sh

git diff --exit-code -- \
  .doc/技术设计/电力运维云平台/assets/td005-migration/V008__iot_sink_telemetry_inbox.sql \
  .doc/技术设计/电力运维云平台/assets/td005-migration/V010__telemetry_quality.sql \
  .scripts/postgresql/td005-migration/precheck_runtime_profile.sql \
  .scripts/docker/init-databases.sh

git diff --check
```

dry-run 必须零数据库写；真实合同必须全部通过且无 skip，临时数据库/备份按测试约定清理。`iot-device20` 只允许在 Sol 复核时执行与 §20.1 等价的 read-only 基线查询，不得成为 fixture 或迁移目标。

### 20.7 停工条件、交付与授权状态

出现以下任一情形必须立即停止并交回 Sol：需要修改 V008/V010 或其 history/hash；需要 `DEFAULT/NOT NULL`、回填、索引或约束；需要采用既有列；需要改 Java/安装器/precheck/其他 dump；需要在共享/目标库执行 DDL；无法通过 RESTRICT 回滚；无法让全量基线与迁移链同时成立；测试只能依赖真实业务数据或不能可靠清理；发现凭据、用户改动或白名单外冲突。

Luna 交付必须列出实际 V009 SHA-256、变更文件、每个 fixture 场景/断言数、数据库版本、临时库和备份清理证据、未执行项与风险；状态只能是“待 Sol 复核”，不得自行标记 COMPLETE / SOL-ACCEPTED，不得 commit。

本节现为 **FROZEN / NOT-YET-AUTHORIZED**。本次用户“继续下一步”只授权 GPT-5.6 Sol 完成 §20 冻结，不授权 GPT-5.6 Luna 修改文件，也不授权任何 DDL、共享库/目标库写入或落库窗口。下一步须由决策所有者再次独立授权 GPT-5.6 Luna（max reasoning）严格执行 §20.1～§20.7；`LC02-08`～`LC02-10`、正式 V009 落库、真实 MQTT/ACL、资源压测、Linux PTY/锁互操作、Windows 发布资格和现场验证继续锁定/OPEN。

### 20.8 独立实现授权与执行交接（2026-08-24）

决策所有者已在 §20 冻结后独立授权执行下一步。Sol 按 AGENTS.md 将白名单核心实现交给 GPT-5.6 Luna（max reasoning）：Luna 完成 V009/U009、runner V009 增链与依赖分类、COMMENT gate、README/env、首装全量基线及 OPEN 窗口申请单。首个 Luna 实例在强制读取阶段持续无产出；重试实例完成核心文件后又在 `lc02_v009_contract.sh` 编排阶段持续无响应。Sol 已明确报告该模型运行限制，没有静默替换为 Terra 或其他模型，随后按仓库规则接手缺失测试、修正与验收。

Sol 接手审查发现首装基线漏写 `schema_migration_history.id` 与 `telemetry_inbox.id` 两个主键，且 `ck_inbox_state` 使用 pg_dump 展开表达式会与 V008 原始约束形成 catalog 差异；已在 §20.5 白名单内补齐主键、改回与 runner/V008 同形的约束表达，并新增真实 PostgreSQL 自动合同脚本。未修改 V008、V010、precheck、`init-databases.sh`、Java/前端或其他 dump，未执行 commit。

### 20.9 Sol 最终复核与接受（2026-08-24）

结论：**接受，`LC02-07` 转为 COMPLETE / SOL-ACCEPTED。** Sol 依据双基线、ADR-013 1.5.7、ADR-017 1.0.0 与 §20 独立审查全部迁移 diff，并在 PostgreSQL `18.4 (Debian 18.4-1.pgdg13+1)` 执行最终自动合同：

- `bash -n`、runner dry-run 和真实隔离 PostgreSQL 合同全部通过；最终 `PASS_ASSERTIONS=79`，无 skip；
- fresh 缺依赖、仅 V008 缺 V010 均以退出码 2 和 `DEPENDENCY_NOT_SATISFIED V009` 拒绝，零 V009 列/history；
- V008→V010→V009 成功，既有非默认 Inbox 行除新增 NULL 列外逐值不变，约束/索引指纹不变；重复 V009 只 `STEP_SKIPPED`，脚本漂移 `HASH_MISMATCH`，无 history 的既有同名列稳定拒绝；
- COMMENT 正例、缺失反例与恢复通过；U009 全 NULL 成功、非 NULL 拒绝、依赖对象 RESTRICT 拒绝三组合同均保持 history；
- `iot-device10.sql` 完整恢复成功，三条 seed/hash、两个主键、V008/V010/V009 skip 均通过；`iot_sink` schema 与 migration history schema 分别和 runner 构建库逐字节归一化一致；
- runner 共生成 14 份非空仓库外临时备份，结束时临时数据库与备份全部删除，残留为 0；
- V009 实际 SHA-256 为 `48416787b7fc886cc3274be53f3a38c60f9a9dd93ca205e3f0311d54a8eafbde`；V008/V010/precheck/init 脚本 hash 前后精确保持，`git diff --check` 通过，仅有工作树既有换行符提示。

对 `iot-device20` 的最终核验严格使用 `BEGIN READ ONLY`：V008/V010 仍为原 `SUCCEEDED` hash，V009 history 数为 0，`product_identification` 列数为 0，LC02 临时库残留为 0。因此本次只完成迁移资产与临时库资格，**没有正式落库**；`V009落库窗口申请单-20260824.md` 继续为 OPEN / NOT APPROVED，不能复用本次实现授权执行目标库 DDL。

当前实现授权归零。下一步只能由 Sol 先细化并冻结 `LC02-08` 的 Inbox 产品身份写入、历史 NULL 补全/重复碰撞与并发合同，经决策所有者再次独立授权后再交 GPT-5.6 Luna（max reasoning）或在 Luna 不可用时按 AGENTS.md 明示处置；`LC02-09`～`LC02-10` 与正式 V009 落库继续锁定。

## 21. `LC02-08` Inbox 产品身份接线、历史 NULL 补全与并发合同冻结单（2026-08-24）

### 21.1 冻结依据、当前事实与单一目标

Sol 已重新读取并核对《EasyAIoT 项目开发宪法》v1.6.0（SHA-256 `76BF30903A5A62C957A75B57E24080AA7132DE6992D56C262EEAFBE7BE553C4D`）、《平台功能计划》v1.5.0（SHA-256 `F0E5734C8FADA6D0E4DAB98F7D12F58940077F9C60AA8DA42AC8EF45C6F69869`）、ADR-017 1.0.0、已接受的 `LC02-06`/`LC02-07`、M1-LC-01 接收结果合同和当前 Java/SQL 实现。已确认：

1. `CenterTelemetryIngressHandler` 已按 Topic → Envelope device/tenant → 权威注册 tenant 的顺序完成三方校验，但当前解码器在校验完成前直接构造不含产品身份的 `InboxEnvelope`；
2. `JdbcTelemetryInbox` 当前新写 SQL、既有行查询和六字段重复判定均未读写 `product_identification`；唯一作用域仍为 `(tenant_id, message_id)`，每条输入仍使用独立 `REQUIRES_NEW` 事务；
3. `InboxEnvelope` 只有一个生产构造点，位于 `TelemetryEnvelopeDecoder`；仓库内其余直接构造点均为五个既有测试文件，未发现跨服务或反射构造调用；
4. V009 候选资产已由 LC02-07 验收，但 `iot-device20`/共享/生产数据库仍没有 V009 history 和目标列，正式落库窗口仍为 OPEN / NOT APPROVED；
5. Envelope V1、canonical bytes/SHA-256、`TelemetryInboxPort.receiveEnvelopes(List<InboxEnvelope>)` 和 `InboxReceiveResult.Batch/Item/Status` 均为受保护既有合同。

本包唯一目标是：把已通过 LC02-06 三方校验的 Topic 产品身份作为受信任路由元数据附加到 `InboxEnvelope` 并写入 V009 列；对历史 `product_identification IS NULL` 的完全同一消息做一次原子补全；把产品身份纳入重复/碰撞判定；用隔离真实 PostgreSQL 证明新写、历史补全和同键并发不会覆盖既有事实。本包不发布 ACK、不写拒绝审计、不执行 V009 正式落库、不修改投影或 Store。

### 21.2 API、解码与受信任产品身份合同

`InboxEnvelope` 必须新增且只新增一个必填 record component：

```java
String productIdentification
```

组件位置固定在 `tenantId` 之后、`siteCode` 之前。构造时必须复用 `TelemetryRoute.validateProductIdentification(...)` 做原值校验；禁止 trim、大小写折叠、Unicode 归一化、默认值和从 `siteCode`/`deviceIdentification`/`propertyCode` 推断。不得保留或新增允许生产调用方省略产品身份的旧构造器、工厂或 null 兼容路径；五个既有测试文件只做显式 fixture 参数迁移。

`TelemetryEnvelopeDecoder` 必须停止在未完成 Topic/authority 校验时构造最终 `InboxEnvelope`。冻结结构为：解码器返回一个包内私有、不可变的 route-free decoded value，保留现有 Envelope V1 字段、原始 payload 和对原始 payload 的 SHA-256；`CenterTelemetryIngressHandler` 继续按 LC02-06 固定顺序完成全部 Topic、device、tenant 和 authority 校验后，才以 `route.productIdentification()` 构造最终 `InboxEnvelope` 并调用 Inbox。实现可把转换方法内聚在 decoded value 内，但不得新增第二个 public Inbox 端口或跨线程/全局 side channel。

强制语义：

- 产品身份只能来自已解析且已通过权威注册校验的 Topic route；Envelope V1 中即使出现同名未知字段也不得读取、比较或覆盖该 route 值；
- 新 component 不进入 Envelope V1 JSON，不重序列化 payload，不改变 canonical bytes、`contentSha256`、message/request/tenant/site/device/property、采集时间、sequence、source 或 configVersion；
- Topic、Envelope device、Envelope tenant 或 authority 任一拒绝时仍必须在 Inbox 调用前结束，不能借历史补全产生数据库写；
- `TelemetryInboxPort` 方法签名和 `InboxReceiveResult` 三状态/逐条顺序/持久化时间语义完全不变；数据库异常继续向上抛出，不伪造 `DUPLICATE`、`COLLISION` 或 security rejection；
- `TelemetrySample.fromInboxEnvelope`、投影编排器和两个 Store adapter 不消费产品身份，本包不得修改。

### 21.3 JDBC 新写、历史 NULL 原子补全与碰撞状态机

`JdbcTelemetryInbox` 的 INSERT 必须显式写入非空 `product_identification`，并继续使用 `(tenant_id, message_id) ON CONFLICT DO NOTHING`。数据库没有 V009 列时必须让 PostgreSQL 异常原样进入既有失败边界；禁止检测缺列后降级为旧 INSERT，禁止本包自动执行 DDL。

唯一冲突后固定执行以下状态机，所有步骤都在该输入自己的既有 `REQUIRES_NEW` 事务内：

1. 先执行单条、条件式 `UPDATE ... SET product_identification = ? ... RETURNING received_at_ms`。WHERE 必须同时满足 `(tenant_id, message_id)`、`product_identification IS NULL`，以及 M1-LC-01 六个既有身份字段与本次输入完全一致：`content_sha256`、`request_id`、`message_id_wire`、`site_code`、`device_identification`、`property_code`。可空字段必须使用 PostgreSQL null-safe 相等语义（`IS NOT DISTINCT FROM`），禁止只比较 hash；
2. UPDATE 命中一行时只允许把该行产品身份从 NULL 写为本次受信任产品身份，不更新 `received_at_ms`、`updated_at_ms`、payload、hash、六个身份字段、投影状态/租约/错误/次数或任何其他列；结果返回 `DUPLICATE`，`persistedAtMs` 必须是原始首次入库时间；
3. UPDATE 未命中时查询既有行，查询字段必须包含六个既有身份字段、`product_identification` 和原始 `received_at_ms`。六字段和非空产品身份均与输入精确相同才返回 `DUPLICATE`；产品身份或任一既有字段不同均返回 `MESSAGE_ID_COLLISION`，且 `persistedAtMs=null`；
4. 历史 NULL 行若六字段任一不同，必须 collision 且保持 NULL，禁止“先补产品再判断”；既有非空产品身份与 Topic 产品不同，必须 collision，禁止覆盖、追加第二行或把产品差异降级为 duplicate；
5. 新行成功 INSERT 仍返回 `ACCEPTED_DURABLE`；同批次输入顺序、逐消息独立事务、前项已提交后续失败、碰撞脱敏日志和跨租户相同 messageId 语义全部继承 M1-LC-01，不得改变。

不得引入表锁、全局 JVM 锁、业务轮询、`SERIALIZABLE` 全局升级或额外唯一键。依赖 PostgreSQL unique-conflict 等待与条件 UPDATE 的行锁/WHERE 重检查收敛同键并发；若实际实现不能在默认 `READ COMMITTED` 下证明下列矩阵，必须停工交 Sol。

### 21.4 并发与失败的确定性矩阵

真实 PostgreSQL 直接测试必须用 barrier/latch 同时释放独立线程和独立数据库连接，并设置有限超时；不得以顺序调用伪造并发。冻结结果如下：

| ID | 初始事实与并发输入 | 必须结果 |
|---|---|---|
| LC02-08-01 | 新 key，单条合法产品 | `ACCEPTED_DURABLE`；列为输入产品；canonical bytes/hash 与输入逐字节一致 |
| LC02-08-02 | 既有非空产品，六字段与产品均相同 | `DUPLICATE`；首次 `received_at_ms` 不变 |
| LC02-08-03 | 既有非空产品，六字段相同但产品不同 | `MESSAGE_ID_COLLISION`；原产品/全行不变、行数 1 |
| LC02-08-04 | 历史 NULL，六字段全部相同，单次有效 replay | `DUPLICATE`；只补产品；`received_at_ms`/`updated_at_ms`/其余列不变 |
| LC02-08-05 | 历史 NULL，hash/request/site/device/property 任一不同 | 每个差异均 collision；产品仍为 NULL、原行不变 |
| LC02-08-06 | 历史 NULL，两个相同产品的同键完全重复并发 | 两个结果均 `DUPLICATE`；最终一个非空产品、行数 1、时间/其他列不变 |
| LC02-08-07 | 历史 NULL，两个不同产品的同键完全重复并发 | 恰好一个 `DUPLICATE`、一个 collision；胜者产品允许非确定，但只能写一次且不得被败者覆盖 |
| LC02-08-08 | 不存在 key，两个同产品/同内容输入并发 | 恰好一个 accepted、一个 duplicate；行数 1，persistedAt 一致 |
| LC02-08-09 | 不存在 key，两个不同产品但其余身份相同输入并发 | 恰好一个 accepted、一个 collision；胜者产品稳定、行数 1 |
| LC02-08-10 | 同 messageId 跨租户 | 各租户独立 accepted/duplicate，不互相补全或碰撞 |
| LC02-08-11 | 缺 V009 列或数据库失败 | 抛数据库异常；不返回成功/终态结果，不执行兼容 INSERT 或 DDL |
| LC02-08-12 | handler 合法三方匹配与伪造 payload 产品字段 | Inbox 只收到 Topic 产品；payload bytes/hash 不变；authority/Inbox 各调用一次 |
| LC02-08-13 | handler 任一前置拒绝/authority 异常 | Inbox 调用 0 次，历史 NULL 行无写入 |
| LC02-08-14 | API 产品 null/空白/非法 topic-level 字符/超 128 code points | 构造 fail-fast；合法边界值原样保留 |
| LC02-08-15 | M1-LC-01 回归 | 空/null、混合批次、六字段 collision、失败透传、Store/投影直接回归全部保持 |

所有并发 future 必须在冻结超时内结束；超时、死锁、额外行、非预期异常或允许两个不同产品都返回 duplicate 均为失败。测试不得依赖线程调度来断言具体哪个产品获胜，只断言状态多重集和最终产品属于唯一胜者。

### 21.5 精确文件白名单

决策所有者后续独立授权后，GPT-5.6 Luna（max reasoning）只允许新增或修改：

**生产文件（仅 4 个）：**

1. `DEVICE/iot-sink/iot-sink-api/src/main/java/com/basiclab/iot/sink/telemetry/inbox/InboxEnvelope.java`；
2. `DEVICE/iot-sink/iot-sink-biz/src/main/java/com/basiclab/iot/sink/telemetry/inbox/route/TelemetryEnvelopeDecoder.java`；
3. `DEVICE/iot-sink/iot-sink-biz/src/main/java/com/basiclab/iot/sink/telemetry/inbox/route/CenterTelemetryIngressHandler.java`；
4. `DEVICE/iot-sink/iot-sink-biz/src/main/java/com/basiclab/iot/sink/telemetry/inbox/jdbc/JdbcTelemetryInbox.java`。

**直接测试与隔离编排：**

5. `DEVICE/iot-sink/iot-sink-biz/src/test/java/com/basiclab/iot/sink/telemetry/inbox/InboxEnvelopeProductIdentityContractTest.java`（可新增）；
6. `DEVICE/iot-sink/iot-sink-biz/src/test/java/com/basiclab/iot/sink/telemetry/inbox/route/CenterTelemetryIngressHandlerTest.java`；
7. `DEVICE/iot-sink/iot-sink-biz/src/test/java/com/basiclab/iot/sink/telemetry/inbox/jdbc/JdbcTelemetryInboxContractTest.java`；
8. `DEVICE/iot-sink/iot-sink-biz/src/test/java/com/basiclab/iot/sink/telemetry/inbox/jdbc/JdbcTelemetryInboxProductIdentityContractTest.java`（可新增）；
9. `DEVICE/iot-sink/iot-sink-biz/src/test/java/com/basiclab/iot/sink/telemetry/inbox/jdbc/JdbcTelemetryInboxFailureContractTest.java`；
10. `DEVICE/iot-sink/iot-sink-biz/src/test/java/com/basiclab/iot/sink/telemetry/store/TelemetryStoreBatchContractTest.java`（只允许机械补 fixture 产品参数）；
11. `DEVICE/iot-sink/iot-sink-biz/src/test/java/com/basiclab/iot/sink/telemetry/store/tdengine/TDengineTelemetryStoreContractTest.java`（只允许机械补 fixture 产品参数）；
12. `.scripts/postgresql/td005-migration/tests/lc02_08_inbox_product_contract.sh`（可新增）；
13. 本任务单与 `M1-SDD进度与续作入口.md`（只更新实现/验收证据与状态）。
14. `DEVICE/iot-sink/iot-sink-biz/src/test/java/com/basiclab/iot/sink/telemetry/inbox/TelemetryInboxAutoConfigurationTest.java`（仅 LC02-08-R2：机械对齐当前 `telemetryDeviceAuthoritySigner(Environment, ...)` 生产签名并加入回归；不得修改生产装配）。

`TelemetryInboxPort`、`InboxReceiveResult`、`TelemetryRoute`、Topic parser/enums、authority port/adapter/auth、subscriber/auto-configuration、`TelemetrySample`、投影编排器、两个 Store adapter、所有 DDL/migration/runner/dump/Compose/配置、其他模块和前端全部为只读保护区。不得新增 POM 依赖、public API、配置开关、后台任务或数据库对象；实现者不得 commit。

LC02-07 迁移保护集共 13 文件，按“路径升序 + TAB + 文件小写 SHA-256 + LF，末尾再加 LF”形成的冻结聚合 SHA-256 为 `2ab39079fb84d8ba42b056cbbdc25685fc0461f82eda762565f620eada662adb`。实现开始和交付时必须复算并一致；其中 V009 必须仍为 `48416787b7fc886cc3274be53f3a38c60f9a9dd93ca205e3f0311d54a8eafbde`。保护集为 V008/V009/U009/V010/窗口申请单、runner/COMMENT/env/README/LC02-07 脚本、`iot-device10.sql`、precheck 和 `init-databases.sh`；任何漂移立即停工。

### 21.6 隔离 PostgreSQL fixture 与验收命令

新增编排脚本必须默认拒绝运行；仅当 `LC02_08_PG_ENABLED=true` 且显式提供容器、用户、密码、唯一数据库前缀与 JDBC host/port 时启用。脚本必须：

1. 只创建带唯一前缀的临时数据库，开始前拒绝同前缀残留；以当前 V008→V010→V009 资产建立 schema，禁止使用 `iot-device20`、共享/生产数据库或业务数据；
2. 向 Maven 子进程注入专用 `LC02_08_PG_URL/USERNAME/PASSWORD`；真实 JDBC 测试在 enabled=true 时缺少参数或连接失败必须 fail，不得 assumption skip；默认未启用时允许 Java 集成类明确标记 `NOT_RUN_LOCAL_ENV`，但冻结编排内 Skipped 必须为 0；
3. 运行 §21.4 全矩阵及 M1-LC-01/Store 直接回归，检查 Surefire Failures/Errors/Skipped 均为 0；测试前后只操作保留测试租户和唯一 messageId 前缀；
4. 用 `trap` 删除本次临时数据库、临时 SQL/报告副本；结束证明该前缀数据库残留为 0。不得打印密码、完整 JDBC URL、payload 或凭据；不得启动、重启或修改既有 EasyAIoT 服务容器；
5. 对“缺 V009 列”另建独立临时库或在可销毁副本上验证，绝不能从验收主库 drop 正在使用的列，也不能修改 migration history。

冻结命令为：

```bash
bash -n .scripts/postgresql/td005-migration/tests/lc02_08_inbox_product_contract.sh

LC02_08_PG_ENABLED=true \
PG_CONTAINER=postgres-server \
PG_USER=postgres \
PG_PASSWORD="$LC02_08_PG_PASSWORD" \
LC02_08_DB_PREFIX=lc02_08_inbox_contract \
LC02_08_JDBC_HOST=localhost \
LC02_08_JDBC_PORT=5432 \
bash .scripts/postgresql/td005-migration/tests/lc02_08_inbox_product_contract.sh

mvn -f DEVICE/pom.xml -pl iot-sink/iot-sink-biz -am compile -DskipTests

rg -n "productIdentification|product_identification" \
  DEVICE/iot-sink/iot-sink-api/src/main/java/com/basiclab/iot/sink/telemetry/inbox \
  DEVICE/iot-sink/iot-sink-biz/src/main/java/com/basiclab/iot/sink/telemetry/inbox

git diff --check
```

编排脚本内 Maven 冻结类至少包含 `InboxEnvelopeProductIdentityContractTest`、`CenterTelemetryIngressHandlerTest`、`JdbcTelemetryInboxContractTest`、`JdbcTelemetryInboxProductIdentityContractTest`、`JdbcTelemetryInboxFailureContractTest`、`InboxReceiveResultContractTest` 和 `TelemetryStoreBatchContractTest`。`TDengineTelemetryStoreContractTest` 本包只允许机械补 fixture 产品参数并由 test compilation 覆盖；它依赖本包范围外的真实 TDengine，不进入 LC02-08 PostgreSQL 编排，也不得以 skip 冒充回归通过。所有新/改 Java 生产文件必须由完整 reactor compile 覆盖。

### 21.7 禁止范围、停工条件与部署顺序

本包不得：执行或修改 V009/U009；把 V009 窗口标为批准；修改 Envelope V1/canonical/hash；修改 Topic/auth/authority/ACK/拒绝审计/投影/Store；从 payload、站点、属性、设备或默认配置推断产品；保留无产品新写；用查询后无条件 UPDATE、upsert 覆盖产品或第二条记录解决冲突；连接现场/生产数据；执行 MQTT/ACL、Linux PTY/锁互操作、资源压测、Windows 发布资格或现场验证。

出现以下任一情形必须立即停止并交回 Sol：需要白名单外生产文件或 Schema/索引/约束；需要改变 M1-LC-01 状态/时间/事务语义；不能在 `READ_COMMITTED` 下稳定实现 §21.4；需要从 Envelope 读取产品；必须先在 `iot-device20`/共享库落 V009 才能测试；隔离数据库不能可靠清理；LC02-07 保护摘要漂移；发现白名单文件存在无法安全合并的用户改动、凭据或双基线冲突。

正式部署顺序固定为：未来单独批准 V009 窗口并完成目标环境迁移/核验 → 部署 LC02-08 Java → 启用/验证现有中心入口。不得先部署依赖列的新代码，不得把本地隔离合同视为 V009 正式落库批准。本包完成后只可形成 `Verified-Local` 证据；真实 MQTT/EMQX ACL、`LC02-06-RUNTIME-01`、资源/稳定性、Linux PTY/锁互操作、Windows 发布资格和现场验证继续 OPEN。

### 21.8 交付要求与当前授权状态

Luna 交付必须列出：实际变更文件、迁移保护摘要前后值、每个 §21.4 场景与断言数、PostgreSQL 版本、Failures/Errors/Skipped、并发超时、临时数据库清理、compile/边界扫描结果、未执行项与风险。状态只能写“待 Sol 复核”，不得自行标记 COMPLETE / SOL-ACCEPTED，不得 commit。Sol 必须独立审查 SQL 状态机、白名单和受保护摘要，并重跑 §21.6 后再决定接受、最小 R1 或停工重冻结。

冻结时状态为 **FROZEN / NOT-YET-AUTHORIZED**：当时只授权 GPT-5.6 Sol 完成 LC02-08 架构收敛与冻结，不授权实现、验收脚本或任何数据库 DDL。后续独立实现授权、交付与 Sol 验收见 §21.9～§21.10；该授权始终不包含 `LC02-09`～`LC02-10`、V009 目标落库与全部运行期资格。

### 21.9 独立授权、Luna 交付与最小修订记录（2026-08-24）

决策所有者已独立授权 GPT-5.6 Luna（max reasoning）执行 §21 实现与测试。Luna 在 §21.5 原白名单内完成四个生产文件、两个新增合同类、既有 fixture 迁移和隔离 PostgreSQL 编排脚本的核心实现；生产编译已通过。两次 Luna Max 执行回合随后分别在无工具/文件进展和补齐测试后无响应，Sol 已向决策所有者明示运行限制并中止挂起回合，没有静默替换为其他模型。

Sol 审查核心实现后保留其生产设计，并完成两个最小修订：

1. **LC02-08-R1**：修正跨租户测试标识、旧 JDBC 测试对 `iot-device20` 的隐式默认、Maven `test-compile`、指定测试类执行证明、零 skip、唯一前缀清理与残留断言，并将编排的空白检查限定在本任务授权文件；
2. **LC02-08-R2**：完整 reactor test compilation 暴露既有 `TelemetryInboxAutoConfigurationTest` 仍调用旧 signer 签名。Sol 将 §21.5 最小扩展一个测试文件，仅用 `StandardEnvironment` 对齐当前生产签名并加入回归；未修改生产装配、依赖、配置或公开合同。

本轮最终实际生产变更仍严格为 §21.5 的四个文件：产品 component 在 `tenantId` 后注入并复用 Topic 级校验；decoder 保持 route-free 和 payload/hash 原样；handler 在全部三方校验后只注入 Topic 产品；JDBC 使用 INSERT 非空产品、历史 NULL 六字段条件 UPDATE/RETURNING、产品参与 duplicate/collision。未读取 payload 产品字段，未新增 DDL fallback、锁、ACK、拒绝审计、投影或 Store 生产修改。

### 21.10 Sol 独立验收结论（2026-08-24）

结论：**接受，`LC02-08` 转为 COMPLETE / SOL-ACCEPTED（Verified-Local）。** Sol 独立审查 SQL 状态机、事务边界、并发收敛、受信任身份来源、精确白名单及既有脏工作区，并对最终脚本完整复跑 §21.6：

- PostgreSQL `18.4 (Debian 18.4-1.pgdg13+1)` 两套隔离夹具按 `V008 → V010 → V009` 与缺失 V009 构建；§21.4 的 15 项矩阵全部覆盖；八个指定直接合同类合计 **49/49**，Failures=0、Errors=0、Skipped=0；并发 barrier/future 的单段超时均为 10 秒且全部正常结束；
- 另行纯 JVM 回归六类 **25/25**，Failures=0、Errors=0、Skipped=0；受影响完整 reactor 共 28 个模块 `test-compile` 为 `BUILD SUCCESS`；产品身份边界扫描和 LC02-08 scoped `git diff --check` 通过；
- 临时数据库开始前残留为 0，成功路径显式清理与 EXIT safety-net 后残留仍为 0；未启动、重启或修改既有 EasyAIoT 服务，未连接 `iot-device20`；
- LC02-07 的 13 文件保护集交付复算仍为 `2ab39079fb84d8ba42b056cbbdc25685fc0461f82eda762565f620eada662adb`，V009 仍为 `48416787b7fc886cc3274be53f3a38c60f9a9dd93ca205e3f0311d54a8eafbde`，没有迁移资产漂移；
- 未执行真实 MQTT/EMQX ACL、资源/稳定性压测、Linux PTY/锁互操作、Windows 发布资格或现场验证，这些运行期资格继续 OPEN。

本地代码虽然已接受，但正式部署前置仍固定为“另行批准 V009 目标窗口并迁移/核验 → 部署 LC02-08 Java → 启用/验证中心入口”。本次授权不批准 V009 正式落库，不外延至 `LC02-09`～`LC02-10`。当前实现授权归零；下一步由 Sol 细化并冻结 `LC02-09` 的 EMQX ACL/中心身份协同有界任务单，待决策所有者再次独立授权后再交 GPT-5.6 Luna（max reasoning）实现与测试。

## 22. `LC02-09` EMQX 最小 ACL、中心固定共享订阅与隔离合同冻结单（2026-08-24）

### 22.1 冻结依据、实际配置事实与架构结论

Sol 已重新读取并核对《EasyAIoT 项目开发宪法》v1.6.0（SHA-256 `76BF30903A5A62C957A75B57E24080AA7132DE6992D56C262EEAFBE7BE553C4D`）、《平台功能计划》v1.5.0（SHA-256 `F0E5734C8FADA6D0E4DAB98F7D12F58940077F9C60AA8DA42AC8EF45C6F69869`）、ADR-017 1.0.0、已接受的 LC02-05/06/08、当前 EMQX 5.8.7 容器只读配置和 MQTT Java/NODE 接线。确认以下事实：

1. 当前 `emqx-server` 为 EMQX 5.8.7，`authentication=[]`，`authorization.no_match=allow`，默认文件 ACL 以 `{allow, all}.` 收尾；当前实例不能作为“最小 ACL 已生效”的证据，也不得在本包中原地修改；
2. ADR-017 已固定 collector 只能向自己的 `/iot/{product}/{device}/property/upstream/report` 发布，center 只能订阅 canonical 上行集合；broker 负责“已认证主体 → Topic”，应用层继续负责“Topic → 权威注册 product/device → tenant/envelope”，两层不得互相替代；
3. `TelemetryUpstreamTopicParser.sharedSubscriptionFilter()` 当前实际返回普通 `/iot/+/+/property/upstream/report`，而非 `$share/<group>/...`；日志虽声称 shared subscription，代码尚未形成共享组负载均衡合同；
4. center 配置已有 username/password，但 enabled 时只校验 Topic 与 ADR-018 authority key，空 MQTT 身份不会在装配前 fail-closed；
5. collector 配置也已有 username/password，但 NODE 当前只把 `EASYAIOT_BROKER_SECRET_FILE=/run/secrets/easyaiot-broker` 注入容器，Java 端没有读取该文件。一个 collector workload 还可包含多个设备，不能在没有账号签发/ACL 动态配置合同的情况下把“单账号等于单设备”静默当成生产事实；
6. 现有 `SqliteOutboxAutoConfiguration` 在 collector MQTT enabled 时还会创建旧 `/telemetry/ack/#` subscriber，而 M1-LC-02 明确不实现 ACK。该路径、collector 凭据消费和生产 ACL 签发必须留待独立运行期包，不得混入本地 ACL 合同；
7. EMQX 官方合同要求共享订阅使用 `$share/{group}/{topic-filter}`；文件 ACL 按顺序匹配，生产安全姿态应为 `authorization.no_match=deny` 并以 `{deny, all}.` 兜底。EMQX 5.8.7 是否能在授权层精确区分指定共享组、普通订阅和其他共享组，必须由本包真实测试裁决，不能只靠配置文本推断。

据此，本包唯一目标收敛为两件事：

- 在 Java 侧把 center 的唯一订阅固定为共享组 `easyaiot-center-inbox-v1`，并在 MQTT 身份或精确 filter 缺失时于创建网络客户端前失败；
- 在不触碰既有服务的隔离 EMQX 5.8.7 中，用三个非 superuser 主体和真实 MQTT 连接证明最小认证/ACL矩阵。

本包完成后最多形成 `Verified-Local`。它不等于生产 broker 已加固，不关闭账号签发、凭据文件消费/轮换、TLS、集群 ACL 分发、灰度激活、稳定性/资源、Linux PTY、Windows 发布资格或现场门禁。

### 22.2 Topic、共享组与 Java fail-closed 合同

canonical 发布 Topic 保持唯一且逐字节不变：

```text
/iot/{productIdentification}/{deviceIdentification}/property/upstream/report
```

基础订阅 filter 固定为：

```text
/iot/+/+/property/upstream/report
```

center 唯一共享订阅 filter 固定由“`$share/` + 固定组名 + `/` + 基础 filter”机械派生。由于基础 filter 本身以 `/` 开头，最终精确值必须是：

```text
$share/easyaiot-center-inbox-v1//iot/+/+/property/upstream/report
```

不得 trim、折叠双斜杠、允许配置共享组、改用 `$queue`、接受普通基础 filter、`/iot/#`、`#`、其他共享组或旧 `/telemetry/**`。`TelemetryUpstreamTopicParser` 必须明确区分“基础 filter”和“center 共享 filter”：实现可重命名当前误导方法并同步唯一调用方，但不得新增第二套 Topic 模板；两者都必须从 `IotDeviceTopicEnum.PROPERTY_UPSTREAM_REPORT` 派生。`parse(...)` 仍只解析 broker 交付的真实发布 Topic，不得接受 `$share` filter 或 wildcard 作为消息 Topic。

当 `easyaiot.telemetry.mqtt.enabled=true` 时，`TelemetryMqttProperties.validateForEnabledSubscriber()` 必须在 `CenterMqttInboxSubscriber` 构造/联网前同时证明：

- topicFilter 与上述 center 共享 filter 逐字节相等；
- clientId、username、password、ADR-018 authorityKeyId 均非 null/非空白；
- clientId/username 不含 `/`、`+`、`#`、NUL 或控制字符；password 只检查存在性，不回显、不 trim、不规范化；
- 失败抛稳定且不含实际身份/凭据值的错误码：Topic 为 `TELEMETRY_MQTT_TOPIC_FILTER_INVALID`，clientId 为 `TELEMETRY_MQTT_CLIENT_ID_INVALID`，username/password 为 `TELEMETRY_MQTT_CREDENTIALS_MISSING`，authority key 沿用 `SERVICE_AUTH_KEY_UNKNOWN`。

`application.yaml` 的默认 Topic 必须同步为唯一共享 filter，否则显式启用时会被 YAML 旧值覆盖并自相矛盾。中心默认 enabled 状态、QoS 1、clean session false、authority 调用、Inbox/Store/投影和日志脱敏语义不变；不得在本包引入自动重连、ACK、TLS 或新的配置开关。

### 22.3 隔离 broker 主体与最小 ACL 合同

fixture 只允许以下三个固定、非敏感 username 标识；其密码每次在仓库外临时目录随机生成，`is_superuser=false`，不得写入仓库、命令行、Surefire XML、控制台或 broker 日志：

| 主体 | 唯一允许动作 | 必须拒绝 |
|---|---|---|
| `lc02-collector-a` | QoS 1、retain=false 发布 `/iot/product-a/device-a/property/upstream/report` | 所有订阅；其他 product/device；ACK/legacy/系统 Topic；QoS 0/2；retain=true |
| `lc02-collector-b` | QoS 1、retain=false 发布 `/iot/product-b/device-b/property/upstream/report` | 与 A 对称；不得发布 A 的 Topic |
| `lc02-center-inbox` | QoS 1 订阅唯一固定共享 filter | 所有发布；普通基础 filter；其他共享组；`/iot/#`、`#`、ACK/legacy、`$SYS/#`；QoS 0/2 |

隔离 broker 必须使用已存在于本机的精确 `emqx/emqx:5.8.7` 镜像，启用 built-in database password authentication，bootstrap CSV 只存在于临时目录并只读挂载；ACL 使用 file authorizer，`authorization.no_match=deny`、`deny_action=disconnect`，移除 localhost/IP/superuser 绕过并以 `{deny, all}.` 收尾。ACL 中只可出现上述固定 username 和两条固定设备 Topic；不得用 `${username}`/`${clientid}` 拼接产品或设备身份，不得出现 `{allow, all}`、`#` 放行或 `is_superuser=true`。

对 center 的授权结果必须在 MQTT 行为层精确成立：固定共享 filter 成功，而同一账号对普通基础 filter和另一组 `$share/lc02-other/...` 都失败。实现可根据 EMQX 5.8.7 实际授权检查格式选择正确的 ACL `eq` 表达，但不得通过应用侧跳过失败用例、日志字符串、Dashboard/API 后验改 ACL 或放宽 broker 断言来伪造精确组权限。

### 22.4 真实 MQTT 确定性矩阵

新增 Java 集成测试必须使用现有 Vert.x MQTT 依赖和真实 TCP 连接；不得新增 POM 依赖、mock broker 或只解析配置文本。每个拒绝场景使用新 client，有限超时，断言 broker 的 connect/suback/disconnect/消息行为；不能把客户端本地拒绝非法包当成 broker ACL 证据。

| ID | 场景 | 必须结果 |
|---|---|---|
| LC02-09-01 | 匿名、未知账号、三个账号的错误密码 | CONNECT 均失败；无订阅/发布副作用 |
| LC02-09-02 | A/B 正确凭据、唯一 clientId | CONNECT 成功，且均非 superuser |
| LC02-09-03 | A/B 各发布自己的 canonical Topic，QoS 1、retain=false | 发布被接受；center 共享组收到原 Topic/原 payload，各消息在组内合计恰好一次 |
| LC02-09-04 | A 发布 B/sibling product/device Topic；B 发布 A Topic | 全部拒绝/断连；center 收到 0 条 |
| LC02-09-05 | A/B 发布 ACK、`/telemetry/**`、`$SYS/**`、QoS 0/2 或 retain=true | 全部拒绝/断连；无 retained/下游消息 |
| LC02-09-06 | A/B 订阅自身 Topic、canonical 基础 filter、`#` | 全部拒绝；不得收到消息 |
| LC02-09-07 | 两个 center client 用不同 clientId 订阅固定共享 filter | 两者 SUBACK 成功；多条 A/B 消息在组内总数等于发布数、每条只出现一次，不要求具体实例分配 |
| LC02-09-08 | center 订阅普通基础 filter、其他共享组、`/iot/#`、`#`、ACK、legacy、`$SYS/#`、QoS 0/2 | 全部拒绝；不能建立残留订阅 |
| LC02-09-09 | center 发布任意 canonical/ACK/系统 Topic | 全部拒绝；其他 client 收到 0 条 |
| LC02-09-10 | 未命中主体/Topic、localhost 容器来源与重连新 clientId | 仍走 deny；无 IP/默认 allow 绕过 |
| LC02-09-11 | Java Topic 派生与 enabled 配置 | 基础/共享值精确；旧/broad/其他组与缺 clientId/username/password/key 均在联网前 fail-closed |
| LC02-09-12 | 禁止边界与清理 | ACK/collector/NODE/主 Compose 无 diff；临时容器、网络、目录、凭据和 Surefire 敏感输出残留为 0 |

至少一条成功 payload 使用 UTF-8 非 ASCII 字符和原始字节断言；不得把 payload、密码、authorization header 或完整连接 URI写入日志。`deny_action=disconnect` 导致的连接关闭是预期拒绝证据，不得被吞成 skip。

### 22.5 精确文件白名单

决策所有者后续独立授权后，GPT-5.6 Luna（max reasoning）只允许新增或修改：

**生产与配置文件（仅 3 个）：**

1. `DEVICE/iot-sink/iot-sink-biz/src/main/java/com/basiclab/iot/sink/telemetry/inbox/route/TelemetryUpstreamTopicParser.java`；
2. `DEVICE/iot-sink/iot-sink-biz/src/main/java/com/basiclab/iot/sink/telemetry/inbox/mqtt/TelemetryMqttProperties.java`；
3. `DEVICE/iot-sink/iot-sink-biz/src/main/resources/application.yaml`（只改 center 默认 Topic；不得写身份或凭据）。

**直接测试与隔离资产：**

4. `DEVICE/iot-sink/iot-sink-biz/src/test/java/com/basiclab/iot/sink/telemetry/inbox/route/TelemetryUpstreamTopicParserContractTest.java`；
5. `DEVICE/iot-sink/iot-sink-biz/src/test/java/com/basiclab/iot/sink/telemetry/inbox/TelemetryInboxAutoConfigurationTest.java`；
6. `DEVICE/iot-sink/iot-sink-biz/src/test/java/com/basiclab/iot/sink/telemetry/inbox/mqtt/EmqxTelemetryAclIntegrationTest.java`（可新增）；
7. `.scripts/emqx/lc02-09/emqx.conf`（可新增，无 secret）；
8. `.scripts/emqx/lc02-09/acl.conf`（可新增，无 secret）；
9. `.scripts/emqx/lc02-09/lc02_09_emqx_acl_contract.sh`（可新增）；
10. 本任务单与 `M1-SDD进度与续作入口.md`（只更新授权、交付、验收证据与状态）。

`CenterMqttInboxSubscriber`、`TelemetryInboxAutoConfiguration` 生产类、`CollectorMqttProperties`、`SqliteOutboxAutoConfiguration`、所有 ACK 类、`IotEmqxAuthEventHandler`、`/mqtt/auth`/`/mqtt/acl`、NODE、iot-node、iot-device、POM、主/cluster Compose、现有 EMQX 配置、V009/数据库、Envelope/Inbox/authority/Store/投影和前端均为只读保护区。实现者不得 commit。

### 22.6 隔离编排与验收命令

脚本必须在任意运行前验证 Docker daemon 可用、运行的是 Linux containers、本机已有精确 5.8.7 镜像，且不会命中既有 container/network 名；任一不满足必须以非零状态和稳定 `ENVIRONMENT_UNAVAILABLE` 结束，不得 pull 镜像、skip 测试或改用其他版本。

每次运行使用高熵唯一后缀创建仓库外临时目录、container、network 和随机 loopback host port。脚本生成三个随机密码的 bootstrap CSV，文件权限尽可能收紧并只读挂载；启动后必须通过 broker 内部版本/认证/授权只读检查证明实际加载 5.8.7、三个非 superuser、`no_match=deny`、`deny_action=disconnect` 和目标 ACL，再运行：

```bash
cd DEVICE
mvn -pl iot-sink/iot-sink-biz -am \
  -Dmaven.test.skip=false -DskipTests=false \
  -Dtest=TelemetryUpstreamTopicParserContractTest,TelemetryInboxAutoConfigurationTest \
  -Dsurefire.failIfNoSpecifiedTests=false test

LC02_09_EMQX_ENABLED=true \
bash ../.scripts/emqx/lc02-09/lc02_09_emqx_acl_contract.sh
```

第二条命令由脚本把 broker host/port、三组临时凭据作为单次 Maven 进程环境或 system property 注入，并只运行 `EmqxTelemetryAclIntegrationTest`。测试类在普通 reactor 中可以条件禁用，但当 `LC02_09_EMQX_ENABLED=true` 时，缺参数、连接失败、超时、用例未发现或任何 skip 都必须失败。脚本必须解析 Surefire 结果并证明该类 `Tests run > 0` 且 Failures/Errors/Skipped 均为 0。

EXIT trap 无论成功失败都必须删除本次 container、network、临时目录、bootstrap 凭据和本轮 Surefire 敏感临时物；不得删除既有容器/网络/镜像/volume。结束后按唯一前缀断言残留为 0，并运行白名单 scoped `git diff --check`、凭据值/`allow all`/superuser/旧 Topic/ACK 生产 diff 扫描。脚本交付须报告 EMQX image ID/version、通过场景数、Tests/Failures/Errors/Skipped、端口、拒绝方式和清理结果，但不得报告密码。

### 22.7 禁止范围、停工条件与运行期后继

本包不得：修改/重启现有 `emqx-server` 或 EasyAIoT 服务；连接现场/生产 broker；打开主 Compose 匿名/ACL；实现账号签发、凭据轮换、secret 文件读取、HTTP authz、动态 ACL、TLS、ACK；给 center/collector superuser；用 IP/localhost 规则放行；新增依赖；执行 V009 正式落库；执行压测、Linux PTY/锁互操作、Windows 发布资格或现场验证。

出现以下任一情形必须立即停止并交回 Sol：

- EMQX 5.8.7 不能在 broker 行为层同时做到“固定共享组允许、普通基础 filter/其他共享组拒绝”；
- 需要白名单外生产文件、POM、主 Compose、现有 broker、NODE/collector/ACK 或数据库变更；
- 需要把密码、hash、token 或真实账号写入仓库/日志才能测试；
- 真实拒绝只能靠客户端本地校验、sleep/重试概率或跳过用例证明；
- 隔离容器/网络/凭据不能确定清理，或发现与现有脏工作区无法安全合并；
- 双基线、ADR-017 或当前 Topic/主体事实发生冲突。

本包之后仍有独立 `LC02-09-RUNTIME-01`：设计多设备 workload 的账号/ACL 签发模型，接通 `EASYAIOT_BROKER_SECRET_FILE` 的 Java 消费与轮换，选择 file ACL、HTTP authn 返回 ACL 或专用 authorizer，完成 TLS/集群分发、secret provider、主 Compose、灰度/回滚及真实 collector→broker→center 验证。该运行期包必须由 Sol 另行设计/ADR 决策并单独批准，不能由 Luna 在本包顺手实现。

### 22.8 当前授权状态与后继门禁

本节现为 **FROZEN / NOT-YET-AUTHORIZED**。本次用户“继续下一步”只授权 GPT-5.6 Sol 完成 LC02-09 的事实核验、接口合同、主体/ACL矩阵、精确白名单、隔离编排和验收命令冻结；没有授权 GPT-5.6 Luna 修改生产代码、测试或脚本，也没有授权修改/重启任何 broker、服务或执行生产凭据/ACL 激活。

下一步须由决策所有者再次独立授权 GPT-5.6 Luna（max reasoning）严格执行 §22.1～§22.7；完成后只可交付“待 Sol 复核”，由 Sol 独立复跑并决定接受、最小修订或停工重冻结。`LC02-10`、正式 V009 落库、`LC02-09-RUNTIME-01` 与全部运行期资格继续锁定/OPEN。

### 22.9 独立实现授权（2026-08-24）

决策所有者已明确授权 GPT-5.6 Luna（max reasoning）执行 `LC02-09` §22 实现与测试。当前唯一实现范围为 §22.5 的精确白名单；Luna 必须保护工作区既有未提交改动，特别是 `TelemetryInboxAutoConfigurationTest.java` 的 LC02-08 既有增量，只允许加入 LC02-09 所需局部修改。实现者不得 commit，不得修改/重启现有 EMQX 或 EasyAIoT 服务；遇到 §22.7 任一停工条件必须立即停止并交回 Sol。

该授权不包含 `LC02-10`、`LC02-09-RUNTIME-01`、正式 V009 落库、主/cluster Compose、生产账号/ACL/凭据/TLS、NODE/collector/ACK/POM/数据库或运行期资格。Luna 完成后状态只能为“待 Sol 复核”，不能自行标记 COMPLETE / SOL-ACCEPTED。

### 22.10 实现交付、真实 EMQX 裁决与停工结论（2026-08-24）

决策所有者授权后，两个 GPT-5.6 Luna（max reasoning）执行回合均受运行时限制，未形成可独立验收的完整交付；其中延迟落盘的首批增量仅涉及 `TelemetryUpstreamTopicParser` 与 `TelemetryMqttProperties`。Sol 已明确披露限制并在同一 §22.5 白名单内接管，未替换或冒充 Luna，完成 Java 默认配置、直接测试、真实 MQTT 集成测试和隔离 EMQX 资产。当前已形成但尚未接受的实现事实如下：

1. Java center filter 已机械派生并固定为 `$share/easyaiot-center-inbox-v1//iot/+/+/property/upstream/report`；enabled 时对精确 filter、clientId、username/password 和 authority key fail-closed；
2. `TelemetryUpstreamTopicParserContractTest` 与 `TelemetryInboxAutoConfigurationTest` 共 **11/11** 通过，Failures/Errors/Skipped 均为 0，28 模块 reactor `BUILD SUCCESS`；
3. 隔离脚本使用本地精确 `emqx/emqx:5.8.7` 镜像、built-in database 随机临时凭据、三个 `is_superuser=false` 主体、file authorizer、`no_match=deny`、`deny_action=disconnect`、随机 loopback 端口和退出清理；多轮失败后 container/network/临时凭据残留均为 **0**；
4. 镜像内置 `emqx_authz_rule` 抽象代码证明 5.8.7 富动作语法为 `{subscribe, [{qos, 1}]}`，脚本已按该真实格式修正；WSL 无 JDK 时仅通过 `WSLENV` 把单次临时变量安全传给 Windows Maven/JDK，密码未写入仓库或测试命令行；
5. 最终真实 MQTT 矩阵发现并执行 **12** 项，结果为 **10 通过、2 失败、0 error、0 skipped**。失败均为固定共享 filter 的允许场景（LC02-09-03、LC02-09-07）：EMQX 脱敏日志明确记录 center 实际授权检查 Topic 为 `/iot/+/+/property/upstream/report`，而非客户端发送的 `$share/easyaiot-center-inbox-v1//iot/+/+/property/upstream/report`，随后按 `source: file, action: SUBSCRIBE(Q1)` 拒绝；
6. EMQX 上游接口事实与本地行为一致：共享订阅进入 authorize 阶段时只传递 real topic，组名前缀不属于 file authorizer 的匹配输入。因此若把 ACL 改为允许基础 filter，同一 center 主体的普通订阅和其他共享组也会命中同一许可，无法满足 §22.3/§22.4 的三分结果。

该结果精确触发 §22.7 第一项停工条件。Sol 结论为 **STOPPED / BLOCKED-EMQX-SHARED-GROUP-AUTHZ / NOT ACCEPTED**：不得把 ACL 放宽为基础 filter、不得跳过两项失败、不得标记 `Verified-Local`，当前实现授权归零，`LC02-10` 不解锁。现有白名单增量和失败测试作为可复现证据保留，未 commit。

下一步只能由 Sol 形成独立 `LC02-09-R1` 决策包，并由决策所有者在以下方向中选择后重新冻结：

- 接受“broker 仅按 center 主体 + real filter 授权，固定共享组由 Java fail-closed 保证”的责任重划分，并同步修订 ADR-017/§22 验收语义；或
- 引入能读取原始 SUBSCRIBE filter 的 EMQX `client.subscribe` hook/plugin/exhook，在 broker 行为层强制固定组，并为新增组件、部署和失败模式另行设计白名单与运行期合同。

在该决策完成并重新独立授权前，不得继续修改 ACL、主 Compose、现有 broker、POM、NODE/collector/ACK/数据库，也不得进入 `LC02-09-RUNTIME-01` 或 `LC02-10`。

## 23. `LC02-09-R1` center real Topic ACL 与 Java 固定共享组责任重划分冻结单（2026-08-24）

### 23.1 决策与冻结依据

决策所有者已选择并接受 §22.10 的第一种方向：**Broker 只约束 center 身份和 real Topic，共享组由 Java fail-closed 保证**。Sol 已重新读取并核对《EasyAIoT 项目开发宪法》v1.6.0（SHA-256 `76BF30903A5A62C957A75B57E24080AA7132DE6992D56C262EEAFBE7BE553C4D`）、《平台功能计划》v1.5.0（SHA-256 `F0E5734C8FADA6D0E4DAB98F7D12F58940077F9C60AA8DA42AC8EF45C6F69869`）、ADR-017 v1.1.0、§22 的实现和真实 EMQX 5.8.7 失败证据。未发现双基线冲突。

本决策不扩大 center 的业务数据域：center 本就消费全部 canonical 上行遥测。它只承认 EMQX 5.8.7 file authorizer 的真实输入不包含 `$share/{group}/`，并把“固定组负载均衡”从 broker 数据授权维度改为受控 Java 客户端的消费拓扑合同。Broker 仍必须强制身份、real filter、QoS 和动作方向；Java 仍必须在联网前逐字节固定共享 filter。两层均不得省略或互相冒充。

### 23.2 最终责任合同

| 层 | 必须允许 | 必须拒绝/失败 | 不得声称 |
|---|---|---|---|
| EMQX authentication | 三个 fixture 正确凭据主体 | 匿名、未知主体、错误密码 | Java 配置校验可替代身份认证 |
| collector file ACL | A/B 各自 exact canonical Topic，QoS1、retain=false 发布 | 所有订阅、跨设备/产品、ACK/legacy/系统 Topic、QoS0/2、retain=true | workload 多设备生产签发已解决 |
| center file ACL | `lc02-center-inbox` 以 QoS1 订阅精确 real filter `/iot/+/+/property/upstream/report` | 所有发布；其他 real filter，包括 `/iot/#`、`#`、ACK、legacy、`$SYS/#`；QoS0/2 | file ACL 能识别 `$share` 组名 |
| center Java | 唯一 `$share/easyaiot-center-inbox-v1//iot/+/+/property/upstream/report` | 普通 real filter、其他共享组、`$queue`、broad/legacy filter，以及缺 clientId/username/password/key；全部在联网前失败 | 被盗凭据无法由其他 MQTT 客户端绕过 Java |
| center ingress | canonical Topic、权威 product/device/tenant 与 Envelope 一致 | 任一身份、租户、签名或 payload 合同不一致 | broker ACL 可替代权威注册校验 |

center 凭据必须视为独占服务凭据。凭据持有者绕过 Java 建立普通或其他共享组订阅时，不会取得 center 授权域之外的 Topic，也不能发布，但会取得额外遥测副本并增加 broker 负载；该行为属于运行期安全事件，不得描述为安全等价。TLS/mTLS、secret 文件消费与轮换、连接/异常订阅监控和生产网络隔离继续属于 `LC02-09-RUNTIME-01`，不因本地 R1 通过而关闭。

### 23.3 R1 唯一实现增量

R1 只允许在已保留的 §22 增量上做以下最小修订：

1. `acl.conf` 的 center allow 规则从 `$share/...` 精确字符串改为 QoS1 的 exact real filter `/iot/+/+/property/upstream/report`；collector 两条 exact publish、最终 `{deny, all}.`、`no_match=deny`、`deny_action=disconnect` 和非 superuser 合同不变；
2. 真实 EMQX 测试把原“普通/其他共享组必须由 broker 拒绝”改为显式能力画像：同一 center 主体对固定共享组、普通 real filter、其他共享组均获得 QoS1 SUBACK，因为三者进入 file authorizer 的输入相同；画像订阅建立后必须立即正常断开，不发布消息、不留残余 session；
3. 同一测试仍必须证明 center 对 `/iot/#`、`#`、ACK、legacy、`$SYS/#`、QoS0/2 和所有 publish 被拒绝；两个固定共享组 center client 的组内恰好一次负载均衡仍必须成功；
4. Java 两个直接合同保持 **11/11**：只允许固定共享 filter，普通/其他组/broad/缺身份或 key 在联网前 fail-closed。不得为了适配 broker 画像而放宽 `TelemetryMqttProperties` 或默认 YAML；
5. 隔离脚本增加静态断言：center ACL 必须含 exact real filter、不得含 `$share` allow、不得出现 `{allow, all}`、superuser/IP bypass 或仓库凭据；最终必须解析目标类 **12/12** 且 Failures/Errors/Skipped 均为 0。

### 23.4 修订后的 12 项确定性矩阵

| ID | 场景 | R1 必须结果 |
|---|---|---|
| LC02-09-01 | 匿名、未知账号、错误密码 | CONNECT 均失败 |
| LC02-09-02 | A/B/center 正确临时凭据 | CONNECT 成功，均非 superuser |
| LC02-09-03 | A/B exact QoS1 non-retained 发布，center 固定共享组消费 | 原 Topic/原 payload 到达，消息合计恰好一次 |
| LC02-09-04 | collector 跨设备/产品发布 | 全部拒绝/断连，无消息 |
| LC02-09-05 | collector ACK/legacy/系统、QoS0/2、retain=true | 全部拒绝/断连，无副作用 |
| LC02-09-06 | collector 任意订阅 | 全部拒绝 |
| LC02-09-07 | 两个 center client 加入固定共享组 | 均 SUBACK；多消息组内不重不漏 |
| LC02-09-08 | center 固定共享组、普通 real filter、其他共享组能力画像；随后尝试其他 real filter/QoS | 前三者 QoS1 SUBACK 后立即清理；`/iot/#`、`#`、ACK、legacy、`$SYS/#`、QoS0/2 全部拒绝 |
| LC02-09-09 | center 发布 canonical/ACK/系统 Topic | 全部拒绝 |
| LC02-09-10 | 未命中主体/Topic、localhost 来源、重连新 clientId | 仍默认拒绝，无 IP/clientId 绕过 |
| LC02-09-11 | Java 基础/共享派生和 enabled 配置 | 固定值精确；普通/其他组/broad/缺身份或 key 联网前失败 |
| LC02-09-12 | 禁止边界、敏感输出和清理 | 保护区无 diff；容器/网络/目录/凭据残留 0 |

能力画像不是生产客户端授权建议，不得把“普通/其他组可 SUBACK”写成 Java 可配置能力。其唯一作用是固定 EMQX 5.8.7 的已接受限制，防止未来再次把共享组错误建模为 file ACL 输入。

### 23.5 精确文件白名单

决策所有者后续再次独立授权后，GPT-5.6 Luna（max reasoning）只允许修改：

1. `.scripts/emqx/lc02-09/acl.conf`；
2. `DEVICE/iot-sink/iot-sink-biz/src/test/java/com/basiclab/iot/sink/telemetry/inbox/mqtt/EmqxTelemetryAclIntegrationTest.java`；
3. `.scripts/emqx/lc02-09/lc02_09_emqx_acl_contract.sh`；
4. 本任务单与 `M1-SDD进度与续作入口.md`，仅追加授权、执行和验收证据。

§22.5 的三个生产/配置文件、两个 Java 直接测试、`emqx.conf` 均转为只读保护区；现有 broker、主/cluster Compose、NODE、collector/ACK、POM、数据库/V009、ADR、authority/Inbox/Store/投影和前端继续禁止修改。不得新增文件、依赖、服务、端口、插件或功能开关，不得 commit。

### 23.6 验收命令与接受标准

沿用 §22.6 两条命令，顺序固定为 Java fail-closed 直接合同在前、隔离真实 EMQX 在后：

```bash
cd DEVICE
mvn -pl iot-sink/iot-sink-biz -am \
  -Dmaven.test.skip=false -DskipTests=false \
  -Dtest=TelemetryUpstreamTopicParserContractTest,TelemetryInboxAutoConfigurationTest \
  -Dsurefire.failIfNoSpecifiedTests=false test

LC02_09_EMQX_ENABLED=true \
bash ../.scripts/emqx/lc02-09/lc02_09_emqx_acl_contract.sh
```

接受结果必须同时为：直接合同 **11/11**、真实 EMQX **12/12**，Failures/Errors/Skipped 全部为 0；image/version、随机端口、默认拒绝、能力画像、组内恰好一次、凭据扫描和零残留均有证据；scoped `git diff --check` 通过。普通/其他共享组画像不得替代 Java 负向合同，也不得计作 broker 安全拒绝。

### 23.7 停工条件与当前授权状态

出现以下任一情形立即停工交回 Sol：需要修改 Java 生产文件或放宽固定共享 filter；需要允许 center 订阅其他 real filter、发布或使用 QoS0/2；需要修改 collector ACL；需要 hook/plugin/exhook、POM、Compose、现有 broker、NODE/ACK/数据库；能力画像只能靠 skip/mock/日志字符串而非真实 SUBACK；凭据进入仓库/命令行/报告；隔离残留不能清零；或双基线/ADR-017 v1.1.0 冲突。

当前状态为 **FROZEN / NOT-YET-AUTHORIZED**。本次选择方案只授权 Sol 修订 ADR、冻结 R1 和同步续作入口，不等于授权 GPT-5.6 Luna 修改上述三个实现文件或运行测试。下一步必须由决策所有者再次明确授权“GPT-5.6 Luna Max 执行 LC02-09-R1 §23”，完成后交 Sol 独立复核；`LC02-10`、正式 broker/V009 激活和全部运行期资格继续锁定。

### 23.8 独立实现授权（2026-08-24）

决策所有者已明确授权 GPT-5.6 Luna（max reasoning）执行 `LC02-09-R1` §23 实现与测试。唯一可写范围为 §23.5 的 `acl.conf`、`EmqxTelemetryAclIntegrationTest.java`、隔离验收脚本，以及两份进度文档的执行证据；必须保护当前脏工作区和 §22 已通过的生产 Java/直接测试增量，不得 commit。

该授权不包含现有 broker、`emqx.conf`、主/cluster Compose、NODE、collector/ACK、POM、数据库/V009、ADR、authority/Inbox/Store/投影、`LC02-09-RUNTIME-01`、`LC02-10` 或任何运行期资格。Luna 遇到 §23.7 任一停工条件必须立即交回 Sol；交付状态只能为“待 Sol 复核”，不得自行标记 COMPLETE / SOL-ACCEPTED。

### 23.9 Luna 交付与 Sol 独立验收（2026-08-24）

首个 GPT-5.6 Luna（max reasoning）回合持续无消息、无落盘且不响应状态询问，Sol 明确披露运行时限制并中断；精简上下文的第二个 Luna Max 回合成功完成三个白名单文件的实现落盘，但在测试阶段再次持续无响应。Sol 未替换或冒充 Luna，而是在保留其实际实现的前提下接管代码审查和全部验收命令。

最终实现严格位于 §23.5 三文件白名单：

1. center file ACL 改为 `{subscribe, [{qos, 1}]}` + exact real filter `/iot/+/+/property/upstream/report`，collector 两条 exact publish 和最终 deny 不变；ACL 不含 `$share` allow、`allow all`、IP bypass、superuser 或凭据；
2. `LC02-09-08` 使用真实 SUBACK 固定 EMQX 5.8.7 能力画像：固定共享组、普通 real filter、其他共享组均由同一 center 主体以 QoS1 成功订阅并立即清理；同一测试继续拒绝 `/iot/#`、`#`、legacy、ACK、`$SYS/#` 和 QoS0/2；
3. 固定共享组的两个 center client 仍完成组内消息不重不漏，center 所有 publish、collector 跨设备/非法动作和默认绕过继续拒绝；
4. 隔离脚本新增 exact real filter 存在与 ACL 中 `$share` 禁止断言，保留本地精确 5.8.7 镜像、随机临时凭据、Windows/WSL Maven 桥接、敏感输出扫描、严格 Surefire 汇总和退出清理。

Sol 独立验收结果：

- Java fail-closed 直接合同：`TelemetryUpstreamTopicParserContractTest` **6/6**、`TelemetryInboxAutoConfigurationTest` **5/5**，合计 **11/11**，Failures/Errors/Skipped 均为 0；28 模块 reactor `BUILD SUCCESS`。首次 PowerShell 命令仅因 `-D` 参数被宿主误解析而未进入编译，使用停止解析模式后同一目标完整通过，不计作代码失败；
- 真实 MQTT 隔离合同：本地 `emqx/emqx:5.8.7`，image ID `sha256:556aea6d62134524ecd1fcca53380b460b52995344dce571d484f042d9b15e7d`，随机 loopback 端口 `64509`；`EmqxTelemetryAclIntegrationTest` **12/12**，Failures/Errors/Skipped 均为 0，耗时 2.765 秒；
- `bash -n`、scoped `git diff --check`、ACL 禁止项和临时凭据输出扫描通过；最终 container/network/临时目录/凭据残留均为 **0**；
- 三个 R1 文件 SHA-256 分别为：ACL `0E32F21A7D32EE06CC17F69445B2300ADED8B3D651D6E68FB75668392C6D6534`、集成测试 `B549680AD2655224EE43B12A80D01A2378B5905B3FBF3970EAD903E5945795A3`、隔离脚本 `7EE7F985FFA9E3D36A6370BE4B8184676BBA86FBD1CB57E59D5A6567F7704BD4`。

Sol 结论：`LC02-09-R1` 转为 **COMPLETE / SOL-ACCEPTED（Verified-Local）**，当前实现授权归零。该结论只接受隔离本地合同，不代表现有/生产 broker 已启用认证与 ACL，也不关闭 secret 文件消费/轮换、TLS、集群分发、灰度回滚、稳定性/资源、Linux PTY、Windows 发布资格或现场门禁。未 commit。下一步仅由 Sol 细化并冻结 `LC02-10` 全模块回归与文档收口任务；冻结前不得交 Luna 实现。

## 24. `LC02-10` 全模块回归、保护区扫描与文档收口冻结单（2026-08-24）

### 24.1 冻结依据与包性质

Sol 已完整重读并核对《EasyAIoT 项目开发宪法》v1.6.0（SHA-256 `76BF30903A5A62C957A75B57E24080AA7132DE6992D56C262EEAFBE7BE553C4D`）、《平台功能计划》v1.5.0（SHA-256 `F0E5734C8FADA6D0E4DAB98F7D12F58940077F9C60AA8DA42AC8EF45C6F69869`）、ADR-017 v1.1.1、§12～§23 的已接受交付及本文件原第 9～10 节。未发现需要停止并修订双基线的冲突。

`LC02-10` 是 **零实现、只执行既有验收并回填证据** 的关闭包。它不新增或修改业务代码、测试、脚本、配置、Schema、依赖、容器定义或凭据，只验证 `LC02-01A/R1 → 04A/B/C → 05 → 06 → 07 → 08 → 09-R1` 的组合回归，并决定 M1-LC-02 是否可从 `Approved / Frozen` 转为 `Implemented / Verified-Local`。测试发现缺陷时不得在本包内顺手修复；必须停止、保留证据并交 Sol 判断是否另冻最小 R1。

### 24.2 范围与明确不做

强制范围分为六层：

1. `iot-device` 的中心清单、授权与权威设备解析纯合同回归；
2. `iot-sink` 的产品身份、SQLite V3、离线回填、canonical Topic、Claim、中心三方校验与 Inbox 纯合同回归；
3. `iot-sink-biz` 完整模块测试及受影响 reactor `test-compile`；
4. 现有隔离脚本对 V009、Inbox JDBC 与 EMQX 5.8.7 的真实环境零 skip 复验；
5. 迁移、ACK/投影、Store、R1 ACL 及既有脏工作树保护；
6. 本任务单与 M1 SDD 的最终状态、证据和 M1-LC-03 续作入口回填。

本包不执行正式 V009 落库，不修改或重启现有 EasyAIoT 服务/broker，不连接共享或生产数据库，不激活生产账号/ACL/TLS，不实现或改造 ACK 发布/消费、拒绝审计、投影或 Store，不执行 Linux PTY/锁互操作、资源/稳定性压测、Windows 发布资格或现场验证。上述项目继续保持各自 `OPEN` / `OPEN-RUNTIME`，不得以本地回归推断关闭。

### 24.3 冻结保护摘要

执行开始和结束必须复算并逐项相等：

| 保护集 | 文件数 | 冻结 SHA-256 / 规则 |
|---|---:|---|
| LC02-07 迁移保护集 | 13 | `2ab39079fb84d8ba42b056cbbdc25685fc0461f82eda762565f620eada662adb`（沿用 §21.5 的 TAB/小写摘要算法）；V009 单文件必须为 `48416787b7fc886cc3274be53f3a38c60f9a9dd93ca205e3f0311d54a8eafbde` |
| Inbox/Outbox 当前已接受生产集 | 25 | `B2B72C3D6F0F691F8DDCEF21BAD2E73BFC1B87F17335854B61DF0D981F154F2C` |
| Store 生产集 | 9 | `B9AC52AA98EA2424B22AA129DD5E81CA59CD855BAB6A46EB5F57DDF6F990F604` |
| ACK/投影关键生产集 | 5 | `6E2A808692FD12B0415E5037C6AF624F7538AD1650E83597281031446CE30C9B` |
| LC02-09 最终 EMQX 资产/测试集 | 4 | `EA6DCFD71843C19C9882FD59C788532CD639AC7872610AD8FA370C2A393809BC` |

后三个聚合及 25 文件聚合统一采用：“仓库相对路径正序 + 一个空格 + 文件 SHA-256 大写 hex，以 LF 连接并在末尾保留 LF，再对 UTF-8 bytes 做 SHA-256”。其中 R1 三个关键文件还必须分别保持：ACL `0E32F21A7D32EE06CC17F69445B2300ADED8B3D651D6E68FB75668392C6D6534`、真实测试 `B549680AD2655224EE43B12A80D01A2378B5905B3FBF3970EAD903E5945795A3`、隔离脚本 `7EE7F985FFA9E3D36A6370BE4B8184676BBA86FBD1CB57E59D5A6567F7704BD4`。

复算函数冻结为：

```powershell
function Get-Lc02ManifestHash([string[]]$Paths) {
  $repo = (Resolve-Path '.').Path
  $files = foreach ($path in $Paths) {
    if (Test-Path -LiteralPath $path -PathType Leaf) {
      Get-Item -LiteralPath $path
    } elseif (Test-Path -LiteralPath $path -PathType Container) {
      Get-ChildItem -LiteralPath $path -Recurse -File
    } else {
      throw "PROTECTED_PATH_MISSING: $path"
    }
  }
  $entries = @($files | Sort-Object FullName -Unique | ForEach-Object {
    $relative = $_.FullName.Substring($repo.Length + 1).Replace('\', '/')
    $hash = (Get-FileHash -Algorithm SHA256 -LiteralPath $_.FullName).Hash.ToUpperInvariant()
    "$relative $hash"
  })
  $payload = ($entries -join "`n") + "`n"
  $sha = [System.Security.Cryptography.SHA256]::Create()
  try {
    $digest = $sha.ComputeHash([Text.Encoding]::UTF8.GetBytes($payload))
    [pscustomobject]@{
      Count = $entries.Count
      Hash = ([BitConverter]::ToString($digest)).Replace('-', '')
    }
  } finally {
    $sha.Dispose()
  }
}
```

对应路径集合必须严格使用：

- 25 文件：`InboxEnvelope.java`、`TelemetryInboxPort.java`、`telemetry/inbox/jdbc/**`、`iot-sink-biz/.../outbox/**`；
- Store 9 文件：`iot-sink-api/.../telemetry/store/**` 与 `iot-sink-biz/.../telemetry/store/**`；
- ACK/投影 5 文件：`AckCommand.java`、`AckResultCode.java`、`TelemetryOutboxPort.java`、`CenterMqttAckPublisher.java`、`TelemetryProjectionOrchestrator.java`；
- EMQX 4 文件：§22 的 `emqx.conf`、§23 的 `acl.conf`、隔离脚本与 `EmqxTelemetryAclIntegrationTest.java`。

### 24.4 强制测试矩阵与 skip 规则

| 层 | 必须结果 |
|---|---|
| device 定向纯合同 | §24.5 指定 7 类均被发现并执行，Failures/Errors/Skipped=0 |
| sink 定向纯合同 | §24.5 指定 29 类均被发现并执行，Failures/Errors/Skipped=0；覆盖 LC02 所有本地确定性路径 |
| 完整 `iot-sink-biz` 本地模块回归 | 除四个明确的 TDengine/TQ 外部服务资格类外，其余 `*Test` 全部纳入；Maven 退出 0，所有执行测试 Failures/Errors=0；必须报告总 Tests/Skipped、具体 skipped 类和四个排除类 |
| V009 PostgreSQL | 现有 `lc02_v009_contract.sh` 全部 **79/79**，无 skip，临时库/备份残留 0 |
| Inbox PostgreSQL | 现有 `lc02_08_inbox_product_contract.sh` 指定类合计 **49/49**，Failures/Errors/Skipped=0，双夹具和临时库残留 0 |
| EMQX 5.8.7 | Java fail-closed **11/11** 与真实 broker **12/12**，Failures/Errors/Skipped=0，容器/网络/目录/凭据残留 0 |
| 编译 | `iot-device-biz` + `iot-sink-biz` 受影响 reactor `test-compile` 为 BUILD SUCCESS |

完整模块命令明确排除不属于 M1-LC-02 完成定义且需要独立外部服务资格的 `TDengineTelemetryStoreContractTest`、`TDengineIdempotencySpikeTest`、`JdbcTelemetryQueryAdapterContractTest`、`TDengineTelemetryQueryAdapterContractTest`；这四类维持既有证据/OPEN，不以本包通过或失败。允许出现的环境条件 skip 仅限：随后在本节真实隔离阶段以零 skip 重跑的 `JdbcTelemetryInboxContractTest`、`JdbcTelemetryInboxProductIdentityContractTest`、`EmqxTelemetryAclIntegrationTest`。任何其他 LC02 类 skip、未发现、只跑旧报告、用 mock 替代真实脚本或把 `NOT_RUN_LOCAL_ENV` 计作通过，均为失败。真实隔离阶段的三组脚本不允许任何 skip。

### 24.5 唯一验收命令

先在仓库根目录执行 PowerShell 定向回归与完整模块回归：

```powershell
mvn -f DEVICE/pom.xml test-compile `
  -pl 'iot-device/iot-device-biz,iot-sink/iot-sink-biz' -am `
  '-Dmaven.test.skip=false'

mvn -f DEVICE/pom.xml test -pl iot-device/iot-device-biz -am `
  '-Dtest=RouteBackfillManifestContractTest,RouteBackfillManifestResolverTest,CollectorConfigSnapshotContractTest,RouteBackfillAuthorizationContractTest,RouteBackfillAuthorizationSignerTest,TelemetryDeviceAuthorityServiceContractTest,TelemetryDeviceAuthorityInternalApiContractTest' `
  '-Dsurefire.failIfNoSpecifiedTests=false' '-Dmaven.test.skip=false'

mvn -f DEVICE/pom.xml test -pl iot-sink/iot-sink-biz -am `
  '-Dtest=AppendBatchResultContractTest,TelemetryOutboxBatchContractTest,SqliteOutboxRouteMigrationTest,CollectorTelemetryWriterTest,SqliteOutboxAppendBatchTest,SqliteOutboxDurabilityTest,OutboxClaimTest,CollectorCrossTdContractTest,RouteInventoryContractTest,SqliteOutboxRouteInventoryExporterTest,RouteBackfillAuthorizationContractTest,RouteBackfillAuthorizationVerifierTest,RouteBackfillApplyResultContractTest,SqliteOutboxRouteBackfillApplierTest,OutboxFileLockTest,TelemetryRouteContractTest,OutboxUnfinishedRoutesTest,CollectorTelemetryRouteSetProviderTest,OutboxStateMachineTest,CollectorPollingRuntimeTest,TelemetryUpstreamTopicParserContractTest,TelemetryDeviceAuthorityClientAdapterTest,CenterTelemetryIngressHandlerTest,CenterMqttInboxSubscriberContractTest,TelemetryInboxAutoConfigurationTest,InboxEnvelopeProductIdentityContractTest,InboxReceiveResultContractTest,TelemetryStoreBatchContractTest,TelemetryEnvelopeCodecTest' `
  '-Dsurefire.failIfNoSpecifiedTests=false' '-Dmaven.test.skip=false'

mvn -f DEVICE/pom.xml test -pl iot-sink/iot-sink-biz -am `
  '-Dtest=*Test,!TDengineTelemetryStoreContractTest,!TDengineIdempotencySpikeTest,!JdbcTelemetryQueryAdapterContractTest,!TDengineTelemetryQueryAdapterContractTest' `
  '-Dsurefire.failIfNoSpecifiedTests=false' '-Dmaven.test.skip=false'
```

随后执行静态预检和三个真实隔离阶段。密码必须已经存在于调用进程环境变量中，命令和报告不得输出其值：

```bash
bash -n .scripts/postgresql/td005-migration/td005_migration.sh
bash -n .scripts/postgresql/td005-migration/tests/lc02_v009_contract.sh
bash -n .scripts/postgresql/td005-migration/tests/lc02_08_inbox_product_contract.sh
bash -n .scripts/emqx/lc02-09/lc02_09_emqx_acl_contract.sh

LC02_V009_PG_ENABLED=true \
PG_CONTAINER=postgres-server \
PG_USER=postgres \
PG_PASSWORD="$LC02_V009_PG_PASSWORD" \
LC02_V009_DB_PREFIX=lc02_v009_close \
bash .scripts/postgresql/td005-migration/tests/lc02_v009_contract.sh

LC02_08_PG_ENABLED=true \
PG_CONTAINER=postgres-server \
PG_USER=postgres \
PG_PASSWORD="$LC02_08_PG_PASSWORD" \
LC02_08_DB_PREFIX=lc02_08_close \
LC02_08_JDBC_HOST=localhost \
LC02_08_JDBC_PORT=5432 \
bash .scripts/postgresql/td005-migration/tests/lc02_08_inbox_product_contract.sh

cd DEVICE
mvn -pl iot-sink/iot-sink-biz -am \
  -Dmaven.test.skip=false -DskipTests=false \
  -Dtest=TelemetryUpstreamTopicParserContractTest,TelemetryInboxAutoConfigurationTest \
  -Dsurefire.failIfNoSpecifiedTests=false test

LC02_09_EMQX_ENABLED=true \
bash ../.scripts/emqx/lc02-09/lc02_09_emqx_acl_contract.sh
cd ..
```

最后执行保护和禁止边界检查：

```powershell
rg -n '"/telemetry/|/telemetry/#' `
  DEVICE/iot-sink/iot-sink-api/src/main/java/com/basiclab/iot/sink/telemetry/outbox/TelemetryRoute.java `
  DEVICE/iot-sink/iot-sink-biz/src/main/java/com/basiclab/iot/sink/outbox/sqlite `
  DEVICE/iot-sink/iot-sink-biz/src/main/java/com/basiclab/iot/sink/protocol/polling/CollectorTelemetryRouteSetProvider.java `
  DEVICE/iot-sink/iot-sink-biz/src/main/java/com/basiclab/iot/sink/telemetry/inbox/route `
  DEVICE/iot-sink/iot-sink-biz/src/main/java/com/basiclab/iot/sink/telemetry/inbox/mqtt/CenterMqttInboxSubscriber.java `
  DEVICE/iot-sink/iot-sink-biz/src/main/java/com/basiclab/iot/sink/telemetry/inbox/mqtt/TelemetryMqttProperties.java

rg -n 'telemetry_ingress_rejection|ack_sent_at|ack_attempts' `
  DEVICE/iot-sink/iot-sink-api/src/main/java/com/basiclab/iot/sink/telemetry `
  DEVICE/iot-sink/iot-sink-biz/src/main/java/com/basiclab/iot/sink/telemetry `
  .doc/技术设计/电力运维云平台/assets/td005-migration `
  .scripts/postgresql/td005-migration

git diff --check -- `
  DEVICE/iot-device/iot-device-biz/src/main/java/com/basiclab/iot/device/service/device/authority `
  DEVICE/iot-device/iot-device-biz/src/main/java/com/basiclab/iot/device/service/collector/backfill `
  DEVICE/iot-sink/iot-sink-api/src/main/java/com/basiclab/iot/sink/telemetry `
  DEVICE/iot-sink/iot-sink-biz/src/main/java/com/basiclab/iot/sink/outbox `
  DEVICE/iot-sink/iot-sink-biz/src/main/java/com/basiclab/iot/sink/protocol/polling `
  DEVICE/iot-sink/iot-sink-biz/src/main/java/com/basiclab/iot/sink/telemetry `
  DEVICE/iot-sink/iot-sink-biz/src/test/java/com/basiclab/iot/sink `
  .scripts/postgresql/td005-migration `
  .scripts/emqx/lc02-09 `
  .doc/技术设计/电力运维云平台/M1-LC-02-遥测Topic与产品路由身份收口任务单.md `
  .doc/技术设计/电力运维云平台/M1-SDD进度与续作入口.md
git status --short
```

两个 `rg` 均必须 0 命中。既有 `CenterMqttAckPublisher` 中历史 `/telemetry/ack/**` 事实由 ACK/投影冻结摘要保护，本包既不修改也不宣称其已迁移；可靠上行/Claim/center 入口的旧 Topic 必须为 0。冻结时全仓 `git diff --check` 已被本任务范围外、用户既有 Markdown hard-break 尾空格命中，因此本包只以命令中列出的精确 LC02 scope 为门禁，不得越权修理那些既有文件。`git status --short` 用于比对开始时既有脏工作树，不能据此清理、覆盖或归属用户改动；除两份进度文档外不得产生新的源码/测试/脚本/配置 diff。

### 24.6 交付证据与完成判定

Luna 交付必须逐层报告：实际执行命令、Maven reactor 模块数、每个定向集合与完整模块的 Tests/Failures/Errors/Skipped、具体 skip 类及归类、PostgreSQL/EMQX/Docker 版本和 image ID、V009/Inbox/EMQX 固定断言数、并发超时结果、所有临时数据库/容器/网络/目录/凭据残留、五组保护摘要前后值、禁止项扫描、工作树新增 diff、未执行项与风险。不得报告密码、完整 JDBC URL、payload、token 或 authorization header。

只有 §24.4～§24.5 全部通过、保护摘要完全一致、实现文件零修改、临时资源残留为 0，Sol 独立复核后才可：

1. 将 `LC02-10` 标记 `COMPLETE / SOL-ACCEPTED`；
2. 将 M1-LC-02 标记 `Implemented / Verified-Local`；
3. 勾选第 10 节本地完成定义；
4. 把 M1-LC-03 的 ACK 接线列为唯一下一编码任务，同时明确正式 V009 与生产 broker 激活仍需各自独立窗口。

Luna 只能交付“待 Sol 复核”，不得自行完成上述状态转换，不得 commit。

### 24.7 文件白名单与停工条件

后续获得独立执行授权后，唯一可写文件为：

1. 本任务单，仅追加执行证据，不得改变冻结合同；
2. `M1-SDD进度与续作入口.md`，仅追加同一证据和下一入口。

生产源码、测试源码、POM、SQL/migration/dump、脚本、EMQX 资产、YAML/Compose、NODE/collector、前端和其他文档全部只读；测试生成的 `target/**`、仓库外临时目录和隔离容器不属于交付文件，结束后按现有脚本规则清理。不得新增文件、依赖、配置键、测试替身或永久容器，不得 commit。

出现任一情形立即停工并交回 Sol：任一保护摘要漂移；需要修改任何非文档文件；定向类未发现/skip；真实脚本不能零 skip 或不能清理；完整模块出现 failure/error 或无法解释的新 skip；Docker 不是 Linux containers、缺少本地精确 EMQX 5.8.7 镜像且只能 pull/换版；只能使用共享/生产数据库；发现 ACK/审计/投影/Store 新增量、凭据泄漏、双基线/ADR 冲突或用户并行改动无法隔离。不得自行修复、删除 lock、回滚用户改动或改用 mock 降级。

### 24.8 当前授权状态与下一步

冻结时状态为 **FROZEN / NOT-YET-AUTHORIZED**。当时的“授权下一步”只授权 GPT-5.6 Sol 完成 `LC02-10` 的需求拆解、回归架构、保护摘要、精确命令、白名单和停工条件冻结；不授权 GPT-5.6 Luna 运行 §24.5，不授权任何实现修改、数据库/服务/生产 broker 操作或 commit。后续独立执行授权及实际运行状态见 §24.9。

冻结后的原定下一步是由决策所有者再次明确授权“GPT-5.6 Luna Max 执行 LC02-10 §24 回归与文档收口”。该授权现已发生，结果见 §24.9；正式 V009 落库、`LC02-09-RUNTIME-01`、生产凭据/ACL/TLS、Linux PTY/锁互操作、资源/稳定性压测、Windows 发布资格和现场验证继续独立锁定/OPEN。

### 24.9 独立执行授权与 Luna 运行时阻塞（2026-08-24）

决策所有者已明确授权“GPT-5.6 Luna Max 执行 LC02-10 §24 回归与文档收口”。Sol 按冻结边界先后派发两个独立 GPT-5.6 Luna（max reasoning）回合，均明确要求先发送最小开工确认、复算保护摘要、逐层执行 §24.5，并在任一停工条件出现时立即返回。

实际运行结果：首个回合持续约 7 分钟无消息、无新测试报告、无文件落盘，且不响应两次阶段状态请求；Sol 中断后确认没有新增 Maven 测试进程或交付文件。随后以精简上下文启动第二个 Luna Max 回合，持续约 6 分钟仍未发送开工确认、无可观察工具/文件进展且不响应最后状态请求，Sol 再次中断。两个回合均未形成可证明的保护摘要前检、Maven/Docker 命令或 Tests/Failures/Errors/Skipped 证据。

Sol 未静默替换模型、未冒充 Luna 执行 §24.5，也未修改任何生产源码、测试、脚本或配置。当前状态因此为 **FROZEN / AUTHORIZED / EXECUTION-BLOCKED-LUNA-RUNTIME**，不是测试失败，也不得标记 COMPLETE / SOL-ACCEPTED。下一步只能二选一：等待 GPT-5.6 Luna Max 运行能力恢复后按原授权重试，或由决策所有者另行明确授权 GPT-5.6 Sol 接管执行 §24.5；在此之前不得继续测试或关闭 M1-LC-02。

### 24.10 Sol 接管执行与冻结门禁停工（2026-08-24）

决策所有者已明确授权“GPT-5.6 Sol 接管执行 §24.5”。Sol 重新读取《EasyAIoT 项目开发宪法》v1.6.0（SHA-256 `76BF30903A5A62C957A75B57E24080AA7132DE6992D56C262EEAFBE7BE553C4D`）与《平台功能计划》v1.5.0（SHA-256 `F0E5734C8FADA6D0E4DAB98F7D12F58940077F9C60AA8DA42AC8EF45C6F69869`），并在 §24.7 只读/文档白名单内开始执行。Docker Server 为 Linux containers 29.7.2，本地精确 EMQX 镜像为 `emqx/emqx:5.8.7`、image ID `sha256:556aea6d62134524ecd1fcca53380b460b52995344dce571d484f042d9b15e7d`；`postgres-server` 正在运行。两组 PostgreSQL 密码环境变量均未注入，未读取容器或文件中的替代凭据，也未连接共享/目标数据库。

执行结果按冻结顺序为：

1. 联合 `test-compile` 在授权网络环境使用 §24.5 原命令完成 **34/34 reactor BUILD SUCCESS**；首次沙箱内尝试在 Maven BOM 下载阶段因网络/文件权限被拒，未进入项目编译，不计为代码失败；
2. device 七类定向合同实际执行 **39/39**，Failures=0、Errors=0、Skipped=0，33 个 reactor 全部成功；
3. sink 二十九类定向合同实际执行 **155** 项，Failures=0、Errors=3、Skipped=0，触发 §24.7 立即停工：
   - `CollectorCrossTdContractTest.queueTimeoutIsBackpressureAndUnavailableDatabaseIsStable` 在构造缺失父目录的 `SqliteTelemetryOutbox` 时即抛出 `OutboxUnavailableException: ROUTE_BACKFILL_APPLY_FAILED: outbox startup failed`，根因为 `missing/collector-outbox.lock` 的 `NoSuchFileException`；测试未进入其预期的 `appendBatch` 断言阶段；
   - `SqliteOutboxRouteBackfillApplierTest.databaseSymbolicLinkIsRejectedWithoutChangingRealDatabase` 与 `symbolicLinkLockIsRejectedWithoutChangingDatabase` 均在 Windows fixture 调用 `Files.createSymbolicLink` 时因“客户端没有所需的特权”报 `FileSystemException`，未进入生产拒绝断言；
4. 因第 3 步已经命中明确停工条件，完整 `iot-sink-biz` 模块、V009 PostgreSQL 79/79、Inbox PostgreSQL 49/49、Java fail-closed 11/11 与真实 EMQX 12/12 均未继续执行；此外，即使前序测试通过，当前进程缺少 `LC02_V009_PG_PASSWORD` 和 `LC02_08_PG_PASSWORD` 也不满足两个真实 PostgreSQL 阶段的冻结前提。

停工后复算五组保护事实均未漂移：迁移 13 文件 `2ab39079fb84d8ba42b056cbbdc25685fc0461f82eda762565f620eada662adb`，V009 `48416787B7FC886CC3274BE53F3A38C60F9A9DD93CA205E3F0311D54A8EAFBDE`，Inbox/Outbox 25 文件 `B2B72C3D6F0F691F8DDCEF21BAD2E73BFC1B87F17335854B61DF0D981F154F2C`，Store 9 文件 `B9AC52AA98EA2424B22AA129DD5E81CA59CD855BAB6A46EB5F57DDF6F990F604`，ACK/投影 5 文件 `6E2A808692FD12B0415E5037C6AF624F7538AD1650E83597281031446CE30C9B`，EMQX 4 文件 `EA6DCFD71843C19C9882FD59C788532CD639AC7872610AD8FA370C2A393809BC`；ACL/真实测试/隔离脚本三个单文件摘要也分别保持 §24.3 冻结值。两个禁止项扫描均 0 命中；未创建 `lc02*` 临时容器或网络，现有 `emqx-server` 未被修改；无本轮 Maven 进程残留，所见两个 Java 进程均为既有 VS Code Java language server。

本轮没有修改生产源码、测试、POM、SQL、脚本或配置，没有 commit。`LC02-10` 因此转为 **STOPPED / NOT ACCEPTED**，M1-LC-02 保持 Approved / Frozen，不得标记 Implemented / Verified-Local。下一步必须由 Sol 单独分析第一项合同漂移并冻结 `LC02-10-R1` 的最小处置，同时把两个符号链接用例限定到已有 Linux Docker 资格路径；重新执行真实 PostgreSQL 阶段前，调用进程还必须显式注入两组密码环境变量。未经新的 R1 冻结和独立授权，不得修改实现或继续 §24.5 后继阶段。

## 25. `LC02-10-R1` 构造期合同对齐、Linux Docker 资格与完整重验冻结单（2026-08-24）

### 25.1 冻结依据与结论

本节同时依据《EasyAIoT 项目开发宪法》v1.6.0（SHA-256 `76BF30903A5A62C957A75B57E24080AA7132DE6992D56C262EEAFBE7BE553C4D`）、《平台功能计划》v1.5.0（SHA-256 `F0E5734C8FADA6D0E4DAB98F7D12F58940077F9C60AA8DA42AC8EF45C6F69869`）、已接受的 `LC02-04C-2` 生命周期合同及 §24.10 失败证据冻结。R1 仍是零功能扩展的收敛包，不改变产品范围、部署档位、接口、数据、Topic、ACK、Store、迁移或 broker 责任。

Sol 对 Git 历史与当前调用链的结论是：`CollectorCrossTdContractTest.queueTimeoutIsBackpressureAndUnavailableDatabaseIsStable` 创建于构造期只启动 writer 的旧事实；后续已接受的 `LC02-04C-2` 把 `SqliteTelemetryOutbox` 固定为“构造时先持有同目录 `collector-outbox.lock` → migration → writer start”。缺失父目录因此必须在构造期 fail-closed，不能形成一个可调用 `appendBatch` 或 `shutdown` 的半初始化对象。当前生产顺序是正确基线，R1 只修订过期测试，不得把异常推迟回 append 阶段，不得自动创建缺失父目录。

### 25.2 唯一实现增量与精确断言

获得后续独立实现授权后，只允许在 `CollectorCrossTdContractTest.java` 的现有 `queueTimeoutIsBackpressureAndUnavailableDatabaseIsStable` 方法内做机械对齐，并可机械增加该断言所需的 JDK/JUnit import：

1. 原 `OutboxCommandQueue` 容量/零等待背压断言原样保留；
2. 对 `new SqliteTelemetryOutbox(missingParentDb, ...)` 直接执行 `assertThrows(OutboxUnavailableException.class, ...)`，不得先保存对象、不得调用 append 或 shutdown；
3. 断言顶层消息精确以 `ROUTE_BACKFILL_APPLY_FAILED: outbox startup failed` 开头，cause 必须为 `java.nio.file.NoSuchFileException`，且 cause 路径指向缺失父目录内的 `collector-outbox.lock`；不得接受空 cause、任意 RuntimeException 或错误码漂移；
4. 失败后 `missing/`、`outbox.db`、`collector-outbox.lock` 均不得存在，证明没有隐式 mkdir、数据库或锁残留；
5. 测试总数不增加、不删除、不 disable、不 assumption skip；允许把方法名机械改为准确表达 `...FailsAtStartup`，但不得拆分为新测试或改变其他四个场景。

禁止修改 `SqliteTelemetryOutbox`、`OutboxFileLock`、`SqliteOutboxMigration`、`SqliteOutboxWriter`、`SqliteOutboxRouteBackfillApplier` 及任何生产文件。禁止将 `NoSuchFileException` 吞掉、改为延迟异步失败、自动创建目录、移除启动锁、改变稳定错误码或降低符号链接拒绝强度。

### 25.3 Linux Docker 唯一 Java 资格入口

Windows 主机无创建测试符号链接权限不是发布资格证据，也不得通过 skip、管理员模式、开发者模式或放宽断言规避。R1 的全部纯 Java 阶段统一在本地 Linux Docker 中执行，使二十九类定向集和完整 `iot-sink-biz` 模块共享同一 Java 17、文件系统与锁语义；不得把“Windows 153 通过 + Linux 2 通过”拼成一次 155 项通过。

冻结环境为 Docker Client/Server 29.7.2、`linux/amd64`，本地镜像必须精确为：

- Maven `maven:3.9.16-amazoncorretto-17-alpine`：`sha256:53215f45dda1e255693160346acc2a9cc10e3b6a59a19ce3a2fc95c476c1772a`；
- EMQX `emqx/emqx:5.8.7`：`sha256:556aea6d62134524ecd1fcca53380b460b52995344dce571d484f042d9b15e7d`；
- PostgreSQL `postgres:18`：`sha256:3a82e1f56c8f0f5616a11103ac3d47e632c3938698946a7ad26da0df1334744a`。

镜像缺失或摘要不符时立即停止；本包不允许 pull、build、换 tag 或联网下载依赖。Maven 容器必须使用 `--network none`、`--rm`、仓库 bind mount、现有本机 Maven repository bind mount和 `/tmp` tmpfs；固定容器名开始前必须不存在，结束后必须为零残留。仓库挂载只允许 Maven 生成/更新 `target/**`，不得在容器内改源码。

从仓库根目录执行以下 PowerShell 前检和四个 Linux Java 阶段；每条命令退出非零即停止，不得继续后继阶段：

```powershell
$repoPath = (Resolve-Path '.').Path
$m2Path = (Resolve-Path (Join-Path $env:USERPROFILE '.m2/repository')).Path

if ((docker version --format '{{.Server.Os}}') -ne 'linux') {
  throw 'LC02_R1_DOCKER_NOT_LINUX'
}
if (docker ps -a --format '{{.Names}}' | Select-String -Pattern '^lc02-10-r1-') {
  throw 'LC02_R1_CONTAINER_RESIDUE'
}

docker run --rm --name lc02-10-r1-compile --network none `
  --mount "type=bind,source=$repoPath,target=/workspace" `
  --mount "type=bind,source=$m2Path,target=/root/.m2/repository" `
  --tmpfs /tmp:exec,size=1g -w /workspace `
  maven:3.9.16-amazoncorretto-17-alpine `
  mvn -f DEVICE/pom.xml test-compile `
  -pl 'iot-device/iot-device-biz,iot-sink/iot-sink-biz' -am `
  '-Dmaven.test.skip=false'
if ($LASTEXITCODE -ne 0) { throw 'LC02_R1_TEST_COMPILE_FAILED' }

docker run --rm --name lc02-10-r1-device --network none `
  --mount "type=bind,source=$repoPath,target=/workspace" `
  --mount "type=bind,source=$m2Path,target=/root/.m2/repository" `
  --tmpfs /tmp:exec,size=1g -w /workspace `
  maven:3.9.16-amazoncorretto-17-alpine `
  mvn -f DEVICE/pom.xml test -pl iot-device/iot-device-biz -am `
  '-Dtest=RouteBackfillManifestContractTest,RouteBackfillManifestResolverTest,CollectorConfigSnapshotContractTest,RouteBackfillAuthorizationContractTest,RouteBackfillAuthorizationSignerTest,TelemetryDeviceAuthorityServiceContractTest,TelemetryDeviceAuthorityInternalApiContractTest' `
  '-Dsurefire.failIfNoSpecifiedTests=false' '-Dmaven.test.skip=false'
if ($LASTEXITCODE -ne 0) { throw 'LC02_R1_DEVICE_TARGETED_FAILED' }

docker run --rm --name lc02-10-r1-sink --network none `
  --mount "type=bind,source=$repoPath,target=/workspace" `
  --mount "type=bind,source=$m2Path,target=/root/.m2/repository" `
  --tmpfs /tmp:exec,size=1g -w /workspace `
  maven:3.9.16-amazoncorretto-17-alpine `
  mvn -f DEVICE/pom.xml test -pl iot-sink/iot-sink-biz -am `
  '-Dtest=AppendBatchResultContractTest,TelemetryOutboxBatchContractTest,SqliteOutboxRouteMigrationTest,CollectorTelemetryWriterTest,SqliteOutboxAppendBatchTest,SqliteOutboxDurabilityTest,OutboxClaimTest,CollectorCrossTdContractTest,RouteInventoryContractTest,SqliteOutboxRouteInventoryExporterTest,RouteBackfillAuthorizationContractTest,RouteBackfillAuthorizationVerifierTest,RouteBackfillApplyResultContractTest,SqliteOutboxRouteBackfillApplierTest,OutboxFileLockTest,TelemetryRouteContractTest,OutboxUnfinishedRoutesTest,CollectorTelemetryRouteSetProviderTest,OutboxStateMachineTest,CollectorPollingRuntimeTest,TelemetryUpstreamTopicParserContractTest,TelemetryDeviceAuthorityClientAdapterTest,CenterTelemetryIngressHandlerTest,CenterMqttInboxSubscriberContractTest,TelemetryInboxAutoConfigurationTest,InboxEnvelopeProductIdentityContractTest,InboxReceiveResultContractTest,TelemetryStoreBatchContractTest,TelemetryEnvelopeCodecTest' `
  '-Dsurefire.failIfNoSpecifiedTests=false' '-Dmaven.test.skip=false'
if ($LASTEXITCODE -ne 0) { throw 'LC02_R1_SINK_TARGETED_FAILED' }

docker run --rm --name lc02-10-r1-sink-full --network none `
  --mount "type=bind,source=$repoPath,target=/workspace" `
  --mount "type=bind,source=$m2Path,target=/root/.m2/repository" `
  --tmpfs /tmp:exec,size=1g -w /workspace `
  maven:3.9.16-amazoncorretto-17-alpine `
  mvn -f DEVICE/pom.xml test -pl iot-sink/iot-sink-biz -am `
  '-Dtest=*Test,!TDengineTelemetryStoreContractTest,!TDengineIdempotencySpikeTest,!JdbcTelemetryQueryAdapterContractTest,!TDengineTelemetryQueryAdapterContractTest' `
  '-Dsurefire.failIfNoSpecifiedTests=false' '-Dmaven.test.skip=false'
if ($LASTEXITCODE -ne 0) { throw 'LC02_R1_SINK_FULL_FAILED' }

if (docker ps -a --format '{{.Names}}' | Select-String -Pattern '^lc02-10-r1-') {
  throw 'LC02_R1_CONTAINER_CLEANUP_FAILED'
}
```

Linux sink 定向集必须为原二十九类 **155/155**，Failures=0、Errors=0、Skipped=0；两个 symlink fixture 必须真实创建链接并进入生产拒绝断言。device 必须保持 **39/39**、零 skip。完整模块必须报告实际 Tests/Failures/Errors/Skipped 和全部 skipped 类；skip 规则仍严格沿用 §24.4，不因改到 Docker 而放宽。

### 25.4 PostgreSQL 凭据门禁与完整重验顺序

R1 不创建、不读取、不轮换凭据。开始任何验收前，调用进程必须已经显式注入非空 `LC02_V009_PG_PASSWORD` 和 `LC02_08_PG_PASSWORD`；只允许检查存在性，不得回显长度、值、完整 JDBC URL或从 `postgres-server`、Compose、`.env`、历史命令、日志中寻找替代值。两项任一缺失时整个 R1 验收保持 NOT RUN，不得先跑一部分后等待凭据。

凭据前检、Docker/image/hash 前检全部通过后，验收必须从头按以下单一顺序执行，不能复用 §24.10 的历史成功报告：

1. §25.3 四个 Linux Java 阶段：34 reactor `test-compile`、device 39/39、sink 155/155、完整模块；
2. §24.5 的 bash 静态语法预检；
3. V009 隔离 PostgreSQL **79/79**，前缀固定 `lc02_v009_close`，零 skip/库/备份残留；
4. Inbox 隔离 PostgreSQL **49/49**，前缀固定 `lc02_08_close`，零 skip/双夹具/库残留；
5. §24.5 Java fail-closed **11/11** 与真实 EMQX **12/12**；
6. §24.3 五组保护摘要、R1 单文件保护、两个禁止扫描、scoped `git diff --check`、新增 diff 和全部临时资源/凭据环境清理。

任何阶段失败立即停止，不得继续收集“后面通过”来抵消前序失败。PG 脚本只允许使用现有冻结参数和当前进程继承的密码环境变量；结束时必须从临时 `PG_PASSWORD`/JDBC 子进程变量中移除，不得删除 owner 注入的两个源变量。

### 25.5 文件白名单、保护摘要与停工条件

后续独立授权后的唯一实现白名单为：

1. `DEVICE/iot-sink/iot-sink-biz/src/test/java/com/basiclab/iot/sink/outbox/sqlite/CollectorCrossTdContractTest.java`，仅 §25.2 的单方法/import 机械修订；
2. 本任务单与 `M1-SDD进度与续作入口.md`，仅回填执行证据和状态。

不得新增文件。开始和结束时，以下生产文件必须保持当前 SHA-256：

- `SqliteTelemetryOutbox.java`：`6F3AE4AAE4EA7F025E19F2689387583B9055DB98BEBBE461AD19463C972F9682`；
- `OutboxFileLock.java`：`B3FC80A50D48145FC34BFAD8E599CCF2235E52E3546FFB455EACE69B325AE4D3`；
- `SqliteOutboxMigration.java`：`FD9183221D35F760B76F6CFB2003FF450B7BAC174760805ADE81F8B259EBBA90`；
- symlink 资格测试 `SqliteOutboxRouteBackfillApplierTest.java`：`2E6FD4774A08FC113DBBA4FA2544A2E588318885C4C834B579D312C6366D9441`。

§24.3 的迁移 13、Inbox/Outbox 25、Store 9、ACK/投影 5、EMQX 4 及 R1 三关键单文件摘要继续全部受保护。`CollectorCrossTdContractTest.java` 的冻结前摘要为 `7F22F01DACC15AA6BF2C15C3EF703EAF12C8B54128434034545CA5458F3A4BD2`；实现后允许变化，但 diff 必须只包含 §25.2 指定方法/必要 import，交付必须报告新摘要。

出现生产文件漂移、测试范围外改动、需要新增 helper/script/POM、自动 mkdir、构造顺序或错误码变更、symlink skip/assumption、Docker 非 Linux/镜像不符/需要 pull、Maven 需要网络、PG 密码缺失或泄漏、临时资源不能清理、任何测试 failure/error/非法 skip 时立即停工。实现者不得修复新发现缺陷、不得 commit、不得回退或清理用户既有脏改动。

### 25.6 完成判定、授权状态与下一步

只有 §25.2 实现经 Sol diff 审查、§25.3～§25.4 从头完整通过、所有保护摘要一致、除单一测试和两份文档外零新增 diff、临时资源与临时凭据变量残留为 0，Sol 才可把 `LC02-10-R1` 和 `LC02-10` 标记 COMPLETE / SOL-ACCEPTED，并把 M1-LC-02 转为 Implemented / Verified-Local。该结论仍不批准正式 V009 落库、生产 broker/ACL/TLS 激活、资源/稳定性压测、Linux PTY/跨进程锁互操作、Windows 发布资格或现场验证。

本节当前状态为 **FROZEN / NOT-YET-AUTHORIZED**。本轮仅授权 Sol 分析并冻结 R1，不授权修改测试，不授权运行 Docker/Maven/PG/EMQX 完整验收。下一步须决策所有者独立授权“GPT-5.6 Luna Max 执行 LC02-10-R1 §25.2 实现与测试”；若 Luna 不可用，必须明确报告后再由 owner 决定是否授权 Sol 接管。两组 PostgreSQL 密码环境变量也必须在实际验收回合开始前由 owner 运行环境显式提供。

### 25.7 Luna Max 独立授权与运行时阻塞（2026-08-24）

决策所有者已明确授权“GPT-5.6 Luna Max 执行 LC02-10-R1 §25.2 实现与测试”。Sol 前检确认双基线、`SqliteTelemetryOutbox.java`、`OutboxFileLock.java`、`SqliteOutboxMigration.java`、`CollectorCrossTdContractTest.java` 与 `SqliteOutboxRouteBackfillApplierTest.java` 摘要均精确保持 §25 冻结值；同时确认当前进程的 `LC02_V009_PG_PASSWORD`、`LC02_08_PG_PASSWORD` 均未注入，因此派发范围只包含 §25.2 单测试修订及最小 Linux Docker 直接测试，不包含完整 §25.3～§25.4。

首个 GPT-5.6 Luna（max reasoning）回合超过约 3 分钟未发送开工确认、未修改文件、未生成新测试报告，且不响应阶段状态请求；Sol 中断后以无历史精简上下文派发第二个 Luna Max 回合，仍无开工确认、文件改动或测试报告，随后再次中断。两个回合均未形成可证明的实现或测试证据；`CollectorCrossTdContractTest.java` 仍为冻结前 SHA-256 `7F22F01DACC15AA6BF2C15C3EF703EAF12C8B54128434034545CA5458F3A4BD2`，保护生产文件未变，未启动 PG/EMQX 或完整验收。

当前状态为 **FROZEN / AUTHORIZED / EXECUTION-BLOCKED-LUNA-RUNTIME**，不是实现失败或测试失败。Sol 未静默替换模型、未自行接管实现。下一步只能等待 Luna Max 运行能力恢复后按现有授权重试，或由决策所有者另行明确授权 GPT-5.6 Sol 接管 §25.2；完整验收仍须先显式注入两组 PostgreSQL 密码环境变量。

### 25.8 Sol 接管 §25.2 实现与直接测试结果（2026-08-24）

决策所有者随后明确授权“GPT-5.6 Sol 接管 LC02-10-R1 §25.2”。Sol 重新核对双基线和全部 R1 单文件摘要，在 §25.2 精确白名单内只修改 `CollectorCrossTdContractTest.java`：保留队列容量与零等待背压断言；把缺失父目录场景改为构造 `SqliteTelemetryOutbox` 时直接捕获 `OutboxUnavailableException`；精确断言稳定消息前缀、`NoSuchFileException` cause 指向 `missing/collector-outbox.lock`，以及 missing 目录、数据库和锁文件均不存在。未修改生产代码、其他测试、POM、脚本或配置。

最小直接验证使用 Docker Server 29.7.2、Linux/amd64、本地精确镜像 `maven:3.9.16-amazoncorretto-17-alpine@sha256:53215f45dda1e255693160346acc2a9cc10e3b6a59a19ce3a2fc95c476c1772a`，设置 `--network none`、`--rm`、仓库与本地 Maven repository bind mount及 `/tmp` tmpfs。`CollectorCrossTdContractTest` 全类实际 **5/5**，Failures=0、Errors=0、Skipped=0；28 个 reactor 模块全部 SUCCESS，BUILD SUCCESS。Maven 在禁网条件下尝试刷新 BouncyCastle metadata 并收到不可达 warning，但依赖由既有本地缓存满足，没有下载、pull 或解除禁网。固定容器残留为 0。

交付复核：`CollectorCrossTdContractTest.java` 新 SHA-256 为 `4CE831266BF37FE220FF006C0B05F2A5A83ED9B444CD19C06763FBCEFFA484C8`；`SqliteTelemetryOutbox.java`、`OutboxFileLock.java`、`SqliteOutboxMigration.java`、`SqliteOutboxRouteBackfillApplierTest.java` 分别仍为 §25.5 的 `6F3AE4AA...F9682`、`B3FC80A5...E4D3`、`FD918322...BA90`、`2E6FD477...9441`；测试文件 scoped `git diff --check` 通过。没有 commit。

由于当前进程的 `LC02_V009_PG_PASSWORD` 和 `LC02_08_PG_PASSWORD` 仍均未注入，依 §25.4 没有启动 §25.3 四阶段完整 Java 验收、V009 79/79、Inbox 49/49、Java fail-closed 11/11 或真实 EMQX 12/12。本结果只证明 §25.2 实现与直接测试通过，状态为 **IMPLEMENTED / DIRECT-TEST-PASSED / FULL-ACCEPTANCE-NOT-RUN**，不得据此关闭 `LC02-10` 或 M1-LC-02。下一步须先在调用进程显式提供两组密码变量，再由决策所有者独立授权执行 §25.3～§25.4 完整验收。

### 25.9 完整重验触发依赖 reactor 停工（2026-08-25）

决策所有者继续授权执行 §25.3～§25.4，并明确允许本回合从 `DEVICE/.env` 读取两组 PostgreSQL 密码作为凭据来源例外。Sol 只检查两项非空存在性且全程未回显值、长度或完整 JDBC URL；由于本轮在 PostgreSQL 阶段前已经停工，两组密码没有传入 PostgreSQL、Maven 或 EMQX 子进程。该来源偏离 §25.4 原冻结要求，不能作为后续回合的默认授权，重新验收前必须由 Sol 决定恢复原调用进程注入方式或另冻凭据来源修订。

完整前检使用 Docker Server 29.7.2、Linux/amd64；Maven、EMQX、PostgreSQL 三张本地镜像摘要均与 §25.3 精确值一致。开始时迁移 13、Inbox/Outbox 25、Store 9、ACK/投影 5、EMQX 4 五组聚合摘要及 R1 九个关键单文件摘要全部一致。按冻结顺序实际结果为：

1. Linux Docker 联合 `test-compile`：34/34 reactor `BUILD SUCCESS`，退出码 0；禁网下仅出现 BouncyCastle metadata 不可达 warning，依赖由既有本地缓存满足；
2. device 七类：实际 **39/39**，Failures=0、Errors=0、Skipped=0，33/33 reactor `BUILD SUCCESS`；
3. sink 二十九类：实际 **155/155**，Failures=0、Errors=0、Skipped=0，28/28 reactor `BUILD SUCCESS`；`SqliteOutboxRouteBackfillApplierTest` 31/31，两个符号链接 fixture 在 Linux 中真实创建链接并进入生产拒绝断言；
4. 完整模块冻结命令在 `iot-common-web` reactor 执行 `DesensitizeTest.test` 时失败：Tests=1、Failures=1、Errors=0、Skipped=0，断言期望脱敏结果 `芋***`、实际为 `B*********`。该失败发生在 `iot-sink-biz` 前，故 `iot-sink-biz` 及其后续结果均未形成，本命令 `BUILD FAILURE`；
5. 依 §25.4“任何阶段失败立即停止”，没有继续 bash 静态预检、V009 PostgreSQL 79/79、Inbox PostgreSQL 49/49、Java fail-closed 11/11 或真实 EMQX 12/12，也没有修改或重启现有 PostgreSQL/EMQX 服务。

停工后五组聚合保护摘要仍分别为迁移 13 `2ab39079fb84d8ba42b056cbbdc25685fc0461f82eda762565f620eada662adb`、Inbox/Outbox 25 `B2B72C3D6F0F691F8DDCEF21BAD2E73BFC1B87F17335854B61DF0D981F154F2C`、Store 9 `B9AC52AA98EA2424B22AA129DD5E81CA59CD855BAB6A46EB5F57DDF6F990F604`、ACK/投影 5 `6E2A808692FD12B0415E5037C6AF624F7538AD1650E83597281031446CE30C9B`、EMQX 4 `EA6DCFD71843C19C9882FD59C788532CD639AC7872610AD8FA370C2A393809BC`。固定 `lc02-10-r1-*` 容器、`lc02_v009_close*` / `lc02_08_close*` 数据库残留均为 0。执行期间出现任务范围外的并行文档改动，均未清理、覆盖或归属本轮。

结论：`LC02-10-R1` 转为 **STOPPED / FULL-RETEST-FAILED / NOT ACCEPTED**，`LC02-10` 与 M1-LC-02 继续保持 **STOPPED / NOT ACCEPTED** 和 Approved / Frozen。本包不得修复 `DesensitizeTest`。下一步必须由 Sol 只读分析该测试的确定性合同、为何 `-am` 完整命令把依赖 reactor 测试纳入门禁，以及凭据来源例外，再决定是否冻结 `LC02-10-R2`；未经新的独立授权不得继续 §25.4 或修改测试。

## 26. `LC02-10-R2` 确定性旧断言对齐、凭据来源例外与完整重验冻结单（2026-08-25）

### 26.1 双基线、失败事实与架构结论

本节同时依据《EasyAIoT 项目开发宪法》v1.6.0、《平台功能计划》v1.5.0、§24～§25 的冻结合同及 §25.9 真实 Linux 失败证据。R2 是零业务功能、零生产实现、零接口/Schema/Topic/部署变化的关闭包，只处置一个确定性测试旧断言并重新执行完整验收；不改变 `standard/full` 产品范围，不批准 `mini`、正式 V009、生产 broker/ACL/TLS、运行期压测、现场或发布资格。

Sol 只读核验确认：`DesensitizeTest` 当前输入固定为 `BasicLab源码`，Java `String.length()` 为 10；`@ChineseNameDesensitize` 固定 `prefixKeep=1`、`suffixKeep=0`、`replacer="*"`，生产 `AbstractSliderDesensitizationHandler` 因此确定返回首字符 `B` 加 9 个 `*`，即 `B*********`。测试仍断言与当前输入无关的 `芋***`，是确定性旧 fixture 漂移，不是生产脱敏实现缺陷。R2 禁止修改生产处理器、注解默认值、输入字符串或其他断言来迁就旧期望。

§25.3 完整模块命令带 `-am`，且 `-Dtest=*Test,...` 会作用于所选 reactor，因此依赖模块 `iot-common-web` 的 `DesensitizeTest` 属于该次完整验收门禁。R2 明确保留这一事实：不得去掉 `-am`、不得加 `-DskipTests`、不得只在 `iot-sink-biz` 恢复执行、不得排除 `DesensitizeTest`，也不得复用 §25.9 已通过的三个 Java 阶段。修正后必须从头重跑；若依赖 reactor 暴露下一个 failure/error/非法 skip，仍立即停工，不得把它归类为“LC02 范围外”后继续。

### 26.2 唯一实现增量、白名单与保护摘要

获得后续独立实现授权后，唯一测试增量是：

1. 在 `DEVICE/iot-common/iot-common-web/src/test/java/com/basiclab/iot/mybatis/desensitize/core/DesensitizeTest.java` 第一个昵称断言中，仅把期望字符串 `芋***` 机械改为 `B*********`；
2. `setNickname("BasicLab源码")`、其余 13 个断言、类/方法/注解/import 和测试数量必须原样保持；不得重命名、拆分、disable、加 assumption、动态计算期望值或修改字符编码；
3. 当前测试文件 SHA-256 为 `403B2D6B95909474BCB31AB2449E7BF750B3E3EB957FA357EFE863CAD858EBEF`；实现后必须报告新摘要，scoped diff 必须只包含上述一个字符串 literal；
4. 三个生产保护文件必须分别保持：`ChineseNameDesensitization.java` `0DD501489F0AF4E6E2F7CC9531A85D964312B93F9486545730C0779FF7DA5CB6`、`AbstractSliderDesensitizationHandler.java` `43A4E9EEC2BDF2E45D65F9AC2908706D351A60F80CCF6A4E3C303B41F6DD81E3`、`ChineseNameDesensitize.java` `8331EDFAD23952962CD5A3CCAA4194653309CD2BCA2BA9605F64BD9CAFBA58B0`；
5. §25.5 的五个 R1 文件、§24.3 五组聚合、V009 及 R1 三个 EMQX 单文件摘要继续全部冻结不变。

唯一可写文件为上述 `DesensitizeTest.java`、本任务单和 `M1-SDD进度与续作入口.md`。不得修改任何生产源码、其他测试、POM、脚本、SQL、配置、`.env`、Compose、Docker 资产或其他文档；不得新增 helper/script/file，不得 commit，不得清理或归属用户并行改动。

### 26.3 `DEVICE/.env` 本地凭据来源书面例外

决策所有者已经书面授权本地 R2 从 `DEVICE/.env` 读取 `LC02_V009_PG_PASSWORD` 和 `LC02_08_PG_PASSWORD`。本节据此替代 §25.4 的“调用进程预注入”要求，但只对本地隔离验收有效，并冻结以下安全边界：

1. `DEVICE/.env` 必须继续被 `DEVICE/.gitignore` 命中且不在 `git ls-files`；文件不得修改、复制、提交、挂入交付物或作为生产凭据来源；
2. 只允许按精确键名读取上述两项；每项必须恰好出现一次且值非空。只允许报告 `2/2 present`，不得输出值、长度、hash、部分字符、完整 JDBC URL、文件内容或环境变量清单；
3. 四个 Linux Maven 容器和单类直接测试必须在仓库 bind mount 之上，再以仓库外临时空文件只读覆盖 `/workspace/DEVICE/.env`，确保 Maven/JVM 无法读取宿主凭据文件；空遮罩文件必须在 `finally` 删除；
4. 只有四个 Java 阶段、bash 静态语法预检全部通过后，才重新读取两项并写入当前 PowerShell 进程环境；V009 与 Inbox 脚本各自只继承其所需的临时 `PG_PASSWORD`，脚本结束立即清除通用 PG/JDBC 临时变量；两段 PG 完成或任一异常后，两个源环境变量也必须在 `finally` 清除；
5. 禁止从 `postgres-server`、Compose、历史命令、日志或对话复制替代值，禁止把真实值写入本任务单、SDD、命令行 literal、临时脚本或报告；
6. 由于凭据值曾在对话中出现，它只能用于本地隔离验收，正式环境或任何共享环境使用前必须外部轮换并确认旧值失效。本包不执行轮换，也不批准生产复用。

冻结凭据前检函数如下；执行者不得把返回值写到管道或输出：

```powershell
$lc02EnvFile = (Resolve-Path 'DEVICE/.env').Path
git check-ignore --quiet -- 'DEVICE/.env'
if ($LASTEXITCODE -ne 0) { throw 'LC02_R2_ENV_NOT_IGNORED' }
git ls-files --error-unmatch -- 'DEVICE/.env' *> $null
if ($LASTEXITCODE -eq 0) { throw 'LC02_R2_ENV_IS_TRACKED' }

function Read-Lc02R2Secret([string]$Name) {
  $pattern = '^' + [regex]::Escape($Name) + '='
  $matches = @(Get-Content -LiteralPath $lc02EnvFile | Where-Object { $_ -match $pattern })
  if ($matches.Count -ne 1) { throw "LC02_R2_SECRET_CARDINALITY: $Name" }
  $secret = $matches[0].Substring($matches[0].IndexOf('=') + 1).Trim()
  if (($secret.StartsWith('"') -and $secret.EndsWith('"')) -or
      ($secret.StartsWith("'") -and $secret.EndsWith("'"))) {
    $secret = $secret.Substring(1, $secret.Length - 2)
  }
  if ([string]::IsNullOrWhiteSpace($secret)) { throw "LC02_R2_SECRET_EMPTY: $Name" }
  return $secret
}

foreach ($name in @('LC02_V009_PG_PASSWORD', 'LC02_08_PG_PASSWORD')) {
  $secret = Read-Lc02R2Secret $name
  Remove-Variable secret -ErrorAction SilentlyContinue
}
Write-Host 'LC02_R2_CREDENTIALS=2/2_PRESENT_REDACTED'
```

### 26.4 Linux 单类资格、完整验收命令与顺序

R2 继续使用 §25.3 的 Docker/Linux/镜像摘要、禁网、本地 Maven repository、`/tmp` tmpfs、固定容器名和零残留要求。开始前创建仓库外临时空遮罩文件：

```powershell
$repoPath = (Resolve-Path '.').Path
$m2Path = (Resolve-Path (Join-Path $env:USERPROFILE '.m2/repository')).Path
$lc02EnvMask = New-TemporaryFile
```

所有 Maven `docker run` 命令必须在 §25.3 的两个既有 bind mount 后机械增加以下只读覆盖，结束在 `finally` 删除 `$lc02EnvMask`：

```powershell
--mount "type=bind,source=$($lc02EnvMask.FullName),target=/workspace/DEVICE/.env,readonly"
```

实现后的第一条直接资格命令固定为：

```powershell
docker run --rm --name lc02-10-r2-desensitize --network none `
  --mount "type=bind,source=$repoPath,target=/workspace" `
  --mount "type=bind,source=$m2Path,target=/root/.m2/repository" `
  --mount "type=bind,source=$($lc02EnvMask.FullName),target=/workspace/DEVICE/.env,readonly" `
  --tmpfs /tmp:exec,size=1g -w /workspace `
  maven:3.9.16-amazoncorretto-17-alpine `
  mvn -f DEVICE/pom.xml test -pl iot-common/iot-common-web -am `
  '-Dtest=DesensitizeTest' '-Dsurefire.failIfNoSpecifiedTests=false' `
  '-Dmaven.test.skip=false'
if ($LASTEXITCODE -ne 0) { throw 'LC02_R2_DESENSITIZE_DIRECT_FAILED' }
```

单类必须实际 **1/1**、Failures=0、Errors=0、Skipped=0。随后完整验收必须从零按以下单一顺序执行，任一阶段非零立即停止：

1. 重新执行 §25.3 四个 Linux Java 命令，容器名前缀改为 `lc02-10-r2-`，并全部增加 `.env` 空文件覆盖；必须分别得到 34-reactor `test-compile`、device 39/39、sink 155/155 和完整模块 `BUILD SUCCESS`；
2. 完整模块命令仍精确使用 `-pl iot-sink/iot-sink-biz -am` 与 `-Dtest=*Test,!TDengineTelemetryStoreContractTest,!TDengineIdempotencySpikeTest,!JdbcTelemetryQueryAdapterContractTest,!TDengineTelemetryQueryAdapterContractTest`。必须逐 reactor 报告 Tests/Failures/Errors/Skipped；四个排除类保持明确列出，其他依赖或 LC02 测试不得排除、未发现或非法 skip；
3. 四条 §24.5 `bash -n` 静态语法预检；
4. 静态预检通过后，以 `Read-Lc02R2Secret` 分别导入两个源进程变量，再原样执行 §24.5 的 V009 隔离 PostgreSQL **79/79**（前缀 `lc02_v009_close`）与 Inbox 隔离 PostgreSQL **49/49**（前缀 `lc02_08_close`）；Failures/Errors/Skipped 和数据库/备份/夹具残留均为 0；
5. 清除两个源密码和全部临时 PG/JDBC 环境变量后，原样执行 §24.5 Java fail-closed **11/11** 与真实 EMQX **12/12**；
6. 原样执行 §24.5 两个禁止扫描、scoped `git diff --check`、五组聚合及全部单文件保护摘要复算、测试单文件精确 diff、容器/网络/数据库/临时目录/遮罩文件/凭据环境残留检查。

凭据导入与无条件清理必须使用以下边界，不得省略 `finally`：

```powershell
try {
  $env:LC02_V009_PG_PASSWORD = Read-Lc02R2Secret 'LC02_V009_PG_PASSWORD'
  $env:LC02_08_PG_PASSWORD = Read-Lc02R2Secret 'LC02_08_PG_PASSWORD'
  # 此处仅执行 §24.5 两段 PostgreSQL 隔离脚本。
} finally {
  foreach ($name in @(
    'LC02_V009_PG_PASSWORD', 'LC02_08_PG_PASSWORD', 'PG_PASSWORD',
    'LC02_08_PG_URL', 'LC02_08_PG_USERNAME', 'LC02_08_PG_PASSWORD',
    'LC02_V009_PG_ENABLED', 'LC02_08_PG_ENABLED', 'LC02_V009_DB_PREFIX',
    'LC02_08_DB_PREFIX', 'LC02_08_JDBC_HOST', 'LC02_08_JDBC_PORT'
  )) {
    Remove-Item "Env:$name" -ErrorAction SilentlyContinue
  }
}
```

### 26.5 停工条件、完成定义与授权状态

以下任一情形立即停工：旧测试无法通过单 literal 修正；需要改输入/生产实现/其他断言；`DEVICE/.env` 未忽略、已跟踪、键重复/缺失/空白或遮罩失败；任何 secret 被输出；镜像/摘要/Docker 平台漂移；直接测试不是 1/1；完整命令需要去掉 `-am`、排除新测试或继续失败；任一 Java/PG/EMQX failure/error/非法 skip；保护摘要漂移；临时资源或环境变量不能清理；出现白名单外新增 diff；并行工作区无法隔离。实现者不得顺手修复下一个缺陷、不得改命令降级、不得继续后继阶段收集抵消证据。

只有单测试 diff 经 Sol 审查、直接 1/1 通过、§26.4 从头完整通过、五组与所有单文件摘要一致、两个禁止扫描和 scoped diff 通过、除单测试与两份文档外零新增 diff、所有临时资源和凭据变量残留为 0，Sol 才可把 `LC02-10-R2`、`LC02-10-R1` 与 `LC02-10` 标记 COMPLETE / SOL-ACCEPTED，并把 M1-LC-02 转为 Implemented / Verified-Local。正式 V009、生产 broker/ACL/TLS、Linux PTY/跨进程锁、资源/稳定性压测、Windows 发布资格和现场验证仍继续 OPEN，不因本地接受而关闭。

本节当前状态为 **FROZEN / NOT-YET-AUTHORIZED**。本轮只授权 GPT-5.6 Sol 形成 R2 冻结合同，没有修改 `DesensitizeTest`，没有运行 Maven/Docker/PG/EMQX，也没有批准 commit。下一步必须由决策所有者独立授权“GPT-5.6 Luna Max 执行 LC02-10-R2 §26.2～§26.5 实现与测试”；若 Luna Max 不可用，不得静默替换，须明确报告后再由 owner 决定是否授权 Sol 接管。

### 26.6 Luna 单 literal 落盘、Sol 接管授权与保护摘要前检停工（2026-08-25）

决策所有者先独立授权 GPT-5.6 Luna Max 执行 §26.2～§26.5。Luna 在白名单内只把 `DesensitizeTest` 的昵称期望 `芋***` 改为 `B*********`，输入、其他断言、生产实现和测试数量未变；新 SHA-256 为 `852A24BB6009A500A425FDA25E2307CBABDE9C523551E93C9D7A77014BF8E2C7`，scoped diff 仅一删一增。Luna 两个执行回合均未启动 Docker 或返回测试退出码，Sol 明确报告运行限制后停止代理，没有把“运行中”冒充通过。决策所有者随后于 **13:30** 明确授权“GPT-5.6 Sol 接管 LC02-10-R2 §26.3～§26.5 完整验收与证据收口”。

Sol 接管后重新读取《EasyAIoT 项目开发宪法》v1.6.0 与《平台功能计划》v1.5.0，并在启动任何测试前完成冻结前检。通过项为：Docker Server `linux/29.7.2`；本地 Maven、EMQX、PostgreSQL 三张镜像摘要分别精确保持 §25.3 的 `53215f45...1772a`、`556aea6d...15e7d`、`3a82e1f5...744a`；固定 `lc02-10-r2-*` 容器残留 0；`DEVICE/.env` 继续 ignored / untracked，两项精确键均唯一非空，凭据基数前检只报告 `2/2_PRESENT_REDACTED`；Inbox/Outbox 25、Store 9、ACK/投影 5、EMQX 4 聚合分别仍为 `B2B72C3...54F2C`、`B9AC52AA...F604`、`6E2A8086...30C9B`、`EA6DCFD...09BC`；R1 五个单文件、三个生产脱敏文件、V009、ACL、真实测试和隔离脚本单文件摘要全部一致。没有读取、回显、复制或向子进程注入凭据值。

迁移 13 文件保护前检未通过。按 §21.5 明示的 V008/V009/U009/V010、V009 窗口申请单、runner、COMMENT gate、env、README、LC02-07 脚本、`iot-device10.sql`、precheck 与 `init-databases.sh` 路径集合，以及“路径升序 + TAB + 小写文件 SHA-256 + LF，末尾 LF”算法，当前聚合为 `fd59868a2d2075a0b8ba835d77a9802482d43bec1e6411d749e3d2d305fc76a3`，不等于冻结值 `2ab39079fb84d8ba42b056cbbdc25685fc0461f82eda762565f620eada662adb`。Git 事实确认当前 HEAD `78939834`（2026-08-25 12:04，`feat(prd-02): gate V011 review-only runner`）已在本 R2 白名单外修改以下四个受保护文件：

1. `.scripts/postgresql/td005-migration/td005_migration.sh`；
2. `.scripts/postgresql/td005-migration/check_ddl_comments.sql`；
3. `.scripts/postgresql/td005-migration/env.example`；
4. `.scripts/postgresql/td005-migration/README.md`。

该事实精确命中 §26.5 的“保护摘要漂移 / 白名单外并行变化”停工条件。Sol 因此没有创建 `.env` 遮罩文件，没有启动 `lc02-10-r2-desensitize` 或任何后续 Maven 容器，没有运行四段 Java、bash、V009 PostgreSQL 79/79、Inbox PostgreSQL 49/49、Java fail-closed 11/11 或真实 EMQX 12/12，也没有修改、重启或连接 PostgreSQL/EMQX；测试证据必须如实记为 **NOT RUN**。前检产生的仓库外临时快照目录已在 `finally` 清理，固定 R2 容器残留仍为 0；`.env` 未修改，无凭据环境变量注入或残留；没有 commit。最终工作树核对还出现 `WEB/package.json`、`WEB/scripts/` 与 `iot-device` 告警域资源/实现/测试等范围外并行改动，Sol 未触碰、清理或归属这些文件。

安全更正：在停工后的零残留诊断中，Sol 的只读命令误输出了 `DEVICE/.env` 的**整体文件 SHA-256**。命令没有输出密码值、值长度、部分字符、完整 JDBC URL 或环境变量内容，且没有把密码注入任何测试子进程；但整体文件摘要仍属于由秘密文件派生的 hash，违反 §26.3 第 2 项的零 hash 输出规则，因此同时独立命中 §26.5 的“任何 secret 被输出”停工条件。本文不复述该摘要。对话中已出现过的本地凭据仍必须外部轮换并确认旧值失效，R3 不得把该值批准为共享或生产凭据。

当前状态转为 **STOPPED / PROTECTED-MANIFEST-DRIFT / NOT ACCEPTED**。该状态不是测试失败，也不能用 §25.9 的历史成功结果替代。下一步必须先由 Sol 在新的 `LC02-10-R3` 中只读审查 V011 四文件变化与 LC02-07/V009 保护边界，决定将迁移保护拆为不变量单文件/语义摘要还是接受新的完整聚合基线；在 R3 冻结并获得独立授权前，不得继续 §26.4、修改测试或回退 V011 提交。

## 27. `LC02-10-R3` V011 / LC02-07 分层保护边界与完整重验冻结单（2026-08-25）

### 27.1 冻结依据、事实审查与架构决策

本节同时依据《EasyAIoT 项目开发宪法》v1.6.0（SHA-256 `76BF30903A5A62C957A75B57E24080AA7132DE6992D56C262EEAFBE7BE553C4D`）、《平台功能计划》v1.5.0（SHA-256 `F0E5734C8FADA6D0E4DAB98F7D12F58940077F9C60AA8DA42AC8EF45C6F69869`）、§26.6 停工证据、HEAD `78939834` 的 V011 review-only 提交和 [P02-M2-02A 本地静态验收记录](./P02-M2-02A-本地静态验收记录.md) 冻结。R3 仍是零业务功能、零生产实现、零接口/Schema/Topic/部署变化的本地关闭包，不授权 V011 临时/目标/共享/生产数据库执行，也不授权正式 V009、生产 broker、资源压测或现场资格。

Sol 逐行审查确认：`78939834` 只在共享 runner、COMMENT gate、env 示例和 README 中追加 V011/U011 显式 review-only 支路；V011/U011 不进入默认 dry-run、默认 `APPLY_STEPS` 或默认 uninstall。默认 `APPLY_STEPS` 仍精确为 `M05 M15 M16 V001 V002 V003 V004 V005 V006 V007 V008 V010 V009`，V009 仍要求 V008/V010 的 `SUCCEEDED + exact hash`，仍在单事务中执行 `V009_SQL` 并记录稳定 history/hash。V008/V009/U009/V010、V009 窗口申请单、LC02-07 真实合同、precheck、init、首装 dump 在该提交中均为零 diff；P02-M2-02A 已以 58 项静态/假命令断言证明 V011 只可显式 review-only，真实数据库调用和真实 DDL 均为 0。

§21.5 / §24.3 的旧迁移 13 文件聚合 `2ab39079...2adb` 同时包含 LC02 专属资产和可被后续迁移合法扩展的共享 runner/COMMENT/env/README，因此不能区分“V009 不变量漂移”和“已接受的新迁移追加”。R3 决定：旧聚合只保留为 R1/R2 历史快照，不再作为 R3 及其后继执行的当前阻断摘要；替代门禁为专属资产固定摘要、共享文件单回合一致性和可执行语义合同三层组合。该调整不接受当前全部共享文件内容为 LC02 产品事实，也不放松 V009 hash、依赖顺序、nullable expand、中文 COMMENT、首装 seed/schema、一致性、回滚拒绝或真实 PostgreSQL 79/79。

### 27.2 三层保护集合与摘要算法

所有固定聚合继续使用 §24.3 的 `Get-Lc02ManifestHash` 算法：“仓库相对路径正序 + 一个空格 + 文件 SHA-256 大写 hex，以 LF 连接并保留末尾 LF，再对 UTF-8 bytes 做 SHA-256”。

**A. LC02/V009 专属不可变集（6 文件）**

```text
.doc/技术设计/电力运维云平台/assets/td005-migration/V008__iot_sink_telemetry_inbox.sql
.doc/技术设计/电力运维云平台/assets/td005-migration/V009__telemetry_inbox_product_identity.sql
.doc/技术设计/电力运维云平台/assets/td005-migration/U009__telemetry_inbox_product_identity.sql
.doc/技术设计/电力运维云平台/assets/td005-migration/V010__telemetry_quality.sql
.doc/技术设计/电力运维云平台/assets/td005-migration/V009落库窗口申请单-20260824.md
.scripts/postgresql/td005-migration/tests/lc02_v009_contract.sh
```

固定聚合为 `3FD4294B822C6811E93C4A47C10CAF485656CF5971E57D7C7E1E10EAE38B45AA`。六个单文件 SHA-256 依次为 `693C0473386048567886B382C8C984AB98A267B7E7CE8659307B0D7395048469`、`48416787B7FC886CC3274BE53F3A38C60F9A9DD93CA205E3F0311D54A8EAFBDE`、`5D3E83C95FBC95CF7F097E2C385431C9356E2838270480F9A7FFED3121CFF273`、`08D809A4453E1E0EFD16F29522F0682E3B9A10B20DF4F04BE4A93F7D200D6662`、`AA2A07816B405CDCD6F24E1A06D450BBE85B8162D8D3289A2B35ABD84E5455DB`、`66D9E9F52DDB11AD943012FFC1C4BFB36135E496616F648A56E86A4840C8162F`；交付必须报告完整实际值，不得只比对缩写。

**B. V011 review-only 专属不可变集（3 文件）**

```text
.doc/技术设计/电力运维云平台/assets/td006-migration/V011__alarm_core_candidate.sql
.doc/技术设计/电力运维云平台/assets/td006-migration/U011__alarm_core_candidate.sql
.scripts/postgresql/td005-migration/tests/p02_v011_contract.sh
```

固定聚合为 `30EC12B49685BE7FDF7BD471651358F9A25094FCAA90734E85DD874BAC3954D0`；单文件分别为 `C121B1B26334D7B89BF526CB28273B2E931896ABA71B9072362A9E3EDE377269`、`3C2DAEA944696A36B89A65818F9D5A2DED454BF0C1115569E527F85DE77D74EA`、`80AA9D4A9797ACD7125DDEA1C15A80C86FE03AAE2FDAE9B1FD546865E47D28A1`。本集合只保护已静态接受的候选资产和假命令合同，不授权执行 V011/U011 DDL。

**C. 运行防护固定集（2 文件）**

`precheck_runtime_profile.sql` 与 `.scripts/docker/init-databases.sh` 固定聚合为 `8D9AE0CF686CBCD735F5D8B20E0308A5A33A5B8DE3BA5AD5349CDF7A4E81D865`，单文件仍分别为 `AC382AF6D06F342B34A21285A61FF6E244F556871E407B155747E3C215AA8CDD` 与 `ABFA13D48AFFBB7E7174D3D9E1738733A39D451868343C35AA30C2E7A3E87EDD`；不可变 LC02-07 合同脚本也必须继续内置并前后复核这两个精确值。

**D. 共享可演进集（5 文件）**

```text
.scripts/postgresql/td005-migration/td005_migration.sh
.scripts/postgresql/td005-migration/check_ddl_comments.sql
.scripts/postgresql/td005-migration/env.example
.scripts/postgresql/td005-migration/README.md
.scripts/postgresql/iot-device10.sql
```

这五个文件不再绑定 R2 前的历史整文件摘要。执行回合开始时必须用 `Get-Lc02ManifestHash` 记录一个 `sharedStart`，结束时复算 `sharedEnd` 并逐字节相等；其作用仅是阻断验收期间的并行漂移，不把未来合法追加自动视为 LC02 破坏。每次开工仍必须通过 §27.3 语义门禁；任一共享文件在回合内变化、V009/V011 语义合同失败或需要修改共享文件，立即停工。

Inbox/Outbox 25、Store 9、ACK/投影 5、EMQX 4 仍精确保持 §24.3 聚合；§25.5 R1 文件、§26.2 三个生产脱敏文件与当前 `DesensitizeTest` SHA-256 `852A24BB6009A500A425FDA25E2307CBABDE9C523551E93C9D7A77014BF8E2C7` 继续冻结。R3 不改变这些边界。

### 27.3 共享 runner 的唯一语义前检

R3 执行者必须在创建 Maven 容器或导入 PostgreSQL 凭据前，使用 Windows Git Bash 执行以下只读门禁；不得使用 WSL、真实 PostgreSQL 或 Docker 替代 P02 的假命令合同：

```powershell
$gitBash = 'C:\Program Files\Git\bin\bash.exe'
if (-not (Test-Path -LiteralPath $gitBash -PathType Leaf)) {
  throw 'LC02_R3_GIT_BASH_MISSING'
}

foreach ($script in @(
  '.scripts/postgresql/td005-migration/td005_migration.sh',
  '.scripts/postgresql/td005-migration/tests/lc02_v009_contract.sh',
  '.scripts/postgresql/td005-migration/tests/lc02_08_inbox_product_contract.sh',
  '.scripts/emqx/lc02-09/lc02_09_emqx_acl_contract.sh',
  '.scripts/postgresql/td005-migration/tests/p02_v011_contract.sh'
)) {
  & $gitBash -n $script
  if ($LASTEXITCODE -ne 0) { throw "LC02_R3_BASH_SYNTAX_FAILED: $script" }
}

$runner = Get-Content -Raw -LiteralPath '.scripts/postgresql/td005-migration/td005_migration.sh'
$expectedApply = 'APPLY_STEPS=(M05 M15 M16 V001 V002 V003 V004 V005 V006 V007 V008 V010 V009)'
if (([regex]::Matches($runner, [regex]::Escape($expectedApply))).Count -ne 1) {
  throw 'LC02_R3_DEFAULT_APPLY_STEPS_DRIFT'
}
$applyLine = (Select-String -LiteralPath '.scripts/postgresql/td005-migration/td005_migration.sh' `
  -Pattern '^APPLY_STEPS=').Line
if ($applyLine -match 'V011|U011') { throw 'LC02_R3_V011_ENTERED_DEFAULT_PLAN' }
foreach ($literal in @(
  'V008_SQL="${V008_SQL:-${ASSET_DIR}/V008__iot_sink_telemetry_inbox.sql}"',
  'V010_SQL="${V010_SQL:-${ASSET_DIR}/V010__telemetry_quality.sql}"',
  'V009_SQL="${V009_SQL:-${ASSET_DIR}/V009__telemetry_inbox_product_identity.sql}"',
  'DEPENDENCY_NOT_SATISFIED V009',
  'cat "${V009_SQL}"'
)) {
  if (([regex]::Matches($runner, [regex]::Escape($literal))).Count -lt 1) {
    throw "LC02_R3_V009_RUNNER_SEMANTIC_MISSING: $literal"
  }
}

$dryRun = & $gitBash -lc `
  'export PATH=/usr/bin:/bin:$PATH; .scripts/postgresql/td005-migration/td005_migration.sh dry-run --db iot-device20' 2>&1
if ($LASTEXITCODE -ne 0 -or ($dryRun -join "`n") -notmatch 'V008 \(txn\) -> V010 \(txn\) -> V009 \(txn\)') {
  throw 'LC02_R3_V009_DRY_RUN_ORDER_FAILED'
}
if (($dryRun -join "`n") -match 'V011|U011') { throw 'LC02_R3_V011_VISIBLE_IN_DEFAULT_DRY_RUN' }

$p02 = & $gitBash -lc `
  'export PATH=/usr/bin:/bin:$PATH; .scripts/postgresql/td005-migration/tests/p02_v011_contract.sh' 2>&1
if ($LASTEXITCODE -ne 0 -or ($p02 -join "`n") -notmatch 'PASS_COUNT=58; real_database_calls=0; real_ddl=0') {
  throw 'LC02_R3_V011_STATIC_CONTRACT_FAILED'
}
```

此外必须只读确认 `check_ddl_comments.sql` 中 `iot_sink.telemetry_inbox.product_identification` 的 LC02-07 中文 COMMENT gate 仍恰好存在，`env.example` 仍包含 `# V009_SQL=`，README 仍声明 `V008 → V010 → V009` 和 V009/U009 边界；首装 dump 的最终结构、三条 seed/hash 与 runner 迁移库一致性不靠文本 hash 代替，必须由真实 V009 79/79 合同证明。

### 27.4 凭据、文件白名单与安全补偿

R3 完整执行继续沿用 §26.3 的本地 `.env` 书面例外、Maven 容器空文件只读覆盖和 PostgreSQL 阶段限定导入，但增加以下强制补偿：

1. 不得对 `DEVICE/.env` 或任何秘密值调用 `Get-FileHash`、摘要、编码、长度、substring、掩码预览或通用环境变量枚举；不得把 `.env` 放入任何 manifest、diff、归档、日志或报告；
2. 只允许精确检查两键各一且非空，并只输出 `2/2_PRESENT_REDACTED`；读取函数、导入时机和 `finally` 精确清理仍采用 §26.3～§26.4，不得输出函数返回值；
3. Maven 容器继续在仓库 bind 之后用仓库外临时空文件只读覆盖 `/workspace/DEVICE/.env`；遮罩存在性只报告布尔结果，不报告宿主秘密文件或遮罩文件 hash；
4. 已在对话中出现的凭据值及秘密文件派生摘要均视为已暴露材料，只允许本地隔离验收；共享/正式/生产使用前必须外部轮换并确认旧值失效；
5. 实际执行的新写入白名单仅为本任务单和 `M1-SDD进度与续作入口.md` 的验收证据。现有 `DesensitizeTest` 单 literal diff作为受保护输入保留，不得再次修改；生产、其他测试、POM、脚本、SQL、V011 资产、配置和其他文档全部只读；不得 commit；
6. 当前 WEB、告警域、`DEVICE/.claude/` 及任何后续范围外工作树变化均归并行工作所有者，R3 不得清理、格式化、暂存、提交或归属。只有它们不覆盖本节固定/共享保护路径时才可隔离继续。

### 27.5 完整重验唯一顺序

获得后续独立执行授权后，必须从零按单一顺序执行，任一非零或非法 skip 立即停止：

1. 复核双基线版本/摘要、Docker Linux 29.7.2、三张 §25.3 精确镜像、R2 固定容器零残留、`DesensitizeTest` 单 literal diff/摘要、A/B/C 三组固定聚合、§24/§25/§26 其他保护摘要；记录共享 5 文件 `sharedStart`，完成 §27.3 全部语义前检和 V011 静态 **58/58**；
2. 创建仓库外空遮罩文件，按 §26.4 原命令执行 `DesensitizeTest` Linux 直接 **1/1**；
3. 从头执行 §25.3 四段 Linux Java，容器名前缀继续使用 `lc02-10-r3-`，每段都增加 `.env` 空覆盖：34-reactor `test-compile`、device **39/39**、sink **155/155**、带 `-am` 的完整模块；完整模块仍只排除四个已冻结外部资格类，必须逐 reactor 报告 Tests/Failures/Errors/Skipped；
4. 再执行 §27.3 五个 `bash -n`；然后才按 §26.3 精确读取两组本地凭据，在 `try/finally` 中原样执行 V009 PostgreSQL **79/79**（前缀 `lc02_v009_close`）与 Inbox PostgreSQL **49/49**（前缀 `lc02_08_close`），零 skip、零临时库/备份/夹具残留；V009 脚本必须同时证明 full dump seed/hash/schema、V008→V010→V009 顺序、COMMENT、hash drift、U009 拒绝与 precheck/init 固定值；
5. 无条件清除两组源变量和全部 PG/JDBC 临时变量后，原样执行 Java fail-closed **11/11** 与真实 EMQX **12/12**；P02 V011 真实 PostgreSQL 合同仍 **NOT AUTHORIZED / NOT RUN**；
6. 执行 §24.5 两个禁止扫描、scoped `git diff --check`、A/B/C 固定聚合、Inbox/Outbox 25、Store 9、ACK 5、EMQX 4、R1/脱敏单文件摘要和单 literal 精确 diff；复算 `sharedEnd` 并要求与 `sharedStart` 完全一致；检查固定容器/网络/PG 前缀/临时目录/遮罩/精确凭据变量零残留。不得计算或输出 `.env` 或秘密值摘要。

§25.9 历史的前三段 Java 成功和 §26 的未运行状态均不得复用为 R3 当前通过；所有阶段必须在同一授权回合从头形成证据。

### 27.6 停工条件、完成定义与授权状态

以下任一情形立即停工：A/B/C 固定聚合或既有 §24～§26 保护漂移；共享语义门禁失败；V011/U011 出现在默认计划/dry-run；P02 静态不是 58/58 或产生真实数据库/DDL 调用；`sharedStart != sharedEnd`；需要修改任何脚本/SQL/测试/生产文件；`.env` 被跟踪、键异常、遮罩失败或输出任何值/长度/部分/hash；任一 Java/PG/EMQX failure/error/非法 skip；完整命令需要去掉 `-am` 或新增排除；临时资源不能清理；范围外并行改动覆盖保护路径。不得顺手修复、回退 V011、重写历史摘要、使用旧测试报告或执行 V011/U011 真实 DDL。

只有 §27.3～§27.5 全部通过、A/B/C 与既有保护摘要一致、共享文件单回合零漂移、测试单 literal 经 Sol 复核、所有临时资源与精确凭据变量残留为 0，Sol 才可把 `LC02-10-R3`、`LC02-10-R2`、`LC02-10-R1` 与 `LC02-10` 标记 COMPLETE / SOL-ACCEPTED，并把 M1-LC-02 转为 Implemented / Verified-Local。该结论仍不批准正式 V009、V011 任何真实数据库合同/落库、生产 broker/ACL/TLS、Linux PTY/锁互操作、资源/稳定性压测、Windows 发布资格或现场验证。

本节当前状态为 **FROZEN / NOT-YET-AUTHORIZED**。本轮 owner 只授权 GPT-5.6 Sol 重新收敛并冻结 R3 保护边界；没有运行 bash/Maven/Docker/PostgreSQL/EMQX，没有修改测试/生产/脚本/SQL/配置，没有读取 `.env`，没有 commit。下一步须决策所有者独立授权 GPT-5.6 Luna Max 执行 `LC02-10-R3 §27.3～§27.6`；若 Luna Max 不可用，不得静默替换，须明确报告后再由 owner 决定是否授权 Sol 接管。

### 27.7 Sol 接管完整重验在 OPEN03 组合测试停工（2026-08-25）

决策所有者先独立授权 GPT-5.6 Luna Max 执行 §27.3～§27.6；连续两个 Luna Max 回合均没有工具回报、没有 `lc02-10-r3-*` 容器或其他可观察执行进展，Sol 明确中断，未静默替换。决策所有者随后书面授权“GPT-5.6 Sol 接管 LC02-10-R3 §27.3～§27.6 完整验收与证据收口”，本节据此形成当前回合证据。

开工前重读双基线并复算：宪法 v1.6.0 与平台计划 v1.5.0 摘要均等于 §27.1；Docker Server 为 Linux 29.7.2，Maven、EMQX、PostgreSQL 三张本地镜像 ID 均等于 §25.3；`.env` 为 ignored / untracked，两键唯一非空，只输出 `2/2_PRESENT_REDACTED`，未计算或输出秘密文件、秘密值的 hash、长度、部分字符、编码、预览或环境清单。A/B/C、Inbox/Outbox 25、Store 9、ACK 5、EMQX 4、R1 文件、三个生产脱敏文件及 `DesensitizeTest` 摘要全部一致；`sharedStart=C0D28DB92BCEBE67BEAD498B84F9DA9C9A2F1E4E9E3FF468EAE4AB9ECCC50DD0`。

§27.3 实际结果：五个脚本 `bash -n` 全部通过；默认 `APPLY_STEPS` 精确为 `M05 M15 M16 V001 V002 V003 V004 V005 V006 V007 V008 V010 V009`，默认 dry-run 精确包含 `V008 (txn) -> V010 (txn) -> V009 (txn)` 且不含 V011/U011；P02 V011 假命令合同为 **58/58**，`real_database_calls=0`、`real_ddl=0`；COMMENT gate、`# V009_SQL=` 与 README 顺序/回滚边界均通过。第一次补充 COMMENT 检查错误地要求不存在的三段式组合 literal，随后只读定位确认当前 gate 由表名、列名及中文说明分别组成，并以五个精确语义片段各一次重新通过；该检查器误判没有修改文件，也不是仓库语义漂移。

Linux Docker Java 从零按冻结顺序实际执行：

1. `DesensitizeTest` 直接 **1/1**，Failures=0、Errors=0、Skipped=0，10/10 reactor `BUILD SUCCESS`；
2. 联合 `test-compile` **34/34 reactor BUILD SUCCESS**；
3. device 七类 **39/39**，Failures=0、Errors=0、Skipped=0，33/33 reactor `BUILD SUCCESS`；
4. sink 二十九类 **155/155**，Failures=0、Errors=0、Skipped=0，28/28 reactor `BUILD SUCCESS`；`SqliteOutboxRouteBackfillApplierTest` **31/31** 在 Linux 中实际执行，没有 symlink skip/assumption；
5. 完整 `iot-sink-biz -am` 继续只排除冻结的四个外部资格类；依赖 reactor 的 `DesensitizeTest` 再次 **1/1** 通过，但进入 `iot-sink-biz` 后 `CollectorOpen03CombinedApplyStageTest.applyConfiguredStage(Path)` 实际 Tests=1、Failures=0、Errors=1、Skipped=0，错误为 `CollectorConfigStateException: COLLECTOR_CONFIG_PERMISSION_INVALID`，调用点为该测试第 122 行 `provider.reconcile(graph::set)`。测试文件为 tracked-clean，SHA-256 `04D814041BF47748C4D782D55CBF08E9B34D33662F69CFE00D00A80C1DD153BA`。Sol 立即中断完整命令，因此该命令没有形成最终 reactor/总测试汇总，不能标记通过。

依 §27.6，失败后没有继续第二轮 `bash -n`、V009 PostgreSQL 79/79、Inbox PostgreSQL 49/49、Java fail-closed 11/11 或真实 EMQX 12/12；V011/U011 真实数据库/DDL 继续 **NOT AUTHORIZED / NOT RUN**。两组 PostgreSQL 凭据没有导入环境或传给任何子进程。中断后空遮罩文件、`lc02-10-r3-*` 容器、`lc02_v009_close*` / `lc02_08_close*` 网络与数据库、精确 PG/JDBC 环境变量残留均为 0。

STOP 后的第一次收尾快照中，A/B/C、Inbox/Outbox 25、Store 9、ACK 5、EMQX 4 与全部 R1/脱敏关键单文件摘要均一致；`sharedEnd=C0D28DB92BCEBE67BEAD498B84F9DA9C9A2F1E4E9E3FF468EAE4AB9ECCC50DD0`，与 `sharedStart` 相等；scoped `git diff --check` 通过。初次证据回填后的最终工作树复核又发现并行改动覆盖保护集 B：`V011__alarm_core_candidate.sql` 与 `p02_v011_contract.sh` 变为 modified，观察时两个单文件 SHA-256 分别为 `20CF0219FFF757F0397D7DE858885394234FCA822AD7EE426E1B8E67550B6A7F`、`327E0E94176B7FF8B65DB2CA31DCA2DDD529AB873035AD21CCC375379EFD5174`，B 聚合变为 `1AB2C4FA56BE22037EE735616EF6E0E3A8898355E81E7E8B40AAF14D7E47520E`；未改的 U011 仍为冻结摘要。该变化发生在第一次收尾摘要快照之后，本轮没有修改、清理或归属这些并行文件；依 §27.6，它构成除 Java Error 外的第二个独立 STOP 条件，当前不能再声称保护集 B 仍一致。除本任务单和 M1 SDD 证据外，Sol 没有修改生产、测试、脚本、SQL、配置或范围外并行工作，也没有 commit。

结论：`LC02-10-R3`、`LC02-10-R2`、`LC02-10-R1`、`LC02-10` 与 M1-LC-02 均保持 **STOPPED / FULL-RETEST-FAILED / NOT ACCEPTED**，不得解锁。下一步须由 Sol 只读分析 `CollectorOpen03CombinedApplyStageTest` 的 standalone fixture 权限与生产 fail-closed 合同，再决定是否冻结新的有界修订包；未经新的独立授权不得修改测试/生产实现或继续 PostgreSQL/EMQX 后继门禁。正式 V009、生产 broker/ACL/TLS、Linux PTY/锁互操作、资源/稳定性压测、Windows 发布资格与现场验证继续 OPEN。

## 28. `LC02-10-R4` standalone POSIX fixture 修订与 P02 隔离门禁冻结单（2026-08-25）

### 28.1 双基线、根因结论与保护边界决策

本节同时依据《EasyAIoT 项目开发宪法》v1.6.0（SHA-256 `76BF30903A5A62C957A75B57E24080AA7132DE6992D56C262EEAFBE7BE553C4D`）、《平台功能计划》v1.5.0（SHA-256 `F0E5734C8FADA6D0E4DAB98F7D12F58940077F9C60AA8DA42AC8EF45C6F69869`）和 §27.7 的两个 STOP 事实冻结。R4 是一个测试 fixture 机械修订加完整重验包，不新增功能，不改变产品范围、Schema、Topic、接口、生产权限、部署或安全合同。

Sol 只读复核得到唯一根因：生产 `LocalFilePollingConfigProvider` 对既有配置目录和文件精确 fail-closed 校验 `02770/0660`；其当前 SHA-256 为 `F56180592AF8F3022998205FDA70F8316C0167B6FCE9A07CEBFF82C4D30DB0C8`。`LocalFilePollingConfigProviderTest` 与 `CollectorPollingRuntimeTest` 已在 POSIX 文件系统上按生产常量准备目录/文件权限；只有 `CollectorOpen03CombinedApplyStageTest.runStandaloneApplyContract(...)` 创建 `@TempDir` 子目录和 `desired.json` 后直接调用 `reconcile`，没有准备权限。Linux 默认 mode 因而被生产实现正确拒绝。这是 standalone 测试 fixture 漂移，不是生产缺陷；不得放宽、绕过或修改 Provider 的精确权限合同。

§27 的固定保护集 B 把 LC02 验收绑定到正在演进的 M2/P02 V011 候选 DDL 和静态测试整文件摘要，已被本轮并行增加 `alarm_record.last_action_sequence` 及配套断言合法触发。R4 不接受、批准或验证这些告警域变化，也不把其当前内容提升为 LC02 产品事实。LC02 只保护以下隔离不变量：V011/U011 不得进入默认 `APPLY_STEPS`、默认 dry-run 或默认 uninstall；执行 P02 静态假命令合同时必须退出 0、报告正数 `PASS_COUNT`、`real_database_calls=0`、`real_ddl=0`。V011、U011 与 P02 脚本仅在同一 R4 执行回合开始/结束各取 manifest 并要求相等，以阻断验收期间并行漂移；不再要求等于 §27.2 B 的历史固定摘要，也不授权真实 V011/U011 数据库或 DDL。

§27.2 A 的 6 个 LC02/V009 专属文件固定聚合 `3FD4294B822C6811E93C4A47C10CAF485656CF5971E57D7C7E1E10EAE38B45AA`、C 的 2 个运行防护文件固定聚合 `8D9AE0CF686CBCD735F5D8B20E0308A5A33A5B8DE3BA5AD5349CDF7A4E81D865`，以及 Inbox/Outbox 25、Store 9、ACK/投影 5、EMQX 4、R1/脱敏、`DesensitizeTest` 和 §27.2 D 的共享 5 文件单回合保护全部继续有效。R4 不重写 R3 历史证据。

### 28.2 唯一实现白名单与精确机械改动

获得独立实现授权后，唯一可修改的实现/测试文件是：

```text
DEVICE/iot-sink/iot-sink-biz/src/test/java/com/basiclab/iot/sink/protocol/polling/CollectorOpen03CombinedApplyStageTest.java
```

执行者只可在该文件增加 `LinkOption`、`PosixFileAttributeView` 所需 import，并只在 `runStandaloneApplyContract(Path directory)` 内完成以下机械改动：

1. `Files.createDirectories(directory)` 后，以 `Files.getFileAttributeView(directory, PosixFileAttributeView.class, LinkOption.NOFOLLOW_LINKS) != null` 判断 POSIX 能力；存在时用 `Files.setAttribute(..., "unix:mode", LocalFilePollingConfigProvider.LINUX_CONFIG_DIRECTORY_MODE, LinkOption.NOFOLLOW_LINKS)` 设置目录 mode；
2. 将 `desired.json` 保存为局部 `Path desired`，保持 canonical bytes 原样写入；写入后用同一 POSIX 能力判断把它设置为 `LocalFilePollingConfigProvider.LINUX_CONFIG_FILE_MODE`；
3. 保持 Provider 构造、`reconcile`、APPLIED/version/hash/graph/active bytes 断言不变。除为复用 `desired` 局部变量所需的等价引用外，不得改其他语义。

禁止修改该类的 repository-wide staged E2E 分支、增加 assumption/skip、按 OS 禁用测试、硬编码另一套 mode、增加 helper/新文件或移动测试；禁止修改生产 Provider、其他测试、POM、脚本、SQL、配置和范围外文件。POSIX capability guard 仅保证 Windows/非 POSIX 文件系统仍可运行 standalone 合同，不得在 Linux POSIX 上跳过权限准备或生产校验。

完成机械改动后，先在 §25.3 的精确 Linux Maven 17 镜像、禁网、仓库 `.env` 空文件只读覆盖条件下依次执行：

1. `CollectorOpen03CombinedApplyStageTest` 直接 **1/1**，Failures=0、Errors=0、Skipped=0；
2. `CollectorOpen03CombinedApplyStageTest,LocalFilePollingConfigProviderTest,CollectorPollingRuntimeTest` 联合 **16/16**，Failures=0、Errors=0、Skipped=0。

上述任一不满足立即停止，不得进入完整验收。执行者必须报告测试文件修改前后完整 SHA-256、精确 diff、命令、退出码和 Surefire Tests/Failures/Errors/Skipped；不得只报告 `BUILD SUCCESS`。

### 28.3 P02/V011 隔离门禁与单回合 manifest

完整验收开工时对以下 3 个并行 P02 输入按 §24.3 算法记录 `p02Start`，收工复算 `p02End`，要求完全相等：

```text
.doc/技术设计/电力运维云平台/assets/td006-migration/V011__alarm_core_candidate.sql
.doc/技术设计/电力运维云平台/assets/td006-migration/U011__alarm_core_candidate.sql
.scripts/postgresql/td005-migration/tests/p02_v011_contract.sh
```

仍须执行 §27.3 的五个 `bash -n`、默认 `APPLY_STEPS` 精确顺序、默认 dry-run 顺序与 V011/U011 不可见检查。P02 命令的通过条件改为：退出码为 0，输出匹配 `PASS_COUNT=[1-9][0-9]*; real_database_calls=0; real_ddl=0`；不得把 PASS_COUNT 固定为 58 或当前观察值，也不得从其通过推导 V011 DDL 已被 LC02 接受。任一 P02 输入在单回合内变化、进入默认链、产生真实数据库/DDL 调用或无法证明隔离，立即停工并交由 P02 所有者处理；R4 不得修改、回退或提交它们。

### 28.4 从零完整验收顺序

§28.2 的 1/1 与 16/16 通过后，必须从零重新执行 §27.3～§27.5 的完整顺序，不能从 R3 失败点续跑、复用 §27.7 结果或缩减 `-am`：

1. 复核双基线、Docker Linux 29.7.2、三张冻结镜像、A/C 固定聚合和所有既有 LC02 保护摘要；记录共享 5 文件 `sharedStart` 与 P02 3 文件 `p02Start`，通过 §28.3 语义前检；
2. `DesensitizeTest` Linux 直接 **1/1**；34-reactor `test-compile`；device 七类 **39/39**；sink 二十九类 **155/155**，包括 Linux symlink fixture 实际执行；
3. 完整 `iot-sink-biz -am` 仅排除既有四个外部资格类，逐 reactor 报告汇总，不得增加排除或去掉 `-am`；
4. 再跑五个 `bash -n`，随后按 §26.3 的秘密零输出规则与 `try/finally` 依次执行 V009 PostgreSQL **79/79**、Inbox PostgreSQL **49/49**；V011/U011 真实数据库/DDL继续 NOT AUTHORIZED / NOT RUN；
5. 无条件清除 PG/JDBC 变量后执行 Java fail-closed **11/11** 与真实 EMQX **12/12**；
6. 完成禁止扫描、scoped `git diff --check`、A/C 与全部既有 LC02 摘要复核，要求 `sharedStart == sharedEnd`、`p02Start == p02End`，并确认容器、网络、数据库前缀、临时目录、遮罩和精确凭据变量残留为 0。不得计算或输出 `.env` 或秘密值的 hash、长度、片段、编码或预览。

R4 的新增测试文件 SHA-256 应在 §28.2 改动完成后建立为本回合受保护输入，并在完整验收结束复核相等。实际写入白名单除该测试文件外，仍只包括本任务单与 `M1-SDD进度与续作入口.md` 的证据回填；不得 commit，除非决策所有者另行明确授权。

### 28.5 停工条件、完成定义与授权状态

以下任一情形立即停工：实现 diff 超出 §28.2；生产 Provider 或 staged E2E 分支发生变化；Linux 1/1 或 16/16 不满足；A/C 或任一既有 LC02 保护摘要漂移；共享或 P02 manifest 单回合不相等；V011/U011 进入默认链；P02 产生真实数据库/DDL；需要新增排除、skip、放宽 mode 或修改脚本/SQL；任一 Java/PG/EMQX failure/error/非法 skip；秘密规则或清理规则失败；范围外并行改动覆盖保护路径。不得顺手修复或扩大白名单。

只有 §28.2～§28.4 全部通过、测试 diff 经 Sol 复核、所有保护与零残留成立，Sol 才可把 `LC02-10-R4`、`LC02-10-R3`、`LC02-10-R2`、`LC02-10-R1`、`LC02-10` 标记 COMPLETE / SOL-ACCEPTED，并把 M1-LC-02 转为 Implemented / Verified-Local。该结论仍不批准正式 V009/V011 落库、生产 broker/ACL/TLS 激活、Linux PTY/锁互操作、资源/稳定性压测、Windows 发布资格或现场验证。

本节当前状态为 **FROZEN / NOT-YET-AUTHORIZED**。本轮 Sol 只完成根因、边界和验收命令冻结；没有修改测试或生产实现，没有运行 R4 Java/PostgreSQL/EMQX 验收，没有读取 `.env`，没有执行 V011/U011 DDL，也没有 commit。下一步须决策所有者独立授权 GPT-5.6 Luna Max 执行 `LC02-10-R4 §28.2～§28.5`；若 Luna Max 不可用，不得静默替换，须明确报告后再由 owner 决定是否授权 Sol 接管。

### 28.6 Luna Max 执行、Sol skip 复核与 V009 PostgreSQL 停工（2026-08-25）

决策所有者已明确授权 GPT-5.6 Luna Max 执行 §28.2～§28.5。首个 Luna Max 回合完成唯一白名单修改后长时间没有测试退出码或报告，Sol 中断并启动同模型 max reasoning 精简重试；未静默替换模型。精简回合确认 diff 严格限于两个 import 与 standalone 目录/`desired.json` POSIX mode 准备：修改前 SHA-256 `04D814041BF47748C4D782D55CBF08E9B34D33662F69CFE00D00A80C1DD153BA`，修改后 `A5CA16342A74CD36E954AE03433F411AAE656C0AA2F92111A9058B1BCECB7D46`。生产 Provider、staged E2E 分支、其他测试、POM、脚本、SQL 和配置均未改。

§28.2 Linux Docker 直接门禁全部通过：`CollectorOpen03CombinedApplyStageTest` **1/1**；该类、`CollectorPollingRuntimeTest` 与 `LocalFilePollingConfigProviderTest` 联合 **16/16**（1+2+13）；两次均退出 0，Failures=0、Errors=0、Skipped=0，冻结 Maven 17 镜像、禁网、空 `.env` 只读遮罩生效，容器残留 0。

§28.3 开工语义门禁通过：A/C 与全部既有 LC02 保护摘要一致；`sharedStart=C0D28DB92BCEBE67BEAD498B84F9DA9C9A2F1E4E9E3FF468EAE4AB9ECCC50DD0`，`p02Start=1AB2C4FA56BE22037EE735616EF6E0E3A8898355E81E7E8B40AAF14D7E47520E`；五个 `bash -n`、默认 apply/dry-run/uninstall 的 V011/U011 隔离全部通过；P02 静态 **60 项**，`real_database_calls=0`、`real_ddl=0`，不构成 V011 DDL 接受或执行。

§28.4 Linux Java 从零结果：

1. `DesensitizeTest` **1/1**，Failures=0、Errors=0、Skipped=0；
2. 受影响 `test-compile` **34/34 reactor**；
3. device 七类 **39/39**，Failures=0、Errors=0、Skipped=0；
4. sink 二十九类 **155/155**，Failures=0、Errors=0、Skipped=0；Linux symlink fixture 实际 **31/31**，无 assumption/skip；
5. 完整 `iot-sink-biz -am` 严格保留 `-am` 和原四个外部资格类排除，退出码 0、`BUILD SUCCESS`，Tests=284、Failures=0、Errors=0、Skipped=1。Surefire TXT 把汇总文件命名为 `CenterTelemetryIngressHandlerTest`，但该源码精确只有 7 个 `@Test`，XML 的第 8 个、唯一 skipped testcase 的 `classname` 明确为 `EmqxTelemetryAclIntegrationTest`，原因是未设置 `LC02_09_EMQX_ENABLED`。Sol 依 §24.4 复核为允许随后真实 12/12 重跑的环境 skip，不是 Center 类非法 skip；未修改命令、排除、测试或报告来改变结论。

恢复后第二轮五个 `bash -n` 全部退出 0。Luna 随后按 §26.3 从本地 ignored/untracked `.env` 精确读取两键，仅报告 `2/2_PRESENT_REDACTED`；V009 隔离 PostgreSQL 合同脚本退出码 **1**。脚本输出只在内存捕获且未保存，为遵守失败即停没有复现，故当前具体失败阶段为 `failure_detail_not_captured`。该证据不足以诊断为凭据、Docker、SQL、fixture 或代码缺陷，也不能把 R3 的历史 79/79 当作本轮通过。

V009 失败后立即执行 `finally` 清除两个源密码、`PG_PASSWORD` 与全部 PG/JDBC 临时变量；Inbox PostgreSQL **49/49**、Java fail-closed **11/11**、真实 EMQX **12/12** 均 **NOT RUN**，V011/U011 真实数据库/DDL仍 **NOT AUTHORIZED / NOT RUN**。最终 `sharedEnd == sharedStart`、`p02End == p02Start`，A/C、Inbox/Outbox 25、Store 9、ACK/投影 5、EMQX 4、R1/脱敏及 R4 测试摘要均未漂移；scoped `git diff --check` 退出 0；R4 容器/网络、两类数据库前缀、遮罩与精确环境变量残留均为 0。未输出秘密值、长度、hash、片段、编码、预览、JDBC URL 或环境清单；未 commit。

结论：R4 唯一测试修订已实现且 Java 门禁通过，但完整关闭在 V009 PostgreSQL 失败，`LC02-10-R4` 转为 **IMPLEMENTED / JAVA-PASSED / PG-FAILED / NOT ACCEPTED**；R3/R2/R1/LC02-10 与 M1-LC-02 继续 STOPPED / NOT ACCEPTED。下一步必须先由 Sol 冻结新的有界 PostgreSQL 诊断重验包，规定失败阶段、退出码和脱敏日志的安全留证方式，再决定是环境处置还是实现任务；未经新冻结和独立授权不得直接重跑 V009、执行后继门禁或修改 SQL/脚本/生产代码。

## 29. `LC02-10-R5` V009 PostgreSQL 安全诊断重验与后继收口冻结单（2026-08-26）

### 29.1 冻结依据、当前事实与复用边界

本节同时依据《EasyAIoT 项目开发宪法》v1.6.0（SHA-256 `76BF30903A5A62C957A75B57E24080AA7132DE6992D56C262EEAFBE7BE553C4D`）、《平台功能计划》v1.5.0（SHA-256 `F0E5734C8FADA6D0E4DAB98F7D12F58940077F9C60AA8DA42AC8EF45C6F69869`）和 §28.6 的真实停工证据冻结。2026-08-26 开工核对时，R4 提交 `32fbc62c7` 是当前 HEAD `528e93736` 的祖先；工作树除现有未跟踪 `DEVICE/.claude/` 外干净。该目录属于并行/本地工具状态，R5 不得读取、清理、暂存、提交或归属。

R4 已在同一保护回合内通过 Linux 1/1、16/16、脱敏 1/1、34-reactor、device 39/39、sink 155/155 和完整 `-am`，随后才在 V009 PostgreSQL 首次执行退出 1；最终全部固定摘要、`sharedStart == sharedEnd`、`p02Start == p02End`、R4 测试 SHA 与零残留均成立。R5 是零实现的诊断续跑包：只要 §29.2 的保护前检完全通过，R4 的 Java 成功证据可以复用，不要求再次消耗完整 Java 回归；任一受保护文件、双基线、镜像、默认迁移语义或 R4 测试摘要漂移，则复用资格失效并立即停工，不得在 R5 内自行扩大为全量重验。

当前 `lc02_v009_contract.sh` SHA-256 仍为 `66D9E9F52DDB11AD943012FFC1C4BFB36135E496616F648A56E86A4840C8162F`。脚本使用 `set -euo pipefail`、稳定 `[lc02-v009][PASS]` / `[lc02-v009][FAIL]` 标签、唯一临时数据库前缀和 EXIT trap；没有 `set -x`，密码只通过进程环境传给 `docker exec`。R4 未保存原始输出，因此不能从退出码 1 推断为凭据、容器、SQL、fixture、保护摘要或产品代码缺陷。R5 不修改脚本，只增加执行编排层的安全留证。

### 29.2 开工保护与环境前检

获得独立执行授权后，必须在读取 `.env` 前完成：

1. 重读双基线并复算 §29.1 两个完整摘要；确认 `git merge-base --is-ancestor 32fbc62c7 HEAD` 成功；记录开工 `git status --short`，确认 LC02 受保护路径没有新增修改；
2. 复核 Docker Server 仍为 Linux 29.7.2、§25.3 PostgreSQL/EMQX/Maven 三张精确镜像、`postgres-server` 正在运行，且不得重启、重建或修改该容器；
3. 复核 §28 的 A/C、Inbox/Outbox 25、Store 9、ACK/投影 5、EMQX 4、R1/脱敏、R4 测试 `A5CA16342A74CD36E954AE03433F411AAE656C0AA2F92111A9058B1BCECB7D46`；记录共享 5 文件 `sharedStart` 与 P02 3 文件 `p02Start`；
4. 执行 §28.3 的五个 `bash -n`、默认 apply/dry-run/uninstall 隔离和 P02 静态合同；P02 仍只要求正数 PASS_COUNT、`real_database_calls=0`、`real_ddl=0`；
5. 检查 `lc02_v009_r5*`、`lc02_08_r5*` 数据库/容器/网络前缀和精确 PG/JDBC 环境变量残留为 0。任何既有残留不得由 R5 猜测归属后删除，应停工交回 Sol。

随后按 §26.3 的精确两键、ignored/untracked、唯一非空规则读取本地凭据，只允许输出 `2/2_PRESENT_REDACTED`。不得对 `.env`、秘密值或其派生物计算/输出 hash、长度、片段、编码、预览；不得输出命令行环境、完整 JDBC/PostgreSQL URL、`PGPASSWORD`、连接串或通用环境变量清单。凭据只在 PostgreSQL 阶段导入当前进程，并必须由最外层 `finally` 清除 §26.4 全部精确变量。

### 29.3 V009 单次安全诊断协议

V009 只允许执行一次，不得因失败自动或人工重试。执行编排必须满足：

1. 使用仓库外、随机、仅本回合的临时文本文件承接 stdout/stderr；不得把原始日志写入仓库、Surefire、任务单、终端、聊天、归档或 Git，也不得计算其 hash；
2. 固定使用 `LC02_V009_PG_ENABLED=true`、`PG_CONTAINER=postgres-server`、`PG_USER=postgres`、当前进程秘密环境变量和唯一前缀 `lc02_v009_r5`，原样调用现有 `lc02_v009_contract.sh`；禁止 `set -x`、`bash -x`、命令回显、管道实时打印或修改脚本；
3. 记录退出码后，在内存中先检查原始日志不包含两项秘密值，也不包含 `PGPASSWORD=`、`jdbc:postgresql:`、`postgresql://`、authorization/token 或 shell trace 的秘密注入形式。任一命中立即标记 `SECRET_OUTPUT_GUARD_FAILED`，不输出命中内容，并进入清理；
4. 安全检查通过后，只允许从日志提取：`PASS` 标签数量、最后一个完整 `PASS` 标签的业务说明、首个 `FAIL` 标签冒号前的业务说明，以及以下单一脱敏类别，不得输出 `expected/actual`、SQL、数据库名、临时路径、payload 或原始 stderr：
   - 工具/容器不存在或未运行：`ENVIRONMENT_UNAVAILABLE`；
   - PostgreSQL 认证拒绝：`POSTGRES_AUTH_FAILED`；
   - 连接/传输失败：`POSTGRES_CONNECTION_FAILED`；
   - 权限拒绝：`POSTGRES_PERMISSION_FAILED`；
   - SQL/psql/pg_dump 执行失败：`POSTGRES_EXECUTION_FAILED`；
   - `[FAIL]` 断言标签存在：`V009_CONTRACT_ASSERTION_FAILED`；
   - 前缀数据库或临时资产清理失败：`CLEANUP_FAILED`；
   - 无法安全归类：`UNCLASSIFIED_FAILURE`。
5. 无论成功、失败或分类异常，最内层 `finally` 都必须删除原始日志文件；不得输出其路径、大小、hash 或内容。最外层 `finally` 再清除凭据与 PG/JDBC 变量，并检查数据库前缀、临时目录与日志文件残留为 0。

V009 只有脚本退出码 0、`PASS_ASSERTIONS=79`、FAIL 标签 0、秘密输出防护通过、前缀数据库/临时备份/原始日志/环境变量残留均为 0，才判 **79/79 PASS**。若退出非零，交付只报告稳定阶段标签、脱敏类别、退出码、已通过断言数和清理结果；立即停止，禁止执行 §29.4、修改脚本/SQL/凭据/容器或用第二次运行补证。

### 29.4 V009 通过后的后继关闭顺序

只有 §29.3 V009 **79/79 PASS** 后才按以下顺序继续：

1. 仍使用仓库外临时日志和相同秘密禁出规则，原样执行 Inbox PostgreSQL **49/49**，前缀改为 `lc02_08_r5`；任一非零、failure/error/skip 或清理失败立即停工；
2. 无条件清除两组源密码、`PG_PASSWORD` 与全部 PG/JDBC 临时变量，并证明精确残留为 0；
3. 原样执行 Java fail-closed **11/11**，Failures=0、Errors=0、Skipped=0；
4. 原样执行真实 EMQX **12/12**，Failures=0、Errors=0、Skipped=0，容器/网络/临时凭据/目录残留为 0；
5. V011/U011 真实数据库/DDL继续 **NOT AUTHORIZED / NOT RUN**。

R5 不重新执行已受保护且可复用的 R4 Java 1/1、16/16、34-reactor、device 39、sink 155 或完整 284；也不得把这种有条件复用解释为永久豁免。若 §29.2 任一保护不成立，必须回 Sol 决定新的全量包。

### 29.5 收尾保护、写入白名单与完成定义

实际写入白名单只有本任务单与 `M1-SDD进度与续作入口.md` 的执行证据；生产、测试、POM、脚本、SQL、配置、`.env`、V011/P02 和其他文档全部只读。不得 commit，除非决策所有者另行明确授权。

收尾必须复算 A/C 与全部既有 LC02 固定摘要，要求 `sharedEnd == sharedStart`、`p02End == p02Start`、R4 测试 SHA 不变；执行两个禁止扫描、scoped `git diff --check`，并证明 `lc02_v009_r5*` / `lc02_08_r5*` 数据库/容器/网络、仓库外临时日志/目录、遮罩、精确 PG/JDBC/EMQX 变量与临时凭据残留全部为 0。范围外 `DEVICE/.claude/` 或其他并行改动只记录开工/收工差异，不得触碰。

只有 §29.2～§29.5 全部通过，Sol 才可接受 `LC02-10-R5`、R4、R3、R2、R1 与 LC02-10，并把 M1-LC-02 转为 Implemented / Verified-Local。该接受仍不批准正式 V009/V011 落库、生产 broker/ACL/TLS 激活、Linux PTY/锁互操作、资源/稳定性压测、Windows 发布资格或现场验证。

以下任一情形立即停工：秘密输出防护命中；无法建立或清除仓库外日志；V009/Inbox 非零或断言数不符；需要第二次 V009；需要修改脚本、SQL、生产或测试；保护摘要/共享/P02 漂移；V011/U011 进入默认链或执行真实 DDL；Java/EMQX failure/error/skip；临时资源或变量不能清零。不得顺手处置。

本节当前状态为 **FROZEN / NOT-YET-AUTHORIZED**。本轮 Sol 只读分析并冻结 R5，没有读取 `.env`、没有运行 Docker/PostgreSQL/Maven/EMQX、没有修改实现/测试/脚本/SQL/配置，也没有 commit。下一步须决策所有者独立授权 GPT-5.6 Luna Max 执行 `LC02-10-R5 §29.2～§29.5`；若 Luna Max 不可用，不得静默替换，须明确报告后再由 owner 决定是否授权 Sol 接管。

### 29.6 Luna Max 执行在 Inbox 计数/退出证据停工（2026-08-26）

决策所有者已明确授权 GPT-5.6 Luna Max 执行 §29.2～§29.5。Luna 完整读取双基线与冻结单后确认：双基线摘要匹配，`32fbc62c7` 为当前 HEAD 祖先；Docker Linux 29.7.2、三张冻结镜像、`postgres-server` 运行状态、A/C、Inbox/Outbox 25、Store 9、ACK/投影 5、EMQX 4、R1/脱敏与 R4 测试摘要全部通过；`sharedStart=C0D28DB92BCEBE67BEAD498B84F9DA9C9A2F1E4E9E3FF468EAE4AB9ECCC50DD0`，`p02Start=1AB2C4FA56BE22037EE735616EF6E0E3A8898355E81E7E8B40AAF14D7E47520E`。五个 `bash -n`、默认 apply/dry-run/uninstall 隔离、COMMENT/env/README 语义与 P02 正数 PASS/真实 DB=0/真实 DDL=0 均通过。

凭据前检只输出 `2/2_PRESENT_REDACTED`。V009 按 §29.3 只运行一次：退出码 0、PASS 标签 **79**、FAIL 标签 0，最后 PASS 标签为 `end has no LC02 V009 database residue`；秘密/连接串禁出扫描通过，仓库外原始日志已删除，`lc02_v009_r5` 数据库、临时目录、日志和精确 PG/JDBC 变量残留均为 0。因此 V009 本轮结论为 **79/79 PASS**，R4 的 `PG-FAILED` 已由 R5 当前证据替换为 `PG-PASSED-IN-R5`；该结果仍只是隔离合同，不批准正式落库。

随后 Inbox 单次编排的八类 Surefire 结果为：

| 测试类 | Tests | Failures | Errors | Skipped |
|---|---:|---:|---:|---:|
| `InboxEnvelopeProductIdentityContractTest` | 4 | 0 | 0 | 0 |
| `CenterTelemetryIngressHandlerTest` | 7 | 0 | 0 | 0 |
| `JdbcTelemetryInboxContractTest` | 13 | 0 | 0 | 0 |
| `JdbcTelemetryInboxProductIdentityContractTest` | 11 | 0 | 0 | 0 |
| `JdbcTelemetryInboxFailureContractTest` | 1 | 0 | 0 | 0 |
| `InboxReceiveResultContractTest` | 5 | 0 | 0 | 0 |
| `TelemetryInboxAutoConfigurationTest` | 5 | 0 | 0 | 0 |
| `TelemetryStoreBatchContractTest` | 4 | 0 | 0 | 0 |
| **合计** | **50** | **0** | **0** | **0** |

实际 50 与 §29.4 继承的冻结 49 不相等，Luna 因此正确 STOP，没有重跑 Inbox，也没有执行 Java fail-closed 11/11 或真实 EMQX 12/12。Sol 只读 history/source 定位确认：第 50 项是 `TelemetryInboxAutoConfigurationTest.mqttPropertiesRejectMissingOrUnsafeBrokerIdentityBeforeNetworking()`，由提交 `148ed68af` 于 2026-08-24 加入，属于 LC02-09 broker identity fail-closed 增量；其余七类合计 45，该类从 4 增为 5，故当前总数精确为 50。当前八类源码及 `lc02_08_inbox_product_contract.sh` 与 R4 提交 `32fbc62c7` 均零 diff；Inbox 脚本当前 SHA-256 为 `F61E223CE7C784946188FA2D9A2D9D5D39C01B8D183098D37B7C4C34AEC8173E`，显式要求八类被发现且 0 failure/error/skip，本身未硬编码 49。由此判定任务单计数是历史基线漂移，不是新增测试或实现漂移。

但本轮 Inbox wrapper 没有保留可安全回报的顶层退出码或编排级脱敏摘要；原始日志已按安全规则删除，不能为补证而重跑。八类 Surefire 50/0/0/0 与独立零残留只能证明测试类结果和清理事实，不能证明脚本的所有前后置门禁最终退出 0。因此 Inbox 编排整体仍为 **EXIT-EVIDENCE-NOT-CAPTURED / NOT ACCEPTED**，不得仅凭 Surefire 报告标记 50/50 PASS。

收尾时 `sharedEnd == sharedStart`、`p02End == p02Start`，全部固定摘要与 R4 测试 SHA 一致，两个禁止扫描 0 命中，scoped `git diff --check` 退出 0；R5 数据库/容器/网络/仓库外日志/遮罩/精确 PG/JDBC/EMQX 变量残留均为 0。V011/U011 真实数据库/DDL仍 NOT AUTHORIZED / NOT RUN。执行期间出现 P02-M2-02C1P-G2-03 证据文档与 `assets/c1p-g2/` 等范围外并行变化，R5 只记录、不触碰或归属；`DEVICE/.claude/` 亦保持未触碰。R5 未修改实现/测试/脚本/SQL/配置，未 commit。

结论：`LC02-10-R5` 转为 **STOPPED / INBOX-COUNT-AND-EXIT-EVIDENCE-DRIFT / NOT ACCEPTED**。下一步应由 Sol 冻结最小 `LC02-10-R6`：只修订当前/后继验收计数口径为八类 **50/50**，保留 §21.8 的历史 49/49 作为当时证据而不改写；在全部保护仍一致时复用本轮 V009 79/79，但必须按安全协议重新执行一次 Inbox 并捕获顶层退出码、八类 50/50 与编排清理结果，成功后才执行 Java fail-closed 11/11、真实 EMQX 12/12 和最终保护/零残留。R6 冻结并获得独立授权前，不得直接运行后继门禁或修改测试/脚本。

## 30. `LC02-10-R6` Inbox 50 项基线修订、退出证据与最终关闭冻结单（2026-08-26）

### 30.1 依据、计数更正与证据复用决策

本节同时依据《EasyAIoT 项目开发宪法》v1.6.0（SHA-256 `76BF30903A5A62C957A75B57E24080AA7132DE6992D56C262EEAFBE7BE553C4D`）、《平台功能计划》v1.5.0（SHA-256 `F0E5734C8FADA6D0E4DAB98F7D12F58940077F9C60AA8DA42AC8EF45C6F69869`）和 §29.6 冻结。R6 是零实现、零脚本修改的计数事实修订与后继关闭包，不改变接口、Schema、Topic、安全、部署或产品范围。

§21.8 的 49/49 是 LC02-08 当时的验收记录，作为历史证据保留，不回写为 50。提交 `148ed68af` 随后在 `TelemetryInboxAutoConfigurationTest` 增加 `mqttPropertiesRejectMissingOrUnsafeBrokerIdentityBeforeNetworking()`，形成当前 LC02-09 broker identity fail-closed 保护；当前八类 Surefire 精确为 `4+7+13+11+1+5+5+4=50`。因此从 R6 起，所有当前和后继 Inbox 关闭门禁以八类 **50/50**、Failures=0、Errors=0、Skipped=0 为唯一计数事实；§24～§29 中的 49/49 仅代表旧冻结口径，不再作为 R6 阻断值。

R5 的 V009 单次执行具备退出码 0、79 个 PASS 标签、FAIL=0、秘密禁出、原始日志删除和零数据库/变量残留，且最终固定/共享/P02保护一致。R6 开工若 §30.3 全部保护仍成立，可复用该 **V009 79/79**，不得再次执行 V009；若任一保护漂移，复用立即失效并停工。R5 Inbox 只复用作“当前八类实际为 50”的事实发现，不复用作完成证据，因为顶层退出码缺失。

### 30.2 Inbox 九文件固定保护清单

R6 新增以下 9 文件固定保护；聚合算法沿用 §24.3：“仓库相对路径正序 + 一个空格 + 大写 SHA-256，以 LF 连接并保留末尾 LF，再对 UTF-8 bytes 做 SHA-256”。固定聚合为 `CD9897727D139F8B3EBFC9D178091BF695FFDACC70279B7C8519EB7736E9336A`：

```text
.scripts/postgresql/td005-migration/tests/lc02_08_inbox_product_contract.sh F61E223CE7C784946188FA2D9A2D9D5D39C01B8D183098D37B7C4C34AEC8173E
DEVICE/iot-sink/iot-sink-biz/src/test/java/com/basiclab/iot/sink/telemetry/inbox/InboxEnvelopeProductIdentityContractTest.java 74A2F30A52F2F5D1EF54D7ECE179BB8CB55DE51DD5D6ACC24183C951EDEBAD89
DEVICE/iot-sink/iot-sink-biz/src/test/java/com/basiclab/iot/sink/telemetry/inbox/InboxReceiveResultContractTest.java AE640AB957DC8C3F1F55FC04FA9EA4D79888BAC38FE18923681129CA3C0B3A75
DEVICE/iot-sink/iot-sink-biz/src/test/java/com/basiclab/iot/sink/telemetry/inbox/jdbc/JdbcTelemetryInboxContractTest.java E2E443812E05D433D52E76577F7772F19BF4279B586DF5379A36F756E1B91C17
DEVICE/iot-sink/iot-sink-biz/src/test/java/com/basiclab/iot/sink/telemetry/inbox/jdbc/JdbcTelemetryInboxFailureContractTest.java AC02BBE5014A77433A37FE28A3A053491604E1D85AD1C2FD80E1847AA100158F
DEVICE/iot-sink/iot-sink-biz/src/test/java/com/basiclab/iot/sink/telemetry/inbox/jdbc/JdbcTelemetryInboxProductIdentityContractTest.java C1B65B2ECD9F73F0A25706786879FE5F2544409A76EE556FB24D77F4EBC10DFF
DEVICE/iot-sink/iot-sink-biz/src/test/java/com/basiclab/iot/sink/telemetry/inbox/route/CenterTelemetryIngressHandlerTest.java 4B38C6B38FFE1294A734CDA000BC31089F1B2ED1A47F63374C939B521403BAAF
DEVICE/iot-sink/iot-sink-biz/src/test/java/com/basiclab/iot/sink/telemetry/inbox/TelemetryInboxAutoConfigurationTest.java 2487B9A5FEDCEE5FA002968083EFF3F71A7276E262A7D91B5592AED5057C5949
DEVICE/iot-sink/iot-sink-biz/src/test/java/com/basiclab/iot/sink/telemetry/store/TelemetryStoreBatchContractTest.java E0C4F8A25A104C4FD1E3697CD7C6F9CCA7A0D4A6E5846027981FC74C80597EEC
```

任一单文件或聚合漂移立即停工；R6 不得接受新摘要、修改测试数量、排除类、脚本或 POM。九文件不替代既有 A/C、Inbox/Outbox 25、Store 9、ACK/投影 5、EMQX 4、R1/脱敏、R4 测试、共享 5 和 P02 3 文件保护，而是追加保护。

### 30.3 开工门禁与 V009 复用资格

获得独立执行授权后，必须重新：

1. 读取双基线、确认 `32fbc62c7` 仍为 HEAD 祖先；记录工作树开工差异，只允许两份进度文档和已识别范围外并行工作，不得触碰 `DEVICE/.claude/`；
2. 复核 Docker Linux 29.7.2、三张冻结镜像、`postgres-server` 运行、全部既有固定摘要、§30.2 九文件聚合、`sharedStart`、`p02Start`；
3. 执行五个 `bash -n`、默认 apply/dry-run/uninstall 隔离与 P02 静态正数/真实 DB=0/真实 DDL=0；
4. 检查 `lc02_v009_r5*`、`lc02_08_r6*` 数据库/容器/网络/临时日志/遮罩和精确 PG/JDBC/EMQX 环境变量残留为 0；
5. 复核 §29.6 V009 的退出码、79 PASS、FAIL=0、秘密扫描、日志删除和零残留证据均已写入当前任务单，且 A/C/共享/P02/R4/V009资产自 R5 收工没有漂移。

全部成立才报告 `R5_V009_79_REUSE=ELIGIBLE` 并禁止重跑 V009。任一不成立立即停止并由 Sol 决定新的全量包，不得退回 R5 凭据或重新执行 V009补证。

### 30.4 Inbox 单次重验与顶层退出码硬门禁

按 §26.3 精确读取本地两键，只输出 `2/2_PRESENT_REDACTED`，秘密规则完全沿用 §29.2。Inbox 只允许执行一次，固定前缀 `lc02_08_r6`，输出写入仓库外随机临时日志；禁止实时打印、`set -x` / `bash -x`、修改脚本或第二次运行。

wrapper 必须在调用 `lc02_08_inbox_product_contract.sh` 的同一 PowerShell 进程和同一 `try` 作用域中，于任何日志读取/删除前立即执行 `$inboxExit = $LASTEXITCODE`。`$inboxExit` 初始必须为 `$null`；若执行后仍为 `$null`，稳定失败为 `INBOX_TOP_LEVEL_EXIT_NOT_CAPTURED` 并立即清理停工。不得依赖会话句柄、后续 shell、Surefire XML或进程存在性反推顶层退出码。

捕获退出码后按 §29.3 的秘密/连接串禁出规则扫描原始日志；命中只报告 `SECRET_OUTPUT_GUARD_FAILED`，不得输出内容。安全后必须同时证明：

1. `$inboxExit -eq 0`；
2. 日志中 `[lc02-08][FAIL]` 为 0，`[lc02-08][PASS]` 精确为 9，包含 V009 hash、开工前缀清洁、双夹具、八类直接合同、reactor test-compile、产品身份扫描、scoped diff、显式清理和 EXIT trap 最终残留九个稳定标签；
3. 八类 Surefire 分项精确为 §29.6 表格的 4/7/13/11/1/5/5/4，总计 **50**，Failures=0、Errors=0、Skipped=0；不得只读取旧报告，报告时间必须属于本次运行；
4. `lc02_08_r6*` 数据库、Maven/临时目录、仓库外日志和精确 PG/JDBC变量残留均为 0。

无论结果如何，内层 `finally` 删除原始日志且不输出路径/大小/hash/内容，外层 `finally` 清除全部秘密和 PG/JDBC变量。上述任一不满足立即停工，不得重跑、修改脚本/测试或进入 §30.5。

### 30.5 后继 Java、EMQX 与最终完成定义

只有 §30.4 全部通过后，确认全部 PG/秘密变量为 0，再原样执行：

1. Java fail-closed `TelemetryUpstreamTopicParserContractTest` 6/6 + `TelemetryInboxAutoConfigurationTest` 5/5，合计 **11/11**，Failures=0、Errors=0、Skipped=0；
2. 真实隔离 EMQX **12/12**，Failures=0、Errors=0、Skipped=0，固定镜像/能力画像/随机临时凭据与全部清理合同保持 §23；
3. 最终复算 §30.2 九文件、A/C、Inbox/Outbox 25、Store 9、ACK/投影 5、EMQX 4、R1/脱敏、R4测试，要求 `sharedEnd == sharedStart`、`p02End == p02Start`；执行禁止扫描、scoped `git diff --check`，确认数据库/容器/网络/日志/目录/遮罩/精确环境变量/临时凭据残留全为 0。

实际写入白名单只有本任务单和 `M1-SDD进度与续作入口.md` 的执行证据；不得修改或提交其他文件，不得 commit，除非决策所有者另行授权。V011/U011 真实数据库/DDL继续 NOT AUTHORIZED / NOT RUN。

只有 §30.3～§30.5 全部通过，Sol 才可接受 R6/R5/R4/R3/R2/R1/LC02-10，并把 M1-LC-02 转为 Implemented / Verified-Local。该接受不批准正式 V009/V011 落库、生产 broker/ACL/TLS 激活、Linux PTY/锁互操作、资源/稳定性压测、Windows 发布资格或现场验证。

本节当前状态为 **FROZEN / NOT-YET-AUTHORIZED**。本轮 Sol 只重读双基线、审查 R5 证据、建立九文件摘要并冻结 R6；没有读取 `.env`、没有运行 Docker/PostgreSQL/Maven/EMQX、没有修改实现/测试/脚本/SQL/配置，也没有 commit。下一步须决策所有者独立授权 GPT-5.6 Luna Max 执行 `LC02-10-R6 §30.3～§30.5`；若 Luna Max 不可用，不得静默替换，须明确报告后再由 owner 决定是否授权 Sol 接管。

### 30.6 Luna Max 执行与 Sol 最终接受（2026-08-26）

决策所有者两次明确授权 GPT-5.6 Luna Max 执行 §30.3～§30.5。首个回合在运行任何 R6 门禁前因 Luna 用量限制失败，Sol 明确报告限制，没有静默改用其他模型；第二次同模型 max reasoning 授权重试完成全部验收。

§30.3 结果：双基线摘要、`32fbc62c7` 祖先关系、Docker Linux 29.7.2、三张冻结镜像与 `postgres-server` 运行状态通过；§30.2 九文件聚合 `CD9897727D139F8B3EBFC9D178091BF695FFDACC70279B7C8519EB7736E9336A` 及 9 个单文件全部一致；A/C、Inbox/Outbox 25、Store 9、ACK/投影 5、EMQX 4、R1/脱敏、R4测试保护一致；五个 `bash -n` 退出 0，默认 apply/dry-run/uninstall 与 P02 静态隔离通过，P02 真实 DB/DDL 为 0。`sharedStart == sharedEnd == C0D28DB92BCEBE67BEAD498B84F9DA9C9A2F1E4E9E3FF468EAE4AB9ECCC50DD0`，`p02Start == p02End == 1AB2C4FA56BE22037EE735616EF6E0E3A8898355E81E7E8B40AAF14D7E47520E`。R5 V009 的 79 PASS、退出 0、秘密扫描、日志删除和零残留证据完整且保护无漂移，因此 `R5_V009_79_REUSE=ELIGIBLE`；R6 没有重跑 V009。

§30.4 Inbox 只运行一次：凭据前检只报告 `2/2_PRESENT_REDACTED`；wrapper 在同一 PowerShell 进程/`try` 作用域内、日志读取或删除前捕获顶层退出码 **0**；秘密/连接串禁出扫描通过；脚本稳定标签 **9 PASS / 0 FAIL**。八类本轮 Surefire 精确为 4/7/13/11/1/5/5/4，合计 **50**，Failures=0、Errors=0、Skipped=0。初次只读汇总器以宽泛通配符误匹配旧 `TDengineTelemetryStoreBatchContractTest` 报告并报告缺项；执行者没有重跑 Inbox，而是按冻结精确报告文件名确认 `TelemetryStoreBatchContractTest` 为 4/0/0/0。Sol 独立读取本轮报告及时间戳，确认八类结果均属于本次运行。`lc02_08_r6*` 数据库、临时目录、仓库外日志、秘密与 PG/JDBC变量残留均为 0。

§30.5 后继结果：Java fail-closed 中 `TelemetryUpstreamTopicParserContractTest` **6/6**、`TelemetryInboxAutoConfigurationTest` **5/5**，合计 **11/11**；真实隔离 `EmqxTelemetryAclIntegrationTest` **12/12**。两阶段退出码均为 0，Failures=0、Errors=0、Skipped=0；Sol 独立复核的本轮 Surefire 时间戳分别为 2026-08-26 14:58 与 15:00。EMQX 临时凭据防护通过，容器、网络、目录、日志和凭据残留 0；两个禁止扫描 0 命中，scoped `git diff --check` 退出 0；最终数据库、容器、网络、日志、遮罩和精确环境变量残留均为 0。V011/U011 真实数据库/DDL保持 NOT AUTHORIZED / NOT RUN。

本轮未修改实现、测试、POM、脚本、SQL、配置或 `.env`，未 commit。执行期间 P02/WEB/证据文档与 `DEVICE/.claude/` 存在范围外并行变化，R6 只记录、未读取、清理、暂存、提交或归属。Sol 最终结论：`LC02-10-R6`、R5、R4、R3、R2、R1、LC02-10 与 M1-LC-02 全部 **COMPLETE / SOL-ACCEPTED（Implemented / Verified-Local）**。正式 V009/V011 落库、生产 broker/ACL/TLS 激活、`LC02-09-RUNTIME-01`、Linux PTY/锁互操作、资源/稳定性压测、Windows 发布资格和现场验证继续 OPEN，不能由本地接受替代。
