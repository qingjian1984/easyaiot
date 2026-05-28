<template>
  <ConfigProvider :get-popup-container="getModalContainer">
  <div ref="container" class="annotation-container">
    <!-- 顶栏：进度与操作 -->
    <div class="top-toolbar">
      <div class="progress-info">
        <Icon icon="ant-design:picture-outlined"/>
        <span>进度 <strong>{{ globalImageIndex + 1 }}</strong> / {{ totalImages }}</span>
        <span v-if="totalImages > 0" class="progress-percent">
          （已完成 {{ completedCount }}，{{ progressPercent }}%）
        </span>
        <span v-if="batchTaskRunning" class="batch-task-hint">
          <Icon icon="ant-design:loading-outlined" spin/>
          批量AI标注进行中…
        </span>
      </div>
      <div class="top-actions">
        <div class="tool-group">
          <button
            v-for="tool in tools"
            :key="tool.id"
            type="button"
            class="tool-button"
            :class="{ active: activeTool === tool.id }"
            @click="setActiveTool(tool.id)"
          >
            <Icon :icon="tool.icon"/>
            <span>{{ tool.name }} ({{ tool.shortcut }})</span>
          </button>
        </div>
        <span class="top-actions-divider"/>
        <button type="button" class="action-btn import-btn" @click="openImportModal">
          <Icon icon="ant-design:upload-outlined"/>
          添加数据集
        </button>
        <button type="button" class="action-btn" @click="openExportModal">
          <Icon icon="ant-design:download-outlined"/>
          导出数据集
        </button>
        <span class="top-actions-divider"/>
        <button type="button" class="action-btn" :disabled="saving" @click="saveCurrentAnnotations">
          <Icon icon="ant-design:save-outlined"/>
          保存标注
        </button>
        <button type="button" class="action-btn ai-batch-btn" @click="openAiBatchModal">
          <Icon icon="ant-design:robot-outlined"/>
          批量AI标注
        </button>
      </div>
    </div>

    <!-- 主内容区 -->
    <div class="main-content">
      <!-- 左侧图片列表（虚拟滚动，单次最多加载 1000 条） -->
      <div ref="imagePanelRef" class="image-panel">
        <div class="image-list-header">
          <div class="image-list-stats">
            <span class="stat-done">已完成 {{ completedCount }}</span>
            <span class="stat-sep">/</span>
            <span class="stat-total">{{ totalImages }}</span>
            <span v-if="listFilterStatus !== 'all'" class="stat-filtered">· {{ displayImages.length }}</span>
          </div>
          <Select
            v-model:value="listFilterStatus"
            size="small"
            class="filter-select"
            :options="listFilterOptions"
            popup-class-name="image-panel-dropdown"
            :get-popup-container="getSidePanelPopupContainer"
          />
        </div>
        <div
          ref="listScrollRef"
          class="image-list-scroll"
          @scroll="onListScroll"
        >
          <div class="image-list-phantom" :style="{ height: `${listPhantomHeight}px` }"/>
          <ul
            class="image-list"
            :style="{ transform: `translateY(${listOffsetY}px)` }"
          >
            <li
              v-for="{ img, index } in visibleListImages"
              :key="img.id"
              class="image-list-item"
              :class="{
                active: currentImage.id === img.id,
                annotated: hasAnnotations(img),
                completed: img.completed === 1
              }"
              @click="selectImageInList(img)"
            >
              <span class="image-index">{{ index + 1 }}</span>
              <span class="image-name" :title="img.name">{{ img.name }}</span>
              <span class="image-status-badge" :class="getImageStatusClass(img)">
                {{ getImageStatusText(img) }}
              </span>
            </li>
          </ul>
          <div v-if="displayImages.length === 0" class="image-list-empty">
            暂无图片
          </div>
        </div>
        <Pagination
          v-if="totalPages > 1"
          v-model:current="listChunkPage"
          class="image-list-pagination"
          size="small"
          simple
          :total="totalImages"
          :page-size="LIST_CHUNK_SIZE"
          :show-size-changer="false"
          @change="onListChunkPageChange"
        />
      </div>

      <!-- 画布区域 -->
      <div class="canvas-area">
        <div class="image-position-indicator">
          <div class="position-text">
            当前图片: <span class="current-index">{{ globalImageIndex + 1 }}</span> /
            <span class="total-count">{{ totalImages }}</span> <!-- 修改为 totalImages -->
          </div>
        </div>
        <div class="canvas-wrapper">
          <canvas
            ref="canvas"
            class="annotation-canvas"
            @mousedown="handleMouseDown"
            @mousemove="handleMouseMove"
            @mouseup="handleMouseUp"
            @dblclick="handleDoubleClick"
            @wheel.prevent="handleCanvasWheel"
          ></canvas>
        </div>

        <div class="status-indicator">
          <div class="status-header">
            <div class="completion-status" :class="{ completed: currentImage.completed === 1 }">
              {{ currentImage.completed === 1 ? '✅ 已完成标注' : '⏳ 标注中' }}
            </div>
            <div v-if="currentImage.completed === 1" class="modification-info">
              <div>修改次数: {{ currentImage.modificationCount || 0 }}</div>
              <div>最后修改: {{ formatDateTime(currentImage.lastModified) }}</div>
            </div>
          </div>
          <div class="annotation-count">
            <div class="status-dot"></div>
            <span>{{ statusText }}</span>
            <div v-if="!isSaved" class="unsaved-indicator">(未保存)</div>
          </div>
        </div>

        <div class="fullscreen-control" @click="toggleFullscreen">
          <Icon :icon="isFullscreen ? 'fa:compress' : 'fa:expand'"/>
          <span>{{ isFullscreen ? '退出全屏' : '全屏标注' }}</span>
        </div>

        <div class="shortcut-hint">
          <div v-for="hint in shortcutHints" :key="hint.key" class="hint-item">
            <span class="key">{{ hint.key }}</span>
            <span class="text">{{ hint.text }}</span>
          </div>
        </div>
      </div>

      <!-- 右侧标签栏 -->
      <div class="label-panel">
        <div class="panel-header">
          <span>标签管理</span>
        </div>

        <div class="label-list">
          <div
            v-for="(label, index) in labels"
            :key="label.id"
            class="label-item"
            :class="{ active: currentLabelIndex === index }"
            @click="setCurrentLabel(index)"
          >
            <div class="color-badge" :style="{ backgroundColor: label.color }"></div>
            <div class="label-name">{{ label.name }}</div>
            <div class="label-shortcut">{{ label.shortcut }}</div>
          </div>
        </div>

        <div class="object-layer-section">
          <div class="panel-header">
            <i class="fas fa-layer-group"></i>
            <span>对象图层 ({{ annotations.length }})</span>
          </div>
          <div class="object-list">
            <div
              v-for="(anno, index) in annotations"
              :key="anno.id"
              class="object-item"
              :class="{ selected: selectedAnnotationId === anno.id }"
              @click="selectAnnotation(anno.id)"
            >
              <div class="object-color" :style="{ backgroundColor: anno.color }"></div>
              <div class="object-name">{{ getLabelName(anno.label) }} #{{ index + 1 }}</div>
              <div class="object-actions">
                <button class="delete-btn" @click.stop="deleteAnnotation(anno.id)">
                  <Icon icon="fa:trash"/>
                </button>
              </div>
            </div>
          </div>
        </div>
      </div>
    </div>

    <AILabelModal
      ref="aiLabelModalRef"
      :dataset-id="datasetId"
      :get-container="getModalContainer"
      @success="onBatchAiSuccess"
    />
    <ImportDatasetModal
      ref="importModalRef"
      :dataset-id="datasetId"
      :get-container="getModalContainer"
      @success="onImportSuccess"
    />
    <ExportDatasetModal
      ref="exportModalRef"
      :dataset-id="datasetId"
      :dataset-labels="labels"
      :get-container="getModalContainer"
    />
  </div>
  </ConfigProvider>
</template>

