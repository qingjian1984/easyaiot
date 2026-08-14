# M1-LC-01：Inbox 接收结果合同修复任务单

> 文档版本：1.0.0  
> 状态：Implemented / Verified-Local  
> 冻结日期：2026-08-13  
> 架构负责人：GPT-5.6 Sol  
> 实现与测试执行者：GPT-5.6 Sol（Luna Max 当前运行时不可用，未静默替换模型）  
> 适用档位：`standard` / `full`；`mini` 不启用电力遥测可靠链路  
> 上游基线：[平台功能计划 1.5.0](../../架构设计/平台功能计划.md)、[项目开发宪法 1.6.0](../../开发规范/EasyAIoT项目开发宪法.md)、[TD-003 1.0.1](./TD-003-遥测Inbox-ACK与时序投影.md)

## 1. 任务目标

修复 `JdbcTelemetryInbox.receiveEnvelopes(...)` 已识别新增、重复和碰撞，却始终返回 `Received` 的合同错误，使调用方可以按输入消息逐条、无歧义地获得以下内部接收事实：

- `ACCEPTED_DURABLE`：Inbox 新记录已经可靠提交；
- `DUPLICATE`：同租户、同 messageId 的既有记录与本次输入完全一致；
- `MESSAGE_ID_COLLISION`：同租户、同 messageId 的既有记录与本次输入的 hash、requestId、wire messageId 或设备身份至少一项不同。

本任务只修复 Inbox 端口返回合同和 JDBC 判定，不发送 MQTT ACK，不新增拒绝审计，不修改数据库 Schema。

## 2. 已确认的当前事实

1. `TelemetryInboxPort` 当前只有 `iot-sink` 内部调用方：`JdbcTelemetryInbox`、`CenterMqttInboxSubscriber` 和相关测试；没有跨服务消费者。
2. 方法签名当前为 `InboxReceiveResult receiveEnvelopes(List<InboxEnvelope> envelopes)`。
3. 现有 sealed 类型只有批次级 `Received`、`Duplicate`、`Collision`，无法表达混合批次。
4. `JdbcTelemetryInbox` 已收集三类 messageId，但返回时丢弃了 duplicate/collision 集合。
5. TD-003 要求重复消息返回原始持久化时间；碰撞不得覆盖原 payload，也不得创建第二条 Inbox 记录。

## 3. 冻结接口契约

### 3.1 方法签名

本任务 **MUST 保持** `TelemetryInboxPort` 方法签名不变：

```java
InboxReceiveResult receiveEnvelopes(List<InboxEnvelope> envelopes);
```

不得新增平行端口、重载方法或第二套结果事实。

### 3.2 `InboxReceiveResult` 结构

在现有 sealed interface 中新增 `Batch`，并新增逐消息 `Item` 与 `Status`。冻结的逻辑结构如下；实现可补充必要的 compact constructor 做不变量校验，但不得改变字段语义：

```java
public sealed interface InboxReceiveResult
        permits InboxReceiveResult.Batch,
                InboxReceiveResult.Received,
                InboxReceiveResult.Duplicate,
                InboxReceiveResult.Collision {

    record Batch(List<Item> items) implements InboxReceiveResult {}

    record Item(
            int inputIndex,
            String messageId,
            String requestId,
            Status status,
            Long persistedAtMs
    ) {}

    enum Status {
        ACCEPTED_DURABLE,
        DUPLICATE,
        MESSAGE_ID_COLLISION
    }

    /** @deprecated 兼容一个开发周期；新实现不得返回。 */
    @Deprecated record Received(List<String> messageIds) implements InboxReceiveResult {}

    /** @deprecated 兼容一个开发周期；新实现不得返回。 */
    @Deprecated record Duplicate(List<String> messageIds) implements InboxReceiveResult {}

    /** @deprecated 兼容一个开发周期；新实现不得返回。 */
    @Deprecated record Collision(List<String> messageIds) implements InboxReceiveResult {}
}
```

强制不变量：

- `Batch.items` MUST 做不可变防御性复制，且不得为 `null`；
- `items` MUST 与输入顺序一致，长度必须等于输入长度；
- `inputIndex` MUST 从 `0` 连续递增；
- `messageId`、`requestId`、`status` MUST 非空；
- `ACCEPTED_DURABLE` 和 `DUPLICATE` 的 `persistedAtMs` MUST 为首次 Inbox 提交的 `received_at_ms`，且非负；
- `MESSAGE_ID_COLLISION` 的 `persistedAtMs` MUST 为 `null`；
- 空输入返回 `Batch(List.of())`；传入 `null` 列表 MUST fail-fast，不能当作空批次。

