package com.basiclab.iot.common.security.internal;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.util.Collection;
import java.util.Locale;
import java.util.Objects;
import java.util.regex.Pattern;

/** ADR-018 verifier：allowlist、body hash、时间窗、HMAC 与 Redis nonce 原子占用。 */
public final class InternalServiceAuthVerifier {

    private static final Pattern HEX_256 = Pattern.compile("[0-9a-fA-F]{64}");
    private static final Pattern NONCE = Pattern.compile("[0-9a-fA-F]{32,128}");

    private final InternalServiceKeyProvider keyProvider;
    private final InternalServiceAuthNonceStore nonceStore;
    private final Collection<InternalServiceAuthRoute> routes;
    private final Clock clock;
    private final long maxClockSkewSeconds;
    private final long nonceTtlSeconds;

    public InternalServiceAuthVerifier(InternalServiceKeyProvider keyProvider,
                                       InternalServiceAuthNonceStore nonceStore,
                                       Collection<InternalServiceAuthRoute> routes,
                                       Clock clock, long maxClockSkewSeconds,
                                       long nonceTtlSeconds) {
        this.keyProvider = Objects.requireNonNull(keyProvider);
        this.nonceStore = Objects.requireNonNull(nonceStore);
        this.routes = Objects.requireNonNull(routes);
        this.clock = Objects.requireNonNull(clock);
        this.maxClockSkewSeconds = maxClockSkewSeconds;
        this.nonceTtlSeconds = nonceTtlSeconds;
    }

    public VerificationResult verify(InternalServiceAuthRequest request) {
        String serviceId = required(request.header(InternalServiceAuthHeaders.SERVICE_ID),
                "SERVICE_AUTH_MISSING");
        String keyId = required(request.header(InternalServiceAuthHeaders.KEY_ID),
                "SERVICE_AUTH_MISSING");
        String timestampText = required(request.header(InternalServiceAuthHeaders.TIMESTAMP),
                "SERVICE_AUTH_MISSING");
        String nonce = required(request.header(InternalServiceAuthHeaders.NONCE),
                "SERVICE_AUTH_MISSING");
        String bodyHash = required(request.header(InternalServiceAuthHeaders.BODY_SHA256),
                "SERVICE_AUTH_MISSING").toLowerCase(Locale.ROOT);
        String signature = required(request.header(InternalServiceAuthHeaders.SIGNATURE),
                "SERVICE_AUTH_MISSING").toLowerCase(Locale.ROOT);
        long timestamp;
        try {
            timestamp = Long.parseLong(timestampText);
        } catch (NumberFormatException e) {
            throw new InternalServiceAuthException("SERVICE_AUTH_EXPIRED");
        }
        long now = clock.instant().getEpochSecond();
        if (Math.abs(now - timestamp) > maxClockSkewSeconds) {
            throw new InternalServiceAuthException("SERVICE_AUTH_EXPIRED");
        }
        if (!NONCE.matcher(nonce).matches() || !HEX_256.matcher(bodyHash).matches()
                || !HEX_256.matcher(signature).matches()) {
            throw new InternalServiceAuthException("SERVICE_AUTH_MISSING");
        }
        String normalizedPath = InternalServiceAuthCanonicalizer.normalizePathWithSortedQuery(
                request.getPathWithQuery());
        String routePath = InternalServiceAuthCanonicalizer.pathWithoutQuery(normalizedPath);
        boolean allowed = routes.stream().anyMatch(route -> route.matches(request.getMethod(), routePath));
        if (!allowed) {
            throw new InternalServiceAuthException("SERVICE_AUTH_UNKNOWN_CALLER");
        }
        String actualBodyHash = InternalServiceAuthCanonicalizer.sha256Hex(request.getBody());
        if (!constantTimeEquals(bodyHash, actualBodyHash)) {
            throw new InternalServiceAuthException("SERVICE_AUTH_BODY_HASH_MISMATCH");
        }
        byte[] key = keyProvider.findKey(serviceId, keyId)
                .orElseThrow(() -> new InternalServiceAuthException("SERVICE_AUTH_KEY_UNKNOWN"));
        if (key.length < 32) {
            throw new InternalServiceAuthException("SERVICE_AUTH_KEY_UNKNOWN");
        }
        String canonical = InternalServiceAuthCanonicalizer.canonicalRequest(
                request.getMethod(), normalizedPath, serviceId, keyId,
                timestampText, nonce, actualBodyHash);
        String expected = InternalServiceAuthCanonicalizer.hmacSha256Hex(key, canonical);
        if (!constantTimeEquals(expected, signature)) {
            throw new InternalServiceAuthException("SERVICE_AUTH_SIGNATURE_INVALID");
        }
        try {
            if (!nonceStore.claim(serviceId, nonce, nonceTtlSeconds)) {
                throw new InternalServiceAuthException("SERVICE_AUTH_REPLAYED");
            }
        } catch (InternalServiceAuthException e) {
            throw e;
        } catch (RuntimeException e) {
            throw new InternalServiceAuthException("SERVICE_AUTH_NONCE_STORE_UNAVAILABLE", e);
        }
        return new VerificationResult(serviceId, keyId);
    }

    private static String required(String value, String errorCode) {
        if (value == null || value.trim().isEmpty()) {
            throw new InternalServiceAuthException(errorCode);
        }
        return value.trim();
    }

    private static boolean constantTimeEquals(String left, String right) {
        return java.security.MessageDigest.isEqual(
                left.getBytes(StandardCharsets.US_ASCII), right.getBytes(StandardCharsets.US_ASCII));
    }

    public record VerificationResult(String serviceId, String keyId) {
    }
}
