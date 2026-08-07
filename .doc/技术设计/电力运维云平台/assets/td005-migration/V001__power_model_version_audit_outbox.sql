-- ============================================================================
-- TD-005 V001 候选（STEP M1，仅供评审，禁止在生产/共享库执行）
--
-- 双基线：平台功能计划 1.4.0 / EasyAIoT 项目开发宪法 1.5.0
-- 上游：TD-005 migration 0.1.8、ADR-013 1.4.0（Proposed）
-- 执行器：受控迁移步骤执行器（ADR-013）；批准前不得执行
--
-- 步骤划分（runner 按迁移 ID 分步执行）：
--   STEP M15  独立非事务：product (tenant_id, product_identification) CONCURRENTLY
--   STEP M16  事务型：附加 product 同租户 UNIQUE 约束（短锁，USING INDEX）
--   STEP V001 事务型（本文件）：模板/版本/成员索引/审计/Outbox 五表
--   STEP V002 事务型：binding 表 + 同租户 FK NOT VALID + VALIDATE
--
-- 说明：
--   1. schema_migration_history 由 runner 引导独占维护（单一事实源），本文件不再包含；
--   2. 业务表主键统一为 BIGINT：ID 由应用统一 ID 生成策略赋值，
--      数据库不兜底生成，禁止混用序列值作为业务 ID；
--   3. 本文件必须整体在单事务内执行，任一步失败全部回滚。
-- ============================================================================

-- ----------------------------------------------------------------------------
-- 1. power_model_template
-- ----------------------------------------------------------------------------
CREATE TABLE public.power_model_template (
    id BIGINT NOT NULL,
    tenant_id BIGINT NOT NULL,
    template_code VARCHAR(64) NOT NULL,
    template_name VARCHAR(128) NOT NULL,
    device_type VARCHAR(64) NOT NULL,
    template_kind VARCHAR(16) NOT NULL CHECK (template_kind IN ('STANDARD','VENDOR')),
    owner_scope VARCHAR(16) NOT NULL CHECK (owner_scope IN ('SYSTEM','TENANT')),
    status VARCHAR(16) NOT NULL DEFAULT 'ACTIVE' CHECK (status IN ('ACTIVE','DISABLED')),
    row_version BIGINT NOT NULL DEFAULT 0,
    created_by VARCHAR(64),
    updated_by VARCHAR(64),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT power_model_template_pkey PRIMARY KEY (id),
    CONSTRAINT uq_power_model_template_tenant_code UNIQUE (tenant_id, template_code),
    CONSTRAINT uq_power_model_template_tenant_id UNIQUE (tenant_id, id),
    CONSTRAINT ck_power_model_template_scope CHECK (
        (owner_scope = 'SYSTEM' AND tenant_id = 0)
        OR (owner_scope = 'TENANT' AND tenant_id <> 0)
    )
);

COMMENT ON TABLE public.power_model_template IS '电力物模型模板身份（ADR-009/ADR-012 单一事实）';
COMMENT ON COLUMN public.power_model_template.id IS '主键（应用统一 ID 策略赋值，数据库不兜底生成）';
COMMENT ON COLUMN public.power_model_template.tenant_id IS '租户编号（SYSTEM 固定 0，TENANT 为当前租户）';
COMMENT ON COLUMN public.power_model_template.template_code IS '模板编码（规范化后不可修改）';
COMMENT ON COLUMN public.power_model_template.template_name IS '模板名称';
COMMENT ON COLUMN public.power_model_template.device_type IS '设备类型';
COMMENT ON COLUMN public.power_model_template.template_kind IS '模板种类（STANDARD/VENDOR）';
COMMENT ON COLUMN public.power_model_template.owner_scope IS '所有者范围（SYSTEM/TENANT）';
COMMENT ON COLUMN public.power_model_template.status IS '身份状态（ACTIVE/DISABLED，不代替版本生命周期）';
COMMENT ON COLUMN public.power_model_template.row_version IS '行版本（乐观锁）';
COMMENT ON COLUMN public.power_model_template.created_by IS '创建人';
COMMENT ON COLUMN public.power_model_template.updated_by IS '更新人';
COMMENT ON COLUMN public.power_model_template.created_at IS '创建时间';
COMMENT ON COLUMN public.power_model_template.updated_at IS '更新时间';

