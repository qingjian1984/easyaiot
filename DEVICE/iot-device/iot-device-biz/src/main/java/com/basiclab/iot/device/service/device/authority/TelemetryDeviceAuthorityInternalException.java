package com.basiclab.iot.device.service.device.authority;

/** Stable, sanitized errors for the ADR-018 authority endpoint. */
public class TelemetryDeviceAuthorityInternalException extends RuntimeException {

    private final String code;
    private final int httpStatus;

    public TelemetryDeviceAuthorityInternalException(String code, int httpStatus) {
        super(code);
        this.code = code;
        this.httpStatus = httpStatus;
    }

    public TelemetryDeviceAuthorityInternalException(String code, int httpStatus, Throwable cause) {
        super(code, cause);
        this.code = code;
        this.httpStatus = httpStatus;
    }

    public String getCode() {
        return code;
    }

    public int getHttpStatus() {
        return httpStatus;
    }
}
