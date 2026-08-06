-- power_idempotency_record 候选 DDL 临时库烟测（评审用，随临时库销毁）
\set ON_ERROR_STOP on

-- 正例：首次争抢 insert IN_PROGRESS（唯一作用域仲裁入口）
INSERT INTO public.power_idempotency_record
    (tenant_id, principal_type, principal_id, operation, key_hash, request_hash, state)
VALUES (1, 'USER', 'u-100', 'power.model.template.publish',
        decode(repeat('01', 32), 'hex'), decode(repeat('02', 32), 'hex'), 'IN_PROGRESS');

-- 默认 24 小时保留窗口
DO $$
DECLARE rec public.power_idempotency_record%ROWTYPE;
BEGIN
    SELECT * INTO rec FROM public.power_idempotency_record;
    ASSERT rec.expires_at > rec.created_at + INTERVAL '23 hours 59 minutes'
       AND rec.expires_at < rec.created_at + INTERVAL '24 hours 1 minute',
       'default 24h expiry violated';
END $$;

-- 状态迁移：IN_PROGRESS -> SUCCEEDED（携带终态响应）
UPDATE public.power_idempotency_record
   SET state = 'SUCCEEDED', http_status = 200,
       response_payload = '{"ok":true}'::jsonb,
       result_ref = 'tpl-1', updated_at = CURRENT_TIMESTAMP;

