# TD-005 Canary Chrome CDP 认证进度检查点（2026-08-11）

> 双基线：平台功能计划 1.4.0 / EasyAIoT 项目开发宪法 1.5.0
> 当前状态：`AUTH_ONLY_PASS` / `ACTIVE_ACCESS_6114` / `NO_CANARY_WRITE`
> 登录对象：tenant 123 `codex测试` / user 132 `aotemane` / role 112
> 注意：认证-only 窗口已验收通过，但不构成 Canary 写入批准。

## 1. 已完成事实

- role 112 已按独立批准仅获得菜单 3900～3902；3903～3906 保持 0。执行前仓库外完整备份为
  `easyaiot-backups/td005-canary-role112-window-20260811/ruoyi-vue-pro20_20260811_td005_role112.dump`，
  SHA-256 为 `10a534a9da63611c26f77a812306296915eae8cd2bdfbe56d97da44353a83bbc`。
- 首次 CDP 认证暴露两个前端缺陷：`Login.vue` 重复渲染两套登录表单；`LoginForm.vue` 在提交时优先使用
  `localhost:8888` 网站映射并覆盖手工租户。该缺陷曾向 tenant 1 / user 115 错误签发 3 组令牌。
- 错误租户令牌已按 `USER-APPROVAL-20260811-TD005-WRONG-TENANT-TOKEN-CONTAINMENT` 完成处置：
  浏览器 localStorage/sessionStorage 清空且未读取值；access IDs 6094/6096/6098 与 refresh IDs
  6093/6095/6097 均软撤销。仓库外完整备份 SHA-256 为
  `7724ffd28b26d532b8626f2fbf49580ee23366f0cd94c73bb945e4fa6c75624b`。
- WEB 源码已修复登录表单重复渲染、手工租户优先、回车统一进入验证码流程、权限信息控制台日志和 Axios
  错误拦截器 `requestOptions` 空值保护。Vite 生产构建 PASS；ESLint 因仓库既有 `micromark` exports
  依赖冲突未能运行。
- 修复版 WEB 已按 `USER-APPROVAL-20260811-TD005-WEB-LOGIN-FIX-DEPLOY` 以
  `VITE_GLOB_DEPLOY_PROFILE=full` 构建并仅重建 `web-service`。运行镜像为
  `sha256:cca2c5c4df72206769a1157e63c7f7ea1319f1c063fe6fd60ed09cc57998a047`，容器 healthy，
  `/health` 与 `/login` 均为 HTTP 200；部署前镜像保留为
  `web-service:rollback-td005-login-fix-20260811`。其他容器 ID 未变化。
- 部署后 DOM 只剩 1 套表单、1 个密码框和 1 个 rememberMe；rememberMe 已取消。未读取密码、验证码、
  存储值或 Token 内容。

## 2. 当前阻断证据

- 手工租户修复已生效：当前浏览器实际请求使用 `/system/tenant/get-id-by-name`，不再使用
  `localhost:8888` 的 tenant 1 网站映射。
- 当前认证链实际到达并收到 HTTP 200：验证码 get/check、tenant get-id-by-name、`/system/auth/login`、
  `/system/auth/get-permission-info`。没有读取请求体、响应体、请求头或 Token。
- tenant 123 / user 132 在阻断期间产生过 3 组令牌元数据，现均已按独立处置窗口软撤销：
  - access IDs：6102、6104、6106；
  - refresh IDs：6101、6103、6105；
  - access 创建时间：17:30:59、17:33:10、17:33:30；refresh token 到期时间为 2026-08-12 对应时刻；
  - 当前六行 `deleted=1`，user 132 active access/refresh 均为 0。
- UI 仍显示 `Network Error`。根因不是上述认证 API 网络失败，而是认证成功后前端固定跳转 `/dashboard`；
  路由守卫首先调用 `/system/dict-data/list-all-simple`，随后 Dashboard 还会调用 `/video/alert/**`。
  这些接口不在已批准的认证 allowlist 中，浏览器层按批准正确阻断，因此 Axios 返回 `Network Error`。
- 当前“只允许租户/验证码/login/permission-info”与“登录后进入正常 Dashboard”的前端流程不兼容。
  简单放开 dict 或 video API 会扩大原批准范围，不能作为静默修复。
- 本轮没有调用 `/api/v1/power/**`，没有写模板 Canary，没有修改角色、Secret、Topic、Nacos、capability
  或数据库业务数据。

## 3. 当前工作区与运行态

- 未提交源码：
  - `WEB/src/utils/http/axios/index.ts`；
  - `WEB/src/views/base/login/Login.vue`；
  - `WEB/src/views/base/login/LoginForm.vue`。
- 临时认证白名单 `.td005-auth-allowlist.js` 仍为未跟踪文件；认证窗口关闭后必须删除，不得提交。
- 用户配置 `.claude/settings.json`、`CLAUDE.md`、`DEVICE/.claude/` 不属于本任务，继续禁止纳入提交。
- 修复版 WEB 已从当前未提交工作树构建；提交前必须重新复核差异和部署证据。

## 4. 下一步（严格顺序）

1. **立即停止重复登录**：在认证-only 流程改造完成前，不再点击登录或刷新令牌。
2. **令牌处置已完成**：access IDs 6102/6104/6106 与 refresh IDs 6101/6103/6105 已按独立批准软撤销，
   user 132 active access/refresh 均为 0；本窗口没有清浏览器存储。执行证据见
   [`token-disposal-window-execution-20260811.md`](./token-disposal-window-execution-20260811.md)。
3. **认证-only harness 代码与构建已完成，尚未部署**：入口受
   `VITE_TD005_AUTH_HARNESS=true` 构建门禁控制；已移除 token 探测、页面清会话和重试入口，首次租户查询前
   永久锁定，验证码成功/失败最多进入一次认证链，permission-info 后停止。静态契约检查和启用 gate 的
   Vite 生产构建 PASS；ESLint 被既有 `micromark` 解析错误阻断，全量 `vue-tsc` 被既有跨模块错误阻断，
   过滤输出未发现 harness 局部类型错误；当前运行 WEB 未变化。
4. **独立 WEB 部署窗口已完成**：owner 以
   `USER-APPROVAL-20260811-TD005-AUTH-HARNESS-WEB-DEPLOY` 批准后，仅以
   `full + VITE_TD005_AUTH_HARNESS=true` 构建并重建 `web-service`。新镜像
   `sha256:6789fb7c54fb480c848679f1786ff86c2165de555c40aed583b253857377c89e`、新容器
   `7f1b877fe479` 均 healthy；其他容器 ID 未变，原镜像已保留专用回退标签。详见
   [`auth-harness-web-deploy-execution-20260811.md`](./auth-harness-web-deploy-execution-20260811.md)。
5. **认证-only 单次运行验证已通过**：owner 以
   `USER-APPROVAL-20260811-TD005-AUTH-HARNESS-SINGLE-AUTH` 批准后，用户本人完成凭据和验证码输入；四步均
   `ok`，页面停在 harness，无 `Network Error`，仅出现批准认证端点且 login 仅一次。只读元数据确认新增
   access ID 6114 / refresh ID 6113 各一条，tenant 123 / user 132 active=1/1；未读取 Token 字段。详见
   [`auth-harness-single-auth-execution-20260811.md`](./auth-harness-single-auth-execution-20260811.md)。
6. **下一步申请 Canary 写入，尚未授权**：identity→draft→validate→publish 仍需独立批准；当前不得执行。
7. **收尾提交**：验证完成后删除临时白名单，只提交经复核的 WEB 修复、harness、正式证据和续作记录；
   继续排除用户配置。
