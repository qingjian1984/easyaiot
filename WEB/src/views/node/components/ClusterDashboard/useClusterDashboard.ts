import { computed, onMounted, onUnmounted, ref, watch } from 'vue';
import { getNodeMetricTrend, getNodePage, type ComputeNodeVO } from '@/api/device/node';
import {
  appendLiveNodeTrendPoints,
  appendTrendPoint,
  buildClusterSnapshot,
  buildNodeDiskList,
  buildNodeLoadList,
  buildNodeTrendSeriesFromApi,
  collectGpuCards,
  groupGpusByNode,
  isComputeNode,
  mergeNodeTrendSeries,
  type ClusterSnapshot,
  type ClusterTrendPoint,
  type GpuCardMetric,
  type NodeDiskItem,
  type NodeGpuGroup,
  type NodeLoadItem,
  type NodeTrendSeries,
  type TrendMetricKey,
  type TrendViewMode,
} from '../../utils/clusterMetrics';
import {
  connectClusterMetricsWebSocket,
  disconnectClusterMetricsWebSocket,
  wsPayloadToComputeNode,
  type ClusterWsMessage,
  type ClusterWsStatus,
} from '../../utils/clusterMetricsWs';
import { TREND_SAMPLE_INTERVAL_DEFAULT } from '../../utils/constants';

export type { TrendViewMode };

const MAX_TREND_POINTS = 120;

const shared = {
  trendHistory: [] as ClusterTrendPoint[],
  nodeTrendSeries: [] as NodeTrendSeries[],
  trendSampleIntervalSec: TREND_SAMPLE_INTERVAL_DEFAULT,
  initialized: false,
};

