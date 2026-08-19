<template>
  <div class="p-4 telemetry-history">
    <Card title="遥测历史查询" class="query-card">
      <div class="query-form">
        <div class="form-row">
          <span class="form-label">测点</span>
          <div class="form-control-wide">
            <MetricPicker v-model="series" />
          </div>
        </div>
        <div class="form-row">
          <span class="form-label">时间范围</span>
          <RangePicker
            v-model:value="range"
            :show-time="{ format: 'HH:mm' }"
            format="YYYY-MM-DD HH:mm"
            :placeholder="['开始时间', '结束时间']"
            class="form-control"
          />
        </div>
        <div class="form-row">
          <span class="form-label">粒度</span>
          <Segmented v-model:value="granularity" :options="granularityOptions" class="form-control" />
        </div>
        <div class="form-row" v-if="granularity !== 'RAW'">
          <span class="form-label">聚合</span>
          <Segmented v-model:value="aggregation" :options="aggregationOptions" class="form-control" />
        </div>
        <div class="form-row actions">
          <Button type="primary" :loading="loading" @click="query">查询</Button>
          <Button :loading="exporting" @click="exportCsv">导出 CSV</Button>
        </div>
      </div>
    </Card>

    <Card title="曲线对比" class="chart-card">
      <div ref="chartDom" class="chart-dom"></div>
    </Card>

    <Card title="数据明细">
      <Table
        :columns="columns"
        :data-source="tableRows"
        :pagination="{ pageSize: 20, showSizeChanger: true }"
        row-key="__key"
        size="small"
      >
        <template #bodyCell="{ column, record }">
          <template v-if="column.key === 'quality'">
            <QualityTag :quality="record.quality" />
          </template>
        </template>
      </Table>
    </Card>
  </div>
</template>

