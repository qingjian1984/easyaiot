"""
RTSP 流推理检测参数与执行（对齐 VIDEO algo_model_detect + run_deploy 阈值策略）。
"""
from __future__ import annotations

import os
import threading
from typing import Any, Callable, Dict, List, Optional, Set

from app.utils.onnx_inference import ONNXInference

_MODEL_INFER_LOCKS: Dict[int, threading.Lock] = {}
_MODEL_INFER_LOCKS_GUARD = threading.Lock()


def _get_model_infer_lock(model: Any) -> threading.Lock:
    key = id(model)
    with _MODEL_INFER_LOCKS_GUARD:
        lock = _MODEL_INFER_LOCKS.get(key)
        if lock is None:
            lock = threading.Lock()
            _MODEL_INFER_LOCKS[key] = lock
        return lock


def is_onnx_detector(model: Any) -> bool:
    return isinstance(model, ONNXInference)


def is_yolo26_model(
    model: Any,
    *,
    model_path: str = '',
    model_id: Optional[int] = None,
) -> bool:
    if model_id == -3:
        return True
    path_lower = str(model_path or '').lower()
    if 'yolo26' in path_lower:
        return True
    if is_onnx_detector(model):
        return False
    overrides = getattr(model, 'overrides', None) or {}
    if 'yolo26' in str(overrides.get('model', '')).lower():
        return True
    inner = getattr(model, 'model', None)
    if inner is not None:
        if bool(getattr(inner, 'end2end', False)):
            return True
        yaml_cfg = getattr(inner, 'yaml', None)
        if isinstance(yaml_cfg, dict):
            yaml_file = str(yaml_cfg.get('yaml_file', '')).lower()
            if 'yolo26' in yaml_file or yaml_cfg.get('end2end'):
                return True
    return False


def is_end2end_ultralytics_model(model: Any) -> bool:
    if is_yolo26_model(model):
        return True
    if is_onnx_detector(model):
        return False
    inner = getattr(model, 'model', None)
    if inner is not None and bool(getattr(inner, 'end2end', False)):
        return True
    yaml_cfg = getattr(inner, 'yaml', None) if inner is not None else None
    if isinstance(yaml_cfg, dict) and yaml_cfg.get('end2end'):
        return True
    return False


def resolve_stream_detect_conf(
    model: Any,
    *,
    model_path: str = '',
    model_id: Optional[int] = None,
    parameters: Optional[dict] = None,
) -> float:
    """流推理置信度：默认与 VIDEO 算法任务一致（0.5）。"""
    params = parameters or {}
    if params.get('stream_conf_thres') is not None:
        return float(params['stream_conf_thres'])
    if params.get('use_stream_algorithm_defaults') is False:
        return float(params.get('conf_thres', 0.5))
    raw = os.getenv('YOLO_DETECT_CONF', '').strip()
    if raw:
        try:
            return float(raw)
        except ValueError:
            pass
    return 0.5


def resolve_stream_detect_imgsz(
    model: Any,
    *,
    frame=None,
    frame_height: int = 0,
    base_imgsz: int = 640,
) -> int:
    """1080p 源流下 YOLO26 自动提高推理分辨率。"""
    env_raw = os.getenv('YOLO_DETECT_IMG_SIZE', '').strip()
    if env_raw:
        try:
            return max(32, int(env_raw))
        except ValueError:
            pass
    if is_yolo26_model(model):
        yolo26_imgsz = int(os.getenv('YOLO26_IMG_SIZE', '1280'))
        h = frame_height
        if frame is not None and getattr(frame, 'shape', None):
            h = int(frame.shape[0])
        if h >= 1080:
            return max(base_imgsz, yolo26_imgsz)
        if h >= 720:
            return max(base_imgsz, min(yolo26_imgsz, 960))
    return max(32, int(base_imgsz))


