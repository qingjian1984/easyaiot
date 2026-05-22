<template>
  <BasicDrawer
    v-bind="$attrs"
    @register="register"
    :title="drawerTitle"
    width="1280"
    placement="right"
    :showFooter="true"
    :showOkBtn="false"
    cancelText="关闭"
    @close="handleClose"
  >
    <Spin :spinning="state.scanning || state.registering">
      <div class="segment-scan-modal">
        <Alert
          type="info"
          show-icon
          class="scan-tip"
          message="填写网段与 Web 登录凭证后扫描；可添加多组用户名密码，将按从上到下的顺序依次尝试。支持 CIDR（如 192.168.1.0/24）、IP 范围（10.0.0.1-50）、单 IP 及 IP:端口。"
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
            <Col :span="24">
              <FormItem label="端口">
                <Input v-model:value="form.ports" placeholder="80,443,8000,8443" :disabled="state.scanning" />
              </FormItem>
            </Col>
            <Col :span="24">
              <FormItem label="Web 登录凭证" required>
                <div class="credentials-block">
                  <div
                    v-for="(cred, idx) in form.credentials"
                    :key="idx"
                    class="credential-row"
                  >
                    <span class="cred-order">{{ idx + 1 }}</span>
                    <Input
                      v-model:value="cred.username"
                      placeholder="用户名"
                      :disabled="state.scanning"
                      class="cred-user"
                    />
                    <Input.Password
                      v-model:value="cred.password"
                      placeholder="密码"
                      :disabled="state.scanning"
                      class="cred-pass"
                    />
                    <a-button
                      type="link"
                      danger
                      size="small"
                      :disabled="state.scanning || form.credentials.length <= 1"
                      @click="removeCredential(idx)"
                    >
                      删除
                    </a-button>
                  </div>
                  <a-button type="dashed" block :disabled="state.scanning" @click="addCredential">
                    添加凭证
                  </a-button>
                  <div class="cred-hint">按列表顺序从上到下依次尝试，留空用户名的行将被忽略</div>
                </div>
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
            <span v-if="state.scanProgress" class="progress-text">{{ state.scanProgress }}</span>
          </div>
        </Form>

        <Alert
          v-if="hasScanResult"
          type="success"
          show-icon
          class="result-hint"
          :message="resultHintText"
        >
          <template #action>
            <a-button type="primary" size="small" @click="openResultModal(true)">查看扫描结果</a-button>
          </template>
        </Alert>
      </div>
    </Spin>
  </BasicDrawer>

  <BasicModal
    @register="registerResultModal"
    :title="resultModalTitle"
    :width="1500"
    :canFullscreen="true"
    :showOkBtn="false"
    cancelText="关闭"
    :destroyOnClose="false"
    @cancel="handleResultModalClose"
  >
    <Spin :spinning="state.registering">
      <div class="segment-scan-result-modal">
        <Table
          v-if="resultTableKind === 'camera'"
          :columns="cameraColumns"
          :data-source="state.devices"
          :pagination="tablePagination"
          :scroll="{ x: 1200 }"
          row-key="ip"
          size="middle"
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

        <Table
          v-else-if="resultTableKind === 'nvr'"
          :columns="nvrColumns"
          :data-source="state.devices"
          :pagination="tablePagination"
          :scroll="{ x: 1100 }"
          row-key="ip"
          size="middle"
          bordered
        >
          <template #bodyCell="{ column, record }">
            <template v-if="column.dataIndex === 'action'">
              <a-button type="link" size="small" @click="handleRegisterNvrWithChannels(record)">登记NVR及通道</a-button>
            </template>
          </template>
        </Table>
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
import { BasicDrawer, useDrawerInner } from '@/components/Drawer';
import { BasicModal, useModal } from '@/components/Modal';
import { useMessage } from '@/hooks/web/useMessage';
import {
  registerDevice,
  registerNvrWithChannels,
  type NvrInfo,
  scanSegmentDevices,
  type CredentialPair,
  type SegmentScanDeviceRow,
} from '@/api/device/camera';
import {
  getCameraScanColumns,
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
  registering: false,
  devices: [] as SegmentScanDeviceRow[],
  scanProgress: '',
});

const form = reactive({
  targets: '',
  ports: '80,443,8000,8443',
  credentials: [{ username: 'admin', password: '' }] as CredentialPair[],
  concurrency: 200,
  timeout: 5,
  only_hits: true,
});

function getValidCredentials(): CredentialPair[] {
  return form.credentials
    .map((c) => ({ username: (c.username || '').trim(), password: c.password || '' }))
    .filter((c) => c.username);
}

function resolveCredential(authUsername?: string): CredentialPair {
  const list = getValidCredentials();
  if (authUsername) {
    const found = list.find((c) => c.username === authUsername);
    if (found) return found;
  }
  return list[0];
}

function addCredential() {
  form.credentials.push({ username: '', password: '' });
}

