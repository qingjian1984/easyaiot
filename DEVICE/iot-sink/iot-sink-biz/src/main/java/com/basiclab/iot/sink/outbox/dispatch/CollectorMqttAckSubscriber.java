package com.basiclab.iot.sink.outbox.dispatch;

import com.basiclab.iot.sink.telemetry.outbox.AckCommand;
import com.basiclab.iot.sink.telemetry.outbox.AckResultCode;
import com.basiclab.iot.sink.telemetry.outbox.TelemetryOutboxPort;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.netty.handler.codec.mqtt.MqttQoS;
import io.vertx.core.buffer.Buffer;
import io.vertx.mqtt.MqttClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;

/**
 * TD-003 §9 ACK 订阅器：订阅 ACK Topic → 解析 ACK V1 JSON → applyAck。
 *
 * <p>ACK V1 payload 格式（TD-003 §9 冻结）：
 * <pre>{@code
 * {"messageId":"uuid","resultCode":"ACCEPTED_DURABLE","errorCode":"OK","observedAt":1691234567890}
 * }</pre>
 *
 * <p>解析失败（畸形/缺字段/未知 resultCode）→ 记日志丢弃，不静默写 outbox。
 */
public class CollectorMqttAckSubscriber implements AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(CollectorMqttAckSubscriber.class);

    private final TelemetryOutboxPort outbox;
    private final ObjectMapper mapper;
    private final MqttClient client;
    private final String ackTopicFilter;

    public CollectorMqttAckSubscriber(TelemetryOutboxPort outbox,
                                      MqttClient client,
                                      String ackTopicFilter) {
        this.outbox = outbox;
        this.mapper = new ObjectMapper();
        this.client = client;
        this.ackTopicFilter = ackTopicFilter;
    }

    public void start() {
        client.subscribe(ackTopicFilter, 1);
        client.publishHandler(publish -> {
            try {
                onAckMessage(publish.topicName(), publish.payload());
            } catch (Exception e) {
                log.warn("ACK message handling error: topic={} error={}",
                        publish.topicName(), e.getMessage());
            }
        });
        log.info("ACK subscriber started: filter={}", ackTopicFilter);
    }

    private void onAckMessage(String topic, Buffer payload) {
        String json = payload.toString(StandardCharsets.UTF_8);
        try {
            JsonNode node = mapper.readTree(json);
            String messageId = node.path("messageId").asText(null);
            String resultCodeStr = node.path("resultCode").asText(null);
            String errorCode = node.path("errorCode").asText("UNKNOWN");
            long observedAt = node.path("observedAt").asLong(System.currentTimeMillis());

            if (messageId == null || messageId.isBlank()) {
                log.warn("ACK missing messageId: topic={} payload={}", topic, json);
                return;
            }
            if (resultCodeStr == null || resultCodeStr.isBlank()) {
                log.warn("ACK missing resultCode: messageId={}", messageId);
                return;
            }

            AckResultCode resultCode;
            try {
                resultCode = AckResultCode.valueOf(resultCodeStr);
            } catch (IllegalArgumentException e) {
                log.warn("ACK unknown resultCode: messageId={} resultCode={}", messageId, resultCodeStr);
                return;
            }

            outbox.applyAck(new AckCommand(messageId, resultCode, errorCode, observedAt));
            log.debug("ACK applied: messageId={} result={} error={}", messageId, resultCode, errorCode);

        } catch (Exception e) {
            log.warn("ACK parse failed: topic={} error={}", topic, e.getMessage());
        }
    }

    @Override
    public void close() {
        try {
            client.unsubscribe(ackTopicFilter);
        } catch (Exception ignore) {
        }
    }
}
