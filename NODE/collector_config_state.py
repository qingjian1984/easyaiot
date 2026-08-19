"""Local collector configuration state machine.

The NODE configuration API accepts one closed envelope and stores the exact
UTF-8 canonical payload bytes.  This module deliberately does not apply a
configuration or manufacture an ``active``/``APPLIED`` state: OPEN03-05 owns
the desired-side hand-off and the atomic primitives used by the later
collector provider package.

The implementation uses only the Python standard library for JCS-compatible
canonicalisation of the 1.1 snapshot (the schema permits integers, strings,
booleans, arrays and objects, but no JSON floating-point values) and for the
per-workload file lock.  No external package is silently required.
"""

from __future__ import annotations

import hashlib
import json
import os
import re
import stat
import threading
import uuid
from contextlib import contextmanager
from dataclasses import dataclass
from pathlib import Path
from typing import Any, Dict, Iterator, Mapping, Optional, Sequence, Tuple

from jsonschema import Draft202012Validator, FormatChecker

from collector_workload import collector_config_directory, collector_workload_identity


REQUEST_MAX_BYTES = 4 * 1024 * 1024
PAYLOAD_MAX_BYTES = 2 * 1024 * 1024
SCHEMA_VERSION = "1.1"
CANONICALIZATION_VERSION = "jcs-rfc8785-v1"
MAX_CONFIG_VERSION = 9_223_372_036_854_775_807
LINUX_CONFIG_DIRECTORY_MODE = 0o2770
LINUX_CONFIG_FILE_MODE = 0o660

ENVELOPE_FIELDS = frozenset(
    {
        "workloadId",
        "configVersion",
        "schemaVersion",
        "canonicalizationVersion",
        "payloadCanonical",
        "payloadSha256",
        "canonicalLengthBytes",
    }
)
STATE_PAYLOAD_FILES = ("desired.json", "active.json")
OBSERVED_FIELDS = frozenset(
    {
        "workloadId",
        "status",
        "configVersion",
        "payloadSha256",
        "observedAt",
        "errorCode",
    }
)
OBSERVED_STATUSES = frozenset(
    {"WAITING_CONFIG", "AGENT_ACCEPTED", "APPLIED", "FAILED", "DEGRADED"}
)
LOWER_HEX_256 = re.compile(r"^[0-9a-f]{64}$")
SAFE_FILE_NAME = re.compile(r"^[0-9]+-[0-9a-f]{8,64}\.json$")
# Only the exact names emitted by _write_atomic for a history artifact are
# ignored on restart.  Other hidden/temp-looking files remain corruption.
ATOMIC_TEMP_NAME = re.compile(
    r"^\.[0-9]+-[0-9a-f]{8,64}\.json\.[0-9a-f]{32}\.(?:tmp|restore)$"
)
STABLE_ERROR_CODE = re.compile(r"^[A-Z][A-Z0-9_]{0,63}$")

ERROR_CODES = frozenset(
    {
        "COLLECTOR_CONFIG_REQUEST_INVALID",
        "COLLECTOR_CONFIG_TOO_LARGE",
        "COLLECTOR_CONFIG_HASH_MISMATCH",
        "COLLECTOR_CONFIG_SCHEMA_INVALID",
        "COLLECTOR_CONFIG_CANONICAL_INVALID",
        "CONFIG_VERSION_STALE",
        "CONFIG_VERSION_CONFLICT",
        "COLLECTOR_CONFIG_PATH_FORBIDDEN",
        "COLLECTOR_CONFIG_PERMISSION_INVALID",
        "COLLECTOR_CONFIG_STATE_CORRUPT",
        "COLLECTOR_CONFIG_WRITE_FAILED",
        "COLLECTOR_WORKLOAD_NOT_FOUND",
    }
)


class CollectorConfigStateError(ValueError):
    """Stable error that is safe to expose at the HTTP boundary."""

    def __init__(self, code: str):
        super().__init__(code if code in ERROR_CODES else "COLLECTOR_CONFIG_STATE_CORRUPT")
        self.code = code if code in ERROR_CODES else "COLLECTOR_CONFIG_STATE_CORRUPT"


class _DuplicateKey(ValueError):
    pass


class _InvalidConstant(ValueError):
    pass


def _strict_object_pairs(pairs: Sequence[Tuple[str, Any]]) -> Dict[str, Any]:
    result: Dict[str, Any] = {}
    for key, value in pairs:
        if key in result:
            raise _DuplicateKey(key)
        result[key] = value
    return result


def _reject_constant(value: str) -> Any:
    raise _InvalidConstant(value)


