from agent_security import (
    AgentSigningKey,
    InMemoryAgentSigningKeyProvider,
    PersistentNonceStore,
    sign_agent_request,
)
import time


def test_collector_routes_do_not_fallback_to_agent_token(tmp_path, monkeypatch):
    import agent_server

    key = AgentSigningKey(7, "k1", b"synthetic-node-key-012345678901234567890")
    monkeypatch.setattr(
        agent_server,
        "_agent_signing_key_provider",
        InMemoryAgentSigningKeyProvider([key]),
    )
    monkeypatch.setattr(
        agent_server,
        "_agent_nonce_store",
        PersistentNonceStore(str(tmp_path / "replay.db")),
    )
    client = agent_server.app.test_client()

    token_only = client.put(
        "/workload/collector/deploy",
        data=b"{}",
        headers={"X-Agent-Token": "legacy-token"},
    )
    assert token_only.status_code == 401
    assert token_only.get_json()["msg"] == "AGENT_AUTH_MISSING"

    headers = sign_agent_request(
        "POST", "/workload/collector/deploy", b"{}", 7, key,
        timestamp=int(time.time()), nonce="00112233445566778899aabbccddeeff",
    )
    signed = client.post("/workload/collector/deploy", data=b"{}", headers=headers)
    # 签名成功后必须到达已实现的专用路由；404 不能作为“签名后成功”的预期。
    assert signed.status_code == 400
    assert signed.get_json()["msg"] in {
        "COLLECTOR_WORKLOAD_SCHEMA_INVALID",
        "COLLECTOR_DEPLOY_CONFIGURATION_INVALID",
    }
