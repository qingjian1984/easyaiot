"""
从 WVP/国标服务同步通道到 VIDEO device 表，供设备目录与分屏监控展示。
"""
import logging
from typing import Any, Dict, List, Optional, Tuple

import requests
from app.utils.gb28181_source import GB28181_SOURCE_PREFIX, _candidate_bases
from app.services.camera_service import (
    get_or_create_default_directory,
    gb28181_device_stream_urls,
    sync_unassigned_devices_to_default_directory,
)
from models import Device, db

logger = logging.getLogger(__name__)


def _extract_list(body: Any) -> List[dict]:
    if isinstance(body, list):
        return body
    if not isinstance(body, dict):
        return []
    data = body.get('data', body)
    if isinstance(data, list):
        return data
    if isinstance(data, dict):
        return data.get('list') or data.get('records') or data.get('rows') or []
    return []


def _gb28181_api_base() -> Optional[str]:
    for base in _candidate_bases():
        b = (base or '').strip().rstrip('/')
        if not b:
            continue
        if b.endswith('/device/query'):
            return b
        if b.endswith('/gb28181'):
            return f'{b}/device/query'
        if b.endswith('/api'):
            return f'{b}/device/query'
        return f'{b}/device/query'
    return None


def _normalize_channel(item: dict, sip_device_id: str) -> Optional[Tuple[str, str, str]]:
    """返回 (sip_device_id, channel_gb_id, display_name)。"""
    parent = str(
        item.get('parentId')
        or item.get('parentDeviceId')
        or item.get('gbParentId')
        or sip_device_id
        or '',
    ).strip()
    channel_id = str(
        item.get('channelId')
        or item.get('deviceChannelId')
        or item.get('gbDeviceId')
        or item.get('deviceId')
        or item.get('id')
        or item.get('gbId')
        or '',
    ).strip()
    if not parent or not channel_id:
        return None
    if channel_id == parent:
        return None
    name = (
        item.get('name')
        or item.get('channelName')
        or item.get('deviceName')
        or item.get('gbName')
        or channel_id
    )
    return parent, channel_id, str(name).strip()


def _virtual_device_id(sip_device_id: str, channel_id: str) -> str:
    return f'gb28181_{sip_device_id}_{channel_id}'


def _upsert_gb_device(sip_device_id: str, channel_id: str, name: str, default_dir_id: int) -> bool:
    mapped_id = _virtual_device_id(sip_device_id, channel_id)
    source = f'{GB28181_SOURCE_PREFIX}{sip_device_id}/{channel_id}'
    # 国标通道播放走 WVP 点播，live 流地址留空；算法任务需 ai_rtmp_stream 推送检测结果
    rtmp_stream, http_stream, ai_rtmp_stream, ai_http_stream = gb28181_device_stream_urls(mapped_id)

    device = Device.query.get(mapped_id)
    if device:
        changed = False
        if device.name != name:
            device.name = name
            changed = True
        if device.source != source:
            device.source = source
            changed = True
        if not device.directory_id:
            device.directory_id = default_dir_id
            changed = True
        if device.rtmp_stream or device.http_stream:
            device.rtmp_stream = rtmp_stream
            device.http_stream = http_stream
            changed = True
        if not (device.ai_rtmp_stream or '').strip():
            device.ai_rtmp_stream = ai_rtmp_stream
            changed = True
        if not (device.ai_http_stream or '').strip():
            device.ai_http_stream = ai_http_stream
            changed = True
        if changed:
            db.session.commit()
        return False

    device = Device(
        id=mapped_id,
        name=name or mapped_id,
        source=source,
        rtmp_stream=rtmp_stream,
        http_stream=http_stream,
        ai_rtmp_stream=ai_rtmp_stream,
        ai_http_stream=ai_http_stream,
        manufacturer='GB28181',
        model='GB28181-Channel',
        serial_number=sip_device_id,
        hardware_id=channel_id,
        nvr_channel=0,
        directory_id=default_dir_id,
    )
    db.session.add(device)
    db.session.commit()
    return True


def backfill_gb28181_ai_stream_urls() -> int:
    """为 device 表中 source 为 gb28181:// 且缺少 AI 推流地址的记录补全字段。"""
    prefix = GB28181_SOURCE_PREFIX.lower()
    updated = 0
    for device in Device.query.filter(Device.source.isnot(None)).all():
        source = (device.source or '').strip()
        if not source.lower().startswith(prefix):
            continue
        need_ai = not (device.ai_rtmp_stream or '').strip() or not (device.ai_http_stream or '').strip()
        if not need_ai:
            continue
        _, _, ai_rtmp, ai_http = gb28181_device_stream_urls(device.id)
        if not (device.ai_rtmp_stream or '').strip():
            device.ai_rtmp_stream = ai_rtmp
        if not (device.ai_http_stream or '').strip():
            device.ai_http_stream = ai_http
        updated += 1
    if updated:
        db.session.commit()
        logger.info(f'国标设备 AI 推流地址回填完成，更新 {updated} 条')
    return updated


def sync_gb28181_channels_to_devices() -> int:
    """
    拉取 WVP 国标设备与通道，写入/更新 device 表并归入默认分组。
    返回本次新创建的设备数量。
    """
    api_root = _gb28181_api_base()
    if not api_root:
        logger.warning('未配置 GB28181 服务地址，跳过国标通道同步')
        return 0

    default_dir = get_or_create_default_directory()
    created = 0

    try:
        devices_resp = requests.get(
            f'{api_root}/devices',
            params={'page': 1, 'count': 10000},
            timeout=20,
        )
        devices_resp.raise_for_status()
        gb_devices = _extract_list(devices_resp.json())
    except Exception as e:
        logger.warning(f'拉取国标设备列表失败: {e}')
        return 0

    for gb_dev in gb_devices:
        sip_id = str(
            gb_dev.get('deviceId')
            or gb_dev.get('deviceIdentification')
            or gb_dev.get('id')
            or '',
        ).strip()
        if not sip_id:
            continue

        try:
            ch_resp = requests.get(
                f'{api_root}/devices/{sip_id}/channels',
                params={'page': 1, 'count': 10000},
                timeout=20,
            )
            ch_resp.raise_for_status()
            channels = _extract_list(ch_resp.json())
        except Exception as e:
            logger.debug(f'拉取国标设备 {sip_id} 通道失败: {e}')
            continue

        for ch in channels:
            normalized = _normalize_channel(ch, sip_id)
            if not normalized:
                continue
            parent_id, channel_id, ch_name = normalized
            try:
                if _upsert_gb_device(parent_id, channel_id, ch_name, default_dir.id):
                    created += 1
            except Exception as e:
                db.session.rollback()
                logger.warning(
                    f'同步国标通道失败 {parent_id}/{channel_id}: {e}',
                )

    sync_unassigned_devices_to_default_directory()
    if created:
        logger.info(f'国标通道同步完成，新增 {created} 个设备记录')
    return created


def ensure_directory_devices_synced() -> None:
    """目录/分屏树加载前：同步国标 + 未分组设备归入默认目录。"""
    try:
        sync_gb28181_channels_to_devices()
    except Exception as e:
        logger.warning(f'国标设备同步异常: {e}')
        sync_unassigned_devices_to_default_directory()
    try:
        backfill_gb28181_ai_stream_urls()
    except Exception as e:
        logger.warning(f'国标设备 AI 推流地址回填异常: {e}')
