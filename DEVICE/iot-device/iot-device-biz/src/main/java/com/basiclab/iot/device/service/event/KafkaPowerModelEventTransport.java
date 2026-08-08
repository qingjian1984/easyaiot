package com.basiclab.iot.device.service.event;

import org.apache.kafka.common.errors.RetriableException;
import org.springframework.kafka.KafkaException;
import org.springframework.kafka.core.KafkaTemplate;

import java.util.Objects;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * ADR-014：{@link PowerModelEventTransport} 的 Kafka 适配（薄封装，可替换）。
 * 错误分流（OUT-004）：RetriableException/超时/连接中断 → retryable；
 * 其余（序列化、消息超限、未知 topic 等）→ final。
 * 错误摘要做长度与内容脱敏：仅异常类名，绝不携带 payload 正文。Java 8 兼容。
 */
public class KafkaPowerModelEventTransport implements PowerModelEventTransport {

    private static final int MAX_DIGEST_LENGTH = 128;

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final long sendTimeoutMs;

    public KafkaPowerModelEventTransport(KafkaTemplate<String, String> kafkaTemplate,
                                         long sendTimeoutMs) {
        this.kafkaTemplate = Objects.requireNonNull(kafkaTemplate, "kafkaTemplate");
        if (sendTimeoutMs < 1) {
            throw new IllegalArgumentException(
                    "MODEL_EVENT_RETRY_POLICY_INVALID: sendTimeoutMs 必须 >= 1");
        }
        this.sendTimeoutMs = sendTimeoutMs;
    }

    @Override
    public TransportResult send(String topic, String key, String payload) {
        try {
            kafkaTemplate.send(topic, key, payload).get(sendTimeoutMs, TimeUnit.MILLISECONDS);
            return TransportResult.success();
        } catch (TimeoutException e) {
            return TransportResult.failure(true, "MODEL_EVENT_SEND_TIMEOUT", digest(e));
        } catch (ExecutionException e) {
            Throwable cause = e.getCause() == null ? e : e.getCause();
            return TransportResult.failure(isRetryable(cause), errorCode(cause), digest(cause));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return TransportResult.failure(true, "MODEL_EVENT_SEND_INTERRUPTED", digest(e));
        } catch (KafkaException e) {
            return TransportResult.failure(isRetryable(e), errorCode(e), digest(e));
        }
    }

    private static boolean isRetryable(Throwable error) {
        Throwable current = error;
        while (current != null) {
            if (current instanceof RetriableException) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

    private static String errorCode(Throwable error) {
        return isRetryable(error) ? "MODEL_EVENT_SEND_RETRYABLE" : "MODEL_EVENT_SEND_FINAL";
    }

    /** 脱敏摘要：仅异常类全名，截断到 DDL 列宽；不含 message（可能夹带敏感上下文）。 */
    private static String digest(Throwable error) {
        String name = error.getClass().getName();
        return name.length() <= MAX_DIGEST_LENGTH ? name : name.substring(0, MAX_DIGEST_LENGTH);
    }
}
