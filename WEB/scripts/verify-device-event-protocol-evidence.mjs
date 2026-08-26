#!/usr/bin/env node
/**
 * PRD-02 C1P-G2-03 DEVICE_EVENT 协议证据本地门禁。
 *
 * 本脚本只读取仓库内 evidence、fixture 和脱敏 raw capture；不会访问网络、
 * 数据库、broker、Docker 或业务运行时。路径始终相对仓库根解析。
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
const EVIDENCE_SCHEMA_PATH = path.join(ASSET_ROOT, "device-event-protocol-evidence-v1.schema.json");
const FIXTURE_SCHEMA_PATH = path.join(ASSET_ROOT, "device-event-protocol-fixture-v1.schema.json");
const DEFAULT_EVIDENCE_PATH = path.join(ASSET_ROOT, "device-event-protocol-evidence-template.json");
const HASH_PATTERN = /^sha256:[0-9a-f]{64}$/;
const FIXTURE_KEYS = {
  original: "ORIGINAL",
  retry: "RETRY",
  collision: "COLLISION",
  missingRequestId: "MISSING_REQUEST_ID",
  invalidOccurredAt: "INVALID_OCCURRED_AT",
};

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
  if (value.approval && typeof value.approval === "object") delete value.approval.contentSha256;
  return sha256Bytes(Buffer.from(canonicalize(value), "utf8"));
}

function isSafeRepositoryPath(value) {
  if (
    typeof value !== "string" ||
    value.length === 0 ||
    !value.toLowerCase().endsWith(".json") ||
    value.includes("\\") ||
    value.startsWith("/") ||
    /^[A-Za-z]:/.test(value)
  )
    return false;
  const segments = value.split("/");
  return (
    !segments.includes("..") &&
    !segments.includes(".git") &&
    !segments.includes("node_modules") &&
    !segments.some((segment) => /^\.env(?:\.|$)/i.test(segment))
  );
}

function isSafeArtifactPath(value) {
  if (
    typeof value !== "string" ||
    value.length === 0 ||
    value.includes("\\") ||
    value.startsWith("/") ||
    /^[A-Za-z]:/.test(value)
  )
    return false;
  const segments = value.split("/");
  return (
    !segments.includes("..") &&
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

function createValidators() {
  const ajv = new Ajv2020({ strict: true, allErrors: true });
  addFormats(ajv);
  return {
    evidence: ajv.compile(readJson(EVIDENCE_SCHEMA_PATH)),
    fixture: ajv.compile(readJson(FIXTURE_SCHEMA_PATH)),
  };
}

function issue(issues, code, target = "") {
  issues.push({ code, target });
}

function validateArtifact(artifact, label, root, issues) {
  if (!artifact || typeof artifact !== "object") {
    issue(issues, "ARTIFACT_INVALID", label);
    return null;
  }
  if (!isSafeArtifactPath(artifact.path)) {
    issue(issues, "ARTIFACT_PATH_INVALID", `${label}:${artifact.path ?? ""}`);
    return null;
  }
  const full = path.resolve(root, ...artifact.path.split("/"));
  if (!isInside(root, full)) {
    issue(issues, "ARTIFACT_PATH_OUTSIDE_REPOSITORY", `${label}:${artifact.path}`);
    return null;
  }
  if (!fs.existsSync(full) || !fs.statSync(full).isFile()) {
    issue(issues, "ARTIFACT_PATH_MISSING", `${label}:${artifact.path}`);
    return null;
  }
  const realRoot = fs.realpathSync(root);
  const realFile = fs.realpathSync(full);
  if (!isInside(realRoot, realFile)) {
    issue(issues, "ARTIFACT_PATH_OUTSIDE_REPOSITORY", `${label}:${artifact.path}`);
    return null;
  }
  if (!HASH_PATTERN.test(artifact.sha256 ?? "") || sha256File(full) !== artifact.sha256) {
    issue(issues, "ARTIFACT_SHA256_MISMATCH", `${label}:${artifact.path}`);
    return null;
  }
  return full;
}

function expect(issues, condition, code, target) {
  if (!condition) issue(issues, code, target);
}

function validateFixtureSemantics(fixture, label, issues) {
  const identity = fixture.wireIdentity;
  const processing = fixture.processing;
  const ack = fixture.acknowledgement;
  const stringIdentity = () =>
    typeof identity.requestId === "string" &&
    typeof identity.occurredAt === "string" &&
    HASH_PATTERN.test(identity.canonicalPayloadSha256 ?? "");
  const commonNoFallback = () =>
    !processing.backendRequestIdGenerated && !processing.receiveTimeFallbackUsed;

  if (fixture.fixtureType === "ORIGINAL") {
    expect(issues, stringIdentity(), "ORIGINAL_IDENTITY_INVALID", label);
    expect(issues, processing.accepted === true, "ORIGINAL_NOT_ACCEPTED", label);
    expect(issues, processing.duplicate === false, "ORIGINAL_DUPLICATE", label);
    expect(issues, processing.collisionQuarantined === false, "ORIGINAL_COLLISION", label);
    expect(
      issues,
      processing.sourceTransactionCommitted === true,
      "ORIGINAL_SOURCE_TRANSACTION_NOT_COMMITTED",
      label,
    );
    expect(
      issues,
      processing.existingSourceTransactionVerified === false,
      "ORIGINAL_EXISTING_TRANSACTION",
      label,
    );
    expect(
      issues,
      processing.failurePropagatedToTransport === false,
      "ORIGINAL_FAILURE_PROPAGATED",
      label,
    );
    expect(issues, commonNoFallback(), "ORIGINAL_BACKEND_FALLBACK", label);
    expect(
      issues,
      ack.mode === "ACK" && ack.afterDurableDecision === true,
      "ORIGINAL_ACK_BEFORE_COMMIT",
      label,
    );
  } else if (fixture.fixtureType === "RETRY") {
    expect(issues, stringIdentity(), "RETRY_IDENTITY_INVALID", label);
    expect(issues, processing.accepted === true, "RETRY_NOT_ACCEPTED", label);
    expect(issues, processing.duplicate === true, "RETRY_NOT_DUPLICATE", label);
    expect(issues, processing.collisionQuarantined === false, "RETRY_COLLISION", label);
    expect(
      issues,
      processing.sourceTransactionCommitted === false,
      "RETRY_NEW_SOURCE_TRANSACTION",
      label,
    );
    expect(
      issues,
      processing.existingSourceTransactionVerified === true,
      "RETRY_EXISTING_TRANSACTION_UNVERIFIED",
      label,
    );
    expect(
      issues,
      processing.failurePropagatedToTransport === false,
      "RETRY_FAILURE_PROPAGATED",
      label,
    );
    expect(issues, commonNoFallback(), "RETRY_BACKEND_FALLBACK", label);
    expect(
      issues,
      ack.mode === "ACK" && ack.afterDurableDecision === true,
      "RETRY_ACK_BEFORE_DUPLICATE_CHECK",
      label,
    );
  } else if (fixture.fixtureType === "COLLISION") {
    expect(issues, stringIdentity(), "COLLISION_IDENTITY_INVALID", label);
    expect(issues, processing.accepted === false, "COLLISION_ACCEPTED", label);
    expect(issues, processing.duplicate === false, "COLLISION_MARKED_DUPLICATE", label);
    expect(issues, processing.collisionQuarantined === true, "COLLISION_NOT_QUARANTINED", label);
    expect(
      issues,
      processing.sourceTransactionCommitted === false,
      "COLLISION_SOURCE_TRANSACTION_COMMITTED",
      label,
    );
    expect(
      issues,
      processing.existingSourceTransactionVerified === false,
      "COLLISION_EXISTING_TRANSACTION_ACCEPTED",
      label,
    );
    expect(
      issues,
      processing.failurePropagatedToTransport === true,
      "COLLISION_FAILURE_NOT_PROPAGATED",
      label,
    );
    expect(issues, commonNoFallback(), "COLLISION_BACKEND_FALLBACK", label);
    expect(
      issues,
      ack.mode === "NACK" && ack.afterDurableDecision === true,
      "COLLISION_NACK_INVALID",
      label,
    );
  } else if (fixture.fixtureType === "MISSING_REQUEST_ID") {
    expect(issues, identity.requestId === null, "MISSING_REQUEST_ID_NOT_NULL", label);
    expect(issues, processing.accepted === false, "MISSING_REQUEST_ID_ACCEPTED", label);
    expect(
      issues,
      processing.duplicate === false && processing.collisionQuarantined === false,
      "MISSING_REQUEST_ID_WRONG_CLASSIFICATION",
      label,
    );
    expect(
      issues,
      processing.sourceTransactionCommitted === false &&
        processing.existingSourceTransactionVerified === false,
      "MISSING_REQUEST_ID_SOURCE_MUTATED",
      label,
    );
    expect(
      issues,
      processing.failurePropagatedToTransport === true,
      "MISSING_REQUEST_ID_FAILURE_NOT_PROPAGATED",
      label,
    );
    expect(
      issues,
      processing.backendRequestIdGenerated === false,
      "MISSING_REQUEST_ID_BACKEND_GENERATED",
      label,
    );
    expect(
      issues,
      ack.mode === "NACK" && ack.afterDurableDecision === true,
      "MISSING_REQUEST_ID_NACK_INVALID",
      label,
    );
  } else if (fixture.fixtureType === "INVALID_OCCURRED_AT") {
    expect(
      issues,
      typeof identity.requestId === "string" && typeof identity.occurredAt === "string",
      "INVALID_OCCURRED_AT_WIRE_IDENTITY_INVALID",
      label,
    );
    expect(issues, processing.accepted === false, "INVALID_OCCURRED_AT_ACCEPTED", label);
    expect(
      issues,
      processing.duplicate === false && processing.collisionQuarantined === false,
      "INVALID_OCCURRED_AT_WRONG_CLASSIFICATION",
      label,
    );
    expect(
      issues,
      processing.sourceTransactionCommitted === false &&
        processing.existingSourceTransactionVerified === false,
      "INVALID_OCCURRED_AT_SOURCE_MUTATED",
      label,
    );
    expect(
      issues,
      processing.failurePropagatedToTransport === true,
      "INVALID_OCCURRED_AT_FAILURE_NOT_PROPAGATED",
      label,
    );
    expect(
      issues,
      processing.receiveTimeFallbackUsed === false,
      "INVALID_OCCURRED_AT_RECEIVE_TIME_FALLBACK",
      label,
    );
    expect(
      issues,
      processing.backendRequestIdGenerated === false,
      "INVALID_OCCURRED_AT_BACKEND_ID",
      label,
    );
    expect(
      issues,
      ack.mode === "NACK" && ack.afterDurableDecision === true,
      "INVALID_OCCURRED_AT_NACK_INVALID",
      label,
    );
  }
}

function validateProtocolFixtures(protocol, root, validators, issues) {
  const loaded = new Map();
  for (const [fixtureKey, expectedType] of Object.entries(FIXTURE_KEYS)) {
    const artifact = protocol.fixtures[fixtureKey];
    if (artifact === null) continue;
    const label = `${protocol.protocolId}:${fixtureKey}`;
    const file = validateArtifact(artifact, label, root, issues);
    if (!file) continue;
    let fixture;
    try {
      fixture = readJson(file);
    } catch {
      issue(issues, "FIXTURE_JSON_INVALID", label);
      continue;
    }
    if (!validators.fixture(fixture)) {
      issue(issues, "FIXTURE_SCHEMA_INVALID", label);
      continue;
    }
    expect(issues, fixture.fixtureType === expectedType, "FIXTURE_TYPE_MISMATCH", label);
    expect(
      issues,
      fixture.protocolId === protocol.protocolId,
      "FIXTURE_PROTOCOL_ID_MISMATCH",
      label,
    );
    expect(
      issues,
      fixture.protocolVersion === protocol.protocolVersion,
      "FIXTURE_PROTOCOL_VERSION_MISMATCH",
      label,
    );
    validateArtifact(fixture.rawArtifact, `${label}:raw`, root, issues);
    validateFixtureSemantics(fixture, label, issues);
    loaded.set(fixtureKey, fixture);
  }

  if (protocol.decision !== "ENABLED") return;
  for (const key of Object.keys(FIXTURE_KEYS)) {
    expect(issues, loaded.has(key), "ENABLED_FIXTURE_MISSING", `${protocol.protocolId}:${key}`);
  }
  if (loaded.size !== Object.keys(FIXTURE_KEYS).length) return;

  const captureIds = [...loaded.values()].map((fixture) => fixture.captureId);
  expect(
    issues,
    new Set(captureIds).size === captureIds.length,
    "FIXTURE_CAPTURE_ID_DUPLICATE",
    protocol.protocolId,
  );
  const original = loaded.get("original").wireIdentity;
  const retry = loaded.get("retry").wireIdentity;
  const collision = loaded.get("collision").wireIdentity;
  expect(
    issues,
    retry.requestId === original.requestId,
    "RETRY_REQUEST_ID_MISMATCH",
    protocol.protocolId,
  );
  expect(
    issues,
    retry.occurredAt === original.occurredAt,
    "RETRY_OCCURRED_AT_MISMATCH",
    protocol.protocolId,
  );
  expect(
    issues,
    retry.canonicalPayloadSha256 === original.canonicalPayloadSha256,
    "RETRY_CANONICAL_HASH_MISMATCH",
    protocol.protocolId,
  );
  expect(
    issues,
    collision.requestId === original.requestId,
    "COLLISION_REQUEST_ID_MISMATCH",
    protocol.protocolId,
  );
  expect(
    issues,
    collision.canonicalPayloadSha256 !== original.canonicalPayloadSha256,
    "COLLISION_HASH_NOT_DISTINCT",
    protocol.protocolId,
  );
}

function validateEvidence(evidence, root, validators) {
  const issues = [];
  if (!validators.evidence(evidence)) {
    issue(issues, "EVIDENCE_SCHEMA_INVALID", validators.evidence.errors?.[0]?.instancePath ?? "");
    return { issues, protocols: 0, enabled: 0, qualification: "INVALID" };
  }

  const protocols = evidence.protocols;
  const protocolIds = protocols.map((protocol) => protocol.protocolId);
  expect(
    issues,
    new Set(protocolIds).size === protocolIds.length,
    "PROTOCOL_ID_DUPLICATE",
    protocolIds.join(","),
  );
  for (const protocol of protocols) {
    expect(
      issues,
      protocol.requestIdEvidence.minUtf8Bytes <= protocol.requestIdEvidence.maxUtf8Bytes,
      "REQUEST_ID_BYTE_RANGE_INVALID",
      protocol.protocolId,
    );
    validateProtocolFixtures(protocol, root, validators, issues);
  }

  if (evidence.documentStatus === "DRAFT") {
    expect(issues, evidence.approval === null, "DRAFT_APPROVAL_MUST_BE_NULL", "approval");
  }

  if (evidence.documentStatus === "APPROVED" || evidence.documentStatus === "RETIRED") {
    let actual;
    try {
      actual = approvalContentSha256(evidence);
    } catch {
      issue(issues, "APPROVAL_CANONICALIZATION_FAILED");
    }
    if (actual && actual !== evidence.approval.contentSha256) {
      issue(issues, "APPROVAL_CONTENT_SHA256_MISMATCH");
    }
  }

  const enabled = protocols.filter((protocol) => protocol.decision === "ENABLED").length;
  const qualification =
    evidence.documentStatus === "DRAFT"
      ? "DRAFT"
      : enabled > 0
        ? "ENABLED_PROTOCOLS_PRESENT"
        : "NO_ENABLED_PROTOCOL";
  return { issues, protocols: protocols.length, enabled, qualification };
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
  if (!isSafeRepositoryPath(value)) return null;
  const full = path.resolve(REPO_ROOT, ...value.split("/"));
  return isInside(REPO_ROOT, full) ? full : null;
}

function fixtureProcessing(overrides = {}) {
  return {
    accepted: false,
    duplicate: false,
    collisionQuarantined: false,
    sourceTransactionCommitted: false,
    existingSourceTransactionVerified: false,
    failurePropagatedToTransport: true,
    backendRequestIdGenerated: false,
    receiveTimeFallbackUsed: false,
    ...overrides,
  };
}

function writeFixture(root, key, fixtureType, rawText, identity, processing, acknowledgement) {
  const rawRelative = `raw/${key}.txt`;
  const fixtureRelative = `fixtures/${key}.json`;
  fs.mkdirSync(path.join(root, "raw"), { recursive: true });
  fs.mkdirSync(path.join(root, "fixtures"), { recursive: true });
  fs.writeFileSync(path.join(root, rawRelative), rawText, "utf8");
  const fixture = {
    schemaVersion: "1.0",
    fixtureType,
    protocolId: "example.direct-event",
    protocolVersion: "1.0",
    captureId: `capture-${key}`,
    rawArtifact: { path: rawRelative, sha256: sha256File(path.join(root, rawRelative)) },
    wireIdentity: identity,
    processing,
    acknowledgement,
  };
  fs.writeFileSync(
    path.join(root, fixtureRelative),
    `${JSON.stringify(fixture, null, 2)}\n`,
    "utf8",
  );
  return {
    path: fixtureRelative,
    sha256: sha256File(path.join(root, fixtureRelative)),
  };
}

function buildApprovedEvidence(root) {
  const originalHash = `sha256:${"1".repeat(64)}`;
  const collisionHash = `sha256:${"2".repeat(64)}`;
  const identity = {
    requestId: "device-request-1",
    occurredAt: "2026-08-26T13:00:00.000+08:00",
    canonicalPayloadSha256: originalHash,
  };
  const fixtures = {
    original: writeFixture(
      root,
      "original",
      "ORIGINAL",
      '{"id":"device-request-1","value":1}',
      identity,
      fixtureProcessing({
        accepted: true,
        sourceTransactionCommitted: true,
        failurePropagatedToTransport: false,
      }),
      { mode: "ACK", afterDurableDecision: true },
    ),
    retry: writeFixture(
      root,
      "retry",
      "RETRY",
      '{"id":"device-request-1","value":1}',
      identity,
      fixtureProcessing({
        accepted: true,
        duplicate: true,
        existingSourceTransactionVerified: true,
        failurePropagatedToTransport: false,
      }),
      { mode: "ACK", afterDurableDecision: true },
    ),
    collision: writeFixture(
      root,
      "collision",
      "COLLISION",
      '{"id":"device-request-1","value":2}',
      { ...identity, canonicalPayloadSha256: collisionHash },
      fixtureProcessing({ collisionQuarantined: true }),
      { mode: "NACK", afterDurableDecision: true },
    ),
    missingRequestId: writeFixture(
      root,
      "missing-request-id",
      "MISSING_REQUEST_ID",
      '{"value":1}',
      { requestId: null, occurredAt: identity.occurredAt, canonicalPayloadSha256: originalHash },
      fixtureProcessing(),
      { mode: "NACK", afterDurableDecision: true },
    ),
    invalidOccurredAt: writeFixture(
      root,
      "invalid-occurred-at",
      "INVALID_OCCURRED_AT",
      '{"id":"device-request-2","occurredAt":"not-a-time"}',
      {
        requestId: "device-request-2",
        occurredAt: "not-a-time",
        canonicalPayloadSha256: originalHash,
      },
      fixtureProcessing(),
      { mode: "NACK", afterDurableDecision: true },
    ),
  };
  const evidence = {
    schemaVersion: "1.0",
    canonicalizationVersion: "jcs-rfc8785-v1",
    documentStatus: "APPROVED",
    revision: 1,
    protocols: [
      {
        protocolId: "example.direct-event",
        protocolVersion: "1.0",
        transport: "MQTT",
        codecClass: "ExampleCodec",
        directEventTopicPattern: "/iot/{product}/{device}/event/upstream/report/{identifier}",
        subjectMode: "DIRECT",
        requestIdEvidence: {
          wireField: "id",
          deviceGenerated: true,
          retryStable: true,
          uniquenessScope: "TENANT_PRODUCT_DEVICE",
          uniquenessWindowSeconds: 86400,
          minUtf8Bytes: 1,
          maxUtf8Bytes: 128,
          controlCharactersRejected: true,
        },
        occurredAtEvidence: {
          wireField: "occurredAt",
          deviceGenerated: true,
          format: "RFC3339_OFFSET_MILLIS",
          originalOffsetPreserved: true,
          millisecondPrecision: true,
          clockSource: "device-rtc",
          maxFutureSkewSeconds: 300,
          maxHistoryAgeSeconds: 2592000,
        },
        payloadCanonicalization: {
          strategy: "JCS_RFC8785_V1",
          rawBytesHashPreserved: true,
          scriptTransformAllowed: false,
        },
        ackContract: {
          ackMode: "APPLICATION_ACK",
          afterSourceTransactionCommit: true,
          failurePropagatedToTransport: true,
        },
        fixtures,
        decision: "ENABLED",
        reasonCodes: [],
      },
    ],
    approval: {
      ownerRole: "protocol-owner",
      approvedBy: "self-test",
      approvedAt: "2026-08-26T13:00:00+08:00",
      decisionRef: "self-test-only",
      contentSha256: `sha256:${"0".repeat(64)}`,
    },
  };
  evidence.approval.contentSha256 = approvalContentSha256(evidence);
  return evidence;
}

function refreshFixture(root, evidence, key, mutate, refreshArtifact = true) {
  const artifact = evidence.protocols[0].fixtures[key];
  const file = path.join(root, ...artifact.path.split("/"));
  const fixture = readJson(file);
  mutate(fixture);
  fs.writeFileSync(file, `${JSON.stringify(fixture, null, 2)}\n`, "utf8");
  if (refreshArtifact) artifact.sha256 = sha256File(file);
  evidence.approval.contentSha256 = approvalContentSha256(evidence);
}

function runSelfTests(validators) {
  const cases = [
    [
      "draft",
      null,
      (root) => ({
        schemaVersion: "1.0",
        canonicalizationVersion: "jcs-rfc8785-v1",
        documentStatus: "DRAFT",
        revision: 1,
        protocols: [],
        approval: null,
      }),
      "DRAFT",
    ],
    ["approved-enabled", null, (root) => buildApprovedEvidence(root), "ENABLED_PROTOCOLS_PRESENT"],
    [
      "approved-no-enabled",
      null,
      (root) => {
        const evidence = buildApprovedEvidence(root);
        const protocol = evidence.protocols[0];
        protocol.decision = "DISABLED";
        protocol.reasonCodes = ["OWNER_APPROVED_NONE_ENABLED"];
        for (const key of Object.keys(protocol.fixtures)) protocol.fixtures[key] = null;
        evidence.approval.contentSha256 = approvalContentSha256(evidence);
        return evidence;
      },
      "NO_ENABLED_PROTOCOL",
    ],
    [
      "retry-request-id",
      "RETRY_REQUEST_ID_MISMATCH",
      (root) => {
        const evidence = buildApprovedEvidence(root);
        refreshFixture(root, evidence, "retry", (fixture) => {
          fixture.wireIdentity.requestId = "other-id";
        });
        return evidence;
      },
    ],
    [
      "collision-hash",
      "COLLISION_HASH_NOT_DISTINCT",
      (root) => {
        const evidence = buildApprovedEvidence(root);
        refreshFixture(root, evidence, "collision", (fixture) => {
          fixture.wireIdentity.canonicalPayloadSha256 = `sha256:${"1".repeat(64)}`;
        });
        return evidence;
      },
    ],
    [
      "artifact-tamper",
      "ARTIFACT_SHA256_MISMATCH",
      (root) => {
        const evidence = buildApprovedEvidence(root);
        refreshFixture(
          root,
          evidence,
          "original",
          (fixture) => {
            fixture.captureId = "tampered";
          },
          false,
        );
        return evidence;
      },
    ],
    [
      "unsafe-path",
      "ARTIFACT_PATH_INVALID",
      (root) => {
        const evidence = buildApprovedEvidence(root);
        evidence.protocols[0].fixtures.original.path = "../outside.json";
        evidence.approval.contentSha256 = approvalContentSha256(evidence);
        return evidence;
      },
    ],
    [
      "approval-hash",
      "APPROVAL_CONTENT_SHA256_MISMATCH",
      (root) => {
        const evidence = buildApprovedEvidence(root);
        evidence.approval.contentSha256 = `sha256:${"f".repeat(64)}`;
        return evidence;
      },
    ],
    [
      "draft-approval",
      "DRAFT_APPROVAL_MUST_BE_NULL",
      (root) => ({
        schemaVersion: "1.0",
        canonicalizationVersion: "jcs-rfc8785-v1",
        documentStatus: "DRAFT",
        revision: 1,
        protocols: [],
        approval: {
          ownerRole: "protocol-owner",
          approvedBy: "not-valid-for-draft",
          approvedAt: "2026-08-26T13:00:00+08:00",
          decisionRef: "self-test-only",
          contentSha256: `sha256:${"0".repeat(64)}`,
        },
      }),
    ],
    [
      "precommit-schema",
      "EVIDENCE_SCHEMA_INVALID",
      (root) => {
        const evidence = buildApprovedEvidence(root);
        evidence.protocols[0].ackContract.afterSourceTransactionCommit = false;
        return evidence;
      },
    ],
    [
      "byte-range",
      "REQUEST_ID_BYTE_RANGE_INVALID",
      (root) => {
        const evidence = buildApprovedEvidence(root);
        evidence.protocols[0].requestIdEvidence.minUtf8Bytes = 100;
        evidence.protocols[0].requestIdEvidence.maxUtf8Bytes = 10;
        evidence.approval.contentSha256 = approvalContentSha256(evidence);
        return evidence;
      },
    ],
  ];

  let passed = 0;
  for (const [name, expectedCode, build, expectedQualification] of cases) {
    const root = fs.mkdtempSync(path.join(os.tmpdir(), `p02-g2-03-${name}-`));
    try {
      const evidence = build(root);
      const result = validateEvidence(evidence, root, validators);
      const ok =
        expectedCode === null
          ? result.issues.length === 0 &&
            (expectedQualification === undefined || result.qualification === expectedQualification)
          : result.issues.some((entry) => entry.code === expectedCode);
      if (!ok) {
        process.stderr.write(
          `FAIL self-test=${name} expected=${expectedCode ?? "PASS"} actual=${result.issues.map((entry) => entry.code).join(",")}\n`,
        );
        return false;
      }
      passed += 1;
    } finally {
      fs.rmSync(root, { recursive: true, force: true });
    }
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

  let validators;
  try {
    validators = createValidators();
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
  if (!isInside(fs.realpathSync(REPO_ROOT), fs.realpathSync(evidencePath))) {
    process.stderr.write(
      `FAIL code=EVIDENCE_PATH_OUTSIDE_REPOSITORY value=${parsed.file ?? "default"}\n`,
    );
    process.exitCode = 2;
    return;
  }

  let evidence;
  try {
    evidence = readJson(evidencePath);
  } catch {
    process.stderr.write(`FAIL code=EVIDENCE_JSON_INVALID value=${parsed.file ?? "default"}\n`);
    process.exitCode = 1;
    return;
  }
  const result = validateEvidence(evidence, REPO_ROOT, validators);
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

  const displayPath = path.relative(REPO_ROOT, evidencePath).replaceAll(path.sep, "/");
  process.stdout.write(
    `PASS evidence=${displayPath} protocols=${result.protocols} enabled=${result.enabled} qualification=${result.qualification}\n`,
  );
  if (parsed.selfTest && !runSelfTests(validators)) process.exitCode = 1;
}

main();
