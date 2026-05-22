<template>
  <div class="camera-container">
    <div class="camera-tab">
      <Tabs
        :activeKey="state.activeKey"
        :animated="{ inkBar: true, tabPane: true }"
        :tabBarGutter="60"
        @tabClick="handleTabClick"
      >
        <TabPane key="1" tab="分屏监控">
          <SplitScreenMonitor
            ref="splitScreenMonitorRef"
            :initial-mode="splitScreenInitialMode"
            @play="handleCardPlay"
          />
        </TabPane>
        <TabPane key="2" tab="设备列表">
          <GpuStackMonitorTip class="page-monitor-tip" />
          <Gb28181DeviceDetail
            v-if="gbDetailVisible"
            :sip-device-id="gbDetailSipId"
            :title="gbDetailTitle"
            channel-hint="点击下方通道进行点播播放"
            @back="closeGbDetail"
          />
          <NvrDeviceDetail
            v-else-if="nvrDetailVisible"
            :nvr-id="nvrDetailId"
            :title="nvrDetailTitle"
            @back="closeNvrDetail"
            @view="handleNvrChannelView"
            @edit="handleNvrChannelEdit"
            @play="handleCardPlay"
          />
          <template v-else>
          <!-- 列表模式 -->
          <BasicTable v-if="viewMode === 'table'" @register="registerTable">
                <template #toolbar>
                  <div class="toolbar-buttons">
                    <a-button type="primary" @click="handleScanOnvif">
                      <template #icon>
                        <RadarChartOutlined/>
                      </template>
                      扫描局域网ONVIF设备
                    </a-button>
                    <a-button @click="handleRefreshOnvifDevices">
                      <template #icon>
                        <SyncOutlined/>
                      </template>
                      更新ONVIF设备
                    </a-button>
                    <a-button @click="handleScanSegmentCamera">
                      <template #icon>
                        <SearchOutlined/>
                      </template>
                      通过网段注册摄像头
                    </a-button>
                    <a-button @click="handleScanSegmentNvr">
                      <template #icon>
                        <ClusterOutlined/>
                      </template>
                      通过网段注册NVR
                    </a-button>
                    <a-button @click="openAddModal('source')">
                      <template #icon>
                        <VideoCameraAddOutlined/>
                      </template>
                      新增直连设备
                    </a-button>
                    <a-button @click="handleToggleViewMode" type="default">
                      <template #icon>
                        <SwapOutlined />
                      </template>
                      切换视图
                    </a-button>
                  </div>
                </template>
                <template #bodyCell="{ column, record }">
                  <template v-if="column.dataIndex === 'name'">
                    <span style="cursor: pointer" @click="handleCopy(record.name)">
                      <Icon icon="tdesign:copy-filled" color="#4287FCFF"/>
                      {{ formatCameraDeviceLabel(record) }}
                    </span>
                  </template>
                  <template
                    v-else-if="['id', 'model', 'source', 'rtmp_stream', 'http_stream', 'ai_rtmp_stream', 'ai_http_stream'].includes(column.key)">
            <span style="cursor: pointer" @click="handleCopy(record[column.key])"><Icon
              icon="tdesign:copy-filled" color="#4287FCFF"/> {{ record[column.key] }}</span>
                  </template>

                  <!-- 流媒体状态显示 -->
                  <template v-else-if="column.dataIndex === 'stream_status'">
                    <a-tag :color="getStreamStatusColor(record.stream_status)">
                      {{ getStreamStatusText(record.stream_status) }}
                    </a-tag>
                  </template>

                  <template v-else-if="column.dataIndex === 'action'">
                    <TableAction
                      :actions="getTableActions(record)"
                    />
                  </template>
                </template>
              </BasicTable>

              <!-- 卡片模式 -->
              <div v-else class="card-mode-wrapper">
                <DeviceMixedCardList
                  ref="deviceMixedCardListRef"
                  :params="{}"
                  @view="handleCardView"
                  @edit="handleCardEdit"
                  @delete="handleCardDelete"
                  @play="handleCardPlay"
                  @playAI="handleCardPlayAI"
                  @toggleStream="handleCardToggleStream"
                  @open-gb-device="handleOpenGbDevice"
                  @refresh-gb-device="handleRefreshGbDevice"
                  @view-gb-device="handleViewGbDevice"
                  @edit-gb-device="handleEditGbDevice"
                  @open-nvr-device="handleOpenNvrDevice"
                >
                  <template #header>
                    <a-button type="primary" @click="handleScanOnvif">
                      <template #icon>
                        <RadarChartOutlined/>
                      </template>
                      扫描局域网ONVIF设备
                    </a-button>
                    <a-button @click="handleRefreshOnvifDevices">
                      <template #icon>
                        <SyncOutlined/>
                      </template>
                      更新ONVIF设备
                    </a-button>
                    <a-button @click="handleScanSegmentCamera">
                      <template #icon>
                        <SearchOutlined/>
                      </template>
                      通过网段注册摄像头
                    </a-button>
                    <a-button @click="handleScanSegmentNvr">
                      <template #icon>
                        <ClusterOutlined/>
                      </template>
                      通过网段注册NVR
                    </a-button>
                    <a-button @click="openAddModal('source')">
                      <template #icon>
                        <VideoCameraAddOutlined/>
                      </template>
                      新增直连设备
                    </a-button>
                    <a-button @click="handleToggleViewMode" type="default">
                      <template #icon>
                        <SwapOutlined />
                      </template>
                      切换视图
                    </a-button>
                  </template>
                </DeviceMixedCardList>
              </div>
          </template>

          <DialogPlayer title="视频播放" @register="registerPlayerAddModel"
                        @success="handlePlayerSuccess"/>
          <VideoModal @register="registerAddModel" @success="handleSuccess"/>
          <SegmentScanModal @register="registerSegmentScanModal" @success="handleSuccess"/>
          <Gb28181DeviceModal @register="registerGbDeviceModal" @success="handleSuccess"/>
        </TabPane>
        <TabPane key="3" tab="抓拍空间">
          <SnapSpace ref="snapSpaceRef"/>
        </TabPane>
        <TabPane key="4" tab="录像空间">
          <RecordSpace ref="recordSpaceRef"/>
        </TabPane>
        <TabPane key="5" tab="推流转发">
          <StreamForward ref="streamForwardRef"/>
        </TabPane>
        <TabPane key="6" tab="算法任务">
          <AlgorithmTask ref="algorithmTaskRef"/>
        </TabPane>
        <TabPane key="7" tab="拉流代理">
          <Gb28181PullProxy ref="gb28181PullProxyRef"/>
        </TabPane>
        <TabPane key="8" tab="节点管理">
          <Gb28181Node ref="gb28181NodeRef"/>
        </TabPane>
      </Tabs>
    </div>
  </div>
