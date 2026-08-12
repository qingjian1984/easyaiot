package com.basiclab.iot.device.config;

import com.basiclab.iot.common.capability.CapabilityService;
import com.basiclab.iot.common.capability.ManifestCapabilityService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 双基线配置组合门禁：mini 全关，standard/full 只允许完整写链启用。 */
class PowerModelActivationGuardTest {

    private static final byte[] TEST_SECRET =
            "0123456789abcdef0123456789abcdef".getBytes(StandardCharsets.UTF_8);

    @Test
    void allDisabledIsSafeForMini() {
        assertDoesNotThrow(() -> PowerModelActivationGuard.verify(
                ManifestCapabilityService.disabled("mini"), false, false, false, false, new byte[0]));
    }

    @Test
    void bindingApiRejectsMissingReleasePortOrEventPipeline() throws Exception {
        CapabilityService standard = load("electric-standard.json");
        IllegalStateException noRelease = assertThrows(IllegalStateException.class,
                () -> PowerModelActivationGuard.verify(
                        standard, true, true, false, true, TEST_SECRET));
        assertTrue(noRelease.getMessage().startsWith("POWER_MODEL_ACTIVATION_INCOMPLETE"));
        IllegalStateException noEvents = assertThrows(IllegalStateException.class,
                () -> PowerModelActivationGuard.verify(
                        standard, true, true, true, false, TEST_SECRET));
        assertTrue(noEvents.getMessage().startsWith("POWER_MODEL_ACTIVATION_INCOMPLETE"));
    }

    @Test
    void completeWriteChainIsAllowedForStandardAndFull() throws Exception {
        assertDoesNotThrow(() -> PowerModelActivationGuard.verify(
                load("electric-standard.json"), true, true, true, true, TEST_SECRET));
        assertDoesNotThrow(() -> PowerModelActivationGuard.verify(
                load("electric-full.json"), true, true, true, true, TEST_SECRET));
    }

    @Test
    void bindingApiRejectsMissingOrShortIdempotencySecret() throws Exception {
        CapabilityService standard = load("electric-standard.json");
        IllegalStateException missing = assertThrows(IllegalStateException.class,
                () -> PowerModelActivationGuard.verify(
                        standard, true, true, true, true, new byte[0]));
        assertTrue(missing.getMessage().startsWith("POWER_MODEL_IDEMPOTENCY_SECRET_INVALID"));
        IllegalStateException shortSecret = assertThrows(IllegalStateException.class,
                () -> PowerModelActivationGuard.verify(
                        standard, true, true, true, true,
                        "不足三十二字节".getBytes(StandardCharsets.UTF_8)));
        assertTrue(shortSecret.getMessage().startsWith("POWER_MODEL_IDEMPOTENCY_SECRET_INVALID"));
    }

    @Test
    void anyActivationRejectsMiniAndDisabledCapability() {
        IllegalStateException mini = assertThrows(IllegalStateException.class,
                () -> PowerModelActivationGuard.verify(
                        ManifestCapabilityService.disabled("mini"), false, false,
                        false, true, new byte[0]));
        assertTrue(mini.getMessage().startsWith("POWER_MODEL_PROFILE_NOT_SUPPORTED"));
        IllegalStateException disabled = assertThrows(IllegalStateException.class,
                () -> PowerModelActivationGuard.verify(
                        ManifestCapabilityService.disabled("standard"), true, true, true, true,
                        TEST_SECRET));
        assertTrue(disabled.getMessage().startsWith("POWER_MODEL_CAPABILITY_DISABLED"));
    }

    @Test
    void standardMayStageReleasePortButRejectsEventsWithoutIt() throws Exception {
        CapabilityService standard = load("electric-standard.json");
        assertDoesNotThrow(() -> PowerModelActivationGuard.verify(
                standard, false, false, true, false, new byte[0]));
        assertDoesNotThrow(() -> PowerModelActivationGuard.verify(
                standard, false, false, true, true, new byte[0]));
        IllegalStateException eventsOnly = assertThrows(IllegalStateException.class,
                () -> PowerModelActivationGuard.verify(
                        standard, false, false, false, true, new byte[0]));
        assertTrue(eventsOnly.getMessage().startsWith("POWER_MODEL_ACTIVATION_INCOMPLETE"));
    }

