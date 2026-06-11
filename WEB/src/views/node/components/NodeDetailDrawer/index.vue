<script lang="ts" setup>
import { computed, ref, watch } from 'vue';
import { Alert, Tabs } from 'ant-design-vue';
import { BasicDrawer, useDrawerInner } from '@/components/Drawer';
import { BasicTitle } from '@/components/Basic';
import { Description, useDescription } from '@/components/Description';
import { Icon } from '@/components/Icon';
import { Button } from '@/components/Button';
import { useMessage } from '@/hooks/web/useMessage';
import { formatToDateTime } from '@/utils/dateUtil';
import { getNode, testNodeSsh, type ComputeNodeVO } from '@/api/device/node';
import {
  NODE_DETAIL,
  NODE_ROLE_DESC,
  loadNodeControlPlaneUrlAsync,
  saveNodeControlPlaneUrl,
} from '../../utils/constants';
import { mediaDetailSchema, nodeSetupSummarySchema } from '../../Data';
import NodeMetaBadge from '../NodeMetaBadge/index.vue';
import SetupOverviewPanel from '../SetupOverviewPanel/index.vue';
import NodeDetailResourcePanel from '../NodeDetailResourcePanel/index.vue';
import MediaStackSetupPanel from '../MediaStackSetupPanel/index.vue';
import AgentDeployPanel from '../AgentDeployPanel/index.vue';
import SetupVerifyPanel from '../SetupVerifyPanel/index.vue';

defineOptions({ name: 'NodeDetailDrawer' });

type DetailTabKey = 'resource' | 'config' | 'access' | 'mediaDeploy' | 'agentDeploy';

const emit = defineEmits([
  'register',
  'edit',
  'resetToken',
  'maintenance',
  'deployMedia',
  'continueSetup',
  'refresh',
  'closed',
]);

const { createMessage } = useMessage();

const loading = ref(false);
const drawerOpen = ref(false);
const testingSsh = ref(false);
const activeTab = ref<DetailTabKey>('resource');
const node = ref<ComputeNodeVO | null>(null);
const controlPlaneUrl = ref('');
const skipControlPlanePersist = ref(false);

watch(controlPlaneUrl, (url) => {
  if (skipControlPlanePersist.value) return;
  if (node.value?.id) saveNodeControlPlaneUrl(node.value.id, url);
});

const isMediaNode = computed(
  () => node.value?.nodeRole === 'media' || node.value?.nodeRole === 'hybrid',
);

const roleDesc = computed(() => NODE_ROLE_DESC[node.value?.nodeRole || ''] || '');

const mediaFormValues = computed(() => {
  const current = node.value;
  if (!current) return undefined;
  const tags = current.tags || {};
  return {
    nodeRole: current.nodeRole,
    nodeId: current.id,
    name: current.name,
    host: current.host,
    sshUsername: current.sshUsername,
    sshCredentialConfigured: current.sshCredentialConfigured,
    sshLastTestOk: current.sshLastTestOk,
    sshPort: current.sshPort,
    srsRtmpPort: tags.srs_rtmp_port ? Number(tags.srs_rtmp_port) : 1935,
    srsHttpPort: tags.srs_http_port ? Number(tags.srs_http_port) : 8080,
    srsApiPort: tags.srs_api_port ? Number(tags.srs_api_port) : 1985,
    zlmHttpPort: tags.zlm_http_port ? Number(tags.zlm_http_port) : 6080,
    zlmRtmpPort: tags.zlm_rtmp_port ? Number(tags.zlm_rtmp_port) : 10935,
    zlmRtpPortMin: tags.zlm_rtmp_port_min ? Number(tags.zlm_rtp_port_min) : 30000,
    zlmRtpPortMax: tags.zlm_rtmp_port_max ? Number(tags.zlm_rtmp_port_max) : 30500,
  };
});

