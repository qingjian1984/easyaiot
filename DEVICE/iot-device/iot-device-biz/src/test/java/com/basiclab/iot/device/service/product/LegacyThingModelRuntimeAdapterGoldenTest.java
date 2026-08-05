package com.basiclab.iot.device.service.product;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.EnumMap;
import java.util.Comparator;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LegacyThingModelRuntimeAdapterGoldenTest {

    private static final String CASE_PATH = ".doc/规格/电力运维云平台/assets/model-templates/verification/"
            + "legacy-roundtrip/easyaiot-legacy-thing-model-v1_td005-1.0.10";

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final LegacyThingModelRuntimeAdapter adapter = new LegacyThingModelRuntimeAdapter(objectMapper);

    @Test
    void importAndExportMustMatchTheFrozenRuntimeAndLegacyGoldens() throws IOException {
        Path caseDirectory = findWorkspaceRoot().resolve(CASE_PATH);
        JsonNode fixture = read(caseDirectory.resolve("legacy-input.json"));
        JsonNode expectedRuntime = read(caseDirectory.resolve("runtime-rows.golden.json"));
        JsonNode expectedOutput = read(caseDirectory.resolve("legacy-output.golden.json"));

        ObjectNode runtime = adapter.importToRuntime(fixture, new FrozenIdAllocator());
        ObjectNode output = adapter.exportFromRuntime(runtime);

        assertTrue(expectedRuntime.equals(JSON_VALUE_COMPARATOR, runtime),
                "Java importer 必须消费同一 runtime rows golden");
        assertTrue(expectedOutput.equals(JSON_VALUE_COMPARATOR, output),
                "Java exporter 必须消费同一 legacy output golden");
        assertFalse(runtime.path("tables").path("product_properties").get(0).has("serviceId"));
        assertEquals(1, runtime.path("tables").path("product_commands_requests").size());
        assertEquals(1, runtime.path("tables").path("product_commands_response").size());
        assertEquals(1, runtime.path("tables").path("product_event_response").size());
    }

    @Test
    void importerMustRejectAmbiguousServicePropertiesInsteadOfWritingRootFacts() throws IOException {
        ObjectNode fixture = (ObjectNode) read(findWorkspaceRoot().resolve(CASE_PATH).resolve("legacy-input.json"));
        ObjectNode service = (ObjectNode) fixture.withArray("services").get(0);
        service.set("properties", objectMapper.createArrayNode()
                .add(objectMapper.createObjectNode().put("propertyCode", "ambiguous")));

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> adapter.importToRuntime(fixture, new FrozenIdAllocator()));
        assertTrue(error.getMessage().startsWith("MODEL_LEGACY_SERVICE_PROPERTIES_AMBIGUOUS"));
    }

    @Test
    void importerMustRejectServiceIdOnRootProperty() throws IOException {
        ObjectNode fixture = (ObjectNode) read(findWorkspaceRoot().resolve(CASE_PATH).resolve("legacy-input.json"));
        ((ObjectNode) fixture.withArray("properties").get(0)).put("serviceId", 1201L);

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> adapter.importToRuntime(fixture, new FrozenIdAllocator()));
        assertTrue(error.getMessage().startsWith("MODEL_ROOT_PROPERTY_SERVICE_ID_FORBIDDEN"));
    }

    private JsonNode read(Path path) throws IOException {
        return objectMapper.readTree(Files.newBufferedReader(path));
    }

    private Path findWorkspaceRoot() {
        Path current = Paths.get(System.getProperty("user.dir")).toAbsolutePath();
        while (current != null) {
            if (Files.isRegularFile(current.resolve(CASE_PATH).resolve("legacy-input.json"))) {
                return current;
            }
            current = current.getParent();
        }
        throw new IllegalStateException("找不到冻结 TD-005 legacy round-trip 资产目录");
    }

    private static final class FrozenIdAllocator implements LegacyThingModelRuntimeAdapter.RuntimeIdAllocator {

        private final EnumMap<LegacyThingModelRuntimeAdapter.RuntimeEntity, Long> next =
                new EnumMap<>(LegacyThingModelRuntimeAdapter.RuntimeEntity.class);

        private FrozenIdAllocator() {
            next.put(LegacyThingModelRuntimeAdapter.RuntimeEntity.PRODUCT, 1001L);
            next.put(LegacyThingModelRuntimeAdapter.RuntimeEntity.ROOT_PROPERTY, 1101L);
            next.put(LegacyThingModelRuntimeAdapter.RuntimeEntity.SERVICE, 1201L);
            next.put(LegacyThingModelRuntimeAdapter.RuntimeEntity.COMMAND, 1301L);
            next.put(LegacyThingModelRuntimeAdapter.RuntimeEntity.SERVICE_INPUT, 1401L);
            next.put(LegacyThingModelRuntimeAdapter.RuntimeEntity.SERVICE_OUTPUT, 1501L);
            next.put(LegacyThingModelRuntimeAdapter.RuntimeEntity.EVENT, 1601L);
            next.put(LegacyThingModelRuntimeAdapter.RuntimeEntity.EVENT_OUTPUT, 1701L);
        }

        @Override
        public long next(LegacyThingModelRuntimeAdapter.RuntimeEntity type) {
            Long value = next.get(type);
            if (value == null) {
                throw new IllegalArgumentException("未配置冻结 ID: " + type);
            }
            next.put(type, value + 1);
            return value;
        }
    }

    private static final Comparator<JsonNode> JSON_VALUE_COMPARATOR = (left, right) -> {
        if (left != null && right != null && left.isNumber() && right.isNumber()) {
            return left.decimalValue().compareTo(right.decimalValue());
        }
        return left == null ? (right == null ? 0 : -1) : left.equals(right) ? 0 : 1;
    };
}
