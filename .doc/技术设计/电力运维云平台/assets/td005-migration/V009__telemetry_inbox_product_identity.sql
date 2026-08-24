-- TD-003 §10 产品路由身份补列（V009）
--
-- 该步骤只允许在 V008 + V010 已由同一受控 runner 成功落库后执行。
-- 业务事实来源为 MQTT Topic、认证主体与载荷设备身份校验，禁止从站点或属性推断。
-- 本资产不包含 BEGIN/COMMIT，由 TD-005 runner 事务步骤统一包裹。

DO $$
BEGIN
    IF to_regclass('iot_sink.telemetry_inbox') IS NULL THEN
        RAISE EXCEPTION 'V009_TARGET_TABLE_MISSING';
    END IF;

    IF EXISTS (
        SELECT 1
          FROM information_schema.columns
         WHERE table_schema = 'iot_sink'
           AND table_name = 'telemetry_inbox'
           AND column_name = 'product_identification'
    ) THEN
        RAISE EXCEPTION 'V009_PREEXISTING_COLUMN_WITHOUT_HISTORY';
    END IF;
END
$$;

ALTER TABLE iot_sink.telemetry_inbox
    ADD COLUMN product_identification VARCHAR(128);

COMMENT ON COLUMN iot_sink.telemetry_inbox.product_identification IS
    '经 MQTT Topic、认证主体与载荷设备身份校验后持久化的产品路由标识；禁止由站点或属性推断';
