package com.basiclab.iot.sink.outbox.sqlite;

import com.basiclab.iot.sink.telemetry.envelope.EnvelopeJcsCanonicalizer;
import com.basiclab.iot.sink.telemetry.outbox.backfill.RouteBackfillApplyRequest;
import com.basiclab.iot.sink.telemetry.outbox.backfill.RouteBackfillAuthorization;
import com.basiclab.iot.sink.telemetry.outbox.backfill.RouteBackfillAuthorizationArtifact;
import com.basiclab.iot.sink.telemetry.outbox.backfill.RouteBackfillManifest;
import com.basiclab.iot.sink.telemetry.outbox.backfill.RouteBackfillManifestArtifact;
import com.basiclab.iot.sink.telemetry.outbox.backfill.RouteBackfillVerificationResult;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.Signature;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;

class RouteBackfillAuthorizationVerifierTest {

    private static final String INVENTORY_HASH = "a".repeat(64);
    private static final String OPERATION_ID = "123e4567-e89b-12d3-a456-426614174000";
    private static final long NOW = 1_700_000_000L;
    private static final Clock CLOCK = Clock.fixed(Instant.ofEpochSecond(NOW), ZoneOffset.UTC);
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final EnvelopeJcsCanonicalizer JCS = new EnvelopeJcsCanonicalizer();

    @Test
    void verifierAcceptsSignerShapeAndBindsExpectedWorkload() throws Exception {
        KeyPair pair = keyPair();
        Fixture fixture = fixture(pair, "workload-a", "workload-a", null,
                RouteBackfillAuthorization.SIGNATURE_CONTEXT, NOW - 10, NOW + 600,
                pair.getPrivate());
        RouteBackfillVerificationResult.Verified verified = assertInstanceOf(
                RouteBackfillVerificationResult.Verified.class,
                verify(fixture.request(), id -> Optional.of(pair.getPublic()), "workload-a"));
        assertEquals(fixture.request(), verified.request());
    }

    @Test
    void verifierRejectsManifestAuthorizationAndTargetBindingFailures() throws Exception {
        KeyPair pair = keyPair();
        Fixture valid = fixture(pair, "workload-a", "workload-a", null,
                RouteBackfillAuthorization.SIGNATURE_CONTEXT, NOW - 10, NOW + 600,
                pair.getPrivate());
        assertCode(new RouteBackfillApplyRequest(null, valid.request().authorizationArtifact()),
                id -> Optional.of(pair.getPublic()), "workload-a",
                "ROUTE_BACKFILL_MANIFEST_INTEGRITY_FAILED");

        Fixture inventoryMismatch = fixture(pair, "workload-a", "workload-a",
                "f".repeat(64), RouteBackfillAuthorization.SIGNATURE_CONTEXT,
                NOW - 10, NOW + 600, pair.getPrivate());
        assertCode(inventoryMismatch.request(), id -> Optional.of(pair.getPublic()), "workload-a",
                "ROUTE_BACKFILL_AUTHORIZATION_BINDING_MISMATCH");

        Fixture workloadMismatch = fixture(pair, "workload-a", "workload-b", null,
                RouteBackfillAuthorization.SIGNATURE_CONTEXT, NOW - 10, NOW + 600,
                pair.getPrivate());
        assertCode(workloadMismatch.request(), id -> Optional.of(pair.getPublic()), "workload-a",
                "ROUTE_BACKFILL_AUTHORIZATION_BINDING_MISMATCH");

        assertCode(valid.request(), id -> Optional.of(pair.getPublic()), "workload-b",
                "ROUTE_BACKFILL_TARGET_WORKLOAD_MISMATCH");
    }

