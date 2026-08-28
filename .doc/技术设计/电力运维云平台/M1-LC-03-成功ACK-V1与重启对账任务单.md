# M1-LC-03：成功 ACK V1 与重启对账任务单

> 状态：In Progress（LC03-01～03 COMPLETE / SOL-ACCEPTED；LC03-04A IMPLEMENTED / CONTAINER-5of6-ENV-BLOCKED（§19，待决策所有者选定路径）；LC03-04B NOT-AUTHORIZED）
> 版本：1.1.2
> 日期：2026-08-28
> 架构负责人：GPT-5.6 Sol
> 计划实现执行者：GPT-5.6 Luna（max reasoning；须逐包独立授权）
> 双基线：EasyAIoT 项目开发宪法 1.6.0 / 平台功能计划 1.5.0

## 1. 任务结论

M1-LC-02 已完成 canonical `/iot/{product}/{device}/property/upstream/report`、产品路由身份持久化、center 权威注册校验和精确路由集合前置合同。本任务只关闭可靠遥测链路中的成功确认闭环：

```text
PostgreSQL Inbox 单消息事务提交
  → 仅 ACCEPTED_DURABLE / DUPLICATE 生成 ACK V1
  → /iot/{product}/{device}/property/downstream/report/ack（QoS 1）
  → collector 同时校验 schema/messageId/requestId/Topic route/status-code-reason
  → SQLite 单 writer 将 PENDING/IN_FLIGHT 幂等转为 ACKED
  → center 重启扫描未确认发送记录并安全补发
```

本任务不把 MQTT PUBACK 解释为业务成功。`persistedAt` 只表示中心 Inbox 首次可靠提交时间，不表示时序投影、告警、影子、聚合或查询水位已经完成。

## 2. 强制依据与代码事实

### 2.1 设计依据

- [EasyAIoT 项目开发宪法 1.6.0](../../开发规范/EasyAIoT项目开发宪法.md)；
- [平台功能计划 1.5.0](../../架构设计/平台功能计划.md)；
- [ADR-003 遥测 ACK 机制](../../架构决策/电力运维云平台/ADR-003-遥测ACK机制.md)（Accepted）；
- [ADR-017 遥测可靠链路 Topic 与产品路由身份收口 1.1.1](../../架构决策/电力运维云平台/ADR-017-遥测可靠链路Topic与产品路由身份收口.md)（Accepted）；
- [TD-002 SQLite Outbox 与恢复迁移](./TD-002-SQLite-Outbox与恢复迁移.md)（In Review）；
- [TD-003 遥测 Inbox、ACK 与时序投影 1.0.3](./TD-003-遥测Inbox-ACK与时序投影.md)（In Review）；
- [M1-LC-01 Inbox 接收结果合同](./M1-LC-01-Inbox接收结果合同任务单.md)（Implemented / Verified-Local）；
- [M1-LC-02 Topic 与产品路由身份收口](./M1-LC-02-遥测Topic与产品路由身份收口任务单.md)（Implemented / Verified-Local）。

### 2.2 2026-08-26 代码差距

| 事项 | 当前事实 | LC03 冻结结果 |
|---|---|---|
| ACK payload | `messageId/resultCode/errorCode/observedAt` | 改为 §3 的七字段 ACK V1 |
| ACK Topic | center 仍拼 `/telemetry/ack/**` | 只使用 `TelemetryRoute.ackTopic()` |
| collector 订阅 | `ackTopicPrefix + #` | 只订阅 §4 精确 Topic 集合 |
| 关联校验 | writer 只按 messageId 查行 | 同时校验 requestId 与产品/设备 Topic route |
| center 发送状态 | Inbox 无 `ack_sent_at/ack_attempts` | 增加 V012 候选列、索引与 repository |
| 重启恢复 | 无成功 ACK 扫描器 | 启动立即扫描，之后每 10 秒扫描，最多 1000 条 |
| center 接线 | ingress 只返回 Inbox 结果 | 事务返回后由成功 ACK service 从持久行生成 ACK |

现有 `InboxReceiveResult.Status.MESSAGE_ID_COLLISION` 不在本任务中生成 ACK。碰撞、鉴权失败、格式失败、权威注册失败及数据库不可用的拒绝事实与 FINAL/RETRYABLE ACK 仍由 M1-LC-04 负责。

## 3. ACK V1 线上合同

### 3.1 唯一 payload

```json
{
  "schemaVersion": "1.0",
  "messageId": "2ca80f25-4b6c-443f-a114-1b3df0a8cdf9",
  "requestId": "a9afddc7-02ee-4df3-905b-ec3e4107f25d",
  "status": "ACCEPTED_DURABLE",
  "code": 0,
  "reasonCode": "OK",
  "persistedAt": "2026-07-30T03:00:00.123Z"
}
```

字段顺序不作为消费者正确性的条件；生产 publisher 按上例固定顺序输出 UTF-8 JSON。禁止输出 payload/value、tenant/site/property、数据库主键、节点地址、异常文本或凭据。

### 3.2 成功组合

| Inbox 结果 | status | code | reasonCode | persistedAt |
|---|---|---:|---|---|
| `ACCEPTED_DURABLE` | `ACCEPTED_DURABLE` | 0 | `OK` | 首次插入的 `received_at_ms` |
| `DUPLICATE` | `DUPLICATE` | 1001 | `DUPLICATE` | 既有行首次 `received_at_ms`，禁止改为重放时间 |

约束：

- `schemaVersion` 必须逐字节为 `1.0`；
- `messageId` 精确回显 Inbox 的 `message_id_wire`，32/36 位兼容形式不得重写；
- `requestId` 必须来自同一持久 Inbox 行；
- `persistedAt` 由持久毫秒值格式化为 UTC RFC 3339，固定 `Z`，毫秒精度，不使用本机时区；
- `status/code/reasonCode` 必须是上表合法三元组；错配、缺字段、null、错误 JSON 类型、重复 JSON key 或未知主版本均不得调用 `applyAck`；
- 1.x 未知可选字段由消费者忽略，但不得覆盖或替代七个必填字段；
- 本任务 publisher 不生成 `REJECTED_RETRYABLE`、`REJECTED_FINAL` 或 collision ACK；不得用 success code 包装失败。

### 3.3 共享类型边界

在 `iot-sink-api` 建立不可变 `TelemetryAckV1` 与 `TelemetryAckStatus`。`AckCommand` 从旧四字段改为至少携带：

```java
record AckCommand(
    String schemaVersion,
    String messageId,
    String requestId,
    TelemetryRoute route,
    TelemetryAckStatus status,
    int code,
    String reasonCode,
    long persistedAtMs,
    long observedAtMs
) {}
```

`observedAtMs` 仅是 collector 本地接收时间，不进入 ACK V1 wire payload。旧 `resultCode/errorCode/observedAt` JSON 不再作为可靠 ACK V1 接受；不得保留静默双协议解析或 `/telemetry/**` 回退。

## 4. collector 精确订阅与 ACK 应用

### 4.1 精确集合

唯一订阅集合继续复用 LC02 已实现的 `TelemetryRouteSetProvider.currentRoutes()`：

```text
ConfigSnapshot 1.1 当前已应用的产品/设备路由
UNION
SQLite Outbox 中 PENDING/IN_FLIGHT 的产品/设备路由
```

每个元素只映射到 `TelemetryRoute.ackTopic()`。生产配置不得再暴露或使用 `ackTopicPrefix`；禁止 `#`、`+`、`$queue`、共享订阅和普通 broad filter。

### 4.2 刷新顺序

每次刷新固定执行：

1. 读取一次不可变 route snapshot；
2. 计算 `additions = desired - active` 与 `removals = active - desired`；
3. 按 `TelemetryRoute` 排序逐个/批量订阅 additions，全部得到成功 SUBACK；
4. 仅在 additions 全成功后原子替换 active set；
5. 再取消 removals；取消失败只延迟清理，不回滚已成功的新订阅；
6. 任一新增订阅失败时 active set 不变、dispatcher 不发送新路由消息，并报告稳定码 `ACK_SUBSCRIPTION_NOT_READY`。

MQTT 初次连接后必须先完成初始集合 SUBACK，再允许 Outbox dispatcher claim/publish。collector 重启时先启动 SQLite writer，再恢复精确 ACK 订阅，最后启动 dispatcher 和 Poller。配置 APPLIED 后必须在新图开始轮询前刷新订阅；旧路由存在 PENDING/IN_FLIGHT 时不得取消。

### 4.3 入站关联

collector 收到 ACK 后按以下顺序 fail-closed：

1. Topic 必须精确解析为 canonical ACK route；
2. payload 必须通过 ACK V1 结构与三元组校验；
3. SQLite 单 writer 按 messageId 查既有行；
4. requestId、productIdentification、deviceIdentification 必须全部相等；
5. 只有 `ACCEPTED_DURABLE` 或 `DUPLICATE` 才把 `PENDING/IN_FLIGHT` 转为 `ACKED`；
6. `ACKED` 重复 ACK 幂等，`DEAD_LETTER` 不自动复活，未知 messageId 不建行。

关联失败不得改变 outbox 状态、attempts、gap 或 route set。日志只记录稳定分类和低敏上下文，不输出完整 ACK/payload/凭据。

## 5. center 持久发送与重启对账

### 5.1 V012 候选

V011 已被 TD-006 告警候选保留，LC03 不得复用。中心 ACK 状态使用独立后继编号 `V012`：

```sql
ALTER TABLE iot_sink.telemetry_inbox
    ADD COLUMN ack_sent_at_ms BIGINT,
    ADD COLUMN ack_attempts INTEGER NOT NULL DEFAULT 0;

CREATE INDEX idx_inbox_ack_pending
    ON iot_sink.telemetry_inbox(received_at_ms, id)
    WHERE ack_sent_at_ms IS NULL;
```

正式资产必须包含全部中文列/索引注释、V008+V009 前置签名、同 ID/hash 幂等、异 hash 阻断、预存在对象拒绝、备份/画像/回滚说明。`ack_attempts >= 0` 必须有数据库 CHECK。

V012/U012 在本任务中只允许作为候选资产和隔离临时 PostgreSQL 合同执行，不接入默认 `APPLY_STEPS`，不修改 V011/U011，不执行现有/共享/生产数据库 DDL，不更新首装 dump。正式 runner 与首装基线接线另立 `LC03-DB-RUNTIME-01`，须独立部署授权。