<script setup lang="ts">
import {computed, nextTick, onMounted, onUnmounted, ref, watch} from 'vue';
import {ConfigProvider, Pagination, Select} from 'ant-design-vue';
import {Icon} from '@/components/Icon';
import {useMessage} from "@/hooks/web/useMessage";
import {useRoute} from "vue-router";
import {getDatasetImagePage, getDatasetTagPage, updateDatasetImage} from "@/api/device/dataset";
import { getAutoLabelTask } from '@/api/device/auto-label';
import AILabelModal from '@/views/dataset/components/AutoLabel/AILabelModal/index.vue';
import ImportDatasetModal from '@/views/dataset/components/AutoLabel/ImportDatasetModal/index.vue';
import ExportDatasetModal from '@/views/dataset/components/AutoLabel/ExportDatasetModal/index.vue';

defineOptions({name: 'AnnotationTool'});

const {createMessage, createConfirm} = useMessage();
const route = useRoute();
const datasetId = computed(() => Number(route.params['id']));
const aiLabelModalRef = ref<InstanceType<typeof AILabelModal> | null>(null);
const importModalRef = ref<InstanceType<typeof ImportDatasetModal> | null>(null);
const exportModalRef = ref<InstanceType<typeof ExportDatasetModal> | null>(null);

// 标注数据
const annotations = ref<Annotation[]>([]);
const selectedAnnotationId = ref<number | null>(null);
const annotationCount = computed<number>(() => annotations.value.length);
const statusText = computed<string>(() => `已标注 ${annotationCount.value} 个对象`);
const isSaved = ref(true);

/** 左侧列表每个分块展示的条数（与后端 PageParam.PAGE_SIZE_MAX 一致） */
const LIST_CHUNK_SIZE = 1000;
const LIST_ITEM_HEIGHT = 36;
const LIST_OVERSCAN = 10;

type ListFilterStatus = 'all' | 'pending' | 'annotated' | 'completed';

const listFilterOptions: { label: string; value: ListFilterStatus }[] = [
  {label: '全部', value: 'all'},
  {label: '待完成', value: 'pending'},
  {label: '有标注', value: 'annotated'},
  {label: '已完成', value: 'completed'},
];

const listFilterStatus = ref<ListFilterStatus>('all');
const imagePanelRef = ref<HTMLElement | null>(null);
const listLoading = ref(false);
const listChunkPage = ref(1);
const totalImages = ref(0);
const completedCount = ref(0);
const listScrollRef = ref<HTMLElement | null>(null);
const listScrollTop = ref(0);

// 添加保存状态锁
const saving = ref(false);

const batchTaskRunning = ref(false);
let batchTaskPollTimer: ReturnType<typeof setInterval> | null = null;

const totalPages = computed(() => Math.max(1, Math.ceil(totalImages.value / LIST_CHUNK_SIZE)));

const displayImages = computed(() => images.value);

const globalImageIndex = computed(() => {
  return (listChunkPage.value - 1) * LIST_CHUNK_SIZE + currentImageIndex.value;
});

const progressPercent = computed(() => {
  if (totalImages.value <= 0) return 0;
  return Math.round((completedCount.value / totalImages.value) * 100);
});

const listPhantomHeight = computed(() => displayImages.value.length * LIST_ITEM_HEIGHT);

const listOffsetY = computed(() => visibleListRange.value.start * LIST_ITEM_HEIGHT);

const visibleListRange = computed(() => {
  const count = displayImages.value.length;
  if (count === 0) return {start: 0, end: 0};
  const container = listScrollRef.value;
  const viewHeight = container?.clientHeight ?? 480;
  const start = Math.max(0, Math.floor(listScrollTop.value / LIST_ITEM_HEIGHT) - LIST_OVERSCAN);
  const visibleCount = Math.ceil(viewHeight / LIST_ITEM_HEIGHT) + LIST_OVERSCAN * 2;
  const end = Math.min(count, start + visibleCount);
  return {start, end};
});

const visibleListImages = computed(() => {
  const {start, end} = visibleListRange.value;
  const base = (listChunkPage.value - 1) * LIST_CHUNK_SIZE;
  return displayImages.value.slice(start, end).map((img, i) => ({
    img,
    index: base + start + i,
  }));
});

const onListScroll = (e: Event) => {
  listScrollTop.value = (e.target as HTMLElement).scrollTop;
};

const scrollListToActive = () => {
  nextTick(() => {
    const idx = images.value.findIndex((i) => i.id === currentImage.value.id);
    if (idx < 0 || !listScrollRef.value) return;
    const targetTop = idx * LIST_ITEM_HEIGHT;
    const el = listScrollRef.value;
    const viewBottom = el.scrollTop + el.clientHeight;
    if (targetTop < el.scrollTop || targetTop + LIST_ITEM_HEIGHT > viewBottom) {
      el.scrollTop = Math.max(0, targetTop - el.clientHeight / 2 + LIST_ITEM_HEIGHT / 2);
      listScrollTop.value = el.scrollTop;
    }
  });
};

const buildListQueryParams = (pageNo: number) => {
  const params: Record<string, unknown> = {
    datasetId: route.params['id'],
    pageNo,
    pageSize: LIST_CHUNK_SIZE,
  };
  if (listFilterStatus.value === 'pending') {
    params.completed = 0;
  } else if (listFilterStatus.value === 'completed') {
    params.completed = 1;
  }
  return params;
};

const getSidePanelPopupContainer = (): HTMLElement => {
  return imagePanelRef.value || document.body;
};

const onListChunkPageChange = async (page: number) => {
  const prevPage = listChunkPage.value;
  if (page === prevPage) return;
  const ok = await confirmDiscardUnsaved();
  if (!ok) {
    listChunkPage.value = prevPage;
    return;
  }
  currentImageIndex.value = 0;
  await fetchImages(page);
};

// 快捷键提示
const shortcutHints = ref<{ key: string, text: string }[]>([
  {key: 'Del', text: '删除'},
  {key: 'Ctrl+S', text: '保存'},
  {key: 'Space', text: '下一张'},
  {key: '←→', text: '切图'},
  {key: '1-9', text: '标签'},
  {key: '滚轮', text: '缩放'},
  {key: 'Ctrl+Z', text: '撤销'},
]);

// 操作历史记录
const historyStack = ref<Annotation[][]>([]);

// Canvas 状态
const canvas = ref<HTMLCanvasElement | null>(null);
const ctx = ref<CanvasRenderingContext2D | null>(null);
const isDrawing = ref<boolean>(false);
const startX = ref<number>(0);
const startY = ref<number>(0);
const currentPoints = ref<Point[]>([]);
const zoomLevel = ref<number>(1.0);
const offsetX = ref<number>(0);
const offsetY = ref<number>(0);

// 全屏状态
const isFullscreen = ref(false);
const container = ref<HTMLElement | null>(null);

// 定义 TypeScript 类型
const ToolType = {
  SELECT: 'select',
  RECTANGLE: 'rectangle',
  POLYGON: 'polygon'
};

const AnnotationType = {
  RECTANGLE: 'rectangle',
  POLYGON: 'polygon'
};

// 工具类型
interface Tool {
  id: string;
  name: string;
  icon: string;
  shortcut: string;
}

// 标签类型
interface Label {
  id: number;
  name: string;
  color: string;
  shortcut: string; // 确保定义为string类型
}

// 点类型
interface Point {
  x: number;
  y: number;
}

// 标注数据格式
interface Annotation {
  id: number;
  type: string;
  label: string; // 改为存储标签的 shortcut
  color: string;
  points: Point[];
}

// 图片类型
interface Image {
  id: number;
  name: string;
  path: string;
  annotations: Annotation[] | string;
  completed: 0 | 1;
  modificationCount: number;
  lastModified: Date | null;
}

// 保存标注请求类型
interface SaveAnnotationRequest {
  id: number;
  name: string;
  annotations: string;
  completed: 0 | 1;
  modificationCount: number;
  lastModified: Date | null;
}

