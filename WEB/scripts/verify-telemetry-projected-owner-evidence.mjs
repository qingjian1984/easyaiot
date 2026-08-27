#!/usr/bin/env node
/**
 * PRD-02 C1P-G2-02 TELEMETRY_PROJECTED Owner evidence 本地门禁。
 *
 * 仅读取仓库内 evidence 与其引用的 artifact；不会访问网络、数据库、broker、
 * Docker 或业务运行时，也不会修改被验证文件。
 */
import Ajv2020 from "ajv/dist/2020.js";
import addFormats from "ajv-formats";
import crypto from "node:crypto";
import fs from "node:fs";
import os from "node:os";
import path from "node:path";
import { fileURLToPath } from "node:url";

const SCRIPT_DIR = path.dirname(fileURLToPath(import.meta.url));
const REPO_ROOT = path.resolve(SCRIPT_DIR, "..", "..");
const ASSET_ROOT = path.join(REPO_ROOT, ".doc", "技术设计", "电力运维云平台", "assets", "c1p-g2");
const EVIDENCE_SCHEMA_PATH = path.join(
  ASSET_ROOT,
  "telemetry-projected-owner-evidence-v1.schema.json",
);
const DEFAULT_EVIDENCE_PATH = path.join(
  ASSET_ROOT,
  "telemetry-projected-owner-evidence-template.json",
);
const HASH_PATTERN = /^sha256:[0-9a-f]{64}$/;
const VERSION_HEADER_PATTERN = /^> 版本：([0-9]+\.[0-9]+\.[0-9]+)\s*$/m;
const REQUIRED_SOURCE = "iot-sink.telemetry-projector";
const TD_DOCUMENT_PATH = ".doc/技术设计/电力运维云平台/TD-003-遥测Inbox-ACK与时序投影.md";

function sha256Bytes(value) {
  return `sha256:${crypto.createHash("sha256").update(value).digest("hex")}`;
}

function sha256File(file) {
  return sha256Bytes(fs.readFileSync(file));
}

function canonicalize(value) {
  if (value === null || typeof value === "boolean" || typeof value === "string") {
    return JSON.stringify(value);
  }
  if (typeof value === "number") {
    if (!Number.isFinite(value)) throw new TypeError("JCS_NUMBER_NON_FINITE");
    return JSON.stringify(value);
  }
  if (Array.isArray(value)) return `[${value.map(canonicalize).join(",")}]`;
  if (typeof value === "object") {
    return `{${Object.keys(value)
      .sort()
      .map((key) => `${JSON.stringify(key)}:${canonicalize(value[key])}`)
      .join(",")}}`;
  }
  throw new TypeError("JCS_VALUE_UNSUPPORTED");
}

function approvalContentSha256(evidence) {
  const value = structuredClone(evidence);
  if (value.approval && typeof value.approval === "object") {
    delete value.approval.contentSha256;
  }
  return sha256Bytes(Buffer.from(canonicalize(value), "utf8"));
}

function isSafeRepositoryPath(value, { jsonOnly = false } = {}) {
  if (
    typeof value !== "string" ||
    value.length === 0 ||
    value.length > 512 ||
    /[\u0000-\u001f]/.test(value) ||
    value.includes("\\") ||
    value.startsWith("/") ||
    /^[A-Za-z]:/.test(value) ||
    value.includes(":") ||
    value.includes("//") ||
    (jsonOnly && !value.toLowerCase().endsWith(".json"))
  ) {
    return false;
  }
  const segments = value.split("/");
  return (
    segments.every((segment) => segment !== "" && segment !== "." && segment !== "..") &&
    !segments.includes(".git") &&
    !segments.includes("node_modules") &&
    !segments.some((segment) => /^\.env(?:\.|$)/i.test(segment))
  );
}

function isInside(root, target) {
  const relative = path.relative(root, target);
  return (
    relative !== "" &&
    relative !== ".." &&
    !relative.startsWith(`..${path.sep}`) &&
    !path.isAbsolute(relative)
  );
}

function readJson(file) {
  return JSON.parse(fs.readFileSync(file, "utf8"));
}

function createEvidenceValidator() {
  const ajv = new Ajv2020({ strict: true, allErrors: true });
  addFormats(ajv);
  return ajv.compile(readJson(EVIDENCE_SCHEMA_PATH));
}

function issue(issues, code, target = "") {
  issues.push({ code, target });
}

function expect(issues, condition, code, target = "") {
  if (!condition) issue(issues, code, target);
}

