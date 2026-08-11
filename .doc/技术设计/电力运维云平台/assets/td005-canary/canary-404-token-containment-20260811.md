# TD-005 Canary identity 404 令牌收敛证据（2026-08-11）

> 双基线：平台功能计划 1.4.0 / EasyAIoT 项目开发宪法 1.5.0
> 授权：`USER-APPROVAL-20260811-TD005-CANARY-404-TOKEN-CONTAINMENT`
> 目标：tenant 123 / user 132 / access 6118 / refresh 6117
> 结论：`CONTAINED / ACTIVE_TOKENS_0_0 / CANARY_RESIDUAL_0`

## 1. 浏览器收敛

- 通过既有独立 Chrome CDP，在 `localhost:8888` 源内执行 `localStorage.clear()` 与
  `sessionStorage.clear()`；
- 未读取、导出或显示任何存储值，未清除 Cookie 或缓存，未调用 API。

## 2. 备份

- 目标：`postgres-server / ruoyi-vue-pro20`；
- 仓库外 custom-format 文件：
  `D:\working\laoluopro\workspace\easyaiot-backups\td005-canary-404-token-containment-20260811\ruoyi-vue-pro20_20260811_2151_precontainment.dump`；
- 字节数：1,118,403；
- SHA-256：`8804eea31b907279231fb87353b75cd23c6546e0437b93eff34565b784bc1619`；
- `pg_restore -l`：1,039 行；容器与宿主机 hash 一致；
- 复制与校验完成后仅删除容器内精确临时 dump，仓库外恢复点保留。

## 3. 精确软撤销

- 单事务对 `system_oauth2_access_token` 与 `system_oauth2_refresh_token` 取得 EXCLUSIVE lock；
- 前置断言 access 6118、refresh 6117 均精确匹配 tenant 123 / user 132 / deleted=0 各一行；
- 仅设置两行 `deleted=1, update_time=CURRENT_TIMESTAMP`，提交时间
  `2026-08-11 21:53:29.606687`；未修改到期时间；
- 提交后 access 6118 / refresh 6117 均为 deleted=1，tenant 123 / user 132 活动 access/refresh=0/0；
- 全程未查询 Token 字段。

## 4. 最终核验

- 双库 READ ONLY 前检再次 PASS 并 ROLLBACK：权限 3/0、十四类 Canary 业务事实残留 0；
- postgres-server、iot-device、iot-gateway、web-service 均 healthy，未重建任何容器；
- 未修改配置、角色、API、Secret、Topic、Nacos 或 capability，未调用业务 API，未写 Canary。

下一步仍是独立的 iot-device 当前源码镜像修复部署窗口；修复后必须重新认证并取得新的 Canary 单次写入批准。
