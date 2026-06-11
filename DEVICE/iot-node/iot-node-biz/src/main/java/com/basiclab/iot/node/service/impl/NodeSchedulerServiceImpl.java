package com.basiclab.iot.node.service.impl;

import cn.hutool.core.collection.CollUtil;
import com.basiclab.iot.common.domain.PageResult;
import com.basiclab.iot.node.dal.dataobject.ComputeNodeDO;
import com.basiclab.iot.node.dal.dataobject.NodeMetricSnapshotDO;
import com.basiclab.iot.node.dal.dataobject.NodeWorkloadBindingDO;
import com.basiclab.iot.node.dal.pgsql.ComputeNodeMapper;
import com.basiclab.iot.node.dal.pgsql.NodeMetricSnapshotMapper;
import com.basiclab.iot.node.dal.pgsql.NodeWorkloadBindingMapper;
import com.basiclab.iot.node.domain.vo.ComputeNodePageReqVO;
import com.basiclab.iot.node.domain.vo.NodeSchedulerAllocateReqVO;
import com.basiclab.iot.node.domain.vo.NodeSchedulerAllocateRespVO;
import com.basiclab.iot.node.enums.NodeRoleEnum;
import com.basiclab.iot.node.enums.NodeStatusEnum;
import com.basiclab.iot.node.service.NodeSchedulerService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;

import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

import static com.basiclab.iot.common.exception.util.ServiceExceptionUtil.exception;
import static com.basiclab.iot.node.enums.ErrorCodeConstants.NODE_POOL_EXHAUSTED;

@Service
@Validated
public class NodeSchedulerServiceImpl implements NodeSchedulerService {

