<template>
  <div>
    <BasicTable v-if="state.isTableMode" @register="registerTable">
      <template #toolbar>
        <Button type="primary" :preIcon="IconEnum.ADD" @click="handleCreate">{{ NODE_TERM.addNode }}</Button>
        <Button type="default" preIcon="ant-design:swap-outlined" @click="handleClickSwap">
          切换视图
        </Button>
      </template>
      <template #bodyCell="{ column, record }">
        <template v-if="column.dataIndex === 'name'">
          <a class="node-link" @click="handleView(record)">{{ record.name }}</a>
        </template>
        <template v-else-if="column.dataIndex === 'action'">
          <TableAction
            :actions="[
              ...(record.status === 'pending'
                ? [{
                    icon: 'ant-design:rocket-outlined',
                    tooltip: { title: NODE_TERM.continueOnboard, placement: 'top' },
                    onClick: handleContinueAgentSetup.bind(null, record),
                  }]
                : []),
              {
                icon: IconEnum.VIEW,
                tooltip: { title: NODE_TERM.viewDetail, placement: 'top' },
                onClick: handleView.bind(null, record),
              },
              {
                icon: IconEnum.EDIT,
                tooltip: { title: NODE_TERM.editNode, placement: 'top' },
                onClick: handleEdit.bind(null, record),
              },
              {
                icon: IconEnum.DELETE,
                tooltip: { title: '删除', placement: 'top' },
                popConfirm: {
                  placement: 'topRight',
                  title: '确认删除该节点？',
                  confirm: handleDelete.bind(null, record),
                },
              },
            ]"
          />
        </template>
      </template>
    </BasicTable>

    <div v-else>
      <NodeGridPanel
        :params="params"
        :api="getNodePage"
        @get-method="getMethod"
        @view="handleView"
        @edit="handleEdit"
        @test-ssh="handleTestSsh"
        @reset-token="handleResetToken"
        @maintenance="handleMaintenance"
        @deploy-media="handleDeployMedia"
        @delete="handleDel"
        @continue-setup="handleContinueAgentSetup"
      >
        <template #header>
          <Button type="primary" :preIcon="IconEnum.ADD" @click="handleCreate">{{ NODE_TERM.addNode }}</Button>
          <Button type="default" preIcon="ant-design:swap-outlined" @click="handleClickSwap">
            切换视图
          </Button>
        </template>
      </NodeGridPanel>
    </div>

    <NodeModal
      @register="registerNodeDrawer"
      @success="handleSuccess"
      @created="handleCreated"
      @host-exists="handleHostExists"
    />
    <NodeDetailDrawer
      ref="detailDrawerRef"
      @register="registerDetailDrawer"
      @edit="handleEdit"
      @reset-token="handleResetToken"
      @maintenance="handleMaintenance"
      @deploy-media="handleDeployMedia"
      @continue-setup="handleContinueAgentSetup"
      @refresh="handleSuccess"
      @closed="handleDrawerClosed"
    />
    <AgentSetupModal
      @register="registerAgentSetup"
      @success="handleSuccess"
      @edit="handleEdit"
    />
  </div>
</template>

<script lang="ts" setup>
import { reactive, ref } from 'vue';
import { columns, getNodeFormConfig } from '../../Data';
import NodeModal from '../NodeModal/index.vue';
import NodeDetailDrawer from '../NodeDetailDrawer/index.vue';
import AgentSetupModal from '../AgentSetupModal/index.vue';
import NodeGridPanel from '../NodeGridPanel/index.vue';
import { useMessage } from '@/hooks/web/useMessage';
import { useDrawer } from '@/components/Drawer';
import { IconEnum } from '@/enums/appEnum';
import { BasicTable, TableAction, useTable } from '@/components/Table';
import { Button } from '@/components/Button';
import {
  deleteNode,
  deployMediaStack,
  getAgentSetup,
  getNodePage,
  resetAgentToken,
  setNodeMaintenance,
  testNodeSsh,
  type ComputeNodeVO,
} from '@/api/device/node';
import { NODE_STATUS_MAP, NODE_TERM } from '../../utils/constants';

defineOptions({ name: 'ComputeNodeManage' });

const { createMessage, createConfirm } = useMessage();
const detailDrawerRef = ref<InstanceType<typeof NodeDetailDrawer> | null>(null);
const [registerNodeDrawer, { openDrawer: openNodeDrawer }] = useDrawer();
const [registerDetailDrawer, { openDrawer: openDetailDrawer }] = useDrawer();
const [registerAgentSetup, { openDrawer: openAgentSetup }] = useDrawer();

const state = reactive({
  isTableMode: false,
});

const params = {};
let cardListReload = () => {};

function getMethod(m: () => void) {
  cardListReload = m;
}

function handleClickSwap() {
  state.isTableMode = !state.isTableMode;
}

async function handleSuccess() {
  reload();
  cardListReload();
  await detailDrawerRef.value?.reloadDetail?.();
}