### 5.2 持久 dispatch 行

center 发送 ACK 只能从 PostgreSQL 已持久行加载：

```text
tenant_id + message_id/internal key
message_id_wire + request_id
product_identification + device_identification
received_at_ms + ack_sent_at_ms + ack_attempts
```

`product_identification IS NULL`、requestId 缺失、route 非法或状态不是已接收成功事实时 fail-closed，不允许从请求 payload、site/property、当前 DeviceDO 或展示名称补猜。

### 5.3 即时发送

- `JdbcTelemetryInbox.receiveEnvelopes` 每个 item 的事务已提交后才返回；ACK service 只消费已返回的 `Batch`；
- 对 `ACCEPTED_DURABLE/DUPLICATE`，repository 在独立短事务内加载持久行并把 `ack_attempts + 1`，提交后才调用 MQTT；
- 对 `MESSAGE_ID_COLLISION` 不调用 publisher；
- MQTT QoS 1 publish Future 成功后才以条件更新写 `ack_sent_at_ms`；同步异常、Future 失败或进程中止均保持 NULL，交扫描器补发；
- ACK publish 成功但标记前崩溃会产生重复 ACK，collector 必须幂等吸收；禁止用跨 PostgreSQL/MQTT 事务伪造 exactly-once。

### 5.4 重启扫描

- center 启动顺序：Inbox repository 可用 → MQTT 连接并完成上行共享订阅 → ACK scanner 立即运行一次 → projector；
- 之后每 10 秒扫描一次 `ack_sent_at_ms IS NULL`，按 `(received_at_ms,id)` 升序，每批最多 1000；
- 扫描只选择已持久 Inbox 成功行，且产品/设备路由完整；
- 每次发送前增加 `ack_attempts`；失败不写 sent time，下轮可再次领取；
- 多实例允许产生重复 ACK，但不得漏发、错路由或把 collision/拒绝记录混入；`FOR UPDATE SKIP LOCKED` 只用于短事务领取，禁止跨 MQTT publish 持锁；
- collector 重发产生 `DUPLICATE` 时，即使该行已有 `ack_sent_at_ms`，即时路径仍再次发送 DUPLICATE，覆盖“broker 收到 ACK 但 collector 未消费”的窗口。

## 6. 子任务冻结顺序

| ID | 边界 | 主要产物 | 状态 |
|---|---|---|---|
| LC03-00 | Sol 合同与任务拆分 | 本任务单、续作入口、索引 | COMPLETE / SOL-FROZEN |
| LC03-01 | 共享 ACK V1 类型、codec、Topic parser 与旧 wire 拒绝 | 七字段 DTO、严格 codec、直接合同测试 | COMPLETE / SOL-ACCEPTED（2026-08-27） |
| LC03-02 | collector 精确订阅、启动/APPLIED 门禁与成功 ACK 关联应用 | subscription coordinator、runtime ordering、SQLite 状态合同 | COMPLETE / SOL-ACCEPTED（2026-08-27） |
| LC03-03 | V012 候选、center dispatch repository、即时 ACK 与 10 秒扫描 | SQL 候选、JDBC repository、publisher/service/scanner | COMPLETE / SOL-ACCEPTED（2026-08-27，§15.6 含两处偏离修复）；临时 PG 合同脚本已备未执行 |
| LC03-04 | collector↔EMQX↔center↔PG↔SQLite 组合 E2E 与重启故障点 | 04A 确定性 fixture/假服务组合测试；04B 真实隔离 broker/PG | 04A IMPLEMENTED：本地 6/0/0/0、容器稳定 5/6（e2e01 容器专属超时，§19 待决策路径）；04B NOT-YET-AUTHORIZED |
| LC03-05 | 全模块回归、保护扫描、文档收口 | Verified-Local 证据 | LOCKED，待 LC03-04 验收 |

LC03-02 与 LC03-03 在 LC03-01 被 Sol 接受后可由 Sol 判断是否并行；LC03-04 必须等待两者均完成。每个包都须由决策所有者独立授权 GPT-5.6 Luna（max reasoning），实现者不得自行进入下一包或 commit。

## 7. 分包文件白名单

### 7.1 LC03-01

- `DEVICE/iot-sink/iot-sink-api/src/main/java/com/basiclab/iot/sink/telemetry/ack/`（仅本包新增）；
- `DEVICE/iot-sink/iot-sink-api/src/main/java/com/basiclab/iot/sink/telemetry/outbox/AckCommand.java`；
- `DEVICE/iot-sink/iot-sink-api/src/main/java/com/basiclab/iot/sink/telemetry/outbox/AckResultCode.java`（仅兼容迁移；不得扩大状态）；
- `DEVICE/iot-sink/iot-sink-biz/src/main/java/com/basiclab/iot/sink/telemetry/ack/`（codec/parser）；
- 对应 `iot-sink-biz/src/test/java/.../telemetry/ack/` 测试与 fixture。

### 7.2 LC03-02

- `.../outbox/dispatch/CollectorMqttAckSubscriber.java`；
- `.../outbox/dispatch/CollectorMqttProperties.java`；
- `.../outbox/dispatch/VertxCollectorMqttPublisher.java`；
- `.../outbox/dispatch/CollectorAckSubscriptionCoordinator.java`（新增）；
- `.../outbox/sqlite/SqliteOutboxAutoConfiguration.java`；
- `.../outbox/sqlite/SqliteTelemetryOutbox.java`、`SqliteOutboxWriter.java`、`OutboxCommand.java`、`OutboxCommandQueue.java`；
- `.../protocol/polling/CollectorPollingRuntime.java` 与 `.../config/IotGatewayConfiguration.java`（只允许订阅就绪/配置 APPLIED 排序接线）；
- 上述类对应的直接测试；不得修改 SQLite Schema/user_version。

### 7.3 LC03-03

- `iot-sink-api/.../telemetry/inbox/ack/`（dispatch port/row 类型，新增）；
- `iot-sink-biz/.../telemetry/inbox/ack/` 与 `.../telemetry/inbox/jdbc/JdbcTelemetryAckDeliveryRepository.java`（新增）；
- `.../telemetry/inbox/mqtt/CenterMqttAckPublisher.java`；
- `.../telemetry/inbox/mqtt/CenterMqttInboxSubscriber.java`；
- `.../telemetry/inbox/TelemetryInboxAutoConfiguration.java`；
- `.doc/技术设计/电力运维云平台/assets/td005-migration/V012__telemetry_inbox_ack_delivery.sql`（新增候选）；
- `.doc/技术设计/电力运维云平台/assets/td005-migration/U012__telemetry_inbox_ack_delivery.sql`（新增候选）；
- `.scripts/postgresql/td005-migration/tests/lc03_v012_contract.sh`（新增隔离合同，不接共享 runner）；
- 对应直接/真实 JDBC 测试。

### 7.4 LC03-04～05

LC03-04A 只允许新增以下文件，不允许修改任何生产 Java、现有测试、POM、SQL 或配置：

- `DEVICE/iot-sink/iot-sink-biz/src/test/java/com/basiclab/iot/sink/telemetry/inbox/ack/Lc03AckE2eFixture.java`；
- `DEVICE/iot-sink/iot-sink-biz/src/test/java/com/basiclab/iot/sink/telemetry/inbox/ack/TelemetryAckRestartReconciliationTest.java`；
- `DEVICE/iot-sink/iot-sink-biz/src/test/java/com/basiclab/iot/sink/telemetry/inbox/ack/TelemetryAckCombinedE2ETest.java`；
- `DEVICE/iot-sink/iot-sink-biz/src/test/resources/lc03-04/accepted-envelope-v1.json`；
- `DEVICE/iot-sink/iot-sink-biz/src/test/resources/lc03-04/collision-envelope-v1.json`；
- `DEVICE/iot-sink/iot-sink-biz/src/test/resources/lc03-04/fixture-manifest.json`。

LC03-04B 只有在决策所有者另行授权真实隔离运行后，才允许新增：

- `DEVICE/iot-sink/iot-sink-biz/src/test/java/com/basiclab/iot/sink/telemetry/inbox/ack/TelemetryAckRealBrokerPostgresE2ETest.java`；
- `.scripts/mqtt/tests/lc03_success_ack_contract.sh`。

LC03-04/05 共同允许回填本任务单、[技术设计索引](./README.md) 与 [M1 续作入口](./M1-SDD进度与续作入口.md)。现有 LC03-01～03 测试只能回归，禁止为追求计数而修改、排除或重命名。

任何需修改 `TelemetryEnvelope` canonical/hash、V008/V009/V010/V011、TD-006 资产、EMQX 生产 ACL、Docker Compose、NODE、iot-device、TelemetryStore/projector、拒绝审计或告警代码的情况均立即停止并交回 Sol。

## 8. 必测矩阵

### 8.1 ACK codec

- 两个成功三元组逐字段 round-trip；32/36 位 wire messageId 原样回显；UTC 毫秒边界；
- 缺失/null/空白/错类型/重复 key/未知主版本/非法 code-reason-status 组合全部拒绝；
- 未知 1.x 可选字段不改变七字段解释；旧 `resultCode/errorCode/observedAt` wire 不可达；
- payload、异常、凭据和数据库键不进入 ACK JSON 或日志。

### 8.2 collector

- 启动恢复时 applied routes 与 PENDING/IN_FLIGHT routes 并集精确订阅；空集合不 broad subscribe；
- 新增 SUBACK 全成功后才 swap；部分失败保持旧集合且 dispatcher 不发送新路由；
- 解绑但仍有未终态消息的 route 保留，ACKED 后才可取消；取消失败不丢新订阅；
- Topic 产品/设备、requestId、messageId 任一错配不改 SQLite；
- ACCEPTED/DUPLICATE 对 PENDING、IN_FLIGHT、ACKED 的幂等矩阵；DEAD_LETTER 不复活；
- config APPLIED 到首个新路由 publish 之间已完成对应 ACK SUBACK。

### 8.3 center/JDBC

