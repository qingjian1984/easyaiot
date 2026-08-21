package com.basiclab.iot.sink.telemetry.outbox.backfill;

import com.basiclab.iot.sink.telemetry.envelope.EnvelopeJcsCanonicalizer;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class RouteBackfillAuthorizationContractTest {

    private static final String HASH = "c".repeat(64);

    @Test
    void authorizationArtifactDefensivelyCopiesCanonicalBytes() {
        RouteBackfillAuthorization authorization = authorization("workload-a");
        byte[] canonical = canonicalBytes(authorization);
        String digest = sha256(canonical);
        RouteBackfillAuthorizationArtifact artifact = new RouteBackfillAuthorizationArtifact(
                authorization, canonical, digest, Base64Fixtures.SIGNATURE);

        byte[] first = artifact.canonicalBytes();
        first[0] ^= 1;
        assertArrayEquals(canonical, artifact.canonicalBytes());
        assertEquals(digest, artifact.contentSha256());
        assertThrows(UnsupportedOperationException.class, () ->
                RouteBackfillVerificationResult.Rejected.CODES.clear());
    }

    @Test
    void verificationResultRejectsUnknownCodeAndVerifiedRequiresRequest() {
        assertThrows(IllegalArgumentException.class, () ->
                new RouteBackfillVerificationResult.Rejected(
                        "123e4567-e89b-12d3-a456-426614174000", HASH, "UNKNOWN"));
        assertThrows(IllegalArgumentException.class, () ->
                new RouteBackfillVerificationResult.Rejected(
                        "signature=payload", HASH,
                        "ROUTE_BACKFILL_AUTHORIZATION_INTEGRITY_FAILED"));
        assertThrows(IllegalArgumentException.class, () ->
                new RouteBackfillVerificationResult.Rejected(
                        "123e4567-e89b-12d3-a456-426614174000", "A".repeat(64),
                        "ROUTE_BACKFILL_AUTHORIZATION_INTEGRITY_FAILED"));
        assertThrows(IllegalArgumentException.class, () ->
                new RouteBackfillVerificationResult.Verified(null));
        RouteBackfillApplyRequest request = new RouteBackfillApplyRequest(null, null);
        assertEquals(request, new RouteBackfillVerificationResult.Verified(request).request());
    }

    @Test
    void canonicalBytesDoNotNormalizeNfdWorkload() {
        String nfd = "cafe\u0301-workload";
        RouteBackfillAuthorization authorization = authorization(nfd);
        byte[] canonical = canonicalBytes(authorization);
        assertTrueContains(new String(canonical, StandardCharsets.UTF_8), nfd);
        assertEquals(sha256(canonical), new RouteBackfillAuthorizationArtifact(
                authorization, canonical, sha256(canonical), Base64Fixtures.SIGNATURE)
                .contentSha256());
    }

    private static RouteBackfillAuthorization authorization(String workloadId) {
        return new RouteBackfillAuthorization(
                RouteBackfillAuthorization.SCHEMA_VERSION,
                RouteBackfillAuthorization.CANONICALIZATION_VERSION,
                RouteBackfillAuthorization.SIGNATURE_ALGORITHM,
                RouteBackfillAuthorization.SIGNATURE_CONTEXT,
                "key-1",
                "123e4567-e89b-12d3-a456-426614174000",
                1_700_000_000L,
                1_700_000_600L,
                HASH,
                HASH,
                workloadId);
    }

    private static byte[] canonicalBytes(RouteBackfillAuthorization authorization) {
        com.fasterxml.jackson.databind.ObjectMapper mapper =
                new com.fasterxml.jackson.databind.ObjectMapper();
        return new EnvelopeJcsCanonicalizer().canonicalize(mapper.valueToTree(authorization))
                .getBytes(StandardCharsets.UTF_8);
    }

    private static String sha256(byte[] bytes) {
        byte[] digest;
        try {
            digest = MessageDigest.getInstance("SHA-256").digest(bytes);
        } catch (Exception e) {
            throw new AssertionError(e);
        }
        StringBuilder result = new StringBuilder(64);
        for (byte value : digest) {
            result.append(Character.forDigit((value >>> 4) & 0xf, 16));
            result.append(Character.forDigit(value & 0xf, 16));
        }
        return result.toString();
    }

    private static void assertTrueContains(String value, String expected) {
        if (!value.contains(expected)) {
            throw new AssertionError("missing expected exact Unicode value");
        }
    }

    private static final class Base64Fixtures {
        private static final String SIGNATURE = java.util.Base64.getEncoder()
                .encodeToString(new byte[64]);
    }
}
