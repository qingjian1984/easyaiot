package com.basiclab.iot.sink.telemetry.inbox;

import com.basiclab.iot.sink.telemetry.inbox.jdbc.JdbcTelemetryInbox;
import com.basiclab.iot.sink.telemetry.inbox.jdbc.TelemetryProjectionOrchestrator;
import com.basiclab.iot.sink.telemetry.inbox.mqtt.CenterMqttAckPublisher;
import com.basiclab.iot.sink.telemetry.inbox.mqtt.CenterMqttInboxSubscriber;
import com.basiclab.iot.sink.telemetry.store.TelemetryStorePort;
import com.basiclab.iot.sink.telemetry.store.jdbc.JdbcTelemetryStore;
import io.vertx.mqtt.MqttClient;
import io.vertx.core.Vertx;
import io.vertx.mqtt.MqttClientOptions;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.sql.DataSource;

/**
 * TD-003 中心 Inbox + TelemetryStore + 投影编排 + MQTT 订阅 装配。
 * 仅 standard/full Profile + easyaiot.telemetry.inbox.enabled=true。
 */
@Configuration
@ConditionalOnProperty(name = "easyaiot.telemetry.inbox.enabled", havingValue = "true", matchIfMissing = false)
public class TelemetryInboxAutoConfiguration {

    @Bean
    public TelemetryInboxPort telemetryInboxPort(DataSource dataSource) {
        return new JdbcTelemetryInbox(dataSource);
    }

    @Bean
    public TelemetryStorePort telemetryStorePort(DataSource dataSource) {
        return new JdbcTelemetryStore(dataSource);
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
            TelemetryInboxPort inbox,
            @Value("${easyaiot.telemetry.mqtt.host:localhost}") String host,
            @Value("${easyaiot.telemetry.mqtt.port:1883}") int port,
            @Value("${easyaiot.telemetry.mqtt.clientId:center-inbox}") String clientId,
            @Value("${easyaiot.telemetry.mqtt.topic:/telemetry/#}") String topicFilter) {
        CenterMqttInboxSubscriber subscriber = new CenterMqttInboxSubscriber(
                inbox, host, port, clientId, topicFilter);
        subscriber.start();
        return subscriber;
    }

    @Bean
    @ConditionalOnProperty(name = "easyaiot.telemetry.mqtt.enabled", havingValue = "true")
    public Vertx telemetryVertx() {
        return Vertx.vertx();
    }
}