- V012 首次、同 hash 重放、异 hash、预存在列、中文 COMMENT、非负 CHECK、U012 非空拒绝；
- INSERTED/DUPLICATE 从持久行生成正确 Topic、requestId、wire messageId、原 persistedAt；
- collision、缺产品身份、缺 requestId、事务失败均零 publish；
- publish 失败保持 `ack_sent_at_ms=NULL` 且 attempts 增加；成功后 sent time 设置；
- commit 后/publish 前、publish 后/mark 前、mark 后、进程重启四个 kill 点都不漏 ACK；
- 扫描顺序、1000 上限、10 秒周期、多实例重复安全和零跨路由。

### 8.4 组合 E2E

- 真实隔离 PostgreSQL + EMQX + SQLite，collector 发布一条后中心 INSERTED ACK，SQLite 最终 ACKED；
- ACK 丢失后 collector 重发，中心 DUPLICATE ACK，SQLite 最终 ACKED 且 Inbox/Store 不增加第二逻辑行；
- center 在 Inbox commit 后、ACK publish 前重启，scanner 补发；
- center 在 publish 后、mark 前重启，允许重复 ACK，collector 幂等；
- collector 在 publish 后重启，从未终态 route 恢复精确订阅并接收补发 ACK；
- collision 与全部入口拒绝在 LC03 中零 ACK；
- 全部测试 Failures=0、Errors=0、Skipped=0，临时 DB/container/network/file/env residue=0。

## 9. 历史分包验收命令

以下命令保留为 LC03-01～03 历史分包口径；LC03-04 的最终命令、类名、计数和运行隔离以 §16.7～§16.8 为唯一事实。实现者不得自行缩减、增加排除项或改变计数口径。

```bash
cd DEVICE

mvn -pl iot-sink/iot-sink-biz -am \
  -Dmaven.test.skip=false \
  -Dtest=TelemetryAckV1ContractTest,TelemetryAckTopicContractTest,AckCommandContractTest \
  test

mvn -pl iot-sink/iot-sink-biz -am \
  -Dmaven.test.skip=false \
  -Dtest=CollectorMqttAckSubscriberV1Test,CollectorAckSubscriptionCoordinatorTest,OutboxStateMachineTest,CollectorPollingRuntimeTest \
  test

mvn -pl iot-sink/iot-sink-biz -am \
  -Dmaven.test.skip=false \
  -Dtest=JdbcTelemetryAckDeliveryRepositoryTest,CenterTelemetryAckServiceTest,TelemetryAckReconciliationTaskTest \
  test

mvn -pl iot-sink/iot-sink-biz -am \
  -Dmaven.test.skip=false \
  -Dtest=TelemetryAckRestartReconciliationTest,TelemetryAckCombinedE2ETest \
  test

mvn -pl iot-sink/iot-sink-biz -am -Dmaven.test.skip=false test-compile
```

真实 PostgreSQL/EMQX 命令必须使用 Linux Docker 隔离网络、仓库外临时日志和空 `DEVICE/.env` mask；凭据只从进程环境注入，不得输出值、长度、hash、片段或 URL。任何阶段失败立即停止，不重跑、不修改测试来追计数。

## 10. 保护边界与停工条件

必须在每包开工/收工记录以下保护结果：

- `TelemetryEnvelope`、canonical codec、SQLite schema/user_version、V008/V009/V010、LC02 Inbox/Outbox/Store/ACK投影保护组无漂移；
- `/telemetry/**` 在新增生产代码、配置和脚本中零命中；
- 生产 ACK 订阅中的 `#`、`+`、`$queue`、`$share` 零命中；
- `telemetry_ingress_rejection`、collision FINAL ACK、拒绝审计写入零新增；
- V011/U011 与 P02 并行资产开工/收工相等，且 LC03 不执行其真实 DDL；
- `git diff --check` 通过，秘密扫描与资源残留为零。

出现以下任一情况立即停工：

1. 需要改变 ACK V1 七字段、成功三元组或 `persistedAt` 语义；
2. 需要从非持久事实推断 ACK route/requestId；
3. 需要为 ACK 引入 `/telemetry/**`、wildcard collector subscription 或 clean session；
4. 需要发送 collision/FINAL/RETRYABLE ACK 或写拒绝审计；
5. 需要修改 V011、共享默认 migration runner、首装 dump 或生产 ACL/Compose；
6. 真实 PostgreSQL/EMQX 测试出现 skip、秘密泄露或非零残留；
7. 白名单外文件才能编译或通过测试；
8. 双基线、ADR-003、ADR-017、TD-002、TD-003 之间发现不可解冲突。

## 11. 完成定义

只有以下条件全部满足，M1-LC-03 才能标记 `Implemented / Verified-Local`：

- [ ] LC03-01～05 均经 Sol 独立复核为 COMPLETE / SOL-ACCEPTED；
- [ ] 七字段 ACK V1 与 exact `/iot/**/ack` Topic 无旧 wire/旧 Topic 回退；
- [ ] collector 精确订阅先 SUBACK 后发送，重启/解绑/在途场景无漏 ACK；
- [ ] center 只从持久 Inbox 行发送 INSERTED/DUPLICATE 成功 ACK；
- [ ] V012 候选与真实隔离 PostgreSQL 合同通过，未执行生产 DDL；
- [ ] 即时发送和 10 秒 scanner 覆盖全部 commit/publish/mark 重启窗口；
- [ ] SQLite 只在完整关联匹配的成功 ACK 后转 ACKED；
- [ ] 真实隔离 PostgreSQL/EMQX/SQLite 组合 E2E 全绿且零 skip/残留；
- [ ] LC04 拒绝审计/FINAL/RETRYABLE ACK、正式迁移、生产 broker/TLS、压测和现场资格未被提前宣称完成；
- [ ] 文档证据、保护摘要、命令退出码和测试四元组完整回填。

## 12. 当前授权与下一步

LC03-01、LC03-02、LC03-03 均已完成并经 GPT-5.6 Sol 独立复核为 `COMPLETE / SOL-ACCEPTED`。本次授权仅允许 GPT-5.6 Sol 修正进度漂移并冻结 LC03-04，不构成任何 Java/fixture/脚本实现授权，不构成 V012/U012 真实执行、正式落库或生产环境变更授权。

下一步如要进入实现，须由决策所有者独立授权：

> GPT-5.6 Luna Max 执行 M1-LC-03 `LC03-04A` 确定性组合 E2E、fixture 与假服务故障点测试；不得执行 `LC03-04B` 真实隔离 PostgreSQL/EMQX。

LC03-04B 必须在 04A 实现并经 Sol 复核后再次独立授权；LC03-05 保持 LOCKED。正式 V009/V012 落库、生产 MQTT broker/ACL/TLS 激活、资源压测、Linux PTY/锁互操作、Windows 发布资格、7 天稳定性和现场验证均继续 OPEN / NOT APPROVED。

## 13. LC03-01 验收记录（2026-08-27）

### 13.1 实现结果

- `iot-sink-api` 新增不可变 `TelemetryAckV1`、`TelemetryAckStatus`，并将共享 `AckCommand` 扩展为九字段进程内命令；
- `iot-sink-biz` 新增严格 UTF-8/JSON codec 与 canonical ACK Topic parser；codec 固定 ACK V1 七字段语义，拒绝重复 key、错误类型、未知版本、非法身份、非法成功三元组及旧四字段 wire；
- Topic parser 从 `IotDeviceTopicEnum.PROPERTY_DOWNSTREAM_REPORT_ACK` 取得模板，并以 `TelemetryRoute.ackTopic()` 做 canonical 回环校验，不引入旧 Topic 或 wildcard/shared filter；
- 为保持 LC03-02 迁移前现有 SQLite 调用方可编译，`AckCommand` 暂留 deprecated 四参数进程内兼容构造器及旧只读视图；它不解析旧 wire，须在 LC03-02 完成调用方迁移后再次复核清理。

### 13.2 Sol 独立验证

Windows PowerShell 需把两个 `-D` 参数作为完整字符串传给 Maven；由于 `-am` 上游模块没有所选测试，机械增加 `-DfailIfNoTests=false`，测试类选择与冻结口径不变：

```powershell
cd DEVICE
mvn -pl iot-sink/iot-sink-biz -am '-Dmaven.test.skip=false' '-DfailIfNoTests=false' '-Dtest=TelemetryAckV1ContractTest,TelemetryAckTopicContractTest,AckCommandContractTest' test
```

结果：

- `AckCommandContractTest`：5/0/0/0；
- `TelemetryAckTopicContractTest`：4/0/0/0；
- `TelemetryAckV1ContractTest`：14/0/0/0；
- 合计：23 tests，Failures=0，Errors=0，Skipped=0，28 个 reactor 全部 SUCCESS，`BUILD SUCCESS`；
- 新增生产代码 `/telemetry/**`、`#`、`+`、`$queue`、`$share` 扫描零命中；旧 wire 名称仅存在于 codec 显式拒绝集合及上述 deprecated 进程内兼容桥；秘密与尾随空白扫描零命中，`git diff --check` 通过；
- 未执行 PostgreSQL、EMQX、V011/U011、生产 DDL、压测或现场验证；并行 P02、WEB、项目介绍与 `DEVICE/.claude/` 工作树差异均未触碰。

## 14. LC03-02 实现记录（2026-08-27）

### 14.1 实现结果

决策所有者于本轮明确授权后，LC03-02 在 §7.2 白名单内完成：

