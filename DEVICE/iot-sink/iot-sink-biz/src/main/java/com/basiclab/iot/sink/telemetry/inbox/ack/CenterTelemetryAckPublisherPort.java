package com.basiclab.iot.sink.telemetry.inbox.ack;

import com.basiclab.iot.sink.telemetry.ack.TelemetryAckV1;

/**
 * LC03-03 §5.3 ACK V1 发送端口。
 *
 * <p>实现负责把七字段 V1 payload 以 MQTT QoS 1 publish 到
 * {@code TelemetryRoute#ackTopic()}，并只在 publish Future 确认成功后
 * 返回 true；同步异常、Future 失败或进程中止一律返回 false，
 * 由扫描器补发。实现不得伪造 exactly-once。
 */
public interface CenterTelemetryAckPublisherPort {

    /**
     * publish 确认成功返回 true；任何失败返回 false 且不抛出。
     * {@code ackTopic} 必须由调用方从持久行的路由经
     * {@code TelemetryRoute#ackTopic()} 派生。
     */
    boolean publish(TelemetryAckV1 ack, String ackTopic);
}
