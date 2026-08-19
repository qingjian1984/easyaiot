package com.basiclab.iot.node.service.collector.config;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 有界、进程内的确定性指数退避器。
 *
 * <p>条目只含 releaseId/version/hash/attempt/nextAttemptAt，不得扩展为 canonical 或错误正文
 * 缓存。进程重启丢失条目是有意设计，重复派发仍由 Agent 与 iot-device 的幂等合同保护。</p>
 */
public final class CollectorConfigDispatchBackoff {

    public static final int MAX_ENTRIES = 10_000;
    public static final Duration DEFAULT_BASE_DELAY = Duration.ofSeconds(1);
    public static final Duration DEFAULT_MAX_DELAY = Duration.ofSeconds(60);

    private final Clock clock;
    private final Duration baseDelay;
    private final Duration maxDelay;
    private final Map<String, Entry> entries = new LinkedHashMap<>();

    public CollectorConfigDispatchBackoff() {
        this(Clock.systemUTC(), DEFAULT_BASE_DELAY, DEFAULT_MAX_DELAY);
    }

    public CollectorConfigDispatchBackoff(Clock clock, Duration baseDelay, Duration maxDelay) {
        this.clock = Objects.requireNonNull(clock, "clock");
        requirePositive(baseDelay, "baseDelay");
        requirePositive(maxDelay, "maxDelay");
        if (baseDelay.compareTo(maxDelay) > 0) {
            throw new IllegalArgumentException("baseDelay must not exceed maxDelay");
        }
        this.baseDelay = baseDelay;
        this.maxDelay = maxDelay;
    }

    public synchronized boolean isDue(String releaseId) {
        Entry entry = entries.get(releaseId);
        return entry == null || !clock.instant().isBefore(entry.nextAttemptAt);
    }

    public synchronized Entry recordRetry(String releaseId,
                                           String configVersion,
                                           String payloadSha256) {
        requireKey(releaseId, configVersion, payloadSha256);
        Entry previous = entries.get(releaseId);
        int attempt = 1;
        if (previous != null && previous.configVersion.equals(configVersion)
                && previous.payloadSha256.equals(payloadSha256)) {
            attempt = previous.attempt == Integer.MAX_VALUE
                    ? Integer.MAX_VALUE : previous.attempt + 1;
        }
        Duration delay = cappedDelay(attempt);
        Entry next = new Entry(releaseId, configVersion, payloadSha256, attempt,
                clock.instant().plus(delay));
        if (previous == null && entries.size() >= MAX_ENTRIES) {
            evictEarliest();
        }
        entries.put(releaseId, next);
        return next;
    }

    public synchronized void clear(String releaseId) {
        if (releaseId != null) {
            entries.remove(releaseId);
        }
    }

    public synchronized int size() {
        return entries.size();
    }

    public synchronized List<Entry> snapshot() {
        return List.copyOf(new ArrayList<>(entries.values()));
    }

    private Duration cappedDelay(int attempt) {
        Duration delay = baseDelay;
        // A bounded number of doublings is sufficient for Duration's finite range;
        // do not let a saturated attempt counter turn one retry into a huge loop.
        int doublings = Math.min(Math.max(attempt - 1, 0), 128);
        for (int i = 0; i < doublings && delay.compareTo(maxDelay) < 0; i++) {
            try {
                delay = delay.multipliedBy(2);
            } catch (ArithmeticException overflow) {
                return maxDelay;
            }
            if (delay.compareTo(maxDelay) > 0) {
                return maxDelay;
            }
        }
        return delay.compareTo(maxDelay) > 0 ? maxDelay : delay;
    }

    private void evictEarliest() {
        String candidate = null;
        Instant candidateTime = null;
        for (Map.Entry<String, Entry> item : entries.entrySet()) {
            Instant current = item.getValue().nextAttemptAt;
            if (candidateTime == null || current.isBefore(candidateTime)) {
                candidate = item.getKey();
                candidateTime = current;
            }
        }
        if (candidate != null) {
            entries.remove(candidate);
        }
    }

    private static void requireKey(String releaseId, String configVersion, String payloadSha256) {
        if (releaseId == null || releaseId.isBlank()
                || configVersion == null || configVersion.isBlank()
                || payloadSha256 == null || payloadSha256.isBlank()) {
            throw new IllegalArgumentException("backoff key is incomplete");
        }
    }

    private static void requirePositive(Duration value, String name) {
        if (value.isZero() || value.isNegative()) {
            throw new IllegalArgumentException(name + " must be positive");
        }
    }

    public static final class Entry {
        private final String releaseId;
        private final String configVersion;
        private final String payloadSha256;
        private final int attempt;
        private final Instant nextAttemptAt;

        private Entry(String releaseId, String configVersion, String payloadSha256,
                      int attempt, Instant nextAttemptAt) {
            this.releaseId = releaseId;
            this.configVersion = configVersion;
            this.payloadSha256 = payloadSha256;
            this.attempt = attempt;
            this.nextAttemptAt = nextAttemptAt;
        }

        public String getReleaseId() { return releaseId; }

        public String getConfigVersion() { return configVersion; }

        public String getPayloadSha256() { return payloadSha256; }

        public int getAttempt() { return attempt; }

        public Instant getNextAttemptAt() { return nextAttemptAt; }
    }
}
