package com.basiclab.iot.device.service.event;

/**
 * TD-001 §6.2：模板生命周期引用标记端口。
 * 语义：只更新引用标记——引用该模板版本的活跃绑定所属 workload 的后续人工发布
 * 须在确认页提示；**不得改写任何已发布快照**（PRD-01 §4.2：已绑定设备不被
 * 未确认升级自动改变）。
 */
public interface PowerModelTemplateReferencePort {

    /**
     * 记录模板版本生命周期引用标记（DEPRECATED/RETIRED 等）。
     * 同 (tenantId, templateCode, templateVersion, toLifecycle) 键重复标记幂等
     * （刷新标记时间与来源事件），重复/乱序事件由 ADR-014 Inbox 先行去重。
     *
     * @param sourceEventId 来源事件 UUID（幂等溯源，与 Inbox event_id 一致）
     */
    void markLifecycleReference(long tenantId, String templateCode, String templateVersion,
                                String fromLifecycle, String toLifecycle, String sourceEventId);
}
