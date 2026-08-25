-- ============================================================================
-- TD-006 V011 告警核心候选 DDL（仅供评审，禁止在生产/共享库直接执行）
-- 双基线：平台功能计划 1.5.0 / EasyAIoT 项目开发宪法 1.6.0
-- 上游：SPEC-005 0.1.2、ADR-010 1.1.0、TD-006 0.1.2
-- 业务表主键由应用统一 ID 策略赋值；所有时间为 TIMESTAMPTZ。
-- 本文件须由 ADR-013 受控 runner 单事务执行；本文件不声明 BEGIN/COMMIT；批准前不得落库。
-- ============================================================================

CREATE TABLE public.alarm_rule (
    id BIGINT NOT NULL,
    tenant_id BIGINT NOT NULL,
    site_id BIGINT NOT NULL,
    rule_code VARCHAR(64) NOT NULL,
    rule_name VARCHAR(128) NOT NULL,
    rule_kind VARCHAR(32) NOT NULL CHECK (rule_kind IN ('THRESHOLD','COMMUNICATION','COMPOSITE','DEVICE_EVENT')),
    capability_code VARCHAR(64) NOT NULL CHECK (capability_code IN ('power.alarm.core','power.alarm.advanced')),
    status VARCHAR(16) NOT NULL DEFAULT 'ACTIVE' CHECK (status IN ('ACTIVE','DISABLED','RETIRED')),
    row_version BIGINT NOT NULL DEFAULT 0,
    created_by VARCHAR(64) NOT NULL,
    updated_by VARCHAR(64) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT alarm_rule_pkey PRIMARY KEY (id),
    CONSTRAINT uq_alarm_rule_tenant_code UNIQUE (tenant_id, rule_code),
    CONSTRAINT uq_alarm_rule_tenant_id UNIQUE (tenant_id, id),
    CONSTRAINT ck_alarm_rule_tenant_site CHECK (tenant_id > 0 AND site_id > 0)
);

CREATE INDEX idx_alarm_rule_site_status
    ON public.alarm_rule (tenant_id, site_id, status, id);

COMMENT ON TABLE public.alarm_rule IS '统一告警规则稳定身份；规则内容由不可变版本表保存';
COMMENT ON COLUMN public.alarm_rule.id IS '主键，由应用统一 ID 策略赋值';
COMMENT ON COLUMN public.alarm_rule.tenant_id IS '租户编号，所有查询和写入必须显式校验';
COMMENT ON COLUMN public.alarm_rule.site_id IS '规则所属站点稳定标识，不建立跨服务数据库外键';
COMMENT ON COLUMN public.alarm_rule.rule_code IS '租户内唯一规则编码，创建后不可修改';
COMMENT ON COLUMN public.alarm_rule.rule_name IS '规则显示名称';
COMMENT ON COLUMN public.alarm_rule.rule_kind IS '规则类型：阈值、通信、复合或设备事件';
COMMENT ON COLUMN public.alarm_rule.capability_code IS '规则所需 capability；core/advanced 由统一 manifest 决定';
COMMENT ON COLUMN public.alarm_rule.status IS '规则身份状态，不代替版本生命周期';
COMMENT ON COLUMN public.alarm_rule.row_version IS '乐观锁版本，每次业务更新递增';
COMMENT ON COLUMN public.alarm_rule.created_by IS '创建人稳定标识';
COMMENT ON COLUMN public.alarm_rule.updated_by IS '最后修改人稳定标识';
COMMENT ON COLUMN public.alarm_rule.created_at IS '创建时间，UTC 时间事实';
COMMENT ON COLUMN public.alarm_rule.updated_at IS '最后修改时间，UTC 时间事实';

CREATE TABLE public.alarm_rule_version (
    id BIGINT NOT NULL,
    tenant_id BIGINT NOT NULL,
    rule_id BIGINT NOT NULL,
    version VARCHAR(32) NOT NULL,
    lifecycle VARCHAR(16) NOT NULL CHECK (lifecycle IN ('DRAFT','VALIDATED','PUBLISHED','RETIRED')),
    severity VARCHAR(16) NOT NULL CHECK (severity IN ('INFO','NORMAL','IMPORTANT','EMERGENCY')),
    condition_tree JSONB NOT NULL,
    recovery_policy JSONB NOT NULL DEFAULT '{}',
    schedule_policy JSONB NOT NULL DEFAULT '{}',
    canonicalization_version VARCHAR(32) NOT NULL DEFAULT 'jcs-rfc8785-v1',
    content_hash VARCHAR(71) NOT NULL CHECK (content_hash ~ '^sha256:[0-9a-f]{64}$'),
    published_by VARCHAR(64),
    published_at TIMESTAMPTZ,
    created_by VARCHAR(64) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT alarm_rule_version_pkey PRIMARY KEY (id),
    CONSTRAINT uq_alarm_rule_version UNIQUE (tenant_id, rule_id, version),
    CONSTRAINT uq_alarm_rule_version_hash UNIQUE (tenant_id, rule_id, content_hash),
    CONSTRAINT uq_alarm_rule_version_tenant_rule_id UNIQUE (tenant_id, rule_id, id),
    CONSTRAINT fk_alarm_rule_version_rule FOREIGN KEY (tenant_id, rule_id)
        REFERENCES public.alarm_rule (tenant_id, id),
    CONSTRAINT ck_alarm_rule_version_published CHECK (
        lifecycle NOT IN ('PUBLISHED','RETIRED')
        OR (published_by IS NOT NULL AND published_at IS NOT NULL)
    ),
    CONSTRAINT ck_alarm_rule_version_payload_size CHECK (
        octet_length(condition_tree::text) <= 262144
        AND octet_length(recovery_policy::text) <= 65536
        AND octet_length(schedule_policy::text) <= 65536
    ),
    CONSTRAINT ck_alarm_rule_version_tenant CHECK (tenant_id > 0)
);

