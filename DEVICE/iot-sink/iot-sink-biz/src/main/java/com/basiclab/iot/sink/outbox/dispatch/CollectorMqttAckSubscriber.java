package com.basiclab.iot.sink.outbox.dispatch;

import com.basiclab.iot.sink.telemetry.ack.TelemetryAckCodecException;
import com.basiclab.iot.sink.telemetry.ack.TelemetryAckTopicParser;
import com.basiclab.iot.sink.telemetry.ack.TelemetryAckV1Codec;
import com.basiclab.iot.sink.telemetry.outbox.AckCommand;
import com.basiclab.iot.sink.telemetry.outbox.TelemetryOutboxPort;
import com.basiclab.iot.sink.telemetry.outbox.TelemetryRoute;
import io.vertx.core.buffer.Buffer;
import io.vertx.mqtt.MqttClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * LC03-02 §4.3 collector-side ACK V1 consumer.
 *
 * <p>Inbound association is fail-closed in the frozen order: the topic must
 * parse as one exact canonical ACK route, the payload must pass the strict
 * seven-field codec, and the SQLite writer then enforces the
 * messageId/requestId/route match before any state transition.  The legacy
 * four-field wire and any {@code /telemetry/**} shape never reach the writer.
 */
public class CollectorMqttAckSubscriber implements AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(CollectorMqttAckSubscriber.class);

    private final TelemetryOutboxPort outbox;
    private final TelemetryAckV1Codec codec;
    private final TelemetryAckTopicParser topicParser;
    private final MqttClient client;

    public CollectorMqttAckSubscriber(TelemetryOutboxPort outbox, MqttClient client) {
        this.outbox = outbox;
        this.client = client;
        this.codec = new TelemetryAckV1Codec();
        this.topicParser = new TelemetryAckTopicParser();
    }

    /** Registers the inbound handler only; subscriptions belong to the coordinator. */
    public void start() {
        client.publishHandler(publish -> {
            try {
                onAckMessage(publish.topicName(), publish.payload());
            } catch (Exception e) {
                log.warn("ACK handling error: error={}", e.getClass().getSimpleName());
            }
        });
        log.info("ACK V1 subscriber handler registered");
    }

    private void onAckMessage(String topic, Buffer payload) {
        TelemetryRoute route;
        try {
            route = topicParser.parse(topic);
        } catch (IllegalArgumentException e) {
            log.warn("ACK topic rejected: code={}", stableCode(e));
            return;
        }

        byte[] bytes = payload == null ? null : payload.getBytes().clone();
        AckCommand command;
        try {
            command = codec.decodeCommand(bytes, route, System.currentTimeMillis());
        } catch (TelemetryAckCodecException e) {
            log.warn("ACK payload rejected: code={}", e.errorCode());
            return;
        }

        try {
            outbox.applyAck(command);
            log.debug("ACK applied: messageId={} status={}",
                    command.messageId(), command.status());
        } catch (Exception e) {
            log.warn("ACK apply failed: messageId={} error={}",
                    command.messageId(), e.getClass().getSimpleName());
        }
    }

    private static String stableCode(IllegalArgumentException e) {
        return e.getMessage() != null ? e.getMessage() : "ACK_TOPIC_INVALID";
    }

    @Override
    public void close() {
        try {
            client.publishHandler(null);
        } catch (Exception ignore) {
            // Best-effort handler cleanup; subscriptions are owned elsewhere.
        }
    }
}
