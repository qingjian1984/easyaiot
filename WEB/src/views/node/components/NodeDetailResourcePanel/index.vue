<script lang="ts" setup>
import { computed } from 'vue';
import { Progress, Spin } from 'ant-design-vue';
import type { ComputeNodeVO } from '@/api/device/node';
import {
  aggregateGpuVram,
  formatCpuQuantity,
  formatPercent,
  formatStorageRange,
  getProgressColor,
} from '../../utils/clusterMetrics';
import { NODE_DASHBOARD, NODE_DETAIL, NODE_METRIC, parseGpuInfo } from '../../utils/constants';
import NodeGpuMiniBars from '../NodeGpuMiniBars/index.vue';

defineOptions({ name: 'NodeDetailResourcePanel' });

const props = defineProps<{
  node?: ComputeNodeVO | null;
  loading?: boolean;
}>();

const hasMetrics = computed(
  () =>
    props.node?.cpuPercent != null ||
    props.node?.memPercent != null ||
    props.node?.diskPercent != null ||
    props.node?.lastHeartbeatAt,
);

const gpuList = computed(() => parseGpuInfo(props.node?.gpuInfo));
const gpuVram = computed(() => aggregateGpuVram(gpuList.value));

function progressPercent(value?: number | null): number {
  if (value == null) return 0;
  return Math.min(Number(value), 100);
}

const metricCards = computed(() => {
  const node = props.node;
  return [
    {
      key: 'cpu',
      label: NODE_METRIC.cpu,
      percent: node?.cpuPercent,
      capacity: node?.cpuPercent != null ? formatCpuQuantity(node.cpuPercent) : null,
      capacityFirst: true,
      hideSub: true,
    },
    {
      key: 'mem',
      label: NODE_DASHBOARD.statMemCapacity,
      percent: node?.memPercent,
      capacity: formatStorageRange(node?.memUsedBytes, node?.memTotalBytes),
      capacityFirst: true,
    },
    {
      key: 'vram',
      label: NODE_DASHBOARD.statVramCapacity,
      percent: gpuVram.value.totalBytes > 0 ? gpuVram.value.avgPercent : null,
      capacity: formatStorageRange(gpuVram.value.usedBytes, gpuVram.value.totalBytes),
      capacityFirst: true,
    },
    {
      key: 'disk',
      label: NODE_DASHBOARD.statDiskCapacity,
      percent: node?.diskPercent,
      capacity: formatStorageRange(node?.diskUsedBytes, node?.diskTotalBytes),
      capacityFirst: true,
    },
  ];
});
</script>

