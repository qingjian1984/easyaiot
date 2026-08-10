-- ============================================================================
-- TD-001 V003 候选（仅供评审，禁止在生产/共享库执行）
--
-- 双基线：平台功能计划 1.4.0 / EasyAIoT 项目开发宪法 1.5.0
-- 上游：TD-001 1.0.6 §4.1/§6.2、ADR-013 1.5.1（Accepted）、ADR-014 1.3.6（Accepted）
-- 执行器：受控迁移步骤执行器（ADR-013 runner 增链步骤 V003）；批准前不得执行
--
-- 覆盖 TD-001 §6.2 协调器四个端口的持久化事实：
--   1. iot_collector_config_release    —— §4.1 发布单（CollectorConfigReleasePort）
--   2. power_model_template_reference_mark —— 生命周期引用标记（PowerModelTemplateReferencePort）
--   3. power_model_coordination_audit  —— 协调审计（PowerModelCoordinationAuditPort）
--
-- 说明：
--   1. CollectorWorkloadImpactPort 数据源（productId→活动 workload 的设备侧可见性）
--      经 ADR-015 处置：新建 collector_workload_binding_projection 投影表（可变，
--      iot-device 发布单状态机 upsert，不依赖 iot-node 事件同步）。ADR-015 当前
--      Proposed，待评审通过后增链本文件（第四张表）或独立 V004，落库 MUST 经
--      ADR-013 runner；本文件当前不包含该投影表 DDL；
--   2. 业务表主键统一为 BIGINT：ID 由应用统一 ID 生成策略赋值，数据库不兜底生成；
--   3. 本文件必须整体在单事务内执行，任一步失败全部回滚；
--   4. 全部 additive，不触碰任何既有表。
-- ============================================================================

-- ----------------------------------------------------------------------------
-- 1. iot_collector_config_release（TD-001 §4.1 发布单）
-- ----------------------------------------------------------------------------
CREATE TABLE public.iot_collector_config_release (
    id BIGINT NOT NULL,
    tenant_id BIGINT NOT NULL,
    site_id BIGINT NOT NULL,
    site_code VARCHAR(64) NOT NULL,
    workload_id VARCHAR(128) NOT NULL,
    node_id BIGINT NOT NULL,
    config_version BIGINT NOT NULL,
    schema_version VARCHAR(32) NOT NULL,
    canonicalization_version VARCHAR(32) NOT NULL,
    payload_canonical TEXT NOT NULL,
    payload JSONB NOT NULL,
    payload_sha256 CHAR(64) NOT NULL,
    canonical_length_bytes BIGINT NOT NULL,
    status VARCHAR(16) NOT NULL CHECK (status IN (
        'DRAFT','VALIDATED','PUBLISHED','APPLIED','FAILED','APPLY_TIMEOUT','ROLLED_BACK'
    )),
    base_version BIGINT,
    rollback_from_version BIGINT,
    published_by BIGINT,
    published_at TIMESTAMPTZ,
    applied_version BIGINT,
    applied_at TIMESTAMPTZ,
    error_code VARCHAR(64),
    error_detail TEXT,
    row_version BIGINT NOT NULL DEFAULT 0,
    created_by VARCHAR(64),
    updated_by VARCHAR(64),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT iot_collector_config_release_pkey PRIMARY KEY (id),
    CONSTRAINT uq_iot_collector_config_release_version
        UNIQUE (tenant_id, workload_id, config_version),
    CONSTRAINT uq_iot_collector_config_release_tenant_id UNIQUE (tenant_id, id),
    CONSTRAINT ck_iot_collector_config_release_hash CHECK (
        payload_sha256 ~ '^[0-9a-f]{64}$'
    ),
    CONSTRAINT ck_iot_collector_config_release_canonical_len CHECK (
        canonical_length_bytes = octet_length(payload_canonical)
    ),
    CONSTRAINT ck_iot_collector_config_release_published CHECK (
        status NOT IN ('PUBLISHED','APPLIED','APPLY_TIMEOUT','ROLLED_BACK')
        OR (published_by IS NOT NULL AND published_at IS NOT NULL)
    )
);

CREATE INDEX idx_iot_collector_config_release_site
    ON public.iot_collector_config_release (tenant_id, site_id, config_version DESC);
CREATE INDEX idx_iot_collector_config_release_site_code
    ON public.iot_collector_config_release (tenant_id, site_code, config_version DESC);
CREATE INDEX idx_iot_collector_config_release_workload
    ON public.iot_collector_config_release (tenant_id, workload_id, status, config_version DESC);

