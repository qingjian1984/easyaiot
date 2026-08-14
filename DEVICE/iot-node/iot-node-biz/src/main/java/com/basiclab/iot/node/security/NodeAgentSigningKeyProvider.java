package com.basiclab.iot.node.security;

import java.util.List;

/** iot-node 获取节点 signing key 的专用 Provider。 */
@FunctionalInterface
public interface NodeAgentSigningKeyProvider {

    List<NodeAgentSigningKey> findKeys(long nodeId);
}
