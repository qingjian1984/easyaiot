<template>
  <div class="camera-card-list-wrapper p-2">
    <div class="p-4 bg-white" style="margin-bottom: 10px">
      <BasicForm @register="registerForm" />
    </div>
    <div class="p-2 bg-white">
      <Spin :spinning="state.loading">
        <List
          :grid="{ gutter: 12, xs: 1, sm: 2, md: 3, lg: 4, xl: 4, xxl: 4 }"
          :data-source="pageRows"
          :pagination="paginationProp"
        >
          <template #header>
            <div
              style="display: flex; align-items: center; justify-content: space-between; flex-direction: row"
            >
              <span style="padding-left: 7px; font-size: 16px; font-weight: 500; line-height: 24px">
                摄像头列表
              </span>
              <div style="display: flex; gap: 8px">
                <slot name="header" />
              </div>
            </div>
          </template>
          <template #renderItem="{ item }">
            <ListItem
              v-if="item.type === 'nvr'"
              class="product-item normal nvr-list-item"
            >
              <NvrDeviceCard :item="item.nvrItem" @open="handleNvrCardOpen" />
            </ListItem>
            <ListItem
              v-else-if="item.type === 'gb_sip'"
              :class="item.gbItem.onLine ? 'product-item normal' : 'product-item error'"
            >
              <Gb28181DeviceCard
                :item="item.gbItem"
                @open="handleGbCardOpen"
                @refresh="handleGbCardRefresh"
                @view="handleGbCardView"
                @edit="handleGbCardEdit"
              />
            </ListItem>
            <ListItem v-else :class="item.device.online ? 'camera-item normal' : 'camera-item error'">
              <div class="camera-info">
                <div class="status">{{ item.device.online ? '在线' : '离线' }}</div>
                <div class="title o2">{{ formatCameraDeviceLabel(item.device) }}</div>
                <div class="props">
                  <div class="flex" style="justify-content: space-between">
                    <div class="prop">
                      <div class="label">设备型号</div>
                      <div
                        class="value model-value"
                        style="cursor: pointer"
                        @click="handleCopy(item.device.model)"
                      >
                        <span class="model-text">{{ item.device.model || '-' }}</span>
                        <Icon
                          icon="tdesign:copy-filled"
                          :size="14"
                          color="#4287FCFF"
                          class="model-copy-icon"
                        />
                      </div>
                    </div>
                    <div class="prop">
                      <div class="label">制造商</div>
                      <div class="value">{{ item.device.manufacturer || '-' }}</div>
                    </div>
                  </div>
                  <div class="flex" style="justify-content: space-between">
                    <div class="prop">
                      <div class="label">IP地址</div>
                      <div class="value">{{ item.device.ip || '-' }}</div>
                    </div>
                    <div class="prop">
                      <div class="label">端口</div>
                      <div class="value">{{ item.device.port || '-' }}</div>
                    </div>
                  </div>
                </div>
                <div class="btns">
                  <div
                    v-if="hasDirectPlayStream(item.device)"
                    class="btn"
                    @click="handlePlay(item.device)"
                  >
                    <Icon icon="octicon:play-16" :size="15" color="#3B82F6" />
                  </div>
                  <div
                    v-if="hasDirectPlayStream(item.device, true)"
                    class="btn"
                    @click="handlePlayAI(item.device)"
                  >
                    <Icon icon="hugeicons:ai-video" :size="15" color="#3B82F6" />
                  </div>
                  <div class="btn" @click="handleView(item.device)">
                    <Icon icon="ant-design:eye-filled" :size="15" color="#3B82F6" />
                  </div>
                  <div class="btn" @click="handleEdit(item.device)">
                    <Icon icon="ant-design:edit-filled" :size="15" color="#3B82F6" />
                  </div>
                  <div
                    v-if="supportsRtspForward(item.device)"
                    class="btn"
                    @click="handleToggleStream(item.device)"
                  >
                    <Icon
                      :icon="
                        getDeviceStreamStatus(item.device.id) === 'running'
                          ? 'ant-design:pause-circle-outlined'
                          : 'ant-design:swap-outline'
                      "
                      :size="15"
                      color="#3B82F6"
                    />
                  </div>
                  <Popconfirm
                    title="是否确认删除？"
                    ok-text="是"
                    cancel-text="否"
                    @confirm="handleDelete(item.device)"
                  >
                    <div class="btn">
                      <Icon icon="material-symbols:delete-outline-rounded" :size="15" color="#DC2626" />
                    </div>
                  </Popconfirm>
                </div>
              </div>
              <div class="camera-img">
                <img
                  :src="getCameraImage(item.device.manufacturer)"
                  alt=""
                  class="img"
                  @click="handleView(item.device)"
                />
              </div>
            </ListItem>
          </template>
        </List>
      </Spin>
    </div>
  </div>
