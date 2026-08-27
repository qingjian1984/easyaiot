#!/usr/bin/env node
/**
 * PRD-02 C1P-G2-04 DEVICE_EVENT 产品 allowlist 评审本地门禁。
 *
 * 仅读取仓库内 review package、allowlist、协议证据与脱敏 fixture；不会访问
 * 网络、数据库、broker、Docker 或业务运行时。所有输入路径均相对仓库根解析。
 */
import Ajv2020 from "ajv/dist/2020.js";
import addFormats from "ajv-formats";
import crypto from "node:crypto";
import fs from "node:fs";
import os from "node:os";
import path from "node:path";
import { spawnSync } from "node:child_process";
import { fileURLToPath } from "node:url";

const SCRIPT_DIR = path.dirname(fileURLToPath(import.meta.url));
const REPO_ROOT = path.resolve(SCRIPT_DIR, "..", "..");
const DOC_ROOT = path.join(REPO_ROOT, ".doc", "技术设计", "电力运维云平台");
const G1_ASSET_ROOT = path.join(DOC_ROOT, "assets", "c1p-g1");
const G2_ASSET_ROOT = path.join(DOC_ROOT, "assets", "c1p-g2");
const REVIEW_SCHEMA_PATH = path.join(
  G2_ASSET_ROOT,
  "device-event-alarm-allowlist-review-v1.schema.json",
);
const FIXTURE_SCHEMA_PATH = path.join(
  G2_ASSET_ROOT,
  "device-event-alarm-mapping-fixture-v1.schema.json",
);
const ALLOWLIST_SCHEMA_PATH = path.join(
  G1_ASSET_ROOT,
  "device-event-alarm-allowlist-v1.schema.json",
);
const PROTOCOL_SCHEMA_PATH = path.join(
  G2_ASSET_ROOT,
  "device-event-protocol-evidence-v1.schema.json",
);
const DEFAULT_REVIEW_PATH = path.join(
  G2_ASSET_ROOT,
  "device-event-alarm-allowlist-review-template.json",
);
const DEFAULT_PROTOCOL_EVIDENCE_PATH = path.join(
  G2_ASSET_ROOT,
  "device-event-protocol-evidence-template.json",
);
const PROTOCOL_VERIFIER_PATH = path.join(SCRIPT_DIR, "verify-device-event-protocol-evidence.mjs");
const HASH_PATTERN = /^sha256:[0-9a-f]{64}$/;
const OFFSET_MILLIS_PATTERN =
  /^(\d{4})-(\d{2})-(\d{2})T(\d{2}):(\d{2}):(\d{2})\.(\d{3})(Z|([+-])(\d{2}):(\d{2}))$/;
const PLACEHOLDER_ANSWERS = new Set(["同上", "按默认", "TBD", "TODO", "N/A"]);
const FIXTURE_TYPES = {
  raised: "RAISED",
  recovered: "RECOVERED",
  unmatched: "UNMATCHED",
  correlationMismatch: "CORRELATION_MISMATCH",
  predicateTypeMismatch: "PREDICATE_TYPE_MISMATCH",
  fieldMissing: "FIELD_MISSING",
};
const BASE_FIXTURE_KEYS = [
  "raised",
  "recovered",
  "unmatched",
  "correlationMismatch",
  "fieldMissing",
];

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

function hashCanonical(value) {
  return sha256Bytes(Buffer.from(canonicalize(value), "utf8"));
}

function allowlistApprovalContentSha256(allowlist) {
  return hashCanonical({
    schemaVersion: allowlist.schemaVersion,
    canonicalizationVersion: allowlist.canonicalizationVersion,
    documentStatus: allowlist.documentStatus,
    revision: allowlist.revision,
    mappings: allowlist.mappings,
    approvalEvidence: allowlist.approval
      ? {
          approvedBy: allowlist.approval.approvedBy,
          approvedAt: allowlist.approval.approvedAt,
          decisionRef: allowlist.approval.decisionRef,
        }
      : null,
  });
}

function reviewApprovalContentSha256(review) {
  const value = structuredClone(review);
  if (value.reviewApproval && typeof value.reviewApproval === "object") {
    delete value.reviewApproval.contentSha256;
  }
  return hashCanonical(value);
}

function protocolApprovalContentSha256(evidence) {
  const value = structuredClone(evidence);
  if (value.approval && typeof value.approval === "object") {
    delete value.approval.contentSha256;
  }
  return hashCanonical(value);
}

function isSafeRepositoryJsonPath(value) {
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
  const ajv = new Ajv2020({ strict: true, allowUnionTypes: true, allErrors: true });
  addFormats(ajv);
  return {
    review: ajv.compile(readJson(REVIEW_SCHEMA_PATH)),
    allowlist: ajv.compile(readJson(ALLOWLIST_SCHEMA_PATH)),
    protocol: ajv.compile(readJson(PROTOCOL_SCHEMA_PATH)),
    fixture: ajv.compile(readJson(FIXTURE_SCHEMA_PATH)),
  };
}

function issue(issues, code, target = "") {
  issues.push({ code, target });
}

function expect(issues, condition, code, target = "") {
  if (!condition) issue(issues, code, target);
}