const statusAlert = computed(() => {
  const status = node.value?.status;
  if (status === 'pending') {
    return { type: 'warning' as const, message: NODE_DETAIL.pendingMessage, description: NODE_DETAIL.pendingAlert };
  }
  if (status === 'offline') {
    return { type: 'error' as const, message: NODE_DETAIL.offlineMessage, description: NODE_DETAIL.offlineAlert };
  }
  if (status === 'maintenance') {
    return { type: 'warning' as const, message: NODE_DETAIL.maintenanceMessage, description: NODE_DETAIL.maintenanceAlert };
  }
  return null;
});

const mediaPanelActive = computed(
  () => drawerOpen.value && activeTab.value === 'mediaDeploy',
);

const agentPanelActive = computed(
  () => drawerOpen.value && activeTab.value === 'agentDeploy',
);

const verifyPanelActive = computed(
  () => drawerOpen.value && activeTab.value === 'agentDeploy' && node.value?.status !== 'online',
);

const [registerMediaDesc] = useDescription({
  useCollapse: false,
  bordered: true,
  column: 2,
  schema: mediaDetailSchema,
  data: node,
});

const [registerDrawer, { setDrawerProps, closeDrawer }] = useDrawerInner(async (data) => {
  drawerOpen.value = true;
  const pending = data?.record?.status === 'pending';
  const isMedia =
    data?.record?.nodeRole === 'media' || data?.record?.nodeRole === 'hybrid';
  activeTab.value = pending ? (isMedia ? 'mediaDeploy' : 'agentDeploy') : 'resource';
  setDrawerProps({ showFooter: true, showOkBtn: false, showCancelBtn: false });
  if (data?.record?.id) {
    skipControlPlanePersist.value = true;
    controlPlaneUrl.value = await loadNodeControlPlaneUrlAsync(data.record.id);
    skipControlPlanePersist.value = false;
    await loadDetail(data.record.id);
  }
});

async function loadDetail(id: number) {
  loading.value = true;
  try {
    node.value = await getNode(id);
  } catch {
    createMessage.error('加载节点详情失败');
  } finally {
    loading.value = false;
  }
}

async function handleRefresh() {
  if (!node.value?.id) return;
  await loadDetail(node.value.id);
  emit('refresh');
}

async function handleTestSsh() {
  if (!node.value?.id) return;
  testingSsh.value = true;
  try {
    await testNodeSsh(node.value.id);
    createMessage.success('SSH 连接成功');
    await loadDetail(node.value.id);
    emit('refresh');
  } catch {
    createMessage.error('SSH 连接失败');
  } finally {
    testingSsh.value = false;
  }
}

function handleDrawerOpenChange(open: boolean) {
  drawerOpen.value = open;
  if (!open) {
    node.value = null;
    activeTab.value = 'resource';
    emit('closed');
  }
}

function handleClose() {
  closeDrawer();
}

defineExpose({
  reloadDetail: () => {
    const id = node.value?.id;
    if (id) return loadDetail(id);
  },
});
</script>

