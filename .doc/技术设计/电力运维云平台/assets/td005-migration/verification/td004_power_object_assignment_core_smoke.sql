\set ON_ERROR_STOP on

BEGIN;

INSERT INTO public.power_site
    (id, tenant_id, site_code, site_name, owner_dept_id, iana_time_zone, status,
     version, created_by, updated_by)
VALUES
    (94006001, 1, 'plant-a', '评审站点 A', 1, 'Asia/Shanghai', 'ACTIVE', 1, 1, 1),
    (94006002, 2, 'plant-b', '评审站点 B', 2, 'Asia/Shanghai', 'ACTIVE', 1, 2, 2);

INSERT INTO public.power_space_node
    (id, tenant_id, site_id, parent_id, space_code, space_type, space_name,
     sort_order, status, version, created_by, updated_by)
VALUES
    (94006101, 1, 94006001, NULL, 'room-a', 'distribution-room', '配电房 A',
     0, 'ACTIVE', 1, 1, 1);

INSERT INTO public.power_circuit
    (id, tenant_id, site_id, parent_id, circuit_code, circuit_name, circuit_type,
     sort_order, status, version, created_by, updated_by)
VALUES
    (94006201, 1, 94006001, NULL, 'line-a', '进线 A', 'incoming',
     0, 'ACTIVE', 1, 1, 1);

-- 复用目标镜像库中的 tenant=1/device=920002，仅在事务内创建电力身份。
INSERT INTO public.power_device_asset
    (id, tenant_id, device_id, asset_code, object_type, status, version, created_by, updated_by)
VALUES
    (94006301, 1, 920002, 'meter-a', 'meter', 'ACTIVE', 1, 1, 1),
    (94006303, 1, 920001, 'meter-b', 'meter', 'ACTIVE', 1, 1, 1);

INSERT INTO public.power_device_assignment
    (id, tenant_id, device_id, site_id, primary_space_id, primary_circuit_id,
     valid_from, valid_to, change_reason, version, created_by, updated_by)
VALUES
    (94006401, 1, 920002, 94006001, 94006101, 94006201,
     '2026-08-10T00:00:00Z', NULL, 'V006 smoke', 1, 1, 1);

DO $$
BEGIN
    BEGIN
        INSERT INTO public.power_device_asset
            (id, tenant_id, device_id, asset_code, object_type, status,
             version, created_by, updated_by)
        VALUES (94006302, 2, 920002, 'cross-tenant', 'meter', 'ACTIVE', 1, 2, 2);
        RAISE EXCEPTION 'SMOKE_EXPECTED_FAILURE_NOT_RAISED';
    EXCEPTION WHEN OTHERS THEN
        IF SQLERRM = 'SMOKE_EXPECTED_FAILURE_NOT_RAISED'
           OR SQLERRM NOT LIKE '%POWER_DEVICE_TENANT_MISMATCH%' THEN RAISE; END IF;
    END;

    BEGIN
        INSERT INTO public.power_device_assignment
            (id, tenant_id, device_id, site_id, valid_from, change_reason,
             version, created_by, updated_by)
        VALUES (94006402, 1, 920002, 94006001, '2026-08-10T01:00:00Z',
                'duplicate current', 1, 1, 1);
        RAISE EXCEPTION 'expected current assignment uniqueness rejection';
    EXCEPTION WHEN unique_violation THEN NULL;
    END;

    BEGIN
        INSERT INTO public.power_device_assignment
            (id, tenant_id, device_id, site_id, primary_space_id, valid_from,
             change_reason, version, created_by, updated_by)
        VALUES (94006403, 1, 920001, 94006001, 99999999, '2026-08-10T01:00:00Z',
                'foreign space', 1, 1, 1);
        RAISE EXCEPTION 'expected same-site space rejection';
    EXCEPTION WHEN foreign_key_violation THEN NULL;
    END;

    BEGIN
        UPDATE public.power_site SET site_code='plant-renamed' WHERE id=94006001;
        RAISE EXCEPTION 'SMOKE_EXPECTED_FAILURE_NOT_RAISED';
    EXCEPTION WHEN OTHERS THEN
        IF SQLERRM = 'SMOKE_EXPECTED_FAILURE_NOT_RAISED'
           OR SQLERRM NOT LIKE '%POWER_SITE_IDENTITY_IMMUTABLE%' THEN RAISE; END IF;
    END;

    BEGIN
        UPDATE public.power_device_assignment
        SET site_id=94006002, version=2, updated_by=1, updated_at=clock_timestamp()
        WHERE id=94006401;
        RAISE EXCEPTION 'SMOKE_EXPECTED_FAILURE_NOT_RAISED';
    EXCEPTION WHEN OTHERS THEN
        IF SQLERRM = 'SMOKE_EXPECTED_FAILURE_NOT_RAISED'
           OR SQLERRM NOT LIKE '%POWER_ASSIGNMENT_HISTORY_IMMUTABLE%' THEN RAISE; END IF;
    END;

    BEGIN
        UPDATE public.power_device_assignment
        SET valid_to='2026-08-10T02:00:00Z', updated_by=1, updated_at=clock_timestamp()
        WHERE id=94006401;
        RAISE EXCEPTION 'SMOKE_EXPECTED_FAILURE_NOT_RAISED';
    EXCEPTION WHEN OTHERS THEN
        IF SQLERRM = 'SMOKE_EXPECTED_FAILURE_NOT_RAISED'
           OR SQLERRM NOT LIKE '%POWER_ASSIGNMENT_VERSION_CONFLICT%' THEN RAISE; END IF;
    END;