def build_stream_detect_config(
    model: Any,
    *,
    frame=None,
    frame_height: int = 0,
    model_path: str = '',
    model_id: Optional[int] = None,
    parameters: Optional[dict] = None,
    infer_device: str = 'cpu',
) -> Dict[str, Any]:
    params = parameters or {}
    conf = resolve_stream_detect_conf(
        model,
        model_path=model_path,
        model_id=model_id,
        parameters=params,
    )
    iou = float(
        params.get('stream_iou_thres')
        or params.get('iou_thres')
        or os.getenv('YOLO_DETECT_IOU', '0.45')
    )
    base_imgsz = int(
        params.get('stream_imgsz')
        or params.get('imgsz')
        or os.getenv('YOLO_IMG_SIZE', '640')
    )
    imgsz = resolve_stream_detect_imgsz(
        model,
        frame=frame,
        frame_height=frame_height,
        base_imgsz=base_imgsz,
    )
    return {
        'conf': conf,
        'iou': iou,
        'imgsz': imgsz,
        'infer_device': infer_device,
    }


def warmup_stream_detection(
    model: Any,
    *,
    detect_config: Dict[str, Any],
) -> None:
    import numpy as np

    size = max(32, int(detect_config.get('imgsz', 640)))
    dummy = np.zeros((size, size, 3), dtype=np.uint8)
    run_stream_detection(
        model,
        dummy,
        conf=float(detect_config['conf']),
        iou=float(detect_config['iou']),
        imgsz=size,
        infer_device=str(detect_config.get('infer_device', 'cpu')),
    )


def run_stream_detection(
    model: Any,
    frame,
    *,
    conf: float = 0.25,
    iou: float = 0.45,
    imgsz: int = 640,
    infer_device: str = 'cpu',
    class_ids: Optional[Set[int]] = None,
    should_keep: Optional[Callable[[str], bool]] = None,
) -> List[Dict[str, Any]]:
    """对单帧执行检测，返回统一格式的检测列表。"""
    with _get_model_infer_lock(model):
        if is_onnx_detector(model):
            _, raw_detections = model.detect(
                frame,
                conf_threshold=conf,
                iou_threshold=iou,
                draw=False,
                class_ids=list(class_ids) if class_ids else None,
            )
            detections: List[Dict[str, Any]] = []
            for det in raw_detections or []:
                class_name = det['class_name']
                cls_id = int(det.get('class', 0))
                if class_ids is not None and cls_id not in class_ids:
                    continue
                if should_keep and not should_keep(class_name):
                    continue
                x1, y1, x2, y2 = det['bbox']
                detections.append({
                    'class_id': cls_id,
                    'class_name': class_name,
                    'confidence': float(det['confidence']),
                    'bbox': [int(x1), int(y1), int(x2), int(y2)],
                })
            return detections

        predict_kwargs = dict(
            conf=conf,
            iou=iou,
            imgsz=imgsz,
            verbose=False,
            half=False,
            device=infer_device,
        )
        if class_ids:
            predict_kwargs['classes'] = sorted(class_ids)
        if is_end2end_ultralytics_model(model) or is_yolo26_model(model):
            predict_kwargs['max_det'] = 300
            predict_kwargs['iou'] = max(iou, 0.7)

        results = model(frame, **predict_kwargs)
        result = results[0]
        detections = []
        if result.boxes is None or len(result.boxes) == 0:
            return detections

        boxes = result.boxes.xyxy.cpu().numpy()
        confidences = result.boxes.conf.cpu().numpy()
        det_class_ids = result.boxes.cls.cpu().numpy().astype(int)
        names = getattr(model, 'names', {})

        for box, score, cls_id in zip(boxes, confidences, det_class_ids):
            class_name = names[cls_id] if names else f'class_{cls_id}'
            if should_keep and not should_keep(class_name):
                continue
            x1, y1, x2, y2 = map(int, box)
            detections.append({
                'class_id': int(cls_id),
                'class_name': class_name,
                'confidence': float(score),
                'bbox': [int(x1), int(y1), int(x2), int(y2)],
            })
        return detections
