package com.basiclab.iot.sink.telemetry.inbox.route;

import com.basiclab.iot.common.domain.R;
import com.basiclab.iot.device.domain.device.authority.ResolutionStatus;
import com.basiclab.iot.device.domain.device.authority.TelemetryDeviceAuthorityResolutionDTO;
import com.basiclab.iot.sink.telemetry.outbox.TelemetryRoute;

/** Defensive adapter from the signed HTTP contract to the center port. */
public final class TelemetryDeviceAuthorityClientAdapter
        implements TelemetryDeviceAuthorityPort {

    private final TelemetryDeviceAuthorityClient client;

    public TelemetryDeviceAuthorityClientAdapter(TelemetryDeviceAuthorityClient client) {
        this.client = client;
    }

    @Override
    public Resolution resolve(TelemetryRoute route) {
        if (route == null) {
            return new Resolution.Unavailable();
        }
        try {
            R<TelemetryDeviceAuthorityResolutionDTO> response = client.resolve(
                    route.productIdentification(), route.deviceIdentification());
            if (response == null || !response.isSuccess() || response.getData() == null) {
                return new Resolution.Unavailable();
            }
            TelemetryDeviceAuthorityResolutionDTO dto = response.getData();
            if (!route.productIdentification().equals(dto.productIdentification())
                    || !route.deviceIdentification().equals(dto.deviceIdentification())
                    || dto.status() == null) {
                return new Resolution.Unavailable();
            }
            if (dto.status() == ResolutionStatus.NOT_FOUND) {
                return dto.tenantId() == null
                        ? new Resolution.NotFound() : new Resolution.Unavailable();
            }
            if (dto.status() == ResolutionStatus.AMBIGUOUS) {
                return dto.tenantId() == null
                        ? new Resolution.Ambiguous() : new Resolution.Unavailable();
            }
            if (dto.status() == ResolutionStatus.RESOLVED
                    && isCanonicalPositiveTenant(dto.tenantId())) {
                return new Resolution.Resolved(dto.tenantId());
            }
            return new Resolution.Unavailable();
        } catch (RuntimeException exception) {
            return new Resolution.Unavailable();
        }
    }

    private static boolean isCanonicalPositiveTenant(String value) {
        if (value == null || !value.matches("[1-9][0-9]*")) {
            return false;
        }
        try {
            long parsed = Long.parseLong(value);
            return parsed > 0 && Long.toString(parsed).equals(value);
        } catch (NumberFormatException exception) {
            return false;
        }
    }
}
