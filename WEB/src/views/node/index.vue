<template>
  <div class="node-wrapper">
    <div class="node-tab">
      <Tabs
        :activeKey="state.activeKey"
        :animated="{ inkBar: true, tabPane: true }"
        :destroyInactiveTabPane="false"
        :tabBarGutter="60"
        @tabClick="handleTabClick"
      >
        <TabPane key="1" :tab="NODE_PAGE.clusterOverview">
          <ClusterDashboard />
        </TabPane>
        <TabPane key="2" :tab="NODE_PAGE.nodeManage">
          <NodeManage />
        </TabPane>
      </Tabs>
    </div>
  </div>
</template>

<script lang="ts" setup>
import { reactive } from 'vue';
import { TabPane, Tabs } from 'ant-design-vue';
import ClusterDashboard from './components/ClusterDashboard/index.vue';
import NodeManage from './components/NodeManage/index.vue';
import { NODE_PAGE } from './utils/constants';

defineOptions({ name: 'ComputeNodeIndex' });

const state = reactive({
  activeKey: '1',
});

function handleTabClick(activeKey: string) {
  state.activeKey = activeKey;
}
</script>

<style lang="less" scoped>
@import './utils/theme.less';

.node-wrapper {
  min-height: 100%;
  background: @node-bg;

  :deep(.ant-form-item) {
    margin-bottom: 12px;
  }

  :deep(.ant-form-item-label > label) {
    font-size: @node-font-body;
    color: @node-text-body;
  }

  .node-tab {
    padding: 16px 19px 0 15px;

    :deep(.ant-tabs-nav) {
      padding: 5px 0 0 25px;
    }

    :deep(.ant-tabs) {
      background-color: #ffffff;

      :deep(.ant-tabs-nav) {
        padding: 5px 0 0 25px;
      }
    }
  }
}
</style>

<style lang="less">
@import './utils/node-badge.less';
</style>
