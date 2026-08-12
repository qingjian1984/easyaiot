# Presentation Layer — APP 移动端详细架构

> 基于整体架构文件 V9.18.0 + APP 源码深入分析
> 代码规模: 80 个 Vue 组件 / 85 TypeScript 文件 / uni-app 3 + Vue 3 + Vite 5

---

## 一、总体定位

APP 模块是 EasyAIot 的 **跨平台移动管理后台**，与 WEB 模块共享同一套后端 API (`/admin-api`)，提供设备管理、视频监控、AI 推理、告警处理等核心功能的移动端访问能力。

| 指标 | 数据 |
|------|------|
| Vue 组件总数 | 80 |
| TypeScript 文件 | 85 |
| 底部导航标签 | 8 个 |
| Pinia Store | 4 个 (token/user/dict/theme) + 1 Tabbar reactive |
| 支持平台 | H5 / 微信小程序 / 原生 App |
| 框架 | uni-app 3 + Vue 3 |
| 构建工具 | Vite 5.2 |
| UI 框架 | Wot Design Uni 2.x |
| 样式方案 | UnoCSS 66 + SCSS |
| 包管理器 | pnpm 10 |
| 部署端口 | 80 (容器) / 9010 (宿主机映射) |
| 部署规格 | 仅 full 规格 |

---

## 二、技术栈

| 层级 | 技术 | 版本 |
|------|------|------|
| **框架** | uni-app 3 (Vue 3 Composition API) | 3.x |
| **语言** | TypeScript | — |
| **构建** | Vite + @dcloudio/vite-plugin-uni | 5.2.8 |
| **UI 库** | Wot Design Uni (@wot-ui/ui) | 2.x |
| **样式** | UnoCSS + SCSS | 66.x |
| **路由** | 约定式路由 (uni-pages 自动生成) | — |
| **状态管理** | Pinia + persistedstate | — |
| **HTTP** | uni.request 自研封装 (双Token/加密) | — |
| **表单校验** | Wot UI + createFormSchema | — |
| **国际化** | Vue I18n | 9.x |
| **列表分页** | z-paging | 2.x |
| **图表** | ECharts | 5.x |
| **视频播放** | Jessibuca (H5) / flv.js | — |
| **代码规范** | ESLint + @uni-helper/eslint-config | — |

---

## 三、项目结构

