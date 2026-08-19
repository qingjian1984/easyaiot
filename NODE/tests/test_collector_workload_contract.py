import copy
import json
from decimal import Decimal
from pathlib import Path

import pytest

import collector_workload as cw


DIGEST_A = "sha256:" + "a" * 64
DIGEST_B = "sha256:" + "b" * 64


def make_policy(tmp_path, *, profile="full", max_cpu="2.0", max_memory=1024 * 1024 * 1024, windows=False):
    serial = tmp_path / "serial0"
    return cw.CollectorCapabilityPolicy(
        profile=profile,
        allowed_images=(f"registry.example/easyaiot/iot-sink-biz@{DIGEST_A}",),
        collector_root=(tmp_path / "collector").resolve(),
        state_root=(tmp_path / "state").resolve(),
        serial_allowlist=(str(serial),),
        max_cpu_cores=Decimal(max_cpu),
        max_memory_bytes=max_memory,
        windows_enabled=windows,
        windows_java_path=Path("C:/Program Files/Java/bin/java.exe") if windows else None,
        windows_jar_path=Path("C:/Program Files/EasyAIoT/iot-sink.jar") if windows else None,
        windows_runtime_policy_path=Path("C:/ProgramData/EasyAIoT/collector-runtime-policy.json") if windows else None,
        windows_runtime_policy_id="standard-v1" if windows else "",
    )


def make_spec(policy, *, workload_id="collector-site-1001-a"):
    outbox = (policy.collector_root / workload_id / "outbox").resolve()
    return {
        "specVersion": "1.0",
        "workloadType": "iot-sink-collector",
        "workloadId": workload_id,
        "nodeId": "21",
        "image": {
            "repository": "registry.example/easyaiot/iot-sink-biz",
            "digest": DIGEST_A,
        },
        "springProfile": "collector",
        "config": {
            "version": 6,
            "sha256": "c" * 64,
            "targetPath": "/var/lib/easyaiot/config/active.json",
        },
        "resources": {"cpuCores": "1.0", "memoryBytes": 402653184},
        "serialDevices": [
            {
                "hostPath": str(policy.serial_allowlist[0]),
                "containerPath": "/dev/easyaiot/rs485-0",
                "hardwareFingerprint": "usb:vid:pid:serial",
                "readOnly": False,
            }
        ],
        "volumes": [
            {
                "name": "outbox",
                "hostPath": str(outbox),
                "containerPath": "/var/lib/easyaiot/outbox",
                "mode": "rw",
            }
        ],
        "brokerRef": "secret://node/21/collector/site-1001",
        "updatePolicy": {
            "dispatchAckTimeoutSeconds": 10,
            "configApplyTimeoutSeconds": 60,
            "healthWindowSeconds": 60,
            "autoRollback": True,
        },
    }


def test_schema_is_byte_identical_to_java_resource():
    java_schema = Path(
        "DEVICE/iot-node/iot-node-api/src/main/resources/schema/collector/workload/v1/"
        "collector-workload-spec-v1.json"
    ).read_bytes()
    node_schema = Path("NODE/schemas/collector-workload-spec-v1.json").read_bytes()
    assert node_schema == java_schema
    json.loads(node_schema.decode("utf-8"))


def test_valid_linux_plan_is_fixed_and_uses_device_rwm(tmp_path):
    policy = make_policy(tmp_path)
    spec = make_spec(policy)
    plan = cw.build_deployment_plan(spec, policy, platform="linux")
    data = plan.as_dict()

    assert plan.platform == "linux"
    assert len(plan.project) == 49
    assert plan.project == plan.project.lower()
    assert data["argv"] == ["docker", "compose", "-p", plan.project, "-f", "-", "up", "-d", "--no-build"]
    service = data["compose"]["services"]["collector"]
    assert service["restart"] == "on-failure:5"
    assert service["container_name"].startswith(plan.project + "-")
    assert len(service["container_name"]) == 53
    assert service["devices"] == [f"{spec['serialDevices'][0]['hostPath']}:/dev/easyaiot/rs485-0:rwm"]
    assert all("serial0" not in volume for volume in service["volumes"])
    assert service["command"] == ["java", "-jar", "app.jar", "--spring.profiles.active=collector"]
    assert "brokerRef" not in json.dumps(data)
    assert "secret://" not in json.dumps(data)


