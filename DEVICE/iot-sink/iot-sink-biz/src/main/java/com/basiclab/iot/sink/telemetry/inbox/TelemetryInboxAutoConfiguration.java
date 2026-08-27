package com.basiclab.iot.sink.telemetry.inbox;

import com.basiclab.iot.sink.telemetry.inbox.ack.CenterTelemetryAckPublisherPort;
import com.basiclab.iot.sink.telemetry.inbox.ack.CenterTelemetryAckService;
import com.basiclab.iot.sink.telemetry.inbox.ack.JdbcTelemetryAckDeliveryRepository;
import com.basiclab.iot.sink.telemetry.inbox.ack.TelemetryAckDispatchPort;
import com.basiclab.iot.sink.telemetry.inbox.ack.TelemetryAckReconciliationTask;
import com.basiclab.iot.sink.telemetry.inbox.jdbc.JdbcTelemetryInbox;
import com.basiclab.iot.sink.telemetry.inbox.jdbc.TelemetryProjectionOrchestrator;
import com.basiclab.iot.sink.telemetry.inbox.mqtt.CenterMqttAckPublisher;
import com.basiclab.iot.sink.telemetry.inbox.mqtt.CenterMqttInboxSubscriber;
import com.basiclab.iot.sink.telemetry.inbox.mqtt.TelemetryMqttProperties;
import com.basiclab.iot.sink.telemetry.inbox.route.CenterTelemetryIngressHandler;
import com.basiclab.iot.sink.telemetry.inbox.route.TelemetryDeviceAuthorityClient;
import com.basiclab.iot.sink.telemetry.inbox.route.TelemetryDeviceAuthorityClientAdapter;
import com.basiclab.iot.sink.telemetry.inbox.route.TelemetryDeviceAuthorityPort;
import com.basiclab.iot.sink.telemetry.inbox.route.TelemetryUpstreamTopicParser;
import com.basiclab.iot.sink.telemetry.store.TelemetryStorePort;
import com.basiclab.iot.sink.telemetry.store.jdbc.JdbcTelemetryStore;
import io.vertx.core.Vertx;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.sql.DataSource;

/**
 * TD-003 中心 Inbox + TelemetryStore + 投影编排 + MQTT 订阅 装配。
 * 仅 standard/full Profile + easyaiot.telemetry.inbox.enabled=true。
 *
 * <p>LC03-03 §5.4 启动顺序：Inbox repository → MQTT 连接（上行共享
 * 订阅在 {@link CenterMqttInboxSubscriber} 内）→ ACK scanner 启动即
 * 扫描一次 → projector。即时 ACK 由 ack service 从已提交 Inbox 事务
 * 的返回值触发；V012 列缺失时 repository 调用方 fail-closed 不发送。
 */
@Configuration
@ConditionalOnProperty(name = "easyaiot.telemetry.inbox.enabled", havingValue = "true", matchIfMissing = false)
@EnableConfigurationProperties(TelemetryMqttProperties.class)
public class TelemetryInboxAutoConfiguration {

    @Bean
    public TelemetryInboxPort telemetryInboxPort(DataSource dataSource) {
        return new JdbcTelemetryInbox(dataSource);
    }

    @Bean
    @ConditionalOnProperty(name = "easyaiot.telemetry.ack.enabled", havingValue = "true")
    public TelemetryAckDispatchPort telemetryAckDispatchPort(DataSource dataSource) {
        return new JdbcTelemetryAckDeliveryRepository(dataSource);
    }

    @Bean
    public TelemetryStorePort telemetryStorePort(DataSource dataSource) {
        return new JdbcTelemetryStore(dataSource);
    }

    @Bean
    @ConditionalOnProperty(name = "easyaiot.telemetry.mqtt.enabled", havingValue = "true")
    public TelemetryUpstreamTopicParser telemetryUpstreamTopicParser() {
        return new TelemetryUpstreamTopicParser();
    }