CREATE INDEX idx_alarm_rule_version_lifecycle
ON public.alarm_rule_version (tenant_id, rule_id, lifecycle, created_at DESC);

CREATE UNIQUE INDEX uq_alarm_rule_version_published
ON public.alarm_rule_version (tenant_id, rule_id)
WHERE lifecycle = 'PUBLISHED';

COMMENT ON TABLE public.alarm_rule_version IS '不可变告警规则版本；发布版本禁止原地覆盖';
COMMENT ON COLUMN public.alarm_rule_version.id IS '版本主键，由应用统一 ID 策略赋值';
COMMENT ON COLUMN public.alarm_rule_version.tenant_id IS '租户编号';
COMMENT ON COLUMN public.alarm_rule_version.rule_id IS '所属规则稳定身份';
COMMENT ON COLUMN public.alarm_rule_version.version IS '规则版本字符串，由应用校验版本格式和递增关系';
COMMENT ON COLUMN public.alarm_rule_version.lifecycle IS '版本生命周期：草稿、已校验、已发布、已退役；仅生命周期元数据可按事务转换，内容字段发布后不可覆盖';
COMMENT ON COLUMN public.alarm_rule_version.severity IS '该版本命中时产生的四级告警等级';
COMMENT ON COLUMN public.alarm_rule_version.condition_tree IS '受控声明式条件树，禁止脚本、SQL、SpEL 和用户函数';
COMMENT ON COLUMN public.alarm_rule_version.recovery_policy IS '恢复阈值、迟滞和持续时间等版本化策略';
COMMENT ON COLUMN public.alarm_rule_version.schedule_policy IS '规则时段和站点时区策略';
COMMENT ON COLUMN public.alarm_rule_version.canonicalization_version IS '规则内容规范化算法版本';
COMMENT ON COLUMN public.alarm_rule_version.content_hash IS '规范化内容 SHA-256，用于幂等和不可变校验';
COMMENT ON COLUMN public.alarm_rule_version.published_by IS '发布人；已发布/退役版本必填';
COMMENT ON COLUMN public.alarm_rule_version.published_at IS '发布时间；已发布/退役版本必填';
COMMENT ON COLUMN public.alarm_rule_version.created_by IS '版本创建人';
COMMENT ON COLUMN public.alarm_rule_version.created_at IS '版本创建时间，UTC 时间事实';
COMMENT ON INDEX public.uq_alarm_rule_version_published IS '同一租户同一规则同时只能有一个已发布版本；回滚先退役当前版本再事务内激活历史版本';

CREATE FUNCTION public.fn_alarm_rule_version_guard()
RETURNS trigger
LANGUAGE plpgsql
AS $$
BEGIN
    IF TG_OP = 'DELETE' THEN
        IF OLD.lifecycle IN ('PUBLISHED', 'RETIRED') THEN
            RAISE EXCEPTION 'alarm_rule_version published history cannot be deleted';
        END IF;
        RETURN OLD;
    END IF;

    IF NEW.lifecycle IS DISTINCT FROM OLD.lifecycle THEN
        IF OLD.lifecycle = 'DRAFT' AND NEW.lifecycle NOT IN ('DRAFT', 'VALIDATED') THEN
            RAISE EXCEPTION 'alarm_rule_version lifecycle transition forbidden: DRAFT -> %', NEW.lifecycle;
        ELSIF OLD.lifecycle = 'VALIDATED' AND NEW.lifecycle NOT IN ('VALIDATED', 'PUBLISHED') THEN
            RAISE EXCEPTION 'alarm_rule_version lifecycle transition forbidden: VALIDATED -> %', NEW.lifecycle;
        ELSIF OLD.lifecycle = 'PUBLISHED' AND NEW.lifecycle NOT IN ('PUBLISHED', 'RETIRED') THEN
            RAISE EXCEPTION 'alarm_rule_version lifecycle transition forbidden: PUBLISHED -> %', NEW.lifecycle;
        ELSIF OLD.lifecycle = 'RETIRED' AND NEW.lifecycle NOT IN ('RETIRED', 'PUBLISHED') THEN
            RAISE EXCEPTION 'alarm_rule_version lifecycle transition forbidden: RETIRED -> %', NEW.lifecycle;
        END IF;
    END IF;

    IF NEW.id IS DISTINCT FROM OLD.id
        OR NEW.tenant_id IS DISTINCT FROM OLD.tenant_id
        OR NEW.rule_id IS DISTINCT FROM OLD.rule_id
        OR NEW.version IS DISTINCT FROM OLD.version
        OR NEW.created_by IS DISTINCT FROM OLD.created_by
        OR NEW.created_at IS DISTINCT FROM OLD.created_at THEN
        RAISE EXCEPTION 'alarm_rule_version identity is immutable';
    END IF;

    IF OLD.lifecycle IN ('PUBLISHED', 'RETIRED') AND (
        NEW.severity IS DISTINCT FROM OLD.severity
        OR NEW.condition_tree IS DISTINCT FROM OLD.condition_tree
        OR NEW.recovery_policy IS DISTINCT FROM OLD.recovery_policy
        OR NEW.schedule_policy IS DISTINCT FROM OLD.schedule_policy
        OR NEW.canonicalization_version IS DISTINCT FROM OLD.canonicalization_version
        OR NEW.content_hash IS DISTINCT FROM OLD.content_hash
        OR NEW.published_by IS DISTINCT FROM OLD.published_by
        OR NEW.published_at IS DISTINCT FROM OLD.published_at
    ) THEN
        RAISE EXCEPTION 'alarm_rule_version content or identity is immutable after publication';
    END IF;

    RETURN NEW;
