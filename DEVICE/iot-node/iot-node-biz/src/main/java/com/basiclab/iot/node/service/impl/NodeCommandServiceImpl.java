package com.basiclab.iot.node.service.impl;

import cn.hutool.http.HttpRequest;
import cn.hutool.http.HttpResponse;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.basiclab.iot.node.dal.dataobject.ComputeNodeDO;
import com.basiclab.iot.node.dal.dataobject.NodeWorkloadBindingDO;
import com.basiclab.iot.node.dal.pgsql.ComputeNodeMapper;
import com.basiclab.iot.node.dal.pgsql.NodeWorkloadBindingMapper;
import com.basiclab.iot.node.domain.vo.NodeWorkloadDeployReqVO;
import com.basiclab.iot.node.domain.vo.NodeWorkloadDeployRespVO;
import com.basiclab.iot.node.enums.NodeStatusEnum;
import com.basiclab.iot.node.service.NodeCommandService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;

import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

import static com.basiclab.iot.common.exception.util.ServiceExceptionUtil.exception;
import static com.basiclab.iot.node.enums.ErrorCodeConstants.COMPUTE_NODE_NOT_EXISTS;
import static com.basiclab.iot.node.enums.ErrorCodeConstants.COMPUTE_NODE_OFFLINE;

@Slf4j
@Service
@Validated
public class NodeCommandServiceImpl implements NodeCommandService {

    private static final int AGENT_TIMEOUT_MS = 60000;

    @Resource
    private ComputeNodeMapper computeNodeMapper;
    @Resource
    private NodeWorkloadBindingMapper nodeWorkloadBindingMapper;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public NodeWorkloadDeployRespVO deployWorkload(NodeWorkloadDeployReqVO reqVO) {
        ComputeNodeDO node = validateOnlineNode(reqVO.getNodeId());
        Map<String, Object> body = new HashMap<>();
        body.put("workloadType", reqVO.getWorkloadType());
        body.put("workloadId", reqVO.getWorkloadId());
        body.put("command", reqVO.getCommand());
        body.put("workDir", reqVO.getWorkDir());
        body.put("logDir", reqVO.getLogDir());
        body.put("gpuIds", reqVO.getGpuIds());
        body.put("env", reqVO.getEnv());

        JSONObject result = callAgent(node, "/workload/deploy", body);
        Integer pid = result.getInt("pid");

        NodeWorkloadBindingDO binding = nodeWorkloadBindingMapper.selectByWorkload(
                reqVO.getWorkloadType(), reqVO.getWorkloadId());
        if (binding == null) {
            binding = NodeWorkloadBindingDO.builder()
                    .nodeId(node.getId())
                    .workloadType(reqVO.getWorkloadType())
                    .workloadId(reqVO.getWorkloadId())
                    .status("running")
                    .processPid(pid)
                    .bindAt(LocalDateTime.now())
                    .build();
            nodeWorkloadBindingMapper.insert(binding);
        } else {
            binding.setNodeId(node.getId());
            binding.setStatus("running");
            binding.setProcessPid(pid);
            binding.setBindAt(LocalDateTime.now());
            nodeWorkloadBindingMapper.updateById(binding);
        }

        NodeWorkloadDeployRespVO resp = new NodeWorkloadDeployRespVO();
        resp.setPid(pid);
        resp.setNodeId(node.getId());
        resp.setWorkloadType(reqVO.getWorkloadType());
        resp.setWorkloadId(reqVO.getWorkloadId());
        resp.setBindingId(binding.getId());
        return resp;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void stopWorkload(Long nodeId, String workloadType, String workloadId) {
        ComputeNodeDO node = computeNodeMapper.selectById(nodeId);
        if (node == null) {
            throw exception(COMPUTE_NODE_NOT_EXISTS);
        }
        Map<String, Object> body = new HashMap<>();
        body.put("workloadType", workloadType);
        body.put("workloadId", workloadId);
        try {
            callAgent(node, "/workload/stop", body);
        } catch (Exception e) {
            log.warn("远程停止工作负载失败 nodeId={} {}:{} - {}", nodeId, workloadType, workloadId, e.getMessage());
        }
        NodeWorkloadBindingDO binding = nodeWorkloadBindingMapper.selectByWorkload(workloadType, workloadId);
        if (binding != null) {
            binding.setStatus("stopped");
            binding.setProcessPid(null);
            nodeWorkloadBindingMapper.updateById(binding);
        }
    }

    private ComputeNodeDO validateOnlineNode(Long nodeId) {
        ComputeNodeDO node = computeNodeMapper.selectById(nodeId);
        if (node == null) {
            throw exception(COMPUTE_NODE_NOT_EXISTS);
        }
        if (!NodeStatusEnum.ONLINE.getStatus().equals(node.getStatus())) {
            throw exception(COMPUTE_NODE_OFFLINE);
        }
        return node;
    }

    private JSONObject callAgent(ComputeNodeDO node, String path, Map<String, Object> body) {
        int port = node.getAgentPort() != null ? node.getAgentPort() : 9100;
        String url = String.format("http://%s:%d%s", node.getHost(), port, path);
        HttpResponse response = HttpRequest.post(url)
                .header("X-Agent-Token", node.getAgentToken())
                .header("Content-Type", "application/json")
                .body(JSONUtil.toJsonStr(body))
                .timeout(AGENT_TIMEOUT_MS)
                .execute();
        if (!response.isOk()) {
            throw new IllegalStateException("Agent 请求失败 HTTP " + response.getStatus() + ": " + response.body());
        }
        JSONObject json = JSONUtil.parseObj(response.body());
        if (json.getInt("code", -1) != 0) {
            throw new IllegalStateException("Agent 执行失败: " + json.getStr("msg", response.body()));
        }
        return json.getJSONObject("data") != null ? json.getJSONObject("data") : new JSONObject();
    }

}
