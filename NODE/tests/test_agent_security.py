import os
import stat
import tempfile

import pytest

from agent_security import (
    AgentAuthError,
    AgentSigningKey,
    FileAgentSigningKeyProvider,
    InMemoryAgentSigningKeyProvider,
    PersistentNonceStore,
    sign_agent_request,
    verify_agent_request,
)


CURRENT = b"synthetic-node-key-012345678901234567890"
PREVIOUS = b"synthetic-old-key-012345678901234567890123"


def test_hmac_success_and_persistent_replay_window(tmp_path):
    key = AgentSigningKey(7, "k2", CURRENT)
    provider = InMemoryAgentSigningKeyProvider(
        [key, AgentSigningKey(7, "k1", PREVIOUS)]
    )
    replay_db = tmp_path / "replay.db"
    store = PersistentNonceStore(str(replay_db), ttl_seconds=600)
    headers = sign_agent_request(
        "PUT", "/workload/collector/config", b"{}", 7, key,
        timestamp=1_700_000_000, nonce="00112233445566778899aabbccddeeff"
    )

    selected = verify_agent_request(
        "PUT", "/workload/collector/config", b"{}", headers, provider, store,
        now=1_700_000_000
    )
    assert selected.key_id == "k2"
    with pytest.raises(AgentAuthError, match="AGENT_AUTH_REPLAYED"):
        verify_agent_request(
            "PUT", "/workload/collector/config", b"{}", headers, provider, store,
            now=1_700_000_000
        )

    reopened = PersistentNonceStore(str(replay_db), ttl_seconds=600)
    with pytest.raises(AgentAuthError, match="AGENT_AUTH_REPLAYED"):
        verify_agent_request(
            "PUT", "/workload/collector/config", b"{}", headers, provider, reopened,
            now=1_700_000_001
        )


def test_body_timestamp_key_and_signature_fail_closed(tmp_path):
    key = AgentSigningKey(7, "k2", CURRENT)
    provider = InMemoryAgentSigningKeyProvider([key])
    store = PersistentNonceStore(str(tmp_path / "replay.db"))
    headers = sign_agent_request(
        "PUT", "/workload/collector/config", b"payload", 7, key,
        timestamp=1_700_000_000, nonce="11112222333344445555666677778888"
    )

    with pytest.raises(AgentAuthError, match="AGENT_AUTH_BODY_HASH_MISMATCH"):
        verify_agent_request("PUT", "/workload/collector/config", b"tampered", headers,
                             provider, store, now=1_700_000_000)
    with pytest.raises(AgentAuthError, match="AGENT_AUTH_EXPIRED"):
        verify_agent_request("PUT", "/workload/collector/config", b"payload", headers,
                             provider, store, now=1_700_000_301)

    wrong_key = dict(headers)
    wrong_key["X-EasyAIoT-Key-Id"] = "unknown"
    with pytest.raises(AgentAuthError, match="AGENT_AUTH_KEY_UNKNOWN"):
        verify_agent_request("PUT", "/workload/collector/config", b"payload", wrong_key,
                             provider, store, now=1_700_000_000)

    wrong_signature = dict(headers)
    wrong_signature["X-EasyAIoT-Signature"] = "0" * 64
    with pytest.raises(AgentAuthError, match="AGENT_AUTH_SIGNATURE_INVALID"):
        verify_agent_request("PUT", "/workload/collector/config", b"payload", wrong_signature,
                             provider, store, now=1_700_000_000)


def test_file_provider_requires_private_permissions(tmp_path):
    key_file = tmp_path / "agent-signing-key"
    key_file.write_text(
        "nodeId=7\ncurrentKeyId=k2\ncurrentKey=" + CURRENT.decode() + "\n"
        "previousKeyId=k1\npreviousKey=" + PREVIOUS.decode() + "\n",
        encoding="utf-8",
    )
    os.chmod(key_file, stat.S_IRUSR | stat.S_IWUSR)
    provider = FileAgentSigningKeyProvider(str(key_file))
    assert [key.key_id for key in provider.keys(7)] == ["k2", "k1"]
    assert provider.keys(8) == ()