```
APP/
├── package.json                  ← pnpm 10, uni-app 3, Vue 3, Vite 5
├── vite.config.ts                ← Vite 主配置 (插件/代理/优化)
├── manifest.config.ts            ← uni-app manifest 动态配置
├── pages.config.ts               ← 页面配置 (生成 pages.json)
├── tsconfig.json                 ← TypeScript 配置
├── uno.config.ts                 ← UnoCSS 配置
├── eslint.config.mjs             ← ESLint 配置
│
├── env/                          ← 环境变量
│   ├── .env                      ← 公共配置
│   ├── .env.development          ← 开发环境 (Vite Proxy)
│   ├── .env.production           ← 生产环境 (Nginx 代理)
│   └── .env.test                 ← 测试环境
│
├── conf/
│   └── nginx.conf                ← Nginx 生产配置 (9010)
│
├── Dockerfile                    ← 多阶段构建 (node:22 → nginx:1.29.2-alpine)
├── docker-compose.yaml           ← Docker Compose 部署
│
├── vite-plugins/                 ← 自定义 Vite 插件
│   ├── copy-native-resources/    ← 原生资源复制
│   └── sync-manifest-plugins/    ← Manifest 同步
│
├── scripts/                      ← 构建辅助脚本
│   ├── upload-weixin.js          ← 微信小程序上传
│   ├── bump-version.js           ← 版本号管理
│   └── dev-h5.sh                 ← H5 开发启动
│
├── docs/                         ← VitePress 文档站点
│
└── src/                          ← === 核心源码 ===
    ├── main.ts                   ← 应用入口
    ├── App.vue                   ← 根组件 (子包入口)
    ├── App.ku.vue                ← KuRoot 根组件 (主题/Tabbar/Toast)
    │
    ├── pages/                    ← 主包页面 (Tabbar 页面, 8 个标签)
    │   ├── device/               ← 设备管理 + GB28181 + NVR
    │   ├── stream-forward/       ← 推流转发
    │   ├── algorithm/            ← 算法任务
    │   ├── alert/                ← 告警中心
    │   ├── model/                ← 模型管理
    │   ├── inference/            ← 模型推理
    │   ├── train/                ← 模型训练
    │   ├── index/                ← 首页仪表盘
    │   ├── message/              ← 消息通知
    │   ├── contact/              ← 联系我们
    │   └── user/                 ← 个人中心
    │
    ├── pages-core/               ← 分包页面 (独立加载)
    │   ├── auth/                 ← 登录/注册/短信登录/忘记密码
    │   ├── error/                ← 404 / PC 端提示
    │   └── user/                 ← 个人资料/安全设置/FAQ/反馈
    │
    ├── components/               ← 共享组件
    │   ├── app-nav-user-button.vue
    │   ├── jessibuca-player.vue  ← Jessibuca 视频播放器
    │   ├── live-stream-player.vue
    │   ├── dict-tag.vue
    │   ├── wd-popup.vue
    │   └── yudao-ui/             ← 业务组件 (表单/上传/树选择/搜索)
    │       ├── yd-form-picker/
    │       ├── yd-search-date-range/
    │       ├── yd-search-picker/
    │       ├── yd-tree-select/
    │       ├── yd-upload/
    │       ├── yd-upload-file/
    │       └── yd-upload-imgs/
    │
    ├── layouts/
    │   └── default.vue           ← 默认布局 (<slot />)
    │
    ├── router/                   ← 路由系统
    │   ├── config.ts             ← 白名单/黑名单/登录页配置
    │   └── interceptor.ts        ← 登录守卫 (Token 检查 → 跳转登录)
    │
    ├── tabbar/                   ← 自定义底部导航
    │   ├── config.ts             ← 4 种 Tabbar 策略
    │   ├── store.ts              ← Tabbar 状态 (当前选中)
    │   ├── index.vue             ← Tabbar 渲染组件 (UnoCSS 图标)
    │   └── types.ts              ← Tabbar 类型定义
    │
    ├── http/                     ← HTTP 层
    │   ├── http.ts               ← 请求核心 (GET/POST/PUT/DELETE + 双Token刷新)
    │   ├── interceptor.ts        ← 请求拦截器 (Token/租户/加密)
    │   └── types.ts              ← 请求/响应类型
    │
    ├── api/                      ← API 接口层
    │   ├── login.ts              ← 认证 (登录/注册/验证码/Token刷新)
    │   ├── video/                ← 视频域 (camera/gb28181/alert/algorithm/streamForward/node)
    │   ├── model/                ← AI域 (model/inference/train)
    │   ├── system/               ← 系统域 (user/dept/dict/notify)
    │   └── infra/file/           ← 文件上传
    │
    ├── store/                    ← Pinia 状态管理
    │   ├── token.ts              ← Token 状态 (access/refresh Token)
    │   ├── user.ts               ← 用户信息 (角色/权限/租户)
    │   ├── dict.ts               ← 字典缓存 (重试机制)
    │   └── theme.ts              ← 主题管理 (亮/暗)
    │
    ├── hooks/                    ← 组合式函数 (自动导入)
    │   ├── useAccess.ts          ← 权限判断
    │   ├── useDict.ts            ← 字典工具
    │   ├── useRequest.ts         ← 请求封装
    │   ├── useRouteQuery.ts      ← 路由参数
    │   └── useUpload.ts          ← 文件上传
    │
    ├── utils/                    ← 工具函数 (20+)
    │   ├── encrypt.ts            ← AES/RSA 加解密
    │   ├── wot.ts                ← Wot UI 表单校验
    │   ├── validator.ts          ← 邮箱/手机号校验
    │   ├── tree.ts               ← 树形数据处理
    │   ├── video/                ← 视频工具
    │   └── model/                ← 模型工具
    │
    ├── style/                    ← 全局样式 (SCSS + iconfont)
    ├── static/                   ← 静态资源 (Jessibuca 播放器 JS)
    └── types/                    ← TypeScript 类型声明
```

---

## 四、启动流程

```
main.ts
  │
  ├── 1. createSSRApp(App)              ← uni-app SSR 应用
  ├── 2. setupPinia(app)                ← Pinia + persistedstate (uni Storage)
  ├── 3. setupI18n(app)                 ← Vue I18n 初始化
  ├── 4. setupRouterInterceptor()       ← 路由拦截器注册
  ├── 5. setupTabbar()                  ← 自定义 Tabbar 初始化
  ├── 6. app.mount('#app')              ← 挂载
  └── 7. HTTP 拦截器 (懒加载)            ← uni.request 拦截器注册
```

**路由守卫流程:**

```
页面导航
  │
  ├── 检查: 是否需要登录?
  │     ├── 白名单路由 → 直接放行
  │     └── 需要登录 → 检查 Token
  │           ├── Token 有效 → 放行
  │           └── Token 无效 → 跳转登录页
  │
  └── HTTP 层 401 响应
        ├── 触发 refreshToken 刷新
        ├── 刷新成功 → 重放原请求
        └── 刷新失败 → 跳转登录页
```