def _parse_json(raw: bytes, *, require_object: bool = False) -> Any:
    """Parse JSON without duplicate keys, NaN/Infinity, or implicit floats."""

    try:
        text = raw.decode("utf-8")
        value = json.loads(
            text,
            object_pairs_hook=_strict_object_pairs,
            parse_constant=_reject_constant,
            # ConfigSnapshot 1.1 contains only integer JSON numbers.  Rejecting
            # floats avoids accepting 1.0 and then producing a different JCS
            # number spelling with the stdlib encoder.
            parse_float=lambda value: (_ for _ in ()).throw(ValueError(value)),
        )
    except (UnicodeDecodeError, json.JSONDecodeError, TypeError, ValueError):
        raise CollectorConfigStateError(
            "COLLECTOR_CONFIG_REQUEST_INVALID"
        ) from None
    if require_object and not isinstance(value, dict):
        raise CollectorConfigStateError("COLLECTOR_CONFIG_REQUEST_INVALID")
    return value


def _canonical_json(value: Any) -> bytes:
    """Return the RFC 8785 spelling for the supported snapshot value domain."""

    try:
        # The ConfigSnapshot 1.1 schema has no JSON numbers other than
        # integers.  Python's JSON encoder emits those integers verbatim and,
        # with ensure_ascii=False and compact separators, matches JCS for the
        # remaining permitted JSON value types.
        return json.dumps(
            value,
            ensure_ascii=False,
            sort_keys=True,
            separators=(",", ":"),
            allow_nan=False,
        ).encode("utf-8")
    except (UnicodeEncodeError, TypeError, ValueError, OverflowError):
        raise CollectorConfigStateError("COLLECTOR_CONFIG_CANONICAL_INVALID") from None


def _schema_validator() -> Draft202012Validator:
    schema_path = Path(__file__).resolve().parent / "schemas" / "collector-config-snapshot-v1.1.json"
    try:
        schema = json.loads(schema_path.read_text(encoding="utf-8"))
        validator = Draft202012Validator(schema, format_checker=FormatChecker())
        validator.check_schema(schema)
        return validator
    except Exception:
        raise CollectorConfigStateError("COLLECTOR_CONFIG_SCHEMA_INVALID") from None


def _is_positive_int(value: Any) -> bool:
    return isinstance(value, int) and not isinstance(value, bool) and value > 0


def _safe_workload(workload_id: Any) -> str:
    if not isinstance(workload_id, str):
        raise CollectorConfigStateError("COLLECTOR_CONFIG_REQUEST_INVALID")
    try:
        # This is the single identity implementation used by OPEN03-04.
        collector_workload_identity(workload_id)
    except Exception:
        raise CollectorConfigStateError("COLLECTOR_CONFIG_PATH_FORBIDDEN") from None
    return workload_id


@dataclass(frozen=True)
class ConfigArtifact:
    workload_id: str
    config_version: int
    schema_version: str
    payload_sha256: str
    canonical_length_bytes: int
    canonical_bytes: bytes

    def summary(self) -> Mapping[str, Any]:
        return {
            "present": True,
            "schemaVersion": self.schema_version,
            "configVersion": self.config_version,
            "payloadSha256": self.payload_sha256,
            "canonicalLengthBytes": self.canonical_length_bytes,
        }


@dataclass(frozen=True)
class ConfigPutResult:
    status: str
    workload_id: str
    config_version: int
    payload_sha256: str

    def as_dict(self) -> Mapping[str, Any]:
        return {
            "status": self.status,
            "workloadId": self.workload_id,
            "configVersion": self.config_version,
            "payloadSha256": self.payload_sha256,
        }


