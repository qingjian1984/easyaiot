"""专用 collector WorkloadSpec 验证、计划生成和执行边界。

这个模块是 NODE collector 端点的唯一适配层。它只接受
``collector-workload-spec-v1.json`` 定义的闭合合同，然后从本地安装策略
派生不可变的 Linux Compose 或关闭默认的 Windows 固定命令计划。

远端请求不能提供 command、env、files、workDir、logDir、GPU、Compose
project、容器名、restart policy、JVM 参数或宿主机配置路径。brokerRef
只作为合同中的引用存在，绝不会被解析、写入计划或响应。
"""

from __future__ import annotations

import copy
import hashlib
import json
import os
import re
import stat
import subprocess
from dataclasses import dataclass, field
from decimal import Decimal, InvalidOperation
from pathlib import Path
from types import MappingProxyType
from typing import Any, Mapping, MutableMapping, Optional, Protocol, Sequence, Tuple

from jsonschema import Draft202012Validator


ERROR_CODES = frozenset(
    {
        "UNSUPPORTED_GENERIC_DEPLOY",
        "COLLECTOR_DEPLOY_CONFIGURATION_INVALID",
        "COLLECTOR_PROFILE_UNSUPPORTED",
        "COLLECTOR_WORKLOAD_SCHEMA_INVALID",
        "COLLECTOR_IMAGE_FORBIDDEN",
        "COLLECTOR_PATH_FORBIDDEN",
        "COLLECTOR_RESOURCE_LIMIT_EXCEEDED",
        "COLLECTOR_DEPLOY_FAILED",
    }
)

SCHEMA_RELATIVE_PATH = Path("schemas") / "collector-workload-spec-v1.json"
COLLECTOR_WORKLOAD_TYPE = "iot-sink-collector"
COLLECTOR_TARGET_CONFIG_PATH = "/var/lib/easyaiot/config/active.json"
COLLECTOR_CONFIG_CONTAINER_DIR = "/var/lib/easyaiot/config"
COLLECTOR_OUTBOX_CONTAINER_PATH = "/var/lib/easyaiot/outbox"
_SAFE_WORKLOAD_ID = re.compile(r"^[A-Za-z0-9][A-Za-z0-9._-]{0,127}$")
_DIGEST = re.compile(r"^sha256:[0-9a-f]{64}$")
_IMAGE_REPOSITORY = re.compile(r"^[a-z0-9][a-z0-9./_-]{0,254}$")
_SERIAL_CONTAINER = re.compile(r"^/dev/easyaiot/rs485-[0-9]{1,2}$")
_HEX_256 = re.compile(r"^[0-9a-f]{64}$")
_COLLECTOR_IDENTITY_NAMESPACE = "easyaiot-collector-workload-v1"


class CollectorDeploymentError(ValueError):
    """稳定的对外错误；异常文本不携带请求值、凭据或堆栈。"""

    def __init__(self, code: str):
        if code not in ERROR_CODES:
            code = "COLLECTOR_DEPLOY_FAILED"
        super().__init__(code)
        self.code = code


def _configuration_error() -> CollectorDeploymentError:
    return CollectorDeploymentError("COLLECTOR_DEPLOY_CONFIGURATION_INVALID")


def _path_error() -> CollectorDeploymentError:
    return CollectorDeploymentError("COLLECTOR_PATH_FORBIDDEN")


def _first_value(values: Mapping[str, str], *names: str) -> str:
    for name in names:
        value = values.get(name)
        if value is not None and str(value).strip():
            return str(value).strip()
    return ""


def _split_policy_list(value: str) -> Tuple[str, ...]:
    # 允许安装器使用逗号、换行或 PATH 风格分隔符；空白不进入策略值。
    parts = re.split(r"[,\r\n;]+", value)
    return tuple(item.strip() for item in parts if item.strip())


def _parse_positive_int(value: str) -> int:
    if not re.fullmatch(r"[1-9][0-9]*", value):
        raise _configuration_error()
    try:
        result = int(value, 10)
    except ValueError as exc:  # pragma: no cover - regex already guards this
        raise _configuration_error() from exc
    if result <= 0:
        raise _configuration_error()
    return result


