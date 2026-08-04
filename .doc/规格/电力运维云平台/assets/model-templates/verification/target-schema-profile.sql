-- TD-005 target PostgreSQL read-only profile v1.1.0.
-- Run with psql against the prepared iot-device database.
-- The transaction is explicitly read-only and always rolled back.

\pset format unaligned
\pset fieldsep '|'
\pset tuples_only on
\set ON_ERROR_STOP on

BEGIN TRANSACTION READ ONLY;

SELECT 'PROFILE_VERSION', '1.1.0';

SELECT 'ENV',
       current_database(),
       current_user,
       current_setting('server_version'),
       current_setting('transaction_read_only');

SELECT 'COLUMN',
       table_name,
       ordinal_position::text,
       column_name,
       data_type,
       is_nullable,
       coalesce(column_default, '')
FROM information_schema.columns
WHERE table_schema = 'public'
  AND table_name IN (
      'product',
      'product_properties',
      'product_event',
      'product_services',
      'product_commands',
      'product_commands_requests',
      'product_commands_response'
  )
ORDER BY table_name, ordinal_position;

SELECT 'TABLE_SCHEMA_JSON',
       jsonb_object_agg(table_name, details ORDER BY table_name)::text
FROM (
    SELECT table_name,
           jsonb_build_object(
               'columnCount', count(*),
               'hasTenantId', bool_or(column_name = 'tenant_id'),
               'tenantNotNull', bool_or(column_name = 'tenant_id' AND is_nullable = 'NO'),
               'columnSignatures', jsonb_agg(
                   column_name || '|' || data_type || '|' ||
                   CASE WHEN is_nullable = 'NO' THEN 'NOT_NULL' ELSE 'NULLABLE' END
                   ORDER BY ordinal_position
               )
           ) AS details
    FROM information_schema.columns
    WHERE table_schema = 'public'
      AND table_name IN (
          'product',
          'product_properties',
          'product_event',
          'product_services',
          'product_commands',
          'product_commands_requests',
          'product_commands_response'
      )
    GROUP BY table_name
) table_schema_summary;

SELECT 'CONSTRAINT',
       c.conrelid::regclass::text,
       c.conname,
       c.contype::text,
       pg_get_constraintdef(c.oid, true)
FROM pg_constraint c
WHERE c.conrelid IN (
    SELECT to_regclass('public.' || table_name)
    FROM unnest(ARRAY[
        'product',
        'product_properties',
        'product_event',
        'product_services',
        'product_commands',
        'product_commands_requests',
        'product_commands_response'
    ]) AS table_name
)
ORDER BY c.conrelid::regclass::text, c.conname;

SELECT 'INDEX', tablename, indexname, indexdef
FROM pg_indexes
WHERE schemaname = 'public'
  AND tablename IN (
      'product',
      'product_properties',
      'product_event',
      'product_services',
      'product_commands',
      'product_commands_requests',
      'product_commands_response'
  )
ORDER BY tablename, indexname;

SELECT 'TRIGGER',
       event_object_table,
       trigger_name,
       action_timing,
       event_manipulation,
       action_statement
FROM information_schema.triggers
WHERE event_object_schema = 'public'
  AND event_object_table IN (
      'product',
      'product_properties',
      'product_event',
      'product_services',
      'product_commands',
      'product_commands_requests',
      'product_commands_response'
  )
ORDER BY event_object_table, trigger_name, event_manipulation;

SELECT 'ROW_COUNT', 'product', count(*)::text FROM product
UNION ALL SELECT 'ROW_COUNT', 'product_properties', count(*)::text FROM product_properties
UNION ALL SELECT 'ROW_COUNT', 'product_event', count(*)::text FROM product_event
UNION ALL SELECT 'ROW_COUNT', 'product_services', count(*)::text FROM product_services
UNION ALL SELECT 'ROW_COUNT', 'product_commands', count(*)::text FROM product_commands
UNION ALL SELECT 'ROW_COUNT', 'product_commands_requests', count(*)::text FROM product_commands_requests
UNION ALL SELECT 'ROW_COUNT', 'product_commands_response', count(*)::text FROM product_commands_response
ORDER BY 2;

SELECT 'DUPLICATE_GROUPS', 'product_identification', count(*)::text
FROM (
    SELECT tenant_id, lower(product_identification)
    FROM product
    GROUP BY tenant_id, lower(product_identification)
    HAVING count(*) > 1
) duplicate_product
UNION ALL
SELECT 'DUPLICATE_GROUPS', 'property_code_product_scope', count(*)::text
FROM (
    SELECT tenant_id, product_identification, lower(property_code)
    FROM product_properties
    WHERE product_identification IS NOT NULL
    GROUP BY tenant_id, product_identification, lower(property_code)
    HAVING count(*) > 1
) duplicate_property
UNION ALL
SELECT 'DUPLICATE_GROUPS', 'property_code_template_scope', count(*)::text
FROM (
    SELECT tenant_id, template_identification, lower(property_code)
    FROM product_properties
    WHERE template_identification IS NOT NULL
    GROUP BY tenant_id, template_identification, lower(property_code)
    HAVING count(*) > 1
) duplicate_template_property
UNION ALL
SELECT 'DUPLICATE_GROUPS', 'event_code_product_scope', count(*)::text
FROM (
    SELECT tenant_id, product_identification, lower(event_code)
    FROM product_event
    WHERE product_identification IS NOT NULL
    GROUP BY tenant_id, product_identification, lower(event_code)
    HAVING count(*) > 1
) duplicate_event
UNION ALL
SELECT 'DUPLICATE_GROUPS', 'event_code_template_scope', count(*)::text
FROM (
    SELECT tenant_id, template_identification, lower(event_code)
    FROM product_event
    WHERE template_identification IS NOT NULL
    GROUP BY tenant_id, template_identification, lower(event_code)
    HAVING count(*) > 1
) duplicate_template_event
UNION ALL
SELECT 'DUPLICATE_GROUPS', 'service_code_product_scope', count(*)::text
FROM (
    SELECT tenant_id, product_identification, lower(service_code)
    FROM product_services
    WHERE product_identification IS NOT NULL
    GROUP BY tenant_id, product_identification, lower(service_code)
    HAVING count(*) > 1
) duplicate_service
UNION ALL
SELECT 'DUPLICATE_GROUPS', 'service_code_template_scope', count(*)::text
FROM (
    SELECT tenant_id, template_identification, lower(service_code)
    FROM product_services
    WHERE template_identification IS NOT NULL
    GROUP BY tenant_id, template_identification, lower(service_code)
    HAVING count(*) > 1
) duplicate_template_service
ORDER BY 2;

