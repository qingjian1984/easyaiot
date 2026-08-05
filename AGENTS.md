# AGENTS.md

This file provides guidance to Codex (Codex.ai/code) when working with code in this repository.

## 项目强制双基线

EasyAIoT 仓库内所有需求分析、产品设计、Feature Spec、ADR、Technical Design、开发、重构、缺陷修复、测试、评审、发布和运维变更，必须同时依据以下两份当前有效基线：

1. [EasyAIoT 项目开发宪法](.doc/开发规范/EasyAIoT项目开发宪法.md)：规定安全、架构、数据、兼容、流程、质量门禁和完成定义；
2. [平台功能计划](.doc/架构设计/平台功能计划.md)：规定产品范围、版本边界、部署档位、模块归属、里程碑和功能优先级。

执行规则：

- 开始任何项目工作前必须读取两份基线的当前版本，不得只依据其中一份；
- PRD、SPEC、ADR、TD、代码、数据库迁移、测试与交付说明不得违反双基线；
- `mini` / `standard` / `full` 的功能归属以平台功能计划为产品事实，工程实现和质量要求以项目开发宪法为治理事实；
- 双基线或下游文档发生冲突时必须停止实现，先完成事实核对和基线修订/ADR 决策，不得按既有代码或口头约定静默处理；
- 每次 SDD 评审和续作记录必须注明所依据的双基线版本，并把未关闭门禁保留为 OPEN。

## 项目概述

EasyAIoT 是云边端一体化智能算法应用平台，将 AI（YOLO26 目标检测、SAM 自动标注、视觉大模型、人脸/车牌识别）与 IoT 设备管理（MQTT/TCP/Modbus/OPC UA/GB28181/ONVIF）深度融合。同一套代码可部署于 4 GB 边缘盒子、AI 摄像机或全栈企业级一体机——通过安装时的部署配置（`mini` / `standard` / `full`）选择硬件档位。

## 仓库模块一览

| 模块 | 技术栈 | 职责 |
|------|--------|------|
| `DEVICE/` | Java 17+、Spring Boot 2.7、Maven 多模块 | 后端控制平面——设备注册、认证鉴权、任务调度、告警、数据集、物模型 |
| `WEB/` | Vue 3 + TypeScript、Ant Design Vue、Vite、pnpm | 管理控制台 / 指挥中心 UI |
| `VISUALIZE/` | Vue 3 + TypeScript、Naive UI、ECharts、VChart、Three.js、Vite | 拖拽式大屏可视化编辑器 & Web SCADA 组态 |
| `APP/` | UniApp 3 + Vue 3 + TypeScript、Wot Design UI、pnpm | 移动端 App / 小程序（H5、微信、支付宝、iOS、Android） |
| `AI/` | Python 3、Flask、PyTorch 2.9 (CUDA 12.8)、YOLO26、SAM、PaddleOCR、InsightFace、Milvus | AI 推理、训练、自动标注流水线、模型服务 |
| `VIDEO/` | Python 3、Flask、OpenCV、Kafka、ONNX、PaddleOCR、Milvus | 摄像机管理、流媒体、告警、快照、人脸/车牌识别 |
| `TASK/` | C++17、CMake、OpenCV、FFmpeg、ONNX Runtime、jsoncpp、glog | 高性能本地推理引擎（YOLO ONNX + RTMP 编码） |
| `NODE/` | Python 3、Flask、paho-mqtt、Docker Compose（子进程） | Worker 节点代理——工作负载编排、媒体栈、MQTT Broker 管理 |
| `EDGE/` | Python 3、Flask、paho-mqtt（内存占用 ~512 MB） | 边缘运行时——通过 MQTT 在开发板上无头执行算法任务 |
| `.scripts/` | Bash、Docker Compose | 安装脚本、中间件模板、协议演示（MQTT/Modbus/OPC UA） |

## 常用命令

### 后端（Java — `DEVICE/`）

