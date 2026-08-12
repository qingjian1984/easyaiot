# API Gateway + Monitoring Layer 详细架构

> 基于整体架构文件 V9.18.0 + DEVICE 源码深入分析
> 核心组件: iot-gateway (Spring Cloud Gateway) + SkyWalking APM + Spring Boot Admin

---

## 一、总体定位

API 网关层是 EasyAIot 微服务集群的 **统一流量入口** 和 **可观测性中枢**，承担所有外部请求的路由、认证、灰度发布和 CORS 处理，同时通过 SkyWalking 和 Spring Boot Admin 实现全链路监控。

| 指标 | 数据 |
|------|------|
| 网关框架 | Spring Cloud Gateway (反应式) |
| 网关端口 | 48080 |
| Java 文件 | 14 |
| 服务注册 | Nacos 2.5.1 |
| APM | SkyWalking 8.12.0 |
| 监控 | Spring Boot Admin 2.7.15 |
| 认证模式 | OAuth2 Bearer Token (双 Token) |

---

## 二、架构全景

```
                    外部请求 (浏览器 / App / 第三方)
                           │
                           ▼
┌──────────────────────────────────────────────────────────────┐
│                    Nginx (WEB/APP 容器)                       │
│              反向代理 / CORS / WebSocket / Gzip              │
│              /dev-api → gateway:48080/admin-api              │
└──────────────────────────┬───────────────────────────────────┘
                           │
                           ▼
┌──────────────────────────────────────────────────────────────┐
│                iot-gateway (Spring Cloud Gateway :48080)      │
│                                                              │
│  ┌─────────────────────────────────────────────────────────┐ │
│  │  1. CorsFilter (WebFilter)                              │ │
│  │     ├── 非 CORS 请求 → Access-Control-Allow-Origin: *   │ │
│  │     └── OPTIONS 预检 → 200 OK                           │ │
│  ├─────────────────────────────────────────────────────────┤ │
│  │  2. CorsResponseHeaderFilter (GlobalFilter, Ordered)    │ │
│  │     └── 修复 Gateway 2.x 重复 CORS 头问题               │ │
│  ├─────────────────────────────────────────────────────────┤ │
│  │  3. DemoBlockFilter (GlobalFilter)                      │ │
│  │     └── Demo 模式下拦截写操作 (POST/PUT/DELETE)         │ │
│  ├─────────────────────────────────────────────────────────┤ │
│  │  4. TokenAuthenticationFilter (GlobalFilter, -100)      │ │
│  │     ├── 提取 Authorization: Bearer <token>              │ │
│  │     ├── WebClient → OAuth2TokenApi.checkAccessToken()   │ │
│  │     ├── Guava LoadingCache 缓存 LoginUser (TTL: 1min)   │ │
│  │     ├── 有效 → Base64(LoginUser) → login-user 请求头    │ │
│  │     └── 无效 → 直接返回 401 (不进入后端)                │ │
│  ├─────────────────────────────────────────────────────────┤ │
│  │  5. GrayReactiveLoadBalancerClientFilter (GlobalFilter) │ │
│  │     ├── 拦截 grayLb:// 前缀                            │ │
│  │     ├── Version 匹配 (请求头 vs Nacos 元数据)           │ │
│  │     ├── Tag 匹配 (请求头 tag → 实例 tag)               │ │
│  │     └── Nacos 加权随机选择                              │ │
│  └─────────────────────────────────────────────────────────┘ │
│                           │                                  │
│              路由到下游微服务 (lb:// 或 grayLb://)            │
└──────────────────────────┬───────────────────────────────────┘
                           │
          ┌────────────────┼────────────────┐
          ▼                ▼                 ▼
    iot-system       iot-device       iot-message
    (48099)          (48083)          (48088)
```

---

## 三、过滤器链详解

### 3.1 过滤器执行顺序

| 优先级 | 过滤器 | 类型 | 职责 |
|--------|--------|------|------|
| -100 | `TokenAuthenticationFilter` | GlobalFilter | Token 校验 (最先执行) |
| `LOAD_BALANCER_CLIENT_FILTER_ORDER` | `GrayReactiveLoadBalancerClientFilter` | GlobalFilter | 灰度路由 |
| `NettyWriteResponseFilter + 1` | `CorsResponseHeaderFilter` | GlobalFilter | CORS 头修复 (最后执行) |

另外 `CorsFilter` 作为 `WebFilter` 在所有 GlobalFilter 之前执行。

### 3.2 CorsFilter — 跨域处理

