-- TD-005 1.0.23 权限 seed 落库后只读验收；目标库：ruoyi-vue-pro20。
\set ON_ERROR_STOP on

BEGIN TRANSACTION READ ONLY;

DO $verify$
DECLARE
    exact_permissions integer;
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
    ) AND type = 3 AND parent_id = 2931 AND status = 0 AND deleted = 0
      AND creator = 'td005-seed';
    IF exact_permissions <> 7 THEN
        RAISE EXCEPTION 'TD005_PERMISSION_VERIFY_FAILED: exact=%', exact_permissions;
    END IF;

    SELECT count(*) INTO active_role_links
    FROM public.system_role_menu
    WHERE menu_id BETWEEN 3900 AND 3906 AND deleted = 0;
    IF active_role_links <> 0 THEN
        RAISE EXCEPTION 'TD005_PERMISSION_UNAPPROVED_ROLE_GRANT: count=%', active_role_links;
    END IF;
END
$verify$;

SELECT id, name, permission, type, sort, parent_id, status, creator, create_time
FROM public.system_menu
WHERE id BETWEEN 3900 AND 3906
ORDER BY id;

ROLLBACK;
