# 电力物模型写链灰度前只读预检记录

> 执行日期：2026-08-10
> 目标：本地 Docker / `postgres-server / iot-device20`
> 基线：平台功能计划 1.4.0、EasyAIoT 项目开发宪法 1.5.0
> 操作性质：只读；未创建 Topic、未启动服务、未修改容器、未注入密钥、未开启开关

## 1. 运行状态

- `postgres-server`：healthy。
- `kafka-server`：healthy。
- 当前 Docker 中不存在运行中的 `iot-device` 容器；`DEVICE/docker-compose.yml ps -a` 无服务实例。
- `zlmediakit-server` 显示 unhealthy，但不在本次物模型数据库/Kafka 写链的直接依赖路径内，作为独立运维问题保留，不据此放宽本窗口门禁。

## 2. Kafka 事实

- Topic 列表中不存在 `power-model-release-v1` 和 `power-model-release-v1-dlq`。
- 消费组列表为空，不存在 `iot-device-power-model-release`，因此没有可读取的消费延迟或消费者健康证据。
- `kafka-server` 实际环境为 `KAFKA_AUTO_CREATE_TOPICS_ENABLE=true`。不得把自动建 Topic 当作灰度前置完成：ADR-014 的候选配置为 6 分区、30 天保留期，而自动创建可能采用 broker 默认值；分区数、保留期和压测冻结仍为 OPEN。

## 3. PostgreSQL 事实

- `schema_migration_history` 中 M05、M15、M16、V001～V007 均为 `SUCCEEDED`；V007 SHA-256 为 `6590d6daa33e6e3382f17b1ef1ced0ed854c5322857062617d2b77c621e38685`。
- 初始积压：`power_model_release_outbox=0`、`power_model_event_inbox=0`、`iot_collector_config_release=0`、`collector_workload_binding_projection=0`。
- `invalid_indexes=0`。
- 既有业务基线：`product=4 / device=4 / product_properties=17`。

## 4. 结论

数据库前置条件通过，但运行链前置条件不通过。当前至少存在以下阻断项：

1. `iot-device` 未部署/未运行，无法验证 standard/full profile、capability、四处理器装配、健康探针和实际开关状态；
2. 主 Topic、DLQ Topic 尚未按审批后的分区/保留参数显式创建；
3. 消费组尚不存在，无法提供消费者在线与零 lag 证据；
4. 运行时 HMAC secret 尚未通过密钥设施注入（仓库仅保留空默认，预检不读取或记录密钥值）；
5. owner 尚未对本次实际灰度窗口、Topic 参数和单租户/单 workload canary 单独授权。
6. 当前 `DEVICE/.env` 未固化 `EASYAIOT_CAPABILITY_PROFILE` 和
   `EASYAIOT_CAPABILITY_MANIFEST_LOCATION`；若直接使用 Compose 默认值，将得到
   `profile=full` 但 manifest 为空，`CapabilityService` 会按安全默认关闭电力能力。

因此结论为 `BLOCKED_FOR_ACTIVATION / WAITING_OWNER_APPROVAL_AND_RUNTIME_READINESS`。本记录不构成启用授权。

## 5. 可复跑预检

已新增只读脚本 `.scripts/docker/power_model_activation_preflight.ps1`。脚本使用稳定退出码：

- `0`：全部门禁通过；
- `1`：工具或只读查询错误；
- `2`：存在未满足门禁。

Topic 参数与目标档位没有默认裁定，必须通过参数显式传入；脚本只检查、不创建、不启动、
不改配置，并且密钥只报告是否达到 32 UTF-8 字节，不输出值、长度或摘要。

2026-08-10 使用 `standard / 6 partitions / replicationFactor=1 / retention=30d` 候选参数执行
baseline 只读回归（参数仅用于验证脚本，不代表审批）：PowerShell 解析 PASS，脚本稳定返回退出码
`2`；数据库与中间件健康项 PASS，准确报告 `DEVICE/.env` capability 配置缺失、`iot-device`
不存在、主/DLQ Topic 不存在及消费组不存在。回归后数据库四类积压仍为 0，未产生运行状态变更。

## 6. 运行准备定参补充

- 当前保存档位：`full`；宿主机物理内存约 31.5 GiB，D 盘可用约 199.7 GiB。
- Kafka broker：仅 id=1 一个实例，因此本地复制因子最大只能取 1；该值不代表生产高可用基线。
- DEVICE 依赖链端口 48099/48082/48083 均空闲，`easyaiot-network` 已存在。
- `iot-device` 不能单独部署：Compose 健康依赖为 `iot-device → iot-infra → iot-system`。
- 三个对应 `latest` 本地镜像均缺失，运行准备必须包含受控构建；构建前不修改运行状态。

最终建议为本地 `full`、主/DLQ 各 6 分区、复制因子 1、显式 `retention.ms=2592000000`
（30 天），并把授权边界限制在 baseline：同步 capability、构建/启动三服务依赖链、创建 Topic、
只读验收；三项写开关继续为 false，不注入 secret，不执行 canary。

## 7. baseline 运行准备执行结果

owner 于 2026-08-10 在完整定参和禁止范围后回复“继续下一步”，登记批准号
`USER-APPROVAL-20260810-POWER-MODEL-BASELINE`。执行严格停在 baseline：

1. `DEVICE/.env` 已同步 `profile=full` 与
   `file:/opt/easyaiot/capabilities/electric-full.json`，API/release port/events 三开关均显式 false，
   secret 为空；Compose 渲染只输出布尔状态复核 PASS。
2. 显式创建 `power-model-release-v1` 与 `power-model-release-v1-dlq`；两者均为 6 分区、
   replication factor=1、动态 `retention.ms=2592000000`，6 个分区 leader/ISR 均为 broker 1。
