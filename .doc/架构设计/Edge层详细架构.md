# AI / Media Processing — Python 层详细架构

> 基于整体架构文件 V9.18.0 + 代码结构深入分析
> Python 文件总数: 397+

---

## 一、总体定位

Python 层是整个 EasyAIot 平台的 **AI 与网络核心**，负责视频分析、AI 推理训练和节点管理，由 **3 大模块** 组成。边缘算力由 **RUNTIME（C++）原子模式**承接，本机或集群计算节点上 `executor=cpp`，不再有独立 Python 边缘套件。

| 模块 | 端口 | 框架 | 定位 |
|------|------|------|------|
| **VIDEO** | 6000 | Flask | 实时视频流 AI 分析引擎 |
| **AI** | 5000 | Flask | 模型全生命周期管理平台 |
| **NODE Agent** | 9100 | Flask | 边缘节点控制面代理 |

```
┌─────────────────────────────────────────────────────────────────────────┐
│                     AI / Media Processing — Python 层                     │
│                                                                         │
│  ┌──────────────┐  ┌──────────────┐  ┌──────────────┐                   │
│  │  AI (:5000)  │  │ VIDEO (:6000)│  │ NODE (:9100) │                   │
│  │  模型训练推理  │  │  视频分析服务  │  │  节点代理     │                   │
│  │  Flask       │  │  Flask       │  │  Flask       │                   │
│  └──────┬───────┘  └──────┬───────┘  └──────┬───────┘                   │
│         │                 │                 │                             │
│         └─────────────────┴─────────────────┘                             │
│                                    │                                     │
│              共享基础设施: PostgreSQL / Nacos / MinIO / Kafka / MQTT      │
└─────────────────────────────────────────────────────────────────────────┘
```

---

## 二、VIDEO — 视频分析服务 (Flask, Port 6000)

**定位**：实时视频流 AI 分析引擎，是整个平台视频智能化的核心。

### 2.1 技术栈

| 组件 | 版本/说明 |
|------|----------|
| Web 框架 | Flask (Blueprint 路由) |
| 视频处理 | OpenCV + FFmpeg (解码/编码/推流) |
| AI 推理 | YOLO v8/v11/v26 + ONNX Runtime |
| 目标跟踪 | ByteTrack / SimpleTracker |
| 人脸识别 | Milvus 向量数据库 (特征检索) |
| GPU | CUDA (PyTorch + ONNX Runtime GPU) |
| 数据库 | PostgreSQL (业务数据) |
| 消息队列 | Kafka (告警/人脸/车牌/后处理) |
| 存储 | MinIO (快照/模型) + NFS 共享媒体存储 `/mnt/easyaiot-media` (录像 playbacks) |
| 协议 | GB28181 / ONVIF / RTSP / 海康大华私有协议 |

### 2.2 目录结构

