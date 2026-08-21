package com.basiclab.iot.sink.telemetry.inbox.mqtt;

import com.basiclab.iot.sink.telemetry.inbox.route.CenterTelemetryIngressHandler;
import com.basiclab.iot.sink.telemetry.inbox.route.CenterTelemetryIngressResult;
import io.vertx.core.Vertx;
import io.vertx.mqtt.MqttClient;
import io.vertx.mqtt.MqttClientOptions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * MQTT transport shell for the center ingress guard. It copies the incoming
 * topic/payload and delegates all security and Inbox decisions to the pure
 * handler; it never parses or writes Inbox data itself.
 */
public class CenterMqttInboxSubscriber implements AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(CenterMqttInboxSubscriber.class);

    private final CenterTelemetryIngressHandler handler;
    private final Vertx vertx;
    private final MqttClient client;
    private final String host;
    private final int port;
    private final String topicFilter;

    public CenterMqttInboxSubscriber(CenterTelemetryIngressHandler handler,
                                     String host, int port, String clientId,
                                     String topicFilter,
                                     String username, String password) {
        this.handler = handler;
        this.vertx = Vertx.vertx();
        this.host = host;
        this.port = port;
        this.topicFilter = topicFilter;
        MqttClientOptions options = new MqttClientOptions()
                .setClientId(clientId)
                .setCleanSession(false)
                .setKeepAliveInterval(60);
        if (username != null && !username.isBlank()) {
            options.setUsername(username);
            options.setPassword(password);
        }
        this.client = MqttClient.create(vertx, options);
    }

    public void start() {
        client.publishHandler(this::onMessage);
        client.connect(port, host, ar -> {
            if (ar.succeeded()) {
                log.info("center MQTT connected; exact shared subscription enabled");
                client.subscribe(topicFilter, 1);
            } else {
                log.error("center MQTT connect failed: {}", ar.cause().getClass().getSimpleName());
            }
        });
    }

    private void onMessage(io.vertx.mqtt.messages.MqttPublishMessage message) {
        CenterTelemetryIngressResult result = dispatch(
                message.topicName(), message.payload().getBytes().clone());
        if (result instanceof CenterTelemetryIngressResult.Rejected rejected) {
            log.warn("center telemetry rejected: code={} disposition={}",
                    rejected.rejection().code(), rejected.rejection().disposition());
        } else {
            log.debug("center telemetry accepted by Inbox guard");
        }
    }

    /** Package-private transport seam used by direct subscriber contract tests. */
    CenterTelemetryIngressResult dispatch(String topic, byte[] payload) {
        return handler.handle(topic, payload == null ? null : payload.clone());
    }

    @Override
    public void close() {
        try {
            client.disconnect();
        } catch (RuntimeException ignored) {
            // close is best effort; no payload or credential is logged.
        }
        vertx.close();
    }
}