-- ----------------------------------------------------------------------------
-- 2. power_model_template_version
-- ----------------------------------------------------------------------------
CREATE TABLE public.power_model_template_version (
    id BIGINT NOT NULL,
    tenant_id BIGINT NOT NULL,
    template_id BIGINT NOT NULL,
    version VARCHAR(64) NOT NULL,
    major INTEGER NOT NULL,
    minor INTEGER NOT NULL,
    patch INTEGER NOT NULL,
    prerelease VARCHAR(64),
    lifecycle VARCHAR(16) NOT NULL CHECK (lifecycle IN ('DRAFT','PUBLISHED','DEPRECATED','RETIRED')),
    base_template_version_id BIGINT,
    base_version VARCHAR(64),
    base_content_hash VARCHAR(71),
    schema_version VARCHAR(32) NOT NULL DEFAULT '1.0.0',
    canonicalization_version VARCHAR(32) NOT NULL DEFAULT 'jcs-rfc8785-v1',
    hash_algorithm VARCHAR(16) NOT NULL DEFAULT 'SHA-256',
    content_canonical TEXT NOT NULL,
    content_json JSONB NOT NULL,
    content_hash VARCHAR(71) NOT NULL,
    source_type VARCHAR(16) NOT NULL CHECK (source_type IN ('UI','JSON','EXCEL','SYSTEM_SEED')),
    source_artifact_id VARCHAR(128),
    diff_summary JSONB NOT NULL DEFAULT '{}',
    draft_revision BIGINT,
    draft_state VARCHAR(16),
    last_activity_at TIMESTAMPTZ,
    expires_at TIMESTAMPTZ,
    published_by VARCHAR(64),
    published_at TIMESTAMPTZ,
    created_by VARCHAR(64),
    updated_by VARCHAR(64),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT power_model_template_version_pkey PRIMARY KEY (id),
    CONSTRAINT uq_power_model_template_version_unique UNIQUE (tenant_id, template_id, version),
    CONSTRAINT uq_power_model_template_version_hash UNIQUE (tenant_id, template_id, content_hash),
    CONSTRAINT uq_power_model_template_version_tenant_id UNIQUE (tenant_id, id),
    CONSTRAINT fk_power_model_template_version_template
        FOREIGN KEY (tenant_id, template_id)
        REFERENCES public.power_model_template (tenant_id, id),
    CONSTRAINT ck_power_model_template_version_base CHECK (
        (base_template_version_id IS NOT NULL AND base_version IS NOT NULL AND base_content_hash IS NOT NULL)
        OR (base_template_version_id IS NULL AND base_version IS NULL AND base_content_hash IS NULL)
    ),
    CONSTRAINT ck_power_model_template_version_hash CHECK (
        content_hash ~ '^sha256:[0-9a-f]{64}$'
        AND (base_content_hash IS NULL OR base_content_hash ~ '^sha256:[0-9a-f]{64}$')
    ),
    CONSTRAINT ck_power_model_template_version_published CHECK (
        lifecycle NOT IN ('PUBLISHED','DEPRECATED','RETIRED')
        OR (published_by IS NOT NULL AND published_at IS NOT NULL AND prerelease IS NULL)
    ),
    CONSTRAINT ck_power_model_template_version_semver CHECK (
        version ~ '^[0-9]+\.[0-9]+\.[0-9]+(-[0-9A-Za-z.-]+)?$'
        AND (prerelease IS NULL OR prerelease ~ '^[0-9A-Za-z-]+(\.[0-9A-Za-z-]+)*$')
        AND (
            (prerelease IS NULL AND version = (major || '.' || minor || '.' || patch))
            OR (prerelease IS NOT NULL AND version = (major || '.' || minor || '.' || patch || '-' || prerelease))
        )
    ),
    CONSTRAINT ck_power_model_template_version_draft_state CHECK (
        draft_state IS NULL OR draft_state IN ('ACTIVE','ABANDONED')
    )
);

CREATE INDEX idx_power_model_template_version_lifecycle
    ON public.power_model_template_version (tenant_id, template_id, lifecycle, major DESC, minor DESC, patch DESC);

