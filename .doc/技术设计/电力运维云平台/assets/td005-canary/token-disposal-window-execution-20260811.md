# TD-005 Canary 令牌处置窗口执行证据（2026-08-11）

> 双基线：平台功能计划 1.4.0 / EasyAIoT 项目开发宪法 1.5.0
> 批准：`USER-APPROVAL-20260811-TD005-TOKEN-DISPOSAL-6101-6106`
> 结果：`EXECUTED / VERIFIED`
> 目标：`postgres-server / ruoyi-vue-pro20 / public`

## 1. 执行前门禁

- Docker Server 28.5.1；`postgres-server` running/healthy，容器 ID
  `8484916056b694d60880c836cff20bbb662f34513db2e295ebdf8eea3ccc778c`；
- PostgreSQL 18.4，目标库 `ruoyi-vue-pro20`、schema `public`；
- access IDs 6102/6104/6106 精确为 tenant 123 / user 132 / deleted=0，共 3 行；
- refresh IDs 6101/6103/6105 精确为 tenant 123 / user 132 / deleted=0，共 3 行；
- 全库 deleted=0 基线：access 2689、refresh 2689；未查询或输出任何 token 字段值。

## 2. 强制备份

- 仓库外文件：
  `D:\working\laoluopro\workspace\easyaiot-backups\td005-token-disposal-window-20260811\ruoyi-vue-pro20_20260811_td005_token_disposal.dump`；
- 格式：PostgreSQL custom (`pg_dump -Fc`)；
- 大小：1,117,845 字节；
- SHA-256：`c6b62581ff91d9517e460f423a32dcd769e0cddd061e0e8746f127635dc11d64`；
- 容器与宿主机 hash 一致；`pg_restore -l` 1,039 行，TOC 校验 PASS。

## 3. 单事务处置

- 两张令牌表取得 EXCLUSIVE lock，`lock_timeout=5s`；
- 前置断言：目标 3+3 且全库活动基线 2689/2689，结果 PASS；
- access 仅更新 IDs 6102/6104/6106，`UPDATE 3`；
- refresh 仅更新 IDs 6101/6103/6105，`UPDATE 3`；
- 更新字段仅为 `deleted=1, update_time=CURRENT_TIMESTAMP`，未修改 `expires_time`；
- 后置断言：目标 3+3 均软撤销且全库活动计数为 2686/2686，结果 PASS；事务 COMMIT。

## 4. 执行后验收

- 六个目标 ID 均保留原 `expires_time` 且 `deleted=1`；
- tenant 123 / user 132 未过期 active access=0、active refresh=0；
- 全库 deleted=0：access 2686、refresh 2686，仅减少批准的各 3 行；
- role 112 菜单 3900～3902=3，3903～3906=0；
- `postgres-server` 容器 ID 未变化、healthy、restartCount=0；
- 容器内临时 dump 已删除，仓库外备份保留；
- 未清浏览器存储，未重建容器，未修改 API、Secret、角色、Topic、Nacos、capability 或业务数据，
  未调用业务 API，未写 Canary。

## 5. 后续门禁

本窗口只关闭令牌处置门禁，不构成认证-only harness 修改、构建、部署、重新认证或 Canary 写入批准。
下一步先修复并验证 harness 的“不得读取 token 值”和“一次失败即锁止”门禁，再单独申请 WEB harness 部署窗口。
