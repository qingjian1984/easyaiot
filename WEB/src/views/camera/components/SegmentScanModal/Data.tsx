import { BasicColumn } from '@/components/Table';

export function getCameraScanColumns(): BasicColumn[] {
  return [
    { title: 'IP', dataIndex: 'ip', width: 110 },
    { title: '端口', dataIndex: 'port', width: 60 },
    { title: '品牌', dataIndex: 'vendor_label', width: 70 },
    { title: '角色', dataIndex: 'role_label', width: 80 },
    { title: '型号', dataIndex: 'model', width: 100, ellipsis: true },
    { title: '名称', dataIndex: 'device_name', width: 100, ellipsis: true },
    { title: 'MAC', dataIndex: 'mac', width: 110, ellipsis: true },
    { title: 'RTSP', dataIndex: 'rtsp_url', width: 160, ellipsis: true },
    { title: '操作', dataIndex: 'action', width: 70, fixed: 'right' },
  ];
}

export function getNvrScanColumns(): BasicColumn[] {
  return [
    { title: 'IP', dataIndex: 'ip', width: 110 },
    { title: '端口', dataIndex: 'port', width: 60 },
    { title: '品牌', dataIndex: 'vendor_label', width: 70 },
    { title: '角色', dataIndex: 'role_label', width: 80 },
    { title: '型号', dataIndex: 'model', width: 110, ellipsis: true },
    { title: '名称', dataIndex: 'device_name', width: 110, ellipsis: true },
    { title: 'RTSP', dataIndex: 'rtsp_url', width: 160, ellipsis: true },
    { title: '操作', dataIndex: 'action', width: 140, fixed: 'right' },
  ];
}

export function getNvrChannelColumns(): BasicColumn[] {
  return [
    { title: '通道', dataIndex: 'channel_id', width: 60 },
    { title: '名称', dataIndex: 'name', width: 120, ellipsis: true },
    { title: '摄像头IP', dataIndex: 'camera_ip', width: 120 },
    { title: '状态', dataIndex: 'online_text', width: 70 },
    { title: 'RTSP(经NVR)', dataIndex: 'rtsp_url', width: 200, ellipsis: true },
    { title: '操作', dataIndex: 'action', width: 80, fixed: 'right' },
  ];
}
