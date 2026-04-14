"""
为 Ultralytics 结果图与本地 PIL 绘制提供中文字体支持。

Ultralytics 在非 ASCII 类别名时会使用 check_font('Arial.Unicode.ttf')，并优先读取
USER_CONFIG_DIR / 'Arial.Unicode.ttf'。将该文件指向系统里的 CJK 字体（复制即可），
即可在结果图上正常绘制中文类别名，且无需外网下载。
"""
from __future__ import annotations

import logging
import os
import shutil
from functools import lru_cache
from pathlib import Path
from typing import List, Optional

logger = logging.getLogger(__name__)

_ARIAL_UNICODE_NAME = "Arial.Unicode.ttf"


def _ultralytics_user_config_dir() -> Optional[Path]:
    try:
        from ultralytics.utils import USER_CONFIG_DIR

        return Path(USER_CONFIG_DIR)
    except Exception as e:
        logger.debug("无法导入 ultralytics USER_CONFIG_DIR: %s", e)
        return None


def _collect_font_candidates() -> List[Path]:
    env = os.environ.get("YOLO_RESULT_FONT_PATH") or os.environ.get("ULTRALYTICS_PLOT_FONT")
    cands: List[Path] = []
    if env:
        p = Path(env).expanduser()
        if p.is_file():
            cands.append(p)
    win = os.environ.get("WINDIR", r"C:\Windows")
    cands.extend(
        [
            Path(win) / "Fonts" / "msyh.ttc",
            Path(win) / "Fonts" / "msyhbd.ttc",
            Path(win) / "Fonts" / "simhei.ttf",
            Path(win) / "Fonts" / "simsun.ttc",
            Path(win) / "Fonts" / "msjh.ttc",
            Path(win) / "Fonts" / "arialuni.ttf",
            Path(win) / "Fonts" / "Arial Unicode.ttf",
        ]
    )
    cands.extend(
        [
            Path("/usr/share/fonts/truetype/noto/NotoSansCJK-Regular.ttc"),
            Path("/usr/share/fonts/opentype/noto/NotoSansCJK-Regular.ttc"),
            Path("/usr/share/fonts/truetype/wqy/wqy-microhei.ttc"),
            Path("/usr/share/fonts/truetype/wqy/wqy-zenhei.ttc"),
            Path("/usr/share/fonts/truetype/droid/DroidSansFallbackFull.ttf"),
        ]
    )
    cands.extend(
        [
            Path("/System/Library/Fonts/PingFang.ttc"),
            Path("/System/Library/Fonts/STHeiti Light.ttc"),
            Path("/System/Library/Fonts/Supplemental/Arial Unicode.ttf"),
        ]
    )
    return cands


def ensure_ultralytics_chinese_plot_font(min_bytes: int = 50_000) -> bool:
    """
    将本机或环境变量指定的 CJK 字体复制到 Ultralytics 配置目录下的 Arial.Unicode.ttf，
    供 Annotator / result.save() 使用。

    可通过环境变量 YOLO_RESULT_FONT_PATH（或 ULTRALYTICS_PLOT_FONT）指定任意 .ttf/.ttc。

    Returns:
        若配置目录下已存在可用的 Arial.Unicode.ttf 则返回 True。
    """
    cfg = _ultralytics_user_config_dir()
    if not cfg:
        return False
    try:
        cfg.mkdir(parents=True, exist_ok=True)
    except OSError as e:
        logger.warning("无法创建 Ultralytics 配置目录 %s: %s", cfg, e)
        return False

    dest = cfg / _ARIAL_UNICODE_NAME
    if dest.is_file() and dest.stat().st_size >= min_bytes:
        return True

    for src in _collect_font_candidates():
        if not src.is_file():
            continue
        try:
            shutil.copy2(src, dest)
            logger.info("已安装中文标注字体映射: %s -> %s", src, dest)
            return True
        except OSError as e:
            logger.warning("复制字体失败 %s -> %s: %s", src, dest, e)
            continue

    logger.warning(
        "未找到可用的中文字体；结果图中文可能无法显示。请设置环境变量 YOLO_RESULT_FONT_PATH "
        "指向 .ttf/.ttc，或将 Arial.Unicode.ttf 放入 Ultralytics 用户配置目录。"
    )
    return False


def _pil_text_size(font, text: str) -> tuple[int, int]:
    bbox = font.getbbox(text)
    return bbox[2] - bbox[0], bbox[3] - bbox[1]


@lru_cache(maxsize=8)
def get_pil_annotation_font(size: int = 14):
    """加载与 Ultralytics 相同路径下的中文字体，供 ONNX 等 PIL 绘制使用。"""
    from PIL import ImageFont

    ensure_ultralytics_chinese_plot_font()
    cfg = _ultralytics_user_config_dir()
    if not cfg:
        return None
    dest = cfg / _ARIAL_UNICODE_NAME
    if not dest.is_file():
        return None
    try:
        return ImageFont.truetype(str(dest), size)
    except OSError as e:
        logger.warning("PIL 无法加载字体 %s: %s", dest, e)
        return None


def draw_utf8_label_on_bgr(
    img,
    text: str,
    org_xy: tuple[int, int],
    font_size: int = 14,
    text_color_rgb: tuple[int, int, int] = (0, 0, 0),
) -> bool:
    """
    在 BGR 图像上绘制 UTF-8 文本（使用 PIL）。成功返回 True，失败返回 False（调用方可退回 cv2.putText）。
    org_xy 为文字左上角坐标（与 cv2.putText 对齐意图）。
    """
    import cv2
    import numpy as np
    from PIL import Image, ImageDraw

    font = get_pil_annotation_font(font_size)
    if font is None:
        return False

    h, w = img.shape[:2]
    tw, th = _pil_text_size(font, text)
    lx, ly = int(org_xy[0]), int(org_xy[1])
    pad = 2
    x1, y1 = lx - pad, ly - pad
    x2, y2 = lx + tw + pad, ly + th + pad
    x1c, y1c = max(0, x1), max(0, y1)
    x2c, y2c = min(w, x2), min(h, y2)
    if x2c <= x1c or y2c <= y1c:
        return False

    roi = img[y1c:y2c, x1c:x2c]
    roi_rgb = cv2.cvtColor(roi, cv2.COLOR_BGR2RGB)
    pil_im = Image.fromarray(roi_rgb)
    draw = ImageDraw.Draw(pil_im)
    tx, ty = lx - x1c, ly - y1c
    draw.text((tx, ty), text, font=font, fill=text_color_rgb)
    img[y1c:y2c, x1c:x2c] = cv2.cvtColor(np.asarray(pil_im), cv2.COLOR_RGB2BGR)
    return True