def _parse_cpu(value: str) -> Decimal:
    if not re.fullmatch(r"(?:0|[1-9][0-9]*)(?:\.[0-9]+)?", value):
        raise _configuration_error()
    try:
        result = Decimal(value)
    except InvalidOperation as exc:  # pragma: no cover - regex already guards this
        raise _configuration_error() from exc
    if not result.is_finite() or result <= 0:
        raise _configuration_error()
    return result


def _resolve_root(value: str) -> Path:
    if not value or "\x00" in value:
        raise _configuration_error()
    path = Path(value)
    if not path.is_absolute() or any(part == ".." for part in path.parts):
        raise _configuration_error()
    return path.resolve(strict=False)


@dataclass(frozen=True)
class CollectorCapabilityPolicy:
    """安装侧 capability；所有资源配额必须显式注入。"""

    profile: str
    allowed_images: Tuple[str, ...]
    collector_root: Path
    state_root: Path
    serial_allowlist: Tuple[str, ...]
    max_cpu_cores: Decimal
    max_memory_bytes: int
    windows_enabled: bool = False
    windows_java_path: Optional[Path] = None
    windows_jar_path: Optional[Path] = None
    windows_runtime_policy_path: Optional[Path] = None
    windows_runtime_policy_id: str = ""

    def __post_init__(self) -> None:
        profile = str(self.profile).strip().lower()
        if profile not in {"standard", "full", "mini"}:
            raise _configuration_error()
        object.__setattr__(self, "profile", profile)
        if not self.allowed_images:
            raise _configuration_error()
        for image in self.allowed_images:
            if "@" not in image:
                raise _configuration_error()
            repository, digest = image.rsplit("@", 1)
            if not _IMAGE_REPOSITORY.fullmatch(repository) or not _DIGEST.fullmatch(digest):
                raise _configuration_error()
        if not self.serial_allowlist:
            raise _configuration_error()
        if self.max_cpu_cores <= 0 or not self.max_cpu_cores.is_finite():
            raise _configuration_error()
        if self.max_memory_bytes <= 0:
            raise _configuration_error()
        if self.windows_enabled:
            paths = (
                self.windows_java_path,
                self.windows_jar_path,
                self.windows_runtime_policy_path,
            )
            if any(path is None or not path.is_absolute() for path in paths):
                raise _configuration_error()
            if not str(self.windows_runtime_policy_id).strip():
                raise _configuration_error()

    def image_allowed(self, repository: str, digest: str) -> bool:
        # repository + digest 是不可拆分的 allowlist 条目；不做 repository-only 匹配。
        return f"{repository}@{digest}" in self.allowed_images


