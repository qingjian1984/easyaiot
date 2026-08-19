-- ============================================================================
-- TD-003 U006：V010 telemetry_sample 质量码/接收时间列卸载候选
-- （仅供评审，禁止未经审批窗口在生产/共享库执行）
--
-- 双基线：平台功能计划 1.5.0 / EasyAIoT 项目开发宪法 1.6.0
-- 上游：TD-003 §14、V010__telemetry_quality.sql
-- 覆盖：仅 V010 新增对象（quality 列 + CHECK 约束 + received_at_ms 列）。
--
-- 执行前置（全部满足才允许执行）：
--   1. 查询链路（TelemetryQueryPort 适配器）已切回 GOOD 兜底或停用；
--   2. V010 之后无依赖这两列的视图/索引/报表；
--   3. 已确认接受历史质量信息（全部 GOOD）与接收时间信息丢失；
--   4. 备份存在且审批单明确允许卸载。
-- ============================================================================

BEGIN;

-- 防御断言：目标列存在才继续
DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns
                    WHERE table_schema = 'iot_sink'
                      AND table_name  = 'telemetry_sample'
                      AND column_name IN ('quality', 'received_at_ms')) THEN
        RAISE EXCEPTION 'V010 columns absent; nothing to uninstall';
    END IF;
END $$;

ALTER TABLE iot_sink.telemetry_sample
    DROP CONSTRAINT IF EXISTS ck_sample_quality;

ALTER TABLE iot_sink.telemetry_sample
    DROP COLUMN IF EXISTS quality,
    DROP COLUMN IF EXISTS received_at_ms;

COMMIT;
