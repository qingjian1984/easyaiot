<template>
  <BasicModal
    @register="register"
    :title="modalTitle"
    :width="1100"
    :canFullscreen="true"
    :showOkBtn="false"
    cancelText="关闭"
    @cancel="handleClose"
  >
    <Spin :spinning="state.scanning || state.enumerating || state.registering">
      <div class="segment-scan-modal">
        <Alert
          type="info"
          show-icon
          class="scan-tip"
          message="填写网段与 Web 登录凭证后扫描。支持 CIDR（如 192.168.1.0/24）、IP 范围（10.0.0.1-50）、单 IP 及 IP:端口。"
        />
        <Form layout="vertical" class="scan-form">
          <Row :gutter="16">
            <Col :span="24">
              <FormItem label="扫描目标" required>
                <Input.TextArea
                  v-model:value="form.targets"
                  :rows="3"
                  placeholder="示例：&#10;192.168.1.0/24&#10;10.0.0.1-10.0.0.50&#10;1.2.3.4:8080"
                  :disabled="state.scanning"
                />
              </FormItem>
            </Col>
            <Col :span="8">
              <FormItem label="端口">
                <Input v-model:value="form.ports" placeholder="80,443,8000,8443" :disabled="state.scanning" />
              </FormItem>
            </Col>
            <Col :span="8">
              <FormItem label="用户名" required>
                <Input v-model:value="form.username" placeholder="admin" :disabled="state.scanning" />
              </FormItem>
            </Col>
            <Col :span="8">
              <FormItem label="密码" required>
                <Input.Password v-model:value="form.password" :disabled="state.scanning" />
              </FormItem>
            </Col>
            <Col :span="6">
              <FormItem label="并发数">
                <InputNumber v-model:value="form.concurrency" :min="1" :max="2000" style="width: 100%" />
              </FormItem>
            </Col>
            <Col :span="6">
              <FormItem label="超时(秒)">
                <InputNumber v-model:value="form.timeout" :min="0.5" :max="60" :step="0.5" style="width: 100%" />
              </FormItem>
            </Col>
            <Col :span="12">
              <FormItem label=" ">
                <Checkbox v-model:checked="form.only_hits">仅显示已识别的摄像头/录像机</Checkbox>
              </FormItem>
            </Col>
          </Row>
          <div class="scan-actions">
            <a-button type="primary" :loading="state.scanning" @click="handleScan">
              <template #icon><SearchOutlined /></template>
              开始扫描
            </a-button>
            <a-button v-if="state.mode === 'nvr' && state.nvrInventory" @click="backToNvrList">返回 NVR 列表</a-button>
            <span v-if="state.scanProgress" class="progress-text">{{ state.scanProgress }}</span>
          </div>
        </Form>

        <div v-if="state.mode === 'camera' && state.devices.length" class="result-section">
          <div class="result-title">扫描结果（{{ state.devices.length }}）</div>
          <Table
            :columns="cameraColumns"
            :data-source="state.devices"
            :pagination="{ pageSize: 10 }"
            row-key="ip"
            size="small"
            bordered
          >
            <template #bodyCell="{ column, record }">
              <template v-if="column.dataIndex === 'action'">
                <a-button
                  type="link"
                  size="small"
                  :disabled="!record.rtsp_url && !record.is_recognized"
                  @click="handleRegisterCamera(record)"
                >
                  注册
                </a-button>
              </template>
            </template>
          </Table>
        </div>

        <template v-if="state.mode === 'nvr'">
          <div v-if="!state.nvrInventory && state.devices.length" class="result-section">
            <div class="result-title">发现的 NVR（{{ state.devices.length }}）</div>
            <Table
              :columns="nvrColumns"
              :data-source="state.devices"
              :pagination="{ pageSize: 10 }"
              row-key="ip"
              size="small"
              bordered
            >
              <template #bodyCell="{ column, record }">
                <template v-if="column.dataIndex === 'action'">
                  <a-button type="link" size="small" @click="handleRegisterNvrOnly(record)">登记NVR</a-button>
                  <a-button type="link" size="small" @click="handleEnumerate(record)">枚举通道</a-button>
                </template>
              </template>
            </Table>
          </div>

          <div v-if="state.nvrInventory" class="result-section">
            <div class="result-title">
              NVR {{ state.nvrInventory.nvr_ip }} — 下属摄像头（{{ channelRows.length }}）
            </div>
            <p v-if="state.nvrInventory.error" class="nvr-warn">{{ state.nvrInventory.error }}</p>
            <Table
              :columns="channelColumns"
              :data-source="channelRows"
              :pagination="{ pageSize: 10 }"
              row-key="channel_id"
              size="small"
              bordered
            >
              <template #bodyCell="{ column, record }">
                <template v-if="column.dataIndex === 'action'">
                  <a-button
                    type="link"
                    size="small"
                    :disabled="!record.rtsp_url && !record.rtsp_direct"
                    @click="handleRegisterChannel(record)"
                  >
                    注册
                  </a-button>
                </template>
              </template>
            </Table>
          </div>
        </template>
      </div>
    </Spin>
  </BasicModal>