def test_project_identity_is_stable_for_spec_changes_and_distinguishes_workloads(tmp_path):
    policy = make_policy(tmp_path)
    first = make_spec(policy)
    second = copy.deepcopy(first)
    second["config"]["version"] = 7
    second["config"]["sha256"] = "d" * 64
    first_plan = cw.build_deployment_plan(first, policy, platform="linux")
    second_plan = cw.build_deployment_plan(second, policy, platform="linux")
    assert first_plan.project == second_plan.project
    assert first_plan.as_dict()["project"] == second_plan.as_dict()["project"]

    same_prefix_a = make_spec(policy, workload_id="collector-" + "x" * 30 + "-one")
    same_prefix_b = make_spec(policy, workload_id="collector-" + "x" * 30 + "-two")
    case_variant = make_spec(policy, workload_id="Collector-Site-1001-A")
    first_prefix_plan = cw.build_deployment_plan(same_prefix_a, policy, platform="linux")
    second_prefix_plan = cw.build_deployment_plan(same_prefix_b, policy, platform="linux")
    case_variant_plan = cw.build_deployment_plan(case_variant, policy, platform="linux")
    assert first_prefix_plan.project != second_prefix_plan.project
    assert first_plan.project != case_variant_plan.project
    assert first_plan.project.isascii() and second_plan.project.isascii()


def test_broker_ref_is_retained_in_opaque_plan_but_never_serialized_or_executed_unresolved(tmp_path, monkeypatch):
    policy = make_policy(tmp_path)
    spec = make_spec(policy)
    plan = cw.build_deployment_plan(spec, policy, platform="linux")
    broker_ref = spec["brokerRef"]

    assert plan._broker_ref.value_for_executor() == broker_ref
    assert broker_ref not in repr(plan)
    assert broker_ref not in str(plan)
    assert broker_ref not in json.dumps(plan.as_dict())
    assert broker_ref not in json.dumps(plan.as_dict()["compose"])
    assert broker_ref not in " ".join(plan.argv)

    subprocess_calls = []
    monkeypatch.setattr(cw.subprocess, "run", lambda *args, **kwargs: subprocess_calls.append((args, kwargs)))
    with pytest.raises(cw.CollectorDeploymentError) as exc:
        cw.DockerComposeCollectorExecutor().execute(plan)
    assert exc.value.code == "COLLECTOR_DEPLOY_CONFIGURATION_INVALID"
    assert subprocess_calls == []


def test_fake_secret_resolver_lease_is_used_only_inside_executor(tmp_path, monkeypatch):
    policy = make_policy(tmp_path)
    spec = make_spec(policy)
    plan = cw.build_deployment_plan(spec, policy, platform="linux")

    events = []

    class FakeLease:
        secret_file_path = str((tmp_path / "lease.env").resolve())
        closed = False
        committed_projects = []

        def __init__(self):
            Path(self.secret_file_path).write_text("synthetic lease only", encoding="utf-8")
            if __import__("os").name != "nt":
                Path(self.secret_file_path).chmod(0o600)

        def is_usable(self):
            return True

        def close(self):
            self.closed = True

        def commit(self, project):
            events.append("commit")
            self.committed_projects.append(project)
            return True

    class FakeResolver:
        def __init__(self):
            self.refs = []
            self.lease = FakeLease()

        def resolve(self, broker_ref):
            events.append("resolve")
            self.refs.append(broker_ref)
            return self.lease

    resolver = FakeResolver()
    subprocess_calls = []

    def fake_run(*args, **kwargs):
        events.append("subprocess")
        subprocess_calls.append((args, kwargs))

    monkeypatch.setattr(cw.subprocess, "run", fake_run)
    cw.DockerComposeCollectorExecutor(resolver).execute(plan)
    assert resolver.refs == [spec["brokerRef"]]
    assert resolver.lease.closed is False
    assert len(subprocess_calls) == 1
    execution_argv = subprocess_calls[0][0][0]
    execution_compose = json.loads(subprocess_calls[0][1]["input"].decode("utf-8"))
    assert spec["brokerRef"] not in execution_argv
    assert spec["brokerRef"] not in json.dumps(execution_compose)
    assert str(Path(resolver.lease.secret_file_path)) not in execution_argv
    assert execution_compose["secrets"]["easyaiot-broker"]["file"] == str(Path(resolver.lease.secret_file_path))
    assert resolver.lease.committed_projects == [plan.project]
    assert events == ["resolve", "commit", "subprocess"]


