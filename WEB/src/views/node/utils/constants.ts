import { getCachedPlatformLanIp, fetchPlatformLanIp, needsPlatformLanIp } from './platformHost';

/** 阿里云控制台风格主色与排版 */
export const NODE_THEME = {
  primary: '#266cfb',
  primaryHover: '#1a5ae8',
  primaryLight: '#e8f0ff',
  primaryBg: '#f0f5ff',
  bg: '#ffffff',
  border: '#ebebeb',
  textPrimary: '#181818',
  textBody: '#333333',
  textSecondary: '#666666',
  textMuted: '#999999',
  fontTitle: '18px',
  fontSubtitle: '16px',
  fontBody: '14px',
  fontCaption: '13px',
  cardShadow: '0 2px 8px rgba(0, 0, 0, 0.06)',
  cardShadowHover: '0 6px 20px rgba(38, 108, 251, 0.12)',
  borderRadius: '8px',
} as const;

/**
 * 节点模块 — 用户可见术语规范（全模块唯一引用来源）
 *
 * 概念分层：
 * - 纳管：将节点纳入平台管理的过程（状态：待纳管 → 在线）
 * - 上线：节点变为「在线」，节点代理心跳正常
 * - 接入：网络与配置连通（平台接入地址、SSH）
 * - 部署：远程安装或更新组件（节点代理、流媒体服务）
 * - 运维：对已部署服务的启停与卸载
 *
 * 固定用词：
 * - 节点代理（UI 不写 Agent / 代理服务）
 * - 代理令牌（UI 不写 Agent Token）
 * - 流媒体服务（UI 不写 流媒体引擎 / 媒体栈）
 * - 平台接入地址（UI 不写 控制面 / 远程接入地址）
 * - 部署（UI 不写 安装）
 * - 纳管前检查（UI 不写 接入检查）
 * - 验证上线（纳管末步，不写 上线检查）
 * - 接入诊断（连通性排查，与纳管前检查区分）
 */
export const NODE_TERM = {
  agent: '节点代理',
  agentToken: '代理令牌',
  agentPort: '节点代理端口',
  mediaService: '流媒体服务',
  mediaPort: '流媒体端口',
  platformUrl: '平台接入地址',
  remotePlatformUrl: '目标机平台接入地址',
  onboard: '纳管',
  onboardDrawer: '节点纳管',
  continueOnboard: '继续纳管',
  completeOnboard: '完成纳管',
  verifyOnline: '验证上线',
  preCheck: '纳管前检查',
  accessDiagnostic: '接入诊断',
  serviceDeploy: '服务部署',
  resourceMonitor: '资源监控',
  nodeConfig: '节点配置',
  nodeDetail: '节点详情',
  nodeManage: '节点管理',
  clusterOverview: '集群概览',
  pendingTitle: '节点待纳管',
  offlineTitle: '节点离线',
  maintenanceTitle: '维护模式',
  onlineSuccess: '节点已上线',
  notOnlineYet: '节点尚未上线',
  deploy: '部署',
  redeploy: '重新部署',
  remoteDeploy: '远程部署',
  ops: '运维操作',
  deployCheck: '部署检测',
  sshCheck: 'SSH 连通性检测',
  editNode: '编辑节点',
  addNode: '添加节点',
  viewDetail: '详情',
  attentionNodes: '需关注节点',
} as const;

export const NODE_STATUS_MAP: Record<string, { text: string; color: string }> = {
  pending: { text: '待纳管', color: 'processing' },
  online: { text: '在线', color: 'success' },
  offline: { text: '离线', color: 'error' },
  maintenance: { text: '维护中', color: 'warning' },
};

/** 卡片状态徽标样式 */
export const NODE_STATUS_BADGE: Record<string, { bg: string; color: string; border: string }> = {
  pending: { bg: '#f0f5ff', color: '#2f54eb', border: '#adc6ff' },
  online: { bg: '#f6ffed', color: '#389e0d', border: '#b7eb8f' },
  offline: { bg: '#fff1f0', color: '#cf1322', border: '#ffa39e' },
  maintenance: { bg: '#fffbe6', color: '#d48806', border: '#ffe58f' },
};

export const NODE_ROLE_MAP: Record<string, string> = {
  compute: '计算节点',
  media: '媒体节点',
  hybrid: '混合节点',
};

export const NODE_ROLE_DESC: Record<string, string> = {
  compute: '用于 AI 模型部署、算法任务远程运行',
  media: '用于 SRS/ZLM 流媒体集群，设备拉流/推流',
  hybrid: '同时承担计算与媒体调度',
};

