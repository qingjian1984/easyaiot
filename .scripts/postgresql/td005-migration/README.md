# TD-005 受控迁移执行器（候选 / Spike）

> 状态：Review Candidate（LC02-07 V009 资产已实现，待 Sol 复核）
> 日期：2026-08-24
> 决策：ADR-013 1.5.7（Accepted）
> 使用限制：目标实例每次 apply/uninstall 仍须独立审批；LC02-07 当前只允许隔离临时库验证

## 组成

| 文件 | 用途 |
|---|---|
| `td005_migration.sh` | 候选执行器：dry-run / apply / uninstall / check-comments |
| `steps/M15__product_tenant_unique_concurrently.sql` | M1.5 独立非事务步骤：`product (tenant_id, product_identification)` 唯一索引 |
| `check_ddl_comments.sql` | MIG-009：迁移涉及表/字段中文 `COMMENT` 完整性检查（含 LC02-07 V009 目标列） |
| `V001` / `U001` | 引用 `.doc/技术设计/电力运维云平台/assets/td005-migration/` 评审资产 |
| `V009` / `U009` | LC02-07 产品路由身份 nullable 扩展与独立收缩候选；U009 不接入 uninstall |

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

## 行为

- `apply` 先持有 advisory lock，按 M15（非事务 CONCURRENTLY）→ V001～V008 → V010 → V009 顺序执行，并把 `migration_id + script_sha256 + status + evidence` 写入 `schema_migration_history`；V009 执行前强制核验 V008/V010 的成功状态与精确 hash。
- 同 migration ID 同 hash 已 SUCCEEDED 时跳过；同 ID 异 hash 返回 `HASH_MISMATCH` 并退出码 2。
- `uninstall` 执行 U001，非空表时由 U001 断言拒绝。
- `check-comments` 校验既有迁移对象以及 `iot_sink.telemetry_inbox.product_identification` 的中文注释；目标表/列缺失也会返回门禁错误。

## MIG 合同状态

MIG-001/002/004/006/007/008/009 已在临时 PostgreSQL 库验证；MIG-003/005 完成 Spike 级 precheck 验证（完整 12 表画像、TD-004 幂等表 DDL 仍 OPEN）。性能压测与备份/恢复/回滚演练仍 OPEN。

Transport 决策候选见 ADR-014（Proposed）；消费者 Inbox 候选 DDL 见 `.doc/技术设计/电力运维云平台/assets/td005-migration/consumer_inbox_candidate.sql`。
