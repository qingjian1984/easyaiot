package com.basiclab.iot.device.service.collector;

import com.basiclab.iot.common.security.internal.InternalServiceAuthNonceStore;
import com.basiclab.iot.common.security.internal.InternalServiceAuthRoute;
import com.basiclab.iot.common.security.internal.InternalServiceAuthSigner;
import com.basiclab.iot.common.security.internal.InternalServiceAuthVerifier;
import com.basiclab.iot.common.security.internal.InternalServiceKeyProvider;
import com.basiclab.iot.device.CollectorConfigReleaseInternalApi;
import com.basiclab.iot.device.domain.collector.dto.CollectorConfigReleaseDetailDTO;
import com.basiclab.iot.device.domain.collector.dto.CollectorConfigReleaseObservedRequestDTO;
import com.basiclab.iot.device.domain.collector.dto.CollectorConfigReleaseObservedStatus;
import com.basiclab.iot.device.domain.collector.dto.CollectorConfigReleasePendingDTO;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** OPEN03-03 冻结：接口路径、服务身份、租户边界与 observed 业务合同。 */
class CollectorConfigReleaseInternalApiContractTest {

    private static final String HASH = "a".repeat(64);
    private static final Clock CLOCK = Clock.fixed(Instant.ofEpochSecond(1_755_000_000L),
            ZoneOffset.UTC);

    @Test
    void apiRoutesAndBigIntIdsAreStableStrings() throws Exception {
        assertEquals(CollectorConfigReleaseInternalApi.PENDING_PATH,
                CollectorConfigReleaseInternalApi.class.getMethod("listPending", Integer.class)
                        .getAnnotation(GetMapping.class).value()[0]);
        assertEquals(CollectorConfigReleaseInternalApi.DETAIL_PATH,
                CollectorConfigReleaseInternalApi.class.getMethod("getDetail", String.class)
                        .getAnnotation(GetMapping.class).value()[0]);
        assertEquals(CollectorConfigReleaseInternalApi.OBSERVED_PATH,
                CollectorConfigReleaseInternalApi.class.getMethod("reportObserved", String.class,
                        CollectorConfigReleaseObservedRequestDTO.class)
                        .getAnnotation(PostMapping.class).value()[0]);

        CollectorConfigReleasePendingDTO pending = new CollectorConfigReleasePendingDTO();
        pending.setReleaseId("9223372036854770000");
        pending.setTenantId("9000000000000000001");
        pending.setNodeId("9000000000000000002");
        pending.setConfigVersion("9000000000000000003");
        JsonNode json = new ObjectMapper().readTree(new ObjectMapper().writeValueAsBytes(pending));
        assertTrue(json.path("releaseId").isTextual());
        assertTrue(json.path("tenantId").isTextual());
        assertTrue(json.path("nodeId").isTextual());
        assertTrue(json.path("configVersion").isTextual());
    }

