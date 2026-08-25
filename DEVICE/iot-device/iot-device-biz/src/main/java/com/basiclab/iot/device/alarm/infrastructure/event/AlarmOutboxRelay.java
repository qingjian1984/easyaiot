package com.basiclab.iot.device.alarm.infrastructure.event;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Objects;

/**
 * 告警 Outbox 纯编排器：claim → transport send → 状态回写。
 *
 * <p>claim 的互斥和租约恢复由 repository 负责；本类不访问数据库、不创建
 * transport Bean、不调度轮询。只有 transport 返回明确 broker acknowledgement
 * 才回写 PUBLISHED。发送异常永远不会被当作成功。</p>
 */
public final class AlarmOutboxRelay {

    public static final Duration DEFAULT_LEASE_DURATION = Duration.ofSeconds(60);
    public static final int DEFAULT_BATCH_SIZE = 100;
    public static final Duration DEFAULT_RETRY_BASE_DELAY = Duration.ofSeconds(1);
    public static final Duration DEFAULT_RETRY_MAX_DELAY = Duration.ofSeconds(16);
    public static final String DEFAULT_LEASE_OWNER = "alarm-outbox-relay";

    private final AlarmOutboxRepository repository;
    private final AlarmOutboxTransport transport;
    private final String leaseOwner;
    private final Duration leaseDuration;
    private final int batchSize;
    private final Duration retryBaseDelay;
    private final Duration retryMaxDelay;
    private final AlarmOutboxTimeSource timeSource;
    private final AlarmOutboxJitterSource jitterSource;

    public AlarmOutboxRelay(AlarmOutboxRepository repository,
                            AlarmOutboxTransport transport,
                            String leaseOwner, Duration leaseDuration, int batchSize,
                            Duration retryBaseDelay, Duration retryMaxDelay,
                            AlarmOutboxTimeSource timeSource,
                            AlarmOutboxJitterSource jitterSource) {
        this.repository = Objects.requireNonNull(repository, "repository");
        this.transport = Objects.requireNonNull(transport, "transport");
        this.leaseOwner = required(leaseOwner, "leaseOwner");
        this.leaseDuration = positive(leaseDuration, "leaseDuration");
        if (batchSize < 1) {
            throw new IllegalArgumentException("ALARM_OUTBOX_RELAY_INVALID: batchSize < 1");
        }
        this.batchSize = batchSize;
        this.retryBaseDelay = positive(retryBaseDelay, "retryBaseDelay");
        this.retryMaxDelay = positive(retryMaxDelay, "retryMaxDelay");
        if (retryMaxDelay.compareTo(retryBaseDelay) < 0) {
            throw new IllegalArgumentException(
                    "ALARM_OUTBOX_RELAY_INVALID: retryMaxDelay < retryBaseDelay");
        }
        this.timeSource = Objects.requireNonNull(timeSource, "timeSource");
        this.jitterSource = Objects.requireNonNull(jitterSource, "jitterSource");
    }

    /** 使用 Clock 的构造器，Clock 只负责提供可测试的时间。 */
    public AlarmOutboxRelay(AlarmOutboxRepository repository,
                            AlarmOutboxTransport transport,
                            String leaseOwner, Duration leaseDuration, int batchSize,
                            Duration retryBaseDelay, Duration retryMaxDelay,
                            Clock clock, AlarmOutboxJitterSource jitterSource) {
        this(repository, transport, leaseOwner, leaseDuration, batchSize,
                retryBaseDelay, retryMaxDelay,
                Objects.requireNonNull(clock, "clock")::instant, jitterSource);
    }

    /** 小型 fake 合同的默认策略构造器。 */
    public AlarmOutboxRelay(AlarmOutboxRepository repository,
                            AlarmOutboxTransport transport,
                            Clock clock, AlarmOutboxJitterSource jitterSource) {
        this(repository, transport, DEFAULT_LEASE_OWNER, DEFAULT_LEASE_DURATION,
                DEFAULT_BATCH_SIZE, DEFAULT_RETRY_BASE_DELAY,
                DEFAULT_RETRY_MAX_DELAY, clock, jitterSource);
    }

