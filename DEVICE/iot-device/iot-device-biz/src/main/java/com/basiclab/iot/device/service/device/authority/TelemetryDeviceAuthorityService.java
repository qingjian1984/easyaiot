package com.basiclab.iot.device.service.device.authority;

import com.basiclab.iot.device.domain.device.authority.ResolutionStatus;
import com.basiclab.iot.device.domain.device.authority.TelemetryDeviceAuthorityResolutionDTO;
import com.basiclab.iot.device.dal.pgsql.device.DeviceMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Resolves the global registration fact used by center telemetry ingress.
 * This service never consults a current tenant context and never returns a
 * Device entity or duplicate candidates.
 */
@Service
public class TelemetryDeviceAuthorityService {

    private static final int BAD_REQUEST = 400;
    private static final int INTERNAL_ERROR = 500;
    private static final int SERVICE_UNAVAILABLE = 503;

    private final DeviceMapper deviceMapper;

    public TelemetryDeviceAuthorityService(DeviceMapper deviceMapper) {
        this.deviceMapper = deviceMapper;
    }

    @Transactional(readOnly = true)
    public TelemetryDeviceAuthorityResolutionDTO resolve(
            String productIdentification, String deviceIdentification) {
        validateIdentity("productIdentification", productIdentification,
                TelemetryDeviceAuthorityRouteValidator.MAX_PRODUCT_CODE_POINTS,
                "TELEMETRY_DEVICE_AUTHORITY_REQUEST_INVALID");
        validateIdentity("deviceIdentification", deviceIdentification,
                TelemetryDeviceAuthorityRouteValidator.MAX_DEVICE_CODE_POINTS,
                "TELEMETRY_DEVICE_AUTHORITY_REQUEST_INVALID");

        final List<TelemetryDeviceAuthorityCandidate> candidates;
        try {
            candidates = deviceMapper.selectTelemetryDeviceAuthorityCandidates(
                    productIdentification, deviceIdentification);
        } catch (RuntimeException exception) {
            throw new TelemetryDeviceAuthorityInternalException(
                    "TELEMETRY_DEVICE_AUTHORITY_UNAVAILABLE", SERVICE_UNAVAILABLE, exception);
        }
        if (candidates == null) {
            throw new TelemetryDeviceAuthorityInternalException(
                    "TELEMETRY_DEVICE_AUTHORITY_UNAVAILABLE", SERVICE_UNAVAILABLE);
        }
        if (candidates.isEmpty()) {
            return new TelemetryDeviceAuthorityResolutionDTO(
                    productIdentification, deviceIdentification, ResolutionStatus.NOT_FOUND, null);
        }
        if (candidates.size() > 1) {
            return new TelemetryDeviceAuthorityResolutionDTO(
                    productIdentification, deviceIdentification, ResolutionStatus.AMBIGUOUS, null);
        }

        TelemetryDeviceAuthorityCandidate candidate = candidates.get(0);
        if (candidate == null
                || !productIdentification.equals(candidate.getProductIdentification())
                || !deviceIdentification.equals(candidate.getDeviceIdentification())) {
            throw new TelemetryDeviceAuthorityInternalException(
                    "TELEMETRY_DEVICE_AUTHORITY_DATA_INVALID", INTERNAL_ERROR);
        }
        String tenantId = canonicalPositiveTenant(candidate.getTenantId());
        if (tenantId == null) {
            throw new TelemetryDeviceAuthorityInternalException(
                    "TELEMETRY_DEVICE_AUTHORITY_DATA_INVALID", INTERNAL_ERROR);
        }
        return new TelemetryDeviceAuthorityResolutionDTO(
                productIdentification, deviceIdentification, ResolutionStatus.RESOLVED, tenantId);
    }

    private static void validateIdentity(String name, String value, int maxCodePoints,
                                         String code) {
        if (!TelemetryDeviceAuthorityRouteValidator.isValid(value, maxCodePoints)) {
            throw new TelemetryDeviceAuthorityInternalException(code, BAD_REQUEST);
        }
    }

    private static String canonicalPositiveTenant(Long tenantId) {
        if (tenantId == null || tenantId <= 0) {
            return null;
        }
        return Long.toString(tenantId);
    }
}
