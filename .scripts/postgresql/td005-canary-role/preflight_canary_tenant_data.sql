-- TD-005 1.0.25 canary 租户业务事实只读前检；目标库：iot-device20。
\set ON_ERROR_STOP on

BEGIN TRANSACTION READ ONLY;

DO $guard$
DECLARE
    residual_rows bigint;
BEGIN
    IF current_database() <> 'iot-device20' THEN
        RAISE EXCEPTION 'TD005_CANARY_DATA_WRONG_DATABASE: %', current_database();
    END IF;
    SELECT
        (SELECT count(*) FROM public.product WHERE tenant_id = 122) +
        (SELECT count(*) FROM public.device WHERE tenant_id = 122 AND COALESCE(deleted, 0) = 0) +
        (SELECT count(*) FROM public.power_model_template WHERE tenant_id = 122) +
        (SELECT count(*) FROM public.power_model_template_version WHERE tenant_id = 122) +
        (SELECT count(*) FROM public.power_model_member_index WHERE tenant_id = 122) +
        (SELECT count(*) FROM public.power_product_model_binding WHERE tenant_id = 122) +
        (SELECT count(*) FROM public.power_model_audit WHERE tenant_id = 122) +
        (SELECT count(*) FROM public.power_model_release_outbox WHERE tenant_id = 122) +
        (SELECT count(*) FROM public.power_model_event_inbox WHERE tenant_id = 122) +
        (SELECT count(*) FROM public.iot_collector_config_release WHERE tenant_id = 122) +
        (SELECT count(*) FROM public.power_model_template_reference_mark WHERE tenant_id = 122) +
        (SELECT count(*) FROM public.power_model_coordination_audit WHERE tenant_id = 122) +
        (SELECT count(*) FROM public.collector_workload_binding_projection WHERE tenant_id = 122) +
        (SELECT count(*) FROM public.power_idempotency_record WHERE tenant_id = 122)
    INTO residual_rows;
    IF residual_rows <> 0 THEN
        RAISE EXCEPTION 'TD005_CANARY_TENANT_NOT_EMPTY: %', residual_rows;
    END IF;
END
$guard$;

SELECT 122 AS tenant_id, 0 AS residual_rows;

ROLLBACK;
