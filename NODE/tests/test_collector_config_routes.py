import hashlib
import json
from pathlib import Path
import sys
import time

import pytest

import agent_server
import collector_config_state as cs
sys.path.insert(0, str(Path(__file__).parent))
from agent_security import (
    AgentSigningKey,
    InMemoryAgentSigningKeyProvider,
    PersistentNonceStore,
    sign_agent_request,
)
from test_collector_config_contract import envelope, raw_envelope


CURRENT = AgentSigningKey(7, "current", b"synthetic-node-key-012345678901234567890")
PREVIOUS = AgentSigningKey(7, "previous", b"synthetic-old-key-012345678901234567890123")


def sign(method, path, body, key, nonce):
    return sign_agent_request(
        method,
        path,
        body,
        key.node_id,
        key,
        timestamp=int(time.time()),
        nonce=nonce,
    )


@pytest.fixture
def configured_agent(tmp_path, monkeypatch):
    root = tmp_path / "state"
    service = cs.CollectorConfigStateService(root)
    monkeypatch.setattr(
        agent_server,
        "_agent_signing_key_provider",
        InMemoryAgentSigningKeyProvider([CURRENT, PREVIOUS]),
    )
    monkeypatch.setattr(
        agent_server,
        "_agent_nonce_store",
        PersistentNonceStore(str(tmp_path / "replay.db")),
    )
    monkeypatch.setattr(agent_server, "collector_config_state_service", service)
    monkeypatch.setattr(agent_server, "AGENT_TOKEN", "legacy-token")
    agent_server.app.testing = True
    return agent_server.app.test_client(), service, root


def test_put_hmac_success_only_writes_desired_and_redacts_response(configured_agent):
    client, _service, root = configured_agent
    body = raw_envelope()
    response = client.put(
        "/workload/collector/config",
        data=body,
        headers={
            **sign("PUT", "/workload/collector/config", body, CURRENT, "00112233445566778899aabbccddeeff"),
            "Content-Type": "application/json",
        },
    )
    assert response.status_code == 200
    data = response.get_json()["data"]
    assert data["status"] == "ACCEPTED"
    assert "payloadCanonical" not in json.dumps(data)
    assert "canonicalizationVersion" not in json.dumps(data)
    assert "nonce" not in json.dumps(data).lower()
    assert "signature" not in json.dumps(data).lower()
    config_directory = cs.collector_config_directory(root, "collector-site-1001-a")
    assert (config_directory / "desired.json").exists()
    assert not (config_directory / "active.json").exists()
    assert not (config_directory / "observed.json").exists()


def test_previous_key_rotation_and_idempotent_conflict(configured_agent):
    client, _service, _root = configured_agent
    body = raw_envelope()
    first_headers = {
        **sign("PUT", "/workload/collector/config", body, PREVIOUS, "11112222333344445555666677778888"),
        "Content-Type": "application/json",
    }
    assert client.put("/workload/collector/config", data=body, headers=first_headers).status_code == 200
    replay = client.put("/workload/collector/config", data=body, headers=first_headers)
    assert replay.status_code == 401
    assert replay.get_json()["msg"] == "AGENT_AUTH_REPLAYED"

    same = client.put(
        "/workload/collector/config",
        data=body,
        headers={
            **sign("PUT", "/workload/collector/config", body, CURRENT, "22223333444455556666777788889999"),
            "Content-Type": "application/json",
        },
    )
    assert same.status_code == 200
    assert same.get_json()["data"]["status"] == "IDEMPOTENT"

    conflict_value = envelope()
    payload_value = json.loads(conflict_value["payloadCanonical"])
    payload_value["productIdentification"] = "other-product"
    canonical = json.dumps(
        payload_value, ensure_ascii=False, sort_keys=True, separators=(",", ":")
    ).encode()
    conflict_value["payloadCanonical"] = canonical.decode()
    conflict_value["payloadSha256"] = hashlib.sha256(canonical).hexdigest()
    conflict_value["canonicalLengthBytes"] = len(canonical)
    conflict = json.dumps(conflict_value, separators=(",", ":"), ensure_ascii=False).encode()
    response = client.put(
        "/workload/collector/config",
        data=conflict,
        headers={
            **sign("PUT", "/workload/collector/config", conflict, CURRENT, "3333444455556666777788889999aaaa"),
            "Content-Type": "application/json",
        },
    )
    assert response.status_code == 409
    assert response.get_json()["msg"] == "CONFIG_VERSION_CONFLICT"


