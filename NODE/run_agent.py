#!/usr/bin/env python3
"""
EasyAIoT Node Agent - 工作节点代理
- HTTP 服务：接收部署/停止指令（端口 9100）
- 控制面通道：注册 + 心跳
"""
import logging
import os
import platform
import socket
import sys
import threading
import time
from typing import Any, Dict, List

_repo_root = os.path.abspath(os.path.join(os.path.dirname(os.path.abspath(__file__)), '..'))
_lib_root = os.path.join(_repo_root, 'lib')
for _p in (_repo_root, _lib_root):
    if _p not in sys.path:
        sys.path.insert(0, _p)

import psutil
import requests

logging.basicConfig(
    level=logging.INFO,
    format='%(asctime)s [%(levelname)s] %(message)s',
)
logger = logging.getLogger('easyaiot-node-agent')

NODE_ID = int(os.environ.get('NODE_ID', '0'))
AGENT_TOKEN = os.environ.get('AGENT_TOKEN', '')
CONTROL_PLANE_URL = os.environ.get(
    'CONTROL_PLANE_URL', 'http://localhost:48080/admin-api/node/agent'
).rstrip('/')
HEARTBEAT_INTERVAL = int(os.environ.get('HEARTBEAT_INTERVAL', '10'))
AGENT_VERSION = '1.0.0'
AGENT_ENV_FILE = os.environ.get('AGENT_ENV_FILE', '')
BOOTSTRAP_WAIT_SECONDS = int(os.environ.get('BOOTSTRAP_WAIT_SECONDS', '180'))
BOOTSTRAP_RETRY_INTERVAL = int(os.environ.get('BOOTSTRAP_RETRY_INTERVAL', '3'))


def _detect_platform_agent() -> bool:
  """仅当 agent.env 显式设置 PLATFORM_AGENT=1 时才视为控制面宿主机 Agent。"""
  explicit = os.environ.get('PLATFORM_AGENT', '').strip().lower()
  return explicit in ('1', 'true', 'yes')


PLATFORM_AGENT = _detect_platform_agent()


def bootstrap_url() -> str:
    base = CONTROL_PLANE_URL.rstrip('/')
    if base.endswith('/agent'):
        base = base[:-len('/agent')]
    return f'{base}/platform-agent-bootstrap'


def persist_credentials() -> None:
    env_path = AGENT_ENV_FILE
    if not env_path:
        for candidate in (
            '/opt/easyaiot/node-agent/agent.env',
            os.path.join(os.path.dirname(os.path.abspath(__file__)), 'agent.env'),
        ):
            if os.path.isfile(candidate):
                env_path = candidate
                break
    if not env_path or not os.path.isfile(env_path):
        return
    try:
        with open(env_path, encoding='utf-8') as f:
            lines = f.readlines()
        with open(env_path, 'w', encoding='utf-8') as f:
            for line in lines:
                if line.startswith('NODE_ID='):
                    f.write(f'NODE_ID={NODE_ID}\n')
                elif line.startswith('AGENT_TOKEN='):
                    f.write(f'AGENT_TOKEN={AGENT_TOKEN}\n')
                else:
                    f.write(line)
        logger.info('已同步凭据至 %s', env_path)
    except OSError as e:
        logger.debug('无法持久化 agent.env: %s', e)


def try_refresh_credentials(*, allow_node_id_change: bool = False) -> bool:
    global NODE_ID, AGENT_TOKEN
    url = bootstrap_url()
    try:
        resp = requests.get(url, timeout=10)
        if resp.status_code != 200:
            logger.debug('bootstrap 请求失败 HTTP %s: %s', resp.status_code, url)
            return False
        data = resp.json()
        if data.get('code') != 0:
            logger.debug('bootstrap 返回错误: %s', data.get('msg', data))
            return False
        payload = data.get('data') or data
        new_id = payload.get('nodeId')
        new_token = payload.get('agentToken')
        if not new_id or not new_token:
            return False
        new_id = int(new_id)
        can_change_id = allow_node_id_change or PLATFORM_AGENT
        if NODE_ID and NODE_ID != new_id and not can_change_id:
            logger.debug('bootstrap nodeId=%s 与当前 NODE_ID=%s 不一致，跳过刷新', new_id, NODE_ID)
            return False
        if new_token == AGENT_TOKEN and new_id == NODE_ID:
            return True
        if NODE_ID and NODE_ID != new_id:
            logger.info('bootstrap 同步 nodeId %s -> %s', NODE_ID, new_id)
        NODE_ID = new_id
        AGENT_TOKEN = new_token
        logger.info('已从 bootstrap 刷新凭据 nodeId=%s', NODE_ID)
        persist_credentials()
        return True
    except Exception as e:
        logger.debug('bootstrap 刷新失败: %s', e)
    return False


def is_credential_error(msg: str) -> bool:
    if not msg:
        return False
    text = str(msg)
    if '节点不存在' in text:
        return True
    if '令牌' in text:
        return True
    lowered = text.lower()
    return 'token' in lowered and 'invalid' in lowered


def wait_for_platform_credentials() -> None:
    """控制面 Agent 启动时等待 iot-node bootstrap 就绪并同步凭据。"""
    if not PLATFORM_AGENT:
        return
    deadline = time.time() + BOOTSTRAP_WAIT_SECONDS
    logger.info('控制面 Agent 等待 bootstrap 就绪（最长 %ss）...', BOOTSTRAP_WAIT_SECONDS)
    while time.time() < deadline:
        if try_refresh_credentials(allow_node_id_change=True):
            return
        time.sleep(BOOTSTRAP_RETRY_INTERVAL)
    logger.warning('bootstrap 等待超时，将使用本地 agent.env 继续尝试注册')


