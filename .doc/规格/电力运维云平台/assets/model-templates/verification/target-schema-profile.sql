-- TD-005 target PostgreSQL read-only profile v1.2.0.
-- Run with psql against the prepared iot-device database.
-- The transaction is explicitly read-only and always rolled back.

\pset format unaligned
\pset fieldsep '|'
\pset tuples_only on
\set ON_ERROR_STOP on

BEGIN TRANSACTION READ ONLY;

SELECT 'PROFILE_VERSION', '1.2.0';

SELECT 'TABLE_ROLE', table_name, table_role
FROM (VALUES
    ('product', 'CORE_RUNTIME'),
    ('product_properties', 'CORE_RUNTIME'),
    ('product_event', 'CORE_RUNTIME'),
    ('product_event_response', 'CORE_RUNTIME'),
    ('product_services', 'CORE_RUNTIME'),
    ('product_commands', 'CORE_RUNTIME'),
    ('product_commands_requests', 'CORE_RUNTIME'),
    ('product_commands_response', 'CORE_RUNTIME'),
    ('product_script', 'PROTECTED_DEPENDENCY'),
    ('device', 'PROTECTED_DEPENDENCY'),
    ('device_service_invoke_response', 'PROTECTED_DEPENDENCY'),
    ('ota_packages', 'PROTECTED_DEPENDENCY')
) AS profiled_tables(table_name, table_role)
ORDER BY table_role, table_name;

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
      'product_event_response',
      'product_services',
      'product_commands',
      'product_commands_requests',
      'product_commands_response',
      'product_script',
      'device',
      'device_service_invoke_response',
      'ota_packages'
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
          'product_event_response',
          'product_services',
          'product_commands',
          'product_commands_requests',
          'product_commands_response',
          'product_script',
          'device',
          'device_service_invoke_response',
          'ota_packages'
      )
    GROUP BY table_name
) table_schema_summary;

SELECT 'TABLE_CONTRACT_JSON',
       jsonb_object_agg(table_name, details ORDER BY table_name)::text
FROM (
    SELECT profiled.table_name,
           jsonb_build_object(
               'primaryKeyCount', (
                   SELECT count(*) FROM pg_constraint definition
                   WHERE definition.conrelid = to_regclass('public.' || profiled.table_name)
                     AND definition.contype = 'p'
               ),
               'businessUniqueCount', (
                   SELECT count(*)
                   FROM pg_index definition
                   WHERE definition.indrelid = to_regclass('public.' || profiled.table_name)
                     AND definition.indisunique
                     AND NOT definition.indisprimary
               ),
               'foreignKeyCount', (
                   SELECT count(*) FROM pg_constraint definition
                   WHERE definition.conrelid = to_regclass('public.' || profiled.table_name)
                     AND definition.contype = 'f'
               ),
               'checkConstraintCount', (
                   SELECT count(*) FROM pg_constraint definition
                   WHERE definition.conrelid = to_regclass('public.' || profiled.table_name)
                     AND definition.contype = 'c'
               ),
               'triggerCount', (
                   SELECT count(*)
                   FROM information_schema.triggers definition
                   WHERE definition.event_object_schema = 'public'
                     AND definition.event_object_table = profiled.table_name
               ),
               'indexCount', (
                   SELECT count(*)
                   FROM pg_index definition
                   WHERE definition.indrelid = to_regclass('public.' || profiled.table_name)
               )
           ) AS details
    FROM unnest(ARRAY[
        'product',
        'product_properties',
        'product_event',
        'product_event_response',
        'product_services',
        'product_commands',
        'product_commands_requests',
        'product_commands_response',
        'product_script',
        'device',
        'device_service_invoke_response',
        'ota_packages'
    ]) AS profiled(table_name)
) table_contract_summary;

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
        'product_event_response',
        'product_services',
        'product_commands',
        'product_commands_requests',
        'product_commands_response',
        'product_script',
        'device',
        'device_service_invoke_response',
        'ota_packages'
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
      'product_event_response',
      'product_services',
      'product_commands',
      'product_commands_requests',
      'product_commands_response',
      'product_script',
      'device',
      'device_service_invoke_response',
      'ota_packages'
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
      'product_event_response',
      'product_services',
      'product_commands',
      'product_commands_requests',
      'product_commands_response',
      'product_script',
      'device',
      'device_service_invoke_response',
      'ota_packages'
  )
ORDER BY event_object_table, trigger_name, event_manipulation;

