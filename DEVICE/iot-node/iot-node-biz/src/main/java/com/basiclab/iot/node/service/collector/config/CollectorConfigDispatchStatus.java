package com.basiclab.iot.node.service.collector.config;

/** 单条 collector release 派发结果。 */
public enum CollectorConfigDispatchStatus {
    APPLIED,
    FAILED,
    RETRY,
    SKIPPED,
    REENTRANT
}