</template>

<script lang="ts" setup>
import { computed, reactive } from 'vue';
import {
  Alert,
  Checkbox,
  Col,
  Form,
  FormItem,
  Input,
  InputNumber,
  Row,
  Spin,
  Table,
} from 'ant-design-vue';
import { SearchOutlined } from '@ant-design/icons-vue';
import { BasicModal, useModalInner } from '@/components/Modal';
import { useMessage } from '@/hooks/web/useMessage';
import {
  enumerateNvrChannels,
  registerDevice,
  upsertNvr,
  scanSegmentDevices,
  type NvrChannelRow,
  type NvrInventoryResult,
  type SegmentScanDeviceRow,
} from '@/api/device/camera';
import {
  getCameraScanColumns,
  getNvrChannelColumns,
  getNvrScanColumns,
} from './Data';

defineOptions({ name: 'SegmentScanModal' });

const props = defineProps<{
  mode?: 'camera' | 'nvr';
}>();

const emit = defineEmits(['success']);

const { createMessage } = useMessage();

const state = reactive({
  mode: 'camera' as 'camera' | 'nvr',
  scanning: false,
  enumerating: false,
  registering: false,
  devices: [] as SegmentScanDeviceRow[],
  nvrInventory: null as NvrInventoryResult | null,
  scanProgress: '',
});

const form = reactive({
  targets: '',
  ports: '80,443,8000,8443',
  username: 'admin',
  password: '',
  concurrency: 200,
  timeout: 5,
  only_hits: true,
});

const cameraColumns = getCameraScanColumns();
const nvrColumns = getNvrScanColumns();
const channelColumns = getNvrChannelColumns();

const modalTitle = computed(() =>
  state.mode === 'nvr' ? '通过网段注册 NVR' : '通过网段注册摄像头',
);

const channelRows = computed(() => {
  const inv = state.nvrInventory;
  if (!inv?.channels) return [];
  return inv.channels.map((ch) => ({
    ...ch,
    online_text:
      ch.online === true ? '在线' : ch.online === false ? '离线' : '—',
  }));
});

const [register, { closeModal }] = useModalInner((data?: { mode?: 'camera' | 'nvr' }) => {
  state.mode = data?.mode || props.mode || 'camera';
  state.devices = [];
  state.nvrInventory = null;
  state.scanProgress = '';
});

function vendorToCameraType(vendor?: string): string {
  if (vendor === 'hikvision') return 'hikvision';
  if (vendor === 'dahua') return 'dahua';
  return 'custom';
}

async function handleScan() {
  if (!form.targets.trim()) {
    createMessage.warning('请填写扫描目标');
    return;
  }
  if (!form.username.trim()) {
    createMessage.warning('请填写用户名');
    return;
  }
  state.scanning = true;
  state.scanProgress = '正在扫描，请稍候…';
  state.devices = [];
  state.nvrInventory = null;
  try {
    const res = await scanSegmentDevices({
      targets: form.targets.trim(),
      username: form.username.trim(),
      password: form.password,
      ports: form.ports.trim() || undefined,
      concurrency: form.concurrency,
      timeout: form.timeout,
      only_hits: form.only_hits,
      nvr_only: state.mode === 'nvr',
      exclude_nvr: state.mode === 'camera',
    });
    const list = (res as { data?: SegmentScanDeviceRow[] })?.data ?? (res as SegmentScanDeviceRow[]) ?? [];
    state.devices = Array.isArray(list) ? list : [];
    if (!state.devices.length) {
      createMessage.info(state.mode === 'nvr' ? '未发现 NVR 设备' : '未发现可识别设备');
    } else {
      createMessage.success(`扫描完成，共 ${state.devices.length} 台`);
    }
  } catch (e: unknown) {
    const err = e as { msg?: string; message?: string };
    createMessage.error(err?.msg || err?.message || '扫描失败');
  } finally {
    state.scanning = false;
    state.scanProgress = '';
  }
}

