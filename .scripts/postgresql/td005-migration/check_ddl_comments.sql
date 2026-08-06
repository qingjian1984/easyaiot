-- MIG-009：V001/U001 涉及表与字段的中文 COMMENT 完整性检查
--
-- 返回行数为 0 才通过；返回行列出缺失或非中文注释的表/字段。
-- 表级注释按 objsubid=0，字段级注释按 objsubid=attnum。

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
      'power_product_model_binding'
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
      'power_product_model_binding'
  )
  AND (d.description IS NULL OR btrim(d.description) = '' OR d.description !~ '[一-龥]');
