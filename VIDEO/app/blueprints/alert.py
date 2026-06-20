"""
@author 翱翔的雄库鲁
@email andywebjava@163.com
@wechat EasyAIoT2025
"""
from flask import Blueprint, request, jsonify, send_file
from pathlib import Path
import logging
import time
from datetime import datetime, timedelta, timezone
from threading import Lock
from urllib.parse import unquote, parse_qs, urlparse
from app.services.alert_service import (
    get_alert_list,
    get_alert_count,
    create_alert,
    get_dashboard_statistics,
    clear_all_alerts,
    clear_alerts_by_task_name,
    get_correlation_events,
)
from app.utils.service_urls import parse_alert_time_str, normalize_to_shanghai_naive
from app.services.alert_hook_service import process_alert_hook

# 创建Alert蓝图
alert_bp = Blueprint('alert', __name__)
logger = logging.getLogger(__name__)

# 请求去重缓存：避免短时间内重复查询
_query_cache = {}
_cache_lock = Lock()
_cache_ttl = 5  # 缓存有效期5秒


def _parse_alert_time_str(alert_time_str: str):
    return parse_alert_time_str(alert_time_str)


def _to_shanghai_naive(value):
    return normalize_to_shanghai_naive(value)


def api_response(code=200, message="success", data=None):
    """统一 API 响应格式（与改造后的前端 axios 解析一致：业务 code 成功为 0，HTTP 统一 200）"""
    business_code = 0 if code == 200 else code
    response = {
        "code": business_code,
        "msg": message,
        "message": message,
        "data": data
    }
    return jsonify(response), 200


@alert_bp.route('/page')
def get_alert_list_route():
    """获取报警列表"""
    try:
        args_dict = {}
        for key, value in request.args.items():
            if isinstance(value, list):
                args_dict[key] = value[0] if value else None
            else:
                args_dict[key] = value

        logger.debug(f'告警列表查询参数: {args_dict}')
        result = get_alert_list(args_dict)
        return api_response(data=result)
    except Exception as e:
        logger.error(f'获取报警列表失败: {str(e)}', exc_info=True)
        return api_response(500, f'获取失败: {str(e)}')


@alert_bp.route('/correlation', methods=['GET'])
def get_correlation_events_route():
    """按 correlation_id 查询同一帧关联的告警、人脸匹配、车牌匹配记录"""
    try:
        correlation_id = request.args.get('correlation_id') or request.args.get('correlationId')
        if not correlation_id:
            return api_response(400, 'correlation_id 不能为空')
        result = get_correlation_events(correlation_id)
        return api_response(data=result)
    except ValueError as e:
        return api_response(400, str(e))
    except Exception as e:
        logger.error(f'查询关联事件失败: {str(e)}', exc_info=True)
        return api_response(500, f'查询失败: {str(e)}')


@alert_bp.route('/count')
def get_alert_count_route():
    """获取报警统计"""
    try:
        args_dict = {}
        for key, value in request.args.items():
            if isinstance(value, list):
                args_dict[key] = value[0] if value else None
            else:
                args_dict[key] = value
        result = get_alert_count(args_dict)
        return api_response(data=result)
    except Exception as e:
        logger.error(f'获取报警统计失败: {str(e)}')
        return api_response(500, f'获取失败: {str(e)}')


@alert_bp.route('/statistics', methods=['GET'])
def get_dashboard_statistics_route():
    """获取仪表板统计信息（统一接口）"""
    try:
        result = get_dashboard_statistics()
        return api_response(data=result)
    except Exception as e:
        logger.error(f'获取仪表板统计信息失败: {str(e)}')
        return api_response(500, f'获取失败: {str(e)}')


