/** 节点角色视觉配置（Icon 组合 + 主题色，对齐存储空间文件夹图标方案） */
export interface NodeRoleVisual {
  coverClass: string;
  iconClass: string;
  bodyIcon: string;
  roleMarkIcon: string;
}

export const NODE_ROLE_VISUAL: Record<string, NodeRoleVisual> = {
  compute: {
    coverClass: 'node-card-cover--compute',
    iconClass: 'node-server-icon--compute',
    bodyIcon: 'mdi:server',
    roleMarkIcon: 'mdi:chip',
  },
  media: {
    coverClass: 'node-card-cover--media',
    iconClass: 'node-server-icon--media',
    bodyIcon: 'mdi:server',
    roleMarkIcon: 'mdi:cast',
  },
  hybrid: {
    coverClass: 'node-card-cover--hybrid',
    iconClass: 'node-server-icon--hybrid',
    bodyIcon: 'mdi:server',
    roleMarkIcon: 'mdi:lan',
  },
};

export function getNodeRoleVisual(role?: string): NodeRoleVisual {
  return NODE_ROLE_VISUAL[role || ''] || NODE_ROLE_VISUAL.compute;
}
