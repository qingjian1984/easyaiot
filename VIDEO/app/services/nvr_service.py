"""NVR 记录与直连摄像头挂载关系（字段对齐 hiktools）。"""
from __future__ import annotations

import logging
import re
import time
from typing import Any
from urllib.parse import urlparse

from sqlalchemy import or_
from models import Device, Nvr, db

logger = logging.getLogger(__name__)

_GENERIC_CAMERA_NAME = re.compile(r'^camera\s*0*\d+\s*$', re.IGNORECASE)


def _resolve_channel_display_name(
    raw_name: str | None,
    channel_id: int,
    nvr_ip: str,
    cam_ip: str = '',
) -> str:
    """将海康默认的 Camera 01 等重复名称替换为可区分名称。"""
    name = (raw_name or '').strip()
    if name and not _GENERIC_CAMERA_NAME.match(name):
        return name
    if cam_ip:
        return f'CH{channel_id}-{cam_ip}'
    return f'NVR-{nvr_ip}-CH{channel_id}'


def _refresh_generic_channel_names(nvr_id: int) -> None:
    """将库中仍为 Camera 01 等默认名的 NVR 通道改为可区分名称。"""
    nvr = Nvr.query.get(nvr_id)
    if not nvr:
        return
    nvr_ip = nvr.ip or ''
    changed = False
    for cam in Device.query.filter_by(nvr_id=nvr_id).all():
        ch = cam.nvr_channel or 0
        if ch <= 0:
            continue
        new_name = _resolve_channel_display_name(cam.name, ch, nvr_ip, (cam.ip or '').strip())
        if new_name != (cam.name or '').strip():
            cam.name = new_name
            changed = True
    if changed:
        db.session.commit()


def _ensure_nvr_stream_forward_task(nvr_id: int) -> None:
    """为 NVR 下全部 RTSP 挂载通道确保单一推流转发任务（更新/同步后调用）。"""
    try:
        from app.services.stream_forward_service import ensure_nvr_stream_forward_task
        ensure_nvr_stream_forward_task(nvr_id)
    except Exception as e:
        logger.warning(
            f'为 NVR {nvr_id} 自动创建/更新推流转发任务失败: {e}，不影响 NVR 保存',
        )


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
        'rtmp_stream': cam.rtmp_stream,
        'http_stream': cam.http_stream,
        'ai_rtmp_stream': cam.ai_rtmp_stream,
        'ai_http_stream': cam.ai_http_stream,
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


def _apply_nvr_fields(nvr: Nvr, info: dict[str, Any]) -> None:
    ip = (info.get('ip') or '').strip()
    if ip:
        nvr.ip = ip
    if 'port' in info and info.get('port') is not None:
        try:
            nvr.port = int(info.get('port') or 80)
        except (TypeError, ValueError):
            pass
    for field in (
        'username', 'password', 'name', 'model', 'vendor',
        'serial_number', 'firmware_version', 'device_type', 'mac',
        'scheme', 'rtsp_url', 'source',
    ):
        if field not in info:
            continue
        val = info.get(field)
        if val is None:
            continue
        if isinstance(val, str) and val.strip() == '':
            continue
        setattr(nvr, field, val)


def get_or_create_nvr(info: dict[str, Any]) -> int:
    """按 id 或 IP+端口查找或创建 NVR，返回 nvr.id。"""
    ip = (info.get('ip') or '').strip()
    if not ip:
        raise ValueError('NVR IP 不能为空')
    try:
        port = int(info.get('port') or 80)
    except (TypeError, ValueError):
        port = 80

    raw_id = info.get('id')
    if raw_id is not None and raw_id != '':
        try:
            nvr_id = int(raw_id)
            nvr = Nvr.query.get(nvr_id)
            if nvr:
                conflict = Nvr.query.filter(
                    Nvr.ip == ip,
                    Nvr.port == port,
                    Nvr.id != nvr_id,
                ).first()
                if conflict:
                    raise ValueError(f'IP {ip}:{port} 已被其他 NVR 占用')
                _apply_nvr_fields(nvr, info)
                db.session.flush()
                return nvr.id
        except (TypeError, ValueError):
            pass

    nvr = Nvr.query.filter_by(ip=ip, port=port).first()
    if not nvr:
        nvr = Nvr.query.filter_by(ip=ip).order_by(Nvr.id).first()
    if not nvr:
        nvr = Nvr(ip=ip, port=port)
        db.session.add(nvr)

    _apply_nvr_fields(nvr, info)
    db.session.flush()
    return nvr.id