</template>

<script lang="ts" setup>
import {onMounted, onUnmounted, reactive, ref} from 'vue';
import {useRoute} from 'vue-router';
import {TabPane, Tabs} from 'ant-design-vue';
import {BasicTable, TableAction, useTable} from '@/components/Table';
import {useMessage} from '@/hooks/web/useMessage';
import {getBasicColumns, getFormConfig} from "./Data";
import {useModal} from "@/components/Modal";
import VideoModal from "./components/VideoModal/index.vue";
import {
  deleteDevice,
  DeviceInfo,
  getDeviceList,
  getStreamStatus,
  refreshDevices,
  startStreamForwarding,
  stopStreamForwarding,
  StreamStatusResponse
} from '@/api/device/camera';
import {
  ClusterOutlined,
  RadarChartOutlined,
  SearchOutlined,
  SwapOutlined,
  SyncOutlined,
  VideoCameraAddOutlined,
} from '@ant-design/icons-vue';
import SegmentScanModal from './components/SegmentScanModal/index.vue';
import DialogPlayer from "@/components/VideoPlayer/DialogPlayer.vue";
import SplitScreenMonitor from "./components/SplitScreenMonitor/index.vue";
import SnapSpace from "./components/SnapSpace/index.vue";
import AlgorithmTask from "./components/AlgorithmTask/index.vue";
import RecordSpace from "./components/RecordSpace/index.vue";
import DeviceMixedCardList from './components/DeviceMixedCardList/index.vue';
import Gb28181DeviceDetail from './components/Gb28181DeviceDetail/index.vue';
import NvrDeviceDetail from './components/NvrDeviceDetail/index.vue';
import Gb28181DeviceModal from './components/Gb28181DeviceModal/index.vue';
import type { Gb28181CardItem } from './components/Gb28181DeviceCard/index.vue';
import type { NvrCardItem } from './utils/nvrDeviceGroup';
import {
  fetchMergedDeviceList,
  isGb28181SipListRow,
  type GbSipDeviceSummary,
} from './utils/gb28181DeviceGroup';
import { isNvrListRow } from './utils/deviceLabel';
import StreamForward from "./components/StreamForward/index.vue";
import Gb28181PullProxy from "@/views/gb28181/components/PullProxy/index.vue";
import { formatCameraDeviceLabel } from './utils/deviceLabel';
import {
  hasDirectPlayStream,
  openDeviceInDialogPlayer,
  supportsRtspForward,
} from './utils/devicePlay';
import Gb28181Node from "@/views/gb28181/components/Node/index.vue";
import GpuStackMonitorTip from '@/components/GpuStackMonitorTip/index.vue';

