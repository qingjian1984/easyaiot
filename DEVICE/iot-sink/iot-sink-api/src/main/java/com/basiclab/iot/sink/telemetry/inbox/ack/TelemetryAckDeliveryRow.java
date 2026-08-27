package com.basiclab.iot.sink.telemetry.inbox.ack;

import com.basiclab.iot.sink.telemetry.outbox.TelemetryRoute;

import java.util.Objects;

/**
 * LC03-03 §5.2 一条已持久 Inbox 行的 ACK 派发事实。
 *
 * <p>所有字段均来自 PostgreSQL 已持久行；发送方不得从请求 payload、
 * site/property、当前 DeviceDO 或展示名称补猜任何字段。
 */
public record TelemetryAckDeliveryRow(
        long tenantId,
        String messageIdWire,
        String requestId,
        TelemetryRoute route,
        long receivedAtMs,
        Long ackSentAtMs,
        int ackAttempts
) {

    public TelemetryAckDeliveryRow {
        Objects.requireNonNull(messageIdWire, "messageIdWire");
        Objects.requireNonNull(requestId, "requestId");
        Objects.requireNonNull(route, "route");
        if (tenantId <= 0) {
            throw new IllegalArgumentException("tenantId must be positive");
        }
        if (receivedAtMs < 0) {
            throw new IllegalArgumentException("receivedAtMs must be non-negative");
        }
        if (ackAttempts < 0) {
            throw new IllegalArgumentException("ackAttempts must be non-negative");
        }
    }

    /** 路由不完整或 requestId 缺失的行不允许进入发送链。 */
    public boolean isSendable() {
        return requestId != null && !requestId.isBlank();
    }
}
