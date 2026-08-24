-- ============================================================================
-- TD-006 U011 告警核心候选卸载（仅供评审，禁止在生产/共享库直接执行）
-- 仅允许在 capability 未启用、消费者/任务停止且所有新增表为空时执行。
-- 已产生任何告警、规则、Inbox、Outbox 或审计后不得使用本脚本删除事实；
-- 此时回滚只能停新流量、切旧读路径并保留新表。
-- ============================================================================

BEGIN;

DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM public.alarm_outbox LIMIT 1)
       OR EXISTS (SELECT 1 FROM public.alarm_source_inbox LIMIT 1)
       OR EXISTS (SELECT 1 FROM public.alarm_false_alarm_review LIMIT 1)
       OR EXISTS (SELECT 1 FROM public.alarm_action_log LIMIT 1)
       OR EXISTS (SELECT 1 FROM public.alarm_source_mapping LIMIT 1)
       OR EXISTS (SELECT 1 FROM public.alarm_record LIMIT 1)
       OR EXISTS (SELECT 1 FROM public.alarm_maintenance_context LIMIT 1)
       OR EXISTS (SELECT 1 FROM public.alarm_rule_version LIMIT 1)
       OR EXISTS (SELECT 1 FROM public.alarm_rule LIMIT 1) THEN
        RAISE EXCEPTION 'U011 refused: alarm core contains business, inbox, outbox or audit facts';
    END IF;
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

COMMIT;
