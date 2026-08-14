"""节点配置 API 的 HMAC 签名、密钥读取和持久防重放基础设施。

该模块不参与旧的 ``X-Agent-Token`` 工作负载接口。新 collector 配置接口
必须显式调用 :func:`verify_agent_request`，缺少密钥或 nonce 存储时 fail-closed。
密钥文件位于安装目录外，且不会被写入日志、响应或普通环境变量。
"""

from __future__ import annotations

import hashlib
import hmac
import os
import re
import secrets
import sqlite3
import stat
import threading
import time
from dataclasses import dataclass
from pathlib import Path
from typing import Dict, Iterable, Mapping, Optional, Sequence


NODE_ID_HEADER = "X-EasyAIoT-Node-Id"
KEY_ID_HEADER = "X-EasyAIoT-Key-Id"
TIMESTAMP_HEADER = "X-EasyAIoT-Timestamp"
NONCE_HEADER = "X-EasyAIoT-Nonce"
BODY_SHA256_HEADER = "X-EasyAIoT-Body-SHA256"
SIGNATURE_HEADER = "X-EasyAIoT-Signature"
DEFAULT_REPLAY_TTL_SECONDS = 600
DEFAULT_MAX_CLOCK_SKEW_SECONDS = 300
_HEX_128 = re.compile(r"^[0-9a-fA-F]{32,128}$")
_HEX_256 = re.compile(r"^[0-9a-fA-F]{64}$")


class AgentAuthError(ValueError):
    """带有稳定错误码的认证失败，不包含密钥、token 或请求体。"""

    def __init__(self, code: str):
        super().__init__(code)
        self.code = code


@dataclass(frozen=True)
class AgentSigningKey:
    node_id: int
    key_id: str
    secret: bytes


class AgentSigningKeyProvider:
    """节点签名密钥 Provider；业务代码不得直接读取明文。"""

    def keys(self, node_id: int) -> Sequence[AgentSigningKey]:
        raise NotImplementedError


class FileAgentSigningKeyProvider(AgentSigningKeyProvider):
    """从安装目录外的 0600 凭据文件读取 current/previous key。"""

    def __init__(self, path: Optional[str] = None):
        self.path = Path(path or os.environ.get(
            "AGENT_SIGNING_KEY_FILE",
            "/etc/easyaiot/node-agent/agent-signing-key",
        ))

    def keys(self, node_id: int) -> Sequence[AgentSigningKey]:
        try:
            self._check_permissions()
            values = self._read_values()
            configured_node = values.get("nodeId")
            if configured_node and int(configured_node) != int(node_id):
                return ()
            current_id = values.get("currentKeyId", "").strip()
            current = values.get("currentKey", "").encode("utf-8")
            if not current_id or len(current) < 32:
                return ()
            result = [AgentSigningKey(int(node_id), current_id, current)]
            previous_id = values.get("previousKeyId", "").strip()
            previous = values.get("previousKey", "").encode("utf-8")
            if previous_id and len(previous) >= 32 and previous_id != current_id:
                result.append(AgentSigningKey(int(node_id), previous_id, previous))
            return tuple(result)
        except (ValueError, UnicodeError):
            return ()

    def _check_permissions(self) -> None:
        mode = stat.S_IMODE(self.path.stat().st_mode)
        if os.name != "nt" and mode & 0o077:
            raise PermissionError("agent signing key file must not be group/world readable")

    def _read_values(self) -> Dict[str, str]:
        values: Dict[str, str] = {}
        for raw in self.path.read_text(encoding="utf-8").splitlines():
            line = raw.strip()
            if not line or line.startswith("#") or "=" not in line:
                continue
            key, value = line.split("=", 1)
            values[key.strip()] = value.strip()
        return values


class InMemoryAgentSigningKeyProvider(AgentSigningKeyProvider):
    """仅用于单元测试和本地协议模拟，不作为生产配置源。"""

    def __init__(self, keys: Iterable[AgentSigningKey]):
        self._keys = tuple(keys)

    def keys(self, node_id: int) -> Sequence[AgentSigningKey]:
        return tuple(key for key in self._keys if key.node_id == int(node_id))


class PersistentNonceStore:
    """SQLite 持久 nonce 窗口，重启后仍拒绝 TTL 窗口内的重放。"""

    def __init__(self, path: str, ttl_seconds: int = DEFAULT_REPLAY_TTL_SECONDS):
        self.path = Path(path)
        self.ttl_seconds = int(ttl_seconds)
        self._lock = threading.Lock()
        self.path.parent.mkdir(parents=True, exist_ok=True)
        if os.name != "nt":
            os.chmod(self.path.parent, 0o700)
        with self._connection() as conn:
            conn.execute(
                "CREATE TABLE IF NOT EXISTS replay_nonce ("
                "nonce_hash TEXT PRIMARY KEY, expires_at INTEGER NOT NULL)"
            )
        if os.name != "nt":
            os.chmod(self.path, 0o600)

    def claim(self, nonce_hash: str, now: Optional[int] = None) -> bool:
        current = int(time.time() if now is None else now)
        with self._lock:
            try:
                with self._connection() as conn:
                    conn.execute("DELETE FROM replay_nonce WHERE expires_at <= ?", (current,))
                    cursor = conn.execute(
                        "INSERT OR IGNORE INTO replay_nonce(nonce_hash, expires_at) VALUES (?, ?)",
                        (nonce_hash, current + self.ttl_seconds),
                    )
                    return cursor.rowcount == 1
            except sqlite3.Error as exc:
                raise AgentAuthError("AGENT_AUTH_NONCE_STORE_UNAVAILABLE") from exc

    def _connection(self) -> sqlite3.Connection:
        conn = sqlite3.connect(str(self.path), timeout=5, isolation_level=None)
        conn.execute("PRAGMA busy_timeout=5000")
        return conn