function validateArtifact(artifact, label, root, issues) {
  if (!artifact || typeof artifact !== "object") {
    issue(issues, "ARTIFACT_INVALID", label);
    return null;
  }
  if (!isSafeRepositoryJsonPath(artifact.path)) {
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

function readValidatedJson(file, validate, invalidCode, issues) {
  if (!file) return null;
  let value;
  try {
    value = readJson(file);
  } catch {
    issue(issues, `${invalidCode}_JSON_INVALID`);
    return null;
  }
  if (!validate(value)) {
    issue(issues, `${invalidCode}_SCHEMA_INVALID`, validate.errors?.[0]?.instancePath ?? "");
    return null;
  }
  return value;
}

function jsonType(value) {
  if (value === null) return "null";
  if (Array.isArray(value)) return "array";
  return typeof value;
}

function jsonScalarEqual(left, right) {
  if (left === null || right === null) return left === right;
  if (jsonType(left) !== jsonType(right)) return false;
  return Object.is(left, right);
}

function decodePointer(pointer) {
  if (typeof pointer !== "string" || (pointer !== "" && !pointer.startsWith("/"))) return null;
  if (pointer === "") return [];
  const tokens = pointer.slice(1).split("/");
  if (tokens.some((token) => /~(?:[^01]|$)/.test(token))) return null;
  return tokens.map((token) => token.replaceAll("~1", "/").replaceAll("~0", "~"));
}

function resolveJsonPointer(value, pointer) {
  const tokens = decodePointer(pointer);
  if (tokens === null) return { exists: false, invalid: true, value: undefined };
  let current = value;
  for (const token of tokens) {
    if (Array.isArray(current)) {
      if (!/^(?:0|[1-9]\d*)$/.test(token)) {
        return { exists: false, invalid: true, value: undefined };
      }
      const index = Number(token);
      if (!Number.isSafeInteger(index) || index >= current.length) {
        return { exists: false, invalid: false, value: undefined };
      }
      current = current[index];
    } else if (current !== null && typeof current === "object") {
      if (!Object.prototype.hasOwnProperty.call(current, token)) {
        return { exists: false, invalid: false, value: undefined };
      }
      current = current[token];
    } else {
      return { exists: false, invalid: false, value: undefined };
    }
  }
  return { exists: true, invalid: false, value: current };
}

function evaluatePredicate(payload, predicate) {
  const resolved = resolveJsonPointer(payload, predicate.jsonPointer);
  if (!resolved.exists || resolved.invalid) return false;
  if (predicate.operator === "EXISTS") return true;
  return jsonScalarEqual(resolved.value, predicate.expectedValue);
}

function matcherMatches(matcher, event) {
  return (
    matcher.eventIdentifier === event.eventIdentifier &&
    matcher.payloadPredicates.every((predicate) => evaluatePredicate(event.payload, predicate))
  );
}

function pointerIsPrefix(left, right) {
  const a = decodePointer(left);
  const b = decodePointer(right);
  if (a === null || b === null || a.length >= b.length) return false;
  return a.every((token, index) => token === b[index]);
}

function predicatesSatisfiable(predicates) {
  const equals = new Map();
  for (const predicate of predicates) {
    if (predicate.operator !== "EQUALS") continue;
    if (equals.has(predicate.jsonPointer)) {
      if (!jsonScalarEqual(equals.get(predicate.jsonPointer), predicate.expectedValue))
        return false;
    } else {
      equals.set(predicate.jsonPointer, predicate.expectedValue);
    }
  }
  const pointers = predicates.map((predicate) => predicate.jsonPointer);
  for (const [pointer, scalar] of equals) {
    if (
      (scalar === null || typeof scalar !== "object") &&
      pointers.some((candidate) => pointerIsPrefix(pointer, candidate))
    )
      return false;
  }
  return true;
}

function matchersIntersect(left, right) {
  return (
    left.eventIdentifier === right.eventIdentifier &&
    predicatesSatisfiable([...left.payloadPredicates, ...right.payloadPredicates])
  );
}

function scopesOverlap(left, right) {
  if (left.productIdentification !== right.productIdentification) return false;
  if (left.scopeType === "PRODUCT" || right.scopeType === "PRODUCT") return true;
  return left.modelIdentifier === right.modelIdentifier;
}

function mappingKey(mapping) {
  return `${mapping.mappingId}@${mapping.mappingVersion}`;
}

function isOffsetMillisDateTime(value) {
  if (typeof value !== "string") return false;
  const match = OFFSET_MILLIS_PATTERN.exec(value);
  if (!match) return false;
  const [
    ,
    yearText,
    monthText,
    dayText,
    hourText,
    minuteText,
    secondText,
    ,
    zone,
    ,
    offsetHourText,
    offsetMinuteText,
  ] = match;
  const year = Number(yearText);
  const month = Number(monthText);
  const day = Number(dayText);
  const hour = Number(hourText);
  const minute = Number(minuteText);
  const second = Number(secondText);
  if (year < 1 || month < 1 || month > 12 || hour > 23 || minute > 59 || second > 59) {
    return false;
  }
  const daysInMonth = new Date(Date.UTC(year, month, 0)).getUTCDate();
  if (day < 1 || day > daysInMonth) return false;
  if (zone !== "Z") {
    const offsetHour = Number(offsetHourText);
    const offsetMinute = Number(offsetMinuteText);
    if (offsetHour > 14 || offsetMinute > 59 || (offsetHour === 14 && offsetMinute !== 0)) {
      return false;
    }
  }
  return Number.isFinite(Date.parse(value));
}

function extractIdentity(mapping, event) {
  const correlation = resolveJsonPointer(event.payload, mapping.correlationJsonPointer);
  const occurredAt = resolveJsonPointer(event.payload, mapping.occurredAtJsonPointer);
  const correlationValid =
    correlation.exists &&
    !correlation.invalid &&
    typeof correlation.value === "string" &&
    Buffer.byteLength(correlation.value, "utf8") >= 1 &&
    Buffer.byteLength(correlation.value, "utf8") <= 512 &&
    correlation.value.trim().length > 0 &&
    !/[\u0000-\u001f\u007f]/.test(correlation.value);
  const occurredAtValid =
    occurredAt.exists && !occurredAt.invalid && isOffsetMillisDateTime(occurredAt.value);
  return {
    valid: correlationValid && occurredAtValid,
    correlationKey: correlationValid ? correlation.value : null,
    occurredAt: occurredAtValid ? occurredAt.value : null,
  };
}

function actionMatches(mapping, event) {
  const actions = [];
  if (matcherMatches(mapping.raisedMatcher, event)) actions.push("RAISED");
  if (matcherMatches(mapping.recoveredMatcher, event)) actions.push("RECOVERED");
  return actions;
}

function expectedMatches(actual, expected) {
  try {
    return canonicalize(actual) === canonicalize(expected);
  } catch {
    return false;
  }
}

function expectedAccepted(mapping, action, identity, pairedIdentity = null) {
  return {
    accepted: true,
    matchedMappingId: mapping.mappingId,
    sourceAction: action,
    correlationKey: identity.correlationKey,
    pairedCorrelationKey: pairedIdentity?.correlationKey ?? null,
    occurredAt: identity.occurredAt,
    pairedOccurredAt: pairedIdentity?.occurredAt ?? null,
    failureCode: null,
  };
}

function expectedRejected(failureCode, identity = null, pairedIdentity = null, mapping = null) {
  return {
    accepted: false,
    matchedMappingId: mapping?.mappingId ?? null,
    sourceAction: null,
    correlationKey: identity?.correlationKey ?? null,
    pairedCorrelationKey: pairedIdentity?.correlationKey ?? null,
    occurredAt: identity?.occurredAt ?? null,
    pairedOccurredAt: pairedIdentity?.occurredAt ?? null,
    failureCode,
  };
}

function matcherHasTypeMismatch(matcher, event) {
  if (matcher.eventIdentifier !== event.eventIdentifier) return false;
  let mismatch = false;
  for (const predicate of matcher.payloadPredicates) {
    const resolved = resolveJsonPointer(event.payload, predicate.jsonPointer);
    if (!resolved.exists || resolved.invalid) return false;
    if (predicate.operator === "EXISTS") continue;
    if (jsonType(resolved.value) !== jsonType(predicate.expectedValue)) {
      mismatch = true;
    } else if (!jsonScalarEqual(resolved.value, predicate.expectedValue)) {
      return false;
    }
  }
  return mismatch;
}

function matcherHasRequiredFieldMissing(mapping, matcher, event) {
  if (matcher.eventIdentifier !== event.eventIdentifier) return false;
  const pointers = [
    ...matcher.payloadPredicates.map((predicate) => predicate.jsonPointer),
    mapping.correlationJsonPointer,
    mapping.occurredAtJsonPointer,
  ];
  const missing = pointers.some((pointer) => !resolveJsonPointer(event.payload, pointer).exists);
  if (!missing) return false;
  return matcher.payloadPredicates.every((predicate) => {
    const resolved = resolveJsonPointer(event.payload, predicate.jsonPointer);
    return !resolved.exists || evaluatePredicate(event.payload, predicate);
  });
}

function validateFixtureSemantics(fixture, slot, mapping, issues) {
  const label = `${mappingKey(mapping)}:${slot}`;
  const expectedType = FIXTURE_TYPES[slot];
  expect(issues, fixture.fixtureType === expectedType, "FIXTURE_TYPE_MISMATCH", label);
  expect(issues, fixture.mappingId === mapping.mappingId, "FIXTURE_MAPPING_ID_MISMATCH", label);
  expect(
    issues,
    fixture.mappingVersion === mapping.mappingVersion,
    "FIXTURE_MAPPING_VERSION_MISMATCH",
    label,
  );
  if (
    fixture.fixtureType !== expectedType ||
    fixture.mappingId !== mapping.mappingId ||
    fixture.mappingVersion !== mapping.mappingVersion
  )
    return;

  const eventActions = actionMatches(mapping, fixture.event);
  const eventIdentity = extractIdentity(mapping, fixture.event);
  let calculated = null;

  if (slot === "raised") {
    expect(
      issues,
      eventActions.length === 1 && eventActions[0] === "RAISED",
      "RAISED_ACTION_INVALID",
      label,
    );
    expect(issues, fixture.pairedEvent === null, "RAISED_PAIRED_EVENT_NOT_NULL", label);
    expect(issues, eventIdentity.valid, "RAISED_IDENTITY_INVALID", label);
    if (eventActions.length === 1 && eventActions[0] === "RAISED" && eventIdentity.valid) {
      calculated = expectedAccepted(mapping, "RAISED", eventIdentity);
    }
  } else if (slot === "recovered") {
    const pairedActions = fixture.pairedEvent ? actionMatches(mapping, fixture.pairedEvent) : [];
    const pairedIdentity = fixture.pairedEvent
      ? extractIdentity(mapping, fixture.pairedEvent)
      : { valid: false };
    expect(
      issues,
      eventActions.length === 1 && eventActions[0] === "RECOVERED",
      "RECOVERED_ACTION_INVALID",
      label,
    );
    expect(
      issues,
      pairedActions.length === 1 && pairedActions[0] === "RAISED",
      "RECOVERED_PAIRED_RAISED_INVALID",
      label,
    );
    expect(
      issues,
      eventIdentity.valid && pairedIdentity.valid,
      "RECOVERED_IDENTITY_INVALID",
      label,
    );
    expect(
      issues,
      eventIdentity.valid &&
        pairedIdentity.valid &&
        eventIdentity.correlationKey === pairedIdentity.correlationKey,
      "RECOVERED_CORRELATION_MISMATCH",
      label,
    );
    if (
      eventActions.length === 1 &&
      eventActions[0] === "RECOVERED" &&
      pairedActions.length === 1 &&
      pairedActions[0] === "RAISED" &&
      eventIdentity.valid &&
      pairedIdentity.valid &&
      eventIdentity.correlationKey === pairedIdentity.correlationKey
    ) {
      calculated = expectedAccepted(mapping, "RECOVERED", eventIdentity, pairedIdentity);
    }
  } else if (slot === "correlationMismatch") {
    const pairedActions = fixture.pairedEvent ? actionMatches(mapping, fixture.pairedEvent) : [];
    const pairedIdentity = fixture.pairedEvent
      ? extractIdentity(mapping, fixture.pairedEvent)
      : { valid: false };
    expect(
      issues,
      eventActions.length === 1 && eventActions[0] === "RAISED",
      "CORRELATION_MISMATCH_RAISED_INVALID",
      label,
    );
    expect(
      issues,
      pairedActions.length === 1 && pairedActions[0] === "RECOVERED",
      "CORRELATION_MISMATCH_RECOVERED_INVALID",
      label,
    );
    expect(
      issues,
      eventIdentity.valid && pairedIdentity.valid,
      "CORRELATION_MISMATCH_IDENTITY_INVALID",
      label,
    );
    expect(
      issues,
      eventIdentity.valid &&
        pairedIdentity.valid &&
        eventIdentity.correlationKey !== pairedIdentity.correlationKey,
      "CORRELATION_MISMATCH_NOT_PROVEN",
      label,
    );
    if (
      eventActions.length === 1 &&
      eventActions[0] === "RAISED" &&
      pairedActions.length === 1 &&
      pairedActions[0] === "RECOVERED" &&
      eventIdentity.valid &&
      pairedIdentity.valid &&
      eventIdentity.correlationKey !== pairedIdentity.correlationKey
    ) {
      calculated = expectedRejected(
        "DEVICE_EVENT_CORRELATION_MISMATCH",
        eventIdentity,
        pairedIdentity,
        mapping,
      );
    }
  } else if (slot === "unmatched") {
    expect(issues, eventActions.length === 0, "UNMATCHED_ACTION_FOUND", label);
    expect(issues, fixture.pairedEvent === null, "UNMATCHED_PAIRED_EVENT_NOT_NULL", label);
    if (eventActions.length === 0 && fixture.pairedEvent === null) {
      calculated = expectedRejected("DEVICE_EVENT_NOT_ALLOWLISTED");
    }
  } else if (slot === "predicateTypeMismatch") {
    const proven =
      matcherHasTypeMismatch(mapping.raisedMatcher, fixture.event) ||
      matcherHasTypeMismatch(mapping.recoveredMatcher, fixture.event);
    expect(issues, eventActions.length === 0, "PREDICATE_TYPE_MISMATCH_ACTION_FOUND", label);
    expect(issues, proven, "PREDICATE_TYPE_MISMATCH_NOT_PROVEN", label);
    expect(
      issues,
      fixture.pairedEvent === null,
      "PREDICATE_TYPE_MISMATCH_PAIRED_EVENT_NOT_NULL",
      label,
    );
    if (eventActions.length === 0 && proven && fixture.pairedEvent === null) {
      calculated = expectedRejected("DEVICE_EVENT_NOT_ALLOWLISTED");
    }
  } else if (slot === "fieldMissing") {
    const proven =
      matcherHasRequiredFieldMissing(mapping, mapping.raisedMatcher, fixture.event) ||
      matcherHasRequiredFieldMissing(mapping, mapping.recoveredMatcher, fixture.event);
    const validActions = eventActions.filter(() => eventIdentity.valid);
    expect(issues, validActions.length === 0, "FIELD_MISSING_VALID_ACTION_FOUND", label);
    expect(issues, proven, "FIELD_MISSING_NOT_PROVEN", label);
    expect(issues, fixture.pairedEvent === null, "FIELD_MISSING_PAIRED_EVENT_NOT_NULL", label);
    if (validActions.length === 0 && proven && fixture.pairedEvent === null) {
      calculated = expectedRejected("DEVICE_EVENT_NOT_ALLOWLISTED");
    }
  }

  if (calculated && !expectedMatches(calculated, fixture.expected)) {
    issue(issues, "FIXTURE_EXPECTED_MISMATCH", label);
  }
}

function hasEqualsPredicate(mapping) {
  return [mapping.raisedMatcher, mapping.recoveredMatcher].some((matcher) =>
    matcher.payloadPredicates.some((predicate) => predicate.operator === "EQUALS"),
  );
}

function validateAnswers(reviewEntry, mapping, issues) {
  for (const [key, value] of Object.entries(reviewEntry.answers)) {
    if (key === "emergencyNonIgnorableConfirmed") continue;
    const normalized = typeof value === "string" ? value.trim() : "";
    expect(issues, normalized.length > 0, "ANSWER_EMPTY", `${mappingKey(mapping)}:${key}`);
    expect(
      issues,
      !PLACEHOLDER_ANSWERS.has(normalized.toUpperCase()) && !PLACEHOLDER_ANSWERS.has(normalized),
      "ANSWER_PLACEHOLDER",
      `${mappingKey(mapping)}:${key}`,
    );
  }
  expect(
    issues,
    reviewEntry.answers.emergencyNonIgnorableConfirmed === (mapping.severity === "EMERGENCY"),
    "EMERGENCY_CONFIRMATION_INVALID",
    mappingKey(mapping),
  );
}

function validateMappingStructure(allowlist, review, enabledProtocols, issues) {
  const mappingsByKey = new Map();
  for (const mapping of allowlist.mappings) {
    const key = mappingKey(mapping);
    if (mappingsByKey.has(key)) issue(issues, "DEVICE_EVENT_MAPPING_ID_DUPLICATE", key);
    else mappingsByKey.set(key, mapping);
    if (!predicatesSatisfiable(mapping.raisedMatcher.payloadPredicates)) {
      issue(issues, "MAPPING_MATCHER_UNSATISFIABLE", `${key}:RAISED`);
    }
    if (!predicatesSatisfiable(mapping.recoveredMatcher.payloadPredicates)) {
      issue(issues, "MAPPING_MATCHER_UNSATISFIABLE", `${key}:RECOVERED`);
    }
    if (matchersIntersect(mapping.raisedMatcher, mapping.recoveredMatcher)) {
      issue(issues, "MAPPING_ACTION_AMBIGUOUS", key);
    }
  }

  const reviewsByKey = new Map();
  for (const entry of review.mappingReviews) {
    const key = `${entry.mappingId}@${entry.mappingVersion}`;
    if (reviewsByKey.has(key)) issue(issues, "MAPPING_REVIEW_DUPLICATE", key);
    else reviewsByKey.set(key, entry);
  }
  const mappingKeys = [...mappingsByKey.keys()].sort();
  const reviewKeys = [...reviewsByKey.keys()].sort();
  if (!expectedMatches(mappingKeys, reviewKeys)) issue(issues, "MAPPING_REVIEW_SET_MISMATCH");

  const groups = new Map();
  for (const mapping of allowlist.mappings) {
    const group = groups.get(mapping.mappingId) ?? [];
    group.push(mapping);
    groups.set(mapping.mappingId, group);
  }
  for (const [mappingId, group] of groups) {
    group.sort((left, right) => left.mappingVersion - right.mappingVersion);
    for (let index = 1; index < group.length; index += 1) {
      const previous = group[index - 1];
      const current = group[index];
      if (
        current.mappingVersion <= previous.mappingVersion ||
        Date.parse(current.effectiveFrom) <= Date.parse(previous.effectiveFrom)
      ) {
        issue(issues, "MAPPING_EFFECTIVE_FROM_NOT_INCREASING", mappingId);
      }
    }
  }

  for (let leftIndex = 0; leftIndex < allowlist.mappings.length; leftIndex += 1) {
    const left = allowlist.mappings[leftIndex];
    for (let rightIndex = leftIndex + 1; rightIndex < allowlist.mappings.length; rightIndex += 1) {
      const right = allowlist.mappings[rightIndex];
      if (left.mappingId === right.mappingId || !scopesOverlap(left.scope, right.scope)) continue;
      const ambiguous = [left.raisedMatcher, left.recoveredMatcher].some((leftMatcher) =>
        [right.raisedMatcher, right.recoveredMatcher].some((rightMatcher) =>
          matchersIntersect(leftMatcher, rightMatcher),
        ),
      );
      if (ambiguous) {
        issue(issues, "DEVICE_EVENT_MAPPING_AMBIGUOUS", `${mappingKey(left)},${mappingKey(right)}`);
      }
    }
  }

  for (const [key, entry] of reviewsByKey) {
    const mapping = mappingsByKey.get(key);
    if (!mapping) continue;
    validateAnswers(entry, mapping, issues);
    const protocolKey = `${entry.protocolId}@${entry.protocolVersion}`;
    if (!enabledProtocols.has(protocolKey)) {
      issue(issues, "PROTOCOL_REFERENCE_NOT_ENABLED", `${key}:${protocolKey}`);
    }
  }
  return { mappingsByKey, reviewsByKey };
}

function validateMappingFixtures(entry, mapping, root, validators, issues) {
  const required = [...BASE_FIXTURE_KEYS];
  if (hasEqualsPredicate(mapping)) required.push("predicateTypeMismatch");
  for (const key of required) {
    if (!entry.fixtures[key]) issue(issues, "FIXTURE_REQUIRED", `${mappingKey(mapping)}:${key}`);
  }
  for (const [slot, artifact] of Object.entries(entry.fixtures)) {
    if (!artifact) continue;
    const label = `${mappingKey(mapping)}:${slot}`;
    const file = validateArtifact(artifact, `fixture:${label}`, root, issues);
    const fixture = readValidatedJson(file, validators.fixture, "FIXTURE", issues);
    if (fixture) validateFixtureSemantics(fixture, slot, mapping, issues);
  }
}

function defaultProtocolVerifier(protocolFile, root, issues) {
  if (path.resolve(root) !== path.resolve(REPO_ROOT)) {
    issue(issues, "PROTOCOL_EVIDENCE_VERIFIER_ROOT_INVALID");
    return;
  }
  const relative = path.relative(REPO_ROOT, protocolFile).replaceAll(path.sep, "/");
  const result = spawnSync(process.execPath, [PROTOCOL_VERIFIER_PATH, "--file", relative], {
    cwd: REPO_ROOT,
    encoding: "utf8",
    windowsHide: true,
  });
  if (result.error || result.status !== 0) {
    issue(issues, "PROTOCOL_EVIDENCE_VERIFIER_FAILED", relative);
  }
}

function validateReview(review, root, validators, dependencies = {}) {
  const issues = [];
  if (!validators.review(review)) {
    issue(issues, "REVIEW_SCHEMA_INVALID", validators.review.errors?.[0]?.instancePath ?? "");
    return { issues, mappings: 0, enabled: 0, qualification: "INVALID" };
  }

  const emptyDraft =
    review.documentStatus === "DRAFT" &&
    review.allowlistArtifact === null &&
    review.protocolEvidenceArtifact === null &&
    review.mappingReviews.length === 0 &&
    review.reviewApproval === null;
  if (emptyDraft) return { issues, mappings: 0, enabled: 0, qualification: "DRAFT_EMPTY" };

  if (review.documentStatus === "DRAFT") {
    expect(issues, review.reviewApproval === null, "DRAFT_REVIEW_APPROVAL_NOT_NULL");
    if (
      review.allowlistArtifact === null ||
      review.protocolEvidenceArtifact === null ||
      review.mappingReviews.length === 0
    ) {
      issue(issues, "DRAFT_PREFLIGHT_INCOMPLETE");
      return {
        issues,
        mappings: review.mappingReviews.length,
        enabled: 0,
        qualification: "INVALID",
      };
    }
  }

  const allowlistFile = validateArtifact(
    review.allowlistArtifact,
    "allowlistArtifact",
    root,
    issues,
  );
  const protocolFile = validateArtifact(
    review.protocolEvidenceArtifact,
    "protocolEvidenceArtifact",
    root,
    issues,
  );
  const allowlist = readValidatedJson(allowlistFile, validators.allowlist, "ALLOWLIST", issues);
  const evidence = readValidatedJson(protocolFile, validators.protocol, "PROTOCOL", issues);
  if (!allowlist || !evidence) {
    return {
      issues,
      mappings: review.mappingReviews.length,
      enabled: 0,
      qualification: "INVALID",
    };
  }

  if (review.documentStatus === "COMPLETE") {
    expect(issues, allowlist.documentStatus === "APPROVED", "ALLOWLIST_STATUS_INVALID");
  } else if (review.documentStatus === "RETIRED") {
    expect(issues, allowlist.documentStatus === "RETIRED", "ALLOWLIST_STATUS_INVALID");
  } else {
    expect(
      issues,
      allowlist.documentStatus === "DRAFT" || allowlist.documentStatus === "APPROVED",
      "ALLOWLIST_STATUS_INVALID",
    );
  }
  if (allowlist.documentStatus === "DRAFT") {
    expect(issues, allowlist.approval === null, "ALLOWLIST_DRAFT_APPROVAL_NOT_NULL");
    expect(issues, allowlist.mappings.length > 0, "ALLOWLIST_DRAFT_MAPPINGS_EMPTY");
  } else {
    let actual;
    try {
      actual = allowlistApprovalContentSha256(allowlist);
    } catch {
      issue(issues, "ALLOWLIST_APPROVAL_CANONICALIZATION_FAILED");
    }
    if (actual && actual !== allowlist.approval.contentSha256) {
      issue(issues, "ALLOWLIST_APPROVAL_CONTENT_SHA256_MISMATCH");
    }
  }

  expect(issues, evidence.documentStatus === "APPROVED", "PROTOCOL_EVIDENCE_NOT_APPROVED");
  let protocolHash;
  try {
    protocolHash = protocolApprovalContentSha256(evidence);
  } catch {
    issue(issues, "PROTOCOL_APPROVAL_CANONICALIZATION_FAILED");
  }
  if (protocolHash && protocolHash !== evidence.approval?.contentSha256) {
    issue(issues, "PROTOCOL_APPROVAL_CONTENT_SHA256_MISMATCH");
  }
  const enabledProtocols = new Map(
    evidence.protocols
      .filter((protocol) => protocol.decision === "ENABLED" && protocol.subjectMode === "DIRECT")
      .map((protocol) => [`${protocol.protocolId}@${protocol.protocolVersion}`, protocol]),
  );
  expect(issues, enabledProtocols.size > 0, "PROTOCOL_EVIDENCE_NO_ENABLED_DIRECT");
  const protocolVerifier = dependencies.protocolVerifier ?? defaultProtocolVerifier;
  if (protocolFile) protocolVerifier(protocolFile, root, issues);

  if (review.documentStatus === "COMPLETE" || review.documentStatus === "RETIRED") {
    let actual;
    try {
      actual = reviewApprovalContentSha256(review);
    } catch {
      issue(issues, "REVIEW_APPROVAL_CANONICALIZATION_FAILED");
    }
    if (actual && actual !== review.reviewApproval.contentSha256) {
      issue(issues, "REVIEW_APPROVAL_CONTENT_SHA256_MISMATCH");
    }
  }

  const { mappingsByKey, reviewsByKey } = validateMappingStructure(
    allowlist,
    review,
    enabledProtocols,
    issues,
  );
  for (const [key, entry] of reviewsByKey) {
    const mapping = mappingsByKey.get(key);
    if (mapping) validateMappingFixtures(entry, mapping, root, validators, issues);
  }

  return {
    issues,
    mappings: allowlist.mappings.length,
    enabled: enabledProtocols.size,
    qualification: review.documentStatus === "DRAFT" ? "DRAFT_PREFLIGHT" : review.documentStatus,
  };
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

function resolveReviewPath(value) {
  if (value === null) return DEFAULT_REVIEW_PATH;
  if (!isSafeRepositoryJsonPath(value)) return null;
  const full = path.resolve(REPO_ROOT, ...value.split("/"));
  return isInside(REPO_ROOT, full) ? full : null;
}

function writeJson(file, value) {
  fs.mkdirSync(path.dirname(file), { recursive: true });
  fs.writeFileSync(file, `${JSON.stringify(value, null, 2)}\n`, "utf8");
}

function artifactFor(root, relative) {
  return { path: relative, sha256: sha256File(path.join(root, ...relative.split("/"))) };
}

function syntheticMapping(overrides = {}) {
  return {
    mappingId: "synthetic.alarm",
    mappingVersion: 1,
    contractMajor: 1,
    lifecycle: "PAIRED",
    scope: {
      scopeType: "PRODUCT",
      productIdentification: "synthetic-product",
      modelIdentifier: null,
    },
    alarmTypeKey: "synthetic.alarm",
    raisedMatcher: {
      eventIdentifier: "alarm.raised",
      payloadPredicates: [{ jsonPointer: "/state", operator: "EQUALS", expectedValue: "ALARM" }],
    },
    recoveredMatcher: {
      eventIdentifier: "alarm.recovered",
      payloadPredicates: [{ jsonPointer: "/state", operator: "EQUALS", expectedValue: "NORMAL" }],
    },
    severity: "IMPORTANT",
    correlationJsonPointer: "/cycleId",
    occurredAtJsonPointer: "/occurredAt",
    oneShotPolicy: "REJECT",
    upgradePolicy: "PIN_ORIGINAL_VERSION_UNTIL_RECOVERED",
    effectiveFrom: "2026-08-26T00:00:00.000+08:00",
    ...overrides,
  };
}

function syntheticEvent(eventIdentifier, state, cycleId, occurredAt, overrides = {}) {
  return {
    eventIdentifier,
    payload: { state, cycleId, occurredAt, ...overrides },
  };
}

function syntheticFixture(type, mapping, event, pairedEvent, expected) {
  return {
    schemaVersion: "1.0",
    fixtureType: type,
    mappingId: mapping.mappingId,
    mappingVersion: mapping.mappingVersion,
    captureId: `${mapping.mappingId}.${type.toLowerCase().replaceAll("_", "-")}`,
    event,
    pairedEvent,
    expected,
  };
}

function buildSyntheticPackage(root) {
  const mapping = syntheticMapping();
  const raisedEvent = syntheticEvent(
    "alarm.raised",
    "ALARM",
    "cycle-1",
    "2026-08-26T10:00:00.000+08:00",
  );
  const recoveredEvent = syntheticEvent(
    "alarm.recovered",
    "NORMAL",
    "cycle-1",
    "2026-08-26T10:05:00.000+08:00",
  );
  const raisedIdentity = extractIdentity(mapping, raisedEvent);
  const recoveredIdentity = extractIdentity(mapping, recoveredEvent);
  const fixtureValues = {
    raised: syntheticFixture(
      "RAISED",
      mapping,
      raisedEvent,
      null,
      expectedAccepted(mapping, "RAISED", raisedIdentity),
    ),
    recovered: syntheticFixture(
      "RECOVERED",
      mapping,
      recoveredEvent,
      raisedEvent,
      expectedAccepted(mapping, "RECOVERED", recoveredIdentity, raisedIdentity),
    ),
    unmatched: syntheticFixture(
      "UNMATCHED",
      mapping,
      syntheticEvent("business.event", "OK", "cycle-1", "2026-08-26T10:01:00.000+08:00"),
      null,
      expectedRejected("DEVICE_EVENT_NOT_ALLOWLISTED"),
    ),
    correlationMismatch: (() => {
      const otherRecovered = syntheticEvent(
        "alarm.recovered",
        "NORMAL",
        "cycle-2",
        "2026-08-26T10:05:00.000+08:00",
      );
      return syntheticFixture(
        "CORRELATION_MISMATCH",
        mapping,
        raisedEvent,
        otherRecovered,
        expectedRejected(
          "DEVICE_EVENT_CORRELATION_MISMATCH",
          raisedIdentity,
          extractIdentity(mapping, otherRecovered),
          mapping,
        ),
      );
    })(),
    predicateTypeMismatch: syntheticFixture(
      "PREDICATE_TYPE_MISMATCH",
      mapping,
      syntheticEvent("alarm.raised", true, "cycle-1", "2026-08-26T10:00:00.000+08:00"),
      null,
      expectedRejected("DEVICE_EVENT_NOT_ALLOWLISTED"),
    ),
    fieldMissing: syntheticFixture(
      "FIELD_MISSING",
      mapping,
      {
        eventIdentifier: "alarm.raised",
        payload: { state: "ALARM", cycleId: "cycle-1" },
      },
      null,
      expectedRejected("DEVICE_EVENT_NOT_ALLOWLISTED"),
    ),
  };
  const fixtureArtifacts = {};
  for (const [key, value] of Object.entries(fixtureValues)) {
    const relative = `fixtures/${key}.json`;
    writeJson(path.join(root, ...relative.split("/")), value);
    fixtureArtifacts[key] = artifactFor(root, relative);
  }

  const allowlist = {
    schemaVersion: "1.0",
    canonicalizationVersion: "jcs-rfc8785-v1",
    documentStatus: "APPROVED",
    revision: 1,
    mappings: [mapping],
    approval: {
      approvedBy: "synthetic-product-owner",
      approvedAt: "2026-08-26T11:00:00+08:00",
      decisionRef: "synthetic-only",
      contentSha256: `sha256:${"0".repeat(64)}`,
    },
  };
  allowlist.approval.contentSha256 = allowlistApprovalContentSha256(allowlist);
  writeJson(path.join(root, "allowlist.json"), allowlist);

  const protocol = {
    protocolId: "synthetic.protocol",
    protocolVersion: "1.0",
    transport: "MQTT",
    codecClass: "SyntheticCodec",
    directEventTopicPattern: "/iot/{product}/{device}/event/upstream/report/{identifier}",
    subjectMode: "DIRECT",
    requestIdEvidence: {
      wireField: "id",
      deviceGenerated: true,
      retryStable: true,
      uniquenessScope: "TENANT_PRODUCT_DEVICE",
      uniquenessWindowSeconds: 86400,
      minUtf8Bytes: 1,
      maxUtf8Bytes: 64,
      controlCharactersRejected: true,
    },
    occurredAtEvidence: {
      wireField: "occurredAt",
      deviceGenerated: true,
      format: "RFC3339_OFFSET_MILLIS",
      originalOffsetPreserved: true,
      millisecondPrecision: true,
      clockSource: "device-clock",
      maxFutureSkewSeconds: 300,
      maxHistoryAgeSeconds: 86400,
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
    fixtures: {
      original: { path: "protocol/original.json", sha256: `sha256:${"a".repeat(64)}` },
      retry: { path: "protocol/retry.json", sha256: `sha256:${"b".repeat(64)}` },
      collision: { path: "protocol/collision.json", sha256: `sha256:${"c".repeat(64)}` },
      missingRequestId: {
        path: "protocol/missing-request-id.json",
        sha256: `sha256:${"d".repeat(64)}`,
      },
      invalidOccurredAt: {
        path: "protocol/invalid-occurred-at.json",
        sha256: `sha256:${"e".repeat(64)}`,
      },
    },
    decision: "ENABLED",
    reasonCodes: [],
  };
  const evidence = {
    schemaVersion: "1.0",
    canonicalizationVersion: "jcs-rfc8785-v1",
    documentStatus: "APPROVED",
    revision: 1,
    protocols: [protocol],
    approval: {
      ownerRole: "synthetic-protocol-owner",
      approvedBy: "synthetic-owner",
      approvedAt: "2026-08-26T10:30:00+08:00",
      decisionRef: "synthetic-only",
      contentSha256: `sha256:${"0".repeat(64)}`,
    },
  };
  evidence.approval.contentSha256 = protocolApprovalContentSha256(evidence);
  writeJson(path.join(root, "protocol.json"), evidence);

  const review = {
    schemaVersion: "1.0",
    canonicalizationVersion: "jcs-rfc8785-v1",
    documentStatus: "COMPLETE",
    revision: 1,
    allowlistArtifact: artifactFor(root, "allowlist.json"),
    protocolEvidenceArtifact: artifactFor(root, "protocol.json"),
    mappingReviews: [
      {
        mappingId: mapping.mappingId,
        mappingVersion: mapping.mappingVersion,
        protocolId: protocol.protocolId,
        protocolVersion: protocol.protocolVersion,
        answers: {
          alarmRationale: "需要形成可恢复的处置闭环",
          raisedRule: "alarm.raised 且 state=ALARM",
          recoveredRule: "alarm.recovered 且 state=NORMAL",
          correlationRationale: "cycleId 在 raised/recovered 间稳定",
          severityRationale: "按产品处置时限固定为 IMPORTANT",
          emergencyNonIgnorableConfirmed: false,
          upgradeClosurePlan: "旧活动周期固定原 mappingVersion 直至恢复",
        },
        fixtures: fixtureArtifacts,
      },
    ],
    reviewApproval: {
      reviewedBy: "synthetic-reviewer",
      reviewedAt: "2026-08-26T11:30:00+08:00",
      decisionRef: "synthetic-only",
      contentSha256: `sha256:${"0".repeat(64)}`,
    },
  };
  review.reviewApproval.contentSha256 = reviewApprovalContentSha256(review);
  return { review, allowlist, evidence, mapping, fixtureValues };
}

function runSelfTests(validators) {
  let passed = 0;
  const dependencies = { protocolVerifier: () => {} };
  const directCase = (condition, name) => {
    if (!condition) throw new Error(`SELF_TEST_FAILED:${name}`);
    passed += 1;
  };
  const packageCase = (name, mutate, expectedCode = null, expectedQualification = null) => {
    const root = fs.mkdtempSync(path.join(os.tmpdir(), "easyaiot-g2-04-"));
    try {
      const synthetic = buildSyntheticPackage(root);
      mutate?.(synthetic, root);
      const result = validateReview(synthetic.review, root, validators, dependencies);
      if (expectedCode) {
        if (!result.issues.some((entry) => entry.code === expectedCode)) {
          throw new Error(
            `SELF_TEST_FAILED:${name}:expected=${expectedCode}:actual=${result.issues
              .map((entry) => `${entry.code}${entry.target ? `@${entry.target}` : ""}`)
              .join(",")}`,
          );
        }
        passed += 1;
      } else {
        directCase(result.issues.length === 0, name);
        if (expectedQualification) {
          directCase(result.qualification === expectedQualification, `${name}:qualification`);
        }
      }
    } finally {
      fs.rmSync(root, { recursive: true, force: true });
    }
  };

  const emptyDraft = readJson(DEFAULT_REVIEW_PATH);
  const emptyResult = validateReview(emptyDraft, REPO_ROOT, validators, dependencies);
  directCase(emptyResult.issues.length === 0, "draft-empty");
  directCase(emptyResult.qualification === "DRAFT_EMPTY", "draft-empty-qualification");
  packageCase("complete", null, null, "COMPLETE");
  packageCase(
    "draft-preflight",
    ({ review }) => {
      review.documentStatus = "DRAFT";
      review.reviewApproval = null;
    },
    null,
    "DRAFT_PREFLIGHT",
  );
  const partialDraft = structuredClone(emptyDraft);
  partialDraft.mappingReviews = [
    {
      mappingId: "synthetic.alarm",
      mappingVersion: 1,
      protocolId: "synthetic.protocol",
      protocolVersion: "1.0",
      answers: {
        alarmRationale: "x",
        raisedRule: "x",
        recoveredRule: "x",
        correlationRationale: "x",
        severityRationale: "x",
        emergencyNonIgnorableConfirmed: false,
        upgradeClosurePlan: "x",
      },
      fixtures: {
        raised: null,
        recovered: null,
        unmatched: null,
        correlationMismatch: null,
        predicateTypeMismatch: null,
        fieldMissing: null,
      },
    },
  ];
  const partialResult = validateReview(partialDraft, REPO_ROOT, validators, dependencies);
  directCase(
    partialResult.issues.some((entry) => entry.code === "DRAFT_PREFLIGHT_INCOMPLETE"),
    "draft-partial",
  );
  packageCase(
    "review-hash",
    ({ review }) => {
      review.reviewApproval.contentSha256 = `sha256:${"f".repeat(64)}`;
    },
    "REVIEW_APPROVAL_CONTENT_SHA256_MISMATCH",
  );
  packageCase(
    "artifact-hash",
    ({ review }) => {
      review.allowlistArtifact.sha256 = `sha256:${"f".repeat(64)}`;
    },
    "ARTIFACT_SHA256_MISMATCH",
  );
  packageCase(
    "allowlist-approval-hash",
    ({ review, allowlist }, root) => {
      allowlist.approval.contentSha256 = `sha256:${"f".repeat(64)}`;
      writeJson(path.join(root, "allowlist.json"), allowlist);
      review.allowlistArtifact = artifactFor(root, "allowlist.json");
      review.reviewApproval.contentSha256 = reviewApprovalContentSha256(review);
    },
    "ALLOWLIST_APPROVAL_CONTENT_SHA256_MISMATCH",
  );
  packageCase(
    "mapping-set",
    ({ review }) => {
      review.mappingReviews[0].mappingVersion = 2;
      review.reviewApproval.contentSha256 = reviewApprovalContentSha256(review);
    },
    "MAPPING_REVIEW_SET_MISMATCH",
  );
  packageCase(
    "protocol-reference",
    ({ review }) => {
      review.mappingReviews[0].protocolVersion = "2.0";
      review.reviewApproval.contentSha256 = reviewApprovalContentSha256(review);
    },
    "PROTOCOL_REFERENCE_NOT_ENABLED",
  );
  packageCase(
    "effective-from",
    ({ review, allowlist, mapping }, root) => {
      const v2 = structuredClone(mapping);
      v2.mappingVersion = 2;
      v2.effectiveFrom = mapping.effectiveFrom;
      allowlist.mappings.push(v2);
      allowlist.approval.contentSha256 = allowlistApprovalContentSha256(allowlist);
      writeJson(path.join(root, "allowlist.json"), allowlist);
      review.allowlistArtifact = artifactFor(root, "allowlist.json");
      const secondReview = structuredClone(review.mappingReviews[0]);
      secondReview.mappingVersion = 2;
      review.mappingReviews.push(secondReview);
      review.reviewApproval.contentSha256 = reviewApprovalContentSha256(review);
    },
    "MAPPING_EFFECTIVE_FROM_NOT_INCREASING",
  );
  packageCase(
    "scope-overlap",
    ({ review, allowlist, mapping }, root) => {
      const other = structuredClone(mapping);
      other.mappingId = "synthetic.other";
      other.alarmTypeKey = "synthetic.other";
      allowlist.mappings.push(other);
      allowlist.approval.contentSha256 = allowlistApprovalContentSha256(allowlist);
      writeJson(path.join(root, "allowlist.json"), allowlist);
      const otherReview = structuredClone(review.mappingReviews[0]);
      otherReview.mappingId = other.mappingId;
      review.mappingReviews.push(otherReview);
      review.allowlistArtifact = artifactFor(root, "allowlist.json");
      review.reviewApproval.contentSha256 = reviewApprovalContentSha256(review);
    },
    "DEVICE_EVENT_MAPPING_AMBIGUOUS",
  );
  packageCase(
    "action-ambiguity",
    ({ review, allowlist }, root) => {
      allowlist.mappings[0].recoveredMatcher = structuredClone(allowlist.mappings[0].raisedMatcher);
      allowlist.approval.contentSha256 = allowlistApprovalContentSha256(allowlist);
      writeJson(path.join(root, "allowlist.json"), allowlist);
      review.allowlistArtifact = artifactFor(root, "allowlist.json");
      review.reviewApproval.contentSha256 = reviewApprovalContentSha256(review);
    },
    "MAPPING_ACTION_AMBIGUOUS",
  );
  packageCase(
    "field-missing-required",
    ({ review }) => {
      review.mappingReviews[0].fixtures.fieldMissing = null;
      review.reviewApproval.contentSha256 = reviewApprovalContentSha256(review);
    },
    "REVIEW_SCHEMA_INVALID",
  );
  packageCase(
    "predicate-required",
    ({ review }) => {
      review.mappingReviews[0].fixtures.predicateTypeMismatch = null;
      review.reviewApproval.contentSha256 = reviewApprovalContentSha256(review);
    },
    "FIXTURE_REQUIRED",
  );
  packageCase(
    "expected-forgery",
    ({ review, fixtureValues }, root) => {
      fixtureValues.raised.expected.accepted = false;
      writeJson(path.join(root, "fixtures", "raised.json"), fixtureValues.raised);
      review.mappingReviews[0].fixtures.raised = artifactFor(root, "fixtures/raised.json");
      review.reviewApproval.contentSha256 = reviewApprovalContentSha256(review);
    },
    "FIXTURE_EXPECTED_MISMATCH",
  );
  packageCase(
    "answer-placeholder",
    ({ review }) => {
      review.mappingReviews[0].answers.alarmRationale = "TBD";
      review.reviewApproval.contentSha256 = reviewApprovalContentSha256(review);
    },
    "ANSWER_PLACEHOLDER",
  );
  packageCase(
    "emergency-confirmation",
    ({ review }) => {
      review.mappingReviews[0].answers.emergencyNonIgnorableConfirmed = true;
      review.reviewApproval.contentSha256 = reviewApprovalContentSha256(review);
    },
    "EMERGENCY_CONFIRMATION_INVALID",
  );
  packageCase(
    "time-invalid",
    ({ review, fixtureValues }, root) => {
      fixtureValues.raised.event.payload.occurredAt = "2026-02-30T10:00:00.000+08:00";
      writeJson(path.join(root, "fixtures", "raised.json"), fixtureValues.raised);
      review.mappingReviews[0].fixtures.raised = artifactFor(root, "fixtures/raised.json");
      review.reviewApproval.contentSha256 = reviewApprovalContentSha256(review);
    },
    "RAISED_IDENTITY_INVALID",
  );
  packageCase(
    "correlation-invalid",
    ({ review, fixtureValues }, root) => {
      fixtureValues.raised.event.payload.cycleId = " ";
      writeJson(path.join(root, "fixtures", "raised.json"), fixtureValues.raised);
      review.mappingReviews[0].fixtures.raised = artifactFor(root, "fixtures/raised.json");
      review.reviewApproval.contentSha256 = reviewApprovalContentSha256(review);
    },
    "RAISED_IDENTITY_INVALID",
  );
  packageCase(
    "predicate-type-proof",
    ({ review, fixtureValues }, root) => {
      fixtureValues.predicateTypeMismatch.event.payload.state = "WRONG";
      writeJson(
        path.join(root, "fixtures", "predicateTypeMismatch.json"),
        fixtureValues.predicateTypeMismatch,
      );
      review.mappingReviews[0].fixtures.predicateTypeMismatch = artifactFor(
        root,
        "fixtures/predicateTypeMismatch.json",
      );
      review.reviewApproval.contentSha256 = reviewApprovalContentSha256(review);
    },
    "PREDICATE_TYPE_MISMATCH_NOT_PROVEN",
  );
  packageCase(
    "field-missing-proof",
    ({ review, fixtureValues }, root) => {
      fixtureValues.fieldMissing.event.payload.occurredAt = "2026-08-26T10:00:00.000+08:00";
      writeJson(path.join(root, "fixtures", "fieldMissing.json"), fixtureValues.fieldMissing);
      review.mappingReviews[0].fixtures.fieldMissing = artifactFor(
        root,
        "fixtures/fieldMissing.json",
      );
      review.reviewApproval.contentSha256 = reviewApprovalContentSha256(review);
    },
    "FIELD_MISSING_NOT_PROVEN",
  );
  packageCase("protocol-verifier", ({ review }, root) => {
    const result = validateReview(review, root, validators, {
      protocolVerifier: (_file, _root, issues) =>
        issue(issues, "PROTOCOL_EVIDENCE_VERIFIER_FAILED"),
    });
    if (!result.issues.some((entry) => entry.code === "PROTOCOL_EVIDENCE_VERIFIER_FAILED")) {
      throw new Error("protocol verifier failure not propagated");
    }
  });
  directCase(
    evaluatePredicate(
      { nullable: null },
      {
        jsonPointer: "/nullable",
        operator: "EXISTS",
        expectedValue: null,
      },
    ),
    "exists-null",
  );
  directCase(
    !evaluatePredicate(
      { value: 1 },
      {
        jsonPointer: "/value",
        operator: "EQUALS",
        expectedValue: "1",
      },
    ),
    "equals-type-strict",
  );
  directCase(
    matcherHasTypeMismatch(
      {
        eventIdentifier: "type.test",
        payloadPredicates: [{ jsonPointer: "/value", operator: "EQUALS", expectedValue: null }],
      },
      { eventIdentifier: "type.test", payload: { value: {} } },
    ),
    "equals-null-object-type-strict",
  );
  directCase(
    !predicatesSatisfiable([
      { jsonPointer: "/parent", operator: "EQUALS", expectedValue: 1 },
      { jsonPointer: "/parent/child", operator: "EXISTS", expectedValue: null },
    ]),
    "matcher-ancestor-contradiction",
  );
  directCase(resolveJsonPointer(["x"], "/00").invalid, "array-index-leading-zero");
  directCase(resolveJsonPointer({ "a/b": { "~": 1 } }, "/a~1b/~0").value === 1, "pointer-escape");
  directCase(parseArgs(["--unknown"]).error === "ARGUMENT_INVALID", "args-unknown");
  directCase(parseArgs(["--file"]).error === "ARGUMENT_VALUE_MISSING", "args-missing-value");
  directCase(
    parseArgs(["--self-test", "--self-test"]).error === "ARGUMENT_DUPLICATE",
    "args-duplicate",
  );
  directCase(resolveReviewPath("../outside.json") === null, "path-traversal");
  directCase(resolveReviewPath("C:/outside.json") === null, "path-absolute");
  directCase(resolveReviewPath(".git/config.json") === null, "path-git");
  const protocolVerifierIssues = [];
  defaultProtocolVerifier(DEFAULT_PROTOCOL_EVIDENCE_PATH, REPO_ROOT, protocolVerifierIssues);
  directCase(protocolVerifierIssues.length === 0, "protocol-verifier-invocation");

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

  const reviewPath = resolveReviewPath(parsed.file);
  if (!reviewPath) {
    process.stderr.write(`FAIL code=REVIEW_PATH_INVALID value=${parsed.file}\n`);
    process.exitCode = 2;
    return;
  }
  if (!fs.existsSync(reviewPath) || !fs.statSync(reviewPath).isFile()) {
    process.stderr.write(`FAIL code=REVIEW_PATH_MISSING value=${parsed.file ?? "default"}\n`);
    process.exitCode = 1;
    return;
  }
  if (!isInside(fs.realpathSync(REPO_ROOT), fs.realpathSync(reviewPath))) {
    process.stderr.write(
      `FAIL code=REVIEW_PATH_OUTSIDE_REPOSITORY value=${parsed.file ?? "default"}\n`,
    );
    process.exitCode = 2;
    return;
  }

  let review;
  try {
    review = readJson(reviewPath);
  } catch {
    process.stderr.write(`FAIL code=REVIEW_JSON_INVALID value=${parsed.file ?? "default"}\n`);
    process.exitCode = 1;
    return;
  }
  const result = validateReview(review, REPO_ROOT, validators);
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

  const displayPath = path.relative(REPO_ROOT, reviewPath).replaceAll(path.sep, "/");
  process.stdout.write(
    `PASS review=${displayPath} mappings=${result.mappings} enabled=${result.enabled} qualification=${result.qualification}\n`,
  );
  if (parsed.selfTest && !runSelfTests(validators)) process.exitCode = 1;
}

main();
