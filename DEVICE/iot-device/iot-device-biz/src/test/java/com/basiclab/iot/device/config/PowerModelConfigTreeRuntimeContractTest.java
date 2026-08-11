package com.basiclab.iot.device.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.Banner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.WebApplicationType;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Configuration;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** Verifies the real Spring Config Data binding used by the TD-005 Secret mount. */
class PowerModelConfigTreeRuntimeContractTest {

    private static final String PROPERTY = "easyaiot.power-model.idempotency-hmac-secret";
    private static final String TEST_SECRET = "td005-config-tree-contract-only-32-bytes";

    @Test
    void directConfigTreePropertyOverridesTheEmptyEnvironmentFallback(@TempDir Path tempDir) throws Exception {
        Path secretsDir = Files.createDirectory(tempDir.resolve("secrets"));
        Files.writeString(secretsDir.resolve(PROPERTY), TEST_SECRET, StandardCharsets.UTF_8);

        try (ConfigurableApplicationContext context = startWithConfigTree(tempDir, secretsDir)) {
            assertEquals(TEST_SECRET, context.getEnvironment().getRequiredProperty(PROPERTY));
        }
    }

    @Test
    void missingConfigTreePropertyKeepsTheFailClosedEmptyFallback(@TempDir Path tempDir) throws Exception {
        Path secretsDir = Files.createDirectory(tempDir.resolve("secrets"));

        try (ConfigurableApplicationContext context = startWithConfigTree(tempDir, secretsDir)) {
            assertEquals("", context.getEnvironment().getRequiredProperty(PROPERTY));
        }
    }

    @Test
    void repositoryMultiDocumentApplicationYamlImportsTheFinalConfigTreeProperty(@TempDir Path tempDir)
            throws Exception {
        Path secretsDir = Files.createDirectory(tempDir.resolve("secrets"));
        Files.writeString(secretsDir.resolve(PROPERTY), TEST_SECRET, StandardCharsets.UTF_8);
        String repositoryYaml = Files.readString(repositoryRoot().resolve(
                "DEVICE/iot-device/iot-device-biz/src/main/resources/application.yaml"));
        String runtimeYaml = repositoryYaml.replace(
                "optional:configtree:/run/secrets/",
                "optional:configtree:" + configTreePath(secretsDir));

        try (ConfigurableApplicationContext context = startWithYaml(tempDir, runtimeYaml)) {
            assertEquals(TEST_SECRET, context.getEnvironment().getRequiredProperty(PROPERTY));
        }
    }

    private static ConfigurableApplicationContext startWithConfigTree(Path tempDir, Path secretsDir)
            throws Exception {
        String applicationYaml = """
                spring:
                  config:
                    import: 'optional:configtree:%s'
                easyaiot:
                  power-model:
                    idempotency-hmac-secret: '${EASYAIOT_POWER_MODEL_IDEMPOTENCY_HMAC_SECRET:}'
                """.formatted(configTreePath(secretsDir));
        return startWithYaml(tempDir, applicationYaml);
    }

    private static ConfigurableApplicationContext startWithYaml(Path tempDir, String applicationYaml)
            throws Exception {
        Path configDir = Files.createDirectory(tempDir.resolve("config"));
        Files.writeString(configDir.resolve("application.yaml"), applicationYaml, StandardCharsets.UTF_8);

        SpringApplication application = new SpringApplication(ProbeConfiguration.class);
        application.setWebApplicationType(WebApplicationType.NONE);
        application.setBannerMode(Banner.Mode.OFF);
        application.setLogStartupInfo(false);
        return application.run("--spring.config.location=" + directoryUri(configDir));
    }

    private static String directoryUri(Path directory) {
        String uri = directory.toUri().toASCIIString();
        return uri.endsWith("/") ? uri : uri + "/";
    }

    private static String configTreePath(Path directory) {
        String path = directory.toAbsolutePath().normalize().toString().replace('\\', '/');
        return path.endsWith("/") ? path : path + "/";
    }

    private static Path repositoryRoot() {
        Path current = Path.of(System.getProperty("user.dir")).toAbsolutePath();
        while (current != null) {
            if (Files.isDirectory(current.resolve("DEVICE")) && Files.isDirectory(current.resolve(".scripts"))) {
                return current;
            }
            current = current.getParent();
        }
        throw new IllegalStateException("repository root not found");
    }

    @Configuration(proxyBeanMethods = false)
    static class ProbeConfiguration {
    }
}
