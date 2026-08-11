package com.basiclab.iot.device.controller.power;

import com.basiclab.iot.common.core.context.TenantContextHolder;
import com.basiclab.iot.common.service.SecurityFrameworkService;
import com.basiclab.iot.device.controller.power.dto.PowerModelTemplateValidationResponse;
import com.basiclab.iot.device.service.power.PowerModelTemplateDraftService;
import com.basiclab.iot.device.service.power.PowerModelTemplateIdentityService;
import com.basiclab.iot.device.service.power.PowerModelTemplatePublishService;
import com.basiclab.iot.device.service.power.PowerModelTemplateValidationService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.config.annotation.method.configuration.EnableGlobalMethodSecurity;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.PostMapping;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** TD-005 §11/§16：默认关闭、权限和字面量冒号路由合同。 */
class PowerModelTemplateControllerContractTest {

    private final PowerModelTemplateIdentityService identityService =
            mock(PowerModelTemplateIdentityService.class);
    private final PowerModelTemplateDraftService draftService =
            mock(PowerModelTemplateDraftService.class);
    private final PowerModelTemplateValidationService validationService =
            mock(PowerModelTemplateValidationService.class);
    private final PowerModelTemplatePublishService publishService =
            mock(PowerModelTemplatePublishService.class);

    @AfterEach
    void clearTenant() {
        TenantContextHolder.clear();
        SecurityContextHolder.clearContext();
    }

    @Test
    void apiAndAdviceAreAbsentUnlessExplicitlyEnabled() {
        ApplicationContextRunner runner = new ApplicationContextRunner()
                .withBean(PowerModelTemplateIdentityService.class, () -> identityService)
                .withBean(PowerModelTemplateDraftService.class, () -> draftService)
                .withBean(PowerModelTemplateValidationService.class, () -> validationService)
                .withBean(PowerModelTemplatePublishService.class, () -> publishService)
                .withUserConfiguration(PowerModelTemplateController.class,
                        PowerModelTemplateExceptionHandler.class);

        runner.run(context -> {
            assertFalse(context.containsBean("powerModelTemplateController"));
            assertFalse(context.containsBean("powerModelTemplateExceptionHandler"));
        });
        runner.withPropertyValues("easyaiot.power-model.template-api-enabled=true")
                .run(context -> {
                    assertNotNull(context.getBean(PowerModelTemplateController.class));
                    assertNotNull(context.getBean(PowerModelTemplateExceptionHandler.class));
                });
    }

    @Test
    void methodSecurityActuallyRejectsMissingEditPermission() {
        SecurityFrameworkService security = mock(SecurityFrameworkService.class);
        ApplicationContextRunner runner = new ApplicationContextRunner()
                .withBean(PowerModelTemplateIdentityService.class, () -> identityService)
                .withBean(PowerModelTemplateDraftService.class, () -> draftService)
                .withBean(PowerModelTemplateValidationService.class, () -> validationService)
                .withBean(PowerModelTemplatePublishService.class, () -> publishService)
                .withBean("ss", SecurityFrameworkService.class, () -> security)
                .withPropertyValues("easyaiot.power-model.template-api-enabled=true")
                .withUserConfiguration(MethodSecurityConfiguration.class,
                        PowerModelTemplateController.class);

        runner.run(context -> {
            SecurityContextHolder.getContext().setAuthentication(
                    new UsernamePasswordAuthenticationToken("contract-user", "n/a",
                            java.util.Collections.emptyList()));
            PowerModelTemplateController controller =
                    context.getBean(PowerModelTemplateController.class);
            assertThrows(org.springframework.security.access.AccessDeniedException.class,
                    () -> controller.validate("meter-standard", 42L));
        });
    }

    @Test
    void mappingsUseFrozenPermissionsAndLiteralColonActions() throws Exception {
        assertGate(PowerModelTemplateController.class);
        assertAction("validate", "/{code}/drafts/{draftId}:validate",
                "@ss.hasPermission('power:model-template:edit')", String.class, long.class);
        assertAction("publish", "/{code}/drafts/{draftId}:publish",
                "@ss.hasPermission('power:model-template:publish')", String.class, long.class,
                String.class, String.class, String.class, String.class,
                com.basiclab.iot.device.controller.power.dto.PowerModelTemplatePublishRequest.class);
    }

