# Control Plane — Java 微服务层详细架构

> 基于整体架构文件 V9.18.0 + DEVICE 源码深入分析（分析日期 2026-08-12）
> 代码规模: 14 个 Maven 模块 / 2,655+ Java 文件 / Spring Boot 2.7 + Spring Cloud Alibaba

---

## 一、总体定位

Java 微服务层是 EasyAIot 的 **控制面 (Control Plane)**，承担平台的核心业务逻辑、设备管理、协议适配、消息推送、数据集管理、集群编排和视频监控信令等全部稳定业务功能。采用 Spring Cloud Alibaba 微服务架构，通过 Nacos 实现服务注册发现，通过 OpenFeign 实现服务间 RPC 调用。

| 指标 | 数据 |
|------|------|
| Maven 模块 | 14 个 (1 BOM + 1 Gateway + 1 Common聚合 + 11 业务模块) |
| Java 文件总数 | 2,655+ |
| 最大模块 | iot-gb28181 (569) |
| 最大业务模块 | iot-system (401: 69 API + 332 BIZ) |
| JDK 版本 | 21 |
| Spring Boot | 2.7.18 → 3.3.7 (已升级) |
| Spring Cloud | 2021.0.5 → 2023.0.6 |
| Spring Cloud Alibaba | 2021.0.4.0 → 2023.0.1.0 |
| 构建工具 | Maven + flatten-maven-plugin (${revision}) |
| ORM | MyBatis-Plus 3.5.5 + MyBatis-Plus-Join 1.4.10 |
| 数据库 | PostgreSQL 42.5.0 + TDengine 3.1.0 + Redis/Redisson |
| 消息队列 | Kafka 3.1.4 + RocketMQ 2.2.3 + Redis Stream |
| 服务注册 | Nacos 2.5.1 |

---

## 二、Maven 工程结构

### 2.1 模块全景

```
DEVICE/pom.xml                          (根聚合 POM, revision=1.0.0)
│
├── iot-parent/pom.xml                  (BOM: 统一依赖版本管理)
│
├── iot-gateway/                        (API 网关, 单模块, 15 Java 文件)
│
├── iot-common/                         (公共库聚合 POM, 17 子模块)
│   ├── iot-common-base                 (核心工具: 常量/异常/枚举/加密/JSON/IO)
│   ├── iot-common-web                  (Web 层: 全局异常/CORS/Jackson/XSS)
│   ├── iot-common-security             (认证授权: Sa-Token/JWT/权限注解/Feign传播)
│   ├── iot-common-mybatis              (MyBatis-Plus: 分页/填充/数据范围/类型处理器)
│   ├── iot-common-redis                (Redis: 缓存/分布式锁/Stream MQ)
│   ├── iot-common-rpc                  (RPC: OpenFeign 通用配置)
│   ├── iot-common-mq                   (消息队列: Redis Stream + Kafka/RocketMQ)
│   ├── iot-common-job                  (定时任务: XXL-Job 集成)
│   ├── iot-common-protection           (服务保护: 防重提交/限流/幂等)
│   ├── iot-common-tenant               (多租户: 上下文隔离/数据源切换)
│   ├── iot-common-data-permission      (数据权限: 部门/角色/自定义范围)
│   ├── iot-common-excel                (Excel: EasyExcel 导入导出)
│   ├── iot-common-env                  (环境: Profile 标识)
│   ├── iot-common-ip                   (IP 地域: ip2region 解析)
│   ├── iot-common-swagger              (文档: Knife4j + SpringDoc)
│   ├── iot-common-test                 (测试: 基类 + Mock)
│   └── iot-common-protocol             (协议: 报文编解码通用接口)
│
├── iot-system/  (api + biz)            (系统管理: 用户/角色/权限/OAuth2/多租户)
├── iot-infra/   (api + biz)            (基础设施: 代码生成/文件/配置/定时任务)
├── iot-device/  (api + biz)            (设备管理: 产品/设备/物模型/影子/OTA)
├── iot-sink/    (api + biz)            (协议适配: MQTT/TCP/Modbus/OPC UA/EMQX)
├── iot-message/ (api + biz)            (消息推送: 邮件/短信/钉钉/飞书/企微)
├── iot-dataset/ (api + biz)            (数据集: 标注/导入导出/YOLO/COCO)
├── iot-node/    (api + biz)            (集群管理: 节点/工作负载/媒体堆栈)
├── iot-file/    (api + biz)            (文件服务: MinIO/本地存储抽象)
├── iot-gb28181/ (api + biz)            (国标视频: SIP信令/wvp平台)
├── iot-tdengine/ (api + biz)           (时序数据: TDengine 超级表)
└── iot-visualize/(api + biz)           (可视化: 低代码大屏后端)
```

### 2.2 API/BIZ 分层模式

所有业务模块遵循标准 **api / biz 双层** 架构：

```
iot-xxx/
├── iot-xxx-api/               ← Feign 接口 + DTO + 常量 (对外暴露)
│   └── src/main/java/.../
│       ├── Remote*.java       ← @FeignClient 接口
│       ├── domain/            ← DTO/VO/BO/QO
│       ├── enums/             ← 枚举常量
│       └── factory/           ← FallbackFactory 降级工厂
│
└── iot-xxx-biz/               ← 业务实现 (不对外暴露)
    └── src/main/java/.../
        ├── controller/        ← REST Controller (admin/app)
        ├── service/           ← 业务接口 + impl/
        ├── dal/
        │   ├── dataobject/    ← DO (MyBatis-Plus Entity)
        │   └── pgsql/         ← Mapper (MyBatis-Plus)
        ├── convert/           ← MapStruct 转换器
        ├── framework/         ← 模块级安全/RPC/文件配置
        └── mq/                ← 消息消费者/生产者
```

### 2.3 各模块 Java 文件统计

| 模块 | API 文件 | BIZ 文件 | 总文件 | 占比 |
|------|----------|----------|--------|------|
| **iot-gb28181** | 1 | 568 | **569** | 21.4% |
| **iot-system** | 69 | 332 | **401** | 15.1% |
| **iot-device** | 182 | 127 | **309** | 11.6% |
| **iot-sink** | 36 | 198 | **234** | 8.8% |
| **iot-infra** | 12 | 180 | **192** | 7.2% |
| **iot-node** | 53 | 76 | **129** | 4.9% |
| **iot-message** | 35 | 91 | **126** | 4.7% |
| **iot-dataset** | 56 | 66 | **122** | 4.6% |
| **iot-visualize** | 3 | 48 | **51** | 1.9% |
| **iot-tdengine** | 25 | 13 | **38** | 1.4% |
| **iot-file** | 8 | 11 | **19** | 0.7% |
| **iot-gateway** | — | 15 | **15** | 0.6% |
| **iot-common** | — | ~450 | **~450** | 16.9% |
| **总计** | — | — | **~2,655** | 100% |

