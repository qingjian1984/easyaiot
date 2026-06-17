package com.basiclab.iot.node.service.impl;

import com.basiclab.iot.node.dal.dataobject.ComputeNodeDO;
import com.basiclab.iot.node.dal.dataobject.NodeSshCredentialDO;
import com.basiclab.iot.node.dal.pgsql.ComputeNodeMapper;
import com.basiclab.iot.node.dal.pgsql.NodeSshCredentialMapper;
import com.basiclab.iot.node.domain.vo.NodeMediaRemoteDeployRespVO;
import com.basiclab.iot.node.domain.vo.NodeStorageMountCheckRespVO;
import com.basiclab.iot.node.domain.vo.NodeStorageStackCheckRespVO;
import com.basiclab.iot.node.service.NodeStorageService;
import com.basiclab.iot.node.util.CredentialEncryptUtil;
import com.basiclab.iot.node.util.SshSessionHelper;
import com.basiclab.iot.node.util.StorageStackDeployUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.validation.annotation.Validated;

import javax.annotation.Resource;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

import static com.basiclab.iot.common.exception.util.ServiceExceptionUtil.exception;
import static com.basiclab.iot.node.enums.ErrorCodeConstants.COMPUTE_NODE_NOT_EXISTS;
import static com.basiclab.iot.node.enums.ErrorCodeConstants.SSH_CREDENTIAL_NOT_EXISTS;
import static com.basiclab.iot.node.enums.ErrorCodeConstants.STORAGE_CLUSTER_SOURCE_NOT_FOUND;
import static com.basiclab.iot.node.enums.ErrorCodeConstants.STORAGE_NODE_ROLE_INVALID;

@Slf4j
@Service
@Validated
public class NodeStorageServiceImpl implements NodeStorageService {

    private static final int DEPLOY_TIMEOUT_MS = 900000;
    private static final int CHECK_TIMEOUT_MS = 120000;
    private static final int OPS_TIMEOUT_MS = 180000;
    private static final String[] SYNC_RELATIVE_FILES = {
            "pool-create.sh",
            "mount-all.sh",
            "check_ceph_health.sh",
            "install_ceph_client.sh",
            "install_ceph_osd.sh",
    };

    @Resource
    private ComputeNodeMapper computeNodeMapper;
    @Resource
    private NodeSshCredentialMapper nodeSshCredentialMapper;

    @Value("${easyaiot.storage.cluster-source-path:}")
    private String storageClusterSourcePath;

    @Override
    public NodeStorageStackCheckRespVO checkStorageStackBySsh(Long nodeId) {
        ComputeNodeDO node = requireNode(nodeId);
        if (!StorageStackDeployUtil.isStorageRole(node.getNodeRole())) {
            throw exception(STORAGE_NODE_ROLE_INVALID);
        }
        return runHealthCheck(node);
    }

    @Override
    public NodeStorageMountCheckRespVO checkStorageMountBySsh(Long nodeId) {
        ComputeNodeDO node = requireNode(nodeId);
        if (!StorageStackDeployUtil.isClientMountRole(node.getNodeRole())) {
            throw exception(STORAGE_NODE_ROLE_INVALID);
        }
        NodeSshCredential credential = loadSshCredential(nodeId);
        int sshPort = ComputeNodeServiceImpl.resolveSshPort(node);

        NodeStorageMountCheckRespVO resp = new NodeStorageMountCheckRespVO();
        List<NodeMediaRemoteDeployRespVO.DeployStep> steps = new ArrayList<>();
        resp.setSteps(steps);

        try (SshSessionHelper ssh = openSshSession(node, credential, sshPort)) {
            steps.add(runStep("SSH 连接", "success", "已连接 " + node.getHost() + ":" + sshPort));
            HealthProbe probe = probeHealth(ssh, node);
            steps.add(probe.mountStep);
            resp.setMountReady(probe.mountReady);
            resp.setSuccess(true);
            resp.setMessage(buildMountCheckMessage(probe, node));
            return resp;
        } catch (Exception e) {
            return buildMountCheckFailure(resp, steps, node, sshPort, e);
        }
    }

