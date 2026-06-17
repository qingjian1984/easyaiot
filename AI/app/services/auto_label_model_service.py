"""
数据集自动标注模型：根据已标注数据微调 YOLO，并维护可配置条数的更新历史。
"""
from __future__ import annotations

import json
import logging
import os
import threading
from datetime import datetime

import requests

from db_models import db, AutoLabelModelHistory, Model, TrainTask
from app.services.auto_label_config import get_model_history_max
from app.services.auto_label_strategy import (
    DEFAULT_STRATEGY,
    merge_strategy,
    resolve_train_pretrained_path,
)
from app.services.auto_label_train_service import (
    export_annotated_yolo_zip,
    extract_map50,
    publish_train_to_model,
    split_dataset_usage,
    start_yolo_train,
    wait_train_task,
)

logger = logging.getLogger(__name__)

MIN_ANNOTATED_IMAGES = int(os.getenv('AUTO_LABEL_MODEL_MIN_ANNOTATED', '10'))
ACTIVE_STATUSES = ('PENDING', 'TRAINING')


def _java_base() -> str:
    return os.getenv(
        'JAVA_BACKEND_URL',
        os.getenv('GATEWAY_URL', 'http://localhost:48080'),
    ).rstrip('/')


def fetch_dataset_class_names(dataset_id: int) -> list[str]:
    """从 iot-dataset 标签列表读取类别名。"""
    resp = requests.get(
        f'{_java_base()}/admin-api/dataset/tag/page',
        params={'datasetId': dataset_id, 'pageNo': 1, 'pageSize': 500},
        timeout=30,
    )
    resp.raise_for_status()
    body = resp.json()
    if body.get('code') != 0:
        raise RuntimeError(body.get('msg') or '获取数据集标签失败')
    data = body.get('data') or {}
    tags = data.get('list') or []
    names = []
    seen = set()
    for tag in tags:
        name = (tag.get('name') or '').strip()
        if name and name not in seen:
            seen.add(name)
            names.append(name)
    return names


def count_annotated_images(dataset_id: int) -> int:
    from app.blueprints.auto_label import _fetch_all_dataset_images

    images = _fetch_all_dataset_images(_java_base(), dataset_id)
    count = 0
    for img in images:
        if img.get('completed') in (1, True, '1'):
            count += 1
            continue
        ann = img.get('annotations')
        if ann and ann not in ('[]', 'null', None):
            try:
                parsed = json.loads(ann) if isinstance(ann, str) else ann
                if isinstance(parsed, list) and len(parsed) > 0:
                    count += 1
            except Exception:
                pass
    return count


def get_dataset_bound_model_id(dataset_id: int) -> int | None:
    resp = requests.get(
        f'{_java_base()}/admin-api/dataset/get',
        params={'id': dataset_id},
        timeout=15,
    )
    resp.raise_for_status()
    body = resp.json()
    if body.get('code') != 0:
        return None
    data = body.get('data') or {}
    raw = data.get('modelServiceId') or data.get('modelId')
    try:
        return int(raw) if raw else None
    except (TypeError, ValueError):
        return None


def set_dataset_auto_label_model(dataset_id: int, model_id: int) -> None:
    resp = requests.post(
        f'{_java_base()}/admin-api/dataset/{dataset_id}/set-auto-label-model',
        json={'modelId': model_id},
        timeout=15,
    )
    resp.raise_for_status()
    body = resp.json()
    if body.get('code') not in (0, 200, None):
        raise RuntimeError(body.get('msg') or '绑定自动标注模型失败')


def _next_version_no(dataset_id: int) -> int:
    last = (
        AutoLabelModelHistory.query.filter_by(dataset_id=dataset_id)
        .order_by(AutoLabelModelHistory.version_no.desc())
        .first()
    )
    return (last.version_no + 1) if last else 1


def prune_model_history(
    dataset_id: int,
    *,
    max_records: int | None = None,
) -> None:
    cap = get_model_history_max(max_records)
    rows = (
        AutoLabelModelHistory.query.filter_by(dataset_id=dataset_id)
        .order_by(AutoLabelModelHistory.created_at.desc())
        .all()
    )
    if len(rows) <= cap:
        return
    for row in rows[cap:]:
        db.session.delete(row)
    db.session.commit()


def create_history_record(
    dataset_id: int,
    *,
    annotated_count: int,
    class_names: list[str],
    source_model_id: int | None,
    trigger_source: str = 'manual',
) -> AutoLabelModelHistory:
    active = AutoLabelModelHistory.query.filter(
        AutoLabelModelHistory.dataset_id == dataset_id,
        AutoLabelModelHistory.status.in_(ACTIVE_STATUSES),
    ).first()
    if active:
        raise RuntimeError('该数据集已有进行中的模型更新任务，请稍后再试')

    record = AutoLabelModelHistory(
        dataset_id=dataset_id,
        version_no=_next_version_no(dataset_id),
        annotated_count=annotated_count,
        class_names=json.dumps(class_names, ensure_ascii=False),
        source_model_id=source_model_id,
        status='PENDING',
        trigger_source=trigger_source,
    )
    db.session.add(record)
    db.session.commit()
    return record


