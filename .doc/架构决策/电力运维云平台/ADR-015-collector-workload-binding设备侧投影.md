# ADR-015：collector workload binding 设备侧投影（TD-001 §6.2 OPEN 项）

> 状态：**Proposed**（待评审。本 ADR 解除 V003 DDL 第 14-16 行登记的「活动 collector workload binding 的设备侧可见性」OPEN 项。评审通过转 Accepted 后，方可在 V003 增链投影表 DDL 并实现 `CollectorWorkloadImpactPort` / `CollectorConfigReleasePort.desiredMatches` 的 JDBC 实现）
> 版本：1.0.0
> 日期：2026-08-10
> 决策范围：TD-001 §6.2 `CollectorWorkloadImpactPort` 数据源——`productId → 受影响活动 collector workload` 的解析路径与持久化事实归属
> 影响章节：《EasyAIoT 项目开发宪法》§5.2、§5.4、§6.2、§第二条之二；TD-001 §3、§4.1、§6.2、§18 任务 7；V003 DDL 第 14-16 行 OPEN 项
> 产品基线：平台功能计划 1.4.0 / 项目开发宪法 1.5.0
> 上游 ADR：[ADR-014](ADR-014-Outbox事件Transport与消费者Inbox.md)（消费者 `iot-device-power-model-release` 即本 ADR 的调用方）、[ADR-013](ADR-013-受控数据库迁移执行器.md)（投影表落库 MUST 经 runner 增链）

| 版本 | 日期 | 变更 |
|---|---|---|
| 1.0.0 | 2026-08-10 | 首次形成候选。经 architect 对抗性审查否决「扩展 `iot_collector_config_release` 加来源字段」方案（首次发布死循环 + 职责混淆 + 触发器保护范围冲突），改为独立可变投影表 + iot-device 发布单状态机 upsert，不依赖 iot-node 事件同步 |

## 背景

TD-001 §6.2 电力物模型事件驱动的快照再生协调器（iot-device-biz 内，消费者组 `iot-device-power-model-release`）在处理 `POWER_PRODUCT_MODEL_BINDING_APPLIED_V1` / `ROLLED_BACK_V1` 时，须按固定顺序解析影响面：**product → 活动未软删 device → site → 活动 collector workload binding**（TD-001 §6.2 L262），最终得到受影响的 `workloadId` 列表，逐个生成单调递增 `configVersion` 的再生发布单。解析结果为空集是合法结果（写 `IMPACT_EMPTY` 协调审计后成功结束）。

四个协调端口中，`CollectorWorkloadImpactPort.resolveActiveWorkloads(tenantId, productId)` 即承担此解析。V003 DDL 第 14-16 行明确登记：

> `CollectorWorkloadImpactPort` 只读既有表（V002 binding / device / 站点），不需要新 DDL；**「活动 collector workload binding」的设备侧可见性（iot-node 查询或设备侧投影）为 TD-001 后续设计 OPEN 项，不在本步骤**。

本 ADR 即为处置该 OPEN 项。

### 仓库现状核对（事实，附证据）

- **iot-device 与 iot-node 完全解耦**：无 pom 依赖（`iot-device-biz/pom.xml` 不依赖 iot-node-api/biz，反向亦然）；无 Feign client（iot-node 无任何 `@FeignClient`，`ServiceNameConstants` 未定义 `IOT_NODE`）；不同数据库（iot-device master 指向 `iot-device10`/`iot-device20`，iot-node 指向 `iot-node20`）；无事件通道（iot-node 无 Kafka 配置；`power-model-release-v1` topic 完全在 iot-device 内闭环，iot-node 既不生产也不消费——ADR-014 L33/L64 明确唯一消费者是 iot-device-biz 内协调器）。
- **`node_workload_binding` 表（iot-node 库）缺关联字段**：字段仅 `id/node_id/workload_type/workload_id/status/process_pid/bind_at` + `BaseDO` 审计字段（`NodeWorkloadBindingDO.java`），**无 `tenant_id/product_id/device_id/site_id`**；由 iot-node-biz `NodeCommandServiceImpl` 在 deploy/stop 时写入；多租户配置 `ignore-tables` 含本表；`workloadType` 枚举现有 9 种，无 `iot-sink-collector`（TD-001 §5 计划新增）。
- **iot-device 库已有产品↔模板绑定事实**：V002 `power_product_model_binding`（已落库）含 `tenant_id/product_id/product_identification/template_version_id/template_code/template_version/binding_revision/status`。即 product→template 的绑定事实已在 iot-device 库内。
- **iot-device 库 product↔device 关联**：通过 `product_identification` 字符串关联（非外键）；`device` 表无 `site_id`；iot-device 库无 `site` 表（`site_id`/`site_code` 是 V003 计划在 `iot_collector_config_release` 引入）。
- **既有跨库直连先例（受控例外）**：iot-sink `PostProcessWorkerResolverImpl` 用 `DynamicDataSourceContextHolder.push("node")` + JdbcTemplate 直读 iot-node 库 `node_workload_binding`+`compute_node`（解析后处理 Worker 地址）。但该先例读取的是「Worker 运行地址」，**不涉及 product/device 关联**，且未查到由 ADR 明确授权——不构成可援引的普遍模式。