- 新增 `CollectorAckSubscriptionCoordinator`：唯一订阅集合固定为 `TelemetryRouteSetProvider.currentRoutes()`（applied ConfigSnapshot 路由 ∪ PENDING/IN_FLIGHT outbox 路由），逐路由映射 `TelemetryRoute.ackTopic()`；刷新顺序按 §4.2 冻结执行——单次快照 → additions 全部 SUBACK 成功才原子替换 active set → 之后才取消 removals（取消失败仅延迟清理）；任一新增失败保持旧集合并报告稳定码 `ACK_SUBSCRIPTION_NOT_READY`；生产 `refresh()`/`recover()`/`close()` 均不产生 `#`、`+`、`$share`、`$queue`；
- `CollectorMqttAckSubscriber` 重写为 §4.3 fail-closed 入站关联：Topic 先经 `TelemetryAckTopicParser` 精确解析，payload 经 LC03-01 严格 codec 解出七字段 `AckCommand`（携带 route + observedAtMs），再交 `TelemetryOutboxPort.applyAck`；旧四字段 wire 在 codec 层即被拒绝；日志只保留稳定分类与 messageId/status，不输出 payload 或凭据；
- `SqliteOutboxWriter.executeApplyAck` 补齐 §4.3 第 4 条：ACK 查询扩展为同行返回 `request_id/product_identification/device_identification`，V1 命令（route 非空）在 requestId 与产品/设备身份任一不匹配时直接 commit 返回，不改状态、attempts、gap 或 route set；deprecated 兼容构造器（route 为 null）保持既有状态机行为供既有测试与 LC04 前过渡；
- `CollectorMqttProperties` 删除 `ackTopicPrefix`，`application-collector.yaml` 同步移除该配置项（生产配置不再暴露 broad 前缀）；
- `VertxCollectorMqttPublisher` 增加逐路由就绪门禁（未 SUBACK 路由 publish 返回 false 并报告 `ACK_SUBSCRIPTION_NOT_READY`）与 `whenConnected` 回调；
- `SqliteOutboxAutoConfiguration` 重排启动顺序：writer（bean 构造期）→ MQTT 连接 → `whenConnected(recover)` 初始精确 SUBACK → `runWhenReady(dispatcher::start)` 才允许 claim/publish；新增 `AckSubscriptionRefreshTask` 以默认 1000ms（可配 `easyaiot.collector.ack-subscription.refresh-interval-ms`）周期刷新订阅集合，与 collector reconcile 1s 节奏对齐，覆盖"配置 APPLIED 后新图首轮询前完成对应 ACK SUBACK"；
- `IotGatewayConfiguration` 注册 `CollectorTelemetryRouteSetProvider` 为 collector Profile bean（LC02 已实现但此前未装配）。

`OutboxCommand`/`OutboxCommandQueue` 在白名单内但本轮无需改动（diff 为零）。

### 14.2 直接测试证据

新增两个测试类（vertx-mqtt 4.5.13 真实 in-process MQTT server + 真实 SQLite outbox，无 mock broker）：

- `CollectorAckSubscriptionCoordinatorTest`（6 项）：恢复时精确并集订阅且无任何 broad filter；空集合不订阅任何东西；新增 SUBACK 部分失败保持旧集合且 `isReady()=false`；removals 仅在 additions 换新后取消；解绑但有未终态消息的路由保留订阅；`runWhenReady` 在完整 SUBACK 前不触发、之后恰好触发一次；
- `CollectorMqttAckSubscriberV1Test`（9 项）：ACCEPTED_DURABLE 经真实 MQTT 将 IN_FLIGHT 转 ACKED；DUPLICATE 对 ACKED 行幂等且零 gap；PENDING 行无需 claim 即可被成功 ACK 置 ACKED；requestId 错配不改 SQLite；同 payload 错设备 Topic 不改状态；旧四字段 wire 不可达；畸形 payload 丢弃；DEAD_LETTER 不被成功 ACK 复活；未知 messageId 不建行。

### 14.3 验收命令结果

```powershell
cd DEVICE
mvn -pl iot-sink/iot-sink-biz -am '-Dmaven.test.skip=false' '-DfailIfNoTests=false' '-Dtest=CollectorMqttAckSubscriberV1Test,CollectorAckSubscriptionCoordinatorTest,OutboxStateMachineTest,CollectorPollingRuntimeTest,TelemetryAckV1ContractTest,TelemetryAckTopicContractTest,AckCommandContractTest,CollectorSpringContextTest,CollectorTelemetryConfigurationTest,CollectorTelemetryRouteSetProviderTest,CollectorProfileArchitectureTest' test
```

- 11 类合计 **54 tests，Failures=0，Errors=0，Skipped=0**，`BUILD SUCCESS`（含真实 collector Spring 上下文装配验证 `CollectorSpringContextTest`，确认新增 bean 后 collector 封闭图不变）；
- 冻结验收命令末条 `mvn -pl iot-sink/iot-sink-biz -am -Dmaven.test.skip=false test-compile` 28 reactor `BUILD SUCCESS`；
- 测试实现说明：fixture 修正为生产同形 requestId（36 位 UUID，与 `PollingResultMapper.generateMessageId()` 一致）；`-am` 上游无所选测试故机械加 `-DfailIfNoTests=false`，与 LC03-01 同口径。

### 14.4 保护边界结果

- 白名单合规：改动文件为 §7.2 列出的 7 个生产文件 + `application-collector.yaml` + 新增协调器/测试；`OutboxCommand`/`OutboxCommandQueue` 零 diff；
- 生产代码 `ackTopicPrefix`/`ack-topic-prefix` 配置项零残留（仅注释中说明其移除）；新增生产代码 `/telemetry/**` 仅存在于订阅器 javadoc 的"never reach the writer"否定描述；
- 新增生产订阅代码 `#`/`+`/`$share`/`$queue` 零命中（协调器 javadoc 否定描述除外）；
- SQLite Schema/user_version、`TelemetryEnvelope` canonical/hash、V008/V009/V010/V011、LC02 Inbox/Outbox/Store/EMQX 资产、`telemetry_ingress_rejection` 全部零 diff；
- `git diff --check` 通过（exit 0，仅 CRLF 换行警告）；秘密扫描零命中；
- 会话开始时已存在的范围外改动（`.doc/` P02 文档、`WEB/package.json`、`WEB/scripts/`、项目介绍 md）未被触碰；本轮未 commit。

### 14.5 未执行项（如实保持 OPEN）

- LC03-04 组合 E2E（真实隔离 EMQX + PG + SQLite 重启故障点矩阵）与 LC03-05 全模块回归未开始，LOCKED 保持；
- 真实 EMQX 5.8.7 下 collector ACL 对下游 ACK Topic 的授权画像未在本包复验（属 LC03-04）；
- 未执行任何 PostgreSQL/V012/生产 DDL/部署/现场验证。

### 14.6 Sol 独立复核（2026-08-27）

Sol 在实现者交付后独立执行以下复核，未依赖实现者自报结果：

- **冻结验收命令复跑**：§14.3 的 11 类命令在 HEAD 工作区原样复跑，54 tests，Failures=0，Errors=0，Skipped=0，28 reactor `BUILD SUCCESS`；冻结的最小 4 类命令（`CollectorMqttAckSubscriberV1Test,CollectorAckSubscriptionCoordinatorTest,OutboxStateMachineTest,CollectorPollingRuntimeTest`）独立复跑 22/22 全绿；
- **代码审阅**：协调器刷新顺序与 §4.2 逐条一致（快照 → additions 全 SUBACK → 原子 swap → removals 取消，失败保持旧集合并报 `ACK_SUBSCRIPTION_NOT_READY`）；订阅器 fail-closed 顺序与 §4.3 一致；`SqliteOutboxWriter` 的 requestId/route 关联校验在 V1 命令路径生效，兼容构造器（route=null）不改既有行为；
- **白名单核验**：`CollectorTelemetryRouteSetProvider` 与 `TelemetryRouteSetProvider` 均为 LC02 已提交代码（`fb75dbade`，工作区副本与提交版本逐字节一致），非本轮新增；其余改动均在 §7.2 白名单内；`iot-sink-api`、V011/U011、`.scripts/`、`CenterMqttAckPublisher`（LC03-03 范围）零 diff；
- **保护扫描**：`git diff --check` exit 0；秘密扫描零命中；生产代码 `ackTopicPrefix` 配置项与订阅 wildcard（`#`/`+`/`$share`/`$queue`）零命中；`OutboxCommand`/`OutboxCommandQueue` 零 diff；
- **遗留确认**：deprecated 四参数 `AckCommand` 构造器在主代码中已无调用（仅 `OutboxStateMachineTest` 等 LC04 前过渡测试使用），其清理复核按 LC03-01 §13.1 约定归 LC03-05 回归收口。

结论：LC03-02 转 `COMPLETE / SOL-ACCEPTED`。下一步须决策所有者独立授权 `LC03-03`（V012 候选 + center dispatch repository + 即时 ACK 与 10 秒扫描；临时 PostgreSQL 合同须另行授权）；LC03-04～05 保持 LOCKED。

## 15. LC03-03 实现记录（2026-08-27，待 Sol 复核）

决策所有者于本轮授权 LC03-03（临时 PostgreSQL 合同执行仍单独授权，本包只准备不执行）。

### 15.1 实现结果（§7.3 白名单内）

- **V012/U012 候选**（`assets/td005-migration/`）：`ack_sent_at_ms BIGINT` + `ack_attempts INTEGER NOT NULL DEFAULT 0` + `ck_inbox_ack_attempts_non_negative` CHECK + `idx_inbox_ack_pending` 部分索引 `(received_at_ms,id) WHERE ack_sent_at_ms IS NULL`；全列/约束/索引中文 COMMENT；V009 前置校验、预存在列/索引幂等拒绝；U012 仅在零发送痕迹时可完整卸载。不接 `APPLY_STEPS`、不改 V011/U011、不更新首装 dump；
- **iot-sink-api** 新增 `telemetry/inbox/ack/TelemetryAckDeliveryRow`（不可变，路由/requestId 缺失即不可发送）与 `TelemetryAckDispatchPort`（claimPending/loadForImmediateAck/markSent）；
- **iot-sink-biz** 新增 `JdbcTelemetryAckDeliveryRepository`（SQL 只选路由完整且 requestId 非空行；`FOR UPDATE SKIP LOCKED` 仅限领取语句，不跨 publish；markSent 条件更新天然幂等）、`CenterTelemetryAckService`（即时路径：行事实与 Inbox 返回的 requestId/persistedAt 逐项比对，不匹配零 publish；publish 失败/抛出保持 sent NULL）、`TelemetryAckReconciliationTask`（启动即扫一次 + 每 10 秒，每批 ≤1000，逐行 DUPLICATE 补发，单行失败不中断整批，claim 失败保持任务存活）、`CenterTelemetryAckPublisherPort`；
- **CenterMqttAckPublisher 重写为 ACK V1**：LC03-01 冻结 codec 产出七字段 payload，Topic 只由 `row.route().ackTopic()` 派生；旧四字段 payload 与 `/telemetry/ack/**` 拼接从生产代码移除（javadoc 否定描述除外）；publish Future 确认成功才返回 true；
- **接线**：`TelemetryInboxAutoConfiguration` 按 `easyaiot.telemetry.ack.enabled`（默认关）装配 dispatch repository、ack service、reconciliation task（`scan-interval-ms`/`batch-size` 可配）；ACK publisher 复用上行 subscriber 的 MQTT client。