@alert_bp.route('/image')
def get_alert_image():
    """获取报警图片（支持本地文件和MinIO存储）"""
    try:
        path = request.args.get('path')
        if not path:
            return api_response(400, '路径参数不能为空')
        
        # 检查是否是MinIO下载URL格式（/api/v1/buckets/{bucket_name}/objects/download?prefix=...）
        if path.startswith('/api/v1/buckets/') and '/objects/download' in path:
            try:
                from app.services.minio_service import ModelService
                from minio.error import S3Error
                from io import BytesIO
                
                # 解析URL：/api/v1/buckets/{bucket_name}/objects/download?prefix={object_name}
                parsed = urlparse(path)
                query_params = parse_qs(parsed.query)
                
                # 提取bucket_name和object_name
                path_parts = parsed.path.split('/')
                if len(path_parts) < 5 or path_parts[1] != 'api' or path_parts[2] != 'v1' or path_parts[3] != 'buckets':
                    return api_response(400, f'MinIO URL格式错误: {path}')
                
                bucket_name = path_parts[4]
                prefix = query_params.get('prefix', [None])[0]
                
                if not prefix:
                    return api_response(400, f'MinIO URL缺少prefix参数: {path}')
                
                # URL解码prefix
                object_name = unquote(prefix)
                
                # 获取MinIO客户端
                minio_client = ModelService.get_minio_client()
                
                # 检查存储桶是否存在
                if not minio_client.bucket_exists(bucket_name):
                    return api_response(400, f'MinIO存储桶不存在: {bucket_name}')
                
                # 从MinIO获取对象
                try:
                    stat = minio_client.stat_object(bucket_name, object_name)
                    data = minio_client.get_object(bucket_name, object_name)
                    content = data.read()
                    data.close()
                    data.release_conn()
                    
                    # 返回文件内容
                    from flask import Response
                    return Response(
                        content,
                        mimetype=stat.content_type or 'image/jpeg',
                        headers={
                            'Content-Disposition': f'inline; filename={object_name.split("/")[-1]}'
                        }
                    )
                except S3Error as e:
                    if e.code == 'NoSuchKey':
                        return api_response(400, f'MinIO对象不存在: {object_name}')
                    raise
            except Exception as e:
                logger.error(f'从MinIO获取报警图片失败: {str(e)}', exc_info=True)
                return api_response(500, f'从MinIO获取失败: {str(e)}')
        else:
            # 本地文件路径
            file_path = Path(path)
            if not file_path.exists():
                return api_response(400, f'文件不存在: {path}')
            
            return send_file(str(file_path))
    except Exception as e:
        logger.error(f'获取报警图片失败: {str(e)}')
        return api_response(500, f'获取失败: {str(e)}')


@alert_bp.route('/record')
def get_alert_record():
    """获取报警录像（支持本地文件 Range 请求）"""
    try:
        from urllib.parse import unquote

        from app.services.media_dvr_utils import resolve_playback_absolute_path

        path = unquote((request.args.get('path') or '').strip())
        if not path:
            return jsonify({'code': 400, 'message': '路径参数不能为空', 'data': None}), 400

        file_path = resolve_playback_absolute_path(path)
        if not file_path or not Path(file_path).exists():
            logger.warning('告警录像不存在 path=%s resolved=%s', path, file_path)
            return jsonify({'code': 404, 'message': f'文件不存在: {path}', 'data': None}), 404

        ext = Path(file_path).suffix.lower()
        mimetype_map = {
            '.flv': 'video/x-flv',
            '.mp4': 'video/mp4',
            '.ts': 'video/mp2t',
            '.mkv': 'video/x-matroska',
        }
        mimetype = mimetype_map.get(ext, 'application/octet-stream')
        return send_file(
            str(file_path),
            mimetype=mimetype,
            conditional=True,
            as_attachment=False,
        )
    except Exception as e:
        logger.error(f'获取报警录像失败: {str(e)}')
        return jsonify({'code': 500, 'message': f'获取失败: {str(e)}', 'data': None}), 500


@alert_bp.route('/hook', methods=['POST'])
def alert_hook():
    """告警Hook接口：接收告警事件并发送到Kafka"""
    try:
        data = request.get_json()
        if not data:
            return api_response(400, '请求数据不能为空')
        
        # 调用告警Hook服务处理
        result = process_alert_hook(data)
        
        if result.get('status') == 'success':
            return api_response(200, '告警事件已发送', result)
        elif result.get('status') in ('skipped', 'suppressed'):
            return api_response(200, '告警事件已跳过', result)
        else:
            return api_response(500, f"告警事件处理失败: {result.get('error', '未知错误')}", result)
    except Exception as e:
        logger.error(f'处理告警Hook失败: {str(e)}', exc_info=True)
        return api_response(500, f'处理失败: {str(e)}')


