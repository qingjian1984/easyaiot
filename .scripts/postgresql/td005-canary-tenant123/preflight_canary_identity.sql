-- TD-005 1.0.44 tenant 123 Canary 身份与最小权限只读前检；目标库：ruoyi-vue-pro20。
\set ON_ERROR_STOP on

BEGIN TRANSACTION READ ONLY;

DO $guard$
DECLARE
    allowed_links integer;
    forbidden_links integer;
BEGIN
    IF current_database() <> 'ruoyi-vue-pro20' THEN
        RAISE EXCEPTION 'TD005_CANARY_IDENTITY_WRONG_DATABASE: %', current_database();
    END IF;
    IF NOT EXISTS (
        SELECT 1 FROM public.system_tenant
        WHERE id = 123 AND name = 'codex测试' AND status = 0 AND deleted = 0
    ) THEN
        RAISE EXCEPTION 'TD005_CANARY_TENANT_MISMATCH';
    END IF;
    IF NOT EXISTS (
        SELECT 1 FROM public.system_role
        WHERE id = 112 AND tenant_id = 123 AND name = '租户管理员'
          AND code = 'tenant_admin' AND status = 0 AND deleted = 0
    ) THEN
        RAISE EXCEPTION 'TD005_CANARY_ROLE_MISMATCH';
    END IF;
    IF NOT EXISTS (
        SELECT 1 FROM public.system_users
        WHERE id = 132 AND tenant_id = 123 AND username = 'aotemane'
          AND status = 0 AND deleted = 0
    ) THEN
        RAISE EXCEPTION 'TD005_CANARY_USER_MISMATCH';
    END IF;
    IF (SELECT count(*) FROM public.system_user_role
        WHERE tenant_id = 123 AND user_id = 132 AND role_id = 112 AND deleted = 0) <> 1 THEN
        RAISE EXCEPTION 'TD005_CANARY_USER_ROLE_MISMATCH';
    END IF;
    IF (SELECT count(*) FROM public.system_menu
        WHERE (id, permission) IN (
            (3900, 'power:model-template:read'),
            (3901, 'power:model-template:edit'),
            (3902, 'power:model-template:publish')
        ) AND type = 3 AND status = 0 AND deleted = 0) <> 3 THEN
        RAISE EXCEPTION 'TD005_CANARY_PERMISSION_DEFINITION_MISMATCH';
    END IF;

    SELECT count(*) INTO allowed_links
    FROM public.system_role_menu
    WHERE tenant_id = 123 AND role_id = 112 AND menu_id IN (3900, 3901, 3902)
      AND creator = 'td005-canary-grant-role112' AND deleted = 0;
    SELECT count(*) INTO forbidden_links
    FROM public.system_role_menu
    WHERE tenant_id = 123 AND role_id = 112 AND menu_id IN (3903, 3904, 3905, 3906)
      AND deleted = 0;
    IF allowed_links <> 3 OR forbidden_links <> 0 THEN
        RAISE EXCEPTION 'TD005_CANARY_ROLE_PERMISSION_DRIFT: allowed=% forbidden=%',
            allowed_links, forbidden_links;
    END IF;
END
$guard$;

SELECT 123 AS tenant_id, 132 AS user_id, 112 AS role_id, 3 AS allowed_links, 0 AS forbidden_links;

ROLLBACK;