```
VIDEO/
├── run.py                          ← 主入口，Flask 应用启动
├── requirements.txt                ← Python 依赖
├── requirements-base.txt           ← 共享业务依赖 (Flask/SQLAlchemy/Ultralytics/ONNX/OpenCV)
├── requirements-docker.txt         ← Docker 构建依赖
├── Dockerfile                      ← 多阶段构建 (pytorch:2.9.0-cuda12.8-cudnn9-devel)
├── Dockerfile.arm                  ← ARM 架构构建
└── app/
    ├── __init__.py                 ← Flask 工厂函数，Blueprint 注册
    ├── blueprints/                 ← API 路由层
    │   ├── alert.py                ← 告警事件管理
    │   ├── algorithm_task.py       ← 算法任务 CRUD (实时/快照)
    │   ├── audio_talk.py           ← ONVIF 双向语音对讲
    │   ├── camera.py               ← 摄像头管理
    │   ├── device_detection_region.py ← 检测区域绘制
    │   ├── face.py                 ← 人脸识别管理
    │   ├── media_hook.py           ← SRS/ZLM Webhook (DVR/快照回调)
    │   ├── patrol.py               ← 巡航/巡检管理
    │   ├── plate.py                ← 车牌识别管理
    │   ├── playback.py             ← 视频回放
    │   ├── record.py               ← 录像管理
    │   ├── scenario_pose.py        ← 场景姿态检测
    │   ├── snap.py                 ← 快照抓拍管理 (943 行)
    │   └── stream_forward.py       ← 流转发 (529 行)
    ├── services/                   ← 业务逻辑层 (核心)
    │   ├── alert_consumer_service.py   ← 告警 Kafka 消费 (亦消费 iot-sink 转发的 MQTT 告警通知)
    │   ├── alert_hook_service.py       ← 告警 Webhook 回调 (新增 AlgoMqttBus→iot-sink MQTT 来源对齐)
    │   ├── alert_service.py            ← 告警 CRUD
    │   ├── algorithm_service.py        ← 算法管理业务
    │   ├── algorithm_task_daemon.py    ← 算法任务守护进程
    │   ├── algorithm_task_launcher_service.py ← 算法任务启动器
    │   ├── audio_talk_service_onvif.py ← ONVIF 音频回传
    │   ├── auto_frame_extraction_service.py ← 自动帧提取
    │   ├── dvr_upload_service.py       ← DVR Hook/Kafka 事件转发到 iot-sink（默认经 Gateway http://localhost:48080/admin-api/sink/media/hook/srs/on_dvr），录像上传流水线（NFS 读盘→MinIO→Playback/告警回填）已迁移至 iot-sink
    │   ├── face_* (6 文件)             ← 人脸检测/识别/匹配/入库
    │   ├── frame_extractor_service.py  ← 帧提取服务
    │   ├── gb28181_sync_service.py     ← GB28181 设备同步
    │   ├── media_kafka_service.py      ← Kafka 媒体事件集成
    │   ├── minio_service.py            ← MinIO 存储操作
    │   ├── onvif_service.py            ← ONVIF PTZ/设备控制
    │   ├── patrol_session_service.py   ← 巡航会话协调
    │   ├── plate_* (3 文件)            ← 车牌检测/识别/匹配
    │   ├── playback_disk_guard_service.py ← 录像磁盘空间守护 (NFS 共享卷 `/mnt/easyaiot-media/playbacks`)
    │   ├── post_process_* (4 文件)     ← Kafka 后处理编排
    │   ├── pusher_service.py           ← 视频推流服务
    │   ├── record_video_service.py     ← 录像协调服务 (录像落 NFS 共享媒体存储)
    │   ├── snap_* (4 文件)             ← 快照抓拍/上传
    │   ├── sorter_service.py           ← 分析结果排序
    │   ├── srs_container_guard_service.py ← SRS 容器健康守护
    │   ├── stream_forward_* (5 文件)   ← 流转发服务簇
    │   └── storage_service.py          ← 存储抽象层
    └── utils/                      ← 工具层
        ├── decode/                     ← 视频解码子包
        │   ├── ffmpeg_decoder.py       ← FFmpeg 拉流解码 → numpy 帧
        │   ├── shared_memory.py        ← 共享内存进程间零拷贝传输
        │   ├── async_stream.py         ← 异步流多路复用读取
        │   └── frame_queue.py          ← 帧缓冲队列 (生产者-消费者)
        ├── algo_model_detect.py        ← YOLO/ONNX 模型检测抽象
        ├── onnx_inference.py           ← ONNX Runtime 推理
        ├── tracker.py                  ← SimpleTracker 目标跟踪
        ├── flighthub_source.py         ← FlightHub 数据源集成
        ├── face_capture_queue.py       ← 人脸捕获队列
        └── plate_capture_queue.py      ← 车牌捕获队列
```

### 2.3 Blueprint 路由统计

| Blueprint | 行数 | 功能 |
|-----------|------|------|
| snap.py | 943 | 快照管理 |
| stream_forward.py | 529 | 流转发 |
| algorithm_task.py | ~500 | 算法任务 (实时/快照) |
| camera.py | ~400 | 摄像头管理 |
| alert.py | ~400 | 告警事件 |
| record.py | 251 | 录像管理 |
| playback.py | 304 | 回放管理 |
| face.py | ~300 | 人脸识别 |
| device_detection_region.py | ~300 | 检测区域绘制 |

### 2.4 6 个子服务架构

