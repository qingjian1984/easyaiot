package com.basiclab.iot.sink.telemetry.inbox.mqtt;

import com.basiclab.iot.sink.telemetry.ack.TelemetryAckStatus;
import com.basiclab.iot.sink.telemetry.inbox.InboxReceiveResult;
import com.basiclab.iot.sink.telemetry.inbox.ack.CenterTelemetryAckService;
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
 *
 * <p>LC03-03 §5.3：Inbox 事务已提交并返回后，对
 * {@code ACCEPTED_DURABLE/DUPLICATE} 条目触发即时成功 ACK；collision
 * 与一切拒绝条目零 ACK（拒绝事实属 M1-LC-04）。ackService 为 null
 * （未启用 easyaiot.telemetry.ack.enabled）时本类只保留原有行为。
 */
public class CenterMqttInboxSubscriber implements AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(CenterMqttInboxSubscriber.class);

    private final CenterTelemetryIngressHandler handler;
    private final Vertx vertx;
    private final MqttClient client;
    private final String host;
    private final int port;
    private final String topicFilter;
    private final CenterTelemetryAckService ackService;

    public CenterMqttInboxSubscriber(CenterTelemetryIngressHandler handler,
                                     String host, int port, String clientId,
                                     String topicFilter,
                                     String username, String password) {
        this(handler, host, port, clientId, topicFilter, username, password, null);
    }

    public CenterMqttInboxSubscriber(CenterTelemetryIngressHandler handler,
                                     String host, int port, String clientId,
                                     String topicFilter,
                                     String username, String password,
                                     CenterTelemetryAckService ackService) {
        this.handler = handler;
        this.vertx = Vertx.vertx();
        this.host = host;
        this.port = port;
        this.topicFilter = topicFilter;
        this.ackService = ackService;
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
            return;
        }
        log.debug("center telemetry accepted by Inbox guard");
        if (ackService == null) {
            return;
        }
        CenterTelemetryIngressResult.Accepted accepted =
                (CenterTelemetryIngressResult.Accepted) result;
        InboxReceiveResult inboxResult = accepted.inboxResult();
        if (!(inboxResult instanceof InboxReceiveResult.Batch batch)) {
            return;
        }
        for (InboxReceiveResult.Item item : batch.items()) {
            if (item.status() != InboxReceiveResult.Status.ACCEPTED_DURABLE
                    && item.status() != InboxReceiveResult.Status.DUPLICATE) {
                continue;
            }
            try {
                ackService.sendImmediateAck(accepted.tenantId(), item.messageId(),
                        item.requestId(), TelemetryAckStatus.valueOf(item.status().name()),
                        item.persistedAtMs());
            } catch (RuntimeException e) {
                // 即时 ACK 失败不影响 Inbox 已提交事实；扫描器稍后补发。
                log.warn("immediate ACK dispatch failed: code=ACK_IMMEDIATE_ERROR error={}",
                        e.getClass().getSimpleName());
            }
        }
    }

    /** Package-private transport seam used by direct subscriber contract tests. */
    CenterTelemetryIngressResult dispatch(String topic, byte[] payload) {
        return handler.handle(topic, payload == null ? null : payload.clone());
    }

    /** LC03-03：ACK V1 发送复用同一 client（同 broker、同凭证域）。 */
    public MqttClient mqttClient() {
        return client;
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
