"""
@author 翱翔的雄库鲁
@email andywebjava@163.com
@wechat EasyAIoT2025
自动化标注功能蓝图
"""
import os
import json
import logging
import tempfile
import threading
from datetime import datetime
import requests
from flask import Blueprint, request, jsonify, Response
from sqlalchemy import text

from db_models import db, AutoLabelTask, AutoLabelResult, AIService
from app.services.cluster_inference_service import ClusterInferenceService
from app.services.minio_service import ModelService

auto_label_bp = Blueprint('auto_label', __name__)
logger = logging.getLogger(__name__)

# 与 Java PageParam.PAGE_SIZE_MAX 保持一致
_DATASET_IMAGE_PAGE_SIZE = 1000


def _fetch_all_dataset_images(java_backend_url: str, dataset_id: int, extra_params: dict | None = None) -> list:
    """分页拉取数据集全部图片（单页最多 1000 条）"""
    import requests

    all_images: list = []
    page_no = 1
    extra_params = extra_params or {}

    while True:
        params = {
            'datasetId': dataset_id,
            'pageNo': page_no,
            'pageSize': _DATASET_IMAGE_PAGE_SIZE,
            **extra_params,
        }
        response = requests.get(
            f"{java_backend_url}/admin-api/dataset/image/page",
            params=params,
            timeout=60,
        )
        if response.status_code != 200:
            raise RuntimeError(f'获取图片列表失败: HTTP {response.status_code}, {response.text}')

        body = response.json()
        if body.get('code') != 0:
            raise RuntimeError(f'获取图片列表失败: {body.get("msg")}')

        data = body.get('data') or {}
        batch = data.get('list') or []
        total = data.get('total') or 0
        all_images.extend(batch)

        if not batch or len(all_images) >= total or len(batch) < _DATASET_IMAGE_PAGE_SIZE:
            break
        page_no += 1

    return all_images


def _dataset_java_base() -> str:
    return os.getenv('JAVA_BACKEND_URL', 'http://localhost:8080').rstrip('/')


def _dataset_annotation_url(dataset_id: int, suffix: str) -> str:
    return f"{_dataset_java_base()}/admin-api/dataset/{dataset_id}/annotation/{suffix}"


def _forward_request_headers() -> dict:
    headers = {}
    for key in ('Authorization', 'X-Authorization', 'tenant-id'):
        val = request.headers.get(key)
        if val:
            headers[key] = val
    return headers


def _proxy_dataset_json_response(resp: requests.Response):
    try:
        body = resp.json()
    except ValueError:
        return jsonify({'code': 500, 'msg': resp.text or '数据集服务响应异常'}), 500
    if resp.ok:
        return jsonify(body), resp.status_code
    msg = body.get('msg') if isinstance(body, dict) else str(body)
    return jsonify({'code': body.get('code', resp.status_code) if isinstance(body, dict) else resp.status_code, 'msg': msg}), resp.status_code


@auto_label_bp.route('/dataset/<int:dataset_id>/auto-label/start', methods=['POST'])
def start_auto_label(dataset_id):
    """启动自动化标注任务"""
    try:
        data = request.json or {}
        model_service_id = data.get('model_service_id')
        confidence_threshold = float(data.get('confidence_threshold', 0.5))
        
        if not model_service_id:
            return jsonify({'code': 400, 'msg': '请选择AI服务'}), 400
        
        # 验证AI服务是否存在
        ai_service = AIService.query.get(model_service_id)
        if not ai_service:
            return jsonify({'code': 404, 'msg': 'AI服务不存在'}), 404
        
        if ai_service.status != 'running':
            return jsonify({'code': 400, 'msg': 'AI服务未运行'}), 400
        
        # 创建标注任务
        task = AutoLabelTask(
            dataset_id=dataset_id,
            model_service_id=model_service_id,
            confidence_threshold=confidence_threshold,
            status='PENDING'
        )
        db.session.add(task)
        db.session.commit()
        
        # 异步执行标注任务（传递应用上下文）
        from flask import current_app
        app = current_app._get_current_object()
        thread = threading.Thread(target=execute_auto_label_task, args=(app, task.id))
        thread.daemon = True
        thread.start()

        return jsonify({
            'code': 0,
            'msg': '自动化标注任务已启动',
            'data': {
                'task_id': task.id
            }
        })

    except Exception as e:
        logger.error(f"启动自动化标注任务失败: {str(e)}", exc_info=True)
        db.session.rollback()
        return jsonify({'code': 500, 'msg': f'启动任务失败: {str(e)}'}), 500