    @Override
    public NodeMediaRemoteDeployRespVO deployStorageOsdBySsh(Long nodeId) {
        ComputeNodeDO node = requireNode(nodeId);
        if (!StorageStackDeployUtil.isStorageRole(node.getNodeRole())) {
            throw exception(STORAGE_NODE_ROLE_INVALID);
        }
        return deployWithScript(node, "OSD 节点准备", StorageStackDeployUtil.buildOsdInstallScript(node), "OSD_PREPARE_OK");
    }

    @Override
    public NodeMediaRemoteDeployRespVO deployStorageClientBySsh(Long nodeId) {
        ComputeNodeDO node = requireNode(nodeId);
        if (!StorageStackDeployUtil.isClientMountRole(node.getNodeRole())) {
            throw exception(STORAGE_NODE_ROLE_INVALID);
        }
        return deployWithScript(node, "CephFS 客户端挂载", StorageStackDeployUtil.buildClientInstallScript(node), "CLIENT_MOUNT_OK");
    }

    @Override
    public NodeMediaRemoteDeployRespVO deployStoragePoolBySsh(Long nodeId) {
        ComputeNodeDO node = requireNode(nodeId);
        if (!StorageStackDeployUtil.isStorageRole(node.getNodeRole())) {
            throw exception(STORAGE_NODE_ROLE_INVALID);
        }
        return deployWithScript(node, "创建 Ceph 存储池", StorageStackDeployUtil.buildPoolCreateScript(node), "Done.");
    }

    @Override
    public NodeMediaRemoteDeployRespVO stopStorageOsdBySsh(Long nodeId) {
        ComputeNodeDO node = requireNode(nodeId);
        if (!StorageStackDeployUtil.isStorageRole(node.getNodeRole())) {
            throw exception(STORAGE_NODE_ROLE_INVALID);
        }
        NodeSshCredential credential = loadSshCredential(nodeId);
        int sshPort = ComputeNodeServiceImpl.resolveSshPort(node);
        NodeMediaRemoteDeployRespVO resp = new NodeMediaRemoteDeployRespVO();
        List<NodeMediaRemoteDeployRespVO.DeployStep> steps = new ArrayList<>();
        resp.setSteps(steps);
        try (SshSessionHelper ssh = openSshSession(node, credential, sshPort)) {
            steps.add(runStep("SSH 连接", "success", "已连接 " + node.getHost() + ":" + sshPort));
            String script = "#!/usr/bin/env bash\nset -euo pipefail\n"
                    + "if command -v systemctl >/dev/null 2>&1; then\n"
                    + "  for u in $(systemctl list-units 'ceph-osd@*' --no-legend 2>/dev/null | awk '{print $1}'); do\n"
                    + "    systemctl stop \"$u\" 2>/dev/null || true\n"
                    + "  done\n"
                    + "fi\n"
                    + "echo STOP_OSD_OK\n";
            SshSessionHelper.SshExecResult result = execRemoteScript(ssh, script, OPS_TIMEOUT_MS);
            NodeMediaRemoteDeployRespVO.DeployStep step = new NodeMediaRemoteDeployRespVO.DeployStep();
            step.setName("停止 OSD 服务");
            step.setOutput(trimOutput(result.combinedOutput(), 4000));
            boolean ok = result.isSuccess() && result.combinedOutput().contains("STOP_OSD_OK");
            step.setStatus(ok ? "success" : "failed");
            steps.add(step);
            resp.setSuccess(ok);
            resp.setMessage(ok ? "本节点 OSD 服务已停止" : "停止 OSD 失败");
            return resp;
        } catch (Exception e) {
            return buildDeployFailure(resp, steps, node, sshPort, "停止 OSD", e);
        }
    }

