package com.basiclab.iot.sink.telemetry.inbox.ack;

import java.util.List;

/**
 * LC03-03 §5.2/§5.3/§5.4 中心 ACK 派发端口。
 *
 * <p>实现只从 PostgreSQL 已持久 {@code iot_sink.telemetry_inbox} 行加载
 * 派发事实；每次发送前在独立短事务内递增 {@code ack_attempts}，
 * publish 确认后才以条件更新写 {@code ack_sent_at_ms}。
 */
public interface TelemetryAckDispatchPort {

    /**
     * 按 {@code (received_at_ms, id)} 升序领取最多 {@code limit} 条
     * {@code ack_sent_at_ms IS NULL} 且路由完整的待发行。
     *
     * <p>多实例允许重复领取同一批行；正确性由 collector 幂等吸收
     * 重复 ACK 保证，本端口不得跨 publish 持锁。
     */
    List<TelemetryAckDeliveryRow> claimPending(int limit);

    /**
     * 加载一条已持久 Inbox 成功行的派发事实（即时路径）。
     * 行缺失、产品/设备路由不完整或 requestId 缺失时返回 null，
     * 调用方必须 fail-closed 不发送。
     */
    TelemetryAckDeliveryRow loadForImmediateAck(long tenantId, String messageId);

    /** publish 确认成功后标记发送时刻；只更新尚未标记的行。 */
    boolean markSent(long tenantId, String messageId, long sentAtMs);
}