END;
$$;

CREATE TRIGGER trg_alarm_rule_version_guard
BEFORE UPDATE OR DELETE ON public.alarm_rule_version
FOR EACH ROW
EXECUTE FUNCTION public.fn_alarm_rule_version_guard();

CREATE TABLE public.alarm_maintenance_context (
    id BIGINT NOT NULL,
    tenant_id BIGINT NOT NULL,
    site_id BIGINT NOT NULL,
    scope_json JSONB NOT NULL,
    starts_at TIMESTAMPTZ NOT NULL,
    ends_at TIMESTAMPTZ NOT NULL,
    reason VARCHAR(1000) NOT NULL,
    approved_by VARCHAR(64) NOT NULL,
    policy_version VARCHAR(32) NOT NULL,
    status VARCHAR(16) NOT NULL CHECK (status IN ('SCHEDULED','ACTIVE','ENDED','CANCELLED')),
    row_version BIGINT NOT NULL DEFAULT 0,
    created_by VARCHAR(64) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT alarm_maintenance_context_pkey PRIMARY KEY (id),
    CONSTRAINT uq_alarm_maintenance_context_tenant_id UNIQUE (tenant_id, id),
    CONSTRAINT ck_alarm_maintenance_context_tenant_site CHECK (tenant_id > 0 AND site_id > 0),
    CONSTRAINT ck_alarm_maintenance_context_window CHECK (starts_at < ends_at),
    CONSTRAINT ck_alarm_maintenance_context_scope_size CHECK (octet_length(scope_json::text) <= 262144)
);

CREATE INDEX idx_alarm_maintenance_context_active
    ON public.alarm_maintenance_context (tenant_id, site_id, starts_at, ends_at)
    WHERE status IN ('SCHEDULED','ACTIVE');

COMMENT ON TABLE public.alarm_maintenance_context IS 'full 档维护模式上下文；维护期间仍保留规则和告警事实';
COMMENT ON COLUMN public.alarm_maintenance_context.id IS '维护上下文主键，由应用统一 ID 策略赋值';
COMMENT ON COLUMN public.alarm_maintenance_context.tenant_id IS '租户编号';
COMMENT ON COLUMN public.alarm_maintenance_context.site_id IS '维护范围所属站点';
COMMENT ON COLUMN public.alarm_maintenance_context.scope_json IS '受控对象/规则范围，不允许任意脚本';
COMMENT ON COLUMN public.alarm_maintenance_context.starts_at IS '维护开始时间，UTC';
COMMENT ON COLUMN public.alarm_maintenance_context.ends_at IS '维护结束时间，UTC';
COMMENT ON COLUMN public.alarm_maintenance_context.reason IS '维护原因，必填并审计';
COMMENT ON COLUMN public.alarm_maintenance_context.approved_by IS '维护模式批准人稳定标识';
COMMENT ON COLUMN public.alarm_maintenance_context.policy_version IS '抑制/重评估策略版本';
COMMENT ON COLUMN public.alarm_maintenance_context.status IS '维护上下文状态';
COMMENT ON COLUMN public.alarm_maintenance_context.row_version IS '乐观锁版本';
COMMENT ON COLUMN public.alarm_maintenance_context.created_by IS '创建人';
COMMENT ON COLUMN public.alarm_maintenance_context.created_at IS '创建时间，UTC';
COMMENT ON COLUMN public.alarm_maintenance_context.updated_at IS '最后更新时间，UTC';

CREATE TABLE public.alarm_record (
    id BIGINT NOT NULL,
    tenant_id BIGINT NOT NULL,
    site_id BIGINT NOT NULL,
    source_type VARCHAR(32) NOT NULL CHECK (source_type IN ('THRESHOLD','DEVICE_EVENT','VIDEO','AI','RUNTIME')),
    source_id VARCHAR(256) NOT NULL,
    cycle_key VARCHAR(256) NOT NULL,
    cycle_identity TEXT NOT NULL,
    cycle_identity_hash VARCHAR(71) NOT NULL CHECK (cycle_identity_hash ~ '^sha256:[0-9a-f]{64}$'),
    source_object_id VARCHAR(256) NOT NULL,
    device_identification VARCHAR(255) NOT NULL,
    property_code VARCHAR(128),
    rule_id BIGINT,
    rule_version_id BIGINT,
    rule_version VARCHAR(32),
    severity VARCHAR(16) NOT NULL CHECK (severity IN ('INFO','NORMAL','IMPORTANT','EMERGENCY')),
    status VARCHAR(32) NOT NULL CHECK (status IN ('ACTIVE','ACKNOWLEDGED','PROCESSING','RECOVERED','CLOSED','IGNORED','FALSE_ALARM')),
    row_version BIGINT NOT NULL DEFAULT 0,
    occurrence_count BIGINT NOT NULL DEFAULT 1 CHECK (occurrence_count >= 1),
    escalation_level INTEGER NOT NULL DEFAULT 0 CHECK (escalation_level >= 0),
    last_escalated_at TIMESTAMPTZ,
    first_occurred_at TIMESTAMPTZ NOT NULL,
    last_occurred_at TIMESTAMPTZ NOT NULL,
    recovered_at TIMESTAMPTZ,
    closed_at TIMESTAMPTZ,
    maintenance_context_id BIGINT,
    ignored_from_status VARCHAR(32),
    ignored_until TIMESTAMPTZ,
    ignore_generation BIGINT NOT NULL DEFAULT 0 CHECK (ignore_generation >= 0),
    source_timezone VARCHAR(64),
    source_offset VARCHAR(16),
    created_by VARCHAR(64) NOT NULL,
    updated_by VARCHAR(64) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT alarm_record_pkey PRIMARY KEY (id),
    CONSTRAINT uq_alarm_record_tenant_id UNIQUE (tenant_id, id),
    CONSTRAINT uq_alarm_record_cycle_hash UNIQUE (tenant_id, cycle_identity_hash),
    CONSTRAINT fk_alarm_record_rule_version FOREIGN KEY (tenant_id, rule_id, rule_version_id)
        REFERENCES public.alarm_rule_version (tenant_id, rule_id, id),
    CONSTRAINT fk_alarm_record_maintenance FOREIGN KEY (tenant_id, maintenance_context_id)
        REFERENCES public.alarm_maintenance_context (tenant_id, id),
    CONSTRAINT ck_alarm_record_rule_reference CHECK (
        (rule_id IS NULL AND rule_version_id IS NULL AND rule_version IS NULL)
        OR (rule_id IS NOT NULL AND rule_version_id IS NOT NULL AND rule_version IS NOT NULL)
    ),
    CONSTRAINT ck_alarm_record_cycle_identity_size CHECK (octet_length(cycle_identity) <= 4096),
    CONSTRAINT ck_alarm_record_time_order CHECK (first_occurred_at <= last_occurred_at),
    CONSTRAINT ck_alarm_record_recovery CHECK (
        status NOT IN ('RECOVERED','CLOSED') OR recovered_at IS NOT NULL
    ),
    CONSTRAINT ck_alarm_record_close CHECK (status <> 'CLOSED' OR closed_at IS NOT NULL),
    CONSTRAINT ck_alarm_record_ignore CHECK (
        (status = 'IGNORED' AND ignored_from_status IN ('ACTIVE','ACKNOWLEDGED') AND ignored_until IS NOT NULL AND ignore_generation >= 1)
        OR (status <> 'IGNORED' AND ignored_from_status IS NULL AND ignored_until IS NULL)
    ),
    CONSTRAINT ck_alarm_record_emergency_ignore CHECK (
        severity <> 'EMERGENCY' OR status <> 'IGNORED'
    ),
    CONSTRAINT ck_alarm_record_tenant_site CHECK (tenant_id > 0 AND site_id > 0)
);

