#!/usr/bin/env node
/**
 * PRD-02 告警来源迁移防漂移门禁。
 *
 * 本脚本放在 WEB/scripts/ 仅为复用仓库 Node.js 执行环境，不属于 WEB
 * 运行时或构建产物。移动或删除前必须重新评审 P02-M2-02C0。
 * 所有路径均从 import.meta.url 推导，不依赖调用方当前目录。
 */

import fs from 'node:fs';
import os from 'node:os';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const SCRIPT_DIR = path.dirname(fileURLToPath(import.meta.url));
const REPO_ROOT = path.resolve(SCRIPT_DIR, '..', '..');
const DEFAULT_MANIFEST = path.join(
  REPO_ROOT,
  '.doc',
  '技术设计',
  '电力运维云平台',
  'assets',
  'td006-migration',
  'alarm-source-inventory.json',
);

const SOURCE_TYPES = ['THRESHOLD', 'DEVICE_EVENT', 'VIDEO', 'AI', 'RUNTIME'];
const LEGACY_IDS = [
  'DEVICE_ALARM_LOG_SUCCESS',
  'THRESHOLD_WEB_OPEN_READ',
  'VIDEO_WEB_READ_AND_DELETE',
  'VIDEO_APP_READ_AND_DELETE',
  'VIDEO_LEGACY_KAFKA_DIRECT_PERSIST',
  'VIDEO_ALERT_HTTP_API',
  'ALGORITHM_NOTIFICATION_FORWARD',
];
const EXTERNAL_OPEN = [
  'LEGACY_ROW_COUNTS_AND_TENANT_SITE_DISTRIBUTION',
  'SOURCE_RATES_RETRY_OUT_OF_ORDER_AND_BACKLOG',
  'PHYSICAL_TOPIC_GROUP_PARTITION_ACK_RETRY_DLQ',
  'WEB_APP_FEIGN_THIRD_PARTY_CALL_VOLUMES_AND_OWNERS',
  'BACKFILL_BATCH_THROTTLE_DIFFERENCE_OBSERVATION_ROLLBACK',
  'STANDARD_FULL_TARGET_SCALE_AND_REPEATABLE_BENCHMARK',
];
const IDENTITY_KEYS = [
  'messageId',
  'sourceId',
  'cycleIdentity',
  'tenant',
  'site',
  'device',
  'eventTime',
  'quality',
];

function stableSet(values) {
  return [...new Set(values)].sort();
}

function sameSet(actual, expected) {
  return JSON.stringify(stableSet(actual)) === JSON.stringify(stableSet(expected));
}

function countOccurrences(content, needle) {
  if (!needle) return 0;
  let count = 0;
  let offset = 0;
  while ((offset = content.indexOf(needle, offset)) !== -1) {
    count += 1;
    offset += needle.length;
  }
  return count;
}

function isSafeRepositoryPath(value) {
  return typeof value === 'string'
    && value.length > 0
    && !value.includes('\\')
    && !value.startsWith('/')
    && !/^[A-Za-z]:/.test(value)
    && !value.split('/').includes('..');
}

function isInside(root, target) {
  const relative = path.relative(root, target);
  return relative !== '' && !relative.startsWith(`..${path.sep}`) && relative !== '..'
    && !path.isAbsolute(relative);
}

function walkFiles(root) {
  if (!fs.existsSync(root)) return [];
  const output = [];
  const stack = [root];
  while (stack.length > 0) {
    const current = stack.pop();
    for (const entry of fs.readdirSync(current, { withFileTypes: true })) {
      const full = path.join(current, entry.name);
      const relative = path.relative(root, full).replaceAll(path.sep, '/');
      if (entry.isDirectory()) {
        if (entry.name.startsWith('.') || entry.name === '__pycache__'
          || entry.name === 'tests' || entry.name === 'test') continue;
        stack.push(full);
        continue;
      }
      if (/^test[_-]/i.test(entry.name) || /[_-]test\./i.test(entry.name)) continue;
      if (!/\.(?:py|java|cpp|cc|c|h|hpp|js|mjs|ts)$/i.test(entry.name)) continue;
      output.push({ full, relative });
    }
  }
  return output;
}

function scanAiPublishers(repoRoot) {
  const patterns = [
    'mqtt/iot-alert-notification',
    'mqtt/iot-snapshot-alert',
    'publish_alert(',
    '/device/alarm',
  ];
  const hits = [];
  for (const file of walkFiles(path.join(repoRoot, 'AI'))) {
    const content = fs.readFileSync(file.full, 'utf8');
    for (const pattern of patterns) {
      if (content.includes(pattern)) hits.push({ path: `AI/${file.relative}`, pattern });
    }
  }
  return hits;
}

