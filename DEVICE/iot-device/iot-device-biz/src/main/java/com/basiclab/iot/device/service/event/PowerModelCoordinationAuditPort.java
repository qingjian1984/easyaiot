package com.basiclab.iot.device.service.event;

/**
 * TD-001 §6.2：协调审计端口。
 * 记录协调器的处置事实（noop-with-audit、引用标记、影响面空集、再生发布单数量），
 * 保证「未产生发布单」也有持久证据（宪法 §15）。
 * detail 必须脱敏且有界（≤512 字符），绝不携带 payload 正文。
 */
public interface PowerModelCoordinationAuditPort {

    /**
     * 写一条协调审计。
     *
     * @param eventType 来源事件类型（含主版本，与 Envelope eventType 一致）
     * @param action 处置动作（稳定码，如 TEMPLATE_PUBLISHED_NOTED / LIFECYCLE_REFERENCE_MARKED /
     *               IMPACT_EMPTY / REGENERATION_DRAFTS_CREATED）
     * @param detail 脱敏有界摘要（≤512 字符）
     */
    void record(String eventId, long tenantId, String eventType, String action, String detail);
}