def validate_config_envelope(raw_body: bytes) -> Tuple[ConfigArtifact, Mapping[str, Any]]:
    """Validate the complete envelope before any state path is touched."""

    if not isinstance(raw_body, (bytes, bytearray)):
        raise CollectorConfigStateError("COLLECTOR_CONFIG_REQUEST_INVALID")
    raw = bytes(raw_body)
    if len(raw) > REQUEST_MAX_BYTES:
        raise CollectorConfigStateError("COLLECTOR_CONFIG_TOO_LARGE")
    envelope = _parse_json(raw, require_object=True)
    if frozenset(envelope.keys()) != ENVELOPE_FIELDS:
        raise CollectorConfigStateError("COLLECTOR_CONFIG_REQUEST_INVALID")

    workload_id = _safe_workload(envelope.get("workloadId"))
    config_version = envelope.get("configVersion")
    if not _is_positive_int(config_version) or config_version > MAX_CONFIG_VERSION:
        raise CollectorConfigStateError("COLLECTOR_CONFIG_REQUEST_INVALID")
    if envelope.get("schemaVersion") != SCHEMA_VERSION:
        raise CollectorConfigStateError("COLLECTOR_CONFIG_SCHEMA_INVALID")
    if envelope.get("canonicalizationVersion") != CANONICALIZATION_VERSION:
        raise CollectorConfigStateError("COLLECTOR_CONFIG_CANONICAL_INVALID")
    payload_canonical = envelope.get("payloadCanonical")
    if not isinstance(payload_canonical, str):
        raise CollectorConfigStateError("COLLECTOR_CONFIG_REQUEST_INVALID")
    try:
        payload_bytes = payload_canonical.encode("utf-8")
    except UnicodeEncodeError:
        raise CollectorConfigStateError("COLLECTOR_CONFIG_CANONICAL_INVALID") from None
    if len(payload_bytes) > PAYLOAD_MAX_BYTES:
        raise CollectorConfigStateError("COLLECTOR_CONFIG_TOO_LARGE")

    expected_length = envelope.get("canonicalLengthBytes")
    if not _is_positive_int(expected_length) or expected_length > PAYLOAD_MAX_BYTES:
        raise CollectorConfigStateError("COLLECTOR_CONFIG_REQUEST_INVALID")
    if expected_length != len(payload_bytes):
        raise CollectorConfigStateError("COLLECTOR_CONFIG_HASH_MISMATCH")
    expected_hash = envelope.get("payloadSha256")
    if not isinstance(expected_hash, str) or not LOWER_HEX_256.fullmatch(expected_hash):
        raise CollectorConfigStateError("COLLECTOR_CONFIG_REQUEST_INVALID")
    actual_hash = hashlib.sha256(payload_bytes).hexdigest()
    if actual_hash != expected_hash:
        raise CollectorConfigStateError("COLLECTOR_CONFIG_HASH_MISMATCH")

    payload = _parse_json(payload_bytes, require_object=True)
    errors = sorted(_schema_validator().iter_errors(payload), key=lambda error: list(error.path))
    if errors:
        raise CollectorConfigStateError("COLLECTOR_CONFIG_SCHEMA_INVALID")
    if (
        payload.get("workloadId") != workload_id
        or payload.get("configVersion") != config_version
        or payload.get("schemaVersion") != SCHEMA_VERSION
    ):
        raise CollectorConfigStateError("COLLECTOR_CONFIG_CANONICAL_INVALID")
    if _canonical_json(payload) != payload_bytes:
        raise CollectorConfigStateError("COLLECTOR_CONFIG_CANONICAL_INVALID")

    return (
        ConfigArtifact(
            workload_id=workload_id,
            config_version=config_version,
            schema_version=SCHEMA_VERSION,
            payload_sha256=expected_hash,
            canonical_length_bytes=len(payload_bytes),
            canonical_bytes=payload_bytes,
        ),
        envelope,
    )


def _path_has_symlink_component(path: Path) -> bool:
    current = Path(path.anchor) if path.anchor else Path()
    parts = path.parts[1:] if path.anchor else path.parts
    for part in parts:
        current = current / part
        try:
            if current.is_symlink():
                return True
        except OSError:
            return True
    return False


def _validate_state_root(value: Path) -> Path:
    path = Path(value)
    if not path.is_absolute() or any(part == ".." for part in path.parts):
        raise CollectorConfigStateError("COLLECTOR_CONFIG_PATH_FORBIDDEN")
    if _path_has_symlink_component(path):
        raise CollectorConfigStateError("COLLECTOR_CONFIG_PATH_FORBIDDEN")
    return path


def _permission_error() -> CollectorConfigStateError:
    return CollectorConfigStateError("COLLECTOR_CONFIG_PERMISSION_INVALID")


def _ensure_directory_mode(path: Path, *, apply: bool = False) -> None:
    """Apply/check the Linux shared-group mode; Windows remains portable-only."""

    if os.name == "nt":
        return
    try:
        if apply:
            os.chmod(path, LINUX_CONFIG_DIRECTORY_MODE)
        actual = stat.S_IMODE(path.stat().st_mode)
    except (OSError, ValueError):
        raise _permission_error() from None
    if actual != LINUX_CONFIG_DIRECTORY_MODE:
        raise _permission_error()


def _ensure_file_mode(path: Path, *, apply: bool = False) -> None:
    """Check the fixed 0660 shared-group mode on POSIX only."""

    if os.name == "nt":
        return
    try:
        if apply:
            os.chmod(path, LINUX_CONFIG_FILE_MODE)
        actual = stat.S_IMODE(path.stat().st_mode)
    except (OSError, ValueError):
        raise _permission_error() from None
    if actual != LINUX_CONFIG_FILE_MODE:
        raise _permission_error()


def _fsync_directory(directory: Path) -> None:
    """Durability primitive.

    Linux/Unix uses a directory descriptor.  Windows' Python runtime does not
    support opening a directory with ``os.open(..., O_RDONLY)`` reliably; the
    local Windows run therefore performs the file flush and atomic replace but
    does not claim Linux directory-fsync qualification.  Production Windows
    qualification remains an explicit runtime gate.
    """

    if os.name == "nt":
        return
    fd = os.open(str(directory), os.O_RDONLY)
    try:
        os.fsync(fd)
    finally:
        os.close(fd)