</template>

<script lang="ts" setup>
import { computed, onMounted, reactive, ref } from 'vue';
import { List, Popconfirm, Spin } from 'ant-design-vue';
import { BasicForm, useForm } from '@/components/Form';
import { propTypes } from '@/utils/propTypes';
import { Icon } from '@/components/Icon';
import { useMessage } from '@/hooks/web/useMessage';
import { getStreamStatus } from '@/api/device/camera';
import HAIKANG_IMAGE from '@/assets/images/video/haikang.png';
import DAHUA_IMAGE from '@/assets/images/video/dahua.png';
import HUAWEI_IMAGE from '@/assets/images/video/huawei.png';
import OTHER_IMAGE from '@/assets/images/video/other.png';
import type { DeviceInfo, StreamStatusResponse } from '@/api/device/camera';
import { getDeviceList } from '@/api/device/camera';
import { formatCameraDeviceLabel } from '@/views/camera/utils/deviceLabel';
import { hasDirectPlayStream, supportsRtspForward } from '@/views/camera/utils/devicePlay';
import { queryVideoList } from '@/api/device/gb28181';
import {
  buildMergedCardRows,
  type GbSipDeviceSummary,
} from '@/views/camera/utils/gb28181DeviceGroup';
import { fetchNvrList } from '@/views/camera/utils/nvrDeviceGroup';
import type { NvrCardItem } from '@/views/camera/utils/nvrDeviceGroup';
import Gb28181DeviceCard, {
  type Gb28181CardItem,
} from '@/views/camera/components/Gb28181DeviceCard/index.vue';
import NvrDeviceCard from '@/views/camera/components/NvrDeviceCard/index.vue';

const ListItem = List.Item;

const props = defineProps({
  params: propTypes.object.def({}),
});

const emit = defineEmits([
  'getMethod',
  'delete',
  'edit',
  'view',
  'play',
  'playAI',
  'toggleStream',
  'openGbDevice',
  'refreshGbDevice',
  'viewGbDevice',
  'editGbDevice',
  'openNvrDevice',
]);

const { createMessage } = useMessage();

type CardRow =
  | { key: string; type: 'direct'; device: DeviceInfo }
  | { key: string; type: 'gb_sip'; gbItem: Gb28181CardItem }
  | { key: string; type: 'nvr'; nvrItem: NvrCardItem };

const allRows = ref<CardRow[]>([]);
const state = reactive({ loading: true });
const deviceStreamStatuses = ref<Record<string, string>>({});

const page = ref(1);
const pageSize = ref(8);
const total = ref(0);

const pageRows = computed(() => {
  const start = (page.value - 1) * pageSize.value;
  return allRows.value.slice(start, start + pageSize.value);
});

const paginationProp = computed(() => ({
  showSizeChanger: false,
  showQuickJumper: true,
  pageSize: pageSize.value,
  current: page.value,
  total: total.value,
  showTotal: (t: number) => `总 ${t} 条`,
  onChange: pageChange,
  onShowSizeChange: pageSizeChange,
}));

const [registerForm, { validate }] = useForm({
  schemas: [
    { field: 'deviceName', label: '设备名称', component: 'Input' },
    {
      field: 'online',
      label: '在线状态',
      component: 'Select',
      componentProps: {
        options: [
          { value: '', label: '全部' },
          { value: true, label: '在线' },
          { value: false, label: '离线' },
        ],
      },
    },
  ],
  labelWidth: 80,
  baseColProps: { span: 6 },
  actionColOptions: { span: 12, style: { textAlign: 'right' } },
  autoSubmitOnEnter: true,
  submitFunc: handleSubmit,
});