    @Override
    public NodeMediaRemoteDeployRespVO unmountStorageBySsh(Long nodeId) {
        ComputeNodeDO node = requireNode(nodeId);
        if (!StorageStackDeployUtil.isClientMountRole(node.getNodeRole())) {
            throw exception(STORAGE_NODE_ROLE_INVALID);
        }
        String mountRoot = StorageStackDeployUtil.buildDeployEnvMap(node).get("MOUNT_ROOT");
        NodeSshCredential credential = loadSshCredential(nodeId);
        int sshPort = ComputeNodeServiceImpl.resolveSshPort(node);
        NodeMediaRemoteDeployRespVO resp = new NodeMediaRemoteDeployRespVO();
        List<NodeMediaRemoteDeployRespVO.DeployStep> steps = new ArrayList<>();
        resp.setSteps(steps);
        try (SshSessionHelper ssh = openSshSession(node, credential, sshPort)) {
            steps.add(runStep("SSH 连接", "success", "已连接 " + node.getHost() + ":" + sshPort));
            String script = "#!/usr/bin/env bash\nset -euo pipefail\n"
                    + "MOUNT_ROOT=\"" + mountRoot.replace("\"", "") + "\"\n"
                    + "if mountpoint -q \"${MOUNT_ROOT}\" 2>/dev/null; then umount \"${MOUNT_ROOT}\" || true; fi\n"
                    + "echo UNMOUNT_OK\n";
            SshSessionHelper.SshExecResult result = execRemoteScript(ssh, script, OPS_TIMEOUT_MS);
            NodeMediaRemoteDeployRespVO.DeployStep step = new NodeMediaRemoteDeployRespVO.DeployStep();
            step.setName("卸载 CephFS");
            step.setOutput(trimOutput(result.combinedOutput(), 4000));
            boolean ok = result.isSuccess() && result.combinedOutput().contains("UNMOUNT_OK");
            step.setStatus(ok ? "success" : "failed");
            steps.add(step);
            resp.setSuccess(ok);
            resp.setMessage(ok ? "CephFS 已卸载" : "卸载 CephFS 失败");
            return resp;
        } catch (Exception e) {
            return buildDeployFailure(resp, steps, node, sshPort, "卸载 CephFS", e);
        }
    }

    private NodeStorageStackCheckRespVO runHealthCheck(ComputeNodeDO node) {
        NodeSshCredential credential = loadSshCredential(node.getId());
        int sshPort = ComputeNodeServiceImpl.resolveSshPort(node);
        NodeStorageStackCheckRespVO resp = new NodeStorageStackCheckRespVO();
        List<NodeMediaRemoteDeployRespVO.DeployStep> steps = new ArrayList<>();
        resp.setSteps(steps);

        try (SshSessionHelper ssh = openSshSession(node, credential, sshPort)) {
            steps.add(runStep("SSH 连接", "success", "已连接 " + node.getHost() + ":" + sshPort));
            HealthProbe probe = probeHealth(ssh, node);
            steps.add(probe.cephCliStep);
            steps.add(probe.cephHealthStep);
            steps.add(probe.osdStep);
            steps.add(probe.poolStep);
            steps.add(probe.cephfsStep);
            steps.add(probe.mountStep);

            resp.setCephHealthy(probe.cephHealthy);
            resp.setOsdRunning(probe.osdRunning);
            resp.setPoolExists(probe.poolExists);
            resp.setCephfsReady(probe.cephfsReady);
            resp.setMountReady(probe.mountReady);
            boolean deployed = Boolean.TRUE.equals(probe.cephHealthy)
                    && Boolean.TRUE.equals(probe.osdRunning)
                    && Boolean.TRUE.equals(probe.mountReady);
            resp.setDeployed(deployed);
            resp.setSuccess(true);
            resp.setMessage(buildStackCheckMessage(resp, node));
            return resp;
        } catch (Exception e) {
            log.error("Ceph SSH 检测失败 nodeId={} host={}:{}", node.getId(), node.getHost(), sshPort, e);
            NodeMediaRemoteDeployRespVO.DeployStep fail = new NodeMediaRemoteDeployRespVO.DeployStep();
            fail.setName(steps.isEmpty() ? "SSH 连接" : "检测中断");
            fail.setStatus("failed");
            String detail = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
            fail.setOutput("连接 " + node.getHost() + ":" + sshPort + " 失败: " + detail);
            steps.add(fail);
            resp.setSuccess(false);
            resp.setDeployed(false);
            resp.setMessage(fail.getOutput());
            return resp;
        }
    }

