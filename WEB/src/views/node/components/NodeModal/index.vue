<script lang="ts" setup>
import { computed, ref, unref } from 'vue';
import { Spin } from 'ant-design-vue';
import { formSchema } from '../../Data';
import { useMessage } from '@/hooks/web/useMessage';
import { BasicDrawer, useDrawerInner } from '@/components/Drawer';
import { BasicForm, useForm } from '@/components/Form';
import { Button } from '@/components/Button';
import { createNode, updateNode, type ComputeNodeVO } from '@/api/device/node';
import { generateDefaultAgentPort } from '../../utils/constants';
import {
  nodeFormHistoryToFields,
  saveNodeFormHistory,
  valuesToNodeFormHistoryEntry,
  type NodeFormHistoryEntry,
} from '../../utils/nodeFormHistory';
import NodeNameField from '../NodeNameField/index.vue';

defineOptions({ name: 'NodeModal' });

const emit = defineEmits(['success', 'register', 'created', 'hostExists']);
const { createMessage } = useMessage();
const isUpdate = ref(false);
const submitting = ref(false);
const editRecord = ref<ComputeNodeVO | null>(null);
const historyRefreshToken = ref(0);

const drawerTitle = computed(() => (unref(isUpdate) ? '编辑节点' : '添加节点'));

const [registerForm, { setFieldsValue, resetFields, validate, clearValidate }] = useForm({
  labelWidth: 150,
  schemas: formSchema,
  showActionButtonGroup: false,
  baseColProps: { span: 24 },
});

function flattenMediaTags(record: ComputeNodeVO) {
  const tags = record.tags || {};
  return {
    ...record,
    srsRtmpPort: tags.srs_rtmp_port ? Number(tags.srs_rtmp_port) : 1935,
    srsHttpPort: tags.srs_http_port ? Number(tags.srs_http_port) : 8080,
    zlmHttpPort: tags.zlm_http_port ? Number(tags.zlm_http_port) : 6080,
    zlmRtmpPort: tags.zlm_rtmp_port ? Number(tags.zlm_rtmp_port) : 10935,
    zlmRtpPortMin: tags.zlm_rtp_port_min ? Number(tags.zlm_rtp_port_min) : 30000,
    zlmRtpPortMax: tags.zlm_rtp_port_max ? Number(tags.zlm_rtp_port_max) : 30500,
  };
}

function buildMediaTags(values: Record<string, unknown>) {
  if (values.nodeRole !== 'media' && values.nodeRole !== 'hybrid') {
    return values.tags as Record<string, string> | undefined;
  }
  return {
    ...(values.tags as Record<string, string> | undefined),
    srs_rtmp_port: String(values.srsRtmpPort ?? 1935),
    srs_http_port: String(values.srsHttpPort ?? 8080),
    srs_api_port: String(values.srsApiPort ?? 1985),
    zlm_http_port: String(values.zlmHttpPort ?? 6080),
    zlm_rtmp_port: String(values.zlmRtmpPort ?? 10935),
    zlm_rtp_port_min: String(values.zlmRtpPortMin ?? 30000),
    zlm_rtp_port_max: String(values.zlmRtpPortMax ?? 30500),
  };
}

const [registerDrawer, { closeDrawer }] = useDrawerInner(async (data) => {
  resetFields();
  isUpdate.value = !!data?.isUpdate;
  editRecord.value = data?.isUpdate && data.record ? data.record : null;
  if (unref(isUpdate) && data.record) {
    setFieldsValue(flattenMediaTags(data.record));
  } else {
    setFieldsValue({
      sshUsername: 'root',
      agentPort: generateDefaultAgentPort(),
    });
  }
});

function handleNameChange() {
  clearValidate(['name']).catch(() => {});
}

async function applyHistoryEntry(entry: NodeFormHistoryEntry) {
  setFieldsValue(nodeFormHistoryToFields(entry));
  await clearValidate(['name']).catch(() => {});
}

function persistFormHistory(values: Record<string, unknown>) {
  saveNodeFormHistory(valuesToNodeFormHistoryEntry(values));
  historyRefreshToken.value += 1;
}

async function handleSubmit() {
  let raw: ComputeNodeVO & Record<string, unknown>;
  try {
    raw = (await validate()) as ComputeNodeVO & Record<string, unknown>;
  } catch {
    return;
  }
  const values = {
    ...raw,
    tags: buildMediaTags(raw),
  };
  if (unref(isUpdate) && editRecord.value) {
    values.maxGpuCount = editRecord.value.maxGpuCount ?? 0;
    values.maxTaskCount = editRecord.value.maxTaskCount ?? 50;
    values.weight = editRecord.value.weight ?? 100;
  }
  submitting.value = true;
  try {
    if (unref(isUpdate)) {
      await updateNode(values);
      createMessage.success('更新成功');
      closeDrawer();
      emit('success');
    } else {
      const res = await createNode(values, { errorMessageMode: 'none' });
      persistFormHistory(raw);
      closeDrawer();
      emit('success');
      emit('created', { ...values, ...(res || {}), agentToken: res?.agentToken });
    }
  } catch (err) {
    const msg = err instanceof Error ? err.message : String(err);
    if (!unref(isUpdate) && msg.includes('主机地址已存在')) {
      closeDrawer();
      emit('hostExists', raw.host);
      return;
    }
    createMessage.error(msg || '保存失败');
  } finally {
    submitting.value = false;
  }
}

function handleCancel() {
  closeDrawer();
}
</script>

<template>
  <BasicDrawer
    v-bind="$attrs"
    @register="registerDrawer"
    :title="drawerTitle"
    width="1400"
    placement="right"
    :showFooter="true"
    :showOkBtn="false"
    :showCancelBtn="false"
    destroy-on-close
  >
    <template #footer>
      <div class="footer-buttons">
        <Button @click="handleCancel">取消</Button>
        <Button type="primary" :loading="submitting" @click="handleSubmit">
          保存
        </Button>
      </div>
    </template>

    <Spin :spinning="submitting">
      <div class="node-drawer-content">
        <BasicForm @register="registerForm">
          <template #name="{ model, field }">
            <NodeNameField
              v-model:value="model[field]"
              :show-history="!isUpdate"
              :refresh-token="historyRefreshToken"
              @update:value="handleNameChange"
              @apply-history="applyHistoryEntry"
            />
          </template>
        </BasicForm>
      </div>
    </Spin>
  </BasicDrawer>
</template>

<style lang="less" scoped>
.node-drawer-content {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.footer-buttons {
  display: flex;
  justify-content: flex-end;
  align-items: center;
  gap: 8px;
}
</style>
