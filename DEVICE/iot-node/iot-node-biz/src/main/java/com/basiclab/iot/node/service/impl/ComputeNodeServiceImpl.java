package com.basiclab.iot.node.service.impl;

import cn.hutool.core.util.IdUtil;
import cn.hutool.core.util.StrUtil;
import com.basiclab.iot.common.core.query.LambdaQueryWrapperX;
import com.basiclab.iot.common.domain.PageResult;
import com.basiclab.iot.common.utils.object.BeanUtils;
import com.basiclab.iot.node.dal.dataobject.ComputeNodeDO;
import com.basiclab.iot.node.dal.dataobject.NodeMetricSnapshotDO;
import com.basiclab.iot.node.dal.dataobject.NodeSshCredentialDO;
import com.basiclab.iot.node.dal.pgsql.ComputeNodeMapper;
import com.basiclab.iot.node.dal.pgsql.NodeMetricSnapshotMapper;
import com.basiclab.iot.node.dal.pgsql.NodeSshCredentialMapper;
import com.basiclab.iot.node.dal.pgsql.NodeWorkloadBindingMapper;
import com.basiclab.iot.node.domain.vo.ComputeNodePageReqVO;
import com.basiclab.iot.node.domain.vo.ComputeNodeRespVO;
import com.basiclab.iot.node.domain.vo.ComputeNodeSaveReqVO;
import com.basiclab.iot.node.domain.vo.NodeAgentCheckRespVO;
import com.basiclab.iot.node.domain.vo.NodeMediaRemoteDeployRespVO;
import com.basiclab.iot.node.domain.vo.NodeMetricTrendPointRespVO;
import com.basiclab.iot.node.domain.vo.NodeMetricTrendReqVO;
import com.basiclab.iot.node.domain.vo.NodeMetricTrendRespVO;
import com.basiclab.iot.node.domain.vo.NodeMetricTrendSeriesRespVO;
import com.basiclab.iot.node.enums.NodeRoleEnum;
import com.basiclab.iot.node.enums.NodeStatusEnum;
import com.basiclab.iot.node.service.ComputeNodeService;
import com.basiclab.iot.node.util.AgentDeployUtil;
import com.basiclab.iot.node.util.CredentialEncryptUtil;
import com.basiclab.iot.node.util.SshClientUtil;
import com.basiclab.iot.node.util.SshSessionHelper;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;

import javax.annotation.Resource;
import java.io.File;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

import static com.basiclab.iot.common.exception.util.ServiceExceptionUtil.exception;
import static com.basiclab.iot.node.enums.ErrorCodeConstants.*;

@Service
@Validated
@Slf4j
public class ComputeNodeServiceImpl implements ComputeNodeService {

    private static final int DEFAULT_SSH_PORT = 22;
    private static final int DEFAULT_AGENT_PORT = 9100;
    private static final int DEPLOY_TIMEOUT_MS = 300000;

    @Resource
    private ComputeNodeMapper computeNodeMapper;
    @Resource
    private NodeSshCredentialMapper nodeSshCredentialMapper;
    @Resource
    private NodeMetricSnapshotMapper nodeMetricSnapshotMapper;
    @Resource
    private NodeWorkloadBindingMapper nodeWorkloadBindingMapper;
    @Resource
    private ObjectMapper objectMapper;