    @Test
    void verifierRejectsUnknownWrongAndUnavailableKeys() throws Exception {
        KeyPair pair = keyPair();
        Fixture valid = fixture(pair, "workload-a", "workload-a", null,
                RouteBackfillAuthorization.SIGNATURE_CONTEXT, NOW - 10, NOW + 600,
                pair.getPrivate());
        assertCode(valid.request(), id -> Optional.empty(), "workload-a",
                "ROUTE_BACKFILL_AUTHORIZATION_KEY_UNKNOWN");

        KeyPair wrong = keyPair();
        assertCode(valid.request(), id -> Optional.of(wrong.getPublic()), "workload-a",
                "ROUTE_BACKFILL_AUTHORIZATION_SIGNATURE_INVALID");
        assertCode(valid.request(), id -> { throw new IllegalStateException("unavailable"); },
                "workload-a", "ROUTE_BACKFILL_AUTHORIZATION_KEY_UNKNOWN");
    }

    @Test
    void verifierRejectsContextAndSignatureEncodingFailuresWithoutLeakingPayload() throws Exception {
        KeyPair pair = keyPair();
        Fixture context = fixture(pair, "workload-a", "workload-a", null,
                "wrong-context", NOW - 10, NOW + 600, pair.getPrivate());
        assertCode(context.request(), id -> Optional.of(pair.getPublic()), "workload-a",
                "ROUTE_BACKFILL_AUTHORIZATION_INTEGRITY_FAILED");

        Fixture valid = fixture(pair, "workload-a", "workload-a", null,
                RouteBackfillAuthorization.SIGNATURE_CONTEXT, NOW - 10, NOW + 600,
                pair.getPrivate());
        byte[] signature = Base64.getDecoder().decode(
                valid.request().authorizationArtifact().signatureBase64());
        signature[0] ^= 1;
        RouteBackfillAuthorizationArtifact tampered = new RouteBackfillAuthorizationArtifact(
                valid.request().authorizationArtifact().authorization(),
                valid.request().authorizationArtifact().canonicalBytes(),
                valid.request().authorizationArtifact().contentSha256(),
                Base64.getEncoder().encodeToString(signature));
        RouteBackfillApplyRequest tamperedRequest = new RouteBackfillApplyRequest(
                valid.request().manifestArtifact(), tampered);
        RouteBackfillVerificationResult result = verify(tamperedRequest,
                id -> Optional.of(pair.getPublic()), "workload-a");
        assertCode(result, "ROUTE_BACKFILL_AUTHORIZATION_SIGNATURE_INVALID");
        assertFalse(result.toString().contains(valid.request().authorizationArtifact().signatureBase64()));
        assertFalse(result.toString().contains(new String(valid.request().manifestArtifact().canonicalBytes(),
                StandardCharsets.UTF_8)));

        RouteBackfillAuthorizationArtifact nonCanonical = new RouteBackfillAuthorizationArtifact(
                valid.request().authorizationArtifact().authorization(),
                valid.request().authorizationArtifact().canonicalBytes(),
                valid.request().authorizationArtifact().contentSha256(),
                valid.request().authorizationArtifact().signatureBase64() + " ");
        assertCode(new RouteBackfillApplyRequest(valid.request().manifestArtifact(), nonCanonical),
                id -> Optional.of(pair.getPublic()), "workload-a",
                "ROUTE_BACKFILL_AUTHORIZATION_INTEGRITY_FAILED");

        RouteBackfillAuthorizationArtifact shortSignature = new RouteBackfillAuthorizationArtifact(
                valid.request().authorizationArtifact().authorization(),
                valid.request().authorizationArtifact().canonicalBytes(),
                valid.request().authorizationArtifact().contentSha256(),
                Base64.getEncoder().encodeToString(new byte[63]));
        assertCode(new RouteBackfillApplyRequest(valid.request().manifestArtifact(), shortSignature),
                id -> Optional.of(pair.getPublic()), "workload-a",
                "ROUTE_BACKFILL_AUTHORIZATION_INTEGRITY_FAILED");
    }