### 宪法与 ADR 约束

- 开发宪法 1.5.0 §5.2 L224：**禁止跨服务直接查询对方数据库**。
- §6.2 L264：**数据按业务域归属，不得建立跨服务共享表**。
- §第二条之二 L66：跨 Java 模块依赖必须通过 `iot-*-api` 或 `iot-common-*`，不得直接依赖其他模块的 `biz` 实现。
- §5.2 L223：高频/广播/耗时/无需即时结果的流程应优先使用消息总线；同步跨服务调用使用 OpenFeign 契约 + 超时降级。
- TD-001 §3 职责表：iot-device 权威职责含「点表配置、发布单、期望版本、应用结果」；iot-node 权威职责含「工作负载期望/观察状态、串口预占、调用 Agent」；`collector` Profile 禁止直连中心业务数据库。
- ADR-014：iot-device 是 power-model 事件生产者+消费者；iot-node 不参与 power-model 事件链。

## 目标用户与角色

| 角色 | 职责 | 最小权限边界 |
|---|---|---|
| iot-device 发布管线（TD-001 任务 7） | 人工首次发布、应用、回滚时同事务 upsert 投影表 | 仅写 `collector_workload_binding_projection` |
| TD-001 §6.2 协调器（`createRegenerationDraft`） | 再生发布单时同事务 upsert 投影表 | 仅写投影表 + `iot_collector_config_release` |
| workload 停止路径（OPEN 子项） | workload 停止时将 `lifecycle_status` 置 `STOPPED` | 仅更新投影表 `lifecycle_status` |
| DBA | 经 ADR-013 runner 增链执行投影表 DDL 落库 | `migration_executor` 角色 |
| 评审 | 本 ADR Proposed→Accepted 决策 | — |

`ImpactPort` 只读投影表；`createRegenerationDraft` 写发布单 + 投影表（单事务）。任何路径不得跨服务查 iot-node 库。

## 候选方案

| 方案 | 优点 | 风险/缺口 |
|---|---|---|
| A. iot-device 直连 iot-node 库 | 似乎直接 | **违宪**（§5.2 L224/§6.2 L264）；`node_workload_binding` 无 `product_id/device_id/site_id`，无法按 `productId` 过滤；需 JOIN iot-device 库 device 表→跨库 JOIN 不可能。**技术不可行 + 架构违规** |
| B. iot-node 提供 Feign RPC | 符合 §5.2 L222 同步调用规范 | `node_workload_binding` 无关联字段，iot-node 自身不知 workload↔product 映射，RPC 接口无法实现「按 productId 查 workload」；product 是 iot-device 概念，iot-node 持有 product 映射是**职责错位**；事件处理同步路径增加延迟/故障点 |
| C. 设备侧投影表 + iot-node→iot-device 事件同步 | 架构契合（事件驱动） | iot-node 当前无 Kafka 配置，需新建跨模块事件通道；`node_workload_binding` 无 product/device 关联，同步过来信息不足，仍需在 iot-device 侧补关联 |
| D. 扩展 `iot_collector_config_release` 加 `source_product_id/source_template_code/source_template_version/source_binding_revision` 来源字段 | 复用已有表，不新建表 | **首次发布死循环（CRITICAL）**：`createRegenerationDraft` 是再生路径，首次发布走人工 API（§7.1 POST /publish）不经过它→来源字段不写→`BINDING_APPLIED` 时 `ImpactPort` 查不到→空集伪装成「无受影响」→静默漏洞（违反 §6.2 「不得静默吞错」）；**职责混淆（HIGH）**：不可变快照表承担绑定投影，查询需 DISTINCT + status IN + 取最新非 ROLLED_BACK 隐式状态机；**触发器保护范围冲突（MEDIUM）**：来源字段加进触发器则与回滚语义冲突，不加则留误更新口子 |
| **E. 设备侧投影表 + iot-device 发布单状态机 upsert（推荐）** | 完全 iot-device 库内闭环，不跨服务；复用 iot-device 自身发布单事实（workload 存在性由「有发布单」隐含）；投影表可变、与不可变快照表职责分离；查询为点查 + 唯一索引 | 新增一张可变表；首次发布 API 与 workload 停止 API 须同事务 upsert 投影表（任务 7 范畴）；停止路径接线方式为 OPEN 子项 |

