"""Parse raw psql output of target-schema-profile.sql v1.2.0 into the
result JSON contract (schema 1.1.0), validate it, and diff against the
2026-08-05 baseline. Read-only evidence pipeline: no database writes.
"""
import json
import sys
from pathlib import Path

import jsonschema

HERE = Path(__file__).resolve().parent
RAW = HERE / "target-schema-profile-raw-2026-08-07.txt"
BASELINE = HERE / "target-schema-profile-result.json"
SCHEMA = HERE / "target-schema-profile-result.schema.json"
OUT = HERE / "target-schema-profile-result-2026-08-07.json"

TABLES = [
    "product", "product_properties", "product_event", "product_event_response",
    "product_services", "product_commands", "product_commands_requests",
    "product_commands_response", "product_script", "device",
    "device_service_invoke_response", "ota_packages",
]

DUP_KEY_MAP = {
    "product_identification": "productIdentification",
    "property_code_product_scope": "propertyCodeProductScope",
    "property_code_template_scope": "propertyCodeTemplateScope",
    "event_code_product_scope": "eventCodeProductScope",
    "event_code_template_scope": "eventCodeTemplateScope",
    "service_code_product_scope": "serviceCodeProductScope",
    "service_code_template_scope": "serviceCodeTemplateScope",
}
SCOPE_KEY_MAP = {
    "properties_both_null": "propertiesBothNull",
    "properties_both_set": "propertiesBothSet",
    "events_both_null": "eventsBothNull",
    "events_both_set": "eventsBothSet",
    "services_both_null": "servicesBothNull",
    "services_both_set": "servicesBothSet",
}
ORPHAN_KEY_MAP = {
    "properties_without_product": "propertiesWithoutProduct",
    "events_without_product": "eventsWithoutProduct",
    "services_without_product": "servicesWithoutProduct",
    "commands_without_service": "commandsWithoutService",
    "requests_without_command": "requestsWithoutCommand",
    "responses_without_command": "responsesWithoutCommand",
    "event_responses_without_event": "eventResponsesWithoutEvent",
    "event_response_services_without_service": "eventResponseServicesWithoutService",
    "scripts_without_exact_product": "scriptsWithoutExactProduct",
    "devices_without_product": "devicesWithoutProduct",
    "invoke_responses_without_device": "invokeResponsesWithoutDevice",
    "invoke_responses_without_product": "invokeResponsesWithoutProduct",
    "ota_packages_without_product": "otaPackagesWithoutProduct",
}
ANOMALY_KEY_MAP = {
    "request_service_mismatch": "requestServiceMismatch",
    "response_service_mismatch": "responseServiceMismatch",
    "response_service_id_null": "responseServiceIdNull",
    "invoke_device_identity_mismatch": "invokeDeviceIdentityMismatch",
    "ota_tenant_id_null": "otaTenantIdNull",
}


def fail(msg: str) -> None:
    print(f"FAIL: {msg}")
    sys.exit(1)


