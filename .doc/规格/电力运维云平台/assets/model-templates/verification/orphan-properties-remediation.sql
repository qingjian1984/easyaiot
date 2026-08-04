\set ON_ERROR_STOP on

-- TD-005 orphan property remediation candidate.
-- SAFE DEFAULT: changes are rolled back unless COMMIT_REMEDIATION=true is passed.
-- Example authorized execution:
-- psql -v ON_ERROR_STOP=1 -v COMMIT_REMEDIATION=true -f orphan-properties-remediation.sql

BEGIN;
SET LOCAL lock_timeout = '5s';
SET LOCAL statement_timeout = '30s';

LOCK TABLE product,
           product_properties,
           device,
           device_service_invoke_response,
           ota_packages,
           product_event,
           product_script,
           product_services,
           product_template
    IN SHARE ROW EXCLUSIVE MODE;

-- Lock every current direct identifier table, including tables added after this script was authored.
DO $$
DECLARE
    v_identifier_table record;
BEGIN
    FOR v_identifier_table IN
        SELECT DISTINCT c.table_schema, c.table_name
          FROM information_schema.columns c
          JOIN information_schema.tables t
            ON t.table_schema = c.table_schema
           AND t.table_name = c.table_name
           AND t.table_type = 'BASE TABLE'
         WHERE c.table_schema = 'public'
           AND c.column_name IN ('product_identification', 'template_identification')
         ORDER BY c.table_schema, c.table_name
    LOOP
        EXECUTE format(
            'LOCK TABLE %I.%I IN SHARE ROW EXCLUSIVE MODE',
            v_identifier_table.table_schema,
            v_identifier_table.table_name
        );
    END LOOP;
END
$$;

DO $$
DECLARE
    v_exact_rows bigint;
    v_scope_rows bigint;
    v_parent_rows bigint;
    v_downstream_rows bigint;
    v_identifier_column record;
    v_reference_rows bigint;
BEGIN
    WITH expected(id, row_snapshot_md5) AS (
        VALUES
            (900101::bigint, '6e204a7c94fb6c6ca4f8757d004a34bd'),
            (900102::bigint, 'f2ec257836a9db7afeb06458171dcaae'),
            (900111::bigint, 'ee3bef3a0ad9a9ccea56778a264fed14'),
            (900112::bigint, '2c045c8dda97ba57b84460f42d299a5f')
    )
    SELECT count(*)
      INTO v_exact_rows
      FROM product_properties p
      JOIN expected e ON e.id = p.id
     WHERE p.tenant_id = 1
       AND md5(row_to_json(p)::text) = e.row_snapshot_md5;

    IF v_exact_rows <> 4 THEN
        RAISE EXCEPTION 'Expected 4 exact orphan rows, found %', v_exact_rows;
    END IF;

    SELECT count(*)
      INTO v_scope_rows
      FROM product_properties
     WHERE tenant_id = 1
       AND product_identification IN ('8700054938017792', '9030000000000001');

    IF v_scope_rows <> 4 THEN
        RAISE EXCEPTION 'Legacy identifier scope changed: expected 4 rows, found %', v_scope_rows;
    END IF;

    SELECT count(*)
      INTO v_parent_rows
      FROM product
     WHERE product_identification IN ('8700054938017792', '9030000000000001')
        OR template_identification IN ('8700054938017792', '9030000000000001');

    IF v_parent_rows <> 0 THEN
        RAISE EXCEPTION 'Legacy identifiers now have % product/template parent rows', v_parent_rows;
    END IF;

    SELECT
        (SELECT count(*) FROM device
          WHERE product_identification IN ('8700054938017792', '9030000000000001'))
      + (SELECT count(*) FROM device_service_invoke_response
          WHERE product_identification IN ('8700054938017792', '9030000000000001'))
      + (SELECT count(*) FROM ota_packages
          WHERE product_identification IN ('8700054938017792', '9030000000000001'))
      + (SELECT count(*) FROM product_event
          WHERE product_identification IN ('8700054938017792', '9030000000000001')
             OR template_identification IN ('8700054938017792', '9030000000000001'))
      + (SELECT count(*) FROM product_script
          WHERE product_identification IN ('8700054938017792', '9030000000000001'))
      + (SELECT count(*) FROM product_services
          WHERE product_identification IN ('8700054938017792', '9030000000000001')
             OR template_identification IN ('8700054938017792', '9030000000000001'))
      + (SELECT count(*) FROM product_template
          WHERE template_identification IN ('8700054938017792', '9030000000000001'))
      + (SELECT count(*) FROM product_properties
          WHERE template_identification IN ('8700054938017792', '9030000000000001'))
      + (SELECT count(*) FROM product_commands c
          JOIN product_services s ON s.id = c.service_id AND s.tenant_id = c.tenant_id
          WHERE s.product_identification IN ('8700054938017792', '9030000000000001')
             OR s.template_identification IN ('8700054938017792', '9030000000000001'))
      + (SELECT count(*) FROM product_commands_requests req
          JOIN product_services s ON s.id = req.service_id AND s.tenant_id = req.tenant_id
          WHERE s.product_identification IN ('8700054938017792', '9030000000000001')
             OR s.template_identification IN ('8700054938017792', '9030000000000001'))
      + (SELECT count(*) FROM product_commands_response resp
          JOIN product_services s ON s.id = resp.service_id AND s.tenant_id = resp.tenant_id
          WHERE s.product_identification IN ('8700054938017792', '9030000000000001')
             OR s.template_identification IN ('8700054938017792', '9030000000000001'))
      INTO v_downstream_rows;

    IF v_downstream_rows <> 0 THEN
        RAISE EXCEPTION 'Legacy identifiers now have % downstream references', v_downstream_rows;
    END IF;

    FOR v_identifier_column IN
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
            v_identifier_column.table_schema,
            v_identifier_column.table_name,
            v_identifier_column.column_name
        ) INTO v_reference_rows USING ARRAY['8700054938017792', '9030000000000001']::text[];
        IF v_reference_rows <> 0 THEN
            RAISE EXCEPTION 'Dynamic reference check failed: %.%.% has % matching rows',
                v_identifier_column.table_schema,
                v_identifier_column.table_name,
                v_identifier_column.column_name,
                v_reference_rows;
        END IF;
    END LOOP;
END
$$;

WITH deleted AS (
    DELETE FROM product_properties
     WHERE tenant_id = 1
       AND ((id IN (900101, 900102) AND product_identification = '8700054938017792')
         OR (id IN (900111, 900112) AND product_identification = '9030000000000001'))
    RETURNING *
)
SELECT row_to_json(deleted) AS deleted_orphan_evidence
FROM deleted
ORDER BY id;

DO $$
DECLARE
    v_remaining bigint;
BEGIN
    SELECT count(*)
      INTO v_remaining
      FROM product_properties
     WHERE tenant_id = 1
       AND (id IN (900101, 900102, 900111, 900112)
         OR product_identification IN ('8700054938017792', '9030000000000001'));

    IF v_remaining <> 0 THEN
        RAISE EXCEPTION 'Postcheck failed: % legacy orphan rows remain', v_remaining;
    END IF;
END
$$;

\if :{?COMMIT_REMEDIATION}
    \if :COMMIT_REMEDIATION
        \echo 'COMMIT_REMEDIATION=true: committing exact orphan cleanup'
        COMMIT;
    \else
        \echo 'COMMIT_REMEDIATION is not true: rolling back rehearsal'
        ROLLBACK;
    \endif
\else
    \echo 'COMMIT_REMEDIATION was not supplied: rolling back rehearsal'
    ROLLBACK;
\endif