async function handleRegisterNvrOnly(record: SegmentScanDeviceRow) {
  state.registering = true;
  try {
    await upsertNvr({
      ip: record.ip,
      port: record.port || 80,
      username: form.username.trim(),
      password: form.password,
      name: record.device_name,
      model: record.model,
      vendor: record.vendor,
      serial_number: record.serial,
      rtsp_url: record.rtsp_url,
      scheme: record.port && [443, 8443].includes(record.port) ? 'https' : 'http',
    });
    createMessage.success('NVR 已登记');
    emit('success');
  } catch (e: unknown) {
    const err = e as { msg?: string; message?: string };
    createMessage.error(err?.msg || err?.message || '登记失败');
  } finally {
    state.registering = false;
  }
}

async function handleEnumerate(record: SegmentScanDeviceRow) {
  state.enumerating = true;
  try {
    const res = await enumerateNvrChannels({
      ip: record.ip,
      port: record.port || 80,
      username: form.username.trim(),
      password: form.password,
      timeout: form.timeout,
      vendor: record.vendor,
    });
    const inv = (res as { data?: NvrInventoryResult })?.data ?? (res as NvrInventoryResult);
    state.nvrInventory = inv;
    if (!inv?.channels?.length) {
      createMessage.warning(inv?.error || '未枚举到通道');
    }
  } catch (e: unknown) {
    const err = e as { msg?: string; message?: string };
    createMessage.error(err?.msg || err?.message || '枚举失败');
  } finally {
    state.enumerating = false;
  }
}

function backToNvrList() {
  state.nvrInventory = null;
}

async function handleRegisterCamera(record: SegmentScanDeviceRow) {
  const source = record.rtsp_url;
  if (!source) {
    createMessage.warning('无 RTSP 地址，请确认凭证正确或设备已识别');
    return;
  }
  state.registering = true;
  try {
    await registerDevice({
      name: record.device_name || `${record.vendor_label || '设备'}-${record.ip}`,
      source,
      ip: record.ip,
      port: 554,
      username: form.username.trim(),
      password: form.password,
      cameraType: vendorToCameraType(record.vendor),
      stream: 0,
      manufacturer: record.vendor_label,
      model: record.model,
      serial_number: record.serial,
    });
    createMessage.success('注册成功');
    emit('success');
  } catch (e: unknown) {
    const err = e as { msg?: string; message?: string };
    createMessage.error(err?.msg || err?.message || '注册失败');
  } finally {
    state.registering = false;
  }
}

async function handleRegisterChannel(ch: NvrChannelRow & { online_text?: string }) {
  const inv = state.nvrInventory;
  if (!inv) return;
  const source = ch.rtsp_url || ch.rtsp_direct;
  if (!source) {
    createMessage.warning('该通道无 RTSP 地址');
    return;
  }
  state.registering = true;
  try {
    const nvrRow = await upsertNvr({
      ip: inv.nvr_ip,
      port: inv.nvr_port || 80,
      username: form.username.trim(),
      password: form.password,
      name: inv.nvr_device_name,
      model: inv.nvr_model,
      vendor: inv.nvr_vendor,
      serial_number: inv.nvr_serial,
      scheme: inv.nvr_port && [443, 8443].includes(inv.nvr_port) ? 'https' : 'http',
    });
    const nvrId = (nvrRow as { id?: number })?.id;
    await registerDevice({
      name: ch.name || `NVR-${inv.nvr_ip}-CH${ch.channel_id}`,
      source,
      ip: ch.camera_ip || inv.nvr_ip,
      port: ch.camera_port || 554,
      username: form.username.trim(),
      password: form.password,
      cameraType: vendorToCameraType(ch.vendor || inv.nvr_vendor),
      stream: 0,
      model: ch.model,
      serial_number: ch.serial,
      nvr_id: nvrId,
      nvr_channel: ch.channel_id,
      rtsp_direct: ch.rtsp_direct,
      channel_online: ch.online,
      connection_status: ch.connection_status || ch.probe_error,
    });
    createMessage.success('通道注册成功');
    emit('success');
  } catch (e: unknown) {
    const err = e as { msg?: string; message?: string };
    createMessage.error(err?.msg || err?.message || '注册失败');
  } finally {
    state.registering = false;
  }
}

function handleClose() {
  state.devices = [];
  state.nvrInventory = null;
  closeModal();
}
</script>

<style lang="less" scoped>
.segment-scan-modal {
  .scan-tip {
    margin-bottom: 12px;
  }
  .scan-form {
    margin-bottom: 8px;
  }
  .scan-actions {
    display: flex;
    align-items: center;
    gap: 12px;
    margin-bottom: 16px;
    .progress-text {
      color: #666;
      font-size: 13px;
    }
  }
  .result-section {
    margin-top: 8px;
    .result-title {
      font-weight: 600;
      margin-bottom: 8px;
    }
    .nvr-warn {
      color: #fa8c16;
      margin-bottom: 8px;
    }
  }
}
</style>
