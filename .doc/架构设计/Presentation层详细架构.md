# Presentation Layer — WEB 管理控制台详细架构

> 基于整体架构文件 V9.18.0 + WEB 源码深入分析
> 代码规模: 747 个 Vue 组件 / 610 TypeScript 文件 / Vue 3.4 + Vite 4

---

## 一、总体定位

WEB 模块是 EasyAIot 的 **前端管理控制台**，采用 Vue 3 + TypeScript + Vite + Ant Design Vue 4 技术栈，提供 14 个业务域的完整管理能力，是运营人员和终端用户与平台交互的核心入口。

| 指标 | 数据 |
|------|------|
| Vue 组件总数 | 747 |
| TypeScript 文件 | 610+ |
| 业务域 | 14 个 |
| API 模块 | 100+ |
| Pinia Store | 14 个 |
| 支持语言 | 中文 / English |
| 构建工具 | Vite 4.5 |
| UI 框架 | Ant Design Vue 4.0 |
| 包管理器 | pnpm 11.3 |

---

## 二、技术栈

| 层级 | 技术 | 版本 |
|------|------|------|
| **框架** | Vue 3 (Composition API) | ^3.4.33 |
| **语言** | TypeScript | ^5.2.2 |
| **构建** | Vite | ^4.5.0 |
| **UI 库** | Ant Design Vue | ^4.0.7 |
| **路由** | Vue Router (History 模式) | 4.3.0 |
| **状态管理** | Pinia + persistedstate | ^2.1.7 |
| **HTTP** | Axios | ^1.6.1 |
| **国际化** | Vue I18n (Composition API) | ^9.6.5 |
| **图表** | ECharts + vue-echarts | ^5.5.1 |
| **地图** | OpenLayers (ol) + 高德地图 | ^7.4.0 |
| **视频** | EasyPlayer / Jessibuca / EasyWasmPlayer | SDK |
| **CSS** | Sass + UnoCSS + Animate.css | — |
| **3D** | Three.js | ^0.145.0 |
| **编辑器** | TinyMCE / CodeMirror / Vditor | — |
| **动画** | GSAP | — |
| **工具** | Lodash / Day.js / Crypto-js / html2canvas / xlsx | — |

---

## 三、目录结构

