package com.basiclab.iot.node.service.collector.config;

import com.basiclab.iot.node.dal.dataobject.ComputeNodeDO;
import com.basiclab.iot.node.dal.pgsql.ComputeNodeMapper;

import java.util.Optional;

/** 将 ComputeNodeMapper 的只读结果收窄为 collector 派发所需字段。 */
public final class ComputeNodeCollectorAuthorityAdapter implements CollectorNodeAuthorityPort {

    private final ComputeNodeMapper mapper;

    public ComputeNodeCollectorAuthorityAdapter(ComputeNodeMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public Optional<CollectorNodeEndpoint> findById(long nodeId) {
        ComputeNodeDO node = mapper.selectById(nodeId);
        if (node == null || node.getId() == null) {
            return Optional.empty();
        }
        int agentPort = node.getAgentPort() == null ? -1 : node.getAgentPort();
        return Optional.of(new CollectorNodeEndpoint(
                node.getId(), node.getStatus(), node.getHost(), agentPort));
    }
}
