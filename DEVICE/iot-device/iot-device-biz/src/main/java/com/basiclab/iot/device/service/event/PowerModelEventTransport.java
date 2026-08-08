package com.basiclab.iot.device.service.event;

import java.util.Objects;

/**
 * ADR-014 §决策 MUST：事件传输端口（生产侧 Kafka、测试侧 fake 可替换）。
 * 数据库事务提交前不得调用 send；发布器只在 claim 之后调用。
 */
public interface PowerModelEventTransport {

    /**
     * 发送一条事件。
     *
     * @param topic   目标 topic（ADR-014：power-model-release-v1）
     * @param key     分区键（tenantId:aggregateType:aggregateId）
     * @param payload 规范序列化正文
     */
    TransportResult send(String topic, String key, String payload);

    /** 发送结果。 */
    final class TransportResult {
        private final boolean success;
        private final boolean retryable;
        private final String errorCode;
        private final String errorDigest;

        private TransportResult(boolean success, boolean retryable,
                                String errorCode, String errorDigest) {
            this.success = success;
            this.retryable = retryable;
            this.errorCode = errorCode;
            this.errorDigest = errorDigest;
        }

        public static TransportResult success() {
            return new TransportResult(true, false, null, null);
        }

        /**
         * @param retryable   true=可重试错误（连接/超时/可重试 broker 错误）；
         *                    false=final 错误（消息超限、序列化失败、未知 topic）
         * @param errorCode   稳定错误码（不得含敏感值）
         * @param errorDigest 脱敏错误摘要（不得含 payload 正文）
         */
        public static TransportResult failure(boolean retryable, String errorCode, String errorDigest) {
            return new TransportResult(false, retryable,
                    Objects.requireNonNull(errorCode, "errorCode"),
                    Objects.requireNonNull(errorDigest, "errorDigest"));
        }

        public boolean isSuccess() {
            return success;
        }

        public boolean isRetryable() {
            return retryable;
        }

        public String errorCode() {
            return errorCode;
        }

        public String errorDigest() {
            return errorDigest;
        }
    }
}
