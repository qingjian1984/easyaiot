#!/usr/bin/env node
/*
 * P02-M2-02B B0: formal TD-006 alarm Schema gate.
 * This is a local/static contract check only: it reads repository files and
 * never starts Spring, connects PostgreSQL, or contacts a transport.
 */
import Ajv2020 from 'ajv/dist/2020.js'
import addFormats from 'ajv-formats'
import crypto from 'node:crypto'
import fs from 'node:fs'
import path from 'node:path'
import { fileURLToPath } from 'node:url'

const scriptDir = path.dirname(fileURLToPath(import.meta.url))
const repoRoot = path.resolve(scriptDir, '..', '..')
const formalRoot = path.join(
  repoRoot,
  'DEVICE/iot-device/iot-device-api/src/main/resources/events',
)
const assetRoot = path.join(
  repoRoot,
  '.doc/技术设计/电力运维云平台/assets/td006-events',
)
const expected = [
  ['alarm-source-event-v1.schema.json', 'device.alarm.source-event/v1.json'],
  ['device.alarm.created.v1.schema.json', 'device.alarm.created/v1.json'],
  ['device.alarm.occurrence-recorded.v1.schema.json', 'device.alarm.occurrence-recorded/v1.json'],
  ['device.alarm.recovered.v1.schema.json', 'device.alarm.recovered/v1.json'],
  ['device.alarm.status-changed.v1.schema.json', 'device.alarm.status-changed/v1.json'],
  ['device.alarm.escalated.v1.schema.json', 'device.alarm.escalated/v1.json'],
  ['device.alarm.suppression-decided.v1.schema.json', 'device.alarm.suppression-decided/v1.json'],
]

let failures = 0
function check(ok, label, detail = '') {
  if (ok) console.log(`PASS ${label}`)
  else {
    failures += 1
    console.error(`FAIL ${label}${detail ? `: ${detail}` : ''}`)
  }
}

function sha256(file) {
  return crypto.createHash('sha256').update(fs.readFileSync(file)).digest('hex')
}

function readJson(file) {
  return JSON.parse(fs.readFileSync(file, 'utf8'))
}

function baseEnvelope(eventType, payload) {
  return {
    eventId: '00000000-0000-4000-8000-000000000001',
    eventVersion: '1.0',
    eventType,
    tenantId: '1',
    occurredAt: '2026-08-24T08:00:00+08:00',
    recordedAt: '2026-08-24T08:00:01+08:00',
    source: 'iot-device',
    correlationId: 'corr-1',
    payload,
  }
}

const payloads = {
  'device.alarm.source-event.v1': {
    sourceType: 'VIDEO', sourceAction: 'RAISED', sourceId: 'source-1',
    cycleKey: 'cycle-1', sourceObjectId: 'object-1', siteId: '1',
    deviceId: 'device-1', severity: 'NORMAL', occurredAt: '2026-08-24T08:00:00+08:00',
    payloadHash: `sha256:${'a'.repeat(64)}`,
  },
  'device.alarm.created.v1': {
    alarmId: '1001', status: 'ACTIVE', severity: 'NORMAL', sourceType: 'VIDEO',
    sourceId: 'source-1', cycleKey: 'cycle-1', siteId: '1', deviceId: 'device-1',
    propertyCode: 'voltage', ruleId: '10', ruleVersion: '1',
    firstOccurredAt: '2026-08-24T08:00:00+08:00', occurrenceCount: 1,
  },
  'device.alarm.occurrence-recorded.v1': {
    alarmId: '1001', status: 'ACTIVE', occurrenceCount: 2,
    lastOccurredAt: '2026-08-24T08:00:00+08:00',
    sourceMessageId: '00000000-0000-4000-8000-000000000002',
  },
  'device.alarm.recovered.v1': {
    alarmId: '1001', fromStatus: 'ACTIVE', status: 'RECOVERED',
    recoveredAt: '2026-08-24T08:00:00+08:00', version: 1,
  },
  'device.alarm.status-changed.v1': {
    alarmId: '1001', action: 'ACKNOWLEDGE', fromStatus: 'ACTIVE', status: 'ACKNOWLEDGED',
    operatorId: 'operator-1', reasonCode: 'ACK', version: 1,
  },
  'device.alarm.escalated.v1': {
    alarmId: '1001', status: 'ACTIVE', fromLevel: 0, toLevel: 1,
    policyId: '20', policyVersion: '1', escalatedAt: '2026-08-24T08:00:00+08:00', version: 1,
  },
  'device.alarm.suppression-decided.v1': {
    alarmId: '1001', status: 'ACTIVE', decision: 'NOT_SUPPRESSED',
    maintenanceContextId: null, policyId: '20', policyVersion: '1', reasonCode: 'NONE',
    decidedAt: '2026-08-24T08:00:00+08:00', version: 1,
  },
}

