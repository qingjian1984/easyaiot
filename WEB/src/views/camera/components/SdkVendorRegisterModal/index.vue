<template>
  <BasicModal
    v-bind="$attrs"
    @register="register"
    :title="modalTitle"
    width="560"
    @ok="handleSubmit"
    :confirmLoading="loading"
  >
    <BasicForm @register="registerForm" />
  </BasicModal>
</template>

<script lang="ts" setup>
import { computed, ref } from 'vue';
import { BasicForm, useForm } from '@/components/Form';
import { BasicModal, useModalInner } from '@/components/Modal';
import { useMessage } from '@/hooks/web/useMessage';
import {
  fetchDahuaSdkDeviceInfo,
  fetchHikSdkDeviceInfo,
  type SdkVendor,
} from '@/api/device/iot_video_sdk';
import { registerDevice } from '@/api/device/camera';
import { ensureDeviceStreamForwardTask } from '@/api/device/stream_forward';

defineOptions({ name: 'SdkVendorRegisterModal' });

const emit = defineEmits(['success', 'register']);

const { createMessage } = useMessage();
const loading = ref(false);
const vendor = ref<SdkVendor>('dahua');

const modalTitle = computed(() =>
  vendor.value === 'dahua' ? '大华 SDK 注册设备' : '海康 SDK 注册设备',
);

const [registerForm, { validate, resetFields, setFieldsValue }] = useForm({
  labelWidth: 100,
  schemas: [
    {
      field: 'name',
      label: '设备名称',
      component: 'Input',
      required: true,
      componentProps: { placeholder: '请输入设备名称' },
    },
    {
      field: 'ip',
      label: '设备 IP',
      component: 'Input',
      required: true,
      rules: [{ required: true, message: '请输入 IP' }],
    },
    {
      field: 'port',
      label: '端口',
      component: 'InputNumber',
      required: true,
      componentProps: { min: 1, max: 65535, style: { width: '100%' } },
    },
    {
      field: 'username',
      label: '用户名',
      component: 'Input',
      required: true,
    },
    {
      field: 'password',
      label: '密码',
      component: 'InputPassword',
      required: true,
    },
    {
      field: 'stream',
      label: '码流',
      component: 'Select',
      defaultValue: 0,
      componentProps: {
        options: [
          { label: '主码流', value: 0 },
          { label: '子码流', value: 1 },
        ],
      },
    },
  ],
  showActionButtonGroup: false,
});

const [register, { closeModal, setModalProps }] = useModalInner(async (data: { vendor: SdkVendor }) => {
  vendor.value = data?.vendor || 'dahua';
  resetFields();
  const defaultPort = vendor.value === 'dahua' ? 37777 : 8000;
  setFieldsValue({
    port: defaultPort,
    username: 'admin',
    name: '',
    ip: '',
    password: '',
    stream: 0,
  });
});

function buildRtsp(v: SdkVendor, values: any): string {
  const ip = values.ip as string;
  const port = Number(values.port);
  const user = values.username as string;
  const pass = values.password as string;
  const stream = Number(values.stream ?? 0);
  if (v === 'hikvision') {
    const streamType = stream === 0 ? 1 : 2;
    return `rtsp://${user}:${pass}@${ip}:${port}/Streaming/Channels/101${streamType}`;
  }
  const subtype = stream === 0 ? 0 : 1;
  return `rtsp://${user}:${pass}@${ip}:${port}/cam/realmonitor?channel=1&subtype=${subtype}`;
}

async function handleSubmit() {
  try {
    const values = await validate();
    loading.value = true;
    const payload = {
      ip: values.ip,
      port: Number(values.port),
      username: values.username,
      password: values.password,
    };
    const sdkRes =
      vendor.value === 'dahua'
        ? await fetchDahuaSdkDeviceInfo(payload)
        : await fetchHikSdkDeviceInfo(payload);
    const raw = sdkRes as any;
    const httpCode = raw?.code;
    if (httpCode !== undefined && httpCode !== 200 && httpCode !== 0) {
      createMessage.error(raw?.msg || 'SDK 获取设备信息失败');
      return;
    }
    const info = (raw?.data ?? raw) as Record<string, unknown>;
    if (!info || typeof info !== 'object') {
      createMessage.error('SDK 返回数据异常');
      return;
    }

    const source = buildRtsp(vendor.value, values);
    const serial = String(info.serialNumber ?? '').trim();
    const registerPayload: Parameters<typeof registerDevice>[0] = {
      name: values.name as string,
      source,
      stream: Number(values.stream ?? 0),
      cameraType: vendor.value,
      ip: payload.ip,
      port: payload.port,
      username: payload.username,
      password: payload.password,
      manufacturer: vendor.value === 'dahua' ? '大华' : '海康威视',
      serial_number: serial || undefined,
      model: vendor.value === 'dahua' ? 'Dahua-IPC' : 'Hikvision-IPC',
    };

    const reg = await registerDevice(registerPayload as any);
    const deviceId = (reg as any)?.data?.id;
    createMessage.success('设备注册成功');
    if (deviceId) {
      try {
        await ensureDeviceStreamForwardTask(deviceId);
      } catch {
        /* 不影响主流程 */
      }
    }
    closeModal();
    emit('success');
  } catch (e: any) {
    createMessage.error(e?.message || e?.msg || '操作失败');
  } finally {
    loading.value = false;
  }
}
</script>
