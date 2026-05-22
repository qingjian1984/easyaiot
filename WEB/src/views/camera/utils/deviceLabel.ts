/** 国标虚拟源前缀 */
const GB28181_SOURCE_PREFIX = 'gb28181://';

export function isGb28181Device(source?: string | null, deviceKind?: string): boolean {
  if (source?.trim().toLowerCase().startsWith(GB28181_SOURCE_PREFIX)) return true;
  if (deviceKind === 'gb28181' || deviceKind === 'gb28181_sip') return true;
  if (deviceKind === 'direct') return false;
  return false;
}

/** 国标通道在 device 表中的虚拟设备 ID（与 VIDEO gb28181_sync 一致） */
export function gb28181VirtualDeviceId(sipDeviceId: string, channelId: string): string {
  return `gb28181_${sipDeviceId}_${channelId}`;
}

/** 解析 gb28181://{deviceId}/{channelId} */
export function parseGb28181Source(source?: string | null): { deviceId: string; channelId: string } | null {
  if (!source?.trim().toLowerCase().startsWith(GB28181_SOURCE_PREFIX)) {
    return null;
  }
  const rest = source.trim().slice(GB28181_SOURCE_PREFIX.length);
  const slash = rest.indexOf('/');
  if (slash <= 0) return null;
  const deviceId = rest.slice(0, slash).trim();
  const channelId = rest.slice(slash + 1).replace(/^\/+/, '').trim();
  if (!deviceId || !channelId) return null;
  return { deviceId, channelId };
}

/** 是否应走 WVP 国标点播（含 device 表虚拟通道与通道列表页） */
export function shouldPlayViaGb28181(record: Record<string, any> | null | undefined): boolean {
  if (!record) return false;
  const parsed = parseGb28181Source(record.source);
  if (parsed) return true;
  const sip = String(record.deviceIdentification || '').trim();
  const ch = String(record.channelId || '').trim();
  if (sip && ch && sip !== ch) return true;
  if (record.deviceId && record.channelId && record.deviceId !== record.channelId) return true;
  return false;
}

/** 解析国标点播用的 SIP 设备 ID 与通道 ID */
export function getGb28181PlayIds(
  record: Record<string, any> | null | undefined,
): { sipDeviceId: string; channelId: string } | null {
  if (!record) return null;
  const parsed = parseGb28181Source(record.source);
  if (parsed) {
    return { sipDeviceId: parsed.deviceId, channelId: parsed.channelId };
  }
  const sipDeviceId = String(record.deviceIdentification || record.deviceId || '').trim();
  const channelId = String(record.channelId || '').trim();
  if (!sipDeviceId || !channelId || sipDeviceId === channelId) return null;
  return { sipDeviceId, channelId };
}

export function isNvrChannelDevice(device: {
  nvr_id?: number | null;
  device_kind?: string;
}): boolean {
  if (device.device_kind === 'nvr_channel') return true;
  return !!(device.nvr_id && device.nvr_id > 0);
}

export function isNvrListRow(record: {
  device_kind?: string;
  id?: string;
  _isNvr?: boolean;
}): boolean {
  if (record._isNvr) return true;
  if (record.device_kind === 'nvr') return true;
  return String(record.id || '').startsWith('nvr_');
}

export function formatNvrDisplayName(nvr: {
  name?: string | null;
  device_name?: string | null;
  ip?: string;
  port?: number;
  id?: number;
}): string {
  const base = (nvr.name || nvr.device_name || nvr.ip || `NVR-${nvr.id}`).trim();
  if (base.startsWith('[NVR]')) return base;
  return `[NVR] ${base}`;
}

export function formatCameraDeviceLabel(device: {
  name?: string | null;
  id?: string;
  source?: string | null;
  device_kind?: 'direct' | 'gb28181' | 'gb28181_sip' | 'nvr' | 'nvr_channel' | string;
  nvr_id?: number | null;
  nvr_channel?: number;
  nvr_label?: string | null;
}): string {
  const name = (device.name || device.id || '').trim();
  if (device.device_kind === 'nvr' || isNvrListRow(device)) {
    return formatNvrDisplayName({ name, ip: (device as { ip?: string }).ip, id: undefined });
  }
  if (isGb28181Device(device.source, device.device_kind)) {
    if (name.startsWith('[GB28181]')) return name;
    return name ? `[GB28181] ${name}` : '[GB28181]';
  }
  if (isNvrChannelDevice(device)) {
    const ch = device.nvr_channel ? `CH${device.nvr_channel} ` : '';
    const base = name.replace(/^\[NVR\]\s*/i, '').trim();
    return `[NVR] ${ch}${base}`.trim();
  }
  if (name.startsWith('[直连]') || name.startsWith('[NVR]')) return name;
  return `[直连] ${name}`;
}