---

## 三、技术栈与依赖矩阵

### 3.1 核心框架

| 类别 | 技术 | 版本 |
|------|------|------|
| 基础框架 | Spring Boot | 2.7.18 |
| 微服务 | Spring Cloud | 2021.0.5 |
| 微服务 | Spring Cloud Alibaba | 2021.0.4.0 |
| 服务注册/配置 | Nacos | 2.5.1 |
| 服务保护 | Sentinel | — |
| API 网关 | Spring Cloud Gateway | — |
| 声明式 RPC | OpenFeign | 3.1.2 |
| ORM | MyBatis-Plus | 3.5.5 |
| ORM 增强 | MyBatis-Plus-Join | 1.4.10 |
| 连接池 | Druid | 1.2.21 |
| 缓存 | Redis + Redisson | 3.18.0 |
| 分布式锁 | Lock4j-Redisson | 2.2.7 |

### 3.2 数据库与中间件

| 类别 | 技术 | 版本 |
|------|------|------|
| 关系型 | PostgreSQL (主库) | 42.5.0 |
| 时序 | TDengine | JDBC 3.1.0 |
| 对象存储 | MinIO | latest |
| 共享媒体存储 | NFS（挂载根 `/mnt/easyaiot-media`） | — |
| 消息队列 | Kafka | 3.1.4 |
| 消息队列 | RocketMQ | 2.2.3 |
| 消息队列 | Redis Stream (内置MQ) | — |
| 工作流 | Flowable | 6.8.0 |
| 定时调度 | XXL-Job | 2.3.1 |
| APM | SkyWalking | 8.12.0 |
| 监控 | Spring Boot Admin | 2.7.15 |

### 3.3 工具库

| 类别 | 技术 | 版本 |
|------|------|------|
| 工具集 | Hutool | — |
| Excel | EasyExcel | — |
| 对象映射 | MapStruct | 1.5.5.Final |
| 代码简化 | Lombok | 1.18.30 |
| JSON | Fastjson2 / Jackson | — |
| 文档 | Knife4j + SpringDoc | 4.3.0 |
| 验证码 | Captcha-Plus | — |
| 反应式 | Vert.x (MQTT/TCP Server) | — |
| 工业协议 | Modbus (digitalpetri) + OPC UA (Milo) | — |
| SIP 协议 | JAIN SIP (GB28181) | — |

---

## 四、模块依赖关系图

```
                        ┌──────────────┐
                        │  iot-gateway │  (Spring Cloud Gateway, 48080)
                        └──────┬───────┘
                               │ WebClient (反应式令牌校验)
                               ▼
              ┌────────────────────────────────────┐
              │         iot-common (17 子模块)       │
              │  base/web/security/mybatis/redis/   │
              │  rpc/mq/job/protection/tenant/      │
              │  data-permission/excel/env/ip/      │
              │  swagger/test/protocol              │
              └────────────────────────────────────┘
                 ▲          ▲          ▲
                 │          │          │
        ┌────────┴──┐ ┌────┴─────┐ ┌─┴──────────┐
        │ iot-system│ │iot-infra │ │ iot-message │  ← 基础服务
        │  (48099)  │ │ (48082)  │ │  (48088)    │
        └───────────┘ └──────────┘ └─────────────┘
                 ▲          ▲
                 │          │
        ┌────────┴──────────┴──────────────────────┐
        │              iot-device (48083)            │  ← 核心设备域
        └──────┬───────────────────┬────────────────┘
               │                   │
    ┌──────────┴──────┐   ┌────────┴──────────┐
    │   iot-sink      │   │  iot-tdengine     │  ← 数据入口 + 时序存储
    │   (48086)       │   │  (48090)          │
    │ MQTT/TCP/Modbus │   │  时序数据库集成    │
    │ +NFS/MQTT 总线  │   │                   │
    └────────┬────────┘   └───────────────────┘
             │
             ▼
    ┌─────────────────┐
    │ NFS 共享媒体卷    │ ← 录像/告警媒体归档源
    │ /mnt/easyaiot-  │
    │   media         │
    └─────────────────┘
               │
    ┌──────────┴──────┐   ┌───────────────────┐
    │   iot-node      │   │  iot-gb28181      │  ← NFS 媒体纳管 + 视频信令
    │   (48085)       │   │  (48089)          │
    └─────────────────┘   └───────────────────┘
               │
    ┌──────────┴──────┐
    │   iot-file      │   ← 文件存储 (MinIO)
    │   (48084)       │
    └─────────────────┘

    独立服务 (轻量依赖):
    ┌─────────────────┐   ┌───────────────────┐
    │  iot-dataset    │   │  iot-visualize    │
    │  (48087)        │   │  (低代码大屏后端)  │
    └─────────────────┘   └───────────────────┘
```

---

## 五、API Gateway — 网关与安全体系

### 5.1 网关架构 (15 Java 文件)

`iot-gateway` 是整个平台的统一入口，基于 **Spring Cloud Gateway**，采用反应式编程模型，端口 `48080`。

```
客户端请求
  │
  ▼
┌─────────────────────────────────────────────────────────────┐
│  Spring Cloud Gateway (48080)                                │
│                                                              │
│  ┌───────────────┐  ┌──────────────────┐                    │
│  │ CorsFilter    │  │ CorsResponse     │  ← CORS 跨域处理   │
│  │ (WebFilter)   │  │ HeaderFilter     │                    │
│  └───────┬───────┘  └──────────────────┘                    │
│          ▼                                                   │
│  ┌──────────────────────────────────────────┐               │
│  │  TokenAuthenticationFilter (GlobalFilter) │              │
│  │  ├─ 提取 Authorization: Bearer <token>   │              │
│  │  ├─ WebClient 调用 OAuth2TokenApi 校验    │              │
│  │  ├─ Guava LoadingCache 缓存 LoginUser     │              │
│  │  ├─ 有效 → 写入 login-user 请求头         │              │
│  │  └─ 无效 → 直接返回 401                   │              │
│  └──────────────────┬───────────────────────┘               │
│                     ▼                                        │
│  ┌──────────────────────────────────────────┐               │
│  │  GrayReactiveLoadBalancerClientFilter     │              │
│  │  ├─ grayLb:// 前缀 → 灰度路由             │              │
│  │  ├─ GrayLoadBalancer:                     │              │
│  │  │   ├─ version 匹配 (请求头 vs 实例元数据) │              │
│  │  │   └─ tag 匹配 + Nacos 加权随机         │              │
│  │  └─ 支持蓝绿/金丝雀部署                    │              │
│  └──────────────────┬───────────────────────┘               │
│                     ▼                                        │
│              路由到下游微服务                                  │
└─────────────────────────────────────────────────────────────┘
```

