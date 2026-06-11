import { defHttp } from '@/utils/http/axios';

enum Api {
  Node = '/node',
}

type NodeRequestOptions = {
  errorMessageMode?: 'none' | 'message' | 'modal';
  isTransformResponse?: boolean;
  timeout?: number;
  signal?: AbortSignal;
};

const commonApi = (
  method: 'get' | 'post' | 'delete' | 'put',
  url: string,
  params = {},
  options: NodeRequestOptions = {},
) => {
  defHttp.setHeader({ 'X-Authorization': 'Bearer ' + localStorage.getItem('jwt_token') });
  const { isTransformResponse = true, errorMessageMode, timeout } = options;
  return defHttp[method](
    {
      url,
      headers: { ignoreCancelToken: true },
      timeout,
      ...params,
    },
    { isTransformResponse, errorMessageMode },
  );
};

export interface ComputeNodeVO {
  id?: number;
  name: string;
  host: string;
  sshPort?: number;
  agentPort?: number;
  status?: string;
  nodeRole: string;
  region?: string;
  tags?: Record<string, string>;
  capabilities?: Record<string, boolean>;
  maxGpuCount?: number;
  maxTaskCount?: number;
  weight?: number;
  remark?: string;
  agentToken?: string;
  sshUsername?: string;
  sshAuthType?: string;
  sshCredentialConfigured?: boolean;
  sshPassword?: string;
  sshPrivateKey?: string;
  sshLastTestAt?: string;
  sshLastTestOk?: boolean;
  lastHeartbeatAt?: string;
  cpuPercent?: number;
  memPercent?: number;
  memUsedBytes?: number;
  memTotalBytes?: number;
  diskPercent?: number;
  diskUsedBytes?: number;
  diskTotalBytes?: number;
  activeTasks?: number;
  gpuInfo?: string;
  createTime?: string;
  updateTime?: string;
}

export const createNode = (data: ComputeNodeVO, options?: Pick<NodeRequestOptions, 'errorMessageMode'>) => {
  return commonApi('post', Api.Node + '/create', { data }, options);
};

export const updateNode = (data: ComputeNodeVO) => {
  return commonApi('put', Api.Node + '/update', { data });
};

export const deleteNode = (id: number) => {
  return commonApi('delete', `${Api.Node}/delete?id=${id}`);
};

export const getNode = (id: number) => {
  return commonApi('get', Api.Node + '/get', { params: { id } });
};

export interface NodePageResult {
  data: {
    list: ComputeNodeVO[];
    total: number;
  };
}

/** 统一分页响应结构，兼容 axios 原生响应与 transform 后的多种形态 */
function normalizeNodePageResult(res: unknown): NodePageResult {
  const empty: NodePageResult = { data: { list: [], total: 0 } };
  if (!res || typeof res !== 'object') return empty;

  const r = res as Record<string, unknown>;

  if (Array.isArray(r.list)) {
    return { data: { list: r.list as ComputeNodeVO[], total: Number(r.total ?? 0) } };
  }

  const wrapped = r.data as Record<string, unknown> | undefined;
  if (wrapped && Array.isArray(wrapped.list)) {
    return { data: { list: wrapped.list as ComputeNodeVO[], total: Number(wrapped.total ?? 0) } };
  }

  // isTransformResponse: false 时返回 AxiosResponse，业务数据在 data.data
  const envelope = r.data as Record<string, unknown> | undefined;
  const page = envelope?.data as Record<string, unknown> | undefined;
  if (page && Array.isArray(page.list)) {
    return { data: { list: page.list as ComputeNodeVO[], total: Number(page.total ?? 0) } };
  }

  return empty;
}

export const getNodePage = async (params: Record<string, unknown>): Promise<NodePageResult> => {
  const res = await commonApi('get', Api.Node + '/page', { params });
  return normalizeNodePageResult(res);
};

export interface NodeMetricTrendPointVO {
  collectedAt: string;
  cpuPercent?: number;
  memPercent?: number;
  diskPercent?: number;
  gpuMemPercent?: number;
  gpuUtilPercent?: number;
  memUsedBytes?: number;
  diskUsedBytes?: number;
  gpuMemUsedBytes?: number;
  activeTasks?: number;
}

export interface NodeMetricTrendSeriesVO {
  nodeId: number;
  nodeName: string;
  host: string;
  status?: string;
  points: NodeMetricTrendPointVO[];
}

export interface NodeMetricTrendResult {
  series: NodeMetricTrendSeriesVO[];
}