def record_pipeline_model_update(
    dataset_id: int,
    *,
    model_id: int,
    train_task_id: int,
    annotated_count: int,
    class_names: list[str] | None,
    source_model_id: int | None,
    map50: float | None,
    history_max: int | None = None,
) -> AutoLabelModelHistory:
    """智能标注流水线自动训练完成后写入历史并绑定数据集模型。"""
    record = AutoLabelModelHistory(
        dataset_id=dataset_id,
        model_id=model_id,
        train_task_id=train_task_id,
        source_model_id=source_model_id,
        version_no=_next_version_no(dataset_id),
        annotated_count=annotated_count,
        class_names=json.dumps(class_names or [], ensure_ascii=False),
        map50=map50,
        status='COMPLETED',
        trigger_source='pipeline',
        completed_at=datetime.utcnow(),
    )
    db.session.add(record)
    db.session.commit()
    prune_model_history(dataset_id, max_records=history_max)
    try:
        set_dataset_auto_label_model(dataset_id, model_id)
    except Exception as e:
        logger.warning('绑定数据集自动标注模型失败: %s', e)
    return record


def list_model_history(
    dataset_id: int,
    *,
    limit: int | None = None,
    max_records: int | None = None,
) -> list[dict]:
    cap = get_model_history_max(max_records)
    effective_limit = cap if limit is None else min(max(1, int(limit)), cap)
    rows = (
        AutoLabelModelHistory.query.filter_by(dataset_id=dataset_id)
        .order_by(AutoLabelModelHistory.created_at.desc())
        .limit(effective_limit)
        .all()
    )
    return [r.to_dict() for r in rows]


def run_dataset_model_update(
    history_id: int,
    app,
    *,
    strategy_raw: dict | None = None,
) -> None:
    """后台执行：导出 → 训练 → 发布 → 绑定数据集模型。"""
    zip_path = None
    with app.app_context():
        record = AutoLabelModelHistory.query.get(history_id)
        if not record:
            return
        dataset_id = int(record.dataset_id)
        try:
            record.status = 'TRAINING'
            db.session.commit()

            class_names = json.loads(record.class_names or '[]')
            strategy = merge_strategy({**DEFAULT_STRATEGY, **(strategy_raw or {})})

            try:
                split_dataset_usage(dataset_id, 0.7, 0.2, 0.1)
            except Exception as e:
                logger.warning('划分用途失败（继续导出）: %s', e)

            zip_path = export_annotated_yolo_zip(
                dataset_id,
                text_prompts=class_names,
                export_prefix=f'auto_label_model_{dataset_id}_v{record.version_no}',
            )

            source_id = record.source_model_id
            arch_or_path, _ = resolve_train_pretrained_path(
                strategy,
                current_model_id=source_id,
                round_no=record.version_no,
            )
            train_task = start_yolo_train(
                dataset_zip_path=zip_path,
                dataset_id=dataset_id,
                round_no=record.version_no,
                strategy=strategy,
                pretrained_arch=arch_or_path,
            )
            record.train_task_id = train_task.id
            db.session.commit()

            finished = wait_train_task(train_task.id)
            if not finished or finished.status != 'completed':
                raise RuntimeError(
                    finished.error_message if finished and finished.error_message
                    else '模型训练失败或超时'
                )

            map50 = extract_map50(finished)
            publish_id = source_id or get_dataset_bound_model_id(dataset_id)
            model_name = f'auto-label-ds{dataset_id}'
            model_id = publish_train_to_model(
                finished,
                name=model_name,
                published_model_id=publish_id,
                class_names=class_names or None,
                model_origin='auto_label',
                origin_ref=f'dataset:{dataset_id}',
            )

            set_dataset_auto_label_model(dataset_id, model_id)

            record.model_id = model_id
            record.map50 = map50
            record.status = 'COMPLETED'
            record.completed_at = datetime.utcnow()
            record.error_message = None
            db.session.commit()
            prune_model_history(
                dataset_id,
                max_records=strategy.get('model_history_max'),
            )
            logger.info(
                '数据集 %s 自动标注模型更新完成 history=%s model_id=%s map50=%s',
                dataset_id, record.id, model_id, map50,
            )
        except Exception as e:
            logger.error('自动标注模型更新失败 history=%s: %s', history_id, e, exc_info=True)
            record = AutoLabelModelHistory.query.get(history_id)
            if record:
                record.status = 'FAILED'
                record.error_message = str(e)
                record.completed_at = datetime.utcnow()
                db.session.commit()
        finally:
            if zip_path and os.path.exists(zip_path):
                try:
                    os.unlink(zip_path)
                except OSError:
                    pass


def start_dataset_model_update(
    dataset_id: int,
    app,
    *,
    base_model_id: int | None = None,
    strategy_raw: dict | None = None,
) -> AutoLabelModelHistory:
    """校验并异步启动模型更新，返回历史记录。"""
    class_names = fetch_dataset_class_names(dataset_id)
    if not class_names:
        raise ValueError('数据集尚未配置标签，请先在标注工具中添加类别')

    annotated = count_annotated_images(dataset_id)
    if annotated < MIN_ANNOTATED_IMAGES:
        raise ValueError(
            f'已标注图片不足 {MIN_ANNOTATED_IMAGES} 张（当前 {annotated} 张），'
            '请先完成更多手工或自动标注后再更新模型'
        )

    source_id = base_model_id or get_dataset_bound_model_id(dataset_id)
    record = create_history_record(
        dataset_id,
        annotated_count=annotated,
        class_names=class_names,
        source_model_id=source_id,
        trigger_source='manual',
    )
    thread = threading.Thread(
        target=run_dataset_model_update,
        args=(record.id, app),
        kwargs={'strategy_raw': strategy_raw},
        daemon=True,
    )
    thread.start()
    return record