---

## 五、页面与路由

### 5.1 底部导航 (8 个 Tabbar 标签)

```
┌──────────┬──────────┬──────────┬──────────┬──────────┬──────────┬──────────┬──────────┐
│  设备     │  推流     │  算法     │  告警     │  模型     │  推理     │  训练     │  我的     │
│  device  │ forward  │algorithm │  alert   │  model   │inference │  train   │  user    │
└──────────┴──────────┴──────────┴──────────┴──────────┴──────────┴──────────┴──────────┘
```

### 5.2 页面详情

| 标签 | 路径 | 子页面 | 核心功能 |
|------|------|--------|----------|
| **设备** | `/pages/device/index` | GB28181 设备, NVR 通道 | 设备列表/搜索/详情/创建/编辑, 流启动/停止 |
| **推流** | `/pages/stream-forward/index` | — | 推流任务 CRUD, 状态监控, 日志查看 |
| **算法** | `/pages/algorithm/index` | — | 算法任务 CRUD, 启停控制 |
| **告警** | `/pages/alert/index` | — | 告警列表/搜索/详情, 录像回放 |
| **模型** | `/pages/model/index` | — | 模型列表/详情, 类别查询 |
| **推理** | `/pages/inference/index` | — | 图片上传, AI 推理, 结果面板 |
| **训练** | `/pages/train/index` | — | 训练任务 CRUD, GPU 状态, 数据集上传, 权重发布 |
| **我的** | `/pages/user/index` | 首页/消息/联系我们 | 个人中心, 租户切换, 退出登录 |

### 5.3 分包页面 (pages-core)

| 分类 | 页面 | 功能 |
|------|------|------|
| **认证** | login | 账号密码登录 + 租户选择 |
| | register | 新用户注册 |
| | code-login | 短信验证码登录 |
| | forget-password | 忘记密码重置 |
| **错误** | 404 | 页面不存在 |
| | pc-only | PC 端访问提示 |
| **用户** | profile | 个人资料编辑 |
| | security | 密码修改 |
| | faq | 常见问题 |
| | feedback | 意见反馈 |
| | settings/agreement | 用户协议 |
| | settings/privacy | 隐私政策 |

### 5.4 路由策略

**白名单模式** (默认): 所有页面需要登录，白名单页面例外。

```
路由检查
  ├── EXCLUDE_LOGIN_PATH_LIST (白名单)
  │     ├── /pages-core/auth/login
  │     ├── /pages-core/auth/register
  │     ├── /pages-core/auth/code-login
  │     └── /pages-core/auth/forget-password
  │
  ├── definePage({ excludeLoginPath: true }) 标记的页面 → 免登录
  └── 其他所有页面 → 需登录检查
```

---

## 六、状态管理 (5 个 Pinia Store)

| Store | 文件 | 持久化 | 存储方式 | 职责 |
|-------|------|--------|----------|------|
| **token** | `store/token.ts` | ✅ | uni.setStorageSync | accessToken + refreshToken, 登录/登出/刷新 |
| **user** | `store/user.ts` | ✅ | uni.setStorageSync | 用户信息/角色/权限/租户ID |
| **dict** | `store/dict.ts` | ✅ | uni.setStorageSync | 字典数据缓存 (页面可见性变化自动重载) |
| **theme** | `store/theme.ts` | ✅ | uni.setStorageSync | 亮色/暗色主题切换 (store ID: `theme-store`) |
| **tabbarState** | `tabbar/store.ts` | ❌ | reactive (内存) | 当前选中的 Tabbar 索引 (非 Pinia Store) |

---

## 七、HTTP 层

### 7.1 请求链路

```
页面调用 API
  │
  ▼
http.get/post/put/delete(url, params)
  │
  ├── 请求拦截器 (src/http/interceptor.ts)
  │     ├── Authorization: Bearer <accessToken>
  │     ├── tenant-id: <当前租户ID>
  │     └── X-Api-Encrypt (可选: AES/RSA 加密)
  │
  ├── uni.request (原生 HTTP)
  │     ├── H5: Vite Proxy (/admin-api → localhost:48080)
  │     └── App/小程序: 直连 VITE_SERVER_BASEURL
  │
  └── 响应处理 (src/http/http.ts)
        ├── code === 0/200 → resolve(data)
        ├── code === 401 → 触发双Token刷新
        │     ├── 刷新成功 → 重放原请求
        │     └── 刷新失败 → 跳转登录页 + Toast
        └── 其他错误 → Toast 提示
```

