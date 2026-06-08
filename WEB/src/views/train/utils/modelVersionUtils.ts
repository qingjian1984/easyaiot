const DEFAULT_MODEL_VERSION = '1.0.0';

/** 去掉前导 v/V，用于表单输入与 API 提交 */
export function normalizeModelVersion(version?: string | null): string {
  const raw = String(version ?? '').trim();
  if (!raw) return DEFAULT_MODEL_VERSION;
  const normalized = raw.replace(/^[vV]/, '').trim();
  return normalized || DEFAULT_MODEL_VERSION;
}

/** 展示用：仅在没有 v/V 前缀时补一个 v */
export function formatModelVersionDisplay(version?: string | null): string {
  const raw = String(version ?? '').trim();
  if (!raw) return '';
  return /^[vV]/.test(raw) ? raw : `v${raw}`;
}
