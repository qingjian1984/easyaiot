package com.basiclab.iot.common.capability;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/** Immutable effective capability view suitable for a future read-only system API. */
public final class CapabilitySnapshot {

    private final String schemaVersion;
    private final String manifestVersion;
    private final String profile;
    private final String product;
    private final String sha256;
    private final Map<String, CapabilityDefinition> capabilities;

    CapabilitySnapshot(String schemaVersion, String manifestVersion, String profile, String product,
                       String sha256, Map<String, CapabilityDefinition> capabilities) {
        this.schemaVersion = schemaVersion;
        this.manifestVersion = manifestVersion;
        this.profile = profile;
        this.product = product;
        this.sha256 = sha256;
        Map<String, CapabilityDefinition> copy = new LinkedHashMap<>();
        capabilities.forEach((key, value) -> copy.put(key, value.immutableCopy()));
        this.capabilities = Collections.unmodifiableMap(copy);
    }

    public String getSchemaVersion() {
        return schemaVersion;
    }

    public String getManifestVersion() {
        return manifestVersion;
    }

    public String getProfile() {
        return profile;
    }

    public String getProduct() {
        return product;
    }

    public String getSha256() {
        return sha256;
    }

    public Map<String, CapabilityDefinition> getCapabilities() {
        return capabilities;
    }
}
