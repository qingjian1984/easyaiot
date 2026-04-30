import { defHttp } from '@/utils/http/axios';

/** iot-video 网关前缀：与网关路由 /admin-api/video/** 一致 */
const PREFIX = '/video/sdk/register';

export type SdkVendor = 'dahua' | 'hikvision';

export interface SdkAuthPayload {
  ip: string;
  port: number;
  username: string;
  password: string;
}

/** 大华 NetSDK 登录后返回的设备信息 */
export const fetchDahuaSdkDeviceInfo = (data: SdkAuthPayload) => {
  return defHttp.post<{ code: number; msg: string; data: Record<string, unknown> }>({
    url: `${PREFIX}/dahua/device-info`,
    data,
  });
};

/** 海康 HCNetSDK 登录后返回的设备信息 */
export const fetchHikSdkDeviceInfo = (data: SdkAuthPayload) => {
  return defHttp.post<{ code: number; msg: string; data: Record<string, unknown> }>({
    url: `${PREFIX}/hik/device-info`,
    data,
  });
};
