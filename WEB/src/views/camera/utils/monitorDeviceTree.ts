import type { TreeItem } from '@/components/Tree';
import type { MonitorTreeDeviceNode, MonitorTreeDirectoryNode } from '@/api/device/camera';
import { formatCameraDeviceLabel, isGb28181Device } from './deviceLabel';
import {
  buildGbChannelNodesFromSynced,
  buildGbSipDeviceNode,
  groupGb28181ChannelsBySip,
} from './gb28181Tree';

/** 将目录下设备挂到树 children：直连为叶子，国标按 SIP 设备分组 */
export function appendDevicesToMonitorTreeChildren(
  children: TreeItem[],
  devices: MonitorTreeDeviceNode[],
  sipNameMap?: Map<string, string>,
) {
  const direct: MonitorTreeDeviceNode[] = [];
  const gbGrouped = groupGb28181ChannelsBySip(devices);

  for (const d of devices) {
    if (!isGb28181Device(d.source, d.device_kind)) {
      direct.push(d);
    }
  }

  direct.forEach((d) => {
    children.push({
      key: `device_${d.id}`,
      title: formatCameraDeviceLabel(d),
      isLeaf: true,
      isDevice: true,
      icon: 'ant-design:video-camera-outlined',
      device: d,
    } as TreeItem);
  });

  gbGrouped.forEach((channels, sipDeviceId) => {
    const channelNodes = buildGbChannelNodesFromSynced(channels, sipDeviceId);
    children.push(buildGbSipDeviceNode(sipDeviceId, channelNodes, sipNameMap?.get(sipDeviceId)));
  });
}

export function buildMonitorDirectoryTreeNodes(
  directories: MonitorTreeDirectoryNode[],
  options?: { showDeviceCountInTitle?: boolean; sipNameMap?: Map<string, string> },
): TreeItem[] {
  const showCount = options?.showDeviceCountInTitle !== false;
  const sipNameMap = options?.sipNameMap;

  const mapDirectory = (dir: MonitorTreeDirectoryNode): TreeItem => {
    const children: TreeItem[] = [];
    if (dir.children?.length) {
      children.push(...dir.children.map(mapDirectory));
    }
    if (dir.devices?.length) {
      appendDevicesToMonitorTreeChildren(children, dir.devices, sipNameMap);
    }
    const deviceCount = dir.device_count ?? dir.devices?.length ?? 0;
    return {
      key: `dir_${dir.id}`,
      title: showCount ? `${dir.name}（${deviceCount}）` : dir.name,
      isDirectory: true,
      selectable: false,
      icon: 'ant-design:folder-outlined',
      children: children.length ? children : undefined,
    } as TreeItem;
  };

  return (directories || []).map(mapDirectory);
}

export function collectMonitorTreeExpandedKeys(nodes: TreeItem[]): string[] {
  const keys: string[] = [];
  const walk = (list: TreeItem[]) => {
    list.forEach((n) => {
      const key = String(n.key);
      if (key.startsWith('dir_') || key.startsWith('gb_dev_')) {
        keys.push(key);
      }
      if (n.children?.length) walk(n.children as TreeItem[]);
    });
  };
  walk(nodes);
  return keys;
}

export function findMonitorTreeNodeByKey(nodes: TreeItem[], key: string): TreeItem | null {
  for (const node of nodes) {
    if (node.key === key) return node;
    if (node.children?.length) {
      const found = findMonitorTreeNodeByKey(node.children as TreeItem[], key);
      if (found) return found;
    }
  }
  return null;
}

/** 按设备 ID 在监控树中查找直连设备节点 */
export function findMonitorDeviceById(
  nodes: TreeItem[],
  deviceId: string,
): MonitorTreeDeviceNode | null {
  for (const node of nodes) {
    const key = String(node.key ?? '');
    if (key === `device_${deviceId}` && (node as any).device) {
      return (node as any).device as MonitorTreeDeviceNode;
    }
    if (node.children?.length) {
      const found = findMonitorDeviceById(node.children as TreeItem[], deviceId);
      if (found) return found;
    }
  }
  return null;
}

