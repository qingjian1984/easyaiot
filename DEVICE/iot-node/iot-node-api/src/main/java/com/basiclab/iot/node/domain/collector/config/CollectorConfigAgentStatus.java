package com.basiclab.iot.node.domain.collector.config;

/**
 * NODE collector 配置状态的闭集。
 *
 * <p>未知值不会被映射到本枚举；调用方应把未知状态视为不确定并重试，不能把它当成成功。</p>
 */
public enum CollectorConfigAgentStatus {

    ACCEPTED,
    IDEMPOTENT,
    WAITING_CONFIG,
    AGENT_ACCEPTED,
    APPLIED,
    FAILED,
    DEGRADED
}