## 决策（候选）

采用方案 E：在 iot-device 库新建独立可变投影表 `collector_workload_binding_projection`，由 iot-device 自身的发布单状态机同事务 upsert，**不依赖 iot-node 事件同步**。本段使用宪法 §1.1 强度语义。

### MUST

- **新增表** `collector_workload_binding_projection`（iot-device 库），DDL 候选见本 ADR §投影表 DDL 候选；落库 MUST 经 ADR-013 受控 runner 增链步骤（V003 扩展或 V004），禁止手工执行。
- **唯一性**：`UNIQUE (tenant_id, workload_id)`——同一 workload 在投影表至多一行；upsert 以此为冲突键。
- **写入路径（三条，均同事务 upsert）**：
  1. 人工首次发布 API（§7.1 `POST /admin-api/device/collector-config/{id}/publish`）落 `PUBLISHED`/`APPLIED` 时——**这是方案 D 漏掉的关键路径，本方案 MUST 覆盖**；
  2. 协调器再生 `CollectorConfigReleasePort.createRegenerationDraft` 落发布单时；
  3. workload 停止路径置 `lifecycle_status='STOPPED'`（接线方式见 OPEN 子项）。
- **`lifecycle_status` 枚举**固定 `ACTIVE/STOPPED/RETIRED`；`PUBLISHED`/`APPLIED` → `ACTIVE`。未知状态按 `STOPPED` 保守处理（不再生）。
- **`ImpactPort.resolveActiveWorkloads` 查询**：`SELECT workload_id FROM collector_workload_binding_projection WHERE tenant_id=:tenantId AND product_id=:productId AND lifecycle_status='ACTIVE'`——点查，命中索引 `(tenant_id, product_id, lifecycle_status)`；返回空集是合法结果（写 `IMPACT_EMPTY` 审计）。
- **`desiredMatches` 幂等判定**：查投影表 `SELECT template_code, template_version, binding_revision WHERE tenant_id=? AND workload_id=? AND lifecycle_status='ACTIVE'`——单行读取，无 `ORDER BY config_version DESC`、无「取最新非 ROLLED_BACK」隐式状态机。
- **不跨服务**：`ImpactPort` 实现在 iot-device-biz，只读 iot-device 库投影表；禁止直连 iot-node 库、禁止 Feign 调 iot-node、禁止新建 iot-node→iot-device 事件通道。
- **与 §4.1 不可变约束隔离**：投影表是可变表（upsert by workload_id），不触发 `iot_collector_config_release` 的不可变触发器；发布单表保持纯净的「不可变下发快照」语义。
- **空态语义**：投影表为空（任务 7 发布管线未实现前）时，`ImpactPort` 返回空集 → `IMPACT_EMPTY` 审计 → 不产生发布单。这与「处理器注册表空态 → DLQ」同属设计内渐进式空态，非静默丢弃。

### SHOULD / MAY

