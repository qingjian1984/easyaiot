-- ============================================================================
-- TD-004 V006：电力站点、双树骨架、设备资产与当前/历史归属核心事实
--
-- 双基线：平台功能计划 1.4.0 / EasyAIoT 项目开发宪法 1.5.0
-- 上游：TD-004 1.0.1 §7.1～§7.4、TD-001 1.0.8 §6.3、SPEC-001 1.3.0
-- 执行：仅允许经 ADR-013 受控 runner；目标实例 apply 必须另有窗口批准
-- 档位：standard/full 共用；mini 只允许兼容空表，不初始化业务数据
-- ============================================================================

CREATE TABLE public.power_site (
    id BIGINT NOT NULL,
    tenant_id BIGINT NOT NULL,
    site_code VARCHAR(64) NOT NULL,
    site_name VARCHAR(128) NOT NULL,
    owner_dept_id BIGINT NOT NULL,
    iana_time_zone VARCHAR(64) NOT NULL,
    status VARCHAR(16) NOT NULL CHECK (status IN ('DRAFT','ACTIVE','INACTIVE','ARCHIVED')),
    version BIGINT NOT NULL DEFAULT 1 CHECK (version >= 1),
    created_by BIGINT NOT NULL,
    updated_by BIGINT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT power_site_pkey PRIMARY KEY (id),
    CONSTRAINT uq_power_site_tenant_code UNIQUE (tenant_id, site_code),
    CONSTRAINT uq_power_site_tenant_id UNIQUE (tenant_id, id),
    CONSTRAINT ck_power_site_code CHECK (
        site_code ~ '^[a-z0-9](?:[a-z0-9-]{0,62}[a-z0-9])?$'
    )
);

CREATE INDEX idx_power_site_tenant_status
    ON public.power_site (tenant_id, status, site_code);

CREATE TABLE public.power_space_node (
    id BIGINT NOT NULL,
    tenant_id BIGINT NOT NULL,
    site_id BIGINT NOT NULL,
    parent_id BIGINT,
    space_code VARCHAR(64) NOT NULL,
    space_type VARCHAR(64) NOT NULL,
    space_name VARCHAR(128) NOT NULL,
    sort_order INTEGER NOT NULL DEFAULT 0,
    status VARCHAR(16) NOT NULL CHECK (status IN ('DRAFT','ACTIVE','INACTIVE','ARCHIVED')),
    version BIGINT NOT NULL DEFAULT 1 CHECK (version >= 1),
    created_by BIGINT NOT NULL,
    updated_by BIGINT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT power_space_node_pkey PRIMARY KEY (id),
    CONSTRAINT uq_power_space_node_tenant_site_code UNIQUE (tenant_id, site_id, space_code),
    CONSTRAINT uq_power_space_node_tenant_site_id UNIQUE (tenant_id, site_id, id),
    CONSTRAINT fk_power_space_node_site FOREIGN KEY (tenant_id, site_id)
        REFERENCES public.power_site (tenant_id, id),
    CONSTRAINT fk_power_space_node_parent FOREIGN KEY (tenant_id, site_id, parent_id)
        REFERENCES public.power_space_node (tenant_id, site_id, id),
    CONSTRAINT ck_power_space_node_code CHECK (
        space_code ~ '^[a-z0-9](?:[a-z0-9-]{0,62}[a-z0-9])?$'
    ),
    CONSTRAINT ck_power_space_node_not_self CHECK (parent_id IS NULL OR parent_id <> id)
);

CREATE INDEX idx_power_space_node_tree
    ON public.power_space_node (tenant_id, site_id, parent_id, sort_order, space_code, id);

