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
              <NodeItemCard
                :item="item"
                @view="handleView"
                @edit="handleEdit"
                @delete="handleDelete"
                @continue-setup="handleContinueSetup"
              />
            </ListItem>
          </template>
        </List>
      </Spin>
    </div>
  </div>
</template>

<script lang="ts" setup>
import { onMounted, reactive, ref } from 'vue';
import { List, Spin } from 'ant-design-vue';
import { BasicForm, useForm } from '@/components/Form';
import { propTypes } from '@/utils/propTypes';
import { isFunction } from '@/utils/is';
import type { ComputeNodeVO } from '@/api/device/node';
import NodeItemCard from '../NodeItemCard/index.vue';
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
</style>
