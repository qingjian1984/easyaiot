package com.basiclab.iot.sink.protocol.polling;

import com.basiclab.iot.sink.SinkServerApplication;
import com.basiclab.iot.sink.dal.mapper.DeviceMapper;
import com.basiclab.iot.sink.messagebus.core.IotMessageBus;
import com.basiclab.iot.sink.telemetry.outbox.TelemetryOutboxPort;
import com.basiclab.iot.common.service.RedisService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.StandardEnvironment;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Real collector bootstrap test.  This intentionally starts a Spring context
 * rather than inspecting the application source or configuration annotations.
 */
class CollectorSpringContextTest {

    @Test
    void collectorBootstrapsOnlyTheLocalClosedGraph(@TempDir Path temporaryDirectory) throws Exception {
        Path configDirectory = temporaryDirectory.resolve("config");
        Path outboxDirectory = temporaryDirectory.resolve("outbox");
        Files.createDirectories(outboxDirectory);

        String[] applicationArgs = {"--spring.profiles.active=collector"};
        SpringApplication application = SinkServerApplication.createApplication(applicationArgs);
        application.setRegisterShutdownHook(false);
        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("spring.application.name", "sink-server-collector-test");
        properties.put("easyaiot.collector.config-directory", configDirectory.toString());
        properties.put("easyaiot.collector.workload-id", "collector-context-test");
        properties.put("easyaiot.collector.mqtt.enabled", "false");
        properties.put("easyaiot.outbox.enabled", "true");
        properties.put("easyaiot.outbox.volume-path", outboxDirectory.toString());
        ConfigurableEnvironment environment = new StandardEnvironment();
        environment.getPropertySources().addFirst(new MapPropertySource("collector-context-test", properties));
        application.setEnvironment(environment);

        try (ConfigurableApplicationContext context = application.run(applicationArgs)) {
            assertEquals("none", context.getEnvironment().getProperty("spring.main.web-application-type"));
            assertEquals("false", context.getEnvironment().getProperty("spring.cloud.bootstrap.enabled"));
            assertEquals("false", context.getEnvironment().getProperty("spring.cloud.nacos.config.enabled"));
            assertEquals("false", context.getEnvironment().getProperty("spring.cloud.nacos.discovery.enabled"));

            assertNotNull(context.getBean(LocalFilePollingConfigProvider.class));
            assertNotNull(context.getBean(CollectorPollingRuntime.class));
            assertNotNull(context.getBean(TelemetryOutboxPort.class));

            assertTrue(context.getBeansOfType(DeviceMapper.class).isEmpty(),
                    "DeviceMapper must not be assembled in collector profile");
            assertTrue(context.getBeansOfType(IotMessageBus.class).isEmpty(),
                    "center message bus must not be assembled in collector profile");
            assertTrue(context.getBeansOfType(RedisService.class).isEmpty(),
                    "Redis must not be assembled in collector profile");

            assertNoCenterBeanPackages(context);
            assertNoRemoteClientBeans(context);

            Path observed = configDirectory.resolve("observed.json");
            assertTrue(Files.isRegularFile(observed), "startup must persist observed.json");
            String observedJson = Files.readString(observed);
            assertTrue(observedJson.contains("\"workloadId\":\"collector-context-test\""));
            assertTrue(observedJson.contains("\"status\":\"WAITING_CONFIG\""),
                    "first boot without desired config must be WAITING_CONFIG");
            assertFalse(Files.exists(configDirectory.resolve("desired.json")));
            assertFalse(Files.exists(configDirectory.resolve("active.json")));
        }
    }

    private static void assertNoCenterBeanPackages(ConfigurableApplicationContext context) {
        assertTrue(beanTypes(context).stream().noneMatch(type ->
                type.getName().contains(".controller.")));
        assertTrue(beanTypes(context).stream().noneMatch(type ->
                type.getName().contains(".service.")));
    }

    private static void assertNoRemoteClientBeans(ConfigurableApplicationContext context) {
        assertTrue(beanTypes(context).stream().noneMatch(type -> {
            String name = type.getName();
            return name.startsWith("com.alibaba.cloud.nacos.")
                    || name.startsWith("org.springframework.cloud.openfeign.")
                    || name.startsWith("feign.");
        }), "Nacos and Feign client infrastructure must not be assembled");
    }

    private static java.util.Set<Class<?>> beanTypes(ConfigurableApplicationContext context) {
        return Arrays.stream(context.getBeanDefinitionNames())
                .map(context::getType)
                .filter(java.util.Objects::nonNull)
                .collect(java.util.stream.Collectors.toSet());
    }
}
