"""
EasyAIoT 集群模式共享存储配置。

CLUSTER_MODE=true 时，各服务统一使用 CephFS 挂载根目录（默认 /mnt/easyaiot-media）
作为跨节点热缓冲；MinIO 仍为权威归档。

目录规范（见 docs/streaming-cluster/存储与上传流水线.md）：
  /mnt/easyaiot-media/
  ├── playbacks/{live,ai,gb28181}/...
  ├── snaps/...
  ├── ai/datasets/、ai/models/、ai/train/...
  ├── alert_images/...
  └── staging/...
"""
from __future__ import annotations

import logging
import os
from typing import Dict, Optional

logger = logging.getLogger(__name__)

DEFAULT_MOUNT_ROOT = '/mnt/easyaiot-media'


def is_cluster_mode() -> bool:
    raw = (os.getenv('CLUSTER_MODE') or '').strip().lower()
    return raw in ('1', 'true', 'yes', 'on')


def get_mount_root() -> str:
    explicit = (os.getenv('MEDIA_HOST_DATA_ROOT') or os.getenv('CEPH_MOUNT_ROOT') or '').strip()
    if explicit:
        return os.path.normpath(os.path.expanduser(os.path.expandvars(explicit)))
    if is_cluster_mode():
        return DEFAULT_MOUNT_ROOT
    return (os.getenv('SRS_HOST_DATA_ROOT') or '/data').strip()


def get_playbacks_dir() -> str:
    record = (os.getenv('MEDIA_RECORD_DIR') or '').strip()
    if record:
        return record.rstrip('/\\')
    return os.path.join(get_mount_root().rstrip('/\\'), 'playbacks')


def get_snaps_dir() -> str:
    snap = (os.getenv('MEDIA_SNAP_DIR') or '').strip()
    if snap:
        return snap.rstrip('/\\')
    return os.path.join(get_mount_root().rstrip('/\\'), 'snaps')


def get_ai_datasets_dir() -> str:
    explicit = (os.getenv('AI_DATASETS_DIR') or os.getenv('CLUSTER_AI_DATASETS_DIR') or '').strip()
    if explicit:
        return os.path.normpath(explicit)
    if is_cluster_mode():
        return os.path.join(get_mount_root(), 'ai', 'datasets')
    return os.path.join(_local_ai_root(), 'data', 'datasets')


def get_ai_models_dir() -> str:
    explicit = (os.getenv('AI_MODELS_DIR') or os.getenv('CLUSTER_AI_MODELS_DIR') or '').strip()
    if explicit:
        return os.path.normpath(explicit)
    if is_cluster_mode():
        return os.path.join(get_mount_root(), 'ai', 'models')
    return os.path.join(_local_ai_root(), 'data', 'models')


def get_ai_train_dir(task_id: int | str) -> str:
    base = (os.getenv('AI_TRAIN_DIR') or '').strip()
    if not base:
        base = os.path.join(get_mount_root(), 'ai', 'train') if is_cluster_mode() else os.path.join(
            _local_ai_root(), 'data', 'datasets'
        )
    return os.path.join(base, f'train_{task_id}')


def get_alert_images_dir(video_root: str = '') -> str:
    explicit = (os.getenv('ALERT_IMAGES_DIR') or '').strip()
    if explicit:
        return os.path.abspath(os.path.expanduser(explicit))
    if is_cluster_mode():
        return os.path.join(get_mount_root(), 'alert_images')
    base = (video_root or _local_video_root()).rstrip(os.sep)
    return os.path.join(base, 'alert_images')


def get_staging_dir() -> str:
    explicit = (os.getenv('MEDIA_STAGING_DIR') or '').strip()
    if explicit:
        return explicit.rstrip('/\\')
    return os.path.join(get_mount_root().rstrip('/\\'), 'staging')


def verify_ceph_mount(mount_root: Optional[str] = None) -> bool:
    """检查 CephFS 挂载是否就绪（cluster 模式或显式配置了挂载根时）。"""
    root = (mount_root or get_mount_root()).rstrip('/\\')
    if not root or root in ('/data', '/tmp'):
        return True
    try:
        if not os.path.isdir(root):
            return False
        if os.path.ismount(root):
            return True
        # 子目录已存在且可写时也视为可用（嵌套 bind mount 场景）
        probe = os.path.join(root, 'playbacks')
        return os.path.isdir(probe) and os.access(probe, os.W_OK)
    except OSError:
        return False


