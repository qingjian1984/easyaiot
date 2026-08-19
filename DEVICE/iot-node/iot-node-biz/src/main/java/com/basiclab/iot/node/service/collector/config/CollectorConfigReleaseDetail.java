package com.basiclab.iot.node.service.collector.config;

/** iot-device release detail 的一次性内存投影；不被派发器保留或持久化。 */
public final class CollectorConfigReleaseDetail {

    private final String releaseId;
    private final String tenantId;
    private final String nodeId;
    private final String workloadId;
    private final String configVersion;
    private final String schemaVersion;
    private final String canonicalizationVersion;
    private final String payloadCanonical;
    private final String payloadSha256;
    private final Long canonicalLengthBytes;
    private final String publishedAt;

    public CollectorConfigReleaseDetail(String releaseId,
                                        String tenantId,
                                        String nodeId,
                                        String workloadId,
                                        String configVersion,
                                        String schemaVersion,
                                        String canonicalizationVersion,
                                        String payloadCanonical,
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
        this.payloadCanonical = payloadCanonical;
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

    public String getPayloadCanonical() { return payloadCanonical; }

    public String getPayloadSha256() { return payloadSha256; }

    public Long getCanonicalLengthBytes() { return canonicalLengthBytes; }

    public String getPublishedAt() { return publishedAt; }
}
