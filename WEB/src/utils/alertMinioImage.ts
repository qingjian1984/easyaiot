/**
 * 告警/抓拍/录像/模型图片展示地址解析。
 * - MinIO：/api/v1/buckets/... 经 nginx 同源代理
 * - VIDEO API：/video/... 前缀 /dev-api
 * - mini 本地绝对路径：/data/... 经 VIDEO alert/image 代理
 */
export function isHostLocalMediaPath(path: string | null | undefined): boolean {
  if (!path) return false;
  const p = String(path).trim();
  if (!p.startsWith('/')) return false;
  if (p.startsWith('/api/') || p.startsWith('/video/')) return false;
  return (
    p.startsWith('/data/') ||
    p.startsWith('/mnt/') ||
    p.startsWith('/app/') ||
    p.startsWith('/tmp/')
  );
}

function resolveVideoApiPath(path: string): string {
  const apiPrefix = (import.meta.env.VITE_GLOB_API_URL || '').replace(/\/$/, '');
  return `${apiPrefix}${path}`;
}

export function resolveAlertImageDisplayUrl(imageUrl: string | null | undefined): string {
  if (imageUrl == null || String(imageUrl).trim() === '') return '';
  const u = String(imageUrl).trim();
  if (u.startsWith('http://') || u.startsWith('https://')) return u;
  if (u.startsWith('/api/v1/buckets/')) {
    return `${window.location.origin}${u}`;
  }
  if (u.startsWith('/video/')) {
    return resolveVideoApiPath(u);
  }
  if (isHostLocalMediaPath(u)) {
    return resolveVideoApiPath(`/video/alert/image?path=${encodeURIComponent(u)}`);
  }
  if (u.startsWith('/')) {
    return `${window.location.origin}${u}`;
  }
  return u;
}

/**
 * 训练结果图：历史数据写在 model-train 桶，对象 key 与 models 桶中一致，统一走 models 桶访问。
 */
export function resolveTrainResultsDisplayUrl(imageUrl: string | null | undefined): string {
  if (imageUrl == null || String(imageUrl).trim() === '') return '';
  let u = String(imageUrl).trim();
  if (u.includes('/api/v1/buckets/model-train/')) {
    u = u.replace('/api/v1/buckets/model-train/', '/api/v1/buckets/models/');
  }
  return resolveAlertImageDisplayUrl(u);
}

/** 模型管理封面图 */
export function resolveModelImageDisplayUrl(imageUrl: string | null | undefined): string {
  return resolveAlertImageDisplayUrl(imageUrl);
}