def test_secret_lease_failure_closes_without_retaining(tmp_path, monkeypatch):
    policy = make_policy(tmp_path)
    plan = cw.build_deployment_plan(make_spec(policy), policy, platform="linux")

    class FailingLease:
        def __init__(self):
            self.path = (tmp_path / "failed-lease.env").resolve()
            self.path.write_text("synthetic lease only", encoding="utf-8")
            if __import__("os").name != "nt":
                self.path.chmod(0o600)
            self.closed = False

        @property
        def secret_file_path(self):
            return str(self.path)

        def is_usable(self):
            return True

        def close(self):
            self.closed = True

        def commit(self, project):
            return False

    class Resolver:
        def __init__(self):
            self.lease = FailingLease()

        def resolve(self, broker_ref):
            return self.lease

    resolver = Resolver()
    subprocess_calls = []
    monkeypatch.setattr(cw.subprocess, "run", lambda *args, **kwargs: subprocess_calls.append((args, kwargs)))
    with pytest.raises(cw.CollectorDeploymentError) as exc:
        cw.DockerComposeCollectorExecutor(resolver).execute(plan)
    assert exc.value.code == "COLLECTOR_DEPLOY_FAILED"
    assert resolver.lease.closed is True
    assert subprocess_calls == []


def test_subprocess_failure_closes_committed_lease(tmp_path, monkeypatch):
    policy = make_policy(tmp_path)
    plan = cw.build_deployment_plan(make_spec(policy), policy, platform="linux")

    class Lease:
        def __init__(self):
            self.path = (tmp_path / "subprocess-failure.env").resolve()
            self.path.write_text("synthetic lease only", encoding="utf-8")
            if __import__("os").name != "nt":
                self.path.chmod(0o600)
            self.committed = False
            self.closed = False

        @property
        def secret_file_path(self):
            return str(self.path)

        def is_usable(self):
            return True

        def commit(self, project):
            self.committed = True
            return True

        def close(self):
            self.closed = True

    class Resolver:
        def __init__(self):
            self.lease = Lease()

        def resolve(self, broker_ref):
            return self.lease

    resolver = Resolver()

    def fail_run(*args, **kwargs):
        raise RuntimeError("synthetic subprocess failure")

    monkeypatch.setattr(cw.subprocess, "run", fail_run)
    with pytest.raises(cw.CollectorDeploymentError) as exc:
        cw.DockerComposeCollectorExecutor(resolver).execute(plan)
    assert exc.value.code == "COLLECTOR_DEPLOY_FAILED"
    assert resolver.lease.committed is True
    assert resolver.lease.closed is True


def test_missing_install_policy_is_fail_closed(tmp_path):
    with pytest.raises(cw.CollectorDeploymentError) as exc:
        cw.load_collector_policy(
            {
                "EASYAIOT_DEPLOY_PROFILE": "full",
                "COLLECTOR_ROOT": str(tmp_path / "collector"),
                "COLLECTOR_STATE_ROOT": str(tmp_path / "state"),
                "COLLECTOR_SERIAL_ALLOWLIST": str(tmp_path / "serial0"),
                "COLLECTOR_CPU_MAX": "2.0",
                "COLLECTOR_MEMORY_MAX_BYTES": "1073741824",
            }
        )
    assert exc.value.code == "COLLECTOR_DEPLOY_CONFIGURATION_INVALID"


def test_mini_is_rejected_even_with_a_valid_spec(tmp_path):
    policy = make_policy(tmp_path, profile="mini")
    with pytest.raises(cw.CollectorDeploymentError) as exc:
        cw.build_deployment_plan(make_spec(policy), policy)
    assert exc.value.code == "COLLECTOR_PROFILE_UNSUPPORTED"


def test_repository_digest_pair_is_indivisible(tmp_path):
    policy = make_policy(tmp_path)
    spec = make_spec(policy)
    spec["image"]["digest"] = DIGEST_B
    with pytest.raises(cw.CollectorDeploymentError) as exc:
        cw.build_deployment_plan(spec, policy)
    assert exc.value.code == "COLLECTOR_IMAGE_FORBIDDEN"

    spec = make_spec(policy)
    spec["image"]["repository"] = "registry.example/easyaiot/iot-sink-biz:latest"
    with pytest.raises(cw.CollectorDeploymentError) as exc:
        cw.build_deployment_plan(spec, policy)
    assert exc.value.code in {"COLLECTOR_WORKLOAD_SCHEMA_INVALID", "COLLECTOR_IMAGE_FORBIDDEN"}


