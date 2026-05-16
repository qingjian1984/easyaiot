"""
MinIO 工具函数，与 easyaiot/VIDEO 工程中的用法对齐：
- endpoint：同 ``services/*/run_deploy.py`` 中 ``_normalize_minio_endpoint``（host:port，去掉 http(s)://）
- 客户端：同 ``app/blueprints/camera.py`` 的 ``get_minio_client``（secure 支持 bool 或字符串）
- 上传/下载：同 ``app/services/minio_service.py`` 的 ``ModelService``（stat / ensure_bucket / fget / fput）
"""
from __future__ import annotations

import os
from typing import Any, Optional

try:
    from minio import Minio
    from minio.error import S3Error
except ImportError:  # pragma: no cover
    Minio = None  # type: ignore[misc, assignment]

    class S3Error(Exception):  # type: ignore[no-redef]
        pass


def normalize_minio_endpoint(raw: str) -> str:
    """MinIO Python 客户端要求 host:port，去掉误配的 scheme（与 VIDEO run_deploy 一致）。"""
    s = (raw or '').strip()
    for prefix in ('https://', 'http://'):
        if s.lower().startswith(prefix):
            s = s[len(prefix):]
    return s.rstrip('/')


def coerce_minio_secure(secure: Any) -> bool:
    """与 VIDEO camera.get_minio_client 一致：支持布尔或 'true'/'false' 字符串。"""
    if isinstance(secure, bool):
        return secure
    return str(secure).lower() == 'true'


def get_minio_client(
    endpoint: str,
    access_key: str,
    secret_key: str,
    secure: Any = False,
) -> 'Minio':
    """
    创建 MinIO 客户端（与 VIDEO ``camera.get_minio_client`` / ``minio_service.ModelService.get_minio_client`` 行为一致）。
    ``endpoint`` 可带 ``http(s)://``，会自动剥离。
    """
    if Minio is None:
        raise RuntimeError('未安装 minio 包，请执行: pip install minio')
    ep = normalize_minio_endpoint(endpoint)
    if not ep:
        raise ValueError('MinIO endpoint 为空')
    return Minio(
        ep,
        access_key=access_key or '',
        secret_key=secret_key or '',
        secure=coerce_minio_secure(secure),
    )


def ensure_bucket(client: 'Minio', bucket_name: str) -> None:
    """若桶不存在则创建（与 VIDEO ``upload_screenshot_to_minio`` / ``ModelService.upload_to_minio`` 一致）。"""
    if not bucket_name:
        raise ValueError('bucket_name 为空')
    if not client.bucket_exists(bucket_name):
        client.make_bucket(bucket_name)


def image_content_type(ext: str) -> str:
    """常见图片扩展名 → Content-Type（用于 fput_object）。"""
    e = (ext or '').lower().lstrip('.')
    if e in ('jpg', 'jpeg'):
        return 'image/jpeg'
    if e == 'png':
        return 'image/png'
    if e == 'gif':
        return 'image/gif'
    if e == 'bmp':
        return 'image/bmp'
    return 'application/octet-stream'


def download_object_to_path(
    client: 'Minio',
    bucket_name: str,
    object_name: str,
    destination_path: str,
) -> None:
    """
    下载对象到本地路径；先 stat 再 fget（与 VIDEO ``ModelService.download_from_minio`` 一致）。
    使用临时文件再 replace，避免半截文件。
    """
    parent = os.path.dirname(os.path.abspath(destination_path))
    if parent:
        os.makedirs(parent, exist_ok=True)
    client.stat_object(bucket_name, object_name)
    tmp_path = f'{destination_path}.minio.tmp'
    try:
        client.fget_object(bucket_name, object_name, tmp_path)
        os.replace(tmp_path, destination_path)
    except Exception:
        if os.path.isfile(tmp_path):
            try:
                os.remove(tmp_path)
            except OSError:
                pass
        raise


def upload_file_to_minio(
    client: 'Minio',
    bucket_name: str,
    object_name: str,
    file_path: str,
    content_type: Optional[str] = None,
    ensure_bucket_exists: bool = True,
) -> None:
    """上传本地文件；可选自动建桶（与 VIDEO ``ModelService.upload_to_minio`` 一致）。"""
    if ensure_bucket_exists:
        ensure_bucket(client, bucket_name)
    kwargs = {}
    if content_type:
        kwargs['content_type'] = content_type
    client.fput_object(bucket_name, object_name, file_path, **kwargs)