/** 国标通道在目录树中对应的已同步 device 记录（含 ai_http_stream） */
export function findMonitorGbDeviceByChannel(
  nodes: TreeItem[],
  sipDeviceId: string,
  channelId: string,
): MonitorTreeDeviceNode | null {
  const targetSource = `gb28181://${sipDeviceId}/${channelId}`.toLowerCase();
  const walk = (list: TreeItem[]): MonitorTreeDeviceNode | null => {
    for (const node of list) {
      const device = (node as any).device as MonitorTreeDeviceNode | undefined;
      const gb = (node as any).gbChannel as { sipDeviceId: string; channelId: string } | undefined;
      if (gb?.sipDeviceId === sipDeviceId && gb?.channelId === channelId && device) {
        return device;
      }
      if (device?.source?.toLowerCase() === targetSource) {
        return device;
      }
      if (node.children?.length) {
        const found = walk(node.children as TreeItem[]);
        if (found) return found;
      }
    }
    return null;
  };
  return walk(nodes);
}

/** 从监控树节点 key 解析目录 ID */
export function parseMonitorDirectoryId(key: string): number | null {
  if (!key.startsWith('dir_')) return null;
  const id = Number(key.slice(4));
  return Number.isFinite(id) ? id : null;
}

/** 节点是否可移动到目录（直连设备或已入库的国标通道） */
export function getMonitorNodeMoveableDeviceId(node: TreeItem): string | null {
  const key = String(node.key ?? '');
  if (key.startsWith('device_')) {
    const device = (node as any).device as MonitorTreeDeviceNode | undefined;
    return device?.id ?? key.slice('device_'.length);
  }
  if (key.startsWith('gb_ch_')) {
    const device = (node as any).device as MonitorTreeDeviceNode | undefined;
    return device?.id ?? null;
  }
  return null;
}

/** 是否显示单设备「移动到目录」操作 */
export function canShowMonitorDeviceMoveAction(node: TreeItem): boolean {
  return !!getMonitorNodeMoveableDeviceId(node);
}

/** 是否显示目录「批量移动」操作 */
export function canShowMonitorDirectoryBatchMoveAction(node: TreeItem): boolean {
  return String(node.key ?? '').startsWith('dir_');
}

/** 收集目录节点下所有可移动设备（含子目录） */
export function collectMoveableDevicesUnderNode(node: TreeItem): MonitorTreeDeviceNode[] {
  const result: MonitorTreeDeviceNode[] = [];
  const seen = new Set<string>();

  const pushDevice = (device: MonitorTreeDeviceNode) => {
    if (!device?.id || seen.has(device.id)) return;
    seen.add(device.id);
    result.push(device);
  };

  const walk = (n: TreeItem) => {
    const deviceId = getMonitorNodeMoveableDeviceId(n);
    if (deviceId) {
      const device = (n as any).device as MonitorTreeDeviceNode | undefined;
      if (device) {
        pushDevice(device);
      } else {
        pushDevice({
          type: 'device',
          id: deviceId,
          name: String(n.title ?? deviceId),
        });
      }
    }
    if (n.children?.length) {
      (n.children as TreeItem[]).forEach(walk);
    }
  };

  walk(node);
  return result;
}

/** 统计可点播叶子（直连设备 + 国标通道） */
export function countMonitorTreePlayableLeaves(nodes: TreeItem[]): number {
  let count = 0;
  const walk = (list: TreeItem[]) => {
    list.forEach((node) => {
      const key = String(node.key ?? '');
      if (key.startsWith('device_') || key.startsWith('gb_ch_')) {
        count++;
      }
      if (node.children?.length) walk(node.children as TreeItem[]);
    });
  };
  walk(nodes);
  return count;
}
