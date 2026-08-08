package com.basiclab.iot.device.service.event;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 测试共享的指标端口记录 fake（手写，不用 Mockito）。
 */
final class RecordingEventMetrics implements PowerModelEventMetrics {

    final Map<String, Integer> publishedByResult = new HashMap<String, Integer>();
    final List<Duration> deliveryDurations = new ArrayList<Duration>();
    int quarantined;

    @Override
    public void eventPublished(String result) {
        Integer current = publishedByResult.get(result);
        publishedByResult.put(result, current == null ? 1 : current + 1);
    }

    @Override
    public void recordDeliveryDuration(Duration duration) {
        deliveryDurations.add(duration);
    }

    @Override
    public void inboxQuarantined() {
        quarantined++;
    }

    int published(String result) {
        Integer count = publishedByResult.get(result);
        return count == null ? 0 : count;
    }
}
