-- TD-005 消费者 Inbox 候选 DDL（仅供评审，禁止在生产/共享库执行）
--
-- 上游：ADR-014 Proposed / TD-005 migration §4.6.1
-- 语义：event_id 全局唯一；同 eventId 同 hash 返回 DUPLICATE，
--       同 ID 异 hash 进入隔离并 critical；保留窗口不小于重试/死信重放/双版本窗口。
-- ADR-014 1.1.0 处置：event_id 全局唯一已蕴含 (tenant_id, event_id) 唯一，
--       收缩冗余双 UNIQUE 避免写放大；租户隔离由 tenant_id NOT NULL 与应用查询保证。

CREATE TABLE IF NOT EXISTS public.power_model_event_inbox (
    id BIGSERIAL PRIMARY KEY,
    tenant_id BIGINT NOT NULL,
    event_id UUID NOT NULL,
    event_type VARCHAR(96) NOT NULL,
    payload_hash VARCHAR(71) NOT NULL,
    status VARCHAR(16) NOT NULL CHECK (status IN ('RECEIVED','PROCESSED','QUARANTINED')),
    received_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    processed_at TIMESTAMPTZ,
    last_error_code VARCHAR(64),
    last_error_digest VARCHAR(128),
    CONSTRAINT uq_power_model_event_inbox_event UNIQUE (event_id),
    CONSTRAINT ck_power_model_event_inbox_hash CHECK (
        payload_hash ~ '^sha256:[0-9a-f]{64}$'
    )
);

CREATE INDEX IF NOT EXISTS idx_power_model_event_inbox_dispatch
    ON public.power_model_event_inbox (status, received_at, id);

COMMENT ON TABLE public.power_model_event_inbox IS '电力模型事件消费者 Inbox（eventId 幂等去重）';
COMMENT ON COLUMN public.power_model_event_inbox.id IS '主键';
COMMENT ON COLUMN public.power_model_event_inbox.tenant_id IS '租户编号';
COMMENT ON COLUMN public.power_model_event_inbox.event_id IS '事件 UUID（与 Outbox event_id 一致）';
COMMENT ON COLUMN public.power_model_event_inbox.event_type IS '事件类型（含主版本）';
COMMENT ON COLUMN public.power_model_event_inbox.payload_hash IS '事件载荷 SHA-256';
COMMENT ON COLUMN public.power_model_event_inbox.status IS '消费状态（RECEIVED/PROCESSED/QUARANTINED）';
COMMENT ON COLUMN public.power_model_event_inbox.received_at IS '接收时间';
COMMENT ON COLUMN public.power_model_event_inbox.processed_at IS '处理完成时间';
COMMENT ON COLUMN public.power_model_event_inbox.last_error_code IS '最近错误码';
COMMENT ON COLUMN public.power_model_event_inbox.last_error_digest IS '最近错误摘要（脱敏）';
