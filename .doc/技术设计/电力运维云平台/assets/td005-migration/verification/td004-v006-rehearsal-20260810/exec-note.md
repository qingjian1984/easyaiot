# TD-004 V006 临时评审库演练记录

> 日期：2026-08-10
> 双基线：平台功能计划 1.4.0 / EasyAIoT 项目开发宪法 1.5.0
> 环境：本地 `postgres-server`，临时库 `td004_core_review_20260810`（由 `iot-device20` 模板克隆）
> 性质：评审演练，不构成目标实例执行授权

## 资产哈希

- V006：`6fac9b429aae2fff34483fedc800f5d54bab8154b16953a85b2cf96f85229064`
- U005：`f3e5149c5b7219ddc46fb2d3b17fe0cc545b5c3bacc421b518aed64dc41f66b6`
- smoke：`cccca8fc4e4904b78e5da438166272da300cd82dbc30e2898cfaff1315178949`

## 结果

1. runner `apply --step V006`：`STEP_DONE V006` / SUCCEEDED；自动备份写入系统临时目录。
2. 同一 V006/hash 重放：`STEP_SKIPPED V006` / SUCCEEDED。
3. `td004_power_object_assignment_core_smoke.sql`：`TD004_CORE_SMOKE_PASS`；fixture 在事务末 ROLLBACK。
4. 专项审查修订后在第二临时库重跑；覆盖反例：设备与 tenant 不匹配、同设备两条当前归属、跨站/不存在主空间、对象身份原地修改、assignment 历史覆写、错误 version、已关闭行二次修改；均按约束拒绝。
5. 合法路径“关闭当前行且 version+1 → 插入新当前行”通过；READY 闭合查询恰好一条。
6. runner `check-comments`：`MIG-009 PASS`。
7. U005：五表任一非空时拒绝且对象/数据保留；清空后五表与两个函数卸载成功，`schema_migration_history.V006=SUCCEEDED` 按设计保留。
8. 两次临时库验证后均已删除；复核 `pg_database` 中不存在评审库。
9. 目标 `iot-device20` 只读复核：`to_regclass('public.power_site') IS NULL`，且不存在 V006 history；目标未受本次演练影响。

## OPEN

- V006 DBA/架构专项评审与目标窗口申请；
- 全新安装 dump、preflight、存量站点/归属导入与对账；
- Mapper/Service、站点权限、`PowerObjectQueryApi` 与真实 PostgreSQL 合同；
- U002～U005 尚未接入 runner uninstall 驱动，普通故障回滚仍为关闭 capability/写入并保留 Schema。