### 3.3 兼容策略

- 旧 `Received`、`Duplicate`、`Collision` 类型保留一个开发周期并标记 `@Deprecated`，降低仓库内潜在未检索调用方的源代码风险；
- `JdbcTelemetryInbox` 从本任务起 MUST 只返回 `Batch`；
- `CenterMqttInboxSubscriber` 本任务只需保持编译兼容，不得在本任务中发送 ACK；
- 下一任务 `M1-LC-02` 接入 ACK 后，消费者只处理 `Batch/Item`；旧类型的最终删除必须另立兼容清理任务。

## 4. 幂等与碰撞判定

### 4.1 唯一作用域

唯一作用域保持 `(tenant_id, message_id)`，不得改为仅 messageId，也不得把 `content_sha256` 加入唯一键以允许同 messageId 多条记录。

### 4.2 完全重复

发生唯一键冲突后，只有既有记录与输入的下列字段全部一致，才返回 `DUPLICATE`：

- `content_sha256`
- `request_id`
- `message_id_wire`
- `site_code`
- `device_identification`
- `property_code`

tenantId 和 messageId 已由唯一作用域确定。比较必须使用数据库既有值，不能仅比较 hash。

### 4.3 碰撞

上述任一字段不同即返回 `MESSAGE_ID_COLLISION`。碰撞处理 MUST：

- 不覆盖既有 payload、hash、身份或时间；
- 不插入第二条 Inbox 记录；
- 不创建时序样本；
- 不返回 `DUPLICATE`；
- 记录脱敏 warn 日志，只允许 messageId 和稳定原因摘要，不打印 payload 或凭据。

拒绝审计和 `REJECTED_FINAL/4010` ACK 属于 `M1-LC-02/03`，本任务不得提前实现无审计 FINAL ACK。

## 5. 事务与失败边界

### 5.1 批次语义

- 每条输入使用独立数据库事务；一条 collision 不回滚同批次已成功的其他输入；
- 混合批次必须返回逐条结果，禁止以“最严重状态”覆盖整个批次；
- 处理顺序与输入顺序一致，不并行执行同一批次。

### 5.2 数据库失败

- 单条事务发生数据库异常时，MUST 回滚该条并向调用方抛出异常；不得伪造三种成功/终态结果；
- 方法因第 N 条异常而退出时，之前已提交的条目允许保留；调用方重试原批次时，它们必须稳定返回 `DUPLICATE`；
- 本任务不得引入 `PERSISTENCE_RETRYABLE` 作为 Inbox 成功结果；数据库异常到 MQTT retryable ACK 的映射归后续 ACK 任务。

## 6. 允许修改的文件

Luna Max 只可修改以下范围：

1. `DEVICE/iot-sink/iot-sink-api/src/main/java/com/basiclab/iot/sink/telemetry/inbox/InboxReceiveResult.java`
2. `DEVICE/iot-sink/iot-sink-api/src/main/java/com/basiclab/iot/sink/telemetry/inbox/TelemetryInboxPort.java`（仅 JavaDoc）
3. `DEVICE/iot-sink/iot-sink-biz/src/main/java/com/basiclab/iot/sink/telemetry/inbox/jdbc/JdbcTelemetryInbox.java`
4. `DEVICE/iot-sink/iot-sink-biz/src/test/java/com/basiclab/iot/sink/telemetry/inbox/jdbc/JdbcTelemetryInboxContractTest.java`
5. 如单元测试确有必要，可在同一测试包新增一个测试文件。

若实现必须超出此清单，Luna MUST 停止并交回 Sol 重新定界，不得自行扩展范围。

## 7. 明确禁止范围

本任务不得：

- 修改任何 DDL、V008 或 migration runner；
- 修改 `CenterMqttAckPublisher` 或实现 MQTT ACK；
- 新增拒绝审计表或审计写入；
- 修改 `TelemetryProjectionOrchestrator`；
- 修改 PostgreSQL/TDengine `TelemetryStore`；
- 修改 Envelope、Topic、QoS 或 capability manifest；
- 修改 AI、VIDEO、RTC、WEB、APP、VISUALIZE、NODE、RUNTIME；
- 格式化或提交当前工作区的无关用户改动；
- 访问现场设备、生产数据库或生产凭据。

## 8. 测试矩阵

Luna Max MUST 实现并通过以下合同测试：