### 5.2 安全架构

**双认证路径 (Gateway + Service 双层防御):**

```
┌──────────────────────────────────────────────────────────┐
│  Gateway 层 (反应式)                                       │
│  ├─ TokenAuthenticationFilter                            │
│  │   └─ 校验通过 → Base64 编码 LoginUser → login-user 头 │
│  └─ 不依赖 Spring Security Filter Chain                   │
└──────────────────────┬───────────────────────────────────┘
                       │ login-user 头传播
                       ▼
┌──────────────────────────────────────────────────────────┐
│  Service 层 (Servlet)                                     │
│  ├─ TokenAuthenticationFilter (OncePerRequestFilter)     │
│  │   ├─ 优先从 login-user 头读取 LoginUser               │
│  │   └─ 回退: Bearer Token → OAuth2TokenApi 校验         │
│  ├─ @PreAuthenticated → 已登录即可访问                    │
│  ├─ @PreAuthorize → hasPermi/hasRole 权限控制            │
│  ├─ @InnerAuth → FROM_SOURCE=INNER 服务间调用鉴权         │
│  └─ TransmittableThreadLocal → 跨 @Async 传播上下文      │
└──────────────────────────────────────────────────────────┘
```

**安全上下文传播链:**

```
Spring Security Context
  └── TransmittableThreadLocal (阿里 TTL)
        ├── FeignRequestInterceptor → login-user 头 → 下游服务
        ├── LoginUserRequestInterceptor → Feign RPC 传播
        └── @Async 异步任务自动继承
```

### 5.3 灰度发布

`GrayLoadBalancer` 实现 `ReactorServiceInstanceLoadBalancer`，支持两级匹配:
1. **Version 匹配**: 请求头 `version` → 实例元数据 `version`
2. **Tag 匹配**: 请求头 `tag` → 实例元数据 `tag`
3. **随机加权**: Nacos `getHostByRandomWeight3`

路由时使用 `grayLb://service-name` 前缀（替代标准 `lb://`）即可启用灰度路由。

### 5.4 CORS 处理

- `CorsFilter`: WebFilter 级别，为非 CORS 请求添加通配符允许头
- `CorsResponseHeaderFilter`: GlobalFilter 级别，修复 Spring Cloud Gateway 2.x 重复头问题

---

## 六、iot-common — 公共库 (17 子模块, ~450 Java 文件)

### 6.1 iot-common-base (164 文件) — 核心基础

| 包 | 职责 |
|----|------|
| `exception/` | ServiceException、ControllerException、异常码枚举 |
| `constant/` | 全局常量 (GlobalErrorCodeConstants 等) |
| `enums/` | CommonStatusEnum、DeviceStatus 等通用枚举 |
| `utils/` | BeanUtils、JsonUtils、EncryptUtils、DateUtils 等 |
| `pojo/` | CommonResult<T>、PageResult<T>、SortingField |
| `validation/` | 校验注解 (Mobile、InEnum 等) |

### 6.2 iot-common-web (59 文件) — Web 基础设施

| 组件 | 职责 |
|------|------|
| `GlobalExceptionHandler` | 统一异常处理 (16种异常类型映射) |
| `GlobalResponseBodyHandler` | ResponseBodyAdvice 统一响应包装 |
| `ApiRequestFilter` | Admin/App API 前缀过滤 |
| `CacheRequestBodyFilter` | 请求体缓存 (支持重复读取) |
| `XssFilter` + `JsoupXssCleaner` | JSoup XSS 过滤 |
| `DemoFilter` | Demo 模式禁用写操作 |
| `YudaoJacksonAutoConfiguration` | Long→String / LocalDateTime→时间戳 |
| `YudaoSwaggerAutoConfiguration` | Knife4j + SpringDoc 全局配置 |
| `WebProperties` | `iot.web.api.prefix` (/admin-api, /app-api) |

### 6.3 iot-common-security (31 文件) — 认证授权

| 组件 | 职责 |
|------|------|
| `TokenAuthenticationFilter` | 服务端 Token 校验 (OncePerRequestFilter) |
| `YudaoWebSecurityConfigurerAdapter` | Spring Security 主配置 (无状态/CSRF禁用) |
| `SecurityFrameworkService` | 权限/角色/范围检查 (Guava 缓存 + PermissionApi) |
| `PreAuthenticatedAspect` | @PreAuthenticated 切面 |
| `InnerAuthAspect` | @InnerAuth 切面 (服务间调用) |
| `TransmittableThreadLocal*` | TTL SecurityContext 跨线程传播 |
| `LoginUserRequestInterceptor` | Feign LoginUser 传播 |
| `FeignRequestInterceptor` | Feign 用户上下文传播 |
| `SecurityProperties` | `iot.security.*` 配置 (token header/mock/免登录URL) |
| `AuthorizeRequestsCustomizer` | SPI: 模块自定义安全规则 |

**安全注解体系:**

| 注解 | 级别 | 作用 |
|------|------|------|
| `@PreAuthenticated` | 方法 | 已认证即可 (不校验具体权限) |
| `@PreAuthorize(hasPermi="xxx")` | 方法 | 校验具体权限码 |
| `@PreAuthorize(hasRole="xxx")` | 方法 | 校验角色 |
| `@InnerAuth` | 方法 | 仅限服务间调用 (FROM_SOURCE=INNER) |

### 6.4 iot-common-mybatis (37 文件) — MyBatis-Plus 扩展

