package com.basiclab.iot.node.service.impl;

import cn.hutool.core.util.StrUtil;
import com.basiclab.iot.node.dal.dataobject.ComputeNodeDO;
import com.basiclab.iot.node.dal.dataobject.NodeMetricSnapshotDO;
import com.basiclab.iot.node.dal.pgsql.ComputeNodeMapper;
import com.basiclab.iot.node.dal.pgsql.NodeMetricSnapshotMapper;
import com.basiclab.iot.node.domain.vo.NodeAgentHeartbeatReqVO;
import com.basiclab.iot.node.domain.vo.NodeAgentRegisterReqVO;
import com.basiclab.iot.node.enums.NodeRoleEnum;
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
            java.util.Map<String, Boolean> caps = node.getCapabilities() != null
                    ? new java.util.HashMap<>(node.getCapabilities()) : new java.util.HashMap<>();
            caps.putAll(reqVO.getCapabilities());
            if (ComputeNodeServiceImpl.isPlatformNode(node)) {
                caps.put("platform", true);
            }
            node.setCapabilities(caps);
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
        syncGpuCountFromHeartbeat(node, reqVO.getGpuInfo());
        syncCephMountFromHeartbeat(node, reqVO);
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

    /** GPU 节点：根据 Agent 上报的 gpu_info 同步 maxGpuCount */
    private void syncGpuCountFromHeartbeat(ComputeNodeDO node, java.util.List<java.util.Map<String, Object>> gpuInfo) {
        if (!NodeRoleEnum.GPU.getRole().equals(node.getNodeRole()) || gpuInfo == null || gpuInfo.isEmpty()) {
            return;
        }
        int detected = gpuInfo.size();
        if (node.getMaxGpuCount() == null || node.getMaxGpuCount() < detected) {
            node.setMaxGpuCount(detected);
        }
    }

    /** 将 Agent 上报的 Ceph 挂载状态写入节点 tags，供调度器过滤 */
    private void syncCephMountFromHeartbeat(ComputeNodeDO node, NodeAgentHeartbeatReqVO reqVO) {
        if (reqVO.getCephMountReady() == null && StrUtil.isBlank(reqVO.getCephMountRoot())) {
            return;
        }
        java.util.Map<String, String> tags = node.getTags() != null
                ? new java.util.HashMap<>(node.getTags()) : new java.util.HashMap<>();
        if (reqVO.getCephMountReady() != null) {
            tags.put("ceph_mount_ready", Boolean.TRUE.equals(reqVO.getCephMountReady()) ? "true" : "false");
        }
        if (StrUtil.isNotBlank(reqVO.getCephMountRoot())) {
            tags.put("ceph_mount_path", reqVO.getCephMountRoot().trim());
        }
        if (reqVO.getClusterMode() != null) {
            tags.put("cluster_mode", Boolean.TRUE.equals(reqVO.getClusterMode()) ? "true" : "false");
        }
        node.setTags(tags);
    }

}
