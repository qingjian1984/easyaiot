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

    cpu_per = psutil.cpu_percent(interval=0.5, percpu=True)
    cpu = round(sum(cpu_per), 2) if cpu_per else 0.0
    mem = psutil.virtual_memory()
    disk = psutil.disk_usage('/')
    return {
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


def post_json(path: str, payload: Dict[str, Any]) -> bool:
    url = f'{CONTROL_PLANE_URL}{path}'
    try:
        resp = requests.post(url, json=payload, timeout=10)
        if resp.status_code == 200:
            data = resp.json()
            if data.get('code') == 0:
                return True
            logger.warning('请求失败 %s: %s', url, data.get('msg', data))
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


def main():
    if not NODE_ID or not AGENT_TOKEN:
        logger.error('请设置环境变量 NODE_ID 和 AGENT_TOKEN')
        sys.exit(1)

    from agent_server import run_server  # noqa: F401 - 先加载 HTTP 服务与 workload manager

    threading.Thread(target=heartbeat_loop, daemon=True).start()
    run_server()


if __name__ == '__main__':
    main()