COMMENT ON TABLE public.iot_collector_config_release IS 'collector 配置发布单（TD-001 §4.1，快照不可原地修改，configVersion 单调递增）';
COMMENT ON COLUMN public.iot_collector_config_release.id IS '主键（应用统一 ID 策略赋值，数据库不兜底生成）';
COMMENT ON COLUMN public.iot_collector_config_release.tenant_id IS '租户编号';
COMMENT ON COLUMN public.iot_collector_config_release.site_id IS '站点内部主键';
COMMENT ON COLUMN public.iot_collector_config_release.site_code IS '站点业务编码（创建时固化，不可变，用于审计与对外查询）';
COMMENT ON COLUMN public.iot_collector_config_release.workload_id IS '目标 collector workload 标识';
COMMENT ON COLUMN public.iot_collector_config_release.node_id IS '目标节点内部主键';
COMMENT ON COLUMN public.iot_collector_config_release.config_version IS '配置版本（租户+workload 唯一，单调递增，回滚也生成新版本）';
COMMENT ON COLUMN public.iot_collector_config_release.schema_version IS '快照 Schema 版本（首版 1.0）';
COMMENT ON COLUMN public.iot_collector_config_release.canonicalization_version IS '规范化算法版本（首版 jcs-rfc8785-v1）';
COMMENT ON COLUMN public.iot_collector_config_release.payload_canonical IS 'UTF-8 canonical JSON 文本（应用层单次生成，写库/哈希/下发复用同一字节序列）';
COMMENT ON COLUMN public.iot_collector_config_release.payload IS 'canonical 文本的 jsonb 投影（仅供字段查询，不是哈希输入）';
COMMENT ON COLUMN public.iot_collector_config_release.payload_sha256 IS 'UTF-8(payload_canonical) 的 SHA-256（64 位小写十六进制）';
COMMENT ON COLUMN public.iot_collector_config_release.canonical_length_bytes IS 'canonical UTF-8 字节长度（传输完整性校验）';
COMMENT ON COLUMN public.iot_collector_config_release.status IS '发布状态（DRAFT/VALIDATED/PUBLISHED/APPLIED/FAILED/APPLY_TIMEOUT/ROLLED_BACK）';
COMMENT ON COLUMN public.iot_collector_config_release.base_version IS '差异与乐观锁基线版本';
COMMENT ON COLUMN public.iot_collector_config_release.rollback_from_version IS '回滚来源版本（回滚单必填，版本号不倒退）';
COMMENT ON COLUMN public.iot_collector_config_release.published_by IS '发布人';
COMMENT ON COLUMN public.iot_collector_config_release.published_at IS '发布时间';
COMMENT ON COLUMN public.iot_collector_config_release.applied_version IS '运行端观察到的应用版本';
COMMENT ON COLUMN public.iot_collector_config_release.applied_at IS '运行端应用时间';
COMMENT ON COLUMN public.iot_collector_config_release.error_code IS '结构化失败错误码';
COMMENT ON COLUMN public.iot_collector_config_release.error_detail IS '失败详情（必须脱敏）';
COMMENT ON COLUMN public.iot_collector_config_release.row_version IS '行版本（乐观锁）';
COMMENT ON COLUMN public.iot_collector_config_release.created_by IS '创建人';
COMMENT ON COLUMN public.iot_collector_config_release.updated_by IS '更新人';
COMMENT ON COLUMN public.iot_collector_config_release.created_at IS '创建时间';
COMMENT ON COLUMN public.iot_collector_config_release.updated_at IS '更新时间';

-- ----------------------------------------------------------------------------
-- 2. power_model_template_reference_mark（生命周期引用标记）
-- ----------------------------------------------------------------------------
CREATE TABLE public.power_model_template_reference_mark (
    id BIGINT NOT NULL,
    tenant_id BIGINT NOT NULL,
    template_code VARCHAR(64) NOT NULL,
    template_version VARCHAR(64) NOT NULL,
    from_lifecycle VARCHAR(16) NOT NULL CHECK (from_lifecycle IN ('DRAFT','PUBLISHED','DEPRECATED','RETIRED')),
    to_lifecycle VARCHAR(16) NOT NULL CHECK (to_lifecycle IN ('DRAFT','PUBLISHED','DEPRECATED','RETIRED')),
    source_event_id UUID NOT NULL,
    marked_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT power_model_template_reference_mark_pkey PRIMARY KEY (id),
    CONSTRAINT uq_power_model_template_reference_mark UNIQUE
        (tenant_id, template_code, template_version, to_lifecycle)
);

COMMENT ON TABLE public.power_model_template_reference_mark IS '模板生命周期引用标记（TD-001 §6.2：只标记引用，绝不改写已发布快照；人工发布确认页据此提示）';
COMMENT ON COLUMN public.power_model_template_reference_mark.id IS '主键（应用统一 ID 策略赋值）';
COMMENT ON COLUMN public.power_model_template_reference_mark.tenant_id IS '租户编号';
COMMENT ON COLUMN public.power_model_template_reference_mark.template_code IS '模板编码';
COMMENT ON COLUMN public.power_model_template_reference_mark.template_version IS '模板版本';
COMMENT ON COLUMN public.power_model_template_reference_mark.from_lifecycle IS '变更前生命周期';
COMMENT ON COLUMN public.power_model_template_reference_mark.to_lifecycle IS '变更后生命周期（如 DEPRECATED/RETIRED）';
COMMENT ON COLUMN public.power_model_template_reference_mark.source_event_id IS '来源事件 UUID（幂等溯源）';
COMMENT ON COLUMN public.power_model_template_reference_mark.marked_at IS '标记时间（重复事件刷新）';

