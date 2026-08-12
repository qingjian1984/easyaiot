# TD-005 iot-device 直接文件 Provider 部署执行记录（2026-08-12）

> 日期：2026-08-12
> 双基线：平台功能计划 1.4.0 / EasyAIoT 项目开发宪法 1.5.0
> 批准：`USER-APPROVAL-20260812-TD005-IOT-DEVICE-DIRECT-FILE-PROVIDER-DEPLOY`
> 结论：`DEPLOY_PASS / HEALTHY / DIRECT_FILE_PROVIDER_VERIFIED / ROUTE_REGISTERED`

## 1. 部署前门禁

- 候选 JAR `DEVICE/target/jars/iot-device-biz.jar`，279,673,823 字节，SHA-256
  `9dd19363303769c27c7a98dff344fa37e50a5c5a1f639a6d8118797e603ff421`（1.0.54 候选）；
- 镜像内 `/app/app.jar` SHA-256 精确匹配候选；JAR 含 `PowerModelIdempotencySecretProvider` +
  `PowerModelTemplateController` + 4 写服务全部类（unzip -l 验证）；
- 仓库外 Secret 64 字节（Base64，≥32 UTF-8），路径 `D:\working\laoluopro\workspace\easyaiot-secrets\power-model-hmac.secret`；
- 运行开关保持 `full`、`template=true`、`binding=false`、`release=true`、`events=true`；
- `iot-device20` 仓库外 custom-format 备份 447,615 字节，SHA-256
  `92dfc45bc43dc5a2624d131a4ccb27fb05eaeeaee39e55241b9c3ebc67bba77a`（与 1.0.48 前备份同大小，状态干净无 Canary 残留）；
- 旧镜像 `4fa869302238`（1.0.53 回退后）保留回退标签 `rollback-td005-direct-file-provider-predeploy-20260812`；
- 新 compose 组合（base + `power-model-secret.yml` 直接文件 provider + `power-model-template-api.yml`）`config --quiet` PASS。

## 2. 执行

- 构建新镜像 `iot-module-device-biz:latest` = `sha256:3860dce5da25eaf6b7d67c401e573b0db63242c2278c9328cc4d2018308ba35a`；
- `docker compose up -d --no-deps --force-recreate iot-device` 重建；
- 旧容器 `805b39aa4917`（legacy-rollback 配置，挂载 `/run/secrets/easyaiot.power-model.idempotency-hmac-secret-file-content`）
  → 新容器 `52d798735144b0753d1da79a03fe76c2cc16d5d169ca83acbfa1a422b2ad2036`（直接文件 provider，挂载 `/run/secrets/easyaiot_power_model_hmac`）；
- Secret 挂载点从含 `.` 的 legacy target 切换为不含 `.` 的下划线 target，彻底切断 Config Tree 文件名映射依赖。

## 3. 验收（全 PASS）

- 新容器 `52d798735144`，镜像 `3860dce5da25`，**healthy**（~40s 内），`restartCount=0`；
- 启动日志**无** `POWER_MODEL_IDEMPOTENCY_SECRET_INVALID`（直接文件 provider 成功读取 64 字节 Secret）——
  **1.0.51/1.0.52/1.0.53 三次 Config Tree 失败的根因正式解除**；
- 启动日志**无** `POWER_MODEL_ACTIVATION_INCOMPLETE`（`template=true` + release + events 组合有效）；
- `Started DeviceServerApplication in 22.87 seconds`，Tomcat started on port 48083；
- 路由探针 `POST /api/v1/power/model-templates` → **HTTP 400**（1.0.48 是 404；400 证明 `PowerModelTemplateController`
  路由已注册，1.0.48 identity 404 根因正式解除）；
- 数据库（`iot-device20`）：业务 `4/4/17`、迁移 `V001～V007` = 7 SUCCEEDED、积压 `0/0/0/0`
  （outbox/inbox/release/projection），无漂移；
- 其他 6 容器（`iot-system` / `iot-infra` / `postgres-server` / `kafka-server` / `iot-gateway` / `web-service`）
  ID 与启动时间全部不变。

## 4. 边界与后续

- 本窗口成功**不构成**认证窗口或 Canary 写入批准；
- 仓库外 `iot-device20` 备份（SHA-256 `92dfc45b...`）保留；
- 1.0.51 / 1.0.52 / 1.0.53 三个无标签候选镜像（各 4.18 GB）+ 历史回退标签待独立清理窗口；
- 下一步严格顺序（各自独立批准）：
  1. owner 独立批准 harness REAUTH-V3 认证（用户本人在 `/td005-auth-harness` 输入 tenant 123 / user 132 凭据，rememberMe=false）；
  2. identity→draft→validate→publish 单次 Canary 模板写入（冻结资产 `canary-meter-123`）；
  3. 收尾：删除临时 `.td005-auth-allowlist.js`，提交 provider 代码 + 本执行证据 + 进度入口续作。