def load_collector_policy(env: Optional[Mapping[str, str]] = None) -> CollectorCapabilityPolicy:
    """从安装侧环境读取策略，缺少任一生产必需值即 fail-closed。"""

    values: Mapping[str, str] = os.environ if env is None else env
    profile = _first_value(values, "COLLECTOR_DEPLOY_PROFILE", "EASYAIOT_DEPLOY_PROFILE")
    if profile.lower() == "mini":
        raise CollectorDeploymentError("COLLECTOR_PROFILE_UNSUPPORTED")
    images_value = _first_value(values, "COLLECTOR_IMAGE_ALLOWLIST", "COLLECTOR_ALLOWED_IMAGES")
    root_value = _first_value(values, "COLLECTOR_ROOT", "EASYAIOT_COLLECTOR_ROOT")
    state_root_value = _first_value(values, "COLLECTOR_STATE_ROOT", "EASYAIOT_COLLECTOR_STATE_ROOT")
    serial_value = _first_value(values, "COLLECTOR_SERIAL_ALLOWLIST")
    cpu_value = _first_value(values, "COLLECTOR_CPU_MAX", "COLLECTOR_CPU_MAX_CORES")
    memory_value = _first_value(
        values,
        "COLLECTOR_MEMORY_MAX_BYTES",
        "COLLECTOR_MEMORY_MAX",
    )
    if not all((profile, images_value, root_value, state_root_value, serial_value, cpu_value, memory_value)):
        raise _configuration_error()

    images = _split_policy_list(images_value)
    serials = _split_policy_list(serial_value)
    if not images or not serials:
        raise _configuration_error()
    max_cpu = _parse_cpu(cpu_value)
    max_memory = _parse_positive_int(memory_value)
    windows_enabled = _first_value(values, "COLLECTOR_WINDOWS_ENABLED").lower() in {
        "1",
        "true",
        "yes",
    }
    windows_java_path: Optional[Path] = None
    windows_jar_path: Optional[Path] = None
    windows_runtime_policy_path: Optional[Path] = None
    windows_runtime_policy_id = ""
    if windows_enabled:
        java_value = _first_value(values, "COLLECTOR_WINDOWS_JAVA")
        jar_value = _first_value(values, "COLLECTOR_WINDOWS_JAR")
        runtime_policy_value = _first_value(values, "COLLECTOR_WINDOWS_RUNTIME_POLICY")
        windows_runtime_policy_id = _first_value(values, "COLLECTOR_WINDOWS_RUNTIME_POLICY_ID")
        if not all((java_value, jar_value, runtime_policy_value, windows_runtime_policy_id)):
            raise _configuration_error()
        windows_java_path = _resolve_root(java_value)
        windows_jar_path = _resolve_root(jar_value)
        windows_runtime_policy_path = _resolve_root(runtime_policy_value)
    return CollectorCapabilityPolicy(
        profile=profile,
        allowed_images=tuple(images),
        collector_root=_resolve_root(root_value),
        state_root=_resolve_root(state_root_value),
        serial_allowlist=tuple(serials),
        max_cpu_cores=max_cpu,
        max_memory_bytes=max_memory,
        windows_enabled=windows_enabled,
        windows_java_path=windows_java_path,
        windows_jar_path=windows_jar_path,
        windows_runtime_policy_path=windows_runtime_policy_path,
        windows_runtime_policy_id=windows_runtime_policy_id,
    )


def schema_path() -> Path:
    return Path(__file__).resolve().parent / SCHEMA_RELATIVE_PATH


def schema_bytes() -> bytes:
    try:
        return schema_path().read_bytes()
    except OSError as exc:
        raise _configuration_error() from exc


def _schema_validator() -> Draft202012Validator:
    try:
        schema = json.loads(schema_bytes().decode("utf-8"))
        validator = Draft202012Validator(schema)
        validator.check_schema(schema)
        return validator
    except Exception as exc:
        if isinstance(exc, CollectorDeploymentError):
            raise
        raise _configuration_error() from exc


def _canonical_json(value: Mapping[str, Any]) -> bytes:
    try:
        return json.dumps(
            value,
            ensure_ascii=False,
            sort_keys=True,
            separators=(",", ":"),
            allow_nan=False,
        ).encode("utf-8")
    except (TypeError, ValueError) as exc:
        raise CollectorDeploymentError("COLLECTOR_WORKLOAD_SCHEMA_INVALID") from exc


def _freeze(value: Any) -> Any:
    """递归冻结计划结构，避免 executor/调用方改写远端合同派生值。"""

    if isinstance(value, dict):
        return MappingProxyType({key: _freeze(item) for key, item in value.items()})
    if isinstance(value, list):
        return tuple(_freeze(item) for item in value)
    return value


def _thaw(value: Any) -> Any:
    """只为 executor 的受控内存副本或响应视图生成普通可变结构。"""

    if isinstance(value, Mapping):
        return {key: _thaw(item) for key, item in value.items()}
    if isinstance(value, tuple):
        return [_thaw(item) for item in value]
    return value


def _reject_raw_parent(path: str) -> None:
    # 对任何远端路径输入先拒绝显式父目录，避免平台路径归一化产生旁路。
    if "\x00" in path or any(part == ".." for part in Path(path).parts):
        raise _path_error()


def _is_symlink_component(path: Path) -> bool:
    current = Path(path.anchor) if path.anchor else Path()
    for part in path.parts[1:] if path.anchor else path.parts:
        current = current / part
        try:
            if current.is_symlink():
                return True
        except OSError:
            return True
    return False