// 工具状态
const activeTool = ref<string>(ToolType.SELECT);
const tools = ref<Tool[]>([
  {id: ToolType.SELECT, name: '选择', icon: 'mage:mouse-pointer', shortcut: 'V'},
  {id: ToolType.RECTANGLE, name: '矩形', icon: 'uil:vector-square', shortcut: 'R'},
  {id: ToolType.POLYGON, name: '多边形', icon: 'fa-solid:draw-polygon', shortcut: 'P'}
]);

// 图片显示尺寸
const imageDisplaySize = ref({
  x: 0,
  y: 0,
  width: 0,
  height: 0
});

// 标签状态
const currentLabelIndex = ref<number>(0);
const labels = ref<Label[]>([]);

const currentLabel = computed<Label>(() => labels.value[currentLabelIndex.value]);

// 图片数据
const images = ref<Image[]>([]);

// 图片标注状态存储
const imageAnnotations = ref<{ [key: number]: Annotation[] }>({});

const currentImageIndex = ref<number>(0);
const currentImage = computed<Image>(() => images.value[currentImageIndex.value] || {
  id: 0,
  name: '',
  path: '',
  annotations: [],
  completed: 0,
  modificationCount: 0,
  lastModified: null
});

// 图片对象引用
const currentImageObj = ref<HTMLImageElement | null>(null);
const imageLoaded = ref(false);

// 修改图片加载逻辑
const loadImage = (src: string) => {
  imageLoaded.value = false;
  const img = new Image();
  img.crossOrigin = "Anonymous";
  img.onload = () => {
    currentImageObj.value = img;
    imageLoaded.value = true;

    // 计算初始缩放比例和位置
    if (canvas.value) {
      const canvasWidth = canvas.value.width;
      const canvasHeight = canvas.value.height;

      // 确保图片不超过画布
      const scaleX = canvasWidth / img.width;
      const scaleY = canvasHeight / img.height;
      const initScale = Math.min(scaleX, scaleY);

      // 重置缩放和偏移
      zoomLevel.value = initScale;
      offsetX.value = 0;
      offsetY.value = 0;
    }

    draw();
  };
  img.src = src;
};

const getLabelName = (shortcut: string): string => {
  let label = labels.value.find(l => String(l.shortcut) === shortcut);
  if (label == null || label == undefined) {
    label = labels.value.find(l => String(l.name) === shortcut);
  }
  return label ? label.name : '未知标签';
};

watch(listFilterStatus, () => {
  listChunkPage.value = 1;
  fetchImages(1);
});

// 监听当前图片变化
watch(currentImageIndex, () => {
  scrollListToActive();
});

watch(currentImage, (newImage) => {
  if (newImage.path) {
    loadImage(newImage.path);

    // 加载当前图片的标注
    if (typeof newImage.annotations === 'string') {
      try {
        annotations.value = JSON.parse(newImage.annotations);
      } catch (e) {
        createMessage.error('标注解析失败');
        annotations.value = [];
      }
    } else {
      annotations.value = [...newImage.annotations];
    }
    isSaved.value = true;
  }
}, {immediate: true});

// 检查图片是否有标注
const hasAnnotations = (image: Image) => {
  if (typeof image.annotations === 'string') {
    try {
      const parsed = JSON.parse(image.annotations);
      return Array.isArray(parsed) && parsed.length > 0;
    } catch (e) {
      return false;
    }
  }
  return image.annotations && image.annotations.length > 0;
};

// 设置活动工具
const setActiveTool = (toolId: string): void => {
  activeTool.value = toolId;
  selectedAnnotationId.value = null;
  currentPoints.value = [];
};

// 设置当前标签
const setCurrentLabel = (index: number): void => {
  currentLabelIndex.value = index;
  console.log(`当前标签已设置为: ${labels.value[index].name} (shortcut: ${labels.value[index].shortcut})`);
};

const confirmDiscardUnsaved = (): Promise<boolean> => {
  if (isSaved.value) return Promise.resolve(true);
  return new Promise((resolve) => {
    createConfirm({
      iconType: 'warning',
      title: '未保存的标注',
      content: '当前图片有未保存的修改，切换后将丢失。是否继续？',
      onOk: () => resolve(true),
      onCancel: () => resolve(false),
    });
  });
};

const selectImageInList = async (img: Image): Promise<void> => {
  if (currentImage.value.id === img.id) return;
  const ok = await confirmDiscardUnsaved();
  if (!ok) return;
  const idx = images.value.findIndex((i) => i.id === img.id);
  if (idx >= 0) {
    currentImageIndex.value = idx;
    scrollListToActive();
  }
};

const getImageStatusText = (img: Image): string => {
  if (img.completed === 1) return '已完成';
  if (hasAnnotations(img)) return '已标注';
  return '待标注';
};

const getImageStatusClass = (img: Image): string => {
  if (img.completed === 1) return 'status-completed';
  if (hasAnnotations(img)) return 'status-annotated';
  return 'status-pending';
};

// 选择图片（全局索引，用于键盘切换）
const selectImage = async (index: number): Promise<void> => {
  if (index < 0 || index >= totalImages.value) return;
  const ok = await confirmDiscardUnsaved();
  if (!ok) return;

  const targetPage = Math.floor(index / LIST_CHUNK_SIZE) + 1;
  const targetIndex = index % LIST_CHUNK_SIZE;

  if (targetPage !== listChunkPage.value) {
    listChunkPage.value = targetPage;
    await fetchImages(targetPage);
  }
  currentImageIndex.value = Math.min(targetIndex, Math.max(0, images.value.length - 1));
  scrollListToActive();
};

// 图片导航
const nextImage = async (): Promise<void> => {
  const newIndex = globalImageIndex.value + 1;
  if (newIndex < totalImages.value) {
    await selectImage(newIndex);
  }
};

const prevImage = async (): Promise<void> => {
  const newIndex = globalImageIndex.value - 1;
  if (newIndex >= 0) {
    await selectImage(newIndex);
  }
};


// 更新图片状态
const updateImageStatus = (modified: boolean = true) => {
  if (modified) {
    currentImage.value.modificationCount += 1;
    currentImage.value.lastModified = new Date();
    currentImage.value.completed = 0;
    isSaved.value = false;
  }
};

// 保存当前状态到历史记录
const saveToHistory = () => {
  historyStack.value.push(JSON.parse(JSON.stringify(annotations.value)));
  if (historyStack.value.length > 50) {
    historyStack.value.shift();
  }
  isSaved.value = false;
};

// 撤销操作
const undo = () => {
  if (historyStack.value.length > 0) {
    const prevState = historyStack.value.pop();
    if (prevState) {
      annotations.value = JSON.parse(JSON.stringify(prevState));
      draw();
      updateImageStatus();
    }
  }
};

// 从后端分页获取标签数据
const fetchLabels = async (): Promise<void> => {
  try {
    const pageSize = 100;
    let allLabels: Label[] = [];

    const res = await getDatasetTagPage({
      datasetId: route.params['id'],
      pageNo: 1,
      pageSize: pageSize
    });

    if (res?.list) {
      // 确保shortcut转换为字符串
      const pageLabels = res.list.map((tag: any) => ({
        id: tag.id,
        name: tag.name,
        color: tag.color,
        shortcut: String(tag.shortcut) // 关键转换
      }));
      allLabels = [...allLabels, ...pageLabels];
    }

    if (allLabels.length > 0) {
      labels.value = allLabels;
    } else {
      throw new Error("未获取到标签数据");
    }
  } catch (error) {
    // 默认标签也确保shortcut是字符串
    labels.value = [
      {id: 1, name: '人物', color: '#FF5252', shortcut: '1'},
      {id: 2, name: '车辆', color: '#4CAF50', shortcut: '2'},
      {id: 3, name: '动物', color: '#FFC107', shortcut: '3'},
    ];
    currentLabelIndex.value = 0;
  }
};

