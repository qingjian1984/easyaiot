package com.basiclab.iot.node.domain.collector.config;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Pattern;
import java.io.Serializable;

/**
 * NODE 专用 collector 配置 PUT 请求。
 *
 * <p>字段顺序是稳定的，Agent 签名与实际发送的 JSON bytes 必须完全一致。配置版本和长度
 * 在 JSON 中保持 integer；跨服务的 release ID 仍使用十进制字符串。</p>
 */
@JsonIgnoreProperties(ignoreUnknown = false)
@JsonPropertyOrder({
        "workloadId", "configVersion", "schemaVersion", "canonicalizationVersion",
        "payloadCanonical", "payloadSha256", "canonicalLengthBytes"
})
public class CollectorConfigPutRequestDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    @NotBlank
    private String workloadId;

    @NotNull
    private Long configVersion;

    @NotBlank
    private String schemaVersion;

    @NotBlank
    private String canonicalizationVersion;

    @NotBlank
    private String payloadCanonical;

    @NotBlank
    @Pattern(regexp = "[0-9a-f]{64}")
    private String payloadSha256;

    @NotNull
    private Long canonicalLengthBytes;

    public String getWorkloadId() { return workloadId; }
    public void setWorkloadId(String workloadId) { this.workloadId = workloadId; }
    public Long getConfigVersion() { return configVersion; }
    public void setConfigVersion(Long configVersion) { this.configVersion = configVersion; }
    public String getSchemaVersion() { return schemaVersion; }
    public void setSchemaVersion(String schemaVersion) { this.schemaVersion = schemaVersion; }
    public String getCanonicalizationVersion() { return canonicalizationVersion; }
    public void setCanonicalizationVersion(String canonicalizationVersion) {
        this.canonicalizationVersion = canonicalizationVersion;
    }
    public String getPayloadCanonical() { return payloadCanonical; }
    public void setPayloadCanonical(String payloadCanonical) { this.payloadCanonical = payloadCanonical; }
    public String getPayloadSha256() { return payloadSha256; }
    public void setPayloadSha256(String payloadSha256) { this.payloadSha256 = payloadSha256; }
    public Long getCanonicalLengthBytes() { return canonicalLengthBytes; }
    public void setCanonicalLengthBytes(Long canonicalLengthBytes) {
        this.canonicalLengthBytes = canonicalLengthBytes;
    }
}
