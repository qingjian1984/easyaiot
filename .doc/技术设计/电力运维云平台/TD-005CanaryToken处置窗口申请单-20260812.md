# TD-005 Canary Token 处置窗口申请单（2026-08-12）

> 双基线：平台功能计划 1.4.0 / EasyAIoT 项目开发宪法 1.5.0
> 依据：TD-005 1.0.57（Canary 写入成功，恢复 active=0/0 基线）
> 状态：EXECUTED / VERIFIED
> 目标库：`postgres-server / ruoyi-vue-pro20`（schema `public`）
> 处置对象：tenant 123 / user 132 的 4 个 token
> 先例：`USER-APPROVAL-20260811-TD005-TOKEN-DISPOSAL-6101-6106`（1.0.41，已闭合，本次不动）

## 0. 处置对象

| token 类型 | ID | 来源 | 当前状态 |
|---|---:|---|---|
| access | 6120 | REAUTH-V3（1.0.56） | 已过期（expires 2026-08-12 00:55:45），deleted=0 |
| access | 6122 | REAUTH-V4（1.0.57 前置） | 未过期（expires 2026-08-12 10:17:17），deleted=0 |
| refresh | 6119 | REAUTH-V3 | 未过期（expires 2026-08-13 00:25:45），deleted=0 |
| refresh | 6121 | REAUTH-V4 | 未过期（expires 2026-08-13 09:47:17），deleted=0 |

## 1. 唯一允许动作

- 执行前在仓库外生成 `ruoyi-vue-pro20` custom-format 全库备份，hash/TOC 校验通过后才撤销；
- 单事务软撤销上述 4 个 token（`SET deleted=1`），影响行数必须 `2 + 2 = 4`，否则 `ROLLBACK`；
- 不撤销其他用户/租户/时间窗的 token（6093–6098、6101–6106 已闭合，严禁触及）。

## 2. 明确禁止

- 不读取 `access_token` / `refresh_token` 字段值（只按 `id` 定位，结果集不返回 token 字段）；
- 不用物理 `DELETE`，不改 `expires_time`；
- 不刷新、不导出、不复制 token 到终端/仓库/文档/聊天；
- 不重建容器，不改 API 开关/Secret/角色/Topic/Nacos/capability，不写 Canary；
- 不在 `iot-device20` 库执行任何 SQL。

## 3. 冻结撤销 SQL（精确到 id，三重 WHERE 保护）

```sql
-- access token 软撤销（期望影响 2 行：6120, 6122）
UPDATE public.system_oauth2_access_token
SET deleted = 1, update_time = CURRENT_TIMESTAMP
WHERE id IN (6120, 6122)
  AND user_id = 132
  AND tenant_id = 123
  AND deleted = 0;

-- refresh token 软撤销（期望影响 2 行：6119, 6121）
UPDATE public.system_oauth2_refresh_token
SET deleted = 1, update_time = CURRENT_TIMESTAMP
WHERE id IN (6119, 6121)
  AND user_id = 132
  AND tenant_id = 123
  AND deleted = 0;
```

## 4. 验收（执行后只读核验，不取 token 值）

- 4 行 `deleted=1`，`expires_time` 未改；
- user 132 active access = 0、active refresh = 0（`deleted=0 AND expires_time > NOW()`）；
- 全库活动 token 计数下降 4（其他用户/租户不变）；
- role 112 权限仍 `3/0`、tenant 123 业务仍 `0/0/0/0`（iot-device20）、迁移 `7/7`；
- 容器不变。

## 5. 失效条件

任一 UPDATE 影响行数 ≠ 2；验收不符；备份 hash 校验失败 → `ROLLBACK` 或从备份恢复。

## 6. 建议批准语句

> 批准按 `USER-APPROVAL-20260812-TD005-TOKEN-DISPOSAL-6119-6122` 执行：仅在 `postgres-server / ruoyi-vue-pro20` 库，先做仓库外 custom-format 备份并通过 hash/TOC 校验，再单事务软撤销 tenant 123 / user 132 的 access 6120/6122 与 refresh 6119/6121（`SET deleted=1`，三重 WHERE 保护，不物理 DELETE、不改 expires_time、不读 token 值）；影响行数必须各为 2，否则 ROLLBACK；不撤销其他 token；不重建容器、不改 API/Secret/角色、不调业务 API、不写 Canary。

## 7. 后续

1. 本 token 处置窗口；
2. 收尾提交：删除临时 `.td005-auth-allowlist.js`，git 提交全部改动（provider + harness + nginx.conf 修复 + 测试 + 全部申请单/证据 + 进度入口），完成 TD-005 Canary 闭环。

## 8. 执行结果（2026-08-12）

- owner 以 `USER-APPROVAL-20260812-TD005-TOKEN-DISPOSAL-6119-6122` 精确批准本窗口；
- 仓库外 custom-format 全库备份 1,118,689 字节，SHA-256 `026788bedcc5190928078336facb1ba6c365af45265fe02e1e4aa04e98d3ce97`；
- 前置断言 4 行存在（access 2 + refresh 2），全库活动基线 `2689/2689`；
- 单事务 `UPDATE 2`（access 6120/6122）+ `UPDATE 2`（refresh 6119/6121）+ `COMMIT`；
- 4 行 `deleted=1`，user 132 active access/refresh 恢复 `0/0`；全库活动计数 `2689/2689` → `2687/2687`（精确降 2）；
- 业务基线 `4/4/17` + canary-meter-123 模板 1 行保留作 M1 证据、role 112 权限 `3/0` 不变；容器未变化。
