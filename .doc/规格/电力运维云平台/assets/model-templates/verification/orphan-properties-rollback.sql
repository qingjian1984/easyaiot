\set ON_ERROR_STOP on

-- TD-005 orphan property rollback candidate.
-- Restores a known-invalid orphan state and is only for incident recovery.
-- SAFE DEFAULT: changes are rolled back unless COMMIT_ROLLBACK=true is passed.

BEGIN;
SET LOCAL lock_timeout = '5s';
SET LOCAL statement_timeout = '30s';

LOCK TABLE product, product_properties IN SHARE ROW EXCLUSIVE MODE;

DO $$
DECLARE
    v_conflicts bigint;
    v_parent_conflicts bigint;
BEGIN
    SELECT count(*)
      INTO v_conflicts
      FROM product_properties
     WHERE id IN (900101, 900102, 900111, 900112)
        OR (tenant_id = 1
            AND product_identification IN ('8700054938017792', '9030000000000001'));

    IF v_conflicts <> 0 THEN
        RAISE EXCEPTION 'Rollback blocked: % conflicting rows already exist', v_conflicts;
    END IF;

    SELECT count(*)
      INTO v_parent_conflicts
      FROM product
     WHERE product_identification IN ('8700054938017792', '9030000000000001')
        OR template_identification IN ('8700054938017792', '9030000000000001');

    IF v_parent_conflicts <> 0 THEN
        RAISE EXCEPTION 'Rollback blocked: % parent product/template rows changed the known snapshot',
            v_parent_conflicts;
    END IF;
END
$$;

INSERT INTO product_properties (
    id,
    property_name,
    property_code,
    datatype,
    description,
    enumlist,
    max,
    maxlength,
    method,
    min,
    required,
    step,
    unit,
    create_by,
    create_time,
    update_by,
    update_time,
    template_identification,
    product_identification,
    tenant_id
) VALUES
    (900101, convert_from(decode('e6b8a9e5baa6', 'hex'), 'UTF8'), 'temperature', 'double', convert_from(decode('4d6f6462757320e6bc94e7a4bae6b8a9e5baa6', 'hex'), 'UTF8'), NULL, NULL, NULL, 'R', NULL, NULL, NULL, convert_from(decode('c2b0', 'hex'), 'UTF8'), 'admin', TIMESTAMP '2026-07-18 13:18:46.937229', NULL, NULL, NULL, '8700054938017792', 1),
    (900102, convert_from(decode('e8aebee5ae9ae782b9', 'hex'), 'UTF8'), 'setpoint', 'double', convert_from(decode('4d6f6462757320e6bc94e7a4bae8aebee5ae9ae782b9', 'hex'), 'UTF8'), NULL, NULL, NULL, 'RW', NULL, NULL, NULL, convert_from(decode('c2b0', 'hex'), 'UTF8'), 'admin', TIMESTAMP '2026-07-18 13:18:46.937229', NULL, NULL, NULL, '8700054938017792', 1),
    (900111, convert_from(decode('e6b8a9e5baa6', 'hex'), 'UTF8'), 'temperature', 'double', convert_from(decode('4f504320554120e6bc94e7a4bae6b8a9e5baa6', 'hex'), 'UTF8'), NULL, NULL, NULL, 'R', NULL, NULL, NULL, convert_from(decode('c2b0', 'hex'), 'UTF8'), 'admin', TIMESTAMP '2026-07-18 13:18:46.942350', NULL, NULL, NULL, '9030000000000001', 1),
    (900112, convert_from(decode('e8aebee5ae9ae782b9', 'hex'), 'UTF8'), 'setpoint', 'double', convert_from(decode('4f504320554120e6bc94e7a4bae8aebee5ae9ae782b9', 'hex'), 'UTF8'), NULL, NULL, NULL, 'RW', NULL, NULL, NULL, convert_from(decode('c2b0', 'hex'), 'UTF8'), 'admin', TIMESTAMP '2026-07-18 13:18:46.942350', NULL, NULL, NULL, '9030000000000001', 1);

DO $$
DECLARE
    v_restored bigint;
    v_snapshot_matches bigint;
BEGIN
    SELECT count(*)
      INTO v_restored
      FROM product_properties
     WHERE tenant_id = 1
       AND id IN (900101, 900102, 900111, 900112)
       AND product_identification IN ('8700054938017792', '9030000000000001');

    IF v_restored <> 4 THEN
        RAISE EXCEPTION 'Rollback postcheck failed: expected 4 rows, found %', v_restored;
    END IF;

    WITH expected(id, row_snapshot_md5) AS (
        VALUES
            (900101::bigint, '6e204a7c94fb6c6ca4f8757d004a34bd'),
            (900102::bigint, 'f2ec257836a9db7afeb06458171dcaae'),
            (900111::bigint, 'ee3bef3a0ad9a9ccea56778a264fed14'),
            (900112::bigint, '2c045c8dda97ba57b84460f42d299a5f')
    )
    SELECT count(*)
      INTO v_snapshot_matches
      FROM product_properties p
      JOIN expected e ON e.id = p.id
     WHERE md5(row_to_json(p)::text) = e.row_snapshot_md5;

    IF v_snapshot_matches <> 4 THEN
        RAISE EXCEPTION 'Rollback snapshot mismatch: expected 4 exact rows, found %',
            v_snapshot_matches;
    END IF;
END
$$;

SELECT row_to_json(p) AS restored_orphan_evidence
FROM product_properties p
WHERE id IN (900101, 900102, 900111, 900112)
ORDER BY id;

\if :{?COMMIT_ROLLBACK}
    \if :COMMIT_ROLLBACK
        \echo 'COMMIT_ROLLBACK=true: restoring known orphan snapshot'
        COMMIT;
    \else
        \echo 'COMMIT_ROLLBACK is not true: rolling back rehearsal'
        ROLLBACK;
    \endif
\else
    \echo 'COMMIT_ROLLBACK was not supplied: rolling back rehearsal'
    ROLLBACK;
\endif
