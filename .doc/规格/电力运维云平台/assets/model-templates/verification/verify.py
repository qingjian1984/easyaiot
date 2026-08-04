from __future__ import annotations

import argparse
import base64
import copy
import hashlib
import importlib.metadata
import json
import subprocess
import sys
from pathlib import Path
from typing import Any

import rfc8785
from jsonschema import Draft202012Validator


HERE = Path(__file__).resolve().parent
ASSETS = HERE.parent


def pointer_parent(document: Any, pointer: str) -> tuple[Any, str]:
    parts = pointer.removeprefix("/").split("/") if pointer else []
    current = document
    for raw in parts[:-1]:
        token = raw.replace("~1", "/").replace("~0", "~")
        current = current[int(token)] if isinstance(current, list) else current[token]
    last = parts[-1].replace("~1", "/").replace("~0", "~")
    return current, last


def apply_patches(base: Any, patches: list[dict[str, Any]]) -> Any:
    document = copy.deepcopy(base)
    for patch in patches:
        parent, token = pointer_parent(document, patch["path"])
        operation = patch["op"]
        if isinstance(parent, list):
            index = int(token)
            if operation == "add":
                parent.insert(index, copy.deepcopy(patch["value"]))
            elif operation == "replace":
                parent[index] = copy.deepcopy(patch["value"])
            elif operation == "remove":
                parent.pop(index)
            else:
                raise ValueError(f"Unsupported patch operation: {operation}")
        elif operation in {"add", "replace"}:
            parent[token] = copy.deepcopy(patch["value"])
        elif operation == "remove":
            del parent[token]
        else:
            raise ValueError(f"Unsupported patch operation: {operation}")
    return document


def verify_schema() -> tuple[int, int]:
    schema = json.loads((ASSETS / "easyaiot-power-model-template.schema.json").read_text("utf-8"))
    Draft202012Validator.check_schema(schema)
    validator = Draft202012Validator(schema)
    fixtures = json.loads((HERE / "schema-fixtures.json").read_text("utf-8"))
    base_path = (HERE / fixtures["baseArtifact"]).resolve()
    base = json.loads(base_path.read_text("utf-8"))
    positive = negative = 0
    for test_case in fixtures["cases"]:
        instance = apply_patches(base, test_case["patches"])
        errors = sorted(validator.iter_errors(instance), key=lambda error: list(error.absolute_path))
        if test_case["valid"]:
            if errors:
                raise AssertionError(f'{test_case["name"]}: expected valid, got {errors[0].message}')
            positive += 1
        else:
            if not errors:
                raise AssertionError(f'{test_case["name"]}: expected invalid, got valid')
            actual_keywords = {error.validator for error in errors}
            expected_keywords = set(test_case.get("expectKeywords", []))
            if expected_keywords and actual_keywords.isdisjoint(expected_keywords):
                raise AssertionError(
                    f'{test_case["name"]}: expected one of {sorted(expected_keywords)}, got {sorted(actual_keywords)}'
                )
            negative += 1
    return positive, negative


def verify_jcs(node_command: str) -> list[dict[str, Any]]:
    manifest_path = HERE / "jcs-golden.json"
    manifest = json.loads(manifest_path.read_text("utf-8"))
    python_results: list[dict[str, Any]] = []
    for test_case in manifest["cases"]:
        input_path = (HERE / test_case["input"]).resolve()
        value = json.loads(input_path.read_text("utf-8"))
        actual = rfc8785.dumps(value)
        expected = base64.b64decode(test_case["canonicalBase64"])
        actual_hash = hashlib.sha256(actual).hexdigest()
        if actual != expected or actual_hash != test_case["sha256"]:
            raise AssertionError(f'Python JCS golden mismatch: {test_case["name"]}')
        python_results.append({"name": test_case["name"], "sha256": actual_hash, "bytes": len(actual)})

    completed = subprocess.run(
        [node_command, str(HERE / "jcs_canonicalize.mjs"), str(manifest_path)],
        check=True,
        capture_output=True,
        text=True,
        encoding="utf-8",
    )
    node_results = json.loads(completed.stdout)
    if node_results != python_results:
        raise AssertionError("Python and Node JCS results differ")
    return python_results


