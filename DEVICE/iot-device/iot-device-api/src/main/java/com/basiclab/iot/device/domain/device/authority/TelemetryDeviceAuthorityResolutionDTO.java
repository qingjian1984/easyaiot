package com.basiclab.iot.device.domain.device.authority;

/**
 * Minimal authority response.  It intentionally contains no Device entity or
 * candidate list, and only a resolved tenant is disclosed for RESOLVED.
 */
public record TelemetryDeviceAuthorityResolutionDTO(
        String productIdentification,
        String deviceIdentification,
        ResolutionStatus status,
        String tenantId
) {
}
