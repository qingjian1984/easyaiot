package com.basiclab.iot.node.service.collector.config;

import java.util.Optional;

/** 节点地址与在线状态的唯一权威查询 port。 */
@FunctionalInterface
public interface CollectorNodeAuthorityPort {

    Optional<CollectorNodeEndpoint> findById(long nodeId);
}
