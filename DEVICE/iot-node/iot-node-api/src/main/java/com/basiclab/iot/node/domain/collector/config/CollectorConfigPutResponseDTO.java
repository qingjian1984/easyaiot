package com.basiclab.iot.node.domain.collector.config;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import java.io.Serializable;

/** NODE collector 配置 PUT 的脱敏响应。 */
@JsonIgnoreProperties(ignoreUnknown = false)
@JsonPropertyOrder({"status", "workloadId", "configVersion", "payloadSha256"})
public class CollectorConfigPutResponseDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    private CollectorConfigAgentStatus status;
    private String workloadId;
    private Long configVersion;
    private String payloadSha256;

    public CollectorConfigAgentStatus getStatus() { return status; }
    public void setStatus(CollectorConfigAgentStatus status) { this.status = status; }
    public String getWorkloadId() { return workloadId; }
    public void setWorkloadId(String workloadId) { this.workloadId = workloadId; }
    public Long getConfigVersion() { return configVersion; }
    public void setConfigVersion(Long configVersion) { this.configVersion = configVersion; }
    public String getPayloadSha256() { return payloadSha256; }
    public void setPayloadSha256(String payloadSha256) { this.payloadSha256 = payloadSha256; }
}
