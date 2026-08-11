# TD-005 认证-only harness 单次认证执行证据（2026-08-11）

> 双基线：平台功能计划 1.4.0 / EasyAIoT 项目开发宪法 1.5.0
> 授权：`USER-APPROVAL-20260811-TD005-AUTH-HARNESS-SINGLE-AUTH`
> 登录对象：tenant 123 `codex测试` / user 132 `aotemane` / role 112
> 结果：`EXECUTED / VERIFIED / AUTH_ONLY_PASS`

## 1. 浏览器边界

- 使用独立可见 Chrome CDP，页面始终为 `http://localhost:8888/td005-auth-harness`；
- 用户本人输入租户、用户名、现有密码和验证码，自动化未读取或填写密码/验证码；
- `rememberMe=false`；
- 页面白名单标志在用户输入前确认 `true`；
- 首次通过 `--init-script` 注入在 CDP 复用模式下未生效，页面表单仍为空且认证尚未开始；随后在当前页面直接
  注入同一最终白名单并复核成功后才允许用户继续；
- 最终白名单仅允许五个认证 API：tenant get-id-by-name、captcha get/check、login、permission-info；静态资源
  仅允许同源必需路径，并显式阻断 WebSocket/EventSource；
- 页面按钮在首次提交后显示“认证已锁定（不可重试）”。

## 2. 页面结果

- tenant get-id-by-name：`ok`；
- captcha get/check：`ok`；
- auth/login：`ok`；
- auth/get-permission-info：`ok`；
- 页面证据身份：tenantId=123、user.id=132、roles 数量=1；
- 页面停在 harness，未进入 Router/Dashboard，无失败弹窗。

## 3. 请求路径证据

以 `web-service` Nginx 访问日志中 referrer=`/td005-auth-harness` 为事实源，本次仅出现：

- `POST /dev-api/system/captcha/get` → 200（组件初始化及认证完成后的验证码刷新，均属批准端点）；
- `GET /dev-api/system/tenant/get-id-by-name?name=codex测试` → 200；
- `POST /dev-api/system/captcha/check` → 200；
- `POST /dev-api/system/auth/login` → 200；
- `GET /dev-api/system/auth/get-permission-info` → 200。

captcha check 曾先尝试不可达 IPv6 upstream，Nginx 随后回退并返回 200；未导致重复 login。未出现
dict、video、Dashboard、`/api/v1/power/**` 或其他业务 API。日志窗口中更早的 product/OTA/NodeRed 请求
referrer 分别为 `/product`、`/ota`、`/rulechains`，不属于本次 harness 会话，未计入本次认证证据。

## 4. 只读数据库元数据验收

未查询 access/refresh token 字段，只读取 ID、租户、用户、删除标志与时间：

- 新 access ID=`6114`，tenant=123，user=132，deleted=0；
  create_time=`2026-08-11 20:12:26.264123`，expires_time=`2026-08-11 20:42:26.261159`；
- 新 refresh ID=`6113`，tenant=123，user=132，deleted=0；
  create_time=`2026-08-11 20:12:26.254980`，expires_time=`2026-08-12 20:12:26.252272`；
- tenant 123 / user 132 active access/refresh 元数据计数精确为 `1/1`；
- 既有 6101～6106 六行继续保持 deleted=1。

## 5. 未执行与后续门禁

- 未读取、显示、导出或刷新 Token；
- 未修改容器、数据库、配置、API 开关、Secret、角色、Topic、Nacos 或 capability；
- 未调用电力业务 API，未写 Canary；
- 当前独立 Chrome 会话保留，不刷新、不清存储；access 元数据到期时间为 20:42:26；
- 认证窗口到此关闭。identity→draft→validate→publish Canary 写入仍需新的精确批准。