/**
 * 节点监控术语规范（全模块统一引用，避免歧义）
 *
 * - 内存：系统 RAM，与 GPU 显存严格区分
 * - 显存：GPU VRAM，不以「GPU」单独指代显存占用
 * - 磁盘：本地文件系统根分区，与对象存储（MinIO）区分
 * - 使用率：百分比占用指标的统一后缀
 * - 利用率：仅用于 GPU 算力（compute util）
 */
export const NODE_METRIC = {
  cpu: 'CPU',
  cpuUsage: 'CPU 使用率',
  mem: '内存',
  memUsage: '内存使用率',
  vram: '显存',
  vramUsage: '显存使用率',
  gpuUtil: 'GPU 利用率',
  disk: '磁盘',
  diskUsage: '磁盘使用率',
  runningTasks: '运行任务',
} as const;

/** 集群概览「查看节点」下拉中表示全部节点的 sentinel 值 */
export const OVERVIEW_ALL_NODES_ID = 0;

/** 折线图采样间隔（秒） */
export const TREND_SAMPLE_INTERVAL_DEFAULT = 5;
export const TREND_SAMPLE_INTERVAL_OPTIONS = [
  { label: '1 秒', value: 1 },
  { label: '5 秒', value: 5 },
  { label: '10 秒', value: 10 },
] as const;
export type TrendSampleIntervalSec = (typeof TREND_SAMPLE_INTERVAL_OPTIONS)[number]['value'];

export const NODE_DASHBOARD = {
  statTotal: '节点总数',
  statGpuCount: 'GPU 卡数',
  statRunningTasks: '运行任务',
  statVramCapacity: '显存',
  statMemCapacity: '内存',
  statDiskCapacity: '磁盘',
  statCapacityUsageAvg: '平均',
  statOnlineRate: '节点在线率',
  overviewNodeFocus: '查看节点',
  overviewNodeFocusAll: '全部节点（集群汇总）',
  overviewBackToAll: '返回全部节点',
  overviewNodeFocusHint: '选择单个节点后，下方资源图表仅展示该节点数据',
  clusterLoad: '集群资源负载',
  sectionVram: '节点显存分布',
  sectionVramHint: '各 GPU 卡的显存使用率',
  sectionDisk: '节点磁盘使用',
  sectionDiskHint: '根分区磁盘使用率，按使用率从高到低排列',
  sectionResource: '节点资源概览',
  sectionResourceHint: 'CPU、内存、显存、磁盘一览',
  avgVramUsage: '平均显存',
  avgCpuUsage: '平均 CPU',
  avgMemUsage: '平均内存',
  avgDiskUsage: '平均磁盘',
  trendViewCluster: '集群平均',
  trendViewNode: '按节点',
  trendMetricLabel: '指标',
  trendNodeFilter: '节点筛选',
  trendNodeFilterAll: '全部节点',
  trendClusterHint: '按节点展示 CPU 用量（%，多核可超过 100%）及内存 / 显存 / 磁盘已用量',
  trendClusterMetricHint: '左轴：各节点 CPU 用量（%）；右轴：各节点内存 / 显存 / 磁盘已用量',
  trendNodeHint: '按节点对比单一指标；CPU 为用量（%），内存 / 显存 / 磁盘为已用量',
  trendNodeVolumeHint: '当前指标（已用量）：',
  trendNodePercentHint: '当前指标（GPU 利用率）：',
  trendAxisPercent: 'CPU 用量 %',
  trendAxisVolume: '已用量',
  trendSampleIntervalLabel: '采样间隔',
  trendSampleIntervalHint: '折线图追加数据点的时间间隔，间隔内仅更新最新点',
  wsConnected: '实时推送中',
  wsConnecting: '连接中…',
  wsDisconnected: '连接已断开，正在重连',
  vramTight: '显存不足',
  diskTight: '磁盘不足',
  usageHigh: '使用率偏高',
  diskWarningNodes: (count: number) => `${count} 台节点磁盘不足`,
  colName: '名称',
  colHost: 'IP 地址',
  colStatus: '状态',
  diskColUsage: '使用率',
  diskColCapacity: '容量',
  vramColUsed: '已用',
  noComputeNodes: '暂无计算节点数据',
  noVramData: '暂无在线节点的显存数据。请确认已安装 NVIDIA 驱动，且节点代理心跳正常上报。',
  noDiskData: '暂无磁盘使用数据。请确认节点代理已上线并正常上报心跳。',
} as const;

