package com.basiclab.iot.node.domain.collector;

import java.util.Locale;

/**
 * 安装档位。collector 只允许在 standard/full 运行；mini 必须 fail-closed。
 */
public enum CollectorDeploymentProfile {

    MINI("mini"),
    STANDARD("standard"),
    FULL("full");

    private final String value;

    CollectorDeploymentProfile(String value) {
        this.value = value;
    }

    public String getValue() {
        return value;
    }

    public static CollectorDeploymentProfile of(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String normalized = value.trim().toLowerCase(Locale.ROOT);
        for (CollectorDeploymentProfile profile : values()) {
            if (profile.value.equals(normalized)) {
                return profile;
            }
        }
        return null;
    }
}
