-- ============================================================================
-- TD-005 V002 候选（STEP M2，仅供评审，禁止在生产/共享库执行）
--
-- 双基线：平台功能计划 1.4.0 / EasyAIoT 项目开发宪法 1.5.0
-- 上游：TD-005 migration 0.1.8、ADR-013 1.4.0（Proposed）
-- 执行器：受控迁移步骤执行器（ADR-013）；批准前不得执行
--
-- 前置：M15（product 并发唯一索引）与 M16（约束附加）必须已 SUCCEEDED，
--       V001 五表必须已建立；本文件独立成阶段，失败不影响 V001 已建对象。
-- 执行方式：单事务执行；NOT VALID + VALIDATE 在空表上瞬时完成，
--       生产执行仍须按 §7.3 记录锁等待、耗时与 WAL。
-- ============================================================================

-- ----------------------------------------------------------------------------
-- power_product_model_binding（依赖 M15/M16 唯一键，先 NOT VALID 后 VALIDATE）
-- ----------------------------------------------------------------------------
CREATE TABLE public.power_product_model_binding (
    id BIGINT NOT NULL,
    tenant_id BIGINT NOT NULL,
    product_id BIGINT NOT NULL,
    product_identification VARCHAR(100) NOT NULL,
    binding_revision BIGINT NOT NULL,
    status VARCHAR(20) NOT NULL CHECK (status IN ('ACTIVE','SUPERSEDED','ROLLED_BACK')),
    template_version_id BIGINT NOT NULL,
    template_code VARCHAR(64) NOT NULL,
    template_version VARCHAR(64) NOT NULL,
    content_hash VARCHAR(71) NOT NULL,
    binding_snapshot_canonical TEXT NOT NULL,
    binding_snapshot_json JSONB NOT NULL,
    binding_snapshot_hash VARCHAR(71) NOT NULL,
    previous_binding_id BIGINT,
    upgrade_plan_id BIGINT,
    rollback_from_binding_id BIGINT,
    effective_from TIMESTAMPTZ NOT NULL,
    effective_to TIMESTAMPTZ,
    created_by VARCHAR(64) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT power_product_model_binding_pkey PRIMARY KEY (id),
    CONSTRAINT uq_power_product_model_binding_revision UNIQUE (tenant_id, product_id, binding_revision),
    CONSTRAINT uq_power_product_model_binding_tenant_id UNIQUE (tenant_id, id),
    CONSTRAINT fk_power_product_model_binding_template
        FOREIGN KEY (tenant_id, template_version_id)
        REFERENCES public.power_model_template_version (tenant_id, id),
    CONSTRAINT ck_power_product_model_binding_effective CHECK (
        (status = 'ACTIVE' AND effective_to IS NULL)
        OR (status <> 'ACTIVE' AND effective_to IS NOT NULL)
    ),
    CONSTRAINT ck_power_product_model_binding_hash CHECK (
        binding_snapshot_hash ~ '^sha256:[0-9a-f]{64}$'
        AND content_hash ~ '^sha256:[0-9a-f]{64}$'
    )
);

CREATE UNIQUE INDEX uq_power_product_model_binding_active
    ON public.power_product_model_binding (tenant_id, product_id)
    WHERE status = 'ACTIVE';

-- 同租户 product FK：依赖 M15/M16 唯一约束；先 NOT VALID，独立扫描后 VALIDATE
ALTER TABLE ONLY public.power_product_model_binding
    ADD CONSTRAINT fk_power_product_model_binding_product
    FOREIGN KEY (tenant_id, product_identification)
    REFERENCES public.product (tenant_id, product_identification)
    NOT VALID;
ALTER TABLE ONLY public.power_product_model_binding VALIDATE CONSTRAINT fk_power_product_model_binding_product;

-- previous/rollback 同租户自引用 FK（候选；最终约束名由 DBA 冻结）
ALTER TABLE ONLY public.power_product_model_binding
    ADD CONSTRAINT fk_power_product_model_binding_previous
    FOREIGN KEY (tenant_id, previous_binding_id)
    REFERENCES public.power_product_model_binding (tenant_id, id)
    NOT VALID;
ALTER TABLE ONLY public.power_product_model_binding VALIDATE CONSTRAINT fk_power_product_model_binding_previous;

ALTER TABLE ONLY public.power_product_model_binding
    ADD CONSTRAINT fk_power_product_model_binding_rollback
    FOREIGN KEY (tenant_id, rollback_from_binding_id)
    REFERENCES public.power_product_model_binding (tenant_id, id)
    NOT VALID;
ALTER TABLE ONLY public.power_product_model_binding VALIDATE CONSTRAINT fk_power_product_model_binding_rollback;

CREATE OR REPLACE FUNCTION public.fn_power_product_model_binding_immutable()
RETURNS trigger
LANGUAGE plpgsql
AS $$
DECLARE
    v_identification VARCHAR(100);
