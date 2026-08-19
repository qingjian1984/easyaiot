package com.basiclab.iot.node.service.collector.config;

/** 派发结果的脱敏、不可变摘要。 */
public final class CollectorConfigDispatchOutcome {

    private final String releaseId;
    private final CollectorConfigDispatchStatus status;
    private final String stableCode;

    public CollectorConfigDispatchOutcome(String releaseId,
                                          CollectorConfigDispatchStatus status,
                                          String stableCode) {
        this.releaseId = releaseId;
        this.status = status;
        this.stableCode = stableCode;
    }

    public String getReleaseId() { return releaseId; }

    public CollectorConfigDispatchStatus getStatus() { return status; }

    public String getStableCode() { return stableCode; }
}