CREATE TABLE public.power_circuit (
    id BIGINT NOT NULL,
    tenant_id BIGINT NOT NULL,
    site_id BIGINT NOT NULL,
    parent_id BIGINT,
    circuit_code VARCHAR(64) NOT NULL,
    circuit_name VARCHAR(128) NOT NULL,
    circuit_type VARCHAR(64) NOT NULL,
    sort_order INTEGER NOT NULL DEFAULT 0,
    status VARCHAR(16) NOT NULL CHECK (status IN ('DRAFT','ACTIVE','INACTIVE','ARCHIVED')),
    version BIGINT NOT NULL DEFAULT 1 CHECK (version >= 1),
    created_by BIGINT NOT NULL,
    updated_by BIGINT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT power_circuit_pkey PRIMARY KEY (id),
    CONSTRAINT uq_power_circuit_tenant_site_code UNIQUE (tenant_id, site_id, circuit_code),
    CONSTRAINT uq_power_circuit_tenant_site_id UNIQUE (tenant_id, site_id, id),
    CONSTRAINT fk_power_circuit_site FOREIGN KEY (tenant_id, site_id)
        REFERENCES public.power_site (tenant_id, id),
    CONSTRAINT fk_power_circuit_parent FOREIGN KEY (tenant_id, site_id, parent_id)
        REFERENCES public.power_circuit (tenant_id, site_id, id),
    CONSTRAINT ck_power_circuit_code CHECK (
        circuit_code ~ '^[a-z0-9](?:[a-z0-9-]{0,62}[a-z0-9])?$'
    ),
    CONSTRAINT ck_power_circuit_not_self CHECK (parent_id IS NULL OR parent_id <> id)
);

CREATE INDEX idx_power_circuit_tree
    ON public.power_circuit (tenant_id, site_id, parent_id, sort_order, circuit_code, id);

CREATE TABLE public.power_device_asset (
    id BIGINT NOT NULL,
    tenant_id BIGINT NOT NULL,
    device_id BIGINT NOT NULL,
    asset_code VARCHAR(64),
    object_type VARCHAR(64) NOT NULL,
    status VARCHAR(16) NOT NULL CHECK (status IN ('ACTIVE','INACTIVE','ARCHIVED')),
    version BIGINT NOT NULL DEFAULT 1 CHECK (version >= 1),
    created_by BIGINT NOT NULL,
    updated_by BIGINT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT power_device_asset_pkey PRIMARY KEY (id),
    CONSTRAINT uq_power_device_asset_tenant_device UNIQUE (tenant_id, device_id),
    CONSTRAINT uq_power_device_asset_tenant_code UNIQUE (tenant_id, asset_code),
    CONSTRAINT fk_power_device_asset_device FOREIGN KEY (device_id) REFERENCES public.device (id),
    CONSTRAINT ck_power_device_asset_code CHECK (
        asset_code IS NULL OR asset_code ~ '^[a-z0-9](?:[a-z0-9-]{0,62}[a-z0-9])?$'
    ),
    CONSTRAINT ck_power_device_asset_object_type CHECK (btrim(object_type) <> '')
);

CREATE TABLE public.power_device_assignment (
    id BIGINT NOT NULL,
    tenant_id BIGINT NOT NULL,
    device_id BIGINT NOT NULL,
    site_id BIGINT NOT NULL,
    primary_space_id BIGINT,
    primary_circuit_id BIGINT,
    valid_from TIMESTAMPTZ NOT NULL,
    valid_to TIMESTAMPTZ,
    change_reason VARCHAR(256) NOT NULL,
    version BIGINT NOT NULL DEFAULT 1 CHECK (version >= 1),
    created_by BIGINT NOT NULL,
    updated_by BIGINT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT power_device_assignment_pkey PRIMARY KEY (id),
    CONSTRAINT fk_power_device_assignment_asset FOREIGN KEY (tenant_id, device_id)
        REFERENCES public.power_device_asset (tenant_id, device_id),
    CONSTRAINT fk_power_device_assignment_site FOREIGN KEY (tenant_id, site_id)
        REFERENCES public.power_site (tenant_id, id),
    CONSTRAINT fk_power_device_assignment_space FOREIGN KEY (tenant_id, site_id, primary_space_id)
        REFERENCES public.power_space_node (tenant_id, site_id, id),
    CONSTRAINT fk_power_device_assignment_circuit FOREIGN KEY (tenant_id, site_id, primary_circuit_id)
        REFERENCES public.power_circuit (tenant_id, site_id, id),
    CONSTRAINT ck_power_device_assignment_range CHECK (valid_to IS NULL OR valid_to > valid_from),
    CONSTRAINT ck_power_device_assignment_reason CHECK (btrim(change_reason) <> '')
);

CREATE UNIQUE INDEX uq_power_device_assignment_current
    ON public.power_device_assignment (tenant_id, device_id)
    WHERE valid_to IS NULL;
CREATE INDEX idx_power_device_assignment_site_history
    ON public.power_device_assignment (tenant_id, site_id, valid_to, valid_from DESC);
CREATE INDEX idx_power_device_assignment_device_history
    ON public.power_device_assignment (tenant_id, device_id, valid_from DESC);

