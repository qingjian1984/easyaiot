package com.basiclab.iot.device.service.event;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Objects;

/**
 * ADR-014 §验证 OUT-001～004：Outbox 发布器编排（claim → send → 回写）。
 * 数据库事务提交前不发送：发布器只读取已提交的 PENDING 行；
 * 发送后回写崩溃由租约过期恢复（OUT-002）；
 * 并发副本由仓储原子认领隔离（OUT-001/003）；
 * retryable/final 错误分流与 1s→16s 指数退避、超限 DEAD_LETTER（OUT-004）。
 * 可观测性（指标/结构化日志）随本类落地：日志只携带 eventId/aggregateId/tenantId
 * 与 payload_hash，绝不记录 payload 正文。Java 8 兼容。
 */
public class PowerModelOutboxRelay {

    private static final Logger log = LoggerFactory.getLogger(PowerModelOutboxRelay.class);

    private final PowerModelOutboxRepository repository;
    private final PowerModelEventTransport transport;
    private final PowerModelEventMetrics metrics;
    private final String topic;
    private final String leaseOwner;
    private final Duration leaseDuration;
    private final int batchSize;
    private final Duration retryBaseDelay;
    private final Duration retryMaxDelay;

    public PowerModelOutboxRelay(PowerModelOutboxRepository repository,
                                 PowerModelEventTransport transport,
                                 PowerModelEventMetrics metrics,
                                 String topic, String leaseOwner, Duration leaseDuration,
                                 int batchSize, Duration retryBaseDelay, Duration retryMaxDelay) {
        this.repository = Objects.requireNonNull(repository, "repository");
        this.transport = Objects.requireNonNull(transport, "transport");
        this.metrics = Objects.requireNonNull(metrics, "metrics");
        this.topic = requireNonBlank(topic, "topic");
        this.leaseOwner = requireNonBlank(leaseOwner, "leaseOwner");
        this.leaseDuration = Objects.requireNonNull(leaseDuration, "leaseDuration");
        if (batchSize < 1) {
            throw new IllegalArgumentException(
                    "MODEL_EVENT_RETRY_POLICY_INVALID: batchSize 必须 >= 1");
        }
        this.batchSize = batchSize;
        this.retryBaseDelay = Objects.requireNonNull(retryBaseDelay, "retryBaseDelay");
        this.retryMaxDelay = Objects.requireNonNull(retryMaxDelay, "retryMaxDelay");
        // 复用策略参数校验（base>0、cap>=base）。
        OutboxRelayPolicy.nextAttemptAt(1, retryBaseDelay, retryMaxDelay, Instant.EPOCH);
    }

    /**
     * 执行一轮投递。
     *
     * @param now 当前时间（注入以便确定性测试）
     * @return 本轮认领的条目数
     */
    public int relayOnce(Instant now) {
        Objects.requireNonNull(now, "now");
        List<ClaimedOutboxEntry> claimed =
                repository.claimDue(now, leaseOwner, leaseDuration, batchSize);
        for (ClaimedOutboxEntry entry : claimed) {
            deliver(entry, now);
        }
        return claimed.size();
    }

    private void deliver(ClaimedOutboxEntry entry, Instant now) {
        long sendStartNanos = System.nanoTime();
        PowerModelEventTransport.TransportResult result =
                transport.send(topic, entry.topicKey(), entry.payload());
        metrics.recordDeliveryDuration(Duration.ofNanos(System.nanoTime() - sendStartNanos));
        if (result.isSuccess()) {
            repository.markPublished(entry.eventId(), now);
            metrics.eventPublished("published");
            log.info("power-model event published eventId={} tenantId={} aggregate={}:{}",
                    entry.eventId(), entry.tenantId(), entry.aggregateType(), entry.aggregateId());
            return;
        }
        int attempts = entry.retryCount() + 1;
        OutboxRelayPolicy.FailureDecision decision =
                OutboxRelayPolicy.afterFailure(result.isRetryable(), attempts, entry.maxRetries());
        if (decision == OutboxRelayPolicy.FailureDecision.RETRY) {
            Instant nextAttempt =
                    OutboxRelayPolicy.nextAttemptAt(attempts, retryBaseDelay, retryMaxDelay, now);
            repository.markRetry(entry.eventId(), attempts, nextAttempt,
                    result.errorCode(), result.errorDigest());
            metrics.eventPublished("retry_scheduled");
            log.warn("power-model event publish retry eventId={} attempts={} nextAttemptAt={} errorCode={}",
                    entry.eventId(), attempts, nextAttempt, result.errorCode());
        } else {
            repository.markDeadLetter(entry.eventId(), result.errorCode(), result.errorDigest());
            metrics.eventPublished("dead_letter");
            log.error("power-model event dead-lettered eventId={} tenantId={} errorCode={}",
                    entry.eventId(), entry.tenantId(), result.errorCode());
        }
    }

    private static String requireNonBlank(String value, String field) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException(
                    "MODEL_EVENT_ENVELOPE_INVALID: " + field + " 不得为空");
        }
        return value;
    }
}
