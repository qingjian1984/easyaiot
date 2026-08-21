package com.basiclab.iot.device;

import com.basiclab.iot.common.domain.R;
import com.basiclab.iot.device.domain.device.authority.TelemetryDeviceAuthorityResolutionDTO;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * ADR-018 protected, service-to-service authority lookup contract for the
 * center telemetry ingress.  This interface deliberately is not a Feign
 * client; each consumer must bind its own signed client configuration.
 */
public interface TelemetryDeviceAuthorityInternalApi {

    String BASE_PATH = "/internal-api/device/telemetry-authority";
    String RESOLVE_PATH = BASE_PATH + "/resolve";

    @GetMapping(RESOLVE_PATH)
    R<TelemetryDeviceAuthorityResolutionDTO> resolve(
            @RequestParam("productIdentification") String productIdentification,
            @RequestParam("deviceIdentification") String deviceIdentification);
}