export function useClusterDashboard() {
  const loading = ref(true);
  const wsStatus = ref<ClusterWsStatus>('connecting');
  const trendViewMode = ref<TrendViewMode>('cluster');
  const trendMetric = ref<TrendMetricKey>('cpu');
  const selectedNodeIds = ref<number[]>([]);
  const overviewFocusNodeId = ref<number | undefined>(undefined);
  const trendSampleIntervalSec = ref(shared.trendSampleIntervalSec);
  const nodes = ref<ComputeNodeVO[]>([]);
  const snapshot = ref<ClusterSnapshot>(buildClusterSnapshot([]));
  const trendHistory = ref<ClusterTrendPoint[]>([...shared.trendHistory]);
  const nodeTrendSeries = ref<NodeTrendSeries[]>([...shared.nodeTrendSeries]);
  const gpuCards = ref<GpuCardMetric[]>([]);
  const nodeGpuGroups = ref<NodeGpuGroup[]>([]);
  const nodeLoads = ref<NodeLoadItem[]>([]);
  const nodeDisks = ref<NodeDiskItem[]>([]);
  const lastUpdated = ref('');

  const focusNodes = computed(() => {
    if (!overviewFocusNodeId.value) {
      return nodes.value;
    }
    const target = nodes.value.find((node) => node.id === overviewFocusNodeId.value);
    return target ? [target] : nodes.value;
  });

  const displaySnapshot = computed(() => buildClusterSnapshot(focusNodes.value));
  const displayGpuGroups = computed(() => groupGpusByNode(focusNodes.value));
  const displayNodeLoads = computed(() => buildNodeLoadList(focusNodes.value));
  const displayNodeDisks = computed(() => buildNodeDiskList(focusNodes.value));
  const displayNodeTrendSeries = computed(() => {
    if (!overviewFocusNodeId.value) {
      return nodeTrendSeries.value;
    }
    return nodeTrendSeries.value.filter((series) => series.nodeId === overviewFocusNodeId.value);
  });

  function syncSharedState() {
    shared.trendHistory = trendHistory.value;
    shared.nodeTrendSeries = nodeTrendSeries.value;
    shared.trendSampleIntervalSec = trendSampleIntervalSec.value;
  }

  function recomputeDerivedState() {
    snapshot.value = buildClusterSnapshot(nodes.value);
    gpuCards.value = collectGpuCards(nodes.value);
    nodeGpuGroups.value = groupGpusByNode(nodes.value);
    nodeLoads.value = buildNodeLoadList(nodes.value);
    nodeDisks.value = buildNodeDiskList(nodes.value);
    const intervalMs = trendSampleIntervalSec.value * 1000;
    trendHistory.value = appendTrendPoint(
      trendHistory.value,
      snapshot.value,
      MAX_TREND_POINTS,
      intervalMs,
    );
    nodeTrendSeries.value = appendLiveNodeTrendPoints(
      nodeTrendSeries.value,
      nodes.value,
      MAX_TREND_POINTS,
      intervalMs,
    );
    lastUpdated.value = new Date().toLocaleTimeString();
    syncSharedState();
  }

  watch(trendSampleIntervalSec, (seconds) => {
    shared.trendSampleIntervalSec = seconds;
  });

  function applySnapshot(currentNodes: ComputeNodeVO[]) {
    nodes.value = currentNodes;
    recomputeDerivedState();
    loading.value = false;
  }

  function applyNodeUpdate(update: ComputeNodeVO) {
    if (!update.id) return;
    const index = nodes.value.findIndex((node) => node.id === update.id);
    if (index >= 0) {
      nodes.value[index] = { ...nodes.value[index], ...update };
    } else {
      nodes.value.push(update);
    }
    recomputeDerivedState();
    loading.value = false;
  }

  function handleWsMessage(message: ClusterWsMessage) {
    if (message.type === 'snapshot' && message.nodes?.length) {
      applySnapshot(message.nodes.map(wsPayloadToComputeNode));
      return;
    }
    if (message.type === 'node_update' && message.node) {
      applyNodeUpdate(wsPayloadToComputeNode(message.node));
    }
  }

  async function loadInitialData() {
    loading.value = nodes.value.length === 0;
    try {
      const [pageRes] = await Promise.all([
        getNodePage({ pageNo: 1, pageSize: 200 }),
        loadMetricHistory(),
      ]);
      if (!nodes.value.length) {
        applySnapshot(pageRes?.data?.list ?? []);
      }
    } finally {
      loading.value = false;
    }
  }

  async function loadMetricHistory() {
    try {
      const res = await getNodeMetricTrend({ minutes: 30, maxPoints: MAX_TREND_POINTS });
      const fromApi = buildNodeTrendSeriesFromApi(res.series ?? []);
      if (fromApi.length) {
        nodeTrendSeries.value = mergeNodeTrendSeries(nodeTrendSeries.value, fromApi, MAX_TREND_POINTS);
        syncSharedState();
      }
    } catch {
      // 历史接口不可用时依赖 WebSocket 实时累积
    }
  }

  onMounted(async () => {
    if (!shared.initialized) {
      shared.initialized = true;
      await loadInitialData();
    } else {
      loading.value = false;
    }
    connectClusterMetricsWebSocket(handleWsMessage, (status) => {
      wsStatus.value = status;
    });
  });

  onUnmounted(() => {
    disconnectClusterMetricsWebSocket(handleWsMessage);
  });

  return {
    loading,
    wsStatus,
    trendViewMode,
    trendMetric,
    selectedNodeIds,
    overviewFocusNodeId,
    trendSampleIntervalSec,
    nodes,
    snapshot,
    displaySnapshot,
    trendHistory,
    nodeTrendSeries,
    displayNodeTrendSeries,
    gpuCards,
    nodeGpuGroups,
    displayGpuGroups,
    nodeLoads,
    displayNodeLoads,
    nodeDisks,
    displayNodeDisks,
    lastUpdated,
    computeNodeOptions: computed(() =>
      nodes.value
        .filter(isComputeNode)
        .map((node) => ({
          label: `${node.name} (${node.host})`,
          value: node.id!,
        })),
    ),
  };
}
