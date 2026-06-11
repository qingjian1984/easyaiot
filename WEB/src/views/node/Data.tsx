import { h } from 'vue';
import type { BasicColumn, FormSchema } from '@/components/Table';
import { useRender } from '@/components/Table';
import type { DescItem } from '@/components/Description';
import { formatToDateTime } from '@/utils/dateUtil';
import {
  NODE_METRIC,
  NODE_ROLE_DESC,
  NODE_ROLE_MAP,
  NODE_STATUS_MAP,
  NODE_TERM,
  formatGpuSummary,
} from './utils/constants';
import { formatSshUsername, renderNodeRoleBadge, renderNodeStatusBadge } from './utils/nodeDisplay';

export { NODE_ROLE_MAP, NODE_STATUS_MAP };

export const columns: BasicColumn[] = [
  {
    title: '节点名称',
    dataIndex: 'name',
    width: 140,
    ellipsis: true,
  },
  {
    title: '主机',
    dataIndex: 'host',
    width: 140,
    ellipsis: true,
    customRender: ({ text }) =>
      h('span', { style: { fontFamily: 'Consolas, monospace', fontSize: '12px' } }, text || '-'),
  },
  {
    title: '状态',
    dataIndex: 'status',
    width: 96,
    customRender: ({ text }) => renderNodeStatusBadge(text),
  },
  {
    title: '角色',
    dataIndex: 'nodeRole',
    width: 100,
    customRender: ({ text }) => useRender.renderTag(NODE_ROLE_MAP[text] || text, 'processing'),
  },
  {
    title: NODE_METRIC.cpu,
    dataIndex: 'cpuPercent',
    width: 80,
    customRender: ({ text }) => (text != null ? `${text}%` : '-'),
  },
  {
    title: NODE_METRIC.mem,
    dataIndex: 'memPercent',
    width: 80,
    customRender: ({ text }) => (text != null ? `${text}%` : '-'),
  },
  {
    title: NODE_METRIC.runningTasks,
    dataIndex: 'activeTasks',
    width: 70,
    customRender: ({ text }) => text ?? 0,
  },
  {
    title: '最近心跳',
    dataIndex: 'lastHeartbeatAt',
    width: 160,
    customRender: ({ text }) => (text ? useRender.renderDate(text) : '-'),
  },
];

export const searchFormSchema: FormSchema[] = [
  {
    label: '节点名称',
    field: 'name',
    component: 'Input',
    componentProps: { placeholder: '请输入节点名称' },
  },
  {
    label: '主机地址',
    field: 'host',
    component: 'Input',
    componentProps: { placeholder: '请输入主机地址' },
  },
  {
    label: '状态',
    field: 'status',
    component: 'Select',
    componentProps: {
      placeholder: '全部状态',
      options: Object.entries(NODE_STATUS_MAP).map(([value, { text }]) => ({ label: text, value })),
      allowClear: true,
    },
  },
];

export function getNodeFormConfig() {
  return {
    labelWidth: 80,
    baseColProps: { span: 6 },
    showAdvancedButton: false,
    autoSubmitOnEnter: true,
    actionColOptions: { span: 6 },
    schemas: searchFormSchema,
  };
}

