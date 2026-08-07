package com.basiclab.iot.device.service.model;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * TD-005 §5.4：HIGH_RISK 服务必须四项策略齐全（approvalRequired、
 * secondConfirmationRequired、deviceFeedbackRequired、idempotency=REQUIRED），
 * 任一缺失均阻止模板发布。模板标记不代替实际执行权限、审批和现场反馈校验。
 * Java 8 兼容。
 */
public final class TemplateHighRiskValidator {

    private static final String[] REQUIRED_FLAGS = {
            "approvalRequired", "secondConfirmationRequired", "deviceFeedbackRequired"
    };

    public void requireCompleteHighRiskPolicy(JsonNode template) {
        JsonNode services = template.path("services");
        if (!services.isArray()) {
            return;
        }
        for (JsonNode service : services) {
            if (!"HIGH_RISK".equals(service.path("riskLevel").asText())) {
                continue;
            }
            StringBuilder missing = new StringBuilder();
            JsonNode safetyPolicy = service.path("safetyPolicy");
            for (String flag : REQUIRED_FLAGS) {
                if (!safetyPolicy.path(flag).asBoolean(false)) {
                    appendMissing(missing, flag);
                }
            }
            if (!"REQUIRED".equals(service.path("idempotency").asText())) {
                appendMissing(missing, "idempotency=REQUIRED");
            }
            if (missing.length() > 0) {
                throw new IllegalArgumentException(
                        "MODEL_HIGH_RISK_POLICY_INCOMPLETE: 服务 "
                                + service.path("serviceCode").asText() + " 缺少 " + missing);
            }
        }
    }

    private static void appendMissing(StringBuilder missing, String item) {
        if (missing.length() > 0) {
            missing.append(", ");
        }
        missing.append(item);
    }
}