| 组件 | 职责 |
|------|------|
| `BaseDO` | 抽象实体基类 (createTime/updateTime/creator/updater/deleted) |
| `TenantBaseDO` | 多租户实体基类 |
| `BaseMapperX<T>` | 扩展 Mapper (分页/批量/Join) |
| `LambdaQueryWrapperX<T>` | 带 `*IfPresent` 的条件包装器 |
| `MPJLambdaWrapperX<T>` | MyBatis-Plus-Join 增强包装器 |
| `DefaultDBFieldHandler` | 审计字段自动填充 |
| `EncryptTypeHandler` | AES 加密字段类型处理器 |
| `IntegerListTypeHandler` 等 | JSON 列表序列化类型处理器 |
| `IdTypeEnvironmentPostProcessor` | 多数据库 ID 策略自动适配 |

### 6.5 iot-common-redis (6 文件) — Redis 基础设施

| 组件 | 职责 |
|------|------|
| `RedisService` | Redis 操作封装 (锁/字符串/Hash/Set/ZSet/List) |
| `TimeoutRedisCacheManager` | 支持自定义 TTL (如 `cacheName#10m`) |
| `YudaoRedisAutoConfiguration` | JSON 序列化 + JavaTimeModule |

### 6.6 iot-common-mq (15 文件) — 消息队列抽象

**双消息模型 (基于 Redis):**

| 模型 | 基类 | 用途 |
|------|------|------|
| **Pub/Sub** | `AbstractRedisChannelMessage` | 广播消息 (多消费者) |
| **Stream** | `AbstractRedisStreamMessage` | 集群消费 (消费者组 + 确认) |

| 组件 | 职责 |
|------|------|
| `RedisMQTemplate` | 消息发送核心 (Pub/Sub + Stream + 拦截器) |
| `AbstractRedisChannelMessageListener` | Pub/Sub 消费基类 |
| `AbstractRedisStreamMessageListener` | Stream 消费基类 |
| `RedisPendingMessageResendJob` | 待处理消息重新投递 (分布式锁防重) |
| `ConsumerTopicConstant` | MQTT Broker 生命周期 / 设备级主题常量 |

### 6.7 其他公共模块

| 模块 | 职责 |
|------|------|
| **iot-common-protection** (30) | 防重提交 (@RepeatSubmit)、限流、幂等、API 签名 |
| **iot-common-tenant** (22) | 多租户上下文管理、数据源切换 |
| **iot-common-data-permission** (22) | 行级数据权限 (部门/角色/自定义范围) |
| **iot-common-job** (9) | XXL-Job 集成 |
| **iot-common-excel** (15) | EasyExcel 导入导出工具 |
| **iot-common-env** (12) | Profile 环境标识 |
| **iot-common-ip** (7) | IP 地址解析 (ip2region) |
| **iot-common-swagger** (4) | Knife4j + SpringDoc 自动配置 |
| **iot-common-test** (10) | 测试基类 + Mock |
| **iot-common-rpc** (3) | OpenFeign 通用配置 (占位) |
| **iot-common-protocol** (5) | 报文编解码通用接口 |

---

## 七、核心业务模块详解

### 7.1 iot-system (401 文件, 端口 48099) — 系统管理

**最核心的基础服务**，为所有其他模块提供用户、权限、租户等基础能力。

#### Controller 端点矩阵

| Controller | 基础路径 | 关键端点 |
|------------|----------|----------|
| **AuthController** | `/system/auth` | `POST /login`, `/logout`, `/refresh-token`, `/sms-login`, `/send-sms-code`, `/get-permission-info` |
| **UserController** | `/system/user` | CRUD + `/update-password`, `/update-status`, `/export`, `/import` |
| **RoleController** | `/system/role` | CRUD + `/export-excel` |
| **MenuController** | `/system/menu` | CRUD + `/list` + `/simple-list` (树形结构) |
| **DeptController** | `/system/dept` | CRUD + `/list` + `/simple-list` (组织架构树) |
| **PostController** | `/system/post` | CRUD + `/simple-list` |
| **DictTypeController** | `/system/dict-type` | CRUD + `/export` |
| **DictDataController** | `/system/dict-data` | CRUD |
| **OAuth2ClientController** | `/system/oauth2-client` | OAuth2 客户端管理 |
| **OAuth2OpenController** | `/system/oauth2` | `/token` (authorization_code/password/client_credentials/refresh_token), `/check-token`, `/authorize` |
| **TenantController** | `/system/tenant` | CRUD + `/get-id-by-name`, `/get-by-website` |
| **TenantPackageController** | `/system/tenant-package` | 租户套餐管理 |
| **SmsTemplateController** | `/system/sms-template` | CRUD + `/send-sms` |
| **SmsChannelController** | `/system/sms-channel` | 短信渠道管理 |
| **MailTemplateController** | `/system/mail-template` | CRUD + `/send-mail` |
| **MailAccountController** | `/system/mail-account` | 邮箱账户管理 |
| **NotifyTemplateController** | `/system/notify-template` | CRUD + `/send-notify` |
| **NoticeController** | `/system/notice` | 站内通知 |
| **AreaController** | `/system/area` | 行政区划 |
| **LoginLogController** | `/system/login-log` | 登录日志查询 |
| **OperateLogController** | `/system/operate-log` | 操作日志查询 |

#### API Feign 接口 (13 个)

`DeptApi`, `PostApi`, `DictDataApi`, `LoginLogApi`, `OperateLogApi`, `MailSendApi`, `MailTemplateApi`, `NotifyMessageApi`, `NotifyTemplateApi`, `SmsSendApi`, `SmsTemplateApi`, `OAuth2TokenApi`, `TenantApi`, `UserApi`

#### 登录认证流程

```
用户 POST /system/auth/login {username, password, captcha}
  │
  ├── CaptchaController.verifyCaptcha()
  ├── AdminAuthService.authenticate(username, password)
  │     └── BCryptPasswordEncoder.matches()
  ├── AdminAuthService.login()
  │     └── OAuth2TokenApi → 生成 access_token + refresh_token
  └── 返回 { accessToken, refreshToken, expiresTime }
```

---

### 7.2 iot-device (309 文件, 端口 48083) — 设备管理

**IoT 核心引擎**，管理产品定义、设备生命周期、物模型、设备影子和 OTA 升级。

#### 领域划分

