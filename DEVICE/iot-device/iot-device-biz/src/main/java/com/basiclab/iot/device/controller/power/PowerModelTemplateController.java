package com.basiclab.iot.device.controller.power;

import com.basiclab.iot.common.core.context.TenantContextHolder;
import com.basiclab.iot.common.utils.SecurityFrameworkUtils;
import com.basiclab.iot.device.controller.power.dto.PowerModelTemplateCreateRequest;
import com.basiclab.iot.device.controller.power.dto.PowerModelTemplateCreateResponse;
import com.basiclab.iot.device.controller.power.dto.PowerModelTemplateDraftResponse;
import com.basiclab.iot.device.controller.power.dto.PowerModelTemplateDraftWriteRequest;
import com.basiclab.iot.device.controller.power.dto.PowerModelTemplatePublishRequest;
import com.basiclab.iot.device.controller.power.dto.PowerModelTemplatePublishResponse;
import com.basiclab.iot.device.controller.power.dto.PowerModelTemplateValidationResponse;
import com.basiclab.iot.device.service.power.PowerModelTemplateDraftService;
import com.basiclab.iot.device.service.power.PowerModelTemplateIdentityService;
import com.basiclab.iot.device.service.power.PowerModelTemplatePublishService;
import com.basiclab.iot.device.service.power.PowerModelTemplateValidationService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** TD-005 §11.1：物模型模板 identity、草稿校验和发布写边界。 */
@Validated
@RestController
@ConditionalOnProperty(prefix = "easyaiot.power-model",
        name = "template-api-enabled", havingValue = "true")
@RequestMapping("/api/v1/power/model-templates")
public class PowerModelTemplateController {

    private final PowerModelTemplateIdentityService identityService;
    private final PowerModelTemplateDraftService draftService;
    private final PowerModelTemplateValidationService validationService;
    private final PowerModelTemplatePublishService publishService;

    public PowerModelTemplateController(PowerModelTemplateIdentityService identityService,
                                        PowerModelTemplateDraftService draftService,
                                        PowerModelTemplateValidationService validationService,
                                        PowerModelTemplatePublishService publishService) {
        this.identityService = identityService;
        this.draftService = draftService;
        this.validationService = validationService;
        this.publishService = publishService;
    }

    @PostMapping
    @PreAuthorize("@ss.hasPermission('power:model-template:edit')")
    public ResponseEntity<PowerModelTemplateCreateResponse> create(
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @RequestBody PowerModelTemplateCreateRequest request) {
        PowerModelTemplateCreateResponse response = identityService.create(tenantId(), request,
                actorId(), idempotencyKey);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PostMapping("/{code}/drafts")
    @PreAuthorize("@ss.hasPermission('power:model-template:edit')")
    public ResponseEntity<PowerModelTemplateDraftResponse> createDraft(
            @PathVariable("code") String templateCode,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @RequestBody PowerModelTemplateDraftWriteRequest request) {
        PowerModelTemplateDraftResponse response = draftService.create(tenantId(), templateCode,
                request, actorId(), idempotencyKey);
        return withEtag(HttpStatus.CREATED, response);
    }

    @PutMapping("/{code}/drafts/{draftId}")
    @PreAuthorize("@ss.hasPermission('power:model-template:edit')")
    public ResponseEntity<PowerModelTemplateDraftResponse> replaceDraft(
            @PathVariable("code") String templateCode,
            @PathVariable("draftId") long draftId,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @RequestHeader(HttpHeaders.IF_MATCH) String ifMatch,
            @RequestBody PowerModelTemplateDraftWriteRequest request) {
        PowerModelTemplateDraftResponse response = draftService.replace(tenantId(), templateCode,
                draftId, request, actorId(), idempotencyKey, ifMatch);
        return withEtag(HttpStatus.OK, response);
    }

    @PostMapping("/{code}/drafts/{draftId}:validate")
    @PreAuthorize("@ss.hasPermission('power:model-template:edit')")
    public PowerModelTemplateValidationResponse validate(
            @PathVariable("code") String templateCode,
            @PathVariable("draftId") long draftId) {
        return validationService.validate(tenantId(), templateCode, draftId);
    }

    @PostMapping("/{code}/drafts/{draftId}:publish")
    @PreAuthorize("@ss.hasPermission('power:model-template:publish')")
    public PowerModelTemplatePublishResponse publish(
            @PathVariable("code") String templateCode,
            @PathVariable("draftId") long draftId,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @RequestHeader(HttpHeaders.IF_MATCH) String ifMatch,
            @RequestHeader("X-Request-Id") String requestId,
            @RequestHeader(value = "X-Trace-Id", required = false) String traceId,
            @RequestBody PowerModelTemplatePublishRequest request) {
        return publishService.publish(tenantId(), templateCode, draftId, request, actorId(),
                idempotencyKey, requestId, traceId, ifMatch);
    }

    private static long tenantId() {
        return TenantContextHolder.getRequiredTenantId().longValue();
    }

    private static long actorId() {
        Long actorId = SecurityFrameworkUtils.getLoginUserId();
        if (actorId == null || actorId.longValue() <= 0) {
            throw new IllegalArgumentException("MODEL_AUTH_REQUIRED: 缺少已认证操作人");
        }
        return actorId.longValue();
    }

    private static ResponseEntity<PowerModelTemplateDraftResponse> withEtag(
            HttpStatus status, PowerModelTemplateDraftResponse response) {
        return ResponseEntity.status(status).eTag(response.getEtag()).body(response);
    }
}
