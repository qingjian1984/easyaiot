package com.basiclab.iot.node.service.collector.config;

/**
 * iot-device pending 元数据的本地不可变投影。
 *
 * <p>它不是 iot-device 的公共 DTO；release client adapter 负责把已授权的内部 API 响应
 * 转换成该投影。iot-node 不持久化该对象，也不把 canonical 放入该类型。</p>
 */
public final class CollectorConfigReleasePending {

    private final String releaseId;
    private final String tenantId;
    private final String nodeId;
    private final String workloadId;
    private final String configVersion;
    private final String schemaVersion;
    private final String canonicalizationVersion;
    private final String payloadSha256;
    private final Long canonicalLengthBytes;
    private final String publishedAt;

    public CollectorConfigReleasePending(String releaseId,
                                         String tenantId,
                                         String nodeId,
                                         String workloadId,
                                         String configVersion,
                                         String schemaVersion,
                                         String canonicalizationVersion,
                                         String payloadSha256,
                                         Long canonicalLengthBytes,
                                         String publishedAt) {
        this.releaseId = releaseId;
        this.tenantId = tenantId;
        this.nodeId = nodeId;
        this.workloadId = workloadId;
        this.configVersion = configVersion;
        this.schemaVersion = schemaVersion;
        this.canonicalizationVersion = canonicalizationVersion;
        this.payloadSha256 = payloadSha256;
        this.canonicalLengthBytes = canonicalLengthBytes;
        this.publishedAt = publishedAt;
    }

    public String getReleaseId() { return releaseId; }

    public String getTenantId() { return tenantId; }

    public String getNodeId() { return nodeId; }

    public String getWorkloadId() { return workloadId; }

    public String getConfigVersion() { return configVersion; }

    public String getSchemaVersion() { return schemaVersion; }

    public String getCanonicalizationVersion() { return canonicalizationVersion; }

    public String getPayloadSha256() { return payloadSha256; }

    public Long getCanonicalLengthBytes() { return canonicalLengthBytes; }

    public String getPublishedAt() { return publishedAt; }
}