def _serial_symlink_is_allowed(path: Path, resolved: Path) -> bool:
    """只允许 Linux 稳定 by-id symlink，且解析目标仍在 /dev 内。"""

    by_id_root = Path("/dev/serial/by-id")
    dev_root = Path("/dev")
    try:
        path.relative_to(by_id_root)
        resolved.relative_to(dev_root)
        return True
    except ValueError:
        return False


def _safe_workload_id(value: Any) -> str:
    if not isinstance(value, str) or not _SAFE_WORKLOAD_ID.fullmatch(value):
        raise CollectorDeploymentError("COLLECTOR_WORKLOAD_SCHEMA_INVALID")
    if value in {".", ".."} or "/" in value or "\\" in value:
        raise _path_error()
    return value


def collector_workload_identity(workload_id: Any) -> str:
    """Return the fixed safe identity shared by deploy and local state.

    The identity is derived only from the validated, original workload ID and
    the namespace constant.  Configuration/spec/image hashes must never enter
    this value: a workload keeps the same local directory and Compose project
    when its desired configuration changes.
    """

    value = _safe_workload_id(workload_id)
    readable_id = re.sub(r"[^a-z0-9]+", "-", value.lower()).strip("-") or "workload"
    readable_id = (readable_id[:20]).ljust(20, "0")
    identity_hash = hashlib.sha256(
        f"{_COLLECTOR_IDENTITY_NAMESPACE}\x00{value}".encode("utf-8")
    ).hexdigest()[:20]
    return f"eaiot-c-{readable_id}-{identity_hash}"


def collector_config_directory(state_root: Path, workload_id: Any) -> Path:
    """Return the one host config directory shared by deploy and state code.

    The path is deliberately derived from the same safe workload identity used
    for the Compose project.  Callers must never concatenate the raw workload
    id or independently decide whether the ``config`` component is present.
    """

    if not isinstance(state_root, Path):
        state_root = Path(state_root)
    if not state_root.is_absolute() or any(part == ".." for part in state_root.parts):
        raise _path_error()
    if "\x00" in str(state_root):
        raise _path_error()
    identity = collector_workload_identity(workload_id)
    directory = state_root / identity / "config"
    try:
        directory.relative_to(state_root)
    except ValueError as exc:
        raise _path_error() from exc
    return directory


