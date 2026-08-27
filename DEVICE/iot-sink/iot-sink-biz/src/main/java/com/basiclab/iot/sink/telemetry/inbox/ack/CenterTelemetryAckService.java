package com.basiclab.iot.sink.telemetry.inbox.ack;

import com.basiclab.iot.sink.telemetry.ack.TelemetryAckStatus;
import com.basiclab.iot.sink.telemetry.ack.TelemetryAckV1;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * LC03-03 §5.3 即时成功 ACK 服务。
 *
 * <p>只消费 {@code JdbcTelemetryInbox.receiveEnvelopes} 已返回的
 * {@code ACCEPTED_DURABLE / DUPLICATE} 条目：从持久行加载派发事实、
 * 独立短事务递增 {@code ack_attempts}，publish 确认成功后条件更新
 * {@code ack_sent_at_ms}。{@code MESSAGE_ID_COLLISION} 与一切拒绝
 * 条目零 publish（拒绝事实属 M1-LC-04）。
 */
public class CenterTelemetryAckService {

    private static final Logger log = LoggerFactory.getLogger(CenterTelemetryAckService.class);

    private final TelemetryAckDispatchPort dispatchPort;
    private final CenterTelemetryAckPublisherPort publisher;

    public CenterTelemetryAckService(TelemetryAckDispatchPort dispatchPort,
                                     CenterTelemetryAckPublisherPort publisher) {
        this.dispatchPort = dispatchPort;
        this.publisher = publisher;
    }

    /**
     * 对一个已提交的 Inbox 成功条目即时发送 ACK V1。
     * 任何一步失败都保持 {@code ack_sent_at_ms} 为 NULL，交扫描器补发。
     */
    public void sendImmediateAck(long tenantId, String messageId, String requestId,
                                 TelemetryAckStatus status, long persistedAtMs) {
        if (status == null || !status.isSuccess()) {
            return;
        }
        TelemetryAckDeliveryRow row = dispatchPort.loadForImmediateAck(tenantId, messageId);
        if (row == null) {
            log.warn("ACK immediate dispatch skipped: code=ACK_ROW_NOT_SENDABLE messageId={}",
                    messageId);
            return;
        }
        if (!row.requestId().equals(requestId) || row.receivedAtMs() != persistedAtMs) {
            log.warn("ACK immediate dispatch mismatch: code=ACK_ROW_FACT_MISMATCH messageId={}",
                    messageId);
            return;
        }
        publishRow(row, status);
    }

    /** 扫描器补发路径：行内事实已由 repository 校验完整。 */
    void publishRow(TelemetryAckDeliveryRow row, TelemetryAckStatus status) {
        TelemetryAckV1 ack = new TelemetryAckV1(
                TelemetryAckV1.SCHEMA_VERSION,
                row.messageIdWire(),
                row.requestId(),
                status,
                status == TelemetryAckStatus.ACCEPTED_DURABLE ? 0 : 1001,
                status == TelemetryAckStatus.ACCEPTED_DURABLE ? "OK" : "DUPLICATE",
                row.receivedAtMs());
        boolean delivered;
        try {
            delivered = publisher.publish(ack, row.route().ackTopic());
        } catch (RuntimeException e) {
            log.warn("ACK publish failed: code=ACK_PUBLISH_ERROR error={}",
                    e.getClass().getSimpleName());
            return;
        }
        if (!delivered) {
            log.info("ACK publish unconfirmed: messageId={} attempts={}",
                    row.messageIdWire(), row.ackAttempts());
            return;
        }
        dispatchPort.markSent(row.tenantId(), row.messageIdWire(), System.currentTimeMillis());
    }
}