defineOptions({name: 'CAMERA'})

const route = useRoute();

const {createMessage} = useMessage();
const [registerAddModel, {openModal}] = useModal();
const [registerSegmentScanModal, {openModal: openSegmentScanModal}] = useModal();
const [registerGbDeviceModal, {openModal: openGbDeviceModal}] = useModal();

const [registerPlayerAddModel, {openModal: openPlayerAddModel}] = useModal();

// Tab状态
const state = reactive({
  activeKey: '1',
});

// 视图模式（默认卡片模式）
const viewMode = ref<'table' | 'card'>('card');

// 分屏监控组件引用
const splitScreenMonitorRef = ref();
const splitScreenInitialMode = ref<'config' | 'monitor'>('monitor');

// 混合设备卡片列表引用
const deviceMixedCardListRef = ref();

// 国标设备详情内页
const gbDetailVisible = ref(false);
const gbDetailSipId = ref('');
const gbDetailTitle = ref('');

const nvrDetailVisible = ref(false);
const nvrDetailId = ref(0);
const nvrDetailTitle = ref('');

// 抓拍空间组件引用
const snapSpaceRef = ref();

// 录像空间组件引用
const recordSpaceRef = ref();

// 算法任务组件引用
const algorithmTaskRef = ref();

// 推流转发组件引用
const streamForwardRef = ref();

// GB28181组件引用
const gb28181PullProxyRef = ref();
const gb28181NodeRef = ref();

/** 一级 Tab key：1 分屏监控 … 8 节点管理（与模板 TabPane key 一致） */
const CAMERA_TAB_KEYS = {
  SPLIT_MONITOR: '1',
  DEVICE_LIST: '2',
  SNAP: '3',
  RECORD: '4',
  STREAM_FORWARD: '5',
  ALGORITHM: '6',
  GB_PULL_PROXY: '7',
  GB_NODE: '8',
} as const;

const CAMERA_TAB_ID_SET = new Set<string>(Object.values(CAMERA_TAB_KEYS));

/** 旧版 tab 编号兼容（已移除「设备目录」独立 Tab） */
const LEGACY_CAMERA_TAB_MAP: Record<string, string> = {
  '3': CAMERA_TAB_KEYS.SPLIT_MONITOR,
  '4': CAMERA_TAB_KEYS.SNAP,
  '5': CAMERA_TAB_KEYS.RECORD,
  '6': CAMERA_TAB_KEYS.STREAM_FORWARD,
  '7': CAMERA_TAB_KEYS.ALGORITHM,
  '8': CAMERA_TAB_KEYS.GB_PULL_PROXY,
  '9': CAMERA_TAB_KEYS.GB_NODE,
};