- SHOULD workload 停止路径在 TD-001 任务 7 实现时细化为：iot-device 侧停止 API 同事务置 `STOPPED`，或 iot-node stop webhook 经既有控制面对账通道回写（不新建 Kafka）。停止是低频运维操作，不引入新中间件。
- SHOULD 投影表 `release_id` 字段指向最新 `PUBLISHED`/`APPLIED` 发布单行，用于审计溯源（ImpactPort 查询不依赖此字段）。
- MAY 在未来 iot-node 引入 Kafka 后，评估切换到「iot-node 推送 binding 事件 → iot-device upsert 投影表」的同步机制；届时本 ADR 的 Context/Alternatives 供回溯，切换须走新 ADR。
- MAY 投影表 DDL 进 V003 扩展（第四张表）或独立 V004，由评审定；无论何种形式，落库 MUST 经 ADR-013 runner。

## 后果

- **正向**：`ImpactPort` / `desiredMatches` 的 JDBC 实现可立即编写（查投影表，不阻塞于 iot-node）；解耦 iot-device↔iot-node，符合宪法 §5.2/§6.2；查询为点查，性能可预测；发布单表保持不可变纯净语义。
- **负向/成本**：新增一张可变表（与不可变快照表分离，属不同生命周期对象）；首次发布 API 与 workload 停止 API 必须同事务 upsert 投影表——这是任务 7 发布管线的实现约束，须在任务 7 评审时纳入。
- **渐进落地节奏**：
  1. 本 ADR 评审通过 → Accepted；
  2. V003 扩展（或 V004）增链投影表 DDL，经 ADR-013 runner 获批窗口落库；
  3. `ImpactPort` / `desiredMatches` JDBC 实现 + 真实库合同测试（沿用 TD005_PG_ENABLED 跳过模式）；
  4. 投影表 upsert 写入端随 TD-001 任务 7 发布管线接线；
  5. `PowerModelEventWiringConfiguration` 在四端口齐备后填充注册表（当前两端口已实现，ImpactPort 待本 ADR 落地，ReleasePort 待任务 7）。

## 被否决方案

- **方案 A（直连 iot-node 库）**：违反宪法 §5.2 L224/§6.2 L264；且 `node_workload_binding` 无 `product_id/device_id/site_id`，无法按 `productId` 解析，跨库 JOIN 不可能。iot-sink 的跨库直连先例读取的是 Worker 地址、不涉及 product 关联，且未查到 ADR 授权，不构成可援引模式。
- **方案 B（iot-node Feign RPC）**：`node_workload_binding` 无关联字段，iot-node 自身不知 workload↔product 映射；product 是 iot-device 概念，iot-node 持有 product 映射违反 TD-001 §3 职责边界；事件处理同步 RPC 增延迟与故障点，违反 §5.2 L223「耗时/无需即时结果优先消息总线」精神（协调器再生非即时同步语义）。
- **方案 C（投影表 + iot-node 事件同步）**：iot-node 当前无 Kafka，需新建跨模块事件通道；且 `node_workload_binding` 无 product/device 关联，同步过来信息不足。方案 E 证明不需要 iot-node 事件同步——iot-device 自身发布单事实已足够。
- **方案 D（扩展 `iot_collector_config_release` 加来源字段）**：经 architect 对抗性审查否决。**首次发布死循环（CRITICAL）**——`createRegenerationDraft` 是再生路径，首次发布走人工 API 不经过它，来源字段不写，`ImpactPort` 查不到，空集伪装成「无受影响」，违反 §6.2「不得静默吞错」与宪法 §15 持久证据原则（证据是错的）；**职责混淆（HIGH）**——不可变快照表承担绑定投影，查询需 DISTINCT + status IN + 取最新非 ROLLED_BACK 隐式状态机，`desiredMatches` 复杂化；**触发器保护范围冲突（MEDIUM）**——来源字段加进 `fn_iot_collector_config_release_immutable` 触发器则与回滚语义冲突（回滚行 `templateVersion` 为 null，INSERT 后无法 UPDATE 补全），不加则留误更新口子。方案 D 的「复用表 = KISS」是表面优势，实际把复杂性转移到查询/状态机/触发器。

## 投影表 DDL 候选

> 评审候选，未经本 ADR 转 Accepted + ADR-013 runner 获批窗口不得对目标实例执行。字段精简到 `ImpactPort` + `desiredMatches` 真正所需；中文注释为宪法 MUST。

