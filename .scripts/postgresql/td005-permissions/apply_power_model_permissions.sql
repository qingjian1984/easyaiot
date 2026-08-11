-- TD-005 1.0.23 权限点候选；目标库：ruoyi-vue-pro20。
-- 本文件不包含事务控制，执行器必须使用 psql -1 -v ON_ERROR_STOP=1。
-- 只创建权限按钮，不创建菜单页面、不绑定角色、不写租户授权。

SET LOCAL lock_timeout = '5s';
LOCK TABLE public.system_menu IN SHARE ROW EXCLUSIVE MODE;

DO $guard$
DECLARE
    existing_permissions integer;
    exact_permissions integer;
    occupied_ids integer;
BEGIN
    IF current_database() <> 'ruoyi-vue-pro20' THEN
        RAISE EXCEPTION 'TD005_PERMISSION_WRONG_DATABASE: %', current_database();
    END IF;
    IF NOT EXISTS (
        SELECT 1 FROM public.system_menu
        WHERE id = 2931 AND name = '产品管理' AND type = 2 AND deleted = 0
    ) THEN
        RAISE EXCEPTION 'TD005_PERMISSION_PARENT_MISMATCH: expected system_menu id=2931';
    END IF;

    SELECT count(*) INTO existing_permissions
    FROM public.system_menu
    WHERE permission IN (
        'power:model-template:read', 'power:model-template:edit',
        'power:model-template:publish', 'power:model-template:import',
        'power:model-template:upgrade', 'power:model-template:retire',
        'power:system-template:manage'
    );

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
    ) AND type = 3 AND parent_id = 2931 AND status = 0 AND deleted = 0
      AND creator = 'td005-seed';

    IF existing_permissions = 7 AND exact_permissions = 7 THEN
        RETURN;
    END IF;
    IF existing_permissions <> 0 THEN
        RAISE EXCEPTION 'TD005_PERMISSION_PARTIAL_OR_DRIFTED: existing=% exact=%',
            existing_permissions, exact_permissions;
    END IF;

    SELECT count(*) INTO occupied_ids
    FROM public.system_menu WHERE id BETWEEN 3900 AND 3906;
    IF occupied_ids <> 0 THEN
        RAISE EXCEPTION 'TD005_PERMISSION_ID_OCCUPIED: count=%', occupied_ids;
    END IF;
END
$guard$;

INSERT INTO public.system_menu (
    id, name, permission, type, sort, parent_id, path, icon, component, component_name,
    status, visible, keep_alive, always_show, creator, create_time, updater, update_time, deleted
)
SELECT id, name, permission, 3, sort, 2931, '', '', '', NULL,
       0, true, true, true, 'td005-seed', CURRENT_TIMESTAMP,
       'td005-seed', CURRENT_TIMESTAMP, 0
FROM (VALUES
    (3900::bigint, '电力物模型模板读取', 'power:model-template:read', 90),
    (3901::bigint, '电力物模型模板编辑', 'power:model-template:edit', 91),
    (3902::bigint, '电力物模型模板发布', 'power:model-template:publish', 92),
    (3903::bigint, '电力物模型模板导入', 'power:model-template:import', 93),
    (3904::bigint, '电力物模型模板升级', 'power:model-template:upgrade', 94),
    (3905::bigint, '电力物模型模板退役', 'power:model-template:retire', 95),
    (3906::bigint, '系统物模型模板管理', 'power:system-template:manage', 96)
) AS candidate(id, name, permission, sort)
WHERE NOT EXISTS (
    SELECT 1 FROM public.system_menu existing
    WHERE existing.permission = candidate.permission
);

DO $verify$
BEGIN
    IF (SELECT count(*) FROM public.system_menu
        WHERE permission IN (
            'power:model-template:read', 'power:model-template:edit',
            'power:model-template:publish', 'power:model-template:import',
            'power:model-template:upgrade', 'power:model-template:retire',
            'power:system-template:manage'
        )) <> 7 THEN
        RAISE EXCEPTION 'TD005_PERMISSION_VERIFY_DUPLICATE';
    END IF;
    IF (SELECT count(*) FROM public.system_menu
        WHERE (id, permission, name, sort) IN (
            (3900, 'power:model-template:read', '电力物模型模板读取', 90),
            (3901, 'power:model-template:edit', '电力物模型模板编辑', 91),
            (3902, 'power:model-template:publish', '电力物模型模板发布', 92),
            (3903, 'power:model-template:import', '电力物模型模板导入', 93),
            (3904, 'power:model-template:upgrade', '电力物模型模板升级', 94),
            (3905, 'power:model-template:retire', '电力物模型模板退役', 95),
            (3906, 'power:system-template:manage', '系统物模型模板管理', 96)
        ) AND creator = 'td005-seed' AND type = 3 AND parent_id = 2931
          AND status = 0 AND deleted = 0) <> 7 THEN
        RAISE EXCEPTION 'TD005_PERMISSION_VERIFY_FAILED';
    END IF;
END
$verify$;
