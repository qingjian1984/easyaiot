-- TD-005 1.0.25 canary 角色授权后只读验收；目标库：ruoyi-vue-pro20。
\set ON_ERROR_STOP on

BEGIN TRANSACTION READ ONLY;

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

SELECT rm.role_id, rm.tenant_id, rm.menu_id, m.permission, rm.creator
FROM public.system_role_menu rm
JOIN public.system_menu m ON m.id = rm.menu_id
WHERE rm.role_id = 111 AND rm.menu_id BETWEEN 3900 AND 3906 AND rm.deleted = 0
ORDER BY rm.menu_id;

ROLLBACK;
