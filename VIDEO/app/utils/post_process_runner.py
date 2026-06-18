"""
算法任务 AI 后处理脚本加载与执行。
用户在工作区编写 post_process.py，实现 process(ctx) 函数。
"""
from __future__ import annotations

import importlib.util
import logging
import os
import sys
import threading
import time
from pathlib import Path
from typing import Any, Dict, List, Optional

logger = logging.getLogger(__name__)

_MODULE_CACHE: Dict[str, Any] = {}
_MODULE_MTIME: Dict[str, float] = {}
_CACHE_LOCK = threading.Lock()
_STATE: Dict[str, Dict[str, Any]] = {}


def get_workspace_root() -> Path:
    env_root = (os.getenv('POST_PROCESS_WORKSPACE_ROOT') or '').strip()
    if env_root:
        return Path(env_root).resolve()
    video_root = Path(__file__).resolve().parents[2]
    return (video_root.parent / '.scripts' / 'docker' / 'vscode_data' / 'workspaces').resolve()


def get_task_workspace_dir(task_id: int) -> Path:
    return get_workspace_root() / f'task_{task_id}'


def get_task_script_path(task_id: int, script_name: str = 'post_process.py') -> Path:
    return get_task_workspace_dir(task_id) / script_name


def _serialize_detection(det: Dict[str, Any]) -> Dict[str, Any]:
    return {
        'class_id': det.get('class_id'),
        'class_name': det.get('class_name'),
        'confidence': float(det.get('confidence', 0)),
        'bbox': list(det.get('bbox') or []),
        'track_id': det.get('track_id', 0),
        'is_cached': bool(det.get('is_cached', False)),
        'first_seen_time': det.get('first_seen_time'),
        'duration': float(det.get('duration', 0) or 0),
    }


def build_task_context(
    task_config: Any,
    *,
    device_id: str,
    device_name: str,
    frame_number: int,
    timestamp: float,
    detections: List[Dict[str, Any]],
    tracked_detections: Optional[List[Dict[str, Any]]] = None,
    regions: Optional[List[Dict[str, Any]]] = None,
) -> Dict[str, Any]:
    """构建算法任务通用入参，供用户后处理脚本使用。"""
    task_id = getattr(task_config, 'id', None)
    state_key = f'task_{task_id}' if task_id is not None else 'unknown'
    state = _STATE.setdefault(state_key, {})

    tracked = tracked_detections if tracked_detections is not None else detections
    return {
        'task_id': task_id,
        'task_name': getattr(task_config, 'task_name', ''),
        'task_code': getattr(task_config, 'task_code', ''),
        'task_type': getattr(task_config, 'task_type', 'realtime'),
        'device_id': device_id,
        'device_name': device_name,
        'frame_number': frame_number,
        'timestamp': timestamp,
        'detections': [_serialize_detection(d) for d in detections],
        'tracked_detections': [_serialize_detection(d) for d in tracked],
        'tracking_enabled': bool(getattr(task_config, 'tracking_enabled', False)),
        'regions': regions or [],
        'state': state,
        'model_ids': _parse_model_ids(getattr(task_config, 'model_ids', None)),
    }


def _parse_model_ids(raw) -> List[int]:
    if not raw:
        return []
    try:
        import json
        parsed = json.loads(raw) if isinstance(raw, str) else raw
        if isinstance(parsed, list):
            return [int(x) for x in parsed if x is not None and str(x).strip() != '']
    except Exception:
        pass
    return []


def load_regions_for_device(device_id: str) -> List[Dict[str, Any]]:
    try:
        from models import DeviceDetectionRegion
        regions = DeviceDetectionRegion.query.filter_by(
            device_id=device_id,
            is_enabled=True,
        ).order_by(DeviceDetectionRegion.sort_order).all()
        return [r.to_dict() for r in regions]
    except Exception as exc:
        logger.debug('加载设备检测区域失败 device=%s: %s', device_id, exc)
        return []


