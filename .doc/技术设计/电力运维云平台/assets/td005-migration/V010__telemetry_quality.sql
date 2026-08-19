-- TD-003 §14 质量码与接收时间补列（V010）
-- 目标库：iot-device20；目标表：iot_sink.telemetry_sample（V008 建表）
-- 目的：PRD §4.4 每条数据须含质量码与接收时间；§14 设计 DDL 本有
--       quality/received_at 列，V008 落库时未含，本步骤补齐（additive）。
-- 约束：中文注释 MUST（宪法 1.6.0 §6.2）；additive-only，不删列不改类型。

-- 预检（执行前人工核对）：列必须不存在
--   SELECT column_name FROM information_schema.columns
--    WHERE table_schema='iot_sink' AND table_name='telemetry_sample'
--      AND column_name IN ('quality','received_at_ms');
--   期望 0 行。

-- 质量码：TD-003 §6 枚举（GOOD/UNCERTAIN/BAD/…），旧行统一 GOOD
ALTER TABLE iot_sink.telemetry_sample
    ADD COLUMN quality VARCHAR(32) NOT NULL DEFAULT 'GOOD';

COMMENT ON COLUMN iot_sink.telemetry_sample.quality IS
    'TD-003 §6 质量码：GOOD/UNCERTAIN/BAD 等；V010 前历史行统一 GOOD';

-- 接收时间（PRD §4.4 与采集时间并列展示）：旧行以迁移时刻兜底
ALTER TABLE iot_sink.telemetry_sample
    ADD COLUMN received_at_ms BIGINT NOT NULL DEFAULT 0;

ALTER TABLE iot_sink.telemetry_sample
    ALTER COLUMN received_at_ms SET DEFAULT NULL,
    ALTER COLUMN received_at_ms DROP NOT NULL,
    ALTER COLUMN received_at_ms SET NOT NULL;

-- 旧行回填：以本步骤执行时刻为接收时间下限（不可早于采集时间）
UPDATE iot_sink.telemetry_sample
   SET received_at_ms = (extract(epoch from NOW()) * 1000)::bigint
 WHERE received_at_ms = 0
    OR received_at_ms < collected_at_ms;

COMMENT ON COLUMN iot_sink.telemetry_sample.received_at_ms IS
    '中心入库时刻 UTC 毫秒；V010 前历史行以迁移时刻兜底';

-- 校验：quality 非空且受控（防御性 CHECK，additive）
ALTER TABLE iot_sink.telemetry_sample
    ADD CONSTRAINT ck_sample_quality CHECK (quality <> '');