CREATE INDEX idx_alarm_record_page
    ON public.alarm_record (tenant_id, site_id, status, severity, first_occurred_at DESC, id DESC);
CREATE INDEX idx_alarm_record_device_active
    ON public.alarm_record (tenant_id, device_identification, property_code, last_occurred_at DESC)
    WHERE status IN ('ACTIVE','ACKNOWLEDGED','PROCESSING','IGNORED');
CREATE INDEX idx_alarm_record_ignore_expiry
    ON public.alarm_record (ignored_until, tenant_id, id)
    WHERE status = 'IGNORED';

COMMENT ON TABLE public.alarm_record IS '统一告警周期主记录；iot-device 是处置状态唯一事实源';
COMMENT ON COLUMN public.alarm_record.id IS '全局告警 ID，由应用统一 ID 策略赋值；JSON 中以字符串传输';
COMMENT ON COLUMN public.alarm_record.tenant_id IS '租户编号，禁止从未校验请求正文直接采用';
COMMENT ON COLUMN public.alarm_record.site_id IS '站点稳定标识，按数据权限过滤';
COMMENT ON COLUMN public.alarm_record.source_type IS '来源类型：阈值、设备事件、视频、AI 或 RUNTIME';
COMMENT ON COLUMN public.alarm_record.source_id IS '来源记录稳定标识，保留原始证据引用';
COMMENT ON COLUMN public.alarm_record.cycle_key IS '来源故障周期键；恢复后再次发生必须使用新值';
COMMENT ON COLUMN public.alarm_record.cycle_identity IS '规范化完整周期身份，用于哈希冲突复核';
COMMENT ON COLUMN public.alarm_record.cycle_identity_hash IS '规范化周期身份 SHA-256；租户内唯一';
COMMENT ON COLUMN public.alarm_record.source_object_id IS '来源对象稳定标识';
COMMENT ON COLUMN public.alarm_record.device_identification IS '设备业务标识，经设备权威事实核验';
COMMENT ON COLUMN public.alarm_record.property_code IS '可选测点/属性编码';
COMMENT ON COLUMN public.alarm_record.rule_id IS '可选规则稳定 ID';
COMMENT ON COLUMN public.alarm_record.rule_version_id IS '可选规则版本 ID，与 rule_id 同时存在';
COMMENT ON COLUMN public.alarm_record.rule_version IS '规则版本字符串，固化历史解释语义';
COMMENT ON COLUMN public.alarm_record.severity IS '四级告警等级：INFO/NORMAL/IMPORTANT/EMERGENCY';
COMMENT ON COLUMN public.alarm_record.status IS '告警主状态；升级、维护和通知不写入该枚举';
COMMENT ON COLUMN public.alarm_record.row_version IS '乐观锁版本，每次业务更新递增';
COMMENT ON COLUMN public.alarm_record.occurrence_count IS '同一活动周期累计发生次数';
COMMENT ON COLUMN public.alarm_record.escalation_level IS '正交升级级别，0 表示未升级';
COMMENT ON COLUMN public.alarm_record.last_escalated_at IS '最近一次告警升级时间，UTC；升级为正交事实，不改变主状态';
COMMENT ON COLUMN public.alarm_record.first_occurred_at IS '周期首次发生时间，UTC';
COMMENT ON COLUMN public.alarm_record.last_occurred_at IS '周期最近发生时间，UTC';
COMMENT ON COLUMN public.alarm_record.recovered_at IS '来源恢复时间；恢复不等于关闭';
COMMENT ON COLUMN public.alarm_record.closed_at IS '授权业务关闭时间';
COMMENT ON COLUMN public.alarm_record.maintenance_context_id IS '可选维护上下文，同租户关联';
COMMENT ON COLUMN public.alarm_record.ignored_from_status IS '忽略前主状态，仅允许 ACTIVE/ACKNOWLEDGED';
COMMENT ON COLUMN public.alarm_record.ignored_until IS '临时忽略截止时间，紧急告警不得设置';
COMMENT ON COLUMN public.alarm_record.ignore_generation IS '严格单次忽略代次；每次进入 IGNORED 单调递增，到期 CAS 必须匹配领取时快照；离开忽略状态可保留最后代次';
COMMENT ON COLUMN public.alarm_record.source_timezone IS '来源 IANA 时区；未知时为空且不得猜测';
COMMENT ON COLUMN public.alarm_record.source_offset IS '来源原始 UTC offset 文本';
COMMENT ON COLUMN public.alarm_record.created_by IS '创建主体，来源事件使用受认证服务身份';
COMMENT ON COLUMN public.alarm_record.updated_by IS '最后修改主体';
COMMENT ON COLUMN public.alarm_record.created_at IS '平台创建时间，UTC';
COMMENT ON COLUMN public.alarm_record.updated_at IS '平台最后更新时间，UTC';

