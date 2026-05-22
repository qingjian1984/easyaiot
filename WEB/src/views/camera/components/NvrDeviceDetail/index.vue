<template>
  <div class="nvr-device-detail">
    <PageHeader
      class="nvr-detail-header"
      :title="title || `NVR ${nvrId}`"
      sub-title="点击下方摄像头进行管理或播放"
      @back="emit('back')"
    />
    <Spin :spinning="loading">
      <div v-if="nvrInfo" class="nvr-summary">
        <Descriptions :column="3" size="small" bordered>
          <DescriptionsItem label="IP">{{ nvrInfo.ip }}:{{ nvrInfo.port }}</DescriptionsItem>
          <DescriptionsItem label="品牌">{{ nvrInfo.vendor_label || nvrInfo.vendor || '—' }}</DescriptionsItem>
          <DescriptionsItem label="型号">{{ nvrInfo.model || '—' }}</DescriptionsItem>
          <DescriptionsItem label="序列号">{{ nvrInfo.serial_number || '—' }}</DescriptionsItem>
          <DescriptionsItem label="固件">{{ nvrInfo.firmware_version || '—' }}</DescriptionsItem>
          <DescriptionsItem label="MAC">{{ nvrInfo.mac || '—' }}</DescriptionsItem>
          <DescriptionsItem label="Web" :span="3">
            <a v-if="nvrInfo.web_url" :href="nvrInfo.web_url" target="_blank" rel="noopener">{{ nvrInfo.web_url }}</a>
            <span v-else>—</span>
          </DescriptionsItem>
          <DescriptionsItem label="RTSP(经NVR)" :span="3">
            <span class="rtsp-line">{{ nvrInfo.rtsp_url || '—' }}</span>
            <a-button v-if="nvrInfo.rtsp_url" type="link" size="small" @click="copyRtsp(nvrInfo.rtsp_url!)">复制</a-button>
          </DescriptionsItem>
        </Descriptions>
      </div>
      <div class="channel-section">
        <div class="section-title">挂载摄像头（{{ cameras.length }}）</div>
        <Empty v-if="!cameras.length" description="暂无挂载摄像头，可通过网段扫描 NVR 注册通道" />
        <List
          v-else
          :grid="{ gutter: 12, xs: 1, sm: 2, md: 3, lg: 3, xl: 4 }"
          :data-source="cameras"
        >
          <template #renderItem="{ item }">
            <ListItem>
              <Card size="small" :title="formatCameraDeviceLabel(item)">
                <p>通道：{{ item.nvr_channel }}</p>
                <p>IP：{{ item.ip || '—' }}</p>
                <p class="ellipsis" :title="item.source">RTSP(经NVR)：{{ item.rtsp_url || item.source || '—' }}</p>
                <p v-if="item.rtsp_direct" class="ellipsis" :title="item.rtsp_direct">RTSP(直连)：{{ item.rtsp_direct }}</p>
                <p>状态：{{ item.online_text || (item.online ? '在线' : item.online === false ? '离线' : '—') }}</p>
                <template #actions>
                  <a-button type="link" size="small" @click="emit('view', item)">查看</a-button>
                  <a-button type="link" size="small" @click="emit('edit', item)">编辑</a-button>
                  <a-button
                    v-if="hasDirectPlayStream(item)"
                    type="link"
                    size="small"
                    @click="emit('play', item)"
                  >
                    播放
                  </a-button>
                </template>
              </Card>
            </ListItem>
          </template>
        </List>
      </div>
    </Spin>
  </div>
</template>

<script lang="ts" setup>
import { onMounted, ref, watch } from 'vue';
import { Card, Descriptions, DescriptionsItem, Empty, List, PageHeader, Spin } from 'ant-design-vue';
import { getNvrDetail } from '@/api/device/camera';
import type { DeviceInfo, NvrInfo } from '@/api/device/camera';
import { formatCameraDeviceLabel } from '@/views/camera/utils/deviceLabel';
import { hasDirectPlayStream } from '@/views/camera/utils/devicePlay';
import { copyText } from '@/utils/copyTextToClipboard';
import { useMessage } from '@/hooks/web/useMessage';

const ListItem = List.Item;

const props = defineProps<{
  nvrId: number;
  title?: string;
}>();

const emit = defineEmits<{
  back: [];
  view: [device: DeviceInfo];
  edit: [device: DeviceInfo];
  play: [device: DeviceInfo];
}>();

const { createMessage } = useMessage();
const loading = ref(false);
const nvrInfo = ref<NvrInfo | null>(null);
const cameras = ref<Array<DeviceInfo & { online_text?: string; rtsp_url?: string }>>([]);

async function load() {
  loading.value = true;
  try {
    const res = await getNvrDetail(props.nvrId, true);
    const data = (res as NvrInfo) || (res as { data?: NvrInfo })?.data;
    nvrInfo.value = data || null;
    cameras.value = (data?.cameras || []).map((c) => ({
      ...c,
      id: c.id,
      name: c.name || '',
      source: c.source || c.rtsp_url || '',
      device_kind: 'nvr_channel',
      nvr_id: props.nvrId,
      nvr_channel: c.nvr_channel,
    })) as DeviceInfo[];
  } catch (e) {
    console.error(e);
    createMessage.error('加载 NVR 详情失败');
  } finally {
    loading.value = false;
  }
}

function copyRtsp(url: string) {
  copyText(url, '已复制 RTSP');
}

onMounted(load);
watch(() => props.nvrId, load);
</script>

<style lang="less" scoped>
.nvr-device-detail {
  display: flex;
  flex-direction: column;
  min-height: 520px;
  background: #f5f5f5;

  .nvr-detail-header {
    background: #fff;
    margin-bottom: 12px;
    padding: 8px 16px;
  }

  .nvr-summary {
    background: #fff;
    padding: 16px;
    margin-bottom: 12px;
    border-radius: 8px;
  }

  .channel-section {
    background: #fff;
    padding: 16px;
    border-radius: 8px;
    flex: 1;

    .section-title {
      font-weight: 600;
      margin-bottom: 12px;
    }
  }

  .rtsp-line {
    word-break: break-all;
    margin-right: 8px;
  }

  .ellipsis {
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
    margin-bottom: 4px;
  }
}
</style>