def main() -> None:
    raw_lines = RAW.read_text(encoding="utf-8").splitlines()
    # Split on the first separator only: TABLE_*_JSON payloads contain '|'
    # inside column signatures, so a naive split('|') corrupts them.
    rows = []
    for line in raw_lines:
        label, sep, rest = line.partition("|")
        if sep:
            rows.append((label, rest))
    if not any(line == "ROLLBACK" for line in raw_lines):
        fail("raw output missing ROLLBACK terminator; profile may be incomplete")

    profile_version = None
    env = {}
    table_roles = {"coreRuntime": [], "protectedDependency": []}
    table_schema_json = None
    table_contract_json = None
    row_counts = {}
    duplicates = {}
    scopes = {}
    orphans = {}
    anomalies = {}
    flags = {}
    orphan_property_rows = []

    for label, rest in rows:
        parts = [label] + rest.split("|")
        if label == "TABLE_SCHEMA_JSON":
            table_schema_json = json.loads(rest)
            continue
        if label == "TABLE_CONTRACT_JSON":
            table_contract_json = json.loads(rest)
            continue
        if label == "PROFILE_VERSION":
            profile_version = parts[1]
        elif label == "ENV":
            env = {"database": parts[1], "user": parts[2],
                   "version": parts[3], "readOnly": parts[4]}
        elif label == "TABLE_ROLE":
            role = "coreRuntime" if parts[2] == "CORE_RUNTIME" else "protectedDependency"
            table_roles[role].append(parts[1])
        elif label == "ROW_COUNT":
            row_counts[parts[1]] = int(parts[2])
        elif label == "DUPLICATE_GROUPS":
            duplicates[DUP_KEY_MAP[parts[1]]] = int(parts[2])
        elif label == "IDENTIFIER_SCOPE":
            scopes[SCOPE_KEY_MAP[parts[1]]] = int(parts[2])
        elif label == "ORPHAN_COUNT":
            orphans[ORPHAN_KEY_MAP[parts[1]]] = int(parts[2])
        elif label == "RELATIONSHIP_ANOMALY":
            anomalies[ANOMALY_KEY_MAP[parts[1]]] = int(parts[2])
        elif label == "PROFILE_FLAG":
            flags[parts[1]] = parts[2]
        elif label == "ORPHAN_PROPERTY":
            orphan_property_rows.append(parts[1:])

    if profile_version != "1.2.0":
        fail(f"unexpected profile version {profile_version}")
    if table_schema_json is None or table_contract_json is None:
        fail("missing TABLE_SCHEMA_JSON or TABLE_CONTRACT_JSON")

    schema_tables = {}
    for name in TABLES:
        if name not in table_schema_json or name not in table_contract_json:
            fail(f"table {name} missing from profile output")
        merged = dict(table_schema_json[name])
        merged.update({k: int(v) for k, v in table_contract_json[name].items()})
        merged["columnCount"] = int(merged["columnCount"])
        schema_tables[name] = merged

    baseline = json.loads(BASELINE.read_text(encoding="utf-8"))

    result = {
        "profileVersion": "1.2.0",
        "resultSchemaVersion": "1.1.0",
        "verifiedDate": "2026-08-07",
        "scope": baseline["scope"],
        "container": baseline["container"],
        "database": env["database"],
        "databaseUser": env["user"],
        "postgresVersion": env["version"],
        "transactionReadOnly": env["readOnly"] == "on",
        "tables": TABLES,
        "tableRoles": {
            # Keep canonical TABLES order (not alphabetical) for baseline comparability.
            "coreRuntime": [t for t in TABLES if t in table_roles["coreRuntime"]],
            "protectedDependency": [t for t in TABLES if t in table_roles["protectedDependency"]],
        },
        "rowCounts": {name: row_counts[name] for name in TABLES},
        "schemaFacts": {
            "productPropertiesHasServiceId": flags["product_properties_has_service_id"] == "true",
            "allProfiledTablesHaveTenantId": flags["all_profiled_tables_have_tenant_id"] == "true",
            "allProfiledTenantIdsNotNull": flags["all_profiled_tenant_ids_not_null"] == "true",
            "primaryKeyCount": int(flags["primary_key_count"]),
            "businessUniqueConstraintCount": int(flags["business_unique_constraint_count"]),
            "foreignKeyCount": int(flags["foreign_key_count"]),
            "checkConstraintCount": int(flags["check_constraint_count"]),
            "triggerCount": int(flags["trigger_count"]),
            "indexCount": int(flags["index_count"]),
            "tables": schema_tables,
        },
        "duplicateGroups": duplicates,
        "identifierScopeAnomalies": scopes,
        "orphanCounts": orphans,
        "relationshipAnomalies": anomalies,
        "orphanPropertyGroups": [
            {"tenantId": r[0], "id": r[1], "productIdentification": r[2],
             "templateIdentification": r[3], "propertyCode": r[4]}
            for r in orphan_property_rows
        ],
        "repositoryDrift": baseline["repositoryDrift"],
        "comparisonContract": baseline["comparisonContract"],
        "gateDecision": baseline["gateDecision"],
        "result": baseline["result"],
    }

    schema = json.loads(SCHEMA.read_text(encoding="utf-8"))
    jsonschema.validate(result, schema)
    print("schema validation: PASS (1.1.0)")

    OUT.write_text(json.dumps(result, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")

    # ---- diff vs baseline (excluding run-identity fields) ----
    SKIP = {"verifiedDate"}
    diffs = []

    def walk(path, a, b):
        if path and path[-1] in SKIP:
            return
        if isinstance(a, dict) and isinstance(b, dict):
            for key in sorted(set(a) | set(b)):
                walk(path + [key], a.get(key, "<ABSENT>"), b.get(key, "<ABSENT>"))
        elif a != b:
            diffs.append(("/".join(path), a, b))

    walk([], baseline, result)
    if diffs:
        print(f"baseline diff: {len(diffs)} difference(s)")
        for path, old, new in diffs:
            print(f"  {path}: {old!r} -> {new!r}")
    else:
        print("baseline diff: IDENTICAL (excluding verifiedDate)")

    # ---- gate conditions from comparisonContract ----
    blockers = []
    if any(v != 0 for v in duplicates.values()):
        blockers.append("duplicate group count > 0")
    if any(v != 0 for v in scopes.values()):
        blockers.append("identifier scope anomaly > 0")
    if any(v != 0 for v in orphans.values()):
        blockers.append("orphan count > 0")
    mismatch_keys = {"requestServiceMismatch", "responseServiceMismatch", "invokeDeviceIdentityMismatch"}
    if any(anomalies[k] != 0 for k in mismatch_keys):
        blockers.append("relationship mismatch > 0")
    if result["schemaFacts"]["productPropertiesHasServiceId"]:
        blockers.append("service_id shape conflict")
    if not result["schemaFacts"]["allProfiledTablesHaveTenantId"]:
        blockers.append("tenant_id missing on a profiled table")
    print("blocking conditions:", "NONE triggered" if not blockers else blockers)
    print("gate remains: OPEN_REMEDIATION_REQUIRED (unique/FK/trigger baseline not yet built)")
    print(f"result written: {OUT.name}")


if __name__ == "__main__":
    main()
