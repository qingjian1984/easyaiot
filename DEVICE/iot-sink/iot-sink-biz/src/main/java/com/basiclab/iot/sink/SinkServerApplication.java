package com.basiclab.iot.sink;



import com.basiclab.iot.common.annotation.EnableCustomSwagger2;
import com.basiclab.iot.common.annotations.EnableCustomConfig;
import com.basiclab.iot.common.annotations.EnableRyFeignClients;
import com.basiclab.iot.sink.config.IotGatewayConfiguration;
import com.basiclab.iot.sink.outbox.sqlite.SqliteOutboxAutoConfiguration;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.CrossOrigin;

import java.util.Arrays;
import java.util.Map;

@CrossOrigin(origins = "*", maxAge = 3600)
/**
 * SinkServerApplication
 *
 * @author 翱翔的雄库鲁
 * @email andywebjava@163.com
 * @wechat EasyAIoT2025
 */

@Slf4j
@SpringBootConfiguration
@Import({SinkServerApplication.SinkServerCenterConfiguration.class,
        SinkServerApplication.SinkServerCollectorConfiguration.class})
public class SinkServerApplication {

    public static void main(String[] args) {
        createApplication(args).run(args);
    }

    /**
     * Build the same application used by the executable entry point.  Bootstrap
     * properties must be available before Spring Cloud creates its bootstrap
     * context; application-collector.yaml is intentionally retained as the
     * profile-level source of truth, while these low-priority defaults prevent
     * an early Nacos lookup for the single-workload collector process.
     */
    public static SpringApplication createApplication(String... args) {
        SpringApplication application = new SpringApplication(SinkServerApplication.class);
        if (collectorProfileRequested(args)) {
            application.setDefaultProperties(Map.of(
                    "spring.cloud.bootstrap.enabled", "false",
                    "spring.cloud.nacos.config.enabled", "false",
                    "spring.cloud.nacos.discovery.enabled", "false"));
        }
        return application;
    }

    private static boolean collectorProfileRequested(String... args) {
        return Arrays.stream(args)
                .filter(arg -> arg.startsWith("--spring.profiles.active="))
                .map(arg -> arg.substring("--spring.profiles.active=".length()))
                .flatMap(value -> Arrays.stream(value.split(",")))
                .anyMatch("collector"::equals);
    }

    /**
     * The center keeps the historical broad component graph and auto-configuration
     * semantics.  It is explicitly profile-gated so this scan is never evaluated by
     * the local collector bootstrap.
     */
    @Configuration(proxyBeanMethods = false)
    @Profile("!collector")
    @EnableAutoConfiguration
    @ComponentScan(basePackages = "com.basiclab.iot")
    @EnableCustomConfig
    @EnableCustomSwagger2
    @EnableRyFeignClients
    static class SinkServerCenterConfiguration {
    }

    /**
     * The collector is a deliberately closed graph.  Do not replace these imports
     * with a component scan: the collector must not assemble center services,
     * persistence, Redis, Nacos, Feign clients, or the center message bus.
     */
    @Configuration(proxyBeanMethods = false)
    @Profile("collector")
    @Import({IotGatewayConfiguration.class, SqliteOutboxAutoConfiguration.class})
    static class SinkServerCollectorConfiguration {
    }

}