    @Test
    void verifierDoesNotEchoUntrustedOperationIdWithSignatureOrPayloadText() throws Exception {
        KeyPair pair = keyPair();
        String maliciousOperationId = "signature="
                + Base64.getEncoder().encodeToString(new byte[64])
                + ";payload={\"secret\":\"should-not-echo\"}";
        Fixture malformed = fixtureWithOperationId(pair, maliciousOperationId,
                "workload-a", "workload-a", null,
                RouteBackfillAuthorization.SIGNATURE_CONTEXT, NOW - 10, NOW + 600,
                pair.getPrivate());

        RouteBackfillVerificationResult result = verify(malformed.request(),
                id -> Optional.of(pair.getPublic()), "workload-a");
        RouteBackfillVerificationResult.Rejected rejected = assertInstanceOf(
                RouteBackfillVerificationResult.Rejected.class, result);
        assertEquals("ROUTE_BACKFILL_AUTHORIZATION_INTEGRITY_FAILED", rejected.code());
        assertNull(rejected.operationId());
        assertFalse(result.toString().contains(maliciousOperationId));
        assertFalse(result.toString().contains("should-not-echo"));
    }

    @Test
    void verifierRejectsAlgorithmKeyIdOperationAndWindowStructureFailures() throws Exception {
        KeyPair pair = keyPair();
        Fixture valid = fixture(pair, "workload-a", "workload-a", null,
                RouteBackfillAuthorization.SIGNATURE_CONTEXT, NOW - 10, NOW + 600,
                pair.getPrivate());
        RouteBackfillAuthorization authorization = valid.request().authorizationArtifact()
                .authorization();

        RouteBackfillAuthorization wrongAlgorithm = new RouteBackfillAuthorization(
                authorization.schemaVersion(), authorization.canonicalizationVersion(),
                "RSA", authorization.signatureContext(), authorization.keyId(),
                authorization.operationId(), authorization.issuedAtEpochSeconds(),
                authorization.expiresAtEpochSeconds(), authorization.manifestContentSha256(),
                authorization.sourceInventorySha256(), authorization.workloadId());
        assertCode(authorizationVariant(valid, wrongAlgorithm, pair.getPrivate()).request(),
                id -> Optional.of(pair.getPublic()), "workload-a",
                "ROUTE_BACKFILL_AUTHORIZATION_INTEGRITY_FAILED");

        RouteBackfillAuthorization invalidKeyId = new RouteBackfillAuthorization(
                authorization.schemaVersion(), authorization.canonicalizationVersion(),
                authorization.signatureAlgorithm(), authorization.signatureContext(),
                "key id", authorization.operationId(), authorization.issuedAtEpochSeconds(),
                authorization.expiresAtEpochSeconds(), authorization.manifestContentSha256(),
                authorization.sourceInventorySha256(), authorization.workloadId());
        assertCode(authorizationVariant(valid, invalidKeyId, pair.getPrivate()).request(),
                id -> Optional.of(pair.getPublic()), "workload-a",
                "ROUTE_BACKFILL_AUTHORIZATION_INTEGRITY_FAILED");

        RouteBackfillAuthorization nonCanonicalOperationId = new RouteBackfillAuthorization(
                authorization.schemaVersion(), authorization.canonicalizationVersion(),
                authorization.signatureAlgorithm(), authorization.signatureContext(),
                authorization.keyId(), "123E4567-e89b-12d3-a456-426614174000",
                authorization.issuedAtEpochSeconds(), authorization.expiresAtEpochSeconds(),
                authorization.manifestContentSha256(), authorization.sourceInventorySha256(),
                authorization.workloadId());
        assertCode(authorizationVariant(valid, nonCanonicalOperationId, pair.getPrivate()).request(),
                id -> Optional.of(pair.getPublic()), "workload-a",
                "ROUTE_BACKFILL_AUTHORIZATION_INTEGRITY_FAILED");

        Fixture tooLong = fixture(pair, "workload-a", "workload-a", null,
                RouteBackfillAuthorization.SIGNATURE_CONTEXT, NOW - 10, NOW + 86_391,
                pair.getPrivate());
        assertCode(tooLong.request(), id -> Optional.of(pair.getPublic()), "workload-a",
                "ROUTE_BACKFILL_AUTHORIZATION_INTEGRITY_FAILED");
    }

