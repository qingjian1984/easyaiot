from agent_security import (
    AgentSigningKey,
    InMemoryAgentSigningKeyProvider,
    PersistentNonceStore,
    sign_agent_request,
)
import time
import pytest


def test_collector_routes_do_not_fallback_to_agent_token(tmp_path, monkeypatch):
    pytest.importorskip("flask")
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
        "/workload/collector/config",
        data=b"{}",
        headers={"X-Agent-Token": "legacy-token"},
    )
    assert token_only.status_code == 401
    assert token_only.get_json()["msg"] == "AGENT_AUTH_MISSING"

    headers = sign_agent_request(
        "PUT", "/workload/collector/config", b"{}", 7, key,
        timestamp=int(time.time()), nonce="00112233445566778899aabbccddeeff",
    )
    signed = client.put("/workload/collector/config", data=b"{}", headers=headers)
    # 认证通过后才到达当前尚未实现的路由；不能因 404 把 token-only 误报为成功。
    assert signed.status_code == 404
