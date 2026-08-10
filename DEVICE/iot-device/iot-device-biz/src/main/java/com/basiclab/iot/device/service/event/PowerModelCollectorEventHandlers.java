package com.basiclab.iot.device.service.event;

import com.basiclab.iot.device.event.PowerModelEventEnvelope;
import com.basiclab.iot.device.service.event.PowerModelEventHandlerRegistry.PowerModelEventHandler;
import com.basiclab.iot.device.service.event.PowerModelEventHandlerRegistry.PowerModelEventProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * TD-001 §6.2：电力物模型事件驱动的快照再生协调器——四个 V1 事件处理器。
 *
 * <p>语义（owner 2026-08-08 评审通过）：</p>
 * <ul>
 *   <li>PUBLISHED：审计 noop——发布本身不触发快照再生；字段缺失按 final 进 DLQ；</li>
 *   <li>LIFECYCLE_CHANGED：只写引用标记（后续人工发布确认页提示），
 *       <b>绝不改写已发布快照</b>（PRD-01 §4.2）；</li>
 *   <li>BINDING_APPLIED / ROLLED_BACK：解析影响面 → 幂等判定 → 按 workload 生成
 *       单调递增 configVersion 的再生发布单（绝不递减，静态校验冲突落 FAILED，
 *       不静默降级）；影响面为空写 IMPACT_EMPTY 审计（宪法 §15 持久证据）。</li>
 * </ul>
 *
 * <p>异常分流：处理器内字段缺失/解析失败 → final（MODEL_* 稳定码）；
 * 端口 {@link IllegalArgumentException} → final（业务终态，如静态校验冲突）；
 * 其余 {@link RuntimeException} → retryable（瞬态，退避重试）。Java 8 兼容。</p>
 */
public final class PowerModelCollectorEventHandlers {

    /** 字段缺失/类型不符（final）。 */
    public static final String CODE_DATA_FIELD_MISSING = "MODEL_EVENT_DATA_FIELD_MISSING";
    /** dataJson 畸形（final）。 */
    public static final String CODE_DATA_MALFORMED = "MODEL_EVENT_DATA_MALFORMED";
    /** 租户标识非法（final）。 */
    public static final String CODE_TENANT_INVALID = "MODEL_COORDINATION_TENANT_INVALID";
    /** 端口业务终态失败（final）。 */
    public static final String CODE_PORT_FINAL = "MODEL_COORDINATION_PORT_FINAL";
    /** 端口瞬态失败（retryable）。 */
    public static final String CODE_PORT_RETRYABLE = "MODEL_COORDINATION_PORT_RETRYABLE";

    /** 审计动作：发布事件已记录（noop-with-audit）。 */
    public static final String ACTION_PUBLISHED_NOTED = "TEMPLATE_PUBLISHED_NOTED";
    /** 审计动作：生命周期引用标记已写入。 */
    public static final String ACTION_LIFECYCLE_MARKED = "LIFECYCLE_REFERENCE_MARKED";
    /** 审计动作：影响面为空。 */
    public static final String ACTION_IMPACT_EMPTY = "IMPACT_EMPTY";
    /** 审计动作：再生发布单已生成。 */
    public static final String ACTION_DRAFTS_CREATED = "REGENERATION_DRAFTS_CREATED";

    /** 审计 detail 上限（字符）。 */
    static final int AUDIT_DETAIL_MAX = 512;

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private PowerModelCollectorEventHandlers() {
    }

    /**
     * 构建四个 V1 事件处理器注册表（eventType → handler）。
     *
     * @param impactPort    受影响 workload 解析端口
     * @param releasePort   collector 配置发布单端口
     * @param referencePort 模板生命周期引用标记端口
     * @param auditPort     协调审计端口
     * @return 有序注册表（不可变语义由 {@link PowerModelEventHandlerRegistry} 保证）
     */
    public static Map<String, PowerModelEventHandler> create(CollectorWorkloadImpactPort impactPort,
                                                             CollectorConfigReleasePort releasePort,
                                                             PowerModelTemplateReferencePort referencePort,
                                                             PowerModelCoordinationAuditPort auditPort) {
        Objects.requireNonNull(impactPort, "impactPort");
        Objects.requireNonNull(releasePort, "releasePort");
        Objects.requireNonNull(referencePort, "referencePort");
        Objects.requireNonNull(auditPort, "auditPort");
        Map<String, PowerModelEventHandler> handlers =
                new LinkedHashMap<String, PowerModelEventHandler>();
        handlers.put(PowerModelEventEnvelope.EVENT_TEMPLATE_PUBLISHED_V1,
                new TemplatePublishedHandler(auditPort));
        handlers.put(PowerModelEventEnvelope.EVENT_TEMPLATE_LIFECYCLE_CHANGED_V1,
                new LifecycleChangedHandler(referencePort, auditPort));
        handlers.put(PowerModelEventEnvelope.EVENT_BINDING_APPLIED_V1,
                new BindingImpactHandler(impactPort, releasePort, auditPort, true));
        handlers.put(PowerModelEventEnvelope.EVENT_BINDING_ROLLED_BACK_V1,
                new BindingImpactHandler(impactPort, releasePort, auditPort, false));
        return handlers;
    }

