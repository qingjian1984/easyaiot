# TD-005 权限点候选

目标数据库固定为 `ruoyi-vue-pro20`。2026-08-11 经
`USER-APPROVAL-20260811-TD005-PERMISSION-SEED` 独立批准，目标实例已在仓库外自动备份成功后执行 apply，
七个权限点精确验收通过且活动角色关联为 0；rollback 尚未执行。重复 apply 仅用于同资产幂等核验，仍应先
运行只读 preflight，不得借此夹带角色授权或 API 激活。

`apply_power_model_permissions.sql` 只创建七个 `type=3` 权限按钮，挂在现有 `产品管理(id=2931)` 下，不创建页面、不分配角色。脚本会拒绝错误数据库、父节点漂移、部分既有权限、权限语义漂移和 ID 占用。重复执行仅在七行完全匹配时通过。执行事务对 `system_menu` 使用 5 秒有界写锁，避免无唯一约束时的并发重复；锁忙即失败关闭。

`rollback_power_model_permissions.sql` 只删除 `creator=td005-seed` 的精确七行；存在活动 `system_role_menu` 绑定或行漂移时拒绝回滚。回滚事务以同一超时锁定 `system_menu` 与 `system_role_menu`，关闭角色授权竞态。

`preflight_power_model_permissions.sql` 在只读事务中复核目标库、父菜单、权限重复和候选 ID；首次执行要求
权限数与候选 ID 均为 0，重复执行只接受本候选创建的七行完全匹配。`verify_power_model_permissions.sql`
要求七行完全匹配且活动角色授权仍为 0，证明 seed 窗口没有夹带角色授权。两个脚本均显式回滚只读事务。

实际执行前必须：

1. 备份 `ruoyi-vue-pro20` 并记录恢复点；
2. 使用 `psql -v ON_ERROR_STOP=1 -f preflight_power_model_permissions.sql` 只读重跑父节点、ID、权限重复和角色关联画像；
3. 获得权限 seed 目标库窗口的独立批准；
4. 使用 `psql -1 -v ON_ERROR_STOP=1 -f apply_power_model_permissions.sql` 保证单事务；
5. 立即使用 `psql -v ON_ERROR_STOP=1 -f verify_power_model_permissions.sql` 只读验收；
6. 另行批准后才可给明确的 canary 角色分配 `read/edit/publish`，不得默认授予 `power:system-template:manage`；
7. API 回滚并移除角色授权后，才可执行精确 rollback。

真实 secret、用户、角色 ID 和租户 ID 不得写入本目录。
