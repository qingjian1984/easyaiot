package com.basiclab.iot.node.domain.collector.config;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import java.io.Serializable;

/** NODE GET 响应中的 desired/active 脱敏摘要。 */
@JsonIgnoreProperties(ignoreUnknown = false)
@JsonPropertyOrder({
        "present", "schemaVersion", "configVersion", "payloadSha256", "canonicalLengthBytes"
})
public class CollectorConfigStateSummaryDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    private Boolean present;
    private String schemaVersion;
    private Long configVersion;
    private String payloadSha256;
    private Long canonicalLengthBytes;

    public Boolean getPresent() { return present; }
    public void setPresent(Boolean present) { this.present = present; }
    public String getSchemaVersion() { return schemaVersion; }
    public void setSchemaVersion(String schemaVersion) { this.schemaVersion = schemaVersion; }
    public Long getConfigVersion() { return configVersion; }
    public void setConfigVersion(Long configVersion) { this.configVersion = configVersion; }
    public String getPayloadSha256() { return payloadSha256; }
    public void setPayloadSha256(String payloadSha256) { this.payloadSha256 = payloadSha256; }
    public Long getCanonicalLengthBytes() { return canonicalLengthBytes; }
    public void setCanonicalLengthBytes(Long canonicalLengthBytes) {
        this.canonicalLengthBytes = canonicalLengthBytes;
    }
}
