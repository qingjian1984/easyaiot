package com.basiclab.iot.node.service.collector.config;

import com.basiclab.iot.node.domain.collector.config.CollectorConfigGetResponseDTO;
import com.basiclab.iot.node.domain.collector.config.CollectorConfigPutRequestDTO;
import com.basiclab.iot.node.domain.collector.config.CollectorConfigPutResponseDTO;

/** Agent 配置协议 port；生产实现为固定路径的 {@link CollectorAgentClient}。 */
public interface CollectorAgentPort {

    CollectorConfigPutResponseDTO putConfig(CollectorNodeEndpoint node,
                                            CollectorConfigPutRequestDTO request);

    CollectorConfigGetResponseDTO getConfig(CollectorNodeEndpoint node, String workloadId);
}
