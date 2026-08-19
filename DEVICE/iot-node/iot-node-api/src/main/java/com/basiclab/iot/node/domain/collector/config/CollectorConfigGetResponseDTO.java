package com.basiclab.iot.node.domain.collector.config;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import java.io.Serializable;

/** NODE collector 配置 GET 的闭合、脱敏响应。 */
@JsonIgnoreProperties(ignoreUnknown = false)
@JsonPropertyOrder({"workloadId", "desired", "active", "observed"})
public class CollectorConfigGetResponseDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    private String workloadId;
    private CollectorConfigStateSummaryDTO desired;
    private CollectorConfigStateSummaryDTO active;
    private CollectorConfigObservedSummaryDTO observed;

    public String getWorkloadId() { return workloadId; }
    public void setWorkloadId(String workloadId) { this.workloadId = workloadId; }
    public CollectorConfigStateSummaryDTO getDesired() { return desired; }
    public void setDesired(CollectorConfigStateSummaryDTO desired) { this.desired = desired; }
    public CollectorConfigStateSummaryDTO getActive() { return active; }
    public void setActive(CollectorConfigStateSummaryDTO active) { this.active = active; }
    public CollectorConfigObservedSummaryDTO getObserved() { return observed; }
    public void setObserved(CollectorConfigObservedSummaryDTO observed) { this.observed = observed; }
}
