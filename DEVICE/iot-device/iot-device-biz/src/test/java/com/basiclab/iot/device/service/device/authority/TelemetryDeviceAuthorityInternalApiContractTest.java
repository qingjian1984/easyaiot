package com.basiclab.iot.device.service.device.authority;

import com.basiclab.iot.common.domain.R;
import com.basiclab.iot.common.security.internal.InternalServiceAuthException;
import com.basiclab.iot.common.security.internal.InternalServiceAuthNonceStore;
import com.basiclab.iot.common.security.internal.InternalServiceAuthRequest;
import com.basiclab.iot.common.security.internal.InternalServiceAuthRoute;
import com.basiclab.iot.common.security.internal.InternalServiceAuthSigner;
import com.basiclab.iot.common.security.internal.InternalServiceAuthVerifier;
import com.basiclab.iot.common.security.internal.InternalServiceKeyProvider;
import com.basiclab.iot.device.TelemetryDeviceAuthorityInternalApi;
import com.basiclab.iot.device.controller.device.authority.TelemetryDeviceAuthorityInternalController;
import com.basiclab.iot.device.dal.pgsql.device.DeviceMapper;
import com.basiclab.iot.device.domain.device.authority.TelemetryDeviceAuthorityResolutionDTO;
import org.junit.jupiter.api.Test;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.http.ResponseEntity;

