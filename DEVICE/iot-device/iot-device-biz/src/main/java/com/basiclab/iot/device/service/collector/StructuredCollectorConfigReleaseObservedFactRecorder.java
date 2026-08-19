package com.basiclab.iot.device.service.collector;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.stereotype.Component;

/**
 * 默认的 AGENT_ACCEPTED/乱序回报结构化记录器。
 *
 * <p>遵循 ADR-018：日志不写签名、完整 nonce、Token、密钥、hash、canonical 或错误详情。</p>
 */
@Component
@ConditionalOnMissingBean(CollectorConfigReleaseObservedFactRecorder.class)
public class StructuredCollectorConfigReleaseObservedFactRecorder
        implements CollectorConfigReleaseObservedFactRecorder {

    private static final Logger LOGGER = LoggerFactory.getLogger(
            StructuredCollectorConfigReleaseObservedFactRecorder.class);

    @Override
    public void record(CollectorConfigReleaseObservedFact fact, String outcome) {
        LOGGER.info("collector_config_release_observed outcome={} status={} nodeId={} workloadId={}",
                outcome, fact.status().name(), fact.nodeId(), fact.workloadId());
    }
}
