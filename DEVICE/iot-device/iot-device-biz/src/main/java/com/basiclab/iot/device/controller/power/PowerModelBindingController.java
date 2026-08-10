package com.basiclab.iot.device.controller.power;

import com.basiclab.iot.common.core.context.TenantContextHolder;
import com.basiclab.iot.common.domain.R;
import com.basiclab.iot.common.utils.SecurityFrameworkUtils;
import com.basiclab.iot.device.controller.power.dto.PowerModelBindingApplyRequest;
import com.basiclab.iot.device.controller.power.dto.PowerModelBindingApplyResponse;
import com.basiclab.iot.device.service.power.PowerModelBindingApplyService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** TD-005 §11.4 产品模型绑定 action API；权限与 capability 双重 fail-closed。 */
@Validated
@RestController
@ConditionalOnProperty(prefix = "easyaiot.power-model",
        name = "binding-apply-api-enabled", havingValue = "true")
@RequestMapping("/api/v1/products")
public class PowerModelBindingController {

    private final PowerModelBindingApplyService service;

    public PowerModelBindingController(PowerModelBindingApplyService service) {
        this.service = service;
    }

    @PostMapping("/{productIdentification}/model-binding:apply")
    @PreAuthorize("@ss.hasPermission('power:model-template:publish')")
    public R<PowerModelBindingApplyResponse> apply(
            @PathVariable("productIdentification") String productIdentification,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @RequestHeader("X-Request-Id") String requestId,
            @RequestHeader(value = "X-Trace-Id", required = false) String traceId,
            @RequestBody PowerModelBindingApplyRequest request) {
        Long actorId = SecurityFrameworkUtils.getLoginUserId();
        if (actorId == null) {
            throw new IllegalArgumentException("MODEL_AUTH_REQUIRED: 缺少已认证操作者");
        }
        return R.ok(service.apply(TenantContextHolder.getRequiredTenantId(), productIdentification,
                request, actorId.longValue(), idempotencyKey, requestId, traceId));
    }
}