function resolveExistingRepositoryFile(
  root,
  relativePath,
  label,
  issues,
  { jsonOnly = false } = {},
) {
  if (!isSafeRepositoryPath(relativePath, { jsonOnly })) {
    issue(issues, "ARTIFACT_PATH_INVALID", `${label}:${relativePath ?? ""}`);
    return null;
  }
  const full = path.resolve(root, ...relativePath.split("/"));
  if (!isInside(root, full)) {
    issue(issues, "ARTIFACT_PATH_OUTSIDE_REPOSITORY", `${label}:${relativePath}`);
    return null;
  }
  if (!fs.existsSync(full) || !fs.statSync(full).isFile()) {
    issue(issues, "ARTIFACT_PATH_MISSING", `${label}:${relativePath}`);
    return null;
  }
  const realRoot = fs.realpathSync(root);
  const realFile = fs.realpathSync(full);
  if (!isInside(realRoot, realFile)) {
    issue(issues, "ARTIFACT_PATH_OUTSIDE_REPOSITORY", `${label}:${relativePath}`);
    return null;
  }
  return realFile;
}

function validateArtifact(artifact, label, root, issues, options = {}) {
  if (!artifact || typeof artifact !== "object") {
    issue(issues, "ARTIFACT_INVALID", label);
    return null;
  }
  const file = resolveExistingRepositoryFile(root, artifact.path, label, issues, options);
  if (!file) return null;
  if (!HASH_PATTERN.test(artifact.sha256 ?? "") || sha256File(file) !== artifact.sha256) {
    issue(issues, "ARTIFACT_SHA256_MISMATCH", `${label}:${artifact.path}`);
    return null;
  }
  return file;
}

function semverParts(value) {
  const match = /^([0-9]+)\.([0-9]+)\.([0-9]+)$/.exec(value ?? "");
  return match ? match.slice(1).map(Number) : null;
}

function compareSemver(left, right) {
  const a = semverParts(left);
  const b = semverParts(right);
  if (!a || !b) return null;
  for (let index = 0; index < 3; index += 1) {
    if (a[index] !== b[index]) return a[index] < b[index] ? -1 : 1;
  }
  return 0;
}

function validateTdDocument(file, artifact, issues) {
  if (!file) return;
  expect(issues, artifact.path === TD_DOCUMENT_PATH, "TD_DOCUMENT_PATH_INVALID", artifact.path);
  const text = fs.readFileSync(file, "utf8");
  const match = VERSION_HEADER_PATTERN.exec(text);
  if (!match) {
    issue(issues, "TD_VERSION_HEADER_MISSING", artifact.path);
    return;
  }
  expect(
    issues,
    match[1] === artifact.documentVersion,
    "TD_VERSION_MISMATCH",
    `${artifact.path}:${match[1]}!=${artifact.documentVersion}`,
  );
  expect(
    issues,
    compareSemver(artifact.documentVersion, "1.0.3") > 0,
    "TD_VERSION_NOT_NEWER_THAN_OWNER_BASELINE",
    `${artifact.path}:${artifact.documentVersion}`,
  );
}

function walkSchema(node, visitor) {
  if (!node || typeof node !== "object") return;
  visitor(node);
  if (Array.isArray(node)) {
    for (const item of node) walkSchema(item, visitor);
    return;
  }
  for (const value of Object.values(node)) walkSchema(value, visitor);
}

