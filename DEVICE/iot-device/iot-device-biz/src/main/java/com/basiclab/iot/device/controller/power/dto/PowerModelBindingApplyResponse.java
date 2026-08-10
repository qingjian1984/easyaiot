package com.basiclab.iot.device.controller.power.dto;

/** TD-005 §11.4：绑定与 collector 候选原子创建结果。 */
public final class PowerModelBindingApplyResponse {

    private final String bindingId;
    private final String bindingRevision;
    private final String collectorConfigReleaseId;
    private final String configVersion;
    private final String sourceEventId;
    private final String status;

    public PowerModelBindingApplyResponse(String bindingId, long bindingRevision,
                                          String collectorConfigReleaseId, long configVersion,
                                          String sourceEventId, String status) {
        this.bindingId = bindingId;
        this.bindingRevision = Long.toString(bindingRevision);
        this.collectorConfigReleaseId = collectorConfigReleaseId;
        this.configVersion = Long.toString(configVersion);
        this.sourceEventId = sourceEventId;
        this.status = status;
    }

    public String getBindingId() { return bindingId; }
    public String getBindingRevision() { return bindingRevision; }
    public String getCollectorConfigReleaseId() { return collectorConfigReleaseId; }
    public String getConfigVersion() { return configVersion; }
    public String getSourceEventId() { return sourceEventId; }
    public String getStatus() { return status; }
}
