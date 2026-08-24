-- TD-003 §10 V009 产品路由身份补列卸载候选
--
-- 仅供隔离临时库演练或未来单独批准的收缩窗口使用。
-- 生产/共享环境的正常回滚是停用新写链并保留 nullable 列。

BEGIN;

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1
          FROM information_schema.columns
         WHERE table_schema = 'iot_sink'
           AND table_name = 'telemetry_inbox'
           AND column_name = 'product_identification'
    ) THEN
        RAISE EXCEPTION 'U009_TARGET_COLUMN_MISSING';
    END IF;

    IF EXISTS (
        SELECT 1
          FROM iot_sink.telemetry_inbox
         WHERE product_identification IS NOT NULL
    ) THEN
        RAISE EXCEPTION 'U009_NON_NULL_DATA_PRESENT';
    END IF;
END
$$;

ALTER TABLE iot_sink.telemetry_inbox
    DROP COLUMN product_identification RESTRICT;

COMMIT;
