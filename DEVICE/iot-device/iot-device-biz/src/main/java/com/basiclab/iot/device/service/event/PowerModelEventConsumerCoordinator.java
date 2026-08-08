package com.basiclab.iot.device.service.event;

import com.basiclab.iot.device.event.PowerModelEventEnvelope;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;

/**
 * ADR-014 §决策 MUST + P-07 消费契约：消费编排器（单条消息裁决）。
 * 契约落实：
 * - 手动 offset 只在 Inbox 写成功（PROCESSED 落库）或事件被安全处置
 *   （DUPLICATE/QUARANTINED/DLQ 落库或投递）后提交；
 * - poison 消息（畸形/不变量违规）进 DLQ 不跳过、不阻塞分区；
 * - 处理失败按 retryable/final 分流，retryable 指数退避 1s→16s，
 *   超限（候选 maxAttempts=5）进 DLQ；
 * - 首插争抢落败（LOST_CONTENTION）不提交 offset，等待重投后重读裁决；
 * - 已知主版本但未注册处理器按 final 失败进 DLQ（绝不静默丢弃）。
 * 本类为纯编排（注入端口与重试计数），offset 提交/重投由监听适配层执行。
 * Java 8 兼容。
 */
public class PowerModelEventConsumerCoordinator {

    private static final Logger log = LoggerFactory.getLogger(PowerModelEventConsumerCoordinator.class);

    /** 编排裁决。 */
    public enum Action {
        /** 可提交 offset（处理成功/重复/隔离/DLQ 处置完成）。 */
        COMMIT_OFFSET,
        /** 不提交 offset：可重试失败（退避后重投）或首插争抢落败。 */
        NO_COMMIT
    }

    /** 裁决结果。 */
    public static final class ConsumeDecision {
        private final Action action;
        private final Instant nextAttemptAt;
        private final String detail;

        private ConsumeDecision(Action action, Instant nextAttemptAt, String detail) {
            this.action = action;
            this.nextAttemptAt = nextAttemptAt;
            this.detail = detail;
        }

        public Action action() {
            return action;
        }

        /** NO_COMMIT 且为重试时的下次尝试时间（退避）；其余为 null。 */
        public Instant nextAttemptAt() {
            return nextAttemptAt;
        }

        public String detail() {
            return detail;
        }
    }

    private final PowerModelInboxWriter inboxWriter;
    private final PowerModelEventHandlerRegistry handlerRegistry;
    private final PowerModelEventTransport dlqTransport;
    private final String dlqTopic;
    private final int maxAttempts;
    private final Duration retryBaseDelay;
    private final Duration retryMaxDelay;

    public PowerModelEventConsumerCoordinator(PowerModelInboxWriter inboxWriter,
                                              PowerModelEventHandlerRegistry handlerRegistry,
                                              PowerModelEventTransport dlqTransport,
                                              String dlqTopic, int maxAttempts,
                                              Duration retryBaseDelay, Duration retryMaxDelay) {
        this.inboxWriter = Objects.requireNonNull(inboxWriter, "inboxWriter");
        this.handlerRegistry = Objects.requireNonNull(handlerRegistry, "handlerRegistry");
        this.dlqTransport = Objects.requireNonNull(dlqTransport, "dlqTransport");
        if (dlqTopic == null || dlqTopic.trim().isEmpty()) {
            throw new IllegalArgumentException(
                    "MODEL_EVENT_ENVELOPE_INVALID: dlqTopic 不得为空");
        }
        this.dlqTopic = dlqTopic;
        if (maxAttempts < 1) {
            throw new IllegalArgumentException(
                    "MODEL_EVENT_RETRY_POLICY_INVALID: maxAttempts 必须 >= 1");
        }
        this.maxAttempts = maxAttempts;
        this.retryBaseDelay = Objects.requireNonNull(retryBaseDelay, "retryBaseDelay");
        this.retryMaxDelay = Objects.requireNonNull(retryMaxDelay, "retryMaxDelay");
        OutboxRelayPolicy.nextAttemptAt(1, retryBaseDelay, retryMaxDelay, Instant.EPOCH);
    }