def verify_manifest() -> int:
    manifest = json.loads((ASSETS / "model-template-assets.manifest.json").read_text("utf-8"))
    for artifact in manifest["artifacts"]:
        path = ASSETS / artifact["path"]
        if path.stat().st_size != artifact["sizeBytes"]:
            raise AssertionError(f"Manifest size mismatch: {path.name}")
        if hashlib.sha256(path.read_bytes()).hexdigest() != artifact["sha256"]:
            raise AssertionError(f"Manifest hash mismatch: {path.name}")
    return len(manifest["artifacts"])


def verify_target_profile() -> str:
    schema = json.loads((HERE / "target-schema-profile-result.schema.json").read_text("utf-8"))
    result = json.loads((HERE / "target-schema-profile-result.json").read_text("utf-8"))
    Draft202012Validator.check_schema(schema)
    errors = sorted(
        Draft202012Validator(schema, format_checker=Draft202012Validator.FORMAT_CHECKER).iter_errors(result),
        key=lambda error: list(error.absolute_path),
    )
    if errors:
        path = "/".join(str(token) for token in errors[0].absolute_path)
        raise AssertionError(f"Target profile schema mismatch at {path}: {errors[0].message}")

    table_names = set(result["tables"])
    table_facts = result["schemaFacts"]["tables"]
    if table_names != set(table_facts):
        raise AssertionError("Target profile table list and schemaFacts.tables differ")
    for table_name, facts in table_facts.items():
        signatures = facts["columnSignatures"]
        if facts["columnCount"] != len(signatures):
            raise AssertionError(f"Target profile column count mismatch: {table_name}")
        tenant_signature = "tenant_id|bigint|NOT_NULL"
        if facts["hasTenantId"] != (tenant_signature in signatures):
            raise AssertionError(f"Target profile tenant flag mismatch: {table_name}")
        if facts["tenantNotNull"] != (tenant_signature in signatures):
            raise AssertionError(f"Target profile tenant nullability mismatch: {table_name}")
    return result["resultSchemaVersion"]


def verify_utf8_no_bom() -> int:
    text_suffixes = {".json", ".md", ".mjs", ".py", ".sql", ".txt"}
    paths = [path for path in ASSETS.rglob("*") if path.is_file() and path.suffix.lower() in text_suffixes]
    for path in paths:
        content = path.read_bytes()
        if content.startswith(b"\xef\xbb\xbf"):
            raise AssertionError(f"UTF-8 BOM is not allowed: {path.relative_to(ASSETS)}")
        try:
            content.decode("utf-8", errors="strict")
        except UnicodeDecodeError as error:
            raise AssertionError(f"Invalid UTF-8: {path.relative_to(ASSETS)}") from error
    return len(paths)


def main() -> int:
    parser = argparse.ArgumentParser(description="Verify TD-005 model-template review assets")
    parser.add_argument("--node", default="node", help="Node.js executable")
    args = parser.parse_args()
    positive, negative = verify_schema()
    golden = verify_jcs(args.node)
    artifacts = verify_manifest()
    target_profile_schema = verify_target_profile()
    utf8_files = verify_utf8_no_bom()
    summary = {
        "draft": "2020-12",
        "validator": f'python-jsonschema-{importlib.metadata.version("jsonschema")}',
        "schemaPositive": positive,
        "schemaNegative": negative,
        "jcsImplementations": [
            f'python-rfc8785-{importlib.metadata.version("rfc8785")}',
            "node-ecmascript",
        ],
        "jcsCases": golden,
        "manifestArtifacts": artifacts,
        "targetProfileSchema": target_profile_schema,
        "utf8NoBomFiles": utf8_files,
        "result": "PASS",
    }
    recorded = json.loads((HERE / "verification-result.json").read_text("utf-8"))
    for key, value in summary.items():
        if recorded.get(key) != value:
            raise AssertionError(f"Recorded verification result is stale at key: {key}")
    print(json.dumps(summary, ensure_ascii=False))
    return 0


if __name__ == "__main__":
    sys.exit(main())
