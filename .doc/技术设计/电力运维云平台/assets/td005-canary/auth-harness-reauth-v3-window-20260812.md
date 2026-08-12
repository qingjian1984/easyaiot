# TD-005 Canary REAUTH-V3 认证窗口（2026-08-12）

> 双基线：平台功能计划 1.4.0 / EasyAIoT 项目开发宪法 1.5.0
> 依据：TD-005 1.0.55（直接文件 provider 部署成功）
> 状态：EXECUTED / VERIFIED
> 登录对象：tenant 123 `codex测试` / user 132 `aotemane` / role 112
> harness URL：http://localhost:8888/td005-auth-harness

## 0. 预检（全 PASS，2026-08-12）

- harness 路由 `GET /td005-auth-harness` → HTTP 200（web-service 含 harness + 网络门禁）；
- `iot-device` healthy（1.0.55 直接文件 provider 部署成功，容器 `52d798735144`）；
- `iot-device20` tenant 123 业务 `0/0/0/0`（product/template/outbox/inbox，无 Canary 残留）；
- `ruoyi-vue-pro20` user 132 active tokens `0/0`（access/refresh，认证前干净）；
- `ruoyi-vue-pro20` role 112 权限 `3/0`（仅 3900～3902，禁止 3903～3906）。

## 1. 认证步骤（**用户本人操作浏览器**）

1. 在本机独立 Chrome 打开 `http://localhost:8888/td005-auth-harness`；
2. 输入：
   - 租户名：`codex测试`（tenant 123）
   - 用户名：`aotemane`（user 132）
   - 密码：现有凭据（用户本人掌握，不由自动化输入）
3. 完成滑块验证码；
4. 点击「开始认证（rememberMe=false）」；
5. 页面显示「认证成功（已停止）」、四步全绿后，**直接关闭页签**（不要导航至 Dashboard 或其他页面）。

## 2. 约束（harness 内置强制）

- `rememberMe` 强制 false（sessionStorage，30 分钟有效）；
- harness 单次锁定：首次租户查询后永久锁定，**一次认证尝试**，成功/失败/验证码关闭后均不解锁；
- 只允许 5 个认证 API：`tenant/get-id-by-name`、`captcha/get`、`captcha/check`、`auth/login`、`auth/get-permission-info`；
- 页面内置网络门禁阻断 dict/video/电力及其他业务 API；
- 不导出、不显示、不复制 token 值。

## 3. 验收（认证后只读核验，不取 token 值）

- `ruoyi-vue-pro20` user 132 active access = 1、active refresh = 1（新增各 1，未过期）；
- `iot-device20` tenant 123 业务仍 `0/0/0/0`（认证不写业务）；
- role 112 权限仍 `3/0`；
- iot-device / web-service / iot-gateway / iot-system 容器不变；
- harness 页面网络记录仅出现 5 个批准认证 API。

## 4. 失效条件

- 认证失败一次即锁定（harness 设计），不重试、不重置密码；
- 产生 token > 1 组（重复认证）→ 立即停止，多余 token 进独立处置窗口；
- 网络记录出现非认证 API → 停止。

## 5. 后续门禁（独立批准）

认证验收后，申请 `identity→draft→validate→publish` 单次 Canary 模板写入（冻结资产 `canary-meter-123`），仍需 owner 独立批准；本认证窗口不构成 Canary 写入授权。

## 6. 执行结果（2026-08-12）

- 用户本人在独立 Chrome 完成 harness 认证，四步全绿（tenant / captcha / login / permission-info），页面停在 harness 未进 Dashboard；
- `ruoyi-vue-pro20` user 132 新增 active access = 1（ID 6120，expires 2026-08-12 00:55:45，未过期）、active refresh = 1（ID 6119，expires 2026-08-13 00:25:45，未过期）；未查询 token 值；
- `iot-device20` tenant 123 业务仍 `0/0/0/0`（认证不写业务）；role 112 权限仍 `3/0`；
- 容器不变：iot-device `52d798735144`、web-service `f96616cd0756`、iot-gateway `dc358ab7d385`、iot-system `54b6cd52cff6`、postgres-server `8484916056b6`、kafka-server `2ddb63448717` ID 与启动时间不变；
- 认证窗口成功**不构成** Canary 写入批准；下一步申请 identity→draft→validate→publish 单次 Canary（独立批准）。
