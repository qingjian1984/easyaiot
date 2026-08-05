package com.basiclab.iot.common.capability;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** Runtime location/profile of the effective capability manifest. */
@ConfigurationProperties(prefix = "easyaiot.capability")
public class CapabilityProperties {

    private String manifestLocation;
    private String profile = "unconfigured";

    public String getManifestLocation() {
        return manifestLocation;
    }

    public void setManifestLocation(String manifestLocation) {
        this.manifestLocation = manifestLocation;
    }

    public String getProfile() {
        return profile;
    }

    public void setProfile(String profile) {
        this.profile = profile;
    }
}
