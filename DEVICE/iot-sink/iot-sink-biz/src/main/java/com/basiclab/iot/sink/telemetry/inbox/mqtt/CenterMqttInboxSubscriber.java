package com.basiclab.iot.sink.telemetry.inbox.mqtt;

import com.basiclab.iot.sink.telemetry.inbox.InboxEnvelope;
import com.basiclab.iot.sink.telemetry.inbox.InboxReceiveResult;
import com.basiclab.iot.sink.telemetry.inbox.TelemetryInboxPort;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.netty.handler.codec.mqtt.MqttQoS;
import io.vertx.core.buffer.Buffer;
import io.vertx.mqtt.MqttClient;
import io.vertx.mqtt.MqttClientOptions;
import io.vertx.core.Vertx;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * TD-003 §7 中心 MQTT 订阅器：订阅 collector 上行 Topic → 解析 → TelemetryInboxPort。
 *
 * <p>上行 Topic: {@code /telemetry/{siteCode}/{propertyCode}}（collector 发送的 ClaimedEnvelope.topic）。
 * payload = Envelope V1 canonical JSON bytes。
 *
 * <p>解析为 InboxEnvelope 后批量调用 {@link TelemetryInboxPort#receiveEnvelopes}。
 * 单条到达逐条写入（MQTT 消息是单条的）；批量聚合可选（后续优化）。
 */
public class CenterMqttInboxSubscriber implements AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(CenterMqttInboxSubscriber.class);

    private final TelemetryInboxPort inbox;
    private final ObjectMapper mapper;
    private final Vertx vertx;
    private final MqttClient client;
    private final String host;
    private final int port;
    private final String topicFilter;

    public CenterMqttInboxSubscriber(TelemetryInboxPort inbox,
                                     String host, int port, String clientId,
                                     String topicFilter) {
        this.inbox = inbox;
        this.mapper = new ObjectMapper();
        this.vertx = Vertx.vertx();
        this.host = host;
        this.port = port;
        this.topicFilter = topicFilter;
        MqttClientOptions options = new MqttClientOptions()
                .setClientId(clientId)
                .setCleanSession(false)
                .setKeepAliveInterval(60);
        this.client = MqttClient.create(vertx, options);
    }

    public void start() {
        client.connect(port, host, ar -> {
            if (ar.succeeded()) {
                log.info("center MQTT connected, subscribing: {}", topicFilter);
                client.subscribe(topicFilter, 1);
                client.publishHandler(this::onMessage);
            } else {
                log.error("center MQTT connect failed: {}", ar.cause().getMessage());
            }
        });
    }

    private void onMessage(io.vertx.mqtt.messages.MqttPublishMessage message) {
        try {
            InboxEnvelope envelope = parseEnvelope(message.topicName(), message.payload());
            if (envelope == null) {
                return;
            }
            InboxReceiveResult result = inbox.receiveEnvelopes(List.of(envelope));
            log.debug("inbox received: messageId={} result={}",
                    envelope.messageId(), result.getClass().getSimpleName());
        } catch (Exception e) {
            log.warn("inbox message handling error: topic={} error={}",
                    message.topicName(), e.getMessage());
        }
    }

    private InboxEnvelope parseEnvelope(String topic, Buffer payload) {
        try {
            String json = payload.toString(StandardCharsets.UTF_8);
            JsonNode node = mapper.readTree(json);

            String messageId = node.path("messageId").asText(null);
            String requestId = node.path("requestId").asText(null);
            String tenantId = node.path("tenantId").asText(null);
            String siteCode = node.path("siteCode").asText(null);
            String deviceIdentification = node.path("deviceIdentification").asText(null);
            String propertyCode = node.path("propertyCode").asText(null);
            String value = node.path("value").asText(null);
            String contentSha256 = sha256(payload.getBytes());

            if (messageId == null || tenantId == null || siteCode == null
                    || deviceIdentification == null || propertyCode == null) {
                log.warn("envelope missing required fields: messageId={} tenantId={}", messageId, tenantId);
                return null;
            }

            String collectedAt = node.path("collectedAt").asText("1970-01-01T00:00:00Z");
            long collectedAtMs = parseIso8601ToMillis(collectedAt);
            long sequence = node.path("sequence").asLong(0);
            String source = node.path("source").asText("unknown");
            long configVersion = node.path("configVersion").asLong(0);

            return new InboxEnvelope(
                    messageId, requestId, tenantId, siteCode,
                    deviceIdentification, propertyCode,
                    payload.getBytes(),
                    contentSha256,
                    collectedAtMs, sequence, source, configVersion
            );
        } catch (Exception e) {
            log.warn("envelope parse failed: topic={} error={}", topic, e.getMessage());
            return null;
        }
    }

    private static long parseIso8601ToMillis(String iso) {
        try {
            return java.time.Instant.parse(iso).toEpochMilli();
        } catch (Exception e) {
            return System.currentTimeMillis();
        }
    }

    private static String sha256(byte[] input) {
        try {
            byte[] hash = java.security.MessageDigest.getInstance("SHA-256").digest(input);
            StringBuilder hex = new StringBuilder(hash.length * 2);
            for (byte b : hash) {
                hex.append(Character.forDigit((b >> 4) & 0xF, 16));
                hex.append(Character.forDigit(b & 0xF, 16));
            }
            return hex.toString();
        } catch (Exception e) {
            return "sha256-error";
        }
    }

    @Override
    public void close() {
        try {
            client.disconnect();
        } catch (Exception ignore) {
        }
        vertx.close();
    }
}
