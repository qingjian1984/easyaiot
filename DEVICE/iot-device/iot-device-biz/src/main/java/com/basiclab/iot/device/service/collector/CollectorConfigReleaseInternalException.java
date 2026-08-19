package com.basiclab.iot.device.service.collector;

/** 内部 collector release API 的稳定、脱敏业务错误。 */
public class CollectorConfigReleaseInternalException extends RuntimeException {

    private final String code;

    public CollectorConfigReleaseInternalException(String code) {
        super(code);
        this.code = code;
    }

    public String getCode() {
        return code;
    }
}