async function handleSubmit() {
  const data = await validate();
  await fetch(data);
}

function mergedItemsToRows(items: ReturnType<typeof buildMergedCardRows>): CardRow[] {
  return items.map((item) => {
    if (item.kind === 'nvr') {
      return {
        key: `nvr_${item.nvrItem.nvrId}`,
        type: 'nvr' as const,
        nvrItem: item.nvrItem,
      };
    }
    if (item.kind === 'gb_sip') {
      return {
        key: `gb_sip_${(item.gbItem as Gb28181CardItem).deviceIdentification}`,
        type: 'gb_sip' as const,
        gbItem: item.gbItem as Gb28181CardItem,
      };
    }
    return { key: item.device.id, type: 'direct' as const, device: item.device };
  });
}

function filterRows(rows: CardRow[], p: Record<string, any>): CardRow[] {
  let list = rows;
  const name = (p.deviceName || '').trim().toLowerCase();
  if (name) {
    list = list.filter((row) => {
      if (row.type === 'nvr') {
        return (
          row.nvrItem.name.toLowerCase().includes(name) ||
          row.nvrItem.ip.toLowerCase().includes(name)
        );
      }
      if (row.type === 'gb_sip') {
        return (
          row.gbItem.deviceIdentification.toLowerCase().includes(name) ||
          row.gbItem.name.toLowerCase().includes(name)
        );
      }
      return (row.device.name || '').toLowerCase().includes(name);
    });
  }
  if (p.online !== undefined && p.online !== '') {
    const online = p.online === true || p.online === 'true';
    list = list.filter((row) => {
      if (row.type === 'gb_sip') return row.gbItem.onLine === online;
      return row.device.online === online;
    });
  }
  return list;
}

const getDeviceStreamStatus = (deviceId: string) =>
  deviceStreamStatuses.value?.[deviceId] || 'unknown';

const getCameraImage = (manufacturer: string) => {
  if (!manufacturer) return OTHER_IMAGE;
  const mfr = manufacturer.toLowerCase();
  if (mfr.includes('海康') || mfr.includes('hikvision') || mfr.includes('hik')) return HAIKANG_IMAGE;
  if (mfr.includes('大华') || mfr.includes('dahua') || mfr.includes('dh')) return DAHUA_IMAGE;
  if (mfr.includes('华为') || mfr.includes('huawei')) return HUAWEI_IMAGE;
  return OTHER_IMAGE;
};

onMounted(() => {
  fetch();
  emit('getMethod', fetch);
});

async function fetch(p: Record<string, any> = {}) {
  const { params } = props;
  try {
    state.loading = true;
    const search = p.deviceName;
    const [devRes, gbRes, nvrs] = await Promise.all([
      getDeviceList({
        ...params,
        pageNo: 1,
        pageSize: 10000,
        search: search || undefined,
        ...p,
      }),
      queryVideoList({
        page: 1,
        count: 10000,
        query: search || undefined,
        status:
          p.online === true || p.online === 'true'
            ? true
            : p.online === false || p.online === 'false'
              ? false
              : undefined,
      }),
      fetchNvrList(),
    ]);
    let devices: DeviceInfo[] = [];
    if (devRes?.data) {
      devices = devRes.data;
    } else if (Array.isArray(devRes)) {
      devices = devRes;
    }
    const wvpDevices = gbRes?.data ?? [];
    const items = buildMergedCardRows(devices, wvpDevices, nvrs);
    allRows.value = filterRows(mergedItemsToRows(items), p);
    total.value = allRows.value.length;
    if (page.value > 1 && pageRows.value.length === 0) {
      page.value = 1;
    }
    for (const row of allRows.value) {
      if (row.type === 'direct' && !deviceStreamStatuses.value[row.device.id]) {
        deviceStreamStatuses.value[row.device.id] = 'unknown';
      }
    }
  } catch (error) {
    console.error('获取设备列表失败', error);
    allRows.value = [];
    total.value = 0;
  } finally {
    state.loading = false;
  }
}

function pageChange(p: number, pz: number) {
  page.value = p;
  pageSize.value = pz;
}

