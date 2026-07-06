<template>
  <BasicModal
    @register="register"
    :title="modalTitle"
    :footer="null"
    :width="layoutWidth"
    :canFullscreen="true"
    :defaultFullscreen="true"
    :zIndex="10000"
    wrapClassName="monitor-dialog-wrap"
    @cancel="handleCancel"
  >
    <div class="monitor-dialog" :class="{ 'monitor-dialog--vod': state.vodMode }">
      <MonitorControlPanel
        v-if="showControlPanel"
        :talk-protocol="talkProtocol"
        :talk-status="activeTalk.status"
        :talk-info-text="activeTalk.infoText"
        :talk-volume="activeTalk.volume"
        :talk-noise-suppression="activeTalk.noiseSuppression"
        :talk-echo-cancellation="activeTalk.echoCancellation"
        :talk-level="activeTalk.level"
        :show-presets="state.isGb28181 || state.isOnvif"
        :presets="state.presets"
        :preset-loading="state.presetLoading"
        @talk-start="handleStartTalk"
        @talk-stop="handleStopTalk"
        @talk-volume-change="activeTalk.updateVolume"
        @talk-noise-change="(v) => (activeTalk.noiseSuppression = v)"
        @talk-echo-change="(v) => (activeTalk.echoCancellation = v)"
        @ptz="handlePtzCamera"
        @aux="handleAuxControl"
        @preset-call="handlePresetCall"
        @preset-set="handlePresetSet"
        @preset-delete="handlePresetDelete"
        @preset-add="handlePresetAdd"
      />

      <div class="monitor-dialog__main">
        <div class="monitor-dialog__video">
          <div v-if="!state.vodMode" class="monitor-dialog__video-bar">
            <div class="monitor-dialog__video-bar-left">
              <span class="monitor-dialog__live-tag" :class="{ 'is-live': !state.vodMode && state.currentUrl }">
                <span class="monitor-dialog__live-dot" />
                {{ streamLabel }}
              </span>
            </div>
            <div class="monitor-dialog__video-bar-right">
              <span class="monitor-dialog__status-chip" :class="playStatusClass">{{ playStatusText }}</span>
            </div>
          </div>
          <div class="monitor-dialog__video-body">
            <Jessibuca
              v-if="state.currentUrl"
              :key="`${playerKey}-${state.currentUrl}`"
              ref="jessibucaRef"
              :playUrl="state.currentUrl"
              :hasAudio="!!talkProtocol"
              :vodMode="state.vodMode"
            />
            <div v-else-if="state.playLoading" class="monitor-dialog__loading">
              <Spin size="large" />
              <span>{{ state.vodMode ? '录像加载中...' : '正在请求点播...' }}</span>
            </div>
            <div v-else class="monitor-dialog__loading">
              <Icon icon="ant-design:video-camera-outlined" :size="48" />
              <span>暂无播放地址</span>
            </div>
          </div>
        </div>
        <div class="monitor-dialog__statusbar">
          <span>{{ state.deviceName || '摄像机' }}</span>
          <span v-if="state.currentUrl">码率 {{ bitrateText }}</span>
        </div>
      </div>
    </div>
  </BasicModal>
</template>

<script lang="ts" setup>
import { computed, nextTick, reactive, ref } from 'vue';
import { Spin } from 'ant-design-vue';
import { useModalInner } from '@/components/Modal';
import BasicModal from '@/components/Modal/src/BasicModal.vue';
import Jessibuca from '@/components/Player/module/jessibuca.vue';
import { Icon } from '@/components/Icon';
import { useMessage } from '@/hooks/web/useMessage';
import { controlPTZ, callOnvifPreset, deleteOnvifPreset, queryOnvifPresets, setOnvifPreset } from '@/api/device/camera';
import {
  addGbPreset,
  callGbPreset,
  controlGbFocus,
  controlGbIris,
  controlGbPtz,
  deleteGbPreset,
  playByDeviceAndChannel,
  queryGbPreset,
} from '@/api/device/gb28181';
import {
  formatCameraDeviceLabel,
  getGb28181PlayIds,
  shouldPlayViaGb28181,
} from '@/views/camera/utils/deviceLabel';
import { pickWvpPlayUrl } from '@/views/camera/utils/devicePlay';
import { isVodPlaybackUrl } from '@/utils/alertRecord';
import {
  resolveDeviceTalkProtocol,
  supportsMonitorControl,
  isOnvifDevice,
} from '@/views/camera/utils/deviceTalkProtocol';
import MonitorControlPanel from './monitor/MonitorControlPanel.vue';
import { useOnvifAudioTalk } from './monitor/useOnvifAudioTalk';
import { useGb28181AudioTalk } from './monitor/useGb28181AudioTalk';
import type { PresetItem } from './monitor/MonitorPresetPanel.vue';

