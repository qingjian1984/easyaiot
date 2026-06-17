package com.basiclab.iot.node.util;

import cn.hutool.core.util.StrUtil;
import com.basiclab.iot.node.dal.dataobject.ComputeNodeDO;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

public final class StorageStackDeployUtil {

    private static final String REMOTE_ROOT = "/opt/easyaiot/storage-cluster";

    private StorageStackDeployUtil() {
    }

    public static String remoteClusterRoot() {
        return REMOTE_ROOT;
    }

    public static String tagString(Map<String, String> tags, String key, String defaultValue) {
        if (tags == null || !tags.containsKey(key)) {
            return defaultValue;
        }
        String raw = tags.get(key);
        return StrUtil.isBlank(raw) ? defaultValue : raw.trim();
    }

    public static Map<String, String> buildDeployEnvMap(ComputeNodeDO node) {
        Map<String, String> tags = node.getTags();
        Map<String, String> env = new LinkedHashMap<>();
        env.put("STORAGE_CLUSTER_ROOT", REMOTE_ROOT);
        env.put("CEPH_MON", tagString(tags, "ceph_mon_host", "storage-ceph"));
        env.put("CEPHFS_NAME", tagString(tags, "cephfs_name", "easyaiot"));
        env.put("PLAYBACKS_POOL", tagString(tags, "ceph_pool", "easyaiot-playbacks"));
        env.put("SNAPS_POOL", legacySnapsPool(tags));
        env.put("AI_DATA_POOL", tagString(tags, "ceph_ai_data_pool", "easyaiot-ai-data"));
        env.put("CEPH_OSD_PATH", tagString(tags, "ceph_osd_path", "/var/lib/ceph/osd"));
        env.put("MOUNT_ROOT", tagString(tags, "media_mount_path", "/mnt/easyaiot-media"));
        env.put("CEPH_CONF", tagString(tags, "ceph_conf", "/etc/ceph/ceph.conf"));
        env.put("CEPH_KEYRING", tagString(tags, "ceph_keyring", "/etc/ceph/ceph.client.easyaiot.keyring"));
        env.put("CEPH_CLIENT_NAME", tagString(tags, "ceph_client_name", "easyaiot"));
        env.put("NODE_HOST", StrUtil.blankToDefault(node.getHost(), ""));
        env.put("NODE_NAME", StrUtil.blankToDefault(node.getName(), node.getHost()));
        return env;
    }

    private static String legacySnapsPool(Map<String, String> tags) {
        String pool = tagString(tags, "ceph_pool", "easyaiot-playbacks");
        if ("easyaiot-playbacks".equals(pool)) {
            return "easyaiot-snaps";
        }
        if ("gv-playbacks".equals(pool)) {
            return "gv-snaps";
        }
        if (pool.endsWith("-playbacks")) {
            return pool.replace("-playbacks", "-snaps");
        }
        return "easyaiot-snaps";
    }

    public static String buildDeployEnvScript(ComputeNodeDO node) {
        StringBuilder sb = new StringBuilder("#!/usr/bin/env bash\nset -euo pipefail\n");
        for (Map.Entry<String, String> entry : buildDeployEnvMap(node).entrySet()) {
            sb.append("export ").append(entry.getKey()).append("=\"")
                    .append(entry.getValue().replace("\"", "\\\"")).append("\"\n");
        }
        return sb.toString();
    }

    public static String buildClientInstallScript(ComputeNodeDO node) {
        return buildDeployEnvScript(node)
                + "bash \"${STORAGE_CLUSTER_ROOT}/install_ceph_client.sh\"\n";
    }

    public static String buildOsdInstallScript(ComputeNodeDO node) {
        return buildDeployEnvScript(node)
                + "bash \"${STORAGE_CLUSTER_ROOT}/install_ceph_osd.sh\"\n";
    }

    public static String buildPoolCreateScript(ComputeNodeDO node) {
        return buildDeployEnvScript(node)
                + "bash \"${STORAGE_CLUSTER_ROOT}/pool-create.sh\"\n";
    }

    public static String buildHealthCheckScript(ComputeNodeDO node) {
        return buildDeployEnvScript(node)
                + "bash \"${STORAGE_CLUSTER_ROOT}/check_ceph_health.sh\"\n";
    }

    public static boolean isStorageRole(String role) {
        return "storage".equalsIgnoreCase(role);
    }

    public static boolean isClientMountRole(String role) {
        if (role == null) {
            return false;
        }
        String r = role.toLowerCase(Locale.ROOT);
        return "storage".equals(r) || "media".equals(r) || "hybrid".equals(r)
                || "compute".equals(r) || "gpu".equals(r);
    }

}
