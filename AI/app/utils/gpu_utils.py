"""
GPU 探测与训练设备解析（与 VIDEO 算法任务多卡逻辑对齐）。
"""
from __future__ import annotations

import gc
import os
from typing import Any, Callable, List, Optional, Union

YoloDevice = Union[str, int, List[int]]


def _use_gpu_enabled() -> bool:
    return os.environ.get('USE_GPU', 'False').lower() == 'true'


def parse_gpu_id_list(value: str) -> List[int]:
    """解析逗号分隔的 GPU 索引，如 \"0,1,2\"。"""
    if not value:
        return []
    ids: List[int] = []
    for part in str(value).split(','):
        p = part.strip()
        if not p:
            continue
        try:
            ids.append(int(p))
        except Exception:
            continue
    seen = set()
    result: List[int] = []
    for x in ids:
        if x in seen:
            continue
        seen.add(x)
        result.append(x)
    return result


def detect_visible_gpu_ids() -> List[int]:
    """
    返回当前进程可见的 GPU 索引列表。
    若设置 CUDA_VISIBLE_DEVICES，torch 看到的是重映射后的连续索引（0..N-1）。
    """
    if not _use_gpu_enabled():
        return []

    try:
        import torch
        if not torch.cuda.is_available():
            return []
        n = int(torch.cuda.device_count())
        if n <= 0:
            return []
        return list(range(n))
    except Exception:
        return []


def get_visible_gpu_ids() -> List[int]:
    """
    获取用于训练的逻辑 GPU 列表。

    CUDA_VISIBLE_DEVICES 会把宿主机物理编号重映射为当前进程内的连续编号，
    因此仅当 GPU_IDS 已经是有效逻辑编号时才采用；否则以 torch 探测结果为准。
    """
    visible = detect_visible_gpu_ids()
    if not visible:
        return []
    configured = parse_gpu_id_list(os.getenv('GPU_IDS', '').strip())
    if configured and all(gpu_id in visible for gpu_id in configured):
        return configured
    return visible


def normalize_request_gpu_ids(gpu_ids: Any) -> Optional[List[int]]:
    """解析请求体中的 gpu_ids（list 或逗号分隔字符串）。"""
    if gpu_ids is None:
        return None
    if isinstance(gpu_ids, str):
        parsed = parse_gpu_id_list(gpu_ids)
        return parsed if parsed else None
    if isinstance(gpu_ids, (list, tuple)):
        result: List[int] = []
        seen = set()
        for item in gpu_ids:
            try:
                gpu_id = int(item)
            except (TypeError, ValueError):
                continue
            if gpu_id in seen:
                continue
            seen.add(gpu_id)
            result.append(gpu_id)
        return result if result else None
    try:
        return [int(gpu_ids)]
    except (TypeError, ValueError):
        return None


def resolve_request_gpu_ids(
    gpu_ids: Any,
    *,
    manual_selection: bool,
) -> Optional[List[int]]:
    """兼容旧客户端：仅新版手动选择请求才采用 gpu_ids。"""
    if not manual_selection:
        return None
    normalized = normalize_request_gpu_ids(gpu_ids)
    if not normalized:
        raise ValueError('已启用手动 GPU 选择，但未提供有效的 GPU 编号')
    return normalized


def resolve_yolo_train_device(
    use_gpu: bool,
    gpu_ids: Optional[List[int]] = None,
) -> YoloDevice:
    """
    解析 ultralytics YOLO train() 的 device 参数。
    - 单卡: int 索引
    - 多卡: list[int]，启用 DDP 并行训练
    - 未指定 GPU: 默认使用首张可见 GPU
    - 无 GPU: 'cpu'
    """
    if not use_gpu:
        return 'cpu'

    visible = get_visible_gpu_ids()
    if not visible:
        if gpu_ids:
            raise ValueError(
                f'请求使用 GPU {gpu_ids}，但当前进程未检测到可用 CUDA 设备'
            )
        return 'cpu'

    if gpu_ids:
        invalid = [gpu_id for gpu_id in gpu_ids if gpu_id not in visible]
        if invalid:
            raise ValueError(
                f'请求的 GPU 编号 {invalid} 当前不可用，可用 GPU 编号: {visible}'
            )
        selected = gpu_ids
    else:
        selected = [visible[0]]

    if len(selected) == 1:
        return selected[0]
    return selected


