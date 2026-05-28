<template>
  <BasicModal
    @register="register"
    width="800px"
    @cancel="handleCancel"
    :canFullscreen="false"
    :showOkBtn="false"
    :showCancelBtn="false"
    :get-container="getContainer"
    wrap-class-name="dataset-import-modal-wrap"
  >
    <template #title>
      <span class="modal-title-with-icon">
        <Icon icon="ant-design:upload-outlined" class="title-icon" />
        添加数据集
      </span>
    </template>
    <div class="modal-content dataset-modal-content">
      <Tabs v-model:activeKey="tabActive" class="dataset-tabs">
        <TabPane key="image" tab="图片文件夹">
          <div class="upload-area" @drop.prevent="onImageDrop" @dragover.prevent>
            <pre class="dataset-tree-example">uploads/
├── photo1.jpg
├── photo2.png
└── subdir__img3.jpg</pre>
            <Icon icon="ant-design:folder-open-outlined" class="upload-area-icon" />
            <p>拖拽文件夹到此处或点击选择文件夹（支持子目录中的图片）</p>
            <input
              ref="folderInputRef"
              type="file"
              webkitdirectory
              directory
              multiple
              style="display: none"
              @change="onFolderSelect"
            />
            <Button type="primary" @click="triggerFolderSelect">选择文件夹</Button>
            <div v-if="folderFileCount > 0" class="selected-hint">已选择 {{ folderFileCount }} 个文件</div>
          </div>
          <div class="upload-actions">
            <Button type="primary" :loading="loading" :disabled="folderFileCount === 0" @click="uploadImages">
              <Icon icon="ant-design:upload-outlined" />
              上传图片到数据集
            </Button>
          </div>
        </TabPane>

        <TabPane key="video" tab="视频文件">
          <div class="upload-area" @drop.prevent="onVideoDrop" @dragover.prevent>
            <pre class="dataset-tree-example">（本地）my_clip.mp4
        ↓ 抽帧
uploads/
├── my_clip_frame_000030.jpg
└── my_clip_frame_000060.jpg</pre>
            <Icon icon="ant-design:video-camera-outlined" class="upload-area-icon" />
            <p>拖拽视频文件到此处或点击选择视频</p>
            <input ref="videoInputRef" type="file" accept="video/*" style="display: none" @change="onVideoSelect" />
            <Button type="primary" @click="triggerVideoSelect">选择视频文件</Button>
            <div v-if="selectedVideo" class="selected-hint">已选择: {{ selectedVideo.name }}</div>
            <div class="form-group inline-form">
              <label>抽帧间隔 (帧):</label>
              <InputNumber v-model:value="frameInterval" :min="1" :max="1000" />
              <small>每隔指定帧数保存一帧作为样本</small>
            </div>
          </div>
          <div class="upload-actions">
            <Button type="primary" :loading="loading" :disabled="!selectedVideo" @click="extractFrames">
              <Icon icon="ant-design:film-outlined" />
              抽帧并添加到数据集
            </Button>
          </div>
        </TabPane>

        <TabPane key="path" tab="ImageFolder">
          <div class="upload-area path-area">
            <pre class="dataset-tree-example">/data/my_label_project/
├── cats/
│   ├── 001.jpg
│   └── 001.json
├── dogs/
│   └── 002.jpg
├── annotations/
│   └── instances.json
└── notes.txt</pre>
            <div class="form-group">
              <label>工程根目录（绝对路径）</label>
              <Input v-model:value="datasetPath" placeholder="/data/my_label_project" />
            </div>
          </div>
          <div class="upload-actions">
            <Button type="primary" :loading="loading" @click="importImageFolderPath">
              <Icon icon="ant-design:folder-add-outlined" />
              导入 ImageFolder（LabelMe / COCO / YOLO 回退）
            </Button>
          </div>
        </TabPane>

        <TabPane key="yolo" tab="YOLO">
          <div class="upload-area path-area">
            <pre class="dataset-tree-example">/data/yolo_dataset/