### 15.2 直接测试证据

- `CenterTelemetryAckServiceTest`（7 项）：collision/null 状态零 publish；行不可发送 fail-closed；requestId/persistedAt 错配零 publish；成功路径逐字段断言（canonical Topic、code 0/1001、原 persistedAt）；publish 失败与抛出均保持 sent NULL；
- `TelemetryAckReconciliationTaskTest`（5 项）：启动即扫描；整批 DUPLICATE；claim 异常吸收不外抛；**单行 publish 失败不中断整批（首轮实现被该测试否决后已修正）**；非正参数 fail-fast；
- `JdbcTelemetryAckDeliveryRepositoryTest`（6 项，隔离 PG 未注入时 NOT_RUN_LOCAL_ENV 跳过）：领取顺序与上限、attempts 递增、markSent 条件幂等、行事实不完整 fail-closed、即时加载路径。

### 15.3 验收命令结果

`mvn -pl iot-sink/iot-sink-biz -am -Dmaven.test.skip=false -DfailIfNoTests=false -Dtest=...` 12 类（LC03-03 三类 + LC03-01/02 回归 + collector 装配）：**59 tests，Failures=0，Errors=0**；`JdbcTelemetryAckDeliveryRepositoryTest` 按 NOT_RUN_LOCAL_ENV 口径 0 run（环境未注入，非宣称通过）；`test-compile` 28 reactor `BUILD SUCCESS`。

### 15.4 隔离合同脚本（创建未执行）

`.scripts/postgresql/td005-migration/tests/lc03_v012_contract.sh`：唯一前缀临时库；覆盖基线迁移、列/默认/COMMENT/部分索引断言、负 attempts CHECK 拒绝、V012 重放拒绝、缺 V009 前置拒绝、U012 干净卸载；`LC03_V012_PG_ENABLED=true` 显式守卫（本轮验证无环境变量时 exit 1 fail-closed），不接共享 runner。

### 15.5 保护边界结果与未执行项

- 新增生产代码 `/telemetry/**`、`#`、`+`、`$queue`、`$share` 零命中（javadoc 否定描述除外）；V011/U011、共享 runner、首装 dump、EMQX ACL、Docker Compose 零 diff；秘密扫描零命中；`git diff --check` 通过；
- 真实隔离 PostgreSQL 合同（15.4 脚本）未执行，待临时库授权；LC03-04 组合 E2E、LC03-05 回归收口保持 LOCKED；V012 正式落库须另立 `LC03-DB-RUNTIME-01`。

### 15.6 Sol 独立复核（2026-08-27）

Sol 在实现者交付后独立执行以下复核，未依赖实现者自报结果：

- **冻结验收命令复跑**：§15.3 的 12 类命令原样复跑三轮（复核初轮、偏离修复后两轮），最终 59 tests，Failures=0，Errors=0（`JdbcTelemetryAckDeliveryRepositoryTest` 按隔离 PG 未注入口径 0 run）；冻结 `test-compile` 28 reactor `BUILD SUCCESS`；
- **代码审阅发现并处置两个偏离**：
  1. *事务边界注释失实*（文档性）：`claimPending` javadoc 声称 SELECT+递增"同一短事务内"，实现实为两条独立语句、锁不跨语句更不跨 publish——行为满足 §5.4（SKIP LOCKED 只用于领取、禁止跨 publish 持锁、多实例重复领取合法），已修正注释如实描述；
  2. *即时 ACK 未接线*（实质缺口）：`sendImmediateAck` 无任何生产调用方，即时链路实际只有 10 秒扫描器兜底，违背 §2.2"事务返回后由成功 ACK service 从持久行生成 ACK"与 §5.3"即时发送"。Sol 判定属本包应完成未完成部分，已补齐：`CenterTelemetryIngressResult.Accepted` 增加已验证 `tenantId` 字段（handler 从权威注册解析结果传入，不信任 payload）；`CenterMqttInboxSubscriber.onMessage` 在 Inbox 返回后对 `ACCEPTED_DURABLE/DUPLICATE` 条目调用 `sendImmediateAck`（逐条 try/catch，单条失败不影响后续也不影响已提交 Inbox 事实），collision/拒绝零 ACK；AutoConfig 经 `ObjectProvider<CenterTelemetryAckService>` 可选注入避免 bean 环（subscriber→ackService→publisher→subscriber）；
- **白名单核验**：复核改动涉及 `CenterTelemetryIngressResult.java`、`CenterTelemetryIngressHandler.java`（Accepted 构造补 tenantId 一处）两个 §7.3 未列文件。Sol 按 §7 停工条件 7 判定为实现缺口的最小修复而非越界扩展：两处均为 additive（新字段 + 构造参数），不改变既有拒绝语义、guard 顺序或 canonical hash；已在此记录扩界理由。其余改动均在 §7.3 白名单内；V011/U011、共享 runner、首装 dump、EMQX ACL 零 diff；
- **装配合同回归**：`TelemetryInboxAutoConfigurationTest` 反射签名同步（新增可选 ObjectProvider 参数），保留"subscriber 工厂不依赖 publisher"的既有断言；该测试 5/5 通过；
- **保护扫描**：`git diff --check` exit 0；秘密扫描零命中；生产代码 `/telemetry/**`、collector 侧 wildcard（`#`/`+`/`$queue`/`$share`）零命中（center 上行 `$share` 共享组为 LC02 既有合法设计，任务单 §5.4 明确允许）。

结论：LC03-03 转 `COMPLETE / SOL-ACCEPTED`（含 §15.6 两处偏离修复）。LC03-01/02/03 均已接受；下一步须决策所有者独立授权 `LC03-04`（collector↔EMQX↔center↔PG↔SQLite 组合 E2E 与重启故障点矩阵；真实隔离 broker/PG 需另行授权）；LC03-05 保持 LOCKED。临时 PostgreSQL 合同（§15.4 脚本）与 `LC03-DB-RUNTIME-01` 正式落库继续 OPEN。

## 16. LC03-04 Sol 最终冻结（2026-08-27）

### 16.1 漂移修正与授权边界

本节取代 §7.4 的泛化边界与 §9 的候选命令，成为 LC03-04 的唯一实现/验收合同。Sol 已确认：

- 当前基线提交为 `fc12af3ce`，LC03-01～03 已分别由提交 `a40a0ed38`、`affe81527`、`78fce83da`/`fc12af3ce` 实现并接受；
- 原 §12 仍误写“下一步 LC03-02”，原 §7.3 把实际 `td005-migration` 路径误写为 `td003-migration`，已在 1.1.0 修正；
- 本轮只改任务/进度文档，不新增测试或脚本，不运行 Maven、V012/U012、PostgreSQL、EMQX，不修改生产环境；
- LC03-04 拆为两个必须独立授权的包：`04A` 只做确定性组合 fixture/假服务测试，`04B` 才做真实隔离 Linux Docker PostgreSQL/EMQX/SQLite；04A 授权不得被解释为 04B 授权；
- `LC03-DB-RUNTIME-01` 正式落库、默认 migration runner、首装 dump、生产 ACL/TLS/Compose、压测和现场资格均不属于 LC03-04A/04B。

### 16.2 LC03-04A fixture 与假服务编排

04A 的目标是用真实生产类证明重启窗口语义，同时把外部不确定性收敛为可控故障点：

- **真实组件**：进程内 Vert.x MQTT server、`CollectorMqttAckSubscriber`、`CollectorAckSubscriptionCoordinator`、真实临时 SQLite outbox 文件、ACK V1 codec/topic parser、`CenterTelemetryAckService`、`TelemetryAckReconciliationTask`；MQTT 与 SQLite 不得 mock；
- **持久假服务**：`DurableFakeInbox` 按 messageId + canonical 内容区分 INSERTED/DUPLICATE/COLLISION，并保留首次 `persistedAt/requestId/tenant/product/device`；`DurableFakeAckDispatchPort` 持久保存 attempts/sent 状态；`IdempotentFakeStore` 只保存一个逻辑样本；重建 center 对象时这些持久状态不得清空；
- **权威注册假服务**：只允许一个确定性 `DeterministicAuthority`，固定把 fixture route 解析到 tenant `1`；不得启动、修改或替代 iot-device 生产服务，不得信任 payload tenant；
- **故障注入**：`FaultInjectingAckPublisher` 只允许 `DROP_BEFORE_PUBLISH`、`PUBLISH_THEN_FAIL_BEFORE_MARK` 两种单次 barrier；barrier 必须由 latch/future 驱动，不得用 `Thread.sleep` 决定正确性；eventually 上限固定 5 秒；
- **重启定义**：center 重启仅关闭并重建 service/scanner/subscriber，保留 durable fake state；collector 重启关闭并重建 subscriber/coordinator/writer，复用同一 SQLite 文件；不得把“重新 new 一个空内存对象”宣称为重启恢复；
- **fixture 固定值**：`fixture-manifest.json` 固定 `tenantId=1`、`productIdentification=lc03-product`、`deviceIdentification=lc03-device`、合法 UUID requestId/messageId、canonical upstream/ack topic、首次 persistedAt；accepted/collision 两个 envelope 使用同一 messageId 但不同 canonical 内容，秘密字段为零；JSON 资源须由生产 codec 读取，禁止测试内另写宽松 parser。

04A 新增两个测试类各固定 3 个 `@Test`，总计 **6/0/0/0，Skipped=0**。缺少 fixture、端口占用、平台差异或超时均必须失败，不得 assumption/skip。

### 16.3 五个组合 E2E 与故障点

