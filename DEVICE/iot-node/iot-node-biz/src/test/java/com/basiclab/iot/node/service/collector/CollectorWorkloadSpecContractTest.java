package com.basiclab.iot.node.service.collector;

import com.basiclab.iot.node.domain.collector.CollectorDeploymentProfile;
import com.basiclab.iot.node.domain.collector.CollectorWorkloadSpec;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.math.BigDecimal;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CollectorWorkloadSpecContractTest {

    private static final String FIXTURE_RESOURCE =
            "fixture/collector/workload/v1/collector-workload-spec-v1-golden.json";
    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void acceptsGoldenFixtureAndBuildsImmutableArtifact() throws Exception {
        CollectorWorkloadSpecArtifact artifact = validator()
                .validateAndBuild(fixture(), CollectorDeploymentProfile.STANDARD);

        assertEquals("1.0", artifact.getSpec().getSpecVersion());
        assertEquals("iot-sink-collector", artifact.getSpec().getWorkloadType());
        assertEquals("21", artifact.getSpec().getNodeId());
        assertEquals(CollectorWorkloadSpec.CONFIG_TARGET_PATH,
                artifact.getSpec().getConfig().getTargetPath());
        assertEquals(CollectorWorkloadSpec.OUTBOX_CONTAINER_PATH,
                artifact.getSpec().getVolumes().get(0).getContainerPath());
        assertEquals("1.0", artifact.getSpec().getResources().getCpuCores());
        assertEquals(402653184L, artifact.getSpec().getResources().getMemoryBytes());
        assertEquals(artifact.getCanonicalBytes().length, artifact.getCanonicalLengthBytes());
        assertEquals(64, artifact.getSha256().length());
        assertTrue(artifact.getSha256().matches("[0-9a-f]{64}"));

        byte[] bytes = artifact.getCanonicalBytes();
        bytes[0] = bytes[0] == (byte) '{' ? (byte) '[' : (byte) '{';
        assertFalse(java.util.Arrays.equals(bytes, artifact.getCanonicalBytes()));
        assertTrue(artifact.getCanonicalBytes()[0] == '{');
    }

    @Test
    void canonicalBytesAndHashAreStableWhenObjectMembersAreReordered() throws Exception {
        ObjectNode reordered = mapper.createObjectNode();
        List<String> names = new ArrayList<>();
        fixture().fieldNames().forEachRemaining(names::add);
        names.sort(Comparator.reverseOrder());
        for (String name : names) {
            reordered.set(name, fixture().get(name));
        }

        CollectorWorkloadSpecArtifact first = validator().validateAndBuild(fixture());
        CollectorWorkloadSpecArtifact second = validator().validateAndBuild(reordered);
        assertArrayEquals(first.getCanonicalBytes(), second.getCanonicalBytes());
        assertEquals(first.getSha256(), second.getSha256());
        assertEquals(first.getCanonicalLengthBytes(), second.getCanonicalLengthBytes());
    }

    @Test
    void schemaIsParseableClosedAndHasTheSameFieldSetsAsTypedDto() throws Exception {
        JsonNode schema = resource(CollectorWorkloadSpecValidator.SCHEMA_RESOURCE);
        assertTrue(schema.isObject());
        assertEquals(CollectorWorkloadSpec.ROOT_FIELDS, propertyNames(schema));
        assertEquals(CollectorWorkloadSpec.IMAGE_FIELDS, propertyNames(schema.at("/properties/image")));
        assertEquals(CollectorWorkloadSpec.CONFIG_FIELDS, propertyNames(schema.at("/properties/config")));
        assertEquals(CollectorWorkloadSpec.RESOURCE_FIELDS, propertyNames(schema.at("/properties/resources")));
        assertEquals(CollectorWorkloadSpec.SERIAL_DEVICE_FIELDS,
                propertyNames(schema.at("/properties/serialDevices/items")));
        assertEquals(CollectorWorkloadSpec.VOLUME_FIELDS,
                propertyNames(schema.at("/properties/volumes/items")));
        assertEquals(CollectorWorkloadSpec.UPDATE_POLICY_FIELDS,
                propertyNames(schema.at("/properties/updatePolicy")));
        assertEquals(CollectorWorkloadSpec.CONFIG_TARGET_PATH,
                schema.at("/properties/config/properties/targetPath/const").asText());
        assertEveryPayloadObjectIsClosed(schema);
    }

    @Test
    void rejectsUnknownAndForbiddenGenericWorkloadFields() throws Exception {
        ObjectNode unknown = fixture();
        unknown.put("command", "java -jar app.jar");
        assertCode("COLLECTOR_WORKLOAD_SCHEMA_INVALID", unknown);

        ObjectNode nestedUnknown = fixture();
        nestedUnknown.with("image").put("entrypoint", "sh");
        assertCode("COLLECTOR_WORKLOAD_SCHEMA_INVALID", nestedUnknown);

        ObjectNode privileged = fixture();
        privileged.put("privileged", true);
        assertCode("COLLECTOR_WORKLOAD_SCHEMA_INVALID", privileged);

        ObjectNode env = fixture();
        env.putObject("env").put("BROKER_PASSWORD", "not-a-secret");
        assertCode("COLLECTOR_WORKLOAD_SCHEMA_INVALID", env);

        ObjectNode hostNetwork = fixture();
        hostNetwork.put("hostNetwork", true);
        assertCode("COLLECTOR_WORKLOAD_SCHEMA_INVALID", hostNetwork);

        ObjectNode capabilities = fixture();
        capabilities.putArray("capabilities").add("NET_ADMIN");
        assertCode("COLLECTOR_WORKLOAD_SCHEMA_INVALID", capabilities);

        ObjectNode files = fixture();
        files.putArray("files").addObject().put("path", "/etc/passwd");
        assertCode("COLLECTOR_WORKLOAD_SCHEMA_INVALID", files);
    }

    @Test
    void rejectsDriftingOrUntrustedImages() throws Exception {
        ObjectNode tag = fixture();
        tag.with("image").put("digest", "latest");
        assertCode("COLLECTOR_WORKLOAD_SCHEMA_INVALID", tag);

        ObjectNode upperDigest = fixture();
        upperDigest.with("image").put("digest",
                "sha256:AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA");
        assertCode("COLLECTOR_WORKLOAD_SCHEMA_INVALID", upperDigest);

        ObjectNode repository = fixture();
        repository.with("image").put("repository", "registry.attacker.example/iot-sink");
        assertCode("COLLECTOR_WORKLOAD_IMAGE_REPOSITORY_FORBIDDEN", repository);
    }

    @Test
    void rejectsNumericIdsFloatsAndSchemaTransportLimits() throws Exception {
        ObjectNode numericId = fixture();
        numericId.put("nodeId", 21);
        assertCode("COLLECTOR_WORKLOAD_SCHEMA_INVALID", numericId);

        ObjectNode scientificCpu = fixture();
        scientificCpu.with("resources").put("cpuCores", "1e0");
        assertCode("COLLECTOR_WORKLOAD_SCHEMA_INVALID", scientificCpu);

        ObjectNode floatingMemory = fixture();
        floatingMemory.with("resources").put("memoryBytes", 402653184.5d);
        assertCode("COLLECTOR_WORKLOAD_SCHEMA_INVALID", floatingMemory);

        ObjectNode tooMuchMemory = fixture();
        tooMuchMemory.with("resources").put("memoryBytes", 68719476737L);
        assertCode(schemaLimitValidator(), "COLLECTOR_WORKLOAD_SCHEMA_INVALID", tooMuchMemory);

        ObjectNode tooMuchCpu = fixture();
        tooMuchCpu.with("resources").put("cpuCores", "64.1");
        assertCode(schemaLimitValidator(), "COLLECTOR_WORKLOAD_SCHEMA_INVALID", tooMuchCpu);
    }

    @Test
    void configTargetIsFixedContainerPath() throws Exception {
        ObjectNode selectedHostPath = fixture();
        selectedHostPath.with("config").put("targetPath", "/tmp/attacker/config.json");
        assertCode("COLLECTOR_WORKLOAD_SCHEMA_INVALID", selectedHostPath);

        ObjectNode traversal = fixture();
        traversal.with("config").put("targetPath", "/var/lib/easyaiot/config/../escape.json");
        assertCode("COLLECTOR_WORKLOAD_SCHEMA_INVALID", traversal);
    }

    @Test
    void rejectsUnsafeVolumesCrossWorkloadPathsAndSerialPaths() throws Exception {

        ObjectNode socket = fixture();
        ((ObjectNode) socket.withArray("volumes").get(0))
                .put("hostPath", "/var/run/docker.sock");
        assertCode("COLLECTOR_WORKLOAD_PATH_INVALID", socket);

        ObjectNode outside = fixture();
        ((ObjectNode) outside.withArray("volumes").get(0))
                .put("hostPath", "/var/lib/easyaiot/not-collector/collector-site-1001-a/outbox");
        assertCode("COLLECTOR_WORKLOAD_PATH_INVALID", outside);

        ObjectNode crossWorkload = fixture();
        ((ObjectNode) crossWorkload.withArray("volumes").get(0))
                .put("hostPath", "/var/lib/easyaiot/collector/collector-site-1001-b/outbox");
        assertCode("COLLECTOR_WORKLOAD_VOLUME_INVALID", crossWorkload);

        ObjectNode serial = fixture();
        ((ObjectNode) serial.withArray("serialDevices").get(0))
                .put("hostPath", "/dev/ttyUSB0");
        assertCode("COLLECTOR_WORKLOAD_PATH_INVALID", serial);
    }

    @Test
    void rejectsInvalidPolicyAndCrossNodeSecretReference() throws Exception {
        ObjectNode policy = fixture();
        policy.with("updatePolicy").put("healthWindowSeconds", 3601);
        assertCode("COLLECTOR_WORKLOAD_SCHEMA_INVALID", policy);

        ObjectNode broker = fixture();
        broker.put("brokerRef", "secret://node/22/collector/site-1001");
        assertCode("COLLECTOR_WORKLOAD_BROKER_REF_INVALID", broker);
    }

    @Test
    void miniProfileFailsClosedBeforeBuildingArtifact() throws Exception {
        CollectorWorkloadSpecValidationException error = assertThrows(
                CollectorWorkloadSpecValidationException.class,
                () -> validator().validateAndBuild(fixture(), CollectorDeploymentProfile.MINI));
        assertEquals("COLLECTOR_WORKLOAD_PROFILE_UNSUPPORTED", error.getCode());
    }

    @Test
    void installationCapabilityQuotaIsIndependentFromSchemaTransportLimit() throws Exception {
        ObjectNode cpu = fixture();
        cpu.with("resources").put("cpuCores", "2.1");
        assertCode("COLLECTOR_WORKLOAD_RESOURCE_LIMIT_EXCEEDED", cpu);

        ObjectNode memory = fixture();
        memory.with("resources").put("memoryBytes", 536870913L);
        assertCode("COLLECTOR_WORKLOAD_RESOURCE_LIMIT_EXCEEDED", memory);
    }

    @Test
    void missingCapabilityQuotaFailsClosed() {
        IllegalArgumentException error = assertThrows(
                IllegalArgumentException.class,
                () -> new CollectorWorkloadSpecValidator(
                        mapper,
                        Set.of("registry.example/easyaiot/iot-sink-biz"),
                        Path.of("/var/lib/easyaiot/collector"),
                        Set.of(Path.of("/dev/serial/by-id/usb-vendor-device")),
                        null,
                        536870912L));
        assertTrue(error.getMessage().contains("COLLECTOR_WORKLOAD_CONFIGURATION_INVALID"));
    }

    private void assertCode(String code, ObjectNode payload) {
        assertCode(validator(), code, payload);
    }

    private void assertCode(CollectorWorkloadSpecValidator validator,
                            String code,
                            ObjectNode payload) {
        CollectorWorkloadSpecValidationException error = assertThrows(
                CollectorWorkloadSpecValidationException.class,
                () -> validator.validateAndBuild(payload, CollectorDeploymentProfile.STANDARD));
        assertEquals(code, error.getCode(), error.getMessage());
    }

    private CollectorWorkloadSpecValidator validator() {
        return validator(new BigDecimal("2"), 536870912L);
    }

    private CollectorWorkloadSpecValidator schemaLimitValidator() {
        return validator(new BigDecimal("64"), 68719476736L);
    }

    private CollectorWorkloadSpecValidator validator(BigDecimal maxCpuCores,
                                                      long maxMemoryBytes) {
        return new CollectorWorkloadSpecValidator(
                mapper,
                Set.of("registry.example/easyaiot/iot-sink-biz"),
                Path.of("/var/lib/easyaiot/collector"),
                Set.of(Path.of("/dev/serial/by-id/usb-vendor-device")),
                maxCpuCores,
                maxMemoryBytes);
    }

    private ObjectNode fixture() throws Exception {
        return (ObjectNode) resource(FIXTURE_RESOURCE);
    }

    private JsonNode resource(String name) throws Exception {
        try (InputStream input = getClass().getClassLoader().getResourceAsStream(name)) {
            assertNotNull(input, "缺少测试资源 " + name);
            return mapper.readTree(input);
        }
    }

    private static Set<String> propertyNames(JsonNode objectSchema) {
        Set<String> names = new LinkedHashSet<>();
        objectSchema.path("properties").fieldNames().forEachRemaining(names::add);
        return names;
    }

    private static void assertEveryPayloadObjectIsClosed(JsonNode node) {
        if (node.isObject()) {
            if ("object".equals(node.path("type").asText())) {
                assertTrue(node.has("additionalProperties"));
                assertFalse(node.path("additionalProperties").asBoolean(true));
            }
            Iterator<JsonNode> children = node.elements();
            while (children.hasNext()) {
                assertEveryPayloadObjectIsClosed(children.next());
            }
        } else if (node.isArray()) {
            for (JsonNode child : node) {
                assertEveryPayloadObjectIsClosed(child);
            }
        }
    }
}