```java
// WebFilter 级别: 为非 CORS 请求添加通配符 CORS 头
if (非 CORS 预检) {
    response.headers.add("Access-Control-Allow-Origin", "*");
}
if (OPTIONS 预检) {
    return 200 OK;  // 直接响应, 不进入后续过滤链
}
```

### 3.3 TokenAuthenticationFilter — 认证

**核心认证流程:**

```
请求进入
  │
  ├── 1. 移除请求头中的 login-user (防止客户端模拟)
  │
  ├── 2. 提取 Authorization: Bearer <token>
  │     ├── 有 Token → 继续
  │     └── 无 Token → 放行 (由下游服务决定是否拒绝)
  │
  ├── 3. Guava LoadingCache 查找
  │     ├── Key: KeyValue<tenantId, token>
  │     ├── TTL: 1 分钟
  │     ├── 命中 → 直接使用 LoginUser
  │     └── 未命中 → 调用 OAuth2TokenApi
  │
  ├── 4. WebClient (反应式) 调用 OAuth2TokenApi.checkAccessToken()
  │     ├── 有效 → 缓存 LoginUser → 写入 login-user 头
  │     └── 无效 → 返回 401 JSON 响应
  │
  └── 5. LoginUser 信息写入请求头
        ├── login-user-id
        ├── login-user-type
        ├── login-user (Base64 JSON)
        └── tenant-id
```

**为什么用 WebClient 而非 Feign?**

网关基于 WebFlux (反应式)，Feign 是 Servlet 阻塞模型，两者不兼容。使用 `WebClient` + `ReactorLoadBalancerExchangeFilterFunction` 实现反应式服务发现调用。

### 3.4 GrayReactiveLoadBalancerClientFilter — 灰度发布

**两级匹配策略:**

```
路由 URI: grayLb://iot-system
  │
  ├── 1. Version 匹配
  │     ├── 请求头 version → Nacos 实例元数据 version
  │     └── 精确匹配
  │
  ├── 2. Tag 匹配
  │     ├── 请求头 tag → Nacos 实例元数据 tag
  │     └── 通过 EnvUtils 解析
  │
  └── 3. 随机加权 (兜底)
        └── NacosBalancer.getHostByRandomWeight3()
```

**使用方式:**

```yaml
# 应用配置中指定路由前缀
spring:
  cloud:
    gateway:
      routes:
        - id: iot-system-gray
          uri: grayLb://iot-system  # 启用灰度路由
```

**EnvUtils 特殊处理:**

`${HOSTNAME}` 占位符会自动替换为实际主机名，用于 Kubernetes 等容器环境中的实例标识。

---

## 四、安全架构

### 4.1 双层防御模型

```
┌─────────────────────────────────────────────────────────┐
│  第一层: Gateway 认证 (反应式)                            │
│  ├── Token 提前校验 → 无效请求在网关层被拦截              │
│  ├── LoginUser 透传 → 下游服务直接使用                    │
│  └── 减少无效请求到达业务层                                │
└──────────────────────┬──────────────────────────────────┘
                       │ login-user 请求头
                       ▼
┌─────────────────────────────────────────────────────────┐
│  第二层: Service 认证 (Servlet)                           │
│  ├── TokenAuthenticationFilter (OncePerRequestFilter)   │
│  ├── 优先读取 login-user 头 (网关注入)                   │
│  ├── 回退: 直接 Bearer Token 校验                       │
│  └── 即使绕过网关, 服务仍能自我保护                       │
└─────────────────────────────────────────────────────────┘
```

### 4.2 安全上下文传播

```
Gateway (反应式)
  │ Token 校验 → LoginUser → Base64(JSON) → login-user 头
  │
  ▼
Service (Servlet)
  │ login-user 头 → LoginUser 对象 → SecurityContextHolder
  │
  ├── FeignRequestInterceptor
  │     └── 传播: user-id, tenant-id, username, authorization
  │
  ├── LoginUserRequestInterceptor
  │     └── 传播: LoginUser (编码)
  │
  └── TransmittableThreadLocal
        └── 传播: @Async 异步任务自动继承 SecurityContext
```

### 4.3 认证注解体系

| 注解 | 作用域 | 校验内容 | 使用场景 |
|------|--------|----------|----------|
| `@PermitAll` | URL 模式 | 无需认证 | 登录/注册/验证码/静态资源 |
| `@PreAuthenticated` | 方法 | 已认证即可 | 通用业务接口 |
| `@PreAuthorize(hasPermi=)` | 方法 | 具体权限码 | 细粒度权限控制 |
| `@PreAuthorize(hasRole=)` | 方法 | 角色 | 角色级别限制 |
| `@InnerAuth` | 方法 | FROM_SOURCE=INNER | 服务间内部调用 |
| `@PreAuthorize(hasAnyPermi=)` | 方法 | 任一权限 | 多权限接口 |

