package com.basiclab.iot.sink.telemetry.outbox;

/**
 * TD-002 §9 存储不可用（损坏/只读/磁盘故障/I-O 错误）。
 *
 * <p>不静默吞掉；Poller 收到后按 RETRYABLE 处理（TD-002 §10 背压退避）。
 */
public class OutboxUnavailableException extends RuntimeException {
    public OutboxUnavailableException(String message) {
        super(message);
    }

    public OutboxUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