CREATE TABLE public.alarm_source_mapping (
    id BIGINT NOT NULL,
    tenant_id BIGINT NOT NULL,
    alarm_id BIGINT NOT NULL,
    source_type VARCHAR(32) NOT NULL CHECK (source_type IN ('THRESHOLD','DEVICE_EVENT','VIDEO','AI','RUNTIME')),
    source_id VARCHAR(256) NOT NULL,
    cycle_key VARCHAR(256) NOT NULL,
    source_payload_hash VARCHAR(71) NOT NULL CHECK (source_payload_hash ~ '^sha256:[0-9a-f]{64}$'),
    mapping_method VARCHAR(32) NOT NULL CHECK (mapping_method IN ('NATIVE','BACKFILL','MANUAL_REVIEW')),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT alarm_source_mapping_pkey PRIMARY KEY (id),
    CONSTRAINT uq_alarm_source_mapping_source UNIQUE (tenant_id, source_type, source_id, cycle_key),
    CONSTRAINT uq_alarm_source_mapping_alarm_source UNIQUE (tenant_id, alarm_id, source_type, source_id, cycle_key),
    CONSTRAINT fk_alarm_source_mapping_alarm FOREIGN KEY (tenant_id, alarm_id)
        REFERENCES public.alarm_record (tenant_id, id),
    CONSTRAINT ck_alarm_source_mapping_tenant CHECK (tenant_id > 0)
);

CREATE INDEX idx_alarm_source_mapping_alarm
    ON public.alarm_source_mapping (tenant_id, alarm_id, id);

COMMENT ON TABLE public.alarm_source_mapping IS '来源告警/旧记录到统一 alarmId 的可重跑映射';
COMMENT ON COLUMN public.alarm_source_mapping.id IS '映射主键，由应用统一 ID 策略赋值';
COMMENT ON COLUMN public.alarm_source_mapping.tenant_id IS '租户编号';
COMMENT ON COLUMN public.alarm_source_mapping.alarm_id IS '统一告警 ID';
COMMENT ON COLUMN public.alarm_source_mapping.source_type IS '来源类型，与主记录来源枚举一致';
COMMENT ON COLUMN public.alarm_source_mapping.source_id IS '来源记录稳定标识';
COMMENT ON COLUMN public.alarm_source_mapping.cycle_key IS '来源故障周期键';
COMMENT ON COLUMN public.alarm_source_mapping.source_payload_hash IS '来源映射时正文 SHA-256，用于冲突审计';
COMMENT ON COLUMN public.alarm_source_mapping.mapping_method IS '映射方式：原生、回填或人工复核';
COMMENT ON COLUMN public.alarm_source_mapping.created_at IS '映射创建时间，UTC';

CREATE FUNCTION public.fn_alarm_source_mapping_append_only()
RETURNS trigger
LANGUAGE plpgsql
AS $$
BEGIN
    RAISE EXCEPTION 'alarm_source_mapping is append-only: % is forbidden', TG_OP;
END;
$$;

CREATE TRIGGER trg_alarm_source_mapping_append_only
BEFORE UPDATE OR DELETE ON public.alarm_source_mapping
FOR EACH ROW
EXECUTE FUNCTION public.fn_alarm_source_mapping_append_only();

CREATE TABLE public.alarm_action_log (
    id BIGINT NOT NULL,
    tenant_id BIGINT NOT NULL,
    alarm_id BIGINT NOT NULL,
    sequence_no BIGINT NOT NULL CHECK (sequence_no >= 1),
    action_type VARCHAR(64) NOT NULL,
    from_status VARCHAR(32),
    to_status VARCHAR(32),
    actor_type VARCHAR(16) NOT NULL CHECK (actor_type IN ('USER','SERVICE','SCHEDULER','MIGRATION')),
    actor_id VARCHAR(128) NOT NULL,
    reason_code VARCHAR(64),
    reason_text VARCHAR(1000),
    request_id VARCHAR(128),
    trace_id VARCHAR(128),
    details JSONB NOT NULL DEFAULT '{}',
    occurred_at TIMESTAMPTZ NOT NULL,
    recorded_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT alarm_action_log_pkey PRIMARY KEY (id),
    CONSTRAINT uq_alarm_action_log_sequence UNIQUE (tenant_id, alarm_id, sequence_no),
    CONSTRAINT uq_alarm_action_log_request UNIQUE (tenant_id, alarm_id, request_id),
    CONSTRAINT fk_alarm_action_log_alarm FOREIGN KEY (tenant_id, alarm_id)
        REFERENCES public.alarm_record (tenant_id, id),
    CONSTRAINT ck_alarm_action_log_details_size CHECK (octet_length(details::text) <= 262144),
    CONSTRAINT ck_alarm_action_log_tenant CHECK (tenant_id > 0)
);

