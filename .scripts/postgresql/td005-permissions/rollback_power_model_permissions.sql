-- TD-005 权限点精确回滚候选。
-- 若任何权限已绑定活动角色，或目标行发生漂移，回滚 fail-closed。
-- 本文件不包含事务控制，执行器必须使用 psql -1 -v ON_ERROR_STOP=1。

SET LOCAL lock_timeout = '5s';
LOCK TABLE public.system_menu IN SHARE ROW EXCLUSIVE MODE;
LOCK TABLE public.system_role_menu IN SHARE ROW EXCLUSIVE MODE;

DO $guard$
DECLARE
    exact_permissions integer;
    existing_permissions integer;
    active_role_links integer;
BEGIN
    IF current_database() <> 'ruoyi-vue-pro20' THEN
        RAISE EXCEPTION 'TD005_PERMISSION_WRONG_DATABASE: %', current_database();
    END IF;
    SELECT count(*) INTO exact_permissions
    FROM public.system_menu
    WHERE (id, permission, name, sort) IN (
        (3900, 'power:model-template:read', '电力物模型模板读取', 90),
        (3901, 'power:model-template:edit', '电力物模型模板编辑', 91),
        (3902, 'power:model-template:publish', '电力物模型模板发布', 92),
        (3903, 'power:model-template:import', '电力物模型模板导入', 93),
        (3904, 'power:model-template:upgrade', '电力物模型模板升级', 94),
        (3905, 'power:model-template:retire', '电力物模型模板退役', 95),
        (3906, 'power:system-template:manage', '系统物模型模板管理', 96)
    ) AND creator = 'td005-seed' AND type = 3 AND parent_id = 2931
      AND status = 0 AND deleted = 0;
    IF exact_permissions = 0 THEN
        IF EXISTS (
            SELECT 1 FROM public.system_menu
            WHERE permission IN (
                'power:model-template:read', 'power:model-template:edit',
                'power:model-template:publish', 'power:model-template:import',
                'power:model-template:upgrade', 'power:model-template:retire',
                'power:system-template:manage'
            )
        ) THEN
            RAISE EXCEPTION 'TD005_PERMISSION_ROLLBACK_DRIFT: exact=0 but frozen permissions exist';
        END IF;
        RETURN;
    END IF;
    SELECT count(*) INTO existing_permissions
    FROM public.system_menu
    WHERE permission IN (
        'power:model-template:read', 'power:model-template:edit',
        'power:model-template:publish', 'power:model-template:import',
        'power:model-template:upgrade', 'power:model-template:retire',
        'power:system-template:manage'
    );
    IF exact_permissions <> 7 OR existing_permissions <> 7 THEN
        RAISE EXCEPTION 'TD005_PERMISSION_ROLLBACK_DRIFT: exact=% existing=%',
            exact_permissions, existing_permissions;
    END IF;
    SELECT count(*) INTO active_role_links
    FROM public.system_role_menu
    WHERE menu_id BETWEEN 3900 AND 3906 AND deleted = 0;
    IF active_role_links <> 0 THEN
        RAISE EXCEPTION 'TD005_PERMISSION_ROLLBACK_ROLE_LINKED: count=%', active_role_links;
    END IF;
END
$guard$;

DELETE FROM public.system_menu
WHERE (id, permission, name, sort) IN (
    (3900, 'power:model-template:read', '电力物模型模板读取', 90),
    (3901, 'power:model-template:edit', '电力物模型模板编辑', 91),
    (3902, 'power:model-template:publish', '电力物模型模板发布', 92),
    (3903, 'power:model-template:import', '电力物模型模板导入', 93),
    (3904, 'power:model-template:upgrade', '电力物模型模板升级', 94),
    (3905, 'power:model-template:retire', '电力物模型模板退役', 95),
    (3906, 'power:system-template:manage', '系统物模型模板管理', 96)
) AND creator = 'td005-seed' AND type = 3 AND parent_id = 2931
  AND status = 0 AND deleted = 0;
