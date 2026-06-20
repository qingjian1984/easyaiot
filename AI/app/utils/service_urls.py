"""
部署形态解析（mini 形态 vs 完整网关形态）。
"""
from __future__ import annotations

import os

_MINI_PROFILES = frozenset({'mini', '1', 'minimal', '4g'})
_NON_MINI_PROFILES = frozenset({'standard', '2', 'std', '16g', 'full', '3', 'complete'})
_MINI_SYSTEM_PORTS = frozenset({'48099'})


def is_mini_deploy_profile() -> bool:
    profile = (os.getenv('EASYAIOT_DEPLOY_PROFILE') or '').strip().lower()
    if profile in _NON_MINI_PROFILES:
        return False
    if profile in _MINI_PROFILES:
        return True
    # 未显式声明形态时，根据 mini 直连端口推断（iot-system:48099）
    for key in ('GATEWAY_URL', 'JAVA_BACKEND_URL'):
        url = (os.getenv(key) or '').strip().rstrip('/')
        if url and any(url.endswith(f':{port}') for port in _MINI_SYSTEM_PORTS):
            return True
    return False


def minio_storage_enabled() -> bool:
    """mini 形态默认不部署 MinIO，对象存储走本地目录。"""
    explicit = (os.getenv('MINIO_ENABLED') or '').strip().lower()
    if explicit in ('1', 'true', 'yes', 'on'):
        return True
    if explicit in ('0', 'false', 'no', 'off'):
        return False
    return not is_mini_deploy_profile()