```
WEB/
├── index.html                         ← HTML 入口 (Jessibuca/EasyWasmPlayer/video.js)
├── package.json                       ← pnpm, Vue 3.4, Ant Design 4.0, Vite 4.5
├── vite.config.ts                     ← Vite 配置 (alias/proxy/plugins)
├── tsconfig.json                      ← TypeScript 严格模式
├── uno.config.ts                      ← UnoCSS 配置 (主色 #0960bd)
│
├── build/                             ← 构建配置
│   ├── constant.ts                    ← 输出目录/全局配置文件名
│   ├── utils.ts                       ← 环境变量包装器
│   ├── config/themeConfig.ts          ← 主题色生成
│   ├── generate/                      ← 主题变量/图标生成
│   ├── script/postBuild.ts            ← 构建后处理
│   └── vite/                          ← Vite 插件/代理/优化
│
├── conf/                              ← Nginx 配置
│   ├── nginx.conf                     ← 完整版 (511 行, Docker full/standard)
│   ├── nginx.mini.conf                ← Mini 版 (561 行)
│   └── nginx.prod-server.conf         ← 生产裸机版 (292 行)
│
├── Dockerfile                         ← 多阶段构建 (node:22 → nginx:1.29.2-alpine)
├── docker-compose.yaml                ← Docker Compose 部署
│
├── public/                            ← 静态资源
│   ├── resource/tinymce/              ← TinyMCE 编辑器
│   └── static/js/                     ← Jessibuca/EasyWasmPlayer/ZLMRTCClient
│
└── src/                               ← 源代码
    ├── main.ts                        ← 应用入口
    ├── App.vue                        ← 根组件
    │
    ├── api/                           ← API 接口层 (100+ 模块)
    │   ├── axios.ts                   ← Axios 实例 (baseURL/timeout/interceptors)
    │   ├── http.ts                    ← HTTP 工具 (get/post/put/del/customizeHttp)
    │   ├── base/                      ← 通用: login/user/upload/profile
    │   ├── device/                    ← 设备域: 37 个 API 模块
    │   ├── system/                    ← 系统域: 25 个 API 模块
    │   ├── infra/                     ← 基础设施: 12 个 API 模块
    │   ├── bpm/                       ← 工作流: 10 个 API 模块
    │   ├── member/                    ← 会员: 10 个 API 模块
    │   ├── pay/                       ← 支付: 6 个 API 模块
    │   ├── mp/                        ← 微信公众号: 11 个 API 模块
    │   └── modules/                   ← 实体视图/通知/任务
    │
    ├── views/                         ← 页面组件 (509 .vue 文件)
    │   ├── camera/ (100)              ← 📹 摄像头管理 (最大模块)
    │   ├── system/ (55)               ← ⚙️ 系统管理
    │   ├── node/ (56)                 ← 🖥️ 集群节点管理
    │   ├── notice/ (48)               ← 📬 通知管理
    │   ├── devices/ (43)              ← 📡 设备管理
    │   ├── dataset/ (36)              ← 📊 数据集管理
    │   ├── train/ (35)                ← 🧠 模型训练
    │   ├── infra/ (31)                ← 🔧 基础设施
    │   ├── base/ (23)                 ← 🏠 基础页面
    │   ├── gb28181/ (21)              ← 📹 GB28181 国标
    │   ├── product/ (16)              ← 📦 产品管理
    │   ├── visualize/ (16)            ← 📈 可视化大屏
    │   ├── dashboard/ (8)             ← 📊 监控大屏
    │   ├── alert/ (8)                 ← 🚨 告警管理
    │   ├── rulechains/ (5)            ← 🔗 规则链
    │   └── ota/ (3)                   ← ⬆️ OTA 升级
    │
    ├── components/                    ← 公共组件 (200 .vue 文件, 50+ 模块)
    │   ├── FormDesign/ (24)           ← 可视化表单设计器
    │   ├── TiandituMap/ (20)          ← 天地图组件
    │   ├── Table/ (15)                ← 高级表格
    │   ├── Form/ (13)                 ← 高级表单
    │   ├── Crontab/ (10)              ← Cron 表达式编辑
    │   ├── Application/ (9)           ← 应用级组件 (Logo/暗色/搜索)
    │   ├── VideoPlayer/ (9)           ← 视频播放器
    │   ├── SimpleMenu/ (7)            ← 简易菜单
    │   ├── EditDrawer/ (6)            ← 编辑抽屉
    │   └── ... (40+ more)             ← 上传/地图/裁剪/代码编辑器等
    │
    ├── router/                        ← 路由系统
    │   ├── index.ts                   ← createRouter (History 模式)
    │   ├── routes/index.ts            ← basicRoutes + asyncRoutes
    │   ├── routes/basic.ts            ← 静态路由 (login/404/特殊页面)
    │   ├── routes/modules/            ← 动态路由模块
    │   ├── guard/                     ← 9 个路由守卫
    │   ├── helper/                    ← 菜单/路由转换
    │   └── menus/                     ← 菜单构建 (3 种权限模式)
    │
    ├── store/                         ← Pinia 状态管理
    │   ├── modules/app.ts             ← 应用状态 (主题/布局)
    │   ├── modules/user.ts            ← 用户状态 (token/角色)
    │   ├── modules/permission.ts      ← 权限状态 (菜单/路由)
    │   ├── modules/locale.ts          ← 国际化
    │   ├── modules/dict.ts            ← 字典缓存
    │   ├── modules/multipleTab.ts     ← 多标签页
    │   └── ... (14 个 Store)
    │
    ├── layouts/                       ← 布局系统 (35 文件)
    │   ├── default/                   ← 默认布局 (Header+Sidebar+Tabs+Content)
    │   ├── page/                      ← 页面布局 (keep-alive+过渡)
    │   └── iframe/                    ← IFrame 布局
    │
    ├── locales/                       ← 国际化
    │   ├── lang/zh-CN/ (9 JSON)       ← 中文翻译
    │   ├── lang/en/ (8 JSON)          ← 英文翻译
    │   └── setupI18n.ts               ← 异步加载
    │
    ├── hooks/                         ← 组合式函数 (50+)
    │   ├── core/                      ← 核心 hooks
    │   ├── event/                     ← 事件 hooks
    │   ├── setting/                   ← 设置 hooks
    │   ├── web/                       ← 通用 web hooks
    │   └── design/                    ← 可视化编辑器 hooks
    │
    ├── directives/                    ← 自定义指令
    ├── enums/                         ← 枚举常量
    ├── settings/                      ← 项目配置
    ├── design/                        ← 设计系统
    ├── utils/                         ← 工具函数 (60+)
    ├── types/                         ← TypeScript 类型
    └── styles/                        ← 全局样式
```

