package com.basiclab.iot.node.domain.collector.config;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import java.io.Serializable;

/** NODE GET 响应中的 observed 脱敏摘要。 */
@JsonIgnoreProperties(ignoreUnknown = false)
@JsonPropertyOrder({
        "workloadId", "status", "configVersion", "payloadSha256", "observedAt", "errorCode"
})
public class CollectorConfigObservedSummaryDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    private String workloadId;
    private CollectorConfigAgentStatus status;
    private Long configVersion;
    private String payloadSha256;
    private String observedAt;
    private String errorCode;

    public String getWorkloadId() { return workloadId; }
    public void setWorkloadId(String workloadId) { this.workloadId = workloadId; }
    public CollectorConfigAgentStatus getStatus() { return status; }
    public void setStatus(CollectorConfigAgentStatus status) { this.status = status; }
    public Long getConfigVersion() { return configVersion; }
    public void setConfigVersion(Long configVersion) { this.configVersion = configVersion; }
    public String getPayloadSha256() { return payloadSha256; }
    public void setPayloadSha256(String payloadSha256) { this.payloadSha256 = payloadSha256; }
    public String getObservedAt() { return observedAt; }
    public void setObservedAt(String observedAt) { this.observedAt = observedAt; }
    public String getErrorCode() { return errorCode; }
    public void setErrorCode(String errorCode) { this.errorCode = errorCode; }
}