    @Test
    void internalAuthRoutesAreRegisteredAndDisabledByDefault() throws IOException {
        String yaml;
        try (InputStream input = getClass().getResourceAsStream("/application.yaml")) {
            assertNotNull(input);
            yaml = new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
        assertTrue(yaml.contains(
                "enabled: ${EASYAIOT_INTERNAL_SERVICE_AUTH_ENABLED:false}"));
        assertTrue(yaml.contains(
                "- method: GET\n          path: " + CollectorConfigReleaseInternalApi.PENDING_PATH));
        assertTrue(yaml.contains(
                "- method: GET\n          path: " + CollectorConfigReleaseInternalApi.DETAIL_PATH));
        assertTrue(yaml.contains(
                "- method: POST\n          path: " + CollectorConfigReleaseInternalApi.OBSERVED_PATH));
    }

    @Test
    void pendingLimitAndDetailPreserveCanonicalArtifact() {
        FakeRepository repository = new FakeRepository();
        CollectorConfigReleaseInternalRepository.ReleaseRecord row = new CollectorConfigReleaseInternalRepository.ReleaseRecord(
                9001L, 9002L, 9003L, "collector-01", 12L, "1.1",
                "jcs-rfc8785-v1", "{\"productIdentification\":\"meter\"}", HASH,
                36L, "2026-08-17T10:00:00+08:00");
        repository.rows.add(row);
        CollectorConfigReleaseInternalService service = new CollectorConfigReleaseInternalService(
                repository, (fact, outcome) -> { });

        assertThrows(CollectorConfigReleaseInternalException.class,
                () -> service.listPending(0));
        assertThrows(CollectorConfigReleaseInternalException.class,
                () -> service.listPending(101));
        List<CollectorConfigReleasePendingDTO> pending = service.listPending(1);
        assertEquals("9001", pending.get(0).getReleaseId());
        CollectorConfigReleaseDetailDTO detail = service.detail("9001");
        assertEquals(row.payloadCanonical(), detail.getPayloadCanonical());
        assertEquals(row.payloadSha256(), detail.getPayloadSha256());
        assertEquals(row.canonicalLengthBytes(), detail.getCanonicalLengthBytes());
    }

    @Test
    void observedAcceptedIsNotAppliedAndTenantMismatchIsFailClosed() {
        FakeRepository repository = new FakeRepository();
        RecordingFactRecorder recorder = new RecordingFactRecorder();
        CollectorConfigReleaseInternalService service = new CollectorConfigReleaseInternalService(
                repository, recorder);

        CollectorConfigReleaseObservedRequestDTO accepted = observed(
                "9001", "9002", "9003", "collector-01", "12", HASH,
                CollectorConfigReleaseObservedStatus.AGENT_ACCEPTED);
        CollectorConfigReleaseInternalRepository.ObservedCasResult acceptedResult =
                new CollectorConfigReleaseInternalRepository.ObservedCasResult(
                        CollectorConfigReleaseInternalRepository.Outcome.AGENT_ACCEPTED);
        repository.nextResult = acceptedResult;
        assertFalse(service.observe("9001", accepted).isTerminal());
        assertEquals(1, recorder.count.get());
        assertEquals(CollectorConfigReleaseInternalRepository.Outcome.AGENT_ACCEPTED.name(),
                recorder.outcome);

        CollectorConfigReleaseObservedRequestDTO forgedTenant = observed(
                "9001", "9004", "9003", "collector-01", "12", HASH,
                CollectorConfigReleaseObservedStatus.APPLIED);
        repository.nextResult = new CollectorConfigReleaseInternalRepository.ObservedCasResult(
                CollectorConfigReleaseInternalRepository.Outcome.MISMATCH);
        assertFalse(service.observe("9001", forgedTenant).isAccepted());
        assertEquals(2, recorder.count.get());
    }

    @Test
    void adr018VerifierRequiresIotNodeAndDoesNotTrustTenantHeader() {
        byte[] key = new byte[32];
        for (int i = 0; i < key.length; i++) key[i] = (byte) (i + 1);
        InternalServiceKeyProvider keys = (service, keyId) -> Optional.of(key);
        InternalServiceAuthNonceStore nonces = new InternalServiceAuthNonceStore() {
            private final List<String> claimed = new ArrayList<>();

            @Override
            public boolean claim(String serviceId, String nonce, long ttlSeconds) {
                if (claimed.contains(serviceId + ":" + nonce)) return false;
                claimed.add(serviceId + ":" + nonce);
                return true;
            }
        };
        List<InternalServiceAuthRoute> routes = CollectorConfigReleaseInternalAuth.requiredRoutes();
        InternalServiceAuthVerifier verifier = new InternalServiceAuthVerifier(
                keys, nonces, routes, CLOCK, 300, 600);
        CollectorConfigReleaseInternalAuth auth = new CollectorConfigReleaseInternalAuth(verifier);
        InternalServiceAuthSigner pendingSigner = new InternalServiceAuthSigner(keys, "iot-node", "k1",
                CLOCK, () -> "0123456789abcdef0123456789abcdef");
        String path = CollectorConfigReleaseInternalApi.PENDING_PATH + "?limit=1";
        Map<String, String> signed = pendingSigner.sign("GET", path, new byte[0]);
        MockHttpServletRequest request = request("GET", path, signed);
        request.addHeader("X-Tenant-Id", "9002");
        assertEquals("iot-node", auth.verify(request, new byte[0]).serviceId());

        String detailPath = CollectorConfigReleaseInternalApi.BASE_PATH + "/9001";
        InternalServiceAuthSigner detailSigner = new InternalServiceAuthSigner(keys, "iot-node", "k1",
                CLOCK, () -> "1123456789abcdef0123456789abcdef");
        Map<String, String> detailSigned = detailSigner.sign("GET", detailPath, new byte[0]);
        assertEquals("iot-node", auth.verify(request("GET", detailPath, detailSigned), new byte[0])
                .serviceId());

        String observedPath = CollectorConfigReleaseInternalApi.BASE_PATH + "/9001/observed";
        byte[] observedBody = ("{\"releaseId\":\"9001\",\"tenantId\":\"9002\","
                + "\"nodeId\":\"9003\",\"workloadId\":\"collector-01\","
                + "\"configVersion\":\"12\",\"payloadSha256\":\"" + HASH + "\","
                + "\"status\":\"AGENT_ACCEPTED\","
                + "\"observedAt\":\"2026-08-17T10:00:00+08:00\"}")
                .getBytes(java.nio.charset.StandardCharsets.UTF_8);
        InternalServiceAuthSigner observedSigner = new InternalServiceAuthSigner(keys, "iot-node", "k1",
                CLOCK, () -> "2223456789abcdef0123456789abcdef");
        Map<String, String> observedSigned = observedSigner.sign("POST", observedPath, observedBody);
        assertEquals("iot-node", auth.verify(request("POST", observedPath, observedSigned), observedBody)
                .serviceId());
        com.basiclab.iot.common.security.internal.InternalServiceAuthException bodyMismatch =
                assertThrows(com.basiclab.iot.common.security.internal.InternalServiceAuthException.class,
                        () -> auth.verify(request("POST", observedPath, observedSigned),
                                "tampered".getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        assertEquals("SERVICE_AUTH_BODY_HASH_MISMATCH", bodyMismatch.getCode());

        String outOfBoundsPath = CollectorConfigReleaseInternalApi.BASE_PATH + "/9001/extra";
        InternalServiceAuthSigner outOfBoundsSigner = new InternalServiceAuthSigner(keys, "iot-node", "k1",
                CLOCK, () -> "3323456789abcdef0123456789abcdef");
        com.basiclab.iot.common.security.internal.InternalServiceAuthException routeMismatch =
                assertThrows(com.basiclab.iot.common.security.internal.InternalServiceAuthException.class,
                        () -> auth.verify(request("GET", outOfBoundsPath,
                                outOfBoundsSigner.sign("GET", outOfBoundsPath, new byte[0])), new byte[0]));
        assertEquals("SERVICE_AUTH_UNKNOWN_CALLER", routeMismatch.getCode());

        InternalServiceAuthVerifier wrongVerifier = new InternalServiceAuthVerifier(
                keys, (serviceId, nonce, ttl) -> true, routes, CLOCK, 300, 600);
        CollectorConfigReleaseInternalAuth wrongAuth = new CollectorConfigReleaseInternalAuth(wrongVerifier);
        InternalServiceAuthSigner wrongSigner = new InternalServiceAuthSigner(keys, "user-service", "k1",
                CLOCK, () -> "abcdef0123456789abcdef0123456789");
        MockHttpServletRequest wrongRequest = request("GET", path,
                wrongSigner.sign("GET", path, new byte[0]));
        wrongRequest.addHeader("X-Tenant-Id", "9002");
        assertThrows(com.basiclab.iot.common.security.internal.InternalServiceAuthException.class,
                () -> wrongAuth.verify(wrongRequest, new byte[0]));

        MockHttpServletRequest tokenOnly = new MockHttpServletRequest("GET", path);
        tokenOnly.addHeader("Authorization", "Bearer not-an-identity");
        tokenOnly.addHeader("X-Tenant-Id", "9002");
        assertThrows(com.basiclab.iot.common.security.internal.InternalServiceAuthException.class,
                () -> auth.verify(tokenOnly, new byte[0]));
    }

    @Test
    void observedBodyRejectsUnknownJsonFieldsEvenWhenObjectMapperIgnoresThem() throws Exception {
        FakeRepository repository = new FakeRepository();
        CollectorConfigReleaseInternalService service = new CollectorConfigReleaseInternalService(
                repository, (fact, outcome) -> { });
        byte[] key = new byte[32];
        for (int i = 0; i < key.length; i++) key[i] = (byte) (i + 1);
        InternalServiceKeyProvider keys = (serviceId, keyId) -> Optional.of(key);
        InternalServiceAuthVerifier verifier = new InternalServiceAuthVerifier(
                keys, (serviceId, nonce, ttl) -> true,
                CollectorConfigReleaseInternalAuth.requiredRoutes(), CLOCK, 300, 600);
        com.basiclab.iot.device.controller.collector.CollectorConfigReleaseInternalController controller =
                new com.basiclab.iot.device.controller.collector.CollectorConfigReleaseInternalController(
                        service, verifier, new ObjectMapper());
        byte[] body = ("{\"releaseId\":\"9001\",\"tenantId\":\"9002\","
                + "\"nodeId\":\"9003\",\"workloadId\":\"collector-01\","
                + "\"configVersion\":\"12\",\"payloadSha256\":\"" + HASH + "\","
                + "\"status\":\"AGENT_ACCEPTED\","
                + "\"observedAt\":\"2026-08-17T10:00:00+08:00\","
                + "\"unexpected\":\"must-be-rejected\"}")
                .getBytes(java.nio.charset.StandardCharsets.UTF_8);
        String path = CollectorConfigReleaseInternalApi.BASE_PATH + "/9001/observed";
        InternalServiceAuthSigner signer = new InternalServiceAuthSigner(keys, "iot-node", "k1",
                CLOCK, () -> "3323456789abcdef0123456789abcdef");
        assertThrows(CollectorConfigReleaseInternalException.class,
                () -> controller.observed("9001", body,
                        request("POST", path, signer.sign("POST", path, body))));
    }

    private static CollectorConfigReleaseObservedRequestDTO observed(
            String releaseId, String tenantId, String nodeId, String workloadId,
            String configVersion, String hash, CollectorConfigReleaseObservedStatus status) {
        CollectorConfigReleaseObservedRequestDTO request = new CollectorConfigReleaseObservedRequestDTO();
        request.setReleaseId(releaseId);
        request.setTenantId(tenantId);
        request.setNodeId(nodeId);
        request.setWorkloadId(workloadId);
        request.setConfigVersion(configVersion);
        request.setPayloadSha256(hash);
        request.setStatus(status);
        request.setObservedAt("2026-08-17T10:00:00+08:00");
        if (status == CollectorConfigReleaseObservedStatus.FAILED) request.setErrorCode("APPLY_FAILED");
        return request;
    }

    private static MockHttpServletRequest request(String method, String path,
                                                   Map<String, String> headers) {
        String rawPath = path;
        String query = null;
        int index = path.indexOf('?');
        if (index >= 0) {
            rawPath = path.substring(0, index);
            query = path.substring(index + 1);
        }
        MockHttpServletRequest request = new MockHttpServletRequest(method, rawPath);
        request.setQueryString(query);
        headers.forEach(request::addHeader);
        return request;
    }

    private static final class RecordingFactRecorder
            implements CollectorConfigReleaseObservedFactRecorder {
        private final AtomicInteger count = new AtomicInteger();
        private String outcome;

        @Override
        public void record(CollectorConfigReleaseObservedFact fact, String outcome) {
            this.count.incrementAndGet();
            this.outcome = outcome;
        }
    }

    private static final class FakeRepository implements CollectorConfigReleaseInternalRepository {
        private final List<ReleaseRecord> rows = new ArrayList<>();
        private ObservedCasResult nextResult = new ObservedCasResult(Outcome.MISMATCH);

        @Override
        public List<ReleaseRecord> findPending(int limit) {
            return rows.subList(0, Math.min(limit, rows.size()));
        }

        @Override
        public Optional<ReleaseRecord> findById(long releaseId) {
            return rows.stream().filter(row -> row.releaseId() == releaseId).findFirst();
        }

        @Override
        public ObservedCasResult observe(ObservedRecord observed) {
            return nextResult;
        }
    }
}