    @Value("${easyaiot.agent.source-path:}")
    private String agentSourcePath;
    @Value("${easyaiot.agent.control-plane-url:}")
    private String agentControlPlaneUrl;
    @Value("${easyaiot.media.hook-host:127.0.0.1}")
    private String mediaHookHost;
    @Value("${easyaiot.media.hook-port:48080}")
    private int mediaHookPort;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ComputeNodeRespVO createNode(ComputeNodeSaveReqVO createReqVO) {
        if (computeNodeMapper.selectByHost(createReqVO.getHost()) != null) {
            throw exception(COMPUTE_NODE_HOST_EXISTS);
        }
        ComputeNodeDO node = BeanUtils.toBean(createReqVO, ComputeNodeDO.class);
        node.setSshPort(defaultPort(createReqVO.getSshPort(), DEFAULT_SSH_PORT));
        node.setAgentPort(defaultPort(createReqVO.getAgentPort(), DEFAULT_AGENT_PORT));
        node.setStatus(NodeStatusEnum.PENDING.getStatus());
        node.setWeight(createReqVO.getWeight() != null ? createReqVO.getWeight() : 100);
        node.setMaxGpuCount(createReqVO.getMaxGpuCount() != null ? createReqVO.getMaxGpuCount() : 0);
        node.setMaxTaskCount(createReqVO.getMaxTaskCount() != null ? createReqVO.getMaxTaskCount() : 50);
        if (node.getCapabilities() == null) {
            node.setCapabilities(defaultCapabilities(createReqVO.getNodeRole()));
        }
        node.setAgentToken(IdUtil.fastSimpleUUID());
        computeNodeMapper.insert(node);
        saveSshCredential(node.getId(), createReqVO);
        return toRespVO(node, true);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateNode(ComputeNodeSaveReqVO updateReqVO) {
        ComputeNodeDO existing = validateExists(updateReqVO.getId());
        ComputeNodeDO other = computeNodeMapper.selectByHost(updateReqVO.getHost());
        if (other != null && !other.getId().equals(updateReqVO.getId())) {
            throw exception(COMPUTE_NODE_HOST_EXISTS);
        }
        ComputeNodeDO updateObj = BeanUtils.toBean(updateReqVO, ComputeNodeDO.class);
        updateObj.setSshPort(defaultPort(updateReqVO.getSshPort(), defaultPort(existing.getSshPort(), DEFAULT_SSH_PORT)));
        updateObj.setAgentPort(defaultPort(updateReqVO.getAgentPort(), defaultPort(existing.getAgentPort(), DEFAULT_AGENT_PORT)));
        updateObj.setAgentToken(existing.getAgentToken());
        updateObj.setStatus(existing.getStatus());
        updateObj.setLastHeartbeatAt(existing.getLastHeartbeatAt());
        computeNodeMapper.updateById(updateObj);
        saveSshCredential(updateReqVO.getId(), updateReqVO);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteNode(Long id) {
        validateExists(id);
        if (nodeWorkloadBindingMapper.countRunningByNodeId(id) > 0) {
            throw exception(COMPUTE_NODE_HAS_WORKLOAD);
        }
        computeNodeMapper.deleteById(id);
        NodeSshCredentialDO credential = nodeSshCredentialMapper.selectByNodeId(id);
        if (credential != null) {
            nodeSshCredentialMapper.deleteById(credential.getId());
        }
    }

    @Override
    public ComputeNodeRespVO getNode(Long id) {
        ComputeNodeDO node = validateExists(id);
        return toRespVO(node, false);
    }

    @Override
    public PageResult<ComputeNodeRespVO> getNodePage(ComputeNodePageReqVO pageReqVO) {
        PageResult<ComputeNodeDO> pageResult = computeNodeMapper.selectPage(pageReqVO);
        List<ComputeNodeRespVO> list = pageResult.getList().stream()
                .map(node -> toRespVO(node, false))
                .collect(Collectors.toList());
        return new PageResult<>(list, pageResult.getTotal());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean testSsh(Long id) {
        ComputeNodeDO node = validateExists(id);
        NodeSshCredentialDO credential = nodeSshCredentialMapper.selectByNodeId(id);
        if (credential == null) {
            throw exception(SSH_CREDENTIAL_NOT_EXISTS);
        }
        String password = null;
        String privateKey = null;
        if ("password".equals(credential.getAuthType())) {
            password = CredentialEncryptUtil.decrypt(credential.getCredentialEnc());
        } else {
            privateKey = CredentialEncryptUtil.decrypt(credential.getCredentialEnc());
        }
        int sshPort = resolveSshPort(node);
        boolean ok = SshClientUtil.testConnection(node.getHost(), sshPort,
                credential.getUsername(), credential.getAuthType(), password, privateKey);
        credential.setLastTestAt(LocalDateTime.now());
        credential.setLastTestOk(ok);
        nodeSshCredentialMapper.updateById(credential);
        if (!ok) {
            throw exception(SSH_CONNECT_FAILED);
        }
        return true;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public String resetAgentToken(Long id) {
        ComputeNodeDO node = validateExists(id);
        String token = IdUtil.fastSimpleUUID();
        node.setAgentToken(token);
        computeNodeMapper.updateById(node);
        return token;
    }

    @Override
    public ComputeNodeRespVO getAgentSetup(Long id) {
        ComputeNodeDO node = validateExists(id);
        if (!NodeStatusEnum.PENDING.getStatus().equals(node.getStatus())) {
            throw exception(COMPUTE_NODE_NOT_PENDING);
        }
        return toRespVO(node, true);
    }

    @Override
    public NodeMediaRemoteDeployRespVO deployAgentBySsh(Long nodeId, String controlPlaneUrlOverride) {
        ComputeNodeDO node = validateExists(nodeId);
        NodeSshCredentialDO credential = nodeSshCredentialMapper.selectByNodeId(nodeId);
        if (credential == null) {
            throw exception(SSH_CREDENTIAL_NOT_EXISTS);
        }
        String password = null;
        String privateKey = null;
        if ("password".equals(credential.getAuthType())) {
            password = CredentialEncryptUtil.decrypt(credential.getCredentialEnc());
        } else {
            privateKey = CredentialEncryptUtil.decrypt(credential.getCredentialEnc());
        }

        NodeMediaRemoteDeployRespVO resp = new NodeMediaRemoteDeployRespVO();
        List<NodeMediaRemoteDeployRespVO.DeployStep> steps = new ArrayList<>();
        resp.setSteps(steps);

        int sshPort = resolveSshPort(node);
        try (SshSessionHelper ssh = SshSessionHelper.connect(
                node.getHost(),
                sshPort,
                credential.getUsername(),
                credential.getAuthType(),
                password,
                privateKey)) {

            steps.add(runDeployStep("SSH 连接", "success", "已连接 " + node.getHost() + ":" + sshPort));

            String sourceRoot = resolveAgentSource();
            steps.add(syncAgentFiles(ssh, sourceRoot));

            String controlPlaneUrl = resolveControlPlaneUrl(controlPlaneUrlOverride);
            String envContent = AgentDeployUtil.buildEnvContent(node, controlPlaneUrl);
            String installScript = AgentDeployUtil.buildInstallScript(envContent);
            String encoded = Base64.getEncoder().encodeToString(installScript.getBytes(StandardCharsets.UTF_8));
            SshSessionHelper.SshExecResult installResult = ssh.exec(
                    "echo " + encoded + " | base64 -d > /tmp/easyaiot-agent-install.sh "
                            + "&& chmod +x /tmp/easyaiot-agent-install.sh "
                            + "&& sudo bash /tmp/easyaiot-agent-install.sh",
                    DEPLOY_TIMEOUT_MS);
            NodeMediaRemoteDeployRespVO.DeployStep installStep = new NodeMediaRemoteDeployRespVO.DeployStep();
            installStep.setName("安装启动");
            installStep.setOutput(trimDeployOutput(installResult.combinedOutput(), 8000));
            if (!installResult.isSuccess()) {
                installStep.setStatus("failed");
                steps.add(installStep);
                resp.setSuccess(false);
                resp.setMessage("Agent 安装脚本执行失败");
                return resp;
            }
            installStep.setStatus("success");
            steps.add(installStep);

            NodeMediaRemoteDeployRespVO.DeployStep verifyStep = verifyAgentService(ssh, node);
            steps.add(verifyStep);
            boolean ok = "success".equals(verifyStep.getStatus());
            resp.setSuccess(ok);
            resp.setMessage(ok ? "Agent 部署完成，等待心跳上报" : "服务已安装但验证未通过");
            return resp;
        } catch (Exception e) {
            log.error("Agent SSH 部署失败 nodeId={}", nodeId, e);
            NodeMediaRemoteDeployRespVO.DeployStep fail = new NodeMediaRemoteDeployRespVO.DeployStep();
            fail.setName("部署中断");
            fail.setStatus("failed");
            fail.setOutput(e.getMessage());
            steps.add(fail);
            resp.setSuccess(false);
            resp.setMessage(e.getMessage());
            return resp;
        }
    }

    @Override
    public NodeAgentCheckRespVO checkAgentBySsh(Long nodeId, String controlPlaneUrlOverride) {
        ComputeNodeDO node = validateExists(nodeId);
        NodeSshCredentialDO credential = nodeSshCredentialMapper.selectByNodeId(nodeId);
        if (credential == null) {
            throw exception(SSH_CREDENTIAL_NOT_EXISTS);
        }
        String password = null;
        String privateKey = null;
        if ("password".equals(credential.getAuthType())) {
            password = CredentialEncryptUtil.decrypt(credential.getCredentialEnc());
        } else {
            privateKey = CredentialEncryptUtil.decrypt(credential.getCredentialEnc());
        }

        NodeAgentCheckRespVO resp = new NodeAgentCheckRespVO();
        List<NodeMediaRemoteDeployRespVO.DeployStep> steps = new ArrayList<>();
        resp.setSteps(steps);

        int sshPort = resolveSshPort(node);
        try (SshSessionHelper ssh = SshSessionHelper.connect(
                node.getHost(),
                sshPort,
                credential.getUsername(),
                credential.getAuthType(),
                password,
                privateKey)) {

            steps.add(runDeployStep("SSH 连接", "success", "已连接 " + node.getHost() + ":" + sshPort));

            NodeMediaRemoteDeployRespVO.DeployStep dirStep = probeAgentInstallDir(ssh);
            steps.add(dirStep);
            resp.setInstallDirReady("success".equals(dirStep.getStatus()));

            NodeMediaRemoteDeployRespVO.DeployStep serviceStep = probeAgentServiceStatus(ssh);
            steps.add(serviceStep);
            resp.setServiceRunning("success".equals(serviceStep.getStatus()));

            NodeMediaRemoteDeployRespVO.DeployStep healthStep = probeAgentHealth(ssh, node);
            steps.add(healthStep);
            resp.setHealthOk("success".equals(healthStep.getStatus()));

            String expectedControlPlaneUrl = resolveControlPlaneUrl(controlPlaneUrlOverride);
            resp.setExpectedControlPlaneUrl(expectedControlPlaneUrl);
            NodeMediaRemoteDeployRespVO.DeployStep envStep = probeAgentEnvConfig(ssh, node, expectedControlPlaneUrl);
            steps.add(envStep);
            applyEnvProbeResult(resp, envStep);

            NodeMediaRemoteDeployRespVO.DeployStep cpStep = probeControlPlaneReachability(
                    ssh, StrUtil.blankToDefault(resp.getControlPlaneUrl(), expectedControlPlaneUrl));
            steps.add(cpStep);
            resp.setControlPlaneReachable("success".equals(cpStep.getStatus()));

            NodeMediaRemoteDeployRespVO.DeployStep logStep = probeAgentRecentLogs(ssh);
            steps.add(logStep);

            boolean deployed = Boolean.TRUE.equals(resp.getServiceRunning())
                    && Boolean.TRUE.equals(resp.getHealthOk());
            resp.setDeployed(deployed);
            resp.setSuccess(true);
            resp.setMessage(buildAgentCheckMessage(resp, node));
            return resp;
        } catch (Exception e) {
            log.error("Agent SSH 检测失败 nodeId={} host={}:{}", nodeId, node.getHost(), sshPort, e);
            NodeMediaRemoteDeployRespVO.DeployStep fail = new NodeMediaRemoteDeployRespVO.DeployStep();
            fail.setName(steps.isEmpty() ? "SSH 连接" : "检测中断");
            fail.setStatus("failed");
            String detail = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
            fail.setOutput("连接 " + node.getHost() + ":" + sshPort + " 失败: " + detail);
            steps.add(fail);
            resp.setSuccess(false);
            resp.setDeployed(false);
            resp.setInstallDirReady(false);
            resp.setServiceRunning(false);
            resp.setHealthOk(false);
            resp.setMessage(fail.getOutput());
            return resp;
        }
    }

    @Override
    public NodeMediaRemoteDeployRespVO stopAgentBySsh(Long nodeId) {
        return runAgentRemoteOp(nodeId, "停止 Agent", buildAgentStopScript(), "STOP_OK", "节点代理已停止", "停止 Agent 失败");
    }

    @Override
    public NodeMediaRemoteDeployRespVO removeAgentBySsh(Long nodeId) {
        return runAgentRemoteOp(nodeId, "删除 Agent", buildAgentRemoveScript(), "REMOVE_OK",
                "节点代理已删除", "删除 Agent 失败");
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void setMaintenance(Long id, boolean enabled) {
        ComputeNodeDO node = validateExists(id);
        node.setStatus(enabled ? NodeStatusEnum.MAINTENANCE.getStatus() : NodeStatusEnum.OFFLINE.getStatus());
        computeNodeMapper.updateById(node);
    }

    @Override
    public NodeMetricTrendRespVO getMetricTrend(NodeMetricTrendReqVO reqVO) {
        int minutes = reqVO.getMinutes() != null ? reqVO.getMinutes() : 30;
        int maxPoints = reqVO.getMaxPoints() != null ? reqVO.getMaxPoints() : 120;
        LocalDateTime since = LocalDateTime.now().minusMinutes(minutes);

        List<ComputeNodeDO> targetNodes = resolveTrendTargetNodes(reqVO.getNodeIds());
        if (targetNodes.isEmpty()) {
            NodeMetricTrendRespVO empty = new NodeMetricTrendRespVO();
            empty.setSeries(List.of());
            return empty;
        }

        Map<Long, ComputeNodeDO> nodeMap = targetNodes.stream()
                .collect(Collectors.toMap(ComputeNodeDO::getId, n -> n, (a, b) -> a, LinkedHashMap::new));
        List<Long> nodeIds = new ArrayList<>(nodeMap.keySet());
        List<NodeMetricSnapshotDO> snapshots = nodeMetricSnapshotMapper.selectByNodeIdsSince(nodeIds, since);

        Map<Long, List<NodeMetricSnapshotDO>> grouped = snapshots.stream()
                .collect(Collectors.groupingBy(NodeMetricSnapshotDO::getNodeId));

        List<NodeMetricTrendSeriesRespVO> seriesList = new ArrayList<>();
        for (Long nodeId : nodeIds) {
            ComputeNodeDO node = nodeMap.get(nodeId);
            List<NodeMetricSnapshotDO> nodeSnapshots = grouped.getOrDefault(nodeId, List.of());
            List<NodeMetricSnapshotDO> limited = nodeSnapshots.size() > maxPoints
                    ? nodeSnapshots.subList(nodeSnapshots.size() - maxPoints, nodeSnapshots.size())
                    : nodeSnapshots;

            NodeMetricTrendSeriesRespVO series = new NodeMetricTrendSeriesRespVO();
            series.setNodeId(nodeId);
            series.setNodeName(node.getName());
            series.setHost(node.getHost());
            series.setStatus(node.getStatus());
            series.setPoints(limited.stream().map(this::toTrendPoint).collect(Collectors.toList()));
            seriesList.add(series);
        }

        NodeMetricTrendRespVO resp = new NodeMetricTrendRespVO();
        resp.setSeries(seriesList);
        return resp;
    }

    private List<ComputeNodeDO> resolveTrendTargetNodes(List<Long> nodeIds) {
        if (nodeIds != null && !nodeIds.isEmpty()) {
            return nodeIds.stream()
                    .map(computeNodeMapper::selectById)
                    .filter(Objects::nonNull)
                    .filter(this::isTrendEligibleNode)
                    .sorted(Comparator.comparing(ComputeNodeDO::getName, Comparator.nullsLast(String::compareTo)))
                    .collect(Collectors.toList());
        }
        return computeNodeMapper.selectList(new LambdaQueryWrapperX<ComputeNodeDO>()
                        .in(ComputeNodeDO::getNodeRole,
                                NodeRoleEnum.COMPUTE.getRole(), NodeRoleEnum.HYBRID.getRole())
                        .orderByAsc(ComputeNodeDO::getName))
                .stream()
                .filter(this::isTrendEligibleNode)
                .collect(Collectors.toList());
    }

    private boolean isTrendEligibleNode(ComputeNodeDO node) {
        String role = node.getNodeRole();
        return NodeRoleEnum.COMPUTE.getRole().equals(role) || NodeRoleEnum.HYBRID.getRole().equals(role);
    }

    private NodeMetricTrendPointRespVO toTrendPoint(NodeMetricSnapshotDO snapshot) {
        NodeMetricTrendPointRespVO point = new NodeMetricTrendPointRespVO();
        point.setCollectedAt(snapshot.getCollectedAt());
        point.setCpuPercent(snapshot.getCpuPercent());
        point.setMemPercent(snapshot.getMemPercent());
        point.setDiskPercent(snapshot.getDiskPercent());
        point.setActiveTasks(snapshot.getActiveTasks());
        point.setMemUsedBytes(snapshot.getMemUsedBytes());
        point.setDiskUsedBytes(snapshot.getDiskUsedBytes());
        point.setGpuMemUsedBytes(calcGpuMemUsedBytes(snapshot.getGpuInfo()));
        point.setGpuMemPercent(calcAvgGpuMemPercent(snapshot.getGpuInfo()));
        point.setGpuUtilPercent(calcAvgGpuUtil(snapshot.getGpuInfo()));
        return point;
    }

    private Long calcGpuMemUsedBytes(List<Map<String, Object>> gpuInfo) {
        if (gpuInfo == null || gpuInfo.isEmpty()) {
            return 0L;
        }
        long sum = 0;
        for (Map<String, Object> gpu : gpuInfo) {
            sum += Math.round(toDouble(gpu.get("mem_used_mb")) * 1024 * 1024);
        }
        return sum;
    }

    private BigDecimal calcAvgGpuMemPercent(List<Map<String, Object>> gpuInfo) {
        if (gpuInfo == null || gpuInfo.isEmpty()) {
            return BigDecimal.ZERO;
        }
        double sum = 0;
        int count = 0;
        for (Map<String, Object> gpu : gpuInfo) {
            double total = toDouble(gpu.get("mem_total_mb"));
            if (total <= 0) {
                continue;
            }
            sum += toDouble(gpu.get("mem_used_mb")) / total * 100;
            count++;
        }
        return count > 0 ? scalePercent(sum / count) : BigDecimal.ZERO;
    }

    private BigDecimal calcAvgGpuUtil(List<Map<String, Object>> gpuInfo) {
        if (gpuInfo == null || gpuInfo.isEmpty()) {
            return BigDecimal.ZERO;
        }
        double sum = 0;
        int count = 0;
        for (Map<String, Object> gpu : gpuInfo) {
            sum += toDouble(gpu.get("util"));
            count++;
        }
        return count > 0 ? scalePercent(sum / count) : BigDecimal.ZERO;
    }

    private double toDouble(Object value) {
        if (value == null) {
            return 0;
        }
        if (value instanceof Number) {
            return ((Number) value).doubleValue();
        }
        try {
            return Double.parseDouble(String.valueOf(value));
        } catch (NumberFormatException ignored) {
            return 0;
        }
    }

    private BigDecimal scalePercent(double value) {
        return BigDecimal.valueOf(value).setScale(1, RoundingMode.HALF_UP);
    }

    private NodeMediaRemoteDeployRespVO runAgentRemoteOp(Long nodeId, String stepName, String remoteScript,
                                                         String okMarker, String successMessage, String failMessage) {
        ComputeNodeDO node = validateExists(nodeId);
        NodeSshCredentialDO credential = nodeSshCredentialMapper.selectByNodeId(nodeId);
        if (credential == null) {
            throw exception(SSH_CREDENTIAL_NOT_EXISTS);
        }
        String password = null;
        String privateKey = null;
        if ("password".equals(credential.getAuthType())) {
            password = CredentialEncryptUtil.decrypt(credential.getCredentialEnc());
        } else {
            privateKey = CredentialEncryptUtil.decrypt(credential.getCredentialEnc());
        }

        NodeMediaRemoteDeployRespVO resp = new NodeMediaRemoteDeployRespVO();
        List<NodeMediaRemoteDeployRespVO.DeployStep> steps = new ArrayList<>();
        resp.setSteps(steps);

        int sshPort = resolveSshPort(node);
        try (SshSessionHelper ssh = SshSessionHelper.connect(
                node.getHost(),
                sshPort,
                credential.getUsername(),
                credential.getAuthType(),
                password,
                privateKey)) {

            steps.add(runDeployStep("SSH 连接", "success", "已连接 " + node.getHost() + ":" + sshPort));

            String encoded = Base64.getEncoder().encodeToString(remoteScript.getBytes(StandardCharsets.UTF_8));
            SshSessionHelper.SshExecResult result = ssh.exec(
                    "echo " + encoded + " | base64 -d | sudo bash -s",
                    120000);
            NodeMediaRemoteDeployRespVO.DeployStep step = new NodeMediaRemoteDeployRespVO.DeployStep();
            step.setName(stepName);
            step.setOutput(trimDeployOutput(result.combinedOutput(), 6000));
            boolean ok = result.isSuccess() && result.combinedOutput().contains(okMarker);
            step.setStatus(ok ? "success" : "failed");
            steps.add(step);
            resp.setSuccess(ok);
            resp.setMessage(ok ? successMessage : failMessage);
            return resp;
        } catch (Exception e) {
            log.error("Agent SSH 运维失败 nodeId={} op={}", nodeId, stepName, e);
            NodeMediaRemoteDeployRespVO.DeployStep fail = new NodeMediaRemoteDeployRespVO.DeployStep();
            fail.setName(steps.isEmpty() ? "SSH 连接" : stepName);
            fail.setStatus("failed");
            String detail = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
            fail.setOutput("连接 " + node.getHost() + ":" + sshPort + " 失败: " + detail);
            steps.add(fail);
            resp.setSuccess(false);
            resp.setMessage(fail.getOutput());
            return resp;
        }
    }

    private String buildAgentStopScript() {
        return "#!/usr/bin/env bash\n"
                + "set -euo pipefail\n"
                + "if systemctl list-unit-files easyaiot-node-agent.service >/dev/null 2>&1; then\n"
                + "  echo '>>> 停止 easyaiot-node-agent'\n"
                + "  systemctl stop easyaiot-node-agent 2>&1 || true\n"
                + "  echo '[OK] 服务已停止'\n"
                + "else\n"
                + "  echo '[SKIP] 服务未注册'\n"
                + "fi\n"
                + "echo STOP_OK\n";
    }

    private String buildAgentRemoveScript() {
        String dir = AgentDeployUtil.REMOTE_INSTALL_DIR;
        return "#!/usr/bin/env bash\n"
                + "set -euo pipefail\n"
                + "if systemctl list-unit-files easyaiot-node-agent.service >/dev/null 2>&1; then\n"
                + "  echo '>>> 停止并禁用 easyaiot-node-agent'\n"
                + "  systemctl stop easyaiot-node-agent 2>&1 || true\n"
                + "  systemctl disable easyaiot-node-agent 2>&1 || true\n"
                + "fi\n"
                + "if [[ -f /etc/systemd/system/easyaiot-node-agent.service ]]; then\n"
                + "  echo '>>> 删除 systemd 单元文件'\n"
                + "  rm -f /etc/systemd/system/easyaiot-node-agent.service\n"
                + "  systemctl daemon-reload\n"
                + "  echo '[OK] systemd 单元已移除'\n"
                + "else\n"
                + "  echo '[SKIP] systemd 单元不存在'\n"
                + "fi\n"
                + "if [[ -d '" + dir + "' ]]; then\n"
                + "  echo '>>> 删除安装目录 " + dir + "'\n"
                + "  rm -rf '" + dir + "'\n"
                + "  echo '[OK] 安装目录已删除'\n"
                + "else\n"
                + "  echo '[SKIP] 安装目录不存在'\n"
                + "fi\n"
                + "echo REMOVE_OK\n";
    }

    private void saveSshCredential(Long nodeId, ComputeNodeSaveReqVO reqVO) {
        if (StrUtil.isBlank(reqVO.getSshUsername())) {
            return;
        }
        String secret = "password".equals(reqVO.getSshAuthType()) ? reqVO.getSshPassword() : reqVO.getSshPrivateKey();
        if (StrUtil.isBlank(secret)) {
            return;
        }
        NodeSshCredentialDO credential = nodeSshCredentialMapper.selectByNodeId(nodeId);
        if (credential == null) {
            credential = new NodeSshCredentialDO();
            credential.setNodeId(nodeId);
        }
        credential.setUsername(reqVO.getSshUsername());
        credential.setAuthType(StrUtil.blankToDefault(reqVO.getSshAuthType(), "password"));
        credential.setCredentialEnc(CredentialEncryptUtil.encrypt(secret));
        if (credential.getId() == null) {
            nodeSshCredentialMapper.insert(credential);
        } else {
            nodeSshCredentialMapper.updateById(credential);
        }
    }

    private ComputeNodeDO validateExists(Long id) {
        ComputeNodeDO node = computeNodeMapper.selectById(id);
        if (node == null) {
            throw exception(COMPUTE_NODE_NOT_EXISTS);
        }
        return node;
    }

    private ComputeNodeRespVO toRespVO(ComputeNodeDO node, boolean exposeToken) {
        ComputeNodeRespVO resp = BeanUtils.toBean(node, ComputeNodeRespVO.class);
        if (exposeToken) {
            resp.setAgentToken(node.getAgentToken());
        }
        NodeSshCredentialDO credential = nodeSshCredentialMapper.selectByNodeId(node.getId());
        if (credential != null) {
            resp.setSshUsername(credential.getUsername());
            resp.setSshAuthType(credential.getAuthType());
            resp.setSshCredentialConfigured(true);
            resp.setSshLastTestAt(credential.getLastTestAt());
            resp.setSshLastTestOk(credential.getLastTestOk());
        } else {
            resp.setSshCredentialConfigured(false);
        }
        NodeMetricSnapshotDO metric = nodeMetricSnapshotMapper.selectLatestByNodeId(node.getId());
        if (metric != null) {
            resp.setCpuPercent(metric.getCpuPercent());
            resp.setMemPercent(metric.getMemPercent());
            resp.setMemUsedBytes(metric.getMemUsedBytes());
            resp.setMemTotalBytes(metric.getMemTotalBytes());
            resp.setDiskPercent(metric.getDiskPercent());
            resp.setDiskUsedBytes(metric.getDiskUsedBytes());
            resp.setDiskTotalBytes(metric.getDiskTotalBytes());
            resp.setActiveTasks(metric.getActiveTasks());
            try {
                resp.setGpuInfo(metric.getGpuInfo() != null ? objectMapper.writeValueAsString(metric.getGpuInfo()) : null);
            } catch (Exception ignored) {
                resp.setGpuInfo(null);
            }
        }
        return resp;
    }

    private int defaultPort(Integer port, int defaultValue) {
        return port != null && port > 0 ? port : defaultValue;
    }

    static int resolveSshPort(ComputeNodeDO node) {
        return node.getSshPort() != null && node.getSshPort() > 0 ? node.getSshPort() : DEFAULT_SSH_PORT;
    }

    private Map<String, Boolean> defaultCapabilities(String nodeRole) {
        Map<String, Boolean> caps = new HashMap<>();
        if (NodeRoleEnum.COMPUTE.getRole().equals(nodeRole) || NodeRoleEnum.HYBRID.getRole().equals(nodeRole)) {
            caps.put("ai_inference", true);
            caps.put("algorithm_realtime", true);
            caps.put("algorithm_snap", true);
            caps.put("stream_forward", true);
        }
        if (NodeRoleEnum.MEDIA.getRole().equals(nodeRole) || NodeRoleEnum.HYBRID.getRole().equals(nodeRole)) {
            caps.put("srs_live", true);
            caps.put("srs_ai", true);
            caps.put("zlm", true);
        }
        return caps;
    }

    private String resolveControlPlaneUrl(String override) {
        if (override != null && !override.isBlank()) {
            return override.trim().replaceAll("/+$", "");
        }
        if (agentControlPlaneUrl != null && !agentControlPlaneUrl.isBlank()) {
            return agentControlPlaneUrl.trim().replaceAll("/+$", "");
        }
        String fallback = "http://" + mediaHookHost + ":" + mediaHookPort + "/admin-api/node/agent";
        if ("127.0.0.1".equals(mediaHookHost) || "localhost".equalsIgnoreCase(mediaHookHost)) {
            log.warn("未配置 easyaiot.agent.control-plane-url，远程 Agent 将无法访问 {}，"
                    + "请设置 EASYAIOT_AGENT_CONTROL_PLANE_URL 或在部署时传入平台接入地址", fallback);
        }
        return fallback;
    }

    private String resolveAgentSource() {
        if (agentSourcePath != null && !agentSourcePath.isBlank()) {
            File dir = new File(agentSourcePath);
            if (dir.isDirectory()) {
                return dir.getAbsolutePath();
            }
        }
        String[] candidates = {
                "/opt/easyaiot/NODE/agent",
                System.getProperty("user.dir") + "/NODE/agent",
                System.getProperty("user.dir") + "/../NODE/agent",
        };
        for (String path : candidates) {
            File entry = new File(path, "run_agent.py");
            if (entry.isFile()) {
                return new File(path).getAbsolutePath();
            }
        }
        throw exception(AGENT_SOURCE_NOT_FOUND);
    }

    private NodeMediaRemoteDeployRespVO.DeployStep syncAgentFiles(SshSessionHelper ssh, String sourceRoot)
            throws Exception {
        ssh.ensureRemoteDir(AgentDeployUtil.REMOTE_INSTALL_DIR);
        int count = 0;
        for (String relative : AgentDeployUtil.SYNC_RELATIVE_FILES) {
            File local = new File(sourceRoot, relative);
            if (!local.isFile()) {
                throw exception(AGENT_SOURCE_NOT_FOUND);
            }
            ssh.uploadFile(local.getAbsolutePath(), AgentDeployUtil.REMOTE_INSTALL_DIR + "/" + relative);
            if (relative.endsWith(".sh")) {
                ssh.exec("chmod +x " + AgentDeployUtil.REMOTE_INSTALL_DIR + "/" + relative, 10000);
            }
            count++;
        }
        return runDeployStep("同步文件", "success", "已上传 " + count + " 个文件至 " + AgentDeployUtil.REMOTE_INSTALL_DIR);
    }

    private NodeMediaRemoteDeployRespVO.DeployStep probeAgentInstallDir(SshSessionHelper ssh) throws Exception {
        String dir = AgentDeployUtil.REMOTE_INSTALL_DIR;
        SshSessionHelper.SshExecResult result = ssh.exec(
                "if [ -d '" + dir + "' ] && [ -f '" + dir + "/run_agent.py' ]; then echo DIR_OK; "
                        + "ls -la '" + dir + "' 2>/dev/null | head -5; "
                        + "elif [ -d '" + dir + "' ]; then echo DIR_PARTIAL; "
                        + "else echo DIR_MISSING; fi",
                15000);
        String out = result.combinedOutput();
        NodeMediaRemoteDeployRespVO.DeployStep step = new NodeMediaRemoteDeployRespVO.DeployStep();
        step.setName("安装目录");
        step.setOutput(trimDeployOutput(out, 2000));
        if (out.contains("DIR_OK")) {
            step.setStatus("success");
            return step;
        }
        if (out.contains("DIR_PARTIAL")) {
            step.setStatus("failed");
            step.setOutput("目录 " + dir + " 存在但缺少 run_agent.py，可能安装不完整");
            return step;
        }
        step.setStatus("failed");
        step.setOutput("未找到安装目录 " + dir);
        return step;
    }

    private NodeMediaRemoteDeployRespVO.DeployStep probeAgentServiceStatus(SshSessionHelper ssh) throws Exception {
        SshSessionHelper.SshExecResult result = ssh.exec(
                "if systemctl is-active easyaiot-node-agent >/dev/null 2>&1; then echo SERVICE_ACTIVE; "
                        + "systemctl status easyaiot-node-agent --no-pager -l 2>/dev/null | head -3; "
                        + "elif systemctl list-unit-files easyaiot-node-agent.service >/dev/null 2>&1; then echo SERVICE_INACTIVE; "
                        + "else echo SERVICE_MISSING; fi",
                15000);
        String out = result.combinedOutput();
        NodeMediaRemoteDeployRespVO.DeployStep step = new NodeMediaRemoteDeployRespVO.DeployStep();
        step.setName("systemd 服务");
        step.setOutput(trimDeployOutput(out, 2000));
        if (out.contains("SERVICE_ACTIVE")) {
            step.setStatus("success");
            return step;
        }
        if (out.contains("SERVICE_INACTIVE")) {
            step.setStatus("failed");
            step.setOutput("已注册 easyaiot-node-agent 服务但未运行");
            return step;
        }
        step.setStatus("failed");
        step.setOutput("未注册 easyaiot-node-agent 服务");
        return step;
    }

    private NodeMediaRemoteDeployRespVO.DeployStep probeAgentHealth(SshSessionHelper ssh, ComputeNodeDO node)
            throws Exception {
        int agentPort = node.getAgentPort() != null && node.getAgentPort() > 0
                ? node.getAgentPort() : DEFAULT_AGENT_PORT;
        SshSessionHelper.SshExecResult result = ssh.exec(
                "if curl -sf http://127.0.0.1:" + agentPort + "/health >/dev/null 2>&1; then echo HEALTH_OK; "
                        + "else echo HEALTH_FAIL; fi",
                15000);
        String out = result.combinedOutput();
        NodeMediaRemoteDeployRespVO.DeployStep step = new NodeMediaRemoteDeployRespVO.DeployStep();
        step.setName("健康检查");
        if (out.contains("HEALTH_OK")) {
            step.setStatus("success");
            step.setOutput("http://127.0.0.1:" + agentPort + "/health 响应正常");
            return step;
        }
        step.setStatus("failed");
        step.setOutput("http://127.0.0.1:" + agentPort + "/health 无响应");
        return step;
    }

    private String buildAgentCheckMessage(NodeAgentCheckRespVO resp, ComputeNodeDO node) {
        int agentPort = node.getAgentPort() != null && node.getAgentPort() > 0
                ? node.getAgentPort() : DEFAULT_AGENT_PORT;
        if (Boolean.TRUE.equals(resp.getDeployed()) && Boolean.TRUE.equals(resp.getConfigOk())
                && Boolean.TRUE.equals(resp.getControlPlaneReachable())) {
            return "节点代理已部署：服务运行正常，接入配置正确，控制面可达（端口 " + agentPort + "）";
        }
        if (Boolean.TRUE.equals(resp.getDeployed())) {
            StringBuilder sb = new StringBuilder("节点代理已部署且服务正常，但");
            if (Boolean.FALSE.equals(resp.getConfigOk())) {
                sb.append(" agent.env 与平台不一致");
            } else if (Boolean.FALSE.equals(resp.getControlPlaneReachable())) {
                sb.append(" 目标机无法访问控制面地址");
            } else {
                sb.append(" 心跳尚未上报");
            }
            sb.append("，请按下方诊断项排查");
            return sb.toString();
        }
        if (Boolean.TRUE.equals(resp.getServiceRunning()) || Boolean.TRUE.equals(resp.getHealthOk())) {
            StringBuilder sb = new StringBuilder("节点代理部分可用：");
            if (Boolean.TRUE.equals(resp.getServiceRunning())) {
                sb.append("systemd 服务已运行");
            }
            if (Boolean.TRUE.equals(resp.getHealthOk())) {
                if (Boolean.TRUE.equals(resp.getServiceRunning())) {
                    sb.append("，");
                }
                sb.append("健康检查通过");
            }
            sb.append("。建议重新部署或排查服务状态");
            return sb.toString();
        }
        if (Boolean.TRUE.equals(resp.getInstallDirReady())) {
            return "检测到安装目录 " + AgentDeployUtil.REMOTE_INSTALL_DIR
                    + "，但服务未运行，可尝试重新部署";
        }
        return "未检测到节点代理，可进行全新部署";
    }

    private void applyEnvProbeResult(NodeAgentCheckRespVO resp, NodeMediaRemoteDeployRespVO.DeployStep envStep) {
        String out = envStep.getOutput() != null ? envStep.getOutput() : "";
        resp.setNodeIdMatch(out.contains("NODE_ID_MATCH=1"));
        resp.setTokenMatch(out.contains("TOKEN_MATCH=1"));
        resp.setConfigOk(Boolean.TRUE.equals(resp.getNodeIdMatch()) && Boolean.TRUE.equals(resp.getTokenMatch()));
        int cpIdx = out.indexOf("CP_URL=");
        if (cpIdx >= 0) {
            String line = out.substring(cpIdx);
            int end = line.indexOf('\n');
            resp.setControlPlaneUrl(line.substring("CP_URL=".length(), end >= 0 ? end : line.length()).trim());
        }
    }

    private NodeMediaRemoteDeployRespVO.DeployStep probeAgentEnvConfig(
            SshSessionHelper ssh, ComputeNodeDO node, String expectedControlPlaneUrl) throws Exception {
        String envFile = AgentDeployUtil.REMOTE_INSTALL_DIR + "/agent.env";
        String expectedNodeId = String.valueOf(node.getId());
        String expectedToken = node.getAgentToken() != null ? node.getAgentToken() : "";
        String script = "ENV_FILE='" + envFile + "'\n"
                + "EXPECTED_NODE_ID='" + expectedNodeId + "'\n"
                + "EXPECTED_TOKEN='" + expectedToken.replace("'", "'\\''") + "'\n"
                + "EXPECTED_CP='" + expectedControlPlaneUrl.replace("'", "'\\''") + "'\n"
                + "if [ ! -f \"$ENV_FILE\" ]; then echo ENV_MISSING; exit 0; fi\n"
                + "NODE_ID=$(grep '^NODE_ID=' \"$ENV_FILE\" | head -1 | cut -d= -f2-)\n"
                + "TOKEN=$(grep '^AGENT_TOKEN=' \"$ENV_FILE\" | head -1 | cut -d= -f2-)\n"
                + "CP_URL=$(grep '^CONTROL_PLANE_URL=' \"$ENV_FILE\" | head -1 | cut -d= -f2-)\n"
                + "echo NODE_ID=$NODE_ID\n"
                + "echo CP_URL=$CP_URL\n"
                + "echo EXPECTED_CP=$EXPECTED_CP\n"
                + "if [ \"$NODE_ID\" = \"$EXPECTED_NODE_ID\" ]; then echo NODE_ID_MATCH=1; else echo NODE_ID_MATCH=0; fi\n"
                + "if [ \"$TOKEN\" = \"$EXPECTED_TOKEN\" ]; then echo TOKEN_MATCH=1; else echo TOKEN_MATCH=0; fi\n"
                + "if [ \"$CP_URL\" = \"$EXPECTED_CP\" ]; then echo CP_MATCH=1; else echo CP_MATCH=0; fi\n"
                + "if echo \"$CP_URL\" | grep -Eq 'localhost|127\\.0\\.0\\.1'; then echo CP_LOCALHOST=1; fi";
        SshSessionHelper.SshExecResult result = ssh.exec(script, 15000);
        String out = result.combinedOutput();
        NodeMediaRemoteDeployRespVO.DeployStep step = new NodeMediaRemoteDeployRespVO.DeployStep();
        step.setName("接入配置");
        step.setOutput(trimDeployOutput(out, 3000));
        if (out.contains("ENV_MISSING")) {
            step.setStatus("failed");
            step.setOutput("未找到 " + envFile);
            return step;
        }
        boolean idOk = out.contains("NODE_ID_MATCH=1");
        boolean tokenOk = out.contains("TOKEN_MATCH=1");
        boolean cpOk = out.contains("CP_MATCH=1");
        boolean localhost = out.contains("CP_LOCALHOST=1");
        if (idOk && tokenOk && cpOk && !localhost) {
            step.setStatus("success");
        } else if (idOk && tokenOk) {
            step.setStatus(localhost ? "failed" : "success");
            if (localhost) {
                step.setOutput(step.getOutput() + "\nCONTROL_PLANE_URL 使用了 localhost/127.0.0.1，远程 Agent 无法访问平台");
            } else if (!cpOk) {
                step.setOutput(step.getOutput() + "\nCONTROL_PLANE_URL 与平台期望不一致，请更新 agent.env 并重启服务");
            }
        } else {
            step.setStatus("failed");
            if (!idOk) {
                step.setOutput(step.getOutput() + "\nNODE_ID 与平台不一致");
            }
            if (!tokenOk) {
                step.setOutput(step.getOutput() + "\nAGENT_TOKEN 与平台不一致（若重置过令牌需同步 agent.env）");
            }
        }
        return step;
    }

    private NodeMediaRemoteDeployRespVO.DeployStep probeControlPlaneReachability(
            SshSessionHelper ssh, String controlPlaneUrl) throws Exception {
        NodeMediaRemoteDeployRespVO.DeployStep step = new NodeMediaRemoteDeployRespVO.DeployStep();
        step.setName("控制面连通");
        if (controlPlaneUrl == null || controlPlaneUrl.isBlank()) {
            step.setStatus("failed");
            step.setOutput("未读取到 CONTROL_PLANE_URL，无法探测");
            return step;
        }
        String safeUrl = controlPlaneUrl.trim().replaceAll("/+$", "").replace("'", "'\\''");
        String script = "CP='" + safeUrl + "'\n"
                + "CODE=$(curl -s -o /dev/null -w '%{http_code}' --connect-timeout 5 "
                + "-X POST \"$CP/register\" -H 'Content-Type: application/json' "
                + "-d '{\"nodeId\":0,\"agentToken\":\"probe\"}' 2>/dev/null || echo 000)\n"
                + "echo HTTP_CODE=$CODE\n"
                + "echo TARGET=$CP/register";
        SshSessionHelper.SshExecResult result = ssh.exec(script, 20000);
        String out = result.combinedOutput();
        step.setOutput(trimDeployOutput(out, 2000));
        if (out.contains("HTTP_CODE=000")) {
            step.setStatus("failed");
            step.setOutput(step.getOutput() + "\n目标机无法连接控制面，请检查网络、防火墙及 CONTROL_PLANE_URL 是否使用平台可达 IP");
            return step;
        }
        step.setStatus("success");
        return step;
    }

    private NodeMediaRemoteDeployRespVO.DeployStep probeAgentRecentLogs(SshSessionHelper ssh) throws Exception {
        SshSessionHelper.SshExecResult result = ssh.exec(
                "journalctl -u easyaiot-node-agent -n 12 --no-pager 2>/dev/null | tail -8 || "
                        + "echo '无法读取 systemd 日志（服务可能未安装）'",
                15000);
        NodeMediaRemoteDeployRespVO.DeployStep step = new NodeMediaRemoteDeployRespVO.DeployStep();
        step.setName("Agent 日志");
        step.setOutput(trimDeployOutput(result.combinedOutput(), 4000));
        step.setStatus(result.combinedOutput().contains("注册成功") ? "success"
                : result.combinedOutput().contains("注册失败") || result.combinedOutput().contains("请求异常")
                ? "failed" : "success");
        return step;
    }

    private NodeMediaRemoteDeployRespVO.DeployStep verifyAgentService(SshSessionHelper ssh, ComputeNodeDO node)
            throws Exception {
        int agentPort = node.getAgentPort() != null && node.getAgentPort() > 0
                ? node.getAgentPort() : DEFAULT_AGENT_PORT;
        SshSessionHelper.SshExecResult serviceResult = ssh.exec(
                "systemctl is-active easyaiot-node-agent 2>/dev/null || echo INACTIVE",
                15000);
        SshSessionHelper.SshExecResult healthResult = ssh.exec(
                "curl -sf http://127.0.0.1:" + agentPort + "/health >/dev/null && echo AGENT_OK || echo AGENT_FAIL",
                15000);
        String out = serviceResult.combinedOutput() + " " + healthResult.combinedOutput();
        NodeMediaRemoteDeployRespVO.DeployStep step = new NodeMediaRemoteDeployRespVO.DeployStep();
        step.setName("服务验证");
        step.setOutput(out.trim());
        boolean serviceActive = out.contains("active");
        boolean healthOk = out.contains("AGENT_OK");
        if (serviceActive && healthOk) {
            step.setStatus("success");
        } else if (serviceActive || healthOk) {
            step.setStatus("success");
            step.setOutput(out.trim() + "（部分检查通过，请稍后确认心跳）");
        } else {
            step.setStatus("failed");
        }
        return step;
    }

    private NodeMediaRemoteDeployRespVO.DeployStep runDeployStep(String name, String status, String output) {
        NodeMediaRemoteDeployRespVO.DeployStep step = new NodeMediaRemoteDeployRespVO.DeployStep();
        step.setName(name);
        step.setStatus(status);
        step.setOutput(output);
        return step;
    }

    private String trimDeployOutput(String text, int maxLen) {
        if (text == null) {
            return "";
        }
        String trimmed = text.trim();
        if (trimmed.length() <= maxLen) {
            return trimmed;
        }
        return trimmed.substring(0, maxLen) + "\n... (输出已截断)";
    }

}