export const getNodeMetricTrend = async (params?: {
  nodeIds?: number[];
  minutes?: number;
  maxPoints?: number;
}): Promise<NodeMetricTrendResult> => {
  const query: Record<string, unknown> = {
    minutes: params?.minutes ?? 30,
    maxPoints: params?.maxPoints ?? 120,
  };
  if (params?.nodeIds?.length) {
    query.nodeIds = params.nodeIds;
  }
  const res = await commonApi('get', Api.Node + '/metric-trend', { params: query });
  if (res && typeof res === 'object') {
    const r = res as Record<string, unknown>;
    if (Array.isArray(r.series)) {
      return { series: r.series as NodeMetricTrendSeriesVO[] };
    }
    const wrapped = r.data as Record<string, unknown> | undefined;
    if (wrapped && Array.isArray(wrapped.series)) {
      return { series: wrapped.series as NodeMetricTrendSeriesVO[] };
    }
  }
  return { series: [] };
};

export const testNodeSsh = (id: number) => {
  return commonApi('post', `${Api.Node}/test-ssh?id=${id}`);
};

export const resetAgentToken = (id: number) => {
  return commonApi('post', `${Api.Node}/reset-agent-token?id=${id}`);
};

/** 获取待纳管节点的 Agent 配置（含 Token，仅 pending 状态可用） */
export const getAgentSetup = (id: number) => {
  return commonApi('get', Api.Node + '/agent-setup', { params: { id } });
};

export interface PlatformHostVO {
  host: string;
  port: number;
}

/** 获取平台宿主机 IP（供 Agent 平台接入地址自动填充） */
export const getPlatformHost = () => {
  return commonApi('get', Api.Node + '/platform-host', { params: {} });
};

export const setNodeMaintenance = (id: number, enabled: boolean) => {
  return commonApi('post', `${Api.Node}/maintenance?id=${id}&enabled=${enabled}`);
};

export interface DeviceMediaBindingVO {
  deviceId: string;
  srsLiveNodeId?: number;
  srsAiNodeId?: number;
  zlmNodeId?: number;
  rtmpStream?: string;
  httpStream?: string;
  aiRtmpStream?: string;
  aiHttpStream?: string;
  zlmHost?: string;
  zlmHttpPort?: number;
  zlmRtmpPort?: number;
  region?: string;
  status?: string;
}

export const allocateDeviceMedia = (data: {
  deviceId: string;
  needSrsLive?: boolean;
  needSrsAi?: boolean;
  needZlm?: boolean;
  region?: string;
  httpPlayHost?: string;
}) => {
  return commonApi('post', Api.Node + '/media/allocate', { data });
};

export const deployMediaStack = (data: {
  nodeId: number;
  stackType: 'srs_live' | 'srs_ai' | 'zlm';
  env?: Record<string, string>;
}) => {
  return commonApi('post', Api.Node + '/media/deploy-stack', { data });
};

export interface MediaDeployStepVO {
  name: string;
  status: string;
  output?: string;
}

export interface MediaRemoteDeployResult {
  success?: boolean;
  message?: string;
  steps?: MediaDeployStepVO[];
}

export interface MediaStackCheckResult {
  success?: boolean;
  deployed?: boolean;
  srsRunning?: boolean;
  zlmRunning?: boolean;
  dockerReady?: boolean;
  composeReady?: boolean;
  message?: string;
  steps?: MediaDeployStepVO[];
}

export interface AgentCheckResult {
  success?: boolean;
  deployed?: boolean;
  installDirReady?: boolean;
  serviceRunning?: boolean;
  healthOk?: boolean;
  configOk?: boolean;
  nodeIdMatch?: boolean;
  tokenMatch?: boolean;
  controlPlaneReachable?: boolean;
  controlPlaneUrl?: string;
  expectedControlPlaneUrl?: string;
  message?: string;
  steps?: MediaDeployStepVO[];
}

/** 解析 isTransformResponse:false 时的 { code, data } 信封 */
function unwrapNodeApiData<T>(res: unknown): T {
  const body = (res as { data?: unknown })?.data ?? res;
  if (body && typeof body === 'object' && body !== null && 'code' in body && 'data' in body) {
    return (body as { data: T }).data;
  }
  return body as T;
}

/** 通过 SSH 自动部署 SRS + ZLM 媒体栈（本机导出+同步离线镜像，超时 45 分钟） */
export const deployMediaStackBySsh = async (
  nodeId: number,
  options?: { signal?: AbortSignal },
): Promise<MediaRemoteDeployResult> => {
  const res = await commonApi(
    'post',
    `${Api.Node}/media/deploy-ssh?nodeId=${nodeId}`,
    { signal: options?.signal },
    { isTransformResponse: false, timeout: 45 * 60 * 1000 },
  );
  return unwrapNodeApiData<MediaRemoteDeployResult>(res);
};

/** 通过 SSH 停止目标机 SRS 或 ZLMediaKit */
export const stopMediaServiceBySsh = async (
  nodeId: number,
  service: 'srs' | 'zlm',
): Promise<MediaRemoteDeployResult> => {
  const res = await commonApi(
    'post',
    `${Api.Node}/media/stop-ssh?nodeId=${nodeId}&service=${service}`,
    {},
    { isTransformResponse: false, timeout: 3 * 60 * 1000 },
  );
  return unwrapNodeApiData<MediaRemoteDeployResult>(res);
};

