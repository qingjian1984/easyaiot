-- TD-005 1.0.25 canary 角色最小授权候选；目标库：ruoyi-vue-pro20。
-- 执行器必须使用 psql -1 -v ON_ERROR_STOP=1；只授权 read/edit/publish。

SET LOCAL lock_timeout = '5s';
LOCK TABLE public.system_role_menu IN SHARE ROW EXCLUSIVE MODE;

DO $guard$
DECLARE
    existing_links integer;
    exact_links integer;
BEGIN
    IF current_database() <> 'ruoyi-vue-pro20' THEN
        RAISE EXCEPTION 'TD005_CANARY_ROLE_WRONG_DATABASE: %', current_database();
    END IF;
    IF NOT EXISTS (
        SELECT 1 FROM public.system_role
        WHERE id = 111 AND tenant_id = 122 AND code = 'tenant_admin'
          AND status = 0 AND deleted = 0
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

    SELECT count(*) INTO existing_links
    FROM public.system_role_menu
    WHERE role_id = 111 AND menu_id BETWEEN 3900 AND 3906 AND deleted = 0;
    SELECT count(*) INTO exact_links
    FROM public.system_role_menu
    WHERE role_id = 111 AND tenant_id = 122 AND menu_id IN (3900, 3901, 3902)
      AND creator = 'td005-canary-grant' AND deleted = 0;
    IF existing_links = 3 AND exact_links = 3 THEN
        RETURN;
    END IF;
    IF existing_links <> 0 THEN
        RAISE EXCEPTION 'TD005_CANARY_ROLE_LINK_DRIFT: existing=% exact=%',
            existing_links, exact_links;
    END IF;
END
$guard$;

INSERT INTO public.system_role_menu (
    id, role_id, menu_id, creator, create_time, updater, update_time, deleted, tenant_id
)
SELECT nextval('public.system_role_menu_seq'), 111, menu_id,
       'td005-canary-grant', CURRENT_TIMESTAMP,
       'td005-canary-grant', CURRENT_TIMESTAMP, 0, 122
FROM (VALUES (3900::bigint), (3901::bigint), (3902::bigint)) AS candidate(menu_id)
WHERE NOT EXISTS (
    SELECT 1 FROM public.system_role_menu existing
    WHERE existing.role_id = 111 AND existing.menu_id = candidate.menu_id
      AND existing.deleted = 0
);

DO $verify$
BEGIN
    IF (SELECT count(*) FROM public.system_role_menu
        WHERE role_id = 111 AND tenant_id = 122 AND menu_id IN (3900, 3901, 3902)
          AND creator = 'td005-canary-grant' AND deleted = 0) <> 3 THEN
        RAISE EXCEPTION 'TD005_CANARY_ROLE_VERIFY_FAILED';
    END IF;
    IF EXISTS (
        SELECT 1 FROM public.system_role_menu
        WHERE role_id = 111 AND menu_id IN (3903, 3904, 3905, 3906) AND deleted = 0
    ) THEN
        RAISE EXCEPTION 'TD005_CANARY_ROLE_FORBIDDEN_PERMISSION';
    END IF;
END
$verify$;
