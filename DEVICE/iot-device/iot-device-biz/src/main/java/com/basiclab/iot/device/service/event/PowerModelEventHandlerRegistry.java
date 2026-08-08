package com.basiclab.iot.device.service.event;

import com.basiclab.iot.device.event.PowerModelEventEnvelope;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * ADR-014：事件业务处理器分发（消费侧）。
 * TD-001 collector 配置发布协调器按 eventType 注册处理器；
 * 已知主版本但未注册处理器的事件按 final 失败处置（进 DLQ），
 * 绝不静默丢弃（宪法 §15：未处理必须有持久证据）。
 */
public final class PowerModelEventHandlerRegistry {

    /** 事件处理器。 */
    public interface PowerModelEventHandler {
        /**
         * 处理一条已摄入（Inbox RECEIVED）的事件。
         *
         * @throws PowerModelEventProcessingException 处理失败（retryable/final 分流）
         */
        void handle(PowerModelEventEnvelope envelope, String dataJson);
    }

    /** 处理失败异常：retryable=true 可重试（退避），false 为 final（进 DLQ）。 */
    public static final class PowerModelEventProcessingException extends RuntimeException {
        private final boolean retryable;
        private final String errorCode;

        public PowerModelEventProcessingException(boolean retryable, String errorCode,
                                                  String message, Throwable cause) {
            super(message, cause);
            this.retryable = retryable;
            this.errorCode = Objects.requireNonNull(errorCode, "errorCode");
        }

        public boolean isRetryable() {
            return retryable;
        }

        public String errorCode() {
            return errorCode;
        }
    }

    private final Map<String, PowerModelEventHandler> handlers;

    public PowerModelEventHandlerRegistry(Map<String, PowerModelEventHandler> handlers) {
        Objects.requireNonNull(handlers, "handlers");
        this.handlers = Collections.unmodifiableMap(new LinkedHashMap<String, PowerModelEventHandler>(handlers));
    }

    /** 查找处理器；未注册返回 null（由编排器按 final 失败处置）。 */
    public PowerModelEventHandler find(String eventType) {
        return handlers.get(eventType);
    }
}