<script lang="ts" setup>
  /**
   * PRD §4.5 遥测历史页：多设备多测点曲线对比 + 原始/分钟/小时/日粒度
   * + min/max/avg/sum/count 聚合 + 质量码标色 + CSV 导出（受配额约束）。
   */
  import { computed, onBeforeUnmount, onMounted, ref } from 'vue';
  import { Button, Card, RangePicker, Segmented, Table, message } from 'ant-design-vue';
  import * as echarts from 'echarts/core';
  import { LineChart } from 'echarts/charts';
  import {
    DataZoomComponent,
    GridComponent,
    LegendComponent,
    TooltipComponent,
  } from 'echarts/components';
  import { CanvasRenderer } from 'echarts/renderers';
  import dayjs, { type Dayjs } from 'dayjs';
  import MetricPicker from '../components/MetricPicker.vue';
  import QualityTag from '../components/QualityTag.vue';
  import {
    postTelemetryAggregate,
    postTelemetryExport,
    postTelemetryRaw,
    type AggregationType,
    type Granularity,
    type TelemetrySeriesItem,
  } from '@/api/telemetry';

  echarts.use([CanvasRenderer, LineChart, TooltipComponent, GridComponent, LegendComponent, DataZoomComponent]);

  const series = ref<TelemetrySeriesItem[]>([{ deviceIdentification: '', propertyCode: '' }]);
  const range = ref<[Dayjs, Dayjs]>([dayjs().subtract(1, 'hour'), dayjs()]);
  const granularity = ref<Granularity>('MINUTE');
  const aggregation = ref<AggregationType>('AVG');
  const loading = ref(false);
  const exporting = ref(false);

  const granularityOptions = [
    { label: '原始', value: 'RAW' },
    { label: '分钟', value: 'MINUTE' },
    { label: '小时', value: 'HOUR' },
    { label: '日', value: 'DAY' },
  ];
  const aggregationOptions = [
    { label: '平均', value: 'AVG' },
    { label: '最大', value: 'MAX' },
    { label: '最小', value: 'MIN' },
    { label: '累计', value: 'SUM' },
    { label: '计数', value: 'COUNT' },
  ];

  const tableRows = ref<Record<string, any>[]>([]);

  const columns = computed(() => [
    { title: '设备', dataIndex: 'deviceIdentification', key: 'device' },
    { title: '测点', dataIndex: 'propertyCode', key: 'property' },
    { title: '值', dataIndex: 'valueText', key: 'value' },
    granularity.value === 'RAW'
      ? { title: '采集时间', dataIndex: 'timeText', key: 'time' }
      : { title: '桶起点', dataIndex: 'timeText', key: 'time' },
    ...(granularity.value === 'RAW'
      ? [{ title: '接收时间', dataIndex: 'receivedText', key: 'received' }]
      : [{ title: '样本数', dataIndex: 'sampleCount', key: 'count' }]),
    { title: '质量', dataIndex: 'quality', key: 'quality' },
  ]);

  const validSeries = computed(() =>
    series.value.filter(
      (item) => item.deviceIdentification?.trim() && item.propertyCode?.trim(),
    ),
  );

  const chartDom = ref<HTMLDivElement>();
  let chart: echarts.ECharts | null = null;

  const renderChart = (seriesData: { name: string; points: [number, number][] }[]) => {
    if (!chartDom.value) return;
    if (!chart) {
      chart = echarts.init(chartDom.value);
      window.addEventListener('resize', resizeChart);
    }
    chart.setOption(
      {
        tooltip: { trigger: 'axis' },
        legend: { top: 0 },
        grid: { left: 56, right: 24, top: 36, bottom: 48 },
        xAxis: { type: 'time' },
        yAxis: { type: 'value', scale: true },
        dataZoom: [{ type: 'inside' }, { type: 'slider', height: 18, bottom: 8 }],
        series: seriesData.map((item) => ({
          name: item.name,
          type: 'line',
          showSymbol: false,
          data: item.points,
        })),
      },
      { notMerge: true },
    );
  };

  const resizeChart = () => chart?.resize();

  onMounted(() => {
    chart = null;
  });

  onBeforeUnmount(() => {
    window.removeEventListener('resize', resizeChart);
    chart?.dispose();
    chart = null;
  });

  const query = async () => {
    if (!validSeries.value.length) {
      message.warning('请至少填写一个有效的设备标识与测点编码');
      return;
    }
    const [from, to] = range.value;
    if (!from || !to) {
      message.warning('请选择时间范围');
      return;
    }
    loading.value = true;
    try {
      if (granularity.value === 'RAW') {
        const page = await postTelemetryRaw({
          series: validSeries.value,
          fromMs: from.valueOf(),
          toMs: to.valueOf(),
          pageNo: 1,
          pageSize: 1000,
        });
        const rows = page?.rows || [];
        tableRows.value = rows.map((row, index) => ({
          __key: `r-${index}`,
          deviceIdentification: row.deviceIdentification,
          propertyCode: row.propertyCode,
          valueText: row.value ?? '-',
          timeText: dayjs(row.collectedAtMs).format('YYYY-MM-DD HH:mm:ss.SSS'),
          receivedText: dayjs(row.receivedAtMs).format('YYYY-MM-DD HH:mm:ss.SSS'),
          quality: row.quality,
        }));
        // 每序列一条折线（值 vs 采集时间）
        const bySeries = new Map<string, [number, number][]>();
        rows.forEach((row) => {
          const key = `${row.deviceIdentification} / ${row.propertyCode}`;
          const list = bySeries.get(key) || [];
          if (row.value !== null && row.value !== undefined && row.value !== '') {
            list.push([row.collectedAtMs, Number(row.value)]);
          }
          bySeries.set(key, list);
        });
        renderChart(
          Array.from(bySeries.entries()).map(([name, points]) => ({
            name,
            points: points.sort((a, b) => a[0] - b[0]),
          })),
        );
      } else {
        const points = await postTelemetryAggregate({
          series: validSeries.value,
          fromMs: from.valueOf(),
          toMs: to.valueOf(),
          granularity: granularity.value,
          aggregation: aggregation.value,
        });
        tableRows.value = (points || []).map((point, index) => ({
          __key: `a-${index}`,
          deviceIdentification: point.deviceIdentification,
          propertyCode: point.propertyCode,
          valueText: point.value ?? '-',
          timeText: dayjs(point.bucketStartMs).format('YYYY-MM-DD HH:mm'),
          sampleCount: point.sampleCount,
          quality: point.quality,
        }));
        const bySeries = new Map<string, [number, number][]>();
        (points || []).forEach((point) => {
          const key = `${point.deviceIdentification} / ${point.propertyCode}`;
          const list = bySeries.get(key) || [];
          if (point.value !== null && point.value !== undefined) {
            list.push([point.bucketStartMs, Number(point.value)]);
          }
          bySeries.set(key, list);
        });
        renderChart(
          Array.from(bySeries.entries()).map(([name, points2]) => ({
            name,
            points: points2.sort((a, b) => a[0] - b[0]),
          })),
        );
      }
    } catch (error: any) {
      message.error(error?.message || '查询失败');
    } finally {
      loading.value = false;
    }
  };

  const exportCsv = async () => {
    if (!validSeries.value.length) {
      message.warning('请先填写测点再导出');
      return;
    }
    const [from, to] = range.value;
    exporting.value = true;
    try {
      const response = await postTelemetryExport({
        series: validSeries.value,
        fromMs: from.valueOf(),
        toMs: to.valueOf(),
      });
      const blob = new Blob([response as any], { type: 'text/csv;charset=utf-8' });
      const url = URL.createObjectURL(blob);
      const link = document.createElement('a');
      link.href = url;
      link.download = `telemetry-${dayjs().format('YYYYMMDD-HHmmss')}.csv`;
      link.click();
      URL.revokeObjectURL(url);
    } catch (error: any) {
      message.error(error?.message || '导出失败');
    } finally {
      exporting.value = false;
    }
  };
</script>

<style scoped>
  .telemetry-history {
    display: flex;
    flex-direction: column;
    gap: 12px;
  }

  .query-form {
    display: flex;
    flex-direction: column;
    gap: 10px;
  }

  .form-row {
    display: flex;
    align-items: flex-start;
    gap: 12px;
  }

  .form-label {
    width: 64px;
    line-height: 32px;
    flex-shrink: 0;
  }

  .form-control {
    min-width: 320px;
  }

  .form-control-wide {
    flex: 1;
    max-width: 640px;
  }

  .actions {
    padding-left: 76px;
  }

  .chart-dom {
    width: 100%;
    height: 360px;
  }
</style>
