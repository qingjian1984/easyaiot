"""
算法任务统一检测入口：Ultralytics(.pt) 与 ONNXInference(.onnx)
"""
from typing import Any, Callable, Dict, List, Optional

from app.utils.onnx_inference import ONNXInference


def is_onnx_detector(model: Any) -> bool:
    return isinstance(model, ONNXInference)


def run_model_detection(
    model: Any,
    frame,
    *,
    conf: float = 0.25,
    iou: float = 0.45,
    imgsz: int = 640,
    infer_device: str = 'cpu',
    should_keep: Optional[Callable[[str], bool]] = None,
) -> List[Dict[str, Any]]:
    """对单帧执行检测，返回统一格式的检测列表。"""
    if is_onnx_detector(model):
        _, raw_detections = model.detect(frame, conf_threshold=conf, iou_threshold=iou, draw=False)
        detections = []
        for det in raw_detections:
            class_name = det['class_name']
            if should_keep and not should_keep(class_name):
                continue
            x1, y1, x2, y2 = det['bbox']
            detections.append({
                'class_id': int(det.get('class', 0)),
                'class_name': class_name,
                'confidence': float(det['confidence']),
                'bbox': [int(x1), int(y1), int(x2), int(y2)],
            })
        return detections

    results = model(
        frame,
        conf=conf,
        iou=iou,
        imgsz=imgsz,
        verbose=False,
        half=False,
        device=infer_device,
    )
    result = results[0]
    detections = []
    if result.boxes is None or len(result.boxes) == 0:
        return detections

    boxes = result.boxes.xyxy.cpu().numpy()
    confidences = result.boxes.conf.cpu().numpy()
    class_ids = result.boxes.cls.cpu().numpy().astype(int)
    names = getattr(model, 'names', {})

    for box, score, cls_id in zip(boxes, confidences, class_ids):
        x1, y1, x2, y2 = map(int, box)
        class_name = names[cls_id] if names else f'class_{cls_id}'
        if should_keep and not should_keep(class_name):
            continue
        detections.append({
            'class_id': int(cls_id),
            'class_name': class_name,
            'confidence': float(score),
            'bbox': [int(x1), int(y1), int(x2), int(y2)],
        })
    return detections
