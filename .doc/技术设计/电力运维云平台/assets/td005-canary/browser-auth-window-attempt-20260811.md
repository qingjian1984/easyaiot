# TD-005 Canary 浏览器认证窗口首次尝试证据（2026-08-11）

> 双基线：平台功能计划 1.4.0 / EasyAIoT 项目开发宪法 1.5.0
> 批准：`USER-APPROVAL-20260811-TD005-CANARY-BROWSER-AUTH`
> 结果：BLOCKED_BROWSER_CONTROL / NOT_EXECUTED

## 1. 阻断点

应用内浏览器控制在导航到 `http://localhost:8888` 前无法建立连接，主机拒绝访问连接初始化所需的用户配置
元数据。页面未打开，因此没有发生租户查询、验证码、登录、permission-info 或其他 HTTP 请求。

根据浏览器安全规则，执行没有切换到未批准的其他浏览器控制方式，也没有通过脚本或终端直接调用登录 API。

## 2. 事后只读核验

- user 113 未过期 access token 数量：0；
- role 111 允许权限：3，禁止权限：0；
- `web-service`、`iot-gateway`、`iot-system`、`iot-device` 均 healthy，容器 ID和启动时间未变化；
- 未读取密码或 token，未调用业务 API，未写 Canary。

## 3. 恢复条件

修复应用内浏览器控制连接后可沿原批准重试；若改用 Chrome CDP，必须由 owner 另行明确批准替代控制面，
并保持用户本人输入凭据、`rememberMe=false`、认证 API allowlist、token 不导出及无 Canary 写入等原边界。
