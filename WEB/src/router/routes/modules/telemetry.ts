import type { AppRouteModule } from '@/router/types'
import { LAYOUT } from '@/router/constant'

/**
 * PRD §4.5 遥测历史查询（M1：多设备多测点曲线对比/粒度聚合/导出）。
 */
const telemetry: AppRouteModule = {
  path: '/telemetry',
  name: 'TelemetryManage',
  component: LAYOUT,
  redirect: '/telemetry/history',
  meta: {
    orderNo: 9,
    icon: 'ant-design:line-chart-outlined',
    title: '遥测历史',
    hideChildrenInMenu: true,
  },
  children: [
    {
      path: 'history',
      name: 'TelemetryHistory',
      component: () => import('@/views/telemetry/history/index.vue'),
      meta: {
        title: '遥测历史',
        icon: 'ant-design:line-chart-outlined',
        hideMenu: true,
      },
    },
  ],
}

export default telemetry
