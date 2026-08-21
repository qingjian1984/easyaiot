package com.basiclab.iot.sink.telemetry.outbox.backfill;

import com.basiclab.iot.sink.telemetry.envelope.EnvelopeJcsCanonicalizer;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.regex.Pattern;

/**
 * Signed, content-addressed representation of a route backfill authorization.
 * The byte array is copied on ingress and egress so a transport cannot mutate
 * the bytes covered by the authorization after construction.
 */
public record RouteBackfillAuthorizationArtifact(
        RouteBackfillAuthorization authorization,
        byte[] canonicalBytes,
        String contentSha256,
        String signatureBase64
) {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final EnvelopeJcsCanonicalizer JCS = new EnvelopeJcsCanonicalizer();
    private static final Pattern SHA256_HEX = Pattern.compile("[0-9a-f]{64}");

    public RouteBackfillAuthorizationArtifact {
        if (authorization == null) {
            throw new IllegalArgumentException("authorization required");
        }
        if (canonicalBytes == null || canonicalBytes.length == 0) {
            throw new IllegalArgumentException("canonicalBytes required");
        }
        if (contentSha256 == null || !SHA256_HEX.matcher(contentSha256).matches()) {
            throw new IllegalArgumentException("contentSha256 must be lowercase SHA-256 hex");
        }
        canonicalBytes = canonicalBytes.clone();
        if (!Arrays.equals(canonicalBytesFor(authorization), canonicalBytes)) {
            throw new IllegalArgumentException("canonicalBytes do not represent authorization");
        }
        if (!sha256Hex(canonicalBytes).equals(contentSha256)) {
            throw new IllegalArgumentException("contentSha256 does not match canonicalBytes");
        }
    }

    @Override
    public byte[] canonicalBytes() {
        return canonicalBytes == null ? null : canonicalBytes.clone();
    }

    private static byte[] canonicalBytesFor(RouteBackfillAuthorization authorization) {
        try {
            return JCS.canonicalize(MAPPER.valueToTree(authorization))
                    .getBytes(StandardCharsets.UTF_8);
        } catch (RuntimeException e) {
            throw new IllegalArgumentException("authorization cannot be canonicalized", e);
        }
    }

    private static String sha256Hex(byte[] value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(value);
            StringBuilder hex = new StringBuilder(64);
            for (byte b : digest) {
                hex.append(Character.forDigit((b >>> 4) & 0x0f, 16));
                hex.append(Character.forDigit(b & 0x0f, 16));
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("JDK missing SHA-256", e);
        }
    }
}