@auto_label_bp.route('/dataset/<int:dataset_id>/auto-label/task/<int:task_id>', methods=['GET'])
def get_auto_label_task(dataset_id, task_id):
    """获取自动化标注任务状态"""
    try:
        task = AutoLabelTask.query.filter_by(id=task_id, dataset_id=dataset_id).first()
        if not task:
            return jsonify({'code': 404, 'msg': '任务不存在'}), 404

        task_dict = task.to_dict()
        # 添加关联的AI服务信息
        if task.model_service:
            task_dict['model_service'] = task.model_service.to_dict()

        return jsonify({
            'code': 0,
            'msg': '获取成功',
            'data': task_dict
        })

    except Exception as e:
        logger.error(f"获取任务状态失败: {str(e)}", exc_info=True)
        return jsonify({'code': 500, 'msg': f'获取失败: {str(e)}'}), 500


@auto_label_bp.route('/dataset/<int:dataset_id>/auto-label/tasks', methods=['GET'])
def list_auto_label_tasks(dataset_id):
    """获取数据集的所有自动化标注任务列表"""
    try:
        page = int(request.args.get('page', 1))
        page_size = int(request.args.get('page_size', 10))

        tasks = AutoLabelTask.query.filter_by(dataset_id=dataset_id)\
            .order_by(AutoLabelTask.created_at.desc())\
            .paginate(page=page, per_page=page_size, error_out=False)

        # 构建任务列表，包含关联的AI服务信息
        task_list = []
        for task in tasks.items:
            task_dict = task.to_dict()
            if task.model_service:
                task_dict['model_service'] = task.model_service.to_dict()
            task_list.append(task_dict)

        return jsonify({
            'code': 0,
            'msg': '获取成功',
            'data': {
                'list': task_list,
                'total': tasks.total,
                'page': page,
                'page_size': page_size
            }
        })

    except Exception as e:
        logger.error(f"获取任务列表失败: {str(e)}", exc_info=True)
        return jsonify({'code': 500, 'msg': f'获取失败: {str(e)}'}), 500


