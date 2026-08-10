-- ============================================================================
-- ADR-015 V004：collector workload binding 设备侧投影
--
-- 双基线：平台功能计划 1.4.0 / EasyAIoT 项目开发宪法 1.5.0
-- 上游：ADR-015 1.1.0（Accepted）、TD-001 1.0.7、V003
-- 执行：仅允许 ADR-013 runner；目标实例执行须独立批准窗口
-- ============================================================================

CREATE TABLE public.collector_workload_binding_projection (
    id BIGINT NOT NULL,
    tenant_id BIGINT NOT NULL,
    workload_id VARCHAR(128) NOT NULL,
    site_id BIGINT NOT NULL,
    site_code VARCHAR(64) NOT NULL,
    node_id BIGINT NOT NULL,
    product_id BIGINT NOT NULL,
    template_code VARCHAR(64) NOT NULL,
    template_version VARCHAR(64) NOT NULL,
    binding_revision BIGINT NOT NULL,
    config_version BIGINT NOT NULL,
    release_id BIGINT NOT NULL,
    projection_revision BIGINT NOT NULL,
    lifecycle_status VARCHAR(16) NOT NULL,
    last_synced_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT collector_workload_binding_projection_pkey PRIMARY KEY (id),
    CONSTRAINT uq_collector_workload_binding_projection_tenant_workload
        UNIQUE (tenant_id, workload_id),
    CONSTRAINT uq_collector_workload_binding_projection_tenant_id
        UNIQUE (tenant_id, id),
    CONSTRAINT fk_collector_workload_binding_projection_release
        FOREIGN KEY (tenant_id, release_id)
        REFERENCES public.iot_collector_config_release (tenant_id, id)
        ON DELETE RESTRICT,
    CONSTRAINT ck_collector_workload_binding_projection_lifecycle
        CHECK (lifecycle_status IN ('ACTIVE','STOPPED','RETIRED')),
    CONSTRAINT ck_collector_workload_binding_projection_positive CHECK (
        tenant_id > 0 AND site_id > 0 AND node_id > 0 AND product_id > 0
        AND binding_revision > 0 AND config_version > 0
        AND release_id > 0 AND projection_revision > 0
    ),
    CONSTRAINT ck_collector_workload_binding_projection_text CHECK (
        btrim(workload_id) <> '' AND btrim(site_code) <> ''
        AND btrim(template_code) <> '' AND btrim(template_version) <> ''
    )
);

CREATE INDEX idx_collector_workload_binding_projection_product
    ON public.collector_workload_binding_projection
    (tenant_id, product_id, lifecycle_status);

CREATE OR REPLACE FUNCTION public.fn_collector_workload_binding_projection_guard()
RETURNS trigger
LANGUAGE plpgsql
AS $function$
BEGIN
    IF NEW.tenant_id IS DISTINCT FROM OLD.tenant_id
       OR NEW.workload_id IS DISTINCT FROM OLD.workload_id
       OR NEW.id IS DISTINCT FROM OLD.id
       OR NEW.created_at IS DISTINCT FROM OLD.created_at THEN
        RAISE EXCEPTION 'COLLECTOR_PROJECTION_IDENTITY_IMMUTABLE';
    END IF;

    IF NEW.projection_revision < OLD.projection_revision THEN
        RAISE EXCEPTION 'COLLECTOR_PROJECTION_REVISION_STALE';
    END IF;

    IF NEW.projection_revision = OLD.projection_revision AND (
       NEW.site_id IS DISTINCT FROM OLD.site_id
       OR NEW.site_code IS DISTINCT FROM OLD.site_code
       OR NEW.node_id IS DISTINCT FROM OLD.node_id
       OR NEW.product_id IS DISTINCT FROM OLD.product_id
       OR NEW.template_code IS DISTINCT FROM OLD.template_code
       OR NEW.template_version IS DISTINCT FROM OLD.template_version
       OR NEW.binding_revision IS DISTINCT FROM OLD.binding_revision
       OR NEW.config_version IS DISTINCT FROM OLD.config_version
       OR NEW.release_id IS DISTINCT FROM OLD.release_id
       OR NEW.lifecycle_status IS DISTINCT FROM OLD.lifecycle_status) THEN
        RAISE EXCEPTION 'COLLECTOR_PROJECTION_SAME_REVISION_DRIFT';
    END IF;

    RETURN NEW;
END;
$function$;

CREATE TRIGGER trg_collector_workload_binding_projection_guard
BEFORE UPDATE ON public.collector_workload_binding_projection
FOR EACH ROW EXECUTE FUNCTION public.fn_collector_workload_binding_projection_guard();

COMMENT ON TABLE public.collector_workload_binding_projection IS 'collector workload 绑定设备侧投影（ADR-015：可变表，由 iot-device 发布状态机按单调版本更新）';
COMMENT ON COLUMN public.collector_workload_binding_projection.id IS '主键（应用统一 ID 策略赋值，数据库不兜底生成）';
COMMENT ON COLUMN public.collector_workload_binding_projection.tenant_id IS '租户编号（与发布单组成同租户外键）';
COMMENT ON COLUMN public.collector_workload_binding_projection.workload_id IS 'collector workload 标识（租户内唯一）';
COMMENT ON COLUMN public.collector_workload_binding_projection.site_id IS '站点内部主键';
COMMENT ON COLUMN public.collector_workload_binding_projection.site_code IS '站点不可变业务编码';
COMMENT ON COLUMN public.collector_workload_binding_projection.node_id IS '目标节点内部主键';
COMMENT ON COLUMN public.collector_workload_binding_projection.product_id IS '产品内部主键（ImpactPort 查询键）';
COMMENT ON COLUMN public.collector_workload_binding_projection.template_code IS '当前期望模板编码';
COMMENT ON COLUMN public.collector_workload_binding_projection.template_version IS '当前期望模板版本';
COMMENT ON COLUMN public.collector_workload_binding_projection.binding_revision IS '当前产品模型绑定修订号';
COMMENT ON COLUMN public.collector_workload_binding_projection.config_version IS '当前期望配置版本（同 workload 单调递增）';
COMMENT ON COLUMN public.collector_workload_binding_projection.release_id IS '最新 PUBLISHED/APPLIED 发布单 ID（同租户外键且禁止删除）';
COMMENT ON COLUMN public.collector_workload_binding_projection.projection_revision IS '投影语义版本（严格单调；旧版本和同版本语义漂移由触发器拒绝）';
COMMENT ON COLUMN public.collector_workload_binding_projection.lifecycle_status IS 'workload 生命周期状态（ACTIVE/STOPPED/RETIRED）';
COMMENT ON COLUMN public.collector_workload_binding_projection.last_synced_at IS '最近一次受控同步时间';
COMMENT ON COLUMN public.collector_workload_binding_projection.created_at IS '创建时间（身份字段，不可修改）';
COMMENT ON COLUMN public.collector_workload_binding_projection.updated_at IS '最近更新时间';

COMMENT ON FUNCTION public.fn_collector_workload_binding_projection_guard() IS '投影单调与身份保护：拒绝版本回退、同版本语义漂移和身份改写';