```
┌─────────────────────────────────────────────────────────────────────┐
│                    VIDEO 主服务 (Flask :6000)                         │
│                                                                     │
│  调度编排 ─────────────────────────────────────────────────────────  │
│                                                                     │
│  ┌──────────────────┐  ┌──────────────────┐  ┌──────────────────┐   │
│  │ realtime_service │  │ snapshot_service │  │ frame_extractor  │   │
│  │ 实时视频流 AI 分析 │  │ 快照图片 AI 分析  │  │ 视频帧提取        │   │
│  │ YOLO + ByteTrack │  │ 定时抓拍 + 推理  │  │ FFmpeg 解码      │   │
│  │ 推流 + 告警       │  │ 告警联动         │  │ 帧缓冲队列        │   │
│  └────────┬─────────┘  └────────┬─────────┘  └────────┬─────────┘   │
│           │                     │                     │             │
│  ┌──────────────────┐  ┌──────────────────┐  ┌──────────────────┐   │
│  │ sorter_service   │  │ pusher_service   │  │ stream_forward   │   │
│  │ 分析结果排序       │  │ 视频推流          │  │ 流转发            │   │
│  │ 去重 + 最优帧     │  │ RTMP/RTSP 输出   │  │ 协议转换 + 代理   │   │
│  └──────────────────┘  └──────────────────┘  └──────────────────┘   │
└─────────────────────────────────────────────────────────────────────┘
```

| 子服务 | 职责 |
|--------|------|
| **realtime_algorithm_service** | 实时视频流 AI 分析：YOLO 检测 → ByteTrack 跟踪 → RTMP 推流 → 告警输出（C++ 执行器走 AlgoMqttBus→iot-sink MQTT 总线，Python 端 algo_mqtt_bus.py 对齐实现；原 Kafka 通道并列保留） |
| **snapshot_algorithm_service** | 快照图片 AI 分析：Cron 定时抓拍 → 单帧推理 → 告警联动 |
| **frame_extractor_service** | 视频帧提取：FFmpeg 解码 → 帧缓冲队列 → 下游消费 |
| **sorter_service** | 分析结果排序：去重 + 最优帧选择 |
| **pusher_service** | 视频推流：RTMP/RTSP 编码输出 |
| **stream_forward_service** | 流转发：RTSP→RTMP 协议转换 + 多路分发 |

### 2.5 视频解码子包 (`utils/decode/`)

```
┌───────────────────────────────────────────┐
│           视频解码流水线                     │
│                                           │
│  摄像头/GB/RTSP                            │
│       │                                    │
│       ▼                                    │
│  ┌──────────────┐                         │
│  │ ffmpeg_decoder│  FFmpeg 拉流解码        │
│  │              │  RTSP/RTMP → numpy 帧   │
│  └──────┬───────┘                         │
│         │                                  │
│         ▼                                  │
│  ┌──────────────┐                         │
│  │ frame_queue  │  帧缓冲队列              │
│  │              │  生产者-消费者解耦        │
│  └──────┬───────┘                         │
│         │                                  │
│    ┌────┴────┐                             │
│    ▼         ▼                             │
│  ┌─────┐  ┌──────┐                        │
│  │推理  │  │推流   │  多消费者并行          │
│  │YOLO │  │RTMP  │                        │
│  └─────┘  └──────┘                        │
│                                           │
│  ┌──────────────┐                         │
│  │shared_memory │  跨进程零拷贝帧传输       │
│  │              │  (主进程→子进程)         │
│  └──────────────┘                         │
│                                           │
│  ┌──────────────┐                         │
│  │ async_stream │  异步多路流读取          │
│  │              │  单线程管理多路摄像头     │
│  └──────────────┘                         │
└───────────────────────────────────────────┘
```

### 2.6 告警流水线

告警上行有两条并列通道：**MQTT 算法总线**（C++ 执行器 RUNTIME 主路径，Python 端 `algo_mqtt_bus.py` 对齐）与 **Kafka 事件总线**（VIDEO 既有路径）。

**MQTT 路径（C++ 执行器主路径，与原 Kafka 并列）：** RUNTIME 检测到告警 → 写告警图到共享 NFS 的 `ALERT_IMAGES_DIR`，经 `AlgoMqttBus` 发布 MQTT 信封到 `mqtt/iot-alert-notification` → iot-sink 的 `IotAlgoBusMqttHandler` 订阅、落库、从 VIDEO 库补齐通知配置后转发 Kafka 通知。三对 topic/msgType：`mqtt/iot-alert-notification`/`alert.notification`、`mqtt/iot-snapshot-alert`/`alert.snapshot`、`mqtt/iot-post-process-request`/`post_process.request`；iot-sink 用 `$share/algo-sink/` 共享订阅组消费。总开关 `ALGO_BUS_TRANSPORT`（默认空=MQTT；`http/off/0/false/no`=关闭回退 HTTP）。

