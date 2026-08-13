package com.basiclab.iot.sink.outbox.dispatch;

import com.basiclab.iot.sink.telemetry.outbox.ClaimedEnvelope;

/**
 * TD-003 §7 collector MQTT 发送接口（QoS1 publish canonical bytes）。
 * 实现可对接 Vert.x MqttClient / 测试 mock。
 */
public interface CollectorMqttPublisher {

    /**
     * QoS1 publish canonical bytes 到指定 topic。
     *
     * @param envelope claim 选出的 envelope（含 canonical bytes + topic）
     * @return true if publish accepted by MQTT broker
     */
    boolean publish(ClaimedEnvelope envelope);
}
