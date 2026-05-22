"""NVR 记录与直连摄像头挂载关系（字段对齐 hiktools）。"""
from __future__ import annotations

from typing import Any

from models import Device, Nvr, db

_VENDOR_LABELS = {
    'hikvision': '海康',
    'dahua': '大华',
    'huawei': '华为',
    'ezviz': '萤石',
    'xiaomi': '小米',
}


def vendor_label(vendor: str | None) -> str:
    if not vendor:
        return ''
    return _VENDOR_LABELS.get(vendor, vendor)


def _camera_under_nvr_dict(cam: Device) -> dict[str, Any]:
    online = cam.channel_online
    online_text = '在线' if online is True else ('离线' if online is False else '—')
    return {
        'id': cam.id,
        'name': cam.name,
        'ip': cam.ip,
        'port': cam.port,
        'nvr_channel': cam.nvr_channel,
        'source': cam.source,
        'rtsp_url': cam.source,
        'rtsp_direct': cam.rtsp_direct,
        'model': cam.model,
        'serial': cam.serial_number,
        'serial_number': cam.serial_number,
        'mac': cam.mac,
        'manufacturer': cam.manufacturer,
        'online': cam.channel_online if cam.channel_online is not None else None,
        'online_text': online_text,
        'connection_status': cam.connection_status,
        'username': cam.username,
    }


def _nvr_to_dict(nvr: Nvr, *, include_cameras: bool = False) -> dict[str, Any]:
    sch = nvr.scheme or ('https' if (nvr.port or 80) in (443, 8443) else 'http')
    row: dict[str, Any] = {
        'id': nvr.id,
        'ip': nvr.ip,
        'port': nvr.port,
        'scheme': sch,
        'web_url': nvr.web_url,
        'username': nvr.username,
        'name': nvr.name,
        'device_name': nvr.name,
        'model': nvr.model,
        'vendor': nvr.vendor,
        'vendor_label': vendor_label(nvr.vendor),
        'serial_number': nvr.serial_number,
        'serial': nvr.serial_number,
        'firmware_version': nvr.firmware_version,
        'firmware': nvr.firmware_version,
        'device_type': nvr.device_type,
        'mac': nvr.mac,
        'rtsp_url': nvr.rtsp_url,
        'source': nvr.source,
    }
    cameras = list(nvr.cameras or [])
    if include_cameras:
        row['cameras'] = [_camera_under_nvr_dict(c) for c in cameras]
        row['camera_count'] = len(row['cameras'])
    else:
        row['camera_count'] = Device.query.filter_by(nvr_id=nvr.id).count()
    return row


def get_nvr(nvr_id: int, *, include_cameras: bool = False) -> dict[str, Any]:
    nvr = Nvr.query.get(nvr_id)
    if not nvr:
        raise ValueError(f'NVR {nvr_id} 不存在')
    return _nvr_to_dict(nvr, include_cameras=include_cameras)


def list_nvrs(*, include_cameras: bool = False) -> list[dict[str, Any]]:
    nvrs = Nvr.query.order_by(Nvr.ip, Nvr.id).all()
    return [_nvr_to_dict(n, include_cameras=include_cameras) for n in nvrs]


def get_or_create_nvr(info: dict[str, Any]) -> int:
    """按 IP+端口查找或创建 NVR，返回 nvr.id。"""
    ip = (info.get('ip') or '').strip()
    if not ip:
        raise ValueError('NVR IP 不能为空')
    try:
        port = int(info.get('port') or 80)
    except (TypeError, ValueError):
        port = 80

    nvr = Nvr.query.filter_by(ip=ip, port=port).first()
    if not nvr:
        nvr = Nvr(ip=ip, port=port)
        db.session.add(nvr)

    for field in (
        'username', 'password', 'name', 'model', 'vendor',
        'serial_number', 'firmware_version', 'device_type', 'mac',
        'scheme', 'rtsp_url', 'source',
    ):
        val = info.get(field)
        if val is not None and str(val).strip() != '':
            setattr(nvr, field, val)

    db.session.flush()
    return nvr.id


def upsert_nvr(info: dict[str, Any]) -> dict[str, Any]:
    nvr_id = get_or_create_nvr(info)
    db.session.commit()
    return get_nvr(nvr_id, include_cameras=True)


def resolve_nvr_link(payload: dict[str, Any]) -> tuple[int | None, int]:
    """从注册/更新请求解析 nvr_id 与通道号。"""
    try:
        channel = int(payload.get('nvr_channel') if payload.get('nvr_channel') is not None else 0)
    except (TypeError, ValueError):
        channel = 0

    if 'nvr_id' in payload and payload.get('nvr_id') in (None, '', 0):
        return None, 0

    raw_id = payload.get('nvr_id')
    if raw_id is not None and raw_id != '' and raw_id != 0:
        try:
            return int(raw_id), channel
        except (TypeError, ValueError):
            pass

    nvr_obj = payload.get('nvr')
    if isinstance(nvr_obj, dict) and (nvr_obj.get('ip') or '').strip():
        return get_or_create_nvr(nvr_obj), channel

    nvr_ip = (payload.get('nvr_ip') or '').strip()
    if nvr_ip:
        nvr_id = get_or_create_nvr({
            'ip': nvr_ip,
            'port': payload.get('nvr_port', 80),
            'username': payload.get('nvr_username') or payload.get('username'),
            'password': payload.get('nvr_password') or payload.get('password'),
            'name': payload.get('nvr_name'),
            'model': payload.get('nvr_model'),
            'vendor': payload.get('nvr_vendor'),
            'serial_number': payload.get('nvr_serial'),
            'firmware_version': payload.get('nvr_firmware'),
            'device_type': payload.get('nvr_device_type'),
            'mac': payload.get('nvr_mac'),
            'scheme': payload.get('nvr_scheme'),
            'rtsp_url': payload.get('nvr_rtsp_url') or payload.get('rtsp_url'),
            'source': payload.get('nvr_source') or payload.get('source'),
        })
        return nvr_id, channel

    return None, channel


def nvr_fields_for_device(camera: Device) -> dict[str, Any]:
    """设备字典中附带的 NVR 摘要。"""
    if not camera.nvr_id:
        return {
            'nvr_id': None,
            'nvr_channel': camera.nvr_channel or 0,
            'nvr_label': None,
            'nvr': None,
            'device_kind': 'direct',
        }
    nvr = camera.nvr
    if not nvr:
        nvr = Nvr.query.get(camera.nvr_id)
    if not nvr:
        return {
            'nvr_id': camera.nvr_id,
            'nvr_channel': camera.nvr_channel or 0,
            'nvr_label': None,
            'nvr': None,
            'device_kind': 'nvr_channel',
        }
    ch = camera.nvr_channel or 0
    base = nvr.name or nvr.ip
    label = f'{base} / CH{ch}' if ch else base
    return {
        'nvr_id': camera.nvr_id,
        'nvr_channel': ch,
        'nvr_label': label,
        'nvr': _nvr_to_dict(nvr, include_cameras=False),
        'device_kind': 'nvr_channel',
        'rtsp_direct': camera.rtsp_direct,
        'channel_online': camera.channel_online,
        'connection_status': camera.connection_status,
    }
