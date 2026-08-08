package com.basiclab.iot.device.service.event;

import java.time.Duration;

/**
 * ADR-014 §可观测性与对账：事件链路指标端口。领域逻辑只依赖本端口，
 * Micrometer 适配在装配层注入，合同测试使用手写 fake。
 * 指标名冻结（与 ADR-014 §可观测性一致）：
 * {@code power_model_event_publish_total{result}}（result ∈ published /
 * retry_scheduled / dead_letter）、{@code power_model_event_delivery_duration}、
 * {@code power_model_inbox_quarantined_total}。
 * gauge（{@code power_model_outbox_backlog} / {@code power_model_dlq_depth}）
 * 由装配层直接绑定仓储计数供应商，不经本端口。Java 8 兼容。
 */
public interface PowerModelEventMetrics {

    /** 发布结果计数：result ∈ published / retry_scheduled / dead_letter。 */
    void eventPublished(String result);

    /** 单事件投递耗时（transport send 调用时长，P95 目标 ≤ 5 s 待压测冻结）。 */
    void recordDeliveryDuration(Duration duration);

    /** Inbox 隔离处置计数（同 ID 异 hash / 未知主版本 critical 事件）。 */
    void inboxQuarantined();
}