/** 节点详情抽屉 */
export const NODE_DETAIL = {
  title: NODE_TERM.nodeDetail,
  tabResource: NODE_TERM.resourceMonitor,
  tabConfig: NODE_TERM.nodeConfig,
  tabAccess: NODE_TERM.preCheck,
  tabMediaDeploy: NODE_TERM.mediaService,
  tabAgentDeploy: NODE_TERM.agent,
  sectionResource: '资源使用',
  sectionResourceHint: 'CPU、内存、显存、磁盘占用，与集群概览维度一致',
  sectionConfig: NODE_TERM.nodeConfig,
  sectionAccess: NODE_TERM.preCheck,
  sectionMedia: NODE_TERM.mediaService,
  sectionAgent: NODE_TERM.agent,
  sectionVerify: NODE_TERM.verifyOnline,
  gpuSection: 'GPU 算力与显存',
  gpuSectionHint: 'GPU 利用率与显存使用率分开展示',
  noMetrics: '暂无资源数据。节点上线且节点代理心跳正常后将自动上报。',
  pendingAlert: `节点待纳管，请完成${NODE_TERM.mediaService}（如适用）与${NODE_TERM.agent}部署。`,
  offlineAlert: `节点离线，请检查${NODE_TERM.agent}、网络连通性或维护模式设置。`,
  maintenanceAlert: '节点处于维护模式，不会参与任务调度。',
  actionEdit: NODE_TERM.editNode,
  actionSetup: NODE_TERM.continueOnboard,
  actionRefresh: '刷新状态',
  actionMaintenance: '维护模式',
  actionResetToken: `重置${NODE_TERM.agentToken}`,
  footerClose: '关闭',
  pendingMessage: NODE_TERM.pendingTitle,
  offlineMessage: NODE_TERM.offlineTitle,
  maintenanceMessage: NODE_TERM.maintenanceTitle,
} as const;

/** 页面级 Tab */
export const NODE_PAGE = {
  clusterOverview: NODE_TERM.clusterOverview,
  nodeManage: NODE_TERM.nodeManage,
} as const;

/** 集群洞察文案 */
export const NODE_INSIGHT = {
  noNodesTitle: '尚未接入计算节点',
  noNodesDesc: `添加节点并完成${NODE_TERM.agent}纳管后，可在此查看推理调度能力与资源余量。`,
  noNodesAction: `切换到「${NODE_TERM.nodeManage}」添加节点`,
  clusterDownTitle: '集群当前不可用',
  clusterDownDesc: '全部节点离线，模型部署与算法任务将无法被调度到任何机器。',
  clusterDownAction: `请检查${NODE_TERM.agent}、网络连通性或维护模式设置`,
  taskPressureTitle: '推理任务接近容量上限',
  overloadedTitle: (n: number) => `${n} 台节点资源紧张`,
  overloadedDesc: '部分计算节点 CPU、内存或磁盘使用率偏高，可能影响推理时延或模型落盘。',
  overloadedAction: '查看下方节点列表，考虑迁移任务或扩容',
  notReadyTitle: (n: number) => `${n} 台计算节点未就绪`,
  notReadyDesc: '离线或待纳管的节点不会参与调度，可用算力低于集群总量。',
  notReadyAction: `在「${NODE_TERM.nodeManage}」中完成纳管或排查离线原因`,
  healthyTitle: '集群运行正常',
  pendingReason: `待完成${NODE_TERM.agent}纳管`,
  nodeOffline: '节点离线',
} as const;

export interface GpuInfoItem {
  id?: number;
  name?: string;
  util?: number;
  mem_used_mb?: number;
  mem_total_mb?: number;
}

export function parseGpuInfo(raw?: string | GpuInfoItem[] | null): GpuInfoItem[] {
  if (!raw) return [];
  if (Array.isArray(raw)) return raw;
  try {
    const parsed = JSON.parse(raw);
    return Array.isArray(parsed) ? parsed : [];
  } catch {
    return [];
  }
}

export function formatGpuSummary(raw?: string | GpuInfoItem[] | null): string {
  const list = parseGpuInfo(raw);
  if (!list.length) return '-';
  if (list.length === 1) return list[0].name || `GPU ${list[0].id ?? 0}`;
  return `${list.length} 张 GPU`;
}


export function getControlPlaneAgentUrl(): string {
  const envBase = import.meta.env.VITE_GLOB_BASE_URL?.trim();
  const windowBase =
    typeof window !== 'undefined' ? `${window.location.protocol}//${window.location.host}` : '';
  const base = resolveControlPlaneBase(envBase, windowBase, getCachedPlatformLanIp());
  return `${String(base).replace(/\/$/, '')}/admin-api/node/agent`;
}

