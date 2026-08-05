package com.basiclab.iot.common.capability;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** One stable business capability from the versioned manifest. */
public final class CapabilityDefinition {

    private final boolean enabled;
    private final Map<String, Long> quota;
    private final List<String> requires;
    private final String reason;

    @JsonCreator
    public CapabilityDefinition(@JsonProperty("enabled") boolean enabled,
                                @JsonProperty("quota") Map<String, Long> quota,
                                @JsonProperty("requires") List<String> requires,
                                @JsonProperty("reason") String reason) {
        this.enabled = enabled;
        this.quota = Collections.unmodifiableMap(
                quota == null ? new LinkedHashMap<>() : new LinkedHashMap<>(quota));
        this.requires = Collections.unmodifiableList(
                requires == null ? new ArrayList<>() : new ArrayList<>(requires));
        this.reason = reason;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public Map<String, Long> getQuota() {
        return quota;
    }

    public List<String> getRequires() {
        return requires;
    }

    public String getReason() {
        return reason;
    }

    CapabilityDefinition immutableCopy() {
        return new CapabilityDefinition(enabled, quota, requires, reason);
    }
}
