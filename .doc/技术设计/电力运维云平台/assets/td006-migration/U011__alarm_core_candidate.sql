-- ============================================================================
-- TD-006 U011 告警核心候选卸载（仅供评审，禁止在生产/共享库直接执行）
-- 仅允许在 capability 未启用、消费者/任务停止且所有新增表为空时执行。
-- 已产生任何告警、规则、Inbox、Outbox 或审计后不得使用本脚本删除事实；
-- 此时回滚只能停新流量、切旧读路径并保留新表。
-- ============================================================================

BEGIN;

DO $$
DECLARE
    table_name TEXT;
    table_non_empty BOOLEAN;
BEGIN
    FOREACH table_name IN ARRAY ARRAY[
        'alarm_outbox',
        'alarm_source_inbox',
        'alarm_false_alarm_review',
        'alarm_action_log',
        'alarm_source_mapping',
        'alarm_record',
        'alarm_maintenance_context',
        'alarm_rule_version',
        'alarm_rule'
    ] LOOP
        IF to_regclass(format('public.%I', table_name)) IS NULL THEN
            CONTINUE;
        END IF;

        EXECUTE format('SELECT EXISTS (SELECT 1 FROM public.%I LIMIT 1)', table_name)
            INTO table_non_empty;
        IF table_non_empty THEN
            RAISE EXCEPTION 'U011 refused: % contains business, inbox, outbox or audit facts', table_name;
        END IF;
    END LOOP;
END;
$$;

DROP TABLE IF EXISTS public.alarm_outbox;
DROP TABLE IF EXISTS public.alarm_source_inbox;
DROP TABLE IF EXISTS public.alarm_false_alarm_review;
DROP TABLE IF EXISTS public.alarm_action_log;
DROP TABLE IF EXISTS public.alarm_source_mapping;
DROP TABLE IF EXISTS public.alarm_record;
DROP TABLE IF EXISTS public.alarm_maintenance_context;
DROP TABLE IF EXISTS public.alarm_rule_version;
DROP TABLE IF EXISTS public.alarm_rule;

DROP FUNCTION IF EXISTS public.fn_alarm_action_log_append_only();
DROP FUNCTION IF EXISTS public.fn_alarm_source_mapping_append_only();
DROP FUNCTION IF EXISTS public.fn_alarm_rule_version_guard();

COMMIT;