### 7.2 安全特性

| 特性 | 实现 |
|------|------|
| **双 Token 认证** | accessToken (短有效期) + refreshToken (长有效期), 401 时自动排队刷新 |
| **API 加密** | AES-ECB / RSA 可选手动加解密, 请求头 `X-Api-Encrypt` 标识 |
| **多租户** | 请求头 `tenant-id` 自动注入, 支持运行时切换 |
| **Token 持久化** | uni.setStorageSync 持久化, 支持 App/小程序原生存取 |

### 7.3 请求选项

| 选项 | 作用 |
|------|------|
| `hideErrorToast` | 静默错误 (不弹 Toast) |
| `original` | 返回原始响应对象 |
| `isEncrypt` | 启用请求加密 |

---

## 八、Tabbar 策略

`src/tabbar/config.ts` 提供 4 种底部导航策略:

| 模式 | 值 | 描述 | 缓存 |
|------|-----|------|------|
| NO_TABBAR | 0 | 无 Tabbar (全屏页面) | — |
| NATIVE_TABBAR | 1 | uni-app 原生 Tabbar (switchTab) | ✅ |
| **CUSTOM_TABBAR_WITH_CACHE** | **2** | **自定义 Tabbar + 缓存** (当前使用) | ✅ |
| CUSTOM_TABBAR_WITHOUT_CACHE | 3 | 自定义 Tabbar + navigateTo | ❌ |

**当前使用模式 2**，特点是:
- 使用 `uni.switchTab` 切换 (支持页面缓存)
- 自定义渲染组件 (`tabbar/index.vue`)
- UnoCSS 图标渲染 (不依赖字体图标库)
- 8 个标签页带角标支持

---

## 九、API 接口层

### 9.1 认证 API

| 端点 | 方法 | 功能 |
|------|------|------|
| `/system/auth/login` | POST | 账号密码登录 |
| `/system/auth/register` | POST | 用户注册 |
| `/system/auth/sms-login` | POST | 短信验证码登录 |
| `/system/auth/refresh-token` | POST | Token 刷新 |
| `/system/auth/get-permission-info` | GET | 权限信息 |
| `/system/auth/logout` | POST | 退出登录 |
| `/system/captcha/get` | POST | 图形验证码 |

### 9.2 视频/设备 API

| 模块 | 端点前缀 | 功能 |
|------|----------|------|
| **camera** | `/video/camera/` | 设备 CRUD, 流启动/停止, secure_link 签名, NVR 管理, ONVIF 发现 |
| **gb28181** | `/video/gb28181/` | 国标设备查询, 通道列表, 实时点播 |
| **alert** | `/video/alert/` | 告警查询/删除, 统计, 录像回放 |
| **algorithm** | `/video/algorithm/` | 算法任务 CRUD, 启停控制 |
| **streamForward** | `/video/stream-forward/` | 推流任务 CRUD, 状态, 日志 |
| **node** | `/video/node/` | 计算节点列表/详情 |

### 9.3 AI/模型 API

| 模块 | 端点前缀 | 功能 |
|------|----------|------|
| **model** | `/model/` | 模型 CRUD, 类别查询 |
| **inference** | `/model/inference/` | 图片推理 (上传+推理) |
| **train** | `/model/train/` | 训练任务, GPU 状态, 数据集上传, 权重发布 |

### 9.4 系统 API

| 模块 | 端点前缀 | 功能 |
|------|----------|------|
| **user** | `/system/user/` | 用户管理, 个人资料 |
| **dict** | `/system/dict/data/` | 字典数据查询 |
| **notify** | `/system/notify/message/` | 站内信列表/已读 |
| **dept** | `/system/dept/` | 部门管理 |
| **file** | `/infra/file/` | 文件上传 |

---

## 十、视频播放方案

移动端视频播放采用 **Jessibuca** 纯 JS 解码器:

```
视频源
  │
  ├── SRS (HTTP-FLV / WS-FLV)     ← 标准直播
  └── ZLMediaKit (WS-FLV)          ← GB28181 国标流
        │
        ▼
  Nginx (secure_link 鉴权 + CORS)
        │
        ▼
  Jessibuca Player (H5)
  ├── /static/js/jessibuca/jessibuca.js  (全局加载)
  ├── components/jessibuca-player.vue    (Vue 组件封装)
  └── components/live-stream-player.vue  (多格式播放器)
```

---

## 十一、Docker 部署

### 11.1 构建流程