def test_schema_transport_and_install_resource_limits_are_distinct(tmp_path):
    policy = make_policy(tmp_path, max_cpu="1.5", max_memory=402653184)
    spec = make_spec(policy)
    spec["resources"]["cpuCores"] = "64"
    with pytest.raises(cw.CollectorDeploymentError) as exc:
        cw.build_deployment_plan(spec, policy)
    assert exc.value.code == "COLLECTOR_RESOURCE_LIMIT_EXCEEDED"

    spec = make_spec(policy)
    spec["resources"]["memoryBytes"] = 402653185
    with pytest.raises(cw.CollectorDeploymentError) as exc:
        cw.build_deployment_plan(spec, policy)
    assert exc.value.code == "COLLECTOR_RESOURCE_LIMIT_EXCEEDED"


def test_unknown_command_env_files_and_arbitrary_config_path_fail_closed(tmp_path):
    policy = make_policy(tmp_path)
    for field, value in (("command", ["sh", "-c", "id"]), ("env", {"BROKER": "secret"}), ("files", {"x": "y"})):
        spec = make_spec(policy)
        spec[field] = value
        with pytest.raises(cw.CollectorDeploymentError) as exc:
            cw.build_deployment_plan(spec, policy)
        assert exc.value.code == "COLLECTOR_WORKLOAD_SCHEMA_INVALID"

    spec = make_spec(policy)
    spec["config"]["targetPath"] = str(tmp_path / "arbitrary-host-path")
    with pytest.raises(cw.CollectorDeploymentError) as exc:
        cw.build_deployment_plan(spec, policy)
    assert exc.value.code == "COLLECTOR_WORKLOAD_SCHEMA_INVALID"


def test_cross_workload_volume_parent_and_symlink_escape_fail_closed(tmp_path, monkeypatch):
    policy = make_policy(tmp_path)
    spec = make_spec(policy)
    spec["volumes"][0]["hostPath"] = str((policy.collector_root / "another" / "outbox").resolve())
    with pytest.raises(cw.CollectorDeploymentError) as exc:
        cw.build_deployment_plan(spec, policy)
    assert exc.value.code == "COLLECTOR_PATH_FORBIDDEN"

    spec = make_spec(policy)
    spec["volumes"][0]["hostPath"] = str(policy.collector_root / spec["workloadId"] / ".." / "other" / "outbox")
    with pytest.raises(cw.CollectorDeploymentError) as exc:
        cw.build_deployment_plan(spec, policy)
    assert exc.value.code == "COLLECTOR_PATH_FORBIDDEN"

    spec = make_spec(policy)
    monkeypatch.setattr(cw, "_is_symlink_component", lambda path: True)
    with pytest.raises(cw.CollectorDeploymentError) as exc:
        cw.build_deployment_plan(spec, policy)
    assert exc.value.code == "COLLECTOR_PATH_FORBIDDEN"


def test_by_id_symlink_only_allows_target_inside_dev():
    assert cw._serial_symlink_is_allowed(Path("/dev/serial/by-id/usb-demo"), Path("/dev/ttyUSB0"))
    assert not cw._serial_symlink_is_allowed(Path("/dev/serial/by-id/usb-demo"), Path("/etc/passwd"))
    assert not cw._serial_symlink_is_allowed(Path("/tmp/serial-link"), Path("/dev/ttyUSB0"))


def test_windows_is_closed_by_default_and_uses_install_paths_when_explicitly_enabled(tmp_path):
    policy = make_policy(tmp_path, windows=False)
    with pytest.raises(cw.CollectorDeploymentError) as exc:
        cw.build_deployment_plan(make_spec(policy), policy, platform="windows")
    assert exc.value.code == "COLLECTOR_DEPLOY_CONFIGURATION_INVALID"

    enabled = make_policy(tmp_path, windows=True)
    plan = cw.build_deployment_plan(make_spec(enabled), enabled, platform="windows")
    assert Path(plan.argv[0]).is_absolute()
    assert plan.argv[1:4] == ("-jar", str(Path("C:/Program Files/EasyAIoT/iot-sink.jar")), "--spring.profiles.active=collector")
    assert "runtime-policy" in " ".join(plan.argv)
    assert "secret://" not in json.dumps(plan.as_dict())


def test_enabled_windows_policy_requires_all_install_paths(tmp_path):
    with pytest.raises(cw.CollectorDeploymentError) as exc:
        cw.CollectorCapabilityPolicy(
            profile="full",
            allowed_images=(f"registry.example/easyaiot/iot-sink-biz@{DIGEST_A}",),
            collector_root=tmp_path / "collector",
            state_root=tmp_path / "state",
            serial_allowlist=(str(tmp_path / "serial0"),),
            max_cpu_cores=Decimal("2"),
            max_memory_bytes=1024,
            windows_enabled=True,
        )
    assert exc.value.code == "COLLECTOR_DEPLOY_CONFIGURATION_INVALID"
