-- TD-004 幂等记录表候选 DDL（仅供评审，禁止在生产/共享库执行）
--
-- 上游：TD-004 §7.12 列契约与 §7.10 索引基线；TD-005 migration M0.5 串行前置；MIG-005 门禁
-- 语义：跨副本以唯一约束争抢首个 insert 仲裁幂等；冲突方读取同一记录，
--       同 key 异 request_hash 返回 409 IDEMPOTENCY_KEY_REUSED，
--       同 hash 已完成则重放原响应，IN_PROGRESS 返回 IDEMPOTENCY_IN_PROGRESS。
-- 生命周期：默认保留 24 小时（expires_at 默认值），具体操作可延长但不得在仍可重试的
--       业务窗口内提前清理；定时任务按 expires_at 分批删除已完成（SUCCEEDED/FAILED_FINAL）
--       记录；IN_PROGRESS 只能在确认无活动事务且超过恢复阈值后转为可重试，不得直接删除。
-- 落库路径：ADR-013 受控 runner（history + SHA-256 + advisory lock），批准前不得执行；
--       落库评审时须同步把本表追加到 check_ddl_comments.sql 的注释门禁清单。
-- 应用侧责任（DDL 无法表达，评审时核对实现）：key_hash 为客户端 key 的服务端 HMAC，
--       禁止存原文；request_hash 为 method/path/规范 payload 的 SHA-256；
--       response_payload 必须有界且脱敏，二维码签发/轮换禁止保存 payload/shortCode 明文；
--       updated_at 由应用在状态迁移时维护。

CREATE TABLE IF NOT EXISTS public.power_idempotency_record (
    id BIGSERIAL PRIMARY KEY,
    tenant_id BIGINT NOT NULL,
    principal_type VARCHAR(16) NOT NULL,
    principal_id VARCHAR(64) NOT NULL,
    operation VARCHAR(64) NOT NULL,
    key_hash BYTEA NOT NULL,
    request_hash BYTEA NOT NULL,
    state VARCHAR(20) NOT NULL,
    http_status INTEGER,
    response_payload JSONB,
    result_ref VARCHAR(128),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    expires_at TIMESTAMPTZ NOT NULL DEFAULT (CURRENT_TIMESTAMP + INTERVAL '24 hours'),
    CONSTRAINT uq_power_idempotency_scope
        UNIQUE (tenant_id, principal_type, principal_id, operation, key_hash),
    CONSTRAINT ck_power_idempotency_principal_type
        CHECK (principal_type IN ('USER', 'SERVICE')),
    CONSTRAINT ck_power_idempotency_state
        CHECK (state IN ('IN_PROGRESS', 'SUCCEEDED', 'FAILED_FINAL')),
    CONSTRAINT ck_power_idempotency_key_hash_len
        CHECK (octet_length(key_hash) = 32),
    CONSTRAINT ck_power_idempotency_request_hash_len
        CHECK (octet_length(request_hash) = 32),
    CONSTRAINT ck_power_idempotency_http_status
        CHECK (http_status IS NULL OR http_status BETWEEN 100 AND 599),
    CONSTRAINT ck_power_idempotency_state_response
        CHECK (
            (state = 'IN_PROGRESS' AND http_status IS NULL AND response_payload IS NULL)
            OR (state IN ('SUCCEEDED', 'FAILED_FINAL') AND http_status IS NOT NULL)
        ),
    CONSTRAINT ck_power_idempotency_payload_bound
        CHECK (response_payload IS NULL OR octet_length(response_payload::text) <= 16384),
    CONSTRAINT ck_power_idempotency_expiry
        CHECK (expires_at > created_at)
);

-- 清理任务按 expires_at 分批扫描已完成记录（TD-004 §7.10 索引基线）
CREATE INDEX IF NOT EXISTS idx_power_idempotency_record_expires
    ON public.power_idempotency_record (expires_at);

COMMENT ON TABLE public.power_idempotency_record IS '电力域写操作幂等记录（跨副本唯一争抢仲裁，事实来源 TD-004 §7.12）';
COMMENT ON COLUMN public.power_idempotency_record.id IS '主键';
COMMENT ON COLUMN public.power_idempotency_record.tenant_id IS '租户编号（租户隔离列，不可为空）';
COMMENT ON COLUMN public.power_idempotency_record.principal_type IS '主体类型（USER/SERVICE）';
COMMENT ON COLUMN public.power_idempotency_record.principal_id IS '主体标识（防止不同主体碰撞同一幂等 key）';
COMMENT ON COLUMN public.power_idempotency_record.operation IS '稳定操作编码（如模板发布/绑定/导入，不使用 URL 全文）';
COMMENT ON COLUMN public.power_idempotency_record.key_hash IS '客户端幂等 key 的服务端 HMAC 摘要（32 字节，禁止存原文）';
COMMENT ON COLUMN public.power_idempotency_record.request_hash IS 'method/path/规范 payload 的 SHA-256（32 字节，同 key 异 hash 判冲突）';
COMMENT ON COLUMN public.power_idempotency_record.state IS '执行状态（IN_PROGRESS/SUCCEEDED/FAILED_FINAL；IN_PROGRESS 超过恢复阈值只可转可重试，不得直接删除）';
COMMENT ON COLUMN public.power_idempotency_record.http_status IS '已完成响应的 HTTP 状态码（仅终态非空）';
COMMENT ON COLUMN public.power_idempotency_record.response_payload IS '可重放响应（JSONB，有界≤16KiB 且脱敏；二维码明文 payload/shortCode 禁止入库）';
COMMENT ON COLUMN public.power_idempotency_record.result_ref IS '业务结果引用（如 qrCodeId/importJobId，供不可重放场景回查）';
COMMENT ON COLUMN public.power_idempotency_record.created_at IS '首次争抢成功时间（不可变）';
COMMENT ON COLUMN public.power_idempotency_record.updated_at IS '最近状态迁移时间（应用维护）';
COMMENT ON COLUMN public.power_idempotency_record.expires_at IS '到期时间（默认创建后 24 小时；清理任务仅分批删除已完成记录）';
