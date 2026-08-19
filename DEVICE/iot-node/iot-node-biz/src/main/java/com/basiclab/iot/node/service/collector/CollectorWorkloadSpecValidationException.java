package com.basiclab.iot.node.service.collector;

/** WorkloadSpec 1.0 的稳定、脱敏校验异常。 */
public final class CollectorWorkloadSpecValidationException extends IllegalArgumentException {

    private final String code;

    public CollectorWorkloadSpecValidationException(String code, String detail) {
        super(code + ": " + detail);
        this.code = code;
    }

    public String getCode() {
        return code;
    }
}
