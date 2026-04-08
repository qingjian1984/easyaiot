"""
@author 翱翔的雄库鲁
@email andywebjava@163.com
@wechat EasyAIoT2025
"""
from flask import Blueprint, request, jsonify, send_file
from pathlib import Path
import logging
import time
from threading import Lock
from urllib.parse import unquote, parse_qs, urlparse
from app.services.alert_service import (
    get_alert_list,
    get_alert_count,
    create_alert,
    get_dashboard_statistics,
    clear_all_alerts,
    clear_alerts_by_task_name
)
from app.services.alert_hook_service import process_alert_hook

# 创建Alert蓝图
alert_bp = Blueprint('alert', __name__)
logger = logging.getLogger(__name__)

# 请求去重缓存：避免短时间内重复查询
_query_cache = {}
_cache_lock = Lock()
_cache_ttl = 5  # 缓存有效期5秒


def api_response(code=200, message="success", data=None):
    """统一API响应格式"""
    response = {
        "code": code,
        "message": message,
        "data": data
    }
    return jsonify(response), code


@alert_bp.route('/page')
def get_alert_list_route():
    """获取报警列表"""
    try:
        args_dict = dict(request.args)
        result = get_alert_list(args_dict)
        return api_response(data=result)
    except Exception as e:
        logger.error(f'获取报警列表失败: {str(e)}')
        return api_response(500, f'获取失败: {str(e)}')


@alert_bp.route('/count')
def get_alert_count_route():
    """获取报警统计"""
    try:
        args_dict = dict(request.args)
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
    """获取报警录像"""
    try:
        path = request.args.get('path')
        if not path:
            return api_response(400, '路径参数不能为空')
        
        file_path = Path(path)
        if not file_path.exists():
            return api_response(400, f'文件不存在: {path}')
        
        return send_file(str(file_path))
    except Exception as e:
        logger.error(f'获取报警录像失败: {str(e)}')
        return api_response(500, f'获取失败: {str(e)}')


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
        elif result.get('status') == 'skipped':
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
        time_range = int(request.args.get('time_range', 300))  # 默认前后300秒（5分钟）
        
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
        
    # 解析告警时间
    from datetime import datetime, timedelta
    try:
        alert_time = datetime.strptime(alert_time_str, '%Y-%m-%d %H:%M:%S')
    except ValueError:
        return api_response(400, '告警时间格式错误，应为：YYYY-MM-DD HH:MM:SS')
    
    # 计算时间范围（扩大范围以包含更多可能的录像）
    # 考虑到录像可能有duration，需要扩大查询范围
    extended_range = time_range + 600  # 额外增加10分钟
    start_time = alert_time - timedelta(seconds=extended_range)
    end_time = alert_time + timedelta(seconds=extended_range)
    
    # 查询Playback表中匹配的录像
    # 先查询device_id匹配且event_time在扩展时间范围内的所有录像
    from models import Playback
    candidate_playbacks = Playback.query.filter(
        Playback.device_id == device_id,
        Playback.event_time >= start_time,
        Playback.event_time <= end_time
    ).all()
    
    # 在Python中过滤：匹配告警时间在录像时间段内的录像
    # 录像时间段：event_time 到 event_time + duration
    matched_playbacks = []
    for playback in candidate_playbacks:
        playback_start = playback.event_time
        # 处理时区：统一转换为naive datetime进行比较
        if playback_start.tzinfo is not None:
            playback_start = playback_start.replace(tzinfo=None)
        
        playback_end = playback_start + timedelta(seconds=playback.duration or 0)
        
        # 检查告警时间是否在录像的时间段内
        if playback_start <= alert_time <= playback_end:
            matched_playbacks.append((playback, 0))  # 完全匹配，优先级最高
        # 或者检查录像的event_time是否接近告警时间（兼容旧逻辑）
        elif abs((playback_start - alert_time).total_seconds()) <= time_range:
            time_diff = abs((playback_start - alert_time).total_seconds())
            matched_playbacks.append((playback, time_diff))
    
    # 按时间差排序，选择最接近告警时间的录像
    if matched_playbacks:
        matched_playbacks.sort(key=lambda x: x[1])  # 按时间差排序
        playbacks = [p[0] for p in matched_playbacks]
    else:
        playbacks = []
    
    if not playbacks:
        # 使用debug级别避免重复警告日志
        logger.debug(f'未找到匹配的录像 device_id={device_id}, alert_time={alert_time_str}, time_range={time_range}, candidate_count={len(candidate_playbacks)}')
        # 返回友好的提示信息，使用200状态码但code字段表示业务错误（400表示业务错误）
        if len(candidate_playbacks) == 0:
            return jsonify({
                "code": 400,
                "message": f'该设备在告警时间前后{time_range}秒内暂无录像记录，请稍后再试',
                "data": None
            }), 200
        else:
            return jsonify({
                "code": 400,
                "message": f'未找到告警时间点对应的录像，建议扩大时间范围查询',
                "data": None
            }), 200
    
    # 取最接近告警时间的录像
    playback = playbacks[0]
    
    # 直接返回数据库中的录像地址，不检查文件是否存在
    # 前台会自己去下载播放
    file_path = playback.file_path
    video_url = file_path
    
    # 如果file_path是MinIO API路径格式（/api/v1/buckets/...），直接返回
    # 如果file_path是完整URL（http://或https://），直接返回
    # 如果file_path是本地路径，也直接返回，由前台处理
    # 不再检查文件是否存在，直接返回数据库中的地址
    
    return api_response(200, 'success', {
        'playback_id': playback.id,
        'file_path': playback.file_path,
        'video_url': video_url,
        'event_time': playback.event_time.isoformat() if playback.event_time else None,
        'duration': playback.duration,
        'device_id': playback.device_id,
        'device_name': playback.device_name
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