    @Resource
    private ComputeNodeMapper computeNodeMapper;
    @Resource
    private NodeMetricSnapshotMapper nodeMetricSnapshotMapper;
    @Resource
    private NodeWorkloadBindingMapper nodeWorkloadBindingMapper;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public NodeSchedulerAllocateRespVO allocate(NodeSchedulerAllocateReqVO reqVO) {
        if (Boolean.TRUE.equals(reqVO.getSticky())) {
            NodeWorkloadBindingDO existing = nodeWorkloadBindingMapper.selectByWorkload(
                    reqVO.getWorkloadType(), reqVO.getWorkloadId());
            if (existing != null && "running".equals(existing.getStatus())) {
                ComputeNodeDO node = computeNodeMapper.selectById(existing.getNodeId());
                if (node != null && NodeStatusEnum.ONLINE.getStatus().equals(node.getStatus())) {
                    return buildResp(node, existing.getId(), null);
                }
            }
        }

        ComputeNodePageReqVO pageReqVO = new ComputeNodePageReqVO();
        pageReqVO.setPageNo(1);
        pageReqVO.setPageSize(200);
        pageReqVO.setStatus(NodeStatusEnum.ONLINE.getStatus());
        if (reqVO.getRequirements() != null) {
            pageReqVO.setRegion(reqVO.getRequirements().getRegion());
        }
        PageResult<ComputeNodeDO> page = computeNodeMapper.selectPage(pageReqVO);
        List<ComputeNodeDO> candidates = page.getList().stream()
                .filter(node -> matchRequirements(node, reqVO))
                .sorted(Comparator.comparingDouble(this::scoreNode).reversed())
                .toList();

        if (candidates.isEmpty()) {
            throw exception(NODE_POOL_EXHAUSTED);
        }
        ComputeNodeDO selected = candidates.get(0);
        NodeWorkloadBindingDO binding = NodeWorkloadBindingDO.builder()
                .nodeId(selected.getId())
                .workloadType(reqVO.getWorkloadType())
                .workloadId(reqVO.getWorkloadId())
                .status("running")
                .bindAt(LocalDateTime.now())
                .build();
        NodeWorkloadBindingDO existing = nodeWorkloadBindingMapper.selectByWorkload(
                reqVO.getWorkloadType(), reqVO.getWorkloadId());
        if (existing != null) {
            binding.setId(existing.getId());
            nodeWorkloadBindingMapper.updateById(binding);
        } else {
            nodeWorkloadBindingMapper.insert(binding);
        }
        return buildResp(selected, binding.getId(), pickGpuIds(selected));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void release(String workloadType, String workloadId) {
        NodeWorkloadBindingDO binding = nodeWorkloadBindingMapper.selectByWorkload(workloadType, workloadId);
        if (binding != null) {
            binding.setStatus("stopped");
            nodeWorkloadBindingMapper.updateById(binding);
        }
    }

    private boolean matchRequirements(ComputeNodeDO node, NodeSchedulerAllocateReqVO reqVO) {
        if (!matchNodeRole(node, reqVO.getWorkloadType())) {
            return false;
        }
        NodeSchedulerAllocateReqVO.Requirements req = reqVO.getRequirements();
        if (req == null) {
            return true;
        }
        if (CollUtil.isNotEmpty(req.getExcludeNodeIds()) && req.getExcludeNodeIds().contains(node.getId())) {
            return false;
        }
        if (req.getCapabilities() != null) {
            Map<String, Boolean> caps = node.getCapabilities();
            for (String capability : req.getCapabilities()) {
                if (caps == null || !Boolean.TRUE.equals(caps.get(capability))) {
                    return false;
                }
            }
        }
        long running = nodeWorkloadBindingMapper.countRunningByNodeId(node.getId());
        return running < node.getMaxTaskCount();
    }

    private boolean matchNodeRole(ComputeNodeDO node, String workloadType) {
        String role = node.getNodeRole();
        if (isMediaWorkload(workloadType)) {
            return NodeRoleEnum.MEDIA.getRole().equals(role) || NodeRoleEnum.HYBRID.getRole().equals(role);
        }
        if (isComputeWorkload(workloadType)) {
            return NodeRoleEnum.COMPUTE.getRole().equals(role) || NodeRoleEnum.HYBRID.getRole().equals(role);
        }
        return true;
    }

    private boolean isMediaWorkload(String workloadType) {
        return "srs_live".equals(workloadType) || "srs_ai".equals(workloadType) || "zlm".equals(workloadType);
    }

    private boolean isComputeWorkload(String workloadType) {
        return "ai_service".equals(workloadType) || "algorithm_task".equals(workloadType)
                || "stream_forward".equals(workloadType);
    }

    private double scoreNode(ComputeNodeDO node) {
        double weight = node.getWeight() != null ? node.getWeight() : 100;
        NodeMetricSnapshotDO metric = nodeMetricSnapshotMapper.selectLatestByNodeId(node.getId());
        double cpu = metric != null && metric.getCpuPercent() != null
                ? metric.getCpuPercent().doubleValue() / 100.0 : 0.3;
        long running = nodeWorkloadBindingMapper.countRunningByNodeId(node.getId());
        double taskLoad = node.getMaxTaskCount() > 0 ? (double) running / node.getMaxTaskCount() : 0;
        return weight * (1 - cpu) * (1 - taskLoad);
    }

    private String pickGpuIds(ComputeNodeDO node) {
        if (node.getMaxGpuCount() == null || node.getMaxGpuCount() <= 0) {
            return null;
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < node.getMaxGpuCount(); i++) {
            if (i > 0) {
                sb.append(',');
            }
            sb.append(i);
        }
        return sb.toString();
    }

    private NodeSchedulerAllocateRespVO buildResp(ComputeNodeDO node, Long bindingId, String gpuIds) {
        NodeSchedulerAllocateRespVO resp = new NodeSchedulerAllocateRespVO();
        resp.setNodeId(node.getId());
        resp.setHost(node.getHost());
        resp.setAgentPort(node.getAgentPort());
        resp.setGpuIds(gpuIds);
        resp.setBindingId(bindingId);
        return resp;
    }

}