/** 路由 ?tab=：接受 1～8；旧 3（设备目录）映射到分屏监控 */
function normalizeCameraRouteTab(tab: string): string {
  if (CAMERA_TAB_ID_SET.has(tab)) return tab;
  if (LEGACY_CAMERA_TAB_MAP[tab]) return LEGACY_CAMERA_TAB_MAP[tab];
  return CAMERA_TAB_KEYS.DEVICE_LIST;
}

// Tab切换
const handleTabClick = (activeKey: string) => {
  state.activeKey = activeKey;
  // 切换到设备列表标签页时，刷新直连设备数据
  if (activeKey === CAMERA_TAB_KEYS.DEVICE_LIST) {
    handleSuccess();
  }
  // 切换到抓拍空间标签页时，刷新数据
  if (activeKey === CAMERA_TAB_KEYS.SNAP && snapSpaceRef.value) {
    snapSpaceRef.value.refresh();
  }
  // 切换到录像空间标签页时，刷新数据
  if (activeKey === CAMERA_TAB_KEYS.RECORD && recordSpaceRef.value) {
    recordSpaceRef.value.refresh();
  }
  // 切换到算法任务标签页时，刷新数据
  if (activeKey === CAMERA_TAB_KEYS.ALGORITHM && algorithmTaskRef.value) {
    algorithmTaskRef.value.refresh();
  }
  // 切换到推流转发标签页时，刷新数据
  if (activeKey === CAMERA_TAB_KEYS.STREAM_FORWARD && streamForwardRef.value) {
    streamForwardRef.value.refresh();
  }
  if (activeKey === CAMERA_TAB_KEYS.SPLIT_MONITOR && splitScreenMonitorRef.value) {
    splitScreenMonitorRef.value.refresh();
  }
  // GB28181 拉流/节点 Tab 可按需在此 refresh
  // if (activeKey === CAMERA_TAB_KEYS.GB_PULL_PROXY && gb28181PullProxyRef.value) {
  //   gb28181PullProxyRef.value.refresh();
  // }
  // if (activeKey === CAMERA_TAB_KEYS.GB_NODE && gb28181NodeRef.value) {
  //   gb28181NodeRef.value.refresh();
  // }
};

// 切换视图模式
const handleToggleViewMode = () => {
  viewMode.value = viewMode.value === 'table' ? 'card' : 'table';
  if (viewMode.value === 'card' && deviceMixedCardListRef.value) {
    deviceMixedCardListRef.value.fetch();
  }
};

function openGbDetail(summary: GbSipDeviceSummary) {
  gbDetailSipId.value = summary.sipDeviceId;
  gbDetailTitle.value = summary.name || summary.sipDeviceId;
  gbDetailVisible.value = true;
}

function closeGbDetail() {
  gbDetailVisible.value = false;
  gbDetailSipId.value = '';
  gbDetailTitle.value = '';
}

function openNvrDetail(item: NvrCardItem) {
  nvrDetailId.value = item.nvrId;
  nvrDetailTitle.value = item.name;
  nvrDetailVisible.value = true;
}

function closeNvrDetail() {
  nvrDetailVisible.value = false;
  nvrDetailId.value = 0;
  nvrDetailTitle.value = '';
}

function handleOpenNvrDevice(item: NvrCardItem) {
  openNvrDetail(item);
}

function handleNvrChannelView(device: DeviceInfo) {
  openAddModal('view', device);
}

function handleNvrChannelEdit(device: DeviceInfo) {
  openAddModal('edit', device);
}

function handleOpenGbDevice(summary: GbSipDeviceSummary) {
  openGbDetail(summary);
}