---

## 四、启动流程

```
main.ts
  │
  ├── 1. createApp(App)
  ├── 2. setupPinia(app)              ← Pinia + persistedstate 插件
  ├── 3. initAppConfigStore()         ← 初始化应用配置 (主题/布局)
  ├── 4. JWT Token 同步 (VIDEO 子服务)
  ├── 5. registerGlobComp(app)        ← 全局组件注册
  ├── 6. setupCustomComponents(app)   ← 自定义设计组件
  ├── 7. setupI18n(app)               ← 异步加载国际化 (动态 import)
  ├── 8. setupRouter(app)             ← Vue Router 安装
  ├── 9. setupRouterGuard(router)     ← 9 个路由守卫
  ├── 10. setupGlobDirectives(app)    ← 全局指令 (clickOutside/loading/permission/ripple)
  ├── 11. setupErrorHandle(app)       ← 全局错误处理
  ├── 12. app.mount('#app')           ← 挂载
  └── 13. window['$vue'] = app        ← 调试暴露
```

**App.vue 渲染树**:
```
<ConfigProvider :theme="antdTheme" :locale="getAntdLocale">   ← Ant Design 主题/国际化
  <App>                                                         ← Ant Design App 容器
    <AppProvider>                                               ← 自定义应用上下文
      <RouterView />                                            ← 路由渲染
    </AppProvider>
  </App>
</ConfigProvider>
```

---

## 五、路由系统

### 5.1 路由架构

```
Router (History 模式)
  │
  ├── basicRoutes (静态路由, 始终加载)
  │     ├── /login              ← 登录
  │     ├── /sso                ← 单点登录
  │     ├── /                   ← 根路径 (重定向到 /dashboard)
  │     ├── /profile/index      ← 个人中心
  │     ├── /codegen/editTable  ← 代码生成器
  │     ├── /job/job-log        ← 定时任务日志
  │     ├── /gb28181-view/*     ← GB28181 通道/录像
  │     ├── /face-manage/:id    ← 人脸库详情
  │     ├── /plate-manage/:id   ← 车牌库详情
  │     ├── /record-space-manage/:id ← 录像回放 (NFS 共享存储归档)
  │     ├── /snap-space-manage/:id   ← 快照空间
  │     ├── /dataset/sam-model-setup ← SAM 模型安装
  │     ├── /algorithm-post-process/:id ← 算法后处理 IDE
  │     ├── /redirect/:path     ← 路由重定向
  │     └── /:path(.*)*         ← 404
  │
  └── asyncRoutes (动态路由, 登录后按权限加载)
        ├── /dashboard/index    ← 监控大屏
        ├── /about/index        ← 关于
        ├── /node/index         ← 集群管理
        └── /rulechains/index   ← 规则链 (Node-RED)
```

### 5.2 三种权限模式

| 模式 | 菜单/路由来源 | 说明 |
|------|-------------|------|
| **ROLE** | 前端固定 | 按角色编码过滤路由，简单快速 |
| **ROUTE_MAPPING** (默认) | 前端固定 + 后端映射 | 后端返回权限码，前端映射路由 |
| **BACK** | 后端动态 | 后端完全控制菜单+路由结构 |

### 5.3 路由守卫链 (9 个)

```
导航触发
  ├── pageGuard          ← 页面状态守卫
  ├── pageLoadingGuard   ← 页面加载进度条
  ├── httpGuard          ← HTTP 请求管理
  ├── scrollGuard        ← 滚动位置恢复
  ├── messageGuard       ← 消息提示关闭
  ├── progressGuard      ← NProgress 进度条
  ├── permissionGuard    ← 权限校验 (核心)
  │     ├── Token 检查
  │     ├── 用户信息加载
  │     ├── 动态路由构建
  │     └── 权限重定向
  ├── paramMenuGuard     ← 参数菜单守卫
  └── stateGuard         ← 状态清除守卫
```