export const formSchema: FormSchema[] = [
  { label: '编号', field: 'id', show: false, component: 'Input' },
  {
    field: 'dividerBasic',
    component: 'Divider',
    label: '基本信息',
    colProps: { span: 24 },
  },
  {
    label: '节点名称',
    field: 'name',
    required: true,
    component: 'Input',
    slot: 'name',
    colProps: { span: 12 },
    itemProps: { autoLink: false },
  },
  {
    label: '主机地址',
    field: 'host',
    required: true,
    component: 'Input',
    colProps: { span: 12 },
    componentProps: { placeholder: '10.0.0.11 或 node-a.internal' },
  },
  {
    label: '节点角色',
    field: 'nodeRole',
    required: true,
    component: 'Select',
    defaultValue: 'compute',
    colProps: { span: 12 },
    componentProps: {
      options: Object.entries(NODE_ROLE_MAP).map(([value, label]) => ({ label, value })),
    },
  },
  {
    label: '区域',
    field: 'region',
    component: 'Input',
    colProps: { span: 12 },
    componentProps: { placeholder: 'dc-a / 机房A' },
  },
  { label: '备注', field: 'remark', component: 'InputTextArea', colProps: { span: 24 } },
  {
    field: 'dividerConn',
    component: 'Divider',
    label: '连接配置',
    colProps: { span: 24 },
  },
  {
    label: 'SSH 端口',
    field: 'sshPort',
    component: 'InputNumber',
    defaultValue: 22,
    colProps: { span: 8 },
    componentProps: { min: 1, max: 65535 },
  },
  {
    label: NODE_TERM.agentPort,
    field: 'agentPort',
    component: 'InputNumber',
    colProps: { span: 8 },
    componentProps: { min: 1, max: 65535 },
  },
  {
    label: 'SSH 用户名',
    field: 'sshUsername',
    component: 'Input',
    defaultValue: 'root',
    colProps: { span: 8 },
    componentProps: { placeholder: 'root' },
  },
  {
    label: 'SSH 认证',
    field: 'sshAuthType',
    component: 'Select',
    defaultValue: 'password',
    colProps: { span: 8 },
    componentProps: {
      options: [
        { label: '密码', value: 'password' },
        { label: '私钥', value: 'private_key' },
      ],
    },
  },
  {
    label: 'SSH 密码',
    field: 'sshPassword',
    component: 'InputPassword',
    colProps: { span: 16 },
    ifShow: ({ values }) => values.sshAuthType !== 'private_key',
    componentProps: {
      placeholder: '更换目标服务器时请重新填写密码',
    },
  },
  {
    label: 'SSH 私钥',
    field: 'sshPrivateKey',
    component: 'InputTextArea',
    colProps: { span: 24 },
    ifShow: ({ values }) => values.sshAuthType === 'private_key',
    componentProps: { rows: 4, placeholder: '-----BEGIN RSA PRIVATE KEY-----' },
  },
  {
    field: 'dividerMedia',
    component: 'Divider',
    label: `${NODE_TERM.mediaPort}（media / hybrid 节点）`,
    colProps: { span: 24 },
    ifShow: ({ values }) => values.nodeRole === 'media' || values.nodeRole === 'hybrid',
  },
  {
    label: 'SRS RTMP 端口',
    field: 'srsRtmpPort',
    component: 'InputNumber',
    defaultValue: 1935,
    colProps: { span: 8 },
    ifShow: ({ values }) => values.nodeRole === 'media' || values.nodeRole === 'hybrid',
    componentProps: { min: 1, max: 65535 },
  },
  {
    label: 'SRS HTTP 端口',
    field: 'srsHttpPort',
    component: 'InputNumber',
    defaultValue: 8080,
    colProps: { span: 8 },
    ifShow: ({ values }) => values.nodeRole === 'media' || values.nodeRole === 'hybrid',
    componentProps: { min: 1, max: 65535 },
  },
  {
    label: 'ZLM HTTP 端口',
    field: 'zlmHttpPort',
    component: 'InputNumber',
    defaultValue: 6080,
    colProps: { span: 8 },
    ifShow: ({ values }) => values.nodeRole === 'media' || values.nodeRole === 'hybrid',
    componentProps: { min: 1, max: 65535 },
  },
  {
    label: 'ZLM RTMP 端口',
    field: 'zlmRtmpPort',
    component: 'InputNumber',
    defaultValue: 10935,
    colProps: { span: 8 },
    ifShow: ({ values }) => values.nodeRole === 'media' || values.nodeRole === 'hybrid',
    componentProps: { min: 1, max: 65535 },
  },
  {
    label: 'ZLM RTP 端口起',
    field: 'zlmRtpPortMin',
    component: 'InputNumber',
    defaultValue: 30000,
    colProps: { span: 8 },
    ifShow: ({ values }) => values.nodeRole === 'media' || values.nodeRole === 'hybrid',
    componentProps: { min: 1, max: 65535 },
  },
  {
    label: 'ZLM RTP 端口止',
    field: 'zlmRtpPortMax',
    component: 'InputNumber',
    defaultValue: 30500,
    colProps: { span: 8 },
    ifShow: ({ values }) => values.nodeRole === 'media' || values.nodeRole === 'hybrid',
    componentProps: { min: 1, max: 65535 },
  },
];

