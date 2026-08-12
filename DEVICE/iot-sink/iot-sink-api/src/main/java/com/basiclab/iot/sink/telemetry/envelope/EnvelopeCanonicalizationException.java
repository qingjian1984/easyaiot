package com.basiclab.iot.sink.telemetry.envelope;

/**
 * TD-002 §6 Envelope 规范化失败（超 64KiB / 非 UTF-8 / 字段非法）。
 */
public class EnvelopeCanonicalizationException extends RuntimeException {
    public EnvelopeCanonicalizationException(String message) {
        super(message);
    }

    public EnvelopeCanonicalizationException(String message, Throwable cause) {
        super(message, cause);
    }
}
