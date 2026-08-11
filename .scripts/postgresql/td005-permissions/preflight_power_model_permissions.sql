-- TD-005 1.0.23 权限 seed 只读前检；目标库：ruoyi-vue-pro20。
\set ON_ERROR_STOP on

BEGIN TRANSACTION READ ONLY;

DO $guard$
DECLARE
    existing_permissions integer;
    exact_seed_permissions integer;
    occupied_ids integer;
BEGIN
    IF current_database() <> 'ruoyi-vue-pro20' THEN
        RAISE EXCEPTION 'TD005_PERMISSION_WRONG_DATABASE: %', current_database();
    END IF;
    IF NOT EXISTS (
        SELECT 1 FROM public.system_menu
        WHERE id = 2931 AND name = '产品管理' AND type = 2
          AND status = 0 AND deleted = 0
    ) THEN
        RAISE EXCEPTION 'TD005_PERMISSION_PARENT_MISMATCH: expected active system_menu id=2931';
    END IF;

    SELECT count(*) INTO existing_permissions
    FROM public.system_menu
    WHERE permission IN (
        'power:model-template:read', 'power:model-template:edit',
        'power:model-template:publish', 'power:model-template:import',
        'power:model-template:upgrade', 'power:model-template:retire',
        'power:system-template:manage'
    );

    SELECT count(*) INTO exact_seed_permissions
    FROM public.system_menu
    WHERE (id, permission, name) IN (
        (3900, 'power:model-template:read', '电力物模型模板读取'),
        (3901, 'power:model-template:edit', '电力物模型模板编辑'),
        (3902, 'power:model-template:publish', '电力物模型模板发布'),
        (3903, 'power:model-template:import', '电力物模型模板导入'),
        (3904, 'power:model-template:upgrade', '电力物模型模板升级'),
        (3905, 'power:model-template:retire', '电力物模型模板退役'),
        (3906, 'power:system-template:manage', '系统物模型模板管理')
    ) AND type = 3 AND parent_id = 2931 AND status = 0 AND deleted = 0
      AND creator = 'td005-seed';

    IF existing_permissions NOT IN (0, 7)
            OR (existing_permissions = 7 AND exact_seed_permissions <> 7) THEN
        RAISE EXCEPTION 'TD005_PERMISSION_PARTIAL_OR_DRIFTED: existing=% exact_seed=%',
            existing_permissions, exact_seed_permissions;
    END IF;

    SELECT count(*) INTO occupied_ids
    FROM public.system_menu
    WHERE id BETWEEN 3900 AND 3906;
    IF existing_permissions = 0 AND occupied_ids <> 0 THEN
        RAISE EXCEPTION 'TD005_PERMISSION_ID_OCCUPIED: count=%', occupied_ids;
    END IF;
END
$guard$;

SELECT current_database() AS database_name,
       current_setting('server_version') AS server_version,
       (SELECT count(*) FROM public.system_menu WHERE id = 2931 AND deleted = 0) AS parent_count,
       (SELECT count(*) FROM public.system_menu
        WHERE permission LIKE 'power:model-template:%'
           OR permission = 'power:system-template:manage') AS frozen_permission_count,
       (SELECT count(*) FROM public.system_menu WHERE id BETWEEN 3900 AND 3906) AS candidate_id_count,
       (SELECT count(*) FROM public.system_role_menu
        WHERE menu_id BETWEEN 3900 AND 3906 AND deleted = 0) AS active_role_link_count;

ROLLBACK;