CREATE OR REPLACE FUNCTION public.fn_power_device_asset_tenant_guard()
RETURNS trigger
LANGUAGE plpgsql
AS $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM public.device d
        WHERE d.id = NEW.device_id AND d.tenant_id = NEW.tenant_id
    ) THEN
        RAISE EXCEPTION 'POWER_DEVICE_TENANT_MISMATCH';
    END IF;
    RETURN NEW;
END;
$$;

CREATE TRIGGER trg_power_device_asset_tenant_guard
    BEFORE INSERT OR UPDATE OF tenant_id, device_id ON public.power_device_asset
    FOR EACH ROW EXECUTE FUNCTION public.fn_power_device_asset_tenant_guard();

CREATE OR REPLACE FUNCTION public.fn_power_object_identity_guard()
RETURNS trigger
LANGUAGE plpgsql
AS $$
BEGIN
    IF TG_TABLE_NAME = 'power_site' AND (
        NEW.id IS DISTINCT FROM OLD.id
        OR NEW.tenant_id IS DISTINCT FROM OLD.tenant_id
        OR NEW.site_code IS DISTINCT FROM OLD.site_code
    ) THEN
        RAISE EXCEPTION 'POWER_SITE_IDENTITY_IMMUTABLE';
    ELSIF TG_TABLE_NAME = 'power_space_node' AND (
        NEW.id IS DISTINCT FROM OLD.id
        OR NEW.tenant_id IS DISTINCT FROM OLD.tenant_id
        OR NEW.site_id IS DISTINCT FROM OLD.site_id
        OR NEW.space_code IS DISTINCT FROM OLD.space_code
    ) THEN
        RAISE EXCEPTION 'POWER_SPACE_IDENTITY_IMMUTABLE';
    ELSIF TG_TABLE_NAME = 'power_circuit' AND (
        NEW.id IS DISTINCT FROM OLD.id
        OR NEW.tenant_id IS DISTINCT FROM OLD.tenant_id
        OR NEW.site_id IS DISTINCT FROM OLD.site_id
        OR NEW.circuit_code IS DISTINCT FROM OLD.circuit_code
    ) THEN
        RAISE EXCEPTION 'POWER_CIRCUIT_IDENTITY_IMMUTABLE';
    ELSIF TG_TABLE_NAME = 'power_device_asset'
          AND (
              NEW.id IS DISTINCT FROM OLD.id
              OR NEW.tenant_id IS DISTINCT FROM OLD.tenant_id
              OR NEW.device_id IS DISTINCT FROM OLD.device_id
              OR (OLD.asset_code IS NOT NULL AND NEW.asset_code IS DISTINCT FROM OLD.asset_code)
          ) THEN
        RAISE EXCEPTION 'POWER_ASSET_IDENTITY_IMMUTABLE';
    END IF;
    RETURN NEW;
END;
$$;

CREATE TRIGGER trg_power_site_code_immutable BEFORE UPDATE ON public.power_site
    FOR EACH ROW EXECUTE FUNCTION public.fn_power_object_identity_guard();
CREATE TRIGGER trg_power_space_code_immutable BEFORE UPDATE ON public.power_space_node
    FOR EACH ROW EXECUTE FUNCTION public.fn_power_object_identity_guard();
CREATE TRIGGER trg_power_circuit_code_immutable BEFORE UPDATE ON public.power_circuit
    FOR EACH ROW EXECUTE FUNCTION public.fn_power_object_identity_guard();
CREATE TRIGGER trg_power_asset_code_immutable BEFORE UPDATE ON public.power_device_asset
    FOR EACH ROW EXECUTE FUNCTION public.fn_power_object_identity_guard();

CREATE OR REPLACE FUNCTION public.fn_power_device_assignment_history_guard()
RETURNS trigger
LANGUAGE plpgsql
AS $$
BEGIN
    IF NEW.id IS DISTINCT FROM OLD.id
       OR NEW.tenant_id IS DISTINCT FROM OLD.tenant_id
       OR NEW.device_id IS DISTINCT FROM OLD.device_id
       OR NEW.site_id IS DISTINCT FROM OLD.site_id
       OR NEW.primary_space_id IS DISTINCT FROM OLD.primary_space_id
       OR NEW.primary_circuit_id IS DISTINCT FROM OLD.primary_circuit_id
       OR NEW.valid_from IS DISTINCT FROM OLD.valid_from
       OR NEW.change_reason IS DISTINCT FROM OLD.change_reason
       OR NEW.created_by IS DISTINCT FROM OLD.created_by
       OR NEW.created_at IS DISTINCT FROM OLD.created_at THEN
        RAISE EXCEPTION 'POWER_ASSIGNMENT_HISTORY_IMMUTABLE';
    END IF;
    IF OLD.valid_to IS NOT NULL OR NEW.valid_to IS NULL THEN
        RAISE EXCEPTION 'POWER_ASSIGNMENT_INVALID_CLOSE_TRANSITION';
    END IF;
    IF NEW.version <> OLD.version + 1 THEN
        RAISE EXCEPTION 'POWER_ASSIGNMENT_VERSION_CONFLICT';
    END IF;
    RETURN NEW;