    @Test
    void verifierAppliesClockSkewExpiryAndExactUnicodeRules() throws Exception {
        KeyPair pair = keyPair();
        Fixture futureBoundary = fixture(pair, "workload-a", "workload-a", null,
                RouteBackfillAuthorization.SIGNATURE_CONTEXT, NOW + 300, NOW + 900,
                pair.getPrivate());
        assertInstanceOf(RouteBackfillVerificationResult.Verified.class,
                verify(futureBoundary.request(), id -> Optional.of(pair.getPublic()),
                        "workload-a"));

        Fixture future = fixture(pair, "workload-a", "workload-a", null,
                RouteBackfillAuthorization.SIGNATURE_CONTEXT, NOW + 301, NOW + 900,
                pair.getPrivate());
        assertCode(future.request(), id -> Optional.of(pair.getPublic()), "workload-a",
                "ROUTE_BACKFILL_AUTHORIZATION_NOT_YET_VALID");

        Fixture expired = fixture(pair, "workload-a", "workload-a", null,
                RouteBackfillAuthorization.SIGNATURE_CONTEXT, NOW - 600, NOW,
                pair.getPrivate());
        assertCode(expired.request(), id -> Optional.of(pair.getPublic()), "workload-a",
                "ROUTE_BACKFILL_AUTHORIZATION_EXPIRED");

        Fixture exactWindow = fixture(pair, "workload-a", "workload-a", null,
                RouteBackfillAuthorization.SIGNATURE_CONTEXT, NOW - 10, NOW + 86_390,
                pair.getPrivate());
        assertInstanceOf(RouteBackfillVerificationResult.Verified.class,
                verify(exactWindow.request(), id -> Optional.of(pair.getPublic()),
                        "workload-a"));

        Fixture boundary = fixture(pair, "cafe\u0301-workload", "cafe\u0301-workload", null,
                RouteBackfillAuthorization.SIGNATURE_CONTEXT, NOW - 100, NOW + 600,
                pair.getPrivate());
        assertInstanceOf(RouteBackfillVerificationResult.Verified.class,
                verify(boundary.request(), id -> Optional.of(pair.getPublic()), "cafe\u0301-workload"));
        assertCode(boundary.request(), id -> Optional.of(pair.getPublic()), "caf\u00e9-workload",
                "ROUTE_BACKFILL_TARGET_WORKLOAD_MISMATCH");
    }

    private static RouteBackfillVerificationResult verify(RouteBackfillApplyRequest request,
                                                          RouteBackfillVerificationKeyProvider keys,
                                                          String expectedWorkload) {
        return new RouteBackfillAuthorizationVerifier(keys).verify(request, expectedWorkload, CLOCK);
    }

    private static void assertCode(RouteBackfillApplyRequest request,
                                   RouteBackfillVerificationKeyProvider keys,
                                   String expectedWorkload, String expectedCode) {
        assertCode(verify(request, keys, expectedWorkload), expectedCode);
    }

    private static void assertCode(RouteBackfillVerificationResult result, String expectedCode) {
        RouteBackfillVerificationResult.Rejected rejected = assertInstanceOf(
                RouteBackfillVerificationResult.Rejected.class, result);
        assertEquals(expectedCode, rejected.code());
    }

    private static Fixture fixture(KeyPair pair, String manifestWorkload, String authorizationWorkload,
                                   String sourceInventoryOverride, String context, long issued,
                                   long expires, java.security.PrivateKey privateKey) throws Exception {
        return fixtureWithOperationId(pair, OPERATION_ID, manifestWorkload, authorizationWorkload,
                sourceInventoryOverride, context, issued, expires, privateKey);
    }