const { createMessage } = useMessage();
const jessibucaRef = ref();
const playerKey = ref(0);

const state = reactive({
  deviceName: '',
  deviceId: '',
  deviceIdentification: '',
  channelId: '',
  presetPos: '',
  currentUrl: '',
  playLoading: false,
  vodMode: false,
  isGb28181: false,
  isOnvif: false,
  presets: [] as PresetItem[],
  presetLoading: false,
  record: null as Record<string, any> | null,
});

const talkProtocol = computed(() => resolveDeviceTalkProtocol(state.record));

const onvifTalk = useOnvifAudioTalk(() => state.deviceId);
const gbTalk = useGb28181AudioTalk(
  () => state.deviceIdentification,
  () => state.presetPos || state.channelId,
);

const audioTalk = computed(() => (talkProtocol.value === 'gb28181' ? gbTalk : onvifTalk));
const activeTalk = computed(() => audioTalk.value);

const showControlPanel = computed(() => supportsMonitorControl(state.record, state.vodMode));
const layoutWidth = computed(() => {
  if (state.vodMode) return 960;
  return 'min(1280px, 96vw)';
});
const modalTitle = computed(() => state.deviceName || '视频监控');
const streamLabel = computed(() => (state.vodMode ? '录像回放' : state.isGb28181 ? '国标通道' : '实时预览'));
const playStatusText = computed(() => {
  if (state.playLoading) return '连接中';
  if (state.currentUrl) return '已连接';
  return '未连接';
});
const playStatusClass = computed(() => {
  if (state.playLoading) return 'is-connecting';
  if (state.currentUrl) return 'is-online';
  return 'is-offline';
});
const bitrateText = ref('— kbps');

async function loadStream(record: Record<string, any>) {
  const gbIds = getGb28181PlayIds(record);
  const sipDeviceId = gbIds?.sipDeviceId ?? '';
  const channelId = gbIds?.channelId ?? '';
  const gbRecord = shouldPlayViaGb28181(record);

  state.deviceIdentification = gbRecord ? sipDeviceId : '';
  state.channelId = channelId;
  state.presetPos = gbRecord ? channelId : '';
  state.isGb28181 = gbRecord;
  state.isOnvif = gbRecord ? false : isOnvifDevice(record);

  if (gbRecord) {
    state.currentUrl = '';
    state.playLoading = true;
    try {
      const res = await playByDeviceAndChannel(sipDeviceId, channelId);
      const streamContent = res?.data?.data ?? res?.data;
      const url = pickWvpPlayUrl(streamContent) || '';
      if (url) {
        state.vodMode = false;
        state.currentUrl = url;
        playerKey.value += 1;
      } else {
        createMessage.error(streamContent?.msg || res?.data?.msg || '未获取到播放地址');
      }
    } catch {
      createMessage.error('点播失败，请检查设备连接');
    } finally {
      state.playLoading = false;
    }
    return;
  }

  state.deviceId = String(record.id ?? '');
  const streamUrl = String(record.http_stream ?? '').trim();
  if (!streamUrl && record._pendingRecord) {
    state.currentUrl = '';
    state.vodMode = false;
    state.playLoading = true;
    return;
  }

  state.playLoading = false;
  state.vodMode = isVodPlaybackUrl(streamUrl);
  await nextTick();
  state.currentUrl = streamUrl;
  if (streamUrl) playerKey.value += 1;
}

async function loadOnvifPresets() {
  if (!state.isOnvif || !state.deviceId) return;
  state.presetLoading = true;
  try {
    const res: any = await queryOnvifPresets(state.deviceId);
    const body = res?.data ?? res;
    const list = body?.data ?? body?.list ?? body;
    const items = Array.isArray(list) ? list : [];
    state.presets = items.map((item: any, idx: number) => ({
      id: String(item.token ?? item.preset_token ?? idx + 1),
      name: item.name ?? `预置点 ${idx + 1}`,
    }));
  } catch {
    state.presets = [];
  } finally {
    state.presetLoading = false;
  }
}

async function loadPresets() {
  if (state.isGb28181) {
    await loadGbPresets();
  } else if (state.isOnvif) {
    await loadOnvifPresets();
  }
}

async function loadGbPresets() {
  if (!state.isGb28181 || !state.deviceIdentification || !state.presetPos) return;
  state.presetLoading = true;
  try {
    const res: any = await queryGbPreset(state.deviceIdentification, state.presetPos);
    const body = res?.data ?? res;
    const list = body?.data ?? body?.list ?? body;
    const items = Array.isArray(list) ? list : [];
    state.presets = items.map((item: any, idx: number) => ({
      id: Number(item.presetId ?? item.id ?? idx + 1),
      name: item.presetName ?? item.name ?? `预置点 ${item.presetId ?? idx + 1}`,
    }));
  } catch {
    state.presets = Array.from({ length: 0 });
  } finally {
    state.presetLoading = false;
  }
}