function validateProductionSchema(file, artifact, evidence, issues) {
  if (!file) return;
  let schema;
  try {
    schema = readJson(file);
  } catch {
    issue(issues, "PRODUCTION_SCHEMA_JSON_INVALID", artifact.path);
    return;
  }
  try {
    const ajv = new Ajv2020({ strict: true, allErrors: true });
    addFormats(ajv);
    ajv.compile(schema);
  } catch (error) {
    issue(issues, "PRODUCTION_SCHEMA_STRICT_COMPILE_FAILED", error.message);
    return;
  }

  expect(
    issues,
    artifact.eventType === evidence.eventVersionDecision.targetEventType,
    "ARTIFACT_EVENT_TYPE_MISMATCH",
    artifact.path,
  );
  expect(
    issues,
    artifact.schemaVersion === evidence.eventVersionDecision.targetSchemaVersion,
    "ARTIFACT_SCHEMA_VERSION_MISMATCH",
    artifact.path,
  );

  const fieldFacts = new Map(
    evidence.requiredFields.map((field) => [field, { property: false, required: false }]),
  );
  const constants = new Map([
    ["eventType", []],
    ["eventVersion", []],
    ["source", []],
  ]);
  let openRelevantObjects = 0;
  walkSchema(schema, (node) => {
    if (!node.properties || typeof node.properties !== "object" || Array.isArray(node.properties)) {
      return;
    }
    const required = new Set(Array.isArray(node.required) ? node.required : []);
    let relevant = 0;
    for (const [field, facts] of fieldFacts) {
      if (Object.hasOwn(node.properties, field)) {
        relevant += 1;
        facts.property = true;
        if (required.has(field)) facts.required = true;
      }
    }
    if (relevant > 0 && node.additionalProperties !== false) openRelevantObjects += 1;
    for (const [name, values] of constants) {
      const property = node.properties[name];
      if (property && typeof property === "object" && Object.hasOwn(property, "const")) {
        values.push(property.const);
      }
    }
  });

  for (const [field, facts] of fieldFacts) {
    if (!facts.property) issue(issues, "PRODUCTION_SCHEMA_FIELD_MISSING", field);
    else if (!facts.required) issue(issues, "PRODUCTION_SCHEMA_FIELD_NOT_REQUIRED", field);
  }
  expect(
    issues,
    openRelevantObjects === 0,
    "PRODUCTION_SCHEMA_ADDITIONAL_PROPERTIES_OPEN",
    artifact.path,
  );
  const expectedConstants = {
    eventType: evidence.eventVersionDecision.targetEventType,
    eventVersion: evidence.eventVersionDecision.targetSchemaVersion,
    source: REQUIRED_SOURCE,
  };
  for (const [name, expected] of Object.entries(expectedConstants)) {
    const values = constants.get(name);
    if (values.length !== 1) {
      issue(issues, "PRODUCTION_SCHEMA_CONST_NOT_UNIQUE", `${name}:${values.length}`);
    } else if (values[0] !== expected) {
      issue(issues, "PRODUCTION_SCHEMA_CONST_MISMATCH", `${name}:${values[0]}!=${expected}`);
    }
  }
}

function containsToken(text, token) {
  const escaped = token.replace(/[.*+?^${}()|[\]\\]/g, "\\$&");
  return new RegExp(`(?:^|[^A-Z0-9_])${escaped}(?:$|[^A-Z0-9_])`, "m").test(text);
}

function validateTestPlan(file, evidence, issues) {
  if (!file) return;
  const text = fs.readFileSync(file, "utf8");
  for (const area of evidence.testCoverage) {
    expect(issues, containsToken(text, area), "TEST_PLAN_AREA_MISSING", area);
  }
}

function validatePublicationHistory(evidence, consumerFile, issues) {
  const environments = evidence.publicationHistory.environments;
  const ids = environments.map((environment) => environment.environmentId);
  expect(issues, new Set(ids).size === ids.length, "PUBLICATION_ENVIRONMENT_ID_DUPLICATE");
  let latest = Number.NEGATIVE_INFINITY;
  for (const environment of environments) {
    const first = Date.parse(environment.firstPublishedAt);
    const last = Date.parse(environment.latestPublishedAt);
    expect(
      issues,
      Number.isFinite(first) && Number.isFinite(last) && first <= last,
      "PUBLICATION_TIME_ORDER_INVALID",
      environment.environmentId,
    );
    if (Number.isFinite(last)) latest = Math.max(latest, last);
  }
  if (evidence.approval && Number.isFinite(latest)) {
    expect(
      issues,
      Date.parse(evidence.approval.approvedAt) >= latest,
      "APPROVAL_BEFORE_LATEST_PUBLICATION",
    );
  }
  if (consumerFile) {
    const inventory = fs.readFileSync(consumerFile, "utf8");
    for (const environment of environments) {
      expect(
        issues,
        inventory.includes(environment.environmentId),
        "CONSUMER_INVENTORY_ENVIRONMENT_MISSING",
        environment.environmentId,
      );
      for (const consumer of environment.currentConsumers) {
        expect(
          issues,
          inventory.includes(consumer),
          "CONSUMER_INVENTORY_CONSUMER_MISSING",
          `${environment.environmentId}:${consumer}`,
        );
      }
    }
  }
}