/** 异步解析平台接入地址（localhost 时自动探测宿主机 IP） */
export async function resolveControlPlaneAgentUrl(): Promise<string> {
  const envBase = import.meta.env.VITE_GLOB_BASE_URL?.trim();
  const windowBase =
    typeof window !== 'undefined' ? `${window.location.protocol}//${window.location.host}` : '';
  if (needsPlatformLanIp(envBase, windowBase)) {
    await fetchPlatformLanIp();
  }
  return getControlPlaneAgentUrl();
}

/** 解析平台接入根地址：env/浏览器为 localhost 时使用探测到的宿主机 IP + 48080 */
function resolveControlPlaneBase(envBase?: string, windowBase?: string, lanIp?: string): string {
  const gatewayPort = '48080';
  const protocol =
    (() => {
      try {
        if (envBase) return new URL(envBase).protocol;
        if (windowBase) return new URL(windowBase).protocol;
      } catch {
        /* ignore */
      }
      return 'http:';
    })();

  const resolveWithLanIp = (port: string) => {
    if (lanIp && !isLoopbackHost(lanIp)) {
      return `${protocol}//${lanIp}:${port}`;
    }
    return null;
  };

  if (!envBase) {
    if (windowBase) {
      try {
        const win = new URL(windowBase);
        if (!isLoopbackHost(win.hostname)) {
          return `${win.protocol}//${win.hostname}:${gatewayPort}`;
        }
        const lanBase = resolveWithLanIp(gatewayPort);
        if (lanBase) return lanBase;
      } catch {
        /* ignore */
      }
    }
    const lanBase = resolveWithLanIp(gatewayPort);
    if (lanBase) return lanBase;
    return windowBase || `http://${lanIp || 'localhost'}:${gatewayPort}`;
  }
  try {
    const env = new URL(envBase);
    const port = env.port || gatewayPort;
    if (isLoopbackHost(env.hostname)) {
      if (windowBase) {
        const win = new URL(windowBase);
        if (!isLoopbackHost(win.hostname)) {
          return `${win.protocol}//${win.hostname}:${port}`;
        }
      }
      const lanBase = resolveWithLanIp(port);
      if (lanBase) return lanBase;
    }
    return envBase;
  } catch {
    return envBase;
  }
}

function isLoopbackHost(host?: string): boolean {
  const h = host?.trim().toLowerCase();
  return !h || h === 'localhost' || h === '127.0.0.1';
}

export function isLocalControlPlaneUrl(url?: string): boolean {
  if (!url?.trim()) return false;
  try {
    const host = new URL(url.trim()).hostname;
    return host === 'localhost' || host === '127.0.0.1';
  } catch {
    return /localhost|127\.0\.0\.1/.test(url);
  }
}

const CONTROL_PLANE_URL_STORAGE_PREFIX = 'easyaiot_node_control_plane_url:';

/** 读取纳管抽屉中为节点保存的平台接入地址（跨步骤共享） */
export function loadNodeControlPlaneUrl(nodeId?: number): string {
  const fallback = getControlPlaneAgentUrl();
  if (!nodeId) return fallback;
  try {
    const saved = sessionStorage.getItem(`${CONTROL_PLANE_URL_STORAGE_PREFIX}${nodeId}`);
    if (saved?.trim() && !isLocalControlPlaneUrl(saved)) return saved.trim();
  } catch {
    /* ignore */
  }
  return fallback;
}

/** 异步加载平台接入地址：忽略 session 中的 localhost，并自动探测宿主机 IP */
export async function loadNodeControlPlaneUrlAsync(nodeId?: number): Promise<string> {
  if (nodeId) {
    try {
      const saved = sessionStorage.getItem(`${CONTROL_PLANE_URL_STORAGE_PREFIX}${nodeId}`);
      if (saved?.trim() && !isLocalControlPlaneUrl(saved)) return saved.trim();
    } catch {
      /* ignore */
    }
  }
  return resolveControlPlaneAgentUrl();
}

export function saveNodeControlPlaneUrl(nodeId: number | undefined, url: string) {
  if (!nodeId || !url?.trim()) return;
  try {
    sessionStorage.setItem(`${CONTROL_PLANE_URL_STORAGE_PREFIX}${nodeId}`, url.trim());
  } catch {
    /* ignore */
  }
}

