package com.basiclab.iot.device.domain.collector.dto;

/** Agent 对 release 的可观测状态；APPLY_TIMEOUT 不允许由 Agent 自报。 */
public enum CollectorConfigReleaseObservedStatus {
    AGENT_ACCEPTED,
    APPLIED,
    FAILED
}