COMMENT ON TABLE public.power_model_template_version IS '电力物模型模板版本（SemVer + canonical/hash 不可变）';
COMMENT ON COLUMN public.power_model_template_version.id IS '主键（应用统一 ID 策略赋值）';
COMMENT ON COLUMN public.power_model_template_version.tenant_id IS '租户编号';
COMMENT ON COLUMN public.power_model_template_version.template_id IS '模板 ID';
COMMENT ON COLUMN public.power_model_template_version.version IS '版本原文（SemVer，必须与 major/minor/patch/prerelease 一致）';
COMMENT ON COLUMN public.power_model_template_version.major IS '主版本号';
COMMENT ON COLUMN public.power_model_template_version.minor IS '次版本号';
COMMENT ON COLUMN public.power_model_template_version.patch IS '修订号';
COMMENT ON COLUMN public.power_model_template_version.prerelease IS '预发布标识（PUBLISHED 及之后必须为空）';
COMMENT ON COLUMN public.power_model_template_version.lifecycle IS '生命周期（DRAFT/PUBLISHED/DEPRECATED/RETIRED；DRAFT 只能转 PUBLISHED，废弃走 draft_state）';
COMMENT ON COLUMN public.power_model_template_version.base_template_version_id IS '厂家基线模板版本 ID（VENDOR 必填）';
COMMENT ON COLUMN public.power_model_template_version.base_version IS '厂家基线版本（VENDOR 必填）';
COMMENT ON COLUMN public.power_model_template_version.base_content_hash IS '厂家基线内容哈希（VENDOR 必填）';
COMMENT ON COLUMN public.power_model_template_version.schema_version IS 'Schema 版本';
COMMENT ON COLUMN public.power_model_template_version.canonicalization_version IS '规范化算法版本（jcs-rfc8785-v1）';
COMMENT ON COLUMN public.power_model_template_version.hash_algorithm IS '哈希算法（SHA-256）';
COMMENT ON COLUMN public.power_model_template_version.content_canonical IS '规范化内容（唯一内容事实）';
COMMENT ON COLUMN public.power_model_template_version.content_json IS '内容查询投影（同一事务生成，不参与哈希）';
COMMENT ON COLUMN public.power_model_template_version.content_hash IS '内容 SHA-256（sha256: + 64 位小写十六进制）';
COMMENT ON COLUMN public.power_model_template_version.source_type IS '来源类型（UI/JSON/EXCEL/SYSTEM_SEED）';
COMMENT ON COLUMN public.power_model_template_version.source_artifact_id IS '来源工件 ID';
COMMENT ON COLUMN public.power_model_template_version.diff_summary IS '差异摘要（结构化差异和最低 SemVer 增量）';
COMMENT ON COLUMN public.power_model_template_version.draft_revision IS '草稿乐观锁版本';
COMMENT ON COLUMN public.power_model_template_version.draft_state IS '草稿状态（ACTIVE/ABANDONED；草稿废弃不进入生命周期）';
COMMENT ON COLUMN public.power_model_template_version.last_activity_at IS '草稿最近活动时间';
COMMENT ON COLUMN public.power_model_template_version.expires_at IS '草稿过期时间';
COMMENT ON COLUMN public.power_model_template_version.published_by IS '发布人（发布后不可修改）';
COMMENT ON COLUMN public.power_model_template_version.published_at IS '发布时间（发布后不可修改）';
COMMENT ON COLUMN public.power_model_template_version.created_by IS '创建人';
COMMENT ON COLUMN public.power_model_template_version.updated_by IS '更新人';
COMMENT ON COLUMN public.power_model_template_version.created_at IS '创建时间';
COMMENT ON COLUMN public.power_model_template_version.updated_at IS '更新时间';

-- ----------------------------------------------------------------------------
-- 3. power_model_member_index（canonical 的事务内可重建投影）
-- ----------------------------------------------------------------------------
CREATE TABLE public.power_model_member_index (
    id BIGINT NOT NULL,
    tenant_id BIGINT NOT NULL,
    template_version_id BIGINT NOT NULL,
    member_type VARCHAR(32) NOT NULL CHECK (member_type IN ('PROPERTY','EVENT','SERVICE')),
    member_code VARCHAR(128) NOT NULL,
    json_pointer TEXT NOT NULL,
    member_fingerprint VARCHAR(71) NOT NULL,
    required BOOLEAN NOT NULL DEFAULT FALSE,
    semantic_type VARCHAR(64),
    CONSTRAINT power_model_member_index_pkey PRIMARY KEY (id),
    CONSTRAINT uq_power_model_member_index_member UNIQUE (tenant_id, template_version_id, member_type, member_code),
    CONSTRAINT uq_power_model_member_index_tenant_id UNIQUE (tenant_id, id),
    CONSTRAINT fk_power_model_member_index_version
        FOREIGN KEY (tenant_id, template_version_id)
        REFERENCES public.power_model_template_version (tenant_id, id)
);