```bash
cd DEVICE
mvn clean package -DskipTests          # 构建全部模块
mvn -pl iot-device -am compile         # 编译单个模块及其依赖
mvn test -pl iot-device                # 运行单个模块的测试
mvn test -pl iot-device -Dtest=UserServiceTest  # 运行单个测试类
```

`pom.xml` 默认 `maven.test.skip=true`。需要 Java 17+（pom.xml 声明 17，README 推荐 21）。

### Web 管理端（`WEB/`）

```bash
cd WEB
pnpm install
pnpm dev                               # Vite 开发服务器
pnpm build                             # 生产构建（需要 NODE_OPTIONS=--max-old-space-size=8192）
pnpm lint                              # ESLint
pnpm lint:fix                          # ESLint 自动修复
pnpm type:check                        # vue-tsc --noEmit --skipLibCheck
```

Node ≥18（推荐 ≥20），pnpm ≥11.3。

### 可视化（`VISUALIZE/`）

```bash
cd VISUALIZE
pnpm install
pnpm dev                               # Vite 开发服务器
pnpm build                             # 生产构建
pnpm lint                              # ESLint
pnpm lint:fix                          # ESLint 自动修复
```

### 移动端（`APP/`）

```bash
cd APP
pnpm install
pnpm dev                    # H5 开发
pnpm dev:mp                 # 微信小程序
pnpm dev:mp-alipay          # 支付宝小程序
pnpm dev:app                # 原生 App
pnpm build:h5               # H5 构建
pnpm build:mp               # 微信小程序构建
pnpm lint                   # ESLint
pnpm lint:fix               # ESLint 自动修复
```

Node ≥20，pnpm ≥9。基于 `unibest` 模板（UniApp + Vue3 + TS + Vite5 + UnoCSS）。**编辑 APP 代码时务必参考 `APP/.cursor/rules/` 中的 6 个规则文件**，涵盖 uni-app 模式、Vue/TS 约定、样式、API 模式等。

### AI 模块（`AI/`）

```bash
cd AI
pip install -r requirements.txt          # 本地安装（含 PyTorch）
# Docker 构建使用 requirements-docker.txt（见 Dockerfile）——不要混用
```

**模块化 requirements 策略**（不要混用）：
- `requirements.txt` → 完整本地开发（含 PyTorch ≥2.9.0 + torchvision）
- `requirements-docker.txt` → Docker 镜像（PyTorch 已在基础镜像中）
- `requirements-base.txt` → 基础依赖（Flask、ultralytics、onnxruntime-gpu、PaddleOCR 等）
- `requirements-sam.txt` → SAM 自动标注专用
- `requirements-node-ai-service.txt` → 节点推理服务最小集
- `requirements-node-llm-service.txt` → LLM 服务（vLLM、Qwen3）
- `requirements-node-model-train.txt` → 训练 worker

CUDA 版 PyTorch 参考 https://pytorch.org/get-started/locally/。Docker 基础镜像：`pytorch/pytorch:2.9.0-cuda12.8-cudnn9-devel`。

**AI 模块内含 4 个子服务**：`ai_service/`（主推理）、`auto_label_worker/`（SAM 标注）、`llm_service/`（LLM/vLLM）、`train_worker/`（训练）。测试文件（30+ `test_*.py`）可直接用 `pytest` 运行。

### VIDEO 模块

```bash
cd VIDEO
pip install -r requirements.txt
# 同样注意 Docker 环境使用 requirements-docker.txt
```

VIDEO 内含 60+ 服务文件，重度使用 Kafka 事件总线。测试文件同样可直接 `pytest` 运行。

### C++ 推理引擎（`TASK/`）

```bash
cd TASK
mkdir build && cd build
cmake .. && cmake --build .
```

C++17。依赖 OpenCV、FFmpeg 开发库、ONNX Runtime、jsoncpp、glog。

**平台差异**：
- **Windows**：使用 vcpkg + Anaconda ONNX Runtime DirectML（支持 RTX 5060）
- **Linux**：pkg-config + 系统库 + OpenSSL

