/**
 * TD-005 §6：JCS（RFC 8785，`jcs-rfc8785-v1`）canonical 序列化。
 *
 * 与服务端 Java `JcsCanonicalizer`、验证资产 `jcs_canonicalize.mjs` 消费同一
 * golden（`.doc/规格/电力运维云平台/assets/model-templates/verification/jcs-golden.json`）。
 * 对象键按 UTF-16 代码单元排序（JS 默认 sort 语义）；数值禁止非有限值。
 */
export function canonicalizeJson(value: unknown): string {
  if (value === null || typeof value === 'boolean' || typeof value === 'string') {
    return JSON.stringify(value)
  }
  if (typeof value === 'number') {
    if (!Number.isFinite(value)) {
      throw new TypeError('JCS does not allow non-finite numbers')
    }
    return JSON.stringify(value)
  }
  if (Array.isArray(value)) {
    return `[${value.map(canonicalizeJson).join(',')}]`
  }
  if (typeof value === 'object') {
    const members = Object.keys(value as Record<string, unknown>)
      .sort()
      .map((key) => `${JSON.stringify(key)}:${canonicalizeJson((value as Record<string, unknown>)[key])}`)
    return `{${members.join(',')}}`
  }
  throw new TypeError(`Unsupported JSON value type: ${typeof value}`)
}

/** 解析 JSON 文本并输出 canonical 形式（哈希输入、导出预览比对用）。 */
export function canonicalizeJsonText(text: string): string {
  return canonicalizeJson(JSON.parse(text))
}
