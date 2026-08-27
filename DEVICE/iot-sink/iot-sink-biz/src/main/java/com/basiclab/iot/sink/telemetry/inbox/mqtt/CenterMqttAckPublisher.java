package com.basiclab.iot.sink.telemetry.inbox.mqtt;

import com.basiclab.iot.sink.telemetry.ack.TelemetryAckV1;
import com.basiclab.iot.sink.telemetry.ack.TelemetryAckV1Codec;
import com.basiclab.iot.sink.telemetry.inbox.ack.CenterTelemetryAckPublisherPort;
import io.netty.handler.codec.mqtt.MqttQoS;
import io.vertx.core.Future;
import io.vertx.core.buffer.Buffer;
import io.vertx.mqtt.MqttClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * LC03-03 §5.3 中心 ACK V1 发送器。
 *
 * <p>唯一 payload 是七字段 ACK V1（LC03-01 冻结 codec 产出 UTF-8 JSON，
 * 固定生产顺序）；唯一 Topic 由 {@code TelemetryRoute#ackTopic()} 派生，
 * 不再拼接 {@code /telemetry/ack/**}。publish Future 确认成功才返回
 * true；失败返回 false 保持 {@code ack_sent_at_ms} 为 NULL 交扫描器。
 */
public final class CenterMqttAckPublisher implements CenterTelemetryAckPublisherPort {

    private static final Logger log = LoggerFactory.getLogger(CenterMqttAckPublisher.class);

    private final MqttClient client;
    private final TelemetryAckV1Codec codec;

    public CenterMqttAckPublisher(MqttClient client) {
        this.client = client;
        this.codec = new TelemetryAckV1Codec();
    }

    /**
     * 以 QoS 1 publish 一条 ACK V1。
     *
     * @return publish Future 确认成功为 true；断连、失败或异常为 false，不抛出。
     */
    @Override
    public boolean publish(TelemetryAckV1 ack, String ackTopic) {
        if (ack == null || ackTopic == null || ackTopic.isBlank()) {
            return false;
        }
        if (!client.isConnected()) {
            return false;
        }
        try {
            byte[] payload = codec.encode(ack);
            Future<Integer> future = client.publish(
                    ackTopic, Buffer.buffer(payload), MqttQoS.AT_LEAST_ONCE, false, false);
            future.toCompletionStage().toCompletableFuture()
                    .get(10, java.util.concurrent.TimeUnit.SECONDS);
            log.debug("ACK V1 published: messageId={} status={}", ack.messageId(), ack.status());
            return future.succeeded();
        } catch (Exception e) {
            // 只记录稳定分类；payload、凭据与 Topic 级身份不进入日志。
            log.warn("ACK V1 publish failed: code=ACK_PUBLISH_ERROR error={}",
                    e.getClass().getSimpleName());
            return false;
        }
    }
}
