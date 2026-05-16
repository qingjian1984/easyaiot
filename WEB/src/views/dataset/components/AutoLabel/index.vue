<template>
  <div :class="prefixCls" :style="getWrapStyle">
    <Spin :spinning="loading" size="large" :style="getWrapStyle">
      <iframe
        :src="iframeSrc"
        :class="`${prefixCls}__main`"
        ref="frameRef"
        title="自动标注平台"
        @load="hideLoading"
      />
    </Spin>
  </div>
</template>

<script lang="ts" setup>
import type { CSSProperties } from 'vue';
import { ref, unref, computed, onMounted } from 'vue';
import { Spin } from 'ant-design-vue';
import { useRoute } from 'vue-router';
import { useWindowSizeFn } from '@/hooks/event/useWindowSizeFn';
import { useDesign } from '@/hooks/web/useDesign';
import { useLayoutHeight } from '@/layouts/default/content/useContentViewHeight';

defineOptions({ name: 'AutoLabel' });

/** 与 NodeRed 一致：通过 /dev-api 代理访问独立标注服务页面 */
const AUTO_LABEL_PATH = '/dev-api/autoLabeling';

const route = useRoute();
const loading = ref(true);
const topRef = ref(50);
const heightRef = ref(window.innerHeight);
const frameRef = ref<HTMLFrameElement>();
const { headerHeightRef } = useLayoutHeight();
const { prefixCls } = useDesign('iframe-page');

const iframeSrc = computed(() => {
  const base = AUTO_LABEL_PATH.endsWith('/') ? AUTO_LABEL_PATH : `${AUTO_LABEL_PATH}/`;
  const params = new URLSearchParams();
  const datasetId = route.params.id ?? route.query.datasetId;
  if (datasetId != null && String(datasetId) !== '') {
    params.set('datasetId', String(datasetId));
  }
  const qs = params.toString();
  return qs ? `${base}?${qs}` : base;
});

useWindowSizeFn(calcHeight, { wait: 150, immediate: true });

const getWrapStyle = computed((): CSSProperties => {
  return {
    height: `${unref(heightRef)}px`,
  };
});

function calcHeight() {
  const iframe = unref(frameRef);
  if (!iframe) {
    return;
  }
  const top = headerHeightRef.value;
  topRef.value = top;
  // 数据集列表页一级 Tab 导航栏预留高度
  const tabOffset = 100;
  heightRef.value = window.innerHeight - top - tabOffset;
  const clientHeight = document.documentElement.clientHeight - top - tabOffset;
  iframe.style.height = `${Math.max(clientHeight, 480)}px`;
}

function hideLoading() {
  loading.value = false;
  calcHeight();
}

onMounted(() => {
  calcHeight();
});
</script>

<style lang="less" scoped>
@prefix-cls: ~'@{namespace}-iframe-page';

.@{prefix-cls} {
  .ant-spin-nested-loading {
    position: relative;
    height: 100%;

    .ant-spin-container {
      width: 100%;
      height: 100%;
      padding: 0;
    }
  }

  &__main {
    box-sizing: border-box;
    width: 100%;
    height: 100%;
    overflow: hidden;
    border: 0;
    background-color: @component-background;
  }
}
</style>