**Kafka 路径（VIDEO 既有，保留）：**

```
摄像头 → YOLO 检测 → 规则匹配 → Kafka (告警事件)
                                    │
                    ┌───────────────┼───────────────┐
                    ▼               ▼               ▼
              ┌──────────┐  ┌──────────┐  ┌──────────┐
              │ 人脸匹配  │  │ 车牌匹配  │  │ 后处理    │
              │ Milvus   │  │ Kafka    │  │ Kafka Sink│
              └────┬─────┘  └────┬─────┘  └────┬─────┘
                   │             │             │
                   └─────────────┼─────────────┘
                                 ▼
                         ┌──────────────┐
                         │ iot-sink     │
                         │ 邮件/短信/IM  │
                         │ 钉钉/飞书/微信│
                         └──────────────┘
```

> 心跳仍走 HTTP→VIDEO（`/video/algorithm/heartbeat/{realtime,patrol}`），不走 MQTT。

---

## 三、AI — AI 算法服务 (Flask, Port 5000)

**定位**：模型全生命周期管理平台，覆盖训练 → 导出 → 部署 → 推理的完整闭环。

### 3.1 技术栈

| 组件 | 版本/说明 |
|------|----------|
| 深度学习 | PyTorch 2.9 + Ultralytics YOLO (v8/v11/v26) |
| 分割模型 | SAM (Segment Anything Model) |
| 多模态大模型 | Qwen-VL3 / Qwen / DeepSeek |
| OCR | PaddleOCR |
| 语音 | 讯飞语音 API |
| 推理格式 | PyTorch / ONNX / TensorRT / OpenVINO / TorchScript |
| 姿态估计 | MMPose / MMDetection |
| 服务发现 | Nacos 注册 + 心跳 |
| 存储 | PostgreSQL (元数据) + MinIO (模型文件) |
| GPU 集群 | 多 GPU 调度 (哈希/轮询) |

### 3.2 目录结构

