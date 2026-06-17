#!/usr/bin/env bash
# Ceph 存储池与 CephFS 创建（在 Ceph 管理节点或 cephadm shell 内执行）
# 用法: bash pool-create.sh
set -euo pipefail

PLAYBACKS_POOL="${PLAYBACKS_POOL:-easyaiot-playbacks}"
SNAPS_POOL="${SNAPS_POOL:-easyaiot-snaps}"
AI_DATA_POOL="${AI_DATA_POOL:-easyaiot-ai-data}"
CEPHFS_NAME="${CEPHFS_NAME:-easyaiot}"
PG_NUM_PLAYBACKS="${PG_NUM_PLAYBACKS:-128}"
PG_NUM_SNAPS="${PG_NUM_SNAPS:-64}"
PG_NUM_AI="${PG_NUM_AI:-64}"
REPLICA_SIZE="${REPLICA_SIZE:-3}"
MIN_SIZE="${MIN_SIZE:-2}"

create_pool() {
  local pool="$1"
  local pg="$2"
  if ceph osd pool ls | grep -qx "${pool}"; then
    echo "[skip] pool ${pool} already exists"
    return 0
  fi
  ceph osd pool create "${pool}" "${pg}" "${pg}"
  ceph osd pool set "${pool}" size "${REPLICA_SIZE}"
  ceph osd pool set "${pool}" min_size "${MIN_SIZE}"
  echo "[ok] pool ${pool} created (pg=${pg}, size=${REPLICA_SIZE})"
}

create_pool "${PLAYBACKS_POOL}" "${PG_NUM_PLAYBACKS}"
create_pool "${SNAPS_POOL}" "${PG_NUM_SNAPS}"
create_pool "${AI_DATA_POOL}" "${PG_NUM_AI}"

if ! ceph fs ls | awk '{print $2}' | grep -qx "${CEPHFS_NAME}"; then
  ceph fs volume create "${CEPHFS_NAME}" "${PLAYBACKS_POOL}" "${SNAPS_POOL}"
  echo "[ok] CephFS ${CEPHFS_NAME} created"
else
  echo "[skip] CephFS ${CEPHFS_NAME} already exists"
  # 将 AI 数据池加入 CephFS（若尚未挂载）
  if ceph fs get "${CEPHFS_NAME}" 2>/dev/null | grep -q "${AI_DATA_POOL}"; then
    echo "[skip] AI data pool already in CephFS"
  else
    ceph fs add-data-pool "${CEPHFS_NAME}" "${AI_DATA_POOL}" 2>/dev/null || \
      echo "[warn] 无法添加 AI 数据池 ${AI_DATA_POOL}，请手动: ceph fs add-data-pool ${CEPHFS_NAME} ${AI_DATA_POOL}"
  fi
fi

ceph osd pool ls | grep easyaiot || true
echo "Done. Clients: bash .scripts/media-cluster/ceph/mount-all.sh"
