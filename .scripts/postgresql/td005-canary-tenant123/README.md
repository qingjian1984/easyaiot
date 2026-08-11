# TD-005 tenant 123 Canary 只读前检

本目录只核对 tenant 123 `codex测试` / user 132 `aotemane` / role 112 的精确身份、3900～3902 最小权限和
3903～3906 禁止权限，并确认 `iot-device20` 中 tenant 123 的十四类 Canary 业务事实为 0。

唯一入口：

```powershell
.scripts/postgresql/td005-canary-tenant123/run_readonly_preflight.ps1
```

两个 SQL 均以 `BEGIN TRANSACTION READ ONLY` 开始并显式 `ROLLBACK`；包装器拒绝缺少只读事务或包含
`COMMIT` 的脚本，且固定 UTF-8 native pipeline。它不查询密码、Token 或 Secret，不调用 API，不写数据库。

前检 PASS 只关闭运行前身份/权限/空事实门禁，不构成 identity→draft→validate→publish Canary 写入批准。
