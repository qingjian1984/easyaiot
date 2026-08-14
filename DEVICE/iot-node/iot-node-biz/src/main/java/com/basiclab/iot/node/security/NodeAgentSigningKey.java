package com.basiclab.iot.node.security;

/** 节点签名密钥快照；secret 不参与 toString/logging。 */
public final class NodeAgentSigningKey {

    private final long nodeId;
    private final String keyId;
    private final byte[] secret;

    public NodeAgentSigningKey(long nodeId, String keyId, byte[] secret) {
        this.nodeId = nodeId;
        this.keyId = keyId;
        this.secret = secret.clone();
    }

    public long getNodeId() {
        return nodeId;
    }

    public String getKeyId() {
        return keyId;
    }

    public byte[] secretCopy() {
        return secret.clone();
    }
}
