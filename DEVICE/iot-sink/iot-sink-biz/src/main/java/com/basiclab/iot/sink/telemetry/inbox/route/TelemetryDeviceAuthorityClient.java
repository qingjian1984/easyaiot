package com.basiclab.iot.sink.telemetry.inbox.route;

import com.basiclab.iot.common.constant.ServiceNameConstants;
import com.basiclab.iot.device.TelemetryDeviceAuthorityInternalApi;
import org.springframework.cloud.openfeign.FeignClient;

/** Consumer-local Feign binding; the API contract itself is not a Feign client. */
@FeignClient(
        contextId = "telemetryDeviceAuthorityClient",
        value = ServiceNameConstants.IOT_DEVICE,
        configuration = TelemetryDeviceAuthorityFeignConfiguration.class)
public interface TelemetryDeviceAuthorityClient extends TelemetryDeviceAuthorityInternalApi {
}
