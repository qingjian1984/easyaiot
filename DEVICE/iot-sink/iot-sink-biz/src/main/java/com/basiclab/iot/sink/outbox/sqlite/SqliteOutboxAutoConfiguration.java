package com.basiclab.iot.sink.outbox.sqlite;

import com.basiclab.iot.sink.outbox.dispatch.CollectorMqttAckSubscriber;
import com.basiclab.iot.sink.outbox.dispatch.CollectorMqttProperties;
import com.basiclab.iot.sink.outbox.dispatch.CollectorMqttPublisher;
import com.basiclab.iot.sink.outbox.dispatch.LeaseReclaimer;
import com.basiclab.iot.sink.outbox.dispatch.OutboxCleanupTask;
import com.basiclab.iot.sink.outbox.dispatch.OutboxCheckpointTask;
import com.basiclab.iot.sink.outbox.dispatch.OutboxDispatcher;
import com.basiclab.iot.sink.outbox.dispatch.VertxCollectorMqttPublisher;
import com.basiclab.iot.sink.telemetry.envelope.EnvelopeCanonicalCodec;
import com.basiclab.iot.sink.telemetry.outbox.TelemetryOutboxPort;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

import java.nio.file.Path;
import java.sql.SQLException;

/**
 * TD-002 collector Profile outbox + dispatch 装配。
 * 仅 collector Profile + easyaiot.outbox.enabled=true 时装配。
 */
@Configuration
@Profile("collector")
@ConditionalOnProperty(name = "easyaiot.outbox.enabled", havingValue = "true", matchIfMissing = true)
@EnableConfigurationProperties({SqliteOutboxConfig.class, CollectorMqttProperties.class})
public class SqliteOutboxAutoConfiguration {

    @Bean(destroyMethod = "shutdown")
    public SqliteTelemetryOutbox sqliteTelemetryOutbox(SqliteOutboxConfig config) throws SQLException {
        Path db = Path.of(config.getVolumePath()).resolve("outbox.db");
        SqliteOutboxMigration.migrate(db);
        return new SqliteTelemetryOutbox(db, new EnvelopeCanonicalCodec(), config.getQueueCapacity());
    }

    @Bean(destroyMethod = "close")
    @ConditionalOnProperty(name = "easyaiot.collector.mqtt.enabled", havingValue = "true")
    public VertxCollectorMqttPublisher collectorMqttPublisher(CollectorMqttProperties mqttProps) {
        VertxCollectorMqttPublisher publisher = new VertxCollectorMqttPublisher(mqttProps);
        publisher.connect();
        return publisher;
    }

    @Bean(destroyMethod = "stop")
    @ConditionalOnProperty(name = "easyaiot.collector.mqtt.enabled", havingValue = "true")
    public OutboxDispatcher outboxDispatcher(TelemetryOutboxPort outbox,
                                             CollectorMqttPublisher publisher,
                                             SqliteOutboxConfig outboxConfig) {
        OutboxDispatcher dispatcher = new OutboxDispatcher(
                outbox, publisher, 100L, 100, 300_000L);
        dispatcher.start();
        return dispatcher;
    }

    @Bean(destroyMethod = "stop")
    @ConditionalOnProperty(name = "easyaiot.collector.mqtt.enabled", havingValue = "true")
    public LeaseReclaimer leaseReclaimer(SqliteTelemetryOutbox outbox) {
        LeaseReclaimer reclaimer = new LeaseReclaimer(outbox, 30_000L);
        reclaimer.start();
        return reclaimer;
    }

    @Bean(destroyMethod = "close")
    @ConditionalOnProperty(name = "easyaiot.collector.mqtt.enabled", havingValue = "true")
    public CollectorMqttAckSubscriber ackSubscriber(TelemetryOutboxPort outbox,
                                                    VertxCollectorMqttPublisher publisher,
                                                    CollectorMqttProperties mqttProps) {
        String ackFilter = mqttProps.getAckTopicPrefix() + "#";
        CollectorMqttAckSubscriber subscriber = new CollectorMqttAckSubscriber(
                outbox, publisher.getMqttClient(), ackFilter);
        subscriber.start();
        return subscriber;
    }

    @Bean(destroyMethod = "stop")
    @ConditionalOnProperty(name = "easyaiot.collector.mqtt.enabled", havingValue = "true")
    public OutboxCleanupTask outboxCleanupTask(SqliteTelemetryOutbox outbox) {
        OutboxCleanupTask task = new OutboxCleanupTask(outbox.getQueue(), 10_000L, 300_000L, 1000);
        task.start();
        return task;
    }

    @Bean(destroyMethod = "stop")
    @ConditionalOnProperty(name = "easyaiot.collector.mqtt.enabled", havingValue = "true")
    public OutboxCheckpointTask outboxCheckpointTask(SqliteTelemetryOutbox outbox) {
        OutboxCheckpointTask task = new OutboxCheckpointTask(outbox.getQueue(), 30_000L);
        task.start();
        return task;
    }
}