3. Maven 第一次命令在编译前因 PowerShell 未引用 `-D` 参数失败；第二次按默认 source=8
   编译到 `iot-device-biz` 时被现有 Java 文本块正确拒绝。最终显式 source/target=17 后
   35 模块反应堆全部 SUCCESS（测试沿用此前 26/26，本次 package 跳过测试）。
4. 可执行 Jar：
   - `iot-system-biz.jar`：166268276 bytes，SHA-256
     `c9c24123e19d83630a0ceda291f5af35b09bf990d0c1fc469cabeeaef9bb186c`；
   - `iot-infra-biz.jar`：174403720 bytes，SHA-256
     `590bf26a6096268b96c33716a74588672feddcff9ade4ea9f285815b898e74e4`；
   - `iot-device-biz.jar`：279109133 bytes，SHA-256
     `3ed37b8cfd9881b640ce069b07beee3ebac4087dc76e8248612149d2b50c9da1`。
5. 本地镜像构建成功：system
   `sha256:6b2c077ec20788bd36f2826824c1cedfd4f171bf7d0b8ae0024e3971d5ffb71e`、infra
   `sha256:fd182ef841be1ee8818128deb84486579b1254348d375eea4137ca70c865d0bc`、device
   `sha256:e956b7b3aba6e9c2a8519f1ac3a0538897b00ffaca83d58857a73a1dbc62787b`。
6. Compose 按 `iot-system → iot-infra → iot-device` 健康依赖启动；三个容器最终均 healthy，
   容器内 actuator 均返回 `UP`。device 内 full manifest 可读且包含 `power.device.model`；
   日志存在 Tomcat 48083 与 `Started DeviceServerApplication`，无 `POWER_MODEL_`、
   `APPLICATION FAILED` 或 ERROR 标记。
7. 最终 baseline 预检退出码 0，全部检查 PASS：V001～V007/hash、invalid index=0、
   数据库 Outbox/Inbox/release/projection=`0/0/0/0`、业务基线=`4/4/17`、Topic 实际参数匹配；
   events=false，因此消费组“不要求存在”且当前不存在。

结论：`BASELINE_READY / NOT_ACTIVATED`。本执行未开启写开关、未注入 secret、未执行 canary、
未调用 NODE、未执行 DDL；进入 release-port 阶段仍需独立授权。

## 8. 阶段 1：release port

owner 在 M1 复盘后回复“继续下一步”，登记
`USER-APPROVAL-20260810-POWER-MODEL-RELEASE-PORT`。执行前 baseline 预检退出码 0、全项 PASS；
随后只把 `EASYAIOT_POWER_MODEL_COLLECTOR_RELEASE_PORT_ENABLED` 改为 true，并使用
`--no-deps --force-recreate iot-device` 仅重建 device 容器，未重启 system/infra。

验收结果：

- Compose 渲染与容器实际环境均为 API=false、releasePort=true、events=false、secret 未配置；
- `iot-device` healthy，Tomcat 48083 与 `Started DeviceServerApplication` 启动标记存在；
- 最近 500 行日志无 `POWER_MODEL_`、`APPLICATION FAILED` 或 ERROR 标记；
- 阶段 1 预检退出码 0、全项 PASS；V001～V007/hash、invalid index=0、Topic 参数均不变；
- Outbox/Inbox/release/projection=`0/0/0/0`，业务基线仍为 `4/4/17`；
- events=false，消费组在本阶段不要求存在且当前仍不存在。

结论：`STAGE_1_RELEASE_PORT_READY / API_NOT_ACTIVATED`。release port 是被动协调端口，本阶段未产生
业务写入；阶段 2 event pipeline 仍需独立授权。

## 9. 阶段 2：event pipeline

owner 回复“继续下一步”，登记
`USER-APPROVAL-20260810-POWER-MODEL-EVENT-PIPELINE`。范围仅为 release port=true、API=false、
secret 空时开启 events，并验证四处理器、消费组、lag、DLQ 和数据库积压。

执行证据：

1. 新增四类 V1 handler 启动完整性门禁；激活组合、完整性门禁和处理器合同共 24/24 PASS。
2. Java 17 兼容 package SUCCESS；device JAR SHA-256
   `49469f2c7a19e5797a58d61ce4c38dd7203769808358c8e4bfb07853666d5c04`，镜像 ID
   `sha256:4fa869302238d1b931a4fa7b69a0ceaa417a9500952ba6756ec499e088de705b`。
3. 首次开启被消费组门禁阻断。日志显示 listener 已订阅，但 bootstrap 错误回退到
   `localhost:9092`。确认数据库四类积压为 0 后立即关闭 events 并只重建 device；阶段 1
   预检退出码 0、全项恢复。
4. `iot-device` Compose 补齐 `SPRING_KAFKA_BOOTSTRAP_SERVERS=Kafka:9092`，静态部署合同与
   handler 门禁回归 11/11 PASS；随后按同一授权重试。
5. 最终 events 预检退出码 0、全项 PASS：API=false、release=true、events=true、secret 空；
   device healthy；消费组在线，6 分区均分配到唯一消费者且 lag=0；DLQ 六分区 offset 均为 0。
6. V001～V007/hash、Topic 6 分区/RF1/30d、invalid index、业务基线 `4/4/17` 均无漂移；
   Outbox/Inbox/release/projection=`0/0/0/0`。

结论：`STAGE_2_EVENT_PIPELINE_READY / API_NOT_ACTIVATED`。阶段 3 未获授权；未注入 secret、
未调用 binding API、未执行 canary、NODE 或 DDL。
