package com.basiclab.iot.sink.telemetry.inbox.route;

import com.basiclab.iot.common.security.internal.InternalServiceAuthFeignInterceptor;
import com.basiclab.iot.common.security.internal.InternalServiceAuthSigner;
import com.basiclab.iot.common.security.internal.InternalServiceKeyProvider;
import com.basiclab.iot.common.security.internal.InternalServiceAuthException;
import com.basiclab.iot.sink.telemetry.inbox.mqtt.TelemetryMqttProperties;
import feign.Request;
import feign.RequestInterceptor;
import feign.Retryer;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;

/**
 * Feign child-context configuration for this one authority client.  It is not
 * a global interceptor, has no fallback, and refuses to start without an
 * externally supplied ADR-018 key.
 */
@ConditionalOnProperty(name = "easyaiot.telemetry.mqtt.enabled", havingValue = "true")
public class TelemetryDeviceAuthorityFeignConfiguration {

    @Bean
    public RequestInterceptor telemetryDeviceAuthoritySigner(
            InternalServiceKeyProvider keyProvider, TelemetryMqttProperties properties) {
        String keyId = properties.getAuthorityKeyId();
        if (keyId == null || keyId.isBlank()
                || keyProvider.findKey("iot-sink", keyId).isEmpty()) {
            throw new InternalServiceAuthException("SERVICE_AUTH_KEY_UNKNOWN");
        }
        InternalServiceAuthSigner signer = new InternalServiceAuthSigner(
                keyProvider, "iot-sink", keyId);
        return new InternalServiceAuthFeignInterceptor(signer);
    }

    @Bean
    public Request.Options telemetryDeviceAuthorityRequestOptions() {
        return new Request.Options(500, 1000);
    }

    @Bean
    public Retryer telemetryDeviceAuthorityRetryer() {
        return Retryer.NEVER_RETRY;
    }
}