const mapImageRow = (img: any): Image => {
  let rowAnnotations: Annotation[] | string = [];
  if (img.annotations) {
    try {
      rowAnnotations = typeof img.annotations === 'string'
        ? JSON.parse(img.annotations)
        : img.annotations;
    } catch {
      rowAnnotations = [];
    }
  }
  return {
    id: img.id,
    name: img.name,
    path: img.path,
    annotations: rowAnnotations,
    completed: img.completed || 0,
    modificationCount: img.modificationCount || 0,
    lastModified: img.lastModified ? new Date(img.lastModified) : null,
  };
};

const fetchCompletedCount = async (): Promise<void> => {
  try {
    const res = await getDatasetImagePage({
      datasetId: route.params['id'],
      pageNo: 1,
      pageSize: 1,
      completed: 1,
    });
    completedCount.value = res?.total ?? 0;
  } catch {
    completedCount.value = images.value.filter((i) => i.completed === 1).length;
  }
};

/** 加载左侧列表的一个分块（单次最多 LIST_CHUNK_SIZE 条） */
const fetchImages = async (chunkPage: number = 1): Promise<void> => {
  listLoading.value = true;
  try {
    const res = await getDatasetImagePage(buildListQueryParams(chunkPage));

    if (res?.list) {
      totalImages.value = res.total ?? res.list.length;

      let mapped = res.list.map(mapImageRow);
      if (listFilterStatus.value === 'annotated') {
        mapped = mapped.filter((img) => hasAnnotations(img));
      }

      images.value = mapped;
      listChunkPage.value = chunkPage;

      if (images.value.length > 0 && currentImageIndex.value >= images.value.length) {
        currentImageIndex.value = 0;
      }
      await fetchCompletedCount();
      scrollListToActive();
    }
  } catch (error) {
    createMessage.error('获取图片失败:' + error);
    images.value = [];
    totalImages.value = 0;
  } finally {
    listLoading.value = false;
  }
};

// 保存标注到后端（简化版）
const saveAnnotationsToDB = async (requestData: SaveAnnotationRequest): Promise<void> => {
  try {
    requestData['datasetId'] = route.params['id'];
    await updateDatasetImage(requestData);
  } catch (error) {
    createMessage.error('保存到数据库失败:' + error);
    throw error; // 重新抛出错误
  }
};

// 格式化日期时间
const formatDateTime = (date: Date | null): string => {
  if (!date) return '从未修改';

  return new Intl.DateTimeFormat('zh-CN', {
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
    second: '2-digit'
  }).format(date);
};

// 保存当前标注
const saveCurrentAnnotations = async (): Promise<void> => {
  if (saving.value) return;
  saving.value = true;

  if (annotations.value.length === 0) {
    createMessage.warning('请至少标注一个对象');
    saving.value = false;
    return;
  }

  saving.value = true; // 加锁

  try {
    const updatedStatus = {
      completed: 1 as 0 | 1,
      modificationCount: currentImage.value.modificationCount + 1,
      lastModified: new Date()
    };

    const requestData: SaveAnnotationRequest = {
      id: currentImage.value.id,
      name: currentImage.value.name,
      annotations: JSON.stringify(annotations.value),
      ...updatedStatus
    };

    // 使用单一await处理保存操作
    await saveAnnotationsToDB(requestData);

    // 更新当前图片状态
    const currentId = currentImage.value.id;
    const imageIndex = images.value.findIndex(img => img.id === currentId);
    if (imageIndex !== -1) {
      images.value[imageIndex] = {
        ...images.value[imageIndex],
        ...updatedStatus,
        annotations: requestData.annotations
      };
    }

    // 更新标注缓存
    try {
      imageAnnotations.value[currentId] = JSON.parse(requestData.annotations);
    } catch (e) {
      imageAnnotations.value[currentId] = [];
    }

    // 仅显示一次成功提示
    createMessage.success('标注保存成功');
    isSaved.value = true;
    await fetchCompletedCount();
  } catch (error) {
    createMessage.error('保存失败:' + error);
  } finally {
    saving.value = false; // 解锁
  }
};

const handleCanvasWheel = (e: WheelEvent): void => {
  if (!imageLoaded.value) return;
  const delta = e.deltaY > 0 ? -0.08 : 0.08;
  zoomLevel.value = Math.min(5, Math.max(0.05, zoomLevel.value + delta * zoomLevel.value));
  draw();
};

// 初始化画布
const initCanvas = (): void => {
  if (!canvas.value) return;

  ctx.value = canvas.value.getContext('2d');
  resizeCanvas();
  draw();
};

// 调整画布大小 - 优化版
const resizeCanvas = (): void => {
  if (!canvas.value) return;

  const container = canvas.value.parentElement;
  if (!container) return;

  // 保存当前状态
  const wasDrawing = isDrawing.value;
  const hadPoints = [...currentPoints.value];

  // 暂停绘制状态
  isDrawing.value = false;
  currentPoints.value = [];

  // 更新画布尺寸
  canvas.value.width = container.clientWidth;
  canvas.value.height = container.clientHeight;

  // 恢复状态
  requestAnimationFrame(() => {
    isDrawing.value = wasDrawing;
    currentPoints.value = hadPoints;
    draw();
  });
};

// 添加防抖处理 - 优化性能
let resizeTimeout: number | null = null;
const handleResize = () => {
  if (resizeTimeout) clearTimeout(resizeTimeout);
  resizeTimeout = setTimeout(() => {
    resizeCanvas();
  }, 100) as unknown as number;
};

// 绘制网格背景
const drawGridBackground = (): void => {
  if (!ctx.value || !canvas.value) return;

  ctx.value.fillStyle = '#2d3748';
  ctx.value.fillRect(0, 0, canvas.value.width, canvas.value.height);

  ctx.value.strokeStyle = '#3c4757';
  ctx.value.lineWidth = 1;

  for (let x = 0; x < canvas.value.width; x += 25) {
    ctx.value.beginPath();
    ctx.value.moveTo(x, 0);
    ctx.value.lineTo(x, canvas.value.height);
    ctx.value.stroke();
  }

  for (let y = 0; y < canvas.value.height; y += 25) {
    ctx.value.beginPath();
    ctx.value.moveTo(0, y);
    ctx.value.lineTo(canvas.value.width, y);
    ctx.value.stroke();
  }

  ctx.value.fillStyle = '#4cc9f0';
  ctx.value.beginPath();
  ctx.value.arc(canvas.value.width / 2, canvas.value.height / 2, 5, 0, Math.PI * 2);
  ctx.value.fill();
};

// 修改绘制逻辑
const draw = (): void => {
  if (!ctx.value || !canvas.value) return;

  // 清空画布
  ctx.value.clearRect(0, 0, canvas.value.width, canvas.value.height);

  // 绘制网格背景
  drawGridBackground();

  if (currentImageObj.value && imageLoaded.value) {
    const img = currentImageObj.value;

    // 计算缩放后的尺寸
    const scaledWidth = img.width * zoomLevel.value;
    const scaledHeight = img.height * zoomLevel.value;

    // 计算居中位置
    const x = (canvas.value.width - scaledWidth) / 2 + offsetX.value;
    const y = (canvas.value.height - scaledHeight) / 2 + offsetY.value;

    // 保存显示尺寸用于坐标转换
    imageDisplaySize.value = {
      x: x,
      y: y,
      width: scaledWidth,
      height: scaledHeight
    };

    // 绘制图片
    ctx.value.drawImage(img, x, y, scaledWidth, scaledHeight);
  }

  // 绘制标注
  annotations.value.forEach(annotation => {
    drawAnnotation(annotation);
  });

  // 绘制当前标注
  if (isDrawing.value && currentPoints.value.length > 0) {
    drawCurrentAnnotation();
  }
};

