package com.basiclab.iot.sink.telemetry.outbox.backfill;

import com.basiclab.iot.sink.telemetry.envelope.EnvelopeJcsCanonicalizer;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.regex.Pattern;

/**
 * Canonical, content-addressed representation of a {@link RouteInventoryPage}.
 */
public record RouteInventoryArtifact(
        RouteInventoryPage page,
        byte[] canonicalBytes,
        String contentSha256
) {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final EnvelopeJcsCanonicalizer JCS = new EnvelopeJcsCanonicalizer();
    private static final Pattern SHA256_HEX = Pattern.compile("[0-9a-f]{64}");

    public RouteInventoryArtifact(RouteInventoryPage page) {
        this(page, canonicalBytesFor(page), sha256Hex(canonicalBytesFor(page)));
    }

    public RouteInventoryArtifact {
        if (page == null) {
            throw new IllegalArgumentException("page required");
        }
        if (canonicalBytes == null || canonicalBytes.length == 0) {
            throw new IllegalArgumentException("canonicalBytes required");
        }
        canonicalBytes = canonicalBytes.clone();
        if (contentSha256 == null || !SHA256_HEX.matcher(contentSha256).matches()) {
            throw new IllegalArgumentException("contentSha256 must be lowercase SHA-256 hex");
        }
        byte[] expectedBytes = canonicalBytesFor(page);
        if (!java.util.Arrays.equals(expectedBytes, canonicalBytes)) {
            throw new IllegalArgumentException("canonicalBytes do not represent page");
        }
        String expectedHash = sha256Hex(canonicalBytes);
        if (!expectedHash.equals(contentSha256)) {
            throw new IllegalArgumentException("contentSha256 does not match canonicalBytes");
        }
    }

    @Override
    public byte[] canonicalBytes() {
        return canonicalBytes.clone();
    }

    private static byte[] canonicalBytesFor(RouteInventoryPage page) {
        if (page == null) {
            throw new IllegalArgumentException("page required");
        }
        try {
            return JCS.canonicalize(MAPPER.valueToTree(page)).getBytes(StandardCharsets.UTF_8);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("page cannot be canonicalized", e);
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
