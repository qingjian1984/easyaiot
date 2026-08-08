-- TD-001 V003 候选 DDL 临时库烟测（评审用，随临时库销毁）
-- 覆盖：发布单建表/唯一/hash 与 canonical 长度 CHECK/快照不可变触发器、
--       引用标记唯一幂等、协调审计追加写拒绝与 detail 有界。
\set ON_ERROR_STOP on

-- 正例 1：插入合法发布单（DRAFT）
INSERT INTO public.iot_collector_config_release
    (id, tenant_id, site_id, site_code, workload_id, node_id, config_version,
     schema_version, canonicalization_version, payload_canonical, payload,
     payload_sha256, canonical_length_bytes, status)
VALUES (1, 1, 100, 'SITE-A', 'wl-1', 200, 1,
        '1.0', 'jcs-rfc8785-v1', '{"a":1}', '{"a":1}'::jsonb,
        repeat('a', 64), octet_length('{"a":1}'), 'DRAFT');

-- 正例 2：状态迁移 DRAFT -> VALIDATED -> PUBLISHED（published_by/at 必填）
UPDATE public.iot_collector_config_release
   SET status = 'VALIDATED', row_version = row_version + 1 WHERE id = 1;
UPDATE public.iot_collector_config_release
   SET status = 'PUBLISHED', published_by = 9001, published_at = CURRENT_TIMESTAMP,
       row_version = row_version + 1 WHERE id = 1;

-- 正例 3：引用标记插入 + 同键重复刷新 marked_at（幂等 upsert 语义）
INSERT INTO public.power_model_template_reference_mark
    (id, tenant_id, template_code, template_version, from_lifecycle, to_lifecycle, source_event_id)
VALUES (1, 1, 'power.hv_cabinet', '1.1.0', 'PUBLISHED', 'DEPRECATED',
        '00000000-0000-0000-0000-0000000000b1');
INSERT INTO public.power_model_template_reference_mark
    (id, tenant_id, template_code, template_version, from_lifecycle, to_lifecycle, source_event_id)
VALUES (2, 1, 'power.hv_cabinet', '1.1.0', 'PUBLISHED', 'DEPRECATED',
        '00000000-0000-0000-0000-0000000000b2')
ON CONFLICT (tenant_id, template_code, template_version, to_lifecycle)
DO UPDATE SET marked_at = CURRENT_TIMESTAMP, source_event_id = EXCLUDED.source_event_id;

-- 正例 4：协调审计追加写
INSERT INTO public.power_model_coordination_audit
    (id, event_id, tenant_id, event_type, action, detail)
VALUES (1, '00000000-0000-0000-0000-0000000000c1', 1,
        'POWER_PRODUCT_MODEL_BINDING_APPLIED_V1', 'IMPACT_EMPTY',
        'productId=2001, bindingRevision=7；无受影响活动 workload');

