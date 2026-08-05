package com.basiclab.iot.common.capability;

import java.util.Optional;
import java.util.OptionalLong;

/** Single backend entry point for deployment capability checks. */
public interface CapabilityService {

    CapabilitySnapshot snapshot();

    default Optional<CapabilityDefinition> find(String capabilityCode) {
        return Optional.ofNullable(snapshot().getCapabilities().get(capabilityCode));
    }

    default boolean isEnabled(String capabilityCode) {
        return find(capabilityCode).map(CapabilityDefinition::isEnabled).orElse(false);
    }

    default OptionalLong quota(String capabilityCode, String quotaKey) {
        Long value = find(capabilityCode)
                .map(CapabilityDefinition::getQuota)
                .map(quota -> quota.get(quotaKey))
                .orElse(null);
        return value == null ? OptionalLong.empty() : OptionalLong.of(value);
    }
}
