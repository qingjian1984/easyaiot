import hashlib
import json
import threading
from pathlib import Path
import sys
from decimal import Decimal

import pytest

import collector_config_state as cs
import collector_workload as cw
sys.path.insert(0, str(Path(__file__).parent))
from test_collector_config_contract import envelope, raw_envelope


def raw_for(version, *, product="power-meter-v1", workload_id="collector-site-1001-a"):
    value = envelope(workload_id=workload_id, version=version)
    if product != "power-meter-v1":
        payload = json.loads(value["payloadCanonical"])
        payload["productIdentification"] = product
        canonical = cs._canonical_json(payload)
        value["payloadCanonical"] = canonical.decode()
        value["payloadSha256"] = hashlib.sha256(canonical).hexdigest()
        value["canonicalLengthBytes"] = len(canonical)
    return json.dumps(value, separators=(",", ":"), ensure_ascii=False).encode()


def test_put_version_matrix_preserves_exact_bytes_and_does_not_fake_active(tmp_path):
    root = tmp_path / "state"
    service = cs.CollectorConfigStateService(root)
    first = raw_for(1)
    result = service.put(first)
    assert result.status == "ACCEPTED"
    directory = cs.collector_config_directory(root, "collector-site-1001-a")
    desired = directory / "desired.json"
    first_bytes = desired.read_bytes()
    assert first_bytes == json.loads(first.decode())["payloadCanonical"].encode()
    assert not (directory / "active.json").exists()
    assert not (directory / "observed.json").exists()

    assert service.put(first).status == "IDEMPOTENT"
    assert desired.read_bytes() == first_bytes
    with pytest.raises(cs.CollectorConfigStateError, match="CONFIG_VERSION_CONFLICT"):
        service.put(raw_for(1, product="power-meter-v2"))
    assert desired.read_bytes() == first_bytes

    assert service.put(raw_for(2)).status == "ACCEPTED"
    with pytest.raises(cs.CollectorConfigStateError, match="CONFIG_VERSION_STALE"):
        service.put(raw_for(1))


def test_public_config_directory_is_the_compose_mount_and_state_file_fact(tmp_path):
    workload_id = "collector-site-1001-a"
    digest = "sha256:" + "a" * 64
    policy = cw.CollectorCapabilityPolicy(
        profile="full",
        allowed_images=(f"registry.example/easyaiot/iot-sink-biz@{digest}",),
        collector_root=tmp_path / "collector",
        state_root=tmp_path / "state",
        serial_allowlist=(str(tmp_path / "serial0"),),
        max_cpu_cores=Decimal("2"),
        max_memory_bytes=1024 * 1024 * 1024,
    )
    spec = {
        "specVersion": "1.0",
        "workloadType": "iot-sink-collector",
        "workloadId": workload_id,
        "nodeId": "21",
        "image": {"repository": "registry.example/easyaiot/iot-sink-biz", "digest": digest},
        "springProfile": "collector",
        "config": {"version": 1, "sha256": "a" * 64, "targetPath": "/var/lib/easyaiot/config/active.json"},
        "resources": {"cpuCores": "1.0", "memoryBytes": 402653184},
        "serialDevices": [{
            "hostPath": str(tmp_path / "serial0"),
            "containerPath": "/dev/easyaiot/rs485-0",
            "hardwareFingerprint": "usb:vid:pid:serial",
            "readOnly": False,
        }],
        "volumes": [{
            "name": "outbox",
            "hostPath": str(tmp_path / "collector" / workload_id / "outbox"),
            "containerPath": "/var/lib/easyaiot/outbox",
            "mode": "rw",
        }],
        "brokerRef": "secret://node/21/collector/site-1001",
        "updatePolicy": {
            "dispatchAckTimeoutSeconds": 10,
            "configApplyTimeoutSeconds": 60,
            "healthWindowSeconds": 60,
            "autoRollback": True,
        },
    }
    expected = cw.collector_config_directory(policy.state_root, workload_id)
    plan = cw.build_deployment_plan(spec, policy, platform="linux")
    service_plan = plan.as_dict()["compose"]["services"]["collector"]
    mounted = service_plan["volumes"][0]
    assert mounted == f"{expected}:/var/lib/easyaiot/config:rw"
    assert service_plan["environment"]["EASYAIOT_COLLECTOR_WORKLOAD_ID"] == workload_id
    service = cs.CollectorConfigStateService(policy.state_root)
    canonical = json.loads(raw_for(1).decode())["payloadCanonical"].encode()
    service.write_active(workload_id, canonical)
    assert (expected / "active.json").read_bytes() == canonical


def test_linux_shared_group_mode_contract_is_fixed_and_drift_fails_closed(tmp_path, monkeypatch):
    assert cs.LINUX_CONFIG_DIRECTORY_MODE == 0o2770
    assert cs.LINUX_CONFIG_FILE_MODE == 0o660

    monkeypatch.setattr(cs.os, "name", "posix")
    monkeypatch.setattr(cs.stat, "S_IMODE", lambda _mode: 0o2770)
    cs._ensure_directory_mode(tmp_path)

    monkeypatch.setattr(cs.stat, "S_IMODE", lambda _mode: 0o0770)
    with pytest.raises(cs.CollectorConfigStateError, match="COLLECTOR_CONFIG_PERMISSION_INVALID"):
        cs._ensure_directory_mode(tmp_path)


