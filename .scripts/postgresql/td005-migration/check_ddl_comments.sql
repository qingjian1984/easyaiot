-- MIG-009：TD-005 迁移涉及表与字段的中文 COMMENT 完整性检查
--
-- 返回行数为 0 才通过；返回行列出缺失或非中文注释的表/字段。
-- 表级注释按 objsubid=0，字段级注释按 objsubid=attnum。
-- 清单包含既有 TD-005 新表、runner 引导的 history 表、
-- power_idempotency_record 以及 P02-M2-02A V011 九表；未建表对象自动跳过。

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
      'power_model_coordination_audit',
      'collector_workload_binding_projection',
      'power_site',
      'power_space_node',
      'power_circuit',
      'power_device_asset',
      'power_device_assignment',
      'alarm_rule',
      'alarm_rule_version',
      'alarm_maintenance_context',
      'alarm_record',
      'alarm_source_mapping',
      'alarm_action_log',
      'alarm_false_alarm_review',
      'alarm_source_inbox',
      'alarm_outbox'
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
      'power_model_coordination_audit',
      'collector_workload_binding_projection',
      'power_site',
      'power_space_node',
      'power_circuit',
      'power_device_asset',
      'power_device_assignment',
      'alarm_rule',
      'alarm_rule_version',
      'alarm_maintenance_context',
      'alarm_record',
      'alarm_source_mapping',
      'alarm_action_log',
      'alarm_false_alarm_review',
      'alarm_source_inbox',
      'alarm_outbox'
  )
  AND (d.description IS NULL OR btrim(d.description) = '' OR d.description !~ '[一-龥]')

UNION ALL

-- LC02-07 V009：schema-qualified 目标列必须存在且含中文语义注释。
-- 使用 LEFT JOIN 使目标表/列缺失时也稳定返回一行。
SELECT 'iot_sink.telemetry_inbox' AS table_name,
       'product_identification' AS column_name,
       CASE
           WHEN c.oid IS NULL THEN '<MISSING_TABLE>'
           WHEN a.attnum IS NULL THEN '<MISSING_COLUMN>'
           ELSE COALESCE(d.description, '<MISSING>')
       END AS comment_text
FROM (SELECT 1 AS expected) e
LEFT JOIN pg_namespace n
       ON n.nspname = 'iot_sink'
LEFT JOIN pg_class c
       ON c.relnamespace = n.oid
      AND c.relname = 'telemetry_inbox'
      AND c.relkind = 'r'
LEFT JOIN pg_attribute a
       ON a.attrelid = c.oid
      AND a.attname = 'product_identification'
      AND a.attnum > 0
      AND NOT a.attisdropped
LEFT JOIN pg_description d
       ON d.objoid = c.oid
      AND d.objsubid = a.attnum
WHERE c.oid IS NULL
   OR a.attnum IS NULL
   OR d.description IS NULL
   OR btrim(d.description) = ''
   OR d.description !~ '[一-龥]';
