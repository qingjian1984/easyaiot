package com.basiclab.iot.device.service.event;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/**
 * ADR-014 P-07：电力模型事件 Kafka 监听适配（薄壳，裁决在
 * {@link PowerModelEventConsumerCoordinator}）。
 * - 手动 offset：仅 COMMIT_OFFSET 裁决后 ack；NO_COMMIT 走 nack 退避重投
 *   （退避时长取裁决的 nextAttemptAt 与当前的差值，下限 1s）；
 * - 单分区内顺序消费（默认并发 1 由容器工厂保证）；poison/处理失败由编排器
 *   按合同进 DLQ 后再提交 offset，不跳过、不阻塞分区；
 * - 仅 {@code power.model.events.enabled=true}（standard/full）装配；mini 不装配。
 */
@Component
@ConditionalOnProperty(name = "power.model.events.enabled", havingValue = "true")
public class PowerModelEventKafkaListener {

    private static final Logger log = LoggerFactory.getLogger(PowerModelEventKafkaListener.class);
    private static final Duration MIN_NACK_SLEEP = Duration.ofSeconds(1);

    private final PowerModelEventConsumerCoordinator coordinator;
    /** 重试计数（topic:partition:offset 键）；COMMIT 后移除，仅失败条目驻留。 */
    private final Map<String, Integer> attemptsByOffset = new ConcurrentHashMap<String, Integer>();

    public PowerModelEventKafkaListener(PowerModelEventConsumerCoordinator coordinator) {
        this.coordinator = Objects.requireNonNull(coordinator, "coordinator");
    }

    @KafkaListener(
            topics = "${power.model.events.topic:power-model-release-v1}",
            groupId = "${power.model.events.consumer-group:iot-device-power-model-release}",
            containerFactory = "powerModelKafkaListenerContainerFactory")
    public void onMessage(@Payload String raw,
                          @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
                          @Header(KafkaHeaders.RECEIVED_PARTITION_ID) int partition,
                          @Header(KafkaHeaders.OFFSET) long offset,
                          Acknowledgment acknowledgment) {
        String attemptKey = topic + ":" + partition + ":" + offset;
        Integer tracked = attemptsByOffset.get(attemptKey);
        int priorAttempts = tracked == null ? 0 : tracked.intValue();
        Instant now = Instant.now();

        PowerModelEventConsumerCoordinator.ConsumeDecision decision;
        try {
            decision = coordinator.consume(raw, priorAttempts, now);
        } catch (RuntimeException e) {
            // DLQ 投递失败等异常：绝不静默——不提交 offset，退避重投并记 error。
            log.error("power-model event consume error topic={} partition={} offset={} reason={}",
                    topic, partition, offset, e.getMessage());
            attemptsByOffset.put(attemptKey, Integer.valueOf(priorAttempts + 1));
            acknowledgment.nack(MIN_NACK_SLEEP);
            return;
        }

        if (decision.action() == PowerModelEventConsumerCoordinator.Action.COMMIT_OFFSET) {
            attemptsByOffset.remove(attemptKey);
            acknowledgment.acknowledge();
            return;
        }
        attemptsByOffset.put(attemptKey, Integer.valueOf(priorAttempts + 1));
        acknowledgment.nack(nackSleep(decision, now));
    }

    private static Duration nackSleep(PowerModelEventConsumerCoordinator.ConsumeDecision decision,
                                      Instant now) {
        Instant nextAttempt = decision.nextAttemptAt();
        if (nextAttempt == null) {
            return MIN_NACK_SLEEP;
        }
        Duration sleep = Duration.between(now, nextAttempt);
        return sleep.compareTo(MIN_NACK_SLEEP) < 0 ? MIN_NACK_SLEEP : sleep;
    }
}
