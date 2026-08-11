# TD-005 Canary tenant 123 角色授权窗口执行记录

> 日期：2026-08-11
> 双基线：平台功能计划 1.4.0 / EasyAIoT 项目开发宪法 1.5.0
> APPROVAL：owner 原文批准 tenant 123 / role 112 的最小权限授权窗口

## 执行边界

仅允许在 `postgres-server / ruoyi-vue-pro20` 自动备份成功后，向 tenant 123 / role 112
新增菜单 3900～3902。禁止授予 3903～3906、复制 role 111 的其他权限，以及修改 API、Secret、
容器、Topic 或其他数据库数据。

## 备份

- 仓库外路径：`D:\working\laoluopro\workspace\easyaiot-backups\td005-canary-role112-window-20260811\ruoyi-vue-pro20_20260811_td005_role112.dump`
- 格式：PostgreSQL custom；大小：1,116,283 字节；TOC：1,039 行；
- SHA-256：`10a534a9da63611c26f77a812306296915eae8cd2bdfbe56d97da44353a83bbc`；
- 容器端与宿主机 SHA-256 一致，`pg_restore -l` 成功；容器临时文件已清理。

## 执行与验收

1. 写入前确认 tenant 123、user 132、role 112 均启用且未删除，角色归属 tenant 123；
2. 3900～3906 写入前全部未授权；
3. 单事务锁定 `system_role_menu`，仅插入 3900～3902，返回
   `TD005_ROLE112_GRANT_OK inserted=3 before=180 after=183 target=3 forbidden=0`；
4. 写入后精确三行，creator=`td005-canary-grant-role112`，tenant_id=123；
5. 3903～3906 为 0，使用该 creator 的越界记录为 0；
6. tenant 122 / role 111 仍为 3900～3902 三项，3903～3906 为 0；
7. 未重启或重建容器，未修改 API、Secret、Topic，未调用业务 API，未写 Canary 业务数据。

结论：**PASS**。本窗口不构成将浏览器认证账号从 tenant 122 / `aoteman` 切换到
tenant 123 / `aotemane` 的授权；账号切换仍需独立明确批准。
