from __future__ import annotations

import base64
import hashlib
import json
from pathlib import Path
from typing import Any

import rfc8785


HERE = Path(__file__).resolve().parent
CASE_DIR = HERE / "legacy-roundtrip" / "easyaiot-legacy-thing-model-v1_td005-1.0.10"


def _copy_present(target: dict[str, Any], source: dict[str, Any], keys: list[str]) -> None:
    for key in keys:
        if key in source and source[key] is not None:
            target[key] = source[key]


def _as_db_text(value: Any) -> str | None:
    return None if value is None else str(value)


def _as_legacy_number(value: Any) -> int | float | None:
    if value is None:
        return None
    text = str(value)
    return float(text) if "." in text else int(text)


def import_to_runtime(document: dict[str, Any]) -> dict[str, Any]:
    tenant_id = document["tenantId"]
    product_identification = document["productIdentification"]
    tables: dict[str, list[dict[str, Any]]] = {
        "product": [{
            "id": 1001,
            "productIdentification": product_identification,
            "tenantId": tenant_id,
        }],
        "product_properties": [],
        "product_services": [],
        "product_commands": [],
        "product_commands_requests": [],
        "product_commands_response": [],
        "product_event": [],
        "product_event_response": [],
    }

    for index, source in enumerate(document.get("properties", [])):
        row = {
            "id": 1101 + index,
            "propertyCode": source["propertyCode"],
            "propertyName": source["propertyName"],
            "datatype": source["datatype"].upper(),
            "templateIdentification": None,
            "productIdentification": product_identification,
            "tenantId": tenant_id,
        }
        _copy_present(row, source, ["method", "step", "unit", "required", "description"])
        for key in ["min", "max"]:
            if key in source:
                row[key] = _as_db_text(source[key])
        tables["product_properties"].append(row)

    request_index = response_index = 0
    for index, source in enumerate(document.get("services", [])):
        service_id = 1201 + index
        command_id = 1301 + index
        service_row = {
            "id": service_id,
            "serviceCode": source["serviceCode"],
            "serviceName": source["serviceName"],
            "status": source.get("status", "0"),
            "templateIdentification": None,
            "productIdentification": product_identification,
            "tenantId": tenant_id,
        }
        _copy_present(service_row, source, ["description"])
        tables["product_services"].append(service_row)
        command_row = {
            "id": command_id,
            "serviceId": service_id,
            "commandCode": source.get("commandCode", source["serviceCode"]),
            "name": source["serviceName"],
            "tenantId": tenant_id,
        }
        _copy_present(command_row, source, ["description"])
        tables["product_commands"].append(command_row)

        for parameter in source.get("inputParams", []):
            row = {
                "id": 1401 + request_index,
                "serviceId": service_id,
                "commandsId": command_id,
                "parameterCode": parameter["parameterCode"],
                "parameterName": parameter["parameterName"],
                "datatype": parameter["datatype"].upper(),
                "tenantId": tenant_id,
            }
            request_index += 1
            for key in ["min", "max", "step", "required"]:
                if key in parameter:
                    row[key] = _as_db_text(parameter[key])
            _copy_present(row, parameter, ["unit", "enumlist"])
            if "description" in parameter:
                row["parameterDescription"] = parameter["description"]
            tables["product_commands_requests"].append(row)

        for parameter in source.get("outParams", []):
            row = {
                "id": 1501 + response_index,
                "serviceId": service_id,
                "commandsId": command_id,
                "parameterCode": parameter["parameterCode"],
                "parameterName": parameter["parameterName"],
                "datatype": parameter["datatype"].upper(),
                "tenantId": tenant_id,
            }
            response_index += 1
            for key in ["min", "max", "step", "required"]:
                if key in parameter:
                    row[key] = _as_db_text(parameter[key])
            _copy_present(row, parameter, ["unit", "enumlist"])
            if "description" in parameter:
                row["parameterDescription"] = parameter["description"]
            tables["product_commands_response"].append(row)

    event_response_index = 0
    for index, source in enumerate(document.get("events", [])):
        event_id = 1601 + index
        event_row = {
            "id": event_id,
            "eventCode": source["eventCode"],
            "eventName": source["eventName"],
            "eventType": source["eventType"],
            "status": source.get("status", "0"),
            "templateIdentification": None,
            "productIdentification": product_identification,
            "tenantId": tenant_id,
        }
        _copy_present(event_row, source, ["description"])
        tables["product_event"].append(event_row)
        for parameter in source.get("outParams", []):
            row = {
                "id": 1701 + event_response_index,
                "eventId": event_id,
                "serviceId": None,
                "parameterName": parameter["parameterName"],
                "datatype": parameter["datatype"].upper(),
                "tenantId": tenant_id,
            }
            event_response_index += 1
            for key in ["min", "max", "step", "required"]:
                if key in parameter:
                    row[key] = _as_db_text(parameter[key])
            _copy_present(row, parameter, ["unit", "enumlist"])
            if "description" in parameter:
                row["parameterDescription"] = parameter["description"]
            tables["product_event_response"].append(row)

    return {"contractVersion": "td005-runtime-projection-v1", "tables": tables}