    @Test
    void templateApiIsAnIndependentSecretProtectedStageBeforeBindingApi() throws Exception {
        CapabilityService standard = load("electric-standard.json");
        assertDoesNotThrow(() -> PowerModelActivationGuard.verify(
                standard, true, false, true, true, TEST_SECRET));
        IllegalStateException noSecret = assertThrows(IllegalStateException.class,
                () -> PowerModelActivationGuard.verify(
                        standard, true, false, true, true, new byte[0]));
        assertTrue(noSecret.getMessage().startsWith("POWER_MODEL_IDEMPOTENCY_SECRET_INVALID"));
        IllegalStateException bindingWithoutTemplate = assertThrows(IllegalStateException.class,
                () -> PowerModelActivationGuard.verify(
                        standard, false, true, true, true, TEST_SECRET));
        assertTrue(bindingWithoutTemplate.getMessage()
                .startsWith("POWER_MODEL_ACTIVATION_INCOMPLETE"));
    }

    @Test
    void deploymentSurfacesExposeAllThreeSwitchesWithSafeDefaults() throws Exception {
        Path root = findRepositoryRoot();
        String application = Files.readString(root.resolve(
                "DEVICE/iot-device/iot-device-biz/src/main/resources/application.yaml"));
        assertTrue(application.contains(
                "EASYAIOT_POWER_MODEL_TEMPLATE_API_ENABLED:false"));
        assertTrue(application.contains(
                "EASYAIOT_POWER_MODEL_BINDING_APPLY_API_ENABLED:false"));
        assertTrue(application.contains(
                "EASYAIOT_POWER_MODEL_COLLECTOR_RELEASE_PORT_ENABLED:false"));
        assertTrue(application.contains("POWER_MODEL_EVENTS_ENABLED:false"));

        String compose = Files.readString(root.resolve("DEVICE/docker-compose.yml"));
        int deviceService = compose.indexOf("  iot-device:");
        int nextService = compose.indexOf("\n  iot-", deviceService + 3);
        String deviceBlock = compose.substring(deviceService,
                nextService < 0 ? compose.length() : nextService);
        assertTrue(deviceBlock.contains("EASYAIOT_POWER_MODEL_TEMPLATE_API_ENABLED"));
        assertTrue(deviceBlock.contains("EASYAIOT_POWER_MODEL_BINDING_APPLY_API_ENABLED"));
        assertTrue(deviceBlock.contains("EASYAIOT_POWER_MODEL_COLLECTOR_RELEASE_PORT_ENABLED"));
        assertTrue(deviceBlock.contains("EASYAIOT_POWER_MODEL_IDEMPOTENCY_HMAC_SECRET"));
        assertTrue(deviceBlock.contains("POWER_MODEL_EVENTS_ENABLED"));
        assertTrue(deviceBlock.contains("SPRING_KAFKA_BOOTSTRAP_SERVERS=Kafka:9092"));

        String example = Files.readString(root.resolve(".scripts/docker/env.example"));
        assertTrue(example.contains("EASYAIOT_POWER_MODEL_TEMPLATE_API_ENABLED=false"));
        assertTrue(example.contains("EASYAIOT_POWER_MODEL_BINDING_APPLY_API_ENABLED=false"));
        assertTrue(example.contains("EASYAIOT_POWER_MODEL_COLLECTOR_RELEASE_PORT_ENABLED=false"));
        assertTrue(example.contains("POWER_MODEL_EVENTS_ENABLED=false"));
        assertTrue(example.contains("EASYAIOT_POWER_MODEL_IDEMPOTENCY_HMAC_SECRET="));
        assertFalse(example.contains("EASYAIOT_POWER_MODEL_IDEMPOTENCY_HMAC_SECRET=change-me"));
    }

    private static CapabilityService load(String fileName) throws Exception {
        try (InputStream input = Files.newInputStream(findManifest(fileName))) {
            return ManifestCapabilityService.load(input, new ObjectMapper());
        }
    }

    private static Path findManifest(String fileName) {
        return findRepositoryRoot().resolve(".scripts/docker/capabilities").resolve(fileName);
    }

    private static Path findRepositoryRoot() {
        Path current = Paths.get(System.getProperty("user.dir")).toAbsolutePath();
        while (current != null) {
            if (Files.isDirectory(current.resolve(".scripts/docker/capabilities"))
                    && Files.isDirectory(current.resolve("DEVICE"))) return current;
            current = current.getParent();
        }
        throw new IllegalStateException("repository root not found");
    }
}