```
AI/
├── run.py                          ← 主入口，Flask + DB 初始化 + Nacos 注册
├── db_models.py                    ← SQLAlchemy ORM (20+ 表)
├── requirements.txt                ← Python 依赖
├── requirements-base.txt           ← 共享业务依赖
├── requirements-docker.txt         ← Docker 构建依赖
├── requirements-sam.txt            ← SAM 可选依赖 (segment-anything/modelscope/timm)
├── Dockerfile                      ← 多阶段构建 (pytorch:2.9.0-cuda12.8-cudnn9-runtime)
├── Dockerfile.arm                  ← ARM 架构构建
└── app/
    ├── blueprints/                 ← API 路由层
    │   ├── model.py                ← 模型管理 (CRUD/权重上传, 810 行)
    │   ├── train.py                ← 训练任务 (YOLO 微调/超参, 1036 行)
    │   ├── export.py               ← 模型导出 (ONNX/TensorRT/OpenVINO, 677 行)
    │   ├── deploy.py               ← 模型部署 (集群推理/负载均衡, 805 行)
    │   ├── inference.py            ← 推理服务 (单图/批量/流, 613 行)
    │   ├── auto_label.py           ← SAM 自动标注 (预标注工作流, 664 行)
    │   ├── llm.py                  ← 大模型推理 (多模态, 1718 行)
    │   ├── llm_deploy.py           ← 大模型部署
    │   ├── ocr.py                  ← OCR 文字识别 (385 行)
    │   ├── speech.py               ← 语音识别 (247 行)
    │   ├── plate.py                ← 车牌识别 (1114 行)
    │   ├── sam.py                  ← SAM 分割推理
    │   ├── pose.py                 ← 姿态估计
    │   ├── cluster.py              ← GPU 集群管理 (440 行)
    │   └── train_task.py           ← 训练任务调度 (372 行)
    ├── services/                   ← 业务逻辑层
    │   ├── auto_label_* (6 文件)   ← 自动标注编排/策略/模型/训练/聚类
    │   ├── cluster_inference_service.py ← GPU 集群推理协调
    │   ├── deploy_daemon.py        ← 模型部署守护进程
    │   ├── deploy_service.py       ← 模型部署管理
    │   ├── inference_service.py    ← 推理执行引擎
    │   ├── llm_deploy_service.py   ← 大模型部署服务
    │   ├── llm_node_capacity.py    ← 大模型节点容量评估
    │   ├── minio_service.py        ← MinIO 模型存储
    │   ├── ocr_service.py          ← PaddleOCR 服务
    │   ├── pose_service.py         ← MMPose 姿态估计服务
    │   ├── sam_service.py          ← SAM 模型服务
    │   ├── sam_bootstrap_quality.py ← SAM 标注质量引导
    │   ├── speech_service.py       ← 讯飞语音服务
    │   ├── train_launcher_service.py ← 训练任务启动器
    │   └── local_storage_service.py ← mini 规格本地存储
    ├── services/ai_service/        ← 轻量模型部署子服务
    │   ├── run_deploy.py           ← Flask 服务 + YOLO/ONNX 推理 + Nacos 注册
    │   ├── Dockerfile              ← 独立容器构建
    │   └── requirements.txt        ← 子服务依赖
    ├── services/llm_service/       ← 大模型部署子服务
    │   └── run_deploy.py           ← Qwen-VL3 / DeepSeek 推理服务
    ├── services/auto_label_worker/ ← 自动标注 Worker
    │   └── run_worker.py           ← SAM 推理 + 标注回写
    ├── services/train_worker/      ← 训练 Worker
    │   └── run_worker.py           ← YOLO 训练 + 超参搜索 + 断点续训
    ├── utils/                      ← 工具层
    │   ├── ai_env.py               ← 环境变量加载
    │   ├── onnx_inference.py       ← ONNX 推理引擎
    │   ├── onnx_validator.py       ← ONNX 模型验证
    │   ├── sam_inference.py        ← SAM 推理工具
    │   ├── sam_visualize.py        ← SAM 结果可视化
    │   ├── sam_result_parser.py    ← SAM 结果解析
    │   ├── pose_inference.py       ← 姿态估计推理
    │   ├── pose_rtsp_pipeline.py   ← 姿态估计 RTSP 流管道
    │   ├── rtsp_stream_pipeline.py ← 通用 RTSP 流处理管道
    │   ├── stream_detect_utils.py  ← 流检测工具集
    │   ├── train_checkpoint.py     ← 训练断点管理
    │   ├── train_dataset_layout.py ← 数据集布局工具
    │   ├── train_dataset_name.py   ← 数据集命名规范
    │   ├── train_process_control.py ← 训练进程控制
    │   ├── nacos_service_discovery.py ← Nacos 集成
    │   ├── node_client.py          ← 节点通信客户端
    │   ├── node_remote_python.py   ← 节点远程 Python 执行
    │   ├── yolo_chinese_font.py    ← YOLO 中文标注字体
    │   └── yolo_validator.py       ← YOLO 模型验证
    └── config/                     ← 配置
        ├── qwen_models.py          ← Qwen 模型配置
        ├── sam_config.py           ← SAM 配置
        └── xunfei_config.py        ← 讯飞 API 配置
```

### 3.3 Blueprint 路由统计

| Blueprint | 行数 | 功能 |
|-----------|------|------|
| llm.py | 1,718 | 大语言模型推理 (多模态: RTSP/视频/图片/音频/文本) |
| plate.py | 1,114 | 车牌识别 |
| train.py | 1,036 | 模型训练 (YOLO 微调、超参配置) |
| model.py | 810 | 模型管理 |
| deploy.py | 805 | 模型部署 (集群推理、负载均衡) |
| export.py | 677 | 模型导出 (ONNX/TensorRT/OpenVINO) |
| auto_label.py | 664 | SAM 自动标注 |
| inference.py | 613 | 推理服务 |
| cluster.py | 440 | GPU 集群管理 |
| ocr.py | 385 | OCR |
| speech.py | 247 | 语音识别 |
| train_task.py | 372 | 训练任务调度 |

### 3.4 AI 全链路闭环

