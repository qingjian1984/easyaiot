<script lang="ts" setup>
import { computed, nextTick, onMounted, onUnmounted, ref, watch } from 'vue';
import { Checkbox, Space, Tag } from 'ant-design-vue';
import { LeftOutlined, RightOutlined } from '@ant-design/icons-vue';
import type { ClusterLaneVO, ComputeNodeVO } from '@/api/device/node';
import { batchClusterLaneAction } from '@/api/device/node';
import { Button } from '@/components/Button';
import { useMessage } from '@/hooks/web/useMessage';
import NodeItemCard from '../NodeItemCard/index.vue';
import { NODE_TERM, LANE_BATCH_DEPLOY_ACTIONS } from '../../utils/constants';
import { canManageLaneWorkers, laneLabel, laneSyncStatusColor, localLaneWorkers } from '../../utils/clusterLanes';

defineOptions({ name: 'ClusterLaneRow' });

const props = defineProps<{
  lane: ClusterLaneVO;
}>();

const emit = defineEmits<{
  view: [node: ComputeNodeVO];
  edit: [node: ComputeNodeVO];
  delete: [node: ComputeNodeVO];
  continueSetup: [node: ComputeNodeVO];
  batchNavigate: [tab: string, nodeIds: number[]];
  refresh: [];
}>();

const { createMessage } = useMessage();
const scrollRef = ref<HTMLElement | null>(null);
const canScrollLeft = ref(false);
const canScrollRight = ref(false);
const selectedWorkerIds = ref<number[]>([]);
const batchLoading = ref(false);

const workers = computed(() => props.lane.workerNodes || []);
const manageable = computed(() => canManageLaneWorkers(props.lane));
const laneTitle = computed(() => laneLabel(props.lane));

const allWorkerIds = computed(() =>
  localLaneWorkers(props.lane)
    .map((node) => node.id)
    .filter((id): id is number => id != null),
);

const allSelected = computed({
  get: () => allWorkerIds.value.length > 0 && selectedWorkerIds.value.length === allWorkerIds.value.length,
  set: (checked: boolean) => {
    selectedWorkerIds.value = checked ? [...allWorkerIds.value] : [];
  },
});

const hasSelection = computed(() => selectedWorkerIds.value.length > 0);

function handleBatchNavigate(tab: string) {
  if (!selectedWorkerIds.value.length) {
    createMessage.warning('请先选择工作节点');
    return;
  }
  emit('batchNavigate', tab, selectedWorkerIds.value);
}

function updateScrollButtons() {
  const el = scrollRef.value;
  if (!el) {
    canScrollLeft.value = false;
    canScrollRight.value = false;
    return;
  }
  canScrollLeft.value = el.scrollLeft > 4;
  canScrollRight.value = el.scrollLeft + el.clientWidth < el.scrollWidth - 4;
}

function scrollWorkers(direction: 'left' | 'right') {
  const el = scrollRef.value;
  if (!el) return;
  const delta = Math.max(240, Math.floor(el.clientWidth * 0.7));
  el.scrollBy({ left: direction === 'left' ? -delta : delta, behavior: 'smooth' });
}

function toggleWorker(node: ComputeNodeVO, checked: boolean) {
  if (!node.id) return;
  if (checked) {
    if (!selectedWorkerIds.value.includes(node.id)) {
      selectedWorkerIds.value = [...selectedWorkerIds.value, node.id];
    }
    return;
  }
  selectedWorkerIds.value = selectedWorkerIds.value.filter((id) => id !== node.id);
}

async function runBatchMaintenance(enabled: boolean) {
  if (!selectedWorkerIds.value.length) {
    createMessage.warning('请先选择工作节点');
    return;
  }
  batchLoading.value = true;
  try {
    await batchClusterLaneAction({
      laneKey: props.lane.laneKey,
      nodeIds: selectedWorkerIds.value,
      action: enabled ? 'maintenance_on' : 'maintenance_off',
    });
    createMessage.success(enabled ? '已批量进入维护模式' : '已批量退出维护模式');
    selectedWorkerIds.value = [];
    emit('refresh');
  } finally {
    batchLoading.value = false;
  }
}

watch(
  () => [props.lane.laneKey, workers.value.length] as const,
  () => {
    selectedWorkerIds.value = [];
    nextTick(updateScrollButtons);
  },
);

onMounted(() => {
  nextTick(updateScrollButtons);
  scrollRef.value?.addEventListener('scroll', updateScrollButtons, { passive: true });
  window.addEventListener('resize', updateScrollButtons, { passive: true });
});

onUnmounted(() => {
  scrollRef.value?.removeEventListener('scroll', updateScrollButtons);
  window.removeEventListener('resize', updateScrollButtons);
});
</script>