详细构建文档见 `TASK/BUILD_GUIDE.md`、`TASK/WINDOWS_DEPLOYMENT_GUIDE.md`。

### 全栈部署（Docker Compose）

```bash
.scripts/docker/install_linux.sh         # Linux 完整安装
.scripts/docker/install_mac.sh           # macOS
.scripts/docker/install_linux_arm.sh     # ARM / 边缘盒子
.scripts/docker/install_linux_kylin.sh   # 麒麟 OS（国产系统）
.scripts/docker/start_services.sh        # 启动全部服务
```

Docker Compose 文件：`DEVICE/docker-compose.yml`、`AI/docker-compose.yaml`、`VIDEO/docker-compose.yaml`、`WEB/docker-compose.yaml`、`VISUALIZE/docker-compose.yaml`，以及 `.scripts/docker/docker-compose.yml`（中间件）。

`.scripts/docker/` 还包含大量诊断/修复脚本（`fix_*.sh`、`diagnose_*.sh`、`analyze_*.sh`）和初始化脚本（`init-databases.sh`、`init-tdengine.sh`）。

## 架构——模块如何协作

### 数据流全景

```
设备 → MQTT Broker(EMQX) → DEVICE(Java) → TDengine(时序) / PostgreSQL(关系)
                                                ↓
摄像头 → SRS/ZLMediaKit → VIDEO(Python) → Kafka → AI(Python) → 告警/快照
                                                ↓
                                     MinIO(图像/模型交换)
                                     Milvus(人脸向量检索)
```

### 核心模块职责

**DEVICE（Java）** 是中枢大脑，向 WEB、VISUALIZE、APP 暴露 HTTP + WebSocket API。持久化到 PostgreSQL（关系型）、TDengine（时序遥测）、Redis（缓存）、MinIO（对象）、Milvus（人脸向量检索）。Java 服务使用 **Nacos** 作为配置中心 + 服务发现；Python 服务（AI、VIDEO）也注册到 Nacos。

**AI（Python/Flask）** 作为独立服务运行。DEVICE 通过 HTTP 向其派发推理/训练任务，通过 MinIO 交换图像和模型，通过 Milvus 交换人脸向量。4 个子服务：
- `ai_service/` — 主推理服务（YOLO ONNX + Ultralytics）
- `auto_label_worker/` — SAM 自动标注流水线
- `llm_service/` — LLM 服务（vLLM + Qwen3）
- `train_worker/` — PyTorch 模型训练 worker

**VIDEO（Python/Flask）** 负责摄像机管理、告警、快照、人脸/车牌识别。内部重度使用 **Kafka** 作为事件总线（算法结果、告警流水线、快照处理、媒体上传 worker）。通过 SRS/ZLMediaKit 拉取 RTSP/RTMP 流，并向 AI 喂帧。

**流媒体层**：SRS + ZLMediaKit + FFmpeg 处理 RTSP/RTMP/GB28181/ONVIF 的接入、转码、转发。DEVICE 通知 VIDEO 拉哪些流；AI 从 VIDEO 获取帧。**观看链路（~6500 Kbps）与算法链路（~3500 Kbps）在架构上解耦**。

**MQTT Broker**（EMQX）是 IoT 设备消息总线——MQTT/TCP/Modbus/OPC UA 设备遥测在此汇聚，DEVICE 订阅并写入 TDengine。

**NODE（Python 代理）** 运行在每个 worker 节点上，暴露 REST API（端口 9100），供中枢控制平面下发部署规格。通过 Docker Compose 子进程管理工作负载、媒体栈（SRS/FFmpeg）和 EMQX Broker 生命周期。Agent 向控制平面注册、发送心跳（CPU/内存/磁盘/GPU），接收部署/停止命令。

**EDGE（Python）** 是轻量边缘运行时（~512 MB）。注册到 NODE → 拉取配置 → 订阅 MQTT 算法命令 → 本地执行推理 → 上报告警到上游。**业务数据零本地存储**（使用 Ceph 边缘）。CLI：`python -m edge enroll`（注册）、`python -m edge run`（运行）。

