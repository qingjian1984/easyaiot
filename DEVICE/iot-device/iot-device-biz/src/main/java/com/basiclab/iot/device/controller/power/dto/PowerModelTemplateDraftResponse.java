package com.basiclab.iot.device.controller.power.dto;

/** TD-005 §11.1：草稿写入结果；ETag 是 draftRevision 的强校验器。 */
public final class PowerModelTemplateDraftResponse {
    private final String draftId;
    private final String templateCode;
    private final String version;
    private final String lifecycle;
    private final String draftRevision;
    private final String etag;
    private final String contentHash;

    public PowerModelTemplateDraftResponse(String draftId, String templateCode, String version,
                                           String lifecycle, long draftRevision,
                                           String contentHash) {
        this.draftId = draftId;
        this.templateCode = templateCode;
        this.version = version;
        this.lifecycle = lifecycle;
        this.draftRevision = Long.toString(draftRevision);
        this.etag = "\"" + draftRevision + "\"";
        this.contentHash = contentHash;
    }

    public String getDraftId() { return draftId; }
    public String getTemplateCode() { return templateCode; }
    public String getVersion() { return version; }
    public String getLifecycle() { return lifecycle; }
    public String getDraftRevision() { return draftRevision; }
    public String getEtag() { return etag; }
    public String getContentHash() { return contentHash; }
}
