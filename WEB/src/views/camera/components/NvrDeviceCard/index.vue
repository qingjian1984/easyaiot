<template>
  <div class="product-info nvr-card-info">
    <div class="status online">NVR</div>
    <div class="title o2">{{ item.name }}</div>
    <div class="props">
      <div class="prop">
        <div class="label">IP / 端口</div>
        <div class="value">{{ item.ip }}:{{ item.port }}</div>
      </div>
      <div class="flex" style="justify-content: space-between">
        <div class="prop">
          <div class="label">品牌</div>
          <div class="value">{{ item.vendor_label || '-' }}</div>
        </div>
        <div class="prop">
          <div class="label">挂载摄像头</div>
          <div class="value">{{ item.camera_count }} 路</div>
        </div>
      </div>
      <div class="prop" v-if="item.rtsp_url">
        <div class="label">RTSP</div>
        <div class="value rtsp-value" :title="item.rtsp_url">{{ item.rtsp_url }}</div>
      </div>
    </div>
    <div class="btns" @click.stop>
      <div class="btn" title="查看挂载设备" @click="emit('open', item)">
        <Icon icon="ant-design:cluster-outlined" :size="16" color="#3B82F6" />
      </div>
      <div class="btn" title="复制 RTSP" v-if="item.rtsp_url" @click="handleCopyRtsp">
        <Icon icon="tdesign:copy-filled" :size="15" color="#4287FCFF" />
      </div>
    </div>
  </div>
  <div class="product-img nvr-card-img">
    <img :src="deviceImage" alt="" class="img" @click="emit('open', item)" />
  </div>
</template>

<script lang="ts" setup>
import { computed } from 'vue';
import { Icon } from '@/components/Icon';
import { useMessage } from '@/hooks/web/useMessage';
import { copyText } from '@/utils/copyTextToClipboard';
import HAIKANG_IMAGE from '@/assets/images/video/haikang.png';
import DAHUA_IMAGE from '@/assets/images/video/dahua.png';
import OTHER_IMAGE from '@/assets/images/video/other.png';
import type { NvrCardItem } from '@/views/camera/utils/nvrDeviceGroup';

const props = defineProps<{ item: NvrCardItem }>();
const emit = defineEmits<{ open: [item: NvrCardItem] }>();
const { createMessage } = useMessage();

const deviceImage = computed(() => {
  const v = (props.item.vendor_label || '').toLowerCase();
  if (v.includes('海康') || v.includes('hik')) return HAIKANG_IMAGE;
  if (v.includes('大华') || v.includes('dahua')) return DAHUA_IMAGE;
  return OTHER_IMAGE;
});

function handleCopyRtsp() {
  if (!props.item.rtsp_url) return;
  copyText(props.item.rtsp_url, 'RTSP 已复制');
  createMessage.success('已复制');
}
</script>

<style lang="less" scoped>
.nvr-card-info .rtsp-value {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  max-width: 100%;
}
.nvr-card-img .img {
  cursor: pointer;
}
</style>
