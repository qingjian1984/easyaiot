-- TD-003 §10 中心遥测 Inbox + TelemetryStore（V008）
-- 目标库：iot-device20（与 telemetry_outbox SQLite 不同，中心侧用 PostgreSQL）
-- 约束：中文注释 MUST（宪法 1.6.0 §6.2）

CREATE SCHEMA IF NOT EXISTS iot_sink;

-- §10 telemetry_inbox：两层幂等（UNIQUE tenant_id+message_id + content_sha256 校验）
CREATE TABLE IF NOT EXISTS iot_sink.telemetry_inbox (
    id                      BIGSERIAL PRIMARY KEY,
    message_id              UUID NOT NULL,
    message_id_wire         VARCHAR(36),                           -- 兼容 32 位无连字符 messageId
    request_id              VARCHAR(64) NOT NULL,                  -- 重试不刷新，ACK 回显
    tenant_id               BIGINT NOT NULL,                       -- 租户编号
    site_code               VARCHAR(128) NOT NULL,                 -- 站点编码（非空）
    device_identification   VARCHAR(128) NOT NULL,                 -- 设备标识
    property_code           VARCHAR(128) NOT NULL,                 -- 属性编码
    payload                 BYTEA NOT NULL,                        -- canonical UTF-8 字节（不重序列化）
    content_sha256          CHAR(64) NOT NULL,                     -- canonical SHA-256
    collected_at_ms         BIGINT NOT NULL,                       -- 采集时刻 UTC 毫秒
    sequence_no             BIGINT NOT NULL DEFAULT 0,             -- 单调递增序列
    source                  VARCHAR(64) NOT NULL DEFAULT 'unknown', -- 来源（如 modbus-rtu）
    config_version          BIGINT NOT NULL DEFAULT 0,             -- 配置快照版本
    projection_state        VARCHAR(32) NOT NULL DEFAULT 'RECEIVED', -- RECEIVED/PROJECTING/COMPLETED/PROJECTION_DEAD_LETTER
    projection_attempts     INTEGER NOT NULL DEFAULT 0,            -- 投影重试次数
    projection_lease_until  BIGINT,                                -- PROJECTING 租约截止
    next_projection_at_ms   BIGINT,                                -- 下次投影时间（退避后）
    projected_at_ms         BIGINT,                                -- 投影完成时刻
    last_projection_error   VARCHAR(512),                          -- 最后投影错误（脱敏）
    received_at_ms          BIGINT NOT NULL,                       -- 入库时刻
    updated_at_ms           BIGINT NOT NULL,                       -- 最后更新时刻
    created_at              TIMESTAMP NOT NULL DEFAULT NOW(),

    -- 第一层幂等：同租户同 messageId 只能有一条
    CONSTRAINT uq_inbox_tenant_message UNIQUE (tenant_id, message_id),
    -- projection_state 合法值
    CONSTRAINT ck_inbox_state CHECK (projection_state IN
        ('RECEIVED','PROJECTING','COMPLETED','PROJECTION_DEAD_LETTER'))
);

COMMENT ON TABLE iot_sink.telemetry_inbox IS 'TD-003 §10 中心遥测 Inbox：两层幂等接收 + 投影状态机';
COMMENT ON COLUMN iot_sink.telemetry_inbox.message_id IS 'UUID v4 幂等键（不用于排序）';
COMMENT ON COLUMN iot_sink.telemetry_inbox.payload IS 'canonical UTF-8 JSON 原字节（落库/查询/重试复用同一份）';
COMMENT ON COLUMN iot_sink.telemetry_inbox.projection_state IS 'RECEIVED→PROJECTING→COMPLETED/PROJECTION_DEAD_LETTER';

CREATE INDEX IF NOT EXISTS idx_inbox_projection
    ON iot_sink.telemetry_inbox(projection_state, received_at_ms);
CREATE INDEX IF NOT EXISTS idx_inbox_lease
    ON iot_sink.telemetry_inbox(projection_state, projection_lease_until);

-- §13 telemetry_sample：standard PostgreSQL 月分区时序表
CREATE TABLE IF NOT EXISTS iot_sink.telemetry_sample (
    tenant_id               BIGINT NOT NULL,                       -- 租户编号
    message_id              UUID NOT NULL,                         -- 幂等键
    content_sha256          CHAR(64) NOT NULL,                     -- canonical SHA-256
    site_code               VARCHAR(128) NOT NULL,                 -- 站点编码
    device_identification   VARCHAR(128) NOT NULL,                 -- 设备标识
    property_code           VARCHAR(128) NOT NULL,                 -- 属性编码
    value_numeric           NUMERIC(20,6) NOT NULL,                -- 十进制数值（不经 Double）
    collected_at_ms         BIGINT NOT NULL,                       -- 采集时刻 UTC 毫秒
    sequence_no             BIGINT NOT NULL DEFAULT 0,             -- 序列号
    source                  VARCHAR(64) NOT NULL DEFAULT 'unknown', -- 来源
    config_version          BIGINT NOT NULL DEFAULT 0,             -- 配置快照版本

    -- 第二层幂等：telemetry_sample_identity
    CONSTRAINT uq_sample_identity UNIQUE (tenant_id, message_id, content_sha256)
);

COMMENT ON TABLE iot_sink.telemetry_sample IS 'TD-003 §13 standard TelemetryStore：PostgreSQL 月分区时序数据';
COMMENT ON COLUMN iot_sink.telemetry_sample.value_numeric IS '十进制数值 NUMERIC（不经 Double，精度不丢）';

CREATE INDEX IF NOT EXISTS idx_sample_query
    ON iot_sink.telemetry_sample(tenant_id, device_identification, property_code, collected_at_ms DESC);