```
┌──────────────────────────────────────────────────────────────────────┐
│                         AI 全链路闭环                                  │
│                                                                      │
│  ┌──────────┐    ┌──────────┐    ┌──────────┐    ┌──────────┐        │
│  │ 数据采集  │ → │ 数据标注  │ → │ 模型训练  │ → │ 模型导出  │        │
│  │ iot-dataset│  │ auto_label│   │ train    │   │ export   │        │
│  │ YOLO/COCO │   │ SAM预标注 │   │ YOLO微调 │   │ ONNX/TRT │        │
│  └──────────┘    └──────────┘    └──────────┘    └──────────┘        │
│                                                       │              │
│                                                       ▼              │
│  ┌──────────┐    ┌──────────┐    ┌──────────┐    ┌──────────┐        │
│  │ 告警联动  │ ← │ 推理结果  │ ← │ 实时推理  │ ← │ 模型部署  │        │
│  │ iot-sink │    │ 后处理    │   │ inference │   │ deploy   │        │
│  │ 邮件/IM  │    │          │   │ VIDEO/TASK│   │ 集群调度  │        │
│  └──────────┘    └──────────┘    └──────────┘    └──────────┘        │
└──────────────────────────────────────────────────────────────────────┘
```

### 3.5 子进程 Worker 架构

```
AI 主服务 (Flask :5000)
│
├── train_worker/run_worker.py        ← 训练 Worker 子进程
│   └── YOLO 训练 + 超参搜索 + 断点续训
│
├── auto_label_worker/run_worker.py   ← 自动标注 Worker
│   └── SAM 推理 + 标注结果回写
│
├── ai_service/run_deploy.py          ← 模型部署子服务 (独立 Flask)
│   └── YOLO/ONNX 推理 + Nacos 注册 + 心跳
│
└── llm_service/run_deploy.py         ← 大模型部署子服务
    └── Qwen-VL3 / DeepSeek 推理服务
```

### 3.6 模型导出格式链

```
PyTorch 模型
    │
    ├──→ ONNX (通用交换格式)
    │       └── ONNX Runtime (CPU/CUDA/DirectML)
    │
    ├──→ TensorRT (NVIDIA GPU 优化)
    │       └── FP16/INT8 量化加速
    │
    ├──→ OpenVINO (Intel CPU/GPU 优化)
    │       └── 边缘低功耗推理
    │
    └──→ TorchScript (PyTorch 原生序列化)
            └── C++ 加载 (LibTorch)
```

---

## 五、NODE Agent — 节点代理 (Flask, Port 9100)

**定位**：边缘节点控制面代理，管理算法工作负载 + 媒体堆栈 + MQTT 堆栈。

### 5.1 技术特点

| 属性 | 说明 |
|------|------|
| 技术栈 | Python Flask |
| 端口 | 9100 |
| 部署 | systemd 服务, `NODE/install.sh` |
| 安装路径 | `/opt/easyaiot/node-agent` |
| 运行方式 | **Host 原生进程** (非 Docker) |

### 5.2 目录结构

```
NODE/
├── run_agent.py              ← 主入口 (HTTP 服务 + 注册 + 心跳 + GPU 指标)
├── agent_server.py           ← Flask HTTP 路由 (deploy/stop 指令)
├── workload_manager.py       ← 算法进程管理 (GPU 分配 + 模型下载)
├── media_manager.py          ← 媒体堆栈管理 (SRS/ZLM Docker Compose)
├── mqtt_manager.py           ← MQTT 堆栈管理 (EMQX Docker Compose)
├── install.sh                ← systemd 安装脚本
├── requirements.txt          ← 依赖: requests, psutil, flask, minio
└── requirements-py39-extras.txt  ← Python < 3.10 兼容依赖
```

### 5.3 架构全景

```
┌───────────────────────────────────────────────────────┐
│              NODE Agent (Flask :9100)                   │
│              systemd 服务 / /opt/easyaiot/node-agent    │
│                                                       │
│  ┌─────────────────┐  ┌──────────────┐  ┌───────────┐ │
│  │ WorkloadManager │  │ MediaStack   │  │ MQTTStack  │ │
│  │                 │  │ Manager      │  │ Manager    │ │
│  │ 算法进程管理     │  │ Docker容器   │  │ Docker容器 │ │
│  │ GPU 分配        │  │ SRS / ZLM    │  │ EMQX       │ │
│  │ 模型下载(MinIO) │  │ docker-compose│ │ docker-compose│
│  │ 进程生命周期     │  │              │  │            │ │
│  └─────────────────┘  └──────────────┘  └───────────┘ │
│                                                       │
│  对外接口:                                             │
│  POST /deploy  ← 部署算法任务                           │
│  POST /stop    ← 停止算法任务                           │
│  GET  /health  ← 健康检查                              │
└───────────────────────────────────────────────────────┘
```