    @Bean
    @ConditionalOnProperty(name = "easyaiot.telemetry.mqtt.enabled", havingValue = "true")
    public TelemetryDeviceAuthorityPort telemetryDeviceAuthorityPort(
            TelemetryDeviceAuthorityClient client) {
        return new TelemetryDeviceAuthorityClientAdapter(client);
    }

    @Bean
    @ConditionalOnProperty(name = "easyaiot.telemetry.mqtt.enabled", havingValue = "true")
    public CenterTelemetryIngressHandler centerTelemetryIngressHandler(
            TelemetryUpstreamTopicParser parser,
            TelemetryDeviceAuthorityPort authority,
            TelemetryInboxPort inbox) {
        return new CenterTelemetryIngressHandler(parser, authority, inbox);
    }

    @Bean(destroyMethod = "stop")
    @ConditionalOnProperty(name = "easyaiot.telemetry.projection.enabled", havingValue = "true", matchIfMissing = true)
    public TelemetryProjectionOrchestrator projectionOrchestrator(DataSource dataSource,
                                                                   TelemetryStorePort store) {
        TelemetryProjectionOrchestrator orchestrator = new TelemetryProjectionOrchestrator(dataSource, store);
        orchestrator.start();
        return orchestrator;
    }

    @Bean(destroyMethod = "close")
    @ConditionalOnProperty(name = "easyaiot.telemetry.mqtt.enabled", havingValue = "true")
    public CenterMqttInboxSubscriber centerMqttInboxSubscriber(
            CenterTelemetryIngressHandler handler,
            TelemetryMqttProperties mqttProps,
            ObjectProvider<CenterTelemetryAckService> ackServiceProvider) {
        mqttProps.validateForEnabledSubscriber();
        CenterMqttInboxSubscriber subscriber = new CenterMqttInboxSubscriber(
                handler,
                mqttProps.getHost(),
                mqttProps.getPort(),
                mqttProps.getClientId(),
                mqttProps.getTopicFilter(),
                mqttProps.getUsername(),
                mqttProps.getPassword(),
                ackServiceProvider.getIfAvailable());
        subscriber.start();
        return subscriber;
    }

    /** LC03-03：ACK V1 MQTT 发送器（复用上行 subscriber 的 client）。 */
    @Bean
    @ConditionalOnProperty(name = "easyaiot.telemetry.ack.enabled", havingValue = "true")
    public CenterTelemetryAckPublisherPort centerTelemetryAckPublisherPort(
            CenterMqttInboxSubscriber subscriber) {
        return new CenterMqttAckPublisher(subscriber.mqttClient());
    }

    @Bean
    @ConditionalOnProperty(name = "easyaiot.telemetry.ack.enabled", havingValue = "true")
    public CenterTelemetryAckService centerTelemetryAckService(
            TelemetryAckDispatchPort dispatchPort,
            CenterTelemetryAckPublisherPort publisher) {
        return new CenterTelemetryAckService(dispatchPort, publisher);
    }

    @Bean(destroyMethod = "close")
    @ConditionalOnProperty(name = "easyaiot.telemetry.ack.enabled", havingValue = "true")
    public TelemetryAckReconciliationTask telemetryAckReconciliationTask(
            TelemetryAckDispatchPort dispatchPort,
            CenterTelemetryAckService ackService,
            @Value("${easyaiot.telemetry.ack.scan-interval-ms:10000}") long scanIntervalMs,
            @Value("${easyaiot.telemetry.ack.batch-size:1000}") int batchSize) {
        TelemetryAckReconciliationTask task = new TelemetryAckReconciliationTask(
                dispatchPort, ackService, scanIntervalMs, batchSize);
        task.start();
        return task;
    }

    @Bean
    @ConditionalOnProperty(name = "easyaiot.telemetry.mqtt.enabled", havingValue = "true")
    public Vertx telemetryVertx() {
        return Vertx.vertx();
    }
}