<template>
  <section class="cluster-lane" :class="{ 'cluster-lane--remote': !lane.isLocal }">
    <div class="cluster-lane__header">
      <div class="cluster-lane__header-left">
        <span class="cluster-lane__title">{{ laneTitle }}</span>
        <Tag :color="laneSyncStatusColor(lane.syncStatus)">
          {{ lane.isLocal ? '本机' : (lane.syncStatus || 'unknown') }}
        </Tag>
        <span class="cluster-lane__worker-count">
          {{ NODE_TERM.laneWorkers }} {{ workers.length }}
        </span>
      </div>
      <div v-if="manageable" class="cluster-lane__header-actions">
        <Checkbox v-model:checked="allSelected">{{ NODE_TERM.laneBatchSelectAll }}</Checkbox>
        <Space wrap :size="8">
          <Button
            type="default"
            preIcon="ant-design:tool-outlined"
            :loading="batchLoading"
            :disabled="!hasSelection"
            @click="runBatchMaintenance(true)"
          >
            {{ NODE_TERM.laneBatchMaintenanceOn }}
          </Button>
          <Button
            type="default"
            preIcon="ant-design:check-circle-outlined"
            :loading="batchLoading"
            :disabled="!hasSelection"
            @click="runBatchMaintenance(false)"
          >
            {{ NODE_TERM.laneBatchMaintenanceOff }}
          </Button>
          <Button
            v-for="action in LANE_BATCH_DEPLOY_ACTIONS"
            :key="action.tab"
            type="default"
            :preIcon="action.icon"
            :disabled="!hasSelection"
            @click="handleBatchNavigate(action.tab)"
          >
            {{ action.label }}
          </Button>
        </Space>
      </div>
      <span v-else class="cluster-lane__remote-hint">{{ NODE_TERM.laneRemoteHint }}</span>
    </div>

    <div class="cluster-lane__track">
      <aside v-if="lane.centralNode" class="cluster-lane__central">
        <NodeItemCard
          :item="lane.centralNode"
          swimlane
          inlane
          central
          :manageable="false"
          :show-overlay="false"
          @view="emit('view', $event)"
        />
      </aside>

      <div v-if="lane.centralNode && workers.length" class="cluster-lane__divider" aria-hidden="true" />

      <div class="cluster-lane__workers">
        <div
          class="cluster-lane__scroll-wrap"
          :class="{
            'cluster-lane__scroll-wrap--can-left': canScrollLeft,
            'cluster-lane__scroll-wrap--can-right': canScrollRight,
          }"
        >
          <button
            v-show="canScrollLeft"
            type="button"
            class="cluster-lane__scroll-fab cluster-lane__scroll-fab--left"
            @click="scrollWorkers('left')"
          >
            <LeftOutlined />
          </button>

          <div ref="scrollRef" class="cluster-lane__scroll" @scroll="updateScrollButtons">
            <div v-if="!workers.length" class="cluster-lane__empty">暂无工作节点</div>
            <div
              v-for="worker in workers"
              :key="`${lane.laneKey}-${worker.id}`"
              class="cluster-lane__worker-item"
            >
              <NodeItemCard
                :item="worker"
                swimlane
                inlane
                :selectable="manageable && !!worker.id && !worker.isRemote"
                :selected="!!worker.id && selectedWorkerIds.includes(worker.id)"
                :manageable="manageable && !worker.isRemote"
                @view="emit('view', $event)"
                @edit="emit('edit', $event)"
                @delete="emit('delete', $event)"
                @continue-setup="emit('continueSetup', $event)"
                @select="toggleWorker"
              />
            </div>
          </div>

          <button
            v-show="canScrollRight"
            type="button"
            class="cluster-lane__scroll-fab cluster-lane__scroll-fab--right"
            @click="scrollWorkers('right')"
          >
            <RightOutlined />
          </button>
        </div>
      </div>
    </div>
  </section>
</template>

<style lang="less" scoped>
@import '../../utils/theme.less';

.cluster-lane {
  background: #fff;
  overflow: hidden;

  &--remote {
    .cluster-lane__track {
      border-style: dashed;
    }
  }
}

.cluster-lane__header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  padding: 16px 20px 12px;
  flex-wrap: wrap;
}

.cluster-lane__header-left {
  display: flex;
  align-items: center;
  gap: 10px;
  min-width: 0;
  flex-wrap: wrap;
}

.cluster-lane__title {
  font-size: 15px;
  font-weight: 600;
  color: @node-text-primary;
}

.cluster-lane__worker-count {
  font-size: 12px;
  color: @node-text-muted;

  &::before {
    content: '·';
    margin: 0 8px;
    color: @node-border;
  }
}