| ID | 故障点与动作 | 必须成立的终态 |
|---|---|---|
| E2E-01 | 正常 INSERTED：先完成 exact ACK SUBACK，再发布上行，center commit 后即时发送 | SQLite `ACKED`；Inbox=1、Store=1；ACK 为 `ACCEPTED_DURABLE/ACCEPTED_DURABLE/INBOX_COMMITTED`，Topic/requestId/route/persistedAt 全匹配 |
| E2E-02 | 首个 ACK 在 collector 应用前丢失；collector lease/reclaim 后重发同一 envelope | center 返回 `DUPLICATE` 并再次 ACK；SQLite 最终 `ACKED`；Inbox=1、Store=1，无第二逻辑行 |
| E2E-03 | center 在 Inbox commit 后、ACK publish 前重启 | 重建 scanner 首次 `scanOnce()` 补发成功，attempts 增加、sent 设置；collector `ACKED`，无漏发 |
| E2E-04 | center 在 ACK publish 成功后、`markSent` 前重启 | collector 可先 `ACKED`；重建 scanner 允许再发相同业务成功 ACK并最终 mark；重复 ACK 幂等，不回退状态、不增加 Inbox/Store |
| E2E-05 | collector 在上行 publish 后、ACK 应用前重启 | 同一 SQLite 文件恢复未终态 route；exact SUBACK 成功后才恢复 dispatch；center scanner 或 DUPLICATE 即时路径补发，SQLite 最终 `ACKED` |

另设一个负向测试：collision、错误 Topic/tenant/requestId、畸形 ACK、权威注册失败和 Inbox 失败均为 **零成功 ACK、零 sent mark、零 SQLite 状态推进**；不得借此实现 LC04 的拒绝审计、FINAL/RETRYABLE ACK。

### 16.4 LC03-04B 真实隔离编排

04B 只能在 04A 通过并经 Sol 复核、且决策所有者再次明确授权后执行。唯一入口为 `.scripts/mqtt/tests/lc03_success_ack_contract.sh`，其合同如下：

1. 只使用 Linux Docker `maven:3.9.16-amazoncorretto-17-alpine`、`postgres:18`、`emqx/emqx:5.8.7`；镜像 ID 分别固定为 `53215f45dda1e255693160346acc2a9cc10e3b6a59a19ce3a2fc95c476c1772a`、`3a82e1f56c8f0f5616a11103ac3d47e632c3938698946a7ad26da0df1334744a`、`556aea6d62134524ecd1fcca53380b460b52995344dce571d484f042d9b15e7d`；Docker 必须报告 `OSType=linux`；
2. 新建唯一 `lc03-04-*` internal network、临时 PG/EMQX/Maven 容器和仓库外 temp 目录；不复用 `postgres-server`、现有 EMQX、Compose、生产网络或数据库，不暴露宿主端口；
3. 随机 PG、center、collector 凭据只保存在当前脚本进程/临时挂载，禁止读取 `DEVICE/.env`；Maven 容器必须用仓库外空文件只读覆盖 `/workspace/DEVICE/.env`，并挂载 `/tmp` tmpfs；日志只在仓库外暂存；
4. 临时 PG 只按 `V008 → V010 → V009 → V012` 建测试库；先执行既有 `lc03_v012_contract.sh`，要求 `SUMMARY PASS=8`，再额外证明带发送痕迹的 U012 卸载拒绝 **1/1**；V011/U011 不运行；
5. 临时 EMQX 只给 center 身份固定共享上行订阅与 canonical ACK publish，给 collector 身份自身 canonical upstream publish 与 exact ACK subscribe；跨设备、普通/其他共享组、legacy `/telemetry/**`、系统 Topic 和越权 ACK 全拒绝；
6. `TelemetryAckRealBrokerPostgresE2ETest` 使用真实 `JdbcTelemetryInbox`、`JdbcTelemetryAckDeliveryRepository`、`JdbcTelemetryStore`、`TelemetryProjectionOrchestrator`、真实 EMQX 和真实 collector SQLite，复现 §16.3 五场景 + 一项负向矩阵，共 **6/0/0/0，Skipped=0**；只允许把 iot-device 权威注册替换为固定只读 fixture；
7. JUnit 仅在脚本显式设置 `LC03_04_E2E_ENABLED=true` 时被 `-Dtest=TelemetryAckRealBrokerPostgresE2ETest` 选中；开关或任一精确变量缺失必须 fail，不得 skip；
8. `finally` 必须删除全部 `lc03-04-*` container/network/temp/log/mask，清除精确 PG/JDBC/EMQX 环境变量，并验证 PG 数据库/容器/网络/文件/环境 residue 全为 0；秘密扫描在日志删除前完成，只报告 PASS/FAIL，不报告值、长度、hash、片段、编码或 URL。

脚本任一步退出非零、计数不符、出现 skip、秘密扫描命中或残留非零，必须立即停止；同一授权下不重跑、不改断言追计数，交回 Sol 重新冻结。

### 16.5 保护摘要算法

所有摘要在 04A/04B 开工前和收工后各计算一次。文件路径统一为仓库相对路径并把 `\\` 转为 `/`，按 ordinal 升序；每个 manifest 行固定为 `<relative-path>\\t<uppercase-file-SHA256>\\n`，聚合值为该 UTF-8 manifest 字节的 SHA-256。文件缺失、多出或计数变化均直接失败，禁止只比较聚合值。

当前冻结值（基线提交 `fc12af3ce`）：

| 组 | 计数 | 聚合 SHA-256 |
|---|---:|---|
| `BASELINES` | 2 | `541645C6F85B6CD203C8965020C122CA8774A1C6F60971C516432CA613D81656` |
| `LC03_PRODUCTION` | 25 | `E8ED9ABE6FE950F8EFB3BA6E6AFE6E9B464A4AD1A5F3D899CCFA90A5D4B95EFA` |
| `LC03_DIRECT_TESTS` | 9 | `A11640676A8D0E07B9005C2B50CD41D621C483DD3F6C0D0D6DD5F76D68DA22F2` |
| `LC03_MIGRATION` | 3 | `AE9917094BF431E584A341869C1E11641BA260FAC40F2CE05D454623AE33D832` |
| `INBOX_OUTBOX_PRODUCTION` | 73 | `411CB44E1B84071E1F0E29896E6B97EDF07AB46EAF8D40A75CB2A914EC6A55F7` |
| `STORE_PROJECTOR` | 10 | `CC492C22DE4C34B4DFB11D523AD15E83C0A3F04807F00E9C77DC42C064D7A067` |
| `BASE_MIGRATIONS` | 5 | `738D9DD72B920B200BDDAD2D36AFE3203F4262D30CF1EB7C1E5829BB169867E8` |
| `P02_V011` | 3 | `170480FAD8BB731971AA8E8C093C0957B96E1BD9B9B15E5308F25A8C8592B7D3` |
| `EMQX_LC02` | 4 | `7CD42298B18865123E1CC022F365A7CBAA5C8F45809EAC20E02CE8D8BDA2943F` |

`BASELINES` 精确包含双基线。`LC03_MIGRATION` 精确包含 V012、U012、`lc03_v012_contract.sh`。`BASE_MIGRATIONS` 精确包含 V008、V009、U009、V010、`td005_migration.sh`。`P02_V011` 精确包含 V011、U011、`p02_v011_contract.sh`。`EMQX_LC02` 精确包含 LC02-09 的 `emqx.conf`、`acl.conf`、合同脚本与真实集成测试。

`LC03_PRODUCTION` 精确包含下列 25 个现有文件；04A/04B 收工时不得变化：

```text
DEVICE/iot-sink/iot-sink-api/src/main/java/com/basiclab/iot/sink/telemetry/ack/TelemetryAckStatus.java
DEVICE/iot-sink/iot-sink-api/src/main/java/com/basiclab/iot/sink/telemetry/ack/TelemetryAckV1.java
DEVICE/iot-sink/iot-sink-api/src/main/java/com/basiclab/iot/sink/telemetry/outbox/AckCommand.java
DEVICE/iot-sink/iot-sink-biz/src/main/java/com/basiclab/iot/sink/telemetry/ack/TelemetryAckCodecException.java
DEVICE/iot-sink/iot-sink-biz/src/main/java/com/basiclab/iot/sink/telemetry/ack/TelemetryAckTopicParser.java
DEVICE/iot-sink/iot-sink-biz/src/main/java/com/basiclab/iot/sink/telemetry/ack/TelemetryAckV1Codec.java
DEVICE/iot-sink/iot-sink-biz/src/main/java/com/basiclab/iot/sink/config/IotGatewayConfiguration.java
DEVICE/iot-sink/iot-sink-biz/src/main/java/com/basiclab/iot/sink/outbox/dispatch/CollectorAckSubscriptionCoordinator.java
DEVICE/iot-sink/iot-sink-biz/src/main/java/com/basiclab/iot/sink/outbox/dispatch/CollectorMqttAckSubscriber.java
DEVICE/iot-sink/iot-sink-biz/src/main/java/com/basiclab/iot/sink/outbox/dispatch/CollectorMqttProperties.java
DEVICE/iot-sink/iot-sink-biz/src/main/java/com/basiclab/iot/sink/outbox/dispatch/VertxCollectorMqttPublisher.java
DEVICE/iot-sink/iot-sink-biz/src/main/java/com/basiclab/iot/sink/outbox/sqlite/SqliteOutboxAutoConfiguration.java
DEVICE/iot-sink/iot-sink-biz/src/main/java/com/basiclab/iot/sink/outbox/sqlite/SqliteOutboxWriter.java
DEVICE/iot-sink/iot-sink-biz/src/main/resources/application-collector.yaml
DEVICE/iot-sink/iot-sink-api/src/main/java/com/basiclab/iot/sink/telemetry/inbox/ack/TelemetryAckDeliveryRow.java
DEVICE/iot-sink/iot-sink-api/src/main/java/com/basiclab/iot/sink/telemetry/inbox/ack/TelemetryAckDispatchPort.java
DEVICE/iot-sink/iot-sink-biz/src/main/java/com/basiclab/iot/sink/telemetry/inbox/TelemetryInboxAutoConfiguration.java
DEVICE/iot-sink/iot-sink-biz/src/main/java/com/basiclab/iot/sink/telemetry/inbox/ack/CenterTelemetryAckPublisherPort.java
DEVICE/iot-sink/iot-sink-biz/src/main/java/com/basiclab/iot/sink/telemetry/inbox/ack/CenterTelemetryAckService.java
DEVICE/iot-sink/iot-sink-biz/src/main/java/com/basiclab/iot/sink/telemetry/inbox/ack/JdbcTelemetryAckDeliveryRepository.java
DEVICE/iot-sink/iot-sink-biz/src/main/java/com/basiclab/iot/sink/telemetry/inbox/ack/TelemetryAckReconciliationTask.java
DEVICE/iot-sink/iot-sink-biz/src/main/java/com/basiclab/iot/sink/telemetry/inbox/mqtt/CenterMqttAckPublisher.java
DEVICE/iot-sink/iot-sink-biz/src/main/java/com/basiclab/iot/sink/telemetry/inbox/mqtt/CenterMqttInboxSubscriber.java
DEVICE/iot-sink/iot-sink-biz/src/main/java/com/basiclab/iot/sink/telemetry/inbox/route/CenterTelemetryIngressHandler.java
DEVICE/iot-sink/iot-sink-biz/src/main/java/com/basiclab/iot/sink/telemetry/inbox/route/CenterTelemetryIngressResult.java
```

