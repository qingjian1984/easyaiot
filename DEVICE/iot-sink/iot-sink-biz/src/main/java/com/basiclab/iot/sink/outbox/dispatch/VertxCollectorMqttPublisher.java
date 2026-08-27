package com.basiclab.iot.sink.outbox.dispatch;

import com.basiclab.iot.sink.telemetry.outbox.ClaimedEnvelope;
import com.basiclab.iot.sink.telemetry.outbox.TelemetryRoute;
import io.vertx.mqtt.MqttClient;
import io.vertx.mqtt.MqttClientOptions;
import io.vertx.core.Vertx;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.function.Predicate;

/**
 * Vert.x MqttClient 实现 collector MQTT QoS1 发送。
 *
 * <p>独立 clientId + cleanSession=false + 持久会话。
 * 连接失败 publish 返回 false（Dispatcher 等租约到期 reclaim）。
 *
 * <p>LC03-02：publish 前增加精确 ACK 订阅就绪门禁——目标路由未完成
 * SUBACK 时不发送并返回 false，报告稳定码 {@code ACK_SUBSCRIPTION_NOT_READY}；
 * {@link #whenConnected(Runnable)} 用于"初始 SUBACK 先于 dispatcher 启动"的接线。
 */
public class VertxCollectorMqttPublisher implements CollectorMqttPublisher, AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(VertxCollectorMqttPublisher.class);

    private final Vertx vertx;
    private final MqttClient client;
    private final CollectorMqttProperties properties;
    private final Predicate<TelemetryRoute> routeActiveGate;
    private final CompletableFuture<Void> connectedFuture = new CompletableFuture<>();
    private volatile boolean connected = false;

    public VertxCollectorMqttPublisher(CollectorMqttProperties properties) {
        this(properties, null);
    }

    public VertxCollectorMqttPublisher(CollectorMqttProperties properties,
                                       Predicate<TelemetryRoute> routeActiveGate) {
        this.properties = properties;
        this.routeActiveGate = routeActiveGate;
        this.vertx = Vertx.vertx();
        MqttClientOptions options = new MqttClientOptions()
                .setClientId(properties.getClientId())
                .setCleanSession(properties.isCleanSession())
                .setKeepAliveInterval(properties.getKeepAliveSeconds())
                .setMaxInflightQueue(properties.getMaxInflight());
        if (properties.getUsername() != null && !properties.getUsername().isBlank()) {
            options.setUsername(properties.getUsername());
            options.setPassword(properties.getPassword());
        }
        this.client = MqttClient.create(vertx, options);
    }

    public void connect() {
        client.connect(properties.getPort(), properties.getHost(), ar -> {
            if (ar.succeeded()) {
                connected = true;
                log.info("collector MQTT connected: {}:{} clientId={}",
                        properties.getHost(), properties.getPort(), properties.getClientId());
                connectedFuture.complete(null);
            } else {
                connected = false;
                log.error("collector MQTT connect failed: {}", ar.cause().getMessage());
            }
        });
    }

    /**
     * Run the callback once after the first successful connect.  When the
     * broker never accepts the connection the callback never fires, matching
     * the pre-existing single-attempt availability semantics.
     */
    public void whenConnected(Runnable callback) {
        Objects.requireNonNull(callback, "callback");
        connectedFuture.thenRun(callback);
    }

    @Override
    public boolean publish(ClaimedEnvelope envelope) {
        if (!connected) {
            log.debug("publish skipped (not connected): messageId={}", envelope.messageId());
            return false;
        }
        if (routeActiveGate != null) {
            TelemetryRoute route = new TelemetryRoute(
                    envelope.productIdentification(), envelope.deviceIdentification());
            if (!routeActiveGate.test(route)) {
                log.warn("publish skipped: code=ACK_SUBSCRIPTION_NOT_READY messageId={}",
                        envelope.messageId());
                return false;
            }
        }
        try {
            io.netty.handler.codec.mqtt.MqttQoS qos = properties.getQos() == 0
                    ? io.netty.handler.codec.mqtt.MqttQoS.AT_MOST_ONCE
                    : properties.getQos() == 2
                    ? io.netty.handler.codec.mqtt.MqttQoS.EXACTLY_ONCE
                    : io.netty.handler.codec.mqtt.MqttQoS.AT_LEAST_ONCE;
            client.publish(
                    envelope.topic(),
                    io.vertx.core.buffer.Buffer.buffer(envelope.canonicalBytes()),
                    qos,
                    false,
                    false);
            return true;
        } catch (Exception e) {
            log.warn("publish failed: messageId={} error={}", envelope.messageId(), e.getMessage());
            return false;
        }
    }

    public MqttClient getMqttClient() {
        return client;
    }

    @Override
    public void close() {
        try {
            if (connected) {
                client.disconnect();
            }
        } catch (Exception ignore) {
        }
        vertx.close();
    }
}