function pageSizeChange(_current: number, size: number) {
  pageSize.value = size;
  page.value = 1;
}

function handleNvrCardOpen(item: NvrCardItem) {
  emit('openNvrDevice', item);
}

function handleGbCardOpen(item: Gb28181CardItem) {
  emit('openGbDevice', item._summary);
}

function handleGbCardRefresh(item: Gb28181CardItem) {
  emit('refreshGbDevice', item._summary);
}

function handleGbCardView(item: Gb28181CardItem) {
  emit('viewGbDevice', item);
}

function handleGbCardEdit(item: Gb28181CardItem) {
  emit('editGbDevice', item);
}

function handleView(record: DeviceInfo) {
  emit('view', record);
}
function handleEdit(record: DeviceInfo) {
  emit('edit', record);
}
function handleDelete(record: DeviceInfo) {
  emit('delete', record);
}
function handlePlay(record: DeviceInfo) {
  emit('play', record);
}
function handlePlayAI(record: DeviceInfo) {
  emit('playAI', record);
}
function handleToggleStream(record: DeviceInfo) {
  emit('toggleStream', record);
}

async function handleCopy(text: string) {
  if (!text || text === '-') return;
  try {
    if (navigator.clipboard) {
      await navigator.clipboard.writeText(text);
    } else {
      const textarea = document.createElement('textarea');
      textarea.value = text;
      document.body.appendChild(textarea);
      textarea.select();
      document.execCommand('copy');
      document.body.removeChild(textarea);
    }
    createMessage.success('复制成功');
  } catch {
    createMessage.error('复制失败');
  }
}

defineExpose({
  fetch,
  deviceStreamStatuses,
  checkDeviceStreamStatus: async (deviceId: string) => {
    try {
      const response: StreamStatusResponse = await getStreamStatus(deviceId);
      deviceStreamStatuses.value[deviceId] =
        response.code === 0 ? response.data.status : 'error';
    } catch {
      deviceStreamStatuses.value[deviceId] = 'error';
    }
  },
});
</script>

