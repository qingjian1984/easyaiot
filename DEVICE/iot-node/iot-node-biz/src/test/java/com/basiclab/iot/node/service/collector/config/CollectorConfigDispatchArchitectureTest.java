package com.basiclab.iot.node.service.collector.config;

import com.basiclab.iot.node.domain.collector.config.CollectorConfigPutRequestDTO;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CollectorConfigDispatchArchitectureTest {

    @Test
    void requestDtoIsClosedAndAgentPathsAreFixed() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        CollectorConfigPutRequestDTO request = new CollectorConfigPutRequestDTO();
        request.setWorkloadId("collector-site-1001-a");
        request.setConfigVersion(1L);
        request.setSchemaVersion("1.1");
        request.setCanonicalizationVersion("jcs-rfc8785-v1");
        request.setPayloadCanonical("{}");
        request.setPayloadSha256("0".repeat(64));
        request.setCanonicalLengthBytes(2L);
        JsonNode json = mapper.readTree(mapper.writeValueAsBytes(request));
        assertEquals(Set.of("workloadId", "configVersion", "schemaVersion",
                "canonicalizationVersion", "payloadCanonical", "payloadSha256",
                "canonicalLengthBytes"), fields(json));
        assertEquals("/workload/collector/config", CollectorAgentClient.PUT_PATH);
        assertEquals("/workload/collector/", CollectorAgentClient.GET_PATH_PREFIX);
    }

    @Test
    void productionCollectorSourcesDoNotReachGenericWorkloadOrPersistencePaths() throws Exception {
        StringBuilder source = new StringBuilder();
        try (var stream = Files.walk(modulePath("src/main/java/com/basiclab/iot/node/service/collector/config"))) {
            stream.filter(path -> path.toString().endsWith(".java"))
                    .forEach(path -> {
                        try {
                            source.append(Files.readString(path, StandardCharsets.UTF_8));
                        } catch (Exception error) {
                            throw new AssertionError(error);
                        }
                    });
        }
        String value = source.toString();
        assertFalse(value.contains("NodeCommandServiceImpl"));
        assertFalse(value.contains("/workload/deploy"));
        assertFalse(value.contains("NodeWorkloadBindingMapper"));
        assertFalse(value.contains(".insert("));
        assertFalse(value.contains(".update("));
        assertFalse(value.contains("java.lang.reflect"));
        assertFalse(value.contains("Class.forName"));
        assertFalse(value.contains("getMethod("));
        assertTrue(value.contains("/workload/collector/config"));
        assertTrue(value.contains("/workload/collector/"));
    }

    @Test
    void typedReleaseApiAndConditionalScheduledAssemblyAreExplicit() throws Exception {
        StringBuilder source = new StringBuilder();
        try (var stream = Files.walk(modulePath("src/main/java/com/basiclab/iot/node/service/collector/config"))) {
            stream.filter(path -> path.toString().endsWith(".java"))
                    .forEach(path -> {
                        try {
                            source.append(Files.readString(path, StandardCharsets.UTF_8));
                        } catch (Exception error) {
                            throw new AssertionError(error);
                        }
                    });
        }
        String value = source.toString();
        assertTrue(value.contains("CollectorConfigReleaseInternalApi"));
        assertTrue(value.contains("response.isSuccess()"));
        assertTrue(value.contains("response.getData()"));
        assertTrue(value.contains("@ConditionalOnProperty"));
        assertTrue(value.contains("@Scheduled("));
        assertTrue(value.contains("dispatchPending(batchLimit)"));
    }

    @Test
    void agentResponseIsStreamedWithHardLimitAndClosed() throws Exception {
        String source = Files.readString(modulePath(
                "src/main/java/com/basiclab/iot/node/service/collector/config/CollectorAgentClient.java"),
                StandardCharsets.UTF_8);
        assertTrue(source.contains("BodyHandlers.ofInputStream()"));
        assertFalse(source.contains("BodyHandlers.ofByteArray()"));
        assertTrue(source.contains("try (InputStream responseStream = response.body())"));
        assertTrue(source.contains("MAX_RESPONSE_BYTES + 1"));
    }

    @Test
    void backoffEntryHasNoCanonicalOrDetailStorage() {
        for (Field field : CollectorConfigDispatchBackoff.Entry.class.getDeclaredFields()) {
            assertFalse(field.getName().toLowerCase().contains("canonical"));
            assertFalse(field.getName().toLowerCase().contains("detail"));
        }
    }

    @Test
    void productionConfigurationDefaultsToDisabled() throws Exception {
        String resource = Files.readString(modulePath("src/main/resources/application.yaml"),
                StandardCharsets.UTF_8);
        assertTrue(resource.contains("enabled: ${EASYAIOT_COLLECTOR_CONFIG_DISPATCH_ENABLED:false}"));
        assertTrue(resource.contains("fixed-delay-ms: ${EASYAIOT_COLLECTOR_CONFIG_DISPATCH_FIXED_DELAY_MS:30000}"));
    }

    private static Path modulePath(String relative) {
        Path direct = Path.of(relative);
        if (Files.exists(direct)) {
            return direct;
        }
        return Path.of("DEVICE/iot-node/iot-node-biz").resolve(relative);
    }

    private static Set<String> fields(JsonNode node) {
        Set<String> result = new HashSet<>();
        node.fieldNames().forEachRemaining(result::add);
        return result;
    }
}