CREATE INDEX idx_power_model_member_index_required
    ON public.power_model_member_index (tenant_id, template_version_id, member_type, required)
    WHERE required = TRUE;
CREATE INDEX idx_power_model_member_index_semantic
    ON public.power_model_member_index (tenant_id, template_version_id, member_type, semantic_type);
CREATE INDEX idx_power_model_member_index_fingerprint
    ON public.power_model_member_index (member_fingerprint);

COMMENT ON TABLE public.power_model_member_index IS '模板版本成员索引（canonical 事务内可重建投影，不接受独立 CRUD）';
COMMENT ON COLUMN public.power_model_member_index.id IS '主键（应用统一 ID 策略赋值）';
COMMENT ON COLUMN public.power_model_member_index.tenant_id IS '租户编号';
COMMENT ON COLUMN public.power_model_member_index.template_version_id IS '模板版本 ID';
COMMENT ON COLUMN public.power_model_member_index.member_type IS '成员类型（PROPERTY/EVENT/SERVICE）';
COMMENT ON COLUMN public.power_model_member_index.member_code IS '成员编码';
COMMENT ON COLUMN public.power_model_member_index.json_pointer IS '成员在 canonical 中的 JSON 指针';
COMMENT ON COLUMN public.power_model_member_index.member_fingerprint IS '成员指纹（用于版本差异候选定位）';
COMMENT ON COLUMN public.power_model_member_index.required IS '是否必选成员';
COMMENT ON COLUMN public.power_model_member_index.semantic_type IS '语义类型';

-- ----------------------------------------------------------------------------
-- 4. power_model_audit（追加写、不可变）
-- ----------------------------------------------------------------------------
CREATE TABLE public.power_model_audit (
    id BIGINT NOT NULL,
    audit_event_id UUID NOT NULL,
    tenant_id BIGINT NOT NULL,
    operation VARCHAR(64) NOT NULL CHECK (operation IN (
        'TEMPLATE_PUBLISHED','TEMPLATE_DEPRECATED','TEMPLATE_RETIRED',
        'BINDING_APPLIED','BINDING_UPGRADED','BINDING_ROLLED_BACK'
    )),
    aggregate_type VARCHAR(32) NOT NULL,
    aggregate_id VARCHAR(128) NOT NULL,
    template_code VARCHAR(64),
    template_version VARCHAR(64),
    product_id BIGINT,
    product_identification VARCHAR(100),
    binding_revision BIGINT,
    principal_type VARCHAR(16) NOT NULL CHECK (principal_type IN ('USER','SERVICE')),
    principal_id VARCHAR(64) NOT NULL,
    request_id VARCHAR(64) NOT NULL,
    trace_id VARCHAR(128),
    source_type VARCHAR(16),
    source_artifact_id VARCHAR(128),
    before_hash VARCHAR(71),
    after_hash VARCHAR(71),
    semver_bump VARCHAR(16),
    reason_code VARCHAR(64),
    reason_summary VARCHAR(512),
    diff_summary JSONB NOT NULL DEFAULT '{}',
    occurred_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT power_model_audit_pkey PRIMARY KEY (id),
    CONSTRAINT uq_power_model_audit_event UNIQUE (tenant_id, audit_event_id),
    CONSTRAINT ck_power_model_audit_diff_bound CHECK (
        octet_length(diff_summary::text) <= 65536
    )
);

CREATE INDEX idx_power_model_audit_aggregate
    ON public.power_model_audit (tenant_id, aggregate_type, aggregate_id, occurred_at DESC);

