package com.basiclab.iot.device.config;

import com.basiclab.iot.device.service.event.KafkaPowerModelEventTransport;
import com.basiclab.iot.device.service.event.PowerModelEventTransport;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;

import java.util.HashMap;
import java.util.Map;

/**
 * ADR-014 §配置清单：电力模型 Outbox 事件 Kafka 生产者。
 * acks=all（候选，压测豁免口径下保持候选不冻结）、幂等 producer 开启、有界重试。
 * 仅 standard/full 启用：{@code power.model.events.enabled=true} 时才装配；
 * mini 档不装配本生产者（配合 capability fail-closed，不产生 Outbox 待投递残留）。
 */
@Configuration
@ConditionalOnProperty(name = "power.model.events.enabled", havingValue = "true")
public class PowerModelEventKafkaConfiguration {

    @Value("${spring.kafka.bootstrap-servers:localhost:9092}")
    private String bootstrapServers;

    @Value("${power.model.events.producer.acks:all}")
    private String acks;

    @Value("${power.model.events.producer.retries:5}")
    private int retries;

    @Value("${power.model.events.producer.send-timeout-ms:10000}")
    private long sendTimeoutMs;

    @Bean
    @ConditionalOnMissingBean(name = "powerModelKafkaTemplate")
    public KafkaTemplate<String, String> powerModelKafkaTemplate() {
        Map<String, Object> props = new HashMap<>();
        props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        props.put(ProducerConfig.ACKS_CONFIG, acks);
        props.put(ProducerConfig.RETRIES_CONFIG, retries);
        props.put(ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG, true);
        // 有界等待：发送超时由 transport 层 sendTimeoutMs 控制，broker 端等待同样有界。
        props.put(ProducerConfig.MAX_BLOCK_MS_CONFIG, sendTimeoutMs);
        ProducerFactory<String, String> factory = new DefaultKafkaProducerFactory<>(props);
        return new KafkaTemplate<>(factory);
    }

    @Bean
    @ConditionalOnMissingBean(PowerModelEventTransport.class)
    public PowerModelEventTransport powerModelEventTransport(
            KafkaTemplate<String, String> powerModelKafkaTemplate) {
        return new KafkaPowerModelEventTransport(powerModelKafkaTemplate, sendTimeoutMs);
    }
}