function validateInventory(inventory, repoRoot = REPO_ROOT) {
  const issues = [];
  let checks = 0;
  const check = (condition, code, detail = '') => {
    checks += 1;
    if (!condition) issues.push({ code, detail });
  };

  check(inventory && typeof inventory === 'object' && !Array.isArray(inventory),
    'MANIFEST_OBJECT');
  if (!inventory || typeof inventory !== 'object' || Array.isArray(inventory)) {
    return { issues, checks };
  }

  check(inventory.inventoryVersion === '1.0.0', 'VERSION_INVALID');
  check(inventory.evidenceDate === '2026-08-25', 'EVIDENCE_DATE_INVALID');
  check(inventory.scope === 'repository-static', 'SCOPE_INVALID');
  check(inventory.productionFacts === 'OPEN_EXTERNAL', 'PRODUCTION_FACTS_INVALID');
  check(inventory.baselines?.platformPlan === '1.5.0', 'BASELINE_PLATFORM_INVALID');
  check(inventory.baselines?.developmentConstitution === '1.6.0',
    'BASELINE_CONSTITUTION_INVALID');
  check(inventory.baselines?.prd === 'PRD-02 1.2.2', 'BASELINE_PRD_INVALID');

  const stages = inventory.stages ?? {};
  check(stages.C0 === 'APPROVED_LOCAL_STATIC_ONLY', 'STAGE_C0_INVALID');
  for (const stage of ['C1', 'C2', 'C3', 'C4', 'productionTransport', 'productionCapability']) {
    check(stages[stage] === 'CLOSED', 'STAGE_GATED', stage);
  }

  const sources = Array.isArray(inventory.sources) ? inventory.sources : [];
  const sourceTypes = sources.map((item) => item?.sourceType);
  check(sources.length === SOURCE_TYPES.length && sameSet(sourceTypes, SOURCE_TYPES),
    'SOURCE_SET_INVALID');
  check(new Set(sourceTypes).size === sourceTypes.length, 'SOURCE_DUPLICATE');

  const validateEvidence = (item, owner) => {
    check(item && typeof item === 'object', 'EVIDENCE_OBJECT', owner);
    if (!item || typeof item !== 'object') return;
    check(typeof item.role === 'string' && item.role.length > 0, 'EVIDENCE_ROLE', owner);
    check(isSafeRepositoryPath(item.path), 'PATH_INVALID', owner);
    if (!isSafeRepositoryPath(item.path)) return;
    const fullPath = path.resolve(repoRoot, ...item.path.split('/'));
    check(isInside(repoRoot, fullPath), 'PATH_OUTSIDE_REPOSITORY', item.path);
    check(fs.existsSync(fullPath) && fs.statSync(fullPath).isFile(), 'PATH_MISSING', item.path);
    if (!fs.existsSync(fullPath) || !fs.statSync(fullPath).isFile()) return;
    const content = fs.readFileSync(fullPath, 'utf8');
    const anchors = Array.isArray(item.anchors) ? item.anchors : [];
    check(anchors.length > 0, 'ANCHOR_EMPTY', item.path);
    for (const anchor of anchors) {
      check(typeof anchor?.text === 'string' && anchor.text.length > 0,
        'ANCHOR_TEXT_INVALID', item.path);
      check(Number.isInteger(anchor?.count) && anchor.count > 0,
        'ANCHOR_EXPECTED_COUNT_INVALID', item.path);
      if (typeof anchor?.text !== 'string' || !Number.isInteger(anchor?.count)) continue;
      const actual = countOccurrences(content, anchor.text);
      check(actual > 0, 'ANCHOR_MISSING', item.path);
      check(actual === anchor.count, 'ANCHOR_COUNT_MISMATCH', item.path);
    }
  };

  for (const source of sources) {
    const owner = source?.sourceType ?? 'UNKNOWN_SOURCE';
    check(SOURCE_TYPES.includes(owner), 'SOURCE_TYPE_INVALID', owner);
    check(['NOT_READY', 'OPEN_DECISION'].includes(source?.readiness),
      'SOURCE_READINESS_INVALID', owner);
    check(['FOUND_IN_REPOSITORY', 'NOT_FOUND_IN_REPOSITORY'].includes(source?.producerStatus),
      'PRODUCER_STATUS_INVALID', owner);
    check(typeof source?.sourceOwner === 'string' && source.sourceOwner.length > 0,
      'SOURCE_OWNER_INVALID', owner);
    check(typeof source?.adapterOwner === 'string' && source.adapterOwner.length > 0,
      'ADAPTER_OWNER_INVALID', owner);
    const evidence = Array.isArray(source?.evidence) ? source.evidence : [];
    if (owner === 'AI') {
      check(source.producerStatus === 'NOT_FOUND_IN_REPOSITORY', 'AI_PRODUCER_STATUS_INVALID');
      check(evidence.length === 0, 'AI_EVIDENCE_MUST_BE_EMPTY');
    } else {
      check(source.producerStatus === 'FOUND_IN_REPOSITORY', 'SOURCE_PRODUCER_NOT_FOUND', owner);
      check(evidence.length > 0, 'SOURCE_EVIDENCE_EMPTY', owner);
    }
    for (const item of evidence) validateEvidence(item, owner);

    const identity = source?.identity ?? {};
    check(sameSet(Object.keys(identity), IDENTITY_KEYS), 'IDENTITY_KEYS_INVALID', owner);
    for (const key of IDENTITY_KEYS) {
      const value = identity[key];
      check(typeof value === 'string' && value.length > 0, 'IDENTITY_VALUE_INVALID', `${owner}:${key}`);
      check(!/^(?:READY|APPROVED|ACCEPTABLE|AUTHORITATIVE)$/i.test(value ?? ''),
        'IDENTITY_UNAPPROVED', `${owner}:${key}`);
    }
    check(Array.isArray(source?.blockers) && source.blockers.length > 0,
      'SOURCE_BLOCKERS_EMPTY', owner);
  }

  const allAnchors = sources.flatMap((source) => source.evidence ?? [])
    .flatMap((entry) => entry.anchors ?? []).map((anchor) => anchor.text);
  const requiredSourceAnchors = [
    '.tenantId(0L)',
    '.createTime(LocalDateTime.now())',
    'remoteDeviceService.evaluatePropertyThreshold(param);',
    'deviceEventService.save(deviceEvent);',
    "'msgId': str(uuid.uuid4())",
    "tenant = os.getenv('MQTT_ALGO_TENANT') or 'default'",
    'envelope["msgId"] = makeUuid();',
    'tenant = env && *env ? env : "default";',
    'alertService.processAlert(msg);',
    'kafkaTemplate.send(notificationSendTopic',
  ];
  for (const anchor of requiredSourceAnchors) {
    check(allAnchors.includes(anchor), 'REQUIRED_SOURCE_FACT_MISSING', anchor);
  }

  const legacyEntries = Array.isArray(inventory.legacyEntries) ? inventory.legacyEntries : [];
  const legacyIds = legacyEntries.map((entry) => entry?.id);
  check(legacyEntries.length === LEGACY_IDS.length && sameSet(legacyIds, LEGACY_IDS),
    'LEGACY_SET_INVALID');
  check(new Set(legacyIds).size === legacyIds.length, 'LEGACY_DUPLICATE');
  for (const entry of legacyEntries) validateEvidence({ ...entry, role: entry.kind }, entry.id);

  const externalOpen = Array.isArray(inventory.externalOpen) ? inventory.externalOpen : [];
  check(externalOpen.length === EXTERNAL_OPEN.length && sameSet(externalOpen, EXTERNAL_OPEN),
    'EXTERNAL_OPEN_SET_INVALID');

  const serialized = JSON.stringify(inventory);
  check(!/"(?:password|secret|token|credential|productionUrl|mediaUrl)"\s*:/i.test(serialized),
    'SENSITIVE_KEY_FORBIDDEN');
  check(!/(?<!\d)1[3-9]\d{9}(?!\d)/.test(serialized), 'PHONE_FORBIDDEN');
  check(!/https?:\/\/[^\s"]+\?(?:[^"&]+&)*(?:signature|token|x-amz-signature)=/i.test(serialized),
    'SIGNED_URL_FORBIDDEN');

  const aiHits = scanAiPublishers(repoRoot);
  check(aiHits.length === 0, 'AI_PUBLISHER_DRIFT', aiHits[0]?.path ?? 'AI');

  return { issues, checks };
}