| 领域 | Controller | 核心功能 |
|------|------------|----------|
| **Product** | ProductController `/product` | 产品 CRUD、产品 JSON 导入/导出、产品授权 |
| **ThingModel** | ThingModelController `/thingModel` | TSL 物模型定义 (属性/服务/事件)、标识符查重、物模型发布 |
| **Device** | DeviceController `/device` | 设备注册/状态/上报/WebHook (EMQX)、网关子设备关联、地图位置 |
| **Shadow** | DeviceShadowController `/shadow` | 设备影子查询 (期望/上报/delta/版本) |
| **Command** | DeviceCommandController `/deviceCommand` | 命令下发 (MQTT 下行)、自定义消息 |
| **OTA** | DmPackageController `/packages` | 固件版本包 CRUD、上传 (MinIO) |
| **Protocol** | ProtocolController | 协议管理、协议脚本编译 |
| **Webhook** | WebhookTestController | Webhook 测试 |

#### 设备影子 Delta 计算

```
GET /shadow/{deviceId}
  → DeviceShadowService
    → 查询 Redis: reported state (设备上报)
    → 查询 DB: desired state (用户期望)
    → 计算 delta = desired - reported
    → 返回 { reported, desired, delta, version, metadata }
```

#### 网关子设备拓扑

```
Gateway Device
  ├── associateGateway(deviceId, subDeviceId)     ← 关联子设备
  ├── disassociateGateway(deviceId, subDeviceId)  ← 解绑子设备
  ├── detachGatewaySubDevices(deviceId)           ← 批量解绑 (离线)
  └── ensureDeviceOnUplink(...)                   ← 上行数据自动建表
```

#### API Feign 接口 (13 个)

`RemoteDeviceService`, `RemoteDeviceInfoService`, `RemoteDeviceActionService`, `RemoteDeviceDatasService`, `RemoteDeviceServiceService`, `RemoteProductService`, `RemoteProductPropertiesService`, `RemoteProductCommandsService`, `RemoteProductCommandsRequestsService`, `RemoteProductServicesService`, `RemoteProtocolService`, `RemoteAppService`, `RemoteCacheOpenAnyService`

---

### 7.3 iot-sink (234 文件, 端口 48086) — 协议适配层

**全协议接入中心**，支持 5 种物联网协议，是设备数据进入平台的第一站。同时承担录像回调处理与告警媒体归档（NFS 共享媒体存储）以及 MQTT 算法总线消费。

#### 协议适配器矩阵

| 协议 | 核心类 | 机制 | 传输层 |
|------|--------|------|--------|
| **MQTT** (原生) | `IotMqttUpstreamProtocol` | Vert.x MqttServer (内嵌 Broker) | SSL/TLS 端口 |
| **MQTT** (外挂) | `IotEmqxUpstreamProtocol` | MQTT Client 连接到外部 EMQX 集群 | 遗嘱消息/自动重连 |
| **TCP** | `IotTcpUpstreamProtocol` | Vert.x NetServer + ConnectionManager | 自定义编解码 |
| **Modbus TCP** | `IotModbusPollingProtocol` | Modbus Master TCP 轮询 | digitalpetri 库 |
| **Modbus RTU** | `IotModbusRtuPollingProtocol` | Modbus Master RTU 轮询 | 串口锁管理 |
| **OPC UA** | `IotOpcUaPollingProtocol` | OPC UA Client 轮询 | Eclipse Milo |
| **HTTP** | `router/` | HTTP 路由器 | REST |

#### 消息总线架构 (双实现策略)

```java
// 接口抽象
interface IotMessageBus {
    void publish(topic, message);
    void subscribe(topic, group, handler);
}

// 实现 1: Kafka (生产)
class IotKafkaMessageBus implements IotMessageBus { ... }

// 实现 2: 本地内存 (开发/测试)
class IotLocalMessageBus implements IotMessageBus { ... }
```

#### 事件驱动架构 (30+ Handler)

```
设备上报 → IotMqttUpstreamProtocol / IotTcpUpstreamProtocol
  │
  ├── topic: /devices/{productId}/{deviceId}/property/post
  │     └── PropertyEventHandler → IotDeviceService.handleProperty()
  │           ├── TDengine 时序存储
  │           └── Redis 实时影子更新
  │
  ├── topic: /devices/{productId}/{deviceId}/event/post
  │     └── EventEventHandler → iot-message 告警通知
  │
  └── topic: /devices/{productId}/{deviceId}/service/call
        └── ServiceCallEventHandler → 下行控制响应

告警经 MQTT 总线上行（RUNTIME AlgoMqttBus → `mqtt/iot-alert-notification`）→ IotAlgoBusMqttHandler 订阅消费（详见 7.3.x 媒体归档与算法总线）

云端指令 → IotMqttDownstreamSubscriber
  ├── topic: /devices/{productId}/{deviceId}/property/set → 属性设置
  ├── topic: /devices/{productId}/{deviceId}/service/invoke → 服务调用
  ├── ConfigDownstreamPushHandler → 配置推送
  ├── OtaDownstreamPushHandler → OTA 升级下发
  └── BroadcastDownstreamHandler → 广播指令
```

#### 7.3.x 媒体归档与算法总线（2026-08-11 重构新增）

除协议适配外，iot-sink 还承担两类媒体/告警流水线职责：

**(1) 录像回调与归档（NFS 共享媒体存储 → MinIO）**

| 组件 | 职责 |
|------|------|
| **MediaHookController** | 暴露 `/media/hook`，接收 SRS `on_dvr` 与 ZLM `on_record_mp4` 录像回调 Hook，委托 `DvrUploadService` 处理。 |
| **DvrUploadService / Impl** | DVR Hook 处理主流程：`NfsMediaPathResolver` 解析 NFS 路径 → 等待文件写完 → 查/建 `record_space`（`record-<deviceId>`）→ MinIO 上传 → 写 `playback` 表 + 回填 `alert.record_path` → `removeLocalAfterUpload` 时删 NFS 本地 flv。 |
| **NfsMediaPathResolver** | 容器内路径 `/data/...` ↔ NFS 挂载根 `/mnt/easyaiot-media/...` 互转；`nfsOnly=true` 时强制路径落在 `mountRoot` 下，越界抛异常。 |
| **NfsMediaProperties** | 配置前缀 `basiclab.media.*`：`mountRoot=/mnt/easyaiot-media`、`containerDataRoot=/data`、`nfsOnly`、`removeLocalAfterUpload`。 |

**(2) MQTT 算法总线消费（告警上行）**

| 组件 | 职责 |
|------|------|
| **IotAlgoBusMqttHandler** | 订阅 EMQX 算法总线 `mqtt/iot-*`（用 `$share/algo-sink/` 共享订阅组），处理告警：`normalizePayload` → 补必填字段 → 从节点表补边缘维度 → **从 VIDEO 库 `algorithm_task` 补齐通知配置**（`channels`/`notifyUsers`）→ `AlertService` 落库 → 命中时转发 Kafka topic `iot-alert-notification-send` 做下游通知；后处理消息入队 `PostProcessService`。 |

