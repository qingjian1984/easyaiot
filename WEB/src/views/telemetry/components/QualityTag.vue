<template>
  <Tag :color="color">{{ label }}</Tag>
</template>

<script lang="ts" setup>
  /**
   * PRD §4.4 质量码标签：把后端 TelemetryQuality 文本映射为
   * 正常/超时/无效/越界/人工修正/补传 等 PRD 文案与语义色。
   */
  import { computed } from 'vue';
  import { Tag } from 'ant-design-vue';

  const props = defineProps<{ quality?: string | null }>();

  /** 受控映射：未知质量码统一按"未知"灰色展示，不猜语义。 */
  const QUALITY_MAP: Record<string, { label: string; color: string }> = {
    GOOD: { label: '正常', color: 'green' },
    UNCERTAIN: { label: '不确定', color: 'orange' },
    BAD: { label: '无效', color: 'red' },
    TIMEOUT: { label: '超时', color: 'orange' },
    INVALID: { label: '无效', color: 'red' },
    OUT_OF_RANGE: { label: '越界', color: 'volcano' },
    MANUAL_CORRECTED: { label: '人工修正', color: 'blue' },
    BACKFILLED: { label: '补传', color: 'purple' },
  };

  const entry = computed(() => {
    const key = (props.quality || 'GOOD').toUpperCase();
    return QUALITY_MAP[key] || { label: props.quality || '未知', color: 'default' };
  });
  const label = computed(() => entry.value.label);
  const color = computed(() => entry.value.color);
</script>