END
$$;

-- 合法变更只能关闭当前行并以更高的新行表达当前归属，不得覆写历史。
UPDATE public.power_device_assignment
SET valid_to='2026-08-10T02:00:00Z', version=2,
    updated_by=1, updated_at=clock_timestamp()
WHERE id=94006401;

INSERT INTO public.power_device_assignment
    (id, tenant_id, device_id, site_id, primary_space_id, primary_circuit_id,
     valid_from, valid_to, change_reason, version, created_by, updated_by)
VALUES
    (94006404, 1, 920002, 94006001, 94006101, 94006201,
     '2026-08-10T02:00:00Z', NULL, 'V006 smoke reassignment', 1, 1, 1);

DO $$
BEGIN
    BEGIN
        UPDATE public.power_device_assignment
        SET valid_to='2026-08-10T03:00:00Z', version=3,
            updated_by=1, updated_at=clock_timestamp()
        WHERE id=94006401;
        RAISE EXCEPTION 'SMOKE_EXPECTED_FAILURE_NOT_RAISED';
    EXCEPTION WHEN OTHERS THEN
        IF SQLERRM = 'SMOKE_EXPECTED_FAILURE_NOT_RAISED'
           OR SQLERRM NOT LIKE '%POWER_ASSIGNMENT_INVALID_CLOSE_TRANSITION%' THEN RAISE; END IF;
    END;
END
$$;

DO $$
DECLARE
    ready_count INTEGER;
BEGIN
    SELECT count(*) INTO ready_count
    FROM public.power_device_assignment a
    JOIN public.power_device_asset da
      ON da.tenant_id=a.tenant_id AND da.device_id=a.device_id
    JOIN public.device d
      ON d.id=a.device_id AND d.tenant_id=a.tenant_id
    JOIN public.power_site s
      ON s.tenant_id=a.tenant_id AND s.id=a.site_id
    WHERE a.tenant_id=1 AND a.valid_to IS NULL
      AND da.status='ACTIVE' AND s.status='ACTIVE' AND d.deleted=0;
    IF ready_count <> 1 THEN
        RAISE EXCEPTION 'expected exactly one READY collector object fact, actual=%', ready_count;
    END IF;
END
$$;

ROLLBACK;

SELECT 'TD004_CORE_SMOKE_PASS' AS result;
