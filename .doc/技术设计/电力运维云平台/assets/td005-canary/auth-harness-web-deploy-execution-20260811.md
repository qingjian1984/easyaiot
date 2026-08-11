# TD-005 认证-only harness WEB 部署执行证据（2026-08-11）

> 双基线：平台功能计划 1.4.0 / EasyAIoT 项目开发宪法 1.5.0
> 授权：`USER-APPROVAL-20260811-TD005-AUTH-HARNESS-WEB-DEPLOY`
> 结果：`EXECUTED / VERIFIED`
> 范围：仅构建并重建 `web-service`

## 1. 执行前基线

- `web-service` 容器：`179b8c02fea8`，状态 `healthy`，restartCount=0；
- 原镜像：`sha256:cca2c5c4df72206769a1157e63c7f7ea1319f1c063fe6fd60ed09cc57998a047`；
- 专用回退标签：`web-service:rollback-td005-auth-harness-predeploy-20260811`，精确指向原镜像；
- 其余容器 ID 已在执行前冻结；`zlmediakit-server` 在窗口前已为 `unhealthy`，不属于本窗口且未处置。

## 2. 构建与部署

- 仅使用 `WEB/docker-compose.yaml` + `WEB/docker-compose.td005-auth-harness.yml`；
- 构建参数精确为：
  - `VITE_GLOB_DEPLOY_PROFILE=full`；
  - `VITE_TD005_AUTH_HARNESS=true`；
- 仅执行 `docker compose ... build web-service`，构建耗时 90.3 秒并成功；
- 新镜像：`sha256:6789fb7c54fb480c848679f1786ff86c2165de555c40aed583b253857377c89e`；
- 仅执行 `docker compose ... up -d --no-deps web-service`；
- 新容器：`7f1b877fe479`，状态 `running/healthy`，restartCount=0。

## 3. 验证

- 运行容器静态文件中找到 `td005-auth-harness` 路由字符串；
- 运行容器静态文件中找到 `TD-005 Canary` 页面标识；
- 除 `web-service` 外全部容器 ID 与执行前一致；
- 原回退镜像与新运行镜像 ID 均再次核对通过；
- 未打开浏览器，未登录，未调用租户、验证码、认证、permission-info 或业务 API；
- 未修改数据库、API 开关、Secret、角色、Topic、Nacos 或 capability；
- 未重建其他容器，未写 Canary 数据。

## 4. 后续门禁

部署窗口至此关闭。下一步只能在新的精确批准下执行一次认证-only 浏览器窗口：由用户本人输入 tenant 123
`codex测试` / user 132 `aotemane` 的现有凭据和验证码，`rememberMe=false`；只允许租户查询、验证码、login
和 permission-info。失败一次即停止。该认证批准仍不构成 Canary 写入批准。
