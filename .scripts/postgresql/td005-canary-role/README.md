# TD-005 canary 角色授权候选

推荐 canary 边界为测试租户 `122`、角色 `111`。只读画像确认该租户在 iot-device20 的产品、设备、模板、
绑定、事件、幂等和 collector 14 类事实均为 0；角色 111 当前关联 1 个活动用户，TD-005 七项权限关联为 0。

本候选仅授予菜单 3900～3902：`read/edit/publish`。明确不授予 `import/upgrade/retire` 和
`power:system-template:manage`。它不创建用户、角色、产品或模板，不启用 API，不注入 secret。

执行前必须依次运行两个只读 preflight、重新备份 `ruoyi-vue-pro20` 并取得独立角色授权批准。apply/rollback
均要求 `psql -1 -v ON_ERROR_STOP=1`，以 5 秒有界锁关闭并发重复；verify 必须确认恰好三项授权且四项禁止
权限均不存在。普通“继续”不构成授权批准。

Windows PowerShell 必须使用只读封装入口：

```powershell
.scripts/postgresql/td005-canary-role/run_readonly_preflight.ps1
```

该入口显式把 native pipeline 固定为 UTF-8；禁止直接使用未设置 `$OutputEncoding` 的
`Get-Content | docker exec -i`，否则中文租户名可能被错误编码并误报 `TD005_CANARY_TENANT_MISMATCH`。
