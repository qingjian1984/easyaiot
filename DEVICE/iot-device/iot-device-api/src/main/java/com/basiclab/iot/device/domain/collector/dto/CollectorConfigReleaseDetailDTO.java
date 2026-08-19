package com.basiclab.iot.device.domain.collector.dto;

import lombok.Data;

import java.io.Serializable;

/** M1-LC-02A §4.2：collector release 详情，canonical 文本原样返回。 */
@Data
public class CollectorConfigReleaseDetailDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** BIGINT 以十进制字符串跨服务传输。 */
    private String releaseId;
    private String tenantId;
    private String nodeId;
    private String workloadId;
    private String configVersion;
    private String schemaVersion;
    private String canonicalizationVersion;
    private String payloadCanonical;
    private String payloadSha256;
    private Long canonicalLengthBytes;
    private String publishedAt;
}