```sql
-- ============================================================================
-- ADR-015 候选：collector_workload_binding_projection
-- 设备侧 collector workload 绑定投影（可变表，upsert by workload_id）
-- 由 iot-device 发布单状态机同事务 upsert，不依赖 iot-node 事件同步
-- 落库 MUST 经 ADR-013 runner 增链步骤
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
    lifecycle_status VARCHAR(16) NOT NULL CHECK (lifecycle_status IN ('ACTIVE','STOPPED','RETIRED')),
    last_synced_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT collector_workload_binding_projection_pkey PRIMARY KEY (id),
    CONSTRAINT uq_collector_workload_binding_projection_tenant_workload
        UNIQUE (tenant_id, workload_id),
    CONSTRAINT uq_collector_workload_binding_projection_tenant_id UNIQUE (tenant_id, id)
);

CREATE INDEX idx_collector_workload_binding_projection_product
    ON public.collector_workload_binding_projection (tenant_id, product_id, lifecycle_status);

COMMENT ON TABLE public.collector_workload_binding_projection IS 'collector workload 绑定设备侧投影（ADR-015：可变表，由 iot-device 发布单状态机 upsert，ImpactPort 据此解析 productId→活动 workload）';
COMMENT ON COLUMN public.collector_workload_binding_projection.id IS '主键（应用统一 ID 策略赋值，数据库不兜底生成）';
COMMENT ON COLUMN public.collector_workload_binding_projection.tenant_id IS '租户编号';
COMMENT ON COLUMN public.collector_workload_binding_projection.workload_id IS 'collector workload 标识（与 iot_collector_config_release.workload_id 一致）';
COMMENT ON COLUMN public.collector_workload_binding_projection.site_id IS '站点内部主键';
COMMENT ON COLUMN public.collector_workload_binding_projection.site_code IS '站点业务编码（不可变，用于对外查询）';
COMMENT ON COLUMN public.collector_workload_binding_projection.node_id IS '目标节点内部主键';
COMMENT ON COLUMN public.collector_workload_binding_projection.product_id IS '产品内部主键（ImpactPort 查询键）';
COMMENT ON COLUMN public.collector_workload_binding_projection.template_code IS '当前 desired 模板编码（desiredMatches 幂等判定）';
COMMENT ON COLUMN public.collector_workload_binding_projection.template_version IS '当前 desired 模板版本（desiredMatches 幂等判定）';
COMMENT ON COLUMN public.collector_workload_binding_projection.binding_revision IS '当前 desired 绑定修订号';
COMMENT ON COLUMN public.collector_workload_binding_projection.config_version IS '当前 desired 配置版本（最新 PUBLISHED/APPLIED）';
COMMENT ON COLUMN public.collector_workload_binding_projection.release_id IS '最新 PUBLISHED/APPLIED 发布单 ID（审计溯源，指向 iot_collector_config_release.id）';
COMMENT ON COLUMN public.collector_workload_binding_projection.lifecycle_status IS 'workload 生命周期状态（ACTIVE/STOPPED/RETIRED；PUBLISHED/APPLIED→ACTIVE，未知按 STOPPED 保守）';
COMMENT ON COLUMN public.collector_workload_binding_projection.last_synced_at IS '最近一次 upsert 时间（发布单状态机写入）';
COMMENT ON COLUMN public.collector_workload_binding_projection.created_at IS '创建时间';
COMMENT ON COLUMN public.collector_workload_binding_projection.updated_at IS '更新时间';
```

## OPEN 项

- 本 ADR Proposed→Accepted 评审（待架构负责人 + DBA）。
- 投影表 DDL 候选字段集与约束经评审定稿（当前为最小集，评审可增减）。
- workload 停止路径接线方式（iot-device 停止 API 同事务 vs iot-node stop webhook 回写）在 TD-001 任务 7 实现时细化。
- V003 扩展（第四张表）或独立 V004 的形式由评审定；落库 MUST 经 ADR-013 runner 获批窗口。
- `ImpactPort` / `desiredMatches` JDBC 实现 + 真实库合同测试，待本 ADR Accepted + 投影表落库后接入。
- `PowerModelEventWiringConfiguration` 四端口齐备后填充注册表（当前 Audit/TemplateReference 两端口已实现，ImpactPort 待本 ADR，ReleasePort 待任务 7）。