async function handleRefreshGbDevice(summary: GbSipDeviceSummary) {
  try {
    const { refreshChannelList } = await import('@/api/device/gb28181');
    await refreshChannelList(summary.sipDeviceId);
    createMessage.success('已开始同步通道');
    if (deviceMixedCardListRef.value) {
      deviceMixedCardListRef.value.fetch();
    }
  } catch (e) {
    console.error(e);
    createMessage.error('同步通道失败');
  }
}

function gbSipIdFromRecord(record: { sip_device_id?: string; id?: string; deviceIdentification?: string }) {
  return (
    record.sip_device_id ||
    String(record.deviceIdentification || '').trim() ||
    String(record.id || '').replace(/^gb_sip_/, '')
  );
}

function openGbDeviceInfoModal(type: 'view' | 'edit', payload: { sipDeviceId: string }) {
  openGbDeviceModal(true, {
    isView: type === 'view',
    sipDeviceId: payload.sipDeviceId,
  });
}

function handleViewGbDevice(item: Gb28181CardItem) {
  openGbDeviceInfoModal('view', { sipDeviceId: item.deviceIdentification });
}

function handleEditGbDevice(item: Gb28181CardItem) {
  openGbDeviceInfoModal('edit', { sipDeviceId: item.deviceIdentification });
}

function handleTableViewGbDevice(record: DeviceInfo & { sip_device_id?: string }) {
  openGbDeviceInfoModal('view', { sipDeviceId: gbSipIdFromRecord(record) });
}

function handleTableEditGbDevice(record: DeviceInfo & { sip_device_id?: string }) {
  openGbDeviceInfoModal('edit', { sipDeviceId: gbSipIdFromRecord(record) });
}

// 设备流状态映射
const deviceStreamStatuses = ref<Record<string, string>>({});
// 状态检查定时器
const statusCheckTimer = ref<NodeJS.Timeout | null>(null);

// 获取流状态文本
const getStreamStatusText = (status: string) => {
  const statusMap: Record<string, string> = {
    'running': '运行中',
    'stopped': '已停止',
    'error': '错误',
    'unknown': '未知'
  };
  return statusMap[status] || status;
};

// 获取流状态颜色
const getStreamStatusColor = (status: string) => {
  const colorMap: Record<string, string> = {
    'running': 'green',
    'stopped': 'red',
    'error': 'orange',
    'unknown': 'default'
  };
  return colorMap[status] || 'default';
};

// 检查单个设备的流状态
const checkDeviceStreamStatus = async (deviceId: string) => {
  try {
    // 确保 deviceStreamStatuses.value 始终是一个对象
    if (!deviceStreamStatuses.value) {
      deviceStreamStatuses.value = {};
    }
    const response: StreamStatusResponse = await getStreamStatus(deviceId);
    if (response.code === 0) {
      deviceStreamStatuses.value[deviceId] = response.data.status;
    } else {
      deviceStreamStatuses.value[deviceId] = 'error';
    }
  } catch (error) {
    console.error(`检查设备 ${deviceId} 流状态失败`, error);
    // 确保 deviceStreamStatuses.value 始终是一个对象
    if (!deviceStreamStatuses.value) {
      deviceStreamStatuses.value = {};
    }
    deviceStreamStatuses.value[deviceId] = 'error';
  }
};

// 检查所有设备的流状态
const checkAllDevicesStreamStatus = async (devices: DeviceInfo[]) => {
  try {
    const deviceIds = devices.map(device => device.id);
    for (const deviceId of deviceIds) {
      await checkDeviceStreamStatus(deviceId);
    }
  } catch (error) {
    console.error('检查设备流状态失败', error);
  }
};