function validateEvidence(evidence, root, validateSchema) {
  const issues = [];
  if (!validateSchema(evidence)) {
    issue(issues, "EVIDENCE_SCHEMA_INVALID", validateSchema.errors?.[0]?.instancePath ?? "");
    return { issues, qualification: "INVALID" };
  }

  const files = {};
  for (const [key, artifact] of Object.entries(evidence.artifacts)) {
    if (artifact !== null) {
      files[key] = validateArtifact(artifact, key, root, issues, {
        jsonOnly: key === "productionSchema",
      });
    }
  }
  const migrationArtifact = evidence.eventVersionDecision.migrationPlan;
  if (migrationArtifact !== null) {
    files.migrationPlan = validateArtifact(migrationArtifact, "migrationPlan", root, issues);
  }

  if (evidence.approval !== null) {
    const decisionRef = resolveExistingRepositoryFile(
      root,
      evidence.approval.decisionRef,
      "decisionRef",
      issues,
    );
    if (!decisionRef) issue(issues, "DECISION_REF_INVALID", evidence.approval.decisionRef);
    let actualHash;
    try {
      actualHash = approvalContentSha256(evidence);
    } catch {
      issue(issues, "APPROVAL_CANONICALIZATION_FAILED");
    }
    if (actualHash && actualHash !== evidence.approval.contentSha256) {
      issue(issues, "APPROVAL_CONTENT_SHA256_MISMATCH");
    }
  }

  if (evidence.artifacts.tdDocument !== null) {
    validateTdDocument(files.tdDocument, evidence.artifacts.tdDocument, issues);
  }
  if (evidence.artifacts.productionSchema !== null) {
    validateProductionSchema(
      files.productionSchema,
      evidence.artifacts.productionSchema,
      evidence,
      issues,
    );
  }
  if (evidence.artifacts.testPlan !== null) {
    validateTestPlan(files.testPlan, evidence, issues);
  }
  validatePublicationHistory(evidence, files.consumerInventory, issues);

  let qualification = "DRAFT_PREFLIGHT";
  if (
    evidence.documentStatus === "DRAFT" &&
    evidence.publicationHistory.status === "UNCONFIRMED" &&
    evidence.eventVersionDecision.decision === "UNDECIDED"
  ) {
    qualification = "DRAFT_UNCONFIRMED";
  } else if (evidence.documentStatus === "APPROVED") {
    qualification =
      evidence.eventVersionDecision.decision === "CREATE_V2"
        ? "APPROVED_V2_READY_FOR_SOL_REVIEW"
        : "APPROVED_V1_READY_FOR_SOL_REVIEW";
  }
  return { issues, qualification };
}

function parseArgs(args) {
  let file = null;
  let selfTest = false;
  let separator = false;
  for (let index = 0; index < args.length; index += 1) {
    const arg = args[index];
    if (arg === "--") {
      if (separator) return { error: "ARGUMENT_DUPLICATE", value: arg };
      separator = true;
    } else if (arg === "--self-test") {
      if (selfTest) return { error: "ARGUMENT_DUPLICATE", value: arg };
      selfTest = true;
    } else if (arg === "--file") {
      if (file !== null) return { error: "ARGUMENT_DUPLICATE", value: arg };
      if (index + 1 >= args.length || args[index + 1].startsWith("--")) {
        return { error: "ARGUMENT_VALUE_MISSING", value: arg };
      }
      file = args[index + 1];
      index += 1;
    } else {
      return { error: "ARGUMENT_INVALID", value: arg };
    }
  }
  return { file, selfTest };
}

function resolveEvidencePath(value) {
  if (value === null) return DEFAULT_EVIDENCE_PATH;
  if (!isSafeRepositoryPath(value, { jsonOnly: true })) return null;
  const full = path.resolve(REPO_ROOT, ...value.split("/"));
  return isInside(REPO_ROOT, full) ? full : null;
}

function writeText(root, relative, text) {
  const file = path.join(root, ...relative.split("/"));
  fs.mkdirSync(path.dirname(file), { recursive: true });
  fs.writeFileSync(file, text, "utf8");
  return file;
}

function artifactFor(root, relative, extra = {}) {
  return { path: relative, sha256: sha256File(path.join(root, ...relative.split("/"))), ...extra };
}

function productionSchema(eventType, version, requiredFields, overrides = {}) {
  const properties = Object.fromEntries(
    requiredFields.map((field) => [field, { type: field === "sequence" ? "integer" : "string" }]),
  );
  properties.eventType = { const: eventType };
  properties.eventVersion = { const: version };
  properties.source = { const: REQUIRED_SOURCE };
  return {
    $schema: "https://json-schema.org/draft/2020-12/schema",
    $id: `https://easyaiot.local/self-test/${eventType.toLowerCase()}/${version}`,
    type: "object",
    additionalProperties: false,
    required: [...requiredFields],
    properties,
    ...overrides,
  };
}