def apply_cluster_env_defaults(force: bool = False) -> Dict[str, str]:
    """CLUSTER_MODE 开启时写入标准环境变量，返回已设置的键值。"""
    if not force and not is_cluster_mode():
        return {}
    root = get_mount_root()
    defaults = {
        'MEDIA_HOST_DATA_ROOT': root,
        'MEDIA_RECORD_DIR': get_playbacks_dir(),
        'MEDIA_SNAP_DIR': get_snaps_dir(),
        'SRS_HOST_DATA_ROOT': root,
        'SRS_RECORD_DIR': get_playbacks_dir(),
        'AI_DATASETS_DIR': get_ai_datasets_dir(),
        'AI_MODELS_DIR': get_ai_models_dir(),
        'MEDIA_STAGING_DIR': get_staging_dir(),
    }
    if is_cluster_mode():
        defaults.setdefault('MEDIA_UPLOAD_MODE', os.getenv('MEDIA_UPLOAD_MODE') or 'kafka')
        defaults.setdefault('MEDIA_NODE_POOL_ENABLED', os.getenv('MEDIA_NODE_POOL_ENABLED') or 'true')
    applied: Dict[str, str] = {}
    for key, value in defaults.items():
        if value and not os.getenv(key):
            os.environ[key] = value
            applied[key] = value
    return applied


def ensure_cluster_dirs() -> None:
    """创建集群模式下所需的目录结构。"""
    dirs = [
        get_playbacks_dir(),
        os.path.join(get_playbacks_dir(), 'live'),
        os.path.join(get_playbacks_dir(), 'ai'),
        os.path.join(get_playbacks_dir(), 'gb28181'),
        get_snaps_dir(),
        get_staging_dir(),
    ]
    if is_cluster_mode():
        dirs.extend([
            get_ai_datasets_dir(),
            get_ai_models_dir(),
            os.path.join(get_mount_root(), 'ai', 'train'),
            get_alert_images_dir(),
        ])
    for path in dirs:
        try:
            os.makedirs(path, exist_ok=True)
        except OSError as e:
            logger.warning('创建集群目录失败 path=%s error=%s', path, e)


def resolve_container_path(local_path: str, cwd: str = '') -> str:
    """容器内 /data 或 /mnt/easyaiot-media 路径映射到宿主机 CephFS 挂载。"""
    if not local_path:
        return local_path
    if not os.path.isabs(local_path) and cwd:
        local_path = os.path.join(cwd, local_path)

    media_root = get_mount_root()
    for prefix in ('/data', '/mnt/easyaiot-media'):
        p = os.path.normpath(local_path)
        if p == prefix or p.startswith(prefix + os.sep):
            if not os.path.lexists(p):
                try:
                    rel = os.path.relpath(p, prefix)
                    mapped = os.path.join(media_root, rel)
                    if os.path.lexists(mapped) or prefix == '/data':
                        return mapped
                except ValueError:
                    pass
    return local_path


def _local_ai_root() -> str:
    env_root = (os.getenv('NODE_REMOTE_AI_ROOT') or os.getenv('AI_ROOT') or '').strip()
    if env_root:
        return env_root
    # 从 lib/cluster_storage 向上两级到 repo 根，再进 AI
    here = os.path.dirname(os.path.abspath(__file__))
    candidate = os.path.join(here, '..', '..', 'AI')
    if os.path.isdir(candidate):
        return os.path.abspath(candidate)
    return os.path.abspath(os.path.join(os.getcwd(), 'AI'))


def _local_video_root() -> str:
    env_root = (os.getenv('NODE_REMOTE_VIDEO_ROOT') or os.getenv('VIDEO_ROOT') or '').strip()
    if env_root:
        return env_root
    here = os.path.dirname(os.path.abspath(__file__))
    candidate = os.path.join(here, '..', '..', 'VIDEO')
    if os.path.isdir(candidate):
        return os.path.abspath(candidate)
    return os.path.abspath(os.path.join(os.getcwd(), 'VIDEO'))