**TASK（C++）** 是高性能本地推理引擎，基于 ONNX Runtime + FFmpeg + OpenCV，多线程池 + RTMP 编码 + 告警回调。用于 Python 开销不可接受的上墙/设备端推理场景。

**WEB、VISUALIZE、APP** 是纯前端，消费 DEVICE 的 HTTP/WS API。VISUALIZE 另外嵌入 Fuxa 用于 Web SCADA。

## Maven 模块结构（DEVICE/）

```
iot (root)
├── iot-parent              # BOM — 管理全部依赖版本
├── iot-gateway             # Spring Cloud Gateway（API 网关）
├── iot-common              # 共享库（17 子模块）
│   ├── iot-common-base, iot-common-security, iot-common-redis,
│   ├── iot-common-mybatis, iot-common-mq, iot-common-rpc,
│   ├── iot-common-web, iot-common-swagger, iot-common-tenant,
│   ├── iot-common-env, iot-common-excel, iot-common-ip,
│   ├── iot-common-job, iot-common-data-permission,
│   ├── iot-common-protection, iot-common-protocol, iot-common-test
├── iot-system              # 系统管理（用户、角色、权限、租户）
├── iot-infra               # 基础设施（代码生成、API 日志、文件、任务调度）
├── iot-file                # 文件上传/下载
├── iot-dataset             # AI 数据集管理
├── iot-node                # 节点控制平面（计算/媒体节点管理）
├── iot-visualize           # 可视化后端（大屏/SCADA 项目元数据）
├── iot-device              # 设备管理核心（api + biz）
├── iot-tdengine            # TDengine 时序集成
├── iot-sink                # 数据汇聚（告警证据、媒体归档）
├── iot-message             # 消息总线集成（MQTT、Kafka 桥接）
└── iot-gb28181             # GB28181 视频监控协议
```

跨模块依赖走 `iot-common-*`。模块按 `iot-*` 命名。

## 部署档位

安装时通过 `INSTALL_PROFILE` 环境变量选择（由 `.scripts/docker/deploy_profile.sh` 控制）：

| 档位 | 最低内存 | 适用场景 |
|------|----------|----------|
| `mini` | 4 GB | 边缘盒子，单点智能（实际占用 ~2 GB） |
| `standard` | 16 GB | AI 摄像机，楼层/园区覆盖（实际占用 ~10 GB） |
| `full` | 20 GB | 全栈一体机，IoT + 视频 + AI（实际占用 ~14 GB） |

## 关键中间件（Docker）

PostgreSQL、Redis、TDengine、MinIO、Milvus、EMQX（MQTT）、Kafka、Nacos、SRS、ZLMediaKit、Node-RED、Fuxa。初始化脚本位于 `.scripts/docker/`（`init-databases.sh`、`init-tdengine.sh` 等）。

## 语言 / 框架注意事项

- **Java 后端**：大量使用 Lombok（`@Data`、`@Builder` 等）+ MapStruct 做 DTO 映射。ORM 使用 MyBatis-Plus 3.5.5。`pom.xml` 固定了 `project.build.outputTimestamp` 以实现可复现构建——**不要删除**。
- **Python 服务（AI/VIDEO）**：Flask REST API，注册到 Nacos，内部异步事件走 Kafka。**本地 `requirements.txt` 与 Docker `requirements-docker.txt` 分离——不要混用。**
- **WEB/VISUALIZE**：Vue 3 Composition API + TypeScript。提交前运行 `pnpm type:check`。
- **APP**：UniApp + Wot Design UI。uni-app 特有模式（条件编译 `#ifdef`、生命周期 `onLoad/onShow/onReady`、rpx 单位、UnoCSS 原子类）请参考 `APP/.cursor/rules/`。
- **TASK（C++）**：C++17、CMake。Windows 与 Linux 构建路径不同（见上方平台差异）。
- 所有中间件端口/凭据通过 `.env` 文件配置（参见各模块下的 `env.example`）。
