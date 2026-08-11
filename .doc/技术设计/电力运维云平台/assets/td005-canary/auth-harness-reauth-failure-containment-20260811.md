# TD-005 认证 harness 重认证失败与令牌收敛证据（2026-08-11）

> 双基线：平台功能计划 1.4.0 / EasyAIoT 项目开发宪法 1.5.0
> 认证批准：`USER-APPROVAL-20260811-TD005-AUTH-HARNESS-REAUTH`
> 收敛批准：`USER-APPROVAL-20260811-TD005-REAUTH-FAILURE-TOKEN-CONTAINMENT`
> 结论：`AUTH WINDOW FAILED / TOKEN CONTAINMENT VERIFIED / FIX BUILT NOT DEPLOYED`

## 1. 认证窗口失败事实

- 独立 Chrome 在清空 localhost:8888 localStorage/sessionStorage 后打开 `/td005-auth-harness`；未读取存储值，
  未清 Cookie 或缓存；
- 20:45:50 tenant 查询返回 401，Axios 会话失效流程随后调用白名单外 `/system/auth/logout`，该调用也返回 401；
- 20:46:32～20:46:36 的正式 tenant/captcha/login/permission-info 均为 200，login 精确一次，页面四步均 `ok`
  并永久锁定；但因出现白名单外路径，本窗口按失败处理，不授权使用该会话执行 Canary；
- 浏览器随后安装全网络锁止；全程未调用电力 API、未写 Canary、未读取或显示 Token。

## 2. 令牌收敛

- 收敛前只读确认 access 6114/6116、refresh 6113/6115 均为 tenant 123 / user 132 / deleted=0；
- 仓库外 PostgreSQL custom-format 全库备份：1,118,259 字节，SHA-256
  `7fc735636066cee6ad5d5b05a8d7acb4615eecf930dde24c1d6624124a0cbc16`，容器/宿主机 hash 一致，
  `pg_restore -l` 1,039 行 PASS；
- 单事务取得两张令牌表 EXCLUSIVE lock，前置全库未删除计数 2689/2689；access 与 refresh 各 `UPDATE 2`，
  仅设置 `deleted=1, update_time=CURRENT_TIMESTAMP`；到期时间未修改；
- 提交后四行均 deleted=1，全库未删除计数 2687/2687，tenant 123 / user 132 未删除 access/refresh=0/0；
- tenant 123 双库只读前检仍为权限 3/0、十四类业务事实残留 0；PostgreSQL 容器 healthy、未重启；
  容器临时 dump 已删除，仓库外备份保留。

## 3. 代码修复与验证（尚未部署）

- 新增 harness 唯一网络门禁 `td005AuthAllowlist.js`，作为页面最先执行的副作用模块；页面重载会重新安装，
  不再依赖 CDP 会话长期保持；
- 安装顺序改为先对 fetch/XHR/sendBeacon/WebSocket/EventSource 全拒绝，再开放五类认证 API 和静态资源；
  任一网络面不可替换时保持全拒绝且不写完成标记；
- logout、refresh-token、跨域、WebSocket、EventSource、重复安装及部分安装失败均进入 Node 合同；
  `pnpm verify:td005-auth-allowlist` PASS；
- `VITE_GLOB_DEPLOY_PROFILE=full`、`VITE_TD005_AUTH_HARNESS=true` 生产构建 PASS（135.9 秒）；dist 唯一 chunk
  同时包含 harness、`TD005_AUTH_ALLOWLIST_SURFACE_UNPATCHABLE` 与 `TD005_AUTH_WINDOW_BLOCKED`；
- 当前运行中的 web-service **尚未重建**，因此不能申请再次认证。下一步须独立批准仅部署该 WEB 构建，
  部署验收后再申请新的单次认证；Canary 写入继续保持 OPEN。

## 4. 网络门禁 WEB 部署结果

- owner 以 `USER-APPROVAL-20260811-TD005-AUTH-HARNESS-NETWORK-GATE-WEB-DEPLOY` 精确批准仅部署 WEB；
- 部署前 `web-service` 容器 `7f1b877fe479…`、镜像 `sha256:6789fb7c…c89e` healthy/restartCount=0；
  旧镜像已保留为 `web-service:rollback-td005-network-gate-predeploy-20260811`；
- Docker 构建日志明确 `VITE_GLOB_DEPLOY_PROFILE=full`、`VITE_TD005_AUTH_HARNESS=true`，Vite/postBuild PASS；
  新镜像为 `sha256:fd7c5887…c1e3a`；
- 仅以 Compose `--no-deps` 重建 web-service；新容器 `f96616cd0756…` healthy、restartCount=0；其他 20 个
  容器 ID 20/20 未变化；
- 容器运行资产 `/usr/share/nginx/html/assets/index-c6336efc.js` 同时包含
  `TD005_AUTH_ALLOWLIST_SURFACE_UNPATCHABLE` 与 `TD005_AUTH_WINDOW_BLOCKED`；harness 路由资产存在；
- 未打开浏览器、未登录、未调用 API、未改数据库/Secret/角色/Topic/Nacos/capability，未写 Canary；
  回退未触发。下一步必须另批新的单次认证窗口。
