package com.basiclab.iot.sink.outbox.sqlite;

import com.basiclab.iot.sink.telemetry.envelope.EnvelopeJcsCanonicalizer;
import com.basiclab.iot.sink.telemetry.outbox.backfill.RouteBackfillApplyRequest;
import com.basiclab.iot.sink.telemetry.outbox.backfill.RouteBackfillAuthorization;
import com.basiclab.iot.sink.telemetry.outbox.backfill.RouteBackfillAuthorizationArtifact;
import com.basiclab.iot.sink.telemetry.outbox.backfill.RouteBackfillManifestArtifact;
import com.basiclab.iot.sink.telemetry.outbox.backfill.RouteBackfillVerificationResult;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.PublicKey;
import java.security.Signature;
import java.time.Clock;
import java.util.Arrays;
import java.util.Base64;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Collector-side fail-closed verification of a signed route-backfill request.
 * The verifier only returns a correlation-safe rejection or the original
 * request after every manifest and authorization invariant has been checked.
 */
public final class RouteBackfillAuthorizationVerifier {

    public static final long MAX_CLOCK_SKEW_SECONDS = 300L;
    public static final String DOMAIN_SEPARATOR =
            "EASYAIOT-ROUTE-BACKFILL-AUTHORIZATION-V1\n";

    private static final Pattern KEY_ID = Pattern.compile("[A-Za-z0-9._-]{1,64}");
    private static final Pattern SHA256_HEX = Pattern.compile("[0-9a-f]{64}");
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final EnvelopeJcsCanonicalizer JCS = new EnvelopeJcsCanonicalizer();

    private final RouteBackfillVerificationKeyProvider keyProvider;

    public RouteBackfillAuthorizationVerifier(RouteBackfillVerificationKeyProvider keyProvider) {
        if (keyProvider == null) {
            throw new IllegalArgumentException("keyProvider required");
        }
        this.keyProvider = keyProvider;
    }

    /**
     * Verifies one request against the collector workload and injected clock.
     * No persistence or side effect is performed by this method.
     */
    public RouteBackfillVerificationResult verify(RouteBackfillApplyRequest request,
                                                  String expectedWorkloadId,
                                                  Clock clock) {
        if (request == null || request.manifestArtifact() == null) {
            return rejected(request, "ROUTE_BACKFILL_MANIFEST_INTEGRITY_FAILED");
        }

        RouteBackfillManifestArtifact manifest = verifyManifest(request.manifestArtifact());
        if (manifest == null) {
            return rejected(request, "ROUTE_BACKFILL_MANIFEST_INTEGRITY_FAILED");
        }

        RouteBackfillAuthorizationArtifact authorizationArtifact = request.authorizationArtifact();
        RouteBackfillAuthorization authorization = authorizationArtifact == null
                ? null : authorizationArtifact.authorization();
        if (!validAuthorizationStructure(authorizationArtifact, authorization)) {
            return rejected(request, "ROUTE_BACKFILL_AUTHORIZATION_INTEGRITY_FAILED");
        }

        byte[] canonicalBytes = authorizationArtifact.canonicalBytes();
        byte[] expectedCanonicalBytes = canonicalBytesFor(authorization);
        if (!Arrays.equals(expectedCanonicalBytes, canonicalBytes)
                || !SHA256_HEX.matcher(authorizationArtifact.contentSha256()).matches()
                || !sha256Hex(canonicalBytes).equals(authorizationArtifact.contentSha256())) {
            return rejected(request, "ROUTE_BACKFILL_AUTHORIZATION_INTEGRITY_FAILED");
        }

        byte[] signatureBytes = decodeCanonicalSignature(authorizationArtifact.signatureBase64());
        if (signatureBytes == null) {
            return rejected(request, "ROUTE_BACKFILL_AUTHORIZATION_INTEGRITY_FAILED");
        }

        if (!authorization.manifestContentSha256().equals(manifest.contentSha256())
                || !authorization.sourceInventorySha256()
                .equals(manifest.manifest().sourceInventorySha256())
                || !authorization.workloadId().equals(manifest.manifest().workloadId())) {
            return rejected(request, "ROUTE_BACKFILL_AUTHORIZATION_BINDING_MISMATCH");
        }

        if (expectedWorkloadId == null
                || !expectedWorkloadId.equals(manifest.manifest().workloadId())) {
            return rejected(request, "ROUTE_BACKFILL_TARGET_WORKLOAD_MISMATCH");
        }

        if (clock == null) {
            throw new IllegalArgumentException("clock required");
        }
        long now = clock.instant().getEpochSecond();
        if (authorization.issuedAtEpochSeconds() > now
                && authorization.issuedAtEpochSeconds() - now > MAX_CLOCK_SKEW_SECONDS) {
            return rejected(request, "ROUTE_BACKFILL_AUTHORIZATION_NOT_YET_VALID");
        }
        if (authorization.expiresAtEpochSeconds() <= now) {
            return rejected(request, "ROUTE_BACKFILL_AUTHORIZATION_EXPIRED");
        }

        Optional<PublicKey> publicKey = findKey(authorization.keyId());
        if (publicKey.isEmpty()) {
            return rejected(request, "ROUTE_BACKFILL_AUTHORIZATION_KEY_UNKNOWN");
        }

        if (!verifySignature(publicKey.get(), signingInput(canonicalBytes), signatureBytes)) {
            return rejected(request, "ROUTE_BACKFILL_AUTHORIZATION_SIGNATURE_INVALID");
        }
        return new RouteBackfillVerificationResult.Verified(request);
    }

