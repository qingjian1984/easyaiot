-- TD-005 数据库角色与最小授权候选（仅供评审，禁止在生产/共享库执行）
--
-- 双基线：平台功能计划 1.4.0 / EasyAIoT 项目开发宪法 1.5.0
-- 上游：TD-005 migration 0.1.8 §9、ADR-013 1.4.0（Proposed）、ADR-014 1.1.0（Proposed）
-- 说明：DBA/架构专项评审 M-09 处置资产；四个角色分离，禁止共享超管凭据。
--       本脚本幂等（角色存在则跳过创建，GRANT 可重复执行）。

-- migration_executor：受控迁移执行角色（ADR-013）
DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'migration_executor') THEN
        CREATE ROLE migration_executor NOLOGIN;
    END IF;
END
$$;
COMMENT ON ROLE migration_executor IS 'TD-005 受控迁移执行角色（最小 DDL 权限，禁止超管）';

-- power_model_write：iot-device 电力模型业务写身份
DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'power_model_write') THEN
        CREATE ROLE power_model_write NOLOGIN;
    END IF;
END
$$;
COMMENT ON ROLE power_model_write IS '电力物模型业务写角色（模板/版本/绑定/审计/Outbox 入表）';

-- power_model_outbox_pub：Outbox 发布器内部服务身份（只更新状态列）
DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'power_model_outbox_pub') THEN
        CREATE ROLE power_model_outbox_pub NOLOGIN;
    END IF;
END
$$;
COMMENT ON ROLE power_model_outbox_pub IS 'Outbox 发布器角色（仅 SELECT 与状态列 UPDATE，不得改业务列）';

-- power_model_readonly：运维/审计只读身份
DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'power_model_readonly') THEN
        CREATE ROLE power_model_readonly NOLOGIN;
    END IF;
END
$$;
COMMENT ON ROLE power_model_readonly IS '电力模型只读运维角色（对账/审计查询）';

-- migration_executor：仅迁移步骤所需最小权限
GRANT CREATE ON SCHEMA public TO migration_executor;
GRANT INSERT, SELECT, UPDATE ON public.schema_migration_history TO migration_executor;

-- 业务写：模板/版本/成员索引/绑定可写；审计只允许 INSERT/SELECT；Outbox 只允许 INSERT/SELECT
GRANT SELECT, INSERT, UPDATE ON public.power_model_template TO power_model_write;
GRANT SELECT, INSERT, UPDATE ON public.power_model_template_version TO power_model_write;
GRANT SELECT, INSERT, UPDATE, DELETE ON public.power_model_member_index TO power_model_write;
GRANT SELECT, INSERT, UPDATE ON public.power_product_model_binding TO power_model_write;
GRANT INSERT, SELECT ON public.power_model_audit TO power_model_write;
GRANT INSERT, SELECT ON public.power_model_release_outbox TO power_model_write;

-- 发布器：读取待发布行，仅更新状态/租约/结果列
GRANT SELECT ON public.power_model_release_outbox TO power_model_outbox_pub;
GRANT UPDATE (status, retry_count, next_attempt_at, lease_until, lease_owner,
              last_error_code, last_error_digest, published_at, updated_at)
    ON public.power_model_release_outbox TO power_model_outbox_pub;

-- 只读运维：全表只读
GRANT SELECT ON public.power_model_template TO power_model_readonly;
GRANT SELECT ON public.power_model_template_version TO power_model_readonly;
GRANT SELECT ON public.power_model_member_index TO power_model_readonly;
GRANT SELECT ON public.power_model_audit TO power_model_readonly;
GRANT SELECT ON public.power_model_release_outbox TO power_model_readonly;
GRANT SELECT ON public.power_product_model_binding TO power_model_readonly;
GRANT SELECT ON public.schema_migration_history TO power_model_readonly;
