-- ADR-015 V004 卸载候选：只允许空表卸载；不接入普通业务路径。
DO $block$
BEGIN
    IF EXISTS (SELECT 1 FROM public.collector_workload_binding_projection LIMIT 1) THEN
        RAISE EXCEPTION 'COLLECTOR_PROJECTION_UNINSTALL_NON_EMPTY';
    END IF;
END;
$block$;

DROP TABLE public.collector_workload_binding_projection;
DROP FUNCTION public.fn_collector_workload_binding_projection_guard();