/** Gateway 地址，供 SRS/ZLM Hook 回调 VIDEO 服务 */
export function getControlPlaneHookEndpoint(): { host: string; port: number } {
  const envBase = import.meta.env.VITE_GLOB_BASE_URL?.trim();
  const windowBase =
    typeof window !== 'undefined' ? `${window.location.protocol}//${window.location.host}` : '';
  const base = resolveControlPlaneBase(envBase, windowBase, getCachedPlatformLanIp());
  try {
    const url = new URL(base);
    const port = url.port ? Number(url.port) : url.protocol === 'https:' ? 443 : 80;
    return { host: url.hostname, port };
  } catch {
    const lanIp = getCachedPlatformLanIp();
    return { host: lanIp || '127.0.0.1', port: 48080 };
  }
}

function sanitizeNodeName(name?: string): string {
  const raw = (name || 'media-node').trim().toLowerCase();
  const slug = raw.replace(/[^a-z0-9-]+/g, '-').replace(/^-+|-+$/g, '');
  return slug || 'media-node';
}

export interface MediaStackScriptParams {
  name?: string;
  host?: string;
  srsRtmpPort?: number;
  srsHttpPort?: number;
  srsApiPort?: number;
  zlmHttpPort?: number;
  zlmRtmpPort?: number;
  zlmRtpPortMin?: number;
  zlmRtpPortMax?: number;
}

function isValidHost(host?: string): boolean {
  const h = host?.trim();
  if (!h || h.includes('<') || h.includes('>')) return false;
  const ipv4 = /^(\d{1,3}\.){3}\d{1,3}$/;
  if (ipv4.test(h)) {
    return h.split('.').every((octet) => {
      const n = Number(octet);
      return Number.isInteger(n) && n >= 0 && n <= 255;
    });
  }
  return /^[a-zA-Z0-9]([a-zA-Z0-9.-]*[a-zA-Z0-9])?$/.test(h);
}

function isValidPort(port?: number): boolean {
  return typeof port === 'number' && Number.isFinite(port) && port >= 1 && port <= 65535;
}

function areMediaPortsValid(params?: MediaStackScriptParams): boolean {
  if (!params) return false;
  const ports = [
    params.srsRtmpPort,
    params.srsHttpPort,
    params.srsApiPort,
    params.zlmHttpPort,
    params.zlmRtmpPort,
    params.zlmRtpPortMin,
    params.zlmRtpPortMax,
  ];
  if (!ports.every(isValidPort)) return false;
  return (params.zlmRtpPortMin as number) <= (params.zlmRtpPortMax as number);
}

/** 媒体栈脚本是否可生成（角色为 media/hybrid 且 IP、端口已填写有效） */
export function isMediaStackScriptReady(
  params: (MediaStackScriptParams & { nodeRole?: string }) | undefined,
): boolean {
  if (!params || (params.nodeRole !== 'media' && params.nodeRole !== 'hybrid')) return false;
  return isValidHost(params.host) && areMediaPortsValid(params);
}

export interface MediaStackGuideItem {
  key: string;
  label: string;
  done: boolean;
  hint?: string;
}

export interface MediaStackGuideState {
  isMediaRole: boolean;
  isReady: boolean;
  pendingItems: MediaStackGuideItem[];
  readySummary: string;
}

/** 媒体栈部署引导状态（用于 UI 展示待填项与就绪摘要） */
export function getMediaStackGuideState(
  params: (MediaStackScriptParams & { nodeRole?: string }) | undefined,
): MediaStackGuideState {
  const isMediaRole = params?.nodeRole === 'media' || params?.nodeRole === 'hybrid';
  const hostDone = isValidHost(params?.host);
  const portsDone = areMediaPortsValid(params);
  const hook = getControlPlaneHookEndpoint();

  const pendingItems: MediaStackGuideItem[] = [
    {
      key: 'host',
      label: '主机地址',
      done: hostDone,
      hint: '目标节点 IP 或主机名',
    },
    {
      key: 'ports',
      label: '媒体端口',
      done: portsDone,
      hint: 'SRS/ZLM 监听端口，默认配置适用于空闲节点',
    },
  ];

  const isReady = isMediaStackScriptReady(params);
  let readySummary = '';
  if (isReady && params?.host) {
    readySummary =
      `目标机 ${params.host}：` +
      `SRS RTMP ${params.srsRtmpPort} / HTTP ${params.srsHttpPort} / API ${params.srsApiPort}，` +
      `ZLM HTTP ${params.zlmHttpPort} / RTMP ${params.zlmRtmpPort}，` +
      `RTP ${params.zlmRtpPortMin}-${params.zlmRtpPortMax}，` +
      `Hook 回调 ${hook.host}:${hook.port}`;
  }

  return { isMediaRole, isReady, pendingItems, readySummary };
}