<template>
  <Spin :spinning="!!loading">
    <div class="resource-panel">
      <div v-if="!loading && !hasMetrics" class="resource-panel__empty">
        {{ NODE_DETAIL.noMetrics }}
      </div>

      <template v-else-if="hasMetrics || !loading">
      <div class="metric-grid">
        <div
          v-for="item in metricCards"
          :key="item.key"
          class="metric-card"
          :class="{ 'metric-card--capacity': item.capacityFirst }"
        >
          <template v-if="item.capacityFirst">
            <span class="metric-card__label">{{ item.label }}</span>
            <strong class="metric-card__value">
              {{ item.capacity && item.capacity !== '-' ? item.capacity : '—' }}
            </strong>
            <span v-if="!item.hideSub && item.percent != null" class="metric-card__sub">
              {{ formatPercent(item.percent) }}
            </span>
            <Progress
              class="metric-card__progress"
              :percent="progressPercent(item.percent)"
              :stroke-color="getProgressColor(progressPercent(item.percent))"
              :show-info="false"
            />
          </template>
          <template v-else>
            <div class="metric-card__head">
              <span>{{ item.label }}</span>
              <strong>{{ formatCpuQuantity(item.percent) }}</strong>
            </div>
            <Progress
              :percent="progressPercent(item.percent)"
              :stroke-color="getProgressColor(progressPercent(item.percent))"
              :show-info="false"
            />
          </template>
        </div>

      </div>

      <div v-if="gpuList.length" class="gpu-block">
        <div class="gpu-block__head">
          <h4>{{ NODE_DETAIL.gpuSection }}</h4>
          <span class="gpu-block__hint">{{ NODE_DETAIL.gpuSectionHint }}</span>
        </div>

        <div class="gpu-util-grid">
          <div v-for="gpu in gpuList" :key="`util-${gpu.id ?? 0}`" class="gpu-util-item">
            <div class="gpu-util-item__label">
              <span>GPU{{ gpu.id ?? 0 }}</span>
              <span class="gpu-util-item__name">{{ gpu.name || '—' }}</span>
            </div>
            <div class="gpu-metric-row">
              <span class="gpu-metric-row__title">{{ NODE_METRIC.gpuUtil }}</span>
              <NodeGpuMiniBars :gpu-info="[gpu]" metric="util" :show-label="false" />
            </div>
            <div class="gpu-metric-row">
              <span class="gpu-metric-row__title">{{ NODE_METRIC.vramUsage }}</span>
              <NodeGpuMiniBars :gpu-info="[gpu]" metric="vram" :show-label="false" />
            </div>
          </div>
        </div>
      </div>
      </template>
    </div>
  </Spin>
</template>

<style lang="less" scoped>
.resource-panel {
  min-height: 200px;
}

.resource-panel__empty {
  padding: 28px 16px;
  text-align: center;
  font-size: 13px;
  color: #8c8c8c;
  background: #fafafa;
  border: 1px dashed #e8e8e8;
  border-radius: 8px;
}

.metric-grid {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 12px;
  margin-bottom: 16px;
}

.metric-card {
  padding: 14px 16px;
  background: #fafafa;
  border: 1px solid #f0f0f0;
  border-radius: 8px;
}

.metric-card--capacity {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.metric-card__label {
  font-size: 12px;
  color: #8c8c8c;
}

.metric-card__value {
  font-size: 16px;
  font-weight: 600;
  color: #262626;
  font-variant-numeric: tabular-nums;
  line-height: 1.3;
}

.metric-card__sub {
  font-size: 14px;
  font-weight: 600;
  color: #262626;
  font-variant-numeric: tabular-nums;
}

.metric-card__progress {
  margin-top: 4px;
}

.metric-card__head {
  display: flex;
  justify-content: space-between;
  align-items: center;
  font-size: 12px;
  color: #8c8c8c;
  margin-bottom: 8px;

  strong {
    font-size: 16px;
    color: #262626;
    font-variant-numeric: tabular-nums;
  }
}

.gpu-block {
  margin-top: 4px;
}

.gpu-block__head {
  display: flex;
  align-items: baseline;
  gap: 10px;
  margin-bottom: 12px;

  h4 {
    margin: 0;
    font-size: 14px;
    font-weight: 600;
    color: #262626;
  }
}

.gpu-block__hint {
  font-size: 12px;
  color: #8c8c8c;
}

.gpu-util-grid {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: 10px;
  margin-bottom: 12px;
}

.gpu-util-item {
  padding: 12px 14px;
  background: #fafafa;
  border: 1px solid #f0f0f0;
  border-radius: 8px;
}

.gpu-util-item__label {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 8px;
  font-size: 12px;
  color: #595959;
  font-weight: 500;
}

.gpu-util-item__name {
  color: #8c8c8c;
  font-weight: normal;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.gpu-metric-row {
  display: grid;
  grid-template-columns: 72px 1fr;
  gap: 8px;
  align-items: center;
  margin-top: 8px;
}

.gpu-metric-row__title {
  font-size: 11px;
  color: #8c8c8c;
}

@media (max-width: 900px) {
  .metric-grid,
  .gpu-util-grid {
    grid-template-columns: 1fr;
  }
}
</style>
