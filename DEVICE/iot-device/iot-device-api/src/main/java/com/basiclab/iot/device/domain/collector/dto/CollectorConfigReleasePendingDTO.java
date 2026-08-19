package com.basiclab.iot.device.domain.collector.dto;

import lombok.Data;

import java.io.Serializable;

/** M1-LC-02A §4.2：collector release pending 的轻量元数据。 */
@Data
public class CollectorConfigReleasePendingDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** BIGINT 以十进制字符串跨服务传输，避免 JavaScript/JSON 精度丢失。 */
    private String releaseId;
    private String tenantId;
    private String nodeId;
    private String workloadId;
    private String configVersion;
    private String schemaVersion;
    private String canonicalizationVersion;
    private String payloadSha256;
    private Long canonicalLengthBytes;
    private String publishedAt;
}
