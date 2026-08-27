-- TD-003 §5.1（M1-LC-03）V012 ACK 发送状态补列卸载候选
--
-- 仅供隔离临时库演练或未来单独批准的收缩窗口使用。
-- 生产/共享环境的正常回滚是停用 ACK 发送链并保留 nullable 列。
-- 待发索引与 CHECK 约束随列一并移除。

BEGIN;

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1
          FROM information_schema.columns
         WHERE table_schema = 'iot_sink'
           AND table_name = 'telemetry_inbox'
           AND column_name = 'ack_sent_at_ms'
    ) THEN
        RAISE EXCEPTION 'U012_TARGET_COLUMN_MISSING';
    END IF;

    IF EXISTS (
        SELECT 1
          FROM iot_sink.telemetry_inbox
         WHERE ack_sent_at_ms IS NOT NULL
    ) THEN
        RAISE EXCEPTION 'U012_SENT_STATE_PRESENT';
    END IF;
END
$$;

DROP INDEX IF EXISTS iot_sink.idx_inbox_ack_pending;

ALTER TABLE iot_sink.telemetry_inbox
    DROP CONSTRAINT IF EXISTS ck_inbox_ack_attempts_non_negative,
    DROP COLUMN ack_sent_at_ms RESTRICT,
    DROP COLUMN ack_attempts RESTRICT;

COMMIT;
