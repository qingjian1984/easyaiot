package com.basiclab.iot.device.service.collector;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 默认的 AGENT_ACCEPTED/乱序回报结构化记录器。
 *
 * <p>遵循 ADR-018：日志不写签名、完整 nonce、Token、密钥、hash、canonical 或错误详情。</p>
 *
 * <p>由 {@code CollectorConfigReleaseWiringConfiguration} 显式 @Bean 装配
 * （组件扫描 + @ConditionalOnMissingBean 在 verifier 启用的装配顺序下不可靠，
 * 见该配置类注释）；类上不再放 Spring 注解。</p>
 */
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