├── data.yaml
├── classes.txt
├── train/
│   ├── images/
│   │   └── img001.jpg
│   └── labels/
│       └── img001.txt
├── val/ ...
└── test/ ...</pre>
            <div class="form-group">
              <label>YOLO 数据集根目录（绝对路径）</label>
              <Input v-model:value="yoloPath" placeholder="/data/yolo_dataset" />
            </div>
          </div>
          <div class="upload-actions">
            <Button type="primary" :loading="loading" @click="importYoloPath">
              <Icon icon="ant-design:import-outlined" />
              导入 YOLO（仅 .txt）
            </Button>
          </div>
        </TabPane>

        <TabPane key="coco" tab="COCO">
          <div class="upload-area path-area">
            <pre class="dataset-tree-example">/data/coco2017/
├── annotations/
│   └── instances_train2017.json
└── train2017/
    ├── 000000000001.jpg
    └── 000000000002.jpg</pre>
            <div class="form-group">
              <label>instances JSON 绝对路径</label>
              <Input v-model:value="cocoJsonPath" placeholder="/path/to/instances_default.json" />
            </div>
            <div class="form-group">
              <label>图片根目录（可选）</label>
              <Input v-model:value="cocoImagesRoot" placeholder="/path/to/images" />
            </div>
          </div>
          <div class="upload-actions">
            <Button type="primary" :loading="loading" @click="importCocoPath">
              <Icon icon="ant-design:database-outlined" />
              导入 COCO
            </Button>
          </div>
        </TabPane>

        <TabPane key="cloud" tab="云平台">
          <div class="upload-area path-area">
            <p class="cloud-desc">
              从云平台数据集服务选择数据集，下载压缩包并解析图片与标注到当前数据集。
            </p>
            <div class="form-group">
              <label>选择数据集</label>
              <Select
                v-model:value="cloudDatasetId"
                :loading="cloudLoading"
                placeholder="请选择数据集"
                style="width: 100%"
                :options="cloudOptions"
              />
            </div>
          </div>
          <div class="upload-actions">
            <Button type="primary" :loading="loading" @click="importFromCloud">
              <Icon icon="ant-design:cloud-download-outlined" />
              从云平台导入
            </Button>
          </div>
        </TabPane>
      </Tabs>
    </div>
  </BasicModal>
</template>

<script lang="ts" setup>
import { ref, watch } from 'vue';
import { BasicModal, useModal } from '@/components/Modal';
import { Icon } from '@/components/Icon';
import { Tabs, TabPane, InputNumber, Button, Input, Select } from 'ant-design-vue';
import { useMessage } from '@/hooks/web/useMessage';
import {
  importAnnotationImageFolder,
  importAnnotationImageFolderPath,
  importAnnotationYoloPath,
  importAnnotationCocoPath,
  listAnnotationCloudDatasets,
  importAnnotationFromCloud,
  extractAnnotationFrames,
  type DatasetAnnotationImportResult,
} from '@/api/device/dataset';

defineOptions({ name: 'ImportDatasetModal' });

const props = defineProps<{
  datasetId?: number;
  getContainer?: () => HTMLElement;
}>();

const { createMessage } = useMessage();
const emits = defineEmits(['success']);

const loading = ref(false);
const tabActive = ref('image');
const folderFiles = ref<File[]>([]);
const folderFileCount = ref(0);
const selectedVideo = ref<File | null>(null);
const frameInterval = ref(30);
const datasetPath = ref('');
const yoloPath = ref('');
const cocoJsonPath = ref('');
const cocoImagesRoot = ref('');
const cloudDatasetId = ref<number | undefined>();
const cloudOptions = ref<{ label: string; value: number }[]>([]);
const cloudLoading = ref(false);

const folderInputRef = ref<HTMLInputElement | null>(null);
const videoInputRef = ref<HTMLInputElement | null>(null);

const [register, { openModal, closeModal }] = useModal();

function resetState() {
  folderFiles.value = [];
  folderFileCount.value = 0;
  selectedVideo.value = null;
  datasetPath.value = '';
  yoloPath.value = '';
  cocoJsonPath.value = '';
  cocoImagesRoot.value = '';
  cloudDatasetId.value = undefined;
}