const [registerTable, {reload}] = useTable({
  canResize: true,
  showIndexColumn: false,
  title: '摄像头列表',
  api: fetchMergedDeviceList,
  columns: getBasicColumns(),
  useSearchForm: true,
  showTableSetting: false,
  pagination: true,
  formConfig: getFormConfig(),
  fetchSetting: {
    listField: 'data',
    totalField: 'total',
  },
  rowKey: 'id',
  // 添加成功回调，获取设备流状态
  onSuccess: (data) => {
    if (data && data.data) {
      // 确保 deviceStreamStatuses.value 始终是一个对象
      if (!deviceStreamStatuses.value) {
        deviceStreamStatuses.value = {};
      }
      // 初始化设备流状态
      data.data.forEach((device: DeviceInfo) => {
        if (!deviceStreamStatuses.value[device.id]) {
          deviceStreamStatuses.value[device.id] = 'unknown';
        }
      });

      // 已禁用自动检查设备流状态
      // checkAllDevicesStreamStatus(data.data);
    }
  }
});

// 启动状态检查定时器
const startStatusCheckTimer = () => {
  if (statusCheckTimer.value) {
    clearInterval(statusCheckTimer.value);
  }

  statusCheckTimer.value = setInterval(() => {
    if (Object.keys(deviceStreamStatuses.value).length > 0) {
      Object.keys(deviceStreamStatuses.value).forEach(deviceId => {
        checkDeviceStreamStatus(deviceId);
      });
    }
  }, 10000); // 每10秒检查一次
};

// 获取表格操作按钮
const getTableActions = (record) => {
  if (isNvrListRow(record)) {
    const nvrId = record.nvr_id_num ?? Number(String(record.id).replace(/^nvr_/, ''));
    return [
      {
        icon: 'ant-design:cluster-outlined',
        tooltip: '挂载摄像头',
        onClick: () => {
          openNvrDetail({
            nvrId,
            name: record.name || `[NVR] ${record.ip}`,
            ip: record.ip,
            port: record.port ?? 80,
            camera_count: record.channel_count ?? 0,
            _nvr: { id: nvrId, ip: record.ip },
          } as NvrCardItem);
        },
      },
    ];
  }
  if (isGb28181SipListRow(record)) {
    return [
      {
        icon: 'ant-design:eye-filled',
        tooltip: '详情',
        onClick: () => handleTableViewGbDevice(record),
      },
      {
        icon: 'ant-design:edit-filled',
        tooltip: '编辑',
        onClick: () => handleTableEditGbDevice(record),
      },
      {
        icon: 'ant-design:video-camera-outlined',
        tooltip: '通道列表',
        onClick: () => {
          openGbDetail({
            sipDeviceId: gbSipIdFromRecord(record),
            name: record.name || record.sip_device_id,
            channelCount: record.channel_count || 0,
            online: !!record.online,
            channels: [],
          });
        },
      },
    ];
  }

  const actions = [];

  if (hasDirectPlayStream(record)) {
    actions.push({
      icon: 'octicon:play-16',
      tooltip: supportsRtspForward(record) ? '播放视频流' : '播放国标通道',
      onClick: () => handlePlay(record),
    });
  }

  if (hasDirectPlayStream(record, true)) {
    actions.push({
      icon: 'hugeicons:ai-video',
      tooltip: '查看AI流',
      onClick: () => handlePlayAIStream(record),
    });
  }

  if (supportsRtspForward(record)) {
    const currentStatus = (deviceStreamStatuses.value && deviceStreamStatuses.value[record.id]) || 'unknown';
    if (currentStatus === 'running') {
      actions.push({
        icon: 'ant-design:pause-circle-outlined',
        tooltip: '停止RTSP转发',
        onClick: () => handleDisableRtsp(record),
      });
    } else {
      actions.push({
        icon: 'ant-design:swap-outline',
        tooltip: '启用RTSP转发',
        onClick: () => handleEnableRtsp(record),
      });
    }
  }

  // 添加详情、编辑、删除按钮
  actions.push(
    {
      icon: 'ant-design:eye-filled',
      tooltip: '详情',
      onClick: () => openAddModal('view', record)
    },
    {
      icon: 'ant-design:edit-filled',
      tooltip: '编辑',
      onClick: () => openAddModal('edit', record)
    },
    {
      icon: 'material-symbols:delete-outline-rounded',
      tooltip: '删除',
      popConfirm: {
        title: '确定删除此设备？',
        confirm: () => handleDelete(record)
      }
    }
  );

  return actions;
};