---

## 六、状态管理 (Pinia)

### 6.1 核心 Store

| Store | ID | 持久化 | 职责 |
|-------|----|--------|------|
| **app** | `app` | 是 | 主题(dark/light)、布局模式、页面加载、组件尺寸 |
| **user** | `app-user` | 是(AES加密) | Token(access/refresh)、用户信息、角色列表、登录/登出/短信登录 |
| **permission** | `app-permission` | 否 | 权限码、菜单列表、动态路由构建(3种模式) |
| **locale** | `app-locale` | 是 | 语言切换(zh-CN/en)、Ant Design 国际化 |
| **dict** | `app-dict` | 是(60s缓存) | 字典数据缓存 |
| **lock** | `app-lock` | 是 | 锁屏状态、密码验证 |
| **errorLog** | `app-error-log` | 是 | 客户端错误日志 |
| **multipleTab** | `app-multiple-tab` | 是 | 标签页管理(打开/关闭/缓存/排序) |
| **userMessage** | `userMessage` | 否 | 未读通知数量 |

### 6.2 可视化编辑器 Store

| Store | 职责 |
|-------|------|
| **chartEditStore** | 画布编辑状态(缩放/组件/选择/剪贴板) |
| **chartHistoryStore** | 撤销/重做栈 |
| **chartLayoutStore** | 编辑器面板布局 |
| **designStore** | 编辑器主题 |
| **packagesStore** | 组件包注册 |
| **settingStore** | 编辑器系统设置 |

### 6.3 持久化策略

```
Pinia Store
  └── persist 插件
        ├── localStorage 存储
        └── user Store 使用 AES 加密 (crypto-js)
```

---

## 七、API 接口层

### 7.1 请求链路

```
页面组件
  │
  ├── API 函数 (src/api/device/camera.ts)
  │     └── http.get/post/put/del(url, params)
  │           │
  │           ▼
  │     axiosInstance (src/api/axios.ts)
  │       ├── baseURL: /dev-api
  │       ├── timeout: ResultEnum.TIMEOUT
  │       ├── request interceptor: (透传)
  │       └── response interceptor:
  │             ├── code === SUCCESS → resolve(data)
  │             └── code !== SUCCESS → redirect to error page
  │
  ├── DEV 环境: Vite Proxy
  │     /dev-api → http://127.0.0.1:48080/admin-api
  │     /video-api → http://127.0.0.1:6000
  │     /ai-api → http://127.0.0.1:5000
  │     /srs-api → http://127.0.0.1:8080
  │     /zlm-api → http://127.0.0.1:6080
  │     /minio-api → http://127.0.0.1:9001
  │     /nodered → http://127.0.0.1:1880
  │
  └── PROD 环境: Nginx 反向代理
        /dev-api → gateway:48080/admin-api/
        /video-api → video-host:6000
        /ai-api → ai-host:5000
        ...
```

### 7.2 API 模块统计 (100+)

| 目录 | 模块数 | 职责 |
|------|--------|------|
| `api/base/` | 4 | 登录/用户/上传/个人中心 |
| `api/device/` | **37** | 摄像头/设备/产品/物模型/GB28181/训练/部署/数据集/OTA/告警/录像/快照/车牌/人脸/LLM/SAM/流转发/ONVIF/巡航/可视化（告警经 iot-sink MQTT 总线归档，录像经 iot-sink NFS→MinIO 归档） |
| `api/system/` | 25 | 用户/角色/菜单/部门/岗位/字典/通知/短信/邮件/OAuth2/租户/权限/日志 |
| `api/infra/` | 12 | 代码生成/文件/配置/定时任务/Redis/API日志 |
| `api/mp/` | 11 | 微信公众号(账号/菜单/消息/素材/标签) |
| `api/bpm/` | 10 | 工作流(流程/表单/任务/审批) |
| `api/member/` | 10 | 会员(地址/分组/等级/积分/签到) |
| `api/pay/` | 6 | 支付(渠道/订单/退款/回调) |
| `api/modules/` | 4 | 实体视图/通知/任务/用户 |

