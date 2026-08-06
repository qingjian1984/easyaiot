-- STEP M1.5：product 同租户业务唯一索引（独立非事务步骤）
--
-- 只能由受控 runner 在事务外执行；失败产生的 INVALID index 必须
-- 按批准脚本清理，禁止自动归并产品。

CREATE UNIQUE INDEX CONCURRENTLY uq_product_tenant_identification
    ON public.product (tenant_id, product_identification);