**MQTT 契约**：envelope `{version,msgId,msgType,tenant,ts,payload}`；三对 topic/msgType —— `mqtt/iot-alert-notification`/`alert.notification`、`mqtt/iot-snapshot-alert`/`alert.snapshot`、`mqtt/iot-post-process-request`/`post_process.request`。RUNTIME 侧总开关 `ALGO_BUS_TRANSPORT`（默认空=MQTT；`http/off/0/false/no`=关闭回退 HTTP）。心跳仍走 HTTP→VIDEO。

#### 设备认证 (类阿里云物联网平台)

```
POST /iot/auth/register/device
  ├── 产品密钥/应用ID/应用密钥 验证
  ├── HMACMD5/HMACSHA1/HMACSHA256 签名验证
  └── 返回: deviceId + deviceSecret
```

---

### 7.4 iot-message (126 文件, 端口 48088) — 消息推送

**多渠道消息推送中心**，支持 7 种消息渠道，采用策略+工厂模式。

#### 消息渠道矩阵

| 渠道 | Maker | Sender | 传输方式 |
|------|-------|--------|----------|
| **短信 (阿里云)** | `AliyunMsgMaker` | `AliYunMsgSender` | 阿里云 SMS API |
| **短信 (腾讯云)** | `TxYunMsgMaker` | `TxYunMsgSender` | 腾讯云 SMS API |
| **邮件** | `MailMsgMaker` | `MailMsgSender` | SMTP (Hutool MailUtil) |
| **钉钉** | `DingMsgMaker` | `DingMsgSender` | 工作通知 + 群机器人, Token 缓存 |
| **飞书** | `FeishuMsgMaker` | `FeishuMsgSender` | Webhook (文本/富文本/交互卡片) |
| **企业微信** | `WxCpMsgMaker` | `WxCpMsgSender` | WxJava (工作通知 + 群机器人) |
| **HTTP 回调** | `HttpMsgMaker` | `HttpMsgSender` | 通用 HTTP POST |

#### 消息发送流程

```
Controller → MessageSendService
  → PushControl (异步线程池)
    → MsgMakerFactory.getMsgMaker(msgType)
      → IMsgMaker.makeMessage()     ← 构建消息内容
      → IMsgSender.send()           ← 发送到渠道
      → PushHistoryService.save()   ← 记录推送历史
    → SendResult
```

#### Controller

| Controller | 职责 |
|------------|------|
| `MessageConfigController` | 渠道配置管理 |
| `MessageTemplateController` | 消息模板管理 |
| `MessageSendController` | 手动消息发送 |
| `MessagePrepareController` | 消息预览/准备 |
| `PreviewUserController` | 预览用户管理 |
| `TPreviewUserGroupController` | 用户组管理 |
| `PushHistoryController` | 推送历史查询 |

---

### 7.5 iot-infra (192 文件, 端口 48082) — 基础设施

**运维和开发支撑服务**，提供代码生成、文件管理、定时任务和系统监控。

#### 核心组件

| 组件 | 基础路径 | 核心功能 |
|------|----------|----------|
| **CodegenController** | `/infra/codegen` | 代码生成器: 数据库表→Java/Vue/SQL 代码, Velocity 模板引擎 |
| **FileController** | `/infra/file` | 文件上传/下载/预签名 URL (S3 兼容) |
| **FileConfigController** | — | 存储后端配置 (DB/FTP/Local/S3/SFTP) |
| **ConfigController** | `/infra/config` | 应用参数配置管理 |
| **JobController** | `/infra/job` | Quartz 定时任务 (CRUD + trigger + sync) |
| **JobLogController** | — | 任务执行历史 |
| **RedisController** | `/infra/redis` | Redis 监控 (info + dbSize + commandstats) |
| **DataSourceConfigController** | — | 多数据源管理 |

#### 文件客户端架构 (策略模式)

```
FileClientFactory
  ├── DBFileClient       ← 数据库 BLOB 存储
  ├── LocalFileClient    ← 本地磁盘存储
  ├── FtpFileClient      ← FTP 协议
  ├── SftpFileClient     ← SFTP 协议
  └── S3FileClient       ← MinIO / AWS S3 (预签名 URL)
```

---

## 八、专业模块详解

### 8.1 iot-dataset (122 文件, 端口 48087) — 数据集管理

**AI 训练数据管理平台**，支持多种标注格式的导入导出和数据管理。

| 功能域 | 核心能力 |
|--------|----------|
| **数据集管理** | CRUD + 版本管理 |
| **标注工具集成** | LabelMe (JSON), YOLO (txt), COCO (JSON) 格式 |
| **图片管理** | 批量上传 (MinIO)、图片预览、标注渲染 |
| **帧提取** | 从视频流提取帧形成数据集 |
| **格式转换** | YOLO ↔ COCO ↔ VOC 格式互转 |
| **SAM 集成** | 调用 AI 服务 SAM 自动标注 |
| **导入导出** | EasyExcel 批量导入导出 |

---

### 8.2 iot-node (129 文件, 端口 48085) — 集群节点管理

**集群节点编排中心**，负责节点的全生命周期管理、远程部署与共享媒体存储纳管。

| 功能域 | 核心能力 |
|--------|----------|
| **节点注册** | 节点心跳/状态监控/GPU 指标 |
| **工作负载调度** | Docker Compose / Systemd 服务部署 |
| **媒体堆栈部署** | SRS + ZLMediaKit 远程安装 |
| **MQTT 堆栈部署** | EMQX 远程安装配置 |
| **SSH 远程执行** | JSch 远程命令/文件传输 |
| **WebSocket** | 实时部署进度推送 |
| **集群监控** | GPU/CPU/内存/磁盘/网络指标 |
| **媒体存储** | NFS 共享卷纳管 + Ceph 拓扑/集群分配（原 Ceph OSD/Client 部署脚本已替换为 `install_nfs_server`/`install_nfs_client`/`mount-all`/`check_nfs_health`） |

#### NodeStorageService — NFS 共享媒体存储纳管

`NodeStorageService` 接口（命名保留 `Ceph` 前缀以兼容前端，语义已改为 NFS 共享媒体节点拓扑）：

