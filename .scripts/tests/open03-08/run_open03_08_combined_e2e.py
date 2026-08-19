"""Run the frozen OPEN03-08 success and failure chains against a real loopback Agent.

The Java tests remain module-local.  This repository-level runner is the only
place that composes them, so no iot-*biz module needs a dependency on another
biz module.
"""

from __future__ import annotations

import hashlib
import importlib
import json
import os
import re
import secrets
import shutil
import socket
import subprocess
import sys
import tempfile
import time
from pathlib import Path
from urllib.error import URLError
from urllib.request import urlopen


sys.dont_write_bytecode = True
REPO_ROOT = Path(__file__).resolve().parents[3]
NODE_ROOT = REPO_ROOT / "NODE"
FIXTURE_ROOT = Path(__file__).resolve().parent / "fixtures"
WORKLOAD_ID = "collector-open03-e2e-a"

sys.path.insert(0, str(NODE_ROOT))
import collector_workload  # noqa: E402


def fail(message: str) -> None:
    raise RuntimeError(message)


def require_tools() -> tuple[str, str]:
    python_executable = sys.executable
    if not python_executable or not shutil.which(python_executable):
        fail("Python executable is unavailable")
    maven_executable = shutil.which("mvn") or shutil.which("mvn.cmd")
    if not maven_executable:
        fail("Maven is unavailable")
    if not shutil.which("java"):
        fail("Java is unavailable")
    for module in ("flask", "jsonschema"):
        try:
            importlib.import_module(module)
        except ImportError as error:
            fail(f"Python dependency {module} is unavailable: {error}")
    return python_executable, maven_executable


def load_fixtures() -> tuple[dict, dict, dict]:
    manifest_path = FIXTURE_ROOT / "manifest.json"
    trace_path = FIXTURE_ROOT / "expected-trace.json"
    if not manifest_path.is_file() or not trace_path.is_file():
        fail("OPEN03-08 fixture manifest or trace is missing")
    manifest = json.loads(manifest_path.read_text(encoding="utf-8"))
    if manifest.get("schemaVersion") != "open03-08-fixture/1":
        fail("unsupported OPEN03-08 fixture manifest")
    if manifest.get("workloadId") != WORKLOAD_ID:
        fail("fixture workload identity drifted")
    loaded = {}
    for name, item in manifest.get("fixtures", {}).items():
        fixture = FIXTURE_ROOT / item["file"]
        if not fixture.is_file():
            fail(f"fixture missing: {item['file']}")
        payload = fixture.read_bytes()
        actual_hash = hashlib.sha256(payload).hexdigest()
        if len(payload) != item["canonicalLengthBytes"] or actual_hash != item["sha256"]:
            fail(f"fixture bytes drifted: {item['file']}")
        json.loads(payload.decode("utf-8"))
        loaded[name] = {"manifest": item, "path": fixture, "bytes": payload, "sha256": actual_hash}
    expected = json.loads(trace_path.read_text(encoding="utf-8"))
    if expected != {
        "success": ["PUBLISHED", "AGENT_ACCEPTED", "DESIRED_WRITTEN", "COLLECTOR_APPLIED", "DEVICE_APPLIED"],
        "failure": ["PUBLISHED", "AGENT_ACCEPTED", "DESIRED_WRITTEN", "COLLECTOR_FAILED", "DEVICE_FAILED"],
    }:
        fail("expected OPEN03-08 trace drifted")
    return manifest, loaded, expected


def free_port() -> int:
    with socket.socket(socket.AF_INET, socket.SOCK_STREAM) as listener:
        listener.bind(("127.0.0.1", 0))
        return int(listener.getsockname()[1])