function formatImportMsg(d: DatasetAnnotationImportResult): string {
  const parts: string[] = [];
  if (d.imagesCopied != null) parts.push(`图片 ${d.imagesCopied} 张`);
  if (d.labelmeImages) parts.push(`LabelMe 标注 ${d.labelmeImages} 张`);
  if (d.cocoImages) parts.push(`COCO 标注 ${d.cocoImages} 张`);
  if (d.yoloImages) parts.push(`YOLO .txt 标注 ${d.yoloImages} 张`);
  let msg = parts.length ? parts.join('，') : '导入完成';
  if (d.hint) msg += `（${d.hint}）`;
  return msg;
}

async function loadCloudDatasets() {
  cloudLoading.value = true;
  try {
    const res = await listAnnotationCloudDatasets();
    const list = Array.isArray(res) ? res : (res?.data ?? res?.list ?? []);
    cloudOptions.value = (Array.isArray(list) ? list : []).map((d: any) => ({
      value: d.id,
      label: d.version ? `${d.name} [${d.version}] (ID:${d.id})` : `${d.name} (ID:${d.id})`,
    }));
  } catch {
    cloudOptions.value = [];
  } finally {
    cloudLoading.value = false;
  }
}

watch(tabActive, (key) => {
  if (key === 'cloud') loadCloudDatasets();
});

const openModalWithReset = () => {
  resetState();
  tabActive.value = 'image';
  openModal();
};

defineExpose({ openModal: openModalWithReset, closeModal });

function requireDatasetId(): number | null {
  if (!props.datasetId) {
    createMessage.warning('请先选择数据集');
    return null;
  }
  return props.datasetId;
}

function collectImageFiles(fileList: FileList | File[]) {
  const files = Array.from(fileList).filter((f) => f.type.startsWith('image/') || /\.(jpe?g|png|bmp|gif)$/i.test(f.name));
  folderFiles.value = files;
  folderFileCount.value = files.length;
}

function triggerFolderSelect() {
  folderInputRef.value?.click();
}

function onFolderSelect(e: Event) {
  const input = e.target as HTMLInputElement;
  if (input.files) collectImageFiles(input.files);
}

function onImageDrop(e: DragEvent) {
  if (e.dataTransfer?.files) collectImageFiles(e.dataTransfer.files);
}

function triggerVideoSelect() {
  videoInputRef.value?.click();
}

function onVideoSelect(e: Event) {
  const input = e.target as HTMLInputElement;
  if (input.files?.[0]) selectedVideo.value = input.files[0];
}

function onVideoDrop(e: DragEvent) {
  const f = e.dataTransfer?.files?.[0];
  if (f?.type.startsWith('video/')) selectedVideo.value = f;
}

async function uploadImages() {
  const dsId = requireDatasetId();
  if (!dsId || folderFiles.value.length === 0) return;
  loading.value = true;
  try {
    const formData = new FormData();
    folderFiles.value.forEach((f) => formData.append('files', f, f.webkitRelativePath || f.name));
    const res = await importAnnotationImageFolder(dsId, formData);
    const data = res?.data ?? res;
    createMessage.success(formatImportMsg(data) || '上传成功');
    closeModal();
    emits('success');
  } catch (e: any) {
    createMessage.error(e?.message || '上传失败');
  } finally {
    loading.value = false;
  }
}

async function extractFrames() {
  const dsId = requireDatasetId();
  if (!dsId || !selectedVideo.value) return;
  loading.value = true;
  try {
    const formData = new FormData();
    formData.append('file', selectedVideo.value);
    formData.append('frame_interval', String(frameInterval.value));
    const res = await extractAnnotationFrames(dsId, formData);
    const data = res?.data ?? res;
    createMessage.success(formatImportMsg(data) || `抽帧完成，共 ${data?.imagesCopied ?? 0} 帧`);
    closeModal();
    emits('success');
  } catch (e: any) {
    createMessage.error(e?.message || '抽帧失败');
  } finally {
    loading.value = false;
  }
}