const REQUIRED_FIELDS = [
  "eventId",
  "eventType",
  "eventVersion",
  "occurredAt",
  "source",
  "eventContentSha256",
  "messageId",
  "contentSha256",
  "tenantId",
  "workloadId",
  "productIdentification",
  "siteCode",
  "deviceIdentification",
  "propertyCode",
  "value",
  "valueEncoding",
  "quality",
  "dataPriority",
  "collectedAt",
  "originalOffset",
  "sequence",
  "configVersion",
  "storeResult",
];
const TEST_AREAS = [
  "SCHEMA",
  "IDENTITY_COLLISION",
  "ROUTE",
  "VALUE_QUALITY",
  "TRANSACTION_ROLLBACK",
  "RELAY_RECOVERY",
  "CAPABILITY_ISOLATION",
];

function finalizeApproval(evidence) {
  evidence.approval.contentSha256 = approvalContentSha256(evidence);
}

function buildApprovedEvidence(root, version = 1) {
  const isV2 = version === 2;
  const eventType = isV2 ? "TELEMETRY_PROJECTED_V2" : "TELEMETRY_PROJECTED_V1";
  const schemaVersion = isV2 ? "2.0" : "1.0";
  const schemaRelative = `DEVICE/iot-sink/iot-sink-api/src/main/resources/events/telemetry.projected/v${version}.json`;
  const tdRelative = TD_DOCUMENT_PATH;
  const testRelative = ".doc/技术设计/电力运维云平台/G2-02-test-plan.md";
  const decisionRelative = ".doc/技术设计/电力运维云平台/G2-02-signoff.md";
  writeText(root, tdRelative, "# TD-003\n\n> 版本：1.0.4\n> 状态：In Review\n");
  writeText(root, testRelative, TEST_AREAS.join("\n"));
  writeText(root, decisionRelative, "# G2-02 owner signoff\n");
  writeText(
    root,
    schemaRelative,
    `${JSON.stringify(productionSchema(eventType, schemaVersion, REQUIRED_FIELDS), null, 2)}\n`,
  );

  const evidence = {
    schemaVersion: "1.0",
    canonicalizationVersion: "jcs-rfc8785-v1",
    documentStatus: "APPROVED",
    revision: 1,
    publicationHistory: isV2
      ? {
          status: "PUBLISHED",
          environments: [
            {
              environmentId: "prod-a",
              firstPublishedAt: "2026-01-01T00:00:00Z",
              latestPublishedAt: "2026-08-01T00:00:00Z",
              publishedEventType: "TELEMETRY_PROJECTED_V1",
              publishedSchemaVersion: "1.0",
              currentConsumers: ["iot-device"],
            },
          ],
        }
      : { status: "NEVER_PUBLISHED", environments: [] },
    eventVersionDecision: {
      decision: isV2 ? "CREATE_V2" : "EXTEND_V1_BEFORE_FIRST_PUBLISH",
      targetEventType: eventType,
      targetSchemaVersion: schemaVersion,
      migrationPlan: null,
    },
    artifacts: {
      tdDocument: artifactFor(root, tdRelative, { documentVersion: "1.0.4" }),
      productionSchema: artifactFor(root, schemaRelative, { eventType, schemaVersion }),
      testPlan: artifactFor(root, testRelative),
      consumerInventory: null,
    },
    requiredFields: [...REQUIRED_FIELDS],
    testCoverage: [...TEST_AREAS],
    approval: {
      ownerRole: "M1/TD-003 owner",
      approvedBy: "self-test",
      approvedAt: "2026-08-27T12:00:00+08:00",
      decision: "APPROVE_RECOMMENDATION",
      decisionRef: decisionRelative,
      contentSha256: `sha256:${"0".repeat(64)}`,
    },
  };
  if (isV2) {
    const consumerRelative = ".doc/技术设计/电力运维云平台/G2-02-consumers.md";
    const migrationRelative = ".doc/技术设计/电力运维云平台/G2-02-v2-migration.md";
    writeText(root, consumerRelative, "prod-a\niot-device\n");
    writeText(root, migrationRelative, "# v2 migration\n双发\n对账\n回滚\n");
    evidence.artifacts.consumerInventory = artifactFor(root, consumerRelative);
    evidence.eventVersionDecision.migrationPlan = artifactFor(root, migrationRelative);
  }
  finalizeApproval(evidence);
  return evidence;
}