`LC03_DIRECT_TESTS` 精确包含现有 `AckCommandContractTest`、`TelemetryAckTopicContractTest`、`TelemetryAckV1ContractTest`、`CollectorAckSubscriptionCoordinatorTest`、`CollectorMqttAckSubscriberV1Test`、`CenterTelemetryAckServiceTest`、`JdbcTelemetryAckDeliveryRepositoryTest`、`TelemetryAckReconciliationTaskTest`、`TelemetryInboxAutoConfigurationTest`。`INBOX_OUTBOX_PRODUCTION` 为 API `telemetry/inbox/**`、API `telemetry/outbox/**`、biz `telemetry/inbox/**`、biz `outbox/**` 下现有生产 Java/YAML/SQL/shell/conf 文件；`STORE_PROJECTOR` 为 API/biz `telemetry/store/**` 生产 Java加 `TelemetryProjectionOrchestrator.java`。两组都必须同时校验计数、manifest 明细和聚合值。

双基线单文件值另固定为：宪法 `76BF30903A5A62C957A75B57E24080AA7132DE6992D56C262EEAFBE7BE553C4D`；平台计划 `F0E5734C8FADA6D0E4DAB98F7D12F58940077F9C60AA8DA42AC8EF45C6F69869`。

### 16.6 静态禁止扫描

开工/收工必须同时满足：

- 新增 LC03-04 文件中 `/telemetry/`、`$queue`、未冻结 `$share`、MQTT `#`/`+` broad collector ACK subscription 零命中；fixture 中 canonical `/iot/...` 不算禁止；
- `telemetry_ingress_rejection`、`REJECTED_FINAL`、`REJECTED_RETRYABLE`、collision ACK publisher、生产 ACL/Compose/default migration runner 修改零新增；
- `git diff --check` 通过；白名单外工作树差异开工/收工逐项相等，不得触碰或纳入提交；
- 任何 `.env`、凭据、token、password、JDBC URL、日志、临时数据库转储不得进入 Git 或输出。

### 16.7 LC03-04A 最终验收命令

命令必须在仓库根目录的 Linux shell 执行；`LC03_M2_REPOSITORY` 必须预先指向仓库外 Maven repository。两个 Maven 容器均使用空 `.env` mask、禁外网和 `/tmp` tmpfs：

```bash
set -euo pipefail
LC03_REPO="$(pwd)"
: "${LC03_M2_REPOSITORY:?must point to an external Maven repository}"
LC03_EMPTY_ENV="$(mktemp)"
trap 'rm -f "${LC03_EMPTY_ENV}"' EXIT

docker run --rm --name lc03-04a-direct --network none \
  -v "${LC03_REPO}:/workspace" \
  -v "${LC03_M2_REPOSITORY}:/root/.m2" \
  -v "${LC03_EMPTY_ENV}:/workspace/DEVICE/.env:ro" \
  --tmpfs /tmp:rw,noexec,nosuid,size=512m \
  -w /workspace/DEVICE \
  maven:3.9.16-amazoncorretto-17-alpine \
  mvn -pl iot-sink/iot-sink-biz -am \
    -Dmaven.test.skip=false -DfailIfNoTests=false \
    -Dtest=TelemetryAckRestartReconciliationTest,TelemetryAckCombinedE2ETest test

docker run --rm --name lc03-04a-regression --network none \
  -v "${LC03_REPO}:/workspace" \
  -v "${LC03_M2_REPOSITORY}:/root/.m2" \
  -v "${LC03_EMPTY_ENV}:/workspace/DEVICE/.env:ro" \
  --tmpfs /tmp:rw,noexec,nosuid,size=512m \
  -w /workspace/DEVICE \
  maven:3.9.16-amazoncorretto-17-alpine \
  mvn -pl iot-sink/iot-sink-biz -am \
    -Dmaven.test.skip=false -DfailIfNoTests=false \
    -Dtest=AckCommandContractTest,TelemetryAckTopicContractTest,TelemetryAckV1ContractTest,CollectorAckSubscriptionCoordinatorTest,CollectorMqttAckSubscriberV1Test,OutboxStateMachineTest,CollectorPollingRuntimeTest,CenterTelemetryAckServiceTest,JdbcTelemetryAckDeliveryRepositoryTest,TelemetryAckReconciliationTaskTest,TelemetryInboxAutoConfigurationTest,CenterTelemetryIngressHandlerTest,TelemetryAckRestartReconciliationTest,TelemetryAckCombinedE2ETest test

docker run --rm --name lc03-04a-compile --network none \
  -v "${LC03_REPO}:/workspace" \
  -v "${LC03_M2_REPOSITORY}:/root/.m2" \
  -v "${LC03_EMPTY_ENV}:/workspace/DEVICE/.env:ro" \
  --tmpfs /tmp:rw,noexec,nosuid,size=512m \
  -w /workspace/DEVICE \
  maven:3.9.16-amazoncorretto-17-alpine \
  mvn -pl iot-sink/iot-sink-biz -am -Dmaven.test.skip=false test-compile
```

固定判定：直接新测试 **6/0/0/0**；14 类回归 **75/0/0/0**（其中真实 JDBC 类在未选真实环境时为 0 run，不得报告为已通过 PG 合同，但也不得产生 skip）；28 个受影响 reactor `test-compile` 全部 SUCCESS；三个容器、mask、临时文件和精确环境变量 residue=0。若实现新增测试方法导致计数不再为 6/75，必须先交 Sol 修订冻结单，不得自行接受漂移。

### 16.8 LC03-04B 最终验收命令

04B 获独立授权后，唯一允许的执行命令为：

```bash
bash .scripts/mqtt/tests/lc03_success_ack_contract.sh
```

脚本必须内建并逐项断言 §16.4，不接受手工拆跑替代。固定结果：V012 候选隔离合同 `SUMMARY PASS=8`；U012 有发送痕迹拒绝 `1/1`；真实 PG/EMQX/SQLite E2E `6/0/0/0`；ACL 正负矩阵全部通过；Failures=0、Errors=0、Skipped=0；全部前缀 residue=0；保护组 start/end 完全相等。正式 V012 落库和生产环境变更仍为 `NOT AUTHORIZED / NOT RUN`。

### 16.9 完成判定与下一授权

- 本次 Sol 工作完成条件只是：任务单 1.1.0、M1 续作入口和技术设计索引一致，文档 diff/链接/状态通过；不得把 LC03-04 标为实现完成；
- 04A 完成后状态只能转 `LC03-04A COMPLETE / SOL-ACCEPTED；04B NOT-YET-AUTHORIZED`；
- 只有 04B 的真实隔离证据也由 Sol 接受后，LC03-04 才可整体转 `COMPLETE / SOL-ACCEPTED` 并解锁 LC03-05；
- 下一条可执行授权文本固定为：`授权 GPT-5.6 Luna Max 执行 M1-LC-03 LC03-04A 确定性组合 E2E、fixture 与假服务故障点测试；不得执行 LC03-04B 真实隔离 PostgreSQL/EMQX。`

## 17. LC03-04A 暂停检查点（2026-08-27）

### 17.1 调度与授权事实

- 决策所有者已授权 GPT-5.6 Luna Max 执行 LC03-04A，并明确禁止 LC03-04B；两个 Luna Max 代理实例均长时间没有 checkpoint、工具输出或文件写入，已被安全中断，没有静默替换为其他模型；
- 决策所有者随后明确授权 GPT-5.6 Sol 接管 LC03-04A，继续禁止 LC03-04B；Sol 已开始保护/API 前检，但在新增文件前按决策所有者要求暂停并保存进度；
- 此暂停不撤销 LC03-04A 授权，但任何续作模型仍必须完整遵守 §7.4、§16.2～§16.7；04B、正式 V012 落库和生产环境变更仍未授权。

### 17.2 已完成前检

- 当前检查点 HEAD 为 `74eb61285`，`4eff90c96` 与 `fc12af3ce` 均为其 ancestor；
- 双基线仍为宪法 1.6.0 / 平台计划 1.5.0，单文件 SHA-256 分别保持 `76BF30903A5A62C957A75B57E24080AA7132DE6992D56C262EEAFBE7BE553C4D`、`F0E5734C8FADA6D0E4DAB98F7D12F58940077F9C60AA8DA42AC8EF45C6F69869`；
- `fc12af3ce..HEAD` 未观察到 LC03 生产、既有直接测试或 V012/U012/LC03 migration 保护路径修改；§16.5 九组摘要仍须由续作模型在真正开工前完整复算，不能把本条路径 diff 核对替代 manifest 门禁；
- Sol 已读取并确认可复用接口：真实进程内 Vert.x MQTT server、`CollectorMqttAckSubscriber`、`CollectorAckSubscriptionCoordinator`、真实 `SqliteTelemetryOutbox`、`CenterTelemetryIngressHandler`、`CenterTelemetryAckService`、`TelemetryAckReconciliationTask`；确定性 fake 只承担 authority、durable Inbox/dispatch/store 与两种 fault barrier。

### 17.3 尚未执行与零产物

