package com.basiclab.iot.device.controller.power.dto;

/** TD-005 §11.1：模板发布成功的最小稳定响应。 */
public class PowerModelTemplatePublishResponse {
    private String templateVersionId;
    private String templateCode;
    private String version;
    private String lifecycle;
    private String contentHash;
    private String schemaVersion;
    private String canonicalizationVersion;
    private String publishedAt;
    private String sourceEventId;

    public PowerModelTemplatePublishResponse() { }

    public PowerModelTemplatePublishResponse(String templateVersionId, String templateCode,
                                             String version, String contentHash,
                                             String publishedAt, String sourceEventId) {
        this.templateVersionId = templateVersionId;
        this.templateCode = templateCode;
        this.version = version;
        this.lifecycle = "PUBLISHED";
        this.contentHash = contentHash;
        this.schemaVersion = "1.0.0";
        this.canonicalizationVersion = "jcs-rfc8785-v1";
        this.publishedAt = publishedAt;
        this.sourceEventId = sourceEventId;
    }

    public String getTemplateVersionId() { return templateVersionId; }
    public String getTemplateCode() { return templateCode; }
    public String getVersion() { return version; }
    public String getLifecycle() { return lifecycle; }
    public String getContentHash() { return contentHash; }
    public String getSchemaVersion() { return schemaVersion; }
    public String getCanonicalizationVersion() { return canonicalizationVersion; }
    public String getPublishedAt() { return publishedAt; }
    public String getSourceEventId() { return sourceEventId; }
    public void setTemplateVersionId(String value) { this.templateVersionId = value; }
    public void setTemplateCode(String value) { this.templateCode = value; }
    public void setVersion(String value) { this.version = value; }
    public void setLifecycle(String value) { this.lifecycle = value; }
    public void setContentHash(String value) { this.contentHash = value; }
    public void setSchemaVersion(String value) { this.schemaVersion = value; }
    public void setCanonicalizationVersion(String value) { this.canonicalizationVersion = value; }
    public void setPublishedAt(String value) { this.publishedAt = value; }
    public void setSourceEventId(String value) { this.sourceEventId = value; }
}