def test_token_only_replay_body_hash_and_malformed_requests_have_zero_disk_side_effects(configured_agent):
    client, _service, root = configured_agent
    token_only = client.put(
        "/workload/collector/config",
        data=b"not-json",
        headers={"X-Agent-Token": "legacy-token", "Content-Type": "application/json"},
    )
    assert token_only.status_code == 401
    assert token_only.get_json()["msg"] == "AGENT_AUTH_MISSING"
    assert not root.exists()

    body = raw_envelope()
    headers = sign("PUT", "/workload/collector/config", body, CURRENT, "444455556666777788889999aaaabbbb")
    mismatch = client.put(
        "/workload/collector/config",
        data=body + b" ",
        headers={**headers, "Content-Type": "application/json"},
    )
    assert mismatch.status_code == 401
    assert mismatch.get_json()["msg"] == "AGENT_AUTH_BODY_HASH_MISMATCH"
    assert not root.exists()

    invalid_json = b"{not-json"
    invalid_headers = sign("PUT", "/workload/collector/config", invalid_json, CURRENT, "55556666777788889999aaaabbbbcccc")
    invalid = client.put(
        "/workload/collector/config",
        data=invalid_json,
        headers={**invalid_headers, "Content-Type": "application/json"},
    )
    assert invalid.status_code == 400
    assert invalid.get_json()["msg"] == "COLLECTOR_CONFIG_REQUEST_INVALID"
    assert not root.exists()


def test_invalid_envelope_is_rejected_before_state_root_resolution(configured_agent, monkeypatch):
    client, _service, root = configured_agent
    monkeypatch.setattr(agent_server, "collector_config_state_service", None)
    monkeypatch.delenv("COLLECTOR_STATE_ROOT", raising=False)
    monkeypatch.delenv("EASYAIOT_COLLECTOR_STATE_ROOT", raising=False)
    body = b"{not-json"
    path = "/workload/collector/config"
    response = client.put(
        path,
        data=body,
        headers={
            **sign("PUT", path, body, CURRENT, "66660000111122223333444455556666"),
            "Content-Type": "application/json",
        },
    )
    assert response.status_code == 400
    assert response.get_json()["msg"] == "COLLECTOR_CONFIG_REQUEST_INVALID"
    assert not root.exists()


def test_get_is_hmac_only_redacted_and_reports_missing(configured_agent):
    client, service, _root = configured_agent
    path = "/workload/collector/collector-site-1001-a"
    missing = client.get(
        path,
        headers=sign("GET", path, b"", CURRENT, "6666777788889999aaaabbbbccccdddd"),
    )
    assert missing.status_code == 404
    assert missing.get_json()["msg"] == "COLLECTOR_WORKLOAD_NOT_FOUND"

    body = raw_envelope()
    put_path = "/workload/collector/config"
    assert client.put(
        put_path,
        data=body,
        headers={
            **sign("PUT", put_path, body, CURRENT, "777788889999aaaabbbbccccddddeeee"),
            "Content-Type": "application/json",
        },
    ).status_code == 200
    payload_canonical = json.loads(body.decode())["payloadCanonical"].encode()
    service.write_active("collector-site-1001-a", payload_canonical)
    service.write_observed(
        "collector-site-1001-a",
        {"status": "APPLIED", "configVersion": 1, "payloadSha256": hashlib.sha256(payload_canonical).hexdigest()},
    )
    response = client.get(
        path,
        headers=sign("GET", path, b"", CURRENT, "88889999aaaabbbbccccddddeeeeffff"),
    )
    assert response.status_code == 200
    serialized = json.dumps(response.get_json(), ensure_ascii=False)
    assert "payloadCanonical" not in serialized
    assert "secret://" not in serialized
    assert "signature" not in serialized.lower()
    assert "nonce" not in serialized.lower()
    assert str(service.state_root) not in serialized
    assert response.get_json()["data"]["observed"]["status"] == "APPLIED"


def test_oversized_payload_is_rejected_after_auth_without_workload_directory(configured_agent):
    client, _service, root = configured_agent
    body = b"{" + b"x" * (cs.REQUEST_MAX_BYTES + 1) + b"}"
    response = client.put(
        "/workload/collector/config",
        data=body,
        headers={
            **sign("PUT", "/workload/collector/config", body, CURRENT, "9999aaaabbbbccccddddeeeeffff0000"),
            "Content-Type": "application/json",
        },
    )
    assert response.status_code == 413
    assert response.get_json()["msg"] == "COLLECTOR_CONFIG_TOO_LARGE"
    assert not root.exists()


def test_permission_drift_is_stable_503_and_fail_closed(configured_agent, monkeypatch):
    client, _service, _root = configured_agent

    class PermissionDenied:
        def put(self, _body):
            raise cs.CollectorConfigStateError("COLLECTOR_CONFIG_PERMISSION_INVALID")

    monkeypatch.setattr(agent_server, "collector_config_state_service", PermissionDenied())
    body = raw_envelope()
    response = client.put(
        "/workload/collector/config",
        data=body,
        headers={
            **sign("PUT", "/workload/collector/config", body, CURRENT, "aaaabbbbccccddddeeeeffff00001111"),
            "Content-Type": "application/json",
        },
    )
    assert response.status_code == 503
    assert response.get_json()["msg"] == "COLLECTOR_CONFIG_PERMISSION_INVALID"
