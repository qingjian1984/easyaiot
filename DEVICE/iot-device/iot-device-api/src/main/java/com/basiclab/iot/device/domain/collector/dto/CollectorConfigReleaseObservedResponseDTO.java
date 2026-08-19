package com.basiclab.iot.device.domain.collector.dto;

import lombok.Data;

import java.io.Serializable;

/** observed CAS 的稳定、脱敏响应。 */
@Data
public class CollectorConfigReleaseObservedResponseDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** BIGINT 以十进制字符串跨服务传输。 */
    private String releaseId;
    private CollectorConfigReleaseObservedStatus status;
    private boolean accepted;
    private boolean terminal;
    private boolean idempotent;
}