@auto_label_bp.route('/dataset/<int:dataset_id>/auto-label/image/<int:image_id>', methods=['POST'])
def label_single_image(dataset_id, image_id):
    """单张图片AI标注"""
    try:
        data = request.json or {}
        model_service_id = data.get('model_service_id')
        confidence_threshold = float(data.get('confidence_threshold', 0.5))

        if not model_service_id:
            return jsonify({'code': 400, 'msg': '请选择AI服务'}), 400

        # 验证AI服务是否存在
        ai_service = AIService.query.get(model_service_id)
        if not ai_service:
            return jsonify({'code': 404, 'msg': 'AI服务不存在'}), 404

        if ai_service.status != 'running':
            return jsonify({'code': 400, 'msg': 'AI服务未运行'}), 400

        # 从Java后端获取图片信息
        import requests
        java_backend_url = os.getenv('JAVA_BACKEND_URL', 'http://localhost:8080')
        image_response = requests.get(
            f"{java_backend_url}/admin-api/dataset/image/get",
            params={'id': image_id},
            timeout=10
        )

        if image_response.status_code != 200:
            return jsonify({'code': 404, 'msg': '图片不存在'}), 404

        image_data = image_response.json()
        if image_data.get('code') != 0:
            return jsonify({'code': 404, 'msg': '获取图片信息失败'}), 404

        image_info = image_data.get('data', {})
        image_path = image_info.get('path')

        if not image_path:
            return jsonify({'code': 400, 'msg': '图片路径不存在'}), 400

        # 从MinIO下载图片到临时文件
        bucket_name, object_key = _parse_minio_path(image_path)
        if not bucket_name or not object_key:
            return jsonify({'code': 400, 'msg': '无法解析图片路径'}), 400

        temp_file = tempfile.NamedTemporaryFile(delete=False, suffix='.jpg')
        temp_file.close()

        success, error_msg = ModelService.download_from_minio(bucket_name, object_key, temp_file.name)
        if not success:
            return jsonify({'code': 500, 'msg': f'下载图片失败: {error_msg}'}), 500

        try:
            # 调用推理服务
            result = ClusterInferenceService.inference_via_cluster(
                model_id=ai_service.model_id,
                model_format=ai_service.format or 'onnx',
                model_version=ai_service.model_version or '1.0',
                file_path=temp_file.name,
                parameters={
                    'conf_thres': confidence_threshold,
                    'iou_thres': 0.45
                }
            )

            # 解析推理结果并转换为标注格式
            image_width = image_info.get('width', 0)
            image_height = image_info.get('heigh', 0)
            annotations = _parse_inference_result(result, image_width, image_height)

            # 更新Java后端的图片标注信息
            update_response = requests.put(
                f"{java_backend_url}/admin-api/dataset/image/update",
                json={
                    'id': image_id,
                    'datasetId': dataset_id,
                    'annotations': json.dumps(annotations, ensure_ascii=False),
                    'completed': 1 if annotations else 0
                },
                timeout=10
            )

            if update_response.status_code != 200:
                logger.warning(f"更新图片标注失败: {image_id}")
                return jsonify({'code': 500, 'msg': '更新图片标注失败'}), 500

            return jsonify({
                'code': 0,
                'msg': '标注成功',
                'data': {
                    'annotations': annotations,
                    'count': len(annotations)
                }
            })

        finally:
            # 清理临时文件
            if os.path.exists(temp_file.name):
                os.unlink(temp_file.name)

    except Exception as e:
        logger.error(f"单张图片AI标注失败: {str(e)}", exc_info=True)
        return jsonify({'code': 500, 'msg': f'标注失败: {str(e)}'}), 500


@auto_label_bp.route('/dataset/<int:dataset_id>/auto-label/export', methods=['POST'])
def export_labeled_dataset(dataset_id):
    """导出标注数据集（转发至 iot-dataset /annotation/export）"""
    try:
        data = request.json or {}
        if data.get('task_id'):
            logger.warning('export task_id 已由 iot-dataset 统一导出，旧参数 task_id 已忽略')
        export_format = (data.get('format') or 'yolo').lower()
        if export_format not in ('yolo', ''):
            return jsonify({'code': 400, 'msg': f'导出格式 {export_format} 已下线，请使用 YOLO 导出'}), 400
        body = {
            'trainRatio': float(data.get('train_ratio', 0.7)),
            'valRatio': float(data.get('val_ratio', 0.2)),
            'testRatio': float(data.get('test_ratio', 0.1)),
            'sampleSelection': data.get('sample_selection') or data.get('sample_type', 'all'),
            'selectedClasses': data.get('selected_classes') or [],
            'exportPrefix': data.get('export_prefix') or data.get('file_prefix') or '',
        }
        if not body['selectedClasses']:
            return jsonify({'code': 400, 'msg': '请至少选择一个导出类别'}), 400
        resp = requests.post(
            _dataset_annotation_url(dataset_id, 'export'),
            json=body,
            headers=_forward_request_headers(),
            timeout=1800,
            stream=True,
        )
        if not resp.ok:
            return _proxy_dataset_json_response(resp)
        headers = {
            k: v for k, v in resp.headers.items()
            if k.lower() in ('content-type', 'content-disposition', 'content-length')
        }
        return Response(resp.iter_content(chunk_size=8192), status=resp.status_code, headers=headers)
    except Exception as e:
        logger.error(f"导出数据集失败: {str(e)}", exc_info=True)
        return jsonify({'code': 500, 'msg': f'导出失败: {str(e)}'}), 500


