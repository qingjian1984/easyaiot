"""
SAM 万物识别 Flask 蓝图
"""
import json
import logging
import os
import tempfile

from flask import Blueprint, request, jsonify
from werkzeug.utils import secure_filename

from app.services.sam_service import get_sam_service, SamService, SAM_ENABLED, SAM_MAX_IMAGE_BYTES
from app.utils.sam_model_download import get_sam_model_status, start_sam_model_download
from db_models import db, SAMInferenceResult

logger = logging.getLogger(__name__)

sam_bp = Blueprint('sam', __name__)


def _ok(data=None, msg='success'):
    return jsonify({'code': 0, 'msg': msg, 'data': data or {}})


def _err(code, msg, http_status=400):
    return jsonify({'code': code, 'msg': msg}), http_status


def _parse_predict_params():
    """从 JSON 或 multipart 解析推理参数。"""
    if request.content_type and 'multipart/form-data' in request.content_type:
        text_raw = request.form.get('text')
        bboxes_raw = request.form.get('bboxes')
        params = {
            'return_masks': request.form.get('return_masks', 'true').lower() in ('1', 'true', 'yes'),
            'conf': float(request.form.get('conf', 0.45)),
            'save_result': request.form.get('save_result', 'false').lower() in ('1', 'true', 'yes'),
            'text': json.loads(text_raw) if text_raw else None,
            'bboxes': json.loads(bboxes_raw) if bboxes_raw else None,
        }
        image = request.files.get('file')
        return params, image
    data = request.json or {}
    params = {
        'text': data.get('text'),
        'bboxes': data.get('bboxes'),
        'return_masks': data.get('return_masks', True),
        'conf': float(data.get('conf', 0.45)),
        'save_result': data.get('save_result', False),
        'image_base64': data.get('image_base64'),
        'image_url': data.get('image_url'),
    }
    return params, None


def _load_image_from_request(params, file_obj):
    svc = get_sam_service()
    if file_obj and file_obj.filename:
        raw = file_obj.read()
        if len(raw) > SAM_MAX_IMAGE_BYTES:
            raise ValueError('图片超过大小限制')
        return svc.decode_image_bytes(raw)
    if params.get('image_base64'):
        return svc.decode_base64_image(params['image_base64'])
    if params.get('image_url'):
        return svc.download_image_url(params['image_url'])
    raise ValueError('请提供 file、image_base64 或 image_url')


@sam_bp.route('/model/status', methods=['GET'])
def sam_model_status():
    try:
        return _ok(get_sam_model_status())
    except Exception as e:
        logger.error('SAM model status failed: %s', e)
        return _err(500, str(e), 500)


@sam_bp.route('/model/download', methods=['POST'])
def sam_model_download():
    try:
        data = start_sam_model_download()
        return _ok(data, msg=data.get('message', 'success'))
    except Exception as e:
        logger.error('SAM model download failed: %s', e)
        return _err(500, str(e), 500)


@sam_bp.route('/health', methods=['GET'])
def health_check():
    svc = get_sam_service()
    try:
        status = 'healthy' if svc.enabled else 'disabled'
        if svc.enabled and not svc.model_loaded and not os.getenv('SAM_WORKER_URL'):
            status = 'standby'
        return jsonify({
            'status': status,
            'engine': os.getenv('SAM_ENGINE', 'sam3'),
            'model_loaded': svc.model_loaded,
            'device': svc.get_device(),
            'enabled': svc.enabled,
        }), 200 if status in ('healthy', 'disabled', 'standby') else 503
    except Exception as e:
        logger.error('SAM health check failed: %s', e)
        return jsonify({'status': 'unhealthy', 'message': str(e)}), 500


@sam_bp.route('/predict', methods=['POST'])
def predict():
    svc = get_sam_service()
    if not svc.enabled:
        return _err(503, 'SAM 未启用，请设置 SAM_ENABLED=true', 503)
    try:
        params, file_obj = _parse_predict_params()
        text = params.get('text')
        bboxes = params.get('bboxes')
        if text and bboxes:
            return _err(400, 'text 与 bboxes 不能同时指定')
        if not text and not bboxes:
            return _err(400, '请提供 text 或 bboxes')

        image = _load_image_from_request(params, file_obj)
        result = svc.predict(
            image,
            text=text,
            bboxes=bboxes,
            return_masks=params['return_masks'],
            conf=params['conf'],
        )

        if params.get('save_result'):
            prompt_type = result.get('prompt_type', 'text' if text else 'box')
            prompt_data = json.dumps({'text': text} if text else {'bboxes': bboxes}, ensure_ascii=False)
            record = SAMInferenceResult(
                prompt_type=prompt_type,
                prompt_data=prompt_data,
                image_url=params.get('image_url'),
                result_data=json.dumps(result, ensure_ascii=False),
                model_type=os.getenv('SAM_ENGINE', 'sam3'),
                inference_ms=result.get('inference_ms'),
            )
            db.session.add(record)
            db.session.commit()
            result['record_id'] = record.id

        return _ok(result)
    except TimeoutError as e:
        return _err(504, str(e), 504)
    except ValueError as e:
        return _err(400, str(e), 400)
    except Exception as e:
        logger.error('SAM predict failed: %s', e, exc_info=True)
        return _err(500, f'推理失败: {e}', 500)


@sam_bp.route('/history', methods=['GET'])
def history():
    try:
        page = int(request.args.get('page', 1))
        page_size = min(int(request.args.get('page_size', 20)), 100)
        prompt_type = request.args.get('prompt_type')

        q = SAMInferenceResult.query
        if prompt_type:
            q = q.filter_by(prompt_type=prompt_type)
        pagination = q.order_by(SAMInferenceResult.created_at.desc()).paginate(
            page=page, per_page=page_size, error_out=False
        )
        return _ok({
            'list': [r.to_dict() for r in pagination.items],
            'total': pagination.total,
            'page': page,
            'page_size': page_size,
        })
    except Exception as e:
        logger.error('SAM history failed: %s', e, exc_info=True)
        return _err(500, str(e), 500)
