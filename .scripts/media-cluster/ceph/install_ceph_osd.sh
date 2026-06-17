#!/usr/bin/env bash
# Ceph OSD 节点准备（存储节点纳管；完整集群需 cephadm/ceph-volume 预先 bootstrap）
set -euo pipefail

CEPH_OSD_PATH="${CEPH_OSD_PATH:-/var/lib/ceph/osd}"
PLAYBACKS_POOL="${PLAYBACKS_POOL:-easyaiot-playbacks}"

if ! command -v ceph >/dev/null 2>&1; then
  echo "[ERROR] 未安装 ceph 客户端/工具，请先在该节点安装 Ceph 软件源或加入 cephadm 集群"
  exit 1
fi

mkdir -p "${CEPH_OSD_PATH}"
chmod 755 "${CEPH_OSD_PATH}" 2>/dev/null || true

if ceph -s 2>/dev/null | grep -qiE 'HEALTH_(OK|WARN)'; then
  echo CEPH_HEALTH_OK
else
  echo "[WARN] 集群状态非 HEALTH_OK，请检查 MON/MGR/OSD"
  ceph -s 2>&1 | head -n 10 || true
fi

if ceph osd stat 2>/dev/null | grep -qi 'up'; then
  echo OSD_UP
else
  echo "[WARN] 当前无在线 OSD，请使用 cephadm / ceph-volume 添加 OSD 至 ${CEPH_OSD_PATH}"
fi

if ceph osd pool ls 2>/dev/null | grep -qx "${PLAYBACKS_POOL}"; then
  echo POOL_PLAYBACKS_OK
else
  echo "[INFO] 存储池 ${PLAYBACKS_POOL} 尚未创建，可在 MON 节点执行 pool-create.sh"
fi

echo "OSD path ready: ${CEPH_OSD_PATH}"
echo OSD_PREPARE_OK
