from __future__ import annotations

from urllib.parse import quote

from .device_role import ROLE_NVR_DAHUA, ROLE_NVR_HIK, is_nvr_role
from .models import Credential, Device
from .vendors import VENDOR_DAHUA, VENDOR_EZVIZ, VENDOR_HIKVISION

RTSP_PORT = 554


def _quote_userinfo(value: str) -> str:
    return quote(value, safe="")


def _auth_prefix(username: str | None, password: str | None) -> str:
    if not username:
        return ""
    user = _quote_userinfo(username)
    pw = _quote_userinfo(password or "")
    return f"{user}:{pw}@"


def _pick_credential(
    credentials: list[Credential] | tuple[Credential, ...],
    preferred: Credential | None = None,
    username: str | None = None,
) -> Credential | None:
    if preferred:
        return preferred
    if username:
        for c in credentials:
            if c.username == username:
                return c
    if credentials:
        return credentials[0]
    return None


def build_hikvision_rtsp(
    host: str,
    *,
    channel: int = 1,
    subtype: int = 0,
    port: int = RTSP_PORT,
    username: str | None = None,
    password: str | None = None,
) -> str:
    """Hikvision RTSP: channel 1 main stream -> /Streaming/Channels/101."""
    stream_id = channel * 100 + (1 + subtype)
    auth = _auth_prefix(username, password)
    return f"rtsp://{auth}{host}:{port}/Streaming/Channels/{stream_id}"


def build_dahua_rtsp(
    host: str,
    *,
    channel: int = 1,
    subtype: int = 0,
    port: int = RTSP_PORT,
    username: str | None = None,
    password: str | None = None,
) -> str:
    """Dahua RTSP: /cam/realmonitor?channel=1&subtype=0 (0=主码流)."""
    auth = _auth_prefix(username, password)
    return (
        f"rtsp://{auth}{host}:{port}/cam/realmonitor"
        f"?channel={channel}&subtype={subtype}"
    )


def build_device_rtsp_url(
    device: Device,
    credentials: list[Credential] | tuple[Credential, ...] = (),
    *,
    preferred: Credential | None = None,
    channel: int = 1,
    subtype: int = 0,
) -> str | None:
    """Build RTSP URL for a scanned device (IPC or NVR preview on channel 1)."""
    if not device.is_recognized or not device.vendor:
        return None

    cred = _pick_credential(list(credentials), preferred)
    user = cred.username if cred else None
    pw = cred.password if cred else None
    host = device.ip

    if is_nvr_role(device.device_role):
        if device.device_role == ROLE_NVR_HIK or device.vendor == VENDOR_HIKVISION:
            return build_hikvision_rtsp(
                host, channel=channel, subtype=subtype, username=user, password=pw
            )
        if device.device_role == ROLE_NVR_DAHUA or device.vendor == VENDOR_DAHUA:
            return build_dahua_rtsp(
                host, channel=channel, subtype=subtype, username=user, password=pw
            )

    if device.vendor in (VENDOR_HIKVISION, VENDOR_EZVIZ):
        return build_hikvision_rtsp(
            host, channel=1, subtype=subtype, username=user, password=pw
        )
    if device.vendor == VENDOR_DAHUA:
        return build_dahua_rtsp(
            host, channel=1, subtype=subtype, username=user, password=pw
        )
    return None


def build_nvr_channel_rtsp(
    nvr_vendor: str,
    nvr_host: str,
    channel_id: int,
    credentials: list[Credential] | tuple[Credential, ...] = (),
    *,
    preferred: Credential | None = None,
    channel_username: str | None = None,
    subtype: int = 0,
    port: int = RTSP_PORT,
) -> str | None:
    """RTSP via NVR (use NVR IP + channel number)."""
    cred = _pick_credential(
        list(credentials), preferred, username=channel_username
    )
    user = cred.username if cred else None
    pw = cred.password if cred else None

    if nvr_vendor == VENDOR_HIKVISION:
        return build_hikvision_rtsp(
            nvr_host,
            channel=channel_id,
            subtype=subtype,
            port=port,
            username=user,
            password=pw,
        )
    if nvr_vendor == VENDOR_DAHUA:
        return build_dahua_rtsp(
            nvr_host,
            channel=channel_id,
            subtype=subtype,
            port=port,
            username=user,
            password=pw,
        )
    return None


def build_camera_direct_rtsp(
    camera_ip: str,
    vendor: str | None,
    credentials: list[Credential] | tuple[Credential, ...] = (),
    *,
    channel_username: str | None = None,
    subtype: int = 0,
    port: int = RTSP_PORT,
) -> str | None:
    """RTSP directly to camera IP (when reachable)."""
    cred = _pick_credential(list(credentials), username=channel_username)
    user = cred.username if cred else None
    pw = cred.password if cred else None

    if vendor in (VENDOR_HIKVISION, VENDOR_EZVIZ):
        return build_hikvision_rtsp(
            camera_ip, channel=1, subtype=subtype, port=port, username=user, password=pw
        )
    if vendor == VENDOR_DAHUA:
        return build_dahua_rtsp(
            camera_ip, channel=1, subtype=subtype, port=port, username=user, password=pw
        )
    return None