def _write_atomic(target: Path, data: bytes) -> None:
    """Write exact bytes with same-directory temp, fsync and atomic replace."""

    directory = target.parent
    if not directory.exists() or directory.is_symlink() or not directory.is_dir():
        raise CollectorConfigStateError("COLLECTOR_CONFIG_PATH_FORBIDDEN")
    temp = directory / f".{target.name}.{uuid.uuid4().hex}.tmp"
    fd: Optional[int] = None
    replaced = False
    old_data: Optional[bytes] = None
    try:
        if target.exists():
            if target.is_symlink() or not target.is_file():
                raise CollectorConfigStateError("COLLECTOR_CONFIG_PATH_FORBIDDEN")
            _ensure_file_mode(target)
            old_data = target.read_bytes()
        fd = os.open(
            str(temp), os.O_WRONLY | os.O_CREAT | os.O_EXCL, LINUX_CONFIG_FILE_MODE
        )
        if os.name != "nt":
            os.chmod(temp, LINUX_CONFIG_FILE_MODE)
        _ensure_file_mode(temp, apply=True)
        with os.fdopen(fd, "wb", closefd=True) as stream:
            fd = None
            stream.write(data)
            stream.flush()
            os.fsync(stream.fileno())
        os.replace(str(temp), str(target))
        replaced = True
        _fsync_directory(directory)
        if not target.is_file() or target.is_symlink():
            raise CollectorConfigStateError("COLLECTOR_CONFIG_WRITE_FAILED")
        _ensure_file_mode(target)
    except Exception as exc:
        # A failure before replace leaves the old formal file untouched.  A
        # post-replace directory-fsync/crash failure must roll back both the
        # replacement and the first-write case; otherwise a caller could see
        # a failed PUT with a new formal state after restart.
        if replaced:
            try:
                if old_data is None:
                    target.unlink(missing_ok=True)
                else:
                    restore = directory / f".{target.name}.{uuid.uuid4().hex}.restore"
                    restore_fd = os.open(
                        str(restore), os.O_WRONLY | os.O_CREAT | os.O_EXCL, LINUX_CONFIG_FILE_MODE
                    )
                    if os.name != "nt":
                        os.chmod(restore, LINUX_CONFIG_FILE_MODE)
                    _ensure_file_mode(restore, apply=True)
                    with os.fdopen(restore_fd, "wb", closefd=True) as stream:
                        stream.write(old_data)
                        stream.flush()
                        os.fsync(stream.fileno())
                    os.replace(str(restore), str(target))
                    _fsync_directory(directory)
            except Exception:
                # The formal path is never reported as accepted.  Leave the
                # state for the next fail-closed integrity read rather than
                # claiming that the old bytes were restored.
                pass
        if isinstance(exc, CollectorConfigStateError) and not replaced:
            raise
        raise CollectorConfigStateError("COLLECTOR_CONFIG_WRITE_FAILED") from None
    finally:
        if fd is not None:
            try:
                os.close(fd)
            except OSError:
                pass
        try:
            if temp.exists() and not temp.is_symlink():
                temp.unlink()
        except OSError:
            pass


def _validate_observed_summary(value: Mapping[str, Any], workload_id: str) -> Mapping[str, Any]:
    """Validate the one closed observed summary stored per workload.

    ``workloadId`` is part of the on-disk summary so copying an observed file
    across workload directories is detected rather than silently accepted.
    Free-form error details are intentionally not part of this contract;
    callers report only a stable ``errorCode``.
    """

    if not isinstance(value, Mapping):
        raise CollectorConfigStateError("COLLECTOR_CONFIG_REQUEST_INVALID")
    if frozenset(value.keys()) - OBSERVED_FIELDS:
        raise CollectorConfigStateError("COLLECTOR_CONFIG_REQUEST_INVALID")
    safe_workload_id = _safe_workload(workload_id)
    if value.get("workloadId") != safe_workload_id:
        raise CollectorConfigStateError("COLLECTOR_CONFIG_PATH_FORBIDDEN")
    if value.get("status") not in OBSERVED_STATUSES:
        raise CollectorConfigStateError("COLLECTOR_CONFIG_REQUEST_INVALID")
    if "configVersion" in value and (
        not _is_positive_int(value["configVersion"])
        or value["configVersion"] > MAX_CONFIG_VERSION
    ):
        raise CollectorConfigStateError("COLLECTOR_CONFIG_REQUEST_INVALID")
    if "payloadSha256" in value and (
        not isinstance(value["payloadSha256"], str)
        or not LOWER_HEX_256.fullmatch(value["payloadSha256"])
    ):
        raise CollectorConfigStateError("COLLECTOR_CONFIG_REQUEST_INVALID")
    for key in ("observedAt", "errorCode"):
        if key in value and (
            not isinstance(value[key], str)
            or len(value[key]) > 128
            or any(ord(char) < 0x20 for char in value[key])
        ):
            raise CollectorConfigStateError("COLLECTOR_CONFIG_REQUEST_INVALID")
    if "errorCode" in value and not STABLE_ERROR_CODE.fullmatch(value["errorCode"]):
        raise CollectorConfigStateError("COLLECTOR_CONFIG_REQUEST_INVALID")
    return dict(value)


