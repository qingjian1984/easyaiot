package com.basiclab.iot.device.service.event;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * ADR-014 §可观测性与对账：Micrometer 指标适配合同。
 * 指标名与 result tag 冻结；同 result 复用同一 Counter；空参数 fail-closed。
 */
class MicrometerPowerModelEventMetricsTest {

    @Test
    void countersAndTimerRegisterWithFrozenNames() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        MicrometerPowerModelEventMetrics metrics = new MicrometerPowerModelEventMetrics(registry);

        metrics.eventPublished("published");
        metrics.eventPublished("published");
        metrics.eventPublished("retry_scheduled");
        metrics.recordDeliveryDuration(Duration.ofMillis(120));
        metrics.inboxQuarantined();

        assertEquals(2.0, registry.get(MicrometerPowerModelEventMetrics.PUBLISH_TOTAL)
                .tag("result", "published").counter().count());
        assertEquals(1.0, registry.get(MicrometerPowerModelEventMetrics.PUBLISH_TOTAL)
                .tag("result", "retry_scheduled").counter().count());
        assertEquals(1, registry.get(MicrometerPowerModelEventMetrics.DELIVERY_DURATION)
                .timer().count());
        assertEquals(120.0, registry.get(MicrometerPowerModelEventMetrics.DELIVERY_DURATION)
                .timer().totalTime(java.util.concurrent.TimeUnit.MILLISECONDS));
        assertEquals(1.0, registry.get(MicrometerPowerModelEventMetrics.QUARANTINED_TOTAL)
                .counter().count());
    }

    @Test
    void sameResultReusesRegisteredCounter() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        MicrometerPowerModelEventMetrics metrics = new MicrometerPowerModelEventMetrics(registry);

        metrics.eventPublished("dead_letter");
        metrics.eventPublished("dead_letter");

        assertEquals(1L, registry.getMeters().stream()
                .filter(meter -> MicrometerPowerModelEventMetrics.PUBLISH_TOTAL.equals(meter.getId().getName()))
                .count(), "同 result 不得重复注册 Counter");
    }

    @Test
    void nullArgsRejected() {
        MicrometerPowerModelEventMetrics metrics =
                new MicrometerPowerModelEventMetrics(new SimpleMeterRegistry());
        assertThrows(NullPointerException.class, () -> new MicrometerPowerModelEventMetrics(null));
        assertThrows(NullPointerException.class, () -> metrics.eventPublished(null));
        assertThrows(NullPointerException.class, () -> metrics.recordDeliveryDuration(null));
    }
}
