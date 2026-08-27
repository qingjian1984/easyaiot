# M1-LC-03：成功 ACK V1 与重启对账任务单

> 状态：In Progress（LC03-01、LC03-02 COMPLETE / SOL-ACCEPTED）
> 版本：1.0.3
> 日期：2026-08-27
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
| LC03-03 | V012 候选、center dispatch repository、即时 ACK 与 10 秒扫描 | SQL 候选、JDBC repository、publisher/service/scanner | FROZEN / NOT-YET-AUTHORIZED；临时 PG 另授权 |
| LC03-04 | collector↔EMQX↔center↔PG↔SQLite 组合 E2E 与重启故障点 | 假服务 fixture、真实隔离 broker/PG、restart reconciliation | LOCKED，待 LC03-02/03 验收 |
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
- `.doc/技术设计/电力运维云平台/assets/td003-migration/V012__telemetry_inbox_ack_delivery.sql`（新增候选）；
- `.doc/技术设计/电力运维云平台/assets/td003-migration/U012__telemetry_inbox_ack_delivery.sql`（新增候选）；
- `.scripts/postgresql/td003-migration/tests/lc03_v012_contract.sh`（新增隔离合同，不接共享 runner）；
- 对应直接/真实 JDBC 测试。

### 7.4 LC03-04～05

- 只允许新增 `iot-sink-biz/src/test` 下 LC03 组合 fixture/E2E；
- 只允许新增 `.scripts/mqtt/tests/lc03_success_ack_contract.sh` 或任务单后续由 Sol 指定的等价隔离脚本；
- 本任务单、[技术设计索引](./README.md) 与 [M1 续作入口](./M1-SDD进度与续作入口.md) 的证据回填。

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

## 9. 验收命令候选

每包实施前由 Sol 把实际类名和当前保护摘要回填为最终命令；Luna 不得以找不到候选类为由自行改验收口径。当前冻结的最小命令形态为：

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

LC03-01 已由 GPT-5.6 Luna Max 在冻结白名单内实现，并经 GPT-5.6 Sol 独立协议审查、边界扫描和直接测试复验后接受。LC03-02～05 尚未获得实现、DDL 或运行期执行授权。

下一步须由决策所有者独立授权：

> GPT-5.6 Luna Max 执行 M1-LC-03 `LC03-02` collector 精确订阅、启动/APPLIED 门禁与成功 ACK 关联应用。

LC03-03 也已具备冻结边界，但仍需决策所有者单独授权；LC03-04～05 保持 LOCKED。正式 V009/V012 落库、生产 MQTT broker/ACL/TLS 激活、资源压测、Linux PTY/锁互操作、Windows 发布资格、7 天稳定性和现场验证均继续 OPEN / NOT APPROVED。

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
