# V001 落库窗口执行记录（2026-08-08）

- 批准：owner 于 2026-08-08 批准窗口并指示立即执行（申请单：../V001落库窗口申请单-20260808.md）
- 批准单号（APPROVAL）：V001-WINDOW-20260808
- 目标：postgres-server / iot-device20（本地 Docker 集成实例，如实声明非独立生产）
- 命令：`td005_migration.sh apply --yes`（PG_CONTAINER=postgres-server 显式传参；BACKUP_DIR 位于仓库外，避免数据入库）
- 结果：**apply SUCCEEDED**（2026-08-08T06:16:27Z）
- 执行前自动备份：`iot-device20_20260808_061623.sql`（仓库外目录，含业务数据，不提交）
- 执行后只读验证（verify.txt）：
  - history：M05/M15/M16/V001/V002 全部 SUCCEEDED
  - invalid_indexes=0
  - MIG-009 中文注释门禁 PASS（check-comments.log）
  - 业务计数不变：product=4、device=4、product_properties=17
  - power_model_release_outbox 行数=0（新表无残留）
- 回滚入口：U001（仅空表）；pg_dump 备份（仓库外）；应用层开关 `power.model.events.enabled`