def get_gpu_info() -> List[Dict[str, Any]]:
    gpus: List[Dict[str, Any]] = []
    try:
        import subprocess
        result = subprocess.run(
            ['nvidia-smi', '--query-gpu=index,name,utilization.gpu,memory.used,memory.total',
             '--format=csv,noheader,nounits'],
            capture_output=True, text=True, timeout=5,
        )
        if result.returncode == 0:
            for line in result.stdout.strip().split('\n'):
                if not line.strip():
                    continue
                parts = [p.strip() for p in line.split(',')]
                if len(parts) >= 5:
                    gpus.append({
                        'id': int(parts[0]),
                        'name': parts[1],
                        'util': float(parts[2]) if parts[2] != '[N/A]' else 0,
                        'mem_used_mb': float(parts[3]) if parts[3] != '[N/A]' else 0,
                        'mem_total_mb': float(parts[4]) if parts[4] != '[N/A]' else 0,
                    })
    except Exception as e:
        logger.debug('GPU 采集跳过: %s', e)
    return gpus


def collect_metrics() -> Dict[str, Any]:
    from workload_manager import WorkloadManager
    # 使用模块级 manager（agent_server 中创建）
    try:
        from agent_server import manager as workload_mgr
        active_tasks = workload_mgr.active_count()
        workloads = workload_mgr.list_workloads()
    except Exception:
        active_tasks = 0
        workloads = []

    # 整机平均利用率 0–100%，与系统监视器一致（勿对各核求和，否则会随逻辑核数虚高）
    cpu = round(psutil.cpu_percent(interval=0.5), 2)
    mem = psutil.virtual_memory()
    disk = psutil.disk_usage('/')
    metrics = {
        'cpuPercent': cpu,
        'memPercent': round(mem.percent, 2),
        'memUsedBytes': mem.used,
        'memTotalBytes': mem.total,
        'diskPercent': round(disk.percent, 2),
        'diskUsedBytes': disk.used,
        'diskTotalBytes': disk.total,
        'activeTasks': active_tasks,
        'bandwidthMbps': 0,
        'gpuInfo': get_gpu_info(),
        'workloads': workloads,
    }
    try:
        from cluster_storage import get_mount_root, is_cluster_mode, verify_ceph_mount
        metrics['clusterMode'] = is_cluster_mode()
        metrics['cephMountRoot'] = get_mount_root()
        metrics['cephMountReady'] = verify_ceph_mount()
    except ImportError:
        pass
    return metrics


def post_json(path: str, payload: Dict[str, Any], *, allow_refresh: bool = True) -> bool:
    url = f'{CONTROL_PLANE_URL}{path}'
    try:
        resp = requests.post(url, json=payload, timeout=10)
        if resp.status_code == 200:
            data = resp.json()
            if data.get('code') == 0:
                return True
            msg = data.get('msg', data)
            if allow_refresh and is_credential_error(str(msg)):
                node_missing = '节点不存在' in str(msg)
                if try_refresh_credentials(
                    allow_node_id_change=node_missing or PLATFORM_AGENT,
                ):
                    payload = {**payload, 'nodeId': NODE_ID, 'agentToken': AGENT_TOKEN}
                    return post_json(path, payload, allow_refresh=False)
            logger.warning('请求失败 %s: %s', url, msg)
        else:
            logger.warning('HTTP %s: %s', resp.status_code, url)
    except Exception as e:
        logger.warning('请求异常 %s: %s', url, e)
    return False


def register() -> bool:
    payload = {
        'nodeId': NODE_ID,
        'agentToken': AGENT_TOKEN,
        'hostname': socket.gethostname(),
        'osInfo': f'{platform.system()} {platform.release()}',
        'agentVersion': AGENT_VERSION,
    }
    logger.info('注册节点 nodeId=%s -> %s', NODE_ID, CONTROL_PLANE_URL)
    return post_json('/register', payload)


def heartbeat() -> bool:
    metrics = collect_metrics()
    payload = {'nodeId': NODE_ID, 'agentToken': AGENT_TOKEN, **metrics}
    return post_json('/heartbeat', payload)


def heartbeat_loop():
    while not register():
        logger.info('注册失败，5 秒后重试...')
        time.sleep(5)
    logger.info('注册成功，开始心跳 (interval=%ss)', HEARTBEAT_INTERVAL)
    while True:
        if not heartbeat():
            logger.warning('心跳失败，尝试重新注册...')
            register()
        time.sleep(HEARTBEAT_INTERVAL)


def _wait_for_control_plane() -> None:
    """iot-node 重启后再次尝试 bootstrap，避免使用过期凭据上报。"""
    if not PLATFORM_AGENT:
        return
    deadline = time.time() + min(BOOTSTRAP_WAIT_SECONDS, 60)
    url = bootstrap_url()
    while time.time() < deadline:
        if try_refresh_credentials(allow_node_id_change=True):
            return
        try:
            resp = requests.get(url, timeout=5)
            if resp.status_code == 200 and (resp.json() or {}).get('code') == 0:
                try_refresh_credentials(allow_node_id_change=True)
                return
        except Exception:
            pass
        time.sleep(BOOTSTRAP_RETRY_INTERVAL)


def main():
    wait_for_platform_credentials()
    _wait_for_control_plane()
    if not NODE_ID or not AGENT_TOKEN:
        logger.error('请设置环境变量 NODE_ID 和 AGENT_TOKEN')
        sys.exit(1)

    from agent_server import run_server  # noqa: F401 - 先加载 HTTP 服务与 workload manager

    threading.Thread(target=heartbeat_loop, daemon=True).start()
    run_server()


if __name__ == '__main__':
    main()