```
Dockerfile (多阶段)
  ├── Stage 1: Builder (node:22-alpine3.21)
  │     ├── pnpm install (BuildKit 缓存挂载)
  │     ├── vite build (H5 模式)
  │     └── 产物: dist/build/h5/
  │
  └── Stage 2: Runtime (nginx:1.29.2-alpine)
        ├── 复制 nginx.conf
        ├── 复制构建产物
        └── 暴露端口: 9010
```

### 11.2 Nginx 反向代理

`APP/conf/nginx.conf` 核心代理配置:

| 路径 | 代理目标 | 说明 |
|------|----------|------|
| `/` | `dist/build/h5/` | SPA 前端 (try_files fallback) |
| `/admin-api/` | `gateway:48080` | 管理后台 API (经过网关) |
| `/dev-api/model/` | AI 服务 | 模型管理 API |
| `/dev-api/ai/` | AI 服务 | AI 推理 API |
| `/dev-api/video/` | VIDEO 服务 | 视频管理 API |
| `/dev-api/nodeRed/` | NodeRED | 规则链编辑器 |
| `/dev-api/srs/` | SRS | 流媒体管理 API |
| `/rtp/` | ZLMediaKit | GB28181 RTP 流 |
| `/ai\|/live/` | SRS | HTTP-FLV / WS-FLV 直播流 |

**安全特性:** `secure_link_md5` 鉴权 (流地址签名验证，默认注释未启用)、WebSocket 升级、Gzip 压缩、CORS 处理。

---

## 十二、与 WEB 端的对比

| 维度 | WEB 管理控制台 | APP 移动端 |
|------|---------------|------------|
| **框架** | Vue 3.4 + Vite 4 | uni-app 3 + Vue 3 + Vite 5 |
| **UI 库** | Ant Design Vue 4.0 | Wot Design Uni 2.x |
| **组件数** | 747 | 83 |
| **业务域** | 14 个 (全功能) | 8 个 (核心功能) |
| **路由** | Vue Router History | 约定式路由 + pages.json |
| **状态管理** | 14 个 Pinia Store | 5 个 Pinia Store |
| **视频播放** | EasyPlayer / Jessibuca / jsmpeg | Jessibuca / flv.js |
| **部署端口** | 8888 (WEB) | 9010 (APP) |
| **平台** | 浏览器 (桌面/平板) | H5 / 微信小程序 / App |
| **部署规格** | 全部 3 种规格 | 仅 full 规格 |

**功能对比:**

| 功能域 | WEB | APP |
|--------|-----|-----|
| 设备管理 (camera) | ✅ 100 组件 | ✅ 核心 CRUD |
| 系统管理 (system) | ✅ 55 组件 | ✅ 个人资料 |
| 集群管理 (node) | ✅ 53 组件 | ✅ 节点查询 |
| 通知管理 (notice) | ✅ 48 组件 | ✅ 站内信 |
| 设备管理 (devices) | ✅ 43 组件 | ❌ |
| 数据集 (dataset) | ✅ 36 组件 | ❌ |
| 模型训练 (train) | ✅ 35 组件 | ✅ 核心功能 |
| 基础设施 (infra) | ✅ 31 组件 | ❌ |
| GB28181 (gb28181) | ✅ 21 组件 | ✅ 设备查询 |
| 可视化 (visualize) | ✅ 16 组件 | ❌ |
| 产品管理 (product) | ✅ 16 组件 | ❌ |
| 告警 (alert) | ✅ 8 组件 | ✅ 核心功能 |
| 仪表盘 (dashboard) | ✅ 8 组件 | ✅ 首页 |
| 推流转发 | ✅ | ✅ 核心功能 |

---

## 十三、修订记录

| 版本 | 日期 | 变更摘要 | 触发来源 |
|------|------|----------|----------|
| V9.18.0 | 2026-08-12 | 版本号同步对齐 V9.18.0；本文档内容（uni-app 移动端 / 路由 / 状态管理 / API 层 / 视频播放）未受 2026-08-11 NFS + MQTT + 删 EDGE 重构直接影响，基线版本跟随整体架构文件升级 | 基线同步 |

> **一句话总结:** APP 移动端是 EasyAIot 的 uni-app 3 跨平台移动管理后台，83 个 Vue 组件覆盖 8 个核心业务域（设备/推流/算法/告警/模型/推理/训练/个人），通过自定义 Tabbar + 约定式路由 + 双 Token 无感刷新 + API 加密 + Jessibuca 视频播放，实现了从设备监控、AI 推理到告警处理的完整移动端管理能力，与 WEB 端共享后端 API，是运营人员随时随地管理平台的移动入口。
