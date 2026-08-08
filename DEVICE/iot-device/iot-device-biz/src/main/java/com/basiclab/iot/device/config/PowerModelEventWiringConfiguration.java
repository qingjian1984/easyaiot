package com.basiclab.iot.device.config;

import com.basiclab.iot.device.event.PowerModelEventEnvelope;
import com.basiclab.iot.device.service.event.PowerModelEventConsumerCoordinator;
import com.basiclab.iot.device.service.event.PowerModelEventHandlerRegistry;
import com.basiclab.iot.device.service.event.PowerModelEventTransport;
import com.basiclab.iot.device.service.event.PowerModelInboxRepository;
import com.basiclab.iot.device.service.event.PowerModelInboxWriter;
import com.basiclab.iot.device.service.event.PowerModelOutboxRelay;
import com.basiclab.iot.device.service.event.PowerModelOutboxRelayScheduler;
import com.basiclab.iot.device.service.event.PowerModelOutboxRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

import java.time.Clock;
import java.time.Duration;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/**
 * ADR-014：电力模型事件链路编排 Bean 装配（Inbox 写入、处理器注册表、
 * 消费编排器、Outbox 发布器与调度驱动）。仅 {@code power.model.events.enabled=true} 装配。
 * 调度驱动选型（2026-08-08 owner 部署评审裁定）：Spring Scheduling。
 * 本类显式 {@code @EnableScheduling}——iot-device 运行时虽经 iot-common-mq
 * 传递激活调度设施，但显式声明保证该传递依赖变化时发布器轮询不会静默停转
 * （失败关闭原则）；类级门禁保证 mini 档不装配任何事件 Bean、不产生调度。
 * Quartz/iot-common-job 不采用：引入新依赖与 QRTZ_* 表属过度设计，多实例并发
 * 安全由认领 SQL 的 FOR UPDATE SKIP LOCKED + 租约承担。
 */
@Configuration
@EnableScheduling
@ConditionalOnProperty(name = "power.model.events.enabled", havingValue = "true")
public class PowerModelEventWiringConfiguration {

    @Value("${power.model.events.dlq.topic:power-model-release-v1-dlq}")
    private String dlqTopic;

    @Value("${power.model.events.retry.max-attempts:5}")
    private int retryMaxAttempts;

    @Value("${power.model.events.retry.base-delay-ms:1000}")
    private long retryBaseDelayMs;

    @Value("${power.model.events.retry.max-delay-ms:16000}")
    private long retryMaxDelayMs;

    @Value("${power.model.events.topic:power-model-release-v1}")
    private String topic;

    @Value("${power.model.events.relay.lease-owner:pmoutbox-local}")
    private String leaseOwner;

    @Value("${power.model.events.relay.lease-duration-ms:60000}")
    private long leaseDurationMs;

    @Value("${power.model.events.relay.batch-size:100}")
    private int batchSize;

    @Bean
    @ConditionalOnMissingBean
    public PowerModelInboxWriter powerModelInboxWriter(PowerModelInboxRepository inboxRepository) {
        Set<Integer> supportedMajors = new HashSet<Integer>(
                Collections.singletonList(PowerModelEventEnvelope.SUPPORTED_MAJOR_VERSION));
        return new PowerModelInboxWriter(inboxRepository, supportedMajors);
    }

    /**
     * 处理器注册表。当前为空注册表：TD-001 collector 配置发布协调器的业务处理器
     * 随其实现接入；空注册表下事件按「处理器缺失 → DLQ」处置，绝不静默丢弃。
     */
    @Bean
    @ConditionalOnMissingBean
    public PowerModelEventHandlerRegistry powerModelEventHandlerRegistry() {
        return new PowerModelEventHandlerRegistry(
                Collections.<String, PowerModelEventHandlerRegistry.PowerModelEventHandler>emptyMap());
    }

    @Bean
    @ConditionalOnMissingBean
    public PowerModelEventConsumerCoordinator powerModelEventConsumerCoordinator(
            PowerModelInboxWriter inboxWriter,
            PowerModelEventHandlerRegistry handlerRegistry,
            PowerModelEventTransport transport) {
        return new PowerModelEventConsumerCoordinator(inboxWriter, handlerRegistry, transport,
                dlqTopic, retryMaxAttempts,
                Duration.ofMillis(retryBaseDelayMs), Duration.ofMillis(retryMaxDelayMs));
    }

    @Bean
    @ConditionalOnMissingBean
    public PowerModelOutboxRelay powerModelOutboxRelay(
            PowerModelOutboxRepository outboxRepository,
            PowerModelEventTransport transport) {
        return new PowerModelOutboxRelay(outboxRepository, transport, topic, leaseOwner,
                Duration.ofMillis(leaseDurationMs), batchSize,
                Duration.ofMillis(retryBaseDelayMs), Duration.ofMillis(retryMaxDelayMs));
    }

    /**
     * 发布器调度驱动：fixedDelay 轮询 {@code relayOnce}（轮询间隔
     * {@code power.model.events.relay.poll-interval-ms} 候选 1000ms，压测后冻结）。
     */
    @Bean
    @ConditionalOnMissingBean
    public PowerModelOutboxRelayScheduler powerModelOutboxRelayScheduler(PowerModelOutboxRelay relay) {
        return new PowerModelOutboxRelayScheduler(relay, Clock.systemUTC());
    }
}