def _ensure_version_consistency(artifacts: Sequence[Optional[ConfigArtifact]]) -> None:
    """Reject a contradictory local version rather than masking it with PUT."""

    by_version: Dict[int, set[str]] = {}
    for artifact in artifacts:
        if artifact is None:
            continue
        by_version.setdefault(artifact.config_version, set()).add(artifact.payload_sha256)
    if any(len(hashes) > 1 for hashes in by_version.values()):
        raise CollectorConfigStateError("COLLECTOR_CONFIG_STATE_CORRUPT")


class _WorkloadFileLock:
    """Small cross-platform OS file lock with a process-local guard."""

    _guards: Dict[str, threading.RLock] = {}
    _guards_lock = threading.Lock()

    def __init__(self, path: Path):
        self.path = path
        with self._guards_lock:
            self._guard = self._guards.setdefault(str(path), threading.RLock())
        self._stream = None

    def __enter__(self) -> "_WorkloadFileLock":
        self._guard.acquire()
        try:
            existed = self.path.exists()
            if existed and (self.path.is_symlink() or not self.path.is_file()):
                raise CollectorConfigStateError("COLLECTOR_CONFIG_PATH_FORBIDDEN")
            flags = os.O_RDWR | os.O_CREAT
            no_follow = getattr(os, "O_NOFOLLOW", 0)
            if no_follow:
                flags |= no_follow
            fd = os.open(str(self.path), flags, LINUX_CONFIG_FILE_MODE)
            self._stream = os.fdopen(fd, "r+b", closefd=True)
            if self.path.is_symlink() or not self.path.is_file():
                raise CollectorConfigStateError("COLLECTOR_CONFIG_PATH_FORBIDDEN")
            _ensure_file_mode(self.path, apply=not existed)
            if self.path.stat().st_size == 0:
                self._stream.write(b"0")
                self._stream.flush()
                os.fsync(self._stream.fileno())
            self._stream.seek(0)
            if os.name == "nt":
                import msvcrt

                msvcrt.locking(self._stream.fileno(), msvcrt.LK_NBLCK, 1)
            else:
                import fcntl

                # Python ``lockf`` uses POSIX record locks (fcntl F_SETLKW),
                # which are interoperable with Java FileChannel.lock().  Do
                # not use flock here: it is a different lock namespace on
                # several Unix filesystems.
                fcntl.lockf(self._stream.fileno(), fcntl.LOCK_EX)
            return self
        except CollectorConfigStateError:
            if self._stream is not None:
                self._stream.close()
                self._stream = None
            self._guard.release()
            raise
        except Exception:
            if self._stream is not None:
                self._stream.close()
                self._stream = None
            self._guard.release()
            raise CollectorConfigStateError("COLLECTOR_CONFIG_WRITE_FAILED") from None

    def __exit__(self, exc_type, exc, tb) -> None:
        try:
            if self._stream is not None:
                try:
                    if os.name == "nt":
                        import msvcrt

                        self._stream.seek(0)
                        msvcrt.locking(self._stream.fileno(), msvcrt.LK_UNLCK, 1)
                    else:
                        import fcntl

                        fcntl.lockf(self._stream.fileno(), fcntl.LOCK_UN)
                finally:
                    self._stream.close()
                    self._stream = None
        finally:
            self._guard.release()