/** 流媒体远程自动部署进度步骤（与后端返回步骤名一致） */
export const MEDIA_STACK_DEPLOY_PENDING_STEPS = [
  '准备离线镜像',
  'SSH 连接',
  '检测已有服务',
  'Docker',
  '清理旧目录',
  '同步 media-cluster',
  'Docker Compose',
  '导入镜像',
  '启动服务',
  '服务验证',
] as const;

export const MEDIA_CLUSTER_REMOTE_DIR = '/opt/easyaiot/media-cluster';

export const MEDIA_CLUSTER_DIR = '.scripts/media-cluster';

/** 在本机导出 SRS/ZLM 离线镜像 */
export function buildMediaStackExportImagesCommand(): string {
  return `bash ${MEDIA_CLUSTER_DIR}/export_media_images.sh`;
}

/** 手动部署完整指引（含导出镜像、同步、目标机执行） */
export function buildMediaStackManualContent(params: MediaStackScriptParams = {}): string {
  const host = params.host?.trim() || '<目标服务器>';
  const exportCmd = buildMediaStackExportImagesCommand();
  const cleanRemote = `ssh root@${host} 'rm -rf ${MEDIA_CLUSTER_REMOTE_DIR}/srs ${MEDIA_CLUSTER_REMOTE_DIR}/zlm; rm -f ${MEDIA_CLUSTER_REMOTE_DIR}/install_media_stack.sh ${MEDIA_CLUSTER_REMOTE_DIR}/install_docker.sh ${MEDIA_CLUSTER_REMOTE_DIR}/docker-compose.media-node.yml; mkdir -p ${MEDIA_CLUSTER_REMOTE_DIR}/images'`;
  const rsync = `rsync -avz --progress ${MEDIA_CLUSTER_DIR}/ root@${host}:${MEDIA_CLUSTER_REMOTE_DIR}/`;
  const deployScript = buildMediaStackInstallScript(params);

  return `# ========== 步骤 1：在本机（平台服务器）拉取并导出离线镜像（必须） ==========
# 目标机不联网拉取镜像，所有镜像均在本机下载后通过 rsync 同步
${exportCmd}

# 导出产物（自动部署也会在本机缺失时执行此步骤）：
#   ${MEDIA_CLUSTER_DIR}/images/ossrs-srs-5.tar
#   ${MEDIA_CLUSTER_DIR}/images/zlmediakit-master.tar

# ========== 步骤 2：清理目标机旧配置并增量同步 ==========
# 仅清理脚本/配置目录，保留已有 images/*.tar；目标机已有 Docker 镜像时可跳过对应 tar
${cleanRemote}
${rsync}

# ========== 步骤 3：SSH 登录目标主机，docker load 导入并启动 SRS/ZLM ==========
${deployScript}`;
}

/** 生成可在媒体节点直接复制执行的 SRS + ZLM 部署脚本 */
export function buildMediaStackInstallScript(params: MediaStackScriptParams = {}): string {
  const hook = getControlPlaneHookEndpoint();
  const nodeName = sanitizeNodeName(params.name || params.host);
  const host = params.host?.trim() || '';
  const srsRtmp = params.srsRtmpPort ?? 1935;
  const srsHttp = params.srsHttpPort ?? 8080;
  const srsApi = params.srsApiPort ?? 1985;
  const zlmHttp = params.zlmHttpPort ?? 6080;
  const zlmRtmp = params.zlmRtmpPort ?? 10935;
  const zlmRtpMin = params.zlmRtpPortMin ?? 30000;
  const zlmRtpMax = params.zlmRtpPortMax ?? 30500;

  return `#!/usr/bin/env bash
# EasyAIoT 媒体节点 — SRS + ZLMediaKit 一键部署
# 在目标服务器 ${host} 上执行；若 SRS/ZLM 已在运行则自动跳过。
set -euo pipefail

# ---------- 1. 配置（与控制台表单一致，可按需修改） ----------
export MEDIA_CLUSTER_ROOT="/opt/easyaiot/media-cluster"
export MEDIA_NODE_NAME="${nodeName}"
export MEDIA_NODE_HOST="${host}"
export MEDIA_HOOK_HOST="${hook.host}"
export MEDIA_HOOK_PORT="${hook.port}"
export SRS_CANDIDATE_IP="${host}"
export SRS_RTMP_PORT=${srsRtmp}
export SRS_HTTP_PORT=${srsHttp}
export SRS_API_PORT=${srsApi}
export ZLM_HTTP_PORT=${zlmHttp}
export ZLM_RTMP_PORT=${zlmRtmp}
export ZLM_RTP_PORT_MIN=${zlmRtpMin}
export ZLM_RTP_PORT_MAX=${zlmRtpMax}
export ZLM_SECRET="EasyAIoT_Media_Secret"

# ---------- 2. 前置检查 ----------
if ! command -v docker >/dev/null 2>&1; then
  echo "[ERROR] 未安装 Docker，请先安装 Docker Engine"
  exit 1
fi
if ! docker info >/dev/null 2>&1; then
  echo "[ERROR] Docker 未运行或无权限（可尝试 sudo 或 usermod -aG docker \\$USER）"
  exit 1
fi
if [[ ! -f "\${MEDIA_CLUSTER_ROOT}/install_media_stack.sh" ]]; then
  echo "[ERROR] 未找到 \${MEDIA_CLUSTER_ROOT}/install_media_stack.sh"
  echo "请先将仓库 .scripts/media-cluster 同步到目标机，例如:"
  echo "  rsync -avz .scripts/media-cluster/ root@${host}:\${MEDIA_CLUSTER_ROOT}/"
  exit 1
fi

# ---------- 3. 执行部署（已运行则跳过） ----------
echo ">>> 开始部署媒体栈: \${MEDIA_NODE_NAME} @ \${MEDIA_NODE_HOST}"
echo ">>> Hook 回调: \${MEDIA_HOOK_HOST}:\${MEDIA_HOOK_PORT}"
bash "\${MEDIA_CLUSTER_ROOT}/install_media_stack.sh"

echo ""
echo "[OK] 媒体栈部署完成"
echo "  SRS API: http://\${MEDIA_NODE_HOST}:\${SRS_API_PORT}/api/v1/versions"
echo "  ZLM API: http://\${MEDIA_NODE_HOST}:\${ZLM_HTTP_PORT}/index/api/getServerConfig"`;
}