---

## 八、14 个业务域

### 8.1 业务域概览

```
┌─────────────────────────────────────────────────────────────────────┐
│                        WEB 管理控制台 — 14 业务域                      │
│                                                                     │
│  ┌──────────┐ ┌──────────┐ ┌──────────┐ ┌──────────┐ ┌──────────┐  │
│  │ Dashboard│ │ Camera  │ │ Devices │ │ Product │ │ GB28181 │  │
│  │ 监控大屏  │ │ 摄像头  │ │ 设备管理 │ │ 产品管理 │ │ 国标视频 │  │
│  │  8 组件  │ │ 100组件 │ │ 43 组件 │ │ 16 组件 │ │ 21 组件 │  │
│  └──────────┘ └──────────┘ └──────────┘ └──────────┘ └──────────┘  │
│                                                                     │
│  ┌──────────┐ ┌──────────┐ ┌──────────┐ ┌──────────┐ ┌──────────┐  │
│  │  Train   │ │ Dataset │ │  Alert   │ │  Notice  │ │  Node   │  │
│  │ 模型训练  │ │ 数据集  │ │ 告警管理 │ │ 通知管理 │ │ 集群管理 │  │
│  │ 35 组件  │ │ 36 组件 │ │  8 组件  │ │ 48 组件 │ │ 56 组件 │  │
│  └──────────┘ └──────────┘ └──────────┘ └──────────┘ └──────────┘  │
│                                                                     │
│  ┌──────────┐ ┌──────────┐ ┌──────────┐ ┌──────────┐              │
│  │  System  │ │  Infra   │ │Visualize│ │OTA/Rules│              │
│  │ 系统管理  │ │ 基础设施 │ │ 可视化   │ │ OTA/规则│              │
│  │ 55 组件  │ │ 31 组件 │ │ 16 组件 │ │ 8 组件  │              │
│  └──────────┘ └──────────┘ └──────────┘ └──────────┘              │
└─────────────────────────────────────────────────────────────────────┘
```

### 8.2 各域详细信息

#### 📹 Camera (100 组件) — 摄像头管理

**核心子模块**:
| 子模块 | 功能 |
|--------|------|
| VideoCardList | 视频卡片列表 (主页面) |
| DeviceCreate | 摄像头添加向导 (GB28181/ONVIF/RTSP/海康/大华) |
| Gb28181DeviceCard | GB28181 设备卡片 (通道选择/PTZ控制) |
| NvrDeviceCard | NVR 设备卡片 (多通道管理) |
| AlgorithmTask | 算法任务管理 (实时/快照分析) |
| PlaybackList | 录像回放列表 (关联 iot-sink NFS→MinIO 归档链路) |
| SnapTask | 快照任务管理 |
| StreamForward | 流转发管理 |
| FrameExtractor | 帧提取服务 |
| SplitScreenMonitor | 分屏监控 (1/4/9/16 画面) |
| FaceLibrary | 人脸库管理 |
| PlateLibrary | 车牌库管理 |
| RecordSpace | 录像空间管理 (关联 NFS 共享存储 / Ceph 拓扑面板) |
| ScenarioPoseLibrary | 场景姿态库 |

#### ⚙️ System (55 组件) — 系统管理

| 子模块 | 功能 |
|--------|------|
| user | 用户管理 (CRUD/导入导出/重置密码) |
| role | 角色管理 (权限分配/数据范围) |
| menu | 菜单管理 (树形结构/按钮权限) |
| dept | 部门管理 (组织架构树) |
| post | 岗位管理 |
| dict | 字典管理 (类型/数据) |
| sms | 短信管理 (模板/签名/日志) |
| mail | 邮件管理 (模板/账户/日志) |
| oauth2 | OAuth2 客户端管理 |
| tenant | 多租户管理 (套餐/租户) |
| notify | 站内信管理 |
| area | 行政区划管理 |
| loginLog | 登录日志 |
| operateLog | 操作日志 |

#### 🖥️ Node (56 组件) — 集群节点管理

