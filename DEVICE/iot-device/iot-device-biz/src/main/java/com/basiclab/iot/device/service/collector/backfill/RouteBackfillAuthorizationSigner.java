package com.basiclab.iot.device.service.collector.backfill;

import com.basiclab.iot.sink.telemetry.envelope.EnvelopeJcsCanonicalizer;
import com.basiclab.iot.sink.telemetry.outbox.backfill.RouteBackfillAuthorization;
import com.basiclab.iot.sink.telemetry.outbox.backfill.RouteBackfillAuthorizationArtifact;
import com.basiclab.iot.sink.telemetry.outbox.backfill.RouteBackfillManifestArtifact;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.Signature;
import java.time.Instant;
import java.util.Base64;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Center-side signer for one already resolved route-backfill manifest.
 *
 * <p>The private key is deliberately supplied for one call and is never kept
 * by this object.  This class has no framework lifecycle so a caller must
 * explicitly choose the key source and the authorization time window.</p>
 */
public final class RouteBackfillAuthorizationSigner {

    public static final String DOMAIN_SEPARATOR =
            "EASYAIOT-ROUTE-BACKFILL-AUTHORIZATION-V1\n";

    private static final Pattern KEY_ID = Pattern.compile("[A-Za-z0-9._-]{1,64}");
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final EnvelopeJcsCanonicalizer JCS = new EnvelopeJcsCanonicalizer();

    /**
     * Creates an Ed25519 authorization bound to the supplied manifest.
     *
     * @throws IllegalArgumentException when an input is outside the frozen
     *                                  route-backfill contract
     */
    public RouteBackfillAuthorizationArtifact authorize(
            RouteBackfillManifestArtifact manifestArtifact,
            String keyId,
            String operationId,
            Instant issuedAt,
            Instant expiresAt,
            PrivateKey privateKey) {
        RouteBackfillManifestArtifact verifiedManifest = verifyManifest(manifestArtifact);
        validateAuthorizationInputs(keyId, operationId, issuedAt, expiresAt, privateKey);

        long issued = issuedAt.getEpochSecond();
        long expires = expiresAt.getEpochSecond();
        RouteBackfillAuthorization authorization = new RouteBackfillAuthorization(
                RouteBackfillAuthorization.SCHEMA_VERSION,
                RouteBackfillAuthorization.CANONICALIZATION_VERSION,
                RouteBackfillAuthorization.SIGNATURE_ALGORITHM,
                RouteBackfillAuthorization.SIGNATURE_CONTEXT,
                keyId,
                operationId,
                issued,
                expires,
                verifiedManifest.contentSha256(),
                verifiedManifest.manifest().sourceInventorySha256(),
                verifiedManifest.manifest().workloadId());

        byte[] canonicalBytes = canonicalBytesFor(authorization);
        String contentSha256 = sha256Hex(canonicalBytes);
        byte[] signature = sign(privateKey, signingInput(canonicalBytes));
        return new RouteBackfillAuthorizationArtifact(
                authorization,
                canonicalBytes,
                contentSha256,
                Base64.getEncoder().encodeToString(signature));
    }

    private static RouteBackfillManifestArtifact verifyManifest(
            RouteBackfillManifestArtifact artifact) {
        if (artifact == null) {
            throw new IllegalArgumentException("ROUTE_BACKFILL_MANIFEST_INTEGRITY_FAILED");
        }
        try {
            // Rebuild the artifact so neither its object nor its supplied
            // bytes/hash are treated as trusted merely because it is a DTO.
            return new RouteBackfillManifestArtifact(
                    artifact.manifest(), artifact.canonicalBytes(), artifact.contentSha256());
        } catch (RuntimeException e) {
            throw new IllegalArgumentException("ROUTE_BACKFILL_MANIFEST_INTEGRITY_FAILED", e);
        }
    }

    private static void validateAuthorizationInputs(String keyId, String operationId,
                                                     Instant issuedAt, Instant expiresAt,
                                                     PrivateKey privateKey) {
        if (keyId == null || !KEY_ID.matcher(keyId).matches()) {
            throw new IllegalArgumentException("ROUTE_BACKFILL_AUTHORIZATION_INTEGRITY_FAILED");
        }
        if (!isCanonicalUuid(operationId)) {
            throw new IllegalArgumentException("ROUTE_BACKFILL_AUTHORIZATION_INTEGRITY_FAILED");
        }
        if (issuedAt == null || expiresAt == null || privateKey == null) {
            throw new IllegalArgumentException("ROUTE_BACKFILL_AUTHORIZATION_INTEGRITY_FAILED");
        }
        long issued = issuedAt.getEpochSecond();
        long expires = expiresAt.getEpochSecond();
        if (issued <= 0 || expires <= 0 || expires <= issued) {
            throw new IllegalArgumentException("ROUTE_BACKFILL_AUTHORIZATION_INTEGRITY_FAILED");
        }
        final long window;
        try {
            window = Math.subtractExact(expires, issued);
        } catch (ArithmeticException e) {
            throw new IllegalArgumentException("ROUTE_BACKFILL_AUTHORIZATION_INTEGRITY_FAILED", e);
        }
        if (window > RouteBackfillAuthorization.MAX_AUTHORIZATION_WINDOW_SECONDS) {
            throw new IllegalArgumentException("ROUTE_BACKFILL_AUTHORIZATION_INTEGRITY_FAILED");
        }
    }

    private static boolean isCanonicalUuid(String operationId) {
        if (operationId == null || operationId.length() != 36) {
            return false;
        }
        try {
            return UUID.fromString(operationId).toString().equals(operationId);
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    private static byte[] canonicalBytesFor(RouteBackfillAuthorization authorization) {
        try {
            return JCS.canonicalize(MAPPER.valueToTree(authorization))
                    .getBytes(StandardCharsets.UTF_8);
        } catch (RuntimeException e) {
            throw new IllegalArgumentException("ROUTE_BACKFILL_AUTHORIZATION_INTEGRITY_FAILED", e);
        }
    }

    private static byte[] signingInput(byte[] canonicalBytes) {
        byte[] domain = DOMAIN_SEPARATOR.getBytes(StandardCharsets.UTF_8);
        byte[] input = new byte[domain.length + canonicalBytes.length];
        System.arraycopy(domain, 0, input, 0, domain.length);
        System.arraycopy(canonicalBytes, 0, input, domain.length, canonicalBytes.length);
        return input;
    }

    private static byte[] sign(PrivateKey privateKey, byte[] input) {
        try {
            Signature signer = Signature.getInstance(RouteBackfillAuthorization.SIGNATURE_ALGORITHM);
            signer.initSign(privateKey);
            signer.update(input);
            return signer.sign();
        } catch (GeneralSecurityException e) {
            throw new IllegalArgumentException("ROUTE_BACKFILL_AUTHORIZATION_INTEGRITY_FAILED", e);
        }
    }

    private static String sha256Hex(byte[] bytes) {
        try {
            return toLowerHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }

    private static String toLowerHex(byte[] bytes) {
        StringBuilder result = new StringBuilder(bytes.length * 2);
        for (byte value : bytes) {
            result.append(Character.forDigit((value >>> 4) & 0x0f, 16));
            result.append(Character.forDigit(value & 0x0f, 16));
        }
        return result.toString();
    }
}