    private NodeMediaRemoteDeployRespVO deployWithScript(
            ComputeNodeDO node, String phaseName, String scriptBody, String successToken) {
        NodeSshCredential credential = loadSshCredential(node.getId());
        int sshPort = ComputeNodeServiceImpl.resolveSshPort(node);
        String sourceRoot = resolveStorageClusterSource();

        NodeMediaRemoteDeployRespVO resp = new NodeMediaRemoteDeployRespVO();
        List<NodeMediaRemoteDeployRespVO.DeployStep> steps = new ArrayList<>();
        resp.setSteps(steps);

        try (SshSessionHelper ssh = openSshSession(node, credential, sshPort)) {
            steps.add(runStep("SSH 连接", "success", "已连接 " + node.getHost() + ":" + sshPort));
            steps.add(syncStorageCluster(ssh, sourceRoot));
            SshSessionHelper.SshExecResult result = execRemoteScript(ssh, scriptBody, DEPLOY_TIMEOUT_MS);
            NodeMediaRemoteDeployRespVO.DeployStep deployStep = new NodeMediaRemoteDeployRespVO.DeployStep();
            deployStep.setName(phaseName);
            deployStep.setOutput(trimOutput(result.combinedOutput(), 8000));
            boolean ok = result.isSuccess() && result.combinedOutput().contains(successToken);
            deployStep.setStatus(ok ? "success" : "failed");
            steps.add(deployStep);
            resp.setSuccess(ok);
            resp.setMessage(ok ? phaseName + " 完成" : phaseName + " 失败");
            return resp;
        } catch (Exception e) {
            return buildDeployFailure(resp, steps, node, sshPort, phaseName, e);
        }
    }

    private HealthProbe probeHealth(SshSessionHelper ssh, ComputeNodeDO node) throws Exception {
        String sourceRoot = resolveStorageClusterSource();
        syncStorageCluster(ssh, sourceRoot);
        SshSessionHelper.SshExecResult result = execRemoteScript(
                ssh, StorageStackDeployUtil.buildHealthCheckScript(node), CHECK_TIMEOUT_MS);
        String out = result.combinedOutput();

        HealthProbe probe = new HealthProbe();
        probe.cephCliStep = stepFromToken("Ceph CLI", out, "CEPH_CLI_OK", "CEPH_CLI_MISSING", "未安装 ceph 命令");
        probe.cephHealthStep = stepFromToken("集群健康", out, "CEPH_HEALTH_OK", "CEPH_HEALTH_BAD", "集群状态异常");
        probe.osdStep = stepFromToken("OSD 状态", out, "OSD_UP", "OSD_DOWN", "无在线 OSD");
        boolean poolOk = out.contains("POOL_PLAYBACKS_OK") && out.contains("POOL_SNAPS_OK");
        probe.poolStep = runStep("存储池", poolOk ? "success" : "failed",
                poolOk ? "easyaiot-playbacks / easyaiot-snaps 已创建" : "存储池未完整创建");
        probe.cephfsStep = stepFromToken("CephFS", out, "CEPHFS_OK", "CEPHFS_MISSING", "CephFS 未创建");
        boolean mountOk = out.contains("MOUNT_ROOT_OK");
        probe.mountStep = runStep("CephFS 挂载", mountOk ? "success" : "failed",
                mountOk ? "挂载点 " + StorageStackDeployUtil.buildDeployEnvMap(node).get("MOUNT_ROOT") + " 已就绪"
                        : "CephFS 未挂载");

        probe.cephHealthy = out.contains("CEPH_HEALTH_OK");
        probe.osdRunning = out.contains("OSD_UP");
        probe.poolExists = poolOk;
        probe.cephfsReady = out.contains("CEPHFS_OK");
        probe.mountReady = mountOk;
        if (!probe.cephCliStep.getOutput().isBlank() && probe.cephCliStep.getOutput().length() < 20) {
            probe.cephCliStep.setOutput(trimOutput(out, 1500));
        }
        return probe;
    }