<template>
  <BasicDrawer
    v-bind="$attrs"
    width="1400"
    placement="right"
    destroy-on-close
    root-class-name="node-detail-drawer"
    :loading="loading"
    @register="registerDrawer"
    @open-change="handleDrawerOpenChange"
  >
    <template #title>
      <div v-if="node" class="detail-drawer-header">
        <div class="detail-drawer-header__main">
          <div class="detail-drawer-header__icon">
            <Icon icon="ant-design:desktop-outlined" :size="22" />
          </div>
          <div>
            <BasicTitle span class="detail-drawer-header__title">{{ NODE_DETAIL.title }}</BasicTitle>
            <div class="detail-drawer-header__meta">
              <span>{{ node.name }}</span>
              <span class="meta-sep">·</span>
              <span>{{ node.host }}</span>
              <template v-if="node.lastHeartbeatAt">
                <span class="meta-sep">·</span>
                <span>心跳 {{ formatToDateTime(node.lastHeartbeatAt) }}</span>
              </template>
            </div>
          </div>
        </div>
        <div class="detail-drawer-header__tags">
          <NodeMetaBadge type="status" :status="node.status" size="lg" />
          <NodeMetaBadge type="role" :role="node.nodeRole" size="lg" />
        </div>
      </div>
    </template>

    <template #footer>
      <div class="detail-footer">
        <div class="detail-footer__left">
          <Button @click="handleClose">{{ NODE_DETAIL.footerClose }}</Button>
          <Button @click="handleRefresh">{{ NODE_DETAIL.actionRefresh }}</Button>
        </div>
        <div class="detail-footer__right">
          <Button
            v-if="node?.status === 'pending'"
            type="primary"
            @click="emit('continueSetup', node)"
          >
            {{ NODE_DETAIL.actionSetup }}
          </Button>
          <Button @click="emit('edit', node)">{{ NODE_DETAIL.actionEdit }}</Button>
          <Button @click="emit('maintenance', node, node?.status !== 'maintenance')">
            {{ node?.status === 'maintenance' ? '退出维护' : NODE_DETAIL.actionMaintenance }}
          </Button>
          <Button danger ghost @click="emit('resetToken', node)">
            {{ NODE_DETAIL.actionResetToken }}
          </Button>
        </div>
      </div>
    </template>

    <div v-if="node" class="detail-drawer-content">
      <Alert
        v-if="statusAlert"
        :type="statusAlert.type"
        show-icon
        class="detail-status-alert"
        :message="statusAlert.message"
        :description="statusAlert.description"
      >
        <template v-if="node.status === 'pending'" #action>
          <Button size="small" type="primary" @click="emit('continueSetup', node)">
            {{ NODE_DETAIL.actionSetup }}
          </Button>
        </template>
      </Alert>

      <Tabs v-model:activeKey="activeTab" class="detail-tabs">
        <Tabs.TabPane :key="'resource'" :tab="NODE_DETAIL.tabResource">
          <div class="detail-tab-pane">
            <p class="detail-tab-hint">{{ NODE_DETAIL.sectionResourceHint }}</p>
            <NodeDetailResourcePanel :node="node" />
          </div>
        </Tabs.TabPane>

        <Tabs.TabPane :key="'config'" :tab="NODE_DETAIL.tabConfig">
          <div class="detail-tab-pane">
            <Alert v-if="roleDesc" type="info" show-icon class="detail-tab-alert" :message="roleDesc" />
            <div class="setup-desc">
              <Description
                :use-collapse="false"
                bordered
                :column="2"
                :schema="nodeSetupSummarySchema"
                :data="node"
              />
            </div>
            <div v-if="isMediaNode" class="media-desc-block">
              <h4 class="detail-subtitle">{{ NODE_DETAIL.sectionMedia }}</h4>
              <Description @register="registerMediaDesc" />
            </div>
          </div>
        </Tabs.TabPane>

        <Tabs.TabPane :key="'access'" :tab="NODE_DETAIL.tabAccess">
          <div class="detail-tab-pane">
            <SetupOverviewPanel
              embedded
              :show-node-info="false"
              :node="node"
              :testing-ssh="testingSsh"
              @edit="emit('edit', node)"
              @test-ssh="handleTestSsh"
            />
          </div>
        </Tabs.TabPane>

        <Tabs.TabPane
          v-if="isMediaNode"
          :key="'mediaDeploy'"
          :tab="NODE_DETAIL.tabMediaDeploy"
        >
          <div class="detail-tab-pane detail-tab-pane--flush">
            <MediaStackSetupPanel
              :active="mediaPanelActive"
              :form-values="mediaFormValues"
              @deployed="handleRefresh"
            />
          </div>
        </Tabs.TabPane>

        <Tabs.TabPane :key="'agentDeploy'" :tab="NODE_DETAIL.tabAgentDeploy">
          <div class="detail-tab-pane detail-tab-pane--flush">
            <div class="detail-tab-stack">
              <AgentDeployPanel
                v-model:control-plane-url="controlPlaneUrl"
                :active="agentPanelActive"
                :node="node"
                :agent-token="node.agentToken"
                @deployed="handleRefresh"
              />

              <SetupVerifyPanel
                v-if="node.status !== 'online'"
                v-model:control-plane-url="controlPlaneUrl"
                :node-id="node.id"
                :active="verifyPanelActive"
                :ssh-ready="!!node.sshUsername?.trim() || node.sshCredentialConfigured === true"
                @online="handleRefresh"
              />
            </div>
          </div>
        </Tabs.TabPane>
      </Tabs>
    </div>
  </BasicDrawer>
