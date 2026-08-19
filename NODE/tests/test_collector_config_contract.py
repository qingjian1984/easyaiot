import copy
import hashlib
import json
from pathlib import Path

import pytest

import collector_config_state as cs


def payload(*, workload_id="collector-site-1001-a", version=1):
    return {
        "schemaVersion": "1.1",
        "productIdentification": "power-meter-v1",
        "workloadId": workload_id,
        "tenantId": "100",
        "siteId": "200",
        "siteCode": "site-a",
        "configVersion": version,
        "generatedAt": "2026-08-17T12:00:00+08:00",
        "serialBuses": [
            {
                "busId": "bus-a",
                "serialPort": "/dev/easyaiot/rs485-0",
                "baudRate": 9600,
                "dataBits": 8,
                "stopBits": "1",
                "parity": "NONE",
                "transmitDelayMs": 0,
                "rs485Mode": True,
                "devices": [
                    {
                        "deviceId": "300",
                        "deviceIdentification": "meter-1",
                        "unitId": 1,
                        "pollIntervalMs": 5000,
                        "requestTimeoutMs": 1000,
                        "maxRetries": 2,
                        "points": [
                            {
                                "propertyCode": "active-power",
                                "function": "HOLDING_REGISTER",
                                "address": 0,
                                "quantity": 2,
                                "dataType": "FLOAT32",
                                "byteOrder": "BIG_ENDIAN",
                                "wordOrder": "BIG_ENDIAN",
                                "scale": "1",
                                "offset": "0",
                                "dataPriority": "METERING_TOTAL",
                                "writable": False,
                                "pollGroup": "normal",
                            }
                        ],
                    }
                ],
            }
        ],
    }


def envelope(value=None, *, workload_id="collector-site-1001-a", version=1):
    value = copy.deepcopy(value or payload(workload_id=workload_id, version=version))
    canonical = cs._canonical_json(value)
    return {
        "workloadId": workload_id,
        "configVersion": version,
        "schemaVersion": "1.1",
        "canonicalizationVersion": "jcs-rfc8785-v1",
        "payloadCanonical": canonical.decode("utf-8"),
        "payloadSha256": hashlib.sha256(canonical).hexdigest(),
        "canonicalLengthBytes": len(canonical),
    }


def raw_envelope(value=None, **kwargs):
    return json.dumps(
        envelope(value, **kwargs), ensure_ascii=False, separators=(",", ":")
    ).encode("utf-8")


def test_schema_is_byte_identical_to_java_resource():
    java = Path(
        "DEVICE/iot-device/iot-device-api/src/main/resources/schema/collector/v1.1/"
        "collector-config-snapshot-v1.1.json"
    ).read_bytes()
    node = Path("NODE/schemas/collector-config-snapshot-v1.1.json").read_bytes()
    assert node == java
    json.loads(node.decode("utf-8"))


def test_valid_envelope_checks_length_hash_jcs_and_cross_identity():
    raw = raw_envelope()
    artifact, _ = cs.validate_config_envelope(raw)
    assert artifact.canonical_bytes == envelope()["payloadCanonical"].encode()
    assert artifact.canonical_length_bytes == len(artifact.canonical_bytes)
    assert artifact.payload_sha256 == hashlib.sha256(artifact.canonical_bytes).hexdigest()

    changed = envelope()
    changed["payloadCanonical"] = changed["payloadCanonical"].replace(
        '"siteCode":"site-a"', '"siteCode": "site-a"'
    )
    changed["canonicalLengthBytes"] = len(changed["payloadCanonical"].encode())
    changed["payloadSha256"] = hashlib.sha256(changed["payloadCanonical"].encode()).hexdigest()
    with pytest.raises(cs.CollectorConfigStateError, match="COLLECTOR_CONFIG_CANONICAL_INVALID"):
        cs.validate_config_envelope(json.dumps(changed, separators=(",", ":")).encode())

    drift = envelope()
    drift["workloadId"] = "collector-other"
    with pytest.raises(cs.CollectorConfigStateError, match="COLLECTOR_CONFIG_CANONICAL_INVALID"):
        cs.validate_config_envelope(json.dumps(drift, separators=(",", ":")).encode())


def test_closed_envelope_duplicate_keys_and_payload_duplicate_keys_fail():
    canonical = envelope()["payloadCanonical"]
    digest = hashlib.sha256(canonical.encode()).hexdigest()
    duplicate = (
        '{"workloadId":"collector-site-1001-a","workloadId":"other",'
        '"configVersion":1,"schemaVersion":"1.1",'
        '"canonicalizationVersion":"jcs-rfc8785-v1",'
        f'"payloadCanonical":{json.dumps(canonical)},"payloadSha256":"{digest}",'
        f'"canonicalLengthBytes":{len(canonical.encode())}}}'
    ).encode()
    with pytest.raises(cs.CollectorConfigStateError, match="COLLECTOR_CONFIG_REQUEST_INVALID"):
        cs.validate_config_envelope(duplicate)

    duplicate_payload = canonical[:-1] + ',"siteCode":"other"}'
    env = envelope()
    env["payloadCanonical"] = duplicate_payload
    env["canonicalLengthBytes"] = len(duplicate_payload.encode())
    env["payloadSha256"] = hashlib.sha256(duplicate_payload.encode()).hexdigest()
    with pytest.raises(cs.CollectorConfigStateError, match="COLLECTOR_CONFIG_REQUEST_INVALID"):
        cs.validate_config_envelope(json.dumps(env, separators=(",", ":")).encode())


@pytest.mark.parametrize(
    "mutator,code",
    [
        (lambda value: value.update({"unknown": 1}), "COLLECTOR_CONFIG_REQUEST_INVALID"),
        (lambda value: value.update({"payloadSha256": "0" * 64}), "COLLECTOR_CONFIG_HASH_MISMATCH"),
        (lambda value: value.update({"canonicalLengthBytes": 1}), "COLLECTOR_CONFIG_HASH_MISMATCH"),
        (lambda value: value.update({"schemaVersion": "1.0"}), "COLLECTOR_CONFIG_SCHEMA_INVALID"),
        (lambda value: value.update({"canonicalizationVersion": "other"}), "COLLECTOR_CONFIG_CANONICAL_INVALID"),
    ],
)
def test_contract_error_codes_are_stable(mutator, code):
    value = envelope()
    mutator(value)
    with pytest.raises(cs.CollectorConfigStateError, match=code):
        cs.validate_config_envelope(json.dumps(value, separators=(",", ":")).encode())


def test_request_and_payload_hard_limits_are_checked_before_state(tmp_path):
    service = cs.CollectorConfigStateService(tmp_path / "state")
    with pytest.raises(cs.CollectorConfigStateError, match="COLLECTOR_CONFIG_TOO_LARGE"):
        service.put(b"{" + b"x" * cs.REQUEST_MAX_BYTES + b"}")
    assert not (tmp_path / "state").exists()

    value = envelope()
    value["payloadCanonical"] = "{" + "x" * cs.PAYLOAD_MAX_BYTES + "}"
    value["canonicalLengthBytes"] = len(value["payloadCanonical"].encode())
    value["payloadSha256"] = hashlib.sha256(value["payloadCanonical"].encode()).hexdigest()
    with pytest.raises(cs.CollectorConfigStateError, match="COLLECTOR_CONFIG_TOO_LARGE"):
        service.put(json.dumps(value, separators=(",", ":")).encode())
    assert not (tmp_path / "state").exists()