CREATE INDEX idx_alarm_action_log_timeline
    ON public.alarm_action_log (tenant_id, alarm_id, occurred_at, sequence_no);

COMMENT ON TABLE public.alarm_action_log IS '告警动作追加历史；普通业务不得更新或删除';
COMMENT ON COLUMN public.alarm_action_log.id IS '动作记录主键，由应用统一 ID 策略赋值';
COMMENT ON COLUMN public.alarm_action_log.tenant_id IS '租户编号';
COMMENT ON COLUMN public.alarm_action_log.alarm_id IS '统一告警 ID';
COMMENT ON COLUMN public.alarm_action_log.sequence_no IS '告警内单调递增动作序号';
COMMENT ON COLUMN public.alarm_action_log.action_type IS '来源、状态、升级、维护、抑制等稳定动作类型';
COMMENT ON COLUMN public.alarm_action_log.from_status IS '动作前主状态；无状态变化时可为空';
COMMENT ON COLUMN public.alarm_action_log.to_status IS '动作后主状态；无状态变化时可为空';
COMMENT ON COLUMN public.alarm_action_log.actor_type IS '动作主体类型';
COMMENT ON COLUMN public.alarm_action_log.actor_id IS '动作主体稳定标识';
COMMENT ON COLUMN public.alarm_action_log.reason_code IS '稳定原因码';
COMMENT ON COLUMN public.alarm_action_log.reason_text IS '受长度限制的原因文本，不保存凭据或敏感原文';
COMMENT ON COLUMN public.alarm_action_log.request_id IS '写动作幂等请求 ID；来源重复由 Inbox 处理';
COMMENT ON COLUMN public.alarm_action_log.trace_id IS '链路追踪 ID';
COMMENT ON COLUMN public.alarm_action_log.details IS '有界结构化动作明细；不能作为第二主状态事实';
COMMENT ON COLUMN public.alarm_action_log.occurred_at IS '动作业务发生时间，UTC';
COMMENT ON COLUMN public.alarm_action_log.recorded_at IS '平台记录时间，UTC';

CREATE FUNCTION public.fn_alarm_action_log_append_only()
RETURNS trigger
LANGUAGE plpgsql
AS $$
BEGIN
    RAISE EXCEPTION 'alarm_action_log is append-only: % is forbidden', TG_OP;
END;
$$;

CREATE TRIGGER trg_alarm_action_log_append_only
BEFORE UPDATE OR DELETE ON public.alarm_action_log
FOR EACH ROW
EXECUTE FUNCTION public.fn_alarm_action_log_append_only();

CREATE TABLE public.alarm_false_alarm_review (
    id BIGINT NOT NULL,
    tenant_id BIGINT NOT NULL,
    alarm_id BIGINT NOT NULL,
    status VARCHAR(16) NOT NULL CHECK (status IN ('PENDING','APPROVED','REJECTED')),
    proposed_by VARCHAR(64) NOT NULL,
    proposed_reason VARCHAR(1000) NOT NULL,
    proposed_at TIMESTAMPTZ NOT NULL,
    reviewed_by VARCHAR(64),
    reviewed_reason VARCHAR(1000),
    reviewed_at TIMESTAMPTZ,
    row_version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT alarm_false_alarm_review_pkey PRIMARY KEY (id),
    CONSTRAINT uq_alarm_false_alarm_review_tenant_id UNIQUE (tenant_id, id),
    CONSTRAINT fk_alarm_false_alarm_review_alarm FOREIGN KEY (tenant_id, alarm_id)
        REFERENCES public.alarm_record (tenant_id, id),
    CONSTRAINT ck_alarm_false_alarm_review_people CHECK (reviewed_by IS NULL OR reviewed_by <> proposed_by),
    CONSTRAINT ck_alarm_false_alarm_review_result CHECK (
        (status = 'PENDING' AND reviewed_by IS NULL AND reviewed_reason IS NULL AND reviewed_at IS NULL)
        OR (status IN ('APPROVED','REJECTED') AND reviewed_by IS NOT NULL AND reviewed_reason IS NOT NULL AND reviewed_at IS NOT NULL)
    ),
    CONSTRAINT ck_alarm_false_alarm_review_tenant CHECK (tenant_id > 0)
);

CREATE UNIQUE INDEX uq_alarm_false_alarm_review_pending
    ON public.alarm_false_alarm_review (tenant_id, alarm_id)
    WHERE status = 'PENDING';

COMMENT ON TABLE public.alarm_false_alarm_review IS '误报提议与独立复核记录；批准后告警进入不可逆 FALSE_ALARM';
COMMENT ON COLUMN public.alarm_false_alarm_review.id IS '复核记录主键，由应用统一 ID 策略赋值';
COMMENT ON COLUMN public.alarm_false_alarm_review.tenant_id IS '租户编号';
COMMENT ON COLUMN public.alarm_false_alarm_review.alarm_id IS '统一告警 ID';
COMMENT ON COLUMN public.alarm_false_alarm_review.status IS '复核状态：待复核、批准或拒绝';
COMMENT ON COLUMN public.alarm_false_alarm_review.proposed_by IS '误报提议人，不能复核同一提议';
COMMENT ON COLUMN public.alarm_false_alarm_review.proposed_reason IS '误报提议原因';
COMMENT ON COLUMN public.alarm_false_alarm_review.proposed_at IS '提议时间，UTC';
COMMENT ON COLUMN public.alarm_false_alarm_review.reviewed_by IS '独立复核人';
COMMENT ON COLUMN public.alarm_false_alarm_review.reviewed_reason IS '复核结论原因';
COMMENT ON COLUMN public.alarm_false_alarm_review.reviewed_at IS '复核时间，UTC';
COMMENT ON COLUMN public.alarm_false_alarm_review.row_version IS '乐观锁版本';

