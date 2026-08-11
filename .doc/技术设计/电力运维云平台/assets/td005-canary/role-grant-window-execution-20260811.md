# TD-005 Canary 角色授权窗口执行记录

> 日期：2026-08-11
> 双基线：平台功能计划 1.4.0 / EasyAIoT 项目开发宪法 1.5.0
> APPROVAL：`USER-APPROVAL-20260811-TD005-CANARY-ROLE-GRANT`

## 执行边界

仅允许在 `postgres-server / ruoyi-vue-pro20` 自动备份成功后，向 tenant 122 / role 111 新增菜单
3900～3902。禁止 3903～3906；禁止启用 API、修改 Secret、重启容器或写入 Canary 业务数据。

## 备份

- 仓库外路径：`D:\working\laoluopro\workspace\easyaiot-backups\td005-canary-role-window-20260811\ruoyi-vue-pro20_20260811_td005_canary_role.dump`
- 格式：PostgreSQL custom；大小：1,112,181 字节；TOC：1024；
- SHA-256：`8bfc32f04b2075f00a0dc49e3e68f7cd2428c266bb42b875116e09b009b8062b`；
- 容器/宿主机 hash 一致，`pg_restore -l` PASS；容器临时文件已清理。

## 执行与验收

1. 五项冻结资产 hash 与审批单一致；授权前双库只读 preflight PASS 并 ROLLBACK；
2. apply 使用 `psql -1 -v ON_ERROR_STOP=1`，返回 `INSERT 0 3`；
3. 冻结 verify 只返回 role 111 / tenant 122 / 3900～3902，creator=`td005-canary-grant`；
4. 3903～3906 为 0，tenant 122 的 14 类业务事实为 0；
5. template API=false、binding API=false；阶段 2 16/16 PASS；
6. `iot-device` 启动时间执行前后均为 `2026-08-11T03:32:51.046352699Z`，未重启；
7. 未修改 Secret，未创建产品、模板、设备或绑定，未调用 Canary API。

结论：**PASS**。下一门禁是独立的 template API 启用窗口；本批准不得复用于该窗口或 Canary 写入。
