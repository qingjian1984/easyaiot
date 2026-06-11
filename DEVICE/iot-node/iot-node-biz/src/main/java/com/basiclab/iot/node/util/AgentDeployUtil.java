package com.basiclab.iot.node.util;

import com.basiclab.iot.node.dal.dataobject.ComputeNodeDO;

public final class AgentDeployUtil {

    public static final String REMOTE_INSTALL_DIR = "/opt/easyaiot/node-agent";

    public static final String[] SYNC_RELATIVE_FILES = {
            "run_agent.py",
            "agent_server.py",
            "media_manager.py",
            "workload_manager.py",
            "requirements.txt",
            "agent.env.example",
            "install.sh",
    };

    private AgentDeployUtil() {
    }

    public static String buildEnvContent(ComputeNodeDO node, String controlPlaneUrl) {
        int port = node.getAgentPort() != null && node.getAgentPort() > 0 ? node.getAgentPort() : 9100;
        return "# EasyAIoT Node Agent 配置\n"
                + "NODE_ID=" + node.getId() + "\n"
                + "AGENT_TOKEN=" + node.getAgentToken() + "\n"
                + "CONTROL_PLANE_URL=" + controlPlaneUrl + "\n"
                + "HEARTBEAT_INTERVAL=10\n"
                + "AGENT_LISTEN_HOST=0.0.0.0\n"
                + "AGENT_LISTEN_PORT=" + port + "\n"
                + "AI_ROOT=/opt/easyaiot/AI\n"
                + "VIDEO_ROOT=/opt/easyaiot/VIDEO\n"
                + "MEDIA_CLUSTER_ROOT=/opt/easyaiot/media-cluster\n"
                + "MINIO_ENDPOINT=http://localhost:9000\n"
                + "MINIO_ACCESS_KEY=minioadmin\n"
                + "MINIO_SECRET_KEY=your-secret\n";
    }

    public static String buildInstallScript(String envContent) {
        return "#!/usr/bin/env bash\n"
                + "set -euo pipefail\n"
                + "INSTALL_DIR=\"" + REMOTE_INSTALL_DIR + "\"\n"
                + "cd \"$INSTALL_DIR\"\n"
                + "sudo tee agent.env > /dev/null <<'EOF'\n"
                + envContent
                + "EOF\n"
                + "sudo chmod +x install.sh\n"
                + "sudo bash install.sh\n"
                + "sudo systemctl enable --now easyaiot-node-agent\n";
    }

}
