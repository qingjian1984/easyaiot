package com.basiclab.iot.node.service.collector.config;

import com.basiclab.iot.node.domain.collector.config.CollectorConfigAgentStatus;
import com.basiclab.iot.node.domain.collector.config.CollectorConfigGetResponseDTO;
import com.basiclab.iot.node.domain.collector.config.CollectorConfigPutRequestDTO;
import com.basiclab.iot.node.domain.collector.config.CollectorConfigPutResponseDTO;
import com.basiclab.iot.node.security.NodeAgentRequestSigner;
import com.basiclab.iot.node.security.NodeAgentSigningKey;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CollectorAgentClientTest {

    private static final String WORKLOAD_ID = "collector-site-1001-a";
    private final ObjectMapper mapper = new ObjectMapper();
    private HttpServer server;
    private ExecutorService executor;
    private RequestCapture capture;
    private CollectorAgentClient client;
    private NodeAgentRequestSigner signer;

    @BeforeEach
    void setUp() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        capture = new RequestCapture();
        server.createContext("/", this::handle);
        executor = Executors.newSingleThreadExecutor();
        server.setExecutor(executor);
        server.start();
        signer = new NodeAgentRequestSigner(
                nodeId -> List.of(new NodeAgentSigningKey(nodeId, "k1", "01234567890123456789012345678901".getBytes(
                        StandardCharsets.UTF_8))));
        client = new CollectorAgentClient(mapper, signer, Duration.ofSeconds(2), Duration.ofSeconds(3));
    }

    @AfterEach
    void tearDown() {
        if (server != null) {
            server.stop(0);
        }
        if (executor != null) {
            executor.shutdownNow();
        }
    }

    @Test
    void signsExactPutBytesAndUsesOnlyCollectorPath() throws Exception {
        CollectorConfigPutRequestDTO request = request();

        CollectorConfigPutResponseDTO response = client.putConfig(
                new CollectorNodeEndpoint(21, "online", "127.0.0.1", server.getAddress().getPort()), request);

        assertEquals(CollectorConfigAgentStatus.ACCEPTED, response.getStatus());
        assertEquals("collector-site-1001-a", response.getWorkloadId());
        assertEquals("PUT", capture.method);
        assertEquals(CollectorAgentClient.PUT_PATH, capture.path);
        assertEquals(64, capture.headers.get(NodeAgentRequestSigner.NODE_ID_HEADER).length() > 0
                ? capture.headers.get(NodeAgentRequestSigner.SIGNATURE_HEADER).length() : 0);
        assertTrue(capture.headers.keySet().containsAll(List.of(
                NodeAgentRequestSigner.NODE_ID_HEADER,
                NodeAgentRequestSigner.KEY_ID_HEADER,
                NodeAgentRequestSigner.TIMESTAMP_HEADER,
                NodeAgentRequestSigner.NONCE_HEADER,
                NodeAgentRequestSigner.BODY_SHA256_HEADER,
                NodeAgentRequestSigner.SIGNATURE_HEADER)));
        assertEquals(sha256(capture.body), capture.headers.get(NodeAgentRequestSigner.BODY_SHA256_HEADER));
        assertEquals(mapper.writeValueAsString(request), new String(capture.body, StandardCharsets.UTF_8));
    }

    @Test
    void getsRedactedStateWithEncodedWorkloadPathAndRejectsRedirects() {
        CollectorConfigGetResponseDTO response = client.getConfig(
                new CollectorNodeEndpoint(21, "online", "127.0.0.1", server.getAddress().getPort()),
                "collector-site-1001-a");

        assertEquals("collector-site-1001-a", response.getWorkloadId());
        assertEquals("GET", capture.method);
        assertEquals("/workload/collector/collector-site-1001-a", capture.path);
        assertEquals(0, capture.body.length);
    }

    @Test
    void unknownEnvelopeFieldAndOversizedResponseFailClosed() throws Exception {
        ObjectNode unknownEnvelope = mapper.createObjectNode()
                .put("code", 0).put("msg", "success");
        unknownEnvelope.putObject("data");
        unknownEnvelope.put("unexpected", true);
        prepareResponse(200, unknownEnvelope);
        assertAgentFailure(() -> client.putConfig(node(), request()),
                CollectorAgentClient.Kind.RETRYABLE, -1, "AGENT_RESPONSE_INVALID");

        prepareResponse(200, mapper.createObjectNode().put("code", 0).put("msg", "success")
                .put("data", "x".repeat(CollectorAgentClient.MAX_RESPONSE_BYTES + 1)));
        assertAgentFailure(() -> client.putConfig(node(), request()),
                CollectorAgentClient.Kind.RETRYABLE, 200, "AGENT_RESPONSE_TOO_LARGE");
        assertTrue(capture.handlerFinished.await(2, TimeUnit.SECONDS),
                "localhost response handler must finish after bounded body close");
    }

    @Test
    void redirectsAreNotFollowedAndSecurityStatusesRemainRetryable() throws Exception {
        prepareResponse(302, envelope("AGENT_REDIRECT"));
        assertAgentFailure(() -> client.putConfig(node(), request()),
                CollectorAgentClient.Kind.RETRYABLE, 302, "AGENT_REDIRECT");
        assertTrue(capture.handlerFinished.await(2, TimeUnit.SECONDS));

        for (int status : List.of(401, 403)) {
            prepareResponse(status, envelope(status == 401 ? "AGENT_UNAUTHORIZED" : "AGENT_FORBIDDEN"));
            assertAgentFailure(() -> client.putConfig(node(), request()),
                    CollectorAgentClient.Kind.RETRYABLE, status,
                    status == 401 ? "AGENT_UNAUTHORIZED" : "AGENT_FORBIDDEN");
            assertTrue(capture.handlerFinished.await(2, TimeUnit.SECONDS));
        }
    }

    @Test
    void conflictStatusesAreDeterministicAndServerFailuresRetry() throws Exception {
        for (String code : List.of("CONFIG_VERSION_STALE", "CONFIG_VERSION_CONFLICT")) {
            prepareResponse(409, envelope(code));
            assertAgentFailure(() -> client.putConfig(node(), request()),
                    CollectorAgentClient.Kind.DETERMINISTIC, 409, code);
            assertTrue(capture.handlerFinished.await(2, TimeUnit.SECONDS));
        }

        prepareResponse(500, envelope("AGENT_HTTP_500"));
        assertAgentFailure(() -> client.putConfig(node(), request()),
                CollectorAgentClient.Kind.RETRYABLE, 500, "AGENT_HTTP_500");
        assertTrue(capture.handlerFinished.await(2, TimeUnit.SECONDS));
    }

    @Test
    void get404IsRetryableAndDoesNotFollowRedirect() throws Exception {
        prepareResponse(404, envelope("COLLECTOR_WORKLOAD_NOT_FOUND"));
        assertAgentFailure(() -> client.getConfig(node(), WORKLOAD_ID),
                CollectorAgentClient.Kind.RETRYABLE, 404, "COLLECTOR_WORKLOAD_NOT_FOUND");
        assertTrue(capture.handlerFinished.await(2, TimeUnit.SECONDS));
    }

    @Test
    void readTimeoutIsRetryableAndServerResourceIsStopped() throws Exception {
        capture.responseDelayMillis = 500L;
        prepareResponse(200, successPutEnvelope());
        client = new CollectorAgentClient(mapper, signer, Duration.ofSeconds(2), Duration.ofMillis(50));

        assertAgentFailure(() -> client.putConfig(node(), request()),
                CollectorAgentClient.Kind.RETRYABLE, -1, "AGENT_CONNECTION_FAILED");
        assertTrue(capture.handlerFinished.await(2, TimeUnit.SECONDS),
                "timed-out localhost handler must not keep the test executor alive");
    }

    @Test
    void nonzeroCodeAndMalformedEnvelopeFailClosed() throws Exception {
        ObjectNode nonzeroCode = mapper.createObjectNode().put("code", 17).put("msg", "bad");
        nonzeroCode.putObject("data");
        prepareResponse(200, nonzeroCode);
        assertAgentFailure(() -> client.putConfig(node(), request()),
                CollectorAgentClient.Kind.RETRYABLE, 200, "AGENT_RESPONSE_INVALID");

        ObjectNode unknownData = mapper.createObjectNode().put("code", 0).put("msg", "success");
        unknownData.putObject("data").put("unexpected", true);
        prepareResponse(200, unknownData);
        assertAgentFailure(() -> client.putConfig(node(), request()),
                CollectorAgentClient.Kind.RETRYABLE, 200, "AGENT_RESPONSE_INVALID");

        prepareResponse(200, null);
        capture.rawResponse = "not-json".getBytes(StandardCharsets.UTF_8);
        assertAgentFailure(() -> client.putConfig(node(), request()),
                CollectorAgentClient.Kind.RETRYABLE, -1, "AGENT_RESPONSE_INVALID");
    }

    private CollectorNodeEndpoint node() {
        return new CollectorNodeEndpoint(21, "online", "127.0.0.1", server.getAddress().getPort());
    }

    private void prepareResponse(int status, ObjectNode response) {
        capture.responseStatus = status;
        capture.response = response;
        capture.rawResponse = null;
        capture.handlerFinished = new CountDownLatch(1);
    }

    private ObjectNode envelope(String msg) {
        ObjectNode root = mapper.createObjectNode().put("code", 0).put("msg", msg);
        root.putObject("data");
        return root;
    }

    private ObjectNode successPutEnvelope() {
        ObjectNode response = mapper.createObjectNode().put("code", 0).put("msg", "success");
        response.putObject("data").put("status", "ACCEPTED")
                .put("workloadId", WORKLOAD_ID).put("configVersion", 1).put("payloadSha256", requestHash());
        return response;
    }

    private static void assertAgentFailure(Executable operation,
                                            CollectorAgentClient.Kind kind,
                                            int httpStatus,
                                            String stableCode) {
        CollectorAgentClient.CollectorAgentException error = assertThrows(
                CollectorAgentClient.CollectorAgentException.class, operation);
        assertEquals(kind, error.getKind());
        assertEquals(httpStatus, error.getHttpStatus());
        assertEquals(stableCode, error.getStableCode());
    }

    private CollectorConfigPutRequestDTO request() {
        CollectorConfigPutRequestDTO request = new CollectorConfigPutRequestDTO();
        request.setWorkloadId("collector-site-1001-a");
        request.setConfigVersion(1L);
        request.setSchemaVersion("1.1");
        request.setCanonicalizationVersion("jcs-rfc8785-v1");
        request.setPayloadCanonical("{\"configVersion\":1}");
        request.setPayloadSha256(sha256(request.getPayloadCanonical().getBytes(StandardCharsets.UTF_8)));
        request.setCanonicalLengthBytes((long) request.getPayloadCanonical().getBytes(StandardCharsets.UTF_8).length);
        return request;
    }

    private void handle(HttpExchange exchange) throws IOException {
        try {
            capture.method = exchange.getRequestMethod();
            capture.path = exchange.getRequestURI().getRawPath();
            capture.body = exchange.getRequestBody().readAllBytes();
            capture.headers = new java.util.HashMap<>();
            exchange.getRequestHeaders().forEach((key, values) -> {
                String actual = values.get(0);
                for (String expected : List.of(NodeAgentRequestSigner.NODE_ID_HEADER,
                        NodeAgentRequestSigner.KEY_ID_HEADER, NodeAgentRequestSigner.TIMESTAMP_HEADER,
                        NodeAgentRequestSigner.NONCE_HEADER, NodeAgentRequestSigner.BODY_SHA256_HEADER,
                        NodeAgentRequestSigner.SIGNATURE_HEADER)) {
                    if (expected.equalsIgnoreCase(key)) {
                        capture.headers.put(expected, actual);
                    }
                }
            });
            if (capture.responseDelayMillis > 0) {
                try {
                    Thread.sleep(capture.responseDelayMillis);
                } catch (InterruptedException error) {
                    Thread.currentThread().interrupt();
                    return;
                }
            }
            ObjectNode response = capture.response;
            byte[] raw = capture.rawResponse == null ? mapper.writeValueAsBytes(
                    response == null ? ("PUT".equals(capture.method) ? successPutEnvelope() : successGetEnvelope())
                            : response) : capture.rawResponse;
            exchange.sendResponseHeaders(capture.responseStatus, raw.length);
            exchange.getResponseBody().write(raw);
        } finally {
            exchange.close();
            capture.handlerFinished.countDown();
        }
    }

    private ObjectNode successGetEnvelope() {
        ObjectNode response = mapper.createObjectNode().put("code", 0).put("msg", "success");
        ObjectNode data = response.putObject("data").put("workloadId", WORKLOAD_ID);
        state(data.putObject("desired"));
        state(data.putObject("active"));
        ObjectNode observed = data.putObject("observed");
        observed.put("workloadId", WORKLOAD_ID).put("status", "APPLIED")
                .put("configVersion", 1).put("payloadSha256", requestHash())
                .put("observedAt", "2026-08-17T10:00:00Z").putNull("errorCode");
        return response;
    }

    private void state(ObjectNode state) {
        state.put("present", true).put("schemaVersion", "1.1").put("configVersion", 1)
                .put("payloadSha256", requestHash())
                .put("canonicalLengthBytes", (long) "{\"configVersion\":1}".getBytes(StandardCharsets.UTF_8).length);
    }

    private String requestHash() {
        return sha256("{\"configVersion\":1}".getBytes(StandardCharsets.UTF_8));
    }

    private static String sha256(byte[] bytes) {
        try {
            return java.util.HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (Exception error) {
            throw new AssertionError(error);
        }
    }

    private static final class RequestCapture {
        private String method;
        private String path;
        private byte[] body = new byte[0];
        private Map<String, String> headers = Map.of();
        private ObjectNode response;
        private byte[] rawResponse;
        private int responseStatus = 200;
        private long responseDelayMillis;
        private CountDownLatch handlerFinished = new CountDownLatch(1);
    }
}