def delete_nvr(nvr_id: int) -> None:
    """删除 NVR 记录（挂载摄像头 nvr_id 由外键 SET NULL）。"""
    nvr = Nvr.query.get(nvr_id)
    if not nvr:
        raise ValueError(f'NVR {nvr_id} 不存在')
    db.session.delete(nvr)
    db.session.commit()


def upsert_nvr(info: dict[str, Any]) -> dict[str, Any]:
    nvr_id = get_or_create_nvr(info)
    db.session.commit()
    _refresh_generic_channel_names(nvr_id)
    _ensure_nvr_stream_forward_task(nvr_id)
    return get_nvr(nvr_id, include_cameras=True)


def _vendor_to_camera_type(vendor: str | None) -> str:
    v = (vendor or '').strip().lower()
    if v in ('hikvision', '海康'):
        return 'hikvision'
    if v in ('dahua', '大华'):
        return 'dahua'
    return 'custom'


def _resolve_channel_source(
    nvr: Nvr,
    ch: dict[str, Any],
    *,
    username: str,
    password: str,
    vendor: str | None,
) -> str | None:
    """根据枚举结果或 NVR 模板生成经 NVR 取流的 RTSP，不探测单台 IPC。"""
    source = (ch.get('rtsp_url') or ch.get('source') or '').strip()
    if source:
        return source
    try:
        channel_id = int(
            ch.get('channel_id') if ch.get('channel_id') is not None else ch.get('nvr_channel') or 0
        )
    except (TypeError, ValueError):
        return None
    if channel_id <= 0:
        return None
    nvr_vendor = (ch.get('vendor') or vendor or nvr.vendor or 'hikvision').strip()
    from app.vendor.hiktools.core.models import Credential
    from app.vendor.hiktools.core.rtsp import build_nvr_channel_rtsp

    cred = Credential(username=username or nvr.username or '', password=password or nvr.password or '')
    return build_nvr_channel_rtsp(
        nvr_vendor,
        nvr.ip,
        channel_id,
        [cred],
        preferred=cred,
        channel_username=ch.get('username'),
    )


def _upsert_nvr_channel_device(
    nvr_id: int,
    nvr: Nvr,
    ch: dict[str, Any],
    *,
    username: str,
    password: str,
    vendor: str | None,
) -> bool:
    """将枚举通道直接关联到 NVR（仅写库，不 ONVIF、不连通性探测）。"""
    try:
        channel_id = int(
            ch.get('channel_id') if ch.get('channel_id') is not None else ch.get('nvr_channel') or 0
        )
    except (TypeError, ValueError):
        return False
    if channel_id <= 0:
        return False

    source = _resolve_channel_source(nvr, ch, username=username, password=password, vendor=vendor)
    if not source:
        return False

    from app.services.camera_service import _generate_stream_urls, directory_id_for_new_device

    nvr_ip = nvr.ip or ''
    ch_vendor = ch.get('vendor') or vendor or nvr.vendor
    mfr = (vendor_label(ch_vendor) or ch_vendor or 'EasyAIoT').strip()
    model = (ch.get('model') or 'NVR-Channel').strip() or 'NVR-Channel'
    cam_ip = (ch.get('camera_ip') or ch.get('ip') or '').strip()
    name = _resolve_channel_display_name(ch.get('name'), channel_id, nvr_ip, cam_ip)
    try:
        cam_port = _normalize_ipc_display_port(int(ch.get('camera_port') or ch.get('port') or 554))
    except (TypeError, ValueError):
        cam_port = 554

    online = ch.get('online')
    conn = ch.get('connection_status') or ch.get('probe_error')
    rtsp_direct = ch.get('rtsp_direct')
    serial = ch.get('serial') or ch.get('serial_number') or ''

    from app.services.camera_service import find_existing_device_for_register

    existing = find_existing_device_for_register(
        ip=cam_ip,
        mac=(ch.get('mac') or '').strip(),
        serial_number=serial,
        nvr_id=nvr_id,
        nvr_channel=channel_id,
    )
    if existing:
        device_id = existing.id
        rtmp_stream, http_stream, ai_rtmp_stream, ai_http_stream = _generate_stream_urls(source, device_id)
        existing.nvr_id = int(nvr_id)
        existing.nvr_channel = channel_id
        if not existing.nvr_id:
            raise RuntimeError(f'通道 CH{channel_id} 关联 NVR 失败：nvr_id 为空')
        existing.name = name
        existing.source = source
        existing.rtmp_stream = rtmp_stream
        existing.http_stream = http_stream
        existing.ai_rtmp_stream = ai_rtmp_stream
        existing.ai_http_stream = ai_http_stream
        existing.ip = cam_ip
        existing.port = cam_port
        existing.username = username or nvr.username or ''
        existing.password = password if password is not None else (nvr.password or '')
        existing.manufacturer = mfr
        existing.model = model
        existing.serial_number = serial or existing.serial_number
        existing.mac = ch.get('mac') or existing.mac
        existing.rtsp_direct = rtsp_direct
        existing.channel_online = online
        existing.connection_status = conn
        return True

    device_id = str(time.time_ns())
    rtmp_stream, http_stream, ai_rtmp_stream, ai_http_stream = _generate_stream_urls(source, device_id)
    camera = Device(
        id=device_id,
        name=name,
        source=source,
        rtmp_stream=rtmp_stream,
        http_stream=http_stream,
        ai_rtmp_stream=ai_rtmp_stream,
        ai_http_stream=ai_http_stream,
        stream=0,
        ip=cam_ip,
        port=cam_port,
        username=username or nvr.username or '',
        password=password if password is not None else (nvr.password or ''),
        mac=ch.get('mac') or '',
        manufacturer=mfr,
        model=model,
        firmware_version=ch.get('firmware') or '',
        serial_number=serial,
        nvr_id=int(nvr_id),
        nvr_channel=channel_id,
        rtsp_direct=rtsp_direct,
        channel_online=online,
        connection_status=conn,
        directory_id=directory_id_for_new_device(),
    )
    if not camera.nvr_id:
        raise RuntimeError(f'通道 CH{channel_id} 创建失败：nvr_id 为空')
    db.session.add(camera)
    return True


