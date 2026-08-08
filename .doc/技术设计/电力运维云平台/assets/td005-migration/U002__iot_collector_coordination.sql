-- ============================================================================
-- TD-001 U002 候选卸载骨架（仅供评审，禁止在生产/共享库执行）
--
-- 双基线：平台功能计划 1.4.0 / EasyAIoT 项目开发宪法 1.5.0
-- 上游：TD-001 1.0.6 §4.1/§6.2、ADR-013 1.5.1（Accepted）
-- 覆盖：V003（iot_collector_config_release / power_model_template_reference_mark /
--       power_model_coordination_audit）一批对象；本脚本为全量卸载候选，不拆分。
--
-- 执行前置（全部满足才允许执行）：
--   1. power.model.events.enabled 已关闭、协调器处理器已摘除；
--   2. 三张新增表业务行数为 0；
--   3. 备份与恢复演练有效、变更审批明确允许；
--   4. 首条 DDL 前重复断言，任一条件不满足直接抛错且零变更。
-- ============================================================================

BEGIN;

DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM public.iot_collector_config_release LIMIT 1) THEN
        RAISE EXCEPTION 'U002 refused: iot_collector_config_release is not empty';
    END IF;
    IF EXISTS (SELECT 1 FROM public.power_model_template_reference_mark LIMIT 1) THEN
        RAISE EXCEPTION 'U002 refused: power_model_template_reference_mark is not empty';
    END IF;
    IF EXISTS (SELECT 1 FROM public.power_model_coordination_audit LIMIT 1) THEN
        RAISE EXCEPTION 'U002 refused: power_model_coordination_audit is not empty';
    END IF;
END;
$$;

DROP TABLE IF EXISTS public.power_model_coordination_audit;
DROP TABLE IF EXISTS public.power_model_template_reference_mark;
DROP TABLE IF EXISTS public.iot_collector_config_release;

DROP FUNCTION IF EXISTS public.fn_power_model_coordination_audit_append_only();
DROP FUNCTION IF EXISTS public.fn_iot_collector_config_release_immutable();

-- schema_migration_history 由 runner 管理，U002 不删除。

COMMIT;
