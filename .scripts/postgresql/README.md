# PostgreSQL 运维工具索引

本目录维护 EasyAIoT PostgreSQL 的安装基线、结构同步、备份和受控迁移工具。

| 工具 | 用途 | 备注 |
|---|---|---|
| `*10.sql` | 各业务库全量安装基线（`<名字>10.sql` → 库 `<名字>20`） | `init-databases.sh`/安装器首启导入 |
| `schema-sync/` | migra 结构漂移同步：dry-run、备份、破坏性语句拦截 | 存量结构对齐 |
| `backup_databases.sh` | 容器内 `pg_dump` 多库备份 | 版本匹配 |
| `td005-migration/` | TD-005 受控迁移执行器候选（ADR-013 Proposed） | 仅评审/临时库，批准前不得用于生产 |

## 约定

- 新增库：按 `<名字>10.sql` 命名即可被自动发现。
- 数据库密码通过 `.env` 或环境变量提供，禁止写死。
- 新表/新字段 DDL 必须含中文 `COMMENT ON TABLE` / `COMMENT ON COLUMN`（宪法 1.5.0）。