    private static RouteBackfillManifestArtifact verifyManifest(
            RouteBackfillManifestArtifact artifact) {
        try {
            return new RouteBackfillManifestArtifact(
                    artifact.manifest(), artifact.canonicalBytes(), artifact.contentSha256());
        } catch (RuntimeException e) {
            return null;
        }
    }

    private static boolean validAuthorizationStructure(
            RouteBackfillAuthorizationArtifact artifact,
            RouteBackfillAuthorization authorization) {
        if (artifact == null || authorization == null) {
            return false;
        }
        if (!RouteBackfillAuthorization.SCHEMA_VERSION.equals(authorization.schemaVersion())
                || !RouteBackfillAuthorization.CANONICALIZATION_VERSION
                .equals(authorization.canonicalizationVersion())
                || !RouteBackfillAuthorization.SIGNATURE_ALGORITHM
                .equals(authorization.signatureAlgorithm())
                || !RouteBackfillAuthorization.SIGNATURE_CONTEXT
                .equals(authorization.signatureContext())) {
            return false;
        }
        if (authorization.keyId() == null || !KEY_ID.matcher(authorization.keyId()).matches()
                || !isCanonicalUuid(authorization.operationId())) {
            return false;
        }
        long issued = authorization.issuedAtEpochSeconds();
        long expires = authorization.expiresAtEpochSeconds();
        if (issued <= 0 || expires <= 0 || expires <= issued) {
            return false;
        }
        final long window;
        try {
            window = Math.subtractExact(expires, issued);
        } catch (ArithmeticException e) {
            return false;
        }
        if (window > RouteBackfillAuthorization.MAX_AUTHORIZATION_WINDOW_SECONDS) {
            return false;
        }
        if (authorization.manifestContentSha256() == null
                || !SHA256_HEX.matcher(authorization.manifestContentSha256()).matches()
                || authorization.sourceInventorySha256() == null
                || !SHA256_HEX.matcher(authorization.sourceInventorySha256()).matches()
                || authorization.workloadId() == null) {
            return false;
        }
        byte[] canonicalBytes = artifact.canonicalBytes();
        return canonicalBytes != null && canonicalBytes.length > 0
                && artifact.contentSha256() != null
                && artifact.signatureBase64() != null;
    }

    private Optional<PublicKey> findKey(String keyId) {
        try {
            Optional<PublicKey> result = keyProvider.findKey(keyId);
            return result == null ? Optional.empty() : result.filter(key -> key != null);
        } catch (RuntimeException e) {
            return Optional.empty();
        }
    }

    private static byte[] decodeCanonicalSignature(String signatureBase64) {
        try {
            byte[] decoded = Base64.getDecoder().decode(signatureBase64);
            if (!Base64.getEncoder().encodeToString(decoded).equals(signatureBase64)
                    || decoded.length != 64) {
                return null;
            }
            return decoded;
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private static boolean verifySignature(PublicKey publicKey, byte[] input, byte[] signature) {
        if (publicKey == null) {
            return false;
        }
        try {
            Signature verifier = Signature.getInstance(RouteBackfillAuthorization.SIGNATURE_ALGORITHM);
            verifier.initVerify(publicKey);
            verifier.update(input);
            return verifier.verify(signature);
        } catch (GeneralSecurityException e) {
            return false;
        }
    }

    private static byte[] canonicalBytesFor(RouteBackfillAuthorization authorization) {
        try {
            return JCS.canonicalize(MAPPER.valueToTree(authorization))
                    .getBytes(StandardCharsets.UTF_8);
        } catch (RuntimeException e) {
            return new byte[0];
        }
    }

    private static byte[] signingInput(byte[] canonicalBytes) {
        byte[] domain = DOMAIN_SEPARATOR.getBytes(StandardCharsets.UTF_8);
        byte[] input = new byte[domain.length + canonicalBytes.length];
        System.arraycopy(domain, 0, input, 0, domain.length);
        System.arraycopy(canonicalBytes, 0, input, domain.length, canonicalBytes.length);
        return input;
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

    private static RouteBackfillVerificationResult.Rejected rejected(
            RouteBackfillApplyRequest request, String code) {
        return new RouteBackfillVerificationResult.Rejected(
                safeOperationId(request), safeManifestHash(request), code);
    }

    private static String safeOperationId(RouteBackfillApplyRequest request) {
        if (request == null || request.authorizationArtifact() == null
                || request.authorizationArtifact().authorization() == null) {
            return null;
        }
        String operationId = request.authorizationArtifact().authorization().operationId();
        return isCanonicalUuid(operationId) ? operationId : null;
    }

    private static String safeManifestHash(RouteBackfillApplyRequest request) {
        if (request == null || request.manifestArtifact() == null) {
            return null;
        }
        return request.manifestArtifact().contentSha256();
    }
}
