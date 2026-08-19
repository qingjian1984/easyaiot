package com.basiclab.iot.device.domain.collector.dto;

import lombok.Data;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Pattern;
import javax.validation.constraints.Size;
import java.io.Serializable;

/** M1-LC-02A §4.2：Agent observed 回报。 */
@Data
public class CollectorConfigReleaseObservedRequestDTO implements Serializable {

    private static final long serialVersionUID = 1L;
    private static final String SHA256 = "[0-9a-f]{64}";

    /** BIGINT 以十进制字符串跨服务传输。 */
    @NotBlank
    private String releaseId;
    @NotBlank
    private String tenantId;
    @NotBlank
    private String nodeId;
    @NotBlank
    private String workloadId;
    @NotBlank
    private String configVersion;
    @NotBlank
    @Pattern(regexp = SHA256)
    private String payloadSha256;
    @NotNull
    private CollectorConfigReleaseObservedStatus status;
    @NotBlank
    @Size(max = 64)
    private String observedAt;
    @Size(max = 64)
    @Pattern(regexp = "[A-Za-z0-9._:-]*")
    private String errorCode;
    @Size(max = 256)
    private String errorDetailSanitized;
}
