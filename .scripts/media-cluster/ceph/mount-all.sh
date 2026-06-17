#!/usr/bin/env bash
# 客户端挂载 CephFS（录像/抓拍缓冲目录）
set -euo pipefail

CEPHFS_NAME="${CEPHFS_NAME:-easyaiot}"
CEPH_MON="${CEPH_MON:-storage-ceph}"
MOUNT_ROOT="${MOUNT_ROOT:-/mnt/easyaiot-media}"
CEPH_CONF="${CEPH_CONF:-/etc/ceph/ceph.conf}"
CEPH_KEYRING="${CEPH_KEYRING:-/etc/ceph/ceph.client.easyaiot.keyring}"
CEPH_CLIENT_NAME="${CEPH_CLIENT_NAME:-easyaiot}"

mkdir -p "${MOUNT_ROOT}" "${MOUNT_ROOT}/playbacks" "${MOUNT_ROOT}/snaps" "${MOUNT_ROOT}/logs"

mountpoint -q "${MOUNT_ROOT}" || \
  mount -t ceph "${CEPH_MON}:${CEPHFS_NAME}" "${MOUNT_ROOT}" \
    -o "name=${CEPH_CLIENT_NAME},fs=${CEPHFS_NAME},conf=${CEPH_CONF},keyring=${CEPH_KEYRING},_netdev"

mkdir -p \
  "${MOUNT_ROOT}/playbacks/live" \
  "${MOUNT_ROOT}/playbacks/ai" \
  "${MOUNT_ROOT}/playbacks/gb28181" \
  "${MOUNT_ROOT}/snaps" \
  "${MOUNT_ROOT}/staging" \
  "${MOUNT_ROOT}/ai/datasets/uploads" \
  "${MOUNT_ROOT}/ai/models" \
  "${MOUNT_ROOT}/ai/train" \
  "${MOUNT_ROOT}/alert_images"

df -h "${MOUNT_ROOT}"
echo "CephFS ${CEPHFS_NAME} mounted at ${MOUNT_ROOT}"
