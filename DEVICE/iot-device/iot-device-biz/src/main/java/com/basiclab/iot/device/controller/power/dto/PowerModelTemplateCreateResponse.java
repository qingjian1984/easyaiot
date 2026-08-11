package com.basiclab.iot.device.controller.power.dto;

/** TD-005 §11.1：租户模板身份创建结果；bigint 始终以十进制字符串返回。 */
public final class PowerModelTemplateCreateResponse {
    private final String templateId;
    private final String templateCode;
    private final String ownerScope;
    private final String status;
    private final String rowVersion;

    public PowerModelTemplateCreateResponse(String templateId, String templateCode,
                                            String ownerScope, String status, long rowVersion) {
        this.templateId = templateId;
        this.templateCode = templateCode;
        this.ownerScope = ownerScope;
        this.status = status;
        this.rowVersion = Long.toString(rowVersion);
    }

    public String getTemplateId() { return templateId; }
    public String getTemplateCode() { return templateCode; }
    public String getOwnerScope() { return ownerScope; }
    public String getStatus() { return status; }
    public String getRowVersion() { return rowVersion; }
}