// 启用RTSP转发
const handleEnableRtsp = async (record) => {
  try {
    // 确保 deviceStreamStatuses.value 始终是一个对象
    if (!deviceStreamStatuses.value) {
      deviceStreamStatuses.value = {};
    }
    createMessage.loading({content: '正在启动RTSP转发...', key: 'rtsp'});

    const response = await startStreamForwarding(record.id);
    if (response.code === 0) {
      createMessage.success({content: 'RTSP转发已启动', key: 'rtsp'});
      // 更新设备状态
      deviceStreamStatuses.value[record.id] = 'running';
      // 更新卡片列表中的流状态
      if (deviceMixedCardListRef.value && deviceMixedCardListRef.value.deviceStreamStatuses) {
        deviceMixedCardListRef.value.deviceStreamStatuses[record.id] = 'running';
      }
      // 重新加载数据
      handleSuccess();
    } else {
      createMessage.error({content: `启动失败: ${response.data.msg}`, key: 'rtsp'});
      deviceStreamStatuses.value[record.id] = 'error';
    }
  } catch (error) {
    console.error('启动RTSP转发失败', error);
    createMessage.error({content: '启动RTSP转发失败', key: 'rtsp'});
    // 确保 deviceStreamStatuses.value 始终是一个对象
    if (!deviceStreamStatuses.value) {
      deviceStreamStatuses.value = {};
    }
    deviceStreamStatuses.value[record.id] = 'error';
  }
};

// 表格刷新
function handlePlayerSuccess() {
}

// 停止RTSP转发
const handleDisableRtsp = async (record) => {
  try {
    // 确保 deviceStreamStatuses.value 始终是一个对象
    if (!deviceStreamStatuses.value) {
      deviceStreamStatuses.value = {};
    }
    createMessage.loading({content: '正在停止RTSP转发...', key: 'rtsp'});

    const response = await stopStreamForwarding(record.id);
    if (response.code === 0) {
      createMessage.success({content: 'RTSP转发已停止', key: 'rtsp'});
      // 更新设备状态
      deviceStreamStatuses.value[record.id] = 'stopped';
      // 更新卡片列表中的流状态
      if (deviceMixedCardListRef.value && deviceMixedCardListRef.value.deviceStreamStatuses) {
        deviceMixedCardListRef.value.deviceStreamStatuses[record.id] = 'stopped';
      }
      // 重新加载数据
      handleSuccess();
    } else {
      createMessage.error({content: `停止失败: ${response.data.msg}`, key: 'rtsp'});
      deviceStreamStatuses.value[record.id] = 'error';
    }
  } catch (error) {
    console.error('停止RTSP转发失败', error);
    createMessage.error({content: '停止RTSP转发失败', key: 'rtsp'});
    // 确保 deviceStreamStatuses.value 始终是一个对象
    if (!deviceStreamStatuses.value) {
      deviceStreamStatuses.value = {};
    }
    deviceStreamStatuses.value[record.id] = 'error';
  }
};

function handlePlay(record: DeviceInfo) {
  if (!openDeviceInDialogPlayer(openPlayerAddModel, record)) {
    createMessage.warning('该设备暂无可播放地址');
  }
}

function handlePlayAIStream(record: DeviceInfo) {
  if (!openDeviceInDialogPlayer(openPlayerAddModel, record, { ai: true })) {
    createMessage.warning('该设备暂无 AI 流地址');
  }
}

async function handleCopy(text: string) {
  if (navigator.clipboard) {
    await navigator.clipboard.writeText(text);
  } else {
    const textarea = document.createElement('textarea');
    textarea.value = text;
    document.body.appendChild(textarea);
    textarea.select();
    document.execCommand('copy');
    document.body.removeChild(textarea);
  }
  createMessage.success('复制成功');
}

