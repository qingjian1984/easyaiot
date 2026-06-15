"""
iot-node 控制面客户端：节点调度与工作负载远程部署。
"""
import logging
import os
from typing import Any, Dict, List, Optional

import requests

logger = logging.getLogger(__name__)

JAVA_BACKEND_URL = os.getenv('JAVA_BACKEND_URL', 'http://localhost:48080').rstrip('/')
NODE_API_BASE = f'{JAVA_BACKEND_URL}/admin-api/node'
REQUEST_TIMEOUT = 90


def _headers() -> Dict[str, str]:
    headers = {'Content-Type': 'application/json'}
    token = os.getenv('JWT_TOKEN') or ''
    if not token:
        try:
            from flask import has_request_context, request as flask_request
            if has_request_context():
                token = flask_request.headers.get('X-Authorization', '').replace('Bearer ', '')
        except Exception:
            pass
    if token:
        headers['X-Authorization'] = f'Bearer {token}'
    return headers


def _post(path: str, payload: Dict[str, Any]) -> Dict[str, Any]:
    url = f'{NODE_API_BASE}{path}'
    resp = requests.post(url, json=payload, headers=_headers(), timeout=REQUEST_TIMEOUT)
    resp.raise_for_status()
    data = resp.json()
    if data.get('code') != 0:
        msg = data.get('msg') or data.get('message') or f'节点 API 失败: {url}'
        if msg == '系统异常':
            logger.error(
                '节点 API 返回系统异常（详见 iot-node 日志）url=%s payload_keys=%s resp=%s',
                url, list(payload.keys()), data,
            )
        raise RuntimeError(msg)
    return data.get('data') or {}


def _get(path: str, params: Dict[str, Any]) -> Dict[str, Any]:
    url = f'{NODE_API_BASE}{path}'
    resp = requests.get(url, params=params, headers=_headers(), timeout=REQUEST_TIMEOUT)
    resp.raise_for_status()
    data = resp.json()
    if data.get('code') != 0:
        raise RuntimeError(data.get('msg') or f'节点 API 失败: {url}')
    return data.get('data') or {}


def is_remote_deploy_enabled() -> bool:
    return os.getenv('NODE_REMOTE_DEPLOY', 'true').lower() in ('1', 'true', 'yes')


def allocate_node(
    workload_type: str,
    workload_id: str,
    capabilities: Optional[List[str]] = None,
    gpu_count: int = 0,
    region: Optional[str] = None,
    sticky: bool = True,
    target_node_id: Optional[int] = None,
    exclude_node_ids: Optional[List[int]] = None,
) -> Dict[str, Any]:
    if target_node_id:
        node = get_node(target_node_id)
        return {
            'nodeId': target_node_id,
            'host': node.get('host'),
            'agentPort': node.get('agentPort', 9100),
            'gpuIds': _format_gpu_ids(node.get('maxGpuCount', 0)),
            'bindingId': None,
        }

    requirements: Dict[str, Any] = {
        'capabilities': capabilities or ['algorithm_realtime'],
        'gpuCount': gpu_count,
        'region': region,
    }
    if exclude_node_ids:
        requirements['excludeNodeIds'] = exclude_node_ids

    payload = {
        'workloadType': workload_type,
        'workloadId': workload_id,
        'sticky': sticky,
        'requirements': requirements,
    }
    return _post('/scheduler/allocate', payload)


def release_workload(workload_type: str, workload_id: str) -> None:
    url = f'{NODE_API_BASE}/scheduler/release'
    params = {
        'workloadType': workload_type,
        'workloadId': workload_id,
    }
    resp = requests.post(url, params=params, headers=_headers(), timeout=REQUEST_TIMEOUT)
    resp.raise_for_status()
    data = resp.json()
    if data.get('code') != 0:
        raise RuntimeError(data.get('msg') or '释放节点绑定失败')


def get_node(node_id: int) -> Dict[str, Any]:
    return _get('/get', {'id': node_id})


def get_platform_node_id() -> Optional[int]:
    """获取控制面节点 ID，用于调度时排除或降权本机。"""
    try:
        data = _get('/platform-agent-bootstrap', {})
        node_id = data.get('nodeId')
        return int(node_id) if node_id is not None else None
    except Exception as e:
        logger.debug('获取控制面节点 ID 失败: %s', e)
        return None


def deploy_media_stack(node_id: int, stack_type: str = 'srs_live') -> Dict[str, Any]:
    """通过 Agent 在目标节点部署 SRS/ZLM 媒体栈。"""
    payload = {
        'nodeId': node_id,
        'stackType': stack_type,
    }
    return _post('/media/deploy-stack', payload)


def deploy_workload(
    node_id: int,
    workload_type: str,
    workload_id: str,
    command: List[str],
    work_dir: str,
    log_dir: str,
    env: Dict[str, str],
    gpu_ids: Optional[str] = None,
) -> Dict[str, Any]:
    payload = {
        'nodeId': node_id,
        'workloadType': workload_type,
        'workloadId': workload_id,
        'command': command,
        'workDir': work_dir,
        'logDir': log_dir,
        'gpuIds': gpu_ids,
        'env': env,
    }
    return _post('/workload/deploy', payload)


def stop_workload(node_id: int, workload_type: str, workload_id: str) -> None:
    url = f'{NODE_API_BASE}/workload/stop'
    params = {
        'nodeId': node_id,
        'workloadType': workload_type,
        'workloadId': workload_id,
    }
    resp = requests.post(url, params=params, headers=_headers(), timeout=REQUEST_TIMEOUT)
    resp.raise_for_status()
    data = resp.json()
    if data.get('code') != 0:
        raise RuntimeError(data.get('msg') or '停止远程工作负载失败')


def _format_gpu_ids(max_gpu_count: int) -> Optional[str]:
    if not max_gpu_count or max_gpu_count <= 0:
        return None
    return ','.join(str(i) for i in range(max_gpu_count))