async function importImageFolderPath() {
  const dsId = requireDatasetId();
  const path = datasetPath.value.trim();
  if (!dsId || !path) {
    createMessage.warning('请填写数据集目录的绝对路径');
    return;
  }
  loading.value = true;
  try {
    const res = await importAnnotationImageFolderPath(dsId, path);
    const data = res?.data ?? res;
    createMessage.success('ImageFolder 导入成功：' + formatImportMsg(data));
    closeModal();
    emits('success');
  } catch (e: any) {
    createMessage.error(e?.message || '导入失败');
  } finally {
    loading.value = false;
  }
}

async function importYoloPath() {
  const dsId = requireDatasetId();
  const path = yoloPath.value.trim();
  if (!dsId || !path) {
    createMessage.warning('请填写 YOLO 数据集根目录的绝对路径');
    return;
  }
  loading.value = true;
  try {
    const res = await importAnnotationYoloPath(dsId, path);
    const data = res?.data ?? res;
    createMessage.success('YOLO 导入成功：' + formatImportMsg(data));
    closeModal();
    emits('success');
  } catch (e: any) {
    createMessage.error(e?.message || '导入失败');
  } finally {
    loading.value = false;
  }
}

async function importCocoPath() {
  const dsId = requireDatasetId();
  const cocoJson = cocoJsonPath.value.trim();
  if (!dsId || !cocoJson) {
    createMessage.warning('请填写 COCO instances JSON 的绝对路径');
    return;
  }
  loading.value = true;
  try {
    const res = await importAnnotationCocoPath(dsId, {
      cocoJson,
      imagesRoot: cocoImagesRoot.value.trim() || undefined,
    });
    const data = res?.data ?? res;
    createMessage.success('COCO 导入成功：' + formatImportMsg(data));
    closeModal();
    emits('success');
  } catch (e: any) {
    createMessage.error(e?.message || '导入失败');
  } finally {
    loading.value = false;
  }
}

async function importFromCloud() {
  const dsId = requireDatasetId();
  if (!dsId || cloudDatasetId.value == null) {
    createMessage.warning('请选择云平台数据集');
    return;
  }
  loading.value = true;
  try {
    const res = await importAnnotationFromCloud(dsId, cloudDatasetId.value);
    const data = res?.data ?? res;
    createMessage.success(formatImportMsg(data) || '云平台导入成功');
    closeModal();
    emits('success');
  } catch (e: any) {
    createMessage.error(e?.message || '导入失败');
  } finally {
    loading.value = false;
  }
}

function handleCancel() {
  closeModal();
  resetState();
}
</script>

<style lang="less" scoped>
.modal-title-with-icon {
  display: flex;
  align-items: center;
  gap: 8px;
  .title-icon { font-size: 18px; }
}

.dataset-modal-content {
  padding: 8px 4px 16px;
}

.dataset-tabs {
  :deep(.ant-tabs-nav) { margin-bottom: 16px; }
}

.upload-area {
  border: 2px dashed #d9d9d9;
  border-radius: 8px;
  padding: 20px;
  text-align: center;
  background: #fafafa;
  margin-bottom: 16px;

  &.path-area { text-align: left; }

  p { color: #666; margin: 12px 0; font-size: 14px; }
  .cloud-desc { color: #555; font-size: 13px; text-align: left; }
}

.upload-area-icon {
  font-size: 48px;
  color: #999;
  display: block;
  margin: 8px auto;
}

.dataset-tree-example {
  text-align: left;
  font-size: 12px;
  line-height: 1.5;
  background: #f0f0f0;
  padding: 12px;
  border-radius: 6px;
  margin-bottom: 16px;
  color: #444;
  overflow-x: auto;
}

.upload-actions {
  text-align: center;
  margin-top: 8px;
}

.selected-hint {
  margin-top: 10px;
  font-size: 13px;
  color: #666;
}

.form-group {
  margin-top: 16px;
  text-align: left;

  label {
    display: block;
    font-weight: 500;
    margin-bottom: 6px;
    color: #333;
  }

  small {
    display: block;
    margin-top: 6px;
    color: #999;
    font-size: 12px;
  }

  &.inline-form {
    display: flex;
    flex-wrap: wrap;
    align-items: center;
    gap: 8px;
    label { margin-bottom: 0; min-width: 100px; }
    small { width: 100%; margin-left: 0; }
  }
}
</style>
