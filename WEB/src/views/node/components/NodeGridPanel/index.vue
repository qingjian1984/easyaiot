<template>
  <div class="node-card-list-wrapper">
    <div class="search-bar">
      <BasicForm @register="registerForm" @reset="handleSubmit" />
    </div>
    <div class="list-panel">
      <Spin :spinning="state.loading">
        <List
          :grid="{ gutter: 18, xs: 2, sm: 3, md: 4, lg: 5, xl: 6, xxl: 6 }"
          :data-source="data"
          :pagination="paginationProp"
        >
          <template #header>
            <div class="list-header">
              <span class="list-title">节点列表</span>
              <div class="list-actions">
                <slot name="header" />
              </div>
            </div>
          </template>
          <template #renderItem="{ item }">
            <ListItem class="node-list-item">
              <div
                class="node-card"
                @mouseenter="hoverId = item.id"
                @mouseleave="hoverId = null"
              >
                <div
                  class="node-card-cover"
                  :class="[
                    getNodeRoleVisual(item.nodeRole).coverClass,
                    item.status ? `node-card-cover--${item.status}` : '',
                  ]"
                  @click="handleView(item)"
                >
                  <div class="node-card-cover-inner">
                    <NodeRoleIcon :role="item.nodeRole" size="lg" />
                  </div>
                  <div class="node-card-status" @click.stop>
                    <component :is="renderNodeStatusBadge(item.status)" />
                  </div>
                  <div
                    v-show="hoverId === item.id"
                    class="node-card-overlay"
                    @click.stop
                  >
                    <div class="overlay-actions">
                      <Tooltip v-if="item.status === 'pending'" :title="NODE_TERM.continueOnboard">
                        <button class="overlay-btn" @click="handleContinueSetup(item)">
                          <RocketOutlined />
                        </button>
                      </Tooltip>
                      <Tooltip title="查看详情">
                        <button class="overlay-btn" @click="handleView(item)">
                          <EyeOutlined />
                        </button>
                      </Tooltip>
                      <Tooltip title="编辑">
                        <button class="overlay-btn" @click="handleEdit(item)">
                          <EditOutlined />
                        </button>
                      </Tooltip>
                      <Popconfirm title="确认删除该节点？" @confirm="handleDelete(item)">
                        <Tooltip title="删除">
                          <button class="overlay-btn overlay-btn--danger">
                            <DeleteOutlined />
                          </button>
                        </Tooltip>
                      </Popconfirm>
                    </div>
                  </div>
                </div>

                <div class="node-card-body">
                  <h3 class="node-card-title" :title="item.name" @click="handleView(item)">
                    {{ item.name }}
                  </h3>
                  <p v-if="getMetaText(item)" class="node-card-meta-line" :title="getMetaText(item)">
                    {{ getMetaText(item) }}
                  </p>
                </div>
              </div>
            </ListItem>
          </template>
        </List>
      </Spin>
    </div>
  </div>
</template>

<script lang="ts" setup>
import { onMounted, reactive, ref } from 'vue';
import { List, Popconfirm, Spin, Tooltip } from 'ant-design-vue';
import { DeleteOutlined, EditOutlined, EyeOutlined, RocketOutlined } from '@ant-design/icons-vue';
import { BasicForm, useForm } from '@/components/Form';
import { propTypes } from '@/utils/propTypes';
import { isFunction } from '@/utils/is';
import type { ComputeNodeVO } from '@/api/device/node';
import { NODE_ROLE_MAP, NODE_TERM } from '../../utils/constants';
import { getNodeRoleVisual } from '../../utils/nodeAssets';
import { renderNodeStatusBadge } from '../../utils/nodeDisplay';
import NodeRoleIcon from '../NodeRoleIcon/index.vue';
import { getNodeCardFormConfig } from './Data';

defineOptions({ name: 'NodeGridPanel' });

const ListItem = List.Item;

const props = defineProps({
  params: propTypes.object.def({}),
  api: propTypes.func,
});

const emit = defineEmits([
  'getMethod',
  'view',
  'edit',
  'testSsh',
  'resetToken',
  'maintenance',
  'deployMedia',
  'delete',
  'continueSetup',
]);

const data = ref<ComputeNodeVO[]>([]);
const hoverId = ref<number | null>(null);
const state = reactive({ loading: true });

const [registerForm, { validate }] = useForm({
  ...getNodeCardFormConfig(),
  submitFunc: handleSubmit,
});

onMounted(() => {
  fetch();
  emit('getMethod', fetch);
});

async function handleSubmit() {
  const formData = await validate();
  page.value = 1;
  await fetch(formData);
}

async function fetch(p: Record<string, unknown> = {}) {
  const { api, params } = props;
  if (api && isFunction(api)) {
    state.loading = true;
    try {
      const res = await api({
        ...params,
        pageNo: page.value,
        pageSize: pageSize.value,
        ...p,
      });
      data.value = res?.data?.list ?? [];
      total.value = res?.data?.total ?? 0;
    } finally {
      state.loading = false;
    }
  }
}

const page = ref(1);
const pageSize = ref(18);
const total = ref(0);

const paginationProp = ref({
  showSizeChanger: true,
  showQuickJumper: true,
  pageSize,
  current: page,
  total,
  pageSizeOptions: ['12', '18', '24', '36'],
  showTotal: (t: number) => `总 ${t} 条`,
  onChange: pageChange,
  onShowSizeChange: pageSizeChange,
});