def _export_parameter(row: dict[str, Any], service: bool) -> dict[str, Any]:
    target: dict[str, Any] = {"id": row["id"]}
    if service:
        target["serviceId"] = row["serviceId"]
        target["parameterCode"] = row["parameterCode"]
        target["parameterName"] = row["parameterName"]
        target["propertyCode"] = row["parameterCode"]
        target["propertyName"] = row["parameterName"]
    else:
        target["eventId"] = row["eventId"]
        target["parameterName"] = row["parameterName"]
    target["datatype"] = row["datatype"]
    for key in ["min", "max", "step", "required"]:
        if key in row:
            target[key] = _as_legacy_number(row[key])
    _copy_present(target, row, ["unit", "enumlist"])
    if "parameterDescription" in row:
        target["description"] = row["parameterDescription"]
        target["parameterDescription"] = row["parameterDescription"]
    if row.get("datatype") == "BOOL" and row.get("enumlist"):
        labels = json.loads(row["enumlist"])
        target["boolClose"] = str(labels.get("0", "关"))
        target["boolOpen"] = str(labels.get("1", "开"))
    return target


def export_from_runtime(runtime: dict[str, Any]) -> dict[str, Any]:
    tables = runtime["tables"]
    product = tables["product"][0]
    result: dict[str, Any] = {
        "schemaVersion": "easyaiot-legacy-thing-model-v1",
        "tenantId": product["tenantId"],
        "productIdentification": product["productIdentification"],
        "properties": [],
        "services": [],
        "events": [],
    }
    for row in tables["product_properties"]:
        item = {
            "id": row["id"],
            "propertyCode": row["propertyCode"],
            "propertyName": row["propertyName"],
            "datatype": row["datatype"],
        }
        for key in ["min", "max"]:
            if key in row:
                item[key] = _as_legacy_number(row[key])
        _copy_present(item, row, ["method", "step", "unit", "required", "description"])
        result["properties"].append(item)

    commands = {row["serviceId"]: row for row in tables["product_commands"]}
    requests: dict[int, list[dict[str, Any]]] = {}
    responses: dict[int, list[dict[str, Any]]] = {}
    for row in tables["product_commands_requests"]:
        requests.setdefault(row["commandsId"], []).append(row)
    for row in tables["product_commands_response"]:
        responses.setdefault(row["commandsId"], []).append(row)
    for row in tables["product_services"]:
        command = commands[row["id"]]
        item = {
            "id": row["id"],
            "serviceCode": row["serviceCode"],
            "serviceName": row["serviceName"],
            "status": row["status"],
        }
        _copy_present(item, row, ["description"])
        item["commandId"] = command["id"]
        item["commandCode"] = command["commandCode"]
        item["inputParams"] = [_export_parameter(value, True) for value in requests.get(command["id"], [])]
        item["outParams"] = [_export_parameter(value, True) for value in responses.get(command["id"], [])]
        result["services"].append(item)

    event_responses: dict[int, list[dict[str, Any]]] = {}
    for row in tables["product_event_response"]:
        event_responses.setdefault(row["eventId"], []).append(row)
    for row in tables["product_event"]:
        item = {
            "id": row["id"],
            "eventCode": row["eventCode"],
            "eventName": row["eventName"],
            "eventType": row["eventType"],
            "status": row["status"],
        }
        _copy_present(item, row, ["description"])
        item["outParams"] = [_export_parameter(value, False) for value in event_responses.get(row["id"], [])]
        result["events"].append(item)
    return result


