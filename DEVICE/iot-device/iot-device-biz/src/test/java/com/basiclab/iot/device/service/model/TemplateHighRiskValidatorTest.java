package com.basiclab.iot.device.service.model;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * TD-005 §5.4：HIGH_RISK 服务必须四项策略齐全，任一缺失阻止发布。
 */
class TemplateHighRiskValidatorTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final TemplateHighRiskValidator validator = new TemplateHighRiskValidator();

    @Test
    void completeHighRiskPolicyIsAccepted() throws IOException {
        JsonNode template = objectMapper.readTree(
                "{\"services\":[{\"serviceCode\":\"remote-open\",\"riskLevel\":\"HIGH_RISK\","
                        + "\"idempotency\":\"REQUIRED\",\"safetyPolicy\":{\"approvalRequired\":true,"
                        + "\"secondConfirmationRequired\":true,\"deviceFeedbackRequired\":true}}]}");
        assertDoesNotThrow(() -> validator.requireCompleteHighRiskPolicy(template));
    }

    @Test
    void missingAnySingleFlagBlocksPublishing() throws IOException {
        String[] incompletePolicies = {
                "{\"approvalRequired\":false,\"secondConfirmationRequired\":true,\"deviceFeedbackRequired\":true}",
                "{\"approvalRequired\":true,\"secondConfirmationRequired\":false,\"deviceFeedbackRequired\":true}",
                "{\"approvalRequired\":true,\"secondConfirmationRequired\":true,\"deviceFeedbackRequired\":false}",
        };
        for (String policy : incompletePolicies) {
            JsonNode template = objectMapper.readTree(
                    "{\"services\":[{\"serviceCode\":\"remote-open\",\"riskLevel\":\"HIGH_RISK\","
                            + "\"idempotency\":\"REQUIRED\",\"safetyPolicy\":" + policy + "}]}");
            IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                    () -> validator.requireCompleteHighRiskPolicy(template));
            assertTrue(error.getMessage().startsWith("MODEL_HIGH_RISK_POLICY_INCOMPLETE"));
            assertTrue(error.getMessage().contains("remote-open"), "错误信息须指出服务 code");
        }
    }

    @Test
    void nonRequiredIdempotencyBlocksPublishing() throws IOException {
        JsonNode template = objectMapper.readTree(
                "{\"services\":[{\"serviceCode\":\"remote-open\",\"riskLevel\":\"HIGH_RISK\","
                        + "\"idempotency\":\"OPTIONAL\",\"safetyPolicy\":{\"approvalRequired\":true,"
                        + "\"secondConfirmationRequired\":true,\"deviceFeedbackRequired\":true}}]}");
        assertTrue(assertThrows(IllegalArgumentException.class,
                () -> validator.requireCompleteHighRiskPolicy(template))
                .getMessage().startsWith("MODEL_HIGH_RISK_POLICY_INCOMPLETE"));
    }

    @Test
    void missingSafetyPolicyEntirelyBlocksPublishing() throws IOException {
        JsonNode template = objectMapper.readTree(
                "{\"services\":[{\"serviceCode\":\"remote-open\",\"riskLevel\":\"HIGH_RISK\","
                        + "\"idempotency\":\"REQUIRED\"}]}");
        assertTrue(assertThrows(IllegalArgumentException.class,
                () -> validator.requireCompleteHighRiskPolicy(template))
                .getMessage().startsWith("MODEL_HIGH_RISK_POLICY_INCOMPLETE"));
    }

    @Test
    void lowAndMediumRiskServicesAreNotConstrained() throws IOException {
        JsonNode template = objectMapper.readTree(
                "{\"services\":[{\"serviceCode\":\"read-data\",\"riskLevel\":\"LOW\",\"idempotency\":\"NOT_SUPPORTED\"},"
                        + "{\"serviceCode\":\"reset-energy\",\"riskLevel\":\"MEDIUM\",\"idempotency\":\"OPTIONAL\"}]}");
        assertDoesNotThrow(() -> validator.requireCompleteHighRiskPolicy(template));
    }
}
