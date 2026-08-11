-- TD-005 1.0.25 canary 角色只读前检；目标库：ruoyi-vue-pro20。
\set ON_ERROR_STOP on

BEGIN TRANSACTION READ ONLY;

DO $guard$
DECLARE
    active_users integer;
    existing_links integer;
    exact_links integer;
BEGIN
    IF current_database() <> 'ruoyi-vue-pro20' THEN
        RAISE EXCEPTION 'TD005_CANARY_ROLE_WRONG_DATABASE: %', current_database();
    END IF;
    IF NOT EXISTS (
        SELECT 1 FROM public.system_tenant
        WHERE id = 122 AND name = '测试租户' AND status = 0 AND deleted = 0
    ) THEN
        RAISE EXCEPTION 'TD005_CANARY_TENANT_MISMATCH';
    END IF;
    IF NOT EXISTS (
        SELECT 1 FROM public.system_role
        WHERE id = 111 AND tenant_id = 122 AND name = '租户管理员'
          AND code = 'tenant_admin' AND status = 0 AND deleted = 0
    ) THEN
        RAISE EXCEPTION 'TD005_CANARY_ROLE_MISMATCH';
    END IF;
    IF (SELECT count(*) FROM public.system_menu
        WHERE (id, permission) IN (
            (3900, 'power:model-template:read'),
            (3901, 'power:model-template:edit'),
            (3902, 'power:model-template:publish')
        ) AND type = 3 AND status = 0 AND deleted = 0) <> 3 THEN
        RAISE EXCEPTION 'TD005_CANARY_PERMISSION_MISMATCH';
    END IF;

    SELECT count(DISTINCT u.id) INTO active_users
    FROM public.system_user_role ur
    JOIN public.system_users u ON u.id = ur.user_id AND u.tenant_id = 122
    WHERE ur.role_id = 111 AND ur.tenant_id = 122 AND ur.deleted = 0
      AND u.status = 0 AND u.deleted = 0;
    IF active_users <> 1 THEN
        RAISE EXCEPTION 'TD005_CANARY_ROLE_USER_COUNT_MISMATCH: %', active_users;
    END IF;

    SELECT count(*) INTO existing_links
    FROM public.system_role_menu
    WHERE role_id = 111 AND menu_id BETWEEN 3900 AND 3906 AND deleted = 0;
    SELECT count(*) INTO exact_links
    FROM public.system_role_menu
    WHERE role_id = 111 AND tenant_id = 122 AND menu_id IN (3900, 3901, 3902)
      AND creator = 'td005-canary-grant' AND deleted = 0;
    IF existing_links NOT IN (0, 3)
            OR (existing_links = 3 AND exact_links <> 3) THEN
        RAISE EXCEPTION 'TD005_CANARY_ROLE_LINK_DRIFT: existing=% exact=%',
            existing_links, exact_links;
    END IF;
END
$guard$;

SELECT 122 AS tenant_id, 111 AS role_id,
       (SELECT count(*) FROM public.system_role_menu
        WHERE role_id = 111 AND menu_id BETWEEN 3900 AND 3906 AND deleted = 0) AS existing_td005_links,
       (SELECT count(DISTINCT u.id) FROM public.system_user_role ur
        JOIN public.system_users u ON u.id = ur.user_id AND u.tenant_id = 122
        WHERE ur.role_id = 111 AND ur.tenant_id = 122 AND ur.deleted = 0
          AND u.status = 0 AND u.deleted = 0) AS active_user_count;

ROLLBACK;
