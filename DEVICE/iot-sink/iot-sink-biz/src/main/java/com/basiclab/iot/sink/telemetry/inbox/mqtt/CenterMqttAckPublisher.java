package com.basiclab.iot.sink.telemetry.inbox.mqtt;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.netty.handler.codec.mqtt.MqttQoS;
import io.vertx.core.buffer.Buffer;
import io.vertx.mqtt.MqttClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * TD-003 §9 中心 ACK 发送器：向 collector ACK Topic 发送 ACK V1 JSON。
 *
 * <p>ACK V1 格式（与 CollectorMqttAckSubscriber 解析一致）：
 * <pre>{@code
 * {"messageId":"uuid","resultCode":"ACCEPTED_DURABLE","errorCode":"OK","observedAt":1691234567890}
 * }</pre>
 *
 * <p>ACK Topic 规则（TD-003 §7）：
 * {@code /telemetry/ack/{siteCode}/{deviceIdentification}/{propertyCode}}
 */
public class CenterMqttAckPublisher {

    private static final Logger log = LoggerFactory.getLogger(CenterMqttAckPublisher.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final MqttClient client;

    public CenterMqttAckPublisher(MqttClient client) {
        this.client = client;
    }

    /**
     * 发送 ACK 到 collector。
     *
     * @param messageId     envelope messageId
     * @param resultCode    ACCEPTED_DURABLE / DUPLICATE / REJECTED_RETRYABLE / REJECTED_FINAL
     * @param errorCode     错误码（OK / DUP / BUSY / REJECTED / ...）
     * @param ackTopic      ACK Topic（/telemetry/ack/...）
     */
    public void sendAck(String messageId, String resultCode, String errorCode, String ackTopic) {
        try {
            ObjectNode ack = MAPPER.createObjectNode();
            ack.put("messageId", messageId);
            ack.put("resultCode", resultCode);
            ack.put("errorCode", errorCode != null ? errorCode : "OK");
            ack.put("observedAt", System.currentTimeMillis());

            byte[] payload = MAPPER.writeValueAsBytes(ack);
            client.publish(ackTopic, Buffer.buffer(payload),
                    MqttQoS.AT_LEAST_ONCE, false, false);
            log.debug("ACK sent: messageId={} result={} topic={}", messageId, resultCode, ackTopic);
        } catch (Exception e) {
            log.warn("ACK send failed: messageId={} error={}", messageId, e.getMessage());
        }
    }

    /**
     * 构造 ACK Topic（TD-003 §7 规则）。
     */
    public static String buildAckTopic(String siteCode, String deviceIdentification, String propertyCode) {
        return "/telemetry/ack/" + siteCode + "/" + deviceIdentification + "/" + propertyCode;
    }
}
