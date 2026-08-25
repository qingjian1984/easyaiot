package com.basiclab.iot.device.alarm.infrastructure.event;

/**
 * 告警 Outbox transport 端口。
 *
 * <p>本任务只定义端口和 fake 合同，不提供 Kafka/MQTT 适配。只有明确的
 * broker acknowledgement 才允许 relay 将 Outbox 标记为 PUBLISHED。</p>
 */
public interface AlarmOutboxTransport {

    /** 发布完整的已认领条目；实现不得把 payload 写入日志或错误摘要。 */
    TransportResult send(AlarmOutboxClaimedEntry entry);

    /** 不含 transport 依赖的发送结果。 */
    final class TransportResult {
        private final boolean brokerAcknowledged;
        private final boolean retryable;
        private final String errorCode;
        private final String errorSummary;

        private TransportResult(boolean brokerAcknowledged, boolean retryable,
                                String errorCode, String errorSummary) {
            this.brokerAcknowledged = brokerAcknowledged;
            this.retryable = retryable;
            this.errorCode = errorCode;
            this.errorSummary = errorSummary;
        }

        /** broker 已确认；error 字段必须被 relay 忽略。 */
        public static TransportResult acknowledged() {
            return new TransportResult(true, false, null, null);
        }

        /** retryable 失败；最终摘要仍由 relay 统一脱敏。 */
        public static TransportResult retryable(String errorCode, String errorSummary) {
            return new TransportResult(false, true, errorCode, errorSummary);
        }

        /** final 失败；最终摘要仍由 relay 统一脱敏。 */
        public static TransportResult finalFailure(String errorCode, String errorSummary) {
            return new TransportResult(false, false, errorCode, errorSummary);
        }

        /** 通用工厂，便于 fake transport 构造结果。 */
        public static TransportResult failure(boolean retryable, String errorCode,
                                              String errorSummary) {
            return new TransportResult(false, retryable, errorCode, errorSummary);
        }

        public boolean brokerAcknowledged() {
            return brokerAcknowledged;
        }

        public boolean isAcknowledged() {
            return brokerAcknowledged;
        }

        public boolean isSuccess() {
            return brokerAcknowledged;
        }

        public boolean retryable() {
            return retryable;
        }

        public boolean isRetryable() {
            return retryable;
        }

        public String errorCode() {
            return errorCode;
        }

        public String errorSummary() {
            return errorSummary;
        }

        /** 兼容已有事件端口的命名；仍返回未处理的原始摘要，仅供 relay 消毒。 */
        public String errorDigest() {
            return errorSummary;
        }
    }
}
