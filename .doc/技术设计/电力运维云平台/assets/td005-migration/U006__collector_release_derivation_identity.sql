-- TD-001 V007 卸载候选；禁止直接执行，须经 ADR-013 独立批准。
-- 仅允许空发布单表卸载，避免丢失发布审计身份。

DO $guard$
BEGIN
    IF EXISTS (SELECT 1 FROM public.iot_collector_config_release LIMIT 1) THEN
        RAISE EXCEPTION 'U006_REFUSED_RELEASE_NOT_EMPTY';
    END IF;
END
$guard$;

DROP INDEX IF EXISTS public.idx_iot_collector_config_release_derivation;

ALTER TABLE public.iot_collector_config_release
    DROP CONSTRAINT IF EXISTS fk_iot_collector_config_release_source_event,
    DROP CONSTRAINT IF EXISTS fk_iot_collector_config_release_binding,
    DROP CONSTRAINT IF EXISTS uq_iot_collector_config_release_source_event,
    DROP CONSTRAINT IF EXISTS ck_iot_collector_config_release_derivation_positive,
    DROP CONSTRAINT IF EXISTS ck_iot_collector_config_release_derivation_text,
    DROP COLUMN IF EXISTS source_reason_code,
    DROP COLUMN IF EXISTS source_event_id,
    DROP COLUMN IF EXISTS binding_revision,
    DROP COLUMN IF EXISTS template_version,
    DROP COLUMN IF EXISTS template_code,
    DROP COLUMN IF EXISTS product_id;

ALTER TABLE public.power_model_release_outbox
    DROP CONSTRAINT IF EXISTS uq_power_model_release_outbox_tenant_event;

CREATE OR REPLACE FUNCTION public.fn_iot_collector_config_release_immutable()
RETURNS trigger
LANGUAGE plpgsql
AS $$
BEGIN
    IF TG_OP = 'UPDATE' AND (
        NEW.id IS DISTINCT FROM OLD.id
        OR NEW.tenant_id IS DISTINCT FROM OLD.tenant_id
        OR NEW.site_id IS DISTINCT FROM OLD.site_id
        OR NEW.site_code IS DISTINCT FROM OLD.site_code
        OR NEW.workload_id IS DISTINCT FROM OLD.workload_id
        OR NEW.config_version IS DISTINCT FROM OLD.config_version
        OR NEW.schema_version IS DISTINCT FROM OLD.schema_version
        OR NEW.canonicalization_version IS DISTINCT FROM OLD.canonicalization_version
        OR NEW.payload_canonical IS DISTINCT FROM OLD.payload_canonical
        OR NEW.payload IS DISTINCT FROM OLD.payload
        OR NEW.payload_sha256 IS DISTINCT FROM OLD.payload_sha256
        OR NEW.canonical_length_bytes IS DISTINCT FROM OLD.canonical_length_bytes
    ) THEN
        RAISE EXCEPTION 'iot_collector_config_release snapshot fields are immutable';
    END IF;
    RETURN NEW;
END;
$$;

COMMENT ON FUNCTION public.fn_iot_collector_config_release_immutable() IS
    '保护 collector 发布单快照字段不可原地修改；仅允许受控状态与应用结果演进';
