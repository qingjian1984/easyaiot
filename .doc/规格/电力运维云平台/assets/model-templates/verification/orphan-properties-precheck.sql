\set ON_ERROR_STOP on

-- TD-005 orphan property remediation precheck.
-- Read-only: safe to run repeatedly. Any non-PASS result blocks remediation.

BEGIN TRANSACTION READ ONLY;

SELECT 'TARGET_ROWS' AS check_name,
       CASE WHEN count(*) = 4 THEN 'PASS' ELSE 'FAIL' END AS result,
       count(*) AS actual,
       4 AS expected
FROM product_properties
WHERE tenant_id = 1
  AND id IN (900101, 900102, 900111, 900112)
  AND product_identification IN ('8700054938017792', '9030000000000001');

SELECT 'TARGET_SCOPE_ROWS' AS check_name,
       CASE WHEN count(*) = 4 THEN 'PASS' ELSE 'FAIL' END AS result,
       count(*) AS actual,
       4 AS expected
FROM product_properties
WHERE tenant_id = 1
  AND product_identification IN ('8700054938017792', '9030000000000001');

SELECT 'PARENT_PRODUCTS' AS check_name,
       CASE WHEN count(*) = 0 THEN 'PASS' ELSE 'FAIL' END AS result,
       count(*) AS actual,
       0 AS expected
FROM product
WHERE product_identification IN ('8700054938017792', '9030000000000001')
   OR template_identification IN ('8700054938017792', '9030000000000001');

WITH downstream_refs AS (
    SELECT count(*) AS n FROM device
     WHERE product_identification IN ('8700054938017792', '9030000000000001')
    UNION ALL
    SELECT count(*) FROM device_service_invoke_response
     WHERE product_identification IN ('8700054938017792', '9030000000000001')
    UNION ALL
    SELECT count(*) FROM ota_packages
     WHERE product_identification IN ('8700054938017792', '9030000000000001')
    UNION ALL
    SELECT count(*) FROM product_event
     WHERE product_identification IN ('8700054938017792', '9030000000000001')
        OR template_identification IN ('8700054938017792', '9030000000000001')
    UNION ALL
    SELECT count(*) FROM product_script
     WHERE product_identification IN ('8700054938017792', '9030000000000001')
    UNION ALL
    SELECT count(*) FROM product_services
     WHERE product_identification IN ('8700054938017792', '9030000000000001')
        OR template_identification IN ('8700054938017792', '9030000000000001')
    UNION ALL
    SELECT count(*) FROM product_template
     WHERE template_identification IN ('8700054938017792', '9030000000000001')
    UNION ALL
    SELECT count(*) FROM product_properties
     WHERE template_identification IN ('8700054938017792', '9030000000000001')
    UNION ALL
    SELECT count(*) FROM product_commands c
     JOIN product_services s ON s.id = c.service_id AND s.tenant_id = c.tenant_id
     WHERE s.product_identification IN ('8700054938017792', '9030000000000001')
        OR s.template_identification IN ('8700054938017792', '9030000000000001')
    UNION ALL
    SELECT count(*) FROM product_commands_requests r
     JOIN product_services s ON s.id = r.service_id AND s.tenant_id = r.tenant_id
     WHERE s.product_identification IN ('8700054938017792', '9030000000000001')
        OR s.template_identification IN ('8700054938017792', '9030000000000001')
    UNION ALL
    SELECT count(*) FROM product_commands_response r
     JOIN product_services s ON s.id = r.service_id AND s.tenant_id = r.tenant_id
     WHERE s.product_identification IN ('8700054938017792', '9030000000000001')
        OR s.template_identification IN ('8700054938017792', '9030000000000001')
)
SELECT 'DOWNSTREAM_REFERENCES' AS check_name,
       CASE WHEN sum(n) = 0 THEN 'PASS' ELSE 'FAIL' END AS result,
       sum(n) AS actual,
       0 AS expected
FROM downstream_refs;

-- Fail closed when a future schema adds another direct product/template identifier column.
DO $$
DECLARE
    r record;
    v_count bigint;
    v_scanned integer := 0;
BEGIN
    FOR r IN
        SELECT c.table_schema, c.table_name, c.column_name
          FROM information_schema.columns c
          JOIN information_schema.tables t
            ON t.table_schema = c.table_schema
           AND t.table_name = c.table_name
           AND t.table_type = 'BASE TABLE'
         WHERE c.table_schema = 'public'
           AND c.column_name IN ('product_identification', 'template_identification')
           AND NOT (c.table_name = 'product_properties'
                    AND c.column_name = 'product_identification')
         ORDER BY c.table_name, c.column_name
    LOOP
        EXECUTE format(
            'SELECT count(*) FROM %I.%I WHERE %I::text = ANY ($1)',
            r.table_schema,
            r.table_name,
            r.column_name
        ) INTO v_count USING ARRAY['8700054938017792', '9030000000000001']::text[];
        v_scanned := v_scanned + 1;
        IF v_count <> 0 THEN
            RAISE EXCEPTION 'DYNAMIC_REFERENCE_CHECK failed: %.%.% has % matching rows',
                r.table_schema, r.table_name, r.column_name, v_count;
        END IF;
    END LOOP;
    RAISE NOTICE 'DYNAMIC_REFERENCE_CHECK PASS: % identifier columns scanned', v_scanned;
END
$$;

WITH expected(id, row_snapshot_md5) AS (
    VALUES
        (900101::bigint, '6e204a7c94fb6c6ca4f8757d004a34bd'),
        (900102::bigint, 'f2ec257836a9db7afeb06458171dcaae'),
        (900111::bigint, 'ee3bef3a0ad9a9ccea56778a264fed14'),
        (900112::bigint, '2c045c8dda97ba57b84460f42d299a5f')
)
SELECT 'FULL_ROW_SNAPSHOT' AS check_name,
       CASE WHEN count(*) = 4 THEN 'PASS' ELSE 'FAIL' END AS result,
       count(*) AS actual,
       4 AS expected
FROM product_properties p
JOIN expected e ON e.id = p.id
WHERE md5(row_to_json(p)::text) = e.row_snapshot_md5;

SELECT id,
       tenant_id,
       product_identification,
       template_identification,
       property_code,
       property_name,
       datatype,
       method,
       description,
       create_by,
       create_time
FROM product_properties
WHERE id IN (900101, 900102, 900111, 900112)
ORDER BY id;

ROLLBACK;
