import type { ClusterLaneVO, ComputeNodeVO } from '@/api/device/node';
import { isPlatformNode } from './platformNode';

export const LOCAL_LANE_KEY = 'local';
export const ACTIVE_LANE_STORAGE_KEY = 'easyaiot_active_control_plane_lane';

export function readActiveLaneKey(): string {
  return sessionStorage.getItem(ACTIVE_LANE_STORAGE_KEY) || LOCAL_LANE_KEY;
}

export function writeActiveLaneKey(laneKey: string) {
  sessionStorage.setItem(ACTIVE_LANE_STORAGE_KEY, laneKey);
}

export function laneLabel(lane: ClusterLaneVO): string {
  const name = lane.centralNode?.name || lane.laneKey;
  if (lane.isLocal) return `${name}（本机）`;
  return name;
}

export function flattenLaneNodes(lane: ClusterLaneVO): ComputeNodeVO[] {
  const nodes: ComputeNodeVO[] = [];
  if (lane.centralNode) nodes.push(lane.centralNode);
  if (lane.workerNodes?.length) nodes.push(...lane.workerNodes);
  return nodes;
}

export function filterNodesByLane(nodes: ComputeNodeVO[], lane: ClusterLaneVO | null | undefined): ComputeNodeVO[] {
  if (!lane) return nodes;
  const allowedIds = new Set(
    flattenLaneNodes(lane)
      .map((node) => node.id)
      .filter((id): id is number => id != null),
  );
  return nodes.filter((node) => node.id != null && allowedIds.has(node.id));
}

export function findLaneByKey(lanes: ClusterLaneVO[], laneKey?: string | null): ClusterLaneVO | undefined {
  const key = laneKey || LOCAL_LANE_KEY;
  return lanes.find((lane) => lane.laneKey === key) || lanes.find((lane) => lane.isLocal) || lanes[0];
}

export function localLaneWorkers(lane: ClusterLaneVO): ComputeNodeVO[] {
  return (lane.workerNodes || []).filter((node) => !isPlatformNode(node) && !node.isRemote);
}

export function canManageLaneWorkers(lane: ClusterLaneVO): boolean {
  return lane.isLocal === true;
}

export function laneSyncStatusColor(status?: string): string {
  if (status === 'online' || status === 'synced') return 'success';
  if (status === 'offline') return 'error';
  return 'default';
}
