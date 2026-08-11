package com.basiclab.iot.device.controller.power.dto;

import java.util.ArrayList;
import java.util.List;

/** TD-005 §7.1/§11.1/§12：草稿全量校验结果。 */
public class PowerModelTemplateValidationResponse {

    private String draftId;
    private String templateCode;
    private String templateVersion;
    private String contentHash;
    private boolean valid;
    private String comparisonVersion;
    private String minimumBump;
    private List<PowerModelTemplateValidationError> errors = new ArrayList<>();

    public String getDraftId() { return draftId; }
    public void setDraftId(String draftId) { this.draftId = draftId; }
    public String getTemplateCode() { return templateCode; }
    public void setTemplateCode(String templateCode) { this.templateCode = templateCode; }
    public String getTemplateVersion() { return templateVersion; }
    public void setTemplateVersion(String templateVersion) { this.templateVersion = templateVersion; }
    public String getContentHash() { return contentHash; }
    public void setContentHash(String contentHash) { this.contentHash = contentHash; }
    public boolean isValid() { return valid; }
    public void setValid(boolean valid) { this.valid = valid; }
    public String getComparisonVersion() { return comparisonVersion; }
    public void setComparisonVersion(String comparisonVersion) { this.comparisonVersion = comparisonVersion; }
    public String getMinimumBump() { return minimumBump; }
    public void setMinimumBump(String minimumBump) { this.minimumBump = minimumBump; }
    public List<PowerModelTemplateValidationError> getErrors() { return errors; }
    public void setErrors(List<PowerModelTemplateValidationError> errors) { this.errors = errors; }
}