@auto_label_bp.route('/dataset/<int:dataset_id>/extract-frames', methods=['POST'])
def extract_frames_from_video(dataset_id):
    """视频抽帧（转发至 iot-dataset /annotation/extract-frames）"""
    try:
        if 'file' not in request.files:
            return jsonify({'code': 400, 'msg': '未找到视频文件'}), 400
        file = request.files['file']
        if not file or file.filename == '':
            return jsonify({'code': 400, 'msg': '未选择视频文件'}), 400
        frame_interval = request.form.get('frame_interval', '30')
        resp = requests.post(
            _dataset_annotation_url(dataset_id, 'extract-frames'),
            files={'file': (file.filename, file.stream, file.content_type or 'video/mp4')},
            data={'frame_interval': frame_interval},
            headers=_forward_request_headers(),
            timeout=1800,
        )
        return _proxy_dataset_json_response(resp)
    except Exception as e:
        logger.error(f"视频抽帧失败: {str(e)}", exc_info=True)
        return jsonify({'code': 500, 'msg': f'抽帧失败: {str(e)}'}), 500


@auto_label_bp.route('/dataset/<int:dataset_id>/import-labelme', methods=['POST'])
def import_labelme_dataset(dataset_id):
    """导入 LabelMe（转发至 iot-dataset /annotation/import-labelme）"""
    try:
        files = request.files.getlist('files')
        if not files:
            return jsonify({'code': 400, 'msg': '未选择文件'}), 400
        multipart = []
        for f in files:
            multipart.append(('files', (f.filename, f.stream, f.content_type or 'application/octet-stream')))
        resp = requests.post(
            _dataset_annotation_url(dataset_id, 'import-labelme'),
            files=multipart,
            headers=_forward_request_headers(),
            timeout=1800,
        )
        if not resp.ok:
            return _proxy_dataset_json_response(resp)
        body = resp.json()
        data = body.get('data') if isinstance(body, dict) else {}
        images = (data or {}).get('imagesCopied') or (data or {}).get('imported_count') or 0
        return jsonify({
            'code': 0,
            'msg': body.get('msg') or f'导入完成，共导入 {images} 个文件',
            'data': {'imported_count': images, **(data or {})},
        })
    except Exception as e:
        logger.error(f"导入labelme数据集失败: {str(e)}", exc_info=True)
        return jsonify({'code': 500, 'msg': f'导入失败: {str(e)}'}), 500


