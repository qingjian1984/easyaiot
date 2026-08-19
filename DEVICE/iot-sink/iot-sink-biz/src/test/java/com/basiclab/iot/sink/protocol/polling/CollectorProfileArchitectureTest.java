package com.basiclab.iot.sink.protocol.polling;

import com.basiclab.iot.sink.config.IotGatewayConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CollectorProfileArchitectureTest {
    @Test
    void collectorMountAndProfileUseLocalConfigOnly() throws Exception {
        String workload = Files.readString(Path.of("..", "..", "..", "NODE", "collector_workload.py"));
        String yaml = Files.readString(Path.of("src", "main", "resources", "application-collector.yaml"));
        String rtu = Files.readString(Path.of("src", "main", "java", "com", "basiclab", "iot", "sink",
                "protocol", "modbus", "IotModbusRtuPollingProtocol.java"));
        String gateway = Files.readString(Path.of("src", "main", "java", "com", "basiclab", "iot", "sink",
                "config", "IotGatewayConfiguration.java"));
        assertTrue(workload.contains("EASYAIOT_COLLECTOR_WORKLOAD_ID"));
        assertTrue(workload.contains(":rw"));
        assertFalse(workload.contains("COLLECTOR_CONFIG_CONTAINER_DIR}:ro"));
        assertTrue(yaml.contains("spring:\n  main:\n    web-application-type: none\n  cloud:\n"),
                "collector YAML must select a non-web process before profile imports");
        assertTrue(yaml.contains("modbus-rtu:\n          # Center"));
        assertTrue(yaml.contains("bootstrap:\n      enabled: false"));
        assertTrue(yaml.contains("discovery:\n        enabled: false"));
        assertTrue(yaml.contains("config:\n        enabled: false"));
        assertTrue(gateway.contains("@Profile(\"!collector\")"));
        assertFalse(rtu.contains("DeviceMapper"));
        assertFalse(rtu.contains("IotDeviceMessageService"));
        assertFalse(rtu.contains("IotMessageBus"));
        assertFalse(rtu.contains("AbstractIndustrialPollingProtocol"));
        assertFalse(Files.exists(Path.of("src", "main", "java", "com", "basiclab", "iot", "sink", "protocol",
                "modbus", "ModbusValueCodec.java")));
    }

    @Test
    void centerProtocolConfigurationsAreProfileGatedAndCollectorFactoriesAreLocalOnly() {
        for (Class<?> nested : IotGatewayConfiguration.class.getDeclaredClasses()) {
            if (!nested.getSimpleName().endsWith("ProtocolConfiguration")) {
                continue;
            }
            assertNotNull(nested.getAnnotation(Configuration.class), nested.getName());
            Profile profile = nested.getAnnotation(Profile.class);
            assertNotNull(profile, nested.getName());
            assertTrue(Arrays.asList(profile.value()).contains("!collector"), nested.getName());
        }

        Set<String> forbidden = Set.of("DeviceMapper", "IotDeviceMessageService", "IotMessageBus");
        for (Method method : IotGatewayConfiguration.class.getDeclaredMethods()) {
            if (method.getAnnotation(Bean.class) == null || method.getAnnotation(Profile.class) == null
                    || !Arrays.asList(method.getAnnotation(Profile.class).value()).contains("collector")) {
                continue;
            }
            for (Class<?> parameter : method.getParameterTypes()) {
                assertFalse(forbidden.contains(parameter.getSimpleName()), method.toGenericString());
            }
        }
        long pollingProviderBeans = Arrays.stream(IotGatewayConfiguration.class.getDeclaredMethods())
                .filter(method -> method.getAnnotation(Bean.class) != null)
                .filter(method -> method.getAnnotation(Profile.class) != null)
                .filter(method -> Arrays.asList(method.getAnnotation(Profile.class).value()).contains("collector"))
                .filter(method -> com.basiclab.iot.sink.polling.PollingConfigProvider.class
                        .isAssignableFrom(method.getReturnType()))
                .count();
        assertEquals(1L, pollingProviderBeans, "collector must expose one PollingConfigProvider bean");
        Set<String> runtimeForbidden = Set.of("DeviceMapper", "IotDeviceMessageService", "IotMessageBus",
                "LocalFilePollingConfigProvider");
        for (Class<?> collectorType : Set.of(CollectorPollingRuntime.class,
                com.basiclab.iot.sink.protocol.modbus.IotModbusRtuPollingProtocol.class)) {
            for (Field field : collectorType.getDeclaredFields()) {
                assertFalse(runtimeForbidden.contains(field.getType().getSimpleName()), field.toGenericString());
            }
            for (Constructor<?> constructor : collectorType.getDeclaredConstructors()) {
                for (Class<?> parameter : constructor.getParameterTypes()) {
                    assertFalse(runtimeForbidden.contains(parameter.getSimpleName()), constructor.toGenericString());
                }
            }
        }
    }
}
