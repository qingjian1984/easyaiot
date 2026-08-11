# TD-005 Canary WEB 首次部署窗口执行证据（2026-08-11）

> 双基线：平台功能计划 1.4.0 / EasyAIoT 项目开发宪法 1.5.0
> 批准：`USER-APPROVAL-20260811-TD005-CANARY-WEB-FIRST-DEPLOY`
> 结果：PASS；删除式回退未触发

## 1. 执行边界

执行前不存在 `web-service` 容器和 `web-service:latest` 镜像。仅允许以 full 档位从当前提交构建镜像，
并以 `--no-deps` 首次创建 WEB；禁止登录、token、业务 API、Canary 及其他运行配置变更。

## 2. 部署证据

- 构建参数：`VITE_GLOB_DEPLOY_PROFILE=full`；构建日志确认生产环境追加值为 full；
- Vite build 与 postBuild 均成功；
- 镜像 ID：`sha256:04e02c37984302f960de097e259dbd7cce61e2033aa06106ac293528d21bb645`；
- 容器 ID：`e9b9afad06f70f17740799b8d78c26f920bc923eb8ce3a6d5d06fbbfe1295fb6`；
- 启动时间：`2026-08-11T05:40:27.445116394Z`；最终 `running / healthy`；
- 端口映射：宿主 8888 → 容器 80。

## 3. 非变更与只读验收

- `iot-gateway`、`iot-system`、`iot-device`、`postgres-server`、`kafka-server` 的容器 ID和启动时间不变；
- template-api 阶段 17 项全部 PASS；
- user 113 未过期 access token 数量仍为 0；role 111 允许权限 3、禁止权限 0；
- tenant 122 的 14 类事实 `residual_rows=0`；
- 未打开登录页、未登录、未读取密码或 token、未调用任何 API、未写 Canary。

## 4. 后续门禁

WEB 部署不构成登录或 Canary 写入授权。下一步须先完成独立浏览器认证窗口，再申请单次隔离模板 Canary
写入窗口。
