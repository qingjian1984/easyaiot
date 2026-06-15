<template>
  <BasicModal
    @register="register"
    width="960px"
    title="SAM 模型安装"
    :canFullscreen="false"
    :showOkBtn="false"
    :showCancelBtn="true"
    cancelText="关闭"
    :get-container="getContainer"
    wrap-class-name="sam-model-setup-modal"
    @cancel="handleClose"
  >
    <SamModelSetupPanel
      :checking="!modelStatusChecked"
      :model-status="modelStatus"
      :show-progress="showProgressPanel"
      :progress="displayProgress"
      :current-step="downloadStepCurrent"
      :finished="modelDownloadJustFinished"
      :starting="downloadStarting"
      @download="handleDownloadModel"
    />
  </BasicModal>
</template>

<script lang="ts" setup>
import { BasicModal, useModal } from '@/components/Modal';
import SamModelSetupPanel from '@/views/dataset/components/AutoLabel/SamModelSetupPanel/index.vue';
import { useSamModelSetup } from '@/views/dataset/components/AutoLabel/useSamModelSetup';

defineOptions({ name: 'SamModelSetupModal' });

const props = defineProps<{
  getContainer?: () => HTMLElement;
}>();

const emit = defineEmits<{ ready: []; closed: []; register: [] }>();

const [register, { openModal, closeModal }] = useModal();

const {
  modelStatusChecked,
  modelStatus,
  modelReady,
  showProgressPanel,
  displayProgress,
  downloadStepCurrent,
  modelDownloadJustFinished,
  downloadStarting,
  refreshModelStatus,
  handleDownloadModel,
} = useSamModelSetup(() => {
  closeModal();
  emit('ready');
});

async function open() {
  modelStatusChecked.value = false;
  openModal();
  await refreshModelStatus();
}

function handleClose() {
  emit('closed');
}

async function ensureReady(): Promise<boolean> {
  await refreshModelStatus();
  if (modelReady.value) return true;
  openModal();
  return false;
}

defineExpose({ openModal: open, ensureReady, refreshModelStatus, modelReady });
</script>

<style lang="less">
.sam-model-setup-modal {
  .ant-modal-body {
    padding: 0;
  }
}
</style>
