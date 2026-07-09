"""
推理结果检测框绘制（绿框 + 类别置信度标签）。
"""
from __future__ import annotations

from typing import Any, Dict, List, Optional, Tuple

import cv2
import numpy as np

# 检测框颜色（统一绿色）
INFERENCE_BOX_COLOR = (0, 255, 0)  # BGR 亮绿色
INFERENCE_BOX_COLOR_CACHED = (0, 200, 0)
INFERENCE_BOX_THICKNESS = 3
INFERENCE_FONT_SCALE = 0.90
INFERENCE_FONT_THICKNESS = 2
ALGORITHM_DEFAULT_CONF = 0.5

# 兼容旧引用
ALGORITHM_BOX_COLOR = INFERENCE_BOX_COLOR
ALGORITHM_BOX_THICKNESS = INFERENCE_BOX_THICKNESS
ALGORITHM_FONT_SCALE = INFERENCE_FONT_SCALE
ALGORITHM_FONT_THICKNESS = INFERENCE_FONT_THICKNESS


def _scaled_draw_params(frame_h: int, frame_w: int) -> tuple[int, float, int, int, int]:
    """按分辨率微调线宽与字号。"""
    scale = max(frame_h, frame_w) / 1080.0
    thickness = max(2, int(round(INFERENCE_BOX_THICKNESS * min(scale, 1.15))))
    font_scale = INFERENCE_FONT_SCALE * min(scale, 1.3)
    font_thickness = INFERENCE_FONT_THICKNESS
    cn_font_size = max(24, int(round(28 * min(scale, 1.3))))
    label_gap = max(10, int(round(12 * min(scale, 1.2))))
    return thickness, font_scale, font_thickness, cn_font_size, label_gap


def _measure_label_size(
    text: str,
    font_scale: float,
    font_thickness: int,
    cn_font_size: int,
) -> Tuple[int, int, bool]:
    """返回 (宽, 高, 是否用 PIL 绘制中文)。"""
    use_pil = not text.isascii()
    if use_pil:
        from app.utils.yolo_chinese_font import get_pil_annotation_font

        font = get_pil_annotation_font(cn_font_size)
        if font is not None:
            bbox = font.getbbox(text)
            return bbox[2] - bbox[0], bbox[3] - bbox[1], True
    (text_w, text_h), baseline = cv2.getTextSize(
        text,
        cv2.FONT_HERSHEY_SIMPLEX,
        font_scale,
        font_thickness,
    )
    # 预留边距，避免粗体/抗锯齿导致右侧或底部被裁切
    return text_w + 8, text_h + baseline + 6, False


def _label_position_above_box(
    x1: int,
    y1: int,
    text_w: int,
    text_h: int,
    frame_w: int,
    frame_h: int,
    label_gap: int,
) -> Tuple[int, int, int]:
    """标签始终贴在框上方，与框顶保持 label_gap 间距。"""
    margin = 2
    text_x = max(margin, min(x1, frame_w - text_w - margin))
    # 文字底边距框顶 label_gap 像素
    pil_top = max(margin, y1 - label_gap - text_h)
    text_y = max(text_h + margin, pil_top + text_h)
    return text_x, text_y, pil_top


def yolo_results_to_detections(result) -> List[Dict[str, Any]]:
    """Ultralytics Results -> 算法任务通用 detection 列表。"""
    detections: List[Dict[str, Any]] = []
    boxes = getattr(result, 'boxes', None)
    if boxes is None or len(boxes) == 0:
        return detections
    names = getattr(result, 'names', {}) or {}
    for box in boxes:
        cls_id = int(box.cls.item())
        detections.append({
            'bbox': box.xyxy.tolist()[0],
            'class_name': names.get(cls_id, str(cls_id)),
            'confidence': float(box.conf.item()),
        })
    return detections


def draw_algorithm_detections(
    frame: np.ndarray,
    detections: Optional[List[Dict[str, Any]]],
    *,
    tracking_enabled: bool = False,
) -> np.ndarray:
    """在帧上绘制检测结果（绿框 + 类别置信度）。"""
    if frame is None:
        return frame
    if not detections:
        return frame

    annotated_frame = frame.copy()
    h, w = annotated_frame.shape[:2]
    box_thickness, font_scale, font_thickness, cn_font_size, label_gap = _scaled_draw_params(h, w)

    for det in detections:
        bbox = det.get('bbox') or []
        if not bbox or len(bbox) != 4:
            continue

        x1, y1, x2, y2 = bbox
        x1 = max(0, min(int(x1), w - 1))
        y1 = max(0, min(int(y1), h - 1))
        x2 = max(x1 + 1, min(int(x2), w))
        y2 = max(y1 + 1, min(int(y2), h))

        class_name = str(det.get('class_name') or 'unknown')
        track_id = det.get('track_id', 0)
        is_cached = bool(det.get('is_cached', False))

        if is_cached:
            color = INFERENCE_BOX_COLOR_CACHED
            thickness = box_thickness
            alpha = 0.7
        else:
            color = INFERENCE_BOX_COLOR
            thickness = box_thickness
            alpha = 1.0

        if is_cached:
            overlay = annotated_frame.copy()
            cv2.rectangle(overlay, (x1, y1), (x2, y2), color, thickness)
            cv2.addWeighted(overlay, alpha, annotated_frame, 1 - alpha, 0, annotated_frame)
        else:
            cv2.rectangle(annotated_frame, (x1, y1), (x2, y2), color, thickness)

        if tracking_enabled:
            text = f'ID:{track_id} {class_name}'
        else:
            conf = det.get('confidence')
            if conf is not None:
                text = f'{class_name} {float(conf):.2f}'
            else:
                text = class_name

        text_w, text_h, use_pil = _measure_label_size(
            text, font_scale, font_thickness, cn_font_size,
        )
        text_x, text_y, pil_y = _label_position_above_box(
            x1, y1, text_w, text_h, w, h, label_gap,
        )

        from app.utils.yolo_chinese_font import draw_utf8_label_on_bgr, get_pil_annotation_font

        font = get_pil_annotation_font(cn_font_size)
        if font is not None:
            bbox = font.getbbox(text)
            pil_text_h = max(1, bbox[3] - bbox[1])
            pil_text_w = max(1, bbox[2] - bbox[0])
            pil_x = max(2, min(text_x, w - pil_text_w - 2))
            pil_y = max(2, y1 - label_gap - pil_text_h)
            draw_utf8_label_on_bgr(
                annotated_frame,
                text,
                (pil_x, pil_y),
                font_size=cn_font_size,
                text_color_rgb=(color[2], color[1], color[0]),
            )
        else:
            cv2.putText(
                annotated_frame,
                text,
                (text_x, text_y),
                cv2.FONT_HERSHEY_SIMPLEX,
                font_scale,
                color,
                font_thickness,
                lineType=cv2.LINE_AA,
            )

    return annotated_frame