def _link_channels_to_nvr(nvr_id: int, nvr_ip: str | None = None) -> int:
    """登记后兜底：同 NVR 取流地址或 NVR-Channel 记录必须带上 nvr_id。"""
    nvr = Nvr.query.get(nvr_id)
    if not nvr:
        return 0
    host = (nvr_ip or nvr.ip or '').strip()
    fixed = 0
    for cam in Device.query.filter(
        or_(Device.nvr_id.is_(None), Device.nvr_id != nvr_id),
    ).all():
        if not is_nvr_channel_device(cam) and not (host and host in (cam.source or '')):
            continue
        if int(cam.nvr_channel or 0) <= 0 and host and host not in (cam.source or ''):
            continue
        cam.nvr_id = nvr_id
        fixed += 1
    return fixed


def bulk_register_nvr_channels(
    nvr_info: dict[str, Any],
    channels: list[dict[str, Any]],
    *,
    username: str = '',
    password: str = '',
    vendor: str | None = None,
) -> dict[str, Any]:
    """登记/更新 NVR，并按枚举结果批量关联通道（不逐台 ONVIF/连通性探测）。"""
    nvr_id = get_or_create_nvr(nvr_info)
    db.session.flush()
    nvr = Nvr.query.get(nvr_id)
    if not nvr:
        raise ValueError(f'NVR {nvr_id} 不存在')

    user = (username or nvr_info.get('username') or nvr.username or '').strip()
    pwd = password if password is not None else (nvr_info.get('password') or nvr.password or '')
    v = vendor or nvr_info.get('vendor') or nvr.vendor

    registered = 0
    skipped = 0
    errors: list[str] = []

    for ch in channels or []:
        try:
            ch_no = ch.get('channel_id', ch.get('nvr_channel', '?'))
            if _upsert_nvr_channel_device(nvr_id, nvr, ch, username=user, password=pwd, vendor=v):
                registered += 1
            else:
                skipped += 1
        except Exception as e:
            errors.append(f'CH{ch_no}: {e}')

    try:
        linked = _link_channels_to_nvr(nvr_id, nvr.ip)
        if linked:
            logger.info(f'NVR {nvr_id} 登记后补全 {linked} 条通道的 nvr_id')
        db.session.commit()
    except Exception as e:
        db.session.rollback()
        raise RuntimeError(f'批量挂载提交失败: {e}') from e

    _ensure_nvr_stream_forward_task(nvr_id)
    result = get_nvr(nvr_id, include_cameras=True)
    result['register_stats'] = {
        'registered': registered,
        'skipped': skipped,
        'errors': errors,
    }
    return result


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


_WEB_PROBE_PORTS = frozenset({80, 443, 8000, 8443, 37777})


def _normalize_ipc_display_port(port: int | None) -> int:
    """NVR 枚举返回的 port 多为 IPC Web 端口，展示/存库用 RTSP 默认 554。"""
    try:
        p = int(port) if port is not None else 0
    except (TypeError, ValueError):
        return 554
    if p <= 0 or p in _WEB_PROBE_PORTS:
        return 554
    return p