-- ----------------------------------------------------------------------------
-- 3. power_model_coordination_audit（协调审计，追加写、不可变）
-- ----------------------------------------------------------------------------
CREATE TABLE public.power_model_coordination_audit (
    id BIGINT NOT NULL,
    event_id UUID NOT NULL,
    tenant_id BIGINT NOT NULL,
    event_type VARCHAR(96) NOT NULL,
    action VARCHAR(64) NOT NULL CHECK (action IN (
        'TEMPLATE_PUBLISHED_NOTED','LIFECYCLE_REFERENCE_MARKED',
        'IMPACT_EMPTY','REGENERATION_DRAFTS_CREATED'
    )),
    detail VARCHAR(512) NOT NULL,
    occurred_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT power_model_coordination_audit_pkey PRIMARY KEY (id),
    CONSTRAINT uq_power_model_coordination_audit_event_action
        UNIQUE (tenant_id, event_id, action)
);

CREATE INDEX idx_power_model_coordination_audit_tenant_time
    ON public.power_model_coordination_audit (tenant_id, occurred_at DESC);

COMMENT ON TABLE public.power_model_coordination_audit IS '电力物模型事件协调审计（TD-001 §6.2：追加写不可变，未产生发布单也有持久证据）';
COMMENT ON COLUMN public.power_model_coordination_audit.id IS '主键（应用统一 ID 策略赋值）';
COMMENT ON COLUMN public.power_model_coordination_audit.event_id IS '来源事件 UUID（与 Inbox event_id 一致）';
COMMENT ON COLUMN public.power_model_coordination_audit.tenant_id IS '租户编号';
COMMENT ON COLUMN public.power_model_coordination_audit.event_type IS '来源事件类型（含主版本）';
COMMENT ON COLUMN public.power_model_coordination_audit.action IS '处置动作（发布已记录/引用标记/影响面空集/再生发布单数量）';
COMMENT ON COLUMN public.power_model_coordination_audit.detail IS '处置摘要（必须脱敏且有界 ≤512 字符，绝不携带载荷正文）';
COMMENT ON COLUMN public.power_model_coordination_audit.occurred_at IS '发生时间（服务端 UTC 事实）';

CREATE OR REPLACE FUNCTION public.fn_power_model_coordination_audit_append_only()
RETURNS trigger
LANGUAGE plpgsql
AS $$
BEGIN
    RAISE EXCEPTION 'power_model_coordination_audit is append-only';
END;
$$;

DROP TRIGGER IF EXISTS trg_power_model_coordination_audit_append_only
    ON public.power_model_coordination_audit;
CREATE TRIGGER trg_power_model_coordination_audit_append_only
    BEFORE UPDATE OR DELETE ON public.power_model_coordination_audit
    FOR EACH ROW EXECUTE FUNCTION public.fn_power_model_coordination_audit_append_only();

REVOKE UPDATE, DELETE ON public.power_model_coordination_audit FROM PUBLIC;

-- ----------------------------------------------------------------------------
-- 4. 发布单快照不可变触发器（§4.1：快照不可原地修改）
-- ----------------------------------------------------------------------------
CREATE OR REPLACE FUNCTION public.fn_iot_collector_config_release_immutable()
RETURNS trigger
LANGUAGE plpgsql
AS $$
BEGIN
    IF TG_OP = 'UPDATE' AND (
        NEW.id IS DISTINCT FROM OLD.id
        OR NEW.tenant_id IS DISTINCT FROM OLD.tenant_id
        OR NEW.site_id IS DISTINCT FROM OLD.site_id
        OR NEW.site_code IS DISTINCT FROM OLD.site_code
        OR NEW.workload_id IS DISTINCT FROM OLD.workload_id
        OR NEW.config_version IS DISTINCT FROM OLD.config_version
        OR NEW.schema_version IS DISTINCT FROM OLD.schema_version
        OR NEW.canonicalization_version IS DISTINCT FROM OLD.canonicalization_version
        OR NEW.payload_canonical IS DISTINCT FROM OLD.payload_canonical
        OR NEW.payload IS DISTINCT FROM OLD.payload
        OR NEW.payload_sha256 IS DISTINCT FROM OLD.payload_sha256
        OR NEW.canonical_length_bytes IS DISTINCT FROM OLD.canonical_length_bytes
    ) THEN
        RAISE EXCEPTION 'iot_collector_config_release snapshot fields are immutable';
    END IF;
    RETURN NEW;
END;
$$;

DROP TRIGGER IF EXISTS trg_iot_collector_config_release_immutable
    ON public.iot_collector_config_release;
CREATE TRIGGER trg_iot_collector_config_release_immutable
    BEFORE UPDATE ON public.iot_collector_config_release
    FOR EACH ROW EXECUTE FUNCTION public.fn_iot_collector_config_release_immutable();