SELECT 'IDENTIFIER_SCOPE', 'properties_both_null', count(*)::text
FROM product_properties
WHERE product_identification IS NULL AND template_identification IS NULL
UNION ALL
SELECT 'IDENTIFIER_SCOPE', 'properties_both_set', count(*)::text
FROM product_properties
WHERE product_identification IS NOT NULL AND template_identification IS NOT NULL
UNION ALL
SELECT 'IDENTIFIER_SCOPE', 'events_both_null', count(*)::text
FROM product_event
WHERE product_identification IS NULL AND template_identification IS NULL
UNION ALL
SELECT 'IDENTIFIER_SCOPE', 'events_both_set', count(*)::text
FROM product_event
WHERE product_identification IS NOT NULL AND template_identification IS NOT NULL
UNION ALL
SELECT 'IDENTIFIER_SCOPE', 'services_both_null', count(*)::text
FROM product_services
WHERE product_identification IS NULL AND template_identification IS NULL
UNION ALL
SELECT 'IDENTIFIER_SCOPE', 'services_both_set', count(*)::text
FROM product_services
WHERE product_identification IS NOT NULL AND template_identification IS NOT NULL
ORDER BY 2;

SELECT 'ORPHAN_COUNT', 'properties_without_product', count(*)::text
FROM product_properties child
WHERE child.product_identification IS NOT NULL
  AND NOT EXISTS (
      SELECT 1 FROM product parent
      WHERE parent.tenant_id = child.tenant_id
        AND parent.product_identification = child.product_identification
  )
UNION ALL
SELECT 'ORPHAN_COUNT', 'events_without_product', count(*)::text
FROM product_event child
WHERE child.product_identification IS NOT NULL
  AND NOT EXISTS (
      SELECT 1 FROM product parent
      WHERE parent.tenant_id = child.tenant_id
        AND parent.product_identification = child.product_identification
  )
UNION ALL
SELECT 'ORPHAN_COUNT', 'services_without_product', count(*)::text
FROM product_services child
WHERE child.product_identification IS NOT NULL
  AND NOT EXISTS (
      SELECT 1 FROM product parent
      WHERE parent.tenant_id = child.tenant_id
        AND parent.product_identification = child.product_identification
  )
UNION ALL
SELECT 'ORPHAN_COUNT', 'commands_without_service', count(*)::text
FROM product_commands child
WHERE NOT EXISTS (
    SELECT 1 FROM product_services parent
    WHERE parent.tenant_id = child.tenant_id
      AND parent.id = child.service_id
)
UNION ALL
SELECT 'ORPHAN_COUNT', 'requests_without_command', count(*)::text
FROM product_commands_requests child
WHERE NOT EXISTS (
    SELECT 1 FROM product_commands parent
    WHERE parent.tenant_id = child.tenant_id
      AND parent.id = child.commands_id
)
UNION ALL
SELECT 'ORPHAN_COUNT', 'responses_without_command', count(*)::text
FROM product_commands_response child
WHERE NOT EXISTS (
    SELECT 1 FROM product_commands parent
    WHERE parent.tenant_id = child.tenant_id
      AND parent.id = child.commands_id
)
ORDER BY 2;

SELECT 'ORPHAN_PROPERTY',
       child.tenant_id::text,
       child.id::text,
       child.product_identification,
       coalesce(child.template_identification, '<NULL>'),
       child.property_code
FROM product_properties child
WHERE child.product_identification IS NOT NULL
  AND NOT EXISTS (
      SELECT 1 FROM product parent
      WHERE parent.tenant_id = child.tenant_id
        AND parent.product_identification = child.product_identification
  )
ORDER BY child.tenant_id, child.product_identification, child.id;

SELECT 'PROFILE_FLAG',
       'product_properties_has_service_id',
       EXISTS (
           SELECT 1
           FROM information_schema.columns
           WHERE table_schema = 'public'
             AND table_name = 'product_properties'
             AND column_name = 'service_id'
       )::text
UNION ALL
SELECT 'PROFILE_FLAG',
       'foreign_key_count',
       count(*)::text
FROM information_schema.table_constraints
WHERE table_schema = 'public'
  AND table_name IN (
      'product',
      'product_properties',
      'product_event',
      'product_services',
      'product_commands',
      'product_commands_requests',
      'product_commands_response'
  )
  AND constraint_type = 'FOREIGN KEY'
UNION ALL
SELECT 'PROFILE_FLAG',
       'trigger_count',
       count(*)::text
FROM information_schema.triggers
WHERE event_object_schema = 'public'
  AND event_object_table IN (
      'product',
      'product_properties',
      'product_event',
      'product_services',
      'product_commands',
      'product_commands_requests',
      'product_commands_response'
  );

ROLLBACK;
