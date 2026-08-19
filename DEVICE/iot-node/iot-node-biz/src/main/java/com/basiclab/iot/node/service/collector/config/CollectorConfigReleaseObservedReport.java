package com.basiclab.iot.node.service.collector.config;

import java.util.Objects;

/** iot-node 向 iot-device 回报的最小脱敏事实。 */
public final class CollectorConfigReleaseObservedReport {

    public enum Status {
        AGENT_ACCEPTED,
        APPLIED,
        FAILED
    }

    private final String releaseId;
    private final String tenantId;
    private final String nodeId;
    private final String workloadId;
    private final String configVersion;
    private final String payloadSha256;
    private final Status status;
    private final String observedAt;
    private final String errorCode;

    public CollectorConfigReleaseObservedReport(String releaseId,
                                                String tenantId,
                                                String nodeId,
                                                String workloadId,
                                                String configVersion,
                                                String payloadSha256,
                                                Status status,
                                                String observedAt,
                                                String errorCode) {
        this.releaseId = releaseId;
        this.tenantId = tenantId;
        this.nodeId = nodeId;
        this.workloadId = workloadId;
        this.configVersion = configVersion;
        this.payloadSha256 = payloadSha256;
        this.status = Objects.requireNonNull(status, "status");
        this.observedAt = observedAt;
        this.errorCode = errorCode;
    }

    public String getReleaseId() { return releaseId; }

    public String getTenantId() { return tenantId; }

    public String getNodeId() { return nodeId; }

    public String getWorkloadId() { return workloadId; }

    public String getConfigVersion() { return configVersion; }

    public String getPayloadSha256() { return payloadSha256; }

    public Status getStatus() { return status; }

    public String getObservedAt() { return observedAt; }

    public String getErrorCode() { return errorCode; }
}
