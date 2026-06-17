<template>
  <div class="node-wrapper">
    <div class="node-tab">
      <Tabs
        class="node-tabs-bar"
        :activeKey="state.activeKey"
        :animated="{ inkBar: true, tabPane: false }"
        :tabBarGutter="40"
        @tabClick="handleTabClick"
      >
        <TabPane key="1" :tab="NODE_PAGE.clusterOverview" />
        <TabPane key="2" :tab="NODE_PAGE.nodeInventory" />
        <TabPane key="3" :tab="NODE_PAGE.clusterEnvAgent" />
        <TabPane key="4" :tab="NODE_PAGE.clusterEnvStorage" />
        <TabPane key="5" :tab="NODE_PAGE.clusterEnvMedia" />
        <TabPane key="6" :tab="NODE_PAGE.clusterEnvFfmpeg" />
        <TabPane key="7" :tab="NODE_PAGE.clusterEnvVideo" />
        <TabPane key="8" :tab="NODE_PAGE.clusterEnvAi" />
      </Tabs>

      <div class="node-tab-content">
        <ClusterDashboard v-if="tabMounted['1']" v-show="state.activeKey === '1'" />
        <NodeManage v-if="tabMounted['2']" v-show="state.activeKey === '2'" />
        <AgentEnvBatch
          v-if="tabMounted['3']"
          v-show="state.activeKey === '3'"
          :initial-node-id="selectedNodeId"
          :initial-node-ids="selectedNodeIds"
        />
        <StorageEnvBatch
          v-if="tabMounted['4']"
          v-show="state.activeKey === '4'"
          :initial-node-ids="selectedNodeIds"
        />
        <MediaEnvBatch
          v-if="tabMounted['5']"
          v-show="state.activeKey === '5'"
          :initial-node-id="selectedNodeId"
          :initial-node-ids="selectedNodeIds"
        />
        <FfmpegEnvTab
          v-if="tabMounted['6']"
          v-show="state.activeKey === '6'"
          :initial-node-ids="selectedNodeIds"
        />
        <VideoWorkloadInit
          v-if="tabMounted['7']"
          v-show="state.activeKey === '7'"
          :initial-node-ids="selectedNodeIds"
        />
        <AiWorkloadInit
          v-if="tabMounted['8']"
          v-show="state.activeKey === '8'"
          :initial-node-ids="selectedNodeIds"
        />
      </div>
    </div>
  </div>
</template>

<script lang="ts" setup>
import { onMounted, reactive, ref, watch } from 'vue';
import { useRoute } from 'vue-router';
import { TabPane, Tabs } from 'ant-design-vue';
import AgentEnvBatch from './components/AgentEnvBatch/index.vue';
import AiWorkloadInit from './components/AiWorkloadInit/index.vue';
import ClusterDashboard from './components/ClusterDashboard/index.vue';
import FfmpegEnvTab from './components/FfmpegEnvTab/index.vue';
import MediaEnvBatch from './components/MediaEnvBatch/index.vue';
import NodeManage from './components/NodeManage/index.vue';
import StorageEnvBatch from './components/StorageEnvBatch/index.vue';
import VideoWorkloadInit from './components/VideoWorkloadInit/index.vue';
import { NODE_PAGE, resolveLegacyWorkloadTab } from './utils/constants';
import { useNodePageTabRequest } from './utils/useNodePageTab';

defineOptions({ name: 'ComputeNodeIndex' });

const NODE_TAB_KEYS = ['1', '2', '3', '4', '5', '6', '7', '8'] as const;

const route = useRoute();
const tabRequest = useNodePageTabRequest();

function resolveRouteTab(): string {
  const raw = String(route.query.tab || '1');
  if (raw === '3' && route.query.bundle) {
    return resolveLegacyWorkloadTab(String(route.query.bundle));
  }
  return raw;
}

function parseRouteNodeIds(): number[] | undefined {
  const raw = String(route.query.nodeIds || '');
  if (!raw) return undefined;
  const ids = raw
    .split(',')
    .map((item) => Number(item.trim()))
    .filter((id) => Number.isFinite(id) && id > 0);
  return ids.length ? ids : undefined;
}

function parseRouteNodeId(): number | undefined {
  const id = Number(route.query.nodeId);
  return Number.isFinite(id) && id > 0 ? id : undefined;
}

function createTabMounted(initialKey: string): Record<string, boolean> {
  const mounted: Record<string, boolean> = {};
  NODE_TAB_KEYS.forEach((key) => {
    mounted[key] = key === initialKey;
  });
  return mounted;
}

function ensureTabMounted(tabKey: string) {
  if (tabKey in tabMounted) tabMounted[tabKey] = true;
}

const state = reactive({
  activeKey: resolveRouteTab(),
});

const tabMounted = reactive(createTabMounted(state.activeKey));
const selectedNodeIds = ref<number[]>([]);
const selectedNodeId = ref<number | undefined>();

function applyNodeSelection(nodeIds?: number[], nodeId?: number) {
  if (nodeIds?.length) {
    selectedNodeIds.value = [...nodeIds];
    selectedNodeId.value = nodeIds.length === 1 ? nodeIds[0] : nodeId;
    return;
  }
  if (nodeId) {
    selectedNodeId.value = nodeId;
    selectedNodeIds.value = [nodeId];
    return;
  }
  selectedNodeIds.value = [];
  selectedNodeId.value = undefined;
}

function applyFromRouteQuery() {
  if (!route.query.tab && !route.query.bundle && !route.query.nodeId && !route.query.nodeIds) return;
  state.activeKey = resolveRouteTab();
  ensureTabMounted(state.activeKey);
  const routeIds = parseRouteNodeIds();
  const routeId = parseRouteNodeId();
  if (routeIds?.length || routeId) {
    applyNodeSelection(routeIds, routeId);
  }
}

function handleTabClick(activeKey: string) {
  state.activeKey = activeKey;
  ensureTabMounted(activeKey);
}

watch(
  () => [route.query.tab, route.query.bundle, route.query.nodeId, route.query.nodeIds] as const,
  () => applyFromRouteQuery(),
);

watch(
  () => state.activeKey,
  (tabKey) => {
    ensureTabMounted(tabKey);
  },
);

watch(tabRequest, (req) => {
  if (!req) return;
  state.activeKey = req.tab;
  ensureTabMounted(req.tab);
  if (req.nodeIds?.length || req.nodeId) {
    applyNodeSelection(req.nodeIds, req.nodeId);
  }
});

onMounted(() => {
  applyFromRouteQuery();
});
</script>

<style lang="less" scoped>
@import './utils/theme.less';

.node-wrapper {
  min-height: 100%;
  background: @node-bg;

  :deep(.ant-form-item) {
    margin-bottom: 12px;
  }

  :deep(.ant-form-item-label > label) {
    font-size: @node-font-body;
    color: @node-text-body;
  }

  .node-tab {
    padding: 16px 19px 0 15px;

    :deep(.node-tabs-bar .ant-tabs-nav) {
      padding: 5px 0 0 25px;
    }

    :deep(.node-tabs-bar) {
      background-color: #ffffff;

      .ant-tabs-content-holder {
        display: none;
      }
    }
  }

  .node-tab-content {
    background-color: #ffffff;
  }
}
</style>

<style lang="less">
@import './utils/node-badge.less';
</style>