    /** 执行一轮，时间来自注入的 TimeSource。 */
    public int relayOnce() {
        return relayOnce(timeSource.now());
    }

    /** 显式时间入口，便于纯单测复放同一时刻。 */
    public int relayOnce(Instant now) {
        Objects.requireNonNull(now, "now");
        List<AlarmOutboxClaimedEntry> claimed = Objects.requireNonNull(
                repository.claimDue(now, leaseOwner, leaseDuration, batchSize),
                "repository.claimDue returned null");
        for (AlarmOutboxClaimedEntry entry : claimed) {
            if (entry != null) {
                deliver(entry, now);
            }
        }
        return claimed.size();
    }

    private void deliver(AlarmOutboxClaimedEntry entry, Instant now) {
        AlarmOutboxTransport.TransportResult result;
        try {
            result = transport.send(entry);
            if (result == null) {
                result = AlarmOutboxTransport.TransportResult.retryable(
                        "ALARM_OUTBOX_TRANSPORT_INVALID_RESULT", "transport returned no result");
            }
        } catch (RuntimeException transportFailure) {
            // 不携带异常 message，避免把 URL、凭据或 payload 泄漏进持久化摘要。
            result = AlarmOutboxTransport.TransportResult.retryable(
                    "ALARM_OUTBOX_TRANSPORT_EXCEPTION", "transport exception");
        }

        if (result.brokerAcknowledged()) {
            repository.markPublished(entry.eventId(), leaseOwner, now);
            return;
        }

        int attempts = entry.retryCount() == Integer.MAX_VALUE
                ? Integer.MAX_VALUE : entry.retryCount() + 1;
        String errorCode = AlarmOutboxRetryPolicy.stableErrorCode(result.errorCode());
        String errorSummary = AlarmOutboxRetryPolicy.sanitizeErrorSummary(
                result.errorSummary());
        AlarmOutboxRetryPolicy.FailureDecision decision =
                AlarmOutboxRetryPolicy.afterFailure(result.retryable(), attempts,
                        entry.maxRetries());
        if (decision == AlarmOutboxRetryPolicy.FailureDecision.RETRY) {
            Duration exponential = AlarmOutboxRetryPolicy.exponentialDelay(
                    attempts, retryBaseDelay, retryMaxDelay);
            Duration jitter = safeJitter(entry, attempts, exponential);
            Instant nextAttemptAt = AlarmOutboxRetryPolicy.nextAttemptAt(
                    now, attempts, retryBaseDelay, retryMaxDelay, jitter);
            repository.markRetry(entry.eventId(), leaseOwner, attempts, now, nextAttemptAt,
                    errorCode, errorSummary);
            return;
        }
        repository.markDeadLetter(entry.eventId(), leaseOwner, now, errorCode, errorSummary);
    }

    private Duration safeJitter(AlarmOutboxClaimedEntry entry, int attempts,
                               Duration exponential) {
        try {
            Duration jitter = jitterSource.jitter(entry.eventId(), attempts, exponential);
            return jitter == null || jitter.isNegative() ? Duration.ZERO : jitter;
        } catch (RuntimeException ignored) {
            // 抖动只是调度优化；其提供方异常不能阻止失败状态回写。
            return Duration.ZERO;
        }
    }

    private static String required(String value, String field) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException("ALARM_OUTBOX_RELAY_INVALID: " + field + " blank");
        }
        return value;
    }

    private static Duration positive(Duration value, String field) {
        if (value == null || value.isZero() || value.isNegative()) {
            throw new IllegalArgumentException("ALARM_OUTBOX_RELAY_INVALID: " + field + " <= 0");
        }
        return value;
    }
}
