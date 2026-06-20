<template>
  <div class="rulechain-card-list-wrapper p-2">
    <div class="p-4 bg-white" style="margin-bottom: 10px">
      <BasicForm @register="registerForm"/>
    </div>
    <div class="p-2 bg-white">
      <Spin :spinning="state.loading">
        <List
          :grid="{ gutter: 2, xs: 1, sm: 2, md: 4, lg: 4, xl: 4, xxl: 4 }"
          :data-source="data"
          :pagination="paginationProp"
        >
          <template #header>
            <div
              style="display: flex;align-items: center;justify-content: space-between;flex-direction: row;">
              <span style="padding-left: 7px;font-size: 16px;font-weight: 500;line-height: 24px;">链式规则列表</span>
              <div class="space-x-2">
                <slot name="header"></slot>
              </div>
            </div>
          </template>
          <template #renderItem="{ item }">
            <ListItem :class="!item.disabled? 'product-item normal' : 'product-item error'">
              <div class="product-info">
                <div class="status">{{ !item.disabled ? '启用' : '禁用' }}</div>
                <div class="title o2">{{ item.label }}</div>
                <div class="props">
                  <div class="flex" style="justify-content: space-between;">
                    <div class="prop">
                      <div class="label">规则链ID</div>
                      <div class="value">{{ item.id }}</div>
                    </div>
                    <div class="prop">
                      <div class="label">规则状态</div>
                      <div class="value">{{ !item.disabled ? '启用' : '禁用' }}</div>
                    </div>
                  </div>
                  <div class="prop">
                    <div class="label">规则名称</div>
                    <div class="value">{{ item.label }}</div>
                  </div>
                </div>
                <div class="btns">
                  <Tooltip title="详情">
                    <span class="action-btn" @click="handleView(item)">
                      <Icon icon="ant-design:eye-filled" :size="15" />
                    </span>
                  </Tooltip>
                  <Tooltip title="编辑">
                    <span class="action-btn" @click="handleEdit(item)">
                      <Icon icon="ant-design:edit-filled" :size="15" />
                    </span>
                  </Tooltip>
                  <PopConfirmButton
                    type="default"
                    placement="topRight"
                    title="是否确认删除？"
                    preIcon="material-symbols:delete-outline-rounded"
                    @confirm="handleDelete(item)"
                  />
                </div>
              </div>
              <div class="product-img">
                <img
                  :src="item.ruleType == 'LAYOUT'? RULE_C3 : item.ruleType == 'FORWARD'? RULE_C2 : RULE_C1"
                  alt="" class="img" @click="handleGo(item)">
              </div>
            </ListItem>
          </template>
        </List>
      </Spin>
    </div>
  </div>
</template>
<script lang="ts" setup>
import {onMounted, reactive, ref} from 'vue';
import {List, Spin, Tooltip} from 'ant-design-vue';
import {BasicForm, useForm} from '@/components/Form';
import {PopConfirmButton} from '@/components/Button';
import {Icon} from '@/components/Icon';
import {propTypes} from '@/utils/propTypes';
import {isFunction} from '@/utils/is';

import RULE_C1 from '@/assets/images/rule/rule_c1.png';
import RULE_C2 from '@/assets/images/rule/rule_c2.png';
import RULE_C3 from '@/assets/images/rule/rule_c3.png';

defineOptions({ name: 'RulechainCardList' });

const ListItem = List.Item;
// 组件接收参数
const props = defineProps({
  // 请求API的参数
  params: propTypes.object.def({}),
  //api
  api: propTypes.func,
});
const emit = defineEmits(['getMethod', 'delete', 'edit', 'view', 'go']);
const data = ref([]);

const state = reactive({
  loading: true,
});

//表单
const [registerForm, {validate}] = useForm({
  schemas: [
    {
      field: `productName`,
      label: `规则名称`,
      component: 'Input',
    },
    {
      field: `model`,
      label: `规则型号`,
      component: 'Input',
    },
    {
      field: `manufacturerName`,
      label: `规则描述`,
      component: 'Input',
    },
  ],
  labelWidth: 80,
  baseColProps: {span: 6},
  actionColOptions: {span: 6},
  autoSubmitOnEnter: true,
  submitFunc: handleSubmit,
});

//表单提交
async function handleSubmit() {
  const data = await validate();
  await fetch(data);
}

// 自动请求并暴露内部方法
onMounted(() => {
  fetch();
  emit('getMethod', fetch);
});