    // ------------------------------------------------------------------
    // 共享解析与分流助手
    // ------------------------------------------------------------------

    private static JsonNode parseData(String dataJson) {
        if (dataJson == null || dataJson.trim().isEmpty()) {
            throw new PowerModelEventProcessingException(false, CODE_DATA_MALFORMED,
                    "data 载荷为空", null);
        }
        JsonNode root;
        try {
            root = MAPPER.readTree(dataJson);
        } catch (Exception e) {
            throw new PowerModelEventProcessingException(false, CODE_DATA_MALFORMED,
                    "data 载荷无法解析为 JSON", e);
        }
        if (root == null || !root.isObject()) {
            throw new PowerModelEventProcessingException(false, CODE_DATA_MALFORMED,
                    "data 载荷必须为 JSON 对象", null);
        }
        return root;
    }

    private static String requiredText(JsonNode root, String field) {
        JsonNode node = root.get(field);
        if (node == null || !node.isTextual() || node.asText().trim().isEmpty()) {
            throw new PowerModelEventProcessingException(false, CODE_DATA_FIELD_MISSING,
                    "data." + field + " 缺失或非字符串", null);
        }
        return node.asText();
    }

    private static long requiredDecimalId(JsonNode root, String field) {
        JsonNode node = root.get(field);
        if (node == null || !node.isTextual() || !node.asText().matches("^[0-9]+$")) {
            throw new PowerModelEventProcessingException(false, CODE_DATA_FIELD_MISSING,
                    "data." + field + " 缺失或不是十进制 ID 字符串", null);
        }
        try {
            long value = Long.parseLong(node.asText());
            if (value <= 0) {
                throw new NumberFormatException("non-positive");
            }
            return value;
        } catch (NumberFormatException e) {
            throw new PowerModelEventProcessingException(false, CODE_DATA_FIELD_MISSING,
                    "data." + field + " 超出正 bigint 范围", e);
        }
    }

    private static long tenantIdOf(PowerModelEventEnvelope envelope) {
        try {
            return Long.parseLong(envelope.tenantId());
        } catch (NumberFormatException e) {
            throw new PowerModelEventProcessingException(false, CODE_TENANT_INVALID,
                    "envelope.tenantId 非数值: " + envelope.tenantId(), e);
        }
    }

    /** 端口异常分流：IllegalArgumentException → final；其余 RuntimeException → retryable。 */
    private static PowerModelEventProcessingException mapPortFailure(RuntimeException e) {
        if (e instanceof PowerModelEventProcessingException) {
            return (PowerModelEventProcessingException) e;
        }
        if (e instanceof IllegalArgumentException) {
            return new PowerModelEventProcessingException(false, CODE_PORT_FINAL,
                    "端口业务终态失败: " + e.getClass().getSimpleName(), e);
        }
        return new PowerModelEventProcessingException(true, CODE_PORT_RETRYABLE,
                "端口瞬态失败: " + e.getClass().getSimpleName(), e);
    }

    /** 审计 detail 有界化（≤512 字符，绝不携带 payload 正文）。 */
    private static String bounded(String detail) {
        if (detail == null) {
            return "";
        }
        return detail.length() <= AUDIT_DETAIL_MAX ? detail : detail.substring(0, AUDIT_DETAIL_MAX);
    }

    // ------------------------------------------------------------------
    // 处理器一：模板已发布（noop-with-audit）
    // ------------------------------------------------------------------

    static final class TemplatePublishedHandler implements PowerModelEventHandler {

        private final PowerModelCoordinationAuditPort auditPort;

        TemplatePublishedHandler(PowerModelCoordinationAuditPort auditPort) {
            this.auditPort = auditPort;
        }

        @Override
        public void handle(PowerModelEventEnvelope envelope, String dataJson) {
            JsonNode data = parseData(dataJson);
            String templateCode = requiredText(data, "templateCode");
            String templateVersion = requiredText(data, "templateVersion");
            long tenantId = tenantIdOf(envelope);
            try {
                auditPort.record(envelope.eventId(), tenantId, envelope.eventType(), ACTION_PUBLISHED_NOTED,
                        bounded("templateCode=" + templateCode + ", templateVersion="
                                + templateVersion + "；发布本身不触发快照再生（noop-with-audit）"));
            } catch (RuntimeException e) {
                throw mapPortFailure(e);
            }
        }
    }