</template>

<style lang="less" scoped>
@import '../../utils/setup-panel.less';

.detail-drawer-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  width: 100%;
  padding-right: 32px;
}

.detail-drawer-header__main {
  display: flex;
  align-items: center;
  gap: 12px;
  min-width: 0;
}

.detail-drawer-header__icon {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 40px;
  height: 40px;
  border-radius: 10px;
  background: linear-gradient(135deg, #eef4ff, #dce8ff);
  color: @node-primary;
  flex-shrink: 0;
}

.detail-drawer-header__title {
  font-size: 18px !important;
  font-weight: 600 !important;
}

.detail-drawer-header__meta {
  margin-top: 2px;
  font-size: 12px;
  color: rgba(0, 0, 0, 0.45);
}

.meta-sep {
  margin: 0 6px;
}

.detail-drawer-header__tags {
  display: flex;
  gap: 10px;
  flex-shrink: 0;
}

.detail-drawer-content {
  display: flex;
  flex-direction: column;
  gap: 16px;
  min-height: 100%;
}

.detail-status-alert {
  margin-bottom: 0;
}

.detail-tabs {
  :deep(.ant-tabs-nav) {
    margin-bottom: 0;
    padding: 0 4px;
    background: #fff;
    border: 1px solid rgba(0, 0, 0, 0.06);
    border-radius: @setup-panel-radius;
    box-shadow: @setup-panel-shadow;

    &::before {
      border-bottom: none;
    }
  }

  :deep(.ant-tabs-tab) {
    padding: 12px 20px;
    font-size: 14px;
  }

  :deep(.ant-tabs-content-holder) {
    padding-top: 16px;
  }
}

.detail-tab-pane {
  padding: 20px 24px;
  background: #fff;
  border: 1px solid rgba(0, 0, 0, 0.06);
  border-radius: @setup-panel-radius;
  box-shadow: @setup-panel-shadow;
}

.detail-tab-pane--flush {
  padding: 0;
  background: transparent;
  border: none;
  box-shadow: none;
}

.detail-tab-stack {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.detail-tab-hint {
  margin: 0 0 16px;
  font-size: 13px;
  color: rgba(0, 0, 0, 0.45);
}

.detail-tab-alert {
  margin-bottom: 16px;
}

.detail-subtitle {
  margin: 0 0 12px;
  font-size: 14px;
  font-weight: 600;
  color: #262626;
}

.media-desc-block {
  margin-top: 20px;
  padding-top: 20px;
  border-top: 1px dashed #f0f0f0;
}

.detail-footer {
  display: flex;
  justify-content: space-between;
  align-items: center;
  width: 100%;
  gap: 12px;
  flex-wrap: wrap;
}

.detail-footer__left,
.detail-footer__right {
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
}
</style>

<style lang="less">
.node-detail-drawer {
  .ant-drawer-header {
    padding: 16px 24px;
    border-bottom: 1px solid #f0f0f0;
  }

  .ant-drawer-body {
    background: linear-gradient(180deg, #f7f9fc 0%, #ffffff 120px);
  }

  .scrollbar__wrap {
    padding: 20px 24px !important;
  }

  .ant-drawer-footer {
    padding: 12px 24px;
    border-top: 1px solid #f0f0f0;
    background: #fff;
  }
}
</style>
