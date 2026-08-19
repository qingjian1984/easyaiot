import copy
import json
import time
from decimal import Decimal

import pytest

import agent_server
import collector_workload as cw
from agent_security import (
    AgentSigningKey,
    InMemoryAgentSigningKeyProvider,
    PersistentNonceStore,
    sign_agent_request,
)


KEY = AgentSigningKey(7, "k1", b"synthetic-node-key-012345678901234567890")
DIGEST = "sha256:" + "a" * 64


class FakeExecutor:
    def __init__(self):
        self.calls = []

    def execute(self, plan):
        self.calls.append(plan)


def make_policy(tmp_path):
    return cw.CollectorCapabilityPolicy(
        profile="full",
        allowed_images=(f"registry.example/easyaiot/iot-sink-biz@{DIGEST}",),
        collector_root=(tmp_path / "collector").resolve(),
        state_root=(tmp_path / "state").resolve(),
        serial_allowlist=(str(tmp_path / "serial0"),),
        max_cpu_cores=Decimal("2.0"),
        max_memory_bytes=1024 * 1024 * 1024,
    )


def make_spec(policy, *, workload_id="collector-site-1001-a"):
    return {
        "specVersion": "1.0",
        "workloadType": "iot-sink-collector",
        "workloadId": workload_id,
        "nodeId": "21",
        "image": {"repository": "registry.example/easyaiot/iot-sink-biz", "digest": DIGEST},
        "springProfile": "collector",
        "config": {"version": 6, "sha256": "c" * 64, "targetPath": "/var/lib/easyaiot/config/active.json"},
        "resources": {"cpuCores": "1.0", "memoryBytes": 402653184},
        "serialDevices": [{
            "hostPath": str(policy.serial_allowlist[0]),
            "containerPath": "/dev/easyaiot/rs485-0",
            "hardwareFingerprint": "usb:vid:pid:serial",
            "readOnly": False,
        }],
        "volumes": [{
            "name": "outbox",
            "hostPath": str((policy.collector_root / workload_id / "outbox").resolve()),
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


def signed_headers(method, path, body, nonce):
    return sign_agent_request(
        method,
        path,
        body,
        KEY.node_id,
        KEY,
        timestamp=int(time.time()),
        nonce=nonce,
    )


@pytest.fixture
def configured_agent(tmp_path, monkeypatch):
    fake = FakeExecutor()
    policy = make_policy(tmp_path)
    monkeypatch.setattr(agent_server, "_agent_signing_key_provider", InMemoryAgentSigningKeyProvider([KEY]))
    monkeypatch.setattr(agent_server, "_agent_nonce_store", PersistentNonceStore(str(tmp_path / "replay.db")))
    monkeypatch.setattr(agent_server, "collector_service", cw.CollectorDeploymentService(fake))
    monkeypatch.setattr(agent_server, "load_collector_policy", lambda: policy)
    monkeypatch.setattr(agent_server, "AGENT_TOKEN", "legacy-token")
    monkeypatch.setenv("COLLECTOR_AGENT_PLATFORM", "linux")
    agent_server.app.testing = True
    return agent_server.app.test_client(), fake, policy


def test_hmac_success_reaches_fake_executor_without_secret_material(configured_agent):
    client, fake, policy = configured_agent
    body = json.dumps(make_spec(policy), separators=(",", ":")).encode("utf-8")
    response = client.post(
        "/workload/collector/deploy",
        data=body,
        headers={**signed_headers("POST", "/workload/collector/deploy", body, "00112233445566778899aabbccddeeff"), "Content-Type": "application/json"},
    )
    assert response.status_code == 200
    assert len(fake.calls) == 1
    result = response.get_json()["data"]
    assert result["lifecycle"] == "REQUESTED"
    assert "secret://" not in json.dumps(result)
    assert "synthetic-node-key" not in json.dumps(result)
    assert fake.calls[0].platform == "linux"


def test_token_only_and_replayed_requests_are_rejected_before_executor(configured_agent):
    client, fake, policy = configured_agent
    invalid_json = b"{not-json"
    token_only = client.post(
        "/workload/collector/deploy",
        data=invalid_json,
        headers={"X-Agent-Token": "legacy-token", "Content-Type": "application/json"},
    )
    assert token_only.status_code == 401
    assert token_only.get_json()["msg"] == "AGENT_AUTH_MISSING"
    assert not fake.calls

    body = json.dumps(make_spec(policy), separators=(",", ":")).encode("utf-8")
    headers = {**signed_headers("POST", "/workload/collector/deploy", body, "11112222333344445555666677778888"), "Content-Type": "application/json"}
    first = client.post("/workload/collector/deploy", data=body, headers=headers)
    replay = client.post("/workload/collector/deploy", data=body, headers=headers)
    assert first.status_code == 200
    assert replay.status_code == 401
    assert replay.get_json()["msg"] == "AGENT_AUTH_REPLAYED"
    assert len(fake.calls) == 1


def test_body_hash_and_signature_fail_closed(configured_agent):
    client, fake, policy = configured_agent
    body = json.dumps(make_spec(policy), separators=(",", ":")).encode("utf-8")
    headers = signed_headers("POST", "/workload/collector/deploy", body, "22223333444455556666777788889999")
    mismatched = client.post(
        "/workload/collector/deploy",
        data=body + b" ",
        headers={**headers, "Content-Type": "application/json"},
    )
    assert mismatched.status_code == 401
    assert mismatched.get_json()["msg"] == "AGENT_AUTH_BODY_HASH_MISMATCH"
    assert not fake.calls

    wrong_path_headers = signed_headers("POST", "/workload/collector/deploy", body, "3333444455556666777788889999aaaa")
    wrong_path_headers["X-EasyAIoT-Signature"] = "0" * 64
    invalid_signature = client.post(
        "/workload/collector/deploy",
        data=body,
        headers={**wrong_path_headers, "Content-Type": "application/json"},
    )
    assert invalid_signature.status_code == 401
    assert invalid_signature.get_json()["msg"] == "AGENT_AUTH_SIGNATURE_INVALID"
    assert not fake.calls


def test_generic_collector_entrypoint_rejects_before_port_probe_or_manager(configured_agent, monkeypatch):
    client, fake, _ = configured_agent
    calls = []
    monkeypatch.setattr(agent_server, "find_available_port", lambda *args, **kwargs: calls.append("port"))
    monkeypatch.setattr(agent_server.manager, "deploy", lambda spec: calls.append("manager"))
    body = json.dumps({"workloadType": "iot-sink-collector", "workloadId": "collector-1", "command": ["id"]}).encode()
    response = client.post(
        "/workload/deploy",
        data=body,
        headers={"X-Agent-Token": "legacy-token", "Content-Type": "application/json"},
    )
    assert response.status_code == 400
    assert response.get_json()["msg"] == "UNSUPPORTED_GENERIC_DEPLOY"
    assert calls == []
    assert fake.calls == []

    # collector 类型优先级高于通用 workloadId 必填校验。
    missing_id_body = json.dumps({"workloadType": "iot-sink-collector", "command": ["id"]}).encode()
    missing_id = client.post(
        "/workload/deploy",
        data=missing_id_body,
        headers={"X-Agent-Token": "legacy-token", "Content-Type": "application/json"},
    )
    assert missing_id.status_code == 400
    assert missing_id.get_json()["msg"] == "UNSUPPORTED_GENERIC_DEPLOY"
    assert calls == []
    assert fake.calls == []


def test_malicious_spec_has_zero_side_effects(configured_agent, monkeypatch):
    client, fake, policy = configured_agent
    calls = []
    monkeypatch.setattr(agent_server, "find_available_port", lambda *args, **kwargs: calls.append("port"))
    malicious = copy.deepcopy(make_spec(policy))
    malicious["command"] = ["sh", "-c", "touch /tmp/pwned"]
    body = json.dumps(malicious, separators=(",", ":")).encode()
    response = client.post(
        "/workload/collector/deploy",
        data=body,
        headers={**signed_headers("POST", "/workload/collector/deploy", body, "444455556666777788889999aaaabbbb"), "Content-Type": "application/json"},
    )
    assert response.status_code == 400
    assert response.get_json()["msg"] == "COLLECTOR_WORKLOAD_SCHEMA_INVALID"
    assert not fake.calls
    assert calls == []
    assert not (policy.collector_root / "collector-site-1001-a").exists()


def test_valid_route_never_calls_subprocess_or_materializes_env_files(configured_agent, monkeypatch):
    client, fake, policy = configured_agent
    subprocess_calls = []
    monkeypatch.setattr(cw.subprocess, "run", lambda *args, **kwargs: subprocess_calls.append((args, kwargs)))
    body = json.dumps(make_spec(policy), separators=(",", ":")).encode()
    response = client.post(
        "/workload/collector/deploy",
        data=body,
        headers={**signed_headers("POST", "/workload/collector/deploy", body, "55556666777788889999aaaabbbbcccc"), "Content-Type": "application/json"},
    )
    assert response.status_code == 200
    assert len(fake.calls) == 1
    assert subprocess_calls == []
