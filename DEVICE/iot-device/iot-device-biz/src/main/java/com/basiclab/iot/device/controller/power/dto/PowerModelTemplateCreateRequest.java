package com.basiclab.iot.device.controller.power.dto;

/** TD-005 §11.1：创建当前租户模板身份；tenant、ownerScope 和审计主体由服务端取得。 */
public class PowerModelTemplateCreateRequest {
    private String templateCode;
    private String templateName;
    private String deviceType;
    private String templateKind;

    public String getTemplateCode() { return templateCode; }
    public void setTemplateCode(String templateCode) { this.templateCode = templateCode; }
    public String getTemplateName() { return templateName; }
    public void setTemplateName(String templateName) { this.templateName = templateName; }
    public String getDeviceType() { return deviceType; }
    public void setDeviceType(String deviceType) { this.deviceType = deviceType; }
    public String getTemplateKind() { return templateKind; }
    public void setTemplateKind(String templateKind) { this.templateKind = templateKind; }
}
