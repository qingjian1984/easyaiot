#!/usr/bin/env bash
# CephFS 客户端挂载（媒体/计算/存储客户端节点）
set -euo pipefail

STORAGE_CLUSTER_ROOT="${STORAGE_CLUSTER_ROOT:-/opt/easyaiot/storage-cluster}"
CEPHFS_NAME="${CEPHFS_NAME:-easyaiot}"
CEPH_MON="${CEPH_MON:-storage-ceph}"
MOUNT_ROOT="${MOUNT_ROOT:-/mnt/easyaiot-media}"
CEPH_CONF="${CEPH_CONF:-/etc/ceph/ceph.conf}"
CEPH_KEYRING="${CEPH_KEYRING:-/etc/ceph/ceph.client.easyaiot.keyring}"
CEPH_CLIENT_NAME="${CEPH_CLIENT_NAME:-easyaiot}"

if [[ ! -f "${CEPH_CONF}" ]]; then
  echo "[ERROR] 未找到 ${CEPH_CONF}，请先从 Ceph 管理节点分发客户端配置"
  exit 1
fi

mkdir -p "${MOUNT_ROOT}" "${MOUNT_ROOT}/playbacks" "${MOUNT_ROOT}/snaps" "${MOUNT_ROOT}/logs"
mkdir -p "${MOUNT_ROOT}/playbacks/live" "${MOUNT_ROOT}/playbacks/ai" "${MOUNT_ROOT}/playbacks/gb28181"

if ! mountpoint -q "${MOUNT_ROOT}"; then
  mount -t ceph "${CEPH_MON}:${CEPHFS_NAME}" "${MOUNT_ROOT}" \
    -o "name=${CEPH_CLIENT_NAME},fs=${CEPHFS_NAME},conf=${CEPH_CONF},keyring=${CEPH_KEYRING},_netdev"
fi

df -h "${MOUNT_ROOT}"
echo CLIENT_MOUNT_OK