// 绘制单个标注
const drawAnnotation = (annotation: Annotation): void => {
  if (!ctx.value || !imageDisplaySize.value) return;

  const {x: imgX, y: imgY, width: imgWidth, height: imgHeight} = imageDisplaySize.value;

  // 转换归一化坐标为实际坐标
  const toCanvasCoords = (point: Point) => ({
    x: imgX + point.x * imgWidth,
    y: imgY + point.y * imgHeight
  });

  ctx.value.save();
  ctx.value.strokeStyle = annotation.color;
  ctx.value.lineWidth = 2;
  ctx.value.fillStyle = annotation.color + '20';

  const isSelected = annotation.id === selectedAnnotationId.value;

  if (isSelected) {
    ctx.value.strokeStyle = '#ffffff';
    ctx.value.lineWidth = 3;
  }

  if (annotation.points.length > 0) {
    const startPoint = toCanvasCoords(annotation.points[0]);
    ctx.value.beginPath();
    ctx.value.moveTo(startPoint.x, startPoint.y);

    for (let i = 1; i < annotation.points.length; i++) {
      const point = toCanvasCoords(annotation.points[i]);
      ctx.value.lineTo(point.x, point.y);
    }

    if (annotation.type === AnnotationType.RECTANGLE ||
      annotation.type === AnnotationType.POLYGON) {
      ctx.value.closePath();
    }

    ctx.value.fill();
    ctx.value.stroke();

    if (annotation.points.length > 0) {
      drawAnnotationLabel(
        annotation,
        annotation.points[0].x,
        annotation.points[0].y
      );
    }

    drawAnnotationLabel(annotation, startPoint.x, startPoint.y);
  }

  ctx.value.restore();
};

// 绘制当前正在创建的标注
const drawCurrentAnnotation = (): void => {
  if (!ctx.value || !imageDisplaySize.value || currentPoints.value.length === 0) return;

  const {x: imgX, y: imgY, width: imgWidth, height: imgHeight} = imageDisplaySize.value;

  // 转换归一化坐标为实际canvas坐标
  const toCanvasCoords = (point: Point) => ({
    x: imgX + point.x * imgWidth,
    y: imgY + point.y * imgHeight
  });

  ctx.value.save();
  ctx.value.strokeStyle = currentLabel.value.color;
  ctx.value.lineWidth = 2;
  ctx.value.fillStyle = currentLabel.value.color + '40';

  switch (activeTool.value) {
    case ToolType.RECTANGLE:
      const rectStart = toCanvasCoords(currentPoints.value[0]);
      const rectEnd = toCanvasCoords({x: startX.value, y: startY.value});
      const width = rectEnd.x - rectStart.x;
      const height = rectEnd.y - rectStart.y;

      ctx.value.beginPath();
      ctx.value.rect(rectStart.x, rectStart.y, width, height);
      ctx.value.fill();
      ctx.value.stroke();

      drawAnnotationLabel({
        id: 0,
        type: AnnotationType.RECTANGLE,
        label: currentLabel.value.name,
        color: currentLabel.value.color,
        points: [
          {x: currentPoints.value[0].x, y: currentPoints.value[0].y},
          {x: currentPoints.value[0].x + width / imgWidth, y: currentPoints.value[0].y},
          {
            x: currentPoints.value[0].x + width / imgWidth,
            y: currentPoints.value[0].y + height / imgHeight
          },
          {x: currentPoints.value[0].x, y: currentPoints.value[0].y + height / imgHeight}
        ]
      }, rectStart.x, rectStart.y);
      break;

    case ToolType.POLYGON:
      if (currentPoints.value.length > 0) {
        ctx.value.beginPath();
        const firstPoint = toCanvasCoords(currentPoints.value[0]);
        ctx.value.moveTo(firstPoint.x, firstPoint.y);

        for (let i = 1; i < currentPoints.value.length; i++) {
          const point = toCanvasCoords(currentPoints.value[i]);
          ctx.value.lineTo(point.x, point.y);
        }

        const currentPoint = toCanvasCoords({x: startX.value, y: startY.value});
        ctx.value.lineTo(currentPoint.x, currentPoint.y);
        ctx.value.stroke();

        ctx.value.fillStyle = currentLabel.value.color;
        currentPoints.value.forEach(point => {
          const canvasPoint = toCanvasCoords(point);
          ctx.value.beginPath();
          ctx.value.arc(canvasPoint.x, canvasPoint.y, 4, 0, Math.PI * 2);
          ctx.value.fill();
        });
      }
      break;
  }

  ctx.value.restore();
};

// 绘制标注
const drawAnnotationLabel = (annotation: Annotation, x: number, y: number): void => {
  if (!ctx.value) return;

  ctx.value.save();
  ctx.value.fillStyle = annotation.color;
  ctx.value.font = '14px Inter';

  // 使用getLabelName方法获取标签名称
  const labelName = getLabelName(annotation.label);
  const textWidth = ctx.value.measureText(labelName).width;

  ctx.value.fillRect(x - 2, y - 25, textWidth + 10, 20);
  ctx.value.fillStyle = 'white';
  ctx.value.fillText(labelName, x + 3, y - 10); // 使用标签名称显示

  ctx.value.restore();
};

// 检查点是否在标注内
const isPointInAnnotation = (annotation: Annotation, x: number, y: number): boolean => {
  if (annotation.type === AnnotationType.RECTANGLE) {
    const [p1, p2, p3, p4] = annotation.points;
    const minX = Math.min(p1.x, p2.x, p3.x, p4.x);
    const maxX = Math.max(p1.x, p2.x, p3.x, p4.x);
    const minY = Math.min(p1.y, p2.y, p3.y, p4.y);
    const maxY = Math.max(p1.y, p2.y, p3.y, p4.y);

    return x >= minX && x <= maxX && y >= minY && y <= maxY;
  } else if (annotation.type === AnnotationType.POLYGON) {
    let inside = false;
    for (let i = 0, j = annotation.points.length - 1; i < annotation.points.length; j = i++) {
      const xi = annotation.points[i].x;
      const yi = annotation.points[i].y;
      const xj = annotation.points[j].x;
      const yj = annotation.points[j].y;

      const intersect = ((yi > y) !== (yj > y)) &&
        (x < ((xj - xi) * (y - yi)) / (yj - yi) + xi);
      if (intersect) inside = !inside;
    }
    return inside;
  }
  return false;
};

// 选择标注
const selectAnnotation = (id: number): void => {
  selectedAnnotationId.value = id;
  draw();
};

// 删除标注
const deleteAnnotation = (id: number): void => {
  saveToHistory();
  annotations.value = annotations.value.filter(a => a.id !== id);
  if (selectedAnnotationId.value === id) {
    selectedAnnotationId.value = null;
  }
  draw();
  updateImageStatus();
};

// 鼠标事件处理
const handleMouseDown = (e: MouseEvent): void => {
  if (!canvas.value || !imageDisplaySize.value) return;

  const rect = canvas.value.getBoundingClientRect();
  const canvasX = e.clientX - rect.left;
  const canvasY = e.clientY - rect.top;

  const {x: imgX, y: imgY, width: imgWidth, height: imgHeight} = imageDisplaySize.value;

  // 转换为归一化坐标 (0-1)
  const x = (canvasX - imgX) / imgWidth;
  const y = (canvasY - imgY) / imgHeight;

  // 确保坐标在图片范围内
  if (x < 0 || x > 1 || y < 0 || y > 1) return;

  startX.value = x;
  startY.value = y;

  if (activeTool.value === ToolType.SELECT) {
    let clickedAnnotation = false;

    for (let i = annotations.value.length - 1; i >= 0; i--) {
      const annotation = annotations.value[i];
      if (isPointInAnnotation(annotation, x, y)) {
        selectedAnnotationId.value = annotation.id;
        clickedAnnotation = true;
        saveToHistory();
        break;
      }
    }

    if (!clickedAnnotation) {
      selectedAnnotationId.value = null;
    }
    draw();
    return;
  }

  if ([ToolType.RECTANGLE, ToolType.POLYGON].includes(activeTool.value)) {
    isDrawing.value = true;

    if (activeTool.value === ToolType.POLYGON && currentPoints.value.length === 0) {
      currentPoints.value.push({x, y});
    } else if (activeTool.value !== ToolType.POLYGON) {
      currentPoints.value = [{x, y}];
    }

    saveToHistory();
    updateImageStatus();
  }
};