function readManifest(manifestPath) {
  try {
    return JSON.parse(fs.readFileSync(manifestPath, 'utf8'));
  } catch (error) {
    const stable = error instanceof SyntaxError ? 'JSON_PARSE_FAILED' : 'MANIFEST_READ_FAILED';
    process.stderr.write(`FAIL code=${stable} path=${path.relative(REPO_ROOT, manifestPath)}\n`);
    process.exitCode = 1;
    return null;
  }
}

function clone(value) {
  return JSON.parse(JSON.stringify(value));
}

function runSelfTests(base) {
  const cases = [
    ['missing-source', 'SOURCE_SET_INVALID', (x) => x.sources.pop()],
    ['duplicate-source', 'SOURCE_DUPLICATE', (x) => x.sources.push(clone(x.sources[0]))],
    ['production-scope', 'SCOPE_INVALID', (x) => { x.scope = 'production'; }],
    ['absolute-path', 'PATH_INVALID', (x) => { x.sources[0].evidence[0].path = 'C:/outside.java'; }],
    ['missing-path', 'PATH_MISSING', (x) => { x.sources[0].evidence[0].path = 'AI/not-present.py'; }],
    ['missing-anchor', 'ANCHOR_MISSING', (x) => { x.sources[0].evidence[0].anchors[0].text = 'NO_SUCH_ANCHOR'; }],
    ['anchor-count', 'ANCHOR_COUNT_MISMATCH', (x) => { x.sources[0].evidence[0].anchors[0].count = 99; }],
    ['missing-legacy', 'LEGACY_SET_INVALID', (x) => x.legacyEntries.pop()],
    ['ai-claimed-producer', 'AI_PRODUCER_STATUS_INVALID', (x) => {
      x.sources.find((s) => s.sourceType === 'AI').producerStatus = 'FOUND_IN_REPOSITORY';
    }],
    ['source-ready', 'SOURCE_READINESS_INVALID', (x) => { x.sources[0].readiness = 'READY'; }],
    ['c1-approved', 'STAGE_GATED', (x) => { x.stages.C1 = 'APPROVED'; }],
    ['production-claimed', 'PRODUCTION_FACTS_INVALID', (x) => { x.productionFacts = 'CLOSED'; }],
    ['external-gate-removed', 'EXTERNAL_OPEN_SET_INVALID', (x) => x.externalOpen.pop()],
    ['identity-acceptable', 'IDENTITY_UNAPPROVED', (x) => { x.sources[0].identity.tenant = 'ACCEPTABLE'; }],
    ['sensitive-key', 'SENSITIVE_KEY_FORBIDDEN', (x) => { x.secret = 'forbidden'; }],
  ];

  let passed = 0;
  for (const [name, expectedCode, mutate] of cases) {
    const value = clone(base);
    mutate(value);
    const result = validateInventory(value);
    if (!result.issues.some((issue) => issue.code === expectedCode)) {
      process.stderr.write(`FAIL self-test=${name} expected=${expectedCode}\n`);
      process.exitCode = 1;
      return false;
    }
    passed += 1;
  }

  const tempRoot = fs.mkdtempSync(path.join(os.tmpdir(), 'p02-c0-ai-'));
  try {
    fs.mkdirSync(path.join(tempRoot, 'AI'), { recursive: true });
    fs.writeFileSync(path.join(tempRoot, 'AI', 'producer.py'),
      "publish('mqtt/iot-alert-notification', payload)\n", 'utf8');
    const hits = scanAiPublishers(tempRoot);
    if (hits.length !== 1) {
      process.stderr.write('FAIL self-test=ai-publisher-drift expected=AI_PUBLISHER_DRIFT\n');
      process.exitCode = 1;
      return false;
    }
    passed += 1;
  } finally {
    fs.rmSync(tempRoot, { recursive: true, force: true });
  }

  process.stdout.write(`SELF_TEST PASS cases=${passed}\n`);
  return true;
}

function main() {
  const args = new Set(process.argv.slice(2));
  const unknown = [...args].filter((arg) => arg !== '--self-test');
  if (unknown.length > 0) {
    process.stderr.write(`FAIL code=ARGUMENT_INVALID value=${unknown[0]}\n`);
    process.exitCode = 2;
    return;
  }

  const inventory = readManifest(DEFAULT_MANIFEST);
  if (!inventory) return;
  const result = validateInventory(inventory);
  if (result.issues.length > 0) {
    for (const issue of result.issues) {
      process.stderr.write(`FAIL code=${issue.code}${issue.detail ? ` target=${issue.detail}` : ''}\n`);
    }
    process.stderr.write(`SUMMARY FAIL checks=${result.checks} issues=${result.issues.length}\n`);
    process.exitCode = 1;
    return;
  }

  process.stdout.write(
    `PASS checks=${result.checks} sources=${inventory.sources.length} legacy=${inventory.legacyEntries.length} scope=${inventory.scope}\n`,
  );
  if (args.has('--self-test')) runSelfTests(inventory);
}

main();
