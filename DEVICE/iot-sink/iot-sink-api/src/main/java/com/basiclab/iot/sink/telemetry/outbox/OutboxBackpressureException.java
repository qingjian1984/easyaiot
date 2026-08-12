package com.basiclab.iot.sink.telemetry.outbox;

/**
 * TD-002 §9 队列满且入队超时（背压）。
 *
 * <p>Poller 等待本地提交超时后必须保留原 messageId 重试（禁止重新生成 ID）；
 * STORED 或 DUPLICATE 均视为成功。
 */
public class OutboxBackpressureException extends RuntimeException {
    public OutboxBackpressureException(String message) {
        super(message);
    }

    public OutboxBackpressureException(String message, Throwable cause) {
        super(message, cause);
    }
}
