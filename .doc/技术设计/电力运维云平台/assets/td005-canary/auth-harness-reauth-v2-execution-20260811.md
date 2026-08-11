# TD-005 认证 harness REAUTH-V2 执行证据（2026-08-11）

> 双基线：平台功能计划 1.4.0 / EasyAIoT 项目开发宪法 1.5.0
> 授权：`USER-APPROVAL-20260811-TD005-AUTH-HARNESS-REAUTH-V2`
> 登录对象：tenant 123 `codex测试` / user 132 `aotemane` / role 112
> 结论：`AUTH_ONLY_PASS / NETWORK_GATE_PASS / NO_CANARY_WRITE`

## 1. 浏览器边界与结果

- 连接既有独立可见 Chrome CDP；无读取地清除 `localhost:8888` 的 localStorage/sessionStorage，未清 Cookie 或缓存；
- 仅打开 `http://localhost:8888/td005-auth-harness`，用户输入凭据前核验页面内置门禁
  `window.__TD005_AUTH_ALLOWLIST__ === true`；
- 用户本人输入现有密码与验证码，`rememberMe=false`；页面始终停留在 harness，未进入 Dashboard；
- tenant、captcha、login、permission-info 四步均为 `ok`，提交按钮永久锁定；页面证据为 tenantId=123、
  user.id=132、permissions=72、roles=1；
- 浏览器资源记录中认证 API 精确为 tenant 查询 1 次、captcha get 2 次、captcha check 1 次、login 1 次、
  permission-info 1 次，未出现 logout、refresh-token、电力或其他业务 API；
- 未读取、显示、导出或刷新 Token，未调用 Canary API。

## 2. 只读数据库元数据

未查询 access/refresh token 字段，仅核对 ID、租户、用户、删除状态与时间：

- access ID 6118：tenant=123、user=132、deleted=0，创建于 `2026-08-11 21:28:56.458202`，
  到期于 `2026-08-11 21:58:56.455295`；
- refresh ID 6117：tenant=123、user=132、deleted=0，创建于 `2026-08-11 21:28:56.448254`，
  到期于 `2026-08-12 21:28:56.445666`；
- user 132 活动 access/refresh 精确为 1/1；既有 6113～6116 继续保持 deleted=1。

## 3. Canary 写入前新鲜度复核

21:34 再次执行仓库冻结的 `.scripts/postgresql/td005-canary-tenant123/run_readonly_preflight.ps1`：

- `ruoyi-vue-pro20`：tenant/user/role 精确匹配，允许菜单 3900～3902=3，禁止菜单 3903～3906=0；
- `iot-device20`：十四类 Canary 业务事实残留=0；
- 两个 SQL 均为 `BEGIN TRANSACTION READ ONLY` 并显式 `ROLLBACK`。

## 4. 剩余门禁

本窗口未修改容器、数据库、配置、角色、API、Secret、Topic、Nacos 或 capability，未写 Canary。下一步仅为
owner 独立精确批准冻结资产的 `identity → draft → validate → publish` 单次 Canary 窗口；本认证批准不构成
该写入授权。