| 子模块 | 功能 |
|--------|------|
| NodeList | 节点列表 (注册/心跳/GPU指标) |
| WorkloadDeploy | 工作负载部署 |
| MediaStackDeploy | 媒体堆栈部署 (SRS/ZLM) |
| MqttStackDeploy | MQTT堆栈部署 (EMQX) |
| GPUClusterMonitor | GPU 集群监控 |
| NodeCheck | 节点健康检查 |
| CephTopologyPanel | NFS 共享媒体节点拓扑面板 (命名沿用，语义为 NFS 共享媒体拓扑；center/nodes/links/summary 可视化，对应后端 NodeCephTopologyRespVO) |
| MediaPathReadinessBar | 媒体路径就绪状态条 (alert_images/playbacks/snaps 子目录探测，对应 iot-sink NfsMediaPathResolver 与 iot-node check_nfs_health) |
| MediaEnvBatch | 媒体环境批量配置 (NFS 服务端/客户端分配、mountRoot/nfsExport/nfsMountOpts，对应 NodeNfsClusterAssignReqVO) |

#### 📡 Devices (43 组件) — 设备管理

| 子模块 | 功能 |
|--------|------|
| DeviceList | 设备列表 (注册/状态/标签) |
| DeviceControl | 设备控制 (指令下发) |
| DeviceShadow | 设备影子 (期望/上报状态对比) |
| DeviceLog | 设备日志 (上行/下行/事件) |
| PhysicalModel (TSL) | 物模型编辑器 (属性/服务/事件) |
| OTA | OTA 固件管理 |

#### 🧠 Train (35 组件) — 模型训练

| 子模块 | 功能 |
|--------|------|
| TrainTask | 训练任务 (YOLO微调/超参/进度) |
| ModelManager | 模型管理 (版本/权重/下载) |
| DeployService | 部署服务 (集群推理/负载均衡) |
| ExportTask | 模型导出 (ONNX/TensorRT/OpenVINO) |
| InferenceTask | 推理任务 (图片/视频/流) |
| LLMManager | 大模型管理 (Qwen/DeepSeek) |
| LLMDeploy | 大模型部署 + 节点容量 |

#### 📊 Dataset (36 组件) — 数据集管理

| 子模块 | 功能 |
|--------|------|
| DatasetList | 数据集列表 (YOLO/COCO/ImageFolder) |
| AnnotationTool | 标注工具 (矩形/多边形/关键点) |
| AutoLabel | SAM 自动标注 |
| ImageUpload | 图片批量上传 |
| DatasetExport | 格式导出 |

#### 📹 GB28181 (21 组件) — 国标视频平台

| 子模块 | 功能 |
|--------|------|
| DeviceCatalog | 设备目录 (组织/区域/设备) |
| ChannelList | 通道列表 |
| SplitScreen | 分屏监控 |
| CloudRecord | 云端录像 (关联 NFS 共享存储归档) |
| DeviceRecord | 设备端录像 (关联 NFS 共享存储归档) |
| PullProxy | 拉流代理 |

#### 📈 Visualize (16 组件) — 可视化大屏

| 子模块 | 功能 |
|--------|------|
| ProjectManager | 大屏项目管理 |
| ScreenDesigner | 可视化编辑器 (拖拽/组件/数据源) |
| DashboardTemplate | 模板库 |

---

## 九、公共组件库 (50+ 模块, 200 组件)

### 9.1 核心组件

| 组件 | 文件数 | 说明 |
|------|--------|------|
| **FormDesign** | 24 | 可视化表单设计器 (VFormDesign/VFormCreate/VFormItem) |
| **TiandituMap** | 20 | 天地图地图组件 (核心/组合式/业务) |
| **Table** | 15 | 高级表格 (可编辑/列设置/搜索/导出) |
| **Form** | 13 | 高级表单 (ApiSelect/ApiCascader/ApiTreeSelect) |
| **Crontab** | 10 | Cron 表达式编辑器 (秒/分/时/日/月/周可视化) |
| **Application** | 9 | Logo/暗色切换/语言切换/全局搜索/应用上下文 |
| **VideoPlayer** | 9 | 视频播放器监控模块 |
| **SimpleMenu** | 7 | 简易侧边栏菜单 |
| **EditDrawer** | 6 | 编辑抽屉 (属性/数据上传) |

