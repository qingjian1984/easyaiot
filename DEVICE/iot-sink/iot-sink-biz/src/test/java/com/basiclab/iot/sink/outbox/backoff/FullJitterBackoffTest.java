package com.basiclab.iot.sink.outbox.backoff;

import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * full jitter 退避合同：delay ∈ [0, min(cap, base*2^attempts)]。
 */
class FullJitterBackoffTest {

    private final FullJitterBackoff backoff = new FullJitterBackoff(1000L, 1_800_000L);

    @RepeatedTest(20)
    void delayAlwaysNonNegative() {
        long delay = backoff.nextDelayMs(3);
        assertTrue(delay >= 0, "delay must be >= 0");
    }

    @RepeatedTest(20)
    void delayWithinExponentialCap() {
        long delay = backoff.nextDelayMs(2);
        assertTrue(delay <= 4000L, "attempt 2: base*2^2=4000, delay=" + delay);
    }

    @Test
    void delayRespectsCap() {
        long delay = backoff.nextDelayMs(30);
        assertTrue(delay <= 1_800_000L, "cap=30min, delay=" + delay);
    }

    @Test
    void attemptZeroDelayWithinBase() {
        long delay = backoff.nextDelayMs(0);
        assertTrue(delay <= 1000L, "attempt 0: base=1000, delay=" + delay);
    }
}
