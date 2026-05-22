from __future__ import annotations

import ipaddress
from pathlib import Path
from typing import Iterable

DEFAULT_PORTS: tuple[int, ...] = (80, 443, 8000, 8443)


def parse_ports(spec: str | Iterable[int] | None) -> list[int]:
    if spec is None:
        return list(DEFAULT_PORTS)
    if isinstance(spec, str):
        out: list[int] = []
        for part in spec.replace(";", ",").split(","):
            part = part.strip()
            if not part:
                continue
            if "-" in part:
                lo_s, hi_s = part.split("-", 1)
                lo, hi = int(lo_s), int(hi_s)
                if lo > hi or lo < 1 or hi > 65535:
                    raise ValueError(f"invalid port range: {part!r}")
                out.extend(range(lo, hi + 1))
            else:
                p = int(part)
                if not 1 <= p <= 65535:
                    raise ValueError(f"port out of range: {p}")
                out.append(p)
        return _dedup_preserve(out)
    return _dedup_preserve(int(p) for p in spec)


def _dedup_preserve(items: Iterable[int]) -> list[int]:
    seen: set[int] = set()
    out: list[int] = []
    for it in items:
        if it not in seen:
            seen.add(it)
            out.append(it)
    return out


def _expand_ip_token(token: str) -> tuple[list[str], int | None]:
    """Return (ips, inline_port_or_None) for one token. Token may include :port."""
    inline_port: int | None = None
    if token.count(":") == 1 and "/" not in token and "-" not in token.split(":")[0]:
        host, port_s = token.rsplit(":", 1)
        inline_port = int(port_s)
        token = host

    if "/" in token:
        net = ipaddress.ip_network(token, strict=False)
        return [str(h) for h in net.hosts()] or [str(net.network_address)], inline_port

    if "-" in token:
        lo_s, hi_s = token.split("-", 1)
        if "." in hi_s:
            lo = ipaddress.IPv4Address(lo_s)
            hi = ipaddress.IPv4Address(hi_s)
        else:
            lo = ipaddress.IPv4Address(lo_s)
            base = lo_s.rsplit(".", 1)[0]
            hi = ipaddress.IPv4Address(f"{base}.{hi_s}")
        if int(hi) < int(lo):
            raise ValueError(f"invalid IP range: {token!r}")
        return [str(ipaddress.IPv4Address(i)) for i in range(int(lo), int(hi) + 1)], inline_port

    ipaddress.ip_address(token)
    return [token], inline_port


def parse_targets(
    raw: str,
    ports: Iterable[int] | None = None,
) -> list[tuple[str, int]]:
    """Parse multi-line/comma-separated target spec into (ip, port) tuples.

    Tokens supported per line:
      - 192.168.1.0/24
      - 10.0.0.1-10.0.0.50  or  10.0.0.1-50
      - 1.2.3.4
      - 1.2.3.4:8080  (inline port overrides ports arg for this token)
    """
    default_ports = list(ports) if ports is not None else list(DEFAULT_PORTS)
    if not default_ports:
        raise ValueError("at least one port is required")

    tasks: list[tuple[str, int]] = []
    seen: set[tuple[str, int]] = set()

    for line in raw.splitlines():
        line = line.split("#", 1)[0].strip()
        if not line:
            continue
        for token in line.replace(";", ",").split(","):
            token = token.strip()
            if not token:
                continue
            ips, inline_port = _expand_ip_token(token)
            port_list = [inline_port] if inline_port is not None else default_ports
            for ip in ips:
                for p in port_list:
                    key = (ip, p)
                    if key not in seen:
                        seen.add(key)
                        tasks.append(key)
    return tasks


def load_targets_from_file(path: str | Path) -> str:
    return Path(path).read_text(encoding="utf-8")