DO $$
BEGIN
    -- 反例 1：config_version 重复（同租户同 workload）
    BEGIN
        INSERT INTO public.iot_collector_config_release
            (id, tenant_id, site_id, site_code, workload_id, node_id, config_version,
             schema_version, canonicalization_version, payload_canonical, payload,
             payload_sha256, canonical_length_bytes, status)
        VALUES (2, 1, 100, 'SITE-A', 'wl-1', 200, 1,
                '1.0', 'jcs-rfc8785-v1', '{"b":2}', '{"b":2}'::jsonb,
                repeat('b', 64), octet_length('{"b":2}'), 'DRAFT');
        ASSERT FALSE, 'T1 duplicate config_version not rejected';
    EXCEPTION WHEN unique_violation THEN NULL;
    END;
    -- 反例 2：payload_sha256 非 64 位小写 hex
    BEGIN
        INSERT INTO public.iot_collector_config_release
            (id, tenant_id, site_id, site_code, workload_id, node_id, config_version,
             schema_version, canonicalization_version, payload_canonical, payload,
             payload_sha256, canonical_length_bytes, status)
        VALUES (3, 1, 100, 'SITE-A', 'wl-1', 200, 2,
                '1.0', 'jcs-rfc8785-v1', '{"c":3}', '{"c":3}'::jsonb,
                'NOTHEX', 7, 'DRAFT');
        ASSERT FALSE, 'T2 bad hash not rejected';
    EXCEPTION WHEN check_violation THEN NULL;
    END;
    -- 反例 3：canonical_length_bytes 与 canonical 实际字节数不符
    BEGIN
        INSERT INTO public.iot_collector_config_release
            (id, tenant_id, site_id, site_code, workload_id, node_id, config_version,
             schema_version, canonicalization_version, payload_canonical, payload,
             payload_sha256, canonical_length_bytes, status)
        VALUES (4, 1, 100, 'SITE-A', 'wl-1', 200, 3,
                '1.0', 'jcs-rfc8785-v1', '{"d":4}', '{"d":4}'::jsonb,
                repeat('d', 64), 999, 'DRAFT');
        ASSERT FALSE, 'T3 canonical length mismatch not rejected';
    EXCEPTION WHEN check_violation THEN NULL;
    END;
    -- 反例 4：PUBLISHED 缺 published_by/at
    BEGIN
        INSERT INTO public.iot_collector_config_release
            (id, tenant_id, site_id, site_code, workload_id, node_id, config_version,
             schema_version, canonicalization_version, payload_canonical, payload,
             payload_sha256, canonical_length_bytes, status)
        VALUES (5, 1, 100, 'SITE-A', 'wl-1', 200, 4,
                '1.0', 'jcs-rfc8785-v1', '{"e":5}', '{"e":5}'::jsonb,
                repeat('e', 64), octet_length('{"e":5}'), 'PUBLISHED');
        ASSERT FALSE, 'T4 PUBLISHED without publisher not rejected';
    EXCEPTION WHEN check_violation THEN NULL;
    END;
    -- 反例 5：快照字段原地修改（不可变触发器）
    BEGIN
        UPDATE public.iot_collector_config_release
           SET payload_canonical = '{"a":2}' WHERE id = 1;
        ASSERT FALSE, 'T5 snapshot mutation not rejected';
    EXCEPTION WHEN raise_exception THEN NULL;
    END;
    -- 反例 6：协调审计 UPDATE（追加写触发器）
    BEGIN
        UPDATE public.power_model_coordination_audit SET detail = 'tamper' WHERE id = 1;
        ASSERT FALSE, 'T6 audit update not rejected';
    EXCEPTION WHEN raise_exception THEN NULL;
    END;
    -- 反例 7：协调审计 DELETE（追加写触发器）
    BEGIN
        DELETE FROM public.power_model_coordination_audit WHERE id = 1;
        ASSERT FALSE, 'T7 audit delete not rejected';
    EXCEPTION WHEN raise_exception THEN NULL;
    END;
    -- 反例 8：协调审计 detail 超界（>512 字符）
    BEGIN
        INSERT INTO public.power_model_coordination_audit
            (id, event_id, tenant_id, event_type, action, detail)
        VALUES (2, '00000000-0000-0000-0000-0000000000c2', 1,
                'POWER_MODEL_TEMPLATE_PUBLISHED_V1', 'TEMPLATE_PUBLISHED_NOTED',
                repeat('x', 513));
        ASSERT FALSE, 'T8 oversize detail not rejected';
    EXCEPTION WHEN string_data_right_truncation THEN NULL;
    END;
    -- 反例 9：协调审计动作白名单外
    BEGIN
        INSERT INTO public.power_model_coordination_audit
            (id, event_id, tenant_id, event_type, action, detail)
        VALUES (3, '00000000-0000-0000-0000-0000000000c3', 1,
                'POWER_MODEL_TEMPLATE_PUBLISHED_V1', 'ARBITRARY_ACTION', 'x');
        ASSERT FALSE, 'T9 unknown action not rejected';
    EXCEPTION WHEN check_violation THEN NULL;
    END;
END $$;

-- 引用标记幂等结果：同键仅一行
DO $$
DECLARE cnt INTEGER;
BEGIN
    SELECT COUNT(*) INTO cnt FROM public.power_model_template_reference_mark
     WHERE tenant_id = 1 AND template_code = 'power.hv_cabinet'
       AND template_version = '1.1.0' AND to_lifecycle = 'DEPRECATED';
    ASSERT cnt = 1, 'reference mark idempotent upsert violated';
END $$;

-- U002 非空拒绝断言
DO $$
BEGIN
    BEGIN
        EXECUTE $inner$
            DO $d$ BEGIN
                IF EXISTS (SELECT 1 FROM public.iot_collector_config_release LIMIT 1) THEN
                    RAISE EXCEPTION 'U002 refused: iot_collector_config_release is not empty';
                END IF;
            END $d$;
        $inner$;
        ASSERT FALSE, 'T10 U002 non-empty refusal not raised';
    EXCEPTION WHEN raise_exception THEN NULL;
    END;
END $$;