-- 反例组：每一项必须按预期约束拒绝
DO $$
BEGIN
    -- 1. key_hash 非 32 字节
    BEGIN
        INSERT INTO public.power_idempotency_record
            (tenant_id, principal_type, principal_id, operation, key_hash, request_hash, state)
        VALUES (1, 'USER', 'u-100', 'op', decode(repeat('01', 31), 'hex'), decode(repeat('02', 32), 'hex'), 'IN_PROGRESS');
        ASSERT FALSE, 'T1 key_hash length not rejected';
    EXCEPTION WHEN check_violation THEN NULL;
    END;
    -- 2. IN_PROGRESS 携带 http_status
    BEGIN
        INSERT INTO public.power_idempotency_record
            (tenant_id, principal_type, principal_id, operation, key_hash, request_hash, state, http_status)
        VALUES (1, 'USER', 'u-100', 'op', decode(repeat('03', 32), 'hex'), decode(repeat('02', 32), 'hex'), 'IN_PROGRESS', 200);
        ASSERT FALSE, 'T2 IN_PROGRESS with http_status not rejected';
    EXCEPTION WHEN check_violation THEN NULL;
    END;
    -- 3. SUCCEEDED 缺 http_status
    BEGIN
        INSERT INTO public.power_idempotency_record
            (tenant_id, principal_type, principal_id, operation, key_hash, request_hash, state)
        VALUES (1, 'USER', 'u-100', 'op', decode(repeat('04', 32), 'hex'), decode(repeat('02', 32), 'hex'), 'SUCCEEDED');
        ASSERT FALSE, 'T3 SUCCEEDED without http_status not rejected';
    EXCEPTION WHEN check_violation THEN NULL;
    END;
    -- 4. 非法状态枚举
    BEGIN
        INSERT INTO public.power_idempotency_record
            (tenant_id, principal_type, principal_id, operation, key_hash, request_hash, state)
        VALUES (1, 'USER', 'u-100', 'op', decode(repeat('05', 32), 'hex'), decode(repeat('02', 32), 'hex'), 'UNKNOWN');
        ASSERT FALSE, 'T4 invalid state not rejected';
    EXCEPTION WHEN check_violation THEN NULL;
    END;
    -- 5. expires_at 不晚于 created_at
    BEGIN
        INSERT INTO public.power_idempotency_record
            (tenant_id, principal_type, principal_id, operation, key_hash, request_hash, state, created_at, expires_at)
        VALUES (1, 'USER', 'u-100', 'op', decode(repeat('06', 32), 'hex'), decode(repeat('02', 32), 'hex'), 'IN_PROGRESS',
                TIMESTAMPTZ '2026-08-06 00:00:00+00', TIMESTAMPTZ '2026-08-06 00:00:00+00');
        ASSERT FALSE, 'T5 expiry ordering not rejected';
    EXCEPTION WHEN check_violation THEN NULL;
    END;
    -- 6. response_payload 超界（>16KiB）
    BEGIN
        INSERT INTO public.power_idempotency_record
            (tenant_id, principal_type, principal_id, operation, key_hash, request_hash, state, http_status, response_payload)
        VALUES (1, 'USER', 'u-100', 'op', decode(repeat('07', 32), 'hex'), decode(repeat('02', 32), 'hex'), 'FAILED_FINAL', 500,
                ('{"d":"' || repeat('x', 17000) || '"}')::jsonb);
        ASSERT FALSE, 'T6 oversized payload not rejected';
    EXCEPTION WHEN check_violation THEN NULL;
    END;
    -- 7. 非法 principal_type
    BEGIN
        INSERT INTO public.power_idempotency_record
            (tenant_id, principal_type, principal_id, operation, key_hash, request_hash, state)
        VALUES (1, 'ADMIN', 'u-100', 'op', decode(repeat('08', 32), 'hex'), decode(repeat('02', 32), 'hex'), 'IN_PROGRESS');
        ASSERT FALSE, 'T7 invalid principal_type not rejected';
    EXCEPTION WHEN check_violation THEN NULL;
    END;
    -- 8. 跨副本同作用域二次争抢（同 key_hash，不同 request_hash）必须唯一冲突
    BEGIN
        INSERT INTO public.power_idempotency_record
            (tenant_id, principal_type, principal_id, operation, key_hash, request_hash, state)
        VALUES (1, 'USER', 'u-100', 'power.model.template.publish',
                decode(repeat('01', 32), 'hex'), decode(repeat('09', 32), 'hex'), 'IN_PROGRESS');
        ASSERT FALSE, 'T8 duplicate scope contention not rejected';
    EXCEPTION WHEN unique_violation THEN NULL;
    END;
    -- 9. 不同租户同 key 不构成冲突（租户隔离维度生效，应插入成功）
    INSERT INTO public.power_idempotency_record
        (tenant_id, principal_type, principal_id, operation, key_hash, request_hash, state)
    VALUES (2, 'USER', 'u-100', 'power.model.template.publish',
            decode(repeat('01', 32), 'hex'), decode(repeat('02', 32), 'hex'), 'IN_PROGRESS');
END $$;

-- 注释完整性（MIG-009 同构）：表 + 14 列均须有非空中文注释
DO $$
DECLARE missing INTEGER;
BEGIN
    SELECT count(*) INTO missing
    FROM (
        SELECT COALESCE(d.description, '') AS cmt
        FROM pg_class c JOIN pg_namespace n ON n.oid = c.relnamespace
        LEFT JOIN pg_description d ON d.objoid = c.oid AND d.objsubid = 0
        WHERE n.nspname = 'public' AND c.relname = 'power_idempotency_record'
        UNION ALL
        SELECT COALESCE(d.description, '')
        FROM pg_class c JOIN pg_namespace n ON n.oid = c.relnamespace
        JOIN pg_attribute a ON a.attrelid = c.oid AND a.attnum > 0 AND NOT a.attisdropped
        LEFT JOIN pg_description d ON d.objoid = c.oid AND d.objsubid = a.attnum
        WHERE n.nspname = 'public' AND c.relname = 'power_idempotency_record'
    ) all_comments
    WHERE btrim(cmt) = '' OR cmt !~ '[一-龥]';
    ASSERT missing = 0, 'comment completeness violated: ' || missing;
END $$;

SELECT 'SMOKE_RESULT', count(*)::text FROM public.power_idempotency_record;