def _validate_path_policy(spec: Mapping[str, Any], policy: CollectorCapabilityPolicy, workload_id: str) -> Tuple[Path, Path]:
    collector_root = policy.collector_root
    state_root = policy.state_root
    # Configuration state and the durable outbox must have independent host
    # roots.  Equality or ancestor/descendant nesting would let a workload
    # volume replace or traverse the Agent's config state, so reject it before
    # deriving any workload paths.  Both roots are already resolved by the
    # installation-policy loader; this comparison is deliberately fail-closed.
    try:
        roots_overlap = collector_root == state_root
        if not roots_overlap:
            try:
                collector_root.relative_to(state_root)
                roots_overlap = True
            except ValueError:
                try:
                    state_root.relative_to(collector_root)
                    roots_overlap = True
                except ValueError:
                    pass
        if roots_overlap:
            raise _path_error()
    except (TypeError, ValueError):
        raise _path_error() from None
    workload_outbox = (collector_root / workload_id / "outbox").resolve(strict=False)
    workload_config = collector_config_directory(state_root, workload_id)
    try:
        workload_outbox.relative_to(collector_root)
        workload_config.relative_to(state_root)
    except ValueError as exc:
        raise _path_error() from exc
    if _is_symlink_component(state_root) or _is_symlink_component(workload_config):
        raise _path_error()

    volumes = spec.get("volumes")
    if not isinstance(volumes, list) or len(volumes) != 1:
        raise _path_error()
    volume = volumes[0]
    if not isinstance(volume, dict):
        raise _path_error()
    supplied = volume.get("hostPath")
    if not isinstance(supplied, str):
        raise _path_error()
    _reject_raw_parent(supplied)
    if volume.get("name") != "outbox" or volume.get("containerPath") != COLLECTOR_OUTBOX_CONTAINER_PATH:
        raise _path_error()
    if volume.get("mode") != "rw":
        raise _path_error()
    supplied_path = Path(supplied)
    if not supplied_path.is_absolute() or os.path.normcase(os.path.normpath(str(supplied_path))) != os.path.normcase(
        os.path.normpath(str(workload_outbox))
    ):
        raise _path_error()
    if supplied_path.resolve(strict=False) != workload_outbox or _is_symlink_component(supplied_path):
        raise _path_error()

    devices = spec.get("serialDevices")
    if not isinstance(devices, list) or not devices:
        raise _path_error()
    seen_host = set()
    seen_container = set()
    allowed_serials = {Path(item).resolve(strict=False) for item in policy.serial_allowlist}
    for device in devices:
        if not isinstance(device, dict):
            raise _path_error()
        host_path = device.get("hostPath")
        container_path = device.get("containerPath")
        if not isinstance(host_path, str) or not isinstance(container_path, str):
            raise _path_error()
        _reject_raw_parent(host_path)
        if host_path in {"/", "\\", "", "/var/run/docker.sock"} or host_path.endswith("/docker.sock"):
            raise _path_error()
        if not Path(host_path).is_absolute() or not _SERIAL_CONTAINER.fullmatch(container_path):
            raise _path_error()
        canonical_host = Path(host_path).resolve(strict=False)
        raw_host = Path(host_path)
        has_symlink = _is_symlink_component(raw_host)
        if canonical_host not in allowed_serials or (
            has_symlink and not _serial_symlink_is_allowed(raw_host, canonical_host)
        ):
            raise _path_error()
        if canonical_host in seen_host or container_path in seen_container:
            raise _path_error()
        seen_host.add(canonical_host)
        seen_container.add(container_path)
        if device.get("readOnly") is not False:
            raise _path_error()
    return workload_config, workload_outbox


def validate_collector_spec(
    spec: Mapping[str, Any], policy: CollectorCapabilityPolicy
) -> Tuple[Mapping[str, Any], Path, Path]:
    """Schema + 安装 capability + 路径合同验证；成功才返回计划输入。"""

    if not isinstance(spec, Mapping):
        raise CollectorDeploymentError("COLLECTOR_WORKLOAD_SCHEMA_INVALID")
    try:
        errors = sorted(_schema_validator().iter_errors(spec), key=lambda error: list(error.path))
    except CollectorDeploymentError:
        raise
    except Exception as exc:
        raise CollectorDeploymentError("COLLECTOR_WORKLOAD_SCHEMA_INVALID") from exc
    if errors:
        raise CollectorDeploymentError("COLLECTOR_WORKLOAD_SCHEMA_INVALID")
    if spec.get("workloadType") != COLLECTOR_WORKLOAD_TYPE:
        raise CollectorDeploymentError("COLLECTOR_WORKLOAD_SCHEMA_INVALID")
    if policy.profile == "mini":
        raise CollectorDeploymentError("COLLECTOR_PROFILE_UNSUPPORTED")
    workload_id = _safe_workload_id(spec.get("workloadId"))

    image = spec.get("image")
    if not isinstance(image, Mapping):
        raise CollectorDeploymentError("COLLECTOR_IMAGE_FORBIDDEN")
    repository = image.get("repository")
    digest = image.get("digest")
    if not isinstance(repository, str) or not isinstance(digest, str) or not policy.image_allowed(repository, digest):
        raise CollectorDeploymentError("COLLECTOR_IMAGE_FORBIDDEN")

    resources = spec.get("resources")
    if not isinstance(resources, Mapping):
        raise CollectorDeploymentError("COLLECTOR_RESOURCE_LIMIT_EXCEEDED")
    try:
        cpu = Decimal(str(resources.get("cpuCores")))
        memory = int(resources.get("memoryBytes"))
    except (InvalidOperation, TypeError, ValueError, OverflowError) as exc:
        raise CollectorDeploymentError("COLLECTOR_RESOURCE_LIMIT_EXCEEDED") from exc
    if not cpu.is_finite() or cpu <= 0 or cpu > policy.max_cpu_cores or memory <= 0 or memory > policy.max_memory_bytes:
        raise CollectorDeploymentError("COLLECTOR_RESOURCE_LIMIT_EXCEEDED")

    config = spec.get("config")
    if not isinstance(config, Mapping) or config.get("targetPath") != COLLECTOR_TARGET_CONFIG_PATH:
        raise _path_error()
    if not isinstance(config.get("sha256"), str) or not _HEX_256.fullmatch(config["sha256"]):
        raise CollectorDeploymentError("COLLECTOR_WORKLOAD_SCHEMA_INVALID")
    workload_config, workload_outbox = _validate_path_policy(spec, policy, workload_id)
    # 深拷贝阻断调用方在 plan 构建后改变合同数据；不把原始 payload 交给 executor。
    return copy.deepcopy(dict(spec)), workload_config, workload_outbox