def test_existing_directory_and_file_mode_drift_is_not_silently_chmodded(tmp_path, monkeypatch):
    root = tmp_path / "state"
    service = cs.CollectorConfigStateService(root)
    service.put(raw_for(1))
    def permission_drift(_path, *, apply=False):
        raise cs.CollectorConfigStateError("COLLECTOR_CONFIG_PERMISSION_INVALID")

    monkeypatch.setattr(cs, "_ensure_directory_mode", permission_drift)
    with pytest.raises(cs.CollectorConfigStateError, match="COLLECTOR_CONFIG_PERMISSION_INVALID"):
        service.get("collector-site-1001-a")


def test_existing_formal_file_mode_drift_fails_closed_for_get_and_put(tmp_path, monkeypatch):
    service = cs.CollectorConfigStateService(tmp_path / "state")
    service.put(raw_for(1))

    def permission_drift(_path, *, apply=False):
        raise cs.CollectorConfigStateError("COLLECTOR_CONFIG_PERMISSION_INVALID")

    monkeypatch.setattr(cs, "_ensure_file_mode", permission_drift)
    with pytest.raises(cs.CollectorConfigStateError, match="COLLECTOR_CONFIG_PERMISSION_INVALID"):
        service.get("collector-site-1001-a")
    with pytest.raises(cs.CollectorConfigStateError, match="COLLECTOR_CONFIG_PERMISSION_INVALID"):
        service.put(raw_for(2))


def test_stale_request_is_rejected_against_active_and_damaged_state_fails_closed(tmp_path):
    service = cs.CollectorConfigStateService(tmp_path / "state")
    service.write_active("collector-site-1001-a", json.loads(raw_for(2).decode())["payloadCanonical"].encode())
    with pytest.raises(cs.CollectorConfigStateError, match="CONFIG_VERSION_STALE"):
        service.put(raw_for(1))
    directory = cs.collector_config_directory(tmp_path / "state", "collector-site-1001-a")
    desired = directory / "desired.json"
    desired.write_bytes(b"{\"corrupt\":true}")
    with pytest.raises(cs.CollectorConfigStateError, match="COLLECTOR_CONFIG_STATE_CORRUPT"):
        service.put(raw_for(3))
    assert desired.read_bytes() == b"{\"corrupt\":true}"


def test_atomic_active_history_and_observed_primitives_keep_original_bytes(tmp_path):
    service = cs.CollectorConfigStateService(tmp_path / "state")
    canonical = json.loads(raw_for(1).decode())["payloadCanonical"].encode()
    artifact = service.write_active("collector-site-1001-a", canonical)
    history_path = service.write_history("collector-site-1001-a", canonical)
    service.write_observed(
        "collector-site-1001-a",
        {"status": "APPLIED", "configVersion": 1, "payloadSha256": artifact.payload_sha256},
    )
    assert artifact.canonical_bytes == canonical
    assert history_path.read_bytes() == canonical
    result = service.get("collector-site-1001-a")
    assert result["active"]["payloadSha256"] == artifact.payload_sha256
    assert result["observed"]["status"] == "APPLIED"
    assert "payloadCanonical" not in json.dumps(result)

    stale = history_path.parent / f".{history_path.name}.{'a' * 32}.tmp"
    stale.write_bytes(b"not a formal state")
    restore = history_path.parent / f".{history_path.name}.{'c' * 32}.restore"
    restore.write_bytes(b"not a formal state")
    assert service.get("collector-site-1001-a")["active"] is not None

    unknown_temp = history_path.parent / f".unrecognized.{'b' * 32}.tmp"
    unknown_temp.write_bytes(b"not an implementation temp")
    with pytest.raises(cs.CollectorConfigStateError, match="COLLECTOR_CONFIG_STATE_CORRUPT"):
        service.get("collector-site-1001-a")

    with pytest.raises(cs.CollectorConfigStateError, match="COLLECTOR_CONFIG_REQUEST_INVALID"):
        service.write_observed(
            "collector-site-1001-a",
            {"status": "FAILED", "errorDetailSanitized": "stack trace"},
        )
    with pytest.raises(cs.CollectorConfigStateError, match="COLLECTOR_CONFIG_PATH_FORBIDDEN"):
        service.write_observed(
            "collector-site-1001-a",
            {"workloadId": "collector-other", "status": "FAILED"},
        )


def test_injected_atomic_failure_keeps_previous_desired(tmp_path, monkeypatch):
    service = cs.CollectorConfigStateService(tmp_path / "state")
    service.put(raw_for(1))
    directory = cs.collector_config_directory(tmp_path / "state", "collector-site-1001-a")
    desired = directory / "desired.json"
    before = desired.read_bytes()

    def fail_write(*args, **kwargs):
        raise cs.CollectorConfigStateError("COLLECTOR_CONFIG_WRITE_FAILED")

    monkeypatch.setattr(cs, "_write_atomic", fail_write)
    with pytest.raises(cs.CollectorConfigStateError, match="COLLECTOR_CONFIG_WRITE_FAILED"):
        service.put(raw_for(2))
    assert desired.read_bytes() == before