END;
$$;

CREATE TRIGGER trg_power_device_assignment_history_guard
    BEFORE UPDATE ON public.power_device_assignment
    FOR EACH ROW EXECUTE FUNCTION public.fn_power_device_assignment_history_guard();

COMMENT ON TABLE public.power_site IS '电力站点权威事实（TD-004：租户内编码不可变，standard/full 共用）';
COMMENT ON COLUMN public.power_site.id IS '站点内部主键（公共 JSON 按十进制字符串返回）';
COMMENT ON COLUMN public.power_site.tenant_id IS '认证租户编号，不接受业务请求扩大范围';
COMMENT ON COLUMN public.power_site.site_code IS '租户内唯一且创建后不可修改的规范站点编码';
COMMENT ON COLUMN public.power_site.site_name IS '站点显示名称，可重复';
COMMENT ON COLUMN public.power_site.owner_dept_id IS 'iot-system 部门内部主键，仅经 API 校验，不建立跨库外键';
COMMENT ON COLUMN public.power_site.iana_time_zone IS '站点 IANA 时区，由服务层按 ZoneId 校验';
COMMENT ON COLUMN public.power_site.status IS '站点状态：DRAFT/ACTIVE/INACTIVE/ARCHIVED';
COMMENT ON COLUMN public.power_site.version IS '乐观锁版本，从 1 开始';
COMMENT ON COLUMN public.power_site.created_by IS '创建操作者内部主键';
COMMENT ON COLUMN public.power_site.updated_by IS '最后更新操作者内部主键';
COMMENT ON COLUMN public.power_site.created_at IS '服务端带时区创建时间';
COMMENT ON COLUMN public.power_site.updated_at IS '服务端带时区最后更新时间';

COMMENT ON TABLE public.power_space_node IS '电力空间树节点（与一次回路树独立，父节点必须同租户同站点）';
COMMENT ON COLUMN public.power_space_node.id IS '空间节点内部主键';
COMMENT ON COLUMN public.power_space_node.tenant_id IS '认证租户编号';
COMMENT ON COLUMN public.power_space_node.site_id IS '所属电力站点内部主键';
COMMENT ON COLUMN public.power_space_node.parent_id IS '同租户同站点父空间节点，根节点为空';
COMMENT ON COLUMN public.power_space_node.space_code IS '站点内唯一且不可修改的规范空间编码';
COMMENT ON COLUMN public.power_space_node.space_type IS '标准空间类型或租户局部 x- 扩展类型';
COMMENT ON COLUMN public.power_space_node.space_name IS '空间显示名称';
COMMENT ON COLUMN public.power_space_node.sort_order IS '同级展示顺序，重复时按编码和主键稳定排序';
COMMENT ON COLUMN public.power_space_node.status IS '空间状态：DRAFT/ACTIVE/INACTIVE/ARCHIVED';
COMMENT ON COLUMN public.power_space_node.version IS '乐观锁版本，从 1 开始';
COMMENT ON COLUMN public.power_space_node.created_by IS '创建操作者内部主键';
COMMENT ON COLUMN public.power_space_node.updated_by IS '最后更新操作者内部主键';
COMMENT ON COLUMN public.power_space_node.created_at IS '服务端带时区创建时间';
COMMENT ON COLUMN public.power_space_node.updated_at IS '服务端带时区最后更新时间';

