package com.basiclab.iot.device.config;

import com.basiclab.iot.common.capability.CapabilityService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Objects;

/**
 * 电力物模型写链启动门禁：模板/绑定 API 配置组合不完整、档位不支持或 capability 关闭时终止启动。
 */
@Component
public final class PowerModelActivationGuard {

    static final String CAPABILITY_CODE = "power.device.model";

    public PowerModelActivationGuard(
            CapabilityService capabilityService,
            @Value("${easyaiot.power-model.template-api-enabled:false}")
            boolean templateApiEnabled,
            @Value("${easyaiot.power-model.binding-apply-api-enabled:false}")
            boolean bindingApiEnabled,
            @Value("${easyaiot.power-model.collector-release-port-enabled:false}")
            boolean collectorReleasePortEnabled,
            @Value("${power.model.events.enabled:false}") boolean eventsEnabled,
            PowerModelIdempotencySecretProvider secretProvider) {
        verify(capabilityService, templateApiEnabled, bindingApiEnabled,
                collectorReleasePortEnabled, eventsEnabled,
                Objects.requireNonNull(secretProvider, "secretProvider").getSecret());
    }

    static void verify(CapabilityService capabilityService, boolean templateApiEnabled,
                       boolean bindingApiEnabled,
                       boolean collectorReleasePortEnabled, boolean eventsEnabled,
                       byte[] idempotencySecret) {
        CapabilityService required = Objects.requireNonNull(capabilityService, "capabilityService");
        if (!templateApiEnabled && !bindingApiEnabled
                && !collectorReleasePortEnabled && !eventsEnabled) {
            return;
        }
        String profile = required.snapshot() == null ? null : required.snapshot().getProfile();
        if (!"standard".equals(profile) && !"full".equals(profile)) {
            throw new IllegalStateException("POWER_MODEL_PROFILE_NOT_SUPPORTED: " + profile);
        }
        if (!required.isEnabled(CAPABILITY_CODE)) {
            throw new IllegalStateException("POWER_MODEL_CAPABILITY_DISABLED: " + CAPABILITY_CODE);
        }
        if (templateApiEnabled && (!collectorReleasePortEnabled || !eventsEnabled)) {
            throw new IllegalStateException("POWER_MODEL_ACTIVATION_INCOMPLETE: template API"
                    + " requires collector release port and event pipeline");
        }
        if (bindingApiEnabled && (!templateApiEnabled
                || !collectorReleasePortEnabled || !eventsEnabled)) {
            throw new IllegalStateException("POWER_MODEL_ACTIVATION_INCOMPLETE: binding API requires"
                    + " template API, collector release port and event pipeline");
        }
        if (eventsEnabled && !collectorReleasePortEnabled) {
            throw new IllegalStateException("POWER_MODEL_ACTIVATION_INCOMPLETE: event pipeline"
                    + " requires collector release port");
        }
        if ((templateApiEnabled || bindingApiEnabled) && (idempotencySecret == null
                || idempotencySecret.length < 32)) {
            throw new IllegalStateException("POWER_MODEL_IDEMPOTENCY_SECRET_INVALID:"
                    + " write API requires at least 32 UTF-8 bytes");
        }
    }
}
