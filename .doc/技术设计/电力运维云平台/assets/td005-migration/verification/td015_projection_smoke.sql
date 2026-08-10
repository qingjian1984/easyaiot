-- ADR-015 / V004 临时评审库语义烟测；只写 fixture，末尾整体回滚。
BEGIN;

INSERT INTO public.iot_collector_config_release (
    id, tenant_id, site_id, site_code, workload_id, node_id, config_version,
    schema_version, canonicalization_version, payload_canonical, payload,
    payload_sha256, canonical_length_bytes, status, published_by, published_at
) VALUES (
    910001, 910, 920, 'site-920', 'collector-site-920-a', 930, 1,
    '1.0', 'jcs-rfc8785-v1', '{}', '{}'::jsonb,
    repeat('a', 64), 2, 'PUBLISHED', 940, CURRENT_TIMESTAMP
);

INSERT INTO public.collector_workload_binding_projection (
    id, tenant_id, workload_id, site_id, site_code, node_id, product_id,
    template_code, template_version, binding_revision, config_version,
    release_id, projection_revision, lifecycle_status
) VALUES (
    950001, 910, 'collector-site-920-a', 920, 'site-920', 930, 960,
    'meter-standard', '1.0.0', 1, 1, 910001, 1, 'ACTIVE'
);

-- 更高 revision 可推进 STOPPED。
UPDATE public.collector_workload_binding_projection
SET lifecycle_status = 'STOPPED', projection_revision = 2,
    last_synced_at = CURRENT_TIMESTAMP, updated_at = CURRENT_TIMESTAMP
WHERE tenant_id = 910 AND workload_id = 'collector-site-920-a';

DO $block$
BEGIN
    BEGIN
        UPDATE public.collector_workload_binding_projection
        SET lifecycle_status = 'ACTIVE', projection_revision = 1
        WHERE tenant_id = 910 AND workload_id = 'collector-site-920-a';
        RAISE EXCEPTION 'SMOKE_EXPECTED_STALE_REJECTION';
    EXCEPTION WHEN OTHERS THEN
        IF SQLERRM NOT LIKE '%COLLECTOR_PROJECTION_REVISION_STALE%' THEN
            RAISE;
        END IF;
    END;

    BEGIN
        UPDATE public.collector_workload_binding_projection
        SET lifecycle_status = 'ACTIVE', projection_revision = 2
        WHERE tenant_id = 910 AND workload_id = 'collector-site-920-a';
        RAISE EXCEPTION 'SMOKE_EXPECTED_SAME_REVISION_REJECTION';
    EXCEPTION WHEN OTHERS THEN
        IF SQLERRM NOT LIKE '%COLLECTOR_PROJECTION_SAME_REVISION_DRIFT%' THEN
            RAISE;
        END IF;
    END;

    BEGIN
        UPDATE public.collector_workload_binding_projection
        SET tenant_id = 911, projection_revision = 3
        WHERE tenant_id = 910 AND workload_id = 'collector-site-920-a';
        RAISE EXCEPTION 'SMOKE_EXPECTED_IDENTITY_REJECTION';
    EXCEPTION WHEN OTHERS THEN
        IF SQLERRM NOT LIKE '%COLLECTOR_PROJECTION_IDENTITY_IMMUTABLE%' THEN
            RAISE;
        END IF;
    END;

    BEGIN
        INSERT INTO public.collector_workload_binding_projection (
            id, tenant_id, workload_id, site_id, site_code, node_id, product_id,
            template_code, template_version, binding_revision, config_version,
            release_id, projection_revision, lifecycle_status
        ) VALUES (
            950002, 911, 'collector-invalid-fk', 920, 'site-920', 930, 960,
            'meter-standard', '1.0.0', 1, 1, 910001, 1, 'ACTIVE'
        );
        RAISE EXCEPTION 'SMOKE_EXPECTED_TENANT_FK_REJECTION';
    EXCEPTION WHEN foreign_key_violation THEN
        NULL;
    END;

    BEGIN
        INSERT INTO public.collector_workload_binding_projection (
            id, tenant_id, workload_id, site_id, site_code, node_id, product_id,
            template_code, template_version, binding_revision, config_version,
            release_id, projection_revision, lifecycle_status
        ) VALUES (
            950003, 910, ' ', 920, 'site-920', 930, 960,
            'meter-standard', '1.0.0', 1, 1, 910001, 1, 'ACTIVE'
        );
        RAISE EXCEPTION 'SMOKE_EXPECTED_TEXT_REJECTION';
    EXCEPTION WHEN check_violation THEN
        NULL;
    END;
END;
$block$;

DO $block$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM public.collector_workload_binding_projection
        WHERE tenant_id = 910 AND workload_id = 'collector-site-920-a'
          AND lifecycle_status = 'STOPPED' AND projection_revision = 2
    ) THEN
        RAISE EXCEPTION 'SMOKE_FINAL_STATE_MISMATCH';
    END IF;
END;
$block$;

ROLLBACK;
SELECT 'TD015_V004_SMOKE_PASS' AS result;