def execute_auto_label_task(app, task_id):
    """执行自动化标注任务（异步）"""
    task = None
    # 在应用上下文中执行所有数据库操作
    with app.app_context():
        try:
            task = AutoLabelTask.query.get(task_id)
            if not task:
                logger.error(f"任务不存在: {task_id}")
                return

            task.status = 'PROCESSING'
            task.started_at = datetime.now()
            db.session.commit()

            # 获取AI服务
            ai_service = AIService.query.get(task.model_service_id)
            if not ai_service:
                raise Exception('AI服务不存在')

            # 从Java后端获取数据集图片列表
            import requests

            java_backend_url = os.getenv('JAVA_BACKEND_URL', 'http://localhost:8080')
            logger.info(f"开始获取数据集图片列表: dataset_id={task.dataset_id}")

            images = _fetch_all_dataset_images(java_backend_url, task.dataset_id)
            logger.info(f"获取图片列表完成，共 {len(images)} 张")
            task.total_images = len(images)
            db.session.commit()

            logger.info(f"数据集 {task.dataset_id} 共有 {len(images)} 张图片待处理")

            success_count = 0
            failed_count = 0

            # 处理每张图片
            for idx, image in enumerate(images):
                try:
                    image_id = image.get('id')
                    image_path = image.get('path')  # MinIO路径

                    if not image_path:
                        logger.warning(f"图片 {image_id} 没有路径，跳过")
                        failed_count += 1
                        continue

                    # 调用AI服务进行推理
                    # 从MinIO下载图片到临时文件
                    bucket_name, object_key = _parse_minio_path(image_path)
                    if not bucket_name or not object_key:
                        logger.warning(f"无法解析图片路径: {image_path}")
                        failed_count += 1
                        continue

                    temp_file = tempfile.NamedTemporaryFile(delete=False, suffix='.jpg')
                    temp_file.close()

                    success, error_msg = ModelService.download_from_minio(bucket_name, object_key, temp_file.name)
                    if not success:
                        logger.error(f"下载图片失败: {error_msg}")
                        failed_count += 1
                        continue

                    # **关键修复**: 从下载的图片文件中读取实际尺寸
                    try:
                        from PIL import Image as PILImage
                        with PILImage.open(temp_file.name) as img:
                            image_width, image_height = img.size
                        logger.info(f"图片 {image_id} 实际尺寸: {image_width}x{image_height}")
                    except Exception as e:
                        logger.error(f"读取图片尺寸失败: {str(e)}")
                        failed_count += 1
                        if os.path.exists(temp_file.name):
                            os.unlink(temp_file.name)
                        continue

                    # 调用推理服务
                    result = ClusterInferenceService.inference_via_cluster(
                        model_id=ai_service.model_id,
                        model_format=ai_service.format or 'onnx',
                        model_version=ai_service.model_version or '1.0',
                        file_path=temp_file.name,
                        parameters={
                            'conf_thres': task.confidence_threshold,
                            'iou_thres': 0.45
                        }
                    )

                    # 解析推理结果并转换为标注格式（使用实际读取的图片尺寸）
                    annotations = _parse_inference_result(result, image_width, image_height)

                    # 保存标注结果
                    label_result = AutoLabelResult(
                        task_id=task_id,
                        dataset_image_id=image_id,
                        annotations=json.dumps(annotations, ensure_ascii=False),
                        status='SUCCESS'
                    )
                    db.session.add(label_result)

                    # 更新Java后端的图片标注信息
                    update_response = requests.put(
                        f"{java_backend_url}/admin-api/dataset/image/update",
                        json={
                            'id': image_id,
                            'datasetId': task.dataset_id,
                            'annotations': json.dumps(annotations, ensure_ascii=False),
                            'completed': 1 if annotations else 0
                        },
                        timeout=10
                    )

                    if update_response.status_code != 200:
                        logger.warning(f"更新图片标注失败: {image_id}")

                    success_count += 1

                    # 清理临时文件
                    if os.path.exists(temp_file.name):
                        os.unlink(temp_file.name)

                except Exception as e:
                    logger.error(f"处理图片失败: {str(e)}", exc_info=True)
                    failed_count += 1

                    # 记录失败结果
                    label_result = AutoLabelResult(
                        task_id=task_id,
                        dataset_image_id=image.get('id', 0),
                        status='FAILED',
                        error_message=str(e)
                    )
                    db.session.add(label_result)

                # 更新进度
                task.processed_images = idx + 1
                task.success_count = success_count
                task.failed_count = failed_count
                db.session.commit()

            # 完成任务
            task.status = 'COMPLETED'
            task.completed_at = datetime.now()
            db.session.commit()

            logger.info(f"自动化标注任务完成: task_id={task_id}, success={success_count}, failed={failed_count}")

        except Exception as e:
            logger.error(f"执行自动化标注任务失败: {str(e)}", exc_info=True)
            if task:
                task.status = 'FAILED'
                task.error_message = str(e)
                task.completed_at = datetime.now()
                db.session.commit()


