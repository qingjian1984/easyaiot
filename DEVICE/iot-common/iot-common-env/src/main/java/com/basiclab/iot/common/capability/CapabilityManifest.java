package com.basiclab.iot.common.capability;

import java.util.LinkedHashMap;
import java.util.Map;

/** JSON representation of ADR-011 capability manifest schema 1.0. */
public class CapabilityManifest {

    private String schemaVersion;
    private String manifestVersion;
    private String profile;
    private String product;
    private Map<String, CapabilityDefinition> capabilities = new LinkedHashMap<>();

    public String getSchemaVersion() {
        return schemaVersion;
    }

    public void setSchemaVersion(String schemaVersion) {
        this.schemaVersion = schemaVersion;
    }

    public String getManifestVersion() {
        return manifestVersion;
    }

    public void setManifestVersion(String manifestVersion) {
        this.manifestVersion = manifestVersion;
    }

    public String getProfile() {
        return profile;
    }

    public void setProfile(String profile) {
        this.profile = profile;
    }

    public String getProduct() {
        return product;
    }

    public void setProduct(String product) {
        this.product = product;
    }

    public Map<String, CapabilityDefinition> getCapabilities() {
        return capabilities;
    }

    public void setCapabilities(Map<String, CapabilityDefinition> capabilities) {
        this.capabilities = capabilities == null ? new LinkedHashMap<>() : new LinkedHashMap<>(capabilities);
    }
}