COMMENT ON TABLE public.power_circuit IS '电力一次回路树节点（与空间树独立，父节点必须同租户同站点）';
COMMENT ON COLUMN public.power_circuit.id IS '回路内部主键';
COMMENT ON COLUMN public.power_circuit.tenant_id IS '认证租户编号';
COMMENT ON COLUMN public.power_circuit.site_id IS '所属电力站点内部主键';
COMMENT ON COLUMN public.power_circuit.parent_id IS '同租户同站点父回路，根回路为空';
COMMENT ON COLUMN public.power_circuit.circuit_code IS '站点内唯一且不可修改的规范回路编码';
COMMENT ON COLUMN public.power_circuit.circuit_name IS '回路显示名称';
COMMENT ON COLUMN public.power_circuit.circuit_type IS '标准回路类型或租户局部 x- 扩展类型';
COMMENT ON COLUMN public.power_circuit.sort_order IS '同级展示顺序，重复时按编码和主键稳定排序';
COMMENT ON COLUMN public.power_circuit.status IS '回路状态：DRAFT/ACTIVE/INACTIVE/ARCHIVED';
COMMENT ON COLUMN public.power_circuit.version IS '乐观锁版本，从 1 开始';
COMMENT ON COLUMN public.power_circuit.created_by IS '创建操作者内部主键';
COMMENT ON COLUMN public.power_circuit.updated_by IS '最后更新操作者内部主键';
COMMENT ON COLUMN public.power_circuit.created_at IS '服务端带时区创建时间';
COMMENT ON COLUMN public.power_circuit.updated_at IS '服务端带时区最后更新时间';

COMMENT ON TABLE public.power_device_asset IS '设备永久电力资产身份（与站点归属历史分离，资产编码不可复用）';
COMMENT ON COLUMN public.power_device_asset.id IS '电力资产身份内部主键';
COMMENT ON COLUMN public.power_device_asset.tenant_id IS '认证租户编号，与 device.tenant_id 由触发器一致性校验';
COMMENT ON COLUMN public.power_device_asset.device_id IS '现有 device.id，设备身份唯一事实仍归 device 表';
COMMENT ON COLUMN public.power_device_asset.asset_code IS '租户内永久唯一且不可修改的规范资产编码，可为空';
COMMENT ON COLUMN public.power_device_asset.object_type IS 'SPEC-001 标准设备类型或租户局部 x- 扩展类型';
COMMENT ON COLUMN public.power_device_asset.status IS '资产状态：ACTIVE/INACTIVE/ARCHIVED';
COMMENT ON COLUMN public.power_device_asset.version IS '资产乐观锁版本，从 1 开始';
COMMENT ON COLUMN public.power_device_asset.created_by IS '创建操作者内部主键';
COMMENT ON COLUMN public.power_device_asset.updated_by IS '最后更新操作者内部主键';
COMMENT ON COLUMN public.power_device_asset.created_at IS '服务端带时区创建时间';
COMMENT ON COLUMN public.power_device_asset.updated_at IS '服务端带时区最后更新时间';

COMMENT ON TABLE public.power_device_assignment IS '设备电力站点当前与历史归属（左闭右开，当前记录 valid_to 为空）';
COMMENT ON COLUMN public.power_device_assignment.id IS '设备归属历史记录内部主键';
COMMENT ON COLUMN public.power_device_assignment.tenant_id IS '认证租户编号，与资产和站点复合外键一致';
COMMENT ON COLUMN public.power_device_assignment.device_id IS '现有设备内部主键，必须先存在同租户电力资产身份';
COMMENT ON COLUMN public.power_device_assignment.site_id IS '必填主站点内部主键';
COMMENT ON COLUMN public.power_device_assignment.primary_space_id IS '可空主空间，必须与站点同租户同站点';
COMMENT ON COLUMN public.power_device_assignment.primary_circuit_id IS '可空主回路，必须与站点同租户同站点';
COMMENT ON COLUMN public.power_device_assignment.valid_from IS '归属生效时间，左闭边界';
COMMENT ON COLUMN public.power_device_assignment.valid_to IS '归属失效时间，右开边界；当前归属为空';
COMMENT ON COLUMN public.power_device_assignment.change_reason IS '归属新增或变更原因，禁止空白';
COMMENT ON COLUMN public.power_device_assignment.version IS '归属记录乐观锁版本，从 1 开始';
COMMENT ON COLUMN public.power_device_assignment.created_by IS '创建操作者内部主键';
COMMENT ON COLUMN public.power_device_assignment.updated_by IS '最后更新操作者内部主键';
COMMENT ON COLUMN public.power_device_assignment.created_at IS '服务端带时区创建时间';
COMMENT ON COLUMN public.power_device_assignment.updated_at IS '服务端带时区最后更新时间';
