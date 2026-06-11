import { NODE_ROLE_MAP } from './constants';

export interface NodeFormHistoryEntry {
  id: string;
  savedAt: number;
  name: string;
  host: string;
  nodeRole: string;
  region?: string;
  remark?: string;
  sshPort?: number;
  agentPort?: number;
  sshUsername?: string;
  sshAuthType?: string;
  sshPassword?: string;
  sshPrivateKey?: string;
  srsRtmpPort?: number;
  srsHttpPort?: number;
  zlmHttpPort?: number;
  zlmRtmpPort?: number;
  zlmRtpPortMin?: number;
  zlmRtpPortMax?: number;
}

const STORAGE_KEY = 'easyaiot_node_form_history_v1';
const MAX_ENTRIES = 15;

function loadAll(): NodeFormHistoryEntry[] {
  try {
    const raw = localStorage.getItem(STORAGE_KEY);
    if (!raw) return [];
    const list = JSON.parse(raw);
    return Array.isArray(list) ? list : [];
  } catch {
    return [];
  }
}

function persist(list: NodeFormHistoryEntry[]) {
  localStorage.setItem(STORAGE_KEY, JSON.stringify(list));
}

function entryFingerprint(entry: Pick<NodeFormHistoryEntry, 'name' | 'host' | 'nodeRole' | 'sshPort' | 'agentPort'>): string {
  return [
    (entry.name || '').trim(),
    (entry.host || '').trim(),
    entry.nodeRole || 'compute',
    String(entry.sshPort ?? 22),
    String(entry.agentPort ?? ''),
  ].join('\0');
}

export function loadNodeFormHistory(): NodeFormHistoryEntry[] {
  return loadAll().sort((a, b) => b.savedAt - a.savedAt);
}

export function saveNodeFormHistory(
  entry: Omit<NodeFormHistoryEntry, 'id' | 'savedAt'> & { id?: string; savedAt?: number },
): NodeFormHistoryEntry[] {
  const saved: NodeFormHistoryEntry = {
    ...entry,
    name: (entry.name || '').trim(),
    host: (entry.host || '').trim(),
    nodeRole: entry.nodeRole || 'compute',
    id: entry.id || `node_${Date.now()}`,
    savedAt: entry.savedAt ?? Date.now(),
  };
  const fp = entryFingerprint(saved);
  const deduped = loadAll().filter((e) => entryFingerprint(e) !== fp);
  persist([saved, ...deduped].slice(0, MAX_ENTRIES));
  return loadNodeFormHistory();
}

export function removeNodeFormHistory(id: string): void {
  persist(loadAll().filter((e) => e.id !== id));
}

export function formatNodeFormHistoryTime(savedAt: number): string {
  const t = new Date(savedAt);
  const pad = (n: number) => String(n).padStart(2, '0');
  return `${t.getFullYear()}-${pad(t.getMonth() + 1)}-${pad(t.getDate())} ${pad(t.getHours())}:${pad(t.getMinutes())}`;
}

export function formatNodeFormHistoryPrimary(entry: NodeFormHistoryEntry): string {
  const name = entry.name?.trim();
  const host = entry.host?.trim() || '未填写主机';
  return name ? `${name} · ${host}` : host;
}

export function formatNodeFormHistoryMeta(entry: NodeFormHistoryEntry): string {
  const parts: string[] = [
    NODE_ROLE_MAP[entry.nodeRole] || entry.nodeRole || '计算节点',
    formatNodeFormHistoryTime(entry.savedAt),
  ];
  if (entry.region?.trim()) {
    parts.splice(1, 0, entry.region.trim());
  }
  return parts.join(' · ');
}

export function nodeFormHistoryToFields(entry: NodeFormHistoryEntry): Record<string, unknown> {
  return {
    name: entry.name,
    host: entry.host,
    nodeRole: entry.nodeRole,
    region: entry.region,
    remark: entry.remark,
    sshPort: entry.sshPort ?? 22,
    agentPort: entry.agentPort,
    sshUsername: entry.sshUsername ?? 'root',
    sshAuthType: entry.sshAuthType ?? 'password',
    sshPassword: entry.sshPassword,
    sshPrivateKey: entry.sshPrivateKey,
    srsRtmpPort: entry.srsRtmpPort ?? 1935,
    srsHttpPort: entry.srsHttpPort ?? 8080,
    zlmHttpPort: entry.zlmHttpPort ?? 6080,
    zlmRtmpPort: entry.zlmRtmpPort ?? 10935,
    zlmRtpPortMin: entry.zlmRtpPortMin ?? 30000,
    zlmRtpPortMax: entry.zlmRtpPortMax ?? 30500,
  };
}

export function valuesToNodeFormHistoryEntry(values: Record<string, unknown>): Omit<NodeFormHistoryEntry, 'id' | 'savedAt'> {
  return {
    name: String(values.name || '').trim(),
    host: String(values.host || '').trim(),
    nodeRole: String(values.nodeRole || 'compute'),
    region: values.region ? String(values.region) : undefined,
    remark: values.remark ? String(values.remark) : undefined,
    sshPort: values.sshPort != null ? Number(values.sshPort) : 22,
    agentPort: values.agentPort != null ? Number(values.agentPort) : undefined,
    sshUsername: values.sshUsername ? String(values.sshUsername) : 'root',
    sshAuthType: values.sshAuthType ? String(values.sshAuthType) : 'password',
    sshPassword: values.sshPassword ? String(values.sshPassword) : undefined,
    sshPrivateKey: values.sshPrivateKey ? String(values.sshPrivateKey) : undefined,
    srsRtmpPort: values.srsRtmpPort != null ? Number(values.srsRtmpPort) : undefined,
    srsHttpPort: values.srsHttpPort != null ? Number(values.srsHttpPort) : undefined,
    zlmHttpPort: values.zlmHttpPort != null ? Number(values.zlmHttpPort) : undefined,
    zlmRtmpPort: values.zlmRtmpPort != null ? Number(values.zlmRtmpPort) : undefined,
    zlmRtpPortMin: values.zlmRtpPortMin != null ? Number(values.zlmRtpPortMin) : undefined,
    zlmRtpPortMax: values.zlmRtpPortMax != null ? Number(values.zlmRtpPortMax) : undefined,
  };
}