function pageChange(p: number, pz: number) {
  page.value = p;
  pageSize.value = pz;
  fetch();
}

function pageSizeChange(_current: number, size: number) {
  pageSize.value = size;
  fetch();
}

function getMetaText(item: ComputeNodeVO): string {
  const parts: string[] = [];
  const roleLabel = NODE_ROLE_MAP[item.nodeRole || ''];
  if (roleLabel) parts.push(roleLabel);
  if (item.host) parts.push(item.host);
  if (!parts.length) return item.id != null ? `ID: ${item.id}` : '';
  return parts.join('  |  ');
}

function handleDelete(record: ComputeNodeVO) {
  emit('delete', record);
}

function handleView(record: ComputeNodeVO) {
  emit('view', record);
}

function handleEdit(record: ComputeNodeVO) {
  emit('edit', record);
}

function handleContinueSetup(record: ComputeNodeVO) {
  emit('continueSetup', record);
}

defineExpose({ fetch });
</script>

<style lang="less" scoped>
.node-card-list-wrapper {
  background: #fff;
  min-height: 100%;
}

.search-bar {
  padding: 16px 16px 0;
  margin-bottom: 10px;
  background: #fff;

  :deep(.ant-form-item) {
    margin-bottom: 12px;
  }
}

.list-panel {
  background: #fff;
  padding: 0 8px 16px;

  :deep(.ant-list-header) {
    border: 0;
    padding: 8px 12px 16px;
    background: transparent;
  }

  :deep(.ant-list) {
    padding: 0 8px;
  }

  :deep(.ant-row) {
    display: flex;
    flex-wrap: wrap;
    row-gap: 18px;
  }

  :deep(.ant-col) {
    display: flex;
  }

  :deep(.ant-list-item) {
    margin-bottom: 0;
    padding: 0 !important;
    border: none;
    width: 100%;
    height: 100%;
    display: flex;
  }

  :deep(.ant-list-pagination) {
    margin-top: 32px;
    padding-top: 12px;
    text-align: center;
  }
}

.list-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.list-title {
  padding-left: 4px;
  font-size: 16px;
  font-weight: 500;
  line-height: 24px;
  color: #181818;
}

.list-actions {
  display: flex;
  gap: 8px;
}

.node-list-item {
  width: 100%;
}

@cover-height: 200px;
@body-min-height: 96px;

@import '../../utils/nodeRoleTheme.less';

.node-card {
  position: relative;
  display: flex;
  flex-direction: column;
  width: 100%;
  min-height: @cover-height + @body-min-height;
  background: #fff;
  border-radius: 6px;
  box-shadow: 0 1px 4px rgba(24, 24, 24, 0.1);
  overflow: hidden;
  transition: box-shadow 0.25s ease, transform 0.25s ease;
  cursor: default;

  &:hover {
    box-shadow: 0 3px 12px rgba(0, 0, 0, 0.12);
    transform: translateY(-1px);
  }
}

.node-card-cover {
  position: relative;
  width: 100%;
  height: @cover-height;
  flex-shrink: 0;
  overflow: hidden;
  cursor: pointer;
  transition: background 0.2s ease;

  &--compute {
    background: linear-gradient(180deg, @node-compute-tile-start 0%, @node-compute-tile-end 100%);
  }

  &--media {
    background: linear-gradient(180deg, @node-media-tile-start 0%, @node-media-tile-end 100%);
  }

  &--hybrid {
    background: linear-gradient(180deg, @node-hybrid-tile-start 0%, @node-hybrid-tile-end 100%);
  }

  &--offline {
    .node-card-cover-inner {
      opacity: 0.72;
      filter: grayscale(0.35);
    }
  }

  &--pending {
    .node-card-cover-inner {
      opacity: 0.88;
    }
  }
}

.node-card-cover-inner {
  position: absolute;
  inset: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 16px;
  box-sizing: border-box;
  transition: opacity 0.2s ease, filter 0.2s ease;
}

.node-card-status {
  position: absolute;
  top: 10px;
  right: 10px;
  z-index: 2;
}

.node-card-overlay {
  position: absolute;
  inset: 0;
  z-index: 3;
  border-radius: 6px 6px 0 0;
  overflow: hidden;
  display: flex;
  align-items: center;
  justify-content: center;
  background: rgba(0, 0, 0, 0.45);
}

.overlay-actions {
  display: flex;
  gap: 10px;
  flex-wrap: wrap;
  justify-content: center;
  padding: 0 8px;
}

.overlay-btn {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 34px;
  height: 34px;
  border: none;
  border-radius: 50%;
  background: rgba(255, 255, 255, 0.92);
  color: #266cfb;
  font-size: 16px;
  cursor: pointer;
  transition: background 0.2s, color 0.2s, transform 0.2s;

  &:hover {
    background: #fff;
    transform: scale(1.08);
  }

  &--danger {
    color: #f5222d;

    &:hover {
      background: #fff1f0;
    }
  }
}

.node-card-body {
  flex: 1;
  min-height: @body-min-height;
  padding: 24px 16px 14px;
  box-sizing: border-box;
}

.node-card-title {
  margin: 0 0 8px;
  font-size: 15px;
  font-weight: 600;
  line-height: 1.45;
  color: #181818;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  cursor: pointer;

  &:hover {
    color: #266cfb;
  }
}

.node-card-meta-line {
  margin: 0;
  font-size: 13px;
  line-height: 1.5;
  color: #999;
  white-space: normal;
  word-break: break-all;
}
</style>
