package com.basiclab.iot.common.capability;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.io.InputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Collections;
import java.util.Map;
import java.util.regex.Pattern;

/** Loads and validates one immutable ADR-011 manifest. */
public final class ManifestCapabilityService implements CapabilityService {

    public static final String SCHEMA_VERSION = "1.0";
    public static final String PRODUCT = "power-operations";
    private static final Pattern CAPABILITY_CODE = Pattern.compile(
            "^power\\.[a-z][a-z0-9-]*(\\.[a-z][a-z0-9-]*)+$");

    private final CapabilitySnapshot snapshot;

    private ManifestCapabilityService(CapabilitySnapshot snapshot) {
        this.snapshot = snapshot;
    }

    public static ManifestCapabilityService load(InputStream input, ObjectMapper sourceMapper) throws IOException {
        byte[] bytes = input.readAllBytes();
        ObjectMapper mapper = sourceMapper.copy()
                .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
        CapabilityManifest manifest = mapper.readValue(bytes, CapabilityManifest.class);
        validate(manifest);
        return new ManifestCapabilityService(new CapabilitySnapshot(
                manifest.getSchemaVersion(), manifest.getManifestVersion(), manifest.getProfile(),
                manifest.getProduct(), sha256(bytes), manifest.getCapabilities()));
    }

    public static ManifestCapabilityService disabled(String profile) {
        String effectiveProfile = profile == null || profile.trim().isEmpty() ? "unconfigured" : profile.trim();
        return new ManifestCapabilityService(new CapabilitySnapshot(
                SCHEMA_VERSION, "0.0.0", effectiveProfile, PRODUCT, sha256(new byte[0]), Collections.emptyMap()));
    }

    private static void validate(CapabilityManifest manifest) {
        require(SCHEMA_VERSION.equals(manifest.getSchemaVersion()), "Unsupported schemaVersion");
        require(manifest.getManifestVersion() != null
                        && manifest.getManifestVersion().matches("^[0-9]+\\.[0-9]+\\.[0-9]+$"),
                "Invalid manifestVersion");
        require("standard".equals(manifest.getProfile()) || "full".equals(manifest.getProfile()),
                "Invalid profile");
        require(PRODUCT.equals(manifest.getProduct()), "Invalid product");
        require(manifest.getCapabilities() != null && !manifest.getCapabilities().isEmpty(),
                "Capabilities must not be empty");
        for (Map.Entry<String, CapabilityDefinition> entry : manifest.getCapabilities().entrySet()) {
            require(CAPABILITY_CODE.matcher(entry.getKey()).matches(),
                    "Invalid capability code: " + entry.getKey());
            CapabilityDefinition definition = entry.getValue();
            require(definition != null, "Missing capability definition: " + entry.getKey());
            require(definition.getQuota() != null, "Missing quota: " + entry.getKey());
            definition.getQuota().forEach((key, value) -> {
                require(key != null && !key.trim().isEmpty(), "Invalid quota key: " + entry.getKey());
                require(value != null && value >= 0, "Invalid quota value: " + entry.getKey() + "." + key);
            });
            require(definition.getRequires() != null, "Missing requires: " + entry.getKey());
            if (!definition.isEnabled()) {
                require(definition.getReason() != null && !definition.getReason().trim().isEmpty(),
                        "Disabled capability requires reason: " + entry.getKey());
            }
        }
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new IllegalArgumentException(message);
        }
    }

    private static String sha256(byte[] bytes) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(bytes);
            StringBuilder result = new StringBuilder(digest.length * 2);
            for (byte value : digest) {
                result.append(String.format("%02x", value & 0xff));
            }
            return result.toString();
        } catch (NoSuchAlgorithmException error) {
            throw new IllegalStateException("SHA-256 unavailable", error);
        }
    }

    @Override
    public CapabilitySnapshot snapshot() {
        return snapshot;
    }
}
