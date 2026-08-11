package com.basiclab.iot.device.config;

import com.basiclab.iot.device.service.model.PowerModelTemplateContentValidator;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.util.HexFormat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** TD-005 1.0.41：tenant 123 隔离模板 Canary 请求资产静态合同。 */
class PowerModelCanaryAssetContractTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Test
    void canaryRequestsMatchIdentityAndFrozenSchemaWithoutRuntimeFacts() throws Exception {
        Path root = root();
        Path assets = root.resolve(".doc/技术设计/电力运维云平台/assets/td005-canary");
        JsonNode identity = MAPPER.readTree(Files.newInputStream(assets.resolve("identity-request.json")));
        JsonNode draft = MAPPER.readTree(Files.newInputStream(assets.resolve("draft-request.json")));
        JsonNode publish = MAPPER.readTree(Files.newInputStream(assets.resolve("publish-request.json")));
        JsonNode content = draft.path("content");
        JsonNode schema = MAPPER.readTree(Files.newInputStream(root.resolve(
                "DEVICE/iot-device/iot-device-biz/src/main/resources/schemas/power-model/"
                        + "easyaiot-power-model-template.schema.json")));

        assertEquals("canary-meter-123", identity.path("templateCode").asText());
        assertEquals(identity.path("templateCode"), content.path("templateCode"));
        assertEquals(identity.path("templateName"), content.path("templateName"));
        assertEquals(identity.path("deviceType"), content.path("deviceType"));
        assertEquals(identity.path("templateKind"), content.path("templateKind"));
        assertEquals("1.0.0", content.path("version").asText());
        assertEquals(1, content.path("properties").size());
        assertTrue(content.path("events").isEmpty());
        assertTrue(content.path("services").isEmpty());
        assertTrue(new PowerModelTemplateContentValidator(schema).validate(content).isEmpty());
        assertEquals("M1_CANARY_VALIDATION", publish.path("reasonCode").asText());

        String allRequests = identity.toString() + draft + publish;
        assertFalse(allRequests.contains("tenantId"));
        assertFalse(allRequests.contains("actorId"));
        assertFalse(allRequests.contains("Idempotency-Key"));
        assertFalse(allRequests.contains("draftId"));
        assertFalse(allRequests.contains("etag"));
        assertFalse(allRequests.contains("requestId"));
        assertFalse(allRequests.contains("secret"));
    }

    @Test
    void manifestHashesMatchExactBytesAndReferencesFrozenAssetCommit() throws Exception {
        Path root = root();
        Path assets = root.resolve(".doc/技术设计/电力运维云平台/assets/td005-canary");
        JsonNode manifest = MAPPER.readTree(Files.newInputStream(assets.resolve("manifest.json")));
        assertEquals("REVIEW_CANDIDATE", manifest.path("status").asText());
        assertTrue(manifest.path("gitCommit").asText().matches("[0-9a-f]{40}"));
        assertFalse("UNCOMMITTED".equals(manifest.path("gitCommit").asText()));
        assertEquals("123", manifest.path("tenantCandidate").asText());
        assertEquals("canary-meter-123", manifest.path("templateCode").asText());
        for (JsonNode file : manifest.path("files")) {
            assertEquals(file.path("sha256").asText(), sha256(assets.resolve(file.path("path").asText())));
        }
        assertEquals(manifest.path("schema").path("sha256").asText(), sha256(root.resolve(
                manifest.path("schema").path("path").asText())));
    }

    @Test
    void gatewayRoutesVersionedPowerApiWithoutRemovingOrRewritingPrefix() throws Exception {
        String gateway = Files.readString(root().resolve(
                "DEVICE/iot-gateway/src/main/resources/application.yaml"));
        int routeStart = gateway.indexOf("- id: device-power-model-api");
        int routeEnd = gateway.indexOf("## system-server", routeStart);
        assertTrue(routeStart >= 0);
        assertTrue(routeEnd > routeStart);
        String route = gateway.substring(routeStart, routeEnd);
        assertTrue(route.contains("uri: lb://device-server"));
        assertTrue(route.contains("Path=/api/v1/power/**"));
        assertFalse(route.contains("StripPrefix"));
        assertFalse(route.contains("RewritePath"));
    }

    private static String sha256(Path path) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                .digest(Files.readAllBytes(path)));
    }

    private static Path root() {
        Path current = Paths.get(System.getProperty("user.dir")).toAbsolutePath();
        while (current != null) {
            if (Files.isDirectory(current.resolve(".doc"))
                    && Files.isDirectory(current.resolve("DEVICE"))) return current;
            current = current.getParent();
        }
        throw new IllegalStateException("repository root not found");
    }
}
