package com.basiclab.iot.device.controller.power.dto;

import java.util.Collections;
import java.util.List;

/** TD-005 §11 固定失败响应 envelope。 */
public final class PowerModelTemplateErrorResponse {
    private final String code;
    private final String message;
    private final List<?> errors;
    private final String traceId;
    private final String timestamp;
    private final boolean retryable;

    public PowerModelTemplateErrorResponse(String code, String message, String traceId,
                                           String timestamp, boolean retryable) {
        this.code = code;
        this.message = message;
        this.errors = Collections.emptyList();
        this.traceId = traceId;
        this.timestamp = timestamp;
        this.retryable = retryable;
    }

    public String getCode() { return code; }
    public String getMessage() { return message; }
    public List<?> getErrors() { return errors; }
    public String getTraceId() { return traceId; }
    public String getTimestamp() { return timestamp; }
    public boolean isRetryable() { return retryable; }
}