SELECT 'ROW_COUNT', 'product', count(*)::text FROM product
UNION ALL SELECT 'ROW_COUNT', 'product_properties', count(*)::text FROM product_properties
UNION ALL SELECT 'ROW_COUNT', 'product_event', count(*)::text FROM product_event
UNION ALL SELECT 'ROW_COUNT', 'product_event_response', count(*)::text FROM product_event_response
UNION ALL SELECT 'ROW_COUNT', 'product_services', count(*)::text FROM product_services
UNION ALL SELECT 'ROW_COUNT', 'product_commands', count(*)::text FROM product_commands
UNION ALL SELECT 'ROW_COUNT', 'product_commands_requests', count(*)::text FROM product_commands_requests
UNION ALL SELECT 'ROW_COUNT', 'product_commands_response', count(*)::text FROM product_commands_response
UNION ALL SELECT 'ROW_COUNT', 'product_script', count(*)::text FROM product_script
UNION ALL SELECT 'ROW_COUNT', 'device', count(*)::text FROM device
UNION ALL SELECT 'ROW_COUNT', 'device_service_invoke_response', count(*)::text FROM device_service_invoke_response
UNION ALL SELECT 'ROW_COUNT', 'ota_packages', count(*)::text FROM ota_packages
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
UNION ALL
SELECT 'ORPHAN_COUNT', 'event_responses_without_event', count(*)::text
FROM product_event_response child
WHERE NOT EXISTS (
    SELECT 1 FROM product_event parent
    WHERE parent.tenant_id = child.tenant_id
      AND parent.id = child.event_id
)
UNION ALL
SELECT 'ORPHAN_COUNT', 'event_response_services_without_service', count(*)::text
FROM product_event_response child
WHERE child.service_id IS NOT NULL
  AND NOT EXISTS (
      SELECT 1 FROM product_services parent
      WHERE parent.tenant_id = child.tenant_id
        AND parent.id = child.service_id
  )
UNION ALL
SELECT 'ORPHAN_COUNT', 'scripts_without_exact_product', count(*)::text
FROM product_script child
WHERE NOT EXISTS (
    SELECT 1 FROM product parent
    WHERE parent.tenant_id = child.tenant_id
      AND parent.id = child.product_id
      AND parent.product_identification = child.product_identification
)
UNION ALL
SELECT 'ORPHAN_COUNT', 'devices_without_product', count(*)::text
FROM device child
WHERE NOT EXISTS (
    SELECT 1 FROM product parent
    WHERE parent.tenant_id = child.tenant_id
      AND parent.product_identification = child.product_identification
)
UNION ALL
SELECT 'ORPHAN_COUNT', 'invoke_responses_without_device', count(*)::text
FROM device_service_invoke_response child
WHERE NOT EXISTS (
    SELECT 1 FROM device parent
    WHERE parent.tenant_id = child.tenant_id
      AND parent.id = child.device_id
)
UNION ALL
SELECT 'ORPHAN_COUNT', 'invoke_responses_without_product', count(*)::text
FROM device_service_invoke_response child
WHERE child.product_identification IS NOT NULL
  AND NOT EXISTS (
      SELECT 1 FROM product parent
      WHERE parent.tenant_id = child.tenant_id
        AND parent.product_identification = child.product_identification
  )
UNION ALL
SELECT 'ORPHAN_COUNT', 'ota_packages_without_product', count(*)::text
FROM ota_packages child
WHERE child.tenant_id IS NOT NULL
  AND NOT EXISTS (
      SELECT 1 FROM product parent
      WHERE parent.tenant_id = child.tenant_id
        AND parent.product_identification = child.product_identification
  )
ORDER BY 2;

SELECT 'RELATIONSHIP_ANOMALY', 'request_service_mismatch', count(*)::text
FROM product_commands_requests child
JOIN product_commands command
  ON command.tenant_id = child.tenant_id
 AND command.id = child.commands_id
WHERE child.service_id <> command.service_id
UNION ALL
SELECT 'RELATIONSHIP_ANOMALY', 'response_service_mismatch', count(*)::text
FROM product_commands_response child
JOIN product_commands command
  ON command.tenant_id = child.tenant_id
 AND command.id = child.commands_id
WHERE child.service_id IS NOT NULL
  AND child.service_id <> command.service_id