/** 添加节点时生成随机 Agent 监听端口（19100–19999，避开常见服务端口） */
export function generateDefaultAgentPort(): number {
  return 19100 + Math.floor(Math.random() * 900);
}

export const AGENT_INSTALL_DIR = '/opt/easyaiot/node-agent';

export function buildAgentEnvContent(node: {
  id?: number;
  agentPort?: number;
  agentToken?: string;
  controlPlaneUrl?: string;
}): string {
  const port = node.agentPort ?? 9100;
  const controlPlane = node.controlPlaneUrl?.trim() || getControlPlaneAgentUrl();
  return `# EasyAIoT Node Agent 配置
NODE_ID=${node.id ?? ''}
AGENT_TOKEN=${node.agentToken ?? 'your-agent-token-here'}
CONTROL_PLANE_URL=${controlPlane}
HEARTBEAT_INTERVAL=10
AGENT_LISTEN_HOST=0.0.0.0
AGENT_LISTEN_PORT=${port}
AI_ROOT=/opt/easyaiot/AI
VIDEO_ROOT=/opt/easyaiot/VIDEO
MEDIA_CLUSTER_ROOT=/opt/easyaiot/media-cluster
MINIO_ENDPOINT=http://localhost:9000
MINIO_ACCESS_KEY=minioadmin
MINIO_SECRET_KEY=your-secret
`;
}

/** 从控制面同步 Agent 文件到目标主机 */
export function buildAgentRsyncCommand(host?: string, sshUsername = 'root'): string {
  const targetHost = host?.trim() || '<目标服务器>';
  const user = sshUsername?.trim() || 'root';
  return `rsync -avz NODE/agent/ ${user}@${targetHost}:${AGENT_INSTALL_DIR}/`;
}

/** 目标主机上写入配置、安装并启动 Agent */
export function buildAgentInstallCommands(node: {
  id?: number;
  agentPort?: number;
  agentToken?: string;
  host?: string;
  controlPlaneUrl?: string;
}): string {
  const host = node.host?.trim() || '<目标服务器>';
  const env = buildAgentEnvContent(node).trimEnd();
  return `# 在目标服务器 ${host} 上执行
sudo mkdir -p ${AGENT_INSTALL_DIR}
cd ${AGENT_INSTALL_DIR}
sudo tee agent.env > /dev/null <<'EOF'
${env}
EOF
sudo bash install.sh
sudo systemctl enable --now easyaiot-node-agent
sudo systemctl status easyaiot-node-agent --no-pager`;
}

/** 一键部署脚本（需先完成 rsync 同步） */
export function buildAgentDeployScript(node: {
  id?: number;
  agentPort?: number;
  agentToken?: string;
  host?: string;
  controlPlaneUrl?: string;
}): string {
  return `${buildAgentRsyncCommand(node.host)}\n\n# 同步完成后 SSH 登录目标主机，执行：\n${buildAgentInstallCommands(node)}`;
}