### 9.2 特色组件

| 组件 | 用途 |
|------|------|
| **EasyPlayer / EasyWasmPlayer** | H.265/H.264 视频流播放 (WebSocket/WebRTC) |
| **Jessibuca** | 纯 JS 视频播放器 (H.264) |
| **MapBaseMapSwitcher** | 地图底图切换器 |
| **MapLayerSwitcher** | 地图图层切换器 |
| **PoseSkeletonOverlay** | 姿态骨架叠加层 |
| **GpuStackMonitorTip** | GPU 监控提示 |
| **ViewModeSwitcher** | 视图模式切换 (卡片/表格/网格) |
| **CodeEditor** | 代码/JSON 编辑器 |
| **Markdown** | Markdown 编辑器 |
| **Cropper** | 图片裁剪器 |
| **Excel** | Excel 导入/导出 |
| **CountTo** | 数字动画 |
| **VirtualScroll** | 虚拟滚动列表 |

---

## 十、布局系统

### 10.1 三种布局

| 布局 | 文件 | 用途 |
|------|------|------|
| **Default** | `layouts/default/index.vue` | 主应用外壳 (Header + Sidebar + Tabs + Content + Footer) |
| **Page** | `layouts/page/index.vue` | 独立页面 (keep-alive 缓存 + 过渡动画) |
| **IFrame** | `layouts/iframe/index.vue` | 内嵌外部页面 (规则链/可视化大屏) |

### 10.2 Default 布局结构

```
<Layout>
  ├── <Header>
  │     ├── Breadcrumb           ← 面包屑导航
  │     ├── FullScreen           ← 全屏切换
  │     ├── ErrorAction          ← 错误日志入口
  │     ├── Notify               ← 通知铃铛 (站内信/告警)
  │     ├── Lock                 ← 锁屏
  │     └── UserDropdown         ← 用户头像下拉 (个人中心/退出)
  │
  ├── <Layout>
  │     ├── <Sider>              ← 侧边栏菜单
  │     │     ├── Menu          ← 权限过滤的菜单树
  │     │     └── DragBar       ← 侧边栏拖拽调整
  │     │
  │     └── <Content>
  │           ├── <MultipleTabs> ← 多标签页
  │           └── <RouterView /> ← 页面内容 (keep-alive)
  │
  └── <Footer>                   ← 页脚
```

---

## 十一、国际化 (i18n)

```
locales/
├── setupI18n.ts                      ← 异步初始化 (dynamic import)
├── useLocale.ts                      ← 切换语言 composable
├── lang/
│   ├── zh-CN/                        ← 中文 (9 文件)
│   │   ├── common.json               ← 通用文本
│   │   ├── action.json               ← 操作按钮
│   │   ├── component.json            ← 组件文本
│   │   ├── layout.json               ← 布局文本
│   │   ├── profile.json              ← 个人中心
│   │   ├── sys.json                  ← 系统管理
│   │   ├── routes/basic.json         ← 基础路由标题
│   │   ├── routes/dashboard.json     ← 仪表盘路由
│   │   └── antdLocale/DatePicker.json ← Ant Design 日期组件
│   │
│   └── en/                           ← 英文 (8 文件, 同上)
│
settings/localeSetting.ts             ← 语言配置 (zh_CN/en, 默认/回退)
store/modules/locale.ts               ← 语言 Store (持久化)
```

**语言切换流程**:
```
用户点击切换 → localeStore.setLocale('en')
  → useLocale().changeLocale()
    → dynamic import('@/locales/lang/en.ts')
    → i18n.global.setLocaleMessage('en', messages)
    → i18n.global.locale = 'en'
    → Ant Design ConfigProvider :locale 更新
```

---

## 十二、构建与部署

### 12.1 多环境配置

| 环境文件 | 场景 | 关键配置 |
|----------|------|---------|
| `.env` | 基础配置 | Port 8888, 标题, 租户启用, full 规格 |
| `.env.development` | 本地开发 | Vite Proxy 到所有后端服务 |
| `.env.production` | Docker 生产 | Nginx 反向代理 |
| `.env.test` | 测试环境 | Gzip 压缩, 验证码关闭 |
| `.env.static` | 静态部署 | 直连 localhost:48080 |
| `.env.front` | 前端独立部署 | 远程代理 doc.basiclab.top |