@alert_bp.route('/record/query', methods=['GET'])
def query_alert_record():
    """根据告警时间和设备ID查询对应的录像
    
    参数:
        device_id: 设备ID（必填）
        alert_time: 告警时间，格式：'YYYY-MM-DD HH:MM:SS'（必填）
        time_range: 时间范围（秒），默认300秒，用于查找告警时间前后范围内的录像
    """
    try:
        device_id = request.args.get('device_id')
        alert_time_str = request.args.get('alert_time')
        alert_id = request.args.get('alert_id')
        time_range = int(request.args.get('time_range', 300))  # 默认前后300秒（5分钟）

        # 已回写 record_path 时直接返回（on_dvr -> patch_alerts_record）
        if alert_id:
            try:
                from urllib.parse import quote

                from models import Alert
                from app.services.alert_service import is_minio_download_path
                from app.utils.service_urls import is_local_filesystem_path, minio_storage_enabled

                alert_row = Alert.query.get(int(alert_id))
                record_path = (alert_row.record_path or '').strip() if alert_row else ''
                if record_path:
                    if is_minio_download_path(record_path):
                        return api_response(200, 'success', {
                            'video_url': record_path,
                            'file_path': record_path,
                            'device_id': alert_row.device_id,
                            'source': 'alert_record_path',
                        })
                    if not minio_storage_enabled() and is_local_filesystem_path(record_path):
                        api_path = f'/video/alert/record?path={quote(record_path, safe="")}'
                        return api_response(200, 'success', {
                            'video_url': api_path,
                            'file_path': record_path,
                            'device_id': alert_row.device_id,
                            'source': 'alert_record_path',
                        })
            except (TypeError, ValueError):
                pass
        
        if not device_id:
            return api_response(400, '设备ID不能为空')
        if not alert_time_str:
            return api_response(400, '告警时间不能为空')
        
        # 请求去重：检查是否在短时间内有相同的请求
        cache_key = f"{device_id}:{alert_time_str}:{time_range}"
        current_time = time.time()
        
        with _cache_lock:
            # 清理过期的缓存
            expired_keys = [k for k, (_, timestamp) in _query_cache.items() 
                          if current_time - timestamp > _cache_ttl]
            for key in expired_keys:
                _query_cache.pop(key, None)
            
            # 检查是否有相同的请求在缓存中
            if cache_key in _query_cache:
                cached_result, cached_timestamp = _query_cache[cache_key]
                if current_time - cached_timestamp < _cache_ttl:
                    logger.debug(f'使用缓存结果，避免重复查询 cache_key={cache_key}')
                    return cached_result
        
        # 执行查询
        try:
            result = _do_query_alert_record(device_id, alert_time_str, time_range)
            
            # 缓存结果（只缓存400错误，避免重复查询）
            if result[1] == 400:  # result是(Response, status_code)元组
                with _cache_lock:
                    _query_cache[cache_key] = (result, current_time)
            
            return result
        except Exception as e:
            logger.error(f'查询告警录像失败: {str(e)}', exc_info=True)
            return api_response(500, f'查询失败: {str(e)}')
    except Exception as e:
        logger.error(f'查询告警录像失败: {str(e)}', exc_info=True)
        return api_response(500, f'查询失败: {str(e)}')


def _do_query_alert_record(device_id, alert_time_str, time_range):
    """执行实际的查询逻辑"""
    from app.services.alert_service import find_playback_for_alert

    alert_time_aware, err = _parse_alert_time_str(alert_time_str)
    if err:
        return api_response(400, err)

    playback = find_playback_for_alert(device_id, alert_time_aware, time_range)
    if not playback:
        logger.debug(
            '未找到匹配的录像 device_id=%s, alert_time=%s, time_range=%s',
            device_id, alert_time_str, time_range,
        )
        return jsonify({
            "code": 400,
            "message": f'该设备在告警时间前后{time_range}秒内暂无录像记录，请稍后再试',
            "data": None
        }), 200

    file_path = playback.file_path
    return api_response(200, 'success', {
        'playback_id': playback.id,
        'file_path': file_path,
        'video_url': file_path,
        'event_time': playback.event_time.isoformat() if playback.event_time else None,
        'duration': playback.duration,
        'device_id': playback.device_id,
        'device_name': playback.device_name,
        'source': 'playback_match',
    })


@alert_bp.route('/clear', methods=['DELETE'])
def clear_alerts_by_task_name_route():
    """清空任务的所有告警记录（通过task_name）"""
    try:
        task_name = request.args.get('task_name')
        if not task_name:
            return api_response(400, 'task_name参数不能为空')

        result = clear_alerts_by_task_name(task_name)
        return api_response(200, 'success', result)
    except ValueError as e:
        return api_response(400, str(e))
    except Exception as e:
        logger.error(f'清空任务告警失败: {str(e)}', exc_info=True)
        return api_response(500, f'清空失败: {str(e)}')


@alert_bp.route('/clear/all', methods=['DELETE'])
def clear_all_alerts_route():
    """清空所有告警记录"""
    try:
        result = clear_all_alerts()
        return api_response(200, 'success', result)
    except Exception as e:
        logger.error(f'清空所有告警失败: {str(e)}', exc_info=True)
        return api_response(500, f'清空失败: {str(e)}')



