package com.basiclab.iot.sink.outbox.sqlite;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * TD-002 §8 outbox 配置（候选值待 TD-001 压测冻结，非生产值）。
 */
@ConfigurationProperties(prefix = "easyaiot.outbox")
public class SqliteOutboxConfig {

    private String volumePath = "/var/lib/easyaiot/outbox";
    private int queueCapacity = 4096;
    private boolean enabled = true;

    public String getVolumePath() {
        return volumePath;
    }

    public void setVolumePath(String volumePath) {
        this.volumePath = volumePath;
    }

    public int getQueueCapacity() {
        return queueCapacity;
    }

    public void setQueueCapacity(int queueCapacity) {
        this.queueCapacity = queueCapacity;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }
}
