-- ============================================================================
-- TD-001 V007 候选：collector 发布单派生身份
--
-- 双基线：平台功能计划 1.4.0 / EasyAIoT 项目开发宪法 1.5.0
-- 状态：REVIEW CANDIDATE；未接入 runner，禁止直接执行
-- 前置：V003、V004；执行须经 ADR-013 独立窗口批准
--
-- 背景：绑定事务会在 Outbox 事件提交前生成 VALIDATED collector 发布单。
-- 消费者必须用同一 source_event_id 精确找到该候选单，禁止复制上一版点表、
-- 猜测四项采集策略或按“最新一条”发布。
-- ============================================================================

DO $guard$
BEGIN
    IF EXISTS (SELECT 1 FROM public.iot_collector_config_release LIMIT 1) THEN
        RAISE EXCEPTION
            'V007_PRECONDITION_RELEASE_NOT_EMPTY: backfill derivation identity before migration';
    END IF;
END
$guard$;

ALTER TABLE public.iot_collector_config_release
    ADD COLUMN product_id BIGINT NOT NULL,
    ADD COLUMN template_code VARCHAR(64) NOT NULL,
    ADD COLUMN template_version VARCHAR(64) NOT NULL,
    ADD COLUMN binding_revision BIGINT NOT NULL,
    ADD COLUMN source_event_id UUID NOT NULL,
    ADD COLUMN source_reason_code VARCHAR(128) NOT NULL;

ALTER TABLE public.iot_collector_config_release
    ADD CONSTRAINT fk_iot_collector_config_release_binding
        FOREIGN KEY (tenant_id, product_id, binding_revision)
        REFERENCES public.power_product_model_binding
        (tenant_id, product_id, binding_revision)
        ON DELETE RESTRICT,
    ADD CONSTRAINT uq_iot_collector_config_release_source_event
        UNIQUE (tenant_id, workload_id, source_event_id),
    ADD CONSTRAINT ck_iot_collector_config_release_derivation_positive
        CHECK (product_id > 0 AND binding_revision > 0),
    ADD CONSTRAINT ck_iot_collector_config_release_derivation_text
        CHECK (btrim(template_code) <> '' AND btrim(template_version) <> ''
            AND source_reason_code IN ('BINDING_APPLIED','BINDING_ROLLED_BACK'));

ALTER TABLE public.power_model_release_outbox
    ADD CONSTRAINT uq_power_model_release_outbox_tenant_event
        UNIQUE (tenant_id, event_id);

ALTER TABLE public.iot_collector_config_release
    ADD CONSTRAINT fk_iot_collector_config_release_source_event
        FOREIGN KEY (tenant_id, source_event_id)
        REFERENCES public.power_model_release_outbox (tenant_id, event_id)
        ON DELETE RESTRICT;

CREATE INDEX idx_iot_collector_config_release_derivation
    ON public.iot_collector_config_release
    (tenant_id, workload_id, product_id, binding_revision, status, config_version DESC);

COMMENT ON COLUMN public.iot_collector_config_release.product_id IS
    '派生发布单对应产品 ID；与租户、workload、绑定修订共同参与候选定位';
COMMENT ON COLUMN public.iot_collector_config_release.template_code IS
    '派生发布单对应模板编码审计副本；回滚时由目标 bindingRevision 解析后固化';
COMMENT ON COLUMN public.iot_collector_config_release.template_version IS
    '派生发布单对应模板版本审计副本；回滚时由目标 bindingRevision 解析后固化';
COMMENT ON COLUMN public.iot_collector_config_release.binding_revision IS
    '生成该点表快照的产品绑定修订号';
COMMENT ON COLUMN public.iot_collector_config_release.source_event_id IS
    '触发候选单的物模型 Outbox 事件 UUID；同租户外键且同租户+workload 唯一，供消费幂等精确定位';
COMMENT ON COLUMN public.iot_collector_config_release.source_reason_code IS
    '生成原因稳定码（仅 BINDING_APPLIED/BINDING_ROLLED_BACK），禁止保存自由文本或敏感信息';

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
        OR NEW.node_id IS DISTINCT FROM OLD.node_id
        OR NEW.config_version IS DISTINCT FROM OLD.config_version
        OR NEW.schema_version IS DISTINCT FROM OLD.schema_version
        OR NEW.canonicalization_version IS DISTINCT FROM OLD.canonicalization_version
        OR NEW.payload_canonical IS DISTINCT FROM OLD.payload_canonical
        OR NEW.payload IS DISTINCT FROM OLD.payload
        OR NEW.payload_sha256 IS DISTINCT FROM OLD.payload_sha256
        OR NEW.canonical_length_bytes IS DISTINCT FROM OLD.canonical_length_bytes
        OR NEW.base_version IS DISTINCT FROM OLD.base_version
        OR NEW.rollback_from_version IS DISTINCT FROM OLD.rollback_from_version
        OR NEW.product_id IS DISTINCT FROM OLD.product_id
        OR NEW.template_code IS DISTINCT FROM OLD.template_code
        OR NEW.template_version IS DISTINCT FROM OLD.template_version
        OR NEW.binding_revision IS DISTINCT FROM OLD.binding_revision
        OR NEW.source_event_id IS DISTINCT FROM OLD.source_event_id
        OR NEW.source_reason_code IS DISTINCT FROM OLD.source_reason_code
    ) THEN
        RAISE EXCEPTION 'iot_collector_config_release snapshot/derivation fields are immutable';
    END IF;
    RETURN NEW;
END;
$$;

COMMENT ON FUNCTION public.fn_iot_collector_config_release_immutable() IS
    '保护 collector 发布单快照及派生身份不可原地修改；仅允许受控状态与应用结果演进';
