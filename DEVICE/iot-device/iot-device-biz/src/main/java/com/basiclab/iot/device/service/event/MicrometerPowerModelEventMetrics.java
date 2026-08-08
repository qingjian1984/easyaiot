package com.basiclab.iot.device.service.event;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;

import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * ADR-014 §可观测性与对账：{@link PowerModelEventMetrics} 的 Micrometer 适配。
 * 指标名与 tag 冻结见端口 javadoc；按 result 值缓存 Counter，避免每次新建。
 * 本类只做计量转发，不含业务语义。Java 8 兼容。
 */
public class MicrometerPowerModelEventMetrics implements PowerModelEventMetrics {

    /** 发布结果计数（tag：result）。 */
    public static final String PUBLISH_TOTAL = "power_model_event_publish_total";
    /** 单事件投递耗时。 */
    public static final String DELIVERY_DURATION = "power_model_event_delivery_duration";
    /** Inbox 隔离处置计数。 */
    public static final String QUARANTINED_TOTAL = "power_model_inbox_quarantined_total";

    private final MeterRegistry registry;
    private final ConcurrentMap<String, Counter> publishCounters =
            new ConcurrentHashMap<String, Counter>();
    private final Timer deliveryTimer;
    private final Counter quarantinedCounter;

    public MicrometerPowerModelEventMetrics(MeterRegistry registry) {
        this.registry = Objects.requireNonNull(registry, "registry");
        this.deliveryTimer = Timer.builder(DELIVERY_DURATION)
                .description("Power model event delivery duration")
                .register(registry);
        this.quarantinedCounter = Counter.builder(QUARANTINED_TOTAL)
                .description("Power model inbox quarantined total")
                .register(registry);
    }

    @Override
    public void eventPublished(String result) {
        Objects.requireNonNull(result, "result");
        Counter counter = publishCounters.get(result);
        if (counter == null) {
            counter = Counter.builder(PUBLISH_TOTAL)
                    .description("Power model event publish total")
                    .tag("result", result)
                    .register(registry);
            Counter existing = publishCounters.putIfAbsent(result, counter);
            if (existing != null) {
                counter = existing;
            }
        }
        counter.increment();
    }

    @Override
    public void recordDeliveryDuration(Duration duration) {
        deliveryTimer.record(Objects.requireNonNull(duration, "duration"));
    }

    @Override
    public void inboxQuarantined() {
        quarantinedCounter.increment();
    }
}
