<template>
  <div class="metric-picker">
    <div v-for="(item, index) in seriesRef" :key="index" class="metric-row">
      <Input
        v-model:value="item.deviceIdentification"
        placeholder="设备标识"
        class="metric-input"
        @change="emitChange"
      />
      <Input
        v-model:value="item.propertyCode"
        placeholder="测点编码"
        class="metric-input"
        @change="emitChange"
      />
      <Button
        :danger="seriesRef.length > 1"
        :disabled="seriesRef.length <= 1"
        size="small"
        @click="removeRow(index)"
      >
        删除
      </Button>
    </div>
    <Button
      type="dashed"
      block
      :disabled="seriesRef.length >= 10"
      @click="addRow"
    >
      + 添加测点（最多 10 个）
    </Button>
  </div>
</template>

<script lang="ts" setup>
  /**
   * 多设备多测点选择器（PRD §4.5：series ≤10）。
   * M1 以标识手工输入为主；后续可切换物模型树数据源。
   */
  import { ref, watch } from 'vue';
  import { Button, Input } from 'ant-design-vue';
  import type { TelemetrySeriesItem } from '@/api/telemetry';

  const props = defineProps<{ modelValue: TelemetrySeriesItem[] }>();
  const emit = defineEmits<{ (e: 'update:modelValue', v: TelemetrySeriesItem[]): void }>();

  const seriesRef = ref<TelemetrySeriesItem[]>(
    props.modelValue?.length ? props.modelValue.map((s) => ({ ...s })) : [{ deviceIdentification: '', propertyCode: '' }],
  );

  watch(
    () => props.modelValue,
    (next) => {
      if (next && next.length) {
        seriesRef.value = next.map((s) => ({ ...s }));
      }
    },
  );

  const emitChange = () => {
    emit('update:modelValue', seriesRef.value.map((s) => ({ ...s })));
  };

  const addRow = () => {
    if (seriesRef.value.length >= 10) return;
    seriesRef.value.push({ deviceIdentification: '', propertyCode: '' });
    emitChange();
  };

  const removeRow = (index: number) => {
    if (seriesRef.value.length <= 1) return;
    seriesRef.value.splice(index, 1);
    emitChange();
  };
</script>

<style scoped>
  .metric-picker {
    display: flex;
    flex-direction: column;
    gap: 8px;
  }

  .metric-row {
    display: flex;
    gap: 8px;
    align-items: center;
  }

  .metric-input {
    flex: 1;
  }
</style>