def _load_process_module(task_id: int, script_name: str):
    script_path = get_task_script_path(task_id, script_name)
    if not script_path.is_file():
        return None

    cache_key = str(script_path)
    mtime = script_path.stat().st_mtime
    with _CACHE_LOCK:
        cached = _MODULE_CACHE.get(cache_key)
        if cached is not None and _MODULE_MTIME.get(cache_key) == mtime:
            return cached

    spec = importlib.util.spec_from_file_location(f'post_process_task_{task_id}', script_path)
    if spec is None or spec.loader is None:
        return None
    module = importlib.util.module_from_spec(spec)
    sys.modules[spec.name] = module
    spec.loader.exec_module(module)
    with _CACHE_LOCK:
        _MODULE_CACHE[cache_key] = module
        _MODULE_MTIME[cache_key] = mtime
    return module


def run_post_process(
    task_config: Any,
    ctx: Dict[str, Any],
) -> Optional[Dict[str, Any]]:
    """执行用户后处理脚本，返回 process(ctx) 的结果。"""
    if not task_config or not bool(getattr(task_config, 'post_process_enabled', False)):
        return None

    task_id = getattr(task_config, 'id', None)
    if task_id is None:
        return None

    script_name = (getattr(task_config, 'post_process_script', None) or 'post_process.py').strip()
    module = _load_process_module(task_id, script_name)
    if module is None:
        return None

    process_fn = getattr(module, 'process', None)
    if not callable(process_fn):
        logger.warning('任务 %s 后处理脚本缺少 process(ctx) 函数', task_id)
        return None

    started = time.time()
    try:
        result = process_fn(ctx)
    except Exception as exc:
        logger.error('任务 %s 后处理执行失败: %s', task_id, exc, exc_info=True)
        return None
    finally:
        elapsed_ms = (time.time() - started) * 1000
        if elapsed_ms > 200:
            logger.info('任务 %s 后处理耗时 %.1fms', task_id, elapsed_ms)

    if result is None:
        return None
    if not isinstance(result, dict):
        logger.warning('任务 %s 后处理返回值必须是 dict，实际: %s', task_id, type(result).__name__)
        return None
    return result


def enqueue_post_process_request(
    task_config: Any,
    *,
    device_id: str,
    device_name: str,
    frame_number: int,
    timestamp: float,
    detections: List[Dict[str, Any]],
    tracked_detections: Optional[List[Dict[str, Any]]] = None,
    alert_image_path: Optional[str] = None,
) -> None:
    """将检测结果 HTTP 投递至 iot-sink 入队，由 iot-sink 对接 Kafka 并分发 Worker。"""
    if not task_config or not bool(getattr(task_config, 'post_process_enabled', False)):
        return
    regions = load_regions_for_device(device_id)
    ctx = build_task_context(
        task_config,
        device_id=device_id,
        device_name=device_name,
        frame_number=frame_number,
        timestamp=timestamp,
        detections=detections,
        tracked_detections=tracked_detections,
        regions=regions,
    )
    if alert_image_path:
        ctx['alert_image_path'] = alert_image_path
    try:
        from app.services.post_process_sink_client import publish_post_process_request_async
        publish_post_process_request_async(ctx, alert_image_path=alert_image_path)
    except Exception as exc:
        logger.warning('后处理请求投递 iot-sink 失败: %s', exc)


def apply_post_process(
    task_config: Any,
    *,
    device_id: str,
    device_name: str,
    frame_number: int,
    timestamp: float,
    detections: List[Dict[str, Any]],
    tracked_detections: Optional[List[Dict[str, Any]]] = None,
) -> Optional[Dict[str, Any]]:
    """兼容旧调用：仅投递 Kafka 请求，不在算法进程内执行后处理。"""
    enqueue_post_process_request(
        task_config,
        device_id=device_id,
        device_name=device_name,
        frame_number=frame_number,
        timestamp=timestamp,
        detections=detections,
        tracked_detections=tracked_detections,
    )
    return None


def resolve_post_process_outcome(
    pp_result: Optional[Dict[str, Any]],
    detections: List[Dict[str, Any]],
) -> tuple[List[Dict[str, Any]], List[Dict[str, Any]]]:
    """解析后处理返回值，得到用于默认告警的检测列表与自定义告警。"""
    if not pp_result:
        return detections, []

    detections_for_alert = detections
    if pp_result.get('suppress_default_alert'):
        detections_for_alert = []
    elif pp_result.get('detections') is not None:
        detections_for_alert = pp_result.get('detections') or []

    custom_alerts = pp_result.get('alerts') or []
    if custom_alerts and not isinstance(custom_alerts, list):
        custom_alerts = []
    return detections_for_alert, custom_alerts
