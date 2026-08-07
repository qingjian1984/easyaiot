-- ============================================================================
-- TD-005 U001 候选卸载骨架（仅供评审，禁止在生产/共享库执行）
--
-- 双基线：平台功能计划 1.4.0 / EasyAIoT 项目开发宪法 1.5.0
-- 上游：TD-005 migration 0.1.8、ADR-013 1.4.0（Proposed）
-- 覆盖：V001（模板/版本/成员索引/审计/Outbox）+ V002（binding）两批对象；
--       本脚本为全量卸载候选，不拆分部分卸载。
--
-- 执行前置（全部满足才允许执行）：
--   1. power.device.model capability 已关闭、发布器已停止；
--   2. 所有本批次新增表业务行数为 0；
--   3. 无 FK/视图/函数/权限依赖；
--   4. 备份与恢复演练有效、变更审批明确允许；
--   5. 首条 DDL 前重复断言，任一条件不满足直接抛错且零变更。
--
-- 本脚本不删除 product (tenant_id, product_identification) 唯一约束：
-- 它属于既有产品表的 additive 约束，移除须另行评审/独立 ADR。
-- ============================================================================

BEGIN;

DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM public.power_product_model_binding LIMIT 1) THEN
        RAISE EXCEPTION 'U001 refused: power_product_model_binding is not empty';
    END IF;
    IF EXISTS (SELECT 1 FROM public.power_model_release_outbox LIMIT 1) THEN
        RAISE EXCEPTION 'U001 refused: power_model_release_outbox is not empty';
    END IF;
    IF EXISTS (SELECT 1 FROM public.power_model_audit LIMIT 1) THEN
        RAISE EXCEPTION 'U001 refused: power_model_audit is not empty';
    END IF;
    IF EXISTS (SELECT 1 FROM public.power_model_member_index LIMIT 1) THEN
        RAISE EXCEPTION 'U001 refused: power_model_member_index is not empty';
    END IF;
    IF EXISTS (SELECT 1 FROM public.power_model_template_version LIMIT 1) THEN
        RAISE EXCEPTION 'U001 refused: power_model_template_version is not empty';
    END IF;
    IF EXISTS (SELECT 1 FROM public.power_model_template LIMIT 1) THEN
        RAISE EXCEPTION 'U001 refused: power_model_template is not empty';
    END IF;
END;
$$;

-- 反向依赖顺序卸载
DROP TABLE IF EXISTS public.power_product_model_binding;
DROP TABLE IF EXISTS public.power_model_release_outbox;
DROP TABLE IF EXISTS public.power_model_audit;
DROP TABLE IF EXISTS public.power_model_member_index;
DROP TABLE IF EXISTS public.power_model_template_version;
DROP TABLE IF EXISTS public.power_model_template;

DROP FUNCTION IF EXISTS public.fn_power_product_model_binding_immutable();
DROP FUNCTION IF EXISTS public.fn_power_model_audit_append_only();
DROP FUNCTION IF EXISTS public.fn_power_model_template_version_immutable();
DROP FUNCTION IF EXISTS public.fn_power_model_template_code_immutable();

-- schema_migration_history 由 runner 管理，U001 不删除；
-- 如需清理执行历史，必须走运维保留政策与审批。

COMMIT;