COMMENT ON TABLE public.power_model_audit IS '电力物模型领域审计（同事务追加写、不可变）';
COMMENT ON COLUMN public.power_model_audit.id IS '主键（应用统一 ID 策略赋值）';
COMMENT ON COLUMN public.power_model_audit.audit_event_id IS '审计事件 UUID（应用生成 UUID v4）';
COMMENT ON COLUMN public.power_model_audit.tenant_id IS '租户编号';
COMMENT ON COLUMN public.power_model_audit.operation IS '操作类型（模板发布/生命周期/绑定应用/升级/回滚）';
COMMENT ON COLUMN public.power_model_audit.aggregate_type IS '聚合类型';
COMMENT ON COLUMN public.power_model_audit.aggregate_id IS '聚合 ID';
COMMENT ON COLUMN public.power_model_audit.template_code IS '模板编码审计副本';
COMMENT ON COLUMN public.power_model_audit.template_version IS '模板版本审计副本';
COMMENT ON COLUMN public.power_model_audit.product_id IS '产品 ID 审计副本';
COMMENT ON COLUMN public.power_model_audit.product_identification IS '产品标识审计副本';
COMMENT ON COLUMN public.power_model_audit.binding_revision IS '绑定修订号审计副本';
COMMENT ON COLUMN public.power_model_audit.principal_type IS '主体类型（USER/SERVICE）';
COMMENT ON COLUMN public.power_model_audit.principal_id IS '主体 ID';
COMMENT ON COLUMN public.power_model_audit.request_id IS '请求 ID';
COMMENT ON COLUMN public.power_model_audit.trace_id IS '链路追踪 ID';
COMMENT ON COLUMN public.power_model_audit.source_type IS '来源类型';
COMMENT ON COLUMN public.power_model_audit.source_artifact_id IS '来源工件 ID';
COMMENT ON COLUMN public.power_model_audit.before_hash IS '变更前内容哈希';
COMMENT ON COLUMN public.power_model_audit.after_hash IS '变更后内容哈希';
COMMENT ON COLUMN public.power_model_audit.semver_bump IS 'SemVer 增量（MAJOR/MINOR/PATCH）';
COMMENT ON COLUMN public.power_model_audit.reason_code IS '原因编码';
COMMENT ON COLUMN public.power_model_audit.reason_summary IS '原因摘要（必须脱敏）';
COMMENT ON COLUMN public.power_model_audit.diff_summary IS '差异摘要（有界 JSONB，正文 ≤64KiB）';
COMMENT ON COLUMN public.power_model_audit.occurred_at IS '发生时间（服务端 UTC 事实）';

CREATE OR REPLACE FUNCTION public.fn_power_model_audit_append_only()
RETURNS trigger
LANGUAGE plpgsql
AS $$
BEGIN
    RAISE EXCEPTION 'power_model_audit is append-only';
END;
$$;

DROP TRIGGER IF EXISTS trg_power_model_audit_append_only ON public.power_model_audit;
CREATE TRIGGER trg_power_model_audit_append_only
    BEFORE UPDATE OR DELETE ON public.power_model_audit
    FOR EACH ROW EXECUTE FUNCTION public.fn_power_model_audit_append_only();

-- 候选权限基线：审计表只允许 INSERT/SELECT；角色授权见 roles_candidate.sql（M-09）
REVOKE UPDATE, DELETE ON public.power_model_audit FROM PUBLIC;

-- ----------------------------------------------------------------------------
-- 5. power_model_release_outbox
-- ----------------------------------------------------------------------------
CREATE TABLE public.power_model_release_outbox (
    id BIGINT NOT NULL,
    event_id UUID NOT NULL,
    tenant_id BIGINT NOT NULL,
    audit_event_id UUID NOT NULL,
    aggregate_type VARCHAR(32) NOT NULL,
    aggregate_id VARCHAR(128) NOT NULL,
    event_type VARCHAR(96) NOT NULL,
    schema_version INTEGER NOT NULL DEFAULT 1,
    payload JSONB NOT NULL,
    payload_hash VARCHAR(71) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING' CHECK (status IN ('PENDING','PUBLISHING','PUBLISHED','DEAD_LETTER')),
    retry_count INTEGER NOT NULL DEFAULT 0,
    max_retries INTEGER NOT NULL DEFAULT 12,
    next_attempt_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    lease_until TIMESTAMPTZ,
    lease_owner VARCHAR(128),
    last_error_code VARCHAR(64),
    last_error_digest VARCHAR(128),
    published_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT power_model_release_outbox_pkey PRIMARY KEY (id),
    CONSTRAINT uq_power_model_release_outbox_event UNIQUE (event_id),
    CONSTRAINT uq_power_model_release_outbox_audit_event UNIQUE (tenant_id, audit_event_id, event_type),
    CONSTRAINT fk_power_model_release_outbox_audit
        FOREIGN KEY (tenant_id, audit_event_id)
        REFERENCES public.power_model_audit (tenant_id, audit_event_id),
    CONSTRAINT ck_power_model_release_outbox_hash CHECK (
        payload_hash ~ '^sha256:[0-9a-f]{64}$'
    ),
    CONSTRAINT ck_power_model_release_outbox_payload_bound CHECK (
        octet_length(payload::text) <= 2097152
    )
);

