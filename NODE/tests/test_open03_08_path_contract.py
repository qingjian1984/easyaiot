import json
from decimal import Decimal
from pathlib import Path

import pytest

import collector_workload as cw


DIGEST = "sha256:" + "a" * 64
WORKLOAD = "collector-open03-e2e-a"


def make_policy(tmp_path, *, collector_root=None, state_root=None):
    serial = (tmp_path / "serial0").resolve()
    return cw.CollectorCapabilityPolicy(
        profile="standard",
        allowed_images=(f"registry.example/easyaiot/iot-sink-biz@{DIGEST}",),
        collector_root=(collector_root or tmp_path / "collector").resolve(),
        state_root=(state_root or tmp_path / "state").resolve(),
        serial_allowlist=(str(serial),),
        max_cpu_cores=Decimal("2.0"),
        max_memory_bytes=1024 * 1024 * 1024,
    )


def make_spec(policy):
    outbox = (policy.collector_root / WORKLOAD / "outbox").resolve()
    return {
        "specVersion": "1.0",
        "workloadType": "iot-sink-collector",
        "workloadId": WORKLOAD,
        "nodeId": "21",
        "image": {"repository": "registry.example/easyaiot/iot-sink-biz", "digest": DIGEST},
        "springProfile": "collector",
        "config": {"version": 1, "sha256": "b" * 64,
                    "targetPath": "/var/lib/easyaiot/config/active.json"},
        "resources": {"cpuCores": "1.0", "memoryBytes": 402653184},
        "serialDevices": [{
            "hostPath": policy.serial_allowlist[0],
            "containerPath": "/dev/easyaiot/rs485-0",
            "hardwareFingerprint": "usb:vid:pid:serial",
            "readOnly": False,
        }],
        "volumes": [{
            "name": "outbox", "hostPath": str(outbox),
            "containerPath": "/var/lib/easyaiot/outbox", "mode": "rw",
        }],
        "brokerRef": "secret://node/21/collector/open03-site",
        "updatePolicy": {
            "dispatchAckTimeoutSeconds": 10,
            "configApplyTimeoutSeconds": 60,
            "healthWindowSeconds": 60,
            "autoRollback": True,
        },
    }


def test_sibling_roots_validate_and_render_exact_mounts(tmp_path):
    policy = make_policy(tmp_path)
    spec = make_spec(policy)
    _, config_path, outbox_path = cw.validate_collector_spec(spec, policy)
    assert config_path == cw.collector_config_directory(policy.state_root, WORKLOAD)
    assert outbox_path == (policy.collector_root / WORKLOAD / "outbox").resolve()
    assert policy.collector_root != policy.state_root
    assert not config_path.is_relative_to(policy.collector_root)

    plan = cw.build_deployment_plan(spec, policy, platform="linux")
    service = plan.as_dict()["compose"]["services"]["collector"]
    assert service["volumes"] == [
        f"{config_path}:/var/lib/easyaiot/config:rw",
        f"{outbox_path}:/var/lib/easyaiot/outbox:rw",
    ]


@pytest.mark.parametrize("layout", ["equal", "collector-under-state", "state-under-collector"])
def test_equal_or_nested_roots_fail_closed(tmp_path, layout):
    base = tmp_path / "roots"
    if layout == "equal":
        collector_root = state_root = base
    elif layout == "collector-under-state":
        state_root = base
        collector_root = base / "collector"
    else:
        collector_root = base
        state_root = base / "state"
    policy = make_policy(tmp_path, collector_root=collector_root, state_root=state_root)
    with pytest.raises(cw.CollectorDeploymentError) as error:
        cw.validate_collector_spec(make_spec(policy), policy)
    assert error.value.code == "COLLECTOR_PATH_FORBIDDEN"


def test_fixture_manifest_rejects_byte_drift_and_has_no_secret_or_path(tmp_path):
    fixtures = Path(".scripts/tests/open03-08/fixtures")
    manifest = json.loads((fixtures / "manifest.json").read_text(encoding="utf-8"))
    assert manifest["schemaVersion"] == "open03-08-fixture/1"
    serialized = json.dumps(manifest, ensure_ascii=False)
    assert "HMAC" not in serialized and "nonce" not in serialized
    assert "127.0.0.1" not in serialized
    for item in manifest["fixtures"].values():
        payload = (fixtures / item["file"]).read_bytes()
        import hashlib
        assert len(payload) == item["canonicalLengthBytes"]
        assert hashlib.sha256(payload).hexdigest() == item["sha256"]