function handleDrawerClosed() {
  reload();
  cardListReload();
}

function handleCreate() {
  openNodeDrawer(true, { isUpdate: false });
}

function handleEdit(record: Recordable) {
  openNodeDrawer(true, { record, isUpdate: true });
}

function handleView(record: Recordable) {
  openDetailDrawer(true, { record });
  reload();
  cardListReload();
}

function handleDel(record: Recordable) {
  handleDelete(record);
}

function handleCreated(record: ComputeNodeVO & { agentToken?: string }) {
  openAgentSetup(true, { record, agentToken: record.agentToken });
}

async function handleContinueAgentSetup(record: Recordable) {
  if (!record?.id) return;
  try {
    const setup = await getAgentSetup(record.id);
    openAgentSetup(true, {
      record: { ...record, ...setup },
      agentToken: setup?.agentToken,
      resume: true,
    });
  } catch {
    createMessage.error(`获取${NODE_TERM.onboard}信息失败，请稍后重试`);
  }
}

async function handleHostExists(host: string) {
  let existing: ComputeNodeVO | undefined;
  try {
    const res = await getNodePage({ pageNo: 1, pageSize: 1, host });
    existing = res?.data?.list?.[0];
  } catch {
    // ignore
  }
  handleSuccess();

  if (!existing) {
    createConfirm({
      iconType: 'info',
      title: '该主机地址已存在',
      content: `主机 ${host} 已在系统中注册，请在列表中点击「${NODE_TERM.continueOnboard}」。`,
      okText: '我知道了',
      cancelButtonProps: { style: { display: 'none' } },
    });
    return;
  }

  const statusLabel = NODE_STATUS_MAP[existing.status || '']?.text || existing.status || '未知';
  const isPending = existing.status === 'pending';

  createConfirm({
    iconType: 'info',
    title: '该主机地址已存在',
    content: isPending
      ? `节点「${existing.name}」(${statusLabel})，是否${NODE_TERM.continueOnboard}？`
      : `节点「${existing.name}」(${statusLabel})，是否${NODE_TERM.viewDetail}？`,
    okText: isPending ? NODE_TERM.continueOnboard : NODE_TERM.viewDetail,
    cancelText: '取消',
    onOk: async () => {
      if (isPending) await handleContinueAgentSetup(existing!);
      else handleView(existing!);
    },
  });
}

async function handleDelete(record: Recordable) {
  await deleteNode(record.id);
  createMessage.success('删除成功');
  handleSuccess();
}

async function handleTestSsh(record: Recordable) {
  try {
    await testNodeSsh(record.id);
    createMessage.success('SSH 连接成功');
    handleSuccess();
  } catch {
    createMessage.error('SSH 连接失败');
  }
}

async function handleResetToken(record: Recordable) {
  createConfirm({
    title: `重置${NODE_TERM.agentToken}`,
    content: `重置后需在目标服务器更新 agent.env 中的 AGENT_TOKEN，并重启${NODE_TERM.agent}`,
    onOk: async () => {
      const res = await resetAgentToken(record.id);
      const token = typeof res === 'string' ? res : res?.data ?? res;
      openAgentSetup(true, { record, agentToken: token });
      handleSuccess();
    },
  });
}

async function handleMaintenance(record: Recordable, enabled: boolean) {
  await setNodeMaintenance(record.id, enabled);
  createMessage.success(enabled ? '已进入维护模式' : '已退出维护模式');
  await handleSuccess();
}

async function handleDeployMedia(record: Recordable, stackType: 'srs_live' | 'srs_ai' | 'zlm') {
  const labels: Record<string, string> = {
    srs_live: 'SRS Live',
    srs_ai: 'SRS AI',
    zlm: 'ZLM',
  };
  try {
    await deployMediaStack({ nodeId: record.id, stackType });
    createMessage.success(`${labels[stackType]} 部署指令已下发`);
  } catch {
    createMessage.error(`${NODE_TERM.mediaService}${NODE_TERM.deploy}失败，请确认${NODE_TERM.agent}在线且已安装 Docker`);
  }
}

const [registerTable, { reload }] = useTable({
  canResize: true,
  showIndexColumn: false,
  title: '节点管理',
  api: getNodePage,
  columns,
  useSearchForm: true,
  showTableSetting: false,
  pagination: true,
  formConfig: getNodeFormConfig(),
  actionColumn: {
    width: 160,
    title: '操作',
    dataIndex: 'action',
    fixed: 'right',
  },
  fetchSetting: {
    pageField: 'pageNo',
    sizeField: 'pageSize',
    listField: 'data.list',
    totalField: 'data.total',
  },
  rowKey: 'id',
});
</script>

<style lang="less" scoped>
.node-link {
  color: #266cfb;
  cursor: pointer;
  font-weight: 500;

  &:hover {
    color: #1a5ae8;
  }
}

:deep(.xingyuv-basic-table-action) {
  .ant-btn {
    padding-inline: 6px;
  }
}
</style>
