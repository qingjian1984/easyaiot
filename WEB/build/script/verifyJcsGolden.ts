/**
 * TD-005 §23 门禁 3：生产 TypeScript 实现消费同一 JCS golden 的合同脚本。
 * 逐 case 比对 canonical 字节（Base64）、字节数与 SHA-256，任一不一致即退出码 1。
 * 运行：pnpm verify:jcs-golden（从 WEB 目录执行）。
 */
import crypto from 'node:crypto'
import fs from 'node:fs'
import path from 'node:path'
import process from 'node:process'
import { canonicalizeJson } from '../../src/utils/jcs'

interface GoldenCase {
  name: string
  input: string
  canonicalBase64: string
  bytes: number
  sha256: string
}

const goldenPath = path.resolve(
  process.cwd(),
  '../.doc/规格/电力运维云平台/assets/model-templates/verification/jcs-golden.json',
)
const golden = JSON.parse(fs.readFileSync(goldenPath, 'utf-8'))

if (golden.canonicalizationVersion !== 'jcs-rfc8785-v1' || golden.hashAlgorithm !== 'SHA-256') {
  console.error('FAIL: golden 版本标识不匹配', golden.canonicalizationVersion, golden.hashAlgorithm)
  process.exit(1)
}

let failed = 0
for (const goldenCase of golden.cases as GoldenCase[]) {
  const inputPath = path.resolve(path.dirname(goldenPath), goldenCase.input)
  const input = JSON.parse(fs.readFileSync(inputPath, 'utf-8'))
  const canonical = canonicalizeJson(input)
  const canonicalBytes = Buffer.from(canonical, 'utf-8')
  const sha256 = crypto.createHash('sha256').update(canonicalBytes).digest('hex')

  const problems: string[] = []
  if (canonicalBytes.toString('base64') !== goldenCase.canonicalBase64) {
    problems.push('canonical 字节不一致')
  }
  if (canonicalBytes.length !== goldenCase.bytes) {
    problems.push(`字节数不一致: ${canonicalBytes.length} != ${goldenCase.bytes}`)
  }
  if (sha256 !== goldenCase.sha256) {
    problems.push('sha256 不一致')
  }
  if (problems.length > 0) {
    failed++
    console.error(`FAIL ${goldenCase.name}: ${problems.join('; ')}`)
  } else {
    console.log(`PASS ${goldenCase.name} (${goldenCase.bytes} bytes)`)
  }
}

if (failed > 0) {
  console.error(`JCS golden 合同失败: ${failed}/${golden.cases.length} 个 case 不一致`)
  process.exit(1)
}
console.log(`JCS golden 合同 PASS: TypeScript 实现与 golden 全量一致（${golden.cases.length} 个 case）`)
