-- TD-005 1.0.25 canary 角色授权精确回滚候选；目标库：ruoyi-vue-pro20。
-- 执行器必须使用 psql -1 -v ON_ERROR_STOP=1。

SET LOCAL lock_timeout = '5s';
LOCK TABLE public.system_role_menu IN SHARE ROW EXCLUSIVE MODE;

DO $guard$
DECLARE
    exact_links integer;
    all_links integer;
BEGIN
    IF current_database() <> 'ruoyi-vue-pro20' THEN
        RAISE EXCEPTION 'TD005_CANARY_ROLE_WRONG_DATABASE: %', current_database();
    END IF;
    SELECT count(*) INTO exact_links
    FROM public.system_role_menu
    WHERE role_id = 111 AND tenant_id = 122 AND menu_id IN (3900, 3901, 3902)
      AND creator = 'td005-canary-grant' AND deleted = 0;
    SELECT count(*) INTO all_links
    FROM public.system_role_menu
    WHERE role_id = 111 AND menu_id BETWEEN 3900 AND 3906 AND deleted = 0;
    IF exact_links = 0 AND all_links = 0 THEN
        RETURN;
    END IF;
    IF exact_links <> 3 OR all_links <> 3 THEN
        RAISE EXCEPTION 'TD005_CANARY_ROLE_ROLLBACK_DRIFT: exact=% all=%',
            exact_links, all_links;
    END IF;
END
$guard$;

DELETE FROM public.system_role_menu
WHERE role_id = 111 AND tenant_id = 122 AND menu_id IN (3900, 3901, 3902)
  AND creator = 'td005-canary-grant' AND deleted = 0;