BEGIN
    IF TG_OP = 'INSERT' THEN
        SELECT product_identification INTO v_identification
        FROM public.product
        WHERE tenant_id = NEW.tenant_id
          AND id = NEW.product_id
          AND product_identification = NEW.product_identification;
        IF v_identification IS NULL THEN
            RAISE EXCEPTION 'product_id does not match product_identification';
        END IF;
    END IF;
    IF TG_OP = 'UPDATE' AND (
        NEW.product_id IS DISTINCT FROM OLD.product_id
        OR NEW.product_identification IS DISTINCT FROM OLD.product_identification
        OR NEW.binding_revision IS DISTINCT FROM OLD.binding_revision
        OR NEW.template_version_id IS DISTINCT FROM OLD.template_version_id
        OR NEW.template_code IS DISTINCT FROM OLD.template_code
        OR NEW.template_version IS DISTINCT FROM OLD.template_version
        OR NEW.content_hash IS DISTINCT FROM OLD.content_hash
        OR NEW.binding_snapshot_canonical IS DISTINCT FROM OLD.binding_snapshot_canonical
        OR NEW.binding_snapshot_json IS DISTINCT FROM OLD.binding_snapshot_json
        OR NEW.binding_snapshot_hash IS DISTINCT FROM OLD.binding_snapshot_hash
        OR NEW.previous_binding_id IS DISTINCT FROM OLD.previous_binding_id
        OR NEW.rollback_from_binding_id IS DISTINCT FROM OLD.rollback_from_binding_id
    ) THEN
        RAISE EXCEPTION 'binding history/snapshot fields are immutable';
    END IF;
    IF NEW.status = 'ACTIVE' AND NEW.effective_to IS NOT NULL THEN
        RAISE EXCEPTION 'ACTIVE binding must have effective_to IS NULL';
    END IF;
    IF NEW.status <> 'ACTIVE' AND NEW.effective_to IS NULL THEN
        RAISE EXCEPTION 'non-ACTIVE binding must have effective_to';
    END IF;
    RETURN NEW;
END;
$$;

DROP TRIGGER IF EXISTS trg_power_product_model_binding_immutable ON public.power_product_model_binding;
CREATE TRIGGER trg_power_product_model_binding_immutable
    BEFORE INSERT OR UPDATE ON public.power_product_model_binding
    FOR EACH ROW EXECUTE FUNCTION public.fn_power_product_model_binding_immutable();

COMMENT ON TABLE public.power_product_model_binding IS '产品物模型绑定修订（精确模板版本 + 运行快照，历史不可修改）';
COMMENT ON COLUMN public.power_product_model_binding.id IS '主键（应用统一 ID 策略赋值）';
COMMENT ON COLUMN public.power_product_model_binding.tenant_id IS '租户编号';
COMMENT ON COLUMN public.power_product_model_binding.product_id IS '产品 ID（锁定行审计副本）';
COMMENT ON COLUMN public.power_product_model_binding.product_identification IS '产品标识（锁定行审计副本）';
COMMENT ON COLUMN public.power_product_model_binding.binding_revision IS '绑定修订号（产品内单调递增）';
COMMENT ON COLUMN public.power_product_model_binding.status IS '绑定状态（ACTIVE/SUPERSEDED/ROLLED_BACK）';
COMMENT ON COLUMN public.power_product_model_binding.template_version_id IS '模板版本 ID';
COMMENT ON COLUMN public.power_product_model_binding.template_code IS '模板编码审计副本';
COMMENT ON COLUMN public.power_product_model_binding.template_version IS '模板版本审计副本';
COMMENT ON COLUMN public.power_product_model_binding.content_hash IS '模板内容哈希审计副本';
COMMENT ON COLUMN public.power_product_model_binding.binding_snapshot_canonical IS '绑定快照规范化内容（唯一可写事实）';
COMMENT ON COLUMN public.power_product_model_binding.binding_snapshot_json IS '绑定快照查询投影（同一事务生成）';
COMMENT ON COLUMN public.power_product_model_binding.binding_snapshot_hash IS '绑定快照 SHA-256';
COMMENT ON COLUMN public.power_product_model_binding.previous_binding_id IS '上一绑定修订 ID';
COMMENT ON COLUMN public.power_product_model_binding.upgrade_plan_id IS '升级计划 ID';
COMMENT ON COLUMN public.power_product_model_binding.rollback_from_binding_id IS '回滚来源绑定修订 ID';
COMMENT ON COLUMN public.power_product_model_binding.effective_from IS '生效开始时间';
COMMENT ON COLUMN public.power_product_model_binding.effective_to IS '生效结束时间（ACTIVE 必须为空）';
COMMENT ON COLUMN public.power_product_model_binding.created_by IS '创建人';
COMMENT ON COLUMN public.power_product_model_binding.created_at IS '创建时间';
