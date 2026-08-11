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
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** TD-005 1.0.44：tenant 123 隔离模板 Canary 资产与只读前检静态合同。 */
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

    @Test
    void tenant123PreflightRemainsReadOnlyAndChecksExactCanaryScope() throws Exception {
        Path directory = root().resolve(".scripts/postgresql/td005-canary-tenant123");
        String identity = Files.readString(directory.resolve("preflight_canary_identity.sql"));
        String tenantData = Files.readString(directory.resolve("preflight_canary_tenant_data.sql"));
        String wrapper = Files.readString(directory.resolve("run_readonly_preflight.ps1"));

        for (String sql : List.of(identity, tenantData)) {
            assertTrue(sql.contains("BEGIN TRANSACTION READ ONLY;"));
            assertTrue(sql.contains("ROLLBACK;"));
            assertFalse(sql.matches("(?is).*\\bCOMMIT\\s*;.*"));
            assertFalse(sql.contains("tenant_id = 122"));
        }
        assertTrue(identity.contains("id = 123 AND name = 'codex测试'"));
        assertTrue(identity.contains("id = 132 AND tenant_id = 123 AND username = 'aotemane'"));
        assertTrue(identity.contains("id = 112 AND tenant_id = 123"));
        assertTrue(identity.contains("menu_id IN (3900, 3901, 3902)"));
        assertTrue(identity.contains("menu_id IN (3903, 3904, 3905, 3906)"));

        for (String table : List.of(
                "product", "device", "power_model_template", "power_model_template_version",
                "power_model_member_index", "power_product_model_binding", "power_model_audit",
                "power_model_release_outbox", "power_model_event_inbox", "iot_collector_config_release",
                "power_model_template_reference_mark", "power_model_coordination_audit",
                "collector_workload_binding_projection", "power_idempotency_record")) {
            assertTrue(tenantData.contains("FROM public." + table + " WHERE tenant_id = 123"), table);
        }
        assertTrue(wrapper.contains("preflight_canary_identity.sql"));
        assertTrue(wrapper.contains("preflight_canary_tenant_data.sql"));
        assertTrue(wrapper.contains("[Text.UTF8Encoding]::new($false)"));
        assertTrue(wrapper.contains("TD005_CANARY_PREFLIGHT_NOT_READ_ONLY"));
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