function rewriteArtifact(root, artifact, text) {
  writeText(root, artifact.path, text);
  artifact.sha256 = sha256File(path.join(root, ...artifact.path.split("/")));
}

function rewriteProductionSchema(root, evidence, mutate) {
  const artifact = evidence.artifacts.productionSchema;
  const file = path.join(root, ...artifact.path.split("/"));
  const schema = readJson(file);
  mutate(schema);
  rewriteArtifact(root, artifact, `${JSON.stringify(schema, null, 2)}\n`);
  finalizeApproval(evidence);
}

function runSelfTests(validateSchema) {
  const cases = [
    [
      "draft",
      null,
      () => ({
        schemaVersion: "1.0",
        canonicalizationVersion: "jcs-rfc8785-v1",
        documentStatus: "DRAFT",
        revision: 1,
        publicationHistory: { status: "UNCONFIRMED", environments: [] },
        eventVersionDecision: {
          decision: "UNDECIDED",
          targetEventType: null,
          targetSchemaVersion: null,
          migrationPlan: null,
        },
        artifacts: {
          tdDocument: null,
          productionSchema: null,
          testPlan: null,
          consumerInventory: null,
        },
        requiredFields: [],
        testCoverage: [],
        approval: null,
      }),
      "DRAFT_UNCONFIRMED",
    ],
    [
      "approved-v1",
      null,
      (root) => buildApprovedEvidence(root, 1),
      "APPROVED_V1_READY_FOR_SOL_REVIEW",
    ],
    [
      "approved-v2",
      null,
      (root) => buildApprovedEvidence(root, 2),
      "APPROVED_V2_READY_FOR_SOL_REVIEW",
    ],
    [
      "schema-invalid",
      "EVIDENCE_SCHEMA_INVALID",
      (root) => {
        const value = buildApprovedEvidence(root, 1);
        delete value.revision;
        return value;
      },
    ],
    [
      "artifact-missing",
      "ARTIFACT_PATH_MISSING",
      (root) => {
        const value = buildApprovedEvidence(root, 1);
        value.artifacts.testPlan.path = ".doc/技术设计/电力运维云平台/missing.md";
        finalizeApproval(value);
        return value;
      },
    ],
    [
      "artifact-tamper",
      "ARTIFACT_SHA256_MISMATCH",
      (root) => {
        const value = buildApprovedEvidence(root, 1);
        writeText(root, value.artifacts.testPlan.path, "tampered");
        return value;
      },
    ],
    [
      "approval-hash",
      "APPROVAL_CONTENT_SHA256_MISMATCH",
      (root) => {
        const value = buildApprovedEvidence(root, 1);
        value.approval.contentSha256 = `sha256:${"f".repeat(64)}`;
        return value;
      },
    ],
    [
      "decision-ref",
      "DECISION_REF_INVALID",
      (root) => {
        const value = buildApprovedEvidence(root, 1);
        value.approval.decisionRef = ".doc/技术设计/电力运维云平台/missing-signoff.md";
        finalizeApproval(value);
        return value;
      },
    ],
    [
      "td-old",
      "TD_VERSION_NOT_NEWER_THAN_OWNER_BASELINE",
      (root) => {
        const value = buildApprovedEvidence(root, 1);
        value.artifacts.tdDocument.documentVersion = "1.0.3";
        rewriteArtifact(root, value.artifacts.tdDocument, "# TD-003\n\n> 版本：1.0.3\n");
        finalizeApproval(value);
        return value;
      },
    ],
    [
      "td-version-mismatch",
      "TD_VERSION_MISMATCH",
      (root) => {
        const value = buildApprovedEvidence(root, 1);
        rewriteArtifact(root, value.artifacts.tdDocument, "# TD-003\n\n> 版本：1.0.5\n");
        finalizeApproval(value);
        return value;
      },
    ],
    [
      "td-path",
      "TD_DOCUMENT_PATH_INVALID",
      (root) => {
        const value = buildApprovedEvidence(root, 1);
        const alternate = ".doc/技术设计/电力运维云平台/TD-003-copy.md";
        writeText(
          root,
          alternate,
          fs.readFileSync(path.join(root, ...TD_DOCUMENT_PATH.split("/")), "utf8"),
        );
        value.artifacts.tdDocument = artifactFor(root, alternate, { documentVersion: "1.0.4" });
        finalizeApproval(value);
        return value;
      },
    ],
    [
      "production-json",
      "PRODUCTION_SCHEMA_JSON_INVALID",
      (root) => {
        const value = buildApprovedEvidence(root, 1);
        rewriteArtifact(root, value.artifacts.productionSchema, "{not-json\n");
        finalizeApproval(value);
        return value;
      },
    ],
    [
      "production-strict",
      "PRODUCTION_SCHEMA_STRICT_COMPILE_FAILED",
      (root) => {
        const value = buildApprovedEvidence(root, 1);
        rewriteProductionSchema(root, value, (schema) => {
          schema.properties.messageId = { pattern: "^x$" };
        });
        return value;
      },
    ],
    [
      "production-field-missing",
      "PRODUCTION_SCHEMA_FIELD_MISSING",
      (root) => {
        const value = buildApprovedEvidence(root, 1);
        rewriteProductionSchema(root, value, (schema) => {
          delete schema.properties.workloadId;
          schema.required = schema.required.filter((field) => field !== "workloadId");
        });
        return value;
      },
    ],
    [
      "production-field-optional",
      "PRODUCTION_SCHEMA_FIELD_NOT_REQUIRED",
      (root) => {
        const value = buildApprovedEvidence(root, 1);
        rewriteProductionSchema(root, value, (schema) => {
          schema.required = schema.required.filter((field) => field !== "quality");
        });
        return value;
      },
    ],
    [
      "production-open",
      "PRODUCTION_SCHEMA_ADDITIONAL_PROPERTIES_OPEN",
      (root) => {
        const value = buildApprovedEvidence(root, 1);
        rewriteProductionSchema(root, value, (schema) => {
          schema.additionalProperties = true;
        });
        return value;
      },
    ],
    ...["eventType", "eventVersion", "source"].map((field) => [
      `production-${field}`,
      "PRODUCTION_SCHEMA_CONST_MISMATCH",
      (root) => {
        const value = buildApprovedEvidence(root, 1);
        rewriteProductionSchema(root, value, (schema) => {
          schema.properties[field].const = "WRONG";
        });
        return value;
      },
    ]),
    [
      "history-order",
      "PUBLICATION_TIME_ORDER_INVALID",
      (root) => {
        const value = buildApprovedEvidence(root, 2);
        value.publicationHistory.environments[0].firstPublishedAt = "2026-08-02T00:00:00Z";
        finalizeApproval(value);
        return value;
      },
    ],
    [
      "history-duplicate-environment",
      "PUBLICATION_ENVIRONMENT_ID_DUPLICATE",
      (root) => {
        const value = buildApprovedEvidence(root, 2);
        value.publicationHistory.environments.push({
          ...value.publicationHistory.environments[0],
          latestPublishedAt: "2026-08-02T00:00:00Z",
        });
        finalizeApproval(value);
        return value;
      },
    ],
    [
      "approval-before-publication",
      "APPROVAL_BEFORE_LATEST_PUBLICATION",
      (root) => {
        const value = buildApprovedEvidence(root, 2);
        value.approval.approvedAt = "2026-07-01T00:00:00Z";
        finalizeApproval(value);
        return value;
      },
    ],
    [
      "consumer-environment",
      "CONSUMER_INVENTORY_ENVIRONMENT_MISSING",
      (root) => {
        const value = buildApprovedEvidence(root, 2);
        rewriteArtifact(root, value.artifacts.consumerInventory, "iot-device\n");
        finalizeApproval(value);
        return value;
      },
    ],
    [
      "consumer-name",
      "CONSUMER_INVENTORY_CONSUMER_MISSING",
      (root) => {
        const value = buildApprovedEvidence(root, 2);
        rewriteArtifact(root, value.artifacts.consumerInventory, "prod-a\n");
        finalizeApproval(value);
        return value;
      },
    ],
    [
      "test-plan-token",
      "TEST_PLAN_AREA_MISSING",
      (root) => {
        const value = buildApprovedEvidence(root, 1);
        rewriteArtifact(root, value.artifacts.testPlan, TEST_AREAS.slice(0, -1).join("\n"));
        finalizeApproval(value);
        return value;
      },
    ],
    [
      "version-exact-mismatch",
      "ARTIFACT_SCHEMA_VERSION_MISMATCH",
      (root) => {
        const value = buildApprovedEvidence(root, 1);
        value.eventVersionDecision.targetSchemaVersion = "1.1";
        finalizeApproval(value);
        return value;
      },
    ],
    [
      "migration-missing",
      "ARTIFACT_PATH_MISSING",
      (root) => {
        const value = buildApprovedEvidence(root, 2);
        value.eventVersionDecision.migrationPlan.path =
          ".doc/技术设计/电力运维云平台/missing-migration.md";
        finalizeApproval(value);
        return value;
      },
    ],
  ];

  let passed = 0;
  for (const [name, expectedCode, build, expectedQualification] of cases) {
    const root = fs.mkdtempSync(path.join(os.tmpdir(), `p02-g2-02-${name}-`));
    try {
      const evidence = build(root);
      const result = validateEvidence(evidence, root, validateSchema);
      const ok =
        expectedCode === null
          ? result.issues.length === 0 && result.qualification === expectedQualification
          : result.issues.some((entry) => entry.code === expectedCode);
      if (!ok) {
        process.stderr.write(
          `FAIL self-test=${name} expected=${expectedCode ?? expectedQualification} actual=${result.issues.map((entry) => entry.code).join(",")} qualification=${result.qualification}\n`,
        );
        return false;
      }
      passed += 1;
    } finally {
      fs.rmSync(root, { recursive: true, force: true });
    }
  }

  const directCases = [
    ["args-unknown", parseArgs(["--unknown"]).error === "ARGUMENT_INVALID"],
    ["args-missing", parseArgs(["--file"]).error === "ARGUMENT_VALUE_MISSING"],
    ["args-duplicate", parseArgs(["--self-test", "--self-test"]).error === "ARGUMENT_DUPLICATE"],
    ["unsafe-parent", !isSafeRepositoryPath("../outside.json", { jsonOnly: true })],
    ["unsafe-uri", !isSafeRepositoryPath("https://example.test/evidence.json", { jsonOnly: true })],
    ["realpath-boundary", !isInside("C:\\repo", "C:\\outside\\file")],
  ];
  for (const [name, ok] of directCases) {
    if (!ok) {
      process.stderr.write(`FAIL self-test=${name}\n`);
      return false;
    }
    passed += 1;
  }
  process.stdout.write(`SELF_TEST PASS cases=${passed}\n`);
  return true;
}

