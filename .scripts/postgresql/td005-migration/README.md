# TD-005 受控迁移执行器（候选 / Spike）

> 状态：Review Candidate（P02-M2-02A review-only 静态接线；数据库合同未授权）
> 日期：2026-08-25
> 决策：ADR-013 1.5.7（Accepted）
> 使用限制：目标实例每次 apply/uninstall 仍须独立审批；V011/U011 当前禁止执行，任务单不构成临时库或目标库授权

## 组成

| 文件 | 用途 |
|---|---|
| `td005_migration.sh` | 候选执行器：dry-run / apply / uninstall / check-comments |
| `steps/M15__product_tenant_unique_concurrently.sql` | M1.5 独立非事务步骤：`product (tenant_id, product_identification)` 唯一索引 |
| `check_ddl_comments.sql` | MIG-009：迁移涉及表/字段中文 `COMMENT` 完整性检查（含 LC02-07 V009 目标列） |
| `V001` / `U001` | 引用 `.doc/技术设计/电力运维云平台/assets/td005-migration/` 评审资产 |
| `V009` / `U009` | LC02-07 产品路由身份 nullable 扩展与独立收缩候选；U009 不接入 uninstall |
| `V011` / `U011` | TD-006 告警核心 review-only 候选；不进入默认 apply/uninstall 链 |
| `tests/p02_v011_contract.sh` | P02-M2-02A 纯静态与假命令反例；不连接 PostgreSQL/Docker |

## 使用

```bash
PG_PASSWORD=<password> PG_DB=<target> APPROVAL=<approval-id> \
  ./td005_migration.sh dry-run

PG_PASSWORD=<password> PG_DB=<target> APPROVAL=<approval-id> \
  ./td005_migration.sh apply

PG_PASSWORD=<password> PG_DB=<target> APPROVAL=<approval-id> \
  ./td005_migration.sh check-comments
```

默认通过 Docker 容器 `postgres-server` 执行 psql；设置 `PG_CONTAINER=` 可使用本机 `psql`。

环境变量示例见 [env.example](./env.example)。

### TD-006 review-only 入口

V011/U011 不在默认 `APPLY_STEPS`、默认 dry-run 或默认 uninstall 计划内。只有另行获得隔离临时库执行批准后，才可准备以下门禁；本文和 P02-M2-02A 任务单本身都不是执行授权：

```bash
# 仅展示接口形态，当前禁止执行
TD006_REVIEW_ONLY=1 \
TD006_TEMP_DB=td006_review_<unique> \
TD006_TEMP_SYSTEM_IDENTIFIER=<approved-temporary-cluster-id> \
PG_DB=td006_review_<unique> APPROVAL=<approval-id> BACKUP_DIR=<backup-dir> \
  ./td005_migration.sh apply --step V011

# 仅允许空表卸载；仍需同一组 review-only、身份、审批和备份门禁
./td005_migration.sh uninstall --step U011
```

runner 会在 `pg_dump` 和业务 DDL 之前，以只读会话同时核对 `current_database()` 与 `pg_control_system().system_identifier`。库名必须匹配 `^td006_review_[a-z0-9_]+$`，并内置拒绝 `iot-device20`、`postgres`、`template0`、`template1`；可用 `TD006_DENY_DATABASES` 增加本地拒绝清单。review-only 路径不接受 `SKIP_PRECHECK=1`。

## 行为

- `apply` 先持有 advisory lock，按 M15（非事务 CONCURRENTLY）→ V001～V008 → V010 → V009 顺序执行，并把 `migration_id + script_sha256 + status + evidence` 写入 `schema_migration_history`；V009 执行前强制核验 V008/V010 的成功状态与精确 hash。
- 同 migration ID 同 hash 已 SUCCEEDED 时跳过；同 ID 异 hash 返回 `HASH_MISMATCH` 并退出码 2。
- `uninstall` 执行 U001，非空表时由 U001 断言拒绝。
- `check-comments` 校验既有迁移对象以及 `iot_sink.telemetry_inbox.product_identification` 的中文注释；目标表/列缺失也会返回门禁错误。
- V011 首次执行前拒绝任何受管对象预存在；同 ID 异 hash 在业务 DDL 前返回 `HASH_MISMATCH`。首次成功把表列、约束、索引、trigger、函数的 catalog 签名写入 history；同 ID/hash 重放只有签名一致才 `STEP_SKIPPED`，否则返回 `SCHEMA_SIGNATURE_MISMATCH`。
- V011 资产与 SUCCEEDED history 由 runner 放在同一事务；U011 仅在 V011 history 与实时签名一致、九表全部为空时允许显式卸载，history 表始终保留。
- review-only 前置稳定错误包括 `TD006_REVIEW_ONLY_REQUIRED`、`TD006_TEMP_DB_MISSING/MISMATCH/INVALID`、`TD006_TEMP_SYSTEM_IDENTIFIER_MISSING`、`TD006_DATABASE_DENIED`、`TD006_*_IDENTITY_MISMATCH`、`TD006_PREEXISTING_OBJECT`、`SCHEMA_SIGNATURE_MISMATCH` 和 `NON_EMPTY_TABLE_REJECTED`。

## MIG 合同状态

MIG-001/002/004/006/007/008/009 已在临时 PostgreSQL 库验证；MIG-003/005 完成 Spike 级 precheck 验证（完整 12 表画像、TD-004 幂等表 DDL 仍 OPEN）。性能压测与备份/恢复/回滚演练仍 OPEN。

Transport 决策候选见 ADR-014（Proposed）；消费者 Inbox 候选 DDL 见 `.doc/技术设计/电力运维云平台/assets/td005-migration/consumer_inbox_candidate.sql`。
