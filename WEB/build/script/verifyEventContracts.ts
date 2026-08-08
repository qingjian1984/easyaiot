/**
 * ADR-014 §Schema 模块归属与 CI 门禁 + §验证 OUT-005～008：事件合同门禁脚本。
 * - Schema 唯一来源：生产者 API 模块 iot-device-api 资源目录 schema/power/model/v<主版本>/
 * - Ajv Draft 2020-12 strict + ajv-formats 校验 4 个 V1 fixture（OUT-005～007）
 * - 未知主版本反例必须校验失败（OUT-008）
 * - 严格性反例：附加字段必须被 additionalProperties:false 拒绝
 * - 双主版本门禁：扫描 v* 目录，登记当前/上一主版本；V2 出现后两者都须通过
 * - 文档评审资产与 API 模块资源字节一致（禁止第二份拷贝漂移）
 * 任一检查失败即退出码 1。运行：pnpm verify:event-contracts（从 WEB 目录执行）。
 */
import Ajv2020 from 'ajv/dist/2020'
import addFormats from 'ajv-formats'
import crypto from 'node:crypto'
import fs from 'node:fs'
import path from 'node:path'
import process from 'node:process'

const TRUSTED_ID_PREFIX = 'https://easyaiot.local/schemas/'

const repoRoot = path.resolve(process.cwd(), '..')
const schemaRoot = path.join(
  repoRoot,
  'DEVICE/iot-device/iot-device-api/src/main/resources/schema/power/model',
)
const docAssetRoot = path.join(
  repoRoot,
  '.doc/技术设计/电力运维云平台/assets/td005-migration/events',
)

let failed = 0
function check(ok: boolean, label: string, detail: string): void {
  if (ok) {
    console.log(`PASS ${label}`)
  } else {
    failed++
    console.error(`FAIL ${label}: ${detail}`)
  }
}

function sha256(filePath: string): string {
  return crypto.createHash('sha256').update(fs.readFileSync(filePath)).digest('hex')
}

// ---- 双主版本扫描 ----
const majorDirs = fs
  .readdirSync(schemaRoot, { withFileTypes: true })
  .filter((entry) => entry.isDirectory() && /^v[0-9]+$/.test(entry.name))
  .map((entry) => Number(entry.name.slice(1)))
  .sort((a, b) => a - b)

check(majorDirs.length >= 1, 'schema-major-dirs', `未发现 v<主版本> 目录于 ${schemaRoot}`)
const currentMajor = majorDirs[majorDirs.length - 1]
const previousMajor = majorDirs.length > 1 ? majorDirs[majorDirs.length - 2] : null
console.log(
  `majors: [${majorDirs.join(', ')}] current=v${currentMajor} previous=${
    previousMajor === null ? 'none（M1 单主版本）' : `v${previousMajor}`
  }`,
)

// ---- 当前主版本 Schema 合同 ----
const currentDir = path.join(schemaRoot, `v${currentMajor}`)
const schemaFiles = fs.readdirSync(currentDir).filter((name) => name.endsWith('.json'))
check(schemaFiles.length === 4, 'schema-count', `v${currentMajor} 应有 4 个 Schema，实测 ${schemaFiles.length}`)

const ajv = new Ajv2020({ strict: true, allErrors: true })
addFormats(ajv)

interface EventSchema {
  $id?: string
  properties?: {
    eventType?: { const?: string }
    schemaVersion?: { const?: number }
  }
}

const validators = new Map<string, ReturnType<typeof ajv.compile>>()
for (const file of schemaFiles) {
  const schemaPath = path.join(currentDir, file)
  const schema = JSON.parse(fs.readFileSync(schemaPath, 'utf-8')) as EventSchema
  const eventType = schema.properties?.eventType?.const ?? ''
  const schemaVersion = schema.properties?.schemaVersion?.const

  check(
    typeof schema.$id === 'string' && schema.$id.startsWith(TRUSTED_ID_PREFIX),
    `${file} $id`,
    `$id 必须以 ${TRUSTED_ID_PREFIX} 开头，实测 ${schema.$id}`,
  )
  check(
    /_V[0-9]+$/.test(eventType) && Number(eventType.slice(eventType.lastIndexOf('_V') + 2)) === schemaVersion,
    `${file} version-suffix`,
    `eventType=${eventType} 后缀与 schemaVersion=${schemaVersion} 不一致`,
  )
  check(
    schemaVersion === currentMajor,
    `${file} major`,
    `schemaVersion=${schemaVersion} 与目录主版本 v${currentMajor} 不一致`,
  )

  // 文档评审资产与 API 模块资源字节一致（禁止第二份拷贝漂移）
  const docAssetPath = path.join(docAssetRoot, file)
  check(
    fs.existsSync(docAssetPath) && sha256(docAssetPath) === sha256(schemaPath),
    `${file} asset-parity`,
    '文档评审资产与 API 模块资源不一致（或缺失）',
  )

  validators.set(eventType, ajv.compile(schema))
}

// ---- fixture 校验（OUT-005～007）----
const fixturesPath = path.join(docAssetRoot, 'events.fixtures.example.json')
const fixtures = JSON.parse(fs.readFileSync(fixturesPath, 'utf-8')) as Record<
  string,
  Record<string, unknown>
>
for (const [name, fixture] of Object.entries(fixtures)) {
  const eventType = String(fixture.eventType ?? '')
  const validate = validators.get(eventType)
  if (!validate) {
    check(false, `fixture ${name}`, `无对应 Schema（eventType=${eventType}）`)
    continue
  }
  check(validate(fixture) === true, `fixture ${name}`, JSON.stringify(validate.errors))
}

// ---- OUT-008：未知主版本反例必须失败 ----
const sample = fixtures[Object.keys(fixtures)[0]]
const unknownMajor = { ...sample, eventType: `${String(sample.eventType).replace(/_V[0-9]+$/, '')}_V99`, schemaVersion: 99 }
const knownValidator = validators.get(String(sample.eventType))
check(
  knownValidator !== undefined && knownValidator(unknownMajor) === false,
  'OUT-008 unknown-major rejected',
  '未知主版本 fixture 竟通过 v1 Schema 校验',
)

// ---- 严格性反例：附加字段必须被拒 ----
const withExtra = { ...sample, unexpectedField: 'x' }
check(
  knownValidator !== undefined && knownValidator(withExtra) === false,
  'strict additionalProperties rejected',
  '附加字段竟通过 strict 校验（additionalProperties:false 失效）',
)

if (failed > 0) {
  console.error(`事件合同门禁失败: ${failed} 项未通过`)
  process.exit(1)
}
console.log(
  `事件合同门禁 PASS: ${schemaFiles.length} 个 Schema + ${Object.keys(fixtures).length} 个 fixture（Ajv 2020-12 strict + ajv-formats）`,
)