    private static Fixture fixtureWithOperationId(KeyPair pair, String operationId,
                                                  String manifestWorkload,
                                                  String authorizationWorkload,
                                                  String sourceInventoryOverride, String context,
                                                  long issued, long expires,
                                                  java.security.PrivateKey privateKey) throws Exception {
        RouteBackfillManifest manifest = new RouteBackfillManifest(
                RouteBackfillManifest.SCHEMA_VERSION,
                RouteBackfillManifest.CANONICALIZATION_VERSION,
                INVENTORY_HASH, manifestWorkload, List.of(), null);
        RouteBackfillManifestArtifact manifestArtifact = new RouteBackfillManifestArtifact(manifest);
        RouteBackfillAuthorization authorization = new RouteBackfillAuthorization(
                RouteBackfillAuthorization.SCHEMA_VERSION,
                RouteBackfillAuthorization.CANONICALIZATION_VERSION,
                RouteBackfillAuthorization.SIGNATURE_ALGORITHM,
                context,
                "key-1",
                operationId,
                issued,
                expires,
                manifestArtifact.contentSha256(),
                sourceInventoryOverride == null ? INVENTORY_HASH : sourceInventoryOverride,
                authorizationWorkload);
        byte[] canonicalBytes = canonicalBytes(authorization);
        byte[] signature = sign(canonicalBytes, privateKey);
        RouteBackfillAuthorizationArtifact authorizationArtifact =
                new RouteBackfillAuthorizationArtifact(authorization, canonicalBytes,
                        sha256(canonicalBytes), Base64.getEncoder().encodeToString(signature));
        return new Fixture(new RouteBackfillApplyRequest(manifestArtifact, authorizationArtifact));
    }

    private static Fixture authorizationVariant(Fixture base,
                                                RouteBackfillAuthorization authorization,
                                                java.security.PrivateKey privateKey) throws Exception {
        byte[] canonicalBytes = canonicalBytes(authorization);
        byte[] signature = sign(canonicalBytes, privateKey);
        RouteBackfillAuthorizationArtifact authorizationArtifact =
                new RouteBackfillAuthorizationArtifact(authorization, canonicalBytes,
                        sha256(canonicalBytes), Base64.getEncoder().encodeToString(signature));
        return new Fixture(new RouteBackfillApplyRequest(base.request().manifestArtifact(),
                authorizationArtifact));
    }

    private static byte[] canonicalBytes(RouteBackfillAuthorization authorization) {
        return JCS.canonicalize(MAPPER.valueToTree(authorization)).getBytes(StandardCharsets.UTF_8);
    }

    private static byte[] sign(byte[] canonicalBytes, java.security.PrivateKey privateKey)
            throws Exception {
        byte[] domain = RouteBackfillAuthorizationVerifier.DOMAIN_SEPARATOR
                .getBytes(StandardCharsets.UTF_8);
        byte[] input = new byte[domain.length + canonicalBytes.length];
        System.arraycopy(domain, 0, input, 0, domain.length);
        System.arraycopy(canonicalBytes, 0, input, domain.length, canonicalBytes.length);
        Signature signer = Signature.getInstance("Ed25519");
        signer.initSign(privateKey);
        signer.update(input);
        return signer.sign();
    }

    private static String sha256(byte[] bytes) throws Exception {
        byte[] digest = java.security.MessageDigest.getInstance("SHA-256").digest(bytes);
        StringBuilder result = new StringBuilder(64);
        for (byte value : digest) {
            result.append(Character.forDigit((value >>> 4) & 0xf, 16));
            result.append(Character.forDigit(value & 0xf, 16));
        }
        return result.toString();
    }

    private static KeyPair keyPair() throws Exception {
        return KeyPairGenerator.getInstance("Ed25519").generateKeyPair();
    }

    private record Fixture(RouteBackfillApplyRequest request) {
    }
}
