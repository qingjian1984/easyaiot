from __future__ import annotations

from dataclasses import dataclass
from typing import Iterable

import httpx

from .models import Credential

DEVICE_INFO_PATH = "/ISAPI/System/deviceInfo"


@dataclass
class IsapiResult:
    status: int | None
    body: str | None
    www_authenticate: str | None
    server: str | None
    used_credential: Credential | None
    error: str | None = None


async def fetch_isapi_path(
    client: httpx.AsyncClient,
    base_url: str,
    path: str,
    credentials: Iterable[Credential] = (),
    timeout: float = 5.0,
) -> IsapiResult:
    """GET an ISAPI path; tries Digest auth with each credential after 401."""
    url = f"{base_url.rstrip('/')}{path}"

    try:
        r = await client.get(url, timeout=timeout)
    except (httpx.TimeoutException, httpx.TransportError) as e:
        return IsapiResult(None, None, None, None, None, error=str(e))

    challenge = r.headers.get("WWW-Authenticate")
    server = r.headers.get("Server")
    last = IsapiResult(r.status_code, r.text, challenge, server, None)

    if r.status_code != 401:
        return last

    for cred in credentials:
        try:
            ra = await client.get(
                url,
                auth=httpx.DigestAuth(cred.username, cred.password),
                timeout=timeout,
            )
        except (httpx.TimeoutException, httpx.TransportError) as e:
            last = IsapiResult(None, None, challenge, server, cred, error=str(e))
            continue

        if ra.status_code == 200:
            return IsapiResult(
                ra.status_code,
                ra.text,
                challenge,
                ra.headers.get("Server") or server,
                cred,
            )
        last = IsapiResult(
            ra.status_code,
            ra.text,
            ra.headers.get("WWW-Authenticate") or challenge,
            ra.headers.get("Server") or server,
            cred,
        )

    return last


async def fetch_device_info(
    client: httpx.AsyncClient,
    base_url: str,
    credentials: Iterable[Credential] = (),
    timeout: float = 5.0,
) -> IsapiResult:
    """Query /ISAPI/System/deviceInfo."""
    return await fetch_isapi_path(
        client, base_url, DEVICE_INFO_PATH, credentials, timeout
    )
