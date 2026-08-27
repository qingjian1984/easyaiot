-- TD-003 §5.1（M1-LC-03）中心 Inbox ACK 发送状态补列与待发索引（V012）
--
-- 该步骤只允许在 V008 + V009 + V010 已由同一受控 runner 成功落库后执行。
-- V011 已被 TD-006 告警候选保留，本步骤不复用、不依赖 V011。
-- 本资产不接入共享 APPLY_STEPS，不修改 V011/U011，不更新首装 dump；
-- 只有 LC03-DB-RUNTIME-01 获得独立部署授权后才可接线正式 runner。
-- 回滚候选见 U012；生产/共享环境的正常回滚是停用发送链并保留列。
-- 本资产不包含 BEGIN/COMMIT，由受控 runner 事务步骤统一包裹。

DO $$
BEGIN
    IF to_regclass('iot_sink.telemetry_inbox') IS NULL THEN
        RAISE EXCEPTION 'V012_TARGET_TABLE_MISSING';
    END IF;

    IF NOT EXISTS (
        SELECT 1
          FROM information_schema.columns
         WHERE table_schema = 'iot_sink'
           AND table_name = 'telemetry_inbox'
           AND column_name = 'product_identification'
    ) THEN
        RAISE EXCEPTION 'V012_V009_PREREQUISITE_MISSING';
    END IF;

    IF EXISTS (
        SELECT 1
          FROM information_schema.columns
         WHERE table_schema = 'iot_sink'
           AND table_name = 'telemetry_inbox'
           AND column_name = 'ack_sent_at_ms'
    ) THEN
        RAISE EXCEPTION 'V012_PREEXISTING_COLUMN_WITHOUT_HISTORY';
    END IF;

    IF EXISTS (
        SELECT 1
          FROM information_schema.columns
         WHERE table_schema = 'iot_sink'
           AND table_name = 'telemetry_inbox'
           AND column_name = 'ack_attempts'
    ) THEN
        RAISE EXCEPTION 'V012_PREEXISTING_COLUMN_WITHOUT_HISTORY';
    END IF;

    IF to_regclass('iot_sink.idx_inbox_ack_pending') IS NOT NULL THEN
        RAISE EXCEPTION 'V012_PREEXISTING_INDEX_WITHOUT_HISTORY';
    END IF;
END
$$;

ALTER TABLE iot_sink.telemetry_inbox
    ADD COLUMN ack_sent_at_ms BIGINT,
    ADD COLUMN ack_attempts INTEGER NOT NULL DEFAULT 0,
    ADD CONSTRAINT ck_inbox_ack_attempts_non_negative CHECK (ack_attempts >= 0);

COMMENT ON COLUMN iot_sink.telemetry_inbox.ack_sent_at_ms IS
    '成功 ACK V1 已通过 MQTT QoS1 publish 确认发送的时刻（UTC 毫秒）；NULL 表示尚未确认发送，由 10 秒扫描器补发';
COMMENT ON COLUMN iot_sink.telemetry_inbox.ack_attempts IS
    'ACK 发送尝试次数（含即时路径与扫描器补发）；每次发送前递增，禁止为负';
COMMENT ON CONSTRAINT ck_inbox_ack_attempts_non_negative ON iot_sink.telemetry_inbox IS
    'ACK 尝试次数必须非负；负值只可能来自绕过 repository 的非法写入';

CREATE INDEX idx_inbox_ack_pending
    ON iot_sink.telemetry_inbox(received_at_ms, id)
    WHERE ack_sent_at_ms IS NULL;

COMMENT ON INDEX iot_sink.idx_inbox_ack_pending IS
    '部分索引：仅覆盖尚未确认发送 ACK 的 Inbox 行，支撑 10 秒扫描器按 (received_at_ms,id) 升序批量领取';