async function handleStopTalk() {
  await audioTalk.value.stop();
}

async function handleStartTalk() {
  await audioTalk.value.start();
}

const [register, { closeModal }] = useModalInner(async (record) => {
  await handleStopTalk();
  state.record = record;
  state.deviceName = formatCameraDeviceLabel(record);
  state.presets = [];
  await loadStream(record);
  if (talkProtocol.value === 'onvif') {
    await onvifTalk.checkCapabilities();
  }
  await loadPresets();
});

async function handlePresetAdd() {
  if (state.isOnvif) {
    const name = `预置点 ${state.presets.length + 1}`;
    try {
      await setOnvifPreset(state.deviceId, { name });
      createMessage.success(`${name} 已添加`);
      await loadOnvifPresets();
    } catch {
      createMessage.error('添加预置点失败');
    }
    return;
  }
  const lastId = Number(state.presets[state.presets.length - 1]?.id ?? 0);
  const nextId = lastId + 1;
  if (nextId > 255) {
    createMessage.warning('预置点编号已达上限');
    return;
  }
  handlePresetSet(nextId);
}

const gbCommandMap: Record<string, string> = {
  UP: 'up',
  DOWN: 'down',
  LEFT: 'left',
  RIGHT: 'right',
  LEFT_UP: 'upleft',
  RIGHT_UP: 'upright',
  LEFT_DOWN: 'downleft',
  RIGHT_DOWN: 'downright',
  ZOOM_IN: 'zoomin',
  ZOOM_OUT: 'zoomout',
  STOP: 'stop',
};

async function handlePtzCamera(command: string, speed: number) {
  if (state.deviceIdentification && state.presetPos) {
    const gbCommand = gbCommandMap[command];
    if (!gbCommand) return;
    await controlGbPtz(state.deviceIdentification, state.presetPos, {
      command: gbCommand,
      horizonSpeed: ['LEFT', 'RIGHT', 'LEFT_UP', 'RIGHT_UP', 'LEFT_DOWN', 'RIGHT_DOWN'].includes(command)
        ? speed
        : 0,
      verticalSpeed: ['UP', 'DOWN', 'LEFT_UP', 'RIGHT_UP', 'LEFT_DOWN', 'RIGHT_DOWN'].includes(command)
        ? speed
        : 0,
      zoomSpeed: ['ZOOM_IN', 'ZOOM_OUT'].includes(command)
        ? Math.min(Math.max(Math.round(speed / 10), 1), 15)
        : 0,
    });
    return;
  }

  const directionMap: Record<string, { x: number; y: number; z: number }> = {
    UP: { x: 0, y: speed, z: 0 },
    DOWN: { x: 0, y: -speed, z: 0 },
    LEFT: { x: -speed, y: 0, z: 0 },
    RIGHT: { x: speed, y: 0, z: 0 },
    ZOOM_IN: { x: 0, y: 0, z: speed },
    ZOOM_OUT: { x: 0, y: 0, z: -speed },
  };
  if (command === 'STOP') {
    controlPTZ(state.deviceId, { x: 0, y: 0, z: 0 });
  } else if (directionMap[command]) {
    controlPTZ(state.deviceId, directionMap[command]);
  }
}

async function handleAuxControl(type: 'focus' | 'iris', action: 'in' | 'out' | 'stop') {
  if (!state.isGb28181 || !state.deviceIdentification || !state.presetPos) return;
  if (type === 'focus') {
    const cmd = action === 'in' ? 'near' : action === 'out' ? 'far' : 'stop';
    await controlGbFocus(state.deviceIdentification, state.presetPos, cmd);
  } else {
    const cmd = action === 'stop' ? 'stop' : action;
    await controlGbIris(state.deviceIdentification, state.presetPos, cmd);
  }
}

async function handlePresetCall(id: string | number) {
  if (state.isOnvif) {
    await callOnvifPreset(state.deviceId, String(id));
    return;
  }
  if (!state.deviceIdentification || !state.presetPos) return;
  await callGbPreset(state.deviceIdentification, state.presetPos, Number(id));
}

async function handlePresetSet(id: string | number) {
  if (state.isOnvif) {
    const preset = state.presets.find((p) => p.id === id);
    const name = preset?.name ?? `预置点 ${id}`;
    try {
      await setOnvifPreset(state.deviceId, {
        name,
        preset_token: String(id),
      });
      createMessage.success(`${name} 已更新`);
      await loadOnvifPresets();
    } catch {
      createMessage.error('设置预置点失败');
    }
    return;
  }
  if (!state.deviceIdentification || !state.presetPos) return;
  await addGbPreset(state.deviceIdentification, state.presetPos, Number(id));
  createMessage.success(`预置点 ${id} 已设置`);
  await loadGbPresets();
}