| ID | 场景 | 预期 |
|---|---|---|
| LC01-01 | 空列表 | 返回空 `Batch` |
| LC01-02 | `null` 列表 | fail-fast |
| LC01-03 | 单条新消息 | `ACCEPTED_DURABLE`，返回首次 `received_at_ms` |
| LC01-04 | 同一对象第二次提交 | `DUPLICATE`，`persistedAtMs` 与首次完全相同 |
| LC01-05 | 同 ID、不同 hash | `MESSAGE_ID_COLLISION`，原记录不变，行数仍为 1 |
| LC01-06 | 同 ID/hash、不同 requestId | collision |
| LC01-07 | 同 ID/hash/requestId、不同 site | collision |
| LC01-08 | 同 ID/hash/requestId、不同 device | collision |
| LC01-09 | 同 ID/hash/requestId、不同 property | collision |
| LC01-10 | 混合批次：new/duplicate/collision | 结果数和顺序与输入一致，各自状态准确 |
| LC01-11 | 防御性复制 | 外部不能修改 `Batch.items` |
| LC01-12 | 数据库失败 | 不返回成功结果；该条无部分写入 |
| LC01-13 | 失败后重试 | 先前已提交项稳定转为 duplicate，不新增第二行 |
| LC01-14 | 跨租户相同 messageId | 各租户可独立接收，不互相泄露或碰撞 |

测试 fixture MUST 使用保留测试租户并在前后清理；不得输出数据库密码。现有“同 messageId 不同 hash 仍可写入第二个样本”的 Store 测试不属于本任务，禁止顺手修改。

## 9. 验证命令

最小验证：

```powershell
mvn -f DEVICE/pom.xml test `
  -pl iot-sink/iot-sink-biz -am `
  -Dtest=JdbcTelemetryInboxContractTest `
  -DfailIfNoTests=false `
  -Dmaven.test.skip=false
```

补充编译验证：

```powershell
mvn -f DEVICE/pom.xml -pl iot-sink/iot-sink-biz -am compile -DskipTests
```

如果本地 PostgreSQL 不可用，Luna MUST：

1. 完成不依赖外部服务的单元测试和编译；
2. 明确把 PostgreSQL 合同测试标为 `NOT_RUN_LOCAL_ENV`；
3. 不得把未运行描述为 PASS。

## 10. 完成定义

只有同时满足以下条件，`M1-LC-01` 才能转为 `Implemented / Verified-Local`：

- 冻结接口结构和不变量已实现；
- JDBC 实现只返回 `Batch`，混合结果不丢失；
- duplicate 比较覆盖冻结的六个字段；
- collision 不覆盖、不增行；
- 数据库异常不伪造成功；
- LC01-01～14 均有自动测试，其中依赖本地 PostgreSQL 的测试必须如实记录执行状态；
- 相关模块编译通过；
- 未修改禁止范围和用户已有无关工作区改动；
- 交付说明列出实际测试、未运行项、已知限制和下一任务 `M1-LC-02`。

## 11. 回滚策略

本任务无数据库和配置变更。若实现需要回滚：

1. 恢复 `JdbcTelemetryInbox` 返回旧结果的代码；
2. 保留或移除新增 `Batch/Item/Status` 均不会影响数据库事实；
3. 不需要数据迁移或清理；
4. 回滚后必须重新标记“混合批次结果丢失”为 OPEN，禁止继续接入 ACK。

## 12. Luna Max 执行提示

执行者不再进行架构选型，只按本任务单实现。遇到以下任一情况必须停止并返回 Sol：

- 需要修改允许清单以外的生产文件；
- 发现 DDL 与本合同无法兼容；
- 无法用逐消息独立事务同时满足顺序和幂等要求；
- 需要决定 ACK、审计、投影或 Store 的新语义；
- 现有用户改动与允许修改文件发生重叠。

## 13. 本次执行记录

- 已实现 `Batch/Item/Status` 结果合同、六字段重复判定、逐条 `REQUIRES_NEW` 事务、碰撞不覆盖/不增行和数据库异常透传。
- 纯契约测试：5/5 通过；数据库失败边界测试：1/1 通过；相关模块 `compile -DskipTests`：通过。
- JDBC PostgreSQL 合同测试：13/13 通过；LC01-12 数据库失败边界测试：1/1 通过；相关模块 `compile -DskipTests`：通过。
- 本次使用仓库既有本地 Docker PostgreSQL（`iot-device20`，仅启动 `PostgresSQL-init`/`PostgresSQL`）完成真实 JDBC 验证；测试前后仅清理保留测试租户 `999888777/999888778` 的数据。
- 未修改 DDL、ACK、审计、投影、Store 语义及工作区既有无关改动；下一任务为 `M1-LC-02`。
