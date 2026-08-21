package com.basiclab.iot.sink.telemetry.outbox.backfill;

import com.basiclab.iot.sink.telemetry.envelope.EnvelopeJcsCanonicalizer;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.regex.Pattern;

/** Content-addressed canonical bytes for a resolved route manifest. */
public record RouteBackfillManifestArtifact(
        RouteBackfillManifest manifest,
        byte[] canonicalBytes,
        String contentSha256
) {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final EnvelopeJcsCanonicalizer JCS = new EnvelopeJcsCanonicalizer();
    private static final Pattern SHA256_HEX = Pattern.compile("[0-9a-f]{64}");

    public RouteBackfillManifestArtifact(RouteBackfillManifest manifest) {
        this(manifest, canonicalBytesFor(manifest), sha256Hex(canonicalBytesFor(manifest)));
    }

    public RouteBackfillManifestArtifact {
        if (manifest == null) {
            throw new IllegalArgumentException("manifest required");
        }
        if (canonicalBytes == null || canonicalBytes.length == 0) {
            throw new IllegalArgumentException("canonicalBytes required");
        }
        canonicalBytes = canonicalBytes.clone();
        if (contentSha256 == null || !SHA256_HEX.matcher(contentSha256).matches()) {
            throw new IllegalArgumentException("contentSha256 must be lowercase SHA-256 hex");
        }
        byte[] expectedBytes = canonicalBytesFor(manifest);
        if (!Arrays.equals(expectedBytes, canonicalBytes)) {
            throw new IllegalArgumentException("canonicalBytes do not represent manifest");
        }
        if (!sha256Hex(canonicalBytes).equals(contentSha256)) {
            throw new IllegalArgumentException("contentSha256 does not match canonicalBytes");
        }
    }

    @Override
    public byte[] canonicalBytes() {
        return canonicalBytes.clone();
    }

    private static byte[] canonicalBytesFor(RouteBackfillManifest manifest) {
        if (manifest == null) {
            throw new IllegalArgumentException("manifest required");
        }
        try {
            return JCS.canonicalize(MAPPER.valueToTree(manifest)).getBytes(StandardCharsets.UTF_8);
        } catch (RuntimeException e) {
            throw new IllegalArgumentException("manifest cannot be canonicalized", e);
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