function removeCredential(index: number) {
  if (form.credentials.length <= 1) return;
  form.credentials.splice(index, 1);
}

const cameraColumns = getCameraScanColumns();
const nvrColumns = getNvrScanColumns();

function segmentScanDrawerTitle(mode: 'camera' | 'nvr') {
  return mode === 'nvr' ? '通过网段注册 NVR' : '通过网段注册摄像头';
}

const drawerTitle = computed(() => segmentScanDrawerTitle(state.mode));

const tablePagination = {
  pageSize: 20,
  showSizeChanger: true,
  pageSizeOptions: ['10', '20', '50', '100'],
  showQuickJumper: true,
  showTotal: (total: number) => `共 ${total} 条`,
};

const [registerResultModal, { openModal: openResultModal, closeModal: closeResultModal }] = useModal();

const resultTableKind = computed<'camera' | 'nvr'>(() =>
  state.mode === 'camera' ? 'camera' : 'nvr',
);

const hasScanResult = computed(() => state.devices.length > 0);

const resultHintText = computed(() => {
  const unit = state.mode === 'nvr' ? '台 NVR' : '台摄像头';
  return `已发现 ${state.devices.length} ${unit}，可在弹窗中分页查看并注册`;
});

const resultModalTitle = computed(() => {
  if (state.mode === 'camera') {
    return `扫描结果 — 摄像头（${state.devices.length}）`;
  }
  return `扫描结果 — NVR（${state.devices.length}）`;
});

const [register, { closeDrawer, setDrawerProps }] = useDrawerInner((data?: { mode?: 'camera' | 'nvr' }) => {
  const mode = data?.mode || props.mode || 'camera';
  state.mode = mode;
  state.devices = [];
  state.scanProgress = '';
  setDrawerProps({ title: segmentScanDrawerTitle(mode) });
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
  const credentials = getValidCredentials();
  if (!credentials.length) {
    createMessage.warning('请至少填写一组用户名');
    return;
  }
  state.scanning = true;
  state.scanProgress = '正在扫描，请稍候…';
  state.devices = [];
  try {
    const res = await scanSegmentDevices({
      targets: form.targets.trim(),
      credentials,
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
      openResultModal(true);
    }
  } catch (e: unknown) {
    const err = e as { msg?: string; message?: string };
    createMessage.error(err?.msg || err?.message || '扫描失败');
  } finally {
    state.scanning = false;
    state.scanProgress = '';
  }
}

async function handleRegisterNvrWithChannels(record: SegmentScanDeviceRow) {
  const cred = resolveCredential(record.auth_username);
  const credentials = getValidCredentials();
  state.registering = true;
  try {
    const res = await registerNvrWithChannels({
      ip: record.ip,
      port: record.port || 80,
      username: cred.username,
      password: cred.password,
      credentials,
      timeout: form.timeout,
      vendor: record.vendor,
      name: record.device_name,
      model: record.model,
      serial_number: record.serial,
      rtsp_url: record.rtsp_url,
      scheme: record.port && [443, 8443].includes(record.port) ? 'https' : 'http',
    });
    const stats = (res as { stats?: { registered?: number; skipped?: number } })?.stats;
    const n = stats?.registered ?? (res as NvrInfo)?.camera_count ?? 0;
    createMessage.success(`NVR 已登记，已挂载 ${n} 路通道`);
    emit('success');
  } catch (e: unknown) {
    const err = e as { msg?: string; message?: string };
    createMessage.error(err?.msg || err?.message || '登记失败');
  } finally {
    state.registering = false;
  }
}

async function handleRegisterCamera(record: SegmentScanDeviceRow) {
  const source = record.rtsp_url;
  if (!source) {
    createMessage.warning('无 RTSP 地址，请确认凭证正确或设备已识别');
    return;
  }
  const cred = resolveCredential(record.auth_username);
  state.registering = true;
  try {
    await registerDevice({
      name: record.device_name || `${record.vendor_label || '设备'}-${record.ip}`,
      source,
      ip: record.ip,
      port: 554,
      username: cred.username,
      password: cred.password,
      cameraType: 'custom',
      skip_onvif: true,
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

function handleResultModalClose() {
  closeResultModal();
}

function handleClose() {
  closeResultModal();
  state.devices = [];
  closeDrawer();
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
  .credentials-block {
    .credential-row {
      display: flex;
      align-items: center;
      gap: 8px;
      margin-bottom: 8px;
      .cred-order {
        flex: 0 0 20px;
        text-align: center;
        color: #999;
        font-size: 12px;
      }
      .cred-user {
        flex: 1;
        min-width: 120px;
      }
      .cred-pass {
        flex: 1;
        min-width: 120px;
      }
    }
    .cred-hint {
      margin-top: 6px;
      color: #999;
      font-size: 12px;
    }
  }
  .result-hint {
    margin-top: 12px;
  }
}

</style>