CREATE INDEX idx_power_model_release_outbox_dispatch
    ON public.power_model_release_outbox (status, next_attempt_at, created_at, id)
    WHERE status IN ('PENDING','PUBLISHING');
CREATE INDEX idx_power_model_release_outbox_lease
    ON public.power_model_release_outbox (status, lease_until)
    WHERE status = 'PUBLISHING';
CREATE INDEX idx_power_model_release_outbox_aggregate
    ON public.power_model_release_outbox (tenant_id, aggregate_type, aggregate_id, created_at DESC);
CREATE INDEX idx_power_model_release_outbox_retention
    ON public.power_model_release_outbox (status, published_at, id)
    WHERE status = 'PUBLISHED';

COMMENT ON TABLE public.power_model_release_outbox IS '模板发布 Outbox（业务事务同提交，异步投递）';
COMMENT ON COLUMN public.power_model_release_outbox.id IS '主键（应用统一 ID 策略赋值）';
COMMENT ON COLUMN public.power_model_release_outbox.event_id IS '事件 UUID（应用在事务前生成 v4，全局唯一）';
COMMENT ON COLUMN public.power_model_release_outbox.tenant_id IS '租户编号';
COMMENT ON COLUMN public.power_model_release_outbox.audit_event_id IS '审计事件 UUID（复合 FK 指向领域审计）';
COMMENT ON COLUMN public.power_model_release_outbox.aggregate_type IS '聚合类型';
COMMENT ON COLUMN public.power_model_release_outbox.aggregate_id IS '聚合 ID';
COMMENT ON COLUMN public.power_model_release_outbox.event_type IS '事件类型（名称包含主版本，如 _V1）';
COMMENT ON COLUMN public.power_model_release_outbox.schema_version IS '事件 Schema 主版本';
COMMENT ON COLUMN public.power_model_release_outbox.payload IS '事件载荷（有界 JSONB，序列化正文 ≤2MiB，与 max-payload-bytes 对齐）';
COMMENT ON COLUMN public.power_model_release_outbox.payload_hash IS '事件载荷 SHA-256';
COMMENT ON COLUMN public.power_model_release_outbox.status IS '发布状态（PENDING/PUBLISHING/PUBLISHED/DEAD_LETTER）';
COMMENT ON COLUMN public.power_model_release_outbox.retry_count IS '已重试次数';
COMMENT ON COLUMN public.power_model_release_outbox.max_retries IS '最大重试次数（候选值待压测冻结）';
COMMENT ON COLUMN public.power_model_release_outbox.next_attempt_at IS '下次尝试时间';
COMMENT ON COLUMN public.power_model_release_outbox.lease_until IS '租约到期时间';
COMMENT ON COLUMN public.power_model_release_outbox.lease_owner IS '租约持有者（pmoutbox-{instanceId}）';
COMMENT ON COLUMN public.power_model_release_outbox.last_error_code IS '最近错误码';
COMMENT ON COLUMN public.power_model_release_outbox.last_error_digest IS '最近错误摘要（脱敏）';
COMMENT ON COLUMN public.power_model_release_outbox.published_at IS '发布时间';
COMMENT ON COLUMN public.power_model_release_outbox.created_at IS '创建时间';
COMMENT ON COLUMN public.power_model_release_outbox.updated_at IS '更新时间';

