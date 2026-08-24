package com.basiclab.iot.sink.telemetry.inbox.route;

import com.basiclab.iot.common.security.internal.InternalServiceAuthFeignInterceptor;
import com.basiclab.iot.common.security.internal.InternalServiceAuthSigner;
import com.basiclab.iot.common.security.internal.InternalServiceKeyProvider;
import com.basiclab.iot.common.security.internal.EnvironmentInternalServiceKeyProvider;
import com.basiclab.iot.common.security.internal.InternalServiceAuthException;
import com.basiclab.iot.sink.telemetry.inbox.mqtt.TelemetryMqttProperties;
import feign.Request;
import feign.RequestInterceptor;
import feign.Retryer;
import org.springframework.context.annotation.Bean;

/**
 * Feign child-context configuration for this one authority client.  It is not
 * a global interceptor, has no fallback, and refuses to start without an
 * externally supplied ADR-018 key.
 */
// 注：不加 @ConditionalOnProperty——Feign 子上下文（child context）评估条件时读不到
// 父上下文的 easyaiot.telemetry.mqtt.enabled，会导致 signer interceptor 被跳过、
// 请求裸发被 verifier 拒为 SERVICE_AUTH_MISSING（2026-08-24 实测）。该 configuration
// 仅被 TelemetryDeviceAuthorityClient 这一个 @FeignClient 显式引用，装配已受
// client 消费方（CenterTelemetryIngressHandler 链）控制，无需独立开关。
public class TelemetryDeviceAuthorityFeignConfiguration {

    @Bean
    public RequestInterceptor telemetryDeviceAuthoritySigner(
            org.springframework.core.env.Environment environment,
            TelemetryMqttProperties properties) {
        String keyId = properties.getAuthorityKeyId();
        if (keyId == null || keyId.isBlank()) {
            throw new InternalServiceAuthException("SERVICE_AUTH_KEY_UNKNOWN");
        }
        // 直接从 Environment 构造 provider：Feign 子上下文/装配时序下注入共享
        // InternalServiceKeyProvider Bean 不可靠（2026-08-24 实测主上下文
        // NoSuchBean；子上下文条件评估又拿不到 interceptor）。引用表与主上下文
        // 同源（easyaiot.security.internal.key-references）。
        java.util.Map<String, String> references = new java.util.HashMap<>();
        String reference = environment.getProperty(
                "easyaiot.security.internal.key-references.iot-sink:" + keyId);
        if (reference == null || reference.isBlank()) {
            reference = environment.getProperty(
                    "easyaiot.security.internal.key-references.iot-sink" + keyId);
        }
        if (reference == null || reference.isBlank()) {
            throw new InternalServiceAuthException("SERVICE_AUTH_KEY_UNKNOWN");
        }
        references.put("iot-sink:" + keyId, reference);
        InternalServiceKeyProvider keyProvider = new EnvironmentInternalServiceKeyProvider(
                environment, references);
        if (keyProvider.findKey("iot-sink", keyId).isEmpty()) {
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
