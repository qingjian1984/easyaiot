package com.basiclab.iot.node.service.collector.config;

import com.basiclab.iot.node.domain.collector.config.CollectorConfigGetResponseDTO;
import com.basiclab.iot.node.domain.collector.config.CollectorConfigAgentStatus;
import com.basiclab.iot.node.domain.collector.config.CollectorConfigObservedSummaryDTO;
import com.basiclab.iot.node.domain.collector.config.CollectorConfigPutRequestDTO;
import com.basiclab.iot.node.domain.collector.config.CollectorConfigPutResponseDTO;
import com.basiclab.iot.node.domain.collector.config.CollectorConfigStateSummaryDTO;
import com.basiclab.iot.node.enums.NodeStatusEnum;
import com.basiclab.iot.node.security.FileNodeAgentSigningKeyProvider;
import com.basiclab.iot.node.security.NodeAgentRequestSigner;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.net.URI;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.nio.file.StandardOpenOption;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * OPEN03-08 node stage.  It deliberately uses the production dispatch service,
 * signer and HTTP Agent client; only release and node authority are file-backed
 * fakes owned by this test.
 */
class CollectorOpen03CombinedStageTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final String WORKLOAD_ID = "collector-open03-e2e-a";
    private static final String TENANT_ID = "100";
    private static final String NODE_ID = "21";
    private static final String HOST = "127.0.0.1";
    private static final String ONLINE = NodeStatusEnum.ONLINE.getStatus();

    @Test
    void runConfiguredNodeStage() throws Exception {
        String stage = System.getenv("OPEN03_NODE_STAGE");
        if (stage == null || stage.isBlank()) {
            runStandaloneDispatchContract();
            return;
        }
        Path releaseState = Path.of(required("OPEN03_RELEASE_STATE")).toAbsolutePath().normalize();
        Path trace = Path.of(required("OPEN03_TRACE_FILE")).toAbsolutePath().normalize();
        Path fixture = Path.of(required(stage.endsWith("v1") ? "OPEN03_FIXTURE_V1" : "OPEN03_FIXTURE_V2"))
                .toAbsolutePath().normalize();
        String chain = required("OPEN03_CHAIN");
        int agentPort = Integer.parseInt(required("OPEN03_AGENT_PORT"));
        Path keyFile = Path.of(required("OPEN03_KEY_FILE")).toAbsolutePath().normalize();
        Path responseFile = Path.of(required("OPEN03_RESPONSE_FILE")).toAbsolutePath().normalize();

        FileRelease release = new FileRelease(releaseState, fixture, trace, chain);
        if (stage.equals("dispatch-v1")) {
            release.ensurePublished("9003001", 1L);
        } else if (stage.equals("dispatch-v2")) {
            release.ensurePublished("9003002", 2L);
        } else if (!stage.equals("reconcile-v1") && !stage.equals("reconcile-v2")) {
            fail("unsupported OPEN03_NODE_STAGE");
        }

        NodeAgentRequestSigner signer = new NodeAgentRequestSigner(
                new FileNodeAgentSigningKeyProvider(keyFile));
        CollectorAgentClient agentClient = new CollectorAgentClient(
                MAPPER, signer, Duration.ofSeconds(3), Duration.ofSeconds(10));
        CollectorConfigDispatchService service = new CollectorConfigDispatchService(
                release,
                nodeId -> nodeId == Long.parseLong(NODE_ID)
                        ? Optional.of(new CollectorNodeEndpoint(nodeId, ONLINE, HOST, agentPort))
                        : Optional.empty(),
                agentClient);

        CollectorConfigDispatchBatchResult batch = service.dispatchPending(1);
        assertEquals(1, batch.getOutcomes().size());
        CollectorConfigDispatchOutcome outcome = batch.getOutcomes().get(0);
        if (stage.startsWith("dispatch-")) {
            assertEquals(CollectorConfigDispatchStatus.RETRY, outcome.getStatus());
            assertEquals("AGENT_OBSERVED_NOT_READY", outcome.getStableCode());
            CollectorConfigGetResponseDTO state = agentClient.getConfig(
                    new CollectorNodeEndpoint(Long.parseLong(NODE_ID), ONLINE, HOST, agentPort), WORKLOAD_ID);
            assertNotNull(state.getDesired());
            assertEquals(Long.valueOf(stage.equals("dispatch-v1") ? 1L : 2L),
                    state.getDesired().getConfigVersion());
            if (stage.equals("dispatch-v1")) {
                assertNull(state.getActive());
                assertNull(state.getObserved());
            } else {
                byte[] v1 = Files.readAllBytes(Path.of(required("OPEN03_FIXTURE_V1")));
                String v1Hash = sha256(v1);
                assertNotNull(state.getActive());
                assertEquals(Long.valueOf(1L), state.getActive().getConfigVersion());
                assertEquals(v1Hash, state.getActive().getPayloadSha256());
                assertNotNull(state.getObserved());
                assertEquals(CollectorConfigAgentStatus.APPLIED, state.getObserved().getStatus());
                assertEquals(Long.valueOf(1L), state.getObserved().getConfigVersion());
                assertEquals(v1Hash, state.getObserved().getPayloadSha256());
            }
            appendTrace(trace, chain, "DESIRED_WRITTEN");
            appendResponseSummary(responseFile, stage, state);
            assertNoCanonicalInState(releaseState);
            return;
        }

        boolean failure = stage.equals("reconcile-v2");
        assertEquals(failure ? CollectorConfigDispatchStatus.FAILED : CollectorConfigDispatchStatus.APPLIED,
                outcome.getStatus());
        assertEquals(failure ? "COLLECTOR_CONFIG_APPLY_FAILED" : "APPLIED", outcome.getStableCode());
        JsonNode persisted = MAPPER.readTree(Files.readAllBytes(releaseState));
        assertEquals(failure ? "FAILED" : "APPLIED", persisted.path("status").asText());
        CollectorConfigGetResponseDTO state = agentClient.getConfig(
                new CollectorNodeEndpoint(Long.parseLong(NODE_ID), ONLINE, HOST, agentPort), WORKLOAD_ID);
        appendResponseSummary(responseFile, stage, state);
        assertNoCanonicalInState(releaseState);
    }

    /**
     * The repository runner supplies the real Flask Agent and invokes this
     * class six times.  The frozen Maven matrix also invokes the class
     * directly, so that path runs the production dispatch service against
     * closed in-memory ports instead of failing on absent runner variables or
     * pretending to have completed an E2E chain.
     */
    private static void runStandaloneDispatchContract() throws Exception {
        byte[] canonical = "{\"configVersion\":1,\"schemaVersion\":\"1.1\",\"workloadId\":\"collector-open03-e2e-a\"}"
                .getBytes(StandardCharsets.UTF_8);
        String hash = sha256(canonical);
        CollectorConfigReleasePending pending = new CollectorConfigReleasePending(
                "9003999", TENANT_ID, NODE_ID, WORKLOAD_ID, "1", "1.1",
                "jcs-rfc8785-v1", hash, (long) canonical.length, "2026-08-17T00:00:00Z");
        StandaloneRelease release = new StandaloneRelease(pending,
                new CollectorConfigReleaseDetail(
                        pending.getReleaseId(), pending.getTenantId(), pending.getNodeId(),
                        pending.getWorkloadId(), pending.getConfigVersion(), pending.getSchemaVersion(),
                        pending.getCanonicalizationVersion(), new String(canonical, StandardCharsets.UTF_8),
                        pending.getPayloadSha256(), pending.getCanonicalLengthBytes(), pending.getPublishedAt()));
        StandaloneAgent agent = new StandaloneAgent(pending);
        CollectorConfigDispatchService service = new CollectorConfigDispatchService(
                release,
                nodeId -> Optional.of(new CollectorNodeEndpoint(nodeId, ONLINE, HOST, 9100)),
                agent);

        CollectorConfigDispatchOutcome outcome = service.dispatchOne(pending);
        assertEquals(CollectorConfigDispatchStatus.APPLIED, outcome.getStatus());
        assertEquals("APPLIED", outcome.getStableCode());
        assertEquals(List.of(
                CollectorConfigReleaseObservedReport.Status.AGENT_ACCEPTED,
                CollectorConfigReleaseObservedReport.Status.APPLIED), release.statuses);
        assertTrue(agent.putCalled);
    }

    private static void appendResponseSummary(Path responseFile, String stage,
                                               CollectorConfigGetResponseDTO state) throws Exception {
        ObjectNode summary = MAPPER.createObjectNode();
        summary.put("stage", stage);
        summary.set("state", MAPPER.valueToTree(state));
        Files.createDirectories(responseFile.getParent());
        byte[] line = (MAPPER.writeValueAsString(summary) + "\n").getBytes(StandardCharsets.UTF_8);
        Files.write(responseFile, line, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
    }

    private static void assertNoCanonicalInState(Path state) throws Exception {
        String raw = Files.readString(state, StandardCharsets.UTF_8);
        assertTrue(!raw.contains("payloadCanonical") && !raw.contains("canonicalPayload"),
                "fake release state must not persist canonical payload");
    }

    private static void appendTrace(Path trace, String chain, String event) throws Exception {
        ObjectNode root;
        if (Files.exists(trace)) {
            root = (ObjectNode) MAPPER.readTree(Files.readAllBytes(trace));
        } else {
            Files.createDirectories(trace.getParent());
            root = MAPPER.createObjectNode();
        }
        ArrayNode events = root.withArray(chain);
        boolean present = false;
        for (JsonNode value : events) {
            if (event.equals(value.asText())) {
                present = true;
                break;
            }
        }
        if (!present) {
            events.add(event);
        }
        Files.write(trace, MAPPER.writeValueAsBytes(root));
    }

    private static String required(String name) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(name + " is required");
        }
        return value;
    }

    private static String sha256(byte[] bytes) throws Exception {
        return java.util.HexFormat.of().formatHex(
                java.security.MessageDigest.getInstance("SHA-256").digest(bytes));
    }

    private static final class StandaloneRelease implements CollectorConfigReleaseClientPort {
        private final CollectorConfigReleasePending pending;
        private final CollectorConfigReleaseDetail detail;
        private final List<CollectorConfigReleaseObservedReport.Status> statuses = new java.util.ArrayList<>();

        private StandaloneRelease(CollectorConfigReleasePending pending, CollectorConfigReleaseDetail detail) {
            this.pending = pending;
            this.detail = detail;
        }

        @Override
        public List<CollectorConfigReleasePending> listPending(int limit) {
            return List.of(pending);
        }

        @Override
        public Optional<CollectorConfigReleaseDetail> getDetail(String releaseId) {
            return Optional.of(detail);
        }

        @Override
        public CollectorConfigObservedResponse reportObserved(CollectorConfigReleaseObservedReport report) {
            statuses.add(report.getStatus());
            return new CollectorConfigObservedResponse(report.getReleaseId(), report.getStatus(),
                    true, report.getStatus() != CollectorConfigReleaseObservedReport.Status.AGENT_ACCEPTED, false);
        }
    }

    private static final class StandaloneAgent implements CollectorAgentPort {
        private final CollectorConfigReleasePending pending;
        private boolean putCalled;

        private StandaloneAgent(CollectorConfigReleasePending pending) {
            this.pending = pending;
        }

        @Override
        public CollectorConfigPutResponseDTO putConfig(CollectorNodeEndpoint node,
                                                        CollectorConfigPutRequestDTO request) {
            putCalled = true;
            CollectorConfigPutResponseDTO response = new CollectorConfigPutResponseDTO();
            response.setStatus(CollectorConfigAgentStatus.ACCEPTED);
            response.setWorkloadId(request.getWorkloadId());
            response.setConfigVersion(request.getConfigVersion());
            response.setPayloadSha256(request.getPayloadSha256());
            return response;
        }

        @Override
        public CollectorConfigGetResponseDTO getConfig(CollectorNodeEndpoint node, String workloadId) {
            CollectorConfigStateSummaryDTO summary = new CollectorConfigStateSummaryDTO();
            summary.setPresent(true);
            summary.setSchemaVersion(pending.getSchemaVersion());
            summary.setConfigVersion(Long.parseLong(pending.getConfigVersion()));
            summary.setPayloadSha256(pending.getPayloadSha256());
            summary.setCanonicalLengthBytes(pending.getCanonicalLengthBytes());
            CollectorConfigObservedSummaryDTO observed = new CollectorConfigObservedSummaryDTO();
            observed.setWorkloadId(workloadId);
            observed.setStatus(CollectorConfigAgentStatus.APPLIED);
            observed.setConfigVersion(summary.getConfigVersion());
            observed.setPayloadSha256(summary.getPayloadSha256());
            CollectorConfigGetResponseDTO response = new CollectorConfigGetResponseDTO();
            response.setWorkloadId(workloadId);
            response.setDesired(summary);
            response.setActive(summary);
            response.setObserved(observed);
            return response;
        }
    }

    /** File-backed fake for the already-tested iot-device release/CAS boundary. */
    private static final class FileRelease implements CollectorConfigReleaseClientPort {
        private final Path statePath;
        private final Path fixture;
        private final Path trace;
        private final String chain;

        private FileRelease(Path statePath, Path fixture, Path trace, String chain) {
            this.statePath = statePath;
            this.fixture = fixture;
            this.trace = trace;
            this.chain = chain;
        }

        void ensurePublished(String releaseId, long version) throws Exception {
            ObjectNode state = MAPPER.createObjectNode();
            state.put("releaseId", releaseId);
            state.put("tenantId", TENANT_ID);
            state.put("nodeId", NODE_ID);
            state.put("workloadId", WORKLOAD_ID);
            state.put("configVersion", Long.toString(version));
            state.put("schemaVersion", "1.1");
            state.put("canonicalizationVersion", "jcs-rfc8785-v1");
            byte[] canonical = Files.readAllBytes(fixture);
            state.put("payloadSha256", sha256(canonical));
            state.put("canonicalLengthBytes", canonical.length);
            state.put("publishedAt", "2026-08-17T00:00:00Z");
            state.put("status", "PUBLISHED");
            state.put("agentAccepted", false);
            if (Files.exists(statePath)) {
                JsonNode old = MAPPER.readTree(Files.readAllBytes(statePath));
                if (version == old.path("configVersion").asLong(-1)
                        && releaseId.equals(old.path("releaseId").asText())
                        && "PUBLISHED".equals(old.path("status").asText())) {
                    return;
                }
            }
            write(state);
            appendTrace(trace, chain, "PUBLISHED");
        }

        @Override
        public List<CollectorConfigReleasePending> listPending(int limit) {
            try {
                ObjectNode state = read();
                if (!"PUBLISHED".equals(state.path("status").asText())) {
                    return List.of();
                }
                return List.of(pending(state));
            } catch (Exception error) {
                throw new IllegalStateException("fake release unavailable");
            }
        }

        @Override
        public Optional<CollectorConfigReleaseDetail> getDetail(String releaseId) {
            try {
                ObjectNode state = read();
                if (!"PUBLISHED".equals(state.path("status").asText())
                        || !releaseId.equals(state.path("releaseId").asText())) {
                    return Optional.empty();
                }
                byte[] canonical = Files.readAllBytes(fixture);
                return Optional.of(new CollectorConfigReleaseDetail(
                        state.path("releaseId").asText(), state.path("tenantId").asText(),
                        state.path("nodeId").asText(), state.path("workloadId").asText(),
                        state.path("configVersion").asText(), state.path("schemaVersion").asText(),
                        state.path("canonicalizationVersion").asText(),
                        new String(canonical, StandardCharsets.UTF_8),
                        state.path("payloadSha256").asText(), state.path("canonicalLengthBytes").asLong(),
                        state.path("publishedAt").asText()));
            } catch (Exception error) {
                throw new IllegalStateException("fake release detail unavailable");
            }
        }

        @Override
        public CollectorConfigObservedResponse reportObserved(CollectorConfigReleaseObservedReport report) {
            try {
                ObjectNode state = read();
                if (!sameIdentity(state, report)) {
                    return new CollectorConfigObservedResponse(report.getReleaseId(), report.getStatus(),
                            false, false, false);
                }
                String current = state.path("status").asText();
                if (report.getStatus() == CollectorConfigReleaseObservedReport.Status.AGENT_ACCEPTED) {
                    if (!"PUBLISHED".equals(current)) {
                        return new CollectorConfigObservedResponse(report.getReleaseId(), report.getStatus(),
                                false, false, false);
                    }
                    boolean idempotent = state.path("agentAccepted").asBoolean(false);
                    state.put("agentAccepted", true);
                    write(state);
                    if (!idempotent) {
                        appendTrace(trace, chain, "AGENT_ACCEPTED");
                    }
                    return new CollectorConfigObservedResponse(report.getReleaseId(), report.getStatus(),
                            true, false, idempotent);
                }
                String terminal = report.getStatus() == CollectorConfigReleaseObservedReport.Status.APPLIED
                        ? "APPLIED" : "FAILED";
                if (terminal.equals(current)) {
                    return new CollectorConfigObservedResponse(report.getReleaseId(), report.getStatus(),
                            true, true, true);
                }
                if (!state.path("agentAccepted").asBoolean(false) || !"PUBLISHED".equals(current)) {
                    return new CollectorConfigObservedResponse(report.getReleaseId(), report.getStatus(),
                            false, false, false);
                }
                state.put("status", terminal);
                if (report.getErrorCode() != null) {
                    state.put("errorCode", report.getErrorCode());
                }
                write(state);
                appendTrace(trace, chain, report.getStatus() == CollectorConfigReleaseObservedReport.Status.APPLIED
                        ? "DEVICE_APPLIED" : "DEVICE_FAILED");
                return new CollectorConfigObservedResponse(report.getReleaseId(), report.getStatus(),
                        true, true, false);
            } catch (Exception error) {
                throw new IllegalStateException("fake release report unavailable");
            }
        }

        private CollectorConfigReleasePending pending(ObjectNode state) {
            return new CollectorConfigReleasePending(
                    state.path("releaseId").asText(), state.path("tenantId").asText(),
                    state.path("nodeId").asText(), state.path("workloadId").asText(),
                    state.path("configVersion").asText(), state.path("schemaVersion").asText(),
                    state.path("canonicalizationVersion").asText(), state.path("payloadSha256").asText(),
                    state.path("canonicalLengthBytes").asLong(), state.path("publishedAt").asText());
        }

        private boolean sameIdentity(ObjectNode state, CollectorConfigReleaseObservedReport report) {
            return state.path("releaseId").asText().equals(report.getReleaseId())
                    && state.path("tenantId").asText().equals(report.getTenantId())
                    && state.path("nodeId").asText().equals(report.getNodeId())
                    && state.path("workloadId").asText().equals(report.getWorkloadId())
                    && state.path("configVersion").asText().equals(report.getConfigVersion())
                    && state.path("payloadSha256").asText().equals(report.getPayloadSha256());
        }

        private ObjectNode read() throws Exception {
            if (!Files.isRegularFile(statePath)) {
                throw new IllegalStateException("fake release state missing");
            }
            JsonNode node = MAPPER.readTree(Files.readAllBytes(statePath));
            if (!(node instanceof ObjectNode object)) {
                throw new IllegalStateException("fake release state invalid");
            }
            return object;
        }

        private void write(ObjectNode state) throws Exception {
            Files.createDirectories(statePath.getParent());
            Files.write(statePath, MAPPER.writeValueAsBytes(state));
        }

        private static String sha256(byte[] bytes) throws Exception {
            return java.util.HexFormat.of().formatHex(
                    java.security.MessageDigest.getInstance("SHA-256").digest(bytes));
        }

    }
}
