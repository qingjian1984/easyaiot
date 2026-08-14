package com.basiclab.iot.common.security.internal;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class InternalServiceAuthContractTest {

    private static final byte[] SECRET = "synthetic-test-secret-012345678901234567890".getBytes(StandardCharsets.UTF_8);
    private static final Clock CLOCK = Clock.fixed(Instant.ofEpochSecond(1_700_000_000), ZoneOffset.UTC);

    @Test
    void signerAndVerifierUseSortedQueryAndClaimNonceExactlyOnce() {
        InternalServiceKeyProvider keys = (service, key) ->
                "iot-node".equals(service) && "k1".equals(key) ? Optional.of(SECRET) : Optional.empty();
        Set<String> claimed = new HashSet<>();
        InternalServiceAuthNonceStore nonces = (service, nonce, ttl) -> claimed.add(service + ":" + nonce);
        InternalServiceAuthSigner signer = new InternalServiceAuthSigner(
                keys, "iot-node", "k1", CLOCK, () -> "00112233445566778899aabbccddeeff");
        Map<String, String> headers = signer.sign("POST", "/internal-api/device/releases?b=2&a=1",
                "payload".getBytes(StandardCharsets.UTF_8));
        InternalServiceAuthVerifier verifier = verifier(keys, nonces,
                new InternalServiceAuthRoute("POST", "/internal-api/device/releases"));
        InternalServiceAuthRequest request = new InternalServiceAuthRequest(
                "POST", "/internal-api/device/releases?a=1&b=2",
                "payload".getBytes(StandardCharsets.UTF_8), headers);

        assertEquals("iot-node", verifier.verify(request).serviceId());
        InternalServiceAuthException replay = assertThrows(InternalServiceAuthException.class,
                () -> verifier.verify(request));
        assertEquals("SERVICE_AUTH_REPLAYED", replay.getCode());
    }

    @Test
    void rejectsBodyTamperingBeforeSignatureDecision() {
        InternalServiceKeyProvider keys = (service, key) -> Optional.of(SECRET);
        InternalServiceAuthSigner signer = new InternalServiceAuthSigner(
                keys, "iot-node", "k1", CLOCK, () -> "11112222333344445555666677778888");
        Map<String, String> headers = signer.sign("PUT", "/internal-api/device/release", new byte[]{1});
        InternalServiceAuthVerifier verifier = verifier(keys, (service, nonce, ttl) -> true,
                new InternalServiceAuthRoute("PUT", "/internal-api/device/release"));
        InternalServiceAuthException error = assertThrows(InternalServiceAuthException.class,
                () -> verifier.verify(new InternalServiceAuthRequest("PUT", "/internal-api/device/release",
                        new byte[]{2}, headers)));
        assertEquals("SERVICE_AUTH_BODY_HASH_MISMATCH", error.getCode());
    }

    @Test
    void rejectsExpiredUnknownRouteAndRedisFailure() {
        InternalServiceKeyProvider keys = (service, key) -> Optional.of(SECRET);
        InternalServiceAuthSigner signer = new InternalServiceAuthSigner(
                keys, "iot-node", "k1", CLOCK, () -> "22223333444455556666777788889999");
        Map<String, String> headers = new HashMap<>(signer.sign("GET", "/internal-api/device/release", new byte[0]));
        InternalServiceAuthVerifier expired = new InternalServiceAuthVerifier(keys, (s, n, t) -> true,
                Set.of(new InternalServiceAuthRoute("GET", "/internal-api/device/release")),
                Clock.fixed(Instant.ofEpochSecond(1_700_000_400), ZoneOffset.UTC), 300, 600);
        InternalServiceAuthException expiredError = assertThrows(InternalServiceAuthException.class,
                () -> expired.verify(new InternalServiceAuthRequest("GET", "/internal-api/device/release",
                        new byte[0], headers)));
        assertEquals("SERVICE_AUTH_EXPIRED", expiredError.getCode());

        InternalServiceAuthVerifier unknownRoute = verifier(keys, (s, n, t) -> true,
                new InternalServiceAuthRoute("GET", "/different"));
        InternalServiceAuthException routeError = assertThrows(InternalServiceAuthException.class,
                () -> unknownRoute.verify(new InternalServiceAuthRequest("GET", "/internal-api/device/release",
                        new byte[0], headers)));
        assertEquals("SERVICE_AUTH_UNKNOWN_CALLER", routeError.getCode());

        InternalServiceAuthVerifier redisFailure = verifier(keys, (s, n, t) -> {
            throw new IllegalStateException("synthetic redis down");
        }, new InternalServiceAuthRoute("GET", "/internal-api/device/release"));
        InternalServiceAuthException redisError = assertThrows(InternalServiceAuthException.class,
                () -> redisFailure.verify(new InternalServiceAuthRequest("GET", "/internal-api/device/release",
                        new byte[0], headers)));
        assertEquals("SERVICE_AUTH_NONCE_STORE_UNAVAILABLE", redisError.getCode());
    }

    private static InternalServiceAuthVerifier verifier(InternalServiceKeyProvider keys,
                                                          InternalServiceAuthNonceStore nonces,
                                                          InternalServiceAuthRoute route) {
        return new InternalServiceAuthVerifier(keys, nonces, Set.of(route), CLOCK, 300, 600);
    }
}
