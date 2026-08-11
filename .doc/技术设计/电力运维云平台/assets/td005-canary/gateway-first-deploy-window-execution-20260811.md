# TD-005 电力 API 网关首次部署窗口执行证据（2026-08-11）

> 双基线：平台功能计划 1.4.0 / EasyAIoT 项目开发宪法 1.5.0
> 批准：`USER-APPROVAL-20260811-TD005-POWER-API-GATEWAY-FIRST-DEPLOY`
> 结果：PASS；删除式回退未触发

## 1. 执行边界

首次预检确认不存在 `iot-gateway` 容器及 `iot-gateway:latest` 镜像。经修正批准后，仅允许从当前冻结
gateway JAR 构建镜像，并以 `--no-deps` 首次创建 `iot-gateway`。禁止重建其他容器、修改 API 开关、
Secret、角色、数据库、Topic 或 Nacos；禁止获取 token、调用业务 API或写 Canary。

## 2. 部署证据

- 目标 JAR 路由块：`device-power-model-api`，`Path=/api/v1/power/**`，`uri=lb://device-server`；目标块
  不含 StripPrefix 或 RewritePath；
- 宿主构建 JAR SHA-256：`2A91097BD2AC616E5CD82A6A15D55901C07B7EBB5E0CDB31CD153B615C96992E`；
- 容器 `/app/app.jar` SHA-256 与宿主一致；
- 网关容器 ID：`dc358ab7d385ee28f0faf9768615980adb30643aed0c6f1d7582e51e2fc876f6`；
- 网关镜像 ID：`sha256:e68504341ffcb6f863bcea243069ae05cc72322c90bb99823e935b6d2a3268f7`；
- 启动时间：`2026-08-11T04:37:56.189335982Z`；最终状态 `running / healthy`。

## 3. 非变更与只读验收

- `iot-device`、`iot-system`、`iot-infra`、`postgres-server`、`kafka-server` 的容器 ID和启动时间与部署前一致；
- template-api 阶段只读预检 17 项全部 PASS：template=true、binding=false、release/events=true，Secret
  仍来自 Config Tree，V001～V007=7/7，数据库积压 0/0/0/0，业务基线 4/4/17，Kafka 6 分区、RF1、
  30 天保留且消费组 lag=0；
- 冻结只读角色校验仅返回 tenant 122 / role 111 的 3900～3902 三行，3903～3906 为 0；
- 冻结 tenant 只读校验返回 14 类事实 residual_rows=0；
- 未获取或读取 token，未访问 `/api/v1/power/**` 或其他业务 API，未写 Canary 数据。

## 4. 后续门禁

网关路由部署不构成 Canary 写入授权。下一步必须由 `user 113 / aoteman` 正常登录取得未过期短时令牌，
并在另一个独立批准窗口内执行 identity→draft→validate→publish 单次 Canary 写入。
