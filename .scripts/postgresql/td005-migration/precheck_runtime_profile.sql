-- MIG-003/005：迁移前运行时画像 precheck（只读）
--
-- 检测产品重复、运行表孤儿引用和可选幂等表缺失；返回行数为 0 才通过。
-- 缺少的表自动跳过；app.require_idempotency=1 时强制校验 power_idempotency_record。

CREATE TEMP TABLE tmp_profile_anomalies (
    anomaly_type text NOT NULL,
    detail text NOT NULL
);

DO $$
BEGIN
    IF to_regclass('public.product') IS NOT NULL THEN
        INSERT INTO tmp_profile_anomalies
        SELECT 'product_duplicate', tenant_id || ':' || product_identification
        FROM (
            SELECT tenant_id, product_identification, count(*) AS c
            FROM public.product
            GROUP BY tenant_id, product_identification
            HAVING count(*) > 1
        ) d;
    END IF;

    IF to_regclass('public.product_properties') IS NOT NULL
       AND to_regclass('public.product') IS NOT NULL THEN
        INSERT INTO tmp_profile_anomalies
        SELECT 'orphan_product_properties', tenant_id || ':' || product_identification
        FROM public.product_properties p
        WHERE p.product_identification IS NOT NULL
          AND NOT EXISTS (
              SELECT 1 FROM public.product x
              WHERE x.tenant_id = p.tenant_id
                AND x.product_identification = p.product_identification
          );
    END IF;

    IF to_regclass('public.product_commands') IS NOT NULL
       AND to_regclass('public.product_services') IS NOT NULL THEN
        INSERT INTO tmp_profile_anomalies
        SELECT 'orphan_product_commands', tenant_id || ':' || service_id::text
        FROM public.product_commands c
        WHERE NOT EXISTS (
            SELECT 1 FROM public.product_services s
            WHERE s.id = c.service_id AND s.tenant_id = c.tenant_id
        );
    END IF;

    IF to_regclass('public.product_event_response') IS NOT NULL
       AND to_regclass('public.product_event') IS NOT NULL THEN
        INSERT INTO tmp_profile_anomalies
        SELECT 'orphan_product_event_response', tenant_id || ':' || event_id::text
        FROM public.product_event_response r
        WHERE NOT EXISTS (
            SELECT 1 FROM public.product_event e
            WHERE e.id = r.event_id AND e.tenant_id = r.tenant_id
        );
    END IF;

    IF to_regclass('public.device') IS NOT NULL
       AND to_regclass('public.product') IS NOT NULL THEN
        INSERT INTO tmp_profile_anomalies
        SELECT 'orphan_device', tenant_id || ':' || product_identification
        FROM public.device d
        WHERE d.product_identification IS NOT NULL
          AND NOT EXISTS (
              SELECT 1 FROM public.product x
              WHERE x.tenant_id = d.tenant_id
                AND x.product_identification = d.product_identification
          );
    END IF;

    IF current_setting('app.require_idempotency', true) = '1'
       AND to_regclass('public.power_idempotency_record') IS NULL THEN
        INSERT INTO tmp_profile_anomalies
        VALUES ('missing_power_idempotency_record', 'required but missing');
    END IF;
END
$$;

SELECT anomaly_type, detail
FROM tmp_profile_anomalies
ORDER BY anomaly_type, detail;

DROP TABLE tmp_profile_anomalies;
