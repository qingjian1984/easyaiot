import { defHttp } from '@/utils/http/axios';
import {
  exportAnnotationDataset,
  extractAnnotationFrames,
  importAnnotationLabelme,
  type DatasetAnnotationExportParams,
} from '@/api/device/dataset';

enum Api {
  /** AI 推理与自动标注任务（model-server） */
  AutoLabel = '/model/dataset',
  AIService = '/model/deploy_service',
}

const commonApi = (
  method: 'get' | 'post' | 'delete' | 'put',
  url: string,
  params = {},
  headers = {},
  isTransformResponse = true,
) => {
  defHttp.setHeader({ 'X-Authorization': 'Bearer ' + localStorage.getItem('jwt_token') });

  return defHttp[method](
    {
      url,
      headers: {
        // @ts-ignore
        ignoreCancelToken: true,
        ...headers,
      },
      ...params,
    },
    {
      isTransformResponse: isTransformResponse,
    },
  );
};

// —— AI 自动标注（仍走 model-server）——

export const startAutoLabel = (datasetId: number, data: Record<string, unknown>) => {
  return commonApi('post', `${Api.AutoLabel}/dataset/${datasetId}/auto-label/start`, { data });
};

export const getAutoLabelTask = (datasetId: number, taskId: number) => {
  return commonApi('get', `${Api.AutoLabel}/dataset/${datasetId}/auto-label/task/${taskId}`);
};

export const listAutoLabelTasks = (datasetId: number, params: any) => {
  return commonApi('get', `${Api.AutoLabel}/dataset/${datasetId}/auto-label/tasks`, { params });
};

export const getAIServiceList = (params = {}) => {
  return commonApi('get', `${Api.AIService}/list`, { params }, {}, false);
};

export const labelSingleImage = (datasetId: number, imageId: number, data: Record<string, unknown>) => {
  return commonApi('post', `${Api.AutoLabel}/dataset/${datasetId}/auto-label/image/${imageId}`, { data });
};

// —— 以下导入/导出已迁移至 iot-dataset（/dataset/{id}/annotation/*），保留别名兼容旧调用 ——

/** @deprecated 请使用 `exportAnnotationDataset`（@/api/device/dataset） */
export const exportLabeledDataset = (datasetId: number, params: Record<string, unknown>) => {
  const body: DatasetAnnotationExportParams = {
    trainRatio: Number(params.train_ratio ?? 0.7),
    valRatio: Number(params.val_ratio ?? 0.2),
    testRatio: Number(params.test_ratio ?? 0.1),
    sampleSelection: (params.sample_selection ?? params.sample_type ?? 'all') as DatasetAnnotationExportParams['sampleSelection'],
    selectedClasses: (params.selected_classes as string[]) ?? [],
    exportPrefix: String(params.export_prefix ?? params.file_prefix ?? ''),
  };
  return exportAnnotationDataset(datasetId, body);
};

/** @deprecated 请使用 `extractAnnotationFrames`（@/api/device/dataset） */
export const extractFramesFromVideo = extractAnnotationFrames;

/** @deprecated 请使用 `importAnnotationLabelme`（@/api/device/dataset） */
export const importLabelmeDataset = importAnnotationLabelme;
