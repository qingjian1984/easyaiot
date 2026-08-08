-- MIG-009：TD-005 迁移涉及表与字段的中文 COMMENT 完整性检查
--
-- 返回行数为 0 才通过；返回行列出缺失或非中文注释的表/字段。
-- 表级注释按 objsubid=0，字段级注释按 objsubid=attnum。
-- 清单包含 V001/V002/V003 全部新表、runner 引导的 history 表，
-- 以及待落库的 power_idempotency_record / power_model_event_inbox（未建表时自动跳过）。

SELECT c.relname AS table_name, '<TABLE>' AS column_name,
       COALESCE(d.description, '<MISSING>') AS comment_text
FROM pg_class c
JOIN pg_namespace n ON n.oid = c.relnamespace
LEFT JOIN pg_description d ON d.objoid = c.oid AND d.objsubid = 0
WHERE n.nspname = 'public'
  AND c.relkind = 'r'
  AND c.relname IN (
      'schema_migration_history',
      'power_model_template',
      'power_model_template_version',
      'power_model_member_index',
      'power_model_audit',
      'power_model_release_outbox',
      'power_product_model_binding',
      'power_idempotency_record',
      'power_model_event_inbox',
      'iot_collector_config_release',
      'power_model_template_reference_mark',
      'power_model_coordination_audit'
  )
  AND (d.description IS NULL OR btrim(d.description) = '' OR d.description !~ '[一-龥]')

UNION ALL

SELECT c.relname AS table_name, a.attname AS column_name,
       COALESCE(d.description, '<MISSING>') AS comment_text
FROM pg_class c
JOIN pg_namespace n ON n.oid = c.relnamespace
JOIN pg_attribute a
    ON a.attrelid = c.oid
   AND a.attnum > 0
   AND NOT a.attisdropped
LEFT JOIN pg_description d
    ON d.objoid = c.oid
   AND d.objsubid = a.attnum
WHERE n.nspname = 'public'
  AND c.relkind = 'r'
  AND c.relname IN (
      'schema_migration_history',
      'power_model_template',
      'power_model_template_version',
      'power_model_member_index',
      'power_model_audit',
      'power_model_release_outbox',
      'power_product_model_binding',
      'power_idempotency_record',
      'power_model_event_inbox',
      'iot_collector_config_release',
      'power_model_template_reference_mark',
      'power_model_coordination_audit'
  )
  AND (d.description IS NULL OR btrim(d.description) = '' OR d.description !~ '[一-龥]');