- §7.4 的 3 个 Java 测试/fixture 与 3 个 JSON 资源均尚未创建；LC03-04A 源码 diff 为零；
- §16.7 的直接 6 项、14 类 75 项回归和 28-reactor `test-compile` 均 `NOT RUN`；
- PostgreSQL、EMQX、V012/U012、`.scripts/mqtt/tests/lc03_success_ack_contract.sh`、`TelemetryAckRealBrokerPostgresE2ETest` 和生产环境变更全部 `NOT RUN / NOT CREATED`；
- 工作树存在 P02/TD-003/events/`.claude` 等并行范围外改动，LC03 续作不得触碰、归属或提交它们，实际清单须在下一次开工重新读取。

### 17.4 下一次直接续作入口

下一模型从以下步骤继续，不需重新设计 LC03-04：

1. 完整读取双基线、根 `AGENTS.md`、本任务单 §7.4 与 §16～§17；
2. 重新读取工作树并完整复算 §16.5 九组 start manifest；任一漂移立即 STOP；
3. 只新增 §7.4 的六个 04A 文件，按 §16.2～§16.3 实现两个类各 3 个测试；
4. 严格执行 §16.7 三段 Linux Docker 禁网验收并收集 `6/0/0/0`、`75/0/0/0`、28-reactor SUCCESS；
5. 复算保护 start/end、静态禁止扫描和零残留，交 Sol 独立复核；不得进入 §16.8。

## 18. LC03-04A 第二次暂停检查点（2026-08-27，用户主动中止）

### 18.1 已完成（本轮 Sol 接管执行）

- **§17.4 步骤 2 完成**：九组 start manifest 全部复算通过。关键发现：§16.5 冻结聚合值的排序规则是 **locale 排序**（ASCII 大小写不敏感 + 中文目录拼音序，即 zh_CN/en locale 的 `sort` 行为），不是字节序。BASELINES 需 [计划, 宪法] 顺序、LC03_PRODUCTION 需 case-insensitive 排序；九组均在复现规则下逐组匹配冻结值，无漂移。复算脚本存于 `C:\Users\青见\lc03_manifest_verify.py`（工具性质，未入仓库）；
- **§7.4 六个 04A 文件全部创建**：三个 JSON 资源（fixture-manifest 固定 tenantId=1/lc03-product/lc03-device/合法 UUID/canonical Topic/首次 persistedAt，accepted 与 collision 同 messageId 不同 canonical 内容）+ `Lc03AckE2eFixture`（真实 Vert.x MQTT server + 真实 SQLite outbox 文件 + 生产 collector subscriber/coordinator/ack service/scanner/codec；DurableFakeInbox/DispatchPort/Store 跨重启保留；FaultInjectingAckPublisher 两种单次 barrier 由 latch 驱动；ACK 经 server 端 endpoint 下发给 collector 精确订阅）+ 两个测试类各 3 项；
- **直接验收（本机 Windows）**：`TelemetryAckRestartReconciliationTest` 3/3 + `TelemetryAckCombinedE2ETest` 3/3 = **6/0/0/0**，覆盖 §16.3 E2E-01～05 全部五个场景与负向矩阵（collision/requestId/persistedAt 错配/null 状态/未知 messageId → 零成功 ACK、零 sent mark、SQLite 停留 PENDING）；
- **14 类回归（本机 Windows）**：**75/0/0/0**（JDBC 类 0 run 非 skip），与 §16.7 冻结计数完全一致；
- **§16.6 静态禁止扫描**：六个新文件 `/telemetry/`、`$queue`、`$share`、broad wildcard、拒绝审计关键词零命中；`git diff --check` 通过；未修改任何生产 Java/现有测试/POM/SQL/配置。

### 18.2 受阻项（§16.7 三段 Docker 禁网验收 NOT RUN）

本机 Docker（Linux 29.7.2）+ 冻结镜像 `maven:3.9.16-amazoncorretto-17-alpine`（ID `53215f45dda1` 与 §16.4 一致）可用，但容器内 Maven 依赖解析受阻：

1. 项目 POM 声明 `huaweicloud` 仓库，本机 `~/.m2` 构件的 `_remote.repositories` 元数据不含该来源，`-o` 离线模式拒绝解析；
2. 改用容器内 settings.xml 把 `*` 镜像到 `file:///root/.m2/repository` 后，`bcpkix-jdk18on-1.80.pom` 的范围依赖 `bcutil-jdk18on:[1.80,1.81)` 解析失败——本机仓库 `bcutil-jdk18on/1.80/` 缺 JAR（已从 central 补齐 JAR + 裸 `maven-metadata.xml`，并清理 `resolver-status.properties` 陈旧错误缓存），第四次尝试进行中被用户中止；
3. 已清理全部 `lc03-04a-*` 容器，无残留。

**评估**：这是本机 Maven 仓库元数据与容器隔离解析的环境性问题，不是测试/代码缺陷（同命令本机全绿）。§16.5 的 `LC03_M2_REPOSITORY`（仓库外独立 Maven repository）正是为此设计——用完整独立仓库（曾在联网容器内执行过 `mvn dependency:go-offline` 的）即可规避本机元数据问题。

### 18.3 下一次续作入口

1. 准备仓库外 `LC03_M2_REPOSITORY`：联网容器内对 DEVICE 执行 `mvn dependency:go-offline`（或等价 populate），确保范围版本元数据完整；
2. 重跑 §16.7 三段命令（direct 6、regression 75、test-compile 28-reactor），收集完整输出；
3. 收工复算九组 manifest（用 §18.1 的 locale 排序规则）+ §16.6 扫描 + 零残留；
4. 交 Sol 独立复核（含对本轮六个新文件代码审阅与 §16.2 逐条核对），转 `LC03-04A COMPLETE / SOL-ACCEPTED`。

六个新文件未提交（工作区），生产代码零改动；LC03-04B 仍 NOT-AUTHORIZED。

## 19. LC03-04A 第三次暂停检查点（2026-08-28，环境性受阻）

### 19.1 §18.3 续作步骤执行结果

1. **外部 M2 仓库就绪**：`~/lc03-m2-repo`（~750MB）由联网容器 `dependency:go-offline` 填充；随后补齐 go-offline 遗漏的三个关键构件（均从 central 直取）：`bcutil-jdk18on-1.80.jar`（范围 `[1.80,1.81)` 解析）、`bcprov-jdk18on-1.80.2.jar`、`surefire-junit-platform-3.0.0-M5`（jar+pom）、`surefire-providers-3.0.0-M5.pom`、`junit-platform-launcher-1.8.2`；并为 bouncycastle 三构件补裸 `maven-metadata.xml`、清理 `resolver-status.properties` 陈旧错误缓存、统一 `_remote.repositories` 来源标记。本机 settings.xml 的 `localRepository=D:\tools\repository` 是 §18.2 挂载冲突根源，容器内一律用注入的 file-mirror settings 绕开；
2. **容器内运行环境两项偏离（已记录，未改 §16.7 命令语义）**：a) SQLite JDBC 需解压 native `.so` 到可执行目录，本 Docker Desktop 对 tmpfs/shm 一律 noexec、gVisor 对 Windows bind-mount 的 .so 间歇拒绝 dlopen——最终以 `-Djava.io.tmpdir=/root/lc03-tmp`（容器 overlay FS，容器内 mkdir 预建）落 SQLite 临时文件后测试可运行；b) 无网络下 `-o` 因 `_remote.repositories` 元数据拒绝解析，改用 `file:///root/.m2/repository` 镜像 settings（禁网语义不变）；
3. **§16.7 第一段（direct）容器结果：稳定 5/6**——`TelemetryAckRestartReconciliationTest` 3/3 全绿；`TelemetryAckCombinedE2ETest` 中 e2e02、negative 全绿，**唯 e2e01 在容器内超时**（本地持续 6/6）。四轮复现一致。故障特征：e2e01 期间 Vert.x eventloop-0 被阻塞约 2.2–3.6 秒（BlockedThreadChecker 记录），吃掉 §16.2 冻结的 5 秒 eventually 预算后 `awaitSqliteStatus` 超时；e2e02 起全部正常。已尝试两种缓解均无效：(a) fixture 启动时 eventloop tick 预热；(b) 首次发布全路径预热（真实 server→collector 投递一条未知 messageId 探针 + SQLite writer 线程 append 探针，均无场景副作用）。第三种缓解（context-hop 发布）亦未改变。surefire 报告计数 2 而非 3 的显示差异源于 surefire 3.0.0-M5 的 RunListenerAdapter 在首测失败时类加载边界缺陷（PojoStackTraceWriter CNFE）吞掉报告行——测试本体确实执行且失败于 TimeoutException，非未执行；
4. **§16.7 第二/三段未执行**：第一段未达 6/0/0/0 判定，按 §16.7"任一失败立即停止"不再续跑；
5. 工作区并行提示：G2-03R1 TCP-JSON 会话的未提交文件（`codec/tcp`、`deviceevent/**`、`g2-03r1a` 资源）与 LC03 无关，本轮一次容器编译失败（文件半写竞态）后自行恢复，未触碰。

### 19.2 Sol 判定与请求决策

- **非测试/生产缺陷**：同命令本机 Windows 持续 6/6；容器内 e2e02～05、negative 均过；e2e01 失败模式为容器专属的首次场景 eventloop 停顿（gVisor/Docker Desktop 环境），两次语义无关的预热缓解无效，根因不在被测合同；
- **不篡改结果**：按 §16.7 纪律，5/6 不得报告为通过；§16.2 的 5 秒 eventually 为冻结值，Sol 不擅自放宽；
- **两条可行路径，请决策所有者选择**：
  1. **重冻结容器预算**：Sol 出 1.1.2 修订——容器内（且仅容器内）eventually 上限调至 10 秒（本地保持 5 秒），理由为环境冷启动不属于被测语义；随后重跑三段；
  2. **换执行宿主**：在性能正常的 Linux CI/主机按 §16.7 原命令重跑（现有外部 M2 仓库可复用）；
- 六个 04A 文件保持未提交（含本轮新增的预热探针与 context-hop，均无场景副作用）；生产代码零改动；九组 manifest 本轮复算仍全 OK。

### 19.3 续作入口

决策所有者选定路径后：路径 1 → Sol 修订任务单后重跑 §16.7 三段；路径 2 → 在目标宿主执行 §16.7 原命令。达成 6/0/0/0 后继续第二段（14 类 75/0/0/0）、第三段（28-reactor test-compile）、收工复算与 §16.5 start/end 比对。
