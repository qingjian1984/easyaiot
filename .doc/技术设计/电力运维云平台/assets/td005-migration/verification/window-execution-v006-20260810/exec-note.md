# TD-004 V006 目标窗口执行记录

> 日期：2026-08-10
> 双基线：平台功能计划 1.4.0 / EasyAIoT 项目开发宪法 1.5.0
> 目标：本地目标集成实例 `postgres-server / iot-device20`
> 批准：`USER-APPROVAL-20260810-V006`

## 执行资产

- V006：`6fac9b429aae2fff34483fedc800f5d54bab8154b16953a85b2cf96f85229064`
- runner：`.scripts/postgresql/td005-migration/td005_migration.sh apply --step V006`
- 仓库外备份：`D:\working\laoluopro\workspace\easyaiot-backups\td004-v006-window-20260810\iot-device20_20260810_124655.sql`（324139 bytes；SHA-256 `9d9c0097f85db3e2e00534b864e8eb04dd4bdf5e3f856b9145827ff55bf0a2d9`）

## 前置检查

- PostgreSQL 18.4，目标库名精确为 `iot-device20`。
- M05/M15/M16/V001～V005 均为 `SUCCEEDED` 且 hash 与冻结资产一致；V006 history 不存在。
- `power_site/power_space_node/power_circuit/power_device_asset/power_device_assignment` 均不存在。
- invalid index=0；`product=4 / device=4 / product_properties=17`。

## 执行与验收

1. runner precheck PASS，自动 `pg_dump` 成功后才进入迁移事务。
2. V006 单事务完成，runner 返回 `STEP_DONE V006` 与 `apply SUCCEEDED`。
3. history 中 V006=`SUCCEEDED`，hash 精确匹配，approval=`USER-APPROVAL-20260810-V006`；V001～V005 history/hash 未改变。
4. 五表全部存在且各为 0 行；未插入 fixture 或业务初始化数据。
5. 三个保护函数与六个 trigger 存在；invalid index=0。
6. runner `check-comments` 返回 `MIG-009 PASS`。
7. 执行后 `product=4 / device=4 / product_properties=17`，与前置检查一致。

## 安全边界与下一步

- 本窗口只建立 additive 空 Schema，不表示 TD-004 功能上线或冻结。
- `power.device.model`、菜单、路由、后台任务和第四协调端口均未启用；mini 继续禁用电力能力。
- 下一步实现 tenant-safe `PowerObjectQueryApi`/Mapper/Service 与真实 PostgreSQL 合同；全量安装 dump、存量导入和四项采集策略来源继续 OPEN。
- U005 仍是独立空表收缩候选；未取得新的 owner 批准不得执行。
