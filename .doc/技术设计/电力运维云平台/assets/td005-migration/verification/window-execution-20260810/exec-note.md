# TD-005 V003/V004/V005 目标实例窗口执行记录

> 执行日期：2026-08-10
> 目标：`postgres-server / iot-device20`（本地目标集成实例，非独立生产实例）
> 用户授权：`USER-CONTINUE-20260810-V003-V005`
> 双基线：平台功能计划 1.4.0 / EasyAIoT 项目开发宪法 1.5.0

## 执行前门禁

- 目标 PostgreSQL：18.4；其他活动会话 0；V003/V004/V005 对象均不存在。
- 一次性镜像评审库 `td005_window_review_20260810`：V003/V004/V005 首次执行 SUCCEEDED；二次执行 M05～V005 全部 SKIPPED。
- MIG-009 中文注释门禁 PASS；V004 单调 revision、同版本漂移、身份改写、跨租户 FK 等反例烟测 PASS。
- 协调端口 3 项 + Inbox 5 项真实 PostgreSQL 合同测试：8/8 PASS、0 skipped、fixture 残留 0。

## 版本资产与执行结果

| 步骤 | SHA-256 | 结果 |
|---|---|---|
| V003 | `94da985f2bae6bfcaa0df68983857a20d4d40418b0f4c09e89624553749bb693` | SUCCEEDED |
| V004 | `fb6708219c38e88ccf1f181828004deeb30ba09ca29d967eddba92ed81714603` | SUCCEEDED |
| V005 | `a29f57d603b8beca667038a3625223f58c6463f32a2a9e1877e42e9dc41cc52c` | SUCCEEDED |

runner 在执行前自动生成完整 `pg_dump`：

`D:/working/laoluopro/workspace/easyaiot-backups/td005-window-20260810/iot-device20_20260810_111153.sql`

备份位于仓库外且未提交；执行证据已写入 `schema_migration_history.evidence`。

## 执行后验证

- V003 三表、V004 投影表、V005 Inbox 表均存在；MIG-009 PASS。
- 目标实例真实 PostgreSQL 合同测试 8/8 PASS、0 failures、0 errors、0 skipped。
- 测试后五张新增业务表行数均为 0，无 fixture 残留。
- 一次性评审库及仓库内演练备份已删除；目标恢复备份保留。
- 四协调端口当前仍仅 3/4；`CollectorConfigReleasePort` 未实现，注册表继续空回退并进 DLQ，未启用生产协调链。
