package com.basiclab.iot.common.capability;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CapabilityManifestContractTest {

    private static final String MANIFEST_DIR = ".scripts/docker/capabilities";
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void manifestsMustMatchSchemaIdentityAndTd005QuotaContract() throws Exception {
        CapabilityService standard = load("electric-standard.json");
        CapabilityService full = load("electric-full.json");

        assertEquals("standard", standard.snapshot().getProfile());
        assertEquals("full", full.snapshot().getProfile());
        assertEquals(64, standard.snapshot().getSha256().length());
        assertEquals(64, full.snapshot().getSha256().length());
        assertNotEquals(standard.snapshot().getSha256(), full.snapshot().getSha256());

        Set<String> quotaKeys = Set.of("maxTemplates", "maxImportLines", "maxConcurrentImportJobs",
                "maxConcurrentPublish", "maxRebasesPerDay", "maxVersionsPerTemplate",
                "maxTemplateCanonicalBytes");
        assertEquals(quotaKeys, standard.find("power.device.model").orElseThrow().getQuota().keySet());
        assertEquals(quotaKeys, full.find("power.device.model").orElseThrow().getQuota().keySet());
    }

    @Test
    void fullEnabledCapabilitiesMustBeStrictSupersetAndSharedQuotasCannotDecrease() throws Exception {
        CapabilityService standard = load("electric-standard.json");
        CapabilityService full = load("electric-full.json");
        Set<String> standardEnabled = enabled(standard);
        Set<String> fullEnabled = enabled(full);

        assertTrue(fullEnabled.containsAll(standardEnabled));
        assertTrue(fullEnabled.size() > standardEnabled.size());
        for (String code : standardEnabled) {
            CapabilityDefinition standardDefinition = standard.find(code).orElseThrow();
            CapabilityDefinition fullDefinition = full.find(code).orElseThrow();
            assertTrue(fullDefinition.getRequires().containsAll(standardDefinition.getRequires()));
            for (Map.Entry<String, Long> quota : standardDefinition.getQuota().entrySet()) {
                assertTrue(fullDefinition.getQuota().get(quota.getKey()) >= quota.getValue(),
                        code + "." + quota.getKey() + " decreased in full");
            }
        }
    }

    @Test
    void missingManifestMustFailClosedForMini() {
        CapabilityService mini = ManifestCapabilityService.disabled("mini");
        assertEquals("mini", mini.snapshot().getProfile());
        assertFalse(mini.isEnabled("power.device.model"));
        assertTrue(mini.quota("power.device.model", "maxTemplates").isEmpty());
    }

    @Test
    void versionedSchemaMustDeclareClosedDraft202012Contract() throws Exception {
        JsonNode schema = objectMapper.readTree(manifestPath("capability.schema.json").toFile());
        assertEquals("https://json-schema.org/draft/2020-12/schema", schema.path("$schema").asText());
        assertFalse(schema.path("additionalProperties").asBoolean(true));
        assertEquals("1.0", schema.path("properties").path("schemaVersion").path("const").asText());
    }

    private CapabilityService load(String fileName) throws IOException {
        try (InputStream input = Files.newInputStream(manifestPath(fileName))) {
            return ManifestCapabilityService.load(input, objectMapper);
        }
    }

    private Set<String> enabled(CapabilityService service) {
        return service.snapshot().getCapabilities().entrySet().stream()
                .filter(entry -> entry.getValue().isEnabled())
                .map(Map.Entry::getKey)
                .collect(Collectors.toSet());
    }

    private Path manifestPath(String fileName) {
        Path current = Paths.get(System.getProperty("user.dir")).toAbsolutePath();
        while (current != null) {
            Path candidate = current.resolve(MANIFEST_DIR).resolve(fileName);
            if (Files.isRegularFile(candidate)) {
                return candidate;
            }
            current = current.getParent();
        }
        throw new IllegalStateException("Capability manifest directory not found");
    }
}