const handleMouseMove = (e: MouseEvent): void => {
  if (!canvas.value || !imageDisplaySize.value) return;

  const rect = canvas.value.getBoundingClientRect();
  const canvasX = e.clientX - rect.left;
  const canvasY = e.clientY - rect.top;

  const {x: imgX, y: imgY, width: imgWidth, height: imgHeight} = imageDisplaySize.value;

  // 转换为归一化坐标 (0-1)
  const x = (canvasX - imgX) / imgWidth;
  const y = (canvasY - imgY) / imgHeight;

  startX.value = x;
  startY.value = y;

  if (isDrawing.value) {
    draw();
  }
};

const handleMouseUp = (): void => {
  if (isDrawing.value && currentPoints.value.length > 0) {
    if (activeTool.value === ToolType.RECTANGLE) {
      const width = startX.value - currentPoints.value[0].x;
      const height = startY.value - currentPoints.value[0].y;

      if (Math.abs(width) > 0.01 && Math.abs(height) > 0.01) {
        console.log(`创建矩形标注，使用标签: ${currentLabel.value.name} (shortcut: ${currentLabel.value.shortcut})`);

        const newAnnotation: Annotation = {
          id: Date.now(),
          type: AnnotationType.RECTANGLE,
          label: currentLabel.value.shortcut, // 确保使用 shortcut
          color: currentLabel.value.color,
          points: [
            {x: currentPoints.value[0].x, y: currentPoints.value[0].y},
            {x: currentPoints.value[0].x + width, y: currentPoints.value[0].y},
            {x: currentPoints.value[0].x + width, y: currentPoints.value[0].y + height},
            {x: currentPoints.value[0].x, y: currentPoints.value[0].y + height}
          ]
        };

        annotations.value.push(newAnnotation);
        selectedAnnotationId.value = newAnnotation.id;
        draw();
      }
    } else if (activeTool.value === ToolType.POLYGON) {
      currentPoints.value.push({x: startX.value, y: startY.value});
      return;
    }

    isDrawing.value = false;
    currentPoints.value = [];
  }
};

const handleDoubleClick = (): void => {
  if (activeTool.value === ToolType.POLYGON && currentPoints.value.length > 2) {
    console.log(`创建多边形标注，使用标签: ${currentLabel.value.name} (shortcut: ${currentLabel.value.shortcut})`);

    const newAnnotation: Annotation = {
      id: Date.now(),
      type: AnnotationType.POLYGON,
      label: currentLabel.value.shortcut, // 确保使用 shortcut
      color: currentLabel.value.color,
      points: [...currentPoints.value]
    };

    annotations.value.push(newAnnotation);
    selectedAnnotationId.value = newAnnotation.id;
    draw();

    isDrawing.value = false;
    currentPoints.value = [];
  }
};

/** 全屏时 Modal/下拉层须挂到全屏元素内，否则浏览器不会显示（默认挂 body） */
const getModalContainer = (): HTMLElement => {
  const fsEl =
    document.fullscreenElement ||
    (document as any).webkitFullscreenElement ||
    (document as any).msFullscreenElement;
  if (fsEl instanceof HTMLElement) {
    return fsEl;
  }
  return document.body;
};

// 全屏切换逻辑
const toggleFullscreen = () => {
  if (!container.value) return;

  if (!isFullscreen.value) {
    const element = container.value;
    if (element.requestFullscreen) {
      element.requestFullscreen();
    } else if ((element as any).webkitRequestFullscreen) {
      (element as any).webkitRequestFullscreen();
    } else if ((element as any).msRequestFullscreen) {
      (element as any).msRequestFullscreen();
    }
  } else {
    if (document.exitFullscreen) {
      document.exitFullscreen();
    } else if ((document as any).webkitExitFullscreen) {
      (document as any).webkitExitFullscreen();
    } else if ((document as any).msExitFullscreen) {
      (document as any).msExitFullscreen();
    }
  }
};

// 处理全屏变化
const handleFullscreenChange = () => {
  isFullscreen.value = Boolean(
    document.fullscreenElement ||
    (document as any).webkitFullscreenElement ||
    (document as any).msFullscreenElement
  );

  requestAnimationFrame(() => {
    resizeCanvas();
  });
};

const isTypingInInput = (): boolean => {
  const el = document.activeElement as HTMLElement | null;
  if (!el) return false;
  return !!el.closest('.ant-input, .ant-select, textarea, input, select');
};

// 键盘快捷键
const handleKeyDown = (e: KeyboardEvent): void => {
  if (isTypingInInput()) {
    return;
  }

  if (e.ctrlKey) {
    if (e.key === 's') {
      e.preventDefault();
      saveCurrentAnnotations();
    } else if (e.key === 'z') {
      e.preventDefault();
      undo();
    }
  }

  switch (e.key) {
    case '1':
    case '2':
    case '3':
    case '4':
    case '5':
    case '6':
    case '7':
    case '8':
    case '9':
    case '0':
      const shortcut = e.key;
      const index = labels.value.findIndex(l => l.shortcut === shortcut);
      if (index !== -1) {
        setCurrentLabel(index);
      } else {
        console.warn(`未找到匹配的标签 shortcut: ${shortcut}`);
      }
      break;
    case 'r':
      setActiveTool(ToolType.RECTANGLE);
      break;
    case 'p':
      setActiveTool(ToolType.POLYGON);
      break;
    case 'v':
      setActiveTool(ToolType.SELECT);
      break;
    case 'ArrowRight':
    case ' ':
      e.preventDefault();
      nextImage();
      break;
    case 'ArrowLeft':
      prevImage();
      break;
    case 'n':
    case 'N':
      nextImage();
      break;
    case 'b':
    case 'B':
      prevImage();
      break;
    case 'Delete':
      if (selectedAnnotationId.value !== null) {
        deleteAnnotation(selectedAnnotationId.value);
      }
      break;
    case 'Escape':
      if (isDrawing.value && activeTool.value === ToolType.POLYGON) {
        isDrawing.value = false;
        currentPoints.value = [];
        draw();
      }
      break;
    case 'f':
      toggleFullscreen();
      break;
  }
};

function openAiBatchModal(): void {
  aiLabelModalRef.value?.openModal();
}

function openImportModal(): void {
  importModalRef.value?.openModal();
}

function openExportModal(): void {
  exportModalRef.value?.openModal();
}

async function onImportSuccess(): Promise<void> {
  listChunkPage.value = 1;
  await fetchImages(1);
}

function stopBatchTaskPoll(): void {
  if (batchTaskPollTimer) {
    clearInterval(batchTaskPollTimer);
    batchTaskPollTimer = null;
  }
  batchTaskRunning.value = false;
}

async function pollBatchTask(taskId: number): Promise<void> {
  stopBatchTaskPoll();
  batchTaskRunning.value = true;

  const check = async () => {
    try {
      const res = await getAutoLabelTask(datasetId.value, taskId);
      const task = res?.data ?? res;
      const status = task?.status;
      if (status === 'COMPLETED') {
        stopBatchTaskPoll();
        createMessage.success(`批量 AI 标注完成，共处理 ${task.processed_count ?? ''} 张`);
        await fetchImages(listChunkPage.value);
      } else if (status === 'FAILED') {
        stopBatchTaskPoll();
        createMessage.error(task?.error_message || '批量 AI 标注失败');
      }
    } catch {
      stopBatchTaskPoll();
    }
  };

  await check();
  batchTaskPollTimer = setInterval(check, 2500);
}

function onBatchAiSuccess(payload: { taskId?: number }): void {
  if (payload?.taskId) {
    pollBatchTask(payload.taskId);
  }
}

// 初始化
onMounted(() => {
  initCanvas();
  window.addEventListener('resize', handleResize);
  window.addEventListener('keydown', handleKeyDown);
  window.addEventListener('resize', resizeCanvas);

  document.addEventListener('fullscreenchange', handleFullscreenChange);
  document.addEventListener('webkitfullscreenchange', handleFullscreenChange);
  document.addEventListener('msfullscreenchange', handleFullscreenChange);

  fetchLabels();
  fetchImages(1);
});