def body_sha256(body: bytes) -> str:
    return hashlib.sha256(body or b"").hexdigest()


def canonical_agent_request(
    method: str,
    path: str,
    timestamp: str,
    nonce: str,
    body_hash: str,
    node_id: int,
    key_id: str,
) -> bytes:
    """TD-001/ADR-018 固定 canonical request（每项一行，UTF-8）。"""

    return (
        f"{method.upper()}\n{path}\n{timestamp}\n{nonce}\n{body_hash}\n"
        f"{int(node_id)}\n{key_id}"
    ).encode("utf-8")


def sign_agent_request(
    method: str,
    path: str,
    body: bytes,
    node_id: int,
    key: AgentSigningKey,
    *,
    timestamp: Optional[int] = None,
    nonce: Optional[str] = None,
) -> Dict[str, str]:
    if key.node_id != int(node_id):
        raise AgentAuthError("AGENT_SIGNING_KEY_UNAVAILABLE")
    stamp = str(int(time.time() if timestamp is None else timestamp))
    request_nonce = nonce or secrets.token_hex(16)
    if not _HEX_128.fullmatch(request_nonce):
        raise AgentAuthError("AGENT_AUTH_MISSING")
    digest = body_sha256(body)
    signature = hmac.new(
        key.secret,
        canonical_agent_request(method, path, stamp, request_nonce, digest, node_id, key.key_id),
        hashlib.sha256,
    ).hexdigest()
    return {
        NODE_ID_HEADER: str(node_id),
        KEY_ID_HEADER: key.key_id,
        TIMESTAMP_HEADER: stamp,
        NONCE_HEADER: request_nonce,
        BODY_SHA256_HEADER: digest,
        SIGNATURE_HEADER: signature,
    }


def verify_agent_request(
    method: str,
    path: str,
    body: bytes,
    headers: Mapping[str, str],
    key_provider: AgentSigningKeyProvider,
    nonce_store: PersistentNonceStore,
    *,
    now: Optional[int] = None,
    max_clock_skew_seconds: int = DEFAULT_MAX_CLOCK_SKEW_SECONDS,
) -> AgentSigningKey:
    """验证 collector 配置请求；任何失败都返回稳定错误码并 fail-closed。"""

    normalized = {str(k).lower(): str(v) for k, v in headers.items()}

    def header(name: str) -> str:
        return normalized.get(name.lower(), "").strip()

    node_text = header(NODE_ID_HEADER)
    key_id = header(KEY_ID_HEADER)
    timestamp = header(TIMESTAMP_HEADER)
    nonce = header(NONCE_HEADER)
    expected_body_hash = header(BODY_SHA256_HEADER).lower()
    signature = header(SIGNATURE_HEADER).lower()
    if not node_text or not key_id or not timestamp or not nonce or not expected_body_hash or not signature:
        raise AgentAuthError("AGENT_AUTH_MISSING")
    try:
        node_id = int(node_text)
        request_time = int(timestamp)
    except ValueError as exc:
        raise AgentAuthError("AGENT_AUTH_MISSING") from exc
    current_time = int(time.time() if now is None else now)
    if abs(current_time - request_time) > int(max_clock_skew_seconds):
        raise AgentAuthError("AGENT_AUTH_EXPIRED")
    if not _HEX_128.fullmatch(nonce) or not _HEX_256.fullmatch(expected_body_hash):
        raise AgentAuthError("AGENT_AUTH_MISSING")
    actual_body_hash = body_sha256(body)
    if not hmac.compare_digest(expected_body_hash, actual_body_hash):
        raise AgentAuthError("AGENT_AUTH_BODY_HASH_MISMATCH")
    if not _HEX_256.fullmatch(signature):
        raise AgentAuthError("AGENT_AUTH_SIGNATURE_INVALID")

    selected: Optional[AgentSigningKey] = None
    canonical = canonical_agent_request(
        method, path, timestamp, nonce, actual_body_hash, node_id, key_id
    )
    for candidate in key_provider.keys(node_id):
        if candidate.key_id != key_id:
            continue
        expected = hmac.new(candidate.secret, canonical, hashlib.sha256).hexdigest()
        if hmac.compare_digest(expected, signature):
            selected = candidate
            break
    if selected is None:
        known_key = any(candidate.key_id == key_id for candidate in key_provider.keys(node_id))
        raise AgentAuthError(
            "AGENT_AUTH_SIGNATURE_INVALID" if known_key else "AGENT_AUTH_KEY_UNKNOWN"
        )

    nonce_hash = hashlib.sha256(
        f"{node_id}:{key_id}:{nonce}".encode("utf-8")
    ).hexdigest()
    if not nonce_store.claim(nonce_hash, now=current_time):
        raise AgentAuthError("AGENT_AUTH_REPLAYED")
    return selected
