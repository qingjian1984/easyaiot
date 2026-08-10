# V007 目标窗口执行记录

> 日期：2026-08-10（Asia/Shanghai）
> 目标：`postgres-server / iot-device20`
> 授权：`USER-APPROVAL-20260810-V007`
> 执行边界：runner 自动备份后，仅执行 V007；未启用第四端口配置

## 执行资产

- V007 SHA-256：`6590d6daa33e6e3382f17b1ef1ced0ed854c5322857062617d2b77c621e38685`
- runner：`.scripts/postgresql/td005-migration/td005_migration.sh apply --step V007`
- 仓库外备份：`D:\working\laoluopro\workspace\easyaiot-backups\td001-v007-window-20260810\iot-device20_20260810_142623.sql`
- 备份大小：358012 bytes
- 备份 SHA-256：`c1196e0f99d040029506eb2a590b51346e12831e8f3f69675a56f74e6ff2a53b`

首次调用因 PowerShell 环境变量未传入 WSL，在任何备份/DDL 前以 `BACKUP_MISSING` 失败关闭；
随后在同一 bash 进程内设置 `BACKUP_DIR` 与未回显容器凭据，按同一授权重试成功。

## 验收结果

1. runner precheck PASS、仓库外备份非空、单步骤 `STEP_DONE V007`、apply SUCCEEDED。
2. `schema_migration_history`：V007 / SUCCEEDED / SHA-256 精确匹配。
3. 新增列 6/6、约束 6/6、不可变函数 1/1；新增列 COMMENT 缺失 0。
4. MIG-009 中文注释门禁 PASS；invalid index=0。
5. `JdbcCollectorConfigReleasePortPostgresIntegrationTest` 2/2 PASS（静态默认关闭 1 + 目标 PG 事务合同 1）。
6. `PowerModelCollectorEventHandlersTest` 13/13 PASS；合计 15/15，无失败/错误/跳过。
7. 测试事务回滚后 release/projection/binding/outbox/template fixture 均为 0。
8. 原业务计数保持 product/device/product_properties=`4/4/17`。
9. 仓库运行配置未出现 `collector-release-port-enabled=true`，第四端口仍不装配。

## 下一步边界

- V007 数据库门禁已关闭，但首次 VALIDATED 候选创建 API/事务尚未实现，不能启用第四端口。
- 下一步应实现 validate/导入事务：同事务写产品绑定、不可变 collector 候选、领域审计和 Outbox；
  完成真实 PG 原子合同后，再单独评审是否开启 `collector-release-port-enabled`。