.cluster-lane__header-actions {
  display: flex;
  align-items: center;
  gap: 14px;
  flex-wrap: wrap;
}

.cluster-lane__remote-hint {
  font-size: 12px;
  color: @node-text-muted;
  line-height: 1.5;
}

/* 白色底统一轨道：细边框围合，中心与工作节点同处一面 */
.cluster-lane__track {
  display: flex;
  align-items: stretch;
  margin: 0 20px 24px;
  padding: 20px;
  border-radius: 12px;
  background: #fff;
  border: 1px solid @node-border;
  box-shadow: inset 4px 0 0 @node-primary;
  min-height: 228px;
  transition: border-color 0.2s ease, box-shadow 0.2s ease;

  &:hover {
    border-color: fade(@node-primary, 20%);
    box-shadow:
      inset 4px 0 0 @node-primary,
      0 4px 20px rgba(38, 108, 251, 0.07);
  }
}

.cluster-lane--remote .cluster-lane__track {
  box-shadow: inset 4px 0 0 @node-text-muted;

  &:hover {
    box-shadow:
      inset 4px 0 0 @node-text-muted,
      @node-card-shadow;
  }
}

.cluster-lane__central {
  flex: 0 0 228px;
  width: 228px;
  display: flex;
  align-items: stretch;
}

.cluster-lane__divider {
  flex: 0 0 1px;
  align-self: stretch;
  margin: 6px 20px;
  background: @node-border;
}

.cluster-lane__workers {
  flex: 1;
  min-width: 0;
  display: flex;
  align-items: stretch;
}

.cluster-lane__scroll-wrap {
  position: relative;
  display: flex;
  align-items: stretch;
  width: 100%;
  min-width: 0;

  &::before,
  &::after {
    content: '';
    position: absolute;
    top: 0;
    bottom: 0;
    width: 32px;
    z-index: 2;
    pointer-events: none;
    opacity: 0;
    transition: opacity 0.25s ease;
  }

  &::before {
    left: 0;
    background: linear-gradient(90deg, #fff 0%, rgba(255, 255, 255, 0) 100%);
  }

  &::after {
    right: 0;
    background: linear-gradient(270deg, #fff 0%, rgba(255, 255, 255, 0) 100%);
  }

  &--can-left::before {
    opacity: 1;
  }

  &--can-right::after {
    opacity: 1;
  }
}

.cluster-lane--remote .cluster-lane__scroll-wrap {
  &::before,
  &::after {
    background: linear-gradient(90deg, #fff 0%, rgba(255, 255, 255, 0) 100%);
  }

  &::after {
    background: linear-gradient(270deg, #fff 0%, rgba(255, 255, 255, 0) 100%);
  }
}

.cluster-lane__scroll-fab {
  position: absolute;
  top: 50%;
  z-index: 3;
  display: flex;
  align-items: center;
  justify-content: center;
  width: 32px;
  height: 32px;
  border: 1px solid @node-border;
  border-radius: 50%;
  background: #fff;
  color: @node-primary;
  font-size: 13px;
  box-shadow: 0 2px 10px rgba(0, 0, 0, 0.08);
  cursor: pointer;
  transform: translateY(-50%);
  transition: border-color 0.2s, box-shadow 0.2s, transform 0.2s;

  &:hover {
    border-color: fade(@node-primary, 30%);
    box-shadow: @node-card-shadow-hover;
    transform: translateY(-50%) scale(1.04);
  }

  &--left {
    left: 4px;
  }

  &--right {
    right: 4px;
  }
}

.cluster-lane__scroll {
  display: flex;
  gap: 16px;
  overflow-x: auto;
  scroll-behavior: smooth;
  padding: 2px 4px;
  flex: 1;
  min-width: 0;
  align-items: stretch;
  scroll-padding: 0 40px;

  scrollbar-width: none;

  &::-webkit-scrollbar {
    display: none;
  }
}

.cluster-lane__worker-item {
  flex: 0 0 212px;
  width: 212px;
  display: flex;
}

.cluster-lane__empty {
  display: flex;
  align-items: center;
  justify-content: center;
  flex: 1;
  min-height: 200px;
  padding: 28px;
  color: @node-text-muted;
  font-size: 14px;
  border-radius: 12px;
  background: #fff;
  border: 1px dashed @node-border;
}

@media (max-width: 768px) {
  .cluster-lane__track {
    flex-direction: column;
    margin: 0 12px 12px;
    padding: 12px;
  }

  .cluster-lane__central {
    flex: none;
    width: 100%;
    max-width: 260px;
    margin: 0 auto;
  }

  .cluster-lane__divider {
    width: 100%;
    height: 1px;
    margin: 12px 0;
    background: @node-border;
  }

  .cluster-lane__workers {
    width: 100%;
  }
}
</style>