    private NodeMediaRemoteDeployRespVO.DeployStep stepFromToken(
            String name, String output, String okToken, String failToken, String failHint) {
        if (output.contains(okToken)) {
            return runStep(name, "success", name + " 正常");
        }
        if (output.contains(failToken)) {
            return runStep(name, "failed", failHint);
        }
        return runStep(name, "failed", name + " 状态未知");
    }

    private NodeMediaRemoteDeployRespVO.DeployStep syncStorageCluster(SshSessionHelper ssh, String sourceRoot)
            throws Exception {
        String remoteRoot = StorageStackDeployUtil.remoteClusterRoot();
        ssh.ensureRemoteDir(remoteRoot);
        int count = 0;
        for (String relative : SYNC_RELATIVE_FILES) {
            File local = new File(sourceRoot, relative);
            if (!local.isFile()) {
                throw exception(STORAGE_CLUSTER_SOURCE_NOT_FOUND);
            }
            ssh.uploadFile(local.getAbsolutePath(), remoteRoot + "/" + relative);
            ssh.exec("chmod +x " + remoteRoot + "/" + relative, 10000);
            count++;
        }
        return runStep("同步 storage-cluster", "success",
                "已上传 " + count + " 个 Ceph 脚本至 " + remoteRoot);
    }

    private String resolveStorageClusterSource() {
        if (storageClusterSourcePath != null && !storageClusterSourcePath.isBlank()) {
            File dir = new File(storageClusterSourcePath);
            if (dir.isDirectory()) {
                return dir.getAbsolutePath();
            }
        }
        String[] candidates = {
                "/opt/easyaiot/.scripts/media-cluster/ceph",
                System.getProperty("user.dir") + "/.scripts/media-cluster/ceph",
                System.getProperty("user.dir") + "/../.scripts/media-cluster/ceph",
        };
        for (String path : candidates) {
            File check = new File(path, "check_ceph_health.sh");
            if (check.isFile()) {
                return new File(path).getAbsolutePath();
            }
        }
        throw exception(STORAGE_CLUSTER_SOURCE_NOT_FOUND);
    }

    private String buildStackCheckMessage(NodeStorageStackCheckRespVO resp, ComputeNodeDO node) {
        String mount = StorageStackDeployUtil.buildDeployEnvMap(node).get("MOUNT_ROOT");
        if (Boolean.TRUE.equals(resp.getDeployed())) {
            return "Ceph 存储已就绪：集群健康、OSD 在线，CephFS 已挂载至 " + mount;
        }
        if (Boolean.TRUE.equals(resp.getCephHealthy()) && Boolean.TRUE.equals(resp.getOsdRunning())) {
            return "Ceph 集群正常，但 CephFS 未挂载。请执行客户端挂载部署";
        }
        if (Boolean.TRUE.equals(resp.getMountReady())) {
            return "CephFS 已挂载，但集群或 OSD 状态需检查";
        }
        return "Ceph 存储未就绪，请按纳管向导完成 OSD 准备、建池与客户端挂载";
    }

    private String buildMountCheckMessage(HealthProbe probe, ComputeNodeDO node) {
        String mount = StorageStackDeployUtil.buildDeployEnvMap(node).get("MOUNT_ROOT");
        if (Boolean.TRUE.equals(probe.mountReady)) {
            return "CephFS 已挂载至 " + mount;
        }
        return "CephFS 未挂载至 " + mount + "，请执行客户端挂载部署";
    }