def format_device_for_log(device: YoloDevice) -> str:
    if device == 'cpu':
        return 'cpu'
    if isinstance(device, list):
        return ','.join(str(d) for d in device)
    return str(device)


def _get_gpu_memory_status(torch_module, index: int) -> dict:
    try:
        free_bytes, total_bytes = torch_module.cuda.mem_get_info(index)
        return {
            'free_memory_gb': round(free_bytes / (1024 ** 3), 2),
            'used_memory_gb': round((total_bytes - free_bytes) / (1024 ** 3), 2),
        }
    except Exception:
        return {}


def check_gpu_status() -> dict:
    """检查并返回 GPU 状态（供 API 与训练日志使用）。"""
    status = {
        'use_gpu_env': _use_gpu_enabled(),
        'gpu_ids_env': os.getenv('GPU_IDS', '').strip() or None,
        'cuda_visible_devices': os.environ.get('CUDA_VISIBLE_DEVICES'),
        'nvidia_visible_devices': os.environ.get('NVIDIA_VISIBLE_DEVICES'),
    }

    try:
        import torch
        status['pytorch_version'] = torch.__version__
        status['cuda_available'] = torch.cuda.is_available()
        status['cuda_version'] = (
            torch.version.cuda if hasattr(torch.version, 'cuda') else '未知'
        )
        status['device_count'] = (
            torch.cuda.device_count() if torch.cuda.is_available() else 0
        )
    except Exception as e:
        status['pytorch_version'] = None
        status['cuda_available'] = False
        status['cuda_version'] = '未知'
        status['device_count'] = 0
        status['error'] = str(e)
        status['visible_gpu_ids'] = []
        status['devices'] = []
        status['multi_gpu'] = False
        return status

    visible = get_visible_gpu_ids()
    status['visible_gpu_ids'] = visible
    status['multi_gpu'] = len(visible) > 1
    status['devices'] = []

    if torch.cuda.is_available():
        for i in range(torch.cuda.device_count()):
            props = torch.cuda.get_device_properties(i)
            device_status = {
                'index': i,
                'name': torch.cuda.get_device_name(i),
                'capability': torch.cuda.get_device_capability(i),
                'total_memory_gb': round(props.total_memory / (1024 ** 3), 2),
            }
            device_status.update(_get_gpu_memory_status(torch, i))
            status['devices'].append(device_status)
            status[f'device_{i}_name'] = torch.cuda.get_device_name(i)
            status[f'device_{i}_capability'] = torch.cuda.get_device_capability(i)

    return status


def release_cuda_memory(*, synchronize: bool = True, log_fn: Optional[Callable[[str], None]] = None) -> None:
    """释放 PyTorch CUDA 缓存（训练异常或结束后调用，避免显存长期占用）。"""
    gc.collect()
    try:
        import torch
        if not torch.cuda.is_available():
            return
        if synchronize:
            try:
                torch.cuda.synchronize()
            except Exception:
                pass
        torch.cuda.empty_cache()
        ipc_collect = getattr(torch.cuda, 'ipc_collect', None)
        if callable(ipc_collect):
            ipc_collect()
    except Exception as exc:
        if log_fn:
            log_fn(f'CUDA 显存释放失败: {exc}')


def resolve_train_dataloader_workers(use_gpu: bool) -> int:
    """
    解析 YOLO 训练 DataLoader workers。
    Windows + GPU 时默认降低 workers，减轻 pin_memory 线程导致的显存峰值/OOM。
    容器训练默认禁用子进程 worker，避免 /dev/shm 或 worker OOM 导致异常退出。
    可通过环境变量 TRAIN_DATALOADER_WORKERS 覆盖。
    """
    raw = os.environ.get('TRAIN_DATALOADER_WORKERS', '').strip()
    if raw:
        try:
            return max(0, int(raw))
        except ValueError:
            pass
    if use_gpu and os.name == 'nt':
        return 2
    if os.name != 'nt':
        if os.path.exists('/.dockerenv') or os.path.exists('/run/.containerenv'):
            return 0
        try:
            shm_stat = os.statvfs('/dev/shm')
            shm_bytes = shm_stat.f_frsize * shm_stat.f_bavail
            if shm_bytes < 512 * 1024 * 1024:
                return 0
        except OSError:
            pass
    return 4 if use_gpu else 8