/** 通过 SSH 删除目标机 SRS/ZLM 媒体容器 */
export const removeMediaContainerBySsh = async (nodeId: number): Promise<MediaRemoteDeployResult> => {
  const res = await commonApi(
    'post',
    `${Api.Node}/media/remove-container-ssh?nodeId=${nodeId}`,
    {},
    { isTransformResponse: false, timeout: 3 * 60 * 1000 },
  );
  return unwrapNodeApiData<MediaRemoteDeployResult>(res);
};

/** 通过 SSH 删除目标机 SRS/ZLM Docker 镜像 */
export const removeMediaImageBySsh = async (nodeId: number): Promise<MediaRemoteDeployResult> => {
  const res = await commonApi(
    'post',
    `${Api.Node}/media/remove-image-ssh?nodeId=${nodeId}`,
    {},
    { isTransformResponse: false, timeout: 3 * 60 * 1000 },
  );
  return unwrapNodeApiData<MediaRemoteDeployResult>(res);
};

/** 通过 SSH 检测目标机 SRS/ZLM 是否已部署 */
export const checkMediaStackBySsh = async (nodeId: number): Promise<MediaStackCheckResult> => {
  const res = await commonApi(
    'post',
    `${Api.Node}/media/check-ssh?nodeId=${nodeId}`,
    {},
    { isTransformResponse: false, timeout: 2 * 60 * 1000 },
  );
  return unwrapNodeApiData<MediaStackCheckResult>(res);
};

/** 通过 SSH 检测目标机 Node Agent 是否已部署 */
export const checkAgentBySsh = async (
  nodeId: number,
  controlPlaneUrl?: string,
): Promise<AgentCheckResult> => {
  const query = new URLSearchParams({ nodeId: String(nodeId) });
  if (controlPlaneUrl?.trim()) {
    query.set('controlPlaneUrl', controlPlaneUrl.trim());
  }
  const res = await commonApi(
    'post',
    `${Api.Node}/check-agent-ssh?${query.toString()}`,
    {},
    { isTransformResponse: false, timeout: 2 * 60 * 1000 },
  );
  return unwrapNodeApiData<AgentCheckResult>(res);
};

/** 通过 SSH 停止目标机 Node Agent 服务 */
export const stopAgentBySsh = async (nodeId: number): Promise<MediaRemoteDeployResult> => {
  const res = await commonApi(
    'post',
    `${Api.Node}/stop-agent-ssh?nodeId=${nodeId}`,
    {},
    { isTransformResponse: false, timeout: 3 * 60 * 1000 },
  );
  return unwrapNodeApiData<MediaRemoteDeployResult>(res);
};

/** 通过 SSH 删除目标机 Node Agent 服务及安装目录 */
export const removeAgentBySsh = async (nodeId: number): Promise<MediaRemoteDeployResult> => {
  const res = await commonApi(
    'post',
    `${Api.Node}/remove-agent-ssh?nodeId=${nodeId}`,
    {},
    { isTransformResponse: false, timeout: 3 * 60 * 1000 },
  );
  return unwrapNodeApiData<MediaRemoteDeployResult>(res);
};

/** 通过 SSH 自动部署 Node Agent（耗时较长，超时 5 分钟） */
export const deployAgentBySsh = async (
  nodeId: number,
  controlPlaneUrl?: string,
): Promise<MediaRemoteDeployResult> => {
  const query = new URLSearchParams({ nodeId: String(nodeId) });
  if (controlPlaneUrl?.trim()) {
    query.set('controlPlaneUrl', controlPlaneUrl.trim());
  }
  const res = await commonApi(
    'post',
    `${Api.Node}/deploy-agent-ssh?${query.toString()}`,
    {},
    { isTransformResponse: false, timeout: 5 * 60 * 1000 },
  );
  return unwrapNodeApiData<MediaRemoteDeployResult>(res);
};

export const getDeviceMediaBinding = (deviceId: string) => {
  return commonApi('get', Api.Node + '/media/binding', { params: { deviceId } });
};

export const releaseDeviceMedia = (deviceId: string) => {
  return commonApi('post', `${Api.Node}/media/release?deviceId=${encodeURIComponent(deviceId)}`);
};

/** 获取可用于调度的在线节点（compute / hybrid） */
export const listScheduleNodes = async () => {
  const res = await getNodePage({ pageNo: 1, pageSize: 200, status: 'online' });
  const list = res?.data?.list ?? [];
  return list.filter(
    (node: ComputeNodeVO) => node.nodeRole === 'compute' || node.nodeRole === 'hybrid',
  );
};

/** 获取可用于媒体调度的在线节点（media / hybrid） */
export const listMediaNodes = async () => {
  const res = await getNodePage({ pageNo: 1, pageSize: 200, status: 'online' });
  const list = res?.data?.list ?? [];
  return list.filter(
    (node: ComputeNodeVO) => node.nodeRole === 'media' || node.nodeRole === 'hybrid',
  );
};
