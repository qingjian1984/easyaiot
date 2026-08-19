package com.basiclab.iot.node.service.collector.config;

/** ComputeNodeMapper 权威查询的不可变网络投影。 */
public final class CollectorNodeEndpoint {

    private final long nodeId;
    private final String status;
    private final String host;
    private final int agentPort;

    public CollectorNodeEndpoint(long nodeId, String status, String host, int agentPort) {
        this.nodeId = nodeId;
        this.status = status;
        this.host = host;
        this.agentPort = agentPort;
    }

    public long getNodeId() { return nodeId; }

    public String getStatus() { return status; }

    public String getHost() { return host; }

    public int getAgentPort() { return agentPort; }
}
