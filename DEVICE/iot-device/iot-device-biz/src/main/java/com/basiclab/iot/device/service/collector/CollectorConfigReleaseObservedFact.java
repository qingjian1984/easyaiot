package com.basiclab.iot.device.service.collector;

import com.basiclab.iot.device.domain.collector.dto.CollectorConfigReleaseObservedStatus;

/** 仅用于结构化 observed 可观测记录的已校验事实；不含错误详情或 canonical payload。 */
public record CollectorConfigReleaseObservedFact(
        long releaseId,
        long tenantId,
        long nodeId,
        String workloadId,
        long configVersion,
        CollectorConfigReleaseObservedStatus status) {
}
