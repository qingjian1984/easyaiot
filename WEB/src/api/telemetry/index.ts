import { defHttp } from '@/utils/http/axios';

/**
 * PRD §4.5 遥测查询/导出（后端 iot-sink /telemetry/*，经 gateway /admin-api/telemetry）。
 * series 上限 10；原始跨度 ≤31 天；导出为 CSV blob。
 */

enum Api {
  Raw = '/telemetry/raw',
  Aggregate = '/telemetry/aggregate',
  Latest = '/telemetry/latest',
  Export = '/telemetry/export',
}

export interface TelemetrySeriesItem {
  deviceIdentification: string;
  propertyCode: string;
}

export interface TelemetrySampleRow {
  deviceIdentification: string;
  propertyCode: string;
  value: number | string | null;
  collectedAtMs: number;
  receivedAtMs: number;
  quality: string;
  messageId: string;
}

export interface TelemetryRawPage {
  totalRows: number;
  pageNo: number;
  pageSize: number;
  rows: TelemetrySampleRow[];
}

export type Granularity = 'MINUTE' | 'HOUR' | 'DAY';
export type AggregationType = 'MIN' | 'MAX' | 'AVG' | 'SUM' | 'COUNT';

export interface TelemetryAggregatePoint {
  deviceIdentification: string;
  propertyCode: string;
  bucketStartMs: number;
  value: number | null;
  sampleCount: number;
  quality: string;
}

export interface TelemetryLatestSample {
  deviceIdentification: string;
  propertyCode: string;
  value: number | string | null;
  collectedAtMs: number;
  receivedAtMs: number;
  quality: string;
}

export const postTelemetryRaw = (data: {
  series: TelemetrySeriesItem[];
  fromMs: number;
  toMs: number;
  pageNo?: number;
  pageSize?: number;
}) => {
  return defHttp.post<TelemetryRawPage>({ url: Api.Raw, data }, { isTransformResponse: true });
};

export const postTelemetryAggregate = (data: {
  series: TelemetrySeriesItem[];
  fromMs: number;
  toMs: number;
  granularity: Granularity;
  aggregation: AggregationType;
}) => {
  return defHttp.post<TelemetryAggregatePoint[]>(
    { url: Api.Aggregate, data },
    { isTransformResponse: true },
  );
};

export const postTelemetryLatest = (data: { series: TelemetrySeriesItem[] }) => {
  return defHttp.post<TelemetryLatestSample[]>(
    { url: Api.Latest, data },
    { isTransformResponse: true },
  );
};

/** CSV 导出（blob 下载；受原始查询同一配额约束）。 */
export const postTelemetryExport = (data: {
  series: TelemetrySeriesItem[];
  fromMs: number;
  toMs: number;
}) => {
  return defHttp.post<Blob>(
    {
      url: Api.Export,
      data,
      responseType: 'blob',
    },
    { isTransformResponse: false, isReturnNativeResponse: true },
  );
};