| 方法 / DTO | 职责 |
|------------|------|
| `getCephTopology()` | 返回 NFS 共享媒体节点拓扑（`NodeCephTopologyRespVO`），命名保留语义改 NFS。 |
| `assignNfsCluster(NodeNfsClusterAssignReqVO)` | 分配/切换 NFS 集群：指定服务端与客户端节点，写节点 tags。 |
| `NodeNfsClusterAssignReqVO` | 入参：`serverNodeId` / `clientNodeIds` / `mountRoot=/mnt/easyaiot-media/nfsExport` / `nfsMountOpts=vers=3,tcp,nolock,_netdev`。 |
| `NodeCephTopologyRespVO` | 出参：`center` / `nodes` / `links` / `summary`；`node.kind=platform|storage_nfs|nfs_client`；含 `@Deprecated` 旧 Ceph 字段以兼容前端。 |

**SYNC_RELATIVE_FILES** 中同步到边缘节点的脚本清单已由 Ceph 系脚本（`install_ceph_*`/`ceph_deploy_*`）替换为 NFS 系脚本（`install_nfs_server`、`install_nfs_client`、`mount-all`、`check_nfs_health`）。

---

### 8.3 iot-file (19 文件, 端口 48084) — 文件服务

**轻量级文件存储抽象层**，为所有模块提供统一文件访问。

| 功能 | 实现 |
|------|------|
| **上传** | 分片上传 (MinIO) |
| **下载** | 流式下载 + 预签名 URL |
| **存储后端** | MinIO (主) + 本地文件系统 (备用) |
| **API** | 8 个 Feign 接口 |

---

### 8.4 iot-gb28181 (569 文件, 端口 48089) — 国标视频平台

**最大的单体模块**，基于 wvp-GB28181-Pro 框架的完整 GB/T 28181 视频监控平台。

#### 技术特点

- **SIP 信令栈**: JAIN SIP (jain-sip-ri)，处理设备注册/心跳/目录查询/实时点播/PTZ 控制
- **流媒体会话**: SDP 协商 + RTP/RTCP 媒体流管理
- **WebSocket**: 实时推送设备状态/告警事件
- **日志**: Log4j2 (排除 Logback 以避免冲突)
- **地图**: JTS 几何计算 + Protobuf 矢量瓦片
- **认证**: HTTP Digest 认证

#### 功能域

| 功能域 | 说明 |
|--------|------|
| **设备注册** | SIP REGISTER 处理，设备认证 |
| **设备目录** | 组织/区域/设备/通道树形结构 |
| **实时点播** | INVITE → SDP 协商 → RTP 流 |
| **PTZ 控制** | 云台控制 (上下左右/变焦/预置位) |
| **录像回放** | 设备端 + 云端录像查询/回放 |
| **语音对讲** | 双向语音广播 |
| **告警** | 移动侦测/遮挡/视频丢失告警 |
| **平台级联** | 下级/上级平台级联 |
| **地图渲染** | 矢量瓦片地图 + 设备位置标注 |
| **拉流代理** | 主动拉取非国标摄像头流 |

---

### 8.5 iot-tdengine (38 文件, 端口 48090) — 时序数据

**TDengine 时序数据库集成**，处理海量设备遥测数据的存储和查询。

| 功能域 | 核心能力 |
|--------|----------|
| **数据库管理** | CREATE/DROP DATABASE + 保留策略 |
| **超级表管理** | 按产品自动创建超级表 (STable) |
| **子表管理** | 按设备自动创建子表 (使用设备标识) |
| **数据写入** | DML INSERT 批量写入 |
| **数据查询** | 时间范围查询 / 标签过滤 / 降采样 |
| **代码生成** | 超级表→Java 实体代码生成 |

---

### 8.6 iot-visualize (51 文件) — 低代码大屏

**可视化大屏后端**，为大屏设计器提供项目/画布/模板/素材的 CRUD 持久化。

---

## 九、服务间通信架构

### 9.1 三种通信模式

```
┌────────────────────────────────────────────────────────┐
│  1. OpenFeign 声明式 RPC (同步)                         │
│  ┌──────────┐  @FeignClient   ┌──────────┐            │
│  │  Caller  │ ───────────────→│  Callee  │            │
│  │  -biz    │    -api 接口     │  -biz    │            │
│  └──────────┘                 └──────────┘            │
│  + FallbackFactory 降级 + RpcApiBeanUtils 本地优化    │
├────────────────────────────────────────────────────────┤
│  2. 消息队列 (异步, 基于 Redis)                         │
│  ┌──────────┐  Redis Pub/Sub ┌──────────┐            │
│  │ Publisher│ ───────────────→│ Consumer │ (广播)     │
│  └──────────┘                 └──────────┘            │
│  ┌──────────┐  Redis Stream  ┌──────────┐            │
│  │ Publisher│ ───────────────→│ Consumer │ (集群消费)  │
│  └──────────┘                 └──────────┘            │
│  + Kafka / RocketMQ (可选, 生产环境)                   │
├────────────────────────────────────────────────────────┤
│  3. WebClient 反应式调用 (网关)                         │
│  Gateway ──WebClient + LoadBalancer──→ OAuth2TokenApi  │
└────────────────────────────────────────────────────────┘
```

### 9.2 Feign 调用优化

```java
// RpcApiBeanUtils: 本地优先策略
if (localBeanExists("xxxApiImpl")) {
    return localBean;  // 避免 Feign 回环调用
} else {
    return feignProxy;  // 远程调用
}
```

示例: `iot-infra` 调用 `iot-system` 的 `UserApi` 时：
1. 如果 `iot-infra-biz` 中存在 `UserApiImpl` → 直接本地调用
2. 否则 → 通过 Feign 远程调用 `iot-system-biz`

---

## 十、数据层架构

### 10.1 多数据库架构

```
┌─────────────────────────────────────────────────────┐
│  PostgreSQL 18 (关系型主库 - 6 个业务库)              │
│  ├── ruoyi-vue-pro  (系统管理: 用户/角色/权限/租户)   │
│  ├── iot-device     (设备/产品/物模型/OTA)            │
│  ├── iot-ai         (模型/训练/部署)                  │
│  ├── iot-video      (摄像头/算法任务/告警)            │
│  ├── iot-gb28181    (国标设备/通道/录像)              │
│  └── iot-message    (消息模板/推送历史)               │
├─────────────────────────────────────────────────────┤
│  TDengine 3.x (时序数据库)                            │
│  └── 设备遥测数据 (超级表模型: 1 产品 = 1 超级表)     │
├─────────────────────────────────────────────────────┤
│  Redis (缓存 + 消息 + 锁)                             │
│  ├── 设备影子实时状态                                  │
│  ├── Session/Token 缓存                               │
│  ├── 分布式锁 (SETNX + Lua / Redisson)                │
│  ├── Stream MQ (内置消息队列)                         │
│  └── 字典数据缓存 (60s TTL)                           │
└─────────────────────────────────────────────────────┘
```