---

## 五、服务发现与负载均衡

### 5.1 Nacos 服务注册

```
各微服务启动
  │
  ├── 向 Nacos 注册 (服务名/主机/端口)
  ├── 上报元数据: version, tag, 主机名
  └── 心跳维持: 5 秒间隔
        │
        ▼
  Nacos Server (:8848)
  ├── 命名空间: local (默认)
  ├── 分组: DEFAULT_GROUP
  └── 健康检查: 主动探测 + 被动心跳
        │
        ▼
  Gateway 订阅服务列表
  ├── 服务变化实时推送
  └── 本地缓存 + 故障转移
```

### 5.2 负载均衡策略

| 策略 | 说明 |
|------|------|
| **默认 (标准 lb://)** | Spring Cloud LoadBalancer 轮询 |
| **灰度 (grayLb://)** | Version → Tag → 加权随机 |
| **Nacos 加权** | NacosBalancer.getHostByRandomWeight3() |

---

## 六、SkyWalking APM — 链路追踪

### 6.1 架构

```
┌──────────────────────────────────────────────────────┐
│  SkyWalking 8.12.0                                   │
│                                                      │
│  ┌──────────────┐  ┌──────────────┐                  │
│  │  OAP Server  │  │  Web UI      │                  │
│  │  (分析引擎)   │  │  (可视化面板) │  端口: 8080      │
│  └──────┬───────┘  └──────────────┘                  │
│         │                                            │
│         ▼                                            │
│  ┌──────────────────────────────────────────────┐   │
│  │  Java Agent (javaagent 挂载)                   │   │
│  │  ├── 自动探针: Spring MVC/Cloud/Gateway       │   │
│  │  ├── 数据库探针: MyBatis/JDBC/Redis           │   │
│  │  ├── MQ 探针: Kafka/RocketMQ                  │   │
│  │  └── 跨服务追踪: TraceId 自动传播 (Feign)     │   │
│  └──────────────────────────────────────────────┘   │
└──────────────────────────────────────────────────────┘
```

### 6.2 追踪能力

| 维度 | 能力 |
|------|------|
| **服务拓扑** | 自动生成微服务调用拓扑图 |
| **调用链** | 单个请求的完整调用链 (Gateway → Service → DB/Redis/MQ) |
| **性能分析** | 慢端点识别、响应时间百分位 (P50/P90/P99) |
| **错误追踪** | 异常堆栈关联、错误率统计 |
| **依赖分析** | 服务间调用频率/延迟矩阵 |

### 6.3 集成方式

```bash
# JVM 启动参数
-javaagent:/path/to/skywalking-agent.jar
-Dskywalking.agent.service_name=iot-gateway
-Dskywalking.collector.backend_service=skywalking-oap:11800
```

**iot-parent POM 依赖:**

```xml
<skywalking.version>8.12.0</skywalking.version>
<dependency>
    <groupId>org.apache.skywalking</groupId>
    <artifactId>apm-toolkit-trace</artifactId>
</dependency>
<dependency>
    <groupId>org.apache.skywalking</groupId>
    <artifactId>apm-toolkit-opentracing</artifactId>
</dependency>
```

---

## 七、Spring Boot Admin — 服务监控

### 7.1 架构

```
┌──────────────────────────────────────────────────────┐
│  Spring Boot Admin Server (集成在 iot-infra 中)       │
│  端口: 通过 iot-infra 访问                             │
│                                                      │
│  ┌──────────────────────────────────────────┐       │
│  │  监控面板                                  │       │
│  │  ├── 服务列表 (在线/离线/启动时间)          │       │
│  │  ├── 健康检查 (DB/Redis/MQ 连通性)         │       │
│  │  ├── 指标面板 (CPU/内存/线程/GC)           │       │
│  │  ├── 日志查看 (实时 + 历史)                │       │
│  │  ├── 环境变量 (运行时配置)                 │       │
│  │  └── 通知 (邮件/钉钉/飞书 离线告警)         │       │
│  └──────────────────────────────────────────┘       │
│         ▲                                            │
│         │ HTTP (Actuator Endpoints)                   │
│    ┌────┴────┬─────────┬─────────┐                   │
│    ▼         ▼         ▼         ▼                   │
│  gateway  system    device    message                │
│  :48080   :48099   :48083   :48088                  │
└──────────────────────────────────────────────────────┘
```

### 7.2 监控指标

| 指标类别 | 具体指标 |
|----------|----------|
| **JVM** | 堆内存 (已用/最大)、线程数 (活跃/峰值)、GC 次数/耗时 |
| **CPU** | 进程 CPU 使用率、系统 CPU 使用率 |
| **HTTP** | 请求数、响应时间、状态码分布 |
| **数据源** | 连接池活跃/空闲、等待线程数 |
| **缓存** | Redis 命中率、连接数 |
| **自定义** | 业务指标 (设备在线数、告警数等) |

---

## 八、日志与异常监控

### 8.1 日志架构

```
各微服务
  ├── Logback (Spring Boot 默认)
  ├── 日志格式: JSON (结构化)
  ├── TraceId 自动注入 (SkyWalking)
  └── 输出: stdout → Docker log driver
        │
        ▼
  SkyWalking OAP (可选: 日志采集)
  ├── 关联 TraceId ↔ 日志
  └── 错误日志聚合分析
```

### 8.2 API 错误日志

`iot-infra` 提供 `ApiErrorLogApi`，集中收集所有微服务的 API 异常:

```
iot-common-web: GlobalExceptionHandler
  │
  ├── 捕获所有未处理异常
  ├── 记录: 请求URL/参数/用户ID/堆栈
  └── 异步发送到 iot-infra → ApiErrorLog 持久化
        │
        ▼
  WEB 管理后台 /infra/api-error-log 页面查看
```

---

## 九、CORS 完整链路

```
浏览器跨域请求
  │
  ├── OPTIONS 预检
  │     ├── Gateway: CorsFilter → 200 OK (不进入后端)
  │     └── Gateway: CorsResponseHeaderFilter → 修复重复头
  │
  └── 实际请求
        ├── Nginx: Access-Control-Allow-Origin
        ├── Gateway: CorsFilter → 设置通配符 CORS 头
        └── Service: YudaoWebAutoConfiguration → 注册 CorsFilter
```

**注意:** CorsResponseHeaderFilter 是一个专门针对 Spring Cloud Gateway 2.x 的补丁，用于解决 `Access-Control-Allow-Origin` 和 `Access-Control-Allow-Credentials` 头重复的问题。升级到 Spring Cloud 2023.x 后可能不再需要。

---

## 十、端口与组件速查

| 组件 | 端口 | 职责 |
|------|------|------|
| **iot-gateway** | 48080 | API 统一入口, 路由/认证/灰度 |
| **SkyWalking OAP** | 11800 (gRPC) | 链路数据采集 |
| **SkyWalking UI** | 8080 | 链路可视化面板 |
| **Spring Boot Admin** | 集成在 iot-infra | 服务监控面板 |
| **Nacos** | 8848 | 服务注册/配置中心 |

---

## 十一、灰度发布实战

### 11.1 蓝绿部署

```
生产版本 (v1.0) → Nacos 元数据 version=1.0
新版本 (v2.0)   → Nacos 元数据 version=2.0

请求头 version=2.0 → 灰度路由器 → 只路由到 v2.0 实例
请求头 version=1.0 (或无) → 路由到 v1.0 实例
```

### 11.2 金丝雀发布

```
标签灰度:
  实例 A: tag=stable  (90% 流量)
  实例 B: tag=canary  (10% 流量)

请求头 tag=canary → 灰度路由器 → 只路由到实例 B
普通请求 → Nacos 加权随机 → 主要路由到实例 A
```

---

## 十二、修订记录

| 版本 | 日期 | 变更摘要 | 触发来源 |
|------|------|----------|----------|
| V9.18.0 | 2026-08-12 | 版本号同步对齐 V9.18.0；本文档内容（API 网关 / 过滤器链 / 安全架构 / SkyWalking / Spring Boot Admin 可观测性）未受 2026-08-11 NFS + MQTT + 删 EDGE 重构直接影响，基线版本跟随整体架构文件升级 | 基线同步 |

> **一句话总结:** API 网关与监控层是 EasyAIot 的流量入口和可观测性中枢，Spring Cloud Gateway 通过 4 层过滤器 (CORS→Token认证→灰度路由→响应修复) 为 12 个微服务提供统一路由和双层安全防御，SkyWalking + Spring Boot Admin 提供从链路追踪到服务监控的完整可观测性能力。