onUnmounted(() => {
  stopBatchTaskPoll();
  window.removeEventListener('resize', handleResize);
  window.removeEventListener('keydown', handleKeyDown);
  window.removeEventListener('resize', resizeCanvas);
  document.removeEventListener('fullscreenchange', handleFullscreenChange);
  document.removeEventListener('webkitfullscreenchange', handleFullscreenChange);
  document.removeEventListener('msfullscreenchange', handleFullscreenChange);
});
</script>

<style lang="less">
// 定义LESS变量
@primary-color: #4361ee;
@success-color: #4cc9f0;
@warning-color: #f8961e;
@error-color: #f72585;
@dark-color: #1a1c2c;
@light-color: #f8f9fa;
@gray-color: #6c757d;
@border-color: #dee2e6;

.annotation-container {
  height: min(92vh, 900px);
  display: flex;
  flex-direction: column;
  background: @dark-color;
  transition: all 0.3s ease;

  .top-toolbar {
    display: flex;
    align-items: center;
    justify-content: space-between;
    padding: 8px 16px;
    background: #fff;
    border-bottom: 1px solid #e8e8e8;
    flex-shrink: 0;

    .progress-info {
      display: flex;
      align-items: center;
      gap: 8px;
      font-size: 14px;
      color: #333;
      min-width: 0;
      flex: 1;
      flex-wrap: wrap;

      strong {
        color: @primary-color;
      }

      .batch-task-hint {
        margin-left: 12px;
        color: @warning-color;
        font-size: 13px;
      }

      .progress-percent {
        color: #6b7a90;
        font-size: 12px;
      }
    }

    .top-actions {
      display: flex;
      align-items: center;
      gap: 8px;
    }

    .top-actions-divider {
      width: 1px;
      height: 24px;
      background: #e8e8e8;
      margin: 0 4px;
      flex-shrink: 0;
    }

    .tool-group {
      display: flex;
      align-items: center;
      gap: 6px;
    }

    .tool-button {
      display: inline-flex;
      align-items: center;
      gap: 6px;
      padding: 6px 12px;
      border-radius: 6px;
      border: 1px solid #d9d9d9;
      background: #fff;
      font-size: 13px;
      color: @gray-color;
      cursor: pointer;
      transition: all 0.2s;

      &:hover {
        border-color: @primary-color;
        color: @primary-color;
      }

      &.active {
        background: fade(@primary-color, 10%);
        border-color: @primary-color;
        color: @primary-color;
      }
    }

    .action-btn {
      display: inline-flex;
      align-items: center;
      gap: 6px;
      padding: 6px 14px;
      border-radius: 6px;
      border: 1px solid #d9d9d9;
      background: #fff;
      font-size: 13px;
      color: @gray-color;
      cursor: pointer;
      transition: all 0.2s;

      &:hover:not(:disabled) {
        border-color: @primary-color;
        color: @primary-color;
      }

      &:disabled {
        opacity: 0.5;
        cursor: not-allowed;
      }

      &.import-btn {
        background: @primary-color;
        border-color: @primary-color;
        color: #fff;

        &:hover:not(:disabled) {
          opacity: 0.9;
          border-color: @primary-color;
          color: #fff;
        }
      }

      &.ai-batch-btn {
        background: #fff7e6;
        border-color: #ffc53d;
        color: #d48806;

        &:hover:not(:disabled) {
          background: #ffe58f;
          border-color: #ffc53d;
          color: #d48806;
        }
      }
    }
  }

  .main-content {
    display: flex;
    flex: 1;
    min-height: 0;
    min-width: 0;
  }

  .image-panel {
    width: 280px;
    min-width: 240px;
    background: #1e2433;
    display: flex;
    flex-direction: column;
    border-right: 1px solid rgba(255, 255, 255, 0.08);
    flex-shrink: 0;

    .image-list-header {
      display: flex;
      align-items: center;
      justify-content: space-between;
      gap: 8px;
      padding: 10px 10px 8px;
      flex-shrink: 0;
    }

    :deep(.ant-select-selector) {
      background: rgba(255, 255, 255, 0.06) !important;
      border-color: rgba(255, 255, 255, 0.12) !important;
      color: #e8edf5;
    }

    :deep(.ant-select) {
      .ant-select-selection-item {
        color: #e8edf5;
        font-size: 12px;
      }

      .ant-select-arrow {
        color: #8c9ab0;
      }

      &:hover .ant-select-selector,
      &.ant-select-focused .ant-select-selector {
        border-color: @primary-color !important;
      }
    }

    .image-list-stats {
      display: flex;
      align-items: center;
      gap: 4px;
      min-width: 0;
      flex: 1;
      font-size: 12px;
      color: #9aa8bc;

      .stat-done {
        color: #6bcb77;
      }

      .stat-total {
        color: #c5d0e0;
        font-weight: 600;
      }

      .stat-filtered {
        color: #8c9ab0;
      }
    }

    .filter-select {
      width: 96px;
      flex-shrink: 0;
    }

    .image-list-scroll {
      position: relative;
      flex: 1;
      min-height: 0;
      overflow-y: auto;
      margin: 0 8px;

      &::-webkit-scrollbar {
        width: 6px;
      }

      &::-webkit-scrollbar-thumb {
        background: rgba(255, 255, 255, 0.15);
        border-radius: 3px;
      }
    }

    .image-list-phantom {
      width: 100%;
      pointer-events: none;
    }

    .image-list {
      list-style: none;
      margin: 0;
      padding: 0;
      position: absolute;
      top: 0;
      left: 0;
      right: 0;
      will-change: transform;
    }

    .image-list-item {
      display: flex;
      align-items: center;
      gap: 6px;
      height: 36px;
      box-sizing: border-box;
      padding: 0 8px;
      margin-bottom: 0;
      border-radius: 6px;
      cursor: pointer;
      border: 1px solid transparent;
      transition: background 0.15s;
      color: #c5d0e0;
      font-size: 13px;

      &:hover {
        background: rgba(255, 255, 255, 0.08);
      }

      &.active {
        background: rgba(67, 97, 238, 0.35);
        border-color: rgba(67, 97, 238, 0.5);
        color: #fff;
      }

      .image-index {
        flex-shrink: 0;
        width: 28px;
        text-align: right;
        color: #8c9ab0;
        font-size: 12px;
        font-variant-numeric: tabular-nums;
      }

      &.active .image-index {
        color: rgba(255, 255, 255, 0.85);
      }

      .image-name {
        flex: 1;
        min-width: 0;
        overflow: hidden;
        text-overflow: ellipsis;
        white-space: nowrap;
      }

      .image-status-badge {
        flex-shrink: 0;
        font-size: 11px;
        padding: 1px 6px;
        border-radius: 10px;

        &.status-pending {
          color: #9aa8bc;
          background: rgba(255, 255, 255, 0.06);
        }

        &.status-annotated {
          color: #4cc9f0;
          background: rgba(76, 201, 240, 0.15);
        }

        &.status-completed {
          color: #6bcb77;
          background: rgba(107, 203, 119, 0.15);
        }
      }
    }

    .image-list-empty {
      position: absolute;
      inset: 0;
      display: flex;
      align-items: center;
      justify-content: center;
      color: #6b7a90;
      font-size: 13px;
      pointer-events: none;
    }

    .image-list-pagination {
      display: flex;
      align-items: center;
      justify-content: center;
      padding: 8px;
      border-top: 1px solid rgba(255, 255, 255, 0.08);
      flex-shrink: 0;

      :deep(.ant-pagination) {
        color: #9aa8bc;
      }

      :deep(.ant-pagination-simple-pager input) {
        background: rgba(255, 255, 255, 0.06);
        border-color: rgba(255, 255, 255, 0.12);
        color: #e8edf5;
      }

      :deep(.ant-pagination-item-link) {
        color: #c5d0e0;

        &:hover {
          color: @primary-color;
        }
      }
    }
  }

  .canvas-area {
    flex: 1;
    display: flex;
    flex-direction: column;
    background: @dark-color;
    position: relative;
    min-width: 0;
    overflow: hidden;

    .image-position-indicator {
      position: absolute;
      top: 20px;
      left: 50%;
      transform: translateX(-50%);
      background: rgba(0, 0, 0, 0.85);
      color: white;
      padding: 8px 16px;
      border-radius: 30px;
      font-size: 16px;
      font-weight: 500;
      z-index: 20;
      display: flex;
      align-items: center;
      box-shadow: 0 4px 12px rgba(0, 0, 0, 0.25);

      .position-text {
        display: flex;
        align-items: center;

        .current-index {
          color: #4cc9f0;
          font-weight: bold;
          font-size: 18px;
          margin: 0 4px;
        }

        .total-count {
          color: #a0aec0;
          margin-left: 4px;
        }
      }
    }

    .canvas-wrapper {
      flex: 1;
      display: flex;
      align-items: center;
      justify-content: center;
      overflow: auto;
      padding: 20px;
      max-width: 100%;

      .annotation-canvas {
        box-shadow: 0 10px 30px rgba(0, 0, 0, 0.2);
        background: #2d3748;
        border-radius: 8px;
      }
    }

    .shortcut-hint {
      position: absolute;
      bottom: 90px;
      left: 50%;
      transform: translateX(-50%);
      max-width: calc(100% - 40px);
      background: rgba(0, 0, 0, 0.7);
      color: white;
      padding: 10px 20px;
      border-radius: 30px;
      font-size: 14px;
      display: flex;
      flex-wrap: nowrap;
      align-items: center;
      gap: 14px;
      z-index: 10;
      overflow-x: auto;
      overflow-y: hidden;
      white-space: nowrap;
      box-shadow: 0 4px 12px rgba(0, 0, 0, 0.25);
      scrollbar-width: none;

      &::-webkit-scrollbar {
        display: none;
      }

      .hint-item {
        display: inline-flex;
        flex-shrink: 0;
        align-items: center;
        gap: 6px;
        white-space: nowrap;

        .key {
          flex-shrink: 0;
          background: rgba(255, 255, 255, 0.2);
          padding: 2px 8px;
          border-radius: 4px;
          font-weight: 500;
          line-height: 1.5;
        }

        .text {
          flex-shrink: 0;
          line-height: 1.5;
        }
      }
    }

    .status-indicator {
      position: absolute;
      top: 20px;
      right: 20px;
      background: rgba(0, 0, 0, 0.85);
      color: white;
      padding: 12px;
      border-radius: 8px;
      font-size: 14px;
      min-width: 280px;
      z-index: 10;

      .status-header {
        display: flex;
        flex-direction: column;
        gap: 8px;
        padding-bottom: 10px;
        border-bottom: 1px solid rgba(255, 255, 255, 0.2);
        margin-bottom: 10px;

        .completion-status {
          font-weight: bold;
          font-size: 16px;

          &.completed {
            color: #4CAF50;
          }
        }

        .modification-info {
          display: flex;
          flex-direction: column;
          gap: 4px;
          font-size: 13px;
          color: #e0e0e0;
        }
      }

      .annotation-count {
        display: flex;
        align-items: center;
        gap: 8px;
        font-size: 14px;

        .status-dot {
          width: 10px;
          height: 10px;
          border-radius: 50%;
          background: #4CAF50;
        }

        .unsaved-indicator {
          color: #ff6b6b;
          font-weight: bold;
          margin-left: 8px;
        }
      }
    }

    .fullscreen-control {
      position: absolute;
      bottom: 20px;
      right: 20px;
      background: rgba(0, 0, 0, 0.7);
      color: white;
      padding: 10px 15px;
      border-radius: 30px;
      font-size: 14px;
      cursor: pointer;
      display: flex;
      align-items: center;
      gap: 8px;
      z-index: 10;
      transition: background 0.3s;

      &:hover {
        background: rgba(0, 0, 0, 0.9);
      }

      i {
        font-size: 16px;
      }
    }
  }

  .label-panel {
    width: 240px;
    background: white;
    padding: 16px 14px;
    box-shadow: -2px 0 10px rgba(0, 0, 0, 0.05);
    display: flex;
    flex-direction: column;
    z-index: 5;
    overflow-y: auto;

    .panel-header {
      padding-left: 5px;
      font-size: 16px;
      font-weight: 600;
      margin-bottom: 16px;
      color: @dark-color;
      display: flex;
      align-items: center;
      gap: 8px;
      flex-wrap: wrap;

      i {
        color: @primary-color;
      }

      .annotation-stats {
        flex: 100%;
        font-size: 14px;
        font-weight: normal;
        margin-top: 8px;
        color: @gray-color;
      }
    }

    .label-list {
      display: flex;
      flex-direction: column;
      gap: 8px;
      overflow-y: auto;
      flex: 1;
      max-height: 200px;

      .label-item {
        display: flex;
        align-items: center;
        padding: 12px;
        border-radius: 8px;
        cursor: pointer;
        transition: all 0.2s;
        border: 1px solid @border-color;

        &:hover {
          transform: translateY(-2px);
          box-shadow: 0 4px 6px rgba(0, 0, 0, 0.05);
        }

        &.active {
          border-color: @primary-color;
          background: fade(@primary-color, 5%);
        }

        .color-badge {
          width: 24px;
          height: 24px;
          border-radius: 6px;
          margin-right: 12px;
        }

        .label-name {
          flex: 1;
          font-weight: 500;
        }

        .label-shortcut {
          background: #e9ecef;
          padding: 2px 8px;
          border-radius: 4px;
          font-size: 12px;
          font-weight: 600;
          color: @gray-color;
        }
      }
    }

    .object-layer-section {
      margin-top: 20px;
      border-top: 1px solid #eee;
      padding-top: 15px;

      .panel-header {
        margin-bottom: 10px;
        font-size: 14px;
        color: #666;

        i {
          color: #666;
        }
      }

      .object-list {
        max-height: 250px;
        overflow-y: auto;
        border: 1px solid #eee;
        border-radius: 6px;
        padding: 5px;

        .object-item {
          display: flex;
          align-items: center;
          padding: 8px;
          border-radius: 4px;
          margin-bottom: 5px;
          cursor: pointer;
          transition: all 0.2s;

          &:hover {
            background-color: #f5f7fa;
          }

          &.selected {
            background-color: fade(@primary-color, 10%);
            border-left: 3px solid @primary-color;
          }

          .object-color {
            width: 16px;
            height: 16px;
            border-radius: 4px;
            margin-right: 10px;
          }

          .object-name {
            flex: 1;
            font-size: 13px;
            color: #333;
          }

          .object-actions {
            .delete-btn {
              background: none;
              border: none;
              color: #f44336;
              cursor: pointer;
              padding: 4px;
              border-radius: 4px;

              &:hover {
                background-color: #ffeeee;
              }
            }
          }
        }
      }
    }
  }
}

// 全屏模式下的样式调整
:fullscreen .annotation-container,
:-webkit-full-screen .annotation-container,
:-moz-full-screen .annotation-container,
:-ms-fullscreen .annotation-container {
  height: 100vh;
  width: 100vw;
  background: @dark-color;

  :deep(.ant-modal-wrap),
  :deep(.ant-modal-mask) {
    position: fixed;
    z-index: 2000;
  }

  .main-content {
    flex: 1;
    min-height: 0;
  }

  .canvas-area {
    flex: 1;
  }

  .label-panel {
    width: 220px;
    min-width: 250px;
    max-width: 350px;
    transition: width 0.3s ease;
  }
}

@media (min-width: 1920px) {
  :fullscreen .label-panel {
    width: 220px;
  }
}

.image-panel-dropdown.ant-select-dropdown {
  background: #2a3142;
  border: 1px solid rgba(255, 255, 255, 0.12);
  padding: 4px;

  .ant-select-item {
    color: #c5d0e0;
    border-radius: 4px;
  }

  .ant-select-item-option-active,
  .ant-select-item-option-selected {
    background: rgba(67, 97, 238, 0.35);
    color: #fff;
  }
}
</style>
