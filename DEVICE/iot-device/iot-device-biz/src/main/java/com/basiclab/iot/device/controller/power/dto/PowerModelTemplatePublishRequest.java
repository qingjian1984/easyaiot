package com.basiclab.iot.device.controller.power.dto;

/** TD-005 §7.1/§11.1：发布原因只用于审计，不影响服务端 SemVer 判定。 */
public class PowerModelTemplatePublishRequest {
    private String reasonCode;
    private String reasonSummary;

    public String getReasonCode() { return reasonCode; }
    public void setReasonCode(String reasonCode) { this.reasonCode = reasonCode; }
    public String getReasonSummary() { return reasonSummary; }
    public void setReasonSummary(String reasonSummary) { this.reasonSummary = reasonSummary; }
}