def start_agent(python_executable: str, temp_root: Path, state_root: Path) -> tuple[subprocess.Popen, object]:
    key_path = temp_root / "agent-signing-key"
    key_value = secrets.token_hex(32)
    key_path.write_text(
        "nodeId=21\ncurrentKeyId=open03-random\ncurrentKey=" + key_value + "\n",
        encoding="utf-8",
    )
    if os.name != "nt":
        key_path.chmod(0o600)
    replay_db = temp_root / "replay-window.db"
    log_handle = (temp_root / "agent.log").open("wb")
    port = free_port()
    env = os.environ.copy()
    env.update(
        {
            "PYTHONDONTWRITEBYTECODE": "1",
            "AGENT_LISTEN_HOST": "127.0.0.1",
            "AGENT_LISTEN_PORT": str(port),
            "AGENT_SIGNING_KEY_FILE": str(key_path),
            "AGENT_REPLAY_DB": str(replay_db),
            "COLLECTOR_STATE_ROOT": str(state_root),
            "COLLECTOR_DEPLOY_PROFILE": "standard",
            "EASYAIOT_DEPLOY_PROFILE": "standard",
            "COLLECTOR_AGENT_PLATFORM": "windows" if os.name == "nt" else "linux",
        }
    )
    process = subprocess.Popen(
        [python_executable, "-c", "from agent_server import run_server; run_server()"],
        cwd=str(NODE_ROOT),
        env=env,
        stdout=log_handle,
        stderr=subprocess.STDOUT,
    )
    for _ in range(100):
        if process.poll() is not None:
            fail("loopback Agent exited before /health became ready")
        try:
            with urlopen(f"http://127.0.0.1:{port}/health", timeout=1) as response:
                if response.status == 200:
                    process._open03_port = port  # type: ignore[attr-defined]
                    process._open03_key = key_path  # type: ignore[attr-defined]
                    process._open03_key_value = key_value  # type: ignore[attr-defined]
                    return process, log_handle
        except (OSError, URLError):
            time.sleep(0.1)
    fail("loopback Agent did not become ready")


def stop_agent(process: subprocess.Popen | None) -> None:
    if process is None or process.poll() is not None:
        return
    process.terminate()
    try:
        process.wait(timeout=5)
    except subprocess.TimeoutExpired:
        process.kill()
        process.wait(timeout=5)


def run_maven_stage(maven: str, module: str, test_class: str, env: dict[str, str], label: str) -> None:
    command = [
        maven,
        "-f",
        str(REPO_ROOT / "DEVICE" / "pom.xml"),
        "test",
        "-pl",
        module,
        "-am",
        f"-Dtest={test_class}",
        "-Dsurefire.failIfNoSpecifiedTests=false",
        "-Dmaven.test.skip=false",
    ]
    result = subprocess.run(
        command,
        cwd=str(REPO_ROOT),
        env=env,
        stdout=subprocess.PIPE,
        stderr=subprocess.STDOUT,
        text=True,
        errors="replace",
        check=False,
    )
    if result.returncode != 0:
        # Maven output can contain paths or protocol details.  Keep the
        # repository-level failure marker stable and non-sensitive.
        fail(f"{label} failed with exit {result.returncode}")
    print(f"{label}: PASS")


_SENSITIVE_FIELD_MARKERS = (
    "payloadCanonical",
    "canonicalPayload",
    "nonce",
    "signature",
    "currentKey",
)
_WINDOWS_ABSOLUTE_PATH = re.compile(
    r"(?i)(?<![A-Za-z0-9])(?:[A-Za-z]:[\\/]|\\\\)[^\\/\s\"']+(?:[\\/][^\\/\s\"']+)+"
)
_POSIX_ABSOLUTE_PATH = re.compile(
    r"(?<![A-Za-z0-9_])/(?:var|tmp|home|opt|etc|run|usr|srv|mnt|media|root)"
    r"(?:/[^\s\"']+)+"
)
_FILE_URI_PATH = re.compile(r"(?i)file://(?:/[^\s\"']+)+")


def _fixture_payload_markers(payloads: list[bytes]) -> tuple[str, ...]:
    """Return canonical/value markers without treating hashes as secrets."""

    markers: list[str] = []
    for payload in payloads:
        canonical = payload.decode("utf-8")
        markers.append(canonical)
        # A response/log may escape a canonical payload before emitting it.
        markers.append(json.dumps(canonical, ensure_ascii=False))
        try:
            value = json.loads(canonical)
        except json.JSONDecodeError:
            continue

        def collect_values(node: object) -> None:
            if isinstance(node, dict):
                for name, child in node.items():
                    if name == "value" and isinstance(child, str) and len(child) >= 3:
                        markers.append(child)
                    collect_values(child)
            elif isinstance(node, list):
                for child in node:
                    collect_values(child)

        collect_values(value)
    return tuple(dict.fromkeys(marker for marker in markers if marker))


