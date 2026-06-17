#!/usr/bin/env bash
# Ceph / CephFS 健康探测（控制面 SSH 检测调用）
set -euo pipefail

CEPHFS_NAME="${CEPHFS_NAME:-easyaiot}"
PLAYBACKS_POOL="${PLAYBACKS_POOL:-easyaiot-playbacks}"
SNAPS_POOL="${SNAPS_POOL:-easyaiot-snaps}"
MOUNT_ROOT="${MOUNT_ROOT:-/mnt/easyaiot-media}"

if command -v ceph >/dev/null 2>&1; then
  echo CEPH_CLI_OK
  if ceph -s 2>/dev/null | grep -qiE 'HEALTH_(OK|WARN)'; then
    echo CEPH_HEALTH_OK
    ceph -s 2>/dev/null | head -n 8
  else
    echo CEPH_HEALTH_BAD
    ceph -s 2>&1 | head -n 12 || true
  fi
  if ceph osd stat 2>/dev/null | grep -qi 'up'; then
    echo OSD_UP
    ceph osd stat 2>/dev/null || true
  else
    echo OSD_DOWN
  fi
  if ceph osd pool ls 2>/dev/null | grep -qx "${PLAYBACKS_POOL}"; then
    echo POOL_PLAYBACKS_OK
  else
    echo POOL_PLAYBACKS_MISSING
  fi
  if ceph osd pool ls 2>/dev/null | grep -qx "${SNAPS_POOL}"; then
    echo POOL_SNAPS_OK
  else
    echo POOL_SNAPS_MISSING
  fi
  AI_DATA_POOL="${AI_DATA_POOL:-easyaiot-ai-data}"
  if ceph osd pool ls 2>/dev/null | grep -qx "${AI_DATA_POOL}"; then
    echo POOL_AI_DATA_OK
  else
    echo POOL_AI_DATA_MISSING
  fi
  if ceph fs ls 2>/dev/null | awk '{print $2}' | grep -qx "${CEPHFS_NAME}"; then
    echo CEPHFS_OK
  else
    echo CEPHFS_MISSING
  fi
else
  echo CEPH_CLI_MISSING
fi

if mountpoint -q "${MOUNT_ROOT}" 2>/dev/null; then
  echo MOUNT_ROOT_OK
  df -h "${MOUNT_ROOT}" 2>/dev/null || true
else
  echo MOUNT_ROOT_MISSING
fi

for sub in playbacks snaps; do
  if mountpoint -q "${MOUNT_ROOT}/${sub}" 2>/dev/null || [[ -d "${MOUNT_ROOT}/${sub}" ]]; then
    echo "MOUNT_${sub^^}_OK"
  else
    echo "MOUNT_${sub^^}_MISSING"
  fi
done

echo CHECK_CEPH_DONE