### 12.2 Docker 构建

```
Dockerfile (多阶段构建)
  ├── Stage 1: Builder (node:22-alpine3.21)
  │     ├── pnpm install (Registry 镜像 + 缓存挂载)
  │     ├── vite build
  │     └── postBuild.ts (配置注入)
  │
  └── Stage 2: Runtime (nginx:1.29.2-alpine)
        ├── 复制 nginx.conf
        ├── 复制构建产物
        ├── 健康检查: http://localhost/health
        └── 暴露端口: 80
```

### 12.3 Nginx 三套配置

| 配置 | 行数 | 用途 |
|------|------|------|
| `nginx.conf` | 511 | Docker full/standard: 反代全部微服务 (gateway/video/ai/srs/zlm/nodered/minio) |
| `nginx.mini.conf` | 561 | Docker mini: 无 gateway/GB28181, 直连 iot-system |
| `nginx.prod-server.conf` | 292 | 裸机/JAR 部署: 4 个 server block (WEB/Visualize/FUXA/file) |

---

## 十三、核心架构模式

| 模式 | 说明 |
|------|------|
| **Composition API** | 全部组件使用 `<script setup lang="ts">` |
| **动态路由 + 权限** | 3 种权限模式 (ROLE/ROUTE_MAPPING/BACK) 动态构建菜单 |
| **多标签页** | keep-alive 缓存已打开页面，减少重复渲染 |
| **Pinia 持久化** | 用户 Token/主题/语言/标签页持久化到 localStorage |
| **Axios 拦截器** | 统一错误处理/Token 刷新/重定向 |
| **Vite Proxy** | 开发环境代理解决跨域，生产环境 Nginx |
| **异步 i18n** | 语言包按需动态加载，避免构建体积膨胀 |
| **全局组件注册** | `registerGlobComp` 批量注册 Ant Design 高频组件 |
| **monorepo 子包** | 部分特殊页面独立打包 (可视化编辑器/算法 IDE) |
| **响应式设计** | Ant Design 断点 + 自定义 breakpoint hooks |

---

## 十四、视频播放方案

平台支持多种视频播放协议和场景：

| 播放器 | 协议 | 用途 |
|--------|------|------|
| **EasyPlayer** | HTTP-FLV / WebSocket / HLS | 主流实时播放 |
| **EasyWasmPlayer** | WebSocket | H.265 WebAssembly 软解 |
| **Jessibuca** | WebSocket / HTTP | 纯 JS 高性能播放器 |
| **ZLMRTCClient** | WebRTC | 超低延迟 (ZLMediaKit) |
| **jsmpeg** | WebSocket MPEG1 | 兼容降级方案 |

---

## 十五、地图方案

| 地图引擎 | 用途 |
|----------|------|
| **天地图 (Tianditu)** | 2D/卫星/地形底图，设备位置标注，GIS 分析 |
| **高德地图 (AMap)** | 国内地址解析、POI 搜索、路径规划 |
| **OpenLayers (ol)** | 通用地图框架，支持 WMS/WMTS/TMS/矢量切片 |

---

## 十六、修订记录

| 版本 | 日期 | 变更摘要 | 触发来源 |
|------|------|----------|----------|
| V9.18.0 | 2026-08-12 | Node 节点页新增 NFS/Ceph 拓扑相关 3 个组件（CephTopologyPanel/MediaPathReadinessBar/MediaEnvBatch），组件计数 53→56；录像/告警 API 注释关联 iot-sink MQTT 总线与 NFS→MinIO 归档新链路 | commits f13c491d/3b7c7f5c/242f8f31/ac3b08a6/28a2c318/42945f3f/7c47d3b9 |

---

> **一句话总结:** WEB 模块是 EasyAIot 的 Vue 3 全功能管理控制台，747 个组件覆盖 14 个业务域，通过 9 层路由守卫 + 3 种权限模式 + 100+ API 模块 + Pinia 状态管理，实现了从摄像头接入、设备管理、AI 训练部署到告警通知、可视化大屏的完整前端能力闭环。