def _normalise_path_marker(path: Path) -> str:
    return str(path).replace("\\", "/")


def _contains_absolute_path(raw: str) -> bool:
    return bool(
        _WINDOWS_ABSOLUTE_PATH.search(raw)
        or _POSIX_ABSOLUTE_PATH.search(raw)
        or _FILE_URI_PATH.search(raw)
    )


def assert_no_sensitive_text(
    label: str,
    path: Path,
    *,
    key_value: str,
    key_path: Path,
    temp_root: Path,
    payload_markers: tuple[str, ...],
) -> None:
    if not path.is_file():
        fail(f"SENSITIVE_SCAN_MISSING:{label}")
    raw = path.read_text(encoding="utf-8", errors="replace")
    folded = raw.casefold()
    for marker in _SENSITIVE_FIELD_MARKERS:
        if marker.casefold() in folded:
            fail(f"SENSITIVE_FIELD:{marker}")
    if key_value and key_value in raw:
        fail("SENSITIVE_RANDOM_KEY_VALUE")
    for path_marker in (_normalise_path_marker(temp_root), _normalise_path_marker(key_path)):
        if path_marker and path_marker.casefold() in folded:
            fail("SENSITIVE_TEMP_OR_KEY_PATH")
    if _contains_absolute_path(raw):
        fail("SENSITIVE_ABSOLUTE_PATH")
    for payload_marker in payload_markers:
        if payload_marker in raw:
            fail("SENSITIVE_CANONICAL_OR_VALUE")


def assert_final_state(
    config_root: Path,
    outbox_root: Path,
    v1: dict,
    v2: dict,
    expected: dict,
    trace: Path,
    release: Path,
    response_file: Path,
) -> None:
    config_directory = collector_workload.collector_config_directory(config_root, WORKLOAD_ID)
    if config_root == outbox_root or config_root in outbox_root.parents or outbox_root in config_root.parents:
        fail("config and outbox roots are not independent siblings")
    active = config_directory / "active.json"
    desired = config_directory / "desired.json"
    observed = config_directory / "observed.json"
    history = config_directory / "history"
    if active.read_bytes() != v1["bytes"] or desired.read_bytes() != v2["bytes"]:
        fail("final active/desired bytes do not preserve v1/v2 contract")
    history_names = sorted(item.name for item in history.glob("*.json"))
    if history_names != [f"1-{v1['sha256'][:8]}.json"]:
        fail(f"unexpected history files: {history_names}")
    observed_value = json.loads(observed.read_text(encoding="utf-8"))
    if observed_value.get("status") != "FAILED" or observed_value.get("configVersion") != 2:
        fail("final observed state is not FAILED v2")
    if observed_value.get("payloadSha256") != v2["sha256"] or observed_value.get("errorCode") != "COLLECTOR_CONFIG_APPLY_FAILED":
        fail("final observed error contract drifted")
    trace_value = json.loads(trace.read_text(encoding="utf-8"))
    if trace_value != expected:
        fail(f"trace drifted: {trace_value}")
    if not response_file.is_file() or response_file.stat().st_size == 0:
        fail("response summary is missing")