@dataclass(frozen=True)
class CollectorDeploymentPlan:
    platform: str
    workload_id: str
    project: str
    image: str
    config_version: int
    config_sha256: str
    spec_sha256: str
    argv: Tuple[str, ...]
    _broker_ref: "_OpaqueBrokerRef" = field(repr=False, compare=False)
    compose: Optional[Mapping[str, Any]] = None

    def as_dict(self) -> Mapping[str, Any]:
        value: MutableMapping[str, Any] = {
            "platform": self.platform,
            "workloadId": self.workload_id,
            "project": self.project,
            "image": self.image,
            "configVersion": self.config_version,
            "configSha256": self.config_sha256,
            "specSha256": self.spec_sha256,
            "argv": list(self.argv),
        }
        if self.compose is not None:
            value["compose"] = _thaw(self.compose)
        return value


@dataclass(frozen=True, repr=False)
class _OpaqueBrokerRef:
    """计划内部的 broker 引用；repr/str 不得暴露不透明引用内容。"""

    _value: str = field(repr=False, compare=False)

    def value_for_executor(self) -> str:
        return self._value

    def __repr__(self) -> str:
        return "<opaque-broker-ref>"

    def __str__(self) -> str:
        return "<opaque-broker-ref>"


def build_deployment_plan(
    spec: Mapping[str, Any],
    policy: CollectorCapabilityPolicy,
    *,
    platform: Optional[str] = None,
) -> CollectorDeploymentPlan:
    validated, config_dir, outbox_dir = validate_collector_spec(spec, policy)
    actual_platform = (platform or ("windows" if os.name == "nt" else "linux")).lower()
    if actual_platform not in {"linux", "windows"}:
        raise _configuration_error()
    if actual_platform == "windows" and not policy.windows_enabled:
        raise _configuration_error()

    workload_id = str(validated["workloadId"])
    repository = str(validated["image"]["repository"])
    digest = str(validated["image"]["digest"])
    image = f"{repository}@{digest}"
    config_version = int(validated["config"]["version"])
    config_sha256 = str(validated["config"]["sha256"])
    spec_sha256 = hashlib.sha256(_canonical_json(validated)).hexdigest()
    broker_ref = _OpaqueBrokerRef(str(validated["brokerRef"]))
    # Docker Compose project/container 名固定长度、全小写；identity hash 只覆盖
    # namespace + 原始 workloadId。同一 workload 的配置/镜像/spec 变化不改名，
    # 大小写不同或可读前缀截断相同的 workloadId 仍由完整 identity hash 区分。
    project = collector_workload_identity(workload_id)

    if actual_platform == "windows":
        return CollectorDeploymentPlan(
            platform="windows",
            workload_id=workload_id,
            project=project,
            image=image,
            config_version=config_version,
            config_sha256=config_sha256,
            spec_sha256=spec_sha256,
            argv=(
                str(policy.windows_java_path),
                "-jar",
                str(policy.windows_jar_path),
                "--spring.profiles.active=collector",
                "--easyaiot.collector.runtime-policy",
                str(policy.windows_runtime_policy_path),
                "--easyaiot.collector.runtime-policy-id",
                policy.windows_runtime_policy_id,
            ),
            _broker_ref=broker_ref,
            compose=None,
        )

    serial_devices = []
    for device in validated["serialDevices"]:
        serial_devices.append(f"{device['hostPath']}:{device['containerPath']}:rwm")
    compose = {
        "name": project,
        "services": {
            "collector": {
                "image": image,
                "container_name": f"{project}-app",
                "restart": "on-failure:5",
                "read_only": True,
                "security_opt": ["no-new-privileges:true"],
                "cap_drop": ["ALL"],
                "environment": {
                    "SPRING_PROFILES_ACTIVE": "collector",
                    # The collector may only address the one workload whose
                    # config directory is mounted below.  Keep this identity
                    # explicit and non-secret so the Java side cannot accept
                    # an arbitrary workload from a request.
                    "EASYAIOT_COLLECTOR_WORKLOAD_ID": workload_id,
                    "EASYAIOT_BROKER_SECRET_FILE": "/run/secrets/easyaiot-broker",
                },
                "volumes": [
                    f"{config_dir}:{COLLECTOR_CONFIG_CONTAINER_DIR}:rw",
                    f"{outbox_dir}:{COLLECTOR_OUTBOX_CONTAINER_PATH}:rw",
                ],
                "secrets": ["easyaiot-broker"],
                "devices": serial_devices,
                "deploy": {
                    "resources": {
                        "limits": {
                            "cpus": str(validated["resources"]["cpuCores"]),
                            "memory": f"{int(validated['resources']['memoryBytes'])}b",
                        }
                    }
                },
                "command": ["java", "-jar", "app.jar", "--spring.profiles.active=collector"],
            }
        },
        "secrets": {
            "easyaiot-broker": {"file": "__EASYAIOT_BROKER_SECRET_LEASE_FILE__"}
        },
    }
    return CollectorDeploymentPlan(
        platform="linux",
        workload_id=workload_id,
        project=project,
        image=image,
        config_version=config_version,
        config_sha256=config_sha256,
        spec_sha256=spec_sha256,
        argv=("docker", "compose", "-p", project, "-f", "-", "up", "-d", "--no-build"),
        _broker_ref=broker_ref,
        compose=_freeze(compose),
    )


