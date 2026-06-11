import type { FormSchema } from '@/components/Table';
import { NODE_STATUS_MAP } from '../../utils/constants';

export function getNodeCardFormSchemas(): FormSchema[] {
  return [
    {
      field: 'name',
      label: '节点名称',
      component: 'Input',
      componentProps: { placeholder: '请输入节点名称', allowClear: true },
    },
    {
      field: 'host',
      label: '主机地址',
      component: 'Input',
      componentProps: { placeholder: '请输入主机地址', allowClear: true },
    },
    {
      field: 'status',
      label: '状态',
      component: 'Select',
      componentProps: {
        placeholder: '全部状态',
        allowClear: true,
        options: Object.entries(NODE_STATUS_MAP).map(([value, { text }]) => ({ label: text, value })),
      },
    },
  ];
}

export function getNodeCardFormConfig() {
  return {
    labelWidth: 80,
    baseColProps: { span: 6 },
    showAdvancedButton: false,
    autoSubmitOnEnter: true,
    actionColOptions: { span: 6 },
    schemas: getNodeCardFormSchemas(),
  };
}