<style lang="less" scoped>
.camera-card-list-wrapper {
  :deep(.ant-list-header) {
    border-block-end: 0;
    padding-top: 0;
    padding-bottom: 8px;
  }

  :deep(.ant-list) {
    padding: 6px;
  }

  :deep(.ant-list-item) {
    margin: 6px;
  }

  /* 直连设备卡片（与 camera/VideoCardList 一致） */
  :deep(.camera-item) {
    overflow: hidden;
    box-shadow: 0 0 4px #00000026;
    border-radius: 8px;
    padding: 16px 0;
    position: relative;
    background-color: #fff;
    background-repeat: no-repeat;
    background-position: center center;
    background-size: 104% 104%;
    transition: all 0.5s;
    min-height: 208px;
    height: 100%;

    &.normal {
      background-image: url('@/assets/images/product/blue-bg.719b437a.png');

      .camera-info .status {
        background: #d9dffd;
        color: #266cfbff;
      }
    }

    &.error {
      background-image: url('@/assets/images/product/red-bg.101af5ac.png');

      .camera-info .status {
        background: #fad7d9;
        color: #d43030;
      }
    }

    .camera-info {
      flex-direction: column;
      max-width: calc(100% - 128px);
      padding-left: 16px;

      .status {
        min-width: 90px;
        height: 25px;
        border-radius: 6px 0 0 6px;
        font-size: 12px;
        font-weight: 500;
        line-height: 25px;
        text-align: center;
        position: absolute;
        right: 0;
        top: 16px;
        padding: 0 8px;
        white-space: nowrap;
      }

      .title {
        font-size: 16px;
        font-weight: 600;
        color: #050708;
        line-height: 20px;
        height: 40px;
        padding-right: 90px;
      }

      .props {
        margin-top: 10px;

        .prop {
          flex: 1;
          margin-bottom: 10px;

          .label {
            font-size: 12px;
            font-weight: 400;
            color: #666;
            line-height: 14px;
          }

          .value {
            font-size: 14px;
            font-weight: 600;
            color: #050708;
            line-height: 14px;
            white-space: nowrap;
            overflow: hidden;
            text-overflow: ellipsis;
            margin-top: 6px;
          }
        }

        .model-value {
          display: flex;
          align-items: center;
          gap: 4px;
          overflow: visible;

          .model-text {
            max-width: 90px;
            overflow: hidden;
            text-overflow: ellipsis;
            white-space: nowrap;
            flex-shrink: 1;
          }

          .model-copy-icon {
            flex-shrink: 0;
          }
        }
      }

      .btns {
        display: flex;
        position: absolute;
        left: 16px;
        bottom: 16px;
        margin-top: 20px;
        width: 200px;
        height: 28px;
        border-radius: 45px;
        justify-content: space-around;
        padding: 0 10px;
        align-items: center;
        border: 2px solid #266cfbff;

        .btn {
          width: 28px;
          text-align: center;
          position: relative;
          cursor: pointer;

          &:before {
            content: '';
            display: block;
            position: absolute;
            width: 1px;
            height: 7px;
            background-color: #e2e2e2;
            left: 0;
            top: 9px;
          }

          &:first-child:before {
            display: none;
          }

          :deep(.anticon) {
            display: flex;
            align-items: center;
            justify-content: center;
            color: #87ceeb;
            transition: color 0.3s;
          }

          &:hover :deep(.anticon) {
            color: #5ba3f5;
          }
        }
      }
    }

    .camera-img {
      position: absolute;
      right: 20px;
      top: 50px;

      img {
        cursor: pointer;
        width: 120px;
      }
    }
  }

  /* 国标设备卡片（与 gb28181/VideoCardList 一致） */
  :deep(.product-item) {
    overflow: hidden;
    box-shadow: 0 0 4px #00000026;
    border-radius: 8px;
    padding: 16px 0;
    position: relative;
    background-color: #fff;
    background-repeat: no-repeat;
    background-position: center center;
    background-size: 104% 104%;
    transition: all 0.5s;
    min-height: 208px;
    height: 100%;

    &.normal {
      background-image: url('@/assets/images/product/blue-bg.719b437a.png');

      .status {
        background: #d9dffd;
        color: #266cfbff;
      }
    }

    &.error {
      background-image: url('@/assets/images/product/red-bg.101af5ac.png');

      .status {
        background: #fad7d9;
        color: #d43030;
      }
    }

    .product-info {
      flex-direction: column;
      max-width: calc(100% - 128px);
      padding-left: 16px;

      .status {
        width: 57px;
        height: 25px;
        border-radius: 6px 0 0 6px;
        font-size: 12px;
        font-weight: 500;
        line-height: 25px;
        text-align: center;
        position: absolute;
        right: 0;
        top: 16px;
      }

      .title {
        font-size: 16px;
        font-weight: 600;
        color: #050708;
        line-height: 20px;
        height: 40px;
      }

      .props {
        margin-top: 10px;

        .prop {
          flex: 1;
          margin-bottom: 10px;

          .label {
            font-size: 12px;
            font-weight: 400;
            color: #666;
            line-height: 14px;
          }

          .value {
            font-size: 14px;
            font-weight: 600;
            color: #050708;
            line-height: 14px;
            white-space: nowrap;
            overflow: hidden;
            text-overflow: ellipsis;
            margin-top: 6px;
          }
        }
      }

      .btns {
        display: flex;
        position: absolute;
        left: 16px;
        bottom: 16px;
        margin-top: 20px;
        width: 140px;
        height: 28px;
        border-radius: 45px;
        justify-content: space-around;
        padding: 0 10px;
        align-items: center;
        border: 2px solid #266cfbff;

        .btn {
          width: 28px;
          height: 22px;
          text-align: center;
          position: relative;

          &:before {
            content: '';
            display: block;
            position: absolute;
            width: 1px;
            height: 7px;
            background-color: #e2e2e2;
            left: 0;
            top: 9px;
          }

          &:first-child:before {
            display: none;
          }

          img {
            width: 15px;
            height: 15px;
            margin: 0 auto;
            cursor: pointer;
          }

          svg {
            width: 15px;
            height: 15px;
            cursor: pointer;
            margin-top: 4px;
          }
        }
      }
    }

    .product-img {
      position: absolute;
      right: 20px;
      top: 50px;

      img {
        cursor: pointer;
        width: 120px;
      }
    }
  }
}
</style>