    @Test
    void mockMvcAcceptsLiteralColonAndRejectsSlashAlias() throws Exception {
        TenantContextHolder.setTenantId(920013001L);
        PowerModelTemplateValidationResponse response = new PowerModelTemplateValidationResponse();
        response.setDraftId("42");
        response.setTemplateCode("meter-standard");
        response.setValid(true);
        when(validationService.validate(920013001L, "meter-standard", 42L)).thenReturn(response);
        MockMvc mvc = mvc();

        mvc.perform(post("/api/v1/power/model-templates/meter-standard/drafts/42:validate"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.draftId").value("42"))
                .andExpect(jsonPath("$.valid").value(true));
        mvc.perform(post("/api/v1/power/model-templates/meter-standard/drafts/42/validate"))
                .andExpect(status().isNotFound());
    }

    @Test
    void businessFailuresUseFrozenEnvelopeAndStatus() throws Exception {
        TenantContextHolder.setTenantId(920013001L);
        mvc().perform(post("/api/v1/power/model-templates")
                        .header("Idempotency-Key", "contract-key")
                        .header("X-Trace-Id", "trace-contract")
                        .contentType("application/json")
                        .content("{\"templateCode\":\"meter-standard\",\"templateName\":\"Meter\","
                                + "\"deviceType\":\"METER\",\"templateKind\":\"STANDARD\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("MODEL_AUTH_REQUIRED"))
                .andExpect(jsonPath("$.errors").isArray())
                .andExpect(jsonPath("$.traceId").value("trace-contract"))
                .andExpect(jsonPath("$.timestamp").isNotEmpty())
                .andExpect(jsonPath("$.retryable").value(false));
    }

    @Test
    void conflictPreconditionAndRetryableStatusMatrixIsStable() {
        PowerModelTemplateExceptionHandler handler = new PowerModelTemplateExceptionHandler();
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-Trace-Id", "trace-matrix");

        ResponseEntity<?> precondition = handler.business(new IllegalArgumentException(
                "MODEL_PRECONDITION_FAILED: stale revision"), request);
        assertEquals(HttpStatus.PRECONDITION_FAILED, precondition.getStatusCode());

        ResponseEntity<?> reused = handler.business(new IllegalArgumentException(
                "IDEMPOTENCY_KEY_REUSED: different request"), request);
        assertEquals(HttpStatus.CONFLICT, reused.getStatusCode());

        ResponseEntity<com.basiclab.iot.device.controller.power.dto.PowerModelTemplateErrorResponse>
                lockTimeout = handler.business(new IllegalArgumentException(
                "MODEL_TEMPLATE_PUBLISH_LOCK_TIMEOUT: retry later"), request);
        assertEquals(HttpStatus.CONFLICT, lockTimeout.getStatusCode());
        assertTrue(lockTimeout.getBody().isRetryable());
    }

    @Test
    void missingHeaderAndMalformedJsonUseTheSameEnvelope() throws Exception {
        TenantContextHolder.setTenantId(920013001L);
        MockMvc mvc = mvc();
        mvc.perform(post("/api/v1/power/model-templates")
                        .contentType("application/json").content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("MODEL_TEMPLATE_REQUEST_INVALID"))
                .andExpect(jsonPath("$.errors").isArray());
        mvc.perform(post("/api/v1/power/model-templates")
                        .header("Idempotency-Key", "malformed-json")
                        .contentType("application/json").content("{"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("MODEL_TEMPLATE_REQUEST_INVALID"))
                .andExpect(jsonPath("$.retryable").value(false));
    }

    private MockMvc mvc() {
        return MockMvcBuilders.standaloneSetup(new PowerModelTemplateController(identityService,
                        draftService, validationService, publishService))
                .setControllerAdvice(new PowerModelTemplateExceptionHandler()).build();
    }

    private static void assertGate(Class<?> type) {
        ConditionalOnProperty gate = type.getAnnotation(ConditionalOnProperty.class);
        assertNotNull(gate);
        assertEquals("easyaiot.power-model", gate.prefix());
        assertEquals("template-api-enabled", gate.name()[0]);
        assertEquals("true", gate.havingValue());
        assertFalse(gate.matchIfMissing());
    }

    private static void assertAction(String methodName, String path, String permission,
                                     Class<?>... parameterTypes) throws Exception {
        Method method = PowerModelTemplateController.class.getMethod(methodName, parameterTypes);
        PostMapping mapping = method.getAnnotation(PostMapping.class);
        assertNotNull(mapping);
        assertEquals(path, mapping.value()[0]);
        PreAuthorize authorize = method.getAnnotation(PreAuthorize.class);
        assertNotNull(authorize);
        assertEquals(permission, authorize.value());
    }

    @Configuration(proxyBeanMethods = false)
    @EnableGlobalMethodSecurity(prePostEnabled = true)
    static class MethodSecurityConfiguration {
    }
}