CREATE TABLE public.alarm_source_inbox (
    id BIGINT NOT NULL,
    message_id UUID NOT NULL,
    tenant_id BIGINT NOT NULL,
    event_type VARCHAR(128) NOT NULL,
    source_action VARCHAR(16) CHECK (source_action IS NULL OR source_action IN ('RAISED','RECOVERED')),
    event_version VARCHAR(16) NOT NULL,
    source VARCHAR(128) NOT NULL,
    envelope_hash VARCHAR(71) NOT NULL CHECK (envelope_hash ~ '^sha256:[0-9a-f]{64}$'),
    payload_json JSONB NOT NULL,
    status VARCHAR(16) NOT NULL DEFAULT 'RECEIVED' CHECK (status IN ('RECEIVED','PROCESSED','QUARANTINED')),
    last_error_code VARCHAR(64),
    last_error_summary VARCHAR(1000),
    received_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    processed_at TIMESTAMPTZ,
    quarantined_at TIMESTAMPTZ,
    CONSTRAINT alarm_source_inbox_pkey PRIMARY KEY (id),
    CONSTRAINT uq_alarm_source_inbox_message UNIQUE (message_id),
    CONSTRAINT ck_alarm_source_inbox_payload_size CHECK (octet_length(payload_json::text) <= 1048576),
    CONSTRAINT ck_alarm_source_inbox_state_time CHECK (
        (processed_at IS NULL OR received_at <= processed_at)
        AND (quarantined_at IS NULL OR received_at <= quarantined_at)
        AND (
            (status = 'RECEIVED' AND processed_at IS NULL AND quarantined_at IS NULL AND last_error_code IS NULL)
            OR (status = 'PROCESSED' AND processed_at IS NOT NULL AND quarantined_at IS NULL AND last_error_code IS NULL)
            OR (status = 'QUARANTINED' AND quarantined_at IS NOT NULL AND last_error_code IS NOT NULL)
        )
    ),
    CONSTRAINT ck_alarm_source_inbox_current_contract CHECK (
        status = 'QUARANTINED'
        OR (
            event_type = 'device.alarm.source-event.v1'
            AND event_version = '1.0'
            AND source = 'iot-device'
            AND source_action IS NOT NULL
        )
    ),
    CONSTRAINT ck_alarm_source_inbox_tenant CHECK (tenant_id > 0)
);

CREATE INDEX idx_alarm_source_inbox_claim
    ON public.alarm_source_inbox (received_at, id)
    WHERE status = 'RECEIVED';

COMMENT ON TABLE public.alarm_source_inbox IS '告警来源专用 Inbox；与 power-model Inbox 分离，确保幂等和冲突隔离';
COMMENT ON COLUMN public.alarm_source_inbox.id IS 'Inbox 主键，由应用统一 ID 策略赋值';
COMMENT ON COLUMN public.alarm_source_inbox.message_id IS '来源事件全局唯一 ID；同 ID 不同 envelope_hash 必须隔离';
COMMENT ON COLUMN public.alarm_source_inbox.tenant_id IS '经权威事实核验的租户编号';
COMMENT ON COLUMN public.alarm_source_inbox.event_type IS '收到的完整版本化来源事件名；当前有效值为 device.alarm.source-event.v1，未知主版本只允许隔离';
COMMENT ON COLUMN public.alarm_source_inbox.source_action IS '来源动作：RAISED 或 RECOVERED；无法解析的隔离消息可为空';
COMMENT ON COLUMN public.alarm_source_inbox.event_version IS '来源事件版本；未知主版本隔离';
COMMENT ON COLUMN public.alarm_source_inbox.source IS 'Envelope 生产者身份；当前有效合同固定为 iot-device，非法来源只允许隔离';
COMMENT ON COLUMN public.alarm_source_inbox.envelope_hash IS '规范消息字节 SHA-256，用于 messageId 重复/冲突判定；不同于 payload.payloadHash 来源证据摘要';
COMMENT ON COLUMN public.alarm_source_inbox.payload_json IS '有界来源正文；不得包含凭据或媒体大对象';
COMMENT ON COLUMN public.alarm_source_inbox.status IS 'Inbox 处理状态：RECEIVED、PROCESSED 或 QUARANTINED；同 ID 异 hash 进入 QUARANTINED 且保留首个 envelope_hash';
COMMENT ON COLUMN public.alarm_source_inbox.last_error_code IS '隔离稳定错误码；同 ID 异 hash 使用 ALARM_SOURCE_HASH_CONFLICT';
COMMENT ON COLUMN public.alarm_source_inbox.last_error_summary IS '脱敏错误摘要，不保存完整 payload/堆栈';
COMMENT ON COLUMN public.alarm_source_inbox.received_at IS '首次接收时间，UTC';
COMMENT ON COLUMN public.alarm_source_inbox.processed_at IS '成功处理时间，UTC；既有成功消息后续发现同 ID 异 hash 时保留该首次处理事实';
COMMENT ON COLUMN public.alarm_source_inbox.quarantined_at IS '进入隔离时间，UTC；仅 QUARANTINED 状态填写';