    // ------------------------------------------------------------------
    // 处理器二：模板生命周期变更（只写引用标记，绝不改写快照）
    // ------------------------------------------------------------------

    static final class LifecycleChangedHandler implements PowerModelEventHandler {

        private final PowerModelTemplateReferencePort referencePort;
        private final PowerModelCoordinationAuditPort auditPort;

        LifecycleChangedHandler(PowerModelTemplateReferencePort referencePort,
                                PowerModelCoordinationAuditPort auditPort) {
            this.referencePort = referencePort;
            this.auditPort = auditPort;
        }

        @Override
        public void handle(PowerModelEventEnvelope envelope, String dataJson) {
            JsonNode data = parseData(dataJson);
            String templateCode = requiredText(data, "templateCode");
            String templateVersion = requiredText(data, "templateVersion");
            String fromLifecycle = requiredText(data, "fromLifecycle");
            String toLifecycle = requiredText(data, "toLifecycle");
            long tenantId = tenantIdOf(envelope);
            try {
                referencePort.markLifecycleReference(tenantId, templateCode, templateVersion,
                        fromLifecycle, toLifecycle, envelope.eventId());
                auditPort.record(envelope.eventId(), tenantId, envelope.eventType(), ACTION_LIFECYCLE_MARKED,
                        bounded("templateCode=" + templateCode + ", templateVersion=" + templateVersion
                                + ", " + fromLifecycle + "->" + toLifecycle
                                + "；仅引用标记，不改写已发布快照"));
            } catch (RuntimeException e) {
                throw mapPortFailure(e);
            }
        }
    }

    // ------------------------------------------------------------------
    // 处理器三/四：绑定应用/回滚（影响面解析 + 快照再生发布单）
    // ------------------------------------------------------------------

    static final class BindingImpactHandler implements PowerModelEventHandler {

        private final CollectorWorkloadImpactPort impactPort;
        private final CollectorConfigReleasePort releasePort;
        private final PowerModelCoordinationAuditPort auditPort;
        /** true=BINDING_APPLIED（data 携带 templateVersion）；false=ROLLED_BACK（由端口按 bindingRevision 解析）。 */
        private final boolean applied;

        BindingImpactHandler(CollectorWorkloadImpactPort impactPort,
                             CollectorConfigReleasePort releasePort,
                             PowerModelCoordinationAuditPort auditPort,
                             boolean applied) {
            this.impactPort = impactPort;
            this.releasePort = releasePort;
            this.auditPort = auditPort;
            this.applied = applied;
        }

        @Override
        public void handle(PowerModelEventEnvelope envelope, String dataJson) {
            JsonNode data = parseData(dataJson);
            long productId = requiredDecimalId(data, "productId");
            String templateCode = applied ? requiredText(data, "templateCode") : null;
            String templateVersion = applied ? requiredText(data, "templateVersion") : null;
            long bindingRevision = requiredDecimalId(data,
                    applied ? "bindingRevision" : "toBindingRevision");
            long confirmedBy = requiredDecimalId(data, applied ? "appliedBy" : "rolledBackBy");
            String reasonCode = applied ? "BINDING_APPLIED" : "BINDING_ROLLED_BACK";
            long tenantId = tenantIdOf(envelope);
            try {
                List<String> workloads = impactPort.resolveActiveWorkloads(tenantId, productId);
                if (workloads == null) {
                    throw new PowerModelEventProcessingException(false, CODE_PORT_FINAL,
                            "impactPort 返回 null（合同违反：空集须以空 List 表达）", null);
                }
                if (workloads.isEmpty()) {
                    auditPort.record(envelope.eventId(), tenantId, envelope.eventType(), ACTION_IMPACT_EMPTY,
                            bounded("productId=" + productId + ", bindingRevision=" + bindingRevision
                                    + "；无受影响活动 workload"));
                    return;
                }
                int created = 0;
                for (String workloadId : workloads) {
                    if (releasePort.desiredMatches(tenantId, workloadId, templateCode,
                            templateVersion, bindingRevision)) {
                        continue;
                    }
                    releasePort.createRegenerationDraft(workloadId, tenantId, productId,
                            templateCode, templateVersion, bindingRevision, reasonCode,
                            envelope.eventId(), confirmedBy);
                    created++;
                }
                auditPort.record(envelope.eventId(), tenantId, envelope.eventType(), ACTION_DRAFTS_CREATED,
                        bounded("productId=" + productId + ", bindingRevision=" + bindingRevision
                                + ", impacted=" + workloads.size() + ", draftsCreated=" + created
                                + ", reasonCode=" + reasonCode));
            } catch (RuntimeException e) {
                throw mapPortFailure(e);
            }
        }
    }
}