async function fetch(p = {}) {
  const {api, params} = props;
  if (api && isFunction(api)) {
    const res = await api({...params, pageNo: page.value, pageSize: pageSize.value, ...p});
    let list = [];
    res['data'].forEach((element) => {
      if (element.type == 'tab') {
        list.push(element);
      }
    });
    data.value = list;
    total.value = res.total;
    hideLoading();
  }
}

function hideLoading() {
  state.loading = false;
}

//分页相关
const page = ref(1);
const pageSize = ref(8);
const total = ref(0);
const paginationProp = ref({
  showSizeChanger: false,
  showQuickJumper: true,
  pageSize,
  current: page,
  total,
  showTotal: (total: number) => `总 ${total} 条`,
  onChange: pageChange,
  onShowSizeChange: pageSizeChange,
});

function pageChange(p: number, pz: number) {
  page.value = p;
  pageSize.value = pz;
  fetch();
}

function pageSizeChange(_current, size: number) {
  pageSize.value = size;
  fetch();
}

async function handleView(record: object) {
  emit('view', record);
}

async function handleEdit(record: object) {
  emit('edit', record);
}

async function handleGo(record: object) {
  emit('go', record);
}

async function handleDelete(record: object) {
  emit('delete', record);
}
</script>

<style lang="less" scoped>
.rulechain-card-list-wrapper {

  :deep(.ant-list-header) {
    border-block-end: 0;
  }

  :deep(.ant-list-header) {
    padding-top: 0;
    padding-bottom: 8px;
  }

  :deep(.ant-list) {
    padding: 6px;
  }

  :deep(.ant-list-item) {
    margin: 6px;
  }

  :deep(.product-item) {
    overflow: hidden;
    box-shadow: 0 0 4px #00000026;
    border-radius: 8px;
    padding: 16px 0;
    position: relative;
    background-color: #fff;
    background-repeat: no-repeat;
    background-position: center center;
    background-size: 104% 104%;
    transition: all .5s;
    min-height: 208px;
    height: 100%;

    &.normal {
      background-image: url('@/assets/images/product/blue-bg.719b437a.png');

      .status {
        background: #d9dffd;
        color: #266CFBFF;
      }
    }

    &.error {
      background-image: url('@/assets/images/product/red-bg.101af5ac.png');

      .status {
        background: #fad7d9;
        color: #d43030;
      }
    }

    .product-info {
      flex-direction: column;
      max-width: calc(100% - 128px);
      padding-left: 16px;

      .status {
        width: 57px;
        height: 25px;
        border-radius: 6px 0 0 6px;
        font-size: 12px;
        font-weight: 500;
        line-height: 25px;
        text-align: center;
        position: absolute;
        right: 0;
        top: 16px;
      }

      .title {
        font-size: 16px;
        font-weight: 600;
        color: #050708;
        line-height: 20px;
        height: 40px;
      }

      .props {
        margin-top: 10px;

        .prop {
          flex: 1;
          margin-bottom: 10px;

          .label {
            font-size: 12px;
            font-weight: 400;
            color: #666;
            line-height: 14px;
          }

          .value {
            font-size: 14px;
            font-weight: 600;
            color: #050708;
            line-height: 14px;
            white-space: nowrap;
            overflow: hidden;
            text-overflow: ellipsis;
            margin-top: 6px;
          }
        }
      }

      .btns {
        display: flex;
        position: absolute;
        left: 16px;
        bottom: 16px;
        margin-top: 20px;
        width: 130px;
        height: 28px;
        border-radius: 45px;
        justify-content: space-around;
        padding: 0 10px;
        align-items: center;
        border: 2px solid #266CFBFF;

        .ant-btn-default {
          background: none;
          border: none;
          box-shadow: none;
        }

        .ant-btn {
          padding: 0;
          width: 28px;
        }

        .action-btn {
          display: inline-flex;
          align-items: center;
          justify-content: center;
          width: 28px;
          height: 28px;
          cursor: pointer;
          position: relative;
          color: #266cfb;

          &:before {
            content: '';
            display: block;
            position: absolute;
            width: 1px;
            height: 7px;
            background-color: #e2e2e2;
            left: -5px;
            top: 9px;
          }

          &:first-child:before {
            display: none;
          }
        }
      }
    }

    .product-img {
      position: absolute;
      right: 20px;
      top: 50px;

      img {
        cursor: pointer;
        width: 120px;
      }
    }
  }
}
</style>