class CollectorExecutor(Protocol):
    def execute(self, plan: CollectorDeploymentPlan) -> None:
        """执行已经固定的计划；实现不得重新解释远端 spec。"""


class BrokerSecretLease(Protocol):
    """本地短时 secret lease；接口不暴露 secret 内容。"""

    @property
    def secret_file_path(self) -> str:
        """仅返回受保护的本地 lease 文件路径。"""

    def is_usable(self) -> bool:
        """返回 lease 是否仍在安全有效期内。"""

    def close(self) -> None:
        """释放 lease。"""

    def commit(self, project: str) -> Optional[bool]:
        """将 lease 所有权转移给成功启动的 workload。"""


class BrokerSecretResolver(Protocol):
    def resolve(self, broker_ref: str) -> Optional[BrokerSecretLease]:
        """用不透明引用解析本地 lease；不得返回 secret 内容。"""


def _lease_file_path(lease: BrokerSecretLease) -> Path:
    try:
        raw_path = getattr(lease, "secret_file_path")
        if callable(raw_path):
            raw_path = raw_path()
        if not isinstance(raw_path, (str, Path)):
            raise ValueError
        path = Path(raw_path)
        if not path.is_absolute() or any(part == ".." for part in path.parts):
            raise ValueError
        if _is_symlink_component(path) or path.is_symlink() or not path.is_file():
            raise ValueError
        mode = stat.S_IMODE(path.stat().st_mode)
        if os.name != "nt" and mode & 0o077:
            raise ValueError
        if not stat.S_ISREG(path.stat().st_mode):
            raise ValueError
        return path
    except Exception as exc:
        raise _configuration_error() from exc


def _lease_is_usable(lease: BrokerSecretLease) -> bool:
    try:
        checker = getattr(lease, "is_usable", None)
        if callable(checker):
            return checker() is True
        return getattr(lease, "usable", False) is True
    except Exception:
        return False


