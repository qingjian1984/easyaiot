package com.basiclab.iot.node.service.impl;

import cn.hutool.core.util.StrUtil;
import com.basiclab.iot.node.dal.dataobject.ComputeNodeDO;
import com.basiclab.iot.node.dal.dataobject.NodeMetricSnapshotDO;
import com.basiclab.iot.node.dal.pgsql.ComputeNodeMapper;
import com.basiclab.iot.node.dal.pgsql.NodeMetricSnapshotMapper;
import com.basiclab.iot.node.domain.vo.NodeAgentHeartbeatReqVO;
import com.basiclab.iot.node.domain.vo.NodeAgentRegisterReqVO;
import com.basiclab.iot.node.enums.NodeStatusEnum;
import com.basiclab.iot.node.service.NodeAgentService;
import com.basiclab.iot.node.service.NodeClusterMetricsBroadcaster;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;

import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.util.concurrent.TimeUnit;

import static com.basiclab.iot.common.exception.util.ServiceExceptionUtil.exception;
import static com.basiclab.iot.node.enums.ErrorCodeConstants.AGENT_TOKEN_INVALID;
import static com.basiclab.iot.node.enums.ErrorCodeConstants.COMPUTE_NODE_NOT_EXISTS;

@Service
@Validated
public class NodeAgentServiceImpl implements NodeAgentService {

    private static final String HEARTBEAT_KEY_PREFIX = "node:heartbeat:";
    private static final long HEARTBEAT_TTL_SECONDS = 60;

    @Resource
    private ComputeNodeMapper computeNodeMapper;
    @Resource
    private NodeMetricSnapshotMapper nodeMetricSnapshotMapper;
    @Resource
    private StringRedisTemplate stringRedisTemplate;
    @Resource
    private NodeClusterMetricsBroadcaster nodeClusterMetricsBroadcaster;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void register(NodeAgentRegisterReqVO reqVO) {
        ComputeNodeDO node = validateAgent(reqVO.getNodeId(), reqVO.getAgentToken());
        node.setStatus(NodeStatusEnum.ONLINE.getStatus());
        node.setLastHeartbeatAt(LocalDateTime.now());
        if (reqVO.getCapabilities() != null && !reqVO.getCapabilities().isEmpty()) {
            node.setCapabilities(reqVO.getCapabilities());
        }
        computeNodeMapper.updateById(node);
        touchHeartbeat(node.getId());
        nodeClusterMetricsBroadcaster.broadcastSnapshot();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void heartbeat(NodeAgentHeartbeatReqVO reqVO) {
        ComputeNodeDO node = validateAgent(reqVO.getNodeId(), reqVO.getAgentToken());
        node.setStatus(NodeStatusEnum.ONLINE.getStatus());
        node.setLastHeartbeatAt(LocalDateTime.now());
        computeNodeMapper.updateById(node);

        NodeMetricSnapshotDO snapshot = NodeMetricSnapshotDO.builder()
                .nodeId(node.getId())
                .cpuPercent(reqVO.getCpuPercent())
                .memPercent(reqVO.getMemPercent())
                .memUsedBytes(reqVO.getMemUsedBytes())
                .memTotalBytes(reqVO.getMemTotalBytes())
                .diskPercent(reqVO.getDiskPercent())
                .diskUsedBytes(reqVO.getDiskUsedBytes())
                .diskTotalBytes(reqVO.getDiskTotalBytes())
                .bandwidthMbps(reqVO.getBandwidthMbps())
                .activeTasks(reqVO.getActiveTasks())
                .gpuInfo(reqVO.getGpuInfo())
                .collectedAt(LocalDateTime.now())
                .build();
        nodeMetricSnapshotMapper.insert(snapshot);
        touchHeartbeat(node.getId());
        nodeClusterMetricsBroadcaster.broadcastNodeUpdate(node, snapshot);
    }

    private ComputeNodeDO validateAgent(Long nodeId, String agentToken) {
        ComputeNodeDO node = computeNodeMapper.selectById(nodeId);
        if (node == null) {
            throw exception(COMPUTE_NODE_NOT_EXISTS);
        }
        if (StrUtil.isBlank(agentToken) || !agentToken.equals(node.getAgentToken())) {
            throw exception(AGENT_TOKEN_INVALID);
        }
        return node;
    }

    private void touchHeartbeat(Long nodeId) {
        stringRedisTemplate.opsForValue().set(
                HEARTBEAT_KEY_PREFIX + nodeId,
                String.valueOf(System.currentTimeMillis()),
                HEARTBEAT_TTL_SECONDS,
                TimeUnit.SECONDS);
    }

}
