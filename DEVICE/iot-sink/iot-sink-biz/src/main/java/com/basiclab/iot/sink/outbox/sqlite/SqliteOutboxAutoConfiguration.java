package com.basiclab.iot.sink.outbox.sqlite;

import com.basiclab.iot.sink.outbox.dispatch.CollectorAckSubscriptionCoordinator;
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
import com.basiclab.iot.sink.telemetry.outbox.TelemetryRouteSetProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

import java.nio.file.Path;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * TD-002 collector Profile outbox + dispatch 装配。
 * 仅 collector Profile + easyaiot.outbox.enabled=true 时装配。
 *
 * <p>LC03-02 启动顺序（§4.2）：SQLite writer（bean 构造期，见
 * {@link SqliteTelemetryOutbox}）→ MQTT 连接 → 初始精确 ACK 订阅 SUBACK →
 * dispatcher/Poller 才开始 claim/publish；此后周期刷新订阅集合，
 * "配置 APPLIED 后新图首轮询前完成对应 ACK SUBACK"由 1s 刷新 + publisher
 * 逐路由就绪门禁共同保证。
 */
@Configuration
@Profile("collector")
@ConditionalOnProperty(name = "easyaiot.outbox.enabled", havingValue = "true", matchIfMissing = true)
@EnableConfigurationProperties({SqliteOutboxConfig.class, CollectorMqttProperties.class})
public class SqliteOutboxAutoConfiguration {

    @Bean(destroyMethod = "shutdown")
    public SqliteTelemetryOutbox sqliteTelemetryOutbox(SqliteOutboxConfig config) {
        Path db = Path.of(config.getVolumePath()).resolve("outbox.db");
        return new SqliteTelemetryOutbox(db, new EnvelopeCanonicalCodec(), config.getQueueCapacity());
    }

    @Bean(destroyMethod = "close")
    @ConditionalOnProperty(name = "easyaiot.collector.mqtt.enabled", havingValue = "true")
    public CollectorAckSubscriptionCoordinator collectorAckSubscriptionCoordinator(
            VertxCollectorMqttPublisher publisher,
            TelemetryRouteSetProvider routeSetProvider) {
        CollectorAckSubscriptionCoordinator coordinator = new CollectorAckSubscriptionCoordinator(
                publisher.getMqttClient(), routeSetProvider);
        // Initial recovery runs inside whenConnected so the exact SUBACK set
        // exists before the dispatcher bean below is allowed to publish.
        publisher.whenConnected(coordinator::recover);
        return coordinator;
    }

    @Bean(destroyMethod = "close")
    @ConditionalOnProperty(name = "easyaiot.collector.mqtt.enabled", havingValue = "true")
    public VertxCollectorMqttPublisher collectorMqttPublisher(
            CollectorMqttProperties mqttProps,
            CollectorAckSubscriptionCoordinator coordinator) {
        VertxCollectorMqttPublisher publisher = new VertxCollectorMqttPublisher(
                mqttProps, coordinator::isRouteActive);
        publisher.connect();
        return publisher;
    }

    @Bean(destroyMethod = "stop")
    @ConditionalOnProperty(name = "easyaiot.collector.mqtt.enabled", havingValue = "true")
    public OutboxDispatcher outboxDispatcher(TelemetryOutboxPort outbox,
                                             CollectorMqttPublisher publisher,
                                             SqliteOutboxConfig outboxConfig,
                                             CollectorAckSubscriptionCoordinator coordinator) {
        OutboxDispatcher dispatcher = new OutboxDispatcher(
                outbox, publisher, 100L, 100, 300_000L);
        // The dispatcher only starts publishing after the initial exact ACK
        // subscription set has been SUBACKed; a failed initial refresh keeps
        // the per-route gate closed so no new route is ever sent blind.
        coordinator.runWhenReady(dispatcher::start);
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
                                                    VertxCollectorMqttPublisher publisher) {
        CollectorMqttAckSubscriber subscriber = new CollectorMqttAckSubscriber(
                outbox, publisher.getMqttClient());
        subscriber.start();
        return subscriber;
    }

    @Bean(destroyMethod = "close")
    @ConditionalOnProperty(name = "easyaiot.collector.mqtt.enabled", havingValue = "true")
    public AckSubscriptionRefreshTask ackSubscriptionRefreshTask(
            CollectorAckSubscriptionCoordinator coordinator,
            @Value("${easyaiot.collector.ack-subscription.refresh-interval-ms:1000}") long refreshIntervalMs) {
        AckSubscriptionRefreshTask task = new AckSubscriptionRefreshTask(
                coordinator, Math.max(1L, refreshIntervalMs));
        task.start();
        return task;
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

    /** Periodic exact-set refresh aligned with the config reconcile cadence. */
    static final class AckSubscriptionRefreshTask implements AutoCloseable {
        private final CollectorAckSubscriptionCoordinator coordinator;
        private final long intervalMs;
        private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread thread = new Thread(r, "collector-ack-subscription-refresh");
            thread.setDaemon(true);
            return thread;
        });
        private volatile boolean started;

        AckSubscriptionRefreshTask(CollectorAckSubscriptionCoordinator coordinator, long intervalMs) {
            this.coordinator = coordinator;
            this.intervalMs = intervalMs;
        }

        void start() {
            if (started) {
                return;
            }
            started = true;
            scheduler.scheduleWithFixedDelay(() -> {
                try {
                    coordinator.refresh();
                } catch (RuntimeException e) {
                    // Keep the loop alive; the coordinator itself reports the
                    // stable NOT_READY classification through its own logs.
                }
            }, intervalMs, intervalMs, TimeUnit.MILLISECONDS);
        }

        @Override
        public void close() {
            scheduler.shutdownNow();
        }
    }
}