import javax.servlet.http.HttpServletRequest;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class TelemetryDeviceAuthorityInternalApiContractTest {

    private static final String PATH = TelemetryDeviceAuthorityInternalApi.RESOLVE_PATH
            + "?deviceIdentification=d-1&productIdentification=p-1";
    private static final byte[] KEY = "01234567890123456789012345678901"
            .getBytes(StandardCharsets.UTF_8);
    private static final byte[] NODE_KEY = "abcdefabcdefabcdefabcdefabcdefab"
            .getBytes(StandardCharsets.UTF_8);
    private static final Clock CLOCK = Clock.fixed(Instant.ofEpochSecond(1_700_000_000L),
            ZoneOffset.UTC);

    @Test
    void apiIsAPlainSpringContractAndHasOnlyTheResolveEndpoint() throws Exception {
        assertFalse(TelemetryDeviceAuthorityInternalApi.class.isAnnotationPresent(FeignClient.class));
        assertEquals(TelemetryDeviceAuthorityInternalApi.RESOLVE_PATH,
                TelemetryDeviceAuthorityInternalApi.class
                        .getDeclaredMethod("resolve", String.class, String.class)
                        .getAnnotation(org.springframework.web.bind.annotation.GetMapping.class)
                        .value()[0]);
        assertEquals(List.of(new InternalServiceAuthRoute(
                        "GET", TelemetryDeviceAuthorityInternalApi.RESOLVE_PATH)),
                TelemetryDeviceAuthorityInternalAuth.requiredRoutes());

        assertArrayEquals(new String[]{
                        "productIdentification", "deviceIdentification", "status", "tenantId"},
                Arrays.stream(TelemetryDeviceAuthorityResolutionDTO.class.getRecordComponents())
                        .map(java.lang.reflect.RecordComponent::getName)
                        .toArray(String[]::new));
        assertFalse(Arrays.stream(TelemetryDeviceAuthorityResolutionDTO.class.getRecordComponents())
                .anyMatch(component -> component.getType().getName().contains("Device")
                        || component.getType().getName().contains("Candidate")
                        || List.class.isAssignableFrom(component.getType())));
    }

    @Test
    void correctSinkSignaturePassesAndQueryTamperingFails() {
        InternalServiceAuthSigner signer = new InternalServiceAuthSigner(
                provider(), "iot-sink", "k1", CLOCK, () -> "0123456789abcdef0123456789abcdef");
        Map<String, String> headers = signer.sign("GET", PATH, new byte[0]);
        InternalServiceAuthVerifier verifier = verifier();

        assertEquals("iot-sink", verifier.verify(new InternalServiceAuthRequest(
                "GET", PATH, new byte[0], headers)).serviceId());

        assertThrows(RuntimeException.class, () -> verifier.verify(new InternalServiceAuthRequest(
                "GET", TelemetryDeviceAuthorityInternalApi.RESOLVE_PATH
                        + "?deviceIdentification=d-2&productIdentification=p-1",
                new byte[0], headers)));
    }

    @Test
    void wrongServiceAndReplayAreRejected() {
        InternalServiceAuthVerifier verifier = verifier();
        InternalServiceAuthSigner wrongSigner = new InternalServiceAuthSigner(
                provider(), "iot-node", "node-k1", CLOCK,
                () -> "fedcba9876543210fedcba9876543210");
        Map<String, String> wrongServiceHeaders = wrongSigner.sign("GET", PATH, new byte[0]);
        InternalServiceAuthException wrongService = assertThrows(InternalServiceAuthException.class,
                () -> new TelemetryDeviceAuthorityInternalAuth(verifier)
                        .verify(request(wrongServiceHeaders, PATH)));
        assertEquals("SERVICE_AUTH_UNKNOWN_CALLER", wrongService.getCode());

        InternalServiceAuthVerifier replayVerifier = verifier();
        InternalServiceAuthSigner signer = new InternalServiceAuthSigner(
                provider(), "iot-sink", "k1", CLOCK, () -> "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa");
        Map<String, String> headers = signer.sign("GET", PATH, new byte[0]);
        replayVerifier.verify(new InternalServiceAuthRequest("GET", PATH, new byte[0], headers));
        assertThrows(RuntimeException.class, () -> replayVerifier.verify(
                new InternalServiceAuthRequest("GET", PATH, new byte[0], headers)));
    }

    @Test
    void internalAuthRejectsMissingWrongKeyExpiredBadSignatureAndTamperedQuery() {
        Map<String, String> missing = signedHeaders("iot-sink", "k1", CLOCK, PATH);
        missing.remove("X-EasyAIoT-Service-Id");
        assertAuthFailure("SERVICE_AUTH_MISSING", missing, PATH);

        Map<String, String> wrongKey = signedHeaders("iot-sink", "k1", CLOCK, PATH);
        wrongKey.put("X-EasyAIoT-Key-Id", "unknown");
        assertAuthFailure("SERVICE_AUTH_KEY_UNKNOWN", wrongKey, PATH);

        Clock expiredClock = Clock.fixed(Instant.ofEpochSecond(1_699_999_000L), ZoneOffset.UTC);
        assertAuthFailure("SERVICE_AUTH_EXPIRED",
                signedHeaders("iot-sink", "k1", expiredClock, PATH), PATH);

        Map<String, String> badSignature = signedHeaders("iot-sink", "k1", CLOCK, PATH);
        String signature = badSignature.get("X-EasyAIoT-Signature");
        badSignature.put("X-EasyAIoT-Signature",
                (signature.charAt(0) == '0' ? '1' : '0') + signature.substring(1));
        assertAuthFailure("SERVICE_AUTH_SIGNATURE_INVALID", badSignature, PATH);

        Map<String, String> valid = signedHeaders("iot-sink", "k1", CLOCK, PATH);
        assertAuthFailure("SERVICE_AUTH_SIGNATURE_INVALID", valid,
                TelemetryDeviceAuthorityInternalApi.RESOLVE_PATH
                        + "?deviceIdentification=d-2&productIdentification=p-1");
    }

    @Test
    void controllerMapsStableAuthAndContractErrorsAndAuthenticatesBeforeService() {
        DeviceMapper mapper = mock(DeviceMapper.class);
        TelemetryDeviceAuthorityInternalController unauthorizedController = controller(mapper);
        InternalServiceAuthException unauthorized = assertThrows(InternalServiceAuthException.class,
                () -> unauthorizedController.resolve("bad/route", "d-1",
                        request(Map.of(), PATH)));
        assertResponse(unauthorizedController.authFailure(unauthorized), 401,
                "SERVICE_AUTH_MISSING");
        verifyNoInteractions(mapper);

        TelemetryDeviceAuthorityInternalController invalidRouteController = controller(mapper);
        TelemetryDeviceAuthorityInternalException invalidRoute = assertThrows(
                TelemetryDeviceAuthorityInternalException.class,
                () -> invalidRouteController.resolve("bad/route", "d-1",
                        request(signedHeaders("iot-sink", "k1", CLOCK, PATH), PATH)));
        assertResponse(invalidRouteController.contractFailure(invalidRoute), 400,
                "TELEMETRY_DEVICE_AUTHORITY_REQUEST_INVALID");

        when(mapper.selectTelemetryDeviceAuthorityCandidates("p-1", "d-1"))
                .thenReturn(List.of(candidate(0L)));
        TelemetryDeviceAuthorityInternalController dataController = controller(mapper);
        TelemetryDeviceAuthorityInternalException invalidData = assertThrows(
                TelemetryDeviceAuthorityInternalException.class,
                () -> dataController.resolve("p-1", "d-1",
                        request(signedHeaders("iot-sink", "k1", CLOCK, PATH), PATH)));
        assertResponse(dataController.contractFailure(invalidData), 500,
                "TELEMETRY_DEVICE_AUTHORITY_DATA_INVALID");

        when(mapper.selectTelemetryDeviceAuthorityCandidates("p-1", "d-1"))
                .thenThrow(new IllegalStateException("database unavailable"));
        TelemetryDeviceAuthorityInternalController unavailableController = controller(mapper);
        TelemetryDeviceAuthorityInternalException unavailable = assertThrows(
                TelemetryDeviceAuthorityInternalException.class,
                () -> unavailableController.resolve("p-1", "d-1",
                        request(signedHeaders("iot-sink", "k1", CLOCK, PATH), PATH)));
        assertResponse(unavailableController.contractFailure(unavailable), 503,
                "TELEMETRY_DEVICE_AUTHORITY_UNAVAILABLE");
    }

    private static InternalServiceAuthVerifier verifier() {
        AtomicInteger claims = new AtomicInteger();
        InternalServiceAuthNonceStore nonceStore = (serviceId, nonce, ttl) -> claims.getAndIncrement() == 0;
        return new InternalServiceAuthVerifier(provider(), nonceStore,
                TelemetryDeviceAuthorityInternalAuth.requiredRoutes(), CLOCK, 300, 600);
    }

    private static InternalServiceKeyProvider provider() {
        return (serviceId, keyId) -> {
            if ("iot-sink".equals(serviceId) && "k1".equals(keyId)) {
                return Optional.of(KEY);
            }
            if ("iot-node".equals(serviceId) && "node-k1".equals(keyId)) {
                return Optional.of(NODE_KEY);
            }
            return Optional.empty();
        };
    }

    private static TelemetryDeviceAuthorityInternalController controller(DeviceMapper mapper) {
        return new TelemetryDeviceAuthorityInternalController(
                new TelemetryDeviceAuthorityService(mapper), verifier());
    }

    private static void assertAuthFailure(String expectedCode,
                                          Map<String, String> headers,
                                          String pathWithQuery) {
        InternalServiceAuthException exception = assertThrows(InternalServiceAuthException.class,
                () -> new TelemetryDeviceAuthorityInternalAuth(verifier())
                        .verify(request(headers, pathWithQuery)));
        assertEquals(expectedCode, exception.getCode());
    }

    private static Map<String, String> signedHeaders(String serviceId, String keyId,
                                                      Clock signerClock, String pathWithQuery) {
        InternalServiceAuthSigner signer = new InternalServiceAuthSigner(
                provider(), serviceId, keyId, signerClock,
                () -> "0123456789abcdef0123456789abcdef");
        return new HashMap<>(signer.sign("GET", pathWithQuery, new byte[0]));
    }

    private static HttpServletRequest request(Map<String, String> headers, String pathWithQuery) {
        int querySeparator = pathWithQuery.indexOf('?');
        String path = querySeparator < 0
                ? pathWithQuery : pathWithQuery.substring(0, querySeparator);
        String query = querySeparator < 0
                ? null : pathWithQuery.substring(querySeparator + 1);
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getMethod()).thenReturn("GET");
        when(request.getRequestURI()).thenReturn(path);
        when(request.getQueryString()).thenReturn(query);
        when(request.getHeader(anyString())).thenAnswer(invocation ->
                headers.get(invocation.getArgument(0)));
        return request;
    }

    private static void assertResponse(ResponseEntity<R<Void>> response,
                                       int status,
                                       String message) {
        assertEquals(status, response.getStatusCodeValue());
        R<Void> body = response.getBody();
        assertNotNull(body);
        assertEquals(status, body.getCode());
        assertEquals(message, body.getMsg());
        assertNull(body.getData());
    }

    private static TelemetryDeviceAuthorityCandidate candidate(long tenantId) {
        TelemetryDeviceAuthorityCandidate candidate = new TelemetryDeviceAuthorityCandidate();
        candidate.setTenantId(tenantId);
        candidate.setProductIdentification("p-1");
        candidate.setDeviceIdentification("d-1");
        return candidate;
    }
}