    private ComputeNodeDO requireNode(Long nodeId) {
        ComputeNodeDO node = computeNodeMapper.selectById(nodeId);
        if (node == null) {
            throw exception(COMPUTE_NODE_NOT_EXISTS);
        }
        return node;
    }

    private static final class NodeSshCredential {
        private final NodeSshCredentialDO credential;
        private final String password;
        private final String privateKey;

        private NodeSshCredential(NodeSshCredentialDO credential, String password, String privateKey) {
            this.credential = credential;
            this.password = password;
            this.privateKey = privateKey;
        }
    }

    private static final class HealthProbe {
        private NodeMediaRemoteDeployRespVO.DeployStep cephCliStep;
        private NodeMediaRemoteDeployRespVO.DeployStep cephHealthStep;
        private NodeMediaRemoteDeployRespVO.DeployStep osdStep;
        private NodeMediaRemoteDeployRespVO.DeployStep poolStep;
        private NodeMediaRemoteDeployRespVO.DeployStep cephfsStep;
        private NodeMediaRemoteDeployRespVO.DeployStep mountStep;
        private Boolean cephHealthy;
        private Boolean osdRunning;
        private Boolean poolExists;
        private Boolean cephfsReady;
        private Boolean mountReady;
    }

    private NodeSshCredential loadSshCredential(Long nodeId) {
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
        return new NodeSshCredential(credential, password, privateKey);
    }

    private SshSessionHelper openSshSession(ComputeNodeDO node, NodeSshCredential credential, int sshPort)
            throws Exception {
        return SshSessionHelper.connect(
                node.getHost(),
                sshPort,
                credential.credential.getUsername(),
                credential.credential.getAuthType(),
                credential.password,
                credential.privateKey);
    }

    private SshSessionHelper.SshExecResult execRemoteScript(SshSessionHelper ssh, String scriptBody, int timeoutMs)
            throws Exception {
        String encoded = Base64.getEncoder().encodeToString(scriptBody.getBytes(StandardCharsets.UTF_8));
        String tmpScript = "/tmp/easyaiot-storage-op-" + System.currentTimeMillis() + ".sh";
        return ssh.exec(
                "echo " + encoded + " | base64 -d > " + tmpScript
                        + " && chmod +x " + tmpScript
                        + " && bash " + tmpScript
                        + " ; rm -f " + tmpScript,
                timeoutMs);
    }

    private NodeMediaRemoteDeployRespVO buildDeployFailure(
            NodeMediaRemoteDeployRespVO resp,
            List<NodeMediaRemoteDeployRespVO.DeployStep> steps,
            ComputeNodeDO node,
            int sshPort,
            String stepName,
            Exception e) {
        log.error("Ceph SSH 操作失败 nodeId={} host={}:{} step={}",
                node.getId(), node.getHost(), sshPort, stepName, e);
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

    private NodeStorageMountCheckRespVO buildMountCheckFailure(
            NodeStorageMountCheckRespVO resp,
            List<NodeMediaRemoteDeployRespVO.DeployStep> steps,
            ComputeNodeDO node,
            int sshPort,
            Exception e) {
        NodeMediaRemoteDeployRespVO.DeployStep fail = new NodeMediaRemoteDeployRespVO.DeployStep();
        fail.setName(steps.isEmpty() ? "SSH 连接" : "检测中断");
        fail.setStatus("failed");
        String detail = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
        fail.setOutput("连接 " + node.getHost() + ":" + sshPort + " 失败: " + detail);
        steps.add(fail);
        resp.setSuccess(false);
        resp.setMountReady(false);
        resp.setMessage(fail.getOutput());
        return resp;
    }

    private NodeMediaRemoteDeployRespVO.DeployStep runStep(String name, String status, String output) {
        NodeMediaRemoteDeployRespVO.DeployStep step = new NodeMediaRemoteDeployRespVO.DeployStep();
        step.setName(name);
        step.setStatus(status);
        step.setOutput(output);
        return step;
    }

    private String trimOutput(String text, int maxLen) {
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
