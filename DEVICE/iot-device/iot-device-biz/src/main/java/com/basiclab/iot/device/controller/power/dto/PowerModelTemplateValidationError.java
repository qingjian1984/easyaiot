package com.basiclab.iot.device.controller.power.dto;

/** TD-005 §12：模板草稿校验的稳定机器错误。 */
public class PowerModelTemplateValidationError {

    private String code;
    private String templateCode;
    private String templateVersion;
    private String path;
    private String severity;
    private String message;

    public PowerModelTemplateValidationError() {
    }

    public PowerModelTemplateValidationError(String code, String templateCode,
                                             String templateVersion, String path,
                                             String severity, String message) {
        this.code = code;
        this.templateCode = templateCode;
        this.templateVersion = templateVersion;
        this.path = path;
        this.severity = severity;
        this.message = message;
    }

    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }
    public String getTemplateCode() { return templateCode; }
    public void setTemplateCode(String templateCode) { this.templateCode = templateCode; }
    public String getTemplateVersion() { return templateVersion; }
    public void setTemplateVersion(String templateVersion) { this.templateVersion = templateVersion; }
    public String getPath() { return path; }
    public void setPath(String path) { this.path = path; }
    public String getSeverity() { return severity; }
    public void setSeverity(String severity) { this.severity = severity; }
    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }
}