### 5.4 核心模块详解

| 模块 | 核心文件 | 职责 |
|------|---------|------|
| **入口** | `run_agent.py` | HTTP 服务启动 / 注册 / 心跳 / GPU 指标采集 |
| **API 服务** | `agent_server.py` | Flask 路由, 接收 deploy/stop 指令 |
| **工作负载管理** | `workload_manager.py` | 算法子进程生命周期 / GPU 分配 (哈希/轮询) / 模型 MinIO 下载 |
| **媒体堆栈管理** | `media_manager.py` | SRS/ZLM 通过 Docker Compose 启停/健康检查 |
| **MQTT 堆栈管理** | `mqtt_manager.py` | EMQX 通过 Docker Compose 启停/配置管理 |

---

## 六、共享库 (`.scripts/lib/`)

Python 层跨模块共享的工具库。

| 模块 | 路径 | 职责 |
|------|------|------|
| **cluster_storage** | `.scripts/lib/cluster_storage/__init__.py` | 集群模式存储管理: NFS 共享媒体存储挂载 (`/mnt/easyaiot-media`), 目录解析 (playbacks/snaps/datasets/models) — NFS 取代 CephFS |
| **model_resolver** | `.scripts/lib/model_resolver/__init__.py` | 模型路径解析: 本地存储 / Cluster (NFS) / MinIO 三种模式统一解析 |

---

## 七、模拟器 Demo (`.scripts/`)

用于演示和测试工业协议接入的 Python 模拟器。

| 协议 | 脚本数 | 功能 |
|------|--------|------|
| **MQTT** | 8 | 上行/下行/全流程/编解码/网关子设备 |
| **Modbus TCP** | 5 | TCP Slave / 上行/下行/平台属性 |
| **Modbus RTU** | 5 | RTU 上行/下行/虚拟串口/自测 |
| **OPC UA** | 4 | OPC UA Server / 上行/下行/平台属性 |
| **Go-View** | 1 | 可视化仪表盘数据生成 |

---

## 八、三模块协作关系

```
                    云中心 (Docker)
          ┌─────────────────────────────────┐
          │  iot-node (Java :48085)         │ ← 边缘节点管理
          │  EMQX (MQTT Broker :1883)       │ ← 任务指令下发 / AlgoMqttBus 告警总线
          │  SRS (流媒体 :1935/8080)         │ ← 流媒体接收
          └──────────┬──────────────────────┘
                     │
                     ▼
              ┌────────────────┐
              │ NODE Agent     │
              │ (Flask :9100)  │
              │                │
              │ 管理:           │
              │ • Workload     │
              │ • Media (NFS)  │
              │ • MQTT         │
              └────────┬───────┘
                       │
                       ▼
              ┌──────────────────┐
              │ RUNTIME (C++)    │
              │ executor=cpp     │
              │ YOLO + 告警(MQTT)│
              │ + 推流           │
              └────────┬─────────┘
                       │
                       ▼
              ┌──────────────┐
              │ Hardware     │
              │ 摄像头/传感器 │
              └──────────────┘
```

### 数据流路径

| 数据流 | 路径 |
|--------|------|
| **传感器数据** | 传感器 → EMQX (MQTT) → iot-sink → Redis/TDengine/Kafka |
| **视频流** | 摄像头 (RTSP/GB28181) → SRS/ZLM → VIDEO (:6000) → YOLO 推理 → Kafka/告警 |
| **AI 训练** | iot-dataset 标注 → AI (:5000) train → export → deploy → VIDEO/RUNTIME 推理 |
| **边缘推理** | 云中心 (EMQX) → MQTT 指令 → NODE Agent → RUNTIME (C++, executor=cpp) → NFS 共享媒体存储归档 |
| **告警上行** | RUNTIME → AlgoMqttBus → MQTT `mqtt/iot-alert-notification` → iot-sink IotAlgoBusMqttHandler → Kafka 通知 |
| **指令下发** | WEB → iot-device → Kafka → iot-sink (Modbus/OPC UA) / NODE Agent → RUNTIME |

---

## 九、关键设计模式