def _parse_minio_path(path):
    """解析MinIO路径，返回bucket和object_key"""
    # 格式: /api/v1/buckets/{bucket}/objects/download?prefix={object_key}
    from urllib.parse import urlparse, parse_qs

    try:
        parsed = urlparse(path)
        path_parts = parsed.path.split('/')

        if len(path_parts) >= 5 and path_parts[3] == 'buckets':
            bucket_name = path_parts[4]
        else:
            return None, None

        query_params = parse_qs(parsed.query)
        object_key = query_params.get('prefix', [None])[0]

        return bucket_name, object_key
    except Exception as e:
        logger.error(f"解析MinIO路径失败: {path}, 错误: {str(e)}")
        return None, None


def _parse_inference_result(result, image_width, image_height):
    """解析推理结果，转换为标注格式（归一化坐标 + {x,y}格式）"""
    annotations = []

    try:
        if not result:
            return annotations

        # 检查返回格式：可能是 {'code': 0, 'data': {...}} 或直接是 data
        if isinstance(result, dict) and 'code' in result:
            if result.get('code') != 0:
                logger.warning(f"推理返回错误: {result.get('msg', '未知错误')}")
                return annotations
            predictions = result.get('data', {}).get('predictions', [])
        elif isinstance(result, dict) and 'data' in result:
            predictions = result.get('data', {}).get('predictions', [])
        elif isinstance(result, dict) and 'predictions' in result:
            predictions = result.get('predictions', [])
        else:
            logger.warning(f"无法识别的推理结果格式: {type(result)}")
            return annotations

        # 验证图片尺寸
        if not image_width or not image_height or image_width <= 0 or image_height <= 0:
            logger.error(f"图片尺寸无效: width={image_width}, height={image_height}")
            return annotations

        for pred in predictions:
            try:
                # 推理结果格式: {'class': int, 'class_name': str, 'confidence': float, 'bbox': [x1, y1, x2, y2]}
                class_id = pred.get('class')
                class_name = pred.get('class_name', '')
                confidence = float(pred.get('confidence', 0))
                bbox = pred.get('bbox', [])

                # 如果没有class_name，尝试使用class_id
                if not class_name and class_id is not None:
                    class_name = str(class_id)

                if not class_name or not bbox or len(bbox) < 4:
                    continue

                x1, y1, x2, y2 = bbox[:4]

                # 确保坐标在图片范围内
                x1 = max(0, min(x1, image_width))
                y1 = max(0, min(y1, image_height))
                x2 = max(0, min(x2, image_width))
                y2 = max(0, min(y2, image_height))

                # **关键修复**: 将像素坐标归一化为0-1范围
                norm_x1 = x1 / image_width
                norm_y1 = y1 / image_height
                norm_x2 = x2 / image_width
                norm_y2 = y2 / image_height

                # **关键修复**: 转换为前端期望的格式 - 归一化坐标 + {x, y}对象格式 + label字段
                annotation = {
                    'label': class_name,  # 使用label字段而不是class，匹配手动标注
                    'confidence': confidence,
                    'points': [
                        {'x': norm_x1, 'y': norm_y1},
                        {'x': norm_x2, 'y': norm_y1},
                        {'x': norm_x2, 'y': norm_y2},
                        {'x': norm_x1, 'y': norm_y2}
                    ],
                    'type': 'rectangle',
                    'auto': True,
                    'color': '#52c41a'  # AI标注使用绿色
                }
                annotations.append(annotation)

                logger.info(f"转换标注: {class_name}, 像素[{x1:.1f},{y1:.1f},{x2:.1f},{y2:.1f}] -> 归一化[{norm_x1:.3f},{norm_y1:.3f},{norm_x2:.3f},{norm_y2:.3f}]")

            except Exception as e:
                logger.error(f"解析单个预测结果失败: {str(e)}, pred: {pred}")
                continue
            
    except Exception as e:
        logger.error(f"解析推理结果失败: {str(e)}", exc_info=True)
    
    return annotations