-- ----------------------------------------------------------------------------
-- 候选触发器：模板身份、版本内容与生命周期（M-02/M-03 处置版）
-- ----------------------------------------------------------------------------
CREATE OR REPLACE FUNCTION public.fn_power_model_template_code_immutable()
RETURNS trigger
LANGUAGE plpgsql
AS $$
BEGIN
    IF TG_OP = 'UPDATE' AND (
        NEW.template_code IS DISTINCT FROM OLD.template_code
        OR NEW.tenant_id IS DISTINCT FROM OLD.tenant_id
        OR NEW.owner_scope IS DISTINCT FROM OLD.owner_scope
    ) THEN
        RAISE EXCEPTION 'power_model_template identity fields are immutable';
    END IF;
    RETURN NEW;
END;
$$;

DROP TRIGGER IF EXISTS trg_power_model_template_code_immutable ON public.power_model_template;
CREATE TRIGGER trg_power_model_template_code_immutable
    BEFORE UPDATE ON public.power_model_template
    FOR EACH ROW EXECUTE FUNCTION public.fn_power_model_template_code_immutable();

CREATE OR REPLACE FUNCTION public.fn_power_model_template_version_immutable()
RETURNS trigger
LANGUAGE plpgsql
AS $$
BEGIN
    -- 身份列全生命周期不可变（含 DRAFT）
    IF TG_OP = 'UPDATE' AND (
        NEW.id IS DISTINCT FROM OLD.id
        OR NEW.tenant_id IS DISTINCT FROM OLD.tenant_id
        OR NEW.template_id IS DISTINCT FROM OLD.template_id
    ) THEN
        RAISE EXCEPTION 'template version identity fields are immutable';
    END IF;
    -- 已发布内容、版本、基线、来源与发布事实不可变
    IF TG_OP = 'UPDATE' AND OLD.lifecycle <> 'DRAFT' AND (
        NEW.content_canonical IS DISTINCT FROM OLD.content_canonical
        OR NEW.content_json IS DISTINCT FROM OLD.content_json
        OR NEW.content_hash IS DISTINCT FROM OLD.content_hash
        OR NEW.version IS DISTINCT FROM OLD.version
        OR NEW.major IS DISTINCT FROM OLD.major
        OR NEW.minor IS DISTINCT FROM OLD.minor
        OR NEW.patch IS DISTINCT FROM OLD.patch
        OR NEW.prerelease IS DISTINCT FROM OLD.prerelease
        OR NEW.base_template_version_id IS DISTINCT FROM OLD.base_template_version_id
        OR NEW.base_version IS DISTINCT FROM OLD.base_version
        OR NEW.base_content_hash IS DISTINCT FROM OLD.base_content_hash
        OR NEW.source_type IS DISTINCT FROM OLD.source_type
        OR NEW.source_artifact_id IS DISTINCT FROM OLD.source_artifact_id
        OR NEW.published_by IS DISTINCT FROM OLD.published_by
        OR NEW.published_at IS DISTINCT FROM OLD.published_at
    ) THEN
        RAISE EXCEPTION 'published version content/version/base/source/publish fields are immutable';
    END IF;
    -- 生命周期合法迁移：DRAFT 只能保持 DRAFT 或转 PUBLISHED；废弃走 draft_state=ABANDONED
    IF OLD.lifecycle = 'DRAFT' AND NEW.lifecycle NOT IN ('DRAFT','PUBLISHED') THEN
        RAISE EXCEPTION 'invalid lifecycle transition from DRAFT';
    END IF;
    IF OLD.lifecycle = 'PUBLISHED' AND NEW.lifecycle NOT IN ('DEPRECATED','RETIRED') THEN
        RAISE EXCEPTION 'invalid lifecycle transition from PUBLISHED';
    END IF;
    IF OLD.lifecycle = 'DEPRECATED' AND NEW.lifecycle <> 'RETIRED' THEN
        RAISE EXCEPTION 'invalid lifecycle transition from DEPRECATED';
    END IF;
    IF OLD.lifecycle = 'RETIRED' AND NEW.lifecycle IS DISTINCT FROM OLD.lifecycle THEN
        RAISE EXCEPTION 'RETIRED is terminal';
    END IF;
    RETURN NEW;
END;
$$;

DROP TRIGGER IF EXISTS trg_power_model_template_version_immutable ON public.power_model_template_version;
CREATE TRIGGER trg_power_model_template_version_immutable
    BEFORE UPDATE ON public.power_model_template_version
    FOR EACH ROW EXECUTE FUNCTION public.fn_power_model_template_version_immutable();