| 模式 | 应用位置 | 说明 |
|------|---------|------|
| **Flask Blueprint** | AI / VIDEO | 按功能域拆分路由, 各 Blueprint 独立注册 |
| **Nacos 服务发现** | AI / VIDEO / ai_service | 所有服务注册到 Nacos, 健康检查 + 心跳 |
| **MQTT 命令总线** | NODE / RUNTIME | 云边控制面: 任务指令通过 EMQX MQTT 下发；告警上行经 AlgoMqttBus |
| **Kafka 事件总线** | VIDEO | 告警/人脸/车牌匹配结果异步流转 |
| **子进程工作负载** | NODE / AI | subprocess.Popen 管理算法进程生命周期 |
| **Docker Compose 堆栈** | NODE Agent | SRS/ZLM/EMQX 的 Docker 容器生命周期管理 |
| **GPU 多卡调度** | AI / VIDEO | 哈希/轮询策略分配 GPU 设备给不同流 |
| **生产者-消费者队列** | VIDEO decode/ | 解码帧 → 缓冲队列 → 推理/推流多消费者 |
| **共享内存零拷贝** | VIDEO decode/ | 主进程→子进程帧传输避免序列化开销 |
| **策略模式** | AI auto_label | 多种自动标注策略 (SAM/聚类/主动学习) |

---

## 十、Docker 部署与原生进程分界

| 运行方式 | 组件 | 原因 |
|----------|------|------|
| **Docker 容器** | AI (:5000) | CUDA 环境隔离 |
| **Docker 容器** | VIDEO (:6000) | FFmpeg/OpenCV 环境隔离 |
| **Docker 容器** | 6 个 VIDEO 子服务 | 独立扩缩容 |
| **Host 原生进程** | NODE Agent (:9100) | 需要管理 Docker 容器生命周期 |
| **Host 原生进程** | RUNTIME (C++ executor) | 编译后的原生二进制 |

> **原因**: NODE Agent 必须在宿主机运行, 因为它需要调用 Docker CLI 管理容器、挂载 NFS 共享媒体存储、管理子进程生命周期。AI 和 VIDEO 则容器化以便于 CUDA/FFmpeg 环境管理和版本控制。

---

## 十一、Python 依赖管理

### 共享依赖 (`requirements-base.txt`)

```
Flask / SQLAlchemy / ultralytics / onnxruntime / opencv-python
paddleocr / psutil / requests / minio / kafka-python / paho-mqtt
```

### 模块特有依赖

| 模块 | 特有依赖 |
|------|---------|
| AI | torch, torchvision, segment-anything, modelscope, openai-clip, timm, mmdet, mmpose |
| VIDEO | opencv-python-headless, ffmpeg-python |
| NODE | psutil, flask, minio (精简) |

---

## 十二、服务端口汇总 (Python 层)

| 服务 | 端口 | 协议 |
|------|------|------|
| AI 主服务 | 5000 | HTTP (Flask) |
| VIDEO 主服务 | 6000 | HTTP (Flask) |
| NODE Agent | 9100 | HTTP (Flask) |
| ai_service (部署子服务) | 自动分配 | HTTP (Flask) |
| llm_service | 自动分配 | HTTP (Flask) |

---

## 十三、修订记录

| 版本 | 日期 | 变更摘要 | 触发来源 |
|------|------|---------|---------|
| V9.18.0 | 2026-08-12 | 删除 EDGE 联邦边缘模块（整章移除，边缘算力改由 RUNTIME C++ 原子模式承接）；存储后端 Ceph→NFS 共享媒体栈（`/mnt/easyaiot-media`）；告警上报 HTTP→MQTT 算法总线（AlgoMqttBus / IotAlgoBusMqttHandler）；标题由「Edge / Media Processing」改为「AI / Media Processing」 | commits 847b3c85/f13c491d/3b7c7f5c/242f8f31/42945f3f/7c47d3b9 |

---

> **一句话总结:** Python 层是 EasyAIot 的 **AI 智能化引擎** — AI 负责模型全生命周期 (训练→导出→部署→推理), VIDEO 负责实时视频分析 (检测→跟踪→告警→推流), NODE Agent 负责边缘控制面管理 (工作负载+媒体+MQTT 堆栈)。三模块通过 Nacos + MQTT + Kafka 协作, 边缘算力由 RUNTIME (C++) 原子模式承接, 形成从云端训练到边缘推理的完整 AIoT 闭环。