CREATE TABLE public.alarm_outbox (
    id BIGINT NOT NULL,
    event_id UUID NOT NULL,
    tenant_id BIGINT NOT NULL,
    alarm_id BIGINT NOT NULL,
    event_type VARCHAR(128) NOT NULL,
    event_version VARCHAR(16) NOT NULL,
    partition_key VARCHAR(256) NOT NULL,
    payload_hash VARCHAR(71) NOT NULL CHECK (payload_hash ~ '^sha256:[0-9a-f]{64}$'),
    payload_json JSONB NOT NULL,
    headers_json JSONB NOT NULL DEFAULT '{}',
    status VARCHAR(16) NOT NULL DEFAULT 'PENDING' CHECK (status IN ('PENDING','PUBLISHING','PUBLISHED','DEAD_LETTER')),
    retry_count INTEGER NOT NULL DEFAULT 0 CHECK (retry_count >= 0),
    max_retries INTEGER NOT NULL DEFAULT 5 CHECK (max_retries >= 0),
    next_attempt_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    lease_owner VARCHAR(128),
    lease_until TIMESTAMPTZ,
    last_error_code VARCHAR(64),
    last_error_summary VARCHAR(1000),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    published_at TIMESTAMPTZ,
    dead_lettered_at TIMESTAMPTZ,
    CONSTRAINT alarm_outbox_pkey PRIMARY KEY (id),
    CONSTRAINT uq_alarm_outbox_event UNIQUE (event_id),
    CONSTRAINT fk_alarm_outbox_alarm FOREIGN KEY (tenant_id, alarm_id)
        REFERENCES public.alarm_record (tenant_id, id),
    CONSTRAINT ck_alarm_outbox_payload_size CHECK (
        octet_length(payload_json::text) <= 1048576
        AND octet_length(headers_json::text) <= 65536
    ),
    CONSTRAINT ck_alarm_outbox_retry_budget CHECK (retry_count <= max_retries),
    CONSTRAINT ck_alarm_outbox_state_time CHECK (
        created_at <= updated_at
        AND (published_at IS NULL OR published_at >= created_at)
        AND (dead_lettered_at IS NULL OR dead_lettered_at >= created_at)
        AND (
            (status = 'PENDING' AND published_at IS NULL AND dead_lettered_at IS NULL AND lease_owner IS NULL AND lease_until IS NULL)
            OR (status = 'PUBLISHING' AND published_at IS NULL AND dead_lettered_at IS NULL AND lease_owner IS NOT NULL AND lease_until IS NOT NULL)
            OR (status = 'PUBLISHED' AND published_at IS NOT NULL AND dead_lettered_at IS NULL AND lease_owner IS NULL AND lease_until IS NULL)
            OR (status = 'DEAD_LETTER' AND published_at IS NULL AND dead_lettered_at IS NOT NULL AND lease_owner IS NULL AND lease_until IS NULL)
        )
    ),
    CONSTRAINT ck_alarm_outbox_tenant CHECK (tenant_id > 0)
);

CREATE INDEX idx_alarm_outbox_claim
    ON public.alarm_outbox (next_attempt_at, created_at, id)
    WHERE status = 'PENDING';
CREATE INDEX idx_alarm_outbox_publishing_expired
    ON public.alarm_outbox (lease_until, created_at, id)
    WHERE status = 'PUBLISHING';
CREATE INDEX idx_alarm_outbox_alarm
    ON public.alarm_outbox (tenant_id, alarm_id, created_at, id);

COMMENT ON TABLE public.alarm_outbox IS '统一告警领域事件 Outbox；与告警状态同事务提交';
COMMENT ON COLUMN public.alarm_outbox.id IS 'Outbox 主键，由应用统一 ID 策略赋值';
COMMENT ON COLUMN public.alarm_outbox.event_id IS '领域事件 UUID，全局唯一';
COMMENT ON COLUMN public.alarm_outbox.tenant_id IS '租户编号';
COMMENT ON COLUMN public.alarm_outbox.alarm_id IS '统一告警 ID';
COMMENT ON COLUMN public.alarm_outbox.event_type IS '完整版本化事件名称';
COMMENT ON COLUMN public.alarm_outbox.event_version IS '事件 Schema 版本';
COMMENT ON COLUMN public.alarm_outbox.partition_key IS '有序发布分区键，候选为 alarmId 字符串';
COMMENT ON COLUMN public.alarm_outbox.payload_hash IS '事件正文 SHA-256，用于发布完整性校验';
COMMENT ON COLUMN public.alarm_outbox.payload_json IS '有界事件正文，不含通知接收人、凭据、媒体或大对象';
COMMENT ON COLUMN public.alarm_outbox.headers_json IS '有界 transport header 投影';
COMMENT ON COLUMN public.alarm_outbox.status IS 'Outbox 投递状态：PENDING、PUBLISHING、PUBLISHED 或 DEAD_LETTER';
COMMENT ON COLUMN public.alarm_outbox.retry_count IS '已消耗的发布重试次数';
COMMENT ON COLUMN public.alarm_outbox.max_retries IS '该事件允许的最大发布重试次数';
COMMENT ON COLUMN public.alarm_outbox.next_attempt_at IS '可重试错误下次领取时间';
COMMENT ON COLUMN public.alarm_outbox.lease_owner IS '当前发布租约持有者';
COMMENT ON COLUMN public.alarm_outbox.lease_until IS '发布租约到期时间';
COMMENT ON COLUMN public.alarm_outbox.last_error_code IS '最后稳定错误码';
COMMENT ON COLUMN public.alarm_outbox.last_error_summary IS '脱敏错误摘要';
COMMENT ON COLUMN public.alarm_outbox.created_at IS '事件入列时间，UTC';
COMMENT ON COLUMN public.alarm_outbox.updated_at IS '最近一次投递状态更新时间，UTC';
COMMENT ON COLUMN public.alarm_outbox.published_at IS 'broker 确认成功时间，UTC；调用 send 不等于已发布';
COMMENT ON COLUMN public.alarm_outbox.dead_lettered_at IS '进入死信状态时间，UTC';