UNION ALL
SELECT 'RELATIONSHIP_ANOMALY', 'response_service_id_null', count(*)::text
FROM product_commands_response
WHERE service_id IS NULL
UNION ALL
SELECT 'RELATIONSHIP_ANOMALY', 'invoke_device_identity_mismatch', count(*)::text
FROM device_service_invoke_response child
JOIN device parent
  ON parent.tenant_id = child.tenant_id
 AND parent.id = child.device_id
WHERE child.device_identification IS NOT NULL
  AND child.device_identification <> parent.device_identification
UNION ALL
SELECT 'RELATIONSHIP_ANOMALY', 'ota_tenant_id_null', count(*)::text
FROM ota_packages
WHERE tenant_id IS NULL
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
       'all_profiled_tables_have_tenant_id',
       (count(*) FILTER (WHERE column_name = 'tenant_id') = 12)::text
FROM information_schema.columns
WHERE table_schema = 'public'
  AND table_name IN (
      'product',
      'product_properties',
      'product_event',
      'product_event_response',
      'product_services',
      'product_commands',
      'product_commands_requests',
      'product_commands_response',
      'product_script',
      'device',
      'device_service_invoke_response',
      'ota_packages'
  )
UNION ALL
SELECT 'PROFILE_FLAG',
       'all_profiled_tenant_ids_not_null',
       (count(*) FILTER (WHERE column_name = 'tenant_id' AND is_nullable = 'NO') = 12)::text
FROM information_schema.columns
WHERE table_schema = 'public'
  AND table_name IN (
      'product',
      'product_properties',
      'product_event',
      'product_event_response',
      'product_services',
      'product_commands',
      'product_commands_requests',
      'product_commands_response',
      'product_script',
      'device',
      'device_service_invoke_response',
      'ota_packages'
  )
UNION ALL
SELECT 'PROFILE_FLAG',
       'business_unique_constraint_count',
       count(*)::text
FROM pg_index index_definition
JOIN pg_class table_definition ON table_definition.oid = index_definition.indrelid
JOIN pg_namespace table_schema ON table_schema.oid = table_definition.relnamespace
WHERE table_schema.nspname = 'public'
  AND table_definition.relname IN (
      'product',
      'product_properties',
      'product_event',
      'product_event_response',
      'product_services',
      'product_commands',
      'product_commands_requests',
      'product_commands_response',
      'product_script',
      'device',
      'device_service_invoke_response',
      'ota_packages'
  )
  AND index_definition.indisunique
  AND NOT index_definition.indisprimary
UNION ALL
SELECT 'PROFILE_FLAG',
       'primary_key_count',
       count(*)::text
FROM pg_constraint definition
WHERE definition.conrelid IN (
    SELECT to_regclass('public.' || table_name)
    FROM unnest(ARRAY[
        'product',
        'product_properties',
        'product_event',
        'product_event_response',
        'product_services',
        'product_commands',
        'product_commands_requests',
        'product_commands_response',
        'product_script',
        'device',
        'device_service_invoke_response',
        'ota_packages'
    ]) AS table_name
)
  AND definition.contype = 'p'
UNION ALL
SELECT 'PROFILE_FLAG',
       'check_constraint_count',
       count(*)::text
FROM pg_constraint definition
WHERE definition.conrelid IN (
    SELECT to_regclass('public.' || table_name)
    FROM unnest(ARRAY[
        'product',
        'product_properties',
        'product_event',
        'product_event_response',
        'product_services',
        'product_commands',
        'product_commands_requests',
        'product_commands_response',
        'product_script',
        'device',
        'device_service_invoke_response',
        'ota_packages'
    ]) AS table_name
)
  AND definition.contype = 'c'
UNION ALL
SELECT 'PROFILE_FLAG',
       'index_count',
       count(*)::text
FROM pg_index definition
WHERE definition.indrelid IN (
    SELECT to_regclass('public.' || table_name)
    FROM unnest(ARRAY[
        'product',
        'product_properties',
        'product_event',
        'product_event_response',
        'product_services',
        'product_commands',
        'product_commands_requests',
        'product_commands_response',
        'product_script',
        'device',
        'device_service_invoke_response',
        'ota_packages'
    ]) AS table_name
)
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
      'product_event_response',
      'product_services',
      'product_commands',
      'product_commands_requests',
      'product_commands_response',
      'product_script',
      'device',
      'device_service_invoke_response',
      'ota_packages'
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
      'product_event_response',
      'product_services',
      'product_commands',
      'product_commands_requests',
      'product_commands_response',
      'product_script',
      'device',
      'device_service_invoke_response',
      'ota_packages'
  );

ROLLBACK;
