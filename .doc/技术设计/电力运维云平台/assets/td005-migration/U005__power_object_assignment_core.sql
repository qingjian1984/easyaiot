-- TD-004 U005：V006 核心五表空表卸载入口；不得用于日常应用回滚。
BEGIN;

DO $$
DECLARE
    relation_name TEXT;
    has_rows BOOLEAN;
BEGIN
    FOREACH relation_name IN ARRAY ARRAY[
        'power_device_assignment', 'power_device_asset', 'power_circuit',
        'power_space_node', 'power_site'
    ] LOOP
        IF to_regclass('public.' || relation_name) IS NOT NULL THEN
            EXECUTE format('SELECT EXISTS (SELECT 1 FROM public.%I LIMIT 1)', relation_name)
                INTO has_rows;
            IF has_rows THEN
                RAISE EXCEPTION 'U005 refused: % is not empty', relation_name;
            END IF;
        END IF;
    END LOOP;
END
$$;

DROP TABLE IF EXISTS public.power_device_assignment;
DROP TABLE IF EXISTS public.power_device_asset;
DROP TABLE IF EXISTS public.power_circuit;
DROP TABLE IF EXISTS public.power_space_node;
DROP TABLE IF EXISTS public.power_site;
DROP FUNCTION IF EXISTS public.fn_power_device_asset_tenant_guard();
DROP FUNCTION IF EXISTS public.fn_power_object_identity_guard();
DROP FUNCTION IF EXISTS public.fn_power_device_assignment_history_guard();
COMMIT;