### 10.2 MyBatis-Plus 增强

```
BaseMapperX<T> ──extends──→ MPJBaseMapper<T> (MyBatis-Plus-Join)
  │
  ├── selectPage(Page, LambdaQueryWrapperX)
  ├── selectList(LambdaQueryWrapperX)
  ├── insertBatch(List<T>)           ← 批量插入 (兼容 SQL Server)
  ├── updateBatch(List<T>)           ← 批量更新
  └── selectJoin*(...)               ← 多表 JOIN 查询

LambdaQueryWrapperX<T>
  ├── eqIfPresent(field, value)      ← value != null 才加条件
  ├── likeIfPresent(field, value)    ← 可选模糊匹配
  └── limitN(n)                      ← 数据库自适应 LIMIT
```

---

## 十一、核心设计模式

| 模式 | 应用场景 | 位置 |
|------|----------|------|
| **API/BIZ 分层** | 所有业务模块的服务接口与实现分离 | 全模块 |
| **策略模式** | 文件客户端 (DB/FTP/S3/SFTP)、消息渠道 (短信/邮件/钉钉/飞书) | iot-infra, iot-message |
| **工厂模式** | `MsgMakerFactory` (消息构建器), `FileClientFactory` (文件客户端) | iot-message, iot-infra |
| **模板方法** | `AbstractRedisMessageListener` (消息消费), `AbstractTopicHandler` (主题处理) | iot-common-mq, iot-sink |
| **观察者/事件** | 设备事件发布 → 30+ TopicHandler 订阅 | iot-sink |
| **责任链** | 路由守卫链 (9 个 Guard), 消息拦截器链 | iot-gateway, iot-common-mq |
| **适配器模式** | 5 种物联网协议适配为统一消息总线 | iot-sink |
| **代理模式** | `@FeignClient` + `FallbackFactory` | 全模块 API |
| **装饰器模式** | `CacheRequestBodyWrapper` 请求体缓存 | iot-common-web |
| **拦截器模式** | Security Filter Chain, Feign Request Interceptor | iot-common-security |

---

## 十二、服务端口与依赖速查

| 模块 | 端口 | 层级 | 上游依赖 | 关键组件 |
|------|------|------|----------|----------|
| **iot-gateway** | 48080 | 网关 | iot-system-api | Spring Cloud Gateway, Token Filter |
| **iot-system** | 48099 | 基础 | common-*, captcha-plus, aliyun-sdk | Auth, User, Role, Menu, OAuth2 |
| **iot-infra** | 48082 | 基础 | iot-system-api, minio | Codegen, File, Job, Redis Monitor |
| **iot-device** | 48083 | 核心 | sink-api, message-api, tdengine-api, onnxruntime | Product, Device, Shadow, OTA |
| **iot-sink** | 48086 | 核心 | device-api, tdengine-api, vert.x, modbus, milo | MQTT/TCP/Modbus/OPC UA 协议 + 录像回调/告警 MQTT 总线 |
| **iot-message** | 48088 | 基础 | system-api, weixin-java, dingtalk-sdk | 7 渠道消息推送 |
| **iot-dataset** | 48087 | 业务 | file-api, minio | 数据集标注/导入导出 |
| **iot-node** | 48085 | 业务 | jsch, websocket | 节点/工作负载/媒体堆栈 + NFS 媒体纳管 |
| **iot-file** | 48084 | 基础 | minio | 文件存储抽象 |
| **iot-gb28181** | 48089 | 业务 | jain-sip-ri, websocket, protobuf | SIP 信令, 国标全功能 |
| **iot-tdengine** | 48090 | 基础 | taos-jdbcdriver | 时序数据库 |
| **iot-visualize** | — | 业务 | hutool | 低代码大屏后端 |

---

## 十三、升级到 Spring Boot 3.3.x 的变更

Spring Boot 版本已从 2.7.18 升级到 **3.3.7**，相应版本链升级:

| 组件 | 原版本 | 新版本 |
|------|--------|--------|
| spring-boot | 2.7.18 | **3.3.7** |
| spring-cloud | 2021.0.5 | **2023.0.6** |
| spring-cloud-alibaba | 2021.0.4.0 | **2023.0.1.0** |

**关键迁移影响:**
- `javax.*` → `jakarta.*` 包重命名 (Servlet/JPA/Validation)
- Spring Security 6.x API 变更
- OpenFeign 4.x 包名变更
- MyBatis-Plus 需要升级到 3.5.5+ 以确保兼容性
- SpringDoc OpenAPI 升级到 2.x 系列

---

## 十四、修订记录

| 版本 | 日期 | 变更摘要 | 触发来源 |
|------|------|----------|----------|
| V9.18.0 | 2026-08-12 | iot-sink 新增录像回调与告警媒体归档（NFS 共享媒体存储 + MediaHookController/DvrUploadService/NfsMediaPathResolver）与 MQTT 算法总线消费（IotAlgoBusMqttHandler）；iot-node 存储语义由 Ceph 改为 NFS 纳管（NodeStorageService.assignNfsCluster/getCephTopology，命名保留语义改 NFS）；删除 EDGE 措辞（模块依赖图、8.2 节正文） | commits f13c491d/3b7c7f5c/242f8f31/ac3b08a6/28a2c318/42945f3f/7c47d3b9/847b3c85 |

---

> **一句话总结:** Java 微服务层是 EasyAIot 的控制面核心，14 个 Maven 模块 (2,655+ Java 文件) 采用 Spring Cloud Alibaba 微服务架构，通过 API/BIZ 双层分离 + 17 个公共子模块 + 5 种协议适配 (MQTT/TCP/Modbus/OPC UA/EMQX) + 7 渠道消息推送 + OpenFeign 服务间 RPC + Nacos 服务治理，实现了从用户权限到设备管理、从数据采集到时序存储、从 AI 标注到集群编排、从 GB28181 国标信令到可视化大屏的完整 IoT 平台后端能力。