/** 纳管抽屉表单布局（与模型管理抽屉一致） */
export const SETUP_FORM_LABEL_COL = { style: { width: '150px' } };
export const SETUP_FORM_WRAPPER_COL = { span: 21 };

/**
 * 节点纳管流程 — 步骤标题（引用 NODE_TERM，避免与详情 Tab 混用不同说法）
 */
export const SETUP_STEP_LABELS = {
  overview: { title: '确认配置', description: '检查信息' },
  media: { title: NODE_TERM.mediaService, description: 'SRS / ZLM' },
  agent: { title: NODE_TERM.agent, description: NODE_TERM.remoteDeploy },
  verify: { title: NODE_TERM.verifyOnline, description: '心跳确认' },
} as const;

/** 验证上线 — 平台状态自动轮询（间隔 × 次数 ≈ 总时长） */
export const VERIFY_STATUS_POLL_INTERVAL_MS = 4000;
export const VERIFY_STATUS_POLL_MAX_ATTEMPTS = 15;

export const SETUP_COPY = {
  agentName: NODE_TERM.agent,
  mediaService: NODE_TERM.mediaService,
  deployMode: '部署方式',
  deployModeAuto: `${NODE_TERM.remoteDeploy}（推荐）`,
  deployModeManual: '手动部署',
  deployConfig: '部署配置',
  deployScript: '部署脚本',
  deployProgress: '执行进度',
  credentials: '接入凭证',
  preCheck: NODE_TERM.preCheck,
  verifySection: NODE_TERM.verifyOnline,
  verifyUrlSection: NODE_TERM.platformUrl,
  verifyPolling: '自动检测平台状态',
  verifyPollingExhausted: `自动检测已结束，${NODE_TERM.notOnlineYet}。请运行${NODE_TERM.accessDiagnostic}排查，或确认${NODE_TERM.agent}已启动后手动刷新。`,
  verifyRetryPolling: '重新自动检测',
  verifyRefreshStatus: '刷新平台状态',
  verifyExpectedUrl: NODE_TERM.platformUrl,
  verifyRemoteUrl: '目标机 agent.env 配置值',
  verifyRunDiagnostic: NODE_TERM.accessDiagnostic,
  verifyDiagnosticHint: `${NODE_TERM.agent}启动后会向平台上报心跳；若长时间未上线，通常是 CONTROL_PLANE_URL 配置错误或网络不通。`,
  verifyIntro: `进入本步骤后将自动轮询平台节点状态（最多 15 次，约 1 分钟）；也可随时运行 ${NODE_TERM.accessDiagnostic} 排查问题。`,
  remoteDeploy: NODE_TERM.remoteDeploy,
  deployMediaBtn: `部署${NODE_TERM.mediaService}`,
  mediaDeployCheck: NODE_TERM.deployCheck,
  checkMediaDeployBtn: '检测部署状态',
  mediaOps: NODE_TERM.ops,
  stopSrsBtn: '停止 SRS',
  stopZlmBtn: '停止 ZLMediaKit',
  removeContainerBtn: '删除容器',
  removeImageBtn: '删除镜像',
  agentDeployCheck: NODE_TERM.deployCheck,
  checkAgentDeployBtn: '检测部署状态',
  agentOps: NODE_TERM.ops,
  stopAgentBtn: `停止${NODE_TERM.agent}`,
  removeAgentBtn: `删除${NODE_TERM.agent}`,
  deployAgentBtn: `部署${NODE_TERM.agent}`,
  redeployAgentBtn: `${NODE_TERM.redeploy}${NODE_TERM.agent}`,
  redeployAgentHint: `检测到目标机已有${NODE_TERM.agent}，将依次执行：停止 → 删除 → ${NODE_TERM.redeploy}`,
  flowMedia: `部署${NODE_TERM.mediaService} → 部署${NODE_TERM.agent} → ${NODE_TERM.verifyOnline}`,
  flowCompute: `部署${NODE_TERM.agent} → ${NODE_TERM.verifyOnline}`,
  readinessReady: '可以开始部署',
  readinessPending: '请先完善配置',
  nodeInfo: '节点信息',
  sshConnectivity: NODE_TERM.sshCheck,
  mediaPortConfigured: `${NODE_TERM.mediaService}${NODE_TERM.mediaPort}已配置`,
  completeOnboard: NODE_TERM.completeOnboard,
  editNode: NODE_TERM.editNode,
} as const;
