import type { DeviceInfo, NvrInfo } from '@/api/device/camera';
import { getDeviceList, getNvrList } from '@/api/device/camera';
import { formatNvrDisplayName, isNvrChannelDevice, isNvrListRow } from './deviceLabel';

export type { NvrInfo };

export interface NvrCardItem {
  nvrId: number;
  name: string;
  ip: string;
  port: number;
  vendor_label?: string;
  model?: string;
  rtsp_url?: string;
  camera_count: number;
  _nvr: NvrInfo;
}

/** 未挂载到 NVR 的直连设备（顶层卡片/表格行） */
export function filterStandaloneDirectDevices(devices: DeviceInfo[]): DeviceInfo[] {
  return devices.filter((d) => !isNvrChannelDevice(d) && !isNvrListRow(d));
}

export function nvrToCardItem(nvr: NvrInfo): NvrCardItem {
  return {
    nvrId: nvr.id!,
    name: formatNvrDisplayName(nvr),
    ip: nvr.ip,
    port: nvr.port ?? 80,
    vendor_label: nvr.vendor_label,
    model: nvr.model,
    rtsp_url: nvr.rtsp_url,
    camera_count: nvr.camera_count ?? nvr.cameras?.length ?? 0,
    _nvr: nvr,
  };
}

export function nvrToTableRow(nvr: NvrInfo): DeviceInfo & { _isNvr: boolean; nvr_id_num: number } {
  return {
    id: `nvr_${nvr.id}`,
    name: formatNvrDisplayName(nvr),
    device_kind: 'nvr',
    nvr_id_num: nvr.id!,
    ip: nvr.ip,
    port: nvr.port ?? 80,
    model: nvr.model ?? '-',
    manufacturer: nvr.vendor_label || nvr.vendor || 'NVR',
    source: nvr.rtsp_url || '',
    online: true,
    channel_count: nvr.camera_count ?? 0,
    _isNvr: true,
  } as DeviceInfo & { _isNvr: boolean; nvr_id_num: number };
}

export type DeviceListDisplayItem =
  | { kind: 'direct'; device: DeviceInfo }
  | { kind: 'gb_sip'; gbItem: Record<string, unknown> }
  | { kind: 'nvr'; nvrItem: NvrCardItem };

export async function fetchNvrList(): Promise<NvrInfo[]> {
  try {
    const res = await getNvrList(true);
    return Array.isArray(res) ? res : (res as { data?: NvrInfo[] })?.data ?? [];
  } catch {
    return [];
  }
}

export function buildCardRowsWithNvr(
  devices: DeviceInfo[],
  nvrs: NvrInfo[],
  gbItems: DeviceListDisplayItem[] = [],
): DeviceListDisplayItem[] {
  const items: DeviceListDisplayItem[] = filterStandaloneDirectDevices(devices).map((device) => ({
    kind: 'direct' as const,
    device,
  }));
  for (const nvr of nvrs) {
    if (nvr.id) {
      items.push({ kind: 'nvr' as const, nvrItem: nvrToCardItem(nvr) });
    }
  }
  for (const g of gbItems) {
    if (g.kind === 'gb_sip') items.push(g);
  }
  return items;
}
