package com.basiclab.iot.node.service.collector.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** collector 配置派发的安装侧参数；默认值由 application.yaml 保持关闭生产轮询。 */
@ConfigurationProperties(prefix = "easyaiot.collector.config-dispatch")
public class CollectorConfigDispatchProperties {

    private boolean enabled;
    private int batchLimit = CollectorConfigDispatchJob.DEFAULT_BATCH_LIMIT;
    private long connectTimeoutMs = 3_000L;
    private long readTimeoutMs = 10_000L;
    private long baseDelayMs = 1_000L;
    private long maxDelayMs = 60_000L;
    private long fixedDelayMs = 30_000L;
    private String signingKeyFile = "";

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public int getBatchLimit() {
        return batchLimit;
    }

    public void setBatchLimit(int batchLimit) {
        this.batchLimit = batchLimit;
    }

    public long getConnectTimeoutMs() {
        return connectTimeoutMs;
    }

    public void setConnectTimeoutMs(long connectTimeoutMs) {
        this.connectTimeoutMs = connectTimeoutMs;
    }

    public long getReadTimeoutMs() {
        return readTimeoutMs;
    }

    public void setReadTimeoutMs(long readTimeoutMs) {
        this.readTimeoutMs = readTimeoutMs;
    }

    public long getBaseDelayMs() {
        return baseDelayMs;
    }

    public void setBaseDelayMs(long baseDelayMs) {
        this.baseDelayMs = baseDelayMs;
    }

    public long getMaxDelayMs() {
        return maxDelayMs;
    }

    public void setMaxDelayMs(long maxDelayMs) {
        this.maxDelayMs = maxDelayMs;
    }

    public long getFixedDelayMs() {
        return fixedDelayMs;
    }

    public void setFixedDelayMs(long fixedDelayMs) {
        this.fixedDelayMs = fixedDelayMs;
    }

    public String getSigningKeyFile() {
        return signingKeyFile;
    }

    public void setSigningKeyFile(String signingKeyFile) {
        this.signingKeyFile = signingKeyFile;
    }
}