def test_same_existing_version_with_different_desired_and_active_hash_is_state_corrupt(tmp_path):
    service = cs.CollectorConfigStateService(tmp_path / "state")
    service.put(raw_for(1))
    service.write_active(
        "collector-site-1001-a",
        json.loads(raw_for(1, product="power-meter-v2").decode())["payloadCanonical"].encode(),
    )
    with pytest.raises(cs.CollectorConfigStateError, match="COLLECTOR_CONFIG_STATE_CORRUPT"):
        service.put(raw_for(2))
    with pytest.raises(cs.CollectorConfigStateError, match="COLLECTOR_CONFIG_STATE_CORRUPT"):
        service.get("collector-site-1001-a")


def test_post_replace_directory_fsync_failure_rolls_back_new_and_existing_files(tmp_path, monkeypatch):
    service = cs.CollectorConfigStateService(tmp_path / "state")

    def fail_directory_fsync(_directory):
        raise OSError("synthetic directory fsync failure")

    monkeypatch.setattr(cs, "_fsync_directory", fail_directory_fsync)
    with pytest.raises(cs.CollectorConfigStateError, match="COLLECTOR_CONFIG_WRITE_FAILED"):
        service.put(raw_for(1))
    config_directory = cs.collector_config_directory(tmp_path / "state", "collector-site-1001-a")
    assert not (config_directory / "desired.json").exists()

    monkeypatch.setattr(cs, "_fsync_directory", lambda _directory: None)
    service.put(raw_for(1))
    before = (config_directory / "desired.json").read_bytes()
    monkeypatch.setattr(cs, "_fsync_directory", fail_directory_fsync)
    with pytest.raises(cs.CollectorConfigStateError, match="COLLECTOR_CONFIG_WRITE_FAILED"):
        service.put(raw_for(2))
    assert (config_directory / "desired.json").read_bytes() == before


def test_concurrent_puts_are_serialized_and_restart_reads_only_formal_files(tmp_path):
    root = tmp_path / "state"
    service = cs.CollectorConfigStateService(root)
    results = []
    errors = []

    def put(version):
        try:
            results.append(service.put(raw_for(version)).status)
        except cs.CollectorConfigStateError as exc:
            errors.append(exc.code)

    threads = [threading.Thread(target=put, args=(version,)) for version in (1, 2)]
    for thread in threads:
        thread.start()
    for thread in threads:
        thread.join(timeout=5)
    assert not any(thread.is_alive() for thread in threads)
    assert sorted(results + errors) == ["ACCEPTED", "ACCEPTED"] or sorted(results + errors) == [
        "ACCEPTED",
        "CONFIG_VERSION_STALE",
    ]
    restarted = cs.CollectorConfigStateService(root)
    current = restarted.get("collector-site-1001-a")
    assert current["desired"]["configVersion"] == 2


def test_safe_identity_and_path_fail_closed_without_creating_state(tmp_path, monkeypatch):
    service = cs.CollectorConfigStateService(tmp_path / "state")
    for workload_id in ("../escape", "a/b", "C:\\escape", ".", ".."):
        with pytest.raises(cs.CollectorConfigStateError, match="COLLECTOR_CONFIG_PATH_FORBIDDEN"):
            service.put(raw_for(1, workload_id=workload_id))
    assert not (tmp_path / "state").exists()

    monkeypatch.setattr(cs, "_path_has_symlink_component", lambda path: True)
    with pytest.raises(cs.CollectorConfigStateError, match="COLLECTOR_CONFIG_PATH_FORBIDDEN"):
        cs.CollectorConfigStateService(tmp_path / "another")


def test_preexisting_lock_symlink_or_non_regular_path_is_rejected_before_directory_creation(tmp_path, monkeypatch):
    service = cs.CollectorConfigStateService(tmp_path / "state")
    original = cs._path_has_symlink_component
    monkeypatch.setattr(
        cs,
        "_path_has_symlink_component",
        lambda path: path.name == ".state.lock" or original(path),
    )
    with pytest.raises(cs.CollectorConfigStateError, match="COLLECTOR_CONFIG_PATH_FORBIDDEN"):
        service.put(raw_for(1))
    assert not (tmp_path / "state").exists()


def test_python_lock_uses_java_compatible_record_lock_family():
    source = Path(cs.__file__).read_text(encoding="utf-8")
    assert "fcntl.lockf" in source
    assert "fcntl.flock" not in source


def test_missing_workload_does_not_create_directory(tmp_path):
    service = cs.CollectorConfigStateService(tmp_path / "state")
    with pytest.raises(cs.CollectorConfigStateError, match="COLLECTOR_WORKLOAD_NOT_FOUND"):
        service.get("collector-site-1001-a")
    assert not (tmp_path / "state").exists()