// 打开模态框
const openAddModal = (type, record = null) => {
  openModal(true, {
    type,
    record,
    isEdit: type === 'edit',
    isView: type === 'view'
  });
};

/** 单机实时 WS-Discovery（GET /video/camera/discovery） */
const handleScanOnvif = () => openAddModal('onvif');

/** 网段 HTTP 指纹扫描（hiktools） */
const handleScanSegmentCamera = () => openSegmentScanModal(true, { mode: 'camera' });
const handleScanSegmentNvr = () => openSegmentScanModal(true, { mode: 'nvr' });

/** 后台刷新已录入设备的 IP（POST /video/camera/refresh） */
const handleRefreshOnvifDevices = async () => {
  try {
    await refreshDevices();
    createMessage.success('设备刷新任务已启动');
  } catch (e) {
    console.error(e);
    createMessage.error('刷新失败');
  }
};

// 刷新数据
const handleSuccess = () => {
  if (viewMode.value === 'table') {
    reload();
  } else if (deviceMixedCardListRef.value) {
    deviceMixedCardListRef.value.fetch();
  }
};

// 删除设备
const handleDelete = async (record) => {
  try {
    await deleteDevice(record.id);
    createMessage.success('删除成功');
    handleSuccess();
  } catch (error) {
    console.error('删除失败', error);
    createMessage.error('删除失败');
  }
};

// 卡片视图事件处理
const handleCardView = (record) => {
  if (isGb28181SipListRow(record)) {
    handleTableViewGbDevice(record);
    return;
  }
  openAddModal('view', record);
};

const handleCardEdit = (record) => {
  if (isGb28181SipListRow(record)) {
    handleTableEditGbDevice(record);
    return;
  }
  openAddModal('edit', record);
};

const handleCardDelete = async (record) => {
  await handleDelete(record);
};

const handleCardPlay = (record) => {
  handlePlay(record);
};

const handleCardPlayAI = (record) => {
  handlePlayAIStream(record);
};

const handleCardToggleStream = async (record) => {
  const currentStatus = (deviceStreamStatuses.value && deviceStreamStatuses.value[record.id]) || 'unknown';
  if (currentStatus === 'running') {
    await handleDisableRtsp(record);
  } else {
    await handleEnableRtsp(record);
  }
  // 刷新卡片列表
  if (deviceMixedCardListRef.value) {
    deviceMixedCardListRef.value.fetch();
  }
};


// 组件挂载时启动状态检查定时器
onMounted(() => {
  // 已禁用自动状态检查定时器
  // startStatusCheckTimer();
  // 处理路由参数，自动切换到指定tab
  const rawTab = route.query.tab as string;
  if (rawTab === '3' || route.query.mode === 'config') {
    splitScreenInitialMode.value = 'config';
  }
  if (rawTab) {
    state.activeKey = normalizeCameraRouteTab(rawTab);
  } else if (route.query.mode === 'config') {
    state.activeKey = CAMERA_TAB_KEYS.SPLIT_MONITOR;
  }
});

// 组件卸载时清除定时器
onUnmounted(() => {
  if (statusCheckTimer.value) {
    clearInterval(statusCheckTimer.value);
    statusCheckTimer.value = null;
  }
});
</script>

<style lang="less" scoped>
.camera-container {
  :deep(.ant-form-item) {
    margin-bottom: 10px;
  }

  .camera-tab {
    padding: 16px 19px 0 15px;

    :deep(.ant-tabs-nav) {
      padding: 5px 0 0 25px;
    }

    :deep(.ant-tabs) {
      background-color: #FFFFFF;

      :deep(.ant-tabs-nav) {
        padding: 5px 0 0 25px;
      }
    }
  }

  // 工具栏按钮组
  .toolbar-buttons {
    display: flex;
    align-items: center;
    gap: 8px;
  }

}
</style>