def main() -> int:
    python_executable, maven = require_tools()
    _manifest, fixtures, expected = load_fixtures()
    if set(fixtures) != {"success", "failure"}:
        fail("fixture manifest must contain success and failure")
    with tempfile.TemporaryDirectory(prefix="easyaiot-open03-08-") as temporary:
        temp_root = Path(temporary)
        config_root = temp_root / "config-root"
        outbox_root = temp_root / "outbox-root"
        config_root.mkdir()
        outbox_root.mkdir()
        release_state = temp_root / "release-state.json"
        trace = temp_root / "trace.json"
        response_file = temp_root / "responses.jsonl"
        agent_log = temp_root / "agent.log"
        config_directory = collector_workload.collector_config_directory(config_root, WORKLOAD_ID)
        agent = None
        log_handle = None
        key_path = temp_root / "agent-signing-key"
        key_value = ""
        primary_error: BaseException | None = None
        try:
            agent, log_handle = start_agent(python_executable, temp_root, config_root)
            port = str(agent._open03_port)  # type: ignore[attr-defined]
            key_path = Path(agent._open03_key)  # type: ignore[attr-defined]
            key_value = str(agent._open03_key_value)  # type: ignore[attr-defined]
            common = os.environ.copy()
            common.update(
                {
                    "OPEN03_RELEASE_STATE": str(release_state),
                    "OPEN03_TRACE_FILE": str(trace),
                    "OPEN03_CONFIG_DIRECTORY": str(config_directory),
                    "OPEN03_FIXTURE_V1": str(fixtures["success"]["path"]),
                    "OPEN03_FIXTURE_V2": str(fixtures["failure"]["path"]),
                    "OPEN03_AGENT_PORT": port,
                    "OPEN03_KEY_FILE": str(key_path),
                    "OPEN03_OUTBOX_ROOT": str(outbox_root),
                    "OPEN03_RESPONSE_FILE": str(response_file),
                }
            )

            env = common | {"OPEN03_NODE_STAGE": "dispatch-v1", "OPEN03_CHAIN": "success"}
            run_maven_stage(maven, "iot-node/iot-node-biz", "CollectorOpen03CombinedStageTest", env, "success/node dispatch v1")
            env = common | {
                "OPEN03_SINK_STAGE": "apply-v1",
                "OPEN03_SINK_FIXTURE": str(fixtures["success"]["path"]),
            }
            run_maven_stage(maven, "iot-sink/iot-sink-biz", "CollectorOpen03CombinedApplyStageTest", env, "success/sink apply v1")
            env = common | {"OPEN03_NODE_STAGE": "reconcile-v1", "OPEN03_CHAIN": "success"}
            run_maven_stage(maven, "iot-node/iot-node-biz", "CollectorOpen03CombinedStageTest", env, "success/node reconcile v1")

            env = common | {"OPEN03_NODE_STAGE": "dispatch-v2", "OPEN03_CHAIN": "failure"}
            run_maven_stage(maven, "iot-node/iot-node-biz", "CollectorOpen03CombinedStageTest", env, "failure/node dispatch v2")
            env = common | {
                "OPEN03_SINK_STAGE": "apply-v2-failure",
                "OPEN03_SINK_FIXTURE": str(fixtures["failure"]["path"]),
            }
            run_maven_stage(maven, "iot-sink/iot-sink-biz", "CollectorOpen03CombinedApplyStageTest", env, "failure/sink apply v2")
            env = common | {"OPEN03_NODE_STAGE": "reconcile-v2", "OPEN03_CHAIN": "failure"}
            run_maven_stage(maven, "iot-node/iot-node-biz", "CollectorOpen03CombinedStageTest", env, "failure/node reconcile v2")

            assert_final_state(
                config_root,
                outbox_root,
                fixtures["success"],
                fixtures["failure"],
                expected,
                trace,
                release_state,
                response_file,
            )
        except BaseException as error:
            primary_error = error
        finally:
            stop_agent(agent)
            if log_handle is not None:
                log_handle.close()
            try:
                payload_markers = _fixture_payload_markers(
                    [fixtures["success"]["bytes"], fixtures["failure"]["bytes"]]
                )
                for label, path in (
                    ("trace", trace),
                    ("release", release_state),
                    ("response", response_file),
                    ("agent-log", agent_log),
                ):
                    assert_no_sensitive_text(
                        label,
                        path,
                        key_value=key_value,
                        key_path=Path(key_path),
                        temp_root=temp_root,
                        payload_markers=payload_markers,
                    )
            except BaseException as scan_error:
                if primary_error is None:
                    raise scan_error
                # Preserve the original deterministic failure without ever
                # printing a sensitive value from the failed scan.
                print("OPEN03-08 sensitive output scan: FAIL", file=sys.stderr)
        if primary_error is not None:
            raise primary_error
        print("OPEN03-08 combined E2E: PASS (success and failure chains)")
        return 0


if __name__ == "__main__":
    try:
        raise SystemExit(main())
    except Exception as error:
        print(f"OPEN03-08 combined E2E: FAIL: {error}", file=sys.stderr)
        raise SystemExit(1)
