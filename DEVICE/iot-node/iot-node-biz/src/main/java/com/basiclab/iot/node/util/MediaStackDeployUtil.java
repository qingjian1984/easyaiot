package com.basiclab.iot.node.util;

import cn.hutool.core.util.StrUtil;
import com.basiclab.iot.node.dal.dataobject.ComputeNodeDO;

import java.util.Locale;
import java.util.Map;

public final class MediaStackDeployUtil {

    private static final String REMOTE_ROOT = "/opt/easyaiot/media-cluster";

    private MediaStackDeployUtil() {
    }

    public static String remoteClusterRoot() {
        return REMOTE_ROOT;
    }

    public static String sanitizeNodeName(String name, String host) {
        String raw = StrUtil.blankToDefault(name, StrUtil.blankToDefault(host, "media-node")).trim().toLowerCase(Locale.ROOT);
        String slug = raw.replaceAll("[^a-z0-9-]+", "-").replaceAll("^-+|-+$", "");
        return StrUtil.isBlank(slug) ? "media-node" : slug;
    }

    public static int tagInt(Map<String, String> tags, String key, int defaultValue) {
        if (tags == null || !tags.containsKey(key)) {
            return defaultValue;
        }
        try {
            return Integer.parseInt(tags.get(key));
        } catch (NumberFormatException ignored) {
            return defaultValue;
        }
    }

    public enum DeployPhase {
        FULL,
        PREPARE_IMAGES,
        DEPLOY_SERVICES
    }

    public static String buildDeployScript(ComputeNodeDO node, String hookHost, int hookPort) {
        return buildDeployScript(node, hookHost, hookPort, DeployPhase.FULL);
    }

    public static String buildDeployScript(ComputeNodeDO node, String hookHost, int hookPort, DeployPhase phase) {
        StringBuilder sb = new StringBuilder(buildDeployEnvScript(node, hookHost, hookPort));
        if (phase == DeployPhase.PREPARE_IMAGES) {
            sb.append("export MEDIA_PREPARE_IMAGES_ONLY=1\n");
        } else if (phase == DeployPhase.DEPLOY_SERVICES) {
            sb.append("export MEDIA_DEPLOY_SERVICES_ONLY=1\n");
        }
        sb.append("bash \"${MEDIA_CLUSTER_ROOT}/install_media_stack.sh\"\n");
        return sb.toString();
    }

    private static String buildDeployEnvScript(ComputeNodeDO node, String hookHost, int hookPort) {
        Map<String, String> tags = node.getTags();
        String nodeName = sanitizeNodeName(node.getName(), node.getHost());
        String host = node.getHost();
        int srsRtmp = tagInt(tags, "srs_rtmp_port", 1935);
        int srsHttp = tagInt(tags, "srs_http_port", 8080);
        int srsApi = tagInt(tags, "srs_api_port", 1985);
        int zlmHttp = tagInt(tags, "zlm_http_port", 6080);
        int zlmRtmp = tagInt(tags, "zlm_rtmp_port", 10935);
        int zlmRtpMin = tagInt(tags, "zlm_rtp_port_min", 30000);
        int zlmRtpMax = tagInt(tags, "zlm_rtp_port_max", 30500);

        return "#!/usr/bin/env bash\n"
                + "set -euo pipefail\n"
                + "export MEDIA_CLUSTER_ROOT=\"" + REMOTE_ROOT + "\"\n"
                + "export MEDIA_NODE_NAME=\"" + nodeName + "\"\n"
                + "export MEDIA_NODE_HOST=\"" + host + "\"\n"
                + "export MEDIA_HOOK_HOST=\"" + hookHost + "\"\n"
                + "export MEDIA_HOOK_PORT=\"" + hookPort + "\"\n"
                + "export SRS_CANDIDATE_IP=\"" + host + "\"\n"
                + "export SRS_RTMP_PORT=" + srsRtmp + "\n"
                + "export SRS_HTTP_PORT=" + srsHttp + "\n"
                + "export SRS_API_PORT=" + srsApi + "\n"
                + "export ZLM_HTTP_PORT=" + zlmHttp + "\n"
                + "export ZLM_RTMP_PORT=" + zlmRtmp + "\n"
                + "export ZLM_RTP_PORT_MIN=" + zlmRtpMin + "\n"
                + "export ZLM_RTP_PORT_MAX=" + zlmRtpMax + "\n"
                + "export ZLM_SECRET=\"EasyAIoT_Media_Secret\"\n";
    }

}
