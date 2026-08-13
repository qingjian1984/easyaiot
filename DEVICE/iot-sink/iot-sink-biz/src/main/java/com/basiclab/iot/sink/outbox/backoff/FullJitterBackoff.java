package com.basiclab.iot.sink.outbox.backoff;

import java.util.concurrent.ThreadLocalRandom;

/**
 * TD-002 §10 full jitter 退避（base 1s, cap 30min）。
 * delay = random(0, min(cap, base * 2^attempts))。
 */
public final class FullJitterBackoff {

    private final long baseMs;
    private final long capMs;

    public FullJitterBackoff(long baseMs, long capMs) {
        this.baseMs = baseMs;
        this.capMs = capMs;
    }

    public long nextDelayMs(int attempts) {
        long exponential = Math.min(capMs, baseMs * (1L << Math.min(attempts, 30)));
        return ThreadLocalRandom.current().nextLong(0, exponential + 1);
    }
}
