import type { Router } from 'vue-router';
import { NODE_SERVICE_TAB, resolveOnboardServiceTab, type NodeServiceTabKey } from './constants';
import { requestNodePageTab } from './useNodePageTab';

function isNodeIndexRoute(router: Router) {
  return router.currentRoute.value.path === '/node/index';
}

function resolveTabKey(tab: NodeServiceTabKey | string) {
  return tab in NODE_SERVICE_TAB ? NODE_SERVICE_TAB[tab as NodeServiceTabKey] : String(tab);
}

export function navigateToNodeServiceTab(
  router: Router,
  tab: NodeServiceTabKey | string,
  nodeId?: number,
) {
  const tabKey = resolveTabKey(tab);
  if (isNodeIndexRoute(router)) {
    requestNodePageTab({ tab: tabKey, nodeId });
    return;
  }
  router.push({
    path: '/node/index',
    query: {
      tab: tabKey,
      ...(nodeId ? { nodeId: String(nodeId) } : {}),
    },
  });
}

/** 泳道批量操作：携带多节点跳转部署 Tab */
export function navigateToNodeBatchTab(router: Router, tab: string, nodeIds: number[]) {
  if (!nodeIds.length) return;
  if (isNodeIndexRoute(router)) {
    requestNodePageTab({ tab, nodeIds });
    return;
  }
  router.push({
    path: '/node/index',
    query: {
      tab,
      nodeIds: nodeIds.join(','),
    },
  });
}

export function navigateToOnboardService(
  router: Router,
  node: { id?: number; nodeRole?: string | null },
) {
  if (!node.id) return;
  navigateToNodeServiceTab(router, resolveOnboardServiceTab(node.nodeRole), node.id);
}