async function handlePresetDelete(id: string | number) {
  if (state.isOnvif) {
    try {
      await deleteOnvifPreset(state.deviceId, String(id));
      createMessage.success('预置点已删除');
      await loadOnvifPresets();
    } catch {
      createMessage.error('删除预置点失败');
    }
    return;
  }
  if (!state.deviceIdentification || !state.presetPos) return;
  await deleteGbPreset(state.deviceIdentification, state.presetPos, Number(id));
  createMessage.success(`预置点 ${id} 已删除`);
  await loadGbPresets();
}

function handleCancel() {
  handleStopTalk();
  state.currentUrl = '';
  state.playLoading = false;
  state.record = null;
  closeModal();
}
</script>

<style lang="less">
.monitor-dialog-wrap {
  .ant-modal-body {
    padding: 0 !important;
  }

  .ant-modal-header {
    padding: 12px 16px;
    border-bottom: 1px solid #e2e8f0;
  }

  .ant-modal-title {
    font-size: 15px;
    font-weight: 600;
  }

  .monitor-ptz {
    .ant-slider-track {
      background-color: #3b6cf5;
    }

    .ant-slider-handle::after {
      box-shadow: 0 0 0 2px #3b6cf5;
    }
  }

  .audio-talk {
    .ant-slider-track {
      background-color: #3b6cf5;
    }

    .ant-slider-handle::after {
      box-shadow: 0 0 0 2px #3b6cf5;
    }
  }
}

.monitor-dialog {
  display: flex;
  height: min(82vh, 780px);
  min-height: 520px;
  background: #f1f5f9;

  &--vod {
    .monitor-dialog__main {
      flex: 1;
    }
  }
}

.monitor-dialog__main {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  background: #0a0f1a;
}

.monitor-dialog__video {
  flex: 1;
  min-height: 0;
  position: relative;
  display: flex;
  flex-direction: column;
  background: #000;
}

.monitor-dialog__video-bar {
  flex-shrink: 0;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  min-height: 40px;
  padding: 0 14px;
  background: linear-gradient(180deg, rgba(15, 23, 42, 0.96), rgba(15, 23, 42, 0.88));
  border-bottom: 1px solid rgba(255, 255, 255, 0.08);
}

.monitor-dialog__video-bar-left,
.monitor-dialog__video-bar-right {
  display: flex;
  align-items: center;
  gap: 8px;
  min-width: 0;
}

.monitor-dialog__live-tag {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 4px 10px;
  border-radius: 999px;
  background: rgba(255, 255, 255, 0.08);
  color: rgba(255, 255, 255, 0.88);
  font-size: 12px;
  white-space: nowrap;
  line-height: 1;

  &.is-live {
    background: rgba(59, 108, 245, 0.18);
    color: #93bbfd;
  }
}

.monitor-dialog__live-dot {
  width: 7px;
  height: 7px;
  border-radius: 50%;
  background: #64748b;
  flex-shrink: 0;

  .is-live & {
    background: #5b8df7;
    box-shadow: 0 0 0 3px rgba(91, 141, 247, 0.28);
  }
}

.monitor-dialog__status-chip {
  display: inline-flex;
  align-items: center;
  padding: 3px 10px;
  border-radius: 999px;
  font-size: 11px;
  white-space: nowrap;
  line-height: 1.2;
  background: rgba(255, 255, 255, 0.08);
  color: rgba(255, 255, 255, 0.72);

  &.is-online {
    background: rgba(59, 108, 245, 0.2);
    color: #93bbfd;
  }

  &.is-connecting {
    background: rgba(245, 158, 11, 0.18);
    color: #fcd34d;
  }

  &.is-offline {
    background: rgba(148, 163, 184, 0.16);
    color: #cbd5e1;
  }
}

.monitor-dialog__video-body {
  flex: 1;
  min-height: 0;
  position: relative;
}

.monitor-dialog__loading {
  height: 100%;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 12px;
  color: rgba(255, 255, 255, 0.75);
  font-size: 14px;
}

.monitor-dialog__statusbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 8px 14px;
  background: rgba(15, 23, 42, 0.96);
  color: rgba(255, 255, 255, 0.78);
  font-size: 12px;
  border-top: 1px solid rgba(255, 255, 255, 0.08);

  span:first-child {
    min-width: 0;
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
  }

  span:last-child {
    flex-shrink: 0;
    white-space: nowrap;
  }
}
</style>
