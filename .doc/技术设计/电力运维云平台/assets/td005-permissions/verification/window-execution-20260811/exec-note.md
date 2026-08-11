# TD-005 权限 Seed 目标窗口执行记录

> 日期：2026-08-11
> 双基线：平台功能计划 1.4.0 / EasyAIoT 项目开发宪法 1.5.0
> 目标：`postgres-server / ruoyi-vue-pro20` 本地目标集成实例
> 批准：`USER-APPROVAL-20260811-TD005-PERMISSION-SEED`

## 执行资产

- preflight：`355bd5454ae60c5ee0d5f9274d49c4a019c2c2811146f2c130d95b22b4dd5866`
- apply：`f99e191d401c8ff0579a3869f0352694d40941c9f6dd0db08fb9a6057828573f`
- verify：`b400384440d247f2eca713aecaa351cc4f871bc4e5feabe35189247e6bfb140b`
- rollback 候选：`88223bb266f2bb6d75fbf7e2c58395df5c1c1aac42def6b19ae3e7ff2c6aa5d8`

## 备份

- 路径：`D:\working\laoluopro\workspace\easyaiot-backups\td005-permissions-window-20260811\ruoyi-vue-pro20_20260811_td005_permissions.dump`
- 格式：pg_dump custom；大小：1,111,985 字节。
- SHA-256：`3a4470580ba883acb826cf5e19afdbb4dd029bd27a40d3f1f95c9cd5b51f499e`。
- 宿主机与容器内 hash 一致；`pg_restore -l` 成功，archive 数据库与 pg_dump 版本均为 PostgreSQL 18.4，
  TOC 1028 项。
- 保留：至少至 Stage 3 canary 验收完成后 7 天；删除需运维确认。

## 前检、执行与验收

1. 冻结 preflight 在只读事务中 PASS 后 ROLLBACK：父菜单 2931=产品管理/type 2/active，权限=0、候选 ID=0、
   角色关联=0。
2. 自动备份成功并完成 hash/TOC 校验后才进入写事务。
3. apply 以 `psql -1 -v ON_ERROR_STOP=1` 执行，5 秒锁门禁未触发，返回 `INSERT 0 7`。
4. 冻结 verify 在只读事务中逐行核对 3900～3906 全部字段后 ROLLBACK；精确 seed 行=7，角色关联=0。
5. template API 仍为默认 false、binding API=false、HMAC secret=0 字节；`iot-device` running/healthy。
6. 未授权角色、未启用 API、未注入 secret、未重启容器、未写 canary 数据。
7. 容器 `/tmp` 三个临时文件已删除；仓库外备份保留。

## 后续边界

权限 seed 已完成不等于 Stage 3 获准。canary 角色选择/授权、HMAC secret 保管与注入、隔离 canary
租户/产品创建、template API 启用和任何 canary 写入仍分别为 OPEN，必须逐项获得独立批准。
