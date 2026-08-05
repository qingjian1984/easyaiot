package com.basiclab.iot.system.controller.admin.capability.vo;

import com.basiclab.iot.common.capability.CapabilityDefinition;
import com.basiclab.iot.common.capability.CapabilitySnapshot;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/** Public capability projection; deployment dependency topology stays server-side. */
public final class CapabilityRespVO {

    private final String schemaVersion;
    private final String manifestVersion;
    private final String profile;
    private final String product;
    private final String sha256;
    private final Map<String, CapabilityItemRespVO> capabilities;

    private CapabilityRespVO(CapabilitySnapshot snapshot) {
        this.schemaVersion = snapshot.getSchemaVersion();
        this.manifestVersion = snapshot.getManifestVersion();
        this.profile = snapshot.getProfile();
        this.product = snapshot.getProduct();
        this.sha256 = snapshot.getSha256();
        Map<String, CapabilityItemRespVO> items = new LinkedHashMap<>();
        snapshot.getCapabilities().forEach((code, definition) ->
                items.put(code, new CapabilityItemRespVO(definition)));
        this.capabilities = Collections.unmodifiableMap(items);
    }

    public static CapabilityRespVO from(CapabilitySnapshot snapshot) {
        return new CapabilityRespVO(snapshot);
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

    public Map<String, CapabilityItemRespVO> getCapabilities() {
        return capabilities;
    }

    public static final class CapabilityItemRespVO {

        private final boolean enabled;
        private final Map<String, Long> quota;
        private final String reason;

        private CapabilityItemRespVO(CapabilityDefinition definition) {
            this.enabled = definition.isEnabled();
            this.quota = definition.getQuota();
            this.reason = definition.getReason();
        }

        public boolean isEnabled() {
            return enabled;
        }

        public Map<String, Long> getQuota() {
            return quota;
        }

        public String getReason() {
            return reason;
        }
    }
}