def _semantic(document: dict[str, Any]) -> dict[str, Any]:
    def parameter(value: dict[str, Any], event: bool = False) -> dict[str, Any]:
        keys = ["parameterName", "datatype", "min", "max", "step", "unit", "required", "enumlist", "description"]
        if not event:
            keys.insert(0, "parameterCode")
        return {key: value[key] for key in keys if key in value}

    return {
        "schemaVersion": document["schemaVersion"],
        "tenantId": document["tenantId"],
        "productIdentification": document["productIdentification"],
        "properties": [
            {key: value[key] for key in [
                "propertyCode", "propertyName", "datatype", "method", "min", "max", "step", "unit",
                "required", "description"
            ] if key in value}
            for value in document.get("properties", [])
        ],
        "services": [
            {
                **{key: value[key] for key in [
                    "serviceCode", "serviceName", "status", "description", "commandCode"
                ] if key in value},
                "inputParams": [parameter(item) for item in value.get("inputParams", [])],
                "outParams": [parameter(item) for item in value.get("outParams", [])],
            }
            for value in document.get("services", [])
        ],
        "events": [
            {
                **{key: value[key] for key in [
                    "eventCode", "eventName", "eventType", "status", "description"
                ] if key in value},
                "outParams": [parameter(item, True) for item in value.get("outParams", [])],
            }
            for value in document.get("events", [])
        ],
    }


def verify_legacy_roundtrip() -> dict[str, Any]:
    fixture = json.loads((CASE_DIR / "legacy-input.json").read_text("utf-8"))
    expected_runtime = json.loads((CASE_DIR / "runtime-rows.golden.json").read_text("utf-8"))
    expected_output = json.loads((CASE_DIR / "legacy-output.golden.json").read_text("utf-8"))
    runtime = import_to_runtime(fixture)
    if runtime != expected_runtime:
        raise AssertionError("Legacy fixture import does not match runtime rows golden")
    output = export_from_runtime(runtime)
    if output != expected_output:
        raise AssertionError("Runtime export does not match legacy output golden")
    if _semantic(fixture) != _semantic(output):
        raise AssertionError("Legacy round-trip changed unapproved business semantics")
    canonical = rfc8785.dumps(output)
    recorded_base64 = (CASE_DIR / "legacy-output.canonical.b64.txt").read_text("ascii").strip()
    if base64.b64decode(recorded_base64) != canonical:
        raise AssertionError("Legacy output canonical bytes differ from golden")
    manifest = json.loads((CASE_DIR / "roundtrip-manifest.json").read_text("utf-8"))
    for artifact in manifest["artifacts"]:
        path = CASE_DIR / artifact["path"]
        if hashlib.sha256(path.read_bytes()).hexdigest() != artifact["sha256"]:
            raise AssertionError(f"Legacy round-trip manifest hash mismatch: {artifact['path']}")
    return {
        "contractVersion": runtime["contractVersion"],
        "legacySchemaVersion": fixture["schemaVersion"],
        "runtimeTables": len(runtime["tables"]),
        "rootProperties": len(runtime["tables"]["product_properties"]),
        "services": len(runtime["tables"]["product_services"]),
        "commands": len(runtime["tables"]["product_commands"]),
        "serviceInputs": len(runtime["tables"]["product_commands_requests"]),
        "serviceOutputs": len(runtime["tables"]["product_commands_response"]),
        "events": len(runtime["tables"]["product_event"]),
        "eventOutputs": len(runtime["tables"]["product_event_response"]),
        "canonicalSha256": hashlib.sha256(canonical).hexdigest(),
        "result": "PASS",
    }


if __name__ == "__main__":
    print(json.dumps(verify_legacy_roundtrip(), ensure_ascii=False))