    /**
     * 裁决一条原始消息。
     *
     * @param raw           原始消息正文
     * @param priorAttempts 本消息此前处理失败次数（由适配层按 eventId/offset 跟踪，首轮为 0）
     * @param now           当前时间（注入以便确定性测试）
     */
    public ConsumeDecision consume(String raw, int priorAttempts, Instant now) {
        Objects.requireNonNull(raw, "raw");
        Objects.requireNonNull(now, "now");
        if (priorAttempts < 0) {
            throw new IllegalArgumentException(
                    "MODEL_EVENT_RETRY_POLICY_INVALID: priorAttempts 必须 >= 0");
        }

        PowerModelEventEnvelope envelope;
        try {
            envelope = PowerModelEventEnvelopeCodec.parse(raw);
        } catch (IllegalArgumentException e) {
            // poison：进 DLQ 后提交 offset（不跳过、不阻塞分区）。
            sendToDlq(raw, e.getMessage());
            log.error("power-model event poison -> dlq reason={}", sanitize(e.getMessage()));
            return new ConsumeDecision(Action.COMMIT_OFFSET, null, "poison");
        }

        PowerModelInboxWriter.IngestResult ingested = inboxWriter.ingest(envelope, raw, now);
        switch (ingested.action()) {
            case DUPLICATE:
                return new ConsumeDecision(Action.COMMIT_OFFSET, null, "duplicate");
            case QUARANTINED:
                return new ConsumeDecision(Action.COMMIT_OFFSET, null,
                        ingested.isCritical() ? "quarantined-critical" : "quarantined");
            case LOST_CONTENTION:
                return new ConsumeDecision(Action.NO_COMMIT, null, "lost-contention");
            case PROCESS:
            default:
                return process(envelope, raw, priorAttempts, now);
        }
    }

    private ConsumeDecision process(PowerModelEventEnvelope envelope, String raw,
                                    int priorAttempts, Instant now) {
        PowerModelEventHandlerRegistry.PowerModelEventHandler handler =
                handlerRegistry.find(envelope.eventType());
        if (handler == null) {
            sendToDlq(raw, "MODEL_EVENT_HANDLER_MISSING: " + envelope.eventType());
            log.error("power-model event no handler -> dlq eventId={} eventType={}",
                    envelope.eventId(), envelope.eventType());
            return new ConsumeDecision(Action.COMMIT_OFFSET, null, "handler-missing");
        }
        try {
            handler.handle(envelope, PowerModelEventEnvelopeCodec.dataJson(envelope));
            inboxWriter.markProcessed(envelope.eventId(), now);
            log.info("power-model event processed eventId={} tenantId={} eventType={}",
                    envelope.eventId(), envelope.tenantId(), envelope.eventType());
            return new ConsumeDecision(Action.COMMIT_OFFSET, null, "processed");
        } catch (PowerModelEventHandlerRegistry.PowerModelEventProcessingException e) {
            int attempts = priorAttempts + 1;
            OutboxRelayPolicy.FailureDecision decision =
                    OutboxRelayPolicy.afterFailure(e.isRetryable(), attempts, maxAttempts);
            if (decision == OutboxRelayPolicy.FailureDecision.RETRY) {
                Instant nextAttempt =
                        OutboxRelayPolicy.nextAttemptAt(attempts, retryBaseDelay, retryMaxDelay, now);
                log.warn("power-model event process retry eventId={} attempts={} nextAttemptAt={} errorCode={}",
                        envelope.eventId(), attempts, nextAttempt, e.errorCode());
                return new ConsumeDecision(Action.NO_COMMIT, nextAttempt, "retry:" + e.errorCode());
            }
            sendToDlq(raw, e.errorCode());
            log.error("power-model event process failed -> dlq eventId={} attempts={} errorCode={}",
                    envelope.eventId(), attempts, e.errorCode());
            return new ConsumeDecision(Action.COMMIT_OFFSET, null, "dlq:" + e.errorCode());
        }
    }

    private void sendToDlq(String raw, String reason) {
        PowerModelEventTransport.TransportResult result =
                dlqTransport.send(dlqTopic, null, raw);
        if (!result.isSuccess()) {
            // DLQ 投递失败不静默：记 error 并仍不提交 offset 的决策由调用方按 critical 告警处置；
            // 本批合同：DLQ 传输失败时按可重试失败处理，避免消息既未处理又未留痕。
            throw new IllegalStateException(
                    "MODEL_EVENT_DLQ_SEND_FAILED: " + result.errorCode());
        }
    }

    private static String sanitize(String message) {
        if (message == null) {
            return "";
        }
        return message.length() <= 128 ? message : message.substring(0, 128);
    }
}