export const basicDetailSchema: DescItem[] = [
  { field: 'id', label: '节点 ID' },
  { field: 'host', label: '主机地址' },
  { field: 'sshPort', label: 'SSH 端口', render: (val) => val ?? 22 },
  { field: 'agentPort', label: NODE_TERM.agentPort, render: (val) => val ?? 9100 },
  {
    field: 'nodeRole',
    label: '节点角色',
    render: (val) => NODE_ROLE_MAP[val] || val,
  },
  {
    field: 'nodeRoleDesc',
    label: '角色说明',
    span: 2,
    render: (_val, data) => NODE_ROLE_DESC[data?.nodeRole] || '-',
  },
  { field: 'region', label: '区域', render: (val) => val || '-' },
  {
    field: 'gpuInfo',
    label: 'GPU 硬件',
    render: (_val, data) => formatGpuSummary(data?.gpuInfo),
  },
  { field: 'activeTasks', label: NODE_METRIC.runningTasks, render: (val) => val ?? 0 },
  {
    field: 'lastHeartbeatAt',
    label: '最近心跳',
    span: 2,
    render: (val) => (val ? formatToDateTime(val) : '-'),
  },
  {
    field: 'sshLastTestOk',
    label: 'SSH 测试',
    span: 2,
    render(val, data) {
      const tag =
        val === true
          ? useRender.renderTag('最近测试通过', 'success')
          : val === false
            ? useRender.renderTag('最近测试失败', 'error')
            : useRender.renderTag('未测试', 'default');
      const time = data?.sshLastTestAt
        ? h('span', { style: { marginLeft: '8px', color: '#888' } }, formatToDateTime(data.sshLastTestAt))
        : null;
      return h('span', {}, [tag, time]);
    },
  },
  {
    field: 'remark',
    label: '备注',
    span: 2,
    show: (data) => !!data?.remark,
  },
];

/** 节点纳管抽屉 — 节点概览 */
export const nodeSetupSummarySchema: DescItem[] = [
  { field: 'name', label: '节点名称', labelMinWidth: 108 },
  {
    field: 'status',
    label: '节点状态',
    labelMinWidth: 108,
    render: (val) => renderNodeStatusBadge(val),
  },
  {
    field: 'nodeRole',
    label: '节点角色',
    labelMinWidth: 108,
    render: (val) => renderNodeRoleBadge(val),
  },
  { field: 'host', label: '主机地址', labelMinWidth: 108 },
  { field: 'id', label: '节点 ID', labelMinWidth: 108 },
  {
    field: 'sshUsername',
    label: 'SSH 用户名',
    labelMinWidth: 108,
    render: (val, data) => formatSshUsername(val, data),
  },
  {
    field: 'sshPort',
    label: 'SSH 端口',
    labelMinWidth: 108,
    render: (val) => val ?? 22,
  },
  {
    field: 'agentPort',
    label: NODE_TERM.agentPort,
    labelMinWidth: 108,
    render: (val) => val ?? 9100,
  },
];

export const mediaDetailSchema: DescItem[] = [
  {
    field: 'tags.srs_rtmp_port',
    label: 'SRS RTMP',
    render: (_val, data) => data?.tags?.srs_rtmp_port ?? 1935,
  },
  {
    field: 'tags.srs_http_port',
    label: 'SRS HTTP',
    render: (_val, data) => data?.tags?.srs_http_port ?? 8080,
  },
  {
    field: 'tags.zlm_http_port',
    label: 'ZLM HTTP',
    render: (_val, data) => data?.tags?.zlm_http_port ?? 6080,
  },
  {
    field: 'tags.zlm_rtmp_port',
    label: 'ZLM RTMP',
    render: (_val, data) => data?.tags?.zlm_rtmp_port ?? 10935,
  },
  {
    field: 'tags.zlm_rtp_port_min',
    label: 'ZLM RTP 范围',
    span: 2,
    render: (_val, data) =>
      `${data?.tags?.zlm_rtp_port_min ?? 30000} - ${data?.tags?.zlm_rtp_port_max ?? 30500}`,
  },
];

export const gpuColumns: BasicColumn[] = [
  { title: '序号', dataIndex: 'id', width: 60 },
  { title: '型号', dataIndex: 'name', ellipsis: true },
  {
    title: NODE_METRIC.gpuUtil,
    dataIndex: 'util',
    width: 90,
    customRender: ({ text }) => (text != null ? `${text}%` : '-'),
  },
  {
    title: NODE_METRIC.vram,
    dataIndex: 'mem_total_mb',
    width: 120,
    customRender: ({ record }) =>
      record.mem_total_mb
        ? `${Math.round(record.mem_used_mb ?? 0)}/${Math.round(record.mem_total_mb)}M`
        : '-',
  },
];