def is_nvr_channel_device(camera: Device) -> bool:
    """是否 NVR 挂载通道（经 NVR/RTSP 取流，不应走单机 ONVIF）。"""
    if camera.nvr_id:
        return True
    ch = int(camera.nvr_channel or 0)
    model = (camera.model or '').strip()
    source = (camera.source or '').strip().lower()
    if ch > 0 and model == 'NVR-Channel':
        return True
    if model == 'NVR-Channel' and source.startswith('rtsp://'):
        if '/streaming/channels/' in source or 'channel=' in source:
            return True
    nvr_id, inferred_ch = infer_nvr_link_from_source(camera.source)
    if nvr_id and (ch > 0 or inferred_ch > 0):
        return True
    return False


def repair_nvr_channel_links(*, commit: bool = True) -> int:
    """将 nvr_id 因外键 SET NULL 等方式丢失的通道，按 RTSP 源重新关联到 NVR。"""
    nvr_by_ip = build_nvr_ip_index()
    fixed = 0
    q = Device.query.filter(Device.nvr_id.is_(None))
    for cam in q.all():
        if not is_nvr_channel_device(cam):
            continue
        nvr_id, inferred_ch = infer_nvr_link_from_source(cam.source, nvr_by_ip=nvr_by_ip)
        if not nvr_id:
            continue
        cam.nvr_id = nvr_id
        if inferred_ch and not int(cam.nvr_channel or 0):
            cam.nvr_channel = inferred_ch
        fixed += 1
    if fixed and commit:
        db.session.commit()
        logger.info(f'已修复 {fixed} 条 NVR 通道的 nvr_id 关联')
    return fixed


def build_nvr_ip_index() -> dict[str, int]:
    """NVR IP -> id，用于从 RTSP 源反查挂载关系。"""
    index: dict[str, int] = {}
    for nvr in Nvr.query.all():
        ip = (nvr.ip or '').strip()
        if ip and nvr.id:
            index[ip] = nvr.id
    return index


def infer_nvr_link_from_source(
    source: str | None,
    *,
    nvr_by_ip: dict[str, int] | None = None,
) -> tuple[int | None, int]:
    """
    从 RTSP 源推断 (nvr_id, nvr_channel)。
    海康 NVR：/Streaming/Channels/2301 -> 通道 23（stream_id // 100）。
    """
    text = (source or '').strip()
    if not text.lower().startswith('rtsp://'):
        return None, 0
    host = (urlparse(text).hostname or '').strip()
    if not host:
        return None, 0
    if nvr_by_ip is None:
        nvr_by_ip = build_nvr_ip_index()
    nvr_id = nvr_by_ip.get(host)
    if not nvr_id:
        return None, 0

    channel = 0
    m = re.search(r'/Streaming/Channels/(\d+)', text, re.I)
    if m:
        stream_id = int(m.group(1))
        channel = stream_id // 100 if stream_id >= 100 else stream_id
    else:
        m2 = re.search(r'[?&]channel=(\d+)', text, re.I)
        if m2:
            channel = int(m2.group(1))
    return nvr_id, channel


def nvr_fields_for_device(camera: Device) -> dict[str, Any]:
    """设备字典中附带的 NVR 摘要。"""
    if not camera.nvr_id:
        ch = camera.nvr_channel or 0
        if is_nvr_channel_device(camera):
            inferred_id, inferred_ch = infer_nvr_link_from_source(camera.source)
            if inferred_ch and not ch:
                ch = inferred_ch
            nvr = Nvr.query.get(inferred_id) if inferred_id else None
            if nvr:
                base = nvr.name or nvr.ip
                label = f'{base} / CH{ch}' if ch else base
                return {
                    'nvr_id': inferred_id,
                    'nvr_channel': ch,
                    'nvr_label': label,
                    'nvr': _nvr_to_dict(nvr, include_cameras=False),
                    'device_kind': 'nvr_channel',
                    'rtsp_direct': camera.rtsp_direct,
                    'channel_online': camera.channel_online,
                    'connection_status': camera.connection_status,
                }
            return {
                'nvr_id': inferred_id,
                'nvr_channel': ch,
                'nvr_label': None,
                'nvr': None,
                'device_kind': 'nvr_channel',
                'rtsp_direct': camera.rtsp_direct,
                'channel_online': camera.channel_online,
                'connection_status': camera.connection_status,
            }
        return {
            'nvr_id': None,
            'nvr_channel': ch,
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