def _close_lease(lease: BrokerSecretLease) -> None:
    try:
        closer = getattr(lease, "close", None) or getattr(lease, "release", None)
        if callable(closer):
            closer()
    except Exception:
        # 释放失败不把任何 lease 内容写入日志；进程退出后由 lease TTL 回收。
        pass


class DockerComposeCollectorExecutor:
    """固定 Compose 计划执行器；测试必须注入 fake，不启动 Docker。"""

    def __init__(self, secret_resolver: Optional[BrokerSecretResolver] = None):
        self._secret_resolver = secret_resolver

    def execute(self, plan: CollectorDeploymentPlan) -> None:
        if plan.platform != "linux" or plan.compose is None:
            raise CollectorDeploymentError("COLLECTOR_DEPLOY_CONFIGURATION_INVALID")
        if self._secret_resolver is None:
            raise CollectorDeploymentError("COLLECTOR_DEPLOY_CONFIGURATION_INVALID")
        lease: Optional[BrokerSecretLease] = None
        retained = False
        try:
            try:
                lease = self._secret_resolver.resolve(plan._broker_ref.value_for_executor())
            except Exception as exc:
                raise CollectorDeploymentError("COLLECTOR_DEPLOY_CONFIGURATION_INVALID") from exc
            if lease is None or not _lease_is_usable(lease):
                raise CollectorDeploymentError("COLLECTOR_DEPLOY_CONFIGURATION_INVALID")
            lease_path = _lease_file_path(lease)
            # 仅在 executor 的内存副本内注入本地 lease 文件路径；原始 plan、
            # Compose JSON 视图和 HTTP 响应仍不包含 brokerRef 或 secret。
            compose = _thaw(plan.compose)
            compose["secrets"]["easyaiot-broker"]["file"] = str(lease_path)
            compose_json = json.dumps(
                compose,
                ensure_ascii=False,
                sort_keys=True,
                separators=(",", ":"),
            ).encode("utf-8")
            try:
                commit = getattr(lease, "commit", None) or getattr(lease, "retain", None)
                if not callable(commit):
                    raise CollectorDeploymentError("COLLECTOR_DEPLOY_FAILED")
                committed = commit(plan.project)
                if committed is False:
                    raise CollectorDeploymentError("COLLECTOR_DEPLOY_FAILED")
            except Exception as exc:
                if isinstance(exc, CollectorDeploymentError):
                    raise
                raise CollectorDeploymentError("COLLECTOR_DEPLOY_FAILED") from exc
            try:
                subprocess.run(
                    list(plan.argv),
                    input=compose_json,
                    stdout=subprocess.PIPE,
                    stderr=subprocess.PIPE,
                    check=True,
                    shell=False,
                )
                # commit 先于 subprocess；只有进程真正启动成功才把 lease
                # 所有权留给 workload。进程启动失败由 finally 释放/回滚。
                retained = True
            except Exception as exc:
                raise CollectorDeploymentError("COLLECTOR_DEPLOY_FAILED") from exc
        finally:
            if lease is not None and not retained:
                _close_lease(lease)


class CollectorDeploymentService:
    def __init__(self, executor: CollectorExecutor):
        self._executor = executor

    def deploy(
        self,
        spec: Mapping[str, Any],
        policy: CollectorCapabilityPolicy,
        *,
        platform: Optional[str] = None,
    ) -> Mapping[str, Any]:
        plan = build_deployment_plan(spec, policy, platform=platform)
        try:
            self._executor.execute(plan)
        except CollectorDeploymentError:
            raise
        except Exception as exc:
            raise CollectorDeploymentError("COLLECTOR_DEPLOY_FAILED") from exc
        # 不透传 executor 返回值或原始 spec；响应只含稳定元数据。
        return {
            "workloadId": plan.workload_id,
            "platform": plan.platform,
            "project": plan.project,
            "image": plan.image,
            "configVersion": plan.config_version,
            "configSha256": plan.config_sha256,
            "specSha256": plan.spec_sha256,
            "lifecycle": "REQUESTED",
        }