class CollectorConfigStateService:
    """State root and desired/active/observed atomic file primitives."""

    def __init__(self, state_root: Path):
        self.state_root = _validate_state_root(Path(state_root))

    @classmethod
    def from_env(cls, env: Optional[Mapping[str, str]] = None) -> "CollectorConfigStateService":
        values = os.environ if env is None else env
        raw = str(values.get("COLLECTOR_STATE_ROOT") or values.get("EASYAIOT_COLLECTOR_STATE_ROOT") or "").strip()
        if not raw:
            raise CollectorConfigStateError("COLLECTOR_CONFIG_PATH_FORBIDDEN")
        return cls(Path(raw))

    def _workload_dir(self, workload_id: str, *, create: bool) -> Path:
        workload_id = _safe_workload(workload_id)
        root = self.state_root
        if _path_has_symlink_component(root):
            raise CollectorConfigStateError("COLLECTOR_CONFIG_PATH_FORBIDDEN")
        if root.exists() and (root.is_symlink() or not root.is_dir()):
            raise CollectorConfigStateError("COLLECTOR_CONFIG_PATH_FORBIDDEN")
        directory = collector_config_directory(root, workload_id)
        identity_dir = directory.parent
        if _path_has_symlink_component(identity_dir) or _path_has_symlink_component(directory):
            raise CollectorConfigStateError("COLLECTOR_CONFIG_PATH_FORBIDDEN")
        if identity_dir.exists() and (identity_dir.is_symlink() or not identity_dir.is_dir()):
            raise CollectorConfigStateError("COLLECTOR_CONFIG_PATH_FORBIDDEN")
        if directory.exists() and (directory.is_symlink() or not directory.is_dir()):
            raise CollectorConfigStateError("COLLECTOR_CONFIG_PATH_FORBIDDEN")
        identity_existed = identity_dir.exists()
        directory_existed = directory.exists()
        if create:
            try:
                root.mkdir(parents=True, exist_ok=True)
                identity_dir.mkdir(mode=LINUX_CONFIG_DIRECTORY_MODE, exist_ok=True)
                directory.mkdir(mode=LINUX_CONFIG_DIRECTORY_MODE, exist_ok=True)
            except OSError:
                raise CollectorConfigStateError("COLLECTOR_CONFIG_WRITE_FAILED") from None
            if _path_has_symlink_component(root) or _path_has_symlink_component(identity_dir) or _path_has_symlink_component(directory):
                raise CollectorConfigStateError("COLLECTOR_CONFIG_PATH_FORBIDDEN")
        if identity_dir.exists():
            _ensure_directory_mode(identity_dir, apply=create and not identity_existed)
        if directory.exists():
            _ensure_directory_mode(directory, apply=create and not directory_existed)
        return directory

    @staticmethod
    def _formal_path(directory: Path, filename: str) -> Path:
        if filename not in (*STATE_PAYLOAD_FILES, "observed.json"):
            raise CollectorConfigStateError("COLLECTOR_CONFIG_PATH_FORBIDDEN")
        path = directory / filename
        if _path_has_symlink_component(path):
            raise CollectorConfigStateError("COLLECTOR_CONFIG_PATH_FORBIDDEN")
        return path

    def _read_payload_file(self, path: Path, workload_id: str) -> Optional[ConfigArtifact]:
        if not path.exists():
            return None
        if path.is_symlink() or not path.is_file():
            raise CollectorConfigStateError("COLLECTOR_CONFIG_STATE_CORRUPT")
        _ensure_file_mode(path)
        try:
            data = path.read_bytes()
            if len(data) > PAYLOAD_MAX_BYTES:
                raise ValueError
            payload = _parse_json(data, require_object=True)
            errors = sorted(_schema_validator().iter_errors(payload), key=lambda error: list(error.path))
            if errors or payload.get("workloadId") != workload_id:
                raise ValueError
            canonical = _canonical_json(payload)
            if canonical != data:
                raise ValueError
            version = payload.get("configVersion")
            if not _is_positive_int(version) or version > MAX_CONFIG_VERSION:
                raise ValueError
            return ConfigArtifact(
                workload_id=workload_id,
                config_version=version,
                schema_version=SCHEMA_VERSION,
                payload_sha256=hashlib.sha256(data).hexdigest(),
                canonical_length_bytes=len(data),
                canonical_bytes=data,
            )
        except CollectorConfigStateError as exc:
            if exc.code == "COLLECTOR_CONFIG_PERMISSION_INVALID":
                raise
            raise CollectorConfigStateError("COLLECTOR_CONFIG_STATE_CORRUPT") from None
        except (OSError, UnicodeError, ValueError, TypeError):
            raise CollectorConfigStateError("COLLECTOR_CONFIG_STATE_CORRUPT") from None

    def _read_observed(self, path: Path, workload_id: str) -> Optional[Mapping[str, Any]]:
        if not path.exists():
            return None
        if path.is_symlink() or not path.is_file():
            raise CollectorConfigStateError("COLLECTOR_CONFIG_STATE_CORRUPT")
        _ensure_file_mode(path)
        try:
            raw = path.read_bytes()
            value = _parse_json(raw, require_object=True)
            value = _validate_observed_summary(value, workload_id)
            # Stored summaries are compact canonical JSON too, but they are
            # intentionally not ConfigSnapshot canonical payloads.
            if _canonical_json(value) != raw:
                raise ValueError
            return dict(value)
        except CollectorConfigStateError as exc:
            if exc.code == "COLLECTOR_CONFIG_PERMISSION_INVALID":
                raise
            raise CollectorConfigStateError("COLLECTOR_CONFIG_STATE_CORRUPT") from None
        except (OSError, UnicodeError, ValueError, TypeError):
            raise CollectorConfigStateError("COLLECTOR_CONFIG_STATE_CORRUPT") from None

    def _read_history(self, directory: Path, workload_id: str) -> Tuple[ConfigArtifact, ...]:
        history = directory / "history"
        if history.is_symlink():
            raise CollectorConfigStateError("COLLECTOR_CONFIG_STATE_CORRUPT")
        if not history.exists():
            return ()
        if not history.is_dir() or _path_has_symlink_component(history):
            raise CollectorConfigStateError("COLLECTOR_CONFIG_STATE_CORRUPT")
        _ensure_directory_mode(history)
        artifacts = []
        for path in sorted(history.iterdir(), key=lambda item: item.name):
            # A crash can leave a same-directory atomic-write temp behind;
            # it is deliberately ignored and is never treated as state.
            if path.is_symlink():
                raise CollectorConfigStateError("COLLECTOR_CONFIG_STATE_CORRUPT")
            if ATOMIC_TEMP_NAME.fullmatch(path.name):
                continue
            if not path.is_file() or not SAFE_FILE_NAME.fullmatch(path.name):
                raise CollectorConfigStateError("COLLECTOR_CONFIG_STATE_CORRUPT")
            artifact = self._read_payload_file(path, workload_id)
            if artifact is None:
                raise CollectorConfigStateError("COLLECTOR_CONFIG_STATE_CORRUPT")
            expected_prefix = f"{artifact.config_version}-"
            if not path.name.startswith(expected_prefix) or not path.name.endswith(
                f"{artifact.payload_sha256[:8]}.json"
            ):
                raise CollectorConfigStateError("COLLECTOR_CONFIG_STATE_CORRUPT")
            artifacts.append(artifact)
        return tuple(artifacts)

    @contextmanager
    def _locked_workload(self, workload_id: str, *, create: bool) -> Iterator[Path]:
        # Resolve and vet the complete fixed lock path before creating a new
        # workload directory.  Invalid pre-existing lock paths therefore have
        # no state-directory side effect.
        directory = self._workload_dir(workload_id, create=False)
        lock_path = directory / ".state.lock"
        if _path_has_symlink_component(lock_path) or (lock_path.exists() and not lock_path.is_file()):
            raise CollectorConfigStateError("COLLECTOR_CONFIG_PATH_FORBIDDEN")
        if create and not directory.exists():
            directory = self._workload_dir(workload_id, create=True)
        with _WorkloadFileLock(lock_path):
            yield directory

    def put(self, raw_body: bytes) -> ConfigPutResult:
        # This call is intentionally before _locked_workload: invalid requests
        # must not create a workload directory or lock file.
        artifact, _envelope = validate_config_envelope(raw_body)
        with self._locked_workload(artifact.workload_id, create=True) as directory:
            desired = self._read_payload_file(
                self._formal_path(directory, "desired.json"), artifact.workload_id
            )
            active = self._read_payload_file(
                self._formal_path(directory, "active.json"), artifact.workload_id
            )
            history = self._read_history(directory, artifact.workload_id)
            self._read_observed(
                self._formal_path(directory, "observed.json"), artifact.workload_id
            )
            _ensure_version_consistency((desired, active, *history))
            existing = tuple(item for item in (desired, active) if item is not None)
            if existing:
                highest = max(item.config_version for item in existing)
                same_version = [item for item in existing if item.config_version == artifact.config_version]
                if highest > artifact.config_version:
                    raise CollectorConfigStateError("CONFIG_VERSION_STALE")
                if same_version:
                    if any(item.payload_sha256 != artifact.payload_sha256 for item in same_version):
                        raise CollectorConfigStateError("CONFIG_VERSION_CONFLICT")
                    return ConfigPutResult(
                        "IDEMPOTENT",
                        artifact.workload_id,
                        artifact.config_version,
                        artifact.payload_sha256,
                    )
            _write_atomic(self._formal_path(directory, "desired.json"), artifact.canonical_bytes)
            return ConfigPutResult(
                "ACCEPTED",
                artifact.workload_id,
                artifact.config_version,
                artifact.payload_sha256,
            )

    def get(self, workload_id: str) -> Mapping[str, Any]:
        workload_id = _safe_workload(workload_id)
        directory = self._workload_dir(workload_id, create=False)
        if not directory.exists():
            raise CollectorConfigStateError("COLLECTOR_WORKLOAD_NOT_FOUND")
        with self._locked_workload(workload_id, create=False) as locked_directory:
            desired = self._read_payload_file(
                self._formal_path(locked_directory, "desired.json"), workload_id
            )
            active = self._read_payload_file(
                self._formal_path(locked_directory, "active.json"), workload_id
            )
            history = self._read_history(locked_directory, workload_id)
            observed = self._read_observed(
                self._formal_path(locked_directory, "observed.json"), workload_id
            )
            _ensure_version_consistency((desired, active, *history))
            if desired is None and active is None and observed is None:
                raise CollectorConfigStateError("COLLECTOR_WORKLOAD_NOT_FOUND")
            return {
                "workloadId": workload_id,
                "desired": desired.summary() if desired else None,
                "active": active.summary() if active else None,
                "observed": observed,
            }

    def write_active(self, workload_id: str, payload_canonical: bytes) -> ConfigArtifact:
        """Future collector-provider primitive; never called by PUT."""

        artifact = self._validate_payload_bytes(payload_canonical, workload_id)
        with self._locked_workload(workload_id, create=True) as directory:
            _write_atomic(self._formal_path(directory, "active.json"), artifact.canonical_bytes)
        return artifact

    def write_history(self, workload_id: str, payload_canonical: bytes) -> Path:
        """Future collector-provider primitive preserving original bytes."""

        artifact = self._validate_payload_bytes(payload_canonical, workload_id)
        with self._locked_workload(workload_id, create=True) as directory:
            history = directory / "history"
            if history.is_symlink() or (history.exists() and not history.is_dir()):
                raise CollectorConfigStateError("COLLECTOR_CONFIG_PATH_FORBIDDEN")
            history_existed = history.exists()
            history.mkdir(mode=LINUX_CONFIG_DIRECTORY_MODE, exist_ok=True)
            if _path_has_symlink_component(history):
                raise CollectorConfigStateError("COLLECTOR_CONFIG_PATH_FORBIDDEN")
            _ensure_directory_mode(history, apply=not history_existed)
            target = history / f"{artifact.config_version}-{artifact.payload_sha256[:8]}.json"
            _write_atomic(target, artifact.canonical_bytes)
            return target

    def write_observed(self, workload_id: str, observed: Mapping[str, Any]) -> None:
        """Future collector-provider primitive for a closed status summary."""

        workload_id = _safe_workload(workload_id)
        if not isinstance(observed, Mapping) or not observed:
            raise CollectorConfigStateError("COLLECTOR_CONFIG_REQUEST_INVALID")
        stored = dict(observed)
        if "workloadId" in stored and stored["workloadId"] != workload_id:
            raise CollectorConfigStateError("COLLECTOR_CONFIG_PATH_FORBIDDEN")
        stored["workloadId"] = workload_id
        try:
            validated = _validate_observed_summary(stored, workload_id)
            data = _canonical_json(validated)
        except CollectorConfigStateError:
            raise CollectorConfigStateError("COLLECTOR_CONFIG_REQUEST_INVALID") from None
        if len(data) > 64 * 1024:
            raise CollectorConfigStateError("COLLECTOR_CONFIG_TOO_LARGE")
        # Re-use the exact same parser and validator before touching disk.
        parsed = _parse_json(data, require_object=True)
        _validate_observed_summary(parsed, workload_id)
        with self._locked_workload(workload_id, create=True) as directory:
            _write_atomic(self._formal_path(directory, "observed.json"), data)

    @staticmethod
    def _validate_payload_bytes(payload_canonical: bytes, workload_id: str) -> ConfigArtifact:
        if not isinstance(payload_canonical, (bytes, bytearray)):
            raise CollectorConfigStateError("COLLECTOR_CONFIG_REQUEST_INVALID")
        raw_payload = bytes(payload_canonical)
        if len(raw_payload) > PAYLOAD_MAX_BYTES:
            raise CollectorConfigStateError("COLLECTOR_CONFIG_TOO_LARGE")
        try:
            payload = _parse_json(raw_payload, require_object=True)
            errors = sorted(_schema_validator().iter_errors(payload), key=lambda error: list(error.path))
            if errors or payload.get("workloadId") != _safe_workload(workload_id):
                raise ValueError
            if _canonical_json(payload) != raw_payload:
                raise ValueError
            version = payload.get("configVersion")
            if not _is_positive_int(version) or version > MAX_CONFIG_VERSION:
                raise ValueError
        except CollectorConfigStateError:
            raise CollectorConfigStateError("COLLECTOR_CONFIG_CANONICAL_INVALID") from None
        except (ValueError, TypeError, UnicodeError):
            raise CollectorConfigStateError("COLLECTOR_CONFIG_CANONICAL_INVALID") from None
        return ConfigArtifact(
            workload_id=workload_id,
            config_version=version,
            schema_version=SCHEMA_VERSION,
            payload_sha256=hashlib.sha256(raw_payload).hexdigest(),
            canonical_length_bytes=len(raw_payload),
            canonical_bytes=raw_payload,
        )
