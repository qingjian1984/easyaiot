-- STEP M16：product 同租户业务唯一约束附加（事务型步骤）
--
-- 双基线：平台功能计划 1.4.0 / EasyAIoT 项目开发宪法 1.5.0
-- 上游：TD-005 migration 0.1.8 §7.2、ADR-013（Proposed）
--
-- 前置：M15 的 CREATE UNIQUE INDEX CONCURRENTLY 已成功且索引 VALID。
-- 动作：短锁附加稳定 UNIQUE 约束（复用既有索引，不扫描表），
--       供 power_product_model_binding 的同租户 FK 引用。
-- 说明：本步骤在单事务内执行；锁等待受 runner lock_timeout 约束，
--       超时零语义变化退出，修复后可幂等重跑。

ALTER TABLE ONLY public.product
    ADD CONSTRAINT uq_product_tenant_identification
    UNIQUE USING INDEX uq_product_tenant_identification;

COMMENT ON CONSTRAINT uq_product_tenant_identification ON public.product
    IS '产品同租户业务唯一约束（tenant_id + product_identification；供绑定表复合 FK 引用，ADR-013 受控迁移附加）';
