package com.basiclab.iot.sink.telemetry.inbox;

import com.basiclab.iot.common.security.internal.InternalServiceAuthFeignInterceptor;
import com.basiclab.iot.common.security.internal.InternalServiceAuthException;
import com.basiclab.iot.common.constant.ServiceNameConstants;
import com.basiclab.iot.sink.telemetry.inbox.mqtt.CenterMqttInboxSubscriber;
import com.basiclab.iot.sink.telemetry.inbox.mqtt.TelemetryMqttProperties;
import com.basiclab.iot.sink.telemetry.inbox.route.CenterTelemetryIngressHandler;
import com.basiclab.iot.sink.telemetry.inbox.route.TelemetryDeviceAuthorityClient;
import com.basiclab.iot.sink.telemetry.inbox.route.TelemetryDeviceAuthorityFeignConfiguration;
import com.basiclab.iot.sink.telemetry.inbox.route.TelemetryUpstreamTopicParser;
import feign.Request;
import feign.RequestInterceptor;
import feign.Retryer;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.StandardEnvironment;

import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TelemetryInboxAutoConfigurationTest {

    @Test
    void centerAssemblyIsExplicitlyGatedAndUsesThePureHandler() throws Exception {
        ConditionalOnProperty gate =
                TelemetryInboxAutoConfiguration.class.getAnnotation(ConditionalOnProperty.class);
        assertArrayEquals(new String[]{"easyaiot.telemetry.inbox.enabled"}, gate.name());
        assertEquals("true", gate.havingValue());
        assertEquals(false, gate.matchIfMissing());

        Method subscriberFactory = TelemetryInboxAutoConfiguration.class.getMethod(
                "centerMqttInboxSubscriber", CenterTelemetryIngressHandler.class,
                TelemetryMqttProperties.class);
        assertEquals(CenterMqttInboxSubscriber.class, subscriberFactory.getReturnType());
        assertTrue(java.util.Arrays.asList(subscriberFactory.getParameterTypes())
                .contains(CenterTelemetryIngressHandler.class));
        assertTrue(java.util.Arrays.stream(subscriberFactory.getParameterTypes())
                .noneMatch(type -> type.getName().contains("CenterMqttAckPublisher")));
    }

    @Test
    void mqttPropertiesFailClosedForOldOrBroadFiltersAndMissingAuthorityKey() {
        TelemetryMqttProperties properties = new TelemetryMqttProperties();
        properties.setClientId("center-inbox-1");
        properties.setUsername("lc02-center-inbox");
        properties.setPassword("fixture-only");
        properties.setAuthorityKeyId("key-1");
        properties.validateForEnabledSubscriber();
        assertEquals("$share/easyaiot-center-inbox-v1//iot/+/+/property/upstream/report",
                properties.getTopicFilter());

        for (String invalid : new String[]{"/telemetry/#", "/iot/#",
                "/iot/+/+/property/+/report", "#", "/iot/+/+/property/upstream/report",
                "$share/other//iot/+/+/property/upstream/report",
                "$share/easyaiot-center-inbox-v1//iot/+/+/property/upstream/report/"}) {
            properties.setTopicFilter(invalid);
            assertThrows(IllegalStateException.class, properties::validateForEnabledSubscriber);
        }

        properties.setTopicFilter(TelemetryUpstreamTopicParser.sharedSubscriptionFilter());
        properties.setAuthorityKeyId("");
        assertThrows(IllegalStateException.class, properties::validateForEnabledSubscriber);
    }

    @Test
    void mqttPropertiesRejectMissingOrUnsafeBrokerIdentityBeforeNetworking() {
        TelemetryMqttProperties properties = validMqttProperties();
        for (String invalid : new String[]{null, "", " ", "center/one", "center+", "center#",
                "center\u0000", "center\n"}) {
            properties.setClientId(invalid);
            IllegalStateException failure = assertThrows(IllegalStateException.class,
                    properties::validateForEnabledSubscriber);
            assertEquals("TELEMETRY_MQTT_CLIENT_ID_INVALID", failure.getMessage());
        }

        properties = validMqttProperties();
        for (String invalid : new String[]{null, "", " ", "center/one", "center+", "center#",
                "center\u0000", "center\r"}) {
            properties.setUsername(invalid);
            IllegalStateException failure = assertThrows(IllegalStateException.class,
                    properties::validateForEnabledSubscriber);
            assertEquals("TELEMETRY_MQTT_CREDENTIALS_MISSING", failure.getMessage());
        }

        properties = validMqttProperties();
        properties.setPassword(" ");
        IllegalStateException failure = assertThrows(IllegalStateException.class,
                properties::validateForEnabledSubscriber);
        assertEquals("TELEMETRY_MQTT_CREDENTIALS_MISSING", failure.getMessage());
    }

    @Test
    void feignBindingIsConsumerLocalSignedNoFallbackNoRetryAndBounded() {
        FeignClient annotation = TelemetryDeviceAuthorityClient.class.getAnnotation(FeignClient.class);
        assertEquals("telemetryDeviceAuthorityClient", annotation.contextId());
        assertEquals(ServiceNameConstants.IOT_DEVICE, annotation.value());
        assertArrayEquals(new Class<?>[]{TelemetryDeviceAuthorityFeignConfiguration.class},
                annotation.configuration());
        assertEquals(void.class, annotation.fallback());
        assertEquals(void.class, annotation.fallbackFactory());

        TelemetryDeviceAuthorityFeignConfiguration configuration =
                new TelemetryDeviceAuthorityFeignConfiguration();
        TelemetryMqttProperties properties = new TelemetryMqttProperties();
        properties.setAuthorityKeyId("key-1");
        StandardEnvironment environment = new StandardEnvironment();
        environment.getPropertySources().addFirst(new MapPropertySource("test", Map.of(
                "easyaiot.security.internal.key-references.iot-sink:key-1",
                "test.authority.key",
                "test.authority.key", "k".repeat(32))));
        RequestInterceptor interceptor =
                configuration.telemetryDeviceAuthoritySigner(environment, properties);
        assertInstanceOf(InternalServiceAuthFeignInterceptor.class, interceptor);

        Request.Options options = configuration.telemetryDeviceAuthorityRequestOptions();
        assertEquals(500, options.connectTimeoutMillis());
        assertEquals(1000, options.readTimeoutMillis());
        assertSameRetryerNeverRetries(configuration.telemetryDeviceAuthorityRetryer());

        properties.setAuthorityKeyId("missing");
        assertThrows(InternalServiceAuthException.class,
                () -> configuration.telemetryDeviceAuthoritySigner(environment, properties));
    }

    @Test
    void shippedConfigurationKeepsMqttDisabledByDefaultAndUsesCanonicalFilter() throws IOException {
        String standard = resourceText("application-standard.yaml");
        String application = resourceText("application.yaml");
        assertTrue(standard.contains("EASYAIOT_TELEMETRY_MQTT_ENABLED:false"));
        assertTrue(application.contains(
                "$share/easyaiot-center-inbox-v1//iot/+/+/property/upstream/report"));
        assertTrue(!application.contains("/telemetry/#"));
    }

    private static TelemetryMqttProperties validMqttProperties() {
        TelemetryMqttProperties properties = new TelemetryMqttProperties();
        properties.setClientId("center-inbox-1");
        properties.setUsername("lc02-center-inbox");
        properties.setPassword("fixture-only");
        properties.setAuthorityKeyId("key-1");
        return properties;
    }

    private static void assertSameRetryerNeverRetries(Retryer retryer) {
        assertEquals(Retryer.NEVER_RETRY, retryer);
    }

    private static String resourceText(String name) throws IOException {
        try (InputStream input = TelemetryInboxAutoConfigurationTest.class
                .getClassLoader().getResourceAsStream(name)) {
            assertTrue(input != null, "missing resource " + name);
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