function main() {
  const parsed = parseArgs(process.argv.slice(2));
  if (parsed.error) {
    process.stderr.write(`FAIL code=${parsed.error} value=${parsed.value}\n`);
    process.exitCode = 2;
    return;
  }

  let validateSchema;
  try {
    validateSchema = createEvidenceValidator();
  } catch (error) {
    process.stderr.write(`FAIL code=SCHEMA_COMPILE_FAILED detail=${error.message}\n`);
    process.exitCode = 1;
    return;
  }

  const evidencePath = resolveEvidencePath(parsed.file);
  if (!evidencePath) {
    process.stderr.write(`FAIL code=EVIDENCE_PATH_INVALID value=${parsed.file}\n`);
    process.exitCode = 2;
    return;
  }
  if (!fs.existsSync(evidencePath) || !fs.statSync(evidencePath).isFile()) {
    process.stderr.write(`FAIL code=EVIDENCE_PATH_MISSING value=${parsed.file ?? "default"}\n`);
    process.exitCode = 1;
    return;
  }
  const realRoot = fs.realpathSync(REPO_ROOT);
  const realEvidence = fs.realpathSync(evidencePath);
  if (!isInside(realRoot, realEvidence)) {
    process.stderr.write(
      `FAIL code=EVIDENCE_PATH_OUTSIDE_REPOSITORY value=${parsed.file ?? "default"}\n`,
    );
    process.exitCode = 2;
    return;
  }

  let evidence;
  try {
    evidence = readJson(realEvidence);
  } catch {
    process.stderr.write(`FAIL code=EVIDENCE_JSON_INVALID value=${parsed.file ?? "default"}\n`);
    process.exitCode = 1;
    return;
  }
  const result = validateEvidence(evidence, REPO_ROOT, validateSchema);
  if (result.issues.length > 0) {
    for (const entry of result.issues) {
      process.stderr.write(
        `FAIL code=${entry.code}${entry.target ? ` target=${entry.target}` : ""}\n`,
      );
    }
    process.stderr.write(`SUMMARY FAIL issues=${result.issues.length}\n`);
    process.exitCode = 1;
    return;
  }

  const displayPath = path.relative(REPO_ROOT, realEvidence).replaceAll(path.sep, "/");
  process.stdout.write(`PASS evidence=${displayPath} qualification=${result.qualification}\n`);
  if (parsed.selfTest && !runSelfTests(validateSchema)) process.exitCode = 1;
}

main();