const ajv = new Ajv2020({ strict: true, allErrors: true })
addFormats(ajv)
const validators = new Map()

check(fs.existsSync(formalRoot), 'formal-resource-root')
const formalEntries = fs.existsSync(formalRoot) ? fs.readdirSync(formalRoot) : []
check(!formalEntries.some((name) => /alarm-domain|union/i.test(name)), 'review-union-not-copied')

for (const [assetName, formalRelative] of expected) {
  const asset = path.join(assetRoot, assetName)
  const formal = path.join(formalRoot, formalRelative)
  check(fs.existsSync(asset) && fs.existsSync(formal), `${formalRelative} exists`)
  if (!fs.existsSync(asset) || !fs.existsSync(formal)) continue

  check(sha256(asset) === sha256(formal), `${formalRelative} asset-parity`)
  const schema = readJson(formal)
  const eventType = schema?.properties?.eventType?.const
  check(typeof eventType === 'string' && eventType.endsWith('.v1'), `${formalRelative} current-major`)
  check(schema?.additionalProperties === true, `${formalRelative} additive-fields-allowed`)
  try {
    validators.set(eventType, ajv.compile(schema))
    console.log(`PASS ${formalRelative} ajv-compile`)
  } catch (error) {
    check(false, `${formalRelative} ajv-compile`, error.message)
  }
}

for (const [eventType, payload] of Object.entries(payloads)) {
  const validate = validators.get(eventType)
  const fixture = baseEnvelope(eventType, payload)
  check(Boolean(validate && validate(fixture)), `${eventType} positive`, validate?.errors?.map((e) => e.message).join('; '))
  const schema = readJson(path.join(formalRoot, eventType.replace(/\.v1$/, ''), 'v1.json'))
  for (const field of schema.required ?? []) {
    const missing = structuredClone(fixture)
    delete missing[field]
    check(Boolean(validate && !validate(missing)), `${eventType} missing-envelope-${field}`)
  }
  for (const field of schema?.properties?.payload?.required ?? []) {
    const missing = structuredClone(fixture)
    delete missing.payload[field]
    check(Boolean(validate && !validate(missing)), `${eventType} missing-payload-${field}`)
  }
  const additive = { ...fixture, futureOptionalField: { v2: true } }
  check(Boolean(validate && validate(additive)), `${eventType} additive-field`)
  const additivePayload = structuredClone(fixture)
  additivePayload.payload.futureOptionalField = { v2: true }
  check(Boolean(validate && validate(additivePayload)), `${eventType} additive-payload-field`)
}

const source = baseEnvelope('device.alarm.source-event.v1', payloads['device.alarm.source-event.v1'])
check(!validators.get('device.alarm.source-event.v1')({ ...source, tenantId: 1 }), 'tenant-number-rejected')
check(!validators.get('device.alarm.created.v1')({
  ...baseEnvelope('device.alarm.created.v1', payloads['device.alarm.created.v1']),
  eventId: '00000000-0000-4000-8000-00000000000A',
}), 'uppercase-uuid-rejected')
check(!validators.get('device.alarm.created.v1')({
  ...baseEnvelope('device.alarm.created.v1', payloads['device.alarm.created.v1']),
  occurredAt: '2026-08-24T08:00:00',
}), 'offset-required')
check(!validators.get('device.alarm.created.v1')({
  ...baseEnvelope('device.alarm.created.v1', payloads['device.alarm.created.v1']),
  eventType: 'device.alarm.created.v2',
  eventVersion: '2.0',
}), 'unknown-major-rejected')

if (failures > 0) {
  console.error(`告警事件合同门禁失败: ${failures} 项`)
  process.exit(1)
}
console.log(`告警事件合同门禁 PASS: ${expected.length} 个正式 Schema（Ajv 2020-12 strict + formats）`)
